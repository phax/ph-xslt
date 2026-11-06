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

import com.helger.xpath.xdm.IXdmNode;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;

/**
 * Transforms the parsed document with the stylesheet of an {@link EXsltCase}, with the document
 * node as global context item and initial match selection. Measured: one transformation including
 * the serialisation of the principal result to a string; not measured: generating and parsing the
 * document and compiling the stylesheet. The document is parsed once, so the name indexes it
 * builds on first use are reused by later runs (the key indexes are per transformation).
 *
 * @author Philip Helger
 */
public final class XsltWorkload implements IWorkload
{
  private final EXsltCase m_eCase;
  private final BenchData m_aData;
  private final IXdmNode m_aDoc;
  private final XsltExecutable m_aExecutable;

  /**
   * @param eCase
   *        The stylesheet to run.
   * @param nLines
   *        The number of invoice lines of the document.
   * @throws Exception
   *         if parsing or compiling fails
   */
  public XsltWorkload (@NonNull final EXsltCase eCase, final int nLines) throws Exception
  {
    m_eCase = eCase;
    m_aData = BenchData.create (nLines, true);
    m_aDoc = BenchData.parse (m_aData.getBytes (), "urn:bench:invoice.xml");
    m_aExecutable = XsltCompiler.compile (BenchResources.getText (eCase.getResourceName ()), new XsltCompileOptions ());
  }

  @NonNull
  public Object run ()
  {
    // No instruction limit: the default limit would stop large sizes
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (m_aDoc)
                                                                     .setMaxSteps (Long.MAX_VALUE);
    return m_aExecutable.transform (aRequest).principalSerialized ();
  }

  public void verify (@NonNull final Object aResult)
  {
    m_eCase.verify (m_aData, (String) aResult);
  }
}
