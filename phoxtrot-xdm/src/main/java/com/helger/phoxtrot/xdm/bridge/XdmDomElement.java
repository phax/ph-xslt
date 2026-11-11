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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;

import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * An element of an {@link XdmDomView}.
 *
 * @author Philip Helger
 */
final class XdmDomElement extends AbstractXdmDomNode implements Element
{
  XdmDomElement (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode)
  {
    super (aView, aNode);
  }

  @Override
  IXdmNode getNamespaceContextElement ()
  {
    return getXdmNode ();
  }

  public String getNodeName ()
  {
    return getXdmNode ().getName ().getLexicalName ();
  }

  public short getNodeType ()
  {
    return ELEMENT_NODE;
  }

  @Override
  public NamedNodeMap getAttributes ()
  {
    return getView ().internalGetAttributeMap (this);
  }

  @Override
  public String getNamespaceURI ()
  {
    return nullIfEmpty (getXdmNode ().getName ().namespaceUri ());
  }

  @Override
  public String getPrefix ()
  {
    return nullIfEmpty (getXdmNode ().getName ().prefix ());
  }

  @Override
  public String getLocalName ()
  {
    return getXdmNode ().getName ().localName ();
  }

  @Override
  public boolean hasAttributes ()
  {
    return getXdmNode ().getAttributeCount () > 0 || getXdmNode ().hasDeclaredNamespaces ();
  }

  public String getTagName ()
  {
    return getNodeName ();
  }

  public String getAttribute (final String sName)
  {
    final Attr aAttr = getAttributeNode (sName);
    return aAttr == null ? "" : aAttr.getValue ();
  }

  public void setAttribute (final String sName, final String sValue)
  {
    throw createReadOnly ();
  }

  public void removeAttribute (final String sName)
  {
    throw createReadOnly ();
  }

  public Attr getAttributeNode (final String sName)
  {
    return (Attr) getAttributes ().getNamedItem (sName);
  }

  public Attr setAttributeNode (final Attr aNewAttr)
  {
    throw createReadOnly ();
  }

  public Attr removeAttributeNode (final Attr aOldAttr)
  {
    throw createReadOnly ();
  }

  public NodeList getElementsByTagName (final String sName)
  {
    return collectDescendantElements (createTagNameFilter (sName));
  }

  public String getAttributeNS (final String sNamespaceUri, final String sLocalName)
  {
    final Attr aAttr = getAttributeNodeNS (sNamespaceUri, sLocalName);
    return aAttr == null ? "" : aAttr.getValue ();
  }

  public void setAttributeNS (final String sNamespaceUri, final String sQualifiedName, final String sValue)
  {
    throw createReadOnly ();
  }

  public void removeAttributeNS (final String sNamespaceUri, final String sLocalName)
  {
    throw createReadOnly ();
  }

  public Attr getAttributeNodeNS (final String sNamespaceUri, final String sLocalName)
  {
    return (Attr) getAttributes ().getNamedItemNS (sNamespaceUri, sLocalName);
  }

  public Attr setAttributeNodeNS (final Attr aNewAttr)
  {
    throw createReadOnly ();
  }

  public NodeList getElementsByTagNameNS (final String sNamespaceUri, final String sLocalName)
  {
    return collectDescendantElements (createNameNSFilter (sNamespaceUri, sLocalName));
  }

  public boolean hasAttribute (final String sName)
  {
    return getAttributeNode (sName) != null;
  }

  public boolean hasAttributeNS (final String sNamespaceUri, final String sLocalName)
  {
    return getAttributeNodeNS (sNamespaceUri, sLocalName) != null;
  }

  public TypeInfo getSchemaTypeInfo ()
  {
    return new XdmDomTypeInfo (getXdmNode ().getTypeAnnotation (), getXdmNode ().getSchemaType ());
  }

  public void setIdAttribute (final String sName, final boolean bIsId)
  {
    throw createReadOnly ();
  }

  public void setIdAttributeNS (final String sNamespaceUri, final String sLocalName, final boolean bIsId)
  {
    throw createReadOnly ();
  }

  public void setIdAttributeNode (final Attr aIdAttr, final boolean bIsId)
  {
    throw createReadOnly ();
  }
}
