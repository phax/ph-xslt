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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.xpath.XPathException;

/**
 * Test class for class {@link XPathRegex}.
 *
 * @author Philip Helger
 */
public final class XPathRegexTest
{
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
    // XSD 1.0 (default): a '-' after a range is only allowed at the end
    try
    {
      XPathRegex.compile ("[a-c-1-4]", null);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("FORX0002", ex.getCode ());
    }
    XPathRegex.compile ("[a-c-]", null);
    XPathRegex.compile ("[a-z--[b]]", null);
    // XSD 1.1: a literal
    assertTrue (XPathRegex.compile ("^[a-c-1-4]+$", null, true).getPattern ().matcher ("-2b").matches ());
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
}
