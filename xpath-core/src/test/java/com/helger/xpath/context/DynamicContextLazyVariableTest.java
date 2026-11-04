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
package com.helger.xpath.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for the lazy variable bindings of class {@link DynamicContext}.
 *
 * @author Philip Helger
 */
public final class DynamicContextLazyVariableTest
{
  private static final XdmQName V = new XdmQName ("v");

  @Test
  public void testUnusedIsNeverEvaluated ()
  {
    final StaticContext aStatic = new StaticContext ().declareVariable (V);
    final AtomicInteger aCalls = new AtomicInteger ();
    final DynamicContext aCtx = new DynamicContext (aStatic).withLazyVariable (V, () -> {
      aCalls.incrementAndGet ();
      throw XPathException.of ("FOAR0001", "Division by zero");
    });
    final XdmSequence aResult = XPathCompiler.compile ("1 + 1", aStatic).evaluate (aCtx);
    assertEquals ("2", ((XdmAtomic) aResult.getFirst ()).getLexical ());
    assertEquals (0, aCalls.get ());
  }

  @Test
  public void testEvaluatedOnce ()
  {
    final StaticContext aStatic = new StaticContext ().declareVariable (V);
    final AtomicInteger aCalls = new AtomicInteger ();
    final DynamicContext aCtx = new DynamicContext (aStatic).withLazyVariable (V, () -> {
      aCalls.incrementAndGet ();
      return XdmSequence.of (XdmAtomic.integer (21));
    });
    final CompiledXPath aXPath = XPathCompiler.compile ("$v + $v", aStatic);
    assertEquals ("42", ((XdmAtomic) aXPath.evaluate (aCtx).getFirst ()).getLexical ());
    // Also through a derived context
    assertEquals ("42", ((XdmAtomic) aXPath.evaluate (aCtx.withFocus (null, 0, 0)).getFirst ()).getLexical ());
    assertEquals (1, aCalls.get ());
  }

  @Test
  public void testFailureIsKept ()
  {
    final StaticContext aStatic = new StaticContext ().declareVariable (V);
    final AtomicInteger aCalls = new AtomicInteger ();
    final DynamicContext aCtx = new DynamicContext (aStatic).withLazyVariable (V, () -> {
      aCalls.incrementAndGet ();
      throw XPathException.of ("FOAR0001", "Division by zero");
    });
    final CompiledXPath aXPath = XPathCompiler.compile ("$v", aStatic);
    XPathException aFirst = null;
    for (int i = 0; i < 2; ++i)
      try
      {
        aXPath.evaluate (aCtx);
        fail ();
      }
      catch (final XPathException ex)
      {
        assertEquals ("FOAR0001", ex.getCode ());
        if (aFirst == null)
          aFirst = ex;
        else
          assertSame (aFirst, ex);
      }
    assertEquals (1, aCalls.get ());
  }

  @Test
  public void testShadowing ()
  {
    final StaticContext aStatic = new StaticContext ().declareVariable (V);
    final DynamicContext aCtx = new DynamicContext (aStatic).withLazyVariable (V,
                                                                              () -> XdmSequence.of (XdmAtomic.integer (1)))
                                                            .withVariable (V, XdmSequence.of (XdmAtomic.integer (2)));
    assertEquals ("2", ((XdmAtomic) XPathCompiler.compile ("$v", aStatic).evaluate (aCtx).getFirst ()).getLexical ());
  }
}
