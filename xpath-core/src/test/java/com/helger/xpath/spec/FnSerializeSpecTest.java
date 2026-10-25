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

import com.helger.xpath.context.EXmlVersion;

/**
 * Port of go-xslt <code>internal/xpath/fn_serialize_spec_test.go</code> (commit 04ccada):
 * fn:serialize (F&amp;O 3.1 §14.7.3) and the output methods it reaches:
 * sequence normalization (Serialization 3.1 §2), the JSON output method (§9)
 * and the adaptive output method (§10), with parameters given as a map (F&amp;O
 * table) or as an output:serialization-parameters element (§3.1).
 *
 * @author Philip Helger
 */
public final class FnSerializeSpecTest
{
  private static final String JSN_SER_DOC = "<r><n xmlns:p=\"http://example.com/p\"/><d><e a=\"1\">t</e><x/></d>" +
                                             "<o:serialization-parameters xmlns:o=\"http://www.w3.org/2010/xslt-xquery-serialization\"><o:method value=\"text\"/></o:serialization-parameters></r>";

  @Test
  public void serializeJSONMethod ()
  {
    run (JSN_SER_DOC,
      // Maps -> objects, arrays -> arrays, booleans -> true/false, the
      // empty sequence -> null; indent=no forbids whitespace around
      // structural tokens (§9.1.4).
      ok ("serialize(map{\"a\":\"x\"}, map{'method':'json'})", "{\"a\":\"x\"}"),
      ok ("serialize([\"a\", true(), false(), ()], map{'method':'json'})", "[\"a\",true,false,null]"),
      ok ("serialize([[], map{}], map{'method':'json'})", "[[],{}]"),
      ok ("serialize(map{\"k\":[map{\"n\":()}]}, map{'method':'json'})", "{\"k\":[{\"n\":null}]}"),
      ok ("serialize((), map{'method':'json'})", "null"),
      ok ("serialize(true(), map{'method':'json'})", "true"),
      // Any other atomic value -> JSON string of fn:string.
      ok ("serialize(xs:date('2015-07-17'), map{'method':'json'})", "\"2015-07-17\""),
      ok ("serialize(xs:untypedAtomic('u'), map{'method':'json'})", "\"u\""),
      ok ("serialize(map{xs:date('2015-07-17'):1e0}, map{'method':'json'}) => parse-json() => map:keys()", "2015-07-17"),
      // JSON escaping: " \b \f \n \r \t \\ \/.
      ok ("serialize('a\"b\\c/d', map{'method':'json'})", "\"a\\\"b\\\\c\\/d\""),
      ok ("serialize(map{'a/b':'é'}, map{'method':'json'})", "{\"a\\/b\":\"é\"}"),
      // Numbers may use any JSON lexical form: check by round trip.
      ok ("parse-json(serialize(1.5, map{'method':'json'})) eq 1.5e0", "true"),
      ok ("parse-json(serialize(-42, map{'method':'json'})) eq -42e0", "true"),
      ok ("parse-json(serialize(1e6, map{'method':'json'})) eq 1e6", "true"),
      ok ("parse-json(serialize(xs:float('0.5'), map{'method':'json'})) eq 0.5e0", "true"),
      ok ("deep-equal(parse-json(serialize([1, map{\"x\":2.25}], map{'method':'json'})), [1e0, map{\"x\":2.25e0}])", "true"),
      // Nodes: serialized by json-node-output-method (default xml, no XML
      // declaration), then written as a JSON string.
      ok ("serialize(/r/d/e, map{'method':'json'})", "\"<e a=\\\"1\\\">t<\\/e>\""),
      ok ("serialize([/r/d/e], map{'method':'json'})", "[\"<e a=\\\"1\\\">t<\\/e>\"]"),
      ok ("serialize(/r/d/e, map{'method':'json', 'json-node-output-method':'text'})", "\"t\""),
      ok ("serialize(/r/d/e, map{'method':'json', 'json-node-output-method':'xml'})", "\"<e a=\\\"1\\\">t<\\/e>\""),
      // allow-duplicate-names=yes lets equal string keys through (§9.1.16).
      ok ("count(tokenize(serialize(map{'2014-10-01':1, xs:date('2014-10-01'):2}, map{'method':'json', 'allow-duplicate-names':true()}), '\"2014-10-01\":'))", "3"),
      // Characters not representable in the encoding are escaped (§9).
      ok ("lower-case(serialize('é', map{'method':'json', 'encoding':'US-ASCII'}))", "\"\\u00e9\""),
      ok ("lower-case(serialize('\uD83D\uDE00', map{'method':'json', 'encoding':'us-ascii'}))", "\"\\ud83d\\ude00\""),
      ok ("lower-case(serialize('éĀ', map{'method':'json', 'encoding':'ISO-8859-1'}))", "\"é\\u0100\""),
      ok ("serialize('é', map{'method':'json', 'encoding':'UTF-8'})", "\"é\""),
      // A character map replaces a character before (instead of) escaping.
      ok ("serialize('a/b', map{'method':'json', 'use-character-maps':map{'/':'/'}})", "\"a/b\""),
      ok ("serialize(map{'k':'xy'}, map{'method':'json', 'use-character-maps':map{'x':'X'}})", "{\"k\":\"Xy\"}"),
      // indent=yes may only add whitespace adjacent to structural tokens.
      ok ("replace(serialize(map{\"a\":[true(),map{\"b\":\"cd\"}]}, map{'method':'json', 'indent':true()}), '\\s', '')", "{\"a\":[true,{\"b\":\"cd\"}]}")
    );
    run (JSN_SER_DOC,
      // NaN / INF have no JSON form.
      err ("serialize(xs:double('INF'), map{'method':'json'})", "SERE0020"),
      err ("serialize([xs:double('NaN')], map{'method':'json'})", "SERE0020"),
      err ("serialize(map{'a':xs:float('-INF')}, map{'method':'json'})", "SERE0020"),
      // Keys with equal string values are an error by default.
      err ("serialize(map{'2014-10-01':1, xs:date('2014-10-01'):2}, map{'method':'json'})", "SERE0022"),
      err ("serialize(map{'1':1, 1:2}, map{'method':'json', 'allow-duplicate-names':false()})", "SERE0022"),
      // An attribute node, serialized with the xml method, fails sequence
      // normalization (§2, SENR0001).
      err ("serialize(/r/d/e/@a, map{'method':'json'})", "SENR0001")
    );
  }

