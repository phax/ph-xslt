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

import static com.helger.xpath.ops.OpsTestHelper.FUNCTION;
import static com.helger.xpath.ops.OpsTestHelper.assertError;
import static com.helger.xpath.ops.OpsTestHelper.ctx;
import static com.helger.xpath.ops.OpsTestHelper.dbl;
import static com.helger.xpath.ops.OpsTestHelper.dec;
import static com.helger.xpath.ops.OpsTestHelper.flt;
import static com.helger.xpath.ops.OpsTestHelper.i;
import static com.helger.xpath.ops.OpsTestHelper.str;
import static com.helger.xpath.ops.OpsTestHelper.untyped;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link AtomicComparator}.
 *
 * @author Philip Helger
 */
public final class AtomicComparatorTest
{
  private static final DynamicContext CTX = ctx ();

  /** A simple case-insensitive collation for tests. */
  private static final ICollation CASE_INSENSITIVE = new ICollation ()
  {
    public String getUri ()
    {
      return "urn:test:ci";
    }

    public int compare (final String sLeft, final String sRight)
    {
      return sLeft.toLowerCase (Locale.ROOT).compareTo (sRight.toLowerCase (Locale.ROOT));
    }

    public Object getCollationKey (final String sValue)
    {
      return sValue.toLowerCase (Locale.ROOT);
    }
  };

  private static boolean _v (final EComparisonOp eOp, final XdmAtomic a, final XdmAtomic b)
  {
    return AtomicComparator.valueCompare (eOp, a, b, null, CTX);
  }

  private static XdmSequence _seq (final IXdmItem... aItems)
  {
    return XdmSequence.of (aItems);
  }

  private static boolean _g (final EComparisonOp eOp, final XdmSequence a, final XdmSequence b)
  {
    return AtomicComparator.generalCompare (eOp, a, b, null, CTX);
  }

  private static XdmAtomic _qn (final String sNS, final String sLocal, final String sPrefix)
  {
    return XdmAtomic.qname (new XdmQName (sNS, sLocal, sPrefix));
  }

  private static XdmAtomic _hex (final String s)
  {
    return AtomicCaster.cast (str (s), EAtomicType.HEX_BINARY, null);
  }

  @Test
  public void testNumericValueComparison ()
  {
    assertTrue (_v (EComparisonOp.VALUE_EQ, i (1), dec ("1.0")));
    assertTrue (_v (EComparisonOp.VALUE_EQ, i (1), dbl (1)));
    assertTrue (_v (EComparisonOp.VALUE_EQ, dbl (0), dbl (-0.0)));
    assertFalse (_v (EComparisonOp.VALUE_LT, dbl (-0.0), dbl (0)));
    assertTrue (_v (EComparisonOp.VALUE_LT, i (1), dec ("1.5")));
    assertTrue (_v (EComparisonOp.VALUE_GE, dbl (2), flt (2f)));
    // a decimal facing a float is promoted to float
    assertTrue (_v (EComparisonOp.VALUE_EQ, flt (1.01f), dec ("1.01")));
    assertFalse (_v (EComparisonOp.VALUE_EQ, flt (1.01f), dbl (1.01)));
    assertTrue (_v (EComparisonOp.VALUE_EQ, dec ("0.1"), dbl (0.1)));
    // integer/decimal comparison is exact
    final XdmAtomic aBig1 = XdmAtomic.integer (new BigInteger ("9007199254740993"));
    final XdmAtomic aBig2 = XdmAtomic.integer (new BigInteger ("9007199254740992"));
    assertFalse (_v (EComparisonOp.VALUE_EQ, aBig1, aBig2));
    assertTrue (_v (EComparisonOp.VALUE_GT, aBig1, dec ("9007199254740992.5")));
    // ... but a double operand promotes the integer to double
    assertTrue (_v (EComparisonOp.VALUE_EQ, aBig1, dbl (9007199254740992d)));
    assertTrue (_v (EComparisonOp.VALUE_EQ, XdmAtomic.of (EAtomicType.BYTE, BigInteger.ONE), i (1)));
  }

