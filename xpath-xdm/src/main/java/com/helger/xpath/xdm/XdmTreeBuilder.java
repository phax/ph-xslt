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
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;

/**
 * SAX2 handler that builds an {@link IXdmNode} tree. Adjacent character events
 * are merged into one text node, comments inside the DTD are dropped and the
 * location of an element is the position the parser reports at the end of its
 * start tag; a text node gets the position reported with its first character
 * event. DTD attribute types ID, IDREF and IDREFS are recorded, and the
 * value of such an attribute as well as of <code>xml:id</code> is normalised
 * like a tokenised attribute (XDM 3.1 section 6.3.3). Schema information is
 * added afterwards, by {@link com.helger.xpath.xdm.schema.IXdmSchemaSet}.
 * <p>
 * The nodes are numbered in document order as they are created, which gives
 * the same numbers as {@link IXdmNode#assignDocumentOrder()} on the finished
 * tree without a second pass. A handler that wraps the builder may change the
 * tree while it is built (e.g. drop a whitespace text node); such a change
 * removes the numbering, and the finished tree is then numbered at the end.
 * The name indexes of the document are only valid for the finished tree.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmTreeBuilder extends DefaultHandler2
{
  private final ICommonsList <String> m_aPendingPrefixes = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aPendingUris = new CommonsArrayList <> ();
  private final StringBuilder m_aText = new StringBuilder ();
  // Location of the first character event of the pending text
  private int m_nTextLine;
  private int m_nTextColumn;
  private Locator m_aLocator;
  private XdmDocumentNode m_aDocument;
  private AbstractXdmParentNode m_aCurrent;
  // The tree number of the numbering given while building; 0 once a change of
  // the tree from outside removed it
  private long m_nTree;
  // The next document order number
  private int m_nOrder;
  private boolean m_bInDTD = false;
  // The system ID in effect for each open element
  private final ArrayDeque <String> m_aSystemIds = new ArrayDeque <> ();
  // One shared name instance per namespace URI and lexical name, so that the
  // nodes of a document do not each carry their own copy
  private final ICommonsMap <String, ICommonsMap <String, XdmQName>> m_aNames = new CommonsHashMap <> ();

  /**
   * Give a new node the next document order number, as long as the tree is
   * numbered while it is built. A structural change through the public API
   * (by a wrapping handler) removes the numbering of the whole tree, the
   * document node included; from then on no node is numbered here, and
   * {@link #endDocument()} numbers the finished tree.
   */
  private void _stamp (@NonNull final AbstractXdmNode aNode)
  {
    if (m_nTree != 0)
    {
      if (m_aDocument.internalGetTree () == m_nTree)
        aNode.internalStamp (m_nTree, m_nOrder++);
      else
        m_nTree = 0;
    }
  }

  /**
   * Append a new node as the last child of the current node, keeping the
   * numbering of the tree, and number it.
   */
  private void _append (@NonNull final AbstractXdmNode aNode)
  {
    m_aCurrent.internalAppendNumbered (aNode);
    _stamp (aNode);
  }

  /**
   * Append the character data collected since the last node as one text node
   * to the current node, with the location of its first character event, and
   * clear the buffer. Nothing happens if no character data is pending, so no
   * empty text node is ever created.
   */
  private void _flushText ()
  {
    if (!m_aText.isEmpty ())
    {
      final XdmTextNode aText = new XdmTextNode (m_aText.toString ());
      if (m_aLocator != null)
        aText.setLocation (m_nTextLine, m_nTextColumn);
      _append (aText);
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
  private String _applyEntityBase (@NonNull final IXdmNode aNode)
  {
    final String sParentSystemId = m_aSystemIds.isEmpty () ? m_aDocument.getDocumentUri () : m_aSystemIds.peek ();
    if (m_aLocator == null)
      return sParentSystemId;
    final String sSystemId = m_aLocator.getSystemId ();
    if (sSystemId != null && !sSystemId.equals (sParentSystemId))
      aNode.setBaseUri (sSystemId);
    return sSystemId;
  }

  /**
   * @param sQName
   *        A lexical QName as reported by SAX.
   * @return The part before the first colon, or the empty string if there is
   *         no colon.
   */
  @NonNull
  private static String _getPrefix (@NonNull final String sQName)
  {
    final int nIndex = sQName.indexOf (':');
    return nIndex < 0 ? "" : sQName.substring (0, nIndex);
  }

  /**
   * Get the shared name instance for a namespace URI and lexical name,
   * creating it on first use with canonical strings
   * ({@link XdmQName#getInterned()}), as the names of node tests have them.
   * The cache key includes the prefix, so that each node keeps the prefix used
   * in the source.
   *
   * @param sUri
   *        Namespace URI, empty for none.
   * @param sLocalName
   *        Local part.
   * @param sQName
   *        Lexical name as written in the source.
   * @return The name. Never <code>null</code>.
   */
  @NonNull
  private XdmQName _getName (@NonNull final String sUri, @NonNull final String sLocalName, @NonNull final String sQName)
  {
    // Keyed by the lexical name and not by XdmQName, whose equals ignores the
    // prefix
    return m_aNames.computeIfAbsent (sUri, k -> new CommonsHashMap <> ())
                   .computeIfAbsent (sQName, k -> new XdmQName (sUri, sLocalName, _getPrefix (sQName)).getInterned ());
  }

  /**
   * Map the DTD attribute type reported by SAX to the ID kind.
   *
   * @param sSaxType
   *        The attribute type from {@link Attributes#getType(int)}; may be
   *        <code>null</code>.
   * @return {@link EIDKind#ID}, {@link EIDKind#IDREF} or
   *         {@link EIDKind#IDREFS} for these types, {@link EIDKind#NONE} for
   *         every other type (<code>CDATA</code> for undeclared attributes).
   */
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
  public IXdmNode getCurrentNode ()
  {
    return m_aCurrent;
  }

  /**
   * @return The finished document. Only valid after the parse run completed.
   */
  @NonNull
  public IXdmNode getDocument ()
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

  /**
   * Create the document node, which becomes the current node, with the system
   * ID of the locator (if any) as document URI. It is the first node of a new
   * numbering; its end order stays open until {@link #endDocument()}.
   */
  @Override
  public void startDocument ()
  {
    m_aDocument = new XdmDocumentNode ();
    m_aCurrent = m_aDocument;
    if (m_aLocator != null)
      m_aDocument.setDocumentUri (m_aLocator.getSystemId ());
    m_nTree = AbstractXdmNode.internalNewTree ();
    m_nOrder = 0;
    m_aDocument.internalStamp (m_nTree, m_nOrder++);
    m_aDocument.internalSetEndOrder (Integer.MAX_VALUE);
  }

  /**
   * Append pending text and finish the numbering: the document ends with the
   * last node numbered. If a change from outside removed the numbering while
   * building, the finished tree is numbered in a second pass
   * ({@link IXdmNode#assignDocumentOrder()}). Name indexes asked for while
   * building would miss the later nodes and are dropped.
   */
  @Override
  public void endDocument ()
  {
    _flushText ();
    if (m_nTree != 0 && m_aDocument.internalGetTree () == m_nTree)
    {
      m_aDocument.internalSetEndOrder (m_nOrder - 1);
      m_aDocument.internalDropNameIndex ();
    }
    else
      m_aDocument.assignDocumentOrder ();
  }

  /**
   * Remember the mapping until the next {@link #startElement}, which declares
   * it on the new element. An empty URI (undeclaring the default namespace)
   * is kept as it is.
   */
  @Override
  public void startPrefixMapping (final String sPrefix, final String sUri)
  {
    m_aPendingPrefixes.add (sPrefix);
    m_aPendingUris.add (sUri);
  }

  /**
   * Append pending text, then create the element with the location reported
   * at the end of its start tag, the namespace declarations collected by
   * {@link #startPrefixMapping(String, String)} and the base URI of an entity
   * boundary. Attributes follow in the reported order, without the
   * <code>xmlns</code> declarations: a DTD type ID, IDREF or IDREFS, and an
   * <code>xml:id</code> attribute (always an ID), set the ID kind and
   * normalise the value with {@link #normaliseTokenised(String)}. The element
   * is appended to the current node, numbered followed by its attributes, and
   * becomes the current node.
   */
  @Override
  public void startElement (final String sUri,
                            final String sLocalName,
                            final String sQName,
                            final Attributes aAttrs)
  {
    _flushText ();
    final XdmElementNode aElement = new XdmElementNode (_getName (sUri, sLocalName, sQName));
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
      final XdmQName aAttrName = _getName (aAttrs.getURI (i), aAttrs.getLocalName (i), sAttrQName);
      final String sValue = aAttrs.getValue (i);
      EIDKind eIDKind = _getIDKind (aAttrs.getType (i));
      if (eIDKind == EIDKind.NONE && aAttrName.isSameName (XdmConstants.NS_XML, "id"))
        eIDKind = EIDKind.ID;
      final XdmAttributeNode aAttr = new XdmAttributeNode (aAttrName,
                                                           eIDKind == EIDKind.NONE ? sValue : normaliseTokenised (sValue));
      aAttr.setIDKind (eIDKind);
      if (m_aLocator != null)
        aAttr.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
      // The element is not numbered yet, so this keeps the numbering
      aElement.addAttribute (aAttr);
    }
    _append (aElement);
    for (int i = 0; i < aElement.getAttributeCount (); ++i)
      _stamp (aElement.getAttributeAtIndex (i));
    aElement.internalSetEndOrder (Integer.MAX_VALUE);
    m_aCurrent = aElement;
    m_aSystemIds.push (sSystemId == null ? "" : sSystemId);
  }

  /**
   * Append pending text, end the closed element with the last node numbered
   * and make its parent the current node.
   */
  @Override
  public void endElement (final String sUri, final String sLocalName, final String sQName)
  {
    _flushText ();
    m_aCurrent.internalSetEndOrder (m_nOrder - 1);
    m_aCurrent = (AbstractXdmParentNode) m_aCurrent.getParent ();
    m_aSystemIds.pop ();
  }

  /**
   * Collect character data for the next text node; the location of the first
   * event after a flush becomes the location of that node.
   */
  @Override
  public void characters (final char [] aChars, final int nStart, final int nLength)
  {
    if (m_aText.isEmpty () && m_aLocator != null)
    {
      m_nTextLine = m_aLocator.getLineNumber ();
      m_nTextColumn = m_aLocator.getColumnNumber ();
    }
    m_aText.append (aChars, nStart, nLength);
  }

  /**
   * Dropped: this is whitespace in element-only content, and XDM 3.1 section
   * 6.7.3 discards such text.
   */
  @Override
  public void ignorableWhitespace (final char [] aChars, final int nStart, final int nLength)
  {
    // A schema validator, and the parser for an element declared with element
    // content in the DTD, report whitespace in element-only content this way.
    // XDM 3.1 section 6.7.3 drops it: such a text node has empty content and
    // is discarded
  }

  /**
   * Record an unparsed entity on the document; a declaration without name or
   * system ID, or before the document node exists, is ignored. The notation
   * name is not kept.
   */
  @Override
  public void unparsedEntityDecl (final String sName, final String sPublicId, final String sSystemId, final String sNotationName)
  {
    if (m_aDocument != null && sName != null && sSystemId != null)
      m_aDocument.addUnparsedEntity (sName, sSystemId, sPublicId);
  }

  /**
   * Record the name of an internal general entity on the document; parameter
   * entities (name starting with <code>%</code>) are ignored.
   */
  @Override
  public void internalEntityDecl (final String sName, final String sValue)
  {
    if (m_aDocument != null && sName != null && !sName.startsWith ("%"))
      m_aDocument.addParsedEntityName (sName);
  }

  /**
   * Record the name of an external parsed general entity on the document;
   * parameter entities (name starting with <code>%</code>) are ignored.
   */
  @Override
  public void externalEntityDecl (final String sName, final String sPublicId, final String sSystemId)
  {
    if (m_aDocument != null && sName != null && !sName.startsWith ("%"))
      m_aDocument.addParsedEntityName (sName);
  }

  /**
   * Append pending text, then a processing instruction node (missing data
   * becomes the empty string) with the current location and the base URI of
   * an entity boundary.
   */
  @Override
  public void processingInstruction (final String sTarget, final String sData)
  {
    _flushText ();
    final XdmProcessingInstructionNode aPI = new XdmProcessingInstructionNode (new XdmQName (sTarget),
                                                                              sData == null ? "" : sData);
    if (m_aLocator != null)
      aPI.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    _applyEntityBase (aPI);
    _append (aPI);
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

  /**
   * Append pending text, then a comment node with the current location.
   * Comments inside the DTD are dropped.
   */
  @Override
  public void comment (final char [] aChars, final int nStart, final int nLength)
  {
    if (m_bInDTD)
      return;
    _flushText ();
    final XdmCommentNode aComment = new XdmCommentNode (new String (aChars, nStart, nLength));
    if (m_aLocator != null)
      aComment.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    _append (aComment);
  }
}
