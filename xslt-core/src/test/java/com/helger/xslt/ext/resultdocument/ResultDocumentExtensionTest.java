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
package com.helger.xslt.ext.resultdocument;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformResult;

/**
 * Test class for class {@link ResultDocumentExtension}.
 *
 * @author Philip Helger
 */
public final class ResultDocumentExtensionTest
{
  @Test
  public void testSecondaryResult ()
  {
    final XsltTransformResult aResult = XsltTestHelper.run (XsltTestHelper.SHEET +
                                                            "<xsl:template name='xsl:initial-template'>" +
                                                            "<main xsl:exclude-result-prefixes='#all'/>" +
                                                            "<xsl:result-document href='{\"a\"}.txt' method='text'>" +
                                                            "<x>abc</x></xsl:result-document>" +
                                                            "</xsl:template>" +
                                                            XsltTestHelper.END,
                                                            null,
                                                            null);
    assertEquals (1, aResult.secondaryResults ().size ());
    assertEquals ("a.txt", aResult.secondaryResults ().get (0).href ());
    assertEquals ("abc", aResult.secondaryResults ().get (0).serialized ());
    assertEquals ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><main/>", aResult.principalSerialized ());
  }

  @Test
  public void testPrincipalWithoutHref ()
  {
    assertEquals ("abc",
                  XsltTestHelper.transform (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'>" +
                                            "<xsl:result-document method='text'><x>abc</x></xsl:result-document>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            null,
                                            null));
  }

  @Test
  public void testErrors ()
  {
    // The same URI twice
    assertEquals ("XTDE1490",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'>" +
                                            "<xsl:result-document href='a.xml'><a/></xsl:result-document>" +
                                            "<xsl:result-document href='a.xml'><a/></xsl:result-document>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    // In temporary output state
    assertEquals ("XTDE1480",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'>" +
                                            "<xsl:variable name='v'><xsl:result-document href='a.xml'><a/></xsl:result-document></xsl:variable>" +
                                            "<xsl:sequence select='$v'/>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    // Unknown format
    assertEquals ("XTDE1460",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'>" +
                                            "<xsl:result-document href='a.xml' format='{\"nope\"}'><a/></xsl:result-document>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    // Invalid boolean
    assertEquals ("SEPM0016",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'>" +
                                            "<xsl:result-document href='a.xml' indent='{\"perhaps\"}'><a/></xsl:result-document>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }
}
