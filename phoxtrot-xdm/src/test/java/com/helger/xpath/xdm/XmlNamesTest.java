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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Test class for class {@link XmlNames} (go-xslt <code>names_test.go</code>).
 *
 * @author Philip Helger
 */
public final class XmlNamesTest
{
  /**
   * The NameStartChar production of XML 1.0 (5th edition) section 2.3,
   * transcribed from the specification independently of {@link XmlNames}.
   */
  private static boolean _specNameStart (final int c)
  {
    return c == ':' ||
           (c >= 'A' && c <= 'Z') ||
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

  private static boolean _specNameChar (final int c)
  {
    return _specNameStart (c) ||
           c == '-' ||
           c == '.' ||
           (c >= '0' && c <= '9') ||
           c == 0xB7 ||
           (c >= 0x300 && c <= 0x36F) ||
           (c >= 0x203F && c <= 0x2040);
  }

  private static boolean _inRanges (final int [] aRanges, final int c)
  {
    for (int i = 0; i < aRanges.length; i += 2)
      if (c >= aRanges[i] && c <= aRanges[i + 1])
        return true;
    return false;
  }

  @Test
  public void testPredicatesMatchSpec ()
  {
    final int [] aStart = XmlNames.getNCNameStartRanges ();
    final int [] aExtra = XmlNames.getNameCharExtraRanges ();
    for (int c = 0; c <= 0x10FFFF; ++c)
    {
      final boolean bStart = _specNameStart (c);
      final boolean bChar = _specNameChar (c);
      if (XmlNames.isNameStartChar (c) != bStart ||
          XmlNames.isNameChar (c) != bChar ||
          XmlNames.isNCNameStartChar (c) != (bStart && c != ':') ||
          XmlNames.isNCNameChar (c) != (bChar && c != ':'))
        fail ("Predicate differs from the specification at U+" + Integer.toHexString (c));
      // The ranges for character classes say the same
      if (_inRanges (aStart, c) != XmlNames.isNCNameStartChar (c) ||
          (_inRanges (aStart, c) || _inRanges (aExtra, c)) != XmlNames.isNCNameChar (c))
        fail ("Ranges differ from the predicate at U+" + Integer.toHexString (c));
    }
  }

  @Test
  public void testNameStrings ()
  {
    final Object [] [] aCases = { { "a", Boolean.TRUE, Boolean.TRUE },
                                  { "_x.y-z", Boolean.TRUE, Boolean.TRUE },
                                  { "a:b", Boolean.FALSE, Boolean.TRUE },
                                  { ":a", Boolean.FALSE, Boolean.TRUE },
                                  { "", Boolean.FALSE, Boolean.FALSE },
                                  { "1a", Boolean.FALSE, Boolean.FALSE },
                                  { "-a", Boolean.FALSE, Boolean.FALSE },
                                  { ".a", Boolean.FALSE, Boolean.FALSE },
                                  { "a b", Boolean.FALSE, Boolean.FALSE },
                                  { "😀", Boolean.TRUE, Boolean.TRUE },
                                  { "৪", Boolean.TRUE, Boolean.TRUE },
                                  { "a😀", Boolean.TRUE, Boolean.TRUE },
                                  { "𐀀x", Boolean.TRUE, Boolean.TRUE },
                                  { "x৪", Boolean.TRUE, Boolean.TRUE },
                                  { "̀a", Boolean.FALSE, Boolean.FALSE },
                                  { "à", Boolean.TRUE, Boolean.TRUE },
                                  { "a￾", Boolean.FALSE, Boolean.FALSE },
                                  { "a￿", Boolean.FALSE, Boolean.FALSE },
                                  { "󰀀", Boolean.FALSE, Boolean.FALSE },
                                  { "×", Boolean.FALSE, Boolean.FALSE },
                                  // U+00B5 lies below 0xC0: no NameStartChar
                                  { "µ", Boolean.FALSE, Boolean.FALSE },
                                  { null, Boolean.FALSE, Boolean.FALSE } };
    for (final Object [] aCase : aCases)
    {
      final String s = (String) aCase[0];
      assertEquals (s, aCase[1], Boolean.valueOf (XmlNames.isNCName (s)));
      assertEquals (s, aCase[2], Boolean.valueOf (XmlNames.isName (s)));
    }
  }
}
