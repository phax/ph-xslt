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

import static com.helger.xpath.spec.FnCoreTestHelper.coreRun;
import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.spec.SpecTestHelper.Env;
import com.helger.xpath.xdm.XdmNode;

/**
 * Port of go-xslt <code>internal/xpath/fn_resource_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnResourceSpecTest
{
  @NonNull
  private static FnSpecResolver _newResolver ()
  {
    final XdmNode aA = FnSpecResolver.doc ("<a>1</a>", "http://example.com/c/a.xml");
    final XdmNode aB = FnSpecResolver.doc ("<b>2</b>", "http://example.com/c/b.xml");
    // go-xslt also serves "a\xffb" (not UTF-8) as bad.txt; a Java resolver
    // returns decoded text, so that case is not ported
    return new FnSpecResolver ().text ("http://example.com/t/plain.txt", "hello world")
                                .text ("http://example.com/t/lines.txt", "abc\r\ndef\rghi\njkl\n")
                                .text ("http://example.com/t/blank.txt", "a\n\nb")
                                .text ("http://example.com/t/empty.txt", "")
                                .text ("http://example.com/t/nl.txt", "\n")
                                .text ("http://example.com/t/nul.txt", "a\u0000b")
                                .doc ("http://example.com/d/doc.xml",
                                      FnSpecResolver.doc ("<doc><x/></doc>", "http://example.com/d/doc.xml"))
                                .coll ("", aA)
                                .coll ("http://example.com/c/coll", aA, aB);
  }

  @NonNull
  private static Env _env (@NonNull final IResourceResolver aRes, @Nullable final String sBaseUri)
  {
    return env ().staticSetup (aSC -> aSC.setBaseUri (sBaseUri)).dynamicSetup (aCtx -> {
      aCtx.setResourceResolver (aRes);
      return aCtx;
    });
  }

  /**
   * Covers fn:unparsed-text, fn:unparsed-text-lines
   * and fn:unparsed-text-available (F&O 3.1 §14.8.5-§14.8.7).
   */
  @Test
  public void resourceUnparsedText ()
  {
    coreRun (_env (_newResolver (), "http://example.com/t/base.xq"),
      // absolute and relative (against the static base URI) hrefs
      ok ("unparsed-text('http://example.com/t/plain.txt')", "hello world"),
      ok ("unparsed-text('plain.txt')", "hello world"),
      ok ("unparsed-text('plain.txt', 'utf-8')", "hello world"),
      // "If $href is the empty sequence, the function returns the empty sequence."
      ok ("unparsed-text(())", "()"),
      // FOUT1170: fragment identifier, or resource cannot be retrieved
      err ("unparsed-text('plain.txt#frag')", "FOUT1170"),
      err ("unparsed-text('missing.txt')", "FOUT1170"),
      // FOUT1190: invalid encoding name; characters not permitted in XML
      err ("unparsed-text('plain.txt', '123')", "FOUT1190"),
      err ("unparsed-text('nul.txt')", "FOUT1190"),
      // $href as xs:string?, $encoding as xs:string
      err ("unparsed-text(1)", "XPTY0004"),
      err ("unparsed-text(('a','b'))", "XPTY0004"),
      err ("unparsed-text('plain.txt', ())", "XPTY0004"),

      // unparsed-text-lines: lines are separated by CRLF, CR or LF, and a
      // terminating newline does not produce a trailing empty line.
      ok ("unparsed-text-lines('lines.txt')", "abc|def|ghi|jkl"),
      ok ("count(unparsed-text-lines('blank.txt'))", "3"),
      ok ("unparsed-text-lines('blank.txt')[2]", ""),
      ok ("unparsed-text-lines('empty.txt')", "()"),
      // tokenize("\n", '\r\n|\r|\n') is ("", ""); only the LAST empty
      // string is dropped, so a lone newline is one empty line.
      ok ("count(unparsed-text-lines('nl.txt'))", "1"),
      ok ("unparsed-text-lines('nl.txt')", ""),
      ok ("unparsed-text-lines('plain.txt', 'UTF-8')", "hello world"),
      ok ("unparsed-text-lines(())", "()"),
      err ("unparsed-text-lines('missing.txt')", "FOUT1170"),
      err ("unparsed-text-lines('plain.txt', '-x')", "FOUT1190"),
      err ("unparsed-text-lines(1)", "XPTY0004"),
      err ("unparsed-text-lines('plain.txt', 1)", "XPTY0004"),

      // unparsed-text-available: true exactly when unparsed-text would
      // succeed; false otherwise, never an error for the retrieval outcome.
      ok ("unparsed-text-available('plain.txt')", "true"),
      ok ("unparsed-text-available('plain.txt', 'utf-8')", "true"),
      ok ("unparsed-text-available('missing.txt')", "false"),
      ok ("unparsed-text-available('plain.txt#f')", "false"),
      ok ("unparsed-text-available('nul.txt')", "false"),
      ok ("unparsed-text-available('plain.txt', '123')", "false"),
      ok ("unparsed-text-available(())", "false"),
      err ("unparsed-text-available(2)", "XPTY0004"),
      err ("unparsed-text-available('plain.txt', ())", "XPTY0004")
    );
    // With no resolver nothing can be retrieved.
    coreRun (
      err ("unparsed-text('http://example.com/t/plain.txt')", "FOUT1170"),
      ok ("unparsed-text-available('http://example.com/t/plain.txt')", "false")
    );
  }

  /**
   * Covers fn:doc and fn:doc-available (F&O 3.1 §14.6.1/§14.6.2).
   */
  @Test
  public void resourceDoc ()
  {
    coreRun (_env (_newResolver (), "http://example.com/d/base.xq"),
      ok ("name(doc('http://example.com/d/doc.xml')/*)", "doc"),
      ok ("name(doc('doc.xml')/*)", "doc"),
      ok ("doc('doc.xml') instance of document-node()", "true"),
      // stability: two calls with the same URI return the same node
      ok ("doc('doc.xml') is doc('http://example.com/d/doc.xml')", "true"),
      ok ("doc(())", "()"),
      err ("doc('nope.xml')", "FODC0002"),
      err ("doc(1)", "XPTY0004"),
      ok ("doc-available('doc.xml')", "true"),
      ok ("doc-available('nope.xml')", "false"),
      // "If $uri is the empty sequence, this function returns false."
      ok ("doc-available(())", "false"),
      err ("doc-available(xs:integer(2))", "XPTY0004")
    );
    coreRun (
      err ("doc('http://example.com/d/doc.xml')", "FODC0002"),
      ok ("doc-available('http://example.com/d/doc.xml')", "false")
    );
  }

  /**
   * Covers fn:collection and fn:uri-collection
   * (F&O 3.1 §14.6.6/§14.6.7).
   */
  @Test
  public void resourceCollection ()
  {
    coreRun (_env (_newResolver (), "http://example.com/c/base.xq"),
      ok ("count(collection('http://example.com/c/coll'))", "2"),
      ok ("collection('coll') ! string(.)", "1|2"),
      // no argument or the empty sequence: the default collection
      ok ("collection() ! string(.)", "1"),
      ok ("collection(()) ! string(.)", "1"),
      err ("collection('nope')", "FODC0002"),
      err ("collection(1)", "XPTY0004"),
      // uri-collection yields the URIs of the collection's documents
      ok ("uri-collection('coll')", "http://example.com/c/a.xml|http://example.com/c/b.xml"),
      ok ("uri-collection('coll')[1] instance of xs:anyURI", "true"),
      ok ("uri-collection()", "http://example.com/c/a.xml"),
      err ("uri-collection('nope')", "FODC0002")
    );
    // A resolver without collection support: the default collection is
    // absent (FODC0002) and so is every named one.
    coreRun (_env (new FnSpecResolver (false), null),
      err ("collection()", "FODC0002"),
      err ("collection('http://example.com/x')", "FODC0002")
    );
  }

  /**
   * Covers fn:parse-xml and fn:parse-xml-fragment
   * (F&O 3.1 §14.9.1/§14.9.2), including the REC's examples.
   */
  @Test
  public void resourceParseXML ()
  {
    coreRun (
      ok ("parse-xml(\"<alpha>abcd</alpha>\") instance of document-node(element(alpha))", "true"),
      ok ("string(parse-xml(\"<alpha>abcd</alpha>\"))", "abcd"),
      ok ("parse-xml(())", "()"),
      err ("parse-xml(\"<a>\")", "FODC0006"),
      // XML 1.0 §2.1 [1] document ::= prolog element Misc*: a root element
      // is required and no character data may stand beside it.
      err ("parse-xml(\"text only\")", "FODC0006"),
      err ("parse-xml(\"<!-- c -->\")", "FODC0006"),
      err ("parse-xml(\"<a/>tail\")", "FODC0006"),
      ok ("string(parse-xml-fragment(\"text only\"))", "text only"),
      // namespace well-formedness: unbound element or attribute prefix
      err ("parse-xml(\"<p:a/>\")", "FODC0006"),
      err ("parse-xml(\"<a q:x='1'/>\")", "FODC0006"),
      ok ("namespace-uri(parse-xml(\"<p:a xmlns:p='urn:p'/>\")/*)", "urn:p"),
      ok ("string(parse-xml(\"<a xmlns:q='urn:q' q:x='1'/>\")/*/@*:x)", "1"),
      ok ("namespace-uri(parse-xml(\"<a xmlns='urn:d'/>\")/*)", "urn:d"),
      // a parsed document was not retrieved from anywhere
      ok ("document-uri(parse-xml(\"<a/>\"))", "()"),

      // parse-xml-fragment REC examples
      ok ("count(parse-xml-fragment(\"<alpha>abcd</alpha><beta>abcd</beta>\")/*)", "2"),
      ok ("parse-xml-fragment(\"<alpha>abcd</alpha><beta>abcd</beta>\") instance of document-node()", "true"),
      ok ("count(parse-xml-fragment(\"He was <i>so</i> kind\")/node())", "3"),
      ok ("string(parse-xml-fragment(\"He was <i>so</i> kind\"))", "He was so kind"),
      ok ("count(parse-xml-fragment(\"\")/node())", "0"),
      ok ("count(parse-xml-fragment(\" \")/text())", "1"),
      err ("parse-xml-fragment('<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><a/>')", "FODC0006"),
      ok ("parse-xml-fragment(())", "()"),
      // a text declaration must have an encoding declaration
      ok ("count(parse-xml-fragment('<?xml encoding=\"utf-8\"?><a/><b/>')/*)", "2"),
      err ("parse-xml-fragment('<?xml version=\"1.0\"?><a/>')", "FODC0006"),
      err ("parse-xml-fragment('<!DOCTYPE a><a/>')", "FODC0006"),
      err ("parse-xml-fragment('<a>')", "FODC0006"),
      err ("parse-xml-fragment('<p:a/>')", "FODC0006"),
      // the fragment's top-level nodes have the document node as parent
      ok ("let $d := parse-xml-fragment('<a/><b/>') return ($d/b/.. is $d)", "true")
    );
  }

  /**
   * Covers fn:environment-variable and
   * fn:available-environment-variables (F&O 3.1 §14.10.4/§14.10.5).
   */
  @Test
  public void resourceEnvironment ()
  {
    coreRun (
      // every advertised name has a value
      ok ("every $n in available-environment-variables() satisfies exists(environment-variable($n))", "true"),
      ok ("environment-variable('GO_XSLT_NO_SUCH_VARIABLE_8d2f')", "()"),
      err ("environment-variable(1)", "XPTY0004"),
      err ("environment-variable(())", "XPTY0004")
    );
  }

  /**
   * Pins the exported resolution helper on
   * RFC 3986 §5.4.1 examples.
   */
  @Test
  public void resourceResolveAgainstBase ()
  {
    // go-xslt tests its exported ResolveAgainstBase helper; the RFC examples
    // are checked through fn:resolve-uri here, the helper's own fallbacks
    // (no or unparsable base) are not ported
    run (ok ("resolve-uri('g', 'http://a/b/c/d;p?q')", "http://a/b/c/g"),
         ok ("resolve-uri('../g', 'http://a/b/c/d;p?q')", "http://a/b/g"),
         ok ("resolve-uri('/g', 'http://a/b/c/d;p?q')", "http://a/g"),
         ok ("resolve-uri('?y', 'http://a/b/c/d;p?q')", "http://a/b/c/d;p?y"),
         ok ("resolve-uri('g:h', 'http://a/b/c/d;p?q')", "g:h"));
  }
}
