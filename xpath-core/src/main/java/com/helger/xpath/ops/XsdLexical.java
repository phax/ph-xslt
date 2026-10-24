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
package com.helger.xpath.ops;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Lexical helpers of XML Schema 1.1 Part 2 (datatypes): the whitespace facet,
 * the lexical spaces of the numeric types, the XML name productions (XML 1.0
 * fifth edition), xs:language, and the binary encodings.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdLexical
{
  private static final char [] HEX_UPPER = "0123456789ABCDEF".toCharArray ();

  @PresentForCodeCoverage
  private static final XsdLexical INSTANCE = new XsdLexical ();

  private XsdLexical ()
  {}

  /**
   * @param c
   *        Character to check.
   * @return <code>true</code> for the four XML whitespace characters (space,
   *         tab, CR, LF). No other character counts as whitespace in XML
   *         Schema.
   */
  public static boolean isXmlWhitespace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /**
   * The whitespace facet value "replace": tab, CR and LF become spaces.
   *
   * @param s
   *        Source string.
   * @return The replaced string.
   */
  @NonNull
  public static String replaceWhitespace (@NonNull final String s)
  {
    int i = 0;
    final int nLen = s.length ();
    while (i < nLen && s.charAt (i) != '\t' && s.charAt (i) != '\n' && s.charAt (i) != '\r')
      i++;
    if (i == nLen)
      return s;
    final char [] aChars = s.toCharArray ();
    for (; i < nLen; ++i)
    {
      final char c = aChars[i];
      if (c == '\t' || c == '\n' || c == '\r')
        aChars[i] = ' ';
    }
    return new String (aChars);
  }

  /**
   * The whitespace facet value "collapse": replace, then strip leading and
   * trailing spaces and squeeze runs of spaces into one.
   *
   * @param s
   *        Source string.
   * @return The collapsed string.
   */
  @NonNull
  public static String collapseWhitespace (@NonNull final String s)
  {
    final int nLen = s.length ();
    // Fast path: nothing to do
    boolean bClean = nLen == 0 || !isXmlWhitespace (s.charAt (0)) && !isXmlWhitespace (s.charAt (nLen - 1));
    if (bClean)
      for (int i = 0; i < nLen; ++i)
      {
        final char c = s.charAt (i);
        if (c == '\t' || c == '\n' || c == '\r' || c == ' ' && i + 1 < nLen && s.charAt (i + 1) == ' ')
        {
          bClean = false;
          break;
        }
      }
    if (bClean)
      return s;
    final StringBuilder aSB = new StringBuilder (nLen);
    boolean bPendingSpace = false;
    for (int i = 0; i < nLen; ++i)
    {
      final char c = s.charAt (i);
      if (isXmlWhitespace (c))
        bPendingSpace = aSB.length () > 0;
      else
      {
        if (bPendingSpace)
        {
          aSB.append (' ');
          bPendingSpace = false;
        }
        aSB.append (c);
      }
    }
    return aSB.toString ();
  }

  /**
   * Strip leading and trailing XML whitespace only (unlike
   * {@link String#trim()}, which strips all control characters).
   *
   * @param s
   *        Source string.
   * @return The trimmed string.
   */
  @NonNull
  public static String trimWhitespace (@NonNull final String s)
  {
    int nStart = 0;
    int nEnd = s.length ();
    while (nStart < nEnd && isXmlWhitespace (s.charAt (nStart)))
      nStart++;
    while (nEnd > nStart && isXmlWhitespace (s.charAt (nEnd - 1)))
      nEnd--;
    return nStart == 0 && nEnd == s.length () ? s : s.substring (nStart, nEnd);
  }

  private static boolean _isDigit (final char c)
  {
    return c >= '0' && c <= '9';
  }

  /**
   * @param s
   *        Collapsed lexical form.
   * @return <code>true</code> if it matches <code>[\-+]?[0-9]+</code>.
   */
  public static boolean isIntegerLexical (@NonNull final String s)
  {
    final int nLen = s.length ();
    int i = 0;
    if (i < nLen && (s.charAt (i) == '+' || s.charAt (i) == '-'))
      i++;
    if (i == nLen)
      return false;
    for (; i < nLen; ++i)
      if (!_isDigit (s.charAt (i)))
        return false;
    return true;
  }

  /**
   * @param s
   *        Collapsed lexical form.
   * @return <code>true</code> if it matches
   *         <code>(\+|-)?([0-9]+(\.[0-9]*)?|\.[0-9]+)</code>.
   */
  public static boolean isDecimalLexical (@NonNull final String s)
  {
    return _scanDecimal (s, 0) == s.length ();
  }

  /**
   * @return the index after a decimal lexical starting at nStart, or -1.
   */
  private static int _scanDecimal (@NonNull final String s, final int nStart)
  {
    final int nLen = s.length ();
    int i = nStart;
    if (i < nLen && (s.charAt (i) == '+' || s.charAt (i) == '-'))
      i++;
    int nDigits = 0;
    while (i < nLen && _isDigit (s.charAt (i)))
    {
      i++;
      nDigits++;
    }
    if (i < nLen && s.charAt (i) == '.')
    {
      i++;
      while (i < nLen && _isDigit (s.charAt (i)))
      {
        i++;
        nDigits++;
      }
    }
    return nDigits == 0 ? -1 : i;
  }

  /**
   * @param s
   *        Collapsed lexical form.
   * @return <code>true</code> if it is a valid xs:double/xs:float lexical form
   *         of XSD 1.1, including <code>INF</code>, <code>+INF</code>,
   *         <code>-INF</code> and <code>NaN</code>.
   */
  public static boolean isDoubleLexical (@NonNull final String s)
  {
    if (s.equals ("INF") || s.equals ("+INF") || s.equals ("-INF") || s.equals ("NaN"))
      return true;
    int i = _scanDecimal (s, 0);
    if (i < 0)
      return false;
    final int nLen = s.length ();
    if (i == nLen)
      return true;
    if (s.charAt (i) != 'e' && s.charAt (i) != 'E')
      return false;
    i++;
    return isIntegerLexical (s.substring (i));
  }

  private static boolean _isNameStartChar (final int c, final boolean bAllowColon)
  {
    if (c < 0x80)
      return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_' || c == ':' && bAllowColon;
    return c >= 0xC0 && c <= 0xD6 ||
           c >= 0xD8 && c <= 0xF6 ||
           c >= 0xF8 && c <= 0x2FF ||
           c >= 0x370 && c <= 0x37D ||
           c >= 0x37F && c <= 0x1FFF ||
           c >= 0x200C && c <= 0x200D ||
           c >= 0x2070 && c <= 0x218F ||
           c >= 0x2C00 && c <= 0x2FEF ||
           c >= 0x3001 && c <= 0xD7FF ||
           c >= 0xF900 && c <= 0xFDCF ||
           c >= 0xFDF0 && c <= 0xFFFD ||
           c >= 0x10000 && c <= 0xEFFFF;
  }

  private static boolean _isNameChar (final int c, final boolean bAllowColon)
  {
    if (_isNameStartChar (c, bAllowColon))
      return true;
    return c == '-' ||
           c == '.' ||
           c >= '0' && c <= '9' ||
           c == 0xB7 ||
           c >= 0x300 && c <= 0x36F ||
           c >= 0x203F && c <= 0x2040;
  }

  private static boolean _isName (@NonNull final String s, final boolean bAllowColon)
  {
    final int nLen = s.length ();
    if (nLen == 0)
      return false;
    int i = 0;
    boolean bFirst = true;
    while (i < nLen)
    {
      final int c = s.codePointAt (i);
      if (bFirst ? !_isNameStartChar (c, bAllowColon) : !_isNameChar (c, bAllowColon))
        return false;
      bFirst = false;
      i += Character.charCount (c);
    }
    return true;
  }

  /**
   * @param s
   *        String to check.
   * @return <code>true</code> if it matches the XML <code>Name</code>
   *         production.
   */
  public static boolean isName (@Nullable final String s)
  {
    return s != null && _isName (s, true);
  }

  /**
   * @param s
   *        String to check.
   * @return <code>true</code> if it matches the <code>NCName</code>
   *         production.
   */
  public static boolean isNCName (@Nullable final String s)
  {
    return s != null && _isName (s, false);
  }

  /**
   * @param s
   *        String to check.
   * @return <code>true</code> if it matches the <code>Nmtoken</code>
   *         production.
   */
  public static boolean isNmtoken (@Nullable final String s)
  {
    if (s == null || s.isEmpty ())
      return false;
    int i = 0;
    while (i < s.length ())
    {
      final int c = s.codePointAt (i);
      if (!_isNameChar (c, true))
        return false;
      i += Character.charCount (c);
    }
    return true;
  }

  /**
   * @param s
   *        String to check.
   * @return <code>true</code> if it is a lexical QName
   *         (<code>NCName (':' NCName)?</code>).
   */
  public static boolean isQNameLexical (@Nullable final String s)
  {
    if (s == null)
      return false;
    final int nColon = s.indexOf (':');
    if (nColon < 0)
      return isNCName (s);
    return isNCName (s.substring (0, nColon)) && isNCName (s.substring (nColon + 1));
  }

  /**
   * @param s
   *        String to check.
   * @return <code>true</code> if it matches the xs:language pattern
   *         <code>[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*</code>.
   */
  public static boolean isLanguage (@Nullable final String s)
  {
    if (s == null)
      return false;
    final int nLen = s.length ();
    int i = 0;
    boolean bFirstPart = true;
    while (true)
    {
      final int nPartStart = i;
      while (i < nLen)
      {
        final char c = s.charAt (i);
        final boolean bAlpha = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
        if (!bAlpha && (bFirstPart || !_isDigit (c)))
          break;
        i++;
      }
      final int nPartLen = i - nPartStart;
      if (nPartLen < 1 || nPartLen > 8)
        return false;
      if (i == nLen)
        return true;
      if (s.charAt (i) != '-')
        return false;
      i++;
      bFirstPart = false;
    }
  }

  /**
   * The additional check XPath applies to xs:anyURI values (F&amp;O 3.1 section
   * 19.2 allows rejecting what is not a valid IRI reference): a percent sign
   * must start a <code>%HH</code> escape and a URI may not start with a colon.
   *
   * @param s
   *        The collapsed value.
   * @return <code>true</code> if acceptable.
   */
  public static boolean isAnyUriLexical (@NonNull final String s)
  {
    if (s.startsWith (":"))
      return false;
    final int nLen = s.length ();
    for (int i = 0; i < nLen; ++i)
      if (s.charAt (i) == '%')
      {
        if (i + 2 >= nLen || Character.digit (s.charAt (i + 1), 16) < 0 || Character.digit (s.charAt (i + 2), 16) < 0)
          return false;
        i += 2;
      }
    return true;
  }

  private static int _hexValue (final char c)
  {
    if (c >= '0' && c <= '9')
      return c - '0';
    if (c >= 'a' && c <= 'f')
      return c - 'a' + 10;
    if (c >= 'A' && c <= 'F')
      return c - 'A' + 10;
    return -1;
  }

  /**
   * @param s
   *        Collapsed hexBinary lexical form.
   * @return The bytes or <code>null</code> if the form is invalid.
   */
  public static byte @Nullable [] decodeHex (@NonNull final String s)
  {
    final int nLen = s.length ();
    if ((nLen & 1) != 0)
      return null;
    final byte [] ret = new byte [nLen / 2];
    for (int i = 0; i < nLen; i += 2)
    {
      final int nHigh = _hexValue (s.charAt (i));
      final int nLow = _hexValue (s.charAt (i + 1));
      if (nHigh < 0 || nLow < 0)
        return null;
      ret[i / 2] = (byte) (nHigh << 4 | nLow);
    }
    return ret;
  }

  /**
   * @param aBytes
   *        Bytes to encode.
   * @return The canonical (upper case) hexBinary form.
   */
  @NonNull
  public static String encodeHex (final byte @NonNull [] aBytes)
  {
    final char [] ret = new char [aBytes.length * 2];
    for (int i = 0; i < aBytes.length; ++i)
    {
      final int n = aBytes[i] & 0xff;
      ret[i * 2] = HEX_UPPER[n >> 4];
      ret[i * 2 + 1] = HEX_UPPER[n & 0x0f];
    }
    return new String (ret);
  }

  private static int _base64Value (final char c)
  {
    if (c >= 'A' && c <= 'Z')
      return c - 'A';
    if (c >= 'a' && c <= 'z')
      return c - 'a' + 26;
    if (c >= '0' && c <= '9')
      return c - '0' + 52;
    if (c == '+')
      return 62;
    if (c == '/')
      return 63;
    return -1;
  }

  /**
   * Decode the XSD base64Binary lexical form: groups of four characters,
   * single spaces allowed between characters, correct padding, and the unused
   * bits of the last group must be zero.
   *
   * @param s
   *        Collapsed lexical form.
   * @return The bytes or <code>null</code> if the form is invalid.
   */
  public static byte @Nullable [] decodeBase64 (@NonNull final String s)
  {
    // After collapsing, only single spaces between characters remain, which
    // the XSD grammar allows everywhere between two characters.
    final StringBuilder aSB = new StringBuilder (s.length ());
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c == ' ')
      {
        if (i == 0 || i == s.length () - 1 || s.charAt (i + 1) == ' ')
          return null;
      }
      else
        aSB.append (c);
    }
    final int nLen = aSB.length ();
    if (nLen % 4 != 0)
      return null;
    if (nLen == 0)
      return new byte [0];
    int nPad = 0;
    if (aSB.charAt (nLen - 1) == '=')
    {
      nPad++;
      if (aSB.charAt (nLen - 2) == '=')
        nPad++;
    }
    final byte [] ret = new byte [nLen / 4 * 3 - nPad];
    int nOut = 0;
    for (int i = 0; i < nLen; i += 4)
    {
      final boolean bLast = i + 4 == nLen;
      final int n0 = _base64Value (aSB.charAt (i));
      final int n1 = _base64Value (aSB.charAt (i + 1));
      if (n0 < 0 || n1 < 0)
        return null;
      if (bLast && nPad == 2)
      {
        // B04: only the top 2 bits of the second character may be set
        if ((n1 & 0x0f) != 0)
          return null;
        ret[nOut++] = (byte) (n0 << 2 | n1 >> 4);
        continue;
      }
      final int n2 = _base64Value (aSB.charAt (i + 2));
      if (n2 < 0)
        return null;
      if (bLast && nPad == 1)
      {
        // B16: the low 2 bits of the third character must be zero
        if ((n2 & 0x03) != 0)
          return null;
        ret[nOut++] = (byte) (n0 << 2 | n1 >> 4);
        ret[nOut++] = (byte) ((n1 & 0x0f) << 4 | n2 >> 2);
        continue;
      }
      final int n3 = _base64Value (aSB.charAt (i + 3));
      if (n3 < 0)
        return null;
      ret[nOut++] = (byte) (n0 << 2 | n1 >> 4);
      ret[nOut++] = (byte) ((n1 & 0x0f) << 4 | n2 >> 2);
      ret[nOut++] = (byte) ((n2 & 0x03) << 6 | n3);
    }
    return ret;
  }
}
