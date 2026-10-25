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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;

/**
 * The Unicode general categories of the <code>\p{..}</code> regular expression
 * escapes, pinned to Unicode 15.0 (the version of the block table in
 * {@link UnicodeBlocks} and of the JDK 21 character tables), so that a regular
 * expression matches the same characters on every JDK. The category of every
 * code point comes from <code>unicode-15-categories.txt</code> (generated from
 * UnicodeData.txt 15.0). The translation still uses the fast built-in
 * <code>\p{..}</code> classes of {@link java.util.regex.Pattern}, corrected by
 * the code points whose category in the running JDK differs from Unicode 15.0
 * (none on JDK 21).
 *
 * @author Philip Helger
 */
@Immutable
final class UnicodeCategories
{
  private static final String RESOURCE = "unicode-15-categories.txt";

  /** Java's general category constants to their two letter names */
  private static final String [] JDK_NAMES = new String [Character.FINAL_QUOTE_PUNCTUATION + 1];

  static
  {
    JDK_NAMES[Character.UNASSIGNED] = "Cn";
    JDK_NAMES[Character.UPPERCASE_LETTER] = "Lu";
    JDK_NAMES[Character.LOWERCASE_LETTER] = "Ll";
    JDK_NAMES[Character.TITLECASE_LETTER] = "Lt";
    JDK_NAMES[Character.MODIFIER_LETTER] = "Lm";
    JDK_NAMES[Character.OTHER_LETTER] = "Lo";
    JDK_NAMES[Character.NON_SPACING_MARK] = "Mn";
    JDK_NAMES[Character.ENCLOSING_MARK] = "Me";
    JDK_NAMES[Character.COMBINING_SPACING_MARK] = "Mc";
    JDK_NAMES[Character.DECIMAL_DIGIT_NUMBER] = "Nd";
    JDK_NAMES[Character.LETTER_NUMBER] = "Nl";
    JDK_NAMES[Character.OTHER_NUMBER] = "No";
    JDK_NAMES[Character.SPACE_SEPARATOR] = "Zs";
    JDK_NAMES[Character.LINE_SEPARATOR] = "Zl";
    JDK_NAMES[Character.PARAGRAPH_SEPARATOR] = "Zp";
    JDK_NAMES[Character.CONTROL] = "Cc";
    JDK_NAMES[Character.FORMAT] = "Cf";
    JDK_NAMES[Character.PRIVATE_USE] = "Co";
    JDK_NAMES[Character.SURROGATE] = "Cs";
    JDK_NAMES[Character.DASH_PUNCTUATION] = "Pd";
    JDK_NAMES[Character.START_PUNCTUATION] = "Ps";
    JDK_NAMES[Character.END_PUNCTUATION] = "Pe";
    JDK_NAMES[Character.CONNECTOR_PUNCTUATION] = "Pc";
    JDK_NAMES[Character.OTHER_PUNCTUATION] = "Po";
    JDK_NAMES[Character.MATH_SYMBOL] = "Sm";
    JDK_NAMES[Character.CURRENCY_SYMBOL] = "Sc";
    JDK_NAMES[Character.MODIFIER_SYMBOL] = "Sk";
    JDK_NAMES[Character.OTHER_SYMBOL] = "So";
    JDK_NAMES[Character.INITIAL_QUOTE_PUNCTUATION] = "Pi";
    JDK_NAMES[Character.FINAL_QUOTE_PUNCTUATION] = "Pf";
  }

  /** The general category names of XSD regular expressions */
  private static final ICommonsSet <String> NAMES = new CommonsHashSet <> ("L",
                                                                          "Lu",
                                                                          "Ll",
                                                                          "Lt",
                                                                          "Lm",
                                                                          "Lo",
                                                                          "M",
                                                                          "Mn",
                                                                          "Mc",
                                                                          "Me",
                                                                          "N",
                                                                          "Nd",
                                                                          "Nl",
                                                                          "No",
                                                                          "P",
                                                                          "Pc",
                                                                          "Pd",
                                                                          "Ps",
                                                                          "Pe",
                                                                          "Pi",
                                                                          "Pf",
                                                                          "Po",
                                                                          "Z",
                                                                          "Zs",
                                                                          "Zl",
                                                                          "Zp",
                                                                          "S",
                                                                          "Sm",
                                                                          "Sc",
                                                                          "Sk",
                                                                          "So",
                                                                          "C",
                                                                          "Cc",
                                                                          "Cf",
                                                                          "Co",
                                                                          "Cn");

  @PresentForCodeCoverage
  private static final UnicodeCategories INSTANCE = new UnicodeCategories ();

  private UnicodeCategories ()
  {}

  /**
   * A code point whose category in the running JDK differs from Unicode 15.0.
   *
   * @param codePoint
   *        The code point.
   * @param pinned
   *        Its Unicode 15.0 category.
   * @param jdk
   *        Its category in the running JDK.
   */
  private record Difference (int codePoint, @NonNull String pinned, @NonNull String jdk)
  {}

  private static boolean _isIn (@NonNull final String sCategory, @NonNull final String sName)
  {
    return sName.length () == 1 ? sCategory.charAt (0) == sName.charAt (0) : sCategory.equals (sName);
  }

