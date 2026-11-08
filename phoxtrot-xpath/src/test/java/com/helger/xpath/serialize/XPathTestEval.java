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
package com.helger.xpath.serialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;

/**
 * Evaluates XPath expressions for the tests of the map, array, JSON and
 * serialisation functions.
 *
 * @author Philip Helger
 */
public final class XPathTestEval
{
  @PresentForCodeCoverage
  private static final XPathTestEval INSTANCE = new XPathTestEval ();

  private XPathTestEval ()
  {}

  @NonNull
  public static XdmSequence eval (@NonNull final String sExpr)
  {
    final CompiledXPath aXPath = XPathCompiler.compile (sExpr, new StaticContext ());
    return aXPath.evaluate (aXPath.createDynamicContext ());
  }

  /**
   * @param sExpr
   *        Expression
   * @return The lexical forms of the atomic result items, separated by a
   *         space.
   */
  @NonNull
  public static String evalString (@NonNull final String sExpr)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : eval (sExpr))
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (((XdmAtomic) aItem).getLexical ());
    }
    return aSB.toString ();
  }

  /**
   * @param sExpr
   *        Expression
   * @return The result serialised with the adaptive method.
   */
  @NonNull
  public static String adaptive (@NonNull final String sExpr)
  {
    return evalString ("serialize(" + sExpr + ", map{'method':'adaptive','item-separator':' '})");
  }

  public static void assertError (@NonNull final String sCode, @NonNull final String sExpr)
  {
    try
    {
      eval (sExpr);
      fail ("Expected error " + sCode + " for " + sExpr);
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr + ": " + ex.getMessage (), sCode, ex.getCode ());
    }
  }
}
