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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;

/**
 * The name productions of XML 1.0 (5th edition) and Namespaces in XML: the one
 * definition of NameStartChar and NameChar that the XPath lexer, the casts to
 * the name types, the XSLT name checks, the XSD lexical checks and the regular
 * expression classes <code>\i</code> and <code>\c</code> all use.
 *
 * @author Philip Helger
 */
@Immutable
public final class XmlNames
{
  /** NameStartChar without the colon, as inclusive code point ranges */
  private static final int [] NC_NAME_START_RANGES = { 'A',
                                                       'Z',
                                                       '_',
                                                       '_',
                                                       'a',
                                                       'z',
                                                       0xC0,
                                                       0xD6,
                                                       0xD8,
                                                       0xF6,
                                                       0xF8,
                                                       0x2FF,
                                                       0x370,
                                                       0x37D,
                                                       0x37F,
                                                       0x1FFF,
                                                       0x200C,
                                                       0x200D,
                                                       0x2070,
                                                       0x218F,
                                                       0x2C00,
                                                       0x2FEF,
                                                       0x3001,
                                                       0xD7FF,
                                                       0xF900,
                                                       0xFDCF,
                                                       0xFDF0,
                                                       0xFFFD,
                                                       0x10000,
                                                       0xEFFFF };
  /** The characters a NameChar may have beyond a NameStartChar */
  private static final int [] NAME_EXTRA_RANGES = { '-', '-', '.', '.', '0', '9', 0xB7, 0xB7, 0x300, 0x36F, 0x203F, 0x2040 };

  @PresentForCodeCoverage
  private static final XmlNames INSTANCE = new XmlNames ();

  private XmlNames ()
  {}

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameStartChar other than the colon.
   */
  public static boolean isNCNameStartChar (final int c)
  {
    if (c < 0x80)
      return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    return (c >= 0xC0 && c <= 0xD6) ||
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

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameChar other than the colon.
   */
  public static boolean isNCNameChar (final int c)
  {
    return isNCNameStartChar (c) ||
           c == '-' ||
           c == '.' ||
           (c >= '0' && c <= '9') ||
           c == 0xB7 ||
           (c >= 0x300 && c <= 0x36F) ||
           (c >= 0x203F && c <= 0x2040);
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameStartChar.
   */
  public static boolean isNameStartChar (final int c)
  {
    return c == ':' || isNCNameStartChar (c);
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameChar.
   */
  public static boolean isNameChar (final int c)
  {
    return c == ':' || isNCNameChar (c);
  }

  /**
   * Check a string code point by code point (surrogate pairs included): the
   * first must be an NCName start character, the others NCName characters,
   * and a colon is accepted anywhere, also first, only if allowed.
   *
   * @param s
   *        The string to check. May not be <code>null</code>.
   * @param bAllowColon
   *        <code>true</code> for the XML <code>Name</code> production,
   *        <code>false</code> for <code>NCName</code>.
   * @return <code>false</code> for the empty string.
   */
  private static boolean _isName (@NonNull final String s, final boolean bAllowColon)
  {
    final int nLen = s.length ();
    if (nLen == 0)
      return false;
    int i = 0;
    while (i < nLen)
    {
      final int c = s.codePointAt (i);
      if (c == ':' ? !bAllowColon : i == 0 ? !isNCNameStartChar (c) : !isNCNameChar (c))
        return false;
      i += Character.charCount (c);
    }
    return true;
  }

  /**
   * @param s
   *        String to check. May be <code>null</code>.
   * @return <code>true</code> if it matches the <code>NCName</code>
   *         production.
   */
  public static boolean isNCName (@Nullable final String s)
  {
    return s != null && _isName (s, false);
  }

  /**
   * @param s
   *        String to check. May be <code>null</code>.
   * @return <code>true</code> if it matches the XML <code>Name</code>
   *         production.
   */
  public static boolean isName (@Nullable final String s)
  {
    return s != null && _isName (s, true);
  }

  /**
   * @return NameStartChar without the colon as inclusive code point ranges
   *         (start, end, start, end, ...), for building character classes.
   */
  @NonNull
  @ReturnsMutableCopy
  public static int [] getNCNameStartRanges ()
  {
    return NC_NAME_START_RANGES.clone ();
  }

  /**
   * @return The code points a NameChar may have beyond a NameStartChar, as
   *         inclusive ranges.
   */
  @NonNull
  @ReturnsMutableCopy
  public static int [] getNameCharExtraRanges ()
  {
    return NAME_EXTRA_RANGES.clone ();
  }
}
