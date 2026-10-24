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

import static com.helger.xpath.eval.ExprTestHelper.absPath;
import static com.helger.xpath.eval.ExprTestHelper.anyName;
import static com.helger.xpath.eval.ExprTestHelper.assertEvalError;
import static com.helger.xpath.eval.ExprTestHelper.assumeOps;
import static com.helger.xpath.eval.ExprTestHelper.assumeParser;
import static com.helger.xpath.eval.ExprTestHelper.atomicType;
import static com.helger.xpath.eval.ExprTestHelper.bin;
import static com.helger.xpath.eval.ExprTestHelper.boolValue;
import static com.helger.xpath.eval.ExprTestHelper.call;
import static com.helger.xpath.eval.ExprTestHelper.child;
import static com.helger.xpath.eval.ExprTestHelper.cmp;
import static com.helger.xpath.eval.ExprTestHelper.ctx;
import static com.helger.xpath.eval.ExprTestHelper.dbl;
import static com.helger.xpath.eval.ExprTestHelper.descOrSelf;
import static com.helger.xpath.eval.ExprTestHelper.dot;
import static com.helger.xpath.eval.ExprTestHelper.dynCall;
import static com.helger.xpath.eval.ExprTestHelper.eval;
import static com.helger.xpath.eval.ExprTestHelper.filter;
import static com.helger.xpath.eval.ExprTestHelper.fn;
import static com.helger.xpath.eval.ExprTestHelper.forExpr;
import static com.helger.xpath.eval.ExprTestHelper.hole;
import static com.helger.xpath.eval.ExprTestHelper.inline;
import static com.helger.xpath.eval.ExprTestHelper.inlineTyped;
import static com.helger.xpath.eval.ExprTestHelper.ints;
import static com.helger.xpath.eval.ExprTestHelper.let;
import static com.helger.xpath.eval.ExprTestHelper.lit;
import static com.helger.xpath.eval.ExprTestHelper.longs;
import static com.helger.xpath.eval.ExprTestHelper.nameTest;
import static com.helger.xpath.eval.ExprTestHelper.names;
import static com.helger.xpath.eval.ExprTestHelper.parse;
import static com.helger.xpath.eval.ExprTestHelper.path;
import static com.helger.xpath.eval.ExprTestHelper.pathFrom;
import static com.helger.xpath.eval.ExprTestHelper.postfix;
import static com.helger.xpath.eval.ExprTestHelper.qn;
import static com.helger.xpath.eval.ExprTestHelper.range;
import static com.helger.xpath.eval.ExprTestHelper.seq;
import static com.helger.xpath.eval.ExprTestHelper.step;
import static com.helger.xpath.eval.ExprTestHelper.str;
import static com.helger.xpath.eval.ExprTestHelper.stringValue;
import static com.helger.xpath.eval.ExprTestHelper.texts;
import static com.helger.xpath.eval.ExprTestHelper.var;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.EComparisonOp;
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
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link XPathEvaluator}.
 *
 * @author Philip Helger
 */
public final class XPathEvaluatorTest
{
  private static final String XML = "<r><a id='1'><b>x</b><b>y</b></a><a id='2'><b>z</b><c/></a><d/></r>";

  private static XdmNode _doc ()
  {
    return parse (XML);
  }

