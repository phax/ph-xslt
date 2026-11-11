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

import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import com.helger.phoxtrot.xdm.EIDKind;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * The document node of an {@link XdmDomView}. It has no document type and no DOM configuration
 * (<code>getDomConfig</code> returns <code>null</code>); its document URI is the one of the XDM document.
 *
 * @author Philip Helger
 */
final class XdmDomDocument extends AbstractXdmDomNode implements Document
{
  XdmDomDocument (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode)
  {
    super (aView, aNode);
  }

  @Override
  IXdmNode getNamespaceContextElement ()
  {
    final IXdmNode aNode = getXdmNode ();
    for (int i = 0; i < aNode.getChildCount (); ++i)
      if (aNode.getChildAtIndex (i).isElement ())
        return aNode.getChildAtIndex (i);
    return null;
  }

  public String getNodeName ()
  {
    return "#document";
  }

  public short getNodeType ()
  {
    return DOCUMENT_NODE;
  }

  @Override
  public Document getOwnerDocument ()
  {
    return null;
  }

  @Override
  public String getTextContent ()
  {
    return null;
  }

  @Override
  public void setTextContent (final String sTextContent)
  {
    // Setting a value that is defined as null has no effect
  }

  public DocumentType getDoctype ()
  {
    return null;
  }

  public DOMImplementation getImplementation ()
  {
    return XdmDomImplementation.INSTANCE;
  }

  public Element getDocumentElement ()
  {
    final IXdmNode aElement = getNamespaceContextElement ();
    return aElement == null ? null : (Element) getView ().internalWrap (aElement);
  }

  public Element createElement (final String sTagName)
  {
    throw createNotSupported ("createElement");
  }

  public DocumentFragment createDocumentFragment ()
  {
    throw createNotSupported ("createDocumentFragment");
  }

  public Text createTextNode (final String sData)
  {
    throw createNotSupported ("createTextNode");
  }

  public Comment createComment (final String sData)
  {
    throw createNotSupported ("createComment");
  }

  public CDATASection createCDATASection (final String sData)
  {
    throw createNotSupported ("createCDATASection");
  }

  public ProcessingInstruction createProcessingInstruction (final String sTarget, final String sData)
  {
    throw createNotSupported ("createProcessingInstruction");
  }

  public Attr createAttribute (final String sName)
  {
    throw createNotSupported ("createAttribute");
  }

  public EntityReference createEntityReference (final String sName)
  {
    throw createNotSupported ("createEntityReference");
  }

  public NodeList getElementsByTagName (final String sTagname)
  {
    return collectDescendantElements (createTagNameFilter (sTagname));
  }

  public Node importNode (final Node aImportedNode, final boolean bDeep)
  {
    throw createNotSupported ("importNode");
  }

  public Element createElementNS (final String sNamespaceUri, final String sQualifiedName)
  {
    throw createNotSupported ("createElementNS");
  }

  public Attr createAttributeNS (final String sNamespaceUri, final String sQualifiedName)
  {
    throw createNotSupported ("createAttributeNS");
  }

  public NodeList getElementsByTagNameNS (final String sNamespaceUri, final String sLocalName)
  {
    return collectDescendantElements (createNameNSFilter (sNamespaceUri, sLocalName));
  }

  /**
   * The first element in document order with an attribute of ID kind ID and the value, found by an iterative walk.
   */
  public Element getElementById (final String sElementId)
  {
    if (sElementId == null)
      return null;
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (getXdmNode ());
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
      {
        final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
        if (aAttr.getIDKind () == EIDKind.ID && sElementId.equals (aAttr.getValue ()))
          return (Element) getView ().internalWrap (aCur);
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        if (aCur.getChildAtIndex (i).isElement ())
          aStack.push (aCur.getChildAtIndex (i));
    }
    return null;
  }

  public String getInputEncoding ()
  {
    return null;
  }

  public String getXmlEncoding ()
  {
    return null;
  }

  public boolean getXmlStandalone ()
  {
    return false;
  }

  public void setXmlStandalone (final boolean bXmlStandalone)
  {
    throw createReadOnly ();
  }

  public String getXmlVersion ()
  {
    return "1.0";
  }

  public void setXmlVersion (final String sXmlVersion)
  {
    throw createReadOnly ();
  }

  public boolean getStrictErrorChecking ()
  {
    return true;
  }

  public void setStrictErrorChecking (final boolean bStrictErrorChecking)
  {
    throw createReadOnly ();
  }

  public String getDocumentURI ()
  {
    return getXdmNode ().getDocumentUri ();
  }

  public void setDocumentURI (final String sDocumentUri)
  {
    throw createReadOnly ();
  }

  public Node adoptNode (final Node aSource)
  {
    throw createReadOnly ();
  }

  public DOMConfiguration getDomConfig ()
  {
    return null;
  }

  public void normalizeDocument ()
  {
    // Nothing to do: XDM merges adjacent text already
  }

  public Node renameNode (final Node aNode, final String sNamespaceUri, final String sQualifiedName)
  {
    throw createReadOnly ();
  }
}
