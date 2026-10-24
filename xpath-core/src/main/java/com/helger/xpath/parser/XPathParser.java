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
package com.helger.xpath.parser;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;

/**
 * Recursive-descent parser for XPath 3.1 (a port of go-xslt's
 * <code>internal/xpath/parser.go</code> and <code>lexer.go</code>). All QNames
 * are resolved against the static context at parse time; unbound prefixes are
 * XPST0081, syntax errors XPST0003. Nesting depth is limited so that hostile
 * input cannot overflow the Java stack (XPDY0130 as go-xslt does); a parse that
 * still overflows a small caller stack is repeated once on a helper thread
 * with a large stack, so the result does not depend on the caller's stack. Further
 * static checks done here: XPST0017 (unknown function name/arity), XPST0008
 * (undeclared variable, unknown type in a kind test, schema-element and
 * schema-attribute tests without a schema), XPST0051 (unknown atomic type in
 * a SequenceType), XPST0080 and XQST0052 (invalid cast targets), XQST0039
 * (duplicate inline function parameter) and XQST0070 (the xmlns namespace in
 * a name test).
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathParser
{
  /** Result of {@link #parseFunctionSignature(String, StaticContext)}. */
  public record FunctionSignature (@NonNull List <SeqType> paramTypes, @NonNull SeqType returnType)
  {}

  @PresentForCodeCoverage
  private static final XPathParser INSTANCE = new XPathParser ();

  private XPathParser ()
  {}

  /**
   * @param sExpr
   *        The expression text.
   * @param aStaticCtx
   *        The static context for name resolution.
   * @return The AST.
   * @throws com.helger.xpath.XPathException
   *         for static errors.
   */
  @NonNull
  public static IExpr parse (@NonNull final String sExpr, @NonNull final StaticContext aStaticCtx)
  {
    ValueEnforcer.notNull (sExpr, "Expr");
    ValueEnforcer.notNull (aStaticCtx, "StaticCtx");
    return XPathParserImpl.runWithStackGuard ( () -> new XPathParserImpl (sExpr, aStaticCtx).parseExpression (), sExpr);
  }

  /**
   * Parse a SequenceType on its own, e.g. <code>xs:string?</code> or
   * <code>element(a)*</code>.
   */
  @NonNull
  public static SeqType parseSequenceType (@NonNull final String sType, @NonNull final StaticContext aStaticCtx)
  {
    ValueEnforcer.notNull (sType, "Type");
    ValueEnforcer.notNull (aStaticCtx, "StaticCtx");
    return XPathParserImpl.runWithStackGuard ( () -> new XPathParserImpl (sType, aStaticCtx).parseSequenceTypeOnly (),
                                               sType);
  }

  /**
   * Parse a function signature in F&amp;O notation without parameter names:
   * <code>(xs:string?, item()*) as xs:boolean</code>.
   */
  @NonNull
  public static FunctionSignature parseFunctionSignature (@NonNull final String sSignature,
                                                          @NonNull final StaticContext aStaticCtx)
  {
    ValueEnforcer.notNull (sSignature, "Signature");
    ValueEnforcer.notNull (aStaticCtx, "StaticCtx");
    return XPathParserImpl.runWithStackGuard ( () -> new XPathParserImpl (sSignature, aStaticCtx).parseFunctionSignature (),
                                               sSignature);
  }
}
