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
 * Port of go-xslt <code>internal/xslt/construct_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ConstructSpecTest
{
  /**
   * XSLT 3.0 §11.9.1 xsl:copy (shallow copy) for every node kind.
   */
  @Test
  public void constructSpecCopy ()
  {
    final String sSrc = "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\" x=\"1\"><b>t</b><!--c--><?pi v?></p:a>";
    specRunAll (
      ok ("element without content", specXml ("\n" +
        "<xsl:template match=\"/*\"><xsl:copy/></xsl:template>"), sSrc, "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\"/>"),
      ok ("element with attributes and children copied by the body", specXml ("\n" +
        "<xsl:template match=\"/*\"><xsl:copy><xsl:for-each select=\"@*, node()\"><xsl:copy><xsl:apply-templates select=\"node()\"/></xsl:copy></xsl:for-each></xsl:copy></xsl:template>\n" +
        "<xsl:template match=\"text()\"><xsl:copy/></xsl:template>"), sSrc, "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\" x=\"1\"><b>t</b><!--c--><?pi v?></p:a>"),
      // copy-namespaces="no" drops namespaces the element does not use.
      ok ("copy-namespaces no", specXml ("\n" +
        "<xsl:template match=\"/*\"><xsl:copy copy-namespaces=\"no\"/></xsl:template>"), sSrc, "<p:a xmlns:p=\"urn:p\"/>"),
      ok ("use-attribute-sets", specXml ("\n" +
        "<xsl:attribute-set name=\"as\"><xsl:attribute name=\"s\">v</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:template match=\"/*\"><xsl:copy use-attribute-sets=\"as\"/></xsl:template>"), sSrc, "<p:a xmlns:p=\"urn:p\" xmlns:q=\"urn:q\" s=\"v\"/>"),
      ok ("document node", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:copy><r/></xsl:copy></xsl:template>"), sSrc, "<r/>"),
      ok ("document node kept as a node in a sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"d\" as=\"document-node()\"><xsl:copy><r/></xsl:copy></xsl:variable><xsl:value-of select=\"$d instance of document-node(), count($d/r)\"/></xsl:template>", sSrc, "true 1"),
      err ("attribute in a document node", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:copy><xsl:attribute name=\"a\">1</xsl:attribute></xsl:copy></xsl:template>"), sSrc, "XTDE0420"),
      // §11.9.1: select (XSLT 3.0).
      ok ("select", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:copy select=\"//b\"/></xsl:template>"), sSrc, "<b xmlns:p=\"urn:p\" xmlns:q=\"urn:q\"/>"),
      ok ("select empty", specXml ("\n" +
        "<xsl:template match=\"/\"><r><xsl:copy select=\"//nothing\"/></r></xsl:template>"), sSrc, "<r/>"),
      ok ("select an atomic value", specXml ("\n" +
        "<xsl:template match=\"/\"><r><xsl:copy select=\"5\"/></r></xsl:template>"), sSrc, "<r>5</r>"),
      // XTTE3180: select yields more than one item.
      err ("select two items", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:copy select=\"//b, //b\"/></xsl:template>"), sSrc, "XTTE3180"),
      // go-xslt expects XPDY0002; §11.9.1 says "[ERR XTTE0945] It is a type
      // error to use the xsl:copy instruction with no select attribute when
      // the context item is absent."
      err ("absent focus", specXml ("\n" +
        "<xsl:function name=\"f:c\"><xsl:copy/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:sequence select=\"f:c()\"/></xsl:template>"), sSrc, "XTTE0945"),
      // XTDE0410: an attribute after a child.
      err ("attribute copied after a child", specXml ("\n" +
        "<xsl:template match=\"/*\"><e><c/><xsl:for-each select=\"@x\"><xsl:copy/></xsl:for-each></e></xsl:template>"), sSrc, "XTDE0410"),
      ok ("comment and PI", specXml ("\n" +
        "<xsl:template match=\"/*\"><e><xsl:for-each select=\"comment(), processing-instruction()\"><xsl:copy/></xsl:for-each></e></xsl:template>"), sSrc, "<e><!--c--><?pi v?></e>"),
      ok ("copy-of copy-namespaces no", specXml ("\n" +
        "<xsl:template match=\"/*\"><xsl:copy-of select=\"b\" copy-namespaces=\"no\"/></xsl:template>"), sSrc, "<b>t</b>"));
  }

  /**
   * XSLT 3.0 §11.2 xsl:element and §11.3 xsl:attribute, with their dynamic
   * errors.
   */
  @Test
  public void constructSpecElementAttribute ()
  {
    specRunAll (
      ok ("computed element in a namespace", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"p:e\" namespace=\"urn:p\"/></xsl:template>"), null, "<p:e xmlns:p=\"urn:p\"/>"),
      ok ("computed element, prefix from scope", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"f:e\"/></xsl:template>"), null, "<f:e xmlns:f=\"urn:f\"/>"),
      ok ("computed element, no prefix, namespace", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"e\" namespace=\"urn:d\"/></xsl:template>"), null, "<e xmlns=\"urn:d\"/>"),
      ok ("element use-attribute-sets", specXml ("\n" +
        "<xsl:attribute-set name=\"s\"><xsl:attribute name=\"a\">1</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"e\" use-attribute-sets=\"s\"/></xsl:template>"), null, "<e a=\"1\"/>"),
      // XTDE0820: name is not a lexical QName.
      err ("invalid element name", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"{'1e'}\"/></xsl:template>"), null, "XTDE0820"),
      // XTDE0830: prefix with no namespace binding.
      err ("unbound element prefix", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"{'zz:e'}\"/></xsl:template>"), null, "XTDE0830"),
      // XTDE0835: namespace attribute not a valid URI.
      err ("invalid element namespace", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"e\" namespace=\"{'http://www.w3.org/2000/xmlns/'}\"/></xsl:template>"), null, "XTDE0835"),
      ok ("attribute with select and separator", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"a\" select=\"1 to 3\" separator=\",\"/></e></xsl:template>"), null, "<e a=\"1,2,3\"/>"),
      ok ("attribute select default separator", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"a\" select=\"1 to 3\"/></e></xsl:template>"), null, "<e a=\"1 2 3\"/>"),
      ok ("attribute in a namespace with a prefix", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"p:a\" namespace=\"urn:p\">v</xsl:attribute></e></xsl:template>"), null, "<e xmlns:p=\"urn:p\" p:a=\"v\"/>"),
      ok ("attribute replaces one of the same name", specXml ("\n" +
        "<xsl:template match=\"/\"><e a=\"1\"><xsl:attribute name=\"a\">2</xsl:attribute></e></xsl:template>"), null, "<e a=\"2\"/>"),
      // XTDE0850: not a lexical QName.
      err ("invalid attribute name", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"{'a b'}\">v</xsl:attribute></e></xsl:template>"), null, "XTDE0850"),
      // XTDE0855: the name xmlns.
      err ("attribute named xmlns", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"{'xmlns'}\">v</xsl:attribute></e></xsl:template>"), null, "XTDE0855"),
      // XTDE0860: unbound prefix.
      err ("unbound attribute prefix", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"{'zz:a'}\">v</xsl:attribute></e></xsl:template>"), null, "XTDE0860"),
      // XTDE0865: invalid namespace URI.
      err ("invalid attribute namespace", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"a\" namespace=\"{'http://www.w3.org/2000/xmlns/'}\">v</xsl:attribute></e></xsl:template>"), null, "XTDE0865"),
      // XTDE0420: attribute in a document node.
      err ("attribute in a document", specXml ("\n" +
        "<xsl:variable name=\"d\" as=\"document-node()\"><xsl:document><xsl:attribute name=\"a\">v</xsl:attribute></xsl:document></xsl:variable>\n" +
        "<xsl:template match=\"/\"><xsl:copy-of select=\"$d\"/></xsl:template>"), null, "XTDE0420"),
      ok ("standalone attribute node", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"a\" as=\"attribute()\"><xsl:attribute name=\"a\">v</xsl:attribute></xsl:variable><xsl:value-of select=\"name($a), '=', string($a)\" separator=\"\"/></xsl:template>", null, "a=v"),
      ok ("processing instruction", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:processing-instruction name=\"target\" select=\"'data'\"/></xsl:template>"), null, "<?target data?>"),
      // XTDE0890: PI name not an NCName or "xml".
      err ("PI named xml", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:processing-instruction name=\"{'xml'}\"/></xsl:template>"), null, "XTDE0890"),
      ok ("comment with select", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:comment select=\"'a', 'b'\"/></xsl:template>"), null, "<!--a b-->"));
  }

}
