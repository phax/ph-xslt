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

import static com.helger.xslt.spec.InstrNumberSpecTest.numSpec;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;

import org.junit.Test;

/**
 * Port of go-xslt's <code>internal/xslt/instr_number_tokens_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrNumberTokensSpecTest
{
  /**
   * XSLT 3.0 §12.4 format tokens beyond the Latin ones (F&amp;O 3.1 §4.6.1:
   * any token in a numbering sequence other than decimal digits, a/A, i/I and
   * w generates that sequence), English numeric ordinals, and grouping with
   * other digit families.
   */
  @Test
  public void numberSpecTokens ()
  {
    specRunAll (numSpec ("Greek alphabetic", "<xsl:number value=\"1, 2, 3\" format=\"&#x3B1;\"/>", "α.β.γ"),
                numSpec ("circled digits", "<xsl:number value=\"3\" format=\"&#x2460;\"/>", "③"),
                // English ordinal suffixes, including the teens.
                numSpec ("numeric ordinals",
                         "<xsl:for-each select=\"1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111\"><xsl:number value=\".\" ordinal=\"yes\"/><xsl:text> </xsl:text></xsl:for-each>",
                         "1st 2nd 3rd 4th 11th 12th 13th 21st 22nd 23rd 101st 111th "),
                numSpec ("ordinal with zero padding", "<xsl:number value=\"2\" format=\"01\" ordinal=\"yes\"/>", "02nd"),
                numSpec ("grouping in another digit family",
                         "<xsl:number value=\"1234\" format=\"&#x660;\" grouping-separator=\",\" grouping-size=\"3\"/>",
                         "١,٢٣٤"),
                numSpec ("roman beyond the classic range", "<xsl:number value=\"0, 4000\" format=\"i\"/>", "0.4000"),
                numSpec ("alphabetic zero", "<xsl:number value=\"0\" format=\"a\"/>", "0"),
                numSpec ("big integer with a padded token",
                         "<xsl:number value=\"xs:integer('123456789012345678901')\" format=\"0000000000000000000000001\"/>",
                         "0000123456789012345678901"),
                numSpec ("big integer with an alphabetic token",
                         "<xsl:number value=\"xs:integer('123456789012345678901')\" format=\"a\"/>",
                         "123456789012345678901"),
                numSpec ("ordinal words in German", "<xsl:number value=\"1\" format=\"w\" ordinal=\"yes\" lang=\"de\"/>", "erste"),
                // The from pattern bounds the search for the counted ancestor.
                ok ("count beyond the from boundary", """
                    <xsl:template match="/">[<xsl:for-each select="//b"><xsl:number count="r" from="a"/></xsl:for-each>]</xsl:template>""",
                    "<r><a><b/></a></r>",
                    "[]"),
                ok ("level any with from on an ancestor", """
                    <xsl:template match="/"><xsl:for-each select="//n"><xsl:number level="any" from="s"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>""",
                    "<r><s><n/><n/></s><s><n/></s></r>",
                    "1 2 1 "),
                ok ("level multiple default count", """
                    <xsl:template match="/"><xsl:for-each select="//a"><xsl:number level="multiple"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>""",
                    "<r><a><a/><a><a/></a></a></r>",
                    "1 1.1 1.2 1.2.1 "));
  }
}
