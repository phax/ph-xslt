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
import static com.helger.xslt.spec.XsltSpecTestHelper.specXml;

import org.junit.Test;

/**
 * Port of go-xslt's <code>internal/xslt/spec_known_bugs_test.go</code>. The
 * cases of the first method are skipped as known bugs in go-xslt.
 *
 * @author Philip Helger
 */
public final class SpecKnownBugsSpecTest
{
  @Test
  public void specKnownBugs ()
  {
    specRunAll (
                // §10.4: a static error in xsl:evaluate's expression
                err ("evaluate syntax error", """
                    <xsl:template match="/"><xsl:evaluate xpath="'1 +'"/></xsl:template>""", null, "XTDE3160"),
                err ("result-document escape-uri-attributes AVT", """
                    <xsl:template match="/"><xsl:result-document href="a" escape-uri-attributes="{'sometimes'}">1</xsl:result-document></xsl:template>""",
                     null,
                     "SEPM0016"),
                // §3.10: an unknown XSLT instruction outside forwards-compatible
                // mode
                err ("unknown instruction", """
                    <xsl:template match="/"><xsl:future-thing><xsl:fallback>fb</xsl:fallback></xsl:future-thing></xsl:template>""",
                     null,
                     "XTSE0010"),
                // §9.5: a required stylesheet parameter that is not supplied
                err ("required stylesheet parameter", """
                    <xsl:param name="p" required="yes"/>
                    <xsl:template match="/"><xsl:value-of select="$p"/></xsl:template>""", null, "XTDE0050"),
                // §5.7.1: an attribute constructed after a child node
                err ("attribute after child", specXml ("""
                    <xsl:template match="/"><e><c/><xsl:attribute name="a">v</xsl:attribute></e></xsl:template>"""), null, "XTDE0410"),
                // An invalid NameTest in xsl:strip-space/@elements
                err ("strip-space invalid name test",
                     "<xsl:strip-space elements=\"1bad\"/><xsl:template match=\"/\">x</xsl:template>",
                     null,
                     "XTSE0020"),
                // XPath 3.1 §3.1.5: a call to an undeclared function
                err ("unknown function", "<xsl:template match=\"/\"><xsl:value-of select=\"f:nope()\"/></xsl:template>", null, "XPST0017"));
  }

  /** Former BUG: entries, now checked against the REC. */
  @Test
  public void specFixedBugs ()
  {
    specRunAll (
                // §5.3.4: the global context item must match
                // xsl:global-context-item/@as (XTTE0590), whichever entry point
                // runs.
                err ("global-context-item via TransformFull", """
                    <xsl:global-context-item as="element()"/><xsl:template match="/">x</xsl:template>""", null, "XTTE0590"),
                ok ("global-context-item matching via TransformFull", """
                    <xsl:global-context-item as="document-node()"/><xsl:template match="/">x</xsl:template>""", null, "x"),
                // §11.9.1: xsl:copy of a namespace node returns a copy of it.
                ok ("copy namespace node", specXml ("""
                    <xsl:template match="/*"><e><xsl:for-each select="namespace::q"><xsl:copy/></xsl:for-each></e></xsl:template>"""),
                    "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\"/>",
                    "<e xmlns:q=\"urn:q\"/>"),
                // §11.3: "If the attribute is in a non-null namespace and no
                // prefix is specified, then the namespace fixup process will
                // invent a prefix."
                ok ("attribute prefix fixup", """
                    <xsl:template match="/"><xsl:variable name="e"><e><xsl:attribute name="a" namespace="urn:x">v</xsl:attribute></e></xsl:variable><xsl:value-of select="contains(name($e/e/@*), ':')"/></xsl:template>""",
                    null,
                    "true"),
                ok ("attribute prefix fixup reuses a bound prefix", """
                    <xsl:template match="/"><xsl:variable name="e"><e xmlns:x="urn:x"><xsl:attribute name="a" namespace="urn:x">v</xsl:attribute></e></xsl:variable><xsl:value-of select="name($e/e/@*)"/></xsl:template>""",
                    null,
                    "x:a"));
  }
}
