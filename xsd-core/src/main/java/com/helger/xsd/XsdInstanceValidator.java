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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Instance validation (go-xslt <code>instance.go</code>): parse the instance,
 * assess it strictly from the document element, <code>xsi:type</code>,
 * <code>xsi:nil</code>, <code>xsi:schemaLocation</code> hints, ID/IDREF.
 * <p>
 * <b>Phase V5 owns this class</b> (together with
 * {@link XsdAttributeValidator}, {@link XsdIdentityValidator} and
 * {@link XsdValidationRun}). Until then every method is a stub. The per-call
 * state go-xslt keeps on its per-call <code>Schema</code> copy (ids,
 * typeCache, childModels, unparsed, unassessed, ...) belongs in
 * {@link XsdValidationRun}, never in {@link XsdSchema}.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdInstanceValidator
{
  @PresentForCodeCoverage
  private static final XsdInstanceValidator INSTANCE = new XsdInstanceValidator ();

  private XsdInstanceValidator ()
  {}

  /**
   * go-xslt <code>Schema.Validate</code> for an instance file. Not well-formed
   * input is invalid.
   *
   * @param aSchema
   *        The compiled schema.
   * @param aInstance
   *        The instance file.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  static XsdValidationResult validate (@NonNull final XsdSchema aSchema, @NonNull final Path aInstance)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>Schema.Validate</code> for instance text.
   *
   * @param aSchema
   *        The compiled schema.
   * @param sInstance
   *        The instance document text.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  static XsdValidationResult validateText (@NonNull final XsdSchema aSchema, @NonNull final String sInstance)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