  @NonNull
  private static String _adaptive (@NonNull final String sExpr,
                                   final boolean bCompat10,
                                   @Nullable final XdmNode aContextNode)
  {
    final CompiledXPath aXP = XPathCompiler.compile (sExpr, new StaticContext ().setXPath10Compatibility (bCompat10));
    final DynamicContext aCtx = aXP.createDynamicContext ();
    final XdmSequence aResult = aXP.evaluate (aContextNode == null ? aCtx : aCtx.withFocus (aContextNode, 1, 1));
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aResult)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (aItem instanceof final XdmAtomic aAtomic ? aAtomic.getLexical () : aItem.toString ());
    }
    return aSB.toString ();
  }

  private static void _assertCode (@NonNull final String sCode,
                                   @NonNull final String sExpr,
                                   final boolean bCompat10,
                                   @Nullable final XdmNode aContextNode)
  {
    try
    {
      _adaptive (sExpr, bCompat10, aContextNode);
      fail ("Expected " + sCode + " for " + sExpr);
    }
    catch (final XPathException ex)
    {
      assertEquals (sCode, ex.getCode ());
    }
  }

  @Test
  public void testLiteralAndSequence ()
  {
    assertEquals (longs (1, 2, 3), ints (eval (seq (lit (1), seq (), seq (lit (2), lit (3))))));
    assertTrue (eval (seq ()).isEmpty ());
  }

  @Test
  public void testRange ()
  {
    assertEquals (longs (2, 3, 4), ints (eval (range (lit (2), lit (4)))));
    assertTrue (eval (range (lit (4), lit (2))).isEmpty ());
    assertTrue (eval (range (seq (), lit (2))).isEmpty ());
    final BigInteger aBig = BigInteger.TEN.pow (21);
    final XdmSequence aRes = eval (range (lit (aBig), lit (aBig.add (BigInteger.TWO))));
    assertEquals (3, aRes.size ());
    assertEquals (aBig.add (BigInteger.ONE), ((XdmAtomic) aRes.get (1)).getBigInteger ());
    assertEvalError ("XPDY0130", range (lit (1), lit (100_000_000)));
    assertEvalError ("XPTY0004", range (dbl (1), lit (3)));
    assertEvalError ("XPTY0004", range (seq (lit (1), lit (2)), lit (3)));
  }

  @Test
  public void testContextItem ()
  {
    assertEvalError ("XPDY0002", dot ());
    assertEquals (longs (5), ints (eval (dot (), ctx (XdmAtomic.integer (5)))));
    assertEvalError ("XPDY0002", var ("undefined"));
  }

  @Test
  public void testLetAndFor ()
  {
    assertEquals (longs (7), ints (eval (let ("x", lit (7), var ("x")))));
    assertEquals (longs (1, 1, 2, 2), ints (eval (forExpr ("x", range (lit (1), lit (2)), seq (var ("x"), var ("x"))))));
    final IExpr aNested = new IExpr.ForExpr (List.of (new IExpr.Binding (qn ("a"), null, range (lit (1), lit (2))),
                                                      new IExpr.Binding (qn ("b"), null, range (lit (5), lit (6)))),
                                             seq (var ("a"), var ("b")));
    assertEquals (longs (1, 5, 1, 6, 2, 5, 2, 6), ints (eval (aNested)));
  }

  @Test
  public void testChildAndDescendant ()
  {
    final XdmNode aDoc = _doc ();
    final DynamicContext aCtx = ctx (aDoc);
    assertEquals ("r", names (eval (path (child ("r")), aCtx)));
    assertEquals ("a a", names (eval (path (child ("r"), child ("a")), aCtx)));
    assertEquals ("b b b", names (eval (path (child ("r"), child ("a"), child ("b")), aCtx)));
    // //b
    assertEquals ("b b b", names (eval (absPath (descOrSelf (), child ("b")), aCtx)));
    assertEquals ("a b b a b c d", names (eval (path (step (EAxis.DESCENDANT, anyName ())), ctx (aDoc.getChildAtIndex (0)))));
    assertEquals ("x y z", names (eval (path (step (EAxis.DESCENDANT, NodeTest.ofKind (ENodeTestKind.TEXT))), aCtx)));
    // absolute root
    assertEquals ("#doc", names (eval (absPath (), ctx (aDoc.getChildAtIndex (0).getChildAtIndex (0)))));
  }

  @Test
  public void testPositionalPredicates ()
  {
    final XdmNode aDoc = _doc ();
    final DynamicContext aCtx = ctx (aDoc);
    // //a/b[1] is the first b child of each a
    final XdmSequence aFirstB = eval (path (step (EAxis.DESCENDANT, nameTest ("a")), child ("b", lit (1))), aCtx);
    assertEquals ("x z", texts (aFirstB));
    // (//b)[2]
    final XdmSequence aSecond = eval (filter (absPath (descOrSelf (), child ("b")), lit (2)), aCtx);
    assertEquals ("y", ((XdmNode) aSecond.get (0)).getStringValue ());
    assertTrue (eval (filter (absPath (descOrSelf (), child ("b")), dbl (1.5)), aCtx).isEmpty ());
    assertTrue (eval (filter (absPath (descOrSelf (), child ("b")), lit (0)), aCtx).isEmpty ());
    assertEquals (1, eval (filter (absPath (descOrSelf (), child ("b")), lit (XdmAtomic.decimal (new BigDecimal ("3.0")))), aCtx).size ());
  }

  @Test
  public void testReverseAxes ()
  {
    final XdmNode aDoc = _doc ();
    final XdmNode aC = aDoc.getChildAtIndex (0).getChildAtIndex (1).getChildAtIndex (1);
    assertEquals ("c", aC.getName ().localName ());
    final DynamicContext aCtx = ctx (aC);
    // ancestor::* in document order
    assertEquals ("r a", names (eval (path (step (EAxis.ANCESTOR, anyName ())), aCtx)));
    // ancestor::*[1] is the nearest
    assertEquals ("a", names (eval (path (step (EAxis.ANCESTOR, anyName (), lit (1))), aCtx)));
    assertEquals ("r", names (eval (path (step (EAxis.ANCESTOR, anyName (), lit (2))), aCtx)));
    // preceding-sibling::*[1] is the nearest sibling
    assertEquals ("b", names (eval (path (step (EAxis.PRECEDING_SIBLING, anyName (), lit (1))), aCtx)));
    // preceding::b in document order, preceding::b[1] is "z"
    final XdmSequence aPreceding = eval (path (step (EAxis.PRECEDING, nameTest ("b"))), aCtx);
    assertEquals (3, aPreceding.size ());
    assertEquals ("x", ((XdmNode) aPreceding.get (0)).getStringValue ());
    assertEquals ("z",
                  ((XdmNode) eval (path (step (EAxis.PRECEDING, nameTest ("b"), lit (1))), aCtx).get (0)).getStringValue ());
    // preceding excludes ancestors
    assertTrue (eval (path (step (EAxis.PRECEDING, nameTest ("a"), lit (1))), aCtx).size () == 1);
    assertEquals ("1",
                  ((XdmNode) eval (path (step (EAxis.PRECEDING, nameTest ("a")), step (EAxis.ATTRIBUTE, nameTest ("id"))),
                                   aCtx).get (0)).getValue ());
    // parent and ancestor-or-self
    assertEquals ("a", names (eval (path (step (EAxis.PARENT, NodeTest.ANY_NODE)), aCtx)));
    assertEquals ("#doc r a c", names (eval (path (step (EAxis.ANCESTOR_OR_SELF, NodeTest.ANY_NODE)), aCtx)));
  }

  @Test
  public void testForwardAxes ()
  {
    final XdmNode aDoc = _doc ();
    final XdmNode aA1 = aDoc.getChildAtIndex (0).getChildAtIndex (0);
    final DynamicContext aCtx = ctx (aA1);
    assertEquals ("a d", names (eval (path (step (EAxis.FOLLOWING_SIBLING, anyName ())), aCtx)));
    // following excludes descendants
    assertEquals ("a b c d", names (eval (path (step (EAxis.FOLLOWING, anyName ())), aCtx)));
    assertEquals ("a", names (eval (path (step (EAxis.SELF, anyName ())), aCtx)));
    assertTrue (eval (path (step (EAxis.SELF, nameTest ("b"))), aCtx).isEmpty ());
    assertEquals ("a b b", names (eval (path (step (EAxis.DESCENDANT_OR_SELF, anyName ())), aCtx)));
    // attribute axis and following from an attribute
    final XdmNode aId = aA1.getAttributeAtIndex (0);
    assertEquals ("id", names (eval (path (step (EAxis.ATTRIBUTE, anyName ())), aCtx)));
    assertEquals ("b b a b c d", names (eval (path (step (EAxis.FOLLOWING, anyName ())), ctx (aId))));
    assertTrue (eval (path (step (EAxis.PRECEDING, anyName ())), ctx (aId)).isEmpty ());
    assertTrue (eval (path (step (EAxis.FOLLOWING_SIBLING, NodeTest.ANY_NODE)), ctx (aId)).isEmpty ());
    assertEquals ("a", names (eval (path (step (EAxis.PARENT, NodeTest.ANY_NODE)), ctx (aId))));
    // attribute() kind test on the child axis selects nothing
    assertTrue (eval (path (step (EAxis.CHILD, NodeTest.ofKind (ENodeTestKind.ATTRIBUTE))), aCtx).isEmpty ());
    assertEquals ("id",
                  names (eval (path (step (EAxis.ATTRIBUTE, NodeTest.ofKind (ENodeTestKind.ATTRIBUTE))), aCtx)));
  }

  @Test
  public void testNamespaceAxis ()
  {
    final XdmNode aDoc = parse ("<a xmlns='urn:d' xmlns:p='urn:p' x='1'><b xmlns:q='urn:q'/></a>");
    final XdmNode aA = aDoc.getChildAtIndex (0);
    final XdmNode aB = aA.getChildAtIndex (0);
    final IExpr aNsAxis = path (step (EAxis.NAMESPACE, NodeTest.ofKind (ENodeTestKind.NAMESPACE_NODE)));
    final XdmSequence aNsA = eval (aNsAxis, ctx (aA));
    // xml, default, p
    assertEquals (3, aNsA.size ());
    final XdmSequence aNsB = eval (aNsAxis, ctx (aB));
    assertEquals (4, aNsB.size ());
    // stable identity
    assertSame (aNsB.get (0), eval (aNsAxis, ctx (aB)).get (0));
    // name test on the namespace axis matches the prefix
    final XdmSequence aP = eval (path (step (EAxis.NAMESPACE, nameTest ("p"))), ctx (aB));
    assertEquals (1, aP.size ());
    assertEquals ("urn:p", ((XdmNode) aP.get (0)).getStringValue ());
    assertSame (aB, ((XdmNode) aP.get (0)).getParent ());
    // document order: element < namespace nodes < attributes < children
    final XdmNode aNs = (XdmNode) aNsA.get (1);
    assertTrue (XdmNode.compareDocumentOrder (aA, aNs) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aNs, aA.getAttributeAtIndex (0)) < 0);
    assertTrue (XdmNode.compareDocumentOrder ((XdmNode) aNsA.get (0), aNs) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aNs, aB) < 0);
    // namespace nodes are not on the following axis, but the following axis works from them
    assertEquals ("b", names (eval (path (step (EAxis.FOLLOWING, anyName ())), ctx (aNs))));
    // union keeps them in order without duplicates
    final XdmSequence aUnion = eval (new IExpr.SetExpr (IExpr.SetExpr.ESetOp.UNION, aNsAxis, aNsAxis), ctx (aA));
    assertEquals (3, aUnion.size ());
  }

  @Test
  public void testUnionIntersectExcept ()
  {
    final XdmNode aDoc = _doc ();
    final DynamicContext aCtx = ctx (aDoc.getChildAtIndex (0));
    final IExpr aD = path (child ("d"));
    final IExpr aAs = path (child ("a"));
    assertEquals ("a a d", names (eval (new IExpr.SetExpr (IExpr.SetExpr.ESetOp.UNION, aD, aAs), aCtx)));
    assertEquals ("a a",
                  names (eval (new IExpr.SetExpr (IExpr.SetExpr.ESetOp.INTERSECT,
                                                  path (step (EAxis.CHILD, anyName ())),
                                                  aAs),
                               aCtx)));
    assertEquals ("d",
                  names (eval (new IExpr.SetExpr (IExpr.SetExpr.ESetOp.EXCEPT,
                                                  path (step (EAxis.CHILD, anyName ())),
                                                  aAs),
                               aCtx)));
    assertEvalError ("XPTY0004", new IExpr.SetExpr (IExpr.SetExpr.ESetOp.UNION, lit (1), aAs), aCtx);
  }

  @Test
  public void testPathErrors ()
  {
    final XdmNode aDoc = _doc ();
    // axis step without focus
    assertEvalError ("XPDY0002", path (child ("r")));
    assertEvalError ("XPDY0002", absPath ());
    // axis step with an atomic focus
    assertEvalError ("XPTY0020", path (child ("r")), ctx (XdmAtomic.integer (1)));
    assertEvalError ("XPTY0020", absPath (), ctx (XdmAtomic.integer (1)));
    // non-node before a further step
    assertEvalError ("XPTY0019", pathFrom (seq (lit (1), lit (2)), child ("a")), ctx (aDoc));
    // mixed last step
    assertEvalError ("XPTY0018",
                     path (child ("r"), postfix (seq (dot (), lit (1)))),
                     ctx (aDoc));
    // atomic last step keeps order and duplicates
    assertEquals (longs (1, 1),
                  ints (eval (path (child ("r"), step (EAxis.CHILD, anyName (), lit (1)), postfix (seq (lit (1), lit (1)))),
                              ctx (aDoc))));
    // '/' in a tree without document node
    final XdmNode aElement = XdmNode.createElement (new XdmQName ("e"));
    assertEvalError ("XPDY0050", absPath (), ctx (aElement));
    assertEvalError ("XPDY0050", absPath (child ("e")), ctx (aElement));
  }

  @Test
  public void testPostfixStepSortsNodes ()
  {
    final XdmNode aDoc = _doc ();
    final XdmNode aR = aDoc.getChildAtIndex (0);
    // r/(d, a) delivers a a d in document order
    assertEquals ("a a d", names (eval (path (child ("r"), postfix (seq (path (child ("d")), path (child ("a"))))), ctx (aDoc))));
    // a filter expression first step keeps its own order
    assertEquals ("d a", names (eval (filter (seq (path (child ("d")), path (child ("a", lit (1))))), ctx (aR))));
  }

  @Test
  public void testDocumentAndElementTests ()
  {
    final XdmNode aDoc = _doc ();
    final NodeTest aElemR = new NodeTest (ENodeTestKind.ELEMENT, false, false, false, qn ("r"), null, false, null);
    final NodeTest aDocR = new NodeTest (ENodeTestKind.DOCUMENT, false, false, false, null, null, false, aElemR);
    assertTrue (SequenceTypeMatcher.matchesItem (aDoc, ItemType.node (aDocR)));
    final NodeTest aElemX = new NodeTest (ENodeTestKind.ELEMENT, false, false, false, qn ("x"), null, false, null);
    assertFalse (SequenceTypeMatcher.matchesItem (aDoc,
                                                  ItemType.node (new NodeTest (ENodeTestKind.DOCUMENT,
                                                                               false,
                                                                               false,
                                                                               false,
                                                                               null,
                                                                               null,
                                                                               false,
                                                                               aElemX))));
    final XdmQName aUntyped = new XdmQName (XdmConstants.NS_XS, "untyped");
    final XdmQName aString = new XdmQName (XdmConstants.NS_XS, "string");
    final XdmQName aAnyType = new XdmQName (XdmConstants.NS_XS, "anyType");
    final XdmNode aR = aDoc.getChildAtIndex (0);
    assertTrue (NodeTestMatcher.matchesKindTest (new NodeTest (ENodeTestKind.ELEMENT, true, false, false, null, aUntyped, false, null),
                                                 aR));
    assertTrue (NodeTestMatcher.matchesKindTest (new NodeTest (ENodeTestKind.ELEMENT, true, false, false, null, aAnyType, false, null),
                                                 aR));
    assertFalse (NodeTestMatcher.matchesKindTest (new NodeTest (ENodeTestKind.ELEMENT, true, false, false, null, aString, false, null),
                                                  aR));
    final XdmNode aId = aR.getChildAtIndex (0).getAttributeAtIndex (0);
    assertTrue (NodeTestMatcher.matchesKindTest (new NodeTest (ENodeTestKind.ATTRIBUTE,
                                                               false,
                                                               false,
                                                               false,
                                                               qn ("id"),
                                                               new XdmQName (XdmConstants.NS_XS, "untypedAtomic"),
                                                               false,
                                                               null),
                                                 aId));
    assertFalse (NodeTestMatcher.matchesKindTest (new NodeTest (ENodeTestKind.ATTRIBUTE, true, false, false, null, aUntyped, false, null),
                                                  aId));
    assertFalse (NodeTestMatcher.matchesKindTest (new NodeTest (ENodeTestKind.SCHEMA_ELEMENT, false, false, false, qn ("r"), null, false, null),
                                                  aR));
  }

  @Test
  public void testInstanceOfAndTreat ()
  {
    final SeqType aIntPlus = atomicType (EAtomicType.INTEGER, EOccurrence.ONE_OR_MORE);
    assertTrue (boolValue (eval (new IExpr.InstanceOfExpr (seq (lit (1), lit (2)), aIntPlus))));
    assertFalse (boolValue (eval (new IExpr.InstanceOfExpr (seq (), aIntPlus))));
    assertTrue (boolValue (eval (new IExpr.InstanceOfExpr (seq (), SeqType.EMPTY_SEQUENCE))));
    assertTrue (boolValue (eval (new IExpr.InstanceOfExpr (lit (1), atomicType (EAtomicType.DECIMAL, EOccurrence.ONE)))));
    assertFalse (boolValue (eval (new IExpr.InstanceOfExpr (lit (1), atomicType (EAtomicType.STRING, EOccurrence.ONE)))));
    assertEquals (longs (3), ints (eval (new IExpr.TreatExpr (lit (3), atomicType (EAtomicType.NUMERIC, EOccurrence.ONE)))));
    assertEvalError ("XPDY0050", new IExpr.TreatExpr (str ("a"), atomicType (EAtomicType.INTEGER, EOccurrence.ONE)));
  }

  @Test
  public void testIfAndLogic ()
  {
    assumeOps ();
    assertEquals (longs (1), ints (eval (new IExpr.IfExpr (lit (5), lit (1), lit (2)))));
    assertEquals (longs (2), ints (eval (new IExpr.IfExpr (seq (), lit (1), lit (2)))));
    assertTrue (boolValue (eval (bin (EBinaryOp.OR, lit (0), str ("x")))));
    assertFalse (boolValue (eval (bin (EBinaryOp.AND, lit (1), str ("")))));
    // short circuit: the right operand would raise an error
    assertFalse (boolValue (eval (bin (EBinaryOp.AND, lit (0), dot ()))));
    assertEvalError ("FORG0006", new IExpr.IfExpr (seq (lit (1), lit (2)), lit (1), lit (2)));
  }

  @Test
  public void testQuantified ()
  {
    assumeOps ();
    final IExpr aSome = new IExpr.QuantifiedExpr (false,
                                                  List.of (new IExpr.Binding (qn ("x"), null, range (lit (1), lit (5)))),
                                                  cmp (EComparisonOp.VALUE_GT, var ("x"), lit (4)));
    assertTrue (boolValue (eval (aSome)));
    final IExpr aEvery = new IExpr.QuantifiedExpr (true,
                                                   List.of (new IExpr.Binding (qn ("x"), null, range (lit (1), lit (5)))),
                                                   cmp (EComparisonOp.VALUE_GT, var ("x"), lit (4)));
    assertFalse (boolValue (eval (aEvery)));
    assertTrue (boolValue (eval (new IExpr.QuantifiedExpr (true,
                                                           List.of (new IExpr.Binding (qn ("x"), null, seq ())),
                                                           lit (0)))));
  }

  @Test
  public void testPredicates ()
  {
    assumeOps ();
    final XdmNode aDoc = _doc ();
    final DynamicContext aCtx = ctx (aDoc);
    // //a[c]
    final XdmSequence aWithC = eval (absPath (descOrSelf (), child ("a", path (child ("c")))), aCtx);
    assertEquals (1, aWithC.size ());
    // (1 to 10)[. mod 3 = 0]
    assertEquals (longs (3, 6, 9),
                  ints (eval (filter (range (lit (1), lit (10)),
                                      cmp (EComparisonOp.VALUE_EQ, bin (EBinaryOp.MODULO, dot (), lit (3)), lit (0))))));
    // (5 to 10)[position() = 2] via a computed number
    assertEquals (longs (6), ints (eval (filter (range (lit (5), lit (10)), bin (EBinaryOp.ADD, lit (1), lit (1))))));
    // last()
    assertEquals (longs (10), ints (eval (filter (range (lit (5), lit (10)), call (fn ("last"))))));
  }

  @Test
  public void testArithmetic ()
  {
    assumeOps ();
    assertEquals (longs (5), ints (eval (bin (EBinaryOp.ADD, lit (2), lit (3)))));
    assertTrue (eval (bin (EBinaryOp.ADD, seq (), lit (3))).isEmpty ());
    assertEvalError ("XPTY0004", bin (EBinaryOp.ADD, seq (lit (1), lit (2)), lit (3)));
    // untypedAtomic becomes xs:double
    final XdmNode aDoc = parse ("<a>4</a>");
    final XdmSequence aRes = eval (bin (EBinaryOp.MULTIPLY, path (child ("a")), lit (2)), ctx (aDoc));
    assertEquals (EAtomicType.DOUBLE, ((XdmAtomic) aRes.get (0)).getType ());
    assertEquals (8.0, ((XdmAtomic) aRes.get (0)).getAsDouble (), 0);
    assertEquals (longs (-3), ints (eval (new IExpr.UnaryExpr (true, lit (3)))));
    assertEquals (longs (3), ints (eval (new IExpr.UnaryExpr (false, lit (3)))));
    assertEvalError ("XPTY0004", new IExpr.UnaryExpr (false, str ("a")));
  }

  @Test
  public void testComparisons ()
  {
    assumeOps ();
    assertTrue (boolValue (eval (cmp (EComparisonOp.VALUE_LT, lit (1), lit (2)))));
    assertTrue (eval (cmp (EComparisonOp.VALUE_LT, seq (), lit (2))).isEmpty ());
    assertEvalError ("XPTY0004", cmp (EComparisonOp.VALUE_EQ, seq (lit (1), lit (2)), lit (2)));
    assertTrue (boolValue (eval (cmp (EComparisonOp.GENERAL_EQ, seq (lit (1), lit (2)), lit (2)))));
    assertFalse (boolValue (eval (cmp (EComparisonOp.GENERAL_EQ, seq (), seq ()))));
    // untypedAtomic in a value comparison is a string
    final XdmNode aDoc = parse ("<a>abc</a>");
    assertTrue (boolValue (eval (cmp (EComparisonOp.VALUE_EQ, path (child ("a")), str ("abc")), ctx (aDoc))));
  }

  @Test
  public void testNodeComparisons ()
  {
    final XdmNode aDoc = _doc ();
    final DynamicContext aCtx = ctx (aDoc.getChildAtIndex (0));
    final IExpr aD = path (child ("d"));
    final IExpr aA1 = path (child ("a", lit (1)));
    assertTrue (boolValue (eval (cmp (EComparisonOp.NODE_IS, aD, aD), aCtx)));
    assertFalse (boolValue (eval (cmp (EComparisonOp.NODE_IS, aD, aA1), aCtx)));
    assertTrue (boolValue (eval (cmp (EComparisonOp.NODE_PRECEDES, aA1, aD), aCtx)));
    assertTrue (boolValue (eval (cmp (EComparisonOp.NODE_FOLLOWS, aD, aA1), aCtx)));
    assertTrue (eval (cmp (EComparisonOp.NODE_IS, aD, path (child ("missing"))), aCtx).isEmpty ());
    assertEvalError ("XPTY0004", cmp (EComparisonOp.NODE_IS, aD, lit (1)), aCtx);
    assertEvalError ("XPTY0004", cmp (EComparisonOp.NODE_IS, path (child ("a")), aD), aCtx);
  }

  @Test
  public void testStringConcat ()
  {
    assumeOps ();
    assertEquals ("ab12", stringValue (eval (new IExpr.StringConcatExpr (List.of (str ("a"), str ("b"), seq (), lit (12))))));
    assertEvalError ("XPTY0004", new IExpr.StringConcatExpr (List.of (str ("a"), seq (lit (1), lit (2)))));
  }

  @Test
  public void testSimpleMap ()
  {
    assumeOps ();
    final IExpr aMap = new IExpr.SimpleMapExpr (List.of (range (lit (1), lit (3)), bin (EBinaryOp.MULTIPLY, dot (), lit (2))));
    assertEquals (longs (2, 4, 6), ints (eval (aMap)));
    final IExpr aPos = new IExpr.SimpleMapExpr (List.of (seq (str ("a"), str ("b")), call (fn ("position"))));
    assumeParser ();
    assertEquals (longs (1, 2), ints (eval (aPos)));
  }

  @Test
  public void testCast ()
  {
    assumeOps ();
    final SeqType aInt = atomicType (EAtomicType.INTEGER, EOccurrence.ONE);
    final SeqType aIntOpt = atomicType (EAtomicType.INTEGER, EOccurrence.ZERO_OR_ONE);
    assertEquals (longs (12), ints (eval (new IExpr.CastExpr (str ("12"), aInt, false))));
    assertTrue (eval (new IExpr.CastExpr (seq (), aIntOpt, false)).isEmpty ());
    assertEvalError ("XPTY0004", new IExpr.CastExpr (seq (), aInt, false));
    assertEvalError ("XPTY0004", new IExpr.CastExpr (seq (lit (1), lit (2)), aInt, false));
    assertEvalError ("FORG0001", new IExpr.CastExpr (str ("x"), aInt, false));
    assertTrue (boolValue (eval (new IExpr.CastExpr (str ("12"), aInt, true))));
    assertFalse (boolValue (eval (new IExpr.CastExpr (str ("x"), aInt, true))));
    assertFalse (boolValue (eval (new IExpr.CastExpr (seq (), aInt, true))));
    assertTrue (boolValue (eval (new IExpr.CastExpr (seq (), aIntOpt, true))));
    assertFalse (boolValue (eval (new IExpr.CastExpr (seq (lit (1), lit (2)), aInt, true))));
    // list type
    final SeqType aNMTokens = new SeqType (false, ItemType.atomic (EAtomicType.NMTOKEN), EOccurrence.ONE, true);
    assertEquals (3, eval (new IExpr.CastExpr (str (" a b  c "), aNMTokens, false)).size ());
    assertFalse (boolValue (eval (new IExpr.CastExpr (str ("  "), aNMTokens, true))));
    assertEvalError ("XPST0080",
                     new IExpr.CastExpr (str ("1"), atomicType (EAtomicType.ANY_ATOMIC_TYPE, EOccurrence.ONE), false));
  }

  @Test
  public void testMapAndArrayConstructors ()
  {
    assumeOps ();
    final XdmMap aMap = (XdmMap) eval (new IExpr.MapConstructorExpr (List.of (str ("a"), lit (1)),
                                                                      List.of (lit (10), seq (lit (20), lit (21))))).get (0);
    assertEquals (2, aMap.size ());
    assertEquals (longs (20, 21), ints (aMap.get (XdmAtomic.integer (1))));
    assertEvalError ("XQDY0137", new IExpr.MapConstructorExpr (List.of (lit (1), dbl (1.0)), List.of (lit (1), lit (2))));
    assertEvalError ("XPTY0004", new IExpr.MapConstructorExpr (List.of (seq ()), List.of (lit (1))));

    final XdmArray aSquare = (XdmArray) eval (new IExpr.ArrayConstructorExpr (List.of (lit (1), seq (lit (2), lit (3)), seq ()),
                                                                              false)).get (0);
    assertEquals (3, aSquare.size ());
    assertEquals (2, aSquare.get (1).size ());
    final XdmArray aCurly = (XdmArray) eval (new IExpr.ArrayConstructorExpr (List.of (seq (lit (1), seq (lit (2), lit (3)))),
                                                                             true)).get (0);
    assertEquals (3, aCurly.size ());
    assertEquals (0, ((XdmArray) eval (new IExpr.ArrayConstructorExpr (List.of (), true)).get (0)).size ());
  }

  @Test
  public void testLookup ()
  {
    assumeOps ();
    final IExpr aMap = new IExpr.MapConstructorExpr (List.of (str ("a"), str ("b")), List.of (lit (1), lit (2)));
    final IExpr aArray = new IExpr.ArrayConstructorExpr (List.of (lit (10), lit (20), lit (30)), false);
    assertEquals (longs (1), ints (eval (new IExpr.LookupExpr (aMap, str ("a")))));
    assertTrue (eval (new IExpr.LookupExpr (aMap, str ("x"))).isEmpty ());
    assertEquals (longs (1, 2), ints (eval (new IExpr.LookupExpr (aMap, null))));
    assertEquals (longs (20), ints (eval (new IExpr.LookupExpr (aArray, lit (2)))));
    assertEquals (longs (30, 10), ints (eval (new IExpr.LookupExpr (aArray, seq (lit (3), lit (1))))));
    assertEquals (longs (10, 20, 30), ints (eval (new IExpr.LookupExpr (aArray, null))));
    assertEvalError ("FOAY0001", new IExpr.LookupExpr (aArray, lit (4)));
    assertEvalError ("XPTY0004", new IExpr.LookupExpr (aArray, dbl (1)));
    assertEvalError ("XPTY0004", new IExpr.LookupExpr (lit (1), lit (1)));
    // unary lookup on the context item
    final XdmArray aArr = (XdmArray) eval (aArray).get (0);
    assertEquals (longs (30), ints (eval (new IExpr.LookupExpr (null, lit (3)), ctx (aArr))));
    assertEvalError ("XPDY0002", new IExpr.LookupExpr (null, lit (3)));
  }

  @Test
  public void testInlineFunctions ()
  {
    assumeOps ();
    // let $y := 10, $f := function($x) { $x + $y } return $f(5)
    final IExpr aF = inline (bin (EBinaryOp.ADD, var ("x"), var ("y")), "x");
    assertEquals (longs (15), ints (eval (let ("y", lit (10), let ("f", aF, dynCall (var ("f"), lit (5)))))));
    // the closure keeps $y even when called where $y is rebound
    final IExpr aRebound = let ("y",
                                lit (10),
                                let ("f", aF, let ("y", lit (100), dynCall (var ("f"), lit (5)))));
    assertEquals (longs (15), ints (eval (aRebound)));
    // the focus is absent in the body
    assertEvalError ("XPDY0002", dynCall (inline (dot ())), ctx (XdmAtomic.integer (1)));
    // arity mismatch
    assertEvalError ("XPTY0004", dynCall (aF, lit (1), lit (2)));
    // calling a non-function
    assertEvalError ("XPTY0004", dynCall (lit (1), lit (1)));
    assertEvalError ("XPTY0004", dynCall (seq (aF, aF), lit (1)));
    // declared parameter type converts the argument; declared return type is checked
    final IExpr aTyped = inlineTyped (var ("x"),
                                      atomicType (EAtomicType.DOUBLE, EOccurrence.ONE),
                                      new IExpr.Param (qn ("x"), atomicType (EAtomicType.DOUBLE, EOccurrence.ONE)));
    final XdmSequence aRes = eval (dynCall (aTyped, lit (3)));
    assertEquals (EAtomicType.DOUBLE, ((XdmAtomic) aRes.get (0)).getType ());
    assertEvalError ("XPTY0004", dynCall (aTyped, str ("x")));
    final IExpr aBadReturn = inlineTyped (seq (), atomicType (EAtomicType.INTEGER, EOccurrence.ONE));
    assertEvalError ("XPTY0004", dynCall (aBadReturn));
    final IXdmFunction aFunc = (IXdmFunction) eval (aF).get (0);
    assertEquals (1, aFunc.getArity ());
    assertEquals (null, aFunc.getName ());
  }

  @Test
  public void testPartialApplicationOfInline ()
  {
    assumeOps ();
    final IExpr aSub = inline (bin (EBinaryOp.SUBTRACT, var ("a"), var ("b")), "a", "b");
    // $sub(?, 1)(10) = 9 and $sub(10, ?)(1) = 9
    assertEquals (longs (9), ints (eval (dynCall (dynCall (aSub, hole (), lit (1)), lit (10)))));
    assertEquals (longs (9), ints (eval (dynCall (dynCall (aSub, lit (10), hole ()), lit (1)))));
    final IXdmFunction aPartial = (IXdmFunction) eval (dynCall (aSub, hole (), hole ())).get (0);
    assertEquals (2, aPartial.getArity ());
    assertEvalError ("XPTY0004", dynCall (aSub, hole ()));
  }

  @Test
  public void testMapsAndArraysAsFunctions ()
  {
    assumeOps ();
    final IExpr aMap = new IExpr.MapConstructorExpr (List.of (str ("a")), List.of (lit (1)));
    final IExpr aArray = new IExpr.ArrayConstructorExpr (List.of (lit (10), lit (20)), false);
    assertEquals (longs (1), ints (eval (dynCall (aMap, str ("a")))));
    assertTrue (eval (dynCall (aMap, str ("b"))).isEmpty ());
    assertEquals (longs (20), ints (eval (dynCall (aArray, lit (2)))));
    assertEvalError ("FOAY0001", dynCall (aArray, lit (3)));
    assertEvalError ("XPTY0004", dynCall (aArray, str ("x")));
    assertEvalError ("XPTY0004", dynCall (aMap, seq ()));
    assertEvalError ("XPTY0004", dynCall (aMap, str ("a"), str ("b")));
    // partial application of an array
    assertEquals (longs (10), ints (eval (dynCall (dynCall (aArray, hole ()), lit (1)))));
  }

  @Test
  public void testRecursionDepthIsLimited ()
  {
    assumeOps ();
    // let $f := function($f) { $f($f) } return $f($f)
    final IExpr aSelf = inline (dynCall (var ("f"), var ("f")), "f");
    assertEvalError ("XPDY0130", let ("f", aSelf, dynCall (var ("f"), var ("f"))));
  }

  @Test
  public void testStaticFunctionCalls ()
  {
    assumeOps ();
    assumeParser ();
    assertTrue (boolValue (eval (call (fn ("not"), seq ()))));
    assertTrue (boolValue (eval (call (fn ("true")))));
    assertEvalError ("XPST0017", call (fn ("no-such-function")));
    assertEvalError ("XPST0017", call (fn ("true"), lit (1)));
    // position() and last() in a predicate
    final XdmSequence aLast = eval (filter (range (lit (1), lit (4)),
                                            cmp (EComparisonOp.VALUE_EQ, call (fn ("position")), call (fn ("last")))));
    assertEquals (longs (4), ints (aLast));
  }

  @Test
  public void testNamedFunctionRefAndPartialBuiltIn ()
  {
    assumeOps ();
    assumeParser ();
    final IExpr aNot = new IExpr.NamedFunctionRefExpr (fn ("not"), 1);
    final IXdmFunction aFunc = (IXdmFunction) eval (aNot).get (0);
    assertEquals (fn ("not"), aFunc.getName ());
    assertEquals (1, aFunc.getArity ());
    assertTrue (boolValue (eval (dynCall (aNot, seq ()))));
    assertEvalError ("XPST0017", new IExpr.NamedFunctionRefExpr (fn ("not"), 2));
    // position#0 captures the focus of the reference
    final IExpr aPosRef = new IExpr.NamedFunctionRefExpr (fn ("position"), 0);
    final XdmSequence aPos = eval (filter (range (lit (1), lit (3)), lit (2)), ctx ());
    assertEquals (longs (2), ints (aPos));
    final IExpr aCapture = let ("f",
                                filter (new IExpr.SimpleMapExpr (List.of (range (lit (1), lit (3)), aPosRef)), lit (2)),
                                dynCall (var ("f")));
    assertEquals (longs (2), ints (eval (aCapture)));
    // partial application of a built-in
    final IExpr aPartial = call (fn ("for-each-pair"), range (lit (1), lit (2)), hole (), inline (var ("a"), "a", "b"));
    assertEquals (longs (1, 2), ints (eval (dynCall (aPartial, seq (str ("x"), str ("y"))))));
  }

  @Test
  public void testRangeComparisonWithoutMaterialising ()
  {
    assumeParser ();
    // RangeExpr-409c/d: far beyond the materialisation limit
    assertEquals ("true", _adaptive ("1000000000000000020001 = 1000000000000000000000 to 1000000000000010000003", false, null));
    assertEquals ("true", _adaptive ("1000000000000000020001 < 1000000000000000000000 to 1000000000000500000003", false, null));
    assertEquals ("false", _adaptive ("0 = 1 to 100000000000", false, null));
    assertEquals ("true", _adaptive ("1 to 100000000000 > 99999999999.5", false, null));
    assertEquals ("false", _adaptive ("1 to 100000000000 > 100000000000", false, null));
    assertEquals ("false", _adaptive ("2.5 = 1 to 100000000000", false, null));
    assertEquals ("true", _adaptive ("2.0 = 1 to 100000000000", false, null));
    assertEquals ("true", _adaptive ("(5, 6.5) != 5 to 5", false, null));
    assertEquals ("false", _adaptive ("5 != 5 to 5", false, null));
    assertEquals ("false", _adaptive ("xs:double('NaN') = 1 to 100000000000", false, null));
    assertEquals ("true", _adaptive ("xs:double('NaN') != 1 to 100000000000", false, null));
    assertEquals ("true", _adaptive ("xs:float('3') = 1 to 100000000000", false, null));
    assertEquals ("false", _adaptive ("() = 1 to 100000000000", false, null));
    assertEquals ("false", _adaptive ("1 = 5 to 1", false, null));
    // a non-numeric other operand takes the ordinary path
    assertEquals ("true", _adaptive ("'a' != (1 to 3) ! string(.)", false, null));
    _assertCode ("XPTY0004", "'a' = 1 to 3", false, null);
    _assertCode ("FORG0001", "('x' cast as xs:untypedAtomic) = 1 to 3", false, null);
  }

  @Test
  public void testXPath10Compatibility ()
  {
    assumeParser ();
    final XdmNode aDoc = _doc ();
    // generate-id-018: the first item of a sequence for a single-item parameter
    assertEquals ("true", _adaptive ("generate-id(//a) eq generate-id((//a)[1])", true, aDoc));
    _assertCode ("XPTY0004", "generate-id(//a)", false, aDoc);
    // xs:string? applies fn:string, xs:double applies fn:number
    assertEquals ("1", _adaptive ("string-length(1 + 0)", true, null));
    assertEquals ("x", _adaptive ("substring(//b, 1)", true, aDoc));
    assertEquals ("2", _adaptive ("math:sqrt('4')", true, null));
    assertEquals ("NaN", _adaptive ("math:sqrt(('x', 4))", true, null));
    assertEquals ("NaN", _adaptive ("math:sqrt(//b)", true, aDoc));
    _assertCode ("XPTY0004", "math:sqrt('4')", false, null);
    // arithmetic: first item, empty is NaN, fn:number conversion
    assertEquals ("2", _adaptive ("(1, 2) + 1", true, null));
    assertEquals ("NaN", _adaptive ("() + 1", true, null));
    assertEquals ("2", _adaptive ("true() + 1", true, null));
    assertEquals ("NaN", _adaptive ("'x' + 1", true, null));
    assertEquals ("1.5", _adaptive ("'1.5' * 1", true, null));
    assertEquals ("-2", _adaptive ("-(2, 3)", true, null));
    assertEquals ("NaN", _adaptive ("-()", true, null));
    _assertCode ("XPTY0004", "(1, 2) + 1", false, null);
  }
}
