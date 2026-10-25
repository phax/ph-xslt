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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.math.BigDecimal;

import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;

/**
 * Test class for class {@link DateTimeOps}.
 *
 * @author Philip Helger
 */
public final class DateTimeOpsTest
{
  private static DynamicContext _ctx (final int nImplicitTz)
  {
    final DynamicContext ret = new DynamicContext (new StaticContext ());
    ret.setImplicitTimezoneMinutes (nImplicitTz);
    return ret;
  }

  private static XdmAtomic _a (final EAtomicType eType, final String sLexical)
  {
    if (eType.isDurationFamily ())
      return XdmAtomic.of (eType, XdmDurationValue.parse (eType, sLexical));
    return XdmAtomic.of (eType, XdmDateTimeValue.parse (eType, sLexical));
  }

  private static XdmAtomic _dt (final String s)
  {
    return _a (EAtomicType.DATE_TIME, s);
  }

  private static XdmAtomic _date (final String s)
  {
    return _a (EAtomicType.DATE, s);
  }

  private static XdmAtomic _time (final String s)
  {
    return _a (EAtomicType.TIME, s);
  }

  private static XdmAtomic _ymd (final String s)
  {
    return _a (EAtomicType.YEAR_MONTH_DURATION, s);
  }

  private static XdmAtomic _dtd (final String s)
  {
    return _a (EAtomicType.DAY_TIME_DURATION, s);
  }

  private static String _lex (final XdmAtomic a)
  {
    final Object v = a.getValue ();
    if (v instanceof final XdmDateTimeValue aDT)
      return aDT.toLexical (a.getType ());
    if (v instanceof final XdmDurationValue aDur)
      return aDur.toLexical (a.getType ());
    if (v instanceof final BigDecimal aBD)
      return aBD.toPlainString ();
    return v.toString ();
  }

  private static String _op (final EBinaryOp eOp, final XdmAtomic aL, final XdmAtomic aR, final int nTz)
  {
    final XdmAtomic ret = DateTimeOps.arithmetic (eOp, aL, aR, _ctx (nTz));
    return ret.getType ().getLocalName () + ":" + _lex (ret);
  }

  private static String _op (final EBinaryOp eOp, final XdmAtomic aL, final XdmAtomic aR)
  {
    return _op (eOp, aL, aR, 0);
  }

