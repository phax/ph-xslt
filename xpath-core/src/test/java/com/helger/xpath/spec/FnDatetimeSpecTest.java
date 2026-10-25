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

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.Test;

/**
 * Port of go-xslt's <code>fn_datetime_spec_test.go</code>: duration and
 * date/time component functions, fn:dateTime and the current date/time
 * functions of F&amp;O 3.1.
 *
 * @author Philip Helger
 */
public final class FnDatetimeSpecTest
{
  /**
   * The examples of F&amp;O 3.1 §8.2 (duration components) and §9.5 (date/time
   * component extraction).
   */
  @Test
  public void testDTSpecComponents ()
  {
    run (// §8.2 duration components
        ok ("years-from-duration(xs:yearMonthDuration(\"P20Y15M\"))", "21"),
        ok ("years-from-duration(xs:yearMonthDuration(\"-P15M\"))", "-1"),
        ok ("years-from-duration(xs:dayTimeDuration(\"-P2DT15H\"))", "0"),
        ok ("months-from-duration(xs:yearMonthDuration(\"P20Y15M\"))", "3"),
        ok ("months-from-duration(xs:yearMonthDuration(\"-P20Y18M\"))", "-6"),
        ok ("months-from-duration(xs:dayTimeDuration(\"-P2DT15H0M0S\"))", "0"),
        ok ("days-from-duration(xs:dayTimeDuration(\"P3DT10H\"))", "3"),
        ok ("days-from-duration(xs:dayTimeDuration(\"P3DT55H\"))", "5"),
        ok ("days-from-duration(xs:yearMonthDuration(\"P3Y5M\"))", "0"),
        ok ("days-from-duration(xs:dayTimeDuration(\"-P3DT10H\"))", "-3"),
        ok ("hours-from-duration(xs:dayTimeDuration(\"P3DT10H\"))", "10"),
        ok ("hours-from-duration(xs:dayTimeDuration(\"P3DT12H32M12S\"))", "12"),
        ok ("hours-from-duration(xs:dayTimeDuration(\"PT123H\"))", "3"),
        ok ("hours-from-duration(xs:dayTimeDuration(\"-P3DT10H\"))", "-10"),
        ok ("minutes-from-duration(xs:dayTimeDuration(\"P3DT10H\"))", "0"),
        ok ("minutes-from-duration(xs:dayTimeDuration(\"-P5DT12H30M\"))", "-30"),
        ok ("seconds-from-duration(xs:dayTimeDuration(\"P3DT10H12.5S\"))", "12.5"),
        ok ("seconds-from-duration(xs:dayTimeDuration(\"-PT256S\"))", "-16"),
        ok ("seconds-from-duration(xs:dayTimeDuration(\"P3DT8H2M1.03S\"))", "1.03"),
        ok ("seconds-from-duration(xs:dayTimeDuration(\"PT0S\"))", "0"),
        ok ("seconds-from-duration(xs:dayTimeDuration(\"PT1S\")) instance of xs:decimal", "true"),
        ok ("count(years-from-duration(()))", "0"),
        ok ("count(seconds-from-duration(()))", "0"),
        // §9.5 dateTime components
        ok ("year-from-dateTime(xs:dateTime(\"1999-05-31T13:20:00-05:00\"))", "1999"),
        ok ("year-from-dateTime(xs:dateTime(\"1999-05-31T21:30:00-05:00\"))", "1999"),
        ok ("year-from-dateTime(xs:dateTime(\"1999-12-31T19:20:00\"))", "1999"),
        ok ("year-from-dateTime(xs:dateTime(\"1999-12-31T24:00:00\"))", "2000"),
        ok ("year-from-dateTime(xs:dateTime(\"-0002-06-06T00:00:00\"))", "-2"),
        ok ("month-from-dateTime(xs:dateTime(\"1999-05-31T13:20:00-05:00\"))", "5"),
        ok ("month-from-dateTime(xs:dateTime(\"1999-12-31T19:20:00-05:00\"))", "12"),
        ok ("month-from-dateTime(adjust-dateTime-to-timezone(xs:dateTime(\"1999-12-31T19:20:00-05:00\"), xs:dayTimeDuration(\"PT0S\")))", "1"),
        ok ("day-from-dateTime(xs:dateTime(\"1999-05-31T13:20:00-05:00\"))", "31"),
        ok ("day-from-dateTime(xs:dateTime(\"1999-12-31T20:00:00-05:00\"))", "31"),
        ok ("day-from-dateTime(adjust-dateTime-to-timezone(xs:dateTime(\"1999-12-31T19:20:00-05:00\"), xs:dayTimeDuration(\"PT0S\")))", "1"),
        ok ("hours-from-dateTime(xs:dateTime(\"1999-05-31T08:20:00-05:00\"))", "8"),
        ok ("hours-from-dateTime(xs:dateTime(\"1999-12-31T21:20:00-05:00\"))", "21"),
        ok ("hours-from-dateTime(adjust-dateTime-to-timezone(xs:dateTime(\"1999-12-31T21:20:00-05:00\"), xs:dayTimeDuration(\"PT0S\")))", "2"),
        ok ("hours-from-dateTime(xs:dateTime(\"1999-12-31T12:00:00\"))", "12"),
        ok ("hours-from-dateTime(xs:dateTime(\"1999-12-31T24:00:00\"))", "0"),
        ok ("minutes-from-dateTime(xs:dateTime(\"1999-05-31T13:20:00-05:00\"))", "20"),
        ok ("minutes-from-dateTime(xs:dateTime(\"1999-05-31T13:30:00+05:30\"))", "30"),
        ok ("seconds-from-dateTime(xs:dateTime(\"1999-05-31T13:20:00-05:00\"))", "0"),
        ok ("seconds-from-dateTime(xs:dateTime(\"1999-05-31T13:20:12.25Z\"))", "12.25"),
        ok ("string(timezone-from-dateTime(xs:dateTime(\"1999-05-31T13:20:00-05:00\")))", "-PT5H"),
        ok ("string(timezone-from-dateTime(xs:dateTime(\"2000-06-12T13:20:00Z\")))", "PT0S"),
        ok ("count(timezone-from-dateTime(xs:dateTime(\"2004-08-27T00:00:00\")))", "0"),
        ok ("count(year-from-dateTime(()))", "0"),
        // §9.5 date components
        ok ("year-from-date(xs:date(\"1999-05-31\"))", "1999"),
        ok ("year-from-date(xs:date(\"2000-01-01+05:00\"))", "2000"),
        ok ("year-from-date(xs:date(\"-0002-06-01\"))", "-2"),
        ok ("month-from-date(xs:date(\"1999-05-31-05:00\"))", "5"),
        ok ("month-from-date(xs:date(\"2000-01-01+05:00\"))", "1"),
        ok ("day-from-date(xs:date(\"1999-05-31-05:00\"))", "31"),
        ok ("day-from-date(xs:date(\"2000-01-01+05:00\"))", "1"),
        ok ("string(timezone-from-date(xs:date(\"1999-05-31-05:00\")))", "-PT5H"),
        ok ("string(timezone-from-date(xs:date(\"2000-06-12Z\")))", "PT0S"),
        ok ("count(timezone-from-date(xs:date(\"2000-06-12\")))", "0"),
        // §9.5 time components
        ok ("hours-from-time(xs:time(\"11:23:00\"))", "11"),
        ok ("hours-from-time(xs:time(\"21:23:00\"))", "21"),
        ok ("hours-from-time(xs:time(\"01:23:00+05:00\"))", "1"),
        ok ("hours-from-time(adjust-time-to-timezone(xs:time(\"01:23:00+05:00\"), xs:dayTimeDuration(\"PT0S\")))", "20"),
        ok ("hours-from-time(xs:time(\"24:00:00\"))", "0"),
        ok ("minutes-from-time(xs:time(\"13:00:00Z\"))", "0"),
        ok ("minutes-from-time(xs:time(\"13:45:00Z\"))", "45"),
        ok ("seconds-from-time(xs:time(\"13:20:10.5\"))", "10.5"),
        ok ("string(timezone-from-time(xs:time(\"13:20:00-05:00\")))", "-PT5H"),
        ok ("count(timezone-from-time(xs:time(\"13:20:00\")))", "0"),
        ok ("count(hours-from-time(()))", "0"));
  }

