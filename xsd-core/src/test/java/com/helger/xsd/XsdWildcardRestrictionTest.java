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
 * Wildcard namespace algebra in restrictions (1.0 section 3.10.6 Wildcard
 * Subset, 1.1 section 3.10.6.2 with notNamespace/notQName), xs:all mappings,
 * substitution groups and the 1.1-only language-inclusion routes (1.1 section
 * 3.4.6.4 Content type restricts). Ported from go-xslt
 * <code>wildcard_restriction_test.go</code>.
 *
 * @author Philip Helger
 */
public final class XsdWildcardRestrictionTest
{
  private static final int VER_BOTH = XsdTestSupport.VER_BOTH;
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;
  private static final String EL_A = "<xs:element name=\"a\" type=\"xs:string\"/>";
  private static final String EL_B = "<xs:element name=\"b\" type=\"xs:string\"/>";
  private static final String EL_C = "<xs:element name=\"c\" type=\"xs:string\"/>";
  private static final String SUB = "<xs:element name=\"h\" type=\"xs:string\"/><xs:element name=\"m1\" substitutionGroup=\"h\" type=\"xs:string\"/><xs:element name=\"m2\" substitutionGroup=\"h\" type=\"xs:string\"/>";

  private static String wrap (final String sGroup, final String sBody)
  {
    return "<xs:" + sGroup + ">" + sBody + "</xs:" + sGroup + ">";
  }

