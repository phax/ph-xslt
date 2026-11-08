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
package com.helger.phoxtrot.xpath.bench;

import java.util.function.BiConsumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.id.IHasID;
import com.helger.base.lang.EnumHelper;

/**
 * The XSLT workloads that transform the parsed document with one stylesheet of the resources (see
 * {@link XsltWorkload}). Each case checks the serialised result against the result computed in
 * Java.
 *
 * @author Philip Helger
 */
public enum EXsltCase implements IHasID <String>
{
  /** The identity transform with <code>on-no-match="shallow-copy"</code>: copy and serialise every node. */
  IDENTITY ("identity", (aData, sOut) -> BenchAssert.assertEquals ("identity output", aData.getXml (), sOut)),
  /** Template dispatch over four modes with value and structure predicates in the patterns. */
  DISPATCH ("dispatch", (aData, sOut) -> BenchAssert.assertEquals ("dispatch output", BenchExpected.xsltDispatch (aData), sOut)),
  /** Three <code>xsl:key</code> indexes and one lookup per line. */
  KEYS ("keys", (aData, sOut) -> BenchAssert.assertEquals ("keys output", BenchExpected.xsltKeys (aData), sOut)),
  /** <code>xsl:for-each-group</code> with <code>group-by</code> and <code>group-adjacent</code>. */
  GROUPING ("grouping",
            (aData, sOut) -> BenchAssert.assertEquals ("grouping output", BenchExpected.xsltGrouping (aData), sOut)),
  /** <code>xsl:sort</code> of all lines by two keys. */
  SORT ("sort", (aData, sOut) -> BenchAssert.assertEquals ("sort output", BenchExpected.xsltSort (aData), sOut)),
  /** <code>xsl:number</code> with <code>level="single"</code>, <code>"multiple"</code> and <code>"any"</code> per line. */
  NUMBER ("number", (aData, sOut) -> BenchAssert.assertEquals ("number output", BenchExpected.xsltNumber (aData), sOut)),
  /** <code>xsl:iterate</code> accumulating an <code>array(xs:decimal)</code> parameter. */
  ITERATE ("iterate", (aData, sOut) -> BenchAssert.assertEquals ("iterate output", BenchExpected.xsltIterate (aData), sOut)),
  /** A recursive stylesheet function and a recursive named template. */
  RECURSION ("recursion",
             (aData, sOut) -> BenchAssert.assertEquals ("recursion output", BenchExpected.xsltRecursion (aData), sOut)),
  /** Building a result tree with attribute value templates and serialising it as XML. */
  SERIALIZE_XML ("serialize-xml",
                 (aData, sOut) -> BenchAssert.assertEquals ("serialize-xml output",
                                                            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><report>" +
                                                                                            BenchExpected.xsltSerializeXmlRows (aData) +
                                                                                            "</report>",
                                                            sOut)),
  /** Building an HTML table and serialising it with the HTML 5 output method. */
  SERIALIZE_HTML ("serialize-html",
                  (aData, sOut) -> BenchAssert.assertEquals ("serialize-html output",
                                                             "<!DOCTYPE html><html><head>" +
                                                                                              "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" +
                                                                                              "<title>Invoice lines</title></head><body><table>" +
                                                                                              BenchExpected.xsltSerializeHtmlRows (aData) +
                                                                                              "</table></body></html>",
                                                             sOut)),
  /** Building an array of maps and serialising it with the JSON output method. */
  SERIALIZE_JSON ("serialize-json",
                  (aData, sOut) -> BenchAssert.assertEquals ("serialize-json output length",
                                                             Integer.valueOf (BenchExpected.xsltSerializeJsonLength (aData)),
                                                             Integer.valueOf (sOut.length ())));

  private final String m_sID;
  private final BiConsumer <BenchData, String> m_aVerifier;

  EXsltCase (@NonNull final String sID, @NonNull final BiConsumer <BenchData, String> aVerifier)
  {
    m_sID = sID;
    m_aVerifier = aVerifier;
  }

  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  /** @return The class path resource name of the stylesheet. Never <code>null</code>. */
  @NonNull
  public String getResourceName ()
  {
    return "xsl/" + m_sID + ".xsl";
  }

  /**
   * Check the serialised result of a transformation.
   *
   * @param aData
   *        The transformed document.
   * @param sOutput
   *        The serialised principal result.
   * @throws IllegalStateException
   *         if it is not the expected one
   */
  public void verify (@NonNull final BenchData aData, @NonNull final String sOutput)
  {
    m_aVerifier.accept (aData, sOutput);
  }

  @Nullable
  public static EXsltCase getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EXsltCase.class, sID);
  }
}
