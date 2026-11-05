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
package com.helger.xslt.fn;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Test class for class {@link XsltFunctions}.
 *
 * @author Philip Helger
 */
public final class XsltFunctionsTest
{
  /** A parentless text node: it has no base URI. */
  private static final String TEXT_VAR = "<xsl:variable name='t' as='text()'><xsl:value-of select=\"'abcd.xml'\"/></xsl:variable>";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @Test
  public void testDocumentRelativeWithoutBase ()
  {
    assertEquals ("XTDE1162",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            TEXT_VAR +
                                            "<xsl:template name='xsl:initial-template'><xsl:sequence select='document($t)'/></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    assertEquals ("XTDE1162",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            TEXT_VAR +
                                            "<xsl:template name='xsl:initial-template'><xsl:sequence select=\"document('x.xml', $t)\"/></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }

  @Test
  public void testDocumentAbsoluteWithoutBase () throws IOException
  {
    final Path aFile = m_aTempFolder.newFile ("abs.xml").toPath ();
    Files.writeString (aFile, "<o>absolute</o>", StandardCharsets.UTF_8);
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:output method='text'/>" +
                          TEXT_VAR +
                          "<xsl:template name='xsl:initial-template'><xsl:value-of select=\"document('" +
                          aFile.toUri () +
                          "', $t)/o\"/></xsl:template>" +
                          XsltTestHelper.END;
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setResolver (new FileResourceResolver (false));
    assertEquals ("absolute", aExec.transform (aRequest).principalSerialized ());
  }

  @Test
  public void testUnparsedEntityContextNotANode ()
  {
    assertEquals ("XPTY0004",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:for-each select='1'><xsl:sequence select=\"unparsed-entity-uri('e')\"/></xsl:for-each></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    assertEquals ("XPTY0004",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><xsl:for-each select='1'><xsl:sequence select=\"unparsed-entity-public-id('e')\"/></xsl:for-each></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
  }
}
