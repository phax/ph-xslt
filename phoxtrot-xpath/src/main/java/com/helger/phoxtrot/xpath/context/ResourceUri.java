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
package com.helger.phoxtrot.xpath.context;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;

/**
 * The URI of a resource an {@link IResourceResolver} is asked for: the URI the
 * engine resolved, and the reference as written in the expression or
 * stylesheet with the base URI it was resolved against, for resolvers that
 * resolve references themselves (as JAXP's <code>URIResolver</code> does).
 *
 * @param absoluteUri
 *        The URI resolved against the base URI; the reference as written if
 *        there is no base URI (then it may be relative), the empty string for
 *        the default collection. Never <code>null</code>.
 * @param href
 *        The reference as written (e.g. the <code>href</code> attribute of
 *        <code>xsl:include</code> or the argument of <code>fn:doc</code>),
 *        whitespace trimmed and without fragment identifier where the caller
 *        removes one. Never <code>null</code>.
 * @param baseUri
 *        The base URI the reference was resolved against, <code>null</code>
 *        if there is none.
 * @author Philip Helger
 */
@Immutable
public record ResourceUri (@NonNull String absoluteUri, @NonNull String href, @Nullable String baseUri)
{
  public ResourceUri
  {
    ValueEnforcer.notNull (absoluteUri, "AbsoluteUri");
    ValueEnforcer.notNull (href, "Href");
  }

  /**
   * @param sAbsoluteUri
   *        A URI that was not resolved from a reference (it is also the
   *        reference). May not be <code>null</code>.
   * @return The resource URI without base URI.
   */
  @NonNull
  public static ResourceUri of (@NonNull final String sAbsoluteUri)
  {
    return new ResourceUri (sAbsoluteUri, sAbsoluteUri, null);
  }

  /**
   * @param sAbsoluteUri
   *        The resolved URI. May not be <code>null</code>.
   * @param sHref
   *        The reference as written. May not be <code>null</code>.
   * @param sBaseUri
   *        The base URI it was resolved against, <code>null</code> or empty
   *        for none.
   * @return The resource URI.
   */
  @NonNull
  public static ResourceUri of (@NonNull final String sAbsoluteUri,
                                @NonNull final String sHref,
                                @Nullable final String sBaseUri)
  {
    return new ResourceUri (sAbsoluteUri, sHref, sBaseUri == null || sBaseUri.isEmpty () ? null : sBaseUri);
  }
}
