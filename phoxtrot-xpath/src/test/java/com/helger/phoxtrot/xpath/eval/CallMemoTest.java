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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for {@link CallMemo}: an expression with memoised calls gives the
 * same result as the same expression whose calls cannot be memoised (an
 * argument with <code>[position() ge 1]</code>), and the memo is used where it
 * should be.
 *
 * @author Philip Helger
 */
public final class CallMemoTest
{
  private static final String DOC = "<r><l id='1' p=''>10</l><l id='2' p=' 1 '>4</l><l id='3' p='1'>6</l>" +
                                    "<l id='4' p='3'>x</l><l id='5' p='3'> 7 </l></r>";

  // Pairs of an expression and its reference without memo
  private static final String [] [] PAIRS = { { "for $n in /r/l return count(/r/l[normalize-space(@p) = normalize-space($n/@id)])",
                                                "for $n in /r/l return count(/r/l[normalize-space(@p) = normalize-space($n/@id[position() ge 1])])" },
                                              { "every $n in /r/l[normalize-space(@p) = ''] satisfies sum(/r/l[normalize-space(@p) = string($n/@id)]) = 10",
                                                "every $n in /r/l[normalize-space(@p) = ''] satisfies sum(/r/l[normalize-space(@p) = string($n/@id[position() ge 1])]) = 10" },
                                              // A call reading the focus in the loop body: the same focus in every iteration
                                              { "for $p in /r/l/@p return /r/l[normalize-space($p) = normalize-space(/r/l[2]/@p)]/@id/string()",
                                                "for $p in /r/l/@p return /r/l[normalize-space($p) = normalize-space(/r/l[2]/@p[position() ge 1])]/@id/string()" },
                                              { "/r/l ! (some $p in /r/l/@p satisfies normalize-space($p) = normalize-space(@id))",
                                                "/r/l ! (some $p in /r/l/@p satisfies normalize-space($p) = normalize-space(@id[position() ge 1]))" },
                                              // Nested loops, the same variable name bound twice
                                              { "for $a in (1, 2) return for $a in (3, 4) return string-length(concat(/r/l[$a], $a))",
                                                "for $a in (1, 2) return for $a in (3, 4) return string-length(concat(/r/l[$a][position() ge 1], $a))" },
                                              { "for $a in /r/l return (for $a in $a/@id return string-length(string($a/../text())))",
                                                "for $a in /r/l return (for $a in $a/@id return string-length(string($a/../text()[position() ge 1])))" },
                                              // A call in a predicate inside the loop body reads another focus
                                              { "for $i in 1 to 3 return count(/r/l[string-length(normalize-space(text())) = $i])",
                                                "for $i in 1 to 3 return count(/r/l[string-length(normalize-space(text()[position() ge 1])) = $i])" },
                                              // let and quantifiers inside the call
                                              { "for $n in /r/l return string-join(for $x in $n/@* return string($x), ',')",
                                                "for $n in /r/l return string-join(for $x in $n/@*[position() ge 1] return string($x), ',')" },
                                              // An error is raised every time
                                              { "for $n in /r/l return number($n/text())",
                                                "for $n in /r/l return number($n/text()[position() ge 1])" },
                                              { "for $n in /r/l return xs:integer(normalize-space($n/text()))",
                                                "for $n in /r/l return xs:integer(normalize-space($n/text()[position() ge 1]))" } };

  private static final IXdmNode DOC_NODE;

