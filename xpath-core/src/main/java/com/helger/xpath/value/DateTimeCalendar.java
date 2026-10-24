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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Proleptic Gregorian calendar arithmetic on astronomical year numbers (year 0
 * is 1 BCE, as in XSD 1.1), independent of <code>java.time</code> and of the
 * machine's locale and timezone. Day numbers count from 1970-01-01 (day 0).
 *
 * @author Philip Helger
 */
@Immutable
public final class DateTimeCalendar
{
  /** The largest absolute year supported (nine digits). */
  public static final long MAX_ABS_YEAR = 999_999_999L;

  @PresentForCodeCoverage
  private static final DateTimeCalendar INSTANCE = new DateTimeCalendar ();

  private DateTimeCalendar ()
  {}

  /**
   * @param nYear
   *        Astronomical year.
   * @return <code>true</code> if the year is a leap year.
   */
  public static boolean isLeapYear (final long nYear)
  {
    return Math.floorMod (nYear, 4) == 0 && (Math.floorMod (nYear, 100) != 0 || Math.floorMod (nYear, 400) == 0);
  }

  /**
   * @param nYear
   *        Astronomical year.
   * @param nMonth
   *        Month 1..12.
   * @return The number of days of that month.
   */
  public static int getDaysInMonth (final long nYear, final int nMonth)
  {
    switch (nMonth)
    {
      case 4:
      case 6:
      case 9:
      case 11:
        return 30;
      case 2:
        return isLeapYear (nYear) ? 29 : 28;
      default:
        return 31;
    }
  }

  /**
   * @param nYear
   *        Astronomical year.
   * @param nMonth
   *        Month 1..12.
   * @param nDay
   *        Day of month.
   * @return Days since 1970-01-01.
   */
  public static long getEpochDay (final long nYear, final int nMonth, final int nDay)
  {
    final long nY = nMonth <= 2 ? nYear - 1 : nYear;
    final long nEra = Math.floorDiv (nY, 400);
    final long nYoe = nY - nEra * 400;
    final long nDoy = (153L * (nMonth > 2 ? nMonth - 3 : nMonth + 9) + 2) / 5 + nDay - 1;
    final long nDoe = nYoe * 365 + nYoe / 4 - nYoe / 100 + nDoy;
    return nEra * 146097 + nDoe - 719468;
  }

  /**
   * @param nEpochDay
   *        Days since 1970-01-01.
   * @return <code>[year, month, day]</code>.
   */
  public static long [] getCivilFromEpochDay (final long nEpochDay)
  {
    final long nZ = nEpochDay + 719468;
    final long nEra = Math.floorDiv (nZ, 146097);
    final long nDoe = nZ - nEra * 146097;
    final long nYoe = (nDoe - nDoe / 1460 + nDoe / 36524 - nDoe / 146096) / 365;
    final long nDoy = nDoe - (365 * nYoe + nYoe / 4 - nYoe / 100);
    final long nMp = (5 * nDoy + 2) / 153;
    final long nDay = nDoy - (153 * nMp + 2) / 5 + 1;
    final long nMonth = nMp < 10 ? nMp + 3 : nMp - 9;
    final long nYear = nYoe + nEra * 400 + (nMonth <= 2 ? 1 : 0);
    return new long [] { nYear, nMonth, nDay };
  }

  /**
   * @param nEpochDay
   *        Days since 1970-01-01.
   * @return ISO day of week, 1 = Monday ... 7 = Sunday.
   */
  public static int getIsoDayOfWeek (final long nEpochDay)
  {
    return (int) Math.floorMod (nEpochDay + 3, 7L) + 1;
  }

  /**
   * @param nYear
   *        Astronomical year.
   * @param nMonth
   *        Month.
   * @param nDay
   *        Day.
   * @return Day within the year, 1-based.
   */
  public static int getDayOfYear (final long nYear, final int nMonth, final int nDay)
  {
    return (int) (getEpochDay (nYear, nMonth, nDay) - getEpochDay (nYear, 1, 1)) + 1;
  }

  /**
   * @param nYear
   *        Astronomical year.
   * @param nMonth
   *        Month.
   * @param nDay
   *        Day.
   * @return The ISO 8601 week number (1..53); the week belongs to the year of
   *         its Thursday.
   */
  public static int getIsoWeekOfYear (final long nYear, final int nMonth, final int nDay)
  {
    final long nEpochDay = getEpochDay (nYear, nMonth, nDay);
    final long nThursday = nEpochDay + (4 - getIsoDayOfWeek (nEpochDay));
    final long nIsoYear = getCivilFromEpochDay (nThursday)[0];
    return (int) ((nThursday - getEpochDay (nIsoYear, 1, 1)) / 7) + 1;
  }

  /**
   * @param nFirstOfMonth
   *        Epoch day of the first day of a month.
   * @return The day of the month (possibly zero or negative) of the Monday
   *         that starts the week containing the month's first Thursday.
   */
  private static int _getMondayOfWeek1 (final long nFirstOfMonth)
  {
    final int nWeekDay = getIsoDayOfWeek (nFirstOfMonth) - 1;
    return 1 + (3 - nWeekDay + 7) % 7 - 3;
  }

  /**
   * Week within the month the ISO way: weeks run Monday to Sunday and week 1 is
   * the week containing the month's first Thursday; the days before it belong
   * to the last week of the previous month. The days after the month's last
   * full week stay in that month, even when their week's Thursday falls into
   * the next month (format-date-011 expects 2006-01-30 in week 5 of January).
   *
   * @param nYear
   *        Astronomical year.
   * @param nMonth
   *        Month.
   * @param nDay
   *        Day.
   * @return The week number within the month.
   */
  public static int getWeekOfMonth (final long nYear, final int nMonth, final int nDay)
  {
    final long nFirstOfMonth = getEpochDay (nYear, nMonth, 1);
    final int nMondayOfWeek1 = _getMondayOfWeek1 (nFirstOfMonth);
    if (nDay >= nMondayOfWeek1)
      return (nDay - nMondayOfWeek1) / 7 + 1;
    // The last day of the previous month always lies at or after the Monday of
    // that month's week 1
    final long [] aPrev = getCivilFromEpochDay (nFirstOfMonth - 1);
    final int nPrevDay = (int) aPrev[2];
    final int nPrevMonday = _getMondayOfWeek1 (getEpochDay (aPrev[0], (int) aPrev[1], 1));
    return (nPrevDay - nPrevMonday) / 7 + 1;
  }
}
