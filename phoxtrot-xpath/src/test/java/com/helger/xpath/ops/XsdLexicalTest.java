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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for class {@link XsdLexical}.
 *
 * @author Philip Helger
 */
public final class XsdLexicalTest
{
  @Test
  public void testWhitespace ()
  {
    final String s = "abc";
    assertSame (s, XsdLexical.collapseWhitespace (s));
    assertSame (s, XsdLexical.replaceWhitespace (s));
    assertSame (s, XsdLexical.trimWhitespace (s));
    assertEquals ("a b c", XsdLexical.collapseWhitespace ("  a \t\n b\r\r c  "));
    assertEquals ("", XsdLexical.collapseWhitespace (" \t\n "));
    assertEquals ("a b", XsdLexical.collapseWhitespace ("a  b"));
    assertEquals (" a  b ", XsdLexical.replaceWhitespace ("\ta \nb\r"));
    assertEquals ("a \t b", XsdLexical.trimWhitespace ("\n a \t b \r"));
    // A no-break space is content, not whitespace
    assertEquals (" a ", XsdLexical.collapseWhitespace ("  a  "));
    // Form feed is no XML whitespace either
    assertEquals ("\fa", XsdLexical.trimWhitespace ("\fa "));
  }

  @Test
  public void testNumericLexical ()
  {
    assertTrue (XsdLexical.isIntegerLexical ("0"));
    assertTrue (XsdLexical.isIntegerLexical ("+5"));
    assertTrue (XsdLexical.isIntegerLexical ("-0012"));
    assertFalse (XsdLexical.isIntegerLexical (""));
    assertFalse (XsdLexical.isIntegerLexical ("+"));
    assertFalse (XsdLexical.isIntegerLexical ("1.0"));
    assertFalse (XsdLexical.isIntegerLexical ("1e2"));
    assertFalse (XsdLexical.isIntegerLexical ("1_000"));
    assertFalse (XsdLexical.isIntegerLexical ("١"));

    assertTrue (XsdLexical.isDecimalLexical ("1"));
    assertTrue (XsdLexical.isDecimalLexical ("1."));
    assertTrue (XsdLexical.isDecimalLexical (".5"));
    assertTrue (XsdLexical.isDecimalLexical ("-1.5"));
    assertFalse (XsdLexical.isDecimalLexical ("."));
    assertFalse (XsdLexical.isDecimalLexical ("1e5"));
    assertFalse (XsdLexical.isDecimalLexical ("INF"));
    assertFalse (XsdLexical.isDecimalLexical ("1.2.3"));

    assertTrue (XsdLexical.isDoubleLexical ("1"));
    assertTrue (XsdLexical.isDoubleLexical ("-1.5E-3"));
    assertTrue (XsdLexical.isDoubleLexical (".5e+7"));
    assertTrue (XsdLexical.isDoubleLexical ("5.E1"));
    assertTrue (XsdLexical.isDoubleLexical ("INF"));
    assertTrue (XsdLexical.isDoubleLexical ("+INF"));
    assertTrue (XsdLexical.isDoubleLexical ("-INF"));
    assertTrue (XsdLexical.isDoubleLexical ("NaN"));
    assertFalse (XsdLexical.isDoubleLexical ("-NaN"));
    assertFalse (XsdLexical.isDoubleLexical ("Infinity"));
    assertFalse (XsdLexical.isDoubleLexical ("inf"));
    assertFalse (XsdLexical.isDoubleLexical ("1e"));
    assertFalse (XsdLexical.isDoubleLexical ("e5"));
    assertFalse (XsdLexical.isDoubleLexical ("1d"));
    assertFalse (XsdLexical.isDoubleLexical ("0x10"));
    assertFalse (XsdLexical.isDoubleLexical ("1.5f"));
  }

