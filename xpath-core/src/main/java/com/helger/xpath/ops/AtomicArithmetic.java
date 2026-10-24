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
package com.helger.xpath.ops;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.DateTimeOps;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;

/**
 * Arithmetic on atomic values (XPath 3.1 section 3.5, F&amp;O 3.1 sections 4.2,
 * 8.2 and 9.7): numeric type promotion, xs:integer/xs:decimal exactness,
 * the error codes FOAR0001/FOAR0002, and the date/time and duration
 * operators (delegated to the date/time workstream). Owner: atomic-values
 * workstream.
 * <p>
 * xs:decimal division is exact when the quotient has a terminating decimal
 * expansion; otherwise it is rounded (half up) to at least
 * {@value #DECIMAL_DIVISION_DIGITS} fraction digits and at least
 * {@value #DECIMAL_DIVISION_DIGITS} significant digits.
 *
 * @author Philip Helger
 */
@Immutable
public final class AtomicArithmetic
{
  /** Minimum number of digits of a non-terminating xs:decimal quotient. */
  public static final int DECIMAL_DIVISION_DIGITS = 18;

  @PresentForCodeCoverage
  private static final AtomicArithmetic INSTANCE = new AtomicArithmetic ();

  private AtomicArithmetic ()
  {}

  @NonNull
  private static XPathException _divByZero ()
  {
    return XPathException.of ("FOAR0001", "Division by zero");
  }

  @NonNull
  private static XdmAtomic _integerArithmetic (@NonNull final EBinaryOp eOp,
                                               @NonNull final BigInteger a,
                                               @NonNull final BigInteger b)
  {
    switch (eOp)
    {
      case ADD:
        return XdmAtomic.integer (a.add (b));
      case SUBTRACT:
        return XdmAtomic.integer (a.subtract (b));
      case MULTIPLY:
        return XdmAtomic.integer (a.multiply (b));
      case DIVIDE:
        if (b.signum () == 0)
          throw _divByZero ();
        return XdmAtomic.decimal (divideDecimal (new BigDecimal (a), new BigDecimal (b)));
      case INTEGER_DIVIDE:
        if (b.signum () == 0)
          throw _divByZero ();
        return XdmAtomic.integer (a.divide (b));
      case MODULO:
        if (b.signum () == 0)
          throw _divByZero ();
        return XdmAtomic.integer (a.remainder (b));
      default:
        throw new IllegalArgumentException ("Not an arithmetic operator: " + eOp);
    }
  }

  @NonNull
  private static XdmAtomic _decimalArithmetic (@NonNull final EBinaryOp eOp,
                                               @NonNull final BigDecimal a,
                                               @NonNull final BigDecimal b)
  {
    switch (eOp)
    {
      case ADD:
        return XdmAtomic.decimal (a.add (b));
      case SUBTRACT:
        return XdmAtomic.decimal (a.subtract (b));
      case MULTIPLY:
        return XdmAtomic.decimal (a.multiply (b));
      case DIVIDE:
        if (b.signum () == 0)
          throw _divByZero ();
        return XdmAtomic.decimal (divideDecimal (a, b));
      case INTEGER_DIVIDE:
        if (b.signum () == 0)
          throw _divByZero ();
        return XdmAtomic.integer (a.divideToIntegralValue (b).toBigInteger ());
      case MODULO:
        if (b.signum () == 0)
          throw _divByZero ();
        // a - b * trunc(a div b), exact
        return XdmAtomic.decimal (a.remainder (b));
      default:
        throw new IllegalArgumentException ("Not an arithmetic operator: " + eOp);
    }
  }

  @NonNull
  private static BigInteger _floatingIntegerDivide (final double a, final double b, final double dQuotient)
  {
    if (Double.isNaN (a) || Double.isNaN (b) || Double.isInfinite (a))
      throw XPathException.of ("FOAR0002", "Integer division with a NaN or infinite operand");
    if (b == 0)
      throw _divByZero ();
    if (Double.isInfinite (dQuotient))
      throw XPathException.of ("FOAR0002", "Integer division overflow");
    return AtomicCaster.truncateToBigInteger (dQuotient);
  }

