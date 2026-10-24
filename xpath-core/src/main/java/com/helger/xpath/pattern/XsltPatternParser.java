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
package com.helger.xpath.pattern;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.context.StaticContext;

/**
 * Parser for XSLT 3.0 patterns (a port of go-xslt's
 * <code>internal/xpath/pattern.go</code> and <code>pattern_calls.go</code>).
 * Owner: pattern workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltPatternParser
{
  @PresentForCodeCoverage
  private static final XsltPatternParser INSTANCE = new XsltPatternParser ();

  private XsltPatternParser ()
  {}

  /**
   * @param sPattern
   *        The pattern text.
   * @param aStaticCtx
   *        Static context (namespaces, default element namespace, variables).
   * @return The compiled pattern.
   * @throws com.helger.xpath.XPathException
   *         XTSE0340 for an invalid pattern, other static errors as the
   *         expression parser raises them.
   */
  @NonNull
  public static XsltPattern parse (@NonNull final String sPattern, @NonNull final StaticContext aStaticCtx)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
