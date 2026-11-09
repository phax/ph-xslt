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
package com.helger.phoxtrot.xpath.serialize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.string.StringHex;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;

/**
 * Writes a normalised sequence (nodes and strings) with the xml, xhtml or html output method
 * (Serialization 3.1 sections 5 to 7). The tree is walked iteratively, so deep trees cannot
 * overflow the stack.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class MarkupWriter
{
  static final String NS_XHTML = "http://www.w3.org/1999/xhtml";
  private static final Set <String> HTML_FAMILY_NS = Set.of (NS_XHTML,
                                                             "http://www.w3.org/2000/svg",
                                                             "http://www.w3.org/1998/Math/MathML");
  private static final Set <String> VOID_ELEMENTS = Set.of ("area",
                                                            "base",
                                                            "br",
                                                            "col",
                                                            "embed",
                                                            "hr",
                                                            "img",
                                                            "input",
                                                            "link",
                                                            "meta",
                                                            "param",
                                                            "source",
                                                            "track",
                                                            "wbr",
                                                            "basefont",
                                                            "frame",
                                                            "isindex");
  private static final Set <String> BOOLEAN_ATTRS = Set.of ("checked",
                                                            "compact",
                                                            "declare",
                                                            "defer",
                                                            "disabled",
                                                            "ismap",
                                                            "multiple",
                                                            "noresize",
                                                            "noshade",
                                                            "nowrap",
                                                            "readonly",
                                                            "selected");
  private static final Set <String> INLINE_ELEMENTS = Set.of ("a",
                                                              "abbr",
                                                              "acronym",
                                                              "applet",
                                                              "audio",
                                                              "b",
                                                              "basefont",
                                                              "bdi",
                                                              "bdo",
                                                              "big",
                                                              "br",
                                                              "button",
                                                              "canvas",
                                                              "cite",
                                                              "code",
                                                              "data",
                                                              "datalist",
                                                              "del",
                                                              "dfn",
                                                              "em",
                                                              "embed",
                                                              "font",
                                                              "i",
                                                              "iframe",
                                                              "img",
                                                              "input",
                                                              "ins",
                                                              "kbd",
                                                              "label",
                                                              "map",
                                                              "mark",
                                                              "math",
                                                              "meter",
                                                              "noscript",
                                                              "object",
                                                              "output",
                                                              "picture",
                                                              "progress",
                                                              "q",
                                                              "ruby",
                                                              "s",
                                                              "samp",
                                                              "script",
                                                              "select",
                                                              "slot",
                                                              "small",
                                                              "span",
                                                              "strike",
                                                              "strong",
                                                              "sub",
                                                              "sup",
                                                              "svg",
                                                              "template",
                                                              "textarea",
                                                              "time",
                                                              "tt",
                                                              "u",
                                                              "var",
                                                              "video",
                                                              "wbr");
  private static final Set <String> FORMATTED_ELEMENTS = Set.of ("pre", "script", "style", "title", "textarea");
  private static final Map <String, Set <String>> URI_ATTRS;
  static
  {
    final ICommonsMap <String, Set <String>> aMap = new CommonsHashMap <> ();
    aMap.put ("a", Set.of ("href"));
    aMap.put ("applet", Set.of ("codebase"));
    aMap.put ("area", Set.of ("href"));
    aMap.put ("base", Set.of ("href"));
    aMap.put ("blockquote", Set.of ("cite"));
    aMap.put ("body", Set.of ("background"));
    aMap.put ("button", Set.of ("formaction"));
    aMap.put ("del", Set.of ("cite"));
    aMap.put ("form", Set.of ("action"));
    aMap.put ("frame", Set.of ("longdesc", "src"));
    aMap.put ("head", Set.of ("profile"));
    aMap.put ("iframe", Set.of ("longdesc", "src"));
    aMap.put ("img", Set.of ("longdesc", "src", "usemap", "lowsrc", "dynsrc"));
    aMap.put ("input", Set.of ("src", "usemap", "formaction"));
    aMap.put ("ins", Set.of ("cite"));
    aMap.put ("link", Set.of ("href"));
    aMap.put ("object", Set.of ("classid", "codebase", "data", "usemap"));
    aMap.put ("q", Set.of ("cite"));
    aMap.put ("script", Set.of ("src"));
    aMap.put ("audio", Set.of ("src"));
    aMap.put ("video", Set.of ("src", "poster"));
    aMap.put ("source", Set.of ("src"));
    aMap.put ("track", Set.of ("src"));
    aMap.put ("table", Set.of ("background"));
    aMap.put ("tbody", Set.of ("background"));
    aMap.put ("td", Set.of ("background"));
    aMap.put ("tfoot", Set.of ("background"));
    aMap.put ("th", Set.of ("background"));
    aMap.put ("thead", Set.of ("background"));
    aMap.put ("tr", Set.of ("background"));
    aMap.put ("xmp", Set.of ("href"));
    URI_ATTRS = aMap;
  }

  /**
   * An element whose start tag is written and whose children are being written: the index of the
   * next child, the qualified name for the end tag, the size of the namespace undo log before the
   * start tag, and the indentation decisions taken when it was opened.
   */
  private static final class Frame
  {
    private final IXdmNode m_aElement;
    private final int m_nDepth;
    private final String m_sQName;
    private final int m_nUndoMark;
    private final boolean m_bIndentClose;
    private final boolean m_bSetsSuppress;
    private final boolean m_bMayIndentChildren;
    private int m_nNext = 0;

    Frame (@NonNull final IXdmNode aElement,
           final int nDepth,
           @NonNull final String sQName,
           final int nUndoMark,
           final boolean bIndentClose,
           final boolean bSetsSuppress,
           final boolean bMayIndentChildren)
    {
      m_aElement = aElement;
      m_nDepth = nDepth;
      m_sQName = sQName;
      m_nUndoMark = nUndoMark;
      m_bIndentClose = bIndentClose;
      m_bSetsSuppress = bSetsSuppress;
      m_bMayIndentChildren = bMayIndentChildren;
    }
  }

  /** Buffered characters written to the target writer when it has one. */
  static final int FLUSH_SIZE = 16 * 1024;

  private final SerializationParams m_aParams;
  private final OutputCharset m_aCharset;
  private final StringBuilder m_aSB;
  private final Writer m_aTarget;
  private final boolean m_bHtml;
  private final boolean m_bXhtml;
  private final boolean m_bHtml5;
  private final boolean m_bXml11;
  private final boolean m_bIndent;
  // Output namespace scope with an undo log
  private final ICommonsMap <String, String> m_aScope = new CommonsLinkedHashMap <> ();
  private final ICommonsList <String []> m_aUndo = new CommonsArrayList <> ();
  private boolean m_bSuppress = false;
  private boolean m_bDoctypeDone = false;
  // The elements opened by openElement, innermost first
  private final ArrayDeque <Frame> m_aOpen = new ArrayDeque <> ();

  MarkupWriter (@NonNull final SerializationParams aParams,
                @NonNull final OutputCharset aCharset,
                @NonNull final StringBuilder aSB,
                @NonNull final String sMethod)
  {
    this (aParams, aCharset, aSB, sMethod, null);
  }

  /**
   * @param aParams
   *        Serialization parameters.
   * @param aCharset
   *        Output charset.
   * @param aSB
   *        The buffer everything is written to.
   * @param sMethod
   *        xml, xhtml or html.
   * @param aTarget
   *        If not <code>null</code>, the buffer is written to it whenever it has grown beyond
   *        {@link #FLUSH_SIZE} characters.
   */
  MarkupWriter (@NonNull final SerializationParams aParams,
                @NonNull final OutputCharset aCharset,
                @NonNull final StringBuilder aSB,
                @NonNull final String sMethod,
                @Nullable final Writer aTarget)
  {
    m_aParams = aParams;
    m_aCharset = aCharset;
    m_aSB = aSB;
    m_aTarget = aTarget;
    m_bHtml = SerializationParams.METHOD_HTML.equals (sMethod);
    m_bXhtml = SerializationParams.METHOD_XHTML.equals (sMethod);
    m_bHtml5 = (m_bHtml || m_bXhtml) && _getHtmlVersion (aParams, m_bHtml).compareTo (BigDecimal.valueOf (5)) >= 0;
    m_bXml11 = !m_bHtml && aParams.isXml11 ();
    m_bIndent = aParams.isIndent ();
    m_aScope.put ("xml", XdmConstants.NS_XML);
  }

  /**
   * The HTML version that decides between the HTML5 and the older rules: the html-version parameter
   * if set, else for the html method the version parameter if it is a decimal number, else 4. For
   * the xhtml method the version parameter is the XML version and is not consulted.
   *
   * @param aParams
   *        The serialization parameters.
   * @param bHtmlMethod
   *        <code>true</code> for the html method.
   * @return The HTML version.
   */
  @NonNull
  private static BigDecimal _getHtmlVersion (@NonNull final SerializationParams aParams, final boolean bHtmlMethod)
  {
    if (aParams.getHtmlVersion () != null)
      return aParams.getHtmlVersion ();
    if (bHtmlMethod && aParams.getVersion () != null)
      try
      {
        return new BigDecimal (aParams.getVersion ().trim ());
      }
      catch (final NumberFormatException ex)
      {
        // fall through
      }
    return BigDecimal.valueOf (4);
  }

  /**
   * Append a decimal character reference <code>&amp;#n;</code>.
   */
  private static void _charRef (@NonNull final StringBuilder aSB, final int cp)
  {
    aSB.append ("&#").append (cp).append (';');
  }

  /**
   * The common per-character tail of text and attribute escaping: characters outside the encoding,
   * XML 1.1 control characters and (html/xhtml) C1 controls become character references.
   */
  private void _appendChar (@NonNull final StringBuilder aSB, final int cp)
  {
    // The C0 controls (bar tab, LF and CR) cannot be written in XML 1.0 in any
    // form (Serialization 3.1 section 4)
    if (!m_bXml11 && !m_bHtml && cp >= 0x1 && cp <= 0x1F && cp != 0x9 && cp != 0xA && cp != 0xD)
      throw XPathException.of ("SERE0006",
                               "The character #x" +
                                           Integer.toHexString (cp).toUpperCase (Locale.ROOT) +
                                           " cannot be serialized as XML 1.0");

    if ((m_bXml11 &&
         ((cp >= 0x1 && cp <= 0x1F && cp != 0x9 && cp != 0xA && cp != 0xD) || (cp >= 0x7F && cp <= 0x9F))) ||
        ((m_bHtml || m_bXhtml) && cp >= 0x80 && cp <= 0x9F) ||
        !m_aCharset.canEncode (cp))
      _charRef (aSB, cp);
    else
      if (m_bHtml && cp == 0xA0)
        aSB.append ("&nbsp;");
      else
        aSB.appendCodePoint (cp);
  }

  /**
   * Escape text content: <code>&amp;</code>, <code>&lt;</code> and <code>&gt;</code> become entity
   * references, CR becomes <code>&amp;#13;</code> (written as it is with the html method), and
   * every other character goes through {@link #_appendChar(StringBuilder, int)}.
   *
   * @throws XPathException
   *         SERE0006 for a C0 control character with XML 1.0
   */
  private void _escapeText (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length ();)
    {
      final int cp = s.codePointAt (i);
      i += Character.charCount (cp);
      switch (cp)
      {
        case '&':
          aSB.append ("&amp;");
          break;
        case '<':
          aSB.append ("&lt;");
          break;
        case '>':
          aSB.append ("&gt;");
          break;
        case '\r':
          if (m_bHtml)
            aSB.append ('\r');
          else
            aSB.append ("&#13;");
          break;
        default:
          _appendChar (aSB, cp);
      }
    }
  }

  /**
   * Write the content of an HTML <code>script</code> or <code>style</code> element with the html
   * method, where character references are not recognised: nothing is escaped (Serialization 3.1
   * section 7.1).
   *
   * @throws XPathException
   *         SERE0008 for a character the encoding cannot represent
   */
  private void _escapeRaw (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length ();)
    {
      final int cp = s.codePointAt (i);
      i += Character.charCount (cp);
      if (!m_aCharset.canEncode (cp))
        throw XPathException.of ("SERE0008",
                                 "The character U+" +
                                             Integer.toHexString (cp) +
                                             " cannot be represented in the output encoding inside an HTML script or style element");
      aSB.appendCodePoint (cp);
    }
  }

  /**
   * Write text with disable-output-escaping: written as it is, except that a character the encoding
   * cannot represent is written as a character reference, since the request to disable output
   * escaping is ignored for it (XSLT 3.0 section 26.2).
   */
  private void _writeUnescaped (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length ();)
    {
      final int cp = s.codePointAt (i);
      i += Character.charCount (cp);
      if (!m_aCharset.canEncode (cp))
        _charRef (aSB, cp);
      else
        aSB.appendCodePoint (cp);
    }
  }

  /**
   * Escape an attribute value (also used for namespace declarations): <code>&amp;</code>,
   * <code>&lt;</code>, <code>&gt;</code>, <code>"</code>, tab, LF and CR are escaped, every other
   * character goes through {@link #_appendChar(StringBuilder, int)}. With the html method an
   * ampersand directly followed by <code>{</code>, and <code>&lt;</code>, tab and LF are written as
   * they are.
   *
   * @throws XPathException
   *         SERE0006 for a C0 control character with XML 1.0
   */
  private void _escapeAttr (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length ();)
    {
      final int cp = s.codePointAt (i);
      i += Character.charCount (cp);
      switch (cp)
      {
        case '&':
          // HTML: an ampersand directly followed by '{' is not escaped
          if (m_bHtml && i < s.length () && s.charAt (i) == '{')
            aSB.append ('&');
          else
            aSB.append ("&amp;");
          break;
        case '<':
          if (m_bHtml)
            aSB.append ('<');
          else
            aSB.append ("&lt;");
          break;
        case '>':
          aSB.append ("&gt;");
          break;
        case '"':
          aSB.append ("&#34;");
          break;
        case '\t':
          if (m_bHtml)
            aSB.append ('\t');
          else
            aSB.append ("&#9;");
          break;
        case '\n':
          if (m_bHtml)
            aSB.append ('\n');
          else
            aSB.append ("&#10;");
          break;
        case '\r':
          aSB.append ("&#13;");
          break;
        default:
          _appendChar (aSB, cp);
      }
    }
  }

  /**
   * Escape a URI attribute value for escape-uri-attributes (Serialization 3.1 sections 6 and 7):
   * the value is normalised to NFC, encoded as UTF-8, and every byte outside the printable ASCII
   * range 32 to 126 is written as <code>%HH</code> with upper-case hex digits. Spaces are kept.
   */
  @NonNull
  private static String _escapeUri (@NonNull final String sValue)
  {
    final byte [] aBytes = Normalizer.normalize (sValue, Normalizer.Form.NFC).getBytes (StandardCharsets.UTF_8);
    final StringBuilder aSB = new StringBuilder (aBytes.length);
    for (final byte b : aBytes)
    {
      final int n = b & 0xff;
      if (n >= 32 && n <= 126)
        aSB.append ((char) n);
      else
        aSB.append ('%').append (StringHex.getHexCharUpperCase (n >> 4)).append (StringHex.getHexCharUpperCase (n & 0xf));
    }
    return aSB.toString ();
  }

  /**
   * Whether the HTML rules apply to a node: with the html method an element in no namespace, or
   * (HTML5) in the XHTML namespace; with the xhtml method an element in the XHTML namespace; never
   * with the xml method.
   *
   * @param aNode
   *        The node, may be <code>null</code>.
   * @return <code>false</code> for <code>null</code> and non-elements.
   */
  private boolean _isHtmlElement (@Nullable final IXdmNode aNode)
  {
    if (aNode == null || aNode.getKind () != ENodeKind.ELEMENT)
      return false;

    final String sNS = aNode.getName ().namespaceUri ();
    if (m_bHtml)
      return sNS.isEmpty () || (m_bHtml5 && NS_XHTML.equals (sNS));
    if (m_bXhtml)
      return NS_XHTML.equals (sNS);
    return false;
  }

  /**
   * @param aNode
   *        The node, may be <code>null</code>.
   * @return <code>true</code> for an HTML element (see {@link #_isHtmlElement(IXdmNode)}) whose
   *         local name, in lower case, is one of the phrasing elements in
   *         <code>INLINE_ELEMENTS</code>; no line break is added for indentation before or after
   *         such an element.
   */
  private boolean _isInline (@Nullable final IXdmNode aNode)
  {
    return _isHtmlElement (aNode) && INLINE_ELEMENTS.contains (aNode.getName ().localName ().toLowerCase (Locale.ROOT));
  }

  /**
   * @return <code>true</code> if the string consists of XML whitespace (space, tab, line feed,
   *         carriage return) only, also for the empty string.
   */
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

  /**
   * Whether indentation may add whitespace between the children of an element: not inside the HTML
   * elements pre, script, style, title and textarea, and not if the element has a text child with
   * non-whitespace content (mixed content).
   */
  private boolean _mayIndentIn (@NonNull final IXdmNode aElement)
  {
    if (_isHtmlElement (aElement) &&
        FORMATTED_ELEMENTS.contains (aElement.getName ().localName ().toLowerCase (Locale.ROOT)))
      return false;

    for (int i = 0; i < aElement.getChildCount (); ++i)
    {
      final IXdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.getKind () == ENodeKind.TEXT && !_isWhitespace (aChild.getValue ()))
        return false;
    }
    return true;
  }

  private static boolean _hasElementChildren (@NonNull final IXdmNode aElement)
  {
    for (int i = 0; i < aElement.getChildCount (); ++i)
      if (aElement.getChildAtIndex (i).getKind () == ENodeKind.ELEMENT)
        return true;
    return false;
  }

  @Nullable
  private static IXdmNode _lastChild (@NonNull final IXdmNode aElement)
  {
    final int n = aElement.getChildCount ();
    return n == 0 ? null : aElement.getChildAtIndex (n - 1);
  }

  /**
   * Write the buffer to the target writer.
   *
   * @throws UncheckedIOException
   *         on a write error
   */
  void flushBuffer ()
  {
    if (m_aTarget != null && m_aSB.length () > 0)
    {
      try
      {
        m_aTarget.append (m_aSB);
      }
      catch (final IOException ex)
      {
        throw new UncheckedIOException (ex);
      }
      m_aSB.setLength (0);
    }
  }

  /**
   * Write the buffer to the target writer once it holds at least {@link #FLUSH_SIZE} characters;
   * without a target writer nothing happens.
   *
   * @throws UncheckedIOException
   *         on a write error
   */
  private void _flushIfFull ()
  {
    if (m_aSB.length () >= FLUSH_SIZE)
      flushBuffer ();
  }

  /**
   * Append a line feed and two spaces of indentation per depth level.
   */
  private void _newline (final int nDepth)
  {
    m_aSB.append ('\n');
    for (int i = 0; i < nDepth; ++i)
      m_aSB.append ("  ");
  }

  /**
   * Bind a prefix in the output namespace scope, recording the previous binding in the undo log,
   * and append the declaration (<code>xmlns="uri"</code> for the empty prefix) to
   * <code>aDecls</code>. An empty URI declares an undeclaration.
   */
  private void _bind (@NonNull final String sPrefix, @NonNull final String sUri, @NonNull final StringBuilder aDecls)
  {
    m_aUndo.add (new String [] { sPrefix, m_aScope.get (sPrefix) });
    m_aScope.put (sPrefix, sUri);
    aDecls.append (sPrefix.isEmpty () ? " xmlns" : " xmlns:" + sPrefix).append ("=\"");
    _escapeAttr (aDecls, sUri);
    aDecls.append ('"');
  }

  /**
   * Undo the bindings recorded since the undo log had the size <code>nMark</code>, newest first,
   * restoring the previous binding or removing a prefix that was not bound before.
   */
  private void _unwind (final int nMark)
  {
    for (int i = m_aUndo.size () - 1; i >= nMark; --i)
    {
      final String [] aEntry = m_aUndo.remove (i);
      if (aEntry[1] == null)
        m_aScope.remove (aEntry[0]);
      else
        m_aScope.put (aEntry[0], aEntry[1]);
    }
  }

  /**
   * @return <code>true</code> if the prefix is bound to the URI in the output scope; an unbound
   *         prefix counts as bound to the empty URI.
   */
  private boolean _isBound (@NonNull final String sPrefix, @NonNull final String sUri)
  {
    final String sCur = m_aScope.get (sPrefix);
    return sCur == null ? sUri.isEmpty () : sCur.equals (sUri);
  }

  /** @return A prefix for a namespaced name, declaring it when needed. */
  @NonNull
  private String _getPrefix (@NonNull final XdmQName aName,
                             final boolean bAttribute,
                             @NonNull final StringBuilder aDecls)
  {
    final String sUri = aName.namespaceUri ();
    String sPrefix = aName.prefix ();
    if (bAttribute && sPrefix.isEmpty ())
    {
      // Attributes need a prefix: reuse one bound to the URI
      for (final Map.Entry <String, String> aEntry : m_aScope.entrySet ())
        if (!aEntry.getKey ().isEmpty () && aEntry.getValue ().equals (sUri))
          return aEntry.getKey ();
      sPrefix = "ns0";
    }

    if (_isBound (sPrefix, sUri))
      return sPrefix;

    // Avoid redefining a prefix already declared on this element
    if (m_aScope.containsKey (sPrefix) &&
        aDecls.indexOf (sPrefix.isEmpty () ? " xmlns=" : " xmlns:" + sPrefix + "=") >= 0)
    {
      final String sBase = sPrefix.isEmpty () ? "ns" : sPrefix;
      int n = 0;
      do
      {
        sPrefix = sBase + "_" + n++;
      } while (m_aScope.containsKey (sPrefix) && !_isBound (sPrefix, sUri));
      if (_isBound (sPrefix, sUri))
        return sPrefix;
    }
    _bind (sPrefix, sUri, aDecls);
    return sPrefix;
  }

  /**
   * @return <code>true</code> for a <code>meta</code> element (local name compared
   *         case-insensitively, any namespace) with an <code>http-equiv</code> attribute whose
   *         trimmed value is <code>Content-Type</code> in any case.
   */
  private static boolean _isContentTypeMeta (@NonNull final IXdmNode aNode)
  {
    if (aNode.getKind () != ENodeKind.ELEMENT || !"meta".equalsIgnoreCase (aNode.getName ().localName ()))
      return false;

    for (final IXdmNode aAttr : aNode.getAllAttributes ())
      if (!aAttr.getName ().hasNamespace () &&
          "http-equiv".equalsIgnoreCase (aAttr.getName ().localName ()) &&
          "content-type".equalsIgnoreCase (aAttr.getValue ().trim ()))
        return true;
    return false;
  }

  /**
   * @return The content of the Content-Type <code>meta</code> element: the media-type parameter
   *         (default <code>text/html</code>) followed by <code>; charset=</code> and the encoding
   *         parameter as given.
   */
  @NonNull
  private String _getContentTypeValue ()
  {
    final String sMediaType = m_aParams.getMediaType () != null ? m_aParams.getMediaType () : "text/html";
    return sMediaType + "; charset=" + m_aParams.getEncoding ();
  }

  /**
   * @return <code>true</code> for a <code>head</code> element (local name compared
   *         case-insensitively) in the XHTML namespace, or with the html method also in no
   *         namespace.
   */
  private boolean _isHead (@Nullable final IXdmNode aNode)
  {
    return aNode != null &&
           aNode.getKind () == ENodeKind.ELEMENT &&
           "head".equalsIgnoreCase (aNode.getName ().localName ()) &&
           ((m_bHtml && !aNode.getName ().hasNamespace ()) || NS_XHTML.equals (aNode.getName ().namespaceUri ()));
  }

  /**
   * Write a start tag up to (not including) the closing '&gt;'.
   *
   * @return The qualified name used.
   */
  @NonNull
  private String _writeStartTag (@NonNull final IXdmNode aElement, final boolean bTop)
  {
    final XdmQName aName = aElement.getName ();
    final boolean bNormalisePrefixes = m_bHtml5 && (m_bHtml || m_bXhtml);
    final StringBuilder aDecls = new StringBuilder ();
    boolean bUndeclaresDefault = false;

    // Most elements declare nothing: no copy of an empty map for them
    final Map <String, String> aNamespaces = bTop ? aElement.getInScopeNamespaces ()
                                                  : aElement.hasDeclaredNamespaces () ? aElement.getDeclaredNamespaces ()
                                                                                      : Map.of ();
    for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
    {
      final String sPrefix = aEntry.getKey ();
      final String sUri = aEntry.getValue ();
      if ("xml".equals (sPrefix))
        continue;
      if (bNormalisePrefixes && !sPrefix.isEmpty () && HTML_FAMILY_NS.contains (sUri))
        continue;
      if (sUri.isEmpty ())
      {
        if (sPrefix.isEmpty ())
        {
          // Default namespace undeclaration: handled with the element name
          bUndeclaresDefault = true;
          continue;
        }
        if (m_aParams.isUndeclarePrefixes () && m_bXml11 && m_aScope.containsKey (sPrefix))
          _bind (sPrefix, "", aDecls);
        continue;
      }
      if (!_isBound (sPrefix, sUri))
        _bind (sPrefix, sUri, aDecls);
    }

    final String sQName;
    if (!aName.hasNamespace ())
    {
      if (!_isBound ("", ""))
        _bind ("", "", aDecls);
      sQName = aName.localName ();
    }
    else
      if (bNormalisePrefixes && HTML_FAMILY_NS.contains (aName.namespaceUri ()))
      {
        if (!_isBound ("", aName.namespaceUri ()))
          _bind ("", aName.namespaceUri (), aDecls);
        sQName = aName.localName ();
      }
      else
      {
        final String sPrefix = _getPrefix (aName, false, aDecls);
        sQName = sPrefix.isEmpty () ? aName.localName () : sPrefix + ":" + aName.localName ();
        // A prefixed element that undeclares the default namespace while one
        // is in scope in the output
        if (bUndeclaresDefault && !sPrefix.isEmpty () && !_isBound ("", ""))
          _bind ("", "", aDecls);
      }

    final boolean bHtmlElement = _isHtmlElement (aElement);
    final boolean bFixContentType = (m_bHtml || m_bXhtml) &&
                                    m_aParams.isIncludeContentType () &&
                                    _isHead (aElement.getParent ()) &&
                                    _isContentTypeMeta (aElement);
    final StringBuilder aAttrs = new StringBuilder ();
    // The lower-case names are only needed by the HTML and XHTML methods
    final boolean bHtmlRules = m_bHtml || m_bXhtml;
    final String sLocalLC = bHtmlRules ? aName.localName ().toLowerCase (Locale.ROOT) : null;
    for (final IXdmNode aAttr : aElement.getAllAttributes ())
    {
      final XdmQName aAttrName = aAttr.getName ();
      if (bFixContentType && !aAttrName.hasNamespace () && "content".equalsIgnoreCase (aAttrName.localName ()))
        continue;
      String sAttrQName = aAttrName.localName ();
      if (aAttrName.hasNamespace ())
        sAttrQName = _getPrefix (aAttrName, true, aDecls) + ":" + aAttrName.localName ();
      final String sValue = aAttr.getValue ();
      final String sAttrLC = bHtmlRules ? aAttrName.localName ().toLowerCase (Locale.ROOT) : null;
      if (m_bHtml &&
          bHtmlElement &&
          !aAttrName.hasNamespace () &&
          BOOLEAN_ATTRS.contains (sAttrLC) &&
          sValue.equalsIgnoreCase (aAttrName.localName ()))
      {
        aAttrs.append (' ').append (sAttrQName);
        continue;
      }
      aAttrs.append (' ').append (sAttrQName).append ("=\"");
      final Set <String> aUriAttrs = bHtmlRules ? URI_ATTRS.get (sLocalLC) : null;
      if (bHtmlRules &&
          bHtmlElement &&
          m_aParams.isEscapeUriAttributes () &&
          !aAttrName.hasNamespace () &&
          aUriAttrs != null &&
          aUriAttrs.contains (sAttrLC))
        _escapeAttr (aAttrs, _escapeUri (sValue));
      else
        m_aCharset.mapAndEscape (aAttrs, sValue, this::_escapeAttr);
      aAttrs.append ('"');
    }
    if (bFixContentType)
    {
      aAttrs.append (" content=\"");
      m_aCharset.mapAndEscape (aAttrs, _getContentTypeValue (), this::_escapeAttr);
      aAttrs.append ('"');
    }
    m_aSB.append ('<').append (sQName).append (aDecls).append (aAttrs);
    return sQName;
  }

  /**
   * @return The complete Content-Type <code>meta</code> element the html and xhtml methods add as
   *         first child of <code>head</code> (include-content-type, Serialization 3.1 sections 6
   *         and 7), as an empty-element tag for xhtml.
   */
  @NonNull
  private String _getContentTypeMeta ()
  {
    final StringBuilder aSB = new StringBuilder ("<meta http-equiv=\"Content-Type\" content=\"");
    m_aCharset.mapAndEscape (aSB, _getContentTypeValue (), this::_escapeAttr);
    aSB.append (m_bXhtml ? "\" />" : "\">");
    return aSB.toString ();
  }

  /**
   * @return <code>true</code> if the element is a <code>head</code> element that gets a generated
   *         Content-Type <code>meta</code> element: html or xhtml method, include-content-type set,
   *         and no such <code>meta</code> child present (an existing one is rewritten in place
   *         instead by {@link #_writeStartTag(IXdmNode, boolean)}).
   */
  private boolean _headNeedsMeta (@NonNull final IXdmNode aElement)
  {
    if ((m_bHtml || m_bXhtml) && m_aParams.isIncludeContentType () && _isHead (aElement))
    {
      for (final IXdmNode aChild : aElement.getAllChildren ())
        if (_isContentTypeMeta (aChild))
          return false;
      return true;
    }
    return false;
  }

  /**
   * @return The name without its prefix, for the look-up in the cdata-section-elements and
   *         suppress-indentation sets, which hold expanded names.
   */
  @NonNull
  private static XdmQName _plainName (@NonNull final XdmQName aName)
  {
    return aName.hasPrefix () ? new XdmQName (aName.namespaceUri (), aName.localName ()) : aName;
  }

  /**
   * Write text as CDATA sections (cdata-section-elements). The text is normalised; a
   * <code>]]&gt;</code> inside is split over two sections, and a character the encoding cannot
   * represent ends the current section and is written as a character reference between sections.
   * The character map is not applied.
   */
  private void _writeCData (@NonNull final String sText)
  {
    final String s = m_aCharset.normalize (sText);
    final StringBuilder aRun = new StringBuilder ();
    for (int i = 0; i < s.length ();)
    {
      final int cp = s.codePointAt (i);
      i += Character.charCount (cp);
      if (!m_aCharset.canEncode (cp))
      {
        if (aRun.length () > 0)
        {
          m_aSB.append ("<![CDATA[").append (aRun.toString ().replace ("]]>", "]]]]><![CDATA[>")).append ("]]>");
          aRun.setLength (0);
        }
        _charRef (m_aSB, cp);
      }
      else
        aRun.appendCodePoint (cp);
    }
    if (aRun.length () > 0 || s.isEmpty ())
      m_aSB.append ("<![CDATA[").append (aRun.toString ().replace ("]]>", "]]]]><![CDATA[>")).append ("]]>");
  }

  /**
   * Write text content: nothing for the empty string; as CDATA if the parent is in
   * cdata-section-elements (not with the html method); unescaped for the content of an HTML
   * <code>script</code> or <code>style</code> element with the html method (SERE0008 for a
   * character the encoding cannot represent); escaped otherwise. Character map and normalisation
   * are applied in the last two cases.
   *
   * @param sText
   *        The text.
   * @param aParent
   *        The parent element, <code>null</code> at top level.
   */
  private void _writeText (@NonNull final String sText, @Nullable final IXdmNode aParent)
  {
    if (sText.isEmpty ())
      return;
    if (aParent != null && !m_bHtml && m_aParams.isCdataSectionElement (_plainName (aParent.getName ())))
    {
      _writeCData (sText);
      return;
    }
    if (m_bHtml &&
        aParent != null &&
        _isHtmlElement (aParent) &&
        ("script".equalsIgnoreCase (aParent.getName ().localName ()) ||
         "style".equalsIgnoreCase (aParent.getName ().localName ())))
    {
      m_aCharset.mapAndEscape (m_aSB, sText, this::_escapeRaw);
      return;
    }
    m_aCharset.mapAndEscape (m_aSB, sText, this::_escapeText);
  }

  /**
   * Write a text, comment or processing instruction node; other node kinds are ignored. Text with
   * disable-output-escaping is written as it is, bar the characters the encoding cannot represent
   * (see {@link #_writeUnescaped(StringBuilder, String)}). Comments and processing instructions are
   * written without any escaping or encoding check (the adaptive method delegates to this writer,
   * and the W3C XSLT test output-0707 expects no error for a character outside the encoding in a
   * comment there); a processing instruction ends with <code>&gt;</code> instead of
   * <code>?&gt;</code> with the html method.
   *
   * @param aNode
   *        The node.
   * @param aParent
   *        The parent element, <code>null</code> at top level.
   */
  private void _writeLeaf (@NonNull final IXdmNode aNode, @Nullable final IXdmNode aParent)
  {
    switch (aNode.getKind ())
    {
      case TEXT:
        if (aNode.isDisableOutputEscaping ())
          _writeUnescaped (m_aSB, aNode.getValue ());
        else
          _writeText (aNode.getValue (), aParent);
        break;
      case COMMENT:
        m_aSB.append ("<!--").append (aNode.getValue ()).append ("-->");
        break;
      case PROCESSING_INSTRUCTION:
        m_aSB.append ("<?").append (aNode.getName ().localName ());
        if (!aNode.getValue ().isEmpty ())
          m_aSB.append (' ').append (aNode.getValue ());
        m_aSB.append (m_bHtml ? ">" : "?>");
        break;
      default:
        break;
    }
  }

  private boolean _isSuppressed (@NonNull final IXdmNode aElement)
  {
    return m_aParams.isSuppressIndentation (_plainName (aElement.getName ()));
  }

  /**
   * Open an element: write its start tag and either finish it (no children) or return a frame for
   * its children. With <code>bForceOpen</code> the element is treated as having children although
   * it has none yet (its content is written incrementally).
   */
  @Nullable
  private Frame _openElement (@NonNull final IXdmNode aElement,
                              final int nDepth,
                              @Nullable final IXdmNode aPrev,
                              final boolean bParentMayIndent,
                              final boolean bTop,
                              final boolean bForceOpen)
  {
    final boolean bTriggersSuppress = !m_bSuppress && _isSuppressed (aElement);
    final boolean bIndentClose = m_bIndent &&
                                 !m_bSuppress &&
                                 !bTriggersSuppress &&
                                 _hasElementChildren (aElement) &&
                                 _mayIndentIn (aElement) &&
                                 !_isInline (_lastChild (aElement));
    if (m_bIndent && !m_bSuppress && nDepth > 0 && bParentMayIndent && !_isInline (aElement) && !_isInline (aPrev))
      _newline (nDepth);
    final int nMark = m_aUndo.size ();
    final String sQName = _writeStartTag (aElement, bTop);
    final String sMeta = _headNeedsMeta (aElement) ? _getContentTypeMeta () : null;
    if (!bForceOpen && aElement.getChildCount () == 0)
    {
      final String sLocalLC = aElement.getName ().localName ().toLowerCase (Locale.ROOT);
      if (sMeta != null)
        m_aSB.append ('>').append (sMeta).append ("</").append (sQName).append ('>');
      else
        if (m_bHtml && _isHtmlElement (aElement))
        {
          if (VOID_ELEMENTS.contains (sLocalLC))
            m_aSB.append ('>');
          else
            m_aSB.append ("></").append (sQName).append ('>');
        }
        else
          if (m_bXhtml && (_isHtmlElement (aElement) || (m_bHtml5 && !aElement.getName ().hasNamespace ())))
          {
            if (VOID_ELEMENTS.contains (sLocalLC))
              m_aSB.append (" />");
            else
              m_aSB.append ("></").append (sQName).append ('>');
          }
          else
            m_aSB.append ("/>");
      _unwind (nMark);
      return null;
    }
    m_aSB.append ('>');
    if (sMeta != null)
      m_aSB.append (sMeta);
    if (bTriggersSuppress)
      m_bSuppress = true;
    return new Frame (aElement,
                      nDepth,
                      sQName,
                      nMark,
                      bIndentClose,
                      bTriggersSuppress,
                      m_bIndent && _mayIndentIn (aElement));
  }

  /**
   * Write the end tag of an element opened by
   * {@link #_openElement(IXdmNode, int, IXdmNode, boolean, boolean, boolean)}, preceded by a line
   * break if indentation decided so, drop the namespace bindings of its start tag and end the
   * suppress-indentation region the element started.
   */
  private void _closeElement (@NonNull final Frame aFrame)
  {
    if (aFrame.m_bIndentClose)
      _newline (aFrame.m_nDepth);
    m_aSB.append ("</").append (aFrame.m_sQName).append ('>');
    _unwind (aFrame.m_nUndoMark);
    if (aFrame.m_bSetsSuppress)
      m_bSuppress = false;
  }

  /**
   * Write an element with its subtree, iteratively with an explicit stack of frames, so that the
   * depth of the tree is not limited by the Java stack. The buffer is moved to the target writer
   * when it is full.
   *
   * @param aRoot
   *        The element.
   * @param aPrev
   *        The preceding sibling (or preceding top-level item), for the inline check of
   *        indentation; may be <code>null</code>.
   * @param nDepth
   *        The indentation depth of the element.
   * @param bParentMayIndent
   *        <code>false</code> if the parent forbids added whitespace.
   * @param bTop
   *        <code>true</code> if the element has no output parent, so that all its in-scope
   *        namespaces are declared.
   */
  private void _writeElement (@NonNull final IXdmNode aRoot,
                              @Nullable final IXdmNode aPrev,
                              final int nDepth,
                              final boolean bParentMayIndent,
                              final boolean bTop)
  {
    final ArrayDeque <Frame> aStack = new ArrayDeque <> ();
    final Frame aRootFrame = _openElement (aRoot, nDepth, aPrev, bParentMayIndent, bTop, false);
    if (aRootFrame != null)
      aStack.push (aRootFrame);
    while (!aStack.isEmpty ())
    {
      _flushIfFull ();
      final Frame aFrame = aStack.peek ();
      final IXdmNode aElement = aFrame.m_aElement;
      if (aFrame.m_nNext >= aElement.getChildCount ())
      {
        aStack.pop ();
        _closeElement (aFrame);
        continue;
      }
      final int nIndex = aFrame.m_nNext++;
      final IXdmNode aChild = aElement.getChildAtIndex (nIndex);
      if (aChild.getKind () == ENodeKind.ELEMENT)
      {
        final Frame aChildFrame = _openElement (aChild,
                                                aFrame.m_nDepth + 1,
                                                nIndex > 0 ? aElement.getChildAtIndex (nIndex - 1) : null,
                                                aFrame.m_bMayIndentChildren,
                                                false,
                                                false);
        if (aChildFrame != null)
          aStack.push (aChildFrame);
      }
      else
        _writeLeaf (aChild, aElement);
    }
  }

  /**
   * Write a top-level element with its subtree at depth 0.
   */
  private void _writeElement (@NonNull final IXdmNode aRoot, @Nullable final IXdmNode aPrev)
  {
    _writeElement (aRoot, aPrev, 0, true, true);
  }

  /**
   * @return The string in double quotes, or in single quotes if it contains a double quote.
   * @throws XPathException
   *         SEPM0016 if it contains both quote characters: no literal can hold it, and
   *         Serialization 3.1 section 3 excludes such a doctype-system value
   */
  @NonNull
  private static String _quote (@NonNull final String s)
  {
    if (s.indexOf ('"') < 0)
      return "\"" + s + "\"";
    if (s.indexOf ('\'') >= 0)
      throw XPathException.of ("SEPM0016", "The value '" + s + "' contains both an apostrophe and a quotation mark");
    return "'" + s + "'";
  }

  /**
   * Write the document type declaration before the first top-level element. With the html method:
   * if doctype-public or doctype-system is set, <code>&lt;!DOCTYPE html</code> (HTML5: the
   * element's local name) with PUBLIC and optional system literal or SYSTEM; otherwise, for HTML5
   * and a first element <code>html</code> in no namespace, <code>&lt;!DOCTYPE
   * html&gt;</code>. With xml and xhtml: if doctype-system is set, the element's lexical name with
   * PUBLIC (if doctype-public is set too) or SYSTEM; otherwise, for xhtml with HTML5 and a first
   * element <code>html</code> in the XHTML namespace, <code>&lt;!DOCTYPE
   * html&gt;</code>. doctype-public alone is ignored with xml and xhtml.
   *
   * @param aFirstElement
   *        The first top-level element.
   */
  private void _writeDoctype (@NonNull final IXdmNode aFirstElement)
  {
    final String sSystem = m_aParams.getDoctypeSystem ();
    final String sPublic = m_aParams.getDoctypePublic ();
    final String sLocal = aFirstElement.getName ().localName ();
    if (m_bHtml)
    {
      if (sSystem != null || sPublic != null)
      {
        m_aSB.append ("<!DOCTYPE ").append (m_bHtml5 ? sLocal : "html");
        if (sPublic != null)
        {
          m_aSB.append (" PUBLIC ").append (_quote (sPublic));
          if (sSystem != null)
            m_aSB.append (' ').append (_quote (sSystem));
        }
        else
          m_aSB.append (" SYSTEM ").append (_quote (sSystem));
        m_aSB.append ('>');
      }
      else
        if (m_bHtml5 && "html".equalsIgnoreCase (sLocal) && !aFirstElement.getName ().hasNamespace ())
          m_aSB.append ("<!DOCTYPE ").append (sLocal).append ('>');
      return;
    }
    if (sSystem != null)
    {
      final String sQName = aFirstElement.getName ().getLexicalName ();
      m_aSB.append ("<!DOCTYPE ").append (sQName);
      if (sPublic != null)
        m_aSB.append (" PUBLIC ").append (_quote (sPublic)).append (' ').append (_quote (sSystem));
      else
        m_aSB.append (" SYSTEM ").append (_quote (sSystem));
      m_aSB.append ('>');
    }
    else
      if (m_bXhtml &&
          m_bHtml5 &&
          "html".equalsIgnoreCase (sLocal) &&
          NS_XHTML.equals (aFirstElement.getName ().namespaceUri ()))
        m_aSB.append ("<!DOCTYPE ").append (sLocal).append ('>');
  }

  /**
   * Write the document type declaration before the first top-level element; later top-level
   * elements get none.
   */
  private void _beforeTopLevelElement (@NonNull final IXdmNode aElement)
  {
    if (!m_bDoctypeDone)
    {
      _writeDoctype (aElement);
      m_bDoctypeDone = true;
    }
  }

  /** Write the XML declaration, if the parameters ask for one. */
  void writeDeclaration ()
  {
    if (!m_bHtml && !m_aParams.isOmitXmlDeclaration ())
    {
      m_aSB.append ("<?xml version=\"").append (m_bXml11 ? "1.1" : "1.0").append ("\" encoding=\"");
      m_aSB.append (m_aParams.getEncoding ()).append ('"');
      if (!SerializationParams.STANDALONE_OMIT.equals (m_aParams.getStandalone ()))
        m_aSB.append (" standalone=\"").append (m_aParams.getStandalone ()).append ('"');
      m_aSB.append ("?>");
    }
  }

  /**
   * Write the normalised sequence.
   *
   * @param aItems
   *        Nodes (element, text, comment, processing instruction) and strings (text).
   */
  void write (@NonNull final List <Object> aItems)
  {
    writeDeclaration ();
    Object aPrev = null;
    for (final Object aItem : aItems)
    {
      _flushIfFull ();
      if (aItem instanceof final String sText)
        _writeText (sText, null);
      else
      {
        final IXdmNode aNode = (IXdmNode) aItem;
        if (aNode.getKind () == ENodeKind.ELEMENT)
        {
          _beforeTopLevelElement (aNode);
          _writeElement (aNode, aPrev instanceof final IXdmNode aPrevNode ? aPrevNode : null);
        }
        else
          _writeLeaf (aNode, null);
      }
      aPrev = aItem;
    }
  }

  /**
   * Incremental writing: write the start tag of an element whose content follows through
   * {@link #writeNode(IXdmNode)} and {@link #openElement(IXdmNode)}, and which is closed by
   * {@link #closeElement()}. The element is a child of the innermost open element, or a top-level
   * node if none is open. Its name, attributes and namespaces must be final. Indentation is not
   * supported.
   *
   * @param aElement
   *        The element.
   */
  void openElement (@NonNull final IXdmNode aElement)
  {
    final Frame aParent = m_aOpen.peek ();
    if (aParent == null)
      _beforeTopLevelElement (aElement);
    final Frame aFrame = _openElement (aElement,
                                       aParent == null ? 0 : aParent.m_nDepth + 1,
                                       null,
                                       aParent == null || aParent.m_bMayIndentChildren,
                                       aParent == null,
                                       true);
    m_aOpen.push (aFrame);
  }

  /** Incremental writing: write the end tag of the innermost open element. */
  void closeElement ()
  {
    if (m_aOpen.isEmpty ())
      throw new IllegalStateException ("No element is open");
    _closeElement (m_aOpen.pop ());
  }

  /**
   * Incremental writing: write a complete node as the next child of the innermost open element, or
   * as a top-level node if none is open.
   *
   * @param aNode
   *        An element, text, comment or processing instruction node.
   */
  void writeNode (@NonNull final IXdmNode aNode)
  {
    final Frame aParent = m_aOpen.peek ();
    if (aNode.getKind () == ENodeKind.ELEMENT)
    {
      if (aParent == null)
      {
        _beforeTopLevelElement (aNode);
        _writeElement (aNode, null);
      }
      else
        _writeElement (aNode, null, aParent.m_nDepth + 1, aParent.m_bMayIndentChildren, false);
    }
    else
      _writeLeaf (aNode, aParent == null ? null : aParent.m_aElement);
  }
}
