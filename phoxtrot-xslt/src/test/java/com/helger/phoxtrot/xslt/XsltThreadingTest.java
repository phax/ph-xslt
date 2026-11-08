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
package com.helger.phoxtrot.xslt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.runtime.LargeStackRunner;

/**
 * Test class for {@link EXsltThreading}: the thread each mode runs on, the
 * same results, errors and stack overflows in every mode, and the
 * interruption of the waiting caller.
 *
 * @author Philip Helger
 */
public final class XsltThreadingTest
{
  private static final String SHEET = XsltTestHelper.SHEET +
                                      "<xsl:template match='/'><r><xsl:value-of select='sum(//v)'/></r></xsl:template>" +
                                      XsltTestHelper.END;

  /** Endless recursion of the Java stack; never returns normally. */
  private static int _recurse (final int n)
  {
    return _recurse (n + 1) + 1;
  }

  @Test
  public void testThreadOfEachMode ()
  {
    final Thread aCaller = Thread.currentThread ();
    final Thread aNew = LargeStackRunner.run (Thread::currentThread, "phoxtrot-test", EXsltThreading.NEW_THREAD);
    assertNotSame (aCaller, aNew);
    assertEquals ("phoxtrot-test", aNew.getName ());
    final Thread aPooled = LargeStackRunner.run (Thread::currentThread, "phoxtrot-test", EXsltThreading.THREAD_POOL);
    assertNotSame (aCaller, aPooled);
    assertTrue (aPooled.getName (), aPooled.getName ().startsWith ("phoxtrot-pool-"));
    assertTrue (aPooled.isDaemon ());
    assertSame (aCaller, LargeStackRunner.run (Thread::currentThread, "phoxtrot-test", EXsltThreading.CALLING_THREAD));

    // A nested run stays on the worker thread, whatever its mode
    for (final EXsltThreading eOuter : new EXsltThreading [] { EXsltThreading.NEW_THREAD,
                                                               EXsltThreading.THREAD_POOL })
      for (final EXsltThreading eInner : EXsltThreading.values ())
      {
        final Thread [] aThreads = LargeStackRunner.run ( () -> new Thread [] { Thread.currentThread (),
                                                                                LargeStackRunner.run (Thread::currentThread,
                                                                                                      "inner",
                                                                                                      eInner) },
                                                          "outer",
                                                          eOuter);
        assertSame (eOuter + "/" + eInner, aThreads[0], aThreads[1]);
      }
  }

  @Test
  public void testErrors ()
  {
    for (final EXsltThreading eMode : EXsltThreading.values ())
    {
      // A runtime exception arrives unchanged
      final IllegalArgumentException aEx = new IllegalArgumentException ("test");
      try
      {
        LargeStackRunner.run ( () -> {
          throw aEx;
        }, "phoxtrot-test", eMode);
        fail ();
      }
      catch (final IllegalArgumentException ex)
      {
        assertSame (aEx, ex);
      }

      // A stack overflow becomes XPDY0130
      try
      {
        LargeStackRunner.run ( () -> Integer.valueOf (_recurse (0)), "phoxtrot-test", eMode);
        fail ();
      }
      catch (final XPathException ex)
      {
        assertEquals (eMode.name (), "XPDY0130", ex.getCode ());
      }
    }
  }

  @Test
  public void testInterruptedCaller ()
  {
    for (final EXsltThreading eMode : new EXsltThreading [] { EXsltThreading.NEW_THREAD,
                                                              EXsltThreading.THREAD_POOL })
    {
      Thread.currentThread ().interrupt ();
      try
      {
        LargeStackRunner.run ( () -> "x", "phoxtrot-test", eMode);
        fail ();
      }
      catch (final XPathException ex)
      {
        assertEquals ("FOER0000", ex.getCode ());
      }
      // The interrupt flag is set again
      assertTrue (Thread.interrupted ());
    }
  }

  @Test
  public void testTransformInEachMode () throws Exception
  {
    final IXdmNode aDoc = XsltTestHelper.parse ("<d><v>1</v><v>2</v><v>3</v></d>");
    for (final EXsltThreading eMode : EXsltThreading.values ())
    {
      final XsltExecutable aExec = XsltCompiler.compile (SHEET, new XsltCompileOptions ().setThreading (eMode));
      final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (aDoc)
                                                                       .setThreading (eMode);
      final String sOut = aExec.transform (aRequest).principalSerialized ();
      assertTrue (eMode.name () + ": " + sOut, sOut.endsWith (">6</r>"));
    }

    // Unbounded recursion ends with XPDY0130 on the calling thread as well
    final XsltExecutable aDeep = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                       "<xsl:template match='/'><xsl:call-template name='t'/></xsl:template>" +
                                                       "<xsl:template name='t'><x/><xsl:call-template name='t'/></xsl:template>" +
                                                       XsltTestHelper.END,
                                                       new XsltCompileOptions ());
    try
    {
      aDeep.transform (new XsltTransformRequest ().setGlobalContextItem (aDoc)
                                                  .setThreading (EXsltThreading.CALLING_THREAD));
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
    }
  }

  @Test
  public void testConcurrentPool () throws Exception
  {
    final IXdmNode aDoc = XsltTestHelper.parse ("<d><v>1</v><v>2</v></d>");
    final XsltExecutable aExec = XsltCompiler.compile (SHEET, new XsltCompileOptions ());
    final ExecutorService aCallers = Executors.newFixedThreadPool (8);
    try
    {
      final List <Future <String>> aResults = new ArrayList <> ();
      for (int i = 0; i < 400; ++i)
        aResults.add (aCallers.submit ( () -> aExec.transform (new XsltTransformRequest ().setGlobalContextItem (aDoc)
                                                                                          .setThreading (EXsltThreading.THREAD_POOL))
                                                   .principalSerialized ()));
      for (final Future <String> aResult : aResults)
        assertTrue (aResult.get ().endsWith (">3</r>"));
    }
    finally
    {
      aCallers.shutdown ();
    }
  }
}
