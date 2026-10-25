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
package com.helger.xslt.ext.number;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.fn.FormatInteger;
import com.helger.xpath.fn.FormatWords;

/**
 * The formatting half of xsl:number (XSLT 3.0 section 12.4): the format string
 * is split into a prefix, alphanumeric format tokens, the separators between
 * them and a suffix; each number is rendered with its token.
 *
 * @author Philip Helger
 */
@Immutable
public final class NumberFormatter
{
  /**
   * Unicode "numbering sequence" families: a format token that is a single
   * character of a family renders the integer <code>start + i</code> as the
   * i-th character; numbers outside the family fall back to decimal.
   *
   * @param start
   *        The integer of the first character.
   * @param glyphs
   *        The code points.
   */
  private record Family (long start, int [] glyphs)
  {}

  /**
   * An analysed format string.
   *
   * @param prefix
   *        Leading non-alphanumeric run.
   * @param tokens
   *        The format tokens (at least one).
   * @param seps
   *        The separators between adjacent tokens (one less than tokens).
   * @param suffix
   *        Trailing non-alphanumeric run.
   */
  private record Picture (String prefix, ICommonsList <String> tokens, ICommonsList <String> seps, String suffix)
  {}

  private static final String [] FAMILY_GLYPHS = { "𐄇𐄈𐄉𐄊𐄋𐄌𐄍𐄎𐄏𐄐",
                                                   "𑁒𑁓𑁔𑁕𑁖𑁗𑁘𑁙𑁚𑁛",
                                                   "⓪①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿",
                                                   "㊀㊁㊂㊃㊄㊅㊆㊇㊈㊉",
                                                   "𐋡𐋢𐋣𐋤𐋥𐋦𐋧𐋨𐋩𐋪",
                                                   "𝍠𝍡𝍢𝍣𝍤𝍥𝍦𝍧𝍨",
                                                   "🄀⒈⒉⒊⒋⒌⒍⒎⒏⒐⒑⒒⒓⒔⒕⒖⒗⒘⒙⒚⒛",
                                                   "🄁🄂🄃🄄🄅🄆🄇🄈🄉🄊",
                                                   "🄋➀➁➂➃➄➅➆➇➈➉",
                                                   "⓿❶❷❸❹❺❻❼❽❾❿⓫⓬⓭⓮⓯⓰⓱⓲⓳⓴",
                                                   "🄌➊➋➌➍➎➏➐➑➒➓",
                                                   "⓵⓶⓷⓸⓹⓺⓻⓼⓽⓾",
                                                   "𞣇𞣈𞣉𞣊𞣋𞣌𞣍𞣎𞣏",
                                                   "⑴⑵⑶⑷⑸⑹⑺⑻⑼⑽⑾⑿⒀⒁⒂⒃⒄⒅⒆⒇",
                                                   "㈠㈡㈢㈣㈤㈥㈦㈧㈨㈩",
                                                   "𐹠𐹡𐹢𐹣𐹤𐹥𐹦𐹧𐹨𐹩",
                                                   "𑇡𑇢𑇣𑇤𑇥𑇦𑇧𑇨𑇩𑇪" };
  // The integer of the first glyph of each family above (the DIGIT ONE FULL
  // STOP family starts with the unrelated DIGIT ZERO FULL STOP glyph)
  private static final long [] FAMILY_STARTS = { 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1 };

  // Greek lower case includes the final sigma as its own letter, as the W3C
  // test suite expects; upper case has no such form
  private static final String [] ALPHABETS = { "αβγδεζηθικλμνξοπρςστυφχψω", "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ" };

  private static final ICommonsMap <Integer, Family> FAMILY_INDEX = new CommonsHashMap <> ();
  private static final ICommonsMap <Integer, int []> ALPHABET_INDEX = new CommonsHashMap <> ();

  private static final String [] EN_ONES = { "zero",
                                             "one",
                                             "two",
                                             "three",
                                             "four",
                                             "five",
                                             "six",
                                             "seven",
                                             "eight",
                                             "nine",
                                             "ten",
                                             "eleven",
                                             "twelve",
                                             "thirteen",
                                             "fourteen",
                                             "fifteen",
                                             "sixteen",
                                             "seventeen",
                                             "eighteen",
                                             "nineteen" };
  private static final String [] EN_TENS = { "",
                                             "",
                                             "twenty",
                                             "thirty",
                                             "forty",
                                             "fifty",
                                             "sixty",
                                             "seventy",
                                             "eighty",
                                             "ninety" };
  private static final String [] EN_SCALES = { "",
                                               "thousand",
                                               "million",
                                               "billion",
                                               "trillion",
                                               "quadrillion",
                                               "quintillion" };
  private static final ICommonsMap <String, String> EN_ORDINAL_WORDS = new CommonsHashMap <> ();

