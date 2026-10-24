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
import java.time.OffsetDateTime;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmDurationValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: fn:position, fn:last, fn:current-dateTime, fn:current-date, fn:current-time, fn:implicit-timezone, fn:default-collation, fn:default-language, fn:static-base-uri.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnContext
{
  /** The value of <code>fn:default-language()</code>. */
  public static final String DEFAULT_LANGUAGE = "en";

  @PresentForCodeCoverage
  private static final FnContext INSTANCE = new FnContext ();

  private FnContext ()
  {}

  private static void _requireFocus (@NonNull final DynamicContext aCtx)
  {
    if (aCtx.getContextItem () == null)
      throw XPathException.noContext ("The focus is absent");
  }

  private static void _appendPadded (@NonNull final StringBuilder aSB, final int nValue, final int nDigits)
  {
    final String s = Integer.toString (Math.abs (nValue));
    if (nValue < 0)
      aSB.append ('-');
    for (int i = s.length (); i < nDigits; ++i)
      aSB.append ('0');
    aSB.append (s);
  }

  private static void _appendDate (@NonNull final StringBuilder aSB, @NonNull final OffsetDateTime aDT)
  {
    _appendPadded (aSB, aDT.getYear (), 4);
    aSB.append ('-');
    _appendPadded (aSB, aDT.getMonthValue (), 2);
    aSB.append ('-');
    _appendPadded (aSB, aDT.getDayOfMonth (), 2);
  }

  private static void _appendTime (@NonNull final StringBuilder aSB, @NonNull final OffsetDateTime aDT)
  {
    _appendPadded (aSB, aDT.getHour (), 2);
    aSB.append (':');
    _appendPadded (aSB, aDT.getMinute (), 2);
    aSB.append (':');
    _appendPadded (aSB, aDT.getSecond (), 2);
    final int nNanos = aDT.getNano ();
    if (nNanos != 0)
    {
      final StringBuilder aFrac = new StringBuilder ();
      _appendPadded (aFrac, nNanos, 9);
      int nLen = aFrac.length ();
      while (aFrac.charAt (nLen - 1) == '0')
        nLen--;
      aSB.append ('.').append (aFrac, 0, nLen);
    }
  }

  private static void _appendTimezone (@NonNull final StringBuilder aSB, @NonNull final OffsetDateTime aDT)
  {
    final int nMinutes = aDT.getOffset ().getTotalSeconds () / 60;
    if (nMinutes == 0)
    {
      aSB.append ('Z');
      return;
    }
    aSB.append (nMinutes < 0 ? '-' : '+');
    _appendPadded (aSB, Math.abs (nMinutes) / 60, 2);
    aSB.append (':');
    _appendPadded (aSB, Math.abs (nMinutes) % 60, 2);
  }

  /**
   * @param aDT
   *        The date/time.
   * @param eType
   *        xs:dateTime, xs:date or xs:time.
   * @return The lexical form of the value in that type, with timezone.
   */
  @NonNull
  static String getLexical (@NonNull final OffsetDateTime aDT, @NonNull final EAtomicType eType)
  {
    final StringBuilder aSB = new StringBuilder ();
    if (eType != EAtomicType.TIME)
      _appendDate (aSB, aDT);
    if (eType == EAtomicType.DATE_TIME)
      aSB.append ('T');
    if (eType != EAtomicType.DATE)
      _appendTime (aSB, aDT);
    _appendTimezone (aSB, aDT);
    return aSB.toString ();
  }

  /**
   * @param aCtx
   *        The context with the current date/time of the run.
   * @param eType
   *        xs:dateTimeStamp, xs:date or xs:time.
   * @return The current date/time as a value of that type.
   */
  @NonNull
  private static XdmSequence _current (@NonNull final DynamicContext aCtx, @NonNull final EAtomicType eType)
  {
    final EAtomicType eLexicalType = eType == EAtomicType.DATE_TIME_STAMP ? EAtomicType.DATE_TIME : eType;
    final String sLexical = getLexical (aCtx.getCurrentDateTime (), eLexicalType);
    return XdmSequence.of (XdmAtomic.of (eType, XdmDateTimeValue.parse (eType, sLexical)));
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "position", "() as xs:integer", (aCtx, aArgs) -> {
      _requireFocus (aCtx);
      return XdmSequence.of (XdmAtomic.integer (aCtx.getPosition ()));
    }, true);
    aLib.register (sNS, "last", "() as xs:integer", (aCtx, aArgs) -> {
      _requireFocus (aCtx);
      return XdmSequence.of (XdmAtomic.integer (aCtx.getSize ()));
    }, true);
    aLib.register (sNS,
                   "current-dateTime",
                   "() as xs:dateTimeStamp",
                   (aCtx, aArgs) -> _current (aCtx, EAtomicType.DATE_TIME_STAMP));
    aLib.register (sNS, "current-date", "() as xs:date", (aCtx, aArgs) -> _current (aCtx, EAtomicType.DATE));
    aLib.register (sNS, "current-time", "() as xs:time", (aCtx, aArgs) -> _current (aCtx, EAtomicType.TIME));
    aLib.register (sNS,
                   "implicit-timezone",
                   "() as xs:dayTimeDuration",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.of (EAtomicType.DAY_TIME_DURATION,
                                                                  XdmDurationValue.of (0,
                                                                                       BigDecimal.valueOf (aCtx.getImplicitTimezoneMinutes () *
                                                                                                           60L)))));
    aLib.register (sNS,
                   "default-collation",
                   "() as xs:string",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.string (CollationRegistry.CODEPOINT_URI)));
    aLib.register (sNS,
                   "default-language",
                   "() as xs:language",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.of (EAtomicType.LANGUAGE, DEFAULT_LANGUAGE)));
    aLib.register (sNS, "static-base-uri", "() as xs:anyURI?", (aCtx, aArgs) -> {
      final String sBaseUri = aCtx.getStaticContext ().getBaseUri ();
      return sBaseUri == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (sBaseUri));
    });
  }
}