  @NonNull
  private static XdmAtomic _doubleArithmetic (@NonNull final EBinaryOp eOp, final double a, final double b)
  {
    return switch (eOp)
    {
      case ADD -> XdmAtomic.dbl (a + b);
      case SUBTRACT -> XdmAtomic.dbl (a - b);
      case MULTIPLY -> XdmAtomic.dbl (a * b);
      case DIVIDE -> XdmAtomic.dbl (a / b);
      // Java's % is the IEEE truncated remainder, sign of the dividend
      case MODULO -> XdmAtomic.dbl (a % b);
      case INTEGER_DIVIDE -> XdmAtomic.integer (_floatingIntegerDivide (a, b, a / b));
      default -> throw new IllegalArgumentException ("Not an arithmetic operator: " + eOp);
    };
  }

  @NonNull
  private static XdmAtomic _floatArithmetic (@NonNull final EBinaryOp eOp, final float a, final float b)
  {
    return switch (eOp)
    {
      case ADD -> XdmAtomic.flt (a + b);
      case SUBTRACT -> XdmAtomic.flt (a - b);
      case MULTIPLY -> XdmAtomic.flt (a * b);
      case DIVIDE -> XdmAtomic.flt (a / b);
      case MODULO -> XdmAtomic.flt (a % b);
      case INTEGER_DIVIDE -> XdmAtomic.integer (_floatingIntegerDivide (a, b, a / b));
      default -> throw new IllegalArgumentException ("Not an arithmetic operator: " + eOp);
    };
  }