  @Test
  public void serializeJSONMethodErrorCodes ()
  {
    // Serialization 3.1 §9: "A sequence of length greater than one ... will
    // result in a serialization error [err:SERE0023]"; "Any item ... of type
    // not specified in the above list will result in a serialization error
    // [err:SERE0021]" (function items).
    // Skipped in go-xslt at 04ccada ("BUG: the JSON method raises FOJS0006 instead of SERE0023 (multi-item sequence) and SERE0021 (function item)"); passes here.
    run (
      err ("serialize((1,2), map{'method':'json'})", "SERE0023"),
      err ("serialize([(1,2)], map{'method':'json'})", "SERE0023"),
      err ("serialize(abs#1, map{'method':'json'})", "SERE0021")
    );
  }

  @Test
  public void serializeJSONControlEscape ()
  {
    // Serialization 3.1 §9: "any other codepoint in the range 1-31 or 127-159
    // [is replaced] by an escape in the form <code>&#92;uHHHH</code>".
    run (
      ok ("upper-case(serialize(codepoints-to-string((127,128,159)), map{'method':'json'}))", "\"\\U007F\\U0080\\U009F\"")
    );
  }

  @Test
  public void serializeAdaptiveMethod ()
  {
    run (JSN_SER_DOC,
      // §10 rules per item kind.
      ok ("serialize('a\"b', map{'method':'adaptive'})", "\"a\"\"b\""),
      ok ("serialize(xs:untypedAtomic('u'), map{'method':'adaptive'})", "\"u\""),
      ok ("serialize(xs:anyURI('http://x/'), map{'method':'adaptive'})", "\"http://x/\""),
      ok ("serialize(true(), map{'method':'adaptive'})", "true()"),
      ok ("serialize(false(), map{'method':'adaptive'})", "false()"),
      ok ("serialize(42, map{'method':'adaptive'})", "42"),
      ok ("serialize(-1.5, map{'method':'adaptive'})", "-1.5"),
      // xs:double: format-number(?, '0.0##########################e0').
      ok ("serialize(1.5e6, map{'method':'adaptive'})", "1.5e6"),
      ok ("serialize(1e0, map{'method':'adaptive'})", "1.0e0"),
      ok ("serialize(123e0, map{'method':'adaptive'})", "1.23e2"),
      ok ("serialize(-1e0, map{'method':'adaptive'})", "-1.0e0"),
      ok ("serialize(0.1e0, map{'method':'adaptive'})", "1.0e-1"),
      ok ("serialize(xs:double('INF'), map{'method':'adaptive'})", "INF"),
      ok ("serialize(xs:double('-INF'), map{'method':'adaptive'})", "-INF"),
      ok ("serialize(xs:double('NaN'), map{'method':'adaptive'})", "NaN"),
      ok ("serialize(QName('http://example.com/u', 'p:loc'), map{'method':'adaptive'})", "Q{http://example.com/u}loc"),
      ok ("serialize(QName('', 'loc'), map{'method':'adaptive'})", "Q{}loc"),
      // Maps, arrays: members in general sequences, parenthesised unless
      // of length one; separated by commas.
      ok ("serialize(map{'a':1}, map{'method':'adaptive'})", "map{\"a\":1}"),
      ok ("serialize(map{'a':(1,2)}, map{'method':'adaptive'})", "map{\"a\":(1,2)}"),
      ok ("serialize(map{1:true()}, map{'method':'adaptive'})", "map{1:true()}"),
      ok ("serialize(map{}, map{'method':'adaptive'})", "map{}"),
      ok ("serialize([1,(2,3),'x'], map{'method':'adaptive'})", "[1,(2,3),\"x\"]"),
      ok ("serialize([()], map{'method':'adaptive'})", "[()]"),
      ok ("serialize([], map{'method':'adaptive'})", "[]"),
      ok ("serialize([[1],map{'k':[]}], map{'method':'adaptive'})", "[[1],map{\"k\":[]}]"),
      // Function items: name#arity with conventional prefixes, or
      // (anonymous-function)#A (the REC's own examples).
      ok ("serialize(fn:exists#1, map{'method':'adaptive'})", "fn:exists#1"),
      ok ("serialize(Q{http://www.w3.org/2005/xpath-functions}exists#1, map{'method':'adaptive'})", "fn:exists#1"),
      ok ("serialize(function($a) { $a }, map{'method':'adaptive'})", "(anonymous-function)#1"),
      ok ("serialize(math:pi#0, map{'method':'adaptive'})", "math:pi#0"),
      ok ("serialize(map:get#2, map{'method':'adaptive'})", "map:get#2"),
      ok ("serialize(array:size#1, map{'method':'adaptive'})", "array:size#1"),
      ok ("serialize(xs:integer#1, map{'method':'adaptive'})", "xs:integer#1"),
      // Nodes: xml method; attribute / namespace nodes as if on an element.
      ok ("serialize(/r/d/e, map{'method':'adaptive'})", "<e a=\"1\">t</e>"),
      ok ("serialize(/r/d/e/@a, map{'method':'adaptive'})", "a=\"1\""),
      ok ("serialize(/r/d/e/text(), map{'method':'adaptive'})", "t"),
      ok ("serialize(/r/n/namespace::p, map{'method':'adaptive'})", "xmlns:p=\"http://example.com/p\""),
      ok ("serialize(parse-xml('<e xmlns:p=\"http://example.com/p\" p:a=\"1\"/>')/*/@*, map{'method':'adaptive'})", "p:a=\"1\""),
      ok ("serialize(parse-xml('<e xmlns=\"http://example.com/d\"/>')/*/namespace::*[not(name() = 'xml')], map{'method':'adaptive'})", "xmlns=\"http://example.com/d\""),
      // Items are separated by the item-separator, by default a newline.
      ok ("serialize((1,'a',true()), map{'method':'adaptive'})", "1\n\"a\"\ntrue()"),
      ok ("serialize((1,2), map{'method':'adaptive', 'item-separator':' | '})", "1 | 2"),
      ok ("serialize((), map{'method':'adaptive'})", ""),
      // Character maps apply to values in quotation marks and to nodes.
      ok ("serialize('a<', map{'method':'adaptive', 'use-character-maps':map{'<':'[lt]'}})", "\"a[lt]\""),
      ok ("serialize(/r/d/e, map{'method':'adaptive', 'use-character-maps':map{'t':'T'}})", "<e a=\"1\">T</e>"),
      ok ("serialize(map{'a':'b'}, map{'method':'adaptive', 'use-character-maps':map{'b':'B'}})", "map{\"a\":\"B\"}")
    );
  }

