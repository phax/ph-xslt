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
package com.helger.xpath.value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.Test;

import com.helger.xpath.XPathException;

/**
 * Test class for class {@link XdmDateTimeValue}.
 *
 * @author Philip Helger
 */
public final class XdmDateTimeValueTest
{
  private static void _roundTrip (final EAtomicType eType, final String sIn, final String sExpected)
  {
    assertEquals (sIn, sExpected, XdmDateTimeValue.parse (eType, sIn).toLexical (eType));
  }

  private static void _invalid (final EAtomicType eType, final String sIn, final String sCode)
  {
    try
    {
      XdmDateTimeValue.parse (eType, sIn);
      fail ("Expected " + sCode + " for " + eType + " '" + sIn + "'");
    }
    catch (final XPathException ex)
    {
      assertEquals (sIn, sCode, ex.getCode ());
    }
  }

  private static int _cmp (final EAtomicType eType, final String s1, final String s2, final int nImplicitTz)
  {
    return Integer.signum (XdmDateTimeValue.parse (eType, s1)
                                           .compareTo (XdmDateTimeValue.parse (eType, s2), eType, nImplicitTz));
  }

  @Test
  public void testDateTimeLexical ()
  {
    final EAtomicType e = EAtomicType.DATE_TIME;
    _roundTrip (e, "2004-05-06T07:08:09", "2004-05-06T07:08:09");
    _roundTrip (e, "2004-05-06T07:08:09.5", "2004-05-06T07:08:09.5");
    _roundTrip (e, "2004-05-06T07:08:09.500", "2004-05-06T07:08:09.5");
    _roundTrip (e, "2004-05-06T07:08:09.000", "2004-05-06T07:08:09");
    _roundTrip (e, "2004-05-06T07:08:09.110", "2004-05-06T07:08:09.11");
    _roundTrip (e, "2004-05-06T07:08:09.123456789012345", "2004-05-06T07:08:09.123456789012345");
    _roundTrip (e, "2004-05-06T07:08:09Z", "2004-05-06T07:08:09Z");
    _roundTrip (e, "2004-05-06T07:08:09+00:00", "2004-05-06T07:08:09Z");
    _roundTrip (e, "2004-05-06T07:08:09-00:00", "2004-05-06T07:08:09Z");
    _roundTrip (e, "2004-05-06T07:08:09+14:00", "2004-05-06T07:08:09+14:00");
    _roundTrip (e, "2004-05-06T07:08:09-05:30", "2004-05-06T07:08:09-05:30");
    _roundTrip (e, "  2004-05-06T07:08:09\n", "2004-05-06T07:08:09");
    // 24:00:00 is the first instant of the next day
    _roundTrip (e, "1999-12-31T24:00:00", "2000-01-01T00:00:00");
    _roundTrip (e, "2000-02-28T24:00:00.000Z", "2000-02-29T00:00:00Z");
    // Years beyond 9999, year 0 and negative years
    _roundTrip (e, "12345-01-01T00:00:00", "12345-01-01T00:00:00");
    _roundTrip (e, "0000-01-01T00:00:00", "0000-01-01T00:00:00");
    _roundTrip (e, "-0001-01-01T00:00:00", "-0001-01-01T00:00:00");
    _roundTrip (e, "-12345-01-01T00:00:00", "-12345-01-01T00:00:00");
    _roundTrip (e, "999999999-12-31T23:59:59.999", "999999999-12-31T23:59:59.999");
  }

