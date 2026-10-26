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
package com.helger.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * Input-controlled nesting and cycles never overflow the stack: they end in a
 * verdict or in "unsupported" (go-xslt bounds every recursive walk the same
 * way).
 *
 * @author Philip Helger
 */
public final class XsdLimitsTest
{
  private static final String XS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @Test
  public void testDeeplyNestedParticles ()
  {
    final int nDepth = 3000;
    final StringBuilder aSB = new StringBuilder (XS + "<xs:element name=\"e\"><xs:complexType>");
    for (int i = 0; i < nDepth; i++)
      aSB.append ("<xs:sequence>");
    for (int i = 0; i < nDepth; i++)
      aSB.append ("</xs:sequence>");
    aSB.append ("</xs:complexType></xs:element></xs:schema>");
    final Compiled aCompiled = XsdTestSupport.compile (aSB.toString (), null, EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
    assertTrue (aCompiled.message (), aCompiled.message ().contains ("nested deeper"));
  }

  @Test
  public void testDeeplyNestedLocalElements ()
  {
    final int nDepth = 2000;
    final StringBuilder aSB = new StringBuilder (XS);
    for (int i = 0; i < nDepth; i++)
      aSB.append ("<xs:element name=\"e").append (i).append ("\"><xs:complexType><xs:sequence>");
    for (int i = 0; i < nDepth; i++)
      aSB.append ("</xs:sequence></xs:complexType></xs:element>");
    aSB.append ("</xs:schema>");
    final Compiled aCompiled = XsdTestSupport.compile (aSB.toString (), null, EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
  }

  @Test
  public void testLongSimpleTypeChain ()
  {
    // Each type restricts the next one: a dependency chain longer than the
    // nesting bound
    final int nLength = 3000;
    final StringBuilder aSB = new StringBuilder (XS);
    for (int i = 0; i < nLength; i++)
      aSB.append ("<xs:simpleType name=\"t")
         .append (i)
         .append ("\"><xs:restriction base=\"")
         .append (i + 1 < nLength ? "t" + (i + 1) : "xs:string")
         .append ("\"/></xs:simpleType>");
    aSB.append ("</xs:schema>");
    final Compiled aCompiled = XsdTestSupport.compile (aSB.toString (), null, EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
  }

  @Test
  public void testCycles ()
  {
    // Circular type derivation, group and attribute group references, and
    // substitution groups are schema errors, found without recursion trouble
    for (final String sBody : new String [] { "<xs:complexType name=\"a\"><xs:complexContent><xs:extension base=\"b\"/></xs:complexContent></xs:complexType>" +
                                              "<xs:complexType name=\"b\"><xs:complexContent><xs:extension base=\"a\"/></xs:complexContent></xs:complexType>",
                                              "<xs:group name=\"g\"><xs:sequence><xs:group ref=\"h\"/></xs:sequence></xs:group>" +
                                                                                                                                                 "<xs:group name=\"h\"><xs:sequence><xs:group ref=\"g\"/></xs:sequence></xs:group>",
                                              "<xs:attributeGroup name=\"g\"><xs:attributeGroup ref=\"h\"/></xs:attributeGroup>" +
                                                                                                                                                                                                      "<xs:attributeGroup name=\"h\"><xs:attributeGroup ref=\"g\"/></xs:attributeGroup>",
                                              "<xs:element name=\"a\" substitutionGroup=\"b\"/><xs:element name=\"b\" substitutionGroup=\"a\"/>",
                                              "<xs:simpleType name=\"s\"><xs:restriction base=\"u\"/></xs:simpleType><xs:simpleType name=\"u\"><xs:list itemType=\"s\"/></xs:simpleType>" })
    {
      final Compiled aCompiled = XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, EXsdVersion.V10);
      assertSame (sBody + ": " + aCompiled.message (), EVerdict.INVALID, aCompiled.verdict ());
    }
  }

  /**
   * A union that names itself as a member is a tolerated pure-union cycle in
   * XSD 1.0; walking its members must stay bounded (go-xslt 2168ab5 overflows
   * its stack in checkNotationUsable here).
   */
  @Test
  public void testSelfReferencingUnion ()
  {
    for (final String sBody : new String [] { "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                              "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:simpleType name=\"v\"><xs:restriction base=\"u\"/></xs:simpleType>" })
    {
      final Compiled aCompiled = XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, EXsdVersion.V10);
      assertTrue (sBody + ": " + aCompiled.message (), aCompiled.verdict () != EVerdict.INVALID);
    }
  }

  @Test
  public void testCyclicIncludesTerminate () throws IOException
  {
    final Path aDir = m_aTempFolder.newFolder ().toPath ();
    Files.writeString (aDir.resolve ("a.xsd"),
                       XS + "<xs:include schemaLocation=\"b.xsd\"/><xs:element name=\"a\" type=\"xs:string\"/></xs:schema>");
    Files.writeString (aDir.resolve ("b.xsd"),
                       XS + "<xs:include schemaLocation=\"a.xsd\"/><xs:element name=\"b\" type=\"xs:string\"/></xs:schema>");
    // Not the collection constructor: a Path is itself Iterable
    final ICommonsList <Path> aFiles = new CommonsArrayList <> ();
    aFiles.add (aDir.resolve ("a.xsd"));
    final XsdSchema aSchema = XsdSchema.compileFiles (aFiles, EXsdVersion.V10, null);
    assertEquals (2, aSchema.getView ().getAllGlobalElements ().size ());
  }
}
