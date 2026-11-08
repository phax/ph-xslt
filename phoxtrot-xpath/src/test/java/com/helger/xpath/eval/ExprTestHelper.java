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
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.Assume;

import com.helger.annotation.style.PresentForCodeCoverage;
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
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Factory methods for building expression trees in tests, and guards that
 * skip a test while a workstream it depends on is still a stub.
 *
 * @author Philip Helger
 */
public final class ExprTestHelper
{
  @PresentForCodeCoverage
  private static final ExprTestHelper INSTANCE = new ExprTestHelper ();

  private ExprTestHelper ()
  {}

  private static boolean _isImplemented (final Runnable aCall)
  {
    try
    {
      aCall.run ();
      return true;
    }
    catch (final UnsupportedOperationException ex)
    {
      return false;
    }
    catch (final RuntimeException ex)
    {
      return true;
    }
  }

  /** Skip unless the atomic operations workstream (ops, AtomicLexical) is in. */
  public static void assumeOps ()
  {
    Assume.assumeTrue ("needs atomic-values workstream",
                       _isImplemented ( () -> Atomizer.getEffectiveBooleanValue (XdmSequence.EMPTY)) &&
                                                         _isImplemented ( () -> AtomicComparator.getMapKey (XdmAtomic.integer (1))) &&
                                                         _isImplemented ( () -> AtomicCaster.castFromString ("1",
                                                                                                             EAtomicType.INTEGER,
                                                                                                             null)) &&
                                                         _isImplemented ( () -> XdmAtomic.integer (1).getLexical ()));
  }

  /** Skip unless the parser workstream (function signatures) is in. */
  public static void assumeParser ()
  {
    Assume.assumeTrue ("needs parser workstream",
                       _isImplemented ( () -> XPathParser.parseFunctionSignature ("() as item()*",
                                                                                  new StaticContext ())));
  }

  /** Skip unless the date/time workstream is in. */
  public static void assumeDateTime ()
  {
    Assume.assumeTrue ("needs date/time workstream",
                       _isImplemented ( () -> XdmDateTimeValue.parse (EAtomicType.DATE, "2020-01-01")));
  }

  public static IXdmNode parse (final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  public static DynamicContext ctx ()
  {
    return new DynamicContext (new StaticContext ());
  }

  public static DynamicContext ctx (final IXdmItem aItem)
  {
    return ctx ().withFocus (aItem, 1, 1);
  }

  public static XdmSequence eval (final IExpr aExpr, final DynamicContext aCtx)
  {
    return XPathEvaluator.evaluate (aExpr, aCtx);
  }

  public static XdmSequence eval (final IExpr aExpr)
  {
    return eval (aExpr, ctx ());
  }

  public static void assertError (final String sCode, final Runnable aRunnable)
  {
    try
    {
      aRunnable.run ();
      fail ("Expected error " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (ex.getMessage (), sCode, ex.getCode ());
    }
  }

  public static void assertEvalError (final String sCode, final IExpr aExpr, final DynamicContext aCtx)
  {
    assertError (sCode, () -> eval (aExpr, aCtx));
  }

  public static void assertEvalError (final String sCode, final IExpr aExpr)
  {
    assertEvalError (sCode, aExpr, ctx ());
  }

  /** @return The integer values of an all-integer sequence. */
  public static List <Long> ints (final XdmSequence aSeq)
  {
    final Long [] ret = new Long [aSeq.size ()];
    for (int i = 0; i < ret.length; ++i)
      ret[i] = Long.valueOf (((XdmAtomic) aSeq.get (i)).getBigInteger ().longValueExact ());
    return Arrays.asList (ret);
  }

  public static List <Long> longs (final long... aValues)
  {
    final Long [] ret = new Long [aValues.length];
    for (int i = 0; i < ret.length; ++i)
      ret[i] = Long.valueOf (aValues[i]);
    return Arrays.asList (ret);
  }

  /** @return The local names of an all-node sequence (or attribute value/text content). */
  public static String names (final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      final IXdmNode aNode = (IXdmNode) aItem;
      if (aNode.isElement () || aNode.isAttribute ())
        aSB.append (aNode.getName ().localName ());
      else
        if (aNode.isDocument ())
          aSB.append ("#doc");
        else
          aSB.append (aNode.getStringValue ());
    }
    return aSB.toString ();
  }

  /** @return The string values of the items, space separated. */
  public static String texts (final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (aItem instanceof final IXdmNode aNode ? aNode.getStringValue () : ((XdmAtomic) aItem).getString ());
    }
    return aSB.toString ();
  }

  public static boolean boolValue (final XdmSequence aSeq)
  {
    assertEquals (1, aSeq.size ());
    return ((XdmAtomic) aSeq.get (0)).getBoolean ();
  }

  public static String stringValue (final XdmSequence aSeq)
  {
    assertEquals (1, aSeq.size ());
    return ((XdmAtomic) aSeq.get (0)).getString ();
  }

  public static XdmQName qn (final String sLocal)
  {
    return new XdmQName (sLocal);
  }

  public static XdmQName fn (final String sLocal)
  {
    return new XdmQName (XdmConstants.NS_FN, sLocal);
  }

  public static IExpr lit (final long n)
  {
    return new IExpr.LiteralExpr (XdmAtomic.integer (n));
  }

