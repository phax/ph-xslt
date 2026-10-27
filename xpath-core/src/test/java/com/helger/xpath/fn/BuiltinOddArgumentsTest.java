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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.BuiltInFunctionItem;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;

/**
 * No built-in function may fail with anything but an {@link XPathException} on an argument of any shape, whether the
 * function conversion rules accept it or not, and under XPath 1.0 compatibility, where the strict check is skipped
 * and the implementation sees the more lenient conversion result.
 *
 * @author Philip Helger
 */
public final class BuiltinOddArgumentsTest
{
  private static final String [] CANDIDATES = { "()",
                                                 "'a'",
                                                 "1",
                                                 "1.5e0",
                                                 "xs:double('NaN')",
                                                 "true()",
                                                 "xs:untypedAtomic('2')",
                                                 "QName('urn:x', 'q')",
                                                 ".",
                                                 "/r/c",
                                                 "(1, 2)",
                                                 "('a', 'b')",
                                                 "map{1: 2}",
                                                 "[1]",
                                                 "abs#1" };

  @Test
  public void testNoFailureButXPathException ()
  {
    // Would read files, wait or print
    final ICommonsSet <String> aSkip = new CommonsHashSet <> ("doc",
                                                               "doc-available",
                                                               "collection",
                                                               "uri-collection",
                                                               "unparsed-text",
                                                               "unparsed-text-lines",
                                                               "unparsed-text-available",
                                                               "json-doc",
                                                               "transform",
                                                               "trace");
    final XdmNode aDoc = FnNodeTestHelper.parse ("<r a='1'><c>x</c><!--k--></r>");
    final ICommonsList <String> aProblems = new CommonsArrayList <> ();
    int nCalls = 0;
    for (final boolean bCompat : new boolean [] { false, true })
    {
      final StaticContext aStatic = new StaticContext ();
      aStatic.setXPath10Compatibility (bCompat);
      final CompiledXPath aXP = XPathCompiler.compile ("1", aStatic);
      final DynamicContext aCtx = aXP.createDynamicContext ().withFocus (aDoc, 1, 1);
      final ICommonsList <XdmSequence> aValues = new CommonsArrayList <> ();
      for (final String sCandidate : CANDIDATES)
        aValues.add (XPathCompiler.compile (sCandidate, aStatic).evaluate (aCtx));
      for (final FunctionDef aDef : FunctionLibrary.getDefault ().getAllFunctions ())
      {
        if (aSkip.contains (aDef.getName ().localName ()))
          continue;
        for (int i = 0; i < CANDIDATES.length; ++i)
        {
          final List <XdmSequence> aArgs = new CommonsArrayList <> ();
          for (int j = 0; j < aDef.getArity (); ++j)
            aArgs.add (aValues.get (i));
          ++nCalls;
          try
          {
            XPathEvaluator.callFunction (aCtx, new BuiltInFunctionItem (aDef, aCtx), aArgs);
          }
          catch (final XPathException ex)
          {
            // Fine
          }
          catch (final RuntimeException | StackOverflowError ex)
          {
            aProblems.add (aDef.getName ().localName () +
                           "#" +
                           aDef.getArity () +
                           " (compat=" +
                           bCompat +
                           ") on " +
                           CANDIDATES[i] +
                           ": " +
                           ex);
          }
        }
      }
    }
    assertTrue (nCalls > 4000);
    if (!aProblems.isEmpty ())
      fail (aProblems.size () + " failures:\n" + String.join ("\n", aProblems));
  }
}
