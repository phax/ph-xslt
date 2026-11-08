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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt <code>internal/xslt/expose_spec_test.go</code>: more
 * xsl:expose cases (XSLT 3.0 §3.5.3.1): the consistency table between an
 * explicit xsl:expose and the declaration's own @visibility, implicitly
 * declared modes, and the effect of xsl:expose on the entry points of a
 * top-level package. go-xslt inspects the computed visibilities directly
 * (packageExposure); here they are observed through compilation and the entry
 * points of the package.
 *
 * @author Philip Helger
 */
public final class ExposeSpecTest
{
  private static final String PKA_NS = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

  /**
   * The package of go-xslt's exposeRoot, with text output and implicitly
   * declared modes allowed, so that it can run as a top-level package.
   */
  @NonNull
  private static String _exposeRoot (@NonNull final String sBody)
  {
    return "<xsl:package version=\"3.0\" name=\"urn:p\" declared-modes=\"no\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:p=\"urn:p\">" +
           "<xsl:output method=\"text\"/>" +
           sBody +
           "</xsl:package>";
  }

  /**
   * A top-level package (the principal module) with one named template and
   * one implicitly declared mode (declared-modes="no", since its default on
   * xsl:package is "yes"), exposed as the argument says.
   */
  @NonNull
  private static String _pkATop (@NonNull final String sExpose)
  {
    return "<xsl:package version=\"3.0\" declared-modes=\"no\" " +
           PKA_NS +
           ">\n" +
           "    <xsl:output method=\"text\"/>\n" +
           "    <xsl:template name=\"go\">go</xsl:template>\n" +
           "    <xsl:template match=\"/\" mode=\"m\">m</xsl:template>" +
           sExpose +
           "</xsl:package>";
  }

