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
 * Port of go-xslt <code>internal/xpath/fn_hof_spec_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnHofSpecTest
{
  /**
   * Covers fn:function-name, fn:function-arity,
   * fn:function-lookup and fn:apply (F&O 3.1 §16.1/§16.2) with the REC's
   * examples, function items from every built-in namespace, and the
   * documented error codes.
   */
  @Test
  public void hofSpec ()
  {
    coreRun (
      // §16.1.2 fn:function-name
      ok ("local-name-from-QName(function-name(fn:substring#2))", "substring"),
      ok ("namespace-uri-from-QName(function-name(fn:substring#2))", "http://www.w3.org/2005/xpath-functions"),
      ok ("namespace-uri-from-QName(function-name(math:pi#0))", "http://www.w3.org/2005/xpath-functions/math"),
      ok ("namespace-uri-from-QName(function-name(map:get#2))", "http://www.w3.org/2005/xpath-functions/map"),
      ok ("namespace-uri-from-QName(function-name(array:size#1))", "http://www.w3.org/2005/xpath-functions/array"),
      ok ("namespace-uri-from-QName(function-name(xs:integer#1))", "http://www.w3.org/2001/XMLSchema"),
      ok ("local-name-from-QName(function-name(xs:integer#1))", "integer"),
      ok ("function-name(function($node){count($node/*)})", "()"),
      ok ("function-name(substring(?, 1, 1))", "()"),
      ok ("function-name(map{})", "()"),
      err ("function-name((abs#1, abs#1))", "XPTY0004"),
      err ("function-name(1)", "XPTY0004"),

      // §16.1.3 fn:function-arity
      ok ("function-arity(fn:substring#2)", "2"),
      ok ("function-arity(function($node){name($node)})", "1"),
      ok ("let $initial := fn:substring(?, 1, 1) return function-arity($initial)", "1"),
      ok ("function-arity(map{})", "1"),
      ok ("function-arity([1, 2])", "1"),
      err ("function-arity(())", "XPTY0004"),
      err ("function-arity('abs')", "XPTY0004"),

      // §16.1.1 fn:function-lookup
      ok ("function-lookup(xs:QName('fn:substring'), 2)('abcd', 2)", "bcd"),
      ok ("function-lookup(xs:QName('fn:abs'), 1)(-3)", "3"),
      ok ("function-lookup(QName('http://www.w3.org/2005/xpath-functions/math', 'sqrt'), 1)(4)", "2"),
      ok ("function-lookup(QName('http://www.w3.org/2005/xpath-functions/map', 'size'), 1)(map{1:2})", "1"),
      ok ("function-lookup(QName('http://www.w3.org/2005/xpath-functions/array', 'size'), 1)([1, 2, 3])", "3"),
      ok ("function-lookup(QName('http://www.w3.org/2001/XMLSchema', 'integer'), 1)('12') + 1", "13"),
      ok ("function-arity(function-lookup(xs:QName('fn:concat'), 4))", "4"),
      // no such function, wrong arity, no namespace: the empty sequence
      ok ("function-lookup(xs:QName('fn:abs'), 3)", "()"),
      ok ("function-lookup(QName('', 'abs'), 1)", "()"),
      ok ("function-lookup(QName('http://example.com/none', 'f'), 0)", "()"),
      ok ("function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'no-such-function'), 1)", "()"),
      ok ("function-lookup(QName('http://www.w3.org/2005/xpath-functions/math', 'no-such'), 1)", "()"),
      // $name as xs:QName and $arity as xs:integer are required singletons
      err ("function-lookup((), 1)", "XPTY0004"),
      err ("function-lookup('fn:abs', 1)", "XPTY0004"),
      err ("function-lookup(xs:QName('fn:abs'), ())", "XPTY0004"),
      err ("function-lookup((xs:QName('fn:abs'), xs:QName('fn:abs')), 1)", "XPTY0004"),

      // §16.2.1 fn:apply
      ok ("apply(fn:concat#3, [\"a\", \"b\", \"c\"])", "abc"),
      ok ("apply(function($a, $b){$a * $b}, [6, 7])", "42"),
      ok ("apply(map{'k': 'v'}, ['k'])", "v"),
      err ("apply(abs#1, [1, 2])", "FOAP0001"),
      err ("apply(abs#1, [])", "FOAP0001"),
      err ("apply(abs#1, 1)", "XPTY0004"),
      err ("apply(1, [1])", "XPTY0004")
    );
  }
}
