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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for {@link XPathOperators}.
 *
 * @author Philip Helger
 */
public final class XPathOperatorsTest
{
  @NonNull
  private static XdmSequence _seq (@NonNull final IXdmItem... aItems)
  {
    return XdmSequence.of (aItems);
  }

  @NonNull
  private static XdmSequence _int (final long n)
  {
    return XdmSequence.of (XdmAtomic.integer (n));
  }

  @NonNull
  private static XdmSequence _str (@NonNull final String s)
  {
    return XdmSequence.of (XdmAtomic.string (s));
  }

  @NonNull
  private static String _string (@NonNull final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (Atomizer.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @NonNull
  private static IXdmNode _parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException (ex);
    }
  }

  private static void _assertError (@NonNull final String sCode, @NonNull final Runnable aAction)
  {
    try
    {
      aAction.run ();
      fail ("Expected " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (sCode, ex.getCode ());
    }
  }

  @Test
  public void testGeneralCompare ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    final IXdmNode aDoc = _parse ("<r><a>1</a><a>2</a></r>");
    final XdmSequence aNodes = XPathEvaluator.evaluate (XPathParser.parse ("r/a", new StaticContext ()),
                                                        aCtx.withFocus (aDoc, 1, 1));
    // Atomisation, untypedAtomic against a number, existential semantics
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, aNodes, _int (2), aCtx));
    assertFalse (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, aNodes, _int (3), aCtx));
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_NE, aNodes, _int (1), aCtx));
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_LT, aNodes, _str ("2"), aCtx));
    assertFalse (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, XdmSequence.EMPTY, _int (1), aCtx));
    _assertError ("XPTY0004",
                  () -> XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, _int (1), _str ("1"), aCtx));

    // The equality index for a long sequence gives the same answers
    final List <IXdmItem> aMany = new ArrayList <> ();
    for (int i = 0; i < 200; ++i)
      aMany.add (XdmAtomic.integer (i));
    final XdmSequence aLong = XdmSequence.of (aMany);
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, aLong, _int (150), aCtx));
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ,
                                               XdmSequence.of (XdmAtomic.dbl (150)),
                                               aLong,
                                               aCtx));
    assertFalse (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, aLong, _int (250), aCtx));

    // The default collation of the static context
    final DynamicContext aCaseBlind = new DynamicContext (new StaticContext ().setDefaultCollation (CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI));
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, _str ("abc"), _str ("ABC"), aCaseBlind));
    assertFalse (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, _str ("abc"), _str ("ABC"), aCtx));

    // XPath 1.0 compatibility: a string compared with a number as a number
    final DynamicContext aCompat = new DynamicContext (new StaticContext ().setXPath10Compatibility (true));
    assertTrue (XPathOperators.generalCompare (EComparisonOp.GENERAL_EQ, _int (1), _str ("1"), aCompat));

    try
    {
      XPathOperators.generalCompare (EComparisonOp.VALUE_EQ, _int (1), _int (1), aCtx);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  @Test
  public void testValueAndNodeCompare ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    assertEquals ("true", _string (XPathOperators.valueCompare (EComparisonOp.VALUE_LT, _int (1), _int (2), aCtx)));
    assertEquals ("", _string (XPathOperators.valueCompare (EComparisonOp.VALUE_EQ, XdmSequence.EMPTY, _int (2), aCtx)));
    // untypedAtomic is compared as a string
    assertEquals ("true",
                  _string (XPathOperators.valueCompare (EComparisonOp.VALUE_EQ,
                                                        XdmSequence.of (XdmAtomic.untyped ("a")),
                                                        _str ("a"),
                                                        aCtx)));
    _assertError ("XPTY0004",
                  () -> XPathOperators.valueCompare (EComparisonOp.VALUE_EQ,
                                                     XdmSequence.of (XdmAtomic.untyped ("1")),
                                                     _int (1),
                                                     aCtx));
    _assertError ("XPTY0004",
                  () -> XPathOperators.valueCompare (EComparisonOp.VALUE_EQ,
                                                     _seq (XdmAtomic.integer (1), XdmAtomic.integer (2)),
                                                     _int (1),
                                                     aCtx));

    final IXdmNode aDoc = _parse ("<r><a/><b/></r>");
    final IXdmNode aA = aDoc.getChildAtIndex (0).getChildAtIndex (0);
    final IXdmNode aB = aDoc.getChildAtIndex (0).getChildAtIndex (1);
    assertEquals ("true", _string (XPathOperators.nodeCompare (EComparisonOp.NODE_PRECEDES, _seq (aA), _seq (aB))));
    assertEquals ("false", _string (XPathOperators.nodeCompare (EComparisonOp.NODE_IS, _seq (aA), _seq (aB))));
    assertEquals ("", _string (XPathOperators.nodeCompare (EComparisonOp.NODE_IS, XdmSequence.EMPTY, _seq (aB))));
    _assertError ("XPTY0004", () -> XPathOperators.nodeCompare (EComparisonOp.NODE_IS, _int (1), _seq (aB)));

    // compare dispatches by the kind of the operator
    assertEquals ("true", _string (XPathOperators.compare (EComparisonOp.GENERAL_GE, _int (2), _int (2), aCtx)));
    assertEquals ("false", _string (XPathOperators.compare (EComparisonOp.VALUE_NE, _int (2), _int (2), aCtx)));
    assertEquals ("true", _string (XPathOperators.compare (EComparisonOp.NODE_FOLLOWS, _seq (aB), _seq (aA), aCtx)));
  }

  @Test
  public void testArithmetic ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ());
    assertEquals ("5", _string (XPathOperators.arithmetic (EBinaryOp.ADD, _int (2), _int (3), aCtx)));
    assertEquals ("2.5", _string (XPathOperators.arithmetic (EBinaryOp.DIVIDE, _int (5), _int (2), aCtx)));
    assertEquals ("2", _string (XPathOperators.arithmetic (EBinaryOp.INTEGER_DIVIDE, _int (5), _int (2), aCtx)));
    assertEquals ("", _string (XPathOperators.arithmetic (EBinaryOp.ADD, XdmSequence.EMPTY, _int (3), aCtx)));
    // untypedAtomic is cast to xs:double
    final XdmSequence aSum = XPathOperators.arithmetic (EBinaryOp.ADD, XdmSequence.of (XdmAtomic.untyped ("1")), _int (1), aCtx);
    assertSame (EAtomicType.DOUBLE, ((XdmAtomic) aSum.get (0)).getType ());
    _assertError ("XPTY0004", () -> XPathOperators.arithmetic (EBinaryOp.ADD, _str ("1"), _int (1), aCtx));
    _assertError ("FOAR0001", () -> XPathOperators.arithmetic (EBinaryOp.INTEGER_DIVIDE, _int (1), _int (0), aCtx));
    _assertError ("XPTY0004",
                  () -> XPathOperators.arithmetic (EBinaryOp.ADD,
                                                   _seq (XdmAtomic.integer (1), XdmAtomic.integer (2)),
                                                   _int (1),
                                                   aCtx));

    // XPath 1.0 compatibility: the first item, strings and empty operands as numbers
    final DynamicContext aCompat = new DynamicContext (new StaticContext ().setXPath10Compatibility (true));
    assertEquals ("3", _string (XPathOperators.arithmetic (EBinaryOp.ADD, _str ("1"), _int (2), aCompat)));
    assertEquals ("NaN", _string (XPathOperators.arithmetic (EBinaryOp.ADD, XdmSequence.EMPTY, _int (2), aCompat)));
    assertEquals ("3",
                  _string (XPathOperators.arithmetic (EBinaryOp.ADD,
                                                      _seq (XdmAtomic.integer (1), XdmAtomic.integer (5)),
                                                      _int (2),
                                                      aCompat)));

    assertEquals ("-3", _string (XPathOperators.unaryArithmetic (true, _int (3), aCtx)));
    assertEquals ("3", _string (XPathOperators.unaryArithmetic (false, _int (3), aCtx)));
    assertEquals ("", _string (XPathOperators.unaryArithmetic (true, XdmSequence.EMPTY, aCtx)));
    _assertError ("XPTY0004", () -> XPathOperators.unaryArithmetic (false, _str ("a"), aCtx));

    try
    {
      XPathOperators.arithmetic (EBinaryOp.AND, _int (1), _int (1), aCtx);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  @Test
  public void testCast ()
  {
    final StaticContext aSC = new StaticContext ();
    final DynamicContext aCtx = new DynamicContext (aSC);
    final SeqType aInt = XPathParser.parseSequenceType ("xs:integer", aSC);
    final SeqType aIntOpt = XPathParser.parseSequenceType ("xs:integer?", aSC);
    assertEquals ("12", _string (XPathOperators.cast (_str ("12"), aInt, aCtx)));
    assertEquals ("", _string (XPathOperators.cast (XdmSequence.EMPTY, aIntOpt, aCtx)));
    _assertError ("XPTY0004", () -> XPathOperators.cast (XdmSequence.EMPTY, aInt, aCtx));
    _assertError ("FORG0001", () -> XPathOperators.cast (_str ("x"), aInt, aCtx));
    assertTrue (XPathOperators.castable (_str ("12"), aInt, aCtx));
    assertFalse (XPathOperators.castable (_str ("x"), aInt, aCtx));
    assertFalse (XPathOperators.castable (XdmSequence.EMPTY, aInt, aCtx));
    assertTrue (XPathOperators.castable (XdmSequence.EMPTY, aIntOpt, aCtx));
    assertFalse (XPathOperators.castable (_seq (XdmAtomic.integer (1), XdmAtomic.integer (2)), aInt, aCtx));
    // A node is atomised first
    final IXdmNode aDoc = _parse ("<r>42</r>");
    assertEquals ("42", _string (XPathOperators.cast (_seq (aDoc), aInt, aCtx)));
  }

  @Test
  public void testCallFunction ()
  {
    final StaticContext aSC = new StaticContext ();
    final DynamicContext aCtx = new DynamicContext (aSC);
    final FunctionDef aSubstring = XPathOperators.getFunctionDef (aCtx, new XdmQName (XdmConstants.NS_FN, "substring", ""), 2);
    // Function conversion: untypedAtomic to xs:string, xs:integer promoted to xs:double
    assertEquals ("llo",
                  _string (XPathOperators.callFunction (aSubstring,
                                                        aCtx,
                                                        List.of (XdmSequence.of (XdmAtomic.untyped ("hello")), _int (3)))));
    // A node argument is atomised
    final IXdmNode aDoc = _parse ("<r>hello</r>");
    assertEquals ("ello", _string (XPathOperators.callFunction (aSubstring, aCtx, List.of (_seq (aDoc), _int (2)))));
    // A type error names the function and the argument
    try
    {
      XPathOperators.callFunction (aSubstring, aCtx, List.of (_str ("a"), _str ("b")));
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPTY0004", ex.getCode ());
      assertTrue (ex.getDescription (), ex.getDescription ().startsWith ("fn:substring, argument 2: "));
    }
    _assertError ("XPST0017",
                  () -> XPathOperators.getFunctionDef (aCtx, new XdmQName (XdmConstants.NS_FN, "substring", ""), 7));

    // XPath 1.0 compatibility: a sequence for a single string takes its first item
    final DynamicContext aCompat = new DynamicContext (new StaticContext ().setXPath10Compatibility (true));
    final FunctionDef aStringLength = XPathOperators.getFunctionDef (aCompat,
                                                                     new XdmQName (XdmConstants.NS_FN, "string-length", ""),
                                                                     1);
    assertEquals ("2",
                  _string (XPathOperators.callFunction (aStringLength,
                                                        aCompat,
                                                        List.of (_seq (XdmAtomic.string ("ab"), XdmAtomic.string ("cde"))))));
    _assertError ("XPTY0004",
                  () -> XPathOperators.callFunction (aStringLength,
                                                     aCtx,
                                                     List.of (_seq (XdmAtomic.string ("ab"), XdmAtomic.string ("cde")))));

    try
    {
      XPathOperators.callFunction (aSubstring, aCtx, List.of (_str ("a")));
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }
}
