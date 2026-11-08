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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;

/**
 * The compile-only cost, without any input document. <code>xslt</code> compiles all stylesheets of
 * the XSLT and streaming workloads (12 stylesheets, about 200 lines together without the licence
 * headers, with patterns, functions, keys, grouping, iterate and a streamable construct);
 * <code>xpath</code> compiles all expressions of the XPath workloads and the rule set (21
 * expressions). Measured: parsing the stylesheet text or expression and compiling it; each run
 * compiles the whole set.
 *
 * @author Philip Helger
 */
public final class CompileWorkload implements IWorkload
{
  private final boolean m_bXslt;
  private final ICommonsList <String> m_aSources = new CommonsArrayList <> ();

  /**
   * @param sKind
   *        <code>xslt</code> or <code>xpath</code>.
   */
  public CompileWorkload (@NonNull final String sKind)
  {
    m_bXslt = "xslt".equals (sKind);
    if (m_bXslt)
    {
      for (final EXsltCase eCase : EXsltCase.values ())
        m_aSources.add (BenchResources.getText (eCase.getResourceName ()));
      m_aSources.add (BenchResources.getText ("xsl/streaming.xsl"));
    }
    else
      if ("xpath".equals (sKind))
      {
        for (final EXPathCase eCase : EXPathCase.values ())
          m_aSources.add (eCase.getExpression ());
        m_aSources.add (XPathRulesWorkload.CONTEXT);
        m_aSources.addAll (XPathRulesWorkload.RULES);
      }
      else
        throw new IllegalArgumentException ("Unknown compile kind '" + sKind + "'");
  }

  @NonNull
  public Object run ()
  {
    final ICommonsList <Object> ret = new CommonsArrayList <> (m_aSources.size ());
    if (m_bXslt)
    {
      for (final String sStylesheet : m_aSources)
        ret.add (XsltCompiler.compile (sStylesheet, new XsltCompileOptions ()));
    }
    else
    {
      final StaticContext aSC = new StaticContext ();
      for (final String sExpression : m_aSources)
        ret.add (XPathCompiler.compile (sExpression, aSC));
    }
    return ret;
  }

  public void verify (@NonNull final Object aResult)
  {
    final ICommonsList <?> aCompiled = (ICommonsList <?>) aResult;
    BenchAssert.assertEquals ("compiled", Integer.valueOf (m_aSources.size ()), Integer.valueOf (aCompiled.size ()));
    BenchAssert.assertEquals ("sources", Integer.valueOf (m_bXslt ? 12 : 21), Integer.valueOf (m_aSources.size ()));
    for (final Object aItem : aCompiled)
      BenchAssert.assertTrue ("compiled item", aItem != null);
  }
}
