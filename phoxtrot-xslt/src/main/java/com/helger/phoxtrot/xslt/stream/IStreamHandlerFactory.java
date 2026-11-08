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
package com.helger.phoxtrot.xslt.stream;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.XsltContext;

/**
 * Creates the record handler of a consuming instruction for one streamed
 * execution: the per-item code of the instruction, with the state it keeps
 * across items (the parameters of <code>xsl:iterate</code>, the open group of
 * <code>xsl:for-each-group</code>).
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IStreamHandlerFactory
{
  /**
   * @param aCtx
   *        The run-time context of the instruction.
   * @param aOut
   *        Where the instruction writes its result.
   * @param aRun
   *        The live run, to release retained records with.
   * @return A new handler. Never <code>null</code>.
   */
  @NonNull
  IStreamRecordHandler create (@NonNull XsltContext aCtx, @NonNull IXsltOutput aOut, @NonNull StreamedRun aRun);
}
