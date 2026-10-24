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
package com.helger.xslt.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.conformance.QT3Outcome;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltTransformResult;

/**
 * Test class for class {@link XSLT30AssertionChecker}, with fake
 * transformation results and the real XPath engine for assertion expressions.
 *
 * @author Philip Helger
 */
public final class XSLT30AssertionCheckerTest
{
  private static final XsltException ERROR = new XsltException ("XTDE0700", "Required parameter missing", null, 0, 0);

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  private static XSLT30Assertion _a (final String sKind, final String sValue, final XSLT30Assertion... aChildren)
  {
    return new XSLT30Assertion (sKind,
                                new CommonsLinkedHashMap <> (),
                                sValue,
                                new CommonsLinkedHashMap <> (),
                                new CommonsArrayList <> (aChildren));
  }

  private static XSLT30Assertion _a (final String sKind,
                                     final String sValue,
                                     final String sAttrName,
                                     final String sAttrValue,
                                     final XSLT30Assertion... aChildren)
  {
    final ICommonsOrderedMap <String, String> aAttrs = new CommonsLinkedHashMap <> ();
    aAttrs.put (sAttrName, sAttrValue);
    return new XSLT30Assertion (sKind, aAttrs, sValue, new CommonsLinkedHashMap <> (), new CommonsArrayList <> (aChildren));
  }

  private static XSLT30Assertion _ns (final String sKind, final String sValue, final String sPrefix, final String sUri)
  {
    final ICommonsOrderedMap <String, String> aNS = new CommonsLinkedHashMap <> ();
    aNS.put (sPrefix, sUri);
    return new XSLT30Assertion (sKind, new CommonsLinkedHashMap <> (), sValue, aNS, new CommonsArrayList <> ());
  }

  private static XdmNode _doc (final String sXml) throws Exception
  {
    return new XdmParser ().parse (sXml);
  }

  /** A template-invoked result: the tree and its serialisation. */
  private static XSLT30Actual _out (final String sSerialized) throws Exception
  {
    XdmNode aTree;
    try
    {
      aTree = _doc (sSerialized);
    }
    catch (final Exception ex)
    {
      aTree = XdmNode.createDocument ();
    }
    final XsltTransformResult aResult = new XsltTransformResult (XdmSequence.of (aTree),
                                                                 aTree,
                                                                 sSerialized,
                                                                 new CommonsArrayList <> (),
                                                                 new CommonsArrayList <> ());
    return XSLT30Actual.ofResult (aResult, false, XSLT30Actual.DEFAULT_RESULT_VAR);
  }

  /** An initial-function result: a raw sequence, no tree. */
  private static XSLT30Actual _raw (final XdmSequence aRaw)
  {
    final XsltTransformResult aResult = new XsltTransformResult (aRaw,
                                                                 null,
                                                                 "",
                                                                 new CommonsArrayList <> (),
                                                                 new CommonsArrayList <> ());
    return XSLT30Actual.ofResult (aResult, true, XSLT30Actual.DEFAULT_RESULT_VAR);
  }

  private XSLT30AssertionChecker _checker ()
  {
    return new XSLT30AssertionChecker (m_aTmp.getRoot ().toPath (), OffsetDateTime.now (), false);
  }

  private QT3Outcome _check (final XSLT30Assertion aAssertion, final XSLT30Actual aActual)
  {
    return _checker ().check (aAssertion, aActual);
  }

  @Test
  public void testError () throws Exception
  {
    assertTrue (_check (_a ("error", "", "code", "XTDE0700"), XSLT30Actual.ofError (ERROR)).isPass ());
    // Any error passes by default
    assertTrue (_check (_a ("error", "", "code", "XTSE0010"), XSLT30Actual.ofError (ERROR)).isPass ());
    assertEquals ("expected-error", _check (_a ("error", "", "code", "XTDE0700"), _out ("<a/>")).reason ());
    // With strict codes the code must match
    final XSLT30AssertionChecker aStrict = new XSLT30AssertionChecker (m_aTmp.getRoot ().toPath (),
                                                                       OffsetDateTime.now (),
                                                                       true);
    assertTrue (aStrict.check (_a ("error", "", "code", "XTDE0700"), XSLT30Actual.ofError (ERROR)).isPass ());
    assertTrue (aStrict.check (_a ("error", "", "code", "*"), XSLT30Actual.ofError (ERROR)).isPass ());
    assertTrue (aStrict.check (_a ("error", "", "code", "XTSE0010"), XSLT30Actual.ofError (ERROR)).isFail ());
    // Any other assertion fails after an error
    assertEquals ("errored:XTDE0700", _check (_a ("assert", "true()"), XSLT30Actual.ofError (ERROR)).reason ());
    assertTrue (_check (_a ("assert-serialization-error", ""), XSLT30Actual.ofError (ERROR)).isPass ());
    assertTrue (_check (_a ("assert-serialization-error", ""), _out ("<a/>")).isFail ());
  }

