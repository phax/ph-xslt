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
package com.helger.phoxtrot.xslt.runtime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.phoxtrot.xslt.XsltTestHelper;

/**
 * Test class for the rule index of a mode (<code>Mode.getNamedRuleIndexes</code>) as used by
 * <code>Engine._findRule</code>: rules with and without names merged in rule order, names
 * compared without prefix, xsl:next-match across both kinds and the multiple-match check.
 *
 * @author Philip Helger
 */
public final class RuleIndexTest
{
  private static final String SOURCE = "<r xmlns:p='urn:p' p:a='1' b='2'><x/><p:y/><z>t</z><w/><!--c--><?pi d?></r>";

  @Test
  public void testRuleSelection ()
  {
    final String sBody = "<xsl:template match='/'><xsl:apply-templates select='//node() | //@*'/></xsl:template>" +
                         "<xsl:template match='r'>R</xsl:template>" +
                         "<xsl:template match='x'>X</xsl:template>" +
                         "<xsl:template match='q:y' xmlns:q='urn:p'>Y</xsl:template>" +
                         "<xsl:template match='@b'>B</xsl:template>" +
                         "<xsl:template match='@*'>@</xsl:template>" +
                         "<xsl:template match='z' priority='-1'>zl</xsl:template>" +
                         "<xsl:template match='*'>*<xsl:next-match/></xsl:template>" +
                         "<xsl:template match='text()'>T</xsl:template>" +
                         "<xsl:template match='comment() | processing-instruction()'>C</xsl:template>";
    assertEquals ("R@BXY*zlT*CC", XsltTestHelper.text (sBody, SOURCE));
  }

  @Test
  public void testRuleWithoutNameWinsByPriority ()
  {
    final String sBody = "<xsl:template match='/'><xsl:apply-templates select='//x | //w'/></xsl:template>" +
                         "<xsl:template match='x'>X</xsl:template>" +
                         "<xsl:template match='w'>W<xsl:next-match/></xsl:template>" +
                         "<xsl:template match='node()' priority='5'>N<xsl:next-match/></xsl:template>";
    assertEquals ("NXNW", XsltTestHelper.text (sBody, SOURCE));
  }

  @Test
  public void testUnionOfNameAndWildcard ()
  {
    // One rule (explicit priority) whose pattern names no fixed set of elements
    final String sBody = "<xsl:template match='/'><xsl:apply-templates select='//x | //w'/></xsl:template>" +
                         "<xsl:template match='x | *' priority='1'>U</xsl:template>" +
                         "<xsl:template match='x' priority='0.5'>X</xsl:template>";
    assertEquals ("UU", XsltTestHelper.text (sBody, SOURCE));
  }

  @Test
  public void testMultipleMatch ()
  {
    final String sHead = XsltTestHelper.SHEET +
                         "<xsl:mode on-multiple-match='fail'/>" +
                         "<xsl:template match='/'><xsl:apply-templates select='//x'/></xsl:template>";
    // Two named rules, and a named and an unnamed rule of the same priority
    assertEquals ("XTDE0540",
                  XsltTestHelper.errorCode (sHead +
                                            "<xsl:template match='x'>1</xsl:template>" +
                                            "<xsl:template match='x'>2</xsl:template>" +
                                            XsltTestHelper.END,
                                            SOURCE));
    assertEquals ("XTDE0540",
                  XsltTestHelper.errorCode (sHead +
                                            "<xsl:template match='x' priority='1'>1</xsl:template>" +
                                            "<xsl:template match='node()' priority='1'>2</xsl:template>" +
                                            XsltTestHelper.END,
                                            SOURCE));
  }
}
