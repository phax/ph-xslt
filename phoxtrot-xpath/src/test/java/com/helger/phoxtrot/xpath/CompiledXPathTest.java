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
package com.helger.phoxtrot.xpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FormatDecimalFormat;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for {@link CompiledXPath}.
 *
 * @author Philip Helger
 */
public final class CompiledXPathTest
{
  @Test
  public void testEvaluatesWithOwnStaticContext ()
  {
    final StaticContext aSC = new StaticContext ();
    aSC.declareNamespace ("p", "urn:p");
    aSC.addDecimalFormat (new XdmQName ("de"), FormatDecimalFormat.builder ().decimalSeparator (',').groupingSeparator ('.').build ());
    final XdmQName aVar = new XdmQName ("v");
    aSC.declareVariable (aVar);
    final CompiledXPath aXPath = XPathCompiler.compile ("format-number($v, '#.##0,0', 'de'), namespace-uri-from-QName(xs:QName('p:x'))",
                                                        aSC);

    // A dynamic context of another origin: focus, variables and run-wide
    // state come from it, the static context of the expression is kept
    final DynamicContext aCtx = new DynamicContext (new StaticContext ()).withVariable (aVar,
                                                                                       XdmSequence.of (XdmAtomic.decimal (new BigDecimal ("1234.5"))));
    final XdmSequence aResult = aXPath.evaluate (aCtx);
    assertEquals (2, aResult.size ());
    assertEquals ("1.234,5", Atomizer.getStringValue (aResult.get (0)));
    assertEquals ("urn:p", Atomizer.getStringValue (aResult.get (1)));
  }

  @Test
  public void testFreeVariables ()
  {
    final StaticContext aSC = new StaticContext ();
    for (final String s : new String [] { "a", "b", "c", "x", "unused" })
      aSC.declareVariable (new XdmQName (s));
    final CompiledXPath aXPath = XPathCompiler.compile ("for $x in $a return $x + $b, $x, let $c := 1 return $c, " +
                                                        "function ($b) { $b }, some $y in $c satisfies $y, " +
                                                        "let $x := $x, $z := $x return $z",
                                                        aSC);
    assertEquals (new CommonsArrayList <> (new XdmQName ("a"), new XdmQName ("b"), new XdmQName ("x"), new XdmQName ("c")),
                  new CommonsArrayList <> (aXPath.getFreeVariables ()));
    // A copy each time
    aXPath.getFreeVariables ().clear ();
    assertEquals (4, aXPath.getFreeVariables ().size ());
    assertTrue (XPathCompiler.compile ("for $i in 1 to 3 return $i", aSC).getFreeVariables ().isEmpty ());
  }

  @Test
  public void testNodeTestNamesAreInterned ()
  {
    // A namespace URI read from a file is no interned literal
    final StaticContext aSC = new StaticContext ();
    aSC.declareNamespace ("p", new String ("urn:p:interned"));
    final IExpr.PathExpr aPath = (IExpr.PathExpr) XPathCompiler.compile ("p:a/b", aSC).getExpression ();
    final NodeTest aFirst = aPath.steps ().get (0).test ();
    assertSame ("urn:p:interned", aFirst.name ().namespaceUri ());
    assertSame ("a", aFirst.name ().localName ());
    assertSame ("p", aFirst.name ().prefix ());
    assertSame ("b", aPath.steps ().get (1).test ().name ().localName ());
  }
}
