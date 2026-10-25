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

import com.helger.xpath.spec.SpecTestHelper.Env;

/**
 * Specification tests of sequence type matching, subtyping and function coercion, ported from go-xslt's <code>seqtype_subtype_test.go</code>.
 *
 * @author Philip Helger
 */
public final class SeqtypeSubtypeSpecTest
{


  /**
   * Checks `instance of` against TypedFunctionTests,
   * which XPath 3.1 §2.5.5.7 defines by the subtype judgement of §2.5.6.2:
   * function(Pa…) as Ra is a subtype of function(Pb…) as Rb iff the arities
   * agree, every Pb is a subtype of the corresponding Pa (contravariance) and
   * Ra is a subtype of Rb (covariance). The SequenceType judgement (§2.5.6.1)
   * compares cardinality ranges as well as item types.
   */
  @Test
  public void tyFunctionSubtyping ()
  {
    run (env (),
    // Plain atomic parameters and results.
         ok ("function($x as xs:integer) as xs:integer {$x} instance of function(xs:integer) as xs:integer", "true"),
         ok ("function($x as xs:decimal) as xs:integer {1} instance of function(xs:integer) as xs:decimal", "true"),
         ok ("function($x as xs:integer) as xs:integer {$x} instance of function(xs:decimal) as xs:integer", "false"),
         ok ("function($x as xs:integer) as xs:decimal {$x} instance of function(xs:integer) as xs:integer", "false"),
         ok ("function($x as xs:integer) as xs:integer {$x} instance of function(xs:integer, xs:integer) as xs:integer", "false"),
         ok ("function($x as item()) as xs:integer {1} instance of function(xs:integer) as item()*", "true"),
         ok ("function($x as xs:integer) as xs:integer {1} instance of function(node()) as item()*", "false"),
         ok ("function($x as xs:integer) as xs:integer {1} instance of function(*)", "true"),
    // Cardinality ranges (§2.5.6.1).
         ok ("function($x as xs:integer?) as xs:integer+ {1} instance of function(xs:integer) as xs:integer*", "true"),
         ok ("function($x as xs:integer) as xs:integer* {1} instance of function(xs:integer) as xs:integer+", "false"),
         ok ("function($x as xs:integer) as xs:integer? {1} instance of function(xs:integer?) as xs:integer?", "false"),
         ok ("function($x as xs:integer+) as xs:integer {1} instance of function(xs:integer*) as xs:integer", "false"),
         ok ("function() as empty-sequence() {()} instance of function() as xs:integer?", "true"),
         ok ("function() as empty-sequence() {()} instance of function() as xs:integer*", "true"),
         ok ("function() as empty-sequence() {()} instance of function() as xs:integer", "false"),
         ok ("function() as empty-sequence() {()} instance of function() as empty-sequence()", "true"),
         ok ("function() as xs:integer {1} instance of function() as empty-sequence()", "false"),
    // xs:error has an empty value space: a subtype of every atomic type
    // (§2.5.6.2), while nothing is a subtype of xs:error but itself.
         ok ("function() as xs:error? {()} instance of function() as xs:string?", "true"),
         ok ("function() as xs:string? {()} instance of function() as xs:error?", "false"),
    // xs:numeric is the union of xs:double, xs:float and xs:decimal.
         ok ("function() as xs:numeric {1} instance of function() as xs:anyAtomicType", "true"),
         ok ("function() as xs:numeric {1} instance of function() as xs:numeric", "true"),
         ok ("function() as xs:numeric {1} instance of function() as xs:decimal", "false"),
         ok ("function() as xs:integer {1} instance of function() as xs:numeric", "true"),
    // Node kind tests as parameter types.
         ok ("function($e as element()) {1} instance of function(element(a)) as item()*", "true"),
         ok ("function($e as element(a)) {1} instance of function(element()) as item()*", "false"),
         ok ("function($e as element(a)) {1} instance of function(element(a)) as item()*", "true"),
         ok ("function($e as element(a)) {1} instance of function(element(b)) as item()*", "false"),
         ok ("function($e as element(*)) {1} instance of function(element(b)) as item()*", "true"),
         ok ("function($n as node()) {1} instance of function(text()) as item()*", "true"),
         ok ("function($n as text()) {1} instance of function(node()) as item()*", "false"),
         ok ("function($n as text()) {1} instance of function(comment()) as item()*", "false"),
         ok ("function($n as node()) {1} instance of function(xs:integer) as item()*", "false"),
         ok ("function($d as document-node()) {1} instance of function(document-node(element(a))) as item()*", "true"),
         ok ("function($d as document-node(element(a))) {1} instance of function(document-node()) as item()*", "false"),
         ok ("function($d as document-node(element(a))) {1} instance of function(document-node(element(a))) as item()*", "true"),
         ok ("function($d as document-node(element(a))) {1} instance of function(document-node(element(b))) as item()*", "false"),
    // element(a) admits nilled elements, element(a, xs:anyType) does not:
    // element(a, xs:anyType) ⊆ element(a), but not the other way round.
         ok ("function($e as element(a)) {1} instance of function(element(a, xs:anyType)) as item()*", "true"),
         ok ("function($e as element(a, xs:anyType)) {1} instance of function(element(a)) as item()*", "false"),
    // Map types (§2.5.6.2: map(K1,V1) ⊆ map(K2,V2) iff K1 ⊆ K2, V1 ⊆ V2;
    // map(*) ≡ map(xs:anyAtomicType, item()*)).
         ok ("function($m as map(*)) {1} instance of function(map(xs:string, xs:integer)) as item()*", "true"),
         ok ("function($m as map(xs:string, xs:integer)) {1} instance of function(map(*)) as item()*", "false"),
         ok ("function($m as map(xs:decimal, xs:integer)) {1} instance of function(map(xs:integer, xs:integer)) as item()*", "true"),
         ok ("function($m as map(xs:integer, xs:integer)) {1} instance of function(map(xs:decimal, xs:integer)) as item()*", "false"),
         ok ("function($m as map(xs:string, xs:decimal)) {1} instance of function(map(xs:string, xs:integer)) as item()*", "true"),
         ok ("function($m as map(xs:anyAtomicType, item()*)) {1} instance of function(map(*)) as item()*", "true"),
         ok ("function($m as map(*)) {1} instance of function(array(*)) as item()*", "false"),
    // Array types (array(*) ≡ array(item()*)).
         ok ("function($a as array(*)) {1} instance of function(array(xs:integer)) as item()*", "true"),
         ok ("function($a as array(xs:integer)) {1} instance of function(array(*)) as item()*", "false"),
         ok ("function($a as array(item()*)) {1} instance of function(array(*)) as item()*", "true"),
         ok ("function($a as array(xs:decimal)) {1} instance of function(array(xs:integer)) as item()*", "true"),
         ok ("function($a as array(xs:integer)) {1} instance of function(array(xs:decimal)) as item()*", "false"),
         ok ("function($a as array(*)) {1} instance of function(map(*)) as item()*", "false"),
    // Maps and arrays are functions: map(*) ⊆ function(xs:anyAtomicType)
    // as item()*, array(*) ⊆ function(xs:integer) as item()*.
         ok ("function($f as function(xs:anyAtomicType) as item()*) {1} instance of function(map(*)) as item()*", "true"),
         ok ("function($f as function(xs:integer) as item()*) {1} instance of function(array(*)) as item()*", "true"),
         ok ("function($f as function(xs:string) as item()*) {1} instance of function(array(*)) as item()*", "false"),
         ok ("function($f as function(xs:anyAtomicType) as xs:integer?) {1} instance of function(map(xs:string, xs:integer)) as item()*", "true"),
         ok ("function($f as function(xs:integer) as xs:integer) {1} instance of function(array(xs:integer)) as item()*", "true"),
         ok ("function($f as function(*)) {1} instance of function(map(*)) as item()*", "true"),
         ok ("function($f as function(xs:anyAtomicType) as item()*) {1} instance of function(xs:integer) as item()*", "false"),
    // Function-typed parameters themselves.
         ok ("function($f as function(*)) {1} instance of function(function(xs:integer) as xs:integer) as item()*", "true"),
         ok ("function($f as function(xs:integer) as xs:integer) {1} instance of function(function(*)) as item()*", "false"),
         ok ("function($f as function(xs:decimal) as xs:integer) {1} instance of function(function(xs:integer) as xs:integer) as item()*", "false"),
         ok ("function($f as function(xs:integer) as xs:integer) {1} instance of function(function(xs:decimal) as xs:integer) as item()*", "true"),
         ok ("function($f as function(xs:integer) as xs:integer) {1} instance of function(function(xs:integer, xs:integer) as xs:integer) as item()*", "false")
    );
  }

