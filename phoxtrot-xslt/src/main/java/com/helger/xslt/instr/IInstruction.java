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
import org.jspecify.annotations.Nullable;

import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslNode;

/**
 * A compiled instruction of a sequence constructor. Implementations are
 * immutable (a compiled stylesheet is used by several threads at once); all
 * run-time state lives in the {@link XsltContext} and the engine.
 *
 * @author Philip Helger
 */
public interface IInstruction
{
  /**
   * Execute the instruction.
   *
   * @param aCtx
   *        The run-time context.
   * @param aOut
   *        Where the result goes.
   * @throws com.helger.xpath.XPathException
   *         for dynamic errors; the enclosing sequence constructor attaches
   *         the location of this instruction.
   */
  void execute (@NonNull XsltContext aCtx, @NonNull IXsltOutput aOut);

  /** @return The stylesheet node for error locations, or <code>null</code>. */
  @Nullable
  XslNode getLocation ();
}
