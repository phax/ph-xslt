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
package com.helger.xslt.ext.iterate;

import static com.helger.xslt.XsltTestHelper.END;
import static com.helger.xslt.XsltTestHelper.SHEET;
import static com.helger.xslt.XsltTestHelper.errorCode;
import static com.helger.xslt.XsltTestHelper.text;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link IterateInstruction}.
 *
 * @author Philip Helger
 */
public final class IterateInstructionTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";

  @Test
  public void testRunningTotal ()
  {
    assertEquals ("1 3 6 10 total=10",
                  text (IT +
                        "<xsl:iterate select='1 to 4'>" +
                        "<xsl:param name='sum' select='0'/>" +
                        "<xsl:on-completion><xsl:value-of select=\"'total=' || $sum\"/></xsl:on-completion>" +
                        "<xsl:value-of select='$sum + .'/><xsl:text> </xsl:text>" +
                        "<xsl:next-iteration><xsl:with-param name='sum' select='$sum + .'/></xsl:next-iteration>" +
                        "</xsl:iterate>" +
                        TE,
                        null));
  }

  @Test
  public void testBreak ()
  {
    assertEquals ("123stop",
                  text (IT +
                        "<xsl:iterate select='1 to 10'>" +
                        "<xsl:on-completion select=\"'done'\"/>" +
                        "<xsl:choose><xsl:when test='. gt 3'><xsl:break select=\"'stop'\"/></xsl:when>" +
                        "<xsl:otherwise><xsl:value-of select='.'/></xsl:otherwise></xsl:choose>" +
                        "</xsl:iterate>" +
                        TE,
                        null));
  }

  @Test
  public void testNestedAndLong ()
  {
    assertEquals ("100000",
                  text (IT +
                        "<xsl:iterate select='1 to 100000'>" +
                        "<xsl:param name='n' select='0' as='xs:integer'/>" +
                        "<xsl:on-completion select='$n'/>" +
                        "<xsl:iterate select='1 to 2'><xsl:break/></xsl:iterate>" +
                        "<xsl:next-iteration><xsl:with-param name='n' select='$n + 1'/></xsl:next-iteration>" +
                        "</xsl:iterate>" +
                        TE,
                        null));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTTE0590",
                  errorCode (SHEET +
                             IT +
                             "<xsl:iterate select='1'><xsl:param name='n' select='0' as='xs:integer'/>" +
                             "<xsl:next-iteration><xsl:with-param name='n' select='\"x\"'/></xsl:next-iteration></xsl:iterate>" +
                             TE +
                             END,
                             null));
  }
}
