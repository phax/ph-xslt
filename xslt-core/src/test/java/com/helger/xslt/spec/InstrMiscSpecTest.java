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
 * Port of go-xslt's <code>internal/xslt/instr_misc_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrMiscSpecTest
{
  /** XSLT 3.0 §8.4.2 xsl:on-empty / xsl:on-non-empty. */
  @Test
  public void instrSpecOnEmpty ()
  {
    final String sSheet = specXml ("""
        <xsl:template match="/r">
          <t><xsl:on-non-empty><h/></xsl:on-non-empty><xsl:apply-templates select="row"/><xsl:on-empty><none/></xsl:on-empty></t>
        </xsl:template>
        <xsl:template match="row"><row/></xsl:template>""");
    specRunAll (ok ("non-empty", sSheet, "<r><row/><row/></r>", "<t><h/><row/><row/></t>"),
                ok ("empty", sSheet, "<r/>", "<t><none/></t>"),
                ok ("on-empty with select", specXml ("""
                    <xsl:template match="/r"><t><xsl:sequence select="row"/><xsl:on-empty select="'none'"/></t></xsl:template>"""),
                    "<r/>",
                    "<t>none</t>"),
                ok ("on-non-empty with select", specXml ("""
                    <xsl:template match="/r"><t><xsl:on-non-empty select="'rows:'"/><xsl:copy-of select="row"/></t></xsl:template>"""),
                    "<r><row/></r>",
                    "<t>rows:<row/></t>"),
                // An attribute node is content: the sequence is not empty.
                ok ("attribute counts as content", specXml ("""
                    <xsl:template match="/"><e><xsl:attribute name="a">1</xsl:attribute><xsl:on-empty>E</xsl:on-empty></e></xsl:template>"""),
                    null,
                    "<e a=\"1\"/>"),
                ok ("in a function result", """
                    <xsl:function name="f:c"><xsl:param name="s"/><xsl:sequence select="$s"/><xsl:on-empty select="0"/></xsl:function>
                    <xsl:template match="/"><xsl:value-of select="f:c(()), f:c(1)"/></xsl:template>""", null, "0 1"),
                // A zero-length text node is deemed empty (§8.4.1).
                ok ("zero-length text is empty", specXml ("""
                    <xsl:template match="/"><t><xsl:value-of select="''"/><xsl:on-empty>E</xsl:on-empty></t></xsl:template>"""),
                    null,
                    "<t>E</t>"));
  }

  /** XSLT 3.0 §8.4.3 xsl:where-populated, with the §8.4.1 "deemed empty" rules. */
  @Test
  public void instrSpecWherePopulated ()
  {
    specRunAll (ok ("empty list dropped", specXml ("""
        <xsl:template match="/r"><out><xsl:where-populated><ul><xsl:for-each select="x"><li/></xsl:for-each></ul></xsl:where-populated></out></xsl:template>"""),
                    "<r/>",
                    "<out/>"),
                ok ("populated list kept", specXml ("""
                    <xsl:template match="/r"><out><xsl:where-populated><ul><xsl:for-each select="x"><li/></xsl:for-each></ul></xsl:where-populated></out></xsl:template>"""),
                    "<r><x/></r>",
                    "<out><ul><li/></ul></out>"),
                // An element with attributes but no children is deemed empty.
                ok ("element with only attributes dropped", specXml ("""
                    <xsl:template match="/"><out><xsl:where-populated><e a="1"/></xsl:where-populated></out></xsl:template>"""),
                    null,
                    "<out/>"),
                ok ("items in a function result", """
                    <xsl:function name="f:w" as="item()*"><xsl:param name="v" as="item()*"/><xsl:where-populated><xsl:sequence select="$v"/></xsl:where-populated></xsl:function>
                    <xsl:template match="/"><xsl:value-of select="count(f:w(map{})), count(f:w(map{1:2})),
                      count(f:w([1])), count(f:w([])), count(f:w([()])), count(f:w([[], 'x'])), count(f:w([['']])), count(f:w(1))"/></xsl:template>""",
                    null,
                    "0 1 1 0 0 1 0 1"),
                ok ("comments and PIs", specXml ("""
                    <xsl:template match="/"><out><xsl:where-populated><xsl:comment/><xsl:comment>c</xsl:comment><xsl:processing-instruction name="p"/><xsl:processing-instruction name="p">x</xsl:processing-instruction></xsl:where-populated></out></xsl:template>"""),
                    null,
                    "<out><!--c--><?p x?></out>"),
                ok ("document node children", """
                    <xsl:function name="f:w" as="item()*"><xsl:param name="v" as="item()*"/><xsl:where-populated><xsl:sequence select="$v"/></xsl:where-populated></xsl:function>
                    <xsl:variable name="full"><a/></xsl:variable>
                    <xsl:variable name="empty" as="document-node()"><xsl:document/></xsl:variable>
                    <xsl:template match="/"><xsl:value-of select="count(f:w($full)), count($empty/node())"/></xsl:template>""",
                    null,
                    "1 0"));
  }

  /** XSLT 3.0 §11.7 xsl:namespace and its dynamic errors. */
  @Test
  public void instrSpecNamespace ()
  {
    specRunAll (ok ("select form", specXml ("""
        <xsl:template match="/"><e><xsl:namespace name="p" select="'urn:p'"/></e></xsl:template>"""), null, "<e xmlns:p=\"urn:p\"/>"),
                ok ("content form and AVT name", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="{'q'}">urn:q</xsl:namespace></e></xsl:template>"""),
                    null,
                    "<e xmlns:q=\"urn:q\"/>"),
                ok ("default namespace on a namespaced element", specXml ("""
                    <xsl:template match="/"><p:e xmlns:p="urn:x"><xsl:namespace name="" select="'urn:d'"/></p:e></xsl:template>"""),
                    null,
                    "<p:e xmlns:p=\"urn:x\" xmlns=\"urn:d\"/>"),
                ok ("same binding twice is fine", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="p" select="'urn:p'"/><xsl:namespace name="p" select="'urn:p'"/></e></xsl:template>"""),
                    null,
                    "<e xmlns:p=\"urn:p\"/>"),
                err ("name xmlns", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="{'xmlns'}" select="'urn:p'"/></e></xsl:template>"""), null, "XTDE0920"),
                err ("name not an NCName", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="{'1x'}" select="'urn:p'"/></e></xsl:template>"""), null, "XTDE0920"),
                err ("xml prefix to another URI", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="xml" select="'urn:p'"/></e></xsl:template>"""), null, "XTDE0925"),
                err ("XML namespace to another prefix", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="x" select="'http://www.w3.org/XML/1998/namespace'"/></e></xsl:template>"""),
                     null,
                     "XTDE0925"),
                err ("zero-length URI", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="p" select="''"/></e></xsl:template>"""), null, "XTDE0930"),
                err ("xmlns namespace URI", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="p" select="'http://www.w3.org/2000/xmlns/'"/></e></xsl:template>"""),
                     null,
                     "XTDE0905"),
                err ("default namespace on no-namespace element", specXml ("""
                    <xsl:template match="/"><e><xsl:namespace name="" select="'urn:d'"/></e></xsl:template>"""), null, "XTDE0440"));
  }

  /**
   * XSLT 3.0 §11.4 xsl:document, §5.6.2 text value templates, §19 xsl:fork
   * (outside streaming its branches are simply evaluated in order).
   */
  @Test
  public void instrSpecDocumentTVTFork ()
  {
    specRunAll (ok ("xsl:document builds a document node", """
        <xsl:variable name="d" as="document-node()"><xsl:document><a/></xsl:document></xsl:variable>
        <xsl:template match="/"><xsl:value-of select="$d instance of document-node(), count($d/a)"/></xsl:template>""", null, "true 1"),
                err ("attribute in a document node", """
                    <xsl:variable name="d" as="document-node()"><xsl:document><xsl:attribute name="a">1</xsl:attribute></xsl:document></xsl:variable>
                    <xsl:template match="/"><xsl:value-of select="count($d/*)"/></xsl:template>""", null, "XTDE0420"),
                ok ("text value template", """
                    <xsl:template match="/" expand-text="yes">n={count(//x)} {{lit}}</xsl:template>""", "<r><x/><x/></r>", "n=2 {lit}"),
                ok ("text value template joins with spaces", """
                    <xsl:template match="/" expand-text="yes">{1 to 3}</xsl:template>""", null, "1 2 3"),
                ok ("expand-text off inside", """
                    <xsl:template match="/" expand-text="yes"><xsl:text expand-text="no">{1}</xsl:text></xsl:template>""", null, "{1}"),
                ok ("fork evaluates its branches in order", """
                    <xsl:template match="/"><xsl:fork><xsl:sequence select="1"/><xsl:sequence select="2"/></xsl:fork></xsl:template>""",
                    null,
                    "1 2"));
  }

  /** §8.4.3: xsl:where-populated keeps each non-empty atomic item. */
  @Test
  public void instrSpecMiscKnownBugs ()
  {
    specRunAll (ok ("atomics", """
        <xsl:function name="f:w" as="item()*"><xsl:param name="v" as="item()*"/><xsl:where-populated><xsl:sequence select="$v"/></xsl:where-populated></xsl:function>
        <xsl:template match="/"><xsl:value-of select="count(f:w(('', 'a', 'b')))"/></xsl:template>""", null, "2"),
                ok ("doc", """
                    <xsl:function name="f:w" as="item()*"><xsl:param name="v" as="item()*"/><xsl:where-populated><xsl:sequence select="$v"/></xsl:where-populated></xsl:function>
                    <xsl:variable name="empty" as="document-node()"><xsl:document/></xsl:variable>
                    <xsl:template match="/"><xsl:value-of select="count(f:w($empty))"/></xsl:template>""", null, "0"));
  }

  /** §11.7 (skipped in go-xslt, passes here): conflicting xsl:namespace bindings are XTDE0430. */
  @Test
  public void instrSpecMiscKnownBugsNamespaceConflict ()
  {
    specRunAll (err ("conflict", specXml ("""
        <xsl:template match="/"><e><xsl:namespace name="p" select="'urn:a'"/><xsl:namespace name="p" select="'urn:b'"/></e></xsl:template>"""),
                     null,
                     "XTDE0430"));
  }
}
