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
package com.helger.phoxtrot.xslt.conformance;

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
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.conformance.QT3Outcome;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.XsltTransformResult;

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

  private static IXdmNode _doc (final String sXml) throws Exception
  {
    return new XdmParser ().parse (sXml);
  }

  /** A template-invoked result: the tree and its serialisation. */
  private static XSLT30Actual _out (final String sSerialized) throws Exception
  {
    IXdmNode aTree;
    try
    {
      aTree = _doc (sSerialized);
    }
    catch (final Exception ex)
    {
      // A text result: a document node with one text node
      aTree = IXdmNode.createDocument ();
      aTree.appendChild (IXdmNode.createText (sSerialized));
      aTree.assignDocumentOrder ();
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
    // A wrong code passes with a note
    assertEquals (QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE),
                  _check (_a ("error", "", "code", "XTSE0010"), XSLT30Actual.ofError (ERROR)));
    // Any other assertion fails inconclusively after an error
    assertEquals (QT3Outcome.failInconclusive ("errored:XTDE0700"),
                  _check (_a ("assert", "true()"), XSLT30Actual.ofError (ERROR)));
  }

  @Test
  public void testSerializationError () throws Exception
  {
    final XSLT30Actual aSerError = XSLT30Actual.ofError (new XsltException ("SEPM0016", "Bad parameter", null, 0, 0));
    assertTrue (_check (_a ("assert-serialization-error", "", "code", "SEPM0016"), aSerError).isPass ());
    // Another serialization error code passes with a note, or fails strictly
    assertEquals (QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE),
                  _check (_a ("assert-serialization-error", "", "code", "SESU0007"), aSerError));
    final XSLT30AssertionChecker aStrict = new XSLT30AssertionChecker (m_aTmp.getRoot ().toPath (),
                                                                       OffsetDateTime.now (),
                                                                       true);
    assertTrue (aStrict.check (_a ("assert-serialization-error", "", "code", "SESU0007"), aSerError).isFail ());
    // An error that is no serialization error (here a dynamic error) fails
    assertEquals (QT3Outcome.fail ("serialization-error-code"),
                  _check (_a ("assert-serialization-error", "", "code", "SEPM0016"), XSLT30Actual.ofError (ERROR)));
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
  public void testNot () throws Exception
  {
    // result-document-0245: not(serialization-matches) must not pass because
    // the transformation raised an error
    final XSLT30Assertion aNotDoctype = _a ("not", "", _a ("serialization-matches", "DOCTYPE"));
    assertTrue (_check (aNotDoctype, _out ("<html/>")).isPass ());
    assertTrue (_check (aNotDoctype, _out ("<!DOCTYPE html><html/>")).isFail ());
    assertEquals (QT3Outcome.failInconclusive ("not:errored:XTDE0700"),
                  _check (aNotDoctype, XSLT30Actual.ofError (ERROR)));
    // An expression that does not evaluate
    assertTrue (_check (_a ("not", "", _a ("assert", "/h:html")), _out ("<a/>")).isFail ());
    assertTrue (_check (_a ("not", "", _a ("serialization-matches", "(")), _out ("<a/>")).isFail ());
    // A skipped child (no raw result)
    final XSLT30Actual aNoRaw = new XSLT30Actual ("<a/>",
                                                  null,
                                                  new CommonsArrayList <> (),
                                                  new CommonsArrayList <> (),
                                                  null,
                                                  "result",
                                                  null);
    assertTrue (_check (_a ("not", "", _a ("assert-count", "2")), aNoRaw).isFail ());
    // not(any-of(error, serialization-matches)): the usual pattern
    final XSLT30Assertion aNotAny = _a ("not", "", _a ("any-of", "", _a ("error", ""), _a ("serialization-matches", "x")));
    assertTrue (_check (aNotAny, _out ("<a/>")).isPass ());
    assertTrue (_check (aNotAny, XSLT30Actual.ofError (ERROR)).isFail ());
    assertTrue (_check (aNotAny, _out ("<x/>")).isFail ());
    assertEquals (QT3Outcome.failInconclusive ("harness:not-children"), _check (_a ("not", ""), _out ("<a/>")));
  }

  @Test
  public void testStripDecl () throws Exception
  {
    assertEquals ("<a/>", XSLT30AssertionChecker.stripDecl ("<?xml version=\"1.0\"?>\n<a/>"));
    assertEquals ("<a/>", XSLT30AssertionChecker.stripDecl ("  <?xml\tversion='1.0' encoding='UTF-8'?><a/>"));
    // A processing instruction is no declaration
    assertEquals ("<?xml-stylesheet href='a.xsl'?><a/>",
                  XSLT30AssertionChecker.stripDecl ("<?xml-stylesheet href='a.xsl'?><a/>"));
    // The PI is part of the result: the reparsed document has it
    assertTrue (_check (_a ("assert", "exists(/processing-instruction('xml-stylesheet'))"),
                        _out ("<?xml-stylesheet href='a.xsl'?><a/>")).isPass ());
  }

  @Test
  public void testFindSecondaryWithoutBaseOutputUri () throws Exception
  {
    final IXdmNode aTree = _doc ("<r/>");
    final XsltTransformResult aResult = new XsltTransformResult (XdmSequence.EMPTY,
                                                                 null,
                                                                 "",
                                                                 new CommonsArrayList <> (new XsltTransformResult.SecondaryResult ("out/./a.xml",
                                                                                                                                   XdmSequence.of (aTree),
                                                                                                                                   aTree,
                                                                                                                                   "<r/>")),
                                                                 new CommonsArrayList <> ());
    final XSLT30Actual aActual = XSLT30Actual.ofResult (aResult, false, XSLT30Actual.DEFAULT_RESULT_VAR);
    // Relative to the test-set directory on both sides
    assertTrue (_checker ().findSecondary (aActual, "out/a.xml") != null);
    assertTrue (_checker ().findSecondary (aActual, "a.xml") == null);
    assertTrue (_checker ().findSecondary (aActual, "x/out/a.xml") == null);
  }

  @Test
  public void testStripIndentation ()
  {
    assertEquals ("<a><b>x y</b></a>", XSLT30AssertionChecker.stripIndentation ("<a>\n  <b>x \n y</b>\n</a>", "html"));
    // The content of the formatted elements is kept
    assertEquals ("<a><pre>x \n  y</pre><b>x y</b></a>",
                  XSLT30AssertionChecker.stripIndentation ("<a>\n <pre>x \n  y</pre>\n <b>x  y</b></a>", "html"));
    assertEquals ("<a><TEXTAREA rows='2'>  t </TEXTAREA></a>",
                  XSLT30AssertionChecker.stripIndentation ("<a> <TEXTAREA rows='2'>  t </TEXTAREA></a>", "xhtml"));
    // xml: only whitespace-only runs between tags
    assertEquals ("<a><b>x  y</b></a>", XSLT30AssertionChecker.stripIndentation ("<a>\n  <b>x  y</b>\n</a>", "xml"));
  }

  @Test
  public void testAssertSerialization () throws Exception
  {
    // Exact by default: surrounding whitespace counts
    assertTrue (_check (_a ("assert-serialization", "<a/>"), _out ("<a/>")).isPass ());
    assertTrue (_check (_a ("assert-serialization", "<a/>"), _out ("<a/>\n")).isFail ());
    assertTrue (_check (_a ("assert-serialization", "\n<a/>"), _out ("<a/>")).isFail ());
    assertTrue (_check (_a ("assert-serialization", " <a>x  y</a> ", "normalize-space", "true"), _out ("<a>x y</a>"))
                                                                                                               .isPass ());
    // An expected file that cannot be read is no empty expectation
    assertEquals (QT3Outcome.failInconclusive ("expected-file"),
                  _check (_a ("assert-serialization", "", "file", "missing.out"), _out ("")));
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
    // Several roots are the children of one document node, as in the result
    // tree
    assertTrue (_check (_a ("assert", "count(/*) = 2"), _out ("<a/><b/>")).isPass ());
    // An HTML result with unclosed void elements
    assertTrue (_check (_a ("assert", "/html/body/br"), _out ("<html><body><br>&nbsp;</body></html>")).isPass ());
  }

  @Test
  public void testStringValueAndEq () throws Exception
  {
    assertTrue (_check (_a ("assert-string-value", " a  b "), _out ("<x>a <y>b</y></x>")).isPass ());
    assertTrue (_check (_a ("assert-string-value", "a  b", "normalize-space", "false"), _out ("<x>a b</x>")).isFail ());
    assertTrue (_check (_a ("assert-eq", "'42'"), _out ("<x>42</x>")).isPass ());
    // An untyped result tree compares as the type of the expected value
    assertTrue (_check (_a ("assert-eq", "40 + 2"), _out ("42")).isPass ());
    assertTrue (_check (_a ("assert-eq", "43"), _out ("42")).isFail ());
  }

  @Test
  public void testAssertEqTyped ()
  {
    // A typed string is not equal to an integer, even with the same text
    assertEquals (QT3Outcome.fail ("assert-eq"), _check (_a ("assert-eq", "3"), _raw (XdmSequence.of (XdmAtomic.string ("3")))));
    assertTrue (_check (_a ("assert-eq", "3"), _raw (XdmSequence.of (XdmAtomic.integer (3)))).isPass ());
    assertTrue (_check (_a ("assert-eq", "3"), _raw (XdmSequence.of (XdmAtomic.dbl (3.0)))).isPass ());
    assertTrue (_check (_a ("assert-eq", "'3'"), _raw (XdmSequence.of (XdmAtomic.string ("3")))).isPass ());
    // Exactly one item
    assertTrue (_check (_a ("assert-eq", "3"), _raw (XdmSequence.of (XdmAtomic.integer (3), XdmAtomic.integer (3))))
                                                                                                                 .isFail ());
    assertTrue (_check (_a ("assert-eq", "3"), _raw (XdmSequence.EMPTY)).isFail ());
    // The expected expression must be one atomic value
    assertEquals (QT3Outcome.failInconclusive ("assert-eq-eval"),
                  _check (_a ("assert-eq", "1, 2"), _raw (XdmSequence.of (XdmAtomic.integer (1)))));
    assertEquals (QT3Outcome.failInconclusive ("assert-eq-eval"),
                  _check (_a ("assert-eq", "1 +"), _raw (XdmSequence.of (XdmAtomic.integer (1)))));
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
    // An unknown kind is a harness failure
    assertEquals (QT3Outcome.failInconclusive ("harness:unknown:assert-unknown"),
                  _check (_a ("assert-unknown", ""), aNoRaw));
  }

  @Test
  public void testResultDocumentAndMessages () throws Exception
  {
    final IXdmNode aSecTree = _doc ("<second/>");
    final XsltTransformResult aResult = new XsltTransformResult (XdmSequence.EMPTY,
                                                                 null,
                                                                 "",
                                                                 new CommonsArrayList <> (new XsltTransformResult.SecondaryResult ("file:///tmp/out/second.xml",
                                                                                                                                   XdmSequence.of (aSecTree),
                                                                                                                                   aSecTree,
                                                                                                                                   "<second/>")),
                                                                 new CommonsArrayList <> ("<m>hello</m>", "plain"));
    final XSLT30Actual aActual = XSLT30Actual.ofResult (aResult,
                                                        false,
                                                        XSLT30Actual.DEFAULT_RESULT_VAR,
                                                        true,
                                                        "file:///tmp/");
    final XSLT30Assertion aDoc = _a ("assert-result-document", "", "uri", "out/second.xml", _a ("assert", "/second"));
    assertTrue (_check (aDoc, aActual).isPass ());
    final XSLT30Assertion aMissing = _a ("assert-result-document", "", "uri", "third.xml");
    assertEquals ("result-document-missing", _check (aMissing, aActual).reason ());
    // Only the last path segment equal is not the same result
    assertEquals ("result-document-missing", _check (_a ("assert-result-document", "", "uri", "second.xml"), aActual).reason ());
    assertEquals ("result-document-missing",
                  _check (_a ("assert-result-document", "", "uri", "other/second.xml"), aActual).reason ());

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
