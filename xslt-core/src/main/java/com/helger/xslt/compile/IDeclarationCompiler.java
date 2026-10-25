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

import com.helger.xslt.tree.XslElement;

/**
 * Compiles one top-level declaration element (a child of xsl:stylesheet). It
 * stores what it compiles in the stylesheet, typically via
 * {@link com.helger.xslt.model.CompiledStylesheet#putExtensionData(Object, Object)}.
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IDeclarationCompiler
{
  /**
   * @param aCtx
   *        Compile context (global scope).
   * @param aElement
   *        The declaration element; {@link XslElement#getPrecedence()} gives
   *        its import precedence.
   * @throws com.helger.xslt.XsltException
   *         for static errors.
   */
  void compile (@NonNull CompileContext aCtx, @NonNull XslElement aElement);
}
