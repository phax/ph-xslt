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
package com.helger.xpath.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.helger.xpath.eval.ExprTestHelper.arrayType;
import static com.helger.xpath.eval.ExprTestHelper.assertError;
import static com.helger.xpath.eval.ExprTestHelper.assumeOps;
import static com.helger.xpath.eval.ExprTestHelper.atomicType;
import static com.helger.xpath.eval.ExprTestHelper.bin;
import static com.helger.xpath.eval.ExprTestHelper.ctx;
import static com.helger.xpath.eval.ExprTestHelper.eval;
import static com.helger.xpath.eval.ExprTestHelper.funcType;
import static com.helger.xpath.eval.ExprTestHelper.inline;
import static com.helger.xpath.eval.ExprTestHelper.inlineTyped;
import static com.helger.xpath.eval.ExprTestHelper.lit;
import static com.helger.xpath.eval.ExprTestHelper.mapType;
import static com.helger.xpath.eval.ExprTestHelper.parse;
import static com.helger.xpath.eval.ExprTestHelper.qn;
import static com.helger.xpath.eval.ExprTestHelper.var;

import java.math.BigInteger;
import java.util.List;

import org.junit.Test;

import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.EOccurrence;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link SequenceTypeMatcher}.
 *
 * @author Philip Helger
 */
public final class SequenceTypeMatcherTest
{
  private static final SeqType INTEGER = atomicType (EAtomicType.INTEGER, EOccurrence.ONE);
  private static final SeqType DECIMAL = atomicType (EAtomicType.DECIMAL, EOccurrence.ONE);
  private static final SeqType DOUBLE = atomicType (EAtomicType.DOUBLE, EOccurrence.ONE);
  private static final SeqType FLOAT = atomicType (EAtomicType.FLOAT, EOccurrence.ONE);
  private static final SeqType STRING = atomicType (EAtomicType.STRING, EOccurrence.ONE);
  private static final SeqType STRING_OPT = atomicType (EAtomicType.STRING, EOccurrence.ZERO_OR_ONE);
  private static final SeqType INTEGER_OPT = atomicType (EAtomicType.INTEGER, EOccurrence.ZERO_OR_ONE);
  private static final SeqType ITEM = SeqType.of (ItemType.ITEM, EOccurrence.ONE);

  private static NodeTest _element (final String sName, final String sType, final boolean bNillable)
  {
    return new NodeTest (ENodeTestKind.ELEMENT,
                         sName == null,
                         false,
                         false,
                         sName == null ? null : qn (sName),
                         sType == null ? null : new XdmQName (XdmConstants.NS_XS, sType),
                         bNillable,
                         null);
  }

  private static SeqType _node (final NodeTest aTest)
  {
    return SeqType.of (ItemType.node (aTest), EOccurrence.ONE);
  }

  @Test
  public void testMatchesOccurrence ()
  {
    final XdmSequence aTwo = XdmSequence.of (XdmAtomic.integer (1), XdmAtomic.integer (2));
    assertFalse (SequenceTypeMatcher.matches (aTwo, INTEGER));
    assertFalse (SequenceTypeMatcher.matches (aTwo, INTEGER_OPT));
    assertTrue (SequenceTypeMatcher.matches (aTwo, atomicType (EAtomicType.INTEGER, EOccurrence.ONE_OR_MORE)));
    assertTrue (SequenceTypeMatcher.matches (XdmSequence.EMPTY, INTEGER_OPT));
    assertFalse (SequenceTypeMatcher.matches (XdmSequence.EMPTY, INTEGER));
    assertTrue (SequenceTypeMatcher.matches (XdmSequence.EMPTY, SeqType.EMPTY_SEQUENCE));
    assertFalse (SequenceTypeMatcher.matches (aTwo, SeqType.EMPTY_SEQUENCE));
    assertTrue (SequenceTypeMatcher.matches (aTwo, SeqType.ANY));
  }

  @Test
  public void testMatchesAtomic ()
  {
    final XdmAtomic aByte = XdmAtomic.of (EAtomicType.BYTE, BigInteger.ONE);
    assertTrue (SequenceTypeMatcher.matchesItem (aByte, ItemType.atomic (EAtomicType.INTEGER)));
    assertTrue (SequenceTypeMatcher.matchesItem (aByte, ItemType.atomic (EAtomicType.NUMERIC)));
    assertTrue (SequenceTypeMatcher.matchesItem (aByte, ItemType.atomic (EAtomicType.ANY_ATOMIC_TYPE)));
    assertFalse (SequenceTypeMatcher.matchesItem (aByte, ItemType.atomic (EAtomicType.UNSIGNED_BYTE)));
    assertFalse (SequenceTypeMatcher.matchesItem (aByte, ItemType.atomic (EAtomicType.ERROR)));
    assertFalse (SequenceTypeMatcher.matchesItem (XdmAtomic.string ("a"), ItemType.atomic (EAtomicType.NUMERIC)));
    assertFalse (SequenceTypeMatcher.matchesItem (parse ("<a/>"), ItemType.atomic (EAtomicType.ANY_ATOMIC_TYPE)));
    assertTrue (SequenceTypeMatcher.matchesItem (parse ("<a/>"), ItemType.ITEM));
  }

