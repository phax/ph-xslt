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
   * 3.5): its text, its parsed root or only the URI it is read from. The name
   * and version are taken from the xsl:package element unless given here.
   *
   * @param text
   *        The package source text, or <code>null</code>.
   * @param node
   *        The parsed package (document or xsl:package element), or
   *        <code>null</code>.
   * @param baseUri
   *        The URI of the package module, or <code>null</code>. If neither
   *        text nor node is given, the package is read from it.
   * @param name
   *        The package name, or <code>null</code> to use
   *        xsl:package/@name.
   * @param version
   *        The package version, or <code>null</code> to use
   *        xsl:package/@package-version.
   */
  public record PackageSource (@Nullable String text,
                               @Nullable XdmNode node,
                               @Nullable String baseUri,
                               @Nullable String name,
                               @Nullable String version)
  {
    /**
     * A package whose name and version are those its xsl:package declares.
     *
     * @param text
     *        The package source text, or <code>null</code>.
     * @param node
     *        The parsed package, or <code>null</code>.
     * @param baseUri
     *        The URI of the package module, or <code>null</code>.
     */
    public PackageSource (@Nullable final String text, @Nullable final XdmNode node, @Nullable final String baseUri)
    {
      this (text, node, baseUri, null, null);
    }
  }

  /**
   * A schema document the host offers to xsl:import-schema: used for a
   * declaration that names its namespace but gives no schema location, or
   * whose schema location cannot be read or has another target namespace
   * (XSLT 3.0 section 3.16 makes the location a hint).
   *
   * @param namespace
   *        The target namespace, or <code>null</code> to take it from the
   *        document itself.
   * @param systemId
   *        The absolute URI of the schema document.
   */
  public record HostSchema (@Nullable String namespace, @NonNull String systemId)
  {}

  /** Schema-awareness is off by default: the processor is a basic processor. */
  public static final boolean DEFAULT_SCHEMA_AWARE = false;

  private String m_sBaseUri;
  private IResourceResolver m_aResolver;
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aStaticParams = new CommonsLinkedHashMap <> ();
  private final ICommonsList <PackageSource> m_aPackages = new CommonsArrayList <> ();
  private boolean m_bLoadExternalEntities = false;
  private boolean m_bSchemaAware = DEFAULT_SCHEMA_AWARE;
  private final ICommonsList <HostSchema> m_aHostSchemas = new CommonsArrayList <> ();

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

  /**
   * @return <code>true</code> if external DTDs and entities of stylesheet
   *         modules are loaded. Default <code>false</code> (for untrusted
   *         stylesheets).
   */
  public boolean isLoadExternalEntities ()
  {
    return m_bLoadExternalEntities;
  }

  @NonNull
  public XsltCompileOptions setLoadExternalEntities (final boolean bLoadExternalEntities)
  {
    m_bLoadExternalEntities = bLoadExternalEntities;
    return this;
  }

  /**
   * @return <code>true</code> if the stylesheet is compiled by a schema-aware
   *         processor. Default {@link #DEFAULT_SCHEMA_AWARE}.
   */
  public boolean isSchemaAware ()
  {
    return m_bSchemaAware;
  }

  /**
   * Claim schema-awareness (XSLT 3.0 section 27.2) for the stylesheet:
   * xsl:import-schema, the validation and type attributes, typed input and the
   * schema type tests of a schema-aware processor. It needs Apache Xerces-J on
   * the class path (see
   * {@link com.helger.xpath.xdm.schema.XdmSchemaSupport#isAvailable()});
   * compiling with the claim fails otherwise. Without it, the processor is a
   * basic processor.
   *
   * @param bSchemaAware
   *        <code>true</code> to compile schema-aware.
   * @return this for chaining
   */
  @NonNull
  public XsltCompileOptions setSchemaAware (final boolean bSchemaAware)
  {
    m_bSchemaAware = bSchemaAware;
    return this;
  }

  /**
   * @return The schema documents the host offers to xsl:import-schema
   *         (mutable). Only used when schema-aware.
   */
  @NonNull
  public ICommonsList <HostSchema> hostSchemas ()
  {
    return m_aHostSchemas;
  }
}
