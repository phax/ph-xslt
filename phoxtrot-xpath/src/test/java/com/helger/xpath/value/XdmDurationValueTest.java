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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;

import org.junit.Test;

import com.helger.xpath.XPathException;

/**
 * Test class for class {@link XdmDurationValue}.
 *
 * @author Philip Helger
 */
public final class XdmDurationValueTest
{
  private static void _roundTrip (final EAtomicType eType, final String sIn, final String sExpected)
  {
    assertEquals (sIn, sExpected, XdmDurationValue.parse (eType, sIn).toLexical (eType));
  }

  private static void _invalid (final EAtomicType eType, final String sIn)
  {
    try
    {
      XdmDurationValue.parse (eType, sIn);
      fail ("Expected FORG0001 for " + eType + " '" + sIn + "'");
    }
    catch (final XPathException ex)
    {
      assertEquals (sIn, "FORG0001", ex.getCode ());
    }
  }

  @Test
  public void testDuration ()
  {
    final EAtomicType e = EAtomicType.DURATION;
    _roundTrip (e, "P1Y2M3DT10H30M23S", "P1Y2M3DT10H30M23S");
    _roundTrip (e, "-P1Y2M3DT10H30M23.25S", "-P1Y2M3DT10H30M23.25S");
    _roundTrip (e, "P0Y", "PT0S");
    _roundTrip (e, "PT0S", "PT0S");
    _roundTrip (e, "-PT0S", "PT0S");
    _roundTrip (e, "P13M", "P1Y1M");
    _roundTrip (e, "PT36H", "P1DT12H");
    _roundTrip (e, "PT1M1231.432S", "PT21M31.432S");
    _roundTrip (e, "P31DT3H2M10.001S", "P31DT3H2M10.001S");
    _roundTrip (e, "P1Y", "P1Y");
    _roundTrip (e, "P1Y0M0D", "P1Y");
    _roundTrip (e, "PT0.000S", "PT0S");
    _roundTrip (e, "P1234567890DT1.123456789012S", "P1234567890DT1.123456789012S");
    _roundTrip (e, "P768614336404564650Y", "P768614336404564650Y");
    _invalid (e, "P");
    _invalid (e, "PT");
    _invalid (e, "P1YT");
    _invalid (e, "1Y");
    _invalid (e, "P-1Y");
    _invalid (e, "P1.5Y");
    _invalid (e, "PT1.5H");
    _invalid (e, "PT.5S");
    _invalid (e, "PT5.S");
    _invalid (e, "P1D2Y");
    _invalid (e, "PT1S2M");
    _invalid (e, "P1Y1Y");
    _invalid (e, "P1H");
    _invalid (e, "PT1D");
    _invalid (e, "p1Y");
    _invalid (e, "P768614336404564651Y");
  }

  @Test
  public void testYearMonthDuration ()
  {
    final EAtomicType e = EAtomicType.YEAR_MONTH_DURATION;
    _roundTrip (e, "P1Y2M", "P1Y2M");
    _roundTrip (e, "-P2Y6M", "-P2Y6M");
    _roundTrip (e, "P0Y", "P0M");
    _roundTrip (e, "P24M", "P2Y");
    _roundTrip (e, "-P0M", "P0M");
    _invalid (e, "P1D");
    _invalid (e, "P1Y1D");
    _invalid (e, "PT1H");
    _invalid (e, "P1YT0S");
  }

  @Test
  public void testDayTimeDuration ()
  {
    final EAtomicType e = EAtomicType.DAY_TIME_DURATION;
    _roundTrip (e, "P3DT4H5M6S", "P3DT4H5M6S");
    _roundTrip (e, "-PT4H5M", "-PT4H5M");
    _roundTrip (e, "PT0S", "PT0S");
    _roundTrip (e, "P0D", "PT0S");
    _roundTrip (e, "PT24H", "P1D");
    _roundTrip (e, "PT3.10S", "PT3.1S");
    _invalid (e, "P1Y");
    _invalid (e, "P1M");
    _invalid (e, "P0Y1D");
  }

  @Test
  public void testValue ()
  {
    final XdmDurationValue a = XdmDurationValue.parse (EAtomicType.DURATION, "P1Y");
    final XdmDurationValue b = XdmDurationValue.parse (EAtomicType.YEAR_MONTH_DURATION, "P12M");
    assertEquals (a, b);
    assertEquals (a.hashCode (), b.hashCode ());
    assertEquals (12, a.getMonths ());
    assertEquals (BigDecimal.ZERO, a.getSeconds ());

    final XdmDurationValue c = XdmDurationValue.parse (EAtomicType.DAY_TIME_DURATION, "PT1.50S");
    final XdmDurationValue d = XdmDurationValue.of (0, new BigDecimal ("1.5"));
    assertEquals (c, d);
    assertEquals (c.hashCode (), d.hashCode ());
    assertNotEquals (c, a);

    final XdmDurationValue e = XdmDurationValue.parse (EAtomicType.DURATION, "-P1MT1S");
    assertEquals (-1, e.getMonths ());
    assertEquals (new BigDecimal ("-1"), e.getSeconds ());
    assertTrue (e.isNegative ());
    assertEquals ("P1MT1S", e.negate ().toLexical (EAtomicType.DURATION));
    assertEquals ("-P1M", e.toLexical (EAtomicType.YEAR_MONTH_DURATION));
    assertEquals ("-PT1S", e.toLexical (EAtomicType.DAY_TIME_DURATION));
    assertTrue (XdmDurationValue.of (0, BigDecimal.ZERO).isZero ());

    try
    {
      XdmDurationValue.of (1, BigDecimal.ONE.negate ());
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }
}
