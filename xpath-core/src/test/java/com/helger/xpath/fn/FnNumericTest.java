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

import static com.helger.xpath.fn.FnTestHelper.assertLex;
import static com.helger.xpath.fn.FnTestHelper.assertTyped;
import static com.helger.xpath.fn.FnTestHelper.assertXPathError;
import static com.helger.xpath.fn.FnTestHelper.eval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import com.helger.xpath.eval.ExprTestHelper;
import com.helger.xpath.value.XdmAtomic;

/**
 * Test class for class {@link FnNumeric}.
 *
 * @author Philip Helger
 */
public final class FnNumericTest
{
  @Test
  public void testAbsFloorCeiling ()
  {
    assertTyped ("integer:5", "abs(xs:byte(-5))");
    assertTyped ("decimal:2.5", "abs(-2.5)");
    assertTyped ("float:2", "abs(xs:float('-2'))");
    assertTyped ("double:0", "abs(-0e0)");
    assertTyped ("", "abs(())");
    assertTyped ("decimal:-3", "floor(-2.5)");
    assertTyped ("decimal:-2", "ceiling(-2.5)");
    assertTyped ("double:-0", "ceiling(-0.5e0)");
    assertTyped ("double:-1", "floor(-0.5e0)");
    assertTyped ("double:3", "floor(xs:untypedAtomic('3.7'))");
    assertXPathError ("XPTY0004", "abs('1')");
  }

  @Test
  public void testRound ()
  {
    assertTyped ("decimal:3", "round(2.5)");
    assertTyped ("decimal:-2", "round(-2.5)");
    assertTyped ("double:-0", "round(-0.4e0)");
    assertTyped ("double:-0", "round(-0.5e0)");
    assertTyped ("decimal:1.13", "round(1.125, 2)");
    assertTyped ("decimal:-1.12", "round(-1.125, 2)");
    assertTyped ("integer:12300", "round(12345, -2)");
    assertTyped ("integer:-100", "round(-150, -2)");
    assertTyped ("integer:12345", "round(12345, 3)");
    assertTyped ("double:12000", "round(12345.6e0, -3)");
    // The double nearest -13.65 is below it
    assertTyped ("double:-13.7", "round(-1.365e1, 1)");
    assertTyped ("double:5.0E101", "round(45e100, -101)");
    assertTyped ("decimal:0", "round(4.5, -1000)");
    assertTyped ("decimal:1.5", "round(1.5, 4294967296)");
    assertTyped ("double:INF", "round(xs:double('INF'))");
    assertTyped ("float:NaN", "round(xs:float('NaN'))");
  }

  @Test
  public void testRoundHalfToEven ()
  {
    assertTyped ("decimal:0", "round-half-to-even(0.5)");
    assertTyped ("decimal:2", "round-half-to-even(1.5)");
    assertTyped ("decimal:2", "round-half-to-even(2.5)");
    assertTyped ("decimal:3567.81", "round-half-to-even(3.567812e+3 cast as xs:decimal, 2)");
    assertTyped ("double:3567.81", "round-half-to-even(3.567812e+3, 2)");
    assertTyped ("double:0", "round-half-to-even(4.7564e-3, 2)");
    assertTyped ("integer:35600", "round-half-to-even(35612.25, -2) cast as xs:integer");
    assertTyped ("integer:200", "round-half-to-even(250, -2)");
    assertTyped ("double:-0", "round-half-to-even(-0.5e0)");
    // 250.0250 has no exact double: the double is above the tie
    assertTyped ("double:250.03", "round-half-to-even(250.0250e0, 2)");
  }

  @Test
  public void testNumber ()
  {
    assertTyped ("double:12", "number(' 12 ')");
    assertTyped ("double:NaN", "number('abc')");
    assertTyped ("double:NaN", "number(())");
    assertTyped ("double:1", "number(true())");
    assertTyped ("double:NaN", "number(xs:anyURI('1'))");
    assertTyped ("double:-2.147483648E9", "number(xs:int('-2147483648'))");
    assertEquals ("double:42",
                  FnTestHelper.typed (eval ("number()", ExprTestHelper.parse ("<a>42</a>").getChildAtIndex (0))));
    assertEquals ("double:3", FnTestHelper.typed (eval ("number()", XdmAtomic.string ("3"))));
    assertXPathError ("XPDY0002", "number()");
    assertXPathError ("XPTY0004", "number((1, 2))");
  }

