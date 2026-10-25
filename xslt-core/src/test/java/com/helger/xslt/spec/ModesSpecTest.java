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
import static com.helger.xslt.spec.XsltSpecTestHelper.specXml;

import org.junit.Test;

/**
 * Port of go-xslt's <code>internal/xslt/modes_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ModesSpecTest
{
  /**
   * XSLT 3.0 §6.7 built-in template rules selected by xsl:mode/@on-no-match,
   * and §6.6 on-multiple-match.
   */
  @Test
  public void modeSpecOnNoMatch ()
  {
    final String sSrc = "<a x=\"1\"><b/>t<!--c--><?p d?><c><b/></c></a>";
    specRunAll (ok ("shallow-copy", specXml ("""
        <xsl:mode on-no-match="shallow-copy"/>
        <xsl:template match="b"><B/></xsl:template>"""), sSrc, "<a x=\"1\"><B/>t<!--c--><?p d?><c><B/></c></a>"),
                ok ("shallow-copy reaches attributes", specXml ("""
                    <xsl:mode on-no-match="shallow-copy"/>
                    <xsl:template match="@x"><xsl:attribute name="y" select="."/></xsl:template>"""),
                    sSrc,
                    "<a y=\"1\"><b/>t<!--c--><?p d?><c><b/></c></a>"),
                ok ("shallow-copy keeps namespaces",
                    specXml ("<xsl:mode on-no-match=\"shallow-copy\"/>"),
                    "<p:a xmlns:p=\"urn:p\"><p:b/></p:a>",
                    "<p:a xmlns:p=\"urn:p\"><p:b/></p:a>"),
                ok ("deep-copy", specXml ("""
                    <xsl:mode on-no-match="deep-copy"/>
                    <xsl:template match="/"><A><xsl:apply-templates/></A></xsl:template>
                    <xsl:template match="b"><B/></xsl:template>"""), sSrc, "<A><a x=\"1\"><b/>t<!--c--><?p d?><c><b/></c></a></A>"),
                ok ("shallow-skip", specXml ("""
                    <xsl:mode on-no-match="shallow-skip"/>
                    <xsl:template match="b"><B/></xsl:template>"""), sSrc, "<B/><B/>"),
                ok ("shallow-skip visits attributes", """
                    <xsl:mode on-no-match="shallow-skip"/>
                    <xsl:template match="@*"><xsl:value-of select="name()"/></xsl:template>""", sSrc, "x"),
                // The document node's children are still processed under
                // deep-skip.
                ok ("deep-skip", specXml ("""
                    <xsl:mode on-no-match="deep-skip"/>
                    <xsl:template match="a"><A><xsl:apply-templates/></A></xsl:template>
                    <xsl:template match="b"><B/></xsl:template>"""), sSrc, "<A><B/></A>"),
                ok ("text-only-copy", """
                    <xsl:template match="a"><xsl:apply-templates select="node(), @x"/></xsl:template>""", sSrc, "t1"),
                err ("fail", """
                    <xsl:mode on-no-match="fail"/>
                    <xsl:template match="/"><xsl:apply-templates/></xsl:template>""", sSrc, "XTDE0555"),
                ok ("fail can be caught", """
                    <xsl:mode on-no-match="fail"/>
                    <xsl:template match="/"><xsl:try><xsl:apply-templates/><xsl:catch errors="err:XTDE0555" xmlns:err="http://www.w3.org/2005/xqt-errors">caught</xsl:catch></xsl:try></xsl:template>""",
                    sSrc,
                    "caught"),
                // Arrays: the built-in rule applies templates to the members.
                ok ("array members under text-only-copy", """
                    <xsl:template match="/"><xsl:apply-templates select="[1, [2, 3]]"/></xsl:template>
                    <xsl:template match=".[. instance of xs:integer]"><xsl:value-of select="."/>|</xsl:template>""", null, "1|2|3|"),
                ok ("array under deep-skip", """
                    <xsl:mode on-no-match="deep-skip"/>
                    <xsl:template match="/">[<xsl:apply-templates select="[1, 2]"/>]</xsl:template>
                    <xsl:template match=".[. instance of xs:integer]"><xsl:value-of select="."/></xsl:template>""", null, "[]"),
                err ("array under fail", """
                    <xsl:mode on-no-match="fail"/>
                    <xsl:template match="/"><xsl:apply-templates select="[1, 2]"/></xsl:template>""", null, "XTDE0555"),
                ok ("atomic values under text-only-copy", """
                    <xsl:template match="/"><xsl:apply-templates select="'a', 'b'"/></xsl:template>""", null, "ab"),
                // §6.6: on-multiple-match="fail" - two rules, same precedence and
                // priority.
                err ("on-multiple-match fail", """
                    <xsl:mode on-multiple-match="fail"/>
                    <xsl:template match="a">1</xsl:template>
                    <xsl:template match="*" priority="0">2</xsl:template>
                    <xsl:template match="/"><xsl:apply-templates select="a"/></xsl:template>""", sSrc, "XTDE0540"),
                // Otherwise the last in declaration order is chosen.
                ok ("on-multiple-match use-last", """
                    <xsl:template match="/"><xsl:apply-templates select="a"/></xsl:template>
                    <xsl:template match="a">1</xsl:template>
                    <xsl:template match="a">2</xsl:template>""", sSrc, "2"),
                // XTSE0545: conflicting xsl:mode declarations at the same
                // precedence.
                err ("conflicting mode declarations", """
                    <xsl:mode name="m" on-no-match="deep-skip"/>
                    <xsl:mode name="m" on-no-match="shallow-copy"/>""", null, "XTSE0545"),
                ok ("repeated identical mode declarations", """
                    <xsl:mode name="m" on-no-match="deep-skip"/>
                    <xsl:mode name="m" on-no-match="deep-skip"/>
                    <xsl:template match="/">ok</xsl:template>""", null, "ok"));
  }

  /**
   * XSLT 3.0 §6.6 modes: named modes, #all, #default, #current, #unnamed and
   * default-mode.
   */
  @Test
  public void modeSpecSelection ()
  {
    final String sSrc = "<r><a/><b/></r>";
    specRunAll (ok ("named mode and #current", """
        <xsl:template match="/"><xsl:apply-templates select="r/a" mode="m"/></xsl:template>
        <xsl:template match="a" mode="m">m:a[<xsl:apply-templates select="../b" mode="#current"/>]</xsl:template>
        <xsl:template match="b" mode="m">m:b</xsl:template>
        <xsl:template match="b">default:b</xsl:template>""", sSrc, "m:a[m:b]"),
                ok ("#all", """
                    <xsl:template match="/"><xsl:apply-templates select="r/a" mode="x"/><xsl:text> </xsl:text><xsl:apply-templates select="r/a"/></xsl:template>
                    <xsl:template match="a" mode="#all">all</xsl:template>""", sSrc, "all all"),
                ok ("#default and named in one list", """
                    <xsl:template match="/"><xsl:apply-templates select="r/a" mode="x"/><xsl:apply-templates select="r/b"/></xsl:template>
                    <xsl:template match="a | b" mode="x #default"><xsl:value-of select="name()"/></xsl:template>""", sSrc, "ab"),
                ok ("#unnamed", """
                    <xsl:template match="/"><xsl:apply-templates select="r/a" mode="#unnamed"/></xsl:template>
                    <xsl:template match="a" mode="#unnamed">a</xsl:template>""", sSrc, "a"),
                // §3.7 default-mode on an element makes it the default for the
                // subtree.
                ok ("default-mode",
                    "<xsl:stylesheet version=\"3.0\" " +
                                    SPEC_NS +
                                    " default-mode=\"m\"><xsl:output method=\"text\"/>\n" +
                                    "<xsl:template match=\"/\" mode=\"#unnamed\"><xsl:apply-templates select=\"r/a\"/></xsl:template>\n" +
                                    "<xsl:template match=\"a\">M</xsl:template>\n" +
                                    "<xsl:template match=\"a\" mode=\"#unnamed\">U</xsl:template></xsl:stylesheet>",
                    sSrc,
                    "M"),
                ok ("prefixed mode names compare by namespace", """
                    <xsl:template match="/" xmlns:g="urn:f"><xsl:apply-templates select="r/a" mode="g:m"/></xsl:template>
                    <xsl:template match="a" mode="f:m">ok</xsl:template>""", sSrc, "ok"),
                ok ("mode-specific built-in falls back to text", """
                    <xsl:template match="/"><xsl:apply-templates mode="q"/></xsl:template>""", "<r>t<a>u</a></r>", "tu"),
                // §3.5 XTSE3085: with declared-modes (the package default) every
                // mode used must be declared.
                err ("undeclared mode in a package",
                     "<xsl:package version=\"3.0\" name=\"urn:p\" " +
                                                     SPEC_NS +
                                                     ">\n" +
                                                     "<xsl:mode/><xsl:template match=\"/\"><xsl:apply-templates mode=\"undeclared\"/></xsl:template></xsl:package>",
                     null,
                     "XTSE3085"),
                ok ("declared mode in a package",
                    "<xsl:package version=\"3.0\" name=\"urn:p\" " +
                                                  SPEC_NS +
                                                  "><xsl:output method=\"text\"/>\n" +
                                                  "<xsl:mode/><xsl:mode name=\"m\"/><xsl:template match=\"/\"><xsl:apply-templates select=\"*\" mode=\"m\"/></xsl:template><xsl:template match=\"*\" mode=\"m\">ok</xsl:template></xsl:package>",
                    null,
                    "ok"),
                ok ("declared-modes=no",
                    "<xsl:package version=\"3.0\" name=\"urn:p\" declared-modes=\"no\" " +
                                         SPEC_NS +
                                         "><xsl:output method=\"text\"/>\n" +
                                         "<xsl:template match=\"/\"><xsl:apply-templates select=\"*\" mode=\"m\"/></xsl:template><xsl:template match=\"*\" mode=\"m\">ok</xsl:template></xsl:package>",
                    null,
                    "ok"));
  }

  /**
   * §2.3.3: with no initial mode supplied, the initial mode is the
   * default-mode of the top-level package.
   */
  @Test
  public void modeSpecInitialModeFromDefaultMode ()
  {
    specRunAll (ok ("initial mode from default-mode",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                      SPEC_NS +
                                                      " default-mode=\"m\"><xsl:output method=\"text\"/>\n" +
                                                      "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/a\"/></xsl:template>\n" +
                                                      "<xsl:template match=\"a\">M</xsl:template></xsl:stylesheet>",
                    "<r><a/></r>",
                    "M"));
  }
}
