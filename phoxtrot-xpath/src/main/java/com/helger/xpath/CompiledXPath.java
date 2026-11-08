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
package com.helger.xpath;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.value.XdmSequence;

/**
 * A compiled XPath expression. Immutable and usable from several threads at
 * once, each with its own {@link DynamicContext}.
 *
 * @author Philip Helger
 */
@Immutable
public final class CompiledXPath
{
  private final String m_sSource;
  private final StaticContext m_aStaticCtx;
  private final IExpr m_aExpr;

  CompiledXPath (@NonNull final String sSource, @NonNull final StaticContext aStaticCtx, @NonNull final IExpr aExpr)
  {
    m_sSource = sSource;
    m_aStaticCtx = aStaticCtx;
    m_aExpr = aExpr;
  }

  @NonNull
  public String getSource ()
  {
    return m_sSource;
  }

  @NonNull
  public StaticContext getStaticContext ()
  {
    return m_aStaticCtx;
  }

  @NonNull
  public IExpr getExpression ()
  {
    return m_aExpr;
  }

  /** @return A fresh root dynamic context for this expression. */
  @NonNull
  public DynamicContext createDynamicContext ()
  {
    return new DynamicContext (m_aStaticCtx);
  }

  /**
   * Evaluate the expression. It always sees the static context it was compiled
   * with (decimal formats, default collation, base URI, namespaces for
   * lexical QNames in function arguments), also if the dynamic context was
   * created from another static context, e.g. by a host that evaluates
   * expressions of several origins against one document.
   *
   * @param aCtx
   *        The dynamic context with focus, variables and the run-wide state.
   * @return The result.
   */
  @NonNull
  public XdmSequence evaluate (@NonNull final DynamicContext aCtx)
  {
    final DynamicContext aRealCtx = aCtx.getStaticContext () == m_aStaticCtx ? aCtx : aCtx.withStaticContext (m_aStaticCtx);
    return XPathEvaluator.evaluate (m_aExpr, aRealCtx);
  }

  /**
   * The effective boolean value of the expression, with the static context as
   * in {@link #evaluate(DynamicContext)}. Paths and filter expressions stop at
   * the first decisive item
   * ({@link XPathEvaluator#evaluateEffectiveBooleanValue(IExpr, DynamicContext)}).
   *
   * @param aCtx
   *        The dynamic context with focus, variables and the run-wide state.
   * @return The effective boolean value.
   */
  public boolean evaluateEffectiveBooleanValue (@NonNull final DynamicContext aCtx)
  {
    final DynamicContext aRealCtx = aCtx.getStaticContext () == m_aStaticCtx ? aCtx : aCtx.withStaticContext (m_aStaticCtx);
    return XPathEvaluator.evaluateEffectiveBooleanValue (m_aExpr, aRealCtx);
  }
}
