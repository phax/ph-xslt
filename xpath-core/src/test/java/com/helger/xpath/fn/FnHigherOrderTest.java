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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.helger.xpath.eval.ExprTestHelper.assertEvalError;
import static com.helger.xpath.eval.ExprTestHelper.assumeOps;
import static com.helger.xpath.eval.ExprTestHelper.assumeParser;
import static com.helger.xpath.eval.ExprTestHelper.bin;
import static com.helger.xpath.eval.ExprTestHelper.boolValue;
import static com.helger.xpath.eval.ExprTestHelper.call;
import static com.helger.xpath.eval.ExprTestHelper.cmp;
import static com.helger.xpath.eval.ExprTestHelper.ctx;
import static com.helger.xpath.eval.ExprTestHelper.dynCall;
import static com.helger.xpath.eval.ExprTestHelper.eval;
import static com.helger.xpath.eval.ExprTestHelper.fn;
import static com.helger.xpath.eval.ExprTestHelper.inline;
import static com.helger.xpath.eval.ExprTestHelper.ints;
import static com.helger.xpath.eval.ExprTestHelper.lit;
import static com.helger.xpath.eval.ExprTestHelper.longs;
import static com.helger.xpath.eval.ExprTestHelper.range;
import static com.helger.xpath.eval.ExprTestHelper.seq;
import static com.helger.xpath.eval.ExprTestHelper.str;
import static com.helger.xpath.eval.ExprTestHelper.stringValue;
import static com.helger.xpath.eval.ExprTestHelper.texts;
import static com.helger.xpath.eval.ExprTestHelper.var;

import java.util.List;

import org.junit.Test;

import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnHigherOrder}.
 *
 * @author Philip Helger
 */
public final class FnHigherOrderTest
{
  private static IExpr _qname (final XdmQName aName)
  {
    return lit (XdmAtomic.qname (aName));
  }

  @Test
  public void testForEachAndFilter ()
  {
    assumeParser ();
    assumeOps ();
    final IExpr aDouble = inline (bin (EBinaryOp.MULTIPLY, var ("x"), lit (2)), "x");
    assertEquals (longs (2, 4, 6), ints (eval (call (fn ("for-each"), range (lit (1), lit (3)), aDouble))));
    final IExpr aEven = inline (cmp (EComparisonOp.VALUE_EQ, bin (EBinaryOp.MODULO, var ("x"), lit (2)), lit (0)), "x");
    assertEquals (longs (2, 4), ints (eval (call (fn ("filter"), range (lit (1), lit (5)), aEven))));
    // a non-boolean predicate result is a type error
    assertEvalError ("XPTY0004", call (fn ("filter"), range (lit (1), lit (2)), inline (var ("x"), "x")));
    // wrong arity
    assertEvalError ("XPTY0004", call (fn ("for-each"), range (lit (1), lit (2)), inline (var ("x"), "x", "y")));
    // a map as the function
    final IExpr aMap = new IExpr.MapConstructorExpr (List.of (lit (1), lit (2)), List.of (str ("a"), str ("b")));
    assertEquals ("a b", texts (eval (call (fn ("for-each"), range (lit (1), lit (3)), aMap))));
  }

  @Test
  public void testFolds ()
  {
    assumeParser ();
    assumeOps ();
    final IExpr aConcat = inline (new IExpr.StringConcatExpr (List.of (var ("a"), var ("b"))), "a", "b");
    assertEquals ("abc", stringValue (eval (call (fn ("fold-left"), seq (str ("a"), str ("b"), str ("c")), str (""), aConcat))));
    assertEquals ("abc", stringValue (eval (call (fn ("fold-right"), seq (str ("a"), str ("b"), str ("c")), str (""), aConcat))));
    final IExpr aSub = inline (bin (EBinaryOp.SUBTRACT, var ("a"), var ("b")), "a", "b");
    // ((0 - 1) - 2) - 3 and 1 - (2 - (3 - 0))
    assertEquals (longs (-6), ints (eval (call (fn ("fold-left"), range (lit (1), lit (3)), lit (0), aSub))));
    assertEquals (longs (2), ints (eval (call (fn ("fold-right"), range (lit (1), lit (3)), lit (0), aSub))));
    assertTrue (eval (call (fn ("fold-left"), seq (), seq (), aSub)).isEmpty ());
  }