  @Test
  public void serializeAdaptiveAtomicForms ()
  {
    // §10: "An instance of xs:integer or xs:decimal is serialized by
    // converting the value to a string using the fn:string function"; "An
    // atomic value of any other type is serialized using the syntax of a
    // constructor function: xs:TYPE("VAL")".
    run (
      ok ("serialize(xs:decimal('5'), map{'method':'adaptive'})", "5"),
      ok ("serialize(xs:date('2015-07-17'), map{'method':'adaptive'})", "xs:date(\"2015-07-17\")"),
      ok ("serialize(xs:decimal('1.50'), map{'method':'adaptive'})", "1.5"),
      ok ("serialize(xs:byte(3), map{'method':'adaptive'})", "3"),
      ok ("serialize(xs:float(1), map{'method':'adaptive'})", "xs:float(\"1\")"),
      ok ("serialize(xs:yearMonthDuration('P1Y2M'), map{'method':'adaptive'})", "xs:duration(\"P1Y2M\")"),
      ok ("serialize(xs:dateTimeStamp('2011-07-28T12:34:56-08:00'), map{'method':'adaptive'})", "xs:dateTime(\"2011-07-28T12:34:56-08:00\")"),
      ok ("serialize(xs:hexBinary('0a'), map{'method':'adaptive'})", "xs:hexBinary(\"0A\")")
    );
  }

