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
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

import com.helger.phoxtrot.xdm.EIDKind;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * An attribute of an {@link XdmDomView}. Like any DOM attribute it has no parent and no siblings, and its value is
 * not shown as child nodes.
 *
 * @author Philip Helger
 */
final class XdmDomAttr extends AbstractXdmDomNode implements Attr
{
  XdmDomAttr (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode)
  {
    super (aView, aNode);
  }

  @Override
  boolean isAttributeLike ()
  {
    return true;
  }

  @Override
  IXdmNode getOwnerXdmElement ()
  {
    return getXdmNode ().getParent ();
  }

  public String getNodeName ()
  {
    return getXdmNode ().getName ().getLexicalName ();
  }

  public short getNodeType ()
  {
    return ATTRIBUTE_NODE;
  }

  @Override
  public String getNodeValue ()
  {
    return getXdmNode ().getValue ();
  }

  @Override
  public Node getParentNode ()
  {
    return null;
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
    return getXdmNode ().getValue ();
  }

  public void setValue (final String sValue)
  {
    throw createReadOnly ();
  }

  public Element getOwnerElement ()
  {
    final IXdmNode aParent = getXdmNode ().getParent ();
    return aParent == null ? null : (Element) getView ().internalWrap (aParent);
  }

  public TypeInfo getSchemaTypeInfo ()
  {
    return new XdmDomTypeInfo (getXdmNode ().getTypeAnnotation (), getXdmNode ().getSchemaType ());
  }

  public boolean isId ()
  {
    return getXdmNode ().getIDKind () == EIDKind.ID;
  }
}