  static
  {
    for (int i = 0; i < FAMILY_GLYPHS.length; ++i)
    {
      final Family aFamily = new Family (FAMILY_STARTS[i], FAMILY_GLYPHS[i].codePoints ().toArray ());
      for (final int c : aFamily.glyphs ())
        FAMILY_INDEX.put (Integer.valueOf (c), aFamily);
    }
    for (final String sAlphabet : ALPHABETS)
    {
      final int [] aAlphabet = sAlphabet.codePoints ().toArray ();
      for (final int c : aAlphabet)
        ALPHABET_INDEX.put (Integer.valueOf (c), aAlphabet);
    }
    final String [] aOrdinals = { "zeroth",
                                  "first",
                                  "second",
                                  "third",
                                  "fourth",
                                  "fifth",
                                  "sixth",
                                  "seventh",
                                  "eighth",
                                  "ninth",
                                  "tenth",
                                  "eleventh",
                                  "twelfth",
                                  "thirteenth",
                                  "fourteenth",
                                  "fifteenth",
                                  "sixteenth",
                                  "seventeenth",
                                  "eighteenth",
                                  "nineteenth" };
    for (int i = 0; i < aOrdinals.length; ++i)
      EN_ORDINAL_WORDS.put (EN_ONES[i], aOrdinals[i]);
    final String [] aTensOrdinals = { "twentieth",
                                      "thirtieth",
                                      "fortieth",
                                      "fiftieth",
                                      "sixtieth",
                                      "seventieth",
                                      "eightieth",
                                      "ninetieth" };
    for (int i = 0; i < aTensOrdinals.length; ++i)
      EN_ORDINAL_WORDS.put (EN_TENS[i + 2], aTensOrdinals[i]);
  }

  @PresentForCodeCoverage
  private static final NumberFormatter INSTANCE = new NumberFormatter ();

  private NumberFormatter ()
  {}

  private static boolean _isAlnum (final int c)
  {
    return Character.isLetter (c) || FormatInteger.isNumber (c);
  }

  /**
   * Analyse a format string. A format without any alphanumeric character
   * wraps the implied token "1" on both sides ("*" renders as "*1*").
   */
  @NonNull
  private static Picture _parsePicture (@NonNull final String sFormat)
  {
    final int [] aCPs = sFormat.codePoints ().toArray ();
    final int nLen = aCPs.length;
    int i = 0;
    while (i < nLen && !_isAlnum (aCPs[i]))
      i++;
    final String sPrefix = new String (aCPs, 0, i);
    final ICommonsList <String> aTokens = new CommonsArrayList <> ();
    final ICommonsList <String> aSeps = new CommonsArrayList <> ();
    String sSuffix = "";
    while (i < nLen)
    {
      final int nStart = i;
      while (i < nLen && _isAlnum (aCPs[i]))
        i++;
      aTokens.add (new String (aCPs, nStart, i - nStart));
      final int nSepStart = i;
      while (i < nLen && !_isAlnum (aCPs[i]))
        i++;
      final String sSep = new String (aCPs, nSepStart, i - nSepStart);
      if (i < nLen)
        aSeps.add (sSep);
      else
        sSuffix = sSep;
    }
    if (aTokens.isEmpty ())
    {
      aTokens.add ("1");
      sSuffix = sPrefix;
    }
    return new Picture (sPrefix, aTokens, aSeps, sSuffix);
  }

  /**
   * @return The zero code point of the decimal digit family of the token and
   *         the token length, or <code>null</code> if the token is not made of
   *         decimal digits of one family.
   */
  @Nullable
  private static int [] _digitFamily (@NonNull final String sToken)
  {
    final int [] aCPs = sToken.codePoints ().toArray ();
    if (aCPs.length == 0)
      return null;
    int nZero = -1;
    for (final int c : aCPs)
    {
      if (!FormatInteger.isDigit (c))
        return null;
      final int z = c - FormatInteger.getDigitValue (c);
      if (nZero < 0)
        nZero = z;
      else
        if (z != nZero)
          return null;
    }
    return new int [] { nZero, aCPs.length };
  }

