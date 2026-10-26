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
package com.helger.xsd.conformance;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * The suite's selection rules, ported from the Go harness
 * (<code>expectedFor</code>, <code>versionApplies</code>,
 * <code>isQueried</code>).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdSuiteRules
{
  /** The verdict of a test the suite expects to be valid. */
  public static final String VALID = "valid";
  /** The verdict of a test the suite expects to be invalid. */
  public static final String INVALID = "invalid";
  /** The verdict of a test whose outcome the suite leaves open. */
  public static final String INDETERMINATE = "indeterminate";
  /** The status of a W3C-disputed test. */
  public static final String STATUS_QUERIED = "queried";

  @PresentForCodeCoverage
  private static final XsdSuiteRules INSTANCE = new XsdSuiteRules ();

  private XsdSuiteRules ()
  {}

  /**
   * The suite's version-token semantics (<code>xsts.xsd</code>): an absent
   * attribute applies everywhere; otherwise the whitespace-separated token
   * list must contain the run version. Alien tokens (e.g.
   * <code>Unicode_4.0.0</code>) match neither version. Used at test-set, test
   * group, schema test and instance test level.
   *
   * @param sAttr
   *        The <code>version</code> attribute, empty if absent.
   * @param eVersion
   *        The run version.
   * @return <code>true</code> if the element applies to the run version.
   */
  public static boolean isVersionApplicable (@NonNull final String sAttr, @NonNull final EXsdVersion eVersion)
  {
    if (sAttr.isEmpty ())
      return true;
    for (final String sToken : sAttr.trim ().split ("\\s+"))
      if (sToken.equals (eVersion.getID ()))
        return true;
    return false;
  }

  /**
   * Pick the expected validity for the run version: an
   * <code>&lt;expected&gt;</code> whose version equals the run version wins;
   * otherwise the versionless one.
   *
   * @param aExpected
   *        The expected elements of a test.
   * @param eVersion
   *        The run version.
   * @return The expected validity or <code>null</code> if none applies.
   */
  @Nullable
  public static String getExpectedValidity (@NonNull final List <XsdExpected> aExpected,
                                            @NonNull final EXsdVersion eVersion)
  {
    String sGeneric = null;
    for (final XsdExpected aExp : aExpected)
    {
      if (aExp.version ().equals (eVersion.getID ()))
        return aExp.validity ();
      if (aExp.version ().isEmpty ())
        sGeneric = aExp.validity ();
    }
    return sGeneric;
  }

  /**
   * @param aCurrentStatuses
   *        The statuses of the <code>&lt;current&gt;</code> elements of a test.
   * @return <code>true</code> if the latest one marks the test as disputed
   *         (<code>queried</code>). Such tests are counted on their own,
   *         outside the pass/fail denominator.
   */
  public static boolean isQueried (@NonNull final List <String> aCurrentStatuses)
  {
    return !aCurrentStatuses.isEmpty () && STATUS_QUERIED.equals (aCurrentStatuses.get (aCurrentStatuses.size () - 1));
  }

  /**
   * @param bValid
   *        The validator's verdict.
   * @param sExpected
   *        The expected validity.
   * @return <code>true</code> if the verdict matches; never for an expected
   *         validity other than valid or invalid.
   */
  public static boolean isVerdictOK (final boolean bValid, @NonNull final String sExpected)
  {
    return switch (sExpected)
    {
      case VALID -> bValid;
      case INVALID -> !bValid;
      default -> false;
    };
  }
}
