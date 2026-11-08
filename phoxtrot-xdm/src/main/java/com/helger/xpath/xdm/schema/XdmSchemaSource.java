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

import org.jspecify.annotations.Nullable;

/**
 * One schema document to compile.
 *
 * @param systemId
 *        The absolute URI of the document: where it is read from if
 *        <code>text</code> is <code>null</code>, and the base URI of its
 *        <code>xs:include</code>/<code>xs:import</code> locations. May be
 *        <code>null</code> for inline text.
 * @param text
 *        The document text, or <code>null</code> to read it from the system ID.
 * @author Philip Helger
 */
public record XdmSchemaSource (@Nullable String systemId, @Nullable String text)
{}
