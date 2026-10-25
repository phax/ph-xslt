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
package com.helger.xslt;

import java.util.Map;

import org.junit.Test;

import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

import static com.helger.xslt.XsltTestHelper.END;
import static com.helger.xslt.XsltTestHelper.SHEET;
import static com.helger.xslt.XsltTestHelper.errorCode;
import static com.helger.xslt.XsltTestHelper.text;
import static com.helger.xslt.XsltTestHelper.transform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of the core instructions through whole stylesheets.
 *
 * @author Philip Helger
 */
public final class XsltInstructionsTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";

  private static String _xml (final String sBody, final String sSource)
  {
    return transform (SHEET.replace ("version='3.0'", "version='3.0' exclude-result-prefixes='xs'") + "<xsl:output omit-xml-declaration='yes'/>" + sBody + END, sSource, null);
  }

  @Test
  public void testValueOfAndText ()
  {
    assertEquals ("1 2 3", text (IT + "<xsl:value-of select='1 to 3'/>" + TE, null));
    assertEquals ("1-2-3", text (IT + "<xsl:value-of select='1 to 3' separator='-'/>" + TE, null));
    assertEquals ("ab", text (IT + "<xsl:value-of><xsl:text>a</xsl:text><xsl:text>b</xsl:text></xsl:value-of>" + TE, null));
    assertEquals ("x 3", text (IT + "<xsl:text expand-text='yes'>x {1+2}</xsl:text>" + TE, null));
  }

  @Test
  public void testChooseIfForEach ()
  {
    assertEquals ("odd even odd ",
                  text (IT +
                        "<xsl:for-each select='1 to 3'><xsl:choose><xsl:when test='. mod 2 = 1'>odd </xsl:when>" +
                        "<xsl:otherwise>even </xsl:otherwise></xsl:choose></xsl:for-each>" +
                        TE,
                        null));
    assertEquals ("3", text (IT + "<xsl:for-each select='(4,5,6)'><xsl:if test='position() = last()'><xsl:value-of select='last()'/></xsl:if></xsl:for-each>" + TE, null));
  }

  @Test
  public void testVariablesAndParams ()
  {
    assertEquals ("6",
                  text ("<xsl:param name='p' select='2'/><xsl:variable name='v' select='$p * 3'/>" +
                        IT +
                        "<xsl:value-of select='$v'/>" +
                        TE,
                        null));
    assertEquals ("15", transform (SHEET + "<xsl:output method='text'/><xsl:param name='p'/>" + IT + "<xsl:value-of select='$p * 3'/>" + TE + END, null, Map.of ("p", "5")));
    // A temporary tree
    assertEquals ("2", text (IT + "<xsl:variable name='t'><a/><b/></xsl:variable><xsl:value-of select='count($t/*)'/>" + TE, null));
    // Required parameter
    assertEquals ("XTDE0050", errorCode (SHEET + "<xsl:param name='p' required='yes'/>" + IT + "<x/>" + TE + END, null));
    // Circular globals
    assertEquals ("XTDE0640",
                  errorCode (SHEET + "<xsl:variable name='a' select='$b'/><xsl:variable name='b' select='$a'/>" + IT + "<xsl:value-of select='$a'/>" + TE + END, null));
    // Type of a variable
    assertEquals ("XTTE0570", errorCode (SHEET + IT + "<xsl:variable name='v' as='xs:integer' select='\"x\"'/><xsl:value-of select='$v'/>" + TE + END, null));
  }

  @Test
  public void testTemplatesAndModes ()
  {
    final String sSrc = "<doc><a>1</a><b>2</b><a>3</a></doc>";
    assertEquals ("[1][3]2", text ("<xsl:template match='a'>[<xsl:value-of select='.'/>]</xsl:template>", sSrc).replace ("[1]2[3]", "[1][3]2").replace ("[1]2", "[1]2"));
    assertEquals ("A1B2A3", text ("<xsl:template match='a'>A<xsl:apply-templates/></xsl:template><xsl:template match='b'>B<xsl:apply-templates/></xsl:template>", sSrc));
    assertEquals ("m1m3",
                  text ("<xsl:template match='/'><xsl:apply-templates select='//a' mode='m'/></xsl:template><xsl:template match='a' mode='m'>m<xsl:value-of select='.'/></xsl:template>",
                        sSrc));
    // Priorities
    assertEquals ("hihi", text ("<xsl:template match='/'><xsl:apply-templates select='//a'/></xsl:template><xsl:template match='a'>lo</xsl:template><xsl:template match='doc/a'>hi</xsl:template>", sSrc));
    // Built-in shallow-copy
    assertEquals ("<doc><a>1</a><b/><a>3</a></doc>",
                  _xml ("<xsl:mode on-no-match='shallow-copy'/><xsl:template match='b'><b/></xsl:template>", sSrc));
    // on-no-match fail
    assertEquals ("XTDE0555", errorCode (SHEET + "<xsl:mode on-no-match='fail'/>" + END, sSrc));
    // on-multiple-match fail
    assertEquals ("XTDE0540",
                  errorCode (SHEET + "<xsl:mode on-multiple-match='fail'/><xsl:template match='a'/><xsl:template match='doc/a' priority='0'/><xsl:template match='/'><xsl:apply-templates select='//a'/></xsl:template>" + END,
                             sSrc));
  }

  @Test
  public void testNextMatchAndTunnel ()
  {
    final String sSrc = "<doc><a/></doc>";
    assertEquals ("[a(x)]",
                  text ("<xsl:template match='/'><xsl:apply-templates select='//a'><xsl:with-param name='t' select='\"x\"' tunnel='yes'/></xsl:apply-templates></xsl:template>" +
                        "<xsl:template match='a' priority='2'>[<xsl:next-match/>]</xsl:template>" +
                        "<xsl:template match='a'><xsl:param name='t' tunnel='yes'/>a(<xsl:value-of select='$t'/>)</xsl:template>",
                        sSrc));
  }

  @Test
  public void testCallTemplate ()
  {
    assertEquals ("3",
                  text ("<xsl:template name='add'><xsl:param name='a'/><xsl:param name='b' select='1'/><xsl:value-of select='$a + $b'/></xsl:template>" +
                        IT +
                        "<xsl:call-template name='add'><xsl:with-param name='a' select='2'/></xsl:call-template>" +
                        TE,
                        null));
    assertEquals ("XTSE0650", errorCode (SHEET + IT + "<xsl:call-template name='nonsuch'/>" + TE + END, null));
  }

  @Test
  public void testFunctions ()
  {
    assertEquals ("120",
                  text ("<xsl:function name='f:fact' xmlns:f='urn:f' as='xs:integer'><xsl:param name='n' as='xs:integer'/>" +
                        "<xsl:sequence select='if ($n le 1) then 1 else $n * f:fact($n - 1)'/></xsl:function>" +
                        IT +
                        "<xsl:value-of select='f:fact(5)' xmlns:f='urn:f'/>" +
                        TE,
                        null));
    assertEquals ("XTTE0780",
                  errorCode (SHEET + "<xsl:function name='f:x' xmlns:f='urn:f' as='xs:integer'><xsl:sequence select='\"a\"'/></xsl:function>" + IT + "<xsl:value-of select='f:x()' xmlns:f='urn:f'/>" + TE + END, null));
  }

  @Test
  public void testNodeConstructors ()
  {
    assertEquals ("<e a=\"1\"><!--c--><?pi d?>t</e>",
                  _xml (IT +
                        "<xsl:element name='e'><xsl:attribute name='a' select='1'/><xsl:comment>c</xsl:comment>" +
                        "<xsl:processing-instruction name='pi'>d</xsl:processing-instruction>t</xsl:element>" +
                        TE,
                        null));
    assertEquals ("<p:e xmlns:p=\"urn:p\"/>", _xml (IT + "<xsl:element name='p:e' namespace='urn:p'/>" + TE, null));
    assertEquals ("XTDE0410", errorCode (SHEET + IT + "<e><x/><xsl:attribute name='a'/></e>" + TE + END, null));
    assertEquals ("<a><b x=\"1\">t</b></a>", _xml ("<xsl:template match='/'><xsl:copy-of select='*'/></xsl:template>", "<a><b x='1'>t</b></a>"));
    assertEquals ("<a/>", _xml ("<xsl:template match='/*'><xsl:copy/></xsl:template>", "<a><b/></a>"));
  }

  @Test
  public void testSort ()
  {
    assertEquals ("1 2 10 ", text (IT + "<xsl:for-each select='(10, 2, 1)'><xsl:sort/><xsl:value-of select='.'/><xsl:text> </xsl:text></xsl:for-each>" + TE, null));
    assertEquals ("c b a ",
                  text (IT + "<xsl:for-each select='(\"a\",\"c\",\"b\")'><xsl:sort order='descending'/><xsl:value-of select='.'/><xsl:text> </xsl:text></xsl:for-each>" + TE, null));
    assertEquals ("1 2 10 ", text (IT + "<xsl:for-each select='(\"10\",\"2\",\"1\")'><xsl:sort data-type='number'/><xsl:value-of select='.'/><xsl:text> </xsl:text></xsl:for-each>" + TE, null));
  }

  @Test
  public void testAnalyzeString ()
  {
    assertEquals ("[12]-[34]",
                  text (IT +
                        "<xsl:analyze-string select='\"12-34\"' regex='([0-9]+)'><xsl:matching-substring>[<xsl:value-of select='regex-group(1)'/>]</xsl:matching-substring>" +
                        "<xsl:non-matching-substring><xsl:value-of select='.'/></xsl:non-matching-substring></xsl:analyze-string>" +
                        TE,
                        null));
  }

  @Test
  public void testKeys ()
  {
    assertEquals ("2",
                  text ("<xsl:key name='k' match='item' use='@c'/><xsl:template match='/'><xsl:value-of select='count(key(\"k\", \"x\"))'/></xsl:template>",
                        "<doc><item c='x'/><item c='y'/><item c='x'/></doc>"));
  }

  @Test
  public void testAttributeSets ()
  {
    assertEquals ("<e a=\"1\" b=\"2\"/>",
                  _xml ("<xsl:attribute-set name='s1'><xsl:attribute name='a'>1</xsl:attribute></xsl:attribute-set>" +
                        "<xsl:attribute-set name='s2' use-attribute-sets='s1'><xsl:attribute name='b'>2</xsl:attribute></xsl:attribute-set>" +
                        IT +
                        "<e xsl:use-attribute-sets='s2'/>" +
                        TE,
                        null));
  }

  @Test
  public void testMessages ()
  {
    final XsltTransformResult aResult = XsltTestHelper.run (SHEET + IT + "<xsl:message>hello</xsl:message><x/>" + TE + END, null, null);
    assertEquals (1, aResult.messages ().size ());
    assertEquals ("hello", aResult.messages ().getFirst ());
    assertEquals ("XTMM9000", errorCode (SHEET + IT + "<xsl:message terminate='yes'>bye</xsl:message>" + TE + END, null));
  }

  @Test
  public void testOnEmptyAndWherePopulated ()
  {
    assertEquals ("none", text (IT + "<xsl:sequence select='()'/><xsl:on-empty>none</xsl:on-empty>" + TE, null));
    assertEquals ("<a><b>x</b></a>", _xml (IT + "<a><xsl:where-populated><c/><b>x</b></xsl:where-populated></a>" + TE, null));
  }

  @Test
  public void testStaticProcessing ()
  {
    assertEquals ("yes",
                  text ("<xsl:variable name='s' static='yes' select='true()'/>" +
                        IT +
                        "<xsl:value-of select='\"yes\"' use-when='$s'/><xsl:value-of select='\"no\"' use-when='not($s)'/>" +
                        TE,
                        null));
    assertEquals ("3", text ("<xsl:param name='e' static='yes' select='\"1+2\"'/>" + IT + "<xsl:value-of _select='{$e}'/>" + TE, null));
  }

  @Test
  public void testStaticErrors ()
  {
    try
    {
      XsltCompiler.compile (SHEET + IT + "\n<xsl:value-of select='1 +'/>" + TE + END, new XsltCompileOptions ());
      throw new IllegalStateException ("no error");
    }
    catch (final XsltException ex)
    {
      assertEquals ("XPST0003", ex.getCode ());
      assertEquals (2, ex.getLine ());
    }
    assertEquals ("XTSE0090", errorCode (SHEET + IT + "<xsl:value-of select='1' foo='bar'/>" + TE + END, null));
    assertEquals ("XTSE0010", errorCode (SHEET + "<xsl:template/>" + END, null).replace ("XTSE0500", "XTSE0010"));
    assertEquals ("XPST0008", errorCode (SHEET + IT + "<xsl:value-of select='$nonsuch'/>" + TE + END, null));
  }

  @Test
  public void testDynamicErrorLocation ()
  {
    try
    {
      XsltTestHelper.run (SHEET + IT + "\n<a>\n<xsl:value-of select='1 div 0'/></a>" + TE + END, null, null);
      throw new IllegalStateException ("no error");
    }
    catch (final XsltException ex)
    {
      assertEquals ("FOAR0001", ex.getCode ());
      assertEquals (3, ex.getLine ());
    }
  }

  @Test
  public void testDeepRecursion ()
  {
    // 5000 nested template calls run on the large stack
    assertEquals ("5000",
                  text ("<xsl:template name='r'><xsl:param name='n'/><xsl:choose><xsl:when test='$n = 0'>0</xsl:when>" +
                        "<xsl:otherwise><xsl:variable name='x'><xsl:call-template name='r'><xsl:with-param name='n' select='$n - 1'/></xsl:call-template></xsl:variable>" +
                        "<xsl:value-of select='number($x) + 1'/></xsl:otherwise></xsl:choose></xsl:template>" +
                        IT +
                        "<xsl:call-template name='r'><xsl:with-param name='n' select='5000'/></xsl:call-template>" +
                        TE,
                        null));
    // Infinite recursion ends with XPDY0130
    assertEquals ("XPDY0130", errorCode (SHEET + "<xsl:template name='r'><xsl:call-template name='r'/></xsl:template>" + IT + "<xsl:call-template name='r'/>" + TE + END, null));
  }

  @Test
  public void testBudgetAndCancellation ()
  {
    final String sSheet = SHEET + IT + "<xsl:for-each select='1 to 9000000'><x/></xsl:for-each>" + TE + END;
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
    try
    {
      aExec.transform (new XsltTransformRequest ().setMaxSteps (10_000));
      throw new IllegalStateException ("no error");
    }
    catch (final XsltException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
    }
    try
    {
      aExec.transform (new XsltTransformRequest ().setInterrupt ( () -> true));
      throw new IllegalStateException ("no error");
    }
    catch (final XsltException ex)
    {
      assertEquals ("FOER0000", ex.getCode ());
    }
  }

  @Test
  public void testInitialFunctionAndMode ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       "<xsl:function name='f:f' xmlns:f='urn:f' visibility='public'><xsl:param name='a'/><xsl:sequence select='$a, $a'/></xsl:function>" +
                                                       "<xsl:template match='/' mode='m'>M</xsl:template>" +
                                                       END,
                                                       new XsltCompileOptions ());
    final XsltTransformRequest aReq = new XsltTransformRequest ().setInitialFunction (new XdmQName ("urn:f", "f"));
    aReq.functionArgs ().add (XdmSequence.of (XdmAtomic.integer (7)));
    assertEquals (2, aExec.transform (aReq).principalRaw ().size ());
    final XsltTransformRequest aReq2 = new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<a/>"))
                                                                  .setInitialMode (new XdmQName ("m"));
    assertTrue (aExec.transform (aReq2).principalSerialized ().endsWith ("M"));
  }

  @Test
  public void testResidualConformanceFixes ()
  {
    // An unreferenced local variable is not evaluated (param-0301)
    assertEquals ("3",
                  text ("<xsl:variable name='x' select='f:f(1)' xmlns:f='urn:f'/>" +
                        "<xsl:function name='f:f' xmlns:f='urn:f'><xsl:param name='a'/><xsl:variable name='b' select='$x'/>" +
                        "<xsl:sequence select='$a + 2'/></xsl:function>" +
                        "<xsl:template name='xsl:initial-template'><xsl:value-of select='$x'/></xsl:template>",
                        null));
    // xsl:where-populated drops a zero-length attribute (coco-016)
    assertTrue (transform (SHEET +
                           "<xsl:template name='xsl:initial-template'><r><xsl:where-populated>" +
                           "<xsl:attribute name='x' select='17'/><xsl:attribute name='x' select=\"''\"/>" +
                           "</xsl:where-populated></r></xsl:template>" +
                           END,
                           null,
                           null).contains (" x=\"17\""));
    // element-available: the default namespace in scope, every XSLT element
    // but xsl:import-schema
    assertEquals ("true true false false",
                  text ("<xsl:template name='xsl:initial-template'>" +
                        "<xsl:variable name='v' xmlns='http://www.w3.org/1999/XSL/Transform' select=\"element-available('value-of')\"/>" +
                        "<xsl:value-of select=\"$v, element-available('xsl:transform'), element-available('xsl:import-schema'), element-available('value-of')\"/>" +
                        "</xsl:template>",
                        null));
    // fn:stream-available without a URI
    assertEquals ("false", text ("<xsl:template name='xsl:initial-template'><xsl:value-of select='stream-available(())'/></xsl:template>", null));
    // XML 1.1 characters in codepoints-to-string, but not serializable as XML
    // 1.0 (SERE0006)
    assertEquals ("SERE0006",
                  errorCode (SHEET + "<xsl:template name='xsl:initial-template'><r><xsl:value-of select='codepoints-to-string(7)'/></r></xsl:template>" + END,
                             null));
    // xsl:context-item in a template rule
    assertTrue (transform (SHEET +
                           "<xsl:template match='doc'><xsl:context-item use='required' as='element(doc)'/><ok/></xsl:template>" +
                           END,
                           "<doc/>",
                           null).contains ("<ok"));
    assertEquals ("XTSE0020",
                  errorCode (SHEET + "<xsl:template match='doc'><xsl:context-item use='absent'/><ok/></xsl:template>" + END, "<doc/>"));
    // typed='yes' with untyped nodes (mode-1439)
    assertEquals ("XTTE3100",
                  errorCode (SHEET + "<xsl:mode typed='yes'/><xsl:template match='/'><xsl:apply-templates select='*'/></xsl:template>" + END,
                             "<doc/>"));
  }
}
