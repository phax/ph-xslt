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
 * SAX2 handler that builds an {@link IXdmNode} tree. Adjacent character events are merged into one
 * text node, comments inside the DTD are dropped and the location of an element (if locations are
 * recorded) is the position the parser reports at the end of its start tag, which its attributes
 * and text children report as well (they have no location of their own); comments and processing
 * instructions get the position reported with their event. DTD attribute types ID, IDREF and IDREFS
 * are recorded, and the value of such an attribute as well as of <code>xml:id</code> is normalised
 * like a tokenised attribute (XDM 3.1 section 6.3.3). Schema information is added afterwards, by
 * {@link com.helger.phoxtrot.xdm.schema.IXdmSchemaSet}.
 * <p>
 * The nodes are numbered in document order as they are created, which gives the same numbers as
 * {@link IXdmNode#assignDocumentOrder()} on the finished tree without a second pass. A handler that
 * wraps the builder may change the tree while it is built (e.g. drop a whitespace text node); such
 * a change removes the numbering, and the finished tree is then numbered at the end. The name
 * indexes of the document are only valid for the finished tree.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmTreeBuilder extends DefaultHandler2
{
  // Indentation text up to this length is shared, see _getText
  private static final int MAX_SHARED_INDENT = 64;
  // Attribute values up to this length are shared, see _getShared
  private static final int MAX_SHARED_VALUE = 32;
  // Slots of the table of shared values (a power of two)
  private static final int SHARED_VALUE_SLOTS = 1024;

  private final ICommonsList <String> m_aPendingPrefixes = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aPendingUris = new CommonsArrayList <> ();
  // The pending text of a single character event; null if there is none or
  // more than one event was merged into m_aText
  private String m_sText;
  // The pending text of several character events
  private final StringBuilder m_aText = new StringBuilder ();
  // The shared indentation strings (a line end and spaces, or a line end and
  // tabs) by their length - 1
  private final String [] m_aSpaceIndents = new String [MAX_SHARED_INDENT];
  private final String [] m_aTabIndents = new String [MAX_SHARED_INDENT];
  // The shared short attribute values, by the low bits of their hash code;
  // created on first use
  private String [] m_aSharedValues;
  private Locator m_aLocator;
  private XdmDocumentNode m_aDocument;
  private AbstractXdmParentNode m_aCurrent;
  // The numbering given while building; null once a change of the tree from
  // outside removed it
  private AbstractXdmNode.TreeStamp m_aTree;
  // The next document order number
  private int m_nOrder;
  private boolean m_bInDTD = false;
  // The number of open elements, and the maximum of it so far
  private int m_nDepth;
  private int m_nMaxDepth;
  // The system ID in effect for each open element
  private final ArrayDeque <String> m_aSystemIds = new ArrayDeque <> ();
  // One shared name instance per namespace URI and lexical name, so that the
  // nodes of a document do not each carry their own copy: an open addressing
  // table of the three parallel arrays, at most half full
  private String [] m_aNameUris = new String [64];
  private String [] m_aNameQNames = new String [64];
  private XdmQName [] m_aNames = new XdmQName [64];
  private int m_nNameCount;
  // The attributes of the element being started, before they are set on it
  private AbstractXdmNode [] m_aAttrBuffer = new AbstractXdmNode [8];
  private final boolean m_bRecordLocations;

  /**
   * A builder that records node locations as {@link XdmParser#isRecordLocationsByDefault()} says:
   * not by default, only while {@link XdmParser#getWithRecordLocations(java.util.function.Supplier)}
   * runs on the current thread.
   */
  public XdmTreeBuilder ()
  {
    this (XdmParser.isRecordLocationsByDefault ());
  }

  /**
   * @param bRecordLocations
   *        <code>true</code> to give every element, comment and processing instruction the location
   *        the parser reports (8 bytes more per such node; attributes and text nodes report the
   *        location of their parent), <code>false</code> to build the nodes without location:
   *        {@link IXdmNode#getLine()} and {@link IXdmNode#getColumn()} are then 0.
   */
  public XdmTreeBuilder (final boolean bRecordLocations)
  {
    m_bRecordLocations = bRecordLocations;
  }

  /**
   * @return <code>true</code> if the nodes created now get a location: the builder records
   *         locations and the parser provides a locator.
   */
  private boolean _isLocated ()
  {
    return m_bRecordLocations && m_aLocator != null;
  }

  /**
   * Give a new node the next document order number, as long as the tree is numbered while it is
   * built. A structural change through the public API (by a wrapping handler) removes the numbering
   * of the whole tree, the document node included; from then on no node is numbered here, and
   * {@link #endDocument()} numbers the finished tree.
   */
  private void _stamp (@NonNull final AbstractXdmNode aNode)
  {
    if (m_aTree != null)
    {
      if (m_aDocument.internalGetTree () == m_aTree)
        aNode.internalStamp (m_aTree, m_nOrder++);
      else
        m_aTree = null;
    }
  }

  /**
   * Append a new node as the last child of the current node, keeping the numbering of the tree, and
   * number it.
   */
  private void _append (@NonNull final AbstractXdmNode aNode)
  {
    m_aCurrent.internalAppendNumbered (aNode);
    _stamp (aNode);
  }

  /**
   * Append the character data collected since the last node as one text node to the current node
   * and clear the buffer. Nothing happens if no character data is pending, so no empty text node is
   * ever created.
   */
  private void _flushText ()
  {
    final String sValue;
    if (m_sText != null)
    {
      sValue = m_sText;
      m_sText = null;
    }
    else
      if (!m_aText.isEmpty ())
      {
        sValue = m_aText.toString ();
        m_aText.setLength (0);
      }
      else
        return;
    _append (new XdmTextNode (sValue));
  }

  /**
   * @return The table of shared values, created on the first call.
   */
  @NonNull
  private String [] _getSharedValues ()
  {
    String [] ret = m_aSharedValues;
    if (ret == null)
    {
      ret = new String [SHARED_VALUE_SLOTS];
      m_aSharedValues = ret;
    }
    return ret;
  }

  /**
   * Short attribute values such as codes, amounts, flags and dates (<code>EUR</code>,
   * <code>0088</code>, <code>true</code>) repeat throughout a document. A value of at most
   * {@link #MAX_SHARED_VALUE} characters is shared within the document: the table has one slot per
   * hash code (its low bits), and a different value in the slot is replaced, so the table never
   * grows and keeps the values seen recently. The table belongs to this builder and is dropped with
   * it. Text is not shared: in the parse benchmark that cost about 10 % of the parse time, for 0.5
   * to 17 % less retained heap.
   *
   * @return The shared string equal to the value: the one in the table, or the value itself, which
   *         takes its slot. A value longer than {@link #MAX_SHARED_VALUE} is returned unchanged.
   */
  @NonNull
  private String _getShared (@NonNull final String sValue)
  {
    if (sValue.length () > MAX_SHARED_VALUE)
      return sValue;
    final int nHash = sValue.hashCode ();
    final String [] aTable = _getSharedValues ();
    final int nSlot = (nHash ^ (nHash >>> 16)) & (SHARED_VALUE_SLOTS - 1);
    final String sCandidate = aTable[nSlot];
    if (sValue.equals (sCandidate))
      return sCandidate;
    aTable[nSlot] = sValue;
    return sValue;
  }

  /**
   * The text of one character event as a string. The usual indentation between elements - a line
   * end followed by up to {@link #MAX_SHARED_INDENT} - 1 spaces or tabs only - is a large part of
   * the text nodes of most documents; it is shared within the document, one string per length and
   * indentation character, created on first use. Any other text gives a new string.
   */
  @NonNull
  private String _getText (@NonNull final char [] aChars, final int nStart, final int nLength)
  {
    if (nLength <= MAX_SHARED_INDENT && aChars[nStart] == '\n')
    {
      final int nEnd = nStart + nLength;
      final char cIndent = nLength > 1 ? aChars[nStart + 1] : ' ';
      if (cIndent == ' ' || cIndent == '\t')
      {
        int i = nStart + 1;
        while (i < nEnd && aChars[i] == cIndent)
          ++i;
        if (i == nEnd)
        {
          final String [] aShared = cIndent == ' ' ? m_aSpaceIndents : m_aTabIndents;
          String ret = aShared[nLength - 1];
          if (ret == null)
          {
            ret = new String (aChars, nStart, nLength);
            aShared[nLength - 1] = ret;
          }
          return ret;
        }
      }
    }
    return new String (aChars, nStart, nLength);
  }

  /**
   * A node read from an external parsed entity gets the entity's URI as base URI, but only the
   * outermost node of the entity: its descendants inherit it (and any <code>xml:base</code> in
   * between) through the parent chain.
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
   * @return The part before the first colon, or the empty string if there is no colon.
   */
  @NonNull
  private static String _getPrefix (@NonNull final String sQName)
  {
    final int nIndex = sQName.indexOf (':');
    return nIndex < 0 ? "" : sQName.substring (0, nIndex);
  }

  /**
   * Get the shared name instance for a namespace URI and lexical name, creating it on first use
   * with canonical strings ({@link XdmQName#getInterned()}), as the names of node tests have them.
   * The cache key includes the prefix, so that each node keeps the prefix used in the source.
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
    // prefix. The strings of the JDK parser are canonical and keep their hash
    // code, so a hit is mostly two identity checks
    final int nHash = 31 * sQName.hashCode () + sUri.hashCode ();
    int nMask = m_aNames.length - 1;
    int nSlot = (nHash ^ (nHash >>> 16)) & nMask;
    while (m_aNames[nSlot] != null)
    {
      if (m_aNameQNames[nSlot].equals (sQName) && m_aNameUris[nSlot].equals (sUri))
        return m_aNames[nSlot];
      nSlot = (nSlot + 1) & nMask;
    }
    final XdmQName ret = new XdmQName (sUri, sLocalName, _getPrefix (sQName)).getInterned ();
    m_aNameUris[nSlot] = sUri;
    m_aNameQNames[nSlot] = sQName;
    m_aNames[nSlot] = ret;
    if (++m_nNameCount * 2 > m_aNames.length)
    {
      // Double the table and insert all names again
      final String [] aOldUris = m_aNameUris;
      final String [] aOldQNames = m_aNameQNames;
      final XdmQName [] aOldNames = m_aNames;
      m_aNameUris = new String [aOldNames.length * 2];
      m_aNameQNames = new String [aOldNames.length * 2];
      m_aNames = new XdmQName [aOldNames.length * 2];
      nMask = m_aNames.length - 1;
      for (int i = 0; i < aOldNames.length; ++i)
        if (aOldNames[i] != null)
        {
          final int nOldHash = 31 * aOldQNames[i].hashCode () + aOldUris[i].hashCode ();
          int nNewSlot = (nOldHash ^ (nOldHash >>> 16)) & nMask;
          while (m_aNames[nNewSlot] != null)
            nNewSlot = (nNewSlot + 1) & nMask;
          m_aNameUris[nNewSlot] = aOldUris[i];
          m_aNameQNames[nNewSlot] = aOldQNames[i];
          m_aNames[nNewSlot] = aOldNames[i];
        }
    }
    return ret;
  }

  /**
   * Map the DTD attribute type reported by SAX to the ID kind.
   *
   * @param sSaxType
   *        The attribute type from {@link Attributes#getType(int)}; may be <code>null</code>.
   * @return {@link EIDKind#ID}, {@link EIDKind#IDREF} or {@link EIDKind#IDREFS} for these types,
   *         {@link EIDKind#NONE} for every other type (<code>CDATA</code> for undeclared
   *         attributes).
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
   * @return The value without leading and trailing spaces and with runs of spaces collapsed.
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
   * @return The node that content is currently added to: the innermost open element, or the
   *         document node. For handlers that wrap the builder.
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
   * Create the document node, which becomes the current node, with the system ID of the locator (if
   * any) as document URI. It is the first node of a new numbering; its end order stays open until
   * {@link #endDocument()}.
   */
  @Override
  public void startDocument ()
  {
    m_aDocument = new XdmDocumentNode ();
    m_aCurrent = m_aDocument;
    if (m_aLocator != null)
      m_aDocument.setDocumentUri (m_aLocator.getSystemId ());
    m_aTree = AbstractXdmNode.internalNewTree ();
    m_nOrder = 0;
    m_nDepth = 0;
    m_nMaxDepth = 0;
    m_aDocument.internalStamp (m_aTree, m_nOrder++);
    m_aDocument.internalSetEndOrder (Integer.MAX_VALUE);
  }

  /**
   * Append pending text, trim the child array of the document and finish the numbering: the
   * document ends with the last node numbered, and the maximum element depth is recorded with this
   * numbering ({@link XdmDocumentNode#getMaxElementDepth()}). If a change from outside removed the
   * numbering while building, the finished tree is numbered in a second pass
   * ({@link IXdmNode#assignDocumentOrder()}) and the depth stays unknown. Name indexes asked for
   * while building would miss the later nodes and are dropped.
   */
  @Override
  public void endDocument ()
  {
    _flushText ();
    m_aDocument.internalTrimChildren ();
    if (m_aTree != null && m_aDocument.internalGetTree () == m_aTree)
    {
      m_aDocument.internalSetEndOrder (m_nOrder - 1);
      m_aDocument.internalDropNameIndex ();
      m_aDocument.internalSetMaxElementDepth (m_nMaxDepth);
    }
    else
      m_aDocument.assignDocumentOrder ();
  }

  /**
   * Remember the mapping until the next {@link #startElement}, which declares it on the new
   * element. An empty URI (undeclaring the default namespace) is kept as it is.
   */
  @Override
  public void startPrefixMapping (final String sPrefix, final String sUri)
  {
    m_aPendingPrefixes.add (sPrefix);
    m_aPendingUris.add (sUri);
  }

  /**
   * Append pending text, then create the element with the location reported at the end of its start
   * tag, the namespace declarations collected by {@link #startPrefixMapping(String, String)} and
   * the base URI of an entity boundary. Attributes follow in the reported order, without the
   * <code>xmlns</code> declarations: a DTD type ID, IDREF or IDREFS, and an <code>xml:id</code>
   * attribute (always an ID), set the ID kind and normalise the value with
   * {@link #normaliseTokenised(String)}. The element is appended to the current node, numbered
   * followed by its attributes, and becomes the current node.
   */
  @Override
  public void startElement (final String sUri, final String sLocalName, final String sQName, final Attributes aAttrs)
  {
    _flushText ();
    final XdmQName aName = _getName (sUri, sLocalName, sQName);
    final boolean bLocated = _isLocated ();
    final int nLine = bLocated ? m_aLocator.getLineNumber () : 0;
    final int nColumn = bLocated ? m_aLocator.getColumnNumber () : 0;
    final XdmElementNode aElement = bLocated ? new XdmElementNode.Located (aName, nLine, nColumn)
                                             : new XdmElementNode (aName);
    for (int i = 0; i < m_aPendingPrefixes.size (); ++i)
      aElement.declareNamespace (m_aPendingPrefixes.get (i), m_aPendingUris.get (i));
    m_aPendingPrefixes.clear ();
    m_aPendingUris.clear ();
    final String sSystemId = _applyEntityBase (aElement);
    final int nAttrs = aAttrs.getLength ();
    int nAttrCount = 0;
    if (nAttrs > m_aAttrBuffer.length)
      m_aAttrBuffer = new AbstractXdmNode [nAttrs];
    for (int i = 0; i < nAttrs; ++i)
    {
      final String sAttrQName = aAttrs.getQName (i);
      // Namespace declarations are not attributes in XDM
      if (sAttrQName.equals ("xmlns") || sAttrQName.startsWith ("xmlns:"))
        continue;
      final XdmQName aAttrName = _getName (aAttrs.getURI (i), aAttrs.getLocalName (i), sAttrQName);
      final String sValue = _getShared (aAttrs.getValue (i));
      EIDKind eIDKind = _getIDKind (aAttrs.getType (i));
      if (eIDKind == EIDKind.NONE && aAttrName.isSameName (XdmConstants.NS_XML, "id"))
        eIDKind = EIDKind.ID;
      final String sAttrValue = eIDKind == EIDKind.NONE ? sValue : normaliseTokenised (sValue);
      // An attribute reports the location of its element
      final XdmAttributeNode aAttr = new XdmAttributeNode (aAttrName, sAttrValue);
      aAttr.setIDKind (eIDKind);
      m_aAttrBuffer[nAttrCount++] = aAttr;
    }
    if (nAttrCount > 0)
    {
      // An array of the exact size; the element is not numbered yet
      aElement.internalSetAttributes (Arrays.copyOf (m_aAttrBuffer, nAttrCount));
      Arrays.fill (m_aAttrBuffer, 0, nAttrCount, null);
    }
    _append (aElement);
    for (int i = 0; i < aElement.getAttributeCount (); ++i)
      _stamp (aElement.getAttributeAtIndex (i));
    aElement.internalSetEndOrder (Integer.MAX_VALUE);
    m_aCurrent = aElement;
    m_aSystemIds.push (sSystemId == null ? "" : sSystemId);
    if (++m_nDepth > m_nMaxDepth)
      m_nMaxDepth = m_nDepth;
  }

  /**
   * Append pending text, trim the child array of the closed element, end it with the last node
   * numbered and make its parent the current node.
   */
  @Override
  public void endElement (final String sUri, final String sLocalName, final String sQName)
  {
    _flushText ();
    m_aCurrent.internalTrimChildren ();
    m_aCurrent.internalSetEndOrder (m_nOrder - 1);
    m_aCurrent = (AbstractXdmParentNode) m_aCurrent.getParent ();
    m_aSystemIds.pop ();
    m_nDepth--;
  }

  /**
   * Collect character data for the next text node. The text of the first event is kept as a string
   * (shared for indentation, see {@link #_getText(char[], int, int)}); only a second event merges
   * the text in a buffer. An event without characters is ignored.
   */
  @Override
  public void characters (final char [] aChars, final int nStart, final int nLength)
  {
    if (nLength == 0)
      return;
    if (m_sText == null && m_aText.isEmpty ())
    {
      // The first event of a text node: most text nodes have only one
      m_sText = _getText (aChars, nStart, nLength);
    }
    else
    {
      if (m_sText != null)
      {
        m_aText.append (m_sText);
        m_sText = null;
      }
      m_aText.append (aChars, nStart, nLength);
    }
  }

  /**
   * Dropped: this is whitespace in element-only content, and XDM 3.1 section 6.7.3 discards such
   * text.
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
   * Record an unparsed entity on the document; a declaration without name or system ID, or before
   * the document node exists, is ignored. The notation name is not kept.
   */
  @Override
  public void unparsedEntityDecl (final String sName,
                                  final String sPublicId,
                                  final String sSystemId,
                                  final String sNotationName)
  {
    if (m_aDocument != null && sName != null && sSystemId != null)
      m_aDocument.addUnparsedEntity (sName, sSystemId, sPublicId);
  }

  /**
   * Record the name of an internal general entity on the document; parameter entities (name
   * starting with <code>%</code>) are ignored.
   */
  @Override
  public void internalEntityDecl (final String sName, final String sValue)
  {
    if (m_aDocument != null && sName != null && !sName.startsWith ("%"))
      m_aDocument.addParsedEntityName (sName);
  }

  /**
   * Record the name of an external parsed general entity on the document; parameter entities (name
   * starting with <code>%</code>) are ignored.
   */
  @Override
  public void externalEntityDecl (final String sName, final String sPublicId, final String sSystemId)
  {
    if (m_aDocument != null && sName != null && !sName.startsWith ("%"))
      m_aDocument.addParsedEntityName (sName);
  }

  /**
   * Append pending text, then a processing instruction node (missing data becomes the empty string)
   * with the current location and the base URI of an entity boundary.
   */
  @Override
  public void processingInstruction (final String sTarget, final String sData)
  {
    _flushText ();
    final XdmQName aTarget = new XdmQName (sTarget);
    final String sValue = sData == null ? "" : sData;
    final XdmProcessingInstructionNode aPI = _isLocated () ? new XdmProcessingInstructionNode.Located (aTarget,
                                                                                                       sValue,
                                                                                                       m_aLocator.getLineNumber (),
                                                                                                       m_aLocator.getColumnNumber ())
                                                           : new XdmProcessingInstructionNode (aTarget, sValue);
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
   * Append pending text, then a comment node with the current location. Comments inside the DTD are
   * dropped.
   */
  @Override
  public void comment (final char [] aChars, final int nStart, final int nLength)
  {
    if (m_bInDTD)
      return;
    _flushText ();
    final String sValue = new String (aChars, nStart, nLength);
    final XdmCommentNode aComment = _isLocated () ? new XdmCommentNode.Located (sValue,
                                                                                m_aLocator.getLineNumber (),
                                                                                m_aLocator.getColumnNumber ())
                                                  : new XdmCommentNode (sValue);
    _append (aComment);
  }
}
