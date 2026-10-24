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
package com.helger.xpath.ops;

import static com.helger.xpath.ops.OpsTestHelper.assertError;
import static com.helger.xpath.ops.OpsTestHelper.dbl;
import static com.helger.xpath.ops.OpsTestHelper.dec;
import static com.helger.xpath.ops.OpsTestHelper.flt;
import static com.helger.xpath.ops.OpsTestHelper.i;
import static com.helger.xpath.ops.OpsTestHelper.str;
import static com.helger.xpath.ops.OpsTestHelper.untyped;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;

import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link AtomicCaster}.
 *
 * @author Philip Helger
 */
public final class AtomicCasterTest
{
  private static XdmAtomic _cast (final XdmAtomic aValue, final EAtomicType eTarget)
  {
    return AtomicCaster.cast (aValue, eTarget, null);
  }

  private static void _assertCast (final XdmAtomic aValue,
                                   final EAtomicType eTarget,
                                   final String sExpectedLexical)
  {
    final XdmAtomic aRes = _cast (aValue, eTarget);
    assertSame (eTarget, aRes.getType ());
    assertEquals (sExpectedLexical, aRes.getLexical ());
  }

  private static void _assertCastError (final String sCode, final XdmAtomic aValue, final EAtomicType eTarget)
  {
    assertError (sCode, () -> _cast (aValue, eTarget));
    if (!"XPST0080".equals (sCode))
      assertFalse (AtomicCaster.isCastable (aValue, eTarget, null));
  }

  @Test
  public void testGoReferenceCases ()
  {
    _assertCast (str ("5"), EAtomicType.INTEGER, "5");
    _assertCastError ("FORG0001", str ("5.5"), EAtomicType.INTEGER);
    _assertCast (i (5), EAtomicType.DOUBLE, "5");
    _assertCast (dbl (3.9), EAtomicType.INTEGER, "3");
    _assertCast (str ("3.14"), EAtomicType.DECIMAL, "3.14");
    _assertCast (str ("true"), EAtomicType.BOOLEAN, "true");
    _assertCast (str ("0"), EAtomicType.BOOLEAN, "false");
    _assertCast (i (1), EAtomicType.BOOLEAN, "true");
    _assertCast (i (255), EAtomicType.STRING, "255");
    _assertCastError ("FORG0001", str ("notanumber"), EAtomicType.INTEGER);
    _assertCastError ("FORG0001", str ("xyz"), EAtomicType.BOOLEAN);
    assertTrue (AtomicCaster.isCastable (str ("5"), EAtomicType.INTEGER, null));
    assertFalse (AtomicCaster.isCastable (str ("five"), EAtomicType.INTEGER, null));
  }

  @Test
  public void testIdentityAndUniversalTargets ()
  {
    final XdmAtomic a = i (5);
    assertSame (a, _cast (a, EAtomicType.INTEGER));
    _assertCast (dbl (1e6), EAtomicType.STRING, "1.0E6");
    _assertCast (dbl (1e6), EAtomicType.UNTYPED_ATOMIC, "1.0E6");
    _assertCast (XdmAtomic.TRUE, EAtomicType.UNTYPED_ATOMIC, "true");
    _assertCast (XdmAtomic.qname (new XdmQName ("urn:x", "l", "p")), EAtomicType.STRING, "p:l");
    _assertCast (untyped (" a "), EAtomicType.STRING, " a ");
  }

  @Test
  public void testStringToInteger ()
  {
    _assertCast (str (" 5\n"), EAtomicType.INTEGER, "5");
    _assertCast (str ("+5"), EAtomicType.INTEGER, "5");
    _assertCast (str ("-007"), EAtomicType.INTEGER, "-7");
    _assertCast (str ("123456789012345678901234567890"), EAtomicType.INTEGER, "123456789012345678901234567890");
    _assertCast (untyped ("42"), EAtomicType.INTEGER, "42");
    _assertCastError ("FORG0001", str (""), EAtomicType.INTEGER);
    _assertCastError ("FORG0001", str ("1e2"), EAtomicType.INTEGER);
    _assertCastError ("FORG0001", str ("3.0"), EAtomicType.LONG);
    _assertCastError ("FORG0001", str ("1 2"), EAtomicType.INTEGER);
  }

