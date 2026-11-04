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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * The schema-for-schemas content model of complexType, simpleContent and
 * complexContent, and the position of schema-for-schemas errors (go-xslt
 * <code>content_grammar_test.go</code>, <code>located_test.go</code>). Only the
 * line is compared: a node position here is where the SAX parser reports the
 * end of the start tag, so the column differs from go-xslt's.
 *
 * @author Philip Helger
 */
public final class XsdContentGrammarTest
{
  private static final String HEAD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n";
  private static final String TAIL = "\n</xs:schema>";
  private static final String ERR_ELEMENT_CONTENT = "s4s-elt-invalid-content.1";

  /**
   * @param name
   *        Case name.
   * @param ct
   *        The schema body.
   * @param line
   *        Line of the offending element in the wrapped schema.
   * @param inV10
   *        Also this error under XSD 1.0; else only 1.1 has the element.
   */
  private record Rejected (@NonNull String name, @NonNull String ct, int line, boolean inV10)
  {}

  private static final Rejected [] REJECTED = { new Rejected ("assert next to simpleContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n  <xs:assert test=\". ge 2\"/>\n</xs:complexType>",
                                                              4,
                                                              false),
                                                new Rejected ("attribute next to simpleContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n  <xs:attribute name=\"a\"/>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("openContent next to simpleContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n  <xs:openContent><xs:any/></xs:openContent>\n</xs:complexType>",
                                                              4,
                                                              false),
                                                new Rejected ("openContent after complexContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent>\n  <xs:openContent><xs:any/></xs:openContent>\n</xs:complexType>",
                                                              4,
                                                              false),
                                                new Rejected ("attribute next to complexContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent>\n  <xs:attribute name=\"a\"/>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("anyAttribute next to complexContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent>\n  <xs:anyAttribute/>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("attributeGroup before simpleContent",
                                                              "<xs:attributeGroup name=\"g\"/>\n<xs:complexType name=\"t\">\n  <xs:attributeGroup ref=\"g\"/>\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("particle next to complexContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent>\n  <xs:sequence/>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("two simpleContents",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("simpleContent and complexContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n  <xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("two model groups",
                                                              "<xs:complexType name=\"t\">\n  <xs:sequence/>\n  <xs:choice/>\n</xs:complexType>",
                                                              4,
                                                              true),
                                                new Rejected ("two openContents",
                                                              "<xs:complexType name=\"t\">\n  <xs:openContent><xs:any/></xs:openContent>\n  <xs:openContent><xs:any/></xs:openContent>\n</xs:complexType>",
                                                              4,
                                                              false),
                                                new Rejected ("extension and restriction in simpleContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent>\n    <xs:extension base=\"xs:integer\"/>\n    <xs:restriction base=\"xs:integer\"/>\n  </xs:simpleContent>\n</xs:complexType>",
                                                              5,
                                                              true),
                                                new Rejected ("two extensions in complexContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:complexContent>\n    <xs:extension base=\"xs:anyType\"/>\n    <xs:extension base=\"xs:anyType\"/>\n  </xs:complexContent>\n</xs:complexType>",
                                                              5,
                                                              true),
                                                new Rejected ("empty simpleContent",
                                                              "<xs:complexType name=\"t\">\n  <xs:simpleContent/>\n</xs:complexType>",
                                                              3,
                                                              true),
                                                new Rejected ("anonymous type in an element",
                                                              "<xs:element name=\"n\">\n <xs:complexType>\n  <xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent>\n  <xs:assert test=\". ge 2\"/>\n </xs:complexType>\n</xs:element>",
                                                              5,
                                                              false) };

  @NonNull
  private static XsdInvalidException _compileInvalid (@NonNull final String sSchema, @NonNull final EXsdVersion eVersion)
  {
    try
    {
      XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema, null)), eVersion, null);
    }
    catch (final XsdInvalidException ex)
    {
      return ex;
    }
    catch (final XsdUnsupportedException ex)
    {
      fail ("Unsupported, want a verdict: " + ex.getMessage ());
    }
    fail ("Accepted, want a schema error");
    return null;
  }

