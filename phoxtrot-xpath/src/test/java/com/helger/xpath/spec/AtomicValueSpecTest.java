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

import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Specification tests of atomic values: canonical lexical forms, map keys, conversions and document order, ported from go-xslt's <code>atomic_value_test.go</code>.
 *
 * @author Philip Helger
 */
public final class AtomicValueSpecTest
{


  /**
   * Checks the canonical lexical forms of F&amp;O 3.1
   * §19.1.2.1 (casting numerics to xs:string): xs:double/xs:float use decimal
   * notation for 1e-6 &lt;= |x| &lt; 1e6 and otherwise a mantissa with a decimal
   * point and a bare exponent; xs:decimal has no exponent and no superfluous
   * trailing zeros.
   */
  @Test
  public void tyCanonicalLexical ()
  {
    run (env (),
         ok ("string(xs:double('1e6'))", "1.0E6"),
         ok ("string(xs:double('1.5e-7'))", "1.5E-7"),
         ok ("string(xs:double('-2.5e10'))", "-2.5E10"),
         ok ("string(xs:double('123456.5'))", "123456.5"),
         ok ("string(xs:float('1e7'))", "1.0E7"),
         ok ("string(xs:double('-0'))", "-0"),
         ok ("string(xs:double('0'))", "0"),
         ok ("string(xs:double('INF'))", "INF"),
         ok ("string(xs:double('-INF'))", "-INF"),
         ok ("string(xs:decimal('1.50'))", "1.5"),
         ok ("string(xs:decimal('-0.000000000000000000000000019999'))", "-0.000000000000000000000000019999"),
         ok ("string(xs:decimal('100'))", "100"),
         ok ("string(xs:decimal('0.125'))", "0.125")
    );
  }

  /**
   * Checks op:same-key (F&amp;O 3.1 §17.1.1), which decides
   * whether two atomic values are the same map key.
   */
  @Test
  public void tyMapSameKey ()
  {
    run (env (),
    // Numerics compare in the exact value space; NaN is the same key as NaN.
         ok ("map{xs:double('NaN'): 1}(xs:float('NaN'))", "1"),
         ok ("map{xs:double('INF'): 'p', xs:double('-INF'): 'n'}(xs:float('-INF'))", "n"),
         ok ("map{xs:double('INF'): 'p', xs:double('-INF'): 'n'}(xs:float('INF'))", "p"),
         ok ("map{1: 'a'}(1.0)", "a"),
         ok ("map{1: 'a'}(xs:double(1))", "a"),
         ok ("map{1.5: 'a'}(xs:float(1.5))", "a"),
         ok ("count(map{xs:double('1.1'): 'd'}(1.1))", "0"),
         err ("map:size(map{1: 'a', 1.0: 'b'})", "XQDY0137"), // duplicate keys in a constructor (XPath 3.1 §3.11.1.1)
    // Booleans are not numbers.
         ok ("map{true(): 'y'}(true())", "y"),
         ok ("map{false(): 'n'}(false())", "n"),
         ok ("count(map{true(): 'y'}(1))", "0"),
    // QNames compare by expanded name, not prefix.
         ok ("map{QName('urn:a', 'p:x'): 1}(QName('urn:a', 'q:x'))", "1"),
         ok ("count(map{QName('urn:a', 'x'): 1}(QName('urn:b', 'x')))", "0"),
         ok ("count(map{QName('urn:a', 'x'): 1}('x'))", "0"),
    // xs:string, xs:anyURI and xs:untypedAtomic compare by codepoints.
         ok ("map{'a': 1}(xs:anyURI('a'))", "1"),
         ok ("map{'a': 1}(xs:untypedAtomic('a'))", "1"),
         ok ("map{'a': 1}(xs:token('a'))", "1"),
         ok ("count(map{'1': 1}(1))", "0"),
    // Durations by value, across subtypes.
         ok ("map{xs:yearMonthDuration('P1Y'): 1}(xs:duration('P12M'))", "1"),
         ok ("map{xs:dayTimeDuration('PT60S'): 1}(xs:duration('PT1M'))", "1"),
         ok ("count(map{xs:dayTimeDuration('PT1S'): 1}(xs:dayTimeDuration('PT2S')))", "0"),
    // Date/time values: same instant and same timezone presence.
         ok ("map{xs:time('17:00:00Z'): 1}(xs:time('12:00:00-05:00'))", "1"),
         ok ("map{xs:date('2000-01-01'): 1}(xs:date('2000-01-01'))", "1"),
         ok ("count(map{xs:date('2000-01-01'): 1}(xs:date('2000-01-01Z')))", "0"),
         ok ("count(map{xs:date('2000-01-01'): 1}(xs:dateTime('2000-01-01T00:00:00')))", "0"),
         ok ("map{xs:dateTimeStamp('2000-01-01T00:00:00Z'): 1}(xs:dateTime('2000-01-01T00:00:00Z'))", "1"),
    // Binary values: same type and same octets.
         ok ("map{xs:hexBinary('0F'): 1}(xs:hexBinary('0f'))", "1"),
         ok ("count(map{xs:hexBinary('0F'): 1}(xs:base64Binary('Dw==')))", "0"),
         ok ("map{xs:base64Binary('Dw=='): 1}(xs:base64Binary('Dw=='))", "1"),
    // map:put with a same key replaces the entry, key included
    // (F&O 3.1 §17.1.6).
         ok ("map:keys(map:put(map{3: 'a'}, xs:float(3), 'b')) instance of xs:float", "true"),
         ok ("map:put(map{3: 'a'}, xs:float(3), 'b')(3)", "b"),
         ok ("map:size(map:put(map{3: 'a'}, 3.0, 'b'))", "1")
    );

    // go-xslt also checks its Go map API (Put, Get, Contains, Copy) here; no counterpart.
  }