  @Test
  public void testMatchesNodes ()
  {
    final IXdmNode aDoc = parse ("<a x='1'>t<!--c--></a>");
    final IXdmNode aA = aDoc.getChildAtIndex (0);
    assertTrue (SequenceTypeMatcher.matchesItem (aA, ItemType.node (_element ("a", null, false))));
    assertFalse (SequenceTypeMatcher.matchesItem (aA, ItemType.node (_element ("b", null, false))));
    assertTrue (SequenceTypeMatcher.matchesItem (aA, ItemType.node (_element (null, null, false))));
    assertTrue (SequenceTypeMatcher.matchesItem (aA.getChildAtIndex (0), ItemType.node (NodeTest.ofKind (ENodeTestKind.TEXT))));
    assertTrue (SequenceTypeMatcher.matchesItem (aA.getChildAtIndex (1), ItemType.node (NodeTest.ofKind (ENodeTestKind.COMMENT))));
    assertTrue (SequenceTypeMatcher.matchesItem (aA.getAttributeAtIndex (0), ItemType.node (NodeTest.ofKind (ENodeTestKind.ATTRIBUTE))));
    assertFalse (SequenceTypeMatcher.matchesItem (aA, ItemType.node (NodeTest.ofKind (ENodeTestKind.ATTRIBUTE))));
    assertTrue (SequenceTypeMatcher.matchesItem (aDoc, ItemType.node (NodeTest.ofKind (ENodeTestKind.DOCUMENT))));
    assertFalse (SequenceTypeMatcher.matchesItem (XdmAtomic.integer (1), ItemType.node (NodeTest.ANY_NODE)));
  }

  @Test
  public void testNodeSubtypes ()
  {
    assertTrue (SequenceTypeMatcher.isSubtype (_node (_element ("a", null, false)), _node (_element (null, null, false))));
    assertTrue (SequenceTypeMatcher.isSubtype (_node (_element ("a", null, false)), _node (NodeTest.ANY_NODE)));
    assertFalse (SequenceTypeMatcher.isSubtype (_node (_element (null, null, false)), _node (_element ("a", null, false))));
    // element(a) is element(a, xs:anyType?), which is not element(*, xs:anyType)
    assertFalse (SequenceTypeMatcher.isSubtype (_node (_element ("a", null, false)),
                                                _node (_element (null, "anyType", false))));
    assertTrue (SequenceTypeMatcher.isSubtype (_node (_element ("a", null, false)), _node (_element (null, "anyType", true))));
    assertTrue (SequenceTypeMatcher.isSubtype (_node (_element ("a", "untyped", false)),
                                               _node (_element (null, "anyType", false))));
    assertFalse (SequenceTypeMatcher.isSubtype (_node (_element ("a", "anyType", false)),
                                                _node (_element (null, "untyped", false))));
    assertFalse (SequenceTypeMatcher.isSubtype (_node (NodeTest.ofKind (ENodeTestKind.TEXT)),
                                                _node (_element (null, null, false))));
  }

  @Test
  public void testSequenceSubtypes ()
  {
    assertTrue (SequenceTypeMatcher.isSubtype (INTEGER, DECIMAL));
    assertFalse (SequenceTypeMatcher.isSubtype (DECIMAL, INTEGER));
    assertTrue (SequenceTypeMatcher.isSubtype (INTEGER, INTEGER_OPT));
    assertFalse (SequenceTypeMatcher.isSubtype (INTEGER_OPT, INTEGER));
    assertFalse (SequenceTypeMatcher.isSubtype (atomicType (EAtomicType.INTEGER, EOccurrence.ZERO_OR_MORE), INTEGER_OPT));
    assertTrue (SequenceTypeMatcher.isSubtype (SeqType.EMPTY_SEQUENCE, INTEGER_OPT));
    assertFalse (SequenceTypeMatcher.isSubtype (SeqType.EMPTY_SEQUENCE, INTEGER));
    assertTrue (SequenceTypeMatcher.isSubtype (atomicType (EAtomicType.ERROR, EOccurrence.ONE), STRING));
    assertTrue (SequenceTypeMatcher.isSubtype (DOUBLE, atomicType (EAtomicType.NUMERIC, EOccurrence.ONE)));
    assertFalse (SequenceTypeMatcher.isSubtype (atomicType (EAtomicType.NUMERIC, EOccurrence.ONE), DOUBLE));
    assertTrue (SequenceTypeMatcher.isSubtype (null, SeqType.ANY));
    assertFalse (SequenceTypeMatcher.isSubtype (null, ITEM));
  }

