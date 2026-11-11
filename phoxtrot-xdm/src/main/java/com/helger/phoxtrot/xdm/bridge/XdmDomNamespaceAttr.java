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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * A namespace declaration of an {@link XdmDomView} as <code>xmlns</code> or <code>xmlns:prefix</code> attribute: the
 * declaration of an element in its attribute map (over a parentless namespace node), or an XDM namespace node of
 * the namespace axis (with its parent as owner element). The value is the namespace URI, empty to undeclare the
 * default namespace.
 *
 * @author Philip Helger
 */
final class XdmDomNamespaceAttr extends AbstractXdmDomNode implements Attr
{
  private final IXdmNode m_aOwner;

  /**
   * @param aView
   *        The view.
   * @param aNode
   *        The XDM namespace node.
   * @param aOwner
   *        The XDM element the declaration belongs to, <code>null</code> for a parentless namespace node.
   */
  XdmDomNamespaceAttr (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode, @Nullable final IXdmNode aOwner)
  {
    super (aView, aNode);
    m_aOwner = aOwner;
  }

  @NonNull
  private String _getDeclaredPrefix ()
  {
    final XdmQName aName = getXdmNode ().getName ();
    return aName == null ? "" : aName.localName ();
  }

  @Override
  boolean isAttributeLike ()
  {
    return true;
  }

  @Override
  IXdmNode getOwnerXdmElement ()
  {
    return m_aOwner;
  }

  @Override
  IXdmNode getNamespaceContextElement ()
  {
    return m_aOwner;
  }

  @Override
  IXdmNode getOrderNode ()
  {
    // A declaration of an element sorts with its element; an XDM namespace node has an order of its own
    return m_aOwner != null && getXdmNode ().getParent () == null ? m_aOwner : getXdmNode ();
  }

  public String getNodeName ()
  {
    final String sPrefix = _getDeclaredPrefix ();
    return sPrefix.isEmpty () ? "xmlns" : "xmlns:" + sPrefix;
  }

  public short getNodeType ()
  {
    return ATTRIBUTE_NODE;
  }

  @Override
  public String getNodeValue ()
  {
    return getValue ();
  }

  @Override
  public Node getParentNode ()
  {
    return null;
  }

  @Override
  public String getNamespaceURI ()
  {
    return XdmConstants.NS_XMLNS;
  }

  @Override
  public String getPrefix ()
  {
    return _getDeclaredPrefix ().isEmpty () ? null : "xmlns";
  }

  @Override
  public String getLocalName ()
  {
    final String sPrefix = _getDeclaredPrefix ();
    return sPrefix.isEmpty () ? "xmlns" : sPrefix;
  }

  @Override
  public String getBaseURI ()
  {
    return m_aOwner == null ? null : getBaseUri (m_aOwner);
  }

  public String getName ()
  {
    return getNodeName ();
  }

  public boolean getSpecified ()
  {
    return true;
  }

  public String getValue ()
  {
    return getXdmNode ().getStringValue ();
  }

  public void setValue (final String sValue)
  {
    throw createReadOnly ();
  }

  public Element getOwnerElement ()
  {
    return m_aOwner == null ? null : (Element) getView ().internalWrap (m_aOwner);
  }

  public TypeInfo getSchemaTypeInfo ()
  {
    return new XdmDomTypeInfo (null, null);
  }

  public boolean isId ()
  {
    return false;
  }
}
