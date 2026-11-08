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

import org.junit.Test;

/**
 * Port of go-xslt's <code>fn_adjust_ietf_test.go</code>: the timezone
 * adjustment functions (F&amp;O 3.1 §9.6) and fn:parse-ietf-date (§9.9.1).
 *
 * @author Philip Helger
 */
public final class FnAdjustIetfSpecTest
{
  private static final String TZ10 = "xs:dayTimeDuration(\"-PT10H\")";

  /**
   * The examples of F&amp;O 3.1 §9.6.1-9.6.3 that give an explicit $timezone
   * (the one-argument examples assume an implicit timezone of -05:00; checked
   * separately).
   */
  @Test
  public void testAdjustToTimezone ()
  {
    run (ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00'), " + TZ10 + "))", "2002-03-07T10:00:00-10:00"),
        ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00-07:00'), " + TZ10 + "))", "2002-03-07T07:00:00-10:00"),
        ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00-07:00'), xs:dayTimeDuration(\"PT10H\")))", "2002-03-08T03:00:00+10:00"),
        ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T00:00:00+01:00'), xs:dayTimeDuration(\"-PT8H\")))", "2002-03-06T15:00:00-08:00"),
        ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00'), ()))", "2002-03-07T10:00:00"),
        ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00-07:00'), ()))", "2002-03-07T10:00:00"),
        // §9.6.2 fn:adjust-date-to-timezone
        ok ("string(adjust-date-to-timezone(xs:date(\"2002-03-07\"), " + TZ10 + "))", "2002-03-07-10:00"),
        ok ("string(adjust-date-to-timezone(xs:date(\"2002-03-07-07:00\"), " + TZ10 + "))", "2002-03-06-10:00"),
        ok ("string(adjust-date-to-timezone(xs:date(\"2002-03-07\"), ()))", "2002-03-07"),
        ok ("string(adjust-date-to-timezone(xs:date(\"2002-03-07-07:00\"), ()))", "2002-03-07"),
        // §9.6.3 fn:adjust-time-to-timezone
        ok ("string(adjust-time-to-timezone(xs:time(\"10:00:00\"), " + TZ10 + "))", "10:00:00-10:00"),
        ok ("string(adjust-time-to-timezone(xs:time(\"10:00:00-07:00\"), " + TZ10 + "))", "07:00:00-10:00"),
        ok ("string(adjust-time-to-timezone(xs:time(\"10:00:00\"), ()))", "10:00:00"),
        ok ("string(adjust-time-to-timezone(xs:time(\"10:00:00-07:00\"), ()))", "10:00:00"),
        ok ("string(adjust-time-to-timezone(xs:time(\"10:00:00-07:00\"), xs:dayTimeDuration(\"PT10H\")))", "03:00:00+10:00"),
        // The adjusted time is a proper xs:time: equal to the literal.
        ok ("adjust-time-to-timezone(xs:time(\"10:00:00-07:00\"), xs:dayTimeDuration(\"PT10H\")) eq xs:time(\"03:00:00+10:00\")", "true"),
        ok ("adjust-date-to-timezone(xs:date(\"2002-03-07-07:00\"), " + TZ10 + ") eq xs:date(\"2002-03-06-10:00\")", "true"),
        // Fractional seconds survive.
        ok ("string(adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00.5Z'), xs:dayTimeDuration('PT1H')))", "2002-03-07T11:00:00.5+01:00"),
        // Empty $arg → empty sequence.
        ok ("count(adjust-dateTime-to-timezone((), " + TZ10 + "))", "0"),
        ok ("count(adjust-date-to-timezone(()))", "0"),
        ok ("count(adjust-time-to-timezone(()))", "0"),
        // FODT0003: offset beyond ±PT14H or not an integral number of minutes.
        err ("adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00'), xs:dayTimeDuration('PT14H1M'))", "FODT0003"),
        err ("adjust-date-to-timezone(xs:date('2002-03-07'), xs:dayTimeDuration('-PT15H'))", "FODT0003"),
        err ("adjust-time-to-timezone(xs:time('10:00:00'), xs:dayTimeDuration('PT1H0M30S'))", "FODT0003"),
        err ("adjust-time-to-timezone(xs:time('10:00:00'), xs:dayTimeDuration('PT14H0M0.001S'))", "FODT0003"),
        // The extreme offsets themselves are allowed.
        ok ("string(adjust-time-to-timezone(xs:time('10:00:00Z'), xs:dayTimeDuration('-PT14H')))", "20:00:00-14:00"));
  }