  @Test
  public void testDerivedIntegerRanges ()
  {
    _assertCast (str ("127"), EAtomicType.BYTE, "127");
    _assertCast (str ("-128"), EAtomicType.BYTE, "-128");
    _assertCastError ("FORG0001", str ("128"), EAtomicType.BYTE);
    _assertCastError ("FORG0001", i (-129), EAtomicType.BYTE);
    _assertCast (str ("18446744073709551615"), EAtomicType.UNSIGNED_LONG, "18446744073709551615");
    _assertCastError ("FORG0001", str ("18446744073709551616"), EAtomicType.UNSIGNED_LONG);
    _assertCastError ("FORG0001", i (-1), EAtomicType.UNSIGNED_BYTE);
    _assertCast (str ("9223372036854775807"), EAtomicType.LONG, "9223372036854775807");
    _assertCastError ("FORG0001", str ("9223372036854775808"), EAtomicType.LONG);
    _assertCastError ("FORG0001", i (0), EAtomicType.POSITIVE_INTEGER);
    _assertCast (i (0), EAtomicType.NON_NEGATIVE_INTEGER, "0");
    _assertCast (i (-1), EAtomicType.NEGATIVE_INTEGER, "-1");
    _assertCastError ("FORG0001", i (0), EAtomicType.NEGATIVE_INTEGER);
    _assertCast (i (0), EAtomicType.NON_POSITIVE_INTEGER, "0");
    _assertCast (dbl (300.7), EAtomicType.SHORT, "300");
    _assertCast (XdmAtomic.TRUE, EAtomicType.UNSIGNED_BYTE, "1");
    // derived to base keeps the value with the new type
    _assertCast (XdmAtomic.of (EAtomicType.BYTE, BigInteger.TEN), EAtomicType.INTEGER, "10");
    _assertCast (XdmAtomic.of (EAtomicType.BYTE, BigInteger.TEN), EAtomicType.DECIMAL, "10");
  }

  @Test
  public void testNumericToInteger ()
  {
    _assertCast (dbl (-3.9), EAtomicType.INTEGER, "-3");
    _assertCast (dec ("3.7"), EAtomicType.INTEGER, "3");
    _assertCast (dec ("-3.7"), EAtomicType.INTEGER, "-3");
    _assertCast (flt (2.5f), EAtomicType.INTEGER, "2");
    _assertCast (dbl (1e20), EAtomicType.INTEGER, "100000000000000000000");
    _assertCast (dbl (-0.0), EAtomicType.INTEGER, "0");
    _assertCastError ("FOCA0002", dbl (Double.NaN), EAtomicType.INTEGER);
    _assertCastError ("FOCA0002", dbl (Double.POSITIVE_INFINITY), EAtomicType.INTEGER);
    _assertCastError ("FOCA0002", flt (Float.NEGATIVE_INFINITY), EAtomicType.INT);
    assertEquals (new BigDecimal (1e300).toBigInteger (), _cast (dbl (1e300), EAtomicType.INTEGER).getBigInteger ());
  }

  @Test
  public void testDecimal ()
  {
    _assertCast (str (" .5 "), EAtomicType.DECIMAL, "0.5");
    _assertCast (str ("5."), EAtomicType.DECIMAL, "5");
    _assertCast (str ("-1.50"), EAtomicType.DECIMAL, "-1.5");
    _assertCastError ("FORG0001", str ("1e5"), EAtomicType.DECIMAL);
    _assertCastError ("FORG0001", str ("INF"), EAtomicType.DECIMAL);
    _assertCastError ("FORG0001", str ("."), EAtomicType.DECIMAL);
    _assertCast (i (12), EAtomicType.DECIMAL, "12");
    _assertCast (XdmAtomic.TRUE, EAtomicType.DECIMAL, "1");
    _assertCast (dbl (-0.0), EAtomicType.DECIMAL, "0");
    _assertCast (dbl (1e5), EAtomicType.DECIMAL, "100000");
    // The exact value of the double
    _assertCast (dbl (0.1), EAtomicType.DECIMAL, "0.1000000000000000055511151231257827021181583404541015625");
    _assertCast (flt (0.5f), EAtomicType.DECIMAL, "0.5");
    _assertCastError ("FOCA0002", dbl (Double.NaN), EAtomicType.DECIMAL);
    _assertCastError ("FOCA0002", flt (Float.POSITIVE_INFINITY), EAtomicType.DECIMAL);
  }

