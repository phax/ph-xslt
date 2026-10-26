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

import java.util.BitSet;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmNode;

/**
 * The content-model matcher (go-xslt <code>content.go</code>: the memoised
 * position-set NFA over at most 600 children, open content). Above 600
 * children it hands over to {@link XsdBigMatch}.
 * <p>
 * <b>Phase V4 owns this class.</b> Until then every method is a stub. The
 * parts of <code>content.go</code> the loader needed are already ported and
 * stay where they are: <code>effectiveParticle</code>,
 * <code>effectiveAttrs</code>, <code>effectiveOpenContent</code>,
 * <code>matchesElementName</code>, <code>substitutionAllowed</code>,
 * <code>contentDecls</code> ({@link XsdSchema}), <code>wildcardMatches</code>
 * / <code>wildcardAdmits</code> ({@link XsdWildcard}).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdContentMatcher
{
  @PresentForCodeCoverage
  private static final XsdContentMatcher INSTANCE = new XsdContentMatcher ();

  private XsdContentMatcher ()
  {}

  /**
   * go-xslt <code>contentMatches</code>.
   *
   * @param aSchema
   *        The schema (substitution groups).
   * @param aParticle
   *        The effective content model; <code>null</code> for empty content.
   * @param aKids
   *        The element children of the instance element, in order.
   * @return Whether they match, and whether that was decided.
   */
  @NonNull
  static XsdMatchResult contentMatches (@NonNull final XsdSchema aSchema,
                                        @Nullable final XsdParticle aParticle,
                                        @NonNull final List <XdmNode> aKids)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>contentMatchesOpen</code>: {@link #contentMatches} with XSD
   * 1.1 open content.
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The effective content model.
   * @param aKids
   *        The element children.
   * @param aOpenContent
   *        The effective open content; <code>null</code> for none.
   * @return Whether they match, and whether that was decided.
   */
  @NonNull
  static XsdMatchResult contentMatchesOpen (@NonNull final XsdSchema aSchema,
                                            @Nullable final XsdParticle aParticle,
                                            @NonNull final List <XdmNode> aKids,
                                            @Nullable final XsdOpenContent aOpenContent)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>greedyOpenAttribution</code>: which children the open-content
   * wildcard takes (XSD 1.1).
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The effective content model.
   * @param aKids
   *        The element children.
   * @param aOpenContent
   *        The open content.
   * @return The indices of the children attributed to the open content.
   */
  @NonNull
  static BitSet greedyOpenAttribution (@NonNull final XsdSchema aSchema,
                                       @Nullable final XsdParticle aParticle,
                                       @NonNull final List <XdmNode> aKids,
                                       @NonNull final XsdOpenContent aOpenContent)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