  private static void _err (final String sCode, final EBinaryOp eOp, final XdmAtomic aL, final XdmAtomic aR)
  {
    try
    {
      DateTimeOps.arithmetic (eOp, aL, aR, _ctx (0));
      fail ("Expected " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (sCode, ex.getCode ());
    }
  }

  @Test
  public void testNotApplicable ()
  {
    assertNull (DateTimeOps.arithmetic (EBinaryOp.ADD, XdmAtomic.integer (1), XdmAtomic.integer (2), _ctx (0)));
  }

  @Test
  public void testDurationArithmetic ()
  {
    final EBinaryOp ADD = EBinaryOp.ADD;
    final EBinaryOp SUB = EBinaryOp.SUBTRACT;
    assertEquals ("yearMonthDuration:P6Y2M", _op (ADD, _ymd ("P2Y11M"), _ymd ("P3Y3M")));
    assertEquals ("yearMonthDuration:-P4M", _op (SUB, _ymd ("P2Y11M"), _ymd ("P3Y3M")));
    assertEquals ("dayTimeDuration:P8DT5M", _op (ADD, _dtd ("P2DT12H5M"), _dtd ("P5DT12H")));
    assertEquals ("dayTimeDuration:PT59.403S", _op (ADD, _dtd ("PT3.1S"), _dtd ("PT56.303S")));
    assertEquals ("dayTimeDuration:P1DT1H", _op (SUB, _dtd ("P2DT12H"), _dtd ("P1DT11H")));
    _err ("XPTY0004", ADD, _ymd ("P1Y"), _dtd ("P1D"));
    _err ("XPTY0004", ADD, _a (EAtomicType.DURATION, "P1Y"), _a (EAtomicType.DURATION, "P1Y"));
    _err ("XPTY0004", EBinaryOp.INTEGER_DIVIDE, _dtd ("P1D"), _dtd ("P1D"));
    _err ("FODT0002",
          ADD,
          _a (EAtomicType.YEAR_MONTH_DURATION, "P768614336404564650Y"),
          _a (EAtomicType.YEAR_MONTH_DURATION, "P1Y"));
  }

  @Test
  public void testDurationDivideDuration ()
  {
    final EBinaryOp DIV = EBinaryOp.DIVIDE;
    assertEquals ("decimal:-2.5", _op (DIV, _ymd ("P3Y4M"), _ymd ("-P1Y4M")));
    assertEquals ("decimal:12006", _op (DIV, _ymd ("P1000Y6M"), _ymd ("P0Y1M")));
    assertEquals ("decimal:0", _op (DIV, _ymd ("P0M"), _ymd ("P1M")));
    assertEquals ("decimal:0.1", _op (DIV, _ymd ("P3Y36M"), _ymd ("P60Y")));
    assertEquals ("decimal:1.437834967320261437908496732026144",
                  _op (DIV, _dtd ("P2DT53M11S"), _dtd ("P1DT10H")));
    assertEquals ("decimal:1339199", _op (DIV, _dtd ("P15DT11H59M59S"), _dtd ("PT1S")));
    _err ("FOAR0001", DIV, _ymd ("P2Y"), _ymd ("P0Y"));
    _err ("FOAR0001", DIV, _dtd ("P2D"), _dtd ("PT0S"));
  }

  @Test
  public void testDurationTimesNumber ()
  {
    final EBinaryOp MUL = EBinaryOp.MULTIPLY;
    final EBinaryOp DIV = EBinaryOp.DIVIDE;
    assertEquals ("yearMonthDuration:P6Y9M", _op (MUL, _ymd ("P2Y11M"), XdmAtomic.dbl (2.3)));
    assertEquals ("yearMonthDuration:P6Y9M", _op (MUL, XdmAtomic.dbl (2.3), _ymd ("P2Y11M")));
    assertEquals ("yearMonthDuration:-P3M", _op (MUL, _ymd ("P1M"), XdmAtomic.dbl (-3.5)));
    assertEquals ("yearMonthDuration:P1Y11M", _op (DIV, _ymd ("P2Y11M"), XdmAtomic.dbl (1.5)));
    assertEquals ("yearMonthDuration:P3M", _op (DIV, _ymd ("P5M"), XdmAtomic.integer (2)));
    assertEquals ("yearMonthDuration:P0M", _op (DIV, _ymd ("P5M"), XdmAtomic.integer (-10)));
    assertEquals ("dayTimeDuration:PT9.3S", _op (MUL, _dtd ("PT3.1S"), XdmAtomic.integer (3)));
    assertEquals ("dayTimeDuration:P6DT7H40M", _op (MUL, _dtd ("PT2H10M"), XdmAtomic.dbl (70.0)));
    assertEquals ("dayTimeDuration:PT4H33M", _op (MUL, _dtd ("PT2H10M"), XdmAtomic.dbl (2.1)));
    assertEquals ("dayTimeDuration:PT17H40M7S", _op (DIV, _dtd ("P1DT2H30M10.5S"), XdmAtomic.dbl (1.5)));
    assertEquals ("dayTimeDuration:PT0S", _op (DIV, _dtd ("P1D"), XdmAtomic.dbl (Double.POSITIVE_INFINITY)));
    assertEquals ("dayTimeDuration:PT0.3333333333333333333333333333333333S",
                  _op (DIV, _dtd ("PT1S"), XdmAtomic.integer (3)));
    assertEquals ("dayTimeDuration:PT1.5S",
                  _op (MUL, _dtd ("PT1S"), XdmAtomic.decimal (new BigDecimal ("1.5"))));
    _err ("FOCA0005", MUL, _dtd ("P1D"), XdmAtomic.dbl (Double.NaN));
    _err ("FOCA0005", DIV, _ymd ("P1M"), XdmAtomic.dbl (Double.NaN));
    _err ("FODT0002", MUL, _dtd ("P1D"), XdmAtomic.dbl (Double.POSITIVE_INFINITY));
    _err ("FODT0002", DIV, _dtd ("P1D"), XdmAtomic.dbl (0));
    _err ("FODT0002", MUL, _ymd ("P1M"), XdmAtomic.dbl (1e300));
    _err ("XPTY0004", MUL, _a (EAtomicType.DURATION, "P1D"), XdmAtomic.integer (2));
    _err ("XPTY0004", DIV, XdmAtomic.integer (2), _dtd ("P1D"));
  }

  @Test
  public void testDateTimeMinusDateTime ()
  {
    final EBinaryOp SUB = EBinaryOp.SUBTRACT;
    assertEquals ("dayTimeDuration:P337DT2H12M",
                  _op (SUB, _dt ("2000-10-30T06:12:00"), _dt ("1999-11-28T09:00:00Z"), -300));
    assertEquals ("dayTimeDuration:P337D", _op (SUB, _date ("2000-10-30"), _date ("1999-11-28"), 0));
    assertEquals ("dayTimeDuration:P336DT19H",
                  _op (SUB, _date ("2000-10-30"), _date ("1999-11-28Z"), 300));
    assertEquals ("dayTimeDuration:P5DT7H", _op (SUB, _date ("2000-10-15-05:00"), _date ("2000-10-10+02:00")));
    assertEquals ("dayTimeDuration:PT2H12M", _op (SUB, _time ("11:12:00Z"), _time ("04:00:00-05:00")));
    assertEquals ("dayTimeDuration:PT0S", _op (SUB, _time ("11:00:00-05:00"), _time ("21:30:00+05:30")));
    assertEquals ("dayTimeDuration:P1D", _op (SUB, _time ("17:00:00-06:00"), _time ("08:00:00+09:00")));
    assertEquals ("dayTimeDuration:-PT23H59M59S", _op (SUB, _time ("24:00:00"), _time ("23:59:59")));
    assertEquals ("dayTimeDuration:-P732676D", _op (SUB, _date ("0001-01-01"), _date ("2007-01-01")));
    _err ("XPTY0004", SUB, _date ("2000-10-30"), _dt ("1999-11-28T09:00:00Z"));
    _err ("XPTY0004", SUB, _a (EAtomicType.G_YEAR, "2000"), _a (EAtomicType.G_YEAR, "1999"));
    _err ("XPTY0004", EBinaryOp.ADD, _date ("2000-10-30"), _date ("1999-11-28"));
  }

  @Test
  public void testDateTimePlusDuration ()
  {
    final EBinaryOp ADD = EBinaryOp.ADD;
    final EBinaryOp SUB = EBinaryOp.SUBTRACT;
    assertEquals ("dateTime:2001-12-30T11:12:00", _op (ADD, _dt ("2000-10-30T11:12:00"), _ymd ("P1Y2M")));
    assertEquals ("dateTime:2000-11-02T12:27:00", _op (ADD, _dt ("2000-10-30T11:12:00"), _dtd ("P3DT1H15M")));
    assertEquals ("dateTime:1999-08-30T11:12:00", _op (SUB, _dt ("2000-10-30T11:12:00"), _ymd ("P1Y2M")));
    assertEquals ("dateTime:2000-10-27T10:57:00", _op (SUB, _dt ("2000-10-30T11:12:00"), _dtd ("P3DT0H15M")));
    // End-of-month clamping
    assertEquals ("date:1999-02-28", _op (SUB, _date ("2000-02-29"), _ymd ("P1Y")));
    assertEquals ("date:2000-02-29Z", _op (ADD, _date ("2000-01-31Z"), _ymd ("P1M")));
    assertEquals ("date:2001-02-28", _op (ADD, _date ("2000-01-31"), _ymd ("P13M")));
    assertEquals ("date:2000-09-30", _op (ADD, _date ("2000-10-31"), _ymd ("-P1M")));
    assertEquals ("date:2001-12-30", _op (ADD, _date ("2000-10-30"), _ymd ("P1Y2M")));
    assertEquals ("date:2004-11-01Z", _op (ADD, _date ("2004-10-30Z"), _dtd ("P2DT2H30M0S")));
    assertEquals ("date:2000-10-26", _op (SUB, _date ("2000-10-30"), _dtd ("P3DT1H15M")));
    assertEquals ("date:2000-10-30", _op (ADD, _date ("2000-10-30"), _dtd ("PT23H")));
    assertEquals ("date:2000-10-29", _op (SUB, _date ("2000-10-30"), _dtd ("PT1H")));
    // Year 0 / BCE
    assertEquals ("date:-0001-12-31", _op (SUB, _date ("0000-01-01"), _dtd ("P1D")));
    assertEquals ("date:0000-02-28", _op (ADD, _date ("-0001-02-28"), _ymd ("P1Y")));
    assertEquals ("date:0000-02-29", _op (ADD, _date ("0000-01-31"), _ymd ("P1M")));
    // Commutative addition
    assertEquals ("dateTime:2001-12-30T11:12:00Z", _op (ADD, _ymd ("P1Y2M"), _dt ("2000-10-30T11:12:00Z")));
    // op:add-dayTimeDuration-to-dateTime returns an xs:dateTime, also for a dateTimeStamp
    assertEquals ("dateTime:2000-10-31T11:12:00Z",
                  _op (ADD, _a (EAtomicType.DATE_TIME_STAMP, "2000-10-30T11:12:00Z"), _dtd ("P1D")));
    // xs:time wraps around midnight
    assertEquals ("time:12:27:00", _op (ADD, _time ("11:12:00"), _dtd ("P3DT1H15M")));
    assertEquals ("time:00:12:00-05:00", _op (ADD, _time ("23:12:00-05:00"), _dtd ("P3DT1H")));
    assertEquals ("time:10:57:00", _op (SUB, _time ("11:12:00"), _dtd ("P3DT0H15M")));
    assertEquals ("time:22:58:00Z", _op (SUB, _time ("08:20:00Z"), _dtd ("PT9H22M")));
    _err ("XPTY0004", ADD, _time ("11:12:00"), _ymd ("P1Y"));
    _err ("XPTY0004", SUB, _dtd ("P1D"), _date ("2000-10-30"));
    _err ("XPTY0004", ADD, _a (EAtomicType.G_YEAR, "2000"), _ymd ("P1Y"));
    _err ("XPTY0004", ADD, _date ("2000-10-30"), _a (EAtomicType.DURATION, "P1D"));
    _err ("FODT0001", ADD, _date ("999999999-12-31"), _dtd ("P1D"));
    _err ("FODT0001", ADD, _date ("999999999-12-31"), _ymd ("P1M"));
  }

  @Test
  public void testCast ()
  {
    final XdmAtomic aDT = _dt ("2004-05-06T07:08:09.5-05:00");
    assertEquals ("2004-05-06-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.DATE)));
    assertEquals ("07:08:09.5-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.TIME)));
    assertEquals ("2004-05-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.G_YEAR_MONTH)));
    assertEquals ("2004-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.G_YEAR)));
    assertEquals ("--05-06-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.G_MONTH_DAY)));
    assertEquals ("---06-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.G_DAY)));
    assertEquals ("--05-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.G_MONTH)));
    assertEquals ("2004-05-06T07:08:09.5-05:00", _lex (DateTimeOps.cast (aDT, EAtomicType.DATE_TIME_STAMP)));
    assertEquals ("2004-05-06T00:00:00", _lex (DateTimeOps.cast (_date ("2004-05-06"), EAtomicType.DATE_TIME)));
    assertEquals ("2004", _lex (DateTimeOps.cast (_date ("2004-05-06"), EAtomicType.G_YEAR)));
    // A cast g* value equals the parsed literal
    assertEquals (0,
                  ((XdmDateTimeValue) DateTimeOps.cast (aDT, EAtomicType.G_YEAR).getValue ()).compareTo (XdmDateTimeValue.parse (EAtomicType.G_YEAR,
                                                                                                                              "2004-05:00"),
                                                                                                         EAtomicType.G_YEAR,
                                                                                                         0));
    assertEquals ("P1Y2M", _lex (DateTimeOps.cast (_a (EAtomicType.DURATION, "P1Y2M3DT4H"),
                                                   EAtomicType.YEAR_MONTH_DURATION)));
    assertEquals ("P3DT4H", _lex (DateTimeOps.cast (_a (EAtomicType.DURATION, "P1Y2M3DT4H"),
                                                    EAtomicType.DAY_TIME_DURATION)));
    assertEquals ("PT0S", _lex (DateTimeOps.cast (_ymd ("P1Y"), EAtomicType.DAY_TIME_DURATION)));
    assertEquals ("P1Y", _lex (DateTimeOps.cast (_ymd ("P1Y"), EAtomicType.DURATION)));
    try
    {
      DateTimeOps.cast (_date ("2004-05-06"), EAtomicType.TIME);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPTY0004", ex.getCode ());
    }
    try
    {
      DateTimeOps.cast (_a (EAtomicType.G_YEAR, "2004"), EAtomicType.DATE);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPTY0004", ex.getCode ());
    }
    try
    {
      DateTimeOps.cast (_date ("2004-05-06"), EAtomicType.DAY_TIME_DURATION);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPTY0004", ex.getCode ());
    }
    try
    {
      DateTimeOps.cast (_dt ("2004-05-06T00:00:00"), EAtomicType.DATE_TIME_STAMP);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("FORG0001", ex.getCode ());
    }
  }
}
