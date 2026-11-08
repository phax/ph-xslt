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
package com.helger.xslt.conformance;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.xdm.IXdmNode;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;

/**
 * Compiles a stylesheet for the case runner. The real implementation is
 * {@link #ENGINE} ({@link XsltCompiler}); unit tests plug in a fake.
 *
 * @author Philip Helger
 */
public interface IXSLT30Compiler
{
  /** The engine under test. */
  IXSLT30Compiler ENGINE = new IXSLT30Compiler ()
  {
    @NonNull
    public XsltExecutable compile (@NonNull final String sStylesheet, @NonNull final XsltCompileOptions aOptions)
    {
      return XsltCompiler.compile (sStylesheet, aOptions);
    }

    @NonNull
    public XsltExecutable compile (@NonNull final IXdmNode aStylesheet, @NonNull final XsltCompileOptions aOptions)
    {
      return XsltCompiler.compile (aStylesheet, aOptions);
    }
  };

  /**
   * @param sStylesheet
   *        The stylesheet text.
   * @param aOptions
   *        Compile options.
   * @return The compiled stylesheet.
   */
  @NonNull
  XsltExecutable compile (@NonNull String sStylesheet, @NonNull XsltCompileOptions aOptions);

  /**
   * @param aStylesheet
   *        The stylesheet element (embedded in a source document).
   * @param aOptions
   *        Compile options.
   * @return The compiled stylesheet.
   */
  @NonNull
  XsltExecutable compile (@NonNull IXdmNode aStylesheet, @NonNull XsltCompileOptions aOptions);
}
