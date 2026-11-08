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
package com.helger.phoxtrot.xsd.conformance;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;

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
  /** The version token of a processor that accepts any XPath in CTA. */
  public static final String TOKEN_FULL_XPATH_IN_CTA = "full-xpath-in-CTA";
  /**
   * The version token of a processor that accepts only the required XPath
   * subset in CTA.
   */
  public static final String TOKEN_RESTRICTED_XPATH_IN_CTA = "restricted-xpath-in-CTA";

  @PresentForCodeCoverage
  private static final XsdSuiteRules INSTANCE = new XsdSuiteRules ();

  private XsdSuiteRules ()
  {}

  /**
   * The version tokens (<code>xsts.xsd</code>) a run claims: the run version
   * (<code>1.0</code> or <code>1.1</code>), and for XSD 1.1 the XPath subset
   * of conditional type assignment the validator implements
   * ({@link #TOKEN_FULL_XPATH_IN_CTA} or
   * {@link #TOKEN_RESTRICTED_XPATH_IN_CTA}; the two exclude each other, as
   * the suite gives them contradicting verdicts). No other implementation
   * token (Unicode version, edition, XML version, ...) is claimed.
   *
   * @param eVersion
   *        The run version.
   * @param bFullXPathInCta
   *        <code>true</code> if the validator accepts any XPath in CTA.
   * @return The claimed tokens.
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsSet <String> getClaimedTokens (@NonNull final EXsdVersion eVersion,
                                                       final boolean bFullXPathInCta)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    ret.add (eVersion.getID ());
    if (eVersion == EXsdVersion.XSD_11)
      ret.add (bFullXPathInCta ? TOKEN_FULL_XPATH_IN_CTA : TOKEN_RESTRICTED_XPATH_IN_CTA);
    return ret;
  }

  /**
   * The suite's version-token semantics for test sets, groups, schema tests
   * and instance tests (<code>xsts.xsd</code>: run if <em>any</em> of the
   * tokens is supported): an absent attribute applies everywhere; otherwise
   * one of the whitespace-separated tokens must be claimed by the run.
   *
   * @param sAttr
   *        The <code>version</code> attribute, empty if absent.
   * @param aClaimed
   *        The tokens the run claims, see {@link #getClaimedTokens}.
   * @return <code>true</code> if the element applies to the run.
   */
  public static boolean isVersionApplicable (@NonNull final String sAttr, @NonNull final Set <String> aClaimed)
  {
    if (sAttr.isBlank ())
      return true;
    for (final String sToken : sAttr.trim ().split ("\\s+"))
      if (aClaimed.contains (sToken))
        return true;
    return false;
  }

  /**
   * Like {@link #isVersionApplicable(String, Set)} with only the run version
   * claimed (no CTA token).
   *
   * @param sAttr
   *        The <code>version</code> attribute, empty if absent.
   * @param eVersion
   *        The run version.
   * @return <code>true</code> if the element applies to the run version.
   */
  public static boolean isVersionApplicable (@NonNull final String sAttr, @NonNull final EXsdVersion eVersion)
  {
    return isVersionApplicable (sAttr, Set.of (eVersion.getID ()));
  }

  /**
   * Pick the expected validity for the run (<code>xsts.xsd</code>: the
   * prescribed outcome applies if <em>all</em> tokens of the
   * <code>version</code> of an <code>&lt;expected&gt;</code> are supported):
   * the first <code>&lt;expected&gt;</code> with a version whose tokens are
   * all claimed wins; otherwise the (last) versionless one.
   *
   * @param aExpected
   *        The expected elements of a test.
   * @param aClaimed
   *        The tokens the run claims, see {@link #getClaimedTokens}.
   * @return The expected validity or <code>null</code> if none applies.
   */
  @Nullable
  public static String getExpectedValidity (@NonNull final List <XsdExpected> aExpected,
                                            @NonNull final Set <String> aClaimed)
  {
    String sGeneric = null;
    for (final XsdExpected aExp : aExpected)
    {
      if (aExp.version ().isBlank ())
      {
        sGeneric = aExp.validity ();
        continue;
      }
      boolean bAll = true;
      for (final String sToken : aExp.version ().trim ().split ("\\s+"))
        if (!aClaimed.contains (sToken))
          bAll = false;
      if (bAll)
        return aExp.validity ();
    }
    return sGeneric;
  }

  /**
   * Like {@link #getExpectedValidity(List, Set)} with only the run version
   * claimed (no CTA token).
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
    return getExpectedValidity (aExpected, Set.of (eVersion.getID ()));
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