  @Test
  public void testComplexTypeContentGrammar ()
  {
    for (final Rejected aCase : REJECTED)
      for (final EXsdVersion eVersion : new EXsdVersion [] { EXsdVersion.V10, EXsdVersion.V11 })
      {
        final XsdInvalidException ex = _compileInvalid (HEAD + aCase.ct () + TAIL, eVersion);
        if (eVersion == EXsdVersion.V10 && !aCase.inV10 ())
        {
          // An element XSD 1.0 does not have: refused by its own check
          continue;
        }
        assertEquals (aCase.name () + " " + eVersion, ERR_ELEMENT_CONTENT, ex.getError ().code ());
        assertEquals (aCase.name () + " " + eVersion, aCase.line (), ex.getError ().line ());
      }
  }

  @Test
  public void testLegalArrangements ()
  {
    // assert and openContent are XSD 1.1 only
    final String [] aAccepted11 = { "<xs:complexType name=\"b\"><xs:simpleContent><xs:extension base=\"xs:integer\"/></xs:simpleContent></xs:complexType><xs:complexType name=\"t\"><xs:annotation/><xs:simpleContent><xs:restriction base=\"b\"><xs:minInclusive value=\"1\"/><xs:assert test=\"true()\"/></xs:restriction></xs:simpleContent></xs:complexType>",
                                    "<xs:complexType name=\"t\"><xs:annotation/><xs:openContent mode=\"interleave\"><xs:any/></xs:openContent><xs:sequence><xs:element name=\"e\" minOccurs=\"0\"/></xs:sequence><xs:attribute name=\"a\"/><xs:anyAttribute/><xs:assert test=\"true()\"/><xs:assert test=\"true()\"/></xs:complexType>" };
    final String [] aAcceptedBoth = { "<xs:complexType name=\"t\"><xs:annotation/><xs:complexContent><xs:extension base=\"xs:anyType\"><xs:sequence/><xs:attribute name=\"a\"/></xs:extension></xs:complexContent></xs:complexType>",
                                      "<xs:complexType name=\"t\"/>",
                                      "<xs:group name=\"g\"><xs:sequence/></xs:group><xs:attributeGroup name=\"ag\"/><xs:complexType name=\"t\"><xs:group ref=\"g\"/><xs:attributeGroup ref=\"ag\"/></xs:complexType>" };
    for (final String s : aAccepted11)
    {
      final Compiled aCompiled = XsdTestSupport.compile (HEAD + s + TAIL, null, EXsdVersion.V11);
      assertEquals (s + ": " + aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    }
    for (final String s : aAcceptedBoth)
      for (final EXsdVersion eVersion : new EXsdVersion [] { EXsdVersion.V10, EXsdVersion.V11 })
      {
        final Compiled aCompiled = XsdTestSupport.compile (HEAD + s + TAIL, null, eVersion);
        assertEquals (s + " " + eVersion + ": " + aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
      }
  }

  @Test
  public void testMisplacedAssertionElements ()
  {
    final String [] aRejected = { "<xs:element name=\"e\"><xs:assert test=\"true()\"/></xs:element>",
                                  "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:int\"><xs:assert test=\"true()\"/></xs:restriction></xs:simpleType>",
                                  "<xs:simpleType name=\"s\"><xs:assert test=\"true()\"/><xs:restriction base=\"xs:int\"/></xs:simpleType>",
                                  "<xs:complexType name=\"t\"><xs:complexContent><xs:restriction base=\"xs:anyType\"><xs:assertion test=\"true()\"/></xs:restriction></xs:complexContent></xs:complexType>",
                                  "<xs:complexType name=\"t\"><xs:simpleContent><xs:extension base=\"xs:int\"><xs:assertion test=\"true()\"/></xs:extension></xs:simpleContent></xs:complexType>",
                                  "<xs:complexType name=\"t\"><xs:assertion test=\"true()\"/></xs:complexType>",
                                  "<xs:element name=\"e\"><xs:assertion test=\"true()\"/></xs:element>" };
    for (final String s : aRejected)
      assertNotNull (s, _compileInvalid (HEAD + s + TAIL, EXsdVersion.V11));
  }

  @Test
  public void testSchemaErrorPosition ()
  {
    final String sSchema = HEAD + "  <xs:element name=\"n\">\n" + "    <xs:complexType foo=\"1\"/>\n" + "  </xs:element>\n</xs:schema>";
    final XsdInvalidException ex = _compileInvalid (sSchema, EXsdVersion.V11);
    assertEquals (3, ex.getError ().line ());
    assertNotEquals (0, ex.getError ().column ());
  }
}