  /**
   * Checks the effective boolean value (XPath 3.1 §2.4.3, FORG0006 for values
   * that have none) and fn:number. go-xslt tests its Go conversion helpers
   * (EffectiveBoolErr, ToBool, ToNumber, ToString, ToNodeSet) directly; these
   * are the cases that have an XPath 3.1 counterpart.
   */
  @Test
  public void tyConversions ()
  {
    run (env ("<r><a>x</a><b> 4.5 </b></r>"),
         ok ("boolean(())", "false"),
         ok ("boolean((/r/a, /r/b))", "true"),
         ok ("boolean((/r/a, 1))", "true"),
         err ("boolean((1, 2))", "FORG0006"),
         ok ("boolean(true())", "true"),
         ok ("boolean('')", "false"),
         ok ("boolean('x')", "true"),
         ok ("boolean(xs:double('NaN'))", "false"),
         ok ("boolean(2e0)", "true"),
         ok ("boolean(false())", "false"),
         ok ("boolean(xs:untypedAtomic('u'))", "true"),
         ok ("boolean(xs:anyURI(''))", "false"),
         ok ("boolean(0)", "false"),
         ok ("boolean(0.5)", "true"),
         err ("boolean(xs:date('2000-01-01'))", "FORG0006"),
         err ("boolean(map{})", "FORG0006"),
         ok ("number(1.5e0)", "1.5"),
         ok ("number(true())", "1"),
         ok ("number(false())", "0"),
         ok ("number(' 3.5 ')", "3.5"),
         ok ("number(/r/b)", "4.5"),
         ok ("number(4)", "4"),
         ok ("number(0.25)", "0.25"),
         ok ("number('7')", "7"),
         ok ("number('')", "NaN"),
         ok ("number('abc')", "NaN"),
         ok ("number(/r/a)", "NaN"),
         ok ("number(())", "NaN"));
  }

  /**
   * Checks document order (duplicates removed) on a tree built in memory,
   * whose nodes carry no order numbers: XDM 3.1 §2.4 puts a node's namespace
   * and attribute nodes after it and before its children, and ancestors
   * before descendants.
   */
  @Test
  public void tyDocumentOrderUnnumbered ()
  {
    final IXdmNode aRoot = IXdmNode.createElement (new XdmQName ("root"));
    final IXdmNode aA = IXdmNode.createElement (new XdmQName ("a"));
    aRoot.appendChild (aA);
    aRoot.appendChild (IXdmNode.createElement (new XdmQName ("b")));
    aRoot.addAttribute (IXdmNode.createAttribute (new XdmQName ("x"), "1"));
    final IXdmNode aC = IXdmNode.createElement (new XdmQName ("c"));
    aC.appendChild (IXdmNode.createText ("t"));
    aA.appendChild (aC);
    run (env ().contextItem (aRoot),
         ok ("string-join((b, a/c, @x, a, ., a/c, b) / name(), ',')", "b,c,x,a,root,c,b"),
         ok ("string-join((b | a/c | @x | a | . | a/c | b) ! name(), ',')", "root,x,a,c,b"),
         ok ("string((b | a/c)[1])", "t"));
  }
}
