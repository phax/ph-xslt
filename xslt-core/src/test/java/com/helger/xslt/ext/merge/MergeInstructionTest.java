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
package com.helger.xslt.ext.merge;

import static com.helger.xslt.XsltTestHelper.END;
import static com.helger.xslt.XsltTestHelper.SHEET;
import static com.helger.xslt.XsltTestHelper.errorCode;
import static com.helger.xslt.XsltTestHelper.text;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Test class for class {@link MergeInstruction}.
 *
 * @author Philip Helger
 */
public final class MergeInstructionTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";

  private static String _merge (final String sSources, final String sAction)
  {
    return IT + "<xsl:merge>" + sSources + "<xsl:merge-action>" + sAction + "</xsl:merge-action></xsl:merge>" + TE;
  }

  @Test
  public void testMerge ()
  {
    assertEquals ("1:a|2:a b|3:b|5:a|",
                  text (_merge ("<xsl:merge-source name='a' select='1, 2, 5'><xsl:merge-key select='.'/></xsl:merge-source>" +
                                "<xsl:merge-source name='b' select='2, 3'><xsl:merge-key select='.'/></xsl:merge-source>",
                                "<xsl:value-of select='current-merge-key()'/>:" +
                                "<xsl:value-of select=\"(current-merge-group('a') ! 'a', current-merge-group('b') ! 'b')\"/>|"),
                        null));
  }

  @Test
  public void testSortBeforeMergeAndDescending ()
  {
    assertEquals ("3 3 2 1 ",
                  text (_merge ("<xsl:merge-source select='1, 3, 2' sort-before-merge='yes'><xsl:merge-key select='.' order='descending'/></xsl:merge-source>" +
                                "<xsl:merge-source select='3'><xsl:merge-key select='.' order='descending'/></xsl:merge-source>",
                                "<xsl:value-of select='current-merge-group()'/><xsl:text> </xsl:text>"),
                        null));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTDE2220",
                  errorCode (SHEET + _merge ("<xsl:merge-source select='2, 1'><xsl:merge-key select='.'/></xsl:merge-source>", "") + END, null));
    assertEquals ("XTDE3490",
                  errorCode (SHEET +
                             _merge ("<xsl:merge-source name='a' select='1'><xsl:merge-key select='.'/></xsl:merge-source>",
                                     "<xsl:sequence select=\"current-merge-group('x')\"/>") +
                             END,
                             null));
    assertEquals ("XTDE3480", errorCode (SHEET + IT + "<xsl:sequence select='current-merge-group()'/>" + TE + END, null));
    assertEquals ("XTSE2200",
                  errorCode (SHEET +
                             _merge ("<xsl:merge-source select='1'><xsl:merge-key select='.'/></xsl:merge-source>" +
                                     "<xsl:merge-source select='1'><xsl:merge-key select='.'/><xsl:merge-key select='.'/></xsl:merge-source>",
                                     "") +
                             END,
                             null));
  }

  @Test
  public void testMergeSourceValidationNeedsSchemaAwareness ()
  {
    assertEquals ("XTSE1660",
                  errorCode (SHEET +
                             _merge ("<xsl:merge-source select='1' validation='strict'><xsl:merge-key select='.'/></xsl:merge-source>", "") +
                             END,
                             null));
  }

  @Test
  public void testMergeSourceValidatesForEachSource () throws IOException
  {
    final File aDoc = File.createTempFile ("merge-source", ".xml");
    try
    {
      Files.writeString (aDoc.toPath (), "<order xmlns='urn:my'><qty>99</qty></order>");
      final String sSheet = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                            " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:my='urn:my'>" +
                            "<xsl:output method='text'/>" +
                            "<xsl:import-schema><xs:schema targetNamespace='urn:my' elementFormDefault='qualified'>" +
                            "<xs:element name='order'><xs:complexType><xs:sequence>" +
                            "<xs:element name='qty'><xs:simpleType><xs:restriction base='xs:integer'>" +
                            "<xs:maxInclusive value='20'/></xs:restriction></xs:simpleType></xs:element>" +
                            "</xs:sequence></xs:complexType></xs:element></xs:schema></xsl:import-schema>" +
                            IT +
                            "<xsl:merge><xsl:merge-source for-each-source=\"'" +
                            aDoc.toURI () +
                            "'\" select='.' %s><xsl:merge-key select='1'/></xsl:merge-source>" +
                            "<xsl:merge-action><xsl:value-of select='count(current-merge-group())'/></xsl:merge-action></xsl:merge>" +
                            TE +
                            END;
      final XsltCompileOptions aOptions = new XsltCompileOptions ().setSchemaAware (true);
      // Without validation attributes the document is not validated
      assertEquals ("1", XsltCompiler.compile (sSheet.formatted (""), aOptions).transform (new XsltTransformRequest ().setResolver (FileResourceResolver.INSTANCE)).principalSerialized ());
      try
      {
        XsltCompiler.compile (sSheet.formatted ("validation='strict'"), aOptions).transform (new XsltTransformRequest ().setResolver (FileResourceResolver.INSTANCE));
        fail ();
      }
      catch (final XPathException ex)
      {
        assertEquals ("XTTE1510", ex.getCode ());
      }
    }
    finally
    {
      aDoc.delete ();
    }
  }
}
