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

import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.spec.SpecTestHelper.SpecCase;

/**
 * Port of go-xslt's <code>fn_format_datetime_test.go</code>: fn:format-date,
 * fn:format-time and fn:format-dateTime (F&amp;O 3.1 §9.8).
 *
 * @author Philip Helger
 */
public final class FnFormatDatetimeSpecTest
{
  // The values used by the examples of F&O 3.1 section 9.8.5
  private static final String D = "xs:date('2002-12-31')";
  private static final String T = "xs:time('15:58:45.762+02:00')";
  private static final String DT = "xs:dateTime('2002-12-31T15:58:45.762+02:00')";

  /**
   * The Gregorian examples of F&amp;O 3.1 §9.8.5 whose output is fully
   * determined (no implementation-defined names beyond English/German month and
   * day names).
   */
  @Test
  public void testFormatDTSpecExamples ()
  {
    run (ok ("format-date(" + D + ", \"[Y0001]-[M01]-[D01]\")", "2002-12-31"),
        ok ("format-date(" + D + ", \"[M]-[D]-[Y]\")", "12-31-2002"),
        ok ("format-date(" + D + ", \"[D]-[M]-[Y]\")", "31-12-2002"),
        ok ("format-date(" + D + ", \"[D1] [MI] [Y]\")", "31 XII 2002"),
        ok ("format-date(" + D + ", \"[D1o] [MNn], [Y]\", \"en\", (), ())", "31st December, 2002"),
        ok ("format-date(" + D + ", \"[D01] [MN,*-3] [Y0001]\", \"en\", (), ())", "31 DEC 2002"),
        ok ("format-date(" + D + ", \"[MNn] [D], [Y]\", \"en\", (), ())", "December 31, 2002"),
        ok ("format-date(" + D + ", \"[D] [MNn], [Y]\", \"de\", (), ())", "31 Dezember, 2002"),
        ok ("format-date(" + D + ", \"[[[Y0001]-[M01]-[D01]]]\")", "[2002-12-31]"),
        ok ("format-date(" + D + ", \"[Dwo] [MNn]\", \"de\", (), ())", "einunddreißigste Dezember"),
        ok ("format-time(" + T + ", \"[h]:[m01] [PN]\", \"en\", (), ())", "3:58 PM"),
        ok ("format-time(" + T + ", \"[h]:[m01]:[s01] [Pn]\", \"en\", (), ())", "3:58:45 pm"),
        ok ("format-time(" + T + ",\"[H01]:[m01]\")", "15:58"),
        ok ("format-time(" + T + ",\"[H01]:[m01]:[s01].[f001]\")", "15:58:45.762"),
        ok ("format-time(" + T + ",\"[H01]:[m01]:[s01] [z,6-6]\", \"en\", (), ())", "15:58:45 GMT+02:00"),
        ok ("format-dateTime(" + DT + ", \"[h].[m01][Pn] on [FNn], [D1o] [MNn]\")", "3.58pm on Tuesday, 31st December"),
        ok ("format-dateTime(" + DT + ", \"[M01]/[D01]/[Y0001] at [H01]:[m01]:[s01]\")", "12/31/2002 at 15:58:45"));
  }

