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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * The source document keeps the URI it was read from: <code>document-uri</code>
 * and <code>base-uri</code> report it, relative references resolve against it,
 * and <code>fn:doc</code> of that URI is the source itself (go-xslt
 * <code>sourceuri_test.go</code>).
 *
 * @author Philip Helger
 */
public final class XsltSourceDocumentTest
{
  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  private static final String SHEET = XsltTestHelper.SHEET +
                                      "<xsl:output method='text'/>" +
                                      "<xsl:template match='/'>" +
                                      "<xsl:value-of select='document-uri(/) = base-uri(/)'/>|" +
                                      "<xsl:value-of select='doc(document-uri(/)) is /'/>|" +
                                      "<xsl:value-of select=\"document('other.xml', /)/o\"/>|" +
                                      "<xsl:value-of select=\"doc(resolve-uri('other.xml', base-uri(/)))/o\"/>" +
                                      "</xsl:template>" +
                                      XsltTestHelper.END;

  @Test
  public void testSourceUri () throws IOException, XdmParseException
  {
    final Path aDir = m_aTempFolder.newFolder ("src").toPath ();
    final Path aSource = aDir.resolve ("in.xml");
    Files.writeString (aSource, "<r/>", StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("other.xml"), "<o>beside the source</o>", StandardCharsets.UTF_8);

    final XsltExecutable aExec = XsltCompiler.compile (SHEET, new XsltCompileOptions ());
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (new XdmParser ().parse (aSource));
    aRequest.setResolver (new FileResourceResolver (false));
    assertEquals ("true|true|beside the source|beside the source", aExec.transform (aRequest).principalSerialized ());
  }

  @Test
  public void testNoSourceUri ()
  {
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:output method='text'/>" +
                          "<xsl:template match='/'><xsl:value-of select='empty(document-uri(/))'/></xsl:template>" +
                          XsltTestHelper.END;
    assertEquals ("true", XsltTestHelper.transform (sSheet, "<r/>", null));
  }
}
