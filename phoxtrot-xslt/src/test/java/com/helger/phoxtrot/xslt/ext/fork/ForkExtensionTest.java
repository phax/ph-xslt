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
package com.helger.phoxtrot.xslt.ext.fork;

import static com.helger.phoxtrot.xslt.XsltTestHelper.END;
import static com.helger.phoxtrot.xslt.XsltTestHelper.SHEET;
import static com.helger.phoxtrot.xslt.XsltTestHelper.errorCode;
import static com.helger.phoxtrot.xslt.XsltTestHelper.text;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link ForkExtension}.
 *
 * @author Philip Helger
 */
public final class ForkExtensionTest
{
  @Test
  public void testFork ()
  {
    assertEquals ("123",
                  text ("<xsl:template name='xsl:initial-template'><xsl:value-of>" +
                        "<xsl:fork><xsl:sequence select='1'/><xsl:sequence select='2, 3'/></xsl:fork>" +
                        "</xsl:value-of></xsl:template>",
                        null));
  }

  @Test
  public void testContentModel ()
  {
    assertEquals ("XTSE0010",
                  errorCode (SHEET + "<xsl:template name='xsl:initial-template'><xsl:fork><xsl:value-of select='1'/></xsl:fork></xsl:template>" + END,
                             null));
    // xsl:sequence and xsl:for-each-group mixed, or two xsl:for-each-group
    assertEquals ("XTSE0010",
                  errorCode (SHEET +
                             "<xsl:template name='xsl:initial-template'><xsl:fork><xsl:sequence select='1'/>" +
                             "<xsl:for-each-group select='1' group-by='.'><xsl:sequence select='2'/></xsl:for-each-group>" +
                             "</xsl:fork></xsl:template>" +
                             END,
                             null));
    assertEquals ("XTSE0010",
                  errorCode (SHEET +
                             "<xsl:template name='xsl:initial-template'><xsl:fork>" +
                             "<xsl:for-each-group select='1' group-by='.'><xsl:sequence select='1'/></xsl:for-each-group>" +
                             "<xsl:for-each-group select='2' group-by='.'><xsl:sequence select='2'/></xsl:for-each-group>" +
                             "</xsl:fork></xsl:template>" +
                             END,
                             null));
    // xsl:fallback is allowed around both forms
    assertEquals ("12",
                  text ("<xsl:template name='xsl:initial-template'><xsl:value-of>" +
                        "<xsl:fork><xsl:fallback/><xsl:sequence select='1'/><xsl:fallback/><xsl:sequence select='2'/></xsl:fork>" +
                        "</xsl:value-of></xsl:template>",
                        null));
    assertEquals ("1",
                  text ("<xsl:template name='xsl:initial-template'><xsl:value-of>" +
                        "<xsl:fork><xsl:fallback/><xsl:for-each-group select='1' group-by='.'><xsl:sequence select='.'/></xsl:for-each-group><xsl:fallback/></xsl:fork>" +
                        "</xsl:value-of></xsl:template>",
                        null));
  }
}
