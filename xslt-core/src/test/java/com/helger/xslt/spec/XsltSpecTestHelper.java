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
package com.helger.xslt.spec;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Helper for the specification-based XSLT tests, a port of go-xslt's
 * <code>internal/xslt/spec_helpers_test.go</code>. A case is a stylesheet (or
 * the body of one, see {@link #specSheet(String)}), a source document and
 * either the expected principal output or the W3C error code the compile or
 * the run must fail with.
 *
 * @author Philip Helger
 */
public final class XsltSpecTestHelper
{
  /**
   * One stylesheet-level case.
   *
   * @param name
   *        Name for the failure message.
   * @param sheet
   *        A complete stylesheet or the body of one.
   * @param src
   *        Source document or <code>null</code> for <code>&lt;doc/&gt;</code>.
   * @param want
   *        The expected output; a leading "~" means the output must contain
   *        the rest.
   * @param code
   *        The expected error code or <code>null</code>.
   */
  public record SpecCase (@NonNull String name,
                          @NonNull String sheet,
                          @Nullable String src,
                          @Nullable String want,
                          @Nullable String code)
  {}

  /** The namespaces declared on every wrapped stylesheet. */
  public static final String SPEC_NS = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"" +
                                       " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"" +
                                       " xmlns:map=\"http://www.w3.org/2005/xpath-functions/map\"" +
                                       " xmlns:array=\"http://www.w3.org/2005/xpath-functions/array\"" +
                                       " xmlns:fn=\"http://www.w3.org/2005/xpath-functions\"" +
                                       " xmlns:math=\"http://www.w3.org/2005/xpath-functions/math\"" +
                                       " xmlns:f=\"urn:f\"" +
                                       " exclude-result-prefixes=\"xs map array fn math f\"";

  @PresentForCodeCoverage
  private static final XsltSpecTestHelper INSTANCE = new XsltSpecTestHelper ();

  private XsltSpecTestHelper ()
  {}

  @NonNull
  public static SpecCase ok (@NonNull final String sName,
                             @NonNull final String sSheet,
                             @Nullable final String sSrc,
                             @NonNull final String sWant)
  {
    return new SpecCase (sName, sSheet, sSrc, sWant, null);
  }

  @NonNull
  public static SpecCase err (@NonNull final String sName,
                              @NonNull final String sSheet,
                              @Nullable final String sSrc,
                              @NonNull final String sCode)
  {
    return new SpecCase (sName, sSheet, sSrc, null, sCode);
  }

  /**
   * Wrap a stylesheet body in an XSLT 3.0 stylesheet with text output. A
   * complete stylesheet (or anything not starting with an XSLT element) is
   * returned unchanged.
   *
   * @param sBody
   *        Body or stylesheet.
   * @return The stylesheet.
   */
  @NonNull
  public static String specSheet (@NonNull final String sBody)
  {
    final String b = sBody.strip ();
    final boolean bIsBody = b.startsWith ("<xsl:") &&
                            !b.startsWith ("<xsl:stylesheet") &&
                            !b.startsWith ("<xsl:transform") &&
                            !b.startsWith ("<xsl:package");
    if (!bIsBody)
      return sBody;
    return "<xsl:stylesheet version=\"3.0\" " +
           SPEC_NS +
           ">\n<xsl:output method=\"text\"/>\n" +
           sBody +
           "\n</xsl:stylesheet>";
  }

  /**
   * Wrap a stylesheet body in an XSLT 3.0 stylesheet with XML output and no
   * XML declaration.
   *
   * @param sBody
   *        Body.
   * @return The stylesheet.
   */
  @NonNull
  public static String specXml (@NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"3.0\" " +
           SPEC_NS +
           ">\n<xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>\n" +
           sBody +
           "\n</xsl:stylesheet>";
  }

  @NonNull
  private static XdmNode _parseSource (@NonNull final String sSrc, @Nullable final Path aDir)
  {
    try
    {
      final InputSource aIS = new InputSource (new StringReader (sSrc));
      if (aDir != null)
        aIS.setSystemId (aDir.resolve ("source.xml").toUri ().toString ());
      return new XdmParser ().parse (aIS);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException ("Failed to parse the source " + sSrc, ex);
    }
  }

  /**
   * Compile the stylesheet as the file <code>dir/main.xsl</code> (so relative
   * URIs resolve against dir) and run it against the source.
   *
   * @param aDir
   *        Base directory or <code>null</code>.
   * @param sSheet
   *        Stylesheet or body.
   * @param sSrc
   *        Source or <code>null</code> for <code>&lt;doc/&gt;</code>.
   * @return The full result.
   * @throws XPathException
   *         on a static or dynamic error
   */
  @NonNull
  public static XsltTransformResult specRunIn (@Nullable final Path aDir,
                                               @NonNull final String sSheet,
                                               @Nullable final String sSrc)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    if (aDir != null)
      aOptions.setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ());
    final XsltExecutable aExec = XsltCompiler.compile (specSheet (sSheet), aOptions);
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (_parseSource (sSrc == null ? "<doc/>" : sSrc, aDir));
    if (aDir != null)
      aRequest.setResolver (FileResourceResolver.INSTANCE);
    return aExec.transform (aRequest);
  }

  /**
   * @param sSheet
   *        Stylesheet or body.
   * @param sSrc
   *        Source or <code>null</code>.
   * @return The serialized principal output.
   */
  @NonNull
  public static String specRun (@NonNull final String sSheet, @Nullable final String sSrc)
  {
    return specRunIn (null, sSheet, sSrc).principalSerialized ();
  }

  /**
   * @param sSheet
   *        Stylesheet or body.
   * @param sSrc
   *        Source or <code>null</code>.
   * @return The error code, or <code>null</code> if compile and run succeed.
   */
  @Nullable
  public static String specErrorCode (@NonNull final String sSheet, @Nullable final String sSrc)
  {
    try
    {
      specRun (sSheet, sSrc);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * Check one case.
   *
   * @param aDir
   *        Base directory or <code>null</code>.
   * @param aCase
   *        Case.
   * @return <code>null</code> if it passes, the mismatch otherwise.
   */
  @Nullable
  public static String specCheck (@Nullable final Path aDir, @NonNull final SpecCase aCase)
  {
    final XsltTransformResult aResult;
    try
    {
      aResult = specRunIn (aDir, aCase.sheet (), aCase.src ());
    }
    catch (final XPathException ex)
    {
      if (aCase.code () == null)
        return aCase.name () + ": unexpected error " + ex.getCode () + ": " + ex.getMessage ();
      if (!aCase.code ().equals (ex.getCode ()))
        return aCase.name () + ": want error " + aCase.code () + ", got " + ex.getCode () + ": " + ex.getMessage ();
      return null;
    }
    catch (final RuntimeException ex)
    {
      return aCase.name () + ": unexpected exception " + ex;
    }
    final String sGot = aResult.principalSerialized ();
    if (aCase.code () != null)
      return aCase.name () + ": want error " + aCase.code () + ", got output '" + sGot + "'";
    final String sWant = aCase.want ();
    if (sWant.startsWith ("~"))
    {
      // The output must contain the text (for serializations whose
      // surrounding details the specification leaves to the implementation)
      if (!sGot.contains (sWant.substring (1)))
        return aCase.name () + ":\n    got  '" + sGot + "'\n    want it to contain '" + sWant.substring (1) + "'";
      return null;
    }
    if (!sGot.equals (sWant))
      return aCase.name () + ":\n    got  '" + sGot + "'\n    want '" + sWant + "'";
    return null;
  }

  /**
   * Run all cases and fail with all mismatches.
   *
   * @param aDir
   *        Base directory or <code>null</code>.
   * @param aCases
   *        Cases.
   */
  public static void specRunAll (@Nullable final Path aDir, @NonNull final SpecCase... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final SpecCase aCase : aCases)
    {
      final String sError = specCheck (aDir, aCase);
      if (sError != null)
        aErrors.add (sError);
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  public static void specRunAll (@NonNull final SpecCase... aCases)
  {
    specRunAll (null, aCases);
  }

  /**
   * Write a file below a directory.
   *
   * @param aDir
   *        Directory.
   * @param sName
   *        Slash-separated relative name.
   * @param sContent
   *        Content (UTF-8).
   */
  public static void specWrite (@NonNull final Path aDir, @NonNull final String sName, @NonNull final String sContent)
  {
    try
    {
      final Path aPath = aDir.resolve (sName);
      Files.createDirectories (aPath.getParent ());
      Files.writeString (aPath, sContent, StandardCharsets.UTF_8);
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
  }
}
