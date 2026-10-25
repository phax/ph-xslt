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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt's <code>internal/xslt/use_package_test.go</code>: tests for
 * xsl:use-package, xsl:accept and xsl:override (XSLT 3.0 §3.5), driven end to
 * end: library packages are written to a temporary directory and offered to
 * the compile as host-supplied packages. go-xslt checks references to
 * undeclared functions and variables only for "an error" because its engine
 * had no code for them; here the codes XPST0017 / XPST0008 are required.
 *
 * @author Philip Helger
 */
public final class UsePackageSpecTest
{
  static final String PKA_NS = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

  /** pkALib is a library package with one public component of every kind. */
  static final String PKA_LIB = pkAPkg ("urn:pkA:lib", "1.0", """
      <xsl:function name="l:f" visibility="public" as="xs:string">
        <xsl:param name="x" as="xs:string"/>
        <xsl:sequence select="concat('f:', $x, l:priv())"/>
      </xsl:function>
      <xsl:function name="l:priv" visibility="private"><xsl:sequence select="'!'"/></xsl:function>
      <xsl:template name="l:t" visibility="public"><xsl:text>t</xsl:text></xsl:template>
      <xsl:template name="l:tpriv"><xsl:text>tp</xsl:text></xsl:template>
      <xsl:variable name="l:v" visibility="public" select="'v'"/>
      <xsl:variable name="l:vpriv" select="'vp'"/>
      <xsl:param name="l:p" select="'pdef'"/>
      <xsl:attribute-set name="l:as" visibility="public"><xsl:attribute name="a" select="1"/></xsl:attribute-set>
      <xsl:attribute-set name="l:aspriv"><xsl:attribute name="b" select="2"/></xsl:attribute-set>
      <xsl:mode name="l:m" visibility="public"/>
      <xsl:template match="*" mode="l:m">m:<xsl:value-of select="local-name()"/></xsl:template>""");

  /**
   * The library the override tests replace parts of. Its public template
   * l:callf calls l:f, so an override of l:f must be seen from inside the
   * library too (§3.5.3.3).
   */
  static final String PKA_OV_LIB = pkAPkg ("urn:pkA:lib", "", """
      <xsl:function name="l:f" visibility="public" as="xs:string">
        <xsl:param name="x" as="xs:string"/>
        <xsl:sequence select="concat('lib:', $x)"/>
      </xsl:function>
      <xsl:function name="l:fin" visibility="final"><xsl:sequence select="1"/></xsl:function>
      <xsl:template name="l:callf" visibility="public"><xsl:value-of select="l:f('x')"/></xsl:template>
      <xsl:template name="l:t" visibility="public" as="xs:string">
        <xsl:param name="p" as="xs:string" required="yes"/>
        <xsl:param name="tun" tunnel="yes"/>
        <xsl:sequence select="'T' || $p"/>
      </xsl:template>
      <xsl:variable name="l:v" visibility="public" as="xs:string" select="'lib'"/>
      <xsl:attribute-set name="l:as" visibility="public"><xsl:attribute name="a" select="1"/></xsl:attribute-set>
      <xsl:mode name="l:m" visibility="public"/>
      <xsl:mode name="l:fm" visibility="final"/>
      <xsl:template match="*" mode="l:m">lib</xsl:template>
      <xsl:template match="*" mode="l:fm">fm</xsl:template>""");

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * @param sName
   *        Package name.
   * @param sVer
   *        package-version, omitted when empty.
   * @param sBody
   *        Package body.
   * @return The text of the xsl:package; the prefix l is bound to urn:pkA:l.
   */
  @NonNull
  static String pkAPkg (@NonNull final String sName, @NonNull final String sVer, @NonNull final String sBody)
  {
    final String v = sVer.isEmpty () ? "" : " package-version=\"" + sVer + "\"";
    return "<xsl:package name=\"" + sName + "\"" + v + " version=\"3.0\" " + PKA_NS + " xmlns:l=\"urn:pkA:l\">" + sBody + "</xsl:package>";
  }

  /**
   * @param sBody
   *        Body.
   * @return A principal stylesheet module with text output.
   */
  @NonNull
  static String pkAMain (@NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"3.0\" " +
           PKA_NS +
           " xmlns:l=\"urn:pkA:l\" exclude-result-prefixes=\"#all\">" +
           "<xsl:output method=\"text\"/>" +
           sBody +
           "</xsl:stylesheet>";
  }

