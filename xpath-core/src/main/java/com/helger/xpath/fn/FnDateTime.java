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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.DateTimeIetfParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmDurationValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: the date/time component, adjust and timezone functions of F&amp;O section 9,
 * fn:dateTime and fn:parse-ietf-date.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnDateTime
{
  private static final BigDecimal SIXTY = BigDecimal.valueOf (60);
  private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf (3600);
  private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf (86400);

  @PresentForCodeCoverage
  private static final FnDateTime INSTANCE = new FnDateTime ();

  private FnDateTime ()
  {}

  @Nullable
  private static XdmAtomic _arg (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    if (nIndex >= aArgs.size ())
      return null;
    final XdmSequence aSeq = aArgs.get (nIndex);
    return aSeq.isEmpty () ? null : (XdmAtomic) aSeq.getFirst ();
  }

  @NonNull
  private static XdmSequence _integer (final long n)
  {
    return XdmSequence.of (XdmAtomic.integer (n));
  }

  @NonNull
  private static XdmSequence _timezone (@NonNull final XdmDateTimeValue aValue)
  {
    if (!aValue.hasTimezone ())
      return XdmSequence.EMPTY;
    return XdmSequence.of (XdmAtomic.of (EAtomicType.DAY_TIME_DURATION,
                                         XdmDurationValue.of (0,
                                                              BigDecimal.valueOf (aValue.getTimezoneMinutes () *
                                                                                  60L))));
  }

  @NonNull
  private static IXPathFunction _dateTimeComponent (@NonNull final Function <XdmDateTimeValue, XdmSequence> aExtractor)
  {
    return (aCtx, aArgs) -> {
      final XdmAtomic aValue = _arg (aArgs, 0);
      if (aValue == null)
        return XdmSequence.EMPTY;
      return aExtractor.apply ((XdmDateTimeValue) aValue.getValue ());
    };
  }

  @NonNull
  private static IXPathFunction _durationComponent (@NonNull final Function <XdmDurationValue, XdmSequence> aExtractor)
  {
    return (aCtx, aArgs) -> {
      final XdmAtomic aValue = _arg (aArgs, 0);
      if (aValue == null)
        return XdmSequence.EMPTY;
      return aExtractor.apply ((XdmDurationValue) aValue.getValue ());
    };
  }

  @NonNull
  private static XdmSequence _durationPart (@NonNull final XdmDurationValue aDur,
                                            @NonNull final BigDecimal aUnit,
                                            final int nModulo)
  {
    // Truncation towards zero keeps the sign of the duration
    final BigInteger aWhole = aDur.getSeconds ().divideToIntegralValue (aUnit).toBigInteger ();
    final BigInteger aPart = nModulo > 0 ? aWhole.remainder (BigInteger.valueOf (nModulo)) : aWhole;
    return XdmSequence.of (XdmAtomic.integer (aPart));
  }

  @NonNull
  private static XdmSequence _decimal (@NonNull final BigDecimal aValue)
  {
    BigDecimal aRes = aValue.signum () == 0 ? BigDecimal.ZERO : aValue.stripTrailingZeros ();
    if (aRes.scale () < 0)
      aRes = aRes.setScale (0);
    return XdmSequence.of (XdmAtomic.decimal (aRes));
  }

  // Must match IXPathFunction signature
  @NonNull
  private static XdmSequence _dateTime (@SuppressWarnings ("unused") @NonNull final DynamicContext aCtx,
                                        @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic aDate = _arg (aArgs, 0);
    final XdmAtomic aTime = _arg (aArgs, 1);
    if (aDate == null || aTime == null)
      return XdmSequence.EMPTY;

    final XdmDateTimeValue aD = (XdmDateTimeValue) aDate.getValue ();
    final XdmDateTimeValue aT = (XdmDateTimeValue) aTime.getValue ();
    if (aD.hasTimezone () && aT.hasTimezone () && aD.getTimezoneMinutes () != aT.getTimezoneMinutes ())
      throw new XPathException ("FORG0008", "The arguments of fn:dateTime have different timezones");

    final Integer aTz = aD.hasTimezone () ? aD.getTimezone () : aT.getTimezone ();
    return XdmSequence.of (XdmAtomic.of (EAtomicType.DATE_TIME,
                                         XdmDateTimeValue.of (aD.getYear (),
                                                              aD.getMonth (),
                                                              aD.getDay (),
                                                              aT.getHour (),
                                                              aT.getMinute (),
                                                              aT.getSecond (),
                                                              aTz)));
  }

  /**
   * Adjust a value to a timezone (F&amp;O 9.7).
   *
   * @param aValue
   *        The value.
   * @param bHasTarget
   *        <code>false</code> to remove the timezone.
   * @param nTargetMinutes
   *        The target timezone.
   * @return The adjusted value, local components possibly shifted.
   */
  @NonNull
  private static XdmDateTimeValue _adjust (@NonNull final XdmDateTimeValue aValue,
                                           final boolean bHasTarget,
                                           final int nTargetMinutes)
  {
    if (!bHasTarget)
      return aValue.withTimezone (null);
    final Integer aTarget = Integer.valueOf (nTargetMinutes);
    if (!aValue.hasTimezone ())
      return aValue.withTimezone (aTarget);
    return XdmDateTimeValue.ofEpochSeconds (aValue.getEpochSeconds (0), aTarget);
  }

  @NonNull
  private static IXPathFunction _adjustFunction (@NonNull final EAtomicType eType)
  {
    return (aCtx, aArgs) -> {
      final XdmAtomic aArg = _arg (aArgs, 0);
      if (aArg == null)
        return XdmSequence.EMPTY;
      boolean bHasTarget = true;
      int nTarget = aCtx.getImplicitTimezoneMinutes ();
      if (aArgs.size () > 1)
      {
        final XdmAtomic aTz = _arg (aArgs, 1);
        if (aTz == null)
          bHasTarget = false;
        else
        {
          final XdmDurationValue aDur = (XdmDurationValue) aTz.getValue ();
          final BigDecimal aSecs = aDur.getSeconds ();
          if (aDur.getMonths () != 0 ||
              aSecs.abs ().compareTo (BigDecimal.valueOf (XdmDateTimeValue.MAX_TIMEZONE_MINUTES * 60L)) > 0 ||
              aSecs.remainder (SIXTY).signum () != 0)
            throw new XPathException ("FODT0003",
                                      "Invalid timezone value " + aDur.toLexical (EAtomicType.DAY_TIME_DURATION));
          nTarget = aSecs.divide (SIXTY).intValueExact ();
        }
      }
      final XdmDateTimeValue aValue = (XdmDateTimeValue) aArg.getValue ();
      XdmDateTimeValue aRes = _adjust (aValue, bHasTarget, nTarget);
      if (eType == EAtomicType.DATE)
        aRes = XdmDateTimeValue.of (aRes.getYear (),
                                    aRes.getMonth (),
                                    aRes.getDay (),
                                    0,
                                    0,
                                    BigDecimal.ZERO,
                                    aRes.getTimezone ());
      else
        if (eType == EAtomicType.TIME)
          aRes = XdmDateTimeValue.of (XdmDateTimeValue.REFERENCE_YEAR,
                                      12,
                                      31,
                                      aRes.getHour (),
                                      aRes.getMinute (),
                                      aRes.getSecond (),
                                      aRes.getTimezone ());
      return XdmSequence.of (XdmAtomic.of (eType, aRes));
    };
  }

  @NonNull
  private static XdmSequence _parseIetfDate (@NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic aArg = _arg (aArgs, 0);
    if (aArg == null)
      return XdmSequence.EMPTY;
    return XdmSequence.of (XdmAtomic.of (EAtomicType.DATE_TIME, DateTimeIetfParser.parse (aArg.getString ())));
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "dateTime", "(xs:date?, xs:time?) as xs:dateTime?", FnDateTime::_dateTime);

    aLib.register (sNS,
                   "years-from-duration",
                   "(xs:duration?) as xs:integer?",
                   _durationComponent (d -> _integer (d.getMonths () / 12)));
    aLib.register (sNS,
                   "months-from-duration",
                   "(xs:duration?) as xs:integer?",
                   _durationComponent (d -> _integer (d.getMonths () % 12)));
    aLib.register (sNS,
                   "days-from-duration",
                   "(xs:duration?) as xs:integer?",
                   _durationComponent (d -> _durationPart (d, SECONDS_PER_DAY, 0)));
    aLib.register (sNS,
                   "hours-from-duration",
                   "(xs:duration?) as xs:integer?",
                   _durationComponent (d -> _durationPart (d, SECONDS_PER_HOUR, 24)));
    aLib.register (sNS,
                   "minutes-from-duration",
                   "(xs:duration?) as xs:integer?",
                   _durationComponent (d -> _durationPart (d, SIXTY, 60)));
    aLib.register (sNS,
                   "seconds-from-duration",
                   "(xs:duration?) as xs:decimal?",
                   _durationComponent (d -> _decimal (d.getSeconds ().remainder (SIXTY))));

    aLib.register (sNS,
                   "year-from-dateTime",
                   "(xs:dateTime?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getYear ())));
    aLib.register (sNS,
                   "month-from-dateTime",
                   "(xs:dateTime?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getMonth ())));
    aLib.register (sNS,
                   "day-from-dateTime",
                   "(xs:dateTime?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getDay ())));
    aLib.register (sNS,
                   "hours-from-dateTime",
                   "(xs:dateTime?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getHour ())));
    aLib.register (sNS,
                   "minutes-from-dateTime",
                   "(xs:dateTime?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getMinute ())));
    aLib.register (sNS,
                   "seconds-from-dateTime",
                   "(xs:dateTime?) as xs:decimal?",
                   _dateTimeComponent (v -> _decimal (v.getSecond ())));
    aLib.register (sNS,
                   "timezone-from-dateTime",
                   "(xs:dateTime?) as xs:dayTimeDuration?",
                   _dateTimeComponent (FnDateTime::_timezone));

    aLib.register (sNS,
                   "year-from-date",
                   "(xs:date?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getYear ())));
    aLib.register (sNS,
                   "month-from-date",
                   "(xs:date?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getMonth ())));
    aLib.register (sNS, "day-from-date", "(xs:date?) as xs:integer?", _dateTimeComponent (v -> _integer (v.getDay ())));
    aLib.register (sNS,
                   "timezone-from-date",
                   "(xs:date?) as xs:dayTimeDuration?",
                   _dateTimeComponent (FnDateTime::_timezone));

    aLib.register (sNS,
                   "hours-from-time",
                   "(xs:time?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getHour ())));
    aLib.register (sNS,
                   "minutes-from-time",
                   "(xs:time?) as xs:integer?",
                   _dateTimeComponent (v -> _integer (v.getMinute ())));
    aLib.register (sNS,
                   "seconds-from-time",
                   "(xs:time?) as xs:decimal?",
                   _dateTimeComponent (v -> _decimal (v.getSecond ())));
    aLib.register (sNS,
                   "timezone-from-time",
                   "(xs:time?) as xs:dayTimeDuration?",
                   _dateTimeComponent (FnDateTime::_timezone));

    aLib.register (sNS,
                   "adjust-dateTime-to-timezone",
                   "(xs:dateTime?) as xs:dateTime?",
                   _adjustFunction (EAtomicType.DATE_TIME));
    aLib.register (sNS,
                   "adjust-dateTime-to-timezone",
                   "(xs:dateTime?, xs:dayTimeDuration?) as xs:dateTime?",
                   _adjustFunction (EAtomicType.DATE_TIME));
    aLib.register (sNS, "adjust-date-to-timezone", "(xs:date?) as xs:date?", _adjustFunction (EAtomicType.DATE));
    aLib.register (sNS,
                   "adjust-date-to-timezone",
                   "(xs:date?, xs:dayTimeDuration?) as xs:date?",
                   _adjustFunction (EAtomicType.DATE));
    aLib.register (sNS, "adjust-time-to-timezone", "(xs:time?) as xs:time?", _adjustFunction (EAtomicType.TIME));
    aLib.register (sNS,
                   "adjust-time-to-timezone",
                   "(xs:time?, xs:dayTimeDuration?) as xs:time?",
                   _adjustFunction (EAtomicType.TIME));

    aLib.register (sNS, "parse-ietf-date", "(xs:string?) as xs:dateTime?", FnDateTime::_parseIetfDate);
  }
}
