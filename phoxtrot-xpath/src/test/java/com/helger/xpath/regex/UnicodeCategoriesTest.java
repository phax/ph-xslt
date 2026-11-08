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
package com.helger.xpath.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Test class for class {@link UnicodeCategories}.
 *
 * @author Philip Helger
 */
public final class UnicodeCategoriesTest
{
  private static boolean _matches (final String sRegex, final int c)
  {
    return XPathRegex.compile (sRegex, null).getPattern ().matcher (new String (Character.toChars (c))).matches ();
  }

  @Test
  public void testCategory ()
  {
    assertEquals ("Cc", UnicodeCategories.getCategory (0));
    assertEquals ("Lu", UnicodeCategories.getCategory ('A'));
    assertEquals ("Ll", UnicodeCategories.getCategory ('a'));
    assertEquals ("Cn", UnicodeCategories.getCategory (0x378));
    assertEquals ("Co", UnicodeCategories.getCategory (0x10FFFD));
    assertEquals ("Cn", UnicodeCategories.getCategory (0x10FFFF));
    // Unassigned in Unicode 15.0, assigned in 16.0 (Garay, Latin letters)
    assertEquals ("Cn", UnicodeCategories.getCategory (0x10D50));
    assertEquals ("Cn", UnicodeCategories.getCategory (0xA7CB));
    assertTrue (UnicodeCategories.isCategory ("Lu"));
    assertTrue (UnicodeCategories.isCategory ("C"));
    assertFalse (UnicodeCategories.isCategory ("LC"));
  }

  /**
   * Every escape matches exactly the Unicode 15.0 characters of its category, whatever the Unicode
   * version of the running JDK.
   */
  @Test
  public void testEscapesMatchUnicode15 ()
  {
    for (final String sName : new String [] { "L",
                                              "Lu",
                                              "Ll",
                                              "Lo",
                                              "M",
                                              "Mn",
                                              "N",
                                              "Nd",
                                              "P",
                                              "S",
                                              "So",
                                              "Z",
                                              "C",
                                              "Cn" })
    {
      final Pattern aPos = XPathRegex.compile ("\\p{" + sName + "}", null).getPattern ();
      final Pattern aNeg = XPathRegex.compile ("[\\P{" + sName + "}]", null).getPattern ();
      for (int c = 0; c <= Character.MAX_CODE_POINT; ++c)
      {
        if (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)
          continue;

        final String sCategory = UnicodeCategories.getCategory (c);
        final boolean bExpected = sName.length () == 1 ? sCategory.charAt (0) == sName.charAt (0)
                                                       : sCategory.equals (sName);
        final String sChar = new String (Character.toChars (c));
        assertEquals (sName + " " + Integer.toHexString (c), bExpected, aPos.matcher (sChar).matches ());
        assertEquals (sName + " " + Integer.toHexString (c), !bExpected, aNeg.matcher (sChar).matches ());
      }
    }
  }

  @Test
  public void testMultiCharEscapes ()
  {
    assertTrue (_matches ("\\d", '7'));
    assertFalse (_matches ("\\d", 'a'));
    assertTrue (_matches ("\\w", 'a'));
    // \w excludes the unassigned code points (XSD's C includes Cn)
    assertFalse (_matches ("\\w", 0x378));
    assertFalse (_matches ("\\w", 0x10D50));
    assertTrue (_matches ("\\W", 0x10D50));
  }

  @Test
  public void testCaseInsensitiveUnicode15Only ()
  {
    // Garay capital and small letter A are a case pair since Unicode 16.0
    final Pattern aPattern = XPathRegex.compile (new String (Character.toChars (0x10D50)), "i").getPattern ();
    assertFalse (aPattern.matcher (new String (Character.toChars (0x10D70))).matches ());
    assertTrue (XPathRegex.compile ("a", "i").getPattern ().matcher ("A").matches ());
  }
}
