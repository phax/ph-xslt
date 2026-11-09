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
package com.helger.phoxtrot.xdm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Base class of the node kinds that have children: documents and elements.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract sealed class AbstractXdmParentNode extends AbstractXdmNode permits XdmDocumentNode, XdmElementNode
{
  // The children in the first m_nChildCount slots; null until the first child
  private AbstractXdmNode [] m_aChildren;
  private int m_nChildCount;
  // Order of the last node of the subtree, see getEndOrder
  private int m_nEndOrder;

  protected AbstractXdmParentNode ()
  {}

  /**
   * Store a child in the next slot, growing the array if it is full: the first child gets an array
   * of one (the common leaf element with one text node), the second one of four, and from then on
   * the array grows by half.
   */
  private void _addChild (@NonNull final AbstractXdmNode aChild)
  {
    if (m_aChildren == null)
      m_aChildren = new AbstractXdmNode [1];
    else
      if (m_nChildCount == m_aChildren.length)
        m_aChildren = Arrays.copyOf (m_aChildren, Math.max (4, m_nChildCount + (m_nChildCount >> 1)));
    m_aChildren[m_nChildCount++] = aChild;
  }

  /**
   * Remove the child in a slot, moving the later children one slot to the front.
   */
  private void _removeChildAt (@Nonnegative final int nIndex)
  {
    final int nMoved = m_nChildCount - nIndex - 1;
    if (nMoved > 0)
      System.arraycopy (m_aChildren, nIndex + 1, m_aChildren, nIndex, nMoved);
    m_aChildren[--m_nChildCount] = null;
  }

  /**
   * @return The slot of a child searched by identity, -1 if it is no child.
   */
  private int _indexOfChild (@NonNull final AbstractXdmNode aChild)
  {
    for (int i = 0; i < m_nChildCount; ++i)
      if (m_aChildren[i] == aChild)
        return i;
    return -1;
  }

  /**
   * Append a parentless node as the last child and make this node its parent. A numbering the
   * appended subtree had as a tree of its own is removed. If this tree was numbered, the numbering
   * of the whole tree and the name indexes of its document are removed too; both are assigned again
   * on demand.
   *
   * @throws IllegalArgumentException
   *         if the node already has a parent, or is an attribute, namespace or document node
   */
  @Override
  public final void appendChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    final AbstractXdmNode aRealChild = (AbstractXdmNode) aChild;
    if (aRealChild.getParent () != null)
      throw new IllegalArgumentException ("The child node already has a parent");
    switch (aRealChild.getKind ())
    {
      case ATTRIBUTE:
      case NAMESPACE:
      case DOCUMENT:
        throw new IllegalArgumentException ("A " + aRealChild.getKind () + " node cannot be a child");
      default:
        break;
    }
    // A subtree numbered as a tree of its own must not keep that numbering
    aRealChild.internalInvalidateOrder ();
    aRealChild.internalSetParent (this);
    _addChild (aRealChild);
    internalInvalidateOrder ();
  }

  /**
   * Detach a child, which becomes a parentless root of its own. The numbering of the whole tree,
   * the removed subtree included, and the name indexes of its document are removed first, so both
   * are assigned again on demand.
   *
   * @throws IllegalArgumentException
   *         if the node is not a child of this node
   */
  @Override
  public final void removeChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    final AbstractXdmNode aRealChild = (AbstractXdmNode) aChild;
    if (aRealChild.getParent () != this || m_aChildren == null)
      throw new IllegalArgumentException ("Not a child of this node");
    // Before detaching, so that the removed subtree is reset as well
    internalInvalidateOrder ();
    final int nIndex = _indexOfChild (aRealChild);
    if (nIndex < 0)
      throw new IllegalArgumentException ("Not a child of this node");
    _removeChildAt (nIndex);
    aRealChild.internalSetParent (null);
  }

  /**
   * Append a child to a tree that is numbered while it is built ({@link XdmStreamReader}): the
   * numbering stays.
   */
  final void internalAppendNumbered (@NonNull final AbstractXdmNode aChild)
  {
    aChild.internalSetParent (this);
    _addChild (aChild);
  }

  /**
   * Detach a child from a tree that is numbered while it is built ({@link XdmStreamReader}): the
   * numbering of the remaining nodes stays. The child is searched from the end, where a streamed
   * child normally is.
   *
   * @return <code>true</code> if it was a child.
   */
  final boolean internalDetachNumbered (@NonNull final AbstractXdmNode aChild)
  {
    for (int i = m_nChildCount - 1; i >= 0; --i)
      if (m_aChildren[i] == aChild)
      {
        _removeChildAt (i);
        aChild.internalSetParent (null);
        return true;
      }
    return false;
  }

  /**
   * Shrink the child array to the number of children if at least four slots are unused, for a
   * parent that gets no more children while its tree is built ({@link XdmTreeBuilder}). Smaller
   * gaps are kept, as copying the array would cost more than it saves.
   */
  final void internalTrimChildren ()
  {
    if (m_aChildren != null && m_aChildren.length - m_nChildCount >= 4)
      m_aChildren = Arrays.copyOf (m_aChildren, m_nChildCount);
  }

  final void internalSetEndOrder (@Nonnegative final int nEndOrder)
  {
    m_nEndOrder = nEndOrder;
  }

  @Override
  @Nonnegative
  public final int getEndOrder ()
  {
    return m_nEndOrder;
  }

  @Override
  @Nonnegative
  public final int getChildCount ()
  {
    return m_nChildCount;
  }

  @Override
  @NonNull
  public final AbstractXdmNode getChildAtIndex (@Nonnegative final int nIndex)
  {
    // Fails before the array is used, also when there are no children
    return m_aChildren[Objects.checkIndex (nIndex, m_nChildCount)];
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  public final ICommonsList <IXdmNode> getAllChildren ()
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> (m_nChildCount);
    for (int i = 0; i < m_nChildCount; ++i)
      ret.add (m_aChildren[i]);
    return ret;
  }

  /**
   * The concatenation of the descendant text nodes in document order, built iteratively; a node
   * with exactly one text child returns that value without copying, a node without children the
   * empty string. Comments, processing instructions and attributes do not contribute.
   */
  @NonNull
  public final String getStringValue ()
  {
    if (m_nChildCount == 0)
      return "";
    // The common leaf element with one text child
    if (m_nChildCount == 1 && m_aChildren[0] instanceof final XdmTextNode aText)
      return aText.getValue ();
    // Do not recurse - trees may be very deep
    final StringBuilder aSB = new StringBuilder ();
    final ArrayDeque <AbstractXdmNode> aStack = new ArrayDeque <> ();
    for (int i = m_nChildCount - 1; i >= 0; --i)
      aStack.push (m_aChildren[i]);
    while (!aStack.isEmpty ())
    {
      final AbstractXdmNode aCur = aStack.pop ();
      if (aCur instanceof final XdmTextNode aText)
        aSB.append (aText.getValue ());
      else
        if (aCur instanceof final XdmElementNode aElement)
          for (int i = aElement.getChildCount () - 1; i >= 0; --i)
            aStack.push (aElement.getChildAtIndex (i));
    }
    return aSB.toString ();
  }
}
