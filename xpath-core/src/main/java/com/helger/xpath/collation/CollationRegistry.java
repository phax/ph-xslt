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
package com.helger.xpath.collation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;

/**
 * Resolves collation URIs: the Unicode codepoint collation, the HTML ASCII
 * case-insensitive collation, the case-blind collation of the QT3 test suite
 * catalogue and the UCA collation family with its parameters (F&amp;O 3.1
 * section 5.3).
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class CollationRegistry
{
  public static final String CODEPOINT_URI = "http://www.w3.org/2005/xpath-functions/collation/codepoint";
  public static final String HTML_ASCII_CASE_INSENSITIVE_URI = "http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive";
  public static final String UCA_URI = UcaCollation.UCA_URI;
  /** The case-blind collation the QT3 catalogue declares */
  public static final String QT3_CASE_BLIND_URI = "http://www.w3.org/2010/09/qt-fots-catalog/collation/caseblind";

  /** Maximum number of UCA collations kept */
  private static final int CACHE_SIZE = 256;

  /** The Unicode codepoint collation. */
  public static final ICollation CODEPOINT = new ICollation ()
  {
    public String getUri ()
    {
      return CODEPOINT_URI;
    }

    public int compare (final String sLeft, final String sRight)
    {
      // String.compareTo compares UTF-16 units; codepoint order differs for
      // supplementary characters vs. U+E000..U+FFFF
      final int nLeftLen = sLeft.length ();
      final int nRightLen = sRight.length ();
      int i = 0;
      int j = 0;
      while (i < nLeftLen && j < nRightLen)
      {
        final int cLeft = sLeft.codePointAt (i);
        final int cRight = sRight.codePointAt (j);
        if (cLeft != cRight)
          return cLeft < cRight ? -1 : 1;
        i += Character.charCount (cLeft);
        j += Character.charCount (cRight);
      }
      return Integer.compare (nLeftLen - i, nRightLen - j);
    }

    public Object getCollationKey (final String sValue)
    {
      return sValue;
    }

    public int [] findSubstring (final String sValue, final String sSearch)
    {
      final int n = sValue.indexOf (sSearch);
      return n < 0 ? null : new int [] { n, n + sSearch.length () };
    }

    public boolean startsWith (final String sValue, final String sPrefix)
    {
      return sValue.startsWith (sPrefix);
    }

    public boolean endsWith (final String sValue, final String sSuffix)
    {
      return sValue.endsWith (sSuffix);
    }
  };

  /**
   * A collation that compares by code point after a case mapping that keeps
   * the string length (so offsets into the mapped string are offsets into the
   * original).
   */
  private abstract static class AbstractFoldingCollation implements ICollation
  {
    private final String m_sUri;

    AbstractFoldingCollation (@NonNull final String sUri)
    {
      m_sUri = sUri;
    }

    @NonNull
    protected abstract String fold (@NonNull String s);

    @NonNull
    public final String getUri ()
    {
      return m_sUri;
    }

    public final int compare (@NonNull final String sLeft, @NonNull final String sRight)
    {
      return CODEPOINT.compare (fold (sLeft), fold (sRight));
    }

    @NonNull
    public final Object getCollationKey (@NonNull final String sValue)
    {
      return fold (sValue);
    }

    @NonNull
    public final byte [] getCollationKeyBytes (@NonNull final String sValue)
    {
      return CollationSupport.getCodepointBytes (fold (sValue));
    }
  }

  /** ASCII letters fold, nothing else (HTML ASCII case-insensitive). */
  private static final class AsciiCaseInsensitiveCollation extends AbstractFoldingCollation
  {
    AsciiCaseInsensitiveCollation (@NonNull final String sUri)
    {
      super (sUri);
    }

    @NonNull
    static String asciiFold (@NonNull final String s)
    {
      char [] aChars = null;
      for (int i = 0; i < s.length (); ++i)
      {
        final char c = s.charAt (i);
        if (c >= 'A' && c <= 'Z')
        {
          if (aChars == null)
            aChars = s.toCharArray ();
          aChars[i] = (char) (c + ('a' - 'A'));
        }
      }
      return aChars == null ? s : new String (aChars);
    }

    @Override
    @NonNull
    protected String fold (@NonNull final String s)
    {
      return asciiFold (s);
    }

    @Nullable
    public int [] findSubstring (@NonNull final String sValue, @NonNull final String sSearch)
    {
      return CODEPOINT.findSubstring (asciiFold (sValue), asciiFold (sSearch));
    }

    public boolean startsWith (@NonNull final String sValue, @NonNull final String sPrefix)
    {
      return asciiFold (sValue).startsWith (asciiFold (sPrefix));
    }

    public boolean endsWith (@NonNull final String sValue, @NonNull final String sSuffix)
    {
      return asciiFold (sValue).endsWith (asciiFold (sSuffix));
    }
  }

  /** Full lower-casing (the QT3 catalogue's case-blind collation). */
  private static final class CaseBlindCollation extends AbstractFoldingCollation
  {
    CaseBlindCollation (@NonNull final String sUri)
    {
      super (sUri);
    }

    @Override
    @NonNull
    protected String fold (@NonNull final String s)
    {
      return s.toLowerCase (Locale.ROOT);
    }
  }

  /** The HTML ASCII case-insensitive collation. */
  public static final ICollation HTML_ASCII_CASE_INSENSITIVE = new AsciiCaseInsensitiveCollation (HTML_ASCII_CASE_INSENSITIVE_URI);

  private static final ICollation QT3_CASE_BLIND = new CaseBlindCollation (QT3_CASE_BLIND_URI);

  private static final ICommonsMap <String, ICollation> UCA_CACHE = new CommonsHashMap <> ();

  @PresentForCodeCoverage
  private static final CollationRegistry INSTANCE = new CollationRegistry ();

  private CollationRegistry ()
  {}

  /**
   * @param sUri
   *        Absolute collation URI.
   * @return <code>null</code> if the collation is not supported (callers raise
   *         FOCH0002), including a UCA collation with a parameter that cannot
   *         be honoured under fallback=no.
   */
  @Nullable
  public static ICollation getCollation (@NonNull final String sUri)
  {
    try
    {
      return getCollationOrThrow (sUri);
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  /**
   * @param sUri
   *        Absolute collation URI.
   * @return The collation, never <code>null</code>.
   * @throws XPathException
   *         FOCH0002 if the collation is not supported
   */
  @NonNull
  public static ICollation getCollationOrThrow (@NonNull final String sUri)
  {
    switch (sUri)
    {
      case CODEPOINT_URI:
        return CODEPOINT;
      case HTML_ASCII_CASE_INSENSITIVE_URI:
        return HTML_ASCII_CASE_INSENSITIVE;
      case QT3_CASE_BLIND_URI:
        return QT3_CASE_BLIND;
      default:
        break;
    }
    if (sUri.equals (UCA_URI) || sUri.startsWith (UCA_URI + "?"))
    {
      synchronized (UCA_CACHE)
      {
        final ICollation ret = UCA_CACHE.get (sUri);
        if (ret != null)
          return ret;
      }
      final ICollation ret = UcaCollation.create (sUri);
      synchronized (UCA_CACHE)
      {
        if (UCA_CACHE.size () >= CACHE_SIZE)
          UCA_CACHE.clear ();
        UCA_CACHE.put (sUri, ret);
      }
      return ret;
    }
    throw XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "'");
  }

  /**
   * Resolve a possibly relative collation URI against a base URI.
   *
   * @param sUri
   *        The collation URI as written.
   * @param sBaseUri
   *        The static base URI. May be <code>null</code>.
   * @return The absolute URI, or the URI unchanged if it cannot be resolved.
   */
  @NonNull
  public static String resolveUri (@NonNull final String sUri, @Nullable final String sBaseUri)
  {
    if (sBaseUri == null || sBaseUri.isEmpty ())
      return sUri;
    try
    {
      final URI aUri = new URI (sUri);
      if (aUri.isAbsolute ())
        return sUri;
      return new URI (sBaseUri).resolve (aUri).toString ();
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      return sUri;
    }
  }

  /**
   * Resolve a collation URI as written in an XPath function call: relative
   * URIs are resolved against the static base URI.
   *
   * @param sUri
   *        The collation URI as written.
   * @param sBaseUri
   *        The static base URI. May be <code>null</code>.
   * @return The collation, never <code>null</code>.
   * @throws XPathException
   *         FOCH0002 if the collation is not supported
   */
  @NonNull
  public static ICollation resolveCollation (@NonNull final String sUri, @Nullable final String sBaseUri)
  {
    if (CODEPOINT_URI.equals (sUri))
      return CODEPOINT;
    return getCollationOrThrow (resolveUri (sUri, sBaseUri));
  }
}
