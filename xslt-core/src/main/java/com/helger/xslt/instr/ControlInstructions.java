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
package com.helger.xslt.instr;

import java.util.regex.Matcher;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.regex.XPathRegex;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.StylesheetCompiler;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.runtime.BufferedOutput;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.ParamValue;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.runtime.XsltState;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * The control and invocation instructions: xsl:if, xsl:choose, xsl:for-each,
 * xsl:apply-templates, xsl:call-template, xsl:apply-imports, xsl:next-match,
 * xsl:sequence, xsl:analyze-string, xsl:perform-sort, xsl:message,
 * xsl:where-populated, xsl:on-empty and xsl:on-non-empty.
 *
 * @author Philip Helger
 */
@Immutable
public final class ControlInstructions
{
  private static final XdmQName XTMM9000 = new XdmQName (XPathException.ERR_NAMESPACE, "XTMM9000", "err");

  @PresentForCodeCoverage
  private static final ControlInstructions INSTANCE = new ControlInstructions ();

  private ControlInstructions ()
  {}

  @NonNull
  private static IInstruction _compileIf (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aTest = aCtx.compileRequiredExpression (aEl, "test");
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      if (aTest.evaluateBoolean (aRtCtx))
        aBody.execute (aRtCtx, aRtOut);
    });
  }

  @NonNull
  private static IInstruction _compileChoose (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsList <XPathExpr> aTests = new CommonsArrayList <> ();
    final ICommonsList <SequenceConstructor> aBodies = new CommonsArrayList <> ();
    SequenceConstructor aOtherwise = null;
    for (final XslElement aChild : aEl.getElementChildren ())
      if (aChild.isXsl ("when"))
      {
        aTests.add (aCtx.compileRequiredExpression (aChild, "test"));
        aBodies.add (aCtx.compileContent (aChild));
      }
      else
        if (aChild.isXsl ("otherwise"))
          aOtherwise = aCtx.compileContent (aChild);
    final SequenceConstructor aFinalOtherwise = aOtherwise;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      for (int i = 0; i < aTests.size (); ++i)
      {
        final XPathExpr aTest = aTests.get (i);
        final boolean bTrue;
        try
        {
          bTrue = aTest.evaluateBoolean (aRtCtx);
        }
        catch (final XPathException ex)
        {
          throw XsltException.locate (ex, aTest.getElement ());
        }
        if (bTrue)
        {
          aBodies.get (i).execute (aRtCtx, aRtOut);
          return;
        }
      }
      if (aFinalOtherwise != null)
        aFinalOtherwise.execute (aRtCtx, aRtOut);
    });
  }

  @NonNull
  private static IInstruction _compileForEach (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final ICommonsList <SortKeyDef> aSorts = aCtx.compileSortKeys (aEl);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      XdmSequence aItems = aSelect.evaluate (aRtCtx);
      if (aSorts.isNotEmpty ())
        aItems = SortKeyDef.sort (aSorts, aItems, aRtCtx);
      final XsltContext aBase = aRtCtx.withState (aRtCtx.getState ().withCurrentRule (null, aRtCtx.getState ().getCurrentMode ()));
      final int nSize = aItems.size ();
      for (int i = 0; i < nSize; ++i)
        aBody.execute (aBase.withFocus (aItems.get (i), i + 1, nSize), aRtOut);
    });
  }

  /**
   * Resolve the mode attribute of xsl:apply-templates.
   *
   * @return The mode, or <code>null</code> for #current.
   */
  @Nullable
  private static Mode _resolveApplyMode (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final String sMode = aEl.getAttr ("mode");
    final XdmQName aName;
    if (sMode == null)
      aName = StylesheetCompiler.getDefaultMode (aEl);
    else
    {
      final String s = sMode.trim ();
      if ("#current".equals (s))
        return null;
      if ("#default".equals (s))
        aName = StylesheetCompiler.getDefaultMode (aEl);
      else
        if ("#unnamed".equals (s))
          aName = Mode.UNNAMED;
        else
          aName = aCtx.resolveQName (aEl, s, false);
    }
    return aCtx.getStylesheet ().getOrCreateMode (aName);
  }

  /**
   * @param aCtx
   *        Context.
   * @return The current mode, or the default mode if none is set.
   */
  @NonNull
  public static Mode getCurrentMode (@NonNull final XsltContext aCtx)
  {
    final Mode ret = aCtx.getState ().getCurrentMode ();
    if (ret != null)
      return ret;
    final var aSheet = aCtx.getEngine ().getStylesheet ();
    return aSheet.getOrCreateMode (aSheet.getDefaultModeName ());
  }

  @NonNull
  private static IInstruction _compileApplyTemplates (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aEl.hasAttr ("select") ? aCtx.compileExpression (aEl, aEl.getAttr ("select"))
                                                    : aCtx.compileExpression (aEl, "child::node()");
    final Mode aMode = _resolveApplyMode (aCtx, aEl);
    final ICommonsList <SortKeyDef> aSorts = aCtx.compileSortKeys (aEl);
    final ICommonsList <VariableDef> aParams = aCtx.compileWithParams (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      XdmSequence aItems = aSelect.evaluate (aRtCtx);
      if (aSorts.isNotEmpty ())
        aItems = SortKeyDef.sort (aSorts, aItems, aRtCtx);
      final ICommonsList <ParamValue> aValues = ParamValue.evaluate (aParams, aRtCtx);
      aRtCtx.getEngine ().applyTemplates (aRtCtx, aItems, aMode != null ? aMode : getCurrentMode (aRtCtx), aValues, aRtOut);
    });
  }

  @NonNull
  private static IInstruction _compileCallTemplate (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XdmQName aName = aCtx.resolveQName (aEl, aEl.getAttr ("name"), false);
    final ICommonsList <VariableDef> aParams = aCtx.compileWithParams (aEl);
    final ICommonsList <XdmQName> aNonTunnel = new CommonsArrayList <> ();
    for (final VariableDef aParam : aParams)
      if (!aParam.isTunnel ())
        aNonTunnel.add (aParam.getName ());
    aCtx.getCompiler ().registerCallTemplate (aEl, aName, aNonTunnel);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final Engine aEngine = aRtCtx.getEngine ();
      final Template aTemplate = aEngine.getStylesheet ().getNamedTemplate (aName);
      if (aTemplate == null)
        throw new XPathException ("XTSE0650", "No template named " + aName.getEQName ());
      aEngine.callTemplate (aRtCtx, aTemplate, ParamValue.evaluate (aParams, aRtCtx), aRtOut);
    });
  }

  @NonNull
  private static IInstruction _compileNextMatch (@NonNull final CompileContext aCtx,
                                                 @NonNull final XslElement aEl,
                                                 final boolean bImports)
  {
    final ICommonsList <VariableDef> aParams = aCtx.compileWithParams (aEl);
    return new CompiledInstruction (aEl,
                                    (aRtCtx, aRtOut) -> aRtCtx.getEngine ().applyNextMatch (aRtCtx, bImports, ParamValue.evaluate (aParams, aRtCtx), aRtOut));
  }

  @NonNull
  private static IInstruction _compileSequence (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    if (aSelect != null)
      return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> aRtOut.appendAll (aSelect.evaluate (aRtCtx)));
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    return new CompiledInstruction (aEl, aBody::execute);
  }

  @NonNull
  private static IInstruction _compileAnalyzeString (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final Avt aRegex = aCtx.compileAvt (aEl, aEl.getAttr ("regex"));
    final Avt aFlags = aCtx.compileOptionalAvt (aEl, "flags");
    SequenceConstructor aMatching = null;
    SequenceConstructor aNonMatching = null;
    for (final XslElement aChild : aEl.getElementChildren ())
      if (aChild.isXsl ("matching-substring"))
        aMatching = aCtx.compileContent (aChild);
      else
        if (aChild.isXsl ("non-matching-substring"))
          aNonMatching = aCtx.compileContent (aChild);
    final SequenceConstructor aFinalMatching = aMatching;
    final SequenceConstructor aFinalNonMatching = aNonMatching;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmSequence aInput = Atomizer.atomize (aSelect.evaluate (aRtCtx));
      if (aInput.size () > 1)
        throw new XPathException ("XPTY0004", "The select expression of xsl:analyze-string returns more than one item");
      if (aInput.isSingleton () && !((XdmAtomic) aInput.get (0)).isStringLike ())
        throw new XPathException ("XPTY0004", "The select expression of xsl:analyze-string must return aFnArgs string");
      final String sInput = aInput.isEmpty () ? "" : Atomizer.getStringValue (aInput.get (0));
      final String sRegex = aRegex.evaluate (aRtCtx);
      final String sFlags = aFlags == null ? "" : aFlags.evaluate (aRtCtx);
      final XPathRegex aCompiled;
      try
      {
        aCompiled = XPathRegex.compile (sRegex, sFlags);
      }
      catch (final XPathException ex)
      {
        throw new XPathException ("FORX0001".equals (ex.getCode ()) ? "XTDE1145" : "XTDE1140",
                                  "Invalid regular expression or flags: " + ex.getDescription ());
      }
      // Collect the parts first: position and size refer to all of them
      final ICommonsList <String> aTexts = new CommonsArrayList <> ();
      final ICommonsList <String []> aGroups = new CommonsArrayList <> ();
      final Matcher aMatcher = aCompiled.matcher (aRtCtx.getDynamicContext (), sInput);
      int nLast = 0;
      final int nGroupCount = aCompiled.getGroupCount ();
      while (aMatcher.find ())
      {
        if (aMatcher.start () > nLast)
        {
          aTexts.add (sInput.substring (nLast, aMatcher.start ()));
          aGroups.add (null);
        }
        final String [] aMatchGroups = new String [nGroupCount + 1];
        aMatchGroups[0] = aMatcher.group ();
        for (int g = 1; g <= nGroupCount; ++g)
        {
          final int nJava = aCompiled.getJavaGroup (g);
          final String sGroup = nJava >= 0 ? aMatcher.group (nJava) : null;
          aMatchGroups[g] = sGroup == null ? "" : sGroup;
        }
        aTexts.add (aMatcher.group ());
        aGroups.add (aMatchGroups);
        nLast = aMatcher.end ();
        aRtCtx.getDynamicContext ().checkInterrupt ();
      }
      if (nLast < sInput.length ())
      {
        aTexts.add (sInput.substring (nLast));
        aGroups.add (null);
      }
      final int nSize = aTexts.size ();
      final XsltState aState = aRtCtx.getState ().withCurrentRule (null, aRtCtx.getState ().getCurrentMode ());
      for (int i = 0; i < nSize; ++i)
      {
        final String [] aMatchGroups = aGroups.get (i);
        final SequenceConstructor aBody = aMatchGroups != null ? aFinalMatching : aFinalNonMatching;
        if (aBody == null)
          continue;
        final XsltContext aPartCtx = aRtCtx.withState (aState.withRegexGroups (aMatchGroups))
                                      .withFocus (XdmAtomic.string (aTexts.get (i)), i + 1, nSize);
        aBody.execute (aPartCtx, aRtOut);
      }
    });
  }

  @NonNull
  private static IInstruction _compilePerformSort (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final ICommonsList <SortKeyDef> aSorts = aCtx.compileSortKeys (aEl);
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmSequence aItems;
      if (aSelect != null)
        aItems = aSelect.evaluate (aRtCtx);
      else
      {
        final SequenceOutput aOut = new SequenceOutput ();
        aBody.execute (aRtCtx, aOut);
        aItems = aOut.getSequence ();
      }
      aRtOut.appendAll (SortKeyDef.sort (aSorts, aItems, aRtCtx));
    });
  }

  @NonNull
  private static String _messageText (@NonNull final XdmSequence aValue)
  {
    final SerializationParams aParams = new SerializationParams ();
    aParams.setOmitXmlDeclaration (true);
    final StringBuilder aSB = new StringBuilder ();
    boolean bLastAtomic = false;
    for (final IXdmItem aItem : aValue)
    {
      if (aItem instanceof final XdmAtomic aAtomic)
      {
        if (bLastAtomic)
          aSB.append (' ');
        aSB.append (aAtomic.getLexical ());
        bLastAtomic = true;
        continue;
      }
      bLastAtomic = false;
      if (aItem instanceof final XdmNode aNode)
      {
        if (aNode.isAttribute ())
          aSB.append (aNode.getName ().getLexicalName ()).append ("=\"").append (aNode.getValue ()).append ('"');
        else
          if (aNode.getKind () == ENodeKind.NAMESPACE)
            aSB.append (aNode.getValue ());
          else
            aSB.append (XdmSerializer.serialize (XdmSequence.of (aNode), aParams));
      }
      else
      {
        final SerializationParams aAdaptive = new SerializationParams ();
        aAdaptive.setMethod (SerializationParams.METHOD_ADAPTIVE);
        aSB.append (XdmSerializer.serialize (XdmSequence.of (aItem), aAdaptive));
      }
    }
    return aSB.toString ();
  }

  @NonNull
  private static IInstruction _compileMessage (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final Avt aTerminate = aCtx.compileOptionalAvt (aEl, "terminate");
    final Avt aErrorCode = aCtx.compileOptionalAvt (aEl, "error-code");
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      // The message is the sequence of the select value and the content; aFnArgs
      // dynamic error in it does not end the transformation
      final BufferedOutput aContent = new BufferedOutput ();
      XdmSequence aValue;
      String sMessage;
      try
      {
        if (aSelect != null)
          aContent.appendAll (aSelect.evaluate (aRtCtx));
        aBody.execute (aRtCtx, aContent);
        aValue = aContent.getSequence ();
        sMessage = _messageText (aValue);
      }
      catch (final XPathException ex)
      {
        aValue = XdmSequence.EMPTY;
        sMessage = "Error in xsl:message: " + ex.getMessage ();
      }
      aRtCtx.getEngine ().addMessage (sMessage);
      boolean bTerminate = false;
      if (aTerminate != null)
      {
        final String s = aTerminate.evaluate (aRtCtx);
        if (!XsltNames.isXsltBooleanLexical (s))
          throw new XPathException ("XTDE0030", "terminate='" + s + "' is not yes or no");
        bTerminate = XsltNames.isXsltTrue (s);
      }
      if (bTerminate)
      {
        XdmQName aCode = XTMM9000;
        if (aErrorCode != null)
        {
          final String s = XsltNames.trimXmlWhitespace (aErrorCode.evaluate (aRtCtx));
          final XdmQName aResolved = aEl.resolveQName (s, false);
          if (aResolved == null)
            throw new XPathException ("XTDE1390", "'" + s + "' is not aFnArgs valid error code");
          aCode = aResolved;
        }
        throw new XPathException (aCode, sMessage, aValue);
      }
    });
  }

  private static boolean _isPopulated (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmNode aNode)
    {
      if (aNode.isElement () || aNode.isDocument ())
      {
        for (int i = 0; i < aNode.getChildCount (); ++i)
        {
          final XdmNode aChild = aNode.getChildAtIndex (i);
          if (aChild.isElement () || !aChild.getValue ().isEmpty ())
            return true;
        }
        return false;
      }
      if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
        return true;
      return !aNode.getValue ().isEmpty ();
    }
    if (aItem instanceof final XdmMap aMap)
      return !aMap.isEmpty ();
    if (aItem instanceof final XdmArray aArray)
    {
      for (final XdmSequence aMember : aArray.members ())
        for (final IXdmItem aMemberItem : aMember)
          if (_isPopulated (aMemberItem))
            return true;
      return false;
    }
    return !ConditionalContent.isDeemedEmpty (aItem);
  }

  @NonNull
  private static IInstruction _compileWherePopulated (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final BufferedOutput aBuffer = new BufferedOutput ();
      aBody.execute (aRtCtx, aBuffer);
      for (int i = 0; i < aBuffer.size (); ++i)
        if (_isPopulated (aBuffer.get (i)))
          aBuffer.replay (i, aRtOut);
    });
  }

  @NonNull
  private static IInstruction _compileOnEmpty (@NonNull final CompileContext aCtx,
                                               @NonNull final XslElement aEl,
                                               final boolean bOnNonEmpty)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new OnEmptyInstruction (aEl, bOnNonEmpty, aSelect, aBody);
  }

  /**
   * @param aRegistry
   *        Registry to register the control instructions in.
   */
  static void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("if", ControlInstructions::_compileIf);
    aRegistry.registerInstruction ("choose", ControlInstructions::_compileChoose);
    aRegistry.registerInstruction ("for-each", ControlInstructions::_compileForEach);
    aRegistry.registerInstruction ("apply-templates", ControlInstructions::_compileApplyTemplates);
    aRegistry.registerInstruction ("call-template", ControlInstructions::_compileCallTemplate);
    aRegistry.registerInstruction ("apply-imports", (aCC, aElem) -> _compileNextMatch (aCC, aElem, true));
    aRegistry.registerInstruction ("next-match", (aCC, aElem) -> _compileNextMatch (aCC, aElem, false));
    aRegistry.registerInstruction ("sequence", ControlInstructions::_compileSequence);
    aRegistry.registerInstruction ("analyze-string", ControlInstructions::_compileAnalyzeString);
    aRegistry.registerInstruction ("perform-sort", ControlInstructions::_compilePerformSort);
    aRegistry.registerInstruction ("message", ControlInstructions::_compileMessage);
    aRegistry.registerInstruction ("where-populated", ControlInstructions::_compileWherePopulated);
    aRegistry.registerInstruction ("on-empty", (aCC, aElem) -> _compileOnEmpty (aCC, aElem, false));
    aRegistry.registerInstruction ("on-non-empty", (aCC, aElem) -> _compileOnEmpty (aCC, aElem, true));
  }
}
