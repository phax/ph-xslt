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
package com.helger.xpath.xdm;

import java.util.ArrayDeque;

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
  private ICommonsList <AbstractXdmNode> m_aChildren;
  // Order of the last node of the subtree, see getEndOrder
  private int m_nEndOrder;

  AbstractXdmParentNode ()
  {}

  /**
   * Append a parentless node as the last child and make this node its parent.
   * A numbering the appended subtree had as a tree of its own is removed. If
   * this tree was numbered, the numbering of the whole tree and the name
   * indexes of its document are removed too; both are assigned again on
   * demand.
   *
   * @throws IllegalArgumentException
   *         if the node already has a parent, or is an attribute, namespace or
   *         document node
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
    if (m_aChildren == null)
      m_aChildren = new CommonsArrayList <> ();
    m_aChildren.add (aRealChild);
    internalInvalidateOrder ();
  }

  /**
   * Detach a child, which becomes a parentless root of its own. The numbering
   * of the whole tree, the removed subtree included, and the name indexes of
   * its document are removed first, so both are assigned again on demand.
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
    if (!m_aChildren.removeObject (aRealChild).isChanged ())
      throw new IllegalArgumentException ("Not a child of this node");
    aRealChild.internalSetParent (null);
  }

  /**
   * Append a child to a tree that is numbered while it is built
   * ({@link XdmStreamReader}): the numbering stays.
   */
  final void internalAppendNumbered (@NonNull final AbstractXdmNode aChild)
  {
    aChild.internalSetParent (this);
    if (m_aChildren == null)
      m_aChildren = new CommonsArrayList <> ();
    m_aChildren.add (aChild);
  }

  /**
   * Detach a child from a tree that is numbered while it is built
   * ({@link XdmStreamReader}): the numbering of the remaining nodes stays. The
   * child is searched from the end, where a streamed child normally is.
   *
   * @return <code>true</code> if it was a child.
   */
  final boolean internalDetachNumbered (@NonNull final AbstractXdmNode aChild)
  {
    if (m_aChildren == null)
      return false;
    for (int i = m_aChildren.size () - 1; i >= 0; --i)
      if (m_aChildren.get (i) == aChild)
      {
        m_aChildren.remove (i);
        aChild.internalSetParent (null);
        return true;
      }
    return false;
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
    return m_aChildren == null ? 0 : m_aChildren.size ();
  }

  @Override
  @NonNull
  public final AbstractXdmNode getChildAtIndex (@Nonnegative final int nIndex)
  {
    if (m_aChildren == null)
      throw new IndexOutOfBoundsException ("No children");
    return m_aChildren.get (nIndex);
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  public final ICommonsList <IXdmNode> getAllChildren ()
  {
    return m_aChildren == null ? new CommonsArrayList <> () : new CommonsArrayList <> (m_aChildren);
  }

  /**
   * The concatenation of the descendant text nodes in document order, built
   * iteratively; a node with exactly one text child returns that value
   * without copying, a node without children the empty string. Comments,
   * processing instructions and attributes do not contribute.
   */
  @NonNull
  public final String getStringValue ()
  {
    if (m_aChildren == null)
      return "";
    // The common leaf element with one text child
    if (m_aChildren.size () == 1 && m_aChildren.get (0) instanceof final XdmTextNode aText)
      return aText.getValue ();
    // Do not recurse - trees may be very deep
    final StringBuilder aSB = new StringBuilder ();
    final ArrayDeque <AbstractXdmNode> aStack = new ArrayDeque <> ();
    for (int i = m_aChildren.size () - 1; i >= 0; --i)
      aStack.push (m_aChildren.get (i));
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
