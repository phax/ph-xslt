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
package com.helger.xslt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Options for compiling a stylesheet. Mutable; copied by the compiler.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsltCompileOptions
{
  /**
   * A library package made available to xsl:use-package (XSLT 3.0 section
   * 3.5): either its text or its parsed root, with the URI it was read from.
   * Packages are not implemented yet; the options only carry them.
   *
   * @param text
   *        The package source text, or <code>null</code> if a node is given.
   * @param node
   *        The parsed package (document or xsl:package element), or
   *        <code>null</code> if the text is given.
   * @param baseUri
   *        The URI of the package module, or <code>null</code>.
   */
  public record PackageSource (@Nullable String text, @Nullable XdmNode node, @Nullable String baseUri)
  {}

  private String m_sBaseUri;
  private IResourceResolver m_aResolver;
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aStaticParams = new CommonsLinkedHashMap <> ();
  private final ICommonsList <PackageSource> m_aPackages = new CommonsArrayList <> ();

  /** @return The stylesheet's own URI, against which import/include hrefs resolve. */
  @Nullable
  public String getBaseUri ()
  {
    return m_sBaseUri;
  }

  @NonNull
  public XsltCompileOptions setBaseUri (@Nullable final String sBaseUri)
  {
    m_sBaseUri = sBaseUri;
    return this;
  }

  /**
   * @return The resolver for stylesheet modules (xsl:import, xsl:include) and
   *         documents read at compile time, or <code>null</code> to read
   *         <code>file:</code> URIs directly.
   */
  @Nullable
  public IResourceResolver getResolver ()
  {
    return m_aResolver;
  }

  @NonNull
  public XsltCompileOptions setResolver (@Nullable final IResourceResolver aResolver)
  {
    m_aResolver = aResolver;
    return this;
  }

  /** @return The values of static stylesheet parameters, by name. */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> staticParams ()
  {
    return m_aStaticParams;
  }

  @NonNull
  public XsltCompileOptions setStaticParam (@NonNull final XdmQName aName, @NonNull final XdmSequence aValue)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (aValue, "Value");
    m_aStaticParams.put (aName, aValue);
    return this;
  }

  /** @return The library packages available to xsl:use-package (modifiable). */
  @NonNull
  public ICommonsList <PackageSource> packages ()
  {
    return m_aPackages;
  }

  /**
   * @param aPackage
   *        A library package to make available to xsl:use-package.
   * @return this for chaining
   */
  @NonNull
  public XsltCompileOptions addPackage (@NonNull final PackageSource aPackage)
  {
    ValueEnforcer.notNull (aPackage, "Package");
    m_aPackages.add (aPackage);
    return this;
  }
}
