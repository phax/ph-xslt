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
import java.util.Arrays;

import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmDurationValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnDateTime}.
 *
 * @author Philip Helger
 */
public final class FnDateTimeTest
{
  private static final FunctionLibrary LIB = new FunctionLibrary ();
  static
  {
    FnDateTime.register (LIB);
  }

  private static XdmSequence _a (final EAtomicType eType, final String sLexical)
  {
    if (eType.isDurationFamily ())
      return XdmSequence.of (XdmAtomic.of (eType, XdmDurationValue.parse (eType, sLexical)));
    if (eType == EAtomicType.STRING)
      return XdmSequence.of (XdmAtomic.string (sLexical));
    return XdmSequence.of (XdmAtomic.of (eType, XdmDateTimeValue.parse (eType, sLexical)));
  }

  private static String _lex (final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return "()";
    final XdmAtomic a = (XdmAtomic) aSeq.getFirst ();
    final Object v = a.getValue ();
    final String sValue;
    if (v instanceof final XdmDateTimeValue aDT)
      sValue = aDT.toLexical (a.getType ());
    else
      if (v instanceof final XdmDurationValue aDur)
        sValue = aDur.toLexical (a.getType ());
      else
        if (v instanceof final BigDecimal aBD)
          sValue = aBD.toPlainString ();
        else
          sValue = v.toString ();
    return a.getType ().getLocalName () + ":" + sValue;
  }

  private static String _callTz (final int nImplicitTz, final String sName, final XdmSequence... aArgs)
  {
    final FunctionLibrary.FunctionDef aDef = LIB.getFunction (new XdmQName (XdmConstants.NS_FN, sName),
                                                              aArgs.length);
    assertNotNull (sName + "#" + aArgs.length, aDef);
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    aCtx.setImplicitTimezoneMinutes (nImplicitTz);
    return _lex (aDef.getImpl ().call (aCtx, Arrays.asList (aArgs)));
  }

