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
package com.helger.xpath.xdm.schema;

import java.util.List;
import java.util.function.IntFunction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

/**
 * The schema processor behind {@link XdmSchemaSupport}. The only
 * implementation is the Apache Xerces-J bridge, which is loaded by name and
 * only if Xerces-J is on the class path.
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
   * @param aTypeInfo
   *        A type reported by a validator.
   * @return The type definition if the type was reported by this provider's
   *         validator, otherwise <code>null</code>.
   */
  @Nullable
  IXdmTypeDefinition getDefinition (@NonNull TypeInfo aTypeInfo);

  /**
   * @param aValidatorHandler
   *        A validator handler (of any JAXP schema).
   * @return Access to the declared attribute types during the start tag, if the
   *         validator is this provider's, otherwise <code>null</code>.
   */
  @Nullable
  IntFunction <TypeInfo> getAttributeTypeDefinitionAccess (@NonNull Object aValidatorHandler);
}
