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

import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;

/**
 * A term with occurrence bounds - go-xslt <code>particle</code>.
 * {@link #m_nMax} is {@link XsdConstants#UNBOUNDED} for
 * <code>maxOccurs="unbounded"</code>.
 *
 * @author Philip Helger
 */
final class XsdParticle
{
  int m_nMin;
  int m_nMax;
  IXsdTerm m_aTerm;
  /**
   * The annotation of an xs:element particle (with documentation only),
   * <code>null</code> when there is none.
   */
  ICommonsList <XsdDocumentation> m_aDocs;
  /**
   * Memo of the content-model determinism check (phase V4): 0 not yet
   * computed, 1 deterministic, 2 not. A compiled particle is shared by every
   * concurrent validation of its schema, hence atomic.
   */
  final AtomicInteger m_aDet = new AtomicInteger (0);

  XsdParticle (final int nMin, final int nMax, @NonNull final IXsdTerm aTerm)
  {
    m_nMin = nMin;
    m_nMax = nMax;
    m_aTerm = aTerm;
  }

  @Override
  public String toString ()
  {
    return "XsdParticle[" +
           m_nMin +
           ".." +
           (m_nMax == XsdConstants.UNBOUNDED ? "unbounded" : Integer.toString (m_nMax)) +
           " " +
           m_aTerm +
           "]";
  }
}
