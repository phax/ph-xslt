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
package com.helger.xslt.ext.accumulator;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.fn.IXPathFunction;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.CompiledPattern;
import com.helger.xslt.compile.IInstructionCompiler;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.ControlInstructions;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.Mode;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.runtime.XsltState;
import com.helger.xslt.schema.ValidationRequest;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Accumulators (XSLT 3.0 chapter 18): the xsl:accumulator declaration and the
 * functions accumulator-before() and accumulator-after(). The values of an
 * accumulator are computed for a whole tree at its first use, in one
 * iterative document-order walk, and kept per run.
 *
 * @author Philip Helger
 */
public final class AccumulatorExtension implements IXsltExtension
{
  /** The name of the variable holding the current value in a rule. */
  private static final XdmQName VAR_VALUE = new XdmQName ("value");

  /** Extension data: the list of all compiled accumulator declarations. */
  private static final Object KEY_DECLS = new Object ();
  /** Extension data: the effective accumulators by name. */
  private static final Object KEY_BY_NAME = new Object ();
  /** Extension data: use-accumulators references to check. */
  private static final Object KEY_REFS = new Object ();
  /** Extension data: the accumulators applicable per mode. */
  private static final Object KEY_MODES = new Object ();
  /** Run data: the computed values. */
  private static final Object KEY_RUN = new Object ();

  private record Rule (@NonNull CompiledPattern match,
                       boolean end,
                       @Nullable XPathExpr select,
                       @Nullable SequenceConstructor body)
  {}

  private record Accumulator (@NonNull XslElement element,
                              @NonNull XdmQName name,
                              @NonNull XPathExpr initial,
                              @Nullable SeqType as,
                              boolean streamable,
                              @NonNull ICommonsList <Rule> rules)
  {}

  private record UseRef (@NonNull XslElement element, @NonNull ICommonsList <XdmQName> names)
  {}

  /** The values of one accumulator over one tree: before and after per node. */
  private static final class TreeValues
  {
    private final Map <XdmNode, XdmSequence> m_aBefore = new IdentityHashMap <> ();
    private final Map <XdmNode, XdmSequence> m_aAfter = new IdentityHashMap <> ();
    private boolean m_bDone;
  }

  /** Restrictions of the accumulators applicable to a tree. */
  private record TreeRestriction (@Nullable ICommonsSet <XdmQName> names, boolean streamed)
  {}

