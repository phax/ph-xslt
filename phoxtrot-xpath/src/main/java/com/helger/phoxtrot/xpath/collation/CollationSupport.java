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
package com.helger.phoxtrot.xpath.collation;

import java.util.Arrays;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.string.StringHelper;

/**
 * Helpers shared by the collation implementations.
 *
 * @author Philip Helger
 */
@Immutable
final class CollationSupport
{
  /** A collation key as an int array with value semantics. */
  static final class IntKey implements Comparable <IntKey>
  {
    private final int [] m_aKey;

    IntKey (@NonNull final int [] aKey)
    {
      m_aKey = aKey;
    }

    @Override
    public boolean equals (final Object o)
    {
      return o instanceof final IntKey aOther && Arrays.equals (m_aKey, aOther.m_aKey);
    }

    @Override
    public int hashCode ()
    {
      return Arrays.hashCode (m_aKey);
    }

    public int compareTo (@NonNull final IntKey aOther)
    {
      return Arrays.compare (m_aKey, aOther.m_aKey);
    }
  }

  @PresentForCodeCoverage
  private static final CollationSupport INSTANCE = new CollationSupport ();

  private CollationSupport ()
  {}

  /**
   * @param aKey
   *        Key units, each in the range 0..0xFFFFFF.
   * @return The key as bytes, three per unit, big-endian.
   */
  @NonNull
  static byte [] toBytes (@NonNull final int [] aKey)
  {
    final byte [] ret = new byte [aKey.length * 3];
    int n = 0;
    for (final int v : aKey)
    {
      ret[n++] = (byte) (v >>> 16);
      ret[n++] = (byte) (v >>> 8);
      ret[n++] = (byte) v;
    }
    return ret;
  }

  /**
   * @param sValue
   *        The string.
   * @return The collation key bytes of the Unicode codepoint collation: the
   *         code points of the string, three big-endian bytes each, so that
   *         the unsigned byte order is the code point order.
   */
  @NonNull
  static byte [] getCodepointBytes (@NonNull final String sValue)
  {
    return toBytes (StringHelper.getCodePoints (sValue));
  }

  /**
   * @return <code>true</code> if the index lies inside the string and the
   *         UTF-16 char there is a digit according to
   *         {@link Character#isDigit(char)} (any Unicode decimal digit, not
   *         only ASCII). An index outside the string gives <code>false</code>.
   */
  private static boolean _isDigit (@NonNull final String s, final int nIndex)
  {
    return nIndex >= 0 && nIndex < s.length () && Character.isDigit (s.charAt (nIndex));
  }

  /**
   * A numeric collation compares digit runs by value: a match may then not
   * cut a run of digits in two.
   */
  private static boolean _splitsNumber (@NonNull final String s, final int nStart, final int nEnd)
  {
    if (nEnd > nStart && _isDigit (s, nEnd - 1) && _isDigit (s, nEnd))
      return true;
    return nEnd > nStart && _isDigit (s, nStart) && _isDigit (s, nStart - 1);
  }

  /**
   * @return The code point boundaries of the string as ascending UTF-16
   *         offsets: the start of every code point (a surrogate pair counts
   *         once) followed by the string length. The empty string gives
   *         <code>[0]</code>.
   */
  @NonNull
  private static int [] _getBoundaries (@NonNull final String s)
  {
    final int [] ret = new int [s.length () + 1];
    int n = 0;
    int i = 0;
    while (i < s.length ())
    {
      ret[n++] = i;
      i += Character.charCount (s.codePointAt (i));
    }
    ret[n++] = s.length ();
    return Arrays.copyOf (ret, n);
  }

