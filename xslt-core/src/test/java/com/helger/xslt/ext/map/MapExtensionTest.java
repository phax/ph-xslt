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
package com.helger.xslt.ext.map;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;

/**
 * Test class for class {@link MapExtension}.
 *
 * @author Philip Helger
 */
public final class MapExtensionTest
{
  @Test
  public void testMap ()
  {
    assertEquals ("2 b",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:variable name='m' as='map(*)'><xsl:map>" +
                                       "<xsl:map-entry key='1' select=\"'a'\"/><xsl:map-entry key='2'>b</xsl:map-entry>" +
                                       "</xsl:map></xsl:variable><xsl:value-of select='map:size($m), $m(2)'" +
                                       " xmlns:map='http://www.w3.org/2005/xpath-functions/map'/></xsl:template>",
                                       null));
  }

  @Test
  public void testNestedMaps ()
  {
    assertEquals ("3",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:variable name='m' as='map(*)'><xsl:map>" +
                                       "<xsl:sequence select='map{1:1, 2:2}'/><xsl:map-entry key='3' select='3'/>" +
                                       "</xsl:map></xsl:variable><xsl:value-of select='count(map:keys($m))'" +
                                       " xmlns:map='http://www.w3.org/2005/xpath-functions/map'/></xsl:template>",
                                       null));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTDE3365",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:map><xsl:map-entry key='1' select='1'/><xsl:map-entry key='1' select='2'/></xsl:map></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    assertEquals ("XTTE3375",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:map><xsl:sequence select='1'/></xsl:map></xsl:template>" + XsltTestHelper.END,
                                            null));
    assertEquals ("XTSE3280",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'><xsl:map-entry key='1' select='1'>x</xsl:map-entry></xsl:template>" + XsltTestHelper.END,
                                            null));
  }
}
