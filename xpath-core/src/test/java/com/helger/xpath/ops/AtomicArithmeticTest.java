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

import static com.helger.xpath.ops.OpsTestHelper.assertError;
import static com.helger.xpath.ops.OpsTestHelper.ctx;
import static com.helger.xpath.ops.OpsTestHelper.dbl;
import static com.helger.xpath.ops.OpsTestHelper.dec;
import static com.helger.xpath.ops.OpsTestHelper.flt;
import static com.helger.xpath.ops.OpsTestHelper.i;
import static com.helger.xpath.ops.OpsTestHelper.str;
import static com.helger.xpath.ops.OpsTestHelper.untyped;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.junit.Test;

import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;

/**
 * Test class for class {@link AtomicArithmetic}.
 *
 * @author Philip Helger
 */
public final class AtomicArithmeticTest
{
  private static final DynamicContext CTX = ctx ();

  private static XdmAtomic _op (final EBinaryOp eOp, final XdmAtomic a, final XdmAtomic b)
  {
    return AtomicArithmetic.arithmetic (eOp, a, b, CTX);
  }

  private static void _assert (final EAtomicType eType,
                               final String sLexical,
                               final EBinaryOp eOp,
                               final XdmAtomic a,
                               final XdmAtomic b)
  {
    final XdmAtomic aRes = _op (eOp, a, b);
    assertSame (eType, aRes.getType ());
    assertEquals (sLexical, aRes.getLexical ());
  }

  @Test
  public void testIntegers ()
  {
    _assert (EAtomicType.INTEGER, "5", EBinaryOp.ADD, i (2), i (3));
    _assert (EAtomicType.INTEGER, "-1", EBinaryOp.SUBTRACT, i (2), i (3));
    _assert (EAtomicType.INTEGER, "6", EBinaryOp.MULTIPLY, i (2), i (3));
    _assert (EAtomicType.INTEGER,
             "9223372036854775808",
             EBinaryOp.ADD,
             i (Long.MAX_VALUE),
             i (1));
    // derived integer types give xs:integer
    final XdmAtomic aByte = XdmAtomic.of (EAtomicType.BYTE, BigInteger.valueOf (100));
    _assert (EAtomicType.INTEGER, "200", EBinaryOp.ADD, aByte, aByte);
  }

  @Test
  public void testDivision ()
  {
    _assert (EAtomicType.DECIMAL, "0.5", EBinaryOp.DIVIDE, i (1), i (2));
    _assert (EAtomicType.DECIMAL, "2", EBinaryOp.DIVIDE, i (4), i (2));
    _assert (EAtomicType.DECIMAL, "0.333333333333333333", EBinaryOp.DIVIDE, i (1), i (3));
    _assert (EAtomicType.DECIMAL, "0.666666666666666667", EBinaryOp.DIVIDE, i (2), i (3));
    _assert (EAtomicType.DECIMAL, "-0.666666666666666667", EBinaryOp.DIVIDE, i (-2), i (3));
    _assert (EAtomicType.DECIMAL, "3.333333333333333333", EBinaryOp.DIVIDE, dec ("10"), dec ("3"));
    // terminating quotients are exact, however long
    _assert (EAtomicType.DECIMAL,
             "0.000000000000000000000000019999",
             EBinaryOp.DIVIDE,
             dec ("0.000000000000000000000000039998"),
             i (2));
    _assert (EAtomicType.DECIMAL, "0.0009765625", EBinaryOp.DIVIDE, i (1), i (1024));
    assertError ("FOAR0001", () -> _op (EBinaryOp.DIVIDE, i (1), i (0)));
    assertError ("FOAR0001", () -> _op (EBinaryOp.DIVIDE, dec ("1.5"), dec ("0.0")));
    _assert (EAtomicType.DOUBLE, "INF", EBinaryOp.DIVIDE, dbl (1), dbl (0));
    _assert (EAtomicType.DOUBLE, "-INF", EBinaryOp.DIVIDE, dbl (-1), dbl (0));
    _assert (EAtomicType.DOUBLE, "-INF", EBinaryOp.DIVIDE, dbl (1), dbl (-0.0));
    _assert (EAtomicType.DOUBLE, "NaN", EBinaryOp.DIVIDE, dbl (0), dbl (0));
    _assert (EAtomicType.FLOAT, "INF", EBinaryOp.DIVIDE, flt (1), i (0));
  }

