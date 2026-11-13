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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FnAccessor;

/**
 * Test of the name predicates of {@link XPathEvaluator} (<code>[name() = name($c)]</code>): they
 * give the same results and errors as the generic evaluation.
 *
 * @author Philip Helger
 */
public final class NamePredicateTest
{
  private static final String DOC = "<r xmlns:p='urn:p' xmlns:q='urn:p'><p:a/><a/><q:a/><b x='urn:p'/>" +
                                     "<a x='a'/><p:b/>t<!--c--><?a pi?><q:a/><a/></r>";

  private static final String [] PREDICATES = { "name() = name($c)",
                                                "local-name() = local-name($c)",
                                                "namespace-uri() = namespace-uri($c)",
                                                "local-name() = local-name($c) and namespace-uri() = namespace-uri($c)",
                                                "name() eq name($c)",
                                                "name() eq 'a'",
                                                "'a' = local-name()",
                                                "local-name() = ('a', 'b')",
                                                "local-name() eq ('a', 'b')",
                                                "local-name() eq ()",
                                                "local-name() = ()",
                                                "name() = 1",
                                                "namespace-uri() = $c/@x",
                                                "local-name() = $c/@x",
                                                "local-name() = $c",
                                                "namespace-uri() = 'urn:p' and name() = 'q:a'",
                                                "name() = 'a' and local-name() = 'b'" };

  private static final String [] TEMPLATES = { "count($c/preceding-sibling::node()#)",
                                               "count($c/preceding-sibling::*#)",
                                               "exists($c/following-sibling::node()#)",
                                               "string-join(/r/node()#!path(), ',')",
                                               "string-join(/r/node()#[2]!path(), ',')",
                                               "count((/r/node(), 1)#)",
                                               "count(/r/@*#)" };

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
  private static String _evalOrError (@NonNull final String sExpr, @NonNull final IXdmNode aDoc)
  {
    try
    {
      final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
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
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
  }

  @Test
  public void testEquivalence ()
  {
    final IXdmNode aDoc = _parse (DOC);
    for (final String sPredicate : PREDICATES)
    {
      // The reference has another shape, so it takes the generic evaluation
      final String sReference = "[if (exists(.)) then (" + sPredicate + ") else ()]";
      final String sDirect = "[" + sPredicate + "]";
      for (final String sTemplate : TEMPLATES)
      {
        final String sFor = "for $c in /r/node() return ";
        final String sExpected = _evalOrError (sFor + sTemplate.replace ("#", sReference), aDoc);
        final String sActual = _evalOrError (sFor + sTemplate.replace ("#", sDirect), aDoc);
        assertEquals (sTemplate + " with " + sPredicate, sExpected, sActual);
      }
    }
  }

  @Test
  public void testResults ()
  {
    final IXdmNode aDoc = _parse (DOC);
    // Same name: p:a and q:a differ in name() but not in their expanded name
    assertEquals ("0 0 0 0 1 0 1 2",
                  _evalOrError ("for $c in /r/* return count($c/preceding-sibling::*[name() = name($c)])", aDoc));
    assertEquals ("0 0 1 0 1 0 2 2",
                  _evalOrError ("for $c in /r/* return count($c/preceding-sibling::*[local-name() = local-name($c) and " +
                                "namespace-uri() = namespace-uri($c)])",
                                aDoc));
    assertEquals ("error XPTY0004", _evalOrError ("(/r/a, 1)[name() = 'a']", aDoc));
    assertEquals ("error XPTY0004", _evalOrError ("/r/*[local-name() eq ('a', 'b')]", aDoc));
  }
}
