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
package com.helger.xpath.spec;

import static com.helger.xpath.spec.RegexRuneSpecHelper.r;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.regex.XPathRegex;

/**
 * Port of go-xslt's <code>regex_subtraction_test.go</code>: character class
 * subtraction (XSD 1.1 Part 2 §G.4.2.4).
 *
 * @author Philip Helger
 */
public final class RegexSubtractionSpecTest
{
  /**
   * Character-class subtraction (charClassSub: the set of the base group minus
   * the set of the subtracted class), including negated base and negated
   * subtrahend groups and the escapes a group may hold.
   */
  @Test
  public void testRxClassSubtraction ()
  {
    RegexRuneSpecHelper.runRunes (r ("[a-z-[aeiou]]", 0x0062, true),
                                 r ("[a-z-[aeiou]]", 0x0061, false),
                                 r ("[a-z-[aeiou]]", 0x0041, false),
                                 // [^B-[S]] = (not B) minus S.
                                 r ("[^a-z-[0-4]]", 0x0061, false),
                                 r ("[^a-z-[0-4]]", 0x0031, false),
                                 r ("[^a-z-[0-4]]", 0x0037, true),
                                 r ("[^a-[b]]", 0x0061, false),
                                 r ("[^a-[b]]", 0x0062, false),
                                 r ("[^a-[b]]", 0x0063, true),
                                 // [B-[^S]] = B intersected with S.
                                 r ("[a-z-[^aeiou]]", 0x0061, true),
                                 r ("[a-z-[^aeiou]]", 0x0062, false),
                                 r ("[a-z-[^aeiou]]", 0x0031, false),
                                 // [^B-[^S]] = (not B) intersected with S.
                                 r ("[^a-c-[^a-e]]", 0x0064, true),
                                 r ("[^a-c-[^a-e]]", 0x0061, false),
                                 r ("[^a-c-[^a-e]]", 0x0066, false),
                                 // An empty difference matches nothing.
                                 r ("[a-[a]]", 0x0061, false),
                                 r ("[a-[a]]", 0x0062, false),
                                 r ("[^a-z-[^aeiou]]", 0x0061, false),
                                 r ("[^a-z-[^aeiou]]", 0x0031, false),
                                 r ("[a-z-[^\\n]]", 0x0061, false),
                                 // Single-character escapes in either operand.
                                 r ("[\\*\\+\\?-[\\+]]", 0x002A, true),
                                 r ("[\\*\\+\\?-[\\+]]", 0x002B, false),
                                 r ("[\\*\\+\\?-[\\+]]", 0x003F, true),
                                 r ("[a\\]-[\\]]]", 0x0061, true),
                                 r ("[a\\]-[\\]]]", 0x005D, false),
                                 r ("[\\n\\t-[\\n]]", 0x0009, true),
                                 r ("[\\n\\t-[\\n]]", 0x000A, false),
                                 r ("[\\(\\)\\{\\}\\.\\|\\\\\\^-[\\.]]", 0x007B, true),
                                 r ("[\\(\\)\\{\\}\\.\\|\\\\\\^-[\\.]]", 0x005C, true),
                                 r ("[\\(\\)\\{\\}\\.\\|\\\\\\^-[\\.]]", 0x002E, false),
                                 // \i / \c (XML NameStartChar / NameChar) as operands.
                                 r ("[\\i-[:]]", 0x0061, true),
                                 r ("[\\i-[:]]", 0x005F, true),
                                 r ("[\\i-[:]]", 0x003A, false),
                                 r ("[\\i-[:]]", 0x0031, false),
                                 r ("[\\c-[\\i]]", 0x0031, true),
                                 r ("[\\c-[\\i]]", 0x002D, true),
                                 r ("[\\c-[\\i]]", 0x002E, true),
                                 r ("[\\c-[\\i]]", 0x0061, false),
                                 r ("[\\c-[\\i]]", 0x003A, false),
                                 // Block and category escapes as operands.
                                 r ("[\\p{IsBasicLatin}-[a-z]]", 0x0041, true),
                                 r ("[\\p{IsBasicLatin}-[a-z]]", 0x0061, false),
                                 r ("[\\p{IsBasicLatin}-[a-z]]", 0x00E9, false),
                                 r ("[\\P{IsBasicLatin}-[é]]", 0x00FC, true),
                                 r ("[\\P{IsBasicLatin}-[é]]", 0x00E9, false),
                                 r ("[\\P{IsBasicLatin}-[é]]", 0x0061, false),
                                 r ("[\\p{L}-[a-z]]", 0x0041, true),
                                 r ("[\\p{L}-[a-z]]", 0x0061, false),
                                 r ("[\\p{L}-[a-z]]", 0x0031, false),
                                 r ("[\\P{Lu}-[a]]", 0x0062, true),
                                 r ("[\\P{Lu}-[a]]", 0x0061, false),
                                 r ("[\\P{Lu}-[a]]", 0x0042, false),
                                 // A surrogate block holds no character, so nothing is left.
                                 r ("[\\p{IsHighSurrogates}-[a]]", 0x0061, false),
                                 r ("[\\p{IsHighSurrogates}-[a]]", 0x0062, false),
                                 r ("[\\r\\t-[\\t]]", 0x000D, true),
                                 r ("[\\r\\t-[\\t]]", 0x0009, false),
                                 r ("[\\p{Cn}-[a]]", 0x0378, true), // unassigned
                                 r ("[\\p{Cn}-[a]]", 0x0007, false),
                                 r ("[\\p{C}-[a]]", 0x0007, true),
                                 // Multi-character escapes as operands (XSD's Unicode semantics:
                                 // \d = \p{Nd}, \s = [#x20#x9#xA#xD], \w = not P, Z or C).
                                 r ("[\\D-[a]]", 0x0062, true),
                                 r ("[\\D-[a]]", 0x0061, false),
                                 r ("[\\D-[a]]", 0x0035, false),
                                 r ("[\\d-[5]]", 0x0663, true), // ARABIC-INDIC DIGIT THREE is Nd
                                 r ("[\\S-[a]]", 0x0062, true),
                                 r ("[\\S-[a]]", 0x0020, false),
                                 r ("[\\S-[a]]", 0x0061, false),
                                 r ("[\\W-[!]]", 0x003F, true),
                                 r ("[\\W-[!]]", 0x0021, false),
                                 r ("[\\W-[!]]", 0x0061, false),
                                 r ("[\\w-[b]]", 0x00E9, true),
                                 r ("[\\w-[b]]", 0x005F, false));
  }

