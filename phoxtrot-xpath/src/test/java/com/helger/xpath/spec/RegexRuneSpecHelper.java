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
package com.helger.xpath.spec;

import static org.junit.Assert.fail;

import java.util.Locale;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.regex.XPathRegex;

/**
 * Port of go-xslt's <code>rxRunRunes</code>: a pattern compiled through the
 * XPath regular expression entry point, matched (unanchored) against a
 * one-character input.
 *
 * @author Philip Helger
 */
final class RegexRuneSpecHelper
{
  /**
   * One pattern, one code point, and whether the pattern matches it.
   *
   * @param pattern
   *        The regular expression.
   * @param cp
   *        The code point of the input.
   * @param want
   *        Whether it must match.
   */
  record RuneCase (@NonNull String pattern, int cp, boolean want)
  {}

  @PresentForCodeCoverage
  private static final RegexRuneSpecHelper INSTANCE = new RegexRuneSpecHelper ();

  private RegexRuneSpecHelper ()
  {}

  @NonNull
  static RuneCase r (@NonNull final String sPattern, final int nCP, final boolean bWant)
  {
    return new RuneCase (sPattern, nCP, bWant);
  }

  /**
   * @param sPattern
   *        Pattern.
   * @param sFlags
   *        Flags.
   * @param sInput
   *        Input string.
   * @return <code>true</code> if some substring of the input matches.
   */
  static boolean find (@NonNull final String sPattern, @NonNull final String sFlags, @NonNull final String sInput)
  {
    return XPathRegex.compile (sPattern, sFlags).getPattern ().matcher (sInput).find ();
  }

  static void runRunes (@NonNull final RuneCase... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final RuneCase aCase : aCases)
    {
      try
      {
        final boolean bGot = find (aCase.pattern (), "", new String (Character.toChars (aCase.cp ())));
        if (bGot != aCase.want ())
          aErrors.add (aCase.pattern () +
                       " matches U+" +
                       String.format (Locale.ROOT, "%04X", Integer.valueOf (aCase.cp ())) +
                       " = " +
                       bGot +
                       ", want " +
                       aCase.want ());
      }
      catch (final XPathException ex)
      {
        aErrors.add (aCase.pattern () + ": unexpected error " + ex.getCode () + ": " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }
}