  @Test
  public void testDivisionPrecision ()
  {
    // QT3 op-numeric-divide*: the quotient must survive rounding to 18 places
    BigDecimal aRes = _op (EBinaryOp.DIVIDE,
                           i (830993497117024304L),
                           i (-999999999999999999L)).getBigDecimal ();
    assertEquals (new BigDecimal ("-0.830993497117024305"), aRes.setScale (18, RoundingMode.HALF_EVEN));
    aRes = _op (EBinaryOp.DIVIDE, i (-999999999999999999L), i (830993497117024304L)).getBigDecimal ();
    assertEquals (new BigDecimal ("-1.203378851301859738"), aRes.setScale (18, RoundingMode.HALF_EVEN));
    aRes = _op (EBinaryOp.DIVIDE, i (1), i (52704602390610033L)).getBigDecimal ();
    assertEquals (new BigDecimal ("0.00000000000000002"), aRes.setScale (17, RoundingMode.HALF_EVEN));
    // tiny quotients keep at least 18 significant digits
    aRes = _op (EBinaryOp.DIVIDE, i (1), XdmAtomic.integer (new BigInteger ("700000000000000000000"))).getBigDecimal ();
    assertTrue (aRes.toPlainString (), aRes.toPlainString ().startsWith ("0.00000000000000000000142857142857142857"));
  }

  @Test
  public void testIntegerDivide ()
  {
    _assert (EAtomicType.INTEGER, "3", EBinaryOp.INTEGER_DIVIDE, i (7), i (2));
    _assert (EAtomicType.INTEGER, "-3", EBinaryOp.INTEGER_DIVIDE, i (-7), i (2));
    _assert (EAtomicType.INTEGER, "-3", EBinaryOp.INTEGER_DIVIDE, i (7), i (-2));
    _assert (EAtomicType.INTEGER, "3", EBinaryOp.INTEGER_DIVIDE, dec ("7.5"), i (2));
    _assert (EAtomicType.INTEGER, "-1", EBinaryOp.INTEGER_DIVIDE, dec ("-3.5"), dec ("2"));
    _assert (EAtomicType.INTEGER, "3", EBinaryOp.INTEGER_DIVIDE, dbl (7.9), dbl (2));
    _assert (EAtomicType.INTEGER, "0", EBinaryOp.INTEGER_DIVIDE, dbl (1), dbl (Double.POSITIVE_INFINITY));
    _assert (EAtomicType.INTEGER, "2", EBinaryOp.INTEGER_DIVIDE, flt (5f), flt (2f));
    assertError ("FOAR0001", () -> _op (EBinaryOp.INTEGER_DIVIDE, i (7), i (0)));
    assertError ("FOAR0001", () -> _op (EBinaryOp.INTEGER_DIVIDE, dec ("7.1"), dec ("0")));
    assertError ("FOAR0001", () -> _op (EBinaryOp.INTEGER_DIVIDE, dbl (1), dbl (0)));
    assertError ("FOAR0002", () -> _op (EBinaryOp.INTEGER_DIVIDE, dbl (Double.NaN), dbl (1)));
    assertError ("FOAR0002", () -> _op (EBinaryOp.INTEGER_DIVIDE, dbl (1), dbl (Double.NaN)));
    assertError ("FOAR0002", () -> _op (EBinaryOp.INTEGER_DIVIDE, dbl (Double.POSITIVE_INFINITY), dbl (1)));
    // a quotient beyond 64 bit is still an xs:integer
    final XdmAtomic aBig = _op (EBinaryOp.INTEGER_DIVIDE, dbl (1e38), dbl (1e-37));
    assertSame (EAtomicType.INTEGER, aBig.getType ());
    assertEquals (new BigDecimal (1e38 / 1e-37).toBigInteger (), aBig.getBigInteger ());
  }

  @Test
  public void testModulo ()
  {
    _assert (EAtomicType.INTEGER, "1", EBinaryOp.MODULO, i (7), i (3));
    _assert (EAtomicType.INTEGER, "-1", EBinaryOp.MODULO, i (-7), i (3));
    _assert (EAtomicType.INTEGER, "1", EBinaryOp.MODULO, i (7), i (-3));
    // exact decimal: 0.9, not 0.8999999999999999
    _assert (EAtomicType.DECIMAL, "0.9", EBinaryOp.MODULO, dec ("4.5"), dec ("1.2"));
    _assert (EAtomicType.DECIMAL, "-0.9", EBinaryOp.MODULO, dec ("-4.5"), dec ("1.2"));
    _assert (EAtomicType.DOUBLE, "2", EBinaryOp.MODULO, dbl (5), i (3));
    _assert (EAtomicType.DOUBLE, "-2", EBinaryOp.MODULO, dbl (-5), i (3));
    _assert (EAtomicType.DOUBLE, "NaN", EBinaryOp.MODULO, dbl (1), dbl (0));
    _assert (EAtomicType.DOUBLE, "NaN", EBinaryOp.MODULO, dbl (Double.POSITIVE_INFINITY), dbl (3));
    _assert (EAtomicType.DOUBLE, "3", EBinaryOp.MODULO, dbl (3), dbl (Double.NEGATIVE_INFINITY));
    _assert (EAtomicType.DOUBLE, "-0", EBinaryOp.MODULO, dbl (-6), dbl (3));
    assertError ("FOAR0001", () -> _op (EBinaryOp.MODULO, i (1), i (0)));
    assertError ("FOAR0001", () -> _op (EBinaryOp.MODULO, dec ("1.5"), i (0)));
  }