  private static String _call (final String sName, final XdmSequence... aArgs)
  {
    return _callTz (0, sName, aArgs);
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

  private static XdmSequence _dt (final String s)
  {
    return _a (EAtomicType.DATE_TIME, s);
  }

  private static XdmSequence _date (final String s)
  {
    return _a (EAtomicType.DATE, s);
  }

  private static XdmSequence _time (final String s)
  {
    return _a (EAtomicType.TIME, s);
  }

  private static XdmSequence _dtd (final String s)
  {
    return _a (EAtomicType.DAY_TIME_DURATION, s);
  }

  @Test
  public void testDateTimeConstructor ()
  {
    assertEquals ("dateTime:2004-05-06T07:08:09", _call ("dateTime", _date ("2004-05-06"), _time ("07:08:09")));
    assertEquals ("dateTime:1999-12-31T12:00:00Z", _call ("dateTime", _date ("1999-12-31"), _time ("12:00:00Z")));
    assertEquals ("dateTime:1999-12-31T12:00:00+01:00",
                  _call ("dateTime", _date ("1999-12-31+01:00"), _time ("12:00:00")));
    assertEquals ("dateTime:1999-12-31T00:00:00", _call ("dateTime", _date ("1999-12-31"), _time ("24:00:00")));
    assertEquals ("dateTime:-0044-03-15T12:00:00.123",
                  _call ("dateTime", _date ("-0044-03-15"), _time ("12:00:00.123")));
    assertEquals ("()", _call ("dateTime", XdmSequence.EMPTY, _time ("07:08:09")));
    assertEquals ("()", _call ("dateTime", _date ("2004-05-06"), XdmSequence.EMPTY));
    _err ("FORG0008", "dateTime", _date ("1999-12-31+01:00"), _time ("12:00:00Z"));
  }

  @Test
  public void testComponents ()
  {
    final XdmSequence aDT = _dt ("2004-05-06T07:08:09.5+02:00");
    assertEquals ("integer:2004", _call ("year-from-dateTime", aDT));
    assertEquals ("integer:5", _call ("month-from-dateTime", aDT));
    assertEquals ("integer:6", _call ("day-from-dateTime", aDT));
    assertEquals ("integer:7", _call ("hours-from-dateTime", aDT));
    assertEquals ("integer:8", _call ("minutes-from-dateTime", aDT));
    assertEquals ("decimal:9.5", _call ("seconds-from-dateTime", aDT));
    assertEquals ("decimal:9", _call ("seconds-from-dateTime", _dt ("2004-05-06T07:08:09")));
    assertEquals ("dayTimeDuration:PT2H", _call ("timezone-from-dateTime", aDT));
    assertEquals ("dayTimeDuration:PT0S", _call ("timezone-from-dateTime", _dt ("2004-05-06T07:08:09Z")));
    assertEquals ("()", _call ("timezone-from-dateTime", _dt ("2004-05-06T07:08:09")));
    assertEquals ("()", _call ("year-from-dateTime", XdmSequence.EMPTY));
    assertEquals ("integer:-44", _call ("year-from-dateTime", _dt ("-0044-03-15T00:00:00")));
    assertEquals ("integer:2000", _call ("year-from-dateTime", _dt ("1999-12-31T24:00:00")));
    assertEquals ("integer:0", _call ("hours-from-dateTime", _dt ("1999-12-31T24:00:00")));

    assertEquals ("integer:2004", _call ("year-from-date", _date ("2004-05-06")));
    assertEquals ("integer:5", _call ("month-from-date", _date ("2004-05-06")));
    assertEquals ("integer:6", _call ("day-from-date", _date ("2004-05-06")));
    assertEquals ("dayTimeDuration:-PT5H", _call ("timezone-from-date", _date ("2004-05-06-05:00")));

    assertEquals ("integer:7", _call ("hours-from-time", _time ("07:08:09")));
    assertEquals ("integer:0", _call ("hours-from-time", _time ("24:00:00")));
    assertEquals ("integer:8", _call ("minutes-from-time", _time ("07:08:09")));
    assertEquals ("decimal:9.001", _call ("seconds-from-time", _time ("07:08:09.001")));
    assertEquals ("dayTimeDuration:PT14H", _call ("timezone-from-time", _time ("07:08:09+14:00")));
  }

  @Test
  public void testDurationComponents ()
  {
    final XdmSequence aD = _a (EAtomicType.DURATION, "P2Y6M3DT4H5M6.25S");
    assertEquals ("integer:2", _call ("years-from-duration", aD));
    assertEquals ("integer:6", _call ("months-from-duration", aD));
    assertEquals ("integer:3", _call ("days-from-duration", aD));
    assertEquals ("integer:4", _call ("hours-from-duration", aD));
    assertEquals ("integer:5", _call ("minutes-from-duration", aD));
    assertEquals ("decimal:6.25", _call ("seconds-from-duration", aD));
    assertEquals ("integer:-2", _call ("years-from-duration", _a (EAtomicType.YEAR_MONTH_DURATION, "-P2Y6M")));
    assertEquals ("integer:-6", _call ("months-from-duration", _a (EAtomicType.YEAR_MONTH_DURATION, "-P2Y6M")));
    assertEquals ("integer:1", _call ("years-from-duration", _a (EAtomicType.YEAR_MONTH_DURATION, "P20M")));
    assertEquals ("integer:0", _call ("years-from-duration", _dtd ("P400D")));
    assertEquals ("integer:-4", _call ("hours-from-duration", _dtd ("-PT4H5M")));
    assertEquals ("integer:12", _call ("hours-from-duration", _dtd ("P3DT12H32M12S")));
    assertEquals ("integer:3", _call ("hours-from-duration", _dtd ("PT123H")));
    assertEquals ("integer:5", _call ("days-from-duration", _dtd ("PT123H")));
    assertEquals ("integer:-30", _call ("minutes-from-duration", _dtd ("-P5DT12H30M")));
    assertEquals ("decimal:1.03", _call ("seconds-from-duration", _dtd ("P3DT8H2M1.03S")));
    assertEquals ("decimal:-10.5", _call ("seconds-from-duration", _dtd ("-PT10.5S")));
    assertEquals ("decimal:-16", _call ("seconds-from-duration", _dtd ("-PT256S")));
    assertEquals ("()", _call ("seconds-from-duration", XdmSequence.EMPTY));
  }

  @Test
  public void testAdjustDateTime ()
  {
    final int nTz = -300;
    final String f = "adjust-dateTime-to-timezone";
    assertEquals ("dateTime:2002-03-07T10:00:00-05:00", _callTz (nTz, f, _dt ("2002-03-07T10:00:00")));
    assertEquals ("dateTime:2002-03-07T12:00:00-05:00", _callTz (nTz, f, _dt ("2002-03-07T10:00:00-07:00")));
    assertEquals ("dateTime:2002-03-07T10:00:00-10:00",
                  _callTz (nTz, f, _dt ("2002-03-07T10:00:00"), _dtd ("-PT10H")));
    assertEquals ("dateTime:2002-03-07T07:00:00-10:00",
                  _callTz (nTz, f, _dt ("2002-03-07T10:00:00-07:00"), _dtd ("-PT10H")));
    assertEquals ("dateTime:2002-03-08T03:00:00+10:00",
                  _callTz (nTz, f, _dt ("2002-03-07T10:00:00-07:00"), _dtd ("PT10H")));
    assertEquals ("dateTime:2002-03-06T15:00:00-08:00",
                  _callTz (nTz, f, _dt ("2002-03-07T00:00:00+01:00"), _dtd ("-PT8H")));
    assertEquals ("dateTime:2002-03-07T10:00:00", _callTz (nTz, f, _dt ("2002-03-07T10:00:00"), XdmSequence.EMPTY));
    assertEquals ("dateTime:2002-03-07T10:00:00",
                  _callTz (nTz, f, _dt ("2002-03-07T10:00:00-07:00"), XdmSequence.EMPTY));
    assertEquals ("dateTime:2002-03-07T10:00:00.123Z",
                  _callTz (nTz, f, _dt ("2002-03-07T10:00:00.123Z"), _dtd ("PT0S")));
    assertEquals ("()", _callTz (nTz, f, XdmSequence.EMPTY));
    _err ("FODT0003", f, _dt ("2002-03-07T10:00:00"), _dtd ("PT14H0M0.001S"));
    _err ("FODT0003", f, _dt ("2002-03-07T10:00:00"), _dtd ("-PT15H"));
    _err ("FODT0003", f, _dt ("2002-03-07T10:00:00"), _dtd ("PT5H30S"));
  }

  @Test
  public void testAdjustDate ()
  {
    final int nTz = -300;
    final String f = "adjust-date-to-timezone";
    assertEquals ("date:2002-03-07-05:00", _callTz (nTz, f, _date ("2002-03-07")));
    assertEquals ("date:2002-03-07-05:00", _callTz (nTz, f, _date ("2002-03-07-07:00")));
    assertEquals ("date:2002-03-07-10:00", _callTz (nTz, f, _date ("2002-03-07"), _dtd ("-PT10H")));
    assertEquals ("date:2002-03-06-10:00", _callTz (nTz, f, _date ("2002-03-07-07:00"), _dtd ("-PT10H")));
    assertEquals ("date:2002-03-07", _callTz (nTz, f, _date ("2002-03-07"), XdmSequence.EMPTY));
    assertEquals ("date:2002-03-07", _callTz (nTz, f, _date ("2002-03-07-07:00"), XdmSequence.EMPTY));
  }

  @Test
  public void testAdjustTime ()
  {
    final int nTz = -300;
    final String f = "adjust-time-to-timezone";
    assertEquals ("time:10:00:00-05:00", _callTz (nTz, f, _time ("10:00:00")));
    assertEquals ("time:12:00:00-05:00", _callTz (nTz, f, _time ("10:00:00-07:00")));
    assertEquals ("time:10:00:00-10:00", _callTz (nTz, f, _time ("10:00:00"), _dtd ("-PT10H")));
    assertEquals ("time:07:00:00-10:00", _callTz (nTz, f, _time ("10:00:00-07:00"), _dtd ("-PT10H")));
    assertEquals ("time:10:00:00", _callTz (nTz, f, _time ("10:00:00-07:00"), XdmSequence.EMPTY));
    assertEquals ("time:03:00:00+10:00", _callTz (nTz, f, _time ("10:00:00-07:00"), _dtd ("PT10H")));
    // The adjusted time is on the reference date again, so it equals the
    // parsed literal
    final XdmDateTimeValue aAdjusted = (XdmDateTimeValue) ((XdmAtomic) LIB.getFunction (new XdmQName (XdmConstants.NS_FN,
                                                                                                         f),
                                                                                           2)
                                                                              .getImpl ()
                                                                              .call (new DynamicContext (new StaticContext ()),
                                                                                     Arrays.asList (_time ("03:00:00+10:00"),
                                                                                                    _dtd ("-PT10H")))
                                                                              .getFirst ()).getValue ();
    assertEquals (XdmDateTimeValue.parse (EAtomicType.TIME, "07:00:00-10:00"), aAdjusted);
  }

  @Test
  public void testParseIetfDate ()
  {
    final String f = "parse-ietf-date";
    final String sExpected = "dateTime:2014-08-20T19:36:01Z";
    for (final String s : new String [] { "Wed, 20 Aug 2014 19:36:01 GMT",
                                          "  Wed, 20 Aug 2014 19:36:01 GMT",
                                          "Wed, 20 Aug 2014 19:36:01 GMT    ",
                                          "Wed 20 Aug 2014 19:36:01 GMT",
                                          "Wednesday 20 Aug 2014 19:36:01 GMT",
                                          "wed, 20 aug 2014 19:36:01 gmt",
                                          "WED, 20 AUG 2014 19:36:01 GMT",
                                          "WED, 20 AUG 2014 19:36:01",
                                          "Wed, 20 Aug 2014 19:36:01GMT",
                                          " 20 Aug 2014 19:36:01 GMT",
                                          "Aug 20 19:36:01 2014",
                                          "Wed, 20 Aug 2014 19:36:01 UT",
                                          "Wed, 20 Aug 2014 19:36:01 UTC",
                                          "Wed, 20 Aug 2014 14:36:01 EST",
                                          "Wed, 20 Aug 2014 15:36:01 EDT",
                                          "Wed, 20 Aug 2014 13:36:01 CST",
                                          "Wed, 20 Aug 2014 12:36:01 PDT",
                                          "Wed, 20 Aug 2014 14:36:01 -05:00(EST)",
                                          "Wed, 20 Aug 2014 14:36:01 -05:00  (  EST  )",
                                          "Wed, 20 Aug 2014 14:36:01-05:00",
                                          "Wed, 20 Aug 2014 14:36:01-05",
                                          "Wed, 20 Aug 2014 14:36:01-05:",
                                          "Wed, 20 Aug 2014 14:36:01 -0500",
                                          "Wed, 20 Aug 2014 14:36:01 -500",
                                          "Wed, 20 - Aug - 2014 19:36:01",
                                          "Wed, 20- Aug- 2014 19:36:01",
                                          "Wed, 20 -Aug -2014 19:36:01",
                                          "Aug-20 19:36:01 2014" })
    {
      // Compare as instants: the lexical form keeps the parsed timezone
      final XdmDateTimeValue aParsed = (XdmDateTimeValue) ((XdmAtomic) LIB.getFunction (new XdmQName (XdmConstants.NS_FN,
                                                                                                        f),
                                                                                          1)
                                                                             .getImpl ()
                                                                             .call (new DynamicContext (new StaticContext ()),
                                                                                    Arrays.asList (_a (EAtomicType.STRING,
                                                                                                       s)))
                                                                             .getFirst ()).getValue ();
      assertEquals (s,
                    0,
                    aParsed.compareTo (XdmDateTimeValue.parse (EAtomicType.DATE_TIME, "2014-08-20T19:36:01Z"),
                                       EAtomicType.DATE_TIME,
                                       0));
    }
    assertEquals (sExpected, _call (f, _a (EAtomicType.STRING, "Wed, 20 Aug 2014 19:36:01 GMT")));
    assertEquals ("dateTime:2014-08-20T14:36:01-05:00",
                  _call (f, _a (EAtomicType.STRING, "Wed, 20 Aug 2014 14:36:01 -05:00")));
    assertEquals ("dateTime:1914-08-20T19:36:01Z", _call (f, _a (EAtomicType.STRING, "20 Aug 14 19:36:01")));
    assertEquals ("dateTime:2014-08-20T19:36:01.25Z",
                  _call (f, _a (EAtomicType.STRING, "Wed, 20 Aug 2014 19:36:01.25 GMT")));
    assertEquals ("dateTime:2014-09-09T09:36:00Z", _call (f, _a (EAtomicType.STRING, "Tue, 9 Sep 2014 9:36 GMT")));
    assertEquals ("dateTime:2014-08-21T00:00:00Z", _call (f, _a (EAtomicType.STRING, "Aug 20 24:00:00 2014")));
    assertEquals ("dateTime:2014-08-21T00:00:00Z", _call (f, _a (EAtomicType.STRING, "Aug 20 24:00 2014")));
    assertEquals ("dateTime:2014-08-20T19:36:01-05:00", _call (f, _a (EAtomicType.STRING, "Aug 20 19:36:01 -5 2014")));
    assertEquals ("()", _call (f, XdmSequence.EMPTY));

    for (final String s : new String [] { "2014-08-20T19:36:01Z",
                                          "Wed, 020 Aug 2014 19:36:01 GMT",
                                          "Wed, 20 August 2014 19:36:01 GMT",
                                          "Wed, 20 Aug 114 19:36:01 GMT",
                                          "Wed, 20 Aug 2014 19:36:01 CET",
                                          "Mon, 32 Aug 2014 19:36:01 GMT",
                                          "Sat, 29 Feb 2014 19:36:01 GMT",
                                          "Feb 28 19:36:01 GMT -2000",
                                          "Wed, 00 Aug 2014 19:36:01 GMT",
                                          "Boy 20 Aug 2014 19:36:01 GMT",
                                          "Manchester, 20 Aug 2014 19:36:01 GMT",
                                          "Aug,20 19:36:01 2014",
                                          "Aug20 19:36:01 2014",
                                          "Wed,20 Aug 2014 19:36:01",
                                          "Aug 2014 19:36:01",
                                          "20Aug 2014 19:36:01",
                                          "20 Aug2014 19:36:01",
                                          "Aug 20 19:36:01GMT2014",
                                          "Aug 20 19:36:01GMT Manchester",
                                          "Aug 20 19:36:01GMT 014",
                                          "Wed, 20 Aug 2014 19:36:01 GMT Manchester",
                                          "Aug 20 19.36.01GMT 2014",
                                          "Aug 20 19:3:01GMT 2014",
                                          "Aug 20 19:36:0.1GMT 2014",
                                          "Aug 20 19:36:GMT 2014",
                                          "Aug 20 19:36:01.GMT 2014",
                                          "Aug 20 19:36:01 -05:0 2014",
                                          "Aug 20 19:36:01 -05:00 () 2014",
                                          "Aug 20 19:36:01 -05:00 (CET) 2014",
                                          "Aug 20 19:36:01 -05:00 EST 2014",
                                          "Aug 20 19:36(EST) 2014",
                                          "",
                                          "Aug 20 29:36:01GMT 2014",
                                          "Aug 20 19:66:01GMT 2014",
                                          "Aug 20 19:36:71GMT 2014",
                                          "Aug 20 19:36:01 -15:00 2014",
                                          "Aug 20 19:36:01 -05:60 2014" })
      _err ("FORG0010", f, _a (EAtomicType.STRING, s));
  }

  @Test
  public void testAllRegistered ()
  {
    for (final String s : new String [] { "dateTime#2",
                                          "years-from-duration#1",
                                          "months-from-duration#1",
                                          "days-from-duration#1",
                                          "hours-from-duration#1",
                                          "minutes-from-duration#1",
                                          "seconds-from-duration#1",
                                          "year-from-dateTime#1",
                                          "month-from-dateTime#1",
                                          "day-from-dateTime#1",
                                          "hours-from-dateTime#1",
                                          "minutes-from-dateTime#1",
                                          "seconds-from-dateTime#1",
                                          "timezone-from-dateTime#1",
                                          "year-from-date#1",
                                          "month-from-date#1",
                                          "day-from-date#1",
                                          "timezone-from-date#1",
                                          "hours-from-time#1",
                                          "minutes-from-time#1",
                                          "seconds-from-time#1",
                                          "timezone-from-time#1",
                                          "adjust-dateTime-to-timezone#1",
                                          "adjust-dateTime-to-timezone#2",
                                          "adjust-date-to-timezone#1",
                                          "adjust-date-to-timezone#2",
                                          "adjust-time-to-timezone#1",
                                          "adjust-time-to-timezone#2",
                                          "parse-ietf-date#1" })
    {
      final int nHash = s.indexOf ('#');
      assertTrue (s,
                  LIB.getFunction (new XdmQName (XdmConstants.NS_FN, s.substring (0, nHash)),
                                   Integer.parseInt (s.substring (nHash + 1))) != null);
    }
  }
}