  @Test
  public void testFunctionSubtypes ()
  {
    final SeqType aIntToString = SeqType.of (funcType (STRING, INTEGER), EOccurrence.ONE);
    final SeqType aIntToAny = SeqType.of (funcType (SeqType.ANY, INTEGER), EOccurrence.ONE);
    final SeqType aDecToString = SeqType.of (funcType (STRING, DECIMAL), EOccurrence.ONE);
    final SeqType aAnyFunc = SeqType.of (new ItemType (ItemType.Kind.FUNCTION, null, null, true, null, null, null, null),
                                         EOccurrence.ONE);
    assertTrue (SequenceTypeMatcher.isSubtype (aIntToString, aIntToAny));
    assertFalse (SequenceTypeMatcher.isSubtype (aIntToAny, aIntToString));
    // contravariant parameters
    assertTrue (SequenceTypeMatcher.isSubtype (aDecToString, aIntToString));
    assertFalse (SequenceTypeMatcher.isSubtype (aIntToString, aDecToString));
    assertTrue (SequenceTypeMatcher.isSubtype (aIntToString, aAnyFunc));
    assertFalse (SequenceTypeMatcher.isSubtype (aAnyFunc, aIntToString));

    // map(xs:string, xs:integer) is function(xs:anyAtomicType) as xs:integer?
    final SeqType aMap = SeqType.of (mapType (EAtomicType.STRING, INTEGER), EOccurrence.ONE);
    assertTrue (SequenceTypeMatcher.isSubtype (aMap, SeqType.of (funcType (INTEGER_OPT, STRING), EOccurrence.ONE)));
    assertFalse (SequenceTypeMatcher.isSubtype (aMap, SeqType.of (funcType (INTEGER, STRING), EOccurrence.ONE)));
    assertTrue (SequenceTypeMatcher.isSubtype (aMap, SeqType.of (mapType (EAtomicType.ANY_ATOMIC_TYPE, DECIMAL), EOccurrence.ONE)));
    // array(xs:integer) is function(xs:integer) as xs:integer
    final SeqType aArray = SeqType.of (arrayType (INTEGER), EOccurrence.ONE);
    assertTrue (SequenceTypeMatcher.isSubtype (aArray, SeqType.of (funcType (INTEGER, INTEGER), EOccurrence.ONE)));
    assertFalse (SequenceTypeMatcher.isSubtype (aArray, SeqType.of (funcType (INTEGER, STRING), EOccurrence.ONE)));
    assertTrue (SequenceTypeMatcher.isSubtype (aArray, SeqType.of (arrayType (DECIMAL), EOccurrence.ONE)));
  }

  @Test
  public void testMatchesMapsArraysFunctions ()
  {
    assumeOps ();
    final XdmMap aMap = XdmMap.EMPTY.put (XdmAtomic.string ("a"), XdmSequence.of (XdmAtomic.integer (1)));
    assertTrue (SequenceTypeMatcher.matchesItem (aMap, mapType (EAtomicType.STRING, INTEGER)));
    assertFalse (SequenceTypeMatcher.matchesItem (aMap, mapType (EAtomicType.INTEGER, INTEGER)));
    assertFalse (SequenceTypeMatcher.matchesItem (aMap, mapType (EAtomicType.STRING, STRING)));
    assertTrue (SequenceTypeMatcher.matchesItem (aMap, funcType (INTEGER_OPT, STRING)));
    assertFalse (SequenceTypeMatcher.matchesItem (aMap, funcType (INTEGER, STRING)));
    assertFalse (SequenceTypeMatcher.matchesItem (aMap, arrayType (INTEGER)));

    final XdmArray aArray = XdmArray.of (List.of (XdmSequence.of (XdmAtomic.integer (1))));
    assertTrue (SequenceTypeMatcher.matchesItem (aArray, arrayType (INTEGER)));
    assertTrue (SequenceTypeMatcher.matchesItem (aArray, funcType (DECIMAL, INTEGER)));
    assertFalse (SequenceTypeMatcher.matchesItem (aArray, funcType (DECIMAL, STRING)));
    assertFalse (SequenceTypeMatcher.matchesItem (aArray, mapType (EAtomicType.STRING, INTEGER)));

    final IXdmFunction aFunc = (IXdmFunction) eval (inlineTyped (var ("x"),
                                                                 INTEGER,
                                                                 new IExpr.Param (qn ("x"), DECIMAL))).get (0);
    assertTrue (SequenceTypeMatcher.matchesItem (aFunc, funcType (DECIMAL, INTEGER)));
    assertFalse (SequenceTypeMatcher.matchesItem (aFunc, funcType (INTEGER, SeqType.ANY)));
    assertFalse (SequenceTypeMatcher.matchesItem (aFunc, funcType (INTEGER, INTEGER, INTEGER)));
  }

