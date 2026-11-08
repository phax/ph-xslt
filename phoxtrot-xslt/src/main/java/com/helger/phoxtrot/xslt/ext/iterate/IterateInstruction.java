/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phoxtrot.xslt.ext.iterate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.stream.IStreamRecordHandler;
import com.helger.phoxtrot.xslt.stream.StreamConsumer;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;

/**
 * xsl:iterate with its parameters and xsl:on-completion, plus xsl:break and
 * xsl:next-iteration (XSLT 3.0 section 7.2). xsl:break and
 * xsl:next-iteration may only appear in tail position (checked statically by
 * the core), so they leave the current iteration by an exception that the
 * innermost xsl:iterate catches.
 *
 * @author Philip Helger
 */
@Immutable
public final class IterateInstruction
{
  /** Thrown by xsl:next-iteration; carries the new parameter values. */
  private static final class NextIterationSignal extends RuntimeException
  {
    private final ICommonsList <XdmQName> m_aNames;
    private final ICommonsList <XdmSequence> m_aValues;

    NextIterationSignal (@NonNull final ICommonsList <XdmQName> aNames, @NonNull final ICommonsList <XdmSequence> aValues)
    {
      super ("xsl:next-iteration", null, false, false);
      m_aNames = aNames;
      m_aValues = aValues;
    }
  }

  /** Thrown by xsl:break after its result was written. */
  private static final class BreakSignal extends RuntimeException
  {
    BreakSignal ()
    {
      super ("xsl:break", null, false, false);
    }
  }

  private final XPathExpr m_aSelect;
  private final ICommonsList <VariableDef> m_aParams;
  private final SequenceConstructor m_aBody;
  private final XPathExpr m_aCompletionSelect;
  private final SequenceConstructor m_aCompletionBody;

  private IterateInstruction (@NonNull final XPathExpr aSelect,
                              @NonNull final ICommonsList <VariableDef> aParams,
                              @NonNull final SequenceConstructor aBody,
                              @Nullable final XPathExpr aCompletionSelect,
                              @Nullable final SequenceConstructor aCompletionBody)
  {
    m_aSelect = aSelect;
    m_aParams = aParams;
    m_aBody = aBody;
    m_aCompletionSelect = aCompletionSelect;
    m_aCompletionBody = aCompletionBody;
  }

  /**
   * Bind the current values of the iteration parameters, in declaration
   * order, as variables of a context.
   *
   * @param aCtx
   *        The context to extend.
   * @param aValues
   *        The current values, parallel to the parameters.
   * @return The derived context. Never <code>null</code>.
   */
  @NonNull
  private XsltContext _bindParams (@NonNull final XsltContext aCtx, @NonNull final XdmSequence [] aValues)
  {
    XsltContext ret = aCtx;
    for (int i = 0; i < aValues.length; ++i)
      ret = ret.withVariable (m_aParams.get (i).getName (), aValues[i]);
    return ret;
  }

  /** @return The initial parameter values, each in the scope of the preceding ones. */
  @NonNull
  private XdmSequence [] _initParams (@NonNull final XsltContext aCtx)
  {
    final XdmSequence [] ret = new XdmSequence [m_aParams.size ()];
    XsltContext aInit = aCtx;
    for (int i = 0; i < ret.length; ++i)
    {
      final VariableDef aParam = m_aParams.get (i);
      if (aParam.isRequired ())
        throw new XPathException ("XTDE0700", "The parameter $" + aParam.getName ().getEQName () + " of xsl:iterate has no value");
      ret[i] = aParam.evaluate (aInit);
      aInit = aInit.withVariable (aParam.getName (), ret[i]);
    }
    return ret;
  }

  /**
   * Run the body for one item.
   *
   * @return <code>true</code> if xsl:break ended the iteration.
   */
  private boolean _iterate (@NonNull final XsltContext aIterCtx,
                            @NonNull final XdmSequence [] aValues,
                            @NonNull final IXsltOutput aOut)
  {
    try
    {
      m_aBody.execute (aIterCtx, aOut);
    }
    catch (final NextIterationSignal ex)
    {
      for (int n = 0; n < ex.m_aNames.size (); ++n)
      {
        final XdmQName aName = ex.m_aNames.get (n);
        for (int p = 0; p < aValues.length; ++p)
          if (m_aParams.get (p).getName ().equals (aName))
            aValues[p] = m_aParams.get (p).convert (ex.m_aValues.get (n), aIterCtx);
      }
    }
    catch (final BreakSignal ex)
    {
      return true;
    }
    return false;
  }

  /** Run xsl:on-completion, without a focus. */
  private void _complete (@NonNull final XsltContext aBase,
                          @NonNull final XdmSequence [] aValues,
                          @NonNull final IXsltOutput aOut)
  {
    if (m_aCompletionSelect == null && m_aCompletionBody == null)
      return;
    final XsltContext aDoneCtx = _bindParams (aBase.withFocus (null, 0, 0), aValues);
    if (m_aCompletionSelect != null)
      aOut.appendAll (m_aCompletionSelect.evaluate (aDoneCtx));
    else
      m_aCompletionBody.execute (aDoneCtx, aOut);
  }

  /**
   * The streamed execution: the records arrive one at a time, the parameters
   * are carried from one to the next (XSLT 3.0 section 19.8.4.22). A port of
   * go-xslt's <code>strbIterState</code>.
   */
  private final class StreamedIteration implements IStreamRecordHandler
  {
    private final XsltContext m_aBase;
    private final IXsltOutput m_aOut;
    private final XdmSequence [] m_aValues;
    private boolean m_bDone;

