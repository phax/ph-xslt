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

import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Case;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.DocCheck;
import com.helger.xsd.XsdTestSupport.EVerdict;
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Spec-derived tests for complex-type derivation by restriction: XSD 1.0
 * Structures section 3.4.6 (derivation-ok-restriction) and section 3.9.6
 * (Particle Valid (Restriction), cos-particle-restrict and its sub-clauses), and
 * XSD 1.1 Structures sections 3.4.6.3 and 3.9.6 where 1.1 replaces the clause
 * tables by language inclusion. Ported from go-xslt
 * <code>restriction_derivation_test.go</code>; an unsupported outcome (a stub of
 * a later phase) is tolerated and counted, a decided verdict must be the
 * expected one.
 *
 * @author Philip Helger
 */
public final class XsdRestrictionDerivationTest
{
  private static final int VER_BOTH = XsdTestSupport.VER_BOTH;
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;
  private static final String EL_A = "<xs:element name=\"a\" type=\"xs:string\"/>";
  private static final String EL_B = "<xs:element name=\"b\" type=\"xs:string\"/>";
  private static final String EL_C = "<xs:element name=\"c\" type=\"xs:string\"/>";
  private static final String OPT_B = "<xs:element name=\"b\" type=\"xs:string\" minOccurs=\"0\"/>";
  private static final String CK_EL_A = "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/></xs:sequence>";

  private static String _group (final String sName, final String... aMembers)
  {
    return "<xs:" + sName + ">" + String.join ("", aMembers) + "</xs:" + sName + ">";
  }

  private static String seq (final String... aMembers)
  {
    return _group ("sequence", aMembers);
  }

  private static String choice (final String... aMembers)
  {
    return _group ("choice", aMembers);
  }

  private static String all (final String... aMembers)
  {
    return _group ("all", aMembers);
  }

  /**
   * Declare base type B with the given content and derive D from it by
   * restriction with the other content; extra holds further global declarations.
   */
  private static String restrictionSchema (final String sBase, final String sDerived, final String sExtra)
  {
    return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
           "  <xs:complexType name=\"B\">" + sBase + "</xs:complexType>\n" +
           "  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\">" + sDerived + "</xs:restriction></xs:complexContent></xs:complexType>\n" +
           "  <xs:element name=\"root\" type=\"D\"/>" + sExtra + "\n" +
           "</xs:schema>";
  }

  private static Case c (final String sName, final int nVer, final String sBase, final String sDerived, final String sExtra, final boolean bValid)
  {
    return new Case (sName, nVer, restrictionSchema (sBase, sDerived, sExtra), bValid);
  }