  @Test
  public void testDateTimeInvalid ()
  {
    final EAtomicType e = EAtomicType.DATE_TIME;
    _invalid (e, "2004-05-06", "FORG0001");
    _invalid (e, "2004-05-06T07:08", "FORG0001");
    _invalid (e, "2004-5-06T07:08:09", "FORG0001");
    _invalid (e, "04-05-06T07:08:09", "FORG0001");
    _invalid (e, "02004-05-06T07:08:09", "FORG0001");
    _invalid (e, "2004-13-06T07:08:09", "FORG0001");
    _invalid (e, "2004-00-06T07:08:09", "FORG0001");
    _invalid (e, "2004-02-30T07:08:09", "FORG0001");
    _invalid (e, "2003-02-29T07:08:09", "FORG0001");
    _invalid (e, "1900-02-29T07:08:09", "FORG0001");
    _invalid (e, "2004-04-31T07:08:09", "FORG0001");
    _invalid (e, "2004-05-06T25:00:00", "FORG0001");
    _invalid (e, "2004-05-06T24:00:01", "FORG0001");
    _invalid (e, "2004-05-06T24:01:00", "FORG0001");
    _invalid (e, "2004-05-06T24:00:00.001", "FORG0001");
    _invalid (e, "2004-05-06T07:60:00", "FORG0001");
    _invalid (e, "2004-05-06T07:08:60", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09.", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09+14:01", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09+15:00", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09+05:60", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09+0500", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09z", "FORG0001");
    _invalid (e, "2004-05-06t07:08:09", "FORG0001");
    _invalid (e, "", "FORG0001");
    _invalid (e, "2004-05-06T07:08:09 Z", "FORG0001");
    // Year overflow: an implementation limit
    _invalid (e, "-25252734927766555-06-06T00:00:00", "FODT0001");
    _invalid (EAtomicType.DATE, "25252734927766555-07-29", "FODT0001");
  }

  @Test
  public void testDateTimeStamp ()
  {
    _roundTrip (EAtomicType.DATE_TIME_STAMP, "2004-05-06T07:08:09Z", "2004-05-06T07:08:09Z");
    _invalid (EAtomicType.DATE_TIME_STAMP, "2004-05-06T07:08:09", "FORG0001");
  }

  @Test
  public void testDateAndTime ()
  {
    _roundTrip (EAtomicType.DATE, "2004-05-06", "2004-05-06");
    _roundTrip (EAtomicType.DATE, "2004-05-06-05:00", "2004-05-06-05:00");
    _roundTrip (EAtomicType.DATE, "2000-02-29Z", "2000-02-29Z");
    _roundTrip (EAtomicType.DATE, "-0044-03-15", "-0044-03-15");
    _invalid (EAtomicType.DATE, "2004-05-06T00:00:00", "FORG0001");
    _invalid (EAtomicType.DATE, "2004-05", "FORG0001");

    _roundTrip (EAtomicType.TIME, "07:08:09", "07:08:09");
    _roundTrip (EAtomicType.TIME, "07:08:09.010Z", "07:08:09.01Z");
    _roundTrip (EAtomicType.TIME, "23:59:59.999999999-14:00", "23:59:59.999999999-14:00");
    // For xs:time, 24:00:00 is 00:00:00 of the same (reference) day
    _roundTrip (EAtomicType.TIME, "24:00:00", "00:00:00");
    assertEquals (0, _cmp (EAtomicType.TIME, "24:00:00", "00:00:00", 0));
    _invalid (EAtomicType.TIME, "24:00:01", "FORG0001");
    _invalid (EAtomicType.TIME, "7:08:09", "FORG0001");
    _invalid (EAtomicType.TIME, "07:08", "FORG0001");
  }