  @Test
  public void testCombinators () throws Exception
  {
    final XSLT30Actual aOut = _out ("<a/>");
    assertTrue (_check (_a ("all-of", "", _a ("assert", "/a"), _a ("assert", "count(//*) = 1")), aOut).isPass ());
    assertEquals ("assert", _check (_a ("all-of", "", _a ("assert", "/a"), _a ("assert", "/b")), aOut).reason ());
    assertTrue (_check (_a ("any-of", "", _a ("assert", "/b"), _a ("assert", "/a")), aOut).isPass ());
    assertTrue (_check (_a ("any-of", "", _a ("assert-xml", "<a/>"), _a ("error", "")), XSLT30Actual.ofError (ERROR))
                                                                                                                  .isPass ());
    assertEquals ("any-of", _check (_a ("any-of", "", _a ("assert", "/b")), aOut).reason ());
    assertTrue (_check (_a ("not", "", _a ("assert", "/b")), aOut).isPass ());
    assertTrue (_check (_a ("not", "", _a ("assert", "/a")), aOut).isFail ());
    assertTrue (_check (_a ("not", "", _a ("error", "")), aOut).isPass ());
    assertTrue (_check (_a ("assert-warning", ""), aOut).isPass ());
  }

  @Test
  public void testAssertXml () throws Exception
  {
    // Whitespace-only text between elements and prefixes are not significant
    assertTrue (_check (_a ("assert-xml", "<out>\n  <p:x xmlns:p='urn:x' a='1'/>\n</out>"),
                        _out ("<?xml version=\"1.0\"?><out><q:x xmlns:q=\"urn:x\" a=\"1\"/></out>")).isPass ());
    assertTrue (_check (_a ("assert-xml", "<out><x a='2'/></out>"), _out ("<out><x a=\"1\"/></out>")).isFail ());
    assertTrue (_check (_a ("assert-xml", "<out>a  b</out>"), _out ("<out>a b</out>")).isFail ());
    assertTrue (_check (_a ("assert-xml", "<out>a  b</out>", "normalize-space", "true"), _out ("<out>a b</out>"))
                                                                                                                  .isPass ());
    // Fragments with several roots and text
    assertTrue (_check (_a ("assert-xml", "<a/>text<b/>"), _out ("<a/>text<b/>")).isPass ());
  }

  @Test
  public void testExpectedFromFile () throws Exception
  {
    // CRs are removed and a byte order mark is dropped, as in the reference runner
    Files.write (m_aTmp.getRoot ().toPath ().resolve ("exp.out"),
                 "﻿line1\r\nline2".getBytes (StandardCharsets.UTF_8));
    assertTrue (_check (_a ("assert-serialization", "", "file", "exp.out"), _out ("line1\nline2")).isPass ());
    assertTrue (_check (_a ("assert-serialization", "", "file", "exp.out"), _out ("line1 line2")).isFail ());
  }

  @Test
  public void testAssert () throws Exception
  {
    assertTrue (_check (_a ("assert", "/out/x = 'v'"), _out ("<out><x>v</x></out>")).isPass ());
    assertTrue (_check (_a ("assert", "/out/x = 'w'"), _out ("<out><x>v</x></out>")).isFail ());
    assertTrue (_check (_ns ("assert", "/h:html", "h", "urn:h"), _out ("<html xmlns='urn:h'/>")).isPass ());
    // Unbound prefix: the expression does not compile
    assertEquals ("assert-eval", _check (_a ("assert", "/h:html"), _out ("<html/>")).reason ());
    // $result is the raw result, here the result tree
    assertTrue (_check (_a ("assert", "$result instance of document-node()"), _out ("<a/>")).isPass ());
    // Several roots are parsed inside a wrapper
    assertTrue (_check (_a ("assert", "count(/*/*) = 2"), _out ("<a/><b/>")).isPass ());
    // An HTML result with unclosed void elements
    assertTrue (_check (_a ("assert", "/html/body/br"), _out ("<html><body><br>&nbsp;</body></html>")).isPass ());
  }

  @Test
  public void testStringValueAndEq () throws Exception
  {
    assertTrue (_check (_a ("assert-string-value", " a  b "), _out ("<x>a <y>b</y></x>")).isPass ());
    assertTrue (_check (_a ("assert-string-value", "a  b", "normalize-space", "false"), _out ("<x>a b</x>")).isFail ());
    assertTrue (_check (_a ("assert-eq", "'42'"), _out ("<x>42</x>")).isPass ());
    assertTrue (_check (_a ("assert-eq", "40 + 2"), _out ("42")).isPass ());
    assertTrue (_check (_a ("assert-eq", "43"), _out ("42")).isFail ());
  }

