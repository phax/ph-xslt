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
package com.helger.xslt.ext.transform;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.model.CompiledStylesheet;

/**
 * The XSLT side of fn:transform: the function itself is registered by
 * xpath-core when it finds {@link XsltTransformHandler} through the service
 * loader; this extension remembers the compile options of every stylesheet,
 * so that a transformation started from it compiles with the same resolver and
 * finds the same library packages (option <code>package-name</code>).
 *
 * @author Philip Helger
 */
public final class TransformExtension implements IXsltExtension
{
  private static final Object KEY_OPTIONS = new Object ();

  /**
   * @param aSheet
   *        A compiled stylesheet.
   * @return The options it was compiled with, or <code>null</code> if unknown.
   */
  @Nullable
  static XsltCompileOptions getCompileOptions (@NonNull final CompiledStylesheet aSheet)
  {
    return aSheet.getExtensionData (KEY_OPTIONS) instanceof final XsltCompileOptions aOptions ? aOptions : null;
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerCompileFinisher (aCtx -> aCtx.getStylesheet ()
                                                   .putExtensionData (KEY_OPTIONS, aCtx.getCompiler ().getOptions ()));
  }
}
