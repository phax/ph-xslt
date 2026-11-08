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
package com.helger.phoxtrot.xsd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.AtomicCaster;
import com.helger.phoxtrot.xpath.ops.AtomicComparator;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmDateTimeValue;
import com.helger.phoxtrot.xpath.value.XdmDurationValue;

/**
 * Casting and ordering of atomic values for the facet checks (go-xslt
 * <code>xpath.CastTo</code> and <code>xpath.CompareAtomic</code> as the XSD
 * code uses them). Values without timezone compare with the implicit timezone
 * UTC, so results never depend on the machine.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdValueCompare
{
  /**
   * The four reference dateTimes (all at 00:00:00Z) of the duration order
   * (Datatypes 1.1 3.3.6.1, 1.0 3.2.6.2).
   */
  private static final LocalDate [] DURATION_REFERENCES = { LocalDate.of (1696, 9, 1),
                                                            LocalDate.of (1697, 2, 1),
                                                            LocalDate.of (1903, 3, 1),
                                                            LocalDate.of (1903, 7, 1) };
  /** Months of a Gregorian 400-year cycle. */
  private static final long CYCLE_MONTHS = 4800;
  /** Seconds of a Gregorian 400-year cycle (146097 days). */
  private static final BigDecimal CYCLE_SECONDS = BigDecimal.valueOf (146097L * 86400L);
  private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf (86400L);

  @PresentForCodeCoverage
  private static final XsdValueCompare INSTANCE = new XsdValueCompare ();

  private XsdValueCompare ()
  {}

  /**
   * @param aValue
   *        An atomic value.
   * @return <code>true</code> if it is an xs:double or xs:float NaN.
   */
  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return (aPayload instanceof final Double aD && aD.isNaN ()) || (aPayload instanceof final Float aF && aF.isNaN ());
  }

  /**
   * @param eType
   *        An atomic type.
   * @return <code>true</code> for the five Gregorian types xs:gYear,
   *         xs:gYearMonth, xs:gMonth, xs:gMonthDay and xs:gDay.
   */
  private static boolean _isGType (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.G_YEAR ||
           eType == EAtomicType.G_YEAR_MONTH ||
           eType == EAtomicType.G_MONTH ||
           eType == EAtomicType.G_MONTH_DAY ||
           eType == EAtomicType.G_DAY;
  }

  /**
   * @param sLexical
   *        The lexical form (whitespace already normalised).
   * @param eTarget
   *        The target type.
   * @return The cast value, or <code>null</code> if the cast fails.
   */
  @Nullable
  static XdmAtomic castOrNull (@NonNull final String sLexical, @NonNull final EAtomicType eTarget)
  {
    // XSD 1.1 xs:anyURI accepts every string (even ":a" and "%"); the stricter
    // check of the XPath cast does not apply, and 1.0's RFC 2396 rule is checked
    // where it applies (XsdLexHelper.validAnyURI10)
    if (eTarget == EAtomicType.ANY_URI)
      return XdmAtomic.anyUri (XsdWhiteSpace.collapse (sLexical));
    try
    {
      return AtomicCaster.castFromString (sLexical, eTarget, null);
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  /**
   * The seconds from a reference date to the reference date plus a duration
   * (Datatypes Appendix E: the months first, then the seconds). Whole
   * 400-year cycles of the months are counted as 146097 days each, so no
   * duration overflows the date arithmetic. The reference dates are first days
   * of a month, so no day is pinned.
   *
   * @param aRef
   *        The reference date (00:00:00Z).
   * @param aDuration
   *        The duration to add.
   * @return The difference in seconds.
   */
  @NonNull
  private static BigDecimal _secondsAfter (@NonNull final LocalDate aRef, @NonNull final XdmDurationValue aDuration)
  {
    final long nCycles = Math.floorDiv (aDuration.getMonths (), CYCLE_MONTHS);
    final long nRest = Math.floorMod (aDuration.getMonths (), CYCLE_MONTHS);
    final long nDays = ChronoUnit.DAYS.between (aRef, aRef.plusMonths (nRest));
    return CYCLE_SECONDS.multiply (BigDecimal.valueOf (nCycles))
                        .add (SECONDS_PER_DAY.multiply (BigDecimal.valueOf (nDays)))
                        .add (aDuration.getSeconds ());
  }

  /**
   * Order two durations by the partial order of Datatypes 1.1 3.3.6.1 (1.0
   * 3.2.6.2): both are added to each of the four reference dateTimes; if all
   * four pairs are ordered the same way, so are the durations, otherwise they
   * are incomparable (e.g. P1M and P30D).
   *
   * @param aA
   *        Left duration.
   * @param aB
   *        Right duration.
   * @return negative, 0 or positive; <code>null</code> if incomparable.
   */
  @Nullable
  private static Integer _compareDurations (@NonNull final XdmDurationValue aA, @NonNull final XdmDurationValue aB)
  {
    Integer ret = null;
    for (final LocalDate aRef : DURATION_REFERENCES)
    {
      final int c = _secondsAfter (aRef, aA).compareTo (_secondsAfter (aRef, aB));
      if (ret == null)
        ret = Integer.valueOf (Integer.signum (c));
      else
        if (ret.intValue () != Integer.signum (c))
          return null;
    }
    return ret;
  }

  /**
   * Order two atomic values (go-xslt <code>CompareAtomic</code>): durations
   * follow the partial order of the XSD datatypes (go-xslt folds months to
   * seconds, a total order the specification does not have); NaN and
   * incomparable values have no order.
   *
   * @param aA
   *        Left value.
   * @param aB
   *        Right value.
   * @return negative, 0 or positive; <code>null</code> if not comparable.
   */
  @Nullable
  static Integer compare (@NonNull final XdmAtomic aA, @NonNull final XdmAtomic aB)
  {
    if (aA.getValue () instanceof final XdmDurationValue aDA && aB.getValue () instanceof final XdmDurationValue aDB)
      return _compareDurations (aDA, aDB);
    // The g* types have no order in XPath; XSD orders them on their reference
    // dates (go-xslt compares the time line value of every date/time type)
    if (aA.getType () == aB.getType () &&
        aA.getType ().isDateTimeFamily () &&
        _isGType (aA.getType ()) &&
        aA.getValue () instanceof final XdmDateTimeValue aTA &&
        aB.getValue () instanceof final XdmDateTimeValue aTB)
      return Integer.valueOf (Integer.signum (aTA.compareTo (aTB, aA.getType (), 0)));
    if (_isNaN (aA) || _isNaN (aB))
      return null;
    try
    {
      final DynamicContext aCtx = new DynamicContext (new StaticContext ());
      aCtx.setImplicitTimezoneMinutes (0);
      return Integer.valueOf (AtomicComparator.compareForOrder (aA, aB, null, aCtx));
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  /**
   * Cast two lexical forms (each collapsed) to a type and order them.
   *
   * @return negative, 0 or positive; <code>null</code> if a cast fails or the
   *         values are not comparable.
   */
  @Nullable
  static Integer compareLexical (@NonNull final String sA, @NonNull final String sB, @NonNull final EAtomicType eType)
  {
    final XdmAtomic aA = castOrNull (XsdWhiteSpace.collapse (sA), eType);
    final XdmAtomic aB = castOrNull (XsdWhiteSpace.collapse (sB), eType);
    if (aA == null || aB == null)
      return null;
    return compare (aA, aB);
  }
}