  private static String anyW (final String sAttrs)
  {
    return "<xs:any processContents=\"lax\" " + sAttrs + "/>";
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

  private static final Case [] CASES = {
    c ("not-ns-list-subset",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", anyW ("namespace=\"urn:y\"")),
      "",
      true),
    c ("not-ns-list-hits-excluded",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", anyW ("namespace=\"urn:x\"")),
      "",
      false),
    c ("not-ns-larger-exclusion",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", anyW ("notNamespace=\"urn:x urn:y\"")),
      "",
      true),
    c ("not-ns-other-exclusion",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", anyW ("notNamespace=\"urn:y\"")),
      "",
      false),
    c ("not-ns-vs-other",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      "",
      false),
    c ("not-local-vs-other",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"##local\"")),
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      "",
      true),
    c ("any-base-not-derived",
      VER_11,
      wrap ("sequence", anyW ("")),
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      "",
      true),
    c ("other-base-not-local-derived",
      VER_11,
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      wrap ("sequence", anyW ("notNamespace=\"##local\"")),
      "",
      true),
    c ("other-base-not-derived-too-wide",
      VER_11,
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      "",
      false),
    c ("list-base-not-derived",
      VER_11,
      wrap ("sequence", anyW ("namespace=\"urn:a\"")),
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      "",
      false),
    c ("not-ns-base-any-derived",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", anyW ("")),
      "",
      false),
    c ("elt-vs-not-ns",
      VER_11,
      wrap ("sequence", anyW ("notNamespace=\"urn:x\"")),
      wrap ("sequence", EL_A),
      "",
      true),
    c ("not-qname-dropped",
      VER_11,
      wrap ("sequence", anyW ("notQName=\"a\"")),
      wrap ("sequence", anyW ("")),
      "",
      false),
    c ("not-qname-kept",
      VER_11,
      wrap ("sequence", anyW ("notQName=\"a\"")),
      wrap ("sequence", anyW ("notQName=\"a b\"")),
      "",
      true),
    c ("elt-excluded-by-not-qname",
      VER_11,
      wrap ("sequence", anyW ("notQName=\"a\"")),
      wrap ("sequence", EL_A),
      "",
      false),
    c ("elt-not-excluded-by-not-qname",
      VER_11,
      wrap ("sequence", anyW ("notQName=\"b\"")),
      wrap ("sequence", EL_A),
      "",
      true),
    c ("other-vs-other",
      VER_BOTH,
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      "",
      true),
    c ("other-vs-list",
      VER_BOTH,
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      wrap ("sequence", anyW ("namespace=\"urn:a\"")),
      "",
      true),
    c ("other-vs-list-with-local",
      VER_BOTH,
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      wrap ("sequence", anyW ("namespace=\"urn:a ##local\"")),
      "",
      false),
    c ("list-vs-other",
      VER_BOTH,
      wrap ("sequence", anyW ("namespace=\"urn:a\"")),
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      "",
      false),
    c ("any-vs-other",
      VER_BOTH,
      wrap ("sequence", anyW ("")),
      wrap ("sequence", anyW ("namespace=\"##other\"")),
      "",
      true),
    c ("seq-restricts-all",
      VER_BOTH,
      wrap ("all", EL_A+"<xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("sequence", "<xs:element name=\"b\"/>"+EL_A),
      "",
      true),
    c ("seq-restricts-all-unmapped",
      VER_BOTH,
      wrap ("all", EL_A+"<xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("sequence", EL_A+EL_C),
      "",
      false),
    c ("seq-restricts-all-missing-required",
      VER_BOTH,
      wrap ("all", EL_A+EL_B),
      wrap ("sequence", EL_A),
      "",
      false),
    c ("all-restricts-seq",
      VER_10,
      wrap ("sequence", EL_A+EL_B),
      wrap ("all", EL_A+EL_B),
      "",
      false),
    c ("all-restricts-choice",
      VER_BOTH,
      wrap ("choice", EL_A+EL_B),
      wrap ("all", EL_A+"<xs:element name=\"b\" minOccurs=\"0\"/>"),
      "",
      false),
    c ("choice-restricts-all",
      VER_10,
      wrap ("all", "<xs:element name=\"a\" minOccurs=\"0\"/><xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("choice", EL_A+EL_B),
      "",
      false),
    c ("choice-restricts-all-1.1",
      VER_11,
      wrap ("all", "<xs:element name=\"a\" minOccurs=\"0\"/><xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("choice", EL_A+EL_B),
      "",
      true),
    c ("choice-restricts-all-1.1-branch-misses",
      VER_11,
      wrap ("all", "<xs:element name=\"a\" minOccurs=\"0\"/><xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("choice", EL_A+EL_C),
      "",
      false),
    c ("choice-over-run-1.1",
      VER_11,
      wrap ("sequence", "<xs:element name=\"a\" minOccurs=\"0\"/><xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("sequence", wrap ("choice", EL_A+EL_B)),
      "",
      true),
    c ("choice-over-run-1.0",
      VER_10,
      wrap ("sequence", "<xs:element name=\"a\" minOccurs=\"0\"/><xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("sequence", wrap ("choice", EL_A+EL_B)),
      "",
      false),
    c ("all-wildcard-narrowed",
      VER_11,
      wrap ("all", EL_A+anyW ("namespace=\"##other\"")),
      wrap ("all", EL_A+anyW ("namespace=\"urn:x\"")),
      "",
      true),
    c ("all-wildcard-widened",
      VER_11,
      wrap ("all", EL_A+anyW ("namespace=\"urn:x\"")),
      wrap ("all", EL_A+anyW ("namespace=\"##other\"")),
      "",
      false),
    c ("all-wildcard-union",
      VER_11,
      wrap ("all", anyW ("namespace=\"##local\" minOccurs=\"0\"")+anyW ("notNamespace=\"##local\" minOccurs=\"0\"")),
      wrap ("all", anyW ("minOccurs=\"0\"")),
      "",
      true),
    c ("all-wildcard-union-gap",
      VER_11,
      wrap ("all", anyW ("namespace=\"##local\" minOccurs=\"0\"")+anyW ("namespace=\"urn:x\" minOccurs=\"0\"")),
      wrap ("all", anyW ("minOccurs=\"0\"")),
      "",
      false),
    c ("all-members-fan-in",
      VER_11,
      wrap ("all", "<xs:element ref=\"h\" minOccurs=\"0\" maxOccurs=\"4\"/>"),
      wrap ("all", "<xs:element ref=\"m1\" minOccurs=\"0\" maxOccurs=\"2\"/><xs:element ref=\"m2\" minOccurs=\"0\" maxOccurs=\"2\"/>"),
      SUB,
      true),
    c ("all-members-fan-in-too-many",
      VER_11 + SKIP_BUG,
      wrap ("all", "<xs:element ref=\"h\" minOccurs=\"0\" maxOccurs=\"3\"/>"),
      wrap ("all", "<xs:element ref=\"m1\" minOccurs=\"0\" maxOccurs=\"2\"/><xs:element ref=\"m2\" minOccurs=\"0\" maxOccurs=\"2\"/>"),
      SUB,
      false),
    c ("choice-of-members-restricts-head",
      VER_BOTH,
      wrap ("sequence", "<xs:element ref=\"h\"/>"),
      wrap ("sequence", wrap ("choice", "<xs:element ref=\"m1\"/><xs:element ref=\"m2\"/>")),
      SUB,
      true),
    c ("choice-member-or-wildcard-vs-head",
      VER_BOTH,
      wrap ("sequence", "<xs:element ref=\"h\"/>"),
      wrap ("sequence", wrap ("choice", "<xs:element ref=\"m1\"/>"+anyW (""))),
      SUB,
      false),
    c ("blocked-member-restricts-head",
      VER_BOTH,
      wrap ("sequence", "<xs:element ref=\"h\"/>"),
      wrap ("sequence", "<xs:element ref=\"m1\"/>"),
      "<xs:element name=\"h\" type=\"xs:string\" block=\"substitution\"/><xs:element name=\"m1\" substitutionGroup=\"h\" type=\"xs:string\"/>",
      false),
    c ("wildcard-absorbs-run",
      VER_BOTH,
      wrap ("sequence", anyW ("minOccurs=\"2\" maxOccurs=\"3\"")),
      wrap ("sequence", EL_A+EL_B),
      "",
      true),
    c ("wildcard-absorbs-run-too-few",
      VER_BOTH,
      wrap ("sequence", anyW ("minOccurs=\"3\" maxOccurs=\"3\"")),
      wrap ("sequence", EL_A+EL_B),
      "",
      false),
    c ("wildcard-absorbs-run-too-many",
      VER_BOTH,
      wrap ("sequence", anyW ("minOccurs=\"1\" maxOccurs=\"2\"")),
      wrap ("sequence", EL_A+EL_B+EL_C),
      "",
      false),
    c ("choice-of-groups-vs-wildcard",
      VER_BOTH,
      wrap ("sequence", anyW ("maxOccurs=\"2\"")),
      wrap ("sequence", wrap ("choice", wrap ("sequence", EL_A+EL_B)+EL_C)),
      "",
      true),
    c ("choice-of-groups-vs-wildcard-unbounded",
      VER_BOTH,
      wrap ("sequence", anyW ("maxOccurs=\"2\"")),
      wrap ("sequence", wrap ("choice", "<xs:element name=\"a\" maxOccurs=\"unbounded\"/>"+EL_C)),
      "",
      false),
    c ("removed-particle",
      VER_BOTH,
      wrap ("sequence", EL_A+"<xs:element name=\"b\" minOccurs=\"0\"/>"),
      wrap ("sequence", EL_A+"<xs:element name=\"b\" minOccurs=\"0\" maxOccurs=\"0\"/>"),
      "",
      true),
    c ("removed-choice-branch",
      VER_BOTH,
      wrap ("choice", EL_A+EL_B),
      wrap ("choice", EL_A+"<xs:element name=\"b\" minOccurs=\"0\" maxOccurs=\"0\"/>"),
      "",
      true)
  };

  @Test
  public void testWildcardAndAllRestriction ()
  {
    final Tally aTally = XsdTestSupport.runCases (CASES, null, false);
    aTally.log ("wildcard and all restriction");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
    assertTrue (aTally.m_nDecided > 50);
  }
}
