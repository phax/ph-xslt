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
package com.helger.xpath.fn;

import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Integer spellout for the <code>w</code>, <code>W</code> and <code>Ww</code>
 * presentation tokens of format-integer, format-date and xsl:number: English,
 * plus native German, French and Italian (CLDR spellout-cardinal /
 * spellout-ordinal rules). German writes numbers below a million as one word
 * and larger groups with spaces; French hyphenates 17-99 and uses "et" before 1
 * in 21..61 and 71; Italian joins everything up to the millions into one word
 * with the usual elisions.
 *
 * @author Philip Helger
 */
@Immutable
public final class FormatWords
{
  /** Numbers at or beyond this are spelled in English. */
  public static final long LANG_MAX = 1_000_000_000_000L;

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
  private static final String [] EN_ORD_ONES = { "zeroth",
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
  private static final String [] EN_ORD_TENS = { "",
                                                 "",
                                                 "twentieth",
                                                 "thirtieth",
                                                 "fortieth",
                                                 "fiftieth",
                                                 "sixtieth",
                                                 "seventieth",
                                                 "eightieth",
                                                 "ninetieth" };
  private static final long [] EN_SCALES = { 1_000_000_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1000L };
  private static final String [] EN_SCALE_NAMES = { "quintillion",
                                                    "quadrillion",
                                                    "trillion",
                                                    "billion",
                                                    "million",
                                                    "thousand" };

  private static final String [] DE_ONES = { "null",
                                             "eins",
                                             "zwei",
                                             "drei",
                                             "vier",
                                             "fünf",
                                             "sechs",
                                             "sieben",
                                             "acht",
                                             "neun",
                                             "zehn",
                                             "elf",
                                             "zwölf",
                                             "dreizehn",
                                             "vierzehn",
                                             "fünfzehn",
                                             "sechzehn",
                                             "siebzehn",
                                             "achtzehn",
                                             "neunzehn" };
  private static final String [] DE_TENS = { "",
                                             "",
                                             "zwanzig",
                                             "dreißig",
                                             "vierzig",
                                             "fünfzig",
                                             "sechzig",
                                             "siebzig",
                                             "achtzig",
                                             "neunzig" };
  private static final String [] DE_ORD_SMALL = { "nullt",
                                                  "erst",
                                                  "zweit",
                                                  "dritt",
                                                  "viert",
                                                  "fünft",
                                                  "sechst",
                                                  "siebt",
                                                  "acht",
                                                  "neunt",
                                                  "zehnt",
                                                  "elft",
                                                  "zwölft",
                                                  "dreizehnt",
                                                  "vierzehnt",
                                                  "fünfzehnt",
                                                  "sechzehnt",
                                                  "siebzehnt",
                                                  "achtzehnt",
                                                  "neunzehnt" };

  private static final String [] FR_ONES = { "zéro",
                                             "un",
                                             "deux",
                                             "trois",
                                             "quatre",
                                             "cinq",
                                             "six",
                                             "sept",
                                             "huit",
                                             "neuf",
                                             "dix",
                                             "onze",
                                             "douze",
                                             "treize",
                                             "quatorze",
                                             "quinze",
                                             "seize",
                                             "dix-sept",
                                             "dix-huit",
                                             "dix-neuf" };
  private static final String [] FR_TENS = { "", "", "vingt", "trente", "quarante", "cinquante", "soixante" };

  private static final String [] IT_ONES = { "zero",
                                             "uno",
                                             "due",
                                             "tre",
                                             "quattro",
                                             "cinque",
                                             "sei",
                                             "sette",
                                             "otto",
                                             "nove",
                                             "dieci",
                                             "undici",
                                             "dodici",
                                             "tredici",
                                             "quattordici",
                                             "quindici",
                                             "sedici",
                                             "diciassette",
                                             "diciotto",
                                             "diciannove" };
  private static final String [] IT_TENS = { "",
                                             "",
                                             "venti",
                                             "trenta",
                                             "quaranta",
                                             "cinquanta",
                                             "sessanta",
                                             "settanta",
                                             "ottanta",
                                             "novanta" };
  private static final String [] IT_ORD_SMALL = { "",
                                                  "prim",
                                                  "second",
                                                  "terz",
                                                  "quart",
                                                  "quint",
                                                  "sest",
                                                  "settim",
                                                  "ottav",
                                                  "non",
                                                  "decim" };

  @PresentForCodeCoverage
  private static final FormatWords INSTANCE = new FormatWords ();

  private FormatWords ()
  {}

  @NonNull
  private static String _enCardinal (final long n)
  {
    if (n < 20)
      return EN_ONES[(int) n];
    if (n < 100)
    {
      final String s = EN_TENS[(int) (n / 10)];
      return n % 10 != 0 ? s + "-" + EN_ONES[(int) (n % 10)] : s;
    }
    if (n < 1000)
    {
      final String s = EN_ONES[(int) (n / 100)] + " hundred";
      return n % 100 != 0 ? s + " and " + _enCardinal (n % 100) : s;
    }
    for (int i = 0; i < EN_SCALES.length; ++i)
    {
      final long nScale = EN_SCALES[i];
      if (n >= nScale)
      {
        String s = _enCardinal (n / nScale) + " " + EN_SCALE_NAMES[i];
        final long nRem = n % nScale;
        if (nRem != 0)
          s += (nRem < 100 ? " and " : " ") + _enCardinal (nRem);
        return s;
      }
    }
    return EN_ONES[0];
  }

  @NonNull
  private static String _enOrdinal (final long n)
  {
    if (n < 20)
      return EN_ORD_ONES[(int) n];
    if (n < 100)
    {
      if (n % 10 == 0)
        return EN_ORD_TENS[(int) (n / 10)];
      return EN_TENS[(int) (n / 10)] + "-" + EN_ORD_ONES[(int) (n % 10)];
    }
    for (int i = 0; i <= EN_SCALES.length; ++i)
    {
      final long nScale = i < EN_SCALES.length ? EN_SCALES[i] : 100;
      final String sName = i < EN_SCALES.length ? EN_SCALE_NAMES[i] : "hundred";
      if (n >= nScale)
      {
        final String sHead = _enCardinal (n / nScale) + " " + sName;
        final long nRem = n % nScale;
        if (nRem == 0)
          return sHead + "th";
        return sHead + (nRem < 100 ? " and " : " ") + _enOrdinal (nRem);
      }
    }
    return EN_ORD_ONES[0];
  }

  @NonNull
  private static String _deUnit (final long n)
  {
    return n == 1 ? "ein" : DE_ONES[(int) n];
  }

  @NonNull
  private static String _deBelow100 (final long n, final boolean bAttach)
  {
    if (n < 20)
      return n == 1 && bAttach ? "ein" : DE_ONES[(int) n];
    final long t = n / 10;
    final long o = n % 10;
    if (o == 0)
      return DE_TENS[(int) t];
    return _deUnit (o) + "und" + DE_TENS[(int) t];
  }

  @NonNull
  private static String _deBelow1000 (final long n, final boolean bAttach)
  {
    final long h = n / 100;
    final long r = n % 100;
    String s = "";
    if (h > 0)
      s = _deUnit (h) + "hundert";
    if (r > 0)
      s += _deBelow100 (r, bAttach);
    return s;
  }

  @NonNull
  private static String _deCardinal (final long n)
  {
    if (n == 0)
      return "null";
    final ICommonsList <String> aParts = new CommonsArrayList <> ();
    final long b = n / 1_000_000_000;
    if (b > 0)
      aParts.add (b == 1 ? "eine Milliarde" : _deBelow1000 (b, true) + " Milliarden");
    final long m = n % 1_000_000_000 / 1_000_000;
    if (m > 0)
      aParts.add (m == 1 ? "eine Million" : _deBelow1000 (m, true) + " Millionen");
    final long t = n % 1_000_000 / 1000;
    final long r = n % 1000;
    String sTail = "";
    if (t > 0)
      sTail = t == 1 ? "eintausend" : _deBelow1000 (t, true) + "tausend";
    if (r > 0)
      sTail += _deBelow1000 (r, false);
    if (!sTail.isEmpty ())
      aParts.add (sTail);
    return String.join (" ", aParts);
  }

  @NonNull
  private static String _deOrdinalStem (final long n)
  {
    final long [] aScales = { 1_000_000_000L, 1_000_000L };
    final String [] aWords = { "milliardst", "millionst" };
    for (int i = 0; i < aScales.length; ++i)
    {
      final long v = aScales[i];
      if (n < v)
        continue;
      final long nRem = n % v;
      if (nRem != 0)
        return _deCardinal (n - nRem) + " " + _deOrdinalStem (nRem);
      final long q = n / v;
      if (q != 1)
        return _deBelow1000 (q, true) + aWords[i];
      return aWords[i];
    }
    if (n < 20)
      return DE_ORD_SMALL[(int) n];
    final long r = n % 100;
    if (r >= 1 && r < 20)
      return _deCardinal (n - r) + DE_ORD_SMALL[(int) r];
    return _deCardinal (n) + "st";
  }

  @Nullable
  private static String _ordinalEnding (@NonNull final String sVariant)
  {
    if (sVariant.startsWith ("-") && sVariant.length () > 1)
      return sVariant.substring (1);
    return null;
  }

  @NonNull
  private static String _deOrdinal (final long n, @NonNull final String sVariant)
  {
    final String sEnding = _ordinalEnding (sVariant);
    return _deOrdinalStem (n) + (sEnding != null ? sEnding : "e");
  }

  @NonNull
  private static String _frBelow100 (final long n, final boolean bFinal)
  {
    if (n < 20)
      return FR_ONES[(int) n];
    if (n < 70)
    {
      final long t = n / 10;
      final long o = n % 10;
      if (o == 0)
        return FR_TENS[(int) t];
      if (o == 1)
        return FR_TENS[(int) t] + " et un";
      return FR_TENS[(int) t] + "-" + FR_ONES[(int) o];
    }
    if (n < 80)
    {
      if (n == 71)
        return "soixante et onze";
      return "soixante-" + FR_ONES[(int) (n - 60)];
    }
    if (n == 80)
      return bFinal ? "quatre-vingts" : "quatre-vingt";
    return "quatre-vingt-" + FR_ONES[(int) (n - 80)];
  }

  @NonNull
  private static String _frBelow1000 (final long n, final boolean bFinal)
  {
    final long h = n / 100;
    final long r = n % 100;
    if (h == 0)
      return _frBelow100 (r, bFinal);
    String sHead = "cent";
    if (h > 1)
    {
      sHead = FR_ONES[(int) h] + " cent";
      if (r == 0 && bFinal)
        sHead += "s";
    }
    if (r == 0)
      return sHead;
    return sHead + " " + _frBelow100 (r, bFinal);
  }

  @NonNull
  private static String _frCardinal (final long n)
  {
    if (n == 0)
      return "zéro";
    final ICommonsList <String> aParts = new CommonsArrayList <> ();
    final long b = n / 1_000_000_000;
    if (b > 0)
      aParts.add (_frBelow1000 (b, true) + " milliard" + (b > 1 ? "s" : ""));
    final long m = n % 1_000_000_000 / 1_000_000;
    if (m > 0)
      aParts.add (_frBelow1000 (m, true) + " million" + (m > 1 ? "s" : ""));
    final long t = n % 1_000_000 / 1000;
    if (t > 0)
      aParts.add (t == 1 ? "mille" : _frBelow1000 (t, false) + " mille");
    final long r = n % 1000;
    if (r > 0)
      aParts.add (_frBelow1000 (r, true));
    return String.join (" ", aParts);
  }

  @NonNull
  private static String _frOrdinal (final long n, @NonNull final String sVariant)
  {
    if (n == 1)
    {
      final String sEnding = _ordinalEnding (sVariant);
      if (sEnding != null &&
          (sEnding.equals ("e") || sEnding.equals ("re") || sEnding.equals ("ère") || sEnding.equals ("ere")) ||
          sVariant.endsWith ("feminine"))
        return "première";
      return "premier";
    }
    final String sCard = _frCardinal (n);
    final int nCut = Math.max (sCard.lastIndexOf (' '), sCard.lastIndexOf ('-')) + 1;
    final String sHead = sCard.substring (0, nCut);
    String sLast = sCard.substring (nCut);
    switch (sLast)
    {
      case "vingts":
      case "cents":
      case "millions":
      case "milliards":
        sLast = sLast.substring (0, sLast.length () - 1);
        break;
      default:
        break;
    }
    if (sLast.equals ("mille"))
      sLast = "mill";
    else
      if (sLast.equals ("cinq"))
        sLast = "cinqu";
      else
        if (sLast.equals ("neuf"))
          sLast = "neuv";
        else
          if (sLast.endsWith ("e"))
            sLast = sLast.substring (0, sLast.length () - 1);
    return sHead + sLast + "ième";
  }

  @NonNull
  private static String _itBelow100 (final long n, final boolean bFinal)
  {
    if (n < 20)
      return IT_ONES[(int) n];
    final long t = n / 10;
    final long o = n % 10;
    String sBase = IT_TENS[(int) t];
    if (o == 0)
      return sBase;
    if (o == 1 || o == 8)
      sBase = sBase.substring (0, sBase.length () - 1);
    else
      if (o == 3 && bFinal)
        return sBase + "tré";
    return sBase + IT_ONES[(int) o];
  }

  @NonNull
  private static String _itBelow1000 (final long n, final boolean bFinal)
  {
    final long h = n / 100;
    final long r = n % 100;
    if (h == 0)
      return _itBelow100 (r, bFinal);
    String sHead = h > 1 ? IT_ONES[(int) h] + "cento" : "cento";
    if (r == 0)
      return sHead;
    final String sRest = _itBelow100 (r, bFinal);
    if (sRest.startsWith ("o"))
      sHead = sHead.substring (0, sHead.length () - 1);
    return sHead + sRest;
  }

  @NonNull
  private static String _itCardinal (final long n)
  {
    if (n == 0)
      return "zero";
    final ICommonsList <String> aParts = new CommonsArrayList <> ();
    final long b = n / 1_000_000_000;
    if (b > 0)
      aParts.add (b == 1 ? "un miliardo" : _itBelow1000 (b, true) + " miliardi");
    final long m = n % 1_000_000_000 / 1_000_000;
    if (m > 0)
      aParts.add (m == 1 ? "un milione" : _itBelow1000 (m, true) + " milioni");
    final long t = n % 1_000_000 / 1000;
    final long r = n % 1000;
    String sTail = "";
    if (t > 0)
      sTail = t == 1 ? "mille" : _itBelow1000 (t, false) + "mila";
    if (r > 0)
      sTail += _itBelow1000 (r, true);
    if (!sTail.isEmpty ())
      aParts.add (sTail);
    return String.join (" ", aParts);
  }

  @NonNull
  private static String _itOrdinal (final long n, @NonNull final String sVariant)
  {
    String sEnding = _ordinalEnding (sVariant);
    if (sEnding == null)
      sEnding = sVariant.endsWith ("feminine") ? "a" : "o";
    if (n >= 1 && n <= 10)
      return IT_ORD_SMALL[(int) n] + sEnding;
    final String sCard = _itCardinal (n);
    if (n == 0)
      return sCard;
    final int nCut = sCard.lastIndexOf (' ') + 1;
    final String sHead = sCard.substring (0, nCut);
    String sLast = sCard.substring (nCut);
    if (sLast.endsWith ("mila"))
      sLast = sLast.substring (0, sLast.length () - 4) + "mill";
    else
      if (sLast.endsWith ("mille"))
        sLast = sLast.substring (0, sLast.length () - 5) + "mill";
      else
        if (sLast.endsWith ("milione") ||
            sLast.endsWith ("milioni") ||
            sLast.endsWith ("miliardo") ||
            sLast.endsWith ("miliardi"))
          sLast = sLast.substring (0, sLast.length () - 1);
        else
          if (sLast.endsWith ("tré"))
            sLast = sLast.substring (0, sLast.length () - 1) + "e";
          else
            if (!sLast.endsWith ("tre") && !sLast.endsWith ("sei"))
              sLast = sLast.substring (0, sLast.length () - 1);
    return sHead + sLast + "esim" + sEnding;
  }

  /**
   * @param sTag
   *        A language tag such as "de" or "fr-CA"; may be <code>null</code>.
   * @return The base language ("de", "fr", "it") if natively spelled, else
   *         <code>null</code> (English).
   */
  @Nullable
  public static String getNativeLanguage (@Nullable final String sTag)
  {
    if (sTag == null)
      return null;
    String sBase = sTag.strip ().toLowerCase (Locale.ROOT);
    final int nDash = sBase.indexOf ('-');
    if (nDash >= 0)
      sBase = sBase.substring (0, nDash);
    final int nUnderscore = sBase.indexOf ('_');
    if (nUnderscore >= 0)
      sBase = sBase.substring (0, nUnderscore);
    switch (sBase)
    {
      case "de":
      case "fr":
      case "it":
        return sBase;
      default:
        return null;
    }
  }

  /**
   * Title case: the first letter of every space- or hyphen-separated word upper
   * case, the rest lower case.
   *
   * @param s
   *        Source
   * @return Title-cased string.
   */
  @NonNull
  public static String titleCase (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder ();
    boolean bUpNext = true;
    for (final int c : s.codePoints ().toArray ())
    {
      if (c == ' ' || c == '-')
      {
        bUpNext = true;
        aSB.appendCodePoint (c);
      }
      else
        if (bUpNext)
        {
          aSB.appendCodePoint (Character.toUpperCase (c));
          bUpNext = false;
        }
        else
          aSB.appendCodePoint (Character.toLowerCase (c));
    }
    return aSB.toString ();
  }

  /**
   * Spell a number in words.
   *
   * @param n
   *        The number (any sign; a negative number gets a leading minus).
   * @param sToken
   *        "w" (lower case), "W" (upper case) or "Ww" (title case).
   * @param bOrdinal
   *        <code>true</code> for an ordinal ("first").
   * @param sLanguage
   *        Language tag or <code>null</code>; de/fr/it are native, everything
   *        else is English.
   * @param sVariant
   *        Ordinal variant, the text between the parentheses of
   *        <code>o(...)</code> (e.g. "-er", "%spellout-ordinal-feminine"), or
   *        empty.
   * @return The words.
   */
  @NonNull
  public static String spell (final long n,
                              @NonNull final String sToken,
                              final boolean bOrdinal,
                              @Nullable final String sLanguage,
                              @NonNull final String sVariant)
  {
    // Long.MIN_VALUE cannot be negated; spell its magnitude via the next value
    final long nAbs = n == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs (n);
    final String sLang = nAbs < LANG_MAX ? getNativeLanguage (sLanguage) : null;
    String sWords;
    if (sLang == null)
      sWords = bOrdinal ? _enOrdinal (nAbs) : _enCardinal (nAbs);
    else
      switch (sLang)
      {
        case "de":
          sWords = bOrdinal ? _deOrdinal (nAbs, sVariant) : _deCardinal (nAbs);
          break;
        case "fr":
          sWords = bOrdinal ? _frOrdinal (nAbs, sVariant) : _frCardinal (nAbs);
          break;
        default:
          sWords = bOrdinal ? _itOrdinal (nAbs, sVariant) : _itCardinal (nAbs);
          break;
      }
    if (sToken.equals ("W"))
      sWords = sWords.toUpperCase (Locale.ROOT);
    else
      if (sToken.equals ("Ww"))
        sWords = titleCase (sWords);
    return n < 0 ? "-" + sWords : sWords;
  }

  /**
   * @param n
   *        Number.
   * @return The English ordinal suffix: st, nd, rd or th.
   */
  @NonNull
  public static String getOrdinalSuffix (final long n)
  {
    final long a = Math.abs (n % 100);
    if (a >= 11 && a <= 13)
      return "th";
    switch ((int) (a % 10))
    {
      case 1:
        return "st";
      case 2:
        return "nd";
      case 3:
        return "rd";
      default:
        return "th";
    }
  }
}
