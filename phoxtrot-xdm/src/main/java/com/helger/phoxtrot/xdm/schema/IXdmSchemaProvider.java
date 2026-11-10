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
package com.helger.phoxtrot.xdm.schema;

import java.util.List;

import org.jspecify.annotations.NonNull;

/**
 * A schema processor. The one behind {@link XdmSchemaSupport} is the native validator of the module
 * <code>phoxtrot-xsd</code> or the Apache Xerces-J bridge, each loaded by name and only if it is on
 * the class path; {@link com.helger.phoxtrot.xdm.jaxp.JdkSchemaProvider} is a limited one on the
 * JDK's own validator.
 *
 * @author Philip Helger
 */
public interface IXdmSchemaProvider
{
  /**
   * @param aSources
   *        The schema documents. May be empty for the built-in types only.
   * @return The compiled schema set.
   * @throws XdmSchemaException
   *         if the documents cannot be read or are not a valid schema
   */
  @NonNull
  IXdmSchemaSet compile (@NonNull List <XdmSchemaSource> aSources) throws XdmSchemaException;

  /**
   * Compile schema documents and control what the processor reads on its own. The sources passed
   * here are trusted and read as given (a source without text from its system ID). The documents
   * they reference (<code>xs:include</code>, <code>xs:import</code>, <code>xs:redefine</code>,
   * <code>xs:override</code>) are read from local files only if allowed, and never from the
   * network.
   * <p>
   * The default implementation fails closed: a provider that cannot restrict its reads refuses to
   * compile non-empty sources without local file access.
   *
   * @param aSources
   *        The schema documents. May be empty for the built-in types only.
   * @param bReadReferencedFiles
   *        <code>true</code> to read referenced documents from local files, <code>false</code> to
   *        read none (a reference then contributes nothing, as an unavailable location does).
   * @return The compiled schema set.
   * @throws XdmSchemaException
   *         if the documents cannot be read or are not a valid schema
   */
  @NonNull
  default IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources,
                                 final boolean bReadReferencedFiles) throws XdmSchemaException
  {
    if (!bReadReferencedFiles && !aSources.isEmpty ())
      throw new XdmSchemaException ("The schema processor " +
                                    getClass ().getSimpleName () +
                                    " cannot compile without reading referenced documents",
                                    new UnsupportedOperationException ());
    return compile (aSources);
  }
}
