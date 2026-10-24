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
package com.helger.xpath.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for {@link XPathParser#parse(String, StaticContext)}: AST shapes.
 *
 * @author Philip Helger
 */
public final class XPathParserTest
{
  private static void _check (@NonNull final String sExpected, @NonNull final String sExpr)
  {
    assertEquals (sExpr, sExpected, ParserTestHelper.parse (sExpr));
  }

  @NonNull
  private static IExpr _parse (@NonNull final String sExpr)
  {
    return XPathParser.parse (sExpr, ParserTestHelper.createContext ());
  }

  @Test
  public void testLiterals ()
  {
    final IExpr.LiteralExpr aInt = (IExpr.LiteralExpr) _parse ("42");
    assertEquals (EAtomicType.INTEGER, aInt.value ().getType ());
    assertEquals (BigInteger.valueOf (42), aInt.value ().getValue ());

    final IExpr.LiteralExpr aBig = (IExpr.LiteralExpr) _parse ("123456789012345678901234567890");
    assertEquals (new BigInteger ("123456789012345678901234567890"), aBig.value ().getValue ());

    final IExpr.LiteralExpr aDec = (IExpr.LiteralExpr) _parse ("1.50");
    assertEquals (EAtomicType.DECIMAL, aDec.value ().getType ());
    assertEquals (new BigDecimal ("1.50"), aDec.value ().getValue ());

    assertEquals (EAtomicType.DECIMAL, ((IExpr.LiteralExpr) _parse (".5")).value ().getType ());
    assertEquals (EAtomicType.DECIMAL, ((IExpr.LiteralExpr) _parse ("5.")).value ().getType ());

    final IExpr.LiteralExpr aDbl = (IExpr.LiteralExpr) _parse ("1.5e3");
    assertEquals (EAtomicType.DOUBLE, aDbl.value ().getType ());
    assertEquals (Double.valueOf (1500), aDbl.value ().getValue ());
    assertEquals (EAtomicType.DOUBLE, ((IExpr.LiteralExpr) _parse ("1E-2")).value ().getType ());
    assertEquals (EAtomicType.DOUBLE, ((IExpr.LiteralExpr) _parse (".5e+1")).value ().getType ());

    final IExpr.LiteralExpr aStr = (IExpr.LiteralExpr) _parse ("'it''s'");
    assertEquals (EAtomicType.STRING, aStr.value ().getType ());
    assertEquals ("it's", aStr.value ().getString ());
    assertEquals ("say \"hi\"", ((IExpr.LiteralExpr) _parse ("\"say \"\"hi\"\"\"")).value ().getString ());
    assertEquals ("", ((IExpr.LiteralExpr) _parse ("''")).value ().getString ());
    // No entity references in XPath
    assertEquals ("&amp;", ((IExpr.LiteralExpr) _parse ("'&amp;'")).value ().getString ());
  }

  @Test
  public void testCommentsAndWhitespace ()
  {
    _check ("(add 1 2)", "1 (: comment :) + 2");
    _check ("(add 1 2)", "1(: outer (: nested :) still :)+2");
    _check ("(add 1 2)", "\t1\r\n+\n2 ");
    _check ("(seq)", "( (: empty :) )");
  }

  @Test
  public void testPrimaries ()
  {
    _check (".", ".");
    _check ("(seq)", "()");
    _check ("1", "(1)");
    _check ("(seq 1 2 3)", "(1, 2, 3)");
    _check ("(seq 1 2)", "1, 2");
    _check ("$v", "$v");
    _check ("$v", "$ v");
    _check ("$Q{urn:p}pv", "$p:pv");
    _check ("$Q{urn:p}pv", "$Q{urn:p}pv");
    _check ("$v", "$Q{}v");
    _check ("(fn:true)", "true()");
    _check ("(fn:count $v)", "count($v)");
    _check ("(fn:count $v)", "fn:count($v)");
    _check ("(fn:count $v)", "Q{http://www.w3.org/2005/xpath-functions}count($v)");
    _check ("(fn:count $v)", "Q{  http://www.w3.org/2005/xpath-functions }count($v)");
    _check ("(xs:integer '1')", "xs:integer('1')");
    _check ("(math:pi)", "math:pi()");
    _check ("(map:get $m 'a')", "map:get($m, 'a')");
  }

  @Test
  public void testFunctionReferencesAndPartialApplication ()
  {
    _check ("fn:abs#1", "abs#1");
    _check ("fn:string-join#2", "fn:string-join#2");
    _check ("xs:integer#1", "xs:integer#1");
    _check ("(fn:substring ? 1 ?)", "substring(?, 1, ?)");
    _check ("(fn:concat 'a' ?)", "concat('a', ?)");
    _check ("(dyn (fn:concat 'a' ?) 'b')", "(concat('a', ?))('b')");
    _check ("(dyn $f ?)", "$f(?)");
    _check ("(dyn (dyn $f 1) 2)", "$f(1)(2)");
    // A '?' not followed by ',' or ')' is a unary lookup
    _check ("(fn:count (ulookup 'a'))", "count(?a)");
    // fn:concat is variadic
    _check ("(fn:concat 1 2 3 4 5)", "concat(1, 2, 3, 4, 5)");
  }

  @Test
  public void testInlineFunctions ()
  {
    _check ("(function () 1)", "function () { 1 }");
    _check ("(function () (seq))", "function () { }");
    _check ("(function () (seq))", "function () { (: nothing :) }");
    _check ("(function ($a, $b) (add $a $b))", "function($a, $b) { $a + $b }");
    _check ("(function ($a as xs:integer) as xs:string* (fn:string $a))",
            "function($a as xs:integer) as xs:string* { string($a) }");
    _check ("(function ($x) (function ($y) (add $x $y)))", "function($x) { function($y) { $x + $y } }");
    // Inline function bodies see the variables of the enclosing expression
    _check ("(for ($i in (to 1 3)) (function () $i))", "for $i in 1 to 3 return function() { $i }");
    _check ("(fn:for-each (seq 1 2 3) (function ($x) (multiply $x 10)))",
            "for-each((1,2,3), function($x) { $x * 10 })");
    _check ("(function ($Q{urn:p}a) $Q{urn:p}a)", "function($p:a) { $p:a }");
  }

  @Test
  public void testArithmeticPrecedence ()
  {
    _check ("(add 2 (multiply 3 4))", "2 + 3 * 4");
    _check ("(multiply (add 2 3) 4)", "(2 + 3) * 4");
    _check ("(subtract (subtract 1 2) 3)", "1 - 2 - 3");
    _check ("(divide (multiply 6 2) 3)", "6 * 2 div 3");
    _check ("(add (integer_divide 5 2) (modulo 7 3))", "5 idiv 2 + 7 mod 3");
    _check ("(add (neg 3) 5)", "-3 + 5");
    _check ("(neg 3)", "--- 3");
    _check ("(plus 3)", "-- 3");
    _check ("(plus 3)", "+3");
    _check ("(subtract $v 1)", "$v - 1");
    _check ("(subtract $v (neg 1))", "$v - -1");
    // "a-b" is one name
    _check ("(path child::a-b)", "a-b");
    _check ("(subtract (path child::a) (path child::b))", "a - b");
  }

  @Test
  public void testLogicalAndComparison ()
  {
    _check ("(or (and 1 2) 3)", "1 and 2 or 3");
    _check ("(or 1 (and 2 3))", "1 or 2 and 3");
    _check ("(and (general_eq 'a' 'a') (general_ne 'b' 'c'))", "'a' = 'a' and 'b' != 'c'");
    _check ("(value_eq 1 1)", "1 eq 1");
    _check ("(value_ne 1 1)", "1 ne 1");
    _check ("(value_lt 1 1)", "1 lt 1");
    _check ("(value_le 1 1)", "1 le 1");
    _check ("(value_gt 1 1)", "1 gt 1");
    _check ("(value_ge 1 1)", "1 ge 1");
    _check ("(general_lt 1 2)", "1 < 2");
    _check ("(general_le 1 2)", "1 <= 2");
    _check ("(general_gt 1 2)", "1 > 2");
    _check ("(general_ge 1 2)", "1 >= 2");
    _check ("(node_is $v $v)", "$v is $v");
    _check ("(node_precedes $v $v)", "$v << $v");
    _check ("(node_follows $v $v)", "$v >> $v");
    _check ("(general_gt (add 1 2) (multiply 3 4))", "1 + 2 > 3 * 4");
    _check ("(general_eq (to 1 3) 2)", "1 to 3 = 2");
    _check ("(general_eq (concat 'a' 'b') 'ab')", "'a' || 'b' = 'ab'");
  }

  @Test
  public void testStringConcatAndRange ()
  {
    _check ("(concat 'a' 'b' 'c')", "'a' || 'b' || 'c'");
    _check ("(concat 1 '-' 2)", "1 || '-' || 2");
    _check ("(concat (concat 'a' 'b') 'c')", "('a' || 'b') || 'c'");
    _check ("(concat 'a' (to 1 2))", "'a' || 1 to 2");
    _check ("(to 1 (add 2 3))", "1 to 2 + 3");
    _check ("(to 1 5)", "1 to 5");
  }

  @Test
  public void testSetOperators ()
  {
    _check ("(union (path child::a) (path child::b))", "a | b");
    _check ("(union (path child::a) (path child::b))", "a union b");
    _check ("(union (path child::a) (intersect (path child::b) (path child::c)))", "a | b intersect c");
    _check ("(except (intersect (path child::a) (path child::b)) (path child::c))", "a intersect b except c");
    _check ("(multiply (union (path child::a) (path child::b)) 2)", "(a | b) * 2");
    _check ("(add 1 (union $v $v))", "1 + $v | $v");
  }

  @Test
  public void testKeywordsAsNames ()
  {
    // Operator keywords are names where an operand is expected
    _check ("(path child::div)", "div");
    _check ("(divide (path child::div) (path child::div))", "div div div");
    _check ("(and (path child::and) (path child::or))", "and and or");
    _check ("(multiply (path child::*) (path child::*))", "* * *");
    _check ("(path child::return)", "return");
    _check ("(path child::if)", "if");
    _check ("(path child::for)", "for");
    _check ("(path child::union child::except)", "union/except");
    _check ("(path / child::div)", "/div");
    _check ("(path child::a child::instance)", "a/instance");
    _check ("(path child::element)", "element");
    _check ("(path child::text)", "text");
    _check ("(path child::map)", "map");
    _check ("(path child::function)", "function");
    _check ("(path child::to)", "to");
    _check ("(to (path child::to) (path child::to))", "to to to");
    _check ("(path attribute::div)", "@div");
    _check ("(path child::eq)", "eq");
  }

  @Test
  public void testConditionalsAndBindings ()
  {
    _check ("(if (value_eq 1 1) 'y' 'n')", "if (1 eq 1) then 'y' else 'n'");
    _check ("(if (seq 1 2) 1 (if 2 3 4))", "if (1, 2) then 1 else if (2) then 3 else 4");
    _check ("(for ($x in (to 1 4)) (multiply $x $x))", "for $x in 1 to 4 return $x * $x");
    _check ("(for ($x in (seq 1 2)) ($y in $x) (add $x $y))", "for $x in (1,2), $y in $x return $x + $y");
    _check ("(let ($a := 5) (multiply $a 2))", "let $a := 5 return $a * 2");
    _check ("(let ($a := 1) ($b := $a) $b)", "let $a := 1, $b := $a return $b");
    _check ("(some ($x in (seq 1 2 3)) (general_eq $x 2))", "some $x in (1,2,3) satisfies $x = 2");
    _check ("(every ($x in (seq 2 4)) ($y in $x) (general_eq (modulo $y 2) 0))",
            "every $x in (2,4), $y in $x satisfies $y mod 2 = 0");
    _check ("(for ($Q{urn:p}x in 1) $Q{urn:p}x)", "for $p:x in 1 return $p:x");
    // ExprSingle has lower precedence than the comma
    _check ("(seq (for ($x in 1) $x) 2)", "for $x in 1 return $x, 2");
    _check ("(for ($in in 1) $in)", "for $in in 1 return $in");
    _check ("(let ($x := 1) (add $x 1))", "let $x:=1 return $x+1");
  }

  @Test
  public void testTypeOperators ()
  {
    _check ("(instance 'x' xs:string)", "'x' instance of xs:string");
    _check ("(instance (seq 1 2 3) xs:integer+)", "(1,2,3) instance of xs:integer+");
    _check ("(instance (seq) empty-sequence())", "() instance of empty-sequence()");
    _check ("(treat 3 xs:integer)", "3 treat as xs:integer");
    _check ("(cast '5' xs:integer)", "'5' cast as xs:integer");
    _check ("(cast '5' xs:integer?)", "'5' cast as xs:integer?");
    _check ("(castable '5' xs:integer)", "'5' castable as xs:integer");
    _check ("(cast 'a b' list:xs:NMTOKEN)", "'a b' cast as xs:NMTOKENS");
    _check ("(castable 'a' list:xs:IDREF?)", "'a' castable as xs:IDREFS?");
    _check ("(cast 'a' list:xs:ENTITY)", "'a' cast as xs:ENTITIES");
    _check ("(cast 1 xs:numeric)", "1 cast as xs:numeric");
    _check ("(castable 1 xs:error)", "1 castable as xs:error");
    // The occurrence indicator binds to the SequenceType
    _check ("(subtract (treat 4 item()+) 5)", "4 treat as item() + - 5");
    _check ("(multiply (treat 3 xs:integer?) 3)", "3 treat as xs:integer ? * 3");
    // Nesting order: instance of < treat < castable < cast < arrow < unary
    _check ("(instance (treat (castable (cast (neg 1) xs:string) xs:integer) xs:boolean) xs:boolean)",
            "-1 cast as xs:string castable as xs:integer treat as xs:boolean instance of xs:boolean");
    _check ("(and (instance 1 xs:integer) (instance 2 xs:integer))", "1 instance of xs:integer and 2 instance of xs:integer");
    _check ("(general_eq (cast 1 xs:string) '1')", "1 cast as xs:string = '1'");
    _check ("(instance 1 xs:integer)", "1 instance of Q{http://www.w3.org/2001/XMLSchema}integer");
  }

  @Test
  public void testArrow ()
  {
    _check ("(fn:upper-case 'hello')", "'hello' => upper-case()");
    _check ("(fn:count (seq 1 2 3))", "(1,2,3) => count()");
    _check ("(fn:string-join (fn:reverse (seq 1 2)) ',')", "(1, 2) => reverse() => string-join(',')");
    _check ("(dyn $f 1 2)", "1 => $f(2)");
    _check ("(dyn (function ($a) $a) 1)", "1 => (function($a) { $a })()");
    _check ("(fn:abs (neg 1))", "-1 => abs()");
    _check ("(fn:sum (map! (seq 1 2) (multiply . 2)))", "(1,2) ! (. * 2) => sum()");
    _check ("(cast (fn:abs 1) xs:string)", "1 => abs() cast as xs:string");
    _check ("(fn:substring 'abc' ? 2)", "'abc' => substring(?, 2)");
    _check ("(fn:count 1)", "1 => Q{http://www.w3.org/2005/xpath-functions}count()");
  }

  @Test
  public void testSimpleMap ()
  {
    _check ("(map! (seq 1 2 3) (multiply . 2))", "(1,2,3) ! (. * 2)");
    _check ("(map! (path child::a) (path child::b) (fn:string))", "a ! b ! string()");
    _check ("(map! (path / child::a) (path attribute::id))", "/a ! @id");
    _check ("(general_ne 1 2)", "1 != 2");
    _check ("(neg (map! 1 2))", "-1 ! 2");
  }

  @Test
  public void testPaths ()
  {
    _check ("(path /)", "/");
    _check ("(path / child::a)", "/a");
    _check ("(path / child::catalog child::book)", "/catalog/book");
    _check ("(path / descendant-or-self::node() child::title)", "//title");
    _check ("(path child::a descendant-or-self::node() child::b)", "a//b");
    _check ("(path child::a)", "a");
    _check ("(path child::a child::b child::c)", "a/b/c");
    _check ("(path attribute::id)", "@id");
    _check ("(path child::a attribute::id)", "a/@id");
    _check ("(path parent::node())", "..");
    _check ("(path parent::node() child::a)", "../a");
    _check ("(path parent::node()[(path attribute::x)])", "..[@x]");
    _check ("(path . child::a)", "./a");
    _check ("(path child::a {.})", "a/.");
    _check ("(path child::a {(filter .[1])})", "a/.[1]");
    _check ("(path / child::*)", "/*");
    _check ("(path child::*:local)", "*:local");
    _check ("(path child::Q{urn:p}*)", "p:*");
    _check ("(path child::Q{urn:x}*)", "Q{urn:x}*");
    _check ("(path child::Q{urn:x}a)", "Q{urn:x}a");
    _check ("(path child::Q{urn:p}a)", "p:a");
    _check ("(path child::book[(general_gt (path child::price) 50)] child::title)", "book[price > 50]/title");
    _check ("(path child::a[1][2])", "a[1][2]");
    _check ("(path / child::r child::a[(fn:position)])", "/r/a[position()]");
    _check ("(path (path /) child::r)", "(/)/r");
    // "union" after a leading slash is a name test (leading-lone-slash rule)
    _check ("(path / child::union child::a)", "/ union /a");
  }

  @Test
  public void testPathPostfixSteps ()
  {
    _check ("(path / child::r child::v {(xs:decimal .)})", "/r/v/xs:decimal(.)");
    _check ("(path child::a {$v})", "a/$v");
    _check ("(path child::a {(union (path child::b) (path child::c))})", "a/(b|c)");
    _check ("(path child::a {(filter (union (path child::b) (path child::c))[1])})", "a/(b|c)[1]");
    _check ("(path $v child::a)", "$v/a");
    _check ("(path (filter $v[1]) child::a)", "$v[1]/a");
    _check ("(path (fn:root) descendant-or-self::node() child::x)", "root()//x");
    _check ("(path / {42})", "/42");
    _check ("(path / {(array (path child::bid) (path child::bid))})", "/[bid, bid]");
    _check ("(path child::a {'lit'})", "a/'lit'");
    _check ("(path child::a {fn:abs#1})", "a/abs#1");
    _check ("(path child::a {(lookup $m 'k')})", "a/$m?k");
  }

  @Test
  public void testAxes ()
  {
    for (final EAxis e : EAxis.values ())
    {
      final IExpr.PathExpr aPath = (IExpr.PathExpr) _parse (e.getName () + "::x");
      final IExpr.StepExpr aStep = aPath.steps ().get (0);
      assertSame (e, aStep.axis ());
      assertTrue (aStep.axisExplicit ());
      assertEquals (ENodeTestKind.NAME, aStep.test ().kind ());
      // Principal node kind: element, except attribute and namespace
      assertEquals (e.getName (), "x", aStep.test ().name ().localName ());
    }
    _check ("(path parent::*)", "parent::*");
    _check ("(path ancestor-or-self::*)", "ancestor-or-self :: *");
    _check ("(path child::a following-sibling::b[1])", "a/following-sibling::b[1]");

    final IExpr.PathExpr aDefault = (IExpr.PathExpr) _parse ("a");
    assertFalse (aDefault.steps ().get (0).axisExplicit ());
    assertTrue (((IExpr.PathExpr) _parse ("@a")).steps ().get (0).axisExplicit ());
    assertTrue (((IExpr.PathExpr) _parse ("..")).steps ().get (0).axisExplicit ());
  }

  @Test
  public void testKindTests ()
  {
    _check ("(path child::node())", "node()");
    _check ("(path child::text())", "text()");
    _check ("(path child::comment())", "comment()");
    _check ("(path child::processing-instruction())", "processing-instruction()");
    _check ("(path child::processing-instruction(pi))", "processing-instruction(pi)");
    _check ("(path child::processing-instruction(pi))", "processing-instruction('  pi ')");
    _check ("(path child::element())", "element()");
    _check ("(path child::element())", "element(*)");
    _check ("(path child::element(a))", "element(a)");
    _check ("(path child::element(a,xs:string))", "element(a, xs:string)");
    _check ("(path child::element(a,xs:anyType?))", "element(a, xs:anyType?)");
    _check ("(path child::element(*,xs:untyped))", "element(*, xs:untyped)");
    // Abbreviated axis for attribute tests and namespace-node()
    _check ("(path attribute::attribute())", "attribute()");
    _check ("(path attribute::attribute(a,xs:integer))", "attribute(a, xs:integer)");
    _check ("(path child::a attribute::attribute(b))", "a/attribute(b)");
    _check ("(path namespace::namespace-node())", "namespace-node()");
    _check ("(path / child::* namespace::namespace-node())", "/*/namespace-node()");
    _check ("(path child::document-node())", "document-node()");
    _check ("(path child::document-node(element(a)))", "document-node(element(a))");
    _check ("(path child::document-node(element()))", "document-node(element(*))");
    _check ("(path self::element(a))", "self::element(a)");
    _check ("(path attribute::node())", "@node()");
  }

  @Test
  public void testNameResolution ()
  {
    final StaticContext aCtx = ParserTestHelper.createContext ();
    aCtx.setDefaultElementNamespace ("urn:elem");
    aCtx.setDefaultFunctionNamespace (XdmConstants.NS_FN);

    // Elements use the default element namespace, attributes do not
    IExpr.PathExpr aPath = (IExpr.PathExpr) XPathParser.parse ("a/@b/namespace::c/attribute::d/self::e", aCtx);
    assertEquals (new XdmQName ("urn:elem", "a"), aPath.steps ().get (0).test ().name ());
    assertEquals (new XdmQName ("", "b"), aPath.steps ().get (1).test ().name ());
    assertEquals (new XdmQName ("", "c"), aPath.steps ().get (2).test ().name ());
    assertEquals (new XdmQName ("", "d"), aPath.steps ().get (3).test ().name ());
    assertEquals (new XdmQName ("urn:elem", "e"), aPath.steps ().get (4).test ().name ());

    aPath = (IExpr.PathExpr) XPathParser.parse ("element(x)/attribute(y)/p:z", aCtx);
    assertEquals (new XdmQName ("urn:elem", "x"), aPath.steps ().get (0).test ().name ());
    assertEquals (new XdmQName ("", "y"), aPath.steps ().get (1).test ().name ());
    assertEquals (new XdmQName ("urn:p", "z"), aPath.steps ().get (2).test ().name ());
    assertEquals ("p", aPath.steps ().get (2).test ().name ().prefix ());

    // Q{} is no namespace even with a default element namespace
    aPath = (IExpr.PathExpr) XPathParser.parse ("Q{}a", aCtx);
    assertEquals (new XdmQName ("", "a"), aPath.steps ().get (0).test ().name ());

    // Variables are never in the default element namespace
    final IExpr.VarRefExpr aVar = (IExpr.VarRefExpr) XPathParser.parse ("$v", aCtx);
    assertEquals (new XdmQName ("", "v"), aVar.name ());

    // Default function namespace
    final StaticContext aCtx2 = ParserTestHelper.createContext ();
    aCtx2.setDefaultFunctionNamespace (XdmConstants.NS_XS);
    final IExpr.FunctionCallExpr aCall = (IExpr.FunctionCallExpr) XPathParser.parse ("integer('1')", aCtx2);
    assertEquals (new XdmQName (XdmConstants.NS_XS, "integer"), aCall.name ());

    // Unprefixed type names use the default element/type namespace
    final StaticContext aCtx3 = ParserTestHelper.createContext ();
    aCtx3.setDefaultElementNamespace (XdmConstants.NS_XS);
    final IExpr.InstanceOfExpr aInst = (IExpr.InstanceOfExpr) XPathParser.parse ("1 instance of integer", aCtx3);
    assertEquals (EAtomicType.INTEGER, aInst.type ().itemType ().atomicType ());
    final IExpr.CastExpr aCast = (IExpr.CastExpr) XPathParser.parse ("1 cast as string", aCtx3);
    assertEquals (EAtomicType.STRING, aCast.type ().itemType ().atomicType ());
  }

  @Test
  public void testWildcardNames ()
  {
    final IExpr.StepExpr aLocal = ((IExpr.PathExpr) _parse ("*:a")).steps ().get (0);
    assertTrue (aLocal.test ().anyNamespace ());
    assertFalse (aLocal.test ().anyName ());
    assertEquals ("a", aLocal.test ().name ().localName ());

    final IExpr.StepExpr aPrefix = ((IExpr.PathExpr) _parse ("p:*")).steps ().get (0);
    assertTrue (aPrefix.test ().anyLocalName ());
    assertEquals ("urn:p", aPrefix.test ().name ().namespaceUri ());

    final IExpr.StepExpr aAny = ((IExpr.PathExpr) _parse ("@*")).steps ().get (0);
    assertTrue (aAny.test ().anyName ());
    assertNull (aAny.test ().name ());

    final IExpr.StepExpr aNoNs = ((IExpr.PathExpr) _parse ("Q{}*")).steps ().get (0);
    assertTrue (aNoNs.test ().anyLocalName ());
    assertEquals ("", aNoNs.test ().name ().namespaceUri ());
    _check ("(except (path self::Q{urn:p}*) (path self::Q{urn:p}output))", "self::p:* except self::p:output");
  }

  @Test
  public void testMapsAndArrays ()
  {
    _check ("(mapc)", "map {}");
    _check ("(mapc ('a' 1) ('b' 2))", "map { 'a': 1, 'b': 2 }");
    _check ("(mapc (1 'x'))", "map{1:'x'}");
    _check ("(mapc ((path child::a) (path child::b)))", "map{a :b}");
    _check ("(mapc ((path child::Q{urn:p}b) 1))", "map{p:b : 1}");
    _check ("(mapc ('k' (mapc ('n' 1))))", "map{'k': map{'n': 1}}");
    _check ("(array)", "[]");
    _check ("(array 1 (seq 2 3))", "[1, (2, 3)]");
    _check ("(array{})", "array {}");
    _check ("(array{} (seq 1 2))", "array { 1, 2 }");
    _check ("(array{} (to 1 4))", "array { 1 to 4 }");
  }

  @Test
  public void testLookup ()
  {
    _check ("(lookup (mapc ('a' 1)) 'a')", "map { 'a': 1 }?a");
    _check ("(lookup (array 10 20) 2)", "[10, 20]?2");
    _check ("(lookup $m *)", "$m?*");
    _check ("(lookup $m (seq 'a' 'b'))", "$m?('a', 'b')");
    _check ("(lookup $m (seq))", "$m?()");
    _check ("(lookup $m 'a-b')", "$m?a-b");
    _check ("(lookup (lookup $m 'a') 'b')", "$m?a?b");
    _check ("(lookup (filter (lookup $m 'a')[1]) 'b')", "$m?a[1]?b");
    _check ("(dyn (lookup $m 'f') 1)", "$m?f(1)");
    _check ("(lookup $m 'a')", "$m ? a");
    _check ("(ulookup 'a')", "?a");
    _check ("(ulookup 1)", "?1");
    _check ("(ulookup *)", "?*");
    _check ("(map! $m (ulookup 'x'))", "$m ! ?x");
    _check ("(filter $v[(ulookup 'a')])", "$v[?a]");
    _check ("(lookup $m (fn:true))", "$m?(true())");
  }

  @Test
  public void testFilterExpressions ()
  {
    _check ("(filter $v[1])", "$v[1]");
    _check ("(filter $v[1][2])", "$v[1][2]");
    _check ("(filter (seq 1 2 3)[(general_gt . 1)])", "(1,2,3)[. > 1]");
    _check ("(filter (to 1 5)[(general_gt . 3)])", "(1 to 5)[. > 3]");
    _check ("(filter .[1])", ".[1]");
    _check ("(filter (dyn (filter $f[1]) 2)[3])", "$f[1](2)[3]");
  }

  @Test
  public void testSequenceTypeSyntaxInExpressions ()
  {
    _check ("(instance $v item()*)", "$v instance of item()*");
    _check ("(instance $v node()?)", "$v instance of node()?");
    _check ("(instance $v element(a)+)", "$v instance of element(a)+");
    _check ("(instance $v function(*))", "$v instance of function(*)");
    _check ("(instance $v (function(xs:string,item()*) as xs:boolean))",
            "$v instance of function(xs:string, item()*) as xs:boolean");
    _check ("(instance $v (function() as item()*))", "$v instance of function() as item()*");
    _check ("(instance $v map(*))", "$v instance of map(*)");
    _check ("(instance $v map(xs:string,xs:integer*))", "$v instance of map(xs:string, xs:integer*)");
    _check ("(instance $v array(*))", "$v instance of array(*)");
    _check ("(instance $v array(xs:integer))", "$v instance of array(xs:integer)");
    _check ("(instance $v xs:integer*)", "$v instance of (xs:integer)*");
    _check ("(instance $v document-node(element(a)))", "$v instance of document-node(element(a))");
  }

  @Test
  public void testGoReferenceExpressions ()
  {
    // Expressions from go-xslt's xpath tests must all parse
    final String [] aExprs = { "count(/catalog/book)",
                               "/catalog/book[1]/title",
                               "/catalog/book[@id='b2']/title",
                               "//title",
                               "sum(//price)",
                               "count(//book[@cat='go'])",
                               "string-length('hello')",
                               "substring('hello', 2, 3)",
                               "translate('bar','abc','ABC')",
                               "not(1 = 2)",
                               "(1 + 1) instance of xs:integer",
                               "(1 div 2) instance of xs:decimal",
                               "(1.0e0) instance of xs:double",
                               "/r/a << /r/b",
                               "count((/r/a | /r/b) intersect /r/b)",
                               "count(/r/* except /r/b)",
                               "string-join(for-each(('a','b','c'), upper-case#1), '')",
                               "sum(/r/v/xs:decimal(.))",
                               "string-join(/r/v/upper-case(string(.)), ',')",
                               "count(/r/v/number(.))",
                               "name(parent::*)",
                               "count(ancestor-or-self::*)",
                               "not(ancestor::nope)",
                               "count(distinct-values((1,2,2,3,3,3)))",
                               "subsequence((10,20,30,40), 2, 2) => string-join(',')",
                               "fold-left((1,2,3,4), 0, function($a, $b) { $a + $b })",
                               "string-join(sort((3,1,2)), ',')",
                               "count(array { 1 to 4 } ? *)",
                               "array:get(['p','q'], 2)",
                               "map:size(map { 'a':1, 'b':2 })" };
    for (final String s : aExprs)
      _parse (s);
  }
}