  /**
   * Write the packages into a fresh directory, register each by file only (its
   * name and version come from its own xsl:package element) and compile main
   * as if read from that directory.
   *
   * @param aTemp
   *        Temporary folder rule.
   * @param sMain
   *        Principal module.
   * @param aPkgs
   *        Package texts.
   * @param aExtra
   *        Pairs of extra file names and texts written next to the principal
   *        module.
   * @return The compiled stylesheet.
   */
  @NonNull
  static XsltExecutable pkACompile (@NonNull final TemporaryFolder aTemp,
                                    @NonNull final String sMain,
                                    @NonNull final String [] aPkgs,
                                    @NonNull final String... aExtra)
  {
    final Path aDir;
    try
    {
      aDir = aTemp.newFolder ().toPath ();
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ());
    aOptions.setResolver (FileResourceResolver.INSTANCE);
    for (int i = 0; i < aPkgs.length; ++i)
    {
      specWrite (aDir, "pkg" + i + ".xsl", aPkgs[i]);
      aOptions.addPackage (new XsltCompileOptions.PackageSource (null, null, aDir.resolve ("pkg" + i + ".xsl").toUri ().toString ()));
    }
    for (int i = 0; i < aExtra.length; i += 2)
      specWrite (aDir, aExtra[i], aExtra[i + 1]);
    return XsltCompiler.compile (sMain, aOptions);
  }

  @NonNull
  static String pkARun (@NonNull final TemporaryFolder aTemp,
                        @NonNull final String sMain,
                        @NonNull final String [] aPkgs,
                        @NonNull final String... aExtra)
  {
    return pkACompile (aTemp, sMain, aPkgs, aExtra).transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<r><i>1</i><i>2</i></r>")))
                                                   .principalSerialized ();
  }

  static void pkAWant (@NonNull final TemporaryFolder aTemp,
                       @NonNull final String sName,
                       @NonNull final String sMain,
                       @NonNull final String [] aPkgs,
                       @NonNull final String sWant)
  {
    try
    {
      assertEquals (sName, sWant, pkARun (aTemp, sMain, aPkgs));
    }
    catch (final XPathException ex)
    {
      fail (sName + ": unexpected error " + ex.getCode () + ": " + ex.getMessage ());
    }
  }

  static void pkAWantErr (@NonNull final TemporaryFolder aTemp,
                          @NonNull final String sName,
                          @NonNull final String sMain,
                          @NonNull final String [] aPkgs,
                          @Nullable final String sCode,
                          @NonNull final String... aExtra)
  {
    try
    {
      final String sOut = pkARun (aTemp, sMain, aPkgs, aExtra);
      fail (sName + ": no error, want " + sCode + " (output '" + sOut + "')");
    }
    catch (final XPathException ex)
    {
      if (sCode != null && !sCode.equals (ex.getCode ()))
        fail (sName + ": got " + ex.getCode () + " (" + ex.getMessage () + "), want " + sCode);
    }
  }

  @NonNull
  static String [] pkgs (@NonNull final String... a)
  {
    return a;
  }

  /**
   * Every public component of a used package - and its global parameters,
   * which are always public (§3.5.3.1) - is accepted and usable from the using
   * package; the library's own reference to its private function still works.
   */
  @Test
  public void usePackagePublicComponents ()
  {
    final String sMain = pkAMain ("""
        <xsl:use-package name="urn:pkA:lib" package-version="1.0"/>
        <xsl:template match="/">
          <xsl:value-of select="l:f('a'), $l:v, $l:p" separator="|"/>|<xsl:call-template name="l:t"/>|<xsl:apply-templates select="r" mode="l:m"/>|<xsl:variable name="e"><e xsl:use-attribute-sets="l:as"/></xsl:variable><xsl:value-of select="$e/e/@a"/>
        </xsl:template>""");
    pkAWant (m_aTempDir, "public components", sMain, pkgs (PKA_LIB), "f:a!|v|pdef|t|m:r|1");
  }

