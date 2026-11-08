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

import org.junit.Test;

/**
 * Port of go-xslt's <code>regex_blocks_test.go</code>: block escapes (XSD 1.1
 * Part 2 §G.4.2.3). <code>TestRxXSDBlockKnown</code> is not ported: it tests
 * go-xslt's XSD pattern-facet validator, XSD validation is left to the JDK
 * here.
 *
 * @author Philip Helger
 */
public final class RegexBlocksSpecTest
{
  /**
   * The \\p{IsX} / \\P{IsX} block escapes inside and outside a character class:
   * current Unicode block names, the superseded Unicode 3.1 names §G.4.2.3
   * asks processors to keep, and the surrogate blocks, which contain no
   * character at all.
   */
  @Test
  public void testRxBlockEscapes ()
  {
    RegexRuneSpecHelper.runRunes (r ("\\p{IsBasicLatin}", 0x0061, true),
                                 r ("\\p{IsBasicLatin}", 0x00E9, false),
                                 r ("\\P{IsBasicLatin}", 0x00E9, true),
                                 r ("\\P{IsBasicLatin}", 0x0061, false),
                                 r ("[\\p{IsBasicLatin}]", 0x0061, true),
                                 r ("[\\P{IsBasicLatin}]", 0x00E9, true),
                                 r ("[\\P{IsBasicLatin}]", 0x0061, false),
                                 r ("[\\P{IsBasicLatin}a]", 0x0061, true),
                                 r ("[\\p{IsLatin-1Supplement}x]", 0x00E9, true),
                                 r ("[\\p{IsLatin-1Supplement}x]", 0x0078, true),
                                 r ("[\\p{IsLatin-1Supplement}x]", 0x0079, false),
                                 // A block that is newer than Unicode 3.1 (Adlam: U+1E900..U+1E95F).
                                 r ("\\p{IsAdlam}", 0x1E900, true),
                                 r ("\\p{IsAdlam}", 0x1E95F, true),
                                 r ("\\p{IsAdlam}", 0x1E8FF, false),
                                 // Superseded names keep their Unicode 3.1 ranges.
                                 r ("\\p{IsGreek}", 0x03B1, true),
                                 r ("\\p{IsGreek}", 0x03FF, true),
                                 r ("\\p{IsGreek}", 0x0061, false),
                                 r ("\\p{IsCombiningMarksforSymbols}", 0x20D0, true),
                                 r ("\\p{IsCombiningMarksforSymbols}", 0x20FF, true),
                                 r ("\\p{IsCombiningMarksforSymbols}", 0x2100, false),
                                 r ("\\p{IsPrivateUse}", 0xE000, true),
                                 r ("\\p{IsPrivateUse}", 0xF8FF, true),
                                 r ("\\p{IsPrivateUse}", 0xF0000, true),
                                 r ("\\p{IsPrivateUse}", 0x100000, true),
                                 r ("\\p{IsPrivateUse}", 0xF900, false),
                                 r ("\\P{IsPrivateUse}", 0xF0000, false),
                                 r ("\\P{IsPrivateUse}", 0x0061, true),
                                 // Surrogate blocks hold no scalar value: \p matches nothing, \P
                                 // everything.
                                 r ("\\p{IsHighSurrogates}", 0x0061, false),
                                 r ("\\p{IsHighSurrogates}", 0x10000, false),
                                 r ("\\P{IsHighSurrogates}", 0x0061, true),
                                 r ("\\P{IsLowSurrogates}", 0x10FFFF, true),
                                 r ("[\\p{IsHighSurrogates}a]", 0x0061, true),
                                 r ("[\\p{IsHighSurrogates}a]", 0x0062, false),
                                 r ("[\\P{IsHighPrivateUseSurrogates}]", 0x0061, true));
    // Under 'i' a block escape is unaffected (F&O 3.1 §5.6.2 'i' changes
    // only characters and ranges); BasicLatin contains both cases anyway.
    assertTrue (RegexRuneSpecHelper.find ("^\\p{IsBasicLatin}+$", "i", "aZ"));
    assertFalse (RegexRuneSpecHelper.find ("^\\p{IsBasicLatin}+$", "i", "\u00E9"));
  }
}
