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

import java.util.Arrays;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xslt.tree.XsltNames;

/**
 * The package-version-ranges of xsl:use-package (XSLT 3.0 section 3.5.2):
 *
 * <pre>
 * PackageVersionRanges ::= PackageVersionRange ("," PackageVersionRange)*
 * PackageVersionRange  ::= "*" | PackageVersion | PackageVersion ".*"
 *                        | PackageVersion "to" (PackageVersion | PackageVersion ".*")
 *                        | "to" (PackageVersion | PackageVersion ".*") | PackageVersion "+"
 * </pre>
 *
 * An upper bound "to V.*" admits every version that is less than or equal to
 * some version matching the prefix V.*.
 *
 * @author Philip Helger
 */
@Immutable
public final class PackageVersionRanges
{
  private record Range (boolean any,
                        long @Nullable [] prefix,
                        @Nullable PackageVersion exact,
                        @Nullable PackageVersion lo,
                        @Nullable PackageVersion hi,
                        boolean hiIsPrefix)
  {
    boolean matches (@NonNull final PackageVersion aVersion)
    {
      if (any)
        return true;
      if (prefix != null)
        return aVersion.startsWith (prefix);
      if (exact != null)
        return aVersion.compareTo (exact) == 0;
      if (lo != null && aVersion.compareTo (lo) < 0)
        return false;
      if (hi == null)
        return true;
      if (hiIsPrefix && aVersion.startsWith (hi.getParts ()))
        return true;
      return aVersion.compareTo (hi) <= 0;
    }
  }

  private final ICommonsList <Range> m_aRanges;

  private PackageVersionRanges (@NonNull final ICommonsList <Range> aRanges)
  {
    m_aRanges = aRanges;
  }

  @Nullable
  private static Range _parseRange (@NonNull final String sToken)
  {
    if (sToken.isEmpty ())
      return null;
    if ("*".equals (sToken))
      return new Range (true, null, null, null, null, false);
    final String [] aFields = XsltNames.tokenize (sToken);
    for (int i = 0; i < aFields.length; ++i)
      if ("to".equals (aFields[i]))
      {
        final String sLo = String.join ("", Arrays.copyOfRange (aFields, 0, i));
        final String sHi = String.join ("", Arrays.copyOfRange (aFields, i + 1, aFields.length));
        PackageVersion aLo = null;
        PackageVersion aHi = null;
        boolean bHiIsPrefix = false;
        if (!sLo.isEmpty ())
        {
          aLo = PackageVersion.parse (sLo);
          if (aLo == null)
            return null;
        }
        if (!sHi.isEmpty ())
        {
          bHiIsPrefix = sHi.endsWith (".*");
          aHi = PackageVersion.parse (bHiIsPrefix ? sHi.substring (0, sHi.length () - 2) : sHi);
          if (aHi == null || (bHiIsPrefix && aHi.hasSuffix ()))
            return null;
        }
        if (aLo == null && aHi == null)
          return null;
        return new Range (false, null, null, aLo, aHi, bHiIsPrefix);
      }
    if (aFields.length != 1)
      return null;
    final String s = aFields[0];
    if (s.endsWith ("+"))
    {
      final PackageVersion aLo = PackageVersion.parse (s.substring (0, s.length () - 1));
      return aLo == null ? null : new Range (false, null, null, aLo, null, false);
    }
    if (s.endsWith (".*"))
    {
      final PackageVersion aHead = PackageVersion.parse (s.substring (0, s.length () - 2));
      return aHead == null || aHead.hasSuffix () ? null : new Range (false, aHead.getParts (), null, null, null, false);
    }
    final PackageVersion aExact = PackageVersion.parse (s);
    return aExact == null ? null : new Range (false, null, aExact, null, null, false);
  }

  /**
   * @param s
   *        The lexical package-version-ranges.
   * @return The parsed ranges or <code>null</code> if the value is invalid
   *         (XTSE0020 for the caller).
   */
  @Nullable
  public static PackageVersionRanges parse (@NonNull final String s)
  {
    final String sTrimmed = s.trim ();
    if (sTrimmed.isEmpty ())
      return null;
    final ICommonsList <Range> aRanges = new CommonsArrayList <> ();
    for (final String sToken : sTrimmed.split (",", -1))
    {
      final Range aRange = _parseRange (sToken.trim ());
      if (aRange == null)
        return null;
      aRanges.add (aRange);
    }
    return new PackageVersionRanges (aRanges);
  }

  /**
   * @param aVersion
   *        A package version.
   * @return <code>true</code> if one of the ranges contains it.
   */
  public boolean matches (@NonNull final PackageVersion aVersion)
  {
    for (final Range aRange : m_aRanges)
      if (aRange.matches (aVersion))
        return true;
    return false;
  }
}
