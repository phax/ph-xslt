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
 * The XSLT/XPath schema-awareness bridge (go-xslt <code>bridge.go</code>):
 * component lookup by name, derivation tests, casting to schema types and
 * validation of an existing tree in place, exposed through the
 * <code>IXdmSchemaProvider</code> SPI of <code>xpath-xdm</code>.
 * <p>
 * <b>Phase V6 owns this class.</b> Until then it is an empty shell.
 *
 * @author Philip Helger
 */
final class XsdBridge
{
  private final XsdSchema m_aSchema;

  XsdBridge (@NonNull final XsdSchema aSchema)
  {
    m_aSchema = aSchema;
  }

  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }
}
