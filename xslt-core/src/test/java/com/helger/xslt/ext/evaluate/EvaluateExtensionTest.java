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
package com.helger.xslt.ext.evaluate;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;

/**
 * Test class for class {@link EvaluateExtension}.
 *
 * @author Philip Helger
 */
public final class EvaluateExtensionTest
{
  @Test
  public void testEvaluate ()
  {
    assertEquals ("7", XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'3 + 4'\"/></xsl:template>", null));
    assertEquals ("b",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:variable name='d'><a><b/></a></xsl:variable>" +
                                       "<xsl:evaluate xpath=\"'name(a/*)'\" context-item='$d'/></xsl:template>",
                                       null));
  }

  @Test
  public void testParams ()
  {
    assertEquals ("5",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'$x + $y'\" with-params='map{xs:QName(\"y\"): 3}'>" +
                                       "<xsl:with-param name='x' select='2'/></xsl:evaluate></xsl:template>",
                                       null));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTDE3160",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'1 +'\"/></xsl:template>" + XsltTestHelper.END, null));
    assertEquals ("XTDE3160",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'current()'\"/></xsl:template>" + XsltTestHelper.END, null));
    assertEquals ("XTTE3210",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'.'\" context-item='1, 2'/></xsl:template>" + XsltTestHelper.END,
                                            null));
    assertEquals ("XPDY0002",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'position()'\"/></xsl:template>" + XsltTestHelper.END, null));
  }

  @Test
  public void testSystemProperty ()
  {
    assertEquals ("yes", XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:value-of select=\"system-property('xsl:supports-dynamic-evaluation')\"/></xsl:template>", null));
  }
}
