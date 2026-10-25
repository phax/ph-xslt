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
package com.helger.xslt.ext.number;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

/**
 * Test class for {@link NumberExtension}.
 *
 * @author Philip Helger
 */
public final class NumberExtensionTest
{
  private static final String DOC = "<doc><a/><b><c/><c/></b><a/><b><c/><c/><c/></b></doc>";

  private static String _text (final String sBody)
  {
    return XsltTestHelper.text ("<xsl:template name='xsl:initial-template'>" + sBody + "</xsl:template>", null);
  }

  private static String _onDoc (final String sBody)
  {
    return XsltTestHelper.text ("<xsl:template match='/'>" + sBody + "</xsl:template>", DOC);
  }

  @Test
  public void testValue ()
  {
    assertEquals ("42", _text ("<xsl:number value='42'/>"));
    assertEquals ("3", _text ("<xsl:number value='2.5'/>"));
    assertEquals ("1.2.3", _text ("<xsl:number value='1 to 3'/>"));
    assertEquals ("1-b-iii", _text ("<xsl:number value='1 to 3' format='1-a-i'/>"));
    assertEquals ("(007)", _text ("<xsl:number value='7' format='(001)'/>"));
    assertEquals ("1,234,567", _text ("<xsl:number value='1234567' grouping-separator=',' grouping-size='3'/>"));
    assertEquals ("*1*", _text ("<xsl:number value='1' format='*'/>"));
    assertEquals ("1234567890123456789012", _text ("<xsl:number value='1234567890123456789012'/>"));
  }

  @Test
  public void testTokens ()
  {
    assertEquals ("AB", _text ("<xsl:number value='28' format='A'/>"));
    assertEquals ("MCMXCIX", _text ("<xsl:number value='1999' format='I'/>"));
    assertEquals ("twenty one", _text ("<xsl:number value='21' format='w'/>"));
    assertEquals ("Twenty First", _text ("<xsl:number value='21' format='Ww' ordinal='yes'/>"));
    assertEquals ("22nd", _text ("<xsl:number value='22' ordinal='yes'/>"));
    assertEquals ("③", _text ("<xsl:number value='3' format='①'/>"));
    assertEquals ("٣", _text ("<xsl:number value='3' format='١'/>"));
    assertEquals ("γ", _text ("<xsl:number value='3' format='α'/>"));
    assertEquals ("drei", _text ("<xsl:number value='3' format='w' lang='de'/>"));
  }

  @Test
  public void testStartAt ()
  {
    assertEquals ("0.10.11", _text ("<xsl:number value='1 to 3' start-at='0 9'/>"));
  }

  @Test
  public void testLevels ()
  {
    assertEquals ("2", _onDoc ("<xsl:number select='/doc/b[1]/c[2]'/>"));
    assertEquals ("1.2", _onDoc ("<xsl:number select='/doc/b[1]/c[2]' level='multiple' count='b|c'/>"));
    assertEquals ("5", _onDoc ("<xsl:number select='/doc/b[2]/c[3]' level='any'/>"));
    assertEquals ("3", _onDoc ("<xsl:number select='/doc/b[2]/c[3]' level='any' from='b'/>"));
    assertEquals ("4", _onDoc ("<xsl:number select='/doc/b[2]' level='any' count='a|b'/>"));
    assertEquals ("", _onDoc ("<xsl:number select='/doc' count='a'/>"));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTDE0980", XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                                        "<xsl:template name='xsl:initial-template'><xsl:number value='-1'/></xsl:template>" +
                                                        XsltTestHelper.END,
                                                        null));
    assertEquals ("XTSE0975", XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                                        "<xsl:template name='xsl:initial-template'><xsl:number value='1' level='any'/></xsl:template>" +
                                                        XsltTestHelper.END,
                                                        null));
    assertEquals ("XTTE1000", XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                                        "<xsl:template name='xsl:initial-template'><xsl:number select='()'/></xsl:template>" +
                                                        XsltTestHelper.END,
                                                        null));
  }
}
