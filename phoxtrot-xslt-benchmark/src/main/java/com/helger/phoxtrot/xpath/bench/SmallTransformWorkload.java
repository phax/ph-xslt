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

import java.lang.reflect.Method;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTransformRequest;

/**
 * Many small transformations, as a Schematron validation of small documents
 * in a server does: the <code>identity</code> stylesheet over a document of
 * three invoice lines (about 1 KB). Each run is one transformation, so the
 * fixed cost per call - the thread the transformation runs on - weighs as
 * much as the transformation itself. The case is the threading mode of the
 * request (<code>XsltTransformRequest.setThreading</code>):
 * <code>new-thread</code> (the default), <code>pool</code> or
 * <code>calling</code>. It is set by reflection, so that the benchmark also
 * runs against an engine without the option, which then always uses its
 * default.
 *
 * @author Philip Helger
 */
public final class SmallTransformWorkload implements IWorkload
{
  /** The number of invoice lines of the document (about 1 KB). */
  public static final int LINES = 3;

  private final BenchData m_aData;
  private final IXdmNode m_aDoc;
  private final XsltExecutable m_aExecutable;
  // XsltTransformRequest.setThreading and its argument; null for the default
  private final Method m_aSetThreading;
  private final Object m_aThreading;

  /**
   * @param sCase
   *        <code>new-thread</code>, <code>pool</code> or
   *        <code>calling</code>.
   * @throws Exception
   *         if the stylesheet does not compile
   */
  public SmallTransformWorkload (@NonNull final String sCase) throws Exception
  {
    m_aData = BenchData.create (LINES, true);
    m_aDoc = BenchData.parse (m_aData.getBytes (), "urn:bench:invoice.xml");
    m_aExecutable = XsltCompiler.compile (BenchResources.getText (EXsltCase.IDENTITY.getResourceName ()),
                                          new XsltCompileOptions ());
    final String sMode = switch (sCase)
    {
      case "new-thread" -> "NEW_THREAD";
      case "pool" -> "THREAD_POOL";
      case "calling" -> "CALLING_THREAD";
      default -> throw new IllegalArgumentException ("Unknown small transformation case '" + sCase + "'");
    };
    final Class <?> aEnum = _findClass ("com.helger.phoxtrot.xpath.EThreading");
    if (aEnum == null)
    {
      m_aSetThreading = null;
      m_aThreading = null;
    }
    else
    {
      m_aSetThreading = XsltTransformRequest.class.getMethod ("setThreading", aEnum);
      m_aThreading = aEnum.getField (sMode).get (null);
    }
  }

  /**
   * @return The class, or <code>null</code> if the engine does not have it.
   */
  @Nullable
  private static Class <?> _findClass (@NonNull final String sName)
  {
    try
    {
      return Class.forName (sName);
    }
    catch (final ClassNotFoundException ex)
    {
      return null;
    }
  }

  @NonNull
  public Object run () throws Exception
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (m_aDoc);
    if (m_aSetThreading != null)
      m_aSetThreading.invoke (aRequest, m_aThreading);
    return m_aExecutable.transform (aRequest).principalSerialized ();
  }

  public void verify (@NonNull final Object aResult)
  {
    EXsltCase.IDENTITY.verify (m_aData, (String) aResult);
  }
}
