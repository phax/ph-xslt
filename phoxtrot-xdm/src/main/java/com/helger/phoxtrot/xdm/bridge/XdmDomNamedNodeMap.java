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
package com.helger.phoxtrot.xdm.bridge;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * The attribute map of an element of an {@link XdmDomView}: the namespace declarations of the element as
 * <code>xmlns</code> attributes, then its attributes, as a namespace aware DOM parser gives them. Read-only.
 *
 * @author Philip Helger
 */
@Immutable
final class XdmDomNamedNodeMap implements NamedNodeMap
{
  private final AbstractXdmDomNode [] m_aAttrs;

  private XdmDomNamedNodeMap (@NonNull final AbstractXdmDomNode [] aAttrs)
  {
    m_aAttrs = aAttrs;
  }

  /**
   * @param aElement
   *        The DOM node of an element.
   * @return The new attribute map of the element. Its namespace declaration attributes are created here, so the map
   *         is built once per element and view ({@link XdmDomView}).
   */
  @NonNull
  static XdmDomNamedNodeMap createFor (@NonNull final XdmDomElement aElement)
  {
    final IXdmNode aNode = aElement.getXdmNode ();
    final XdmDomView aView = aElement.getView ();
    final int nDecls;
    final AbstractXdmDomNode [] aAttrs;
    if (aNode.hasDeclaredNamespaces ())
    {
      final Map <String, String> aDecls = aNode.declaredNamespaces ();
      nDecls = aDecls.size ();
      aAttrs = new AbstractXdmDomNode [nDecls + aNode.getAttributeCount ()];
      int i = 0;
      for (final Map.Entry <String, String> aEntry : aDecls.entrySet ())
        aAttrs[i++] = new XdmDomNamespaceAttr (aView,
                                               IXdmNode.createNamespace (aEntry.getKey (), aEntry.getValue ()),
                                               aNode);
    }
    else
    {
      nDecls = 0;
      aAttrs = new AbstractXdmDomNode [aNode.getAttributeCount ()];
    }
    for (int i = 0; i < aNode.getAttributeCount (); ++i)
      aAttrs[nDecls + i] = aView.internalWrap (aNode.getAttributeAtIndex (i));
    return new XdmDomNamedNodeMap (aAttrs);
  }

  /**
   * @param aNode
   *        The DOM node to find.
   * @return Its position in this map, -1 if it is not in it.
   */
  int indexOf (@NonNull final Node aNode)
  {
    for (int i = 0; i < m_aAttrs.length; ++i)
      if (m_aAttrs[i] == aNode)
        return i;
    return -1;
  }

  @Nullable
  public Node getNamedItem (final String sName)
  {
    for (final AbstractXdmDomNode aAttr : m_aAttrs)
      if (aAttr.getNodeName ().equals (sName))
        return aAttr;
    return null;
  }

  public Node setNamedItem (final Node aArg)
  {
    throw AbstractXdmDomNode.createReadOnly ();
  }

  public Node removeNamedItem (final String sName)
  {
    throw AbstractXdmDomNode.createReadOnly ();
  }

  @Nullable
  public Node item (final int nIndex)
  {
    return nIndex < 0 || nIndex >= m_aAttrs.length ? null : m_aAttrs[nIndex];
  }

  public int getLength ()
  {
    return m_aAttrs.length;
  }

  @Nullable
  public Node getNamedItemNS (final String sNamespaceUri, final String sLocalName)
  {
    final String sNs = AbstractXdmDomNode.nullIfEmpty (sNamespaceUri);
    for (final AbstractXdmDomNode aAttr : m_aAttrs)
      if (aAttr.getLocalName ().equals (sLocalName) &&
          (sNs == null ? aAttr.getNamespaceURI () == null : sNs.equals (aAttr.getNamespaceURI ())))
        return aAttr;
    return null;
  }

  public Node setNamedItemNS (final Node aArg)
  {
    throw AbstractXdmDomNode.createReadOnly ();
  }

  public Node removeNamedItemNS (final String sNamespaceUri, final String sLocalName)
  {
    throw AbstractXdmDomNode.createReadOnly ();
  }
}
