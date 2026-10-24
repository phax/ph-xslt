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

import java.math.BigInteger;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;

/**
 * The algorithm of <code>fn:format-integer</code> (F&amp;O 3.1 section 4.6) and
 * the numbering helpers shared with the date/time formatting: decimal digit
 * patterns in any Unicode digit family, Roman numerals, alphabetic sequences
 * and words.
 *
 * @author Philip Helger
 */
@Immutable
public final class FormatInteger
{
  /** A parsed decimal-digit-pattern. */
  public static final class DigitPattern
  {
    private int m_nMandatory;
    private int m_nZero = '0';
    private ICommonsMap <Integer, Integer> m_aSeps = new CommonsHashMap <> ();
    private int m_nRegular;
    private int m_nRegularSep;

    DigitPattern ()
    {}

    /** @return The number of mandatory digit positions. */
    public int getMandatory ()
    {
      return m_nMandatory;
    }

    /** @return The zero code point of the digit family. */
    public int getZero ()
    {
      return m_nZero;
    }

    /**
     * Insert the family digits and grouping separators.
     *
     * @param sAscii
     *        ASCII digits.
     * @return The rendered string.
     */
    @NonNull
    public String render (@NonNull final String sAscii)
    {
      final String sFam = mapDigitsToFamily (sAscii, m_nZero);
      final int [] aCPs = sFam.codePoints ().toArray ();
      final int n = aCPs.length;
      final StringBuilder aSB = new StringBuilder ();
      for (int i = 0; i < n; ++i)
      {
        final int nPosFromRight = n - i;
        if (i > 0)
        {
          final Integer aSep = m_aSeps.get (Integer.valueOf (nPosFromRight));
          if (aSep != null)
            aSB.appendCodePoint (aSep.intValue ());
          else
            if (m_nRegular > 0 && nPosFromRight % m_nRegular == 0)
              aSB.appendCodePoint (m_nRegularSep);
        }
        aSB.appendCodePoint (aCPs[i]);
      }
      return aSB.toString ();
    }
  }

  @PresentForCodeCoverage
  private static final FormatInteger INSTANCE = new FormatInteger ();

  private FormatInteger ()
  {}

  /**
   * @param c
   *        A code point.
   * @return <code>true</code> for a Unicode decimal digit (category Nd).
   */
  public static boolean isDigit (final int c)
  {
    return Character.getType (c) == Character.DECIMAL_DIGIT_NUMBER;
  }

  /**
   * @param c
   *        A code point.
   * @return <code>true</code> for any Unicode number (categories Nd, Nl, No).
   */
  public static boolean isNumber (final int c)
  {
    final int nType = Character.getType (c);
    return nType == Character.DECIMAL_DIGIT_NUMBER ||
           nType == Character.LETTER_NUMBER ||
           nType == Character.OTHER_NUMBER;
  }

  /**
   * @param c
   *        A Unicode decimal digit.
   * @return Its value 0..9.
   */
  public static int getDigitValue (final int c)
  {
    final int n = Character.digit (c, 10);
    return n < 0 ? 0 : n;
  }

  /**
   * @param sAscii
   *        String with ASCII digits.
   * @param nZero
   *        Zero code point of the target family.
   * @return The digits rewritten into the family.
   */
  @NonNull
  public static String mapDigitsToFamily (@NonNull final String sAscii, final int nZero)
  {
    if (nZero == '0')
      return sAscii;
    final StringBuilder aSB = new StringBuilder ();
    for (final char c : sAscii.toCharArray ())
      if (c >= '0' && c <= '9')
        aSB.appendCodePoint (nZero + (c - '0'));
      else
        aSB.append (c);
    return aSB.toString ();
  }

  /**
   * @param s
   *        A picture.
   * @return <code>true</code> if it contains a Unicode decimal digit, i.e. it
   *         is a decimal-digit-pattern (F&amp;O 4.6.1).
   */
  public static boolean isDigitPatternPicture (@NonNull final String s)
  {
    return s.codePoints ().anyMatch (FormatInteger::isDigit);
  }

