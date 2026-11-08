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
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xslt/functions_body_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class FunctionsBodySpecTest
{
  /**
   * XSLT 3.0 §10.3 stylesheet functions: the result is the sequence the body
   * constructs (no tree is built), converted to @as by the function conversion
   * rules.
   */
  @Test
  public void xsltFuncConstructedResults ()
  {
    specRunAll (
      ok ("empty body result is the empty sequence", "\n" +
        "<xsl:function name=\"f:e\"><xsl:copy-of select=\"()\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:e())\"/></xsl:template>", null, "0"),
      ok ("literal text constructs a text node", "\n" +
        "<xsl:function name=\"f:t\">abc</xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:t() instance of text()\"/></xsl:template>", null, "true"),
      ok ("two atomic items stay two items", "\n" +
        "<xsl:function name=\"f:s\"><xsl:sequence select=\"1\"/><xsl:sequence select=\"2\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:s())\"/></xsl:template>", null, "2"),
      ok ("typed atomic value keeps its type", "\n" +
        "<xsl:function name=\"f:b\"><xsl:try select=\"false()\"><xsl:catch select=\"true()\"/></xsl:try></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:b() instance of xs:boolean, not(f:b())\"/></xsl:template>", null, "true true"),
      ok ("choose body cast to the declared type", "\n" +
        "<xsl:function name=\"f:c\" as=\"xs:integer\"><xsl:param name=\"b\"/><xsl:choose><xsl:when test=\"$b\"><xsl:sequence select=\"5\"/></xsl:when><xsl:otherwise><xsl:value-of select=\"6\"/></xsl:otherwise></xsl:choose></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:c(true()), f:c(false()) instance of xs:integer\"/></xsl:template>", null, "5 true"),
      ok ("text content cast to the declared type", "\n" +
        "<xsl:function name=\"f:c\" as=\"xs:integer\"><xsl:if test=\"true()\"><xsl:text>7</xsl:text></xsl:if></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:c()\"/></xsl:template>", null, "7"),
      ok ("elements are returned as nodes", "\n" +
        "<xsl:function name=\"f:n\"><a/><b/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:n()), f:n() ! name()\"/></xsl:template>", null, "2 a b"),
      ok ("attribute nodes are returned as nodes", "\n" +
        "<xsl:function name=\"f:a\"><xsl:attribute name=\"x\">1</xsl:attribute></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"name(f:a()) || '=' || f:a()\"/></xsl:template>", null, "x=1"),
      ok ("source nodes are returned by identity", "\n" +
        "<xsl:function name=\"f:id\"><xsl:param name=\"n\"/><xsl:if test=\"true()\"><xsl:sequence select=\"$n\"/></xsl:if></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:id(/r/a) is /r/a\"/></xsl:template>", "<r><a/></r>", "true"),
      ok ("cached function", "\n" +
        "<xsl:function name=\"f:m\" cache=\"yes\"><xsl:param name=\"n\"/><xsl:sequence select=\"$n * 2\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:m(3), f:m(3)\"/></xsl:template>", null, "6 6"),
      ok ("cached function with node arguments", "\n" +
        "<xsl:function name=\"f:m\" new-each-time=\"no\"><xsl:param name=\"n\"/><xsl:sequence select=\"string($n)\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:m(/r/a[1]), f:m(/r/a[2])\"/></xsl:template>", "<r><a>1</a><a>2</a></r>", "1 2"),
      ok ("braced EQName call", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:app=\"http://app.example/\">\n" +
        "<xsl:output method=\"text\"/>\n" +
        "<xsl:function name=\"app:f\"><xsl:param name=\"n\"/><xsl:sequence select=\"$n + 1\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"Q{http://app.example/}f(3)\"/></xsl:template></xsl:stylesheet>", null, "4"));
  }

  /**
   * fn:copy-of / fn:snapshot and namespaces (XSLT 3.0 §20.?).
   */
  @Test
  public void xsltFuncCopyNamespaces ()
  {
    final String sSrc = "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\" x=\"1\"><c/></p:a>";
    specRunAll (
      ok ("copy-of keeps inherited namespaces", "\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"sort(in-scope-prefixes(copy-of(/*/c)))\"/></xsl:template>", sSrc, "p q xml"),
      ok ("snapshot of an attribute", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"s\" select=\"snapshot(/*/@x)\"/><xsl:value-of select=\"name($s/..), string($s)\"/></xsl:template>", sSrc, "p:a 1"),
      ok ("snapshot of a namespace node", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"s\" select=\"snapshot(/*/namespace::q)\"/><xsl:value-of select=\"name($s/..), string($s)\"/></xsl:template>", sSrc, "p:a urn:q"),
      ok ("snapshot of a text node", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"s\" select=\"snapshot(/r/a/text())\"/><xsl:value-of select=\"name($s/..), name($s/../..)\"/></xsl:template>", "<r><a>t</a></r>", "a r"),
      ok ("snapshot nodes have distinct ids", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"s\" select=\"snapshot(/r/a)\"/><xsl:value-of select=\"generate-id($s) ne generate-id($s/..)\"/></xsl:template>", "<r><a/></r>", "true"));
  }

}
