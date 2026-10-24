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
package com.helger.xpath.fn;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: fn:resolve-uri, encode-for-uri, iri-to-uri,
 * escape-html-uri. URI references are resolved per RFC 3986 section 5.2 on the
 * raw strings: no case normalisation and no percent-encoding of spaces or
 * non-ASCII characters (the LEIRI characters F&amp;O allows).
 *
 * @author Philip Helger
 */
@Immutable
public final class FnUri
{
  /**
   * A URI reference split into its five RFC 3986 components (appendix B). A
   * <code>null</code> component is absent, which differs from an empty one.
   */
  private static final class UriRef
  {
    private String m_sScheme;
    private String m_sAuthority;
    private String m_sPath = "";
    private String m_sQuery;
    private String m_sFragment;

    @NonNull
    String getAsString ()
    {
      final StringBuilder aSB = new StringBuilder ();
      if (m_sScheme != null)
        aSB.append (m_sScheme).append (':');
      if (m_sAuthority != null)
        aSB.append ("//").append (m_sAuthority);
      aSB.append (m_sPath);
      if (m_sQuery != null)
        aSB.append ('?').append (m_sQuery);
      if (m_sFragment != null)
        aSB.append ('#').append (m_sFragment);
      return aSB.toString ();
    }
  }

  private static final char [] HEX = "0123456789ABCDEF".toCharArray ();

  @PresentForCodeCoverage
  private static final FnUri INSTANCE = new FnUri ();

  private FnUri ()
  {}

  private static int _indexOfAny (@NonNull final String s, @NonNull final String sChars, final int nFrom)
  {
    for (int i = nFrom; i < s.length (); ++i)
      if (sChars.indexOf (s.charAt (i)) >= 0)
        return i;
    return -1;
  }

  /**
   * Split per the RFC 3986 appendix B regular expression, purely lexically.
   */
  @NonNull
  private static UriRef _split (@NonNull final String s)
  {
    final UriRef ret = new UriRef ();
    String sRest = s;
    final int nSchemeEnd = _indexOfAny (sRest, ":/?#", 0);
    if (nSchemeEnd > 0 && sRest.charAt (nSchemeEnd) == ':')
    {
      ret.m_sScheme = sRest.substring (0, nSchemeEnd);
      sRest = sRest.substring (nSchemeEnd + 1);
    }
    if (sRest.startsWith ("//"))
    {
      sRest = sRest.substring (2);
      int nEnd = _indexOfAny (sRest, "/?#", 0);
      if (nEnd < 0)
        nEnd = sRest.length ();
      ret.m_sAuthority = sRest.substring (0, nEnd);
      sRest = sRest.substring (nEnd);
    }
    final int nHash = sRest.indexOf ('#');
    if (nHash >= 0)
    {
      ret.m_sFragment = sRest.substring (nHash + 1);
      sRest = sRest.substring (0, nHash);
    }
    final int nQuery = sRest.indexOf ('?');
    if (nQuery >= 0)
    {
      ret.m_sQuery = sRest.substring (nQuery + 1);
      sRest = sRest.substring (0, nQuery);
    }
    ret.m_sPath = sRest;
    return ret;
  }

