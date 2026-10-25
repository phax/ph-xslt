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

import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;

/**
 * Test class for class {@link AccumulatorExtension}.
 *
 * @author Philip Helger
 */
public final class AccumulatorExtensionTest
{
  private static final String ACC = "<xsl:accumulator name='n' initial-value='0' as='xs:integer'>" +
                                    "<xsl:accumulator-rule match='b' select='$value + 1'/>" +
                                    "</xsl:accumulator>";

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
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + ACC + "<xsl:mode use-accumulators='zz'/>" + XsltTestHelper.END, "<a/>"));
    assertEquals ("XTDE3400",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:accumulator name='r' initial-value='0'>" +
                                            "<xsl:accumulator-rule match='b' select=\"$value + accumulator-after('r')\"/></xsl:accumulator>" +
                                            "<xsl:template match='/'><xsl:value-of select=\"/accumulator-after('r')\"/></xsl:template>" +
                                            XsltTestHelper.END,
                                            "<a><b/></a>"));
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
}
