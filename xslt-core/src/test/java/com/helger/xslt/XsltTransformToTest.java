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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.runtime.ResultDrain;

/**
 * Writing the principal result to a writer or stream
 * ({@link XsltExecutable#transform(XsltTransformRequest, java.io.Writer)}):
 * the characters are those of the string result, written while the result is
 * built where the output definition allows it and after the run otherwise.
 *
 * @author Philip Helger
 */
public final class XsltTransformToTest
{
  private static final String SOURCE = "<data><rec id='1'><v>a &amp; b</v></rec><rec id='2'><v>&lt;c&gt;</v></rec>" +
                                       "<!--note--><?pi x?><rec id='3'/></data>";

  @NonNull
  private static XsltTransformRequest _request (@Nullable final String sSource)
  {
    final XsltTransformRequest ret = new XsltTransformRequest ();
    if (sSource != null)
      ret.setGlobalContextItem (XsltTestHelper.parse (sSource));
    return ret;
  }

  /**
   * Transform to a string and to a writer and a stream, check that all agree
   * and whether the writer run was drained.
   *
   * @return The output.
   */
  @NonNull
  private static String _check (@NonNull final String sSheet, @Nullable final String sSource, final boolean bDrained)
  {
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
    final String sExpected = aExec.transform (_request (sSource)).principalSerialized ();

    final long nBefore = ResultDrain.getDrainedRunCount ();
    final StringWriter aSW = new StringWriter ();
    final XsltTransformResult aResult = aExec.transform (_request (sSource), aSW);
    assertEquals (sSheet, bDrained ? 1 : 0, ResultDrain.getDrainedRunCount () - nBefore);
    assertEquals (sSheet, sExpected, aSW.toString ());
    assertEquals ("", aResult.principalSerialized ());
    assertNull (aResult.principalTree ());
    assertTrue (aResult.principalRaw ().isEmpty ());

    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    aExec.transform (_request (sSource), aBAOS);
    final Charset aCharset = sSheet.contains ("encoding='ISO-8859-1'") ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
    assertArrayEquals (sSheet, sExpected.getBytes (aCharset), aBAOS.toByteArray ());
    return sExpected;
  }

  @NonNull
  private static String _errorCode (@NonNull final String sSheet, @Nullable final String sSource, @NonNull final StringWriter aSW)
  {
    try
    {
      XsltCompiler.compile (sSheet, new XsltCompileOptions ()).transform (_request (sSource), aSW);
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    fail ("No error");
    return null;
  }

  @Test
  public void testXmlDrained ()
  {
    final String s = _check (XsltTestHelper.SHEET +
                             "<xsl:output method='xml'/>" +
                             "<xsl:template match='/'>" +
                             "<out xmlns:p='urn:p' a='1'>" +
                             "<xsl:attribute name='b'>2</xsl:attribute>" +
                             "<xsl:for-each select='data/rec'>" +
                             "<p:r id='{@id}'><xsl:value-of select='v'/>-<xsl:sequence select='1, 2'/></p:r>" +
                             "<xsl:element name='q:e' namespace='urn:q'><xsl:attribute name='q:x' namespace='urn:q'>y</xsl:attribute></xsl:element>" +
                             "<xsl:element name='e2' namespace='urn:p'/>" +
                             "<xsl:comment>c</xsl:comment>" +
                             "</xsl:for-each>" +
                             "<xsl:copy-of select='data/node()'/>" +
                             "<empty/>text<xsl:sequence select='3, 4'/>" +
                             "</out>" +
                             "<xsl:processing-instruction name='after'>z</xsl:processing-instruction>" +
                             "</xsl:template>" +
                             XsltTestHelper.END,
                             SOURCE,
                             true);
    assertTrue (s, s.contains (" xmlns:p=\"urn:p\" a=\"1\" b=\"2\"><p:r id=\"1\">a &amp; b-1 2</p:r>"));
    assertTrue (s, s.endsWith ("<empty/>text3 4</out><?after z?>"));
  }

  @Test
  public void testIdentityDrained ()
  {
    // xsl:copy of the document node and of elements
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
            "<xsl:template match='node() | @*'><xsl:copy><xsl:apply-templates select='node() | @*'/></xsl:copy></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            true);
    // The built-in shallow copy
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xml'/>" +
            "<xsl:mode on-no-match='shallow-copy'/>" +
            "<xsl:template match='v'><w><xsl:apply-templates/></w></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            true);
  }

  @Test
  public void testNamespacesDrained ()
  {
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xml'/>" +
            "<xsl:template name='xsl:initial-template'>" +
            "<a xmlns='urn:d' xmlns:p='urn:p'>" +
            "<b><p:c xmlns:p='urn:other'><xsl:attribute name='p:x' namespace='urn:p'>1</xsl:attribute>t</p:c></b>" +
            "<xsl:element name='p:d' namespace='urn:p2'><xsl:namespace name='p'>urn:p3</xsl:namespace>u</xsl:element>" +
            "<e xmlns=''><f>v</f></e>" +
            "<g xsl:inherit-namespaces='no' xmlns:n='urn:n'><h><n:i/></h></g>" +
            "</a>" +
            "</xsl:template>" +
            XsltTestHelper.END,
            null,
            true);
  }

