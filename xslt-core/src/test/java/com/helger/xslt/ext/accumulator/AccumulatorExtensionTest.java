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
package com.helger.xslt.ext.accumulator;

import org.junit.Test;

import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test class for class {@link AccumulatorExtension}.
 *
 * @author Philip Helger
 */
public final class AccumulatorExtensionTest
{
  private static final String ACC = "<xsl:accumulator name='n' initial-value='0' as='xs:integer'>" +
                                    "<xsl:accumulator-rule match='b' select='$value + 1'/>" +
                                    "</xsl:accumulator><xsl:mode use-accumulators='#all'/>";

  @Test
  public void testBeforeAfter ()
  {
    assertEquals ("1 2 2 2 2",
                  XsltTestHelper.text (ACC +
                                       "<xsl:template match='/'><xsl:value-of select=\"//b/accumulator-before('n'), //b[2]/accumulator-after('n')," +
                                       " /accumulator-after('n'), /a/accumulator-after('n')\"/></xsl:template>",
                                       "<a><b/><b/></a>"));
  }

  @Test
  public void testPhaseEnd ()
  {
    assertEquals ("0 3",
                  XsltTestHelper.text ("<xsl:accumulator name='c' initial-value='0'>" +
                                       "<xsl:accumulator-rule match='*' phase='end' select='$value + 1'/></xsl:accumulator>" +
                                       "<xsl:mode use-accumulators='c'/>" +
                                       "<xsl:template match='/'><xsl:value-of select=\"/a/accumulator-before('c'), /a/accumulator-after('c')\"/></xsl:template>",
                                       "<a><b/><b/></a>"));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTDE3340",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + ACC + "<xsl:template match='/'><xsl:value-of select=\"accumulator-before('x')\"/></xsl:template>" +
                                            XsltTestHelper.END,
                                            "<a/>"));
    assertEquals ("XTSE3350", XsltTestHelper.errorCode (XsltTestHelper.SHEET + ACC + ACC + XsltTestHelper.END, "<a/>"));
    assertEquals ("XTSE3300",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + ACC + "<xsl:mode name='m' use-accumulators='zz'/>" + XsltTestHelper.END, "<a/>"));
    assertEquals ("XTDE3400",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:accumulator name='r' initial-value='0'>" +
                                            "<xsl:accumulator-rule match='b' select=\"$value + accumulator-after('r')\"/></xsl:accumulator>" +
                                            "<xsl:mode use-accumulators='r'/>" +
                                            "<xsl:template match='/'><xsl:value-of select=\"/accumulator-after('r')\"/></xsl:template>" +
                                            XsltTestHelper.END,
                                            "<a><b/></a>"));
  }

  @Test
  public void testUseAccumulatorsSyntax ()
  {
    assertEquals ("XTSE3300",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + ACC + "<xsl:mode name='m' use-accumulators='n n'/>" + XsltTestHelper.END, "<a/>"));
    assertEquals ("XTSE3300",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + ACC + "<xsl:mode name='m' use-accumulators='#all n'/>" + XsltTestHelper.END, "<a/>"));
    assertEquals ("XTSE3300",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            ACC +
                                            "<xsl:template match='/'><xsl:source-document href='x.xml' use-accumulators='1n'/></xsl:template>" +
                                            XsltTestHelper.END,
                                            "<a/>"));
  }

  @Test
  public void testRuleSelectAndContent ()
  {
    assertEquals ("XTSE0010",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:accumulator name='c' initial-value='0'><xsl:accumulator-rule match='b' select='1'>2</xsl:accumulator-rule></xsl:accumulator>" +
                                            XsltTestHelper.END,
                                            "<a/>"));
  }

  @Test
  public void testNotApplicable ()
  {
    // The initial mode does not list the accumulator for the principal source
    assertEquals ("XTDE3362",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:accumulator name='n' initial-value='0'><xsl:accumulator-rule match='b' select='$value + 1'/></xsl:accumulator>" +
                                            "<xsl:template match='/'><xsl:value-of select=\"accumulator-after('n')\"/></xsl:template>" +
                                            XsltTestHelper.END,
                                            "<a/>"));
  }

  @Test
  public void testDeepTree ()
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < 5000; ++i)
      aSB.append ("<b>");
    for (int i = 0; i < 5000; ++i)
      aSB.append ("</b>");
    assertEquals ("5000", XsltTestHelper.text (ACC + "<xsl:template match='/'><xsl:value-of select=\"accumulator-after('n')\"/></xsl:template>", aSB.toString ()));
  }

  @Test
  public void testCopyWrappersStayInTheStylesheet ()
  {
    // fn:copy-of and fn:snapshot and their wrappers are registered in the stylesheet's library, a child of the
    // shared default library, which must stay without them
    final XdmQName aCopyOf = new XdmQName (XdmConstants.NS_FN, "copy-of");
    final XdmQName aSnapshot = new XdmQName (XdmConstants.NS_FN, "snapshot");
    final int nDefaultCount = FunctionLibrary.getDefault ().getAllFunctions ().size ();
    for (int i = 0; i < 2; ++i)
      assertEquals ("1 1",
                    XsltTestHelper.text (ACC +
                                         "<xsl:template match='/'><xsl:value-of select=\"copy-of(//b[1])/accumulator-after('n'), " +
                                         "snapshot(//b[1])/accumulator-after('n')\"/></xsl:template>",
                                         "<a><b/><b/></a>"));
    for (int i = 0; i <= 1; ++i)
    {
      assertNull (FunctionLibrary.getDefault ().getFunction (aCopyOf, i));
      assertNull (FunctionLibrary.getDefault ().getFunction (aSnapshot, i));
    }
    assertEquals (nDefaultCount, FunctionLibrary.getDefault ().getAllFunctions ().size ());
  }
}
