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
package com.helger.phoxtrot.xpath.bench;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Evaluates one compiled expression of {@link EXPathCase} with the parsed document node as context
 * item. Measured: the evaluation, including the creation of the dynamic context; not measured: the
 * generation, parsing and compilation. The document is parsed once, so the name indexes it builds
 * on first use are reused by later runs.
 *
 * @author Philip Helger
 */
public final class XPathWorkload implements IWorkload
{
  private final EXPathCase m_eCase;
  private final BenchData m_aData;
  private final IXdmNode m_aDoc;
  private final CompiledXPath m_aXPath;

  /**
   * @param eCase
   *        The expression to evaluate.
   * @param nLines
   *        The number of invoice lines of the document.
   * @throws Exception
   *         if parsing fails
   */
  public XPathWorkload (@NonNull final EXPathCase eCase, final int nLines) throws Exception
  {
    m_eCase = eCase;
    m_aData = BenchData.create (nLines, true);
    m_aDoc = BenchData.parse (m_aData.getBytes (), "urn:bench:invoice.xml");
    m_aXPath = XPathCompiler.compile (eCase.getExpression (), new StaticContext ());
  }

  @NonNull
  public Object run ()
  {
    return m_aXPath.evaluate (m_aXPath.createDynamicContext ().withFocus (m_aDoc, 1, 1));
  }

  public void verify (@NonNull final Object aResult)
  {
    final XdmSequence aSeq = (XdmSequence) aResult;
    BenchAssert.assertEquals ("Result size of xpath " + m_eCase.getID (), Integer.valueOf (1), Integer.valueOf (aSeq.size ()));
    BenchAssert.assertEquals ("Result of xpath " + m_eCase.getID (),
                              m_eCase.getExpected (m_aData),
                              Atomizer.getStringValue (aSeq.getFirst ()));
  }
}