  private static final Case [] PARTICLE_CASES = {
    c ("elt-same",
      VER_BOTH,
      seq (EL_A),
      seq (EL_A),
      "",
      true),
    c ("elt-occurs-narrower",
      VER_BOTH,
      seq ("<xs:element name=\"a\" minOccurs=\"0\" maxOccurs=\"5\"/>"),
      seq ("<xs:element name=\"a\" minOccurs=\"1\" maxOccurs=\"3\"/>"),
      "",
      true),
    c ("elt-occurs-wider-max",
      VER_BOTH,
      seq ("<xs:element name=\"a\" maxOccurs=\"5\"/>"),
      seq ("<xs:element name=\"a\" maxOccurs=\"6\"/>"),
      "",
      false),
    c ("elt-occurs-unbounded-vs-bounded",
      VER_BOTH,
      seq ("<xs:element name=\"a\" maxOccurs=\"5\"/>"),
      seq ("<xs:element name=\"a\" maxOccurs=\"unbounded\"/>"),
      "",
      false),
    c ("elt-occurs-lower-min",
      VER_BOTH,
      seq ("<xs:element name=\"a\" minOccurs=\"2\" maxOccurs=\"5\"/>"),
      seq ("<xs:element name=\"a\" minOccurs=\"1\" maxOccurs=\"5\"/>"),
      "",
      false),
    c ("elt-other-name",
      VER_BOTH,
      seq (EL_A),
      seq (EL_B),
      "",
      false),
    c ("elt-type-derived",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:decimal\"/>"),
      seq ("<xs:element name=\"a\" type=\"xs:integer\"/>"),
      "",
      true),
    c ("elt-type-not-derived",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:decimal\"/>"),
      seq ("<xs:element name=\"a\" type=\"xs:string\"/>"),
      "",
      false),
    c ("elt-nillable-added",
      VER_BOTH,
      seq (EL_A),
      seq ("<xs:element name=\"a\" type=\"xs:string\" nillable=\"true\"/>"),
      "",
      false),
    c ("elt-nillable-removed",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:string\" nillable=\"true\"/>"),
      seq (EL_A),
      "",
      true),
    c ("elt-fixed-dropped",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:string\" fixed=\"x\"/>"),
      seq (EL_A),
      "",
      false),
    c ("elt-fixed-kept",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:string\" fixed=\"x\"/>"),
      seq ("<xs:element name=\"a\" type=\"xs:string\" fixed=\"x\"/>"),
      "",
      true),
    c ("elt-fixed-changed",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:string\" fixed=\"x\"/>"),
      seq ("<xs:element name=\"a\" type=\"xs:string\" fixed=\"y\"/>"),
      "",
      false),
    c ("elt-fixed-added",
      VER_BOTH,
      seq (EL_A),
      seq ("<xs:element name=\"a\" type=\"xs:string\" fixed=\"y\"/>"),
      "",
      true),
    c ("elt-block-weakened",
      VER_BOTH,
      seq ("<xs:element name=\"a\" type=\"xs:string\" block=\"extension\"/>"),
      seq (EL_A),
      "",
      false),
    c ("elt-block-strengthened",
      VER_BOTH,
      seq (EL_A),
      seq ("<xs:element name=\"a\" type=\"xs:string\" block=\"#all\"/>"),
      "",
      true),
    c ("elt-restricts-any",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\"/>"),
      seq (EL_A),
      "",
      true),
    c ("elt-restricts-any-too-many",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\"/>"),
      seq ("<xs:element name=\"a\" maxOccurs=\"2\"/>"),
      "",
      false),
    c ("elt-not-in-other",
      VER_BOTH,
      seq ("<xs:any namespace=\"##other\" processContents=\"lax\"/>"),
      seq (EL_A),
      "",
      false),
    c ("elt-in-local",
      VER_BOTH,
      seq ("<xs:any namespace=\"##local\" processContents=\"lax\"/>"),
      seq (EL_A),
      "",
      true),
    c ("any-restricts-elt",
      VER_BOTH,
      seq (EL_A),
      seq ("<xs:any processContents=\"lax\"/>"),
      "",
      false),
    c ("any-subset",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\"/>"),
      seq ("<xs:any namespace=\"##other\" processContents=\"lax\"/>"),
      "",
      true),
    c ("any-superset",
      VER_BOTH,
      seq ("<xs:any namespace=\"##other\" processContents=\"lax\"/>"),
      seq ("<xs:any processContents=\"lax\"/>"),
      "",
      false),
    c ("any-list-subset",
      VER_BOTH,
      seq ("<xs:any namespace=\"urn:a urn:b\" processContents=\"lax\"/>"),
      seq ("<xs:any namespace=\"urn:a\" processContents=\"lax\"/>"),
      "",
      true),
    c ("any-list-superset",
      VER_BOTH,
      seq ("<xs:any namespace=\"urn:a\" processContents=\"lax\"/>"),
      seq ("<xs:any namespace=\"urn:a urn:b\" processContents=\"lax\"/>"),
      "",
      false),
    c ("any-process-stronger",
      VER_BOTH,
      seq ("<xs:any processContents=\"skip\"/>"),
      seq ("<xs:any processContents=\"strict\"/>"),
      "",
      true),
    c ("any-process-weaker",
      VER_BOTH,
      seq ("<xs:any processContents=\"strict\"/>"),
      seq ("<xs:any processContents=\"lax\"/>"),
      "",
      false),
    c ("any-occurs-wider",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\" maxOccurs=\"2\"/>"),
      seq ("<xs:any processContents=\"lax\" maxOccurs=\"3\"/>"),
      "",
      false),
    c ("group-restricts-any-unbounded",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\" maxOccurs=\"unbounded\"/>"),
      seq (EL_A, EL_B),
      "",
      true),
    c ("group-restricts-any-count-exceeded",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\"/>"),
      seq (EL_A, EL_B),
      "",
      false),
    c ("choice-restricts-any",
      VER_BOTH,
      seq ("<xs:any processContents=\"lax\"/>"),
      seq (choice (EL_A, EL_B)),
      "",
      true),
    c ("group-restricts-any-ns-mismatch",
      VER_BOTH,
      seq ("<xs:any namespace=\"##other\" processContents=\"lax\" maxOccurs=\"unbounded\"/>"),
      seq (EL_A, EL_B),
      "",
      false),
    c ("seq-drop-optional",
      VER_BOTH,
      seq (EL_A, OPT_B, EL_C),
      seq (EL_A, EL_C),
      "",
      true),
    c ("seq-drop-required",
      VER_BOTH,
      seq (EL_A, OPT_B, EL_C),
      seq (EL_A),
      "",
      false),
    c ("seq-add-particle",
      VER_BOTH,
      seq (EL_A, OPT_B),
      seq (EL_A, EL_B, EL_C),
      "",
      false),
    c ("seq-reorder",
      VER_BOTH,
      seq (EL_A, EL_C),
      seq (EL_C, EL_A),
      "",
      false),
    c ("seq-group-occurs-narrower",
      VER_BOTH,
      "<xs:sequence maxOccurs=\"3\">" + EL_A + "</xs:sequence>",
      "<xs:sequence maxOccurs=\"2\">" + EL_A + "</xs:sequence>",
      "",
      true),
    c ("seq-group-occurs-wider",
      VER_BOTH,
      "<xs:sequence maxOccurs=\"2\">" + EL_A + EL_B + "</xs:sequence>",
      "<xs:sequence maxOccurs=\"3\">" + EL_A + EL_B + "</xs:sequence>",
      "",
      false),
    c ("all-drop-optional",
      VER_BOTH,
      all (EL_A, OPT_B, "<xs:element name=\"c\" minOccurs=\"0\"/>"),
      all (EL_A),
      "",
      true),
    c ("all-drop-required",
      VER_BOTH,
      all (EL_A, EL_B),
      all (EL_A),
      "",
      false),
    c ("all-add-new",
      VER_BOTH,
      all (EL_A, OPT_B),
      all (EL_A, EL_C),
      "",
      false),
    c ("choice-subset",
      VER_BOTH,
      choice (EL_A, EL_B, EL_C),
      choice (EL_A, EL_C),
      "",
      true),
    c ("choice-new-branch",
      VER_BOTH,
      choice (EL_A, EL_B),
      choice (EL_A, EL_C),
      "",
      false),
    c ("choice-occurs-wider",
      VER_BOTH,
      "<xs:choice maxOccurs=\"2\">" + EL_A + EL_B + "</xs:choice>",
      "<xs:choice maxOccurs=\"3\">" + EL_A + EL_B + "</xs:choice>",
      "",
      false),
    c ("choice-reorder-1.0",
      VER_10,
      choice (EL_A, EL_B, EL_C),
      choice (EL_C, EL_A),
      "",
      false),
    c ("choice-reorder-1.1",
      VER_11,
      choice (EL_A, EL_B, EL_C),
      choice (EL_C, EL_A),
      "",
      true),
    c ("seq-restricts-choice-sum-fits",
      VER_BOTH,
      "<xs:choice maxOccurs=\"2\">" + EL_A + EL_B + "</xs:choice>",
      seq (EL_A, EL_B),
      "",
      true),
    c ("seq-restricts-choice-sum-exceeds",
      VER_BOTH,
      choice (EL_A, EL_B),
      seq (EL_A, EL_B),
      "",
      false),
    c ("seq-restricts-choice-unmapped",
      VER_BOTH,
      "<xs:choice maxOccurs=\"2\">" + EL_A + EL_B + "</xs:choice>",
      seq (EL_A, EL_C),
      "",
      false),
    c ("elt-restricts-seq",
      VER_BOTH,
      seq (seq (EL_A, OPT_B), EL_C),
      seq (EL_A, EL_C),
      "",
      true),
    c ("elt-restricts-seq-missing-required",
      VER_BOTH,
      seq (seq (EL_A, EL_B), EL_C),
      seq (EL_A, EL_C),
      "",
      false),
    c ("elt-restricts-choice",
      VER_BOTH,
      seq (choice (EL_A, EL_B), EL_C),
      seq (EL_A, EL_C),
      "",
      true),
    c ("elt-restricts-choice-not-a-branch",
      VER_BOTH,
      seq (choice (EL_A, EL_B), EL_C),
      seq (EL_C, EL_C),
      "",
      false),
    c ("elt-restricts-repeating-choice",
      VER_BOTH,
      seq ("<xs:choice maxOccurs=\"unbounded\">" + EL_A + EL_B + "</xs:choice>"),
      seq ("<xs:element name=\"a\" maxOccurs=\"3\"/>"),
      "",
      true),
    c ("bare-element-in-restriction",
      VER_BOTH,
      seq (EL_A),
      EL_A,
      "",
      false),
    c ("choice-restricts-seq-1.0",
      VER_10,
      seq ("<xs:element name=\"a\" minOccurs=\"0\"/>", OPT_B),
      choice (EL_A, EL_B),
      "",
      false),
    c ("choice-restricts-seq-1.1",
      VER_11,
      seq ("<xs:element name=\"a\" minOccurs=\"0\"/>", OPT_B),
      choice (EL_A, EL_B),
      "",
      true),
    c ("empty-base-empty-derived",
      VER_BOTH,
      "",
      "",
      "",
      true),
    c ("empty-base-optional-elt",
      VER_BOTH,
      "",
      seq ("<xs:element name=\"a\" minOccurs=\"0\"/>"),
      "",
      false),
    c ("empty-base-empty-seq",
      VER_BOTH,
      "",
      "<xs:sequence/>",
      "",
      true),
    c ("emptiable-base-empty-derived",
      VER_BOTH,
      seq ("<xs:element name=\"a\" minOccurs=\"0\"/>"),
      "",
      "",
      true),
    c ("non-emptiable-base-empty-derived",
      VER_10,
      seq (EL_A),
      "",
      "",
      false),
    c ("non-emptiable-base-empty-derived",
      VER_11,
      seq (EL_A),
      "",
      "",
      false),
    c ("member-restricts-head",
      VER_BOTH,
      seq ("<xs:element ref=\"head\"/>"),
      seq ("<xs:element ref=\"member\"/>"),
      "<xs:element name=\"head\" type=\"xs:string\"/><xs:element name=\"member\" substitutionGroup=\"head\" type=\"xs:string\"/>",
      true),
    c ("non-member-restricts-head",
      VER_BOTH,
      seq ("<xs:element ref=\"head\"/>"),
      seq ("<xs:element ref=\"other\"/>"),
      "<xs:element name=\"head\" type=\"xs:string\"/><xs:element name=\"other\" type=\"xs:string\"/>",
      false)
  };

  private static final Case [] ATTRIBUTE_CASES = {
    new Case ("optional-to-required", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" use=\"required\"/>", ""), true),
    new Case ("optional-to-prohibited", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" use=\"prohibited\"/>", ""), true),
    new Case ("required-to-optional", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" use=\"required\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" use=\"optional\"/>", ""), false),
    new Case ("required-to-prohibited", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" use=\"required\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" use=\"prohibited\"/>", ""), false),
    new Case ("type-narrowed", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:decimal\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:integer\"/>", ""), true),
    new Case ("type-unrelated", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:decimal\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:date\"/>", ""), false),
    new Case ("fixed-dropped", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" fixed=\"x\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\"/>", ""), false),
    new Case ("fixed-kept", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" fixed=\"x\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" fixed=\"x\"/>", ""), true),
    new Case ("fixed-changed", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" fixed=\"x\"/>", "<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\" fixed=\"y\"/>", ""), false),
    new Case ("new-attr-no-wildcard", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:attribute name=\"a\" type=\"xs:string\"/>", "<xs:sequence/>" + "<xs:attribute name=\"b\" type=\"xs:string\"/>", ""), false),
    new Case ("new-attr-base-wildcard", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:anyAttribute processContents=\"lax\"/>", "<xs:sequence/>" + "<xs:attribute name=\"b\" type=\"xs:string\"/>", ""), true),
    new Case ("new-attr-wildcard-excludes", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:anyAttribute namespace=\"##other\" processContents=\"lax\"/>", "<xs:sequence/>" + "<xs:attribute name=\"b\" type=\"xs:string\"/>", ""), false),
    new Case ("wildcard-without-base", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "", "<xs:sequence/>" + "<xs:anyAttribute/>", ""), false),
    new Case ("wildcard-subset", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:anyAttribute processContents=\"lax\"/>", "<xs:sequence/>" + "<xs:anyAttribute namespace=\"##local\" processContents=\"lax\"/>", ""), true),
    new Case ("wildcard-superset", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:anyAttribute namespace=\"##local\" processContents=\"lax\"/>", "<xs:sequence/>" + "<xs:anyAttribute processContents=\"lax\"/>", ""), false),
    new Case ("wildcard-weaker-process", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:anyAttribute processContents=\"strict\"/>", "<xs:sequence/>" + "<xs:anyAttribute processContents=\"skip\"/>", ""), false),
    new Case ("wildcard-stronger-process", VER_BOTH, restrictionSchema ("<xs:sequence/>" + "<xs:anyAttribute processContents=\"skip\"/>", "<xs:sequence/>" + "<xs:anyAttribute processContents=\"strict\"/>", ""), true)
  };

  private static final Case [] CONTENT_KIND_CASES = {
    new Case ("mixed-base-element-only-derived", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\" mixed=\"true\">" + CK_EL_A + "</xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\">" + CK_EL_A + "</xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>", true),
    new Case ("element-only-base-mixed-derived", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\">" + CK_EL_A + "</xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent mixed=\"true\"><xs:restriction base=\"B\">" + CK_EL_A + "</xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>", false),
    new Case ("final-restriction", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\" final=\"restriction\">" + CK_EL_A + "</xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\">" + CK_EL_A + "</xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>", false),
    new Case ("final-extension-allows-restriction", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\" final=\"extension\">" + CK_EL_A + "</xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:restriction base=\"B\">" + CK_EL_A + "</xs:restriction></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>", true),
    new Case ("final-default-all", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" finalDefault=\"#all\">\n" +
"\t\t  <xs:complexType name=\"B\">" + CK_EL_A + "</xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:complexContent><xs:extension base=\"B\"/></xs:complexContent></xs:complexType>\n" +
"\t\t</xs:schema>", false),
    new Case ("simple-content-facet", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\"><xs:simpleContent><xs:extension base=\"xs:decimal\"><xs:attribute name=\"u\" type=\"xs:string\"/></xs:extension></xs:simpleContent></xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:simpleContent><xs:restriction base=\"B\"><xs:maxInclusive value=\"10\"/></xs:restriction></xs:simpleContent></xs:complexType>\n" +
"\t\t</xs:schema>", true),
    new Case ("simple-content-from-element-only", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
"\t\t  <xs:complexType name=\"B\">" + CK_EL_A + "</xs:complexType>\n" +
"\t\t  <xs:complexType name=\"D\"><xs:simpleContent><xs:restriction base=\"B\"><xs:maxInclusive value=\"10\"/></xs:restriction></xs:simpleContent></xs:complexType>\n" +
"\t\t</xs:schema>", false)
  };

  @Test
  public void testParticleRestriction ()
  {
    final Tally aTally = XsdTestSupport.runCases (PARTICLE_CASES, null, false);
    aTally.log ("particle restriction");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
    assertTrue (aTally.m_nDecided > 100);
  }

  /** derivation-ok-restriction clauses 2-4: attribute uses and wildcards. */
  @Test
  public void testAttributeRestriction ()
  {
    final Tally aTally = XsdTestSupport.runCases (ATTRIBUTE_CASES, null, false);
    aTally.log ("attribute restriction");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
  }

  /**
   * Content-type agreement of a restriction (derivation-ok-restriction 5) and
   * final on the base (cos-ct-derived-ok / src-ct.1 via final).
   */
  @Test
  public void testRestrictionContentKinds ()
  {
    final Tally aTally = XsdTestSupport.runCases (CONTENT_KIND_CASES, null, false);
    aTally.log ("restriction content kinds");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
  }

  /**
   * A restricted simple-content type validates instances against the restricted
   * value space (cvc-complex-type.2.2).
   */
  @Test
  public void testSimpleContentRestrictionInstances ()
  {
    final String sSchema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                           "  <xs:complexType name=\"B\"><xs:simpleContent><xs:extension base=\"xs:decimal\"><xs:attribute name=\"u\" type=\"xs:string\"/></xs:extension></xs:simpleContent></xs:complexType>\n" +
                           "  <xs:complexType name=\"D\"><xs:simpleContent><xs:restriction base=\"B\"><xs:maxInclusive value=\"10\"/><xs:attribute name=\"u\" type=\"xs:string\" use=\"required\"/></xs:restriction></xs:simpleContent></xs:complexType>\n" +
                           "  <xs:element name=\"v\" type=\"D\"/>\n" +
                           "</xs:schema>";
    final DocCheck [] aDocs = { new DocCheck ("<v u=\"m\">10</v>", true),
                                new DocCheck ("<v u=\"m\">10.5</v>", false),
                                new DocCheck ("<v>3</v>", false),
                                new DocCheck ("<v u=\"m\">x</v>", false) };
    for (final EXsdVersion eVersion : new EXsdVersion [] { EXsdVersion.V10, EXsdVersion.V11 })
    {
      final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, eVersion);
      if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
        continue;
      assertTrue (aCompiled.message (), aCompiled.verdict () == EVerdict.VALID);
      for (final DocCheck aDoc : aDocs)
      {
        final EVerdict eVerdict = XsdTestSupport.validate (aCompiled.schema (), aDoc.instance ());
        if (eVerdict != EVerdict.UNSUPPORTED)
          assertTrue (eVersion + " " + aDoc.instance (), (eVerdict == EVerdict.VALID) == aDoc.valid ());
      }
    }
  }
}
