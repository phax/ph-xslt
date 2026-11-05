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
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

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
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmDocumentNode;
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
import com.helger.xslt.stream.IStreamAccumulators;
import com.helger.xslt.stream.StreamVerdicts;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Accumulators (XSLT 3.0 chapter 18): the xsl:accumulator declaration and the functions
 * accumulator-before() and accumulator-after(). The values of an accumulator are computed for a
 * whole tree at its first use, in one iterative document-order walk, and kept per run.
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

  /**
   * One compiled xsl:accumulator-rule: the match pattern, whether it fires at
   * the end event (phase="end") instead of the start event, and either the
   * select expression or the contained sequence constructor (exactly one of
   * the two is non-<code>null</code>).
   */
  private record Rule (@NonNull CompiledPattern match,
                       boolean end,
                       @Nullable XPathExpr select,
                       @Nullable SequenceConstructor body)
  {}

  /**
   * One compiled xsl:accumulator declaration (XSLT 3.0 section 18.2.1): its
   * element (for the import precedence and error locations), the qualified
   * name, the initial-value expression, the optional declared type
   * (<code>null</code> means item()*), the streamable attribute and the rules
   * in document order.
   */
  private record Accumulator (@NonNull XslElement element,
                              @NonNull XdmQName name,
                              @NonNull XPathExpr initial,
                              @Nullable SeqType as,
                              boolean streamable,
                              @NonNull ICommonsList <Rule> rules)
  {}

  /**
   * A use-accumulators attribute seen during compilation: the element that
   * carries it and the names it lists (a <code>null</code> entry for #all),
   * checked against the declared accumulators in
   * {@link #_finish(CompileContext)}.
   */
  private record UseRef (@NonNull XslElement element, @NonNull ICommonsList <XdmQName> names)
  {}

  /** The values of one accumulator over one tree: before and after per node. */
  private static final class TreeValues
  {
    private final Map <IXdmNode, XdmSequence> m_aBefore = new IdentityHashMap <> ();
    private final Map <IXdmNode, XdmSequence> m_aAfter = new IdentityHashMap <> ();
    private boolean m_bDone;
    // Computed while the tree is read as a stream, for the live nodes only
    private boolean m_bStreamed;
    // The error that stopped the computation while streaming, raised by every
    // lookup of a node that has no value
    private XPathException m_aError;
    // While streaming: the live computation and the index of the accumulator
    // in it, to compute the value of the node being read on demand
    private StreamedValues m_aLive;
    private int m_nLiveIndex;
  }

  /**
   * The accumulator values of a node of a streamed document, taken when a copy
   * of it was made (XSLT 3.0 section 18.2.10): the original is dropped while
   * the copy may live on. A value not yet known then (the post-descent value
   * of an open ancestor of a snapshot) is asked from the original, which is
   * kept for that.
   */
  private record FrozenValues (@NonNull IXdmNode root,
                               @NonNull Map <Accumulator, XdmSequence []> values,
                               @Nullable IXdmNode live)
  {
    /**
     * @param aAcc
     *        The accumulator.
     * @param bAfter
     *        <code>true</code> for the post-descent value, <code>false</code>
     *        for the pre-descent value.
     * @return The value frozen at copy time, or <code>null</code> if the
     *         accumulator had no value for the original then (not computed
     *         for its document, or not yet known).
     */
    @Nullable
    XdmSequence get (@NonNull final Accumulator aAcc, final boolean bAfter)
    {
      final XdmSequence [] aPair = values.get (aAcc);
      return aPair == null ? null : aPair[bAfter ? 1 : 0];
    }
  }

  /** Restrictions of the accumulators applicable to a tree. */
  private record TreeRestriction (@Nullable ICommonsSet <XdmQName> names, boolean streamed)
  {}

  /** Per-run state. */
  private static final class RunData
  {
    private final ICommonsMap <Accumulator, Map <IXdmNode, TreeValues>> m_aValues = new CommonsHashMap <> ();
    private final Map <IXdmNode, TreeRestriction> m_aRestrictions = new IdentityHashMap <> ();
    // Nodes of copies that keep the accumulator values of their original: the
    // original node, or the values of a streamed one. Weak, so that a copy no
    // longer referenced is forgotten (IXdmNode has identity equality)
    private final Map <IXdmNode, Object> m_aOrigins = new WeakHashMap <> ();
  }

  /**
   * @param aEl
   *        An xsl:accumulator-rule element.
   * @return <code>true</code> if it has a child element or a text child that
   *         is not whitespace only, i.e. a non-empty sequence constructor.
   */
  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        return true;
    return false;
  }

  /**
   * Compile one xsl:accumulator-rule (XSLT 3.0 section 18.2.1): the match
   * pattern, the phase (trimmed; absent means start) and the select
   * expression or, without select, the contained sequence constructor. Both
   * are compiled with the extra variable <code>$value</code> in scope.
   *
   * @param aCtx
   *        Compile context of the declaration.
   * @param aEl
   *        The xsl:accumulator-rule element.
   * @return The compiled rule.
   * @throws XsltException
   *         XTSE0020 if phase is neither start nor end; XTSE0010 if both a
   *         select attribute and non-whitespace content are present (the
   *         content of the element is then not the permitted one, section
   *         18.2.1).
   */
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
      throw XsltException.at (aEl, "XTSE0010", "xsl:accumulator-rule has both a select attribute and content");
    final SequenceConstructor aBody = aSelect == null ? aRuleCtx.compileContent (aEl) : null;
    return new Rule (aMatch, bEnd, aSelect, aBody);
  }

  /**
   * @return The list stored as extension data of the stylesheet under the
   *         key, created and stored empty on first access.
   */
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

  /**
   * The declaration compiler of xsl:accumulator (XSLT 3.0 section 18.2.1):
   * compiles the name (qualified for its package), initial-value, as,
   * streamable and the rules, and appends the result to the list of all
   * declarations of the stylesheet. Which declaration of a name wins is
   * decided later by {@link #_finish(CompileContext)}.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:accumulator element.
   * @throws XsltException
   *         XTSE0020 if streamable is not an XSLT boolean; XTSE0010 if there
   *         is no xsl:accumulator-rule child.
   */
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

  /**
   * Compile finisher, run after all declarations are compiled:
   * <ol>
   * <li>picks the effective declaration per name, the one with the highest
   * import precedence (XSLT 3.0 section 18.2.8), and raises XTSE3350 if
   * another declaration of that name has the same precedence;</li>
   * <li>registers the use-accumulators attribute of every declared xsl:mode
   * and raises XTSE3300 for any name in any registered use-accumulators
   * (modes, xsl:source-document, xsl:merge-source) that names no
   * accumulator;</li>
   * <li>stores per mode with a use-accumulators attribute the applicable
   * names (all effective names for #all). A mode without the attribute gets
   * no entry, which means no accumulator applies to the initial match
   * selection in that mode (section 18.2.2).</li>
   * </ol>
   * The streamable="yes" requirement of section 18.2.2 for the named
   * accumulators is not checked: the W3C test suite uses non-streamable
   * accumulators in use-accumulators of unstreamed modes and documents and
   * expects them to apply.
   *
   * @param aCtx
   *        Compile context.
   */
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
                                "Duplicate xsl:accumulator " +
                                            aAcc.name ().getEQName () +
                                            " with the same import precedence");
    }
    aSheet.putExtensionData (KEY_BY_NAME, aByName);
    // Modes
    final ICommonsMap <Mode, ICommonsList <XdmQName>> aModeNames = new CommonsHashMap <> ();
    for (final Mode aMode : aSheet.modes ().values ())
    {
      final XslElement aDecl = aMode.getDeclaration ();
      if (aDecl != null && aDecl.hasAttr ("use-accumulators"))
        aModeNames.put (aMode, registerUseAccumulators (aCtx, aDecl));
    }
    for (final UseRef aRef : AccumulatorExtension.<UseRef> _getList (aSheet, KEY_REFS))
      for (final XdmQName aName : aRef.names ())
        if (aName != null && !aByName.containsKey (aName))
          throw XsltException.at (aRef.element (),
                                  "XTSE3300",
                                  "use-accumulators names the unknown accumulator " + aName.getEQName ());
    final ICommonsMap <Mode, ICommonsSet <XdmQName>> aModes = new CommonsHashMap <> ();
    for (final Map.Entry <Mode, ICommonsList <XdmQName>> aEntry : aModeNames.entrySet ())
    {
      final ICommonsSet <XdmQName> aSet = _resolveSet (aEntry.getValue ());
      aModes.put (aEntry.getKey (), aSet == null ? new CommonsHashSet <> (aByName.keySet ()) : aSet);
    }
    aSheet.putExtensionData (KEY_MODES, aModes);
  }

  /**
   * Parse a use-accumulators attribute: whitespace-separated EQNames, each
   * resolved and qualified for the package of the element, or the token
   * #all. An absent attribute gives an empty list (no accumulator applies).
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element (xsl:mode, xsl:source-document, xsl:merge-source).
   * @return The names; a <code>null</code> entry stands for #all.
   * @throws XsltException
   *         XTSE3300 if a token is no EQName, if #all is combined with other
   *         tokens or if a name occurs more than once (XSLT 3.0 section
   *         18.2.2); XTSE0280 for an unbound prefix.
   */
  @NonNull
  public static ICommonsList <XdmQName> parseUseAccumulators (@NonNull final CompileContext aCtx,
                                                              @NonNull final XslElement aEl)
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
            throw XsltException.at (aEl, "XTSE3300", "#all must be used alone in use-accumulators");
          ret.add (null);
        }
        else
        {
          if (!XsltNames.isEQName (sToken))
            throw XsltException.at (aEl, "XTSE3300", "'" + sToken + "' in use-accumulators is not an EQName");
          final XdmQName aName = PackageModel.qualify (aEl, aCtx.resolveQName (aEl, sToken, false));
          if (ret.contains (aName))
            throw XsltException.at (aEl,
                                    "XTSE3300",
                                    "use-accumulators names the accumulator " + aName.getEQName () + " more than once");
          ret.add (aName);
        }
    }
    return ret;
  }

  /**
   * Parse a use-accumulators attribute and register its names for the static check that they exist
   * (XTSE3300).
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The names; a <code>null</code> entry stands for #all.
   */
  @NonNull
  public static ICommonsList <XdmQName> registerUseAccumulators (@NonNull final CompileContext aCtx,
                                                                 @NonNull final XslElement aEl)
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
   * Restrict the accumulators applicable to a tree (use-accumulators of xsl:source-document).
   * Until {@link #restoreTree(Engine, IXdmNode, Object)}, accumulator-before()
   * and accumulator-after() raise XTDE3362 for a node of this tree when the
   * accumulator is not among the names, or when the tree is streamed and the
   * accumulator is not declared streamable (XSLT 3.0 section 18.2.2).
   *
   * @param aEngine
   *        The transformation.
   * @param aRoot
   *        The root of the tree.
   * @param aNames
   *        The applicable accumulators, a <code>null</code> entry for #all.
   * @param bStreamed
   *        <code>true</code> if the stylesheet asked for the tree to be streamed.
   * @return The previous restriction, to be passed to
   *         {@link #restoreTree(Engine, IXdmNode, Object)}.
   */
  @Nullable
  public static Object restrictTree (@NonNull final Engine aEngine,
                                     @NonNull final IXdmNode aRoot,
                                     @NonNull final ICommonsList <XdmQName> aNames,
                                     final boolean bStreamed)
  {
    final RunData aData = _getRunData (aEngine);
    return aData.m_aRestrictions.put (aRoot, new TreeRestriction (_resolveSet (aNames), bStreamed));
  }

  /**
   * Undo {@link #restrictTree(Engine, IXdmNode, ICommonsList, boolean)}:
   * reinstate the previous restriction of the tree, or remove the restriction
   * if there was none.
   *
   * @param aEngine
   *        The transformation.
   * @param aRoot
   *        The root of the tree.
   * @param aPrevious
   *        The return value of the matching
   *        {@link #restrictTree(Engine, IXdmNode, ICommonsList, boolean)}.
   */
  public static void restoreTree (@NonNull final Engine aEngine,
                                  @NonNull final IXdmNode aRoot,
                                  @Nullable final Object aPrevious)
  {
    final RunData aData = _getRunData (aEngine);
    if (aPrevious == null)
      aData.m_aRestrictions.remove (aRoot);
    else
      aData.m_aRestrictions.put (aRoot, (TreeRestriction) aPrevious);
  }

  /**
   * Convert a value of the initial-value expression or of a rule to the type
   * of the as attribute with the function conversion rules (XSLT 3.0 section
   * 18.2.1); without as the value is returned unchanged.
   *
   * @return The converted value.
   * @throws XsltException
   *         XPTY0004 (or another conversion error) located at the
   *         xsl:accumulator element.
   */
  @NonNull
  private static XdmSequence _coerce (@NonNull final Accumulator aAcc,
                                      @NonNull final XdmSequence aValue,
                                      @NonNull final XsltContext aCtx)
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

  /**
   * The delta function of the formal model (XSLT 3.0 section 18.2.4) for one
   * traversal event: among the rules of the given phase whose pattern matches
   * the node, the last one in document order is evaluated with a singleton
   * focus on the node and <code>$value</code> bound to the previous value,
   * and its result converted to the declared type. Without a matching rule
   * the previous value is returned unchanged.
   *
   * @param aAcc
   *        The accumulator.
   * @param aNode
   *        The node of the event.
   * @param aValue
   *        The value before the event.
   * @param bEnd
   *        <code>true</code> for the end event, <code>false</code> for the
   *        start event.
   * @param aBase
   *        The context to evaluate in.
   * @return The value after the event.
   */
  @NonNull
  private static XdmSequence _applyRule (@NonNull final Accumulator aAcc,
                                         @NonNull final IXdmNode aNode,
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
   * Compute the values of an accumulator for a whole tree: an iterative document-order walk;
   * attributes and namespaces are not visited.
   */
  private static void _computeTree (@NonNull final Accumulator aAcc,
                                    @NonNull final IXdmNode aRoot,
                                    @NonNull final TreeValues aValues,
                                    @NonNull final XsltContext aCaller)
  {
    final Engine aEngine = aCaller.getEngine ();
    // Rules run in temporary output state: no xsl:result-document (XTDE1480)
    final XsltContext aBase = Engine.withTemporaryOutputState (aEngine.createRootContext ()
                                                                      .withState (XsltState.EMPTY));
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
      final IXdmNode aNode = (IXdmNode) aTop[0];
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

  /**
   * The values of the applicable accumulators of a streamed document, computed
   * as it is read (a port of go-xslt's <code>strbAccSet</code>,
   * <code>stream_acc.go</code>): the rule application of
   * {@link #_computeTree(Accumulator, IXdmNode, TreeValues, XsltContext)}, at
   * the moment each node is live.
   */
  private static final class StreamedValues implements IStreamAccumulators
  {
    private final Engine m_aEngine;
    private final ICommonsList <Accumulator> m_aAccumulators;
    private final ICommonsList <TreeValues> m_aValues = new CommonsArrayList <> ();
    private final XdmSequence [] m_aCurrent;
    private final XsltContext m_aBase;
    // The node whose rules are being applied, the phase, and per accumulator
    // whether its rule for it is pending (0), running (1) or applied (2)
    private IXdmNode m_aNode;
    private boolean m_bEnd;
    private final int [] m_aState;

    /**
     * Set up the live computation: per accumulator a {@link TreeValues}
     * marked done and streamed, registered in the run data for the document
     * node so that lookups find it, and the current value set to the
     * initial value. An error of the initial-value expression is kept and
     * raised by the lookups (XSLT 3.0 section 18.2.5). Finally the start
     * event of the document node is applied.
     *
     * @param aCaller
     *        Context.
     * @param aRoot
     *        The live document node.
     * @param aAccumulators
     *        The applicable accumulators, in declaration order.
     */
    StreamedValues (@NonNull final XsltContext aCaller,
                    @NonNull final IXdmNode aRoot,
                    @NonNull final ICommonsList <Accumulator> aAccumulators)
    {
      m_aEngine = aCaller.getEngine ();
      m_aAccumulators = aAccumulators;
      m_aCurrent = new XdmSequence [aAccumulators.size ()];
      m_aState = new int [aAccumulators.size ()];
      // Rules run in temporary output state: no xsl:result-document (XTDE1480)
      m_aBase = Engine.withTemporaryOutputState (m_aEngine.createRootContext ().withState (XsltState.EMPTY));
      final RunData aData = _getRunData (m_aEngine);
      for (int i = 0; i < aAccumulators.size (); ++i)
      {
        final Accumulator aAcc = aAccumulators.get (i);
        final TreeValues aValues = new TreeValues ();
        aValues.m_bDone = true;
        aValues.m_bStreamed = true;
        aValues.m_aLive = this;
        aValues.m_nLiveIndex = i;
        try
        {
          m_aCurrent[i] = _coerce (aAcc, aAcc.initial ().evaluate (m_aBase.withFocus (aRoot, 1, 1)), m_aBase);
        }
        catch (final XPathException ex)
        {
          // Raised when a value is asked for, as in the buffered computation
          aValues.m_aError = XsltException.locate (ex, aAcc.element ());
        }
        aData.m_aValues.computeIfAbsent (aAcc, k -> new IdentityHashMap <> ()).put (aRoot, aValues);
        m_aValues.add (aValues);
      }
      // The document node is entered before anything is read
      enter (aRoot);
    }

    /**
     * Apply the rules of one accumulator to the current node and phase, once:
     * an accumulator already applied is skipped, and one reached again while
     * its own rule runs (through accumulator-before/after on the same node)
     * is a cycle. An accumulator that has failed before stays failed; a new
     * failure is stored and from then on raised by every lookup (XSLT 3.0
     * section 18.2.5) instead of being thrown here. On success the value is
     * recorded as the pre- or post-descent value of the node.
     *
     * @param nIndex
     *        Index of the accumulator.
     * @throws XPathException
     *         XTDE3400 for a cyclic dependency.
     */
    private void _applyOne (final int nIndex)
    {
      if (m_aState[nIndex] == 2)
        return;
      if (m_aState[nIndex] == 1)
        throw new XPathException ("XTDE3400",
                                  "The accumulator " + m_aAccumulators.get (nIndex).name ().getEQName () + " depends on its own value");
      final TreeValues aValues = m_aValues.get (nIndex);
      m_aState[nIndex] = 1;
      try
      {
        if (aValues.m_aError != null)
          return;
        m_aEngine.step ();
        try
        {
          m_aCurrent[nIndex] = _applyRule (m_aAccumulators.get (nIndex), m_aNode, m_aCurrent[nIndex], m_bEnd, m_aBase);
        }
        catch (final XPathException ex)
        {
          // From here on every lookup raises it
          aValues.m_aError = ex;
          return;
        }
        (m_bEnd ? aValues.m_aAfter : aValues.m_aBefore).put (m_aNode, m_aCurrent[nIndex]);
      }
      finally
      {
        m_aState[nIndex] = 2;
      }
    }

    /**
     * Apply one traversal event to all accumulators, in declaration order,
     * with the node and phase as the current ones while the rules run.
     *
     * @param aNode
     *        The node.
     * @param bEnd
     *        <code>true</code> for the end event.
     */
    private void _apply (@NonNull final IXdmNode aNode, final boolean bEnd)
    {
      m_aNode = aNode;
      m_bEnd = bEnd;
      Arrays.fill (m_aState, 0);
      try
      {
        // In declaration order; a rule reading the value of another
        // accumulator at the same node computes that one first
        for (int i = 0; i < m_aAccumulators.size (); ++i)
          _applyOne (i);
      }
      finally
      {
        m_aNode = null;
      }
    }

    /**
     * @return The value of an accumulator at the node whose rules are being
     *         applied, computed now if it was not yet, or <code>null</code> if
     *         the node and phase are not the current ones.
     */
    @Nullable
    XdmSequence computeNow (final int nIndex, @NonNull final IXdmNode aNode, final boolean bAfter)
    {
      if (aNode != m_aNode || bAfter != m_bEnd)
        return null;
      _applyOne (nIndex);
      final TreeValues aValues = m_aValues.get (nIndex);
      return (bAfter ? aValues.m_aAfter : aValues.m_aBefore).get (aNode);
    }

    public void enter (@NonNull final IXdmNode aNode)
    {
      _apply (aNode, false);
    }

    public void exit (@NonNull final IXdmNode aNode)
    {
      _apply (aNode, true);
    }

    /**
     * Remove the pre- and post-descent values of the node and of every node
     * of its subtree, for all accumulators (iterative walk).
     */
    public void forget (@NonNull final IXdmNode aNode)
    {
      final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
      aStack.push (aNode);
      while (!aStack.isEmpty ())
      {
        final IXdmNode aCur = aStack.pop ();
        for (final TreeValues aValues : m_aValues)
        {
          aValues.m_aBefore.remove (aCur);
          aValues.m_aAfter.remove (aCur);
        }
        for (int i = 0; i < aCur.getChildCount (); ++i)
          aStack.push (aCur.getChildAtIndex (i));
      }
    }
  }

  /**
   * @param aNames
   *        The applicable accumulator names, <code>null</code> for all.
   * @return The effective accumulators among them, in declaration order.
   */
  @SuppressWarnings ("unchecked")
  @NonNull
  private static ICommonsList <Accumulator> _getApplicable (@NonNull final CompiledStylesheet aSheet,
                                                            @Nullable final ICommonsSet <XdmQName> aNames)
  {
    final ICommonsList <Accumulator> ret = new CommonsArrayList <> ();
    final ICommonsMap <XdmQName, Accumulator> aByName = (ICommonsMap <XdmQName, Accumulator>) aSheet.getExtensionData (KEY_BY_NAME);
    if (aByName != null)
      for (final Accumulator aAcc : AccumulatorExtension.<Accumulator> _getList (aSheet, KEY_DECLS))
        if (aByName.get (aAcc.name ()) == aAcc && (aNames == null || aNames.contains (aAcc.name ())))
          ret.add (aAcc);
    return ret;
  }

  /**
   * @return The accumulator names applicable in the mode by its
   *         use-accumulators, or an empty set if it has none (or the
   *         stylesheet has no accumulator data).
   */
  @SuppressWarnings ("unchecked")
  @NonNull
  private static ICommonsSet <XdmQName> _getModeAccumulators (@NonNull final CompiledStylesheet aSheet,
                                                              @NonNull final Mode aMode)
  {
    final ICommonsMap <Mode, ICommonsSet <XdmQName>> aModes = (ICommonsMap <Mode, ICommonsSet <XdmQName>>) aSheet.getExtensionData (KEY_MODES);
    final ICommonsSet <XdmQName> ret = aModes == null ? null : aModes.get (aMode);
    return ret == null ? new CommonsHashSet <> () : ret;
  }

  /**
   * Decide whether a document with the given applicable accumulators can be
   * read as a stream: never without streamability verdicts (streaming not
   * claimed); always if no accumulator applies; never for a multi-package
   * stylesheet that has applicable accumulators (their names are qualified
   * per package scope); otherwise only if every applicable accumulator is
   * declared streamable and proven streamable by the analysis.
   *
   * @param aSheet
   *        The stylesheet.
   * @param aNames
   *        The applicable names, <code>null</code> for all.
   * @return <code>true</code> if the document can be streamed.
   */
  private static boolean _isStreamable (@NonNull final CompiledStylesheet aSheet,
                                        @Nullable final ICommonsSet <XdmQName> aNames)
  {
    final StreamVerdicts aVerdicts = aSheet.getStreamVerdicts ();
    if (aVerdicts == null)
      return false;
    final ICommonsList <Accumulator> aAccs = _getApplicable (aSheet, aNames);
    if (aAccs.isEmpty ())
      return true;
    // The names of a package are qualified per scope
    if (aSheet.isMultiPackage ())
      return false;
    for (final Accumulator aAcc : aAccs)
      if (!aAcc.streamable () || !aVerdicts.isAccumulatorStreamable (aAcc.name ()))
        return false;
    return true;
  }

  /**
   * @param aSheet
   *        The stylesheet.
   * @param aUseAccumulators
   *        The use-accumulators of an xsl:source-document, a <code>null</code>
   *        entry for #all.
   * @return <code>true</code> if every applicable accumulator is proven
   *         streamable, so that the document can be read as a stream.
   */
  public static boolean isStreamable (@NonNull final CompiledStylesheet aSheet,
                                      @NonNull final ICommonsList <XdmQName> aUseAccumulators)
  {
    return _isStreamable (aSheet, _resolveSet (aUseAccumulators));
  }

  /**
   * @param aSheet
   *        The stylesheet.
   * @param aMode
   *        The initial mode.
   * @return <code>true</code> if every accumulator applicable to the initial
   *         match selection is proven streamable.
   */
  public static boolean isStreamable (@NonNull final CompiledStylesheet aSheet, @NonNull final Mode aMode)
  {
    return _isStreamable (aSheet, _getModeAccumulators (aSheet, aMode));
  }

  /**
   * Start computing the applicable accumulators of a document read as a
   * stream. Call only after
   * {@link #isStreamable(CompiledStylesheet, ICommonsList)} said yes.
   *
   * @param aCaller
   *        Context.
   * @param aRoot
   *        The live document node.
   * @param aUseAccumulators
   *        The use-accumulators of the xsl:source-document.
   * @return The live computation, or <code>null</code> if no accumulator
   *         applies.
   */
  @Nullable
  public static IStreamAccumulators startStreamed (@NonNull final XsltContext aCaller,
                                                   @NonNull final IXdmNode aRoot,
                                                   @NonNull final ICommonsList <XdmQName> aUseAccumulators)
  {
    final ICommonsList <Accumulator> aAccs = _getApplicable (aCaller.getEngine ().getStylesheet (),
                                                             _resolveSet (aUseAccumulators));
    return aAccs.isEmpty () ? null : new StreamedValues (aCaller, aRoot, aAccs);
  }

  /**
   * Start computing the accumulators of the initial mode over the initial
   * match selection read as a stream. Call only after
   * {@link #isStreamable(CompiledStylesheet, Mode)} said yes.
   *
   * @param aCaller
   *        Context.
   * @param aRoot
   *        The live document node.
   * @param aMode
   *        The initial mode.
   * @return The live computation, or <code>null</code> if no accumulator
   *         applies.
   */
  @Nullable
  public static IStreamAccumulators startStreamed (@NonNull final XsltContext aCaller,
                                                   @NonNull final IXdmNode aRoot,
                                                   @NonNull final Mode aMode)
  {
    final CompiledStylesheet aSheet = aCaller.getEngine ().getStylesheet ();
    final ICommonsList <Accumulator> aAccs = _getApplicable (aSheet, _getModeAccumulators (aSheet, aMode));
    return aAccs.isEmpty () ? null : new StreamedValues (aCaller, aRoot, aAccs);
  }

  /**
   * @return <code>true</code> if the stylesheet declares at least one
   *         accumulator, so that copies need to be recorded.
   */
  private static boolean _hasAccumulators (@NonNull final CompiledStylesheet aSheet)
  {
    final Object aDecls = aSheet.getExtensionData (KEY_DECLS);
    return aDecls instanceof final ICommonsList <?> aList && aList.isNotEmpty ();
  }

  /**
   * @return What a copy of the node keeps: the node itself, or for a node of a
   *         streamed document its accumulator values, since the node is
   *         dropped while the copy may live on.
   */
  @NonNull
  private static Object _getOriginEntry (@NonNull final RunData aData, @NonNull final IXdmNode aOriginal)
  {
    final IXdmNode aRoot = aOriginal.getRoot ();
    if (!(aRoot instanceof final XdmDocumentNode aDoc) || !aDoc.isStreamed ())
      return aOriginal;
    final Map <Accumulator, XdmSequence []> aValues = new IdentityHashMap <> ();
    boolean bIncomplete = false;
    for (final Map.Entry <Accumulator, Map <IXdmNode, TreeValues>> aEntry : aData.m_aValues.entrySet ())
    {
      final TreeValues aTree = aEntry.getValue ().get (aRoot);
      if (aTree == null)
        continue;
      final XdmSequence aBefore = aTree.m_aBefore.get (aOriginal);
      final XdmSequence aAfter = aTree.m_aAfter.get (aOriginal);
      if (aBefore == null || aAfter == null)
        bIncomplete = true;
      aValues.put (aEntry.getKey (), new XdmSequence [] { aBefore, aAfter });
    }
    return new FrozenValues (aRoot, aValues, bIncomplete ? aOriginal : null);
  }

  /**
   * Remember that a copy keeps the accumulator values of its original (XSLT 3.0 section 18.2.10):
   * the subtree pairwise, and the ancestors of a snapshot.
   */
  private static void _recordCopy (@NonNull final Engine aEngine,
                                   @NonNull final IXdmNode aCopy,
                                   @NonNull final IXdmNode aOriginal)
  {
    final RunData aData = _getRunData (aEngine);
    final Map <IXdmNode, Object> aOrigins = aData.m_aOrigins;
    final ArrayDeque <IXdmNode []> aStack = new ArrayDeque <> ();
    aStack.push (new IXdmNode [] { aCopy, aOriginal });
    while (!aStack.isEmpty ())
    {
      final IXdmNode [] aPair = aStack.pop ();
      final IXdmNode aC = aPair[0];
      final IXdmNode aO = aPair[1];
      if (aC == aO || aC.getKind () != aO.getKind ())
        continue;
      aOrigins.put (aC, _getOriginEntry (aData, aO));
      if (aC.getChildCount () == aO.getChildCount ())
        for (int i = 0; i < aC.getChildCount (); ++i)
          aStack.push (new IXdmNode [] { aC.getChildAtIndex (i), aO.getChildAtIndex (i) });
    }
    // Ancestors (fn:snapshot copies them too)
    IXdmNode aC = aCopy.getParent ();
    IXdmNode aO = aOriginal.getParent ();
    while (aC != null && aO != null && aC.getKind () == aO.getKind ())
    {
      aOrigins.put (aC, _getOriginEntry (aData, aO));
      aC = aC.getParent ();
      aO = aO.getParent ();
    }
  }

  /**
   * @return The original of a copy (the node itself if it is none), or the
   *         {@link FrozenValues} of a streamed original.
   */
  @NonNull
  private static Object _originOf (@NonNull final RunData aData, @NonNull final IXdmNode aNode)
  {
    Object ret = aNode;
    // Bounded: a copy of a copy of a copy ...
    for (int i = 0; i < 64 && ret instanceof final IXdmNode aCur; ++i)
    {
      final Object aOrigin = aData.m_aOrigins.get (aCur);
      if (aOrigin == null)
        break;
      ret = aOrigin;
    }
    return ret;
  }

  /**
   * Record the copies made by fn:copy-of or fn:snapshot.
   */
  private static void _recordCopies (@NonNull final DynamicContext aDyn,
                                     @NonNull final XdmSequence aInput,
                                     @NonNull final XdmSequence aResult)
  {
    final XsltContext aCtx = XsltContext.of (aDyn);
    if (aCtx == null || !_hasAccumulators (aCtx.getEngine ().getStylesheet ()) || aInput.size () != aResult.size ())
      return;
    for (int i = 0; i < aInput.size (); ++i)
      if (aInput.get (i) instanceof final IXdmNode aOriginal && aResult.get (i) instanceof final IXdmNode aCopy)
        _recordCopy (aCtx.getEngine (), aCopy, aOriginal);
  }

  /**
   * Replace a built-in fn:copy-of or fn:snapshot of the given arity in the
   * library by a wrapper that calls the original and then records the
   * resulting copies (the context item as input for arity 0), so that the
   * copies keep the accumulator values of their originals (XSLT 3.0 section
   * 18.2.10). Does nothing if the function is not in the library.
   *
   * @param aLib
   *        The function library of the stylesheet.
   * @param sLocalName
   *        "copy-of" or "snapshot".
   * @param nArity
   *        0 or 1.
   */
  private static void _wrapCopyFunction (@NonNull final FunctionLibrary aLib,
                                         @NonNull final String sLocalName,
                                         final int nArity)
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

  /**
   * The replacement compiler of xsl:copy-of. Without
   * copy-accumulators="yes" the core compiler is used unchanged. With it the
   * instruction is compiled here: each selected node is deep-copied (with
   * copy-namespaces and the validation of the core xsl:copy-of, including
   * the XTTE0950 check of validation="preserve"), a copied element
   * without base URI gets the static base URI of the instruction, and the
   * copy is recorded to keep the accumulator values of its original (XSLT
   * 3.0 section 18.2.10). Atomic and function items are appended unchanged.
   *
   * @param aCore
   *        The core xsl:copy-of compiler.
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:copy-of element.
   * @return The compiled instruction.
   */
  @NonNull
  private static IInstruction _compileCopyOf (@NonNull final IInstructionCompiler aCore,
                                              @NonNull final CompileContext aCtx,
                                              @NonNull final XslElement aEl)
  {
    final String sCopyAccumulators = aEl.getAttr ("copy-accumulators");
    if (sCopyAccumulators == null || !XsltNames.isXsltTrue (sCopyAccumulators))
      return aCore.compile (aCtx, aEl);
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final boolean bCopyNamespaces = !aEl.hasAttr ("copy-namespaces") ||
                                    XsltNames.isXsltTrue (aEl.getAttr ("copy-namespaces"));
    final String sStaticBase = aEl.getStaticBaseUri ();
    // Schema-aware: validation as in the core xsl:copy-of
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.COPY_OF);
    final boolean bKeepAnnotations = aValidation != null && !aValidation.isStrip ();
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      for (final IXdmItem aItem : aSelect.evaluate (aRtCtx))
        if (aItem instanceof final IXdmNode aNode)
        {
          if (bKeepAnnotations && aValidation.isPreserve ())
            ValidationRequest.checkPreserveCopy (aNode, bCopyNamespaces);
          final IXdmNode aCopy = NodeCopier.copyDeep (aNode, bCopyNamespaces, bKeepAnnotations);
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

  /**
   * Resolve the name argument of accumulator-before()/accumulator-after()
   * (XSLT 3.0 section 18.2.6): after trimming XML whitespace, a URI-qualified
   * name <code>Q{uri}local</code>, an unprefixed name (no namespace), or a
   * prefixed name resolved against the static namespaces of the call.
   *
   * @param aDyn
   *        Dynamic context of the call.
   * @param sName
   *        The argument string.
   * @return The expanded name, not yet qualified for a package.
   * @throws XPathException
   *         XTDE3340 if the string is not a lexical QName or EQName or its
   *         prefix is not bound.
   */
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

  /**
   * @return The initial mode of the run: the default mode of the stylesheet
   *         when the request names none or #default, the unnamed mode for
   *         #unnamed, otherwise the named mode; <code>null</code> if no such
   *         mode exists.
   */
  @Nullable
  private static Mode _getInitialMode (@NonNull final Engine aEngine)
  {
    final CompiledStylesheet aSheet = aEngine.getStylesheet ();
    final XdmQName aName = aEngine.getRequest ().getInitialMode ();
    if (aName == null || (!aName.hasNamespace () && "#default".equals (aName.localName ())))
      return aSheet.getMode (aSheet.getDefaultModeName ());
    if (!aName.hasNamespace () && "#unnamed".equals (aName.localName ()))
      return aSheet.getMode (Mode.UNNAMED);
    return aSheet.getMode (aName);
  }

  /**
   * @return <code>true</code> if the tree with this root holds nodes of the
   *         initial match selection: never when the run starts with an
   *         initial template; otherwise if an item of the explicit initial
   *         match selection is a node of the tree, or, without one, if the
   *         global context item is a node of the tree.
   */
  private static boolean _isInitialMatchTree (@NonNull final Engine aEngine, @NonNull final IXdmNode aRoot)
  {
    final XsltTransformRequest aRequest = aEngine.getRequest ();
    if (aRequest.getInitialTemplate () != null)
      return false;
    final XdmSequence aSelection = aRequest.getInitialMatchSelection ();
    if (aSelection != null)
    {
      for (final IXdmItem aItem : aSelection)
        if (aItem instanceof final IXdmNode aNode && aNode.getRoot () == aRoot)
          return true;
      return false;
    }
    return aEngine.getGlobalContextItem () instanceof final IXdmNode aGlobal && aGlobal.getRoot () == aRoot;
  }

  /**
   * The implementation of accumulator-before() and accumulator-after() (XSLT
   * 3.0 sections 18.2.6 and 18.2.7): the pre- or post-descent value of the
   * named accumulator at the context node.
   * <ol>
   * <li>The name is resolved (qualified for the package scope in a
   * multi-package stylesheet) and must name an effective accumulator.</li>
   * <li>A node made by copying (copy-of, snapshot, xsl:copy-of with
   * copy-accumulators) stands for its original (section 18.2.10); for a
   * copy of a streamed node the values frozen at copy time are used.</li>
   * <li>Applicability (section 18.2.2): a tree restricted by
   * {@link #restrictTree(Engine, IXdmNode, ICommonsList, boolean)} allows
   * only its names, and only streamable accumulators when streamed; an
   * unrestricted tree of the initial match selection allows the
   * use-accumulators of the initial mode (none without one); any other tree
   * allows all.</li>
   * <li>For an unstreamed tree all values of the accumulator are computed
   * on first use with
   * {@link #_computeTree(Accumulator, IXdmNode, TreeValues, XsltContext)}
   * and kept for the run; a failed computation is discarded and the error
   * propagated. For a streamed tree only the values computed while reading
   * exist; the value of the node whose rules are running is computed on
   * demand.</li>
   * </ol>
   *
   * @param aDyn
   *        Dynamic context of the call.
   * @param sName
   *        The accumulator name argument.
   * @param bAfter
   *        <code>true</code> for accumulator-after().
   * @return The value, empty if the node has none in an unstreamed tree.
   * @throws XPathException
   *         XTDE3340 for an unknown name (also outside an XSLT context);
   *         XTDE3350 without context item; XTTE3360 if the context item is
   *         not a node or is an attribute or namespace node; XTDE3362 if the
   *         accumulator is not applicable or not computed for a streamed
   *         document; the stored error of a failed initial-value or rule
   *         (section 18.2.5); XTSE3430 if a streamed node has no such value
   *         (it was not available while reading); XTDE3400 if the value is
   *         requested while its own computation runs.
   */
  @SuppressWarnings ("unchecked")
  @NonNull
  private static XdmSequence _lookup (@NonNull final DynamicContext aDyn,
                                      @NonNull final String sName,
                                      final boolean bAfter)
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
    // XSLT 3.0 section 18.2.4; also the case of a focus saved in a function
    // item over a streamed node, which is saved absent (section 10.3.6)
    if (aItem == null)
      throw new XPathException ("XTDE3350",
                                "accumulator-" + (bAfter ? "after" : "before") + "() requires a context item");
    if (!(aItem instanceof final IXdmNode aNode) ||
        aNode.getKind () == ENodeKind.ATTRIBUTE ||
        aNode.getKind () == ENodeKind.NAMESPACE)
      throw new XPathException ("XTTE3360",
                                "accumulator-" +
                                            (bAfter ? "after" : "before") +
                                            "() requires a context node other than an attribute or namespace node");
    final ICommonsMap <Mode, ICommonsSet <XdmQName>> aModes = (ICommonsMap <Mode, ICommonsSet <XdmQName>>) aSheet.getExtensionData (KEY_MODES);
    final RunData aData = _getRunData (aCtx.getEngine ());
    final Object aOriginEntry = _originOf (aData, aNode);
    final FrozenValues aFrozen = aOriginEntry instanceof final FrozenValues aFV ? aFV : null;
    // null: only the values frozen in a copy are known
    final IXdmNode aOrigin = aFrozen != null ? aFrozen.live () : (IXdmNode) aOriginEntry;
    final IXdmNode aRoot = aFrozen != null ? aFrozen.root () : aOrigin.getRoot ();
    final TreeRestriction aRestriction = aData.m_aRestrictions.get (aRoot);
    if (aRestriction != null)
    {
      if (aRestriction.names () != null && !aRestriction.names ().contains (aName))
        throw new XPathException ("XTDE3362",
                                  "The accumulator " + aName.getEQName () + " is not applicable to this document");
      if (aRestriction.streamed () && !aAcc.streamable ())
        throw new XPathException ("XTDE3362",
                                  "The accumulator " +
                                              aName.getEQName () +
                                              " is not streamable but the document is streamed");
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
                                      "The accumulator " +
                                                  aName.getEQName () +
                                                  " is not applicable to the principal source document");
        }
      }
    if (aFrozen != null)
    {
      final XdmSequence aFrozenValue = aFrozen.get (aAcc, bAfter);
      if (aFrozenValue != null)
        return aFrozenValue;
    }
    final Map <IXdmNode, TreeValues> aTrees = aData.m_aValues.computeIfAbsent (aAcc, k -> new IdentityHashMap <> ());
    TreeValues aValues = aTrees.get (aRoot);
    if (aValues == null)
    {
      // A document read as a stream holds only a part of itself: only the
      // accumulators computed while it is read have values
      if (aRoot instanceof final XdmDocumentNode aDoc && aDoc.isStreamed ())
        throw new XPathException ("XTDE3362",
                                  "The accumulator " + aName.getEQName () + " is not computed for the streamed document");
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
    XdmSequence ret = aOrigin == null ? null : (bAfter ? aValues.m_aAfter : aValues.m_aBefore).get (aOrigin);
    // A rule reading another accumulator at the node being read
    if (ret == null && aOrigin != null && aValues.m_aLive != null)
      ret = aValues.m_aLive.computeNow (aValues.m_nLiveIndex, aOrigin, bAfter);
    if (ret == null && aValues.m_aError != null)
      throw aValues.m_aError;
    if (ret == null && aValues.m_bStreamed)
      throw new XPathException ("XTSE3430",
                                "The " +
                                          (bAfter ? "post" : "pre") +
                                          "-descent value of the accumulator " +
                                          aName.getEQName () +
                                          " is not available for this node of the streamed document");
    if (ret == null)
    {
      if (!aValues.m_bDone)
        throw new XPathException ("XTDE3400", "The accumulator " + aName.getEQName () + " depends on its own value");
      return XdmSequence.EMPTY;
    }
    return ret;
  }

  /**
   * Register xsl:accumulator, the functions accumulator-before() and
   * accumulator-after(), the wrappers of fn:copy-of and fn:snapshot (both
   * arities) that record copies, the replacement of the core xsl:copy-of
   * compiler for copy-accumulators="yes", and the compile finisher.
   */
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
