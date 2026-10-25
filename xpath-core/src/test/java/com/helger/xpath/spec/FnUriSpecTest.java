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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.spec.SpecTestHelper.SpecCase;
import com.helger.xpath.xdm.XdmNode;

/**
 * Port of go-xslt's <code>fn_uri_spec_test.go</code>: the URI functions of
 * F&amp;O 3.1 §6 and fn:base-uri (§2.5).
 *
 * @author Philip Helger
 */
public final class FnUriSpecTest
{
  /**
   * @return A fn:resolve-uri case against the base of RFC 3986 §5.4.
   */
  @NonNull
  private static SpecCase _rfc (@NonNull final String sRel, @NonNull final String sWant)
  {
    return ok ("resolve-uri(\"" + sRel + "\", \"http://a/b/c/d;p?q\")", sWant);
  }

  /**
   * fn:encode-for-uri, fn:iri-to-uri and fn:escape-html-uri (F&amp;O 3.1
   * §6.2-§6.4) with the REC's examples.
   */
  @Test
  public void testURIEncodingFunctions ()
  {
    CoreRenderSpecHelper.run (env (),
                             ok ("encode-for-uri(\"http://www.example.com/00/Weather/CA/Los%20Angeles#ocean\")", "http%3A%2F%2Fwww.example.com%2F00%2FWeather%2FCA%2FLos%2520Angeles%23ocean"),
                             ok ("concat(\"http://www.example.com/\", encode-for-uri(\"~bébé\"))", "http://www.example.com/~b%C3%A9b%C3%A9"),
                             ok ("concat(\"http://www.example.com/\", encode-for-uri(\"100% organic\"))", "http://www.example.com/100%25%20organic"),
                             ok ("encode-for-uri(())", ""),
                             err ("encode-for-uri(1)", "XPTY0004"),
                             ok ("iri-to-uri(\"http://www.example.com/00/Weather/CA/Los%20Angeles#ocean\")", "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean"),
                             ok ("iri-to-uri(\"http://www.example.com/~bébé\")", "http://www.example.com/~b%C3%A9b%C3%A9"),
                             // Characters that must be escaped: space, < > " { } | \ ^ ` .
                             ok ("iri-to-uri('a b<c>\"{}|\\^" + "`" + "')", "a%20b%3Cc%3E%22%7B%7D%7C%5C%5E%60"),
                             ok ("iri-to-uri(\"http://h/p?a=1&b=[2]\")", "http://h/p?a=1&b=[2]"),
                             ok ("iri-to-uri(())", ""),
                             err ("iri-to-uri(1)", "XPTY0004"),
                             ok ("escape-html-uri(\"http://www.example.com/00/Weather/CA/Los Angeles#ocean\")", "http://www.example.com/00/Weather/CA/Los Angeles#ocean"),
                             ok ("escape-html-uri(\"javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~bébé');\")", "javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~b%C3%A9b%C3%A9');"),
                             ok ("escape-html-uri(codepoints-to-string((97, 9, 127)))", "a%09%7F"),
                             ok ("escape-html-uri(())", ""),
                             err ("escape-html-uri(1)", "XPTY0004"));
  }

