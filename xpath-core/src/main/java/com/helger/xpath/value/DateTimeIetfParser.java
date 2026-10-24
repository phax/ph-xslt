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
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.XPathException;

/**
 * Parser for the IETF date grammar of <code>fn:parse-ietf-date</code> (F&amp;O
 * 3.1 section 9.9.1), a recursive-descent implementation of the grammar:
 *
 * <pre>
 * input     ::= S? (dayname ","? S)? ((datespec S time) | asctime) S?
 * datespec  ::= daynum dsep monthname dsep year
 * asctime   ::= monthname dsep daynum S time S year
 * dsep      ::= S | (S? "-" S?)
 * time      ::= hours ":" minutes (":" seconds)? (S? timezone)?
 * timezone  ::= tzname | tzoffset (S? "(" S? tzname S? ")")?
 * tzoffset  ::= ("+"|"-") hours ":"? minutes?
 * </pre>
 *
 * Names are matched case-insensitively. A two-digit year is 19xx; a missing
 * timezone is UTC.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class DateTimeIetfParser
{
  private static final String [] DAY_NAMES = { "monday",
                                               "tuesday",
                                               "wednesday",
                                               "thursday",
                                               "friday",
                                               "saturday",
                                               "sunday",
                                               "mon",
                                               "tue",
                                               "wed",
                                               "thu",
                                               "fri",
                                               "sat",
                                               "sun" };
  private static final String [] MONTH_NAMES = { "jan",
                                                 "feb",
                                                 "mar",
                                                 "apr",
                                                 "may",
                                                 "jun",
                                                 "jul",
                                                 "aug",
                                                 "sep",
                                                 "oct",
                                                 "nov",
                                                 "dec" };
  // Longest names first, so "UTC" wins over "UT"
  private static final String [] TZ_NAMES = { "utc", "gmt", "est", "edt", "cst", "cdt", "mst", "mdt", "pst", "pdt", "ut" };
  private static final int [] TZ_OFFSETS = { 0, 0, -300, -240, -360, -300, -420, -360, -480, -420, 0 };

  private final String m_sInput;
  private final String m_sLower;
  private int m_nPos;

  private int m_nDay;
  private int m_nMonth;
  private int m_nYear;
  private int m_nHour;
  private int m_nMinute;
  private BigDecimal m_aSecond = BigDecimal.ZERO;
  private int m_nTz;

  private DateTimeIetfParser (@NonNull final String sInput)
  {
    m_sInput = sInput;
    m_sLower = sInput.toLowerCase (Locale.ROOT);
  }

  @NonNull
  private XPathException _error (@NonNull final String sMsg)
  {
    return new XPathException ("FORG0010", "Invalid IETF date '" + m_sInput + "': " + sMsg);
  }

  private boolean _atEnd ()
  {
    return m_nPos >= m_sInput.length ();
  }

  private char _peek ()
  {
    return m_nPos < m_sInput.length () ? m_sInput.charAt (m_nPos) : '\0';
  }

  private static boolean _isSpace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  private static boolean _isDigit (final char c)
  {
    return c >= '0' && c <= '9';
  }

  /** @return <code>true</code> if at least one whitespace character was skipped. */
  private boolean _skipSpace ()
  {
    final int nStart = m_nPos;
    while (!_atEnd () && _isSpace (_peek ()))
      m_nPos++;
    return m_nPos > nStart;
  }

  private void _requireSpace ()
  {
    if (!_skipSpace ())
      throw _error ("whitespace expected at position " + m_nPos);
  }

  /** @return The index of the matched name or -1; consumes the name on success. */
  private int _matchName (@NonNull final String [] aNames)
  {
    for (int i = 0; i < aNames.length; ++i)
    {
      final String sName = aNames[i];
      if (m_sLower.startsWith (sName, m_nPos))
      {
        final int nEnd = m_nPos + sName.length ();
        // A name must not continue with another letter
        if (nEnd < m_sInput.length () && Character.isLetter (m_sInput.charAt (nEnd)))
          continue;
        m_nPos = nEnd;
        return i;
      }
    }
    return -1;
  }

  /** @return The number of digits available at the current position. */
  private int _digitRun ()
  {
    int i = m_nPos;
    while (i < m_sInput.length () && _isDigit (m_sInput.charAt (i)))
      i++;
    return i - m_nPos;
  }

  private int _digits (final int nMin, final int nMax, @NonNull final String sWhat)
  {
    final int nRun = _digitRun ();
    if (nRun < nMin || nRun > nMax)
      throw _error ("invalid " + sWhat);
    final int ret = Integer.parseInt (m_sInput.substring (m_nPos, m_nPos + nRun));
    m_nPos += nRun;
    return ret;
  }

  /** Read exactly n digits (the run may be longer). */
  private int _fixedDigits (final int n)
  {
    final int ret = Integer.parseInt (m_sInput.substring (m_nPos, m_nPos + n));
    m_nPos += n;
    return ret;
  }

  private void _dsep ()
  {
    final boolean bSpace = _skipSpace ();
    if (_peek () == '-')
    {
      m_nPos++;
      _skipSpace ();
    }
    else
      if (!bSpace)
        throw _error ("date separator expected");
  }

  private void _month ()
  {
    final int nIdx = _matchName (MONTH_NAMES);
    if (nIdx < 0)
      throw _error ("month name expected");
    m_nMonth = nIdx + 1;
  }

  private void _year ()
  {
    final int nRun = _digitRun ();
    if (nRun != 2 && nRun != 4)
      throw _error ("invalid year");
    m_nYear = _digits (nRun, nRun, "year");
    if (nRun == 2)
      m_nYear += 1900;
  }

  /**
   * Try to read a tzoffset at the current position.
   *
   * @return <code>null</code> if there is none (position unchanged).
   */
  @Nullable
  private Integer _tzOffset ()
  {
    final char cSign = _peek ();
    if (cSign != '+' && cSign != '-')
      return null;
    m_nPos++;
    final int nRun = _digitRun ();
    final int nHours;
    int nMinutes = 0;
    if (nRun >= 1 && nRun <= 2 && m_nPos + nRun < m_sInput.length () && m_sInput.charAt (m_nPos + nRun) == ':')
    {
      nHours = _digits (nRun, nRun, "timezone hours");
      m_nPos++;
      final int nMinRun = _digitRun ();
      if (nMinRun == 2)
        nMinutes = _digits (2, 2, "timezone minutes");
      else
        if (nMinRun != 0)
          throw _error ("invalid timezone minutes");
    }
    else
      switch (nRun)
      {
        case 1:
        case 2:
          nHours = _digits (nRun, nRun, "timezone hours");
          break;
        case 3:
          nHours = _fixedDigits (1);
          nMinutes = _fixedDigits (2);
          break;
        case 4:
          nHours = _fixedDigits (2);
          nMinutes = _fixedDigits (2);
          break;
        default:
          throw _error ("invalid timezone offset");
      }
    if (nMinutes > 59 || nHours * 60 + nMinutes > XdmDateTimeValue.MAX_TIMEZONE_MINUTES)
      throw _error ("timezone offset out of range");
    final int nTotal = nHours * 60 + nMinutes;
    return Integer.valueOf (cSign == '-' ? -nTotal : nTotal);
  }

  /** Optional timezone after a time; position is restored if there is none. */
  private void _optionalTimezone ()
  {
    final int nSaved = m_nPos;
    _skipSpace ();
    final int nName = _matchName (TZ_NAMES);
    if (nName >= 0)
    {
      m_nTz = TZ_OFFSETS[nName];
      return;
    }
    final Integer aOffset = _tzOffset ();
    if (aOffset == null)
    {
      m_nPos = nSaved;
      return;
    }
    m_nTz = aOffset.intValue ();
    // Optional parenthesised zone name
    final int nBeforeComment = m_nPos;
    _skipSpace ();
    if (_peek () == '(')
    {
      m_nPos++;
      _skipSpace ();
      if (_matchName (TZ_NAMES) < 0)
        throw _error ("timezone name expected in comment");
      _skipSpace ();
      if (_peek () != ')')
        throw _error ("')' expected");
      m_nPos++;
    }
    else
      m_nPos = nBeforeComment;
  }

  private void _time ()
  {
    m_nHour = _digits (1, 2, "hours");
    if (_peek () != ':')
      throw _error ("':' expected");
    m_nPos++;
    m_nMinute = _digits (2, 2, "minutes");
    if (_peek () == ':')
    {
      m_nPos++;
      final int nStart = m_nPos;
      _digits (2, 2, "seconds");
      if (_peek () == '.')
      {
        m_nPos++;
        if (_digitRun () == 0)
          throw _error ("fractional seconds expected");
        m_nPos += _digitRun ();
      }
      m_aSecond = new BigDecimal (m_sInput.substring (nStart, m_nPos));
    }
    _optionalTimezone ();
  }

  @NonNull
  private XdmDateTimeValue _parse ()
  {
    _skipSpace ();
    // Optional day name
    final int nSaved = m_nPos;
    if (_matchName (DAY_NAMES) >= 0)
    {
      if (_peek () == ',')
        m_nPos++;
      _requireSpace ();
    }
    else
      m_nPos = nSaved;

    if (_isDigit (_peek ()))
    {
      // datespec S time
      m_nDay = _digits (1, 2, "day");
      _dsep ();
      _month ();
      _dsep ();
      _year ();
      _requireSpace ();
      _time ();
    }
    else
    {
      // asctime
      _month ();
      _dsep ();
      m_nDay = _digits (1, 2, "day");
      _requireSpace ();
      _time ();
      _requireSpace ();
      _year ();
    }
    _skipSpace ();
    if (!_atEnd ())
      throw _error ("unexpected trailing content");

    if (m_nDay < 1 || m_nDay > DateTimeCalendar.getDaysInMonth (m_nYear, m_nMonth))
      throw _error ("day out of range");
    boolean bEndOfDay = false;
    if (m_nHour == 24 && m_nMinute == 0 && m_aSecond.signum () == 0)
    {
      bEndOfDay = true;
      m_nHour = 0;
    }
    if (m_nHour > 23 || m_nMinute > 59 || m_aSecond.compareTo (BigDecimal.valueOf (60)) >= 0)
      throw _error ("time out of range");
    final XdmDateTimeValue ret = XdmDateTimeValue.of (m_nYear,
                                                      m_nMonth,
                                                      m_nDay,
                                                      m_nHour,
                                                      m_nMinute,
                                                      m_aSecond,
                                                      Integer.valueOf (m_nTz));
    if (!bEndOfDay)
      return ret;
    final long [] aNext = DateTimeCalendar.getCivilFromEpochDay (ret.getEpochDay () + 1);
    return XdmDateTimeValue.of (aNext[0], (int) aNext[1], (int) aNext[2], 0, 0, BigDecimal.ZERO, ret.getTimezone ());
  }

  /**
   * @param sInput
   *        The IETF date string.
   * @return The xs:dateTime payload (always with timezone).
   * @throws XPathException
   *         FORG0010 if the input is not a valid IETF date.
   */
  @NonNull
  public static XdmDateTimeValue parse (@NonNull final String sInput)
  {
    return new DateTimeIetfParser (sInput)._parse ();
  }
}
