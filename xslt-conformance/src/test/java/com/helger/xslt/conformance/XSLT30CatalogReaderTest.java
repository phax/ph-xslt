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
package com.helger.xslt.conformance;

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

import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link XSLT30CatalogReader}.
 *
 * @author Philip Helger
 */
public final class XSLT30CatalogReaderTest
{
  private static final Path DIR = Path.of ("/tmp/set");

  private static final String TEST_SET = """
      <test-set xmlns="http://www.w3.org/2012/10/xslt-test-catalog"
                xmlns:xs="http://www.w3.org/2001/XMLSchema" name="demo">
        <dependencies>
          <spec value="XSLT20+"/>
        </dependencies>
        <environment name="env-a">
          <source role="." file="a.xml" streaming="true"/>
          <source uri="bib.xml" file="bib.xml" validation="strict"/>
          <source role="."><content><![CDATA[<doc><x/></doc>]]></content></source>
          <package file="lib.xsl" role="secondary" uri="http://lib" package-version="1.0"/>
          <schema uri="http://s" file="s.xsd" role="source-reference"/>
          <collection uri="http://coll">
            <source file="c1.xml"/>
            <source file="c2.xml"/>
          </collection>
          <resource uri="http://r/t.txt" file="t.txt" encoding="iso-8859-1"/>
          <param name="p" static="yes" select="'v'" as="xs:string"/>
        </environment>
        <test-case name="demo-001">
          <dependencies>
            <feature value="streaming" satisfied="false"/>
            <year_component_values value="support negative year"/>
          </dependencies>
          <environment ref="env-a"/>
          <test>
            <stylesheet file="demo-001.xsl"/>
            <stylesheet file="demo-lib.xsl" role="secondary"/>
            <initial-template xmlns:my="http://my" name="my:start">
              <param xmlns:my="http://my" name="my:b" tunnel="yes" select="999"/>
            </initial-template>
            <param name="g" select="12" as="xs:integer"/>
            <output file="out/result.xml" serialize="yes" result-var="r"/>
          </test>
          <result xmlns:h="http://www.w3.org/1999/xhtml">
            <any-of xmlns:q="http://q">
              <assert>/h:html/q:x</assert>
              <error code="XTDE0700"/>
            </any-of>
          </result>
        </test-case>
        <test-case name="demo-002">
          <test>
            <stylesheet file="demo-002.xsl"/>
            <initial-mode name="m" select="//item"/>
            <initial-function name="f:go" xmlns:f="urn:f">
              <param select="1"/>
              <param select="'two'"/>
            </initial-function>
            <context-item select="/doc"/>
            <output file="#absent"/>
          </test>
          <result>
            <assert-xml><![CDATA[<out/>]]></assert-xml>
          </result>
        </test-case>
        <test-case name="demo-003">
          <test>
            <initial-template name="unbound:t"/>
          </test>
          <result/>
        </test-case>
      </test-set>
      """;

