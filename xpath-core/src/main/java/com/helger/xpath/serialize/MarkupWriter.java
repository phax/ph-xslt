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
package com.helger.xpath.serialize;

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
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Writes a normalised sequence (nodes and strings) with the xml, xhtml or
 * html output method (Serialization 3.1 sections 5 to 7). The tree is walked
 * iteratively, so deep trees cannot overflow the stack.
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

  private static final class Frame
  {
    private final XdmNode m_aElement;
    private final int m_nDepth;
    private final String m_sQName;
    private final int m_nUndoMark;
    private final boolean m_bIndentClose;
    private final boolean m_bSetsSuppress;
    private final boolean m_bMayIndentChildren;
    private int m_nNext = 0;

    Frame (@NonNull final XdmNode aElement,
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

  private final SerializationParams m_aParams;
  private final OutputCharset m_aCharset;
  private final StringBuilder m_aSB;
  private final boolean m_bHtml;
  private final boolean m_bXhtml;
  private final boolean m_bHtml5;
  private final boolean m_bXml11;
  private final boolean m_bIndent;
  // Output namespace scope with an undo log
  private final ICommonsMap <String, String> m_aScope = new CommonsLinkedHashMap <> ();
  private final ICommonsList <String []> m_aUndo = new CommonsArrayList <> ();
  private boolean m_bSuppress = false;

  MarkupWriter (@NonNull final SerializationParams aParams,
                @NonNull final OutputCharset aCharset,
                @NonNull final StringBuilder aSB,
                @NonNull final String sMethod)
  {
    m_aParams = aParams;
    m_aCharset = aCharset;
    m_aSB = aSB;
    m_bHtml = SerializationParams.METHOD_HTML.equals (sMethod);
    m_bXhtml = SerializationParams.METHOD_XHTML.equals (sMethod);
    m_bHtml5 = (m_bHtml || m_bXhtml) && _getHtmlVersion (aParams, m_bHtml).compareTo (BigDecimal.valueOf (5)) >= 0;
    m_bXml11 = !m_bHtml && aParams.isXml11 ();
    m_bIndent = aParams.isIndent ();
    m_aScope.put ("xml", XdmConstants.NS_XML);
  }

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

  private static void _charRef (@NonNull final StringBuilder aSB, final int cp)
  {
    aSB.append ("&#").append (cp).append (';');
  }

  /**
   * The common per-character tail of text and attribute escaping: characters
   * outside the encoding, XML 1.1 control characters and (html/xhtml) C1
   * controls become character references.
   */
  private void _appendChar (@NonNull final StringBuilder aSB, final int cp)
  {
    if (m_bXml11 && (cp >= 0x1 && cp <= 0x1F && cp != 0x9 && cp != 0xA && cp != 0xD || cp >= 0x7F && cp <= 0x9F) ||
        (m_bHtml || m_bXhtml) && cp >= 0x80 && cp <= 0x9F ||
        !m_aCharset.canEncode (cp))
      _charRef (aSB, cp);
    else
      if (m_bHtml && cp == 0xA0)
        aSB.append ("&nbsp;");
      else
        aSB.appendCodePoint (cp);
  }

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

  private void _escapeRaw (@NonNull final StringBuilder aSB, @NonNull final String s)
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
        aSB.append ('%').append (String.format (Locale.ROOT, "%02X", Integer.valueOf (n)));
    }
    return aSB.toString ();
  }

  private boolean _isHtmlElement (@Nullable final XdmNode aNode)
  {
    if (aNode == null || aNode.getKind () != ENodeKind.ELEMENT)
      return false;
    final String sNS = aNode.getName ().namespaceUri ();
    if (m_bHtml)
      return sNS.isEmpty () || m_bHtml5 && NS_XHTML.equals (sNS);
    if (m_bXhtml)
      return NS_XHTML.equals (sNS);
    return false;
  }

  private boolean _isInline (@Nullable final XdmNode aNode)
  {
    return _isHtmlElement (aNode) && INLINE_ELEMENTS.contains (aNode.getName ().localName ().toLowerCase (Locale.ROOT));
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

  private boolean _mayIndentIn (@NonNull final XdmNode aElement)
  {
    if (_isHtmlElement (aElement) &&
        FORMATTED_ELEMENTS.contains (aElement.getName ().localName ().toLowerCase (Locale.ROOT)))
      return false;
    for (int i = 0; i < aElement.getChildCount (); ++i)
    {
      final XdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.getKind () == ENodeKind.TEXT && !_isWhitespace (aChild.getValue ()))
        return false;
    }
    return true;
  }

  private static boolean _hasElementChildren (@NonNull final XdmNode aElement)
  {
    for (int i = 0; i < aElement.getChildCount (); ++i)
      if (aElement.getChildAtIndex (i).getKind () == ENodeKind.ELEMENT)
        return true;
    return false;
  }

  @Nullable
  private static XdmNode _lastChild (@NonNull final XdmNode aElement)
  {
    final int n = aElement.getChildCount ();
    return n == 0 ? null : aElement.getChildAtIndex (n - 1);
  }

  private void _newline (final int nDepth)
  {
    m_aSB.append ('\n');
    for (int i = 0; i < nDepth; ++i)
      m_aSB.append ("  ");
  }

  private void _bind (@NonNull final String sPrefix, @NonNull final String sUri, @NonNull final StringBuilder aDecls)
  {
    m_aUndo.add (new String [] { sPrefix, m_aScope.get (sPrefix) });
    m_aScope.put (sPrefix, sUri);
    aDecls.append (sPrefix.isEmpty () ? " xmlns" : " xmlns:" + sPrefix).append ("=\"");
    _escapeAttr (aDecls, sUri);
    aDecls.append ('"');
  }

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

  private boolean _isBound (@NonNull final String sPrefix, @NonNull final String sUri)
  {
    final String sCur = m_aScope.get (sPrefix);
    return sCur == null ? sUri.isEmpty () : sCur.equals (sUri);
  }

  /** @return A prefix for a namespaced name, declaring it when needed. */
  @NonNull
  private String _getPrefix (@NonNull final XdmQName aName, final boolean bAttribute, @NonNull final StringBuilder aDecls)
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
    if (m_aScope.containsKey (sPrefix) && aDecls.indexOf (sPrefix.isEmpty () ? " xmlns=" : " xmlns:" + sPrefix + "=") >= 0)
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

  private static boolean _isContentTypeMeta (@NonNull final XdmNode aNode)
  {
    if (aNode.getKind () != ENodeKind.ELEMENT || !"meta".equalsIgnoreCase (aNode.getName ().localName ()))
      return false;
    for (final XdmNode aAttr : aNode.getAllAttributes ())
      if (!aAttr.getName ().hasNamespace () &&
          "http-equiv".equalsIgnoreCase (aAttr.getName ().localName ()) &&
          "content-type".equalsIgnoreCase (aAttr.getValue ().trim ()))
        return true;
    return false;
  }

  @NonNull
  private String _getContentTypeValue ()
  {
    final String sMediaType = m_aParams.getMediaType () != null ? m_aParams.getMediaType () : "text/html";
    return sMediaType + "; charset=" + m_aParams.getEncoding ();
  }

  private boolean _isHead (@Nullable final XdmNode aNode)
  {
    return aNode != null &&
           aNode.getKind () == ENodeKind.ELEMENT &&
           "head".equalsIgnoreCase (aNode.getName ().localName ()) &&
           (m_bHtml && !aNode.getName ().hasNamespace () || NS_XHTML.equals (aNode.getName ().namespaceUri ()));
  }

  /**
   * Write a start tag up to (not including) the closing '&gt;'.
   *
   * @return The qualified name used.
   */
  @NonNull
  private String _writeStartTag (@NonNull final XdmNode aElement, final boolean bTop)
  {
    final XdmQName aName = aElement.getName ();
    final boolean bNormalisePrefixes = m_bHtml5 && (m_bHtml || m_bXhtml);
    final StringBuilder aDecls = new StringBuilder ();
    boolean bUndeclaresDefault = false;

    final ICommonsOrderedMap <String, String> aNamespaces = bTop ? aElement.getInScopeNamespaces ()
                                                                 : aElement.getDeclaredNamespaces ();
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
    final String sLocalLC = aName.localName ().toLowerCase (Locale.ROOT);
    for (final XdmNode aAttr : aElement.getAllAttributes ())
    {
      final XdmQName aAttrName = aAttr.getName ();
      if (bFixContentType && !aAttrName.hasNamespace () && "content".equalsIgnoreCase (aAttrName.localName ()))
        continue;
      String sAttrQName = aAttrName.localName ();
      if (aAttrName.hasNamespace ())
        sAttrQName = _getPrefix (aAttrName, true, aDecls) + ":" + aAttrName.localName ();
      final String sValue = aAttr.getValue ();
      final String sAttrLC = aAttrName.localName ().toLowerCase (Locale.ROOT);
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
      final Set <String> aUriAttrs = URI_ATTRS.get (sLocalLC);
      if ((m_bHtml || m_bXhtml) &&
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

  @NonNull
  private String _getContentTypeMeta ()
  {
    final StringBuilder aSB = new StringBuilder ("<meta http-equiv=\"Content-Type\" content=\"");
    m_aCharset.mapAndEscape (aSB, _getContentTypeValue (), this::_escapeAttr);
    aSB.append (m_bXhtml ? "\" />" : "\">");
    return aSB.toString ();
  }

  private boolean _headNeedsMeta (@NonNull final XdmNode aElement)
  {
    if ((m_bHtml || m_bXhtml) && m_aParams.isIncludeContentType () && _isHead (aElement))
    {
      for (final XdmNode aChild : aElement.getAllChildren ())
        if (_isContentTypeMeta (aChild))
          return false;
      return true;
    }
    return false;
  }

  @NonNull
  private static XdmQName _plainName (@NonNull final XdmQName aName)
  {
    return aName.hasPrefix () ? new XdmQName (aName.namespaceUri (), aName.localName ()) : aName;
  }

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

  private void _writeText (@NonNull final String sText, @Nullable final XdmNode aParent)
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

  private void _writeLeaf (@NonNull final XdmNode aNode, @Nullable final XdmNode aParent)
  {
    switch (aNode.getKind ())
    {
      case TEXT:
        if (aNode.isDisableOutputEscaping ())
          m_aSB.append (aNode.getValue ());
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

  private boolean _isSuppressed (@NonNull final XdmNode aElement)
  {
    return m_aParams.isSuppressIndentation (_plainName (aElement.getName ()));
  }

  /**
   * Open an element: write its start tag and either finish it (no children)
   * or return a frame for its children.
   */
  @Nullable
  private Frame _openElement (@NonNull final XdmNode aElement,
                              final int nDepth,
                              @Nullable final XdmNode aPrev,
                              final boolean bParentMayIndent,
                              final boolean bTop)
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
    if (aElement.getChildCount () == 0)
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
          if (m_bXhtml && (_isHtmlElement (aElement) || m_bHtml5 && !aElement.getName ().hasNamespace ()))
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

  private void _closeElement (@NonNull final Frame aFrame)
  {
    if (aFrame.m_bIndentClose)
      _newline (aFrame.m_nDepth);
    m_aSB.append ("</").append (aFrame.m_sQName).append ('>');
    _unwind (aFrame.m_nUndoMark);
    if (aFrame.m_bSetsSuppress)
      m_bSuppress = false;
  }

  private void _writeElement (@NonNull final XdmNode aRoot, @Nullable final XdmNode aPrev)
  {
    final ArrayDeque <Frame> aStack = new ArrayDeque <> ();
    final Frame aRootFrame = _openElement (aRoot, 0, aPrev, true, true);
    if (aRootFrame != null)
      aStack.push (aRootFrame);
    while (!aStack.isEmpty ())
    {
      final Frame aFrame = aStack.peek ();
      final XdmNode aElement = aFrame.m_aElement;
      if (aFrame.m_nNext >= aElement.getChildCount ())
      {
        aStack.pop ();
        _closeElement (aFrame);
        continue;
      }
      final int nIndex = aFrame.m_nNext++;
      final XdmNode aChild = aElement.getChildAtIndex (nIndex);
      if (aChild.getKind () == ENodeKind.ELEMENT)
      {
        final Frame aChildFrame = _openElement (aChild,
                                                aFrame.m_nDepth + 1,
                                                nIndex > 0 ? aElement.getChildAtIndex (nIndex - 1) : null,
                                                aFrame.m_bMayIndentChildren,
                                                false);
        if (aChildFrame != null)
          aStack.push (aChildFrame);
      }
      else
        _writeLeaf (aChild, aElement);
    }
  }

  @NonNull
  private static String _quote (@NonNull final String s)
  {
    return s.indexOf ('"') >= 0 ? "'" + s + "'" : "\"" + s + "\"";
  }

  private void _writeDoctype (@NonNull final XdmNode aFirstElement)
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
      if (m_bXhtml && m_bHtml5 && "html".equalsIgnoreCase (sLocal) && NS_XHTML.equals (aFirstElement.getName ().namespaceUri ()))
        m_aSB.append ("<!DOCTYPE ").append (sLocal).append ('>');
  }

  /**
   * Write the normalised sequence.
   *
   * @param aItems
   *        Nodes (element, text, comment, processing instruction) and
   *        strings (text).
   */
  void write (@NonNull final List <Object> aItems)
  {
    if (!m_bHtml && !m_aParams.isOmitXmlDeclaration ())
    {
      m_aSB.append ("<?xml version=\"").append (m_bXml11 ? "1.1" : "1.0").append ("\" encoding=\"");
      m_aSB.append (m_aParams.getEncoding ()).append ('"');
      if (!SerializationParams.STANDALONE_OMIT.equals (m_aParams.getStandalone ()))
        m_aSB.append (" standalone=\"").append (m_aParams.getStandalone ()).append ('"');
      m_aSB.append ("?>");
    }
    boolean bDoctypeDone = false;
    Object aPrev = null;
    for (final Object aItem : aItems)
    {
      if (aItem instanceof final String sText)
        _writeText (sText, null);
      else
      {
        final XdmNode aNode = (XdmNode) aItem;
        if (aNode.getKind () == ENodeKind.ELEMENT)
        {
          if (!bDoctypeDone)
          {
            _writeDoctype (aNode);
            bDoctypeDone = true;
          }
          _writeElement (aNode, aPrev instanceof final XdmNode aPrevNode ? aPrevNode : null);
        }
        else
          _writeLeaf (aNode, null);
      }
      aPrev = aItem;
    }
  }
}
