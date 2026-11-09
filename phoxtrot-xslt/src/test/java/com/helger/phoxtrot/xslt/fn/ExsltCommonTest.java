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
package com.helger.phoxtrot.xslt.fn;

import static org.junit.Assert.assertEquals;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.XsltTestHelper;

/**
 * Test class for class {@link ExsltCommon}.
 *
 * @author Philip Helger
 */
public final class ExsltCommonTest
{
  @NonNull
  private static String _eval (@NonNull final String sBody)
  {
    return XsltTestHelper.text ("<xsl:template name='xsl:initial-template' xmlns:exsl='http://exslt.org/common'>" + sBody + "</xsl:template>",
                                null);
  }

  @Test
  public void testNodeSet ()
  {
    // A temporary tree is already a node: the function returns it
    assertEquals ("4",
                  _eval ("<xsl:variable name='t'><a><b><c><d/></c></b></a></xsl:variable>" +
                         "<xsl:value-of select='count(exsl:node-set($t)//*)'/>"));
    // A string becomes a text node
    assertEquals ("text:abc", _eval ("<xsl:variable name='n' select=\"exsl:node-set('abc')\"/><xsl:value-of select=\"'text:' || $n[self::text()]\"/>"));
    assertEquals ("0", _eval ("<xsl:value-of select='count(exsl:node-set(()))'/>"));
    assertEquals ("true", _eval ("<xsl:value-of select=\"function-available('exsl:node-set') and function-available('exsl:object-type', 1)\"/>"));
    assertEquals ("XPTY0004",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template' xmlns:exsl='http://exslt.org/common'>" +
                                            "<xsl:value-of select='exsl:node-set((1, 2))'/></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }

  @Test
  public void testObjectType ()
  {
    assertEquals ("string number boolean node-set node-set external external",
                  _eval ("<xsl:variable name='t'><a/></xsl:variable>" +
                         "<xsl:value-of select=\"exsl:object-type('fred'), exsl:object-type(93.7), exsl:object-type(true())," +
                         " exsl:object-type($t), exsl:object-type(()), exsl:object-type(current-date()), exsl:object-type((1, 2))\"/>"));
  }
}
