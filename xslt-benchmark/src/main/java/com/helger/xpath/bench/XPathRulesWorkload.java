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
package com.helger.xpath.bench;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;

/**
 * A rule set in the style of a Schematron rule: the lines are selected once, then ten assertions are
 * evaluated for their effective boolean value with each line as context item, as a Schematron
 * executor does. Measured: selecting the lines and evaluating all assertions; the result is the
 * number of failures per assertion.
 *
 * @author Philip Helger
 */
public final class XPathRulesWorkload implements IWorkload
{
  /** The rule context. */
  public static final String CONTEXT = "/invoice/lines/line";

  /** The assertions, evaluated with a line as context item. */
  public static final String [] RULES = { "exists(item) and string-length(normalize-space(item)) gt 0",
                                          "matches(sku, '^[A-Z]{3}-[0-9]{4}$')",
                                          "qty castable as xs:integer and xs:integer(qty) ge 1",
                                          "xs:decimal(price) gt 0 and xs:decimal(price) * xs:integer(qty) lt 2000",
                                          "tax/@rate = ('0', '7', '19')",
                                          "@cat != 'C' or tax/@rate = '19'",
                                          "xs:date(period/start) le xs:date(period/end)",
                                          "not(tax/@rate = '0') or exists(tax/@reason)",
                                          "../../@currency = 'EUR'",
                                          "string-length(note) le 40" };

  private final BenchData m_aData;
  private final IXdmNode m_aDoc;
  private final CompiledXPath m_aContext;
  private final CompiledXPath [] m_aRules;

  /**
   * @param nLines
   *        The number of invoice lines of the document.
   * @throws Exception
   *         if parsing fails
   */
  public XPathRulesWorkload (final int nLines) throws Exception
  {
    m_aData = BenchData.create (nLines, true);
    m_aDoc = BenchData.parse (m_aData.getBytes (), "urn:bench:invoice.xml");
    // One static context for all expressions, as a Schematron schema has
    final StaticContext aSC = new StaticContext ();
    m_aContext = XPathCompiler.compile (CONTEXT, aSC);
    m_aRules = new CompiledXPath [RULES.length];
    for (int i = 0; i < RULES.length; ++i)
      m_aRules[i] = XPathCompiler.compile (RULES[i], aSC);
  }

  @NonNull
  public Object run ()
  {
    final DynamicContext aRootCtx = m_aContext.createDynamicContext ().withFocus (m_aDoc, 1, 1);
    final XdmSequence aLines = m_aContext.evaluate (aRootCtx);
    final int nSize = aLines.size ();
    final int [] ret = new int [m_aRules.length];
    for (int i = 0; i < nSize; ++i)
    {
      final DynamicContext aCtx = aRootCtx.withFocus (aLines.get (i), i + 1, nSize);
      for (int j = 0; j < m_aRules.length; ++j)
        if (!m_aRules[j].evaluateEffectiveBooleanValue (aCtx))
          ret[j]++;
    }
    return ret;
  }

  public void verify (@NonNull final Object aResult)
  {
    final int [] aFails = (int []) aResult;
    final StringBuilder aSB = new StringBuilder ();
    for (final int nFails : aFails)
    {
      if (aSB.length () > 0)
        aSB.append (',');
      aSB.append (nFails);
    }
    BenchAssert.assertEquals ("Failures per rule", BenchExpected.xpathRules (m_aData), aSB.toString ());
  }
}
