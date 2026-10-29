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
package com.helger.xpath.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Evaluates FOTS assertions against a test result, a port of Go's
 * <code>checkAssert</code>. Expressions in assertions are evaluated by the
 * engine under test (through {@link IQT3Evaluator}), so an assertion the
 * engine cannot evaluate is a failure, not a skip. An {@link XPathException}
 * while evaluating an assertion fails that assertion; any other
 * {@link Throwable} propagates, and the case runner reports it as a crash.
 * <p>
 * Rules taken over from Go: <code>error</code> passes for any error,
 * whatever its code; every other assertion fails when the test raised an
 * error (except <code>not</code>, which inverts its child).
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3AssertionChecker
{
  /** The variable name of the test result in assertion expressions. */
  public static final XdmQName RESULT_VAR = new XdmQName ("result");

  private static final String ERRORED = "errored";

  private final IQT3Evaluator m_aEvaluator;
  private final Path m_aDir;

  /**
   * @param aEvaluator
   *        Evaluates assertion expressions.
   * @param aDir
   *        Directory of the test set (an assertion's <code>file</code> is
   *        relative to it).
   */
  public QT3AssertionChecker (@NonNull final IQT3Evaluator aEvaluator, @NonNull final Path aDir)
  {
    ValueEnforcer.notNull (aEvaluator, "Evaluator");
    ValueEnforcer.notNull (aDir, "Dir");
    m_aEvaluator = aEvaluator;
    m_aDir = aDir;
  }

  /**
   * Effective boolean value (XPath 3.1 section 2.4.3).
   *
   * @param aSeq
   *        The sequence.
   * @return The EBV, or <code>null</code> if it is a type error (FORG0006).
   */
  @Nullable
  public static Boolean getEffectiveBooleanValue (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return Boolean.FALSE;
    final IXdmItem aFirst = aSeq.getFirst ();
    if (aFirst instanceof IXdmNode)
      return Boolean.TRUE;
    if (!aSeq.isSingleton () || !(aFirst instanceof final XdmAtomic aAtomic))
      return null;
    final EAtomicType eType = aAtomic.getType ();
    if (eType.isDerivedFrom (EAtomicType.BOOLEAN))
      return Boolean.valueOf (aAtomic.getBoolean ());
    if (aAtomic.isStringLike ())
      return Boolean.valueOf (!aAtomic.getString ().isEmpty ());
    if (aAtomic.isNumeric ())
    {
      final double d = aAtomic.getAsDouble ();
      return Boolean.valueOf (d != 0 && !Double.isNaN (d));
    }
    return null;
  }

  /**
   * @param aSeq
   *        An expression result that should be one string.
   * @return The string; the empty string for the empty sequence;
   *         <code>null</code> if it is not a single string-like atomic value.
   */
  @Nullable
  private static String _getString (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return "";
    if (aSeq.isSingleton () && aSeq.getFirst () instanceof final XdmAtomic aAtomic && aAtomic.isStringLike ())
      return aAtomic.getString ();
    return null;
  }

  /** Collapse runs of XML whitespace, like fn:normalize-space. */
  @NonNull
  static String normalizeSpace (@NonNull final String s)
  {
    final String sTrimmed = s.replaceAll ("^[ \\t\\r\\n]+|[ \\t\\r\\n]+$", "");
    return sTrimmed.replaceAll ("[ \\t\\r\\n]+", " ");
  }

  @NonNull
  private Map <XdmQName, XdmSequence> _vars (@Nullable final XdmSequence aResult,
                                              @Nullable final Map <XdmQName, XdmSequence> aMore)
  {
    final ICommonsMap <XdmQName, XdmSequence> ret = new CommonsHashMap <> ();
    if (aResult != null)
      ret.put (RESULT_VAR, aResult);
    if (aMore != null)
      ret.putAll (aMore);
    return ret;
  }

  /**
   * Evaluate a predicate with <code>$result</code> bound.
   *
   * @return The EBV, or <code>null</code> if the engine raised an error or the
   *         EBV is undefined.
   */
  @Nullable
  private Boolean _evalBool (@NonNull final String sExpr, @Nullable final XdmSequence aResult)
  {
    try
    {
      return getEffectiveBooleanValue (m_aEvaluator.evaluate (sExpr, _vars (aResult, null)));
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  /**
   * @return The result of <code>serialize($result)</code> or <code>null</code>
   *         if serialising raised an error.
   */
  @Nullable
  private String _serialize (@NonNull final XdmSequence aResult)
  {
    try
    {
      return _getString (m_aEvaluator.evaluate ("serialize($result)", _vars (aResult, null)));
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  @NonNull
  private QT3Outcome _checkBool (@NonNull final String sExpr,
                                 @NonNull final XdmSequence aResult,
                                 @NonNull final String sKind,
                                 @NonNull final String sEvalFailReason)
  {
    final Boolean aValue = _evalBool (sExpr, aResult);
    if (aValue == null)
      return QT3Outcome.fail (sEvalFailReason);
    return QT3Outcome.of (aValue.booleanValue (), sKind);
  }

  private static boolean _isSingleBoolean (@NonNull final XdmSequence aResult, final boolean bExpected)
  {
    // Equivalent to "$result eq true()" / "$result eq false()": a single
    // boolean; anything else is a type error or a cardinality error
    return aResult.isSingleton () &&
           aResult.getFirst () instanceof final XdmAtomic aAtomic &&
           aAtomic.getType ().isDerivedFrom (EAtomicType.BOOLEAN) &&
           aAtomic.getBoolean () == bExpected;
  }

  /**
   * Check one assertion.
   *
   * @param aAssertion
   *        The assertion.
   * @param aResult
   *        The test result; <code>null</code> if the test raised an error.
   * @param aError
   *        The error the test raised; <code>null</code> if it succeeded.
   * @return The outcome.
   */
  @NonNull
  public QT3Outcome check (@NonNull final QT3Assertion aAssertion,
                           @Nullable final XdmSequence aResult,
                           @Nullable final XPathException aError)
  {
    final String sKind = aAssertion.kind ();
    switch (sKind)
    {
      case "all-of":
        for (final QT3Assertion aChild : aAssertion.children ())
        {
          final QT3Outcome aOutcome = check (aChild, aResult, aError);
          if (!aOutcome.isPass ())
            return aOutcome;
        }
        return QT3Outcome.pass ();
      case "any-of":
      {
        String sAnySkip = null;
        for (final QT3Assertion aChild : aAssertion.children ())
        {
          final QT3Outcome aOutcome = check (aChild, aResult, aError);
          if (aOutcome.isPass ())
            return aOutcome;
          if (aOutcome.isSkip ())
            sAnySkip = aOutcome.reason ();
        }
        return sAnySkip != null ? QT3Outcome.skip (sAnySkip) : QT3Outcome.fail ("any-of");
      }
      case "not":
        if (aAssertion.children ().size () == 1)
        {
          final QT3Outcome aOutcome = check (aAssertion.children ().getFirstOrNull (), aResult, aError);
          if (aOutcome.isSkip ())
            return aOutcome;
          return QT3Outcome.of (!aOutcome.isPass (), sKind);
        }
        return QT3Outcome.skip ("not");
      case "error":
        // Like Go: any error passes, the code is not compared
        return QT3Outcome.of (aError != null, sKind);
      default:
        break;
    }

    if (aError != null || aResult == null)
    {
      // Every remaining assertion needs a result
      switch (sKind)
      {
        case "assert-true":
        case "assert-false":
        case "assert-empty":
        case "assert-count":
        case "assert-type":
        case "assert-eq":
        case "assert-deep-eq":
        case "assert-string-value":
        case "assert":
        case "assert-xml":
        case "assert-serialization":
        case "serialization-matches":
        case "assert-serialization-error":
        case "assert-permutation":
          return QT3Outcome.fail (aError != null ? ERRORED + ":" + aError.getCode () : ERRORED);
        default:
          return QT3Outcome.skip ("unknown:" + sKind);
      }
    }

    switch (sKind)
    {
      case "assert-true":
        return QT3Outcome.of (_isSingleBoolean (aResult, true), sKind);
      case "assert-false":
        return QT3Outcome.of (_isSingleBoolean (aResult, false), sKind);
      case "assert-empty":
        return QT3Outcome.of (aResult.isEmpty (), sKind);
      case "assert-count":
      {
        final int nExpected;
        try
        {
          nExpected = Integer.parseInt (aAssertion.value ().strip ());
        }
        catch (final NumberFormatException ex)
        {
          return QT3Outcome.fail ("count-eval");
        }
        return QT3Outcome.of (aResult.size () == nExpected, sKind);
      }
      case "assert-type":
        return _checkBool ("$result instance of " + aAssertion.value ().strip (), aResult, sKind, "type-parse");
      case "assert-eq":
        return _checkBool ("$result eq (" + aAssertion.value () + ")", aResult, sKind, "eq-eval");
      case "assert-deep-eq":
        return _checkBool ("deep-equal($result, (" + aAssertion.value () + "))", aResult, sKind, "deepeq-eval");
      case "assert":
        return _checkBool (aAssertion.value (), aResult, sKind, "assert-eval");
      case "assert-permutation":
        // Some reordering of $result is deep-equal to the expected sequence
        return _checkBool ("let $e := (" +
                           aAssertion.value () +
                           ") return count($result) eq count($e) and " +
                           "(every $r in $result satisfies " +
                           "count($result[deep-equal(., $r)]) eq count($e[deep-equal(., $r)]))",
                           aResult,
                           sKind,
                           "permutation-eval");
      case "assert-string-value":
      {
        String sGot;
        try
        {
          sGot = _getString (m_aEvaluator.evaluate ("string-join(for $r in $result return string($r), ' ')",
                                                    _vars (aResult, null)));
        }
        catch (final XPathException ex)
        {
          sGot = null;
        }
        if (sGot == null)
          return QT3Outcome.fail ("strval");
        String sWant = aAssertion.value ();
        if ("true".equals (aAssertion.getAttribute ("normalize-space")))
        {
          sGot = normalizeSpace (sGot);
          sWant = normalizeSpace (sWant);
        }
        return QT3Outcome.of (sGot.equals (sWant), sKind);
      }
      case "assert-xml":
      case "assert-serialization":
      {
        // assert-serialization is the old name of assert-xml
        String sWant = aAssertion.value ();
        final String sFile = aAssertion.getAttribute ("file");
        if (!sFile.isEmpty ())
        {
          try
          {
            sWant = Files.readString (m_aDir.resolve (sFile), StandardCharsets.UTF_8);
          }
          catch (final IOException ex)
          {
            return QT3Outcome.fail ("xml-file");
          }
        }
        final String sGot = _serialize (aResult);
        if (sGot == null)
          return QT3Outcome.fail ("serialize");
        return QT3Outcome.of (QT3XmlCompare.isXmlEqual (sWant,
                                                        sGot,
                                                        "true".equals (aAssertion.getAttribute ("ignore-prefixes"))),
                              sKind);
      }
      case "serialization-matches":
      {
        final String sGot = _serialize (aResult);
        if (sGot == null)
          return QT3Outcome.fail ("serialize");
        // The regex is the engine's (XSD/XPath flavour), via fn:matches
        final ICommonsMap <XdmQName, XdmSequence> aMore = new CommonsHashMap <> ();
        aMore.put (new XdmQName ("qt3-s"), XdmSequence.of (XdmAtomic.string (sGot)));
        aMore.put (new XdmQName ("qt3-p"), XdmSequence.of (XdmAtomic.string (aAssertion.value ())));
        aMore.put (new XdmQName ("qt3-f"), XdmSequence.of (XdmAtomic.string (aAssertion.getAttribute ("flags"))));
        final Boolean aMatch;
        try
        {
          aMatch = getEffectiveBooleanValue (m_aEvaluator.evaluate ("matches($qt3-s, $qt3-p, $qt3-f)",
                                                                    _vars (null, aMore)));
        }
        catch (final XPathException ex)
        {
          return QT3Outcome.fail ("regex");
        }
        if (aMatch == null)
          return QT3Outcome.fail ("regex");
        return QT3Outcome.of (aMatch.booleanValue (), sKind);
      }
      case "assert-serialization-error":
        // The test must run without error; only serialising its result fails
        return QT3Outcome.of (_serialize (aResult) == null, sKind);
      default:
        return QT3Outcome.skip ("unknown:" + sKind);
    }
  }
}
