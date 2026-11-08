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
package com.helger.xpath.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.wrapper.Wrapper;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;

/**
 * Test class for the nesting depth limit of {@link XPathParser}: hostile input must yield XPDY0130,
 * never a StackOverflowError, independent of the stack size of the calling thread.
 *
 * @author Philip Helger
 */
public final class XPathParserDepthTest
{
  private static final int MAX = XPathParserImpl.MAX_DEPTH;

  @NonNull
  private static String _nest (@NonNull final String sOpen,
                               @NonNull final String sCore,
                               @NonNull final String sClose,
                               final int n)
  {
    return sOpen.repeat (n) + sCore + sClose.repeat (n);
  }

  private static void _ok (@NonNull final String sExpr)
  {
    final StaticContext aCtx = ParserTestHelper.createContext ();
    assertNotNull (XPathParser.parse (sExpr, aCtx));
  }

  private static void _tooDeep (@NonNull final String sExpr)
  {
    try
    {
      XPathParser.parse (sExpr, ParserTestHelper.createContext ());
      fail ("Expected XPDY0130");
    }
    catch (final XPathException ex)
    {
      assertEquals (ex.getMessage (), "XPDY0130", ex.getCode ());
      // The message must not contain the whole input
      assertTrue (ex.getMessage ().length () < 1000);
    }
  }

  private static void _checkAll ()
  {
    // The outermost ExprSingle counts as one level
    _ok (_nest ("(", "1", ")", MAX - 1));
    _tooDeep (_nest ("(", "1", ")", MAX));
    _tooDeep (_nest ("(", "1", ")", 1_000_000));
    _tooDeep ("(".repeat (1_000_000));

    final String [] [] aNestings = { { "[", "1", "]" },
                                     { "count(", "1", ")" },
                                     { "$v[", "1", "]" },
                                     { "if (1) then ", "1", " else 2" },
                                     { "-(", "1", ")" },
                                     { "map{1:", "1", "}" },
                                     { "array{", "1", "}" },
                                     { "function($a){", "1", "}" },
                                     { "for $x in 1 return ", "$x", "" },
                                     { "let $x := ", "1", " return $x" },
                                     { "$f(", "1", ")" },
                                     { "$m?(", "1", ")" },
                                     { "a[", "1", "]" },
                                     { "a/(", "1", ")" } };
    for (final String [] a : aNestings)
    {
      _ok (_nest (a[0], a[1], a[2], MAX / 2));
      _tooDeep (_nest (a[0], a[1], a[2], MAX + 1));
    }

    // Operator chains build nested AST nodes and count as well
    _ok ("1" + " + 1".repeat (MAX - 2));
    _tooDeep ("1" + " + 1".repeat (MAX + 10));
    _tooDeep ("1" + " or 1".repeat (100_000));
    _tooDeep ("$m" + "?a".repeat (MAX + 10));
    _tooDeep ("$f" + "(1)".repeat (MAX + 10));
    _tooDeep ("1" + " => abs()".repeat (MAX + 10));
    _ok ("1" + " => abs()".repeat (MAX / 2));

    // Flat structures are not limited
    _ok ("-".repeat (100_000) + "1");
    _ok ("1" + ", 1".repeat (100_000));
    _ok ("a" + "/a".repeat (100_000));
    _ok ("1" + " ! 1".repeat (100_000));
    _ok ("$v" + "[1]".repeat (100_000));
    _ok (_nest ("(:", "", ":)", 100_000) + "1");
    _ok ("'" + "x".repeat (1_000_000) + "'");

    // Nested sequence types
    final StaticContext aCtx = ParserTestHelper.createContext ();
    assertNotNull (XPathParser.parseSequenceType (_nest ("array(", "xs:integer", ")", MAX / 2), aCtx));
    try
    {
      XPathParser.parseSequenceType (_nest ("array(", "xs:integer", ")", MAX + 1), aCtx);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
    }
    try
    {
      XPathParser.parse ("1 instance of " + _nest ("function(", "item()", ") as item()", 100_000), aCtx);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
    }
  }

  @Test
  public void testDepthLimit ()
  {
    _checkAll ();
  }

  @Test
  public void testDepthLimitOnSmallStack () throws InterruptedException
  {
    // The same results on a thread with a tiny stack
    final Wrapper <Throwable> aError = new Wrapper <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        _checkAll ();
      }
      catch (final Throwable t)
      {
        aError.set (t);
      }
    }, "small-stack", 256 * 1024);
    aThread.start ();
    aThread.join ();
    if (aError.get () != null)
      aError.get ().printStackTrace ();
    assertNull (aError.get ());
  }
}