  /**
   * A component that is private in the used package becomes hidden in the
   * using package and cannot be referenced (§3.5.3.2).
   */
  @Test
  public void usePackageHiddenComponents ()
  {
    final String sUse = "<xsl:use-package name=\"urn:pkA:lib\"/>";
    pkAWantErr (m_aTempDir,
                "private function",
                pkAMain (sUse + "<xsl:template match=\"/\"><xsl:value-of select=\"l:priv()\"/></xsl:template>"),
                pkgs (PKA_LIB),
                "XPST0017");
    pkAWantErr (m_aTempDir,
                "private variable",
                pkAMain (sUse + "<xsl:template match=\"/\"><xsl:value-of select=\"$l:vpriv\"/></xsl:template>"),
                pkgs (PKA_LIB),
                "XPST0008");
    pkAWantErr (m_aTempDir,
                "private template",
                pkAMain (sUse + "<xsl:template match=\"/\"><xsl:call-template name=\"l:tpriv\"/></xsl:template>"),
                pkgs (PKA_LIB),
                "XTSE0650");
    pkAWantErr (m_aTempDir,
                "private attribute set",
                pkAMain (sUse + "<xsl:template match=\"/\"><e xsl:use-attribute-sets=\"l:aspriv\"/></xsl:template>"),
                pkgs (PKA_LIB),
                "XTSE0710");
    pkAWantErr (m_aTempDir, "accepted as hidden", pkAMain ("""
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="function" names="l:f#1" visibility="hidden"/>
        </xsl:use-package>
        <xsl:template match="/"><xsl:value-of select="l:f('a')"/></xsl:template>"""), pkgs (PKA_LIB), "XPST0017");
  }

  /**
   * Among the xsl:accept elements that match a component, an explicit name
   * beats a partial wildcard, which beats "*" (§3.5.3.2).
   */
  @Test
  public void usePackageAcceptPrecedence ()
  {
    final String [] aLib = pkgs (pkAPkg ("urn:pkA:lib", "", """
        <xsl:template name="l:a" visibility="public">a</xsl:template>
        <xsl:template name="l:b" visibility="public">b</xsl:template>
        <xsl:template name="nons" visibility="public">n</xsl:template>"""));
    final String sAccepts = """
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="template" names="l:b" visibility="hidden"/>
          <xsl:accept component="template" names="l:*" visibility="private"/>
          <xsl:accept component="*" names="*" visibility="hidden"/>
        </xsl:use-package>""";
    pkAWant (m_aTempDir, "partial beats *", pkAMain (sAccepts + _call ("l:a")), aLib, "a");
    pkAWantErr (m_aTempDir, "explicit beats partial", pkAMain (sAccepts + _call ("l:b")), aLib, "XTSE0650");
    pkAWantErr (m_aTempDir, "* applies", pkAMain (sAccepts + _call ("nons")), aLib, "XTSE0650");

    // Q{}* selects exactly the names in no namespace; *:local selects by local
    // name in any namespace.
    final String sNoNS = """
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="template" names="Q{}*" visibility="hidden"/>
          <xsl:accept component="template" names="*:b" visibility="hidden"/>
        </xsl:use-package>""";
    pkAWant (m_aTempDir, "Q{}* keeps namespaced", pkAMain (sNoNS + _call ("l:a")), aLib, "a");
    pkAWantErr (m_aTempDir, "Q{}* hides no-namespace", pkAMain (sNoNS + _call ("nons")), aLib, "XTSE0650");
    pkAWantErr (m_aTempDir, "*:b hides l:b", pkAMain (sNoNS + _call ("l:b")), aLib, "XTSE0650");
    // Q{uri}* and Q{uri}local forms, and a component list naming two kinds.
    final String sQForms = """
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="template" names="Q{urn:pkA:l}*" visibility="hidden"/>
          <xsl:accept component="function template" names="Q{urn:pkA:l}a" visibility="final"/>
        </xsl:use-package>""";
    pkAWant (m_aTempDir, "Q{uri}local beats Q{uri}*", pkAMain (sQForms + _call ("l:a")), aLib, "a");
    pkAWantErr (m_aTempDir, "Q{uri}* hides", pkAMain (sQForms + _call ("l:b")), aLib, "XTSE0650");
    // An accept for another component kind does not apply.
    final String sOther = """
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="function" names="*" visibility="hidden"/>
        </xsl:use-package>""";
    pkAWant (m_aTempDir, "other kind", pkAMain (sOther + _call ("nons")), aLib, "n");
  }

  @NonNull
  private static String _call (@NonNull final String sName)
  {
    return "<xsl:template match=\"/\"><xsl:call-template name=\"" + sName + "\"/></xsl:template>";
  }

  @NonNull
  private static String _useLib (@NonNull final String sChildren)
  {
    return pkAMain ("<xsl:use-package name=\"urn:pkA:lib\">" + sChildren + "</xsl:use-package><xsl:template match=\"/\"/>");
  }

