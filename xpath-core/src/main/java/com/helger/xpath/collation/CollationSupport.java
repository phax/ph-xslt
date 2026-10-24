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

import java.util.Arrays;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

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

  @NonNull
  static byte [] getCodepointBytes (@NonNull final String sValue)
  {
    return toBytes (sValue.codePoints ().toArray ());
  }

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
