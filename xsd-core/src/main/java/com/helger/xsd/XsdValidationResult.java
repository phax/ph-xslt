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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * The outcome of a validation attempt (go-xslt <code>Result</code>): valid iff
 * the instance is schema-valid; otherwise the errors explain why (the first one
 * at least).
 *
 * @param valid
 *        <code>true</code> if the instance is schema-valid.
 * @param errors
 *        The errors, empty when valid.
 * @author Philip Helger
 */
public record XsdValidationResult (boolean valid, @NonNull ICommonsList <XsdError> errors)
{
  /** @return A valid result. */
  @NonNull
  public static XsdValidationResult createValid ()
  {
    return new XsdValidationResult (true, new CommonsArrayList <> ());
  }

  /**
   * @param aError
   *        The (first) error.
   * @return An invalid result.
   */
  @NonNull
  public static XsdValidationResult createInvalid (@NonNull final XsdError aError)
  {
    return new XsdValidationResult (false, new CommonsArrayList <> (aError));
  }
}
