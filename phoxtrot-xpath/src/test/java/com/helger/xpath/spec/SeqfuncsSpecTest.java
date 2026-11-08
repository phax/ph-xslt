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

import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xpath/seqfuncs_spec_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class SeqfuncsSpecTest
{
  /** A small document for the node-related cases. */
  private static final String SQ_DOC = "<r><n v=\"3\">3</n><n v=\"1\">1</n><n v=\"2\">x</n></r>";

  /**
   * F&O 3.1 §14.1.6 fn:reverse, §14.1.7 fn:subsequence, §14.2.1
   * fn:distinct-values, §14.2.2 fn:index-of — including the REC examples.
   */
  @Test
  public void seqSpecBasics ()
  {
    run (
      // §14.1.6 examples.
      ok ("string-join(reverse((\"a\",\"b\",\"c\")), \",\")", "c,b,a"),
      ok ("reverse((\"hello\"))", "hello"),
      ok ("count(reverse(()))", "0"),
      ok ("count(reverse([1,2,3]))", "1"),
      ok ("reverse(([1,2,3],[4,5,6]))[1](1)", "4"),

      // §14.1.7 examples with $seq := ("item1" .. "item5").
      ok ("string-join(subsequence((\"item1\",\"item2\",\"item3\",\"item4\",\"item5\"), 4), \",\")", "item4,item5"),
      ok ("string-join(subsequence((\"item1\",\"item2\",\"item3\",\"item4\",\"item5\"), 3, 2), \",\")", "item3,item4"),
      // The rules are stated on round($start) and round($length): positions
      // p with round(start) <= p < round(start)+round(length).
      ok ("string-join(subsequence((1,2,3,4,5), 1.5, 2), \",\")", "2,3"),
      ok ("string-join(subsequence((1,2,3,4,5), 0, 3), \",\")", "1,2"),
      ok ("string-join(subsequence((1,2,3,4,5), -1), \",\")", "1,2,3,4,5"),
      ok ("count(subsequence((1,2,3), 5))", "0"),
      ok ("count(subsequence((1,2,3), 2, 0))", "0"),
      ok ("count(subsequence((1,2,3), 2, -1))", "0"),
      ok ("count(subsequence((1,2,3), xs:double('NaN')))", "0"),
      ok ("count(subsequence((1,2,3), 1, xs:double('NaN')))", "0"),
      ok ("string-join(subsequence((1,2,3), 2, xs:double('INF')), \",\")", "2,3"),
      // -INF + INF is NaN: nothing selected (§14.1.7 notes).
      ok ("count(subsequence((1,2,3), xs:double('-INF'), xs:double('INF')))", "0"),
      // untypedAtomic arguments are cast to xs:double (function conversion).
      ok ("string-join(subsequence((1,2,3), xs:untypedAtomic('2')), \",\")", "2,3"),

      // §14.2.1: numeric equality across types, NaN equal to itself.
      ok ("count(distinct-values((1, 2.0, 3, 2)))", "3"),
      ok ("count(distinct-values((xs:double('NaN'), xs:float('NaN'), 0 div 0e0)))", "1"),
      ok ("count(distinct-values((0e0, -0e0)))", "1"),
      // a string and a number are never equal.
      ok ("count(distinct-values((1, '1')))", "2"),
      // untypedAtomic compares as a string; the REC example.
      ok ("count(distinct-values((xs:untypedAtomic(\"cherry\"), xs:untypedAtomic(\"plum\"), xs:untypedAtomic(\"plum\"))))", "2"),
      // xs:float 1.2 and xs:decimal 1.2 are eq (comparison in xs:float).
      ok ("count(distinct-values((xs:float('1.2'), 1.2)))", "1"),
      // distinct dates with the same instant collapse.
      ok ("count(distinct-values((xs:dateTime('2000-01-01T12:00:00Z'), xs:dateTime('2000-01-01T13:00:00+01:00'))))", "1"),
      // a yearMonthDuration and a dayTimeDuration of zero length are eq.
      ok ("count(distinct-values((xs:yearMonthDuration('P0M'), xs:dayTimeDuration('PT0S'))))", "1"),
      ok ("count(distinct-values((xs:yearMonthDuration('P1Y'), xs:yearMonthDuration('P12M'))))", "1"),
      // incomparable types are simply distinct.
      ok ("count(distinct-values((xs:date('2000-01-01'), 1, true())))", "3"),
      ok ("count(distinct-values((true(), false(), true())))", "2"),
      ok ("string-join(distinct-values(('a','b','a','c','b')), ',')", "a,b,c"),
      // atomization of nodes.
      ok ("count(distinct-values((xs:untypedAtomic('a'), 'a')))", "1"),
      // an explicit codepoint collation.
      ok ("count(distinct-values(('a','A'), 'http://www.w3.org/2005/xpath-functions/collation/codepoint'))", "2"),
      // html-ascii-case-insensitive makes 'a' and 'A' equal.
      ok ("count(distinct-values(('a','A','b'), 'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive'))", "2"),
      ok ("count(distinct-values(('a','A','b'), 'http://www.w3.org/2013/collation/UCA?strength=primary'))", "2"),

      // §14.2.2 examples.
      ok ("count(index-of((10, 20, 30, 40), 35))", "0"),
      ok ("string-join(index-of((10, 20, 30, 30, 20, 10), 20), \",\")", "2,5"),
      ok ("string-join(index-of((\"a\", \"sport\", \"and\", \"a\", \"pastime\"), \"a\"), \",\")", "1,4"),
      ok ("count(index-of(current-date(), 23))", "0"),
      ok ("string-join(index-of([1, [5, 6], [6, 7]], 6), \",\")", "3,4"),
      // incomparable pairs are unequal, not errors; NaN equals nothing.
      ok ("count(index-of((4, '4'), '4'))", "1"),
      ok ("count(index-of((xs:double('NaN')), xs:double('NaN')))", "0"),
      ok ("string-join(index-of((1, 1.0, 1e0, xs:float(1)), 1), ',')", "1,2,3,4"),
      ok ("string-join(index-of(('a', 'A'), 'a', 'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive'), ',')", "1,2"),
      ok ("string-join(index-of(('a', 'A'), 'a', 'http://www.w3.org/2005/xpath-functions/collation/codepoint'), ',')", "1"),
      ok ("string-join(index-of((xs:untypedAtomic('x'), 'x'), 'x'), ',')", "1,2")
    );
    run (
      // $start is xs:double: a string or the empty sequence is a type error.
      err ("subsequence((1,2), 'a')", "XPTY0004"),
      err ("subsequence((1,2), ())", "XPTY0004"),
      err ("subsequence((1,2), 1, 'x')", "XPTY0004"),
      err ("subsequence((1,2), xs:untypedAtomic('z'))", "FORG0001"),
      // $search is a single atomic value.
      err ("index-of((1,2), (1,2))", "XPTY0004"),
      err ("index-of((1,2), ())", "XPTY0004"),
      // $collation is xs:string, unknown URIs FOCH0002.
      err ("index-of((1,2), 1, ())", "XPTY0004"),
      err ("index-of(('a'), 'a', 'http://example.com/no-such-collation')", "FOCH0002"),
      err ("distinct-values(('a'), 'http://example.com/no-such-collation')", "FOCH0002")
    );
  }

  /**
   * F&O 3.1 §14.4: fn:count/avg/max/min/sum, with the REC examples.
   */
  @Test
  public void seqSpecAggregates ()
  {
    run (
      // §14.4.2 fn:avg. $seq3 := (3, 4, 5).
      ok ("avg((3, 4, 5))", "4"),
      ok ("avg((3, 4, 5)) instance of xs:decimal", "true"),
      ok ("avg((xs:yearMonthDuration(\"P20Y\"), xs:yearMonthDuration(\"P10M\")))", "P10Y5M"),
      ok ("avg((xs:dayTimeDuration(\"PT2H\"), xs:dayTimeDuration(\"PT4H\")))", "PT3H"),
      ok ("count(avg(()))", "0"),
      ok ("string(avg((xs:float('INF'), xs:float('-INF'))))", "NaN"),
      ok ("avg((xs:float('INF'), xs:float('-INF'))) instance of xs:float", "true"),
      ok ("string(avg((3, 4, 5, xs:float('NaN'))))", "NaN"),
      ok ("avg((1.5, 2.5))", "2"),
      ok ("avg((1.5, 2.5)) instance of xs:decimal", "true"),
      ok ("avg((1, 2e0))", "1.5"),
      ok ("avg((1, 2e0)) instance of xs:double", "true"),
      ok ("avg((xs:float(1), xs:float(2))) instance of xs:float", "true"),
      ok ("avg((xs:float(1), 2)) instance of xs:float", "true"),
      // untypedAtomic is cast to xs:double.
      ok ("avg((xs:untypedAtomic('1'), xs:untypedAtomic('2'))) instance of xs:double", "true"),
      ok ("avg([1, 2, 3])", "2"),

      // §14.4.3 fn:max.
      ok ("max((3,4,5))", "5"),
      ok ("max([3,4,5])", "5"),
      ok ("max((xs:integer(5), xs:float(5.0), xs:double(0))) instance of xs:double", "true"),
      ok ("max((xs:integer(5), xs:float(5.0), xs:double(0)))", "5"),
      ok ("max((fn:current-date(), xs:date(\"2100-01-01\")))", "2100-01-01"),
      ok ("max((\"a\", \"b\", \"c\"))", "c"),
      ok ("max((1, xs:float(2))) instance of xs:float", "true"),
      ok ("string(max((1, xs:double('NaN'), 3)))", "NaN"),
      ok ("string(max((1, xs:float('NaN')))) ", "NaN"),
      ok ("max((1, xs:float('NaN'))) instance of xs:float", "true"),
      ok ("max((xs:dayTimeDuration('PT1H'), xs:dayTimeDuration('PT2H')))", "PT2H"),
      ok ("max((xs:yearMonthDuration('P1Y'), xs:yearMonthDuration('P2M')))", "P1Y"),
      ok ("max((true(), false()))", "true"),
      ok ("max((xs:time('10:00:00'), xs:time('09:00:00')))", "10:00:00"),
      ok ("max(('a', 'B'), 'http://www.w3.org/2005/xpath-functions/collation/codepoint')", "a"),
      // An xs:anyURI among strings is promoted to xs:string (§14.4.3 rules).
      ok ("max((xs:anyURI('b'), 'a')) instance of xs:string", "true"),
      ok ("max((xs:anyURI('b'), 'a'))", "b"),
      ok ("max((xs:untypedAtomic('7'), 3)) instance of xs:double", "true"),
      ok ("max((1.5, 2)) instance of xs:decimal", "true"),
      ok ("max((1.5, 2))", "2"),
      ok ("count(max(()))", "0"),

      // §14.4.4 fn:min.
      ok ("min((3,4,5))", "3"),
      ok ("min([3,4,5])", "3"),
      ok ("min((xs:integer(5), xs:float(5), xs:double(10))) instance of xs:double", "true"),
      ok ("min((xs:integer(5), xs:float(5), xs:double(10)))", "5"),
      ok ("min((fn:current-date(), xs:date(\"1900-01-01\")))", "1900-01-01"),
      ok ("min((\"a\", \"b\", \"c\"))", "a"),
      // equal minimal items: which one is returned is implementation-dependent.
      ok ("min(('b', 'A', 'a'), 'http://www.w3.org/2013/collation/UCA?strength=primary') = ('A', 'a')", "true"),
      ok ("min((xs:untypedAtomic('3'), 7)) instance of xs:double", "true"),
      ok ("string(min((xs:double('NaN'), 1)))", "NaN"),

      // §14.4.5 fn:sum.
      ok ("sum((xs:yearMonthDuration(\"P20Y\"), xs:yearMonthDuration(\"P10M\")))", "P20Y10M"),
      ok ("sum((xs:dayTimeDuration(\"PT1H\"), xs:dayTimeDuration(\"PT30M\")))", "PT1H30M"),
      ok ("sum((3, 4, 5))", "12"),
      ok ("sum(())", "0"),
      ok ("sum(()) instance of xs:integer", "true"),
      ok ("count(sum((),()))", "0"),
      ok ("sum((1 to 100)[. lt 0], 0)", "0"),
      ok ("sum((xs:yearMonthDuration(\"P20Y\"), xs:yearMonthDuration(\"P10M\")), \"ein Augenblick\")", "P20Y10M"),
      ok ("sum([1, 2, 3])", "6"),
      ok ("sum([[1, 2], [3, 4]])", "10"),
      ok ("sum((1.5, 2.25))", "3.75"),
      ok ("sum((1.5, 2.25)) instance of xs:decimal", "true"),
      ok ("sum((1, 2.5e0)) instance of xs:double", "true"),
      ok ("sum((xs:float(1), 2)) instance of xs:float", "true"),
      ok ("sum((xs:untypedAtomic('1'), 2)) instance of xs:double", "true"),
      ok ("sum(xs:unsignedShort(3)) instance of xs:unsignedShort", "true"),
      ok ("sum((99999999999999999999, 1))", "100000000000000000000")
    );
    run (
      // FORG0006: mixing non-numeric / unordered types.
      err ("sum(('a', 'b'))", "FORG0006"),
      err ("avg(('a', 'b'))", "FORG0006"),
      err ("sum((xs:yearMonthDuration('P1Y'), xs:dayTimeDuration('PT1H')))", "FORG0006"),
      err ("sum((xs:duration('P1Y'), xs:duration('P1M')))", "FORG0006"),
      err ("sum((1, xs:dayTimeDuration('PT1H')))", "FORG0006"),
      err ("max((1, 'a'))", "FORG0006"),
      err ("min((xs:date('2000-01-01'), 1))", "FORG0006"),
      err ("max((xs:duration('P1Y'), xs:duration('P1M')))", "FORG0006"),
      err ("max((QName('', 'a'), QName('', 'b')))", "FORG0006"),
      err ("max((xs:gYear('2000'), xs:gYear('2001')))", "FORG0006"),
      err ("max((xs:yearMonthDuration('P1Y'), xs:dayTimeDuration('PT1H')))", "FORG0006"),
      // untypedAtomic is cast to xs:double first.
      err ("max((xs:untypedAtomic('x'), 1))", "FORG0001"),
      err ("max(('a'), 'http://example.com/no-such-collation')", "FOCH0002"),
      err ("min(('a'), 'http://example.com/no-such-collation')", "FOCH0002")
    );
  }

  /**
   * F&O 3.1 §16.2 higher-order sequence functions and §14.2.4 fn:sort, with the
   * REC examples.
   */
  @Test
  public void seqSpecHigherOrder ()
  {
    run (
      ok ("string-join(for-each(1 to 5, function($a) { $a * $a }), \",\")", "1,4,9,16,25"),
      ok ("string-join(for-each((\"john\", \"jane\"), fn:string-to-codepoints#1), \",\")", "106,111,104,110,106,97,110,101"),
      ok ("string-join(for-each((\"23\", \"29\"), xs:int#1), \",\")", "23,29"),
      // a map is a function item of arity 1.
      ok ("string-join(for-each((\"a\", \"b\"), map{\"a\": 1, \"b\": 2}), \",\")", "1,2"),
      ok ("string-join(for-each((1, 2), [\"x\", \"y\"]), \",\")", "x,y"),

      ok ("string-join(filter(1 to 10, function($a) {$a mod 2 = 0}), \",\")", "2,4,6,8,10"),
      ok ("count(filter((), fn:lang(\"en\", ?)))", "0"),
      ok ("string-join(filter((1, 2), map{1: true(), 2: false()}), \",\")", "1"),

      ok ("fold-left(1 to 5, 0, function($a, $b) { $a + $b })", "15"),
      ok ("fold-left((2,3,5,7), 1, function($a, $b) { $a * $b })", "210"),
      ok ("fold-left((true(), false(), false()), false(), function($a, $b) { $a or $b })", "true"),
      ok ("fold-left((true(), false(), false()), false(), function($a, $b) { $a and $b })", "false"),
      ok ("string-join(fold-left(1 to 5, (), function($a, $b) {($b, $a)}), \",\")", "5,4,3,2,1"),
      ok ("fold-left(1 to 5, \"\", fn:concat(?, \".\", ?))", ".1.2.3.4.5"),
      ok ("fold-left(1 to 5, \"$zero\", fn:concat(\"$f(\", ?, \", \", ?, \")\"))", "$f($f($f($f($f($zero, 1), 2), 3), 4), 5)"),
      ok ("fold-left(1 to 5, map{}, function($map, $n) {map:put($map, $n, $n*2)})(4)", "8"),

      ok ("fold-right(1 to 5, 0, function($a, $b) { $a + $b })", "15"),
      ok ("fold-right(1 to 5, \"\", fn:concat(?, \".\", ?))", "1.2.3.4.5."),
      ok ("fold-right(1 to 5, \"$zero\", concat(\"$f(\", ?, \", \", ?, \")\"))", "$f(1, $f(2, $f(3, $f(4, $f(5, $zero)))))"),

      ok ("string-join(for-each-pair((\"a\", \"b\", \"c\"), (\"x\", \"y\", \"z\"), concat#2), \",\")", "ax,by,cz"),
      ok ("string-join(for-each-pair(1 to 5, 1 to 5, function($a, $b){10*$a + $b}), \",\")", "11,22,33,44,55"),
      // the shorter sequence bounds the result.
      ok ("string-join(for-each-pair(1 to 3, 1 to 2, function($a, $b){$a + $b}), \",\")", "2,4"),

      // §14.2.4? fn:sort examples (F&O 3.1 §16.2.?? fn:sort).
      ok ("string-join(sort((1, 4, 6, 5, 3)), \",\")", "1,3,4,5,6"),
      ok ("string-join(sort((1, -2, 5, 10, -10, 10, 8), (), fn:abs#1), \",\")", "1,-2,5,8,10,-10,10"),
      ok ("string-join(sort((\"b\", \"A\", \"a\")), \",\")", "A,a,b"),
      ok ("string-join(sort((\"b\", \"A\", \"a\", \"B\"), \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\"), \",\")", "A,a,b,B"),
      // NaN sorts before every other value; a shorter key sorts first.
      ok ("string-join(sort((3, xs:double('NaN'), 1)), \",\")", "NaN,1,3"),
      ok ("string-join(for-each(sort(([2,1],[1],[1,1]), (), function($a){$a}), function($a){string-join($a?*, '')}), \",\")", "1,11,21"),
      ok ("string-join(sort((xs:untypedAtomic('b'), 'a')), \",\")", "a,b"),
      ok ("count(sort(()))", "0")
    );
    run (
      // a function argument must be a single function of the declared arity.
      err ("for-each((1), (abs#1, abs#1))", "XPTY0004"),
      err ("for-each((1), 1)", "XPTY0004"),
      err ("for-each((1), concat#2)", "XPTY0004"),
      err ("filter((1), abs#1)", "XPTY0004"),
      err ("filter((1), function($a){($a, $a)})", "XPTY0004"),
      err ("fold-left((1), 0, abs#1)", "XPTY0004"),
      err ("fold-right((1), 0, abs#1)", "XPTY0004"),
      err ("for-each-pair((1), (2), abs#1)", "XPTY0004"),
      err ("sort((1,2), (), concat#2)", "XPTY0004"),
      // keys must be mutually comparable.
      err ("sort((1, \"a\"))", "XPTY0004"),
      err ("sort(('a'), 'http://example.com/no-such-collation')", "FOCH0002"),
      // errors raised by the callback propagate.
      err ("for-each((0), function($a){1 idiv $a})", "FOAR0001"),
      err ("filter((0), function($a){1 idiv $a = 1})", "FOAR0001"),
      err ("fold-left((0), 0, function($a, $b){1 idiv $b})", "FOAR0001"),
      err ("fold-right((0), 0, function($a, $b){1 idiv $a})", "FOAR0001"),
      err ("for-each-pair((0), (0), function($a, $b){1 idiv $a})", "FOAR0001"),
      err ("sort((0, 1), (), function($a){1 idiv $a})", "FOAR0001")
    );
  }

  /**
   * F&O 3.1 §4.8 math functions (the subset registered in seqfuncs.go).
   */
  @Test
  public void seqSpecMath ()
  {
    run (
      ok ("math:pi()", "3.141592653589793"),
      ok ("count(math:sqrt(()))", "0"),
      ok ("math:sqrt(0.0e0)", "0"),
      ok ("string(math:sqrt(-0.0e0))", "-0"),
      ok ("math:sqrt(1.0e6)", "1000"),
      ok ("string(math:sqrt(-2.0e0))", "NaN"),
      ok ("string(math:sqrt(xs:double('INF')))", "INF"),
      ok ("math:sin(0)", "0"),
      ok ("math:cos(0)", "1"),
      ok ("math:exp(0)", "1"),
      ok ("abs(math:exp(1) - 2.718281828459045e0) lt 1e-15", "true"),
      ok ("math:log(1)", "0"),
      ok ("string(math:log(0))", "-INF"),
      ok ("string(math:log(-1))", "NaN"),
      ok ("math:log10(1.0e3)", "3"),
      ok ("string(math:log10(0))", "-INF"),
      ok ("count(math:pow((), 93.7))", "0"),
      ok ("math:pow(2, 3)", "8"),
      ok ("math:pow(-2, 3)", "-8"),
      ok ("math:pow(2, -3)", "0.125"),
      ok ("math:pow(0, 0)", "1"),
      ok ("string(math:pow(0e0, -3))", "INF"),
      ok ("math:pow(16, 0.5e0)", "4"),
      ok ("string(math:pow(-1, 0.5))", "NaN"),
      ok ("math:pow(xs:double('NaN'), 0)", "1"),
      ok ("math:pow(-1, xs:double('INF'))", "1")
    );
  }

  /**
   * F&O 3.1 §17.1 map functions registered in seqfuncs.go.
   */
  @Test
  public void seqSpecMapFunctions ()
  {
    run (
      ok ("map:get(map{1:\"yes\", 2:\"no\"}, 1)", "yes"),
      ok ("count(map:get(map{1:\"yes\", 2:\"no\"}, 3))", "0"),
      ok ("count(map:get(map{1:\"yes\", 2:\"no\", 3:()}, 3))", "0"),
      ok ("map:contains(map{1:\"yes\", 2:\"no\"}, 1)", "true"),
      ok ("map:contains(map{1:\"yes\", 2:\"no\"}, 3)", "false"),
      ok ("map:contains(map{\"xyz\":23}, \"xyz\")", "true"),
      ok ("map:contains(map{\"abc\":23, \"xyz\":()}, \"xyz\")", "true"),
      ok ("map:contains(map{}, \"xyz\")", "false"),
      // keys compare with op:same-key: 1 and 1.0 are the same key.
      ok ("map:contains(map{1: \"a\"}, 1.0)", "true"),
      ok ("map:size(map{})", "0"),
      ok ("map:size(map{\"true\":1, \"false\":0})", "2"),
      ok ("string-join(for-each(sort(map:keys(map{1:\"yes\", 2:\"no\"})), string#1), \",\")", "1,2"),
      ok ("map:put(map{\"Su\":\"Sunday\"}, \"Mo\", \"Monday\")(\"Mo\")", "Monday"),
      ok ("map:size(map:put(map{\"Su\":\"Sunday\"}, \"Su\", \"Sonntag\"))", "1"),
      ok ("map:put(map{\"Su\":\"Sunday\"}, \"Su\", \"Sonntag\")(\"Su\")", "Sonntag"),
      // the original map is unchanged.
      ok ("let $m := map{\"a\":1} return (map:put($m, \"b\", 2), map:size($m))[2]", "1"),
      ok ("map:size(map:remove(map{0:\"Sun\", 1:\"Mon\", 2:\"Tue\"}, 1))", "2"),
      ok ("map:contains(map:remove(map{0:\"Sun\", 1:\"Mon\", 2:\"Tue\"}, 1), 1)", "false"),
      ok ("map:size(map:remove(map{0:\"Sun\", 1:\"Mon\", 2:\"Tue\"}, 23))", "3"),
      ok ("map:size(map:remove(map{0:\"Sun\", 1:\"Mon\", 2:\"Tue\"}, (0, 2)))", "1"),
      ok ("map:size(map:remove(map{0:\"Sun\", 1:\"Mon\", 2:\"Tue\"}, ()))", "3"),
      ok ("map:entry(\"M\", \"Monday\")(\"M\")", "Monday"),
      ok ("map:size(map:entry(\"M\", \"Monday\"))", "1"),
      // map:merge (§17.1.1) and its duplicates option.
      ok ("map:size(map:merge(()))", "0"),
      ok ("map:size(map:merge((map:entry(0, \"no\"), map:entry(1, \"yes\"))))", "2"),
      ok ("map:merge((map{0:\"a\"}, map{0:\"b\"}))(0)", "a"),
      ok ("map:merge((map{0:\"a\"}, map{0:\"b\"}), map{\"duplicates\":\"use-first\"})(0)", "a"),
      ok ("map:merge((map{0:\"a\"}, map{0:\"b\"}), map{\"duplicates\":\"use-last\"})(0)", "b"),
      ok ("string-join(map:merge((map{0:\"a\"}, map{0:\"b\"}), map{\"duplicates\":\"combine\"})(0), \",\")", "a,b"),
      ok ("map:merge((map{0:\"a\"}, map{0:\"b\"}), map{\"duplicates\":\"use-any\"})(0) = (\"a\", \"b\")", "true"),
      ok ("map:merge((map{0:\"a\"}, map{1:\"b\"}), map{\"duplicates\":\"reject\"})(1)", "b"),
      ok ("map:merge((map{0:\"a\"}), map{})(0)", "a"),
      ok ("string-join(sort(map:for-each(map{1:\"yes\", 2:\"no\"}, function($k, $v){$v})), \",\")", "no,yes"),
      ok ("sum(map:for-each(map{1:10, 2:20}, function($k, $v){$k + $v}))", "33")
    );
    run (
      err ("map:get((map{}, map{}), 1)", "XPTY0004"),
      err ("map:get(1, 1)", "XPTY0004"),
      err ("map:get(map{}, ())", "XPTY0004"),
      err ("map:get(map{}, (1, 2))", "XPTY0004"),
      err ("map:contains((), 1)", "XPTY0004"),
      err ("map:contains(map{}, (1, 2))", "XPTY0004"),
      err ("map:put((), 1, 2)", "XPTY0004"),
      err ("map:put(map{}, (), 2)", "XPTY0004"),
      err ("map:remove((), 1)", "XPTY0004"),
      err ("map:entry((), 1)", "XPTY0004"),
      err ("map:merge((map{0:\"a\"}, map{0:\"b\"}), map{\"duplicates\":\"reject\"})", "FOJS0003"),
      err ("map:merge((map{0:\"a\"}), map{\"duplicates\":\"bogus\"})", "FOJS0005"),
      err ("map:merge((map{0:\"a\"}), ())", "XPTY0004"),
      err ("map:for-each(map{1:0}, function($k, $v){1 idiv $v})", "FOAR0001")
    );
  }

  /**
   * F&O 3.1 §17.3 array functions registered in seqfuncs.go.
   */
  @Test
  public void seqSpecArrayFunctions ()
  {
    run (
      ok ("array:size([\"a\", \"b\", \"c\"])", "3"),
      ok ("array:size([\"a\", [\"b\", \"c\"]])", "2"),
      ok ("array:size([ ])", "0"),
      ok ("array:size([[ ]])", "1"),
      ok ("array:get([\"a\", \"b\", \"c\"], 2)", "b"),
      ok ("array:get([\"a\", [\"b\", \"c\"]], 2)(1)", "b"),
      ok ("array:size(array:append([\"a\", \"b\", \"c\"], \"d\"))", "4"),
      ok ("array:get(array:append([\"a\", \"b\", \"c\"], \"d\"), 4)", "d"),
      ok ("array:size(array:append([\"a\", \"b\", \"c\"], (\"d\", \"e\")))", "4"),
      ok ("array:size(array:append([\"a\", \"b\", \"c\"], [\"d\", \"e\"]))", "4"),
      ok ("string-join(array:flatten([1, 4, 6, 5, 3]), \",\")", "1,4,6,5,3"),
      ok ("string-join(array:flatten(([1, 2, 5], [[10, 11], 12], [], 13)), \",\")", "1,2,5,10,11,12,13"),
      ok ("string-join(array:flatten([(1,0), (1,1), (0,1), (0,0)]), \",\")", "1,0,1,1,0,1,0,0"),
      ok ("array:size(array:join(()))", "0"),
      ok ("array:size(array:join([1, 2, 3]))", "3"),
      ok ("array:size(array:join(([\"a\", \"b\"], [\"c\", \"d\"])))", "4"),
      ok ("array:size(array:join(([\"a\", \"b\"], [\"c\", \"d\"], [ ])))", "4"),
      ok ("array:size(array:join(([\"a\", \"b\"], [\"c\", \"d\"], [[\"e\", \"f\"]])))", "5"),
      ok ("string-join(array:flatten(array:for-each([\"A\", \"B\", 1, 2], function($z) {$z instance of xs:integer})), \",\")", "false,false,true,true"),
      ok ("array:size(array:for-each([\"the cat\", \"sat\", \"on the mat\"], fn:tokenize#1))", "3"),
      ok ("count(array:for-each([\"the cat\", \"sat\", \"on the mat\"], fn:tokenize#1)(3))", "3")
    );
    run (
      err ("array:get([\"a\"], 0)", "FOAY0001"),
      err ("array:get([\"a\"], 2)", "FOAY0001"),
      err ("array:get([], 1)", "FOAY0001"),
      err ("array:get([\"a\"], (1, 1))", "XPTY0004"),
      err ("array:get([\"a\"], 1.0)", "XPTY0004"),
      err ("array:get(map{}, 1)", "XPTY0004"),
      err ("array:for-each([1], concat#2)", "XPTY0004"),
      err ("array:for-each([0], function($a){1 idiv $a})", "FOAR0001")
    );
  }

  /**
   * Aggregates and sequence functions over nodes: atomization yields
   * xs:untypedAtomic, which aggregates cast to xs:double (F&O 3.1 §14.4).
   */
  @Test
  public void seqSpecNodes ()
  {
    run (SQ_DOC,
      ok ("sum(//n[@v != 2]/@v)", "4"),
      ok ("sum(//n/@v) instance of xs:double", "true"),
      ok ("max(//n/@v)", "3"),
      ok ("min(//n/@v)", "1"),
      ok ("avg(//n/@v)", "2"),
      ok ("count(distinct-values(//n/@v))", "3"),
      ok ("string-join(index-of(//n/@v, '1'), ',')", "2"),
      ok ("count(subsequence(//n, //n[2]))", "3"),
      ok ("string-join(remove(//n/@v, //n[2]), ',')", "1,2")
    );
    run (SQ_DOC,
      err ("sum(//n)", "FORG0001"),
      err ("remove((1,2), //n[3])", "FORG0001")
    );
  }
}
