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
import java.math.RoundingMode;
import java.time.OffsetDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.XPathException;

/**
 * Payload of the date/time types (xs:dateTime, xs:dateTimeStamp, xs:date,
 * xs:time and the g* types): the XSD 1.1 value space with years of up to nine
 * digits (including year 0, which is 1 BCE, and negative years), fractional
 * seconds without rounding, and an optional timezone offset in minutes.
 * <p>
 * Every value carries all seven components. Components a type does not have
 * hold the reference values of F&amp;O 3.1 section 10.4 (year 1972, month 12,
 * day 31 for xs:time; month 1, day 1, ... for the g* types), so values of one
 * type compare correctly as instants. The type itself is not part of the
 * payload.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmDateTimeValue
{
  /** Reference year for types without a year (a leap year). */
  public static final int REFERENCE_YEAR = 1972;
  /** The largest timezone offset in minutes. */
  public static final int MAX_TIMEZONE_MINUTES = 14 * 60;

  private static final BigDecimal SIXTY = BigDecimal.valueOf (60);
  private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf (86400);

  private final long m_nYear;
  private final int m_nMonth;
  private final int m_nDay;
  private final int m_nHour;
  private final int m_nMinute;
  private final BigDecimal m_aSecond;
  private final boolean m_bHasTimezone;
  private final int m_nTimezoneMinutes;

  private XdmDateTimeValue (final long nYear,
                            final int nMonth,
                            final int nDay,
                            final int nHour,
                            final int nMinute,
                            @NonNull final BigDecimal aSecond,
                            final boolean bHasTimezone,
                            final int nTimezoneMinutes)
  {
    m_nYear = nYear;
    m_nMonth = nMonth;
    m_nDay = nDay;
    m_nHour = nHour;
    m_nMinute = nMinute;
    m_aSecond = aSecond;
    m_bHasTimezone = bHasTimezone;
    m_nTimezoneMinutes = bHasTimezone ? nTimezoneMinutes : 0;
  }

  @NonNull
  private static BigDecimal _normaliseSeconds (@NonNull final BigDecimal aSecond)
  {
    if (aSecond.signum () == 0)
      return BigDecimal.ZERO;
    final BigDecimal ret = aSecond.stripTrailingZeros ();
    return ret.scale () < 0 ? ret.setScale (0) : ret;
  }

  private static boolean _isXmlSpace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
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

  private static int _digitsFrom (@NonNull final String s, final int nStart)
  {
    int i = nStart;
    while (i < s.length () && _isDigit (s.charAt (i)))
      i++;
    return i;
  }

  private static boolean _twoDigitsAt (@NonNull final String s, final int i)
  {
    return i + 2 <= s.length () && _isDigit (s.charAt (i)) && _isDigit (s.charAt (i + 1));
  }

  private static int _twoDigits (@NonNull final String s, final int i)
  {
    return (s.charAt (i) - '0') * 10 + (s.charAt (i + 1) - '0');
  }

  /**
   * Lexical scanner state for one parse call.
   */
  private static final class Scanner
  {
    private final String m_s;
    private final EAtomicType m_eType;
    private int m_nPos;

    Scanner (@NonNull final String s, @NonNull final EAtomicType eType)
    {
      m_s = s;
      m_eType = eType;
    }

    @NonNull
    XPathException invalid ()
    {
      return _invalid (m_eType, m_s);
    }

    void expect (final char c)
    {
      if (m_nPos >= m_s.length () || m_s.charAt (m_nPos) != c)
        throw invalid ();
      m_nPos++;
    }

    int twoDigits ()
    {
      if (!_twoDigitsAt (m_s, m_nPos))
        throw invalid ();
      final int ret = _twoDigits (m_s, m_nPos);
      m_nPos += 2;
      return ret;
    }

    long year ()
    {
      final int nStart = m_nPos;
      if (m_nPos < m_s.length () && m_s.charAt (m_nPos) == '-')
        m_nPos++;
      final int nDigitStart = m_nPos;
      final int nEnd = _digitsFrom (m_s, m_nPos);
      final int nLen = nEnd - nDigitStart;
      if (nLen < 4)
        throw invalid ();
      // More than four digits must not have a leading zero
      if (nLen > 4 && m_s.charAt (nDigitStart) == '0')
        throw invalid ();
      m_nPos = nEnd;
      if (nLen > 9)
        throw new XPathException ("FODT0001",
                                  "Year '" + m_s.substring (nStart, nEnd) + "' is outside the supported range");
      return Long.parseLong (m_s.substring (nStart, nEnd));
    }

    @NonNull
    BigDecimal seconds ()
    {
      if (!_twoDigitsAt (m_s, m_nPos))
        throw invalid ();
      int nEnd = m_nPos + 2;
      if (nEnd < m_s.length () && m_s.charAt (nEnd) == '.')
      {
        final int nFracEnd = _digitsFrom (m_s, nEnd + 1);
        if (nFracEnd == nEnd + 1)
          throw invalid ();
        nEnd = nFracEnd;
      }
      final BigDecimal ret = new BigDecimal (m_s.substring (m_nPos, nEnd));
      m_nPos = nEnd;
      return ret;
    }

    /**
     * @return <code>null</code> if no timezone follows, else the offset in
     *         minutes. The string must end after it.
     */
    @Nullable
    Integer timezoneAtEnd ()
    {
      final int nRest = m_s.length () - m_nPos;
      if (nRest == 0)
        return null;
      if (nRest == 1 && m_s.charAt (m_nPos) == 'Z')
        return Integer.valueOf (0);
      if (nRest == 6)
      {
        final char cSign = m_s.charAt (m_nPos);
        if ((cSign == '+' || cSign == '-') &&
            _twoDigitsAt (m_s, m_nPos + 1) &&
            m_s.charAt (m_nPos + 3) == ':' &&
            _twoDigitsAt (m_s, m_nPos + 4))
        {
          final int nH = _twoDigits (m_s, m_nPos + 1);
          final int nM = _twoDigits (m_s, m_nPos + 4);
          if (nM > 59 || nH * 60 + nM > MAX_TIMEZONE_MINUTES)
            throw invalid ();
          final int nTotal = nH * 60 + nM;
          return Integer.valueOf (cSign == '-' ? -nTotal : nTotal);
        }
      }
      throw invalid ();
    }
  }

  /**
   * Validate components and build the value; 24:00:00 rolls into the next day
   * (or, for xs:time, to 00:00:00 of the same reference day).
   */
  @NonNull
  private static XdmDateTimeValue _build (@NonNull final Scanner aScanner,
                                          final long nYear,
                                          final int nMonth,
                                          final int nDay,
                                          final int nHour,
                                          final int nMinute,
                                          @NonNull final BigDecimal aSecond,
                                          @Nullable final Integer aTz,
                                          final boolean bIsTime)
  {
    if (nMonth < 1 || nMonth > 12)
      throw aScanner.invalid ();
    if (nDay < 1 || nDay > DateTimeCalendar.getDaysInMonth (nYear, nMonth))
      throw aScanner.invalid ();
    if (nMinute > 59 || aSecond.compareTo (SIXTY) >= 0)
      throw aScanner.invalid ();
    if (nHour > 24 || nHour == 24 && (nMinute != 0 || aSecond.signum () != 0))
      throw aScanner.invalid ();
    final boolean bHasTz = aTz != null;
    final int nTz = bHasTz ? aTz.intValue () : 0;
    if (nHour == 24)
    {
      if (bIsTime)
        return new XdmDateTimeValue (nYear, nMonth, nDay, 0, 0, BigDecimal.ZERO, bHasTz, nTz);
      final long [] aNext = DateTimeCalendar.getCivilFromEpochDay (DateTimeCalendar.getEpochDay (nYear, nMonth, nDay) +
                                                                   1);
      if (Math.abs (aNext[0]) > DateTimeCalendar.MAX_ABS_YEAR)
        throw new XPathException ("FODT0001", "Year is outside the supported range");
      return new XdmDateTimeValue (aNext[0], (int) aNext[1], (int) aNext[2], 0, 0, BigDecimal.ZERO, bHasTz, nTz);
    }
    return new XdmDateTimeValue (nYear, nMonth, nDay, nHour, nMinute, _normaliseSeconds (aSecond), bHasTz, nTz);
  }

  /**
   * @param s
   *        Source string.
   * @return The string without leading and trailing XML whitespace (space,
   *         tab, CR, LF).
   */
  @NonNull
  public static String trimXmlWhitespace (@NonNull final String s)
  {
    int nStart = 0;
    int nEnd = s.length ();
    while (nStart < nEnd && _isXmlSpace (s.charAt (nStart)))
      nStart++;
    while (nEnd > nStart && _isXmlSpace (s.charAt (nEnd - 1)))
      nEnd--;
    return s.substring (nStart, nEnd);
  }

  /**
   * Parse a lexical value.
   *
   * @param eType
   *        One of the date/time types.
   * @param sLexical
   *        The lexical form, whitespace already collapsed (surrounding XML
   *        whitespace is tolerated).
   * @return The value
   * @throws XPathException
   *         FORG0001 for an invalid lexical form or out-of-range component,
   *         FODT0001 for a year beyond nine digits.
   */
  @NonNull
  public static XdmDateTimeValue parse (@NonNull final EAtomicType eType, @NonNull final String sLexical)
  {
    final String s = trimXmlWhitespace (sLexical);
    final Scanner aSc = new Scanner (s, eType);
    switch (eType)
    {
      case DATE_TIME:
      case DATE_TIME_STAMP:
      {
        final long nYear = aSc.year ();
        aSc.expect ('-');
        final int nMonth = aSc.twoDigits ();
        aSc.expect ('-');
        final int nDay = aSc.twoDigits ();
        aSc.expect ('T');
        final int nHour = aSc.twoDigits ();
        aSc.expect (':');
        final int nMinute = aSc.twoDigits ();
        aSc.expect (':');
        final BigDecimal aSec = aSc.seconds ();
        final Integer aTz = aSc.timezoneAtEnd ();
        if (eType == EAtomicType.DATE_TIME_STAMP && aTz == null)
          throw XPathException.invalidValue ("xs:dateTimeStamp requires a timezone: '" + sLexical + "'");
        return _build (aSc, nYear, nMonth, nDay, nHour, nMinute, aSec, aTz, false);
      }
      case DATE:
      {
        final long nYear = aSc.year ();
        aSc.expect ('-');
        final int nMonth = aSc.twoDigits ();
        aSc.expect ('-');
        final int nDay = aSc.twoDigits ();
        return _build (aSc, nYear, nMonth, nDay, 0, 0, BigDecimal.ZERO, aSc.timezoneAtEnd (), false);
      }
      case TIME:
      {
        final int nHour = aSc.twoDigits ();
        aSc.expect (':');
        final int nMinute = aSc.twoDigits ();
        aSc.expect (':');
        final BigDecimal aSec = aSc.seconds ();
        return _build (aSc, REFERENCE_YEAR, 12, 31, nHour, nMinute, aSec, aSc.timezoneAtEnd (), true);
      }
      case G_YEAR_MONTH:
      {
        final long nYear = aSc.year ();
        aSc.expect ('-');
        final int nMonth = aSc.twoDigits ();
        return _build (aSc, nYear, nMonth, 1, 0, 0, BigDecimal.ZERO, aSc.timezoneAtEnd (), false);
      }
      case G_YEAR:
      {
        final long nYear = aSc.year ();
        return _build (aSc, nYear, 1, 1, 0, 0, BigDecimal.ZERO, aSc.timezoneAtEnd (), false);
      }
      case G_MONTH_DAY:
      {
        aSc.expect ('-');
        aSc.expect ('-');
        final int nMonth = aSc.twoDigits ();
        aSc.expect ('-');
        final int nDay = aSc.twoDigits ();
        return _build (aSc, REFERENCE_YEAR, nMonth, nDay, 0, 0, BigDecimal.ZERO, aSc.timezoneAtEnd (), false);
      }
      case G_DAY:
      {
        aSc.expect ('-');
        aSc.expect ('-');
        aSc.expect ('-');
        final int nDay = aSc.twoDigits ();
        return _build (aSc, REFERENCE_YEAR, 12, nDay, 0, 0, BigDecimal.ZERO, aSc.timezoneAtEnd (), false);
      }
      case G_MONTH:
      {
        aSc.expect ('-');
        aSc.expect ('-');
        final int nMonth = aSc.twoDigits ();
        return _build (aSc, REFERENCE_YEAR, nMonth, 1, 0, 0, BigDecimal.ZERO, aSc.timezoneAtEnd (), false);
      }
      default:
        throw new IllegalArgumentException ("Not a date/time type: " + eType);
    }
  }

  /**
   * Create a value from components. Components the target type does not have
   * should hold the reference values (see the class comment).
   *
   * @param nYear
   *        Astronomical year (0 = 1 BCE), at most nine digits.
   * @param nMonth
   *        1..12
   * @param nDay
   *        1..days in month
   * @param nHour
   *        0..23
   * @param nMinute
   *        0..59
   * @param aSecond
   *        0 &lt;= s &lt; 60
   * @param aTimezoneMinutes
   *        Offset in minutes (-840..840) or <code>null</code> for none.
   * @return The value.
   * @throws XPathException
   *         FODT0001 if the year is out of range.
   * @throws IllegalArgumentException
   *         for any other invalid component.
   */
  @NonNull
  public static XdmDateTimeValue of (final long nYear,
                                     final int nMonth,
                                     final int nDay,
                                     final int nHour,
                                     final int nMinute,
                                     @NonNull final BigDecimal aSecond,
                                     @Nullable final Integer aTimezoneMinutes)
  {
    if (Math.abs (nYear) > DateTimeCalendar.MAX_ABS_YEAR)
      throw new XPathException ("FODT0001", "Year " + nYear + " is outside the supported range");
    if (nMonth < 1 ||
        nMonth > 12 ||
        nDay < 1 ||
        nDay > DateTimeCalendar.getDaysInMonth (nYear, nMonth) ||
        nHour < 0 ||
        nHour > 23 ||
        nMinute < 0 ||
        nMinute > 59 ||
        aSecond.signum () < 0 ||
        aSecond.compareTo (SIXTY) >= 0)
      throw new IllegalArgumentException ("Invalid date/time components");
    if (aTimezoneMinutes != null && Math.abs (aTimezoneMinutes.intValue ()) > MAX_TIMEZONE_MINUTES)
      throw new IllegalArgumentException ("Invalid timezone offset " + aTimezoneMinutes);
    return new XdmDateTimeValue (nYear,
                                 nMonth,
                                 nDay,
                                 nHour,
                                 nMinute,
                                 _normaliseSeconds (aSecond),
                                 aTimezoneMinutes != null,
                                 aTimezoneMinutes == null ? 0 : aTimezoneMinutes.intValue ());
  }

  /**
   * Create the value at a given instant, displayed in a timezone.
   *
   * @param aEpochSeconds
   *        Seconds since 1970-01-01T00:00:00Z (UTC).
   * @param aTimezoneMinutes
   *        Timezone in which the local components are expressed, or
   *        <code>null</code>: then the instant is taken as local time without
   *        timezone.
   * @return The value
   * @throws XPathException
   *         FODT0001 if the year is out of range.
   */
  @NonNull
  public static XdmDateTimeValue ofEpochSeconds (@NonNull final BigDecimal aEpochSeconds,
                                                 @Nullable final Integer aTimezoneMinutes)
  {
    final int nTz = aTimezoneMinutes == null ? 0 : aTimezoneMinutes.intValue ();
    final BigDecimal aLocal = aEpochSeconds.add (BigDecimal.valueOf (nTz * 60L));
    final BigDecimal [] aDayAndRest = aLocal.divideAndRemainder (SECONDS_PER_DAY);
    BigInteger aDays = aDayAndRest[0].toBigInteger ();
    BigDecimal aRest = aDayAndRest[1];
    if (aRest.signum () < 0)
    {
      aRest = aRest.add (SECONDS_PER_DAY);
      aDays = aDays.subtract (BigInteger.ONE);
    }
    // ~ 3.66e11 days for 999,999,999 years
    if (aDays.abs ().compareTo (BigInteger.valueOf (400_000_000_000L)) > 0)
      throw new XPathException ("FODT0001", "Date/time value is outside the supported range");
    final long [] aCivil = DateTimeCalendar.getCivilFromEpochDay (aDays.longValueExact ());
    final BigDecimal aWholeRest = aRest.setScale (0, RoundingMode.FLOOR);
    final int nSecOfDay = aWholeRest.intValueExact ();
    final BigDecimal aSecond = aRest.subtract (aWholeRest).add (BigDecimal.valueOf (nSecOfDay % 60));
    return of (aCivil[0],
               (int) aCivil[1],
               (int) aCivil[2],
               nSecOfDay / 3600,
               nSecOfDay / 60 % 60,
               aSecond,
               aTimezoneMinutes);
  }

  /**
   * @param aDateTime
   *        A java.time value, e.g. the current date/time.
   * @return The xs:dateTime payload with the offset of the value (whole
   *         minutes).
   */
  @NonNull
  public static XdmDateTimeValue ofOffsetDateTime (@NonNull final OffsetDateTime aDateTime)
  {
    final BigDecimal aSecond = BigDecimal.valueOf (aDateTime.getSecond ())
                                         .add (BigDecimal.valueOf (aDateTime.getNano (), 9));
    return of (aDateTime.getYear (),
               aDateTime.getMonthValue (),
               aDateTime.getDayOfMonth (),
               aDateTime.getHour (),
               aDateTime.getMinute (),
               aSecond,
               Integer.valueOf (aDateTime.getOffset ().getTotalSeconds () / 60));
  }

  /** @return The astronomical year (0 = 1 BCE). */
  public long getYear ()
  {
    return m_nYear;
  }

  /** @return 1..12 */
  public int getMonth ()
  {
    return m_nMonth;
  }

  /** @return 1..31 */
  public int getDay ()
  {
    return m_nDay;
  }

  /** @return 0..23 */
  public int getHour ()
  {
    return m_nHour;
  }

  /** @return 0..59 */
  public int getMinute ()
  {
    return m_nMinute;
  }

  /** @return Seconds including the fraction, 0 &lt;= s &lt; 60, normalised (no trailing zeros). */
  @NonNull
  public BigDecimal getSecond ()
  {
    return m_aSecond;
  }

  public boolean hasTimezone ()
  {
    return m_bHasTimezone;
  }

  /** @return The timezone offset in minutes; 0 if there is no timezone. */
  public int getTimezoneMinutes ()
  {
    return m_nTimezoneMinutes;
  }

  /** @return The timezone offset in minutes or <code>null</code>. */
  @Nullable
  public Integer getTimezone ()
  {
    return m_bHasTimezone ? Integer.valueOf (m_nTimezoneMinutes) : null;
  }

  /** @return Days since 1970-01-01 of the local date. */
  public long getEpochDay ()
  {
    return DateTimeCalendar.getEpochDay (m_nYear, m_nMonth, m_nDay);
  }

  /**
   * @param nImplicitTimezoneMinutes
   *        Applied if the value has no timezone.
   * @return The instant as seconds since 1970-01-01T00:00:00Z.
   */
  @NonNull
  public BigDecimal getEpochSeconds (final int nImplicitTimezoneMinutes)
  {
    final int nTz = m_bHasTimezone ? m_nTimezoneMinutes : nImplicitTimezoneMinutes;
    final long nWhole = getEpochDay () * 86400 + m_nHour * 3600L + m_nMinute * 60L - nTz * 60L;
    return BigDecimal.valueOf (nWhole).add (m_aSecond);
  }

  /**
   * @param aTimezoneMinutes
   *        The new timezone or <code>null</code>.
   * @return The same local components with a different (or no) timezone.
   */
  @NonNull
  public XdmDateTimeValue withTimezone (@Nullable final Integer aTimezoneMinutes)
  {
    return new XdmDateTimeValue (m_nYear,
                                 m_nMonth,
                                 m_nDay,
                                 m_nHour,
                                 m_nMinute,
                                 m_aSecond,
                                 aTimezoneMinutes != null,
                                 aTimezoneMinutes == null ? 0 : aTimezoneMinutes.intValue ());
  }

  @NonNull
  private static String _pad2 (final int n)
  {
    return n < 10 ? "0" + n : Integer.toString (n);
  }

  /**
   * @param nYear
   *        Astronomical year.
   * @return At least four digits, the sign outside the padding.
   */
  @NonNull
  public static String formatYear (final long nYear)
  {
    final String sAbs = Long.toString (Math.abs (nYear));
    final StringBuilder aSB = new StringBuilder ();
    if (nYear < 0)
      aSB.append ('-');
    for (int i = sAbs.length (); i < 4; ++i)
      aSB.append ('0');
    return aSB.append (sAbs).toString ();
  }

  /**
   * @param nMinutes
   *        Offset in minutes.
   * @return <code>Z</code> or <code>+hh:mm</code>/<code>-hh:mm</code>.
   */
  @NonNull
  public static String formatTimezone (final int nMinutes)
  {
    if (nMinutes == 0)
      return "Z";
    final int nAbs = Math.abs (nMinutes);
    return (nMinutes < 0 ? "-" : "+") + _pad2 (nAbs / 60) + ":" + _pad2 (nAbs % 60);
  }

  @NonNull
  private String _formatSeconds ()
  {
    final BigDecimal aWhole = m_aSecond.setScale (0, RoundingMode.FLOOR);
    final String sWhole = _pad2 (aWhole.intValue ());
    if (m_aSecond.scale () <= 0)
      return sWhole;
    final String sPlain = m_aSecond.toPlainString ();
    return sWhole + sPlain.substring (sPlain.indexOf ('.'));
  }

  /**
   * @param eType
   *        The type of the value this payload belongs to.
   * @return The canonical lexical form for that type.
   */
  @NonNull
  public String toLexical (@NonNull final EAtomicType eType)
  {
    final StringBuilder aSB = new StringBuilder ();
    switch (eType)
    {
      case DATE_TIME:
      case DATE_TIME_STAMP:
        aSB.append (formatYear (m_nYear))
           .append ('-')
           .append (_pad2 (m_nMonth))
           .append ('-')
           .append (_pad2 (m_nDay))
           .append ('T')
           .append (_pad2 (m_nHour))
           .append (':')
           .append (_pad2 (m_nMinute))
           .append (':')
           .append (_formatSeconds ());
        break;
      case DATE:
        aSB.append (formatYear (m_nYear)).append ('-').append (_pad2 (m_nMonth)).append ('-').append (_pad2 (m_nDay));
        break;
      case TIME:
        aSB.append (_pad2 (m_nHour)).append (':').append (_pad2 (m_nMinute)).append (':').append (_formatSeconds ());
        break;
      case G_YEAR_MONTH:
        aSB.append (formatYear (m_nYear)).append ('-').append (_pad2 (m_nMonth));
        break;
      case G_YEAR:
        aSB.append (formatYear (m_nYear));
        break;
      case G_MONTH_DAY:
        aSB.append ("--").append (_pad2 (m_nMonth)).append ('-').append (_pad2 (m_nDay));
        break;
      case G_DAY:
        aSB.append ("---").append (_pad2 (m_nDay));
        break;
      case G_MONTH:
        aSB.append ("--").append (_pad2 (m_nMonth));
        break;
      default:
        throw new IllegalArgumentException ("Not a date/time type: " + eType);
    }
    if (m_bHasTimezone)
      aSB.append (formatTimezone (m_nTimezoneMinutes));
    return aSB.toString ();
  }

  /**
   * Compare two values of the same type for ordering or equality (F&amp;O 3.1
   * section 10.4): both are normalised to UTC, a value without timezone takes
   * the implicit timezone. xs:time values and the g* types compare on their
   * reference dates.
   *
   * @param aOther
   *        Other value of the same type family.
   * @param eType
   *        The type of both values (dateTimeStamp compares as dateTime).
   * @param nImplicitTimezoneMinutes
   *        Timezone applied to values that have none.
   * @return negative, 0, positive. Only xs:dateTime, xs:date, xs:time, and
   *         for equality the g* types, are ordered; callers check that.
   */
  public int compareTo (@NonNull final XdmDateTimeValue aOther,
                        @NonNull final EAtomicType eType,
                        final int nImplicitTimezoneMinutes)
  {
    return getEpochSeconds (nImplicitTimezoneMinutes).compareTo (aOther.getEpochSeconds (nImplicitTimezoneMinutes));
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final XdmDateTimeValue rhs = (XdmDateTimeValue) o;
    return m_nYear == rhs.m_nYear &&
           m_nMonth == rhs.m_nMonth &&
           m_nDay == rhs.m_nDay &&
           m_nHour == rhs.m_nHour &&
           m_nMinute == rhs.m_nMinute &&
           m_aSecond.equals (rhs.m_aSecond) &&
           m_bHasTimezone == rhs.m_bHasTimezone &&
           m_nTimezoneMinutes == rhs.m_nTimezoneMinutes;
  }

  @Override
  public int hashCode ()
  {
    int ret = Long.hashCode (m_nYear);
    ret = ret * 31 + m_nMonth;
    ret = ret * 31 + m_nDay;
    ret = ret * 31 + m_nHour;
    ret = ret * 31 + m_nMinute;
    ret = ret * 31 + m_aSecond.hashCode ();
    ret = ret * 31 + (m_bHasTimezone ? 1 : 0);
    return ret * 31 + m_nTimezoneMinutes;
  }

  @Override
  public String toString ()
  {
    return toLexical (EAtomicType.DATE_TIME);
  }
}
