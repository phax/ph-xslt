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
 * Specification tests of the arithmetic operators, ported from go-xslt's <code>arith_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ArithSpecTest
{
  /**
   * Checks the numeric operators against F&amp;O 3.1 §4.2
   * (op:numeric-add … op:numeric-mod) and XPath 3.1 §3.5 (operand conversion,
   * type promotion B.1, cardinality).
   */
  @Test
  public void caNumericArithmetic ()
  {
    run (env ("<r><a>3</a><b>x</b></r>"),
    // Unary operators keep the operand's type (F&O §4.2.7/4.2.8).
         ok ("-(3) instance of xs:integer and -(3) eq -3", "true"),
         ok ("-(1.5) instance of xs:decimal and -(1.5) eq -1.5", "true"),
         ok ("-xs:float(2) instance of xs:float and -xs:float(2) eq xs:float(-2)", "true"),
         ok ("-xs:double(2) instance of xs:double and -xs:double(2) eq -2e0", "true"),
         ok ("+(1.5) instance of xs:decimal", "true"),
         ok ("empty(-())", "true"),
    // An untypedAtomic operand is cast to xs:double (§3.5).
         ok ("-r/a instance of xs:double and -r/a eq -3e0", "true"),
         err ("-'a'", "XPTY0004"),
         err ("+xs:date('2000-01-01')", "XPTY0004"),
         err ("-r/b", "FORG0001"),
    // Cardinality: more than one item is a type error.
         err ("(1, 2) + 1", "XPTY0004"),
         err ("1 + (1, 2)", "XPTY0004"),
         ok ("empty(() + 1) and empty(1 * ())", "true"),
    // Typed non-numeric operands are XPTY0004 (no implicit cast).
         err ("'3' + 1", "XPTY0004"),
         err ("xs:anyURI('1') + 1", "XPTY0004"),
         err ("xs:hexBinary('01') + 1", "XPTY0004"),
         err ("1 + xs:base64Binary('AQ==')", "XPTY0004"),
         err ("QName('', 'a') + 1", "XPTY0004"),
         // go-xslt writes this without the parentheses, which parses as r/a + (1 instance of xs:double)
         // (InstanceofExpr binds tighter than AdditiveExpr, XPath 3.1 §A.1) and is then XPTY0004, as
         // xs:boolean has no arithmetic (§B.2).
         ok ("(r/a + 1) instance of xs:double and r/a + 1 eq 4", "true"),
         err ("r/a + 1 instance of xs:double", "XPTY0004"),
         err ("1 + true()", "XPTY0004"),
    // xs:integer × xs:integer.
         ok ("(1 + 2) instance of xs:integer and 1 + 2 eq 3", "true"),
         ok ("(xs:short(2) + xs:byte(3)) instance of xs:integer and not((xs:short(2) + xs:byte(3)) instance of xs:short)", "true"),
         ok ("5 - 7 eq -2 and 6 * 7 eq 42", "true"),
         ok ("(5 div 2) instance of xs:decimal and 5 div 2 eq 2.5", "true"),
         ok ("-7 idiv 2 eq -3 and 7 idiv -2 eq -3", "true"),
         ok ("-7 mod 3 eq -1 and 7 mod -3 eq 1", "true"),
         err ("1 div 0", "FOAR0001"),
         err ("1 idiv 0", "FOAR0001"),
         err ("1 mod 0", "FOAR0001"),
         ok ("99999999999999999999 * 10 eq 999999999999999999990", "true"),
    // xs:decimal (and decimal × integer promoting the integer).
         ok ("(1.5 + 1) instance of xs:decimal and 1.5 + 1 eq 2.5", "true"),
         ok ("1 + 1.5 eq 2.5 and 2.5 - 1 eq 1.5 and 1.5 * 2 eq 3.0", "true"),
         ok ("(1.5 div 0.5) instance of xs:decimal and 1.5 div 0.5 eq 3", "true"),
         ok ("(7.5 idiv 2) instance of xs:integer and 7.5 idiv 2 eq 3 and -7.5 idiv 2 eq -3", "true"),
         ok ("4.5 mod 1.2 eq 0.9 and -5.5 mod 2 eq -1.5", "true"),
         err ("1.5 div 0.0", "FOAR0001"),
         err ("1.5 idiv 0.0", "FOAR0001"),
         err ("1.5 mod 0.0", "FOAR0001"),
    // xs:float: decimal/integer promote to float (B.1), idiv stays integer.
         ok ("(1.5 + xs:float(1)) instance of xs:float and 1.5 + xs:float(1) eq xs:float(2.5)", "true"),
         ok ("(xs:float(7) idiv 2) instance of xs:integer and xs:float(7) idiv 2 eq 3", "true"),
         ok ("xs:float(1) div 0 eq xs:float('INF')", "true"),
         ok ("(xs:float(5) mod 3) instance of xs:float", "true"),
    // xs:double, IEEE 754 semantics.
         ok ("(1 + 1e0) instance of xs:double", "true"),
         ok ("(xs:float(1) + 1e0) instance of xs:double", "true"),
         ok ("string(1e0 div 0)", "INF"),
         ok ("string(-1e0 div 0)", "-INF"),
         ok ("string(0e0 div 0)", "NaN"),
         ok ("-7e0 mod 2 eq -1 and 7e0 mod -2 eq 1", "true"),
         ok ("string(xs:double('INF') mod 2)", "NaN"),
         ok ("(7e0 idiv 2) instance of xs:integer and 7e0 idiv 2 eq 3", "true"),
         ok ("1e0 idiv xs:double('INF') eq 0", "true"),
         ok ("(1e38 idiv 1e-37) instance of xs:integer and 1e38 idiv 1e-37 gt 1000000000000000000000000000000000000000000000000000000000000000000000000", "true"),
         err ("xs:double('NaN') idiv 1", "FOAR0002"),
         err ("1 idiv xs:double('NaN')", "FOAR0002"),
         err ("xs:double('-INF') idiv 1", "FOAR0002"),
         err ("1e0 idiv 0e0", "FOAR0001"),
         ok ("1.0e0 - 0.5e0 eq 0.5e0 and 2e0 * 3e0 eq 6e0", "true")
    );
  }

  /**
   * Checks XPath 1.0 compatibility mode
   * (XPath 2.0 §3.4 / XSLT 3.0 §3.9.1): the operand is the FIRST item converted
   * with fn:number, empty is NaN, and the result is xs:double.
   */
  @Test
  public void caBackwardsCompatibleArithmetic ()
  {
    run (env ("<r><a>4</a><a>5</a></r>").staticSetup (x -> x.setXPath10Compatibility (true)),
         ok ("1 + (6 to 10)", "7"),
         ok ("1 + r/a", "5"),
         ok ("1 + ()", "NaN"),
         ok ("'abc' + 1", "NaN"),
         ok ("true() + 1", "2"),
         ok ("'2' * '3'", "6")
    );
  }

  /**
   * Checks F&amp;O 3.1 §9.6/§10.6/§10.8 (duration
   * and date/time arithmetic) and the operator mapping of XPath 3.1 Appendix B.2.
   */
  @Test
  public void caDateTimeDurationArithmetic ()
  {
    run (env (),
    // date/dateTime ± yearMonthDuration: day clamped (XSD 1.1 Part 2 E.3).
         ok ("xs:date('2000-02-29') - xs:yearMonthDuration('P1Y') eq xs:date('1999-02-28')", "true"),
         ok ("xs:date('2000-01-31') + xs:yearMonthDuration('P1M') eq xs:date('2000-02-29')", "true"),
         ok ("xs:date('2000-03-31') - xs:yearMonthDuration('P13M') eq xs:date('1999-02-28')", "true"),
         ok ("xs:date('2000-11-15') + xs:yearMonthDuration('P3M') eq xs:date('2001-02-15')", "true"),
         ok ("xs:yearMonthDuration('P1M') + xs:date('2000-01-15') eq xs:date('2000-02-15')", "true"),
         ok ("xs:dateTime('2000-01-01T00:00:00Z') + xs:dayTimeDuration('PT36H') eq xs:dateTime('2000-01-02T12:00:00Z')", "true"),
         ok ("xs:dayTimeDuration('P1D') + xs:dateTime('2000-12-31T10:00:00') eq xs:dateTime('2001-01-01T10:00:00')", "true"),
         ok ("xs:date('2000-01-01') - xs:dayTimeDuration('PT36H') eq xs:date('1999-12-30')", "true"),
         ok ("(xs:date('2000-01-01') + xs:dayTimeDuration('P1D')) instance of xs:date", "true"),
         ok ("xs:time('23:30:00') + xs:dayTimeDuration('PT1H') eq xs:time('00:30:00')", "true"),
         ok ("xs:dayTimeDuration('PT1H') + xs:time('23:30:00') eq xs:time('00:30:00')", "true"),
         ok ("xs:time('00:30:00') - xs:dayTimeDuration('PT1H') eq xs:time('23:30:00')", "true"),
         ok ("string(xs:dateTime('2000-01-01T00:00:00+02:00') + xs:dayTimeDuration('PT1H'))", "2000-01-01T01:00:00+02:00"),
         err ("xs:time('10:00:00') + xs:yearMonthDuration('P1M')", "XPTY0004"),
         err ("xs:gYear('2000') + xs:yearMonthDuration('P1Y')", "XPTY0004"),
         err ("xs:yearMonthDuration('P1Y') + xs:gYear('2000')", "XPTY0004"),
         err ("xs:date('2000-01-01') * xs:dayTimeDuration('P1D')", "XPTY0004"),
         err ("xs:date('2000-01-01') + xs:duration('P1D')", "XPTY0004"),
         err ("xs:dayTimeDuration('P1D') - xs:date('2000-01-01')", "XPTY0004"),
    // Differences of date/time values are dayTimeDurations (§10.8.1–3).
         ok ("xs:date('2000-03-01') - xs:date('2000-02-01') eq xs:dayTimeDuration('P29D')", "true"),
         ok ("xs:time('12:00:00') - xs:time('10:30:00') eq xs:dayTimeDuration('PT1H30M')", "true"),
         ok ("xs:dateTime('2000-01-02T00:00:00Z') - xs:dateTime('2000-01-01T12:00:00Z') eq xs:dayTimeDuration('PT12H')", "true"),
         ok ("xs:dateTime('2000-01-01T00:00:00.5Z') - xs:dateTime('2000-01-01T00:00:00Z') eq xs:dayTimeDuration('PT0.5S')", "true"),
         ok ("(xs:date('0001-01-01') - xs:date('2001-01-01')) lt xs:dayTimeDuration('-P730000D')", "true"),
         err ("xs:date('2000-01-01') - xs:dateTime('2000-01-01T00:00:00')", "XPTY0004"),
         err ("xs:gYear('2000') - xs:gYear('1999')", "XPTY0004"),
         err ("xs:date('2000-01-01') + xs:date('2000-01-01')", "XPTY0004"),
    // Duration ± duration of the same subtype (§8.4.1–4).
         ok ("xs:yearMonthDuration('P1Y') + xs:yearMonthDuration('P2M') eq xs:yearMonthDuration('P1Y2M')", "true"),
         ok ("xs:yearMonthDuration('P1Y') - xs:yearMonthDuration('P2M') eq xs:yearMonthDuration('P10M')", "true"),
         ok ("xs:dayTimeDuration('P1D') + xs:dayTimeDuration('PT1H') eq xs:dayTimeDuration('P1DT1H')", "true"),
         ok ("xs:dayTimeDuration('P1D') - xs:dayTimeDuration('P2D') eq xs:dayTimeDuration('-P1D')", "true"),
         err ("xs:yearMonthDuration('P1Y') + xs:dayTimeDuration('P1D')", "XPTY0004"),
         err ("xs:duration('P1Y') + xs:duration('P1Y')", "XPTY0004"),
         err ("xs:dayTimeDuration('PT1H') * xs:dayTimeDuration('PT1H')", "XPTY0004"),
    // Duration div duration is an xs:decimal (§8.4.7, §8.4.10).
         ok ("(xs:yearMonthDuration('P3Y') div xs:yearMonthDuration('P1Y6M')) instance of xs:decimal", "true"),
         ok ("xs:yearMonthDuration('P3Y') div xs:yearMonthDuration('P1Y6M') eq 2", "true"),
         ok ("xs:dayTimeDuration('PT1H') div xs:dayTimeDuration('PT15M') eq 4", "true"),
         err ("xs:dayTimeDuration('PT1H') div xs:dayTimeDuration('PT0S')", "FOAR0001"),
         err ("xs:yearMonthDuration('P1Y') div xs:yearMonthDuration('P0M')", "FOAR0001"),
    // Duration × / div number (§8.4.5–6, §8.4.8–9).
         ok ("xs:dayTimeDuration('PT1H') * 2 eq xs:dayTimeDuration('PT2H')", "true"),
         ok ("2 * xs:yearMonthDuration('P1M') eq xs:yearMonthDuration('P2M')", "true"),
         ok ("xs:dayTimeDuration('PT1H') * 0 eq xs:dayTimeDuration('PT0S')", "true"),
         ok ("xs:yearMonthDuration('P5M') div 2 eq xs:yearMonthDuration('P3M')", "true"),
         ok ("xs:yearMonthDuration('P1M') * -3.5 eq xs:yearMonthDuration('-P3M')", "true"),
         ok ("xs:yearMonthDuration('P2Y') * 1.5 eq xs:yearMonthDuration('P3Y')", "true"),
         ok ("xs:dayTimeDuration('PT1H') div 4 eq xs:dayTimeDuration('PT15M')", "true"),
         ok ("xs:dayTimeDuration('PT1H') div xs:double('INF') eq xs:dayTimeDuration('PT0S')", "true"),
         ok ("xs:yearMonthDuration('P1Y') div xs:double('-INF') eq xs:yearMonthDuration('P0M')", "true"),
         err ("xs:dayTimeDuration('PT1H') div 0", "FODT0002"),
         err ("xs:dayTimeDuration('PT1H') div xs:double('NaN')", "FOCA0005"),
         err ("xs:dayTimeDuration('PT1H') * xs:double('INF')", "FODT0002"),
         err ("xs:double('NaN') * xs:yearMonthDuration('P1M')", "FOCA0005"),
         err ("xs:double('INF') * xs:yearMonthDuration('P1M')", "FODT0002"),
         // The overflow limit is implementation-defined; go-xslt stops at 1e15 months and tests 1e16.
         err ("xs:yearMonthDuration('P1M') * 1e300", "FODT0002"),
         err ("xs:duration('P1Y') * 2", "XPTY0004"),
         err ("xs:dayTimeDuration('PT1H') + 1", "XPTY0004"),
         err ("2 div xs:dayTimeDuration('PT1H')", "XPTY0004")
    );
  }
}