  /**
   * F&amp;O 3.1 §9.3.1 fn:dateTime.
   */
  @Test
  public void testDTSpecDateTimeConstructor ()
  {
    run (ok ("string(dateTime(xs:date(\"1999-12-31\"), xs:time(\"12:00:00\")))", "1999-12-31T12:00:00"),
        ok ("string(dateTime(xs:date(\"1999-12-31\"), xs:time(\"24:00:00\")))", "1999-12-31T00:00:00"),
        // A timezone on either argument goes onto the result.
        ok ("string(dateTime(xs:date(\"1999-12-31Z\"), xs:time(\"12:00:00\")))", "1999-12-31T12:00:00Z"),
        ok ("string(dateTime(xs:date(\"1999-12-31\"), xs:time(\"12:00:00+01:00\")))", "1999-12-31T12:00:00+01:00"),
        ok ("string(dateTime(xs:date(\"1999-12-31+01:00\"), xs:time(\"12:00:00+01:00\")))", "1999-12-31T12:00:00+01:00"),
        ok ("string(dateTime(xs:date(\"1999-12-31\"), xs:time(\"12:00:00.25\")))", "1999-12-31T12:00:00.25"),
        ok ("dateTime(xs:date(\"1999-12-31\"), xs:time(\"12:00:00\")) instance of xs:dateTime", "true"),
        // Either argument empty → empty sequence.
        ok ("count(dateTime((), xs:time(\"12:00:00\")))", "0"),
        ok ("count(dateTime(xs:date(\"1999-12-31\"), ()))", "0"),
        // FORG0008: both have a timezone and they differ.
        err ("dateTime(xs:date(\"1999-12-31+01:00\"), xs:time(\"12:00:00Z\"))", "FORG0008"));
  }

