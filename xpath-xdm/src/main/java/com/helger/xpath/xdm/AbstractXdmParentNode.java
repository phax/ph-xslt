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

  AbstractXdmParentNode ()
  {}

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
    aRealChild.internalSetParent (this);
    if (m_aChildren == null)
      m_aChildren = new CommonsArrayList <> ();
    m_aChildren.add (aRealChild);
  }

  @Override
  public final void removeChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    final AbstractXdmNode aRealChild = (AbstractXdmNode) aChild;
    if (aRealChild.getParent () != this ||
        m_aChildren == null ||
        !m_aChildren.removeObject (aRealChild).isChanged ())
      throw new IllegalArgumentException ("Not a child of this node");
    aRealChild.internalSetParent (null);
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

  @NonNull
  public final String getStringValue ()
  {
    if (m_aChildren == null)
      return "";
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