  /**
   * The timezone table of F&amp;O 3.1 §9.8.4.6 (time 12:00:00 at each of the
   * five offsets), plus its no-timezone rules.
   */
  @Test
  public void testFormatDTTimezones ()
  {
    final String [] aOffsets = { "-10:00", "-05:00", "+00:00", "+05:30", "+13:00" };
    final String [] [] aTable = { { "[Z]", "-10:00", "-05:00", "+00:00", "+05:30", "+13:00" },
                                  { "[Z0]", "-10", "-5", "+0", "+5:30", "+13" },
                                  { "[Z0:00]", "-10:00", "-5:00", "+0:00", "+5:30", "+13:00" },
                                  { "[Z00:00]", "-10:00", "-05:00", "+00:00", "+05:30", "+13:00" },
                                  { "[Z0000]", "-1000", "-0500", "+0000", "+0530", "+1300" },
                                  { "[Z00:00t]", "-10:00", "-05:00", "Z", "+05:30", "+13:00" },
                                  { "[z]", "GMT-10:00", "GMT-05:00", "GMT+00:00", "GMT+05:30", "GMT+13:00" },
                                  { "[ZZ]", "W", "R", "Z", "+05:30", "+13:00" } };
    final ICommonsList <SpecCase> aCases = new CommonsArrayList <> ();
    for (final String [] aRow : aTable)
      for (int i = 0; i < aOffsets.length; ++i)
        aCases.add (ok ("format-time(xs:time('12:00:00" + aOffsets[i] + "'), '" + aRow[0] + "')", aRow[i + 1]));
    aCases.addAll (// "If the date/time value to be formatted does not include a timezone
                    // offset, then the timezone component specifier is generally ignored
                    // ... The exception is ... military timezones (format ZZ) ... J".
                    ok ("format-time(xs:time('12:00:00'), '[H01][Z][z]')", "12"),
                    ok ("format-time(xs:time('12:00:00'), '[ZZ]')", "J"),
                    // Military letters A..I, K..M east, N..Y west (§9.8.4.6).
                    ok ("format-time(xs:time('12:00:00+01:00'), '[ZZ]')", "A"),
                    ok ("format-time(xs:time('12:00:00+09:00'), '[ZZ]')", "I"),
                    ok ("format-time(xs:time('12:00:00+10:00'), '[ZZ]')", "K"),
                    ok ("format-time(xs:time('12:00:00+12:00'), '[ZZ]')", "M"),
                    ok ("format-time(xs:time('12:00:00-01:00'), '[ZZ]')", "N"),
                    ok ("format-time(xs:time('12:00:00-12:00'), '[ZZ]')", "Y"),
                    // Three or four digits, no separator: hours and minutes run together.
                    ok ("format-time(xs:time('12:00:00-05:00'), '[Z001]')", "-500"),
                    // A separator other than ':' is used as given ("01.01" → +10.30).
                    ok ("format-time(xs:time('12:00:00+10:30'), '[Z01.01]')", "+10.30"),
                    // The timezone of a dateTime and of a date is formatted the same way.
                    ok ("format-dateTime(xs:dateTime('2002-12-31T12:00:00-05:00'), '[Z0000]')", "-0500"),
                    ok ("format-date(xs:date('2002-12-31Z'), '[Z00:00t]')", "Z"),
                    // [ZN] with an IANA name in $place: the zone's name (§9.8.4.6
                    // table, last row: 12:00 at +00:00 in winter is 07:00 EST).
                    ok ("format-dateTime(xs:dateTime('2002-01-15T12:00:00Z'), '[H00]:[m00] [ZN]', 'en', (), 'America/New_York')", "07:00 EST"),
                    // $place moves the value into the zone, DST included (§9.8.4.8).
                    ok ("format-dateTime(xs:dateTime('2015-08-15T12:00:00Z'), '[H01]:[m01] [Z]', 'en', (), 'America/New_York')", "08:00 -04:00"));
    run (aCases.toArray (new SpecCase [0]));
  }

