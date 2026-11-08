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
import org.jspecify.annotations.Nullable;

/**
 * The validator could not decide: the schema or instance uses a construct that
 * is not implemented (go-xslt's <code>ErrUnsupported</code>). Callers treat it
 * as "no verdict", never as "invalid". Every
 * {@link UnsupportedOperationException} that escapes the implementation (a
 * phase stub, for example) is reported as this exception by the public entry
 * points.
 *
 * @author Philip Helger
 */
public class XsdUnsupportedException extends UnsupportedOperationException
{
  /**
   * @param sMessage
   *        What is not supported.
   */
  public XsdUnsupportedException (@NonNull final String sMessage)
  {
    super (sMessage);
  }

  /**
   * @param sMessage
   *        What is not supported.
   * @param aCause
   *        The cause. May be <code>null</code>.
   */
  public XsdUnsupportedException (@NonNull final String sMessage, @Nullable final Throwable aCause)
  {
    super (sMessage, aCause);
  }
}