  /** The static errors of §3.5.3.2. */
  @Test
  public void usePackageAcceptErrors ()
  {
    final String [] aLib = pkgs (pkAPkg ("urn:pkA:lib", "", """
        <xsl:function name="l:f" visibility="public"><xsl:sequence select="'f'"/></xsl:function>
        <xsl:function name="l:fin" visibility="final"><xsl:sequence select="'fin'"/></xsl:function>"""));
    // XTSE3030: a non-wildcard token matches no component.
    pkAWantErr (m_aTempDir,
                "XTSE3030",
                _useLib ("<xsl:accept component=\"function\" names=\"l:nope#0\" visibility=\"private\"/>"),
                aLib,
                "XTSE3030");
    // XTSE3040: a final component cannot be accepted as public.
    pkAWantErr (m_aTempDir,
                "XTSE3040 final as public",
                _useLib ("<xsl:accept component=\"function\" names=\"l:fin#0\" visibility=\"public\"/>"),
                aLib,
                "XTSE3040");
    // XTSE3051: an explicit accept naming a component the same
    // xsl:use-package overrides.
    pkAWantErr (m_aTempDir, "XTSE3051", _useLib ("""
        <xsl:accept component="function" names="l:f#0" visibility="private"/>
        <xsl:override><xsl:function name="l:f" visibility="public"><xsl:sequence select="'o'"/></xsl:function></xsl:override>"""), aLib, "XTSE3051");

    // With a wildcard, an incompatible visibility is not an error: the accept
    // is treated as not matching the final component.
    pkAWant (m_aTempDir, "wildcard accept of final as public", pkAMain ("""
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="function" names="*" visibility="public"/>
        </xsl:use-package>
        <xsl:template match="/"><xsl:value-of select="l:fin(), l:f()"/></xsl:template>"""), aLib, "fin f");
    // A wildcard that matches nothing is not XTSE3030.
    pkAWant (m_aTempDir, "unmatched wildcard accept", pkAMain ("""
        <xsl:use-package name="urn:pkA:lib">
          <xsl:accept component="template" names="l:*" visibility="private"/>
        </xsl:use-package>
        <xsl:template match="/"><xsl:value-of select="l:f()"/></xsl:template>"""), aLib, "f");
  }

  /**
   * XTSE3050 when two used packages make homonymous components visible;
   * lowering one of them to hidden resolves the conflict. A declaration of the
   * using package homonymous with an accepted component is a static error too.
   */
  @Test
  public void usePackageHomonyms ()
  {
    final String a = pkAPkg ("urn:pkA:a", "", "<xsl:function name=\"l:f\" visibility=\"public\"><xsl:sequence select=\"'A'\"/></xsl:function>");
    final String b = pkAPkg ("urn:pkA:b", "", "<xsl:function name=\"l:f\" visibility=\"public\"><xsl:sequence select=\"'B'\"/></xsl:function>");
    final String sCall = "<xsl:template match=\"/\"><xsl:value-of select=\"l:f()\"/></xsl:template>";
    pkAWantErr (m_aTempDir,
                "two packages",
                pkAMain ("<xsl:use-package name=\"urn:pkA:a\"/><xsl:use-package name=\"urn:pkA:b\"/>" + sCall),
                pkgs (a, b),
                "XTSE3050");
    assertNotNull (pkACompile (m_aTempDir, pkAMain ("""
        <xsl:use-package name="urn:pkA:a"/>
        <xsl:use-package name="urn:pkA:b"><xsl:accept component="function" names="l:f#0" visibility="hidden"/></xsl:use-package>""" + sCall),
                               pkgs (a, b)));
    pkAWantErr (m_aTempDir, "local homonym", pkAMain ("""
        <xsl:use-package name="urn:pkA:a"/>
        <xsl:function name="l:f"><xsl:sequence select="'own'"/></xsl:function>""" + sCall), pkgs (a), null);
  }

  /**
   * With l:f accepted from package a and lowered to hidden from package b, a
   * reference to l:f in the using package can only mean a's.
   */
  @Test
  public void usePackageHiddenHomonym ()
  {
    final String a = pkAPkg ("urn:pkA:a", "", "<xsl:function name=\"l:f\" visibility=\"public\"><xsl:sequence select=\"'A'\"/></xsl:function>");
    final String b = pkAPkg ("urn:pkA:b", "", "<xsl:function name=\"l:f\" visibility=\"public\"><xsl:sequence select=\"'B'\"/></xsl:function>");
    pkAWant (m_aTempDir, "one hidden", pkAMain ("""
        <xsl:use-package name="urn:pkA:a"/>
        <xsl:use-package name="urn:pkA:b"><xsl:accept component="function" names="l:f#0" visibility="hidden"/></xsl:use-package>
        <xsl:template match="/"><xsl:value-of select="l:f()"/></xsl:template>"""), pkgs (a, b), "A");
  }

