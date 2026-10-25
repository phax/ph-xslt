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

import java.time.OffsetDateTime;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

/**
 * One invocation of a compiled stylesheet (XSLT 3.0 section 2.3). Exactly one
 * way of starting is used: an initial template, an initial function, or apply
 * templates to the initial match selection in the initial mode (the default
 * when nothing else is set and a source is given).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsltTransformRequest
{
  /** A template parameter of the initial template or initial mode. */
  public record TemplateParam (@NonNull XdmQName name, @NonNull XdmSequence value, boolean tunnel)
  {}

  private IXdmItem m_aGlobalContextItem;
  private XdmSequence m_aInitialMatchSelection;
  private XdmQName m_aInitialMode;
  private XdmQName m_aInitialTemplate;
  private XdmQName m_aInitialFunction;
  private final ICommonsList <XdmSequence> m_aFunctionArgs = new CommonsArrayList <> ();
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aParams = new CommonsLinkedHashMap <> ();
  private final ICommonsList <TemplateParam> m_aTemplateParams = new CommonsArrayList <> ();
  private String m_sBaseOutputUri;
  private IResourceResolver m_aResolver;
  private OffsetDateTime m_aCurrentDateTime;
  private BooleanSupplier m_aInterrupt;
  private long m_nMaxSteps;
  private int m_nMaxDepth;

  /** @return The global context item (normally the source document node). */
  @Nullable
  public IXdmItem getGlobalContextItem ()
  {
    return m_aGlobalContextItem;
  }

  @NonNull
  public XsltTransformRequest setGlobalContextItem (@Nullable final IXdmItem aItem)
  {
    m_aGlobalContextItem = aItem;
    return this;
  }

  /** @return The initial match selection; defaults to the global context item. */
  @Nullable
  public XdmSequence getInitialMatchSelection ()
  {
    return m_aInitialMatchSelection;
  }

  @NonNull
  public XsltTransformRequest setInitialMatchSelection (@Nullable final XdmSequence aSeq)
  {
    m_aInitialMatchSelection = aSeq;
    return this;
  }

  /**
   * @return The initial mode, <code>null</code> for the default mode. The
   *         names <code>#unnamed</code> and <code>#default</code> (in no
   *         namespace) stand for the unnamed and the default mode.
   */
  @Nullable
  public XdmQName getInitialMode ()
  {
    return m_aInitialMode;
  }

  @NonNull
  public XsltTransformRequest setInitialMode (@Nullable final XdmQName aMode)
  {
    m_aInitialMode = aMode;
    return this;
  }

  @Nullable
  public XdmQName getInitialTemplate ()
  {
    return m_aInitialTemplate;
  }

  /**
   * @param aName
   *        The initial named template; <code>xsl:initial-template</code> in the
   *        XSLT namespace is the conventional default.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setInitialTemplate (@Nullable final XdmQName aName)
  {
    m_aInitialTemplate = aName;
    return this;
  }

  @Nullable
  public XdmQName getInitialFunction ()
  {
    return m_aInitialFunction;
  }

  @NonNull
  public XsltTransformRequest setInitialFunction (@Nullable final XdmQName aName)
  {
    m_aInitialFunction = aName;
    return this;
  }

  /** @return The arguments of the initial function (modifiable). */
  @NonNull
  public ICommonsList <XdmSequence> functionArgs ()
  {
    return m_aFunctionArgs;
  }

  /** @return Stylesheet parameter values by name (modifiable). */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> params ()
  {
    return m_aParams;
  }

  /** @return Parameters for the initial template or mode (modifiable). */
  @NonNull
  public ICommonsList <TemplateParam> templateParams ()
  {
    return m_aTemplateParams;
  }

  @Nullable
  public String getBaseOutputUri ()
  {
    return m_sBaseOutputUri;
  }

  @NonNull
  public XsltTransformRequest setBaseOutputUri (@Nullable final String sUri)
  {
    m_sBaseOutputUri = sUri;
    return this;
  }

  /** @return The resolver for fn:doc, document(), unparsed-text etc. */
  @Nullable
  public IResourceResolver getResolver ()
  {
    return m_aResolver;
  }

  @NonNull
  public XsltTransformRequest setResolver (@Nullable final IResourceResolver aResolver)
  {
    m_aResolver = aResolver;
    return this;
  }

  @Nullable
  public OffsetDateTime getCurrentDateTime ()
  {
    return m_aCurrentDateTime;
  }

  @NonNull
  public XsltTransformRequest setCurrentDateTime (@Nullable final OffsetDateTime aValue)
  {
    m_aCurrentDateTime = aValue;
    return this;
  }

  @Nullable
  public BooleanSupplier getInterrupt ()
  {
    return m_aInterrupt;
  }

  @NonNull
  public XsltTransformRequest setInterrupt (@Nullable final BooleanSupplier aInterrupt)
  {
    m_aInterrupt = aInterrupt;
    return this;
  }

  /** @return The maximum number of instructions to execute, 0 for the default (20,000,000). */
  public long getMaxSteps ()
  {
    return m_nMaxSteps;
  }

  /**
   * @param nMaxSteps
   *        The maximum number of instructions to execute; when exceeded the
   *        transformation fails with XPDY0130. 0 for the default.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setMaxSteps (final long nMaxSteps)
  {
    m_nMaxSteps = nMaxSteps;
    return this;
  }

  /** @return The maximum nesting depth of templates and functions, 0 for the default (10,000). */
  public int getMaxDepth ()
  {
    return m_nMaxDepth;
  }

  /**
   * @param nMaxDepth
   *        The maximum nesting depth of templates and functions; it can only
   *        lower the default of 10,000. 0 for the default.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setMaxDepth (final int nMaxDepth)
  {
    m_nMaxDepth = nMaxDepth;
    return this;
  }
}
