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
package com.helger.phoxtrot.xpath.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.context.DynamicContext;

/**
 * Arithmetic and casting for the date/time and duration families, called by
 * {@link com.helger.phoxtrot.xpath.ops.AtomicArithmetic} and
 * {@link com.helger.phoxtrot.xpath.ops.AtomicCaster}. Owner: date/time workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class DateTimeOps
{
  /**
   * Precision of non-terminating decimal results (duration div duration,
   * duration div number).
   */
  public static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

  private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf (86400);
  private static final BigDecimal HALF = new BigDecimal ("0.5");
  private static final BigDecimal LONG_MAX = BigDecimal.valueOf (Long.MAX_VALUE);

  @PresentForCodeCoverage
  private static final DateTimeOps INSTANCE = new DateTimeOps ();

  private DateTimeOps ()
  {}

  /**
   * @return A new XPTY0004 error saying that the operator is not defined for
   *         the types of the two operands; the caller throws it.
   */
  @NonNull
  private static XPathException _typeError (@NonNull final EBinaryOp eOp,
                                            @NonNull final XdmAtomic aLeft,
                                            @NonNull final XdmAtomic aRight)
  {
    return XPathException.type ("Operator " +
                                eOp +
                                " is not defined for " +
                                aLeft.getType ().getQualifiedName () +
                                " and " +
                                aRight.getType ().getQualifiedName ());
  }

  /**
   * @param eType
   *        A date/time type.
   * @return The primitive type (dateTimeStamp becomes dateTime).
   */
  @NonNull
  private static EAtomicType _primitive (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.DATE_TIME_STAMP ? EAtomicType.DATE_TIME : eType;
  }

  private static boolean _isYmd (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.YEAR_MONTH_DURATION;
  }

  private static boolean _isDtd (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.DAY_TIME_DURATION;
  }

  /**
   * @param aNumber
   *        A numeric value.
   * @return <code>null</code> for NaN or an infinity, else the exact decimal
   *         value; doubles and floats use their shortest decimal
   *         representation, so 0.1e0 is exactly 0.1.
   */
  @Nullable
  private static BigDecimal _toDecimal (@NonNull final XdmAtomic aNumber)
  {
    final Object aValue = aNumber.getValue ();
    if (aValue instanceof final BigInteger aBI)
      return new BigDecimal (aBI);
    if (aValue instanceof final BigDecimal aBD)
      return aBD;
    if (aValue instanceof final Float aFloat)
    {
      final float f = aFloat.floatValue ();
      if (Float.isNaN (f) || Float.isInfinite (f))
        return null;
      return new BigDecimal (Float.toString (f));
    }
    final double d = aNumber.getAsDouble ();
    if (Double.isNaN (d) || Double.isInfinite (d))
      return null;
    return new BigDecimal (Double.toString (d));
  }

  /**
   * Round a scaled month count to a whole number of months the way
   * <code>fn:round</code> does (half towards positive infinity), as required
   * for multiplying and dividing an xs:yearMonthDuration by a number (F&amp;O
   * 3.1 section 10.6).
   *
   * @param aMonths
   *        The exact month count.
   * @return The rounded month count.
   * @throws XPathException
   *         FODT0002 if the rounded value does not fit into a long
   */
  private static long _toMonths (@NonNull final BigDecimal aMonths)
  {
    // Round half towards positive infinity (fn:round)
    final BigDecimal aRounded = aMonths.add (HALF).setScale (0, RoundingMode.FLOOR);
    if (aRounded.abs ().compareTo (LONG_MAX) > 0)
      throw new XPathException ("FODT0002", "Duration arithmetic overflow");
    return aRounded.longValueExact ();
  }

  @NonNull
  private static XdmAtomic _duration (@NonNull final EAtomicType eType,
                                      final long nMonths,
                                      @NonNull final BigDecimal aSeconds)
  {
    return XdmAtomic.of (eType, XdmDurationValue.of (nMonths, aSeconds));
  }

  /**
   * Multiply or divide an xs:yearMonthDuration or xs:dayTimeDuration by a
   * number (F&amp;O 3.1 section 10.6). The number is taken exactly (a double or
   * float by its shortest decimal form). A year-month result is rounded to
   * whole months with <code>_toMonths</code>; a day-time product is exact, a
   * day-time quotient is computed with {@link #DIVISION_CONTEXT}. Dividing by
   * an infinity gives a zero-length duration of the same type.
   *
   * @param aDur
   *        The duration operand, of one of the two duration subtypes.
   * @param aNumber
   *        The numeric operand.
   * @param bDivide
   *        <code>true</code> for <code>aDur div aNumber</code>,
   *        <code>false</code> for the product.
   * @return A duration of the type of <code>aDur</code>.
   * @throws XPathException
   *         FOCA0005 if the number is NaN, FODT0002 for a division by zero, a
   *         multiplication by an infinity or a month count that overflows
   */
  @NonNull
  private static XdmAtomic _scaleDuration (@NonNull final XdmAtomic aDur,
                                           @NonNull final XdmAtomic aNumber,
                                           final boolean bDivide)
  {
    final EAtomicType eType = aDur.getType ();
    final XdmDurationValue aValue = (XdmDurationValue) aDur.getValue ();
    final double d = aNumber.getAsDouble ();
    if (Double.isNaN (d))
      throw new XPathException ("FOCA0005", "Duration arithmetic with NaN");
    if (bDivide)
    {
      if (d == 0)
        throw new XPathException ("FODT0002", "Division of a duration by zero");
      if (Double.isInfinite (d))
        return _duration (eType, 0, BigDecimal.ZERO);
    }
    else
      if (Double.isInfinite (d))
        throw new XPathException ("FODT0002", "Duration arithmetic overflow");
    final BigDecimal aFactor = _toDecimal (aNumber);
    if (_isYmd (eType))
    {
      final BigDecimal aMonths = BigDecimal.valueOf (aValue.getMonths ());
      final BigDecimal aScaled = bDivide ? aMonths.divide (aFactor, DIVISION_CONTEXT) : aMonths.multiply (aFactor);
      return _duration (eType, _toMonths (aScaled), BigDecimal.ZERO);
    }
    final BigDecimal aSecs = aValue.getSeconds ();
    final BigDecimal aScaled = bDivide ? aSecs.divide (aFactor, DIVISION_CONTEXT) : aSecs.multiply (aFactor);
    return _duration (eType, 0, aScaled);
  }

  /**
   * Arithmetic between two durations (F&amp;O 3.1 section 10.6): both must be
   * xs:yearMonthDuration or both xs:dayTimeDuration. Addition and subtraction
   * give a duration of that type (month counts with an overflow check, seconds
   * exactly); division gives the xs:decimal ratio of the month counts or of the
   * seconds, computed with {@link #DIVISION_CONTEXT} and written without
   * trailing zeros and without a negative scale.
   *
   * @return The result value.
   * @throws XPathException
   *         XPTY0004 for mixed or plain xs:duration operands or another
   *         operator, FODT0002 if a month count overflows, FOAR0001 for a
   *         division by a zero-length duration
   */
  @NonNull
  private static XdmAtomic _durationArithmetic (@NonNull final EBinaryOp eOp,
                                                @NonNull final XdmAtomic aLeft,
                                                @NonNull final XdmAtomic aRight)
  {
    final EAtomicType eL = aLeft.getType ();
    final EAtomicType eR = aRight.getType ();
    final boolean bYmd = _isYmd (eL) && _isYmd (eR);
    final boolean bDtd = _isDtd (eL) && _isDtd (eR);
    if (!bYmd && !bDtd)
      throw _typeError (eOp, aLeft, aRight);
    final XdmDurationValue aL = (XdmDurationValue) aLeft.getValue ();
    final XdmDurationValue aR = (XdmDurationValue) aRight.getValue ();
    switch (eOp)
    {
      case ADD:
      case SUBTRACT:
      {
        final boolean bAdd = eOp == EBinaryOp.ADD;
        if (bYmd)
        {
          final long nMonths;
          try
          {
            nMonths = bAdd ? Math.addExact (aL.getMonths (), aR.getMonths ())
                           : Math.subtractExact (aL.getMonths (), aR.getMonths ());
          }
          catch (final ArithmeticException ex)
          {
            throw new XPathException ("FODT0002", "Duration arithmetic overflow");
          }
          return _duration (eL, nMonths, BigDecimal.ZERO);
        }
        return _duration (eL,
                          0,
                          bAdd ? aL.getSeconds ().add (aR.getSeconds ()) : aL.getSeconds ()
                                                                             .subtract (aR.getSeconds ()));
      }
      case DIVIDE:
      {
        final BigDecimal aNum = bYmd ? BigDecimal.valueOf (aL.getMonths ()) : aL.getSeconds ();
        final BigDecimal aDen = bYmd ? BigDecimal.valueOf (aR.getMonths ()) : aR.getSeconds ();
        if (aDen.signum () == 0)
          throw new XPathException ("FOAR0001", "Division by a zero-length duration");
        BigDecimal aRes = aNum.divide (aDen, DIVISION_CONTEXT).stripTrailingZeros ();
        if (aRes.scale () < 0)
          aRes = aRes.setScale (0);
        return XdmAtomic.decimal (aRes);
      }
      default:
        throw _typeError (eOp, aLeft, aRight);
    }
  }

  /**
   * Add months (end-of-month clamping, F&amp;O 10.8.4 / XSD appendix E) and
   * then seconds to a date/time value.
   */
  @NonNull
  private static XdmDateTimeValue _addToDateTime (@NonNull final XdmDateTimeValue aDT,
                                                  final long nMonths,
                                                  @NonNull final BigDecimal aSeconds)
  {
    XdmDateTimeValue aCur = aDT;
    if (nMonths != 0)
    {
      final long nYear = aCur.getYear ();
      if (Math.abs (nMonths) / 12 > 2 * DateTimeCalendar.MAX_ABS_YEAR)
        throw new XPathException ("FODT0001", "Date/time arithmetic overflow");
      final long nTotal = aCur.getMonth () - 1 + nMonths;
      final long nNewYear = nYear + Math.floorDiv (nTotal, 12L);
      final int nNewMonth = (int) Math.floorMod (nTotal, 12L) + 1;
      if (Math.abs (nNewYear) > DateTimeCalendar.MAX_ABS_YEAR)
        throw new XPathException ("FODT0001", "Date/time arithmetic overflow");
      final int nDay = Math.min (aCur.getDay (), DateTimeCalendar.getDaysInMonth (nNewYear, nNewMonth));
      aCur = XdmDateTimeValue.of (nNewYear,
                                  nNewMonth,
                                  nDay,
                                  aCur.getHour (),
                                  aCur.getMinute (),
                                  aCur.getSecond (),
                                  aCur.getTimezone ());
    }
    if (aSeconds.signum () != 0)
    {
      // Work on the local components (timezone 0) and keep the timezone
      final BigDecimal aLocal = aCur.withTimezone (null).getEpochSeconds (0).add (aSeconds);
      aCur = XdmDateTimeValue.ofEpochSeconds (aLocal, null).withTimezone (aCur.getTimezone ());
    }
    return aCur;
  }

  /**
   * Add a duration to (or subtract it from) a date/time value (F&amp;O 3.1
   * section 10.8). xs:dateTime and xs:date accept both duration subtypes,
   * xs:time only xs:dayTimeDuration. For xs:time only the clock is kept: the
   * seconds of the day plus the duration, modulo 24 hours, on the reference
   * date 31 December of {@link XdmDateTimeValue#REFERENCE_YEAR}. For xs:date
   * the value is treated as a dateTime at midnight and the time of the result
   * is dropped again. The timezone of the date/time operand is kept. The result
   * type is the primitive type of the date/time operand, so an xs:dateTimeStamp
   * operand gives an xs:dateTime.
   *
   * @param aDT
   *        The date/time operand.
   * @param aDur
   *        The duration operand.
   * @param bSubtract
   *        <code>true</code> to subtract the duration (it is negated first).
   * @param eOp
   *        The original operator, only for the error message.
   * @param aLeft
   *        The original left operand, only for the error message.
   * @param aRight
   *        The original right operand, only for the error message.
   * @return The result value.
   * @throws XPathException
   *         XPTY0004 for a disallowed type combination (also for a plain
   *         xs:duration), FODT0001 if the year leaves the supported range
   */
  @NonNull
  private static XdmAtomic _addDurationToDateTime (@NonNull final XdmAtomic aDT,
                                                   @NonNull final XdmAtomic aDur,
                                                   final boolean bSubtract,
                                                   @NonNull final EBinaryOp eOp,
                                                   @NonNull final XdmAtomic aLeft,
                                                   @NonNull final XdmAtomic aRight)
  {
    final EAtomicType eDT = _primitive (aDT.getType ());
    final EAtomicType eDur = aDur.getType ();
    final boolean bOk;
    switch (eDT)
    {
      case DATE_TIME:
      case DATE:
        bOk = _isYmd (eDur) || _isDtd (eDur);
        break;
      case TIME:
        bOk = _isDtd (eDur);
        break;
      default:
        bOk = false;
    }
    if (!bOk)
      throw _typeError (eOp, aLeft, aRight);
    XdmDurationValue aD = (XdmDurationValue) aDur.getValue ();
    if (bSubtract)
      aD = aD.negate ();
    final XdmDateTimeValue aValue = (XdmDateTimeValue) aDT.getValue ();
    XdmDateTimeValue aRes;
    switch (eDT)
    {
      case TIME:
      {
        // Only the clock survives, modulo 24 hours, on the reference date
        final BigDecimal aSecOfDay = BigDecimal.valueOf (aValue.getHour () * 3600L + aValue.getMinute () * 60L)
                                               .add (aValue.getSecond ())
                                               .add (aD.getSeconds ());
        BigDecimal aMod = aSecOfDay.remainder (SECONDS_PER_DAY);
        if (aMod.signum () < 0)
          aMod = aMod.add (SECONDS_PER_DAY);
        final BigDecimal aRef = BigDecimal.valueOf (DateTimeCalendar.getEpochDay (XdmDateTimeValue.REFERENCE_YEAR,
                                                                                  12,
                                                                                  31) *
                                                    86400L);
        aRes = XdmDateTimeValue.ofEpochSeconds (aRef.add (aMod), null).withTimezone (aValue.getTimezone ());
        break;
      }
      case DATE:
      {
        aRes = _addToDateTime (aValue, aD.getMonths (), aD.getSeconds ());
        aRes = XdmDateTimeValue.of (aRes.getYear (),
                                    aRes.getMonth (),
                                    aRes.getDay (),
                                    0,
                                    0,
                                    BigDecimal.ZERO,
                                    aRes.getTimezone ());
        break;
      }
      default:
        aRes = _addToDateTime (aValue, aD.getMonths (), aD.getSeconds ());
        break;
    }
    // op:add-*-to-dateTime returns an xs:dateTime, also for an
    // xs:dateTimeStamp operand such as fn:current-dateTime() (F&O 3.1 section
    // 10.8)
    return XdmAtomic.of (eDT, aRes);
  }

  /**
   * @param eOp
   *        The operator.
   * @param aLeft
   *        Left operand.
   * @param aRight
   *        Right operand.
   * @param aCtx
   *        For the implicit timezone.
   * @return The result, or <code>null</code> if neither operand is a
   *         date/time or duration value (the caller then reports XPTY0004
   *         or handles numbers). Durations times/divided by numbers are
   *         handled here.
   * @throws XPathException
   *         XPTY0004 for an undefined operand combination, FODT0001/FODT0002
   *         on overflow, FOCA0005 for NaN, FOAR0001 for a division by a
   *         zero duration.
   */
  @Nullable
  public static XdmAtomic arithmetic (@NonNull final EBinaryOp eOp,
                                      @NonNull final XdmAtomic aLeft,
                                      @NonNull final XdmAtomic aRight,
                                      @NonNull final DynamicContext aCtx)
  {
    final EAtomicType eL = aLeft.getType ();
    final EAtomicType eR = aRight.getType ();
    final boolean bLDT = eL.isDateTimeFamily ();
    final boolean bRDT = eR.isDateTimeFamily ();
    final boolean bLDur = eL.isDurationFamily ();
    final boolean bRDur = eR.isDurationFamily ();
    if (!bLDT && !bRDT && !bLDur && !bRDur)
      return null;

    if (bLDT && bRDur && (eOp == EBinaryOp.ADD || eOp == EBinaryOp.SUBTRACT))
      return _addDurationToDateTime (aLeft, aRight, eOp == EBinaryOp.SUBTRACT, eOp, aLeft, aRight);
    if (bLDur && bRDT && eOp == EBinaryOp.ADD)
      return _addDurationToDateTime (aRight, aLeft, false, eOp, aLeft, aRight);
    if (bLDT && bRDT && eOp == EBinaryOp.SUBTRACT)
    {
      final EAtomicType ePL = _primitive (eL);
      if (ePL != _primitive (eR) ||
          (ePL != EAtomicType.DATE_TIME && ePL != EAtomicType.DATE && ePL != EAtomicType.TIME))
        throw _typeError (eOp, aLeft, aRight);
      final int nTz = aCtx.getImplicitTimezoneMinutes ();
      final BigDecimal aDiff = ((XdmDateTimeValue) aLeft.getValue ()).getEpochSeconds (nTz)
                                                                    .subtract (((XdmDateTimeValue) aRight.getValue ()).getEpochSeconds (nTz));
      return _duration (EAtomicType.DAY_TIME_DURATION, 0, aDiff);
    }
    if (bLDur && bRDur)
      return _durationArithmetic (eOp, aLeft, aRight);
    if (bLDur && aRight.isNumeric () && (eOp == EBinaryOp.MULTIPLY || eOp == EBinaryOp.DIVIDE))
    {
      if (!_isYmd (eL) && !_isDtd (eL))
        throw _typeError (eOp, aLeft, aRight);
      return _scaleDuration (aLeft, aRight, eOp == EBinaryOp.DIVIDE);
    }
    if (aLeft.isNumeric () && bRDur && eOp == EBinaryOp.MULTIPLY)
    {
      if (!_isYmd (eR) && !_isDtd (eR))
        throw _typeError (eOp, aLeft, aRight);
      return _scaleDuration (aRight, aLeft, false);
    }
    throw _typeError (eOp, aLeft, aRight);
  }

  /**
   * Project a date/time payload onto the components of the target type, after
   * <code>_isCastAllowed</code> accepted the cast: the components the target
   * type does not have are set to the defaults used for partial values (time
   * 00:00:00, month and day 1, the reference year
   * {@link XdmDateTimeValue#REFERENCE_YEAR}, December for xs:time and xs:gDay).
   * The timezone is always kept. xs:dateTime and xs:dateTimeStamp return the
   * payload unchanged.
   *
   * @throws IllegalArgumentException
   *         if the target is not a date/time type
   */
  @NonNull
  private static XdmDateTimeValue _castDateTime (@NonNull final XdmDateTimeValue aV, @NonNull final EAtomicType eTarget)
  {
    final Integer aTz = aV.getTimezone ();
    switch (eTarget)
    {
      case DATE_TIME:
      case DATE_TIME_STAMP:
        return aV;
      case DATE:
        return XdmDateTimeValue.of (aV.getYear (), aV.getMonth (), aV.getDay (), 0, 0, BigDecimal.ZERO, aTz);
      case TIME:
        return XdmDateTimeValue.of (XdmDateTimeValue.REFERENCE_YEAR,
                                    12,
                                    31,
                                    aV.getHour (),
                                    aV.getMinute (),
                                    aV.getSecond (),
                                    aTz);
      case G_YEAR_MONTH:
        return XdmDateTimeValue.of (aV.getYear (), aV.getMonth (), 1, 0, 0, BigDecimal.ZERO, aTz);
      case G_YEAR:
        return XdmDateTimeValue.of (aV.getYear (), 1, 1, 0, 0, BigDecimal.ZERO, aTz);
      case G_MONTH_DAY:
        return XdmDateTimeValue.of (XdmDateTimeValue.REFERENCE_YEAR,
                                    aV.getMonth (),
                                    aV.getDay (),
                                    0,
                                    0,
                                    BigDecimal.ZERO,
                                    aTz);
      case G_DAY:
        return XdmDateTimeValue.of (XdmDateTimeValue.REFERENCE_YEAR, 12, aV.getDay (), 0, 0, BigDecimal.ZERO, aTz);
      case G_MONTH:
        return XdmDateTimeValue.of (XdmDateTimeValue.REFERENCE_YEAR, aV.getMonth (), 1, 0, 0, BigDecimal.ZERO, aTz);
      default:
        throw new IllegalArgumentException ("Not a date/time type: " + eTarget);
    }
  }

  /**
   * The date/time part of the casting table (F&amp;O 3.1 section 19.1): an
   * xs:dateTime (or xs:dateTimeStamp) casts to every date/time type, an xs:date
   * to every one except xs:time, and every other type only to itself.
   *
   * @param eSource
   *        A date/time source type.
   * @param eTarget
   *        A date/time target type.
   * @return <code>true</code> if the cast is allowed.
   */
  private static boolean _isCastAllowed (@NonNull final EAtomicType eSource, @NonNull final EAtomicType eTarget)
  {
    switch (_primitive (eSource))
    {
      case DATE_TIME:
        return true;
      case DATE:
        return eTarget != EAtomicType.TIME;
      default:
        return eSource == eTarget;
    }
  }

  /**
   * Cast between date/time and duration types (dateTime to date, date to
   * gYear, duration to yearMonthDuration, ...), following the casting table of
   * F&amp;O 3.1 section 19.1.
   *
   * @param aValue
   *        Source of a date/time or duration type.
   * @param eTarget
   *        Target date/time or duration type.
   * @return The cast value
   * @throws XPathException
   *         XPTY0004 if not allowed, FORG0001 when casting a value without
   *         timezone to xs:dateTimeStamp.
   */
  @NonNull
  public static XdmAtomic cast (@NonNull final XdmAtomic aValue, @NonNull final EAtomicType eTarget)
  {
    final EAtomicType eSource = aValue.getType ();
    if (eSource.isDurationFamily () && eTarget.isDurationFamily ())
    {
      final XdmDurationValue aD = (XdmDurationValue) aValue.getValue ();
      switch (eTarget)
      {
        case YEAR_MONTH_DURATION:
          return _duration (eTarget, aD.getMonths (), BigDecimal.ZERO);
        case DAY_TIME_DURATION:
          return _duration (eTarget, 0, aD.getSeconds ());
        default:
          return XdmAtomic.of (eTarget, aD);
      }
    }
    if (eSource.isDateTimeFamily () && eTarget.isDateTimeFamily () && _isCastAllowed (eSource, eTarget))
    {
      final XdmDateTimeValue aV = (XdmDateTimeValue) aValue.getValue ();
      if (eTarget == EAtomicType.DATE_TIME_STAMP && !aV.hasTimezone ())
        throw XPathException.invalidValue ("xs:dateTimeStamp requires a timezone");
      return XdmAtomic.of (eTarget, _castDateTime (aV, eTarget));
    }
    throw XPathException.type ("Cannot cast " +
                               eSource.getQualifiedName () +
                               " to " +
                               eTarget.getQualifiedName ());
  }
}
