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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

/**
 * The state of one validation call (go-xslt keeps it on a per-call shallow copy
 * of <code>Schema</code>: <code>ids</code>, <code>typeCache</code>,
 * <code>childModels</code>, <code>unparsed</code>, <code>unassessed</code>,
 * <code>coll</code>, <code>interrupt</code>, <code>nodeTypes</code>, ...).
 * <p>
 * <b>Phase V5 owns this class.</b> Only the schema reference exists so far.
 *
 * @author Philip Helger
 */
final class XsdValidationRun
{
  private final XsdSchema m_aSchema;

  XsdValidationRun (@NonNull final XsdSchema aSchema)
  {
    m_aSchema = aSchema;
  }

  /** @return The schema validated against. */
  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }
}