  /**
   * The individual component specifiers and the presentation/width modifiers of
   * F&amp;O 3.1 §9.8.4.1-9.8.4.5.
   */
  @Test
  public void testFormatDTComponents ()
  {
    run (// d = day in year.
        ok ("format-date(xs:date('2002-12-31'), '[d]')", "365"),
        ok ("format-date(xs:date('2004-12-31'), '[d]')", "366"),
        ok ("format-date(xs:date('2004-02-01'), '[d001]')", "032"),
        // F/W in the ISO calendar: Monday = 1; week 1 contains the first
        // Thursday, so 2002-12-31 is in week 1 (of 2003) (§9.8.4.8).
        ok ("format-date(xs:date('2002-12-31'), '[F1]', 'en', 'ISO', ())", "2"),
        ok ("format-date(xs:date('2002-12-29'), '[F1]', 'en', 'ISO', ())", "7"),
        ok ("format-date(xs:date('2002-12-31'), '[W]', 'en', 'ISO', ())", "1"),
        ok ("format-date(xs:date('2005-01-02'), '[W]', 'en', 'ISO', ())", "53"),
        ok ("format-date(xs:date('2013-03-21'), '[W01]', 'en', 'ISO', ())", "12"),
        // w: the week belongs to the month of its Thursday (§9.8.4.8 example:
        // 29 January 2013 and 1 February 2013 are both week 5 of January).
        ok ("format-date(xs:date('2013-01-29'), '[w]')", "5"),
        ok ("format-date(xs:date('2013-02-01'), '[w]')", "5"),
        ok ("format-date(xs:date('2013-02-04'), '[w]')", "1"),
        ok ("format-date(xs:date('2013-01-07'), '[w]')", "2"),
        // Day of week by name, with widths (§9.8.4.3: a name longer than
        // the maximum width is abbreviated or truncated to fit).
        ok ("format-date(xs:date('2002-12-31'), '[FNn]')", "Tuesday"),
        ok ("format-date(xs:date('2002-12-31'), '[FN]')", "TUESDAY"),
        ok ("format-date(xs:date('2002-12-31'), '[Fn]')", "tuesday"),
        ok ("format-date(xs:date('2002-12-31'), '[FNn,3-3]')", "Tue"),
        ok ("format-date(xs:date('2002-12-31'), '[FNn,*-2]')", "Tu"),
        ok ("format-date(xs:date('2002-12-30'), '[FNn]')", "Monday"),
        ok ("format-date(xs:date('2003-01-05'), '[FNn]')", "Sunday"),
        // German month/day names.
        ok ("format-date(xs:date('2003-03-04'), '[FNn], [D]. [MNn] [Y]', 'de', (), ())", "Dienstag, 4. März 2003"),
        ok ("format-date(xs:date('2003-03-04'), '[MN]', 'de-DE', (), ())", "MÄRZ"),
        // Hours.
        ok ("format-time(xs:time('00:30:00'), '[H]')", "0"),
        ok ("format-time(xs:time('00:30:00'), '[H01]')", "00"),
        ok ("format-time(xs:time('00:30:00'), '[h] [Pn]')", "12 am"),
        ok ("format-time(xs:time('12:30:00'), '[h] [Pn]')", "12 pm"),
        ok ("format-time(xs:time('23:30:00'), '[h] [PN]')", "11 PM"),
        // m and s default to "01" (§9.8.4.1 table).
        ok ("format-time(xs:time('09:05:07'), '[H]:[m]:[s]')", "9:05:07"),
        ok ("format-time(xs:time('09:05:07'), '[m1]/[s1]')", "5/7"),
        // Year: modulo 10^N for a multi-digit pattern or a max width (§9.8.4.4).
        ok ("format-date(xs:date('2002-12-31'), '[Y01]')", "02"),
        ok ("format-date(xs:date('2002-12-31'), '[Y,2-2]')", "02"),
        ok ("format-date(xs:date('2002-12-31'), '[Y1,2]')", "2002"),
        ok ("format-date(xs:date('0033-12-31'), '[Y0001]')", "0033"),
        ok ("format-date(xs:date('2008-12-31'), '[Y9,999,*]')", "2,008"),
        ok ("format-date(xs:date('2002-12-31'), '[YI]')", "MMII"),
        ok ("format-date(xs:date('2002-12-31'), '[Yi]')", "mmii"),
        // Width modifier on a decimal pattern raises the mandatory digits.
        ok ("format-date(xs:date('2002-03-04'), '[D,2]-[M,2]')", "04-03"),
        ok ("format-date(xs:date('2002-03-04'), '[D01]-[M01]')", "04-03"),
        // Alphabetic numbering.
        ok ("format-date(xs:date('2002-03-04'), '[Da]/[MA]')", "d/C"),
        // Roman numbering of a month.
        ok ("format-date(xs:date('2002-04-04'), '[Mi]')", "iv"),
        // Ordinal and words for day and month (German, via $language).
        ok ("format-date(xs:date('2002-03-01'), '[Dwo] [MNn]', 'de', (), ())", "erste März"),
        ok ("format-date(xs:date('2002-03-21'), '[Dw]', 'de', (), ())", "einundzwanzig"),
        ok ("format-date(xs:date('2002-03-21'), '[DW]', 'de', (), ())", "EINUNDZWANZIG"),
        ok ("format-date(xs:date('2002-03-21'), '[DWw]', 'de', (), ())", "Einundzwanzig"),
        // Whitespace inside a variable marker is ignored (§9.8.4.1).
        ok ("format-date(xs:date('2002-12-31'), '[ Y 0001 ]-[M 01]')", "2002-12"),
        // Literal "]]" is a single ']'.
        ok ("format-date(xs:date('2002-12-31'), '[D]]]')", "31]"),
        // An unsupported format token falls back to the component's default
        // presentation ("If the implementation does not support the use of
        // the requested format token, it must use the default").
        ok ("format-date(xs:date('2002-12-31'), '[MX]')", "12"),
        // A single character after the component is the FIRST presentation
        // modifier (§9.8.4.1), so [Mo] is the unsupported token "o", which
        // falls back to the default "1" — not an ordinal.
        ok ("format-date(xs:date('2002-12-31'), '[Mo]')", "12"),
        // t/c as second modifier change nothing for decimal digits.
        ok ("format-date(xs:date('2002-12-31'), '[D1c]/[D1t]')", "31/31"),
        // Optional digit after mandatory digits is not a decimal digit
        // pattern: a syntax error in the picture.
        err ("format-date(xs:date('2002-12-31'), '[Y999#]')", "FOFD1340"),
        // Grouping separator in the year pattern; two-or-more-digit pattern
        // "99;999" has W = 5 → modulo 10^5.
        ok ("format-date(xs:date('2002-12-31'), '[Y99;999]')", "02;002"),
        // Roman numerals shorter than the minimum width are padded by
        // appending spaces (§9.8.4.3 "For other presentation modifiers").
        ok ("format-date(xs:date('2002-12-31'), '[YI,6]')", "MMII  "),
        // am/pm marker truncated to a maximum width of 1 (§9.8.4.3).
        ok ("format-time(xs:time('15:00:00'), '[PNn,1-1]')", "P"),
        // [ZN] with no identifiable zone name: fallback format +01:01
        // (§9.8.4.6).
        ok ("format-time(xs:time('12:00:00+05:00'), '[ZN]')", "+05:00"),
        // A timezone is "never shortened" by a width modifier (§9.8.4.6).
        ok ("format-time(xs:time('12:00:00+05:30'), '[z,2-2]')", "GMT+05:30"),
        // Empty input → empty sequence.
        ok ("count(format-date((), '[Y]'))", "0"),
        ok ("count(format-time((), '[H]'))", "0"),
        ok ("count(format-dateTime((), '[H]'))", "0"),
        // An untypedAtomic node value is atomized... but untypedAtomic is not
        // xs:date: only real date/time items are accepted.
        err ("format-date('2002-12-31', '[Y]')", "XPTY0004"),
        err ("format-date(xs:date('2002-12-31'), '[Y]', 5, (), ())", "XPTY0004"));
  }

