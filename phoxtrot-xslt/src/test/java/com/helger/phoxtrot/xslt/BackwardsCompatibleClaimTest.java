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
package com.helger.phoxtrot.xslt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;

/**
 * Test class for the backwards compatibility claim
 * ({@link XsltCompileOptions#setBackwardsCompatibleClaim(boolean)}): XSLT 1.0
 * behaviour of elements with an effective version below 2.0 (XSLT 3.0 section
 * 3.9.1) with the claim, XTDE0160 when such an instruction is evaluated
 * without it.
 *
 * @author Philip Helger
 */
public final class BackwardsCompatibleClaimTest
{
  private static final String NS = " xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:xs='http://www.w3.org/2001/XMLSchema'";
  private static final String SOURCE = "<a><b>3</b><b>1</b><b>2</b><e/></a>";

  @NonNull
  private static XsltCompileOptions _options (final boolean bClaim)
  {
    return new XsltCompileOptions ().setBackwardsCompatibleClaim (bClaim);
  }

  /**
   * @return A stylesheet of the given version with text output and a template
   *         for the document node with the given body.
   */
  @NonNull
  private static String _sheet (@NonNull final String sVersion, @NonNull final String sBody)
  {
    return "<xsl:stylesheet version='" +
           sVersion +
           "'" +
           NS +
           "><xsl:output method='text'/><xsl:template match='/'>" +
           sBody +
           "</xsl:template></xsl:stylesheet>";
  }