  @NonNull
  private static String _ver (@NonNull final String v)
  {
    return pkAPkg ("urn:pkA:v", v, "<xsl:function name=\"l:ver\" visibility=\"public\"><xsl:sequence select=\"'" + v + "'\"/></xsl:function>");
  }

  @NonNull
  private static String _runVer (@NonNull final String sSpec)
  {
    return pkAMain ("<xsl:use-package name=\"urn:pkA:v\" package-version=\"" +
                    sSpec +
                    "\"/>\n<xsl:template match=\"/\"><xsl:value-of select=\"l:ver()\"/></xsl:template>");
  }

  /**
   * xsl:use-package selects by name and by the package-version ranges (§3.5.1,
   * §3.5.2); no match is XTSE3000.
   */
  @Test
  public void usePackageVersions ()
  {
    final String [] aPkgs = pkgs (_ver ("1.0"), _ver ("2.5.1"));
    // 1 and 1.0 are the same version
    pkAWant (m_aTempDir, "exact 1", _runVer ("1"), aPkgs, "1.0");
    pkAWant (m_aTempDir, "range", _runVer ("2 to 3"), aPkgs, "2.5.1");
    pkAWant (m_aTempDir, "prefix", _runVer ("2.5.*"), aPkgs, "2.5.1");
    pkAWant (m_aTempDir, "upper bound", _runVer ("to 1.9"), aPkgs, "1.0");
    pkAWantErr (m_aTempDir, "no version", _runVer ("3+"), aPkgs, "XTSE3000");
    pkAWantErr (m_aTempDir,
                "no name",
                pkAMain ("<xsl:use-package name=\"urn:pkA:other\"/><xsl:template match=\"/\"/>"),
                aPkgs,
                "XTSE3000");
    // A package that declares no package-version has version 1.0 (§3.5.1).
    final String sNov = pkAPkg ("urn:pkA:nov", "", "<xsl:function name=\"l:g\" visibility=\"public\"><xsl:sequence select=\"'g'\"/></xsl:function>");
    pkAWant (m_aTempDir, "default version", pkAMain ("""
        <xsl:use-package name="urn:pkA:nov" package-version="1.0.0"/>
        <xsl:template match="/"><xsl:value-of select="l:g()"/></xsl:template>"""), pkgs (sNov), "g");
  }

  /**
   * A host registration that names the package and version itself is used as
   * given.
   */
  @Test
  public void usePackageHostIdentity () throws IOException
  {
    final Path aDir = m_aTempDir.newFolder ().toPath ();
    specWrite (aDir,
               "lib.xsl",
               pkAPkg ("urn:pkA:file-name", "9.9", "<xsl:function name=\"l:g\" visibility=\"public\"><xsl:sequence select=\"'g'\"/></xsl:function>"));
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ());
    aOptions.setResolver (FileResourceResolver.INSTANCE);
    aOptions.addPackage (new XsltCompileOptions.PackageSource (null,
                                                               null,
                                                               aDir.resolve ("lib.xsl").toUri ().toString (),
                                                               "urn:pkA:host-name",
                                                               "3.0"));
    final XsltExecutable aExec = XsltCompiler.compile (pkAMain ("""
        <xsl:use-package name="urn:pkA:host-name" package-version="3"/>
        <xsl:template match="/"><xsl:value-of select="l:g()"/></xsl:template>"""), aOptions);
    assertEquals ("g",
                  aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<r/>"))).principalSerialized ());
  }

