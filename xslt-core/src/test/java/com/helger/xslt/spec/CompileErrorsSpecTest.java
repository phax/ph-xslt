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

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xslt/compile_errors_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class CompileErrorsSpecTest
{
  /**
   * Static errors of the XSLT 3.0 REC, one case per rule (Appendix E lists the
   * codes; the section is named per case).
   */
  @Test
  public void compileSpecStaticErrors ()
  {
    final String sTpl = "<xsl:template match=\"/\">x</xsl:template>";
    specRunAll (
      // §14.? / §15.?: grouping and merge functions in patterns.
      err ("current-group in a pattern", "<xsl:template match=\"a[current-group()]\">x</xsl:template>", null, "XTSE1060"),
      err ("current-grouping-key in a pattern", "<xsl:template match=\"a[current-grouping-key()]\">x</xsl:template>", null, "XTSE1070"),
      err ("current-merge-group in a pattern", "<xsl:template match=\"a[current-merge-group()]\">x</xsl:template>", null, "XTSE3470"),
      err ("current-merge-key in a pattern", "<xsl:template match=\"a[current-merge-key()]\">x</xsl:template>", null, "XTSE3500"),
      err ("current-group in xsl:number count", "<xsl:template match=\"/\"><xsl:number count=\"a[current-group()]\"/></xsl:template>", null, "XTSE1060"),
      // §20.? XPST0017: an unknown function in a match pattern.
      err ("unknown function in a pattern", "<xsl:template match=\"a[f:nope()]\">x</xsl:template>", null, "XPST0017"),
      // §3.? XTSE0130: a top-level element in no namespace.
      err ("top-level element in no namespace", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + "><foo/>" + sTpl + "</xsl:stylesheet>", null, "XTSE0130"),
      // §10.1 XTSE0660: two named templates of one name and precedence.
      err ("duplicate named template", "<xsl:template name=\"t\">1</xsl:template><xsl:template name=\"t\">2</xsl:template>", null, "XTSE0660"),
      // §10.3 XTSE0770: duplicate function.
      err ("duplicate function", "<xsl:function name=\"f:a\"><xsl:sequence select=\"1\"/></xsl:function><xsl:function name=\"f:a\"><xsl:sequence select=\"2\"/></xsl:function>" + sTpl, null, "XTSE0770"),
      // §9.? XTSE0630: duplicate global variable.
      err ("duplicate global variable", "<xsl:variable name=\"v\" select=\"1\"/><xsl:variable name=\"v\" select=\"2\"/>" + sTpl, null, "XTSE0630"),
      // §9.? XPST0008: a global variable that refers to itself.
      err ("self-referencing global", "<xsl:variable name=\"v\" select=\"$v\"/>" + sTpl, null, "XPST0008"),
      // §6.4 XTSE0530: invalid priority.
      err ("invalid priority", "<xsl:template match=\"a\" priority=\"high\">x</xsl:template>", null, "XTSE0530"),
      // §9.2 XTSE0010: required="yes" together with a default value.
      err ("required param with select", "<xsl:template name=\"t\"><xsl:param name=\"p\" required=\"yes\" select=\"1\"/></xsl:template>", null, "XTSE0010"),
      // §8.4.2 XTSE0010: xsl:on-empty must be last.
      err ("on-empty not last", "<xsl:template match=\"/\"><xsl:on-empty>e</xsl:on-empty><x/></xsl:template>", null, "XTSE0010"),
      // §6.? XTSE0010: xsl:context-item outside xsl:template.
      err ("context-item in for-each", "<xsl:template match=\"/\"><xsl:for-each select=\".\"><xsl:context-item/></xsl:for-each></xsl:template>", null, "XTSE0010"),
      // §11.9.2 copy-accumulators must be a boolean.
      err ("copy-accumulators not a boolean", "<xsl:template match=\"/\"><xsl:copy-of select=\".\" copy-accumulators=\"maybe\"/></xsl:template>", null, "XTSE0020"),
      // §20.2.1 xsl:key.
      err ("key with use and content", "<xsl:key name=\"k\" match=\"a\" use=\"@b\"><xsl:sequence select=\"@c\"/></xsl:key>" + sTpl, null, "XTSE1205"),
      err ("key with neither use nor content", "<xsl:key name=\"k\" match=\"a\"/>" + sTpl, null, "XTSE1205"),
      err ("key with an unknown collation", "<xsl:key name=\"k\" match=\"a\" use=\"@b\" collation=\"http://example.com/nope\"/>" + sTpl, null, "XTSE1210"),
      err ("keys with different collations", "<xsl:key name=\"k\" match=\"a\" use=\"@b\" collation=\"http://www.w3.org/2005/xpath-functions/collation/codepoint\"/>\n" +
        "<xsl:key name=\"k\" match=\"b\" use=\"@b\" collation=\"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\"/>" + sTpl, null, "XTSE1220"),
      err ("keys with different composite", "<xsl:key name=\"k\" match=\"a\" use=\"@b\" composite=\"yes\"/><xsl:key name=\"k\" match=\"b\" use=\"@b\"/>" + sTpl, null, "XTSE1222"),
      // §10.3 XTSE0080 / XTSE0760.
      err ("function in a reserved namespace", "<xsl:function name=\"fn:mine\"><xsl:sequence select=\"1\"/></xsl:function>" + sTpl, null, "XTSE0080"),
      err ("function param with select", "<xsl:function name=\"f:a\"><xsl:param name=\"p\" select=\"1\"/><xsl:sequence select=\"$p\"/></xsl:function>" + sTpl, null, "XTSE0760"),
      err ("function param with content", "<xsl:function name=\"f:a\"><xsl:param name=\"p\">1</xsl:param><xsl:sequence select=\"$p\"/></xsl:function>" + sTpl, null, "XTSE0760"),
      // §17.1 XTSE1130.
      err ("analyze-string without children", "<xsl:template match=\"/\"><xsl:analyze-string select=\"'a'\" regex=\"a\"/></xsl:template>", null, "XTSE1130"),
      // §13.1 xsl:sort.
      err ("sort with an invalid lang", "<xsl:template match=\"/\"><xsl:for-each select=\"*\"><xsl:sort select=\".\" lang=\"not a language\"/></xsl:for-each></xsl:template>", null, "XTSE0020"),
      err ("stable on a second sort", "<xsl:template match=\"/\"><xsl:for-each select=\"*\"><xsl:sort select=\".\"/><xsl:sort select=\".\" stable=\"yes\"/></xsl:for-each></xsl:template>", null, "XTSE1017"),
      // §4.? xsl:strip-space / xsl:preserve-space.
      err ("unbound strip-space prefix", "<xsl:strip-space elements=\"zz:a\"/>" + sTpl, null, "XTSE0280"),
      err ("unbound strip-space prefix wildcard", "<xsl:strip-space elements=\"zz:*\"/>" + sTpl, null, "XTSE0280"),
      err ("strip and preserve the same element", "<xsl:strip-space elements=\"a\"/><xsl:preserve-space elements=\"a\"/>" + sTpl, null, "XTSE0270"),
      // §4.4 xsl:decimal-format.
      err ("zero-digit not a zero", "<xsl:decimal-format name=\"d\" zero-digit=\"1\"/>" + sTpl, null, "XTSE1295"),
      err ("decimal-format symbols not distinct", "<xsl:decimal-format name=\"d\" decimal-separator=\",\" grouping-separator=\",\"/>" + sTpl, null, "XTSE1300"),
      err ("decimal-format symbol in the digit family", "<xsl:decimal-format name=\"d\" percent=\"5\"/>" + sTpl, null, "XTSE1300"),
      err ("conflicting decimal-formats", "<xsl:decimal-format name=\"d\" decimal-separator=\",\"/><xsl:decimal-format name=\"d\" decimal-separator=\";\"/>" + sTpl, null, "XTSE1290"),
      // §26.? xsl:character-map.
      err ("character map references an unknown map", "<xsl:character-map name=\"a\" use-character-maps=\"nope\"/>" + sTpl, null, "XTSE1590"),
      // §11.1.4 xsl:namespace-alias.
      err ("namespace-alias with an unbound prefix", "<xsl:namespace-alias stylesheet-prefix=\"zz\" result-prefix=\"f\"/>" + sTpl, null, "XTSE0812"),
      err ("conflicting namespace aliases", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:a=\"urn:a\" xmlns:b=\"urn:b\">\n" +
        "<xsl:namespace-alias stylesheet-prefix=\"f\" result-prefix=\"a\"/><xsl:namespace-alias stylesheet-prefix=\"f\" result-prefix=\"b\"/>" + sTpl + "</xsl:stylesheet>", null, "XTSE0810"));
  }

  /**
   * Declarations that compile and take effect (positive counterparts).
   */
  @Test
  public void compileSpecDeclarations ()
  {
    specRunAll (
      // §11.1.4: the REC's own use of namespace-alias, generating a stylesheet.
      ok ("namespace-alias", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:axsl=\"urn:alias\">\n" +
        "<xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>\n" +
        "<xsl:namespace-alias stylesheet-prefix=\"axsl\" result-prefix=\"xsl\"/>\n" +
        "<xsl:template match=\"/\"><axsl:stylesheet version=\"3.0\"/></xsl:template></xsl:stylesheet>", null, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"/>"),
      ok ("namespace-alias to the null namespace", "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:a=\"urn:alias\">\n" +
        "<xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>\n" +
        "<xsl:namespace-alias stylesheet-prefix=\"a\" result-prefix=\"#default\"/>\n" +
        "<xsl:template match=\"/\"><a:e/></xsl:template></xsl:stylesheet>", null, "<e/>"),
      // §4.4 + F&O 3.1 §4.7 format-number with a named decimal format.
      ok ("named decimal-format", "<xsl:decimal-format name=\"de\" decimal-separator=\",\" grouping-separator=\".\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"format-number(1234.5, '#.##0,00', 'de')\"/></xsl:template>", null, "1.234,50"),
      ok ("decimal-format special symbols", "<xsl:decimal-format name=\"s\" percent=\"%\" minus-sign=\"-\" NaN=\"NaN!\" infinity=\"inf\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"format-number(0.5, '0%', 's'), format-number(-1, '0', 's'), format-number(number('x'), '0', 's'), format-number(1 div 0e0, '0', 's')\"/></xsl:template>", null, "50% -1 NaN! inf"),
      ok ("repeated identical decimal-formats", "<xsl:decimal-format name=\"d\" decimal-separator=\",\" grouping-separator=\".\"/><xsl:decimal-format name=\"d\" decimal-separator=\",\" grouping-separator=\".\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"format-number(1.5, '0,0', 'd')\"/></xsl:template>", null, "1,5"),
      // §4.3 whitespace stripping.
      ok ("strip-space and preserve-space", "<xsl:strip-space elements=\"*\"/><xsl:preserve-space elements=\"p\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(//text())\"/></xsl:template>", "<r> <p> </p> <q> </q></r>", "1"),
      ok ("strip-space by namespace wildcard", "<xsl:strip-space elements=\"f:*\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(//text())\"/></xsl:template>", "<r xmlns:p=\"urn:f\"><p:a> </p:a><b> </b></r>", "1"),
      ok ("xml:space preserve wins over strip-space", "<xsl:strip-space elements=\"*\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(//text())\"/></xsl:template>", "<r><a xml:space=\"preserve\"> </a><b> </b></r>", "1"),
      ok ("key with a sequence constructor", "<xsl:key name=\"k\" match=\"a\"><xsl:sequence select=\"@b\"/></xsl:key>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k', '1')\"/></xsl:template>", "<r><a b=\"1\">x</a></r>", "x"),
      ok ("same function name, different arity", "<xsl:function name=\"f:a\"><xsl:sequence select=\"1\"/></xsl:function><xsl:function name=\"f:a\"><xsl:param name=\"x\"/><xsl:sequence select=\"$x\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:a(), f:a(2)\"/></xsl:template>", null, "1 2"));
  }

}