  @Test
  public void serializeXMLAndText ()
  {
    run (JSN_SER_DOC,
      // Defaults of the F&O table: method xml, omit-xml-declaration yes.
      ok ("serialize(/r/d/e)", "<e a=\"1\">t</e>"),
      ok ("serialize(/r/d/e, ())", "<e a=\"1\">t</e>"),
      ok ("serialize(/r/d/e, map{})", "<e a=\"1\">t</e>"),
      ok ("serialize(/r/d/e, map{'method':()})", "<e a=\"1\">t</e>"),
      ok ("starts-with(serialize(/r/d/e, map{'omit-xml-declaration':false()}), '<?xml')", "true"),
      ok ("contains(serialize(/r/d/e, map{'omit-xml-declaration':false(), 'standalone':true()}), 'standalone=\"yes\"')", "true"),
      ok ("contains(serialize(/r/d/e, map{'omit-xml-declaration':false(), 'standalone':false()}), 'standalone=\"no\"')", "true"),
      ok ("contains(serialize(/r/d/e, map{'omit-xml-declaration':false(), 'standalone':()}), 'standalone')", "false"),
      ok ("contains(serialize(/r/d/e, map{'omit-xml-declaration':false(), 'version':'1.1'}), 'version=\"1.1\"')", "true"),
      // A boolean parameter accepts xs:untypedAtomic by function conversion.
      ok ("starts-with(serialize(/r/d/e, map{'omit-xml-declaration':xs:untypedAtomic('false')}), '<?xml')", "true"),
      ok ("serialize(/r/d/e, map{'cdata-section-elements':QName('', 'e')})", "<e a=\"1\"><![CDATA[t]]></e>"),
      ok ("serialize(/r/d/e, map{'cdata-section-elements':(QName('', 'z'), QName('', 'e'))})", "<e a=\"1\"><![CDATA[t]]></e>"),
      ok ("serialize(/r/d/e, map{'cdata-section-elements':()})", "<e a=\"1\">t</e>"),
      // Character maps on nodes: text and attribute values.
      ok ("serialize(/r/d/e, map{'use-character-maps':map{'t':'T', '1':'one'}})", "<e a=\"one\">T</e>"),
      // Implementation-defined (QName-keyed) parameters are ignored, even
      // one in no namespace.
      ok ("serialize(/r/d/e, map{QName('http://example.com/x', 'x:indent'):true()})", "<e a=\"1\">t</e>"),
      // Sequence normalization (§2): adjacent atomic values are separated
      // by a single space, nodes are not; an item-separator goes between
      // every pair of items; the empty sequence is a zero-length string.
      ok ("serialize((1, 2, 'a'))", "1 2 a"),
      ok ("serialize((/r/d/e, /r/d/x))", "<e a=\"1\">t</e><x/>"),
      ok ("serialize((1, /r/d/x, 2))", "1<x/>2"),
      ok ("serialize((1, 2), map{'item-separator':'-'})", "1-2"),
      ok ("serialize((/r/d/x, 'a'), map{'item-separator':','})", "<x/>,a"),
      ok ("serialize(())", ""),
      ok ("serialize((), map{'method':'text'})", ""),
      ok ("serialize(xs:date('2015-07-17'))", "2015-07-17"),
      // The text method writes string values only.
      ok ("serialize(/r/d, map{'method':'text'})", "t"),
      ok ("serialize((/r/d/e, 'x'), map{'method':'text', 'item-separator':'|'})", "t|x"),
      // The html method writes a void element without an end tag.
      ok ("serialize(/r/d/x, map{'method':'html'})", "<x></x>"),
      ok ("serialize(parse-xml('<p><br/></p>'), map{'method':'html'})", "<p><br></p>"),
      ok ("contains(serialize(parse-xml('<html><head/></html>'), map{'method':'html'}), 'Content-Type')", "true"),
      ok ("contains(serialize(parse-xml('<html><head/></html>'), map{'method':'html', 'include-content-type':false()}), 'Content-Type')", "false"),
      ok ("contains(serialize(parse-xml('<html><head/></html>'), map{'method':'html', 'html-version':5}), '<head>')", "true")
    );
    run (JSN_SER_DOC,
      // SENR0001: attribute, namespace and function (incl. map) items.
      err ("serialize(/r/d/e/@a)", "SENR0001"),
      err ("serialize(/r/n/namespace::p)", "SENR0001"),
      err ("serialize(map{})", "SENR0001"),
      err ("serialize(abs#1)", "SENR0001"),
      err ("serialize((1, map{}), map{'method':'text'})", "SENR0001"),
      // Map-form value types (option parameter conventions): XPTY0004.
      err ("serialize(1, map{'indent':'yes'})", "XPTY0004"),
      err ("serialize(1, map{'indent':1})", "XPTY0004"),
      err ("serialize(1, map{'indent':(true(), false())})", "XPTY0004"),
      err ("serialize(1, map{'omit-xml-declaration':xs:untypedAtomic('perhaps')})", "FORG0001"),
      err ("serialize(1, map{'standalone':'maybe'})", "XPTY0004"),
      err ("serialize(1, map{'method':('xml', 'text')})", "XPTY0004"),
      err ("serialize(1, map{'cdata-section-elements':'e'})", "XPTY0004"),
      err ("serialize(1, map{'suppress-indentation':('e', 'f')})", "XPTY0004"),
      err ("serialize(1, map{'html-version':'5'})", "XPTY0004"),
      err ("serialize(1, map{'use-character-maps':'x'})", "XPTY0004"),
      err ("serialize(1, map{'use-character-maps':map{1:'x'}})", "XPTY0004"),
      err ("serialize(1, map{'use-character-maps':map{'a':1}})", "XPTY0004"),
      err ("serialize(1, map{'use-character-maps':map{'a':('x','y')}})", "XPTY0004"),
      // The params element must be output:serialization-parameters.
      err ("serialize(1, /r/d/e)", "XPTY0004")
    );
  }

