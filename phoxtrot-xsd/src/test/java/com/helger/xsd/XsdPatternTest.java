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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;

/**
 * The pattern facet: the XSD grammar check and the whole-value matcher.
 *
 * @author Philip Helger
 */
public final class XsdPatternTest
{
  private static boolean _isValid (final String sPattern, final EXsdVersion eVersion)
  {
    try
    {
      XsdPattern.validateSyntax (sPattern, eVersion);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  private static boolean _matches (final String sPattern, final String sValue)
  {
    return XsdPattern.compile (sPattern, EXsdVersion.V11).matches (sValue);
  }

  @Test
  public void testGrammarBothVersions ()
  {
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      assertTrue (_isValid ("a|b", eVersion));
      assertTrue (_isValid ("\\i\\c*", eVersion));
      assertTrue (_isValid ("[a-z-[aeiou]]+", eVersion));
      assertTrue (_isValid ("\\p{L}\\P{Nd}", eVersion));
      assertTrue (_isValid ("a{2,5}b{3}c{4,}", eVersion));
      assertTrue (_isValid ("\\|", eVersion));
      assertTrue (_isValid ("a||b", eVersion));
      assertTrue (_isValid ("[a-z-]", eVersion));
      assertTrue (_isValid ("^$", eVersion));
      assertFalse (_isValid ("a(b", eVersion));
      assertFalse (_isValid ("a)", eVersion));
      assertFalse (_isValid ("a*?", eVersion));
      assertFalse (_isValid ("a**", eVersion));
      assertFalse (_isValid ("*a", eVersion));
      assertFalse (_isValid ("(?:a)", eVersion));
      assertFalse (_isValid ("\\1", eVersion));
      assertFalse (_isValid ("\\b", eVersion));
      assertFalse (_isValid ("\\$", eVersion));
      assertFalse (_isValid ("a]", eVersion));
      assertFalse (_isValid ("a{2,1}", eVersion));
      assertFalse (_isValid ("a{x}", eVersion));
      assertFalse (_isValid ("[]", eVersion));
      assertFalse (_isValid ("[a-\\d]", eVersion));
      assertFalse (_isValid ("[z-a]", eVersion));
      assertFalse (_isValid ("\\p{Foo}", eVersion));
      assertFalse (_isValid ("\\p{Is}", eVersion));
      assertFalse (_isValid ("a\\", eVersion));
    }
  }

  @Test
  public void testGrammarVersionDifferences ()
  {
    // A chained range is invalid in XSD 1.0 only
    assertFalse (_isValid ("[a-c-1-4]", EXsdVersion.V10));
    assertTrue (_isValid ("[a-c-1-4]", EXsdVersion.V11));
    // An unknown block is invalid in XSD 1.0 and matches everything in 1.1
    assertFalse (_isValid ("\\p{IsNoSuchBlock}", EXsdVersion.V10));
    assertTrue (_isValid ("\\p{IsNoSuchBlock}", EXsdVersion.V11));
    assertTrue (_isValid ("\\p{IsBasicLatin}", EXsdVersion.V10));
    assertTrue (XsdPattern.compile ("\\p{IsNoSuchBlock}+", EXsdVersion.V11).matches ("x\u4e2d"));
  }

  @Test
  public void testMatching ()
  {
    assertTrue (_matches ("ab", "ab"));
    assertFalse (_matches ("ab", "xaby"));
    assertFalse (_matches ("a|b", "ab"));
    assertTrue (_matches ("a|ab", "ab"));
    // The anchors are ordinary characters
    assertTrue (_matches ("^a$", "^a$"));
    assertFalse (_matches ("^a$", "a"));
    assertTrue (_matches ("[^a]", "^"));
    // '.' excludes the line breaks
    assertTrue (_matches (".", "x"));
    assertFalse (_matches (".", "\n"));
    assertFalse (_matches (".", "\r"));
    assertTrue (_matches ("[a-z-[aeiou]]+", "bcd"));
    assertFalse (_matches ("[a-z-[aeiou]]+", "bad"));
    assertTrue (_matches ("\\i\\c*", "_x1"));
    assertFalse (_matches ("\\i\\c*", "1x"));
    assertTrue (_matches ("\\d{3}-\\d{2}", "123-45"));
    assertEquals ("a(b)", XsdPattern.compile ("a(b)", EXsdVersion.V10).getSource ());
  }

  @Test
  public void testLongValue ()
  {
    // A backtracking-heavy shape on a long value must not overflow the stack
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < 200_000; i++)
      aSB.append (i % 2 == 0 ? 'a' : 'b');
    assertTrue (_matches ("(a|b)*", aSB.toString ()));
  }
}
