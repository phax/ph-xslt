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
package com.helger.xpath.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import org.junit.Test;
import org.xml.sax.InputSource;

/**
 * Test class for class {@link QT3CatalogReader}.
 *
 * @author Philip Helger
 */
public final class QT3CatalogReaderTest
{
  private static final Path DIR = Path.of ("/suite/fn");

  private static final String TEST_SET = """
      <?xml version="1.0" encoding="UTF-8"?>
      <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="fn-sample">
        <description>Sample</description>
        <dependency type="spec" value="XP31+ XQ31+"/>
        <environment name="local">
          <namespace prefix="a" uri="http://a"/>
          <namespace prefix="" uri="http://default"/>
          <source role="." file="sample/doc.xml" uri="http://www.w3.org/fots/doc.xml" validation="strict">
            <description>ignored</description>
          </source>
          <source role="$in" file="sample/in.xml"/>
          <resource file="sample/t.txt" uri="http://x/t.txt" encoding="iso-8859-1" media-type="text/plain"/>
          <param name="p" select="1 + 1" as="xs:integer" declared="true"/>
          <param name="test:x" xmlns:test="http://test" select="2"/>
          <static-base-uri uri="http://base/"/>
          <collation uri="http://www.w3.org/2013/collation/UCA" default="true"/>
          <decimal-format name="x:df" xmlns:x="http://x" decimal-separator=","/>
          <collection uri="http://coll">
            <source file="sample/c1.xml"/>
            <source file="sample/c2.xml"/>
          </collection>
          <schema uri="http://schema" file="sample/s.xsd"/>
          <context-item select="'London'"/>
        </environment>
        <test-case name="case-1">
          <description>desc</description>
          <created by="x" on="2020-01-01"/>
          <environment ref="local"/>
          <dependency type="feature" value="staticTyping" satisfied="false"/>
          <test>1 + 1</test>
          <result>
            <any-of>
              <assert-eq>2</assert-eq>
              <error code="XPTY0004"/>
            </any-of>
          </result>
        </test-case>
        <test-case name="case-2">
          <environment>
            <source role="." file="inline.xml"/>
          </environment>
          <test><![CDATA[<a/> < 3]]></test>
          <result>
            <assert-xml ignore-prefixes="true"><![CDATA[<x:a xmlns:x="http://x"/>]]></assert-xml>
          </result>
        </test-case>
        <test-case name="case-3">
          <test file="case3.xq"/>
          <result>
            <assert-string-value normalize-space="true">  a  b </assert-string-value>
          </result>
        </test-case>
        <test-case name="case-4">
          <test>()</test>
          <result/>
        </test-case>
      </test-set>
      """;