  @Test
  public void testForEachPair ()
  {
    assumeParser ();
    assumeOps ();
    final IExpr aAdd = inline (bin (EBinaryOp.ADD, var ("a"), var ("b")), "a", "b");
    assertEquals (longs (11, 22), ints (eval (call (fn ("for-each-pair"), seq (lit (1), lit (2), lit (3)), seq (lit (10), lit (20)), aAdd))));
  }

  @Test
  public void testSort ()
  {
    assumeParser ();
    assumeOps ();
    assertEquals (longs (1, 2, 3, 5), ints (eval (call (fn ("sort"), seq (lit (3), lit (1), lit (5), lit (2))))));
    assertEquals (longs (1, 2, 3),
                  ints (eval (call (fn ("sort"),
                                    seq (lit (3), lit (1), lit (2)),
                                    str ("http://www.w3.org/2005/xpath-functions/collation/codepoint")))));
    // by key, descending via negation; the sort is stable
    final IExpr aNeg = inline (new IExpr.UnaryExpr (true, var ("x")), "x");
    assertEquals (longs (5, 3, 1), ints (eval (call (fn ("sort"), seq (lit (3), lit (1), lit (5)), seq (), aNeg))));
    final IExpr aMod = inline (bin (EBinaryOp.MODULO, var ("x"), lit (2)), "x");
    assertEquals (longs (4, 2, 3, 1), ints (eval (call (fn ("sort"), seq (lit (3), lit (4), lit (1), lit (2)), seq (), aMod))));
    // key sequences compare item by item, a shorter one first
    final IExpr aKeys = inline (new IExpr.SequenceExpr (List.of (lit (1), var ("x"))), "x");
    assertEquals (longs (1, 2), ints (eval (call (fn ("sort"), seq (lit (2), lit (1)), seq (), aKeys))));
    assertEvalError ("FOCH0002", call (fn ("sort"), seq (lit (2), lit (1)), str ("http://unknown/collation")));
    assertEvalError ("XPTY0004", call (fn ("sort"), seq (lit (2), str ("a"))));
  }

  @Test
  public void testApply ()
  {
    assumeParser ();
    assumeOps ();
    final IExpr aSub = inline (bin (EBinaryOp.SUBTRACT, var ("a"), var ("b")), "a", "b");
    final IExpr aArgs = new IExpr.ArrayConstructorExpr (List.of (lit (10), lit (3)), false);
    assertEquals (longs (7), ints (eval (call (fn ("apply"), aSub, aArgs))));
    assertEvalError ("FOAP0001",
                     call (fn ("apply"), aSub, new IExpr.ArrayConstructorExpr (List.of (lit (10)), false)));
  }

  @Test
  public void testFunctionNameArityLookup ()
  {
    assumeParser ();
    assumeOps ();
    final XdmSequence aName = eval (call (fn ("function-name"), new IExpr.NamedFunctionRefExpr (fn ("not"), 1)));
    final XdmQName aQName = (XdmQName) ((XdmAtomic) aName.get (0)).getValue ();
    assertEquals (fn ("not"), aQName);
    assertEquals ("fn", aQName.prefix ());
    assertTrue (eval (call (fn ("function-name"), inline (var ("x"), "x"))).isEmpty ());
    assertEquals (longs (2), ints (eval (call (fn ("function-arity"), inline (var ("x"), "x", "y")))));
    assertEquals (longs (1),
                  ints (eval (call (fn ("function-arity"), new IExpr.ArrayConstructorExpr (List.of (), false)))));
    // function-lookup
    final IExpr aLookup = call (fn ("function-lookup"), _qname (fn ("not")), lit (1));
    assertTrue (boolValue (eval (dynCall (aLookup, seq ()))));
    assertTrue (eval (call (fn ("function-lookup"), _qname (fn ("not")), lit (5))).isEmpty ());
    assertTrue (eval (call (fn ("function-lookup"), _qname (new XdmQName ("not")), lit (1))).isEmpty ());
    // a focus-dependent function captures the focus of the lookup
    final IExpr aPos = call (fn ("function-lookup"), _qname (fn ("position")), lit (0));
    assertEquals (longs (3), ints (eval (dynCall (aPos), ctx ().withFocus (XdmAtomic.integer (1), 3, 4))));
  }
}