  /**
   * The collation-aware substring search of F&amp;O 3.1 section 5.3.1 by brute
   * force: for every code point boundary from left to right, the candidate
   * substrings starting there are compared with the search string by
   * {@link ICollation#compare(String, String)}, shortest first, and the first
   * equal one is the match. A start position whose first code point alone
   * compares equal to the empty string (an ignorable character) is skipped.
   * A search string that compares equal to the empty string matches the empty
   * substring at offset 0. With <code>bNumeric</code> a candidate that would
   * cut a run of digits in two (see {@link #_splitsNumber(String, int, int)})
   * is rejected. The cost is quadratic in the length of the value in
   * collation comparisons.
   *
   * @param aColl
   *        The collation to compare with.
   * @param sValue
   *        The string to search in.
   * @param sSearch
   *        The string to search for.
   * @param bNumeric
   *        <code>true</code> for a collation with numeric digit ordering.
   * @return <code>null</code> if there is no match, otherwise the start and
   *         end UTF-16 offset of the shortest match at the leftmost position.
   */
  @Nullable
  static int [] findSubstring (@NonNull final ICollation aColl,
                               @NonNull final String sValue,
                               @NonNull final String sSearch,
                               final boolean bNumeric)
  {
    if (aColl.compare ("", sSearch) == 0)
      return new int [] { 0, 0 };
    final int [] aBounds = _getBoundaries (sValue);
    for (int nStartIdx = 0; nStartIdx + 1 < aBounds.length; ++nStartIdx)
    {
      final int nStart = aBounds[nStartIdx];
      // A match never starts with an ignorable character
      if (aColl.compare (sValue.substring (nStart, aBounds[nStartIdx + 1]), "") == 0)
        continue;
      for (int nEndIdx = nStartIdx + 1; nEndIdx < aBounds.length; ++nEndIdx)
        if (aColl.compare (sValue.substring (nStart, aBounds[nEndIdx]), sSearch) == 0 &&
            !(bNumeric && _splitsNumber (sValue, nStart, aBounds[nEndIdx])))
          return new int [] { nStart, aBounds[nEndIdx] };
    }
    return null;
  }

  /**
   * Collation-aware <code>fn:starts-with</code> (F&amp;O 3.1 section 5.3.1):
   * <code>true</code> if the prefix compares equal to the empty string, or if
   * some prefix of the value that ends on a code point boundary compares
   * equal to it under the collation. With <code>bNumeric</code> a prefix that
   * ends inside a run of digits does not count.
   *
   * @param aColl
   *        The collation to compare with.
   * @param sValue
   *        The string to check.
   * @param sPrefix
   *        The prefix.
   * @param bNumeric
   *        <code>true</code> for a collation with numeric digit ordering.
   * @return <code>true</code> if the value starts with the prefix.
   */
  static boolean startsWith (@NonNull final ICollation aColl,
                             @NonNull final String sValue,
                             @NonNull final String sPrefix,
                             final boolean bNumeric)
  {
    if (aColl.compare ("", sPrefix) == 0)
      return true;
    final int [] aBounds = _getBoundaries (sValue);
    for (int i = 1; i < aBounds.length; ++i)
      if (aColl.compare (sValue.substring (0, aBounds[i]), sPrefix) == 0 &&
          !(bNumeric && _splitsNumber (sValue, 0, aBounds[i])))
        return true;
    return false;
  }

  /**
   * Collation-aware <code>fn:ends-with</code> (F&amp;O 3.1 section 5.3.1), the
   * mirror image of {@link #startsWith(ICollation, String, String, boolean)}:
   * the suffixes of the value starting on a code point boundary are tried
   * from the shortest to the longest. With <code>bNumeric</code> a suffix that
   * starts inside a run of digits does not count.
   *
   * @param aColl
   *        The collation to compare with.
   * @param sValue
   *        The string to check.
   * @param sSuffix
   *        The suffix.
   * @param bNumeric
   *        <code>true</code> for a collation with numeric digit ordering.
   * @return <code>true</code> if the value ends with the suffix.
   */
  static boolean endsWith (@NonNull final ICollation aColl,
                           @NonNull final String sValue,
                           @NonNull final String sSuffix,
                           final boolean bNumeric)
  {
    if (aColl.compare ("", sSuffix) == 0)
      return true;
    final int [] aBounds = _getBoundaries (sValue);
    for (int i = aBounds.length - 2; i >= 0; --i)
      if (aColl.compare (sValue.substring (aBounds[i]), sSuffix) == 0 &&
          !(bNumeric && _splitsNumber (sValue, aBounds[i], sValue.length ())))
        return true;
    return false;
  }
}
