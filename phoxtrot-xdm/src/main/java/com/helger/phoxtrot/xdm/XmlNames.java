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
package com.helger.phoxtrot.xdm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.string.XmlNameChars;

/**
 * The name productions of XML 1.0 (5th edition) and Namespaces in XML: the one definition of
 * NameStartChar and NameChar that the XPath lexer, the casts to the name types, the XSLT name
 * checks, the XSD lexical checks and the regular expression classes <code>\i</code> and
 * <code>\c</code> all use. The character and name checks delegate to ph-commons
 * {@link XmlNameChars}; the code point ranges for the regular expression classes are kept here.
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
  private static final int [] NAME_EXTRA_RANGES = { '-',
                                                    '-',
                                                    '.',
                                                    '.',
                                                    '0',
                                                    '9',
                                                    0xB7,
                                                    0xB7,
                                                    0x300,
                                                    0x36F,
                                                    0x203F,
                                                    0x2040 };

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
    return XmlNameChars.isNCNameStartChar (c);
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameChar other than the colon.
   */
  public static boolean isNCNameChar (final int c)
  {
    return XmlNameChars.isNCNameChar (c);
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameStartChar.
   */
  public static boolean isNameStartChar (final int c)
  {
    return XmlNameChars.isNameStartChar (c);
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is a NameChar.
   */
  public static boolean isNameChar (final int c)
  {
    return XmlNameChars.isNameChar (c);
  }

  /**
   * @param s
   *        String to check. May be <code>null</code>.
   * @return <code>true</code> if it matches the <code>NCName</code> production.
   */
  public static boolean isNCName (@Nullable final String s)
  {
    return XmlNameChars.isNCName (s);
  }

  /**
   * @param s
   *        String to check. May be <code>null</code>.
   * @return <code>true</code> if it matches the XML <code>Name</code> production.
   */
  public static boolean isName (@Nullable final String s)
  {
    return XmlNameChars.isName (s);
  }

  /**
   * @return NameStartChar without the colon as inclusive code point ranges (start, end, start, end,
   *         ...), for building character classes.
   */
  @NonNull
  @ReturnsMutableCopy
  public static int [] getNCNameStartRanges ()
  {
    return NC_NAME_START_RANGES.clone ();
  }

  /**
   * @return The code points a NameChar may have beyond a NameStartChar, as inclusive ranges.
   */
  @NonNull
  @ReturnsMutableCopy
  public static int [] getNameCharExtraRanges ()
  {
    return NAME_EXTRA_RANGES.clone ();
  }
}
