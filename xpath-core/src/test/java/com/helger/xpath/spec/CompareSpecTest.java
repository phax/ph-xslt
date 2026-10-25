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
import static com.helger.xpath.spec.SpecTestHelper.parse;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.value.XdmSequence;

/**
 * Specification tests of the comparison operators, ported from go-xslt's <code>compare_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class CompareSpecTest
{
  /**
   * Checks XPath 3.1 §3.7.2 (value comparisons) and the
   * type pairs F&amp;O 3.1's operator mapping (Appendix B.2) defines eq/ne/lt/… for.
   */
  @Test
  public void caValueComparison ()
  {
    run (env ("<r><a>1</a><a>2</a></r>"),
         ok ("empty(() eq 1) and empty(1 lt ())", "true"),
         err ("(1, 2) eq 1", "XPTY0004"),
         err ("1 eq r/a", "XPTY0004"),
    // untypedAtomic is cast to xs:string, so it does not compare with numbers.
         err ("r/a[1] eq 1", "XPTY0004"),
         ok ("r/a[1] eq '1'", "true"),
         err ("'1' eq 1", "XPTY0004"),
         err ("true() eq 1", "XPTY0004"),
         err ("true() eq 'true'", "XPTY0004"),
         ok ("true() eq true() and false() lt true() and not(true() lt false())", "true"),
         ok ("true() gt false() and true() ge true() and false() le false() and true() ne false()", "true"),
         ok ("xs:anyURI('http://a') eq 'http://a'", "true"),
         ok ("'abc' lt 'abd' and 'b' gt 'abc'", "true"),
    // Numeric promotion: decimal → float, then compare (B.1).
         ok ("xs:float(1.01) eq 1.01", "true"),
         ok ("1.01 eq xs:float(1.01)", "true"),
         ok ("1 lt 1.5 and 2.5 gt 2 and 1e0 eq 1 and xs:float(2) gt 1", "true"),
         ok ("100000000000000000001 gt 100000000000000000000", "true"),
         ok ("xs:double('NaN') eq xs:double('NaN')", "false"),
         ok ("xs:double('NaN') ne xs:double('NaN')", "true"),
         ok ("xs:float('NaN') lt 1", "false"),
    // Date/time: same primitive only; gregorians are eq/ne only.
         ok ("xs:dateTime('2000-01-01T12:00:00Z') eq xs:dateTime('2000-01-01T13:00:00+01:00')", "true"),
         ok ("xs:date('2000-01-01') lt xs:date('2000-01-02') and xs:date('2000-01-02') gt xs:date('2000-01-01')", "true"),
         ok ("xs:time('24:00:00') eq xs:time('00:00:00')", "true"),
         err ("xs:date('2000-01-01') eq xs:dateTime('2000-01-01T00:00:00')", "XPTY0004"),
         err ("xs:date('2000-01-01') eq 1", "XPTY0004"),
         err ("xs:date('2000-01-01') eq 'x'", "XPTY0004"),
         ok ("xs:gYear('2000') eq xs:gYear('2000') and xs:gYear('2000') ne xs:gYear('2001')", "true"),
         ok ("xs:gMonthDay('--12-25') eq xs:gMonthDay('--12-25')", "true"),
         err ("xs:gYear('2000') lt xs:gYear('2001')", "XPTY0004"),
         err ("xs:gDay('---01') gt xs:gDay('---02')", "XPTY0004"),
         err ("xs:dayTimeDuration('P1D') eq xs:date('2000-01-01')", "XPTY0004"),
    // Durations: eq across subtypes, order only within ym or dt.
         ok ("xs:yearMonthDuration('P1Y') lt xs:yearMonthDuration('P13M')", "true"),
         ok ("xs:dayTimeDuration('PT1H') gt xs:dayTimeDuration('PT59M') and xs:dayTimeDuration('PT60M') eq xs:dayTimeDuration('PT1H')", "true"),
         ok ("xs:yearMonthDuration('P0M') eq xs:dayTimeDuration('PT0S')", "true"),
         ok ("xs:duration('P1Y') eq xs:yearMonthDuration('P12M')", "true"),
         ok ("xs:yearMonthDuration('P12M') eq xs:dayTimeDuration('P365D')", "false"),
    // op:duration-equal (F&O 3.1 §8.2.5): months and seconds compared
    // separately, never folded into one another.
         ok ("xs:duration('P1M') eq xs:duration('PT2629746S')", "false"),
         ok ("xs:duration('P1M') ne xs:duration('PT2629746S')", "true"),
         ok ("xs:yearMonthDuration('P1M') = xs:dayTimeDuration('PT2629746S')", "false"),
         ok ("count(distinct-values((xs:duration('P1M'), xs:duration('PT2629746S'))))", "2"),
         ok ("xs:duration('P1M1D') eq xs:duration('P1MT24H')", "true"),
         err ("xs:duration('P1Y') lt xs:duration('P2Y')", "XPTY0004"),
         err ("xs:yearMonthDuration('P1Y') lt xs:dayTimeDuration('P1D')", "XPTY0004"),
         err ("xs:dayTimeDuration('P1D') eq 'P1D'", "XPTY0004"),
         err ("xs:dayTimeDuration('P1D') eq true()", "XPTY0004"),
         err ("xs:dayTimeDuration('P1D') eq QName('', 'a')", "XPTY0004"),
    // QNames: eq/ne on expanded names only, no ordering.
         ok ("QName('u', 'p:a') eq QName('u', 'q:a')", "true"),
         ok ("QName('u', 'a') eq QName('v', 'a') or QName('u', 'a') eq QName('u', 'b')", "false"),
         ok ("QName('u', 'a') ne QName('v', 'a')", "true"),
         err ("QName('u', 'a') lt QName('u', 'b')", "XPTY0004"),
         err ("QName('u', 'a') eq xs:hexBinary('01')", "XPTY0004"),
    // Binaries are ordered in 3.1 (F&O §12.1), same type only.
         ok ("xs:hexBinary('01') lt xs:hexBinary('02') and xs:hexBinary('0102') gt xs:hexBinary('01')", "true"),
         ok ("xs:base64Binary('AQ==') eq xs:base64Binary('AQ==')", "true"),
         err ("xs:hexBinary('01') eq xs:base64Binary('AQ==')", "XPTY0004")
    );
  }

  /**
   * Checks that eq/ne and general comparisons
   * on strings use the in-scope default collation (XPath 3.1 §3.7.2/§3.7.3),
   * while non-string pairs are untouched by it.
   */
  @Test
  public void caValueComparisonCollation ()
  {
    // go-xslt installs a lower-casing comparator; the HTML ASCII case-insensitive collation has the same effect here.
    run (env ().staticSetup (x -> x.setDefaultCollation (CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI)),
         ok ("'A' eq 'a'", "true"),
         ok ("'A' = ('x', 'a')", "true"),
         ok ("'B' gt 'a'", "true"),
         ok ("1 eq 1.0", "true")
    );
  }

  /**
   * Checks XPath 3.1 §3.7.3: existential semantics and
   * the untypedAtomic conversion rules.
   */
  @Test
  public void caGeneralComparison ()
  {
    final String sSrc = "<r><a>3</a><b>three</b><d>2000-01-01</d><c>3</c><e>p:x</e></r>";
    run (env (sSrc),
         ok ("(1, 2) = (2, 3)", "true"),
         ok ("(1, 2) = (3, 4)", "false"),
         ok ("(1, 2) != (1, 2)", "true"),
         ok ("(1, 1) != 1", "false"),
         ok ("() = ()", "false"),
         ok ("(1, 2) < (0, 3) and (5, 1) > 4 and 1 <= (1, 0) and 2 >= (3, 2)", "true"),
    // untyped vs numeric → double; vs string → string; vs other → that type.
         ok ("r/a = 3 and r/a = 3.0 and r/a = 3e0", "true"),
         ok ("3 = r/a", "true"),
         ok ("r/a = '3' and not(r/a = '3.0')", "true"),
         err ("r/b = 3", "FORG0001"),
         err ("3 = r/b", "FORG0001"),
         ok ("r/d = xs:date('2000-01-01')", "true"),
         ok ("xs:date('2000-01-01') = r/d", "true"),
         err ("r/b = xs:date('2000-01-01')", "FORG0001"),
         ok ("r/a = r/c and not(r/a = r/b)", "true"),
         ok ("r/a < r/b", "true"), // both untyped: compared as strings
    // Typed incomparable pairs raise XPTY0004.
         err ("'a' = 1", "XPTY0004"),
         err ("1 = true()", "XPTY0004"),
    // NaN.
         ok ("xs:double('NaN') = (1, xs:double('NaN'))", "false"),
         ok ("xs:double('NaN') != 1", "true"),
         ok ("xs:double('NaN') < 1", "false")
    );
    // An untyped operand facing an xs:QName is cast in the expression's
    // static namespace context (§3.7.3, cast to xs:QName needs it).
    run (env (sSrc).ns ("p", "urn:x"),
         ok ("r/e = QName('urn:x', 'q:x')", "true"),
         ok ("QName('urn:x', 'x') = r/e", "true"),
         ok ("r/e = QName('urn:y', 'x')", "false")
    );
  }

  /**
   * Checks general comparisons against a range
   * expression, whose meaning is that of comparing against every integer in
   * the range (XPath 3.1 §3.3.1 + §3.7.3).
   */
  @Test
  public void caRangeComparison ()
  {
    run (env ("<r><a>3</a><b>x</b></r>"),
         ok ("5 = (1 to 10)", "true"),
         ok ("11 = (1 to 10)", "false"),
         ok ("5.5 = (1 to 10)", "false"),
         ok ("5.0 = (1 to 10)", "true"),
         ok ("(1 to 10) = 5", "true"),
         ok ("5 != (5 to 5)", "false"),
         ok ("5 != (1 to 2)", "true"),
         ok ("5 != (5 to 6)", "true"),
         ok ("0 < (1 to 3)", "true"),
         ok ("3 < (1 to 3)", "false"),
         ok ("3 <= (1 to 3)", "true"),
         ok ("4 <= (1 to 3)", "false"),
         ok ("1 > (1 to 3)", "false"),
         ok ("2 > (1 to 3)", "true"),
         ok ("1 >= (1 to 3)", "true"),
         ok ("0 >= (1 to 3)", "false"),
         ok ("(1 to 3) < 0", "false"),
         ok ("(1 to 3) < 2", "true"),
         ok ("(1 to 3) <= 1", "true"),
         ok ("(1 to 3) > 2", "true"),
         ok ("(1 to 3) > 3", "false"),
         ok ("(1 to 3) >= 3", "true"),
         ok ("(1 to 3) >= 4", "false"),
         ok ("1 = (3 to 1)", "false"),
         ok ("1 != (3 to 1)", "false"),
         ok ("() = (1 to 3)", "false"),
         ok ("1 = (1 to ())", "false"),
         ok ("(7, 2) = (1 to 3)", "true"),
         ok ("1000000000000000000000 = (999999999999999999999 to 1000000000000000000001)", "true"),
         ok ("1000000000000000000000.5 = (999999999999999999999 to 1000000000000000000001)", "false"),
    // Double/float operands: range members are promoted to their type.
         ok ("xs:double(2) = (1 to 3)", "true"),
         ok ("2.5e0 = (1 to 3)", "false"),
         ok ("4e0 = (1 to 3)", "false"),
         ok ("xs:double('NaN') = (1 to 3)", "false"),
         ok ("xs:double('NaN') != (1 to 3)", "true"),
         ok ("xs:double('NaN') < (1 to 3)", "false"),
         ok ("1e0 != (1 to 1)", "false"),
         ok ("1e0 != (1 to 2)", "true"),
         ok ("xs:float(2) <= (1 to 3) and xs:float(2) = (1 to 3)", "true"),
         ok ("xs:double(5) > (1 to 3) and xs:double(1) >= (1 to 3)", "true"),
         ok ("xs:double(0) > (1 to 3) or xs:double(0) >= (1 to 3)", "false"),
         ok ("xs:double(3) < (1 to 3)", "false"),
         ok ("xs:double(3) <= (1 to 3)", "true"),
    // untyped → double; a non-numeric typed operand compares as usual.
         ok ("r/a = (1 to 5)", "true"),
         err ("r/b = (1 to 5)", "FORG0001"),
         err ("'a' = (1 to 3)", "XPTY0004")
    );
  }

  /**
   * Checks XPath 3.1 §3.7.4 (is, &lt;&lt;, &gt;&gt;).
   */
  @Test
  public void caNodeComparison ()
  {
    run (env ("<r><a/><b/></r>"),
         ok ("r/a is r/a", "true"),
         ok ("r/a is r/b", "false"),
         ok ("r/a << r/b and r/b >> r/a", "true"),
         ok ("r/b << r/a or r/a >> r/b", "false"),
         ok ("r << r/a and r/a/.. is r", "true"),
         ok ("empty(() is r/a) and empty(r/a << ())", "true"),
         err ("1 is r/a", "XPTY0004"),
         err ("r/a is 'a'", "XPTY0004"),
         err ("r/* is r/a", "XPTY0004"),
         err ("r/a << r/*", "XPTY0004")
    );
    // Nodes of different trees: the order is implementation-dependent but
    // must be stable and consistent (XDM 3.1 §2.4): exactly one of x << y
    // and y << x holds, and it is the same for every pair across the trees.
    run (env ().var ("d1", XdmSequence.of (parse ("<x><p/><q/></x>")))
               .var ("d2", XdmSequence.of (parse ("<y><p/><q/></y>"))),
         ok ("($d1 << $d2) ne ($d2 << $d1)", "true"),
         ok ("($d1 << $d2) eq ($d1//p << $d2//q) and ($d1 << $d2) eq ($d1//q << $d2//p)", "true"),
         ok ("($d1 >> $d2) eq ($d2 << $d1)", "true")
    );
  }

  /**
   * Checks XPath 1.0 §3.4's comparison
   * rules, which apply under XPath 1.0 compatibility mode.
   */
  @Test
  public void caBackwardsCompatibleComparison ()
  {
    run (env ("<r><a>3</a><b>10</b><c>3</c><s>abc</s></r>").staticSetup (x -> x.setXPath10Compatibility (true)),
    // Scalars: boolean beats number beats string for = and !=.
         ok ("'false' = true()", "true"),
         ok ("true() != 'x'", "false"),
         ok ("1 = '1.0'", "true"),
         ok ("'1.0' != 1", "false"),
         ok ("'a' = 'a' and 'a' != 'b'", "true"),
         ok ("'a' = 'b'", "false"),
    // Relational operators always compare numbers.
         ok ("'10' > '2'", "true"),
         ok ("'x' < 1 or 'x' >= 1", "false"),
         ok ("true() > false() and 1 <= 1 and 2 >= 1 and 0 < 1", "true"),
    // Node-set vs boolean: boolean(node-set), also for an empty one.
         ok ("r/missing = false()", "true"),
         ok ("r/a = true()", "true"),
         ok ("false() = r/missing", "true"),
         ok ("true() != r/a", "false"),
    // Node-set vs number / string: per-node conversion.
         ok ("r/a = 3 and 3 = r/a", "true"),
         ok ("2 < r/a and r/a > 2 and r/b >= 10 and 10 <= r/b", "true"),
         ok ("r/a = '3' and '3' = r/a and r/s != 'x'", "true"),
         ok ("r/s < 'x'", "false"),
    // Node-set vs node-set: string-values; relational numerically.
         ok ("r/a = r/c and not(r/a != r/c)", "true"),
         ok ("r/a < r/b", "true"), // 3 < 10 numerically, not as strings
         ok ("r/* = r/c", "true"),
    // The range fast path is not taken in this mode; results agree.
         ok ("2 = (1 to 3)", "true"),
         ok ("(1 to 3) > 3", "false")
    );
  }

  /**
   * Checks general "=" against long sequences of integers or
   * strings (the hash-index path) against the §3.7.3 meaning, and that the
   * index gives the same answer as the item-by-item comparison.
   */
  @Test
  public void caEqIndex ()
  {
    // go-xslt runs the table with and without its hash index; there is one path here.
    run (env (),
         ok ("7 = (1 to 100, 200)", "true"),
         ok ("300 = (1 to 100, 200)", "false"),
         ok ("100000000000000000000 = (1 to 100, 200)", "false"),
         ok ("5.0 = (1 to 100, 200)", "true"),
         ok ("5.5 = (1 to 100, 200)", "false"),
         ok ("100000000000000000000.0 = (1 to 100, 200)", "false"),
         ok ("xs:untypedAtomic('7') = (1 to 100, 200)", "true"),
         ok ("xs:untypedAtomic('7.5') = (1 to 100, 200)", "false"),
         err ("xs:untypedAtomic('x') = (1 to 100, 200)", "FORG0001"),
         ok ("7e0 = (1 to 100, 200)", "true"),
         ok ("7.5e0 = (1 to 100, 200)", "false"),
         ok ("xs:double('INF') = (1 to 100, 200)", "false"),
         ok ("xs:double('NaN') = (1 to 100, 200)", "false"),
         ok ("1e300 = (1 to 100, 200)", "false"),
         err ("'7' = (1 to 100, 200)", "XPTY0004"),
         ok ("(-9223372036854775808, 2 to 100) = -9223372036854775808", "true"),
         err ("(1 to 100, 'x') = 'x'", "XPTY0004"),
         ok ("'z' = ((for $i in 1 to 100 return string($i)), 'z')", "true"),
         ok ("'q' = ((for $i in 1 to 100 return string($i)), 'z')", "false"),
         ok ("xs:anyURI('z') = ((for $i in 1 to 100 return string($i)), 'z')", "true"),
         ok ("xs:untypedAtomic('50') = ((for $i in 1 to 100 return string($i)), 'z')", "true"),
         err ("1 = ((for $i in 1 to 100 return string($i)), 'z')", "XPTY0004"),
         err ("1 = ((for $i in 1 to 100 return string($i)), 1)", "XPTY0004"),
         ok ("'a' = ((for $i in 1 to 100 return xs:string($i)), 'a')", "true"),
         ok ("true() = (for $i in 1 to 100 return $i mod 2 = 0)", "true"),
         ok ("'r' = ((for $i in 1 to 100 return string($i)), /r)", "false"),
         ok ("count((1 to 5)[. = (for $i in 1 to 100 return $i * 2)])", "2"),
         ok ("let $s := (for $i in 1 to 100 return $i * 2) return count((1 to 9)[. = $s])", "4")
    );
  }

  /**
   * Checks that a dynamic error in an operand
   * propagates out of every comparison form (XPath 3.1 §2.3.1).
   */
  @Test
  public void caComparisonErrors ()
  {
    run (env ("<r><a>3</a></r>"),
         err ("(1 div 0) eq 1", "FOAR0001"),
         err ("1 eq (1 div 0)", "FOAR0001"),
         err ("(1 div 0) = (1 to 3)", "FOAR0001"),
         err ("1 = ((1 div 0) to 3)", "FOAR0001"),
         err ("1 = (1 to (1 div 0))", "FOAR0001"),
         err ("1 = ('a' to 3)", "XPTY0004"),
         err ("1 = (1 to 'a')", "XPTY0004"),
         err ("(1 div 0) is r/a", "FOAR0001"),
         err ("(1 div 0) = 1", "FOAR0001")
    );
    // An untyped value whose prefix is not bound does not cast to xs:QName.
    run (env ("<r><e>zz:x</e></r>").ns ("p", "urn:x"),
         err ("r/e = QName('urn:x', 'x')", "FONS0004")
    );
    run (env ("<r><a>3</a></r>").staticSetup (x -> x.setXPath10Compatibility (true)),
         ok ("r/a = ()", "false"),
         // go-xslt expects true. XPath 3.1 §3.7.2 converts by effective boolean value only when an operand is a
         // SINGLE xs:boolean; here the pair (untyped '3', true()) casts '3' to xs:boolean, and no other pair is
         // true.
         err ("r/a = (1, true())", "FORG0001"),
         ok ("r/a = true()", "true"),
         ok ("4 > r/a and 3 >= r/a and not(3 > r/a)", "true"),
         ok ("xs:boolean('true') = 'false'", "true"),
         ok ("xs:boolean('false') = 0", "true")
    );
  }
}
