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
package com.helger.xpath.spec;

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

/**
 * Specification tests of the casting table, ported from go-xslt's <code>cast_table_test.go</code>.
 *
 * @author Philip Helger
 */
public final class CastTableSpecTest
{


  /**
   * TestTyCastingTable walks the F&amp;O 3.1 §19.1 casting table: each row is a
   * source type, each column a target, and the cell says whether the cast is
   * always allowed (Y), never allowed (N → XPTY0004), or allowed subject to
   * the value (M → FORG0001/FOCA0002 when the value does not fit). The
   * expected results are the canonical lexical forms of §19.1.1–§19.1.6 and
   * XSD 1.1 Part 2.
   */
  @Test
  public void tyCastingTable ()
  {
    run (env (),
    // §19.1.2.1 casting to xs:string / xs:untypedAtomic: always allowed,
    // the canonical lexical form of the value.
         ok ("string(xs:double('1e3') cast as xs:untypedAtomic)", "1000"),
         ok ("(3.50 cast as xs:untypedAtomic) instance of xs:untypedAtomic", "true"),
         ok ("xs:hexBinary('0aff') cast as xs:string", "0AFF"),
         ok ("xs:duration('P1Y13M') cast as xs:string", "P2Y1M"),

    // §19.1.2.2 casting to numeric types.
         ok ("true() cast as xs:double", "1"),
         ok ("false() cast as xs:float", "0"),
         ok ("true() cast as xs:decimal", "1"),
         ok ("false() cast as xs:decimal", "0"),
         ok ("true() cast as xs:integer", "1"),
         ok ("false() cast as xs:integer", "0"),
         ok ("xs:untypedAtomic(' 12 ') cast as xs:integer", "12"),
         ok ("'INF' cast as xs:double", "INF"),
         ok ("'-INF' cast as xs:double", "-INF"),
         ok ("'NaN' cast as xs:float", "NaN"),
         err ("'Infinity' cast as xs:double", "FORG0001"),
         err ("'abc' cast as xs:double", "FORG0001"),
         ok ("'1.5e2' cast as xs:double", "150"),
         ok ("xs:float('1.5') cast as xs:double", "1.5"),
         ok ("1.5 cast as xs:float", "1.5"),
         ok ("xs:double('-7.9') cast as xs:integer", "-7"),
         ok ("xs:float('2.5') cast as xs:integer", "2"),
         err ("xs:double('NaN') cast as xs:integer", "FOCA0002"),
         err ("xs:double('INF') cast as xs:integer", "FOCA0002"),
         err ("xs:float('-INF') cast as xs:decimal", "FOCA0002"),
         err ("xs:double('NaN') cast as xs:decimal", "FOCA0002"),
         ok ("xs:double('0.5') cast as xs:decimal", "0.5"),
         ok ("12.75 cast as xs:integer", "12"),
         ok ("-12.75 cast as xs:integer", "-12"),
         ok ("7 cast as xs:decimal", "7"),
         ok ("(7 cast as xs:decimal) instance of xs:integer", "false"),
         err ("'1e3' cast as xs:decimal", "FORG0001"),
         err ("'12.5.1' cast as xs:decimal", "FORG0001"),
         ok ("' -3.25 ' cast as xs:decimal", "-3.25"),
         err ("'3.0' cast as xs:integer", "FORG0001"),
         ok ("'99999999999999999999999' cast as xs:integer", "99999999999999999999999"),
         err ("'12a' cast as xs:integer", "FORG0001"),
         err ("xs:date('2000-01-01') cast as xs:integer", "XPTY0004"),
         err ("xs:anyURI('1') cast as xs:double", "XPTY0004"),
         err ("xs:hexBinary('01') cast as xs:decimal", "XPTY0004"),
         err ("xs:duration('P1D') cast as xs:float", "XPTY0004"),

    // Derived integer types: value-space bounds (XSD Part 2 §3.4.x).
         ok ("'127' cast as xs:byte", "127"),
         err ("'128' cast as xs:byte", "FORG0001"),
         err ("-129 cast as xs:byte", "FORG0001"),
         err ("32768 cast as xs:short", "FORG0001"),
         err ("2147483648 cast as xs:int", "FORG0001"),
         err ("9223372036854775808 cast as xs:long", "FORG0001"),
         err ("-1 cast as xs:unsignedInt", "FORG0001"),
         err ("256 cast as xs:unsignedByte", "FORG0001"),
         err ("65536 cast as xs:unsignedShort", "FORG0001"),
         ok ("18446744073709551615 cast as xs:unsignedLong", "18446744073709551615"),
         err ("18446744073709551616 cast as xs:unsignedLong", "FORG0001"),
         err ("0 cast as xs:positiveInteger", "FORG0001"),
         ok ("0 cast as xs:nonNegativeInteger", "0"),
         err ("1 cast as xs:nonPositiveInteger", "FORG0001"),
         err ("0 cast as xs:negativeInteger", "FORG0001"),
         ok ("-5 cast as xs:negativeInteger", "-5"),
         ok ("(5 cast as xs:short) instance of xs:int", "true"),
         ok ("(5 cast as xs:unsignedByte) instance of xs:nonNegativeInteger", "true"),

    // §19.1.4 casting to xs:boolean.
         ok ("0 cast as xs:boolean", "false"),
         ok ("xs:double('NaN') cast as xs:boolean", "false"),
         ok ("-0.5 cast as xs:boolean", "true"),
         ok ("'1' cast as xs:boolean", "true"),
         ok ("' false ' cast as xs:boolean", "false"),
         err ("'TRUE' cast as xs:boolean", "FORG0001"),

    // §19.1.5 casting to duration and date/time types.
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:date", "2002-05-31-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:time", "13:20:00-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:gYear", "2002-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:gYearMonth", "2002-05-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:gMonth", "--05-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:gMonthDay", "--05-31-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00-05:00') cast as xs:gDay", "---31-05:00"),
         ok ("xs:dateTime('2002-05-31T13:20:00Z') cast as xs:dateTimeStamp", "2002-05-31T13:20:00Z"),
         ok ("xs:date('2002-05-31') cast as xs:dateTime", "2002-05-31T00:00:00"),
         ok ("xs:date('2002-05-31Z') cast as xs:gMonthDay", "--05-31Z"),
         ok ("xs:date('2002-05-31') cast as xs:gDay", "---31"),
         err ("xs:date('2002-05-31') cast as xs:dateTimeStamp", "FORG0001"),
         err ("xs:date('2002-05-31') cast as xs:time", "XPTY0004"),
         err ("xs:time('13:20:00') cast as xs:date", "XPTY0004"),
         err ("xs:gYear('2002') cast as xs:date", "XPTY0004"),
         ok ("xs:gYear('2002') cast as xs:gYear", "2002"),
         err ("xs:gMonth('--05') cast as xs:gDay", "XPTY0004"),
         err ("'2001-01-01T00:00:00' cast as xs:dateTimeStamp", "FORG0001"),
         ok ("'2001-01-01T00:00:00+01:00' cast as xs:dateTimeStamp", "2001-01-01T00:00:00+01:00"),
         err ("'2001-13-01' cast as xs:date", "FORG0001"),
         err ("12 cast as xs:date", "XPTY0004"),
         err ("true() cast as xs:dateTime", "XPTY0004"),
         ok ("xs:duration('P1Y2M3DT4H') cast as xs:yearMonthDuration", "P1Y2M"),
         ok ("xs:duration('P1Y2M3DT4H') cast as xs:dayTimeDuration", "P3DT4H"),
         ok ("xs:yearMonthDuration('P1Y') cast as xs:dayTimeDuration", "PT0S"),
         ok ("xs:dayTimeDuration('PT5M') cast as xs:yearMonthDuration", "P0M"),
         ok ("xs:dayTimeDuration('PT5M') cast as xs:duration", "PT5M"),
         err ("'P1Y2D' cast as xs:yearMonthDuration", "FORG0001"),
         err ("'P1Y2D' cast as xs:dayTimeDuration", "FORG0001"),
         err ("'P1Q' cast as xs:duration", "FORG0001"),
         ok ("' P2M ' cast as xs:yearMonthDuration", "P2M"),
         err ("3 cast as xs:duration", "XPTY0004"),
         err ("xs:date('2000-01-01') cast as xs:dayTimeDuration", "XPTY0004"),

    // §19.1.6 casting to binary types.
         ok ("xs:hexBinary('48656C6C6F') cast as xs:base64Binary", "SGVsbG8="),
         ok ("xs:base64Binary('SGVsbG8=') cast as xs:hexBinary", "48656C6C6F"),
         ok ("xs:base64Binary('SGVsbG8=') cast as xs:base64Binary", "SGVsbG8="),
         ok ("xs:hexBinary('ff') cast as xs:hexBinary", "FF"),
         err ("'ABC' cast as xs:hexBinary", "FORG0001"),
         err ("'zz' cast as xs:hexBinary", "FORG0001"),
         err ("'@@@' cast as xs:base64Binary", "FORG0001"),
         err ("1 cast as xs:hexBinary", "XPTY0004"),
         err ("true() cast as xs:base64Binary", "XPTY0004"),

    // §19.1.7 casting to xs:anyURI and §19.1.3 to xs:QName.
         ok ("'  http://example.com/a  ' cast as xs:anyURI", "http://example.com/a"),
         err ("1 cast as xs:anyURI", "XPTY0004"),
         ok ("xs:anyURI('http://a/') cast as xs:string", "http://a/"),
         err ("xs:anyURI('x') cast as xs:QName", "XPTY0004"),
         err ("12 cast as xs:QName", "XPTY0004"),
         ok ("local-name-from-QName(xs:QName('xs:integer') cast as xs:QName)", "integer"),

    // Abstract and unknown targets are static errors (XPath 3.1 §3.14.2).
         err ("1 cast as xs:NOTATION", "XPST0080"),
         err ("1 cast as xs:anyAtomicType", "XPST0080"),
         // go-xslt expects XPST0051; XPath 3.1 §3.14.2 names XQST0052 for a cast target that is not a known
         // simple type.
         err ("1 cast as xs:doesNotExist", "XQST0052"),

    // §19.1.1 casting to (derived) string types applies the whiteSpace
    // facet, then checks the lexical space.
         ok ("'  a   b  ' cast as xs:token", "a b"),
         ok ("string-to-codepoints(concat('a', codepoints-to-string(9), 'b') cast as xs:normalizedString)[2]", "32"),
         ok ("'en-US' cast as xs:language", "en-US"),
         err ("'abcdefghi' cast as xs:language", "FORG0001"),
         ok ("'a:b' cast as xs:Name", "a:b"),
         err ("'1a' cast as xs:Name", "FORG0001"),
         err ("'a:b' cast as xs:NCName", "FORG0001"),
         ok ("' id1 ' cast as xs:ID", "id1"),
         err ("'x y' cast as xs:IDREF", "FORG0001"),
         ok ("'e1' cast as xs:ENTITY", "e1"),
         ok ("'-.x' cast as xs:NMTOKEN", "-.x"),
         err ("'a b' cast as xs:NMTOKEN", "FORG0001"),
         ok ("true() cast as xs:token", "true"),
         ok ("12 cast as xs:NMTOKEN", "12"),
         // go-xslt expects XPTY0004. Every primitive type casts to xs:string, and a cast to a type derived from
         // it goes through xs:string (F&O 3.1 §19.3), so this cast is allowed; QT3 K2-SeqExprCast-177 and
         // cbcl-cast-name-002 expect FORG0001 (not XPTY0004) where the lexical form does not fit.
         ok ("xs:date('2000-01-01') cast as xs:token", "2000-01-01"),
         err ("xs:date('2000-01-01') cast as xs:Name", "FORG0001"),
         ok ("('a' cast as xs:NCName) instance of xs:Name", "true"),
         ok ("('a' cast as xs:NCName) instance of xs:NMTOKEN", "false"),

    // xs:numeric is the union (xs:double, xs:float, xs:decimal) — XPath
    // 3.1 §2.5.5.3; a member-typed value is kept, anything else is cast
    // to the first member that accepts it.
         ok ("(17 cast as xs:numeric) instance of xs:integer", "true"),
         ok ("('12' cast as xs:numeric) instance of xs:double", "true"),

    // Empty operand: `cast as T?` gives (), `cast as T` is XPTY0004.
         ok ("count(() cast as xs:integer?)", "0"),
         err ("() cast as xs:integer", "XPTY0004"),
         err ("(1, 2) cast as xs:integer", "XPTY0004"),

    // castable as never raises a dynamic error.
         ok ("'x' castable as xs:integer", "false"),
         ok ("'P1Y' castable as xs:yearMonthDuration", "true"),
         ok ("xs:double('NaN') castable as xs:integer", "false"),
         ok ("() castable as xs:integer?", "true"),
         ok ("() castable as xs:integer", "false")
    );
  }