  /**
   * fn:current-dateTime/-date/-time and fn:implicit-timezone (F&amp;O 3.1
   * §15.3-15.6): they are taken from the dynamic context (here pinned) and are
   * stable within one evaluation.
   */
  @Test
  public void testDTSpecCurrent ()
  {
    final OffsetDateTime aNow = OffsetDateTime.of (2020, 2, 29, 23, 15, 30, 500_000_000, ZoneOffset.UTC);
    run (env ().dynamicSetup (x -> {
      x.setCurrentDateTime (aNow);
      return x;
    }), ok ("string(current-dateTime())", "2020-02-29T23:15:30.5Z"),
         ok ("string(current-date())", "2020-02-29Z"),
         ok ("string(current-time())", "23:15:30.5Z"),
         ok ("current-dateTime() eq dateTime(current-date(), current-time())", "true"),
         ok ("current-dateTime() instance of xs:dateTime", "true"),
         ok ("timezone-from-dateTime(current-dateTime()) eq implicit-timezone()", "true"));
  }

  /**
   * fn:default-language (F&amp;O 3.1 'default language' in the dynamic
   * context): the host's setting when one is configured, an xs:language in
   * every case.
   */
  @Test
  public void testDTSpecDefaultLanguage ()
  {
    run (env ().dynamicSetup (x -> {
      x.setDefaultLanguage ("de");
      return x;
    }), ok ("default-language()", "de"));
    run (ok ("default-language() instance of xs:language", "true"));
  }
}