  /**
   * The one-argument forms adjust to the implicit timezone (§9.6.1), so they
   * equal the two-argument form with implicit-timezone().
   */
  @Test
  public void testAdjustImplicitTimezone ()
  {
    run (ok ("let $v := xs:dateTime('2002-03-07T10:00:00-07:00') return string(adjust-dateTime-to-timezone($v)) eq string(adjust-dateTime-to-timezone($v, implicit-timezone()))", "true"),
        ok ("let $v := xs:dateTime('2002-03-07T10:00:00') return string(adjust-dateTime-to-timezone($v)) eq string(adjust-dateTime-to-timezone($v, implicit-timezone()))", "true"),
        ok ("let $v := xs:date('2002-03-07-07:00') return string(adjust-date-to-timezone($v)) eq string(adjust-date-to-timezone($v, implicit-timezone()))", "true"),
        ok ("let $v := xs:time('10:00:00-07:00') return string(adjust-time-to-timezone($v)) eq string(adjust-time-to-timezone($v, implicit-timezone()))", "true"),
        // Adjusting keeps the instant (for a value that had a timezone).
        ok ("adjust-dateTime-to-timezone(xs:dateTime('2002-03-07T10:00:00-07:00')) eq xs:dateTime('2002-03-07T10:00:00-07:00')", "true"),
        ok ("implicit-timezone() instance of xs:dayTimeDuration", "true"));
  }

