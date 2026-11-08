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

  @Test
  public void testDefaultMethod ()
  {
    // XSLT 3.0 section 26.1: "html" in any case without a namespace gives the
    // html method, only the lower-case "html" in the XHTML namespace gives
    // xhtml
    final String sIT = XsltTestHelper.SHEET + "<xsl:template name='xsl:initial-template'>";
    final String sEnd = "</xsl:template>" + XsltTestHelper.END;
    assertEquals ("html", XsltTestHelper.run (sIT + "<HTML/>" + sEnd, null, null).principalMethod ());
    assertEquals ("xhtml", XsltTestHelper.run (sIT + "<html xmlns='http://www.w3.org/1999/xhtml'/>" + sEnd, null, null).principalMethod ());
    assertEquals ("xml", XsltTestHelper.run (sIT + "<HTML xmlns='http://www.w3.org/1999/xhtml'/>" + sEnd, null, null).principalMethod ());
  }

  @Test
  public void testDoctypeSystemWithTopLevelText ()
  {
    // Serialization 3.1 section 5: doctype-system or standalone require a
    // well-formed document, without text nodes as children of the root
    final String sHead = XsltTestHelper.SHEET + "<xsl:output doctype-system='a.dtd'/><xsl:template name='xsl:initial-template'>";
    final String sEnd = "</xsl:template>" + XsltTestHelper.END;
    assertEquals ("SEPM0004", XsltTestHelper.errorCode (sHead + "<a/><xsl:text>x</xsl:text>" + sEnd, null));
    assertEquals ("SEPM0004", XsltTestHelper.errorCode (sHead + "<a/><b/>" + sEnd, null));
    XsltTestHelper.transform (sHead + "<a/>" + sEnd, null, null);
  }
}
