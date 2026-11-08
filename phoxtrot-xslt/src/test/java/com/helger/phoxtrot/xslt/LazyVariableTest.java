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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Test class for the lazy evaluation of local xsl:variable declarations, and
 * how it interacts with xsl:try (XSLT 3.0 section 8.3).
 *
 * @author Philip Helger
 */
public final class LazyVariableTest
{
  @NonNull
  private static String _text (@NonNull final String sTemplateBody)
  {
    return XsltTestHelper.text ("<xsl:template name='xsl:initial-template'>" + sTemplateBody + "</xsl:template>", null);
  }

  @NonNull
  private static String _error (@NonNull final String sTemplateBody)
  {
    return XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                     "<xsl:output method='text'/><xsl:template name='xsl:initial-template'>" +
                                     sTemplateBody +
                                     "</xsl:template>" +
                                     XsltTestHelper.END,
                                     null);
  }

  @Test
  public void testUnusedVariableWithErrorRaisesNothing ()
  {
    assertEquals ("ok", _text ("<xsl:variable name='v' select='1 div 0'/><xsl:text>ok</xsl:text>"));
    // Also the required type is checked on use only
    assertEquals ("ok", _text ("<xsl:variable name='v' as='xs:integer' select=\"'x'\"/><xsl:text>ok</xsl:text>"));
    // And a sequence constructor
    assertEquals ("ok", _text ("<xsl:variable name='v'><xsl:value-of select='error()'/></xsl:variable><xsl:text>ok</xsl:text>"));
    // A variable only used on a path not taken
    assertEquals ("no",
                  _text ("<xsl:variable name='v' select='1 div 0'/>" +
                         "<xsl:value-of select=\"if (false()) then $v else 'no'\"/>"));
  }

  @Test
  public void testUsedVariableRaisesItsError ()
  {
    assertEquals ("FOAR0001", _error ("<xsl:variable name='v' select='1 div 0'/><xsl:value-of select='$v'/>"));
    assertEquals ("XTTE0570", _error ("<xsl:variable name='v' as='xs:integer' select=\"'x'\"/><xsl:value-of select='$v'/>"));
  }

  @Test
  public void testUsedTwiceIsEvaluatedOnce ()
  {
    // A temporary tree evaluated twice would give two different document nodes
    assertEquals ("true", _text ("<xsl:variable name='v'><a/></xsl:variable><xsl:value-of select='$v is $v'/>"));
    assertEquals ("true",
                  _text ("<xsl:variable name='v' select='generate-id(parse-xml(\"&lt;a/&gt;\"))'/>" +
                         "<xsl:value-of select='$v = $v'/>"));
    // The value is the one of the declaration context, whatever the context of the use
    assertEquals ("1 1",
                  _text ("<xsl:for-each select='1 to 2'><xsl:variable name='p' select='position()'/>" +
                         "<xsl:if test='. = 1'><xsl:for-each select='(5, 6)[1]'><xsl:value-of select='$p'/></xsl:for-each>" +
                         "<xsl:text> </xsl:text><xsl:value-of select='$p'/></xsl:if></xsl:for-each>"));
  }

  @Test
  public void testTryDoesNotCatchErrorOfOuterVariable ()
  {
    // Section 8.3: errors caught "do not include errors that occur while
    // evaluating references to variables whose declaration and initialization
    // is outside the xsl:try"
    assertEquals ("FOAR0001",
                  _error ("<xsl:variable name='v' select='1 div 0'/>" +
                          "<xsl:try><xsl:value-of select='$v'/><xsl:catch><xsl:text>caught</xsl:text></xsl:catch></xsl:try>"));
    // Also when the reference goes through a variable declared inside
    assertEquals ("FOAR0001",
                  _error ("<xsl:variable name='v' select='1 div 0'/>" +
                          "<xsl:try><xsl:variable name='w' select='$v + 1'/><xsl:value-of select='$w'/>" +
                          "<xsl:catch><xsl:text>caught</xsl:text></xsl:catch></xsl:try>"));
    // An outer xsl:try that contains the declaration does catch it
    assertEquals ("outer FOAR0001",
                  _text ("<xsl:try><xsl:variable name='v' select='1 div 0'/>" +
                         "<xsl:try><xsl:value-of select='$v'/><xsl:catch><xsl:text>inner</xsl:text></xsl:catch></xsl:try>" +
                         "<xsl:catch xmlns:err='http://www.w3.org/2005/xqt-errors'><xsl:value-of select=\"'outer', local-name-from-QName($err:code)\"/></xsl:catch></xsl:try>"));
  }

  @Test
  public void testTryCatchesErrorOfInnerVariable ()
  {
    assertEquals ("caught",
                  _text ("<xsl:try><xsl:variable name='v' select='1 div 0'/><xsl:value-of select='$v'/>" +
                         "<xsl:catch><xsl:text>caught</xsl:text></xsl:catch></xsl:try>"));
    // A variable declared in a function called inside the xsl:try
    assertEquals ("caught",
                  XsltTestHelper.text ("<xsl:function name='f:f' xmlns:f='urn:f'><xsl:variable name='v' select='1 div 0'/>" +
                                       "<xsl:sequence select='$v'/></xsl:function>" +
                                       "<xsl:template name='xsl:initial-template' xmlns:f='urn:f'>" +
                                       "<xsl:try><xsl:value-of select='f:f()'/><xsl:catch><xsl:text>caught</xsl:text></xsl:catch></xsl:try>" +
                                       "</xsl:template>",
                                       null));
  }
}
