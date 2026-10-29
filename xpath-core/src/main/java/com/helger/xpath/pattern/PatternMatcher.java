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
package com.helger.xpath.pattern;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.IExpr.StepExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.AxisNavigator;
import com.helger.xpath.eval.NodeTestMatcher;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Matches one item against a parsed pattern (XSLT 3.0 section 5.5.3). One
 * instance serves one match call. Selection patterns are matched right to
 * left: starting at the candidate node, each step determines the context
 * nodes from which it could have selected the node, up to the anchor nodes
 * of the path. A node matches if some anchor exists in the tree of the node
 * (the <code>root(.)//</code> of the formal definition). The first step of a
 * relative path uses the child-or-top, attribute-or-top and namespace-or-top
 * axes and takes a document-node() test without explicit axis on the self
 * axis. Intersections and differences work on the anchor sets, so
 * <code>descendant::a except child::a</code> means what the formal
 * definition says.
 * <p>
 * One deliberate reading of the formal definition: the child axis of a step
 * still selects the children of a parentless element. Taken literally,
 * child-or-top would select only the parentless element itself, and a pattern
 * like <code>item</code> would not match the children of a parentless
 * element (a common case for temporary trees).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class PatternMatcher
{
  /** How the anchors of a path are accepted. */
  private enum EPathMode
  {
    /** A relative path: every node of the tree that is the root or not an attribute or namespace node. */
    RELATIVE,
    /** An absolute path: only the root, and only if it is a document node. */
    ABSOLUTE,
    /** A rooted path: the nodes the leading variable or function call returns. */
    ROOTED
  }

  private final DynamicContext m_aCtx;
  private final IXdmNode m_aCandidate;
  private final Set <IExpr> m_aPositionalPredicates;

  /**
   * @param aCtx
   *        The context, with the current item set to the candidate.
   * @param aCandidate
   *        The candidate node.
   * @param aPositionalPredicates
   *        The predicates that use <code>position()</code> or
   *        <code>last()</code> (identity set).
   */
  PatternMatcher (@NonNull final DynamicContext aCtx,
                  @NonNull final IXdmNode aCandidate,
                  @NonNull final Set <IExpr> aPositionalPredicates)
  {
    m_aCtx = aCtx;
    m_aCandidate = aCandidate;
    m_aPositionalPredicates = aPositionalPredicates;
  }

  /**
   * @return <code>true</code> if the node can be the context node of
   *         <code>root(.)//(EE)</code>: the root of the tree or any node that
   *         is not an attribute or namespace node.
   */
  private static boolean _isEligibleAnchor (@NonNull final IXdmNode aNode)
  {
    if (aNode.getParent () == null)
      return true;
    final ENodeKind eKind = aNode.getKind ();
    return eKind != ENodeKind.ATTRIBUTE && eKind != ENodeKind.NAMESPACE;
  }

  /** @return <code>true</code> if the anchor set has an eligible member in the tree of the candidate. */
  private boolean _isNonEmpty (@NonNull final AnchorSet aSet)
  {
    if (!aSet.isComplement ())
      return !aSet.getNodes ().isEmpty ();
    final ICommonsSet <IXdmNode> aExcluded = aSet.getNodes ();
    final IXdmNode aRoot = m_aCandidate.getRoot ();
    if (!aExcluded.contains (aRoot))
      return true;
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (!aExcluded.contains (aCur))
        return true;
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return false;
  }

  private static boolean _contains (@NonNull final XdmSequence aSeq, @NonNull final IXdmNode aNode)
  {
    for (final IXdmItem aItem : aSeq)
      if (aItem == aNode)
        return true;
    return false;
  }

  private static boolean _isPosition (@NonNull final XdmAtomic aValue, final int nPosition)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 && aBigInt.intValue () == nPosition;
    if (aPayload instanceof final BigDecimal aBigDec)
      return aBigDec.compareTo (BigDecimal.valueOf (nPosition)) == 0;
    return aValue.getAsDouble () == nPosition;
  }

  @Nullable
  private static XdmAtomic _getNumericSingleton (@NonNull final XdmSequence aValue)
  {
    if (aValue.isSingleton () && aValue.get (0) instanceof final XdmAtomic aAtomic && aAtomic.isNumeric ())
      return aAtomic;
    return null;
  }

  /** @return <code>true</code> if the predicate value keeps the item at the position. */
  private static boolean _isKept (@NonNull final XdmSequence aValue, final int nPosition)
  {
    final XdmAtomic aNum = _getNumericSingleton (aValue);
    if (aNum != null)
      return _isPosition (aNum, nPosition);
    return Atomizer.getEffectiveBooleanValue (aValue);
  }

  /**
   * Check the predicates of an axis step: would the step, evaluated from the
   * context node, keep the node?
   *
   * @param aStep
   *        The step.
   * @param eAxis
   *        The effective axis of the step.
   * @param aContext
   *        The context node of the step.
   * @param bTop
   *        <code>true</code> for the child-or-top (etc.) case, where the step
   *        selects the parentless context node itself.
   * @param aNode
   *        The node.
   */
  private boolean _predicatesMatch (@NonNull final StepExpr aStep,
                                    @NonNull final EAxis eAxis,
                                    @NonNull final IXdmNode aContext,
                                    final boolean bTop,
                                    @NonNull final IXdmNode aNode)
  {
    final List <IExpr> aPreds = aStep.predicates ();
    if (aPreds.isEmpty ())
      return true;
    // Fast path: predicates that do not depend on the position and do not
    // return a number only need the node itself
    boolean bNeedPositions = false;
    for (final IExpr aPred : aPreds)
    {
      if (m_aPositionalPredicates.contains (aPred))
      {
        bNeedPositions = true;
        break;
      }
      final XdmSequence aValue = XPathEvaluator.evaluate (aPred, m_aCtx.withFocus (aNode, 1, 1));
      if (_getNumericSingleton (aValue) != null)
      {
        bNeedPositions = true;
        break;
      }
      if (!Atomizer.getEffectiveBooleanValue (aValue))
        return false;
    }
    if (!bNeedPositions)
      return true;

    // The candidate list: the nodes of the axis passing the node test
    final ICommonsList <IXdmNode> aCands = new CommonsArrayList <> ();
    if (bTop)
      aCands.add (aNode);
    else
    {
      final NodeTest aTest = aStep.test ();
      final ENodeKind ePrincipal = AxisNavigator.getPrincipalNodeKind (eAxis);
      AxisNavigator.collect (eAxis, aContext, x -> NodeTestMatcher.matches (aTest, x, ePrincipal), aCands);
    }
    if (aPreds.size () == 1)
    {
      final int nPos = aCands.indexOf (aNode) + 1;
      if (nPos == 0)
        return false;
      final XdmSequence aValue = XPathEvaluator.evaluate (aPreds.get (0),
                                                          m_aCtx.withFocus (aNode, nPos, aCands.size ()));
      return _isKept (aValue, nPos);
    }
    // Several predicates: filter the whole list predicate by predicate
    ICommonsList <IXdmNode> aCur = aCands;
    for (final IExpr aPred : aPreds)
    {
      final ICommonsList <IXdmNode> aKept = new CommonsArrayList <> ();
      final int nSize = aCur.size ();
      for (int i = 0; i < nSize; ++i)
      {
        m_aCtx.checkInterrupt ();
        final IXdmNode aItem = aCur.get (i);
        final XdmSequence aValue = XPathEvaluator.evaluate (aPred, m_aCtx.withFocus (aItem, i + 1, nSize));
        if (_isKept (aValue, i + 1))
          aKept.add (aItem);
      }
      aCur = aKept;
      if (!aCur.contains (aNode))
        return false;
    }
    return true;
  }

  /**
   * The context nodes from which a postfix (parenthesised) step selects the
   * node.
   */
  @NonNull
  private AnchorSet _getPostfixContexts (@NonNull final StepExpr aStep, @NonNull final IXdmNode aNode)
  {
    final IExpr aInner = aStep.postfix ();
    final AnchorSet aInnerAnchors = getAnchors (aInner, aNode);
    if (aStep.predicates ().isEmpty () || aInnerAnchors.isDefinitelyEmpty ())
      return aInnerAnchors;
    // The predicates apply to the result of the parenthesised pattern
    final IExpr aFiltered = new IExpr.FilterExpr (aInner, aStep.predicates ());
    if (aInnerAnchors.isComplement ())
    {
      // Independent of the context node
      return _contains (XPathEvaluator.evaluate (aFiltered, m_aCtx.withFocus (aNode, 1, 1)), aNode) ? aInnerAnchors
                                                                                                    : AnchorSet.createEmpty ();
    }
    final AnchorSet ret = AnchorSet.createEmpty ();
    for (final IXdmNode aContext : aInnerAnchors.getNodes ())
      if (_contains (XPathEvaluator.evaluate (aFiltered, m_aCtx.withFocus (aContext, 1, 1)), aNode))
        ret.add (aContext);
    return ret;
  }

  /** The search of one path for one candidate. */
  private final class PathSearch
  {
    private final IExpr.PathExpr m_aPath;
    private final EPathMode m_eMode;
    private final boolean m_bFirstOnly;
    private final AnchorSet m_aResult = AnchorSet.createEmpty ();
    private AnchorSet m_aMergedResult;
    private ICommonsSet <IXdmNode> m_aStartNodes;
    private boolean m_bFound;
    /**
     * Per step the nodes already searched, so that paths with several
     * descendant steps do not explore the same (step, node) pair twice;
     * <code>null</code> if the path cannot reach a pair twice.
     */
    private final ICommonsList <ICommonsSet <IXdmNode>> m_aVisited;

    PathSearch (final IExpr.@NonNull PathExpr aPath, final boolean bFirstOnly)
    {
      m_aPath = aPath;
      m_eMode = aPath.start () != null ? EPathMode.ROOTED : aPath.absolute () ? EPathMode.ABSOLUTE : EPathMode.RELATIVE;
      m_bFirstOnly = bFirstOnly;
      int nBranching = 0;
      for (final StepExpr aStep : aPath.steps ())
        if (aStep.postfix () != null ||
            aStep.axis () == EAxis.DESCENDANT ||
            aStep.axis () == EAxis.DESCENDANT_OR_SELF)
          nBranching++;
      if (nBranching > 1)
      {
        m_aVisited = new CommonsArrayList <> ();
        for (int i = 0; i < aPath.steps ().size (); ++i)
          m_aVisited.add (new CommonsHashSet <> ());
      }
      else
        m_aVisited = null;
    }

    private boolean _isDone ()
    {
      return m_bFirstOnly && m_bFound;
    }

    @NonNull
    private ICommonsSet <IXdmNode> _getStartNodes ()
    {
      ICommonsSet <IXdmNode> ret = m_aStartNodes;
      if (ret == null)
      {
        ret = new CommonsHashSet <> ();
        for (final IXdmItem aItem : XPathEvaluator.evaluate (m_aPath.start (), m_aCtx.withFocus (m_aCandidate, 1, 1)))
          if (aItem instanceof final IXdmNode aNode)
            ret.add (aNode);
        m_aStartNodes = ret;
      }
      return ret;
    }

    /** The node is a context node of the first step. */
    private void _accept (@NonNull final IXdmNode aNode)
    {
      switch (m_eMode)
      {
        case RELATIVE:
          if (_isEligibleAnchor (aNode))
          {
            m_aResult.add (aNode);
            m_bFound = true;
          }
          break;
        case ABSOLUTE:
          if (aNode.getParent () == null && aNode.isDocument ())
            m_bFound = true;
          break;
        case ROOTED:
          if (_getStartNodes ().contains (aNode))
            m_bFound = true;
          break;
      }
    }

    /** Every node except the given ones is a context node of the first step. */
    private void _acceptAllExcept (@NonNull final ICommonsSet <IXdmNode> aExcluded)
    {
      switch (m_eMode)
      {
        case RELATIVE:
        {
          final AnchorSet aCo = AnchorSet.createComplementOf (aExcluded);
          if (_isNonEmpty (aCo))
          {
            m_bFound = true;
            m_aMergedResult = m_aMergedResult == null ? aCo : m_aMergedResult.getUnion (aCo);
          }
          break;
        }
        case ABSOLUTE:
        {
          final IXdmNode aRoot = m_aCandidate.getRoot ();
          if (aRoot.isDocument () && !aExcluded.contains (aRoot))
            m_bFound = true;
          break;
        }
        case ROOTED:
          for (final IXdmNode aNode : _getStartNodes ())
            if (!aExcluded.contains (aNode))
            {
              m_bFound = true;
              break;
            }
          break;
      }
    }

    @NonNull
    AnchorSet getResult ()
    {
      return m_aMergedResult == null ? m_aResult : m_aMergedResult.getUnion (m_aResult);
    }

    boolean isFound ()
    {
      return m_bFound;
    }

    private void _continue (final int nStep, @NonNull final IXdmNode aContext)
    {
      if (nStep == 0)
        _accept (aContext);
      else
        search (nStep - 1, aContext);
    }

    /**
     * A postfix step whose pattern does not depend on its context node: try
     * every node of the tree as context node, except the given ones.
     */
    private void _searchAllNodesExcept (final int nStep,
                                        @NonNull final IXdmNode aNode,
                                        @NonNull final ICommonsSet <IXdmNode> aExcluded)
    {
      final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
      aStack.push (aNode.getRoot ());
      while (!aStack.isEmpty () && !_isDone ())
      {
        m_aCtx.checkInterrupt ();
        final IXdmNode aCur = aStack.pop ();
        if (!aExcluded.contains (aCur))
          search (nStep, aCur);
        if (aCur.isElement ())
        {
          for (final IXdmNode aNS : aCur.getNamespaceNodes ())
            aStack.push (aNS);
          for (int i = aCur.getAttributeCount () - 1; i >= 0; --i)
            aStack.push (aCur.getAttributeAtIndex (i));
        }
        for (int i = aCur.getChildCount () - 1; i >= 0; --i)
          aStack.push (aCur.getChildAtIndex (i));
      }
    }
    /**
     * @param nStep
     *        Index of the step that must have selected the node.
     * @param aNode
     *        The node.
     */
    void search (final int nStep, @NonNull final IXdmNode aNode)
    {
      if (_isDone ())
        return;
      m_aCtx.checkInterrupt ();
      final List <StepExpr> aSteps = m_aPath.steps ();
      if (nStep < 0)
      {
        // A rooted path without steps or the lone "/"
        _accept (aNode);
        return;
      }
      // The outcome only depends on the pair, and the search state only grows
      if (m_aVisited != null && !m_aVisited.get (nStep).add (aNode))
        return;
      final StepExpr aStep = aSteps.get (nStep);
      final boolean bFirstRelative = nStep == 0 && m_eMode == EPathMode.RELATIVE;
      if (aStep.postfix () != null)
      {
        final AnchorSet aContexts = _getPostfixContexts (aStep, aNode);
        if (aContexts.isComplement ())
        {
          if (nStep == 0)
            _acceptAllExcept (aContexts.getNodes ());
          else
            _searchAllNodesExcept (nStep - 1, aNode, aContexts.getNodes ());
        }
        else
          for (final IXdmNode aContext : new CommonsArrayList <> (aContexts.getNodes ()))
          {
            if (_isDone ())
              return;
            _continue (nStep, aContext);
          }
        return;
      }

      EAxis eAxis = aStep.axis ();
      final NodeTest aTest = aStep.test ();
      if (bFirstRelative && !aStep.axisExplicit () && aTest.kind () == ENodeTestKind.DOCUMENT)
        eAxis = EAxis.SELF;
      final ENodeKind eKind = aNode.getKind ();
      final boolean bAttrOrNs = eKind == ENodeKind.ATTRIBUTE || eKind == ENodeKind.NAMESPACE;
      final IXdmNode aParent = aNode.getParent ();
      switch (eAxis)
      {
        case ATTRIBUTE:
          if (eKind != ENodeKind.ATTRIBUTE)
            return;
          break;
        case NAMESPACE:
          if (eKind != ENodeKind.NAMESPACE)
            return;
          break;
        case CHILD:
          if (bAttrOrNs || eKind == ENodeKind.DOCUMENT)
            return;
          break;
        case DESCENDANT:
          if (bAttrOrNs || aParent == null)
            return;
          break;
        default:
          break;
      }
      if (!NodeTestMatcher.matches (aTest, aNode, AxisNavigator.getPrincipalNodeKind (eAxis)))
        return;

      switch (eAxis)
      {
        case CHILD:
        case ATTRIBUTE:
        case NAMESPACE:
          if (aParent != null)
          {
            if (_predicatesMatch (aStep, eAxis, aParent, false, aNode))
              _continue (nStep, aParent);
          }
          else
            if (bFirstRelative)
            {
              // child-or-top, attribute-or-top, namespace-or-top
              if (_predicatesMatch (aStep, eAxis, aNode, true, aNode))
                _continue (nStep, aNode);
            }
          break;
        case SELF:
          if (_predicatesMatch (aStep, eAxis, aNode, false, aNode))
            _continue (nStep, aNode);
          break;
        case DESCENDANT_OR_SELF:
          if (_predicatesMatch (aStep, eAxis, aNode, false, aNode))
            _continue (nStep, aNode);
          if (!bAttrOrNs)
            for (IXdmNode aAnc = aParent; aAnc != null && !_isDone (); aAnc = aAnc.getParent ())
            {
              m_aCtx.checkInterrupt ();
              if (_predicatesMatch (aStep, eAxis, aAnc, false, aNode))
                _continue (nStep, aAnc);
            }
          break;
        case DESCENDANT:
          for (IXdmNode aAnc = aParent; aAnc != null && !_isDone (); aAnc = aAnc.getParent ())
          {
            m_aCtx.checkInterrupt ();
            if (_predicatesMatch (aStep, eAxis, aAnc, false, aNode))
              _continue (nStep, aAnc);
          }
          break;
        default:
          // Other axes are rejected by the parser
          break;
      }
    }

  }

  /**
   * @param aPattern
   *        A selection pattern (set expressions over path expressions).
   * @param aNode
   *        The node to be selected.
   * @return All anchors from which the pattern selects the node.
   */
  @NonNull
  AnchorSet getAnchors (@NonNull final IExpr aPattern, @NonNull final IXdmNode aNode)
  {
    if (aPattern instanceof final IExpr.SetExpr aSet)
    {
      final AnchorSet aLeft = getAnchors (aSet.left (), aNode);
      final AnchorSet aRight = getAnchors (aSet.right (), aNode);
      return switch (aSet.op ())
      {
        case UNION -> aLeft.getUnion (aRight);
        case INTERSECT -> aLeft.getIntersection (aRight);
        case EXCEPT -> aLeft.getDifference (aRight);
      };
    }
    if (aPattern instanceof final IExpr.PathExpr aPath)
    {
      if (aPath.start () != null || aPath.absolute ())
        return matchesPath (aPath, aNode) ? AnchorSet.createAll () : AnchorSet.createEmpty ();
      final PathSearch aSearch = new PathSearch (aPath, false);
      aSearch.search (aPath.steps ().size () - 1, aNode);
      return aSearch.getResult ();
    }
    throw new IllegalStateException ("Unexpected pattern node " + aPattern);
  }

  /** @return <code>true</code> if the path pattern matches the node. */
  boolean matchesPath (final IExpr.@NonNull PathExpr aPath, @NonNull final IXdmNode aNode)
  {
    final PathSearch aSearch = new PathSearch (aPath, true);
    aSearch.search (aPath.steps ().size () - 1, aNode);
    return aSearch.isFound ();
  }

  /**
   * @param aPattern
   *        A selection pattern.
   * @param aNode
   *        The candidate node.
   * @return <code>true</code> if the node matches.
   */
  boolean matchesNode (@NonNull final IExpr aPattern, @NonNull final IXdmNode aNode)
  {
    if (aPattern instanceof final IExpr.SetExpr aSet)
    {
      if (aSet.op () == IExpr.SetExpr.ESetOp.UNION)
        return matchesNode (aSet.left (), aNode) || matchesNode (aSet.right (), aNode);
      return _isNonEmpty (getAnchors (aPattern, aNode));
    }
    if (aPattern instanceof final IExpr.PathExpr aPath)
      return matchesPath (aPath, aNode);
    throw new IllegalStateException ("Unexpected pattern node " + aPattern);
  }
}
