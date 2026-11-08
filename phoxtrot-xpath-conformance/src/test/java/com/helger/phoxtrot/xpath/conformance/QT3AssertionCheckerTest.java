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
package com.helger.phoxtrot.xpath.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for class {@link QT3AssertionChecker}, with a fake evaluator in
 * place of the engine.
 *
 * @author Philip Helger
 */
public final class QT3AssertionCheckerTest
{
  private static final XdmSequence TRUE = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE = XdmSequence.of (XdmAtomic.FALSE);
  private static final XPathException ERROR = new XPathException ("FOAR0001", "Division by zero");

  /** Fake engine: answers fixed expressions, fails on anything else. */
  private static class FakeEvaluator implements IQT3Evaluator
  {
    private final ICommonsOrderedMap <String, XdmSequence> m_aAnswers = new CommonsLinkedHashMap <> ();
    private final ICommonsList <String> m_aSeen = new CommonsArrayList <> ();
    private Map <XdmQName, XdmSequence> m_aLastVars;

    FakeEvaluator answer (final String sExpr, final XdmSequence aResult)
    {
      m_aAnswers.put (sExpr, aResult);
      return this;
    }

    public XdmSequence evaluate (final String sExpr, final Map <XdmQName, XdmSequence> aExtraVars)
    {
      m_aSeen.add (sExpr);
      m_aLastVars = aExtraVars;
      final XdmSequence ret = m_aAnswers.get (sExpr);
      if (ret == null)
        throw new XPathException ("XPST0003", "Fake cannot evaluate " + sExpr);
      return ret;
    }
  }

  private static QT3Assertion _a (final String sKind, final String sValue, final QT3Assertion... aChildren)
  {
    return new QT3Assertion (sKind, new CommonsLinkedHashMap <> (), sValue, new CommonsArrayList <> (aChildren));
  }

  private static QT3Assertion _a (final String sKind,
                                  final String sValue,
                                  final String sAttrName,
                                  final String sAttrValue)
  {
    final ICommonsOrderedMap <String, String> aAttrs = new CommonsLinkedHashMap <> ();
    aAttrs.put (sAttrName, sAttrValue);
    return new QT3Assertion (sKind, aAttrs, sValue, new CommonsArrayList <> ());
  }

  private static QT3AssertionChecker _checker (final IQT3Evaluator aEval)
  {
    return new QT3AssertionChecker (aEval, Path.of ("."));
  }

  private static XdmSequence _str (final String s)
  {
    return XdmSequence.of (XdmAtomic.string (s));
  }

  @Test
  public void testEffectiveBooleanValue ()
  {
    assertSame (Boolean.FALSE, QT3AssertionChecker.getEffectiveBooleanValue (XdmSequence.EMPTY));
    assertSame (Boolean.TRUE, QT3AssertionChecker.getEffectiveBooleanValue (TRUE));
    assertSame (Boolean.FALSE, QT3AssertionChecker.getEffectiveBooleanValue (FALSE));
    assertSame (Boolean.TRUE, QT3AssertionChecker.getEffectiveBooleanValue (_str ("a")));
    assertSame (Boolean.FALSE, QT3AssertionChecker.getEffectiveBooleanValue (_str ("")));
    assertSame (Boolean.TRUE, QT3AssertionChecker.getEffectiveBooleanValue (XdmSequence.of (XdmAtomic.integer (3))));
    assertSame (Boolean.FALSE, QT3AssertionChecker.getEffectiveBooleanValue (XdmSequence.of (XdmAtomic.integer (0))));
    assertSame (Boolean.FALSE,
                QT3AssertionChecker.getEffectiveBooleanValue (XdmSequence.of (XdmAtomic.dbl (Double.NaN))));
    final IXdmNode aText = IXdmNode.createText ("x");
    assertSame (Boolean.TRUE, QT3AssertionChecker.getEffectiveBooleanValue (XdmSequence.of (aText, XdmAtomic.TRUE)));
    // Two atomic values: FORG0006
    assertNull (QT3AssertionChecker.getEffectiveBooleanValue (XdmSequence.of (XdmAtomic.TRUE, XdmAtomic.TRUE)));
  }

