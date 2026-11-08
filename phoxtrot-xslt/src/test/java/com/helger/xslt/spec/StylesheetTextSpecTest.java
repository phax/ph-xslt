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
 * Port of go-xslt's <code>internal/xslt/stylesheet_text_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class StylesheetTextSpecTest
{
  /**
   * XSLT 3.0 §4.3 stripping whitespace and comments from the stylesheet,
   * §5.6.2 text value templates, and local variable scoping (§9).
   */
  @Test
  public void compileSpecStylesheetText ()
  {
    specRunAll (ok ("xml:space preserve keeps whitespace text",
                    "<xsl:template match=\"/\" xml:space=\"preserve\"> <xsl:value-of select=\"1\"/> </xsl:template>",
                    null,
                    " 1 "),
                // Whitespace before xsl:sort is removed even under
                // xml:space="preserve".
                ok ("whitespace before xsl:sort", """
                    <xsl:template match="/"><xsl:for-each select="2, 1" xml:space="preserve">
                      <xsl:sort select="."/>|<xsl:value-of select="."/></xsl:for-each></xsl:template>""", null, "|1|2"),
                // ... and whitespace children of xsl:choose.
                ok ("whitespace inside xsl:choose", """
                    <xsl:template match="/" xml:space="preserve"><xsl:choose>
                      <xsl:when test="true()">y</xsl:when>
                    </xsl:choose></xsl:template>""", null, "y"),
                ok ("whitespace before xsl:param", """
                    <xsl:template match="/"><xsl:call-template name="t"/></xsl:template>
                    <xsl:template name="t" xml:space="preserve">
                      <xsl:param name="p" select="'p'"/><xsl:value-of select="$p"/></xsl:template>""", null, "p"),
                // Comments are removed first, so the texts around one are
                // adjacent.
                ok ("text split by a comment", "<xsl:template match=\"/\">ab<!--c-->cd</xsl:template>", null, "abcd"),
                ok ("text value template split by a comment",
                    "<xsl:template match=\"/\" expand-text=\"yes\">a{1<!--c-->+1}b</xsl:template>",
                    null,
                    "a2b"),
                ok ("whitespace after a comment joins the text", "<xsl:template match=\"/\">x<!--c--> </xsl:template>", null, "x "),
                ok ("processing instruction in the stylesheet", "<xsl:template match=\"/\">x<?pi data?>y</xsl:template>", null, "xy"),
                // Local variables may shadow earlier ones; a reference sees the
                // binding in scope.
                ok ("shadowing local variables",
                    "<xsl:template match=\"/\"><xsl:variable name=\"x\" select=\"1\"/><xsl:variable name=\"y\" select=\"$x\"/><xsl:variable name=\"x\" select=\"2\"/><xsl:value-of select=\"$y, $x\"/></xsl:template>",
                    null,
                    "1 2"),
                ok ("variable aliasing a sequence",
                    "<xsl:template match=\"/\"><xsl:variable name=\"b\" select=\"(1, 2)\"/><xsl:variable name=\"a\" select=\"$b\"/><xsl:value-of select=\"count($a)\"/></xsl:template>",
                    null,
                    "2"),
                ok ("alias used inside a nested instruction",
                    "<xsl:template match=\"/\"><xsl:variable name=\"b\" select=\"'x'\"/><xsl:variable name=\"a\" select=\"$b\"/><xsl:for-each select=\"1 to 2\"><xsl:value-of select=\"$a\"/><xsl:if test=\"position() lt last()\"><xsl:text> </xsl:text></xsl:if></xsl:for-each></xsl:template>",
                    null,
                    "x x"),
                ok ("variable with an as and a sequence constructor",
                    "<xsl:template match=\"/\"><xsl:variable name=\"n\" as=\"xs:integer*\"><xsl:sequence select=\"1, 2\"/><xsl:sequence select=\"3\"/></xsl:variable><xsl:value-of select=\"count($n)\"/></xsl:template>",
                    null,
                    "3"),
                // A variable with content and no @as is a document node.
                ok ("temporary tree",
                    "<xsl:template match=\"/\"><xsl:variable name=\"t\"><a/><b/></xsl:variable><xsl:value-of select=\"$t instance of document-node(), count($t/*)\"/></xsl:template>",
                    null,
                    "true 2"),
                ok ("empty variable is a zero-length string",
                    "<xsl:template match=\"/\"><xsl:variable name=\"e\"/>[<xsl:value-of select=\"$e\"/>]</xsl:template>",
                    null,
                    "[]"));
  }

  /** XSLT 3.0 §11.1.3 / §11.9: namespace inheritance and copy-namespaces. */
  @Test
  public void constructSpecNamespaces ()
  {
    final String sSrc = "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\"><q:b/><c/></p:a>";
    specRunAll (ok ("copy-namespaces no on xsl:copy",
                    specXml ("<xsl:template match=\"/*\"><xsl:copy copy-namespaces=\"no\"><e/></xsl:copy></xsl:template>"),
                    sSrc,
                    "<p:a xmlns:p=\"urn:p\"><e/></p:a>"),
                ok ("copy-of copy-namespaces no",
                    specXml ("<xsl:template match=\"/*\"><xsl:copy-of select=\"*\" copy-namespaces=\"no\"/></xsl:template>"),
                    sSrc,
                    "<q:b xmlns:q=\"urn:q\"/><c/>"),
                // inherit-namespaces="no": the child does not inherit the
                // parent's namespaces.
                ok ("xsl:element inherit-namespaces no",
                    "<xsl:template match=\"/\"><xsl:variable name=\"v\"><xsl:element name=\"r\" inherit-namespaces=\"no\"><xsl:namespace name=\"x\" select=\"'urn:x'\"/><c/></xsl:element></xsl:variable><xsl:value-of select=\"count(in-scope-prefixes($v/r/c)[. = 'x'])\"/></xsl:template>",
                    null,
                    "0"),
                ok ("xsl:copy inherit-namespaces no",
                    "<xsl:template match=\"/*\"><xsl:variable name=\"v\"><xsl:copy inherit-namespaces=\"no\"><c/></xsl:copy></xsl:variable><xsl:value-of select=\"count(in-scope-prefixes($v/*/c)[. = 'q'])\"/></xsl:template>",
                    sSrc,
                    "0"),
                ok ("exclude-result-prefixes", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:x="urn:x" exclude-result-prefixes="x">
                    <xsl:output method="xml" omit-xml-declaration="yes"/><xsl:template match="/"><r/></xsl:template></xsl:stylesheet>""",
                    null,
                    "<r/>"),
                ok ("exclude-result-prefixes #all", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:x="urn:x" xmlns:y="urn:y">
                    <xsl:output method="xml" omit-xml-declaration="yes"/><xsl:template match="/"><r xsl:exclude-result-prefixes="#all"/></xsl:template></xsl:stylesheet>""",
                    null,
                    "<r/>"),
                ok ("used namespace is not excluded", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:x="urn:x" exclude-result-prefixes="x">
                    <xsl:output method="xml" omit-xml-declaration="yes"/><xsl:template match="/"><x:r/></xsl:template></xsl:stylesheet>""",
                    null,
                    "<x:r xmlns:x=\"urn:x\"/>"),
                // XTSE0808: exclude-result-prefixes names an undeclared prefix.
                err ("exclude an undeclared prefix", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" exclude-result-prefixes="nope">
                    <xsl:template match="/"><r/></xsl:template></xsl:stylesheet>""", null, "XTSE0808"));
  }

  /** Former BUG: entries (§11.1.3, §11.9.1), now checked against the REC. */
  @Test
  public void constructSpecNamespacesFixed ()
  {
    specRunAll (ok ("literal result element inherit-namespaces no",
                    "<xsl:template match=\"/\"><xsl:variable name=\"v\"><r xmlns:x=\"urn:x\" xsl:inherit-namespaces=\"no\"><xsl:element name=\"c\"/></r></xsl:variable><xsl:value-of select=\"in-scope-prefixes($v/r)[. = 'x'], in-scope-prefixes($v/r/c)[. = 'x']\" separator=\"|\"/>|</xsl:template>",
                    null,
                    "x|"),
                ok ("copy-namespaces scope",
                    specXml ("<xsl:template match=\"/*\"><xsl:copy copy-namespaces=\"no\"><xsl:copy-of select=\"*\"/></xsl:copy></xsl:template>"),
                    "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\"><q:b/><c/></p:a>",
                    "<p:a xmlns:p=\"urn:p\"><q:b xmlns:q=\"urn:q\"/><c xmlns:q=\"urn:q\"/></p:a>"));
  }
}