  @Test
  public void serializeParamTypeErrors ()
  {
    // F&O 3.1 §14.7.3: "A type error [err:XPTY0004] occurs if the second
    // argument is present and does not match either of the types
    // element(output:serialization-parameters)? or map(*)"; F&O's own
    // example of SEPM0016 is a use-character-maps key whose length is not one.
    // Skipped in go-xslt at 04ccada ("BUG: a string or a two-element $params is silently accepted, and a multi-character use-character-maps key raises SEPM0018 instead of SEPM0016"); passes here.
    run (JSN_SER_DOC,
      err ("serialize(1, 'x')", "XPTY0004"),
      err ("serialize(1, (/r/*:serialization-parameters, /r/*:serialization-parameters))", "XPTY0004"),
      err ("serialize(1, map{'use-character-maps':map{'ab':'x'}})", "SEPM0016")
    );
  }

  @Test
  public void serializeArrayFlattening ()
  {
    // Serialization 3.1 §2: "Each item in the sequence that is an array is
    // flattened by calling the function array:flatten() before being
    // copied."
    run (
      ok ("serialize([1, [2, 3]])", "1 2 3"),
      ok ("serialize(['a', 'b'], map{'method':'text'})", "a b")
    );
  }

  @Test
  public void serializeParamElement ()
  {
    final String sPre = "<r><d><e a=\"1\">t</e><x/></d><o:serialization-parameters xmlns:o=\"http://www.w3.org/2010/xslt-xquery-serialization\">";
    final String sPost = "</o:serialization-parameters></r>";
    final String [] [] aCases = {
      { "<o:method value=\"text\"/>", "serialize(/r/d/e, /r/*[2])", "t" },
      { "<o:method value=\" text \"/>", "serialize(/r/d/e, /r/*[2])", "t" },
      { "", "serialize(/r/d/e, /r/*[2])", "<e a=\"1\">t</e>" },
      { "<o:omit-xml-declaration value=\"no\"/>", "starts-with(serialize(/r/d/e, /r/*[2]), '<?xml')", "true" },
      { "<o:omit-xml-declaration value=\"no\"/><o:standalone value=\" yes \"/>", "contains(serialize(/r/d/e, /r/*[2]), 'standalone=\"yes\"')", "true" },
      { "<o:omit-xml-declaration value=\"no\"/><o:standalone value=\"omit\"/>", "contains(serialize(/r/d/e, /r/*[2]), 'standalone')", "false" },
      // The item-separator's whitespace is significant.
      { "<o:item-separator value=\" | \"/>", "serialize((1, 2), /r/*[2])", "1 | 2" },
      { "<o:cdata-section-elements value=\"e\"/>", "serialize(/r/d/e, /r/*[2])", "<e a=\"1\"><![CDATA[t]]></e>" },
      { "<o:cdata-section-elements value=\" z  e \"/>", "serialize(/r/d/e, /r/*[2])", "<e a=\"1\"><![CDATA[t]]></e>" },
      { "<o:cdata-section-elements value=\"Q{}e\"/>", "serialize(/r/d/x, /r/*[2])", "<x/>" },
      { "<o:method value=\"json\"/>", "serialize(map{'a':1e0}, /r/*[2]) => parse-json() => map:get('a')", "1" },
      { "<o:method value=\"json\"/><o:allow-duplicate-names value=\"yes\"/>", "count(tokenize(serialize(map{'1':1, 1:2}, /r/*[2]), '\"1\":'))", "3" },
      { "<o:method value=\"json\"/><o:json-node-output-method value=\"text\"/>", "serialize(/r/d/e, /r/*[2])", "\"t\"" },
      { "<o:method value=\"adaptive\"/><o:item-separator value=\";\"/>", "serialize((1, 'x'), /r/*[2])", "1;\"x\"" },
      { "<o:method value=\"html\"/><o:include-content-type value=\"no\"/>", "contains(serialize(parse-xml('<html><head/></html>'), /r/*[2]), 'Content-Type')", "false" },
      // Foreign-namespace parameters are extension parameters: ignored.
      { "<f:indent xmlns:f=\"http://example.com/f\" value=\"yes\"/>", "serialize(/r/d/e, /r/*[2])", "<e a=\"1\">t</e>" },
      // Comments / text between parameters are irrelevant.
      { "<!--c--> <o:method value=\"text\"/>", "serialize(/r/d/e, /r/*[2])", "t" },
      // use-character-maps.
      { "<o:use-character-maps><o:character-map character=\"t\" map-string=\"T\"/><o:character-map character=\"1\" map-string=\"one\"/></o:use-character-maps>",
        "serialize(/r/d/e, /r/*[2])",
        "<e a=\"one\">T</e>" } };
    for (final String [] aCase : aCases)
      run (sPre + aCase[0] + sPost, ok (aCase[1], aCase[2]));
    final String [] [] aErrs = {
      // §3.1: the instance must be valid against the parameter schema
      // (SEPM0017); a parameter may occur once (SEPM0019); a character may
      // be mapped once (SEPM0018).
      { "<o:method value=\"text\"/><o:method value=\"xml\"/>", "SEPM0019" },
      { "<method value=\"text\"/>", "SEPM0017" },
      { "<o:no-such-param value=\"1\"/>", "SEPM0017" },
      { "<o:method value=\"text\" extra=\"1\"/>", "SEPM0017" },
      { "<o:cdata-section-elements value=\"un:e\"/>", "SEPM0017" },
      { "<o:use-character-maps a=\"1\"/>", "SEPM0017" },
      { "<o:use-character-maps><o:other character=\"a\" map-string=\"b\"/></o:use-character-maps>", "SEPM0017" },
      { "<o:use-character-maps><o:character-map character=\"a\"/></o:use-character-maps>", "SEPM0017" },
      { "<o:use-character-maps><o:character-map map-string=\"b\"/></o:use-character-maps>", "SEPM0017" },
      { "<o:use-character-maps><o:character-map character=\"a\" map-string=\"b\" x=\"y\"/></o:use-character-maps>", "SEPM0017" },
      { "<o:use-character-maps><o:character-map character=\"$\" map-string=\"£\"/><o:character-map character=\"$\" map-string=\"€\"/></o:use-character-maps>", "SEPM0018" },
    };
    for (final String [] aErr : aErrs)
      run (sPre + aErr[0] + sPost, err ("serialize(/r/d/e, /r/*[2])", aErr[1]));
    // An attribute on the wrapper element itself.
    run ("<r><o:serialization-parameters xmlns:o=\"http://www.w3.org/2010/xslt-xquery-serialization\" a=\"1\"/></r>",
         err ("serialize(1, /r/*)", "SEPM0017"));
  }

  /**
   * The control-character case of {@link #serializeJSONMethod()}: under XML 1.0
   * (the default here) <code>codepoints-to-string(8)</code> is FOCH0001, so it
   * runs with XML 1.1 characters.
   */
  @Test
  public void serializeJSONMethodControlCharacters ()
  {
    run (env ().staticSetup (aSC -> aSC.setXmlVersion (EXmlVersion.V11)),
      ok ("serialize(codepoints-to-string((8,12,10,13,9)), map{'method':'json'})", "\"\\b\\f\\n\\r\\t\"")
    );
  }
}
