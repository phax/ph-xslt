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
package com.helger.xpath.collation;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A collation as F&amp;O 3.1 section 5.3 defines it. Besides comparison and
 * collation keys it supports the substring matching of F&amp;O 3.1 section
 * 5.3.1 (fn:contains, fn:starts-with, fn:ends-with, fn:substring-before,
 * fn:substring-after): the default implementations match on collation units
 * by comparing candidate substrings, so characters the collation ignores are
 * neither part of a match nor an obstacle to it.
 *
 * @author Philip Helger
 */
public interface ICollation
{
  /** @return The collation URI. */
  @NonNull
  String getUri ();

  /**
   * @param sLeft
   *        First string.
   * @param sRight
   *        Second string.
   * @return Negative, 0 or positive as for {@link java.util.Comparator}.
   */
  int compare (@NonNull String sLeft, @NonNull String sRight);

  /**
   * @param sValue
   *        The string.
   * @return A key whose equals/hashCode agree with
   *         {@link #compare(String, String)} == 0.
   */
  @NonNull
  Object getCollationKey (@NonNull String sValue);

  /**
   * @param sValue
   *        The string.
   * @return The bytes of the collation key for fn:collation-key: two keys are
   *         equal iff the strings compare equal, and the unsigned byte order of
   *         keys is the collation order. The default implementation is
   *         correct for collations that compare by code point only.
   */
  @NonNull
  default byte [] getCollationKeyBytes (@NonNull final String sValue)
  {
    return CollationSupport.getCodepointBytes (sValue);
  }

  /**
   * Find the first match of a substring under this collation.
   *
   * @param sValue
   *        The string to search in.
   * @param sSearch
   *        The string to search for.
   * @return <code>null</code> if there is no match, otherwise the start and
   *         end index (UTF-16 offsets into <code>sValue</code>) of the
   *         shortest match at the leftmost position.
   */
  @Nullable
  default int [] findSubstring (@NonNull final String sValue, @NonNull final String sSearch)
  {
    return CollationSupport.findSubstring (this, sValue, sSearch, false);
  }

  /**
   * @param sValue
   *        The string to check.
   * @param sPrefix
   *        The prefix.
   * @return <code>true</code> if some prefix of the value compares equal to
   *         the passed prefix.
   */
  default boolean startsWith (@NonNull final String sValue, @NonNull final String sPrefix)
  {
    return CollationSupport.startsWith (this, sValue, sPrefix, false);
  }

  /**
   * @param sValue
   *        The string to check.
   * @param sSuffix
   *        The suffix.
   * @return <code>true</code> if some suffix of the value compares equal to
   *         the passed suffix.
   */
  default boolean endsWith (@NonNull final String sValue, @NonNull final String sSuffix)
  {
    return CollationSupport.endsWith (this, sValue, sSuffix, false);
  }
}
