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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmAtomic;

/**
 * The algorithm of <code>fn:format-number</code> (F&amp;O 3.1 section 4.7):
 * picture validation (FODF1310), sub-picture analysis and rendering of plain
 * and exponential notation, with exact decimal arithmetic.
 *
 * @author Philip Helger
 */
@Immutable
public final class FormatNumber
{
  @PresentForCodeCoverage
  private static final FormatNumber INSTANCE = new FormatNumber ();

  /**
   * A format-number input: an exact non-negative magnitude with its sign, or a
   * non-finite value.
   */
  private static final class Input
  {
    private BigDecimal m_aAbs;
    private boolean m_bNeg;
    private boolean m_bNaN;
    private boolean m_bInf;
    private boolean m_bFloat;
    private double m_dAbs;
    private boolean m_bSingle;
  }

  /** The analysis of one sub-picture (F&amp;O 4.7.4). */
  private static final class SubPicture
  {
    private String m_sPrefix = "";
    private String m_sSuffix = "";
    private int m_nMinInt;
    private int m_nScale;
    private int m_nMinFrac;
    private int m_nMaxFrac;
    private int m_nGroupInt;
    private ICommonsSet <Integer> m_aGroupSeps = new CommonsHashSet <> ();
    private final ICommonsList <Integer> m_aFracSeps = new CommonsArrayList <> ();
    private int m_nMult = 1;
    private boolean m_bHasExp;
    private int m_nExpChar;
    private int m_nMinExp;
  }

  private FormatNumber ()
  {}

  @NonNull
  private static XPathException _err (@NonNull final String sMsg, @NonNull final String sPicture)
  {
    return new XPathException ("FODF1310", sMsg + " in picture '" + sPicture + "'");
  }

  @NonNull
  private static Input _fromFloating (final double d, final boolean bSingle)
  {
    final Input ret = new Input ();
    if (Double.isNaN (d))
    {
      ret.m_bNaN = true;
      return ret;
    }
    if (Double.isInfinite (d))
    {
      ret.m_bInf = true;
      ret.m_bNeg = d < 0;
      return ret;
    }
    ret.m_bNeg = Double.doubleToRawLongBits (d) < 0;
    ret.m_bFloat = true;
    ret.m_bSingle = bSingle;
    ret.m_dAbs = Math.abs (d);
    ret.m_aAbs = bSingle ? new BigDecimal (Float.toString ((float) ret.m_dAbs))
                         : new BigDecimal (Double.toString (ret.m_dAbs));
    return ret;
  }

