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
package com.helger.xslt.ext.sourcedocument;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

import static org.junit.Assert.assertEquals;

/**
 * Test class for class {@link SourceDocumentExtension}.
 *
 * @author Philip Helger
 */
public final class SourceDocumentExtensionTest
{
  @Test
  public void testMissingDocument ()
  {
    assertEquals ("FODC0002",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:source-document href='file:///does/not/exist.xml'><xsl:value-of select='.'/></xsl:source-document></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }

  @Test
  public void testSchemaAwareAttributes ()
  {
    assertEquals ("XTSE1660",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:source-document href='a.xml' validation='strict'/></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }
}
