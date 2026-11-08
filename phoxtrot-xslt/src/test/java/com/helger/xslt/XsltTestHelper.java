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

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Helpers for tests that run whole stylesheets.
 *
 * @author Philip Helger
 */
public final class XsltTestHelper
{
  /** The usual stylesheet start tag. */
  public static final String SHEET = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                     " xmlns:xs='http://www.w3.org/2001/XMLSchema'>";
  public static final String END = "</xsl:stylesheet>";

  @PresentForCodeCoverage
  private static final XsltTestHelper INSTANCE = new XsltTestHelper ();

  private XsltTestHelper ()
  {}

  @NonNull
  public static IXdmNode parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException (ex);
    }
  }

  /**
   * Compile and run a stylesheet.
   *
   * @param sSheet
   *        Stylesheet text.
   * @param sSource
   *        Source document or <code>null</code> to start with
   *        xsl:initial-template.
   * @param aParams
   *        Stylesheet parameters (string values, as xs:untypedAtomic) or
   *        <code>null</code>.
   * @return The full result.
   */
  @NonNull
  public static XsltTransformResult run (@NonNull final String sSheet,
                                         @Nullable final String sSource,
                                         @Nullable final Map <String, String> aParams)
  {
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    if (sSource != null)
      aRequest.setGlobalContextItem (parse (sSource));
    if (aParams != null)
      for (final Map.Entry <String, String> aEntry : aParams.entrySet ())
        aRequest.params ().put (new XdmQName (aEntry.getKey ()), XdmSequence.of (XdmAtomic.untyped (aEntry.getValue ())));
    return aExec.transform (aRequest);
  }

  /**
   * Like go-xslt's transform(t, sheet, src, params).
   *
   * @param sSheet
   *        Stylesheet text.
   * @param sSource
   *        Source document or <code>null</code>.
   * @param aParams
   *        Parameters or <code>null</code>.
   * @return The serialized principal result.
   */
  @NonNull
  public static String transform (@NonNull final String sSheet,
                                  @Nullable final String sSource,
                                  @Nullable final Map <String, String> aParams)
  {
    return run (sSheet, sSource, aParams).principalSerialized ();
  }

  /**
   * Run a stylesheet with text output.
   *
   * @param sBody
   *        Declarations placed in a stylesheet with method="text".
   * @param sSource
   *        Source or <code>null</code>.
   * @return The output.
   */
  @NonNull
  public static String text (@NonNull final String sBody, @Nullable final String sSource)
  {
    return transform (SHEET + "<xsl:output method='text'/>" + sBody + END, sSource, null);
  }

  /**
   * @param sSheet
   *        Stylesheet.
   * @param sSource
   *        Source or <code>null</code>.
   * @return The error code of the expected failure.
   */
  @NonNull
  public static String errorCode (@NonNull final String sSheet, @Nullable final String sSource)
  {
    try
    {
      transform (sSheet, sSource, null);
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    throw new IllegalStateException ("No error");
  }
}
