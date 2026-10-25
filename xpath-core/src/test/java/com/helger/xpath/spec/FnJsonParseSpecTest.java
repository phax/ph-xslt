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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.spec.SpecTestHelper.Env;
import com.helger.xpath.xdm.XdmNode;

/**
 * Port of go-xslt <code>internal/xpath/fn_json_parse_spec_test.go</code> (commit 04ccada):
 * fn:parse-json and fn:json-doc (F&amp;O 3.1 §17.5.1, §17.5.2). Every expected
 * value is taken from the REC's own examples or follows directly from its
 * rules; error cases assert the catalogued error codes.
 *
 * @author Philip Helger
 */
public final class FnJsonParseSpecTest
{
  /**
   * An in-memory resource resolver serving fixed texts.
   */
  private static final class JsnResolver implements IResourceResolver
  {
    private final ICommonsMap <String, String> m_aTexts;

    JsnResolver (@NonNull final ICommonsMap <String, String> aTexts)
    {
      m_aTexts = aTexts;
    }

    @Nullable
    public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      return null;
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return m_aTexts.get (sAbsoluteUri);
    }
  }

  @Test
  public void jsonParseSpecExamples ()
  {
    run (
      // F&O 3.1 §17.5.1 examples, checked with deep-equal.
      ok ("deep-equal(parse-json('{\"x\":1, \"y\":[3,4,5]}'), map{\"x\":1e0,\"y\":[3e0,4e0,5e0]})", "true"),
      ok ("parse-json('\"abcd\"')", "abcd"),
      ok ("deep-equal(parse-json('{\"x\":\"\\\\\", \"y\":\"\\u0025\"}'), map{\"x\":\"\\\",\"y\":\"%\"})", "true"),
      ok ("deep-equal(parse-json('{\"x\":\"\\\\\", \"y\":\"\\u0025\"}', map{'escape':true()}), map{\"x\":\"\\\\\",\"y\":\"%\"})", "true"),
      ok ("deep-equal(parse-json('{\"x\":\"\\\\\", \"y\":\"\\u0000\"}'), map{\"x\":\"\\\",\"y\":codepoints-to-string(65533)})", "true"),
      ok ("deep-equal(parse-json('{\"x\":\"\\\\\", \"y\":\"\\u0000\"}', map{'escape':true()}), map{\"x\":\"\\\\\",\"y\":\"\\u0000\"})", "true"),
      ok ("deep-equal(parse-json('{\"x\":\"\\\\\", \"y\":\"\\u0000\"}', map{'fallback':function($s){'['||$s||']'}}), map{\"x\":\"\\\",\"y\":\"[\\u0000]\"})", "true"),
      // "the JSON text {"x":2, "y":5} is transformed to the value map{"x":2, "y":5}"
      ok ("deep-equal(parse-json('{\"x\":2, \"y\":5}'), map{\"x\":2, \"y\":5})", "true"),
      // "the JSON text ["a", "b", null] is transformed to the value ["a", "b", ()]"
      ok ("deep-equal(parse-json('[\"a\", \"b\", null]'), [\"a\", \"b\", ()])", "true")
    );
  }

  @Test
  public void jsonParseTypes ()
  {
    run (
      // The result types listed in the Notes of §17.5.1.
      ok ("parse-json('{}') instance of map(xs:string, item()?)", "true"),
      ok ("parse-json('[]') instance of array(item()?)", "true"),
      ok ("array:size(parse-json('[]'))", "0"),
      ok ("map:size(parse-json('{}'))", "0"),
      ok ("parse-json('\"s\"') instance of xs:string", "true"),
      ok ("parse-json('12') instance of xs:double", "true"),
      ok ("parse-json('true') instance of xs:boolean", "true"),
      ok ("parse-json('false') instance of xs:boolean", "true"),
      ok ("empty(parse-json('null'))", "true"),
      ok ("empty(parse-json(()))", "true"),
      ok ("empty(parse-json((), map{}))", "true"),
      // Numbers: cast from xs:string to xs:double.
      ok ("parse-json('-1.5e2') eq -150e0", "true"),
      ok ("parse-json('0.25') eq 0.25e0", "true"),
      // Nested structures.
      ok ("parse-json('{\"a\":{\"b\":[true,{\"c\":null}]}}')?a?b?1", "true"),
      ok ("empty(parse-json('{\"a\":{\"b\":[true,{\"c\":null}]}}')?a?b?2?c)", "true"),
      ok ("map:contains(parse-json('{\"a\":{\"b\":[true,{\"c\":null}]}}')?a?b?2, 'c')", "true"),
      ok ("array:size(parse-json('[[],[[]],{}]'))", "3"),
      // Insignificant whitespace around tokens is allowed by RFC 7159.
      ok ("parse-json(' \t{ \"a\" : [ 1 , 2 ] } ')?a?2", "2"),
      // A leading byte order mark is ignored.
      ok ("array:size(parse-json(codepoints-to-string(65279) || '[1,2]'))", "2"),
      // Escapes are expanded with escape=false (the behaviour of the
      // REC examples above).
      ok ("parse-json('\"a\\/b\"')", "a/b"),
      ok ("parse-json('\"\\u0041\\u00e9\"')", "Aé"),
      ok ("string-join(string-to-codepoints(parse-json('\"\\n\\r\\t\"')), ' ')", "10 13 9"),
      ok ("string-join(string-to-codepoints(parse-json('\"\\uD83D\\uDE00\"')), ' ')", "128512"),
      ok ("parse-json('\"\\\"\"')", "\""),
      // Codepoints that are not XML characters become U+FFFD.
      ok ("string-join(string-to-codepoints(parse-json('\"\\uFFFF\"')), ' ')", "65533"),
      ok ("string-join(string-to-codepoints(parse-json('\"a\\uD800b\"')), ' ')", "97 65533 98"),
      ok ("string-join(string-to-codepoints(parse-json('\"\\uDC00\"')), ' ')", "65533"),
      ok ("string-join(string-to-codepoints(map:keys(parse-json('{\"\\u0000\":1}'))), ' ')", "65533")
    );
  }

  @Test
  public void jsonParseOutOfRangeNumber ()
  {
    // F&O 3.1 §17.4.2: the JSON-number-to-xs:double mapping "will never
    // generate an error for out-of-range values; instead very large or very
    // small values will be converted to +INF or -INF".
    run (
      ok ("parse-json('1E400') eq xs:double('INF')", "true"),
      ok ("parse-json('-1E400') eq xs:double('-INF')", "true")
    );
  }

  @Test
  public void jsonParseDuplicates ()
  {
    run (
      // Default use-first.
      ok ("parse-json('{\"a\":1,\"a\":2}')?a", "1"),
      ok ("map:size(parse-json('{\"a\":1,\"a\":2,\"b\":3}'))", "2"),
      ok ("parse-json('{\"a\":1,\"a\":2}', map{'duplicates':'use-first'})?a", "1"),
      ok ("parse-json('{\"a\":1,\"a\":2}', map{'duplicates':'use-last'})?a", "2"),
      ok ("parse-json('{\"a\":1,\"a\":2,\"a\":3}', map{'duplicates':'use-last'})?a", "3"),
      // Keys are compared after expanding escape sequences.
      ok ("parse-json('{\"a\":1,\"\\u0061\":2}', map{'duplicates':'use-last'})?a", "2"),
      ok ("map:size(parse-json('{\"a\":1,\"\\u0061\":2}'))", "1"),
      // Without duplicates, reject is harmless.
      ok ("map:size(parse-json('{\"a\":1,\"b\":{\"a\":2}}', map{'duplicates':'reject'}))", "2")
    );
    run (
      err ("parse-json('{\"a\":1,\"a\":2}', map{'duplicates':'reject'})", "FOJS0003"),
      err ("parse-json('[{\"x\":{\"a\":1,\"a\":2}}]', map{'duplicates':'reject'})", "FOJS0003"),
      err ("parse-json('{\"a\":1,\"\\u0061\":2}', map{'duplicates':'reject'})", "FOJS0003"),
      // A value of the right type that is not one of the permitted values.
      err ("parse-json('{}', map{'duplicates':'retain'})", "FOJS0005"),
      err ("parse-json('{}', map{'duplicates':'first'})", "FOJS0005")
    );
  }

  @Test
  public void jsonParseEscapeOption ()
  {
    run (
      // escape=true: special characters (x00-x1F, x7F-x9F, non-XML
      // codepoints, backslash) use JSON escapes, two-character ones where
      // available; anything else is unescaped even if escaped in the input.
      ok ("parse-json('\"a\\tb\"', map{'escape':true()})", "a\\tb"),
      ok ("parse-json('\"\\n\\r\\b\\f\"', map{'escape':true()})", "\\n\\r\\b\\f"),
      ok ("parse-json('\"\\\\\"', map{'escape':true()})", "\\\\"),
      ok ("parse-json('\"\\/\"', map{'escape':true()})", "/"),
      ok ("parse-json('\"\\u0041\"', map{'escape':true()})", "A"),
      ok ("parse-json('\"\\\"\"', map{'escape':true()})", "\""),
      ok ("upper-case(parse-json('\"\\u0001\"', map{'escape':true()}))", "\\U0001"),
      ok ("upper-case(parse-json('\"\\u007f\\u009F\"', map{'escape':true()}))", "\\U007F\\U009F"),
      ok ("upper-case(parse-json('\"\\uFFFF\"', map{'escape':true()}))", "\\UFFFF"),
      ok ("upper-case(parse-json('\"x\\udead\"', map{'escape':true()}))", "X\\UDEAD"),
      // A properly paired surrogate is an XML character: unescaped.
      ok ("string-join(string-to-codepoints(parse-json('\"\\uD83D\\uDE00\"', map{'escape':true()})), ' ')", "128512"),
      // A raw non-special character passes through.
      ok ("parse-json('\"é\"', map{'escape':true()})", "é"),
      // escape=false given explicitly behaves like the default.
      ok ("string-join(string-to-codepoints(parse-json('\"a\\tb\"', map{'escape':false()})), ' ')", "97 9 98"),
      // Keys are escaped as well.
      ok ("map:keys(parse-json('{\"a\\\\b\":1}', map{'escape':true()}))", "a\\\\b"),
      // liberal and validate are accepted as booleans.
      ok ("parse-json('[1]', map{'liberal':false()})?1", "1"),
      ok ("parse-json('[1]', map{'validate':false()})?1", "1")
    );
  }

  @Test
  public void jsonParseFallback ()
  {
    run (
      // The fallback is called with the escape sequence of each non-XML
      // character; its result is inserted in place of the character.
      ok ("parse-json('\"a\\uFFFFb\"', map{'fallback':function($s){'['||$s||']'}})", "a[\\uFFFF]b"),
      ok ("parse-json('\"\\uDEAD\"', map{'fallback':function($s){'['||$s||']'}})", "[\\uDEAD]"),
      ok ("parse-json('\"\\uD800x\"', map{'fallback':function($s){'<'||$s||'>'}})", "<\\uD800>x"),
      // Valid XML characters never reach the fallback.
      ok ("string-join(string-to-codepoints(parse-json('\"\\u0041\\n\"', map{'fallback':function($s){'!'}})), ' ')", "65 10"),
      ok ("string-join(string-to-codepoints(parse-json('\"\\uD83D\\uDE00\"', map{'fallback':function($s){'!'}})), ' ')", "128512"),
      // Keys go through the fallback too.
      ok ("map:keys(parse-json('{\"\\uFFFF\":1}', map{'fallback':function($s){'K'}}))", "K"),
      // A named function of arity 1 is acceptable.
      ok ("parse-json('\"\\uFFFF\"', map{'fallback':upper-case#1})", "\\UFFFF"),
      // fallback with escape=false is permitted.
      ok ("parse-json('\"\\uFFFF\"', map{'escape':false(), 'fallback':function($s){'?'}})", "?")
    );
    run (
      // "It is an error to supply the fallback option if the escape option
      // is present with the value true" (FOJS0005).
      err ("parse-json('\"a\"', map{'escape':true(), 'fallback':function($s){$s}})", "FOJS0005"),
      // The fallback may raise a dynamic error of its own.
      err ("parse-json('\"\\uFFFF\"', map{'fallback':function($s){error(QName('http://www.w3.org/2005/xqt-errors','FOJS9999'))}})", "FOJS9999"),
      // Type: function(xs:string) as xs:string — wrong types are XPTY0004.
      err ("parse-json('\"a\"', map{'fallback':'x'})", "XPTY0004"),
      err ("parse-json('\"a\"', map{'fallback':function($a,$b){$a}})", "XPTY0004")
    );
  }

  @Test
  public void jsonParseOptionTypes ()
  {
    // Option parameter conventions: a value of the wrong type is XPTY0004.
    run (
      err ("parse-json('1', map{'liberal':'yes'})", "XPTY0004"),
      err ("parse-json('1', map{'liberal':()})", "XPTY0004"),
      err ("parse-json('1', map{'liberal':(true(),false())})", "XPTY0004"),
      err ("parse-json('1', map{'escape':1})", "XPTY0004")
    );
    // go-xslt also expects XPTY0004 for map{'validate':'false'}, but validate
    // is no option of fn:parse-json (only of fn:json-to-xml), and keys that
    // are not defined are ignored (QT3 fn-parse-json-065, json-doc-044)
    run (ok ("parse-json('1', map{'validate':'false'})", "1"));
  }

  @Test
  public void jsonParseSyntaxErrors ()
  {
    // FOJS0001: input that does not conform to the JSON grammar of RFC 7159
    // (liberal defaults to false).
    run (
      err ("parse-json('')", "FOJS0001"),
      err ("parse-json('   ')", "FOJS0001"),
      err ("parse-json('{')", "FOJS0001"),
      err ("parse-json('}')", "FOJS0001"),
      err ("parse-json('[1,]')", "FOJS0001"),
      err ("parse-json('[1 2]')", "FOJS0001"),
      err ("parse-json('{\"a\" 1}')", "FOJS0001"),
      err ("parse-json('{a:1}')", "FOJS0001"),
      err ("parse-json(\"{'a':1}\")", "FOJS0001"),
      err ("parse-json('{\"a\":1}}')", "FOJS0001"),
      err ("parse-json('[1] [2]')", "FOJS0001"),
      err ("parse-json('01')", "FOJS0001"),
      err ("parse-json('-')", "FOJS0001"),
      err ("parse-json('1.')", "FOJS0001"),
      err ("parse-json('.5')", "FOJS0001"),
      err ("parse-json('+1')", "FOJS0001"),
      err ("parse-json('tru')", "FOJS0001"),
      err ("parse-json('nul')", "FOJS0001"),
      err ("parse-json('NaN')", "FOJS0001"),
      err ("parse-json('\"abc')", "FOJS0001"),
      err ("parse-json('\"\\x41\"')", "FOJS0001"),
      err ("parse-json('\"\\u00G0\"')", "FOJS0001"),
      err ("parse-json('\"a' || codepoints-to-string(9) || 'b\"')", "FOJS0001"),
      err ("parse-json('\"a' || codepoints-to-string(10) || 'b\"')", "FOJS0001"),
      err ("parse-json('{\"a\":[1,{\"b\":}]}')", "FOJS0001"),
      // The same grammar applies when options are given.
      err ("parse-json('[1,]', map{'escape':true()})", "FOJS0001"),
      err ("parse-json('\"\\q\"', map{'escape':true()})", "FOJS0001"),
      err ("parse-json('\"\\q\"', map{'fallback':function($s){$s}})", "FOJS0001"),
      err ("parse-json('{\"a\":1', map{'duplicates':'use-last'})", "FOJS0001")
    );
  }

  @Test
  public void jsonDoc ()
  {
    final ICommonsMap <String, String> aTexts = new CommonsHashMap <> ();
    aTexts.put ("http://example.com/obj.json", "{\"name\":\"go\",\"tags\":[\"a\",\"b\"],\"n\":null}");
    aTexts.put ("http://example.com/dup.json", "{\"k\":1,\"k\":2}");
    aTexts.put ("http://example.com/bom.json", "\uFEFF[true]");
    aTexts.put ("http://example.com/bad.json", "{\"k\":");
    aTexts.put ("http://example.com/esc.json", "[\"\\u0000\"]");
    final JsnResolver aRes = new JsnResolver (aTexts);
    final Env aEnv = env ().dynamicSetup (aCtx -> {
      aCtx.setResourceResolver (aRes);
      return aCtx;
    });
    // fn:json-doc($H, $M) is fn:unparsed-text($H) => fn:parse-json($M).
    run (aEnv,
      ok ("json-doc('http://example.com/obj.json')?name", "go"),
      ok ("array:size(json-doc('http://example.com/obj.json')?tags)", "2"),
      ok ("deep-equal(json-doc('http://example.com/obj.json'), parse-json(unparsed-text('http://example.com/obj.json')))", "true"),
      ok ("json-doc('http://example.com/dup.json')?k", "1"),
      ok ("json-doc('http://example.com/dup.json', map{'duplicates':'use-last'})?k", "2"),
      ok ("json-doc('http://example.com/bom.json')?1", "true"),
      ok ("string-join(string-to-codepoints(json-doc('http://example.com/esc.json')?1), ' ')", "65533"),
      ok ("upper-case(json-doc('http://example.com/esc.json', map{'escape':true()})?1)", "\\U0000"),
      ok ("json-doc('http://example.com/esc.json', map{'fallback':function($s){'F'}})?1", "F"),
      // "If the value of $href is the empty sequence, the function returns
      // the empty sequence."
      ok ("empty(json-doc(()))", "true")
    );
    run (aEnv,
      // Errors of fn:unparsed-text: resource cannot be retrieved.
      err ("json-doc('http://example.com/missing.json')", "FOUT1170"),
      // Errors of fn:parse-json.
      err ("json-doc('http://example.com/bad.json')", "FOJS0001"),
      err ("json-doc('http://example.com/dup.json', map{'duplicates':'reject'})", "FOJS0003"),
      err ("json-doc('http://example.com/obj.json', map{'duplicates':'nope'})", "FOJS0005"),
      err ("json-doc('http://example.com/obj.json', map{'liberal':'no'})", "XPTY0004")
    );
    // Without any resolver, no resource can be retrieved.
    run (
      err ("json-doc('http://example.com/obj.json')", "FOUT1170")
    );
  }
}