  @Test
  public void testConvertAtomic ()
  {
    assumeOps ();
    final DynamicContext aCtx = ctx ();
    final XdmSequence aUntyped = XdmSequence.of (XdmAtomic.untyped ("12"));
    assertEquals (EAtomicType.INTEGER,
                  ((XdmAtomic) SequenceTypeMatcher.convert (aUntyped, INTEGER, aCtx, "XPTY0004").get (0)).getType ());
    assertEquals (EAtomicType.DOUBLE,
                  ((XdmAtomic) SequenceTypeMatcher.convert (aUntyped,
                                                            atomicType (EAtomicType.NUMERIC, EOccurrence.ONE),
                                                            aCtx,
                                                            "XPTY0004").get (0)).getType ());
    assertSame (aUntyped,
                SequenceTypeMatcher.convert (aUntyped,
                                             atomicType (EAtomicType.ANY_ATOMIC_TYPE, EOccurrence.ONE),
                                             aCtx,
                                             "XPTY0004"));
    // numeric promotion
    final XdmSequence aInt = XdmSequence.of (XdmAtomic.integer (3));
    assertEquals (EAtomicType.DOUBLE,
                  ((XdmAtomic) SequenceTypeMatcher.convert (aInt, DOUBLE, aCtx, "XPTY0004").get (0)).getType ());
    assertEquals (EAtomicType.FLOAT,
                  ((XdmAtomic) SequenceTypeMatcher.convert (aInt, FLOAT, aCtx, "XPTY0004").get (0)).getType ());
    assertSame (aInt, SequenceTypeMatcher.convert (aInt, DECIMAL, aCtx, "XPTY0004"));
    assertError ("XPTY0004",
                 () -> SequenceTypeMatcher.convert (XdmSequence.of (XdmAtomic.dbl (1)), FLOAT, aCtx, "XPTY0004"));
    // URI promotion
    assertEquals (EAtomicType.STRING,
                  ((XdmAtomic) SequenceTypeMatcher.convert (XdmSequence.of (XdmAtomic.anyUri ("urn:x")),
                                                            STRING,
                                                            aCtx,
                                                            "XPTY0004").get (0)).getType ());
    // atomisation of a node
    final IXdmNode aDoc = parse ("<a>7</a>");
    final XdmSequence aConv = SequenceTypeMatcher.convert (XdmSequence.of (aDoc), INTEGER_OPT, aCtx, "XPTY0004");
    assertEquals (7, ((XdmAtomic) aConv.get (0)).getBigInteger ().intValue ());
    // cardinality with a custom code
    assertError ("XTTE0780", () -> SequenceTypeMatcher.convert (XdmSequence.EMPTY, STRING, aCtx, "XTTE0780"));
    assertError ("XPTY0004", () -> SequenceTypeMatcher.convert (aInt, STRING_OPT, aCtx, "XPTY0004"));
    assertError ("XPTY0004", () -> SequenceTypeMatcher.convert (aInt, SeqType.EMPTY_SEQUENCE, aCtx, "XPTY0004"));
    // cast failure of an untyped value
    assertError ("FORG0001",
                 () -> SequenceTypeMatcher.convert (XdmSequence.of (XdmAtomic.untyped ("x")), INTEGER, aCtx, "XPTY0004"));
    // item()* passes everything unchanged
    assertSame (aUntyped, SequenceTypeMatcher.convert (aUntyped, SeqType.ANY, aCtx, "XPTY0004"));
  }

