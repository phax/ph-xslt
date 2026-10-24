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
package com.helger.xslt;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmNode;

/**
 * Entry point: compile an XSLT 3.0 stylesheet (or package). Owner: XSLT core
 * workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltCompiler
{
  @PresentForCodeCoverage
  private static final XsltCompiler INSTANCE = new XsltCompiler ();

  private XsltCompiler ()
  {}

  /**
   * @param sStylesheet
   *        The stylesheet text.
   * @param aOptions
   *        Compile options.
   * @return The compiled stylesheet.
   * @throws XsltException
   *         for static errors.
   */
  @NonNull
  public static XsltExecutable compile (@NonNull final String sStylesheet, @NonNull final XsltCompileOptions aOptions)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * Compile from an already parsed stylesheet document (e.g. an embedded
   * stylesheet element or a stylesheet built in memory).
   *
   * @param aStylesheet
   *        Document or element node of the stylesheet.
   * @param aOptions
   *        Compile options.
   * @return The compiled stylesheet.
   */
  @NonNull
  public static XsltExecutable compile (@NonNull final XdmNode aStylesheet, @NonNull final XsltCompileOptions aOptions)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
