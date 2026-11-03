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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmDocumentNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * The 13 XPath axes over {@link IXdmNode} (a port of go-xslt's
 * <code>axes.go</code>). Every traversal is iterative. Nodes are delivered in
 * axis order: document order for forward axes, reverse document order for
 * reverse axes.
 *
 * @author Philip Helger
 */
@Immutable
public final class AxisNavigator
{
  @PresentForCodeCoverage
  private static final AxisNavigator INSTANCE = new AxisNavigator ();

  private AxisNavigator ()
  {}

  private static void _add (@NonNull final IXdmNode aNode,
                            @NonNull final Predicate <IXdmNode> aFilter,
                            @NonNull final List <IXdmNode> aOut)
  {
    if (aFilter.test (aNode))
      aOut.add (aNode);
  }

  /** The descendants of aNode in document order (pre-order, without attributes). */
  private static void _addDescendants (@NonNull final IXdmNode aNode,
                                       @NonNull final Predicate <IXdmNode> aFilter,
                                       @NonNull final List <IXdmNode> aOut)
  {
    final int nCount = aNode.getChildCount ();
    if (nCount == 0)
      return;
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (int i = nCount - 1; i >= 0; --i)
      aStack.push (aNode.getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      _add (aCur, aFilter, aOut);
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
  }

  /** aNode and its descendants in reverse document order. */
  private static void _addSubtreeReverse (@NonNull final IXdmNode aNode,
                                          @NonNull final Predicate <IXdmNode> aFilter,
                                          @NonNull final List <IXdmNode> aOut)
  {
    final List <IXdmNode> aAll = new ArrayList <> ();
    aAll.add (aNode);
    _addDescendants (aNode, x -> true, aAll);
    for (int i = aAll.size () - 1; i >= 0; --i)
      _add (aAll.get (i), aFilter, aOut);
  }

  /** @return The index of aNode among the children of its parent, -1 if it is no child. */
  private static int _getChildIndex (@NonNull final IXdmNode aParent, @NonNull final IXdmNode aNode)
  {
    final int nCount = aParent.getChildCount ();
    for (int i = 0; i < nCount; ++i)
      if (aParent.getChildAtIndex (i) == aNode)
        return i;
    return -1;
  }

  private static boolean _isAttributeLike (@NonNull final IXdmNode aNode)
  {
    return aNode.getKind () == ENodeKind.ATTRIBUTE || aNode.getKind () == ENodeKind.NAMESPACE;
  }

  private static void _addFollowing (@NonNull final IXdmNode aNode,
                                     @NonNull final Predicate <IXdmNode> aFilter,
                                     @NonNull final List <IXdmNode> aOut)
  {
    IXdmNode aCur = aNode;
    if (_isAttributeLike (aNode))
    {
      // Everything after the owner element follows the attribute, including the
      // children of that element
      aCur = aNode.getParent ();
      if (aCur == null)
        return;
      _addDescendants (aCur, aFilter, aOut);
    }
    for (; aCur != null; aCur = aCur.getParent ())
    {
      final IXdmNode aParent = aCur.getParent ();
      if (aParent == null)
        break;
      final int nIndex = _getChildIndex (aParent, aCur);
      if (nIndex < 0)
        continue;
      final int nCount = aParent.getChildCount ();
      for (int i = nIndex + 1; i < nCount; ++i)
      {
        final IXdmNode aSibling = aParent.getChildAtIndex (i);
        _add (aSibling, aFilter, aOut);
        _addDescendants (aSibling, aFilter, aOut);
      }
    }
  }

  private static void _addPreceding (@NonNull final IXdmNode aNode,
                                     @NonNull final Predicate <IXdmNode> aFilter,
                                     @NonNull final List <IXdmNode> aOut)
  {
    // The owner element of an attribute is its ancestor, so it is excluded
    IXdmNode aCur = _isAttributeLike (aNode) ? aNode.getParent () : aNode;
    for (; aCur != null; aCur = aCur.getParent ())
    {
      final IXdmNode aParent = aCur.getParent ();
      if (aParent == null)
        break;
      final int nIndex = _getChildIndex (aParent, aCur);
      for (int i = nIndex - 1; i >= 0; --i)
        _addSubtreeReverse (aParent.getChildAtIndex (i), aFilter, aOut);
    }
  }

  private static void _addSiblings (@NonNull final IXdmNode aNode,
                                    final boolean bFollowing,
                                    @NonNull final Predicate <IXdmNode> aFilter,
                                    @NonNull final List <IXdmNode> aOut)
  {
    if (_isAttributeLike (aNode))
      return;
    final IXdmNode aParent = aNode.getParent ();
    if (aParent == null)
      return;
    final int nIndex = _getChildIndex (aParent, aNode);
    if (nIndex < 0)
      return;
    if (bFollowing)
    {
      final int nCount = aParent.getChildCount ();
      for (int i = nIndex + 1; i < nCount; ++i)
        _add (aParent.getChildAtIndex (i), aFilter, aOut);
    }
    else
      for (int i = nIndex - 1; i >= 0; --i)
        _add (aParent.getChildAtIndex (i), aFilter, aOut);
  }

  /**
   * @return The first index from nFrom on whose node has an order of at least
   *         nOrder; the size of the list if there is none.
   */
  private static int _lowerBound (@NonNull final List <IXdmNode> aSorted, final int nFrom, final int nOrder)
  {
    int nLow = nFrom;
    int nHigh = aSorted.size ();
    while (nLow < nHigh)
    {
      final int nMid = (nLow + nHigh) >>> 1;
      if (aSorted.get (nMid).getOrder () < nOrder)
        nLow = nMid + 1;
      else
        nHigh = nMid;
    }
    return nLow;
  }

  /**
   * Collect the elements with a name on the descendant or descendant-or-self
   * axis of context nodes from the name index of their document, instead of
   * walking the subtrees. The descendants of a node are the nodes whose order
   * lies in <code>(getOrder (), getEndOrder ()]</code>, so each context node
   * cuts an interval out of the sorted index. A context node inside the
   * interval of an earlier one adds nothing new and is skipped, so the result
   * is in document order and free of duplicates.
   *
   * @param eAxis
   *        {@link EAxis#DESCENDANT} or {@link EAxis#DESCENDANT_OR_SELF}.
   * @param aContextNodes
   *        The context nodes in document order without duplicates.
   * @param aName
   *        The element name to select.
   * @param aOut
   *        The list to append to.
   * @return <code>true</code> if the nodes were collected, <code>false</code>
   *         (with nothing added) if the index cannot be used: the nodes are
   *         not all in the same numbered document, not in document order, or
   *         a namespace node is among them.
   */
  public static boolean collectNamedDescendants (@NonNull final EAxis eAxis,
                                                 @NonNull final List <IXdmNode> aContextNodes,
                                                 @NonNull final XdmQName aName,
                                                 @NonNull final List <IXdmNode> aOut)
  {
    if (eAxis != EAxis.DESCENDANT && eAxis != EAxis.DESCENDANT_OR_SELF)
      throw new IllegalArgumentException ("Not a descendant axis: " + eAxis);
    if (aContextNodes.isEmpty ())
      return true;
    if (!(aContextNodes.get (0).getRoot () instanceof final XdmDocumentNode aDoc))
      return false;
    // Built on demand; null if the tree is not numbered
    final List <IXdmNode> aElements = aDoc.getElementsByName (aName);
    if (aElements == null)
      return false;
    int nPrevious = -1;
    for (final IXdmNode aNode : aContextNodes)
    {
      if (aNode.getKind () == ENodeKind.NAMESPACE || aNode.getOrder () <= nPrevious || aNode.getRoot () != aDoc)
        return false;
      nPrevious = aNode.getOrder ();
    }
    final boolean bSelf = eAxis == EAxis.DESCENDANT_OR_SELF;
    int nPos = 0;
    int nCovered = -1;
    for (final IXdmNode aNode : aContextNodes)
    {
      final int nStart = aNode.getOrder ();
      if (nStart <= nCovered)
        continue;
      final int nEnd = aNode.getEndOrder ();
      nCovered = nEnd;
      nPos = _lowerBound (aElements, nPos, bSelf ? nStart : nStart + 1);
      while (nPos < aElements.size () && aElements.get (nPos).getOrder () <= nEnd)
        aOut.add (aElements.get (nPos++));
    }
    return true;
  }

  /**
   * @param eAxis
   *        The axis.
   * @return The principal node kind of the axis.
   */
  @NonNull
  public static ENodeKind getPrincipalNodeKind (@NonNull final EAxis eAxis)
  {
    switch (eAxis)
    {
      case ATTRIBUTE:
        return ENodeKind.ATTRIBUTE;
      case NAMESPACE:
        return ENodeKind.NAMESPACE;
      default:
        return ENodeKind.ELEMENT;
    }
  }

  /**
   * Collect the nodes of an axis that pass a filter.
   *
   * @param eAxis
   *        The axis.
   * @param aNode
   *        The context node.
   * @param aFilter
   *        The filter, typically a node test. <code>null</code> means all
   *        nodes.
   * @param aOut
   *        The list to append to, in axis order.
   */
  public static void collect (@NonNull final EAxis eAxis,
                              @NonNull final IXdmNode aNode,
                              @Nullable final Predicate <IXdmNode> aFilter,
                              @NonNull final List <IXdmNode> aOut)
  {
    final Predicate <IXdmNode> aRealFilter = aFilter != null ? aFilter : x -> true;
    switch (eAxis)
    {
      case CHILD:
      {
        final int nCount = aNode.getChildCount ();
        for (int i = 0; i < nCount; ++i)
          _add (aNode.getChildAtIndex (i), aRealFilter, aOut);
        break;
      }
      case DESCENDANT:
        _addDescendants (aNode, aRealFilter, aOut);
        break;
      case DESCENDANT_OR_SELF:
        _add (aNode, aRealFilter, aOut);
        _addDescendants (aNode, aRealFilter, aOut);
        break;
      case ATTRIBUTE:
      {
        final int nCount = aNode.getAttributeCount ();
        for (int i = 0; i < nCount; ++i)
          _add (aNode.getAttributeAtIndex (i), aRealFilter, aOut);
        break;
      }
      case SELF:
        _add (aNode, aRealFilter, aOut);
        break;
      case NAMESPACE:
        for (final IXdmNode aNS : aNode.getNamespaceNodes ())
          _add (aNS, aRealFilter, aOut);
        break;
      case FOLLOWING_SIBLING:
        _addSiblings (aNode, true, aRealFilter, aOut);
        break;
      case PRECEDING_SIBLING:
        _addSiblings (aNode, false, aRealFilter, aOut);
        break;
      case FOLLOWING:
        _addFollowing (aNode, aRealFilter, aOut);
        break;
      case PRECEDING:
        _addPreceding (aNode, aRealFilter, aOut);
        break;
      case PARENT:
      {
        final IXdmNode aParent = aNode.getParent ();
        if (aParent != null)
          _add (aParent, aRealFilter, aOut);
        break;
      }
      case ANCESTOR:
        for (IXdmNode aCur = aNode.getParent (); aCur != null; aCur = aCur.getParent ())
          _add (aCur, aRealFilter, aOut);
        break;
      case ANCESTOR_OR_SELF:
        for (IXdmNode aCur = aNode; aCur != null; aCur = aCur.getParent ())
          _add (aCur, aRealFilter, aOut);
        break;
      default:
        throw new IllegalStateException ("Unsupported axis " + eAxis);
    }
  }

  /**
   * @param eAxis
   *        The axis.
   * @param aNode
   *        The context node.
   * @return All nodes of the axis in axis order.
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsList <IXdmNode> getAxisNodes (@NonNull final EAxis eAxis, @NonNull final IXdmNode aNode)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    collect (eAxis, aNode, null, ret);
    return ret;
  }
}
