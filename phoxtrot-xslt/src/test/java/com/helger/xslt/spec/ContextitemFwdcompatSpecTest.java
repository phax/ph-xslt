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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xslt/contextitem_fwdcompat_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ContextitemFwdcompatSpecTest
{
  @NonNull
  private static String _fwd (@NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"4.0\" " + SPEC_NS + "><xsl:output method=\"text\"/>" + sBody + "</xsl:stylesheet>";
  }

  @NonNull
  private static String _pkg (@NonNull final String sAttrs)
  {
    return "<xsl:package version=\"3.0\" name=\"urn:p\" " + sAttrs + " " + SPEC_NS + "><xsl:output method=\"text\"/><xsl:mode/><xsl:template match=\"/\">ok</xsl:template></xsl:package>";
  }

  /**
   * XSLT 3.0 §6.? xsl:context-item: static constraints and dynamic checks.
   */
  @Test
  public void compileSpecContextItem ()
  {
    specRunAll (
      ok ("as satisfied", "\n" +
        "<xsl:template match=\"/\"><xsl:apply-templates select=\"r\"/></xsl:template>\n" +
        "<xsl:template match=\"r\"><xsl:context-item as=\"element()\" use=\"required\"/><xsl:value-of select=\"name()\"/></xsl:template>", "<r/>", "r"),
      ok ("optional and absent context item", "\n" +
        "<xsl:function name=\"f:t\"><xsl:call-template name=\"t\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:t()\"/></xsl:template>\n" +
        "<xsl:template name=\"t\"><xsl:context-item use=\"optional\"/>ok</xsl:template>", null, "ok"),
      // XTTE3090: required context item absent.
      err ("required but absent", "\n" +
        "<xsl:function name=\"f:t\"><xsl:call-template name=\"t\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:t()\"/></xsl:template>\n" +
        "<xsl:template name=\"t\"><xsl:context-item use=\"required\"/>x</xsl:template>", null, "XTTE3090"),
      // XTTE0590: context item does not match @as.
      err ("type mismatch", "\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"/></xsl:template>\n" +
        "<xsl:template name=\"t\"><xsl:context-item as=\"element()\"/>x</xsl:template>", null, "XTTE0590"),
      ok ("atomic context item against as", "\n" +
        "<xsl:template match=\"/\"><xsl:for-each select=\"5\"><xsl:call-template name=\"t\"/></xsl:for-each></xsl:template>\n" +
        "<xsl:template name=\"t\"><xsl:context-item as=\"xs:integer\"/><xsl:value-of select=\". \"/></xsl:template>", null, "5"),
      // use="absent": the body has no focus, so "." is XPDY0002.
      err ("absent focus", "\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"/></xsl:template>\n" +
        "<xsl:template name=\"t\"><xsl:context-item use=\"absent\"/><xsl:value-of select=\"name(.)\"/></xsl:template>", null, "XPDY0002"),
      ok ("absent focus body that ignores it", "\n" +
        "<xsl:template match=\"/\"><xsl:call-template name=\"t\"/></xsl:template>\n" +
        "<xsl:template name=\"t\"><xsl:context-item use=\"absent\"/>fine</xsl:template>", null, "fine"),
      err ("two declarations", "\n" +
        "<xsl:template name=\"t\"><xsl:context-item/><xsl:context-item/></xsl:template>", null, "XTSE0010"),
      err ("not the first child", "\n" +
        "<xsl:template name=\"t\"><xsl:param name=\"p\"/><xsl:context-item/></xsl:template>", null, "XTSE0010"),
      err ("text before it", "\n" +
        "<xsl:template name=\"t\">text<xsl:context-item/></xsl:template>", null, "XTSE0010"),
      err ("unknown attribute", "\n" +
        "<xsl:template name=\"t\"><xsl:context-item select=\".\"/></xsl:template>", null, "XTSE0090"),
      err ("bad use", "\n" +
        "<xsl:template name=\"t\"><xsl:context-item use=\"sometimes\"/></xsl:template>", null, "XTSE0020"),
      err ("as with use=absent", "\n" +
        "<xsl:template name=\"t\"><xsl:context-item use=\"absent\" as=\"node()\"/></xsl:template>", null, "XTSE3088"));
  }

  /**
   * XSLT 3.0 §3.10 forwards-compatible processing and §24.2 extension
   * instructions with xsl:fallback.
   */
  @Test
  public void compileSpecForwardsCompatible ()
  {
    specRunAll (
      ok ("unknown instruction with fallback", _fwd ("\n" +
        "<xsl:template match=\"/\"><xsl:future-thing><xsl:fallback>fb1</xsl:fallback><xsl:fallback>fb2</xsl:fallback></xsl:future-thing></xsl:template>"), null, "fb1fb2"),
      // go-xslt expects "ok" and XTDE1425 here, but §3.10 says: "If the element
      // has no xsl:fallback children, then a static error is reported in the
      // same way as if forwards compatible behavior were not enabled."
      err ("unknown instruction not evaluated", _fwd ("\n" +
        "<xsl:template match=\"/\"><xsl:if test=\"false()\"><xsl:future-thing/></xsl:if>ok</xsl:template>"), null, "XTSE0010"),
      err ("unknown instruction evaluated without fallback", _fwd ("\n" +
        "<xsl:template match=\"/\"><xsl:future-thing/></xsl:template>"), null, "XTSE0010"),
      ok ("unknown top-level declaration ignored", _fwd ("\n" +
        "<xsl:future-declaration/><xsl:template match=\"/\">ok</xsl:template>"), null, "ok"),
      ok ("unknown attribute ignored", _fwd ("\n" +
        "<xsl:template match=\"/\" future-attr=\"1\">ok</xsl:template>"), null, "ok"),
      // Without forwards-compatible mode these are static errors.
      err ("unknown declaration in 3.0 mode", "\n" +
        "<xsl:future-declaration/><xsl:template match=\"/\">ok</xsl:template>", null, "XTSE0010"),
      err ("instruction at top level", "\n" +
        "<xsl:value-of select=\"1\"/>", null, "XTSE0010"),
      err ("expose outside a package", "\n" +
        "<xsl:expose component=\"template\" names=\"*\" visibility=\"public\"/>", null, "XTSE0010"),
      ok ("local version attribute opens forwards-compatible scope", "\n" +
        "<xsl:template match=\"/\"><xsl:future-thing version=\"5.0\"><xsl:fallback>fb</xsl:fallback></xsl:future-thing></xsl:template>", null, "fb"),
      // §24.2: extension instructions.
      ok ("extension instruction falls back", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:ext=\"urn:ext\" extension-element-prefixes=\"ext\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\"><ext:do><xsl:fallback>fb</xsl:fallback></ext:do></xsl:template></xsl:stylesheet>", null, "fb"),
      err ("extension instruction without fallback", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:ext=\"urn:ext\" extension-element-prefixes=\"ext\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\"><ext:do/></xsl:template></xsl:stylesheet>", null, "XTDE1450"),
      ok ("extension instruction never evaluated", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:ext=\"urn:ext\" extension-element-prefixes=\"ext\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\"><xsl:if test=\"false()\"><ext:do/></xsl:if>ok</xsl:template></xsl:stylesheet>", null, "ok"),
      ok ("element-available of an unknown extension", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " xmlns:ext=\"urn:ext\" extension-element-prefixes=\"ext\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"element-available('ext:do')\"/></xsl:template></xsl:stylesheet>", null, "false"));
  }

  /**
   * XSLT 3.0 §3.5.1: xsl:package attributes package-version and declared-modes.
   */
  @Test
  public void compileSpecPackageAttributes ()
  {
    specRunAll (
      ok ("valid package-version", _pkg ("package-version=\"1.0.2-alpha\""), null, "ok"),
      ok ("numeric package-version", _pkg ("package-version=\"2\""), null, "ok"),
      ok ("declared-modes no", _pkg ("declared-modes=\"no\""), null, "ok"),
      err ("package-version with a non-numeric part", _pkg ("package-version=\"1.x\""), null, "XTSE0020"),
      err ("package-version with an empty part", _pkg ("package-version=\"1..2\""), null, "XTSE0020"),
      err ("package-version with a bad suffix", _pkg ("package-version=\"1.0-9a\""), null, "XTSE0020"),
      err ("empty package-version", _pkg ("package-version=\"\""), null, "XTSE0020"),
      err ("declared-modes not a boolean", _pkg ("declared-modes=\"maybe\""), null, "XTSE0020"));
  }

}
