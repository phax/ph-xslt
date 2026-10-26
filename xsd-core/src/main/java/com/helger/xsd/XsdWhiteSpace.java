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
import com.helger.xpath.ops.XsdLexical;
import com.helger.xpath.value.EAtomicType;

/**
 * The whiteSpace facet: modes, ranks and normalisation (from go-xslt
 * <code>simpletype.go</code>).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdWhiteSpace
{
  static final String PRESERVE = "preserve";
  static final String REPLACE = "replace";
  static final String COLLAPSE = "collapse";

  @PresentForCodeCoverage
  private static final XsdWhiteSpace INSTANCE = new XsdWhiteSpace ();

  private XsdWhiteSpace ()
  {}

  /**
   * @param ePrim
   *        The primitive.
   * @param eVariety
   *        The variety.
   * @return The built-in whiteSpace of a primitive and variety.
   */
  @NonNull
  static String primitiveWhiteSpace (@NonNull final EAtomicType ePrim, @NonNull final EXsdVariety eVariety)
  {
    if (eVariety == EXsdVariety.LIST)
      return COLLAPSE;
    if (eVariety == EXsdVariety.UNION)
      return PRESERVE;
    switch (ePrim)
    {
      case STRING:
      case UNTYPED_ATOMIC:
      case ANY_ATOMIC_TYPE:
        return PRESERVE;
      case NORMALIZED_STRING:
        return REPLACE;
      default:
        return COLLAPSE;
    }
  }

  /**
   * @param sMode
   *        A whiteSpace value.
   * @return 0 for preserve (and unknown values), 1 for replace, 2 for
   *         collapse.
   */
  static int rank (@NonNull final String sMode)
  {
    switch (sMode)
    {
      case REPLACE:
        return 1;
      case COLLAPSE:
        return 2;
      default:
        return 0;
    }
  }

  @NonNull
  static String stronger (@NonNull final String sA, @NonNull final String sB)
  {
    return rank (sB) > rank (sA) ? sB : sA;
  }

  /**
   * @param sMode
   *        <code>replace</code>, <code>collapse</code>, anything else
   *        preserves.
   * @param s
   *        The value.
   * @return The normalised value.
   */
  @NonNull
  static String apply (@NonNull final String sMode, @NonNull final String s)
  {
    switch (sMode)
    {
      case REPLACE:
        return XsdLexical.replaceWhitespace (s);
      case COLLAPSE:
        return XsdLexical.collapseWhitespace (s);
      default:
        return s;
    }
  }

  /**
   * @param s
   *        The value.
   * @return The value with whiteSpace="collapse" applied.
   */
  @NonNull
  static String collapse (@NonNull final String s)
  {
    return XsdLexical.collapseWhitespace (s);
  }
}
