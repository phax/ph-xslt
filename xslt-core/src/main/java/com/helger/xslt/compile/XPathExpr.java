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
package com.helger.xslt.compile;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * An XPath expression written in a stylesheet, compiled with the static
 * context of its element (namespaces, xpath-default-namespace, variables in
 * scope, ...). Immutable.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathExpr
{
  private final String m_sSource;
  private final IExpr m_aExpr;
  private final StaticContext m_aStaticContext;
  private final XslElement m_aElement;

  public XPathExpr (@NonNull final String sSource,
                    @NonNull final IExpr aExpr,
                    @NonNull final StaticContext aStaticContext,
                    @NonNull final XslElement aElement)
  {
    m_sSource = sSource;
    m_aExpr = aExpr;
    m_aStaticContext = aStaticContext;
    m_aElement = aElement;
  }

  @NonNull
  public String getSource ()
  {
    return m_sSource;
  }

  @NonNull
  public IExpr getExpression ()
  {
    return m_aExpr;
  }

  @NonNull
  public StaticContext getStaticContext ()
  {
    return m_aStaticContext;
  }

  @NonNull
  public XslElement getElement ()
  {
    return m_aElement;
  }

  /**
   * @param aCtx
   *        Run-time context.
   * @return The XPath dynamic context for this expression.
   */
  @NonNull
  public DynamicContext createDynamicContext (@NonNull final XsltContext aCtx)
  {
    return aCtx.getDynamicContext ().withStaticContext (m_aStaticContext);
  }

  /**
   * @param aCtx
   *        Run-time context.
   * @return The value.
   */
  @NonNull
  public XdmSequence evaluate (@NonNull final XsltContext aCtx)
  {
    return XPathEvaluator.evaluate (m_aExpr, aCtx.getDynamicContext ().withStaticContext (m_aStaticContext));
  }

  /**
   * @param aCtx
   *        Run-time context.
   * @return The effective boolean value.
   */
  public boolean evaluateBoolean (@NonNull final XsltContext aCtx)
  {
    return Atomizer.getEffectiveBooleanValue (evaluate (aCtx));
  }

  /**
   * @param aCtx
   *        Run-time context.
   * @param sSeparator
   *        Separator between the string values of the atomized items.
   * @return The joined string values of the atomized result.
   */
  @NonNull
  public String evaluateString (@NonNull final XsltContext aCtx, @NonNull final String sSeparator)
  {
    final XdmSequence aAtomized = Atomizer.atomize (evaluate (aCtx));
    if (aAtomized.isEmpty ())
      return "";
    if (aAtomized.isSingleton ())
      return Atomizer.getStringValue (aAtomized.get (0));
    final StringBuilder aSB = new StringBuilder ();
    boolean bFirst = true;
    for (final IXdmItem aItem : aAtomized)
    {
      if (!bFirst)
        aSB.append (sSeparator);
      aSB.append (Atomizer.getStringValue (aItem));
      bFirst = false;
    }
    return aSB.toString ();
  }

  @Override
  public String toString ()
  {
    return m_sSource;
  }
}
