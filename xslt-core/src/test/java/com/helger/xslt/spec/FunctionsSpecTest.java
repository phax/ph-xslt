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
package com.helger.xslt.spec;

import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xslt/functions_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class FunctionsSpecTest
{
  /**
   * XSLT 3.0 §20.2 keys: fn:key.
   */
  @Test
  public void xsltFuncKey ()
  {
    final String sSrc = "<r><p id=\"a\" g=\"x\">1</p><p id=\"b\" g=\"y\">2</p><p id=\"c\" g=\"x\">3</p><s><p id=\"d\" g=\"x\">4</p></s></r>";
    specRunAll (
      ok ("single value", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k','x')\"/></xsl:template>", sSrc, "1 3 4"),
      ok ("sequence of values, document order, no duplicates", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k',('y','x','x'))\"/></xsl:template>", sSrc, "1 2 3 4"),
      ok ("node-valued search argument uses string values", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k', //q)\"/></xsl:template>", "<r><q>y</q><q>x</q>" + sSrc.substring (3), "1 2 3 4"),
      ok ("third argument restricts to subtree", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k','x', //s)\"/></xsl:template>", sSrc, "4"),
      ok ("third argument with several values", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k',('x','y'), //s)\"/></xsl:template>", sSrc, "4"),
      ok ("multiple use values per node", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"tokenize(@t)\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k','b')\"/></xsl:template>", "<r><p t=\"a b\">1</p><p t=\"b\">2</p></r>", "1 2"),
      ok ("typed numeric search value compares by eq", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"xs:decimal(@n)\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k', 1)\"/></xsl:template>", "<r><p n=\"1.0\">a</p><p n=\"2\">b</p></r>", "a"),
      ok ("composite key", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@a, @b\" composite=\"yes\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k', ('2','1'))\"/></xsl:template>", "<r><p a=\"1\" b=\"2\">x</p><p a=\"2\" b=\"1\">y</p></r>", "y"),
      ok ("prefixed key name resolved by namespace URI", "\n" +
        "<xsl:key name=\"f:k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\" xmlns:g=\"urn:f\"><xsl:value-of select=\"key('g:k','y')\"/></xsl:template>", sSrc, "2"),
      ok ("no match is empty", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(key('k','zz'))\"/></xsl:template>", sSrc, "0"),
      // §20.2.2: XTDE1260 if the name does not match an xsl:key declaration.
      err ("undeclared key", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('nokey','x')\"/></xsl:template>", sSrc, "XTDE1260"),
      err ("unbound prefix in key name", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key(string-join(('zz','k'),':'),'x')\"/></xsl:template>", sSrc, "XTDE1260"),
      // §20.2.2: XTDE1270 when the context item is absent (inside xsl:function).
      err ("no context item", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:function name=\"f:k\"><xsl:sequence select=\"key('k','x')\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:k()\"/></xsl:template>", sSrc, "XTDE1270"),
      // ... and when the tree searched is not rooted at a document node.
      err ("tree not rooted in a document", "\n" +
        "<xsl:key name=\"k\" match=\"p\" use=\"@g\"/>\n" +
        "<xsl:variable name=\"e\" as=\"element()\"><p g=\"x\"/></xsl:variable>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(key('k','x',$e))\"/></xsl:template>", sSrc, "XTDE1270"));
  }

  /**
   * XSLT 3.0 §20.4 generate-id, §20.4.1 current, §17.1 regex-group.
   */
  @Test
  public void xsltFuncContextFunctions ()
  {
    final String sSrc = "<r><a>1</a><a>2</a><b>1</b></r>";
    specRunAll (
      ok ("generate-id stable and distinct", "<xsl:template match=\"/r\"><xsl:value-of select=\"generate-id(a[1]) = generate-id(a[1]), generate-id(a[1]) = generate-id(a[2]), generate-id() = generate-id(.)\"/></xsl:template>", sSrc, "true false true"),
      // F&O 3.1 §14.5.4: an ASCII letter followed by letters/digits.
      ok ("generate-id is an NCName of letters and digits", "<xsl:template match=\"/r\"><xsl:value-of select=\"matches(generate-id(b), '^[A-Za-z][A-Za-z0-9]*$')\"/></xsl:template>", sSrc, "true"),
      ok ("generate-id of empty sequence", "<xsl:template match=\"/r\"><xsl:value-of select=\"generate-id(())\"/></xsl:template>", sSrc, ""),
      ok ("current() inside a predicate", "<xsl:template match=\"/r\"><xsl:for-each select=\"b\"><xsl:value-of select=\"count(../a[. = current()])\"/></xsl:for-each></xsl:template>", sSrc, "1"),
      ok ("current() over atomic items", "<xsl:template match=\"/\"><xsl:for-each select=\"2,3\"><xsl:value-of select=\"('a','b','c')[current()]\"/><xsl:if test=\"position() ne last()\"><xsl:text> </xsl:text></xsl:if></xsl:for-each></xsl:template>", sSrc, "b c"),
      // §20.4.1: XTDE1360 when the current item is absent (function body).
      err ("current() with absent focus", "<xsl:function name=\"f:c\"><xsl:sequence select=\"current()\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:c()\"/></xsl:template>", sSrc, "XTDE1360"),
      // §17.2: regex-group with no current captured substrings is "".
      ok ("regex-group outside analyze-string", "<xsl:template match=\"/\">[<xsl:value-of select=\"regex-group(1)\"/>]</xsl:template>", sSrc, "[]"),
      ok ("regex-group inside matching-substring", "<xsl:template match=\"/\"><xsl:analyze-string select=\"'d=2026-10'\" regex=\"(\\d+)-(\\d+)\"><xsl:matching-substring><xsl:value-of select=\"regex-group(1), regex-group(2)\" separator=\"|\"/><xsl:value-of select=\"regex-group(9)\"/></xsl:matching-substring></xsl:analyze-string></xsl:template>", sSrc, "2026|10"));
  }

  /**
   * XSLT 3.0 §20.5 fn:copy-of / fn:snapshot (F&O 3.1 §16.?? via XSLT).
   */
  @Test
  public void xsltFuncCopyOfSnapshot ()
  {
    final String sSrc = "<r><a x=\"1\"><b>t</b></a></r>";
    specRunAll (
      ok ("copy-of makes new identity, deep-equal", "<xsl:template match=\"/r\"><xsl:value-of select=\"copy-of(a) is a, deep-equal(copy-of(a), a)\"/></xsl:template>", sSrc, "false true"),
      ok ("copy-of of a descendant has no parent", "<xsl:template match=\"/r\"><xsl:value-of select=\"count(copy-of(a/b)/..)\"/></xsl:template>", sSrc, "0"),
      ok ("copy-of with no argument copies the context item", "<xsl:template match=\"/r\"><xsl:value-of select=\"a/b/copy-of()\"/></xsl:template>", sSrc, "t"),
      ok ("copy-of passes atomic values through", "<xsl:template match=\"/r\"><xsl:value-of select=\"copy-of((1,'x'))\"/></xsl:template>", sSrc, "1 x"),
      // fn:snapshot keeps the ancestors (shallow copies) of the node.
      ok ("snapshot keeps ancestors", "<xsl:template match=\"/r\"><xsl:value-of select=\"name(snapshot(a/b)/..), name(snapshot(a/b)/../..), snapshot(a/b)/../@x\"/></xsl:template>", sSrc, "a r 1"),
      ok ("snapshot without argument", "<xsl:template match=\"/r\"><xsl:for-each select=\"a/b\"><xsl:value-of select=\"name(snapshot()/../..)\"/></xsl:for-each></xsl:template>", sSrc, "r"),
      err ("copy-of with absent focus", "<xsl:function name=\"f:c\"><xsl:sequence select=\"copy-of()\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:c()\"/></xsl:template>", sSrc, "XPDY0002"));
  }

  /**
   * XSLT 3.0 §20.3.? system-property / available-system-properties.
   */
  @Test
  public void xsltFuncSystemProperty ()
  {
    specRunAll (
      ok ("version and xpath-version", "<xsl:template match=\"/\"><xsl:value-of select=\"system-property('xsl:version'), system-property('xsl:xpath-version')\"/></xsl:template>", null, "3.0 3.1"),
      ok ("vendor properties are non-empty", "<xsl:template match=\"/\"><xsl:value-of select=\"for $p in ('vendor','vendor-url','product-name','product-version') return system-property('xsl:' || $p) ne ''\"/></xsl:template>", null, "true true true true"),
      // §20.3.? : yes/no valued properties.
      ok ("yes/no properties", "<xsl:template match=\"/\"><xsl:value-of select=\"every $p in ('is-schema-aware','supports-serialization','supports-backwards-compatibility','supports-namespace-axis','supports-streaming','supports-dynamic-evaluation','supports-higher-order-functions') satisfies system-property('xsl:' || $p) = ('yes','no')\"/></xsl:template>", null, "true"),
      ok ("dynamic evaluation and HOF are supported", "<xsl:template match=\"/\"><xsl:value-of select=\"system-property('xsl:supports-dynamic-evaluation'), system-property('xsl:supports-higher-order-functions'), system-property('xsl:supports-serialization')\"/></xsl:template>", null, "yes yes yes"),
      ok ("xsd-version", "<xsl:template match=\"/\"><xsl:value-of select=\"system-property('xsl:xsd-version') = ('1.0','1.1')\"/></xsl:template>", null, "true"),
      ok ("Q{} name form", "<xsl:template match=\"/\"><xsl:value-of select=\"system-property('Q{http://www.w3.org/1999/XSL/Transform}version')\"/></xsl:template>", null, "3.0"),
      // An unknown property, or one outside the XSLT namespace, is "".
      ok ("unknown properties", "<xsl:template match=\"/\">[<xsl:value-of select=\"system-property('xsl:no-such')\"/>][<xsl:value-of select=\"system-property('f:version')\"/>]</xsl:template>", null, "[][]"),
      // XTDE1390: not a valid QName / unbound prefix.
      err ("invalid QName", "<xsl:template match=\"/\"><xsl:value-of select=\"system-property(concat('1', 'x'))\"/></xsl:template>", null, "XTDE1390"),
      err ("unbound prefix", "<xsl:template match=\"/\"><xsl:value-of select=\"system-property(concat('zz', ':version'))\"/></xsl:template>", null, "XTDE1390"),
      ok ("available-system-properties lists version and every value is non-empty", "<xsl:template match=\"/\"><xsl:variable name=\"p\" select=\"available-system-properties()\"/><xsl:value-of select=\"QName('http://www.w3.org/1999/XSL/Transform','version') = $p, every $q in $p satisfies system-property('Q{' || namespace-uri-from-QName($q) || '}' || local-name-from-QName($q)) ne ''\"/></xsl:template>", null, "true true"));
  }

  /**
   * XSLT 3.0 §20.3.1 element-available, §20.3.2 function-available, §20.3.3 type-available.
   */
  @Test
  public void xsltFuncAvailability ()
  {
    specRunAll (
      ok ("element-available", "<xsl:template match=\"/\"><xsl:value-of select=\"element-available('xsl:value-of'), element-available('xsl:for-each-group'), element-available('Q{http://www.w3.org/1999/XSL/Transform}iterate'), element-available('xsl:no-such-instruction'), element-available('f:value-of')\"/></xsl:template>", null, "true true true false false"),
      err ("element-available invalid QName", "<xsl:template match=\"/\"><xsl:value-of select=\"element-available(concat('a b',''))\"/></xsl:template>", null, "XTDE1440"),
      ok ("function-available standard", "<xsl:template match=\"/\"><xsl:value-of select=\"function-available('concat'), function-available('fn:substring', 3), function-available('Q{http://www.w3.org/2005/xpath-functions}abs', 1), function-available('abs', 2), function-available('math:pi', 0), function-available('no-such-function')\"/></xsl:template>", null, "true true true false true false"),
      ok ("function-available XSLT functions", "<xsl:template match=\"/\"><xsl:value-of select=\"function-available('key', 2), function-available('document'), function-available('key', 5)\"/></xsl:template>", null, "true true false"),
      ok ("function-available constructor functions", "<xsl:template match=\"/\"><xsl:value-of select=\"function-available('xs:integer', 1), function-available('xs:anyAtomicType', 1)\"/></xsl:template>", null, "true false"),
      ok ("function-available user function by arity", "<xsl:function name=\"f:u\"><xsl:param name=\"a\"/><xsl:sequence select=\"$a\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"function-available('f:u'), function-available('f:u', 1), function-available('f:u', 2)\"/></xsl:template>", null, "true true false"),
      err ("function-available invalid QName", "<xsl:template match=\"/\"><xsl:value-of select=\"function-available(concat(':x',''))\"/></xsl:template>", null, "XTDE1400"),
      ok ("type-available", "<xsl:template match=\"/\"><xsl:value-of select=\"type-available('xs:integer'), type-available('xs:untypedAtomic'), type-available('xs:anyType'), type-available('xs:no-such-type')\"/></xsl:template>", null, "true true true false"),
      // go-xslt expects XTDE1450 (an extension instruction code); §24.1.4 says
      // "[ERR XTDE1428] It is a dynamic error if the argument does not
      // evaluate to a string that is a valid EQName".
      err ("type-available invalid QName", "<xsl:template match=\"/\"><xsl:value-of select=\"type-available(concat('1',''))\"/></xsl:template>", null, "XTDE1428"));
  }

  /**
   * XSLT 3.0 §20.? unparsed-entity-uri / unparsed-entity-public-id.
   */
  @Test
  public void xsltFuncUnparsedEntity ()
  {
    final String sSrc = "<?xml version=\"1.0\"?>\n" +
        "<!DOCTYPE r [\n" +
        "<!NOTATION gif PUBLIC \"-//GIF//EN\" \"image/gif\">\n" +
        "<!ENTITY pic PUBLIC \"-//PIC//EN\" \"http://example.com/pic.gif\" NDATA gif>\n" +
        "<!ENTITY sys SYSTEM \"http://example.com/sys.gif\" NDATA gif>\n" +
        "]>\n" +
        "<r/>";
    specRunAll (
      ok ("uri and public id", "<xsl:template match=\"/\"><xsl:value-of select=\"unparsed-entity-uri('pic'), unparsed-entity-public-id('pic'), unparsed-entity-uri('sys'), unparsed-entity-public-id('sys')\" separator=\"|\"/></xsl:template>", sSrc, "http://example.com/pic.gif|-//PIC//EN|http://example.com/sys.gif|"),
      ok ("uri is xs:anyURI", "<xsl:template match=\"/\"><xsl:value-of select=\"unparsed-entity-uri('pic') instance of xs:anyURI\"/></xsl:template>", sSrc, "true"),
      ok ("unknown entity gives zero-length strings", "<xsl:template match=\"/\">[<xsl:value-of select=\"unparsed-entity-uri('none')\"/>][<xsl:value-of select=\"unparsed-entity-public-id('none')\"/>]</xsl:template>", sSrc, "[][]"),
      ok ("second argument selects the document", "<xsl:template match=\"/\"><xsl:value-of select=\"unparsed-entity-uri('pic', /)\"/></xsl:template>", sSrc, "http://example.com/pic.gif"),
      // XTDE1370: no context node.
      err ("absent focus", "<xsl:function name=\"f:u\"><xsl:sequence select=\"unparsed-entity-uri('pic')\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:u()\"/></xsl:template>", sSrc, "XTDE1370"),
      err ("tree not rooted in a document", "<xsl:variable name=\"e\" as=\"element()\"><e/></xsl:variable>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"unparsed-entity-uri('pic', $e)\"/></xsl:template>", sSrc, "XTDE1370"));
  }

}
