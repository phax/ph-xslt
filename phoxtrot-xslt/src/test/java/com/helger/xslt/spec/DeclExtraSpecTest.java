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

import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompileOptions.PackageSource;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt <code>internal/xslt/decl_extra_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class DeclExtraSpecTest
{
  @NonNull
  private static String _runWithPackage (@NonNull final Path aDir,
                                         @NonNull final String sPackageUri,
                                         @NonNull final String sSheet)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ())
                                                                 .addPackage (new PackageSource (null, null, sPackageUri));
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (XsltTestHelper.parse ("<doc/>"));
    return XsltCompiler.compile (sSheet, aOptions).transform (aRequest).principalSerialized ().trim ();
  }

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  /**
   * XSLT 3.0 §5.3.? xsl:global-context-item declarations and §20.? dynamic
   * calls of the XSLT-defined functions.
   */
  @Test
  public void compileSpecGlobalContextItemDecls () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir, "gci.xsl", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:global-context-item use=\"optional\"/></xsl:stylesheet>");
    specWrite (aDir, "gci-same.xsl", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:global-context-item use=\"required\"/></xsl:stylesheet>");
    specRunAll (aDir,
      // XTSE3087: more than one in a module, or inconsistent across modules.
      err ("two in one module", "<xsl:global-context-item/><xsl:global-context-item/><xsl:template match=\"/\">x</xsl:template>", null, "XTSE3087"),
      err ("inconsistent across modules", "<xsl:include href=\"gci.xsl\"/><xsl:global-context-item use=\"required\"/><xsl:template match=\"/\">x</xsl:template>", null, "XTSE3087"),
      ok ("consistent across modules", "<xsl:include href=\"gci-same.xsl\"/><xsl:global-context-item use=\"required\"/><xsl:template match=\"/\">x</xsl:template>", null, "x"),
      ok ("function-lookup of an XSLT function", "<xsl:template match=\"/\"><xsl:value-of select=\"exists(function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'available-system-properties'), 0)), function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'system-property'), 1)('xsl:version')\"/></xsl:template>", null, "true 3.0"),
      ok ("function-lookup with a wrong arity", "<xsl:template match=\"/\"><xsl:value-of select=\"exists(function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'system-property'), 3))\"/></xsl:template>", null, "false"));
  }

  /**
   * XSLT 3.0 §3.5.3.? overriding an attribute set of a used package, with
   * xsl:original naming the overridden declaration.
   */
  @Test
  public void compileSpecAttributeSetOverride () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir, "lib.xsl", "<xsl:package name=\"urn:lib\" version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
        "<xsl:attribute-set name=\"s\" visibility=\"public\"><xsl:attribute name=\"a\">lib</xsl:attribute></xsl:attribute-set>\n" +
        "</xsl:package>");
    final String sLibUri = aDir.resolve ("lib.xsl").toUri ().toString ();
    final String sTemplate = "<xsl:output method=\"text\"/>\n" +
                             "<xsl:template match=\"/\"><xsl:variable name=\"e\"><e xsl:use-attribute-sets=\"s\"/></xsl:variable>" +
                             "<xsl:value-of select=\"sort($e/e/@*, (), name#1) ! (name(.) || '=' || .)\"/></xsl:template></xsl:stylesheet>";
    // The overriding attribute set includes the original
    assertEquals ("a=lib b=top",
                  _runWithPackage (aDir,
                                   sLibUri,
                                   "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                                            "<xsl:use-package name=\"urn:lib\"><xsl:override><xsl:attribute-set name=\"s\" use-attribute-sets=\"xsl:original\" visibility=\"public\">" +
                                            "<xsl:attribute name=\"b\">top</xsl:attribute></xsl:attribute-set></xsl:override></xsl:use-package>\n" +
                                            sTemplate));
    // The override replaces the original
    assertEquals ("b=top",
                  _runWithPackage (aDir,
                                   sLibUri,
                                   "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                                            "<xsl:use-package name=\"urn:lib\"><xsl:override><xsl:attribute-set name=\"s\" visibility=\"public\">" +
                                            "<xsl:attribute name=\"b\">top</xsl:attribute></xsl:attribute-set></xsl:override></xsl:use-package>\n" +
                                            sTemplate));
  }

}
