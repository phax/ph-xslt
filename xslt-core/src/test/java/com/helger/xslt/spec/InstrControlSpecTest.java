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
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunIn;
import static com.helger.xslt.spec.XsltSpecTestHelper.specXml;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.xslt.XsltTransformResult;

/**
 * Port of go-xslt <code>internal/xslt/instr_control_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrControlSpecTest
{
  private static final String CTL_CITIES = "<cities>\n" +
        "<city name=\"Milano\" country=\"Italia\" pop=\"5\"/>\n" +
        "<city name=\"Paris\" country=\"France\" pop=\"7\"/>\n" +
        "<city name=\"München\" country=\"Deutschland\" pop=\"4\"/>\n" +
        "<city name=\"Lyon\" country=\"France\" pop=\"2\"/>\n" +
        "<city name=\"Venezia\" country=\"Italia\" pop=\"1\"/>\n" +
        "</cities>";

  /**
   * XSLT 3.0 §14 grouping (the REC's own city/country example and friends).
   */
  @Test
  public void groupSpec ()
  {
    specRunAll (
      ok ("group-by with current-group and current-grouping-key", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"cities/city\" group-by=\"@country\"><xsl:value-of select=\"current-grouping-key()\"/>:<xsl:value-of select=\"current-group()/@name\" separator=\",\"/>=<xsl:value-of select=\"sum(current-group()/@pop)\"/>;</xsl:for-each-group></xsl:template>", CTL_CITIES, "Italia:Milano,Venezia=6;France:Paris,Lyon=9;Deutschland:München=4;"),
      ok ("group-by with sort by key", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"cities/city\" group-by=\"@country\"><xsl:sort select=\"current-grouping-key()\"/><xsl:value-of select=\"current-grouping-key()\"/><xsl:text> </xsl:text></xsl:for-each-group></xsl:template>", CTL_CITIES, "Deutschland France Italia "),
      // An item with several keys joins several groups.
      ok ("group-by with several keys per item", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r/i\" group-by=\"tokenize(@k)\"><xsl:value-of select=\"current-grouping-key()\"/>:<xsl:value-of select=\"current-group()\" separator=\"\"/>;</xsl:for-each-group></xsl:template>", "<r><i k=\"a b\">1</i><i k=\"b\">2</i></r>", "a:1;b:12;"),
      ok ("composite group-by", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r/i\" group-by=\"@a, @b\" composite=\"yes\"><xsl:value-of select=\"current-grouping-key()\"/>:<xsl:value-of select=\"count(current-group())\"/>;</xsl:for-each-group></xsl:template>", "<r><i a=\"1\" b=\"x\"/><i a=\"1\" b=\"y\"/><i a=\"1\" b=\"x\"/></r>", "1 x:2;1 y:1;"),
      ok ("group-by with a collation", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r/i\" group-by=\"@k\" collation=\"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\"><xsl:value-of select=\"count(current-group())\"/><xsl:text> </xsl:text></xsl:for-each-group></xsl:template>", "<r><i k=\"A\"/><i k=\"a\"/><i k=\"B\"/></r>", "2 1 "),
      ok ("group-adjacent", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"1, 1, 2, 1, 1\" group-adjacent=\".\"><xsl:value-of select=\"current-group()\" separator=\",\"/>|</xsl:for-each-group></xsl:template>", null, "1,1|2|1,1|"),
      ok ("composite group-adjacent", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r/i\" group-adjacent=\"@a, @b\" composite=\"yes\"><xsl:value-of select=\"count(current-group())\"/><xsl:text> </xsl:text></xsl:for-each-group></xsl:template>", "<r><i a=\"1\" b=\"x\"/><i a=\"1\" b=\"x\"/><i a=\"2\" b=\"x\"/></r>", "2 1 "),
      // §14.? the REC's flat-to-hierarchy example.
      ok ("group-starting-with", specXml ("\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"body/*\" group-starting-with=\"h2\"><s><h><xsl:value-of select=\".\"/></h><xsl:copy-of select=\"current-group()[position() gt 1]\"/></s></xsl:for-each-group></xsl:template>"), "<body><h2>A</h2><p>1</p><p>2</p><h2>B</h2><p>3</p></body>", "<s><h>A</h><p>1</p><p>2</p></s><s><h>B</h><p>3</p></s>"),
      ok ("group-ending-with", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r/p\" group-ending-with=\"p[@c]\"><xsl:value-of select=\"current-group()\" separator=\"\"/>|</xsl:for-each-group></xsl:template>", "<r><p c=\"y\">1</p><p>2</p><p c=\"n\">3</p><p>4</p></r>", "1|23|4|"),
      // XTDE1071: no current grouping key with group-starting-with.
      err ("current-grouping-key with group-starting-with", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r/*\" group-starting-with=\"a\"><xsl:value-of select=\"current-grouping-key()\"/></xsl:for-each-group></xsl:template>", "<r><a/></r>", "XTDE1071"),
      err ("current-grouping-key inside a function", "\n" +
        "<xsl:function name=\"f:k\"><xsl:sequence select=\"current-grouping-key()\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:k()\"/></xsl:template>", null, "XTDE1071"),
      err ("current-group inside a function", "\n" +
        "<xsl:function name=\"f:g\"><xsl:sequence select=\"current-group()\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:g()\"/></xsl:template>", null, "XTDE1061"),
      // XTTE1100: a group-adjacent key of more than one item.
      err ("group-adjacent key sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"1, 2\" group-adjacent=\"(., .)\"><xsl:value-of select=\".\"/></xsl:for-each-group></xsl:template>", null, "XTTE1100"),
      // XTSE1090: @collation without group-by / group-adjacent.
      err ("collation with group-starting-with", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"*\" group-starting-with=\"a\" collation=\"http://www.w3.org/2005/xpath-functions/collation/codepoint\"><x/></xsl:for-each-group></xsl:template>", null, "XTSE1090"));
  }

  /**
   * XSLT 3.0 §7.? xsl:iterate.
   */
  @Test
  public void iterateSpec ()
  {
    specRunAll (
      // The REC's running-balance example (shortened).
      ok ("running total with on-completion", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 3\"><xsl:param name=\"sum\" select=\"0\"/><xsl:on-completion>total=<xsl:value-of select=\"$sum\"/></xsl:on-completion><xsl:value-of select=\"$sum + .\"/><xsl:text> </xsl:text><xsl:next-iteration><xsl:with-param name=\"sum\" select=\"$sum + .\"/></xsl:next-iteration></xsl:iterate></xsl:template>", null, "1 3 6 total=6"),
      ok ("break with select", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 10\"><xsl:choose><xsl:when test=\". eq 3\"><xsl:break select=\"'stop'\"/></xsl:when><xsl:otherwise><xsl:value-of select=\".\"/><xsl:text> </xsl:text></xsl:otherwise></xsl:choose></xsl:iterate></xsl:template>", null, "1 2 stop"),
      ok ("on-completion with an empty sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"()\"><xsl:on-completion select=\"'none'\"/><xsl:value-of select=\".\"/></xsl:iterate></xsl:template>", null, "none"),
      ok ("next-iteration in a choose", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 4\"><xsl:param name=\"odd\" select=\"true()\"/><xsl:choose><xsl:when test=\"$odd\"><xsl:value-of select=\".\"/><xsl:next-iteration><xsl:with-param name=\"odd\" select=\"false()\"/></xsl:next-iteration></xsl:when><xsl:otherwise><xsl:next-iteration><xsl:with-param name=\"odd\" select=\"true()\"/></xsl:next-iteration></xsl:otherwise></xsl:choose></xsl:iterate></xsl:template>", null, "13"),
      ok ("typed iterate parameter", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 3\"><xsl:param name=\"p\" as=\"xs:integer\" select=\"0\"/><xsl:on-completion><xsl:value-of select=\"$p\"/></xsl:on-completion><xsl:next-iteration><xsl:with-param name=\"p\" select=\"$p + .\"/></xsl:next-iteration></xsl:iterate></xsl:template>", null, "6"),
      // XTSE3120: next-iteration / break not in tail position.
      err ("next-iteration not in tail position", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 2\"><xsl:next-iteration/><xsl:value-of select=\".\"/></xsl:iterate></xsl:template>", null, "XTSE3120"),
      err ("break not in tail position", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 2\"><xsl:break/><xsl:value-of select=\".\"/></xsl:iterate></xsl:template>", null, "XTSE3120"),
      // XTSE3130: next-iteration names a parameter the iterate lacks.
      err ("undeclared iteration parameter", "\n" +
        "<xsl:template match=\"/\"><xsl:iterate select=\"1 to 2\"><xsl:param name=\"a\" select=\"0\"/><xsl:next-iteration><xsl:with-param name=\"b\" select=\"1\"/></xsl:next-iteration></xsl:iterate></xsl:template>", null, "XTSE3130"));
  }

  /**
   * XSLT 3.0 §8.3 xsl:try / xsl:catch.
   */
  @Test
  public void tryCatchSpec ()
  {
    specRunAll (
      ok ("error variables", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:value-of select=\"1 idiv 0\"/><xsl:catch><xsl:value-of select=\"local-name-from-QName($err:code), $err:description ne ''\" separator=\"|\" xmlns:err=\"http://www.w3.org/2005/xqt-errors\"/></xsl:catch></xsl:try></xsl:template>", null, "FOAR0001|true"),
      ok ("user error with value", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:sequence select=\"error(QName('urn:x', 'e'), 'msg', 42)\"/><xsl:catch errors=\"Q{urn:x}e\" xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"local-name-from-QName($err:code), $err:value\" separator=\"|\"/></xsl:catch></xsl:try></xsl:template>", null, "e|42"),
      ok ("namespace wildcard", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:sequence select=\"error(QName('urn:x', 'e'))\"/><xsl:catch errors=\"Q{urn:x}*\">caught</xsl:catch></xsl:try></xsl:template>", null, "caught"),
      ok ("local-name wildcard", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:sequence select=\"error(QName('urn:y', 'e'))\"/><xsl:catch errors=\"*:e\">caught</xsl:catch></xsl:try></xsl:template>", null, "caught"),
      ok ("first matching catch wins", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:sequence select=\"1 idiv 0\"/><xsl:catch errors=\"err:XPTY0004\" xmlns:err=\"http://www.w3.org/2005/xqt-errors\">first</xsl:catch><xsl:catch errors=\"err:FOAR0001 err:FOAR0002\" xmlns:err=\"http://www.w3.org/2005/xqt-errors\">second</xsl:catch><xsl:catch>third</xsl:catch></xsl:try></xsl:template>", null, "second"),
      err ("no matching catch propagates", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:sequence select=\"1 idiv 0\"/><xsl:catch errors=\"err:XPTY0004\" xmlns:err=\"http://www.w3.org/2005/xqt-errors\">no</xsl:catch></xsl:try></xsl:template>", null, "FOAR0001"),
      ok ("nested try rethrows", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:try><xsl:sequence select=\"1 idiv 0\"/><xsl:catch><xsl:sequence select=\"error(QName('urn:x', 'again'))\"/></xsl:catch></xsl:try><xsl:catch errors=\"Q{urn:x}again\">outer</xsl:catch></xsl:try></xsl:template>", null, "outer"),
      ok ("catch with select", "\n" +
        "<xsl:template match=\"/\"><xsl:try select=\"1 idiv 0\"><xsl:catch select=\"'fallback'\"/></xsl:try></xsl:template>", null, "fallback"),
      ok ("partial output is rolled back", specXml ("\n" +
        "<xsl:template match=\"/\"><r><xsl:try><partial/><xsl:sequence select=\"1 idiv 0\"/><xsl:catch>caught</xsl:catch></xsl:try></r></xsl:template>"), null, "<r>caught</r>"),
      ok ("dynamic error code in the catch", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:element name=\"{'1x'}\"/><xsl:catch xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"local-name-from-QName($err:code)\"/></xsl:catch></xsl:try></xsl:template>", null, "XTDE0820"));
  }

  /**
   * XSLT 3.0 §21.? xsl:map / xsl:map-entry.
   */
  @Test
  public void mapSpec ()
  {
    specRunAll (
      ok ("entries and nesting", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"m\" as=\"map(*)\"><xsl:map><xsl:map-entry key=\"'a'\" select=\"1\"/><xsl:map-entry key=\"'b'\"><xsl:map><xsl:map-entry key=\"'c'\" select=\"'x', 'y'\"/></xsl:map></xsl:map-entry></xsl:map></xsl:variable><xsl:value-of select=\"map:size($m), $m?a, $m?b?c\"/></xsl:template>", null, "2 1 x y"),
      ok ("map built from a for-each", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"m\" as=\"map(*)\"><xsl:map><xsl:for-each select=\"r/i\"><xsl:map-entry key=\"string(@k)\" select=\"string(@v)\"/></xsl:for-each></xsl:map></xsl:variable><xsl:value-of select=\"for $k in sort(map:keys($m)) return $k || '=' || $m($k)\"/></xsl:template>", "<r><i k=\"a\" v=\"1\"/><i k=\"b\" v=\"2\"/></r>", "a=1 b=2"),
      ok ("maps merged from xsl:sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"m\" as=\"map(*)\"><xsl:map><xsl:sequence select=\"map{1: 'a'}, map{2: 'b'}\"/><xsl:map-entry key=\"3\" select=\"'c'\"/></xsl:map></xsl:variable><xsl:value-of select=\"map:size($m)\"/></xsl:template>", null, "3"),
      err ("duplicate keys from xsl:sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"m\" as=\"map(*)\"><xsl:map><xsl:sequence select=\"map{1: 'a'}\"/><xsl:map-entry key=\"1\" select=\"'b'\"/></xsl:map></xsl:variable><xsl:value-of select=\"map:size($m)\"/></xsl:template>", null, "XTDE3365"),
      // XTSE3280: map-entry with both select and content.
      err ("map-entry select and content", "\n" +
        "<xsl:template match=\"/\"><xsl:map><xsl:map-entry key=\"1\" select=\"2\">3</xsl:map-entry></xsl:map></xsl:template>", null, "XTSE3280"));
  }

  /**
   * XSLT 3.0 §23.1 xsl:message and §23.2 xsl:assert.
   */
  @Test
  public void messageSpec ()
  {
    specRunAll (
      err ("terminate yes", "\n" +
        "<xsl:template match=\"/\"><xsl:message terminate=\"yes\">stop</xsl:message></xsl:template>", null, "XTMM9000"),
      err ("terminate AVT", "\n" +
        "<xsl:template match=\"/\"><xsl:message terminate=\"{'yes'}\">stop</xsl:message></xsl:template>", null, "XTMM9000"),
      err ("terminate invalid", "\n" +
        "<xsl:template match=\"/\"><xsl:message terminate=\"{'maybe'}\">stop</xsl:message></xsl:template>", null, "XTDE0030"),
      err ("assert with error-code", "\n" +
        "<xsl:template match=\"/\"><xsl:assert test=\"1 = 2\" error-code=\"f:BAD\" select=\"'no'\"/></xsl:template>", null, "BAD"),
      err ("assert failing", "\n" +
        "<xsl:template match=\"/\"><xsl:assert test=\"false()\"/></xsl:template>", null, "XTMM9001"),
      err ("assert on an empty sequence fails", "\n" +
        "<xsl:template match=\"/\"><xsl:assert test=\"()\">empty</xsl:assert></xsl:template>", null, "XTMM9001"));
    final XsltTransformResult aResult = specRunIn (null,
                                                   "<xsl:template match=\"/\"><xsl:message select=\"'a', 'b'\"/><xsl:message>body <xsl:value-of select=\"1 + 1\"/></xsl:message>done</xsl:template>",
                                                   null);
    assertEquals ("done", aResult.principalSerialized ());
    assertEquals (2, aResult.messages ().size ());
    assertEquals ("a b", aResult.messages ().get (0));
    assertEquals ("body 2", aResult.messages ().get (1));
  }

  /**
   * XSLT 3.0 §5.6.1 attribute value templates.
   */
  @Test
  public void avtSpec ()
  {
    specRunAll (
      ok ("doubled braces", specXml ("<xsl:template match=\"/\"><e a=\"{{x}}\"/></xsl:template>"), null, "<e a=\"{x}\"/>"),
      ok ("brace inside a string literal", specXml ("<xsl:template match=\"/\"><e a=\"{'}' || '{'}\"/></xsl:template>"), null, "<e a=\"}{\"/>"),
      ok ("XPath comment inside", specXml ("<xsl:template match=\"/\"><e a=\"{1 (: one } :) + 1}\"/></xsl:template>"), null, "<e a=\"2\"/>"),
      ok ("sequence joined by spaces", specXml ("<xsl:template match=\"/\"><e a=\"{1 to 3}\"/></xsl:template>"), null, "<e a=\"1 2 3\"/>"),
      ok ("several parts", specXml ("<xsl:template match=\"/\"><e a=\"x{1}y{2}z\"/></xsl:template>"), null, "<e a=\"x1y2z\"/>"),
      ok ("nested map constructor", specXml ("<xsl:template match=\"/\"><e a=\"{map{'k': 'v'}?k}\"/></xsl:template>"), null, "<e a=\"v\"/>"),
      // XTSE0370: an unescaped "}" outside an expression.
      err ("lone closing brace", specXml ("<xsl:template match=\"/\"><e a=\"x}y\"/></xsl:template>"), null, "XTSE0370"));
  }

  /**
   * XSLT 3.0 §10.2 attribute sets and §10.1.? call-template parameter checks.
   */
  @Test
  public void attributeSetSpec ()
  {
    specRunAll (
      ok ("attribute sets using others", ("\n" +
        "<xsl:attribute-set name=\"s1\" use-attribute-sets=\"s2\"><xsl:attribute name=\"a\">1</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:attribute-set name=\"s2\"><xsl:attribute name=\"b\">2</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:attribute-set name=\"s3\"><xsl:attribute name=\"c\">3</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"e\"><e xsl:use-attribute-sets=\"s1 s3\"/></xsl:variable><xsl:value-of select=\"sort($e/e/@*, (), name#1) ! (name(.) || '=' || .)\"/></xsl:template>"), null, "a=1 b=2 c=3"),
      // Later attributes override earlier ones; the element's own win.
      ok ("override order", specXml ("\n" +
        "<xsl:attribute-set name=\"s\"><xsl:attribute name=\"a\">set</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:template match=\"/\"><e xsl:use-attribute-sets=\"s\" a=\"own\"/></xsl:template>"), null, "<e a=\"own\"/>"),
      ok ("attribute set merged across declarations", ("\n" +
        "<xsl:attribute-set name=\"s\"><xsl:attribute name=\"a\">1</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:attribute-set name=\"s\"><xsl:attribute name=\"b\">2</xsl:attribute></xsl:attribute-set>\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"e\"><e xsl:use-attribute-sets=\"s\"/></xsl:variable><xsl:value-of select=\"sort($e/e/@*, (), name#1) ! (name(.) || '=' || .)\"/></xsl:template>"), null, "a=1 b=2"),
      ok ("attribute set sees the context node", specXml ("\n" +
        "<xsl:attribute-set name=\"s\"><xsl:attribute name=\"id\" select=\"/r/@id\"/></xsl:attribute-set>\n" +
        "<xsl:template match=\"/\"><xsl:element name=\"e\" use-attribute-sets=\"s\"/></xsl:template>"), "<r id=\"7\"/>", "<e id=\"7\"/>"),
      // XTSE0710: an undeclared attribute set.
      err ("undeclared attribute set", specXml ("\n" +
        "<xsl:template match=\"/\"><e xsl:use-attribute-sets=\"nope\"/></xsl:template>"), null, "XTSE0710"),
      err ("undeclared attribute set in a declaration", specXml ("\n" +
        "<xsl:attribute-set name=\"s\" use-attribute-sets=\"nope\"/>\n" +
        "<xsl:template match=\"/\"><e/></xsl:template>"), null, "XTSE0710"),
      // XTSE0720: an attribute set that uses itself.
      err ("circular attribute sets", specXml ("\n" +
        "<xsl:attribute-set name=\"a\" use-attribute-sets=\"b\"/><xsl:attribute-set name=\"b\" use-attribute-sets=\"a\"/>\n" +
        "<xsl:template match=\"/\"><e xsl:use-attribute-sets=\"a\"/></xsl:template>"), null, "XTSE0720"),
      // XTSE0680: a non-tunnel with-param the called template does not declare.
      err ("call-template with an undeclared parameter", "\n" +
        "<xsl:template name=\"t\"><xsl:param name=\"a\"/></xsl:template>\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"><xsl:with-param name=\"b\" select=\"1\"/></xsl:call-template></xsl:template>", null, "XTSE0680"),
      ok ("tunnel parameters pass through", "\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"a\"><xsl:with-param name=\"t\" select=\"'T'\" tunnel=\"yes\"/></xsl:call-template></xsl:template>\n" +
        "<xsl:template name=\"a\"><xsl:call-template name=\"b\"/></xsl:template>\n" +
        "<xsl:template name=\"b\"><xsl:param name=\"t\" tunnel=\"yes\"/><xsl:value-of select=\"$t\"/></xsl:template>", null, "T"),
      // XTTE0590: a parameter value that does not match @as.
      err ("template parameter type", "\n" +
        "<xsl:template name=\"t\"><xsl:param name=\"a\" as=\"xs:integer\"/><xsl:value-of select=\"$a\"/></xsl:template>\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"><xsl:with-param name=\"a\" select=\"'x'\"/></xsl:call-template></xsl:template>", null, "XTTE0590"),
      // XTTE0505: a template result that does not match @as.
      err ("template result type", "\n" +
        "<xsl:template name=\"t\" as=\"xs:integer\"><xsl:sequence select=\"'x'\"/></xsl:template>\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"/></xsl:template>", null, "XTTE0505"));
  }

  /**
   * Former BUG: entries, now checked against the REC.
   */
  @Test
  public void instrControlSpecFixed ()
  {
    specRunAll (
      // §14.2.1: an invocation construct other than a function call (and
      // outside a declared-streamable construct) leaves the current group
      // unchanged, so a template applied from xsl:for-each-group sees it.
      ok ("current-group in an applied template", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"r\" group-by=\"name()\"><xsl:apply-templates select=\".\"/></xsl:for-each-group></xsl:template>\n" +
        "<xsl:template match=\"r\"><xsl:value-of select=\"count(current-group())\"/></xsl:template>", "<r/>", "1"),
      // §23.1: terminate="yes" raises the error code named by @error-code.
      err ("terminate with error-code", "\n" +
        "<xsl:template match=\"/\"><xsl:message terminate=\"yes\" error-code=\"f:MYERR\">stop</xsl:message></xsl:template>", null, "MYERR"),
      ok ("terminate can be caught", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:message terminate=\"yes\" error-code=\"f:MYERR\">stop</xsl:message><xsl:catch xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"'Q{' || namespace-uri-from-QName($err:code) || '}' || local-name-from-QName($err:code)\"/></xsl:catch></xsl:try></xsl:template>", null, "Q{urn:f}MYERR"),
      ok ("error-code as an AVT in Q{} form", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:message terminate=\"yes\" error-code=\"{'Q{urn:g}E' || 2}\">stop</xsl:message><xsl:catch xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"'Q{' || namespace-uri-from-QName($err:code) || '}' || local-name-from-QName($err:code)\"/></xsl:catch></xsl:try></xsl:template>", null, "Q{urn:g}E2"),
      // §23.1: an effective value that is not a valid EQName gives XTMM9000.
      ok ("invalid error-code falls back to XTMM9000", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:message terminate=\"yes\" error-code=\"not a name\">stop</xsl:message><xsl:catch xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"local-name-from-QName($err:code)\"/></xsl:catch></xsl:try></xsl:template>", null, "XTMM9000"),
      // §8.3: $err:description is the description passed to fn:error.
      ok ("err:description from fn:error", "\n" +
        "<xsl:template match=\"/\"><xsl:try><xsl:sequence select=\"error(QName('urn:e', 'e:e'), 'msg', 42)\"/><xsl:catch xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"$err:description\"/></xsl:catch></xsl:try></xsl:template>", null, "msg"));
  }

  /**
   * Behaviour that disagrees with the REC, skipped with the bug it shows.
   */
  @Test
  public void instrControlKnownBugs ()
  {
    specRunAll (
      // go-xslt BUG: xsl:for-each-group with two grouping attributes raises XTSE1300 (a decimal-format code); §14.1 defines XTSE1080
      err ("two grouping attributes", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"*\" group-by=\".\" group-adjacent=\".\"><x/></xsl:for-each-group></xsl:template>", null, "XTSE1080"),
      // go-xslt BUG: xsl:for-each-group with no grouping attribute raises XTSE1300; §14.1 defines XTSE1080
      err ("no grouping attribute", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each-group select=\"*\"><x/></xsl:for-each-group></xsl:template>", null, "XTSE1080"),
      // go-xslt BUG: xsl:try without xsl:catch fails without an error code; the content model violation is XTSE0010
      err ("try without catch", "\n" +
        "<xsl:template match=\"/\"><xsl:try><x/></xsl:try></xsl:template>", null, "XTSE0010"),
      // go-xslt BUG: an xsl:map-entry key of two items fails without an error code; §21.? requires XPTY0004
      err ("map-entry key sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"m\" as=\"map(*)\"><xsl:map><xsl:map-entry key=\"1, 2\" select=\"3\"/></xsl:map></xsl:variable><xsl:value-of select=\"map:size($m)\"/></xsl:template>", null, "XPTY0004"),
      // go-xslt BUG: xsl:map content that is not a map is reported only by the variable's @as (XTTE0570); §21.? defines XTTE3375 for xsl:map itself
      err ("non-map content", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"m\" as=\"map(*)\"><xsl:map><xsl:sequence select=\"1\"/></xsl:map></xsl:variable><xsl:value-of select=\"map:size($m)\"/></xsl:template>", null, "XTTE3375"),
      // go-xslt BUG: an unmatched { in an AVT fails without an error code; §5.6.1 defines XTSE0350
      err ("unclosed brace", specXml ("<xsl:template match=\"/\"><e a=\"x{1\"/></xsl:template>"), null, "XTSE0350"),
      // go-xslt BUG: an unterminated comment in an AVT expression fails without an error code (XTSE0350, the expression is not closed)
      err ("unclosed comment", specXml ("<xsl:template match=\"/\"><e a=\"{1 (: c }\"/></xsl:template>"), null, "XTSE0350"),
      // go-xslt BUG: xsl:call-template omitting a required non-tunnel parameter raises the dynamic XTDE0700; §10.1.? makes it the static XTSE0690
      err ("call-template missing a required parameter", "\n" +
        "<xsl:template name=\"t\"><xsl:param name=\"a\" required=\"yes\"/></xsl:template>\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"/></xsl:template>", null, "XTSE0690"),
      // go-xslt BUG: xsl:call-template naming no template fails without an error code; §10.1 defines XTSE0650
      err ("call-template to an unknown template", "\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"nope\"/></xsl:template>", null, "XTSE0650"));
  }

}
