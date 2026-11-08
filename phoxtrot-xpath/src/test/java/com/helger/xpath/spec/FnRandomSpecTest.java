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

import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xpath/fn_random_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnRandomSpecTest
{
  /**
   * fn:random-number-generator (F&O 3.1 §4.9.1). The numbers themselves are
   * implementation-dependent, so these tests check the rules that ARE specified:
   * the map's entries and their types, number in [0,1), determinism for equal
   * arguments within one execution scope, next() yielding another generator,
   * permute returning a permutation of its argument.
   */
  @Test
  public void randomNumberGeneratorSpec ()
  {
    run (
      ok ("random-number-generator() instance of map(xs:string, item())", "true"),
      ok ("random-number-generator(42) instance of map(xs:string, item())", "true"),
      ok ("every $k in ('number', 'next', 'permute') satisfies map:contains(random-number-generator(1), $k)", "true"),
      ok ("random-number-generator(1)?number instance of xs:double", "true"),
      ok ("random-number-generator(1)?next instance of function() as map(*)", "true"),
      ok ("function-arity(random-number-generator(1)?next)", "0"),
      ok ("function-arity(random-number-generator(1)?permute)", "1"),
      // number is in [0, 1), for many successive generators and seed types.
      ok ("every $s in (0, 1, 42, -7, 'seed', xs:date('2020-01-01'), 3.5e0, true()) " +
          "satisfies (let $g := random-number-generator($s) return $g?number ge 0 and $g?number lt 1)",
          "true"),
      ok ("let $nums := fold-left(1 to 200, [random-number-generator(9)], " +
          "function($acc, $i) { array:append($acc, $acc(array:size($acc))?next()) }) " +
          "return every $g in $nums?* satisfies ($g?number ge 0e0 and $g?number lt 1e0)",
          "true"),
      // Deterministic: same arguments give the same results.
      ok ("random-number-generator(42)?number eq random-number-generator(42)?number", "true"),
      ok ("random-number-generator()?number eq random-number-generator()?number", "true"),
      ok ("random-number-generator(())?number eq random-number-generator(())?number", "true"),
      ok ("random-number-generator(42)?next()?next()?number eq random-number-generator(42)?next()?next()?number", "true"),
      ok ("deep-equal(random-number-generator('x')?permute(1 to 50), random-number-generator('x')?permute(1 to 50))", "true"),
      // next() returns another generator.
      ok ("random-number-generator(5)?next() instance of map(xs:string, item())", "true"),
      ok ("let $n := random-number-generator(5)?next()?number return $n ge 0 and $n lt 1", "true"),
      // permute returns a permutation of its argument (same items, any order).
      ok ("let $p := random-number-generator(3)?permute(1 to 100) return count($p) eq 100 and deep-equal(sort($p), 1 to 100)", "true"),
      ok ("let $p := random-number-generator(3)?next()?permute(('a', 'b', 'c', 'd')) return deep-equal(sort($p), ('a', 'b', 'c', 'd'))", "true"),
      ok ("empty(random-number-generator(3)?permute(()))", "true"),
      ok ("random-number-generator(3)?permute('only')", "only"),
      // Spec example: a random permutation of 1 to 100.
      ok ("count(distinct-values(fn:random-number-generator()?permute(1 to 100)))", "100")
    );
  }
}
