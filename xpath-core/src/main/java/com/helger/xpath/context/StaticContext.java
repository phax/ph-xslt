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
package com.helger.xpath.context;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.fn.FormatDecimalFormat;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

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
  private final ICommonsMap <String, String> m_aNamespaces = new CommonsHashMap <> ();
  private String m_sDefaultElementNamespace = "";
  private String m_sDefaultFunctionNamespace = XdmConstants.NS_FN;
  private String m_sBaseUri;
  private FunctionLibrary m_aFunctions = FunctionLibrary.getDefault ();
  private boolean m_bXPath10Compatibility = false;
  private String m_sDefaultCollation = CollationRegistry.CODEPOINT_URI;
  private FormatDecimalFormat m_aDefaultDecimalFormat = FormatDecimalFormat.DEFAULT;
  private final ICommonsMap <XdmQName, FormatDecimalFormat> m_aDecimalFormats = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aDeclaredVariables = new CommonsHashSet <> ();

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
   * @param sPrefix
   *        The prefix.
   * @return The namespace URI or <code>null</code> if the prefix is unbound.
   */
  @Nullable
  public String getNamespaceUri (@NonNull final String sPrefix)
  {
    return m_aNamespaces.get (sPrefix);
  }

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

  @Nullable
  public String getBaseUri ()
  {
    return m_sBaseUri;
  }

  @NonNull
  public StaticContext setBaseUri (@Nullable final String sBaseUri)
  {
    m_sBaseUri = sBaseUri;
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
    return this;
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
}
