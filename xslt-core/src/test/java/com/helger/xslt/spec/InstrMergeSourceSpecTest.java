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

import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt <code>internal/xslt/instr_merge_source_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrMergeSourceSpecTest
{
  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  @NonNull
  private static String _run (@NonNull final String sSheet, @NonNull final String sSrc)
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (XsltTestHelper.parse (sSrc));
    return XsltCompiler.compile (sSheet, new XsltCompileOptions ()).transform (aRequest).principalSerialized ();
  }

  /**
   * Reads one input sequence per document named by @for-each-source (XSLT 3.0
   * §15.3), relative URIs resolving against the xsl:merge-source element's
   * base URI. Within a group, items from different input sequences of one
   * source follow the order of their anchors (§15.6.1).
   */
  @Test
  public void mergeForEachSource () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir, "a.xml", "<log><e t=\"1\">a1</e><e t=\"4\">a4</e></log>");
    specWrite (aDir, "b.xml", "<log><e t=\"2\">b2</e><e t=\"4\">b4</e></log>");
    final String sSheet = InstrMergeSpecTest.mergeSheet ("<xsl:merge>\n" +
                                                         "  <xsl:merge-source name=\"logs\" for-each-source=\"('a.xml', 'b.xml')\" select=\"log/e\"><xsl:merge-key select=\"xs:integer(@t)\"/></xsl:merge-source>\n" +
                                                         "  <xsl:merge-action>[<xsl:value-of select=\"current-merge-key()\"/>:<xsl:value-of select=\"current-merge-group('logs')\"/>]</xsl:merge-action>\n" +
                                                         "</xsl:merge>");
    final XsltExecutable aExec = XsltCompiler.compile (sSheet,
                                                       new XsltCompileOptions ().setBaseUri (aDir.resolve ("m.xsl").toUri ().toString ()));
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (XsltTestHelper.parse ("<x/>"));
    aRequest.setResolver (FileResourceResolver.INSTANCE);
    assertEquals ("[1:a1][2:b2][4:a4 b4]", aExec.transform (aRequest).principalSerialized ());
  }

  /**
   * Merge keys compare under the collation named by @collation: with the UCA
   * at primary strength "a" and "A" are equal keys (F&amp;O 3.1 §5.3.4), so
   * they form one group.
   */
  @Test
  public void mergeCollation ()
  {
    final String sSheet = InstrMergeSpecTest.mergeSheet ("<xsl:merge>\n" +
                                                         "  <xsl:merge-source select=\"('a', 'A', 'b')\"><xsl:merge-key select=\".\" collation=\"http://www.w3.org/2013/collation/UCA?strength=primary\"/></xsl:merge-source>\n" +
                                                         "  <xsl:merge-source select=\"('B')\"><xsl:merge-key select=\".\" collation=\"http://www.w3.org/2013/collation/UCA?strength=primary\"/></xsl:merge-source>\n" +
                                                         "  <xsl:merge-action>[<xsl:value-of select=\"current-merge-group()\"/>]</xsl:merge-action>\n" +
                                                         "</xsl:merge>");
    assertEquals ("[a A][b B]", _run (sSheet, "<x/>"));
  }

  /**
   * With no @data-type, merge keys are compared "using the rules appropriate
   * to their datatype", xs:untypedAtomic cast to xs:string (XSLT 3.0 §13.1.2,
   * applied to merge keys by §15.5), so the untyped @k values "10" and "9" are
   * strings and "10" precedes "9".
   */
  @Test
  public void mergeStringKeysCompareAsStrings ()
  {
    for (final String sKey : new String [] { "@k", "xs:string(@k)" })
    {
      final String sSheet = InstrMergeSpecTest.mergeSheet ("<xsl:merge>\n" +
                                                           "  <xsl:merge-source select=\"r/i\"><xsl:merge-key select=\"" +
                                                           sKey +
                                                           "\"/></xsl:merge-source>\n" +
                                                           "  <xsl:merge-action>[<xsl:value-of select=\"current-merge-key()\"/>]</xsl:merge-action>\n" +
                                                           "</xsl:merge>");
      assertEquals (sKey, "[10][9]", _run (sSheet, "<r><i k=\"10\"/><i k=\"9\"/></r>"));
    }
    // Numeric keys still compare as numbers
    final String sSheet = InstrMergeSpecTest.mergeSheet ("<xsl:merge>\n" +
                                                         "  <xsl:merge-source select=\"r/i\"><xsl:merge-key select=\"xs:integer(@k)\"/></xsl:merge-source>\n" +
                                                         "  <xsl:merge-action>[<xsl:value-of select=\"current-merge-key()\"/>]</xsl:merge-action>\n" +
                                                         "</xsl:merge>");
    assertEquals ("[9][10]", _run (sSheet, "<r><i k=\"9\"/><i k=\"10\"/></r>"));
  }

  /**
   * current-merge-key() returns "the merge key of the first item in the group,
   * after atomization and casting of xs:untypedAtomic values to xs:string"
   * (XSLT 3.0 §15.6.2).
   */
  @Test
  public void mergeCurrentKeyIsAtomized ()
  {
    String sSheet = InstrMergeSpecTest.mergeSheet ("<xsl:merge>\n" +
                                                   "  <xsl:merge-source select=\"r/i\"><xsl:merge-key select=\"@k\"/></xsl:merge-source>\n" +
                                                   "  <xsl:merge-action>[<xsl:value-of select=\"current-merge-key() instance of xs:string, current-merge-key() instance of node()\"/>]</xsl:merge-action>\n" +
                                                   "</xsl:merge>");
    assertEquals ("[true false]", _run (sSheet, "<r><i k=\"a\"/></r>"));
    // An atomic merge item keeps its own type as the key
    sSheet = InstrMergeSpecTest.mergeSheet ("<xsl:merge>\n" +
                                            "  <xsl:merge-source select=\"(1, 2)\"><xsl:merge-key select=\".\"/></xsl:merge-source>\n" +
                                            "  <xsl:merge-action>[<xsl:value-of select=\"current-merge-key() instance of xs:integer\"/>]</xsl:merge-action>\n" +
                                            "</xsl:merge>");
    assertEquals ("[true][true]", _run (sSheet, "<x/>"));
  }
}
