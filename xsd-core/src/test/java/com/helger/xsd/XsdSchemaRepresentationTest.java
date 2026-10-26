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
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Schema documents whose validity is decided by the XML representation of
 * schema components: the schema for schemas and the Schema Representation
 * Constraints (src-*) and component constraints of XSD 1.0 / 1.1 Part 1
 * section 3. Ported from go-xslt <code>schema_representation_test.go</code>.
 *
 * @author Philip Helger
 */
public final class XsdSchemaRepresentationTest
{
  private static final String TNS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:t\" xmlns:t=\"urn:t\">";
  private static final int VER_BOTH = XsdTestSupport.VER_BOTH;
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;

  private static final Case [] CASES = {
    new Case ("xsd-namespace-attribute", VER_BOTH, "<xs:element name=\"e\" xs:type=\"xs:string\"/>", false),
    new Case ("foreign-attribute", VER_BOTH, "<xs:element name=\"e\" xmlns:f=\"urn:f\" f:x=\"1\"/>", true),
    new Case ("unknown-unqualified-attribute", VER_BOTH, "<xs:element name=\"e\" colour=\"red\"/>", false),
    new Case ("id-not-ncname", VER_BOTH, "<xs:element name=\"e\" id=\"1a\"/>", false),
    new Case ("id-duplicate", VER_BOTH, "<xs:element name=\"e\" id=\"x\"/><xs:element name=\"f\" id=\"x\"/>", false),
    new Case ("id-distinct", VER_BOTH, "<xs:element name=\"e\" id=\"x\"/><xs:element name=\"f\" id=\"y\"/>", true),
    new Case ("boolean-bad", VER_BOTH, "<xs:element name=\"e\" nillable=\"yes\"/>", false),
    new Case ("boolean-numeric", VER_BOTH, "<xs:element name=\"e\" nillable=\"1\"/>", true),
    new Case ("element-block-list", VER_BOTH, "<xs:element name=\"e\" block=\"list\"/>", false),
    new Case ("element-block-ok", VER_BOTH, "<xs:element name=\"e\" block=\"substitution extension\"/>", true),
    new Case ("element-final-substitution", VER_BOTH, "<xs:element name=\"e\" final=\"substitution\"/>", false),
    new Case ("complextype-block-substitution", VER_BOTH, "<xs:complexType name=\"c\" block=\"substitution\"/>", false),
    new Case ("complextype-final-all", VER_BOTH, "<xs:complexType name=\"c\" final=\"#all\"/>", true),
    new Case ("simpletype-final-list-union", VER_BOTH, "<xs:simpleType name=\"s\" final=\"list union\"><xs:restriction base=\"xs:string\"/></xs:simpleType>", true),
    new Case ("simpletype-final-extension-1.0", VER_10, "<xs:simpleType name=\"s\" final=\"extension\"><xs:restriction base=\"xs:string\"/></xs:simpleType>", false),
    new Case ("simpletype-final-extension-1.1", VER_11, "<xs:simpleType name=\"s\" final=\"extension\"><xs:restriction base=\"xs:string\"/></xs:simpleType>", true),
    new Case ("block-default-list", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" blockDefault=\"list\"/>", false),
    new Case ("final-default-substitution", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" finalDefault=\"substitution\"/>", false),
    new Case ("final-default-list", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" finalDefault=\"list union restriction\"/>", true),
    new Case ("process-contents-bad", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:any processContents=\"never\"/></xs:sequence></xs:complexType>", false),
    new Case ("use-bad", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute name=\"a\" use=\"sometimes\"/></xs:complexType>", false),
    new Case ("form-bad", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" form=\"global\"/></xs:sequence></xs:complexType>", false),
    new Case ("element-form-default-bad", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"yes\"/>", false),
    new Case ("unknown-xs-element", VER_BOTH, "<xs:elephant name=\"e\"/>", false),
    new Case ("ct-annotation-after-content", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence/><xs:annotation/></xs:complexType>", false),
    new Case ("ct-two-annotations", VER_BOTH, "<xs:complexType name=\"c\"><xs:annotation/><xs:annotation/></xs:complexType>", false),
    new Case ("ct-direct-element", VER_BOTH, "<xs:complexType name=\"c\"><xs:element name=\"a\"/></xs:complexType>", false),
    new Case ("ct-attribute-beside-simplecontent", VER_BOTH, "<xs:complexType name=\"c\"><xs:simpleContent><xs:extension base=\"xs:string\"/></xs:simpleContent><xs:attribute name=\"a\"/></xs:complexType>", false),
    new Case ("ct-simple-and-complex-content", VER_BOTH, "<xs:complexType name=\"c\"><xs:simpleContent><xs:extension base=\"xs:string\"/></xs:simpleContent><xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent></xs:complexType>", false),
    new Case ("ct-content-and-group", VER_BOTH, "<xs:complexType name=\"c\"><xs:complexContent><xs:extension base=\"xs:anyType\"/></xs:complexContent><xs:sequence/></xs:complexType>", false),
    new Case ("ct-two-groups", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence/><xs:choice/></xs:complexType>", false),
    new Case ("ct-attribute-before-group", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute name=\"a\"/><xs:sequence/></xs:complexType>", false),
    new Case ("ct-ok", VER_BOTH, "<xs:complexType name=\"c\"><xs:annotation/><xs:sequence><xs:element name=\"a\"/></xs:sequence><xs:attribute name=\"b\"/><xs:anyAttribute/></xs:complexType>", true),
    new Case ("sc-empty", VER_BOTH, "<xs:complexType name=\"c\"><xs:simpleContent/></xs:complexType>", false),
    new Case ("sc-restrict-anytype-without-simpletype", VER_BOTH, "<xs:complexType name=\"c\"><xs:simpleContent><xs:restriction base=\"xs:anyType\"/></xs:simpleContent></xs:complexType>", false),
    new Case ("sc-restrict-anytype-with-simpletype", VER_BOTH, "<xs:complexType name=\"c\"><xs:simpleContent><xs:restriction base=\"xs:anyType\"><xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:restriction></xs:simpleContent></xs:complexType>", true),
    new Case ("sc-extension-of-element-only", VER_BOTH, "<xs:complexType name=\"b\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType><xs:complexType name=\"c\"><xs:simpleContent><xs:extension base=\"b\"/></xs:simpleContent></xs:complexType>", false),
    new Case ("sc-facet-after-attribute", VER_BOTH, "<xs:complexType name=\"b\"><xs:simpleContent><xs:extension base=\"xs:int\"/></xs:simpleContent></xs:complexType><xs:complexType name=\"c\"><xs:simpleContent><xs:restriction base=\"b\"><xs:attribute name=\"a\"/><xs:maxInclusive value=\"3\"/></xs:restriction></xs:simpleContent></xs:complexType>", false),
    new Case ("sc-simpletype-after-attribute", VER_BOTH, "<xs:complexType name=\"b\"><xs:simpleContent><xs:extension base=\"xs:int\"/></xs:simpleContent></xs:complexType><xs:complexType name=\"c\"><xs:simpleContent><xs:restriction base=\"b\"><xs:attribute name=\"a\"/><xs:simpleType><xs:restriction base=\"xs:int\"/></xs:simpleType></xs:restriction></xs:simpleContent></xs:complexType>", false),
    new Case ("sc-inline-type-not-derived", VER_BOTH + SKIP_BUG, "<xs:complexType name=\"b\"><xs:simpleContent><xs:extension base=\"xs:decimal\"/></xs:simpleContent></xs:complexType><xs:complexType name=\"c\"><xs:simpleContent><xs:restriction base=\"b\"><xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:restriction></xs:simpleContent></xs:complexType>", false),
    new Case ("sc-inline-type-derived", VER_BOTH, "<xs:complexType name=\"b\"><xs:simpleContent><xs:extension base=\"xs:decimal\"/></xs:simpleContent></xs:complexType><xs:complexType name=\"c\"><xs:simpleContent><xs:restriction base=\"b\"><xs:simpleType><xs:restriction base=\"xs:integer\"/></xs:simpleType></xs:restriction></xs:simpleContent></xs:complexType>", true),
    new Case ("sc-extension-of-final-simple", VER_11, "<xs:simpleType name=\"s\" final=\"extension\"><xs:restriction base=\"xs:string\"/></xs:simpleType><xs:complexType name=\"c\"><xs:simpleContent><xs:extension base=\"s\"/></xs:simpleContent></xs:complexType>", false),
    new Case ("cc-empty", VER_BOTH, "<xs:complexType name=\"c\"><xs:complexContent/></xs:complexType>", false),
    new Case ("cc-extension-of-simple-type", VER_BOTH, "<xs:complexType name=\"c\"><xs:complexContent><xs:extension base=\"xs:string\"/></xs:complexContent></xs:complexType>", false),
    new Case ("cc-extension-of-simple-content", VER_BOTH, "<xs:complexType name=\"b\"><xs:simpleContent><xs:extension base=\"xs:string\"/></xs:simpleContent></xs:complexType><xs:complexType name=\"c\"><xs:complexContent><xs:extension base=\"b\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>", false),
    new Case ("cc-mixed-conflict-1.1", VER_11, "<xs:complexType name=\"c\" mixed=\"true\"><xs:complexContent mixed=\"false\"><xs:extension base=\"xs:anyType\"/></xs:complexContent></xs:complexType>", false),
    new Case ("cc-mixed-agree", VER_BOTH, "<xs:complexType name=\"c\" mixed=\"true\"><xs:complexContent mixed=\"true\"><xs:extension base=\"xs:anyType\"/></xs:complexContent></xs:complexType>", true),
    new Case ("cc-extend-mixed-element-only", VER_BOTH, "<xs:complexType name=\"b\" mixed=\"true\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType><xs:complexType name=\"c\"><xs:complexContent><xs:extension base=\"b\"><xs:sequence><xs:element name=\"z\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>", false),
    new Case ("cc-extend-mixed-empty", VER_BOTH, "<xs:complexType name=\"b\" mixed=\"true\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType><xs:complexType name=\"c\"><xs:complexContent><xs:extension base=\"b\"/></xs:complexContent></xs:complexType>", true),
    new Case ("cc-extend-element-only-mixed", VER_BOTH, "<xs:complexType name=\"b\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType><xs:complexType name=\"c\" mixed=\"true\"><xs:complexContent><xs:extension base=\"b\"><xs:sequence><xs:element name=\"z\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>", false),
    new Case ("attribute-in-sequence", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:attribute name=\"a\"/></xs:sequence></xs:complexType>", false),
    new Case ("group-ref-missing", VER_BOTH, "<xs:complexType name=\"c\"><xs:group/></xs:complexType>", false),
    new Case ("group-ref-unresolved", VER_BOTH, "<xs:complexType name=\"c\"><xs:group ref=\"nope\"/></xs:complexType>", false),
    new Case ("group-ref-not-imported", VER_BOTH, "<xs:complexType name=\"c\" xmlns:o=\"urn:o\"><xs:group ref=\"o:g\"/></xs:complexType>", false),
    new Case ("group-self-cycle", VER_BOTH, "<xs:group name=\"g\"><xs:sequence><xs:group ref=\"g\"/></xs:sequence></xs:group>", false),
    new Case ("group-indirect-cycle", VER_BOTH, "<xs:group name=\"g\"><xs:sequence><xs:group ref=\"h\"/></xs:sequence></xs:group><xs:group name=\"h\"><xs:choice><xs:group ref=\"g\"/></xs:choice></xs:group>", false),
    new Case ("group-ok", VER_BOTH, "<xs:group name=\"g\"><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:group><xs:complexType name=\"c\"><xs:sequence><xs:group ref=\"g\" maxOccurs=\"2\"/></xs:sequence></xs:complexType>", true),
    new Case ("group-without-name", VER_BOTH, "<xs:group><xs:sequence/></xs:group>", false),
    new Case ("group-two-compositors", VER_BOTH, "<xs:group name=\"g\"><xs:sequence/><xs:choice/></xs:group>", false),
    new Case ("group-def-with-occurs", VER_BOTH, "<xs:group name=\"g\" maxOccurs=\"2\"><xs:sequence/></xs:group>", false),
    new Case ("min-greater-than-max", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" minOccurs=\"3\" maxOccurs=\"2\"/></xs:sequence></xs:complexType>", false),
    new Case ("max-occurs-bad", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" maxOccurs=\"many\"/></xs:sequence></xs:complexType>", false),
    new Case ("min-occurs-negative", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" minOccurs=\"-1\"/></xs:sequence></xs:complexType>", false),
    new Case ("all-in-sequence", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:all><xs:element name=\"a\"/></xs:all></xs:sequence></xs:complexType>", false),
    new Case ("all-ref-in-sequence", VER_BOTH, "<xs:group name=\"g\"><xs:all><xs:element name=\"a\"/></xs:all></xs:group><xs:complexType name=\"c\"><xs:sequence><xs:group ref=\"g\"/></xs:sequence></xs:complexType>", false),
    new Case ("all-max-two", VER_BOTH, "<xs:complexType name=\"c\"><xs:all maxOccurs=\"2\"><xs:element name=\"a\"/></xs:all></xs:complexType>", false),
    new Case ("all-child-max-two-1.0", VER_10, "<xs:complexType name=\"c\"><xs:all><xs:element name=\"a\" maxOccurs=\"2\"/></xs:all></xs:complexType>", false),
    new Case ("all-child-max-two-1.1", VER_11, "<xs:complexType name=\"c\"><xs:all><xs:element name=\"a\" maxOccurs=\"2\"/></xs:all></xs:complexType>", true),
    new Case ("all-with-any-1.0", VER_10, "<xs:complexType name=\"c\"><xs:all><xs:any/></xs:all></xs:complexType>", false),
    new Case ("all-with-any-1.1", VER_11, "<xs:complexType name=\"c\"><xs:all><xs:any/></xs:all></xs:complexType>", true),
    new Case ("all-with-choice", VER_BOTH, "<xs:complexType name=\"c\"><xs:all><xs:choice/></xs:all></xs:complexType>", false),
    new Case ("edc-same-name-different-type", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" type=\"xs:int\"/><xs:element name=\"a\" type=\"xs:string\"/></xs:sequence></xs:complexType>", false),
    new Case ("edc-same-name-same-type", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" type=\"xs:int\"/><xs:element name=\"b\"/><xs:element name=\"a\" type=\"xs:int\"/></xs:sequence></xs:complexType>", true),
    new Case ("edc-nested-group", VER_BOTH, "<xs:complexType name=\"c\"><xs:choice><xs:element name=\"a\" type=\"xs:int\"/><xs:sequence><xs:element name=\"a\" type=\"xs:date\"/></xs:sequence></xs:choice></xs:complexType>", false),
    new Case ("local-element-ref-and-name", VER_BOTH, "<xs:element name=\"g\"/><xs:complexType name=\"c\"><xs:sequence><xs:element ref=\"g\" name=\"x\"/></xs:sequence></xs:complexType>", false),
    new Case ("local-element-no-name", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element type=\"xs:string\"/></xs:sequence></xs:complexType>", false),
    new Case ("local-element-ref-with-type", VER_BOTH, "<xs:element name=\"g\"/><xs:complexType name=\"c\"><xs:sequence><xs:element ref=\"g\" type=\"xs:string\"/></xs:sequence></xs:complexType>", false),
    new Case ("local-element-ref-with-nillable", VER_BOTH, "<xs:element name=\"g\"/><xs:complexType name=\"c\"><xs:sequence><xs:element ref=\"g\" nillable=\"true\"/></xs:sequence></xs:complexType>", false),
    new Case ("local-element-ref-unresolved", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element ref=\"g\"/></xs:sequence></xs:complexType>", false),
    new Case ("local-element-type-and-inline", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" type=\"xs:string\"><xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:element></xs:sequence></xs:complexType>", false),
    new Case ("local-element-abstract", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" abstract=\"true\"/></xs:sequence></xs:complexType>", false),
    new Case ("local-element-subst-group", VER_BOTH, "<xs:element name=\"h\"/><xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" substitutionGroup=\"h\"/></xs:sequence></xs:complexType>", false),
    new Case ("global-element-with-occurs", VER_BOTH, "<xs:element name=\"g\" minOccurs=\"0\"/>", false),
    new Case ("global-element-with-ref", VER_BOTH, "<xs:element name=\"g\"/><xs:element ref=\"g\"/>", false),
    new Case ("global-element-form", VER_BOTH, "<xs:element name=\"g\" form=\"qualified\"/>", false),
    new Case ("element-default-and-fixed", VER_BOTH, "<xs:element name=\"g\" type=\"xs:string\" default=\"a\" fixed=\"a\"/>", false),
    new Case ("element-default-not-of-type", VER_BOTH, "<xs:element name=\"g\" type=\"xs:int\" default=\"x\"/>", false),
    new Case ("element-fixed-of-type", VER_BOTH, "<xs:element name=\"g\" type=\"xs:int\" fixed=\" 7 \"/>", true),
    new Case ("element-default-element-only", VER_BOTH, "<xs:element name=\"g\" default=\"x\"><xs:complexType><xs:sequence><xs:element name=\"a\"/></xs:sequence></xs:complexType></xs:element>", false),
    new Case ("element-default-mixed-emptiable", VER_BOTH, "<xs:element name=\"g\" default=\"x\"><xs:complexType mixed=\"true\"><xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/></xs:sequence></xs:complexType></xs:element>", true),
    new Case ("element-id-default-1.0", VER_10, "<xs:element name=\"g\" type=\"xs:ID\" default=\"a\"/>", false),
    new Case ("element-id-default-1.1", VER_11, "<xs:element name=\"g\" type=\"xs:ID\" default=\"a\"/>", true),
    new Case ("local-tns-1.0", VER_10, TNS + "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" targetNamespace=\"urn:t\"/></xs:sequence></xs:complexType></xs:schema>", false),
    new Case ("local-tns-outside-restriction-1.1", VER_11, TNS + "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" targetNamespace=\"urn:x\"/></xs:sequence></xs:complexType></xs:schema>", false),
    new Case ("local-tns-with-form-1.1", VER_11 + SKIP_BUG, TNS + "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" targetNamespace=\"urn:t\" form=\"qualified\"/></xs:sequence></xs:complexType></xs:schema>", false),
    new Case ("local-tns-same-1.1", VER_11, TNS + "<xs:complexType name=\"c\"><xs:sequence><xs:element name=\"a\" targetNamespace=\"urn:t\"/></xs:sequence></xs:complexType></xs:schema>", true),
    new Case ("local-tns-in-restriction-1.1", VER_11, TNS + "<xs:complexType name=\"b\"><xs:sequence><xs:any namespace=\"##any\" processContents=\"lax\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:complexType name=\"c\"><xs:complexContent><xs:restriction base=\"t:b\"><xs:sequence><xs:element name=\"a\" targetNamespace=\"urn:x\"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType></xs:schema>", true),
    new Case ("local-attr-tns-with-form-1.1", VER_11 + SKIP_BUG, TNS + "<xs:complexType name=\"c\"><xs:attribute name=\"a\" targetNamespace=\"urn:t\" form=\"qualified\"/></xs:complexType></xs:schema>", false),
    new Case ("wildcard-any-in-list", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:any namespace=\"##any urn:a\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-other-in-list", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:any namespace=\"urn:a ##other\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-list-ok", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:any namespace=\"urn:a ##local ##targetNamespace\"/></xs:sequence></xs:complexType>", true),
    new Case ("wildcard-bad-keyword", VER_BOTH, "<xs:complexType name=\"c\"><xs:sequence><xs:any namespace=\"##foo\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-ns-and-notns-1.1", VER_11, "<xs:complexType name=\"c\"><xs:sequence><xs:any namespace=\"urn:a\" notNamespace=\"urn:b\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-notns-1.0", VER_10 + SKIP_BUG, "<xs:complexType name=\"c\"><xs:sequence><xs:any notNamespace=\"urn:b\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-notns-keywords-1.1", VER_11, "<xs:complexType name=\"c\"><xs:sequence><xs:any notNamespace=\"##local ##targetNamespace urn:b\"/></xs:sequence></xs:complexType>", true),
    new Case ("wildcard-notns-any-1.1", VER_11, "<xs:complexType name=\"c\"><xs:sequence><xs:any notNamespace=\"##any\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-notqname-defined-1.1", VER_11, "<xs:complexType name=\"c\"><xs:sequence><xs:any notQName=\"##defined ##definedSibling\"/></xs:sequence></xs:complexType>", true),
    new Case ("anyattribute-notqname-sibling-1.1", VER_11, "<xs:complexType name=\"c\"><xs:anyAttribute notQName=\"##definedSibling\"/></xs:complexType>", false),
    new Case ("anyattribute-notqname-defined-1.1", VER_11, "<xs:complexType name=\"c\"><xs:anyAttribute notQName=\"##defined\"/></xs:complexType>", true),
    new Case ("wildcard-notqname-bad-1.1", VER_11, "<xs:complexType name=\"c\"><xs:sequence><xs:any notQName=\"1x\"/></xs:sequence></xs:complexType>", false),
    new Case ("wildcard-notqname-1.0", VER_10 + SKIP_BUG, "<xs:complexType name=\"c\"><xs:sequence><xs:any notQName=\"a\"/></xs:sequence></xs:complexType>", false),
    new Case ("anyattribute-twice", VER_BOTH, "<xs:complexType name=\"c\"><xs:anyAttribute/><xs:anyAttribute/></xs:complexType>", false),
    new Case ("anyattribute-before-attribute", VER_BOTH, "<xs:complexType name=\"c\"><xs:anyAttribute/><xs:attribute name=\"a\"/></xs:complexType>", false),
    new Case ("attr-default-and-fixed", VER_BOTH, "<xs:attribute name=\"a\" default=\"x\" fixed=\"x\"/>", false),
    new Case ("attr-default-required", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute name=\"a\" default=\"x\" use=\"required\"/></xs:complexType>", false),
    new Case ("attr-fixed-required", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute name=\"a\" fixed=\"x\" use=\"required\"/></xs:complexType>", true),
    new Case ("attr-ref-and-type", VER_BOTH, "<xs:attribute name=\"g\"/><xs:complexType name=\"c\"><xs:attribute ref=\"g\" type=\"xs:string\"/></xs:complexType>", false),
    new Case ("attr-ref-and-name", VER_BOTH, "<xs:attribute name=\"g\"/><xs:complexType name=\"c\"><xs:attribute ref=\"g\" name=\"h\"/></xs:complexType>", false),
    new Case ("attr-ref-unresolved", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute ref=\"nope\"/></xs:complexType>", false),
    new Case ("attr-no-name", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute type=\"xs:string\"/></xs:complexType>", false),
    new Case ("attr-type-and-inline", VER_BOTH, "<xs:attribute name=\"a\" type=\"xs:string\"><xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:attribute>", false),
    new Case ("attr-complex-type", VER_BOTH, "<xs:complexType name=\"ct\"/><xs:attribute name=\"a\" type=\"ct\"/>", false),
    new Case ("attr-name-xmlns", VER_BOTH, "<xs:attribute name=\"xmlns\"/>", false),
    new Case ("attr-tns-xsi", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://www.w3.org/2001/XMLSchema-instance\"><xs:attribute name=\"foo\"/></xs:schema>", false),
    new Case ("attr-default-not-of-type", VER_BOTH, "<xs:attribute name=\"a\" type=\"xs:boolean\" default=\"maybe\"/>", false),
    new Case ("attr-global-use", VER_BOTH, "<xs:attribute name=\"a\" use=\"optional\"/>", false),
    new Case ("attr-duplicate-use", VER_BOTH, "<xs:complexType name=\"c\"><xs:attribute name=\"a\"/><xs:attribute name=\"a\"/></xs:complexType>", false),
    new Case ("attr-duplicate-via-group", VER_BOTH, "<xs:attributeGroup name=\"g\"><xs:attribute name=\"a\"/></xs:attributeGroup><xs:complexType name=\"c\"><xs:attribute name=\"a\"/><xs:attributeGroup ref=\"g\"/></xs:complexType>", false),
    new Case ("attr-use-fixed-vs-decl-fixed", VER_BOTH, "<xs:attribute name=\"g\" type=\"xs:string\" fixed=\"x\"/><xs:complexType name=\"c\"><xs:attribute ref=\"g\" fixed=\"y\"/></xs:complexType>", false),
    new Case ("two-id-attributes-1.0", VER_10, "<xs:complexType name=\"c\"><xs:attribute name=\"a\" type=\"xs:ID\"/><xs:attribute name=\"b\" type=\"xs:ID\"/></xs:complexType>", false),
    new Case ("two-id-attributes-1.1", VER_11, "<xs:complexType name=\"c\"><xs:attribute name=\"a\" type=\"xs:ID\"/><xs:attribute name=\"b\" type=\"xs:ID\"/></xs:complexType>", true),
    new Case ("id-attribute-fixed-1.0", VER_10, "<xs:attribute name=\"a\" type=\"xs:ID\" fixed=\"x\"/>", false),
    new Case ("attrgroup-unresolved", VER_BOTH, "<xs:complexType name=\"c\"><xs:attributeGroup ref=\"nope\"/></xs:complexType>", false),
    new Case ("attrgroup-cycle-1.0", VER_10, "<xs:attributeGroup name=\"g\"><xs:attributeGroup ref=\"h\"/></xs:attributeGroup><xs:attributeGroup name=\"h\"><xs:attributeGroup ref=\"g\"/></xs:attributeGroup>", false),
    new Case ("attrgroup-element-child", VER_BOTH, "<xs:attributeGroup name=\"g\"><xs:element name=\"e\"/></xs:attributeGroup>", false),
    new Case ("st-no-derivation", VER_BOTH, "<xs:simpleType name=\"s\"/>", false),
    new Case ("st-two-derivations", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:string\"/><xs:list itemType=\"xs:int\"/></xs:simpleType>", false),
    new Case ("st-restriction-base-and-inline", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:string\"><xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:restriction></xs:simpleType>", false),
    new Case ("st-restriction-neither", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction/></xs:simpleType>", false),
    new Case ("st-list-both", VER_BOTH, "<xs:simpleType name=\"s\"><xs:list itemType=\"xs:int\"><xs:simpleType><xs:restriction base=\"xs:int\"/></xs:simpleType></xs:list></xs:simpleType>", false),
    new Case ("st-list-neither", VER_BOTH, "<xs:simpleType name=\"s\"><xs:list/></xs:simpleType>", false),
    new Case ("st-list-of-list", VER_BOTH, "<xs:simpleType name=\"l\"><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:simpleType name=\"s\"><xs:list itemType=\"l\"/></xs:simpleType>", false),
    new Case ("st-union-neither", VER_BOTH, "<xs:simpleType name=\"s\"><xs:union/></xs:simpleType>", false),
    new Case ("st-union-ok", VER_BOTH, "<xs:simpleType name=\"s\"><xs:union memberTypes=\"xs:int\"><xs:simpleType><xs:restriction base=\"xs:date\"/></xs:simpleType></xs:union></xs:simpleType>", true),
    new Case ("st-restrict-complex", VER_BOTH, "<xs:complexType name=\"ct\"/><xs:simpleType name=\"s\"><xs:restriction base=\"ct\"/></xs:simpleType>", false),
    new Case ("st-restrict-anysimpletype", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:anySimpleType\"/></xs:simpleType>", false),
    new Case ("st-restrict-final", VER_BOTH, "<xs:simpleType name=\"b\" final=\"restriction\"><xs:restriction base=\"xs:string\"/></xs:simpleType><xs:simpleType name=\"s\"><xs:restriction base=\"b\"/></xs:simpleType>", false),
    new Case ("st-list-of-final", VER_BOTH, "<xs:simpleType name=\"b\" final=\"list\"><xs:restriction base=\"xs:string\"/></xs:simpleType><xs:simpleType name=\"s\"><xs:list itemType=\"b\"/></xs:simpleType>", false),
    new Case ("st-union-of-final", VER_BOTH, "<xs:simpleType name=\"b\" final=\"union\"><xs:restriction base=\"xs:string\"/></xs:simpleType><xs:simpleType name=\"s\"><xs:union memberTypes=\"b\"/></xs:simpleType>", false),
    new Case ("st-self-cycle", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"s\"/></xs:simpleType>", false),
    new Case ("st-union-cycle", VER_11, "<xs:simpleType name=\"s\"><xs:union memberTypes=\"u\"/></xs:simpleType><xs:simpleType name=\"u\"><xs:union memberTypes=\"s\"/></xs:simpleType>", false),
    new Case ("st-local-with-name", VER_BOTH, "<xs:element name=\"e\"><xs:simpleType name=\"s\"><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:element>", false),
    new Case ("st-global-without-name", VER_BOTH, "<xs:simpleType><xs:restriction base=\"xs:string\"/></xs:simpleType>", false),
    new Case ("ct-type-cycle", VER_BOTH, "<xs:complexType name=\"a\"><xs:complexContent><xs:extension base=\"b\"/></xs:complexContent></xs:complexType><xs:complexType name=\"b\"><xs:complexContent><xs:extension base=\"a\"/></xs:complexContent></xs:complexType>", false),
    new Case ("duplicate-global-type", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:string\"/></xs:simpleType><xs:complexType name=\"s\"/>", false),
    new Case ("duplicate-global-element", VER_BOTH, "<xs:element name=\"e\"/><xs:element name=\"e\"/>", false),
    new Case ("subst-type-not-derived", VER_BOTH, "<xs:element name=\"h\" type=\"xs:int\"/><xs:element name=\"m\" substitutionGroup=\"h\" type=\"xs:date\"/>", false),
    new Case ("subst-type-derived", VER_BOTH, "<xs:element name=\"h\" type=\"xs:decimal\"/><xs:element name=\"m\" substitutionGroup=\"h\" type=\"xs:int\"/>", true),
    new Case ("subst-type-inherited", VER_BOTH, "<xs:element name=\"h\" type=\"xs:int\"/><xs:element name=\"m\" substitutionGroup=\"h\"/>", true),
    new Case ("subst-head-final-extension", VER_BOTH, "<xs:complexType name=\"b\"/><xs:complexType name=\"d\"><xs:complexContent><xs:extension base=\"b\"><xs:sequence><xs:element name=\"z\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
               "		  <xs:element name=\"h\" type=\"b\" final=\"extension\"/><xs:element name=\"m\" substitutionGroup=\"h\" type=\"d\"/>", false),
    new Case ("subst-head-final-restriction-ok", VER_BOTH, "<xs:complexType name=\"b\"/><xs:complexType name=\"d\"><xs:complexContent><xs:extension base=\"b\"><xs:sequence><xs:element name=\"z\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
               "		  <xs:element name=\"h\" type=\"b\" final=\"restriction\"/><xs:element name=\"m\" substitutionGroup=\"h\" type=\"d\"/>", true),
    new Case ("subst-cycle", VER_BOTH, "<xs:element name=\"a\" substitutionGroup=\"b\"/><xs:element name=\"b\" substitutionGroup=\"a\"/>", false),
    new Case ("subst-unresolved-head", VER_BOTH + SKIP_BUG, "<xs:element name=\"a\" substitutionGroup=\"nope\"/>", false),
    new Case ("subst-multiple-heads-1.0", VER_10 + SKIP_BUG, "<xs:element name=\"h1\"/><xs:element name=\"h2\"/><xs:element name=\"m\" substitutionGroup=\"h1 h2\"/>", false),
    new Case ("subst-multiple-heads-1.1", VER_11, "<xs:element name=\"h1\"/><xs:element name=\"h2\"/><xs:element name=\"m\" substitutionGroup=\"h1 h2\"/>", true),
    new Case ("identity-duplicate-name", VER_BOTH, "<xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:key name=\"k\"><xs:selector xpath=\"a\"/><xs:field xpath=\".\"/></xs:key><xs:unique name=\"k\"><xs:selector xpath=\"a\"/><xs:field xpath=\".\"/></xs:unique></xs:element>", false),
    new Case ("identity-keyref-unresolved", VER_BOTH, "<xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:keyref name=\"r\" refer=\"nope\"><xs:selector xpath=\"a\"/><xs:field xpath=\".\"/></xs:keyref></xs:element>", false),
    new Case ("identity-keyref-field-count", VER_BOTH, "<xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:key name=\"k\"><xs:selector xpath=\"a\"/><xs:field xpath=\".\"/></xs:key><xs:keyref name=\"r\" refer=\"k\"><xs:selector xpath=\"a\"/><xs:field xpath=\".\"/><xs:field xpath=\"@x\"/></xs:keyref></xs:element>", false),
    new Case ("identity-selector-attribute", VER_BOTH, "<xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:key name=\"k\"><xs:selector xpath=\"a/@x\"/><xs:field xpath=\".\"/></xs:key></xs:element>", false),
    new Case ("identity-field-bad-xpath", VER_BOTH, "<xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:key name=\"k\"><xs:selector xpath=\"a\"/><xs:field xpath=\"../b\"/></xs:key></xs:element>", false),
    new Case ("identity-no-field", VER_BOTH, "<xs:element name=\"e\"><xs:complexType><xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
               "		  <xs:key name=\"k\"><xs:selector xpath=\"a\"/></xs:key></xs:element>", false),
    new Case ("notation-ok", VER_BOTH, "<xs:notation name=\"n\" public=\"p\"/><xs:simpleType name=\"s\"><xs:restriction base=\"xs:NOTATION\"><xs:enumeration value=\"n\"/></xs:restriction></xs:simpleType>", true),
    new Case ("notation-without-ids", VER_BOTH, "<xs:notation name=\"n\"/>", false),
    new Case ("notation-direct-use", VER_BOTH, "<xs:element name=\"e\" type=\"xs:NOTATION\"/>", false),
    new Case ("notation-enum-undeclared", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:NOTATION\"><xs:enumeration value=\"nope\"/></xs:restriction></xs:simpleType>", false),
    new Case ("notation-no-enumeration", VER_BOTH, "<xs:simpleType name=\"s\"><xs:restriction base=\"xs:NOTATION\"/></xs:simpleType><xs:element name=\"e\" type=\"s\"/>", false)
 };

  @Test
  public void testSchemaRepresentation ()
  {
    final Tally aTally = XsdTestSupport.runCases (CASES, null, true);
    aTally.log ("schema representation");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
    // The loader decides the bulk of these without the later phases
    assertTrue (aTally.m_nDecided > 150);
  }
}
