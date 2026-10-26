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

import org.jspecify.annotations.NonNull;

/**
 * A schema processor. The one behind {@link XdmSchemaSupport} is the Apache
 * Xerces-J bridge, which is loaded by name and only if Xerces-J is on the
 * class path; {@link com.helger.xpath.xdm.jaxp.JdkSchemaProvider} is a
 * limited one on the JDK's own validator.
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
}
