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

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;

/**
 * Decides whether a test case applies to this processor, a port of Go's
 * <code>xsltSkip</code> in its default profile: a basic XSLT 3.0 processor
 * (XSLT 3.0 section 26) without the optional features streaming,
 * schema-awareness and backwards compatibility. Packages, higher-order
 * functions, serialization and dynamic evaluation (<code>xsl:evaluate</code>)
 * are claimed; a test for a processor <em>without</em> a claimed feature does
 * not apply.
 * <p>
 * The test-set dependencies are checked before the test-case ones, and within
 * one <code>&lt;dependencies&gt;</code> element the categories are checked in
 * the fixed order below, so the first reason found is the one Go reports.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30Dependencies
{
  /**
   * Features not implemented: a test requiring one (satisfied other than
   * "false") is skipped. <code>streaming-fallback</code> names a processor
   * configuration, not a capability.
   */
  private static final ICommonsSet <String> UNSUPPORTED_FEATURES = new CommonsHashSet <> ("streaming",
                                                                                         "schema_aware",
                                                                                         "schemaImport",
                                                                                         "schemaValidation",
                                                                                         "schemaAware",
                                                                                         "static_typing",
                                                                                         "staticTyping",
                                                                                         "backwards_compatibility",
                                                                                         "advanced_uca_fallback",
                                                                                         "streaming-fallback");

  /**
   * Optional features this processor claims: a test written for a processor
   * without one (satisfied="false") does not apply. A feature in neither set
   * (e.g. <code>higher_order_functions</code>, <code>serialization</code>,
   * <code>namespace_axis</code>, <code>dtd</code>) always runs.
   */
  private static final ICommonsSet <String> SUPPORTED_FEATURES = new CommonsHashSet <> ("dynamic_evaluation",
                                                                                       "disabling_output_escaping",
                                                                                       "XPath_3.1",
                                                                                       "XSD_1.1");

  @PresentForCodeCoverage
  private static final XSLT30Dependencies INSTANCE = new XSLT30Dependencies ();

  private XSLT30Dependencies ()
  {}

  /**
   * An XSLT 3.0 processor satisfies any spec admitting 3.0: XSLT30, XSLT10+ or
   * XSLT20+ ("1.0 or later"), XT30, or XPath-only specs. A spec pinned to an
   * exact older version does not apply.
   */
  private static boolean _isSpecApplicable (@NonNull final String v)
  {
    return v.contains ("XSLT30") ||
           v.contains ("XT30") ||
           v.contains ("XP") ||
           ((v.startsWith ("XSLT10") || v.startsWith ("XSLT20") || v.startsWith ("XT10") || v.startsWith ("XT20")) &&
            v.contains ("+"));
  }

  @Nullable
  private static String _getSkipReason (@NonNull final List <XSLT30Dependency> aDeps)
  {
    for (final XSLT30Dependency aDep : aDeps)
      if ("spec".equals (aDep.type ()) && !aDep.isNotSatisfied () && !_isSpecApplicable (aDep.value ()))
        return "spec:" + aDep.value ();

    for (final XSLT30Dependency aDep : aDeps)
      if ("feature".equals (aDep.type ()))
      {
        if (!aDep.isNotSatisfied () && UNSUPPORTED_FEATURES.contains (aDep.value ()))
          return "feat:" + aDep.value ();
        if (aDep.isNotSatisfied () && SUPPORTED_FEATURES.contains (aDep.value ()))
          return "feat-not:" + aDep.value ();
      }

    // XSD 1.1 is what the reference processor claims (see SUPPORTED_FEATURES)
    for (final XSLT30Dependency aDep : aDeps)
      if ("xsd-version".equals (aDep.type ()) &&
          !aDep.isNotSatisfied () &&
          aDep.value ().contains ("1.0") &&
          !aDep.value ().contains ("1.1"))
        return "xsd1.0";

    // A unicode-version dependency does not skip a whole set; see
    // XSLT30UnicodeExclusions

    // Negative years and years above 9999 are supported, so the variants for
    // a processor without them do not apply
    for (final XSLT30Dependency aDep : aDeps)
      if ("year_component_values".equals (aDep.type ()) && aDep.isNotSatisfied ())
        return "year-component-values-unsupported-variant";

    // Word numbering exists in English, German, French and Italian
    for (final XSLT30Dependency aDep : aDeps)
      if ("languages_for_numbering".equals (aDep.type ()) && !aDep.isNotSatisfied ())
      {
        final String sValue = aDep.value ().strip ().toLowerCase (Locale.ROOT);
        final int nDash = sValue.indexOf ('-');
        final String sBase = nDash >= 0 ? sValue.substring (0, nDash) : sValue;
        switch (sBase)
        {
          case "":
          case "en":
          case "de":
          case "fr":
          case "it":
            break;
          default:
            return "lang:" + aDep.value ();
        }
      }

    // The default html-version is 5
    for (final XSLT30Dependency aDep : aDeps)
      if ("default_html_version".equals (aDep.type ()) && !aDep.isNotSatisfied ())
      {
        final String sValue = aDep.value ().strip ();
        if (!sValue.isEmpty () && !"5".equals (sValue) && !"5.0".equals (sValue))
          return "default_html_version:" + sValue;
      }

    // Assertions are always evaluated
    for (final XSLT30Dependency aDep : aDeps)
      if ("enable_assertions".equals (aDep.type ()) && aDep.isNotSatisfied ())
        return "feat:assertions-disabled";

    // A failed document retrieval is always an error
    for (final XSLT30Dependency aDep : aDeps)
      if ("ignore_doc_failure".equals (aDep.type ()) && "true".equals (aDep.satisfied ()))
        return "feat:ignore_doc_failure";

    // xsl:use-package picks the highest matching version
    for (final XSLT30Dependency aDep : aDeps)
      if ("package_version_resolution".equals (aDep.type ()) && !aDep.isNotSatisfied ())
      {
        final String sValue = aDep.value ().strip ();
        if (!"highest_version".equals (sValue) && !"unspecified".equals (sValue))
          return "package_version_resolution:" + sValue;
      }
    return null;
  }

  /**
   * @param aSetDeps
   *        The dependencies of the test set.
   * @param aCaseDeps
   *        The dependencies of the test case.
   * @return <code>null</code> if the case applies, otherwise a short skip
   *         reason (e.g. <code>feat:streaming</code>,
   *         <code>spec:XSLT20</code>).
   */
  @Nullable
  public static String getSkipReason (@NonNull final List <XSLT30Dependency> aSetDeps,
                                      @NonNull final List <XSLT30Dependency> aCaseDeps)
  {
    final String ret = _getSkipReason (aSetDeps);
    return ret != null ? ret : _getSkipReason (aCaseDeps);
  }
}
