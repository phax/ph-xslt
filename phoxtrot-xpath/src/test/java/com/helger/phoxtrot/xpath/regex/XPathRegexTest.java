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
package com.helger.phoxtrot.xpath.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;

/**
 * Test class for class {@link XPathRegex}.
 *
 * @author Philip Helger
 */
public final class XPathRegexTest
{
  /** @return All matches of the pattern with the spans of all groups. */
  private static String _getAllMatches (final Pattern aPattern, final String sInput)
  {
    final StringBuilder aSB = new StringBuilder ();
    final Matcher aMatcher = aPattern.matcher (sInput);
    while (aMatcher.find ())
    {
      for (int i = 0; i <= aMatcher.groupCount (); ++i)
        aSB.append (aMatcher.start (i)).append ('-').append (aMatcher.end (i)).append (' ');
      aSB.append ('|');
    }
    return aSB.toString ();
  }
  @Test
  public void testCache ()
  {
    assertSame (XPathRegex.compile ("a+b", "i"), XPathRegex.compile ("a+b", "i"));
  }

  @Test
  public void testGroups ()
  {
    final XPathRegex aRegex = XPathRegex.compile ("(a(b)(?:c(d))[(]e)(f)", null);
    assertEquals (4, aRegex.getGroupCount ());
    assertEquals (0, aRegex.getParentGroup (1));
    assertEquals (1, aRegex.getParentGroup (2));
    assertEquals (1, aRegex.getParentGroup (3));
    assertEquals (0, aRegex.getParentGroup (4));
    assertEquals (0, XPathRegex.compile ("(a)", "q").getGroupCount ());
    assertTrue (XPathRegex.compile ("(a)", "q").isLiteral ());
  }

  @Test
  public void testEmptyString ()
  {
    assertTrue (XPathRegex.compile ("a*", null).matchesEmptyString ());
    assertTrue (XPathRegex.compile ("^", null).matchesEmptyString ());
    assertFalse (XPathRegex.compile ("a", null).matchesEmptyString ());
  }

  @Test
  public void testXsdVersions ()
  {
    // XSD 1.0: a '-' after a range is only allowed at the end
    try
    {
      XPathRegex.compile ("[a-c-1-4]", null, false);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("FORX0002", ex.getCode ());
    }
    XPathRegex.compile ("[a-c-]", null, false);
    XPathRegex.compile ("[a-z--[b]]", null, false);
    // XSD 1.1 (default): a literal
    assertTrue (XPathRegex.compile ("^[a-c-1-4]+$", null, true).getPattern ().matcher ("-2b").matches ());
    assertTrue (XPathRegex.compile ("^[a-c-1-4]+$", null).getPattern ().matcher ("-2b").matches ());
  }

  @Test
  public void testStripWhitespace ()
  {
    assertEquals ("hello\\sworld", RegexTranslator.stripWhitespace ("hello\\ sworld"));
    assertEquals ("a[ b]c", RegexTranslator.stripWhitespace (" a [ b] c "));
  }

  @Test
  public void testCaseVariants ()
  {
    final int [] aK = CaseVariants.getVariants ('k');
    assertEquals (3, aK.length);
    assertEquals (0, CaseVariants.getVariants ('1').length);
    // Dotted and dotless i are not folded together with i
    for (final int c : CaseVariants.getVariants ('i'))
      assertTrue (c == 'i' || c == 'I');
  }

  @Test
  public void testBlocks ()
  {
    assertEquals (0x0000, UnicodeBlocks.getBlockRanges ("BasicLatin")[0]);
    // XSD 1.0 alias of a renamed block
    assertEquals (0x0370, UnicodeBlocks.getBlockRanges ("Greek")[0]);
    // Unicode 9.0 block
    assertEquals (0x1E900, UnicodeBlocks.getBlockRanges ("Adlam")[0]);
    assertEquals (0, UnicodeBlocks.getBlockRanges ("HighSurrogates").length);
    assertEquals (6, UnicodeBlocks.getBlockRanges ("PrivateUse").length);
    assertEquals (null, UnicodeBlocks.getBlockRanges ("Klingon"));
  }

  @Test
  public void testBuiltInCategoryPattern ()
  {
    final StringBuilder aASCII = new StringBuilder ();
    while (aASCII.length () < 1024)
      aASCII.append ("Hello, World! 42 $name (x) _a-b\t\n");
    // U+0897 is unassigned in Unicode 15.0 and a mark since Unicode 16.0 (JDK 24)
    final String [] aInputs = { aASCII.toString (),
                                "a\u0897b 1",
                                "\u0897",
                                "x\uD83D\uDE00y",
                                "Gr\u00FC\u00DFe \u0663",
                                "" };
    final String [] aPatterns = { "\\w",
                                  "\\W*",
                                  "\\d+",
                                  "\\D",
                                  "\\p{L}+",
                                  "\\P{C}",
                                  "\\p{Cn}",
                                  "[\\p{Nd}a-c]",
                                  "[^\\w]",
                                  "(\\W*)\\$name(\\W*)",
                                  "(\\w)\\1" };
    final boolean bDifferences = UnicodeCategories.getDifferenceCount () > 0;
    for (final String sPattern : aPatterns)
    {
      final XPathRegex aRegex = XPathRegex.compile (sPattern, null);
      for (final String sInput : aInputs)
      {
        final Pattern aChosen = aRegex.getPattern (sInput);
        // The built-in classes only for an input without a changed category
        assertEquals (sPattern,
                      bDifferences && !UnicodeCategories.containsDifference (sInput),
                      aChosen != aRegex.getPattern ());
        assertEquals (sPattern, _getAllMatches (aRegex.getPattern (), sInput), _getAllMatches (aChosen, sInput));
      }
    }
    // Without a category class there is one pattern only
    final XPathRegex aRegex = XPathRegex.compile ("a+b", null);
    assertSame (aRegex.getPattern (), aRegex.getPattern (aASCII.toString ()));
  }
}
