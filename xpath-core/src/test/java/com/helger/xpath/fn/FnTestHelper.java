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
package com.helger.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * Helper for testing functions through complete XPath expressions.
 *
 * @author Philip Helger
 */
final class FnTestHelper
{
  @PresentForCodeCoverage
  private static final FnTestHelper INSTANCE = new FnTestHelper ();

  private FnTestHelper ()
  {}

  @NonNull
  static XdmSequence eval (@NonNull final String sExpr, @Nullable final IXdmItem aContextItem)
  {
    final CompiledXPath aXP = XPathCompiler.compile (sExpr, new StaticContext ());
    DynamicContext aCtx = aXP.createDynamicContext ();
    if (aContextItem != null)
      aCtx = aCtx.withFocus (aContextItem, 1, 1);
    return aXP.evaluate (aCtx);
  }

  @NonNull
  static XdmSequence eval (@NonNull final String sExpr)
  {
    return eval (sExpr, null);
  }

  /**
   * @return The items separated by blanks: atomic values as
   *         "type:lexical", nodes by their local name.
   */
  @NonNull
  static String typed (@NonNull final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      if (aItem instanceof final XdmAtomic aAtomic)
        aSB.append (aAtomic.getType ().getLocalName ()).append (':').append (aAtomic.getLexical ());
      else
        if (aItem instanceof final XdmNode aNode)
          aSB.append (aNode.getName () == null ? aNode.getKind ().toString () : aNode.getName ().localName ());
        else
          aSB.append (aItem.toString ());
    }
    return aSB.toString ();
  }

  /** @return The lexical forms of the atomic results separated by blanks. */
  @NonNull
  static String lex (@NonNull final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      if (aItem instanceof final XdmAtomic aAtomic)
        aSB.append (aAtomic.getLexical ());
      else
        if (aItem instanceof final XdmNode aNode && aNode.getName () != null)
          aSB.append (aNode.getName ().localName ());
        else
          aSB.append (aItem.toString ());
    }
    return aSB.toString ();
  }

  static void assertTyped (@NonNull final String sExpected, @NonNull final String sExpr)
  {
    assertEquals (sExpr, sExpected, typed (eval (sExpr)));
  }

  static void assertLex (@NonNull final String sExpected, @NonNull final String sExpr)
  {
    assertEquals (sExpr, sExpected, lex (eval (sExpr)));
  }

  @NonNull
  static XPathException assertXPathError (@NonNull final String sCode,
                                          @NonNull final String sExpr,
                                          @Nullable final IXdmItem aContextItem)
  {
    try
    {
      final XdmSequence aResult = eval (sExpr, aContextItem);
      fail (sExpr + ": expected error " + sCode + " but got " + typed (aResult));
      throw new IllegalStateException ();
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr + ": " + ex.getMessage (), sCode, ex.getCode ());
      return ex;
    }
  }

  @NonNull
  static XPathException assertXPathError (@NonNull final String sCode, @NonNull final String sExpr)
  {
    return assertXPathError (sCode, sExpr, null);
  }
}