  /**
   * fn:resolve-uri (F&amp;O 3.1 §6.1), which resolves per RFC 3986 §5.2; the
   * expectations are RFC 3986 §5.4's own reference resolution examples
   * against the base "http://a/b/c/d;p?q".
   */
  @Test
  public void testURIResolve ()
  {
    CoreRenderSpecHelper.run (env (),
                             // §5.4.1 normal examples
                             _rfc ("g:h", "g:h"),
                             _rfc ("g", "http://a/b/c/g"),
                             _rfc ("./g", "http://a/b/c/g"),
                             _rfc ("g/", "http://a/b/c/g/"),
                             _rfc ("/g", "http://a/g"),
                             _rfc ("//g", "http://g"),
                             _rfc ("?y", "http://a/b/c/d;p?y"),
                             _rfc ("g?y", "http://a/b/c/g?y"),
                             _rfc ("#s", "http://a/b/c/d;p?q#s"),
                             _rfc ("g#s", "http://a/b/c/g#s"),
                             _rfc ("g?y#s", "http://a/b/c/g?y#s"),
                             _rfc (";x", "http://a/b/c/;x"),
                             _rfc ("g;x", "http://a/b/c/g;x"),
                             _rfc ("g;x?y#s", "http://a/b/c/g;x?y#s"),
                             _rfc ("", "http://a/b/c/d;p?q"),
                             _rfc (".", "http://a/b/c/"),
                             _rfc ("./", "http://a/b/c/"),
                             _rfc ("..", "http://a/b/"),
                             _rfc ("../", "http://a/b/"),
                             _rfc ("../g", "http://a/b/g"),
                             _rfc ("../..", "http://a/"),
                             _rfc ("../../", "http://a/"),
                             _rfc ("../../g", "http://a/g"),
                             // §5.4.2 abnormal examples
                             _rfc ("../../../g", "http://a/g"),
                             _rfc ("../../../../g", "http://a/g"),
                             _rfc ("/./g", "http://a/g"),
                             _rfc ("/../g", "http://a/g"),
                             _rfc ("g.", "http://a/b/c/g."),
                             _rfc (".g", "http://a/b/c/.g"),
                             _rfc ("g..", "http://a/b/c/g.."),
                             _rfc ("..g", "http://a/b/c/..g"),
                             _rfc ("./../g", "http://a/b/g"),
                             _rfc ("./g/.", "http://a/b/c/g/"),
                             _rfc ("g/./h", "http://a/b/c/g/h"),
                             _rfc ("g/../h", "http://a/b/c/h"),
                             _rfc ("g;x=1/./y", "http://a/b/c/g;x=1/y"),
                             _rfc ("g;x=1/../y", "http://a/b/c/y"),
                             _rfc ("g?y/./x", "http://a/b/c/g?y/./x"),
                             _rfc ("g?y/../x", "http://a/b/c/g?y/../x"),
                             _rfc ("g#s/./x", "http://a/b/c/g#s/./x"),
                             _rfc ("g#s/../x", "http://a/b/c/g#s/../x"),
                             _rfc ("http:g", "http:g"),
                             // §5.2.3 merge with an authority and an empty base path.
                             ok ("resolve-uri(\"g\", \"http://a\")", "http://a/g"),
                             ok ("resolve-uri(\"//h/x/../y?q\", \"http://a/b\")", "http://h/y?q"),
                             ok ("resolve-uri(\"g\", \"urn:x\")", "urn:g"),
                             ok ("resolve-uri(\"g\", \"http://a/b\") instance of xs:anyURI", "true"),
                             ok ("resolve-uri((), \"http://a/b\")", "()"),
                             // An invalid reference or a base that is not absolute: FORG0002.
                             err ("resolve-uri(\"http:%%\", \"http://a/b\")", "FORG0002"),
                             err ("resolve-uri(\":\", \"http://a/b\")", "FORG0002"),
                             err ("resolve-uri(\"##x\", \"http://a/b\")", "FORG0002"),
                             err ("resolve-uri(\"1a:b\", \"http://a/b\")", "FORG0002"),
                             err ("resolve-uri(\"g\", \"b/c\")", "FORG0002"),
                             err ("resolve-uri(\"g\", \"http://a/b#frag\")", "FORG0002"),
                             err ("resolve-uri(\"g\", \"http://a/%zz\")", "FORG0002"));
    // The one-argument form resolves against the static base URI.
    CoreRenderSpecHelper.run (env ().staticSetup (x -> x.setBaseUri ("http://a/b/c/d;p?q")),
                                                                                                ok ("resolve-uri(\"../g\")", "http://a/b/g"),
                                                                                                ok ("resolve-uri(\"http://x/y\")", "http://x/y"));
  }

  /**
   * fn:base-uri (F&amp;O 3.1 §2.5) over XML Base: xml:base attributes of the
   * ancestor-or-self elements, resolved innermost against outermost and
   * against the document's base URI.
   */
  @Test
  public void testURIBaseURI ()
  {
    // go-xslt's context base URI is also the document's base URI here
    final XdmNode aDoc = SpecTestHelper.parse ("<r xml:base=\"x/\"><t/></r>");
    aDoc.setBaseUri ("http://h/doc.xml");
    CoreRenderSpecHelper.run (env ("<r xml:base=\"http://ex.com/a/\"><s xml:base=\"sub/\" a=\"1\"><t>x<!--c--><?pi d?></t></s><u xml:base=\"/top/\"/></r>"),
                             ok ("base-uri(/r)", "http://ex.com/a/"),
                             ok ("base-uri(//t)", "http://ex.com/a/sub/"),
                             ok ("base-uri(//s/@a)", "http://ex.com/a/sub/"),
                             ok ("base-uri(//t/text())", "http://ex.com/a/sub/"),
                             ok ("base-uri(//t/comment())", "http://ex.com/a/sub/"),
                             ok ("base-uri(//t/processing-instruction())", "http://ex.com/a/sub/"),
                             ok ("base-uri(//u)", "http://ex.com/top/"),
                             ok ("base-uri(//t) instance of xs:anyURI", "true"),
                             ok ("base-uri(())", "()"));
    CoreRenderSpecHelper.run (env ().contextItem (aDoc).staticSetup (x -> x.setBaseUri ("http://h/doc.xml")),
                             ok ("base-uri(//t)", "http://h/x/"),
                             ok ("base-uri(/)", "http://h/doc.xml"));
  }
}
