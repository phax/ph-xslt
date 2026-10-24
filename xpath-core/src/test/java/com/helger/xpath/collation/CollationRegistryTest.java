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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.XPathException;

/**
 * Test class for class {@link CollationRegistry}.
 *
 * @author Philip Helger
 */
public final class CollationRegistryTest
{
  private static final String UCA = CollationRegistry.UCA_URI;

  private static void _assertFOCH0002 (@NonNull final String sUri)
  {
    try
    {
      CollationRegistry.getCollationOrThrow (sUri);
      fail ("Expected FOCH0002 for " + sUri);
    }
    catch (final XPathException ex)
    {
      assertEquals ("FOCH0002", ex.getCode ());
    }
    assertNull (CollationRegistry.getCollation (sUri));
  }

  private static int _cmp (@NonNull final String sUri, @NonNull final String a, @NonNull final String b)
  {
    final ICollation aColl = CollationRegistry.getCollationOrThrow (sUri);
    final int ret = Integer.signum (aColl.compare (a, b));
    // Keys must agree with the comparison
    assertEquals (ret == 0, aColl.getCollationKey (a).equals (aColl.getCollationKey (b)));
    final int nKeyCmp = Integer.signum (Arrays.compareUnsigned (aColl.getCollationKeyBytes (a),
                                                                          aColl.getCollationKeyBytes (b)));
    assertEquals (ret, nKeyCmp);
    return ret;
  }

  @Test
  public void testBasics ()
  {
    assertSame (CollationRegistry.CODEPOINT, CollationRegistry.getCollation (CollationRegistry.CODEPOINT_URI));
    assertNotNull (CollationRegistry.getCollation (CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI));
    assertNotNull (CollationRegistry.getCollation (UCA));
    _assertFOCH0002 ("http://example.org/collation");
    _assertFOCH0002 (UCA + "?reorder=Grek;fallback=no");
    _assertFOCH0002 (UCA + "?strength=superstrong;fallback=no");
    _assertFOCH0002 (UCA + "?version=96.5;fallback=no");
    _assertFOCH0002 (UCA + "?fallback=maybe");
    // fallback=yes ignores what cannot be honoured
    assertNotNull (CollationRegistry.getCollation (UCA + "?reorder=Grek;strength=superstrong"));
  }

  @Test
  public void testResolveUri ()
  {
    assertEquals (CollationRegistry.CODEPOINT_URI,
                  CollationRegistry.resolveUri ("collation/codepoint", "http://www.w3.org/2005/xpath-functions/"));
    assertEquals ("http://a/b", CollationRegistry.resolveUri ("http://a/b", "http://c/"));
    assertEquals ("rel", CollationRegistry.resolveUri ("rel", null));
  }

  @Test
  public void testCodepoint ()
  {
    final String sUri = CollationRegistry.CODEPOINT_URI;
    assertEquals (-1, _cmp (sUri, "a", "b"));
    assertEquals (0, _cmp (sUri, "a", "a"));
    // Code point order, not UTF-16 order
    assertEquals (1, _cmp (sUri, "𐀀", "�"));
  }

  @Test
  public void testHtmlAscii ()
  {
    final String sUri = CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI;
    assertEquals (0, _cmp (sUri, "ABC", "abc"));
    assertNotEquals (0, _cmp (sUri, "Á", "á"));
    final ICollation aColl = CollationRegistry.HTML_ASCII_CASE_INSENSITIVE;
    assertTrue (aColl.startsWith ("ABCdef", "abc"));
    assertTrue (aColl.endsWith ("abcDEF", "def"));
  }

  @Test
  public void testUca ()
  {
    assertEquals (-1, _cmp (UCA, "a", "A"));
    assertEquals (-1, _cmp (UCA, "a", "b"));
    assertEquals (-1, _cmp (UCA, "A", "b"));
    assertEquals (0, _cmp (UCA + "?strength=primary", "a", "Á"));
    assertEquals (-1, _cmp (UCA + "?strength=secondary", "a", "á"));
    assertEquals (0, _cmp (UCA + "?strength=secondary", "a", "A"));
    // Non-ignorable (the default): spaces and punctuation count
    assertEquals (1, _cmp (UCA, "database", "data base"));
    assertEquals (1, _cmp (UCA + "?strength=primary", "abc123", "ABC 123"));
    // Shifted: they are ignored below the quaternary level
    assertEquals (0, _cmp (UCA + "?alternate=shifted", "data-base", "database"));
    assertEquals (-1, _cmp (UCA + "?alternate=shifted;strength=quaternary", "data-base", "database"));
    // caseFirst
    assertEquals (1, _cmp (UCA + "?caseFirst=upper", "a", "A"));
    // numeric
    assertEquals (-1, _cmp (UCA + "?numeric=yes", "a9", "a10"));
    assertEquals (1, _cmp (UCA, "a9", "a10"));
    // Swedish: a-diaeresis after z
    assertEquals (1, _cmp (UCA + "?lang=sv", "ä", "z"));
    assertEquals (-1, _cmp (UCA, "ä", "z"));
    // French accents: backwards only in Canada
    assertEquals (1, _cmp (UCA + "?lang=fr;strength=secondary", "pêche", "péché"));
    assertEquals (-1, _cmp (UCA + "?lang=fr-CA;strength=secondary", "pêche", "péché"));
  }

  @Test
  public void testUcaSubstring ()
  {
    final ICollation aColl = CollationRegistry.getCollationOrThrow (UCA + "?strength=primary");
    final int [] aMatch = aColl.findSubstring ("Café au lait", "CAFE");
    assertNotNull (aMatch);
    assertEquals (0, aMatch[0]);
    assertEquals (4, aMatch[1]);
    assertNull (aColl.findSubstring ("abc", "d"));
    // A numeric collation does not split a number
    final ICollation aNum = CollationRegistry.getCollationOrThrow (UCA + "?numeric=yes");
    assertNull (aNum.findSubstring ("Chapter-100", "Chapter-10"));
  }
}