  @Test
  public void testDoubleAndFloat ()
  {
    _assertCast (str ("1e5"), EAtomicType.DOUBLE, "100000");
    _assertCast (str (" -1.5E-3 "), EAtomicType.DOUBLE, "-0.0015");
    _assertCast (str ("INF"), EAtomicType.DOUBLE, "INF");
    _assertCast (str ("+INF"), EAtomicType.DOUBLE, "INF");
    _assertCast (str ("-INF"), EAtomicType.DOUBLE, "-INF");
    _assertCast (str ("NaN"), EAtomicType.DOUBLE, "NaN");
    _assertCast (str ("-0"), EAtomicType.DOUBLE, "-0");
    _assertCast (str ("1e400"), EAtomicType.DOUBLE, "INF");
    _assertCastError ("FORG0001", str ("Infinity"), EAtomicType.DOUBLE);
    _assertCastError ("FORG0001", str ("inf"), EAtomicType.DOUBLE);
    _assertCastError ("FORG0001", str ("0x10"), EAtomicType.DOUBLE);
    _assertCastError ("FORG0001", str ("1d"), EAtomicType.DOUBLE);
    _assertCastError ("FORG0001", str ("1.5f"), EAtomicType.FLOAT);
    _assertCastError ("FORG0001", str (""), EAtomicType.FLOAT);

    _assertCast (str ("1.01"), EAtomicType.FLOAT, "1.01");
    assertEquals (Float.valueOf (1.01f), _cast (str ("1.01"), EAtomicType.FLOAT).getValue ());
    // float to double widens the binary32 value
    _assertCast (flt (1.01f), EAtomicType.DOUBLE, "1.0099999904632568");
    _assertCast (dec ("1.01"), EAtomicType.FLOAT, "1.01");
    _assertCast (i (16777217), EAtomicType.FLOAT, "1.6777216E7");
    _assertCast (dbl (1e40), EAtomicType.FLOAT, "INF");
    _assertCast (XdmAtomic.FALSE, EAtomicType.FLOAT, "0");
    _assertCast (XdmAtomic.TRUE, EAtomicType.DOUBLE, "1");
    _assertCast (dec ("123456789012345678901234567890"), EAtomicType.DOUBLE, "1.2345678901234568E29");
  }

  @Test
  public void testBoolean ()
  {
    _assertCast (str (" true\t"), EAtomicType.BOOLEAN, "true");
    _assertCast (str ("1"), EAtomicType.BOOLEAN, "true");
    _assertCast (untyped ("false"), EAtomicType.BOOLEAN, "false");
    _assertCastError ("FORG0001", str ("TRUE"), EAtomicType.BOOLEAN);
    _assertCastError ("FORG0001", str ("yes"), EAtomicType.BOOLEAN);
    _assertCast (i (0), EAtomicType.BOOLEAN, "false");
    _assertCast (i (-2), EAtomicType.BOOLEAN, "true");
    _assertCast (dec ("0.0"), EAtomicType.BOOLEAN, "false");
    _assertCast (dbl (Double.NaN), EAtomicType.BOOLEAN, "false");
    _assertCast (dbl (-0.0), EAtomicType.BOOLEAN, "false");
    _assertCast (flt (0.1f), EAtomicType.BOOLEAN, "true");
  }

  @Test
  public void testDerivedStrings ()
  {
    _assertCast (str ("a\tb\nc "), EAtomicType.NORMALIZED_STRING, "a b c ");
    _assertCast (str ("  a   b  "), EAtomicType.TOKEN, "a b");
    _assertCast (str (" a"), EAtomicType.TOKEN, " a");
    _assertCast (str (" en-US "), EAtomicType.LANGUAGE, "en-US");
    _assertCastError ("FORG0001", str ("toolongtag1"), EAtomicType.LANGUAGE);
    _assertCastError ("FORG0001", str ("en_US"), EAtomicType.LANGUAGE);
    _assertCast (XdmAtomic.TRUE, EAtomicType.LANGUAGE, "true");
    _assertCastError ("FORG0001", str ("a:b"), EAtomicType.NCNAME);
    _assertCast (str ("a:b"), EAtomicType.NAME, "a:b");
    _assertCast (str ("123"), EAtomicType.NMTOKEN, "123");
    _assertCastError ("FORG0001", str ("a b"), EAtomicType.NMTOKEN);
    _assertCast (str (" id1 "), EAtomicType.ID, "id1");
    _assertCast (str ("r"), EAtomicType.IDREF, "r");
    _assertCast (str ("e"), EAtomicType.ENTITY, "e");
    _assertCastError ("FORG0001", i (1), EAtomicType.NCNAME);
    _assertCastError ("FORG0001", str (""), EAtomicType.NAME);
    _assertCast (dbl (1e6), EAtomicType.TOKEN, "1.0E6");
    _assertCast (XdmAtomic.anyUri ("http://x"), EAtomicType.TOKEN, "http://x");
    // NCName to Name: stays valid
    _assertCast (XdmAtomic.of (EAtomicType.NCNAME, "abc"), EAtomicType.NAME, "abc");
  }

