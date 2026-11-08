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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt's <code>internal/xslt/validate_static_errors_test.go</code>:
 * each case is a stylesheet the XSLT 3.0 REC declares to be in error, with the
 * code the REC assigns. The cases that need a schema-aware or a backwards
 * compatible processor (go-xslt's Features) are not ported, as ph-xslt is
 * neither.
 *
 * @author Philip Helger
 */
public final class ValidateStaticErrorsSpecTest
{
  /**
   * @param sBody
   *        Top-level content.
   * @return A 3.0 stylesheet with the usual prefixes bound.
   */
  @NonNull
  private static String _svDSheet (@NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
           "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
           sBody +
           "</xsl:stylesheet>";
  }

  @NonNull
  private static String _tmpl (@NonNull final String sBody)
  {
    return _svDSheet ("<xsl:template match=\"/\">" + sBody + "</xsl:template>");
  }

  @NonNull
  private static XsltExecutable _compile (@NonNull final String sSheet)
  {
    return XsltCompiler.compile (sSheet, new XsltCompileOptions ());
  }

  private static void _checkAll (@NonNull final String [] [] aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
      try
      {
        _compile (aCase[1]);
        aErrors.add (aCase[0] + ": no error, want " + aCase[2]);
      }
      catch (final XPathException ex)
      {
        if (!aCase[2].equals (ex.getCode ()))
          aErrors.add (aCase[0] + ": got " + ex.getCode () + " (" + ex.getMessage () + "), want " + aCase[2]);
      }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  @Test
  public void staticErrorsOfStylesheetElements ()
  {
    _checkAll (new String [] [] {
                                  // [ERR XTSE0809]: #default excluded with no default
                                  // namespace.
                                  { "lre exclude #default without default ns", _tmpl ("<out xsl:exclude-result-prefixes=\"#default\"/>"), "XTSE0809" },
                                  // [ERR XTSE0808]: an excluded prefix must be bound.
                                  { "lre exclude unbound prefix", _tmpl ("<out xsl:exclude-result-prefixes=\"nope\"/>"), "XTSE0808" },
                                  { "stylesheet exclude unbound prefix",
                                    "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" exclude-result-prefixes=\"nope\"><xsl:template match=\"/\"/></xsl:stylesheet>",
                                    "XTSE0808" },
                                  // [ERR XTSE1430]: extension-element-prefixes on a
                                  // literal result element.
                                  { "lre extension prefix unbound", _tmpl ("<out xsl:extension-element-prefixes=\"nope\"/>"), "XTSE1430" },
                                  { "lre extension #default without default ns",
                                    _tmpl ("<out xsl:extension-element-prefixes=\"#default\"/>"),
                                    "XTSE1430" },
                                  // [ERR XTSE0805]: unknown XSLT-namespace attribute on
                                  // an LRE.
                                  { "lre unknown xsl attribute", _tmpl ("<out xsl:bogus=\"1\"/>"), "XTSE0805" },
                                  // [ERR XTSE0110]: [xsl:]version must be an xs:decimal.
                                  { "lre version not decimal", _tmpl ("<out xsl:version=\"three\"/>"), "XTSE0110" },
                                  { "lre version with exponent", _tmpl ("<out xsl:version=\"3e0\"/>"), "XTSE0110" },
                                  { "stylesheet version not decimal",
                                    "<xsl:stylesheet version=\"x.y\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\"/></xsl:stylesheet>",
                                    "XTSE0110" },
                                  { "stylesheet version sign only",
                                    "<xsl:stylesheet version=\"+\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\"/></xsl:stylesheet>",
                                    "XTSE0110" },
                                  // [ERR XTSE0020]: values outside an attribute's
                                  // permitted values.
                                  { "lre inherit-namespaces not boolean", _tmpl ("<out xsl:inherit-namespaces=\"maybe\"/>"), "XTSE0020" },
                                  { "lre expand-text not boolean", _tmpl ("<out xsl:expand-text=\"maybe\"/>"), "XTSE0020" },
                                  { "stylesheet expand-text not boolean",
                                    "<xsl:stylesheet version=\"3.0\" expand-text=\"TRUE\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\"/></xsl:stylesheet>",
                                    "XTSE0020" },
                                  { "call-template name not a QName", _tmpl ("<xsl:call-template name=\"1x\"/>"), "XTSE0020" },
                                  { "copy-of copy-namespaces not boolean",
                                    _tmpl ("<xsl:copy-of select=\".\" copy-namespaces=\"perhaps\"/>"),
                                    "XTSE0020" },
                                  { "message terminate fixed value", _tmpl ("<xsl:message terminate=\"NO\"/>"), "XTSE0020" },
                                  { "decimal-format zero-digit two chars", _svDSheet ("<xsl:decimal-format zero-digit=\"00\"/>"), "XTSE0020" },
                                  { "global-context-item use out of vocabulary",
                                    _svDSheet ("<xsl:global-context-item use=\"sometimes\"/>"),
                                    "XTSE0020" },
                                  { "output standalone out of vocabulary", _svDSheet ("<xsl:output standalone=\"perhaps\"/>"), "XTSE0020" },
                                  { "mode on-no-match out of vocabulary", _svDSheet ("<xsl:mode on-no-match=\"explode\"/>"), "XTSE0020" },
                                  { "mode on-multiple-match out of vocabulary", _svDSheet ("<xsl:mode on-multiple-match=\"first\"/>"), "XTSE0020" },
                                  { "mode typed out of vocabulary", _svDSheet ("<xsl:mode typed=\"sometimes\"/>"), "XTSE0020" },
                                  { "mode visibility out of vocabulary", _svDSheet ("<xsl:mode name=\"m\" visibility=\"secret\"/>"), "XTSE0020" },
                                  { "apply-templates mode not a QName", _tmpl ("<xsl:apply-templates mode=\"1bad\"/>"), "XTSE0020" },
                                  { "function new-each-time is not boolean",
                                    _svDSheet ("<xsl:function name=\"f:f\" xmlns:f=\"urn:f\" new-each-time=\"Yes\"><xsl:sequence select=\"1\"/></xsl:function>"),
                                    "XTSE0020" },
                                  // [ERR XTSE3089]: as with use="absent".
                                  { "global-context-item absent with as",
                                    _svDSheet ("<xsl:global-context-item use=\"absent\" as=\"item()\"/>"),
                                    "XTSE3089" },
                                  // [ERR XTSE0120]: text at the top level.
                                  { "top-level text", _svDSheet ("hello<xsl:template match=\"/\"/>"), "XTSE0120" },
                                  // [ERR XTSE3120] / [ERR XTSE3130]: xsl:break /
                                  // xsl:next-iteration.
                                  { "break not last in iterate",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:break/><xsl:sequence select=\".\"/></xsl:iterate>"),
                                    "XTSE3120" },
                                  { "break inside a literal result element",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><x><xsl:break/></x></xsl:iterate>"),
                                    "XTSE3120" },
                                  { "break inside for-each inside iterate",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:for-each select=\"1\"><xsl:break/></xsl:for-each></xsl:iterate>"),
                                    "XTSE3120" },
                                  { "next-iteration param not declared",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:param name=\"p\" select=\"0\"/><xsl:next-iteration><xsl:with-param name=\"q\" select=\"1\"/></xsl:next-iteration></xsl:iterate>"),
                                    "XTSE3130" },
                                  // [ERR XTSE0010]: element in a context where it is not
                                  // permitted, a required attribute omitted, or content
                                  // not allowed.
                                  { "when outside choose", _tmpl ("<xsl:if test=\"true()\"><xsl:when test=\"true()\"/></xsl:if>"), "XTSE0010" },
                                  { "when inside a literal result element", _tmpl ("<x><xsl:when test=\"true()\"/></x>"), "XTSE0010" },
                                  { "call-template without name", _tmpl ("<xsl:call-template/>"), "XTSE0010" },
                                  { "accumulator without rules", _svDSheet ("<xsl:accumulator name=\"a\" initial-value=\"0\"/>"), "XTSE0010" },
                                  { "namespace-alias with content",
                                    _svDSheet ("<xsl:namespace-alias stylesheet-prefix=\"#default\" result-prefix=\"#default\"><x/></xsl:namespace-alias>"),
                                    "XTSE0010" },
                                  { "choose with a literal result element child", _tmpl ("<xsl:choose><x/><xsl:when test=\"1\"/></xsl:choose>"), "XTSE0010" },
                                  { "choose with text", _tmpl ("<xsl:choose>text<xsl:when test=\"1\"/></xsl:choose>"), "XTSE0010" },
                                  { "otherwise before when", _tmpl ("<xsl:choose><xsl:otherwise/><xsl:when test=\"1\"/></xsl:choose>"), "XTSE0010" },
                                  { "two otherwise",
                                    _tmpl ("<xsl:choose><xsl:when test=\"1\"/><xsl:otherwise/><xsl:otherwise/></xsl:choose>"),
                                    "XTSE0010" },
                                  { "choose without when", _tmpl ("<xsl:choose><xsl:otherwise/></xsl:choose>"), "XTSE0010" },
                                  { "param after body", _svDSheet ("<xsl:template name=\"t\"><x/><xsl:param name=\"p\"/></xsl:template>"), "XTSE0010" },
                                  { "sort after body", _tmpl ("<xsl:for-each select=\"*\"><x/><xsl:sort/></xsl:for-each>"), "XTSE0010" },
                                  { "non-matching before matching",
                                    _tmpl ("<xsl:analyze-string select=\"'a'\" regex=\"a\"><xsl:non-matching-substring/><xsl:matching-substring/></xsl:analyze-string>"),
                                    "XTSE0010" },
                                  { "fallback before non-matching",
                                    _tmpl ("<xsl:analyze-string select=\"'a'\" regex=\"a\"><xsl:fallback/><xsl:non-matching-substring/></xsl:analyze-string>"),
                                    "XTSE0010" },
                                  { "element child of an empty element", _tmpl ("<xsl:copy-of select=\".\"><x/></xsl:copy-of>"), "XTSE0010" },
                                  // [ERR XTSE0260]: whitespace kept by
                                  // xml:space="preserve" in an empty element.
                                  { "preserved whitespace in an empty element",
                                    _svDSheet ("<xsl:strip-space elements=\"*\" xml:space=\"preserve\"> </xsl:strip-space>"),
                                    "XTSE0260" },
                                  // [ERR XTSE1130]
                                  { "analyze-string with neither substring branch",
                                    _tmpl ("<xsl:analyze-string select=\"'a'\" regex=\"a\"><xsl:fallback/></xsl:analyze-string>"),
                                    "XTSE1130" },
                                  // [ERR XTSE0090]: attributes not defined for an XSLT
                                  // element.
                                  { "unknown attribute on value-of", _tmpl ("<xsl:value-of select=\"1\" colour=\"red\"/>"), "XTSE0090" },
                                  { "xsl-namespace attribute on value-of", _tmpl ("<xsl:value-of select=\"1\" xsl:select=\"2\"/>"), "XTSE0090" },
                                  { "static on a local variable", _tmpl ("<xsl:variable name=\"v\" static=\"yes\" select=\"1\"/>"), "XTSE0090" },
                                  // [ERR XTSE0500]
                                  { "name-only template with mode", _svDSheet ("<xsl:template name=\"t\" mode=\"m\"/>"), "XTSE0500" },
                                  { "name-only template with priority", _svDSheet ("<xsl:template name=\"t\" priority=\"1\"/>"), "XTSE0500" },
                                  { "match-only template with visibility", _svDSheet ("<xsl:template match=\"/\" visibility=\"public\"/>"), "XTSE0500" },
                                  // [ERR XTSE0550]
                                  { "mode list duplicate token", _svDSheet ("<xsl:template match=\"/\" mode=\"a a\"/>"), "XTSE0550" },
                                  { "mode list #all with others", _svDSheet ("<xsl:template match=\"/\" mode=\"#all a\"/>"), "XTSE0550" },
                                  { "mode list empty", _svDSheet ("<xsl:template match=\"/\" mode=\" \"/>"), "XTSE0550" },
                                  { "mode list invalid token", _svDSheet ("<xsl:template match=\"/\" mode=\"1a\"/>"), "XTSE0550" },
                                  // [ERR XTSE0280]: unbound prefix in a QName-valued
                                  // attribute.
                                  { "mode list unbound prefix", _svDSheet ("<xsl:template match=\"/\" mode=\"p:m\"/>"), "XTSE0280" },
                                  { "call-template unbound prefix", _tmpl ("<xsl:call-template name=\"p:t\"/>"), "XTSE0280" },
                                  { "apply-templates mode unbound prefix", _tmpl ("<xsl:apply-templates mode=\"p:m\"/>"), "XTSE0280" },
                                  // [ERR XTSE0080]: reserved namespace in a declared
                                  // name.
                                  { "template in the XSLT namespace", _svDSheet ("<xsl:template name=\"xsl:foo\"/>"), "XTSE0080" },
                                  { "variable in the xml namespace", _svDSheet ("<xsl:variable name=\"xml:v\" select=\"1\"/>"), "XTSE0080" },
                                  // Select attribute and content are mutually
                                  // exclusive: [ERR XTSE0870], [ERR XTSE0840], [ERR
                                  // XTSE0940].
                                  { "value-of select and content", _tmpl ("<xsl:value-of select=\"1\">x</xsl:value-of>"), "XTSE0870" },
                                  { "attribute select and content",
                                    _tmpl ("<x><xsl:attribute name=\"a\" select=\"1\"><y/></xsl:attribute></x>"),
                                    "XTSE0840" },
                                  { "comment select and content", _tmpl ("<xsl:comment select=\"1\">x</xsl:comment>"), "XTSE0940" },
                                  // [ERR XTSE0975]
                                  { "number value with count", _tmpl ("<xsl:number value=\"1\" count=\"x\"/>"), "XTSE0975" },
                                  { "number value with level", _tmpl ("<xsl:number value=\"1\" level=\"any\"/>"), "XTSE0975" },
                                  // [ERR XTSE1570]
                                  { "output method unknown", _svDSheet ("<xsl:output method=\"pdf\"/>"), "XTSE1570" },
                                  { "output method not a QName", _svDSheet ("<xsl:output method=\"1x\"/>"), "XTSE1570" },
                                  // [ERR XTSE0670] / [ERR XTSE0580]
                                  { "duplicate with-param",
                                    _tmpl ("<xsl:call-template name=\"t\"><xsl:with-param name=\"a\" select=\"1\"/><xsl:with-param name=\"a\" select=\"2\"/></xsl:call-template>"),
                                    "XTSE0670" },
                                  { "duplicate param",
                                    _svDSheet ("<xsl:template name=\"t\"><xsl:param name=\"a\"/><xsl:param name=\"a\"/></xsl:template>"),
                                    "XTSE0580" },
                                  // [ERR XTSE0125]
                                  { "unknown default collation",
                                    "<xsl:stylesheet version=\"3.0\" default-collation=\"urn:no-such-collation\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\"/></xsl:stylesheet>",
                                    "XTSE0125" } });
  }

  /**
   * The legal shapes next to the errors above, so a check that over-reaches is
   * caught too.
   */
  @Test
  public void staticChecksAcceptValidForms ()
  {
    final String [] [] aCases = {
                                  // #all and a bound prefix are both legal exclusions
                                  // (§11.1.3).
                                  { "exclude #all and bound prefix", _tmpl ("<out xmlns:p=\"urn:p\" xsl:exclude-result-prefixes=\"#all p\"/>") },
                                  { "exclude #default with default ns", _tmpl ("<out xmlns=\"urn:d\" xsl:exclude-result-prefixes=\"#default\"/>") },
                                  // §8.3: a tail position may be inside xsl:if, xsl:choose
                                  // branches.
                                  { "break inside last xsl:if",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:if test=\". = 2\"><xsl:break/></xsl:if></xsl:iterate>") },
                                  { "break in non-final when",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:choose><xsl:when test=\". = 2\"><xsl:break/></xsl:when><xsl:otherwise><xsl:sequence select=\".\"/></xsl:otherwise></xsl:choose></xsl:iterate>") },
                                  { "next-iteration with declared param",
                                    _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:param name=\"p\" select=\"0\"/><xsl:next-iteration><xsl:with-param name=\"p\" select=\"1\"/></xsl:next-iteration></xsl:iterate>") },
                                  { "break followed by a comment", _tmpl ("<xsl:iterate select=\"1 to 3\"><xsl:break/><!-- done --></xsl:iterate>") },
                                  // XTSE0080's own exception: xsl:initial-template.
                                  { "xsl:initial-template", _svDSheet ("<xsl:template name=\"xsl:initial-template\"/>") },
                                  // The mode list's reserved tokens and a bound EQName.
                                  { "mode tokens", _svDSheet ("<xsl:template match=\"/\" mode=\"#default #unnamed Q{urn:m}m\"/>") },
                                  // An EQName is not a prefixed QName (no XTSE0280).
                                  { "call-template EQName",
                                    _svDSheet ("<xsl:template name=\"Q{urn:x}t\"/><xsl:template match=\"/\"><xsl:call-template name=\" Q{urn:x}t \"/></xsl:template>") },
                                  // The context item declaration precedes the parameters
                                  // (§10.1.1).
                                  { "context-item before params",
                                    _svDSheet ("<xsl:template name=\"t\"><xsl:context-item use=\"absent\"/><xsl:param name=\"p\"/><x/></xsl:template>") },
                                  { "sort first in perform-sort", _tmpl ("<xsl:perform-sort select=\"1\"><xsl:sort/></xsl:perform-sort>") },
                                  { "version is a decimal", _tmpl ("<out xsl:version=\"3.0\"/>") },
                                  { "lre boolean attributes", _tmpl ("<out xsl:inherit-namespaces=\"no\" xsl:expand-text=\"1\"/>") } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
      try
      {
        _compile (aCase[1]);
      }
      catch (final XPathException ex)
      {
        aErrors.add (aCase[0] + ": unexpected error " + ex.getCode () + " " + ex.getMessage ());
      }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * §3.9: with an effective version above 3.0, an unknown XSLT element is
   * ignored with its content, an unknown top-level XSLT element is skipped, and
   * an attribute a known element does not define is ignored.
   */
  @Test
  public void forwardsCompatibleProcessingIgnoresUnknown ()
  {
    final XsltExecutable aExec = _compile ("""
        <xsl:stylesheet version="4.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
          <xsl:output method="text"/>
          <xsl:future-declaration><xsl:call-template/></xsl:future-declaration>
          <xsl:template match="/">
            <xsl:value-of select="'ok'" future-attribute="x"/>
            <xsl:future-instruction><xsl:fallback/><xsl:when test="1"/></xsl:future-instruction>
          </xsl:template>
        </xsl:stylesheet>""");
    assertEquals ("ok",
                  aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<a/>"))).principalSerialized ());
  }

  /**
   * [ERR XTSE1660]: a non-schema-aware processor rejects [xsl:]type anywhere,
   * and [xsl:]validation / [xsl:]default-validation with a value other than
   * strip, preserve or lax. (go-xslt's schema-aware half is not ported.)
   */
  @Test
  public void schemaAttributesWithoutSchemaAwareness ()
  {
    _checkAll (new String [] [] { { "lre xsl:type", _tmpl ("<out xsl:type=\"xs:string\"/>"), "XTSE1660" },
                                  { "lre xsl:validation strict", _tmpl ("<out xsl:validation=\"strict\"/>"), "XTSE1660" },
                                  { "lre xsl:default-validation strict", _tmpl ("<out xsl:default-validation=\"strict\"/>"), "XTSE1660" },
                                  { "copy-of validation strict", _tmpl ("<xsl:copy-of select=\".\" validation=\"strict\"/>"), "XTSE1660" },
                                  { "stylesheet default-validation strict",
                                    "<xsl:stylesheet version=\"3.0\" default-validation=\"strict\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\"/></xsl:stylesheet>",
                                    "XTSE1660" } });
    // lax and preserve are explicitly allowed without schema awareness.
    for (final String sOk : new String [] { _tmpl ("<out xsl:validation=\"lax\"/>"),
                                            _tmpl ("<out xsl:validation=\"preserve\" xsl:default-validation=\"strip\"/>"),
                                            _tmpl ("<xsl:copy-of select=\".\" validation=\"lax\"/>") })
      try
      {
        _compile (sOk);
      }
      catch (final XPathException ex)
      {
        fail ("unexpected error for " + sOk + ": " + ex.getCode () + " " + ex.getMessage ());
      }
  }

  /**
   * [ERR XTDE0160]: an element with an effective version below 2.0, evaluated
   * by a processor not configured for backwards-compatible behaviour. (The
   * backwards compatible half of go-xslt's test is not ported.)
   */
  @Test
  public void backwardsCompatibleVersionNotClaimed ()
  {
    try
    {
      _compile (_tmpl ("<out xsl:version=\"1.0\"/>")).transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<a/>")));
      fail ("want XTDE0160");
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTDE0160", ex.getCode ());
    }
  }
}