  private static boolean _isAsciiLetter (final char c)
  {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean _isHexDigit (final char c)
  {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean _isSchemeValid (@NonNull final String s)
  {
    if (s.isEmpty () || !_isAsciiLetter (s.charAt (0)))
      return false;
    for (int i = 1; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (!_isAsciiLetter (c) && !(c >= '0' && c <= '9') && c != '+' && c != '-' && c != '.')
        return false;
    }
    return true;
  }

  private static boolean _hasValidEscapes (@Nullable final String s)
  {
    if (s != null)
      for (int i = 0; i < s.length (); ++i)
        if (s.charAt (i) == '%')
          if (i + 2 >= s.length () || !_isHexDigit (s.charAt (i + 1)) || !_isHexDigit (s.charAt (i + 2)))
            return false;
    return true;
  }

  /**
   * The RFC 3986 checks that make a reference invalid, without rejecting the
   * LEIRI characters (spaces, non-ASCII) an implementation may accept.
   */
  private static boolean _isValid (@NonNull final UriRef aRef)
  {
    if (aRef.m_sScheme != null)
    {
      if (!_isSchemeValid (aRef.m_sScheme))
        return false;
    }
    else
    {
      // No colon in the first segment of a relative path
      final int nSlash = aRef.m_sPath.indexOf ('/');
      final String sFirst = nSlash < 0 ? aRef.m_sPath : aRef.m_sPath.substring (0, nSlash);
      if (sFirst.indexOf (':') >= 0)
        return false;
    }
    // "#" is not in the repertoire of a fragment
    if (aRef.m_sFragment != null && aRef.m_sFragment.indexOf ('#') >= 0)
      return false;
    return _hasValidEscapes (aRef.m_sAuthority) &&
           _hasValidEscapes (aRef.m_sPath) &&
           _hasValidEscapes (aRef.m_sQuery) &&
           _hasValidEscapes (aRef.m_sFragment);
  }

  /**
   * RFC 3986 section 5.2.4.
   */
  @NonNull
  private static String _removeDotSegments (@NonNull final String sPath)
  {
    final ICommonsList <String> aOut = new CommonsArrayList <> ();
    String sIn = sPath;
    while (!sIn.isEmpty ())
    {
      if (sIn.startsWith ("../"))
        sIn = sIn.substring (3);
      else
        if (sIn.startsWith ("./"))
          sIn = sIn.substring (2);
        else
          if (sIn.startsWith ("/./"))
            sIn = sIn.substring (2);
          else
            if (sIn.equals ("/."))
              sIn = "/";
            else
              if (sIn.startsWith ("/../"))
              {
                sIn = sIn.substring (3);
                if (aOut.isNotEmpty ())
                  aOut.removeLast ();
              }
              else
                if (sIn.equals ("/.."))
                {
                  sIn = "/";
                  if (aOut.isNotEmpty ())
                    aOut.removeLast ();
                }
                else
                  if (sIn.equals (".") || sIn.equals (".."))
                    sIn = "";
                  else
                  {
                    // Move the first segment including its leading "/"
                    final int nStart = sIn.charAt (0) == '/' ? 1 : 0;
                    int nEnd = sIn.indexOf ('/', nStart);
                    if (nEnd < 0)
                      nEnd = sIn.length ();
                    aOut.add (sIn.substring (0, nEnd));
                    sIn = sIn.substring (nEnd);
                  }
    }
    return String.join ("", aOut);
  }

  /**
   * @param s
   *        A string.
   * @return <code>true</code> if it is a syntactically valid URI reference in
   *         the lenient sense F&amp;O allows (see the class comment).
   */
  public static boolean isValidUriReference (@NonNull final String s)
  {
    return _isValid (_split (s));
  }

  /**
   * @param s
   *        A string.
   * @return <code>true</code> if it is a valid URI reference with a scheme.
   */
  public static boolean isAbsoluteUri (@NonNull final String s)
  {
    final UriRef aRef = _split (s);
    return aRef.m_sScheme != null && _isValid (aRef);
  }

  /**
   * @param s
   *        A URI reference.
   * @return <code>true</code> if it contains a fragment identifier.
   */
  public static boolean hasFragment (@NonNull final String s)
  {
    return _split (s).m_sFragment != null;
  }

  /**
   * Resolve a URI reference against a base URI per RFC 3986 section 5.2
   * (strict), on the raw strings.
   *
   * @param sRelative
   *        The reference; returned with dot segments removed if it is absolute.
   * @param sBase
   *        The base URI; must be absolute and without fragment.
   * @return The resolved URI.
   * @throws XPathException
   *         FORG0002 if the reference or the base is invalid.
   */
  @NonNull
  public static String resolveUri (@NonNull final String sRelative, @NonNull final String sBase)
  {
    final UriRef aRel = _split (sRelative);
    if (!_isValid (aRel))
      throw XPathException.of ("FORG0002", "Invalid relative URI reference '" + sRelative + "'");
    if (aRel.m_sScheme != null)
    {
      aRel.m_sPath = _removeDotSegments (aRel.m_sPath);
      return aRel.getAsString ();
    }
    final UriRef aBase = _split (sBase);
    if (aBase.m_sScheme == null || aBase.m_sFragment != null || !_isValid (aBase))
      throw XPathException.of ("FORG0002", "Invalid base URI '" + sBase + "'");
    final UriRef aTarget = new UriRef ();
    if (aRel.m_sAuthority != null)
    {
      aTarget.m_sAuthority = aRel.m_sAuthority;
      aTarget.m_sPath = _removeDotSegments (aRel.m_sPath);
      aTarget.m_sQuery = aRel.m_sQuery;
    }
    else
    {
      if (aRel.m_sPath.isEmpty ())
      {
        aTarget.m_sPath = aBase.m_sPath;
        aTarget.m_sQuery = aRel.m_sQuery != null ? aRel.m_sQuery : aBase.m_sQuery;
      }
      else
      {
        if (aRel.m_sPath.startsWith ("/"))
          aTarget.m_sPath = _removeDotSegments (aRel.m_sPath);
        else
        {
          // Merge (section 5.2.3)
          final String sMerged;
          if (aBase.m_sAuthority != null && aBase.m_sPath.isEmpty ())
            sMerged = "/" + aRel.m_sPath;
          else
          {
            final int nSlash = aBase.m_sPath.lastIndexOf ('/');
            sMerged = nSlash >= 0 ? aBase.m_sPath.substring (0, nSlash + 1) + aRel.m_sPath : aRel.m_sPath;
          }
          aTarget.m_sPath = _removeDotSegments (sMerged);
        }
        aTarget.m_sQuery = aRel.m_sQuery;
      }
      aTarget.m_sAuthority = aBase.m_sAuthority;
    }
    aTarget.m_sScheme = aBase.m_sScheme;
    aTarget.m_sFragment = aRel.m_sFragment;
    return aTarget.getAsString ();
  }

  private static void _appendEscaped (@NonNull final StringBuilder aSB, final int nCodePoint)
  {
    final byte [] aBytes = new String (Character.toChars (nCodePoint)).getBytes (StandardCharsets.UTF_8);
    for (final byte b : aBytes)
      aSB.append ('%').append (HEX[(b >> 4) & 0xf]).append (HEX[b & 0xf]);
  }

  private static boolean _isUnreserved (final int c)
  {
    return (c >= 'A' && c <= 'Z') ||
           (c >= 'a' && c <= 'z') ||
           (c >= '0' && c <= '9') ||
           c == '-' ||
           c == '_' ||
           c == '.' ||
           c == '~';
  }

  /**
   * @param s
   *        Input.
   * @return The result of <code>fn:encode-for-uri</code>: everything but the
   *         RFC 3986 unreserved characters percent-encoded as UTF-8.
   */
  @NonNull
  public static String encodeForUri (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length ());
    s.codePoints ().forEach (c -> {
      if (_isUnreserved (c))
        aSB.append ((char) c);
      else
        _appendEscaped (aSB, c);
    });
    return aSB.toString ();
  }

  /**
   * @param s
   *        Input.
   * @return The result of <code>fn:iri-to-uri</code>: non-ASCII characters,
   *         controls, space and the characters <code>&lt; &gt; " { } | \ ^
   *         `</code> percent-encoded as UTF-8.
   */
  @NonNull
  public static String iriToUri (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length ());
    s.codePoints ().forEach (c -> {
      if (c <= 0x20 || c >= 0x7f || "<>\"{}|\\^`".indexOf (c) >= 0)
        _appendEscaped (aSB, c);
      else
        aSB.append ((char) c);
    });
    return aSB.toString ();
  }

