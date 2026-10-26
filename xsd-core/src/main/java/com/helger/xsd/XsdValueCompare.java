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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDurationValue;

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
  /** Seconds per month in go-xslt's folded duration ordering. */
  private static final double SECONDS_PER_MONTH = 2629746;

  @PresentForCodeCoverage
  private static final XsdValueCompare INSTANCE = new XsdValueCompare ();

  private XsdValueCompare ()
  {}

  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return aPayload instanceof final Double aD && aD.isNaN () || aPayload instanceof final Float aF && aF.isNaN ();
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
   * Order two atomic values (go-xslt <code>CompareAtomic</code>): durations
   * fold months to seconds; NaN and incomparable values have no order.
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
    {
      final double dA = aDA.getMonths () * SECONDS_PER_MONTH + aDA.getSeconds ().doubleValue ();
      final double dB = aDB.getMonths () * SECONDS_PER_MONTH + aDB.getSeconds ().doubleValue ();
      if (dA != dB)
        return Integer.valueOf (dA < dB ? -1 : 1);
      if (aDA.getMonths () != aDB.getMonths ())
        return Integer.valueOf (aDA.getMonths () < aDB.getMonths () ? -1 : 1);
      return Integer.valueOf (Integer.signum (aDA.getSeconds ().compareTo (aDB.getSeconds ())));
    }
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
