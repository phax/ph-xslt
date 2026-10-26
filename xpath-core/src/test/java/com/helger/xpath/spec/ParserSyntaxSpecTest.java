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
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;

/**
 * Specification tests of the XPath grammar and lexer, ported from go-xslt's
 * <code>parser_syntax_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ParserSyntaxSpecTest
{
  /** A small source document shared by the expression tables. */
  static final String PE_DOC = "<r xmlns:p=\"urn:p\"><a x=\"1\">one</a><a>two</a><p:b/><?t data?><!--c--></r>";

  /**
   * Assert that compiling an expression fails.
   *
   * @param sExpr
   *        Expression.
   * @param sCode
   *        The expected error code, <code>null</code> for any.
   * @param aSetup
   *        Static context adjustment, may be <code>null</code>.
   */
  private static void _assertStaticError (@NonNull final String sExpr,
                                          @Nullable final String sCode,
                                          @Nullable final Consumer <StaticContext> aSetup)
  {
    final StaticContext aSC = new StaticContext ();
    if (aSetup != null)
      aSetup.accept (aSC);
    try
    {
      XPathCompiler.compile (sExpr, aSC);
      fail ("Compiling '" + sExpr + "' succeeded, want " + (sCode != null ? sCode : "a static error"));
    }
    catch (final XPathException ex)
    {
      if (sCode != null)
        assertEquals ("Compiling '" + sExpr + "': " + ex.getMessage (), sCode, ex.getCode ());
    }
  }

  /**
   * TestPeGrammarErrors: each input violates the XPath 3.1 grammar (Appendix A.1), so Parse must
   * reject it. Hosts report a parse error without a code as XPST0003, so only the failure itself is
   * asserted here.
   */
  @Test
  public void peGrammarErrors ()
  {
    final String [] aBad = {
                             // for / let / quantified (§3.12, §3.13)
                             "for $x in (1, 2)",
                             "for $x (1) return 1",
                             "for $ in 1 return 1",
                             "for $x in return 1",
                             "for $x in 1 return",
                             "let $x = 1 return $x",
                             "let $x := 1",
                             "let $x := return 1",
                             "let $x := 1 return",
                             "some $x in (1, 2)",
                             "some $x in satisfies 1",
                             "every $x in 1 satisfies",
                             // if (§3.15)
                             "if 1 then 2 else 3",
                             "if (1 then 2 else 3",
                             "if () then 1 else 2",
                             "if (1) 2 else 3",
                             "if (1) then 2",
                             "if (1) then else 3",
                             "if (1) then 2 else",
                             // binary operators with a missing right operand
                             "1 or",
                             "1 and",
                             "1 =",
                             "1 >>",
                             "'a' ||",
                             "1 to",
                             "2 *",
                             "2 +",
                             "a |",
                             "a intersect",
                             "a except",
                             "1 ! ",
                             // type expressions (§3.14)
                             "1 instance xs:integer",
                             "1 instance of",
                             "1 treat xs:integer",
                             "1 treat as",
                             "1 castable xs:integer",
                             "1 castable as",
                             "1 cast xs:integer",
                             "1 cast as",
                             // arrow (§3.16): a specifier and an argument list are required
                             "'a' => 2",
                             "'a' => upper-case",
                             "'a' => $",
                             "'a' => (upper-case#1",
                             "'a' => ()",
                             "'a' => upper-case(",
                             // paths and steps
                             "/a/",
                             "a//",
                             "child::",
                             "@",
                             "a[1",
                             "a[",
                             "a[]",
                             "a/b[1",
                             "(1)[",
                             "(1)[1",
                             // function calls and primaries
                             "f(1,",
                             "f(1",
                             "f(,)",
                             "(1",
                             "(1, 2",
                             "$",
                             "$1",
                             ")",
                             "concat#x",
                             "concat#",
                             // constructors (§3.11)
                             "map{1 2}",
                             "map{1:",
                             "map{1:2",
                             "map{1:2,}",
                             "map{",
                             "map{:1}",
                             "[1,",
                             "[1",
                             "[,]",
                             "array{1",
                             "array{",
                             "array{,}",
                             "function($a $b) {1}",
                             "function($a as) {1}",
                             "function(a) {1}",
                             "function($) {1}",
                             "function($a) as {1}",
                             "function($a) 1",
                             "function($a) {1",
                             "function($a {1}",
                             "function($a) {,}",
                             // kind tests
                             "element(",
                             "element(a",
                             "attribute(a,",
                             "1 + #",
                             "document-node(",
                             "document-node(text())",
                             "processing-instruction(",
                             "text(1)",
                             "schema-element()",
                             // lookups (§3.11.3): a key specifier is NCName | IntegerLiteral |
                             // ParenthesizedExpr | "*"
                             "map{}?'x'",
                             "map{}?(1",
                             "map{}?",
                             "map{}?(,)",
                             // lexical errors
                             "'abc",
                             "\"abc",
                             "(: abc",
                             "(: (: a :) b",
                             "1 (: open", };
    for (final String sSrc : aBad)
      _assertStaticError (sSrc, null, null);
  }

  /**
   * Covers the parse-time errors whose codes the spec names explicitly.
   */
  @Test
  public void peStaticErrorCodes ()
  {
    final String [] [] aCases = {
                                  // §3.3.2.1: the axis names are a closed list; anything else is a
                                  // grammar error.
                                  { "foo::a", "XPST0003" },
                                  { "a/bogus::b", "XPST0003" },
                                  // PITest takes an NCName or a string literal (A.1 production
                                  // PITest); a QName is not an NCName.
                                  { "processing-instruction(a:b)", "XPST0003" },
                                  // DocumentTest ::= "document-node" "(" (ElementTest |
                                  // SchemaElementTest)? ")".
                                  { "document-node(attribute())", "XPST0003" },
                                  // A.2.2 terminal delimitation: a numeric literal must be followed
                                  // by a delimiter (xgc: whitespace / delimiting terminal symbols).
                                  { "12abc", "XPST0003" },
                                  { "1.2.3", "XPST0003" },
                                  { ".5a", "XPST0003" },
                                  { "1e5x", "XPST0003" },
                                  // A name must begin with a NameStartChar (A.2.1, XML 1.0 Names).
                                  { "µ", "XPST0003" },
                                  // §3.11.3.1: NCName key / IntegerLiteral key.
                                  { "map{}?a:b", "XPST0003" },
                                  { "[1]?1.5", "XPST0003" },
                                  { "[1]?1e0", "XPST0003" },
                                  // §3.1.7: an inline function may not have two parameters with the
                                  // same name.
                                  { "function($a, $a) {1}", "XQST0039" },
                                  { "function($a, $Q{}a) {1}", "XQST0039" }, };
    for (final String [] aCase : aCases)
      _assertStaticError (aCase[0], aCase[1], null);
    // A.3 reserved function names may not be used unprefixed in a
    // NamedFunctionRef (xgc: reserved-function-names).
    for (final String sSrc : new String [] { "node#0", "if#1", "item#0", "switch#1", "map#1" })
      _assertStaticError (sSrc, null, null);
  }

  /**
   * TestPeLexer exercises lexical rules of A.2: comments (nested), doubled quote escapes, numeric
   * literal forms and two-character operators.
   */
  @Test
  public void peLexer ()
  {
    run (env (PE_DOC).ns ("p", "urn:p"),
         // A.2.4: comments nest.
         ok ("1 (: a (: b :) c :) + 1", "2"),
         ok ("(: lead :) 3", "3"),
         // §3.1.1: a doubled delimiter inside a literal is one quote.
         ok ("'it''s'", "it's"),
         ok ("\"say \"\"hi\"\"\"", "say \"hi\""),
         ok ("'\"'", "\""),
         // Numeric literals (§3.1.1): .5 is a decimal, exponent forms are
         // doubles.
         ok (".5 instance of xs:decimal", "true"),
         ok (".5 + .25", "0.75"),
         ok ("1e+2 instance of xs:double", "true"),
         ok ("1E2 = 100", "true"),
         ok ("25e-1 = 2.5", "true"),
         ok ("1.5e1 = 15", "true"),
         // ':' after a numeric literal is the map entry separator.
         ok ("map{1:'x'}(1)", "x"),
         // Two-character operators.
         ok ("/r/a[2] >> /r/a[1]", "true"),
         ok ("/r/a[1] >> /r/a[2]", "false"),
         ok ("/r/a[1] << /r/a[2]", "true"),
         ok ("2 >= 2", "true"),
         ok ("1 <= 0", "false"),
         ok ("1 != 2", "true"),
         ok ("let $x := 1 return $x", "1"),
         // A prefix followed by '*' is a wildcard name test, not
         // multiplication, even after an operand-ending token.
         ok ("count(/r/*[self::p:* except self::p:c])", "1"),
         ok ("count(/r/p:*)", "1"),
         ok ("count(/r/*:b)", "1"),
         // '*' after an operand is multiplication.
         ok ("2*3", "6"),
         ok ("count(/r/*) * 2", "6"));
  }

  /**
   * Checks expressions that exercise the less common grammar productions and their meaning.
   */
  @Test
  public void peParsedForms ()
  {
    run (env (PE_DOC).ns ("p", "urn:p"),
         // §3.16 arrow: the specifier may be an EQName, a variable reference
         // or a parenthesized expression.
         ok ("'a' => Q{http://www.w3.org/2005/xpath-functions}upper-case()", "A"),
         ok ("let $f := upper-case#1 return 'a' => $f()", "A"),
         ok ("'a' => (upper-case#1)()", "A"),
         ok ("'abc' => substring(2)", "bc"),
         // A placeholder in the arrow's argument list makes the result a
         // partial application (§3.1.5.1): E => f(?) is f(E, ?).
         ok ("('abcd' => substring(?))(3)", "cd"),
         ok ("(('abcd' => substring(?, ?))(2, 2))", "bc"),
         ok ("let $f := substring#3 return ('abcd' => $f(?, 1))(2)", "b"),
         ok ("'a' => concat('b') => concat('c')", "abc"),
         // §3.3.5: an AttributeTest makes the default axis attribute, a
         // NamespaceNodeTest makes it namespace.
         ok ("string(/r/a[1]/attribute())", "1"),
         ok ("count(/r/a/attribute(x))", "1"),
         ok ("count(/r/a/attribute(*))", "1"),
         ok ("count(/r/a/attribute(y))", "0"),
         ok ("count(/r/namespace-node())", "2"),
         ok ("string-join(for $n in /r/namespace-node() return name($n), ',') = ('p,xml', 'xml,p')", "true"),
         // '.' as a step is self::node() and takes predicates.
         ok ("count(/r/a/.[@x])", "1"),
         ok ("count(/r/a/.)", "2"),
         // Kind tests.
         ok ("count(/r/processing-instruction('t'))", "1"),
         ok ("count(/r/processing-instruction(t))", "1"),
         ok ("count(/r/processing-instruction(u))", "0"),
         ok ("count(/r/processing-instruction())", "1"),
         ok ("count(/r/comment())", "1"),
         ok ("count(/r/element(a))", "2"),
         ok ("count(/r/element(Q{urn:p}b))", "1"),
         ok ("count(/r/element(p:b))", "1"),
         ok ("count(/r/element(*))", "3"),
         ok ("(/) instance of document-node(element(r))", "true"),
         ok ("(/) instance of document-node(element(a))", "false"),
         ok ("(/) instance of document-node()", "true"),
         ok ("/r instance of element(r, xs:untyped?)", "true"),
         // §3.11.3 lookups.
         ok ("map{'a': 1} ! ?a", "1"),
         ok ("string-join(map{'a': 1, 'b': 2}?('a', 'b'), ',')", "1,2"),
         ok ("count(map{'a': 1}?())", "0"),
         ok ("sum(map{'a': 1, 'b': 2}?*)", "3"),
         ok ("[10, 20, 30]?2", "20"),
         ok ("string-join([10, 20, 30]?(1 to 2), ',')", "10,20"),
         ok ("string-join([10, 20]?*, ',')", "10,20"),
         ok ("[[1, 2], [3]]?1?2", "2"),
         ok ("[1, 2] ! ?2", "2"),
         ok ("[10, 20]?(xs:untypedAtomic('1'))", "10"),
         // Braced variable names (§3.1.2: an EQName).
         ok ("let $Q{urn:v}x := 1 return $Q{urn:v}x + 1", "2"),
         ok ("let $x := 2 return $Q{}x", "2"),
         ok ("let $p:x := 3 return $Q{urn:p}x", "3"),
         // Function literals (§3.1.6).
         ok ("Q{http://www.w3.org/2005/xpath-functions}upper-case#1('a')", "A"),
         ok ("fn:upper-case#1('b')", "B"),
         ok ("Q{http://www.w3.org/2005/xpath-functions}concat('a', 'b')", "ab"),
         // Inline functions (§3.1.7).
         ok ("function($a as xs:integer) as xs:integer {$a + 1}(2)", "3"),
         ok ("count(function() {}())", "0"),
         ok ("function($a, $b) {$a * $b}(3, 4)", "12"),
         ok ("function($a as xs:decimal) {$a}(1) instance of xs:integer", "true"),
         ok ("function($a as xs:double) {$a}(1) instance of xs:double", "true"),
         ok ("function() as xs:double {1}() instance of xs:double", "true"),
         // Parenthesized and empty sequences.
         ok ("count(())", "0"),
         ok ("((1), (2, 3))[3]", "3"),
         // Square and curly arrays.
         ok ("array:size([])", "0"),
         ok ("array:size([(), (1, 2)])", "2"),
         ok ("array:size(array{(), (1, 2)})", "2"),
         ok ("array:size(array{})", "0"),
         ok ("map:size(map{})", "0"));
  }

  /**
   * TestPeParseStaticTypingEntry: ParseStaticTyping accepts an ordinary expression exactly like
   * Parse.
   */
  @Test
  public void peParseStaticTypingEntry ()
  {
    run (env ().noContext ().staticSetup (x -> x.setStaticTyping (true)), ok ("1 + 2", "3"));
    _assertStaticError ("1 +", null, x -> x.setStaticTyping (true));
  }
}