  /**
   * Parse a decimal-digit-pattern.
   *
   * @param sPattern
   *        The pattern.
   * @return <code>null</code> if it is malformed (separator at an edge,
   *         adjacent separators, '#' after a mandatory digit, mixed digit
   *         families, no digit sign).
   */
  @Nullable
  public static DigitPattern parseDigitPattern (@NonNull final String sPattern)
  {
    final int [] aCPs = sPattern.codePoints ().toArray ();
    if (aCPs.length == 0)
      return null;
    int nTotal = 0;
    for (final int c : aCPs)
      if (c == '#' || isDigit (c))
        nTotal++;
    if (nTotal == 0)
      return null;
    final DigitPattern ret = new DigitPattern ();
    int nFamZero = -1;
    boolean bSawMandatory = false;
    int nDigitCount = 0;
    boolean bLastWasSep = false;
    final ICommonsMap <Integer, Integer> aSepAt = new CommonsHashMap <> ();
    final ICommonsList <Integer> aSepIntervals = new CommonsArrayList <> ();
    for (int i = 0; i < aCPs.length; ++i)
    {
      final int c = aCPs[i];
      if (c == '#')
      {
        if (bSawMandatory)
          return null;
        nDigitCount++;
        bLastWasSep = false;
      }
      else
        if (isDigit (c))
        {
          final int z = c - getDigitValue (c);
          if (nFamZero < 0)
            nFamZero = z;
          else
            if (z != nFamZero)
              return null;
          bSawMandatory = true;
          ret.m_nMandatory++;
          nDigitCount++;
          bLastWasSep = false;
        }
        else
        {
          if (i == 0 || i == aCPs.length - 1 || bLastWasSep || Character.isLetter (c) || isNumber (c))
            return null;
          final int nPosFromRight = nTotal - nDigitCount;
          aSepAt.put (Integer.valueOf (nPosFromRight), Integer.valueOf (c));
          aSepIntervals.add (Integer.valueOf (nPosFromRight));
          bLastWasSep = true;
        }
    }
    ret.m_nZero = nFamZero < 0 ? '0' : nFamZero;
    ret.m_aSeps = aSepAt;
    if (aSepIntervals.isNotEmpty ())
    {
      int g = Integer.MAX_VALUE;
      for (final Integer aIv : aSepIntervals)
        g = Math.min (g, aIv.intValue ());
      final Integer aFirstSep = aSepAt.get (aSepIntervals.getFirstOrNull ());
      boolean bSameRune = true;
      boolean bRegular = g > 0;
      for (final Integer aIv : aSepIntervals)
      {
        if (!aSepAt.get (aIv).equals (aFirstSep))
          bSameRune = false;
        if (g == 0 || aIv.intValue () % g != 0)
          bRegular = false;
      }
      if (bRegular && bSameRune)
      {
        int nWant = g;
        int nMaxSep = 0;
        for (final Integer aIv : aSepIntervals.getSorted (Integer::compare))
        {
          if (aIv.intValue () != nWant)
          {
            bRegular = false;
            break;
          }
          nWant += g;
          nMaxSep = aIv.intValue ();
        }
        if (bRegular && nTotal - nMaxSep > g)
          bRegular = false;
        if (bRegular)
        {
          ret.m_nRegular = g;
          ret.m_nRegularSep = aFirstSep.intValue ();
          ret.m_aSeps = new CommonsHashMap <> ();
        }
      }
    }
    return ret;
  }

