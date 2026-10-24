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

import static com.helger.xpath.serialize.XPathTestEval.adaptive;
import static com.helger.xpath.serialize.XPathTestEval.assertError;
import static com.helger.xpath.serialize.XPathTestEval.evalString;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link FnJson}.
 *
 * @author Philip Helger
 */
public final class FnJsonTest
{
  @Test
  public void testParseJson ()
  {
    assertEquals ("map{\"a\":[1.0e0,\"x\",true(),(),map{}]}", adaptive ("parse-json('{\"a\":[1,\"x\",true,null,{}]}')"));
    assertEquals ("", adaptive ("parse-json(())"));
    assertEquals ("-0.0e0", adaptive ("parse-json(' -0 ')"));
    assertEquals ("\"\uFEFF\"", adaptive ("parse-json('\"\uFEFF\"')"));
    assertError ("FOJS0001", "parse-json('[1,]')");
    assertError ("FOJS0001", "parse-json('[01]')");
    assertError ("FOJS0001", "parse-json('{\"a\":1} x')");
    assertError ("FOJS0001", "parse-json('\"\\q\"')");
  }

  @Test
  public void testParseJsonOptions ()
  {
    assertEquals ("map{\"a\":1.0e0}", adaptive ("parse-json('{\"a\":1,\"a\":2}')"));
    assertEquals ("map{\"a\":2.0e0}", adaptive ("parse-json('{\"a\":1,\"a\":2}', map{'duplicates':'use-last'})"));
    assertError ("FOJS0003", "parse-json('{\"a\":1,\"a\":2}', map{'duplicates':'reject'})");
    assertError ("FOJS0005", "parse-json('{}', map{'duplicates':'retain'})");
    assertError ("XPTY0004", "parse-json('{}', map{'liberal':'yes'})");
    // non-XML characters
    assertEquals ("\"a\uFFFDb\"", adaptive ("parse-json('\"a\\u0000b\"')"));
    assertEquals ("\"a\\u0000b\\\\\\n\"", adaptive ("parse-json('\"a\\u0000b\\\\\\n\"', map{'escape':true()})"));
    assertEquals ("\"a[\\uDEAD]b\"",
                  adaptive ("parse-json('\"a\\uDEADb\"', map{'fallback':function($s) { '[' || $s || ']' }})"));
    assertError ("FOJS0005", "parse-json('1', map{'escape':true(), 'fallback':function($s) { $s }})");
  }

  @Test
  public void testJsonToXml ()
  {
    assertEquals ("<map xmlns=\"http://www.w3.org/2005/xpath-functions\"><array key=\"a\"><number>1</number>" +
                  "<string>x</string><boolean>true</boolean><null/></array></map>",
                  evalString ("serialize(json-to-xml('{\"a\":[1,\"x\",true,null]}'))"));
    assertEquals ("<map xmlns=\"http://www.w3.org/2005/xpath-functions\"><number key=\"a\">1</number>" +
                  "<number key=\"a\">2</number></map>",
                  evalString ("serialize(json-to-xml('{\"a\":1,\"a\":2}'))"));
    assertEquals ("<map xmlns=\"http://www.w3.org/2005/xpath-functions\"><number key=\"a\">1</number></map>",
                  evalString ("serialize(json-to-xml('{\"a\":1,\"a\":2}', map{'duplicates':'use-first'}))"));
    assertError ("FOJS0003", "json-to-xml('{\"a\":1,\"a\":2}', map{'duplicates':'reject'})");
    assertError ("FOJS0005", "json-to-xml('{}', map{'duplicates':'use-last'})");
    assertEquals ("<string xmlns=\"http://www.w3.org/2005/xpath-functions\" escaped=\"true\">a\\tb</string>",
                  evalString ("serialize(json-to-xml('\"a\\tb\"', map{'escape':true()}))"));
  }

  @Test
  public void testXmlToJson ()
  {
    assertEquals ("{\"a\":[1,\"x\\/y\",true,null,{}]}",
                  evalString ("xml-to-json(json-to-xml('{\"a\":[1.0,\"x/y\",true,null,{}]}'))"));
    assertEquals ("[\"a\\tb\"]", evalString ("xml-to-json(json-to-xml('[\"a\\tb\"]', map{'escape':true()}))"));
    assertError ("FOJS0006", "xml-to-json(parse-xml('<array xmlns=\"http://www.w3.org/2005/xpath-functions\">x</array>'))");
    assertError ("FOJS0006", "xml-to-json(parse-xml('<foo/>'))");
    assertError ("FOJS0006",
                 "xml-to-json(parse-xml('<map xmlns=\"http://www.w3.org/2005/xpath-functions\"><null key=\"a\"/>" +
                             "<null key=\"a\"/></map>'))");
    assertError ("FOJS0007",
                 "xml-to-json(parse-xml('<string xmlns=\"http://www.w3.org/2005/xpath-functions\" escaped=\"true\">\\q</string>'))");
    assertEquals ("{\n  \"a\": [\n    1\n  ]\n}",
                  evalString ("xml-to-json(json-to-xml('{\"a\":[1]}'), map{'indent':true()})"));
  }

  @Test
  public void testParseXml ()
  {
    assertEquals ("<a><b/>t</a>", evalString ("serialize(parse-xml('<a><b/>t</a>'))"));
    assertError ("FODC0006", "parse-xml('<a>')");
    assertError ("FODC0006", "parse-xml('<x:a/>')");
    assertEquals ("t<a/>u", evalString ("serialize(parse-xml-fragment('t<a/>u'))"));
    assertEquals ("<a/>", evalString ("serialize(parse-xml-fragment('<?xml version=\"1.0\" encoding=\"UTF-8\"?><a/>'))"));
    assertError ("FODC0006", "parse-xml-fragment('<?xml version=\"1.0\"?><a/>')");
    assertError ("FODC0006", "parse-xml-fragment('<!DOCTYPE a><a/>')");
    assertEquals ("", evalString ("serialize(parse-xml-fragment(''))"));
  }
}