  /**
   * The examples and grammar of F&amp;O 3.1 §9.9.1 fn:parse-ietf-date.
   */
  @Test
  public void testIETFDate ()
  {
    run (// The five examples of the REC.
        ok ("string(parse-ietf-date(\"Wed, 06 Jun 1994 07:29:35 GMT\"))", "1994-06-06T07:29:35Z"),
        ok ("string(parse-ietf-date(\"Wed, 6 Jun 94 07:29:35 GMT\"))", "1994-06-06T07:29:35Z"),
        ok ("string(parse-ietf-date(\"Wed Jun 06 11:54:45 EST 2013\"))", "2013-06-06T11:54:45-05:00"),
        ok ("string(parse-ietf-date(\"Sunday, 06-Nov-94 08:49:37 GMT\"))", "1994-11-06T08:49:37Z"),
        ok ("string(parse-ietf-date(\"Wed, 6 Jun 94 07:29:35 +0500\"))", "1994-06-06T07:29:35+05:00"),
        // Empty input → empty sequence.
        ok ("count(parse-ietf-date(()))", "0"),
        // The result is an xs:dateTime.
        ok ("parse-ietf-date(\"06 Jun 1994 07:29:35 GMT\") instance of xs:dateTime", "true"),
        // No dayname; no seconds; no zone (00:00 assumed).
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29\"))", "1994-06-06T07:29:00Z"),
        // Case-insensitive.
        ok ("string(parse-ietf-date(\"wed, 06 JUN 1994 07:29:35 gmt\"))", "1994-06-06T07:29:35Z"),
        // Leading/trailing and repeated whitespace (tab, newline) accepted.
        ok ("string(parse-ietf-date(\"  Wed," + "\t" + "06  Jun 1994" + "\n" + "07:29:35 UT  \"))", "1994-06-06T07:29:35Z"),
        // Fractional seconds.
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29:35.25 UTC\"))", "1994-06-06T07:29:35.25Z"),
        // Single-digit hours.
        ok ("string(parse-ietf-date(\"06 Jun 1994 7:29:35 GMT\"))", "1994-06-06T07:29:35Z"),
        // Every tzname of the translation table.
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 EDT\"))", "1994-06-06T07:29:00-04:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 CST\"))", "1994-06-06T07:29:00-06:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 CDT\"))", "1994-06-06T07:29:00-05:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 MST\"))", "1994-06-06T07:29:00-07:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 MDT\"))", "1994-06-06T07:29:00-06:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 PST\"))", "1994-06-06T07:29:00-08:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 PDT\"))", "1994-06-06T07:29:00-07:00"),
        // tzoffset: H, HH, HMM, HHMM, and with a colon.
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 -5\"))", "1994-06-06T07:29:00-05:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 +05\"))", "1994-06-06T07:29:00+05:00"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 +130\"))", "1994-06-06T07:29:00+01:30"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 -0530\"))", "1994-06-06T07:29:00-05:30"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 -5:00\"))", "1994-06-06T07:29:00-05:00"),
        // Offset with zone name: the tzname is ignored.
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29 +0100 (GMT)\"))", "1994-06-06T07:29:00+01:00"),
        // No space needed between time and timezone (S? in the grammar).
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29:35GMT\"))", "1994-06-06T07:29:35Z"),
        ok ("string(parse-ietf-date(\"06 Jun 1994 07:29:35-0500\"))", "1994-06-06T07:29:35-05:00"),
        // dsep may be a hyphen with optional spaces.
        ok ("string(parse-ietf-date(\"06 - Jun - 1994 07:29 GMT\"))", "1994-06-06T07:29:00Z"),
        // A four-digit year is taken as given (0070), a two-digit one +1900.
        ok ("string(parse-ietf-date(\"06 Jun 0070 07:29 GMT\"))", "0070-06-06T07:29:00Z"),
        ok ("string(parse-ietf-date(\"06 Jun 70 07:29 GMT\"))", "1970-06-06T07:29:00Z"),
        // asctime without a timezone.
        ok ("string(parse-ietf-date(\"Sun Nov  6 08:49:37 1994\"))", "1994-11-06T08:49:37Z"),
        // 24:00:00 is the end of the day, as in xs:dateTime.
        ok ("string(parse-ietf-date(\"06 Jun 1994 24:00:00 GMT\"))", "1994-06-07T00:00:00Z"),
        // Leap day in a leap year.
        ok ("string(parse-ietf-date(\"29 Feb 2000 00:00 GMT\"))", "2000-02-29T00:00:00Z"),
        // FORG0010: input not matching the grammar, or an invalid date.
        err ("parse-ietf-date(\"\")", "FORG0010"),
        err ("parse-ietf-date(\"31 Feb 2000 00:00 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"29 Feb 1999 00:00 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"32 Jan 2000 00:00 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"00 Jan 2000 00:00 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"123 Jan 2000 00:00 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 199 07:29 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1 07:29 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Foo 1994 07:29 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"Xyz, 06 Jun 1994 07:29 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 25:00 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:60 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 CET\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 -05:0\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 7 GMT\")", "FORG0010"),
        // Comma only directly after a dayname, followed by whitespace.
        err ("parse-ietf-date(\"Wed,06 Jun 1994 07:29 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06, Jun 1994 07:29 GMT\")", "FORG0010"),
        // A parenthesised tzname only after a tzoffset, and it must be a tzname.
        err ("parse-ietf-date(\"06 Jun 1994 07:29 (GMT)\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 +0100 (CET)\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 +0100 (GMT\")", "FORG0010"),
        // Junk tokens, duplicated fields.
        err ("parse-ietf-date(\"06 Jun 1994 1995 07:29 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 08:30 GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 GMT GMT\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29:35GMT GMT\")", "FORG0010"),
        // Offset beyond ±14:00 or minutes ≥ 60 is not a valid timezone.
        err ("parse-ietf-date(\"06 Jun 1994 07:29 -15:00\")", "FORG0010"),
        err ("parse-ietf-date(\"06 Jun 1994 07:29 -05:60\")", "FORG0010"));
  }
}
