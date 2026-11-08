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
package com.helger.phoxtrot.xslt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Options for compiling a stylesheet. Mutable and not copied by the
 * compiler: the compiled stylesheet keeps a reference to them (for the
 * stylesheets compiled by fn:transform), so changes made after compiling
 * reach those nested compilations.
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
                               @Nullable IXdmNode node,
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
    public PackageSource (@Nullable final String text, @Nullable final IXdmNode node, @Nullable final String baseUri)
    {
      this (text, node, baseUri, null, null);
    }
  }

  /**
   * A schema document the host offers to xsl:import-schema: used for a
   * declaration that names its namespace but gives no schema location, or
   * whose schema location cannot be read or has another target namespace
   * (XSLT 3.0 section 3.15 makes the location a hint).
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

  /**
   * A <code>streamable="yes"</code> declaration is not binding by default: a
   * construct that is not guaranteed streamable runs with the whole document in
   * memory.
   */
  public static final boolean DEFAULT_ENFORCE_STREAMABILITY = false;

  /**
   * The streaming feature is not claimed by default: the default run neither
   * analyses streamability for execution nor executes streamed.
   */
  public static final boolean DEFAULT_STREAMING_CLAIM = false;

  private String m_sBaseUri;
  private IResourceResolver m_aResolver;
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aStaticParams = new CommonsLinkedHashMap <> ();
  private final ICommonsList <PackageSource> m_aPackages = new CommonsArrayList <> ();
  private boolean m_bLoadExternalEntities = false;
  private boolean m_bSchemaAware = DEFAULT_SCHEMA_AWARE;
  private boolean m_bEnforceStreamability = DEFAULT_ENFORCE_STREAMABILITY;
  private boolean m_bStreamingClaim = DEFAULT_STREAMING_CLAIM;
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
   * {@link com.helger.phoxtrot.xdm.schema.XdmSchemaSupport#isAvailable()});
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

  /**
   * @return <code>true</code> if a <code>streamable="yes"</code> declaration is
   *         binding. Default {@link #DEFAULT_ENFORCE_STREAMABILITY}.
   */
  public boolean isEnforceStreamability ()
  {
    return m_bEnforceStreamability;
  }

  /**
   * Make a <code>streamable="yes"</code> declaration binding: a construct
   * declared streamable that is not guaranteed streamable (XSLT 3.0 section 19)
   * is then a static error (XTSE3430) instead of running with the whole
   * document in memory, which XTSE3430 permits when "the user has indicated
   * that the processor is to handle this situation by processing the
   * stylesheet without streaming". The analysis
   * ({@link com.helger.phoxtrot.xslt.stream.XsltStreamability}) runs at the end of
   * compilation and raises XTSE3430 (or XTSE3155 for a declared-streamable
   * function without parameters); without this option it does not run at all.
   *
   * @param bEnforceStreamability
   *        <code>true</code> to make streamability declarations binding.
   * @return this for chaining
   */
  @NonNull
  public XsltCompileOptions setEnforceStreamability (final boolean bEnforceStreamability)
  {
    m_bEnforceStreamability = bEnforceStreamability;
    return this;
  }

  /**
   * @return <code>true</code> if the stylesheet is compiled by a processor
   *         that claims the streaming feature. Default
   *         {@link #DEFAULT_STREAMING_CLAIM}.
   */
  public boolean isStreamingClaim ()
  {
    return m_bStreamingClaim;
  }

  /**
   * Claim the optional streaming conformance feature of XSLT 3.0 for the
   * stylesheet: <code>system-property('xsl:supports-streaming')</code> then
   * reports "yes", the streamability analysis runs at the end of compilation
   * and its verdicts are kept with the stylesheet, so that streamable
   * constructs execute streamed and the run-time streaming rules apply.
   *
   * @param bStreamingClaim
   *        <code>true</code> to claim streaming.
   * @return this for chaining
   */
  @NonNull
  public XsltCompileOptions setStreamingClaim (final boolean bStreamingClaim)
  {
    m_bStreamingClaim = bStreamingClaim;
    return this;
  }
}