  @Test
  public void testReadTestSet () throws IOException
  {
    final QT3TestSet aSet = QT3CatalogReader.readTestSet (TEST_SET.strip (), DIR);
    assertEquals ("fn-sample", aSet.name ());
    assertEquals (DIR, aSet.dir ());
    assertEquals (1, aSet.dependencies ().size ());
    assertEquals (new QT3Dependency ("spec", "XP31+ XQ31+", ""), aSet.dependencies ().getFirstOrNull ());

    final QT3Environment aEnv = aSet.environments ().get ("local");
    assertNotNull (aEnv);
    assertFalse (aEnv.isReference ());
    assertEquals (DIR, aEnv.getBaseDir ());
    assertEquals (2, aEnv.getAllNamespaces ().size ());
    assertEquals (new QT3Environment.Namespace ("", "http://default"), aEnv.getAllNamespaces ().get (1));
    assertEquals (2, aEnv.getAllSources ().size ());
    assertEquals (new QT3Environment.Source (".", "sample/doc.xml", "http://www.w3.org/fots/doc.xml", "strict"),
                  aEnv.getAllSources ().getFirstOrNull ());
    assertEquals ("$in", aEnv.getAllSources ().get (1).role ());
    assertEquals (new QT3Environment.Resource ("sample/t.txt", "http://x/t.txt", "iso-8859-1", "text/plain"),
                  aEnv.getAllResources ().getFirstOrNull ());
    assertEquals (new QT3Environment.Param ("p", "", "1 + 1", "xs:integer", "true"),
                  aEnv.getAllParams ().getFirstOrNull ());
    // The prefix is bound on the param element itself
    assertEquals (new QT3Environment.Param ("test:x", "http://test", "2", "", ""), aEnv.getAllParams ().get (1));
    assertEquals ("http://base/", aEnv.getStaticBaseUri ());
    assertEquals (new QT3Environment.Collation ("http://www.w3.org/2013/collation/UCA", true),
                  aEnv.getAllCollations ().getFirstOrNull ());
    assertEquals (",", aEnv.getAllDecimalFormats ().getFirstOrNull ().attributes ().get ("decimal-separator"));
    assertEquals ("x:df", aEnv.getAllDecimalFormats ().getFirstOrNull ().attributes ().get ("name"));
    assertEquals ("http://coll", aEnv.getAllCollections ().getFirstOrNull ().uri ());
    assertEquals (2, aEnv.getAllCollections ().getFirstOrNull ().sources ().size ());
    assertEquals (new QT3Environment.Schema ("http://schema", "sample/s.xsd"), aEnv.getAllSchemas ().getFirstOrNull ());
    assertEquals ("'London'", aEnv.getContextItemSelect ());

    assertEquals (4, aSet.testCases ().size ());

    final QT3TestCase aCase1 = aSet.testCases ().get (0);
    assertEquals ("case-1", aCase1.name ());
    assertNotNull (aCase1.environment ());
    assertTrue (aCase1.environment ().isReference ());
    assertEquals ("local", aCase1.environment ().getRef ());
    assertEquals (1, aCase1.dependencies ().size ());
    assertFalse (aCase1.dependencies ().getFirstOrNull ().isSatisfied ());
    assertEquals ("1 + 1", aCase1.testExpr ());
    assertEquals ("", aCase1.testFile ());
    final QT3Assertion aAnyOf = aCase1.result ();
    assertNotNull (aAnyOf);
    assertEquals ("any-of", aAnyOf.kind ());
    assertEquals (2, aAnyOf.children ().size ());
    assertEquals ("assert-eq", aAnyOf.children ().get (0).kind ());
    assertEquals ("2", aAnyOf.children ().get (0).value ());
    assertEquals ("error", aAnyOf.children ().get (1).kind ());
    assertEquals ("XPTY0004", aAnyOf.children ().get (1).getAttribute ("code"));

    final QT3TestCase aCase2 = aSet.testCases ().get (1);
    assertNotNull (aCase2.environment ());
    assertFalse (aCase2.environment ().isReference ());
    assertEquals (DIR, aCase2.environment ().getBaseDir ());
    assertEquals ("inline.xml", aCase2.environment ().getAllSources ().getFirstOrNull ().file ());
    assertEquals ("<a/> < 3", aCase2.testExpr ());
    assertEquals ("assert-xml", aCase2.result ().kind ());
    assertEquals ("true", aCase2.result ().getAttribute ("ignore-prefixes"));
    assertEquals ("<x:a xmlns:x=\"http://x\"/>", aCase2.result ().value ());
    assertEquals ("", aCase2.result ().getAttribute ("file"));

    final QT3TestCase aCase3 = aSet.testCases ().get (2);
    assertNull (aCase3.environment ());
    assertEquals ("case3.xq", aCase3.testFile ());
    assertEquals ("  a  b ", aCase3.result ().value ());

    assertNull (aSet.testCases ().get (3).result ());
  }

  @Test
  public void testReadCatalog () throws IOException
  {
    final String sCatalog = """
        <catalog xmlns="http://www.w3.org/2010/09/qt-fots-catalog" test-suite="FOTS" version="3.1">
          <environment name="empty"/>
          <environment name="works">
            <source role="." file="docs/works.xml"/>
          </environment>
          <test-set name="fn-abs" file="fn/abs.xml"/>
          <test-set name="op-add" file="op/add.xml"/>
        </catalog>
        """;
    final Path aRoot = Path.of ("/suite");
    final QT3Catalog aCatalog = QT3CatalogReader.readCatalog (new InputSource (new StringReader (sCatalog)), aRoot);
    assertEquals (aRoot, aCatalog.rootDir ());
    assertEquals (2, aCatalog.environments ().size ());
    assertTrue (aCatalog.environments ().get ("empty").getAllSources ().isEmpty ());
    assertEquals (aRoot, aCatalog.environments ().get ("works").getBaseDir ());
    assertEquals (2, aCatalog.testSets ().size ());
    assertEquals (new QT3Catalog.TestSetRef ("op-add", "op/add.xml"), aCatalog.testSets ().get (1));
  }
}
