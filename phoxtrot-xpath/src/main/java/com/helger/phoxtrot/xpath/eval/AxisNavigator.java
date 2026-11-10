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
package com.helger.phoxtrot.xpath.eval;

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
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmDocumentNode;
import com.helger.phoxtrot.xdm.XdmElementNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.EAxis;

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
  /**
   * The number of children above which a child step with a name asks the name
   * index; below it comparing the children is as cheap as the binary search.
   */
  private static final int MIN_CHILDREN_FOR_INDEX = 32;

  @PresentForCodeCoverage
  private static final AxisNavigator INSTANCE = new AxisNavigator ();

  private AxisNavigator ()
  {}

  /**
   * Append the node to the output list if it passes the filter.
   */
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

  /**
   * @return <code>true</code> for an attribute or namespace node: a node that
   *         has a parent but is not among its children.
   */
  private static boolean _isAttributeLike (@NonNull final IXdmNode aNode)
  {
    return aNode.getKind () == ENodeKind.ATTRIBUTE || aNode.getKind () == ENodeKind.NAMESPACE;
  }

  /**
   * The following axis: walking up from the node, each following sibling of
   * the node and of every ancestor is added together with its descendants, in
   * document order. For an attribute or namespace node the walk starts at its
   * parent element, whose descendants (but not other attributes) come first.
   * Attributes and namespace nodes are never added. A parentless attribute
   * adds nothing.
   */
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

  /**
   * The preceding axis: walking up from the node, the subtrees of the
   * preceding siblings of the node and of every ancestor are added in reverse
   * document order, so ancestors themselves are excluded. For an attribute or
   * namespace node the walk starts at its parent element.
   */
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

  /**
   * The following-sibling (document order) or preceding-sibling (reverse
   * document order) axis. Attribute and namespace nodes and parentless nodes
   * have no siblings.
   *
   * @param bFollowing
   *        <code>true</code> for following-sibling, <code>false</code> for
   *        preceding-sibling.
   */
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
   * @return The document of the context nodes if all of them belong to it, are
   *         in strict document order and none is a namespace node;
   *         <code>null</code> otherwise.
   */
  @Nullable
  private static XdmDocumentNode _getCommonDocument (@NonNull final List <IXdmNode> aContextNodes)
  {
    if (!(aContextNodes.get (0).getRoot () instanceof final XdmDocumentNode aDoc))
      return null;
    int nPrevious = -1;
    for (final IXdmNode aNode : aContextNodes)
    {
      if (aNode.getKind () == ENodeKind.NAMESPACE || aNode.getOrder () <= nPrevious || aNode.getRoot () != aDoc)
        return null;
      nPrevious = aNode.getOrder ();
    }
    return aDoc;
  }

  /**
   * Add the nodes of a sorted index list whose order lies in the subtree
   * interval of a context node: <code>(getOrder (), getEndOrder ()]</code>,
   * or <code>[getOrder (), getEndOrder ()]</code> with bSelf. A context node
   * inside the interval of an earlier one adds nothing new and is skipped, so
   * the result is in document order and free of duplicates.
   */
  private static void _addInSubtrees (@NonNull final List <IXdmNode> aSorted,
                                      @NonNull final List <IXdmNode> aContextNodes,
                                      final boolean bSelf,
                                      @NonNull final List <IXdmNode> aOut)
  {
    final int nSize = aSorted.size ();
    int nPos = 0;
    int nCovered = -1;
    for (final IXdmNode aNode : aContextNodes)
    {
      final int nStart = aNode.getOrder ();
      if (nStart <= nCovered)
        continue;
      final int nEnd = aNode.getEndOrder ();
      nCovered = nEnd;
      nPos = XdmDocumentNode.getLowerBound (aSorted, nPos, bSelf ? nStart : nStart + 1);
      while (nPos < nSize && aSorted.get (nPos).getOrder () <= nEnd)
        aOut.add (aSorted.get (nPos++));
    }
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
   * @throws IllegalArgumentException
   *         for any other axis
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
    final XdmDocumentNode aDoc = _getCommonDocument (aContextNodes);
    if (aDoc == null)
      return false;
    // Optimisation: the descendants by name from the document's element index instead of a walk
    // over the subtrees. Built on demand; null if the tree is not numbered
    final List <IXdmNode> aElements = aDoc.getElementsByName (aName);
    if (aElements == null)
      return false;
    _addInSubtrees (aElements, aContextNodes, eAxis == EAxis.DESCENDANT_OR_SELF, aOut);
    return true;
  }

  /**
   * Collect the attributes with a name of the context nodes and their
   * descendants (the path <code>descendant-or-self::node()/attribute::N</code>,
   * as in <code>//@N</code>) from the attribute index of their document. The
   * attributes of an element are numbered after it and before its children,
   * so the attributes in the subtree of a node are those whose order lies in
   * <code>(getOrder (), getEndOrder ()]</code>.
   *
   * @param aContextNodes
   *        The context nodes in document order without duplicates.
   * @param aName
   *        The attribute name to select.
   * @param aOut
   *        The list to append to, in document order.
   * @return <code>true</code> if the nodes were collected, <code>false</code>
   *         (with nothing added) if the index cannot be used, as for
   *         {@link #collectNamedDescendants(EAxis, List, XdmQName, List)}.
   */
  public static boolean collectNamedSubtreeAttributes (@NonNull final List <IXdmNode> aContextNodes,
                                                       @NonNull final XdmQName aName,
                                                       @NonNull final List <IXdmNode> aOut)
  {
    if (aContextNodes.isEmpty ())
      return true;
    final XdmDocumentNode aDoc = _getCommonDocument (aContextNodes);
    if (aDoc == null)
      return false;
    // Optimisation: the attributes by name from the document's attribute index instead of a walk
    final List <IXdmNode> aAttributes = aDoc.getAttributesByName (aName);
    if (aAttributes == null)
      return false;
    _addInSubtrees (aAttributes, aContextNodes, false, aOut);
    return true;
  }

  /**
   * Collect the child elements with a name. The child elements of a node with
   * many children in a numbered document come from
   * {@link XdmDocumentNode#getChildElementsByName(IXdmNode, XdmQName)}, which
   * keeps them, so that a repeated step does not go through all children
   * again; otherwise the children are compared by name.
   *
   * @param aNode
   *        The parent node.
   * @param aName
   *        The element name to select.
   * @param aOut
   *        The list to append to, in document order.
   */
  public static void collectNamedChildren (@NonNull final IXdmNode aNode,
                                           @NonNull final XdmQName aName,
                                           @NonNull final List <IXdmNode> aOut)
  {
    final int nCount = aNode.getChildCount ();
    if (nCount == 0)
      return;
    // Optimisation for nodes with many children (the root of a large document): the kept child
    // elements by name, so that a repeated step does not scan all children again
    if (nCount > MIN_CHILDREN_FOR_INDEX && aNode.getRoot () instanceof final XdmDocumentNode aDoc)
    {
      final List <IXdmNode> aChildren = aDoc.getChildElementsByName (aNode, aName);
      if (aChildren != null)
      {
        aOut.addAll (aChildren);
        return;
      }
    }
    for (int i = 0; i < nCount; ++i)
    {
      // Optimisation: the element class is final, so a type check and a direct call instead of
      // two calls through the interface
      if (aNode.getChildAtIndex (i) instanceof final XdmElementNode aChild && aName.equals (aChild.getName ()))
        aOut.add (aChild);
    }
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
