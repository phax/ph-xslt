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
package com.helger.phoxtrot.xslt.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.stream.EStreamFunctionCategory;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.compile.FileResourceResolver;

/**
 * Test class for {@link XsltStreamability}: the instruction-level rules of
 * XSLT 3.0 section 19.8.4 driven through a template rule of a streamable mode
 * and a streamable <code>xsl:source-document</code>, stylesheet functions
 * (19.8.5), attribute sets (19.8.6), streamable accumulators (18.2.8) and
 * streamable merge sources (15.4). The cases are those of go-xslt's
 * <code>streamability_test.go</code>, <code>streamability_rules_test.go</code>
 * and <code>streamability_func_test.go</code>. Every stylesheet is compiled
 * with binding streamability declarations; an accepted one is then run (still
 * buffered) and its result compared.
 *
 * @author Philip Helger
 */
public final class XsltStreamabilityTest
{
  /** Three records whose v children sum to 6. */
  private static final String DOC = "<data><rec id='r1'><v>1</v></rec><rec id='r2'><v>2</v></rec><rec id='r3'><v>3</v></rec></data>";
  private static final String NS = " xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                   " xmlns:xs='http://www.w3.org/2001/XMLSchema'" +
                                   " xmlns:f='http://example.com/f'";
  private static final String REC_TEMPLATE = "<xsl:template match='rec'><xsl:value-of select='v'/></xsl:template>";
  private static final String G_REC_TEMPLATE = "<xsl:template match='rec' mode='g'><xsl:value-of select='v'/></xsl:template>";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @NonNull
  private static XsltCompileOptions _strict ()
  {
    return new XsltCompileOptions ().setStreamingClaim (true).setEnforceStreamability (true);
  }

  /** The body as the "/" rule of the unnamed mode, declared streamable. */
  @NonNull
  private static String _modeSheet (@NonNull final String sBody, @NonNull final String sExtra)
  {
    return "<xsl:stylesheet version='3.0'" +
           NS +
           "><xsl:output method='text'/><xsl:mode streamable='yes'/>" +
           sExtra +
           "<xsl:template match='/'>" +
           sBody +
           "</xsl:template></xsl:stylesheet>";
  }

  /** A declaration and a rule for doc in the streamable unnamed mode. */
  @NonNull
  private static String _declSheet (@NonNull final String sDecl, @NonNull final String sBody)
  {
    return "<xsl:stylesheet version='3.0'" +
           NS +
           "><xsl:mode streamable='yes'/>" +
           sDecl +
           "<xsl:template match='doc'>" +
           sBody +
           "</xsl:template></xsl:stylesheet>";
  }

