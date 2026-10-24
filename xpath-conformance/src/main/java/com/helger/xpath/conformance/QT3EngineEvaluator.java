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
package com.helger.xpath.conformance;

import java.time.OffsetDateTime;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

/**
 * Compiles and evaluates expressions with the engine under one configured
 * environment: namespaces, default element namespace, static base URI,
 * variables (declared in the static context, bound in the dynamic context),
 * focus, resource resolver and the run's fixed current date/time. A fresh
 * static context is built per expression, so the static context is never
 * changed after compiling.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class QT3EngineEvaluator implements IQT3Evaluator
{
  private final OffsetDateTime m_aNow;
  private final ICommonsOrderedMap <String, String> m_aNamespaces = new CommonsLinkedHashMap <> ();
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aVars = new CommonsLinkedHashMap <> ();
  private String m_sDefaultElementNamespace;
  private String m_sBaseUri;
  private String m_sDefaultCollation;
  private boolean m_bXPath10Compatibility;
  private IXdmItem m_aContextItem;
  private IResourceResolver m_aResolver;

  /**
   * @param aNow
   *        The current date/time of the run.
   */
  public QT3EngineEvaluator (@NonNull final OffsetDateTime aNow)
  {
    ValueEnforcer.notNull (aNow, "Now");
    m_aNow = aNow;
  }

  @NonNull
  public QT3EngineEvaluator declareNamespace (@NonNull final String sPrefix, @NonNull final String sUri)
  {
    m_aNamespaces.put (sPrefix, sUri);
    return this;
  }

  /**
   * @param sPrefix
   *        A prefix.
   * @return The namespace URI declared here or <code>null</code>.
   */
  @Nullable
  public String getNamespaceUri (@NonNull final String sPrefix)
  {
    return m_aNamespaces.get (sPrefix);
  }

  @NonNull
  public QT3EngineEvaluator setDefaultElementNamespace (@Nullable final String sUri)
  {
    m_sDefaultElementNamespace = sUri;
    return this;
  }

  @NonNull
  public QT3EngineEvaluator setBaseUri (@Nullable final String sBaseUri)
  {
    m_sBaseUri = sBaseUri;
    return this;
  }

  @NonNull
  public QT3EngineEvaluator setDefaultCollation (@Nullable final String sDefaultCollation)
  {
    m_sDefaultCollation = sDefaultCollation;
    return this;
  }

  @NonNull
  public QT3EngineEvaluator setXPath10Compatibility (final boolean b)
  {
    m_bXPath10Compatibility = b;
    return this;
  }

  @NonNull
  public QT3EngineEvaluator setContextItem (@Nullable final IXdmItem aItem)
  {
    m_aContextItem = aItem;
    return this;
  }

  @Nullable
  public IXdmItem getContextItem ()
  {
    return m_aContextItem;
  }

  @NonNull
  public QT3EngineEvaluator setResourceResolver (@Nullable final IResourceResolver aResolver)
  {
    m_aResolver = aResolver;
    return this;
  }

  @NonNull
  public QT3EngineEvaluator setVariable (@NonNull final XdmQName aName, @NonNull final XdmSequence aValue)
  {
    m_aVars.put (aName, aValue);
    return this;
  }

  /**
   * @return A copy of the variables bound so far.
   */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> getAllVariables ()
  {
    return m_aVars.getClone ();
  }

  @NonNull
  public XdmSequence evaluate (@NonNull final String sExpr, @NonNull final Map <XdmQName, XdmSequence> aExtraVars)
  {
    final StaticContext aStatic = new StaticContext ();
    for (final Map.Entry <String, String> aEntry : m_aNamespaces.entrySet ())
      aStatic.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    if (m_sDefaultElementNamespace != null)
      aStatic.setDefaultElementNamespace (m_sDefaultElementNamespace);
    aStatic.setBaseUri (m_sBaseUri);
    if (m_sDefaultCollation != null)
      aStatic.setDefaultCollation (m_sDefaultCollation);
    aStatic.setXPath10Compatibility (m_bXPath10Compatibility);
    for (final XdmQName aName : m_aVars.keySet ())
      aStatic.declareVariable (aName);
    for (final XdmQName aName : aExtraVars.keySet ())
      aStatic.declareVariable (aName);

    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, aStatic);
    DynamicContext aCtx = aCompiled.createDynamicContext ();
    aCtx.setCurrentDateTime (m_aNow);
    if (m_aResolver != null)
      aCtx.setResourceResolver (m_aResolver);
    if (m_aContextItem != null)
      aCtx = aCtx.withFocus (m_aContextItem, 1, 1);
    for (final Map.Entry <XdmQName, XdmSequence> aEntry : m_aVars.entrySet ())
      aCtx = aCtx.withVariable (aEntry.getKey (), aEntry.getValue ());
    for (final Map.Entry <XdmQName, XdmSequence> aEntry : aExtraVars.entrySet ())
      aCtx = aCtx.withVariable (aEntry.getKey (), aEntry.getValue ());
    return aCompiled.evaluate (aCtx);
  }
}