  @Test
  public void testSerializationMatches () throws Exception
  {
    assertTrue (_check (_a ("serialization-matches", "<x>\\d+</x>"), _out ("<x>42</x>")).isPass ());
    assertTrue (_check (_a ("serialization-matches", "^<X>", "flags", "i"), _out ("<x>42</x>")).isPass ());
    assertTrue (_check (_a ("serialization-matches", "<y>"), _out ("<x>42</x>")).isFail ());
    assertEquals ("bad-regex", _check (_a ("serialization-matches", "("), _out ("<x/>")).reason ());
  }

  @Test
  public void testValueAssertions () throws Exception
  {
    final XSLT30Actual aTwo = _raw (XdmSequence.of (XdmAtomic.integer (1), XdmAtomic.string ("a")));
    assertTrue (_check (_a ("assert-count", "2"), aTwo).isPass ());
    assertTrue (_check (_a ("assert-count", "1"), aTwo).isFail ());
    assertTrue (_check (_a ("assert-deep-eq", "1, 'a'"), aTwo).isPass ());
    assertTrue (_check (_a ("assert-deep-eq", "'a', 1"), aTwo).isFail ());
    assertTrue (_check (_a ("assert-permutation", "'a', 1"), aTwo).isPass ());
    assertTrue (_check (_a ("assert-type", "item()+"), aTwo).isPass ());
    assertTrue (_check (_a ("assert-type", "xs:integer+"), aTwo).isFail ());
    assertTrue (_check (_a ("assert-empty", ""), _raw (XdmSequence.EMPTY)).isPass ());
    assertTrue (_check (_a ("assert-empty", ""), aTwo).isFail ());
    assertTrue (_check (_a ("assert-true", ""), _raw (XdmSequence.of (XdmAtomic.TRUE))).isPass ());
    assertTrue (_check (_a ("assert-false", ""), _raw (XdmSequence.of (XdmAtomic.TRUE))).isFail ());
    // A template result: the raw value is the result tree
    assertTrue (_check (_a ("assert-type", "document-node(element(a))"), _out ("<a/>")).isPass ());
    assertTrue (_check (_a ("assert-count", "1"), _out ("<a/><b/>")).isPass ());
    // No raw value at all: skipped
    final XSLT30Actual aNoRaw = new XSLT30Actual ("<a/>",
                                                  null,
                                                  new CommonsArrayList <> (),
                                                  new CommonsArrayList <> (),
                                                  null,
                                                  "result",
                                                  null);
    assertTrue (_check (_a ("assert-count", "1"), aNoRaw).isSkip ());
    assertTrue (_check (_a ("assert-unknown", ""), aNoRaw).isSkip ());
  }

  @Test
  public void testResultDocumentAndMessages () throws Exception
  {
    final XdmNode aSecTree = _doc ("<second/>");
    final XsltTransformResult aResult = new XsltTransformResult (XdmSequence.EMPTY,
                                                                 null,
                                                                 "",
                                                                 new CommonsArrayList <> (new XsltTransformResult.SecondaryResult ("file:///tmp/out/second.xml",
                                                                                                                                   XdmSequence.of (aSecTree),
                                                                                                                                   aSecTree,
                                                                                                                                   "<second/>")),
                                                                 new CommonsArrayList <> ("<m>hello</m>", "plain"));
    final XSLT30Actual aActual = XSLT30Actual.ofResult (aResult, false, XSLT30Actual.DEFAULT_RESULT_VAR);
    final XSLT30Assertion aDoc = _a ("assert-result-document", "", "uri", "out/second.xml", _a ("assert", "/second"));
    assertTrue (_check (aDoc, aActual).isPass ());
    final XSLT30Assertion aMissing = _a ("assert-result-document", "", "uri", "third.xml");
    assertEquals ("result-document-missing", _check (aMissing, aActual).reason ());

    assertTrue (_check (_a ("assert-message", "", _a ("assert", "/m = 'hello'")), aActual).isPass ());
    assertTrue (_check (_a ("assert-message", "", _a ("assert", "/m = 'bye'")), aActual).isFail ());
    assertTrue (_check (_a ("assert-message", ""), aActual).isPass ());
    assertTrue (_check (_a ("assert-message", ""), _out ("<a/>")).isFail ());
  }

  @Test
  public void testXmlCompare ()
  {
    assertTrue (XSLT30XmlCompare.isXmlEqual ("<a><!--c--><?p d?></a>", "<a><!--c--><?p d?></a>", false));
    assertFalse (XSLT30XmlCompare.isXmlEqual ("<a><?p d?></a>", "<a><?q d?></a>", false));
    // Not well-formed: compared as text with whitespace normalised
    assertTrue (XSLT30XmlCompare.isXmlEqual ("<a>  <b>", "<a> <b>", false));
    assertEquals ("a b", XSLT30XmlCompare.normalizeSpace ("  a \t\n b "));
    assertEquals ("a", XSLT30XmlCompare.trimSpace (" a "));
  }
}
