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

import static com.helger.xpath.spec.ParserSyntaxSpecTest.PE_DOC;
import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.xpath.spec.SpecTestHelper.Env;

/**
 * Specification tests of expression evaluation, ported from go-xslt's <code>eval_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class EvalSpecTest
{


  /**
   * TestPeRangeAndPredicates: §3.5 range expressions and §3.3.3 numeric
   * predicates.
   */
  @Test
  public void peRangeAndPredicates ()
  {
    run (env (PE_DOC),
    // Each operand is converted to xs:integer? by the function
    // conversion rules: a sequence of two, a string or a decimal
    // cannot be.
         err ("(1, 2) to 3", "XPTY0004"),
         err ("1 to (2, 3)", "XPTY0004"),
         err ("'a' to 3", "XPTY0004"),
         err ("1.5 to 3", "XPTY0004"),
         ok ("string-join(xs:untypedAtomic('2') to 3, ',')", "2,3"),
         ok ("count(3 to 1)", "0"),
         ok ("count(() to 3)", "0"),
         ok ("count(1 to ())", "0"),
         err ("1 to error()", "FOER0000"),
         err ("error() to 1", "FOER0000"),
    // A numeric predicate selects the item whose position equals it; a
    // non-integral or non-finite value equals no position.
         ok ("count((1, 2, 3)[1.5])", "0"),
         ok ("count((1, 2, 3)[xs:double('NaN')])", "0"),
         ok ("count((1, 2, 3)[xs:double('INF')])", "0"),
         ok ("(5, 6, 7)[2]", "6"),
         ok ("(5, 6, 7)[2.0]", "6"),
         ok ("(5, 6, 7)[xs:double(3)]", "7"),
         ok ("string-join((1, 2, 3)[. > 1], ',')", "2,3"),
         ok ("string((/r/a[1], 'x')[1])", "one"),
         ok ("count((/r/a, 1)[. instance of element()])", "2"),
         err ("(1, 2)[error()]", "FOER0000"),
         ok ("string-join((1 to 5)[position() = (2, 4)], ',')", "2,4")
    );
  }

  /**
   * TestPePaths: §3.3 path expressions over a mix of nodes and atomic values.
   */
  @Test
  public void pePaths ()
  {
    run (env (PE_DOC),
         ok ("string-join(/r/a/string(), ',')", "one,two"),
         ok ("string-join(/r/a/name(), ',')", "a,a"),
    // E1/E2 evaluates E2 once per node of E1; atomic results are not
    // de-duplicated or reordered.
         ok ("count(/r/a/(1, 2))", "4"),
         ok ("count(/r/a/..)", "1"),
    // §3.3.1: a last step mixing nodes and atomics is XPTY0018.
         err ("/r/(a, 1)", "XPTY0018"),
    // §3.3.2: an axis step needs a node context item (XPTY0020).
         err ("1 ! a", "XPTY0020"),
         err ("'a' ! @x", "XPTY0020"),
         err ("(1, 2) ! child::node()", "XPTY0020"),
         err ("1 ! /", "XPTY0020"),
    // §3.1.2 / §2.1.1: the xmlns prefix is never bound, and the xmlns
    // namespace may not appear in a URIQualifiedName.
         err ("/r/xmlns:a", "XPST0081"),
         err ("/r/Q{http://www.w3.org/2000/xmlns/}a", "XQST0070"),
    // Unions, intersections and differences need node operands (§3.4.2).
         err ("(1, 2) intersect (1)", "XPTY0004"),
         err ("/r except 1", "XPTY0004"),
         err ("1 | 2", "XPTY0004"),
         ok ("count(() except /r)", "0"),
         ok ("count(/r/a intersect /r/a[1])", "1"),
         ok ("count(/r/* except /r/a)", "1"),
         ok ("count(/r/a | ())", "2"),
         err ("/r/a intersect error()", "FOER0000"),
         err ("error() except /r", "FOER0000"),
         err ("error() | /r", "FOER0000"),
         ok ("string-join(/r/a ! name(), ',')", "a,a"),
         ok ("string-join((1, 2) ! (. * 10), ',')", "10,20"),
         err ("/r/a[error()]", "FOER0000"),
         err ("/r/error()", "FOER0000")
    );
  }

  /**
   * TestPeFocusAbsent: §2.1.2 — with no context item, '.', a relative path and
   * '/' are XPDY0002.
   */
  @Test
  public void peFocusAbsent ()
  {
    run (env ().noContext (),
         err (".", "XPDY0002"),
         err ("a", "XPDY0002"),
         err ("/", "XPDY0002"),
         err ("/r", "XPDY0002"),
         err ("@x", "XPDY0002"),
         err ("child::a", "XPDY0002"));
  }

  /**
   * TestPeStaticPrefixCheck: an unbound variable-name prefix is XPST0081
   * even on a branch that is never evaluated (§2.4.1: static errors are
   * detected regardless of evaluation order).
   */
  @Test
  public void peStaticPrefixCheck ()
  {
//GO 	for _, ns := range []NamespaceResolver{nil, peNS{"p": "urn:p"}} {
//GO 		for _, expr := range []string{
//GO 			"$q:x",
//GO 			"if (true()) then 1 else $q:x",
//GO 		} {
//GO 			_, err := peRun(expr, peDoc, ns)
//GO 			if err == nil || !strings.Contains(err.Error(), "XPST0081") {
//GO 				t.Errorf("%s (ns %v): %v, want XPST0081", expr, ns, err)
//GO 			}
//GO 		}
//GO 	}
    // A bound prefix and a braced EQName are fine.
    run (env (PE_DOC).ns ("p", "urn:p"),
         ok ("let $p:x := 5 return $p:x", "5"),
         ok ("let $Q{urn:z}x := 6 return $Q{urn:z}x", "6")
    );
  }

  /**
   * Covers ||, treat, map/array constructors,
   * dynamic calls and lookups.
   */
  @Test
  public void peOperatorsAndConstructors ()
  {
    run (env (PE_DOC),
    // §3.7.1: each || operand is atomized to xs:string? — two items is
    // a type error, () is "".
         err ("(1, 2) || 'a'", "XPTY0004"),
         ok ("() || 'a' || ()", "a"),
         err ("'a' || error()", "FOER0000"),
         err ("error() || 'a'", "FOER0000"),
    // §3.14.5 treat.
         err ("1 treat as xs:string", "XPDY0050"),
         ok ("(1 treat as xs:integer) + 1", "2"),
         ok ("count(() treat as empty-sequence())", "0"),
         err ("error() treat as item()", "FOER0000"),
         err ("error() instance of item()", "FOER0000"),
    // §3.11.1.1 map constructor: a key must be one atomic value, and two
    // keys that are the same key (op:same-key) are XQDY0137.
         err ("map{(1, 2): 1}", "XPTY0004"),
         err ("map{(): 1}", "XPTY0004"),
         err ("map{1: 1, 1: 2}", "XQDY0137"),
         err ("map{1: 1, 1.0: 2}", "XQDY0137"),
         ok ("map{'1': 1, 1: 2}(1)", "2"),
         err ("map{error(): 1}", "FOER0000"),
         err ("map{1: error()}", "FOER0000"),
         err ("[error()]", "FOER0000"),
         err ("array{error()}", "FOER0000"),
    // §3.1.5.1 dynamic function calls.
         err ("(upper-case#1, lower-case#1)('a')", "XPTY0004"),
         err ("()('a')", "XPTY0004"),
         err ("upper-case#1('a', 'b')", "XPTY0004"),
         err ("1(2)", "XPTY0004"),
         err ("[1, 2](3)", "FOAY0001"),
         err ("[1, 2](0)", "FOAY0001"),
         err ("[1, 2](1, 2)", "XPTY0004"),
         ok ("[1, 2](2)", "2"),
         ok ("map{1: 2}(1)", "2"),
         ok ("count(map{1: 2}(3))", "0"),
         err ("map{1: 2}((1, 2))", "XPTY0004"),
         err ("map{1: 2}(1, 2)", "XPTY0004"),
         err ("error#0()", "FOER0000"),
         err ("upper-case#1(error())", "FOER0000"),
    // Partial application (§3.1.5.1) of a dynamic call.
         ok ("substring#2(?, 2)('abcd')", "bcd"),
         ok ("substring#3(?, 2, ?)('abcd', 1)", "b"),
         ok ("upper-case#1(?)('x')", "X"),
         ok ("function-arity(substring#3(?, 2, ?))", "2"),
         ok ("[10, 20](?)(2)", "20"),
         ok ("map{1: 'a'}(?)(1)", "a"),
         err ("concat#2(?, error())", "FOER0000"),
    // Function literals naming a function that does not exist at that
    // arity are XPST0017 (§3.1.6).
         err ("upper-case#3", "XPST0017"),
         err ("fn:no-such-function#0", "XPST0017"),
         ok ("string(math:pi#0())", "3.141592653589793"),
    // §3.11.3 lookup errors.
         err ("[1]?(xs:double(1))", "XPTY0004"),
         err ("[1, 2]?3", "FOAY0001"),
         err ("[1]?0", "FOAY0001"),
         err ("1?a", "XPTY0004"),
         err ("(/r)?a", "XPTY0004"),
         ok ("count(map{'a': 1}?b)", "0"),
         ok ("sum((map{'a': 1}, map{'a': 2})?a)", "3"),
         err ("[1]?(error())", "FOER0000"),
         err ("error()?a", "FOER0000")
    );
  }

  /**
   * TestPeBindingsAndFunctions: for/some/every with several clauses, inline
   * function signature checks (§3.1.7, function conversion rules §3.1.5.2).
   */
  @Test
  public void peBindingsAndFunctions ()
  {
    run (env (PE_DOC),
         ok ("string-join(for $x in (1, 2), $y in ($x, 10) return string($y), ',')", "1,10,2,10"),
         err ("for $x in (1, error()) return $x", "FOER0000"),
         err ("for $x in 1 return error()", "FOER0000"),
         err ("for $x in 1, $y in error() return 1", "FOER0000"),
         ok ("some $x in (1, 2), $y in (2, 3) satisfies $x = $y", "true"),
         ok ("every $x in (1, 2), $y in (3, 4) satisfies $x < $y", "true"),
         ok ("every $x in (1, 2), $y in (2, 4) satisfies $x < $y", "false"),
         ok ("some $x in () satisfies error()", "false"),
         ok ("every $x in () satisfies error()", "true"),
         err ("some $x in error() satisfies 1", "FOER0000"),
         err ("some $x in 1, $y in error() satisfies 1", "FOER0000"),
         err ("every $x in (1, 2) satisfies error()", "FOER0000"),
         err ("let $x := error() return 1 + $x", "FOER0000"),
         err ("if (error()) then 1 else 2", "FOER0000"),
         err ("-error()", "FOER0000"),
         err ("error() * 2", "FOER0000"),
         err ("2 * error()", "FOER0000"),
         err ("error() = 1", "FOER0000"),
         err ("1 eq error()", "FOER0000"),
         err ("(1, error())", "FOER0000"),
         // go-xslt passes 'a' => error(); fn:error takes an xs:QName?, and an xs:string is not promoted to
         // one (function coercion, XPath 3.1 §3.1.5.2), so that is XPTY0004.
         err ("'a' => error()", "XPTY0004"),
         err ("() => error()", "FOER0000"),
         err ("error() => string()", "FOER0000"),
    // Arguments not matching a declared parameter type, or a result not
    // matching the declared return type, are XPTY0004.
         err ("function($a as xs:integer) {$a}('x')", "XPTY0004"),
         err ("function($a as xs:integer) {$a}(1.5)", "XPTY0004"),
         err ("function($a as xs:integer) {$a}((1, 2))", "XPTY0004"),
         err ("function() as xs:integer {'x'}()", "XPTY0004"),
         err ("function() as xs:integer {(1, 2)}()", "XPTY0004"),
         err ("function() as xs:integer {error()}()", "FOER0000"),
    // untypedAtomic arguments are cast to the declared atomic type.
         ok ("function($a as xs:integer) {$a + 1}(xs:untypedAtomic('4'))", "5"),
    // Closures capture their defining variables.
         ok ("let $k := 10, $f := function($a) {$a + $k} return $f(1)", "11")
    );
  }

  /**
   * TestPeCast: §3.14.2 cast / castable.
   */
  @Test
  public void peCast ()
  {
    run (env (PE_DOC).ns ("p", "urn:p"),
    // The operand is atomized; () needs the '?' occurrence indicator,
    // more than one item is a type error (castable: false).
         err ("() cast as xs:integer", "XPTY0004"),
         ok ("count(() cast as xs:integer?)", "0"),
         err ("(1, 2) cast as xs:integer", "XPTY0004"),
         ok ("() castable as xs:integer", "false"),
         ok ("() castable as xs:integer?", "true"),
         ok ("(1, 2) castable as xs:integer", "false"),
         ok ("(1, 2) castable as xs:integer?", "false"),
         ok ("'x' castable as xs:integer", "false"),
         err ("error() cast as xs:integer", "FOER0000"),
         err ("error() castable as xs:integer", "FOER0000"),
    // xs:NOTATION and xs:anyAtomicType are not valid cast targets.
         err ("1 cast as xs:NOTATION", "XPST0080"),
         err ("1 cast as xs:anyAtomicType", "XPST0080"),
         err ("1 castable as xs:NOTATION", "XPST0080"),
    // Casting to a list type tokenizes the string (§3.14.2, XSD list
    // types); xs:NMTOKENS has minLength 1.
         ok ("count('a b c' cast as xs:NMTOKENS)", "3"),
         ok ("'a b' castable as xs:NMTOKENS", "true"),
         ok ("'' castable as xs:NMTOKENS", "false"),
         ok ("() castable as xs:NMTOKENS", "false"),
         ok ("(1, 2) castable as xs:NMTOKENS", "false"),
    // F&O 3.1 §19.2: string/untypedAtomic -> xs:QName resolves the
    // prefix against the statically known namespaces.
         ok ("namespace-uri-from-QName('xs:integer' cast as xs:QName)", "http://www.w3.org/2001/XMLSchema"),
         ok ("namespace-uri-from-QName(xs:untypedAtomic('p:a') cast as xs:QName)", "urn:p"),
         ok ("local-name-from-QName(xs:untypedAtomic(' p:a ') cast as xs:QName)", "a"),
         ok ("namespace-uri-from-QName(xs:untypedAtomic('xs:a') cast as xs:QName)", "http://www.w3.org/2001/XMLSchema"),
         err ("xs:untypedAtomic('q:a') cast as xs:QName", "FONS0004"),
         err ("xs:untypedAtomic('1a') cast as xs:QName", "FORG0001"),
         err ("xs:untypedAtomic('a:b:c') cast as xs:QName", "FORG0001")
    );
    // Without any namespace resolver, an unknown prefix cannot be bound.
    run (env (PE_DOC),
         err ("xs:untypedAtomic('q:a') cast as xs:QName", "FONS0004")
    );
    // An unprefixed name takes the default element/type namespace.
    run (env ("<r/>").staticSetup (x -> x.setDefaultElementNamespace ("urn:d")),
         ok ("namespace-uri-from-QName(xs:untypedAtomic('a') cast as xs:QName)", "urn:d"));
  }

  /**
   * TestPeMathExpEdges: math:exp's special values (F&amp;O 3.1 §4.8.4): NaN stays
   * NaN, +INF gives +INF, -INF gives 0; a result beyond the double range
   * overflows to INF or underflows to 0.
   */
  @Test
  public void peMathExpEdges ()
  {
    run (env ("<r/>"),
         ok ("string(math:exp(xs:double('NaN')))", "NaN"),
         ok ("string(math:exp(xs:double('INF')))", "INF"),
         ok ("string(math:exp(xs:double('-INF')))", "0"),
         ok ("string(math:exp(1000))", "INF"),
         ok ("string(math:exp(-1000))", "0"),
         ok ("math:exp(0) = 1", "true"),
    // For |x| < 2^-28 the correctly rounded result is 1 + x.
         ok ("math:exp(1e-10) = 1 + 1e-10", "true"),
         ok ("math:exp(-1e-10) = 1 - 1e-10", "true"),
    // exp(-1) * exp(1) = 1 within a couple of ulps.
         ok ("abs(math:exp(-1) * math:exp(1) - 1) < 1e-15", "true"),
         ok ("abs(math:exp(-2.5) - 0.0820849986238988) < 1e-15", "true")
    );
  }

  /**
   * TestPeTypeNamesAndArity: an unprefixed atomic type name is not in the XSD
   * namespace when the default type namespace is none, so it names no known
   * type (XPST0051, §2.5.4); a partial application must supply exactly the
   * function's arity (XPTY0004, §3.1.5.1).
   */
  @Test
  public void peTypeNamesAndArity ()
  {
    run (env (PE_DOC),
         err ("1 instance of integer", "XPST0051"),
         err ("1 treat as integer", "XPST0051"),
         err ("function($a as integer) {$a}", "XPST0051"),
         err ("function() as integer {1}", "XPST0051"),
         err ("upper-case#1(?, ?)", "XPTY0004"),
         err ("substring#2(?)", "XPTY0004")
    );
    // With XSD as the default namespace (an NS resolver binding the empty
    // prefix to it) the same names resolve.
    run (env (PE_DOC).staticSetup (x -> x.setDefaultElementNamespace ("http://www.w3.org/2001/XMLSchema")),
         ok ("1 instance of integer", "true")
    );
  }

  /**
   * TestPeInterrupt: a host's Interrupt hook stops a long evaluation with the
   * hook's error.
   */
  @Test
  public void peInterrupt ()
  {
    // go-xslt checks for its own hook error; the interrupt here is FOER0000.
    final Env aEnv = env (PE_DOC).dynamicSetup (x -> {
      x.setInterrupt ( () -> true);
      return x;
    });
    run (aEnv,
         err ("for $x in 1 to 100 return $x", "FOER0000"),
         err ("some $x in 1 to 100 satisfies $x = 0", "FOER0000"),
         err ("(1 to 100)[. = 0]", "FOER0000"),
         err ("(1 to 100) ! (. + 1)", "FOER0000"),
         err ("let $f := function($n) {$n} return $f(1)", "FOER0000"),
         err ("/r/a[. = 'x']", "FOER0000"));
  }
}
