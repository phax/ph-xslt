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
package com.helger.xpath.spec;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.XdmNode;

/**
 * Port of go-xslt's <code>rsResolver</code> (fn_resource_test.go): an
 * in-memory resource resolver serving fixed texts, documents and collections.
 *
 * @author Philip Helger
 */
final class FnSpecResolver implements IResourceResolver
{
  private final ICommonsMap <String, String> m_aTexts = new CommonsHashMap <> ();
  private final ICommonsMap <String, XdmNode> m_aDocs = new CommonsHashMap <> ();
  private final ICommonsMap <String, ICommonsList <XdmNode>> m_aColls = new CommonsHashMap <> ();
  private final boolean m_bCollections;

  /**
   * @param bCollections
   *        <code>false</code> for a resolver that knows no collections at all
   *        (go-xslt's <code>rsTextOnly</code>).
   */
  FnSpecResolver (final boolean bCollections)
  {
    m_bCollections = bCollections;
  }

  FnSpecResolver ()
  {
    this (true);
  }

  /**
   * Parse a document and give it a document and base URI (go-xslt's
   * <code>rsDoc</code>).
   *
   * @param sXml
   *        XML.
   * @param sBase
   *        URI.
   * @return The document node.
   */
  @NonNull
  static XdmNode doc (@NonNull final String sXml, @NonNull final String sBase)
  {
    final XdmNode ret = SpecTestHelper.parse (sXml);
    ret.setDocumentUri (sBase);
    ret.setBaseUri (sBase);
    return ret;
  }

  @NonNull
  FnSpecResolver text (@NonNull final String sUri, @NonNull final String sText)
  {
    m_aTexts.put (sUri, sText);
    return this;
  }

  @NonNull
  FnSpecResolver doc (@NonNull final String sUri, @NonNull final XdmNode aDoc)
  {
    m_aDocs.put (sUri, aDoc);
    return this;
  }

  @NonNull
  FnSpecResolver coll (@NonNull final String sUri, @NonNull final XdmNode... aDocs)
  {
    m_aColls.put (sUri, new CommonsArrayList <> (aDocs));
    return this;
  }

  @Nullable
  public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    return m_aDocs.get (sAbsoluteUri);
  }

  @Nullable
  public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
  {
    return m_aTexts.get (sAbsoluteUri);
  }

  @Override
  @Nullable
  public ICommonsList <XdmNode> resolveCollection (@NonNull final String sAbsoluteUri)
  {
    return m_bCollections ? m_aColls.get (sAbsoluteUri) : null;
  }
}
