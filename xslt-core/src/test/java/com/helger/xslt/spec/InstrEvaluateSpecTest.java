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
 * Port of go-xslt <code>internal/xslt/instr_evaluate_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrEvaluateSpecTest
{
  /**
   * XSLT 3.0 §10.4 dynamic XPath evaluation (xsl:evaluate).
   */
  @Test
  public void instrSpecEvaluate ()
  {
    final String sNs = "<r xmlns:p=\"urn:p\"><p:a>v</p:a></r>";
    specRunAll (
      ok ("with-params map", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'$x * 2'\" with-params=\"map{QName('', 'x'): 5}\"/></xsl:template>", null, "10"),
      ok ("with-param children", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'$p + 1'\"><xsl:with-param name=\"p\" select=\"1\"/></xsl:evaluate></xsl:template>", null, "2"),
      err ("with-params not a map", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'1'\" with-params=\"1\"/></xsl:template>", null, "XPTY0004"),
      err ("with-params key not a QName", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'1'\" with-params=\"map{'x': 1}\"/></xsl:template>", null, "XPTY0004"),
      ok ("atomic context item", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'. + 1'\" context-item=\"5\"/></xsl:template>", null, "6"),
      ok ("node context item", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'string(a)'\" context-item=\"/r\"/></xsl:template>", "<r><a>x</a></r>", "x"),
      // An empty context-item means the focus is absent.
      err ("empty context item", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'name(.)'\" context-item=\"()\"/></xsl:template>", null, "XPDY0002"),
      // XTTE3210: context-item with more than one item.
      err ("two context items", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'.'\" context-item=\"1, 2\"/></xsl:template>", null, "XTTE3210"),
      // The namespace-context node supplies the in-scope namespaces.
      ok ("namespace-context prefixes", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'string(p:a)'\" context-item=\"/r\" namespace-context=\"/r\"/></xsl:template>", sNs, "v"),
      // ... and its default namespace is the default element namespace.
      ok ("namespace-context default namespace", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'string(a)'\" context-item=\"/*\" namespace-context=\"/*\"/></xsl:template>", "<r xmlns=\"urn:d\"><a>w</a></r>", "w"),
      err ("namespace-context two nodes", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'1'\" namespace-context=\"//a\"/></xsl:template>", "<r><a/><a/></r>", "XTTE3170"),
      ok ("base-uri", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'string(static-base-uri())'\" base-uri=\"http://example.com/x/\"/></xsl:template>", null, "http://example.com/x/"),
      ok ("as converts the result", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"v\"><xsl:evaluate xpath=\"'3'\" as=\"xs:double\"/></xsl:variable><xsl:value-of select=\"$v = '3'\"/></xsl:template>", null, "true"),
      // XTTE3165: the result does not match @as.
      err ("as mismatch", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'(1, 2)'\" as=\"xs:integer\"/></xsl:template>", null, "XTTE3165"),
      // XTDE3160: a static error in the evaluated expression, including the
      // XSLT functions §10.4.1 keeps out of its static context.
      err ("current() unavailable", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'current()'\" context-item=\".\"/></xsl:template>", null, "XTDE3160"),
      err ("system-property unavailable", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'system-property(&quot;xsl:version&quot;)'\"/></xsl:template>", null, "XTDE3160"),
      // go-xslt expects "7"; §2.3.6.1 builds the result tree by sequence
      // normalization, and "the process raises a serialization error if any
      // item in the raw result is an attribute node".
      err ("attribute result into text", "\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'/r/@a'\" context-item=\".\"/></xsl:template>", "<r a=\"7\"/>", "SENR0001"),
      ok ("attribute result onto an element", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:evaluate xpath=\"'/r/@a'\" context-item=\".\"/></e></xsl:template>"), "<r a=\"7\"/>", "<e a=\"7\"/>"),
      ok ("public function is available", "\n" +
        "<xsl:function name=\"f:d\" visibility=\"public\"><xsl:param name=\"n\"/><xsl:sequence select=\"$n * 2\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:evaluate xpath=\"'f:d(2)'\"/></xsl:template>", null, "4"));
  }

}
