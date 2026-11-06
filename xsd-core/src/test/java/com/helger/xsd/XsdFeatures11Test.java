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
 * XSD 1.1 additions: assertions on complex types (Structures 1.1 section 3.13,
 * cvc-assertion), conditional type assignment (section 3.12,
 * cvc-type-alternative and e-props-correct), inheritable attributes (section
 * 3.3.5.6, used by type alternatives), open content (section 3.4.2.3.3),
 * wildcards with notQName, xs:all with counts, defaultAttributes, and the 1.1
 * only element vocabulary being rejected by 1.0. Ported from go-xslt
 * <code>xsd11_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class XsdFeatures11Test
{
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;

  private static final Case [] CASES = { new Case ("assert-typed-attributes",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:attribute name=\"min\" type=\"xs:int\"/><xs:attribute name=\"max\" type=\"xs:int\"/>\n" +
                "\t\t    <xs:assert test=\"@min le @max\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r min=\"9\" max=\"10\"/>", true),
            new DocCheck ("<r min=\"10\" max=\"9\"/>", false)),
        new Case ("assert-children",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:element name=\"i\" type=\"xs:int\" maxOccurs=\"unbounded\"/></xs:sequence>\n" +
                "\t\t    <xs:attribute name=\"n\" type=\"xs:int\"/><xs:assert test=\"count(i) = @n\"/><xs:assert test=\"sum(i) lt 100\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r n=\"2\"><i>1</i><i>2</i></r>", true),
            new DocCheck ("<r n=\"3\"><i>1</i><i>2</i></r>", false),
            new DocCheck ("<r n=\"2\"><i>60</i><i>50</i></r>", false)),
        new Case ("assert-inherited",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:complexType name=\"B\"><xs:sequence><xs:element name=\"a\" type=\"xs:int\"/></xs:sequence><xs:assert test=\"a gt 0\"/></xs:complexType>\n" +
                "\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"B\"><xs:sequence><xs:element name=\"b\" type=\"xs:int\"/></xs:sequence>\n" +
                "\t\t    <xs:assert test=\"b gt a\"/></xs:extension></xs:complexContent></xs:complexType>\n" +
                "\t\t  <xs:element name=\"r\" type=\"D\"/></xs:schema>",
            true,
            new DocCheck ("<r><a>1</a><b>2</b></r>", true),
            new DocCheck ("<r><a>0</a><b>2</b></r>", false),
            new DocCheck ("<r><a>3</a><b>2</b></r>", false)),
        new Case ("assert-simple-content",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:simpleContent><xs:extension base=\"xs:int\"><xs:attribute name=\"lim\" type=\"xs:int\"/>\n" +
                "\t\t    <xs:assert test=\"$value le @lim\"/></xs:extension></xs:simpleContent></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r lim=\"5\">4</r>", true),
            new DocCheck ("<r lim=\"5\">6</r>", false)),
        // The schema type of an assertion tree node is shared per pair of
        // types, but the union member is resolved per value
        new Case ("assert-shared-schema-types",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:simpleType name=\"U\"><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType>\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:element name=\"e\" maxOccurs=\"unbounded\"><xs:complexType><xs:simpleContent>\n" +
                "\t\t    <xs:extension base=\"U\"><xs:attribute name=\"w\" type=\"xs:int\"/><xs:attribute name=\"u\" type=\"U\"/></xs:extension></xs:simpleContent></xs:complexType></xs:element></xs:sequence>\n" +
                "\t\t    <xs:assert test=\"count(e[data(.) instance of xs:int]) = 2 and count(e[data(.) instance of xs:date]) = 1 and sum(e/@w) = 6 and count(e/@u[data(.) instance of xs:date]) = 1 and count(e/@u[data(.) instance of xs:int]) = 1\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r><e w=\"1\" u=\"2000-01-01\">1</e><e w=\"2\" u=\"7\">2000-01-01</e><e w=\"3\">3</e></r>", true),
            new DocCheck ("<r><e w=\"1\" u=\"2000-01-01\">1</e><e w=\"2\" u=\"7\">2000-01-01</e><e w=\"3\">2000-01-02</e></r>", false),
            new DocCheck ("<r><e w=\"1\" u=\"2000-01-01\">1</e><e w=\"2\" u=\"2000-01-02\">2000-01-01</e><e w=\"3\">3</e></r>", false)),
        new Case ("assert-default-namespace",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:t\" elementFormDefault=\"qualified\" xpathDefaultNamespace=\"##targetNamespace\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:element name=\"i\" maxOccurs=\"unbounded\"/></xs:sequence>\n" +
                "\t\t    <xs:assert test=\"count(i) = 2\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns=\"urn:t\"><i/><i/></r>", true),
            new DocCheck ("<r xmlns=\"urn:t\"><i/></r>", false)),
        new Case ("assert-default-namespace-on-assert",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:t\" elementFormDefault=\"qualified\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:element name=\"i\" maxOccurs=\"unbounded\"/></xs:sequence>\n" +
                "\t\t    <xs:assert test=\"count(i) = 2\" xpathDefaultNamespace=\"##targetNamespace\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns=\"urn:t\"><i/><i/></r>", true)),
        new Case ("assert-dynamic-error-is-false",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:attribute name=\"v\" type=\"xs:string\"/><xs:assert test=\"xs:int(@v) gt 0\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r v=\"3\"/>", true),
            new DocCheck ("<r v=\"x\"/>", false)),
        new Case ("assert-bad-xpath",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:assert test=\"((\"/></xs:complexType></xs:element></xs:schema>",
            false),
        new Case ("assert-no-test",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:assert/></xs:complexType></xs:element></xs:schema>",
            false),
        new Case ("assert-1.0",
            VER_10,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:assert test=\"true()\"/></xs:complexType></xs:element></xs:schema>",
            false),
        new Case ("cta",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:complexType name=\"B\"><xs:sequence><xs:any processContents=\"skip\" minOccurs=\"0\" maxOccurs=\"unbounded\"/></xs:sequence><xs:attribute name=\"kind\" type=\"xs:string\"/><xs:anyAttribute processContents=\"lax\"/></xs:complexType>\n" +
                "\t\t  <xs:complexType name=\"A\"><xs:complexContent><xs:restriction base=\"B\"><xs:sequence><xs:element name=\"a\" type=\"xs:int\"/></xs:sequence><xs:attribute name=\"kind\" type=\"xs:string\"/></xs:restriction></xs:complexContent></xs:complexType>\n" +
                "\t\t  <xs:complexType name=\"C\"><xs:complexContent><xs:restriction base=\"B\"><xs:sequence><xs:element name=\"c\" type=\"xs:date\"/></xs:sequence><xs:attribute name=\"kind\" type=\"xs:string\"/></xs:restriction></xs:complexContent></xs:complexType>\n" +
                "\t\t  <xs:element name=\"r\" type=\"B\">\n" +
                "\t\t    <xs:alternative test=\"@kind = 'a'\" type=\"A\"/>\n" +
                "\t\t    <xs:alternative test=\"@kind = 'c'\" type=\"C\"/>\n" +
                "\t\t    <xs:alternative type=\"B\"/>\n" +
                "\t\t  </xs:element></xs:schema>",
            true,
            new DocCheck ("<r kind=\"a\"><a>1</a></r>", true),
            new DocCheck ("<r kind=\"a\"><a>x</a></r>", false),
            new DocCheck ("<r kind=\"a\"><c>2020-01-01</c></r>", false),
            new DocCheck ("<r kind=\"c\"><c>2020-01-01</c></r>", true),
            new DocCheck ("<r kind=\"z\"><anything/></r>", true)),
        new Case ("cta-no-children-visible",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:alternative test=\"a\" type=\"xs:int\"/><xs:alternative type=\"xs:anyType\"/></xs:element></xs:schema>",
            true,
            new DocCheck ("<r><a/></r>", true)),
        new Case ("cta-typed-attribute",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:complexType name=\"N\"><xs:simpleContent><xs:extension base=\"xs:decimal\"><xs:attribute name=\"v\" type=\"xs:int\"/></xs:extension></xs:simpleContent></xs:complexType>\n" +
                "\t\t  <xs:complexType name=\"Small\"><xs:simpleContent><xs:restriction base=\"N\"><xs:maxInclusive value=\"9\"/></xs:restriction></xs:simpleContent></xs:complexType>\n" +
                "\t\t  <xs:element name=\"r\" type=\"N\"><xs:alternative test=\"@v &lt; 10\" type=\"Small\"/></xs:element></xs:schema>",
            true,
            new DocCheck ("<r v=\"2\">5</r>", true),
            new DocCheck ("<r v=\"2\">15</r>", false),
            new DocCheck ("<r v=\"10\">15</r>", true)),
        new Case ("cta-inherited-attribute",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"outer\"><xs:complexType><xs:sequence><xs:element ref=\"inner\"/></xs:sequence>\n" +
                "\t\t    <xs:attribute name=\"mode\" type=\"xs:string\" inheritable=\"true\"/></xs:complexType></xs:element>\n" +
                "\t\t  <xs:element name=\"inner\" type=\"xs:anyType\"><xs:alternative test=\"@mode = 'num'\" type=\"xs:int\"/></xs:element></xs:schema>",
            true,
            new DocCheck ("<outer mode=\"num\"><inner>5</inner></outer>", true),
            new DocCheck ("<outer mode=\"num\"><inner>x</inner></outer>", false),
            new DocCheck ("<outer mode=\"txt\"><inner>x</inner></outer>", true)),
        new Case ("cta-type-not-derived",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:int\"><xs:alternative test=\"true()\" type=\"xs:string\"/></xs:element></xs:schema>",
            false),
        new Case ("cta-inline-type",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative test=\"true()\"><xs:simpleType><xs:restriction base=\"xs:string\"><xs:maxLength value=\"2\"/></xs:restriction></xs:simpleType></xs:alternative></xs:element></xs:schema>",
            true,
            new DocCheck ("<r>ab</r>", true),
            new DocCheck ("<r>abc</r>", false)),
        new Case ("cta-type-and-inline",
            VER_11 + SKIP_BUG,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative test=\"true()\" type=\"xs:string\"><xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:alternative></xs:element></xs:schema>",
            false),
        new Case ("cta-no-type",
            VER_11 + SKIP_BUG,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative test=\"true()\"/></xs:element></xs:schema>",
            false),
        new Case ("cta-default-not-last",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative type=\"xs:string\"/><xs:alternative test=\"true()\" type=\"xs:string\"/></xs:element></xs:schema>",
            false),
        new Case ("cta-bad-test",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative test=\"@\" type=\"xs:string\"/></xs:element></xs:schema>",
            false),
        new Case ("cta-error-type",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative test=\"@bad\" type=\"xs:error\"/></xs:element></xs:schema>",
            true,
            new DocCheck ("<r>ok</r>", true),
            new DocCheck ("<r bad=\"1\">ok</r>", false)),
        new Case ("cta-1.0",
            VER_10,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\" type=\"xs:string\"><xs:alternative test=\"true()\" type=\"xs:string\"/></xs:element></xs:schema>",
            false),
        new Case ("open-interleave",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:openContent mode=\"interleave\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:openContent>\n" +
                "\t\t    <xs:sequence><xs:element name=\"a\"/><xs:element name=\"b\"/></xs:sequence></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns:p=\"urn:p\"><p:x/><a/><p:y/><b/><p:z/></r>", true),
            new DocCheck ("<r><a/><b/></r>", true),
            new DocCheck ("<r><a/><c/><b/></r>", false),
            new DocCheck ("<r xmlns:p=\"urn:p\"><b/><p:x/><a/></r>", false)),
        new Case ("open-suffix",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:openContent mode=\"suffix\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:openContent>\n" +
                "\t\t    <xs:sequence><xs:element name=\"a\"/><xs:element name=\"b\"/></xs:sequence></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns:p=\"urn:p\"><a/><b/><p:x/><p:y/></r>", true),
            new DocCheck ("<r xmlns:p=\"urn:p\"><a/><p:x/><b/></r>", false)),
        new Case ("open-none",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:openContent mode=\"none\"/>\n" +
                "\t\t    <xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns:p=\"urn:p\"><a/><p:x/></r>", false)),
        new Case ("open-lax-declared",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"g\" type=\"xs:int\"/>\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:openContent><xs:any processContents=\"lax\"/></xs:openContent>\n" +
                "\t\t    <xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r><g>1</g><a/></r>", true),
            new DocCheck ("<r><g>x</g><a/></r>", false)),
        new Case ("default-open-content",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:defaultOpenContent mode=\"suffix\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:defaultOpenContent>\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType></xs:element>\n" +
                "\t\t  <xs:element name=\"e\"><xs:complexType/></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns:p=\"urn:p\"><a/><p:x/></r>", true),
            new DocCheck ("<e xmlns:p=\"urn:p\"><p:x/></e>", false)),
        new Case ("default-open-content-applies-to-empty",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:defaultOpenContent mode=\"interleave\" appliesToEmpty=\"true\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:defaultOpenContent>\n" +
                "\t\t  <xs:element name=\"e\"><xs:complexType/></xs:element></xs:schema>",
            true,
            new DocCheck ("<e xmlns:p=\"urn:p\"><p:x/></e>", true)),
        new Case ("open-content-missing-any",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:openContent mode=\"interleave\"/><xs:sequence/></xs:complexType></xs:element></xs:schema>",
            false),
        new Case ("open-content-1.0",
            VER_10,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:openContent><xs:any/></xs:openContent><xs:sequence/></xs:complexType></xs:element></xs:schema>",
            false),
        new Case ("not-qname-defined",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"g\"/>\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:any notQName=\"##defined\" processContents=\"skip\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r><x/></r>", true),
            new DocCheck ("<r><g/></r>", false)),
        new Case ("not-qname-defined-sibling",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/><xs:any notQName=\"##definedSibling\" processContents=\"skip\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r><a/><x/></r>", true),
            new DocCheck ("<r><a/><a/></r>", false)),
        new Case ("not-qname-explicit",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:any notQName=\"x\" processContents=\"skip\" maxOccurs=\"unbounded\"/></xs:sequence>\n" +
                "\t\t    <xs:anyAttribute notNamespace=\"##local\" processContents=\"skip\"/></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r xmlns:p=\"urn:p\" p:q=\"1\"><y/></r>", true),
            new DocCheck ("<r><x/></r>", false),
            new DocCheck ("<r q=\"1\"><y/></r>", false)),
        new Case ("all-counts",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:all><xs:element name=\"a\" minOccurs=\"0\" maxOccurs=\"2\"/><xs:element name=\"b\" minOccurs=\"2\" maxOccurs=\"3\"/><xs:any namespace=\"##other\" processContents=\"skip\" minOccurs=\"0\"/></xs:all></xs:complexType></xs:element></xs:schema>",
            true,
            new DocCheck ("<r><b/><a/><b/><a/></r>", true),
            new DocCheck ("<r xmlns:p=\"urn:p\"><b/><p:x/><b/></r>", true),
            new DocCheck ("<r><a/><a/><a/><b/><b/></r>", false),
            new DocCheck ("<r><a/><b/></r>", false)),
        new Case ("default-attributes",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" defaultAttributes=\"common\">\n" +
                "\t\t  <xs:attributeGroup name=\"common\"><xs:attribute name=\"lang\" type=\"xs:language\"/></xs:attributeGroup>\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType/></xs:element>\n" +
                "\t\t  <xs:element name=\"s\"><xs:complexType defaultAttributesApply=\"false\"/></xs:element></xs:schema>",
            true,
            new DocCheck ("<r lang=\"en\"/>", true),
            new DocCheck ("<r lang=\"1-bad-\"/>", false),
            new DocCheck ("<s lang=\"en\"/>", false)),
        new Case ("default-attributes-unresolved",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" defaultAttributes=\"nope\">\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType/></xs:element></xs:schema>",
            false),
        new Case ("default-attributes-duplicate",
            VER_11,
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" defaultAttributes=\"common\">\n" +
                "\t\t  <xs:attributeGroup name=\"common\"><xs:attribute name=\"lang\" type=\"xs:language\"/></xs:attributeGroup>\n" +
                "\t\t  <xs:element name=\"r\"><xs:complexType><xs:attribute name=\"lang\" type=\"xs:string\"/></xs:complexType></xs:element></xs:schema>",
            false) };

  @Test
  public void testFeatures ()
  {
    final Tally aTally = XsdTestSupport.runCases (CASES, null, false);
    aTally.finish ("XSD 1.1 features");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
  }
}
