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
package com.helger.xsd.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;
import org.xml.sax.InputSource;

/**
 * Test class for class {@link XsdCatalogReader}.
 *
 * @author Philip Helger
 */
public final class XsdCatalogReaderTest
{
  private static final Path DIR = Path.of ("/suite/msMeta");

  private static final String TEST_SET = """
      <?xml version="1.0" encoding="UTF-8"?>
      <testSet xmlns="http://www.w3.org/XML/2004/xml-schema-test-suite/"
               xmlns:xlink="http://www.w3.org/1999/xlink" name="sample" version="1.0 1.1">
        <testGroup name="g1">
          <annotation><documentation>ignored</documentation></annotation>
          <schemaTest name="g1s" version="1.0">
            <schemaDocument xlink:href="../msData/a.xsd"/>
            <schemaDocument xlink:href="../msData/b.xsd"/>
            <expected validity="valid"/>
            <expected validity="invalid" version="1.1"/>
            <current status="accepted" date="2006-07-16"/>
            <prior status="accepted" date="2005-01-01"/>
            <current status="queried" date="2008-01-01"/>
          </schemaTest>
          <instanceTest name="g1.i">
            <instanceDocument xlink:href="../msData/a.xml"/>
            <expected validity="invalid"/>
          </instanceTest>
        </testGroup>
        <testGroup name="g2" version="1.1">
          <instanceTest name="g2.v" version="1.1">
            <instanceDocument xlink:href="../msData/g2.xml"/>
            <expected validity="valid"/>
          </instanceTest>
        </testGroup>
      </testSet>
      """;

  @Test
  public void testReadTestSet () throws IOException
  {
    final XsdTestSet aSet = XsdCatalogReader.readTestSet (new InputSource (new StringReader (TEST_SET)), DIR);
    assertEquals ("sample", aSet.name ());
    assertEquals ("1.0 1.1", aSet.version ());
    assertEquals (DIR, aSet.dir ());
    assertEquals (2, aSet.groups ().size ());

    final XsdTestGroup aG1 = aSet.groups ().get (0);
    assertEquals ("g1", aG1.name ());
    assertEquals ("", aG1.version ());
    final XsdSchemaTest aSchemaTest = aG1.schemaTest ();
    assertNotNull (aSchemaTest);
    assertEquals ("g1s", aSchemaTest.name ());
    assertEquals ("1.0", aSchemaTest.version ());
    assertEquals (List.of ("../msData/a.xsd", "../msData/b.xsd"), aSchemaTest.documents ());
    assertEquals (List.of (new XsdExpected ("valid", ""), new XsdExpected ("invalid", "1.1")), aSchemaTest.expected ());
    // prior elements are not read
    assertEquals (List.of ("accepted", "queried"), aSchemaTest.currentStatuses ());
    assertEquals (1, aG1.instanceTests ().size ());
    final XsdInstanceTest aInstance = aG1.instanceTests ().get (0);
    assertEquals ("g1.i", aInstance.name ());
    assertEquals ("../msData/a.xml", aInstance.document ());
    assertEquals (List.of (new XsdExpected ("invalid", "")), aInstance.expected ());
    assertEquals (List.of (), aInstance.currentStatuses ());

    final XsdTestGroup aG2 = aSet.groups ().get (1);
    assertEquals ("1.1", aG2.version ());
    assertNull (aG2.schemaTest ());
    assertEquals ("1.1", aG2.instanceTests ().get (0).version ());
  }
}
