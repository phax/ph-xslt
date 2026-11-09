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

import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;

/**
 * Host hook that gives the XPath functions access to external resources
 * (<code>fn:doc</code>, <code>fn:collection</code>,
 * <code>fn:unparsed-text</code>, <code>fn:json-doc</code>,
 * <code>fn:uri-collection</code>), and the XSLT compiler access to stylesheet
 * modules ({@link #resolveStylesheetModule(String)}). The default resolver in
 * {@link DynamicContext} resolves nothing.
 * <p>
 * Node locations: the documents a transformation or query reads
 * ({@link #resolveDocument(String)}, {@link #openDocumentStream(String, Predicate)})
 * need none and should be parsed without (the default of {@link XdmParser});
 * only stylesheet modules need them.
 *
 * @author Philip Helger
 */
public interface IResourceResolver
{
  /**
   * @param sAbsoluteUri
   *        Absolute URI.
   * @return The parsed document or <code>null</code> if it is unavailable.
   *         The same URI must yield the same node within one evaluation
   *         (stability).
   */
  @Nullable
  IXdmNode resolveDocument (@NonNull String sAbsoluteUri);

  /**
   * Resolve a stylesheet module to compile: the target of
   * <code>xsl:include</code> and <code>xsl:import</code>, a library package
   * named by its location, and the <code>stylesheet-location</code> or
   * <code>package-location</code> of <code>fn:transform</code>. Its elements,
   * comments and processing instructions must carry the location the parser
   * reports ({@link XdmParser#setRecordLocations(boolean)}): the errors of the
   * stylesheet report the line and column of its elements, and
   * <code>xsl:try</code> tells the errors of global variables apart by them.
   * Unlike for {@link #resolveDocument(String)}, the same URI need not yield
   * the same node.
   * <p>
   * The default implementation calls {@link #resolveDocument(String)} inside
   * {@link XdmParser#getWithRecordLocations(java.util.function.Supplier)}:
   * every {@link XdmParser} and {@link com.helger.phoxtrot.xdm.XdmTreeBuilder}
   * created on the calling thread meanwhile records locations unless it is
   * told otherwise. A resolver that parses with such a parser and leaves its
   * location setting alone therefore returns a located module without
   * overriding this method. A resolver that returns documents it parsed
   * before (a cache), switches the locations off explicitly or builds its
   * nodes in another way must override this method and parse the module with
   * locations; otherwise errors in its modules have no line and column, and
   * <code>xsl:try</code> may take an error of a template for one of a global
   * variable.
   *
   * @param sAbsoluteUri
   *        Absolute URI without fragment identifier.
   * @return The parsed document or <code>null</code> if it is unavailable.
   */
  @Nullable
  default IXdmNode resolveStylesheetModule (@NonNull final String sAbsoluteUri)
  {
    return XdmParser.getWithRecordLocations ( () -> resolveDocument (sAbsoluteUri));
  }

  /**
   * Open a document for streamed processing (XSLT 3.0 section 19), with the
   * parser settings {@link #resolveDocument(String)} would use, and without
   * node locations (no streamed path reads them). The default implementation
   * streams nothing, and the document is then read in full by
   * {@link #resolveDocument(String)}.
   *
   * @param sAbsoluteUri
   *        Absolute URI without fragment identifier.
   * @param aStripWhitespace
   *        The element names whose whitespace-only text children are to be
   *        removed while reading (xsl:strip-space), or <code>null</code>. Pass
   *        it to
   *        {@link XdmStreamReader#open(org.xml.sax.InputSource, boolean, int, Predicate)}.
   * @return The open reader (the caller closes it), or <code>null</code> if
   *         the document is not streamed by this resolver.
   * @throws XdmParseException
   *         if the document cannot be read; an
   *         {@link com.helger.phoxtrot.xdm.XdmStreamUnsupportedException} means
   *         it is to be read in full instead
   */
  @Nullable
  default XdmStreamReader openDocumentStream (@NonNull final String sAbsoluteUri,
                                              @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
  {
    return null;
  }

  /**
   * @param sAbsoluteUri
   *        Absolute URI.
   * @param sEncoding
   *        Requested encoding or <code>null</code>.
   * @return The decoded text or <code>null</code> if unavailable.
   */
  @Nullable
  String resolveText (@NonNull String sAbsoluteUri, @Nullable String sEncoding);

  /**
   * Resolve a collection for <code>fn:collection</code>. The default
   * implementation knows no collection.
   *
   * @param sAbsoluteUri
   *        Absolute collection URI; the empty string for the default
   *        collection.
   * @return The items of the collection or <code>null</code> if the collection
   *         is unknown.
   */
  @Nullable
  default ICommonsList <? extends IXdmItem> resolveCollection (@NonNull final String sAbsoluteUri)
  {
    return null;
  }

  /**
   * Resolve a URI collection for <code>fn:uri-collection</code>. The default
   * implementation returns the document URIs of the document nodes of
   * {@link #resolveCollection(String)}.
   *
   * @param sAbsoluteUri
   *        Absolute collection URI; the empty string for the default
   *        collection.
   * @return The URIs of the resources in the collection or <code>null</code>
   *         if the collection is unknown.
   */
  @Nullable
  default ICommonsList <String> resolveUriCollection (@NonNull final String sAbsoluteUri)
  {
    final ICommonsList <? extends IXdmItem> aItems = resolveCollection (sAbsoluteUri);
    if (aItems == null)
      return null;
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final IXdmItem aItem : aItems)
      if (aItem instanceof final IXdmNode aNode && aNode.getDocumentUri () != null)
        ret.add (aNode.getDocumentUri ());
    return ret;
  }
}
