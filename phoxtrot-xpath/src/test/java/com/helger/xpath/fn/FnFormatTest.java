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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;

import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnFormat}. Most cases are taken from the W3C
 * QT3 test suite (format-integer, format-number, format-date, format-dateTime,
 * format-time).
 *
 * @author Philip Helger
 */
public final class FnFormatTest
{
  private static final FunctionLibrary LIB = new FunctionLibrary ();
  static
  {
    FnFormat.register (LIB);
  }

  private static XdmSequence _s (final String s)
  {
    return s == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.string (s));
  }

  private static String _callCtx (final DynamicContext aCtx, final String sName, final XdmSequence... aArgs)
  {
    final FunctionLibrary.FunctionDef aDef = LIB.getFunction (new XdmQName (XdmConstants.NS_FN, sName),
                                                              aArgs.length);
    assertNotNull (sName + "#" + aArgs.length, aDef);
    final XdmSequence aRes = aDef.getImpl ().call (aCtx, Arrays.asList (aArgs));
    return aRes.isEmpty () ? null : ((XdmAtomic) aRes.getFirst ()).getString ();
  }

  private static String _call (final String sName, final XdmSequence... aArgs)
  {
    return _callCtx (new DynamicContext (new StaticContext ()), sName, aArgs);
  }

  private static void _err (final String sCode, final String sName, final XdmSequence... aArgs)
  {
    try
    {
      _call (sName, aArgs);
      fail ("Expected " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (sCode, ex.getCode ());
    }
  }

  private static String _fi (final long n, final String sPicture)
  {
    return _call ("format-integer", XdmSequence.of (XdmAtomic.integer (n)), _s (sPicture));
  }

  private static String _fi (final long n, final String sPicture, final String sLang)
  {
    return _call ("format-integer", XdmSequence.of (XdmAtomic.integer (n)), _s (sPicture), _s (sLang));
  }

  private static XdmSequence _dec (final String s)
  {
    return XdmSequence.of (XdmAtomic.decimal (new BigDecimal (s)));
  }

  private static XdmSequence _dbl (final double d)
  {
    return XdmSequence.of (XdmAtomic.dbl (d));
  }

  private static String _fn (final XdmSequence aValue, final String sPicture)
  {
    return _call ("format-number", aValue, _s (sPicture));
  }

  private static XdmSequence _v (final EAtomicType eType, final String sLexical)
  {
    return XdmSequence.of (XdmAtomic.of (eType, XdmDateTimeValue.parse (eType, sLexical)));
  }

  private static String _fname (final EAtomicType eType)
  {
    return eType == EAtomicType.DATE ? "format-date" : eType == EAtomicType.TIME ? "format-time" : "format-dateTime";
  }

  private static String _fd (final EAtomicType eType, final String sLexical, final String sPicture)
  {
    return _call (_fname (eType), _v (eType, sLexical), _s (sPicture));
  }

  private static String _fd (final EAtomicType eType,
                             final String sLexical,
                             final String sPicture,
                             final String sLang,
                             final String sCal,
                             final String sPlace)
  {
    return _call (_fname (eType), _v (eType, sLexical), _s (sPicture), _s (sLang), _s (sCal), _s (sPlace));
  }

  private static String _date (final String sLexical, final String sPicture)
  {
    return _fd (EAtomicType.DATE, sLexical, sPicture);
  }

  private static String _dt (final String sLexical, final String sPicture)
  {
    return _fd (EAtomicType.DATE_TIME, sLexical, sPicture);
  }

  private static String _time (final String sLexical, final String sPicture)
  {
    return _fd (EAtomicType.TIME, sLexical, sPicture);
  }

  @Test
  public void testFormatInteger ()
  {
    assertEquals ("123", _fi (123, "1"));
    assertEquals ("123", _fi (123, "001"));
    assertEquals ("00123", _fi (123, "00001"));
    assertEquals ("-123", _fi (-123, "9"));
    assertEquals ("-00123", _fi (-123, "99999"));
    assertEquals ("000", _fi (0, "000"));
    assertEquals ("007", _fi (7, "000"));
    assertEquals ("-045", _fi (-45, "000"));
    assertEquals ("1,234,567", _fi (1234567, "#,##0"));
    assertEquals ("d", _fi (4, "a"));
    assertEquals ("AB", _fi (28, "A"));
    assertEquals ("xiv", _fi (14, "i"));
    assertEquals ("MCMLXXXIV", _fi (1984, "I"));
    assertEquals ("5000", _fi (5000, "I"));
    assertEquals ("١٢", _fi (12, "١"));
    assertEquals ("Ten", _fi (10, "Ww"));
    assertEquals ("TEN", _fi (10, "W"));
    assertEquals ("ten", _fi (10, "w"));
    assertEquals ("1,500,000", _fi (1500000, "0,000"));
    assertEquals ("1,500,000", _fi (1500000, "#,###,000"));
    assertEquals ("1500000", _fi (1500000, "#"));
    assertEquals ("1500000", _fi (1500000, "#a"));
    assertEquals ("1500𐄀0,00", _fi (1500000, "###𐄀0,00"));
    assertEquals ("602)347-826", _fi (602347826, "#(000)000-000"));
    assertEquals ("SECOND", _fi (2, "W;o"));
    assertEquals ("Erster", _fi (1, "Ww;o(-er)", "de"));
    assertEquals ("Deuxième", _fi (2, "Ww;o", "fr"));
    assertEquals ("Quinto", _fi (5, "Ww;o(-o)", "it"));
    assertEquals ("Quinta", _fi (5, "Ww;o(-a)", "it"));
    assertEquals ("1st", _fi (1, "1;o(-en)"));
    assertEquals ("One", _fi (1, "Ww;t"));
    assertEquals ("1234th", _fi (1234, "()Ww;o"));
    assertEquals ("1234", _fi (1234, "\n"));
    assertEquals ("𐒡,𐒢𐒣𐒤", _fi (1234, "#,𐒠𐒠𐒠"));
    assertEquals ("-85th", _fi (-85, "1;o"));
    assertEquals ("-Fifth", _fi (-5, "Ww;o"));
    assertEquals ("Zero", _fi (0, "Ww"));
    assertEquals ("③", _fi (3, "①"));
    assertEquals ("⑶", _fi (3, "⑴"));
    assertEquals ("⒊", _fi (3, "⒈"));
    assertEquals ("Γ", _fi (3, "Α"));
    assertEquals ("γ", _fi (3, "α"));
    assertEquals ("二千二十五", _fi (2025, "一"));
    assertEquals ("12 345 678 901", _fi (12345678901L, "# 000"));
    assertEquals ("123", _fi (123, "#0"));
    assertEquals ("001", _fi (1, "001;"));
    assertEquals ("1;234", _fi (1234, "#;##1;"));
    assertEquals ("Eleven", _fi (11, "Ww", "en"));
    assertEquals ("Eleven", _fi (11, "Ww", "@*!+%"));
    assertEquals ("Zwanzigste", _fi (20, "Ww;o(%spellout-ordinal)", "de"));
    assertEquals ("zeroth", _fi (0, "w;o"));
    assertEquals ("one hundredth", _fi (100, "w;o"));
    assertEquals ("12345,6,78,9", _fi (123456789, "0,0,00,0"));
    assertEquals ("1,23,45,67,89", _fi (123456789, "00,00,00"));
    assertEquals ("12345,67,89", _fi (123456789, "000,00,00"));
    assertEquals ("-8,500th", _fi (-8500, "#,##1;o"));
    assertEquals ("one thousand nine hundred and ninety", _fi (1990, "w"));
    assertEquals ("two thousand and first", _fi (2001, "w;o"));
    assertEquals ("DREISSIG", _fi (30, "W", "de"));
    assertEquals ("",
                  _call ("format-integer", XdmSequence.EMPTY, _s ("Ww")));
    assertEquals ("123456789012345678901234567890",
                  _call ("format-integer",
                         XdmSequence.of (XdmAtomic.integer (new BigInteger ("123456789012345678901234567890"))),
                         _s ("1")));
    for (final String sBad : new String [] { "", "0,000,", "11#0,000", ",123", "0,00,,000", "1;o(-er)z", "Ww;o()(", "123١", "0#", ";", "1o", "Ww;o(" })
      _err ("FODF1310", "format-integer", XdmSequence.of (XdmAtomic.integer (1)), _s (sBad));
  }

  @Test
  public void testFormatNumber ()
  {
    assertEquals ("087,504.481200", _fn (_dec ("87504.4812"), "000,000.000000"));
    assertEquals ("1,235,464.8812", _fn (_dec ("1235464.8812"), "##,###,000.000###"));
    assertEquals ("-102,136.4812", _fn (_dec ("-102136.4812"), "000,000.000###"));
    assertEquals ("87,504.4812", _fn (_dec ("-87504.4812"), "000,000.000###;###,###.000###"));
    assertEquals ("48.57%", _fn (_dec ("0.4857"), "###.###%"));
    assertEquals ("485.7‰", _fn (_dec ("0.4857"), "###.###‰"));
    assertEquals ("¤95.4857", _fn (_dec ("95.4857"), "¤###.####"));
    assertEquals ("PREFIX185.2812SUFFIX", _fn (_dec ("185.2812"), "PREFIX##00.000###SUFFIX"));
    assertEquals ("Infinity", _fn (_dbl (Double.POSITIVE_INFINITY), "###############################"));
    assertEquals ("-Infinity", _fn (_dbl (Double.NEGATIVE_INFINITY), "###############################"));
    assertEquals ("NaN", _fn (_dbl (Double.NaN), "#############"));
    assertEquals ("NaN", _fn (XdmSequence.EMPTY, "###.###"));
    assertEquals ("-26,931.4", _fn (_dec ("-26931.4"), "+###,###.###;-###,###.###"));
    assertEquals ("26,931.4", _fn (_dec ("-26931.4"), "###,###.###;###,###.###"));
    assertEquals ("9876,543,21.00", _fn (_dec ("987654321"), "###,##0,00.00"));
    assertEquals ("239236.59", _fn (_dec ("239236.588"), "00000.00"));
    assertEquals ("10,000000,000000,000000,000000", _fn (_dbl (1e25), "#,######"));
    assertEquals ("10000000000000000000000000", _fn (_dbl (1e25), "#####################"));
    assertEquals ("1234567890.123", _fn (_dec ("1234567890.123456"), "000.000"));
    assertEquals ("12.34", _fn (_dec ("12.34"), "##.##"));
    assertEquals ("123,456,789.34", _fn (_dec ("123456789.34"), "#,###.##"));
    assertEquals ("0,012.34", _fn (_dec ("12.34"), "9,999.99"));
    assertEquals ("042", _fn (XdmSequence.of (XdmAtomic.integer (42)), "001"));
    assertEquals ("12346", _fn (_dec ("12345.678"), "9."));
    assertEquals ("12345.7", _fn (_dec ("12345.678"), ".9"));
    assertEquals (".2", _fn (_dec ("0.2"), ".#"));
    assertEquals ("1.2346e004", _fn (_dec ("12345.678"), "9.9999e999"));
    assertEquals ("12345.6780eDog", _fn (_dec ("12345.678"), "9.9999eDog"));
    assertEquals ("1.2345e04", _fn (XdmSequence.of (XdmAtomic.integer (12345)), "9.9999e99"));
    assertEquals ("5.0000e-02", _fn (_dec ("0.05"), "9.9999e99"));
    assertEquals ("1.2345e09", _fn (_dbl (123.45e7), "9.9999e99"));
    assertEquals ("9.00001000020000345e-01", _fn (_dec ("0.900001000020000345"), "9.99999999999999999e99"));
    assertEquals ("1.23456789012345678e17", _fn (_dec ("123456789012345678"), "9.99999999999999999e99"));
    assertEquals ("123.46e02", _fn (_dec ("12345.678"), "999.99e99"));
    assertEquals ("12.35e03", _fn (_dec ("12345.678"), "#99.99e99"));
    assertEquals ("0.12e05", _fn (_dec ("12345.678"), "#.99e99"));
    assertEquals ("0.5e02", _fn (_dec ("45.234"), "#.#e99"));
    assertEquals (".2e0", _fn (_dec ("0.2"), ".#e9"));
    assertEquals (".200e0", _fn (_dec ("0.2"), ".999e9"));
    assertEquals ("0.2e0", _fn (_dec ("0.2"), "#.e9"));
    assertEquals ("2e-1", _fn (_dec ("0.2"), "9e9"));
    assertEquals ("123e02", _fn (_dec ("12345.678"), "999.e99"));
    assertEquals ("-123.46e02", _fn (_dec ("-12345.678"), "999.99e99"));
    assertEquals ("-1.23e-04", _fn (_dec ("-0.00012345678"), "9.99e99"));
    assertEquals ("end1.2346e04end", _fn (_dec ("12345.678"), "end9.9999e99end"));
    assertEquals ("12345.6,78,9", _fn (_dec ("12345.6789012345"), "#.#,##,#"));
    assertEquals ("12345.67,89,01", _fn (_dec ("12345.6789012345"), "#.##,##,##"));
    assertEquals ("0", _fn (_dec ("0.2"), "#"));
    assertEquals (".2", _fn (_dec ("0.2"), "#.0"));
    assertEquals ("0", _fn (_dec ("0.2"), "#."));
    assertEquals ("this.2", _fn (_dec ("0.2"), "this.#"));
    assertEquals ("0suffix", _fn (_dec ("0.2"), "#.suffix"));
    assertEquals ("000.2", _fn (_dec ("0.2"), "000.0"));
    assertEquals (".0", _fn (XdmSequence.of (XdmAtomic.integer (0)), "###.###"));
    assertEquals ("0.1e1", _fn (_dec ("1.2"), "#e0"));
    assertEquals ("2e-1", _fn (_dec ("0.2"), "0e0"));
    assertEquals (".1e1", _fn (_dec ("1.2"), ".#e0"));
    assertEquals ("2.0e-1", _fn (_dec ("0.2"), "0.0e0"));
    assertEquals ("200.0e-3", _fn (_dec ("0.2"), "000.0e0"));
    assertEquals (".2e-2", _fn (_dec ("0.002"), ".0e0"));
    assertEquals ("1.0e0", _fn (_dec ("0.99999999"), ".#e0"));
    assertEquals ("1e0", _fn (_dec ("0.99999999"), "#.#e0"));
    assertEquals ("10.0e-1", _fn (_dec ("0.99999999"), "0.0e0"));
    assertEquals ("6421,20", _fn (XdmSequence.of (XdmAtomic.integer (642120)), "####,##"));
    assertEquals ("64,21,20", _fn (XdmSequence.of (XdmAtomic.integer (642120)), "##,##"));
    assertEquals ("6,4,2,1,2,0", _fn (XdmSequence.of (XdmAtomic.integer (642120)), "#,#,#"));
    assertEquals ("6421,2,0", _fn (XdmSequence.of (XdmAtomic.integer (642120)), "##,#,#"));
    assertEquals ("0.0e0", _fn (_dbl (0.0), "0.0e0"));
    assertEquals ("-0.0e0", _fn (_dbl (-0.0), "0.0e0"));
    assertEquals ("0e00", _fn (XdmSequence.of (XdmAtomic.integer (0)), "0e01"));
    assertEquals ("0e0", _fn (XdmSequence.of (XdmAtomic.integer (0)), "#.#e9"));
    assertEquals ("-0", _fn (_dbl (-0.0), "#"));
    assertEquals ("100", _fn (_dbl (1e2), "#,000"));
    assertEquals ("1.00", _fn (_dbl (0.9952832031249997), "###,##0.00"));
    assertEquals ("1,2,3,4,5,6,7,8,9", _fn (_dec ("123456789"), "#,#"));
    assertEquals ("1,23,45,67,89", _fn (_dec ("123456789"), "#,##,00"));
    assertEquals ("123,456,78,9", _fn (_dec ("123456789"), "####,###,##,0"));
    assertEquals ("Infinity%", _fn (_dbl (1e308), "0%"));
    assertEquals ("-Infinity%", _fn (_dbl (-1e308), "0%"));
    assertEquals ("0.10", _fn (_dbl (0.1), "0.00"));
    assertEquals (".00", _fn (_dbl (0), "#.00"));
    assertEquals (".0", _fn (_dbl (0), "#.##"));
    assertEquals ("57%", _fn (XdmSequence.of (XdmAtomic.flt (0.57f)), "0%"));
    for (final String sBad : new String [] { "9.9999E999",
                                             "9.99e99e99",
                                             "9.9999e999%",
                                             "9.9999e,",
                                             ".e99",
                                             "#,.##",
                                             "#.,##",
                                             "#,",
                                             "#,,###",
                                             "#.#,,#",
                                             "000.##0",
                                             "fred.ginger",
                                             "#;#;#",
                                             "##.##.##",
                                             "#%%",
                                             "#%‰",
                                             "0$0" })
      _err ("FODF1310", "format-number", _dec ("1"), _s (sBad));
  }

  @Test
  public void testFormatNumberDecimalFormats ()
  {
    final StaticContext aSC = new StaticContext ();
    aSC.declareNamespace ("b", "http://a.ns/");
    final FormatDecimalFormat aEuro = FormatDecimalFormat.builder ()
                                                         .decimalSeparator (',')
                                                         .groupingSeparator ('.')
                                                         .build ();
    aSC.addDecimalFormat (new XdmQName ("http://a.ns/", "test"), aEuro);
    aSC.addDecimalFormat (new XdmQName ("foo"),
                          FormatDecimalFormat.builder ()
                                             .infinity ("off-the-scale")
                                             .nan ("non-numeric")
                                             .minusSign ('_')
                                             .zeroDigit (0x0660)
                                             .digit ('!')
                                             .patternSeparator ('\\')
                                             .build ());
    final DynamicContext aCtx = new DynamicContext (aSC);
    assertEquals ("0.012,34", _callCtx (aCtx, "format-number", _dec ("12.34"), _s ("0.000,00"), _s (" b:test ")));
    assertEquals ("0.012,34",
                  _callCtx (aCtx, "format-number", _dec ("12.34"), _s ("0.000,00"), _s ("Q{http://a.ns/}test ")));
    assertEquals ("off-the-scale",
                  _callCtx (aCtx, "format-number", _dbl (Double.POSITIVE_INFINITY), _s ("!!!"), _s ("foo")));
    assertEquals ("non-numeric", _callCtx (aCtx, "format-number", _dbl (Double.NaN), _s ("!!!"), _s ("foo")));
    assertEquals ("_٢٦,٩٣١.٤",
                  _callCtx (aCtx, "format-number", _dec ("-26931.4"), _s ("!!!,!!!.!!!"), _s ("foo")));
    assertEquals ("-\u0662\u0666,\u0669\u0663\u0661.\u0664",
                  _callCtx (aCtx, "format-number", _dec ("-26931.4"), _s ("+!!,!!!.!!!\\-!!!,!!!.!!!"), _s ("foo")));
    assertEquals ("+\u0662\u0666,\u0669\u0663\u0661.\u0664",
                  _callCtx (aCtx, "format-number", _dec ("26931.4"), _s ("+!!,!!!.!!!\\-!!!,!!!.!!!"), _s ("foo")));
    // Empty name: default format
    assertEquals ("12.34", _callCtx (aCtx, "format-number", _dec ("12.34"), _s ("#.##"), XdmSequence.EMPTY));
    try
    {
      _callCtx (aCtx, "format-number", _dec ("1"), _s ("#"), _s ("foo:bar"));
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("FODF1280", ex.getCode ());
    }
    try
    {
      _callCtx (aCtx, "format-number", _dec ("1"), _s ("#"), _s ("BadFormat"));
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("FODF1280", ex.getCode ());
    }
  }

  @Test
  public void testFormatDateBasics ()
  {
    final String d = "2003-09-07";
    assertEquals ("2003-09-7", _date (d, "[Y]-[M01]-[D]"));
    assertEquals ("9-7-2003", _date (d, "[M]-[D]-[Y]"));
    assertEquals ("7 IX 2003", _date (d, "[D1] [MI] [Y]"));
    assertEquals ("[2003-09-07]", _date (d, "[[[Y]-[M01]-[D01]]]"));
    assertEquals ("[2003-09-07]", _date (d, "[[[Y0001]-[M01]-[D01]]]"));
    assertEquals ("(03-09-07)", _date (d, "([Y01]-[M01]-[D01])"));
    assertEquals ("๒๐๐๓-๐๙-๗", _date (d, "[Y๐๐๐๑]-[M๐๑]-[D๑]"));
    assertEquals ("0985", _date ("0985-03-01", "[Y,4-4]"));
    assertEquals ("985", _date ("0985-03-01", "[Y,3-4]"));
    assertEquals ("85", _date ("0985-03-01", "[Y,2-2]"));
    assertEquals ("985", _date ("0985-03-01", "[Y,*-4]"));
    assertEquals ("0003", _date ("0985-03-01", "[M,4-4]"));
    assertEquals ("03", _date ("0985-03-01", "[M,2-5]"));
    assertEquals ("3", _date ("0985-03-01", "[M,*-2]"));
    assertEquals ("003", _date ("0985-03-01", "[M,3]"));
    assertEquals ("18 05 2012", _date ("2012-05-18", "[ D 01 ] [M 0 1] [ Y 0 0 0 1 ]"));
    assertEquals ("2;012", _date ("2012-05-18", "[Y9;999]"));
    assertEquals ("2,012", _date ("2012-05-18", "[Y9,999,*]"));
    assertEquals ("2,01-2", _date ("2012-05-18", "[Y9,99-9,*]"));
    assertEquals ("1200", _date ("1200-01-01", "[Y###9]"));
    assertEquals ("900", _date ("0900-01-01", "[Y###9]"));
    assertEquals ("0000000000000000000002012", _date ("2012-05-18", "[Y9999,25]"));
    assertEquals ("32", _date ("2016-02-01", "[d]"));
    // Absurd widths are clamped
    assertEquals (1000, _date ("2016-02-01", "[Y,999999999]").length ());
    assertEquals ("54321", _date ("654321-01-01", "[Y#0,2-5]"));
    assertEquals ("1.6", _date ("2016-01-01", "[Y#.0]"));
    assertEquals ("dcccxvii", _date ("0817-01-01", "[Yi,4-4]"));
    assertEquals ("MCMLI", _date ("1951-01-01", "[YI]"));
    // ISO week, ISO day of week, week of month
    assertEquals ("18", _fd (EAtomicType.DATE, "2004-05-01", "[W]", null, "ISO", null));
    assertEquals ("53", _date ("2005-01-01", "[W]"));
    assertEquals ("04", _fd (EAtomicType.DATE, "2004-01-01", "[F01]", null, "Q{}ISO", null));
    assertEquals ("1", _date ("2005-12-04", "[w]"));
    assertEquals ("2", _date ("2005-12-07", "[w]"));
    for (final String sBad : new String [] { "[bla]", "[Y999#]", "[Y##9#]", "[Y#.0,4-3]", "[yY]", "[Y", "[]", "[Y,0-2]" })
      _err ("FOFD1340", "format-date", _v (EAtomicType.DATE, "2012-05-18"), _s (sBad));
    for (final String sBad : new String [] { "[H]", "[h]", "[P]", "[m]", "[s]", "[f]" })
      _err ("FOFD1350", "format-date", _v (EAtomicType.DATE, "2012-05-18"), _s (sBad));
    assertEquals (null, _call ("format-date", XdmSequence.EMPTY, _s ("[Y]")));
  }

  @Test
  public void testFormatDateTimezones ()
  {
    assertEquals ("-14:00", _date ("1985-03-01-14:00", "[Z]"));
    assertEquals ("+05:30", _date ("1985-03-01+05:30", "[Z]"));
    assertEquals ("GMT-13:30", _date ("1985-03-01-13:30", "[z]"));
    assertEquals ("GMT-14", _date ("1985-03-01-14:00", "[z0]"));
    assertEquals ("GMT-13:30", _date ("1985-03-01-13:30", "[z0]"));
    assertEquals ("GMT+00:00", _date ("1985-03-01Z", "[z]"));
    assertEquals ("Y", _date ("1985-03-01-12:00", "[ZZ]"));
    assertEquals ("N", _date ("1985-03-01-01:00", "[ZZ]"));
    assertEquals ("Z", _date ("1985-03-01Z", "[ZZ]"));
    assertEquals ("A", _date ("1985-03-01+01:00", "[ZZ]"));
    assertEquals ("I", _date ("1985-03-01+09:00", "[ZZ]"));
    assertEquals ("K", _date ("1985-03-01+10:00", "[ZZ]"));
    assertEquals ("M", _date ("1985-03-01+12:00", "[ZZ]"));
    assertEquals ("J", _date ("1987-12-13", "[ZZ]"));
    assertEquals ("+05:30", _date ("1987-12-13+05:30", "[ZZ]"));
    assertEquals ("+13:00", _date ("1987-12-13+13:00", "[ZZ]"));
    assertEquals ("GMT-14~00", _date ("1985-03-01-14:00", "[z00~00]"));
    assertEquals ("+٠٥:٣٠", _date ("2012-05-18+05:30", "[Z٠٠:٠٠]"));
    assertEquals ("-9:30", _date ("1985-03-01-09:30", "[Z0:01]"));
    assertEquals ("-930", _date ("1985-03-01-09:30", "[Z999]"));
    assertEquals ("+1400", _date ("1985-03-01+14:00", "[Z999]"));
    assertEquals ("-09", _date ("1985-03-01-09:00", "[Z99]"));
    assertEquals ("-09:30", _date ("1985-03-01-09:30", "[Z99]"));
    assertEquals ("-9", _date ("1985-03-01-09:00", "[Z0t]"));
    assertEquals ("Z", _date ("1985-03-01Z", "[Z0t]"));
    assertEquals ("GMT-14", _date ("1985-03-01-14:00", "[z,2-2]"));
    assertEquals ("", _date ("1985-03-01", "[Z]"));
  }

  @Test
  public void testFormatDateNames ()
  {
    final String [] aMonths = { "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER" };
    final String [] aMonthsDe = { "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez" };
    for (int i = 1; i <= 12; ++i)
    {
      final String sDate = "2004-" + (i < 10 ? "0" : "") + i + "-01";
      assertEquals (aMonths[i - 1], _fd (EAtomicType.DATE, sDate, "[MN]", "en", null, null));
      assertEquals (aMonths[i - 1].substring (0, 3), _fd (EAtomicType.DATE, sDate, "[MN,3-3]", "en", null, null));
      assertEquals (aMonthsDe[i - 1], _fd (EAtomicType.DATE, sDate, "[MNn,3-3]", "de", null, null));
    }
    assertEquals ("MÄRZ", _fd (EAtomicType.DATE, "2004-03-01", "[MN]", "de", null, null));
    assertEquals ("märz", _fd (EAtomicType.DATE, "2004-03-01", "[Mn]", "de", null, null));
    // 2003-12-08 is a Monday
    final String [] aDays = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };
    final String [] aDaysDe = { "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So" };
    for (int i = 0; i < 7; ++i)
    {
      final String sLex = 8 + i < 10 ? "2003-12-0" + (8 + i) : "2003-12-" + (8 + i);
      assertEquals (aDays[i], _fd (EAtomicType.DATE, sLex, "[FNn]", "en", null, null));
      assertEquals (aDays[i].substring (0, 3), _fd (EAtomicType.DATE, sLex, "[FNn,3-3]", "en", null, null));
      assertEquals (aDays[i].substring (0, 3), _fd (EAtomicType.DATE, sLex, "[FNn,3-4]", "en", null, null));
      assertEquals (aDaysDe[i], _fd (EAtomicType.DATE, sLex, "[FNn,2-2]", "de", null, null));
    }
    assertEquals ("lundi 8 décembre 2003", _fd (EAtomicType.DATE, "2003-12-08", "[Fn] [D] [Mn] [Y]", "fr", null, null));
    assertEquals ("Lunedì 8 Dicembre 2003",
                  _fd (EAtomicType.DATE, "2003-12-08", "[FNn] [D] [MNn] [Y]", "it", null, null));
    assertEquals ("1st 2nd 3rd 11th 22nd",
                  _date ("2003-12-01", "[D1o]") +
                                           " " +
                                           _date ("2003-12-02", "[D1o]") +
                                           " " +
                                           _date ("2003-12-03", "[D1o]") +
                                           " " +
                                           _date ("2003-12-11", "[D1o]") +
                                           " " +
                                           _date ("2003-12-22", "[D1o]"));
    assertEquals ("1991st", _date ("1991-12-01", "[Y1o]"));
    assertEquals ("2000th", _date ("2000-12-01", "[Y0001o]"));
    assertEquals ("TWENTY-ONE", _date ("2003-12-21", "[DW]"));
    assertEquals ("twenty-first", _date ("2003-12-21", "[Dwo]"));
    assertEquals ("Thirty-One", _date ("2003-12-31", "[DWw]"));
    assertEquals ("einunddreißigste", _fd (EAtomicType.DATE, "2003-12-31", "[Dwo]", "de", null, null));
    assertEquals ("1990AD", _date ("1990-12-01", "[Y][EN]"));
    assertEquals ("55BC", _date ("-0055-12-01", "[Y][EN]"));
    // Unsupported language: English with a marker, only if language data was
    // used
    assertEquals ("[Language: en]March", _fd (EAtomicType.DATE, "2006-03-01", "[MNn]", "xib", null, null));
    assertEquals ("03", _fd (EAtomicType.DATE, "2006-03-01", "[M01]", "xib", null, null));
    // Calendars
    assertEquals ("[Calendar: AD]03", _fd (EAtomicType.DATE, "2006-03-01", "[M01]", "en", "CB", null));
    assertEquals ("[Calendar: AD]03",
                  _fd (EAtomicType.DATE,
                       "2006-03-01",
                       "[M01]",
                       "en",
                       "Q{http://calendar.example.com/non-existent-calendar}CB",
                       null));
    assertEquals ("[Calendar: AD]03", _fd (EAtomicType.DATE, "2006-03-01", "[M01]", "en", "cal:CB", null));
    assertEquals ("03", _fd (EAtomicType.DATE, "2006-03-01", "[M01]", "en", "AD", null));
    for (final String sCal : new String [] { "Q{}ZODIAC", "ZODIAC", ":w", "Q{}1" })
      _err ("FOFD1340",
            "format-date",
            _v (EAtomicType.DATE, "2006-03-01"),
            _s ("[M01]"),
            _s ("en"),
            _s (sCal),
            XdmSequence.EMPTY);
  }

  @Test
  public void testFormatDateTime ()
  {
    final String t = "2011-07-01T09:15:06.456";
    assertEquals ("09:15", _dt (t, "[H01]:[m01]"));
    assertEquals ("9:15", _dt (t, "[H]:[m]"));
    assertEquals ("9:15:06", _dt (t, "[H]:[m]:[s]"));
    assertEquals ("9:15:6", _dt (t, "[H]:[m]:[s1]"));
    assertEquals ("9:15:06:456", _dt (t, "[H]:[m]:[s01]:[f001]"));
    assertEquals ("9:15:06.4", _dt (t, "[H]:[m]:[s].[f,1-1]"));
    assertEquals ("9:15:06.45", _dt (t, "[H]:[m]:[s].[f01]"));
    assertEquals ("2011-07-01T09:15:06.456", _dt (t, "[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]"));
    assertEquals ("miv ", _dt ("1004-01-01T12:00:00", "[Yi,4-4]"));
    assertEquals ("4560", _dt ("0985-03-01T09:15:06.456", "[f,4-4]"));
    assertEquals ("45", _dt ("0985-03-01T09:15:06.456", "[f,*-2]"));
    assertEquals ("9:15 am", _dt (t, "[h]:[m01] [P]"));
    assertEquals ("12 pm", _dt ("2011-07-01T12:00:00", "[h] [P]"));
    assertEquals ("12 AM", _dt ("2011-07-01T00:00:00", "[h] [PN]"));
    assertEquals ("a", _dt ("2011-07-01T00:00:00", "[PNn,1-1]").toLowerCase (Locale.ROOT));
    assertEquals ("Gregorian", _dt (t, "[C]"));
    // $place with an Olson timezone name, DST included
    assertEquals ("15 February 2015 @ 13:00 +01:00",
                  _fd (EAtomicType.DATE_TIME,
                       "2015-02-15T12:00:00Z",
                       "[D] [MNn] [Y0001] @ [H01]:[m01] [Z]",
                       "en",
                       null,
                       "Europe/Paris"));
    assertEquals ("15 August 2015 @ 14:00 +02:00",
                  _fd (EAtomicType.DATE_TIME,
                       "2015-08-15T12:00:00Z",
                       "[D] [MNn] [Y0001] @ [H01]:[m01] [Z]",
                       "en",
                       null,
                       "Europe/Paris"));
    assertEquals ("15 February 2015 @ 13:00 CET",
                  _fd (EAtomicType.DATE_TIME,
                       "2015-02-15T12:00:00Z",
                       "[D] [MNn] [Y0001] @ [H01]:[m01] [ZN]",
                       "en",
                       null,
                       "Europe/Paris"));
    assertEquals ("15 August 2015 @ 14:00 CEST",
                  _fd (EAtomicType.DATE_TIME,
                       "2015-08-15T12:00:00Z",
                       "[D] [MNn] [Y0001] @ [H01]:[m01] [ZN]",
                       "en",
                       null,
                       "Europe/Paris"));
    assertEquals ("08:00 EDT",
                  _fd (EAtomicType.DATE_TIME,
                       "2015-08-15T12:00:00Z",
                       "[H01]:[m01] [ZN]",
                       "en",
                       null,
                       "America/New_York"));
    // Without timezone, $place is ignored
    assertEquals ("12:00",
                  _fd (EAtomicType.DATE_TIME, "2015-08-15T12:00:00", "[H01]:[m01]", "en", null, "America/New_York"));
    // An unknown place is ignored
    assertEquals ("12:00",
                  _fd (EAtomicType.DATE_TIME, "2015-08-15T12:00:00Z", "[H01]:[m01]", "en", null, "Nowhere/Never"));
    _err ("FOFD1340", "format-dateTime", _v (EAtomicType.DATE_TIME, t), _s ("[bla]"));
  }

  @Test
  public void testFormatTime ()
  {
    final String t = "09:15:06.456";
    assertEquals ("09:15:06", _time (t, "[H01]:[m01]:[s01]"));
    assertEquals ("015", _time (t, "[m,3]"));
    assertEquals ("456", _time (t, "[f,2-5]"));
    assertEquals ("๔๕๖", _time (t, "[f๐๐๐]"));
    assertEquals ("٩:١٥:٠٦:٤٥٦",
                  _time (t, "[H٠]:[m٠]:[s٠٠]:[f٠٠٠]"));
    assertEquals ("am/Am/AM", _time (t, "[Pn]/[PNn]/[PN]"));
    assertEquals ("pm/Pm/PM", _time ("15:15:06.456", "[Pn]/[PNn]/[PN]"));
    assertEquals ("0060", _time ("09:15:06.006", "[f,4-4]"));
    assertEquals ("00", _time ("09:15:06.006", "[f,2-2]"));
    assertEquals ("0", _time ("09:15:06.006", "[f,*-2]"));
    assertEquals ("006", _time ("09:15:06.006", "[f,3]"));
    assertEquals ("1", _time ("09:15:06.100", "[f,1-4]"));
    assertEquals ("10", _time ("09:15:06.100", "[f,2-5]"));
    assertEquals ("100", _time ("09:15:06.100", "[f,3]"));
    assertEquals ("123", _time ("12:01:01.123", "[f]"));
    assertEquals ("12", _time ("12:01:01.123", "[f99]"));
    assertEquals ("123", _time ("12:01:01.123", "[f99#]"));
    assertEquals ("12", _time ("12:01:01.12", "[f99#]"));
    assertEquals ("120", _time ("12:01:01.12", "[f777]"));
    assertEquals ("12", _time ("12:01:01.127", "[f01]"));
    assertEquals ("1'3'5", _time ("12:01:01.135", "[f0'0'0]"));
    assertEquals ("13'5", _time ("12:01:01.135", "[f00'0]"));
    assertEquals ("135000", _time ("12:01:01.135", "[f,6-*]"));
    assertEquals ("00", _time ("12:01:01", "[f,2-4]"));
    assertEquals ("0", _time ("12:01:01", "[f]"));
    assertEquals ("123", _time ("12:01:01.123", "[f111,2-2]"));
    assertEquals ("١~٢~٣", _time ("12:01:01.123", "[f٠~٠~٠]"));
    assertEquals ("123", _time ("12:01:01.123", "[ f 0 0 0 ]"));
    assertEquals ("100", _time ("12:01:01.1", "[f1###,3-3]"));
    assertEquals ("50", _time ("12:01:01.5", "[f1###,2-3]"));
    assertEquals ("123456789012", _time ("12:01:01.123456789012", "[f]"));
    for (final String sBad : new String [] { "[f#99]", "[f1,0-3]", "[f1,*-0]", "[f1,3-2]", "[f#9٠]", "[H9#]", "[y]" })
      _err ("FOFD1340", "format-time", _v (EAtomicType.TIME, "12:01:01.123"), _s (sBad));
    for (final String sBad : new String [] { "[Y]", "[M]", "[d]", "[D]", "[F]", "[W]", "[w]", "[E]" })
      _err ("FOFD1350", "format-time", _v (EAtomicType.TIME, "12:01:01.123"), _s (sBad));
  }

  @Test
  public void testAllRegistered ()
  {
    for (final String s : new String [] { "format-integer#2",
                                          "format-integer#3",
                                          "format-number#2",
                                          "format-number#3",
                                          "format-dateTime#2",
                                          "format-dateTime#5",
                                          "format-date#2",
                                          "format-date#5",
                                          "format-time#2",
                                          "format-time#5" })
    {
      final int nHash = s.indexOf ('#');
      assertTrue (s,
                  LIB.getFunction (new XdmQName (XdmConstants.NS_FN, s.substring (0, nHash)),
                                   Integer.parseInt (s.substring (nHash + 1))) != null);
    }
  }
}
