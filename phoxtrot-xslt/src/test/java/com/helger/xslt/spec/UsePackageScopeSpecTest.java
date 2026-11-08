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

import static com.helger.xslt.spec.UsePackageSpecTest.PKA_NS;
import static com.helger.xslt.spec.UsePackageSpecTest.PKA_OV_LIB;
import static com.helger.xslt.spec.UsePackageSpecTest.pkACompile;
import static com.helger.xslt.spec.UsePackageSpecTest.pkAMain;
import static com.helger.xslt.spec.UsePackageSpecTest.pkAPkg;
import static com.helger.xslt.spec.UsePackageSpecTest.pkAWant;
import static com.helger.xslt.spec.UsePackageSpecTest.pkAWantErr;
import static com.helger.xslt.spec.UsePackageSpecTest.pkgs;
import static org.junit.Assert.assertNotNull;

import org.jspecify.annotations.NonNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Port of go-xslt's <code>internal/xslt/use_package_scope_test.go</code>: more
 * xsl:use-package cases (XSLT 3.0 §3.5): re-exported components, the rest of
 * the xsl:accept visibility table, template rules for used modes, xsl:original
 * of an abstract component, and package-local declarations.
 *
 * @author Philip Helger
 */
public final class UsePackageScopeSpecTest
{
  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * A package that uses another may make an accepted component part of its own
   * manifest by accepting it with an exposed visibility (§3.5.3.2); xsl:expose
   * in a used package decides what it exports (§3.5.3.1).
   */
  @Test
  public void usePackageReexport ()
  {
    final String sInner = pkAPkg ("urn:pkA:inner", "", """
        <xsl:function name="l:in" visibility="public"><xsl:sequence select="'in'"/></xsl:function>
        <xsl:function name="l:kept" visibility="public"><xsl:sequence select="'kept'"/></xsl:function>""");
    final String sMid = pkAPkg ("urn:pkA:mid", "", """
        <xsl:use-package name="urn:pkA:inner">
          <xsl:accept component="function" names="l:in#0" visibility="public"/>
          <xsl:accept component="function" names="l:kept#0" visibility="private"/>
        </xsl:use-package>
        <xsl:function name="l:exposed"><xsl:sequence select="'exp'"/></xsl:function>
        <xsl:expose component="function" names="l:exposed#0" visibility="public"/>""");
    pkAWant (m_aTempDir,
             "re-exported",
             pkAMain ("<xsl:use-package name=\"urn:pkA:mid\"/><xsl:template match=\"/\"><xsl:value-of select=\"l:in(), l:exposed()\"/></xsl:template>"),
             pkgs (sInner, sMid),
             "in exp");
  }

  /**
   * A component the middle package accepts as private is private there, so it
   * is hidden from a package using the middle one (§3.5.3.2) and cannot be
   * referenced (XPST0017; go-xslt checks only for an error).
   */
  @Test
  public void usePackageAcceptedPrivateNotReexported ()
  {
    final String sInner = pkAPkg ("urn:pkA:inner", "", "<xsl:function name=\"l:kept\" visibility=\"public\"><xsl:sequence select=\"'kept'\"/></xsl:function>");
    final String sMid = pkAPkg ("urn:pkA:mid", "", """
        <xsl:use-package name="urn:pkA:inner">
          <xsl:accept component="function" names="l:kept#0" visibility="private"/>
        </xsl:use-package>""");
    pkAWantErr (m_aTempDir, "accepted privately is not re-exported", pkAMain ("""
        <xsl:use-package name="urn:pkA:mid"/>
        <xsl:template match="/"><xsl:value-of select="l:kept()"/></xsl:template>"""), pkgs (sInner, sMid), "XPST0017");
  }

  @NonNull
  private static String _use (@NonNull final String sPkg, @NonNull final String sChildren)
  {
    return pkAMain ("<xsl:use-package name=\"" + sPkg + "\">" + sChildren + "</xsl:use-package><xsl:template match=\"/\"/>");
  }

  /** Further cases of the §3.5.3.2 table and of XTSE3030. */
  @Test
  public void usePackageMoreAcceptErrors ()
  {
    final String [] aLib = pkgs (pkAPkg ("urn:pkA:lib", "", """
        <xsl:function name="l:f" visibility="public"><xsl:param name="x"/><xsl:sequence select="$x"/></xsl:function>
        <xsl:function name="l:fin" visibility="final"><xsl:sequence select="1"/></xsl:function>"""));
    // l:f has arity 1, so l:f#0 names nothing.
    pkAWantErr (m_aTempDir,
                "wrong arity",
                _use ("urn:pkA:lib", "<xsl:accept component=\"function\" names=\"l:f#0\" visibility=\"private\"/>"),
                aLib,
                "XTSE3030");
    // A name of the right kind is required.
    pkAWantErr (m_aTempDir,
                "wrong kind",
                _use ("urn:pkA:lib", "<xsl:accept component=\"template\" names=\"l:f\" visibility=\"private\"/>"),
                aLib,
                "XTSE3030");
    // final -> final, private and hidden are permitted.
    for (final String v : new String [] { "final", "private", "hidden" })
      assertNotNull (pkACompile (m_aTempDir,
                                 _use ("urn:pkA:lib", "<xsl:accept component=\"function\" names=\"l:fin#0\" visibility=\"" + v + "\"/>"),
                                 aLib));
  }

