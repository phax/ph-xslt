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
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Case;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Facets (go-xslt <code>facet_constraints_test.go</code>): the schema
 * constraints on facets (Datatypes 1.0 and 1.1, section 4.3) and the facet
 * checks of values. The value checks run directly on the simple type of the
 * compiled schema, so they are decided without the instance validator.
 *
 * @author Philip Helger
 */
public final class XsdFacetTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (XsdFacetTest.class);
  private static final int VER_BOTH = XsdTestSupport.VER_BOTH;
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;

  /**
   * A value check.
   *
   * @param name
   *        The case name.
   * @param ver
   *        The version selector.
   * @param typ
   *        The body of the simple type <code>vt</code>.
   * @param value
   *        The raw value.
   * @param valid
   *        The expected verdict.
   */
  private record ValCase (@NonNull String name, int ver, @NonNull String typ, @NonNull String value, boolean valid)
  {}

  /** Build a base type B (restriction of a built-in) and D restricting B. */
  @NonNull
  private static String two (@NonNull final String sBuiltin,
                             @NonNull final String sBaseFacets,
                             @NonNull final String sDerivedFacets)
  {
    return "<xs:simpleType name=\"B\"><xs:restriction base=\"" +
           sBuiltin +
           "\">" +
           sBaseFacets +
           "</xs:restriction></xs:simpleType>" +
           "<xs:simpleType name=\"D\"><xs:restriction base=\"B\">" +
           sDerivedFacets +
           "</xs:restriction></xs:simpleType>";
  }

  @NonNull
  private static String one (@NonNull final String sBuiltin, @NonNull final String sFacets)
  {
    return "<xs:simpleType name=\"D\"><xs:restriction base=\"" +
           sBuiltin +
           "\">" +
           sFacets +
           "</xs:restriction></xs:simpleType>";
  }

  private static final Case [] CASES = {
    // cos-applicable-facets.
    new Case ("length-on-string", VER_BOTH, one("xs:string", "<xs:length value=\"3\"/>"), true),
    new Case ("length-on-hexBinary", VER_BOTH, one("xs:hexBinary", "<xs:length value=\"3\"/>"), true),
    new Case ("length-on-int", VER_BOTH, one("xs:int", "<xs:length value=\"3\"/>"), false),
    new Case ("totalDigits-on-string", VER_BOTH, one("xs:string", "<xs:totalDigits value=\"3\"/>"), false),
    new Case ("fractionDigits-on-double", VER_BOTH, one("xs:double", "<xs:fractionDigits value=\"1\"/>"), false),
    new Case ("maxInclusive-on-boolean", VER_BOTH, one("xs:boolean", "<xs:maxInclusive value=\"true\"/>"), false),
    new Case ("maxInclusive-on-date", VER_BOTH, one("xs:date", "<xs:maxInclusive value=\"2020-01-01\"/>"), true),
    new Case ("pattern-on-boolean", VER_BOTH, one("xs:boolean", "<xs:pattern value=\"true\"/>"), true),
    new Case ("explicitTimezone-1.1", VER_11, one("xs:dateTime", "<xs:explicitTimezone value=\"required\"/>"), true),
    new Case ("explicitTimezone-on-string", VER_11, one("xs:string", "<xs:explicitTimezone value=\"required\"/>"), false),
    new Case ("explicitTimezone-1.0", VER_10 + SKIP_BUG, one("xs:dateTime", "<xs:explicitTimezone value=\"required\"/>"), false),
    new Case ("explicitTimezone-bad-value", VER_11, one("xs:dateTime", "<xs:explicitTimezone value=\"sometimes\"/>"), false),
    new Case ("unknown-facet", VER_BOTH, one("xs:string", "<xs:maxSize value=\"3\"/>"), false),
    // Facet values must be in the facet's value space.
    new Case ("length-negative", VER_BOTH, one("xs:string", "<xs:length value=\"-1\"/>"), false),
    new Case ("length-not-integer", VER_BOTH, one("xs:string", "<xs:length value=\"x\"/>"), false),
    new Case ("totalDigits-zero", VER_BOTH, one("xs:decimal", "<xs:totalDigits value=\"0\"/>"), false),
    new Case ("maxInclusive-not-of-type", VER_BOTH, one("xs:int", "<xs:maxInclusive value=\"abc\"/>"), false),
    new Case ("whiteSpace-bad", VER_BOTH + SKIP_BUG, one("xs:string", "<xs:whiteSpace value=\"trim\"/>"), false),
    new Case ("pattern-bad-regex", VER_BOTH, one("xs:string", "<xs:pattern value=\"a(b\"/>"), false),
    new Case ("enumeration-not-of-type", VER_BOTH, one("xs:int", "<xs:enumeration value=\"x\"/>"), false),
    new Case ("facet-twice", VER_BOTH, one("xs:string", "<xs:maxLength value=\"3\"/><xs:maxLength value=\"4\"/>"), false),
    // Co-occurrence within one step.
    new Case ("minLength-gt-maxLength", VER_BOTH, one("xs:string", "<xs:minLength value=\"5\"/><xs:maxLength value=\"4\"/>"), false),
    new Case ("minLength-le-maxLength", VER_BOTH, one("xs:string", "<xs:minLength value=\"4\"/><xs:maxLength value=\"4\"/>"), true),
    new Case ("length-with-minLength", VER_BOTH, one("xs:string", "<xs:length value=\"4\"/><xs:minLength value=\"4\"/>"), false),
    new Case ("length-with-maxLength", VER_BOTH, one("xs:string", "<xs:length value=\"4\"/><xs:maxLength value=\"5\"/>"), false),
    new Case ("maxInclusive-and-maxExclusive", VER_BOTH, one("xs:int", "<xs:maxInclusive value=\"4\"/><xs:maxExclusive value=\"5\"/>"), false),
    new Case ("minInclusive-and-minExclusive", VER_BOTH, one("xs:int", "<xs:minInclusive value=\"4\"/><xs:minExclusive value=\"3\"/>"), false),
    new Case ("min-gt-max", VER_BOTH, one("xs:int", "<xs:minInclusive value=\"5\"/><xs:maxInclusive value=\"4\"/>"), false),
    new Case ("minExclusive-eq-maxExclusive", VER_BOTH, one("xs:decimal", "<xs:minExclusive value=\"4\"/><xs:maxExclusive value=\"4\"/>"), false),
    new Case ("minInclusive-eq-maxInclusive", VER_BOTH, one("xs:decimal", "<xs:minInclusive value=\"4\"/><xs:maxInclusive value=\"4\"/>"), true),
    new Case ("minExclusive-ge-maxInclusive", VER_BOTH, one("xs:decimal", "<xs:minExclusive value=\"5\"/><xs:maxInclusive value=\"4\"/>"), false),
    new Case ("fractionDigits-gt-totalDigits", VER_BOTH, one("xs:decimal", "<xs:totalDigits value=\"2\"/><xs:fractionDigits value=\"3\"/>"), false),
    new Case ("fractionDigits-on-integer-nonzero", VER_BOTH, one("xs:integer", "<xs:fractionDigits value=\"1\"/>"), false),
    new Case ("date-bounds-ok", VER_BOTH, one("xs:date", "<xs:minInclusive value=\"2000-01-01\"/><xs:maxExclusive value=\"2001-01-01\"/>"), true),
    new Case ("date-bounds-crossed", VER_BOTH, one("xs:date", "<xs:minInclusive value=\"2001-01-01\"/><xs:maxExclusive value=\"2000-01-01\"/>"), false),
    new Case ("duration-bounds-crossed", VER_BOTH, one("xs:duration", "<xs:minInclusive value=\"P2D\"/><xs:maxInclusive value=\"P1D\"/>"), false),
    new Case ("float-bounds-crossed", VER_BOTH, one("xs:float", "<xs:minInclusive value=\"2.5\"/><xs:maxInclusive value=\"1E0\"/>"), false),
    // Restriction steps (*-valid-restriction).
    new Case ("maxLength-widened", VER_BOTH, two("xs:string", "<xs:maxLength value=\"3\"/>", "<xs:maxLength value=\"4\"/>"), false),
    new Case ("maxLength-narrowed", VER_BOTH, two("xs:string", "<xs:maxLength value=\"4\"/>", "<xs:maxLength value=\"3\"/>"), true),
    new Case ("minLength-widened", VER_BOTH, two("xs:string", "<xs:minLength value=\"3\"/>", "<xs:minLength value=\"2\"/>"), false),
    new Case ("maxLength-below-base-minLength", VER_BOTH, two("xs:string", "<xs:minLength value=\"3\"/>", "<xs:maxLength value=\"2\"/>"), false),
    new Case ("length-changed", VER_BOTH + SKIP_BUG, two("xs:string", "<xs:length value=\"3\"/>", "<xs:length value=\"4\"/>"), false),
    new Case ("totalDigits-widened", VER_BOTH, two("xs:decimal", "<xs:totalDigits value=\"3\"/>", "<xs:totalDigits value=\"4\"/>"), false),
    new Case ("fractionDigits-widened", VER_BOTH, two("xs:decimal", "<xs:fractionDigits value=\"2\"/>", "<xs:fractionDigits value=\"3\"/>"), false),
    new Case ("whiteSpace-weakened", VER_BOTH, two("xs:string", "<xs:whiteSpace value=\"collapse\"/>", "<xs:whiteSpace value=\"preserve\"/>"), false),
    new Case ("whiteSpace-strengthened", VER_BOTH, two("xs:string", "<xs:whiteSpace value=\"replace\"/>", "<xs:whiteSpace value=\"collapse\"/>"), true),
    new Case ("whiteSpace-builtin-token-preserve", VER_BOTH, one("xs:token", "<xs:whiteSpace value=\"preserve\"/>"), false),
    new Case ("maxInclusive-widened", VER_BOTH, two("xs:int", "<xs:maxInclusive value=\"10\"/>", "<xs:maxInclusive value=\"11\"/>"), false),
    new Case ("maxInclusive-vs-base-maxExclusive", VER_BOTH, two("xs:int", "<xs:maxExclusive value=\"10\"/>", "<xs:maxInclusive value=\"10\"/>"), false),
    new Case ("maxExclusive-vs-base-maxInclusive", VER_BOTH, two("xs:int", "<xs:maxInclusive value=\"10\"/>", "<xs:maxExclusive value=\"10\"/>"), true),
    new Case ("minInclusive-widened", VER_BOTH, two("xs:int", "<xs:minInclusive value=\"10\"/>", "<xs:minInclusive value=\"9\"/>"), false),
    new Case ("minExclusive-narrowed", VER_BOTH, two("xs:int", "<xs:minInclusive value=\"10\"/>", "<xs:minExclusive value=\"10\"/>"), true),
    new Case ("upper-below-base-lower", VER_BOTH, two("xs:int", "<xs:minInclusive value=\"10\"/>", "<xs:maxInclusive value=\"5\"/>"), false),
    new Case ("lower-above-base-upper", VER_BOTH, two("xs:int", "<xs:maxInclusive value=\"10\"/>", "<xs:minInclusive value=\"11\"/>"), false),
    new Case ("upper-empties-builtin", VER_BOTH, one("xs:positiveInteger", "<xs:maxInclusive value=\"0\"/>"), false),
    new Case ("lower-empties-builtin", VER_BOTH, one("xs:byte", "<xs:minInclusive value=\"128\"/>"), false),
    new Case ("fixed-facet-changed", VER_BOTH, two("xs:string", "<xs:maxLength value=\"4\" fixed=\"true\"/>", "<xs:maxLength value=\"3\"/>"), false),
    new Case ("fixed-facet-same", VER_BOTH, two("xs:string", "<xs:maxLength value=\"4\" fixed=\"true\"/>", "<xs:maxLength value=\"4\"/>"), true),
    new Case ("fixed-bound-changed", VER_BOTH, two("xs:int", "<xs:maxInclusive value=\"10\" fixed=\"true\"/>", "<xs:maxInclusive value=\"9\"/>"), false),
    new Case ("builtin-fixed-whitespace", VER_BOTH, one("xs:integer", "<xs:whiteSpace value=\"replace\"/>"), false),
    new Case ("enumeration-outside-base", VER_BOTH + SKIP_BUG, two("xs:int", "<xs:maxInclusive value=\"10\"/>", "<xs:enumeration value=\"11\"/>"), false),
    new Case ("enumeration-inside-base", VER_BOTH, two("xs:int", "<xs:maxInclusive value=\"10\"/>", "<xs:enumeration value=\"9\"/>"), true),
    new Case ("explicitTimezone-loosened", VER_11, two("xs:dateTime", "<xs:explicitTimezone value=\"required\"/>", "<xs:explicitTimezone value=\"optional\"/>"), false),
    new Case ("explicitTimezone-stamp-prohibited", VER_11, one("xs:dateTimeStamp", "<xs:explicitTimezone value=\"prohibited\"/>"), false),
    new Case ("explicitTimezone-pinned-from-optional", VER_11, two("xs:dateTime", "<xs:explicitTimezone value=\"optional\"/>", "<xs:explicitTimezone value=\"prohibited\"/>"), true),
    // Lists: length facets count items, enumeration literals are lists.
    new Case ("list-length", VER_BOTH, "<xs:simpleType name=\"L\"><xs:list itemType=\"xs:int\"/></xs:simpleType>" + "<xs:simpleType name=\"D\"><xs:restriction base=\"L\"><xs:length value=\"2\"/></xs:restriction></xs:simpleType>", true),
    new Case ("list-maxInclusive", VER_BOTH, "<xs:simpleType name=\"L\"><xs:list itemType=\"xs:int\"/></xs:simpleType>" + "<xs:simpleType name=\"D\"><xs:restriction base=\"L\"><xs:maxInclusive value=\"2\"/></xs:restriction></xs:simpleType>", false),
    new Case ("list-enumeration-invalid-item", VER_BOTH, "<xs:simpleType name=\"L\"><xs:list itemType=\"xs:int\"/></xs:simpleType>" + "<xs:simpleType name=\"D\"><xs:restriction base=\"L\"><xs:enumeration value=\"1 x\"/></xs:restriction></xs:simpleType>", false),
    new Case ("nmtokens-empty-enumeration", VER_BOTH, one("xs:NMTOKENS", "<xs:enumeration value=\"\"/>"), false),
    new Case ("nmtokens-maxLength-zero", VER_BOTH, one("xs:NMTOKENS", "<xs:maxLength value=\"0\"/>"), false),
    new Case ("nmtokens-length-with-inherited-minLength", VER_BOTH, one("xs:NMTOKENS", "<xs:length value=\"2\"/>"), true),
    // Unions accept only pattern, enumeration (and 1.1 assertions).
    new Case ("union-pattern", VER_BOTH, "<xs:simpleType name=\"U\"><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType><xs:simpleType name=\"D\"><xs:restriction base=\"U\"><xs:pattern value=\"\\d+\"/></xs:restriction></xs:simpleType>", true),
    new Case ("union-maxLength", VER_BOTH, "<xs:simpleType name=\"U\"><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType><xs:simpleType name=\"D\"><xs:restriction base=\"U\"><xs:maxLength value=\"3\"/></xs:restriction></xs:simpleType>", false),
    new Case ("union-enumeration-no-member", VER_BOTH + SKIP_BUG, "<xs:simpleType name=\"U\"><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType><xs:simpleType name=\"D\"><xs:restriction base=\"U\"><xs:enumeration value=\"x\"/></xs:restriction></xs:simpleType>", false),
    // Value constraints checked against the facets.
    new Case ("default-violates-facet", VER_BOTH, one("xs:string", "<xs:maxLength value=\"2\"/>") + "<xs:element name=\"e\" type=\"D\" default=\"abc\"/>", false),
    new Case ("fixed-satisfies-facet", VER_BOTH, one("xs:string", "<xs:maxLength value=\"3\"/>") + "<xs:attribute name=\"a\" type=\"D\" fixed=\"abc\"/>", true),
  };

  private static final ValCase [] VALUE_CASES = {
    new ValCase ("string-length-ok", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:length value=\"3\"/></xs:restriction>", "abc", true),
    new ValCase ("string-length-chars-not-bytes", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:length value=\"3\"/></xs:restriction>", "äöü", true),
    new ValCase ("string-length-bad", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:length value=\"3\"/></xs:restriction>", "ab", false),
    new ValCase ("string-minLength-bad", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:minLength value=\"3\"/></xs:restriction>", "ab", false),
    new ValCase ("hex-length-octets", VER_BOTH, "<xs:restriction base=\"xs:hexBinary\"><xs:length value=\"2\"/></xs:restriction>", "0aFF", true),
    new ValCase ("hex-length-octets-bad", VER_BOTH, "<xs:restriction base=\"xs:hexBinary\"><xs:length value=\"2\"/></xs:restriction>", "0aFF00", false),
    new ValCase ("base64-maxLength-octets", VER_BOTH, "<xs:restriction base=\"xs:base64Binary\"><xs:maxLength value=\"3\"/></xs:restriction>", "AQID", true),
    new ValCase ("base64-maxLength-octets-bad", VER_BOTH, "<xs:restriction base=\"xs:base64Binary\"><xs:maxLength value=\"2\"/></xs:restriction>", "AQID", false),
    new ValCase ("anyURI-length", VER_BOTH, "<xs:restriction base=\"xs:anyURI\"><xs:maxLength value=\"5\"/></xs:restriction>", "a/b", true),
    new ValCase ("collapse-before-length", VER_BOTH, "<xs:restriction base=\"xs:token\"><xs:length value=\"3\"/></xs:restriction>", "  a b  ", true),
    new ValCase ("preserve-length-counts-spaces", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:length value=\"3\"/></xs:restriction>", " ab", true),
    new ValCase ("replace-whitespace", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:whiteSpace value=\"replace\"/><xs:pattern value=\"a b\"/></xs:restriction>", "a\tb", true),
    new ValCase ("pattern-anchored", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:pattern value=\"ab\"/></xs:restriction>", "xaby", false),
    new ValCase ("pattern-multiple-or", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:pattern value=\"a\"/><xs:pattern value=\"b\"/></xs:restriction>", "b", true),
    new ValCase ("pattern-steps-and", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:pattern value=\"a.\"/></xs:restriction>", "ab", true),
    new ValCase ("pattern-class-subtraction", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:pattern value=\"[a-z-[aeiou]]+\"/></xs:restriction>", "bcd", true),
    new ValCase ("pattern-class-subtraction-bad", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:pattern value=\"[a-z-[aeiou]]+\"/></xs:restriction>", "bad", false),
    new ValCase ("pattern-name-escape", VER_BOTH, "<xs:restriction base=\"xs:string\"><xs:pattern value=\"\\i\\c*\"/></xs:restriction>", "_x1", true),
    new ValCase ("enumeration-value-space-decimal", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:enumeration value=\"1.0\"/></xs:restriction>", "01.00", true),
    new ValCase ("enumeration-value-space-float-nan", VER_BOTH, "<xs:restriction base=\"xs:float\"><xs:enumeration value=\"NaN\"/></xs:restriction>", "NaN", true),
    new ValCase ("enumeration-qname-value", VER_BOTH, "<xs:restriction base=\"xs:QName\"><xs:enumeration value=\"foo\"/></xs:restriction>", " foo ", true),
    new ValCase ("enumeration-boolean", VER_BOTH, "<xs:restriction base=\"xs:boolean\"><xs:enumeration value=\"true\"/></xs:restriction>", "1", true),
    new ValCase ("maxInclusive-decimal", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:maxInclusive value=\"10.5\"/></xs:restriction>", "10.50", true),
    new ValCase ("maxExclusive-decimal", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:maxExclusive value=\"10.5\"/></xs:restriction>", "10.5", false),
    new ValCase ("minExclusive-double", VER_BOTH, "<xs:restriction base=\"xs:double\"><xs:minExclusive value=\"0\"/></xs:restriction>", "1E-300", true),
    new ValCase ("minExclusive-double-zero", VER_BOTH, "<xs:restriction base=\"xs:double\"><xs:minExclusive value=\"0\"/></xs:restriction>", "-0", false),
    new ValCase ("maxInclusive-double-inf", VER_BOTH, "<xs:restriction base=\"xs:double\"><xs:maxInclusive value=\"1E10\"/></xs:restriction>", "INF", false),
    new ValCase ("maxInclusive-double-nan", VER_BOTH, "<xs:restriction base=\"xs:double\"><xs:maxInclusive value=\"1E10\"/></xs:restriction>", "NaN", false),
    new ValCase ("totalDigits-ok", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:totalDigits value=\"3\"/></xs:restriction>", "12.3", true),
    new ValCase ("totalDigits-trailing-zeros", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:totalDigits value=\"3\"/></xs:restriction>", "12.300", true),
    new ValCase ("totalDigits-leading-zeros", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:totalDigits value=\"2\"/></xs:restriction>", "0012", true),
    new ValCase ("totalDigits-bad", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:totalDigits value=\"3\"/></xs:restriction>", "1234", false),
    new ValCase ("fractionDigits-ok", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:fractionDigits value=\"2\"/></xs:restriction>", "1.20", true),
    new ValCase ("fractionDigits-bad", VER_BOTH, "<xs:restriction base=\"xs:decimal\"><xs:fractionDigits value=\"2\"/></xs:restriction>", "1.234", false),
    new ValCase ("date-maxInclusive", VER_BOTH, "<xs:restriction base=\"xs:date\"><xs:maxInclusive value=\"2020-12-31\"/></xs:restriction>", "2020-12-31", true),
    new ValCase ("date-maxInclusive-bad", VER_BOTH, "<xs:restriction base=\"xs:date\"><xs:maxInclusive value=\"2020-12-31\"/></xs:restriction>", "2021-01-01", false),
    new ValCase ("dateTime-timezone-compare", VER_BOTH, "<xs:restriction base=\"xs:dateTime\"><xs:maxInclusive value=\"2020-01-01T12:00:00Z\"/></xs:restriction>", "2020-01-01T13:00:00+01:00", true),
    new ValCase ("dateTime-timezone-compare-bad", VER_BOTH, "<xs:restriction base=\"xs:dateTime\"><xs:maxInclusive value=\"2020-01-01T12:00:00Z\"/></xs:restriction>", "2020-01-01T13:00:00+00:30", false),
    // An indeterminate comparison (no timezone vs timezone ±14h) fails
    // the facet (Datatypes §3.3.8.4 order relation is partial).
    new ValCase ("dateTime-indeterminate", VER_BOTH, "<xs:restriction base=\"xs:dateTime\"><xs:maxInclusive value=\"2020-01-01T12:00:00Z\"/></xs:restriction>", "2020-01-01T12:00:00", false),
    new ValCase ("time-minExclusive", VER_BOTH, "<xs:restriction base=\"xs:time\"><xs:minExclusive value=\"10:00:00\"/></xs:restriction>", "10:00:01", true),
    new ValCase ("gYear-maxExclusive", VER_BOTH, "<xs:restriction base=\"xs:gYear\"><xs:maxExclusive value=\"2000\"/></xs:restriction>", "1999", true),
    new ValCase ("gYearMonth-bound-bad", VER_BOTH, "<xs:restriction base=\"xs:gYearMonth\"><xs:minInclusive value=\"2000-05\"/></xs:restriction>", "2000-04", false),
    new ValCase ("duration-max", VER_BOTH, "<xs:restriction base=\"xs:duration\"><xs:maxInclusive value=\"P1Y\"/></xs:restriction>", "P12M", true),
    new ValCase ("duration-max-bad", VER_BOTH, "<xs:restriction base=\"xs:duration\"><xs:maxInclusive value=\"P1Y\"/></xs:restriction>", "P13M", false),
    new ValCase ("dayTimeDuration-min", VER_11, "<xs:restriction base=\"xs:dayTimeDuration\"><xs:minInclusive value=\"PT1H\"/></xs:restriction>", "PT59M", false),
    new ValCase ("explicitTimezone-required-bad", VER_11, "<xs:restriction base=\"xs:date\"><xs:explicitTimezone value=\"required\"/></xs:restriction>", "2020-01-01", false),
    new ValCase ("explicitTimezone-required-ok", VER_11, "<xs:restriction base=\"xs:date\"><xs:explicitTimezone value=\"required\"/></xs:restriction>", "2020-01-01Z", true),
    new ValCase ("explicitTimezone-prohibited-bad", VER_11, "<xs:restriction base=\"xs:time\"><xs:explicitTimezone value=\"prohibited\"/></xs:restriction>", "10:00:00+02:00", false),
    new ValCase ("dateTimeStamp-needs-tz", VER_11, "<xs:restriction base=\"xs:dateTimeStamp\"/>", "2020-01-01T00:00:00", false),
    new ValCase ("list-length-items", VER_BOTH, "<xs:restriction><xs:simpleType><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:length value=\"2\"/></xs:restriction>", " 1   2 ", true),
    new ValCase ("list-length-items-bad", VER_BOTH, "<xs:restriction><xs:simpleType><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:length value=\"2\"/></xs:restriction>", "1 2 3", false),
    new ValCase ("list-item-invalid", VER_BOTH, "<xs:list itemType=\"xs:int\"/>", "1 x", false),
    new ValCase ("list-enumeration-whole-list", VER_BOTH, "<xs:restriction><xs:simpleType><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:enumeration value=\"1 2\"/></xs:restriction>", "01 2", true),
    new ValCase ("list-pattern-whole-string", VER_BOTH, "<xs:restriction><xs:simpleType><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:pattern value=\"\\d( \\d)*\"/></xs:restriction>", "1 2", true),
    new ValCase ("list-of-union", VER_BOTH, "<xs:list><xs:simpleType><xs:union memberTypes=\"xs:int xs:boolean\"/></xs:simpleType></xs:list>", "1 true 3", true),
    new ValCase ("list-of-union-bad", VER_BOTH, "<xs:list><xs:simpleType><xs:union memberTypes=\"xs:int xs:boolean\"/></xs:simpleType></xs:list>", "1 maybe", false),
    new ValCase ("union-member-match", VER_BOTH, "<xs:union memberTypes=\"xs:int xs:date\"/>", "2020-01-01", true),
    new ValCase ("union-no-member", VER_BOTH, "<xs:union memberTypes=\"xs:int xs:date\"/>", "x", false),
    new ValCase ("union-restricted-enum", VER_BOTH, "<xs:restriction><xs:simpleType><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType><xs:enumeration value=\"1\"/><xs:enumeration value=\"2020-01-01\"/></xs:restriction>", "2020-01-01", true),
    new ValCase ("union-restricted-enum-miss", VER_BOTH, "<xs:restriction><xs:simpleType><xs:union memberTypes=\"xs:int xs:date\"/></xs:simpleType><xs:enumeration value=\"1\"/></xs:restriction>", "2", false),
    new ValCase ("union-of-list", VER_BOTH, "<xs:union><xs:simpleType><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:simpleType><xs:restriction base=\"xs:string\"><xs:enumeration value=\"none\"/></xs:restriction></xs:simpleType></xs:union>", "1 2", true),
    new ValCase ("assertion-on-simple-1.1", VER_11, "<xs:restriction base=\"xs:int\"><xs:assertion test=\"$value mod 2 = 0\"/></xs:restriction>", "4", true),
    new ValCase ("assertion-on-simple-1.1-bad", VER_11, "<xs:restriction base=\"xs:int\"><xs:assertion test=\"$value mod 2 = 0\"/></xs:restriction>", "5", false),
    new ValCase ("assertion-on-list-1.1", VER_11, "<xs:restriction><xs:simpleType><xs:list itemType=\"xs:int\"/></xs:simpleType><xs:assertion test=\"count($value) = 2\"/></xs:restriction>", "1 2", true),
  };

  @Test
  public void testFacetConstraints ()
  {
    final Tally aTally = XsdTestSupport.runCases (CASES, null, true);
    aTally.log ("facet constraints");
    assertTrue (aTally.m_aWrong.toString (), aTally.m_aWrong.isEmpty ());
  }

  @Test
  public void testFacetValidation ()
  {
    final ICommonsList <String> aWrong = new CommonsArrayList <> ();
    int nDecided = 0;
    int nUndecided = 0;
    for (final ValCase aCase : VALUE_CASES)
    {
      if (aCase.ver () >= SKIP_BUG)
        continue;
      for (final EXsdVersion eVersion : XsdTestSupport.versionsOf (aCase.ver ()))
      {
        final String sSchema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                               "<xs:element name=\"v\" type=\"vt\"/><xs:simpleType name=\"vt\">" +
                               aCase.typ () +
                               "</xs:simpleType></xs:schema>";
        final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, eVersion);
        final String sLabel = aCase.name () + "/" + eVersion.getID ();
        if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
        {
          nUndecided++;
          continue;
        }
        if (aCompiled.verdict () != EVerdict.VALID)
        {
          aWrong.add (sLabel + ": schema does not compile: " + aCompiled.message ());
          continue;
        }
        final XsdSimpleType aType = (XsdSimpleType) aCompiled.schema ().m_aTypes.get (new XsdName ("", "vt"));
        boolean bValid = true;
        try
        {
          XsdSimpleTypeValidator.validate (aType, aCase.value ());
        }
        catch (final XsdInvalidException ex)
        {
          bValid = false;
        }
        catch (final XsdUnsupportedException ex)
        {
          nUndecided++;
          continue;
        }
        nDecided++;
        if (bValid != aCase.valid ())
          aWrong.add (sLabel + ": value '" + aCase.value () + "' valid=" + bValid + ", want " + aCase.valid ());
      }
    }
    LOGGER.info ("facet validation: " + nDecided + " decided, " + nUndecided + " not decidable yet");
    assertTrue (aWrong.toString (), aWrong.isEmpty ());
    assertTrue (nDecided > 100);
  }

  @Test
  public void testAbsentType ()
  {
    try
    {
      XsdSimpleTypeValidator.validate (XsdSimpleType.ABSENT, "x");
      assertTrue ("absent type must refuse every value", false);
    }
    catch (final XsdInvalidException ex)
    {
      assertEquals ("cvc-type", ex.getError ().code ());
    }
  }
}