  /**
   * @param n
   *        1..4999
   * @param bUpper
   *        Upper or lower case.
   * @return Roman numeral.
   */
  @NonNull
  public static String toRoman (final long n, final boolean bUpper)
  {
    final int [] aValues = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    final String [] aSyms = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
    long nRest = n;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aValues.length; ++i)
      while (nRest >= aValues[i])
      {
        aSB.append (aSyms[i]);
        nRest -= aValues[i];
      }
    return bUpper ? aSB.toString () : aSB.toString ().toLowerCase (Locale.ROOT);
  }

  /**
   * Bijective numeration over an alphabet: 1 is the first letter, len the
   * last, len+1 the first letter twice.
   *
   * @param n
   *        Number &gt;= 1.
   * @param aAlphabet
   *        Code points.
   * @return The sequence.
   */
  @NonNull
  public static String toAlphabetic (final long n, @NonNull final int [] aAlphabet)
  {
    final long nBase = aAlphabet.length;
    final ICommonsList <Integer> aRev = new CommonsArrayList <> ();
    long nRest = n;
    while (nRest > 0)
    {
      nRest--;
      aRev.add (Integer.valueOf (aAlphabet[(int) (nRest % nBase)]));
      nRest /= nBase;
    }
    final StringBuilder aSB = new StringBuilder ();
    for (int i = aRev.size () - 1; i >= 0; --i)
      aSB.appendCodePoint (aRev.get (i).intValue ());
    return aSB.toString ();
  }

  /**
   * @param bUpper
   *        Upper or lower case.
   * @return The Latin alphabet a..z.
   */
  @NonNull
  public static int [] getLatinAlphabet (final boolean bUpper)
  {
    final int [] ret = new int [26];
    for (int i = 0; i < 26; ++i)
      ret[i] = (bUpper ? 'A' : 'a') + i;
    return ret;
  }

  @NonNull
  private static int [] _greekAlphabet (final boolean bUpper)
  {
    final int [] ret = new int [24];
    int j = 0;
    for (int c = 0x391; c <= 0x3a9; ++c)
    {
      if (c == 0x3a2)
        continue;
      ret[j++] = bUpper ? c : c + 0x20;
    }
    return ret;
  }

  @NonNull
  private static String _kanji (final long n)
  {
    final String sDigits = "〇一二三四五六七八九";
    final long [] aUnits = { 1000, 100, 10 };
    final char [] aUnitChars = { '千', '百', '十' };
    long nRest = n;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aUnits.length; ++i)
    {
      final long d = nRest / aUnits[i];
      if (d > 0)
      {
        if (d > 1)
          aSB.append (sDigits.charAt ((int) d));
        aSB.append (aUnitChars[i]);
      }
      nRest %= aUnits[i];
    }
    if (nRest > 0)
      aSB.append (sDigits.charAt ((int) nRest));
    return aSB.toString ();
  }

  /**
   * Result of parsing a format-integer modifier.
   *
   * @param ordinal
   *        <code>true</code> for "o".
   * @param variant
   *        The text between the parentheses, or empty.
   */
  private record Modifier (boolean ordinal, String variant)
  {}

  /**
   * Validate the modifier against <code>^([co](\(.+\))?)?[at]?$</code>.
   */
  @NonNull
  private static Modifier _parseModifier (@NonNull final String sModifier)
  {
    String sRest = sModifier;
    boolean bOrdinal = false;
    String sVariant = "";
    if (!sRest.isEmpty () && (sRest.charAt (0) == 'c' || sRest.charAt (0) == 'o'))
    {
      bOrdinal = sRest.charAt (0) == 'o';
      sRest = sRest.substring (1);
      if (sRest.startsWith ("("))
      {
        if (sRest.length () >= 3 && sRest.endsWith (")"))
        {
          sVariant = sRest.substring (1, sRest.length () - 1);
          sRest = "";
        }
        else
          if (sRest.length () >= 4 && (sRest.endsWith (")a") || sRest.endsWith (")t")))
          {
            sVariant = sRest.substring (1, sRest.length () - 2);
            sRest = sRest.substring (sRest.length () - 1);
          }
          else
            throw new XPathException ("FODF1310", "Invalid format-integer modifier '" + sModifier + "'");
      }
    }
    if (!sRest.isEmpty () && !sRest.equals ("a") && !sRest.equals ("t"))
      throw new XPathException ("FODF1310", "Invalid format-integer modifier '" + sModifier + "'");
    return new Modifier (bOrdinal, sVariant);
  }

  /**
   * @return <code>[start, length]</code> of a supported Unicode numbering
   *         sequence whose first member is the picture, or <code>null</code>.
   */
  @Nullable
  private static int [] _unicodeNumberSequence (@NonNull final String sPrimary)
  {
    if (sPrimary.codePointCount (0, sPrimary.length ()) != 1)
      return null;
    switch (sPrimary.codePointAt (0))
    {
      case 0x2460:
        // circled digit one
        return new int [] { 0x2460, 20 };
      case 0x2474:
        // parenthesised digit one
        return new int [] { 0x2474, 20 };
      case 0x2488:
        // digit one full stop
        return new int [] { 0x2488, 20 };
      default:
        return null;
    }
  }

  /**
   * Format an integer.
   *
   * @param aValue
   *        The value.
   * @param sPicture
   *        The picture.
   * @param sLanguage
   *        Language for words, or <code>null</code> for the default.
   * @return The formatted integer.
   * @throws XPathException
   *         FODF1310 for an invalid picture.
   */
  @NonNull
  public static String format (@NonNull final BigInteger aValue,
                               @NonNull final String sPicture,
                               @Nullable final String sLanguage)
  {
    if (sPicture.isEmpty ())
      throw new XPathException ("FODF1310", "The format-integer picture must not be empty");
    String sPrimary = sPicture;
    String sModifier = "";
    final int nSemi = sPicture.lastIndexOf (';');
    if (nSemi >= 0)
    {
      sPrimary = sPicture.substring (0, nSemi);
      sModifier = sPicture.substring (nSemi + 1);
    }
    if (sPrimary.isEmpty ())
      throw new XPathException ("FODF1310", "The format-integer primary format token must not be empty");
    final Modifier aMod = _parseModifier (sModifier);
    final boolean bOrdinal = aMod.ordinal ();
    final BigInteger aAbs = aValue.abs ();
    final String sSign = aValue.signum () < 0 ? "-" : "";
    final boolean bFits = aAbs.bitLength () < 64;
    final long n64 = bFits ? aAbs.longValue () : 0;
    switch (sPrimary)
    {
      case "I":
      case "i":
        if (bFits && n64 > 0 && n64 < 5000)
          return sSign + toRoman (n64, sPrimary.equals ("I"));
        break;
      case "A":
      case "a":
        if (bFits && n64 > 0)
          return sSign + toAlphabetic (n64, getLatinAlphabet (sPrimary.equals ("A")));
        break;
      case "w":
      case "W":
      case "Ww":
        if (bFits)
          return sSign + FormatWords.spell (n64, sPrimary, bOrdinal, sLanguage, aMod.variant ());
        break;
      case "\u0391":
      case "\u03b1":
        if (bFits && n64 > 0)
          return sSign + toAlphabetic (n64, _greekAlphabet (sPrimary.equals ("\u0391")));
        break;
      case "\u4e00":
        if (bFits && n64 > 0 && n64 < 10000)
          return sSign + _kanji (n64);
        break;
      default:
        break;
    }
    final int [] aSeq = _unicodeNumberSequence (sPrimary);
    if (aSeq != null && bFits && n64 >= 1 && n64 <= aSeq[1])
      return sSign + new String (Character.toChars (aSeq[0] + (int) n64 - 1));
    final DigitPattern aDP = parseDigitPattern (sPrimary);
    if (aDP != null)
    {
      String sDigits = aAbs.toString ();
      if (sDigits.length () < aDP.getMandatory ())
        sDigits = "0".repeat (aDP.getMandatory () - sDigits.length ()) + sDigits;
      String sOut = aDP.render (sDigits);
      if (bOrdinal && bFits)
        sOut += FormatWords.getOrdinalSuffix (n64);
      return sSign + sOut;
    }
    if (isDigitPatternPicture (sPrimary))
      throw new XPathException ("FODF1310", "Invalid format-integer picture '" + sPrimary + "'");
    // Any other token is formatted as if the picture were "1"
    String sOut = aAbs.toString ();
    if (bOrdinal && bFits)
      sOut += FormatWords.getOrdinalSuffix (n64);
    return sSign + sOut;
  }
}
