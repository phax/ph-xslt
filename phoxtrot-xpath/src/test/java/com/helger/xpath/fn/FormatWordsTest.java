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
package com.helger.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Test class for class {@link FormatWords}.
 *
 * @author Philip Helger
 */
public final class FormatWordsTest
{
  private static void _check (final String sLang,
                              final long n,
                              final boolean bOrdinal,
                              final String sVariant,
                              final String sExpected)
  {
    assertEquals (sLang + " " + n + " " + bOrdinal + " " + sVariant,
                  sExpected,
                  FormatWords.spell (n, "w", bOrdinal, sLang, sVariant));
  }

  @Test
  public void testNativeLanguages ()
  {
    _check ("de", 0L, false, "", "null");
    _check ("de", 1L, false, "", "eins");
    _check ("de", 21L, false, "", "einundzwanzig");
    _check ("de", 100L, false, "", "einhundert");
    _check ("de", 101L, false, "", "einhunderteins");
    _check ("de", 1234L, false, "", "eintausendzweihundertvierunddreißig");
    _check ("de", 1_000_000L, false, "", "eine Million");
    _check ("de", 2_000_000L, false, "", "zwei Millionen");
    _check ("de", 1_234_567L, false, "", "eine Million zweihundertvierunddreißigtausendfünfhundertsiebenundsechzig");
    _check ("de", 1L, true, "", "erste");
    _check ("de", 1L, true, "-er", "erster");
    _check ("de", 3L, true, "", "dritte");
    _check ("de", 7L, true, "", "siebte");
    _check ("de", 8L, true, "", "achte");
    _check ("de", 19L, true, "", "neunzehnte");
    _check ("de", 20L, true, "%spellout-ordinal", "zwanzigste");
    _check ("de", 21L, true, "", "einundzwanzigste");
    _check ("de", 101L, true, "", "einhunderterste");
    _check ("de", 1000L, true, "", "eintausendste");
    _check ("fr", 0L, false, "", "zéro");
    _check ("fr", 21L, false, "", "vingt et un");
    _check ("fr", 71L, false, "", "soixante et onze");
    _check ("fr", 80L, false, "", "quatre-vingts");
    _check ("fr", 81L, false, "", "quatre-vingt-un");
    _check ("fr", 97L, false, "", "quatre-vingt-dix-sept");
    _check ("fr", 200L, false, "", "deux cents");
    _check ("fr", 201L, false, "", "deux cent un");
    _check ("fr", 1000L, false, "", "mille");
    _check ("fr", 80000L, false, "", "quatre-vingt mille");
    _check ("fr", 200000L, false, "", "deux cent mille");
    _check ("fr", 2_000_000L, false, "", "deux millions");
    _check ("fr", 1L, true, "", "premier");
    _check ("fr", 1L, true, "-e", "première");
    _check ("fr", 2L, true, "", "deuxième");
    _check ("fr", 4L, true, "", "quatrième");
    _check ("fr", 5L, true, "", "cinquième");
    _check ("fr", 9L, true, "", "neuvième");
    _check ("fr", 21L, true, "", "vingt et unième");
    _check ("fr", 80L, true, "", "quatre-vingtième");
    _check ("fr", 1000L, true, "", "millième");
    _check ("it", 0L, false, "", "zero");
    _check ("it", 21L, false, "", "ventuno");
    _check ("it", 23L, false, "", "ventitré");
    _check ("it", 28L, false, "", "ventotto");
    _check ("it", 180L, false, "", "centottanta");
    _check ("it", 200L, false, "", "duecento");
    _check ("it", 1000L, false, "", "mille");
    _check ("it", 2000L, false, "", "duemila");
    _check ("it", 23000L, false, "", "ventitremila");
    _check ("it", 2300L, false, "", "duemilatrecento");
    _check ("it", 1_000_000L, false, "", "un milione");
    _check ("it", 2_000_000L, false, "", "due milioni");
    _check ("it", 5L, true, "-o", "quinto");
    _check ("it", 5L, true, "-a", "quinta");
    _check ("it", 5L, true, "%spellout-ordinal-feminine", "quinta");
    _check ("it", 10L, true, "", "decimo");
    _check ("it", 11L, true, "", "undicesimo");
    _check ("it", 20L, true, "", "ventesimo");
    _check ("it", 21L, true, "", "ventunesimo");
    _check ("it", 23L, true, "", "ventitreesimo");
    _check ("it", 26L, true, "", "ventiseiesimo");
    _check ("it", 100L, true, "", "centesimo");
    _check ("it", 1000L, true, "", "millesimo");
    _check ("it", 2000L, true, "", "duemillesimo");
    _check ("it", 1_000_000L, true, "", "un milionesimo");
  }

  @Test
  public void testEnglish ()
  {
    _check ("en", 0, false, "", "zero");
    _check ("en", 21, false, "", "twenty-one");
    _check ("en", 101, false, "", "one hundred and one");
    _check ("en", 1990, false, "", "one thousand nine hundred and ninety");
    _check ("en", 2200, false, "", "two thousand two hundred");
    _check ("en", 1_000_000, false, "", "one million");
    _check ("en", 1_000_000_000_000L, false, "", "one trillion");
    _check ("en", 1990, true, "", "one thousand nine hundred and ninetieth");
    _check ("en", 2001, true, "", "two thousand and first");
    _check ("en", 12, true, "", "twelfth");
    _check ("en", 40, true, "", "fortieth");
    _check ("en", 43, true, "", "forty-third");
    _check ("en", 100, true, "", "one hundredth");
    _check (null, 7, false, "", "seven");
    // Beyond the native range everything is English
    _check ("de", 1_000_000_000_000L, false, "", "one trillion");
    assertEquals ("-five", FormatWords.spell (-5, "w", false, null, ""));
    assertEquals ("FIVE", FormatWords.spell (5, "W", false, null, ""));
    assertEquals ("Twenty-One", FormatWords.spell (21, "Ww", false, null, ""));
    assertEquals ("DREISSIG", FormatWords.spell (30, "W", false, "de", ""));
    assertEquals ("Vingt Et Un", FormatWords.spell (21, "Ww", false, "fr-CA", ""));
  }

  @Test
  public void testHelpers ()
  {
    assertEquals ("de", FormatWords.getNativeLanguage ("DE-at"));
    assertEquals ("fr", FormatWords.getNativeLanguage ("fr_CA"));
    assertNull (FormatWords.getNativeLanguage ("en"));
    assertNull (FormatWords.getNativeLanguage (null));
    assertEquals ("st", FormatWords.getOrdinalSuffix (1));
    assertEquals ("nd", FormatWords.getOrdinalSuffix (22));
    assertEquals ("rd", FormatWords.getOrdinalSuffix (103));
    assertEquals ("th", FormatWords.getOrdinalSuffix (111));
    assertEquals ("th", FormatWords.getOrdinalSuffix (-12));
    assertEquals ("Hello-World Foo", FormatWords.titleCase ("hELLO-wORLD foo"));
  }
}
