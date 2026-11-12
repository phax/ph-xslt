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
package com.helger.phoxtrot.xpath;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.EAxis;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.pattern.CompiledXsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPatternParser;
import com.helger.phoxtrot.xpath.stream.EPosture;
import com.helger.phoxtrot.xpath.stream.EStreamFunctionCategory;
import com.helger.phoxtrot.xpath.stream.IStreamFunctionResolver;
import com.helger.phoxtrot.xpath.stream.PostureSweep;
import com.helger.phoxtrot.xpath.stream.StreamContext;
import com.helger.phoxtrot.xpath.stream.StreamFunctionSignature;
import com.helger.phoxtrot.xpath.stream.XPathBurstAnalysis;
import com.helger.phoxtrot.xpath.stream.XPathStreamability;

/**
 * Tells whether an XPath expression or an XSLT match pattern can be evaluated while streaming, with the posture and
 * sweep analysis of XSLT 3.0 section 19 (the analysis the XSLT engine uses for its streamability rules). Meant for
 * hosts that decide upfront how to process a document, e.g. a Schematron validator: a rule context (pattern) that is
 * motionless can be matched on the current node, a test that is motionless needs only the context node, its
 * attributes and ancestors, a consuming one only the subtree of the context node read once, a burst one (see
 * {@link #setContextMode(EStreamContextMode)}) the buffered subtree, and anything free-ranging needs more than one
 * pass or the whole tree.
 * <ul>
 * <li>The context item is a streamed node by default (striding, {@link EStreamContextMode#STREAMED}). In
 * {@link EStreamContextMode#GROUNDED} it is a buffered node with its whole tree (every axis available). In
 * {@link EStreamContextMode#BURST} it is the root of a buffered copy of its subtree that keeps its place in the
 * document: an expression that is motionless when streamed stays motionless (unless it navigates from an external
 * node variable), one that needs only the subtree (and the ancestors as in streaming) is
 * {@link EStreamabilityVerdict#BURST}, anything else free-ranging, with the construct that leaves the subtree as reason
 * (<code>parent::x</code>, <code>//</code>, <code>/</code>, <code>preceding-sibling::x</code>, <code>key#2</code>). A
 * pattern is burst if the predicates of its last steps need only the subtree of the matched node.</li>
 * <li>Unlike section 19, a streamed context node is assumed to be no document node (in
 * {@link EStreamContextMode#STREAMED} and {@link EStreamContextMode#BURST}): the document element is then one of its
 * ancestors or itself, so <code>/x</code> is analysed like <code>ancestor-or-self::x</code> and
 * <code>/x/@a</code> or <code>name (/*)</code> are motionless, while <code>/x/y</code> stays free-ranging. A host
 * whose context node can be the document node (a rule context <code>/</code>) evaluates such a test once the start
 * tag of the document element has been read.</li>
 * <li>Variables are grounded (atomic values, or values computed in an earlier pass) unless declared as streamed nodes
 * with {@link #addStreamedVariable(XdmQName)}. A reference to a streamed variable is striding and motionless, and is
 * not streamable inside a construct that is evaluated repeatedly (a predicate, a loop body). In burst mode variables
 * declared with {@link #addExternalNodeVariable(XdmQName)} hold nodes outside the subtree.</li>
 * <li>Built-in functions are classified as the specification says. Any other function (an extension function, a
 * stylesheet function) is not streamable unless declared with
 * {@link #addFunction(XdmQName, EStreamFunctionCategory, List)}.</li>
 * <li>The analysis fails closed: what it does not know is not streamable.</li>
 * </ul>
 * For a verdict other than motionless the result carries a reason: the innermost sub-expression that has the same
 * verdict when analysed on its own (for a path, the first step from which the path has that verdict; for a pattern,
 * the predicate that is not motionless or positional, or the start of a rooted path). Sub-expressions are analysed
 * with the context of the whole, so the reason is a hint and not part of the specified analysis; it has no position
 * in the expression text.
 * <p>
 * An instance is a reusable configuration; it is not thread-safe while it is changed.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XPathStreamabilityAnalyzer
{
  private EStreamContextMode m_eContextMode = EStreamContextMode.STREAMED;
  private final ICommonsOrderedSet <XdmQName> m_aStreamedVariables = new CommonsLinkedHashSet <> ();
  private final ICommonsOrderedSet <XdmQName> m_aExternalNodeVariables = new CommonsLinkedHashSet <> ();
  private final ICommonsMap <String, StreamFunctionSignature> m_aFunctions = new CommonsHashMap <> ();

  public XPathStreamabilityAnalyzer ()
  {}

  @NonNull
  private static String _getFunctionKey (@NonNull final XdmQName aName, final int nArity)
  {
    return aName.getEQName () + "#" + nArity;
  }

  /**
   * @return A node test as written in XPath, e.g. <code>x</code>, <code>*</code>, <code>text()</code>.
   */
  @NonNull
  private static String _getNodeTestText (@Nullable final NodeTest aTest)
  {
    if (aTest == null)
      return "node()";
    if (aTest.kind () == ENodeTestKind.NAME)
    {
      if (aTest.anyName () || aTest.name () == null)
        return "*";
      if (aTest.anyNamespace ())
        return "*:" + aTest.name ().localName ();
      if (aTest.anyLocalName ())
        return aTest.name ().hasPrefix () ? aTest.name ().prefix () + ":*" : "Q{" + aTest.name ().namespaceUri () + "}*";
      return aTest.name ().getLexicalName ();
    }
    return switch (aTest.kind ())
    {
      case ANY_NODE -> "node()";
      case DOCUMENT -> "document-node()";
      default -> aTest.kind ().name ().toLowerCase (Locale.ROOT).replace ('_', '-') + "()";
    };
  }

  @NonNull
  private static XPathStreamabilityReason _expression (@NonNull final EStreamabilityConstruct eConstruct,
                                                       @NonNull final String sName)
  {
    return new XPathStreamabilityReason (eConstruct, sName);
  }

  /**
   * @return The constant name of an operator enum in lower case with hyphens, e.g. <code>integer-divide</code>.
   */
  @NonNull
  private static String _getEnumText (@NonNull final Enum <?> aEnum)
  {
    return aEnum.name ().toLowerCase (Locale.ROOT).replace ('_', '-');
  }

  /**
   * @return The reason naming an expression that is not a path.
   */
  @NonNull
  private static XPathStreamabilityReason _describe (@NonNull final IExpr aExpr)
  {
    return switch (aExpr)
    {
      case final IExpr.FunctionCallExpr e -> _expression (EStreamabilityConstruct.FUNCTION,
                                                         e.name ().getLexicalName () + "#" + e.args ().size ());
      case final IExpr.NamedFunctionRefExpr e -> _expression (EStreamabilityConstruct.FUNCTION,
                                                             e.name ().getLexicalName () + "#" + e.arity ());
      case final IExpr.VarRefExpr e -> _expression (EStreamabilityConstruct.VARIABLE, "$" + e.name ().getLexicalName ());
      case final IExpr.ContextItemExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, ".");
      case final IExpr.ForExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "for");
      case final IExpr.LetExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "let");
      case final IExpr.QuantifiedExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, e.every () ? "every" : "some");
      case final IExpr.IfExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "if");
      case final IExpr.BinaryExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "operator " + _getEnumText (e.op ()));
      case final IExpr.ComparisonExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "comparison");
      case final IExpr.SetExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, _getEnumText (e.op ()));
      case final IExpr.SimpleMapExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "simple map");
      case final IExpr.FilterExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "filter");
      default ->
      {
        // The record name without the "Expr" suffix, e.g. "SequenceExpr" -> "sequence"
        final String sName = aExpr.getClass ().getSimpleName ().replace ("Expr", "");
        yield _expression (EStreamabilityConstruct.EXPRESSION,
                           sName.isEmpty () ? "expression" : sName.toLowerCase (Locale.ROOT));
      }
    };
  }

  /**
   * @return The step without its fused <code>descendant::</code> step, which belongs to the following step and must
   *         not be used when the path ends here.
   */
  private static IExpr.@NonNull StepExpr _withoutFused (final IExpr.@NonNull StepExpr aStep)
  {
    if (aStep.fused () == null)
      return aStep;
    return new IExpr.StepExpr (aStep.axis (), aStep.axisExplicit (), aStep.test (), aStep.predicates (), aStep.postfix ());
  }

  /**
   * @return The resolver of the declared functions, over a copy of the declarations, or <code>null</code> if none is
   *         declared.
   */
  @Nullable
  private IStreamFunctionResolver _createFunctionResolver ()
  {
    if (m_aFunctions.isEmpty ())
      return null;
    final ICommonsMap <String, StreamFunctionSignature> aFunctions = m_aFunctions.getClone ();
    return (aName, nArity) -> aFunctions.get (_getFunctionKey (aName, nArity));
  }

  /**
   * @return The posture of the context item for the posture and sweep analysis: grounded in
   *         {@link EStreamContextMode#GROUNDED}, else striding (the burst analysis starts from the streamed one).
   */
  @NonNull
  private EPosture _getContextPosture ()
  {
    return m_eContextMode == EStreamContextMode.GROUNDED ? EPosture.GROUNDED : EPosture.STRIDING;
  }

  /**
   * @return The analysis context for one call with the given context posture. A new one each time, as the analysis
   *         records its definite flag in it.
   */
  @NonNull
  private StreamContext _createContext (@NonNull final EPosture eContextPosture)
  {
    final StreamContext ret = new StreamContext (eContextPosture);
    // A streamed context node is assumed to be no document node
    ret.setDocumentElementAncestor (m_eContextMode != EStreamContextMode.GROUNDED);
    if (m_aStreamedVariables.isNotEmpty ())
      ret.setStreamingParams (m_aStreamedVariables).setStreamingParamPosture (EPosture.STRIDING);
    final IStreamFunctionResolver aResolver = _createFunctionResolver ();
    if (aResolver != null)
      ret.setFunctionResolver (aResolver);
    return ret;
  }

  @NonNull
  private EStreamabilityVerdict _getVerdict (@NonNull final IExpr aExpr, @NonNull final EPosture eContextPosture)
  {
    return EStreamabilityVerdict.getFromPostureSweep (XPathStreamability.getStreamability (aExpr,
                                                                                          _createContext (eContextPosture)));
  }

  /**
   * @return The reason within a path that has the target verdict and no operand with it: the root, or the first step
   *         from which the path has the verdict.
   */
  @NonNull
  private XPathStreamabilityReason _findStepReason (final IExpr.@NonNull PathExpr aPath,
                                                    @NonNull final EStreamabilityVerdict eTarget,
                                                    @NonNull final EPosture eContextPosture)
  {
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    if (aPath.absolute () &&
        _getVerdict (new IExpr.PathExpr (null, true, new CommonsArrayList <> ()), eContextPosture) == eTarget)
      return new XPathStreamabilityReason (EStreamabilityConstruct.ROOT, "/");
    for (int k = 1; k <= aSteps.size (); ++k)
    {
      final ICommonsList <IExpr.StepExpr> aPrefix = new CommonsArrayList <> (aSteps.subList (0, k - 1));
      final IExpr.StepExpr aStep = aSteps.get (k - 1);
      aPrefix.add (_withoutFused (aStep));
      if (_getVerdict (new IExpr.PathExpr (aPath.start (), aPath.absolute (), aPrefix), eContextPosture) == eTarget)
      {
        if (aStep.postfix () != null)
          return _describe (aStep.postfix ());
        // The step of a "//" (or the same step written out)
        if (aStep.axis () == EAxis.DESCENDANT_OR_SELF &&
            aStep.test () != null &&
            aStep.test ().kind () == ENodeTestKind.ANY_NODE &&
            aStep.predicates ().isEmpty ())
          return new XPathStreamabilityReason (EStreamabilityConstruct.AXIS_STEP, "//");
        return new XPathStreamabilityReason (EStreamabilityConstruct.AXIS_STEP,
                                             aStep.axis ().getName () + "::" + _getNodeTestText (aStep.test ()));
      }
    }
    return new XPathStreamabilityReason (EStreamabilityConstruct.EXPRESSION, "path");
  }

  /**
   * Descend from the expression to the innermost operand that has the target verdict on its own, iteratively.
   */
  @NonNull
  private XPathStreamabilityReason _findReason (@NonNull final IExpr aExpr,
                                               @NonNull final EStreamabilityVerdict eTarget,
                                               @NonNull final EPosture eContextPosture)
  {
    IExpr aCur = aExpr;
    boolean bDescended = true;
    while (bDescended)
    {
      bDescended = false;
      for (final IExpr aChild : ExprWalker.getAllChildren (aCur))
        if (_getVerdict (aChild, eContextPosture) == eTarget)
        {
          aCur = aChild;
          bDescended = true;
          break;
        }
    }
    if (aCur instanceof final IExpr.PathExpr aPath)
      return _findStepReason (aPath, eTarget, eContextPosture);
    return _describe (aCur);
  }

  /**
   * @return The reason for a pattern that is not motionless: the first predicate that is positional or not
   *         motionless, else the start of a rooted path, else <code>null</code>.
   */
  @Nullable
  private XPathStreamabilityReason _findPatternReason (@NonNull final IExpr aPattern,
                                                       @NonNull final EPosture eContextPosture)
  {
    final ICommonsList <IExpr> aPredicates = new CommonsArrayList <> ();
    IExpr aRootedStart = null;
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aPattern);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.SetExpr e ->
        {
          aStack.push (e.right ());
          aStack.push (e.left ());
        }
        case final IExpr.PathExpr e ->
        {
          if (e.start () != null && aRootedStart == null)
            aRootedStart = e.start ();
          for (final IExpr.StepExpr aStep : e.steps ())
            aPredicates.addAll (aStep.predicates ());
        }
        case final IExpr.FilterExpr e -> aPredicates.addAll (e.predicates ());
        default ->
        {
          // Nothing to look at
        }
      }
    }
    for (final IExpr aPredicate : aPredicates)
    {
      if (!XPathStreamability.isNonPositionalPredicate (aPredicate))
        return new XPathStreamabilityReason (EStreamabilityConstruct.POSITIONAL_PREDICATE, "predicate");
      final EStreamabilityVerdict eVerdict = _getVerdict (aPredicate, eContextPosture);
      if (eVerdict != EStreamabilityVerdict.MOTIONLESS)
        return _findReason (aPredicate, eVerdict, eContextPosture);
    }
    return aRootedStart == null ? null : _describe (aRootedStart);
  }

  /**
   * @return The reason for a construct the burst analysis rejected: the root for an absolute path (<code>//</code>
   *         when it starts with one), the axis step, or the expression; <code>null</code> for an expression nested
   *         too deeply.
   */
  @Nullable
  private static XPathStreamabilityReason _describeBurstFailure (final XPathBurstAnalysis.@NonNull BurstFailure aFailure)
  {
    final IExpr.StepExpr aStep = aFailure.step ();
    if (aStep != null)
      return new XPathStreamabilityReason (EStreamabilityConstruct.AXIS_STEP,
                                           aStep.axis ().getName () + "::" + _getNodeTestText (aStep.test ()));
    final IExpr aExpr = aFailure.expression ();
    if (aExpr == null)
      return null;
    if (aExpr instanceof final IExpr.PathExpr aPath && aPath.absolute () && aPath.start () == null)
    {
      // "//x" is "/descendant-or-self::node()/x"
      if (!aPath.steps ().isEmpty ())
      {
        final IExpr.StepExpr aFirst = aPath.steps ().get (0);
        if (aFirst.axis () == EAxis.DESCENDANT_OR_SELF &&
            aFirst.test () != null &&
            aFirst.test ().kind () == ENodeTestKind.ANY_NODE &&
            aFirst.predicates ().isEmpty () &&
            aPath.steps ().size () > 1)
          return new XPathStreamabilityReason (EStreamabilityConstruct.AXIS_STEP, "//");
      }
      return new XPathStreamabilityReason (EStreamabilityConstruct.ROOT, "/");
    }
    return _describe (aExpr);
  }

  /**
   * @return <code>true</code> if the expression references a variable declared with
   *         {@link #addExternalNodeVariable(XdmQName)}, or a local variable of the same name.
   */
  private boolean _referencesExternalNodeVariable (@NonNull final IExpr aExpr)
  {
    if (m_aExternalNodeVariables.isEmpty ())
      return false;
    for (final XdmQName aName : ExprWalker.getAllVariableReferences (aExpr))
      if (m_aExternalNodeVariables.contains (aName))
        return true;
    return false;
  }

  /**
   * @return The burst verdict of an expression that is not motionless with a streamed context item, or that references
   *         an external node variable: if the burst analysis accepts it motionless or burst (with the reason of the
   *         streamed analysis), else free-ranging with the construct that leaves the subtree.
   */
  @NonNull
  private XPathStreamabilityResult _analyzeBurst (@NonNull final IExpr aExpr, @NonNull final PostureSweep aStreamedPS)
  {
    // No shortcut for an expression that is consuming when streamed: that analysis treats external node variables as
    // grounded values, which may be navigated from
    final XPathBurstAnalysis.BurstFailure aFailure = XPathBurstAnalysis.getBurstFailure (aExpr,
                                                                                         m_aStreamedVariables,
                                                                                         m_aExternalNodeVariables,
                                                                                         _createFunctionResolver ());
    if (aFailure == null)
    {
      final EStreamabilityVerdict eStreamed = EStreamabilityVerdict.getFromPostureSweep (aStreamedPS);
      if (eStreamed == EStreamabilityVerdict.MOTIONLESS)
        return new XPathStreamabilityResult (aStreamedPS, null);
      return new XPathStreamabilityResult (aStreamedPS,
                                           EStreamabilityVerdict.BURST,
                                           _findReason (aExpr, eStreamed, EPosture.STRIDING));
    }
    return new XPathStreamabilityResult (aStreamedPS, EStreamabilityVerdict.FREE_RANGING, _describeBurstFailure (aFailure));
  }

  /**
   * @return The path without the predicates of its last step, which go to the first list; the predicates of the other
   *         steps go to the second. A path whose last step is not an axis step is returned as it is, with all its
   *         predicates in the second list.
   */
  @NonNull
  private static IExpr _getWithoutLastPredicates (final IExpr.@NonNull PathExpr aPath,
                                                  @NonNull final ICommonsList <IExpr> aLastPredicates,
                                                  @NonNull final ICommonsList <IExpr> aOtherPredicates)
  {
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    final int nLast = aSteps.size () - 1;
    if (nLast < 0 || aSteps.get (nLast).postfix () != null)
    {
      for (final IExpr.StepExpr aStep : aSteps)
        aOtherPredicates.addAll (aStep.predicates ());
      return aPath;
    }
    final ICommonsList <IExpr.StepExpr> aNewSteps = new CommonsArrayList <> ();
    for (int i = 0; i < nLast; ++i)
    {
      final IExpr.StepExpr aStep = aSteps.get (i);
      aOtherPredicates.addAll (aStep.predicates ());
      // The step of a "//" carries a fused copy of the last step with its predicates
      aNewSteps.add (i == nLast - 1 ? _withoutFused (aStep) : aStep);
    }
    final IExpr.StepExpr aLast = aSteps.get (nLast);
    aLastPredicates.addAll (aLast.predicates ());
    aNewSteps.add (new IExpr.StepExpr (aLast.axis (),
                                       aLast.axisExplicit (),
                                       aLast.test (),
                                       new CommonsArrayList <> (),
                                       null));
    return new IExpr.PathExpr (aPath.start (), aPath.absolute (), aNewSteps);
  }

  /**
   * @return The burst verdict of a pattern: motionless if it is motionless when streamed, burst if it is motionless
   *         without the predicates of the last step of each alternative and these are non-positional and burst (the
   *         host matches the name on the streamed node and evaluates them on the buffered subtree of the candidate),
   *         else free-ranging. Navigation from external node variables is free-ranging in every predicate.
   */
  @NonNull
  private XPathStreamabilityResult _analyzeBurstPattern (@NonNull final IExpr aPattern, @NonNull final PostureSweep aPS)
  {
    final ICommonsList <IExpr> aAlternatives = new CommonsArrayList <> ();
    final ICommonsList <IExpr> aLastPredicates = new CommonsArrayList <> ();
    final ICommonsList <IExpr> aOtherPredicates = new CommonsArrayList <> ();
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aPattern);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.SetExpr e ->
        {
          aStack.push (e.right ());
          aStack.push (e.left ());
        }
        case final IExpr.PathExpr e when e.start () == null &&
                                         !e.absolute () &&
                                         e.steps ().size () == 1 &&
                                         e.steps ().get (0).postfix () != null &&
                                         e.steps ().get (0).predicates ().isEmpty () ->
          // Outer parentheses of a union alternative
          aStack.push (e.steps ().get (0).postfix ());
        case final IExpr.PathExpr e -> aAlternatives.add (_getWithoutLastPredicates (e,
                                                                                    aLastPredicates,
                                                                                    aOtherPredicates));
        case final IExpr.FilterExpr e when e.primary () instanceof IExpr.ContextItemExpr ->
        {
          // The predicate pattern ".[p]"
          aLastPredicates.addAll (e.predicates ());
          aAlternatives.add (e.primary ());
        }
        default -> aAlternatives.add (aCur);
      }
    }

    final boolean bMotionless = EStreamabilityVerdict.getFromPostureSweep (aPS) == EStreamabilityVerdict.MOTIONLESS;
    final ICommonsList <IExpr> aBurstChecked = new CommonsArrayList <> ();
    if (!bMotionless)
    {
      // Union, intersect and except are transparent to the pattern rules, so each alternative is checked on its own
      for (final IExpr aAlternative : aAlternatives)
      {
        final PostureSweep aAltPS = XPathStreamability.getPatternStreamability (aAlternative,
                                                                                _createContext (EPosture.STRIDING));
        if (EStreamabilityVerdict.getFromPostureSweep (aAltPS) != EStreamabilityVerdict.MOTIONLESS)
          return new XPathStreamabilityResult (aPS, _findPatternReason (aAlternative, EPosture.STRIDING));
      }
      for (final IExpr aPredicate : aLastPredicates)
        if (!XPathStreamability.isNonPositionalPredicate (aPredicate))
        {
          final XPathStreamabilityReason aReason = new XPathStreamabilityReason (EStreamabilityConstruct.POSITIONAL_PREDICATE,
                                                                                 "predicate");
          return new XPathStreamabilityResult (aPS, aReason);
        }
      aBurstChecked.addAll (aLastPredicates);
    }
    // The streamed analysis takes external node variables as grounded values, which may be navigated from
    if (_referencesExternalNodeVariable (aPattern))
    {
      aBurstChecked.clear ();
      aBurstChecked.addAll (aLastPredicates);
      aBurstChecked.addAll (aOtherPredicates);
    }
    for (final IExpr aPredicate : aBurstChecked)
    {
      final XPathBurstAnalysis.BurstFailure aFailure;
      aFailure = XPathBurstAnalysis.getPredicateBurstFailure (aPredicate,
                                                              m_aStreamedVariables,
                                                              m_aExternalNodeVariables,
                                                              _createFunctionResolver ());
      if (aFailure != null)
        return new XPathStreamabilityResult (aPS, EStreamabilityVerdict.FREE_RANGING, _describeBurstFailure (aFailure));
    }
    if (bMotionless)
      return new XPathStreamabilityResult (aPS, null);
    return new XPathStreamabilityResult (aPS,
                                         EStreamabilityVerdict.BURST,
                                         _findPatternReason (aPattern, EPosture.STRIDING));
  }

  /**
   * @return What the analysis assumes about the context item. {@link EStreamContextMode#STREAMED} by default.
   */
  @NonNull
  public EStreamContextMode getContextMode ()
  {
    return m_eContextMode;
  }

  /**
   * @param eContextMode
   *        What the analysis assumes about the context item. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer setContextMode (@NonNull final EStreamContextMode eContextMode)
  {
    ValueEnforcer.notNull (eContextMode, "ContextMode");
    m_eContextMode = eContextMode;
    return this;
  }

  /**
   * @return <code>true</code> if the context mode is {@link EStreamContextMode#STREAMED} (the default).
   */
  public boolean isContextStreamed ()
  {
    return m_eContextMode == EStreamContextMode.STREAMED;
  }

  /**
   * Shortcut for {@link #setContextMode(EStreamContextMode)}.
   *
   * @param bContextStreamed
   *        <code>true</code> for {@link EStreamContextMode#STREAMED} (striding, the default), <code>false</code> for
   *        {@link EStreamContextMode#GROUNDED} (a buffered node with its whole tree, or a value).
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer setContextStreamed (final boolean bContextStreamed)
  {
    return setContextMode (bContextStreamed ? EStreamContextMode.STREAMED : EStreamContextMode.GROUNDED);
  }

  /**
   * Declare a variable as bound to streamed nodes; in {@link EStreamContextMode#BURST} to nodes of the buffered
   * subtree. Every other variable is grounded.
   *
   * @param aName
   *        The variable name. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer addStreamedVariable (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aStreamedVariables.add (aName);
    return this;
  }

  /**
   * Declare a variable as bound to nodes outside the subtree of the context node, e.g. a value computed from the whole
   * document in an earlier pass. Only {@link EStreamContextMode#BURST} looks at it: comparing or atomising its value is
   * burst, navigating from its nodes is free-ranging (also where the expression is motionless otherwise). In the other
   * modes it is grounded like any undeclared variable.
   *
   * @param aName
   *        The variable name. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer addExternalNodeVariable (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aExternalNodeVariables.add (aName);
    return this;
  }

  /**
   * Declare how a function outside the <code>fn</code> namespace (an extension function or a function compiled from a
   * stylesheet) treats its arguments, as the <code>streamability</code> attribute of <code>xsl:function</code> does
   * (XSLT 3.0 section 19.8.5). A function that is not declared is not streamable. The declaration does not make the
   * function known to the static context.
   *
   * @param aName
   *        The function name. May not be <code>null</code>.
   * @param eCategory
   *        The streamability category; {@link EStreamFunctionCategory#UNCLASSIFIED} for a function that promises
   *        nothing (it is then streamable where its parameter types atomise streamed nodes). May not be
   *        <code>null</code>.
   * @param aParamTypes
   *        The declared types of the parameters in order (the arity is their number); a <code>null</code> entry stands
   *        for an absent type (<code>item()*</code>). May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer addFunction (@NonNull final XdmQName aName,
                                                 @NonNull final EStreamFunctionCategory eCategory,
                                                 @NonNull final List <SeqType> aParamTypes)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (eCategory, "Category");
    ValueEnforcer.notNull (aParamTypes, "ParamTypes");
    m_aFunctions.put (_getFunctionKey (aName, aParamTypes.size ()),
                      StreamFunctionSignature.createForParameterTypes (eCategory, aParamTypes));
    return this;
  }

  /**
   * Analyse a compiled expression.
   *
   * @param aXPath
   *        The expression. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  public XPathStreamabilityResult analyze (@NonNull final CompiledXPath aXPath)
  {
    ValueEnforcer.notNull (aXPath, "XPath");
    final IExpr aExpr = aXPath.getExpression ();
    final EPosture eContextPosture = _getContextPosture ();
    final PostureSweep aPS = XPathStreamability.getStreamability (aExpr, _createContext (eContextPosture));
    final EStreamabilityVerdict eVerdict = EStreamabilityVerdict.getFromPostureSweep (aPS);
    if (m_eContextMode == EStreamContextMode.BURST &&
        (eVerdict != EStreamabilityVerdict.MOTIONLESS || _referencesExternalNodeVariable (aExpr)))
    {
      // The streamed analysis takes external node variables as grounded values, which may be navigated from
      return _analyzeBurst (aExpr, aPS);
    }
    if (eVerdict == EStreamabilityVerdict.MOTIONLESS)
      return new XPathStreamabilityResult (aPS, null);
    return new XPathStreamabilityResult (aPS, _findReason (aExpr, eVerdict, eContextPosture));
  }

  /**
   * Compile and analyse an expression.
   *
   * @param sExpr
   *        The expression text. May not be <code>null</code>.
   * @param aStaticCtx
   *        The static context to compile it with. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   * @throws XPathException
   *         for static errors of the expression
   */
  @NonNull
  public XPathStreamabilityResult analyze (@NonNull final String sExpr, @NonNull final StaticContext aStaticCtx)
  {
    return analyze (XPathCompiler.compile (sExpr, aStaticCtx));
  }

  /**
   * Parse and analyse an XSLT match pattern (XSLT 3.0 section 19.8.10), e.g. the context of a Schematron rule. A
   * pattern is either motionless or free-ranging. Its predicates are analysed with the matched node as streamed
   * context, in {@link EStreamContextMode#GROUNDED} as grounded. In {@link EStreamContextMode#BURST} a pattern that is
   * not motionless is {@link EStreamabilityVerdict#BURST} if it is motionless without the predicates of the last step
   * of each alternative and these are non-positional and need only the subtree of the matched node (the host matches
   * the name on the streamed node, buffers the subtree of each candidate and evaluates the predicates on it);
   * navigation from an external node variable is free-ranging in any predicate.
   *
   * @param sPattern
   *        The pattern text. May not be <code>null</code>.
   * @param aStaticCtx
   *        The static context to parse it with. <code>fn:key</code> and <code>fn:current</code> must be registered in
   *        it if the pattern uses them. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   * @throws XPathException
   *         XTSE0340 for an invalid pattern, other static errors as the expression parser raises them
   */
  @NonNull
  public XPathStreamabilityResult analyzePattern (@NonNull final String sPattern, @NonNull final StaticContext aStaticCtx)
  {
    final XsltPattern aPattern = XsltPatternParser.parse (sPattern, aStaticCtx);
    final EPosture eContextPosture = _getContextPosture ();
    final PostureSweep aPS = XPathStreamability.getPatternStreamability (aPattern, _createContext (eContextPosture));
    if (m_eContextMode == EStreamContextMode.BURST && aPattern instanceof final CompiledXsltPattern aCompiled)
      return _analyzeBurstPattern (aCompiled.getPatternExpr (), aPS);
    XPathStreamabilityReason aReason = null;
    if (EStreamabilityVerdict.getFromPostureSweep (aPS) != EStreamabilityVerdict.MOTIONLESS &&
        aPattern instanceof final CompiledXsltPattern aCompiled)
      aReason = _findPatternReason (aCompiled.getPatternExpr (), eContextPosture);
    return new XPathStreamabilityResult (aPS, aReason);
  }
}