  public static IExpr lit (final BigInteger a)
  {
    return new IExpr.LiteralExpr (XdmAtomic.integer (a));
  }

  public static IExpr lit (final XdmAtomic a)
  {
    return new IExpr.LiteralExpr (a);
  }

  public static IExpr str (final String s)
  {
    return new IExpr.LiteralExpr (XdmAtomic.string (s));
  }

  public static IExpr dbl (final double d)
  {
    return new IExpr.LiteralExpr (XdmAtomic.dbl (d));
  }

  public static IExpr var (final String s)
  {
    return new IExpr.VarRefExpr (qn (s));
  }

  public static IExpr dot ()
  {
    return new IExpr.ContextItemExpr ();
  }

  public static IExpr seq (final IExpr... a)
  {
    return new IExpr.SequenceExpr (List.of (a));
  }

  public static IExpr range (final IExpr a, final IExpr b)
  {
    return new IExpr.RangeExpr (a, b);
  }

  public static IExpr bin (final EBinaryOp e, final IExpr a, final IExpr b)
  {
    return new IExpr.BinaryExpr (e, a, b);
  }

  public static IExpr cmp (final EComparisonOp e, final IExpr a, final IExpr b)
  {
    return new IExpr.ComparisonExpr (e, a, b);
  }

  public static IExpr call (final XdmQName aName, final IExpr... aArgs)
  {
    return new IExpr.FunctionCallExpr (aName, List.of (aArgs));
  }

  public static IExpr dynCall (final IExpr aBase, final IExpr... aArgs)
  {
    return new IExpr.DynamicCallExpr (aBase, List.of (aArgs));
  }

  public static IExpr hole ()
  {
    return new IExpr.PlaceholderExpr ();
  }

  public static IExpr let (final String sVar, final IExpr aValue, final IExpr aBody)
  {
    return new IExpr.LetExpr (List.of (new IExpr.Binding (qn (sVar), null, aValue)), aBody);
  }

  public static IExpr forExpr (final String sVar, final IExpr aIn, final IExpr aBody)
  {
    return new IExpr.ForExpr (List.of (new IExpr.Binding (qn (sVar), null, aIn)), aBody);
  }

  public static IExpr inline (final IExpr aBody, final String... aParams)
  {
    final IExpr.Param [] aP = new IExpr.Param [aParams.length];
    for (int i = 0; i < aP.length; ++i)
      aP[i] = new IExpr.Param (qn (aParams[i]), null);
    return new IExpr.InlineFunctionExpr (List.of (aP), null, aBody);
  }

  public static IExpr inlineTyped (final IExpr aBody, final SeqType aReturnType, final IExpr.Param... aParams)
  {
    return new IExpr.InlineFunctionExpr (List.of (aParams), aReturnType, aBody);
  }

  public static NodeTest nameTest (final String sLocal)
  {
    return new NodeTest (ENodeTestKind.NAME, false, false, false, qn (sLocal), null, false, null);
  }

  public static NodeTest anyName ()
  {
    return new NodeTest (ENodeTestKind.NAME, true, false, false, null, null, false, null);
  }

  public static IExpr.StepExpr step (final EAxis eAxis, final NodeTest aTest, final IExpr... aPreds)
  {
    return new IExpr.StepExpr (eAxis, true, aTest, List.of (aPreds), null);
  }

  public static IExpr.StepExpr child (final String sLocal, final IExpr... aPreds)
  {
    return step (EAxis.CHILD, nameTest (sLocal), aPreds);
  }

  public static IExpr.StepExpr postfix (final IExpr aExpr, final IExpr... aPreds)
  {
    return new IExpr.StepExpr (null, false, null, List.of (aPreds), aExpr);
  }

  public static IExpr.StepExpr descOrSelf ()
  {
    return step (EAxis.DESCENDANT_OR_SELF, NodeTest.ANY_NODE);
  }

  public static IExpr path (final IExpr.StepExpr... aSteps)
  {
    return new IExpr.PathExpr (null, false, List.of (aSteps));
  }

  public static IExpr absPath (final IExpr.StepExpr... aSteps)
  {
    return new IExpr.PathExpr (null, true, List.of (aSteps));
  }

  public static IExpr pathFrom (final IExpr aStart, final IExpr.StepExpr... aSteps)
  {
    return new IExpr.PathExpr (aStart, false, List.of (aSteps));
  }

  public static IExpr filter (final IExpr aPrimary, final IExpr... aPreds)
  {
    return new IExpr.FilterExpr (aPrimary, List.of (aPreds));
  }

  public static SeqType atomicType (final EAtomicType e, final EOccurrence eOcc)
  {
    return SeqType.of (ItemType.atomic (e), eOcc);
  }

  public static ItemType funcType (final SeqType aReturn, final SeqType... aParams)
  {
    return new ItemType (ItemType.Kind.FUNCTION, null, null, false, List.of (aParams), aReturn, null, null);
  }

  public static ItemType mapType (final EAtomicType eKey, final SeqType aValue)
  {
    return new ItemType (ItemType.Kind.MAP, null, null, false, null, null, eKey, aValue);
  }

  public static ItemType arrayType (final SeqType aMember)
  {
    return new ItemType (ItemType.Kind.ARRAY, null, null, false, null, null, null, aMember);
  }
}
