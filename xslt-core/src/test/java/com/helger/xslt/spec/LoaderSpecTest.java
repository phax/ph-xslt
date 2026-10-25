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

import static com.helger.xslt.spec.XsltSpecTestHelper.SPEC_NS;
import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.fail;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt's <code>internal/xslt/loader_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class LoaderSpecTest
{
  private static final String LD_HEAD = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  @NonNull
  private static String _main (@NonNull final String sDecls)
  {
    return "<xsl:stylesheet version=\"3.0\" " +
           SPEC_NS +
           ">" +
           sDecls +
           "<xsl:output method=\"text\"/>\n" +
           "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/*\"/></xsl:template>\n" +
           "<xsl:template match=\"e\"/></xsl:stylesheet>";
  }

  @NonNull
  private static String _xdnMain (@NonNull final String sDecl)
  {
    return "<xsl:stylesheet version=\"3.0\" " +
           SPEC_NS +
           ">" +
           sDecl +
           "<xsl:output method=\"text\"/>\n" +
           "<xsl:template match=\"/\"><xsl:apply-templates select=\"*/*\"/></xsl:template></xsl:stylesheet>";
  }

  /**
   * XSLT 3.0 §3.11 combining stylesheet modules: xsl:include, xsl:import,
   * import precedence, simplified and embedded modules.
   */
  @Test
  public void loaderSpecModules ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "inc.xsl", LD_HEAD + "<xsl:template match=\"b\">inc-b</xsl:template></xsl:stylesheet>");
    specWrite (aDir,
               "imp1.xsl",
               LD_HEAD +
                           "<xsl:import href=\"sub/imp2.xsl\"/><xsl:template match=\"a\">imp1-a[<xsl:apply-imports/>]</xsl:template><xsl:template match=\"c\">imp1-c</xsl:template></xsl:stylesheet>");
    specWrite (aDir,
               "sub/imp2.xsl",
               LD_HEAD +
                               "<xsl:template match=\"a\">imp2-a</xsl:template><xsl:template match=\"c\">imp2-c</xsl:template><xsl:template match=\"d\">imp2-d</xsl:template></xsl:stylesheet>");
    specWrite (aDir,
               "simple.xsl",
               "<out xsl:version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:value-of select=\"count(//*)\"/></out>");
    specWrite (aDir,
               "host.xml",
               "<doc><xsl:stylesheet id=\"emb\" version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"e\">embedded</xsl:template></xsl:stylesheet></doc>");
    specWrite (aDir, "notxslt.xml", "<doc/>");
    specWrite (aDir, "cyc1.xsl", LD_HEAD + "<xsl:include href=\"cyc2.xsl\"/></xsl:stylesheet>");
    specWrite (aDir, "cyc2.xsl", LD_HEAD + "<xsl:include href=\"cyc1.xsl\"/></xsl:stylesheet>");
    specWrite (aDir, "icyc1.xsl", LD_HEAD + "<xsl:import href=\"icyc2.xsl\"/></xsl:stylesheet>");
    specWrite (aDir, "icyc2.xsl", LD_HEAD + "<xsl:import href=\"icyc1.xsl\"/></xsl:stylesheet>");
    specWrite (aDir,
               "preserve.xsl",
               "<xsl:stylesheet version=\"3.0\" input-type-annotations=\"preserve\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");

    final String sSrc = "<r><a/><b/><c/><d/><e/></r>";
    specRunAll (aDir,
                ok ("include", _main ("<xsl:include href=\"inc.xsl\"/>"), sSrc, "inc-b"),
                // The importing module wins; xsl:apply-imports reaches the next
                // lower one.
                ok ("import chain and apply-imports", _main ("<xsl:import href=\"imp1.xsl\"/>"), sSrc, "imp1-a[imp2-a]imp1-cimp2-d"),
                ok ("importing module overrides",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                  SPEC_NS +
                                                  "><xsl:import href=\"imp1.xsl\"/><xsl:output method=\"text\"/>\n" +
                                                  "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/*\"/></xsl:template>\n" +
                                                  "<xsl:template match=\"c\">MAIN</xsl:template><xsl:template match=\"b | e\"/></xsl:stylesheet>",
                    sSrc,
                    "imp1-a[imp2-a]MAINimp2-d"),
                // §3.8: a simplified module is a stylesheet with one template for
                // "/".
                ok ("import a simplified module",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                  SPEC_NS +
                                                  "><xsl:import href=\"simple.xsl\"/><xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/></xsl:stylesheet>",
                    sSrc,
                    "<out>6</out>"),
                // §3.12: an embedded module named by a fragment identifier.
                ok ("include an embedded module",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                  SPEC_NS +
                                                  "><xsl:include href=\"host.xml#emb\"/><xsl:output method=\"text\"/>\n" +
                                                  "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/e\"/></xsl:template></xsl:stylesheet>",
                    sSrc,
                    "embedded"),
                // XTSE0165: the resource is not a stylesheet module / cannot be
                // retrieved.
                err ("not a stylesheet", _main ("<xsl:include href=\"notxslt.xml\"/>"), null, "XTSE0165"),
                err ("fragment not found", _main ("<xsl:include href=\"host.xml#nope\"/>"), null, "XTSE0165"),
                // XTSE0265: conflicting input-type-annotations.
                err ("conflicting input-type-annotations",
                     "<xsl:stylesheet version=\"3.0\" input-type-annotations=\"strip\" " +
                                                           SPEC_NS +
                                                           "><xsl:include href=\"preserve.xsl\"/>" +
                                                           "<xsl:template match=\"/\">x</xsl:template></xsl:stylesheet>",
                     null,
                     "XTSE0265"));

    // §5.1.2: xpath-default-namespace is a standard attribute whose scope is
    // the element it is on and its descendants - on an included or imported
    // module's root element it governs that module's expressions and
    // patterns, not the including module's.
    specWrite (aDir, "xdn-inc.xsl", """
        <xsl:stylesheet version="3.0" xpath-default-namespace="urn:x" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
        <xsl:template match="item">[<xsl:value-of select="count(//item)"/>]</xsl:template></xsl:stylesheet>""");
    final String sXdnSrc = "<r xmlns=\"urn:x\"><item/><item/></r>";
    specRunAll (aDir,
                ok ("xpath-default-namespace on an included module",
                    _xdnMain ("<xsl:include href=\"xdn-inc.xsl\"/>"),
                    sXdnSrc,
                    "[2][2]"),
                ok ("xpath-default-namespace on an imported module",
                    _xdnMain ("<xsl:import href=\"xdn-inc.xsl\"/>"),
                    sXdnSrc,
                    "[2][2]"));

    // Skipped in go-xslt as known bugs there
    specRunAll (aDir,
                // §3.11.1
                err ("missing module", _main ("<xsl:include href=\"missing.xsl\"/>"), null, "XTSE0165"),
                // §3.11.2
                err ("include cycle", _main ("<xsl:include href=\"cyc1.xsl\"/>"), null, "XTSE0180"),
                // §3.11.3
                err ("import cycle", _main ("<xsl:import href=\"icyc1.xsl\"/>"), null, "XTSE0210"),
                err ("import after a declaration",
                     "<xsl:stylesheet version=\"3.0\" " +
                                                   SPEC_NS +
                                                   "><xsl:template match=\"/\">x</xsl:template><xsl:import href=\"inc.xsl\"/></xsl:stylesheet>",
                     null,
                     "XTSE0200"));
  }

  /** XSLT 3.0 §3.5.2 xsl:use-package against host-supplied packages. */
  @Test
  public void loaderSpecUsePackage ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "lib.xsl", """
        <xsl:package name="urn:lib" package-version="1.0" version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:l="urn:lib">
        <xsl:function name="l:twice" visibility="public"><xsl:param name="n"/><xsl:sequence select="$n * 2"/></xsl:function>
        </xsl:package>""");
    specWrite (aDir,
               "a.xsl",
               "<xsl:package name=\"urn:a\" version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:use-package name=\"urn:b\"/></xsl:package>");
    specWrite (aDir,
               "b.xsl",
               "<xsl:package name=\"urn:b\" version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:use-package name=\"urn:a\"/></xsl:package>");
    specWrite (aDir, "notpkg.xsl", LD_HEAD + "</xsl:stylesheet>");
    final String [] [] aPkgs = { { "urn:lib", "lib.xsl" },
                                 { "urn:a", "a.xsl" },
                                 { "urn:b", "b.xsl" },
                                 { "urn:notpkg", "notpkg.xsl" } };
    final String sTopStart = "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:l=\"urn:lib\">";
    final String sTopEnd = "<xsl:output method=\"text\"/>\n" +
                           "<xsl:template match=\"/\"><xsl:value-of select=\"l:twice(21)\"/></xsl:template></xsl:stylesheet>";
    final String [] [] aCases = { { "use a package", "<xsl:use-package name=\"urn:lib\" package-version=\"1.0\"/>", "42", null },
                                  { "accept inside use-package",
                                    "<xsl:use-package name=\"urn:lib\"><xsl:accept component=\"function\" names=\"*\" visibility=\"public\"/></xsl:use-package>",
                                    "42",
                                    null },
                                  { "use-package without a name", "<xsl:use-package/>", null, "XTSE0010" },
                                  { "template inside use-package",
                                    "<xsl:use-package name=\"urn:lib\"><xsl:template name=\"t\"/></xsl:use-package>",
                                    null,
                                    "XTSE0010" },
                                  { "foreign element inside use-package",
                                    "<xsl:use-package name=\"urn:lib\"><f:x/></xsl:use-package>",
                                    null,
                                    "XTSE0010" },
                                  // XTSE3005: packages that use each other.
                                  { "recursive packages", "<xsl:use-package name=\"urn:a\"/>", null, "XTSE3005" },
                                  { "not a package module", "<xsl:use-package name=\"urn:notpkg\"/>", null, "XTSE0165" },
                                  // XTSE3000: no package of that name/version is
                                  // available.
                                  { "unavailable version",
                                    "<xsl:use-package name=\"urn:lib\" package-version=\"2.0\"/>",
                                    null,
                                    "XTSE3000" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final XsltCompileOptions aOptions = new XsltCompileOptions ();
      aOptions.setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ());
      aOptions.setResolver (FileResourceResolver.INSTANCE);
      for (final String [] aPkg : aPkgs)
        aOptions.addPackage (new XsltCompileOptions.PackageSource (null,
                                                                   null,
                                                                   aDir.resolve (aPkg[1]).toUri ().toString (),
                                                                   aPkg[0],
                                                                   null));
      try
      {
        final XsltExecutable aExec = XsltCompiler.compile (sTopStart + aCase[1] + sTopEnd, aOptions);
        final String sOut = aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<doc/>")))
                                 .principalSerialized ();
        if (aCase[3] != null)
          aErrors.add (aCase[0] + ": want " + aCase[3] + ", got output '" + sOut + "'");
        else
          if (!sOut.equals (aCase[2]))
            aErrors.add (aCase[0] + ": got '" + sOut + "' want '" + aCase[2] + "'");
      }
      catch (final XPathException ex)
      {
        if (aCase[3] == null || !aCase[3].equals (ex.getCode ()))
          aErrors.add (aCase[0] + ": want " + aCase[3] + ", got " + ex.getCode () + ": " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }
}