  @NonNull
  private static String _run (@NonNull final String sSheet, final boolean bClaim)
  {
    return XsltCompiler.compile (sSheet, _options (bClaim))
                       .transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (SOURCE)))
                       .principalSerialized ();
  }

  /** @return The body run in a version 1.0 stylesheet with the claim. */
  @NonNull
  private static String _run10 (@NonNull final String sBody)
  {
    return _run (_sheet ("1.0", sBody), true);
  }

  /** @return The error code of compiling or running the stylesheet. */
  @NonNull
  private static String _code (@NonNull final String sSheet, final boolean bClaim)
  {
    try
    {
      _run (sSheet, bClaim);
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    throw new IllegalStateException ("No error");
  }

  /** @return The error code of compiling the stylesheet. */
  @NonNull
  private static String _compileCode (@NonNull final String sSheet, final boolean bClaim)
  {
    try
    {
      XsltCompiler.compile (sSheet, _options (bClaim));
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    throw new IllegalStateException ("No compile error");
  }

  @Test
  public void testDefaults ()
  {
    assertFalse (XsltCompileOptions.DEFAULT_BACKWARDS_COMPATIBLE_CLAIM);
    assertFalse (new XsltCompileOptions ().isBackwardsCompatibleClaim ());
    assertTrue (new XsltCompileOptions ().setBackwardsCompatibleClaim (true).isBackwardsCompatibleClaim ());
  }

  @Test
  public void testSystemProperty ()
  {
    // At run time and in static expressions
    final String sSheet = _sheet ("3.0",
                                  "<xsl:value-of select=\"system-property('xsl:supports-backwards-compatibility')\"/>" +
                                         "<xsl:text use-when=\"system-property('xsl:supports-backwards-compatibility') = 'yes'\">+</xsl:text>");
    assertEquals ("no", _run (sSheet, false));
    assertEquals ("yes+", _run (sSheet, true));
  }

  @Test
  public void testNotClaimedIsDynamicError ()
  {
    // XTDE0160 only when a 1.0 instruction is evaluated (XSLT 3.0 section
    // 27.4): compiling succeeds
    final String sSheet = _sheet ("1.0", "<xsl:value-of select='1'/>");
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, _options (false));
    assertNotNull (aExec);
    try
    {
      aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (SOURCE)));
      fail ("want XTDE0160");
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTDE0160", ex.getCode ());
    }
    assertEquals ("1", _run (sSheet, true));

    // Any version below 2.0 asks for XSLT 1.0 behaviour; the outermost element counts as well
    assertEquals ("XTDE0160", _code (_sheet ("1.5", "<xsl:value-of select='1'/>"), false));
    assertEquals ("1", _run (_sheet ("0.5", "<xsl:value-of select='1'/>"), true));
    // 2.0 and 3.0 need nothing
    assertEquals ("1", _run (_sheet ("2.0", "<xsl:value-of select='1'/>"), false));

    // A 2.0 template in a 1.0 module runs, a 1.0 one that is not called does
    // no harm
    final String sMixed = "<xsl:stylesheet version='1.0'" +
                          NS +
                          "><xsl:output method='text'/>" +
                          "<xsl:template match='/' version='2.0'><xsl:value-of select='23'/></xsl:template>" +
                          "<xsl:template name='other' version='1.0'><xsl:value-of select='25'/></xsl:template>" +
                          "</xsl:stylesheet>";
    assertEquals ("23", _run (sMixed, false));

    // A literal result element with xsl:version 1.0 is an error only when it
    // is evaluated
    assertEquals ("", _run (_sheet ("3.0", "<xsl:if test='false()'><x xsl:version='1.0'/></xsl:if>"), false));
    assertEquals ("XTDE0160", _code (_sheet ("3.0", "<x xsl:version='1.0'/>"), false));
    assertEquals ("", _run (_sheet ("3.0", "<x xsl:version='1.0'/>"), true));

    // Static errors of the content stay static errors without the claim
    assertEquals ("XPST0017",
                  _compileCode (_sheet ("1.0", "<xsl:value-of select='ext:f()' xmlns:ext='urn:ext'/>"), false));
  }

  @Test
  public void testGeneralComparisons ()
  {
    // XPath 3.1 section 3.7.2: < and > compare as numbers in compatibility mode
    final String sSheet = _sheet ("3.0",
                                  "<xsl:value-of select=\"'abc' &lt; 'abd'\" version='1.0'/>,<xsl:value-of select=\"'abc' &lt; 'abd'\"/>" +
                                         ";<xsl:value-of select=\"/a/b[1] &lt; '10'\" version='1.0'/>,<xsl:value-of select=\"/a/b[1] &lt; '10'\"/>");
    assertEquals ("false,true;true,false", _run (sSheet, true));
    // A single boolean turns the other operand into its effective boolean
    // value, before atomisation: an empty document node is true
    assertEquals ("true",
                  _run10 ("<xsl:variable name='rtf'><xsl:if test='false()'/></xsl:variable><xsl:value-of select='$rtf = true()'/>"));
    assertEquals ("true", _run10 ("<xsl:value-of select=\"true() = 'false'\"/>"));
    assertEquals ("true", _run10 ("<xsl:value-of select='/a/b = true()'/>"));
    assertEquals ("false", _run10 ("<xsl:value-of select='/a/x = true()'/>"));
    assertEquals ("XPTY0004", _code (_sheet ("3.0", "<xsl:value-of select=\"true() = 'false'\"/>"), true));
    // Node-set against number
    assertEquals ("true", _run10 ("<xsl:value-of select='/a/b = 2'/>"));
    assertEquals ("true", _run10 ("<xsl:value-of select='/a/b &gt; 2.5'/>"));
  }

  @Test
  public void testArithmetic ()
  {
    // XPath 3.1 section 3.5: first item, empty is NaN, fn:number, xs:double
    assertEquals ("4", _run10 ("<xsl:value-of select='/a/b + 1'/>"));
    assertEquals ("NaN", _run10 ("<xsl:value-of select=\"'x' + 1\"/>"));
    assertEquals ("NaN", _run10 ("<xsl:value-of select='/a/x + 1'/>"));
    assertEquals ("true", _run10 ("<xsl:value-of select='(1 + 1) instance of xs:double'/>"));
    assertEquals ("false", _run (_sheet ("3.0", "<xsl:value-of select='(1 + 1) instance of xs:double'/>"), true));
    assertEquals ("XPTY0004", _code (_sheet ("3.0", "<xsl:value-of select='/a/b + 1'/>"), true));
    // Range operands take their first item (section 3.4.1)
    assertEquals ("1,2,3", _run10 ("<xsl:value-of select='(1 to 5) to (3, 4)' separator=','/>"));
  }

  @Test
  public void testFunctionConversions ()
  {
    // XPath 3.1 section 3.1.5.2
    assertEquals ("3", _run10 ("<xsl:value-of select='substring(/a/b, 1)'/>"));
    assertEquals ("1", _run10 ("<xsl:value-of select='string-length(/a/b)'/>"));
    assertEquals ("3", _run10 ("<xsl:value-of select=\"round('2.7')\"/>"));
    assertEquals ("3", _run10 ("<xsl:value-of select='number(/a/b)'/>"));
    assertEquals ("false", _run10 ("<xsl:value-of select='boolean(/a/x)'/>"));
    assertEquals ("XPTY0004", _code (_sheet ("3.0", "<xsl:value-of select='substring(/a/b, 1)'/>"), true));
    // The conversion of a declared type stays without compatibility mode
    // (XSLT 3.0 section 5.3.1)
    assertEquals ("XTTE0570", _code (_sheet ("1.0", "<xsl:variable name='v' select='/a/b' as='xs:string'/><xsl:value-of select='$v'/>"), true));
  }

  @Test
  public void testValueOf ()
  {
    // XSLT 3.0 section 11.4.3: the first item without separator
    assertEquals ("3", _run10 ("<xsl:value-of select='/a/b'/>"));
    assertEquals ("3,1,2", _run10 ("<xsl:value-of select='/a/b' separator=','/>"));
    assertEquals ("312", _run10 ("<xsl:value-of><xsl:sequence select='/a/b'/></xsl:value-of>"));
    assertEquals ("3 1 2", _run (_sheet ("3.0", "<xsl:value-of select='/a/b'/>"), true));
  }

  @Test
  public void testValueTemplates ()
  {
    // XSLT 3.0 section 5.6.1: an attribute value template takes the first item
    final String sAvt = "<xsl:variable name='v'><e x='{/a/b}'/></xsl:variable><xsl:value-of select='$v/e/@x'/>";
    assertEquals ("3", _run10 (sAvt));
    assertEquals ("3 1 2", _run (_sheet ("3.0", sAvt), true));
    // A text value template never has XPath 1.0 compatibility mode
    assertEquals ("3 1 2", _run10 ("<xsl:text expand-text='yes'>{/a/b}</xsl:text>"));
  }

  @Test
  public void testSort ()
  {
    // XSLT 3.0 section 13.1.2: the first item of a sort key value
    final String sBody = "<xsl:for-each select='/a/b'><xsl:sort select='(., 0)' data-type='number'/><xsl:value-of select='.'/></xsl:for-each>";
    assertEquals ("123", _run10 (sBody));
    assertEquals ("XTTE1020", _code (_sheet ("3.0", sBody), true));
  }

  @Test
  public void testNumber ()
  {
    // XSLT 3.0 section 12.2
    assertEquals ("1", _run10 ("<xsl:number value='1 to 3'/>"));
    assertEquals ("NaN", _run10 ("<xsl:number value='()'/>"));
    assertEquals ("(NaN)", _run10 ("<xsl:number value=\"'apples'\" format='(1)'/>"));
    assertEquals ("XTDE0980", _code (_sheet ("1.0", "<xsl:number value='-1'/>"), true));
    assertEquals ("1.2.3", _run (_sheet ("3.0", "<xsl:number value='1 to 3'/>"), true));
    assertEquals ("XTDE0980", _code (_sheet ("3.0", "<xsl:number value=\"'apples'\"/>"), true));
  }

  @Test
  public void testCallTemplateParameters ()
  {
    // XSLT 3.0 section 10.1.2: no XTSE0680 for a call with XSLT 1.0 behaviour
    final String sSheet = "<xsl:stylesheet version='VERSION'" +
                          NS +
                          "><xsl:output method='text'/>" +
                          "<xsl:template match='/'><xsl:call-template name='t'><xsl:with-param name='unwanted' select='2'/></xsl:call-template></xsl:template>" +
                          "<xsl:template name='t'>ok</xsl:template>" +
                          "</xsl:stylesheet>";
    assertEquals ("ok", _run (sSheet.replace ("VERSION", "1.0"), true));
    assertEquals ("XTSE0680", _compileCode (sSheet.replace ("VERSION", "3.0"), true));
  }

  @Test
  public void testExtensionFunctions ()
  {
    // XSLT 3.0 sections 3.9.1 and 24.1: an unknown extension function is
    // bound to the fallback error function
    assertEquals ("", _run10 ("<xsl:if test='false()'><xsl:value-of select='ext:f(1)' xmlns:ext='urn:ext'/></xsl:if>"));
    assertEquals ("XTDE1425", _code (_sheet ("1.0", "<xsl:value-of select='ext:f(1)' xmlns:ext='urn:ext'/>"), true));
    assertEquals ("false", _run10 ("<xsl:value-of select=\"function-available('ext:f', 1)\" xmlns:ext='urn:ext'/>"));
    // Not for the standard function namespace, not without XSLT 1.0 behaviour
    assertEquals ("XPST0017", _compileCode (_sheet ("1.0", "<xsl:value-of select='unknown-function(1)'/>"), true));
    assertEquals ("XPST0017",
                  _compileCode (_sheet ("3.0", "<xsl:if test='false()'><xsl:value-of select='ext:f(1)' xmlns:ext='urn:ext'/></xsl:if>"),
                                true));
  }

  @Test
  public void testKeys ()
  {
    // XSLT 3.0 section 20.2.2: a key in backwards compatible mode compares
    // the string values
    final String sSheet = "<xsl:stylesheet version='VERSION'" +
                          NS +
                          "><xsl:output method='text'/><xsl:key name='k' match='b' use='.'/>" +
                          "<xsl:template match='/'><xsl:value-of select=\"count(key('k', 1.0))\" version='3.0'/></xsl:template>" +
                          "</xsl:stylesheet>";
    assertEquals ("1", _run (sSheet.replace ("VERSION", "1.0"), true));
    assertEquals ("0", _run (sSheet.replace ("VERSION", "3.0"), true));
    // Not for a composite key
    assertEquals ("0",
                  _run (sSheet.replace ("VERSION", "1.0").replace ("use='.'", "use='.' composite='yes'"), true));
  }

  @Test
  public void testDefaultOutputMethod ()
  {
    // XSLT 3.0 section 26.1: the implicit result of a 1.0 stylesheet is not
    // xhtml by default
    final String sSheet = "<xsl:stylesheet version='VERSION'" +
                          NS +
                          "><xsl:template match='/'><html xmlns='http://www.w3.org/1999/xhtml'><head><title>t</title></head></html></xsl:template>" +
                          "</xsl:stylesheet>";
    assertFalse (_run (sSheet.replace ("VERSION", "1.0"), true).contains ("<meta"));
    assertTrue (_run (sSheet.replace ("VERSION", "3.0"), true).contains ("<meta"));
  }
}