  @Test
  public void testFunctionCoercion ()
  {
    assumeOps ();
    final DynamicContext aCtx = ctx ();
    // function($x) { $x + 1 } coerced to function(xs:integer) as xs:string
    final XdmSequence aFunc = eval (inline (bin (EBinaryOp.ADD, var ("x"), lit (1)), "x"));
    final SeqType aExpected = SeqType.of (funcType (STRING, INTEGER), EOccurrence.ONE);
    final XdmSequence aCoerced = SequenceTypeMatcher.convert (aFunc, aExpected, aCtx, "XPTY0004");
    assertTrue (aCoerced.get (0) instanceof CoercedFunctionItem);
    assertTrue (SequenceTypeMatcher.matches (aCoerced, aExpected));
    // the deferred return type check fails at call time
    assertError ("XPTY0004",
                 () -> XPathEvaluator.callFunction (aCtx, aCoerced.get (0), List.of (XdmSequence.of (XdmAtomic.integer (1)))));
    // a parameter type check also happens at call time
    assertError ("XPTY0004",
                 () -> XPathEvaluator.callFunction (aCtx, aCoerced.get (0), List.of (XdmSequence.of (XdmAtomic.string ("a")))));
    // the arity must match immediately
    assertError ("XPTY0004",
                 () -> SequenceTypeMatcher.convert (aFunc,
                                                    SeqType.of (funcType (STRING, INTEGER, INTEGER), EOccurrence.ONE),
                                                    aCtx,
                                                    "XPTY0004"));
    // a function that is already a subtype is passed unchanged
    final XdmSequence aTyped = eval (inlineTyped (var ("x"), STRING, new IExpr.Param (qn ("x"), SeqType.ANY)));
    assertSame (aTyped, SequenceTypeMatcher.convert (aTyped, aExpected, aCtx, "XPTY0004"));
    // a non-function is no function
    assertError ("XPTY0004",
                 () -> SequenceTypeMatcher.convert (XdmSequence.of (XdmAtomic.integer (1)), aExpected, aCtx, "XPTY0004"));
    // a map coerced to a typed function
    final XdmMap aMap = XdmMap.EMPTY.put (XdmAtomic.string ("a"), XdmSequence.of (XdmAtomic.integer (1)));
    final XdmSequence aCoercedMap = SequenceTypeMatcher.convert (XdmSequence.of (aMap),
                                                                 SeqType.of (funcType (DOUBLE, STRING), EOccurrence.ONE),
                                                                 aCtx,
                                                                 "XPTY0004");
    final XdmSequence aRes = XPathEvaluator.callFunction (aCtx,
                                                          aCoercedMap.get (0),
                                                          List.of (XdmSequence.of (XdmAtomic.string ("a"))));
    assertEquals (EAtomicType.DOUBLE, ((XdmAtomic) aRes.get (0)).getType ());
  }

  @Test
  public void testGetAsString ()
  {
    assertEquals ("xs:integer?", SequenceTypeMatcher.getAsString (INTEGER_OPT));
    assertEquals ("empty-sequence()", SequenceTypeMatcher.getAsString (SeqType.EMPTY_SEQUENCE));
    assertEquals ("item()*", SequenceTypeMatcher.getAsString (SeqType.ANY));
    assertEquals ("(function(xs:integer) as xs:string)*",
                  SequenceTypeMatcher.getAsString (SeqType.of (funcType (STRING, INTEGER), EOccurrence.ZERO_OR_MORE)));
    assertEquals ("element(a)", SequenceTypeMatcher.getAsString (_node (_element ("a", null, false))));
    assertEquals ("map(xs:string, xs:integer)", SequenceTypeMatcher.getAsString (SeqType.of (mapType (EAtomicType.STRING, INTEGER), EOccurrence.ONE)));
  }

  private static String _evalCompat (final String sExpr, final boolean bCompat)
  {
    final StaticContext aStatic = new StaticContext ().setXPath10Compatibility (bCompat);
    final XdmSequence aRes = XPathCompiler.compile (sExpr, aStatic).evaluate (new DynamicContext (aStatic));
    return ((XdmAtomic) aRes.getFirst ()).getLexical ();
  }

  @Test
  public void testXPath10CompatibilityConversion ()
  {
    // fn:number for xs:double/xs:numeric, fn:string for xs:string, the first
    // item for a single item type
    assertEquals ("NaN", _evalCompat ("format-number('foo', '#')", true));
    assertEquals ("12", _evalCompat ("format-number('12', '#')", true));
    assertEquals ("3", _evalCompat ("string(string-length((123, 4)))", true));
    assertEquals ("3", _evalCompat ("string(abs('-3'))", true));
    try
    {
      _evalCompat ("format-number('foo', '#')", false);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPTY0004", ex.getCode ());
    }
  }
}
