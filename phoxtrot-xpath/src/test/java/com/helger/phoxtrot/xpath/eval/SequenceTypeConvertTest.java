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
package com.helger.phoxtrot.xpath.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import static com.helger.phoxtrot.xpath.eval.ExprTestHelper.atomicType;
import static com.helger.phoxtrot.xpath.eval.ExprTestHelper.parse;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmArray;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmMap;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The function conversion rules of {@link SequenceTypeMatcher#convert(XdmSequence, SeqType, DynamicContext, String)}
 * (XPath 3.1 section 3.1.5.2) for one item, no item and several items: results, unchanged
 * instances, error codes and messages. The expected outcomes were taken from the implementation
 * before the fast path for single items, so they also pin that the fast path changes nothing.
 *
 * @author Philip Helger
 */
public final class SequenceTypeConvertTest
{
  private static final String CODE = "XTTE0780";

  @NonNull
  private static SeqType _t (@NonNull final EAtomicType e, @NonNull final EOccurrence eOcc)
  {
    return atomicType (e, eOcc);
  }

  @NonNull
  private static SeqType _t1 (@NonNull final EAtomicType e)
  {
    return atomicType (e, EOccurrence.ONE);
  }

  @NonNull
  private static String _describe (@NonNull final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      if (aItem instanceof final XdmAtomic aAtomic)
        aSB.append (aAtomic.toString ());
      else
        if (aItem instanceof final IXdmNode aNode)
          aSB.append (aNode.getKind ().name ());
        else
          aSB.append ("other");
    }
    return aSB.toString ();
  }

  /**
   * @return The converted value as text with a <code>same</code> marker if the supplied instance
   *         came back, or the error code and message.
   */
  @NonNull
  private static String _convert (@NonNull final XdmSequence aSeq,
                                  @NonNull final SeqType aType,
                                  @NonNull final DynamicContext aCtx)
  {
    try
    {
      final XdmSequence ret = SequenceTypeMatcher.convert (aSeq, aType, aCtx, CODE);
      return (ret == aSeq ? "same " : "") + "[" + _describe (ret) + "]";
    }
    catch (final XPathException ex)
    {
      return ex.getCode () + ": " + ex.getDescription ();
    }
  }

  @NonNull
  private static XdmSequence _s (@NonNull final IXdmItem... a)
  {
    return XdmSequence.of (a);
  }

  @Test
  public void testSingleAtomic ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    final XdmSequence aInt = _s (XdmAtomic.integer (5));
    assertEquals ("same [xs:integer(5)]", _convert (aInt, _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("same [xs:integer(5)]", _convert (aInt, _t (EAtomicType.INTEGER, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("same [xs:integer(5)]", _convert (aInt, _t1 (EAtomicType.DECIMAL), aCtx));
    assertEquals ("same [xs:integer(5)]", _convert (aInt, _t1 (EAtomicType.ANY_ATOMIC_TYPE), aCtx));
    assertEquals ("same [xs:integer(5)]", _convert (aInt, _t1 (EAtomicType.NUMERIC), aCtx));
    assertEquals ("[xs:double(5)]", _convert (aInt, _t1 (EAtomicType.DOUBLE), aCtx));
    assertEquals ("[xs:float(5)]", _convert (aInt, _t1 (EAtomicType.FLOAT), aCtx));
    final XdmSequence aByte = _s (XdmAtomic.of (EAtomicType.BYTE, BigInteger.ONE));
    assertEquals ("same [xs:byte(1)]", _convert (aByte, _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:double(1)]", _convert (aByte, _t1 (EAtomicType.DOUBLE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:float, but supplied was a value of type xs:double",
                  _convert (_s (XdmAtomic.dbl (1)), _t1 (EAtomicType.FLOAT), aCtx));
    assertEquals ("XTTE0780: Required type is xs:integer, but supplied was a value of type xs:string",
                  _convert (_s (XdmAtomic.string ("1")), _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:string(urn:x)]", _convert (_s (XdmAtomic.anyUri ("urn:x")), _t1 (EAtomicType.STRING), aCtx));
    assertEquals ("same [xs:anyURI(urn:x)]",
                  _convert (_s (XdmAtomic.anyUri ("urn:x")), _t1 (EAtomicType.ANY_URI), aCtx));
    // xs:untypedAtomic
    final XdmSequence aUntyped = _s (XdmAtomic.untyped ("12"));
    assertEquals ("[xs:integer(12)]", _convert (aUntyped, _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:double(12)]", _convert (aUntyped, _t1 (EAtomicType.NUMERIC), aCtx));
    assertEquals ("same [xs:untypedAtomic(12)]", _convert (aUntyped, _t1 (EAtomicType.ANY_ATOMIC_TYPE), aCtx));
    assertEquals ("same [xs:untypedAtomic(12)]", _convert (aUntyped, _t1 (EAtomicType.UNTYPED_ATOMIC), aCtx));
    assertEquals ("[xs:string(12)]", _convert (aUntyped, _t (EAtomicType.STRING, EOccurrence.ZERO_OR_ONE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:error, but supplied was a value of type xs:untypedAtomic",
                  _convert (aUntyped, _t1 (EAtomicType.ERROR), aCtx));
    assertEquals ("FORG0001", _convert (_s (XdmAtomic.untyped ("x")), _t1 (EAtomicType.INTEGER), aCtx).substring (0, 8));
    assertEquals ("XPTY0117", _convert (aUntyped, _t1 (EAtomicType.QNAME), aCtx).substring (0, 8));
    // The cardinality of one item
    assertEquals ("same [xs:integer(5)]", _convert (aInt, _t (EAtomicType.INTEGER, EOccurrence.ONE_OR_MORE), aCtx));
  }

  @Test
  public void testSingleNonAtomic ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    final IXdmNode aDoc = parse ("<a x='3'>7</a>");
    final IXdmNode aElement = aDoc.getChildAtIndex (0);
    final XdmSequence aEl = _s (aElement);
    assertEquals ("[xs:integer(7)]", _convert (aEl, _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:string(7)]", _convert (aEl, _t (EAtomicType.STRING, EOccurrence.ZERO_OR_ONE), aCtx));
    assertEquals ("[xs:untypedAtomic(7)]", _convert (aEl, _t1 (EAtomicType.ANY_ATOMIC_TYPE), aCtx));
    assertEquals ("[xs:double(7)]", _convert (aEl, _t1 (EAtomicType.DOUBLE), aCtx));
    assertEquals ("[xs:integer(3)]", _convert (_s (aElement.getAttributeAtIndex (0)), _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:string(7)]", _convert (_s (aElement.getChildAtIndex (0)), _t1 (EAtomicType.STRING), aCtx));
    assertEquals ("[xs:integer(7)]", _convert (_s (aDoc), _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("FORG0001",
                  _convert (_s (parse ("<a>x</a>").getChildAtIndex (0)), _t1 (EAtomicType.INTEGER), aCtx).substring (0, 8));
    assertEquals ("XPTY0117", _convert (aEl, _t1 (EAtomicType.QNAME), aCtx).substring (0, 8));
    // Maps and functions cannot be atomised
    final XdmSequence aMap = _s (XdmMap.of (XdmAtomic.integer (1), _s (XdmAtomic.integer (2))));
    assertEquals ("FOTY0013", _convert (aMap, _t1 (EAtomicType.STRING), aCtx).substring (0, 8));
    // Arrays are atomised to their members
    final XdmSequence aArray2 = _s (XdmArray.of (_s (XdmAtomic.integer (1)), _s (XdmAtomic.untyped ("2"))));
    assertEquals ("XTTE0780: Required type is xs:integer, but supplied was a sequence of 2 items starting with a value of type xs:integer",
                  _convert (aArray2, _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:integer(1) xs:integer(2)]",
                  _convert (aArray2, _t (EAtomicType.INTEGER, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("[xs:double(1) xs:double(2)]", _convert (aArray2, _t (EAtomicType.DOUBLE, EOccurrence.ONE_OR_MORE), aCtx));
    final XdmSequence aArray1 = _s (XdmArray.of (_s (XdmAtomic.untyped ("3"))));
    assertEquals ("[xs:double(3)]", _convert (aArray1, _t1 (EAtomicType.DOUBLE), aCtx));
    final XdmSequence aArray0 = _s (XdmArray.of ());
    assertEquals ("[]", _convert (aArray0, _t (EAtomicType.INTEGER, EOccurrence.ZERO_OR_ONE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:integer, but supplied was an empty sequence",
                  _convert (aArray0, _t1 (EAtomicType.INTEGER), aCtx));
    final XdmSequence aArrayStr = _s (XdmArray.of (_s (XdmAtomic.string ("1"))));
    assertEquals ("XTTE0780: Required type is xs:integer, but supplied was a value of type xs:string",
                  _convert (aArrayStr, _t1 (EAtomicType.INTEGER), aCtx));
  }

  @Test
  public void testOtherCounts ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    assertEquals ("same []", _convert (XdmSequence.EMPTY, _t (EAtomicType.STRING, EOccurrence.ZERO_OR_ONE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:string, but supplied was an empty sequence",
                  _convert (XdmSequence.EMPTY, _t1 (EAtomicType.STRING), aCtx));
    final XdmSequence aTwo = _s (XdmAtomic.integer (1), XdmAtomic.untyped ("2"));
    assertEquals ("XTTE0780: Required type is xs:integer?, but supplied was a sequence of 2 items starting with a value of type xs:integer",
                  _convert (aTwo, _t (EAtomicType.INTEGER, EOccurrence.ZERO_OR_ONE), aCtx));
    assertEquals ("[xs:integer(1) xs:integer(2)]", _convert (aTwo, _t (EAtomicType.INTEGER, EOccurrence.ONE_OR_MORE), aCtx));
    final XdmSequence aInts = _s (XdmAtomic.integer (1), XdmAtomic.integer (2));
    assertEquals ("same [xs:integer(1) xs:integer(2)]",
                  _convert (aInts, _t (EAtomicType.DECIMAL, EOccurrence.ZERO_OR_MORE), aCtx));
  }

  /**
   * Several items that already match need no conversion; anything else takes the conversion
   * loop.
   */
  @Test
  public void testSeveralAlreadyMatching ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    final XdmSequence aMixed = _s (XdmAtomic.integer (1),
                                   XdmAtomic.of (EAtomicType.BYTE, BigInteger.TWO),
                                   XdmAtomic.decimal (BigDecimal.ONE));
    assertEquals ("same [xs:integer(1) xs:byte(2) xs:decimal(1)]",
                  _convert (aMixed, _t (EAtomicType.DECIMAL, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("same [xs:integer(1) xs:byte(2) xs:decimal(1)]",
                  _convert (aMixed, _t (EAtomicType.ANY_ATOMIC_TYPE, EOccurrence.ONE_OR_MORE), aCtx));
    assertEquals ("[xs:double(1) xs:double(2) xs:double(1)]",
                  _convert (aMixed, _t (EAtomicType.DOUBLE, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:integer*, but supplied was a sequence of 3 items starting with a value of type xs:integer",
                  _convert (aMixed, _t (EAtomicType.INTEGER, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:decimal?, but supplied was a sequence of 3 items starting with a value of type xs:integer",
                  _convert (aMixed, _t (EAtomicType.DECIMAL, EOccurrence.ZERO_OR_ONE), aCtx));
    final XdmSequence aUntyped = _s (XdmAtomic.untyped ("1"), XdmAtomic.untyped ("2"));
    assertEquals ("same [xs:untypedAtomic(1) xs:untypedAtomic(2)]",
                  _convert (aUntyped, _t (EAtomicType.ANY_ATOMIC_TYPE, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("same [xs:untypedAtomic(1) xs:untypedAtomic(2)]",
                  _convert (aUntyped, _t (EAtomicType.UNTYPED_ATOMIC, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("[xs:string(1) xs:string(2)]",
                  _convert (aUntyped, _t (EAtomicType.STRING, EOccurrence.ZERO_OR_MORE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:error*, but supplied was a sequence of 2 items starting with a value of type xs:untypedAtomic",
                  _convert (aUntyped, _t (EAtomicType.ERROR, EOccurrence.ZERO_OR_MORE), aCtx));
    // A node among atomic values is atomised
    final IXdmNode aElement = parse ("<a>3</a>").getChildAtIndex (0);
    assertEquals ("[xs:integer(1) xs:integer(3)]",
                  _convert (_s (XdmAtomic.integer (1), aElement), _t (EAtomicType.INTEGER, EOccurrence.ZERO_OR_MORE), aCtx));
  }

  /**
   * The typed value of single nodes of an untyped tree, per node kind.
   */
  @Test
  public void testSingleUntypedNodes ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    final IXdmNode aDoc = parse ("<a x='1'><!--c--><?p d?>t</a>");
    final IXdmNode aElement = aDoc.getChildAtIndex (0);
    assertEquals ("[xs:untypedAtomic(t)]", _convert (_s (aElement), _t1 (EAtomicType.ANY_ATOMIC_TYPE), aCtx));
    assertEquals ("[xs:untypedAtomic(1)]",
                  _convert (_s (aElement.getAttributeAtIndex (0)), _t1 (EAtomicType.UNTYPED_ATOMIC), aCtx));
    assertEquals ("[xs:string(c)]", _convert (_s (aElement.getChildAtIndex (0)), _t1 (EAtomicType.ANY_ATOMIC_TYPE), aCtx));
    assertEquals ("[xs:string(d)]", _convert (_s (aElement.getChildAtIndex (1)), _t1 (EAtomicType.ANY_ATOMIC_TYPE), aCtx));
    assertEquals ("XTTE0780: Required type is xs:integer, but supplied was a value of type xs:string",
                  _convert (_s (aElement.getChildAtIndex (0)), _t1 (EAtomicType.INTEGER), aCtx));
    assertEquals ("[xs:double(1)]", _convert (_s (aElement.getAttributeAtIndex (0)), _t1 (EAtomicType.NUMERIC), aCtx));
  }

  @Test
  public void testNodeTypes ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    final IXdmNode aDoc = parse ("<a><b/>t</a>");
    final IXdmNode aElement = aDoc.getChildAtIndex (0);
    final XdmSequence aNodes = _s (aElement.getChildAtIndex (0), aElement.getChildAtIndex (1));
    final SeqType aNodeStar = SeqType.of (ItemType.node (NodeTest.ofKind (ENodeTestKind.ANY_NODE)), EOccurrence.ZERO_OR_MORE);
    final SeqType aElementOne = SeqType.of (ItemType.node (NodeTest.ofKind (ENodeTestKind.ELEMENT)), EOccurrence.ONE);
    assertSame (aNodes, SequenceTypeMatcher.convert (aNodes, aNodeStar, aCtx, CODE));
    assertEquals ("same [ELEMENT]", _convert (_s (aElement), aElementOne, aCtx));
    assertEquals ("XTTE0780: Required type is element(), but supplied was a text node",
                  _convert (_s (aElement.getChildAtIndex (1)), aElementOne, aCtx));
    assertEquals ("XTTE0780: Required type is element(), but supplied was a value of type xs:integer",
                  _convert (_s (XdmAtomic.integer (1)), aElementOne, aCtx));
  }

  @Test
  public void testXPath10Compatibility ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ().setXPath10Compatibility (true));
    assertEquals ("[xs:double(NaN)]", _convert (_s (XdmAtomic.string ("abc")), _t1 (EAtomicType.DOUBLE), aCtx));
    assertEquals ("[xs:string(1)]",
                  _convert (_s (XdmAtomic.integer (1), XdmAtomic.integer (2)), _t1 (EAtomicType.STRING), aCtx));
    assertEquals ("same [xs:double(1)]", _convert (_s (XdmAtomic.dbl (1)), _t1 (EAtomicType.DOUBLE), aCtx));
    // A type that allows several items keeps all of them (the xs:string* of
    // fn:id, xpath-compat-0501)
    assertEquals ("[xs:string(a) xs:string(b)]",
                  _convert (_s (XdmAtomic.untyped ("a"), XdmAtomic.untyped ("b")),
                            _t (EAtomicType.STRING, EOccurrence.ZERO_OR_MORE),
                            aCtx));
    assertEquals ("[xs:double(NaN)]",
                  _convert (_s (XdmAtomic.untyped ("x"), XdmAtomic.untyped ("2")),
                            _t (EAtomicType.DOUBLE, EOccurrence.ZERO_OR_ONE),
                            aCtx));
  }
}
