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
package com.helger.xslt.ext.trycatch;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;

/**
 * Test class for class {@link TryCatchExtension}.
 *
 * @author Philip Helger
 */
public final class TryCatchExtensionTest
{
  @Test
  public void testCatch ()
  {
    assertEquals ("caught FOAR0001",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:try>partial<xsl:value-of select='1 idiv 0'/>" +
                                       "<xsl:catch>caught <xsl:value-of select='local-name-from-QName($err:code)'" +
                                       " xmlns:err='http://www.w3.org/2005/xqt-errors'/></xsl:catch></xsl:try></xsl:template>",
                                       null));
  }

  @Test
  public void testNoError ()
  {
    assertEquals ("ok", XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:try select=\"'ok'\"><xsl:catch select=\"'bad'\"/></xsl:try></xsl:template>", null));
  }

  @Test
  public void testErrorsAttribute ()
  {
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:output method='text'/><xsl:template name='xsl:initial-template'><xsl:try select='1 idiv 0'>" +
                          "<xsl:catch errors='err:XPTY0004' select=\"'type'\" xmlns:err='http://www.w3.org/2005/xqt-errors'/>" +
                          "<xsl:catch errors='*:FOAR0001' select=\"'div'\"/>" +
                          "</xsl:try></xsl:template>" +
                          XsltTestHelper.END;
    assertEquals ("div", XsltTestHelper.transform (sSheet, null, null));
    // An unprefixed name is in no namespace and does not match
    assertEquals ("FOAR0001",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:try select='1 idiv 0'><xsl:catch errors='FOAR0001' select='0'/></xsl:try></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }

  @Test
  public void testRollbackNo ()
  {
    assertEquals ("XTDE3530",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:try rollback-output='no'>x<xsl:value-of select='1 idiv 0'/><xsl:catch select='0'/></xsl:try></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }
}