  /**
   * Fractional seconds (§9.8.4.5).
   */
  @Test
  public void testFormatDTFractional ()
  {
    run (// [f1] keeps every digit of the value; [f001] three; [f01] two,
        // truncated rather than rounded.
        ok ("format-time(xs:time('10:00:00.7629'), '[f]')", "7629"),
        ok ("format-time(xs:time('10:00:00.7629'), '[f1]')", "7629"),
        ok ("format-time(xs:time('10:00:00.7629'), '[f001]')", "762"),
        ok ("format-time(xs:time('10:00:00.7699'), '[f01]')", "76"),
        ok ("format-time(xs:time('10:00:00.5'), '[f001]')", "500"),
        // "If exactly one digit is required, ... [f1,1-1]".
        ok ("format-time(xs:time('10:00:00.7629'), '[f1,1-1]')", "7"),
        // A minimum width pads with mandatory digits.
        ok ("format-time(xs:time('10:00:00.762'), '[f,6-*]')", "762000"),
        // Optional digits: [f0#] has at most two digits, at least one.
        ok ("format-time(xs:time('10:00:00.5'), '[f0#]')", "5"),
        ok ("format-time(xs:time('10:00:00.567'), '[f0#]')", "56"),
        // Grouping separator in the fractional pattern (§9.8.4.5's algorithm:
        // 25.8235 with 999'### → 823'5).
        ok ("format-time(xs:time('10:00:25.8235'), \"[f999'###]\")", "823'5"),
        // A zero fraction under [f1] is "0".
        ok ("format-time(xs:time('10:00:00'), '[f1]')", "0"),
        // Non-ASCII digit family: Arabic-Indic digits.
        ok ("format-time(xs:time('10:00:00.25'), '[f" + "٠٠" + "]')", "٢٥"));
  }