  @NonNull
  private static Input _input (final XdmAtomic aValue)
  {
    if (aValue == null)
    {
      final Input ret = new Input ();
      ret.m_bNaN = true;
      return ret;
    }
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBI)
    {
      final Input ret = new Input ();
      ret.m_aAbs = new BigDecimal (aBI.abs ());
      ret.m_bNeg = aBI.signum () < 0;
      return ret;
    }
    if (aPayload instanceof final BigDecimal aBD)
    {
      final Input ret = new Input ();
      ret.m_aAbs = aBD.abs ();
      ret.m_bNeg = aBD.signum () < 0;
      return ret;
    }
    if (aPayload instanceof final Float aFloat)
      return _fromFloating (aFloat.doubleValue (), true);
    return _fromFloating (aValue.getAsDouble (), false);
  }

  private static int _count (@NonNull final int [] aCPs, final int c)
  {
    int ret = 0;
    for (final int x : aCPs)
      if (x == c)
        ret++;
    return ret;
  }

  private static boolean _contains (@NonNull final int [] aCPs, final int c)
  {
    for (final int x : aCPs)
      if (x == c)
        return true;
    return false;
  }

  /** @return One or two sub-pictures, split at the first pattern separator. */
  @NonNull
  private static int [] [] _split (@NonNull final int [] aCPs, final int nSep)
  {
    for (int i = 0; i < aCPs.length; ++i)
      if (aCPs[i] == nSep)
        return new int [] [] { Arrays.copyOfRange (aCPs, 0, i), Arrays.copyOfRange (aCPs, i + 1, aCPs.length) };
    return new int [] [] { aCPs };
  }

  private static boolean _isDigitSign (final int c, @NonNull final FormatDecimalFormat aDF)
  {
    return aDF.isDecimalDigit (c) || c == aDF.getDigit ();
  }

  private static boolean _isActive (final int c, @NonNull final FormatDecimalFormat aDF)
  {
    return _isDigitSign (c, aDF) || c == aDF.getDecimalSeparator () || c == aDF.getGroupingSeparator ();
  }

  /**
   * @return <code>[index, count]</code> of the exponent separator: a character
   *         matching the exponent-separator property that is preceded and
   *         followed by an active character somewhere in the sub-picture.
   */
  @NonNull
  private static int [] _expSepIndex (@NonNull final int [] aCPs, @NonNull final FormatDecimalFormat aDF)
  {
    int nIdx = -1;
    int nCount = 0;
    for (int i = 0; i < aCPs.length; ++i)
    {
      if (aCPs[i] != aDF.getExponentSeparator ())
        continue;
      boolean bBefore = false;
      boolean bAfter = false;
      for (int j = 0; j < i; ++j)
        if (_isActive (aCPs[j], aDF))
        {
          bBefore = true;
          break;
        }
      for (int j = i + 1; j < aCPs.length; ++j)
        if (_isActive (aCPs[j], aDF))
        {
          bAfter = true;
          break;
        }
      if (bBefore && bAfter)
      {
        if (nIdx < 0)
          nIdx = i;
        nCount++;
      }
    }
    return new int [] { nIdx, nCount };
  }

  private static void _validateSubPictureShape (@NonNull final int [] aCPs,
                                                @NonNull final FormatDecimalFormat aDF,
                                                @NonNull final String sPicture)
  {
    final int [] aExp = _expSepIndex (aCPs, aDF);
    final int nExpIdx = aExp[0];
    if (aExp[1] > 1)
      throw _err ("More than one exponent separator", sPicture);
    // A passive character between two active characters is invalid
    int nLastActive = -1;
    boolean bPassiveSince = false;
    for (int i = 0; i < aCPs.length; ++i)
    {
      if (_isActive (aCPs[i], aDF) || i == nExpIdx)
      {
        if (nLastActive >= 0 && bPassiveSince)
          throw _err ("Passive character inside the number", sPicture);
        nLastActive = i;
        bPassiveSince = false;
        continue;
      }
      if (nLastActive >= 0)
        bPassiveSince = true;
    }
    int nMantEnd = aCPs.length;
    if (nExpIdx >= 0)
    {
      nMantEnd = nExpIdx;
      if (_contains (aCPs, aDF.getPercent ()) || _contains (aCPs, aDF.getPerMille ()))
        throw _err ("Percent or per-mille with an exponent", sPicture);
      int nExpDigits = 0;
      for (int i = nExpIdx + 1; i < aCPs.length; ++i)
        if (aDF.isDecimalDigit (aCPs[i]))
          nExpDigits++;
        else
          if (_isActive (aCPs[i], aDF))
            throw _err ("Exponent part must contain only digits", sPicture);
      if (nExpDigits == 0)
        throw _err ("Exponent separator without exponent digits", sPicture);
    }
    boolean bMantissaDigit = false;
    for (int i = 0; i < nMantEnd; ++i)
      if (_isDigitSign (aCPs[i], aDF))
        bMantissaDigit = true;
    if (!bMantissaDigit)
      throw _err ("Mantissa has no digit sign", sPicture);
    int nDec = -1;
    for (int i = 0; i < nMantEnd; ++i)
      if (aCPs[i] == aDF.getDecimalSeparator ())
      {
        nDec = i;
        break;
      }
    boolean bInFrac = false;
    boolean bSeenZeroInt = false;
    boolean bSeenOptFrac = false;
    for (int i = 0; i < nMantEnd; ++i)
    {
      final int c = aCPs[i];
      if (c == aDF.getDecimalSeparator ())
      {
        bInFrac = true;
        continue;
      }
      if (c == aDF.getGroupingSeparator ())
      {
        if (i + 1 < aCPs.length && aCPs[i + 1] == aDF.getGroupingSeparator ())
          throw _err ("Adjacent grouping separators", sPicture);
        if (nDec >= 0 && (i == nDec - 1 || i == nDec + 1))
          throw _err ("Grouping separator adjacent to decimal separator", sPicture);
        if (!bInFrac)
        {
          final int nIntEnd = nDec >= 0 ? nDec : nMantEnd;
          boolean bFollowed = false;
          for (int j = i + 1; j < nIntEnd; ++j)
            if (_isDigitSign (aCPs[j], aDF))
            {
              bFollowed = true;
              break;
            }
          if (!bFollowed)
            throw _err ("Grouping separator at the end of the integer part", sPicture);
        }
      }
      else
        if (aDF.isDecimalDigit (c) && !bInFrac)
          bSeenZeroInt = true;
        else
          if (c == aDF.getDigit () && !bInFrac)
          {
            if (bSeenZeroInt)
              throw _err ("Optional digit after mandatory digit in the integer part", sPicture);
          }
          else
            if (c == aDF.getDigit () && bInFrac)
              bSeenOptFrac = true;
            else
              if (aDF.isDecimalDigit (c) && bInFrac && bSeenOptFrac)
                throw _err ("Mandatory digit after optional digit in the fractional part", sPicture);
    }
  }

  /**
   * Check a picture for the FODF1310 conditions.
   *
   * @param sPicture
   *        The picture.
   * @param aDF
   *        The decimal format.
   * @throws XPathException
   *         FODF1310
   */
  private static void _validatePicture (@NonNull final String sPicture, @NonNull final FormatDecimalFormat aDF)
  {
    final int [] aCPs = sPicture.codePoints ().toArray ();
    if (_count (aCPs, aDF.getPatternSeparator ()) > 1)
      throw _err ("Too many sub-pictures", sPicture);
    for (final int [] aSub : _split (aCPs, aDF.getPatternSeparator ()))
    {
      boolean bHasDigit = false;
      int nDecSeps = 0;
      for (final int c : aSub)
        if (_isDigitSign (c, aDF))
          bHasDigit = true;
        else
          if (c == aDF.getDecimalSeparator ())
            nDecSeps++;
      if (!bHasDigit)
        throw _err ("Sub-picture has no digit sign", sPicture);
      if (nDecSeps > 1)
        throw _err ("Multiple decimal separators", sPicture);
      final int nPct = _count (aSub, aDF.getPercent ());
      final int nPerMille = _count (aSub, aDF.getPerMille ());
      if (nPct > 0 && nPerMille > 0)
        throw _err ("Both percent and per-mille", sPicture);
      if (nPct > 1 || nPerMille > 1)
        throw _err ("Multiple percent or per-mille signs", sPicture);
      _validateSubPictureShape (aSub, aDF, sPicture);
    }
  }

  @NonNull
  private static String _str (@NonNull final int [] aCPs, final int nFrom, final int nTo)
  {
    return new String (aCPs, nFrom, nTo - nFrom);
  }

  /**
   * @return The regular grouping interval, or 0 if the separators are
   *         positional.
   */
  private static int _classifyGrouping (@NonNull final ICommonsList <Integer> aPos, final int nTotalIntDigits)
  {
    if (aPos.isEmpty ())
      return 0;
    final ICommonsList <Integer> aSorted = aPos.getSorted (Integer::compare);
    final int g = aSorted.getFirstOrNull ().intValue ();
    if (g <= 0)
      return 0;
    int nWant = g;
    int nMaxSep = 0;
    for (final Integer aIv : aSorted)
    {
      if (aIv.intValue () != nWant)
        return 0;
      nWant += g;
      nMaxSep = aIv.intValue ();
    }
    if (nTotalIntDigits - nMaxSep > g)
      return 0;
    return g;
  }

  @NonNull
  private static SubPicture _parseSubPicture (@NonNull final int [] aCPs, @NonNull final FormatDecimalFormat aDF)
  {
    final SubPicture ret = new SubPicture ();
    final int nExpIdx = _expSepIndex (aCPs, aDF)[0];
    if (_contains (aCPs, aDF.getPercent ()))
      ret.m_nMult = 100;
    else
      if (_contains (aCPs, aDF.getPerMille ()))
        ret.m_nMult = 1000;
    int nFirst = -1;
    int nLast = -1;
    for (int i = 0; i < aCPs.length; ++i)
      if (_isActive (aCPs[i], aDF) || i == nExpIdx)
      {
        if (nFirst < 0)
          nFirst = i;
        nLast = i;
      }
    if (nFirst < 0)
    {
      ret.m_sPrefix = _str (aCPs, 0, aCPs.length);
      ret.m_nMinInt = 1;
      return ret;
    }
    ret.m_sPrefix = _str (aCPs, 0, nFirst);
    ret.m_sSuffix = _str (aCPs, nLast + 1, aCPs.length);
    int nMantEnd = nLast + 1;
    if (nExpIdx >= 0)
    {
      nMantEnd = nExpIdx;
      ret.m_bHasExp = true;
      ret.m_nExpChar = aCPs[nExpIdx];
      for (int i = nExpIdx + 1; i <= nLast; ++i)
        if (aDF.isDecimalDigit (aCPs[i]))
          ret.m_nMinExp++;
    }
    boolean bInFrac = false;
    int nIntDigits = 0;
    int nOptInt = 0;
    final ICommonsList <Integer> aSepLeftDigits = new CommonsArrayList <> ();
    for (int i = nFirst; i < nMantEnd; ++i)
    {
      final int c = aCPs[i];
      if (c == aDF.getDecimalSeparator ())
        bInFrac = true;
      else
        if (c == aDF.getGroupingSeparator ())
        {
          if (bInFrac)
            ret.m_aFracSeps.add (Integer.valueOf (ret.m_nMaxFrac));
          else
            aSepLeftDigits.add (Integer.valueOf (nIntDigits));
        }
        else
          if (_isDigitSign (c, aDF))
          {
            if (!bInFrac)
            {
              nIntDigits++;
              if (aDF.isDecimalDigit (c))
                ret.m_nMinInt++;
              else
                nOptInt++;
            }
            else
            {
              ret.m_nMaxFrac++;
              if (aDF.isDecimalDigit (c))
                ret.m_nMinFrac++;
            }
          }
    }
    ret.m_nScale = ret.m_nMinInt;
    if (ret.m_nMinInt == 0 && ret.m_nMaxFrac == 0)
    {
      if (ret.m_bHasExp)
      {
        ret.m_nMinFrac = 1;
        ret.m_nMaxFrac = 1;
      }
      else
        ret.m_nMinInt = 1;
    }
    if (ret.m_bHasExp && ret.m_nMinInt == 0 && nOptInt > 0)
      ret.m_nMinInt = 1;
    if (ret.m_nMinInt == 0 && ret.m_nMinFrac == 0)
      ret.m_nMinFrac = 1;
    if (aSepLeftDigits.isNotEmpty ())
    {
      final ICommonsList <Integer> aPosFromRight = new CommonsArrayList <> ();
      for (final Integer aLeft : aSepLeftDigits)
      {
        final int p = nIntDigits - aLeft.intValue ();
        if (p > 0)
          aPosFromRight.add (Integer.valueOf (p));
      }
      final int g = _classifyGrouping (aPosFromRight, nIntDigits);
      if (g > 0)
        ret.m_nGroupInt = g;
      else
        ret.m_aGroupSeps = new CommonsHashSet <> (aPosFromRight);
    }
    return ret;
  }

  @NonNull
  private static String _dfDigits (@NonNull final String s, @NonNull final FormatDecimalFormat aDF)
  {
    if (aDF.getZeroDigit () == '0')
      return s;
    final StringBuilder aSB = new StringBuilder ();
    for (final char c : s.toCharArray ())
      if (c >= '0' && c <= '9')
        aSB.appendCodePoint (aDF.getZeroDigit () + (c - '0'));
      else
        aSB.append (c);
    return aSB.toString ();
  }

  @NonNull
  private static String _group (@NonNull final String sDigits, final int nSize, final int nSep)
  {
    final int [] aCPs = sDigits.codePoints ().toArray ();
    if (nSize <= 0 || aCPs.length <= nSize)
      return sDigits;
    final StringBuilder aSB = new StringBuilder ();
    final int n = aCPs.length;
    for (int i = 0; i < n; ++i)
    {
      final int nPosFromRight = n - i;
      if (i > 0 && nPosFromRight % nSize == 0)
        aSB.appendCodePoint (nSep);
      aSB.appendCodePoint (aCPs[i]);
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _groupPositional (@NonNull final String sDigits,
                                          @NonNull final ICommonsSet <Integer> aSeps,
                                          final int nSep)
  {
    final int [] aCPs = sDigits.codePoints ().toArray ();
    final int n = aCPs.length;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < n; ++i)
    {
      if (i > 0 && aSeps.contains (Integer.valueOf (n - i)))
        aSB.appendCodePoint (nSep);
      aSB.appendCodePoint (aCPs[i]);
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _groupFrac (@NonNull final String sDigits,
                                    @NonNull final ICommonsList <Integer> aPositions,
                                    final int nSep)
  {
    if (aPositions.isEmpty ())
      return sDigits;
    final int [] aCPs = sDigits.codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aCPs.length; ++i)
    {
      if (i > 0 && aPositions.contains (Integer.valueOf (i)))
        aSB.appendCodePoint (nSep);
      aSB.appendCodePoint (aCPs[i]);
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _repeat (final char c, final int n)
  {
    return n <= 0 ? "" : String.valueOf (c).repeat (n);
  }

  /**
   * Round half-to-even at maxFrac places and render with minimum integer and
   * fraction digits, the format's digit family and grouping.
   */
  @NonNull
  private static String _renderMantissa (@NonNull final BigDecimal aAbs,
                                         @NonNull final SubPicture aSP,
                                         @NonNull final FormatDecimalFormat aDF)
  {
    final BigDecimal aRounded = aAbs.setScale (aSP.m_nMaxFrac, RoundingMode.HALF_EVEN);
    final BigInteger aUnscaled = aRounded.unscaledValue ();
    final BigInteger [] aQR = aUnscaled.divideAndRemainder (BigInteger.TEN.pow (aSP.m_nMaxFrac));
    String sInt = aQR[0].signum () != 0 ? aQR[0].toString () : "";
    String sFrac = "";
    if (aSP.m_nMaxFrac > 0)
    {
      sFrac = aQR[1].toString ();
      sFrac = _repeat ('0', aSP.m_nMaxFrac - sFrac.length ()) + sFrac;
      int nEnd = sFrac.length ();
      while (nEnd > 0 && sFrac.charAt (nEnd - 1) == '0')
        nEnd--;
      sFrac = sFrac.substring (0, nEnd);
    }
    sInt = _repeat ('0', aSP.m_nMinInt - sInt.length ()) + sInt;
    sFrac = sFrac + _repeat ('0', aSP.m_nMinFrac - sFrac.length ());
    sInt = _dfDigits (sInt, aDF);
    if (aSP.m_nGroupInt > 0)
      sInt = _group (sInt, aSP.m_nGroupInt, aDF.getGroupingSeparator ());
    else
      if (aSP.m_aGroupSeps.isNotEmpty ())
        sInt = _groupPositional (sInt, aSP.m_aGroupSeps, aDF.getGroupingSeparator ());
    if (sFrac.isEmpty ())
      return sInt;
    return sInt +
           new String (Character.toChars (aDF.getDecimalSeparator ())) +
           _groupFrac (_dfDigits (sFrac, aDF), aSP.m_aFracSeps, aDF.getGroupingSeparator ());
  }

  @NonNull
  private static String _renderScientific (@NonNull final BigDecimal aAbs,
                                           @NonNull final SubPicture aSP,
                                           @NonNull final FormatDecimalFormat aDF)
  {
    int nExp = 0;
    BigDecimal aMant = aAbs;
    if (aAbs.signum () != 0)
    {
      // Number of integer digits: 10^(k-1) <= v < 10^k
      final int k = aAbs.precision () - aAbs.scale ();
      nExp = k - aSP.m_nScale;
      aMant = aAbs.movePointLeft (nExp);
    }
    final String sOut = _renderMantissa (aMant, aSP, aDF);
    String sExp = Integer.toString (Math.abs (nExp));
    final String sSign = nExp < 0 ? new String (Character.toChars (aDF.getMinusSign ())) : "";
    sExp = _repeat ('0', aSP.m_nMinExp - sExp.length ()) + sExp;
    return sOut + new String (Character.toChars (aSP.m_nExpChar)) + sSign + _dfDigits (sExp, aDF);
  }

  /**
   * Format a number.
   *
   * @param aValue
   *        The numeric value or <code>null</code> for the empty sequence (which
   *        formats as NaN).
   * @param sPicture
   *        The picture string.
   * @param aDF
   *        The decimal format.
   * @return The formatted number.
   * @throws XPathException
   *         FODF1310 for an invalid picture.
   */
  @NonNull
  public static String format (final XdmAtomic aValue,
                               @NonNull final String sPicture,
                               @NonNull final FormatDecimalFormat aDF)
  {
    _validatePicture (sPicture, aDF);
    final Input aIn = _input (aValue);
    if (aIn.m_bNaN)
      return aDF.getNaN ();
    final int [] [] aSubs = _split (sPicture.codePoints ().toArray (), aDF.getPatternSeparator ());
    SubPicture aSP = _parseSubPicture (aSubs[0], aDF);
    boolean bSoleNeg = false;
    if (aIn.m_bNeg)
    {
      if (aSubs.length == 2)
        aSP = _parseSubPicture (aSubs[1], aDF);
      else
        bSoleNeg = true;
    }
    final String sMinus = new String (Character.toChars (aDF.getMinusSign ()));
    // A double is scaled by percent/per-mille in floating point arithmetic
    // (and may become infinite), decimals are scaled exactly
    if (!aIn.m_bInf && aIn.m_bFloat && aSP.m_nMult != 1)
    {
      final double dScaled = aIn.m_dAbs * aSP.m_nMult;
      final Input aScaled = _fromFloating (aIn.m_bSingle ? (float) dScaled : dScaled, aIn.m_bSingle);
      aIn.m_aAbs = aScaled.m_aAbs;
      aIn.m_bInf = aScaled.m_bInf;
    }
    if (aIn.m_bInf)
    {
      final String sOut = aSP.m_sPrefix + aDF.getInfinity () + aSP.m_sSuffix;
      return bSoleNeg ? sMinus + sOut : sOut;
    }
    BigDecimal aAbs = aIn.m_aAbs;
    if (aSP.m_nMult != 1 && !aIn.m_bFloat)
      aAbs = aAbs.multiply (BigDecimal.valueOf (aSP.m_nMult));
    final String sBody = aSP.m_bHasExp ? _renderScientific (aAbs, aSP, aDF) : _renderMantissa (aAbs, aSP, aDF);
    final String sOut = aSP.m_sPrefix + sBody + aSP.m_sSuffix;
    return bSoleNeg ? sMinus + sOut : sOut;
  }
}