    /**
     * Evaluate the initial parameter values and clear the current template
     * rule (as for the buffered execution), keeping the current mode.
     *
     * @param aCtx
     *        Context of the xsl:iterate instruction.
     * @param aOut
     *        Where the iteration writes its result.
     */
    StreamedIteration (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
    {
      m_aValues = _initParams (aCtx);
      m_aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
      m_aOut = aOut;
    }

    /**
     * Run the body for one streamed record with the record as context item
     * and position, and the current parameter values bound; the context size
     * is set to the position because the size is unknown. An xsl:break marks
     * the iteration as done. Checks for an interrupt afterwards.
     *
     * @param aRecord
     *        The record node.
     * @param nPosition
     *        Its 1-based position.
     */
    public void handle (@NonNull final IXdmNode aRecord, @Nonnegative final int nPosition)
    {
      // The size is not known while streaming; fn:last is not streamable
      // there (section 19.8.9.14)
      m_bDone = _iterate (_bindParams (m_aBase.withFocus (aRecord, nPosition, nPosition), m_aValues), m_aValues, m_aOut);
      m_aBase.getDynamicContext ().checkInterrupt ();
    }

    public boolean isDone ()
    {
      return m_bDone;
    }

    /**
     * Run xsl:on-completion after the last record with the final parameter
     * values. Only called by the driver when no xsl:break ended the
     * iteration.
     */
    public void complete ()
    {
      _complete (m_aBase, m_aValues, m_aOut);
    }
  }

  /**
   * The buffered execution of xsl:iterate (XSLT 3.0 section 7.2). The select
   * expression is evaluated in full, the initial parameter values are
   * computed, and the current template rule is cleared. The body then runs
   * for each item in turn with that item as context item (position i, size
   * of the whole sequence) and the current parameter values bound; values
   * passed by xsl:next-iteration replace those of the named parameters for
   * the following items. An xsl:break ends the iteration immediately,
   * without xsl:on-completion; otherwise xsl:on-completion runs after the
   * last item (also for an empty sequence). Checks for an interrupt after
   * every item.
   *
   * @param aCtx
   *        Context of the instruction.
   * @param aOut
   *        Where the result is written.
   */
  private void _execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final XdmSequence aItems = m_aSelect.evaluate (aCtx);
    final XdmSequence [] aValues = _initParams (aCtx);
    final XsltContext aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
    final int nSize = aItems.size ();
    for (int i = 0; i < nSize; ++i)
    {
      if (_iterate (_bindParams (aBase.withFocus (aItems.get (i), i + 1, nSize), aValues), aValues, aOut))
        return;
      aCtx.getDynamicContext ().checkInterrupt ();
    }
    _complete (aBase, aValues, aOut);
  }

  /**
   * Compile xsl:iterate.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction.
   */
  @NonNull
  public static IInstruction compileIterate (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final ICommonsList <VariableDef> aParams = new CommonsArrayList <> ();
    CompileContext aScope = aCtx;
    XslElement aCompletion = null;
    final ICommonsList <XslNode> aBodyNodes = new CommonsArrayList <> ();
    for (final XslNode aNode : aEl.getChildren ())
    {
      if (aNode instanceof final XslElement aChild && aChild.isXsl ("param"))
      {
        final VariableDef aParam = aScope.compileVariable (aChild, VariableDef.EKind.OTHER_PARAM);
        aParams.add (aParam);
        aScope = aScope.withVariable (aParam.getName ());
        continue;
      }
      if (aNode instanceof final XslElement aChild && aChild.isXsl ("on-completion"))
      {
        aCompletion = aChild;
        continue;
      }
      aBodyNodes.add (aNode);
    }
    final SequenceConstructor aBody = aScope.compileSequenceConstructor (aEl, aBodyNodes);
    XPathExpr aCompletionSelect = null;
    SequenceConstructor aCompletionBody = null;
    if (aCompletion != null)
    {
      aCompletionSelect = aScope.compileOptionalExpression (aCompletion, "select");
      if (aCompletionSelect == null)
        aCompletionBody = aScope.compileContent (aCompletion);
    }
    final IterateInstruction aInstr = new IterateInstruction (aSelect, aParams, aBody, aCompletionSelect, aCompletionBody);
    // Over the records of a streamed document, if it is the consumer
    final StreamConsumer aStreamed = StreamConsumer.create (aCtx,
                                                            aEl,
                                                            aSelect,
                                                            (aRtCtx, aRtOut, aRun) -> aInstr.new StreamedIteration (aRtCtx,
                                                                                                                    aRtOut));
    if (aStreamed == null)
      return new CompiledInstruction (aEl, aInstr::_execute);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      if (!aStreamed.drive (aRtCtx, aRtOut))
        aInstr._execute (aRtCtx, aRtOut);
    });
  }

  /**
   * Compile xsl:break.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction.
   */
  @NonNull
  public static IInstruction compileBreak (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      if (aSelect != null)
        aRtOut.appendAll (aSelect.evaluate (aRtCtx));
      else
        aBody.execute (aRtCtx, aRtOut);
      throw new BreakSignal ();
    });
  }

  /**
   * Compile xsl:next-iteration.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction.
   */
  @NonNull
  public static IInstruction compileNextIteration (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsList <VariableDef> aParams = aCtx.compileWithParams (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final ICommonsList <XdmQName> aNames = new CommonsArrayList <> (aParams.size ());
      final ICommonsList <XdmSequence> aValues = new CommonsArrayList <> (aParams.size ());
      for (final VariableDef aParam : aParams)
      {
        aNames.add (aParam.getName ());
        aValues.add (aParam.evaluate (aRtCtx));
      }
      throw new NextIterationSignal (aNames, aValues);
    });
  }
}