  /**
   * FOFD1340 (bad picture syntax, §9.8.4.1/9.8.4.2, bad $calendar, §9.8.4.8) and
   * FOFD1350 (component not available, §9.8.4.1).
   */
  @Test
  public void testFormatDTErrors ()
  {
    run (err ("format-date(xs:date('2002-12-31'), '[Y')", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[]')", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[X]')", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[Y,0]')", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[Y,3-2]')", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[Y,1-0]')", "FOFD1340"),
        err ("format-time(xs:time('10:00:00.5'), '[f#9]')", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[H]')", "FOFD1350"),
        err ("format-date(xs:date('2002-12-31'), '[m]')", "FOFD1350"),
        err ("format-date(xs:date('2002-12-31'), '[f]')", "FOFD1350"),
        err ("format-date(xs:date('2002-12-31'), '[P]')", "FOFD1350"),
        err ("format-time(xs:time('10:00:00'), '[Y]')", "FOFD1350"),
        err ("format-time(xs:time('10:00:00'), '[D]')", "FOFD1350"),
        err ("format-time(xs:time('10:00:00'), '[F]')", "FOFD1350"),
        err ("format-time(xs:time('10:00:00'), '[W]')", "FOFD1350"),
        // $calendar: not an EQName, or a no-namespace name not in the list.
        err ("format-date(xs:date('2002-12-31'), '[Y]', 'en', '1AD', ())", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[Y]', 'en', 'XYZ', ())", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[Y]', 'en', 'Q{http://x', ())", "FOFD1340"),
        err ("format-date(xs:date('2002-12-31'), '[Y]', 'en', '1x:AD', ())", "FOFD1340"));
  }

  /**
   * The $language/$calendar arguments (§9.8.4.8): AD and ISO are supported as
   * such; a recognised but unsupported calendar, and an unsupported language,
   * fall back - and the output must then identify what was actually used.
   */
  @Test
  public void testFormatDTCalendarLanguage ()
  {
    run (ok ("format-date(xs:date('2002-12-31'), '[Y]', 'en', 'AD', ())", "2002"),
        ok ("format-date(xs:date('2002-12-31'), '[Y]', 'en', 'ISO', ())", "2002"),
        ok ("format-date(xs:date('2002-12-31'), '[Y]', 'en', (), ())", "2002"));
    for (final String sExpr : new String [] { "format-date(xs:date('2002-12-31'), '[D] [MNn] [Y]', 'en', 'OS', ())",
                                              "format-date(xs:date('2002-12-31'), '[D] [MNn] [Y]', 'en', 'Q{http://example.com/cal}mine', ())" })
    {
      // The fallback must identify the calendar actually used.
      final String sGot = SpecTestHelper.str (sExpr);
      assertTrue (sExpr + " = " + sGot, sGot.startsWith ("[Calendar:"));
    }
    // Swedish is not supported: the result must identify the language
    // actually used, and the names come from that language.
    final String sGot = SpecTestHelper.str ("format-date(xs:date('2002-12-31'), '[FNn] [D] [MNn] [Y]', 'sv', (), ())");
    assertTrue (sGot, sGot.startsWith ("[Language:") && sGot.endsWith ("Tuesday 31 December 2002"));
    // A purely numeric picture needs no language-dependent data, so the
    // same unsupported language changes nothing.
    assertEquals ("31/12/2002", SpecTestHelper.str ("format-date(xs:date('2002-12-31'), '[D]/[M]/[Y]', 'sv', (), ())"));
  }

  /**
   * The implementation-defined E and C components (§9.8.4.7), only for what
   * the spec does fix: they are names, and the era of a BC year differs from
   * that of an AD year.
   */
  @Test
  public void testFormatDTEraCalendarNames ()
  {
    final String sAD = SpecTestHelper.str ("format-date(xs:date('2002-12-31'), '[E]')");
    final String sBC = SpecTestHelper.str ("format-date(xs:date('-0043-03-13'), '[E]')");
    final String sCal = SpecTestHelper.str ("format-date(xs:date('2002-12-31'), '[C]')");
    assertFalse (sAD.isEmpty ());
    assertFalse (sBC.isEmpty ());
    assertNotEquals (sAD, sBC);
    assertFalse (sCal.isEmpty ());
    // The year itself is an absolute value (§9.8.4.1 "year (absolute value)").
    assertEquals ("43", SpecTestHelper.str ("format-date(xs:date('-0043-03-13'), '[Y]')"));
  }
}
