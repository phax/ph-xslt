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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * Options of an instance validation (go-xslt <code>ValidateRequest.Language</code>
 * and <code>ValidateRequest.MaxErrors</code>). Neither option changes the verdict.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsdValidationOptions
{
  private String m_sLanguage = "";
  private int m_nMaxErrors = 0;

  /**
   * @return The preferred <code>xml:lang</code> of <code>msg:message</code>
   *         entries; empty if there is no preference. Default empty.
   */
  @NonNull
  public String getLanguage ()
  {
    return m_sLanguage;
  }

  /**
   * Select the wording of the diagnostics that carry <code>msg:message</code>
   * entries (failed assertions, content-model errors of declarations and
   * groups, missing required attributes): the entry in that language (exact,
   * then its primary subtag), else one without <code>xml:lang</code>, else the
   * first. The language only selects wording; it never changes the verdict.
   *
   * @param sLanguage
   *        The language, e.g. <code>de</code> or <code>de-CH</code>. Empty for
   *        no preference. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XsdValidationOptions setLanguage (@NonNull final String sLanguage)
  {
    ValueEnforcer.notNull (sLanguage, "Language");
    m_sLanguage = sLanguage;
    return this;
  }

  /**
   * @return The maximum number of errors reported; 0 for no limit. Default 0.
   */
  public int getMaxErrors ()
  {
    return m_nMaxErrors;
  }

  /**
   * Stop the validation after that many errors.
   *
   * @param nMaxErrors
   *        The limit; 0 for no limit.
   * @return this for chaining
   */
  @NonNull
  public XsdValidationOptions setMaxErrors (final int nMaxErrors)
  {
    ValueEnforcer.isGE0 (nMaxErrors, "MaxErrors");
    m_nMaxErrors = nMaxErrors;
    return this;
  }
}