  /**
   * XTSE3005 (a package used recursively), XTSE3008 (xsl:use-package reached
   * through xsl:import), XTSE0010 (bad content of xsl:use-package).
   */
  @Test
  public void usePackageStructureErrors ()
  {
    final String a = pkAPkg ("urn:pkA:a", "", "<xsl:use-package name=\"urn:pkA:b\"/>");
    final String b = pkAPkg ("urn:pkA:b", "", "<xsl:use-package name=\"urn:pkA:a\"/>");
    pkAWantErr (m_aTempDir,
                "XTSE3005",
                pkAMain ("<xsl:use-package name=\"urn:pkA:a\"/><xsl:template match=\"/\"/>"),
                pkgs (a, b),
                "XTSE3005");

    final String [] aLib = pkgs (pkAPkg ("urn:pkA:lib", "", "<xsl:function name=\"l:g\" visibility=\"public\"><xsl:sequence select=\"'g'\"/></xsl:function>"));
    final String sMod = "<xsl:stylesheet version=\"3.0\" " + PKA_NS + "><xsl:use-package name=\"urn:pkA:lib\"/></xsl:stylesheet>";
    pkAWantErr (m_aTempDir,
                "XTSE3008",
                pkAMain ("<xsl:import href=\"mod.xsl\"/><xsl:template match=\"/\"/>"),
                aLib,
                "XTSE3008",
                "mod.xsl",
                sMod);
    // Through xsl:include it is allowed (§3.5.2).
    assertEquals ("g",
                  pkARun (m_aTempDir,
                          pkAMain ("<xsl:include href=\"mod.xsl\"/><xsl:template match=\"/\"><xsl:value-of select=\"l:g()\"/></xsl:template>"),
                          aLib,
                          "mod.xsl",
                          sMod));
    pkAWantErr (m_aTempDir, "no name", pkAMain ("<xsl:use-package/><xsl:template match=\"/\"/>"), aLib, "XTSE0010");
    pkAWantErr (m_aTempDir,
                "bad child",
                pkAMain ("<xsl:use-package name=\"urn:pkA:lib\"><xsl:variable name=\"x\"/></xsl:use-package><xsl:template match=\"/\"/>"),
                aLib,
                "XTSE0010");
  }

  /**
   * Overriding a function, a named template, a variable, an attribute set and
   * adding a template rule to a public mode, each reaching the overridden
   * component through xsl:original / next-match (§3.5.3.3, §3.5.3.4).
   */
  @Test
  public void usePackageOverride ()
  {
    final String sMain = pkAMain ("""
        <xsl:use-package name="urn:pkA:lib">
          <xsl:override>
            <xsl:function name="l:f" visibility="public" as="xs:string">
              <xsl:param name="x" as="xs:string"/>
              <xsl:sequence select="concat('ovr(', xsl:original($x), ')')"/>
            </xsl:function>
            <xsl:template name="l:t" visibility="public" as="xs:string">
              <xsl:param name="p" as="xs:string" required="yes"/>
              <xsl:param name="extra" required="no"/>
              <xsl:variable name="o" as="xs:string"><xsl:call-template name="xsl:original"><xsl:with-param name="p" select="$p"/></xsl:call-template></xsl:variable>
              <xsl:sequence select="'O' || $o"/>
            </xsl:template>
            <xsl:variable name="l:v" visibility="public" as="xs:string" select="'o-' || $xsl:original"/>
            <xsl:attribute-set name="l:as" visibility="public" use-attribute-sets="xsl:original"><xsl:attribute name="b" select="2"/></xsl:attribute-set>
            <xsl:template match="r" mode="l:m">ovr+<xsl:next-match/></xsl:template>
          </xsl:override>
        </xsl:use-package>
        <xsl:template match="/">
          <xsl:value-of select="l:f('a')"/>|<xsl:call-template name="l:callf"/>|<xsl:call-template name="l:t"><xsl:with-param name="p" select="'p'"/></xsl:call-template>|<xsl:value-of select="$l:v"/>|<xsl:apply-templates select="r" mode="l:m"/>|<xsl:variable name="e"><e xsl:use-attribute-sets="l:as"/></xsl:variable><xsl:value-of select="$e/e/@*" separator=","/>
        </xsl:template>""");
    pkAWant (m_aTempDir, "override", sMain, pkgs (PKA_OV_LIB), "ovr(lib:a)|ovr(lib:x)|OTp|o-lib|ovr+lib|1,2");
  }

  @NonNull
  private static String _ov (@NonNull final String sDecls)
  {
    return pkAMain ("<xsl:use-package name=\"urn:pkA:lib\"><xsl:override>" + sDecls + "</xsl:override></xsl:use-package><xsl:template match=\"/\"/>");
  }

  @NonNull
  private static String _fn (@NonNull final String sAttrs, @NonNull final String sParams, @NonNull final String sBody)
  {
    return "<xsl:function name=\"l:f\" visibility=\"public\"" +
           sAttrs +
           ">" +
           sParams +
           "<xsl:sequence select=\"" +
           sBody +
           "\"/></xsl:function>";
  }

  @NonNull
  private static String _tmpl (@NonNull final String sAttrs, @NonNull final String sParams)
  {
    return "<xsl:template name=\"l:t\" visibility=\"public\"" + sAttrs + ">" + sParams + "<xsl:sequence select=\"'x'\"/></xsl:template>";
  }

