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

import java.net.URI;
import java.net.URISyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.collation.ICollation;
import com.helger.phoxtrot.xpath.fn.FormatDecimalFormat;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;

/**
 * The static context of an expression: in-scope namespaces, default
 * namespaces, base URI, function library and language version switches. Fill it
 * before compiling; treat it as read-only afterwards.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StaticContext
{
  /** The XML version used unless set otherwise. */
  public static final EXmlVersion DEFAULT_XML_VERSION = EXmlVersion.V10;
  /** The XSD version used unless set otherwise. */
  public static final EXsdVersion DEFAULT_XSD_VERSION = EXsdVersion.V10;

  private final ICommonsMap <String, String> m_aNamespaces = new CommonsHashMap <> ();
  private String m_sDefaultElementNamespace = "";
  private String m_sInScopeDefaultNamespace = "";
  private String m_sDefaultFunctionNamespace = XdmConstants.NS_FN;
  private String m_sBaseUri;
  private FunctionLibrary m_aFunctions = FunctionLibrary.getDefault ();
  private boolean m_bXPath10Compatibility = false;
  private boolean m_bStaticTyping = false;
  private String m_sDefaultCollation = CollationRegistry.CODEPOINT_URI;
  private FormatDecimalFormat m_aDefaultDecimalFormat = FormatDecimalFormat.DEFAULT;
  private final ICommonsMap <XdmQName, FormatDecimalFormat> m_aDecimalFormats = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aDeclaredVariables = new CommonsHashSet <> ();
  private EXmlVersion m_eXmlVersion = DEFAULT_XML_VERSION;
  private EXsdVersion m_eXsdVersion = DEFAULT_XSD_VERSION;
  private boolean m_bXsdVersionSet = false;
  private IXdmSchemaSet m_aSchemaSet;
  // The default collation as resolved by getResolvedDefaultCollation; null until the first successful resolution
  private volatile ICollation m_aResolvedDefaultCollation;

  /**
   * Create a static context with the prefixes <code>xml</code>,
   * <code>xs</code>, <code>xsi</code>, <code>fn</code>, <code>math</code>,
   * <code>map</code>, <code>array</code> and <code>err</code> predeclared, no
   * default element namespace, <code>fn</code> as default function namespace,
   * the default function library and the codepoint collation.
   */
  public StaticContext ()
  {
    m_aNamespaces.put ("xml", XdmConstants.NS_XML);
    m_aNamespaces.put ("xs", XdmConstants.NS_XS);
    m_aNamespaces.put ("xsi", XdmConstants.NS_XSI);
    m_aNamespaces.put ("fn", XdmConstants.NS_FN);
    m_aNamespaces.put ("math", "http://www.w3.org/2005/xpath-functions/math");
    m_aNamespaces.put ("map", "http://www.w3.org/2005/xpath-functions/map");
    m_aNamespaces.put ("array", "http://www.w3.org/2005/xpath-functions/array");
    m_aNamespaces.put ("err", "http://www.w3.org/2005/xqt-errors");
  }

  /**
   * Declare or replace a prefix.
   *
   * @param sPrefix
   *        Prefix, non-empty; use {@link #setDefaultElementNamespace(String)}
   *        for the default namespace.
   * @param sNamespaceUri
   *        Namespace URI.
   * @return this for chaining
   */
  @NonNull
  public StaticContext declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notEmpty (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    m_aNamespaces.put (sPrefix, sNamespaceUri);
    return this;
  }

  /**
   * Remove all prefix bindings except <code>xml</code>, including the
   * predeclared ones (XSLT knows only the namespaces in scope in the
   * stylesheet).
   *
   * @return this for chaining
   */
  @NonNull
  public StaticContext removeAllNamespacesExceptXml ()
  {
    m_aNamespaces.entrySet ().removeIf (e -> !"xml".equals (e.getKey ()));
    return this;
  }

  /**
   * @param sPrefix
   *        The prefix.
   * @return The namespace URI or <code>null</code> if the prefix is unbound.
   */
  @Nullable
  public String getNamespaceUri (@NonNull final String sPrefix)
  {
    return m_aNamespaces.get (sPrefix);
  }

  /**
   * @return The default element/type namespace of XPath: the namespace of an
   *         unprefixed element or type name. Empty for no namespace (the
   *         default).
   */
  @NonNull
  public String getDefaultElementNamespace ()
  {
    return m_sDefaultElementNamespace;
  }

  @NonNull
  public StaticContext setDefaultElementNamespace (@NonNull final String sNamespaceUri)
  {
    m_sDefaultElementNamespace = ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    return this;
  }

  /**
   * @return The default namespace declared in scope of the expression
   *         (<code>xmlns="..."</code> in a host language such as XSLT), which
   *         is not the default element namespace of XPath. Empty if none.
   */
  @NonNull
  public String getInScopeDefaultNamespace ()
  {
    return m_sInScopeDefaultNamespace;
  }

  /**
   * @param sNamespaceUri
   *        The default namespace declared in scope of the expression, empty
   *        for none.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setInScopeDefaultNamespace (@NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    m_sInScopeDefaultNamespace = sNamespaceUri;
    return this;
  }

  /**
   * @return The namespace of an unprefixed function name; the
   *         <code>fn</code> namespace unless set otherwise.
   */
  @NonNull
  public String getDefaultFunctionNamespace ()
  {
    return m_sDefaultFunctionNamespace;
  }

  @NonNull
  public StaticContext setDefaultFunctionNamespace (@NonNull final String sNamespaceUri)
  {
    m_sDefaultFunctionNamespace = ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    return this;
  }

  /**
   * @return The static base URI, used to resolve relative URIs (e.g. of
   *         <code>fn:doc</code>), or <code>null</code> if none is known.
   */
  @Nullable
  public String getBaseUri ()
  {
    return m_sBaseUri;
  }

  @NonNull
  public StaticContext setBaseUri (@Nullable final String sBaseUri)
  {
    m_sBaseUri = sBaseUri;
    m_aResolvedDefaultCollation = null;
    return this;
  }

  @NonNull
  public FunctionLibrary getFunctionLibrary ()
  {
    return m_aFunctions;
  }

  @NonNull
  public StaticContext setFunctionLibrary (@NonNull final FunctionLibrary aFunctions)
  {
    m_aFunctions = ValueEnforcer.notNull (aFunctions, "Functions");
    return this;
  }

  /**
   * @return <code>true</code> if XPath 1.0 compatibility mode is on: general
   *         comparisons, arithmetic and the conversion of function arguments
   *         (XPath 3.1 section 3.1.5.2) follow the XPath 1.0 rules. Default
   *         <code>false</code>.
   */
  public boolean isXPath10Compatibility ()
  {
    return m_bXPath10Compatibility;
  }

  @NonNull
  public StaticContext setXPath10Compatibility (final boolean bXPath10Compatibility)
  {
    m_bXPath10Compatibility = bXPath10Compatibility;
    return this;
  }

  /**
   * @return <code>true</code> if the narrow part of the optional Static Typing
   *         Feature that this engine implements is in effect: an axis step
   *         that provably selects nothing is the static error XPST0005.
   *         Default <code>false</code>.
   */
  public boolean isStaticTyping ()
  {
    return m_bStaticTyping;
  }

  /**
   * Opt in to the narrow part of XPath 3.1's optional Static Typing Feature
   * that this engine implements: axis steps that can be proven, from the axis
   * and node-test shape alone, to always select nothing raise XPST0005 at
   * compile time. Off, the same expression evaluates to an empty sequence.
   *
   * @param bStaticTyping
   *        <code>true</code> to enable the check.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setStaticTyping (final boolean bStaticTyping)
  {
    m_bStaticTyping = bStaticTyping;
    return this;
  }

  /**
   * @return The in-scope schema definitions beyond the built-in types (the
   *         components of a schema-aware host), or <code>null</code> if there
   *         are none.
   */
  @Nullable
  public IXdmSchemaSet getSchemaSet ()
  {
    return m_aSchemaSet;
  }

  /**
   * Set the in-scope schema definitions: type names, element and attribute
   * declarations that <code>element(N, T)</code>,
   * <code>schema-element(N)</code>, <code>schema-attribute(N)</code>, casts
   * and constructor functions may refer to.
   *
   * @param aSchemaSet
   *        The schema set, or <code>null</code> for the built-in types only.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setSchemaSet (@Nullable final IXdmSchemaSet aSchemaSet)
  {
    m_aSchemaSet = aSchemaSet;
    return this;
  }

  /**
   * Declare an in-scope variable, so that a reference to it compiles. The
   * value is bound in the dynamic context.
   *
   * @param aName
   *        Variable name.
   * @return this for chaining
   */
  @NonNull
  public StaticContext declareVariable (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aDeclaredVariables.add (aName);
    return this;
  }

  /**
   * @param aName
   *        Variable name.
   * @return <code>true</code> if the variable was declared via
   *         {@link #declareVariable(XdmQName)}.
   */
  public boolean isVariableDeclared (@Nullable final XdmQName aName)
  {
    return aName != null && m_aDeclaredVariables.contains (aName);
  }

  /** @return The default collation URI; the codepoint collation unless set. */
  @NonNull
  public String getDefaultCollation ()
  {
    return m_sDefaultCollation;
  }

  @NonNull
  public StaticContext setDefaultCollation (@NonNull final String sCollationUri)
  {
    m_sDefaultCollation = ValueEnforcer.notEmpty (sCollationUri, "CollationUri");
    m_aResolvedDefaultCollation = null;
    return this;
  }

  /**
   * Resolve a collation URI as written in a function call: the codepoint collation URI directly, any other URI is
   * parsed, resolved against the static base URI if it is relative and there is one (with
   * {@link URI#resolve(URI)}), and looked up in the {@link CollationRegistry}.
   *
   * @param sUri
   *        The collation URI as written.
   * @return The collation, never <code>null</code> ({@link CollationRegistry#CODEPOINT} for the codepoint
   *         collation).
   * @throws XPathException
   *         FOCH0002 for a syntactically invalid or unsupported collation URI.
   */
  @NonNull
  public ICollation resolveCollation (@NonNull final String sUri)
  {
    // An absolute URI: resolving it changes nothing
    if (CollationRegistry.CODEPOINT_URI.equals (sUri))
      return CollationRegistry.CODEPOINT;
    String sAbsUri = sUri;
    try
    {
      final URI aUri = new URI (sUri);
      if (!aUri.isAbsolute ())
      {
        final String sBase = m_sBaseUri;
        if (sBase != null)
          sAbsUri = new URI (sBase).resolve (aUri).toString ();
      }
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      throw XPathException.of ("FOCH0002", "Invalid collation URI '" + sUri + "'");
    }
    final ICollation ret = CollationRegistry.getCollation (sAbsUri);
    if (ret == null)
      throw XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "'");
    return ret;
  }

  /**
   * The default collation resolved with {@link #resolveCollation(String)}. The first successful resolution is kept
   * until the default collation or the base URI is set again, so that functions which use the default collation do
   * not parse its URI on every call; a failure is not kept and raises the error again on every call. Safe for
   * concurrent readers once the context is filled (the kept value is published through a volatile field, and two
   * threads that resolve at the same time get equal collations).
   *
   * @return The default collation, never <code>null</code> ({@link CollationRegistry#CODEPOINT} for the codepoint
   *         collation).
   * @throws XPathException
   *         FOCH0002 for a syntactically invalid or unsupported default collation URI.
   */
  @NonNull
  public ICollation getResolvedDefaultCollation ()
  {
    ICollation ret = m_aResolvedDefaultCollation;
    if (ret == null)
    {
      ret = resolveCollation (m_sDefaultCollation);
      m_aResolvedDefaultCollation = ret;
    }
    return ret;
  }

  /** @return The unnamed decimal format used by fn:format-number. */
  @NonNull
  public FormatDecimalFormat getDefaultDecimalFormat ()
  {
    return m_aDefaultDecimalFormat;
  }

  @NonNull
  public StaticContext setDefaultDecimalFormat (@NonNull final FormatDecimalFormat aFormat)
  {
    m_aDefaultDecimalFormat = ValueEnforcer.notNull (aFormat, "Format");
    return this;
  }

  /**
   * @param aName
   *        Expanded name of a decimal format.
   * @return The named decimal format or <code>null</code> if there is none.
   */
  @Nullable
  public FormatDecimalFormat getDecimalFormat (@NonNull final XdmQName aName)
  {
    return m_aDecimalFormats.get (aName);
  }

  /**
   * Declare or replace a named decimal format (e.g. an XSLT
   * <code>xsl:decimal-format</code>).
   *
   * @param aName
   *        Expanded name.
   * @param aFormat
   *        The format.
   * @return this for chaining
   */
  @NonNull
  public StaticContext addDecimalFormat (@NonNull final XdmQName aName, @NonNull final FormatDecimalFormat aFormat)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (aFormat, "Format");
    m_aDecimalFormats.put (aName, aFormat);
    return this;
  }

  /**
   * @return The XML version whose rules apply where XML 1.0 and 1.1 differ.
   *         Default is {@link #DEFAULT_XML_VERSION}.
   */
  @NonNull
  public EXmlVersion getXmlVersion ()
  {
    return m_eXmlVersion;
  }

  @NonNull
  public StaticContext setXmlVersion (@NonNull final EXmlVersion eXmlVersion)
  {
    m_eXmlVersion = ValueEnforcer.notNull (eXmlVersion, "XmlVersion");
    return this;
  }

  /**
   * @return The XSD datatype version whose rules apply where XSD 1.0 and 1.1
   *         differ. Default is {@link #DEFAULT_XSD_VERSION}.
   */
  @NonNull
  public EXsdVersion getXsdVersion ()
  {
    return m_eXsdVersion;
  }

  /**
   * @return <code>true</code> if {@link #setXsdVersion(EXsdVersion)} was
   *         called, <code>false</code> if the default applies.
   */
  public boolean isXsdVersionSet ()
  {
    return m_bXsdVersionSet;
  }

  @NonNull
  public StaticContext setXsdVersion (@NonNull final EXsdVersion eXsdVersion)
  {
    m_eXsdVersion = ValueEnforcer.notNull (eXsdVersion, "XsdVersion");
    m_bXsdVersionSet = true;
    return this;
  }
}