  /**
   * @param s
   *        Input.
   * @return The result of <code>fn:escape-html-uri</code>: every character
   *         outside the printable ASCII range 32 to 126 percent-encoded as
   *         UTF-8.
   */
  @NonNull
  public static String escapeHtmlUri (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length ());
    s.codePoints ().forEach (c -> {
      if (c >= 32 && c <= 126)
        aSB.append ((char) c);
      else
        _appendEscaped (aSB, c);
    });
    return aSB.toString ();
  }

  @NonNull
  private static String _getOptionalString (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? "" : ((XdmAtomic) aSeq.get (0)).getString ();
  }

  @NonNull
  private static XdmSequence _resolveUri (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final String sRelative = _getOptionalString (aArgs.get (0));
    final String sBase;
    if (aArgs.size () > 1)
      sBase = ((XdmAtomic) aArgs.get (1).get (0)).getString ();
    else
      sBase = aCtx.getStaticContext ().getBaseUri ();
    if (isAbsoluteUri (sRelative))
      return XdmSequence.of (XdmAtomic.anyUri (resolveUri (sRelative, sRelative)));
    if (sBase == null)
    {
      if (!isValidUriReference (sRelative))
        throw XPathException.of ("FORG0002", "Invalid relative URI reference '" + sRelative + "'");
      throw XPathException.of ("FONS0005", "The static base URI is absent");
    }
    return XdmSequence.of (XdmAtomic.anyUri (resolveUri (sRelative, sBase)));
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "resolve-uri", "(xs:string?) as xs:anyURI?", FnUri::_resolveUri);
    aLib.register (sNS, "resolve-uri", "(xs:string?, xs:string) as xs:anyURI?", FnUri::_resolveUri);
    aLib.register (sNS,
                   "encode-for-uri",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.string (encodeForUri (_getOptionalString (aArgs.get (0))))));
    aLib.register (sNS,
                   "iri-to-uri",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.string (iriToUri (_getOptionalString (aArgs.get (0))))));
    aLib.register (sNS,
                   "escape-html-uri",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.string (escapeHtmlUri (_getOptionalString (aArgs.get (0))))));
  }
}
