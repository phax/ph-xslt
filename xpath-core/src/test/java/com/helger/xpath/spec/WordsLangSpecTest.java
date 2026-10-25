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

import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

/**
 * Port of go-xslt's <code>words_lang_spec_test.go</code>: German, French and
 * Italian spellout through fn:format-integer (F&amp;O 3.1 §4.6.1).
 * <code>TestWordsLangSpellNumber</code> is not ported: it tests go-xslt's
 * internal <code>SpellNumber</code> function, whose contract has no Java
 * counterpart.
 *
 * @author Philip Helger
 */
public final class WordsLangSpecTest
{
  /**
   * The w/W/Ww tokens and the "o" ordinal modifier with $lang. Expected words
   * are the standard (CLDR) spellings.
   */
  @Test
  public void testWordsLangFormatInteger ()
  {
    run (// German
        ok ("format-integer(30, 'W', 'de')", "DREISSIG"),
        ok ("format-integer(21, 'Ww', 'de')", "Einundzwanzig"),
        ok ("format-integer(-21, 'w', 'de')", "-einundzwanzig"),
        ok ("format-integer(1100, 'w', 'de')", "eintausendeinhundert"),
        ok ("format-integer(3000000, 'w', 'de')", "drei Millionen"),
        ok ("format-integer(1000000000, 'w', 'de')", "eine Milliarde"),
        ok ("format-integer(2000000000, 'w', 'de')", "zwei Milliarden"),
        ok ("format-integer(1000000001, 'w', 'de')", "eine Milliarde eins"),
        ok ("format-integer(31, 'w;o', 'de-AT')", "einunddreißigste"),
        ok ("format-integer(2, 'w;o', 'de')", "zweite"),
        // French
        ok ("format-integer(30, 'w', 'fr')", "trente"),
        ok ("format-integer(32, 'w', 'fr')", "trente-deux"),
        ok ("format-integer(72, 'w', 'fr')", "soixante-douze"),
        ok ("format-integer(300, 'w', 'fr')", "trois cents"),
        ok ("format-integer(180, 'w', 'fr')", "cent quatre-vingts"),
        ok ("format-integer(2000, 'w', 'fr')", "deux mille"),
        ok ("format-integer(1000000, 'w', 'fr')", "un million"),
        ok ("format-integer(1000000000, 'w', 'fr')", "un milliard"),
        ok ("format-integer(2000000000, 'w', 'fr')", "deux milliards"),
        ok ("format-integer(21, 'W', 'fr-CA')", "VINGT ET UN"),
        ok ("format-integer(3, 'w;o', 'fr')", "troisième"),
        ok ("format-integer(11, 'w;o', 'fr')", "onzième"),
        ok ("format-integer(200, 'w;o', 'fr')", "deux centième"),
        ok ("format-integer(2000, 'w;o', 'fr')", "deux millième"),
        ok ("format-integer(1000000, 'w;o', 'fr')", "un millionième"),
        // Italian
        ok ("format-integer(21, 'W', 'it')", "VENTUNO"),
        ok ("format-integer(33, 'w', 'it')", "trentatré"),
        ok ("format-integer(2023, 'w', 'it')", "duemilaventitré"),
        ok ("format-integer(1000000000, 'w', 'it')", "un miliardo"),
        ok ("format-integer(3000000000, 'w', 'it')", "tre miliardi"),
        ok ("format-integer(1000, 'w;o', 'it')", "millesimo"),
        ok ("format-integer(2000, 'w;o', 'it')", "duemillesimo"),
        ok ("format-integer(33, 'w;o', 'it')", "trentatreesimo"),
        ok ("format-integer(3, 'w;o', 'it')", "terzo"));
  }
}
