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
package com.helger.xslt.pkg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xslt.tree.XsltNames;

/**
 * A package version (XSLT 3.0 section 3.5.1): decimal parts plus an optional
 * name-part suffix. Versions compare numerically part by part, a missing part
 * counting as 0 ("2.0" equals "2.0.0"); with equal parts a version without a
 * suffix ranks above one with a suffix, and two suffixes compare lexically.
 *
 * @author Philip Helger
 */
@Immutable
public final class PackageVersion implements Comparable <PackageVersion>
{
  /** The version of a package that declares none. */
  public static final String DEFAULT_VERSION = "1.0";

  private final long [] m_aParts;
  private final String m_sSuffix;

  private PackageVersion (final long @NonNull [] aParts, @NonNull final String sSuffix)
  {
    m_aParts = aParts;
    m_sSuffix = sSuffix;
  }

  private static boolean _isDigits (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    for (int i = 0; i < s.length (); ++i)
      if (s.charAt (i) < '0' || s.charAt (i) > '9')
        return false;
    return true;
  }

  /**
   * @param s
   *        The lexical version.
   * @return The parsed version or <code>null</code> if it is not a valid
   *         PackageVersion.
   */
  @Nullable
  public static PackageVersion parse (@NonNull final String s)
  {
    String sNum = s;
    String sSuffix = "";
    final int nDash = s.indexOf ('-');
    if (nDash >= 0)
    {
      sNum = s.substring (0, nDash);
      sSuffix = s.substring (nDash + 1);
    }
    if (sNum.isEmpty ())
      return null;
    final String [] aTokens = sNum.split ("\\.", -1);
    final long [] aParts = new long [aTokens.length];
    for (int i = 0; i < aTokens.length; ++i)
    {
      if (!_isDigits (aTokens[i]) || aTokens[i].length () > 18)
        return null;
      aParts[i] = Long.parseLong (aTokens[i]);
    }
    if (nDash >= 0)
      for (final String sPiece : sSuffix.split ("-", -1))
        if (!XsltNames.isNCName (sPiece))
          return null;
    return new PackageVersion (aParts, sSuffix);
  }

  /** @return The decimal parts. */
  public long @NonNull [] getParts ()
  {
    return m_aParts.clone ();
  }

  /** @return <code>true</code> if the version has a name-part suffix. */
  public boolean hasSuffix ()
  {
    return !m_sSuffix.isEmpty ();
  }

  /**
   * @param aPrefix
   *        Leading decimal parts.
   * @return <code>true</code> if this version starts with these parts.
   */
  public boolean startsWith (final long @NonNull [] aPrefix)
  {
    if (m_aParts.length < aPrefix.length)
      return false;
    for (int i = 0; i < aPrefix.length; ++i)
      if (m_aParts[i] != aPrefix[i])
        return false;
    return true;
  }

  public int compareTo (@NonNull final PackageVersion aOther)
  {
    final int n = Math.max (m_aParts.length, aOther.m_aParts.length);
    for (int i = 0; i < n; ++i)
    {
      final long a = i < m_aParts.length ? m_aParts[i] : 0;
      final long b = i < aOther.m_aParts.length ? aOther.m_aParts[i] : 0;
      if (a != b)
        return a < b ? -1 : 1;
    }
    if (m_sSuffix.equals (aOther.m_sSuffix))
      return 0;
    if (m_sSuffix.isEmpty ())
      return 1;
    if (aOther.m_sSuffix.isEmpty ())
      return -1;
    return m_sSuffix.compareTo (aOther.m_sSuffix) < 0 ? -1 : 1;
  }

  @Override
  public boolean equals (final Object o)
  {
    return o instanceof final PackageVersion aOther && compareTo (aOther) == 0;
  }

  @Override
  public int hashCode ()
  {
    // Trailing zero parts do not change the version
    int n = m_aParts.length;
    while (n > 0 && m_aParts[n - 1] == 0)
      n--;
    int ret = m_sSuffix.hashCode ();
    for (int i = 0; i < n; ++i)
      ret = ret * 31 + Long.hashCode (m_aParts[i]);
    return ret;
  }

  @Override
  public String toString ()
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < m_aParts.length; ++i)
    {
      if (i > 0)
        aSB.append ('.');
      aSB.append (m_aParts[i]);
    }
    if (!m_sSuffix.isEmpty ())
      aSB.append ('-').append (m_sSuffix);
    return aSB.toString ();
  }
}