  @Test
  public void testGregorianTypes ()
  {
    _roundTrip (EAtomicType.G_YEAR, "2004", "2004");
    _roundTrip (EAtomicType.G_YEAR, "-0012Z", "-0012Z");
    _roundTrip (EAtomicType.G_YEAR, "0000", "0000");
    _roundTrip (EAtomicType.G_YEAR_MONTH, "2004-02+01:00", "2004-02+01:00");
    _roundTrip (EAtomicType.G_MONTH, "--12", "--12");
    _roundTrip (EAtomicType.G_MONTH_DAY, "--02-29", "--02-29");
    _roundTrip (EAtomicType.G_MONTH_DAY, "--12-31Z", "--12-31Z");
    _roundTrip (EAtomicType.G_DAY, "---31", "---31");
    _roundTrip (EAtomicType.G_DAY, "---01-14:00", "---01-14:00");
    _invalid (EAtomicType.G_YEAR, "204", "FORG0001");
    _invalid (EAtomicType.G_YEAR, "02004", "FORG0001");
    _invalid (EAtomicType.G_YEAR_MONTH, "2004-13", "FORG0001");
    _invalid (EAtomicType.G_MONTH, "--13", "FORG0001");
    _invalid (EAtomicType.G_MONTH, "--12--", "FORG0001");
    _invalid (EAtomicType.G_MONTH_DAY, "--02-30", "FORG0001");
    _invalid (EAtomicType.G_MONTH_DAY, "--04-31", "FORG0001");
    _invalid (EAtomicType.G_DAY, "---32", "FORG0001");
    _invalid (EAtomicType.G_DAY, "---00", "FORG0001");
    _invalid (EAtomicType.G_DAY, "--01", "FORG0001");
  }

  @Test
  public void testLeapYears ()
  {
    assertTrue (DateTimeCalendar.isLeapYear (2000));
    assertTrue (DateTimeCalendar.isLeapYear (2004));
    assertFalse (DateTimeCalendar.isLeapYear (1900));
    assertFalse (DateTimeCalendar.isLeapYear (2003));
    // Year 0 (1 BCE) is a leap year; so is -4 (5 BCE)
    assertTrue (DateTimeCalendar.isLeapYear (0));
    assertTrue (DateTimeCalendar.isLeapYear (-4));
    assertFalse (DateTimeCalendar.isLeapYear (-1));
    _roundTrip (EAtomicType.DATE, "0000-02-29", "0000-02-29");
    _invalid (EAtomicType.DATE, "-0001-02-29", "FORG0001");
  }

  @Test
  public void testCompare ()
  {
    final EAtomicType e = EAtomicType.DATE_TIME;
    assertEquals (0, _cmp (e, "2002-04-02T12:00:00-01:00", "2002-04-02T17:00:00+04:00", 0));
    assertEquals (-1, _cmp (e, "2002-04-02T12:00:00", "2002-04-02T23:00:00+06:00", 0));
    // Implicit timezone -05:00
    assertEquals (0, _cmp (e, "2002-04-02T12:00:00", "2002-04-02T17:00:00Z", -300));
    assertEquals (0, _cmp (e, "1999-12-31T24:00:00", "2000-01-01T00:00:00", 0));
    assertEquals (1, _cmp (e, "2000-01-01T00:00:00.0000001", "2000-01-01T00:00:00", 0));
    assertEquals (-1, _cmp (e, "-0001-01-01T00:00:00", "0000-01-01T00:00:00", 0));
    assertEquals (0, _cmp (EAtomicType.DATE, "2004-12-25Z", "2004-12-25+00:00", 0));
    assertEquals (0, _cmp (EAtomicType.DATE, "2004-12-25-12:00", "2004-12-26+12:00", 0));
    // xs:time compares on the reference date 1972-12-31
    assertEquals (-1, _cmp (EAtomicType.TIME, "08:00:00+09:00", "17:00:00-06:00", 0));
    assertEquals (0, _cmp (EAtomicType.TIME, "21:30:00+10:30", "06:00:00-05:00", 0));
    // g* types (equality only matters)
    assertEquals (1, _cmp (EAtomicType.G_YEAR, "2005-12:00", "2005+12:00", 0));
    assertEquals (0, _cmp (EAtomicType.G_DAY, "---12-05:00", "---12", -300));
    assertEquals (0, _cmp (EAtomicType.G_MONTH_DAY, "--12-25-14:00", "--12-26+10:00", 0));
  }