  /**
   * The static errors of §3.5.3.3 and the template-rule rules XTSE3440 /
   * XTSE3460.
   */
  @Test
  public void usePackageOverrideErrors ()
  {
    final String sStrParam = "<xsl:param name=\"x\" as=\"xs:string\"/>";
    final String sReqP = "<xsl:param name=\"p\" as=\"xs:string\" required=\"yes\"/>";
    final String [] [] aCases = { { "XTSE3058 unknown function", "<xsl:function name=\"l:nope\"><xsl:sequence select=\"1\"/></xsl:function>", "XTSE3058" },
                                  { "XTSE3058 other arity", _fn (" as=\"xs:string\"", "", "''"), "XTSE3058" },
                                  { "XTSE3060 final function", "<xsl:function name=\"l:fin\"><xsl:sequence select=\"2\"/></xsl:function>", "XTSE3060" },
                                  { "XTSE3060 rule in final mode", "<xsl:template match=\"r\" mode=\"l:fm\">x</xsl:template>", "XTSE3060" },
                                  { "XTSE3070 result type", _fn (" as=\"xs:integer\"", sStrParam, "1"), "XTSE3070" },
                                  { "XTSE3070 param type",
                                    _fn (" as=\"xs:string\"", "<xsl:param name=\"x\" as=\"xs:integer\"/>", "''"),
                                    "XTSE3070" },
                                  { "XTSE3070 new-each-time", _fn (" as=\"xs:string\" new-each-time=\"yes\"", sStrParam, "''"), "XTSE3070" },
                                  { "XTSE3070 template result",
                                    _tmpl (" as=\"xs:integer\"", sReqP + "<xsl:param name=\"tun\" tunnel=\"yes\"/>"),
                                    "XTSE3070" },
                                  { "XTSE3070 template lacks param", _tmpl (" as=\"xs:string\"", ""), "XTSE3070" },
                                  { "XTSE3070 template param type",
                                    _tmpl (" as=\"xs:string\"", "<xsl:param name=\"p\" as=\"xs:integer\" required=\"yes\"/>"),
                                    "XTSE3070" },
                                  { "XTSE3070 template param required",
                                    _tmpl (" as=\"xs:string\"", "<xsl:param name=\"p\" as=\"xs:string\"/>"),
                                    "XTSE3070" },
                                  { "XTSE3070 template param tunnel",
                                    _tmpl (" as=\"xs:string\"", sReqP + "<xsl:param name=\"tun\" tunnel=\"no\"/>"),
                                    "XTSE3070" },
                                  { "XTSE3070 template extra required",
                                    _tmpl (" as=\"xs:string\"", sReqP + "<xsl:param name=\"q\" required=\"yes\"/>"),
                                    "XTSE3070" },
                                  { "XTSE3070 template context item",
                                    _tmpl (" as=\"xs:string\"", "<xsl:context-item use=\"required\"/>" + sReqP),
                                    "XTSE3070" },
                                  { "XTSE3070 variable type",
                                    "<xsl:variable name=\"l:v\" visibility=\"public\" as=\"xs:integer\" select=\"1\"/>",
                                    "XTSE3070" },
                                  { "XTSE3440 no mode", "<xsl:template match=\"r\">x</xsl:template>", "XTSE3440" },
                                  { "XTSE3440 #all", "<xsl:template match=\"r\" mode=\"#all\">x</xsl:template>", "XTSE3440" },
                                  { "XTSE3440 #unnamed", "<xsl:template match=\"r\" mode=\"#unnamed\">x</xsl:template>", "XTSE3440" },
                                  { "XTSE3440 #default", "<xsl:template match=\"r\" mode=\"#default\">x</xsl:template>", "XTSE3440" },
                                  { "XTSE3460 apply-imports",
                                    "<xsl:template match=\"r\" mode=\"l:m\"><xsl:apply-imports/></xsl:template>",
                                    "XTSE3460" } };
    for (final String [] aCase : aCases)
      pkAWantErr (m_aTempDir, aCase[0], _ov (aCase[1]), pkgs (PKA_OV_LIB), aCase[2]);

    // Controls: a signature repeated verbatim, an omitted tunnel parameter,
    // and a template rule whose mode comes from [xsl:]default-mode are all
    // fine.
    final String sOkDecls = _fn (" as=\"xs:string\"", sStrParam, "'ok'") +
                            _tmpl (" as=\"xs:string\"", sReqP) +
                            "<xsl:template match=\"r\" mode=\"#default\" default-mode=\"l:m\">x</xsl:template>";
    assertNotNull (pkACompile (m_aTempDir, _ov (sOkDecls), pkgs (PKA_OV_LIB)));
  }

