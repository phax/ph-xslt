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
package com.helger.xslt.instr;

import org.jspecify.annotations.NonNull;

import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;

/**
 * An instruction that produces no output but changes the context of the
 * instructions following it in the same sequence constructor (a local
 * xsl:variable binds its value for its following siblings).
 *
 * @author Philip Helger
 */
public interface IBindingInstruction extends IInstruction
{
  /**
   * @param aCtx
   *        The context.
   * @return The context for the following siblings.
   */
  @NonNull
  XsltContext bind (@NonNull XsltContext aCtx);

  default void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    bind (aCtx);
  }
}