  @Test
  public void testAnyUri ()
  {
    _assertCast (str (" http://x/ a "), EAtomicType.ANY_URI, "http://x/ a");
    _assertCast (untyped ("rel/path"), EAtomicType.ANY_URI, "rel/path");
    _assertCastError ("FORG0001", str ("%zz"), EAtomicType.ANY_URI);
    _assertCastError ("FORG0001", str (":a"), EAtomicType.ANY_URI);
    _assertCastError ("XPTY0004", i (1), EAtomicType.ANY_URI);
    _assertCastError ("XPTY0004", XdmAtomic.anyUri ("x"), EAtomicType.INTEGER);
    _assertCastError ("XPTY0004", XdmAtomic.TRUE, EAtomicType.ANY_URI);
  }

  @Test
  public void testBinary ()
  {
    _assertCast (str ("0fa0"), EAtomicType.HEX_BINARY, "0FA0");
    _assertCast (str (" "), EAtomicType.HEX_BINARY, "");
    _assertCastError ("FORG0001", str ("0fa"), EAtomicType.HEX_BINARY);
    _assertCastError ("FORG0001", str ("zz"), EAtomicType.HEX_BINARY);
    _assertCast (str ("AQID"), EAtomicType.BASE64_BINARY, "AQID");
    _assertCast (str ("A Q\nI D"), EAtomicType.BASE64_BINARY, "AQID");
    _assertCastError ("FORG0001", str ("AQJ="), EAtomicType.BASE64_BINARY);
    _assertCastError ("FORG0001", str ("AQI"), EAtomicType.BASE64_BINARY);
    _assertCast (_cast (str ("AQID"), EAtomicType.BASE64_BINARY), EAtomicType.HEX_BINARY, "010203");
    _assertCast (_cast (str ("0102"), EAtomicType.HEX_BINARY), EAtomicType.BASE64_BINARY, "AQI=");
    _assertCastError ("XPTY0004", i (1), EAtomicType.HEX_BINARY);
    _assertCastError ("XPTY0004", _cast (str ("01"), EAtomicType.HEX_BINARY), EAtomicType.INTEGER);
  }

  @Test
  public void testQName ()
  {
    final StaticContext aSC = new StaticContext ();
    aSC.declareNamespace ("p", "urn:p");
    aSC.setDefaultElementNamespace ("urn:default");

    XdmAtomic aRes = AtomicCaster.cast (str (" p:l "), EAtomicType.QNAME, aSC);
    assertEquals (new XdmQName ("urn:p", "l"), aRes.getValue ());
    assertEquals ("p:l", aRes.getLexical ());

    aRes = AtomicCaster.cast (str ("l"), EAtomicType.QNAME, aSC);
    assertEquals (new XdmQName ("urn:default", "l"), aRes.getValue ());

    // untypedAtomic is allowed since XPath 3.0
    aRes = AtomicCaster.cast (untyped ("ncname"), EAtomicType.QNAME, aSC);
    assertEquals (new XdmQName ("urn:default", "ncname"), aRes.getValue ());
    aRes = AtomicCaster.castFromString ("p:x", EAtomicType.QNAME, aSC);
    assertEquals (new XdmQName ("urn:p", "x"), aRes.getValue ());

    assertError ("FONS0004", () -> AtomicCaster.cast (str ("q:l"), EAtomicType.QNAME, aSC));
    assertError ("FONS0004", () -> AtomicCaster.cast (str ("p:l"), EAtomicType.QNAME, null));
    assertError ("FORG0001", () -> AtomicCaster.cast (str ("1a"), EAtomicType.QNAME, aSC));
    assertError ("FORG0001", () -> AtomicCaster.cast (str (""), EAtomicType.QNAME, aSC));
    assertError ("FORG0001",
                 () -> AtomicCaster.cast (untyped ("an arbitrary string"), EAtomicType.QNAME, aSC));
    assertEquals (new XdmQName (XdmConstants.NS_XML, "lang"),
                  AtomicCaster.cast (str ("xml:lang"), EAtomicType.QNAME, null).getValue ());
    assertEquals (new XdmQName ("", "l"), AtomicCaster.cast (str ("l"), EAtomicType.QNAME, null).getValue ());

    _assertCastError ("XPTY0004", i (1), EAtomicType.QNAME);
    _assertCastError ("XPTY0004", XdmAtomic.anyUri ("x"), EAtomicType.QNAME);
    _assertCastError ("XPTY0004", XdmAtomic.qname (new XdmQName ("l")), EAtomicType.INTEGER);
    // NOTATION shares the value space
    aRes = _cast (XdmAtomic.of (EAtomicType.NOTATION, new XdmQName ("urn:n", "n", "p")), EAtomicType.QNAME);
    assertSame (EAtomicType.QNAME, aRes.getType ());
    assertEquals ("p:n", aRes.getLexical ());
  }

