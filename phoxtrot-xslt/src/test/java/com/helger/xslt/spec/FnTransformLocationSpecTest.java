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

import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompileOptions.PackageSource;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt <code>internal/xslt/fn_transform_location_test.go</code>.
 *
 * @author Philip Helger
 */
public final class FnTransformLocationSpecTest
{
  /**
   * A stylesheet whose only template comes from a RELATIVE xsl:include, so it runs only when its
   * base URI is known.
   */
  private static final String INCLUDING = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                                          "  <xsl:include href=\"inc.xsl\"/>\n" +
                                          "</xsl:stylesheet>";

  private static final String INCLUDED = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                                         "  <xsl:template name=\"main\"><out>included</out></xsl:template>\n" +
                                         "</xsl:stylesheet>";

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  /** The calling stylesheet of go-xslt's mtEOuter. */
  @NonNull
  private static String _outer (@NonNull final String sExpr)
  {
    return "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
           "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
           "  xmlns:map=\"http://www.w3.org/2005/xpath-functions/map\">\n" +
           "  <xsl:output method=\"text\"/>\n" +
           "  <xsl:template match=\"/\"><xsl:value-of select=\"" +
           sExpr +
           "\"/></xsl:template>\n" +
           "</xsl:stylesheet>";
  }

  /**
   * stylesheet-location and stylesheet-base-uri (F&amp;O 3.1 §16.3.2): the location both locates
   * the module and is the default for its base URI (a relative one resolves against the static base
   * URI of the call); stylesheet-text gets one from stylesheet-base-uri. (Without it the include
   * cannot be retrieved, fn-transform-err-9: XTSE0165. Left out, as in go-xslt.)
   */
  @Test
  public void fnTransformLocations () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir, "main.xsl", INCLUDING);
    specWrite (aDir, "inc.xsl", INCLUDED);
    final String sUri = aDir.resolve ("main.xsl").toUri ().toString ();
    final String [] [] aCases = { { "location-absolute",
                                    "transform(map{'stylesheet-location': $loc, 'initial-template': QName('', 'main')})?output/out" },
                                  { "location-relative",
                                    "transform(map{'stylesheet-location': 'main.xsl', 'initial-template': QName('', 'main')})?output/out" },
                                  { "text-with-base",
                                    "transform(map{'stylesheet-text': $text, 'stylesheet-base-uri': $loc, 'initial-template': QName('', 'main')})?output/out" } };
    for (final String [] aCase : aCases)
    {
      final String sSheet = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                            "  <xsl:param name=\"loc\"/>\n" +
                            "  <xsl:param name=\"text\"/>\n" +
                            "  <xsl:output method=\"text\"/>\n" +
                            "  <xsl:template match=\"/\"><xsl:value-of select=\"" +
                            aCase[1] +
                            "\"/></xsl:template>\n" +
                            "</xsl:stylesheet>";
      final XsltExecutable aExec = XsltCompiler.compile (sSheet,
                                                         new XsltCompileOptions ().setBaseUri (aDir.resolve ("caller.xsl")
                                                                                                   .toUri ()
                                                                                                   .toString ()));
      final XsltTransformRequest aRequest = new XsltTransformRequest ();
      aRequest.setGlobalContextItem (XsltTestHelper.parse ("<x/>"));
      aRequest.setResolver (FileResourceResolver.INSTANCE);
      aRequest.params ().put (new XdmQName ("loc"), XdmSequence.of (XdmAtomic.string (sUri)));
      aRequest.params ().put (new XdmQName ("text"), XdmSequence.of (XdmAtomic.string (INCLUDING)));
      assertEquals (aCase[0], "included", aExec.transform (aRequest).principalSerialized ());
    }
  }

  /**
   * Invokes a host-supplied package by package-name, with package-version defaulting to "*" (any
   * version), with a version that selects it, and with one that selects nothing (FOXT0002).
   */
  @Test
  public void fnTransformPackageName () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir,
               "p.xsl",
               "<xsl:package name=\"urn:mte:p\" package-version=\"1.0\" version=\"3.0\"\n" +
                        "  xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                        "  <xsl:template name=\"main\" visibility=\"public\"><out>from-package</out></xsl:template>\n" +
                        "</xsl:package>");
    final String [] [] aCases = { { "any-version", "'package-name': 'urn:mte:p'", "from-package" },
                                  { "matching-version",
                                    "'package-name': 'urn:mte:p', 'package-version': '1.0'",
                                    "from-package" },
                                  { "no-such-version",
                                    "'package-name': 'urn:mte:p', 'package-version': '2.0'",
                                    "FOXT0002" },
                                  { "with-stylesheet-too",
                                    "'package-name': 'urn:mte:p', 'stylesheet-text': '&lt;x/&gt;'",
                                    "FOXT0002" } };
    for (final String [] aCase : aCases)
    {
      final String sSheet = _outer ("transform(map{" +
                                    aCase[1] +
                                    ", 'initial-template': QName('', 'main')})?output/out");
      final XsltCompileOptions aOptions = new XsltCompileOptions ().setBaseUri (aDir.resolve ("caller.xsl")
                                                                                    .toUri ()
                                                                                    .toString ())
                                                                   .addPackage (new PackageSource (null,
                                                                                                   null,
                                                                                                   aDir.resolve ("p.xsl")
                                                                                                       .toUri ()
                                                                                                       .toString ()));
      final XsltTransformRequest aRequest = new XsltTransformRequest ();
      aRequest.setGlobalContextItem (XsltTestHelper.parse ("<x/>"));
      String sGot;
      try
      {
        sGot = XsltCompiler.compile (sSheet, aOptions).transform (aRequest).principalSerialized ();
      }
      catch (final XPathException ex)
      {
        sGot = ex.getCode ();
      }
      assertEquals (aCase[0], aCase[2], sGot);
    }
  }

  /**
   * Calls fn:transform from a bare XPath expression with no enclosing stylesheet. With none of
   * initial-template, source-node or initial-match-selection, call-template invocation of
   * xsl:initial-template is used (§16.3.2).
   */
  @Test
  public void fnTransformStandalone ()
  {
    final CompiledXPath aXP = XPathCompiler.compile ("transform(map{'stylesheet-text': '<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template name=\"xsl:initial-template\"><out>bare</out></xsl:template></xsl:stylesheet>'})?output/out/string()",
                                                     new StaticContext ());
    final XdmSequence aResult = aXP.evaluate (aXP.createDynamicContext ());
    assertEquals ("bare", ((XdmAtomic) aResult.getFirst ()).getString ());
  }
}
