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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Bounds of the walks over content models (phase V4). go-xslt recurses over
 * the particle tree on a growable stack; Java does not have one, so every
 * recursive walk over a content model counts its depth and gives up with
 * {@link XsdUnsupportedException} beyond {@link #MAX_DEPTH}. No real schema
 * nests particles that deeply.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdModelLimits
{
  /** Maximum nesting of particles in one recursive walk. */
  static final int MAX_DEPTH = 250;

  /** Saturation value for occurrence arithmetic (far above any clamped int). */
  static final long HUGE = 1L << 60;

  @PresentForCodeCoverage
  private static final XsdModelLimits INSTANCE = new XsdModelLimits ();

  private XsdModelLimits ()
  {}

  /**
   * @return The exception for a content model nested deeper than
   *         {@link #MAX_DEPTH}.
   */
  @NonNull
  static XsdUnsupportedException tooDeep ()
  {
    return new XsdUnsupportedException ("content models are nested deeper than " + MAX_DEPTH + " levels");
  }

  /**
   * Check a recursion depth.
   *
   * @param nDepth
   *        The current depth.
   * @throws XsdUnsupportedException
   *         beyond {@link #MAX_DEPTH}
   */
  static void checkDepth (final int nDepth)
  {
    if (nDepth > MAX_DEPTH)
      throw tooDeep ();
  }

  /**
   * Saturating addition of two non-negative occurrence counts.
   *
   * @param n1
   *        First count.
   * @param n2
   *        Second count.
   * @return The sum, at most {@link #HUGE}.
   */
  static long add (final long n1, final long n2)
  {
    return Math.min (HUGE, n1 + n2);
  }

  /**
   * Saturating multiplication of two non-negative occurrence counts.
   *
   * @param n1
   *        First count.
   * @param n2
   *        Second count.
   * @return The product, at most {@link #HUGE}.
   */
  static long mul (final long n1, final long n2)
  {
    if (n1 == 0 || n2 == 0)
      return 0;
    if (n1 > HUGE / n2)
      return HUGE;
    return Math.min (HUGE, n1 * n2);
  }
}
