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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;

import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link AtomicLexical}.
 *
 * @author Philip Helger
 */
public final class AtomicLexicalTest
{
  private static void _assertDouble (final String sExpected, final double d)
  {
    assertEquals (sExpected, AtomicLexical.formatDouble (d));
    assertEquals (sExpected, XdmAtomic.dbl (d).getLexical ());
  }

  private static void _assertFloat (final String sExpected, final float f)
  {
    assertEquals (sExpected, AtomicLexical.formatFloat (f));
    assertEquals (sExpected, XdmAtomic.flt (f).getLexical ());
  }

  @Test
  public void testDoubleSpecialValues ()
  {
    _assertDouble ("NaN", Double.NaN);
    _assertDouble ("INF", Double.POSITIVE_INFINITY);
    _assertDouble ("-INF", Double.NEGATIVE_INFINITY);
    _assertDouble ("0", 0d);
    _assertDouble ("-0", -0d);
  }

  @Test
  public void testDoublePlainRange ()
  {
    _assertDouble ("1", 1d);
    _assertDouble ("-1", -1d);
    _assertDouble ("1.5", 1.5);
    _assertDouble ("100", 100d);
    _assertDouble ("123456.789", 123456.789);
    _assertDouble ("999999", 999999d);
    _assertDouble ("999999.9", 999999.9);
    _assertDouble ("0.000001", 1e-6);
    _assertDouble ("0.0000015", 1.5e-6);
    _assertDouble ("0.001", 0.001);
    _assertDouble ("0.1", 0.1);
    _assertDouble ("0.30000000000000004", 0.1 + 0.2);
    _assertDouble ("-0.5", -0.5);
  }

  @Test
  public void testDoubleExponentRange ()
  {
    _assertDouble ("1.0E6", 1e6);
    _assertDouble ("1.0000001E6", 1000000.1);
    _assertDouble ("1.2345678E7", 12345678d);
    _assertDouble ("1.0E22", 1e22);
    _assertDouble ("1.0E21", 1e21);
    _assertDouble ("1.0E23", 1e23);
    _assertDouble ("-1.5E10", -1.5e10);
    _assertDouble ("9.99E-7", 9.99e-7);
    _assertDouble ("1.0E-7", 1e-7);
    _assertDouble ("1.26743233E15", 1.26743233E15);
    _assertDouble ("4.9E-324", Double.MIN_VALUE);
    _assertDouble ("1.7976931348623157E308", Double.MAX_VALUE);
    _assertDouble ("-1.0E-300", -1e-300);
  }

  @Test
  public void testFloat ()
  {
    _assertFloat ("NaN", Float.NaN);
    _assertFloat ("INF", Float.POSITIVE_INFINITY);
    _assertFloat ("-INF", Float.NEGATIVE_INFINITY);
    _assertFloat ("0", 0f);
    _assertFloat ("-0", -0f);
    // float precision: shortest digits at 32 bit, not the widened double
    _assertFloat ("1.01", 1.01f);
    _assertFloat ("0.1", 0.1f);
    _assertFloat ("0.3", 0.1f + 0.2f);
    _assertFloat ("1.0E6", 1e6f);
    _assertFloat ("999999", 999999f);
    _assertFloat ("1.6777216E7", 16777216f);
    _assertFloat ("3.4028235E38", Float.MAX_VALUE);
    _assertFloat ("1.4E-45", Float.MIN_VALUE);
    // The float nearest 1e-6 is slightly below 1e-6
    _assertFloat ("1.0E-6", 1e-6f);
    _assertFloat ("0.0000011", 1.1e-6f);
    _assertFloat ("-2.5", -2.5f);
  }

  @Test
  public void testDecimal ()
  {
    assertEquals ("1.5", AtomicLexical.formatDecimal (new BigDecimal ("1.5")));
    assertEquals ("10", AtomicLexical.formatDecimal (new BigDecimal ("10.00")));
    assertEquals ("1000", AtomicLexical.formatDecimal (new BigDecimal ("1E+3")));
    assertEquals ("0", AtomicLexical.formatDecimal (new BigDecimal ("0.000")));
    assertEquals ("0", AtomicLexical.formatDecimal (new BigDecimal ("-0.0")));
    assertEquals ("-0.5", AtomicLexical.formatDecimal (new BigDecimal ("-0.50")));
    assertEquals ("0.0000000001", AtomicLexical.formatDecimal (new BigDecimal ("1E-10")));
    assertEquals ("123456789012345678901234567890.1234567890123456789",
                  AtomicLexical.formatDecimal (new BigDecimal ("123456789012345678901234567890.12345678901234567890")));
    assertEquals ("1.5", XdmAtomic.decimal (new BigDecimal ("1.50")).getLexical ());
  }

  @Test
  public void testOtherTypes ()
  {
    assertEquals ("42", XdmAtomic.integer (42).getLexical ());
    assertEquals ("-7", XdmAtomic.integer (-7).getLexical ());
    assertEquals ("123456789012345678901234567890",
                  XdmAtomic.integer (new BigInteger ("123456789012345678901234567890")).getLexical ());
    assertEquals ("127", XdmAtomic.of (EAtomicType.BYTE, BigInteger.valueOf (127)).getLexical ());
    assertEquals ("true", XdmAtomic.TRUE.getLexical ());
    assertEquals ("false", XdmAtomic.FALSE.getLexical ());
    assertEquals ("hi", XdmAtomic.string ("hi").getLexical ());
    assertEquals (" u ", XdmAtomic.untyped (" u ").getLexical ());
    assertEquals ("http://x", XdmAtomic.anyUri ("http://x").getLexical ());
    assertEquals ("0FA0", XdmAtomic.of (EAtomicType.HEX_BINARY, new byte [] { 0x0f, (byte) 0xa0 }).getLexical ());
    assertEquals ("", XdmAtomic.of (EAtomicType.HEX_BINARY, new byte [0]).getLexical ());
    assertEquals ("AQID", XdmAtomic.of (EAtomicType.BASE64_BINARY, new byte [] { 1, 2, 3 }).getLexical ());
    assertEquals ("AQI=", XdmAtomic.of (EAtomicType.BASE64_BINARY, new byte [] { 1, 2 }).getLexical ());
    assertEquals ("p:l", XdmAtomic.qname (new XdmQName ("urn:x", "l", "p")).getLexical ());
    assertEquals ("l", XdmAtomic.qname (new XdmQName ("urn:x", "l")).getLexical ());
    assertEquals ("p:n", XdmAtomic.of (EAtomicType.NOTATION, new XdmQName ("urn:x", "n", "p")).getLexical ());
  }

  @Test
  public void testDateTimeDelegation ()
  {
    final XdmAtomic aDate = XdmAtomic.of (EAtomicType.DATE, XdmDateTimeValue.parse (EAtomicType.DATE, "2004-01-15"));
    assertEquals ("2004-01-15", aDate.getLexical ());
    final XdmAtomic aDur = XdmAtomic.of (EAtomicType.DURATION,
                                         XdmDurationValue.parse (EAtomicType.DURATION, "P1Y2M3DT4H"));
    assertEquals ("P1Y2M3DT4H", aDur.getLexical ());
  }
}
