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
package com.helger.xpath.regex;

import java.util.Arrays;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;

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

  private static final class Holder
  {
    // For every code point with case variants: all members of its class
    static final ICommonsMap <Integer, int []> VARIANTS = _build ();

    private static int _fold (final int c)
    {
      if (c == 0x130 || c == 0x131)
        return c;
      return Character.toLowerCase (Character.toUpperCase (c));
    }

    @NonNull
    private static ICommonsMap <Integer, int []> _build ()
    {
      final ICommonsMap <Integer, int []> aClasses = new CommonsHashMap <> ();
      for (int c = 0; c <= Character.MAX_CODE_POINT; ++c)
      {
        final int nFold = _fold (c);
        // Only characters of Unicode 15.0, the version of UnicodeCategories,
        // so that the result does not depend on the JDK
        if (nFold != c && !"Cn".equals (UnicodeCategories.getCategory (c)) &&
            !"Cn".equals (UnicodeCategories.getCategory (nFold)))
        {
          final Integer aKey = Integer.valueOf (nFold);
          final int [] aOld = aClasses.get (aKey);
          final int [] aNew;
          if (aOld == null)
            aNew = new int [] { nFold, c };
          else
          {
            aNew = Arrays.copyOf (aOld, aOld.length + 1);
            aNew[aOld.length] = c;
          }
          aClasses.put (aKey, aNew);
        }
      }
      final ICommonsMap <Integer, int []> ret = new CommonsHashMap <> ();
      for (final int [] aClass : aClasses.values ())
        for (final int c : aClass)
          ret.put (Integer.valueOf (c), aClass);
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
    final int [] ret = Holder.VARIANTS.get (Integer.valueOf (c));
    return ret == null ? NONE : ret;
  }
}