  @Test
  public void testTestSet () throws IOException
  {
    final XSLT30TestSet aSet = XSLT30CatalogReader.readTestSet (TEST_SET, DIR);
    assertEquals ("demo", aSet.name ());
    assertNull (aSet.file ());
    assertEquals (DIR, aSet.dir ());
    assertEquals (1, aSet.dependencies ().size ());
    assertEquals (new XSLT30Dependency ("spec", "XSLT20+", ""), aSet.dependencies ().getFirstOrNull ());
    assertEquals (3, aSet.testCases ().size ());

    final XSLT30Environment aEnv = aSet.environments ().get ("env-a");
    assertNotNull (aEnv);
    assertFalse (aEnv.isReference ());
    assertFalse (aEnv.catalogLevel ());
    assertEquals (DIR, aEnv.baseDir ());
    assertEquals (3, aEnv.sources ().size ());
    final XSLT30Environment.Source aSrc0 = aEnv.sources ().get (0);
    assertEquals (".", aSrc0.role ());
    assertEquals ("a.xml", aSrc0.file ());
    assertEquals ("true", aSrc0.streaming ());
    assertNull (aSrc0.content ());
    assertFalse (aSrc0.definesStylesheet ());
    assertEquals ("bib.xml", aEnv.sources ().get (1).uri ());
    assertEquals ("strict", aEnv.sources ().get (1).validation ());
    assertEquals ("<doc><x/></doc>", aEnv.sources ().get (2).content ());
    assertEquals (1, aEnv.packages ().size ());
    assertTrue (aEnv.packages ().getFirstOrNull ().isSecondary ());
    assertEquals ("http://lib", aEnv.packages ().getFirstOrNull ().uri ());
    assertEquals ("1.0", aEnv.packages ().getFirstOrNull ().packageVersion ());
    assertEquals (new XSLT30Environment.Schema ("http://s", "s.xsd", "source-reference"),
                  aEnv.schemas ().getFirstOrNull ());
    assertEquals ("http://coll", aEnv.collections ().getFirstOrNull ().uri ());
    assertEquals (2, aEnv.collections ().getFirstOrNull ().sources ().size ());
    assertEquals ("iso-8859-1", aEnv.resources ().getFirstOrNull ().encoding ());
    final XSLT30Param aEnvParam = aEnv.params ().getFirstOrNull ();
    assertTrue (aEnvParam.isStatic ());
    assertEquals ("xs:string", aEnvParam.as ());
    assertEquals (new XdmQName ("p"), aEnvParam.name ());

    final XSLT30TestCase aCase1 = aSet.testCases ().get (0);
    assertEquals ("demo-001", aCase1.name ());
    assertEquals (2, aCase1.dependencies ().size ());
    assertTrue (aCase1.dependencies ().get (0).isNotSatisfied ());
    assertEquals ("year_component_values", aCase1.dependencies ().get (1).type ());
    assertTrue (aCase1.environment ().isReference ());
    assertEquals ("env-a", aCase1.environment ().ref ());

    final XSLT30Invocation aTest1 = aCase1.test ();
    assertEquals (2, aTest1.stylesheets ().size ());
    assertFalse (aTest1.stylesheets ().get (0).isSecondary ());
    assertTrue (aTest1.stylesheets ().get (1).isSecondary ());
    final XSLT30Invocation.Entry aTemplate = aTest1.initialTemplate ();
    assertNotNull (aTemplate);
    assertEquals (new XdmQName ("http://my", "start"), aTemplate.name ());
    assertFalse (aTemplate.isNameUnresolved ());
    final XSLT30Param aTunnel = aTemplate.params ().getFirstOrNull ();
    assertEquals (new XdmQName ("http://my", "b"), aTunnel.name ());
    assertTrue (aTunnel.tunnel ());
    assertFalse (aTunnel.isStatic ());
    assertEquals ("999", aTunnel.select ());
    assertNull (aTest1.initialMode ());
    assertNull (aTest1.initialFunction ());
    assertEquals ("xs:integer", aTest1.params ().getFirstOrNull ().as ());
    assertEquals (new XSLT30Invocation.Output ("out/result.xml", "yes", "r"), aTest1.output ());

    // Namespaces of the assertion: test-set root, <result> and the wrapper
    final XSLT30Assertion aAnyOf = aCase1.result ();
    assertEquals ("any-of", aAnyOf.kind ());
    assertEquals (2, aAnyOf.children ().size ());
    final XSLT30Assertion aAssert = aAnyOf.children ().get (0);
    assertEquals ("/h:html/q:x", aAssert.value ());
    assertEquals ("http://www.w3.org/1999/xhtml", aAssert.namespaces ().get ("h"));
    assertEquals ("http://q", aAssert.namespaces ().get ("q"));
    assertEquals (XdmConstants.NS_XS, aAssert.namespaces ().get ("xs"));
    // the catalog's default namespace is not an assertion namespace
    assertFalse (aAssert.namespaces ().containsKey (""));
    assertEquals ("XTDE0700", aAnyOf.children ().get (1).getAttribute ("code"));
    assertEquals ("", aAnyOf.children ().get (1).getAttribute ("missing"));

    final XSLT30Invocation aTest2 = aSet.testCases ().get (1).test ();
    assertNull (aSet.testCases ().get (1).environment ());
    assertEquals (new XdmQName ("m"), aTest2.initialMode ().name ());
    assertEquals ("//item", aTest2.initialMode ().select ());
    assertEquals (new XdmQName ("urn:f", "go"), aTest2.initialFunction ().name ());
    assertEquals (2, aTest2.initialFunction ().params ().size ());
    assertNull (aTest2.initialFunction ().params ().get (0).name ());
    assertEquals ("'two'", aTest2.initialFunction ().params ().get (1).select ());
    assertEquals ("/doc", aTest2.contextItemSelect ());
    assertEquals ("#absent", aTest2.output ().file ());
    assertEquals ("<out/>", aSet.testCases ().get (1).result ().value ());

    final XSLT30TestCase aCase3 = aSet.testCases ().get (2);
    assertTrue (aCase3.test ().initialTemplate ().isNameUnresolved ());
    assertTrue (aCase3.test ().stylesheets ().isEmpty ());
    assertNull (aCase3.result ());
  }

  @Test
  public void testCatalog () throws IOException
  {
    final XSLT30Catalog aCatalog = XSLT30CatalogReader.readCatalog (new InputSource (new StringReader ("""
        <catalog xmlns="http://www.w3.org/2012/10/xslt-test-catalog">
          <environment name="shared"><source role="." file="shared.xml"/></environment>
          <test-set name="as" file="tests/attr/as/_as-test-set.xml"/>
          <test-set name="avt" file="tests/attr/avt/_avt-test-set.xml"/>
        </catalog>
        """)), DIR);
    assertEquals (DIR, aCatalog.rootDir ());
    assertEquals (2, aCatalog.testSets ().size ());
    assertEquals (new XSLT30Catalog.TestSetRef ("avt", "tests/attr/avt/_avt-test-set.xml"),
                  aCatalog.testSets ().get (1));
    assertTrue (aCatalog.environments ().get ("shared").catalogLevel ());
  }

  @Test
  public void testResolveName ()
  {
    final ICommonsOrderedMap <String, String> aNS = new CommonsLinkedHashMap <> ();
    aNS.put ("a", "http://a");
    assertEquals (new XdmQName ("local"), XSLT30CatalogReader.resolveName ("local", aNS));
    assertEquals (new XdmQName ("http://a", "x"), XSLT30CatalogReader.resolveName (" a:x ", aNS));
    assertEquals (new XdmQName ("urn:e", "y"), XSLT30CatalogReader.resolveName ("Q{urn:e}y", aNS));
    assertEquals (new XdmQName (XdmConstants.NS_XSL, "initial-template"),
                  XSLT30CatalogReader.resolveName ("xsl:initial-template", aNS));
    assertNull (XSLT30CatalogReader.resolveName ("b:x", aNS));
    assertNull (XSLT30CatalogReader.resolveName ("", aNS));
  }
}
