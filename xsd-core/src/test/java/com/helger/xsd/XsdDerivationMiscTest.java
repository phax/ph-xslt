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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xsd.XsdTestSupport.Case;
import com.helger.xsd.XsdTestSupport.DocCheck;
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Assorted derivation rules: open content in derivations (1.1 sections 3.4.6.2
 * and 3.4.6.3), attribute wildcard intersection and union, block on
 * elements/types against xsi:type, year zero, and typed values of list
 * attributes inside assertions. Ported from go-xslt
 * <code>derivation_misc_test.go</code>. Most cases need the XSD 1.1 features
 * (phase V8) or the instance validator (phase V5) and are counted as undecided
 * until those land.
 *
 * @author Philip Helger
 */
public final class XsdDerivationMiscTest
{
  private static final int VER_BOTH = XsdTestSupport.VER_BOTH;
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;
  private static final String XSI_DECL = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

  private static final Case [] CASES = {
    new Case ("open-restriction-narrows",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:openContent mode=\"interleave\"><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\"><xs:openContent mode=\"suffix\"><xs:any namespace=\"urn:x\" processContents=\"strict\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>",
      true),
    new Case ("open-restriction-loosens-mode",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:openContent mode=\"suffix\"><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\"><xs:openContent mode=\"interleave\"><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>",
      false),
    new Case ("open-restriction-widens-wildcard",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:openContent><xs:any namespace=\"urn:x\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\"><xs:openContent><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>",
      false),
    new Case ("open-restriction-over-closed-base",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\"><xs:openContent><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>",
      false),
    new Case ("open-restriction-removed",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:openContent><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\"><xs:openContent mode=\"none\"/><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"d\" type=\"D\"/><xs:element name=\"b\" type=\"B\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<d><a/></d>", true),
      new DocCheck ("<d xmlns:p=\"urn:p\"><a/><p:x/></d>", false),
      new DocCheck ("<b xmlns:p=\"urn:p\"><p:x/><a/></b>", true)),
    new Case ("open-extension-narrows-mode",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:openContent mode=\"interleave\"><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"B\"><xs:openContent mode=\"suffix\"><xs:any namespace=\"##other\" processContents=\"lax\"/></xs:openContent><xs:sequence><xs:element name=\"b\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>",
      false),
    new Case ("open-extension-inherits",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:openContent mode=\"interleave\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:openContent><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"B\"><xs:sequence><xs:element name=\"b\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"d\" type=\"D\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<d xmlns:p=\"urn:p\"><a/><p:x/><b/></d>", true),
      new DocCheck ("<d><a/><c/><b/></d>", false)),
    new Case ("open-extension-over-closed",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"B\"><xs:openContent mode=\"suffix\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:openContent><xs:sequence><xs:element name=\"b\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"d\" type=\"D\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<d xmlns:p=\"urn:p\"><a/><b/><p:x/></d>", true)),
    new Case ("attr-wildcard-intersection",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:attributeGroup name=\"g\"><xs:anyAttribute namespace=\"urn:a urn:b ##local\" processContents=\"skip\"/></xs:attributeGroup>\n" +
"\t\t  <xs:element name=\"e\"><xs:complexType><xs:attributeGroup ref=\"g\"/><xs:anyAttribute namespace=\"##other\" processContents=\"skip\"/></xs:complexType></xs:element>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e xmlns:a=\"urn:a\" a:x=\"1\"/>", true),
      new DocCheck ("<e x=\"1\"/>", false),
      new DocCheck ("<e xmlns:c=\"urn:c\" c:x=\"1\"/>", false)),
    new Case ("attr-wildcard-intersection-groups",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:attributeGroup name=\"g1\"><xs:anyAttribute namespace=\"urn:a urn:b\" processContents=\"skip\"/></xs:attributeGroup>\n" +
"\t\t  <xs:attributeGroup name=\"g2\"><xs:anyAttribute namespace=\"urn:b urn:c\" processContents=\"skip\"/></xs:attributeGroup>\n" +
"\t\t  <xs:element name=\"e\"><xs:complexType><xs:attributeGroup ref=\"g1\"/><xs:attributeGroup ref=\"g2\"/></xs:complexType></xs:element>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e xmlns:b=\"urn:b\" b:x=\"1\"/>", true),
      new DocCheck ("<e xmlns:a=\"urn:a\" a:x=\"1\"/>", false)),
    new Case ("attr-wildcard-intersection-any",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:attributeGroup name=\"g\"><xs:anyAttribute processContents=\"skip\"/></xs:attributeGroup>\n" +
"\t\t  <xs:element name=\"e\"><xs:complexType><xs:attributeGroup ref=\"g\"/><xs:anyAttribute namespace=\"urn:a\" processContents=\"skip\"/></xs:complexType></xs:element>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e xmlns:a=\"urn:a\" a:x=\"1\"/>", true),
      new DocCheck ("<e xmlns:b=\"urn:b\" b:x=\"1\"/>", false)),
    new Case ("attr-wildcard-union-extension",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:anyAttribute namespace=\"urn:a\" processContents=\"skip\"/></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"B\"><xs:anyAttribute namespace=\"urn:b\" processContents=\"skip\"/></xs:extension></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"d\" type=\"D\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<d xmlns:a=\"urn:a\" a:x=\"1\"/>", true),
      new DocCheck ("<d xmlns:b=\"urn:b\" b:x=\"1\"/>", true),
      new DocCheck ("<d xmlns:c=\"urn:c\" c:x=\"1\"/>", false)),
    new Case ("attr-wildcard-union-inexpressible-1.0",
      VER_10,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:t\" xmlns:t=\"urn:t\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:anyAttribute namespace=\"##other\" processContents=\"skip\"/></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"t:B\"><xs:anyAttribute namespace=\"##local\" processContents=\"skip\"/></xs:extension></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>",
      false),
    new Case ("attr-wildcard-union-expressible-1.1",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:t\" xmlns:t=\"urn:t\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:anyAttribute namespace=\"##other\" processContents=\"skip\"/></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"t:B\"><xs:anyAttribute namespace=\"##local\" processContents=\"skip\"/></xs:extension></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"d\" type=\"t:D\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<t:d xmlns:t=\"urn:t\" x=\"1\"/>", true),
      new DocCheck ("<t:d xmlns:t=\"urn:t\" t:x=\"1\"/>", false)),
    new Case ("attr-prohibited-fixed-1.1",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"c\"><xs:attribute name=\"a\" use=\"prohibited\" fixed=\"x\"/></xs:complexType></xs:schema>",
      false),
    new Case ("block-restriction-on-element",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"R\"><xs:complexContent><xs:restriction base=\"B\"><xs:sequence/></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"X\"><xs:complexContent><xs:extension base=\"B\"/></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"XR\"><xs:complexContent><xs:restriction base=\"X\"><xs:sequence/></xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"e\" type=\"B\" block=\"restriction\"/>\n" +
"\t\t  <xs:element name=\"f\" type=\"B\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"R\"/>", false),
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"X\"/>", true),
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"XR\"/>", false),
      new DocCheck ("<f" + XSI_DECL + " xsi:type=\"XR\"/>", true)),
    new Case ("block-on-type-all",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\" block=\"#all\"><xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"X\"><xs:complexContent><xs:extension base=\"B\"/></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"e\" type=\"B\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"X\"/>", false),
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"B\"/>", true)),
    new Case ("block-default",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" blockDefault=\"extension\">\n" +
"\t\t  <xs:complexType name=\"B\" block=\"\"><xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/></xs:sequence></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"X\"><xs:complexContent><xs:extension base=\"B\"/></xs:complexContent></xs:complexType>\n" +
"\t\t  <xs:element name=\"e\" type=\"B\"/>\n" +
"\t\t  <xs:element name=\"g\" type=\"B\" block=\"\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"X\"/>", false),
      new DocCheck ("<g" + XSI_DECL + " xsi:type=\"X\"/>", true)),
    new Case ("block-simple-restriction",
      VER_BOTH,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:t=\"urn:none\">\n" +
"\t\t  <xs:simpleType name=\"S\"><xs:restriction base=\"xs:int\"><xs:maxInclusive value=\"5\"/></xs:restriction></xs:simpleType>\n" +
"\t\t  <xs:element name=\"e\" type=\"xs:int\" block=\"restriction\"/>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e" + XSI_DECL + " xsi:type=\"S\">3</e>", false)),
    new Case ("year-zero-1.0",
      VER_10,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"d\" type=\"xs:date\"/><xs:element name=\"y\" type=\"xs:gYear\"/><xs:element name=\"t\" type=\"xs:dateTime\"/></xs:schema>",
      true,
      new DocCheck ("<d>0000-01-01</d>", false),
      new DocCheck ("<y>0000</y>", false),
      new DocCheck ("<t>0000-01-01T00:00:00</t>", false),
      new DocCheck ("<d>-0001-01-01</d>", true)),
    new Case ("year-zero-1.1",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"d\" type=\"xs:date\"/><xs:element name=\"y\" type=\"xs:gYear\"/></xs:schema>",
      true,
      new DocCheck ("<d>0000-01-01</d>", true),
      new DocCheck ("<y>0000</y>", true)),
    new Case ("assert-list-typed-value",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:simpleType name=\"L\"><xs:list itemType=\"xs:int\"/></xs:simpleType>\n" +
"\t\t  <xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"n\" type=\"xs:decimal\" maxOccurs=\"unbounded\"/></xs:sequence>\n" +
"\t\t    <xs:attribute name=\"l\" type=\"L\"/>\n" +
"\t\t    <xs:assert test=\"count(data(@l)) = 3 and sum(data(@l)) = 6\"/>\n" +
"\t\t    <xs:assert test=\"n[1] = 1.5\"/></xs:complexType></xs:element>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e l=\"1 2 3\"><n>1.50</n></e>", true),
      new DocCheck ("<e l=\"1 2\"><n>1.5</n></e>", false),
      new DocCheck ("<e l=\"1 2 3\"><n>2</n></e>", false)),
    new Case ("assert-union-typed-value",
      VER_11,
      "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:simpleType name=\"U\"><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType>\n" +
"\t\t  <xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"u\" type=\"U\" maxOccurs=\"2\"/></xs:sequence>\n" +
"\t\t    <xs:assert test=\"data(u[1]) instance of xs:int and data(u[2]) instance of xs:date\"/></xs:complexType></xs:element>\n" +
"\t\t</xs:schema>",
      true,
      new DocCheck ("<e><u>1</u><u>2020-01-01</u></e>", true),
      new DocCheck ("<e><u>2020-01-01</u><u>1</u></e>", false))
  };

  @Test
  public void testDerivationMisc ()
  {
    final Tally aTally = XsdTestSupport.runCases (CASES, null, false);
    aTally.log ("derivation misc");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
  }
}
