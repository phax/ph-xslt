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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Unique Particle Attribution (go-xslt <code>upa.go</code>): a content model
 * must be deterministic.
 * <p>
 * <b>Phase V4 owns this class.</b> Until then every method is a stub.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdUpa
{
  @PresentForCodeCoverage
  private static final XsdUpa INSTANCE = new XsdUpa ();

  private XsdUpa ()
  {}

  /**
   * go-xslt <code>checkUPA</code> for one effective content model. The loader
   * calls it for every complex type, named and anonymous, with
   * {@link XsdSchema#effectiveParticle(XsdComplexType)} (go-xslt
   * <code>checkAllUPA</code>).
   *
   * @param aSchema
   *        The schema (substitution groups, version).
   * @param aRoot
   *        The effective particle; <code>null</code> for empty content.
   * @throws XsdInvalidException
   *         (code <code>cos-nonambig</code>) if the model violates UPA
   */
  static void checkUPA (@NonNull final XsdSchema aSchema, @Nullable final XsdParticle aRoot)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>upaNullable</code>.
   *
   * @param aParticle
   *        The particle.
   * @return <code>true</code> if the particle can match the empty sequence.
   */
  static boolean upaNullable (@NonNull final XsdParticle aParticle)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
