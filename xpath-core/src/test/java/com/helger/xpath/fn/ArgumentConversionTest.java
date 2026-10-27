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
package com.helger.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;

/**
 * Test class for the function conversion rules (XPath 3.1 section 3.1.5.2) applied to the arguments of every
 * built-in function: atomize, cast xs:untypedAtomic, promote numerics and xs:anyURI, then SequenceType matching.
 * XPTY0004 on a mismatch of type or cardinality, XPTY0117 for untypedAtomic to xs:QName.
 *
 * @author Philip Helger
 */
public final class ArgumentConversionTest
{
  private static final String SRC = "<r><a>3</a><b>x</b><n>2.5</n><s>abc</s><!--c--></r>";

  private static void _run (final boolean bCompat, final String [] [] aCases)
  {
    final XdmNode aDoc = FnNodeTestHelper.parse (SRC);
    final ICommonsList <String> aFailures = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final String sExpr = aCase[0];
      final boolean bWantError = "code".equals (aCase[1]);
      try
      {
        final StaticContext aStatic = new StaticContext ();
        aStatic.setXPath10Compatibility (bCompat);
        final CompiledXPath aXP = XPathCompiler.compile (sExpr, aStatic);
        final DynamicContext aCtx = aXP.createDynamicContext ().withFocus (aDoc, 1, 1);
        final String sGot = FnTestHelper.lex (aXP.evaluate (aCtx));
        if (bWantError)
          aFailures.add (sExpr + ": got '" + sGot + "', want error " + aCase[2]);
        else
          if (!aCase[2].equals (sGot))
            aFailures.add (sExpr + ": got '" + sGot + "', want '" + aCase[2] + "'");
      }
      catch (final XPathException ex)
      {
        if (!bWantError)
          aFailures.add (sExpr + ": unexpected error " + ex.getCode () + " " + ex.getMessage ());
        else
          if (!aCase[2].equals (ex.getCode ()))
            aFailures.add (sExpr + ": error " + ex.getCode () + ", want " + aCase[2]);
      }
    }
    if (!aFailures.isEmpty ())
      fail (aFailures.size () + " of " + aCases.length + " cases failed:\n" + String.join ("\n", aFailures));
  }

  private static void _run (final String [] [] aCases)
  {
    _run (false, aCases);
  }

  /** A required (no occurrence indicator) parameter takes exactly one item, an optional one the empty sequence. */
  @Test
  public void testEmptyForRequired ()
  {
    _run (false, new String [] [] {
      { "contains-token(\"red green blue\", \"RED\", ())", "code", "XPTY0004" },
      { "empty(math:atan2((), 1))", "code", "XPTY0004" },
      { "error(QName('urn:e','E1'), (), 'v')", "code", "XPTY0004" },
      { "error('a')", "code", "XPTY0004" },
      { "math:atan2(1, ())", "code", "XPTY0004" },
      { "math:pow(2, ())", "code", "XPTY0004" },
      { "substring(\"abc\", ())", "code", "XPTY0004" },
      { "substring(\"abc\", 1, ())", "code", "XPTY0004" },
      { "translate(\"abc\", \"a\", ())", "code", "XPTY0004" },
      { "compare(\"a\", \"b\", ())", "code", "XPTY0004" },
      { "matches(\"a\", \"a\", ())", "code", "XPTY0004" },
      { "matches(\"a\", ())", "code", "XPTY0004" },
      { "replace(\"a\", \"a\", ())", "code", "XPTY0004" },
      { "tokenize(\"a b\", \" \", ())", "code", "XPTY0004" },
      { "normalize-unicode(\"a\", ())", "code", "XPTY0004" },
      { "string-join((\"a\", \"b\"), ())", "code", "XPTY0004" },
      { "resolve-uri(\"a\", ())", "code", "XPTY0004" },
      { "format-integer(1, ())", "code", "XPTY0004" },
      { "round(1.5, ())", "code", "XPTY0004" },
      { "QName(\"urn:x\", ())", "code", "XPTY0004" },
      { "index-of((1, 2), ())", "code", "XPTY0004" },
      { "function-lookup(QName('urn:x', 'f'), ())", "code", "XPTY0004" },
      { "distinct-values((1, 2), ())", "code", "XPTY0004" },
      { "deep-equal(1, 2, ())", "code", "XPTY0004" },
      { "sort((3, 1), (), ())", "code", "XPTY0004" },
      { "map:get(map{}, ())", "code", "XPTY0004" },
      { "map:contains(map{}, ())", "code", "XPTY0004" },
      { "map:entry((), 1)", "code", "XPTY0004" },
      { "array:get([1], ())", "code", "XPTY0004" },
      { "array:size(())", "code", "XPTY0004" },
      { "map:size(())", "code", "XPTY0004" },
      { "environment-variable(())", "code", "XPTY0004" },
      { "empty(codepoint-equal((), \"a\"))", "want", "true" },
      { "string-length(())", "want", "0" },
      { "upper-case(())", "want", "" },
      { "empty(math:pow((), 3))", "want", "true" },
      { "empty(abs(()))", "want", "true" },
      { "empty(year-from-date(()))", "want", "true" },
      { "local-name-from-QName(QName((), \"a\"))", "want", "a" },
      { "concat(\"a\", (), \"b\")", "want", "ab" },
      { "empty(error(()))", "code", "FOER0000" },
    });
  }

  /** More than one item for a single-item parameter is a type error. */
  @Test
  public void testTooManyItems ()
  {
    _run (false, new String [] [] {
      { "upper-case((\"a\", \"b\"))", "code", "XPTY0004" },
      { "string-length((\"a\", \"b\"))", "code", "XPTY0004" },
      { "string-length(r/*)", "code", "XPTY0004" },
      { "substring(\"abc\", (1, 2))", "code", "XPTY0004" },
      { "abs((1, 2))", "code", "XPTY0004" },
      { "round(1, (1, 2))", "code", "XPTY0004" },
      { "name(r/*)", "code", "XPTY0004" },
      { "concat(1, (2, 3))", "code", "XPTY0004" },
      { "string((1, 2))", "code", "XPTY0004" },
      { "number((1, 2))", "code", "XPTY0004" },
      { "sum((1, 2), (1, 2))", "code", "XPTY0004" },
      { "index-of((1, 2), (1, 2))", "code", "XPTY0004" },
      { "random-number-generator((1, 2))", "code", "XPTY0004" },
      { "generate-id((r, r/a))", "code", "XPTY0004" },
      { "math:atan2(1, (2, 3))", "code", "XPTY0004" },
      { "array:get([1, 2], (1, 2))", "code", "XPTY0004" },
      { "map:get(map{}, (1, 2))", "code", "XPTY0004" },
      { "map:size((map{}, map{}))", "code", "XPTY0004" },
      { "function-arity((abs#1, abs#1))", "code", "XPTY0004" },
      { "error(QName('urn:e', 'E1'), ('a', 'b'))", "code", "XPTY0004" },
      { "string-join((\"a\", \"b\", \"c\"), \"-\")", "want", "a-b-c" },
      { "contains-token((\"a\", \"b c\"), \"c\")", "want", "true" },
    });
  }

  /** Wrong atomic type: XPTY0004; the declared type, a subtype and a promotion are accepted. */
  @Test
  public void testWrongAtomicType ()
  {
    _run (false, new String [] [] {
      { "abs(\"a\")", "code", "XPTY0004" },
      { "abs(true())", "code", "XPTY0004" },
      { "substring(\"abc\", \"2\")", "code", "XPTY0004" },
      { "substring(\"abc\", true())", "code", "XPTY0004" },
      { "upper-case(1)", "code", "XPTY0004" },
      { "contains(true(), \"a\")", "code", "XPTY0004" },
      { "string-length(1.5)", "code", "XPTY0004" },
      { "starts-with(\"a\", 1)", "code", "XPTY0004" },
      { "math:sin(\"a\")", "code", "XPTY0004" },
      { "math:pow(2, \"3\")", "code", "XPTY0004" },
      { "format-number(true(), \"0\")", "code", "XPTY0004" },
      { "format-integer(1.5, \"1\")", "code", "XPTY0004" },
      { "format-integer(5e0, \"1\")", "code", "XPTY0004" },
      { "round(1.255, 2.0)", "code", "XPTY0004" },
      { "codepoints-to-string((72, \"i\"))", "code", "XPTY0004" },
      { "string-to-codepoints(1)", "code", "XPTY0004" },
      { "trace(1, 5)", "code", "XPTY0004" },
      { "doc-available(5)", "code", "XPTY0004" },
      { "unparsed-text-available(5)", "code", "XPTY0004" },
      { "normalize-unicode(\"a\", 1)", "code", "XPTY0004" },
      { "compare(\"a\", 1)", "code", "XPTY0004" },
      { "year-from-date(\"2020-01-01\")", "code", "XPTY0004" },
      { "year-from-date(xs:dateTime(\"2020-01-01T00:00:00\"))", "code", "XPTY0004" },
      { "hours-from-time(xs:dateTime(\"2020-01-01T10:00:00\"))", "code", "XPTY0004" },
      { "adjust-date-to-timezone(xs:date(\"2000-01-01\"), \"PT0S\")", "code", "XPTY0004" },
      { "format-date(xs:dateTime(\"2000-01-01T00:00:00\"), \"[Y]\")", "code", "XPTY0004" },
      { "years-from-duration(\"P1Y\")", "code", "XPTY0004" },
      { "prefix-from-QName(\"a\")", "code", "XPTY0004" },
      { "function-lookup(\"abs\", 1)", "code", "XPTY0004" },
      { "parse-json(5)", "code", "XPTY0004" },
      { "parse-json(\"1\", 5)", "code", "XPTY0004" },
      { "parse-json(\"1\", ())", "code", "XPTY0004" },
      { "array:get([5], 1.0)", "code", "XPTY0004" },
      { "array:get([5], \"1\")", "code", "XPTY0004" },
      { "concat(map{}, 1)", "code", "FOTY0013" },
      { "string-length(map{})", "code", "FOTY0013" },
      { "abs(1)", "want", "1" },
      { "abs(-1.5)", "want", "1.5" },
      { "abs(xs:float(-2))", "want", "2" },
      { "abs(xs:byte(-2))", "want", "2" },
      { "format-integer(xs:byte(5), \"1\")", "want", "5" },
      { "substring(\"abcdef\", 3)", "want", "cdef" },
      { "substring(\"abcdef\", 3.0)", "want", "cdef" },
      { "substring(\"abcdef\", xs:float(3))", "want", "cdef" },
      { "substring(\"abcdef\", 2, 2)", "want", "bc" },
      { "math:sqrt(16)", "want", "4" },
      { "math:pow(2, 3)", "want", "8" },
      { "math:pow(2, 1.5) gt 2.8", "want", "true" },
      { "string-join((1, 2), \",\")", "want", "1,2" },
      { "string-join((1, \"a\", true()), \",\")", "want", "1,a,true" },
      { "concat(1, true(), \"a\")", "want", "1truea" },
      { "hours-from-duration(xs:yearMonthDuration(\"P1Y\"))", "want", "0" },
      { "years-from-duration(xs:dayTimeDuration(\"PT1H\"))", "want", "0" },
      { "year-from-dateTime(xs:dateTime(\"2020-01-01T00:00:00\"))", "want", "2020" },
      { "index-of((1, 2, 3), 2)", "want", "2" },
    });
  }

  /** xs:untypedAtomic is cast to the expected type, except for namespace-sensitive types. */
  @Test
  public void testUntypedAtomic ()
  {
    _run (false, new String [] [] {
      { "abs(xs:untypedAtomic(\"-3\"))", "want", "3" },
      { "abs(xs:untypedAtomic(\"-3\")) instance of xs:double", "want", "true" },
      { "substring(\"abc\", xs:untypedAtomic(\"2\"))", "want", "bc" },
      { "format-integer(xs:untypedAtomic(\"12\"), \"1\")", "want", "12" },
      { "round(xs:untypedAtomic(\"2.5\"))", "want", "3" },
      { "format-number(xs:untypedAtomic(\"3\"), \"0\")", "want", "3" },
      { "array:get([5], xs:untypedAtomic(\"1\"))", "want", "5" },
      { "codepoints-to-string(xs:untypedAtomic(\"72\"))", "want", "H" },
      { "year-from-date(xs:untypedAtomic(\"2020-01-01\"))", "want", "2020" },
      { "upper-case(xs:untypedAtomic(\"ab\"))", "want", "AB" },
      { "substring(\"abc\", xs:untypedAtomic(\"x\"))", "code", "FORG0001" },
      { "format-integer(xs:untypedAtomic(\"1.5\"), \"1\")", "code", "FORG0001" },
      { "year-from-dateTime(xs:untypedAtomic(\"x\"))", "code", "FORG0001" },
      { "math:sqrt(xs:untypedAtomic(\"x\"))", "code", "FORG0001" },
      { "local-name-from-QName(xs:untypedAtomic(\"a\"))", "code", "XPTY0117" },
      { "error(xs:untypedAtomic(\"a\"))", "code", "XPTY0117" },
      { "function-lookup(xs:untypedAtomic(\"fn:abs\"), 1)", "code", "XPTY0117" },
      { "local-name-from-QName(\"a\")", "code", "XPTY0004" },
    });
  }

  /** Nodes are atomized, arrays flattened to their members. */
  @Test
  public void testNodesAndArrays ()
  {
    _run (false, new String [] [] {
      { "upper-case(r/s)", "want", "ABC" },
      { "string-length(r/s)", "want", "3" },
      { "string-length(r/nothing)", "want", "0" },
      { "abs(r/a)", "want", "3" },
      { "round(r/n)", "want", "3" },
      { "substring(\"abcdef\", r/a)", "want", "cdef" },
      { "math:sqrt(r/a) gt 1.7", "want", "true" },
      { "abs(r/b)", "code", "FORG0001" },
      { "abs(r/s)", "code", "FORG0001" },
      { "contains-token(r/s, \"abc\")", "want", "true" },
      { "string-join(r/*, \"-\")", "want", "3-x-2.5-abc" },
      { "upper-case(r/comment())", "want", "C" },
      { "name(r/a)", "want", "a" },
      { "name(1)", "code", "XPTY0004" },
      { "root(\"a\")", "code", "XPTY0004" },
      { "innermost((r, 5))", "code", "XPTY0004" },
      { "in-scope-prefixes(5)", "code", "XPTY0004" },
      { "in-scope-prefixes(/)", "code", "XPTY0004" },
      { "id(\"a\", 5)", "code", "XPTY0004" },
      { "lang(\"en\", 5)", "code", "XPTY0004" },
      { "has-children(5)", "code", "XPTY0004" },
      { "node-name(1)", "code", "XPTY0004" },
      { "path(5)", "code", "XPTY0004" },
      { "generate-id(5)", "code", "XPTY0004" },
      { "resolve-QName(\"a\", 5)", "code", "XPTY0004" },
      { "count(innermost((r, r/a)))", "want", "1" },
      { "lang(\"en\", r)", "want", "false" },
      { "string-join([\"a\", \"b\"], \"-\")", "want", "a-b" },
      { "upper-case([\"a\"])", "want", "A" },
      { "abs([3])", "want", "3" },
      { "abs([1, 2])", "code", "XPTY0004" },
      { "empty(abs([]))", "want", "true" },
      { "abs([[3]])", "want", "3" },
    });
  }

  /** Maps, arrays and function items as arguments. */
  @Test
  public void testMapsArraysFunctions ()
  {
    _run (false, new String [] [] {
      { "map:get(\"x\", 1)", "code", "XPTY0004" },
      { "map:size([1])", "code", "XPTY0004" },
      { "array:size(map{})", "code", "XPTY0004" },
      { "array:size(1)", "code", "XPTY0004" },
      { "map:merge((map{1: 2}, 5))", "code", "XPTY0004" },
      { "map:merge(map{1: 2}, 5)", "code", "XPTY0004" },
      { "array:join(([1], 5))", "code", "XPTY0004" },
      { "map:for-each(map{}, 5)", "code", "XPTY0004" },
      { "array:for-each([1], 5)", "code", "XPTY0004" },
      { "array:filter([1], ())", "code", "XPTY0004" },
      { "array:fold-left([1], 0, \"f\")", "code", "XPTY0004" },
      { "for-each((1, 2), \"x\")", "code", "XPTY0004" },
      { "filter(1, 5)", "code", "XPTY0004" },
      { "fold-left(1, 0, ())", "code", "XPTY0004" },
      { "sort((3, 1), (), 5)", "code", "XPTY0004" },
      { "sort((3, 1), (\"a\", \"b\"))", "code", "XPTY0004" },
      { "apply(abs#1, 5)", "code", "XPTY0004" },
      { "apply(5, [1])", "code", "XPTY0004" },
      { "function-arity(5)", "code", "XPTY0004" },
      { "function-name(\"abs\")", "code", "XPTY0004" },
      { "array:subarray([1, 2], \"1\")", "code", "XPTY0004" },
      { "map:remove(map{1: 2}, map{})", "code", "FOTY0013" },
      { "apply(abs#1, [-1])", "want", "1" },
      { "map:keys(map{1: 2})", "want", "1" },
      { "map:get(map{1: 2}, 1)", "want", "2" },
      { "map:contains(map{\"a\": 1}, xs:untypedAtomic(\"a\"))", "want", "true" },
      { "array:size([1, 2, 3])", "want", "3" },
      { "array:get([7, 8], 2)", "want", "8" },
      { "count(array:join(([1], [2, 3])))", "want", "1" },
      { "array:size(array:join(([1], [2, 3])))", "want", "3" },
      { "map:size(map:merge((map{1: 2}, map{3: 4})))", "want", "2" },
      { "map:for-each(map{1: 2}, function($k, $v) { $k + $v })", "want", "3" },
      { "string-join(for-each((1, 2), function($x) { $x * 2 }) ! string(), \" \")", "want", "2 4" },
      { "array:sort([3, 1, 2])?1", "want", "1" },
      { "for-each((1), map{1: \"one\"})", "want", "one" },
      { "for-each((1), [9, 8])", "want", "9" },
    });
  }

  /** Dynamic calls, named references, function-lookup and partial application are checked like a direct call. */
  @Test
  public void testAllCallPaths ()
  {
    _run (false, new String [] [] {
      { "upper-case#1(1)", "code", "XPTY0004" },
      { "let $f := upper-case#1 return $f(1)", "code", "XPTY0004" },
      { "function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'upper-case'), 1)(1)", "code", "XPTY0004" },
      { "let $f := substring(\"abc\", ?) return $f(\"x\")", "code", "XPTY0004" },
      { "let $f := contains-token(\"a\", \"a\", ?) return $f(())", "code", "XPTY0004" },
      { "for-each((\"a\", 1), upper-case#1)", "code", "XPTY0004" },
      { "apply(upper-case#1, [1])", "code", "XPTY0004" },
      { "1 => upper-case()", "code", "XPTY0004" },
      { "\"abc\" => upper-case()", "want", "ABC" },
      { "let $f := upper-case#1 return $f(\"abc\")", "want", "ABC" },
      { "let $f := substring(\"abc\", ?) return $f(2)", "want", "bc" },
      { "fn:upper-case(1)", "code", "XPTY0004" },
      { "Q{http://www.w3.org/2005/xpath-functions}upper-case(1)", "code", "XPTY0004" },
      { "Q{http://www.w3.org/2005/xpath-functions/math}sin(\"a\")", "code", "XPTY0004" },
      { "Q{http://www.w3.org/2005/xpath-functions/map}size(1)", "code", "XPTY0004" },
      { "Q{http://www.w3.org/2005/xpath-functions/array}size(1)", "code", "XPTY0004" },
      { "map:size#1(1)", "code", "XPTY0004" },
      { "array:size#1(1)", "code", "XPTY0004" },
      { "r/s ! upper-case(.)", "want", "ABC" },
    });
  }

  /** Arity errors stay XPST0017, ahead of any argument check. */
  @Test
  public void testArity ()
  {
    _run (false, new String [] [] {
      { "abs()", "code", "XPST0017" },
      { "abs(1, 2)", "code", "XPST0017" },
      { "format-date(xs:date(\"2000-01-01\"), \"[Y]\", \"en\")", "code", "XPST0017" },
      { "math:pi(1)", "code", "XPST0017" },
      { "map:size()", "code", "XPST0017" },
      { "map:size(map{}, 1)", "code", "XPST0017" },
      { "map:find(map{})", "code", "XPST0017" },
      { "array:head()", "code", "XPST0017" },
      { "array:sort([1], (), 5, 6)", "code", "XPST0017" },
      { "concat(\"a\")", "code", "XPST0017" },
      { "concat(\"a\", \"b\", \"c\", \"d\", \"e\")", "want", "abcde" },
      { "concat(1, 2, 3, 4, 5, 6, 7)", "want", "1234567" },
      { "concat(\"a\", \"b\", (), \"c\", ())", "want", "abc" },
      { "concat(\"a\", \"b\", (\"c\", \"d\"))", "code", "XPTY0004" },
      { "concat(\"a\", \"b\", 1, map{})", "code", "FOTY0013" },
    });
  }

  /** Under XPath 1.0 compatibility the strict checks are not made. */
  @Test
  public void testBackwardsCompatible ()
  {
    _run (true, new String [] [] {
      { "substring(\"abc\", \"2\")", "want", "bc" },
      { "contains(1, \"1\")", "want", "true" },
      { "abs(\"-3\")", "want", "3" },
    });
  }

  /** The same calls are errors outside XPath 1.0 compatibility. */
  @Test
  public void testNotBackwardsCompatible ()
  {
    _run (false, new String [] [] {
      { "substring(\"abc\", \"2\")", "code", "XPTY0004" },
      { "contains(1, \"1\")", "code", "XPTY0004" },
      { "abs(\"-3\")", "code", "XPTY0004" },
    });
  }

  /** A function item made from a built-in carries its declared signature. */
  @Test
  public void testFunctionItemSignatures ()
  {
    _run (false, new String [] [] {
      { "abs#1 instance of function(xs:numeric?) as xs:numeric?", "want", "true" },
      { "abs#1 instance of function(xs:string) as xs:string", "want", "false" },
      { "math:sqrt#1 instance of function(xs:double?) as xs:double?", "want", "true" },
      { "math:pi#0 instance of function() as xs:double", "want", "true" },
      { "map:get#2 instance of function(map(*), xs:anyAtomicType) as item()*", "want", "true" },
      { "array:size#1 instance of function(array(*)) as xs:integer", "want", "true" },
      { "substring#2 instance of function(xs:string?, xs:double) as xs:string", "want", "true" },
      { "substring#3 instance of function(xs:string?, xs:double) as xs:string", "want", "false" },
      { "concat#3 instance of function(xs:anyAtomicType?, xs:anyAtomicType?, xs:anyAtomicType?) as xs:string", "want", "true" },
      { "filter#2 instance of function(item()*, function(item()) as xs:boolean) as item()*", "want", "true" },
    });
  }

  /** What the engine returns is accepted by the parameters it feeds. */
  @Test
  public void testResultsFeedParameters ()
  {
    _run (false, new String [] [] {
      { "array:get([7, 8], position())", "want", "7" },
      { "array:get([7, 8], last() + 1)", "want", "8" },
      { "array:get([7, 8], count(r/a))", "want", "7" },
      { "array:get([7, 8], string-length('xy'))", "want", "8" },
      { "array:get([7, 8], array:size([1]))", "want", "7" },
      { "array:get([7, 8], map:size(map{1: 1}))", "want", "7" },
      { "array:get([7, 8], index-of((5, 6), 6))", "want", "8" },
      { "array:get([7, 8], 3 idiv 2)", "want", "7" },
      { "array:get([7, 8], 5 mod 3)", "want", "8" },
      { "array:get([7, 8], xs:integer('2'))", "want", "8" },
      { "array:get([7, 8], xs:int(2))", "want", "8" },
      { "array:get([7, 8], sum((1, 1)))", "want", "8" },
      { "array:get([7, 8], max((1, 2)))", "want", "8" },
      { "array:get([7, 8], (1 to 3)[2])", "want", "8" },
      { "array:get([7, 8], abs(-2))", "want", "8" },
      { "array:get([7, 8], round(1.5))", "code", "XPTY0004" },
      { "array:get([7, 8], 2 * 1)", "want", "8" },
      { "array:get([7, 8, 9], r/a)", "want", "9" },
      { "format-integer(position(), '1')", "want", "1" },
      { "format-integer(count(r/*), '1')", "want", "4" },
      { "substring('abcdef', string-length('ab'), count(r/*))", "want", "bcde" },
      { "round(2.567, count(r/a) + 1)", "want", "2.57" },
    });
  }

  /** A boolean is no arithmetic operand; "div" and "mod" after an occurrence indicator are operators. */
  @Test
  public void testBooleanArithmetic ()
  {
    _run (new String [] [] {
      { "1 + true()", "code", "XPTY0004" },
      { "false() * 2", "code", "XPTY0004" },
      { "-true()", "code", "XPTY0004" },
      { "1 + 1 instance of xs:double", "code", "XPTY0004" },
      { "(1,2) instance of xs:integer* div 2", "code", "XPTY0004" },
      { "(1,2) instance of xs:integer* mod 2", "code", "XPTY0004" },
      { "(r/a + 1) instance of xs:double and r/a + 1 eq 4", "want", "true" },
    });
  }

  /** The reported argument checks of contains-token, math:atan2 and fn:error. */
  @Test
  public void testReportedArgumentChecks ()
  {
    _run (new String [] [] {
      { "contains-token('red green blue', 'RED', ())", "code", "XPTY0004" },
      { "contains-token('red green blue', ())", "code", "XPTY0004" },
      { "math:atan2((), 1)", "code", "XPTY0004" },
      { "math:atan2(1, ())", "code", "XPTY0004" },
      { "error('a')", "code", "XPTY0004" },
      { "'a' => error()", "code", "XPTY0004" },
      { "error((QName('urn:e', 'a'), QName('urn:e', 'b')))", "code", "XPTY0004" },
      { "error(xs:untypedAtomic('a'))", "code", "XPTY0117" },
      { "error(QName('urn:e', 'E1'), (), 'v')", "code", "XPTY0004" },
      { "error(QName('urn:e', 'E1'), ('a', 'b'))", "code", "XPTY0004" },
      { "error(())", "code", "FOER0000" },
      { "error((), 'only a description')", "code", "FOER0000" },
    });
  }

  /** The nillable question mark exists in element() tests only. */
  @Test
  public void testAttributeTestHasNoNillable ()
  {
    for (final String sSrc : new String [] { "//@attribute(a, xs:untypedAtomic?)",
                                             "//@attribute(*, xs:integer?)",
                                             ". instance of attribute(a, xs:string?)" })
      try
      {
        XPathCompiler.compile (sSrc, new StaticContext ());
        fail (sSrc);
      }
      catch (final XPathException ex)
      {
        assertEquals (sSrc, "XPST0003", ex.getCode ());
      }
    for (final String sSrc : new String [] { "//element(a, xs:integer?)",
                                             "//@attribute(a, xs:untypedAtomic)",
                                             ". instance of attribute(a, xs:string)?",
                                             ". instance of element(*, xs:anyType?)?" })
      assertNotNull (sSrc, XPathCompiler.compile (sSrc, new StaticContext ()));
  }

  @Test
  public void testMessage ()
  {
    try
    {
      FnTestHelper.eval ("contains-token('a', 'a', ())");
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPTY0004", ex.getCode ());
      for (final String s : new String [] { "fn:contains-token", "argument 3", "xs:string", "empty sequence" })
        assertTrue (s + " missing in: " + ex.getMessage (), ex.getMessage ().contains (s));
    }
  }
}