  @Test
  public void testTextAndHtmlDrained ()
  {
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='text'/>" +
            "<xsl:template match='/'><xsl:for-each select='//rec'><r><xsl:value-of select='@id'/>:<xsl:value-of select='v'/></r>;</xsl:for-each><xsl:comment>no</xsl:comment>end</xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            true);
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='html' version='5' indent='no' include-content-type='no'/>" +
            "<xsl:template match='/'><html><head><title>t</title></head><body><br/><p checked='checked'>x<script>a &lt; b</script></p>" +
            "<xsl:for-each select='//rec'><div><xsl:value-of select='v'/><hr/></div></xsl:for-each></body></html></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            true);
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xhtml' include-content-type='no'/>" +
            "<xsl:template match='/'><html xmlns='http://www.w3.org/1999/xhtml'><head><title>t</title></head><body><br/><p>x</p></body></html></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            true);
  }

  @Test
  public void testParametersDrained ()
  {
    // Character maps and CDATA sections apply per text node with the xml
    // method; the encoding decides about character references
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xml' use-character-maps='m' cdata-section-elements='c' encoding='ISO-8859-1'/>" +
            "<xsl:character-map name='m'><xsl:output-character character='§' string='[S]'/></xsl:character-map>" +
            "<xsl:template name='xsl:initial-template'><out>a§b€<c>x]]&gt;y</c><d>ä</d></out></xsl:template>" +
            XsltTestHelper.END,
            null,
            true);
    // An initial function
    final XsltExecutable aExec = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                       "<xsl:output method='xml'/>" +
                                                       "<xsl:function name='f:f' xmlns:f='urn:f' visibility='public'><xsl:sequence select='1, 2'/><e/></xsl:function>" +
                                                       XsltTestHelper.END,
                                                       new XsltCompileOptions ());
    final StringWriter aSW = new StringWriter ();
    aExec.transform (new XsltTransformRequest ().setInitialFunction (new XdmQName ("urn:f", "f")), aSW);
    assertEquals (aExec.transform (new XsltTransformRequest ().setInitialFunction (new XdmQName ("urn:f", "f")))
                       .principalSerialized (),
                  aSW.toString ());
  }

  @Test
  public void testLargeDrained ()
  {
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xml'/>" +
            "<xsl:template name='xsl:initial-template'><out><xsl:for-each select='1 to 20000'>" +
            "<r n='{.}'><xsl:value-of select='. * 2'/><s/></r></xsl:for-each></out></xsl:template>" +
            XsltTestHelper.END,
            null,
            true);
  }

  @Test
  public void testFallbacks ()
  {
    final String sBody = "<xsl:template match='/'><out><xsl:copy-of select='//rec'/>text</out></xsl:template>";
    // No method: it is only known from the result
    _check (XsltTestHelper.SHEET + sBody + XsltTestHelper.END, SOURCE, false);
    // html with the Content-Type meta element (the default)
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='html'/>" +
            "<xsl:template match='/'><html><head><title>t</title></head><body/></html></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            false);
    // Indentation, standalone, doctype-system, item separator
    _check (XsltTestHelper.SHEET + "<xsl:output method='xml' indent='yes'/>" + sBody + XsltTestHelper.END, SOURCE, false);
    _check (XsltTestHelper.SHEET + "<xsl:output method='xml' standalone='yes'/>" + sBody + XsltTestHelper.END, SOURCE, false);
    _check (XsltTestHelper.SHEET + "<xsl:output method='xml' doctype-system='a.dtd'/>" + sBody + XsltTestHelper.END, SOURCE, false);
    _check (XsltTestHelper.SHEET + "<xsl:output method='xml' item-separator='|'/>" + sBody + XsltTestHelper.END, SOURCE, false);
    // Character map and normalisation with the text method
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='text' use-character-maps='m'/>" +
            "<xsl:character-map name='m'><xsl:output-character character='a' string='A'/></xsl:character-map>" +
            sBody +
            XsltTestHelper.END,
            SOURCE,
            false);
    _check (XsltTestHelper.SHEET + "<xsl:output method='text' normalization-form='NFC'/>" + sBody + XsltTestHelper.END, SOURCE, false);
    // An xsl:result-document anywhere may redirect the principal output
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='xml'/>" +
            "<xsl:template match='/'><out/><xsl:if test='false()'><xsl:result-document href='x.xml'><y/></xsl:result-document></xsl:if></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            false);
    // The json method serializes a value
    _check (XsltTestHelper.SHEET +
            "<xsl:output method='json'/>" +
            "<xsl:template match='/'><xsl:sequence select='map { \"a\" : 1 }'/></xsl:template>" +
            XsltTestHelper.END,
            SOURCE,
            false);
  }

  @Test
  public void testErrorAfterPartialOutput ()
  {
    final StringWriter aSW = new StringWriter ();
    assertEquals ("FOER0000",
                  _errorCode (XsltTestHelper.SHEET +
                              "<xsl:output method='xml'/>" +
                              "<xsl:template name='xsl:initial-template'><out><xsl:for-each select='1 to 10000'>" +
                              "<r><xsl:value-of select='.'/></r><xsl:if test='. = 9000'><xsl:sequence select='error()'/></xsl:if>" +
                              "</xsl:for-each></out></xsl:template>" +
                              XsltTestHelper.END,
                              null,
                              aSW));
    // What was written before the error
    assertTrue (aSW.toString ().startsWith ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><out"));
    assertTrue (aSW.toString ().contains ("><r>1</r><r>2</r>"));
    assertTrue (aSW.toString ().contains ("<r>8999</r>"));
  }

  @Test
  public void testSerializationErrors ()
  {
    // SERE0006 is found by the writer, after part of the output
    final String sSere0006 = XsltTestHelper.SHEET +
                             "<xsl:output method='xml'/>" +
                             "<xsl:template name='xsl:initial-template'><out><a>x</a><b><xsl:value-of select='codepoints-to-string(1)'/></b><c/></out></xsl:template>" +
                             XsltTestHelper.END;
    assertEquals ("SERE0006", XsltTestHelper.errorCode (sSere0006, null));
    StringWriter aSW = new StringWriter ();
    final long nBefore = ResultDrain.getDrainedRunCount ();
    assertEquals ("SERE0006", _errorCode (sSere0006, null, aSW));
    assertEquals (1, ResultDrain.getDrainedRunCount () - nBefore);
    assertTrue (aSW.toString (), aSW.toString ().endsWith ("><a>x</a><b>"));

    // A dynamic error later in the run takes precedence, as without draining
    final String sLater = XsltTestHelper.SHEET +
                          "<xsl:output method='xml'/>" +
                          "<xsl:template name='xsl:initial-template'><out><b><xsl:value-of select='codepoints-to-string(1)'/></b><xsl:sequence select='error(xs:QName(\"Q\"))'/></out></xsl:template>" +
                          XsltTestHelper.END;
    assertEquals ("Q", XsltTestHelper.errorCode (sLater, null));
    assertEquals ("Q", _errorCode (sLater, null, new StringWriter ()));

    // Sequence normalisation of the top level
    final String sSenr = XsltTestHelper.SHEET +
                         "<xsl:output method='xml'/>" +
                         "<xsl:template name='xsl:initial-template'><a/><xsl:attribute name='x'>1</xsl:attribute></xsl:template>" +
                         XsltTestHelper.END;
    assertEquals ("SENR0001", XsltTestHelper.errorCode (sSenr, null));
    aSW = new StringWriter ();
    assertEquals ("SENR0001", _errorCode (sSenr, null, aSW));

    // html: a processing instruction with '>'
    final String sSere0015 = XsltTestHelper.SHEET +
                             "<xsl:output method='html' indent='no' include-content-type='no'/>" +
                             "<xsl:template name='xsl:initial-template'><html><body><xsl:processing-instruction name='p'>a>b</xsl:processing-instruction></body></html></xsl:template>" +
                             XsltTestHelper.END;
    assertEquals ("SERE0015", XsltTestHelper.errorCode (sSere0015, null));
    assertEquals ("SERE0015", _errorCode (sSere0015, null, new StringWriter ()));
  }

  @Test
  public void testUtf16Stream ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                       "<xsl:output method='xml' encoding='UTF-16'/>" +
                                                       "<xsl:template name='xsl:initial-template'><out>ä</out></xsl:template>" +
                                                       XsltTestHelper.END,
                                                       new XsltCompileOptions ());
    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    aExec.transform (new XsltTransformRequest (), aBAOS);
    final byte [] aBytes = aBAOS.toByteArray ();
    // Exactly one byte order mark
    assertEquals (0xfe, aBytes[0] & 0xff);
    assertEquals (0xff, aBytes[1] & 0xff);
    assertEquals (aExec.transform (new XsltTransformRequest ()).principalSerialized (),
                  new String (aBytes, 2, aBytes.length - 2, StandardCharsets.UTF_16BE));
  }
}
