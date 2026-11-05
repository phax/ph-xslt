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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.EntityDeclaration;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;

/**
 * Reads a document incrementally with the JDK StAX parser and builds ordinary
 * {@link IXdmNode}s one event at a time, the input side of streamed execution
 * (XSLT 3.0 section 19). A port of go-xslt's
 * <code>internal/xmltree/stream.go</code>.
 * <p>
 * The nodes are of the same kinds and shapes {@link XdmParser} builds, so the
 * XPath evaluator and the XSLT engine work on them unchanged. They are linked
 * into the {@link #getDocument() document} as they are read, and numbered as
 * they are created in the order a finished tree would have: an open element
 * covers every node that is read until its end tag. The caller decides what to
 * keep: {@link #drop(IXdmNode)} detaches a completed node and
 * {@link #skipSubtree()} passes over an element without building it. A caller
 * that drops nothing ends up with the whole document. Peak memory is then the
 * ancestor spine plus what the caller keeps.
 * <p>
 * The parser settings follow {@link XdmParser}: the internal DTD subset is read
 * (default attributes, ID types, entities), the external subset and external
 * entities are not, and the same entity and depth limits apply. Adjacent
 * character data forms one text node (the reader merges it itself; the
 * coalescing mode of the JDK parser reports some whitespace in element content
 * inconsistently), whitespace in element-only content (a DTD declaration) is
 * dropped, and whitespace text can be stripped by element name while it is
 * read, honouring <code>xml:space</code>.
 * <p>
 * A document that needs more than an incremental read is refused with
 * {@link XdmStreamUnsupportedException} before the first event is returned:
 * XML 1.1, an external parsed entity, and an external DTD subset when external
 * entities are to be loaded. Not reproduced: the location of a text node is
 * where the parser reports its first chunk, and the declaration order of
 * entities is the one the parser lists.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmStreamReader implements AutoCloseable
{
  /** What {@link XdmStreamReader#next()} read. */
  public enum EEvent
  {
    /**
     * The start tag of an element: the node has its name, namespaces and
     * attributes and is linked to its parent, but has no children yet.
     */
    ENTER,
    /** The end tag of an element: its subtree is complete. */
    EXIT,
    /** A complete text, comment or processing-instruction node. */
    LEAF,
    /** The end of the document. */
    END
  }

  private record Event (@NonNull EEvent kind, @NonNull AbstractXdmNode node, int depth)
  {}

  /** The maximum number of nodes, so that the document order stays an int. */
  private static final int MAX_NODES = Integer.MAX_VALUE - 1;

  private final XMLStreamReader m_aReader;
  private final InputStream m_aOwnedStream;
  private final boolean m_bLoadExternalEntities;
  private final int m_nMaxElementDepth;
  private final Predicate <XdmQName> m_aStripWhitespace;
  private final XdmDocumentNode m_aDocument;
  private final long m_nTree;
  private int m_nOrder;
  // The open elements below the document node, innermost last
  private final ArrayDeque <AbstractXdmParentNode> m_aSpine = new ArrayDeque <> ();
  // xml:space="preserve" in effect, per open element
  private final ArrayDeque <Boolean> m_aPreserve = new ArrayDeque <> ();
  private final StringBuilder m_aText = new StringBuilder ();
  private int m_nTextLine;
  private int m_nTextColumn;
  private final ArrayDeque <Event> m_aQueue = new ArrayDeque <> ();
  private Event m_aCurrent;
  private boolean m_bEnded;
  // The nodes drop took children from; bounded by the live spine
  private final Set <IXdmNode> m_aIncomplete = Collections.newSetFromMap (new IdentityHashMap <> ());
  private final ICommonsMap <String, ICommonsMap <String, XdmQName>> m_aNames = new CommonsHashMap <> ();

  private XdmStreamReader (@NonNull final XMLStreamReader aReader,
                           @Nullable final InputStream aOwnedStream,
                           @Nullable final String sSystemId,
                           final boolean bLoadExternalEntities,
                           final int nMaxElementDepth,
                           @Nullable final Predicate <XdmQName> aStripWhitespace)
  {
    m_aReader = aReader;
    m_aOwnedStream = aOwnedStream;
    m_bLoadExternalEntities = bLoadExternalEntities;
    m_nMaxElementDepth = nMaxElementDepth;
    m_aStripWhitespace = aStripWhitespace;
    m_aDocument = new XdmDocumentNode ();
    m_aDocument.internalSetStreamed (true);
    if (sSystemId != null)
      m_aDocument.setDocumentUri (sSystemId);
    m_nTree = AbstractXdmNode.internalNewTree ();
    m_aDocument.internalStamp (m_nTree, m_nOrder++);
    m_aDocument.internalSetEndOrder (Integer.MAX_VALUE);
  }

  private void _stamp (@NonNull final AbstractXdmNode aNode) throws XdmParseException
  {
    if (m_nOrder >= MAX_NODES)
      throw new XdmParseException ("The streamed document has more than " + MAX_NODES + " nodes");
    aNode.internalStamp (m_nTree, m_nOrder++);
  }

  @NonNull
  private AbstractXdmParentNode _top ()
  {
    final AbstractXdmParentNode ret = m_aSpine.peekLast ();
    return ret != null ? ret : m_aDocument;
  }

  @NonNull
  private static String _getPrefix (@NonNull final String sQName)
  {
    final int nIndex = sQName.indexOf (':');
    return nIndex < 0 ? "" : sQName.substring (0, nIndex);
  }

  @NonNull
  private XdmQName _getName (@Nullable final String sUri, @NonNull final String sLocalName, @Nullable final String sPrefix)
  {
    final String sRealUri = sUri == null ? "" : sUri;
    final String sRealPrefix = sPrefix == null ? "" : sPrefix;
    final String sQName = sRealPrefix.isEmpty () ? sLocalName : sRealPrefix + ':' + sLocalName;
    return m_aNames.computeIfAbsent (sRealUri, k -> new CommonsHashMap <> ())
                   .computeIfAbsent (sQName, k -> new XdmQName (sRealUri, sLocalName, _getPrefix (sQName)));
  }

  @NonNull
  private static EIDKind _getIDKind (@Nullable final String sType)
  {
    if (sType != null)
      switch (sType)
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

  @NonNull
  private static XdmParseException _wrap (@NonNull final XMLStreamException ex)
  {
    return new XdmParseException ("Failed to parse XML: " + ex.getMessage (), ex);
  }

  /**
   * @return <code>true</code> if the DOCTYPE declaration names an external
   *         subset (SYSTEM or PUBLIC before the internal subset).
   */
  private static boolean _hasExternalSubset (@Nullable final String sDoctype)
  {
    if (sDoctype == null)
      return false;
    int nEnd = sDoctype.indexOf ('[');
    if (nEnd < 0)
      nEnd = sDoctype.length ();
    final String sHead = sDoctype.substring (0, nEnd);
    return sHead.contains ("SYSTEM") || sHead.contains ("PUBLIC");
  }

  private void _readDTD () throws XdmParseException
  {
    if (m_bLoadExternalEntities && _hasExternalSubset (m_aReader.getText ()))
      throw new XdmStreamUnsupportedException ("The DOCTYPE names an external DTD subset");
    if (m_aReader.getProperty ("javax.xml.stream.entities") instanceof final List <?> aEntities)
      for (final Object aObj : aEntities)
        if (aObj instanceof final EntityDeclaration aDecl && aDecl.getName () != null && !aDecl.getName ().startsWith ("%"))
        {
          if (aDecl.getNotationName () != null)
          {
            String sSystemId = aDecl.getSystemId ();
            if (sSystemId == null)
              continue;
            try
            {
              if (aDecl.getBaseURI () != null && !URI.create (sSystemId).isAbsolute ())
                sSystemId = URI.create (aDecl.getBaseURI ()).resolve (sSystemId).toString ();
            }
            catch (final IllegalArgumentException ex)
            {
              // Kept as written
            }
            m_aDocument.addUnparsedEntity (aDecl.getName (), sSystemId, aDecl.getPublicId ());
          }
          else
          {
            if (aDecl.getSystemId () != null)
              throw new XdmStreamUnsupportedException ("The DTD declares the external parsed entity '" +
                                                       aDecl.getName () +
                                                       "'");
            m_aDocument.addParsedEntityName (aDecl.getName ());
          }
        }
  }

  private static boolean _isWhitespace (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
        return false;
    }
    return true;
  }

  private void _flushText () throws XdmParseException
  {
    if (m_aText.isEmpty ())
      return;
    final String sText = m_aText.toString ();
    m_aText.setLength (0);
    final AbstractXdmParentNode aParent = _top ();
    // Character data outside the document element is whitespace (anything
    // else is not well-formed) and not part of the tree
    if (aParent == m_aDocument)
      return;
    if (m_aStripWhitespace != null &&
        !m_aPreserve.peekLast ().booleanValue () &&
        _isWhitespace (sText) &&
        m_aStripWhitespace.test (aParent.getName ()))
      return;
    final XdmTextNode aText = new XdmTextNode (sText);
    aText.setLocation (m_nTextLine, m_nTextColumn);
    aParent.internalAppendNumbered (aText);
    _stamp (aText);
    m_aQueue.add (new Event (EEvent.LEAF, aText, m_aSpine.size () + 1));
  }

  private void _startElement () throws XdmParseException
  {
    _flushText ();
    if (m_nMaxElementDepth > 0 && m_aSpine.size () >= m_nMaxElementDepth)
      throw new XdmParseException ("Failed to parse XML: the element nesting is deeper than " + m_nMaxElementDepth);
    final XdmElementNode aElement = new XdmElementNode (_getName (m_aReader.getNamespaceURI (),
                                                                  m_aReader.getLocalName (),
                                                                  m_aReader.getPrefix ()));
    aElement.setLocation (m_aReader.getLocation ().getLineNumber (), m_aReader.getLocation ().getColumnNumber ());
    for (int i = 0; i < m_aReader.getNamespaceCount (); ++i)
    {
      final String sPrefix = m_aReader.getNamespacePrefix (i);
      final String sUri = m_aReader.getNamespaceURI (i);
      aElement.declareNamespace (sPrefix == null ? "" : sPrefix, sUri == null ? "" : sUri);
    }
    for (int i = 0; i < m_aReader.getAttributeCount (); ++i)
    {
      final XdmQName aName = _getName (m_aReader.getAttributeNamespace (i),
                                       m_aReader.getAttributeLocalName (i),
                                       m_aReader.getAttributePrefix (i));
      final String sValue = m_aReader.getAttributeValue (i);
      EIDKind eIDKind = _getIDKind (m_aReader.getAttributeType (i));
      if (eIDKind == EIDKind.NONE && aName.isSameName (XdmConstants.NS_XML, "id"))
        eIDKind = EIDKind.ID;
      final XdmAttributeNode aAttr = new XdmAttributeNode (aName,
                                                           eIDKind == EIDKind.NONE ? sValue
                                                                                   : XdmTreeBuilder.normaliseTokenised (sValue));
      aAttr.setIDKind (eIDKind);
      aAttr.setLocation (aElement.getLine (), aElement.getColumn ());
      aElement.addAttribute (aAttr);
    }
    _top ().internalAppendNumbered (aElement);
    _stamp (aElement);
    for (int i = 0; i < aElement.getAttributeCount (); ++i)
      _stamp (aElement.getAttributeAtIndex (i));
    aElement.internalSetEndOrder (Integer.MAX_VALUE);
    final boolean bInherited = m_aPreserve.isEmpty () ? false : m_aPreserve.peekLast ().booleanValue ();
    boolean bPreserve = bInherited;
    final IXdmNode aSpace = aElement.getAttribute (XdmConstants.NS_XML, "space");
    if (aSpace != null)
    {
      final String s = aSpace.getValue ().trim ();
      if ("preserve".equals (s))
        bPreserve = true;
      else
        if ("default".equals (s))
          bPreserve = false;
    }
    m_aSpine.addLast (aElement);
    m_aPreserve.addLast (Boolean.valueOf (bPreserve));
    m_aQueue.add (new Event (EEvent.ENTER, aElement, m_aSpine.size ()));
  }

  @NonNull
  private AbstractXdmParentNode _popElement ()
  {
    final int nDepth = m_aSpine.size ();
    final AbstractXdmParentNode aElement = m_aSpine.removeLast ();
    m_aPreserve.removeLast ();
    aElement.internalSetEndOrder (m_nOrder - 1);
    m_aQueue.add (new Event (EEvent.EXIT, aElement, nDepth));
    return aElement;
  }

  private void _leaf (@NonNull final AbstractXdmNode aNode) throws XdmParseException
  {
    _flushText ();
    aNode.setLocation (m_aReader.getLocation ().getLineNumber (), m_aReader.getLocation ().getColumnNumber ());
    _top ().internalAppendNumbered (aNode);
    _stamp (aNode);
    m_aQueue.add (new Event (EEvent.LEAF, aNode, m_aSpine.size () + 1));
  }

  private void _end () throws XdmParseException
  {
    _flushText ();
    if (!m_aSpine.isEmpty ())
      throw new XdmParseException ("Failed to parse XML: unexpected end of document inside an element");
    m_aDocument.internalSetEndOrder (m_nOrder - 1);
    m_bEnded = true;
    m_aQueue.add (new Event (EEvent.END, m_aDocument, 0));
  }

  /** Read until at least one more event is queued. */
  private void _pump () throws XdmParseException
  {
    final int nQueued = m_aQueue.size ();
    try
    {
      while (m_aQueue.size () == nQueued)
      {
        if (!m_aReader.hasNext ())
        {
          _end ();
          return;
        }
        final int nEvent = m_aReader.next ();
        switch (nEvent)
        {
          case XMLStreamConstants.DTD:
            _readDTD ();
            break;
          case XMLStreamConstants.START_ELEMENT:
            _startElement ();
            break;
          case XMLStreamConstants.END_ELEMENT:
            _flushText ();
            _popElement ();
            break;
          case XMLStreamConstants.CHARACTERS:
          case XMLStreamConstants.CDATA:
            if (m_aText.isEmpty ())
            {
              m_nTextLine = m_aReader.getLocation ().getLineNumber ();
              m_nTextColumn = m_aReader.getLocation ().getColumnNumber ();
            }
            m_aText.append (m_aReader.getTextCharacters (), m_aReader.getTextStart (), m_aReader.getTextLength ());
            break;
          case XMLStreamConstants.SPACE:
            // Whitespace in element-only content (XDM 3.1 section 6.7.3), and
            // outside the document element
            break;
          case XMLStreamConstants.COMMENT:
            _leaf (new XdmCommentNode (m_aReader.getText ()));
            break;
          case XMLStreamConstants.PROCESSING_INSTRUCTION:
          {
            final String sData = m_aReader.getPIData ();
            _leaf (new XdmProcessingInstructionNode (new XdmQName (m_aReader.getPITarget ()), sData == null ? "" : sData));
            break;
          }
          case XMLStreamConstants.ENTITY_REFERENCE:
            throw new XdmParseException ("Failed to parse XML: the entity '" +
                                         m_aReader.getLocalName () +
                                         "' cannot be expanded");
          case XMLStreamConstants.END_DOCUMENT:
            _end ();
            return;
          default:
            break;
        }
      }
    }
    catch (final XMLStreamException ex)
    {
      throw _wrap (ex);
    }
  }

  /**
   * @return The document node. It is live: its subtree grows as
   *         {@link #next()} runs and shrinks as the caller drops nodes.
   */
  @NonNull
  public IXdmNode getDocument ()
  {
    return m_aDocument;
  }

  /**
   * @return The number of open elements (0 = at document level).
   */
  @Nonnegative
  public int getOpenDepth ()
  {
    return m_aSpine.size ();
  }

  /**
   * Read the next event.
   *
   * @return The event. After {@link EEvent#END} every call returns
   *         {@link EEvent#END} again.
   * @throws XdmParseException
   *         if the document is not well-formed
   */
  @NonNull
  public EEvent next () throws XdmParseException
  {
    if (m_aQueue.isEmpty ())
    {
      if (m_bEnded)
        return EEvent.END;
      _pump ();
    }
    m_aCurrent = m_aQueue.removeFirst ();
    return m_aCurrent.kind ();
  }

  /**
   * @return The node of the last event: the element for {@link EEvent#ENTER}
   *         and {@link EEvent#EXIT}, the leaf, or the document node for
   *         {@link EEvent#END}.
   */
  @NonNull
  public IXdmNode getNode ()
  {
    if (m_aCurrent == null)
      throw new IllegalStateException ("next() was not called");
    return m_aCurrent.node ();
  }

  /**
   * @return The depth of the node of the last event: the document element is
   *         1, a leaf child of it 2, the document node 0.
   */
  @Nonnegative
  public int getDepth ()
  {
    if (m_aCurrent == null)
      throw new IllegalStateException ("next() was not called");
    return m_aCurrent.depth ();
  }

  /**
   * Detach a completed node from its parent, so that it becomes garbage. The
   * parent is then {@link #isIncomplete(IXdmNode) incomplete}.
   *
   * @param aNode
   *        A node of this document that is not open. May not be
   *        <code>null</code>.
   */
  public void drop (@NonNull final IXdmNode aNode)
  {
    ValueEnforcer.notNull (aNode, "Node");
    final AbstractXdmParentNode aParent = (AbstractXdmParentNode) aNode.getParent ();
    if (aParent == null)
      return;
    if (m_aSpine.contains (aNode))
      throw new IllegalArgumentException ("An open element cannot be dropped");
    if (aParent.internalDetachNumbered ((AbstractXdmNode) aNode))
    {
      m_aIncomplete.add (aParent);
      m_aIncomplete.remove (aNode);
    }
  }

  /**
   * Pass over the element the last {@link EEvent#ENTER} returned, with
   * everything inside it, without building any of it, and drop it. No
   * {@link EEvent#EXIT} is returned for it.
   *
   * @throws XdmParseException
   *         if the document is not well-formed
   */
  public void skipSubtree () throws XdmParseException
  {
    if (m_aCurrent == null ||
        m_aCurrent.kind () != EEvent.ENTER ||
        m_aSpine.peekLast () != m_aCurrent.node () ||
        !m_aQueue.isEmpty ())
      throw new IllegalStateException ("skipSubtree() must directly follow the ENTER of an element");
    try
    {
      int nNested = 0;
      while (true)
      {
        if (!m_aReader.hasNext ())
          throw new XdmParseException ("Failed to parse XML: unexpected end of document inside an element");
        final int nEvent = m_aReader.next ();
        if (nEvent == XMLStreamConstants.START_ELEMENT)
        {
          if (m_nMaxElementDepth > 0 && m_aSpine.size () + nNested >= m_nMaxElementDepth)
            throw new XdmParseException ("Failed to parse XML: the element nesting is deeper than " + m_nMaxElementDepth);
          nNested++;
        }
        else
          if (nEvent == XMLStreamConstants.END_ELEMENT)
          {
            if (nNested == 0)
              break;
            nNested--;
          }
          else
            if (nEvent == XMLStreamConstants.ENTITY_REFERENCE)
              throw new XdmParseException ("Failed to parse XML: the entity '" +
                                           m_aReader.getLocalName () +
                                           "' cannot be expanded");
      }
    }
    catch (final XMLStreamException ex)
    {
      throw _wrap (ex);
    }
    final AbstractXdmParentNode aElement = m_aSpine.removeLast ();
    m_aPreserve.removeLast ();
    aElement.internalSetEndOrder (m_nOrder - 1);
    m_aCurrent = null;
    drop (aElement);
  }

  /**
   * @param aNode
   *        A node of this document.
   * @return <code>true</code> if {@link #drop(IXdmNode)} took children away
   *         from it: its string value, its child and descendant axes and every
   *         walk through it describe less than the document contains.
   */
  public boolean isIncomplete (@Nullable final IXdmNode aNode)
  {
    return aNode != null && m_aIncomplete.contains (aNode);
  }

  /**
   * Read the rest of the document without dropping anything, for a caller
   * that turns out to need it in full. The result is the tree
   * {@link XdmParser} builds, and no longer {@link XdmDocumentNode#isStreamed()
   * streamed}.
   *
   * @return The complete document node.
   * @throws XdmParseException
   *         if the document is not well-formed
   * @throws IllegalStateException
   *         if a node was dropped or skipped already
   */
  @NonNull
  public IXdmNode readAll () throws XdmParseException
  {
    if (!m_aIncomplete.isEmpty ())
      throw new IllegalStateException ("Nodes were dropped already");
    while (next () != EEvent.END)
    {
      // Keep everything
    }
    m_aDocument.internalSetStreamed (false);
    return m_aDocument;
  }

  public void close () throws XdmParseException
  {
    try
    {
      m_aReader.close ();
      if (m_aOwnedStream != null)
        m_aOwnedStream.close ();
    }
    catch (final XMLStreamException ex)
    {
      throw _wrap (ex);
    }
    catch (final IOException ex)
    {
      throw new XdmParseException ("Failed to close the streamed document: " + ex.getMessage (), ex);
    }
  }

  private static void _closeQuietly (@Nullable final InputStream aIS)
  {
    if (aIS != null)
      try
      {
        aIS.close ();
      }
      catch (final IOException ex)
      {
        // ignore
      }
  }

  @NonNull
  private static XMLInputFactory _createFactory (final int nMaxElementDepth)
  {
    // Always the JDK parser, as in XdmParser
    final XMLInputFactory aFactory = XMLInputFactory.newDefaultFactory ();
    aFactory.setProperty (XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    aFactory.setProperty (XMLInputFactory.IS_COALESCING, Boolean.FALSE);
    aFactory.setProperty (XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
    aFactory.setProperty (XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
    aFactory.setProperty (XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    aFactory.setProperty (XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
    aFactory.setProperty ("http://java.sun.com/xml/stream/properties/ignore-external-dtd", Boolean.TRUE);
    aFactory.setXMLResolver ( (sPublicId, sSystemId, sBaseUri, sNamespace) -> {
      throw new XMLStreamException ("External resources are not read by the stream reader: " + sSystemId);
    });
    for (final String [] aLimit : XdmParser.ENTITY_LIMITS)
      aFactory.setProperty ("http://www.oracle.com/xml/jaxp/properties/" + aLimit[0], aLimit[1]);
    aFactory.setProperty ("http://www.oracle.com/xml/jaxp/properties/maxElementDepth",
                          Integer.toString (nMaxElementDepth));
    return aFactory;
  }

  /**
   * Start reading a document. The prolog up to the start tag of the document
   * element is read here, so that a document this reader does not stream is
   * refused before the first event.
   *
   * @param aSource
   *        The source: a byte stream, a character stream or a system ID (URL)
   *        that the reader opens. The system ID becomes the document URI. May
   *        not be <code>null</code>.
   * @param bLoadExternalEntities
   *        <code>true</code> if the caller would read the document with
   *        external entities ({@link XdmParser#setLoadExternalEntities(boolean)}):
   *        a document with an external DTD subset is then refused, since this
   *        reader never reads it.
   * @param nMaxElementDepth
   *        The maximum element nesting depth, 0 for unlimited.
   * @param aStripWhitespace
   *        The names of the elements whose whitespace-only text children are
   *        removed while reading, unless <code>xml:space="preserve"</code> is
   *        in effect. May be <code>null</code> to strip nothing.
   * @return The reader. Close it after use.
   * @throws XdmStreamUnsupportedException
   *         if the document must be read in full by {@link XdmParser}
   * @throws XdmParseException
   *         if the document cannot be read
   */
  @NonNull
  public static XdmStreamReader open (@NonNull final InputSource aSource,
                                      final boolean bLoadExternalEntities,
                                      @Nonnegative final int nMaxElementDepth,
                                      @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
  {
    ValueEnforcer.notNull (aSource, "Source");
    ValueEnforcer.isGE0 (nMaxElementDepth, "MaxElementDepth");
    final String sSystemId = aSource.getSystemId ();
    final XMLInputFactory aFactory = _createFactory (nMaxElementDepth);
    InputStream aOwned = null;
    final XMLStreamReader aReader;
    try
    {
      if (aSource.getCharacterStream () != null)
        aReader = aFactory.createXMLStreamReader (sSystemId, aSource.getCharacterStream ());
      else
      {
        InputStream aIS = aSource.getByteStream ();
        if (aIS == null)
        {
          if (sSystemId == null)
            throw new IllegalArgumentException ("The source has neither a stream nor a system ID");
          aOwned = URI.create (sSystemId).toURL ().openStream ();
          aIS = aOwned;
        }
        aReader = aSource.getEncoding () != null ? aFactory.createXMLStreamReader (aIS, aSource.getEncoding ())
                                                 : aFactory.createXMLStreamReader (sSystemId, aIS);
      }
    }
    catch (final XMLStreamException ex)
    {
      _closeQuietly (aOwned);
      throw _wrap (ex);
    }
    catch (final IOException | IllegalArgumentException ex)
    {
      _closeQuietly (aOwned);
      throw new XdmParseException ("Failed to open the streamed document: " + ex.getMessage (), ex);
    }
    final XdmStreamReader ret = new XdmStreamReader (aReader,
                                                     aOwned,
                                                     sSystemId,
                                                     bLoadExternalEntities,
                                                     nMaxElementDepth,
                                                     aStripWhitespace);
    try
    {
      final String sVersion = aReader.getVersion ();
      if (sVersion != null && "1.1".equals (sVersion.trim ().toLowerCase (Locale.ROOT)))
        throw new XdmStreamUnsupportedException ("XML 1.1 documents are read in full");
      // The prolog: the DTD is the last place a document can be refused
      while (!ret.m_bEnded && (ret.m_aQueue.isEmpty () || ret.m_aQueue.peekLast ().kind () == EEvent.LEAF))
        ret._pump ();
    }
    catch (final XdmParseException ex)
    {
      try
      {
        ret.close ();
      }
      catch (final XdmParseException ex2)
      {
        // The original error stands
      }
      throw ex;
    }
    return ret;
  }

  /**
   * Start reading a document with the defaults of {@link XdmParser}.
   *
   * @param aSource
   *        The source. May not be <code>null</code>.
   * @return The reader. Close it after use.
   * @throws XdmParseException
   *         if the document cannot be read or is not streamed
   */
  @NonNull
  public static XdmStreamReader open (@NonNull final InputSource aSource) throws XdmParseException
  {
    return open (aSource, XdmParser.DEFAULT_LOAD_EXTERNAL_ENTITIES, XdmParser.DEFAULT_MAX_ELEMENT_DEPTH, null);
  }
}
