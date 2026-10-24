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
 * Status plus a short reason (empty for a pass) of one test case or assertion.
 *
 * @param status
 *        The status.
 * @param reason
 *        A short reason, e.g. the assertion kind that failed or the skip
 *        category. Empty for a pass.
 * @author Philip Helger
 */
public record QT3Outcome (@NonNull EQT3Status status, @NonNull String reason)
{
  private static final QT3Outcome PASS = new QT3Outcome (EQT3Status.PASS, "");

  @NonNull
  public static QT3Outcome pass ()
  {
    return PASS;
  }

  @NonNull
  public static QT3Outcome fail (@NonNull final String sReason)
  {
    return new QT3Outcome (EQT3Status.FAIL, sReason);
  }

  @NonNull
  public static QT3Outcome skip (@NonNull final String sReason)
  {
    return new QT3Outcome (EQT3Status.SKIP, sReason);
  }

  /**
   * @param bPass
   *        Whether the check passed.
   * @param sReason
   *        The fail reason.
   * @return pass or fail
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

  public boolean isSkip ()
  {
    return status == EQT3Status.SKIP;
  }
}
