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
import static org.junit.Assert.fail;

import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmDocumentNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xslt.IXsltTraceListener;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTraceTemplate;
import com.helger.phoxtrot.xslt.XsltTransformRequest;

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

  /** Records with a group attribute (four records each) and a start mark. */
  @NonNull
  private static String _groupData (final int nRecords)
  {
    final StringBuilder aSB = new StringBuilder ("<?xml version='1.0'?>\n<data id='d'>\n  <meta n='1'/>\n");
    for (int i = 1; i <= nRecords; ++i)
    {
      aSB.append ("  <rec id='r").append (i).append ("' g='").append (i / 4).append ("'");
      if (i % 5 == 1)
        aSB.append (" s='1'");
      aSB.append ("><v>").append (i % 7).append ("</v></rec>\n");
    }
    aSB.append ("  <meta n='2'/>\n</data>\n");
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
                                   @NonNull final String sDoc,
                                   @NonNull final String sDecls,
                                   @NonNull final String sBody,
                                   @Nullable final String sExpected,
                                   final boolean bStreamed,
                                   @Nullable final String sStreamedKind)
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", sDoc);
    final String sSheet = _sheet (sDecls, sBody);
    final String sBuffered = _run (sSheet, aResolver, false);
    final long nBefore = StreamedRun.getStreamedRunCount ();
    final long nKindBefore = sStreamedKind == null ? 0 : StreamedRun.getStreamedCount (sStreamedKind);
    final String sStreamed = _run (sSheet, aResolver, true);
    final long nRuns = StreamedRun.getStreamedRunCount () - nBefore;
    assertEquals (sName, sBuffered, sStreamed);
    if (sExpected != null)
      assertEquals (sName, sExpected, sStreamed);
    assertEquals (sName + ": streamed", bStreamed ? 1 : 0, nRuns);
    if (sStreamedKind != null)
      assertEquals (sName + ": streamed by " + sStreamedKind, 1, StreamedRun.getStreamedCount (sStreamedKind) - nKindBefore);
  }

  private static void _assertCase (@NonNull final String sName,
                                   @NonNull final String sDecls,
                                   @NonNull final String sBody,
                                   @Nullable final String sExpected,
                                   final boolean bStreamed)
  {
    _assertCase (sName, _data (50), sDecls, sBody, sExpected, bStreamed, null);
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
  public void testIterate ()
  {
    final String sDoc = _data (50);
    _assertCase ("iterate with parameters and xsl:on-completion",
                 sDoc,
                 "",
                 "<xsl:iterate select='data/rec'><xsl:param name='s' select='0'/><xsl:param name='n' select='0'/>" +
                       "<xsl:on-completion><t s='{$s}' n='{$n}'/></xsl:on-completion>" +
                       "<xsl:value-of select='position()'/>," +
                       "<xsl:next-iteration><xsl:with-param name='s' select='$s + xs:integer(v)'/><xsl:with-param name='n' select='$n + 1'/></xsl:next-iteration>" +
                       "</xsl:iterate>",
                 null,
                 true,
                 "iterate");
    _assertCase ("iterate with xsl:break",
                 sDoc,
                 "",
                 "<out><xsl:iterate select='data/rec'><xsl:param name='s' select='0'/>" +
                       "<xsl:on-completion>never</xsl:on-completion>" +
                       "<xsl:choose><xsl:when test='$s gt 30'><xsl:break select='string(@id)'/></xsl:when>" +
                       "<xsl:otherwise><xsl:next-iteration><xsl:with-param name='s' select='$s + xs:integer(v)'/></xsl:next-iteration></xsl:otherwise></xsl:choose>" +
                       "</xsl:iterate></out>",
                 "<out>r12</out>",
                 true,
                 "iterate");
    _assertCase ("iterate without records",
                 sDoc,
                 "",
                 "<xsl:iterate select='data/none'><xsl:param name='s' select='0'/><xsl:on-completion select='$s'/>x</xsl:iterate>",
                 "0",
                 true,
                 "iterate");
  }

  @Test
  public void testGrouping ()
  {
    final String sDoc = _groupData (30);
    final String sBody = "<g n='{position()}' ids='{current-group()/@id}'/>";
    _assertCase ("group-adjacent",
                 sDoc,
                 "",
                 "<xsl:for-each-group select='data/rec' group-adjacent='@g'><g k='{current-grouping-key()}' n='{position()}' ids='{current-group()/@id}'/></xsl:for-each-group>",
                 null,
                 true,
                 "for-each-group");
    _assertCase ("group-starting-with",
                 sDoc,
                 "",
                 "<xsl:for-each-group select='data/rec' group-starting-with='rec[@s]'>" + sBody + "</xsl:for-each-group>",
                 null,
                 true,
                 "for-each-group");
    _assertCase ("group-ending-with",
                 sDoc,
                 "",
                 "<xsl:for-each-group select='data/rec' group-ending-with='*[@s]'>" + sBody + "</xsl:for-each-group>",
                 null,
                 true,
                 "for-each-group");
    _assertCase ("group copied",
                 sDoc,
                 "",
                 "<out><xsl:for-each-group select='data/rec' group-adjacent='@g'><xsl:copy-of select='current-group()'/></xsl:for-each-group></out>",
                 null,
                 true,
                 "for-each-group");
    _assertCase ("group climbing",
                 sDoc,
                 "",
                 "<xsl:for-each-group select='data/rec' group-adjacent='@g'><xsl:value-of select='../@id'/></xsl:for-each-group>",
                 null,
                 true,
                 "for-each-group");
    _assertCase ("group-by inside xsl:fork",
                 sDoc,
                 "",
                 "<xsl:fork><xsl:for-each-group select='data/rec' group-by='@g mod 3'><g k='{current-grouping-key()}' n='{position()}' ids='{current-group()/@id}'/></xsl:for-each-group></xsl:fork>",
                 null,
                 true,
                 "fork");
    // Not streamed: group-by outside xsl:fork, sorted groups
    _assertCase ("group-by",
                 sDoc,
                 "",
                 "<xsl:for-each-group select='data/rec' group-by='@g'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>",
                 null,
                 false,
                 null);
    _assertCase ("sorted groups",
                 sDoc,
                 "",
                 "<xsl:for-each-group select='data/rec' group-adjacent='@g'><xsl:sort select='current-grouping-key()' order='descending'/><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>",
                 null,
                 false,
                 null);
  }

  @Test
  public void testFork ()
  {
    final String sDoc = _groupData (30);
    _assertCase ("fork over one path",
                 sDoc,
                 "",
                 "<out><xsl:fork><xsl:sequence><xsl:for-each select='data/rec'><xsl:value-of select='@id'/></xsl:for-each></xsl:sequence>" +
                       "<xsl:sequence><xsl:iterate select='data/rec'><xsl:param name='s' select='0'/><xsl:on-completion><sum><xsl:value-of select='$s'/></sum></xsl:on-completion>" +
                       "<xsl:next-iteration><xsl:with-param name='s' select='$s + xs:integer(v)'/></xsl:next-iteration></xsl:iterate></xsl:sequence></xsl:fork></out>",
                 null,
                 true,
                 "fork");
    _assertCase ("fork over two paths",
                 sDoc,
                 "",
                 "<out><xsl:fork><xsl:sequence><xsl:copy-of select='data/meta'/></xsl:sequence>" +
                       "<xsl:sequence><xsl:for-each-group select='data/rec' group-starting-with='rec[@s]'><g ids='{current-group()/@id}'/></xsl:for-each-group></xsl:sequence>" +
                       "<xsl:sequence><xsl:iterate select='data/rec'><xsl:if test='@id = \"r3\"'><xsl:break select='string(@id)'/></xsl:if></xsl:iterate></xsl:sequence></xsl:fork></out>",
                 null,
                 true,
                 "fork");
    // Not streamed: a record of one prong contains the records of another
    _assertCase ("fork over nested paths",
                 sDoc,
                 "",
                 "<xsl:fork><xsl:sequence><xsl:copy-of select='data'/></xsl:sequence><xsl:sequence><xsl:for-each select='data/rec'><xsl:value-of select='@id'/></xsl:for-each></xsl:sequence></xsl:fork>",
                 null,
                 false,
                 null);
  }

  @Test
  public void testPredicatesAndWrappers ()
  {
    final String sDoc = _groupData (30);
    _assertCase ("predicate on the record step",
                 sDoc,
                 "",
                 "<xsl:for-each select=\"data/rec[@s or @id = 'r4']\"><xsl:value-of select='position(), @id' separator=':'/>,</xsl:for-each>",
                 null,
                 true,
                 "for-each");
    _assertCase ("predicate on an inner step",
                 sDoc,
                 "",
                 "<xsl:for-each select=\"*[@id = 'd']/rec[exists(@s)]\"><xsl:value-of select='@id'/></xsl:for-each>",
                 null,
                 true,
                 "for-each");
    _assertCase ("predicate rejecting every record",
                 sDoc,
                 "",
                 "<xsl:for-each select=\"*[@id = 'x']/rec\"><xsl:value-of select='@id'/></xsl:for-each>",
                 "",
                 true,
                 "for-each");
    _assertCase ("predicate with a variable",
                 sDoc,
                 "<xsl:variable name='want' select=\"'r7'\"/><xsl:mode streamable='yes'/><xsl:template match='rec'><xsl:value-of select='@id'/></xsl:template>",
                 "<xsl:apply-templates select='data/rec[@id = $want]'/>",
                 null,
                 true,
                 "apply-templates");
    _assertCase ("wrapper with attribute value templates",
                 sDoc,
                 "<xsl:param name='p' select='3'/>",
                 "<out a='{$p * 2}' b='x{name()}y' c='{local-name(.)}'><xsl:for-each select='data/rec'><xsl:value-of select='@id'/></xsl:for-each></out>",
                 null,
                 true,
                 "for-each");
    // Not streamed: a predicate reading the content, a numeric predicate
    _assertCase ("predicate reading the record",
                 sDoc,
                 "",
                 "<xsl:for-each select='data/rec[v = 3]'><xsl:value-of select='@id'/></xsl:for-each>",
                 null,
                 false,
                 null);
    _assertCase ("numeric predicate",
                 sDoc,
                 "<xsl:variable name='i' select='2'/>",
                 "<xsl:for-each select='data/rec[$i]'><xsl:value-of select='@id'/></xsl:for-each>",
                 null,
                 false,
                 null);
  }

  @NonNull
  private static String _mergeSheet (@NonNull final String sSources, @NonNull final String sAction)
  {
    return XsltTestHelper.SHEET.replace ("<xsl:stylesheet ",
                                         "<xsl:stylesheet exclude-result-prefixes='xs' xml:base='" + BASE + "' ") +
           "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
           "<xsl:template name='xsl:initial-template'><out><xsl:merge>" +
           sSources +
           "<xsl:merge-action>" +
           sAction +
           "</xsl:merge-action></xsl:merge></out></xsl:template>" +
           XsltTestHelper.END;
  }

  @Test
  public void testMerge ()
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "a.xml",
                                                               "<log><e t='1' v='a1'/><e t='3' v='a3'/><e t='3' v='a3b'/><e t='7' v='a7'/></log>")
                                                          .add (BASE + "b.xml",
                                                                "<log><e t='2' v='b2'/><e t='3' v='b3'/><x/><e t='9' v='b9'/></log>")
                                                          .add (BASE + "c.xml",
                                                                "<log><e t='0' v='c0'/><e t='7' v='c7'/></log>");
    final String sAction = "<g k='{current-merge-key()}' n='{position()}' v='{current-merge-group()!@v}' a='{count(current-merge-group(\"A\"))}'/>";
    final String [] [] aCases = { { "two streamed sources",
                                    "<xsl:merge-source name='A' for-each-source=\"'a.xml'\" streamable='yes' select='log/e'><xsl:merge-key select='xs:integer(@t)'/></xsl:merge-source>" +
                                                            "<xsl:merge-source name='B' for-each-source=\"'b.xml', 'c.xml'\" streamable='yes' select='log/e'><xsl:merge-key select='xs:integer(@t)'/></xsl:merge-source>",
                                    "1" },
                                  { "a streamed and a buffered source",
                                    "<xsl:merge-source name='A' for-each-source=\"'a.xml'\" streamable='yes' select='log/e'><xsl:merge-key select='xs:integer(@t)'/></xsl:merge-source>" +
                                                                          "<xsl:merge-source name='B' for-each-item=\"doc('b.xml')\" select='log/e'><xsl:merge-key select='xs:integer(@t)'/></xsl:merge-source>",
                                    "1" },
                                  { "a streamed source with a predicate",
                                    "<xsl:merge-source name='A' for-each-source=\"'a.xml', 'c.xml'\" streamable='yes' select=\"log/e[@v != 'a3b']\"><xsl:merge-key select='xs:integer(@t)' order='ascending'/></xsl:merge-source>",
                                    "1" } };
    for (final String [] aCase : aCases)
    {
      final String sSheet = _mergeSheet (aCase[1], sAction);
      final String sBuffered = _run (sSheet, aResolver, false);
      final long nBefore = StreamedRun.getStreamedCount ("merge");
      assertEquals (aCase[0], sBuffered, _run (sSheet, aResolver, true));
      assertEquals (aCase[0], Long.parseLong (aCase[2]), StreamedRun.getStreamedCount ("merge") - nBefore);
    }
    // Not sorted: XTDE2220 while reading
    final String sUnsorted = _mergeSheet ("<xsl:merge-source for-each-source=\"'b.xml'\" streamable='yes' select='log/e'><xsl:merge-key select='string(@v)' order='descending'/></xsl:merge-source>",
                                          "<xsl:value-of select='current-merge-key()'/>");
    try
    {
      _run (sUnsorted, aResolver, true);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTDE2220", ex.getCode ());
    }
    // fn:last in the merge action: buffered
    final String sLast = _mergeSheet ("<xsl:merge-source for-each-source=\"'a.xml'\" streamable='yes' select='log/e'><xsl:merge-key select='xs:integer(@t)'/></xsl:merge-source>",
                                      "<xsl:value-of select='position(), last()' separator='/'/>,");
    final long nBefore = StreamedRun.getStreamedCount ("merge");
    assertEquals ("<out>1/3,2/3,3/3,</out>", _run (sLast, aResolver, true));
    assertEquals (0, StreamedRun.getStreamedCount ("merge") - nBefore);
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
  public void testTraceListener ()
  {
    final MemoryResolver aResolver = new MemoryResolver ().add (BASE + "d.xml", _data (3));
    final String sSheet = XsltTestHelper.SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet exclude-result-prefixes='xs' ") +
                          "<xsl:output method='text'/><xsl:mode streamable='yes'/>" +
                          "<xsl:template match='/'><xsl:apply-templates select='data/rec'/></xsl:template>" +
                          "<xsl:template match='rec'>[<xsl:value-of select='@id'/>]</xsl:template>" +
                          XsltTestHelper.END;
    final ICommonsList <String> aEvents = new CommonsArrayList <> ();
    final IXsltTraceListener aTrace = new IXsltTraceListener ()
    {
      public void onTemplateRuleEnter (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
      {
        final boolean bStreamed = aContextItem instanceof final IXdmNode aNode &&
                                  aNode.getRoot () instanceof final XdmDocumentNode aDoc &&
                                  aDoc.isStreamed ();
        aEvents.add ("+" + aTemplate.matchPattern () + (bStreamed ? " streamed" : ""));
      }

      public void onTemplateRuleLeave (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
      {
        aEvents.add ("-" + aTemplate.matchPattern ());
      }
    };
    final long nBefore = StreamedRun.getStreamedRunCount ();
    final String sResult = XsltCompiler.compile (sSheet, new XsltCompileOptions ().setStreamingClaim (true))
                                       .transform (new XsltTransformRequest ().setResolver (aResolver)
                                                                              .setStreamedSourceUri (BASE + "d.xml")
                                                                              .setTraceListener (aTrace))
                                       .principalSerialized ();
    assertEquals ("[r1][r2][r3]", sResult);
    assertEquals (1, StreamedRun.getStreamedRunCount () - nBefore);
    // The templates applied to the streamed records are reported like others
    assertEquals ("[+/ streamed, +rec streamed, -rec, +rec streamed, -rec, +rec streamed, -rec, -/]", aEvents.toString ());
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