  /**
   * An abstract component may live in a library package; overriding it
   * supplies the implementation. Left unoverridden it is hidden, and invoking
   * it from the library is XTDE3052. A top-level package may contain no
   * abstract component (XTSE3080).
   */
  @Test
  public void usePackageAbstract ()
  {
    final String [] aLib = pkgs (pkAPkg ("urn:pkA:abs", "", """
        <xsl:function name="l:abs" visibility="abstract" as="xs:string"/>
        <xsl:template name="l:run" visibility="public"><xsl:value-of select="l:abs()"/></xsl:template>"""));
    final String sRun = "<xsl:template match=\"/\"><xsl:call-template name=\"l:run\"/></xsl:template>";
    pkAWant (m_aTempDir, "overridden", pkAMain ("""
        <xsl:use-package name="urn:pkA:abs"><xsl:override>
          <xsl:function name="l:abs" visibility="public" as="xs:string"><xsl:sequence select="'impl'"/></xsl:function>
        </xsl:override></xsl:use-package>""" + sRun), aLib, "impl");
    pkAWantErr (m_aTempDir, "not overridden", pkAMain ("<xsl:use-package name=\"urn:pkA:abs\"/>" + sRun), aLib, "XTDE3052");
    pkAWantErr (m_aTempDir, "accepted as abstract", pkAMain ("""
        <xsl:use-package name="urn:pkA:abs">
          <xsl:accept component="function" names="l:abs#0" visibility="abstract"/>
        </xsl:use-package>""" + sRun), aLib, "XTSE3080");
    // go-xslt's package has no xsl:mode, so its template rule also uses the
    // undeclared unnamed mode (XTSE3085, §3.5.4.1); which of two static errors
    // is reported is not defined, so the mode is declared here
    pkAWantErr (m_aTempDir, "own abstract", "<xsl:package version=\"3.0\" " + PKA_NS + " xmlns:l=\"urn:pkA:l\">\n" + """
        <xsl:mode/>
        <xsl:function name="l:abs" visibility="abstract"/>
        <xsl:template match="/"/></xsl:package>""", pkgs (), "XTSE3080");
  }

  @NonNull
  private static String _base (@NonNull final String v)
  {
    return pkAPkg ("urn:pkA:base", v, "<xsl:variable name=\"l:v\" visibility=\"public\" select=\"'" + v + "'\"/>");
  }

  @NonNull
  private static String _user (@NonNull final String sName, @NonNull final String sSpec, @NonNull final String sSep)
  {
    return "<xsl:package name=\"" +
           sName +
           "\" version=\"3.0\" " +
           PKA_NS +
           " xmlns:l=\"urn:pkA:l\" xmlns:u=\"" +
           sName +
           "\">\n" +
           "<xsl:decimal-format decimal-separator=\"" +
           sSep +
           "\" grouping-separator=\"_\"/>\n" +
           "<xsl:use-package name=\"urn:pkA:base\" package-version=\"" +
           sSpec +
           "\"/>\n" +
           "<xsl:function name=\"u:get\" visibility=\"public\"><xsl:sequence select=\"$l:v || '/' || format-number(1.5, '0" +
           sSep +
           "0')\"/></xsl:function>\n" +
           "</xsl:package>";
  }

  /**
   * Two used packages each use a different version of one library declaring
   * the same public variable; each sees its own version's value, and each keeps
   * its own package-local decimal format (§3.5).
   */
  @Test
  public void usePackageDiamond ()
  {
    final String sMain = "<xsl:stylesheet version=\"3.0\" " + PKA_NS + " xmlns:a=\"urn:pkA:ua\" xmlns:b=\"urn:pkA:ub\">\n" + """
        <xsl:output method="text"/>
        <xsl:use-package name="urn:pkA:ua"/><xsl:use-package name="urn:pkA:ub"/>
        <xsl:template match="/"><xsl:value-of select="a:get(), b:get(), format-number(1.5, '0.0')" separator=" "/></xsl:template>
        </xsl:stylesheet>""";
    pkAWant (m_aTempDir,
             "diamond",
             sMain,
             pkgs (_base ("1.0"), _base ("2.0"), _user ("urn:pkA:ua", "1", ","), _user ("urn:pkA:ub", "2", "!")),
             "1.0/1,5 2.0/1!5 1.5");
  }
}
