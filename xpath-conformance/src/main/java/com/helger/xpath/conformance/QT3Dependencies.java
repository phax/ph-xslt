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

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Dependency filtering, a port of Go's <code>specApplies</code> and
 * <code>skipReason</code>. The engine is a real XPath 3.1 processor; the only
 * category-level exclusion is a <code>spec</code> dependency its identity does
 * not satisfy (XQuery only, or an exact older version whose behaviour 3.0/3.1
 * changed). Every other dependency (feature, xml-version, xsd-version,
 * default-language, limits, unicode-version, ...) is left to run, so a gap is
 * scored as a fail, not hidden behind a skip.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3Dependencies
{
  /**
   * The language identities of the processor, family to version. Like Go, XT
   * (XSLT 3.0) is included because the engine is meant to embed an XSLT 3.0
   * processor; XQ is absent, so XQuery-only cases never apply.
   */
  public static final Map <String, Integer> ENGINE_SPEC_VERSIONS = Map.of ("XP", Integer.valueOf (31),
                                                                          "XT",
                                                                          Integer.valueOf (30));

  /** Skip reason for an inapplicable spec dependency. */
  public static final String SKIP_SPEC_INAPPLICABLE = "spec-inapplicable";

  /**
   * A parsed spec token like <code>XP31+</code>.
   *
   * @param family
   *        "XP", "XQ", "XT", ...
   * @param version
   *        e.g. 31; 0 if the version part is malformed.
   * @param open
   *        <code>true</code> for a trailing "+" (this version or later).
   */
  public record SpecToken (@NonNull String family, int version, boolean open)
  {}

  @PresentForCodeCoverage
  private static final QT3Dependencies INSTANCE = new QT3Dependencies ();

  private QT3Dependencies ()
  {}

  /**
   * @param sToken
   *        A token like <code>XP31+</code>.
   * @return The parsed token.
   */
  @NonNull
  public static SpecToken parseSpecToken (@NonNull final String sToken)
  {
    final boolean bOpen = sToken.endsWith ("+");
    final String sRest = bOpen ? sToken.substring (0, sToken.length () - 1) : sToken;
    int i = 0;
    while (i < sRest.length () && (sRest.charAt (i) < '0' || sRest.charAt (i) > '9'))
      i++;
    final String sFamily = sRest.substring (0, i);
    int nVersion = 0;
    for (int j = i; j < sRest.length (); ++j)
    {
      final char c = sRest.charAt (j);
      if (c < '0' || c > '9')
        return new SpecToken (sFamily, 0, bOpen);
      nVersion = nVersion * 10 + (c - '0');
    }
    return new SpecToken (sFamily, nVersion, bOpen);
  }

  /**
   * @param sValue
   *        A spec dependency value, a whitespace separated OR of tokens (e.g.
   *        <code>XP31+ XQ31+</code>).
   * @return <code>true</code> if one token admits the engine's identity:
   *         exactly, unless the token is open-ended.
   */
  public static boolean isSpecApplicable (@NonNull final String sValue)
  {
    final String sTrimmed = sValue.trim ();
    if (sTrimmed.isEmpty ())
      return false;
    for (final String sToken : sTrimmed.split ("\\s+"))
    {
      final SpecToken aToken = parseSpecToken (sToken);
      final Integer aHave = ENGINE_SPEC_VERSIONS.get (aToken.family ());
      if (aHave == null)
        continue;
      final int nHave = aHave.intValue ();
      if (aToken.open () && nHave >= aToken.version ())
        return true;
      if (!aToken.open () && nHave == aToken.version ())
        return true;
    }
    return false;
  }

  /**
   * @param aDeps
   *        The union of test-set and test-case dependencies.
   * @return <code>null</code> if the case should run, else a short reason.
   */
  @Nullable
  public static String getSkipReason (@NonNull final List <QT3Dependency> aDeps)
  {
    for (final QT3Dependency aDep : aDeps)
    {
      if ("spec".equals (aDep.type ()) && aDep.isSatisfied () && !isSpecApplicable (aDep.value ()))
        return SKIP_SPEC_INAPPLICABLE;
      // satisfied="false" for FULLY-NORMALIZED asks for a processor that does
      // NOT implement the form - a lesser processor than this one
      if ("unicode-normalization-form".equals (aDep.type ()) &&
          !aDep.isSatisfied () &&
          "FULLY-NORMALIZED".equals (aDep.value ()))
        return "norm-form-not:" + aDep.value ();
    }
    return null;
  }
}