  @Test
  public void testAccessorsAndFactories ()
  {
    final XdmDateTimeValue v = XdmDateTimeValue.parse (EAtomicType.DATE_TIME, "2004-05-06T07:08:09.25-05:30");
    assertEquals (2004, v.getYear ());
    assertEquals (5, v.getMonth ());
    assertEquals (6, v.getDay ());
    assertEquals (7, v.getHour ());
    assertEquals (8, v.getMinute ());
    assertEquals (new BigDecimal ("9.25"), v.getSecond ());
    assertTrue (v.hasTimezone ());
    assertEquals (-330, v.getTimezoneMinutes ());
    assertEquals (Integer.valueOf (-330), v.getTimezone ());
    assertNull (v.withTimezone (null).getTimezone ());

    final XdmDateTimeValue v2 = XdmDateTimeValue.of (2004, 5, 6, 7, 8, new BigDecimal ("9.250"), Integer.valueOf (-330));
    assertEquals (v, v2);
    assertEquals (v.hashCode (), v2.hashCode ());

    final XdmDateTimeValue v3 = XdmDateTimeValue.ofEpochSeconds (v.getEpochSeconds (0), Integer.valueOf (60));
    assertEquals ("2004-05-06T13:38:09.25+01:00", v3.toLexical (EAtomicType.DATE_TIME));
    assertEquals (0, v.compareTo (v3, EAtomicType.DATE_TIME, 0));

    final XdmDateTimeValue v4 = XdmDateTimeValue.ofOffsetDateTime (OffsetDateTime.of (2020,
                                                                                       2,
                                                                                       29,
                                                                                       23,
                                                                                       59,
                                                                                       58,
                                                                                       123_000_000,
                                                                                       ZoneOffset.ofHours (2)));
    assertEquals ("2020-02-29T23:59:58.123+02:00", v4.toLexical (EAtomicType.DATE_TIME));

    assertEquals ("1970-01-01T00:00:00",
                  XdmDateTimeValue.ofEpochSeconds (BigDecimal.ZERO, null).toLexical (EAtomicType.DATE_TIME));
    assertEquals ("1969-12-31T23:59:59.5",
                  XdmDateTimeValue.ofEpochSeconds (new BigDecimal ("-0.5"), null).toLexical (EAtomicType.DATE_TIME));
  }

  @Test
  public void testCalendar ()
  {
    // 1970-01-01 was a Thursday
    assertEquals (4, DateTimeCalendar.getIsoDayOfWeek (0));
    assertEquals (0, DateTimeCalendar.getEpochDay (1970, 1, 1));
    assertEquals (11017, DateTimeCalendar.getEpochDay (2000, 3, 1));
    final long [] a = DateTimeCalendar.getCivilFromEpochDay (DateTimeCalendar.getEpochDay (-4713, 11, 24));
    assertEquals (-4713, a[0]);
    assertEquals (11, a[1]);
    assertEquals (24, a[2]);
    assertEquals (366, DateTimeCalendar.getDayOfYear (2004, 12, 31));
    // ISO weeks: 2005-01-01 belongs to week 53 of 2004, 2008-12-29 to week 1
    // of 2009
    assertEquals (53, DateTimeCalendar.getIsoWeekOfYear (2005, 1, 1));
    assertEquals (1, DateTimeCalendar.getIsoWeekOfYear (2008, 12, 29));
    assertEquals (1, DateTimeCalendar.getIsoWeekOfYear (2004, 1, 1));
    // 2005-12-01 is a Thursday: week 1 of December
    assertEquals (1, DateTimeCalendar.getWeekOfMonth (2005, 12, 4));
    assertEquals (2, DateTimeCalendar.getWeekOfMonth (2005, 12, 7));
    // 2006-01-01 is a Sunday: still in the last week of December 2005
    assertEquals (5, DateTimeCalendar.getWeekOfMonth (2006, 1, 1));
    // 2006-01-30 is a Monday whose Thursday is in February: still week 5 of
    // January (format-date-011)
    assertEquals (5, DateTimeCalendar.getWeekOfMonth (2006, 1, 30));
    assertEquals (1, DateTimeCalendar.getWeekOfMonth (2006, 2, 2));
    assertEquals (5, DateTimeCalendar.getWeekOfMonth (2006, 3, 31));
  }
}