  @Test
  public void testNaN ()
  {
    final XdmAtomic aNaN = dbl (Double.NaN);
    assertFalse (_v (EComparisonOp.VALUE_EQ, aNaN, aNaN));
    assertTrue (_v (EComparisonOp.VALUE_NE, aNaN, aNaN));
    assertFalse (_v (EComparisonOp.VALUE_LT, aNaN, i (1)));
    assertFalse (_v (EComparisonOp.VALUE_GE, aNaN, i (1)));
    assertTrue (_v (EComparisonOp.VALUE_NE, aNaN, i (1)));
    assertFalse (_g (EComparisonOp.GENERAL_EQ, _seq (aNaN), _seq (i (1))));
    assertFalse (_g (EComparisonOp.GENERAL_EQ, _seq (i (1)), _seq (aNaN)));
    assertTrue (_g (EComparisonOp.GENERAL_NE, _seq (aNaN), _seq (i (1))));
    assertFalse (_g (EComparisonOp.GENERAL_LT, _seq (aNaN), _seq (i (1))));
    assertFalse (_g (EComparisonOp.GENERAL_GE, _seq (aNaN), _seq (i (1))));
    assertFalse (_v (EComparisonOp.VALUE_EQ, flt (Float.NaN), flt (Float.NaN)));
  }

