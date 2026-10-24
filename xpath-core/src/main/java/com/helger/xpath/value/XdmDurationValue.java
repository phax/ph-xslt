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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.XPathException;

/**
 * Payload of the duration types: a signed number of months and a signed
 * number of seconds (exact decimal), both with the same sign. Equality is by
 * value, independent of the duration subtype (P1Y equals P12M). Owner:
 * date/time workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmDurationValue
{
  /** The zero duration. */
  public static final XdmDurationValue ZERO = new XdmDurationValue (0, BigDecimal.ZERO);

  private static final BigDecimal SIXTY = BigDecimal.valueOf (60);
  private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf (3600);
  private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf (86400);

  private final long m_nMonths;
  private final BigDecimal m_aSeconds;

  private XdmDurationValue (final long nMonths, @NonNull final BigDecimal aSeconds)
  {
    m_nMonths = nMonths;
    m_aSeconds = aSeconds;
  }

  @NonNull
  private static BigDecimal _normalise (@NonNull final BigDecimal aSeconds)
  {
    if (aSeconds.signum () == 0)
      return BigDecimal.ZERO;
    final BigDecimal ret = aSeconds.stripTrailingZeros ();
    return ret.scale () < 0 ? ret.setScale (0) : ret;
  }

  @NonNull
  private static XPathException _invalid (@NonNull final EAtomicType eType, @NonNull final String sLexical)
  {
    return XPathException.invalidValue ("Invalid " + eType.getQualifiedName () + " value '" + sLexical + "'");
  }

  private static boolean _isDigit (final char c)
  {
    return c >= '0' && c <= '9';
  }

  /**
   * @param eType
   *        xs:duration, xs:yearMonthDuration or xs:dayTimeDuration.
   * @param sLexical
   *        The lexical form.
   * @return The value
   * @throws XPathException
   *         FORG0001 for an invalid lexical form, including a day-time part
   *         in a yearMonthDuration and vice versa.
   */
  @NonNull
  public static XdmDurationValue parse (@NonNull final EAtomicType eType, @NonNull final String sLexical)
  {
    final String s = XdmDateTimeValue.trimXmlWhitespace (sLexical);
    final int nLen = s.length ();
    int i = 0;
    boolean bNeg = false;
    if (i < nLen && s.charAt (i) == '-')
    {
      bNeg = true;
      i++;
    }
    if (i >= nLen || s.charAt (i) != 'P')
      throw _invalid (eType, sLexical);
    i++;
    // Designators in their required order: Y M D, then after T: H M S
    final String sDateOrder = "YMD";
    final String sTimeOrder = "HMS";
    int nDatePos = 0;
    int nTimePos = 0;
    boolean bInTime = false;
    boolean bAnyComponent = false;
    boolean bAnyTimeComponent = false;
    boolean bHasYM = false;
    boolean bHasDT = false;
    BigInteger aYears = BigInteger.ZERO;
    BigInteger aMonths = BigInteger.ZERO;
    BigDecimal aSeconds = BigDecimal.ZERO;
    while (i < nLen)
    {
      final char c = s.charAt (i);
      if (c == 'T')
      {
        if (bInTime)
          throw _invalid (eType, sLexical);
        bInTime = true;
        bHasDT = true;
        i++;
        continue;
      }
      final int nNumStart = i;
      while (i < nLen && _isDigit (s.charAt (i)))
        i++;
      boolean bFraction = false;
      if (i < nLen && s.charAt (i) == '.')
      {
        bFraction = true;
        final int nFracStart = ++i;
        while (i < nLen && _isDigit (s.charAt (i)))
          i++;
        if (i == nFracStart)
          throw _invalid (eType, sLexical);
      }
      if (i == nNumStart || i >= nLen || s.charAt (nNumStart) == '.')
        throw _invalid (eType, sLexical);
      final char cDesignator = s.charAt (i);
      final String sNum = s.substring (nNumStart, i);
      i++;
      if (bInTime)
      {
        final int nIdx = sTimeOrder.indexOf (cDesignator, nTimePos);
        if (nIdx < 0 || bFraction && cDesignator != 'S')
          throw _invalid (eType, sLexical);
        nTimePos = nIdx + 1;
        final BigDecimal aNum = new BigDecimal (sNum);
        if (cDesignator == 'H')
          aSeconds = aSeconds.add (aNum.multiply (SECONDS_PER_HOUR));
        else
          if (cDesignator == 'M')
            aSeconds = aSeconds.add (aNum.multiply (SIXTY));
          else
            aSeconds = aSeconds.add (aNum);
        bAnyTimeComponent = true;
      }
      else
      {
        final int nIdx = sDateOrder.indexOf (cDesignator, nDatePos);
        if (nIdx < 0 || bFraction)
          throw _invalid (eType, sLexical);
        nDatePos = nIdx + 1;
        if (cDesignator == 'Y')
        {
          aYears = new BigInteger (sNum);
          bHasYM = true;
        }
        else
          if (cDesignator == 'M')
          {
            aMonths = new BigInteger (sNum);
            bHasYM = true;
          }
          else
          {
            aSeconds = aSeconds.add (new BigDecimal (sNum).multiply (SECONDS_PER_DAY));
            bHasDT = true;
          }
      }
      bAnyComponent = true;
    }
    if (!bAnyComponent || bInTime && !bAnyTimeComponent)
      throw _invalid (eType, sLexical);
    if (eType == EAtomicType.YEAR_MONTH_DURATION && bHasDT)
      throw _invalid (eType, sLexical);
    if (eType == EAtomicType.DAY_TIME_DURATION && bHasYM)
      throw _invalid (eType, sLexical);
    final BigInteger aTotalMonths = aYears.multiply (BigInteger.valueOf (12)).add (aMonths);
    if (aTotalMonths.bitLength () > 63)
      throw XPathException.invalidValue ("Duration '" + sLexical + "' is out of range");
    long nMonths = aTotalMonths.longValue ();
    if (bNeg)
    {
      nMonths = -nMonths;
      aSeconds = aSeconds.negate ();
    }
    return new XdmDurationValue (nMonths, _normalise (aSeconds));
  }

  /**
   * @param nMonths
   *        Signed months.
   * @param aSeconds
   *        Signed seconds; must not have the opposite sign of the months.
   * @return The value
   */
  @NonNull
  public static XdmDurationValue of (final long nMonths, @NonNull final BigDecimal aSeconds)
  {
    ValueEnforcer.notNull (aSeconds, "Seconds");
    if (nMonths > 0 && aSeconds.signum () < 0 || nMonths < 0 && aSeconds.signum () > 0)
      throw new IllegalArgumentException ("Months and seconds must have the same sign");
    if (nMonths == 0 && aSeconds.signum () == 0)
      return ZERO;
    return new XdmDurationValue (nMonths, _normalise (aSeconds));
  }

  public long getMonths ()
  {
    return m_nMonths;
  }

  /** @return The seconds part, normalised (no trailing zeros). */
  @NonNull
  public BigDecimal getSeconds ()
  {
    return m_aSeconds;
  }

  public boolean isNegative ()
  {
    return m_nMonths < 0 || m_aSeconds.signum () < 0;
  }

  public boolean isZero ()
  {
    return m_nMonths == 0 && m_aSeconds.signum () == 0;
  }

  /**
   * @return The negated duration.
   * @throws XPathException
   *         FODT0002 if the months cannot be negated.
   */
  @NonNull
  public XdmDurationValue negate ()
  {
    if (m_nMonths == Long.MIN_VALUE)
      throw new XPathException ("FODT0002", "Duration overflow");
    return new XdmDurationValue (-m_nMonths, m_aSeconds.negate ());
  }

  private static void _appendDayTime (@NonNull final StringBuilder aSB,
                                      @NonNull final BigDecimal aAbsSeconds,
                                      final boolean bForceZero)
  {
    final BigDecimal [] aDays = aAbsSeconds.divideAndRemainder (SECONDS_PER_DAY);
    final BigDecimal [] aHours = aDays[1].divideAndRemainder (SECONDS_PER_HOUR);
    final BigDecimal [] aMinutes = aHours[1].divideAndRemainder (SIXTY);
    final BigInteger aD = aDays[0].toBigInteger ();
    final int nH = aHours[0].intValue ();
    final int nM = aMinutes[0].intValue ();
    final BigDecimal aS = _normalise (aMinutes[1]);
    if (aD.signum () != 0)
      aSB.append (aD).append ('D');
    if (nH == 0 && nM == 0 && aS.signum () == 0)
    {
      if (aD.signum () == 0 && bForceZero)
        aSB.append ("T0S");
      return;
    }
    aSB.append ('T');
    if (nH != 0)
      aSB.append (nH).append ('H');
    if (nM != 0)
      aSB.append (nM).append ('M');
    if (aS.signum () != 0)
      aSB.append (aS.toPlainString ()).append ('S');
  }

  /**
   * @param eType
   *        The duration type of the value.
   * @return The canonical lexical form for that type.
   */
  @NonNull
  public String toLexical (@NonNull final EAtomicType eType)
  {
    final StringBuilder aSB = new StringBuilder ();
    final long nAbsMonths = Math.abs (m_nMonths);
    final BigDecimal aAbsSeconds = m_aSeconds.abs ();
    switch (eType)
    {
      case YEAR_MONTH_DURATION:
      {
        if (m_nMonths < 0)
          aSB.append ('-');
        aSB.append ('P');
        final long nY = nAbsMonths / 12;
        final long nM = nAbsMonths % 12;
        if (nY != 0)
          aSB.append (nY).append ('Y');
        if (nM != 0 || nY == 0)
          aSB.append (nM).append ('M');
        return aSB.toString ();
      }
      case DAY_TIME_DURATION:
        if (m_aSeconds.signum () < 0)
          aSB.append ('-');
        aSB.append ('P');
        _appendDayTime (aSB, aAbsSeconds, true);
        return aSB.toString ();
      default:
      {
        if (isNegative ())
          aSB.append ('-');
        aSB.append ('P');
        final long nY = nAbsMonths / 12;
        final long nM = nAbsMonths % 12;
        if (nY != 0)
          aSB.append (nY).append ('Y');
        if (nM != 0)
          aSB.append (nM).append ('M');
        _appendDayTime (aSB, aAbsSeconds, nAbsMonths == 0);
        return aSB.toString ();
      }
    }
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final XdmDurationValue rhs = (XdmDurationValue) o;
    return m_nMonths == rhs.m_nMonths && m_aSeconds.compareTo (rhs.m_aSeconds) == 0;
  }

  @Override
  public int hashCode ()
  {
    return Long.hashCode (m_nMonths) * 31 + m_aSeconds.hashCode ();
  }

  @Override
  public String toString ()
  {
    return toLexical (EAtomicType.DURATION);
  }
}
