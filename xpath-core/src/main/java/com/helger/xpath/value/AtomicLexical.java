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
package com.helger.xpath.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ops.XsdLexical;
import com.helger.xpath.xdm.XdmQName;

/**
 * Canonical lexical forms of atomic values (XPath F&amp;O 3.1 section 19,
 * casting to xs:string). Owner: atomic-values workstream. Double and float
 * use the XPath rules (<code>1.0E10</code>, <code>-0</code>, <code>INF</code>,
 * <code>NaN</code>, plain notation between 1.0E-6 and 1.0E6), not
 * {@link Double#toString(double)}.
 *
 * @author Philip Helger
 */
@Immutable
public final class AtomicLexical
{
  @PresentForCodeCoverage
  private static final AtomicLexical INSTANCE = new AtomicLexical ();

  private AtomicLexical ()
  {}

  /**
   * Render the shortest round-trip digits Java produced (in
   * {@link Double#toString(double)} / {@link Float#toString(float)} notation
   * for a positive finite value) in the XPath canonical form.
   *
   * @param bNegative
   *        Whether a minus sign is needed.
   * @param sJava
   *        The Java representation of the absolute value.
   * @param bPlain
   *        <code>true</code> for plain decimal notation, <code>false</code>
   *        for the mantissa/exponent notation.
   * @return The canonical form.
   */
  @NonNull
  private static String _formatFloating (final boolean bNegative, @NonNull final String sJava, final boolean bPlain)
  {
    final int nE = sJava.indexOf ('E');
    final String sMantissa = nE < 0 ? sJava : sJava.substring (0, nE);
    final int nExp = nE < 0 ? 0 : Integer.parseInt (sJava.substring (nE + 1));
    final int nDot = sMantissa.indexOf ('.');
    final String sIntPart = nDot < 0 ? sMantissa : sMantissa.substring (0, nDot);
    final String sFracPart = nDot < 0 ? "" : sMantissa.substring (nDot + 1);
    String sDigits = sIntPart + sFracPart;
    // Position of the decimal point relative to the start of sDigits
    int nPoint = sIntPart.length () + nExp;
    int nLead = 0;
    while (nLead < sDigits.length () - 1 && sDigits.charAt (nLead) == '0')
      nLead++;
    sDigits = sDigits.substring (nLead);
    nPoint -= nLead;
    int nTrail = sDigits.length ();
    while (nTrail > 1 && sDigits.charAt (nTrail - 1) == '0')
      nTrail--;
    sDigits = sDigits.substring (0, nTrail);

    final StringBuilder aSB = new StringBuilder (sDigits.length () + 10);
    if (bNegative)
      aSB.append ('-');
    if (bPlain)
    {
      if (nPoint <= 0)
      {
        aSB.append ("0.");
        for (int i = nPoint; i < 0; ++i)
          aSB.append ('0');
        aSB.append (sDigits);
      }
      else
        if (nPoint >= sDigits.length ())
        {
          aSB.append (sDigits);
          for (int i = sDigits.length (); i < nPoint; ++i)
            aSB.append ('0');
        }
        else
          aSB.append (sDigits, 0, nPoint).append ('.').append (sDigits, nPoint, sDigits.length ());
    }
    else
    {
      aSB.append (sDigits.charAt (0)).append ('.');
      if (sDigits.length () > 1)
        aSB.append (sDigits, 1, sDigits.length ());
      else
        aSB.append ('0');
      aSB.append ('E').append (nPoint - 1);
    }
    return aSB.toString ();
  }

  /**
   * @param d
   *        The value.
   * @return The canonical xs:double lexical form (F&amp;O 3.1 section
   *         19.1.2.2), with the shortest digits that round-trip.
   */
  @NonNull
  public static String formatDouble (final double d)
  {
    if (Double.isNaN (d))
      return "NaN";
    if (d == Double.POSITIVE_INFINITY)
      return "INF";
    if (d == Double.NEGATIVE_INFINITY)
      return "-INF";
    if (d == 0)
      return Double.doubleToRawLongBits (d) < 0 ? "-0" : "0";
    final double dAbs = Math.abs (d);
    return _formatFloating (d < 0, Double.toString (dAbs), dAbs >= 1e-6 && dAbs < 1e6);
  }

  /**
   * @param f
   *        The value.
   * @return The canonical xs:float lexical form, with the shortest digits that
   *         round-trip at single precision.
   */
  @NonNull
  public static String formatFloat (final float f)
  {
    if (Float.isNaN (f))
      return "NaN";
    if (f == Float.POSITIVE_INFINITY)
      return "INF";
    if (f == Float.NEGATIVE_INFINITY)
      return "-INF";
    if (f == 0)
      return Float.floatToRawIntBits (f) < 0 ? "-0" : "0";
    final float fAbs = Math.abs (f);
    final double dAbs = fAbs;
    return _formatFloating (f < 0, Float.toString (fAbs), dAbs >= 1e-6 && dAbs < 1e6);
  }

  /**
   * @param aValue
   *        The value.
   * @return The canonical xs:decimal lexical form: no exponent, no trailing
   *         fraction zeros, no decimal point for integral values.
   */
  @NonNull
  public static String formatDecimal (@NonNull final BigDecimal aValue)
  {
    if (aValue.signum () == 0)
      return "0";
    if (aValue.scale () <= 0)
      return aValue.toBigInteger ().toString ();
    return aValue.stripTrailingZeros ().toPlainString ();
  }

  @NonNull
  public static String toLexical (@NonNull final XdmAtomic aValue)
  {
    final EAtomicType eType = aValue.getType ();
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final String sValue)
      return sValue;
    if (aPayload instanceof final Boolean aBool)
      return aBool.booleanValue () ? "true" : "false";
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.toString ();
    if (aPayload instanceof final BigDecimal aBigDec)
      return formatDecimal (aBigDec);
    if (aPayload instanceof final Double aDouble)
      return formatDouble (aDouble.doubleValue ());
    if (aPayload instanceof final Float aFloat)
      return formatFloat (aFloat.floatValue ());
    if (aPayload instanceof final XdmQName aQName)
      return aQName.getLexicalName ();
    if (aPayload instanceof final byte [] aBytes)
      return eType == EAtomicType.BASE64_BINARY ? Base64.getEncoder ().encodeToString (aBytes)
                                                : XsdLexical.encodeHex (aBytes);
    if (aPayload instanceof final XdmDateTimeValue aDT)
      return aDT.toLexical (eType);
    if (aPayload instanceof final XdmDurationValue aDur)
      return aDur.toLexical (eType);
    throw new IllegalStateException ("Unexpected payload " + aPayload.getClass () + " for " + eType);
  }
}
