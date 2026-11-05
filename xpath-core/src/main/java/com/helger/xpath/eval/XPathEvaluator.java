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
package com.helger.xpath.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.ExprWalker;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.fn.FnBoolean;
import com.helger.xpath.fn.FnSequence;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.fn.IXPathFunction;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Tree-walking evaluator for {@link IExpr} (a port of go-xslt's <code>eval.go</code> and
 * <code>axes.go</code>). Owner: evaluator workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathEvaluator
{
  /**
   * The largest integer range (<code>a to b</code>) that is materialised; longer ranges are an
   * implementation limit (XPDY0130), as in go-xslt.
   */
  public static final long MAX_RANGE_SIZE = 10_000_000L;

  private static final XdmSequence TRUE_SEQ = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE_SEQ = XdmSequence.of (XdmAtomic.FALSE);

  @PresentForCodeCoverage
  private static final XPathEvaluator INSTANCE = new XPathEvaluator ();

  private XPathEvaluator ()
  {}

  @NonNull
  private static XdmSequence _bool (final boolean b)
  {
    return b ? TRUE_SEQ : FALSE_SEQ;
  }

  private static boolean _ebv (@NonNull final XdmSequence aSeq)
  {
    return Atomizer.getEffectiveBooleanValue (aSeq);
  }

  /**
   * @return The context item of the focus, never <code>null</code>.
   * @throws XPathException
   *         XPDY0002 if the context item is absent
   */
  @NonNull
  private static IXdmItem _getContextItem (@NonNull final DynamicContext aCtx)
  {
    final IXdmItem ret = aCtx.getContextItem ();
    if (ret == null)
      throw XPathException.noContext ("The context item is absent");
    return ret;
  }

  /**
   * @return The string value of an atomic value as <code>fn:string</code> gives it: the string
   *         itself for string-like values, the canonical lexical form otherwise.
   */
  @NonNull
  private static String _getStringValue (@NonNull final XdmAtomic aValue)
  {
    return aValue.isStringLike () ? aValue.getString () : aValue.getLexical ();
  }

  /**
   * @return <code>true</code> if the numeric value equals the 1-based position exactly.
   */
  private static boolean _isPosition (@NonNull final XdmAtomic aValue, final int nPosition)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 && aBigInt.intValue () == nPosition;
    if (aPayload instanceof final BigDecimal aBigDec)
      return aBigDec.compareTo (BigDecimal.valueOf (nPosition)) == 0;
    return aValue.getAsDouble () == nPosition;
  }

  /**
   * @return The position a numeric literal predicate selects, or -1 if it selects none (fractional,
   *         NaN, out of range).
   */
  private static int _getLiteralPosition (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 ? aBigInt.intValue () : -1;
    if (aPayload instanceof final BigDecimal aBigDec)
    {
      final BigDecimal aStripped = aBigDec.stripTrailingZeros ();
      if (aStripped.scale () > 0 || aStripped.compareTo (BigDecimal.valueOf (Integer.MAX_VALUE)) > 0)
        return -1;
      return aStripped.intValue ();
    }
    final double d = aValue.getAsDouble ();
    if (d != Math.rint (d) || d < 1 || d > Integer.MAX_VALUE)
      return -1;
    return (int) d;
  }

  /**
   * Evaluate a predicate for one item: a single number is compared with the context position,
   * anything else gives its effective boolean value. A predicate that can only be a node
   * sequence is evaluated with early exit ({@link #_tryEarlyEbv(IExpr, DynamicContext, boolean)}).
   */
  private static boolean _isPredicateTrue (@NonNull final IExpr aPredicate,
                                           @NonNull final IXdmItem aItem,
                                           final int nPosition,
                                           final int nSize,
                                           @NonNull final DynamicContext aCtx)
  {
    final DynamicContext aFocus = aCtx.withFocus (aItem, nPosition, nSize);
    final Boolean aEarly = _tryEarlyEbv (aPredicate, aFocus, true);
    if (aEarly != null)
      return aEarly.booleanValue ();
    final XdmSequence aValue = evaluateInternal (aPredicate, aFocus);
    if (aValue.isSingleton () && aValue.get (0) instanceof final XdmAtomic aAtomic && aAtomic.isNumeric ())
      return _isPosition (aAtomic, nPosition);
    return _ebv (aValue);
  }

  /**
   * Find the first item of a sequence (in its current order) for which a predicate is true, with
   * the context positions and size of the whole sequence.
   *
   * @param nFrom
   *        The 0-based index to start the search at.
   * @return The 0-based index of the item, or -1 if no item from <code>nFrom</code> on passes.
   */
  private static int _findPassing (@NonNull final XdmSequence aSeq,
                                   @NonNull final IExpr aPredicate,
                                   final int nFrom,
                                   @NonNull final DynamicContext aCtx)
  {
    final int nSize = aSeq.size ();
    if (aPredicate instanceof final IExpr.LiteralExpr aLiteral && aLiteral.value ().isNumeric ())
    {
      final int nPos = _getLiteralPosition (aLiteral.value ());
      return nPos > nFrom && nPos <= nSize ? nPos - 1 : -1;
    }
    for (int i = nFrom; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      if (_isPredicateTrue (aPredicate, aSeq.get (i), i + 1, nSize, aCtx))
        return i;
    }
    return -1;
  }

  /**
   * Apply one predicate to a sequence in its current order (axis order for an axis step).
   */
  @NonNull
  private static XdmSequence _applyPredicate (@NonNull final XdmSequence aSeq,
                                              @NonNull final IExpr aPredicate,
                                              @NonNull final DynamicContext aCtx)
  {
    final int nSize = aSeq.size ();
    if (nSize == 0)
      return aSeq;
    if (aPredicate instanceof final IExpr.LiteralExpr aLiteral && aLiteral.value ().isNumeric ())
    {
      // A numeric literal selects one position without evaluating per item
      final int nPos = _getLiteralPosition (aLiteral.value ());
      return nPos >= 1 && nPos <= nSize ? XdmSequence.of (aSeq.get (nPos - 1)) : XdmSequence.EMPTY;
    }
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      final IXdmItem aItem = aSeq.get (i);
      if (_isPredicateTrue (aPredicate, aItem, i + 1, nSize, aCtx))
        aBuilder.add (aItem);
    }
    return aBuilder.build ();
  }

  /**
   * Apply the predicates one after the other (XPath 3.1 sections 3.2.1 and 3.3.3), each to the
   * complete result of the one before, so that the positions of each predicate count within that
   * result.
   *
   * @return The items that pass all predicates in their current order; the input itself without
   *         predicates.
   */
  @NonNull
  private static XdmSequence _applyPredicates (@NonNull final XdmSequence aSeq,
                                               @NonNull final List <IExpr> aPredicates,
                                               @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = aSeq;
    for (final IExpr aPredicate : aPredicates)
      ret = _applyPredicate (ret, aPredicate, aCtx);
    return ret;
  }

  /**
   * Apply all predicates but the last one completely (the positions of a predicate depend on the
   * full result of the one before).
   */
  @NonNull
  private static XdmSequence _applyPredicatesButLast (@NonNull final XdmSequence aSeq,
                                                      @NonNull final List <IExpr> aPredicates,
                                                      @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = aSeq;
    for (int i = 0; i < aPredicates.size () - 1; ++i)
      ret = _applyPredicate (ret, aPredicates.get (i), aCtx);
    return ret;
  }

  /**
   * Early exit: would the predicates keep at least one item? The last predicate stops at the first
   * item that passes.
   */
  private static boolean _hasPassingItem (@NonNull final XdmSequence aSeq,
                                          @NonNull final List <IExpr> aPredicates,
                                          @NonNull final DynamicContext aCtx)
  {
    if (aPredicates.isEmpty ())
      return !aSeq.isEmpty ();
    final XdmSequence aCandidates = _applyPredicatesButLast (aSeq, aPredicates, aCtx);
    return _findPassing (aCandidates, aPredicates.get (aPredicates.size () - 1), 0, aCtx) >= 0;
  }

  /**
   * @return The element name of a descendant or descendant-or-self step with an
   *         exact name test, which the name index of a document can answer;
   *         <code>null</code> for any other step.
   */
  @Nullable
  private static XdmQName _getExactName (@Nullable final NodeTest aTest)
  {
    if (aTest == null ||
        aTest.kind () != ENodeTestKind.NAME ||
        aTest.anyName () ||
        aTest.anyNamespace () ||
        aTest.anyLocalName ())
      return null;
    return aTest.name ();
  }

  /**
   * @return The exact element name of a <code>descendant</code> or
   *         <code>descendant-or-self</code> step, which the name index of a document can answer;
   *         <code>null</code> for any other axis or a test that is no exact name test.
   */
  @Nullable
  private static XdmQName _getIndexableName (final IExpr.@NonNull StepExpr aStep)
  {
    final EAxis eAxis = aStep.axis ();
    if (eAxis != EAxis.DESCENDANT && eAxis != EAxis.DESCENDANT_OR_SELF)
      return null;
    return _getExactName (aStep.test ());
  }

  /**
   * The nodes of the axis of an axis step from one context node that pass its node test.
   *
   * @return The nodes in axis order (reverse document order for a reverse axis).
   */
  @NonNull
  private static XdmSequence _collectAxisCandidates (final IExpr.@NonNull StepExpr aStep, @NonNull final IXdmNode aNode)
  {
    final EAxis eAxis = aStep.axis ();
    final NodeTest aTest = aStep.test ();
    final ENodeKind ePrincipal = AxisNavigator.getPrincipalNodeKind (eAxis);
    final List <IXdmNode> aNodes = new ArrayList <> ();
    final XdmQName aIndexName = _getIndexableName (aStep);
    final XdmQName aChildName = eAxis == EAxis.CHILD ? _getExactName (aTest) : null;
    if (aChildName != null)
      AxisNavigator.collectNamedChildren (aNode, aChildName, aNodes);
    else
      if (aIndexName == null || !AxisNavigator.collectNamedDescendants (eAxis, List.of (aNode), aIndexName, aNodes))
        AxisNavigator.collect (eAxis,
                               aNode,
                               aTest == null ? null : x -> NodeTestMatcher.matches (aTest, x, ePrincipal),
                               aNodes);
    return XdmSequence.of (aNodes);
  }

  /**
   * One axis step from one context node.
   *
   * @return The selected nodes in document order.
   */
  @NonNull
  private static XdmSequence _evalAxisStep (final IExpr.@NonNull StepExpr aStep,
                                            @NonNull final IXdmNode aNode,
                                            @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = _applyPredicates (_collectAxisCandidates (aStep, aNode), aStep.predicates (), aCtx);
    if (aStep.axis ().isReverse () && ret.size () > 1)
    {
      // Positions were counted in reverse document order; the step result is
      // in document order
      final ICommonsList <IXdmItem> aList = ret.getAsList ();
      Collections.reverse (aList);
      ret = XdmSequence.of (aList);
    }
    return ret;
  }

  /**
   * @param sCode
   *        The error code if the item is not a node: XPTY0019 for the result of a previous step,
   *        XPTY0020 for the context item of the first step.
   * @return The item as node.
   * @throws XPathException
   *         with <code>sCode</code> if the item is not a node
   */
  @NonNull
  private static IXdmNode _getStepContextNode (@NonNull final IXdmItem aItem, @NonNull final String sCode)
  {
    if (aItem instanceof final IXdmNode aNode)
      return aNode;
    throw XPathException.of (sCode, "The context item of a path step is not a node");
  }

  /**
   * @return <code>true</code> if all items are nodes (the result needs a document order sort).
   * @throws XPathException
   *         XPTY0018 for a mix of nodes and other items in the last step.
   */
  private static boolean _isAllNodes (@NonNull final XdmSequence aSeq, final boolean bLastStep)
  {
    int nNodes = 0;
    for (final IXdmItem aItem : aSeq)
      if (aItem instanceof IXdmNode)
        nNodes++;
    if (nNodes > 0 && nNodes < aSeq.size () && bLastStep)
      throw XPathException.of ("XPTY0018",
                               "The result of the last step of a path expression mixes nodes and non-node items");
    return nNodes == aSeq.size ();
  }

  /**
   * The candidates of a descendant step with an exact name test from many
   * context nodes at once, through the name index of their document. Only for
   * predicates that select the same nodes whatever the context position is,
   * since the positions count among all selected nodes and not per context
   * node.
   *
   * @return The candidates in document order, before the predicates, or
   *         <code>null</code> if the index cannot be used.
   */
  @Nullable
  private static XdmSequence _getIndexedCandidates (final IExpr.@NonNull StepExpr aStep,
                                                    @NonNull final XdmSequence aCurrent,
                                                    @NonNull final DynamicContext aCtx)
  {
    final XdmQName aIndexName = _getIndexableName (aStep);
    if (aIndexName == null)
      return null;
    for (final IExpr aPredicate : aStep.predicates ())
      if (!ExprWalker.isFocusFreeBoolean (aPredicate))
        return null;
    final List <IXdmNode> aContextNodes = new ArrayList <> (aCurrent.size ());
    for (final IXdmItem aItem : aCurrent)
    {
      if (!(aItem instanceof final IXdmNode aNode))
        return null;
      aContextNodes.add (aNode);
    }
    aCtx.checkInterrupt ();
    final List <IXdmNode> aNodes = new ArrayList <> ();
    if (!AxisNavigator.collectNamedDescendants (aStep.axis (), aContextNodes, aIndexName, aNodes))
      return null;
    return XdmSequence.of (aNodes);
  }

  /**
   * @return <code>true</code> for the step <code>descendant-or-self::node()</code>
   *         without predicates, as <code>//</code> abbreviates it.
   */
  private static boolean _isDescendantOrSelfNode (final IExpr.@NonNull StepExpr aStep)
  {
    return aStep.postfix () == null &&
           aStep.axis () == EAxis.DESCENDANT_OR_SELF &&
           aStep.test () != null &&
           aStep.test ().kind () == ENodeTestKind.ANY_NODE &&
           aStep.predicates ().isEmpty ();
  }

  /**
   * The steps <code>descendant-or-self::node()/attribute::N</code> (as in
   * <code>//@N</code>) with an exact name N from many context nodes at once,
   * through the attribute index of their document. Only for predicates that
   * select the same nodes whatever the context position is, as for
   * {@link #_getIndexedCandidates(IExpr.StepExpr, XdmSequence, DynamicContext)}.
   *
   * @return The selected attributes in document order, or <code>null</code> if
   *         the index cannot be used.
   */
  @Nullable
  private static XdmSequence _applySubtreeAttributeStep (final IExpr.@NonNull StepExpr aAttributeStep,
                                                         @NonNull final XdmSequence aCurrent,
                                                         @NonNull final DynamicContext aCtx)
  {
    if (aAttributeStep.postfix () != null || aAttributeStep.axis () != EAxis.ATTRIBUTE)
      return null;
    final XdmQName aName = _getExactName (aAttributeStep.test ());
    if (aName == null)
      return null;
    for (final IExpr aPredicate : aAttributeStep.predicates ())
      if (!ExprWalker.isFocusFreeBoolean (aPredicate))
        return null;
    final List <IXdmNode> aContextNodes = new ArrayList <> (aCurrent.size ());
    for (final IXdmItem aItem : aCurrent)
    {
      if (!(aItem instanceof final IXdmNode aNode))
        return null;
      aContextNodes.add (aNode);
    }
    aCtx.checkInterrupt ();
    final List <IXdmNode> aNodes = new ArrayList <> ();
    if (!AxisNavigator.collectNamedSubtreeAttributes (aContextNodes, aName, aNodes))
      return null;
    return _applyPredicates (XdmSequence.of (aNodes), aAttributeStep.predicates (), aCtx);
  }

  /** Apply a step (not the first of a relative path) to every item of the previous step. */
  @NonNull
  private static XdmSequence _applyStep (final IExpr.@NonNull StepExpr aStep,
                                         @NonNull final XdmSequence aCurrent,
                                         @NonNull final DynamicContext aCtx,
                                         final boolean bLastStep)
  {
    final int nSize = aCurrent.size ();
    if (nSize == 0)
      return aCurrent;
    if (aStep.postfix () == null)
    {
      if (nSize == 1)
        return _evalAxisStep (aStep, _getStepContextNode (aCurrent.get (0), "XPTY0019"), aCtx);
      final XdmSequence aIndexed = _getIndexedCandidates (aStep, aCurrent, aCtx);
      if (aIndexed != null)
        return _applyPredicates (aIndexed, aStep.predicates (), aCtx);
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aCurrent)
      {
        aCtx.checkInterrupt ();
        aBuilder.addAll (_evalAxisStep (aStep, _getStepContextNode (aItem, "XPTY0019"), aCtx));
      }
      return NodeSorter.sortAndDeduplicate (aBuilder.build ());
    }

    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      final IXdmItem aItem = aCurrent.get (i);
      _getStepContextNode (aItem, "XPTY0019");
      final DynamicContext aSub = aCtx.withFocus (aItem, i + 1, nSize);
      aBuilder.addAll (_applyPredicates (evaluateInternal (aStep.postfix (), aSub), aStep.predicates (), aSub));
    }
    final XdmSequence ret = aBuilder.build ();
    if (_isAllNodes (ret, bLastStep))
      return NodeSorter.sortAndDeduplicate (ret);
    return ret;
  }

  /**
   * Evaluate a path up to a step.
   *
   * @param nEnd
   *        The index of the first step not evaluated; the number of steps for the whole path. A
   *        fused pair of steps (<code>//name</code>) must not be split.
   * @return The result of the steps before <code>nEnd</code>; for a relative path without a start
   *         expression and <code>nEnd</code> 0 the context item.
   */
  @NonNull
  private static XdmSequence _evalPathPrefix (final IExpr.@NonNull PathExpr aExpr,
                                              @NonNull final DynamicContext aCtx,
                                              final int nEnd)
  {
    final List <IExpr.StepExpr> aSteps = aExpr.steps ();
    final int nSteps = aSteps.size ();
    XdmSequence aCurrent;
    int nFirst = 0;
    if (aExpr.start () != null)
      aCurrent = evaluateInternal (aExpr.start (), aCtx);
    else
      if (aExpr.absolute ())
      {
        final IXdmItem aItem = _getContextItem (aCtx);
        if (!(aItem instanceof final IXdmNode aNode))
          throw XPathException.of ("XPTY0020", "The context item for '/' is not a node");
        final IXdmNode aRoot = aNode.getRoot ();
        if (!aRoot.isDocument ())
          throw XPathException.of ("XPDY0050",
                                   "The root of the tree containing the context item is not a document node");
        aCurrent = XdmSequence.of (aRoot);
      }
      else
      {
        if (nSteps == 0 || nEnd == 0)
          return XdmSequence.of (_getContextItem (aCtx));
        final IExpr.StepExpr aFirst = aSteps.get (0);
        if (aFirst.postfix () != null)
        {
          // A filter expression as first step keeps its own order
          aCurrent = _applyPredicates (evaluateInternal (aFirst.postfix (), aCtx), aFirst.predicates (), aCtx);
        }
        else
        {
          final IXdmItem aItem = _getContextItem (aCtx);
          if (!(aItem instanceof final IXdmNode aNode))
            throw XPathException.of ("XPTY0020", "The context item for an axis step is not a node");
          aCurrent = _evalAxisStep (aFirst, aNode, aCtx);
        }
        nFirst = 1;
      }
    for (int i = nFirst; i < nEnd; ++i)
    {
      IExpr.StepExpr aStep = aSteps.get (i);
      // Only within the prefix asked for: the attribute step may be the one
      // the caller evaluates itself (_existsInPath)
      if (i + 1 < nEnd && _isDescendantOrSelfNode (aStep) && aStep.fused () == null)
      {
        // "//@name": the attributes from the index in place of all nodes of
        // the subtrees and the attribute step after them
        final XdmSequence aAttributes = _applySubtreeAttributeStep (aSteps.get (i + 1), aCurrent, aCtx);
        if (aAttributes != null)
        {
          aCurrent = aAttributes;
          i++;
          continue;
        }
      }
      if (aStep.fused () != null)
      {
        // "//name": one descendant step in place of descendant-or-self::node()
        // and the child step after it
        aStep = aStep.fused ();
        i++;
      }
      aCurrent = _applyStep (aStep, aCurrent, aCtx, i == nSteps - 1);
    }
    return aCurrent;
  }

  /**
   * Evaluate a whole path expression (XPath 3.1 section 3.3) with all its steps.
   *
   * @return The result of the last step: nodes in document order without duplicates, or, for a
   *         last step that is a postfix expression yielding no nodes, its items in their order.
   */
  @NonNull
  private static XdmSequence _evalPath (final IExpr.@NonNull PathExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    return _evalPathPrefix (aExpr, aCtx, aExpr.steps ().size ());
  }

  /**
   * Early exit: does an axis step select at least one node from the context items? Every context
   * item is checked to be a node first, as in the full evaluation; then the context nodes are
   * tried one by one until one of them selects a node. Document order and duplicates do not
   * matter for the answer.
   *
   * @param sCode
   *        The error code for a context item that is not a node.
   */
  private static boolean _existsInStep (final IExpr.@NonNull StepExpr aStep,
                                        @NonNull final XdmSequence aCurrent,
                                        @NonNull final DynamicContext aCtx,
                                        @NonNull final String sCode)
  {
    final int nSize = aCurrent.size ();
    if (nSize == 0)
      return false;
    for (final IXdmItem aItem : aCurrent)
      _getStepContextNode (aItem, sCode);
    if (nSize > 1)
    {
      final XdmSequence aIndexed = _getIndexedCandidates (aStep, aCurrent, aCtx);
      if (aIndexed != null)
        return _hasPassingItem (aIndexed, aStep.predicates (), aCtx);
    }
    for (final IXdmItem aItem : aCurrent)
    {
      aCtx.checkInterrupt ();
      if (_hasPassingItem (_collectAxisCandidates (aStep, (IXdmNode) aItem), aStep.predicates (), aCtx))
        return true;
    }
    return false;
  }

  /**
   * Early exit for a path whose last step is an axis step: its value is a node sequence, so its
   * effective boolean value and its existence are the same question. All steps before the last one
   * are evaluated completely.
   *
   * @return <code>null</code> if the last step is not an axis step (the caller then evaluates the
   *         path completely).
   */
  @Nullable
  private static Boolean _existsInPath (final IExpr.@NonNull PathExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final List <IExpr.StepExpr> aSteps = aExpr.steps ();
    final int nSteps = aSteps.size ();
    if (nSteps == 0)
      return null;
    IExpr.StepExpr aLast = aSteps.get (nSteps - 1);
    int nPrefix = nSteps - 1;
    if (nSteps >= 2 && aSteps.get (nSteps - 2).fused () != null)
    {
      // The fused "//name" step replaces the last two steps
      aLast = aSteps.get (nSteps - 2).fused ();
      nPrefix = nSteps - 2;
    }
    if (aLast.postfix () != null)
      return null;
    // "//@name": the full evaluation answers it from the attribute index,
    // which is cheaper than visiting every node of the subtrees here
    if (nPrefix == nSteps - 1 &&
        nSteps >= 2 &&
        _isDescendantOrSelfNode (aSteps.get (nSteps - 2)) &&
        aLast.axis () == EAxis.ATTRIBUTE &&
        _getExactName (aLast.test ()) != null)
      return null;
    final XdmSequence aCurrent = _evalPathPrefix (aExpr, aCtx, nPrefix);
    final boolean bFromContextItem = aExpr.start () == null && !aExpr.absolute () && nPrefix == 0;
    return Boolean.valueOf (_existsInStep (aLast, aCurrent, aCtx, bFromContextItem ? "XPTY0020" : "XPTY0019"));
  }

  /**
   * Early exit for the effective boolean value of a filter expression: the last predicate stops at
   * the first item that passes if that is a node; after an atomic value or a function a second
   * passing item is looked for, which makes the value undefined (FORG0006).
   */
  private static boolean _getFilterEbv (final IExpr.@NonNull FilterExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aPredicates = aExpr.predicates ();
    final XdmSequence aPrimary = evaluateInternal (aExpr.primary (), aCtx);
    if (aPredicates.isEmpty ())
      return _ebv (aPrimary);
    final XdmSequence aCandidates = _applyPredicatesButLast (aPrimary, aPredicates, aCtx);
    final IExpr aLast = aPredicates.get (aPredicates.size () - 1);
    final int nFirst = _findPassing (aCandidates, aLast, 0, aCtx);
    if (nFirst < 0)
      return false;
    final IXdmItem aFirst = aCandidates.get (nFirst);
    if (aFirst instanceof IXdmNode)
      return true;
    final int nSecond = _findPassing (aCandidates, aLast, nFirst + 1, aCtx);
    if (nSecond < 0)
      return _ebv (XdmSequence.of (aFirst));
    return _ebv (XdmSequence.of (aFirst, aCandidates.get (nSecond)));
  }

  /**
   * The effective boolean value of the shapes that can stop at the first decisive item (XPath 3.1
   * section 2.3.4 allows to skip the evaluation of parts that do not change the result): a path
   * whose last step is an axis step and, outside predicates, a filter expression; a
   * parenthesised expression of these.
   *
   * @param bPredicate
   *        <code>true</code> if the expression is a predicate. Then only shapes whose value can
   *        never be a number are handled, since a number would be compared with the position.
   * @return <code>null</code> for any other shape: the caller evaluates it completely.
   */
  @Nullable
  private static Boolean _tryEarlyEbv (@NonNull final IExpr aExpr,
                                       @NonNull final DynamicContext aCtx,
                                       final boolean bPredicate)
  {
    return switch (aExpr)
    {
      case final IExpr.PathExpr e -> _existsInPath (e, aCtx);
      case final IExpr.FilterExpr e -> bPredicate ? null : Boolean.valueOf (_getFilterEbv (e, aCtx));
      case final IExpr.SequenceExpr e -> e.items ().size () == 1 ? _tryEarlyEbv (e.items ().get (0), aCtx, bPredicate)
                                                                 : null;
      default -> null;
    };
  }

  /**
   * The effective boolean value of an expression, stopping at the first decisive item where the
   * shape allows it, and evaluating it completely otherwise.
   */
  private static boolean _getEbv (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final Boolean aEarly = _tryEarlyEbv (aExpr, aCtx, false);
    if (aEarly != null)
      return aEarly.booleanValue ();
    return _ebv (evaluateInternal (aExpr, aCtx));
  }

  /**
   * Is the value of an expression non-empty? Paths whose last step is an axis step and filter
   * expressions stop at the first item; other shapes are evaluated completely.
   */
  private static boolean _exists (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    switch (aExpr)
    {
      case final IExpr.PathExpr e ->
      {
        final Boolean aEarly = _existsInPath (e, aCtx);
        if (aEarly != null)
          return aEarly.booleanValue ();
      }
      case final IExpr.FilterExpr e ->
      {
        return _hasPassingItem (evaluateInternal (e.primary (), aCtx), e.predicates (), aCtx);
      }
      case final IExpr.SequenceExpr e ->
      {
        if (e.items ().size () == 1)
          return _exists (e.items ().get (0), aCtx);
      }
      default ->
      {}
    }
    return !evaluateInternal (aExpr, aCtx).isEmpty ();
  }

  /**
   * A variable reference (XPath 3.1 section 3.1.2): the value bound in the dynamic context.
   *
   * @throws XPathException
   *         XPDY0002 if no value is bound to the name
   */
  @NonNull
  private static XdmSequence _evalVarRef (final IExpr.@NonNull VarRefExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence ret = aCtx.getVariable (aExpr.name ());
    if (ret == null)
      throw XPathException.noContext ("No value is bound to the variable $" + aExpr.name ().getEQName ());
    return ret;
  }

  /**
   * @return <code>true</code> if one of the arguments is an argument placeholder
   *         (<code>?</code>), which makes the call a partial function application (XPath 3.1
   *         section 3.1.5.1).
   */
  private static boolean _hasPlaceholder (@NonNull final List <IExpr> aArgs)
  {
    for (final IExpr aArg : aArgs)
      if (aArg instanceof IExpr.PlaceholderExpr)
        return true;
    return false;
  }

  /**
   * Partial application: evaluate and convert the bound arguments now.
   */
  @NonNull
  private static XdmSequence _partialApply (@NonNull final IXdmFunction aFunc,
                                            @NonNull final List <IExpr> aArgs,
                                            @NonNull final DynamicContext aCtx)
  {
    final List <SeqType> aParamTypes = aFunc.getParamTypes ();
    final XdmSequence [] aBound = new XdmSequence [aArgs.size ()];
    for (int i = 0; i < aBound.length; ++i)
    {
      final IExpr aArg = aArgs.get (i);
      if (!(aArg instanceof IExpr.PlaceholderExpr))
        aBound[i] = XPathOperators.convertArgument (evaluateInternal (aArg, aCtx),
                                                    aParamTypes.get (i),
                                                    aCtx,
                                                    aFunc.getName (),
                                                    i);
    }
    return XdmSequence.of (new PartialFunctionItem (aFunc, aBound));
  }

  /**
   * A static call of fn:exists, fn:empty, fn:boolean or fn:not (recognised by the implementation
   * the name resolves to): only the existence or the effective boolean value of the argument
   * matters, so it is evaluated with early exit. The parameter type <code>item()*</code> needs no
   * conversion.
   *
   * @return <code>null</code> for any other function.
   */
  @Nullable
  private static XdmSequence _tryExistenceCall (@NonNull final FunctionDef aDef,
                                                @NonNull final IExpr aArg,
                                                @NonNull final DynamicContext aCtx)
  {
    final IXPathFunction aImpl = aDef.getImpl ();
    if (aImpl == FnSequence.EXISTS)
      return _bool (_exists (aArg, aCtx));
    if (aImpl == FnSequence.EMPTY)
      return _bool (!_exists (aArg, aCtx));
    if (aImpl == FnBoolean.BOOLEAN)
      return _bool (_getEbv (aArg, aCtx));
    if (aImpl == FnBoolean.NOT)
      return _bool (!_getEbv (aArg, aCtx));
    return null;
  }

  /**
   * A static function call (XPath 3.1 section 3.1.5). The function is resolved by name and arity
   * in the static context. With an argument placeholder the result is a partially applied
   * {@link BuiltInFunctionItem}. A one-argument call of <code>fn:exists</code>,
   * <code>fn:empty</code>, <code>fn:boolean</code> or <code>fn:not</code> is answered with early
   * exit. Otherwise every argument is evaluated, converted to its parameter type with the function
   * conversion rules (including the XPath 1.0 compatibility conversions) and the implementation is
   * invoked.
   *
   * @throws XPathException
   *         XPST0017 (from the function lookup) if no function of that name and arity exists;
   *         XPTY0004 or another conversion error for an argument that does not match its type
   */
  @NonNull
  private static XdmSequence _evalFunctionCall (final IExpr.@NonNull FunctionCallExpr aExpr,
                                                @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aArgExprs = aExpr.args ();
    final FunctionDef aDef = XPathOperators.getFunctionDef (aCtx, aExpr.name (), aArgExprs.size ());
    if (_hasPlaceholder (aArgExprs))
      return _partialApply (new BuiltInFunctionItem (aDef, aCtx), aArgExprs, aCtx);
    if (aArgExprs.size () == 1)
    {
      final XdmSequence aEarly = _tryExistenceCall (aDef, aArgExprs.get (0), aCtx);
      if (aEarly != null)
        return aEarly;
    }

    final List <SeqType> aParamTypes = aDef.getParamTypes ();
    final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> (aArgExprs.size ());
    for (int i = 0; i < aArgExprs.size (); ++i)
    {
      // XPath 1.0 compatibility rules are applied inside convert
      final XdmSequence aValue = evaluateInternal (aArgExprs.get (i), aCtx);
      aArgs.add (XPathOperators.convertArgument (aValue, aParamTypes.get (i), aCtx, aDef.getName (), i));
    }
    return XPathOperators.invokeBuiltIn (aDef, aCtx, aArgs);
  }

  /**
   * A dynamic function call (XPath 3.1 section 3.2.2): the base expression must give exactly one
   * function item (a map or an array counts as a function of arity one) whose arity is the number
   * of arguments. With an argument placeholder the function is partially applied; otherwise the
   * arguments are evaluated and passed to
   * {@link #callFunction(DynamicContext, IXdmItem, List)}, which converts them.
   *
   * @throws XPathException
   *         XPTY0004 if the base is not a single item, not a function item, or of another arity
   */
  @NonNull
  private static XdmSequence _evalDynamicCall (final IExpr.@NonNull DynamicCallExpr aExpr,
                                               @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aBase = evaluateInternal (aExpr.base (), aCtx);
    if (!aBase.isSingleton ())
      throw XPathException.type ("The target of a dynamic function call must be a single item, but it has " +
                                 aBase.size () +
                                 " items");
    final IXdmItem aFuncItem = aBase.get (0);
    final int nArity = getFunctionArity (aFuncItem);
    if (nArity < 0)
      throw XPathException.type ("The target of a dynamic function call is not a function");
    final List <IExpr> aArgExprs = aExpr.args ();
    if (nArity != aArgExprs.size ())
      throw XPathException.type ("A function of arity " +
                                 nArity +
                                 " is called with " +
                                 aArgExprs.size () +
                                 " arguments");
    if (_hasPlaceholder (aArgExprs))
      return _partialApply (asFunction (aFuncItem), aArgExprs, aCtx);

    final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> (aArgExprs.size ());
    for (final IExpr aArgExpr : aArgExprs)
      aArgs.add (evaluateInternal (aArgExpr, aCtx));
    return callFunction (aCtx, aFuncItem, aArgs);
  }

  /**
   * One operand of a range expression <code>a to b</code> (XPath 3.1 section 3.4.1): atomised,
   * an <code>xs:untypedAtomic</code> value cast to <code>xs:integer</code>, and then required to
   * be of an integer type.
   *
   * @return The integer value, or <code>null</code> for the empty sequence.
   * @throws XPathException
   *         XPTY0004 for more than one item or a value that is no integer (<code>1.0</code> and
   *         <code>1e0</code> included); FORG0001 for an untyped value that is no valid integer
   */
  @Nullable
  private static BigInteger _getRangeOperand (@NonNull final XdmSequence aSeq, @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aValue = XPathOperators.atomizeOptional (aSeq, "operand of 'to'");
    if (aValue == null)
      return null;
    if (aValue.getType () == EAtomicType.UNTYPED_ATOMIC)
      aValue = AtomicCaster.castFromString (aValue.getString (), EAtomicType.INTEGER, aCtx.getStaticContext ());
    if (!aValue.getType ().isIntegerFamily ())
      throw XPathException.type ("The operands of 'to' must be integers, not " + aValue.getType ().getQualifiedName ());
    return aValue.getBigInteger ();
  }

  /**
   * @return The general comparison operator with the operands swapped (<code>&lt;</code> becomes
   *         <code>&gt;</code>, <code>&lt;=</code> becomes <code>&gt;=</code> and vice versa);
   *         <code>=</code>, <code>!=</code> and every other operator are returned unchanged.
   */
  @NonNull
  private static EComparisonOp _swapGeneralOp (@NonNull final EComparisonOp eOp)
  {
    return switch (eOp)
    {
      case GENERAL_LT -> EComparisonOp.GENERAL_GT;
      case GENERAL_GT -> EComparisonOp.GENERAL_LT;
      case GENERAL_LE -> EComparisonOp.GENERAL_GE;
      case GENERAL_GE -> EComparisonOp.GENERAL_LE;
      default -> eOp;
    };
  }

  /**
   * @return <code>true</code> if the xs:integer or xs:decimal value compares true under the
   *         operator with some integer of the range. Both are compared exactly, at any magnitude.
   */
  private static boolean _isRangeHitExact (@NonNull final EComparisonOp eOp,
                                           @NonNull final XdmAtomic aValue,
                                           @NonNull final BigInteger aFrom,
                                           @NonNull final BigInteger aTo)
  {
    final BigDecimal aV = aValue.getValue () instanceof final BigInteger aBigInt ? new BigDecimal (aBigInt)
                                                                                 : aValue.getBigDecimal ();
    final BigDecimal aLo = new BigDecimal (aFrom);
    final BigDecimal aHi = new BigDecimal (aTo);
    return switch (eOp)
    {
      case GENERAL_EQ -> (aV.signum () == 0 || aV.stripTrailingZeros ().scale () <= 0) &&
                         aV.compareTo (aLo) >= 0 &&
                         aV.compareTo (aHi) <= 0;
      case GENERAL_NE -> aFrom.compareTo (aTo) != 0 || aV.compareTo (aLo) != 0;
      case GENERAL_LT -> aV.compareTo (aHi) < 0;
      case GENERAL_LE -> aV.compareTo (aHi) <= 0;
      case GENERAL_GT -> aV.compareTo (aLo) > 0;
      default -> aV.compareTo (aLo) >= 0;
    };
  }

  /**
   * {@link #_isRangeHitExact(EComparisonOp, XdmAtomic, BigInteger, BigInteger)} for an xs:double or
   * xs:float value, to whose type every range member is promoted first. Promotion is monotonic, so
   * the promoted bounds settle each operator; for "!=" some member differs from the value unless
   * both promoted bounds (and so every member between them) equal it.
   */
  private static boolean _isRangeHitFloat (@NonNull final EComparisonOp eOp,
                                           @NonNull final XdmAtomic aValue,
                                           @NonNull final BigInteger aFrom,
                                           @NonNull final BigInteger aTo)
  {
    final double d = aValue.getAsDouble ();
    if (Double.isNaN (d))
      return eOp == EComparisonOp.GENERAL_NE;
    final boolean bFloat = aValue.getType () == EAtomicType.FLOAT;
    final double dLo = bFloat ? aFrom.floatValue () : aFrom.doubleValue ();
    final double dHi = bFloat ? aTo.floatValue () : aTo.doubleValue ();
    return switch (eOp)
    {
      case GENERAL_EQ -> d >= dLo && d <= dHi && d == Math.rint (d);
      case GENERAL_NE -> dLo != d || dHi != d;
      case GENERAL_LT -> d < dHi;
      case GENERAL_LE -> d <= dHi;
      case GENERAL_GT -> d > dLo;
      default -> d >= dLo;
    };
  }

  /**
   * A general comparison one of whose operands is a range expression, without materialising the
   * range (<code>$c = (32 to 55295)</code> is a bounds test; RangeExpr-409c compares against a
   * range of 10^13 integers). The existential semantics are exact: for a numeric v and the integers
   * from..to, "v = range" holds iff v is an integer in [from, to], "v != range" iff some member
   * differs from v, and the orderings compare v against the nearest bound. An empty range makes
   * every comparison false.
   *
   * @param eOp
   *        The operator, oriented so that the other operand is on the left.
   * @param aOtherAtoms
   *        The atomised other operand.
   * @return <code>null</code> if the fast path does not apply (the other operand holds anything but
   *         numeric or untyped atomic values); the caller then takes the ordinary path.
   */
  @Nullable
  private static XdmSequence _evalRangeComparison (@NonNull final EComparisonOp eOp,
                                                   final IExpr.@NonNull RangeExpr aRange,
                                                   @NonNull final XdmSequence aOtherAtoms,
                                                   @NonNull final DynamicContext aCtx)
  {
    final ICommonsList <XdmAtomic> aValues = new CommonsArrayList <> (aOtherAtoms.size ());
    for (final IXdmItem aItem : aOtherAtoms)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aItem;
      if (aAtomic.isNumeric ())
        aValues.add (aAtomic);
      else
        if (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC)
          aValues.add (XPathOperators.untypedToDouble (aAtomic, aCtx));
        else
          return null;
    }
    final BigInteger aFrom = _getRangeOperand (evaluateInternal (aRange.from (), aCtx), aCtx);
    final BigInteger aTo = _getRangeOperand (evaluateInternal (aRange.to (), aCtx), aCtx);
    if (aFrom == null || aTo == null || aFrom.compareTo (aTo) > 0 || aValues.isEmpty ())
      return FALSE_SEQ;
    for (final XdmAtomic aValue : aValues)
    {
      final Object aPayload = aValue.getValue ();
      final boolean bHit;
      if (aPayload instanceof BigInteger || aPayload instanceof BigDecimal)
        bHit = _isRangeHitExact (eOp, aValue, aFrom, aTo);
      else
        bHit = _isRangeHitFloat (eOp, aValue, aFrom, aTo);
      if (bHit)
        return TRUE_SEQ;
    }
    return FALSE_SEQ;
  }

  /**
   * A value, general or node comparison (XPath 3.1 section 3.7). A general comparison with a range
   * expression as one operand is first tried without materialising the range
   * ({@link #_evalRangeComparison(EComparisonOp, IExpr.RangeExpr, XdmSequence, DynamicContext)});
   * not in XPath 1.0 compatibility mode, which has its own general comparison rules. Everything
   * else goes to
   * {@link XPathOperators#compare(EComparisonOp, XdmSequence, XdmSequence, DynamicContext)}. The
   * left operand is evaluated before the right one.
   */
  @NonNull
  private static XdmSequence _evalComparison (final IExpr.@NonNull ComparisonExpr aExpr,
                                              @NonNull final DynamicContext aCtx)
  {
    // XPath 1.0 compatibility mode has its own general comparison rules
    if (aExpr.op ().isGeneral () && !aCtx.getStaticContext ().isXPath10Compatibility ())
    {
      if (aExpr.right () instanceof final IExpr.RangeExpr aRange)
      {
        final XdmSequence aLeft = XPathOperators.atomize (evaluateInternal (aExpr.left (), aCtx));
        final XdmSequence aFast = _evalRangeComparison (aExpr.op (), aRange, aLeft, aCtx);
        if (aFast != null)
          return aFast;
        return _bool (XPathOperators.generalCompareAtoms (aExpr.op (),
                                                          aLeft,
                                                          XPathOperators.atomize (evaluateInternal (aRange, aCtx)),
                                                          aCtx));
      }
      if (aExpr.left () instanceof final IExpr.RangeExpr aRange)
      {
        final XdmSequence aRight = XPathOperators.atomize (evaluateInternal (aExpr.right (), aCtx));
        final XdmSequence aFast = _evalRangeComparison (_swapGeneralOp (aExpr.op ()), aRange, aRight, aCtx);
        if (aFast != null)
          return aFast;
        return _bool (XPathOperators.generalCompareAtoms (aExpr.op (),
                                                          XPathOperators.atomize (evaluateInternal (aRange, aCtx)),
                                                          aRight,
                                                          aCtx));
      }
    }
    final XdmSequence aLeft = evaluateInternal (aExpr.left (), aCtx);
    final XdmSequence aRight = evaluateInternal (aExpr.right (), aCtx);
    return XPathOperators.compare (aExpr.op (), aLeft, aRight, aCtx);
  }

  /**
   * A logical (XPath 3.1 section 3.8) or arithmetic (section 3.5) expression. <code>and</code>
   * and <code>or</code> take the effective boolean value of the operands left to right, each with
   * early exit, and do not evaluate the right operand once the left one decides the result. An
   * arithmetic operator evaluates both operands and applies
   * {@link XPathOperators#arithmetic(EBinaryOp, XdmSequence, XdmSequence, DynamicContext)}.
   */
  @NonNull
  private static XdmSequence _evalBinary (final IExpr.@NonNull BinaryExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final EBinaryOp eOp = aExpr.op ();
    if (eOp == EBinaryOp.AND)
      return _bool (_getEbv (aExpr.left (), aCtx) && _getEbv (aExpr.right (), aCtx));
    if (eOp == EBinaryOp.OR)
      return _bool (_getEbv (aExpr.left (), aCtx) || _getEbv (aExpr.right (), aCtx));

    final XdmSequence aLeft = evaluateInternal (aExpr.left (), aCtx);
    final XdmSequence aRight = evaluateInternal (aExpr.right (), aCtx);
    return XPathOperators.arithmetic (eOp, aLeft, aRight, aCtx);
  }

  @NonNull
  private static XdmSequence _evalUnary (final IExpr.@NonNull UnaryExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    return XPathOperators.unaryArithmetic (aExpr.negate (), evaluateInternal (aExpr.operand (), aCtx), aCtx);
  }

  /**
   * The string concatenation <code>a || b || ...</code> (XPath 3.1 section 3.6): each operand is
   * atomised, an empty operand counts as the zero-length string, and the string values are joined.
   *
   * @return A single <code>xs:string</code>.
   * @throws XPathException
   *         XPTY0004 if an operand atomises to more than one item; FOTY0013 for a map or function
   *         item
   */
  @NonNull
  private static XdmSequence _evalStringConcat (final IExpr.@NonNull StringConcatExpr aExpr,
                                                @NonNull final DynamicContext aCtx)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IExpr aPart : aExpr.parts ())
    {
      final XdmAtomic aValue = XPathOperators.atomizeOptional (evaluateInternal (aPart, aCtx), "operand of '||'");
      if (aValue != null)
        aSB.append (_getStringValue (aValue));
    }
    return XdmSequence.of (XdmAtomic.string (aSB.toString ()));
  }

  /**
   * @param sOp
   *        The operator name for the message.
   * @return The sequence itself if all items are nodes.
   * @throws XPathException
   *         XPTY0004 for any item that is not a node
   */
  @NonNull
  private static XdmSequence _checkNodes (@NonNull final XdmSequence aSeq, @NonNull final String sOp)
  {
    for (final IXdmItem aItem : aSeq)
      if (!(aItem instanceof IXdmNode))
        throw XPathException.type ("The operands of '" + sOp + "' must be nodes");
    return aSeq;
  }

  /**
   * <code>union</code>, <code>intersect</code> and <code>except</code> (XPath 3.1 section 3.4.2)
   * on node identity: both operands must be node sequences, the result is in document order
   * without duplicates.
   *
   * @throws XPathException
   *         XPTY0004 if an operand holds an item that is not a node
   */
  @NonNull
  private static XdmSequence _evalSet (final IExpr.@NonNull SetExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final String sOp = aExpr.op ().name ().toLowerCase (Locale.ROOT);
    final XdmSequence aLeft = _checkNodes (evaluateInternal (aExpr.left (), aCtx), sOp);
    final XdmSequence aRight = _checkNodes (evaluateInternal (aExpr.right (), aCtx), sOp);
    if (aExpr.op () == IExpr.SetExpr.ESetOp.UNION)
      return NodeSorter.sortAndDeduplicate (XdmSequence.concat (aLeft, aRight));

    final Set <IXdmItem> aRightSet = Collections.newSetFromMap (new IdentityHashMap <> ());
    for (final IXdmItem aItem : aRight)
      aRightSet.add (aItem);
    final boolean bIntersect = aExpr.op () == IExpr.SetExpr.ESetOp.INTERSECT;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aLeft)
      if (aRightSet.contains (aItem) == bIntersect)
        aBuilder.add (aItem);
    return NodeSorter.sortAndDeduplicate (aBuilder.build ());
  }

  /**
   * The range expression <code>a to b</code> (XPath 3.1 section 3.4.1), materialised as a
   * sequence of <code>xs:integer</code> values in ascending order. An empty operand or
   * <code>a &gt; b</code> gives the empty sequence. Interrupts are checked every 4096 items.
   *
   * @throws XPathException
   *         XPDY0130 if the range has more than {@link #MAX_RANGE_SIZE} items; the operand errors
   *         of {@link #_getRangeOperand(XdmSequence, DynamicContext)}
   */
  @NonNull
  private static XdmSequence _evalRange (final IExpr.@NonNull RangeExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aFromSeq = evaluateInternal (aExpr.from (), aCtx);
    final XdmSequence aToSeq = evaluateInternal (aExpr.to (), aCtx);
    final BigInteger aFrom = _getRangeOperand (aFromSeq, aCtx);
    final BigInteger aTo = _getRangeOperand (aToSeq, aCtx);
    if (aFrom == null || aTo == null || aFrom.compareTo (aTo) > 0)
      return XdmSequence.EMPTY;
    final BigInteger aSpan = aTo.subtract (aFrom);
    if (aSpan.compareTo (BigInteger.valueOf (MAX_RANGE_SIZE)) >= 0)
      throw XPathException.of ("XPDY0130", "The integer range " + aFrom + " to " + aTo + " is too large");
    final int nCount = aSpan.intValue () + 1;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    if (aFrom.bitLength () < 63 && aTo.bitLength () < 63)
    {
      final long nFrom = aFrom.longValue ();
      for (int i = 0; i < nCount; ++i)
      {
        if ((i & 4095) == 0)
          aCtx.checkInterrupt ();
        aBuilder.add (XdmAtomic.integer (nFrom + i));
      }
    }
    else
      for (int i = 0; i < nCount; ++i)
      {
        if ((i & 4095) == 0)
          aCtx.checkInterrupt ();
        aBuilder.add (XdmAtomic.integer (aFrom.add (BigInteger.valueOf (i))));
      }
    return aBuilder.build ();
  }

  /**
   * @return The value to bind to a variable: unchanged without a declared type, otherwise
   *         converted to the declared type with the function conversion rules.
   * @throws XPathException
   *         XPTY0004 or another conversion error if the value does not match the declared type
   */
  @NonNull
  private static XdmSequence _bindingValue (final IExpr.@NonNull Binding aBinding,
                                            @NonNull final XdmSequence aValue,
                                            @NonNull final DynamicContext aCtx)
  {
    if (aBinding.type () == null)
      return aValue;
    return SequenceTypeMatcher.convert (aValue, aBinding.type (), aCtx, "XPTY0004");
  }

  /**
   * The <code>for</code> expression (XPath 3.1 section 3.9) from the binding at
   * <code>nIndex</code> on: the binding sequence is evaluated in the context of the outer
   * variables, and for each of its items in order the variable is bound to that item and the next
   * binding (finally the return expression) is evaluated. The recursion depth is the number of
   * bindings in the expression.
   *
   * @param nIndex
   *        The 0-based index of the binding to iterate; the size of the list evaluates the body.
   * @param aOut
   *        Receives the results of the return expression in iteration order.
   */
  private static void _evalFor (@NonNull final List <IExpr.Binding> aBindings,
                                final int nIndex,
                                @NonNull final IExpr aBody,
                                @NonNull final DynamicContext aCtx,
                                final XdmSequence.@NonNull Builder aOut)
  {
    if (nIndex == aBindings.size ())
    {
      aOut.addAll (evaluateInternal (aBody, aCtx));
      return;
    }
    final IExpr.Binding aBinding = aBindings.get (nIndex);
    final XdmSequence aSeq = evaluateInternal (aBinding.expr (), aCtx);
    for (final IXdmItem aItem : aSeq)
    {
      aCtx.checkInterrupt ();
      final XdmSequence aValue = _bindingValue (aBinding, XdmSequence.of (aItem), aCtx);
      _evalFor (aBindings, nIndex + 1, aBody, aCtx.withVariable (aBinding.name (), aValue), aOut);
    }
  }

  /**
   * The quantified expression <code>some</code>/<code>every</code> (XPath 3.1 section 3.13) from
   * the binding at <code>nIndex</code> on, over all combinations of the binding items in order.
   * <code>some</code> stops at the first combination whose test is true, <code>every</code> at
   * the first one whose test is false, so later combinations and their errors are not evaluated.
   *
   * @return For <code>some</code> <code>false</code>, for <code>every</code> <code>true</code>
   *         if no combination decides it (also for an empty binding sequence).
   */
  private static boolean _evalQuantified (final IExpr.@NonNull QuantifiedExpr aExpr,
                                          final int nIndex,
                                          @NonNull final DynamicContext aCtx)
  {
    final List <IExpr.Binding> aBindings = aExpr.bindings ();
    if (nIndex == aBindings.size ())
      return _getEbv (aExpr.satisfies (), aCtx);
    final IExpr.Binding aBinding = aBindings.get (nIndex);
    final boolean bEvery = aExpr.every ();
    for (final IXdmItem aItem : evaluateInternal (aBinding.expr (), aCtx))
    {
      aCtx.checkInterrupt ();
      final XdmSequence aValue = _bindingValue (aBinding, XdmSequence.of (aItem), aCtx);
      final boolean b = _evalQuantified (aExpr, nIndex + 1, aCtx.withVariable (aBinding.name (), aValue));
      if (bEvery && !b)
        return false;
      if (!bEvery && b)
        return true;
    }
    return bEvery;
  }

  /**
   * The <code>let</code> expression (XPath 3.1 section 3.10): the bindings are evaluated eagerly
   * in order, each seeing the variables bound before it, then the return expression is evaluated
   * with all of them.
   */
  @NonNull
  private static XdmSequence _evalLet (final IExpr.@NonNull LetExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    DynamicContext aCur = aCtx;
    for (final IExpr.Binding aBinding : aExpr.bindings ())
      aCur = aCur.withVariable (aBinding.name (),
                                _bindingValue (aBinding, evaluateInternal (aBinding.expr (), aCur), aCur));
    return evaluateInternal (aExpr.body (), aCur);
  }

  /**
   * The simple map operator <code>a ! b ! ...</code> (XPath 3.1 section 3.15): each step is
   * evaluated once per item of the result before it, with that item, its position and the size
   * as focus, and the results are concatenated in order. Unlike a path, nothing is sorted or
   * deduplicated and atomic values are allowed.
   */
  @NonNull
  private static XdmSequence _evalSimpleMap (final IExpr.@NonNull SimpleMapExpr aExpr,
                                             @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aSteps = aExpr.steps ();
    XdmSequence aCur = evaluateInternal (aSteps.get (0), aCtx);
    for (int nStep = 1; nStep < aSteps.size (); ++nStep)
    {
      final IExpr aStep = aSteps.get (nStep);
      final int nSize = aCur.size ();
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (int i = 0; i < nSize; ++i)
      {
        aCtx.checkInterrupt ();
        aBuilder.addAll (evaluateInternal (aStep, aCtx.withFocus (aCur.get (i), i + 1, nSize)));
      }
      aCur = aBuilder.build ();
    }
    return aCur;
  }

  /**
   * <code>cast as</code> (XPath 3.1 section 3.14.2) or <code>castable as</code> (section 3.14.3),
   * delegated to {@link XPathOperators}.
   *
   * @return The cast value, or for <code>castable as</code> a single boolean.
   */
  @NonNull
  private static XdmSequence _evalCast (final IExpr.@NonNull CastExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aValue = evaluateInternal (aExpr.operand (), aCtx);
    if (aExpr.castable ())
      return _bool (XPathOperators.castable (aValue, aExpr.type (), aCtx));
    return XPathOperators.cast (aValue, aExpr.type (), aCtx);
  }

  /**
   * The map constructor <code>map { k : v, ... }</code> (XPath 3.1 section 3.11.1.1): the entries
   * are evaluated in order, each key atomised to exactly one atomic value.
   *
   * @return A single map.
   * @throws XPathException
   *         XPTY0004 if a key is not exactly one atomic value after atomisation; XQDY0137 if two
   *         keys are the same key (<code>op:same-key</code>)
   */
  @NonNull
  private static XdmSequence _evalMapConstructor (final IExpr.@NonNull MapConstructorExpr aExpr,
                                                  @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aKeys = aExpr.keys ();
    final List <IExpr> aValues = aExpr.values ();
    final XdmMap.Builder aMap = new XdmMap.Builder (aKeys.size ());
    for (int i = 0; i < aKeys.size (); ++i)
    {
      final XdmSequence aKeySeq = XPathOperators.atomize (evaluateInternal (aKeys.get (i), aCtx));
      if (!aKeySeq.isSingleton ())
        throw XPathException.type ("A map key must be a single atomic value, but it has " + aKeySeq.size () + " items");
      final XdmAtomic aKey = (XdmAtomic) aKeySeq.get (0);
      final XdmSequence aValue = evaluateInternal (aValues.get (i), aCtx);
      if (!aMap.putIfAbsent (aKey, aValue))
        throw XPathException.of ("XQDY0137", "Duplicate key in a map constructor");
    }
    return XdmSequence.of (aMap.build ());
  }

  /**
   * The array constructors (XPath 3.1 section 3.11.2): the square form <code>[a, b]</code> makes
   * the value of each expression one member; the curly form <code>array { a, b }</code> makes each
   * item of the concatenated values one member.
   *
   * @return A single array.
   */
  @NonNull
  private static XdmSequence _evalArrayConstructor (final IExpr.@NonNull ArrayConstructorExpr aExpr,
                                                    @NonNull final DynamicContext aCtx)
  {
    final ICommonsList <XdmSequence> aMembers = new CommonsArrayList <> ();
    for (final IExpr aItemExpr : aExpr.items ())
    {
      final XdmSequence aValue = evaluateInternal (aItemExpr, aCtx);
      if (aExpr.curly ())
      {
        for (final IXdmItem aItem : aValue)
          aMembers.add (XdmSequence.of (aItem));
      }
      else
        aMembers.add (aValue);
    }
    return XdmSequence.of (XdmArray.of (aMembers));
  }

  /**
   * The member of an array at a 1-based position given as an atomic key, as an array lookup or a
   * call of an array as function does it. An <code>xs:untypedAtomic</code> key is cast to
   * <code>xs:integer</code> first.
   *
   * @return The member (a sequence of any length).
   * @throws XPathException
   *         XPTY0004 if the key is not of an integer type; FORG0001 for an untyped key that is no
   *         valid integer; FOAY0001 if the position is less than 1 or greater than the size
   */
  @NonNull
  private static XdmSequence _getArrayMember (@NonNull final XdmArray aArray,
                                              @NonNull final XdmAtomic aKey,
                                              @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aIndex = aKey;
    if (aIndex.getType () == EAtomicType.UNTYPED_ATOMIC)
      aIndex = AtomicCaster.castFromString (aIndex.getString (), EAtomicType.INTEGER, aCtx.getStaticContext ());
    if (!aIndex.getType ().isIntegerFamily ())
      throw XPathException.type ("An array index must be an xs:integer, not " + aIndex.getType ().getQualifiedName ());
    final BigInteger aBigIndex = aIndex.getBigInteger ();
    if (aBigIndex.signum () <= 0 || aBigIndex.compareTo (BigInteger.valueOf (aArray.size ())) > 0)
      throw XPathException.of ("FOAY0001",
                               "Array index " + aBigIndex + " is out of bounds (1.." + aArray.size () + ")");
    return aArray.get (aBigIndex.intValue () - 1);
  }

  /**
   * The lookup operator (XPath 3.1 section 3.11.3): postfix <code>E?K</code> with a base
   * expression, unary <code>?K</code> on the context item without one. The key specifier is
   * atomised once; a wildcard (<code>?*</code>, no key expression) selects all values of a map (in
   * the map's iteration order) or all members of an array (in order). For each base item in order,
   * each key is looked up: a key absent from a map contributes nothing, an array is indexed with
   * {@link #_getArrayMember(XdmArray, XdmAtomic, DynamicContext)}. The results are concatenated.
   *
   * @throws XPathException
   *         XPTY0004 if a base item is neither a map nor an array; XPDY0002 for a unary lookup
   *         without context item; the array index errors
   */
  @NonNull
  private static XdmSequence _evalLookup (final IExpr.@NonNull LookupExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aBase = aExpr.base () == null ? XdmSequence.of (_getContextItem (aCtx))
                                                    : evaluateInternal (aExpr.base (), aCtx);
    final XdmSequence aKeys = aExpr.key () == null ? null
                                                   : XPathOperators.atomize (evaluateInternal (aExpr.key (), aCtx));
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aBase)
    {
      if (aItem instanceof final XdmMap aMap)
      {
        if (aKeys == null)
        {
          for (final XdmMap.Entry aEntry : aMap.getAllEntries ())
            aBuilder.addAll (aEntry.value ());
        }
        else
          for (final IXdmItem aKey : aKeys)
          {
            final XdmSequence aValue = aMap.get ((XdmAtomic) aKey);
            if (aValue != null)
              aBuilder.addAll (aValue);
          }
      }
      else
        if (aItem instanceof final XdmArray aArray)
        {
          if (aKeys == null)
          {
            for (int i = 0; i < aArray.size (); ++i)
              aBuilder.addAll (aArray.get (i));
          }
          else
            for (final IXdmItem aKey : aKeys)
              aBuilder.addAll (_getArrayMember (aArray, (XdmAtomic) aKey, aCtx));
        }
        else
          throw XPathException.type ("The lookup operator requires a map or an array");
    }
    return aBuilder.build ();
  }

  /**
   * The comma operator and parenthesised expressions (XPath 3.1 sections 3.1.3 and 3.4.1): the
   * values of the items are concatenated in order; a single item is evaluated directly.
   */
  @NonNull
  private static XdmSequence _evalSequence (final IExpr.@NonNull SequenceExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aItems = aExpr.items ();
    if (aItems.size () == 1)
      return evaluateInternal (aItems.get (0), aCtx);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IExpr aItem : aItems)
      aBuilder.addAll (evaluateInternal (aItem, aCtx));
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _evalForExpr (final IExpr.@NonNull ForExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    _evalFor (aExpr.bindings (), 0, aExpr.body (), aCtx, aBuilder);
    return aBuilder.build ();
  }

  /**
   * <code>treat as</code> (XPath 3.1 section 3.14.5): the value is returned unchanged if it
   * matches the sequence type, without any conversion.
   *
   * @throws XPathException
   *         XPDY0050 if the value does not match the type
   */
  @NonNull
  private static XdmSequence _evalTreat (final IExpr.@NonNull TreatExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aValue = evaluateInternal (aExpr.operand (), aCtx);
    if (!SequenceTypeMatcher.matches (aValue, aExpr.type ()))
      throw XPathException.of ("XPDY0050",
                               "The value does not match the type " + SequenceTypeMatcher.getAsString (aExpr.type ()));
    return aValue;
  }

  /**
   * A named function reference <code>name#arity</code> (XPath 3.1 section 3.1.6): a
   * {@link BuiltInFunctionItem} for the function of that name and arity. A focus-dependent
   * function (such as <code>fn:name#0</code>) captures the current dynamic context, so that it is
   * evaluated later with the focus of the reference.
   *
   * @throws XPathException
   *         XPST0017 (from the function lookup) if no function of that name and arity exists
   */
  @NonNull
  private static XdmSequence _evalNamedFunctionRef (final IExpr.@NonNull NamedFunctionRefExpr aExpr,
                                                    @NonNull final DynamicContext aCtx)
  {
    final FunctionDef aDef = XPathOperators.getFunctionDef (aCtx, aExpr.name (), aExpr.arity ());
    return XdmSequence.of (new BuiltInFunctionItem (aDef, aDef.isFocusDependent () ? aCtx : null));
  }

  /**
   * The recursive evaluation entry, used by the function items of this package. Errors from a too
   * deep Java stack are mapped once in {@link #evaluate(IExpr, DynamicContext)}.
   */
  @NonNull
  static XdmSequence evaluateInternal (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    return switch (aExpr)
    {
      case final IExpr.LiteralExpr e -> XdmSequence.of (e.value ());
      case final IExpr.VarRefExpr e -> _evalVarRef (e, aCtx);
      case final IExpr.ContextItemExpr e -> XdmSequence.of (_getContextItem (aCtx));
      case final IExpr.FunctionCallExpr e -> _evalFunctionCall (e, aCtx);
      case final IExpr.PlaceholderExpr e -> throw XPathException.syntax ("An argument placeholder is only allowed in a function call");
      case final IExpr.NamedFunctionRefExpr e -> _evalNamedFunctionRef (e, aCtx);
      case final IExpr.InlineFunctionExpr e -> XdmSequence.of (new InlineFunctionItem (e, aCtx));
      case final IExpr.DynamicCallExpr e -> _evalDynamicCall (e, aCtx);
      case final IExpr.SequenceExpr e -> _evalSequence (e, aCtx);
      case final IExpr.BinaryExpr e -> _evalBinary (e, aCtx);
      case final IExpr.UnaryExpr e -> _evalUnary (e, aCtx);
      case final IExpr.ComparisonExpr e -> _evalComparison (e, aCtx);
      case final IExpr.SetExpr e -> _evalSet (e, aCtx);
      case final IExpr.RangeExpr e -> _evalRange (e, aCtx);
      case final IExpr.StringConcatExpr e -> _evalStringConcat (e, aCtx);
      case final IExpr.IfExpr e -> _getEbv (e.condition (), aCtx) ? evaluateInternal (e.thenExpr (), aCtx)
                                                                  : evaluateInternal (e.elseExpr (), aCtx);
      case final IExpr.ForExpr e -> _evalForExpr (e, aCtx);
      case final IExpr.LetExpr e -> _evalLet (e, aCtx);
      case final IExpr.QuantifiedExpr e -> _bool (_evalQuantified (e, 0, aCtx));
      case final IExpr.SimpleMapExpr e -> _evalSimpleMap (e, aCtx);
      case final IExpr.PathExpr e -> _evalPath (e, aCtx);
      case final IExpr.FilterExpr e -> _applyPredicates (evaluateInternal (e.primary (), aCtx), e.predicates (), aCtx);
      case final IExpr.InstanceOfExpr e -> _bool (SequenceTypeMatcher.matches (evaluateInternal (e.operand (), aCtx),
                                                                               e.type ()));
      case final IExpr.TreatExpr e -> _evalTreat (e, aCtx);
      case final IExpr.CastExpr e -> _evalCast (e, aCtx);
      case final IExpr.MapConstructorExpr e -> _evalMapConstructor (e, aCtx);
      case final IExpr.ArrayConstructorExpr e -> _evalArrayConstructor (e, aCtx);
      case final IExpr.LookupExpr e -> _evalLookup (e, aCtx);
    };
  }

  /**
   * @param aExpr
   *        Parsed expression.
   * @param aCtx
   *        Dynamic context.
   * @return The result sequence; node sequences produced by path expressions are in document order
   *         without duplicates.
   * @throws com.helger.xpath.XPathException
   *         for dynamic and type errors.
   */
  @NonNull
  public static XdmSequence evaluate (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    try
    {
      return evaluateInternal (aExpr, aCtx);
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The evaluation is nested too deeply", ex);
    }
  }

  /**
   * The effective boolean value of an expression (XPath 3.1 section 2.4.3), for hosts that need
   * one (<code>xsl:if</code>, <code>xsl:when</code>, assertions). Paths and filter expressions stop
   * at the first decisive item (section 2.3.4), so a dynamic error in a part that does not change
   * the result is not raised; other expressions are evaluated completely.
   *
   * @param aExpr
   *        Parsed expression.
   * @param aCtx
   *        Dynamic context.
   * @return The effective boolean value.
   * @throws com.helger.xpath.XPathException
   *         for dynamic and type errors, FORG0006 if the effective boolean value is not defined.
   */
  public static boolean evaluateEffectiveBooleanValue (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    try
    {
      return _getEbv (aExpr, aCtx);
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The evaluation is nested too deeply", ex);
    }
  }

  /**
   * @param aItem
   *        Any item.
   * @return The arity if the item is a function item (1 for maps and arrays), -1 otherwise.
   */
  public static int getFunctionArity (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return aFunc.getArity ();
    if (aItem instanceof XdmMap || aItem instanceof XdmArray)
      return 1;
    return -1;
  }

  /**
   * @param aItem
   *        A function, map or array.
   * @return The item as {@link IXdmFunction}; maps and arrays are wrapped.
   * @throws XPathException
   *         XPTY0004 for other items.
   */
  @NonNull
  public static IXdmFunction asFunction (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return aFunc;
    if (aItem instanceof XdmMap || aItem instanceof XdmArray)
      return new MapArrayFunctionItem (aItem);
    throw XPathException.type ("The item is not a function");
  }

  /**
   * Call a function item dynamically: check the arity, apply the function conversion rules to the
   * arguments, invoke the function within
   * {@link DynamicContext#enterCall()}/{@link DynamicContext#leaveCall()}, and convert the result
   * to the declared return type. Maps and arrays are functions of arity one. This is the entry
   * point for higher-order functions in other packages.
   *
   * @param aCtx
   *        The caller's dynamic context.
   * @param aFuncItem
   *        A function, map or array.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         XPTY0004 for a non-function or an arity mismatch, FOAY0001 for an array index out of
   *         bounds.
   */
  @NonNull
  public static XdmSequence callFunction (@NonNull final DynamicContext aCtx,
                                          @NonNull final IXdmItem aFuncItem,
                                          @NonNull final List <XdmSequence> aArgs)
  {
    final int nArity = getFunctionArity (aFuncItem);
    if (nArity < 0)
      throw XPathException.type ("The item is not a function");
    if (aArgs.size () != nArity)
      throw XPathException.type ("A function of arity " + nArity + " is called with " + aArgs.size () + " arguments");

    if (aFuncItem instanceof final XdmMap aMap)
    {
      final XdmSequence aKey = SequenceTypeMatcher.convert (aArgs.get (0),
                                                            SequenceTypeMatcher.ANY_ATOMIC_ONE,
                                                            aCtx,
                                                            "XPTY0004");
      final XdmSequence ret = aMap.get ((XdmAtomic) aKey.get (0));
      return ret == null ? XdmSequence.EMPTY : ret;
    }
    if (aFuncItem instanceof final XdmArray aArray)
    {
      final XdmSequence aIndex = SequenceTypeMatcher.convert (aArgs.get (0),
                                                              SequenceTypeMatcher.INTEGER_ONE,
                                                              aCtx,
                                                              "XPTY0004");
      return _getArrayMember (aArray, (XdmAtomic) aIndex.get (0), aCtx);
    }

    final IXdmFunction aFunc = (IXdmFunction) aFuncItem;
    final List <SeqType> aParamTypes = aFunc.getParamTypes ();
    final ICommonsList <XdmSequence> aConverted = new CommonsArrayList <> (nArity);
    for (int i = 0; i < nArity; ++i)
      aConverted.add (XPathOperators.convertArgument (aArgs.get (i), aParamTypes.get (i), aCtx, aFunc.getName (), i));
    aCtx.checkInterrupt ();
    final XdmSequence aResult;
    aCtx.enterCall ();
    try
    {
      aResult = aFunc.call (aCtx, aConverted);
    }
    finally
    {
      aCtx.leaveCall ();
    }
    if (aFunc instanceof final AbstractFunctionItem aOwn && aOwn.isResultTrusted ())
      return aResult;
    return SequenceTypeMatcher.convert (aResult, aFunc.getReturnType (), aCtx, "XPTY0004");
  }
}
