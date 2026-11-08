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

import org.junit.Test;

/**
 * Specification tests of the date/time, duration and binary lexical forms, ported from go-xslt's <code>datetime_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class DatetimeSpecTest
{
  /**
   * Checks casting strings to the date/time types (F&amp;O
   * 3.1 §19.2, lexical spaces of XSD 1.1 Part 2 §3.3.7–3.3.15) and their
   * canonical string forms (F&amp;O §19.1.2.2 / XSD canonical mappings).
   */
  @Test
  public void caDateTimeLexical ()
  {
    run (env (),
         ok ("string(xs:gYear('2000'))", "2000"),
         ok ("string(xs:gYear('-0012'))", "-0012"),
         ok ("string(xs:gYear('2000Z'))", "2000Z"),
         ok ("string(xs:gYear('12345'))", "12345"),
         ok ("string(xs:gYearMonth('2000-02-05:00'))", "2000-02-05:00"),
         ok ("string(xs:gMonth('--05'))", "--05"),
         ok ("string(xs:gMonthDay('--02-29'))", "--02-29"),
         ok ("string(xs:gDay('---31+14:00'))", "---31+14:00"),
         ok ("string(xs:date('2000-01-01-05:30'))", "2000-01-01-05:30"),
         ok ("string(xs:date('2000-01-01+00:00'))", "2000-01-01Z"),
         ok ("string(xs:dateTimeStamp('2000-01-01T00:00:00Z'))", "2000-01-01T00:00:00Z"),
         ok ("string(xs:time('24:00:00'))", "00:00:00"),
         ok ("string(xs:dateTime('2000-12-31T24:00:00'))", "2001-01-01T00:00:00"),
         ok ("string(xs:time('10:00:00.500'))", "10:00:00.5"),
         ok ("xs:time('10:00:00.1234567891') gt xs:time('10:00:00.123')", "true"),
         ok ("string(xs:date(' 2000-01-01 '))", "2000-01-01"),
         ok ("string(xs:date('0000-01-01'))", "0000-01-01"),
         err ("xs:gMonthDay('--02-30')", "FORG0001"),
         err ("xs:gDay('---32')", "FORG0001"),
         err ("xs:gMonth('--13')", "FORG0001"),
         err ("xs:gYear('02000')", "FORG0001"),
         err ("xs:gYear('20')", "FORG0001"),
         err ("xs:gYearMonth('2000')", "FORG0001"),
         err ("xs:gMonth('05')", "FORG0001"),
         err ("xs:gMonthDay('--5-1')", "FORG0001"),
         err ("xs:gDay('--01')", "FORG0001"),
         err ("xs:date('2000-13-01')", "FORG0001"),
         err ("xs:date('2001-02-29')", "FORG0001"),
         err ("xs:date('2000-01-01+14:01')", "FORG0001"),
         err ("xs:date('2000-01-01+15:00')", "FORG0001"),
         ok ("xs:date('2000-01-01-14:00') lt xs:date('2000-01-02+14:00')", "false"),
         err ("xs:time('24:30:00')", "FORG0001"),
         err ("xs:time('10:60:00')", "FORG0001"),
         err ("xs:time('10:00:60')", "FORG0001"),
         err ("xs:time('10:00')", "FORG0001"),
         err ("xs:dateTime('2000-01-01')", "FORG0001"),
         err ("xs:date('1999-02-29')", "FORG0001"),
         ok ("string(xs:date('2000-02-29'))", "2000-02-29"),
         ok ("string(xs:date('1900-02-28'))", "1900-02-28"),
         err ("xs:date('1900-02-29')", "FORG0001"),
         err ("xs:date('2000-04-31')", "FORG0001"),
         err ("xs:gYear('1234567890')", "FODT0001")
    );
  }

  /**
   * Checks casting to the duration types and their
   * canonical forms (F&amp;O 3.1 §8.1, XSD 1.1 Part 2 §3.3.6 and §E.? duration
   * canonical mapping: largest units first, zero is PT0S / P0M).
   */
  @Test
  public void caDurationLexical ()
  {
    run (env (),
         ok ("string(xs:duration('-P1Y2M3DT4H'))", "-P1Y2M3DT4H"),
         ok ("string(xs:duration('P12M'))", "P1Y"),
         ok ("string(xs:duration('P1M'))", "P1M"),
         ok ("string(xs:duration('P0Y'))", "PT0S"),
         ok ("string(xs:duration('PT0.5S'))", "PT0.5S"),
         ok ("string(xs:duration('-PT1S'))", "-PT1S"),
         ok ("string(xs:duration('P1YT36H'))", "P1Y1DT12H"),
         ok ("string(xs:duration(' P1D '))", "P1D"),
         ok ("string(xs:yearMonthDuration('-P13M'))", "-P1Y1M"),
         ok ("string(xs:yearMonthDuration('P0Y'))", "P0M"),
         ok ("string(xs:yearMonthDuration('P2Y'))", "P2Y"),
         ok ("string(xs:dayTimeDuration('-PT90M'))", "-PT1H30M"),
         ok ("string(xs:dayTimeDuration('PT0S'))", "PT0S"),
         ok ("string(xs:dayTimeDuration('P1D'))", "P1D"),
         ok ("string(xs:dayTimeDuration('PT61.25S'))", "PT1M1.25S"),
         ok ("string(xs:dayTimeDuration('P200000D'))", "P200000D"),
         ok ("string(xs:dayTimeDuration('P200000DT1H2M3.5S'))", "P200000DT1H2M3.5S"),
         ok ("string(xs:dayTimeDuration('P200000DT3S'))", "P200000DT3S"),
         ok ("string(xs:dayTimeDuration('-P200000DT2M'))", "-P200000DT2M"),
         ok ("string(xs:dayTimeDuration('PT200000000000S'))", "P2314814DT19H33M20S"),
         err ("xs:duration('P')", "FORG0001"),
         err ("xs:duration('PT')", "FORG0001"),
         err ("xs:duration('P1YT')", "FORG0001"),
         err ("xs:duration('P1H')", "FORG0001"),
         err ("xs:duration('1Y')", "FORG0001"),
         err ("xs:duration('P-1Y')", "FORG0001"),
         err ("xs:duration('P1.5Y')", "FORG0001")
    );
  }

  /**
   * Checks casting to xs:hexBinary/xs:base64Binary (XSD 1.1
   * Part 2 §3.3.16–17: hex digits in pairs, canonical upper case; base64 with
   * the padding rules of RFC 2045 as restricted by XSD's lexical grammar).
   */
  @Test
  public void caBinaryLexical ()
  {
    run (env (),
         ok ("string(xs:hexBinary('0f'))", "0F"),
         ok ("string(xs:hexBinary(' 0fA0 '))", "0FA0"),
         err ("xs:hexBinary('0')", "FORG0001"),
         err ("xs:hexBinary('0G')", "FORG0001"),
         ok ("xs:base64Binary('AQID BA==') eq xs:base64Binary('AQIDBA==')", "true"),
         ok ("string(xs:base64Binary('AQIDBA=='))", "AQIDBA=="),
         ok ("string(xs:base64Binary(xs:hexBinary('010203')))", "AQID"),
         ok ("string(xs:hexBinary(xs:base64Binary('AQID')))", "010203"),
         err ("xs:base64Binary('AR==')", "FORG0001"),
         err ("xs:base64Binary('A')", "FORG0001"),
         err ("xs:base64Binary('AQ=')", "FORG0001")
    );
  }
}
