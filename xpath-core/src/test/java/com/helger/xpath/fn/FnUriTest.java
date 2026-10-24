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

import static com.helger.xpath.fn.FnNodeTestHelper.assertError;
import static com.helger.xpath.fn.FnNodeTestHelper.eval;
import static com.helger.xpath.fn.FnNodeTestHelper.str;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for class {@link FnUri}.
 *
 * @author Philip Helger
 */
public final class FnUriTest
{
  private static final String BASE = "http://a/b/c/d;p?q";

  @Test
  public void testRfc3986Examples ()
  {
    // RFC 3986 section 5.4.1 and 5.4.2
    final String [] [] aCases = { { "g:h", "g:h" },
                                  { "g", "http://a/b/c/g" },
                                  { "./g", "http://a/b/c/g" },
                                  { "g/", "http://a/b/c/g/" },
                                  { "/g", "http://a/g" },
                                  { "//g", "http://g" },
                                  { "?y", "http://a/b/c/d;p?y" },
                                  { "g?y", "http://a/b/c/g?y" },
                                  { "#s", "http://a/b/c/d;p?q#s" },
                                  { "g#s", "http://a/b/c/g#s" },
                                  { ";x", "http://a/b/c/;x" },
                                  { "", "http://a/b/c/d;p?q" },
                                  { ".", "http://a/b/c/" },
                                  { "..", "http://a/b/" },
                                  { "../g", "http://a/b/g" },
                                  { "../..", "http://a/" },
                                  { "../../../g", "http://a/g" },
                                  { "/./g", "http://a/g" },
                                  { "/../g", "http://a/g" },
                                  { "g.", "http://a/b/c/g." },
                                  { "..g", "http://a/b/c/..g" },
                                  { "./../g", "http://a/b/g" },
                                  { "g/./h", "http://a/b/c/g/h" },
                                  { "g/../h", "http://a/b/c/h" },
                                  { "g;x=1/../y", "http://a/b/c/y" } };
    for (final String [] aCase : aCases)
      assertEquals (aCase[0], aCase[1], FnUri.resolveUri (aCase[0], BASE));
  }

  @Test
  public void testResolveUriFunction ()
  {
    assertEquals ("http://a/b/c/g", str ("resolve-uri('g', '" + BASE + "')"));
    assertEquals ("http://www.example.com/this doc.html",
                  str ("resolve-uri('this doc.html', 'http://www.example.com/that doc.html')"));
    assertTrue (eval ("resolve-uri((), 'http://a/')").isEmpty ());
    assertEquals ("http://x/y", str ("resolve-uri('http://x/y')"));
    assertEquals ("http://base/g", str (eval ("resolve-uri('g')", "http://base/", null, null)));
    assertError ("FONS0005", "resolve-uri('g')");
    assertError ("FORG0002", "resolve-uri(':', 'http://a/')");
    assertError ("FORG0002", "resolve-uri('g', 'relative/base')");
    assertError ("FORG0002", "resolve-uri('g', 'http://a/#frag')");
    assertError ("FORG0002", "resolve-uri('http:%%', 'http://a/')");
  }

  @Test
  public void testValidity ()
  {
    assertTrue (FnUri.isValidUriReference ("a b"));
    assertFalse (FnUri.isValidUriReference (":/"));
    assertFalse (FnUri.isValidUriReference ("%gg"));
    assertTrue (FnUri.isAbsoluteUri ("file:///x"));
    assertFalse (FnUri.isAbsoluteUri ("x/y"));
    assertTrue (FnUri.hasFragment ("x#"));
  }

  @Test
  public void testEscaping ()
  {
    assertEquals ("http%3A%2F%2Fwww.example.com%2F00%2FWeather%2FCA%2FLos%20Angeles%23ocean",
                  FnUri.encodeForUri ("http://www.example.com/00/Weather/CA/Los Angeles#ocean"));
    assertEquals ("~b%C3%A9b%C3%A9", FnUri.encodeForUri ("~bébé"));
    assertEquals ("100%25%20organic", FnUri.encodeForUri ("100% organic"));
    assertEquals ("http://www.example.com/00/Weather/CA/Los%20Angeles#ocean",
                  FnUri.iriToUri ("http://www.example.com/00/Weather/CA/Los%20Angeles#ocean"));
    assertEquals ("http://www.example.com/~b%C3%A9b%C3%A9", FnUri.iriToUri ("http://www.example.com/~bébé"));
    assertEquals ("%3C%3E%22%7B%7D%7C%5C%5E%60", FnUri.iriToUri ("<>\"{}|\\^`"));
    assertEquals ("http://www.example.com/00/Weather/CA/Los Angeles#ocean",
                  FnUri.escapeHtmlUri ("http://www.example.com/00/Weather/CA/Los Angeles#ocean"));
    assertEquals ("javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~b%C3%A9b%C3%A9');",
                  FnUri.escapeHtmlUri ("javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~bébé');"));
    assertEquals ("%F0%90%80%80", FnUri.encodeForUri ("𐀀"));
    assertEquals ("", str ("encode-for-uri(())"));
    assertEquals ("", str ("iri-to-uri(())"));
    assertEquals ("", str ("escape-html-uri(())"));
    assertError ("XPTY0004", "encode-for-uri(1)");
  }
}
