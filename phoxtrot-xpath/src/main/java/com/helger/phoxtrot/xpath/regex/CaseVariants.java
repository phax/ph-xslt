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
package com.helger.phoxtrot.xpath.regex;

import java.util.Arrays;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.map.IntObjectMap;

/**
 * The case variants of a character for the 'i' flag of the XPath regular
 * expression functions (F&amp;O 3.1 section 5.6.2): two characters match
 * case-insensitively if they have the same simple case folding. The folding is
 * derived from the JDK case mappings as <code>lower(upper(c))</code>, with the
 * Turkic dotted and dotless i excluded (Unicode simple case folding leaves
 * both alone). Characters that are not assigned in Unicode 15.0 have no case
 * variants, so that a newer JDK does not add any.
 *
 * @author Philip Helger
 */
@Immutable
final class CaseVariants
{
  private static final int [] NONE = new int [0];

  @PresentForCodeCoverage
  private static final CaseVariants INSTANCE = new CaseVariants ();

  private CaseVariants ()
  {}

  /**
   * Lazily initialised table of the case variant classes, built on first use
   * of {@link CaseVariants#getVariants(int)}.
   */
  private static final class Holder
  {
    // For every code point with case variants: all members of its class
    static final IntObjectMap <int []> VARIANTS = _build ();

    /**
     * @return The simple case folding of the code point as
     *         <code>lower(upper(c))</code> of the JDK; the Turkic U+0130 and
     *         U+0131 fold to themselves.
     */
    private static int _fold (final int c)
    {
      if (c == 0x130 || c == 0x131)
        return c;
      return Character.toLowerCase (Character.toUpperCase (c));
    }

    /**
     * Group all code points by their folding: every code point whose folding
     * differs from itself joins the class of its folding, if neither is
     * unassigned (<code>Cn</code>) in {@link UnicodeCategories}. Runs once
     * over all code points, on first use of {@link #VARIANTS}.
     *
     * @return A map from every member of a class (the folding first, then the
     *         other members in code point order) to the shared array of all
     *         members. Code points without case variants are absent.
     */
    @NonNull
    private static IntObjectMap <int []> _build ()
    {
      final IntObjectMap <int []> aClasses = new IntObjectMap <> ();
      for (int c = 0; c <= Character.MAX_CODE_POINT; ++c)
      {
        final int nFold = _fold (c);
        // Only characters of Unicode 15.0, the version of UnicodeCategories,
        // so that the result does not depend on the JDK
        if (nFold != c && !"Cn".equals (UnicodeCategories.getCategory (c)) &&
            !"Cn".equals (UnicodeCategories.getCategory (nFold)))
        {
          final int [] aOld = aClasses.get (nFold);
          final int [] aNew;
          if (aOld == null)
            aNew = new int [] { nFold, c };
          else
          {
            aNew = Arrays.copyOf (aOld, aOld.length + 1);
            aNew[aOld.length] = c;
          }
          aClasses.put (nFold, aNew);
        }
      }
      final IntObjectMap <int []> ret = new IntObjectMap <> ();
      aClasses.forEach ( (nFold, aClass) -> {
        for (final int c : aClass)
          ret.put (c, aClass);
      });
      return ret;
    }
  }

  /**
   * @param c
   *        A code point.
   * @return All code points that match <code>c</code> case-insensitively,
   *         including <code>c</code> itself; an empty array if there is no
   *         other one.
   */
  @NonNull
  static int [] getVariants (final int c)
  {
    final int [] ret = Holder.VARIANTS.get (c);
    return ret == null ? NONE : ret;
  }
}