  /**
   * A range endpoint written as a single-character escape denotes that
   * character (XSD Part 2 §G.4.2.4, seRange ::= charOrEsc '-' charOrEsc), so
   * [\\t-\\r] is #x9..#xD.
   */
  @Test
  public void testRxClassSubtractionEscapedRangeEnd ()
  {
    RegexRuneSpecHelper.runRunes (r ("[\\t-\\r-[\\n]]", 0x0009, true),
                                 r ("[\\t-\\r-[\\n]]", 0x000D, true),
                                 r ("[\\t-\\r-[\\n]]", 0x000A, false),
                                 r ("[\\t-\\r-[\\n]]", 0x0061, false));
  }

  /**
   * \\I and \\C (the complements of NameStartChar and NameChar, XSD Part 2
   * §G.4.2.5) are valid operands of a subtraction too.
   */
  @Test
  public void testRxClassSubtractionComplementEscapes ()
  {
    RegexRuneSpecHelper.runRunes (r ("[\\I-[a]]", 0x0031, true),
                                 r ("[\\I-[a]]", 0x002D, true),
                                 r ("[\\I-[a]]", 0x0062, false),
                                 r ("[\\C-[a]]", 0x0020, true),
                                 r ("[\\C-[a]]", 0x0062, false));
  }

  /**
   * go-xslt checks its XSD pattern-facet entry point here; there is none in
   * this port (XSD validation is left to the JDK), so the same checks run
   * against the XPath entry point with the XSD 1.0 class grammar. The
   * back-reference part is left out: XPath allows back-references.
   */
  @Test
  public void testRxClassSubtractionLenient ()
  {
    final XPathRegex aRegex = XPathRegex.compile ("[a-z-[aeiou]]", "", false);
    assertTrue (aRegex.getPattern ().matcher ("b").find ());
    assertFalse (aRegex.getPattern ().matcher ("e").find ());
    for (final String sPattern : new String [] { "[\\p{Xx}-[a]]", "[abc", "[a-[b]c]", "[a-z-[b" })
      try
      {
        XPathRegex.compile (sPattern, "", false);
        fail (sPattern + ": want an error");
      }
      catch (final XPathException ex)
      {
        // expected
      }
  }
}
