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
package com.helger.xslt.ext.iterate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;

/**
 * xsl:iterate with its parameters and xsl:on-completion, plus xsl:break and
 * xsl:next-iteration (XSLT 3.0 section 7.1). xsl:break and
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

  @NonNull
  private XsltContext _bindParams (@NonNull final XsltContext aCtx, @NonNull final XdmSequence [] aValues)
  {
    XsltContext ret = aCtx;
    for (int i = 0; i < aValues.length; ++i)
      ret = ret.withVariable (m_aParams.get (i).getName (), aValues[i]);
    return ret;
  }

  private void _execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final XdmSequence aItems = m_aSelect.evaluate (aCtx);
    // Initial parameter values, each in the scope of the preceding ones
    final XdmSequence [] aValues = new XdmSequence [m_aParams.size ()];
    XsltContext aInit = aCtx;
    for (int i = 0; i < aValues.length; ++i)
    {
      final VariableDef aParam = m_aParams.get (i);
      if (aParam.isRequired ())
        throw new XPathException ("XTDE0700", "The parameter $" + aParam.getName ().getEQName () + " of xsl:iterate has no value");
      aValues[i] = aParam.evaluate (aInit);
      aInit = aInit.withVariable (aParam.getName (), aValues[i]);
    }
    final XsltContext aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
    final int nSize = aItems.size ();
    for (int i = 0; i < nSize; ++i)
    {
      final XsltContext aIterCtx = _bindParams (aBase.withFocus (aItems.get (i), i + 1, nSize), aValues);
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
        return;
      }
      aCtx.getDynamicContext ().checkInterrupt ();
    }
    if (m_aCompletionSelect == null && m_aCompletionBody == null)
      return;
    // xsl:on-completion runs without a focus
    final XsltContext aDoneCtx = _bindParams (aBase.withFocus (null, 0, 0), aValues);
    if (m_aCompletionSelect != null)
      aOut.appendAll (m_aCompletionSelect.evaluate (aDoneCtx));
    else
      m_aCompletionBody.execute (aDoneCtx, aOut);
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
    return new CompiledInstruction (aEl, aInstr::_execute);
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
