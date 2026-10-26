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
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ops.XsdLexical;

/**
 * Lexical helpers the loader needs (from go-xslt <code>simpletype.go</code>,
 * <code>loader.go</code> and <code>attributes.go</code>). The datatype
 * validation proper is phase V3 ({@link XsdSimpleTypeValidator}).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdLexHelper
{
  @PresentForCodeCoverage
  private static final XsdLexHelper INSTANCE = new XsdLexHelper ();

  private XsdLexHelper ()
  {}

  private static boolean _isAlpha (final char c)
  {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
  }

  private static boolean _isHex (final char c)
  {
    return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
  }

  /**
   * go-xslt <code>permissiveNCName</code>: the first character must be a letter
   * (including letter numbers) or '_'; later ASCII characters must be
   * <code>[A-Za-z0-9._-]</code>, and every non-ASCII character is permitted,
   * so a valid Unicode name is never rejected.
   */
  static boolean permissiveNCName (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    final int nLen = s.length ();
    int i = 0;
    boolean bFirst = true;
    while (i < nLen)
    {
      final int c = s.codePointAt (i);
      i += Character.charCount (c);
      if (c == ':')
        return false;
      if (bFirst)
      {
        bFirst = false;
        if (!(Character.isLetter (c) || Character.getType (c) == Character.LETTER_NUMBER || c == '_'))
          return false;
        continue;
      }
      if (c < 0x80)
      {
        final boolean bOK = c >= 'a' && c <= 'z' ||
                            c >= 'A' && c <= 'Z' ||
                            c >= '0' && c <= '9' ||
                            c == '.' ||
                            c == '-' ||
                            c == '_';
        if (!bOK)
          return false;
      }
    }
    return true;
  }

  /** go-xslt <code>isNCName</code>: the permissive NCName check. */
  static boolean isNCName (@NonNull final String s)
  {
    return permissiveNCName (s);
  }

  /**
   * go-xslt <code>validQNameLexical</code>: <code>(NCName ':')? NCName</code>
   * with the permissive NCName reading; the prefix <code>xmlns</code> is never
   * legal.
   */
  static boolean validQNameLexical (@NonNull final String s)
  {
    final int i = s.indexOf (':');
    if (i >= 0)
    {
      final String sPrefix = s.substring (0, i);
      final String sLocal = s.substring (i + 1);
      return !"xmlns".equals (sPrefix) && permissiveNCName (sPrefix) && permissiveNCName (sLocal);
    }
    return permissiveNCName (s);
  }

  /**
   * go-xslt <code>validAnyURI10</code>: the XSD 1.0 xs:anyURI lexical space, an
   * RFC 2396 URI reference (RFC 2732 brackets tolerated), where non-ASCII
   * characters and <code>&lt; &gt; "</code> count as pre-escapable.
   */
  static boolean validAnyURI10 (@NonNull final String s)
  {
    final int nLen = s.length ();
    for (int i = 0; i < nLen; i++)
    {
      final char c = s.charAt (i);
      if (c >= 0x80 || _isAlpha (c) || c >= '0' && c <= '9')
        continue;
      if ("-_.!~*'();/?:@&=+$,#[]<>\"".indexOf (c) >= 0)
        continue;
      if (c == '%')
      {
        if (i + 2 >= nLen || !_isHex (s.charAt (i + 1)) || !_isHex (s.charAt (i + 2)))
          return false;
        i += 2;
        continue;
      }
      return false;
    }
    String sHead = s;
    int nStop = -1;
    for (int i = 0; i < nLen; i++)
    {
      final char c = s.charAt (i);
      if (c == '/' || c == '?' || c == '#')
      {
        nStop = i;
        break;
      }
    }
    if (nStop >= 0)
      sHead = s.substring (0, nStop);
    final int nColon = sHead.indexOf (':');
    if (nColon >= 0)
    {
      final String sScheme = sHead.substring (0, nColon);
      if (sScheme.isEmpty () || !_isAlpha (sScheme.charAt (0)))
        return false;
      for (int j = 1; j < sScheme.length (); j++)
      {
        final char c = sScheme.charAt (j);
        if (!(_isAlpha (c) || c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.'))
          return false;
      }
      String sRest = s.substring (nColon + 1);
      final int nHash = sRest.indexOf ('#');
      if (nHash >= 0)
        sRest = sRest.substring (0, nHash);
      if (sRest.isEmpty ())
        return false;
    }
    return true;
  }

  /** go-xslt <code>lexEqualCollapsed</code>. */
  static boolean lexEqualCollapsed (@NonNull final String sA, @NonNull final String sB)
  {
    return XsdWhiteSpace.collapse (sA).equals (XsdWhiteSpace.collapse (sB));
  }

  /**
   * go-xslt <code>nonNegInt</code>.
   *
   * @return The value, or -1 if the trimmed text is not a non-negative int.
   */
  static int nonNegInt (@NonNull final String s)
  {
    final String t = s.strip ();
    if (t.isEmpty ())
      return -1;
    try
    {
      final int n = Integer.parseInt (t);
      return n < 0 ? -1 : n;
    }
    catch (final NumberFormatException ex)
    {
      return -1;
    }
  }

  /**
   * go-xslt <code>atoiOr</code> for occurrence values: the trimmed text as an
   * integer, clamped to the int range on overflow (go-xslt clamps to its 64-bit
   * int), 0 if it is no integer.
   */
  static int atoiOr (@NonNull final String s)
  {
    final String t = s.strip ();
    int i = 0;
    boolean bNeg = false;
    if (i < t.length () && (t.charAt (i) == '+' || t.charAt (i) == '-'))
    {
      bNeg = t.charAt (i) == '-';
      i++;
    }
    if (i >= t.length ())
      return 0;
    long n = 0;
    for (; i < t.length (); i++)
    {
      final char c = t.charAt (i);
      if (c < '0' || c > '9')
        return 0;
      if (n <= Integer.MAX_VALUE)
        n = n * 10 + (c - '0');
    }
    if (bNeg)
      return n > Integer.MAX_VALUE ? Integer.MIN_VALUE : (int) -n;
    return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
  }

  /**
   * go-xslt <code>xmlFields</code>: split at runs of XML whitespace (space,
   * tab, CR, LF), dropping empty fields.
   */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsList <String> xmlFields (@NonNull final String s)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final int nLen = s.length ();
    int nStart = -1;
    for (int i = 0; i < nLen; i++)
    {
      final char c = s.charAt (i);
      if (XsdLexical.isXmlWhitespace (c))
      {
        if (nStart >= 0)
        {
          ret.add (s.substring (nStart, i));
          nStart = -1;
        }
      }
      else
        if (nStart < 0)
          nStart = i;
    }
    if (nStart >= 0)
      ret.add (s.substring (nStart));
    return ret;
  }

  /** @return The Go <code>%q</code>-like quoting of a value for messages. */
  @NonNull
  static String quote (@NonNull final String s)
  {
    return "\"" + s + "\"";
  }
}