  /**
   * Checks `instance of` on maps, arrays, functions and
   * occurrence indicators (XPath 3.1 §2.5.5).
   */
  @Test
  public void tyItemTypeMatching ()
  {
    run (env (),
    // §2.5.5.8 MapTest.
         ok ("map{'a':1} instance of map(xs:string, xs:integer)", "true"),
         ok ("map{1:1} instance of map(xs:string, xs:integer)", "false"),
         ok ("map{'a':'x'} instance of map(xs:string, xs:integer)", "false"),
         ok ("map{'a':(1,2)} instance of map(xs:string, xs:integer+)", "true"),
         ok ("map{} instance of map(xs:string, xs:integer)", "true"),
         ok ("map{'a':1} instance of map(*)", "true"),
         ok ("1 instance of map(*)", "false"),
         ok ("[1] instance of map(*)", "false"),
    // §2.5.5.9 ArrayTest.
         ok ("[1,2] instance of array(xs:integer)", "true"),
         ok ("[1,'a'] instance of array(xs:integer)", "false"),
         ok ("[(1,2)] instance of array(xs:integer)", "false"),
         ok ("[(1,2)] instance of array(xs:integer+)", "true"),
         ok ("[] instance of array(xs:string)", "true"),
         ok ("map{} instance of array(*)", "false"),
         ok ("'a' instance of array(*)", "false"),
    // §2.5.5.7 FunctionTest: maps and arrays are function items.
         ok ("map{1:2} instance of function(*)", "true"),
         ok ("[1] instance of function(*)", "true"),
         ok ("abs#1 instance of function(*)", "true"),
         ok ("1 instance of function(*)", "false"),
         ok ("map{1:2} instance of function(xs:anyAtomicType) as item()*", "true"),
         ok ("map{1:2} instance of function(xs:anyAtomicType, xs:anyAtomicType) as item()*", "false"),
         ok ("[1] instance of function(xs:integer) as item()*", "true"),
         ok ("[1] instance of function(xs:string) as item()*", "false"),
         ok ("'x' instance of function(xs:integer) as item()*", "false"),
         ok ("abs#1 instance of function(item(), item()) as item()*", "false"),
    // Occurrence indicators (§2.5.4).
         ok ("(1,2) instance of xs:integer?", "false"),
         ok ("() instance of xs:integer+", "false"),
         ok ("() instance of xs:integer?", "true"),
         ok ("(1,2) instance of xs:integer", "false"),
         ok ("(1,'a') instance of xs:integer*", "false"),
         ok ("1 instance of empty-sequence()", "false"),
    // ParenthesizedItemType (XPath 3.1 grammar rule [83]).
         ok ("1 instance of (xs:integer)", "true"),
         ok ("(1,2) instance of (xs:integer)+", "true"),
         ok ("map{} instance of (map(*))", "true"),
         ok ("1 instance of item()", "true"),
         ok ("(1, map{}, [], abs#1) instance of item()+", "true"),
    // Atomic type hierarchy (XSD Part 2 §3).
         ok ("xs:byte(1) instance of xs:short", "true"),
         ok ("xs:short(1) instance of xs:byte", "false"),
         ok ("xs:dayTimeDuration('PT1S') instance of xs:duration", "true"),
         ok ("xs:duration('PT1S') instance of xs:dayTimeDuration", "false"),
         ok ("xs:dateTimeStamp('2000-01-01T00:00:00Z') instance of xs:dateTime", "true"),
         ok ("xs:ID('a') instance of xs:NCName", "true"),
         ok ("xs:language('en') instance of xs:token", "true"),
         ok ("xs:unsignedByte(1) instance of xs:unsignedLong", "true"),
         ok ("xs:unsignedByte(1) instance of xs:long", "false"),
         ok ("1.5 instance of xs:numeric", "true"),
         ok ("'1' instance of xs:numeric", "false"),
         ok ("1 instance of xs:error", "false"),
    // treat as raises XPDY0050 on a mismatch (§3.16.5).
         err ("1 treat as xs:string", "XPDY0050"),
         ok ("(1 treat as xs:decimal) + 1", "2"),
    // Unknown types are static errors XPST0051 (§3.14 / §2.5.4); an
    // unprefixed built-in name is only legal under an XSD default type
    // namespace, which the default static context does not have.
         err ("1 instance of xs:doesNotExist", "XPST0051"),
         err ("1 instance of nosuchtype", "XPST0051"),
         err ("1 instance of integer", "XPST0051"),
         err ("map{} instance of map(integer, xs:string)", "XPST0051"),
         err ("map{} instance of map(xs:nope, xs:string)", "XPST0051"),
         err ("map{} instance of map(nope, xs:string)", "XPST0051"),
         ok ("1 instance of Q{http://www.w3.org/2001/XMLSchema}integer", "true"),
         err ("1 instance of Q{http://www.w3.org/2001/XMLSchema}nope", "XPST0051")
    );
  }