  /**
   * @return The error code of compiling the stylesheet with binding
   *         streamability declarations, or <code>null</code> if it compiles.
   */
  @Nullable
  private static String _compileError (@NonNull final String sSheet, @NonNull final XsltCompileOptions aOptions)
  {
    try
    {
      XsltCompiler.compile (sSheet, aOptions);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  @Nullable
  private static String _compileError (@NonNull final String sSheet)
  {
    return _compileError (sSheet, _strict ());
  }

  /**
   * Compile strictly and expect XTSE3430 (<code>bReject</code>), or run it on
   * {@link #DOC} and compare the text output.
   */
  private void _check (@NonNull final String sSheet, final boolean bReject, @Nullable final String sWant)
  {
    final Path aDir;
    try
    {
      aDir = m_aTempFolder.newFolder ().toPath ();
      Files.writeString (aDir.resolve ("d.xml"), DOC, StandardCharsets.UTF_8);
    }
    catch (final IOException ex)
    {
      throw new IllegalStateException (ex);
    }
    final XsltCompileOptions aOptions = _strict ().setBaseUri (aDir.resolve ("sheet.xsl").toUri ().toString ());
    if (bReject)
    {
      assertEquals (sSheet, "XTSE3430", _compileError (sSheet, aOptions));
      return;
    }
    final XsltExecutable aExec;
    try
    {
      aExec = XsltCompiler.compile (sSheet, aOptions);
    }
    catch (final XPathException ex)
    {
      fail ("Compile failed: " + ex.getMessage () + "\n" + sSheet);
      return;
    }
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (DOC))
                                                                     .setResolver (new FileResourceResolver (false));
    assertEquals (sSheet, sWant, aExec.transform (aRequest).principalSerialized ().trim ());
  }

  private void _accept (@NonNull final String sBody, @NonNull final String sExtra, @NonNull final String sWant)
  {
    _check (_modeSheet (sBody, sExtra), false, sWant);
  }

  private void _reject (@NonNull final String sBody, @NonNull final String sExtra)
  {
    _check (_modeSheet (sBody, sExtra), true, null);
  }

  @Test
  public void testForEach ()
  {
    // Section 19.8.4.18
    _accept ("<xsl:for-each select='data/rec'><xsl:value-of select='v'/></xsl:for-each>", "", "123");
    _reject ("<xsl:for-each select='data/rec'><xsl:sort select='v'/><xsl:value-of select='v'/></xsl:for-each>", "");
    _accept ("<xsl:for-each select='(3, 1, 2)'><xsl:sort select='.' order='descending'/><xsl:value-of select='.'/></xsl:for-each>",
             "",
             "321");
    _reject ("<xsl:for-each select='data/rec'><xsl:value-of select='v'/><xsl:value-of select='v'/></xsl:for-each>", "");
    _reject ("<xsl:for-each select='//rec'><xsl:value-of select='v'/></xsl:for-each>", "");
  }

  @Test
  public void testForEachGroup ()
  {
    // Section 19.8.4.19
    _accept ("<xsl:for-each-group select='data/rec' group-adjacent='@id'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>",
             "",
             "r1r2r3");
    _reject ("<xsl:for-each-group select='data/rec' group-by='@id'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>",
             "");
    _reject ("<xsl:for-each-group select='data/rec' group-adjacent='string(v)'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>",
             "");
    _accept ("<xsl:fork><xsl:for-each-group select='data/rec' group-by='@id'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group></xsl:fork>",
             "",
             "r1r2r3");
    // Only the direct child of xsl:fork may use group-by (a deviation from
    // go-xslt, which lets nested ones pass too)
    _reject ("<xsl:fork><xsl:for-each-group select='data' group-adjacent='name()'>" +
             "<xsl:for-each-group select='rec' group-by='@id'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>" +
             "</xsl:for-each-group></xsl:fork>",
             "");
    _accept ("<xsl:for-each-group select='1 to 4' group-by='. mod 2'><xsl:sort select='current-grouping-key()'/>" +
             "<xsl:value-of select='current-grouping-key(), sum(current-group())'/>;</xsl:for-each-group>",
             "",
             "0 6;1 4;");
    _accept ("<xsl:for-each-group select='data/rec' group-starting-with=\"rec[@id = 'r2']\"><xsl:value-of select='count(current-group())'/></xsl:for-each-group>",
             "",
             "12");
    _reject ("<xsl:for-each-group select='data/rec' group-adjacent='@id'><xsl:sort select='current-grouping-key()'/>" +
             "<xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>",
             "");
    _reject ("<xsl:for-each-group select='//rec' group-adjacent='@id'><xsl:value-of select='v'/></xsl:for-each-group>", "");
    // Section 19.8.10: p[b] is not motionless
    _reject ("<xsl:for-each-group select='data/rec' group-starting-with='rec[v]'><xsl:value-of select='count(current-group())'/></xsl:for-each-group>",
             "");
  }

  @Test
  public void testCurrentGroup ()
  {
    // Section 19.8.9.4: a grounded population never moves the stream
    _accept ("<xsl:for-each-group select='copy-of(data/rec)' group-starting-with='rec[@id]'>" +
             "<a><xsl:value-of select='current-group()[1]/@id'/></a><b><xsl:value-of select='current-group()[1]/v'/></b>" +
             "</xsl:for-each-group>",
             "",
             "r11r22r33");
    // Over a streamed population the two reads compete
    _reject ("<xsl:for-each-group select='data/rec' group-starting-with='rec[@id]'>" +
             "<a><xsl:value-of select='current-group()[1]/v'/></a><b><xsl:value-of select='current-group()[1]/v'/></b>" +
             "</xsl:for-each-group>",
             "");
  }

  @Test
  public void testIterate ()
  {
    // Section 19.8.4.22
    _accept ("<xsl:iterate select='data/rec'><xsl:param name='n' select='0'/>" +
             "<xsl:on-completion><xsl:value-of select='$n'/></xsl:on-completion>" +
             "<xsl:next-iteration><xsl:with-param name='n' select='$n + v'/></xsl:next-iteration></xsl:iterate>",
             "",
             "6");
    _reject ("<xsl:iterate select='data/rec'><xsl:param name='n' select='string(data)'/><xsl:value-of select='v'/></xsl:iterate>",
             "");
    _reject ("<xsl:iterate select='data/rec'><xsl:on-completion><xsl:value-of select='name(.)'/></xsl:on-completion>" +
             "<xsl:value-of select='v'/></xsl:iterate>",
             "");
    _reject ("<xsl:iterate select='//rec'><xsl:value-of select='v'/></xsl:iterate>", "");
    _accept ("<xsl:iterate select='1 to 3'><xsl:param name='s' select='0'/>" +
             "<xsl:on-completion><xsl:value-of select='$s'/></xsl:on-completion>" +
             "<xsl:next-iteration><xsl:with-param name='s' select='$s + .'/></xsl:next-iteration></xsl:iterate>",
             "",
             "6");
  }

  @Test
  public void testFork ()
  {
    // Section 19.8.4.20
    _accept ("<xsl:fork><xsl:sequence select='count(data/rec)'/><xsl:sequence select='sum(data/rec/v)'/></xsl:fork>", "", "3 6");
    _reject ("<xsl:fork><xsl:sequence select='data/rec'/><xsl:sequence select='data'/></xsl:fork>", "");
    _accept ("<xsl:fork/>[<xsl:value-of select='count(data/rec)'/>]", "", "[3]");
  }

  @Test
  public void testMap ()
  {
    // Sections 19.8.4.23 and 19.8.4.24
    _accept ("<xsl:variable name='m' as='map(*)'><xsl:map>" +
             "<xsl:map-entry key=\"'a'\" select='count(data/rec)'/>" +
             "<xsl:map-entry key=\"'b'\" select='sum(data/rec/v)'/></xsl:map></xsl:variable>" +
             "<xsl:value-of select='$m?a, $m?b'/>",
             "",
             "3 6");
    _reject ("<xsl:variable name='m' as='map(*)'><xsl:map><xsl:map-entry key=\"'a'\" select='data/rec'/></xsl:map></xsl:variable>" +
             "<xsl:value-of select='map:size($m)' xmlns:map='http://www.w3.org/2005/xpath-functions/map'/>",
             "");
    _accept ("<xsl:variable name='m' as='map(*)'><xsl:map><xsl:sequence select=\"map{'k': count(data/rec)}\"/></xsl:map></xsl:variable>" +
             "<xsl:value-of select='$m?k'/>",
             "",
             "3");
  }

  @Test
  public void testMerge ()
  {
    // Section 19.8.4.25
    _accept ("<xsl:merge>" +
             "<xsl:merge-source name='g' select='$g/a'><xsl:merge-key select='xs:integer(@k)'/></xsl:merge-source>" +
             "<xsl:merge-source name='h' select='$h/a'><xsl:merge-key select='xs:integer(@k)'/></xsl:merge-source>" +
             "<xsl:merge-action><xsl:value-of select='current-merge-key()'/></xsl:merge-action></xsl:merge>",
             "<xsl:variable name='g'><a k='1'/><a k='3'/></xsl:variable><xsl:variable name='h'><a k='2'/></xsl:variable>",
             "123");
    _reject ("<xsl:merge><xsl:merge-source select='data/rec'><xsl:merge-key select='string(@id)'/></xsl:merge-source>" +
             "<xsl:merge-action><xsl:value-of select='current-merge-key()'/></xsl:merge-action></xsl:merge>",
             "");
  }

  @Test
  public void testNestedSourceDocument ()
  {
    // Section 19.8.4.35
    _accept ("<xsl:source-document href='d.xml'><xsl:value-of select='count(//rec)'/></xsl:source-document>", "", "3");
    _reject ("<xsl:for-each-group select='data/rec' group-adjacent='@id'><xsl:source-document href='d.xml'>" +
             "<xsl:value-of select='count(current-group())'/></xsl:source-document></xsl:for-each-group>",
             "");
  }

  @Test
  public void testApplyTemplates ()
  {
    // Section 19.8.4.5
    _accept ("<xsl:apply-templates select='data/rec'/>", REC_TEMPLATE, "123");
    _accept ("<xsl:apply-templates select='data/rec' mode='#current'/>", REC_TEMPLATE, "123");
    _reject ("<xsl:apply-templates select='data/rec' mode='g'/>", G_REC_TEMPLATE);
    _reject ("<xsl:apply-templates select='data/rec'><xsl:sort select='v'/></xsl:apply-templates>", REC_TEMPLATE);
    _reject ("<xsl:apply-templates select='//rec'/>", REC_TEMPLATE);
    _accept ("<xsl:apply-templates select='copy-of(data/rec)' mode='g'><xsl:sort select='v' order='descending'/></xsl:apply-templates>",
             G_REC_TEMPLATE,
             "321");
  }

  @Test
  public void testGeneralRules ()
  {
    // Section 19.8.4.31: perform-sort of streamed nodes navigates
    _reject ("<xsl:perform-sort select='data/rec'><xsl:sort select='v'/></xsl:perform-sort>", "");
    // Section 19.8.4.30
    _reject ("<xsl:number/>", "");
    _accept ("<xsl:number value='3' format='i'/>", "", "iii");
    _accept ("<xsl:analyze-string select=\"'a1b'\" regex='[0-9]'><xsl:matching-substring>[<xsl:value-of select='.'/>]</xsl:matching-substring>" +
             "<xsl:non-matching-substring><xsl:value-of select='.'/></xsl:non-matching-substring></xsl:analyze-string>",
             "",
             "a[1]b");
    // Section 19.8.4.37: the try body and the catch bodies form a choice group
    _accept ("<xsl:try><xsl:value-of select='sum(data/rec/v)'/><xsl:catch><xsl:value-of select=\"'failed'\"/></xsl:catch></xsl:try>",
             "",
             "6");
    _reject ("<xsl:try><xsl:value-of select='sum(data/rec/v)'/><xsl:catch><xsl:value-of select='count(data/rec)'/></xsl:catch></xsl:try>",
             "");
    _accept ("<xsl:try><xsl:value-of select=\"'t'\"/>" +
             "<xsl:catch errors='err:XTDE0000' xmlns:err='http://www.w3.org/2005/xqt-errors'><xsl:value-of select='count(data/rec)'/></xsl:catch>" +
             "<xsl:catch><xsl:value-of select='sum(data/rec/v)'/></xsl:catch></xsl:try>",
             "",
             "t");
    _accept ("<xsl:copy-of select='data/rec/v'/>", "", "123");
    _reject ("<xsl:value-of select='data/rec/v'/><xsl:value-of select='count(data/rec)'/>", "");
    // Section 19.8.4.41
    _reject ("<xsl:variable name='d' as='element()' select='data'/><xsl:value-of select='name($d)'/>", "");
    _accept ("<xsl:variable name='d' as='xs:string' select='string(data)'/><xsl:value-of select='$d'/>", "", "123");
    _accept ("<xsl:variable name='m' as='map(xs:string, xs:integer)' select=\"map{'n': count(data/rec)}\"/><xsl:value-of select=\"$m('n')\"/>",
             "",
             "3");
  }

  @Test
  public void testDirect ()
  {
    // go-xslt TestClassifyStreamableDirect
    _accept ("", "", "");
    _accept ("hello", "", "hello");
    _accept ("<xsl:value-of select='data/rec'/>", "", "1 2 3");
    _accept ("<xsl:for-each select='data/rec'><xsl:value-of select='@id'/></xsl:for-each>", "", "r1r2r3");
    _accept ("<xsl:copy-of select='data'/>", "", "123");
    _reject ("<xsl:value-of select='data'/><xsl:value-of select='data/rec'/>", "");
    _reject ("<xsl:value-of select='../a/b'/>", "");
    _reject ("<xsl:value-of select='preceding::a'/>", "");
    _reject ("<xsl:variable name='v' select='data'/><xsl:value-of select='$v'/>", "");
    _reject ("<xsl:value-of select='data/rec[last()]'/>", "");
    _accept ("<xsl:value-of select='data/rec[1]'/>", "", "1");
    _accept ("<xsl:fork><xsl:sequence select='copy-of(data/rec[1])'/><xsl:sequence select='copy-of(data)'/></xsl:fork>", "", "1123");
    _accept ("<xsl:choose><xsl:when test='@x'><xsl:value-of select='data'/></xsl:when>" +
             "<xsl:otherwise><xsl:value-of select='data/rec'/></xsl:otherwise></xsl:choose>",
             "",
             "1 2 3");
    _accept ("<xsl:for-each select=\"data/rec[@id='r2']\"><xsl:value-of select='@id'/></xsl:for-each>", "", "r2");
    _reject ("<xsl:value-of select='data[rec]/rec'/>", "");
    // Section 19.8.8.7: a scanning expression may start at "."
    _accept ("<xsl:copy-of select='outermost(.//v)'/>", "", "123");
    _accept ("<xsl:copy-of select='outermost(data//v)'/>", "", "123");
    _reject ("<xsl:copy-of select='.//v/../v'/>", "");
  }

  @Test
  public void testTemplateResultPosture ()
  {
    // Section 6.7.1: a template rule cannot return streamed nodes
    final String sSheetStart = "<xsl:stylesheet version='3.0'" +
                               NS +
                               "><xsl:output method='text'/><xsl:mode streamable='yes'/><xsl:template match='/'";
    final String sSheetEnd = "</xsl:template></xsl:stylesheet>";
    _check (sSheetStart + "><xsl:sequence select='data/rec'/>" + sSheetEnd, true, null);
    _check (sSheetStart + " as='xs:string'><xsl:sequence select='string(data)'/>" + sSheetEnd, false, "123");
    _check (sSheetStart + " as='xs:string*'><xsl:sequence select='data/rec/@id'/>" + sSheetEnd, false, "r1 r2 r3");
  }

  @Test
  public void testMatchPatterns ()
  {
    // Section 19.8.10
    final String sSheetStart = "<xsl:stylesheet version='3.0'" +
                               NS +
                               "><xsl:output method='text'/><xsl:mode streamable='yes'/><xsl:template match=\"";
    final String sSheetEnd = "\"><xsl:value-of select='@id'/></xsl:template><xsl:template match='text()'/></xsl:stylesheet>";
    _check (sSheetStart + "rec" + sSheetEnd, false, "r1r2r3");
    _check (sSheetStart + "rec[@id != 'r2']" + sSheetEnd, false, "r1r3");
    _check (sSheetStart + "rec[v]" + sSheetEnd, true, null);
  }

  @Test
  public void testSourceDocument ()
  {
    // Section 18.1
    final String sStart = "<xsl:stylesheet version='3.0'" + NS + "><xsl:output method='text'/><xsl:template match='/'>";
    final String sEnd = "</xsl:template></xsl:stylesheet>";
    _check (sStart +
            "<xsl:source-document href='d.xml' streamable='yes'><xsl:for-each select='data/rec'><xsl:value-of select='v'/></xsl:for-each></xsl:source-document>" +
            sEnd,
            false,
            "123");
    _check (sStart + "<xsl:source-document href='d.xml' streamable='true'><xsl:value-of select='count(data/rec)'/></xsl:source-document>" + sEnd,
            false,
            "3");
    // A striding body returns streamed nodes
    _check (sStart + "<xsl:source-document href='d.xml' streamable='yes'><xsl:sequence select='data/rec/v'/></xsl:source-document>" + sEnd,
            true,
            null);
    // A verdict the Recommendation states unconditionally
    _check (sStart +
            "<xsl:source-document href='d.xml' streamable='yes'><xsl:for-each-group select='data/rec' group-by='@id'>" +
            "<xsl:value-of select='current-grouping-key()'/></xsl:for-each-group></xsl:source-document>" +
            sEnd,
            true,
            null);
    // Not declared streamable: not analysed
    _check (sStart + "<xsl:source-document href='d.xml'><xsl:sequence select='data/rec/v'/></xsl:source-document>" + sEnd, false, "123");
    _check (sStart + "<xsl:source-document href='d.xml' streamable='no'><xsl:sequence select='data/rec/v'/></xsl:source-document>" + sEnd,
            false,
            "123");
    // Inside xsl:for-each-group the group may be reached
    _check (sStart +
            "<xsl:for-each-group select='1 to 2' group-by='.'><xsl:value-of select='current-grouping-key()'/>" +
            "<xsl:source-document href='d.xml' streamable='yes'><xsl:value-of select='count(data/rec) + count(current-group())'/></xsl:source-document>" +
            "</xsl:for-each-group>" +
            sEnd,
            false,
            "1424");
  }

  @Test
  public void testMergeSource ()
  {
    // Section 15.4
    final String sStart = "<xsl:stylesheet version='3.0'" +
                          NS +
                          "><xsl:output method='text'/><xsl:template match='/'><xsl:merge><xsl:merge-source ";
    final String sEnd = "><xsl:merge-key select='string(@id)'/></xsl:merge-source>" +
                        "<xsl:merge-action><xsl:value-of select='current-merge-key()'/></xsl:merge-action></xsl:merge></xsl:template></xsl:stylesheet>";
    _check (sStart + "for-each-source=\"'d.xml'\" streamable='yes' select='data/rec'" + sEnd, false, "r1r2r3");
    _check (sStart + "streamable='yes' select='data/rec'" + sEnd, true, null);
    _check (sStart + "for-each-source=\"'d.xml'\" streamable='yes' sort-before-merge='yes' select='data/rec'" + sEnd, true, null);
    // //rec is crawling
    _check (sStart + "for-each-source=\"'d.xml'\" streamable='yes' select='//rec'" + sEnd, true, null);
    _check (sStart + "for-each-source=\"'d.xml'\" select='//rec'" + sEnd, false, "r1r2r3");
  }

  @Test
  public void testEnforcementOff ()
  {
    // Without binding declarations a mode the analysis cannot prove runs
    // without streaming, as XTSE3430 permits
    final String sSheet = _modeSheet ("<xsl:value-of select='data/rec/v'/><xsl:value-of select='count(data/rec)'/>", "");
    assertEquals ("XTSE3430", _compileError (sSheet));
    assertNull (_compileError (sSheet, new XsltCompileOptions ().setStreamingClaim (true)));
    assertEquals ("1 2 33", XsltTestHelper.transform (sSheet, DOC, null));

    // A rule of a named streamable mode
    final String sNamed = "<xsl:stylesheet version='3.0'" +
                          NS +
                          "><xsl:mode name='s' streamable='yes'/><xsl:template match='MATCH' mode='s'>BODY</xsl:template></xsl:stylesheet>";
    assertNull (_compileError (sNamed.replace ("MATCH", "r").replace ("BODY", "<xsl:value-of select='a/b'/>")));
    assertEquals ("XTSE3430",
                  _compileError (sNamed.replace ("MATCH", "r")
                                       .replace ("BODY", "<xsl:value-of select='a'/><xsl:value-of select='b'/>")));
    assertEquals ("XTSE3430", _compileError (sNamed.replace ("MATCH", "r[b]").replace ("BODY", "x")));
    // A rule of a mode that is not declared streamable is not analysed
    assertNull (_compileError ("<xsl:stylesheet version='3.0'" +
                               NS +
                               "><xsl:template match='r' mode='s'><xsl:value-of select='a'/><xsl:value-of select='b'/></xsl:template></xsl:stylesheet>"));
  }

  @Test
  public void testFunctionCategory ()
  {
    assertEquals (EStreamFunctionCategory.UNCLASSIFIED, XsltStreamability.getFunctionCategory (null));
    assertEquals (EStreamFunctionCategory.UNCLASSIFIED, XsltStreamability.getFunctionCategory ("unclassified"));
    assertEquals (EStreamFunctionCategory.ABSORBING, XsltStreamability.getFunctionCategory ("absorbing"));
    assertEquals (EStreamFunctionCategory.INSPECTION, XsltStreamability.getFunctionCategory ("inspection"));
    assertEquals (EStreamFunctionCategory.FILTER, XsltStreamability.getFunctionCategory (" filter "));
    assertEquals (EStreamFunctionCategory.SHALLOW_DESCENT, XsltStreamability.getFunctionCategory ("shallow-descent"));
    assertEquals (EStreamFunctionCategory.DEEP_DESCENT, XsltStreamability.getFunctionCategory ("deep-descent"));
    assertEquals (EStreamFunctionCategory.ASCENT, XsltStreamability.getFunctionCategory ("ascent"));
    // A category in an implementation-defined namespace that this processor
    // does not recognise is analysed as unclassified
    assertEquals (EStreamFunctionCategory.UNCLASSIFIED, XsltStreamability.getFunctionCategory ("ext:capture"));
    assertEquals (EStreamFunctionCategory.UNCLASSIFIED, XsltStreamability.getFunctionCategory ("nonsense"));
  }

  @Test
  public void testFunctionCalls ()
  {
    // Section 19.8.5: a call is classified from the declared category
    final String sAbsorbing = "<xsl:function name='f:g' as='xs:string' streamability='absorbing'><xsl:param name='n' as='node()'/>" +
                              "<xsl:sequence select='string($n)'/></xsl:function>";
    final String sAscent = "<xsl:function name='f:g' as='node()*' streamability='ascent'><xsl:param name='n' as='node()'/>" +
                           "<xsl:sequence select='$n/ancestor::x'/></xsl:function>";
    final String sUnclassified = "<xsl:function name='f:g' streamability='unclassified'><xsl:param name='n'/><xsl:sequence select='$n'/></xsl:function>";
    assertNull (_compileError (_declSheet (sUnclassified, "<xsl:copy-of select='f:g(copy-of(.))'/>")));
    assertEquals ("XTSE3430", _compileError (_declSheet (sUnclassified, "<xsl:copy-of select='f:g(a)'/>")));
    assertNull (_compileError (_declSheet ("<xsl:function name='f:g' as='xs:string'><xsl:param name='n' as='xs:string'/>" +
                                           "<xsl:sequence select='$n'/></xsl:function>",
                                           "<xsl:value-of select='f:g(a)'/>")));
    assertNull (_compileError (_declSheet (sAbsorbing, "<xsl:value-of select='f:g(a)'/>")));
    // Section 19.8.5.2: absorption may not be applied to a crawling sequence
    assertEquals ("XTSE3430", _compileError (_declSheet (sAbsorbing, "<xsl:value-of select='f:g(.//a)'/>")));
    // An argument after the first has its own type-determined usage
    assertEquals ("XTSE3430",
                  _compileError (_declSheet ("<xsl:function name='f:g' as='xs:string' streamability='absorbing'><xsl:param name='n' as='node()'/>" +
                                             "<xsl:param name='o'/><xsl:sequence select='string($n)'/></xsl:function>",
                                             "<xsl:value-of select='f:g(a, b)'/>")));
    assertNull (_compileError (_declSheet ("<xsl:function name='f:g' as='xs:string' streamability='inspection'><xsl:param name='n' as='node()'/>" +
                                           "<xsl:sequence select='name($n)'/></xsl:function>",
                                           "<xsl:value-of select='f:g(a)'/>")));
    assertNull (_compileError (_declSheet ("<xsl:function name='f:g' as='node()*' streamability='filter'><xsl:param name='n' as='node()'/>" +
                                           "<xsl:sequence select='$n[@x]'/></xsl:function>",
                                           "<xsl:value-of select='f:g(a)/@y'/>")));
    assertNull (_compileError (_declSheet ("<xsl:function name='f:g' as='node()*' streamability='shallow-descent'><xsl:param name='n' as='element()'/>" +
                                           "<xsl:sequence select='$n/node()'/></xsl:function>",
                                           "<xsl:copy-of select='f:g(a)'/>")));
    assertNull (_compileError (_declSheet ("<xsl:function name='f:g' as='node()*' streamability='deep-descent'><xsl:param name='n' as='element()'/>" +
                                           "<xsl:sequence select='$n//comment()'/></xsl:function>",
                                           "<xsl:copy-of select='f:g(a)'/>")));
    // Section 19.8.5.7: the argument of an ascent call may not consume
    assertNull (_compileError (_declSheet (sAscent, "<xsl:value-of select='f:g(@a)/@y'/>")));
    assertEquals ("XTSE3430", _compileError (_declSheet (sAscent, "<xsl:value-of select='f:g(a)/@y'/>")));
    // A call to a zero-arity function is grounded and motionless
    assertNull (_compileError (_declSheet ("<xsl:function name='f:g' as='xs:integer'><xsl:sequence select='42'/></xsl:function>",
                                           "<xsl:value-of select='a'/><xsl:value-of select='f:g()'/>")));
  }

  @NonNull
  private static String _function (@NonNull final String sAs,
                                   @NonNull final String sCategory,
                                   @NonNull final String sParam,
                                   @NonNull final String sBody)
  {
    return "<xsl:function name='f:g'" +
           (sAs.isEmpty () ? "" : " as='" + sAs + "'") +
           " streamability='" +
           sCategory +
           "'>" +
           sParam +
           "<xsl:sequence select=\"" +
           sBody +
           "\"/></xsl:function>";
  }

  @Nullable
  private static String _functionError (@NonNull final String sDecl)
  {
    return _compileError (_declSheet (sDecl, "x"));
  }

  @Test
  public void testFunctionBodies ()
  {
    // Section 19.8.5: the constraints on the body of each category
    final String sNode = "<xsl:param name='n' as='node()'/>";
    final String sNodes = "<xsl:param name='n' as='node()*'/>";
    assertEquals ("XTSE3430", _functionError (_function ("", "absorbing", sNode, "$n")));
    assertNull (_functionError (_function ("xs:string", "absorbing", sNode, "string($n)")));
    assertEquals ("XTSE3430", _functionError (_function ("xs:boolean", "absorbing", sNodes, "deep-equal($n[1], $n[2])")));
    assertEquals ("XTSE3430", _functionError (_function ("xs:string", "inspection", sNode, "string($n)")));
    assertNull (_functionError (_function ("xs:string", "inspection", sNode, "name($n)")));
    assertEquals ("XTSE3430", _functionError (_function ("node()*", "filter", sNode, "$n/x")));
    assertNull (_functionError (_function ("node()*", "filter", sNode, "$n[@x]")));
    // Every category but absorbing needs a streaming parameter of at most
    // one node
    assertEquals ("XTSE3430", _functionError (_function ("node()*", "filter", sNodes, "$n[@x]")));
    assertEquals ("XTSE3430", _functionError (_function ("xs:string", "inspection", "<xsl:param name='n'/>", "name($n)")));
    assertNull (_functionError (_function ("xs:integer", "absorbing", sNodes, "count($n//*)")));
    assertEquals ("XTSE3430", _functionError (_function ("xs:integer", "absorbing", sNodes, "count($n/a) + count($n/b)")));
    assertEquals ("XTSE3430", _functionError (_function ("node()*", "shallow-descent", sNode, "$n//x")));
    assertNull (_functionError (_function ("node()*", "deep-descent", sNode, "$n//x")));
    assertEquals ("XTSE3430",
                  _functionError (_function ("xs:string*", "absorbing", sNodes, "for $i in 1 to 3 return name($n[$i])")));
    assertNull (_functionError (_function ("", "unclassified", "<xsl:param name='n'/>", "$n/x/preceding-sibling::y")));
    // Only unclassified is permitted for a zero-arity function
    assertEquals ("XTSE3155", _functionError (_function ("", "shallow-descent", "", "23")));
    assertEquals ("XTSE3155", _functionError (_function ("", "absorbing", "", "1")));
  }

  @Test
  public void testAttributeSets ()
  {
    // Section 19.8.6
    final String sStart = "<xsl:stylesheet version='3.0'" + NS + "><xsl:mode streamable='yes'/><xsl:attribute-set name='s' ";
    final String sYes = "streamable='yes'";
    final String sConstant = "<xsl:attribute name='a' select=\"'x'\"/>";
    final String sConsuming = "<xsl:attribute name='a' select='b'/>";
    final String sElementBody = "<xsl:element name='e' use-attribute-sets='s'><xsl:value-of select='a'/></xsl:element>";
    final String sElementEmpty = "<xsl:element name='e' use-attribute-sets='s'/>";
    assertNull (_compileError (sStart + sYes + ">" + sConstant + "</xsl:attribute-set><xsl:template match='doc'>" + sElementBody + "</xsl:template></xsl:stylesheet>"));
    // A consuming set competes with a consuming element body
    assertEquals ("XTSE3430",
                  _compileError (sStart + sYes + ">" + sConsuming + "</xsl:attribute-set><xsl:template match='doc'>" + sElementBody + "</xsl:template></xsl:stylesheet>"));
    assertNull (_compileError (sStart + sYes + ">" + sConsuming + "</xsl:attribute-set><xsl:template match='doc'>" + sElementEmpty + "</xsl:template></xsl:stylesheet>"));
    // The declaration is what a reference reads (section 10.2.3)
    for (final String sDecl : new String [] { "", "streamable='no'" })
      assertEquals ("XTSE3430",
                    _compileError (sStart + sDecl + ">" + sConstant + "</xsl:attribute-set><xsl:template match='doc'>" + sElementEmpty + "</xsl:template></xsl:stylesheet>"));
  }

  @NonNull
  private static String _accumulatorSheet (@NonNull final String sAccumulator)
  {
    return "<xsl:stylesheet version='3.0'" +
           NS +
           "><xsl:mode streamable='yes'/>" +
           sAccumulator +
           "<xsl:template match='doc'><xsl:value-of select='a'/></xsl:template></xsl:stylesheet>";
  }

  @Test
  public void testAccumulators ()
  {
    // Section 18.2.8
    assertNull (_compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:integer' initial-value='0' streamable='yes'>" +
                                                  "<xsl:accumulator-rule match='fig' select='$value + 1'/></xsl:accumulator>")));
    // The declared type atomizes the attribute
    assertNull (_compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:double' initial-value='0' streamable='yes'>" +
                                                  "<xsl:accumulator-rule match='t' select='if (@amount lt $value) then @amount else $value'/></xsl:accumulator>")));
    assertEquals ("XTSE3430",
                  _compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:string' initial-value=\"''\" streamable='yes'>" +
                                                    "<xsl:accumulator-rule match='fig' select='string(.)'/></xsl:accumulator>")));
    // A text node's value is available without reading further input
    assertNull (_compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:string' initial-value=\"''\" streamable='yes'>" +
                                                  "<xsl:accumulator-rule match='fig/text()' select='string(.)'/></xsl:accumulator>")));
    assertEquals ("XTSE3430",
                  _compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:integer' initial-value='0' streamable='yes'>" +
                                                    "<xsl:accumulator-rule match='fig[x]' select='$value + 1'/></xsl:accumulator>")));
    assertEquals ("XTSE3430",
                  _compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:integer' initial-value='count(//x)' streamable='yes'>" +
                                                    "<xsl:accumulator-rule match='fig' select='$value + 1'/></xsl:accumulator>")));
    // Not declared streamable: unconstrained
    assertNull (_compileError (_accumulatorSheet ("<xsl:accumulator name='c' as='xs:string' initial-value=\"''\">" +
                                                  "<xsl:accumulator-rule match='fig' select='string(.)'/></xsl:accumulator>")));
  }

  @Test
  public void testAccumulatorFunctions ()
  {
    // Sections 19.8.9.1 and 19.8.9.2
    final String sStart = "<xsl:stylesheet version='3.0'" +
                          NS +
                          "><xsl:mode streamable='yes'/>" +
                          "<xsl:accumulator name='c' as='xs:integer' initial-value='0' streamable='yes'>" +
                          "<xsl:accumulator-rule match='fig' select='$value + 1'/></xsl:accumulator>" +
                          "<xsl:template match='doc'>";
    final String sEnd = "</xsl:template></xsl:stylesheet>";
    assertNull (_compileError (sStart + "<xsl:value-of select=\"accumulator-before('c')\"/><xsl:apply-templates/>" + sEnd));
    assertNull (_compileError (sStart + "<xsl:apply-templates/><xsl:value-of select=\"accumulator-after('c')\"/>" + sEnd));
    assertEquals ("XTSE3430",
                  _compileError (sStart + "<xsl:value-of select=\"accumulator-after('c')\"/><xsl:apply-templates/>" + sEnd));
  }

  @Test
  public void testForkContentModel ()
  {
    // Section 16.1: a content model violation is XTSE0010, not XTSE3430. Not
    // yet checked by the compiler: xsl:sequence mixed with xsl:for-each-group
    final String sStart = "<xsl:stylesheet version='3.0'" + NS + "><xsl:template match='/'>";
    final String sEnd = "</xsl:template></xsl:stylesheet>";
    for (final String sBad : new String [] { "<xsl:fork><xsl:value-of select='a'/></xsl:fork>", "<xsl:fork><foo/></xsl:fork>" })
      assertEquals (sBad, "XTSE0010", _compileError (sStart + sBad + sEnd));
    for (final String sGood : new String [] { "<xsl:fork/>",
                                              "<xsl:fork><xsl:sequence select='1'/></xsl:fork>",
                                              "<xsl:fork><xsl:sequence select='1'/><xsl:sequence select='2'/></xsl:fork>",
                                              "<xsl:fork><xsl:for-each-group select='a' group-by='@k'><xsl:sequence select='.'/></xsl:for-each-group></xsl:fork>" })
      assertNull (sGood, _compileError (sStart + sGood + sEnd));
  }

  @Test
  public void testTypeHelpers ()
  {
    assertTrue (XsltStreamability.isWritingDescendantStep ("//ITEM/TITLE"));
    assertTrue (XsltStreamability.isWritingDescendantStep ("a/descendant::b"));
    assertFalse (XsltStreamability.isWritingDescendantStep ("(a//b | c)"));
    assertFalse (XsltStreamability.isWritingDescendantStep ("a | b//c"));
    assertFalse (XsltStreamability.isWritingDescendantStep ("a[.//b]"));
    assertFalse (XsltStreamability.isWritingDescendantStep ("'//'"));
    assertFalse (XsltStreamability.isWritingDescendantStep (null));
    assertTrue (XsltStreamability.isPermittingNodes (null));
    assertTrue (XsltStreamability.isPermittingManyNodes (null));
    assertNotNull (XsltStreamability.getAdjustUsage (null));
  }
}
