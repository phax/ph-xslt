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
package com.helger.xpath.xdm;

import org.jspecify.annotations.NonNull;

/**
 * Thrown by {@link XdmStreamReader} for a document it does not read
 * incrementally. It is no parse error: {@link XdmParser} reads such a document
 * in full. The reader raises it before it returns its first event, so a caller
 * can fall back to the full parse before it has acted on anything.
 *
 * @author Philip Helger
 */
public class XdmStreamUnsupportedException extends XdmParseException
{
  public XdmStreamUnsupportedException (@NonNull final String sMessage)
  {
    super (sMessage);
  }
}
