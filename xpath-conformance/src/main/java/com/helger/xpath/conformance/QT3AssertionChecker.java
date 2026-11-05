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
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
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
 * while evaluating an assertion fails that assertion inconclusively; any
 * other {@link Throwable} propagates, and the case runner reports it as a
 * crash.
 * <p>
 * <code>error</code> passes for any error (FOTS: "an implementation is
 * considered to pass a test if the test expects an error and the
 * implementation raises an error, regardless whether the error codes match");
 * with strict error codes the code must match. A code mismatch is reported
 * as {@link QT3Outcome#NOTE_ERROR_CODE} either way. Every other assertion
 * fails inconclusively when the test raised an error.
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
  private final boolean m_bStrictErrorCodes;

  /**
   * Constructor without strict error codes.
   *
   * @param aEvaluator
   *        Evaluates assertion expressions.
   * @param aDir
   *        Directory of the test set (an assertion's <code>file</code> is
   *        relative to it).
   */
  public QT3AssertionChecker (@NonNull final IQT3Evaluator aEvaluator, @NonNull final Path aDir)
  {
    this (aEvaluator, aDir, false);
  }

  /**
   * @param aEvaluator
   *        Evaluates assertion expressions.
   * @param aDir
   *        Directory of the test set (an assertion's <code>file</code> is
   *        relative to it).
   * @param bStrictErrorCodes
   *        <code>true</code> to fail <code>error</code> and
   *        <code>assert-serialization-error</code> when the error code differs
   *        (<code>qt3.strictCode</code>).
   */
  public QT3AssertionChecker (@NonNull final IQT3Evaluator aEvaluator,
                              @NonNull final Path aDir,
                              final boolean bStrictErrorCodes)
  {
    ValueEnforcer.notNull (aEvaluator, "Evaluator");
    ValueEnforcer.notNull (aDir, "Dir");
    m_aEvaluator = aEvaluator;
    m_aDir = aDir;
    m_bStrictErrorCodes = bStrictErrorCodes;
  }

  /**
   * Compare an expected error code of the catalogue with a raised error.
   *
   * @param sExpected
   *        The <code>code</code> attribute: empty or <code>*</code> for any
   *        code, <code>Q{uri}local</code> for a code in any namespace, else
   *        the local name of a code in the standard error namespace
   *        {@link XPathException#ERR_NAMESPACE}.
   * @param aError
   *        The raised error.
   * @return <code>true</code> if the error has the expected code.
   */
  public static boolean isErrorCodeMatching (@NonNull final String sExpected, @NonNull final XPathException aError)
  {
    final String sWant = sExpected.strip ();
    if (sWant.isEmpty () || "*".equals (sWant))
      return true;
    final XdmQName aGot = aError.getCodeName ();
    if (sWant.startsWith ("Q{"))
    {
      final int nEnd = sWant.indexOf ('}');
      return nEnd > 0 &&
             sWant.substring (2, nEnd).equals (aGot.namespaceUri ()) &&
             sWant.substring (nEnd + 1).equals (aGot.localName ());
    }
    return XPathException.ERR_NAMESPACE.equals (aGot.namespaceUri ()) && sWant.equals (aGot.localName ());
  }

  /**
   * @param aError
   *        A raised error.
   * @return <code>true</code> if it is a serialization error: a code in the
   *         standard error namespace starting with <code>SE</code>.
   */
  public static boolean isSerializationError (@NonNull final XPathException aError)
  {
    final XdmQName aCode = aError.getCodeName ();
    return XPathException.ERR_NAMESPACE.equals (aCode.namespaceUri ()) && aCode.localName ().startsWith ("SE");
  }

  /**
   * Judge an error raised where an error was expected.
   *
   * @param sExpectedCode
   *        The <code>code</code> attribute of the assertion.
   * @param aError
   *        The raised error.
   * @param bStrictErrorCodes
   *        <code>true</code> to fail on another code.
   * @return Pass if the code matches; otherwise a fail
   *         {@link QT3Outcome#NOTE_ERROR_CODE} with strict codes, else a pass
   *         with that note.
   */
  @NonNull
  public static QT3Outcome checkErrorCode (@NonNull final String sExpectedCode,
                                          @NonNull final XPathException aError,
                                          final boolean bStrictErrorCodes)
  {
    if (isErrorCodeMatching (sExpectedCode, aError))
      return QT3Outcome.pass ();
    return bStrictErrorCodes ? QT3Outcome.fail (QT3Outcome.NOTE_ERROR_CODE)
                             : QT3Outcome.passWithNote (QT3Outcome.NOTE_ERROR_CODE);
  }

  /**
   * Combine the outcomes of the children of <code>any-of</code>: the first
   * pass wins; else a skip if every child was skipped; else a fail that is
   * conclusive only if every child failed conclusively (a skipped child among
   * failing ones never turns the result into a skip).
   *
   * @param aOutcomes
   *        The outcomes of the children, in order.
   * @return The outcome of <code>any-of</code>.
   */
  @NonNull
  public static QT3Outcome combineAnyOf (@NonNull final Iterable <QT3Outcome> aOutcomes)
  {
    boolean bAllSkipped = true;
    boolean bAllConclusive = true;
    String sSkip = null;
    for (final QT3Outcome aOutcome : aOutcomes)
    {
      if (aOutcome.isPass ())
        return aOutcome;
      if (aOutcome.isSkip ())
      {
        if (sSkip == null)
          sSkip = aOutcome.reason ();
        bAllConclusive = false;
      }
      else
      {
        bAllSkipped = false;
        if (!aOutcome.isConclusiveFail ())
          bAllConclusive = false;
      }
    }
    if (bAllSkipped && sSkip != null)
      return QT3Outcome.skip (sSkip);
    return bAllConclusive ? QT3Outcome.fail ("any-of") : QT3Outcome.failInconclusive ("any-of");
  }

  /**
   * Judge <code>not</code> from the outcome of its child: it passes only if
   * the child was evaluated and is false (a conclusive fail). A passing
   * child fails it conclusively; a skipped child or one that could not be
   * evaluated (the test raised an error, an expression did not evaluate, a
   * harness problem) fails it inconclusively.
   *
   * @param aChild
   *        The outcome of the single child.
   * @return The outcome of <code>not</code>.
   */
  @NonNull
  public static QT3Outcome negate (@NonNull final QT3Outcome aChild)
  {
    if (aChild.isConclusiveFail ())
      return QT3Outcome.pass ();
    if (aChild.isPass ())
      return QT3Outcome.fail ("not");
    return QT3Outcome.failInconclusive ("not:" + aChild.reason ());
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

  /**
   * @param aResult
   *        The test result, bound as <code>$result</code> if not
   *        <code>null</code>.
   * @param aMore
   *        Further variables, may be <code>null</code>.
   * @return The extra variables for one assertion evaluation.
   */
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
   * @param aResult
   *        The test result.
   * @param aErrorOut
   *        Receives the error if serialising raised one; may be
   *        <code>null</code>.
   * @return The result of <code>serialize($result)</code> or <code>null</code>
   *         if serialising raised an error.
   */
  @Nullable
  private String _serialize (@NonNull final XdmSequence aResult, @Nullable final Wrapper <XPathException> aErrorOut)
  {
    try
    {
      return _getString (m_aEvaluator.evaluate ("serialize($result)", _vars (aResult, null)));
    }
    catch (final XPathException ex)
    {
      if (aErrorOut != null)
        aErrorOut.set (ex);
      return null;
    }
  }

  /**
   * Evaluate an assertion expression with <code>$result</code> bound and
   * judge it by its effective boolean value.
   *
   * @param sExpr
   *        The expression.
   * @param aResult
   *        The test result.
   * @param sKind
   *        Fail reason if the value is false.
   * @param sEvalFailReason
   *        Fail reason if the evaluation raised an error or the EBV is
   *        undefined.
   * @return The outcome; an inconclusive fail if the expression did not
   *         evaluate.
   */
  @NonNull
  private QT3Outcome _checkBool (@NonNull final String sExpr,
                                 @NonNull final XdmSequence aResult,
                                 @NonNull final String sKind,
                                 @NonNull final String sEvalFailReason)
  {
    final Boolean aValue = _evalBool (sExpr, aResult);
    if (aValue == null)
      return QT3Outcome.failInconclusive (sEvalFailReason);
    return QT3Outcome.of (aValue.booleanValue (), sKind);
  }

  /**
   * @param aResult
   *        The test result.
   * @param bExpected
   *        The expected boolean.
   * @return <code>true</code> if the result is exactly one
   *         <code>xs:boolean</code> (or derived) with the expected value; no
   *         effective boolean value is taken.
   */
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
   * Check one assertion (FOTS catalogue schema). <code>all-of</code> passes
   * if every child passes and otherwise returns the first non-pass;
   * <code>any-of</code> see {@link #combineAnyOf}; <code>not</code> see
   * {@link #negate} (another number of children than one is a harness
   * fail); <code>error</code> passes for any error, see
   * {@link #checkErrorCode}. With an error, every other known assertion fails
   * inconclusively with <code>errored:CODE</code>. Otherwise:
   * <ul>
   * <li><code>assert-true</code>/<code>assert-false</code>: exactly one
   * boolean with that value.</li>
   * <li><code>assert-empty</code>, <code>assert-count</code> (the trimmed
   * text as integer): the item count.</li>
   * <li><code>assert-type</code>: <code>$result instance of T</code>.</li>
   * <li><code>assert-eq</code>: <code>$result eq (V)</code>;
   * <code>assert-deep-eq</code>: <code>deep-equal($result, (V))</code>;
   * <code>assert</code>: the EBV of the expression;
   * <code>assert-permutation</code>: the same items as <code>(V)</code> by
   * <code>deep-equal</code> with the same multiplicities, in any order.</li>
   * <li><code>assert-string-value</code>: the string values joined with a
   * space must equal the text exactly, or after normalising whitespace on
   * both sides with <code>normalize-space="true"</code>.</li>
   * <li><code>assert-xml</code> (and its old name
   * <code>assert-serialization</code>): <code>serialize($result)</code>
   * compared with the text or the <code>file</code> by
   * {@link QT3XmlCompare#isXmlEqual}.</li>
   * <li><code>serialization-matches</code>: <code>fn:matches</code> of the
   * serialisation with the pattern and <code>flags</code> (unanchored).</li>
   * <li><code>assert-serialization-error</code>: serialising the result
   * raises a serialization error (a code <code>SE*</code>); its code is
   * judged like that of <code>error</code>.</li>
   * </ul>
   * The serialisation always uses the default parameters of
   * <code>fn:serialize</code>. An expression that does not evaluate fails
   * inconclusively. An unknown assertion kind is an inconclusive fail
   * <code>harness:unknown:kind</code>.
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
        final ICommonsList <QT3Outcome> aOutcomes = new CommonsArrayList <> ();
        for (final QT3Assertion aChild : aAssertion.children ())
        {
          final QT3Outcome aOutcome = check (aChild, aResult, aError);
          aOutcomes.add (aOutcome);
          if (aOutcome.isPass ())
            break;
        }
        return combineAnyOf (aOutcomes);
      }
      case "not":
        if (aAssertion.children ().size () == 1)
          return negate (check (aAssertion.children ().getFirstOrNull (), aResult, aError));
        return QT3Outcome.failInconclusive ("harness:not-children");
      case "error":
        if (aError == null)
          return QT3Outcome.fail (sKind);
        return checkErrorCode (aAssertion.getAttribute ("code"), aError, m_bStrictErrorCodes);
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
          return QT3Outcome.failInconclusive (aError != null ? ERRORED + ":" + aError.getCode () : ERRORED);
        default:
          return QT3Outcome.failInconclusive ("harness:unknown:" + sKind);
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
          return QT3Outcome.failInconclusive ("count-eval");
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
          return QT3Outcome.failInconclusive ("strval");
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
            return QT3Outcome.failInconclusive ("xml-file");
          }
        }
        final String sGot = _serialize (aResult, null);
        if (sGot == null)
          return QT3Outcome.failInconclusive ("serialize");
        return QT3Outcome.of (QT3XmlCompare.isXmlEqual (sWant,
                                                        sGot,
                                                        "true".equals (aAssertion.getAttribute ("ignore-prefixes"))),
                              sKind);
      }
      case "serialization-matches":
      {
        final String sGot = _serialize (aResult, null);
        if (sGot == null)
          return QT3Outcome.failInconclusive ("serialize");
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
          return QT3Outcome.failInconclusive ("regex");
        }
        if (aMatch == null)
          return QT3Outcome.failInconclusive ("regex");
        return QT3Outcome.of (aMatch.booleanValue (), sKind);
      }
      case "assert-serialization-error":
      {
        // The test must run without error; only serialising its result fails
        final Wrapper <XPathException> aSerError = new Wrapper <> ();
        if (_serialize (aResult, aSerError) != null || aSerError.isNotSet ())
          return QT3Outcome.fail (sKind);
        if (!isSerializationError (aSerError.get ()))
          return QT3Outcome.fail ("serialization-error-code");
        return checkErrorCode (aAssertion.getAttribute ("code"), aSerError.get (), m_bStrictErrorCodes);
      }
      default:
        return QT3Outcome.failInconclusive ("harness:unknown:" + sKind);
    }
  }
}
