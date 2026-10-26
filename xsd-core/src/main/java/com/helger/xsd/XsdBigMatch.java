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

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmNode;

/**
 * The linear counting matcher for content models of deterministic shape over
 * many children (go-xslt <code>bigmatch.go</code>).
 * <p>
 * <b>Phase V4 owns this class.</b> Until then every method is a stub.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdBigMatch
{
  @PresentForCodeCoverage
  private static final XsdBigMatch INSTANCE = new XsdBigMatch ();

  private XsdBigMatch ()
  {}

  /**
   * go-xslt <code>countMatch</code>.
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The content model.
   * @param aKids
   *        The element children.
   * @return Whether they match, and whether the counting matcher could decide.
   */
  @NonNull
  static XsdMatchResult countMatch (@NonNull final XsdSchema aSchema,
                                    @NonNull final XsdParticle aParticle,
                                    @NonNull final List <XdmNode> aKids)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>particleDeterministic</code> (memoised in
   * {@link XsdParticle#m_aDet}).
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The content model.
   * @return <code>true</code> if the counting matcher may be used.
   */
  static boolean particleDeterministic (@NonNull final XsdSchema aSchema, @NonNull final XsdParticle aParticle)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
