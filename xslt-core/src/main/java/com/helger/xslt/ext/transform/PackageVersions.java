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
package com.helger.xslt.ext.transform;

import java.math.BigInteger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Package versions and version ranges (XSLT 3.0 section 3.5.1), as used by the
 * <code>package-version</code> option of fn:transform: a version is a dotted
 * sequence of integers with an optional <code>-name</code> suffix; a range is
 * <code>*</code>, a version, <code>V.*</code>, <code>V+</code>,
 * <code>to V</code> or <code>V to W</code> (where W may be a prefix
 * <code>W.*</code>), and several ranges are separated by commas.
 *
 * @author Philip Helger
 */
@Immutable
final class PackageVersions
{
  /** A parsed version: numeric parts and the optional name part. */
  record Version (@NonNull ICommonsList <BigInteger> parts, @Nullable String name)
  {}

  private record Range (@Nullable Version from, @Nullable Version to, boolean toIsPrefix, boolean prefix)
  {}

  @PresentForCodeCoverage
  private static final PackageVersions INSTANCE = new PackageVersions ();

  private PackageVersions ()
  {}

  /**
   * @param sVersion
   *        Version text.
   * @return The parsed version or <code>null</code> if it is not a valid
   *         package version.
   */
  @Nullable
  static Version parse (@NonNull final String sVersion)
  {
    String sNumeric = sVersion.trim ();
    String sName = null;
    final int nDash = sNumeric.indexOf ('-');
    if (nDash >= 0)
    {
      sName = sNumeric.substring (nDash + 1);
      sNumeric = sNumeric.substring (0, nDash);
      if (sName.isEmpty ())
        return null;
    }
    if (sNumeric.isEmpty ())
      return null;
    final ICommonsList <BigInteger> aParts = new CommonsArrayList <> ();
    for (final String sPart : sNumeric.split ("\\.", -1))
    {
      if (sPart.isEmpty ())
        return null;
      for (int i = 0; i < sPart.length (); ++i)
        if (sPart.charAt (i) < '0' || sPart.charAt (i) > '9')
          return null;
      aParts.add (new BigInteger (sPart));
    }
    return new Version (aParts, sName);
  }

  @NonNull
  private static BigInteger _part (@NonNull final Version aVersion, final int nIndex)
  {
    return nIndex < aVersion.parts ().size () ? aVersion.parts ().get (nIndex) : BigInteger.ZERO;
  }

  /**
   * Compare two versions: numeric parts first (missing parts count as zero),
   * then a version with a name part comes before the same version without one.
   */
  private static int _compare (@NonNull final Version aA, @NonNull final Version aB)
  {
    final int nMax = Math.max (aA.parts ().size (), aB.parts ().size ());
    for (int i = 0; i < nMax; ++i)
    {
      final int n = _part (aA, i).compareTo (_part (aB, i));
      if (n != 0)
        return n;
    }
    if (aA.name () == null)
      return aB.name () == null ? 0 : 1;
    if (aB.name () == null)
      return -1;
    return aA.name ().compareTo (aB.name ());
  }

  private static boolean _hasPrefix (@NonNull final Version aVersion, @NonNull final Version aPrefix)
  {
    if (aVersion.parts ().size () < aPrefix.parts ().size ())
      return false;
    for (int i = 0; i < aPrefix.parts ().size (); ++i)
      if (!aVersion.parts ().get (i).equals (aPrefix.parts ().get (i)))
        return false;
    return true;
  }

  @Nullable
  private static Range _parseRange (@NonNull final String sRange)
  {
    final String s = sRange.trim ();
    if ("*".equals (s))
      return new Range (null, null, false, false);
    final String [] aWords = s.split ("\\s+");
    if (aWords.length == 2 && "to".equals (aWords[0]) || aWords.length == 3 && "to".equals (aWords[1]))
    {
      Version aFrom = null;
      if (aWords.length == 3)
      {
        aFrom = parse (aWords[0]);
        if (aFrom == null)
          return null;
      }
      String sTo = aWords[aWords.length - 1];
      final boolean bToPrefix = sTo.endsWith (".*");
      if (bToPrefix)
        sTo = sTo.substring (0, sTo.length () - 2);
      final Version aTo = parse (sTo);
      return aTo == null ? null : new Range (aFrom, aTo, bToPrefix, false);
    }
    if (aWords.length != 1)
      return null;
    if (s.endsWith (".*"))
    {
      final Version aPrefix = parse (s.substring (0, s.length () - 2));
      return aPrefix == null ? null : new Range (aPrefix, null, false, true);
    }
    if (s.endsWith ("+"))
    {
      final Version aFrom = parse (s.substring (0, s.length () - 1));
      return aFrom == null ? null : new Range (aFrom, null, false, false);
    }
    final Version aExact = parse (s);
    return aExact == null ? null : new Range (aExact, aExact, false, false);
  }

  private static boolean _matches (@NonNull final Range aRange, @NonNull final Version aVersion)
  {
    if (aRange.prefix ())
      return _hasPrefix (aVersion, aRange.from ());
    if (aRange.from () != null && _compare (aVersion, aRange.from ()) < 0)
      return false;
    if (aRange.to () != null)
    {
      if (aRange.toIsPrefix ())
        return _hasPrefix (aVersion, aRange.to ()) || _compare (aVersion, aRange.to ()) <= 0;
      return _compare (aVersion, aRange.to ()) <= 0;
    }
    return true;
  }

  /**
   * @param sRanges
   *        Comma separated version ranges.
   * @param aVersion
   *        The version of a package.
   * @return <code>true</code> if the version is in one of the ranges.
   *         <code>false</code> also for an invalid range.
   */
  static boolean matches (@NonNull final String sRanges, @NonNull final Version aVersion)
  {
    for (final String sRange : sRanges.split (","))
    {
      final Range aRange = _parseRange (sRange);
      if (aRange != null && _matches (aRange, aVersion))
        return true;
    }
    return false;
  }

  /**
   * @param aA
   *        First version.
   * @param aB
   *        Second version.
   * @return <code>true</code> if the first version is higher.
   */
  static boolean isHigher (@NonNull final Version aA, @NonNull final Version aB)
  {
    return _compare (aA, aB) > 0;
  }
}