  @Test
  public void testError ()
  {
    final QT3AssertionChecker aChecker = _checker (new FakeEvaluator ());
    // FOTS: any error passes, a wrong code is only noted
    assertEquals (QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE),
                  aChecker.check (_a ("error", "", "code", "XPTY0004"), null, ERROR));
    assertSame (QT3Outcome.pass (), aChecker.check (_a ("error", "", "code", "FOAR0001"), null, ERROR));
    assertSame (QT3Outcome.pass (), aChecker.check (_a ("error", "", "code", "*"), null, ERROR));
    assertEquals (QT3Outcome.fail ("error"), aChecker.check (_a ("error", "", "code", "FOAR0001"), TRUE, null));
    // Every value assertion fails inconclusively when the test raised an error
    assertEquals (QT3Outcome.failInconclusive ("errored:FOAR0001"),
                  aChecker.check (_a ("assert-true", ""), null, ERROR));
    assertEquals (QT3Outcome.failInconclusive ("errored:FOAR0001"),
                  aChecker.check (_a ("assert-eq", "1"), null, ERROR));
    // An unknown kind is a harness failure, not a skip
    assertEquals (QT3Outcome.failInconclusive ("harness:unknown:assert-foo"),
                  aChecker.check (_a ("assert-foo", ""), null, ERROR));
    assertEquals (QT3Outcome.failInconclusive ("harness:unknown:assert-foo"),
                  aChecker.check (_a ("assert-foo", ""), TRUE, null));
  }

  @Test
  public void testStrictErrorCode ()
  {
    final QT3AssertionChecker aStrict = new QT3AssertionChecker (new FakeEvaluator (), Path.of ("."), true);
    assertSame (QT3Outcome.pass (), aStrict.check (_a ("error", "", "code", "FOAR0001"), null, ERROR));
    assertSame (QT3Outcome.pass (), aStrict.check (_a ("error", "", "code", "*"), null, ERROR));
    assertEquals (QT3Outcome.fail (QT3Outcome.NOTE_ERROR_CODE),
                  aStrict.check (_a ("error", "", "code", "XPTY0004"), null, ERROR));
    // any-of(error XPTY0004, assert-true) on FOAR0001: no child passes
    assertTrue (aStrict.check (_a ("any-of", "", _a ("error", "", "code", "XPTY0004"), _a ("assert-true", "")),
                               null,
                               ERROR)
                       .isFail ());
  }

  @Test
  public void testErrorCodeMatching ()
  {
    assertTrue (QT3AssertionChecker.isErrorCodeMatching ("", ERROR));
    assertTrue (QT3AssertionChecker.isErrorCodeMatching (" * ", ERROR));
    assertTrue (QT3AssertionChecker.isErrorCodeMatching ("FOAR0001", ERROR));
    assertTrue (QT3AssertionChecker.isErrorCodeMatching ("Q{http://www.w3.org/2005/xqt-errors}FOAR0001", ERROR));
    assertFalse (QT3AssertionChecker.isErrorCodeMatching ("FOAR0002", ERROR));
    assertFalse (QT3AssertionChecker.isErrorCodeMatching ("Q{}FOAR0001", ERROR));
    final XPathException aUser = new XPathException (new XdmQName ("urn:x", "E1", "x"), "user", null);
    assertTrue (QT3AssertionChecker.isErrorCodeMatching ("Q{urn:x}E1", aUser));
    assertFalse (QT3AssertionChecker.isErrorCodeMatching ("E1", aUser));
    assertTrue (QT3AssertionChecker.isSerializationError (new XPathException ("SEPM0004", "x")));
    assertFalse (QT3AssertionChecker.isSerializationError (ERROR));
  }

  @Test
  public void testNativeAssertions ()
  {
    final QT3AssertionChecker aChecker = _checker (new FakeEvaluator ());
    assertTrue (aChecker.check (_a ("assert-true", ""), TRUE, null).isPass ());
    assertEquals (QT3Outcome.fail ("assert-true"), aChecker.check (_a ("assert-true", ""), FALSE, null));
    // "$result eq true()" is a type error for a string
    assertEquals (QT3Outcome.fail ("assert-true"), aChecker.check (_a ("assert-true", ""), _str ("true"), null));
    assertTrue (aChecker.check (_a ("assert-false", ""), FALSE, null).isPass ());
    assertEquals (QT3Outcome.fail ("assert-false"),
                  aChecker.check (_a ("assert-false", ""), XdmSequence.of (XdmAtomic.FALSE, XdmAtomic.FALSE), null));
    assertTrue (aChecker.check (_a ("assert-empty", ""), XdmSequence.EMPTY, null).isPass ());
    assertEquals (QT3Outcome.fail ("assert-empty"), aChecker.check (_a ("assert-empty", ""), TRUE, null));
    assertTrue (aChecker.check (_a ("assert-count", " 2 "), XdmSequence.of (XdmAtomic.TRUE, XdmAtomic.FALSE), null)
                        .isPass ());
    assertEquals (QT3Outcome.fail ("assert-count"), aChecker.check (_a ("assert-count", "1"), XdmSequence.EMPTY, null));
    assertEquals (QT3Outcome.failInconclusive ("count-eval"),
                  aChecker.check (_a ("assert-count", "x"), XdmSequence.EMPTY, null));
  }

  @Test
  public void testEngineAssertions ()
  {
    final XdmSequence aResult = XdmSequence.of (XdmAtomic.integer (2));
    final FakeEvaluator aEval = new FakeEvaluator ().answer ("$result eq (2)", TRUE)
                                                    .answer ("$result eq (3)", FALSE)
                                                    .answer ("deep-equal($result, (2))", TRUE)
                                                    .answer ("$result instance of xs:integer", TRUE)
                                                    .answer ("$result instance of xs:string", FALSE)
                                                    .answer ("$result = 2", TRUE)
                                                    .answer ("string-join(for $r in $result return string($r), ' ')",
                                                             _str ("  2 \n x "));
    final QT3AssertionChecker aChecker = _checker (aEval);
    assertTrue (aChecker.check (_a ("assert-eq", "2"), aResult, null).isPass ());
    // $result is bound for the engine
    assertSame (aResult, aEval.m_aLastVars.get (QT3AssertionChecker.RESULT_VAR));
    assertEquals (QT3Outcome.fail ("assert-eq"), aChecker.check (_a ("assert-eq", "3"), aResult, null));
    assertEquals (QT3Outcome.failInconclusive ("eq-eval"), aChecker.check (_a ("assert-eq", "4"), aResult, null));
    assertTrue (aChecker.check (_a ("assert-deep-eq", "2"), aResult, null).isPass ());
    assertTrue (aChecker.check (_a ("assert-type", " xs:integer\n"), aResult, null).isPass ());
    assertEquals (QT3Outcome.fail ("assert-type"), aChecker.check (_a ("assert-type", "xs:string"), aResult, null));
    assertEquals (QT3Outcome.failInconclusive ("type-parse"),
                  aChecker.check (_a ("assert-type", "xs:foo"), aResult, null));
    assertTrue (aChecker.check (_a ("assert", "$result = 2"), aResult, null).isPass ());
    assertEquals (QT3Outcome.failInconclusive ("assert-eval"),
                  aChecker.check (_a ("assert", "$result = 3"), aResult, null));

    assertEquals (QT3Outcome.fail ("assert-string-value"),
                  aChecker.check (_a ("assert-string-value", "2 x"), aResult, null));
    assertTrue (aChecker.check (_a ("assert-string-value", " 2  x", "normalize-space", "true"), aResult, null)
                        .isPass ());
    assertTrue (aChecker.check (_a ("assert-string-value", "  2 \n x "), aResult, null).isPass ());
  }

  @Test
  public void testCombinators ()
  {
    final QT3AssertionChecker aChecker = _checker (new FakeEvaluator ());
    final QT3Assertion aTrue = _a ("assert-true", "");
    final QT3Assertion aEmpty = _a ("assert-empty", "");
    final QT3Assertion aUnknown = _a ("assert-foo", "");
    assertTrue (aChecker.check (_a ("all-of", "", aTrue, _a ("assert-count", "1")), TRUE, null).isPass ());
    assertEquals (QT3Outcome.fail ("assert-empty"), aChecker.check (_a ("all-of", "", aTrue, aEmpty), TRUE, null));
    assertTrue (aChecker.check (_a ("any-of", "", aEmpty, aTrue), TRUE, null).isPass ());
    assertEquals (QT3Outcome.fail ("any-of"), aChecker.check (_a ("any-of", "", aEmpty), TRUE, null));
    // A child that cannot be evaluated does not turn a failing any-of into a
    // skip
    assertEquals (QT3Outcome.failInconclusive ("any-of"),
                  aChecker.check (_a ("any-of", "", aEmpty, aUnknown), TRUE, null));
    assertTrue (aChecker.check (_a ("any-of", "", aTrue, _a ("error", "")), null, ERROR).isPass ());
    assertTrue (aChecker.check (_a ("not", "", aEmpty), TRUE, null).isPass ());
    assertEquals (QT3Outcome.fail ("not"), aChecker.check (_a ("not", "", aTrue), TRUE, null));
    // not(error) passes when the test succeeds
    assertTrue (aChecker.check (_a ("not", "", _a ("error", "")), TRUE, null).isPass ());
    // not(assert-true) does not pass because the test raised an error
    assertEquals (QT3Outcome.failInconclusive ("not:errored:FOAR0001"),
                  aChecker.check (_a ("not", "", aTrue), null, ERROR));
    // not over an assertion that cannot be evaluated
    assertEquals (QT3Outcome.failInconclusive ("not:assert-eval"),
                  aChecker.check (_a ("not", "", _a ("assert", "unknown()")), TRUE, null));
    assertEquals (QT3Outcome.failInconclusive ("not:harness:unknown:assert-foo"),
                  aChecker.check (_a ("not", "", aUnknown), TRUE, null));
    assertEquals (QT3Outcome.failInconclusive ("harness:not-children"), aChecker.check (_a ("not", ""), TRUE, null));
  }

  @Test
  public void testCombineAnyOf ()
  {
    final QT3Outcome aSkip = QT3Outcome.skip ("s");
    final QT3Outcome aFail = QT3Outcome.fail ("f");
    // Only skipped children: a skip
    assertEquals (aSkip, QT3AssertionChecker.combineAnyOf (List.of (aSkip, QT3Outcome.skip ("t"))));
    // A skipped child among failing ones: a fail, never a skip
    assertEquals (QT3Outcome.failInconclusive ("any-of"), QT3AssertionChecker.combineAnyOf (List.of (aSkip, aFail)));
    assertEquals (QT3Outcome.fail ("any-of"), QT3AssertionChecker.combineAnyOf (List.of (aFail, aFail)));
    assertSame (QT3Outcome.pass (), QT3AssertionChecker.combineAnyOf (List.of (aSkip, aFail, QT3Outcome.pass ())));
  }

  @Test
  public void testNegate ()
  {
    assertSame (QT3Outcome.pass (), QT3AssertionChecker.negate (QT3Outcome.fail ("x")));
    assertEquals (QT3Outcome.fail ("not"), QT3AssertionChecker.negate (QT3Outcome.pass ()));
    assertEquals (QT3Outcome.fail ("not"),
                  QT3AssertionChecker.negate (QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE)));
    assertEquals (QT3Outcome.failInconclusive ("not:s"), QT3AssertionChecker.negate (QT3Outcome.skip ("s")));
    assertEquals (QT3Outcome.failInconclusive ("not:e"),
                  QT3AssertionChecker.negate (QT3Outcome.failInconclusive ("e")));
  }

  @Test
  public void testSerializationAssertions ()
  {
    final XdmSequence aResult = XdmSequence.of (IXdmNode.createText ("ignored by the fake"));
    final FakeEvaluator aEval = new FakeEvaluator ().answer ("serialize($result)",
                                                             _str ("<a xmlns:p='urn:p' p:x='1' y='2'>t</a>"))
                                                    .answer ("matches($qt3-s, $qt3-p, $qt3-f)", TRUE);
    final QT3AssertionChecker aChecker = _checker (aEval);
    assertTrue (aChecker.check (_a ("assert-xml", "<a y=\"2\" xmlns:p='urn:p' p:x=\"1\">t</a>"), aResult, null)
                        .isPass ());
    assertTrue (aChecker.check (_a ("assert-serialization", "<a y='2' xmlns:p='urn:p' p:x='1'>t</a>"), aResult, null)
                        .isPass ());
    assertEquals (QT3Outcome.fail ("assert-xml"),
                  aChecker.check (_a ("assert-xml", "<a y='2' xmlns:q='urn:p' q:x='1'>t</a>"), aResult, null));
    assertTrue (aChecker.check (_a ("assert-xml",
                                    "<a y='2' xmlns:q='urn:p' q:x='1'>t</a>",
                                    "ignore-prefixes",
                                    "true"),
                                aResult,
                                null)
                        .isPass ());
    assertEquals (QT3Outcome.failInconclusive ("xml-file"),
                  aChecker.check (_a ("assert-xml", "", "file", "does-not-exist.xml"), aResult, null));

    assertTrue (aChecker.check (_a ("serialization-matches", "^<a"), aResult, null).isPass ());
    assertEquals ("^<a", ((XdmAtomic) aEval.m_aLastVars.get (new XdmQName ("qt3-p")).getFirst ()).getString ());
    // Serialising succeeds, so assert-serialization-error fails
    assertEquals (QT3Outcome.fail ("assert-serialization-error"),
                  aChecker.check (_a ("assert-serialization-error", "", "code", "SEPM0004"), aResult, null));

    // An engine whose serialisation raises a non-serialization error
    final QT3AssertionChecker aNoSer = _checker (new FakeEvaluator ());
    assertEquals (QT3Outcome.fail ("serialization-error-code"),
                  aNoSer.check (_a ("assert-serialization-error", "", "code", "SEPM0004"), aResult, null));
    assertEquals (QT3Outcome.failInconclusive ("serialize"), aNoSer.check (_a ("assert-xml", "<a/>"), aResult, null));
    assertEquals (QT3Outcome.failInconclusive ("serialize"),
                  aNoSer.check (_a ("serialization-matches", "a"), aResult, null));

    // An engine whose serialisation raises a serialization error
    final FakeEvaluator aSerErr = new FakeEvaluator ()
    {
      @Override
      public XdmSequence evaluate (final String sExpr, final Map <XdmQName, XdmSequence> aExtraVars)
      {
        if ("serialize($result)".equals (sExpr))
          throw new XPathException ("SEPM0004", "Not serialisable");
        return super.evaluate (sExpr, aExtraVars);
      }
    };
    assertSame (QT3Outcome.pass (),
                _checker (aSerErr).check (_a ("assert-serialization-error", "", "code", "SEPM0004"), aResult, null));
    assertEquals (QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE),
                  _checker (aSerErr).check (_a ("assert-serialization-error", "", "code", "SERE0014"), aResult, null));
    assertEquals (QT3Outcome.fail (QT3Outcome.NOTE_ERROR_CODE),
                  new QT3AssertionChecker (aSerErr, Path.of ("."), true).check (_a ("assert-serialization-error",
                                                                                    "",
                                                                                    "code",
                                                                                    "SERE0014"),
                                                                                aResult,
                                                                                null));
  }

  @Test
  public void testPermutation ()
  {
    final FakeEvaluator aEval = new FakeEvaluator ();
    final QT3AssertionChecker aChecker = _checker (aEval);
    assertEquals (QT3Outcome.failInconclusive ("permutation-eval"),
                  aChecker.check (_a ("assert-permutation", "1, 2"), XdmSequence.EMPTY, null));
    assertTrue (aEval.m_aSeen.getLastOrNull ().startsWith ("let $e := (1, 2) return count($result) eq count($e)"));
  }

  @Test
  public void testNormalizeSpace ()
  {
    assertEquals ("a b c", QT3AssertionChecker.normalizeSpace ("\n a \t b\r\nc  "));
    assertEquals ("", QT3AssertionChecker.normalizeSpace ("   "));
  }
}