  /** Insert the separator every nSize digits from the right. */
  @NonNull
  private static String _group (@NonNull final String s, @NonNull final String sSep, final int nSize)
  {
    if (nSize <= 0 || s.length () <= nSize)
      return s;
    final StringBuilder aSB = new StringBuilder ();
    final int nFirst = s.length () % nSize;
    if (nFirst > 0)
      aSB.append (s, 0, nFirst);
    for (int i = nFirst; i < s.length (); i += nSize)
    {
      if (i > 0)
        aSB.append (sSep);
      aSB.append (s, i, i + nSize);
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _padAndGroup (@NonNull final String sDigits,
                                      final int nWidth,
                                      @NonNull final String sGroupSep,
                                      final int nGroupSize)
  {
    String s = sDigits;
    if (nWidth > s.length ())
      s = "0".repeat (nWidth - s.length ()) + s;
    if (!sGroupSep.isEmpty () && nGroupSize > 0)
      s = _group (s, sGroupSep, nGroupSize);
    return s;
  }

  @NonNull
  private static String _formatBigDecimal (@NonNull final BigInteger aValue,
                                           @NonNull final String sToken,
                                           @NonNull final String sGroupSep,
                                           final int nGroupSize)
  {
    final int [] aFamily = _digitFamily (sToken);
    String s = _padAndGroup (aValue.abs ().toString (), aFamily != null ? aFamily[1] : 0, sGroupSep, nGroupSize);
    if (aValue.signum () < 0)
      s = "-" + s;
    return aFamily != null ? FormatInteger.mapDigitsToFamily (s, aFamily[0]) : s;
  }

  private static boolean _isWordToken (@NonNull final String sToken)
  {
    if (sToken.isEmpty ())
      return false;
    for (final char c : sToken.toCharArray ())
      if (c != 'w' && c != 'W')
        return false;
    return true;
  }

  private static boolean _isEnglish (@Nullable final String sLang)
  {
    if (sLang == null)
      return true;
    final String s = sLang.strip ().toLowerCase (Locale.ROOT);
    return s.isEmpty () || s.equals ("en") || s.startsWith ("en-");
  }

  @NonNull
  private static String _spellHundreds (final long n)
  {
    final ICommonsList <String> aParts = new CommonsArrayList <> ();
    long g = n;
    if (g >= 100)
    {
      aParts.add (EN_ONES[(int) (g / 100)] + " hundred");
      g %= 100;
    }
    if (g > 0)
    {
      if (g < 20)
        aParts.add (EN_ONES[(int) g]);
      else
        if (g % 10 == 0)
          aParts.add (EN_TENS[(int) (g / 10)]);
        else
          aParts.add (EN_TENS[(int) (g / 10)] + " " + EN_ONES[(int) (g % 10)]);
    }
    return String.join (" ", aParts);
  }

  /**
   * English cardinal words separated by spaces, e.g. 21 is "twenty one".
   */
  @NonNull
  private static String _spellEnglish (final long n)
  {
    if (n == 0)
      return "zero";
    final BigInteger aThousand = BigInteger.valueOf (1000);
    BigInteger aRest = BigInteger.valueOf (n).abs ();
    final ICommonsList <Long> aGroups = new CommonsArrayList <> ();
    while (aRest.signum () > 0)
    {
      aGroups.add (Long.valueOf (aRest.mod (aThousand).longValue ()));
      aRest = aRest.divide (aThousand);
    }
    final ICommonsList <String> aParts = new CommonsArrayList <> ();
    for (int i = aGroups.size () - 1; i >= 0; --i)
    {
      final long g = aGroups.get (i).longValue ();
      if (g == 0)
        continue;
      String s = _spellHundreds (g);
      if (i < EN_SCALES.length && !EN_SCALES[i].isEmpty ())
        s += " " + EN_SCALES[i];
      aParts.add (s);
    }
    final String ret = String.join (" ", aParts);
    return n < 0 ? "minus " + ret : ret;
  }

  /** English ordinal words: only the last word becomes an ordinal. */
  @NonNull
  private static String _ordinalEnglish (final long n)
  {
    final String [] aWords = _spellEnglish (n).split (" ");
    final String sLast = aWords[aWords.length - 1];
    final String sOrd = EN_ORDINAL_WORDS.get (sLast);
    aWords[aWords.length - 1] = sOrd != null ? sOrd : sLast + "th";
    return String.join (" ", aWords);
  }

  /** "w" lower case, "W" upper case, anything else title case per word. */
  @NonNull
  private static String _caseWords (@NonNull final String sWords, @NonNull final String sToken)
  {
    if (sToken.equals ("w"))
      return sWords;
    if (sToken.equals ("W"))
      return sWords.toUpperCase (Locale.ROOT);
    final String [] aParts = sWords.split (" ", -1);
    for (int i = 0; i < aParts.length; ++i)
    {
      final String p = aParts[i];
      if (!p.isEmpty ())
      {
        final int c = p.codePointAt (0);
        aParts[i] = new String (Character.toChars (Character.toUpperCase (c))) + p.substring (Character.charCount (c));
      }
    }
    return String.join (" ", aParts);
  }

  @NonNull
  private static String _formatOne (@NonNull final BigInteger aValue,
                                    @NonNull final String sTokenIn,
                                    @NonNull final String sGroupSep,
                                    final int nGroupSize,
                                    @Nullable final String sLang,
                                    @NonNull final String sOrdinal)
  {
    final String sToken = sTokenIn.isEmpty () ? "1" : sTokenIn;
    if (aValue.bitLength () >= 64)
    {
      // Beyond the long range only decimal has a defined meaning
      return _formatBigDecimal (aValue, sToken, sGroupSep, nGroupSize);
    }
    final long n = aValue.longValue ();
    switch (sToken)
    {
      case "a":
      case "A":
        return n > 0 ? FormatInteger.toAlphabetic (n, FormatInteger.getLatinAlphabet (sToken.equals ("A")))
                     : Long.toString (n);
      case "i":
      case "I":
        return n > 0 && n < 4000 ? FormatInteger.toRoman (n, sToken.equals ("I")) : Long.toString (n);
      default:
        break;
    }
    final boolean bEnglish = _isEnglish (sLang);
    if (_isWordToken (sToken))
    {
      if (bEnglish)
        return _caseWords (sOrdinal.isEmpty () ? _spellEnglish (n) : _ordinalEnglish (n), sToken);
      if (FormatWords.getNativeLanguage (sLang) != null && Math.abs (n) < FormatWords.LANG_MAX)
        return FormatWords.spell (n,
                                  sToken,
                                  !sOrdinal.isEmpty (),
                                  sLang,
                                  "yes".equals (sOrdinal) ? "" : sOrdinal);
    }
    final int [] aFamily = _digitFamily (sToken);
    if (aFamily != null)
    {
      String s = _padAndGroup (Long.toString (Math.abs (n)), aFamily[1], sGroupSep, nGroupSize);
      if (n < 0)
        s = "-" + s;
      if (!sOrdinal.isEmpty () && bEnglish)
        s += FormatWords.getOrdinalSuffix (n);
      return FormatInteger.mapDigitsToFamily (s, aFamily[0]);
    }
    if (sToken.codePointCount (0, sToken.length ()) == 1)
    {
      final Integer aCP = Integer.valueOf (sToken.codePointAt (0));
      final Family aFam = FAMILY_INDEX.get (aCP);
      if (aFam != null)
      {
        final long nIdx = n - aFam.start ();
        if (nIdx >= 0 && nIdx < aFam.glyphs ().length)
          return new String (Character.toChars (aFam.glyphs ()[(int) nIdx]));
      }
      final int [] aAlphabet = ALPHABET_INDEX.get (aCP);
      if (aAlphabet != null)
        return n > 0 ? FormatInteger.toAlphabetic (n, aAlphabet) : Long.toString (n);
    }
    // Unknown token: plain decimal
    return Long.toString (n);
  }

  /**
   * Format a list of numbers. The last format token is reused for surplus
   * numbers; the separator before a number is the one preceding its token, or
   * "." when there is only one token. An empty list still yields the prefix
   * and the suffix.
   *
   * @param aNums
   *        The numbers.
   * @param sFormat
   *        The format string; empty means "1".
   * @param sGroupSep
   *        Grouping separator, possibly empty.
   * @param nGroupSize
   *        Grouping size, 0 for no grouping.
   * @param sLang
   *        Language, may be <code>null</code>.
   * @param sOrdinal
   *        Value of the ordinal attribute, empty for cardinal numbers.
   * @return The formatted string.
   */
  @NonNull
  public static String format (@NonNull final List <BigInteger> aNums,
                               @NonNull final String sFormat,
                               @NonNull final String sGroupSep,
                               final int nGroupSize,
                               @Nullable final String sLang,
                               @NonNull final String sOrdinal)
  {
    final Picture aPic = _parsePicture (sFormat.isEmpty () ? "1" : sFormat);
    final int m = aPic.tokens ().size ();
    final StringBuilder aSB = new StringBuilder (aPic.prefix ());
    for (int i = 0; i < aNums.size (); ++i)
    {
      final int k = Math.min (i, m - 1);
      if (i > 0)
      {
        if (k >= 1 && k - 1 < aPic.seps ().size ())
          aSB.append (aPic.seps ().get (k - 1));
        else
          aSB.append ('.');
      }
      aSB.append (_formatOne (aNums.get (i), aPic.tokens ().get (k), sGroupSep, nGroupSize, sLang, sOrdinal));
    }
    aSB.append (aPic.suffix ());
    return aSB.toString ();
  }
}