  @Test
  public void testSpecialTargets ()
  {
    _assertCastError ("XPST0080", str ("a"), EAtomicType.NOTATION);
    _assertCastError ("XPST0080", str ("a"), EAtomicType.ANY_ATOMIC_TYPE);
    assertError ("XPST0080", () -> AtomicCaster.isCastable (str ("a"), EAtomicType.NOTATION, null));
    _assertCastError ("FORG0001", i (1), EAtomicType.ERROR);
    _assertCastError ("FORG0001", str ("boo"), EAtomicType.ERROR);
    final XdmAtomic a = i (17);
    assertSame (a, _cast (a, EAtomicType.NUMERIC));
    final XdmAtomic aRes = _cast (str ("12"), EAtomicType.NUMERIC);
    assertSame (EAtomicType.DOUBLE, aRes.getType ());
  }

  @Test
  public void testDisallowedCasts ()
  {
    _assertCastError ("XPTY0004", XdmAtomic.TRUE, EAtomicType.HEX_BINARY);
    _assertCastError ("XPTY0004", i (1), EAtomicType.DATE);
    _assertCastError ("XPTY0004", i (1), EAtomicType.DURATION);
    _assertCastError ("XPTY0004", dbl (1), EAtomicType.QNAME);
  }

  @Test
  public void testDateTimeDelegation ()
  {
    _assertCast (str ("2004-01-15"), EAtomicType.DATE, "2004-01-15");
    _assertCast (str ("2004-01-15T12:30:00"), EAtomicType.DATE_TIME, "2004-01-15T12:30:00");
    _assertCast (str ("P1Y2M"), EAtomicType.YEAR_MONTH_DURATION, "P1Y2M");
    _assertCast (str ("PT1H30M"), EAtomicType.DAY_TIME_DURATION, "PT1H30M");
    _assertCast (_cast (str ("2004-01-15T12:30:00"), EAtomicType.DATE_TIME), EAtomicType.DATE, "2004-01-15");
    _assertCast (_cast (str ("2004-01-15"), EAtomicType.DATE), EAtomicType.G_YEAR, "2004");
    _assertCast (_cast (str ("P1Y2M3D"), EAtomicType.DURATION), EAtomicType.YEAR_MONTH_DURATION, "P1Y2M");
    _assertCastError ("FORG0001", str ("2004-01-15T12:30:00"), EAtomicType.DATE_TIME_STAMP);
    _assertCastError ("FORG0001", str ("2004-13-15"), EAtomicType.DATE);
    _assertCastError ("XPTY0004", _cast (str ("2004-01-15"), EAtomicType.DATE), EAtomicType.DURATION);
    _assertCastError ("XPTY0004", _cast (str ("2004-01-15"), EAtomicType.DATE), EAtomicType.INTEGER);
    _assertCastError ("XPTY0004", _cast (str ("12:00:00"), EAtomicType.TIME), EAtomicType.DATE);
    _assertCast (_cast (str ("2004-01-15"), EAtomicType.DATE), EAtomicType.STRING, "2004-01-15");
  }
}