  @Test
  public void testPromotion ()
  {
    _assert (EAtomicType.DECIMAL, "0.3", EBinaryOp.ADD, dec ("0.1"), dec ("0.2"));
    _assert (EAtomicType.DOUBLE, "0.30000000000000004", EBinaryOp.ADD, dbl (0.1), dbl (0.2));
    _assert (EAtomicType.DECIMAL, "1.5", EBinaryOp.ADD, i (1), dec ("0.5"));
    _assert (EAtomicType.DOUBLE, "1.5", EBinaryOp.ADD, dec ("1"), dbl (0.5));
    _assert (EAtomicType.FLOAT, "1.5", EBinaryOp.ADD, dec ("1"), flt (0.5f));
    _assert (EAtomicType.FLOAT, "3", EBinaryOp.ADD, i (1), flt (2f));
    _assert (EAtomicType.DOUBLE, "3", EBinaryOp.ADD, flt (1f), dbl (2));
    // float arithmetic happens at single precision
    _assert (EAtomicType.FLOAT, "0.3", EBinaryOp.ADD, flt (0.1f), flt (0.2f));
    _assert (EAtomicType.FLOAT, "1.1", EBinaryOp.MULTIPLY, dec ("1.1"), flt (1f));
  }

  @Test
  public void testOverflow ()
  {
    _assert (EAtomicType.DOUBLE, "INF", EBinaryOp.MULTIPLY, dbl (1e308), dbl (10));
    _assert (EAtomicType.DOUBLE, "-INF", EBinaryOp.MULTIPLY, dbl (-1e308), dbl (10));
    _assert (EAtomicType.FLOAT, "INF", EBinaryOp.MULTIPLY, flt (1.5e38f), i (10));
    _assert (EAtomicType.FLOAT, "0", EBinaryOp.MULTIPLY, flt (1e-30f), flt (1e-30f));
  }

  @Test
  public void testTypeErrors ()
  {
    assertError ("XPTY0004", () -> _op (EBinaryOp.ADD, str ("3"), i (3)));
    assertError ("XPTY0004", () -> _op (EBinaryOp.ADD, XdmAtomic.TRUE, i (1)));
    assertError ("XPTY0004", () -> _op (EBinaryOp.ADD, XdmAtomic.anyUri ("x"), i (1)));
    // untypedAtomic is cast to xs:double, should the caller not have done so
    _assert (EAtomicType.DOUBLE, "4", EBinaryOp.ADD, untyped ("3"), i (1));
    assertError ("FORG0001", () -> _op (EBinaryOp.ADD, untyped ("x"), i (1)));
  }

  @Test
  public void testNegate ()
  {
    XdmAtomic aRes = AtomicArithmetic.negate (i (5));
    assertSame (EAtomicType.INTEGER, aRes.getType ());
    assertEquals ("-5", aRes.getLexical ());
    aRes = AtomicArithmetic.negate (XdmAtomic.of (EAtomicType.POSITIVE_INTEGER, BigInteger.TWO));
    assertSame (EAtomicType.INTEGER, aRes.getType ());
    assertEquals ("-2", aRes.getLexical ());
    aRes = AtomicArithmetic.negate (dec ("1.5"));
    assertSame (EAtomicType.DECIMAL, aRes.getType ());
    assertEquals ("-1.5", aRes.getLexical ());
    aRes = AtomicArithmetic.negate (flt (0f));
    assertSame (EAtomicType.FLOAT, aRes.getType ());
    assertEquals ("-0", aRes.getLexical ());
    aRes = AtomicArithmetic.negate (dbl (Double.NEGATIVE_INFINITY));
    assertEquals ("INF", aRes.getLexical ());
    aRes = AtomicArithmetic.negate (untyped ("3"));
    assertSame (EAtomicType.DOUBLE, aRes.getType ());
    assertEquals ("-3", aRes.getLexical ());
    assertError ("XPTY0004", () -> AtomicArithmetic.negate (str ("a")));
    assertError ("XPTY0004", () -> AtomicArithmetic.negate (XdmAtomic.TRUE));
  }

  @Test
  public void testDateTimeDelegation ()
  {
    final XdmAtomic aDate = AtomicCaster.cast (str ("2000-02-29"), EAtomicType.DATE, null);
    final XdmAtomic aYM = AtomicCaster.cast (str ("P1Y"), EAtomicType.YEAR_MONTH_DURATION, null);
    assertEquals ("1999-02-28", _op (EBinaryOp.SUBTRACT, aDate, aYM).getLexical ());
    assertEquals ("P2Y", _op (EBinaryOp.MULTIPLY, aYM, i (2)).getLexical ());
    assertError ("XPTY0004", () -> _op (EBinaryOp.ADD, aDate, aDate));
    assertError ("XPTY0004", () -> _op (EBinaryOp.ADD, aDate, i (1)));
  }
}