  /**
   * Checks the static context's role in type
   * name resolution: an unprefixed type name denotes a built-in only when the
   * default element/type namespace is the XSD namespace (XPath 3.1 §2.1.1),
   * and a prefix without an in-scope binding is XPST0081.
   */
  @Test
  public void tyBareAndPrefixedTypeNames ()
  {
    final Env aXsDefault = env ().staticSetup (x -> x.setDefaultElementNamespace ("http://www.w3.org/2001/XMLSchema"));
    final Env aOnlyMy = env ().ns ("my", "urn:my");
    // go-xslt also binds the empty prefix to the XSD namespace in its resolver (nsDefault); that is the
    // default element/type namespace here, the same as xsDefault.
    run (aXsDefault, ok ("1 instance of integer", "true"));
    run (aXsDefault, ok ("1 instance of integer", "true"));
    run (aXsDefault, ok ("map{1:'a'} instance of map(integer, string)", "true"));
    run (aXsDefault, ok ("function($x as integer) as string {'a'} instance of function(integer) as string", "true"));
    run (aXsDefault, ok ("1 cast as string", "1"));
    run (aOnlyMy, err ("1 instance of integer", "XPST0051"));
    run (aOnlyMy, err ("[1] instance of array(integer)", "XPST0051"));
    run (aOnlyMy, err ("1 instance of foo:bar", "XPST0081"));
    run (aOnlyMy, err ("1 cast as foo:integer", "XPST0081"));
    run (aOnlyMy, err ("function($x as foo:integer) {1}", "XPST0081"));
    run (aOnlyMy, err ("1 instance of function(foo:integer) as item()*", "XPST0081"));
    run (aOnlyMy, err ("1 instance of map(xs:string, foo:integer)", "XPST0081"));
    run (aOnlyMy, err ("1 instance of array(foo:integer)", "XPST0081"));
    run (aOnlyMy, ok ("1 instance of xs:integer", "true"));
  }