  @Test
  public void testNumberOfDate ()
  {
    ExprTestHelper.assumeDateTime ();
    assertTyped ("double:NaN", "number(xs:gYear('2000'))");
  }

  @Test
  public void testRandomNumberGenerator ()
  {
    assertLex ("true", "let $r := random-number-generator(42) return $r?number ge 0 and $r?number lt 1");
    assertLex ("true", "random-number-generator(42)?number eq random-number-generator(42)?number");
    assertLex ("true", "random-number-generator()?number eq random-number-generator()?number");
    assertLex ("true", "random-number-generator(1)?number ne random-number-generator(2)?number");
    assertLex ("true", "random-number-generator(1)?next()?number ne random-number-generator(1)?number");
    assertLex ("true",
               "let $p := random-number-generator(7)?permute(1 to 100) return count($p) eq 100 and " +
                       "deep-equal(sort($p), 1 to 100) and not(deep-equal($p, 1 to 100))");
    assertLex ("true",
               "deep-equal(random-number-generator(7)?permute(1 to 10), random-number-generator(7)?permute(1 to 10))");
    assertNotEquals ("",
                     FnTestHelper.lex (eval ("random-number-generator('seed')?next()?next()?number")));
  }

  @Test
  public void testCountSumAvg ()
  {
    assertTyped ("integer:0", "count(())");
    assertTyped ("integer:3", "count((1, 'a', 2.0))");
    assertTyped ("integer:0", "sum(())");
    assertTyped ("", "sum((), ())");
    assertTyped ("string:x", "sum((), 'x')");
    assertTyped ("integer:6", "sum((1, 2, 3))");
    assertTyped ("unsignedShort:5", "sum(xs:unsignedShort(5))");
    assertTyped ("decimal:6.5", "sum((1, 2, 3.5))");
    assertTyped ("double:6", "sum((1, xs:untypedAtomic('2'), 3))");
    assertTyped ("float:3", "sum((1, xs:float('2')))");
    assertTyped ("double:-INF", "sum((-1.7976931348623157E308, -1.7976931348623157E308))");
    assertXPathError ("FORG0006", "sum(('a', 'b'))");
    assertXPathError ("FORG0006", "sum((1, 'b'))");
    assertXPathError ("FORG0001", "sum(xs:untypedAtomic('x'))");
    assertTyped ("decimal:2", "avg((1, 2, 3))");
    assertTyped ("decimal:2.5", "avg((2, 3))");
    assertTyped ("decimal:0.333333333333333333", "avg((0, 0, 1))");
    assertTyped ("double:2", "avg((1e0, 3))");
    assertTyped ("", "avg(())");
    assertTyped ("double:NaN", "avg((1, xs:double('NaN')))");
    assertXPathError ("FORG0006", "avg(('a'))");
  }

  @Test
  public void testMinMax ()
  {
    assertTyped ("integer:3", "max((1, 3, 2))");
    assertTyped ("integer:1", "min((1, 3, 2))");
    assertTyped ("double:3", "max((1, 3, 2e0))");
    assertTyped ("float:3", "max((1, 3, xs:float('2')))");
    assertTyped ("decimal:3.5", "max((1, 3.5, 2))");
    assertTyped ("double:NaN", "max((1, xs:double('NaN'), 2))");
    assertTyped ("float:NaN", "min((1, xs:float('NaN')))");
    assertTyped ("unsignedShort:7", "max((xs:positiveInteger(5), xs:unsignedShort(7)))");
    assertTyped ("double:5", "max((xs:untypedAtomic('5'), 2))");
    assertTyped ("string:b", "max(('a', 'b'))");
    assertTyped ("string:b", "max((xs:anyURI('b'), 'a'))");
    assertTyped ("anyURI:b", "max((xs:anyURI('b'), xs:anyURI('a')))");
    assertTyped ("boolean:true", "max((false(), true()))");
    assertTyped ("", "min(())");
    assertTyped ("string:a", "min(('b', 'a'), 'http://www.w3.org/2005/xpath-functions/collation/codepoint')");
    assertXPathError ("FORG0006", "max((1, 'a'))");
    assertXPathError ("FORG0006", "max(xs:QName('xs:a'))");
    assertXPathError ("FORG0001", "max((xs:untypedAtomic('x'), 'a'))");
    assertXPathError ("FOCH0002", "max(('a', 'b'), 'http://example.org/unknown')");
  }
}
