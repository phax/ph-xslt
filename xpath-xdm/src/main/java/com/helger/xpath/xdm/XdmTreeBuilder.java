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
import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * SAX2 handler that builds an {@link XdmNode} tree. Adjacent character events
 * are merged into one text node, comments inside the DTD are dropped and the
 * location of an element is the position the parser reports at the end of its
 * start tag. DTD attribute types ID, IDREF and IDREFS are recorded, and the
 * value of such an attribute as well as of <code>xml:id</code> is normalised
 * like a tokenised attribute (XDM 3.1 section 6.3.3). Schema information is
 * added afterwards, by {@link com.helger.xpath.xdm.schema.IXdmSchemaSet}.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmTreeBuilder extends DefaultHandler2
{
  private final ICommonsList <String> m_aPendingPrefixes = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aPendingUris = new CommonsArrayList <> ();
  private final StringBuilder m_aText = new StringBuilder ();
  private Locator m_aLocator;
  private XdmNode m_aDocument;
  private XdmNode m_aCurrent;
  private boolean m_bInDTD = false;
  // The system ID in effect for each open element
  private final ArrayDeque <String> m_aSystemIds = new ArrayDeque <> ();

  private void _flushText ()
  {
    if (!m_aText.isEmpty ())
    {
      m_aCurrent.appendChild (XdmNode.createText (m_aText.toString ()));
      m_aText.setLength (0);
    }
  }

  /**
   * A node read from an external parsed entity gets the entity's URI as base
   * URI, but only the outermost node of the entity: its descendants inherit it
   * (and any <code>xml:base</code> in between) through the parent chain.
   *
   * @return The system ID in effect for the node.
   */
  @Nullable
  private String _applyEntityBase (@NonNull final XdmNode aNode)
  {
    final String sParentSystemId = m_aSystemIds.isEmpty () ? m_aDocument.getDocumentUri () : m_aSystemIds.peek ();
    if (m_aLocator == null)
      return sParentSystemId;
    final String sSystemId = m_aLocator.getSystemId ();
    if (sSystemId != null && !sSystemId.equals (sParentSystemId))
      aNode.setBaseUri (sSystemId);
    return sSystemId;
  }

  @NonNull
  private static String _getPrefix (@NonNull final String sQName)
  {
    final int nIndex = sQName.indexOf (':');
    return nIndex < 0 ? "" : sQName.substring (0, nIndex);
  }

  @NonNull
  private static EIDKind _getIDKind (@Nullable final String sSaxType)
  {
    if (sSaxType != null)
      switch (sSaxType)
      {
        case "ID":
          return EIDKind.ID;
        case "IDREF":
          return EIDKind.IDREF;
        case "IDREFS":
          return EIDKind.IDREFS;
        default:
          break;
      }
    return EIDKind.NONE;
  }

  /**
   * Normalise the value of an attribute of a tokenised type.
   *
   * @param sValue
   *        The attribute value after XML attribute value normalisation.
   * @return The value without leading and trailing spaces and with runs of
   *         spaces collapsed.
   */
  @NonNull
  public static String normaliseTokenised (@NonNull final String sValue)
  {
    // Trim and collapse runs of spaces; XML attribute normalisation already
    // turned all other whitespace characters into spaces
    if (sValue.indexOf (' ') < 0)
      return sValue;
    final StringBuilder aSB = new StringBuilder (sValue.length ());
    boolean bPendingSpace = false;
    for (int i = 0; i < sValue.length (); ++i)
    {
      final char c = sValue.charAt (i);
      if (c == ' ')
        bPendingSpace = !aSB.isEmpty ();
      else
      {
        if (bPendingSpace)
          aSB.append (' ');
        bPendingSpace = false;
        aSB.append (c);
      }
    }
    return aSB.toString ();
  }

  /**
   * @return The node that content is currently added to: the innermost open
   *         element, or the document node. For handlers that wrap the
   *         builder.
   */
  @Nullable
  public XdmNode getCurrentNode ()
  {
    return m_aCurrent;
  }

  /**
   * @return The finished document. Only valid after the parse run completed.
   */
  @NonNull
  public XdmNode getDocument ()
  {
    if (m_aDocument == null)
      throw new IllegalStateException ("No document was built yet");
    return m_aDocument;
  }

  @Override
  public void setDocumentLocator (final Locator aLocator)
  {
    m_aLocator = aLocator;
  }

  @Override
  public void startDocument ()
  {
    m_aDocument = XdmNode.createDocument ();
    m_aCurrent = m_aDocument;
    if (m_aLocator != null)
      m_aDocument.setDocumentUri (m_aLocator.getSystemId ());
  }

  @Override
  public void endDocument ()
  {
    _flushText ();
    m_aDocument.assignDocumentOrder ();
  }

  @Override
  public void startPrefixMapping (final String sPrefix, final String sUri)
  {
    m_aPendingPrefixes.add (sPrefix);
    m_aPendingUris.add (sUri);
  }

  @Override
  public void startElement (final String sUri,
                            final String sLocalName,
                            final String sQName,
                            final Attributes aAttrs)
  {
    _flushText ();
    final XdmNode aElement = XdmNode.createElement (new XdmQName (sUri, sLocalName, _getPrefix (sQName)));
    if (m_aLocator != null)
      aElement.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    for (int i = 0; i < m_aPendingPrefixes.size (); ++i)
      aElement.declareNamespace (m_aPendingPrefixes.get (i), m_aPendingUris.get (i));
    m_aPendingPrefixes.clear ();
    m_aPendingUris.clear ();
    final String sSystemId = _applyEntityBase (aElement);
    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      final String sAttrQName = aAttrs.getQName (i);
      // Namespace declarations are not attributes in XDM
      if (sAttrQName.equals ("xmlns") || sAttrQName.startsWith ("xmlns:"))
        continue;
      final XdmQName aAttrName = new XdmQName (aAttrs.getURI (i), aAttrs.getLocalName (i), _getPrefix (sAttrQName));
      final String sValue = aAttrs.getValue (i);
      EIDKind eIDKind = _getIDKind (aAttrs.getType (i));
      if (eIDKind == EIDKind.NONE && aAttrName.isSameName (XdmConstants.NS_XML, "id"))
        eIDKind = EIDKind.ID;
      final XdmNode aAttr = XdmNode.createAttribute (aAttrName,
                                                     eIDKind == EIDKind.NONE ? sValue : normaliseTokenised (sValue));
      aAttr.setIDKind (eIDKind);
      if (m_aLocator != null)
        aAttr.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
      aElement.addAttribute (aAttr);
    }
    m_aCurrent.appendChild (aElement);
    m_aCurrent = aElement;
    m_aSystemIds.push (sSystemId == null ? "" : sSystemId);
  }

  @Override
  public void endElement (final String sUri, final String sLocalName, final String sQName)
  {
    _flushText ();
    m_aCurrent = m_aCurrent.getParent ();
    m_aSystemIds.pop ();
  }

  @Override
  public void characters (final char [] aChars, final int nStart, final int nLength)
  {
    m_aText.append (aChars, nStart, nLength);
  }

  @Override
  public void ignorableWhitespace (final char [] aChars, final int nStart, final int nLength)
  {
    // A schema validator, and the parser for an element declared with element
    // content in the DTD, report whitespace in element-only content this way.
    // XDM 3.1 section 6.7.3 drops it: such a text node has empty content and
    // is discarded
  }

  @Override
  public void unparsedEntityDecl (final String sName, final String sPublicId, final String sSystemId, final String sNotationName)
  {
    if (m_aDocument != null && sName != null && sSystemId != null)
      m_aDocument.addUnparsedEntity (sName, sSystemId, sPublicId);
  }

  @Override
  public void internalEntityDecl (final String sName, final String sValue)
  {
    if (m_aDocument != null && sName != null && !sName.startsWith ("%"))
      m_aDocument.addParsedEntityName (sName);
  }

  @Override
  public void externalEntityDecl (final String sName, final String sPublicId, final String sSystemId)
  {
    if (m_aDocument != null && sName != null && !sName.startsWith ("%"))
      m_aDocument.addParsedEntityName (sName);
  }

  @Override
  public void processingInstruction (final String sTarget, final String sData)
  {
    _flushText ();
    final XdmNode aPI = XdmNode.createProcessingInstruction (sTarget, sData == null ? "" : sData);
    if (m_aLocator != null)
      aPI.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    _applyEntityBase (aPI);
    m_aCurrent.appendChild (aPI);
  }

  @Override
  public void startDTD (final String sName, final String sPublicId, final String sSystemId) throws SAXException
  {
    m_bInDTD = true;
  }

  @Override
  public void endDTD ()
  {
    m_bInDTD = false;
  }

  @Override
  public void comment (final char [] aChars, final int nStart, final int nLength)
  {
    if (m_bInDTD)
      return;
    _flushText ();
    final XdmNode aComment = XdmNode.createComment (new String (aChars, nStart, nLength));
    if (m_aLocator != null)
      aComment.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    m_aCurrent.appendChild (aComment);
  }
}
