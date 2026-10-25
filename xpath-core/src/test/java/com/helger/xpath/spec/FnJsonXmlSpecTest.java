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

import static org.junit.Assert.assertTrue;

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.eval;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;
import static com.helger.xpath.spec.SpecTestHelper.str;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Port of go-xslt <code>internal/xpath/fn_json_xml_spec_test.go</code> (commit 04ccada):
 * fn:json-to-xml and fn:xml-to-json (F&amp;O 3.1 §17.4.2, §17.5.3, §17.5.4).
 * Expected trees are compared with deep-equal against the context document,
 * which holds the REC's expected XML (prefixes are immaterial, §17.4.2);
 * expected JSON strings follow the escaping rules of §17.5.4.
 *
 * @author Philip Helger
 */
public final class FnJsonXmlSpecTest
{
  private static final String JSN_FN = "xmlns=\"http://www.w3.org/2005/xpath-functions\"";

  @Test
  public void jsonToXMLSpecExamples ()
  {
    final String [] [] aCases = {
      // §17.5.3 examples.
      { "json-to-xml('{\"x\": 1, \"y\": [3,4,5]}')", "<map " + JSN_FN + "><number key=\"x\">1</number><array key=\"y\"><number>3</number><number>4</number><number>5</number></array></map>" },
      { "json-to-xml('\"abcd\"', map{'liberal': false()})", "<string " + JSN_FN + ">abcd</string>" },
      { "json-to-xml('{\"x\": \"\\\\\", \"y\": \"%\"}')", "<map " + JSN_FN + "><string key=\"x\">\\</string><string key=\"y\">%</string></map>" },
      { "json-to-xml('{\"x\": \"\\\\\", \"y\": \"%\"}', map{'escape': true()})", "<map " + JSN_FN + "><string escaped=\"true\" key=\"x\">\\\\</string><string key=\"y\">%</string></map>" },
      // §17.4.2 mapping rules: null, booleans, numbers (lexically the same
      // as in the JSON), strings, nested containers, empty key.
      { "json-to-xml('[true,false,null,\"s\",-1.5E+2,0]')", "<array " + JSN_FN + "><boolean>true</boolean><boolean>false</boolean><null/><string>s</string><number>-1.5E+2</number><number>0</number></array>" },
      { "json-to-xml('{\"\":{},\"e\":[]}')", "<map " + JSN_FN + "><map key=\"\"/><array key=\"e\"/></map>" },
      { "json-to-xml('null')", "<null " + JSN_FN + "/>" },
      { "json-to-xml('12.50')", "<number " + JSN_FN + ">12.50</number>" },
      { "json-to-xml(' [ { \"a\" : \"b\" } ] ')", "<array " + JSN_FN + "><map><string key=\"a\">b</string></map></array>" },
      // Default duplicates (validate false): retain.
      { "json-to-xml('{\"a\":1,\"a\":2}')", "<map " + JSN_FN + "><number key=\"a\">1</number><number key=\"a\">2</number></map>" },
      { "json-to-xml('{\"a\":1,\"a\":2}', map{'duplicates':'retain'})", "<map " + JSN_FN + "><number key=\"a\">1</number><number key=\"a\">2</number></map>" },
      { "json-to-xml('{\"a\":1,\"b\":{\"c\":1,\"c\":2},\"a\":2}', map{'duplicates':'use-first'})", "<map " + JSN_FN + "><number key=\"a\">1</number><map key=\"b\"><number key=\"c\">1</number></map></map>" },
      { "json-to-xml('[{\"a\":1,\"a\":2}]', map{'duplicates':'use-first'})", "<array " + JSN_FN + "><map><number key=\"a\">1</number></map></array>" },
      { "json-to-xml('{\"a\":1,\"b\":2}', map{'duplicates':'reject'})", "<map " + JSN_FN + "><number key=\"a\">1</number><number key=\"b\">2</number></map>" },
      // escape=false: escapes expanded, non-XML characters become U+FFFD.
      { "json-to-xml('[\"\\u0041\\/\", \"\\uFFFF\"]')", "<array " + JSN_FN + "><string>A/</string><string>" + "\uFFFD" + "</string></array>" },
      { "json-to-xml('{\"\\u0000\":1}')", "<map " + JSN_FN + "><number key=\"" + "\uFFFD" + "\">1</number></map>" },
      // escape=true: special characters escaped, escaped="true" /
      // escaped-key="true" present exactly when a backslash occurs.
      { "json-to-xml('{\"a\\tb\":\"c\\nd\",\"plain\":\"\\u0041\"}', map{'escape': true()})", "<map " + JSN_FN + "><string escaped=\"true\" escaped-key=\"true\" key=\"a\\tb\">c\\nd</string><string key=\"plain\">A</string></map>" },
      { "json-to-xml('{\"k\\\\\":1}', map{'escape': true()})", "<map " + JSN_FN + "><number escaped-key=\"true\" key=\"k\\\\\">1</number></map>" },
      // fallback replaces each non-XML character.
      { "json-to-xml('[\"a\\uFFFFb\"]', map{'fallback': function($s){'['||$s||']'}})", "<array " + JSN_FN + "><string>a[\\uFFFF]b</string></array>" },
      // A leading byte order mark is ignored.
      { "json-to-xml(codepoints-to-string(65279) || '[]')", "<array " + JSN_FN + "/>" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final String sError = SpecTestHelper.check (env (aCase[1]), ok ("deep-equal(" + aCase[0] + ", /)", "true"));
      if (sError != null)
        aErrors.add (sError + " (got " + str (eval ("serialize(" + aCase[0] + ")")) + ")");
    }
    assertTrue (String.join ("\n", aErrors), aErrors.isEmpty ());
  }

  @Test
  public void jsonToXMLProperties ()
  {
    run (
      ok ("empty(json-to-xml(()))", "true"),
      ok ("json-to-xml('1') instance of document-node()", "true"),
      // "The function returns a document node, whose only child is the
      // element node representing the outermost construct".
      ok ("count(json-to-xml('[1,2]')/node())", "1"),
      ok ("namespace-uri(json-to-xml('[1,2]')/*)", "http://www.w3.org/2005/xpath-functions"),
      ok ("local-name(json-to-xml('{\"a\":true}')/*/*)", "boolean"),
      // No whitespace text nodes, comments or PIs.
      ok ("count(json-to-xml('[ 1 , [ ] , { } ]')//text())", "1"),
      // Document order of the result is usable.
      ok ("string-join(json-to-xml('[\"a\",\"b\",\"c\"]')//*:string, ',')", "a,b,c"),
      ok ("let $d := json-to-xml('[\"a\",\"b\"]') return ($d//*:string)[2] >> ($d//*:string)[1]", "true"),
      // Round trip through xml-to-json.
      ok ("xml-to-json(json-to-xml('{\"a\":[1,true,null,\"x\"]}'))", "{\"a\":[1,true,null,\"x\"]}")
    );
    run (
      err ("json-to-xml('[1,')", "FOJS0001"),
      err ("json-to-xml('{\"a\":1}x')", "FOJS0001"),
      err ("json-to-xml('[1] 2')", "FOJS0001"),
      err ("json-to-xml('\"\\x\"')", "FOJS0001"),
      err ("json-to-xml('{\"a\":1,\"a\":2}', map{'duplicates':'reject'})", "FOJS0003"),
      err ("json-to-xml('[{\"b\":{\"a\":1,\"a\":2}}]', map{'duplicates':'reject'})", "FOJS0003"),
      // use-last is a parse-json value only; json-to-xml allows
      // reject|use-first|retain.
      err ("json-to-xml('{}', map{'duplicates':'use-last'})", "FOJS0005"),
      err ("json-to-xml('{}', map{'duplicates':'x'})", "FOJS0005"),
      err ("json-to-xml('\"a\"', map{'escape':true(), 'fallback':function($s){$s}})", "FOJS0005"),
      // Option types (option parameter conventions).
      err ("json-to-xml('1', map{'liberal':'true'})", "XPTY0004"),
      err ("json-to-xml('1', map{'validate':()})", "XPTY0004"),
      err ("json-to-xml('1', map{'escape':(true(),true())})", "XPTY0004"),
      err ("json-to-xml('1', map{'fallback':1})", "XPTY0004"),
      err ("json-to-xml('1', map{'fallback':concat#2})", "XPTY0004"),
      // validate=true needs a schema-aware processor (FOJS0004); a bare
      // XPath context has none.
      err ("json-to-xml('1', map{'validate':true()})", "FOJS0004"),
      // The fallback may raise an error of its own.
      err ("json-to-xml('\"\\uFFFF\"', map{'fallback':function($s){error(QName('http://www.w3.org/2005/xqt-errors','FOJS9998'))}})", "FOJS9998")
    );
  }

  @Test
  public void xmlToJSONSpecExamples ()
  {
    final String [] [] aCases = {
      // §17.5.4 examples.
      { "<array " + JSN_FN + "><number>1</number><string>is</string><boolean>1</boolean></array>", "[1,\"is\",true]" },
      { "<map " + JSN_FN + "><number key=\"Sunday\">1</number><number key=\"Monday\">2</number></map>", "{\"Sunday\":1,\"Monday\":2}" },
      // null, booleans (xs:boolean(fn:string($E)), whitespace accepted).
      { "<null " + JSN_FN + "/>", "null" },
      { "<boolean " + JSN_FN + "> false </boolean>", "false" },
      { "<boolean " + JSN_FN + ">0</boolean>", "false" },
      { "<boolean " + JSN_FN + ">true</boolean>", "true" },
      // number: xs:string(xs:double(fn:string($E))).
      { "<number " + JSN_FN + "> 1e2 </number>", "100" },
      { "<number " + JSN_FN + ">-0.5</number>", "-0.5" },
      { "<number " + JSN_FN + ">1.0E6</number>", "1.0E6" },
      { "<number " + JSN_FN + ">0.0000001</number>", "1.0E-7" },
      { "<number " + JSN_FN + ">0.000001</number>", "0.000001" },
      { "<number " + JSN_FN + ">12.50</number>", "12.5" },
      // Empty containers, nesting, whitespace / comments / PIs ignored.
      { "<map " + JSN_FN + "/>", "{}" },
      { "<array " + JSN_FN + "/>", "[]" },
      { "<map " + JSN_FN + "> <array key=\"a\"> <!--c--> <map/> <?pi x?> </array> </map>", "{\"a\":[{}]}" },
      { "<array " + JSN_FN + "><array><array/></array></array>", "[[[]]]" },
      // A key attribute on the top-level element is ignored.
      { "<string key=\"ignored\" " + JSN_FN + ">v</string>", "\"v\"" },
      // Attributes in other namespaces are ignored.
      { "<string xmlns:o=\"http://example.com/o\" o:a=\"1\" " + JSN_FN + ">v</string>", "\"v\"" },
      // Unescaped strings: backslash, quote and control characters escaped.
      { "<string " + JSN_FN + ">a\\b\"c</string>", "\"a\\\\b\\\"c\"" },
      { "<string " + JSN_FN + ">&#9;&#10;&#13;</string>", "\"\\t\\n\\r\"" },
      { "<map " + JSN_FN + "><null key=\"a\\b\"/></map>", "{\"a\\\\b\":null}" },
      { "<map " + JSN_FN + "><null key=\"q&quot;\"/></map>", "{\"q\\\"\":null}" },
      // escaped="true": valid escapes copied unchanged, unescaped special
      // characters escaped.
      { "<string escaped=\"true\" " + JSN_FN + ">a\\nb\\u00e9\\\\\\\"\\/\\b\\f\\r\\t</string>", "\"a\\nb\\u00e9\\\\\\\"\\/\\b\\f\\r\\t\"" },
      { "<string escaped=\"true\" " + JSN_FN + ">\"&#9;</string>", "\"\\\"\\t\"" },
      { "<string escaped=\"1\" " + JSN_FN + ">\\n</string>", "\"\\n\"" },
      { "<string escaped=\"false\" " + JSN_FN + ">\\n</string>", "\"\\\\n\"" },
      // escaped-key="true".
      { "<map " + JSN_FN + "><null key=\"a\\tb\" escaped-key=\"true\"/></map>", "{\"a\\tb\":null}" },
      { "<map " + JSN_FN + "><null key=\"a\\tb\" escaped-key=\"false\"/></map>", "{\"a\\\\tb\":null}" },
      { "<map " + JSN_FN + "><null key=\"\\uD83D\\uDE00\" escaped-key=\"true\"/><null key=\"x\"/></map>", "{\"\\uD83D\\uDE00\":null,\"x\":null}" },
      // Escaped keys and strings: every JSON escape form is accepted, and
      // raw solidus / newline / carriage return / U+007F get escaped.
      { "<map " + JSN_FN + "><null key='\\b\\f\\r\\/\\\"' escaped-key=\"true\"/></map>", "{\"\\b\\f\\r\\/\\\"\":null}" },
      { "<string escaped=\"true\" " + JSN_FN + ">a/b&#10;&#13;&#8232;</string>", "\"a\\/b\\n\\r\u2028\"" },
      { "<string escaped=\"true\" " + JSN_FN + ">&#127;&#159;</string>", "\"\\u007F\\u009F\"" },
      // A document node with one element child is processed by
      // processing that child.
      { "<!--c--><array " + JSN_FN + "/>", "[]" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final String sError = SpecTestHelper.check (env (aCase[0]), ok ("xml-to-json(/)", aCase[1]));
      if (sError != null)
        aErrors.add (aCase[0] + ": " + sError);
    }
    assertTrue (String.join ("\n", aErrors), aErrors.isEmpty ());
  }

  @Test
  public void xmlToJSONArguments ()
  {
    final String sXml = "<array " + JSN_FN + "><map><string key=\"k\">v</string></map><number>2</number></array>";
    run (sXml,
      ok ("empty(xml-to-json(()))", "true"),
      // An element node is accepted as well as a document node, including
      // one that has a key attribute.
      ok ("xml-to-json(/*)", "[{\"k\":\"v\"},2]"),
      ok ("xml-to-json(/*/*[1]/*)", "\"v\""),
      ok ("xml-to-json(/*/*[2])", "2"),
      ok ("xml-to-json(/, map{'indent':false()})", "[{\"k\":\"v\"},2]"),
      // indent=true may only add whitespace between tokens.
      ok ("replace(xml-to-json(/, map{'indent':true()}), '\\s', '')", "[{\"k\":\"v\"},2]"),
      ok ("deep-equal(parse-json(xml-to-json(/, map{'indent':true()})), parse-json(xml-to-json(/)))", "true"),
      // Unknown option keys are ignored.
      ok ("xml-to-json(/*/*[2], map{'other':1})", "2")
    );
    run (sXml,
      err ("xml-to-json(/, map{'indent':'yes'})", "XPTY0004"),
      err ("xml-to-json(/, map{'indent':()})", "XPTY0004"),
      err ("xml-to-json(/, map{'indent':1})", "XPTY0004"),
      // $input as node()? — a two-node sequence is a type error.
      err ("xml-to-json(/*/*)", "XPTY0004")
    );
  }

  @Test
  public void xmlToJSONInvalidInput ()
  {
    // FOJS0006: input not valid against the schema of §C.2 (or not a
    // document/element node); FOJS0007: invalid escape in escaped content.
    final String [] [] aCases = {
      { "<array " + JSN_FN + "><foo/></array>", "xml-to-json(/)", "FOJS0006" },
      { "<array " + JSN_FN + "><item xmlns=\"http://example.com\"/></array>", "xml-to-json(/)", "FOJS0006" },
      { "<array " + JSN_FN + ">text</array>", "xml-to-json(/)", "FOJS0006" },
      { "<map " + JSN_FN + ">text</map>", "xml-to-json(/)", "FOJS0006" },
      { "<map " + JSN_FN + "><null/></map>", "xml-to-json(/)", "FOJS0006" },
      { "<map " + JSN_FN + "><null key=\"a\"/><null key=\"a\"/></map>", "xml-to-json(/)", "FOJS0006" },
      // Keys equal after expanding escapes are duplicates (§17.5.4 Notes:
      // the n escape and the u000A escape).
      { "<map " + JSN_FN + "><null key=\"\\n\" escaped-key=\"true\"/><null key=\"\\u000A\" escaped-key=\"true\"/></map>", "xml-to-json(/)", "FOJS0006" },
      { "<map " + JSN_FN + "><null key=\"\\n\" escaped-key=\"maybe\"/></map>", "xml-to-json(/)", "FOJS0006" },
      { "<string " + JSN_FN + "><a/></string>", "xml-to-json(/)", "FOJS0006" },
      { "<string escaped=\"yes\" " + JSN_FN + ">x</string>", "xml-to-json(/)", "FOJS0006" },
      { "<number " + JSN_FN + ">abc</number>", "xml-to-json(/)", "FOJS0006" },
      { "<number " + JSN_FN + ">INF</number>", "xml-to-json(/)", "FOJS0006" },
      { "<number " + JSN_FN + ">NaN</number>", "xml-to-json(/)", "FOJS0006" },
      { "<number " + JSN_FN + "><a/></number>", "xml-to-json(/)", "FOJS0006" },
      { "<boolean " + JSN_FN + ">yes</boolean>", "xml-to-json(/)", "FOJS0006" },
      { "<boolean " + JSN_FN + "><a/></boolean>", "xml-to-json(/)", "FOJS0006" },
      { "<null " + JSN_FN + ">x</null>", "xml-to-json(/)", "FOJS0006" },
      { "<null " + JSN_FN + "><a/></null>", "xml-to-json(/)", "FOJS0006" },
      // Disallowed attributes: no-namespace ones not in the schema, and
      // any attribute in the xpath-functions namespace.
      { "<number " + JSN_FN + " escaped=\"true\">1</number>", "xml-to-json(/)", "FOJS0006" },
      { "<array " + JSN_FN + " foo=\"1\"/>", "xml-to-json(/)", "FOJS0006" },
      { "<array " + JSN_FN + " xmlns:j=\"http://www.w3.org/2005/xpath-functions\" j:key=\"1\"/>", "xml-to-json(/)", "FOJS0006" },
      // Invalid JSON escapes in escaped content.
      { "<string escaped=\"true\" " + JSN_FN + ">a\\x</string>", "xml-to-json(/)", "FOJS0007" },
      { "<string escaped=\"true\" " + JSN_FN + ">a\\</string>", "xml-to-json(/)", "FOJS0007" },
      { "<string escaped=\"true\" " + JSN_FN + ">\\u12</string>", "xml-to-json(/)", "FOJS0007" },
      { "<string escaped=\"true\" " + JSN_FN + ">\\u12G4</string>", "xml-to-json(/)", "FOJS0007" },
      { "<map " + JSN_FN + "><null key=\"\\q\" escaped-key=\"true\"/></map>", "xml-to-json(/)", "FOJS0007" },
      { "<map " + JSN_FN + "><null key=\"a\\\" escaped-key=\"true\"/></map>", "xml-to-json(/)", "FOJS0007" },
      { "<map " + JSN_FN + "><null key=\"\\u00\" escaped-key=\"true\"/></map>", "xml-to-json(/)", "FOJS0007" },
      { "<map " + JSN_FN + "><null key=\"\\uZZZZ\" escaped-key=\"true\"/></map>", "xml-to-json(/)", "FOJS0007" } };
    for (final String [] aCase : aCases)
      run (aCase[0], err (aCase[1], aCase[2]));
  }

  @Test
  public void xmlToJSONControlEscape ()
  {
    // §17.5.4: "any other codepoint in the range 1-31 or 127-159 is replaced
    // by an escape in the form <code>&#92;uHHHH</code> where HHHH is the upper-case
    // hexadecimal representation". U+007F..U+009F are XML 1.0 characters.
    run (
      ok ("xml-to-json(json-to-xml('\"\\u007F\\u0080\\u009F\"'))", "\"\\u007F\\u0080\\u009F\"")
    );
  }

  @Test
  public void xmlToJSONWrongRootCodes ()
  {
    // §17.5.4: "A dynamic error is raised [err:FOJS0006] if the value of
    // $input is not a document or element node or is not valid according to
    // the schema"; an atomic argument fails the node()? signature (XPTY0004).
    // Skipped in go-xslt at 04ccada ("BUG: xml-to-json raises XPTY0004 for an
    // element outside the fn namespace and FOJS0006 for an atomic argument
    // (codes swapped)").
    final String [] [] aCases = {
      { "<r/>", "xml-to-json(/)", "FOJS0006" },
      { "<map/>", "xml-to-json(/)", "FOJS0006" },
      { "<r><a/></r>", "xml-to-json(/r/a)", "FOJS0006" },
      { "<r/>", "xml-to-json(1)", "XPTY0004" } };
    for (final String [] aCase : aCases)
      run (aCase[0], err (aCase[1], aCase[2]));
  }
}