  static
  {
    try
    {
      DOC_NODE = new XdmParser ().parse (DOC);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @NonNull
  private static String _evalOrError (@NonNull final String sExpr)
  {
    try
    {
      final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
      final StringBuilder aSB = new StringBuilder ();
      for (final IXdmItem aItem : aCompiled.evaluate (aCompiled.createDynamicContext ().withFocus (DOC_NODE, 1, 1)))
      {
        if (!aSB.isEmpty ())
          aSB.append (' ');
        aSB.append (FnAccessor.getStringValue (aItem));
      }
      return aSB.toString ();
    }
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
  }

  @Test
  public void testSameAsWithoutMemo ()
  {
    for (final String [] aPair : PAIRS)
      assertEquals (aPair[0], _evalOrError (aPair[1]), _evalOrError (aPair[0]));
  }

  @Test
  public void testMemoUsed ()
  {
    final CompiledXPath aCompiled = XPathCompiler.compile ("for $n in /r/l return count(/r/l[normalize-space(@p) = normalize-space($n/@id)])",
                                                           new StaticContext ());
    final DynamicContext aCtx = aCompiled.createDynamicContext ().withFocus (DOC_NODE, 1, 1);
    assertEquals (5, aCompiled.evaluate (aCtx).size ());
    // Of the two normalize-space calls only the one with the variable is kept: the other one reads
    // the focus of the predicate
    int nCalls = 0;
    for (final Object aKey : aCtx.getRunMemo ().keySet ())
      if (aKey instanceof final IExpr.FunctionCallExpr aCall && "normalize-space".equals (aCall.name ().localName ()))
      {
        nCalls++;
        assertTrue (aCall.toString (), aCall.args ().get (0) instanceof final IExpr.PathExpr aPath && aPath.start () instanceof IExpr.VarRefExpr);
      }
    assertEquals (1, nCalls);

    // Calls without work or that are not determined by their inputs are not kept
    for (final String sExpr : new String [] { "for $n in /r/l return count(/r/l[normalize-space(@p) = normalize-space($n)])",
                                              "for $n in /r/l return count(/r/l[@p = generate-id($n/@id)])",
                                              "for $n in /r/l return count(/r/l[@p = string(($n/@id)[last()])])" })
    {
      final CompiledXPath aOther = XPathCompiler.compile (sExpr, new StaticContext ());
      final DynamicContext aOtherCtx = aOther.createDynamicContext ().withFocus (DOC_NODE, 1, 1);
      aOther.evaluate (aOtherCtx);
      for (final Object aKey : aOtherCtx.getRunMemo ().keySet ())
        assertFalse (sExpr, aKey instanceof final IExpr.FunctionCallExpr aCall && !"count".equals (aCall.name ().localName ()));
    }
  }

  @Test
  public void testVariableTokens ()
  {
    final StaticContext aSC = new StaticContext ();
    final XdmQName aName = new XdmQName ("n");
    aSC.declareVariable (aName);
    final CompiledXPath aCompiled = XPathCompiler.compile ("string($n/@id)", aSC);
    final DynamicContext aRoot = aCompiled.createDynamicContext ().withFocus (DOC_NODE, 1, 1);
    final XdmSequence aLines = XPathCompiler.compile ("/r/l", aSC).evaluate (aRoot);
    // The same run, other bindings: a new value each time, the same binding: the kept value
    final DynamicContext aFirst = aRoot.withVariable (aName, XdmSequence.of (aLines.get (0)));
    final DynamicContext aSecond = aRoot.withVariable (aName, XdmSequence.of (aLines.get (1)));
    assertEquals ("1", FnAccessor.getStringValue (aCompiled.evaluate (aFirst).get (0)));
    assertEquals ("2", FnAccessor.getStringValue (aCompiled.evaluate (aSecond).get (0)));
    assertEquals ("1", FnAccessor.getStringValue (aCompiled.evaluate (aFirst.withFocus (aLines.get (3), 1, 1)).get (0)));
    assertSame (aFirst.getVariableToken (aName), aFirst.withFocus (aLines.get (3), 1, 1).getVariableToken (aName));
    assertFalse (aFirst.getVariableToken (aName) == aSecond.getVariableToken (aName));
    assertSame (aRoot.getVariableToken (aName), aRoot.getVariableToken (new XdmQName ("other")));
    final Map <Object, Object> aMemo = aRoot.getRunMemo ();
    assertSame (aMemo, aSecond.getRunMemo ());
  }
}
