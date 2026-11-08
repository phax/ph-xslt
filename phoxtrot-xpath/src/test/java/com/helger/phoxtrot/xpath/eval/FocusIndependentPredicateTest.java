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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for the evaluation of focus-independent predicates once per step
 * or filter application in {@link XPathEvaluator}: the result must equal the
 * evaluation per candidate, which a predicate wrapped in
 * <code>if (exists(.)) then (P) else ()</code> still gets.
 *
 * @author Philip Helger
 */
public final class FocusIndependentPredicateTest
{
  private static final String DOC = "<r><x a='1'>p</x><x>q</x><x a='3'>r</x><y/><x a='5'>s</x></r>";

  // Predicates whose value does not depend on the focus
  private static final String [] FOCUS_FREE = { "2",
                                                "$two",
                                                "$two + 1",
                                                "max((1, $two * 3 idiv 2))",
                                                "$two * 1.5",
                                                "$two div 4",
                                                "xs:double($two)",
                                                "xs:float(3)",
                                                "1.0",
                                                "2.5",
                                                "0",
                                                "-1",
                                                "99",
                                                "xs:double('NaN')",
                                                "xs:double('INF')",
                                                "-4294967291.0",
                                                "4294967297",
                                                "true()",
                                                "false()",
                                                "$two = 2",
                                                "$two gt 5",
                                                "$empty",
                                                "($two, 3)[1]",
                                                "'a'",
                                                "''",
                                                "$nodes",
                                                "$nodes/@a",
                                                "$nodes/@missing",
                                                "count($nodes)",
                                                "count($nodes) idiv 2",
                                                "let $k := $two return $k + 1",
                                                "for $i in 1 to 2 return $i = 2",
                                                "some $i in 1 to 3 satisfies $i = $two",
                                                "if ($two eq 2) then 3 else 1",
                                                "string-length('abc')",
                                                "$two instance of xs:integer",
                                                "$two castable as xs:date",
                                                "exists($empty)",
                                                "map { 'k' : 4 }?k",
                                                "string($two) = '2'",
                                                "current-dateTime() = current-dateTime()",
                                                "math:pi() gt 3",
                                                "number('2')" };

  // Predicates that read the focus or are otherwise not decided once
  private static final String [] NOT_FOCUS_FREE = { ".",
                                                    "@a",
                                                    "position()",
                                                    "last()",
                                                    "position() = 2",
                                                    "last() - 1",
                                                    "string()",
                                                    "name()",
                                                    "string-length()",
                                                    "number()",
                                                    "data()",
                                                    "normalize-space()",
                                                    ". = 'q'",
                                                    "/r",
                                                    "self::x",
                                                    "$nodes[. = 'q']",
                                                    "$nodes[@a]",
                                                    "$nodes ! .",
                                                    "?k",
                                                    "function-lookup(xs:QName('fn:true'), 0)()",
                                                    "true#0()",
                                                    "(function () { 2 })()",
                                                    "$f(2)",
                                                    "[1, 2]($two)",
                                                    "trace(2, 'x')",
                                                    "generate-id($nodes[1]) = ''",
                                                    "random-number-generator()?number gt 2",
                                                    "doc-available('x')" };

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

  @NonNull
  private static StaticContext _createStaticContext ()
  {
    final StaticContext ret = new StaticContext ();
    ret.declareNamespace ("math", FunctionLibrary.NS_MATH);
    ret.declareVariable (new XdmQName ("two"));
    return ret;
  }

