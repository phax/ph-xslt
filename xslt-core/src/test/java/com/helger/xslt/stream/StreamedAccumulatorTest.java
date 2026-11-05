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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Test class for accumulators over a streamed document: the values computed
 * while the document is read must equal those of the buffered computation.
 * Ported from go-xslt's <code>stream_acc_test.go</code>.
 *
 * @author Philip Helger
 */
public final class StreamedAccumulatorTest
{
  private static final String BASE = "file:/streamed/";
  private static final String SUM = "<xsl:accumulator name='total' initial-value='0' streamable='yes'>" +
                                    "<xsl:accumulator-rule match='v/text()' select='$value + number(.)'/>" +
                                    "</xsl:accumulator>";
  private static final String DEPTH = "<xsl:accumulator name='depth' initial-value='0' streamable='yes'>" +
                                      "<xsl:accumulator-rule match='rec' phase='start' select='$value + 1'/>" +
                                      "<xsl:accumulator-rule match='rec' phase='end' select='$value - 1'/>" +
                                      "</xsl:accumulator>";
  private static final String RULES = "<xsl:mode streamable='yes'/>" +
                                      "<xsl:template match='rec'>(<xsl:value-of select=\"accumulator-after('total')\"/>)</xsl:template>";

  @NonNull
  private static String _data (final int nRecords)
  {
    final StringBuilder aSB = new StringBuilder ("<data>\n");
    for (int i = 1; i <= nRecords; ++i)
      aSB.append ("  <rec id='r").append (i).append ("'><v>").append (i % 7).append ("</v></rec>\n");
    return aSB.append ("</data>").toString ();
  }

  @NonNull
  private static String _sheet (@NonNull final String sDecls,
                                @NonNull final String sBody,
                                @NonNull final String sStreamable,
                                @NonNull final String sUse)
  {
    return XsltTestHelper.SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet xml:base='" + BASE + "' ") +
           "<xsl:output method='text'/>" +
           sDecls +
           "<xsl:template name='xsl:initial-template'><xsl:source-document href='d.xml' streamable='" +
           sStreamable +
           "' use-accumulators='" +
           sUse +
           "'>" +
           sBody +
           "</xsl:source-document></xsl:template>" +
           XsltTestHelper.END;
  }

  @NonNull
  private static String _run (@NonNull final String sSheet, @NonNull final String sDoc)
  {
    final StreamedExecutionTest.MemoryResolver aResolver = new StreamedExecutionTest.MemoryResolver ().add (BASE + "d.xml",
                                                                                                           sDoc);
    return XsltCompiler.compile (sSheet, new XsltCompileOptions ().setStreamingClaim (true))
                       .transform (new XsltTransformRequest ().setResolver (aResolver))
                       .principalSerialized ();
  }

  private static void _assertCase (@NonNull final String sName,
                                   @NonNull final String sDecls,
                                   @NonNull final String sBody,
                                   @NonNull final String sUse)
  {
    final String sDoc = _data (30);
    final long nBefore = StreamedRun.getStreamedRunCount ();
    final String sStreamed = _run (_sheet (sDecls + RULES, sBody, "yes", sUse), sDoc);
    assertEquals (sName, 1, StreamedRun.getStreamedRunCount () - nBefore);
    final String sBuffered = _run (_sheet (sDecls + RULES, sBody, "no", sUse), sDoc);
    assertEquals (sName, 1, StreamedRun.getStreamedRunCount () - nBefore);
    assertEquals (sName, sBuffered, sStreamed);
    assertFalse (sName, sStreamed.replace (",", "").replace (";", "").isEmpty ());
  }

