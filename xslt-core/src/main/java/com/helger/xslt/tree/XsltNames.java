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
package com.helger.xslt.tree;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Lexical checks for names and XSLT attribute values.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltNames
{
  /** The XSLT namespace URI. */
  public static final String NS_XSL = XdmConstants.NS_XSL;

  @PresentForCodeCoverage
  private static final XsltNames INSTANCE = new XsltNames ();

  private XsltNames ()
  {}

  private static boolean _isNCNameStartChar (final int c)
  {
    return (c >= 'A' && c <= 'Z') ||
           c == '_' ||
           (c >= 'a' && c <= 'z') ||
           (c >= 0xC0 && c <= 0xD6) ||
           (c >= 0xD8 && c <= 0xF6) ||
           (c >= 0xF8 && c <= 0x2FF) ||
           (c >= 0x370 && c <= 0x37D) ||
           (c >= 0x37F && c <= 0x1FFF) ||
           (c >= 0x200C && c <= 0x200D) ||
           (c >= 0x2070 && c <= 0x218F) ||
           (c >= 0x2C00 && c <= 0x2FEF) ||
           (c >= 0x3001 && c <= 0xD7FF) ||
           (c >= 0xF900 && c <= 0xFDCF) ||
           (c >= 0xFDF0 && c <= 0xFFFD) ||
           (c >= 0x10000 && c <= 0xEFFFF);
  }

  private static boolean _isNCNameChar (final int c)
  {
    return _isNCNameStartChar (c) ||
           c == '-' ||
           c == '.' ||
           (c >= '0' && c <= '9') ||
           c == 0xB7 ||
           (c >= 0x300 && c <= 0x36F) ||
           (c >= 0x203F && c <= 0x2040);
  }

  private static boolean _isXmlWhitespace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /**
   * @param s
   *        String to check. May be <code>null</code>.
   * @return <code>true</code> if the string is a valid NCName.
   */
  public static boolean isNCName (@Nullable final String s)
  {
    if (s == null || s.isEmpty ())
      return false;
    int i = 0;
    while (i < s.length ())
    {
      final int c = s.codePointAt (i);
      if (i == 0 ? !_isNCNameStartChar (c) : !_isNCNameChar (c))
        return false;
      i += Character.charCount (c);
    }
    return true;
  }

  /**
   * @param s
   *        String to check, already trimmed.
   * @return <code>true</code> if it is a lexical QName (prefix:local or
   *         local).
   */
  public static boolean isLexicalQName (@Nullable final String s)
  {
    if (s == null)
      return false;
    final int n = s.indexOf (':');
    if (n < 0)
      return isNCName (s);
    return isNCName (s.substring (0, n)) && isNCName (s.substring (n + 1));
  }

  /**
   * @param s
   *        String to check, already trimmed.
   * @return <code>true</code> if it is a lexical QName or a URIQualifiedName
   *         (<code>Q{uri}local</code>).
   */
  public static boolean isEQName (@Nullable final String s)
  {
    if (s == null)
      return false;
    if (s.startsWith ("Q{"))
    {
      final int n = s.indexOf ('}');
      return n > 0 && s.indexOf ('{', 2) < 0 && isNCName (s.substring (n + 1));
    }
    return isLexicalQName (s);
  }

  /**
   * @param s
   *        A value of an XSLT boolean attribute. May be <code>null</code>.
   * @return <code>true</code> if it is one of yes, no, true, false, 1, 0
   *         (surrounding whitespace allowed).
   */
  public static boolean isXsltBooleanLexical (@Nullable final String s)
  {
    if (s == null)
      return false;
    return switch (s.trim ())
    {
      case "yes", "no", "true", "false", "1", "0" -> true;
      default -> false;
    };
  }

  /**
   * @param s
   *        A value of an XSLT boolean attribute. May be <code>null</code>.
   * @return <code>true</code> for yes, true and 1.
   */
  public static boolean isXsltTrue (@Nullable final String s)
  {
    if (s == null)
      return false;
    return switch (s.trim ())
    {
      case "yes", "true", "1" -> true;
      default -> false;
    };
  }

  /**
   * @param s
   *        String to check, already trimmed.
   * @return <code>true</code> if it is a valid xs:decimal lexical form
   *         (optional sign, digits, optional fraction, no exponent).
   */
  public static boolean isDecimalLexical (@Nullable final String s)
  {
    if (s == null || s.isEmpty ())
      return false;
    int i = 0;
    if (s.charAt (0) == '+' || s.charAt (0) == '-')
      i++;
    boolean bDigits = false;
    boolean bDot = false;
    for (; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c >= '0' && c <= '9')
        bDigits = true;
      else
        if (c == '.' && !bDot)
          bDot = true;
        else
          return false;
    }
    return bDigits;
  }

  /**
   * @param sUri
   *        A namespace URI.
   * @return <code>true</code> if it is one of the namespaces reserved by XSLT
   *         3.0 section 3.3.2.
   */
  public static boolean isReservedNamespace (@NonNull final String sUri)
  {
    return switch (sUri)
    {
      case NS_XSL, XdmConstants.NS_XS, XdmConstants.NS_XSI, XdmConstants.NS_FN, "http://www.w3.org/2005/xpath-functions/math", "http://www.w3.org/2005/xpath-functions/map", "http://www.w3.org/2005/xpath-functions/array", XdmConstants.NS_XML, "http://www.w3.org/2005/xqt-errors" -> true;
      default -> false;
    };
  }

  /**
   * @param s
   *        A string. May be <code>null</code>.
   * @return <code>true</code> if it consists of XML whitespace only (also for
   *         the empty string).
   */
  public static boolean isWhitespaceOnly (@Nullable final String s)
  {
    if (s == null)
      return true;
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
        return false;
    }
    return true;
  }

  /**
   * @param s
   *        A string.
   * @return The string with XML whitespace (space, tab, CR, LF) removed at
   *         both ends.
   */
  @NonNull
  public static String trimXmlWhitespace (@NonNull final String s)
  {
    int nStart = 0;
    int nEnd = s.length ();
    while (nStart < nEnd && _isXmlWhitespace (s.charAt (nStart)))
      nStart++;
    while (nEnd > nStart && _isXmlWhitespace (s.charAt (nEnd - 1)))
      nEnd--;
    return s.substring (nStart, nEnd);
  }

  /**
   * @param s
   *        A string.
   * @return The whitespace-separated tokens of the string; empty for a
   *         whitespace-only string.
   */
  @NonNull
  public static String [] tokenize (@NonNull final String s)
  {
    final String sTrimmed = trimXmlWhitespace (s);
    if (sTrimmed.isEmpty ())
      return new String [0];
    return sTrimmed.split ("[ \\t\\r\\n]+");
  }
}
