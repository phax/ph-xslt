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
package com.helger.phoxtrot.xpath.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.EThreading;
import com.helger.phoxtrot.xpath.LargeStackRunner;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ops.Atomizer;

/**
 * Test class for the step limit of {@link DynamicContext} and plain XPath on
 * the threads of {@link LargeStackRunner}.
 *
 * @author Philip Helger
 */
public final class DynamicContextStepLimitTest
{
  private static final String RECURSION = "let $f := function ($f, $n) { if ($n = 0) then 'done' else $f ($f, $n - 1) } return $f ($f, 200)";

  private static void _assertStepLimit (final CompiledXPath aXPath, final long nMaxSteps)
  {
    final DynamicContext aCtx = aXPath.createDynamicContext ();
    aCtx.setMaxSteps (nMaxSteps);
    try
    {
      aXPath.evaluate (aCtx);
      fail ("The step limit must stop " + aXPath.getSource ());
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
      assertTrue (ex.getMessage (), ex.getMessage ().contains ("step limit"));
    }
  }

  @Test
  public void testUnlimitedByDefault ()
  {
    final CompiledXPath aXPath = XPathCompiler.compile ("sum (for $i in 1 to 10000 return $i)", new StaticContext ());
    final DynamicContext aCtx = aXPath.createDynamicContext ();
    assertEquals (DynamicContext.UNLIMITED_STEPS, aCtx.getMaxSteps ());
    assertEquals ("50005000", Atomizer.getStringValue (aXPath.evaluate (aCtx).get (0)));
    assertTrue (aCtx.getStepCount () >= 10000);
  }

  @Test
  public void testLoopAndRecursionAreLimited ()
  {
    _assertStepLimit (XPathCompiler.compile ("sum (for $i in 1 to 10000 return $i)", new StaticContext ()), 1000);
    _assertStepLimit (XPathCompiler.compile ("every $i in 1 to 10000 satisfies $i gt 0", new StaticContext ()), 1000);
    _assertStepLimit (XPathCompiler.compile ("count ((1 to 10000)[. mod 2 = 0])", new StaticContext ()), 1000);
    final CompiledXPath aRecursion = XPathCompiler.compile (RECURSION, new StaticContext ());
    _assertStepLimit (aRecursion, 100);
    // Enough steps: the same expression succeeds
    final DynamicContext aCtx = aRecursion.createDynamicContext ();
    aCtx.setMaxSteps (100_000);
    assertEquals ("done", Atomizer.getStringValue (aRecursion.evaluate (aCtx).get (0)));
  }

  @Test
  public void testLimitIsSharedByDerivedContexts ()
  {
    // No function call: a built-in call with a focus-free argument would be evaluated once per run (CallMemo)
    final CompiledXPath aXPath = XPathCompiler.compile ("(1 to 600)[. mod 2 = 0]", new StaticContext ());
    final DynamicContext aCtx = aXPath.createDynamicContext ();
    aCtx.setMaxSteps (1000);
    assertEquals (300, aXPath.evaluate (aCtx).size ());
    assertTrue (aCtx.getStepCount () >= 600);
    // The second evaluation with a derived context counts on
    try
    {
      aXPath.evaluate (aCtx.withFocus (null, 0, 0));
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
    }
  }

  @Test
  public void testLargeStackRunner ()
  {
    final CompiledXPath aXPath = XPathCompiler.compile (RECURSION, new StaticContext ());
    for (final EThreading eThreading : EThreading.values ())
      assertEquals ("done",
                    LargeStackRunner.run ( () -> Atomizer.getStringValue (aXPath.evaluate (aXPath.createDynamicContext ()).get (0)),
                                           "phoxtrot-xpath-test",
                                           eThreading));
  }
}