  /**
   * Covers the function conversion rules (XPath 3.1
   * §3.1.5.2) applied to arguments of inline functions with declared parameter
   * types: atomization, xs:untypedAtomic casting, numeric promotion, URI
   * promotion and function coercion (§3.1.5.3).
   */
  @Test
  public void tyFunctionCoercion ()
  {
    run (env (),
    // Atomization + casting of xs:untypedAtomic.
         ok ("function($x as xs:integer) {$x + 1}(xs:untypedAtomic('4'))", "5"),
         ok ("function($x as xs:integer*) {sum($x)}([1, 2])", "3"),
         ok ("function($x as xs:anyAtomicType) {$x instance of xs:untypedAtomic}(xs:untypedAtomic('a'))", "true"),
         ok ("function($x as xs:string) {$x}(xs:untypedAtomic('a')) instance of xs:string", "true"),
    // Numeric promotion: decimal → float/double, float → double; never
    // double → float.
         ok ("function($x as xs:double) {$x instance of xs:double}(1)", "true"),
         ok ("function($x as xs:double) {$x instance of xs:double}(xs:float(1))", "true"),
         ok ("function($x as xs:float) {$x instance of xs:float}(1.5)", "true"),
         err ("function($x as xs:float) {$x}(1e0)", "XPTY0004"),
         err ("function($x as xs:decimal) {$x}(1e0)", "XPTY0004"),
    // URI promotion: xs:anyURI → xs:string.
         ok ("function($x as xs:string) {$x instance of xs:string}(xs:anyURI('a'))", "true"),
    // No other conversion: a string is not an integer.
         err ("function($x as xs:integer) {$x}('1')", "XPTY0004"),
         err ("function($x as xs:integer) {$x}((1,2))", "XPTY0004"),
         err ("function($x as xs:integer) {$x}(())", "XPTY0004"),
         // go-xslt accepts any error here; atomizing a map is FOTY0013 (XPath 3.1 §2.4.2)
         err ("function($x as xs:integer) {$x}(map{})", "FOTY0013"),
    // Function coercion (§3.1.5.3): any function item of the right
    // arity is accepted; maps and arrays are arity-one functions.
         ok ("function($g as function(xs:string) as xs:integer) {$g('abc')}(string-length#1)", "3"),
         ok ("function($g as function(xs:anyAtomicType) as item()*) {$g('a')}(map{'a': 5})", "5"),
         ok ("function($g as function(xs:integer) as item()*) {$g(2)}([7, 8])", "8"),
         err ("function($g as function(xs:integer, xs:integer) as item()*) {1}(abs#1)", "XPTY0004"),
         err ("function($g as function(*)) {1}((abs#1, abs#1))", "XPTY0004"),
         err ("function($g as function(*)) {1}(())", "XPTY0004"),
         err ("function($g as function(*)?) {1}((abs#1, abs#1))", "XPTY0004"),
         err ("function($g as function(*)+) {1}(())", "XPTY0004"),
         err ("function($g as function(*)) {1}(1)", "XPTY0004"),
         ok ("count(function($g as function(*)*) {$g}((abs#1, map{}, [])))", "3"),
    // A supplied function that is a subtype is passed through unchanged.
         ok ("function($g as function(xs:integer) as xs:integer) {$g(2)}(function($x as xs:decimal) as xs:integer {xs:integer($x * 2)})", "4"),
    // …otherwise it is wrapped: arguments are converted to the expected
    // parameter types and the result to the expected return type.
         ok ("function($g as function(xs:integer) as xs:double) {$g(2)}(function($x as xs:decimal) as xs:float {$x * 1.5})", "3"),
         ok ("function($g as function(xs:integer) as xs:double) {$g(2) instance of xs:double}(function($x as xs:decimal) as xs:float {$x * 1.5})", "true"),
    // higher-order-functions-066: an xs:float-typed function bound to
    // function(xs:double) as xs:double fails when called with a double.
         err ("function($g as function(xs:double) as xs:double) {$g(2.5e0)}(function($x as xs:float) as xs:float {$x})", "XPTY0004"),
         err ("function($g as function(xs:string) as xs:string) {$g('ab')}(string-length#1)", "XPTY0004")
    );
  }
}