  @NonNull
  private static BigDecimal _toBigDecimal (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigDecimal aBD)
      return aBD;
    return new BigDecimal ((BigInteger) aPayload);
  }

  @NonNull
  private static XdmAtomic _numericArithmetic (@NonNull final EBinaryOp eOp,
                                               @NonNull final XdmAtomic aLeft,
                                               @NonNull final XdmAtomic aRight)
  {
    final EAtomicType eLeft = aLeft.getType ();
    final EAtomicType eRight = aRight.getType ();
    if (eLeft == EAtomicType.DOUBLE || eRight == EAtomicType.DOUBLE)
      return _doubleArithmetic (eOp, aLeft.getAsDouble (), aRight.getAsDouble ());
    if (eLeft == EAtomicType.FLOAT || eRight == EAtomicType.FLOAT)
      return _floatArithmetic (eOp, AtomicCaster.toFloat (aLeft), AtomicCaster.toFloat (aRight));
    final Object aL = aLeft.getValue ();
    final Object aR = aRight.getValue ();
    if (aL instanceof final BigInteger aLI && aR instanceof final BigInteger aRI)
      return _integerArithmetic (eOp, aLI, aRI);
    return _decimalArithmetic (eOp, _toBigDecimal (aLeft), _toBigDecimal (aRight));
  }

  /**
   * xs:decimal division: exact if the quotient terminates, otherwise rounded
   * half up to at least {@value #DECIMAL_DIVISION_DIGITS} fraction and
   * significant digits.
   *
   * @param a
   *        Dividend.
   * @param b
   *        Divisor, not zero.
   * @return The quotient.
   */
  @NonNull
  public static BigDecimal divideDecimal (@NonNull final BigDecimal a, @NonNull final BigDecimal b)
  {
    // A quotient terminates exactly when the reduced divisor's unscaled value
    // has no prime factors other than 2 and 5. Check that cheaply first, so
    // the exact division never runs into its exception path.
    BigInteger aDen = b.unscaledValue ().abs ();
    final BigInteger aGcd = aDen.gcd (a.unscaledValue ().abs ());
    if (aGcd.signum () != 0)
      aDen = aDen.divide (aGcd);
    final int nTwos = aDen.getLowestSetBit ();
    aDen = aDen.shiftRight (nTwos);
    final BigInteger aFive = BigInteger.valueOf (5);
    while (aDen.compareTo (BigInteger.ONE) > 0)
    {
      final BigInteger [] aQR = aDen.divideAndRemainder (aFive);
      if (aQR[1].signum () != 0)
        break;
      aDen = aQR[0];
    }
    if (aDen.equals (BigInteger.ONE))
      return a.divide (b);
    // Non-terminating: scale so that there are enough fraction digits and
    // enough significant digits
    final int nMagnitude = a.precision () - a.scale () - (b.precision () - b.scale ());
    final int nScale = Math.max (DECIMAL_DIVISION_DIGITS, DECIMAL_DIVISION_DIGITS - nMagnitude);
    return a.divide (b, nScale, RoundingMode.HALF_UP);
  }

  /**
   * @param eOp
   *        ADD, SUBTRACT, MULTIPLY, DIVIDE, INTEGER_DIVIDE or MODULO.
   * @param aLeft
   *        Left operand, untypedAtomic already cast to xs:double.
   * @param aRight
   *        Right operand, ditto.
   * @param aCtx
   *        For the implicit timezone in date/time subtraction.
   * @return The result.
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 for operand types without that operator.
   */
  @NonNull
  public static XdmAtomic arithmetic (@NonNull final EBinaryOp eOp,
                                      @NonNull final XdmAtomic aLeft,
                                      @NonNull final XdmAtomic aRight,
                                      @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aL = aLeft;
    XdmAtomic aR = aRight;
    // Defensive: the caller should have done this already
    if (aL.getType () == EAtomicType.UNTYPED_ATOMIC)
      aL = AtomicCaster.cast (aL, EAtomicType.DOUBLE, null);
    if (aR.getType () == EAtomicType.UNTYPED_ATOMIC)
      aR = AtomicCaster.cast (aR, EAtomicType.DOUBLE, null);
    final EAtomicType eLeft = aL.getType ();
    final EAtomicType eRight = aR.getType ();
    if (eLeft.isNumeric () && eRight.isNumeric ())
      return _numericArithmetic (eOp, aL, aR);
    if (eLeft.isDateTimeFamily () ||
        eLeft.isDurationFamily () ||
        eRight.isDateTimeFamily () ||
        eRight.isDurationFamily ())
    {
      final XdmAtomic ret = DateTimeOps.arithmetic (eOp, aL, aR, aCtx);
      if (ret != null)
        return ret;
    }
    throw XPathException.type ("Operator " +
                               eOp +
                               " is not defined for " +
                               eLeft.getQualifiedName () +
                               " and " +
                               eRight.getQualifiedName ());
  }

  /**
   * Unary minus on a numeric value. The result has the primitive numeric type
   * (xs:integer for all integer subtypes).
   *
   * @param aValue
   *        The operand, untypedAtomic already cast to xs:double.
   * @return The negated value.
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 for a non-numeric operand.
   */
  @NonNull
  public static XdmAtomic negate (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aValue.getType () == EAtomicType.UNTYPED_ATOMIC)
      return negate (AtomicCaster.cast (aValue, EAtomicType.DOUBLE, null));
    if (!aValue.isNumeric ())
      throw XPathException.type ("Unary minus is not defined for " + aValue.getType ().getQualifiedName ());
    if (aPayload instanceof final BigInteger aBI)
      return XdmAtomic.integer (aBI.negate ());
    if (aPayload instanceof final BigDecimal aBD)
      return XdmAtomic.decimal (aBD.negate ());
    if (aPayload instanceof final Float aFloat)
      return XdmAtomic.flt (-aFloat.floatValue ());
    return XdmAtomic.dbl (-aValue.getAsDouble ());
  }
}
