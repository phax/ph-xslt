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
package com.helger.xpath.serialize;

import static com.helger.xpath.serialize.XPathTestEval.assertError;
import static com.helger.xpath.serialize.XPathTestEval.evalString;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link XdmSerializer}.
 *
 * @author Philip Helger
 */
public final class XdmSerializerTest
{
  @Test
  public void testSequenceNormalisation ()
  {
    assertEquals ("", evalString ("serialize(())"));
    assertEquals ("1 2<a/>3 4", evalString ("serialize((1, 2, parse-xml('<a/>'), 3, 4))"));
    assertEquals ("1|2|<a/>|3", evalString ("serialize((1, 2, parse-xml('<a/>'), 3), map{'item-separator':'|'})"));
    assertEquals ("1 2 3", evalString ("serialize([1, [2, 3]])"));
    assertError ("SENR0001", "serialize(map{})");
    assertError ("SENR0001", "serialize(map:size#1)");
    assertError ("SENR0001", "serialize(parse-xml('<a x=\"1\"/>')/a/@x)");
  }

  @Test
  public void testXml ()
  {
    assertEquals ("<a xmlns=\"u\"><b x=\"&lt;&amp;&#34;\">&lt;&amp;&gt;</b><!--c--><?p d?><c xmlns=\"\"/></a>",
                  evalString ("serialize(parse-xml('<a xmlns=\"u\"><b x=\"&lt;&amp;&quot;\">&lt;&amp;&gt;</b><!--c--><?p d?><c xmlns=\"\"/></a>'))"));
    assertEquals ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><a/>",
                  evalString ("serialize(parse-xml('<a/>'), map{'omit-xml-declaration':false(), 'standalone':true()})"));
    assertEquals ("<!DOCTYPE a SYSTEM \"a.dtd\"><a/>",
                  evalString ("serialize(parse-xml('<a/>'), map{'doctype-system':'a.dtd'})"));
    assertEquals ("<a>\n  <b>x</b>\n  <c>\n    <d/>\n  </c>\n</a>",
                  evalString ("serialize(parse-xml('<a><b>x</b><c><d/></c></a>'), map{'indent':true()})"));
    assertEquals ("<a><![CDATA[x<y]]></a>",
                  evalString ("serialize(parse-xml('<a>x&lt;y</a>'), parse-xml('<output:serialization-parameters " +
                              "xmlns:output=\"http://www.w3.org/2010/xslt-xquery-serialization\">" +
                              "<output:cdata-section-elements value=\"a\"/></output:serialization-parameters>')/*)"));
    assertEquals ("<a>&#233;</a>", evalString ("serialize(parse-xml('<a>&#233;</a>'), map{'encoding':'US-ASCII'})"));
    assertEquals ("<a>£1</a>", evalString ("serialize(parse-xml('<a>$1</a>'), map{'use-character-maps':map{'$':'£'}})"));
    assertError ("SEPM0009", "serialize(parse-xml('<a/>'), map{'standalone':true()})");
    assertError ("SESU0007", "serialize(parse-xml('<a/>'), map{'encoding':'no-such-encoding'})");
  }

  @Test
  public void testHtmlXhtml ()
  {
    assertEquals ("<!DOCTYPE html><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" +
                  "</head><body><br><p></p><input checked></body></html>",
                  evalString ("serialize(parse-xml('<html><head/><body><br/><p/><input checked=\"checked\"/></body></html>'), " +
                              "map{'method':'html', 'html-version':5})"));
    assertEquals ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><br /><p></p></body></html>",
                  evalString ("serialize(parse-xml('<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><br/><p/></body></html>'), " +
                              "map{'method':'xhtml'})"));
  }

  @Test
  public void testText ()
  {
    assertEquals ("xy1 2", evalString ("serialize((parse-xml('<a>x<b>y</b><!--c--></a>'), 1, 2), map{'method':'text'})"));
  }

  @Test
  public void testJson ()
  {
    assertEquals ("null", evalString ("serialize((), map{'method':'json'})"));
    assertEquals ("{\"a\":[1,2.5,\"x\\/\\\"\",true,null]}",
                  evalString ("serialize(map{'a':[1, 2.5, 'x/\"', true(), ()]}, map{'method':'json'})"));
    assertEquals ("\"<a\\/>\"", evalString ("serialize(parse-xml('<a/>'), map{'method':'json'})"));
    assertError ("SERE0023", "serialize((1, 2), map{'method':'json'})");
    assertError ("SERE0020", "serialize(0e0 div 0e0, map{'method':'json'})");
    assertError ("SERE0022", "serialize(map{'1':1, 1:2}, map{'method':'json'})");
    assertEquals ("{\"1\":1,\"1\":2}",
                  evalString ("serialize(map{'1':1, 1:2}, map{'method':'json', 'allow-duplicate-names':true()})"));
    assertError ("SERE0021", "serialize(map:size#1, map{'method':'json'})");
  }

  @Test
  public void testAdaptive ()
  {
    assertEquals ("1;\"a\"\"b\";true();1.5e6;Q{http://www.w3.org/2005/xpath-functions/map}size;map:size#1;" +
                  "(anonymous-function)#1;[1,(2,3),()];map{\"k\":<a/>}",
                  evalString ("serialize((1, 'a\"b', true(), 1.5e6, function-name(map:size#1), map:size#1, function($x) { $x }, " +
                              "[1, (2, 3), ()], map{'k':parse-xml('<a/>')}), map{'method':'adaptive', 'item-separator':';'})"));
    assertEquals ("x=\"1\"", evalString ("serialize(parse-xml('<a x=\"1\"/>')/a/@x, map{'method':'adaptive'})"));
  }

  @Test
  public void testParams ()
  {
    assertEquals ("<a>\n  <b/>\n</a>",
                  evalString ("serialize(parse-xml('<a><b/></a>'), parse-xml('<output:serialization-parameters " +
                              "xmlns:output=\"http://www.w3.org/2010/xslt-xquery-serialization\"><output:indent value=\"yes\"/>" +
                              "</output:serialization-parameters>')/*)"));
    assertError ("SEPM0017",
                 "serialize(1, parse-xml('<output:serialization-parameters xmlns:output=\"http://www.w3.org/2010/xslt-xquery-serialization\">" +
                             "<output:indent value=\"maybe\"/></output:serialization-parameters>')/*)");
    assertError ("SEPM0019",
                 "serialize(1, parse-xml('<output:serialization-parameters xmlns:output=\"http://www.w3.org/2010/xslt-xquery-serialization\">" +
                             "<output:indent value=\"yes\"/><output:indent value=\"yes\"/></output:serialization-parameters>')/*)");
    assertError ("XPTY0004", "serialize(1, parse-xml('<a/>')/*)");
    assertError ("XPTY0004", "serialize(1, map{'indent':'yes'})");
    assertError ("SEPM0016", "serialize(1, map{'method':'nonsense'})");
    assertError ("SEPM0016", "serialize(1, map{'use-character-maps':map{'ab':'c'}})");
  }
}