  private static void _appendRuns (@NonNull final StringBuilder aSB, @NonNull final ICommonsList <Integer> aCPs)
  {
    int i = 0;
    while (i < aCPs.size ())
    {
      final int nLow = aCPs.get (i).intValue ();
      int nHigh = nLow;
      while (i + 1 < aCPs.size () && aCPs.get (i + 1).intValue () == nHigh + 1)
      {
        i++;
        nHigh++;
      }
      RegexTranslator.appendRange (aSB, nLow, nHigh);
      i++;
    }
  }

  @NonNull
  private static String _buildExpression (@NonNull final String sName,
                                          @NonNull final ICommonsList <Difference> aDifferences)
  {
    final ICommonsList <Integer> aAdd = new CommonsArrayList <> ();
    final ICommonsList <Integer> aRemove = new CommonsArrayList <> ();
    for (final Difference aDiff : aDifferences)
    {
      final boolean bPinned = _isIn (aDiff.pinned (), sName);
      final boolean bJdk = _isIn (aDiff.jdk (), sName);
      if (bPinned && !bJdk)
        aAdd.add (Integer.valueOf (aDiff.codePoint ()));
      else
        if (!bPinned && bJdk)
          aRemove.add (Integer.valueOf (aDiff.codePoint ()));
    }
    final String sBuiltIn = "\\p{" + sName + "}";
    if (aAdd.isEmpty () && aRemove.isEmpty ())
      return sBuiltIn;
    final StringBuilder aSB = new StringBuilder ("[");
    _appendRuns (aSB, aAdd);
    if (aRemove.isEmpty ())
      aSB.append (sBuiltIn);
    else
    {
      aSB.append ('[').append (sBuiltIn).append ("&&[^");
      _appendRuns (aSB, aRemove);
      aSB.append ("]]");
    }
    return aSB.append (']').toString ();
  }

  private static final class Holder
  {
    // First code point of every run of equal category, ascending
    static final int [] RUN_STARTS;
    // The category of each run
    static final String [] RUN_CATEGORIES;
    // Category name to its Java class syntax
    static final ICommonsMap <String, String> EXPRESSIONS = new CommonsHashMap <> ();

    static
    {
      int [] aStarts = new int [4096];
      final ICommonsList <String> aCategories = new CommonsArrayList <> ();
      try (final InputStream aIS = UnicodeCategories.class.getResourceAsStream (RESOURCE))
      {
        if (aIS == null)
          throw new IllegalStateException ("Resource '" + RESOURCE + "' is missing");
        final BufferedReader aReader = new BufferedReader (new InputStreamReader (aIS, StandardCharsets.US_ASCII));
        String sLine;
        while ((sLine = aReader.readLine ()) != null)
        {
          if (sLine.isEmpty () || sLine.charAt (0) == '#')
            continue;
          final int nSpace = sLine.indexOf (' ');
          if (aCategories.size () == aStarts.length)
            aStarts = Arrays.copyOf (aStarts, aStarts.length * 2);
          aStarts[aCategories.size ()] = Integer.parseInt (sLine.substring (0, nSpace), 16);
          aCategories.add (sLine.substring (nSpace + 1));
        }
      }
      catch (final IOException ex)
      {
        throw new UncheckedIOException (ex);
      }
      RUN_STARTS = Arrays.copyOf (aStarts, aCategories.size ());
      RUN_CATEGORIES = aCategories.toArray (new String [0]);

      final ICommonsList <Difference> aDifferences = new CommonsArrayList <> ();
      int nRun = 0;
      for (int c = 0; c <= Character.MAX_CODE_POINT; ++c)
      {
        while (nRun + 1 < RUN_STARTS.length && RUN_STARTS[nRun + 1] <= c)
          nRun++;
        final String sPinned = RUN_CATEGORIES[nRun];
        final String sJdk = JDK_NAMES[Character.getType (c)];
        if (!sPinned.equals (sJdk))
          aDifferences.add (new Difference (c, sPinned, sJdk));
      }
      for (final String sName : NAMES)
        EXPRESSIONS.put (sName, _buildExpression (sName, aDifferences));
    }
  }

  /**
   * @param sName
   *        A general category name valid in XSD regular expressions (one
   *        letter group like <code>L</code> or two letters like
   *        <code>Lu</code>).
   * @param bNegated
   *        <code>true</code> for the complement (<code>\P{..}</code>).
   * @return The Java class syntax for the Unicode 15.0 characters of the
   *         category, usable inside and outside a bracketed class.
   */
  @NonNull
  static String getClassExpression (@NonNull final String sName, final boolean bNegated)
  {
    final String sExpr = Holder.EXPRESSIONS.get (sName);
    if (sExpr == null)
      throw new IllegalArgumentException ("Unknown general category '" + sName + "'");
    if (!bNegated)
      return sExpr;
    if (sExpr.charAt (0) == '\\')
      return "\\P{" + sName + "}";
    return "[^" + sExpr + "]";
  }

  /**
   * @param sName
   *        A name.
   * @return <code>true</code> if it is a general category name valid in XSD
   *         regular expressions.
   */
  static boolean isCategory (@NonNull final String sName)
  {
    return NAMES.contains (sName);
  }

  /**
   * @param c
   *        A code point.
   * @return The two letter Unicode 15.0 general category of the code point.
   */
  @NonNull
  static String getCategory (final int c)
  {
    int nIndex = Arrays.binarySearch (Holder.RUN_STARTS, c);
    if (nIndex < 0)
      nIndex = -nIndex - 2;
    return Holder.RUN_CATEGORIES[nIndex];
  }
}