  @NonNull
  private static String _eval (@NonNull final String sExpr,
                               @NonNull final StaticContext aSC,
                               @NonNull final IXdmNode aDoc)
  {
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, aSC);
    final DynamicContext aCtx = aCompiled.createDynamicContext ().withFocus (aDoc, 1, 1);
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aCompiled.evaluate (aCtx))
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (FnAccessor.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _evalOrError (@NonNull final String sExpr, @NonNull final IXdmNode aDoc)
  {
    try
    {
      return _eval (sExpr, _createStaticContext (), aDoc);
    }
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
  }

  /**
   * The bindings of the variables the predicates use, in front of the
   * expression.
   */
  @NonNull
  private static String _withBindings (@NonNull final String sExpr)
  {
    return "let $two := 2, $empty := (), $nodes := /r/x, $f := function ($a) { $a } return " + sExpr;
  }

  private static void _assertEquivalent (@NonNull final String sPredicate, @NonNull final IXdmNode aDoc)
  {
    final String sReference = "[if (exists(.)) then (" + sPredicate + ") else ()]";
    final String sDirect = "[" + sPredicate + "]";
    // Axis steps, filter expressions on nodes and on atomic values, early exit
    for (final String sTemplate : new String [] { "/r/x#",
                                                  "/r/*#",
                                                  "(/r/x)#",
                                                  "(10 to 15)#",
                                                  "('a', 'b', 'c')#",
                                                  "/r/x#/@a",
                                                  "count(/r/x#)",
                                                  "exists(/r/x#)",
                                                  "empty((10 to 15)#)",
                                                  "boolean(/r/x#)",
                                                  "/r/x[@a]#",
                                                  "/r/x#[2]",
                                                  "//x#",
                                                  "/r/x[1]#" })
    {
      final String sExpected = _evalOrError (_withBindings (sTemplate.replace ("#", sReference)), aDoc);
      final String sActual = _evalOrError (_withBindings (sTemplate.replace ("#", sDirect)), aDoc);
      assertEquals (sTemplate + " with " + sPredicate, sExpected, sActual);
    }
  }

  @Test
  public void testEquivalence ()
  {
    final IXdmNode aDoc = _parse (DOC);
    for (final String sPredicate : FOCUS_FREE)
      _assertEquivalent (sPredicate, aDoc);
    for (final String sPredicate : NOT_FOCUS_FREE)
      if (!sPredicate.contains ("random") && !sPredicate.contains ("trace"))
        _assertEquivalent (sPredicate, aDoc);
  }

  @Test
  public void testClassification ()
  {
    final StaticContext aSC = _createStaticContext ();
    aSC.declareVariable (new XdmQName ("empty"));
    aSC.declareVariable (new XdmQName ("nodes"));
    aSC.declareVariable (new XdmQName ("f"));
    for (final String sPredicate : FOCUS_FREE)
      assertTrue (sPredicate,
                  ExprWalker.isFocusIndependent (XPathCompiler.compile (sPredicate, aSC).getExpression (), x -> true));
    for (final String sPredicate : NOT_FOCUS_FREE)
      assertFalse (sPredicate,
                   ExprWalker.isFocusIndependent (XPathCompiler.compile (sPredicate, aSC).getExpression (), x -> true));
  }

  @Test
  public void testEmptyInputRaisesNothing ()
  {
    final IXdmNode aDoc = _parse (DOC);
    assertEquals ("", _evalOrError ("()[1 div 0]", aDoc));
    assertEquals ("", _evalOrError ("/r/missing[error()]", aDoc));
    assertEquals ("false", _evalOrError ("exists(/r/missing[1 div 0])", aDoc));
    // One candidate: the predicate is evaluated as before
    assertEquals ("error FOAR0001", _evalOrError ("/r/y[1 div 0]", aDoc));
  }

  @Test
  public void testErrors ()
  {
    final IXdmNode aDoc = _parse (DOC);
    assertEquals ("error FOAR0001", _evalOrError ("/r/x[1 div 0]", aDoc));
    assertEquals ("error FOAR0001", _evalOrError ("(1 to 5)[1 idiv 0]", aDoc));
    assertEquals ("error FOAR0001", _evalOrError ("exists((1 to 5)[1 idiv 0])", aDoc));
    // The effective boolean value of a sequence of two atomic values
    assertEquals ("error FORG0006", _evalOrError ("/r/x[(1, 2)]", aDoc));
    assertEquals ("error FOER0000", _evalOrError ("/r/x[error()]", aDoc));
    // A positional predicate before the focus-free one: positions count within its result
    assertEquals ("s", _evalOrError ("let $two := 2 return /r/x[position() gt 2][$two]", aDoc));
  }

  @Test
  public void testHostFunctionUnderBuiltInName ()
  {
    // A host library that replaces fn:abs with a function reading the focus:
    // the predicate must be evaluated per candidate
    final FunctionLibrary aLib = FunctionLibrary.getDefault ().createChild ();
    aLib.register (XdmConstants.NS_FN,
                   "abs",
                   "(xs:numeric?) as xs:numeric?",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.integer (aCtx.getPosition ())),
                   true);
    final StaticContext aSC = new StaticContext ();
    aSC.setFunctionLibrary (aLib);
    final IXdmNode aDoc = _parse (DOC);
    assertEquals ("p q r s", _eval ("/r/x[abs(1)]", aSC, aDoc));
    assertEquals ("12 13", _eval ("(10 to 15)[abs(1) = (3, 4)]", aSC, aDoc));
  }

  @Test
  public void testUnaffectedByOrderOfCandidates ()
  {
    final IXdmNode aDoc = _parse (DOC);
    // Reverse axis: the position counts in reverse document order
    assertEquals ("q", _evalOrError ("let $two := 2 return /r/y/preceding-sibling::x[$two]", aDoc));
    assertEquals ("p", _evalOrError ("let $two := 2 return /r/y/preceding-sibling::x[$two + 1]", aDoc));
  }

  @Test
  public void testRejectsOtherNamespaces ()
  {
    final StaticContext aSC = _createStaticContext ();
    aSC.declareNamespace ("map", FunctionLibrary.NS_MAP);
    // map: and array: functions are left out: several take function items
    assertFalse (ExprWalker.isFocusIndependent (XPathCompiler.compile ("map:size(map{})", aSC).getExpression (),
                                                x -> true));
  }
}
