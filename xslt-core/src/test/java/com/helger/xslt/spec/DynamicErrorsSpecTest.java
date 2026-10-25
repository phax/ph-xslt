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
import static org.junit.Assert.assertEquals;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt <code>internal/xslt/dynamic_errors_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class DynamicErrorsSpecTest
{
  private static final XdmQName INITIAL_TEMPLATE = new XdmQName ("http://www.w3.org/1999/XSL/Transform", "initial-template");

  @NonNull
  private static String _outSheet (@NonNull final String sOutput, @NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"3.0\" " +
           SPEC_NS +
           ">\n<xsl:output " +
           sOutput +
           "/>\n<xsl:template match=\"/\">" +
           sBody +
           "</xsl:template>\n</xsl:stylesheet>";
  }

  /**
   * Run an entry point.
   *
   * @return The output, or the error code.
   */
  @NonNull
  private static String _runEntry (@NonNull final XsltExecutable aExec,
                                   @Nullable final String sSrc,
                                   @Nullable final XdmQName aTemplate,
                                   @Nullable final XdmQName aMode)
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    if (sSrc != null)
      aRequest.setGlobalContextItem (XsltTestHelper.parse (sSrc));
    aRequest.setInitialTemplate (aTemplate);
    aRequest.setInitialMode (aMode);
    try
    {
      return aExec.transform (aRequest).principalSerialized ();
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * Dynamic and serialization errors of XSLT 3.0 / XPath 3.1 / Serialization
   * 3.1, one case per rule.
   */
  @Test
  public void specDynamicErrors ()
  {
    specRunAll (
      // Serialization 3.1 §5: SEPM0009 omit-xml-declaration with a doctype under XML 1.1.
      err ("omitted declaration with doctype at XML 1.1", _outSheet ("method=\"xml\" version=\"1.1\" omit-xml-declaration=\"yes\" doctype-system=\"e.dtd\"", "<e/>"), null, "SEPM0009"),
      // doctype-public must be a PubidLiteral.
      err ("invalid doctype-public", _outSheet ("method=\"xml\" doctype-public=\"a~b\" doctype-system=\"e.dtd\"", "<e/>"), null, "SEPM0016"),
      err ("invalid html-version", _outSheet ("method=\"html\" html-version=\"five\"", "<html/>"), null, "XTSE0020"),
      // SENR0001: a map in a sequence serialized with the xml method.
      err ("map serialized as xml", _outSheet ("method=\"xml\"", "<xsl:sequence select=\"map{}\"/>"), null, "SENR0001"),
      // XTDE1490: principal output written after an href-less xsl:result-document produced it.
      err ("principal written after result-document", "\n" +
        "<xsl:template match=\"/\"><xsl:result-document>a</xsl:result-document>b</xsl:template>", null, "XTDE1490"),
      // The engine's recursion limits raise XPDY0130 (documented in CLAUDE.md).
      err ("unbounded template recursion", "\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"/></xsl:template>\n" +
        "<xsl:template name=\"t\"><x/><xsl:call-template name=\"t\"/></xsl:template>", null, "XPDY0130"),
      err ("unbounded function recursion", "\n" +
        "<xsl:function name=\"f:r\"><xsl:param name=\"n\"/><xsl:sequence select=\"f:r($n + 1)\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:r(0)\"/></xsl:template>", null, "XPDY0130"),
      // XTTE0570: a local variable whose value does not match @as.
      err ("local variable type", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"v\" as=\"xs:integer\" select=\"'a'\"/><xsl:value-of select=\"$v\"/></xsl:template>", null, "XTTE0570"),
      err ("local variable content type", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"v\" as=\"xs:integer\"><xsl:sequence select=\"'a'\"/></xsl:variable><xsl:value-of select=\"$v\"/></xsl:template>", null, "XTTE0570"),
      err ("local variable cardinality", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"v\" as=\"xs:integer\" select=\"1, 2\"/><xsl:value-of select=\"$v\"/></xsl:template>", null, "XTTE0570"),
      // XTDE0420: an attribute in a temporary tree's document node.
      err ("attribute in a temporary tree", "\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"v\"><xsl:attribute name=\"a\">1</xsl:attribute></xsl:variable><xsl:value-of select=\"$v\"/></xsl:template>", null, "XTDE0420"),
      // FOTY0013: xsl:value-of / xsl:attribute of a map.
      err ("value-of a map", "\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"map{1: 2}\"/></xsl:template>", null, "FOTY0013"),
      err ("attribute of a function item", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:attribute name=\"a\" select=\"true#0\"/></e></xsl:template>"), null, "FOTY0013"),
      // XTTE0510: apply-templates without select when the context item is not a node.
      err ("apply-templates on an atomic context", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each select=\"1\"><xsl:apply-templates/></xsl:for-each></xsl:template>", null, "XTTE0510"),
      // XTTE3100: a typed="yes" mode applied to an untyped node.
      err ("typed mode on an untyped node", "\n" +
        "<xsl:mode name=\"t\" typed=\"yes\"/>\n" +
        "<xsl:template match=\"/\"><xsl:apply-templates select=\"*\" mode=\"t\"/></xsl:template>\n" +
        "<xsl:template match=\"*\" mode=\"t\">x</xsl:template>", null, "XTTE3100"),
      ok ("typed=no mode on an untyped node", "\n" +
        "<xsl:mode name=\"t\" typed=\"no\"/>\n" +
        "<xsl:template match=\"/\"><xsl:apply-templates select=\"*\" mode=\"t\"/></xsl:template>\n" +
        "<xsl:template match=\"*\" mode=\"t\">x</xsl:template>", null, "x"),
      // XTDE0410: an attribute node in content after a child.
      err ("attribute node after a child", specXml ("\n" +
        "<xsl:variable name=\"a\" as=\"attribute()\"><xsl:attribute name=\"a\">1</xsl:attribute></xsl:variable>\n" +
        "<xsl:template match=\"/\"><e><c/><xsl:sequence select=\"$a\"/></e></xsl:template>"), null, "XTDE0410"),
      ok ("attribute node before a child", specXml ("\n" +
        "<xsl:variable name=\"a\" as=\"attribute()\"><xsl:attribute name=\"a\">1</xsl:attribute></xsl:variable>\n" +
        "<xsl:template match=\"/\"><e><xsl:sequence select=\"$a\"/><c/></e></xsl:template>"), null, "<e a=\"1\"><c/></e>"),
      // XTDE0450: a function item in element content.
      err ("function item in element content", specXml ("\n" +
        "<xsl:template match=\"/\"><e><xsl:sequence select=\"true#0\"/></e></xsl:template>"), null, "XTDE0450"),
      // XPTY0004: analyze-string select is not a single string.
      err ("analyze-string over a sequence", "\n" +
        "<xsl:template match=\"/\"><xsl:analyze-string select=\"('a', 'b')\" regex=\"a\"><xsl:matching-substring>m</xsl:matching-substring></xsl:analyze-string></xsl:template>", null, "XPTY0004"),
      // XSLT 3.0 (unlike 2.0) allows a regex that matches the empty string.
      ok ("regex matching the empty string", "\n" +
        "<xsl:template match=\"/\"><xsl:analyze-string select=\"'a'\" regex=\"{'x*'}\"><xsl:matching-substring>m</xsl:matching-substring></xsl:analyze-string></xsl:template>", null, "mm"),
      ok ("analyze-string with flags", "\n" +
        "<xsl:template match=\"/\"><xsl:analyze-string select=\"'aba'\" regex=\"A\" flags=\"i\"><xsl:matching-substring>[A]</xsl:matching-substring><xsl:non-matching-substring><xsl:value-of select=\".\"/></xsl:non-matching-substring></xsl:analyze-string></xsl:template>", null, "[A]b[A]"),
      // XTDE0640: a key whose use expression needs the key itself.
      err ("circular key", "\n" +
        "<xsl:key name=\"k\" match=\"a\" use=\"count(key('k', 'x'))\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(key('k', '1'))\"/></xsl:template>", "<r><a/></r>", "XTDE0640"),
      // XTDE1030: durations have no total order.
      err ("sorting durations", "\n" +
        "<xsl:template match=\"/\"><xsl:perform-sort select=\"(xs:duration('P1M'), xs:duration('P30D'))\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:template>", null, "XTDE1030"),
      err ("sorting dates with strings", "\n" +
        "<xsl:template match=\"/\"><xsl:perform-sort select=\"(xs:date('2020-01-01'), 'x')\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:template>", null, "XTDE1030"),
      ok ("sorting dayTimeDurations", "\n" +
        "<xsl:template match=\"/\"><xsl:perform-sort select=\"(xs:dayTimeDuration('PT1H'), xs:dayTimeDuration('PT1M'))\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:template>", null, "PT1M PT1H"));
  }

  /**
   * XSLT 3.0 §2.3: entry points of a package must be public or final.
   */
  @Test
  public void entrySpecPackageVisibility ()
  {
    final String sPkg = "<xsl:package version=\"3.0\" name=\"urn:p\" " + SPEC_NS + "><xsl:output method=\"text\"/>\n" +
        "<xsl:mode/><xsl:mode name=\"pub\" visibility=\"public\"/><xsl:mode name=\"priv\"/>\n" +
        "<xsl:template match=\"/\">u</xsl:template>\n" +
        "<xsl:template match=\"/\" mode=\"pub\">p</xsl:template>\n" +
        "<xsl:template match=\"/\" mode=\"priv\">q</xsl:template>\n" +
        "<xsl:template name=\"xsl:initial-template\">init</xsl:template>\n" +
        "</xsl:package>";
    final XsltExecutable aExec = XsltCompiler.compile (sPkg, new XsltCompileOptions ());
    assertEquals ("public mode", "p", _runEntry (aExec, "<d/>", null, new XdmQName ("pub")));
    assertEquals ("private mode", "XTDE0045", _runEntry (aExec, "<d/>", null, new XdmQName ("priv")));
    // A private xsl:initial-template is no entry point (XTDE0040)
    assertEquals ("private initial template", "XTDE0040", _runEntry (aExec, null, INITIAL_TEMPLATE, null));
  }

  @Test
  public void specDynamicErrorsKnownBugs ()
  {
    specRunAll (
      // §17.1: invalid flags in an AVT-valued xsl:analyze-string/@flags are XTDE1145
      err ("invalid regex flags", "\n" +
        "<xsl:template match=\"/\"><xsl:analyze-string select=\"'a'\" regex=\"a\" flags=\"{'z'}\"><xsl:matching-substring>m</xsl:matching-substring></xsl:analyze-string></xsl:template>", null, "XTDE1145"),
      // §17.1: an invalid AVT-valued xsl:analyze-string/@regex is XTDE1140
      err ("invalid regex", "\n" +
        "<xsl:template match=\"/\"><xsl:analyze-string select=\"'a'\" regex=\"{'('}\"><xsl:matching-substring>m</xsl:matching-substring></xsl:analyze-string></xsl:template>", null, "XTDE1140"));
  }

}
