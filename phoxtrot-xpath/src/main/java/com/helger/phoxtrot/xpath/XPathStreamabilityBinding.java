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
package com.helger.phoxtrot.xpath;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.ast.IExpr;

/**
 * The binding expression of a variable, compiled once by <code>XPathStreamabilityAnalyzer.analyzeBinding</code> and
 * declared with <code>XPathStreamabilityAnalyzer.addBoundVariable</code>, e.g. a <code>sch:let</code> shared by the
 * assertions of a Schematron rule. It also carries the result of the binding expression analysed on its own.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathStreamabilityBinding
{
  private final IExpr m_aExpr;
  private final XPathStreamabilityResult m_aResult;

  XPathStreamabilityBinding (@NonNull final IExpr aExpr, @NonNull final XPathStreamabilityResult aResult)
  {
    m_aExpr = aExpr;
    m_aResult = aResult;
  }

  /**
   * @return The compiled binding expression. Never <code>null</code>.
   */
  @NonNull
  IExpr getExpression ()
  {
    return m_aExpr;
  }

  /**
   * @return The result of the binding expression analysed on its own, with the configuration of the analyser at the
   *         time it was analysed (within the variables bound before). Never <code>null</code>.
   */
  @NonNull
  public XPathStreamabilityResult getResult ()
  {
    return m_aResult;
  }

  @Override
  public String toString ()
  {
    return "binding: " + m_aResult;
  }
}
