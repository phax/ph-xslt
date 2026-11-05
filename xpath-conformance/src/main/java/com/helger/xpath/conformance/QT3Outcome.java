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

import org.jspecify.annotations.NonNull;

/**
 * Status plus a short reason of one test case or assertion. A fail is either
 * conclusive (the assertion was evaluated and does not hold) or inconclusive
 * (it could not be evaluated: the test raised an error, the expected value
 * did not evaluate, the harness could not set the case up). Only a conclusive
 * fail makes a <code>not</code> assertion pass.
 *
 * @param status
 *        The status.
 * @param reason
 *        A short reason, e.g. the assertion kind that failed or the skip
 *        category. Empty for a plain pass; a pass may carry a note (e.g.
 *        {@link #NOTE_ERROR_CODE} for an expected error raised with another
 *        code).
 * @param conclusive
 *        For a fail: <code>true</code> if the assertion was evaluated and is
 *        false, <code>false</code> if it could not be evaluated. Always
 *        <code>true</code> for a pass or skip.
 * @author Philip Helger
 */
public record QT3Outcome (@NonNull EQT3Status status, @NonNull String reason, boolean conclusive)
{
  /**
   * The reason of an expected error raised with another error code: a fail
   * with strict error codes, otherwise a note on a pass.
   */
  public static final String NOTE_ERROR_CODE = "error-code";

  private static final QT3Outcome PASS = new QT3Outcome (EQT3Status.PASS, "", true);

  @NonNull
  public static QT3Outcome pass ()
  {
    return PASS;
  }

  /**
   * @param sNote
   *        The note, e.g. {@link #NOTE_ERROR_CODE}.
   * @return A pass that carries a note in its reason.
   */
  @NonNull
  public static QT3Outcome passWithNote (@NonNull final String sNote)
  {
    return new QT3Outcome (EQT3Status.PASS, sNote, true);
  }

  /**
   * @param sReason
   *        The fail reason.
   * @return A conclusive fail: the assertion was evaluated and is false.
   */
  @NonNull
  public static QT3Outcome fail (@NonNull final String sReason)
  {
    return new QT3Outcome (EQT3Status.FAIL, sReason, true);
  }

  /**
   * @param sReason
   *        The fail reason.
   * @return An inconclusive fail: the assertion could not be evaluated.
   */
  @NonNull
  public static QT3Outcome failInconclusive (@NonNull final String sReason)
  {
    return new QT3Outcome (EQT3Status.FAIL, sReason, false);
  }

  @NonNull
  public static QT3Outcome skip (@NonNull final String sReason)
  {
    return new QT3Outcome (EQT3Status.SKIP, sReason, true);
  }

  /**
   * @param bPass
   *        Whether the check passed.
   * @param sReason
   *        The fail reason.
   * @return pass or conclusive fail
   */
  @NonNull
  public static QT3Outcome of (final boolean bPass, @NonNull final String sReason)
  {
    return bPass ? PASS : fail (sReason);
  }

  public boolean isPass ()
  {
    return status == EQT3Status.PASS;
  }

  public boolean isFail ()
  {
    return status == EQT3Status.FAIL;
  }

  /**
   * @return <code>true</code> for a fail of an assertion that was evaluated
   *         and is false.
   */
  public boolean isConclusiveFail ()
  {
    return status == EQT3Status.FAIL && conclusive;
  }

  public boolean isSkip ()
  {
    return status == EQT3Status.SKIP;
  }
}