  @Test
  public void testStringValueComparison ()
  {
    assertTrue (_v (EComparisonOp.VALUE_EQ, str ("a"), untyped ("a")));
    assertTrue (_v (EComparisonOp.VALUE_EQ, str ("a"), XdmAtomic.anyUri ("a")));
    assertTrue (_v (EComparisonOp.VALUE_EQ, XdmAtomic.of (EAtomicType.NCNAME, "a"), str ("a")));
    assertTrue (_v (EComparisonOp.VALUE_LT, str ("a"), str ("b")));
    assertTrue (_v (EComparisonOp.VALUE_LT, str ("ab"), str ("abc")));
    // codepoint order, not UTF-16 order
    assertTrue (_v (EComparisonOp.VALUE_LT, str ("�"), str ("𐀀")));
    assertFalse (_v (EComparisonOp.VALUE_EQ, str ("A"), str ("a")));
    assertTrue (AtomicComparator.valueCompare (EComparisonOp.VALUE_EQ, str ("A"), str ("a"), CASE_INSENSITIVE, CTX));
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 _seq (str ("A")),
                                                 _seq (untyped ("a")),
                                                 CASE_INSENSITIVE,
                                                 CTX));
  }

  @Test
  public void testOtherValueComparisons ()
  {
    assertTrue (_v (EComparisonOp.VALUE_LT, XdmAtomic.FALSE, XdmAtomic.TRUE));
    assertTrue (_v (EComparisonOp.VALUE_EQ, XdmAtomic.TRUE, XdmAtomic.TRUE));
    // QName: namespace and local name only, eq/ne only
    assertTrue (_v (EComparisonOp.VALUE_EQ, _qn ("urn:x", "a", "p"), _qn ("urn:x", "a", "q")));
    assertTrue (_v (EComparisonOp.VALUE_NE, _qn ("urn:x", "a", "p"), _qn ("urn:y", "a", "p")));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_LT, _qn ("", "a", ""), _qn ("", "b", "")));
    assertError ("XPTY0004",
                 () -> _v (EComparisonOp.VALUE_EQ,
                           _qn ("", "a", ""),
                           XdmAtomic.of (EAtomicType.NOTATION, new XdmQName ("a"))));
    // binary values are ordered since XPath 3.1
    assertTrue (_v (EComparisonOp.VALUE_EQ, _hex ("0fa0"), _hex ("0FA0")));
    assertTrue (_v (EComparisonOp.VALUE_LT, _hex ("01"), _hex ("FF")));
    assertTrue (_v (EComparisonOp.VALUE_LT, _hex ("01"), _hex ("0100")));
    assertError ("XPTY0004",
                 () -> _v (EComparisonOp.VALUE_EQ,
                           _hex ("01"),
                           AtomicCaster.cast (str ("AQ=="), EAtomicType.BASE64_BINARY, null)));
  }

  @Test
  public void testIncomparable ()
  {
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_EQ, i (1), str ("1")));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_EQ, XdmAtomic.TRUE, i (1)));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_EQ, XdmAtomic.TRUE, str ("true")));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_EQ, _hex ("01"), str ("01")));
    assertError ("XPTY0004", () -> _g (EComparisonOp.GENERAL_EQ, _seq (str ("1")), _seq (i (1))));
  }

  @Test
  public void testGeneralComparison ()
  {
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (i (1), i (2)), _seq (i (2), i (3))));
    assertFalse (_g (EComparisonOp.GENERAL_EQ, _seq (i (1), i (2)), _seq (i (3), i (4))));
    assertFalse (_g (EComparisonOp.GENERAL_EQ, XdmSequence.EMPTY, _seq (i (1))));
    assertFalse (_g (EComparisonOp.GENERAL_NE, XdmSequence.EMPTY, XdmSequence.EMPTY));
    assertTrue (_g (EComparisonOp.GENERAL_NE, _seq (i (1), i (2)), _seq (i (1))));
    assertFalse (_g (EComparisonOp.GENERAL_NE, _seq (i (1)), _seq (i (1))));
    assertTrue (_g (EComparisonOp.GENERAL_LT, _seq (i (5), i (1)), _seq (i (2))));
    assertTrue (_g (EComparisonOp.GENERAL_GE, _seq (i (2)), _seq (i (2))));
  }

  @Test
  public void testGeneralComparisonUntyped ()
  {
    // untypedAtomic against a number is cast to xs:double
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (untyped ("1")), _seq (i (1))));
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (i (1)), _seq (untyped (" 1.0 "))));
    assertTrue (_g (EComparisonOp.GENERAL_LT, _seq (untyped ("10")), _seq (i (11))));
    assertError ("FORG0001", () -> _g (EComparisonOp.GENERAL_EQ, _seq (untyped ("x")), _seq (i (1))));
    // two untyped values compare as strings
    assertFalse (_g (EComparisonOp.GENERAL_EQ, _seq (untyped ("1")), _seq (untyped ("1.0"))));
    assertTrue (_g (EComparisonOp.GENERAL_LT, _seq (untyped ("10")), _seq (untyped ("9"))));
    // against a string-derived type: as string
    assertFalse (_g (EComparisonOp.GENERAL_EQ,
                     _seq (untyped ("1")),
                     _seq (XdmAtomic.of (EAtomicType.NCNAME, "string"))));
    // against a boolean: cast to xs:boolean
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (untyped ("true")), _seq (XdmAtomic.TRUE)));
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (untyped ("0")), _seq (XdmAtomic.FALSE)));
    assertError ("FORG0001", () -> _g (EComparisonOp.GENERAL_EQ, _seq (untyped ("yes")), _seq (XdmAtomic.TRUE)));
    // against a QName: resolved with the static namespaces
    final StaticContext aSC = new StaticContext ();
    aSC.declareNamespace ("p", "urn:p");
    final DynamicContext aCtx = new DynamicContext (aSC);
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 _seq (untyped ("p:a")),
                                                 _seq (_qn ("urn:p", "a", "other")),
                                                 null,
                                                 aCtx));
    // An unbound prefix is FONS0004, as for any cast to xs:QName
    assertError ("FONS0004",
                 () -> AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                        _seq (untyped ("zz:a")),
                                                        _seq (_qn ("urn:p", "a", "p")),
                                                        null,
                                                        aCtx));
    // against anyURI: cast to anyURI
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (untyped (" http://x ")), _seq (XdmAtomic.anyUri ("http://x"))));
  }

  @Test
  public void testGeneralComparisonCompatibilityMode ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ().setXPath10Compatibility (true));
    // relational operators are numeric
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_GT,
                                                 _seq (str ("10")),
                                                 _seq (str ("2")),
                                                 null,
                                                 aCtx));
    assertFalse (AtomicComparator.generalCompare (EComparisonOp.GENERAL_LT,
                                                  _seq (str ("abc")),
                                                  _seq (i (1)),
                                                  null,
                                                  aCtx));
    assertFalse (AtomicComparator.generalCompare (EComparisonOp.GENERAL_GE,
                                                  _seq (str ("abc")),
                                                  _seq (i (1)),
                                                  null,
                                                  aCtx));
    // = with a number: both via fn:number
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ, _seq (i (1)), _seq (str ("1")), null, aCtx));
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 _seq (untyped ("1.0")),
                                                 _seq (i (1)),
                                                 null,
                                                 aCtx));
    assertFalse (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                  _seq (str ("x")),
                                                  _seq (i (1)),
                                                  null,
                                                  aCtx));
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_NE,
                                                 _seq (str ("x")),
                                                 _seq (i (1)),
                                                 null,
                                                 aCtx));
    // strings compare as strings
    assertFalse (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                  _seq (str ("1")),
                                                  _seq (untyped ("1.0")),
                                                  null,
                                                  aCtx));
    // a boolean operand turns the other one into a boolean
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 _seq (XdmAtomic.TRUE),
                                                 _seq (str ("x")),
                                                 null,
                                                 aCtx));
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 _seq (str ("")),
                                                 _seq (XdmAtomic.FALSE),
                                                 null,
                                                 aCtx));
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 XdmSequence.EMPTY,
                                                 _seq (XdmAtomic.FALSE),
                                                 null,
                                                 aCtx));
    assertTrue (AtomicComparator.generalCompare (EComparisonOp.GENERAL_EQ,
                                                 _seq (untyped ("a"), untyped ("b")),
                                                 _seq (XdmAtomic.TRUE),
                                                 null,
                                                 aCtx));
  }

  @Test
  public void testCompareForOrder ()
  {
    final XdmAtomic aNaN = dbl (Double.NaN);
    assertEquals (-1, AtomicComparator.compareForOrder (aNaN, i (1), null, CTX));
    assertEquals (1, AtomicComparator.compareForOrder (i (1), aNaN, null, CTX));
    assertEquals (0, AtomicComparator.compareForOrder (aNaN, flt (Float.NaN), null, CTX));
    assertEquals (-1, AtomicComparator.compareForOrder (i (1), dec ("1.5"), null, CTX));
    assertEquals (0, AtomicComparator.compareForOrder (i (1), dbl (1), null, CTX));
    assertEquals (1, AtomicComparator.compareForOrder (str ("b"), untyped ("a"), null, CTX));
    assertEquals (0, AtomicComparator.compareForOrder (str ("B"), str ("b"), CASE_INSENSITIVE, CTX));
    assertError ("XPTY0004", () -> AtomicComparator.compareForOrder (i (1), str ("1"), null, CTX));
    assertError ("XPTY0004",
                 () -> AtomicComparator.compareForOrder (_qn ("", "a", ""), _qn ("", "b", ""), null, CTX));
  }

  @Test
  public void testIsEqualForDistinct ()
  {
    final XdmAtomic aNaN = dbl (Double.NaN);
    assertTrue (AtomicComparator.isEqualForDistinct (aNaN, aNaN, null, CTX));
    assertTrue (AtomicComparator.isEqualForDistinct (aNaN, flt (Float.NaN), null, CTX));
    assertFalse (AtomicComparator.isEqualForDistinct (aNaN, i (1), null, CTX));
    assertTrue (AtomicComparator.isEqualForDistinct (i (1), dec ("1.0"), null, CTX));
    assertFalse (AtomicComparator.isEqualForDistinct (i (1), str ("1"), null, CTX));
    assertTrue (AtomicComparator.isEqualForDistinct (str ("a"), untyped ("a"), null, CTX));
    assertFalse (AtomicComparator.isEqualForDistinct (XdmAtomic.TRUE, i (1), null, CTX));
    assertTrue (AtomicComparator.isEqualForDistinct (_qn ("u", "a", "p"), _qn ("u", "a", "q"), null, CTX));
  }

  @Test
  public void testMapKey ()
  {
    final Object aOne = AtomicComparator.getMapKey (i (1));
    for (final XdmAtomic a : new XdmAtomic [] { dec ("1.0"),
                                                dbl (1),
                                                flt (1f),
                                                XdmAtomic.of (EAtomicType.UNSIGNED_BYTE, BigInteger.ONE) })
    {
      assertEquals (aOne, AtomicComparator.getMapKey (a));
      assertEquals (aOne.hashCode (), AtomicComparator.getMapKey (a).hashCode ());
    }
    assertNotEquals (AtomicComparator.getMapKey (dbl (1.1)), AtomicComparator.getMapKey (dec ("1.1")));
    assertNotEquals (AtomicComparator.getMapKey (flt (1.1f)), AtomicComparator.getMapKey (dbl (1.1)));
    assertEquals (AtomicComparator.getMapKey (dbl (0.5)), AtomicComparator.getMapKey (dec ("0.50")));
    assertEquals (AtomicComparator.getMapKey (i (10)), AtomicComparator.getMapKey (dec ("1E+1")));
    assertEquals (AtomicComparator.getMapKey (dbl (0)), AtomicComparator.getMapKey (dbl (-0.0)));
    assertEquals (AtomicComparator.getMapKey (dbl (Double.NaN)), AtomicComparator.getMapKey (flt (Float.NaN)));
    assertEquals (AtomicComparator.getMapKey (dbl (Double.POSITIVE_INFINITY)),
                  AtomicComparator.getMapKey (flt (Float.POSITIVE_INFINITY)));
    assertNotEquals (AtomicComparator.getMapKey (dbl (Double.POSITIVE_INFINITY)),
                     AtomicComparator.getMapKey (dbl (Double.NEGATIVE_INFINITY)));
    assertEquals (AtomicComparator.getMapKey (dbl (1e20)),
                  AtomicComparator.getMapKey (XdmAtomic.integer (new BigInteger ("100000000000000000000"))));

    assertEquals (AtomicComparator.getMapKey (str ("a")), AtomicComparator.getMapKey (untyped ("a")));
    assertEquals (AtomicComparator.getMapKey (str ("a")), AtomicComparator.getMapKey (XdmAtomic.anyUri ("a")));
    assertNotEquals (AtomicComparator.getMapKey (str ("1")), AtomicComparator.getMapKey (i (1)));
    assertNotEquals (AtomicComparator.getMapKey (str ("true")), AtomicComparator.getMapKey (XdmAtomic.TRUE));
    assertEquals (AtomicComparator.getMapKey (XdmAtomic.TRUE), AtomicComparator.getMapKey (XdmAtomic.bool (true)));
    assertNotEquals (AtomicComparator.getMapKey (XdmAtomic.TRUE), AtomicComparator.getMapKey (i (1)));

    assertEquals (AtomicComparator.getMapKey (_qn ("u", "a", "p")), AtomicComparator.getMapKey (_qn ("u", "a", "q")));
    assertNotEquals (AtomicComparator.getMapKey (_qn ("u", "a", "p")),
                     AtomicComparator.getMapKey (XdmAtomic.of (EAtomicType.NOTATION, new XdmQName ("u", "a"))));
    assertNotEquals (AtomicComparator.getMapKey (_qn ("", "a", "")), AtomicComparator.getMapKey (str ("a")));

    assertEquals (AtomicComparator.getMapKey (_hex ("0fa0")), AtomicComparator.getMapKey (_hex ("0FA0")));
    assertEquals (AtomicComparator.getMapKey (_hex ("0fa0")).hashCode (),
                  AtomicComparator.getMapKey (_hex ("0FA0")).hashCode ());
    assertNotEquals (AtomicComparator.getMapKey (_hex ("01")),
                     AtomicComparator.getMapKey (AtomicCaster.cast (str ("AQ=="), EAtomicType.BASE64_BINARY, null)));
  }

  @Test
  public void testDateTimeAndDuration ()
  {
    final XdmAtomic aT1 = AtomicCaster.cast (str ("17:00:00Z"), EAtomicType.TIME, null);
    final XdmAtomic aT2 = AtomicCaster.cast (str ("12:00:00-05:00"), EAtomicType.TIME, null);
    assertTrue (_v (EComparisonOp.VALUE_EQ, aT1, aT2));
    assertEquals (AtomicComparator.getMapKey (aT1), AtomicComparator.getMapKey (aT2));
    final XdmAtomic aD = AtomicCaster.cast (str ("2004-01-15"), EAtomicType.DATE, null);
    final XdmAtomic aDT = AtomicCaster.cast (str ("2004-01-15T00:00:00"), EAtomicType.DATE_TIME, null);
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_EQ, aD, aDT));
    final XdmAtomic aGY = AtomicCaster.cast (str ("2004"), EAtomicType.G_YEAR, null);
    assertTrue (_v (EComparisonOp.VALUE_EQ, aGY, aGY));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_LT, aGY, aGY));
    final XdmAtomic aYM = AtomicCaster.cast (str ("P1Y"), EAtomicType.YEAR_MONTH_DURATION, null);
    final XdmAtomic aDur = AtomicCaster.cast (str ("P12M"), EAtomicType.DURATION, null);
    final XdmAtomic aDTD = AtomicCaster.cast (str ("PT1H"), EAtomicType.DAY_TIME_DURATION, null);
    assertTrue (_v (EComparisonOp.VALUE_EQ, aYM, aDur));
    assertFalse (_v (EComparisonOp.VALUE_EQ, aYM, aDTD));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_LT, aYM, aDur));
    assertError ("XPTY0004", () -> _v (EComparisonOp.VALUE_LT, aYM, aDTD));
    assertEquals (AtomicComparator.getMapKey (aYM), AtomicComparator.getMapKey (aDur));
    // untyped against a dayTimeDuration is cast to xs:dayTimeDuration
    assertTrue (_g (EComparisonOp.GENERAL_EQ, _seq (untyped ("PT60M")), _seq (aDTD)));
  }

  private static IXdmNode _elem (final String sName, final Object... aContent)
  {
    final IXdmNode ret = IXdmNode.createElement (new XdmQName (sName));
    for (final Object o : aContent)
      if (o instanceof final String s)
        ret.appendChild (IXdmNode.createText (s));
      else
        ret.appendChild ((IXdmNode) o);
    return ret;
  }

  private static boolean _deq (final XdmSequence a, final XdmSequence b)
  {
    return AtomicComparator.deepEqual (a, b, null, CTX);
  }

  @Test
  public void testDeepEqualAtomics ()
  {
    assertTrue (_deq (XdmSequence.EMPTY, XdmSequence.EMPTY));
    assertTrue (_deq (_seq (i (1), i (2)), _seq (dec ("1.0"), dbl (2))));
    assertFalse (_deq (_seq (i (1), i (2)), _seq (i (1))));
    assertFalse (_deq (_seq (i (1), i (2)), _seq (i (2), i (1))));
    // NaN is deep-equal to NaN
    assertTrue (_deq (_seq (dbl (Double.NaN)), _seq (flt (Float.NaN))));
    // incomparable is just false
    assertFalse (_deq (_seq (i (1)), _seq (str ("1"))));
    assertTrue (_deq (_seq (str ("a")), _seq (untyped ("a"))));
    assertFalse (_deq (_seq (str ("A")), _seq (str ("a"))));
    assertTrue (AtomicComparator.deepEqual (_seq (str ("A")), _seq (str ("a")), CASE_INSENSITIVE, CTX));
    assertFalse (_deq (_seq (i (1)), _seq (_elem ("a", "1"))));
  }

  @Test
  public void testDeepEqualMapsAndArrays ()
  {
    final XdmMap aM1 = XdmMap.EMPTY.put (i (1), _seq (str ("a"))).put (str ("k"), _seq (i (1), i (2)));
    final XdmMap aM2 = XdmMap.EMPTY.put (str ("k"), _seq (dbl (1), dbl (2))).put (dec ("1.0"), _seq (str ("a")));
    assertTrue (_deq (_seq (aM1), _seq (aM2)));
    assertFalse (_deq (_seq (aM1), _seq (aM2.put (str ("k"), _seq (i (1))))));
    assertFalse (_deq (_seq (aM1), _seq (aM2.put (str ("x"), XdmSequence.EMPTY))));
    assertFalse (_deq (_seq (aM1), _seq (XdmArray.EMPTY)));

    final XdmArray aA1 = XdmArray.of (List.of (_seq (i (1)), _seq (XdmArray.EMPTY.append (_seq (i (2))))));
    final XdmArray aA2 = XdmArray.of (List.of (_seq (i (1)), _seq (XdmArray.EMPTY.append (_seq (i (2))))));
    final XdmArray aA3 = XdmArray.of (List.of (_seq (i (1)), _seq (XdmArray.EMPTY.append (_seq (i (3))))));
    assertTrue (_deq (_seq (aA1), _seq (aA2)));
    assertFalse (_deq (_seq (aA1), _seq (aA3)));
    assertFalse (_deq (_seq (aA1), _seq (i (1), i (2))));

    assertError ("FOTY0015", () -> _deq (_seq (FUNCTION), _seq (FUNCTION)));
    assertError ("FOTY0015", () -> _deq (_seq (i (1)), _seq (XdmArray.EMPTY.append (_seq (FUNCTION)))));
    assertError ("FOTY0015", () -> _deq (_seq (XdmMap.EMPTY.put (i (1), _seq (FUNCTION))), _seq (i (1))));
  }

  @Test
  public void testDeepEqualDeepArrays ()
  {
    XdmArray aA = XdmArray.EMPTY;
    XdmArray aB = XdmArray.EMPTY;
    for (int n = 0; n < 50_000; ++n)
    {
      aA = XdmArray.EMPTY.append (_seq (aA));
      aB = XdmArray.EMPTY.append (_seq (aB));
    }
    assertTrue (_deq (_seq (aA), _seq (aB)));
  }

  @Test
  public void testDeepEqualNodes ()
  {
    final IXdmNode a1 = _elem ("a", "x", _elem ("b", "y"));
    a1.addAttribute (IXdmNode.createAttribute (new XdmQName ("p"), "1"));
    a1.addAttribute (IXdmNode.createAttribute (new XdmQName ("urn:x", "q", "x"), "2"));
    final IXdmNode a2 = _elem ("a", "x", IXdmNode.createComment ("ignored"), _elem ("b", "y"));
    // attribute order and prefixes do not matter
    a2.addAttribute (IXdmNode.createAttribute (new XdmQName ("urn:x", "q", "other"), "2"));
    a2.addAttribute (IXdmNode.createAttribute (new XdmQName ("p"), "1"));
    a2.appendChild (IXdmNode.createProcessingInstruction ("pi", "ignored"));
    assertTrue (_deq (_seq (a1), _seq (a2)));

    final IXdmNode a3 = _elem ("a", "x", _elem ("b", "z"));
    a3.addAttribute (IXdmNode.createAttribute (new XdmQName ("p"), "1"));
    a3.addAttribute (IXdmNode.createAttribute (new XdmQName ("urn:x", "q"), "2"));
    assertFalse (_deq (_seq (a1), _seq (a3)));

    final IXdmNode a4 = _elem ("a", "x", _elem ("b", "y"));
    a4.addAttribute (IXdmNode.createAttribute (new XdmQName ("p"), "1"));
    assertFalse (_deq (_seq (a1), _seq (a4)));

    final IXdmNode a5 = _elem ("a", "x", _elem ("b", "y"));
    a5.addAttribute (IXdmNode.createAttribute (new XdmQName ("p"), "1"));
    a5.addAttribute (IXdmNode.createAttribute (new XdmQName ("urn:x", "q"), "3"));
    assertFalse (_deq (_seq (a1), _seq (a5)));

    // name differences
    assertFalse (_deq (_seq (_elem ("a")), _seq (_elem ("b"))));
    assertFalse (_deq (_seq (IXdmNode.createElement (new XdmQName ("urn:1", "a"))),
                       _seq (IXdmNode.createElement (new XdmQName ("urn:2", "a")))));
    // whitespace text matters for untyped elements
    assertFalse (_deq (_seq (_elem ("a", " ")), _seq (_elem ("a"))));

    // documents
    final IXdmNode d1 = IXdmNode.createDocument ();
    d1.appendChild (IXdmNode.createComment ("c"));
    d1.appendChild (_elem ("r", "t"));
    final IXdmNode d2 = IXdmNode.createDocument ();
    d2.appendChild (_elem ("r", "t"));
    assertTrue (_deq (_seq (d1), _seq (d2)));
    assertFalse (_deq (_seq (d1), _seq (_elem ("r", "t"))));

    // other kinds
    assertTrue (_deq (_seq (IXdmNode.createText ("t")), _seq (IXdmNode.createText ("t"))));
    assertFalse (_deq (_seq (IXdmNode.createText ("t")), _seq (IXdmNode.createComment ("t"))));
    assertTrue (_deq (_seq (IXdmNode.createComment ("c")), _seq (IXdmNode.createComment ("c"))));
    assertTrue (_deq (_seq (IXdmNode.createProcessingInstruction ("a", "d")),
                      _seq (IXdmNode.createProcessingInstruction ("a", "d"))));
    assertFalse (_deq (_seq (IXdmNode.createProcessingInstruction ("a", "d")),
                       _seq (IXdmNode.createProcessingInstruction ("b", "d"))));
    assertTrue (_deq (_seq (IXdmNode.createAttribute (new XdmQName ("a"), "v")),
                      _seq (IXdmNode.createAttribute (new XdmQName ("a"), "v"))));
    assertFalse (_deq (_seq (IXdmNode.createAttribute (new XdmQName ("a"), "v")),
                       _seq (IXdmNode.createAttribute (new XdmQName ("b"), "v"))));
    assertTrue (AtomicComparator.deepEqual (_seq (_elem ("a", "X")), _seq (_elem ("a", "x")), CASE_INSENSITIVE, CTX));
  }

  @Test
  public void testDeepEqualDeepTree ()
  {
    final IXdmNode aRoot1 = _elem ("a");
    final IXdmNode aRoot2 = _elem ("a");
    IXdmNode aCur1 = aRoot1;
    IXdmNode aCur2 = aRoot2;
    for (int n = 0; n < 100_000; ++n)
    {
      final IXdmNode aNew1 = _elem ("a");
      final IXdmNode aNew2 = _elem ("a");
      aCur1.appendChild (aNew1);
      aCur2.appendChild (aNew2);
      aCur1 = aNew1;
      aCur2 = aNew2;
    }
    aCur1.appendChild (IXdmNode.createText ("x"));
    aCur2.appendChild (IXdmNode.createText ("x"));
    assertTrue (_deq (_seq (aRoot1), _seq (aRoot2)));
    aCur2.appendChild (IXdmNode.createText ("y"));
    assertFalse (_deq (_seq (aRoot1), _seq (aRoot2)));
  }
}