  @Test
  public void testNames ()
  {
    assertTrue (XsdLexical.isNCName ("abc"));
    assertTrue (XsdLexical.isNCName ("_a-b.c1"));
    assertTrue (XsdLexical.isNCName ("été"));
    assertFalse (XsdLexical.isNCName ("a:b"));
    assertFalse (XsdLexical.isNCName ("1a"));
    assertFalse (XsdLexical.isNCName ("-a"));
    assertFalse (XsdLexical.isNCName (""));
    assertFalse (XsdLexical.isNCName (null));
    assertTrue (XsdLexical.isName ("a:b"));
    assertTrue (XsdLexical.isName (":a"));
    assertFalse (XsdLexical.isName ("1a"));
    assertTrue (XsdLexical.isNmtoken ("123"));
    assertTrue (XsdLexical.isNmtoken ("a:b-1"));
    assertFalse (XsdLexical.isNmtoken ("a b"));
    assertFalse (XsdLexical.isNmtoken (""));
    // Supplementary character in the name start range
    assertTrue (XsdLexical.isNCName ("𐀀x"));
    assertTrue (XsdLexical.isQNameLexical ("p:l"));
    assertTrue (XsdLexical.isQNameLexical ("l"));
    assertFalse (XsdLexical.isQNameLexical ("p:"));
    assertFalse (XsdLexical.isQNameLexical (":l"));
    assertFalse (XsdLexical.isQNameLexical ("a:b:c"));
  }

  @Test
  public void testLanguage ()
  {
    assertTrue (XsdLexical.isLanguage ("en"));
    assertTrue (XsdLexical.isLanguage ("en-US"));
    assertTrue (XsdLexical.isLanguage ("zh-Hant-TW"));
    assertTrue (XsdLexical.isLanguage ("x-12345678"));
    assertTrue (XsdLexical.isLanguage ("abcdefgh"));
    assertFalse (XsdLexical.isLanguage ("abcdefghi"));
    assertFalse (XsdLexical.isLanguage ("1en"));
    assertFalse (XsdLexical.isLanguage ("en_US"));
    assertFalse (XsdLexical.isLanguage ("en-"));
    assertFalse (XsdLexical.isLanguage ("-en"));
    assertFalse (XsdLexical.isLanguage (""));
    assertFalse (XsdLexical.isLanguage ("en-123456789"));
  }

  @Test
  public void testAnyUri ()
  {
    assertTrue (XsdLexical.isAnyUriLexical ("http://example.org/a%20b"));
    assertTrue (XsdLexical.isAnyUriLexical (""));
    assertTrue (XsdLexical.isAnyUriLexical ("a b"));
    assertFalse (XsdLexical.isAnyUriLexical ("%zz"));
    assertFalse (XsdLexical.isAnyUriLexical ("%2"));
    assertFalse (XsdLexical.isAnyUriLexical (":a"));
  }

  @Test
  public void testHex ()
  {
    assertArrayEquals (new byte [] { 0x0f, (byte) 0xa0 }, XsdLexical.decodeHex ("0fA0"));
    assertArrayEquals (new byte [0], XsdLexical.decodeHex (""));
    assertNull (XsdLexical.decodeHex ("0fa"));
    assertNull (XsdLexical.decodeHex ("zz"));
    assertEquals ("0FA0", XsdLexical.encodeHex (new byte [] { 0x0f, (byte) 0xa0 }));
  }

  @Test
  public void testBase64 ()
  {
    assertArrayEquals (new byte [] { 1, 2, 3 }, XsdLexical.decodeBase64 ("AQID"));
    assertArrayEquals (new byte [] { 1, 2 }, XsdLexical.decodeBase64 ("AQI="));
    assertArrayEquals (new byte [] { 1 }, XsdLexical.decodeBase64 ("AQ=="));
    assertArrayEquals (new byte [] { 1 }, XsdLexical.decodeBase64 ("AQ= ="));
    assertArrayEquals (new byte [] { 1, 2, 3 }, XsdLexical.decodeBase64 ("A Q I D"));
    assertArrayEquals (new byte [0], XsdLexical.decodeBase64 (""));
    // unused bits must be zero
    assertNull (XsdLexical.decodeBase64 ("AQJ="));
    assertNull (XsdLexical.decodeBase64 ("AR=="));
    // wrong length / padding
    assertNull (XsdLexical.decodeBase64 ("AQI"));
    assertNull (XsdLexical.decodeBase64 ("AQID="));
    assertNull (XsdLexical.decodeBase64 ("A=ID"));
    assertNull (XsdLexical.decodeBase64 ("AQ=I"));
    assertNull (XsdLexical.decodeBase64 ("AQ!D"));
    assertNull (XsdLexical.decodeBase64 ("A  QID"));
  }
}
