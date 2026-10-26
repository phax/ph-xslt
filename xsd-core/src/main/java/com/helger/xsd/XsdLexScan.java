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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ops.XsdLexical;

/**
 * Hand-written lexical scanners for the hot datatypes (go-xslt
 * <code>lexscan.go</code> plus the hexBinary and base64Binary scanners of
 * <code>simpletype.go</code>). Each accepts exactly the language of the regular
 * expression it stands for and costs a character loop instead of a regular
 * expression run, which matters for a base64Binary value of tens of megabytes.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdLexScan
{
  @PresentForCodeCoverage
  private static final XsdLexScan INSTANCE = new XsdLexScan ();

  private XsdLexScan ()
  {}

  private static boolean _isDigit (final char c)
  {
    return c >= '0' && c <= '9';
  }

  /**
   * @param sValue
   *        The value.
   * @return <code>true</code> for <code>[+-]?[0-9]+</code>.
   */
  static boolean isIntegerLex (@NonNull final String sValue)
  {
    final int nLen = sValue.length ();
    int i = 0;
    if (i < nLen && (sValue.charAt (i) == '+' || sValue.charAt (i) == '-'))
      i++;
    if (i == nLen)
      return false;
    for (; i < nLen; i++)
      if (!_isDigit (sValue.charAt (i)))
        return false;
    return true;
  }

  /**
   * @param sValue
   *        The value.
   * @return <code>true</code> for the xs:decimal lexical space
   *         <code>[+-]?([0-9]+\.?[0-9]*|\.[0-9]+)</code>.
   */
  static boolean isDecimalLex (@NonNull final String sValue)
  {
    final int nLen = sValue.length ();
    int i = 0;
    if (i < nLen && (sValue.charAt (i) == '+' || sValue.charAt (i) == '-'))
      i++;
    int nIntDigits = 0;
    while (i < nLen && _isDigit (sValue.charAt (i)))
    {
      i++;
      nIntDigits++;
    }
    int nFracDigits = 0;
    if (i < nLen && sValue.charAt (i) == '.')
    {
      i++;
      while (i < nLen && _isDigit (sValue.charAt (i)))
      {
        i++;
        nFracDigits++;
      }
    }
    return i == nLen && (nIntDigits > 0 || nFracDigits > 0);
  }

  /**
   * An all-ASCII value is decided here (the ASCII part of NameStartChar minus
   * ':' is <code>[A-Za-z_]</code>, NameChar adds <code>[-.0-9]</code>), anything
   * else is left to the full production.
   *
   * @param sValue
   *        The value.
   * @return <code>true</code> for the NCName production.
   */
  static boolean isNCNameLex (@NonNull final String sValue)
  {
    final int nLen = sValue.length ();
    if (nLen == 0)
      return false;
    for (int i = 0; i < nLen; i++)
    {
      final char c = sValue.charAt (i);
      if (c >= 0x80)
        return XsdLexical.isNCName (sValue);
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_')
        continue;
      if (i > 0 && (_isDigit (c) || c == '-' || c == '.'))
        continue;
      // An ASCII character no NCName may hold at this position; a later
      // non-ASCII one cannot make the value match
      return false;
    }
    return true;
  }

  /**
   * @param sValue
   *        The value.
   * @return <code>true</code> for <code>([0-9A-Fa-f]{2})*</code>: an even
   *         number of hex digits, no whitespace.
   */
  static boolean isHexBinaryLex (@NonNull final String sValue)
  {
    final int nLen = sValue.length ();
    if (nLen % 2 != 0)
      return false;
    for (int i = 0; i < nLen; i++)
    {
      final char c = sValue.charAt (i);
      if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'))
        return false;
    }
    return true;
  }

  private static boolean _isBase64Char (final char c)
  {
    return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '/';
  }

  /**
   * The XSD Part 2 grammar: base64 characters in groups of four with optional
   * single spaces between characters, where only the final group may be padded
   * ("=" needs a B16 character before it, "==" a B04 character before that).
   *
   * @param sValue
   *        The (collapsed) value.
   * @return <code>true</code> for the xs:base64Binary lexical space.
   */
  static boolean isBase64BinaryLex (@NonNull final String sValue)
  {
    final int nLen = sValue.length ();
    // Base64 characters and '=' seen, spaces excluded
    int n = 0;
    // '=' seen so far; nothing but '=' may follow the first
    int nPad = 0;
    // The last base64 character, the one before any padding
    char cLast = 0;
    for (int i = 0; i < nLen; i++)
    {
      final char c = sValue.charAt (i);
      if (_isBase64Char (c))
      {
        if (nPad > 0)
          return false;
        cLast = c;
      }
      else
        if (c == ' ')
        {
          // A single space between characters; none leading, trailing or
          // doubled
          if (i == 0 || i == nLen - 1 || sValue.charAt (i - 1) == ' ')
            return false;
          continue;
        }
        else
          if (c == '=')
            nPad++;
          else
            return false;
      n++;
    }
    if (n % 4 != 0)
      return false;
    switch (nPad)
    {
      case 0:
        return true;
      case 1:
        return "AEIMQUYcgkosw048".indexOf (cLast) >= 0;
      case 2:
        return "AQgw".indexOf (cLast) >= 0;
      default:
        return false;
    }
  }
}