  /**
   * Pins the exported Go entry points CastTo, Castable,
   * CallConstructor and AtomicFromItem on the same casting rules.
   */
  @Test
  public void tyCastToAPI ()
  {
    // go-xslt tests its Go entry points CastTo, Castable, CallConstructor,
    // AtomicFromItem and ItemAtomTypeTag; the same rules through XPath:
    run (env ("<r><!--c--><?p data?><e>v</e></r>"),
         ok ("'42' cast as xs:integer instance of xs:integer", "true"),
         ok ("true() cast as xs:double", "1"),
         ok ("(2.5 cast as xs:string) instance of xs:string", "true"),
         err ("map{} cast as xs:string", "FOTY0013"),
         ok ("'abc' castable as xs:integer", "false"),
         ok ("'12' castable as xs:byte", "true"),
         ok ("xs:integer('7')", "7"),
         ok ("count(xs:integer(()))", "0"),
         err ("xs:noSuchType('7')", "XPST0017"),
         err ("xs:integer('x')", "FORG0001"),
         // Comment and PI nodes atomize to xs:string (XDM 3.1 §6.6.5/§6.7.5);
         // element nodes to xs:untypedAtomic (§6.2.5).
         ok ("data(/r/comment()) instance of xs:string", "true"),
         ok ("data(/r/processing-instruction()) instance of xs:string", "true"),
         ok ("data(/r/e) instance of xs:untypedAtomic", "true"));
  }

  /**
   * Covers fn:data's atomization of arrays (XPath 3.1 §2.4.2):
   * an array atomizes to the atomization of its members, recursively.
   */
  @Test
  public void tyAtomize ()
  {
    run (env (),
         ok ("string-join(data([1, [2, 3], ()]), ',')", "1,2,3"),
         ok ("count(data([]))", "0")
    );
  }
}
