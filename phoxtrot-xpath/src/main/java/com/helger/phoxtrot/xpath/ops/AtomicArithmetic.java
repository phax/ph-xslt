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
package com.helger.phoxtrot.xpath.ops;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.value.DateTimeOps;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;

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

  /**
   * @return A new FOAR0001 "Division by zero" error, shared by all numeric
   *         operators that divide.
   */
  @NonNull
  private static XPathException _divByZero ()
  {
    return XPathException.of ("FOAR0001", "Division by zero");
  }

  /**
   * The <code>long</code> fast path of
   * {@link #_integerArithmetic(EBinaryOp, BigInteger, BigInteger)} for
   * <code>+</code>, <code>-</code>, <code>*</code>, <code>idiv</code> and
   * <code>mod</code> on operands that fit into a <code>long</code>: the exact
   * result as xs:integer, or <code>null</code> if it would overflow
   * (<code>Long.MIN_VALUE idiv -1</code> included), if <code>b</code> is zero
   * (the caller raises FOAR0001), or for <code>div</code>, which the caller
   * computes as xs:decimal. Java's <code>/</code> and <code>%</code> truncate
   * towards zero and give the remainder the sign of the dividend, as
   * {@link BigInteger#divide(BigInteger)} and
   * {@link BigInteger#remainder(BigInteger)} do.
   */
  @Nullable
  private static XdmAtomic _longArithmetic (@NonNull final EBinaryOp eOp, final long a, final long b)
  {
    switch (eOp)
    {
      case ADD:
      {
        final long r = a + b;
        // Overflow if both operands have the sign opposite to the result
        return ((a ^ r) & (b ^ r)) < 0 ? null : XdmAtomic.integer (r);
      }
      case SUBTRACT:
      {
        final long r = a - b;
        // Overflow if the operands have different signs and the result has
        // the sign of b
        return ((a ^ b) & (a ^ r)) < 0 ? null : XdmAtomic.integer (r);
      }
      case MULTIPLY:
      {
        final long nHigh = Math.multiplyHigh (a, b);
        final long nLow = a * b;
        // The product fits if the high word is only the sign extension of
        // the low word
        return (nHigh == 0 && nLow >= 0) || (nHigh == -1 && nLow < 0) ? XdmAtomic.integer (nLow) : null;
      }
      case INTEGER_DIVIDE:
        if (b == 0 || (a == Long.MIN_VALUE && b == -1))
          return null;
        return XdmAtomic.integer (a / b);
      case MODULO:
        if (b == 0)
          return null;
        return XdmAtomic.integer (a % b);
      default:
        return null;
    }
  }

  /**
   * Arithmetic on two xs:integer operands (F&amp;O 3.1 section 4.2). The
   * operands are unbounded, so <code>+</code>, <code>-</code> and
   * <code>*</code> never overflow and return xs:integer. <code>div</code>
   * returns an xs:decimal computed by {@link #divideDecimal}, as
   * op:numeric-divide does for two integers. <code>idiv</code> truncates
   * towards zero and <code>mod</code> keeps the sign of the dividend, both as
   * xs:integer. Operands that fit into a <code>long</code> take
   * {@link #_longArithmetic(EBinaryOp, long, long)} first.
   *
   * @return The result, of the primitive type xs:integer or xs:decimal, never
   *         of an integer subtype.
   * @throws XPathException
   *         FOAR0001 if <code>b</code> is zero for <code>div</code>,
   *         <code>idiv</code> or <code>mod</code>.
   * @throws IllegalArgumentException
   *         if <code>eOp</code> is not an arithmetic operator
   */
  @NonNull
  private static XdmAtomic _integerArithmetic (@NonNull final EBinaryOp eOp,
                                               @NonNull final BigInteger a,
                                               @NonNull final BigInteger b)
  {
    if (a.bitLength () < 64 && b.bitLength () < 64)
    {
      final XdmAtomic ret = _longArithmetic (eOp, a.longValue (), b.longValue ());
      if (ret != null)
        return ret;
    }
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

  /**
   * Exact arithmetic on two xs:decimal operands (F&amp;O 3.1 section 4.2), used
   * when at least one operand is an xs:decimal and neither is a float or
   * double. <code>+</code>, <code>-</code>, <code>*</code> and
   * <code>mod</code> are exact; <code>div</code> uses {@link #divideDecimal};
   * <code>idiv</code> truncates the quotient towards zero and returns an
   * xs:integer; <code>mod</code> keeps the sign of the dividend.
   *
   * @return The result as xs:decimal, or xs:integer for <code>idiv</code>.
   * @throws XPathException
   *         FOAR0001 if <code>b</code> is zero for <code>div</code>,
   *         <code>idiv</code> or <code>mod</code>.
   * @throws IllegalArgumentException
   *         if <code>eOp</code> is not an arithmetic operator
   */
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

  /**
   * <code>idiv</code> on xs:double or xs:float operands
   * (op:numeric-integer-divide, F&amp;O 3.1 section 4.2.5): the quotient
   * already computed in the precision of the operand type is truncated
   * towards zero. A finite dividend divided by an infinite divisor gives 0.
   * The checks run in this order, so <code>NaN idiv 0</code> reports
   * FOAR0002.
   *
   * @param a
   *        Dividend.
   * @param b
   *        Divisor.
   * @param dQuotient
   *        <code>a div b</code> in the precision of the operand type (a float
   *        quotient widened to double).
   * @return The truncated quotient.
   * @throws XPathException
   *         FOAR0002 if an operand is NaN or the dividend is infinite,
   *         FOAR0001 if the divisor is zero, FOAR0002 if the quotient
   *         overflows to infinity.
   */
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

  /**
   * IEEE 754 double arithmetic (F&amp;O 3.1 section 4.2): no error for
   * division by zero or overflow, which give INF, -INF or NaN; <code>mod</code>
   * is the truncated remainder with the sign of the dividend; <code>idiv</code>
   * goes through {@link #_floatingIntegerDivide} and returns an xs:integer.
   *
   * @return The result as xs:double, or xs:integer for <code>idiv</code>.
   * @throws IllegalArgumentException
   *         if <code>eOp</code> is not an arithmetic operator
   */
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

  /**
   * Like {@link #_doubleArithmetic}, but every operation is done in single
   * precision, so the result is rounded to xs:float once. For
   * <code>idiv</code> the float quotient is checked, so a quotient that
   * overflows the float range raises FOAR0002 even if it would fit into a
   * double.
   *
   * @return The result as xs:float, or xs:integer for <code>idiv</code>.
   * @throws IllegalArgumentException
   *         if <code>eOp</code> is not an arithmetic operator
   */
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

  /**
   * @param aValue
   *        An xs:decimal or xs:integer (or integer subtype) value.
   * @return The payload as {@link BigDecimal}; an integer payload is
   *         converted exactly. Any other payload fails with a
   *         {@link ClassCastException}.
   */
  @NonNull
  private static BigDecimal _toBigDecimal (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigDecimal aBD)
      return aBD;
    return new BigDecimal ((BigInteger) aPayload);
  }

  /**
   * Numeric type promotion (XPath 3.1 appendix B.1) followed by the operator
   * of the common type: xs:double if either operand is a double, else xs:float
   * if either is a float, else xs:integer if both payloads are integers (any
   * integer subtype counts as xs:integer), else xs:decimal.
   *
   * @return The result of the matching <code>_xxxArithmetic</code> method.
   */
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
   * Apply a binary arithmetic operator to two atomized operands (XPath 3.1
   * section 3.5.1). An xs:untypedAtomic operand is still cast to xs:double
   * here as a safety net. Two numeric operands go through numeric type
   * promotion and the op:numeric-* operators (F&amp;O 3.1 section 4.2). If
   * either operand is a date/time or duration value, the operation is
   * delegated to {@link DateTimeOps#arithmetic}. Any other combination, or a
   * date/time combination that {@link DateTimeOps} does not define, is a type
   * error.
   *
   * @param eOp
   *        ADD, SUBTRACT, MULTIPLY, DIVIDE, INTEGER_DIVIDE or MODULO.
   * @param aLeft
   *        Left operand, untypedAtomic already cast to xs:double.
   * @param aRight
   *        Right operand, ditto.
   * @param aCtx
   *        For the implicit timezone in date/time subtraction.
   * @return The result.
   * @throws com.helger.phoxtrot.xpath.XPathException
   *         XPTY0004 for operand types without that operator, FOAR0001 for a
   *         division by zero of integers or decimals, FOAR0002 for an
   *         invalid floating point <code>idiv</code>, and the date/time
   *         errors of {@link DateTimeOps#arithmetic}.
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
   * @throws com.helger.phoxtrot.xpath.XPathException
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
