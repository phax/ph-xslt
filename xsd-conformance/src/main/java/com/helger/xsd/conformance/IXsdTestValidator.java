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
package com.helger.xsd.conformance;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;

/**
 * The validator under test, as the harness sees it: compile a set of schema
 * documents, then validate instances against the result. Implementations are
 * used by one thread at a time; the runner creates one instance per test set.
 * <p>
 * Every reference a validator follows (<code>xs:include</code>,
 * <code>xs:import</code>, <code>xs:redefine</code>,
 * <code>xsi:schemaLocation</code>, external DTD subsets) must resolve to local
 * files only; the run never touches the network.
 *
 * @author Philip Helger
 */
public interface IXsdTestValidator
{
  /** @return A display name for the summary, e.g. <code>Xerces-J 2.12.2</code>. */
  @NonNull
  String getDisplayName ();

  /**
   * @param eVersion
   *        The XSD version.
   * @return <code>true</code> if the validator implements this version.
   *         Every case of an unsupported version is reported as unsupported.
   */
  boolean isVersionSupported (@NonNull EXsdVersion eVersion);

  /**
   * Compile a schema set.
   *
   * @param aSchemaDocuments
   *        The local schema documents; the first is the entry document. An
   *        empty list is the empty schema set: instances are then governed by
   *        the documents their own <code>xsi:schemaLocation</code> and
   *        <code>xsi:noNamespaceSchemaLocation</code> hints name and by the
   *        built-in types.
   * @param eVersion
   *        The XSD version to apply.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  XsdCompileResult compile (@NonNull List <Path> aSchemaDocuments, @NonNull EXsdVersion eVersion);
}
