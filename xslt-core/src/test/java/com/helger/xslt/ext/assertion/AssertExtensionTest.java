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
package com.helger.xslt.ext.assertion;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;

/**
 * Test class for class {@link AssertExtension}.
 *
 * @author Philip Helger
 */
public final class AssertExtensionTest
{
  @Test
  public void testAssert ()
  {
    assertEquals ("ok", XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:assert test='1 = 1'/>ok</xsl:template>", null));
    assertEquals ("XTMM9001",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:assert test='1 = 2'>failed</xsl:assert></xsl:template>" + XsltTestHelper.END,
                                            null));
    assertEquals ("ABCD9999",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:assert test='false()' error-code='my:ABCD9999' xmlns:my='http://example.com/my'/></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }

  @Test
  public void testCaught ()
  {
    assertEquals ("msg",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:try><xsl:assert test='false()' select=\"'msg'\"/>" +
                                       "<xsl:catch errors='err:XTMM9001' select='$err:value'" +
                                       " xmlns:err='http://www.w3.org/2005/xqt-errors'/></xsl:try></xsl:template>",
                                       null));
  }
}
