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
 * Particle Valid (Restriction), XSD 1.0/1.1 section 3.9.6 (go-xslt
 * <code>restriction.go</code>): whether a derived content model is a valid
 * restriction of its base's.
 * <p>
 * <b>Phase V4 owns this class.</b> Until then every method is a stub.
 * <code>wcProcessRank</code> is already ported as
 * {@link XsdWildcard#processRank(String)}.
 *
 * @author Philip Helger
 */
final class XsdRestrictionChecker
{
  private final XsdSchema m_aSchema;
  /**
   * The comparison sits inside a MapAndSum mapping (sequence restricting a
   * choice): XSD 1.0 grants no substitution-group equivalence there.
   */
  private final boolean m_bInMapAndSum;

  XsdRestrictionChecker (@NonNull final XsdSchema aSchema)
  {
    this (aSchema, false);
  }

  XsdRestrictionChecker (@NonNull final XsdSchema aSchema, final boolean bInMapAndSum)
  {
    m_aSchema = aSchema;
    m_bInMapAndSum = bInMapAndSum;
  }

  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }

  boolean isInMapAndSum ()
  {
    return m_bInMapAndSum;
  }

  /**
   * go-xslt <code>particleRestrictsOK</code>.
   *
   * @param aDerived
   *        The derived particle; <code>null</code> for empty content.
   * @param aBase
   *        The base particle; <code>null</code> for empty content.
   * @return <code>true</code> if the derived particle is a valid restriction
   *         of the base (errs towards "valid" where undecidable).
   */
  boolean particleRestrictsOK (@Nullable final XsdParticle aDerived, @Nullable final XsdParticle aBase)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>nsSubset</code>: whether wildcard a's namespace constraint is
   * a subset of b's.
   *
   * @param aA
   *        The derived wildcard.
   * @param aB
   *        The base wildcard.
   * @return <code>true</code> unless a admits a namespace b forbids.
   */
  static boolean nsSubset (@NonNull final XsdWildcard aA, @NonNull final XsdWildcard aB)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