  /**
   * Per the §3.5.3.2 table an abstract component can only be accepted as
   * abstract or hidden, and only an abstract component can be accepted as
   * abstract; anything else named explicitly is XTSE3040. (Skipped in go-xslt
   * as an engine bug there.)
   */
  @Test
  public void usePackageAbstractAcceptTable ()
  {
    final String [] aLib = pkgs (pkAPkg ("urn:pkA:abs", "", """
        <xsl:function name="l:abs" visibility="abstract"/>
        <xsl:function name="l:pub" visibility="public"><xsl:sequence select="1"/></xsl:function>"""));
    for (final String v : new String [] { "public", "private", "final" })
      pkAWantErr (m_aTempDir,
                  "abstract as " + v,
                  _use ("urn:pkA:abs", "<xsl:accept component=\"function\" names=\"l:abs#0\" visibility=\"" + v + "\"/>"),
                  aLib,
                  "XTSE3040");
    pkAWantErr (m_aTempDir,
                "public as abstract",
                _use ("urn:pkA:abs", "<xsl:accept component=\"function\" names=\"l:pub#0\" visibility=\"abstract\"/>"),
                aLib,
                "XTSE3040");
  }

  /**
   * Template rules for a mode of a used package may only be added through
   * xsl:override (§3.5.3.3); a rule outside it is rejected (W3C xslt30-test
   * override-m-018 expects XTSE3050).
   */
  @Test
  public void usePackageModeRules ()
  {
    pkAWantErr (m_aTempDir, "rule outside override", pkAMain ("""
        <xsl:use-package name="urn:pkA:lib"/>
        <xsl:template match="r" mode="l:m #default">own</xsl:template>"""), pkgs (PKA_OV_LIB), "XTSE3050");
  }

  /**
   * Keys and character maps are local to the package that declares them (§3.5:
   * they "have local scope within that package"), even when two packages use
   * the same name.
   */
  @Test
  public void usePackageLocalDeclarations ()
  {
    final String sLib = pkAPkg ("urn:pkA:lib", "", """
        <xsl:key name="k" match="i" use="."/>
        <xsl:character-map name="cm"><xsl:output-character character="x" string="LIB"/></xsl:character-map>
        <xsl:output method="text" use-character-maps="cm"/>
        <xsl:function name="l:keyed" visibility="public">
          <xsl:param name="d"/>
          <xsl:sequence select="local-name(key('k', '1', $d))"/>
        </xsl:function>""");
    final String sMain = "<xsl:stylesheet version=\"3.0\" " + PKA_NS + " xmlns:l=\"urn:pkA:l\">\n" + """
        <xsl:output method="text" use-character-maps="cm"/>
        <xsl:character-map name="cm"><xsl:output-character character="x" string="MAIN"/></xsl:character-map>
        <xsl:key name="k" match="r" use="'1'"/>
        <xsl:use-package name="urn:pkA:lib"/>
        <xsl:template match="/"><xsl:value-of select="l:keyed(/), local-name(key('k', '1')), 'x'"/></xsl:template>
        </xsl:stylesheet>""";
    pkAWant (m_aTempDir, "local keys and character maps", sMain, pkgs (sLib), "i r MAIN");
  }

  /**
   * xsl:original may not be used when the overridden component is abstract
   * (XTSE3075, §3.5.3.4). (Skipped in go-xslt as an engine bug there.)
   */
  @Test
  @Ignore ("XTSE3075 is not detected; the call fails at run time with XTDE3052 instead (as in go-xslt)")
  public void usePackageOriginalAbstract ()
  {
    final String [] aLib = pkgs (pkAPkg ("urn:pkA:abs", "", "<xsl:function name=\"l:abs\" visibility=\"abstract\" as=\"xs:string\"/>"));
    pkAWantErr (m_aTempDir, "xsl:original of abstract", pkAMain ("""
        <xsl:use-package name="urn:pkA:abs"><xsl:override>
          <xsl:function name="l:abs" visibility="public" as="xs:string"><xsl:sequence select="xsl:original()"/></xsl:function>
        </xsl:override></xsl:use-package><xsl:template match="/"><xsl:value-of select="l:abs()"/></xsl:template>"""), aLib, "XTSE3075");
  }
}
