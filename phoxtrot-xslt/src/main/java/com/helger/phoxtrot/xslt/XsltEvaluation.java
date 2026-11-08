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

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.XsltTransformer;

/**
 * The evaluation of standalone XPath expressions and patterns of a host
 * against one source document, using the declarations of a compiled
 * stylesheet (functions, keys, global variables and parameters). Created by
 * {@link XsltExecutable#createEvaluation(XsltTransformRequest)}; compile the
 * expressions against {@link XsltExecutable#createStaticContext()}.
 * <p>
 * Global variables, key indexes and documents loaded with
 * <code>doc()</code> are computed on first use and kept for the lifetime of
 * this object, so use one instance per source document. Not thread-safe.
 * Unlike a transformation, evaluation runs on the calling thread, so deep
 * recursion in stylesheet functions is limited by its stack.
 * <p>
 * A {@link com.helger.phoxtrot.xpath.CompiledXPath} is always evaluated with its own
 * static context. A pattern has none, so it is matched with the static
 * context of the dynamic context passed in: the ones created here carry
 * {@link XsltExecutable#createStaticContext()}. That only matters for what is
 * looked up at run time, such as a decimal format name or a lexical QName
 * passed to a function.
 *
 * @author Philip Helger
 */
public final class XsltEvaluation
{
  private final Engine m_aEngine;
  private final StaticContext m_aStaticContext;
  private final IXdmItem m_aGlobalContextItem;

  /**
   * Use {@link XsltExecutable#createEvaluation(XsltTransformRequest)}.
   *
   * @param aSheet
   *        The compiled stylesheet.
   * @param aRequest
   *        The request with the global context item, the stylesheet
   *        parameters, the resolvers and the limits.
   * @throws XsltException
   *         if the global context item does not fit the stylesheet or a
   *         required parameter is missing
   */
  public XsltEvaluation (@NonNull final CompiledStylesheet aSheet, @NonNull final XsltTransformRequest aRequest)
  {
    m_aEngine = new Engine (aSheet, aRequest);
    m_aStaticContext = aSheet.createStaticContext ();
    m_aGlobalContextItem = XsltTransformer.initGlobalContextItem (m_aEngine, new CommonsHashMap <> ());
  }

  /**
   * @return The global context item as the stylesheet sees it, or
   *         <code>null</code> if there is none. If the stylesheet strips
   *         whitespace, this is a node of the stripped copy of the tree of
   *         the requested item, and the host must navigate from it rather
   *         than from the original.
   */
  @Nullable
  public IXdmItem getGlobalContextItem ()
  {
    return m_aGlobalContextItem;
  }

  /**
   * @return A new dynamic context without local variables. If there is a
   *         global context item, it is the context item (position and size
   *         1) and the current item (<code>current()</code>); otherwise the
   *         focus is absent. Derive the contexts for other items with
   *         {@link DynamicContext#withFocus(IXdmItem, int, int)} and
   *         {@link DynamicContext#withCurrentItem(IXdmItem)}.
   */
  @NonNull
  public DynamicContext createDynamicContext ()
  {
    final DynamicContext ret = m_aEngine.createRootContext ().getDynamicContext ().withStaticContext (m_aStaticContext);
    if (m_aGlobalContextItem == null)
      return ret;
    return ret.withFocus (m_aGlobalContextItem, 1, 1).withCurrentItem (m_aGlobalContextItem);
  }

  /**
   * @return The texts of the xsl:message instructions executed so far (e.g.
   *         in stylesheet functions).
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getMessages ()
  {
    return m_aEngine.getMessages ().getClone ();
  }
}