  /**
   * Compile and run an entry point.
   *
   * @return The output, or the error code of the compile or the run.
   */
  @NonNull
  private static String _run (@NonNull final String sSheet, @Nullable final XdmQName aTemplate, @Nullable final XdmQName aMode)
  {
    try
    {
      final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
      final XsltTransformRequest aRequest = new XsltTransformRequest ();
      aRequest.setGlobalContextItem (XsltTestHelper.parse ("<r/>"));
      aRequest.setInitialTemplate (aTemplate);
      aRequest.setInitialMode (aMode);
      return aExec.transform (aRequest).principalSerialized ();
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  @Nullable
  private static String _compileError (@NonNull final String sSheet)
  {
    try
    {
      XsltCompiler.compile (sSheet, new XsltCompileOptions ());
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * Rows of the §3.5.3.1 table: the visibility an explicit xsl:expose assigns,
   * against the declared @visibility. "" = permitted, else the error. A
   * permitted public or final template is an entry point, a private one is
   * not (XTDE0040).
   */
  @Test
  public void exposeConsistencyTable ()
  {
    final String [] [] aCases = { { "public", "public", "" },
                                  { "public", "final", "XTSE3010" },
                                  { "public", "private", "XTSE3010" },
                                  { "private", "public", "" },
                                  { "private", "private", "" },
                                  { "private", "final", "" },
                                  { "final", "public", "" },
                                  { "final", "final", "" },
                                  { "final", "private", "XTSE3010" },
                                  { "abstract", "abstract", "" },
                                  { "abstract", "public", "XTSE3010" },
                                  { "private", "abstract", "XTSE3010" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final String sExposed = aCase[0];
      final String sDeclared = aCase[1];
      final String sWant = aCase[2];
      final String sSheet = _exposeRoot ("<xsl:template name=\"a\" visibility=\"" +
                                         sDeclared +
                                         "\"/>\n" +
                                         "<xsl:expose component=\"template\" names=\"a\" visibility=\"" +
                                         sExposed +
                                         "\"/>");
      final String sGot;
      if (!sWant.isEmpty ())
        sGot = _compileError (sSheet);
      else
        if ("abstract".equals (sExposed))
        {
          // Permitted by xsl:expose, but a top-level package may not contain an
          // abstract component (XTSE3080)
          sGot = _compileError (sSheet);
          if (!"XTSE3080".equals (sGot))
            aErrors.add ("expose abstract on declared abstract: got " + sGot + ", want XTSE3080");
          continue;
        }
        else
        {
          final String sOut = _run (sSheet, new XdmQName ("a"), null);
          final String sWantRun = "private".equals (sExposed) ? "XTDE0040" : "";
          if (!sWantRun.equals (sOut))
            aErrors.add ("expose " + sExposed + " on declared " + sDeclared + ": got '" + sOut + "', want '" + sWantRun + "'");
          continue;
        }
      // A top-level package with an abstract component is also XTSE3080, which
      // may be reported first
      if ("abstract".equals (sDeclared) && "XTSE3080".equals (sGot))
        continue;
      if (!sWant.equals (sGot))
        aErrors.add ("expose " + sExposed + " on declared " + sDeclared + ": got " + sGot + ", want " + sWant);
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  @Test
  public void exposeMoreErrors ()
  {
    // "hidden" is not one of xsl:expose's visibilities
    assertEquals ("hidden visibility",
                  "XTSE0020",
                  _compileError (_exposeRoot ("<xsl:template name=\"a\"/>\n" +
                                              "<xsl:expose component=\"template\" names=\"a\" visibility=\"hidden\"/>")));
    // An arity suffix only identifies a function
    assertEquals ("arity on a template",
                  "XTSE3020",
                  _compileError (_exposeRoot ("<xsl:template name=\"a\"/>\n" +
                                              "<xsl:expose component=\"template\" names=\"a#0\" visibility=\"public\"/>")));
    assertEquals ("wrong arity",
                  "XTSE3020",
                  _compileError (_exposeRoot ("<xsl:function name=\"p:f\"><xsl:sequence select=\"1\"/></xsl:function>\n" +
                                              "<xsl:expose component=\"function\" names=\"p:f#2\" visibility=\"public\"/>")));
    assertEquals ("wrong kind",
                  "XTSE3020",
                  _compileError (_exposeRoot ("<xsl:template name=\"a\"/>\n" +
                                              "<xsl:expose component=\"variable\" names=\"a\" visibility=\"public\"/>")));
  }

  /**
   * A mode named only by a template rule's @mode is a component too and can be
   * matched by xsl:expose; a declared xsl:mode and the special mode tokens are
   * not implicit modes. Both modes end up public or final, so both are entry
   * points.
   */
  @Test
  public void exposeImplicitMode ()
  {
    final String sSheet = _exposeRoot ("\n" +
                                       "<xsl:mode name=\"p:declared\"/>\n" +
                                       "<xsl:template match=\"a\" mode=\"p:implicit p:declared #default\"/>\n" +
                                       "<xsl:template match=\"b\" mode=\"p:implicit\"/>\n" +
                                       "<xsl:expose component=\"mode\" names=\"p:implicit\" visibility=\"final\"/>\n" +
                                       "<xsl:expose component=\"mode\" names=\"*\" visibility=\"public\"/>");
    assertEquals ("implicit mode", "", _run (sSheet, null, new XdmQName ("urn:p", "implicit")));
    assertEquals ("declared mode", "", _run (sSheet, null, new XdmQName ("urn:p", "declared")));
  }

  /**
   * The initial named template must be public or final (XTDE0040) and a named
   * initial mode must not be private (XTDE0045); in a package the visibility
   * xsl:expose assigns is what counts.
   */
  @Test
  public void exposeEntryPoints ()
  {
    final String sPub = "<xsl:expose component=\"*\" names=\"*\" visibility=\"public\"/>";
    final String sPriv = "<xsl:expose component=\"*\" names=\"*\" visibility=\"private\"/>";
    assertEquals ("public template", "go", _run (_pkATop (sPub), new XdmQName ("go"), null));
    assertEquals ("private template", "XTDE0040", _run (_pkATop (sPriv), new XdmQName ("go"), null));
    assertEquals ("public mode", "m", _run (_pkATop (sPub), null, new XdmQName ("m")));
    assertEquals ("private mode", "XTDE0045", _run (_pkATop (sPriv), null, new XdmQName ("m")));
  }
}