  /** Per-run state. */
  private static final class RunData
  {
    private final ICommonsMap <Accumulator, Map <XdmNode, TreeValues>> m_aValues = new CommonsHashMap <> ();
    private final Map <XdmNode, TreeRestriction> m_aRestrictions = new IdentityHashMap <> ();
    // Nodes of copies that keep the accumulator values of their original
    private final Map <XdmNode, XdmNode> m_aOrigins = new IdentityHashMap <> ();
  }

  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        return true;
    return false;
  }

  @NonNull
  private static Rule _compileRule (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final CompiledPattern aMatch = aCtx.getCompiler ().compileCheckedPattern (aCtx, aEl, aEl.getAttr ("match"));
    boolean bEnd = false;
    final String sPhase = aEl.getAttr ("phase");
    if (sPhase != null)
    {
      final String s = sPhase.trim ();
      if ("end".equals (s))
        bEnd = true;
      else
        if (!"start".equals (s))
          throw XsltException.at (aEl, "XTSE0020", "phase='" + sPhase + "' must be start or end");
    }
    final CompileContext aRuleCtx = aCtx.withVariable (VAR_VALUE);
    final XPathExpr aSelect = aRuleCtx.compileOptionalExpression (aEl, "select");
    if (aSelect != null && _hasContent (aEl))
      throw XsltException.at (aEl, "XTSE3300", "xsl:accumulator-rule has both a select attribute and content");
    final SequenceConstructor aBody = aSelect == null ? aRuleCtx.compileContent (aEl) : null;
    return new Rule (aMatch, bEnd, aSelect, aBody);
  }

  @SuppressWarnings ("unchecked")
  @NonNull
  private static <T> ICommonsList <T> _getList (@NonNull final CompiledStylesheet aSheet, @NonNull final Object aKey)
  {
    ICommonsList <T> ret = (ICommonsList <T>) aSheet.getExtensionData (aKey);
    if (ret == null)
    {
      ret = new CommonsArrayList <> ();
      aSheet.putExtensionData (aKey, ret);
    }
    return ret;
  }

  private static void _compileAccumulator (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XdmQName aName = PackageModel.qualify (aEl, aCtx.resolveQName (aEl, aEl.getAttr ("name"), false));
    final XPathExpr aInitial = aCtx.compileRequiredExpression (aEl, "initial-value");
    final String sAs = aEl.getAttr ("as");
    final SeqType aAs = sAs == null ? null : aCtx.compileSequenceType (aEl, sAs);
    boolean bStreamable = false;
    final String sStreamable = aEl.getAttr ("streamable");
    if (sStreamable != null)
    {
      if (!XsltNames.isXsltBooleanLexical (sStreamable))
        throw XsltException.at (aEl, "XTSE0020", "streamable='" + sStreamable + "' is not an XSLT boolean");
      bStreamable = XsltNames.isXsltTrue (sStreamable);
    }
    final ICommonsList <Rule> aRules = new CommonsArrayList <> ();
    for (final XslElement aChild : aEl.getXslChildren ("accumulator-rule"))
      aRules.add (_compileRule (aCtx, aChild));
    if (aRules.isEmpty ())
      throw XsltException.at (aEl, "XTSE0010", "xsl:accumulator requires at least one xsl:accumulator-rule");
    AccumulatorExtension.<Accumulator> _getList (aCtx.getStylesheet (), KEY_DECLS)
                        .add (new Accumulator (aEl, aName, aInitial, aAs, bStreamable, aRules));
  }

  /**
   * @return The names, or <code>null</code> for #all.
   */
  @Nullable
  private static ICommonsSet <XdmQName> _resolveSet (@NonNull final ICommonsList <XdmQName> aNames)
  {
    return aNames.contains (null) ? null : new CommonsHashSet <> (aNames);
  }

  private static void _finish (@NonNull final CompileContext aCtx)
  {
    final CompiledStylesheet aSheet = aCtx.getStylesheet ();
    final ICommonsList <Accumulator> aDecls = _getList (aSheet, KEY_DECLS);
    final ICommonsMap <XdmQName, Accumulator> aByName = new CommonsHashMap <> ();
    for (final Accumulator aAcc : aDecls)
    {
      final Accumulator aPrev = aByName.get (aAcc.name ());
      if (aPrev == null || aPrev.element ().getPrecedence () < aAcc.element ().getPrecedence ())
        aByName.put (aAcc.name (), aAcc);
    }
    // XTSE3350: two declarations with the same name and the highest precedence
    for (final Accumulator aAcc : aDecls)
    {
      final Accumulator aBest = aByName.get (aAcc.name ());
      if (aBest != aAcc && aBest.element ().getPrecedence () == aAcc.element ().getPrecedence ())
        throw XsltException.at (aBest.element (),
                                "XTSE3350",
                                "Duplicate xsl:accumulator " + aAcc.name ().getEQName () + " with the same import precedence");
    }
    aSheet.putExtensionData (KEY_BY_NAME, aByName);
    // Modes
    final ICommonsMap <Mode, ICommonsSet <XdmQName>> aModes = new CommonsHashMap <> ();
    for (final Mode aMode : aSheet.modes ().values ())
    {
      final XslElement aDecl = aMode.getDeclaration ();
      if (aDecl != null && aDecl.hasAttr ("use-accumulators"))
        registerUseAccumulators (aCtx, aDecl);
    }
    for (final UseRef aRef : AccumulatorExtension.<UseRef> _getList (aSheet, KEY_REFS))
      for (final XdmQName aName : aRef.names ())
        if (aName != null && !aByName.containsKey (aName))
          throw XsltException.at (aRef.element (), "XTSE3300", "use-accumulators names the unknown accumulator " + aName.getEQName ());
    for (final Mode aMode : aSheet.modes ().values ())
    {
      final XslElement aDecl = aMode.getDeclaration ();
      if (aDecl != null && aDecl.hasAttr ("use-accumulators"))
      {
        final ICommonsSet <XdmQName> aSet = _resolveSet (parseUseAccumulators (aCtx, aDecl));
        aModes.put (aMode, aSet == null ? new CommonsHashSet <> (aByName.keySet ()) : aSet);
      }
    }
    aSheet.putExtensionData (KEY_MODES, aModes);
  }

  /**
   * Parse a use-accumulators attribute.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element (xsl:mode, xsl:source-document, xsl:merge-source).
   * @return The names; a <code>null</code> entry stands for #all.
   */
  @NonNull
  public static ICommonsList <XdmQName> parseUseAccumulators (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsList <XdmQName> ret = new CommonsArrayList <> ();
    final String s = aEl.getAttr ("use-accumulators");
    if (s != null)
    {
      final String [] aTokens = XsltNames.tokenize (s);
      for (final String sToken : aTokens)
        if ("#all".equals (sToken))
        {
          if (aTokens.length > 1)
            throw XsltException.at (aEl, "XTSE0020", "#all must be used alone in use-accumulators");
          ret.add (null);
        }
        else
          ret.add (PackageModel.qualify (aEl, aCtx.resolveQName (aEl, sToken, false)));
    }
    return ret;
  }

  /**
   * Parse a use-accumulators attribute and register its names for the static
   * check that they exist (XTSE3300).
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The names; a <code>null</code> entry stands for #all.
   */
  @NonNull
  public static ICommonsList <XdmQName> registerUseAccumulators (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsList <XdmQName> ret = parseUseAccumulators (aCtx, aEl);
    AccumulatorExtension.<UseRef> _getList (aCtx.getStylesheet (), KEY_REFS).add (new UseRef (aEl, ret));
    return ret;
  }

  @NonNull
  private static RunData _getRunData (@NonNull final Engine aEngine)
  {
    return aEngine.getRunData (KEY_RUN, RunData::new);
  }

  /**
   * Restrict the accumulators applicable to a tree (use-accumulators of
   * xsl:source-document).
   *
   * @param aEngine
   *        The transformation.
   * @param aRoot
   *        The root of the tree.
   * @param aNames
   *        The applicable accumulators, a <code>null</code> entry for #all.
   * @param bStreamed
   *        <code>true</code> if the stylesheet asked for the tree to be
   *        streamed.
   * @return The previous restriction, to be passed to
   *         {@link #restoreTree(Engine, XdmNode, Object)}.
   */
  @Nullable
  public static Object restrictTree (@NonNull final Engine aEngine,
                                     @NonNull final XdmNode aRoot,
                                     @NonNull final ICommonsList <XdmQName> aNames,
                                     final boolean bStreamed)
  {
    final RunData aData = _getRunData (aEngine);
    return aData.m_aRestrictions.put (aRoot, new TreeRestriction (_resolveSet (aNames), bStreamed));
  }

  /**
   * @param aEngine
   *        The transformation.
   * @param aRoot
   *        The root of the tree.
   * @param aPrevious
   *        The return value of the matching
   *        {@link #restrictTree(Engine, XdmNode, ICommonsList, boolean)}.
   */
  public static void restoreTree (@NonNull final Engine aEngine, @NonNull final XdmNode aRoot, @Nullable final Object aPrevious)
  {
    final RunData aData = _getRunData (aEngine);
    if (aPrevious == null)
      aData.m_aRestrictions.remove (aRoot);
    else
      aData.m_aRestrictions.put (aRoot, (TreeRestriction) aPrevious);
  }

  @NonNull
  private static XdmSequence _coerce (@NonNull final Accumulator aAcc, @NonNull final XdmSequence aValue, @NonNull final XsltContext aCtx)
  {
    if (aAcc.as () == null)
      return aValue;
    try
    {
      return SequenceTypeMatcher.convert (aValue, aAcc.as (), aCtx.getDynamicContext (), "XPTY0004");
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aAcc.element ());
    }
  }

  @NonNull
  private static XdmSequence _applyRule (@NonNull final Accumulator aAcc,
                                         @NonNull final XdmNode aNode,
                                         @NonNull final XdmSequence aValue,
                                         final boolean bEnd,
                                         @NonNull final XsltContext aBase)
  {
    final XsltContext aFocus = aBase.withFocus (aNode, 1, 1);
    Rule aChosen = null;
    for (final Rule aRule : aAcc.rules ())
      if (aRule.end () == bEnd && aRule.match ().matches (aNode, aFocus))
        aChosen = aRule;
    if (aChosen == null)
      return aValue;
    final XsltContext aRuleCtx = aFocus.withVariable (VAR_VALUE, aValue);
    final XdmSequence aResult;
    if (aChosen.select () != null)
      aResult = aChosen.select ().evaluate (aRuleCtx);
    else
    {
      final SequenceOutput aOut = new SequenceOutput ();
      aChosen.body ().execute (aRuleCtx, aOut);
      aResult = aOut.getSequence ();
    }
    return _coerce (aAcc, aResult, aRuleCtx);
  }

  /**
   * Compute the values of an accumulator for a whole tree: an iterative
   * document-order walk; attributes and namespaces are not visited.
   */
  private static void _computeTree (@NonNull final Accumulator aAcc,
                                    @NonNull final XdmNode aRoot,
                                    @NonNull final TreeValues aValues,
                                    @NonNull final XsltContext aCaller)
  {
    final Engine aEngine = aCaller.getEngine ();
    // Rules run in temporary output state: no xsl:result-document (XTDE1480)
    final XsltContext aBase = Engine.withTemporaryOutputState (aEngine.createRootContext ().withState (XsltState.EMPTY));
    XdmSequence aValue;
    try
    {
      aValue = _coerce (aAcc, aAcc.initial ().evaluate (aBase.withFocus (aRoot, 1, 1)), aBase);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aAcc.element ());
    }
    // Stack entries: the node and whether its children were already pushed
    final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
    aStack.push (new Object [] { aRoot, Boolean.FALSE });
    while (!aStack.isEmpty ())
    {
      final Object [] aTop = aStack.peek ();
      final XdmNode aNode = (XdmNode) aTop[0];
      if (!((Boolean) aTop[1]).booleanValue ())
      {
        aEngine.step ();
        aValue = _applyRule (aAcc, aNode, aValue, false, aBase);
        aValues.m_aBefore.put (aNode, aValue);
        aTop[1] = Boolean.TRUE;
        for (int i = aNode.getChildCount () - 1; i >= 0; --i)
          aStack.push (new Object [] { aNode.getChildAtIndex (i), Boolean.FALSE });
      }
      else
      {
        aStack.pop ();
        aValue = _applyRule (aAcc, aNode, aValue, true, aBase);
        aValues.m_aAfter.put (aNode, aValue);
      }
    }
  }

  private static boolean _hasAccumulators (@NonNull final CompiledStylesheet aSheet)
  {
    final Object aDecls = aSheet.getExtensionData (KEY_DECLS);
    return aDecls instanceof final ICommonsList <?> aList && aList.isNotEmpty ();
  }

  /**
   * Remember that a copy keeps the accumulator values of its original
   * (XSLT 3.0 section 18.2.9): the subtree pairwise, and the ancestors of a
   * snapshot.
   */
  private static void _recordCopy (@NonNull final Engine aEngine, @NonNull final XdmNode aCopy, @NonNull final XdmNode aOriginal)
  {
    final Map <XdmNode, XdmNode> aOrigins = _getRunData (aEngine).m_aOrigins;
    final ArrayDeque <XdmNode []> aStack = new ArrayDeque <> ();
    aStack.push (new XdmNode [] { aCopy, aOriginal });
    while (!aStack.isEmpty ())
    {
      final XdmNode [] aPair = aStack.pop ();
      final XdmNode aC = aPair[0];
      final XdmNode aO = aPair[1];
      if (aC == aO || aC.getKind () != aO.getKind ())
        continue;
      aOrigins.put (aC, aO);
      if (aC.getChildCount () == aO.getChildCount ())
        for (int i = 0; i < aC.getChildCount (); ++i)
          aStack.push (new XdmNode [] { aC.getChildAtIndex (i), aO.getChildAtIndex (i) });
    }
    // Ancestors (fn:snapshot copies them too)
    XdmNode aC = aCopy.getParent ();
    XdmNode aO = aOriginal.getParent ();
    while (aC != null && aO != null && aC.getKind () == aO.getKind ())
    {
      aOrigins.put (aC, aO);
      aC = aC.getParent ();
      aO = aO.getParent ();
    }
  }

  @NonNull
  private static XdmNode _originOf (@NonNull final RunData aData, @NonNull final XdmNode aNode)
  {
    XdmNode ret = aNode;
    // Bounded: a copy of a copy of a copy ...
    for (int i = 0; i < 64; ++i)
    {
      final XdmNode aOrigin = aData.m_aOrigins.get (ret);
      if (aOrigin == null)
        break;
      ret = aOrigin;
    }
    return ret;
  }

  /**
   * Record the copies made by fn:copy-of or fn:snapshot.
   */
  private static void _recordCopies (@NonNull final DynamicContext aDyn, @NonNull final XdmSequence aInput, @NonNull final XdmSequence aResult)
  {
    final XsltContext aCtx = XsltContext.of (aDyn);
    if (aCtx == null || !_hasAccumulators (aCtx.getEngine ().getStylesheet ()) || aInput.size () != aResult.size ())
      return;
    for (int i = 0; i < aInput.size (); ++i)
      if (aInput.get (i) instanceof final XdmNode aOriginal && aResult.get (i) instanceof final XdmNode aCopy)
        _recordCopy (aCtx.getEngine (), aCopy, aOriginal);
  }

  private static void _wrapCopyFunction (@NonNull final FunctionLibrary aLib, @NonNull final String sLocalName, final int nArity)
  {
    final FunctionLibrary.FunctionDef aDef = aLib.getFunction (new XdmQName (XdmConstants.NS_FN, sLocalName), nArity);
    if (aDef == null)
      return;
    final IXPathFunction aImpl = aDef.getImpl ();
    aLib.registerParsed (aDef.getName (), aDef.getParamTypes (), aDef.getReturnType (), (aDyn, aArgs) -> {
      final XdmSequence ret = aImpl.call (aDyn, aArgs);
      final XdmSequence aInput = nArity == 0 ? aDyn.getContextItem () == null ? XdmSequence.EMPTY
                                                                             : XdmSequence.of (aDyn.getContextItem ())
                                             : aArgs.get (0);
      _recordCopies (aDyn, aInput, ret);
      return ret;
    }, aDef.isFocusDependent ());
  }

  @NonNull
  private static IInstruction _compileCopyOf (@NonNull final IInstructionCompiler aCore,
                                              @NonNull final CompileContext aCtx,
                                              @NonNull final XslElement aEl)
  {
    final String sCopyAccumulators = aEl.getAttr ("copy-accumulators");
    if (sCopyAccumulators == null || !XsltNames.isXsltTrue (sCopyAccumulators))
      return aCore.compile (aCtx, aEl);
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final boolean bCopyNamespaces = !aEl.hasAttr ("copy-namespaces") || XsltNames.isXsltTrue (aEl.getAttr ("copy-namespaces"));
    final String sStaticBase = aEl.getStaticBaseUri ();
    // Schema-aware: validation as in the core xsl:copy-of
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.COPY_OF);
    final boolean bKeepAnnotations = aValidation != null && !aValidation.isStrip ();
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      for (final IXdmItem aItem : aSelect.evaluate (aRtCtx))
        if (aItem instanceof final XdmNode aNode)
        {
          if (bKeepAnnotations && aValidation.isPreserve ())
            ValidationRequest.checkPreserveCopy (aNode, bCopyNamespaces);
          final XdmNode aCopy = NodeCopier.copyDeep (aNode, bCopyNamespaces, bKeepAnnotations);
          if (aCopy.isElement () && aCopy.getBaseUri () == null)
            aCopy.setBaseUri (sStaticBase);
          if (bKeepAnnotations)
            aValidation.apply (aCopy);
          _recordCopy (aRtCtx.getEngine (), aCopy, aNode);
          aRtOut.appendNew (aCopy);
        }
        else
          aRtOut.append (aItem);
    });
  }

  @NonNull
  private static XdmQName _resolveName (@NonNull final DynamicContext aDyn, @NonNull final String sName)
  {
    final String s = XsltNames.trimXmlWhitespace (sName);
    if (s.startsWith ("Q{") && XsltNames.isEQName (s))
    {
      final int nEnd = s.indexOf ('}');
      return new XdmQName (s.substring (2, nEnd), s.substring (nEnd + 1));
    }
    if (!XsltNames.isLexicalQName (s))
      throw new XPathException ("XTDE3340", "'" + sName + "' is not a valid accumulator name");
    final int nColon = s.indexOf (':');
    if (nColon < 0)
      return new XdmQName (s);
    final String sPrefix = s.substring (0, nColon);
    final String sUri = aDyn.getStaticContext ().getNamespaceUri (sPrefix);
    if (sUri == null)
      throw new XPathException ("XTDE3340", "The prefix of the accumulator name '" + sName + "' is not bound");
    return new XdmQName (sUri, s.substring (nColon + 1), sPrefix);
  }

  @Nullable
  private static Mode _getInitialMode (@NonNull final Engine aEngine)
  {
    final CompiledStylesheet aSheet = aEngine.getStylesheet ();
    final XdmQName aName = aEngine.getRequest ().getInitialMode ();
    if (aName == null || !aName.hasNamespace () && "#default".equals (aName.localName ()))
      return aSheet.getMode (aSheet.getDefaultModeName ());
    if (!aName.hasNamespace () && "#unnamed".equals (aName.localName ()))
      return aSheet.getMode (Mode.UNNAMED);
    return aSheet.getMode (aName);
  }

  private static boolean _isInitialMatchTree (@NonNull final Engine aEngine, @NonNull final XdmNode aRoot)
  {
    final XsltTransformRequest aRequest = aEngine.getRequest ();
    if (aRequest.getInitialTemplate () != null)
      return false;
    final XdmSequence aSelection = aRequest.getInitialMatchSelection ();
    if (aSelection != null)
    {
      for (final IXdmItem aItem : aSelection)
        if (aItem instanceof final XdmNode aNode && aNode.getRoot () == aRoot)
          return true;
      return false;
    }
    return aEngine.getGlobalContextItem () instanceof final XdmNode aGlobal && aGlobal.getRoot () == aRoot;
  }

  @SuppressWarnings ("unchecked")
  @NonNull
  private static XdmSequence _lookup (@NonNull final DynamicContext aDyn, @NonNull final String sName, final boolean bAfter)
  {
    final XsltContext aCtx = XsltContext.of (aDyn);
    if (aCtx == null)
      throw new XPathException ("XTDE3340", "There is no accumulator named '" + sName + "'");
    final CompiledStylesheet aSheet = aCtx.getEngine ().getStylesheet ();
    XdmQName aName = _resolveName (aDyn, sName);
    // Accumulators are local to a package
    if (aSheet.isMultiPackage ())
      aName = aSheet.getPackages ().qualifyForScope (aDyn.getStaticContext ().getFunctionLibrary (), aName);
    final ICommonsMap <XdmQName, Accumulator> aByName = (ICommonsMap <XdmQName, Accumulator>) aSheet.getExtensionData (KEY_BY_NAME);
    final Accumulator aAcc = aByName == null ? null : aByName.get (aName);
    if (aAcc == null)
      throw new XPathException ("XTDE3340", "There is no accumulator named '" + sName + "'");
    final IXdmItem aItem = aCtx.getContextItem ();
    if (aItem == null)
      throw new XPathException ("XPDY0002", "accumulator-" + (bAfter ? "after" : "before") + "() requires a context item");
    if (!(aItem instanceof final XdmNode aNode) ||
        aNode.getKind () == ENodeKind.ATTRIBUTE ||
        aNode.getKind () == ENodeKind.NAMESPACE)
      throw new XPathException ("XTTE3360",
                                "accumulator-" + (bAfter ? "after" : "before") + "() requires a context node other than an attribute or namespace node");
    final ICommonsMap <Mode, ICommonsSet <XdmQName>> aModes = (ICommonsMap <Mode, ICommonsSet <XdmQName>>) aSheet.getExtensionData (KEY_MODES);
    final RunData aData = _getRunData (aCtx.getEngine ());
    final XdmNode aOrigin = _originOf (aData, aNode);
    final XdmNode aRoot = aOrigin.getRoot ();
    final TreeRestriction aRestriction = aData.m_aRestrictions.get (aRoot);
    if (aRestriction != null)
    {
      if (aRestriction.names () != null && !aRestriction.names ().contains (aName))
        throw new XPathException ("XTDE3362", "The accumulator " + aName.getEQName () + " is not applicable to this document");
      if (aRestriction.streamed () && !aAcc.streamable ())
        throw new XPathException ("XTDE3362",
                                  "The accumulator " + aName.getEQName () + " is not streamable but the document is streamed");
    }
    else
      if (aModes != null)
      {
        // The tree of the initial match selection: the use-accumulators of the
        // initial mode apply (XSLT 3.0 section 18.2.2), none if it does not say
        if (_isInitialMatchTree (aCtx.getEngine (), aRoot))
        {
          final ICommonsSet <XdmQName> aAllowed = aModes.get (_getInitialMode (aCtx.getEngine ()));
          if (aAllowed == null || !aAllowed.contains (aName))
            throw new XPathException ("XTDE3362",
                                      "The accumulator " + aName.getEQName () + " is not applicable to the principal source document");
        }
      }
    final Map <XdmNode, TreeValues> aTrees = aData.m_aValues.computeIfAbsent (aAcc, k -> new IdentityHashMap <> ());
    TreeValues aValues = aTrees.get (aRoot);
    if (aValues == null)
    {
      aValues = new TreeValues ();
      aTrees.put (aRoot, aValues);
      try
      {
        _computeTree (aAcc, aRoot, aValues, aCtx);
        aValues.m_bDone = true;
      }
      catch (final RuntimeException ex)
      {
        aTrees.remove (aRoot);
        throw ex;
      }
    }
    final XdmSequence ret = (bAfter ? aValues.m_aAfter : aValues.m_aBefore).get (aOrigin);
    if (ret == null)
    {
      if (!aValues.m_bDone)
        throw new XPathException ("XTDE3400", "The accumulator " + aName.getEQName () + " depends on its own value");
      return XdmSequence.EMPTY;
    }
    return ret;
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerDeclaration ("accumulator", AccumulatorExtension::_compileAccumulator);
    aRegistry.registerFunctions (aLib -> {
      aLib.register (XdmConstants.NS_FN,
                     "accumulator-before",
                     "(xs:string) as item()*",
                     (aCtx, aArgs) -> _lookup (aCtx, ((XdmAtomic) aArgs.get (0).get (0)).getString (), false),
                     true);
      aLib.register (XdmConstants.NS_FN,
                     "accumulator-after",
                     "(xs:string) as item()*",
                     (aCtx, aArgs) -> _lookup (aCtx, ((XdmAtomic) aArgs.get (0).get (0)).getString (), true),
                     true);
    });
    aRegistry.registerFunctions (aLib -> {
      _wrapCopyFunction (aLib, "copy-of", 0);
      _wrapCopyFunction (aLib, "copy-of", 1);
      _wrapCopyFunction (aLib, "snapshot", 0);
      _wrapCopyFunction (aLib, "snapshot", 1);
    });
    final IInstructionCompiler aCoreCopyOf = aRegistry.getInstructionCompiler ("copy-of");
    if (aCoreCopyOf != null)
      aRegistry.replaceInstruction ("copy-of", (aCtx, aEl) -> _compileCopyOf (aCoreCopyOf, aCtx, aEl));
    aRegistry.registerCompileFinisher (AccumulatorExtension::_finish);
  }
}
