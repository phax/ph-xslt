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
package com.helger.xslt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * First end-to-end tests.
 *
 * @author Philip Helger
 */
public final class XsltBasicTest
{
  @Test
  public void testIdentityLike ()
  {
    assertEquals ("hello",
                  XsltTestHelper.text ("<xsl:template match='/'><xsl:value-of select='/a/b'/></xsl:template>",
                                       "<a><b>hello</b></a>"));
  }

  @Test
  public void testInitialTemplate ()
  {
    assertEquals ("3", XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:value-of select='1+2'/></xsl:template>", null));
  }

  @Test
  public void testLre ()
  {
    assertEquals ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><out xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" x=\"1\"><b>t</b></out>",
                  XsltTestHelper.transform (XsltTestHelper.SHEET +
                                            "<xsl:template match='/'><out x='{1}'><xsl:apply-templates/></out></xsl:template>" +
                                            "<xsl:template match='b'><b><xsl:value-of select='.'/></b></xsl:template>" +
                                            XsltTestHelper.END,
                                            "<a><b>t</b></a>",
                                            null));
  }
}
