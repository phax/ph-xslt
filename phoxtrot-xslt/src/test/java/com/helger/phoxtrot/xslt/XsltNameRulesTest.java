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
package com.helger.phoxtrot.xslt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * <code>castable as xs:NCName</code> and the name checks of xsl:element,
 * xsl:attribute, xsl:processing-instruction and xsl:namespace follow the same
 * XML 1.0 (5th edition) Name production (go-xslt <code>names_test.go</code>).
 *
 * @author Philip Helger
 */
public final class XsltNameRulesTest
{
  private static final String SHEET = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                      " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:err='http://www.w3.org/2005/xqt-errors'>" +
                                      "<xsl:output method='text'/>" +
                                      "<xsl:param name='list' as='xs:string'/>" +
                                      "<xsl:template name='xsl:initial-template'>" +
                                      "<xsl:for-each select=\"tokenize($list, '&#9;')\">" +
                                      "<xsl:variable name='n' select='.'/>" +
                                      "<xsl:text>castable=</xsl:text><xsl:value-of select='$n castable as xs:NCName'/>" +
                                      "<xsl:text> element=</xsl:text><xsl:try><xsl:variable name='e'><xsl:element name='{$n}'/></xsl:variable><xsl:value-of select=\"$e ! 'ok'\"/>" +
                                      "<xsl:catch><xsl:text>fail</xsl:text></xsl:catch></xsl:try>" +
                                      "<xsl:text> attribute=</xsl:text><xsl:try><xsl:variable name='e'><e><xsl:attribute name='{$n}'>v</xsl:attribute></e></xsl:variable><xsl:value-of select=\"$e ! 'ok'\"/>" +
                                      "<xsl:catch><xsl:text>fail</xsl:text></xsl:catch></xsl:try>" +
                                      "<xsl:text> pi=</xsl:text><xsl:try><xsl:variable name='e'><xsl:processing-instruction name='{$n}'>v</xsl:processing-instruction></xsl:variable><xsl:value-of select=\"$e ! 'ok'\"/>" +
                                      "<xsl:catch><xsl:text>fail</xsl:text></xsl:catch></xsl:try>" +
                                      "<xsl:text> namespace=</xsl:text><xsl:try><xsl:variable name='e'><e><xsl:namespace name='{$n}' select=\"'urn:x'\"/></e></xsl:variable><xsl:value-of select=\"$e ! 'ok'\"/>" +
                                      "<xsl:catch><xsl:text>fail</xsl:text></xsl:catch></xsl:try>" +
                                      "<xsl:text>&#10;</xsl:text>" +
                                      "</xsl:for-each>" +
                                      "</xsl:template>" +
                                      "</xsl:stylesheet>";

  @NonNull
  private static String [] _run (@NonNull final String... aNames)
  {
    final String sOut = XsltTestHelper.transform (SHEET, null, Map.of ("list", String.join ("\t", aNames)));
    return sOut.stripTrailing ().split ("\n", -1);
  }

  @Test
  public void testFifthEditionNames ()
  {
    final String [] aNames = { "😀", "৪", "a😀", "𐀀x", "ĳ", "à", "x·" };
    final String [] aLines = _run (aNames);
    assertEquals (aNames.length, aLines.length);
    for (int i = 0; i < aNames.length; ++i)
      assertEquals (aNames[i], "castable=true element=ok attribute=ok pi=ok namespace=ok", aLines[i]);
  }

  @Test
  public void testIllegalNames ()
  {
    final String [] aNames = { "1a", "a￾", "̀a", "-a", "a b", "×", "µ", "a:b:c" };
    final String [] aLines = _run (aNames);
    assertEquals (aNames.length, aLines.length);
    for (int i = 0; i < aNames.length; ++i)
    {
      assertTrue (aNames[i], aLines[i].contains ("castable=false"));
      assertFalse (aNames[i] + ": " + aLines[i], aLines[i].contains ("=ok"));
    }
  }
}
