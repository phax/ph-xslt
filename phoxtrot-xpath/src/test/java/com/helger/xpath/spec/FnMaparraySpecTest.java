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

import static com.helger.xpath.spec.FnCoreTestHelper.coreRun;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xpath/fn_maparray_spec_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnMaparraySpecTest
{
  /**
   * Covers the array: functions of F&O 3.1 §17.3 and map:find
   * (§17.1.9) with the REC's own examples; array results are compared with
   * deep-equal against the expected array constructor.
   */
  @Test
  public void mapArraySpec ()
  {
    final String sAbcd = "[\"a\", \"b\", \"c\", \"d\"]";
    coreRun (
      // §17.3.5 array:subarray
      ok ("deep-equal(array:subarray(" + sAbcd + ", 2), [\"b\", \"c\", \"d\"])", "true"),
      ok ("deep-equal(array:subarray(" + sAbcd + ", 5), [])", "true"),
      ok ("deep-equal(array:subarray(" + sAbcd + ", 2, 0), [])", "true"),
      ok ("deep-equal(array:subarray(" + sAbcd + ", 2, 1), [\"b\"])", "true"),
      ok ("deep-equal(array:subarray(" + sAbcd + ", 2, 2), [\"b\", \"c\"])", "true"),
      ok ("deep-equal(array:subarray(" + sAbcd + ", 5, 0), [])", "true"),
      ok ("deep-equal(array:subarray([], 1, 0), [])", "true"),
      err ("array:subarray(" + sAbcd + ", 0)", "FOAY0001"),
      err ("array:subarray(" + sAbcd + ", 6)", "FOAY0001"),
      err ("array:subarray(" + sAbcd + ", 2, -1)", "FOAY0002"),
      err ("array:subarray(" + sAbcd + ", 2, 4)", "FOAY0001"),
      err ("array:subarray('a', 1)", "XPTY0004"),

      // §17.3.6 array:remove
      ok ("deep-equal(array:remove([\"a\"], 1), [])", "true"),
      ok ("deep-equal(array:remove(" + sAbcd + ", 1), [\"b\", \"c\", \"d\"])", "true"),
      ok ("deep-equal(array:remove([\"a\", \"b\"], 2), [\"a\"])", "true"),
      ok ("deep-equal(array:remove(" + sAbcd + ", 1 to 3), [\"d\"])", "true"),
      ok ("deep-equal(array:remove([], ()), [])", "true"),
      err ("array:remove([\"a\"], 2)", "FOAY0001"),
      err ("array:remove([\"a\"], 0)", "FOAY0001"),
      err ("array:remove(1, 1)", "XPTY0004"),

      // §17.3.7 array:insert-before
      ok ("deep-equal(array:insert-before(" + sAbcd + ", 3, (\"x\", \"y\")), [\"a\", \"b\", (\"x\", \"y\"), \"c\", \"d\"])", "true"),
      ok ("deep-equal(array:insert-before(" + sAbcd + ", 5, (\"x\", \"y\")), [\"a\", \"b\", \"c\", \"d\", (\"x\", \"y\")])", "true"),
      ok ("deep-equal(array:insert-before(" + sAbcd + ", 3, [\"x\", \"y\"]), [\"a\", \"b\", [\"x\", \"y\"], \"c\", \"d\"])", "true"),
      ok ("deep-equal(array:insert-before([], 1, 'x'), ['x'])", "true"),
      err ("array:insert-before(" + sAbcd + ", 6, 'x')", "FOAY0001"),
      err ("array:insert-before(" + sAbcd + ", 0, 'x')", "FOAY0001"),
      err ("array:insert-before('a', 1, 'x')", "XPTY0004"),

      // §17.3.4 array:put
      ok ("deep-equal(array:put([\"a\", \"b\", \"c\"], 2, \"d\"), [\"a\", \"d\", \"c\"])", "true"),
      ok ("deep-equal(array:put([\"a\", \"b\", \"c\"], 2, (\"d\", \"e\")), [\"a\", (\"d\", \"e\"), \"c\"])", "true"),
      ok ("deep-equal(array:put([\"a\"], 1, [\"d\", \"e\"]), [[\"d\", \"e\"]])", "true"),
      // the input array is unchanged
      ok ("let $a := [\"a\", \"b\"], $b := array:put($a, 1, \"z\") return ($a(1), $b(1))", "a|z"),
      err ("array:put([], 1, 'x')", "FOAY0001"),
      err ("array:put([\"a\"], 2, 'x')", "FOAY0001"),
      err ("array:put('a', 1, 'x')", "XPTY0004"),

      // §17.3.9/§17.3.10 array:head / array:tail
      ok ("array:head([5, 6, 7, 8])", "5"),
      ok ("deep-equal(array:head([[\"a\", \"b\"], [\"c\", \"d\"]]), [\"a\", \"b\"])", "true"),
      ok ("deep-equal(array:head([(\"a\", \"b\"), (\"c\", \"d\")]), (\"a\", \"b\"))", "true"),
      err ("array:head([])", "FOAY0001"),
      err ("array:head(1)", "XPTY0004"),
      ok ("deep-equal(array:tail([5, 6, 7, 8]), [6, 7, 8])", "true"),
      ok ("deep-equal(array:tail([5]), [])", "true"),
      err ("array:tail([])", "FOAY0001"),
      err ("array:tail(1)", "XPTY0004"),

      // §17.3.11 array:reverse
      ok ("deep-equal(array:reverse(" + sAbcd + "), [\"d\", \"c\", \"b\", \"a\"])", "true"),
      ok ("deep-equal(array:reverse([(\"a\", \"b\"), (\"c\", \"d\")]), [(\"c\", \"d\"), (\"a\", \"b\")])", "true"),
      ok ("deep-equal(array:reverse([(1 to 5)]), [(1, 2, 3, 4, 5)])", "true"),
      ok ("deep-equal(array:reverse([]), [])", "true"),
      err ("array:reverse(1)", "XPTY0004"),

      // §17.3.14 array:filter
      ok ("deep-equal(array:filter([\"A\", \"B\", 1, 2], function($x) {$x instance of xs:integer}), [1, 2])", "true"),
      ok ("deep-equal(array:filter([\"the\", \"cat\", \"sat\", \"on\", \"the\", \"mat\"], function($s){fn:string-length($s) gt 1}), [\"the\", \"cat\", \"sat\", \"on\", \"the\", \"mat\"])", "true"),
      ok ("deep-equal(array:filter([\"A\", \"B\", \"\", 0, 1], boolean#1), [\"A\", \"B\", 1])", "true"),
      // the predicate must return a single xs:boolean
      err ("array:filter([\"a\"], function($x) {$x})", "XPTY0004"),
      err ("array:filter(1, boolean#1)", "XPTY0004"),
      err ("array:filter([\"a\"], abs#1)", "XPTY0004"),
      err ("array:filter([\"a\"], concat#2)", "XPTY0004"),

      // §17.3.15/§17.3.16 array:fold-left / array:fold-right
      ok ("array:fold-left([true(), true(), false()], true(), function($x, $y){$x and $y})", "false"),
      ok ("array:fold-left([true(), true(), false()], false(), function($x, $y){$x or $y})", "true"),
      ok ("deep-equal(array:fold-left([1, 2, 3], [], function($x, $y){[$x, $y]}), [[[[], 1], 2], 3])", "true"),
      ok ("array:fold-left([], 7, function($x, $y){$x + $y})", "7"),
      err ("array:fold-left([1], 0, function($x, $y){error()})", "FOER0000"),
      err ("array:fold-left(1, 0, function($x, $y){$x})", "XPTY0004"),
      err ("array:fold-left([1], 0, abs#1)", "XPTY0004"),
      ok ("array:fold-right([true(), true(), false()], true(), function($x, $y){$x and $y})", "false"),
      ok ("array:fold-right([true(), true(), false()], false(), function($x, $y){$x or $y})", "true"),
      ok ("deep-equal(array:fold-right([1, 2, 3], [], function($x, $y){[$x, $y]}), [1, [2, [3, []]]])", "true"),
      err ("array:fold-right([1], 0, function($x, $y){error()})", "FOER0000"),
      err ("array:fold-right(1, 0, function($x, $y){$x})", "XPTY0004"),
      err ("array:fold-right([1], 0, abs#1)", "XPTY0004"),

      // §17.3.17 array:for-each-pair
      ok ("deep-equal(array:for-each-pair([\"A\", \"B\", \"C\"], [1, 2, 3], function($x, $y) { array {$x, $y}}), [[\"A\", 1], [\"B\", 2], [\"C\", 3]])", "true"),
      ok ("let $A := [\"A\", \"B\", \"C\", \"D\"] return deep-equal(array:for-each-pair($A, array:tail($A), concat#2), [\"AB\", \"BC\", \"CD\"])", "true"),
      ok ("deep-equal(array:for-each-pair([1, 2], [10, 20, 30], function($x, $y){$x + $y}), [11, 22])", "true"),
      err ("array:for-each-pair([1], [2], function($x, $y){error()})", "FOER0000"),
      err ("array:for-each-pair(1, [2], concat#2)", "XPTY0004"),
      err ("array:for-each-pair([1], 2, concat#2)", "XPTY0004"),
      err ("array:for-each-pair([1], [2], abs#1)", "XPTY0004"),

      // §17.3.19 array:sort
      ok ("deep-equal(array:sort([1, 4, 6, 5, 3]), [1, 3, 4, 5, 6])", "true"),
      ok ("deep-equal(array:sort([1, -2, 5, 10, -10, 10, 8], (), fn:abs#1), [1, -2, 5, 8, 10, -10, 10])", "true"),
      ok ("deep-equal(array:sort([(1, 0), (1, 1), (0, 1), (0, 0)]), [(0, 0), (0, 1), (1, 0), (1, 1)])", "true"),
      // a key that is a prefix of another sorts first
      ok ("deep-equal(array:sort([(1, 2), 1, ()]), [(), 1, (1, 2)])", "true"),
      // NaN sorts before every other numeric value
      ok ("array:sort([2, xs:double('NaN'), 1])?* ! string(.)", "NaN|1|2"),
      ok ("array:sort([xs:double('NaN'), xs:double('NaN')])?* ! string(.)", "NaN|NaN"),
      ok ("array:sort([\"b\", \"a\", \"C\"])?* ! string(.)", "C|a|b"),
      ok ("array:sort([\"b\", \"a\", \"C\"], \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")?* ! string(.)", "a|b|C"),
      ok ("array:sort([\"b\", \"a\", \"C\"], \"http://www.w3.org/2005/xpath-functions/collation/codepoint\")?* ! string(.)", "C|a|b"),
      // stable for equal keys
      ok ("array:sort([\"b1\", \"a1\", \"b2\", \"a2\"], (), substring(?, 1, 1))?* ! string(.)", "a1|a2|b1|b2"),
      err ("array:sort([1, \"a\"])", "XPTY0004"),
      err ("array:sort([xs:date('2001-01-01'), 1])", "XPTY0004"),
      err ("array:sort([1, 2], (), function($x){error()})", "FOER0000"),
      err ("array:sort([\"a\", \"b\"], \"http://example.com/no-such-collation\")", "FOCH0002"),
      err ("array:sort(1)", "XPTY0004"),

      // §17.1.9 map:find
      ok ("let $responses := [map{0:'no', 1:'yes'}, map{0:'non', 1:'oui'}, map{0:'nein', 1:('ja', 'doch')}] return deep-equal(map:find($responses, 0), ['no', 'non', 'nein'])", "true"),
      ok ("let $responses := [map{0:'no', 1:'yes'}, map{0:'non', 1:'oui'}, map{0:'nein', 1:('ja', 'doch')}] return deep-equal(map:find($responses, 1), ['yes', 'oui', ('ja', 'doch')])", "true"),
      ok ("let $responses := [map{0:'no', 1:'yes'}, map{0:'non', 1:'oui'}, map{0:'nein', 1:('ja', 'doch')}] return deep-equal(map:find($responses, 2), [])", "true"),
      ok ("let $inventory := map{\"name\":\"car\", \"id\":\"QZ123\", \"parts\": [map{\"name\":\"engine\", \"id\":\"YW678\", \"parts\":[]}]} return array:size(map:find($inventory, \"parts\"))", "2"),
      ok ("deep-equal(map:find((1, \"a\"), \"a\"), [])", "true")
    );
  }
}
