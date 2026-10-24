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
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.parser.XPathParser;

/**
 * Entry point: compile an XPath 3.1 expression.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathCompiler
{
  @PresentForCodeCoverage
  private static final XPathCompiler INSTANCE = new XPathCompiler ();

  private XPathCompiler ()
  {}

  /**
   * @param sExpr
   *        Expression text.
   * @param aStaticCtx
   *        Static context; must not be changed afterwards.
   * @return The compiled expression.
   * @throws XPathException
   *         for static errors.
   */
  @NonNull
  public static CompiledXPath compile (@NonNull final String sExpr, @NonNull final StaticContext aStaticCtx)
  {
    ValueEnforcer.notNull (sExpr, "Expr");
    ValueEnforcer.notNull (aStaticCtx, "StaticCtx");
    return new CompiledXPath (sExpr, aStaticCtx, XPathParser.parse (sExpr, aStaticCtx));
  }
}
