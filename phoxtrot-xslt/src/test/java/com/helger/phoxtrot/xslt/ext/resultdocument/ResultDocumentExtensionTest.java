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
package com.helger.phoxtrot.xslt.ext.resultdocument;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.compile.FileResourceResolver;

/**
 * Test class for class {@link ResultDocumentExtension}.
 *
 * @author Philip Helger
 */
public final class ResultDocumentExtensionTest
{
  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  private static String _secondary (final String sContent)
  {
    final XsltTransformResult aResult = XsltTestHelper.run (XsltTestHelper.SHEET +
                                                            "<xsl:template name='xsl:initial-template'>" +
                                                            "<main xsl:exclude-result-prefixes='#all'/>" +
                                                            "<xsl:result-document href='a.out' omit-xml-declaration='yes'>" +
                                                            sContent +
                                                            "</xsl:result-document></xsl:template>" +
                                                            XsltTestHelper.END,
                                                            null,
                                                            null);
    return aResult.secondaryResults ().get (0).serialized ();
  }

  @Test
  public void testDetectMethod ()
  {
    // html in any case without namespace, xhtml only for lower-case html
    assertEquals ("<HTML><br></HTML>", _secondary ("<HTML xsl:exclude-result-prefixes='#all'><br/></HTML>"));
    assertEquals ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><br /></html>",
                  _secondary ("<html xmlns='http://www.w3.org/1999/xhtml' xsl:exclude-result-prefixes='#all'><br/></html>"));
    assertEquals ("<HTML xmlns=\"http://www.w3.org/1999/xhtml\"><br/></HTML>",
                  _secondary ("<HTML xmlns='http://www.w3.org/1999/xhtml' xsl:exclude-result-prefixes='#all'><br/></HTML>"));
  }

  private String _withParameterDocument (final String sParamDoc) throws IOException
  {
    final Path aFile = m_aTempFolder.newFile ().toPath ();
    Files.writeString (aFile, sParamDoc, StandardCharsets.UTF_8);
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:template name='xsl:initial-template'>" +
                          "<main xsl:exclude-result-prefixes='#all'/>" +
                          "<xsl:result-document href='a.out' omit-xml-declaration='no' parameter-document='" +
                          aFile.toUri () +
                          "'><a xsl:exclude-result-prefixes='#all'/></xsl:result-document></xsl:template>" +
                          XsltTestHelper.END;
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setResolver (new FileResourceResolver (false));
    return XsltCompiler.compile (sSheet, new XsltCompileOptions ()).transform (aRequest).secondaryResults ().get (0).serialized ();
  }

  @Test
  public void testParameterDocumentWins () throws IOException
  {
    // XSLT 3.0 section 26.1: the parameter document takes precedence over
    // the attributes of the instruction
    assertEquals ("<a/>",
                  _withParameterDocument ("<output:serialization-parameters xmlns:output='http://www.w3.org/2010/xslt-xquery-serialization'>" +
                                          "<output:omit-xml-declaration value='yes'/></output:serialization-parameters>"));
    try
    {
      _withParameterDocument ("<output:parameters xmlns:output='http://www.w3.org/2010/xslt-xquery-serialization'>" +
                              "<output:omit-xml-declaration value='yes'/></output:parameters>");
      throw new IllegalStateException ("No error");
    }
    catch (final XPathException ex)
    {
      assertEquals ("SEPM0017", ex.getCode ());
    }
  }
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
    // An href that is not a URI reference
    assertEquals ("XTDE0030",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'>" +
                                            "<xsl:result-document href='{\"a#b#c\"}'><a/></xsl:result-document>" +
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
