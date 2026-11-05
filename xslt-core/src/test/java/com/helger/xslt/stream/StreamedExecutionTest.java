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
package com.helger.xslt.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmStreamReader;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Test class for the streamed execution ({@link StreamedRun}): every body
 * shape the dispatcher streams must give the output of the buffered
 * execution, and the test checks which of the two ran. Ported from go-xslt's
 * <code>stream_exec_test.go</code> and <code>stream_exec_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class StreamedExecutionTest
{
  /** Serves documents from memory, read in full or streamed. */
  static final class MemoryResolver implements IResourceResolver
  {
    private final ICommonsMap <String, String> m_aDocs = new CommonsHashMap <> ();

    @NonNull
    MemoryResolver add (@NonNull final String sUri, @NonNull final String sXml)
    {
      m_aDocs.put (sUri, sXml);
      return this;
    }

    @Nullable
    public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      final String sXml = m_aDocs.get (sAbsoluteUri);
      if (sXml == null)
        return null;
      try
      {
        final InputSource aIS = new InputSource (new NonBlockingStringReader (sXml));
        aIS.setSystemId (sAbsoluteUri);
        return new XdmParser ().parse (aIS);
      }
      catch (final XdmParseException ex)
      {
        return null;
      }
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return m_aDocs.get (sAbsoluteUri);
    }

    @Override
    @Nullable
    public XdmStreamReader openDocumentStream (@NonNull final String sAbsoluteUri,
                                               @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
    {
      final String sXml = m_aDocs.get (sAbsoluteUri);
      if (sXml == null)
        return null;
      final InputSource aIS = new InputSource (new NonBlockingStringReader (sXml));
      aIS.setSystemId (sAbsoluteUri);
      return XdmStreamReader.open (aIS, false, XdmParser.DEFAULT_MAX_ELEMENT_DEPTH, aStripWhitespace);
    }
  }

  private static final String BASE = "file:/streamed/";

  @NonNull
  private static String _data (final int nRecords)
  {
    final StringBuilder aSB = new StringBuilder ("<?xml version='1.0'?>\n<data id='d'>\n");
    for (int i = 1; i <= nRecords; ++i)
      aSB.append ("  <rec id='r")
         .append (i)
         .append ("'><v>")
         .append (i % 7)
         .append ("</v><pad>xxxxxxxx</pad></rec>\n");
    aSB.append ("</data>\n");
    return aSB.toString ();
  }

  @NonNull
  private static String _sheet (@NonNull final String sDecls, @NonNull final String sBody)
  {
    return XsltTestHelper.SHEET.replace ("<xsl:stylesheet ",
                                         "<xsl:stylesheet exclude-result-prefixes='xs' xml:base='" + BASE + "' ") +
           "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
           sDecls +
           "<xsl:template name='xsl:initial-template'><xsl:source-document href='d.xml' streamable='yes'>" +
           sBody +
           "</xsl:source-document></xsl:template>" +
           XsltTestHelper.END;
  }

  @NonNull
  private static String _run (@NonNull final String sSheet,
                              @NonNull final MemoryResolver aResolver,
                              final boolean bStreaming)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setStreamingClaim (bStreaming);
    return XsltCompiler.compile (sSheet, aOptions)
                       .transform (new XsltTransformRequest ().setResolver (aResolver))
                       .principalSerialized ();
  }

  private static void _assertCase (@NonNull final String sName,
                                   @NonNull final String sDecls,
                                   @NonNull final String sBody,
                                   @Nullable final String sExpected,
                                   final boolean bStreamed)
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", _data (50));
    final String sSheet = _sheet (sDecls, sBody);
    final String sBuffered = _run (sSheet, aResolver, false);
    final long nBefore = StreamedRun.getStreamedRunCount ();
    final String sStreamed = _run (sSheet, aResolver, true);
    final long nRuns = StreamedRun.getStreamedRunCount () - nBefore;
    assertEquals (sName, sBuffered, sStreamed);
    if (sExpected != null)
      assertEquals (sName, sExpected, sStreamed);
    assertEquals (sName + ": streamed", bStreamed ? 1 : 0, nRuns);
  }

  @Test
  public void testMatchesBufferedExecution ()
  {
    final String sRules = "<xsl:mode streamable='yes'/>" +
                          "<xsl:template match='rec'>(<xsl:value-of select='@id'/>=<xsl:value-of select='v'/>)</xsl:template>" +
                          "<xsl:mode name='p' streamable='yes'/>" +
                          "<xsl:template match='rec' mode='p'>[<xsl:value-of select='position()'/>]</xsl:template>";
    _assertCase ("for-each", "", "<xsl:for-each select='/data/rec'><xsl:value-of select='v'/>,</xsl:for-each>", null, true);
    _assertCase ("for-each with position",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:if test='v = 3'>[<xsl:value-of select='position()'/>:<xsl:value-of select='@id'/>]</xsl:if></xsl:for-each>",
                 null,
                 true);
    _assertCase ("wildcard step", "", "<xsl:for-each select='*/rec'><xsl:value-of select='v'/></xsl:for-each>", null, true);
    _assertCase ("relative path", "", "<xsl:for-each select='data/rec'><xsl:value-of select='v'/></xsl:for-each>", null, true);
    _assertCase ("wrapper",
                 "",
                 "<out><xsl:for-each select='/data/rec'><xsl:value-of select='v'/></xsl:for-each></out>",
                 null,
                 true);
    _assertCase ("literal element inside the record",
                 "",
                 "<out><xsl:for-each select='/data/rec'><item id='{@id}' v='{v}'/></xsl:for-each></out>",
                 null,
                 true);
    _assertCase ("climbing to the retained ancestor",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:value-of select='../@id'/>.</xsl:for-each>",
                 null,
                 true);
    _assertCase ("top-level copy-of", "", "<xsl:copy-of select='/data/rec'/>", null, true);
    _assertCase ("apply-templates", sRules, "<xsl:apply-templates select='/data/rec'/>", null, true);
    _assertCase ("apply-templates with position", sRules, "<xsl:apply-templates select='/data/rec' mode='p'/>", null, true);

    // Not guaranteed streamable, or not a shape the dispatcher runs: buffered
    _assertCase ("absolute path in the body",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:value-of select='count(/data/rec)'/></xsl:for-each>",
                 null,
                 false);
    // Two consuming operands (section 19.8.1)
    _assertCase ("copy-of filtered in a record",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:if test='v = 0'><xsl:copy-of select='.'/></xsl:if></xsl:for-each>",
                 null,
                 false);
    _assertCase ("last()",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:if test='position() = last()'>L<xsl:value-of select='@id'/></xsl:if></xsl:for-each>",
                 null,
                 false);
    _assertCase ("preceding-sibling",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:value-of select='count(preceding-sibling::rec)'/>,</xsl:for-each>",
                 null,
                 false);
    _assertCase ("sort", "", "<xsl:for-each select='/data/rec'><xsl:sort select='v'/><xsl:value-of select='v'/></xsl:for-each>", null, false);
    _assertCase ("descendant path", "", "<xsl:for-each select='//rec'><xsl:value-of select='v'/></xsl:for-each>", null, false);
    _assertCase ("positional predicate",
                 "",
                 "<xsl:for-each select='/data/rec[1]'><xsl:value-of select='@id'/></xsl:for-each>",
                 null,
                 false);
    _assertCase ("wrapper attribute reading the document",
                 "",
                 "<out n='{count(*)}'><xsl:for-each select='/data/rec'><xsl:value-of select='v'/></xsl:for-each></out>",
                 null,
                 false);
    _assertCase ("two consumers",
                 "",
                 "<xsl:for-each select='/data/rec'><xsl:value-of select='v'/></xsl:for-each><xsl:for-each select='/data/rec'><xsl:value-of select='@id'/></xsl:for-each>",
                 null,
                 false);
    _assertCase ("apply-templates with a parameter",
                 "<xsl:mode streamable='yes'/><xsl:template match='rec'><xsl:param name='sep'/><xsl:value-of select='v, $sep' separator=''/></xsl:template>",
                 "<xsl:apply-templates select='data/rec'><xsl:with-param name='sep' select=\"'!'\"/></xsl:apply-templates>",
                 null,
                 false);
  }

  @Test
  public void testSpecBodies ()
  {
    final String sDoc = "<data><rec id='r1'><v>1</v></rec><rec id='r2'><v>2</v></rec><rec id='r3'><v>3</v></rec></data>";
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", sDoc);
    final String [] [] aCases = { { "<xsl:for-each select='data/rec'><e><xsl:attribute name='id' select='@id'/><xsl:comment select='@id'/><xsl:processing-instruction name='p' select='@id'/><xsl:value-of select='v'/></e></xsl:for-each>",
                                    "<e id=\"r1\"><!--r1--><?p r1?>1</e><e id=\"r2\"><!--r2--><?p r2?>2</e><e id=\"r3\"><!--r3--><?p r3?>3</e>" },
                                  { "<xsl:for-each select='data/rec'><xsl:element name=\"{'x' || @id}\"><xsl:copy><xsl:value-of select='v'/></xsl:copy></xsl:element></xsl:for-each>",
                                    "<xr1><rec>1</rec></xr1><xr2><rec>2</rec></xr2><xr3><rec>3</rec></xr3>" },
                                  { "<xsl:for-each select='data/rec'><xsl:variable name='id' select='string(@id)'/><xsl:choose><xsl:when test=\"$id = 'r2'\"><xsl:sequence select='string(v)'/></xsl:when><xsl:otherwise>-</xsl:otherwise></xsl:choose></xsl:for-each>",
                                    "-2-" },
                                  { "<xsl:copy-of select='data/rec/v'/>", "<v>1</v><v>2</v><v>3</v>" } };
    for (final String [] aCase : aCases)
    {
      final long nBefore = StreamedRun.getStreamedRunCount ();
      assertEquals (aCase[0], aCase[1], _run (_sheet ("", aCase[0]), aResolver, true));
      assertEquals (aCase[0], 1, StreamedRun.getStreamedRunCount () - nBefore);
    }
  }

  @Test
  public void testRecordsAreStripped ()
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "s.xml",
                                                               "<data>\n <rec> <v>1</v> </rec>\n <rec> <v>2</v> </rec>\n</data>")
                                                          .add (BASE + "p.xml",
                                                                "<data xml:space=\"preserve\">\n <rec> <v>1</v> </rec>\n <rec> <v>2</v> </rec>\n</data>");
    for (final String [] aCase : new String [] [] { { "s.xml", "11" }, { "p.xml", "33" } })
    {
      final String sSheet = XsltTestHelper.SHEET.replace ("<xsl:stylesheet ",
                                         "<xsl:stylesheet exclude-result-prefixes='xs' xml:base='" + BASE + "' ") +
                            "<xsl:output method='text'/><xsl:strip-space elements='*'/>" +
                            "<xsl:template name='xsl:initial-template'><xsl:source-document href='" +
                            aCase[0] +
                            "' streamable='yes'><xsl:for-each select='data/rec'><xsl:value-of select='count(node())'/></xsl:for-each></xsl:source-document></xsl:template>" +
                            XsltTestHelper.END;
      final long nBefore = StreamedRun.getStreamedRunCount ();
      assertEquals (aCase[1], _run (sSheet, aResolver, true));
      assertEquals (1, StreamedRun.getStreamedRunCount () - nBefore);
    }
  }

  @NonNull
  private static String _runInitialMode (@NonNull final String sSheet,
                                         @NonNull final MemoryResolver aResolver,
                                         final boolean bStreaming)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setStreamingClaim (bStreaming);
    return XsltCompiler.compile (sSheet, aOptions)
                       .transform (new XsltTransformRequest ().setResolver (aResolver).setStreamedSourceUri (BASE + "d.xml"))
                       .principalSerialized ();
  }

  @Test
  public void testStreamableInitialMode ()
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", _data (20));
    final String [] [] aCases = { { "<xsl:template match='/'><out><xsl:for-each select='data/rec'><xsl:value-of select='v'/></xsl:for-each></out></xsl:template>",
                                    "1" },
                                  { "<xsl:template match='/'><xsl:apply-templates select='data/rec'/></xsl:template>" +
                                    "<xsl:template match='rec'>[<xsl:value-of select='position(), @id' separator=':'/>]</xsl:template>",
                                    "1" },
                                  { "<xsl:template match='/'><xsl:apply-templates/></xsl:template>" +
                                    "<xsl:template match='data'><xsl:value-of select='count(rec)'/></xsl:template>",
                                    "1" },
                                  // Two consumers: the source is read in full
                                  { "<xsl:template match='/'><xsl:value-of select='count(data/rec)'/>|<xsl:value-of select='data/@id'/></xsl:template>",
                                    "0" },
                                  // No template rule for the document node
                                  { "<xsl:template match='rec'><xsl:value-of select='v'/></xsl:template>", "0" } };
    for (final String [] aCase : aCases)
    {
      final String sSheet = XsltTestHelper.SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet exclude-result-prefixes='xs' ") +
                            "<xsl:output method='xml' omit-xml-declaration='yes'/><xsl:mode streamable='yes'/>" +
                            aCase[0] +
                            XsltTestHelper.END;
      final String sBuffered = _runInitialMode (sSheet, aResolver, false);
      final long nBefore = StreamedRun.getStreamedRunCount ();
      assertEquals (aCase[0], sBuffered, _runInitialMode (sSheet, aResolver, true));
      assertEquals (aCase[0], Long.parseLong (aCase[1]), StreamedRun.getStreamedRunCount () - nBefore);
    }
  }

  @Test
  public void testKeyIsRefused ()
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", _data (5));
    // The key is reached through a stylesheet function, which the analysis
    // does not look into
    final String sSheet = _sheet ("<xsl:key name='k' match='rec' use='@id'/>" +
                                  "<xsl:function name='f:k' xmlns:f='urn:f'><xsl:param name='n' as='node()'/><xsl:sequence select=\"count(key('k', 'r1', root($n)))\"/></xsl:function>",
                                  "<xsl:for-each select='data/rec' xmlns:f='urn:f'><xsl:value-of select='f:k(.)'/></xsl:for-each>");
    try
    {
      _run (sSheet, aResolver, true);
      // Not streamed at all is acceptable too
    }
    catch (final XPathException ex)
    {
      assertEquals (ex.getMessage (), "XTSE3430", ex.getCode ());
    }
  }

  @Test
  public void testCurrentGroupAbsentInCalledTemplate ()
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", _data (6));
    final String sSheet = _sheet ("<xsl:template name='cgk'><xsl:try><xsl:value-of select='current-grouping-key()'/>" +
                                  "<xsl:catch errors='*:XTDE1071'>#absent#</xsl:catch></xsl:try>,</xsl:template>",
                                  "<xsl:fork><xsl:for-each-group select='data/rec' group-by='v mod 2'><xsl:call-template name='cgk'/></xsl:for-each-group></xsl:fork>");
    // XSLT 3.0 section 14.2.2: absent inside a declared-streamable construct
    assertEquals ("#absent#,#absent#,", _run (sSheet, aResolver, true));
    // A processor without the streaming claim keeps the dynamic scope
    assertEquals ("1,0,", _run (sSheet, aResolver, false));
  }

  @Test
  public void testGlobalVariablesWithoutFocus ()
  {
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:output method='text'/><xsl:mode streamable='yes'/>" +
                          "<xsl:variable name='v' select='exists(.)'/>" +
                          "<xsl:template match='/'><xsl:value-of select='$v'/></xsl:template>" +
                          XsltTestHelper.END;
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<a/>"))
                                                                     .setSourceStreamed (true);
    try
    {
      XsltCompiler.compile (sSheet, new XsltCompileOptions ().setStreamingClaim (true)).transform (aRequest);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0002", ex.getCode ());
    }
    // Without the streaming claim the variable has the global context item
    assertEquals ("true", XsltCompiler.compile (sSheet, new XsltCompileOptions ()).transform (aRequest).principalSerialized ());
  }
}