  @Test
  public void testMatchesBufferedExecution ()
  {
    _assertCase ("accumulator-after per record",
                 SUM,
                 "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-after('total')\"/>,</xsl:for-each>",
                 "total");
    _assertCase ("accumulator-before per record",
                 SUM,
                 "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-before('total')\"/>,</xsl:for-each>",
                 "total");
    _assertCase ("start and end phase",
                 DEPTH,
                 "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-after('depth')\"/>;</xsl:for-each>",
                 "depth");
    _assertCase ("inside an apply-templates rule", SUM, "<xsl:apply-templates select='/data/rec'/>", "total");
    // All applicable accumulators are computed, so a computed name is no
    // obstacle (go-xslt reads only the names it sees and falls back)
    _assertCase ("computed accumulator name",
                 SUM,
                 "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-after(concat('to', 'tal'))\"/>,</xsl:for-each>",
                 "total");
    _assertCase ("declared but unread", SUM, "<xsl:for-each select='/data/rec'><xsl:value-of select='v'/>,</xsl:for-each>", "total");
  }

  @Test
  public void testAccumulatorReadsAnother ()
  {
    // Declared first, reads the value of the later one at the same node
    final String sDecls = "<xsl:accumulator name='dbl' initial-value='0' streamable='yes'>" +
                          "<xsl:accumulator-rule match='rec' select=\"accumulator-before('cnt') * 2\"/>" +
                          "</xsl:accumulator>" +
                          "<xsl:accumulator name='cnt' initial-value='0' streamable='yes'>" +
                          "<xsl:accumulator-rule match='rec' select='$value + 1'/>" +
                          "</xsl:accumulator>";
    _assertCase ("rule reading another accumulator",
                 sDecls,
                 "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-before('dbl')\"/>,</xsl:for-each>",
                 "dbl cnt");
    assertEquals ("2,4,6,", _run (_sheet (sDecls, "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-before('dbl')\"/>,</xsl:for-each>", "yes", "dbl cnt"), _data (3)));
  }

  @Test
  public void testSnapshotKeepsValuesOfDroppedNode ()
  {
    final long nBefore = StreamedRun.getStreamedCount ("iterate");
    _assertCase ("snapshot carried to the next record",
                 SUM,
                 "<xsl:iterate select='/data/rec'><xsl:param name='prev' select='()'/>" +
                       "<xsl:value-of select=\"$prev/accumulator-after('total')\"/>," +
                       "<xsl:next-iteration><xsl:with-param name='prev' select='snapshot(.)'/></xsl:next-iteration></xsl:iterate>",
                 "total");
    assertEquals (1, StreamedRun.getStreamedCount ("iterate") - nBefore);
  }

  @Test
  public void testNonStreamableAccumulatorRefused ()
  {
    final String sDecls = "<xsl:accumulator name='total' initial-value='0'>" +
                          "<xsl:accumulator-rule match='v/text()' select='$value + number(.)'/>" +
                          "</xsl:accumulator>";
    final String sBody = "<xsl:for-each select='/data/rec'><xsl:value-of select=\"accumulator-after('total')\"/>,</xsl:for-each>";
    final long nBefore = StreamedRun.getStreamedRunCount ();
    try
    {
      _run (_sheet (sDecls, sBody, "yes", "total"), _data (5));
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals (ex.getMessage (), "XTDE3362", ex.getCode ());
    }
    assertEquals (0, StreamedRun.getStreamedRunCount () - nBefore);
  }

  @Test
  public void testAccumulatorSeesSkippedSubtrees ()
  {
    final String sDoc = "<data>\n  <meta><tag/><tag/><tag/></meta>\n  <rec id='r1'><v>1</v></rec>\n" +
                        "  <other><tag/><tag/></other>\n  <rec id='r2'><v>2</v></rec>\n</data>";
    final String sDecls = "<xsl:accumulator name='tags' initial-value='0' streamable='yes'>" +
                          "<xsl:accumulator-rule match='tag' select='$value + 1'/>" +
                          "</xsl:accumulator>";
    final String sBody = "<xsl:for-each select='/data/rec'><xsl:value-of select='@id'/>=<xsl:value-of select=\"accumulator-before('tags')\"/>;</xsl:for-each>";
    final long nBefore = StreamedRun.getStreamedRunCount ();
    assertEquals ("r1=3;r2=5;", _run (_sheet (sDecls, sBody, "yes", "tags"), sDoc));
    assertEquals (1, StreamedRun.getStreamedRunCount () - nBefore);
    assertEquals ("r1=3;r2=5;", _run (_sheet (sDecls, sBody, "no", "tags"), sDoc));
  }
}
