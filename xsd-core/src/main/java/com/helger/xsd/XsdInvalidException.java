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

import com.helger.base.enforce.ValueEnforcer;

/**
 * A genuine "invalid" verdict: the schema set is in error, or the instance is
 * not schema-valid. Distinct from {@link XsdUnsupportedException}, which means
 * the validator could not decide.
 *
 * @author Philip Helger
 */
public class XsdInvalidException extends RuntimeException
{
  private final transient XsdError m_aError;

  /**
   * @param aError
   *        The (first) error. May not be <code>null</code>.
   */
  public XsdInvalidException (@NonNull final XsdError aError)
  {
    super (ValueEnforcer.notNull (aError, "Error").toString ());
    m_aError = aError;
  }

  /** @return The error. Never <code>null</code>. */
  @NonNull
  public final XsdError getError ()
  {
    return m_aError;
  }

  /**
   * @param sCode
   *        The constraint code, may be empty.
   * @param sMessage
   *        The message.
   * @return A new exception (to be thrown by the caller).
   */
  @NonNull
  public static XsdInvalidException invalid (@NonNull final String sCode, @NonNull final String sMessage)
  {
    return new XsdInvalidException (XsdError.of (sCode, sMessage));
  }
}
