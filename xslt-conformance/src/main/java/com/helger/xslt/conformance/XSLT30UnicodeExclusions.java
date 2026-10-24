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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;

/**
 * The cases of the two Unicode-version test sets that are excluded one by one,
 * a port of Go's <code>unicodeExclusion</code>. <code>unicode-90</code> is
 * written for Unicode 9.0 and <code>regex-classes</code> for 3.1 to 6.0; both
 * sets run, except:
 * <ul>
 * <li><code>unicode-version</code>: the expected result is the character data
 * of an old Unicode version, and the Unicode version is
 * implementation-defined (F&amp;O 3.1, Conformance).</li>
 * <li><code>suite-defect</code>: no implementation can pass the case as
 * written.</li>
 * </ul>
 * The analysis behind each entry is in go-xslt's
 * <code>docs/accepted-failures/xslt30.md</code>.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30UnicodeExclusions
{
  /**
   * General categories tested against the characters of Unicode 3.1, 5.2 or
   * 6.0. The block cases of the set run.
   */
  private static final ICommonsSet <String> REGEX_CLASSES_VERSION_CASES = new CommonsHashSet <> ("regex-classes-001",
                                                                                                "regex-classes-003",
                                                                                                "regex-classes-004",
                                                                                                "regex-classes-006",
                                                                                                "regex-classes-007",
                                                                                                "regex-classes-008",
                                                                                                "regex-classes-009",
                                                                                                "regex-classes-011",
                                                                                                "regex-classes-012",
                                                                                                "regex-classes-013",
                                                                                                "regex-classes-014",
                                                                                                "regex-classes-015",
                                                                                                "regex-classes-016",
                                                                                                "regex-classes-017",
                                                                                                "regex-classes-018",
                                                                                                "regex-classes-019",
                                                                                                "regex-classes-020",
                                                                                                "regex-classes-022",
                                                                                                "regex-classes-023",
                                                                                                "regex-classes-024",
                                                                                                "regex-classes-026",
                                                                                                "regex-classes-027",
                                                                                                "regex-classes-028",
                                                                                                "regex-classes-029",
                                                                                                "regex-classes-030",
                                                                                                "regex-classes-031",
                                                                                                "regex-classes-032",
                                                                                                "regex-classes-033",
                                                                                                "regex-classes-036");

  /**
   * Modes of the generated unicode-90 cases that do not depend on the tested
   * category. Every other mode tests the category named by the stylesheet
   * parameter <code>charclass</code>, which the catalog never passes: it
   * defaults to Ll, so only the Ll cases test what they mean to.
   */
  private static final ICommonsSet <String> UNICODE90_CLASS_FREE_MODES = new CommonsHashSet <> ("fn-matches9",
                                                                                               "fn-matches10",
                                                                                               "fn-matches12",
                                                                                               "fn-string-length1",
                                                                                               "fn-string-length2",
                                                                                               "fn-translate1",
                                                                                               "fn-translate2",
                                                                                               "fn-translate3",
                                                                                               "fn-translate4",
                                                                                               "fn-replace4");

  /**
   * Ll cases that run over every code point outside the 9.0 Ll data (or count
   * all of them): 170 code points became Ll after Unicode 9.0.
   */
  private static final ICommonsSet <String> UNICODE90_LL_VERSION_CASES = new CommonsHashSet <> ("unicode90-Ll-005",
                                                                                               "unicode90-Ll-006",
                                                                                               "unicode90-Ll-007",
                                                                                               "unicode90-Ll-008",
                                                                                               "unicode90-Ll-014",
                                                                                               "unicode90-Ll-015",
                                                                                               "unicode90-Ll-017",
                                                                                               "unicode90-Ll-018",
                                                                                               "unicode90-Ll-020",
                                                                                               "unicode90-Ll-022",
                                                                                               "unicode90-Ll-024",
                                                                                               "unicode90-Ll-032",
                                                                                               "unicode90-Ll-036",
                                                                                               "unicode90-Ll-037",
                                                                                               "unicode90-Ll-038");

  @PresentForCodeCoverage
  private static final XSLT30UnicodeExclusions INSTANCE = new XSLT30UnicodeExclusions ();

  private XSLT30UnicodeExclusions ()
  {}

  @Nullable
  private static String _getUnicode90Reason (@NonNull final XSLT30TestCase aCase)
  {
    final String sName = aCase.name ();
    switch (sName)
    {
      case "unicode90-001":
      case "unicode90-002":
      case "unicode90-003":
      case "unicode90-004":
      case "unicode90-005":
      case "unicode90-006":
      case "unicode90-007":
      case "unicode90-008":
        // Counts over all planes, but the expected counts are BMP totals
        return "suite-defect";
      case "unicode90-Ll-033":
      case "unicode90-Ll-035":
        // Compare with string-join(*/c, ''), but the <c> elements are empty
        return "suite-defect";
      case "unicode90-C-025":
      case "unicode90-Cn-025":
        // U+FFFE and U+FFFF cannot occur in XML, so the data has c=""
        return "suite-defect";
      default:
        break;
    }
    if (UNICODE90_LL_VERSION_CASES.contains (sName))
      return "unicode-version";
    final XSLT30Invocation.Entry aMode = aCase.test ().initialMode ();
    if (sName.startsWith ("unicode90-Ll-") || !sName.startsWith ("unicode90-") || aMode == null)
      return null;
    if (!UNICODE90_CLASS_FREE_MODES.contains (aMode.lexicalName ()))
    {
      // charclass not passed: tests Ll, not its category
      return "suite-defect";
    }
    return null;
  }

  /**
   * @param sSetName
   *        The test-set name.
   * @param aCase
   *        The test case.
   * @return <code>null</code> if the case runs, otherwise
   *         <code>unicode-version</code> or <code>suite-defect</code>.
   */
  @Nullable
  public static String getExclusionReason (@NonNull final String sSetName, @NonNull final XSLT30TestCase aCase)
  {
    switch (sSetName)
    {
      case "regex-classes":
        return REGEX_CLASSES_VERSION_CASES.contains (aCase.name ()) ? "unicode-version" : null;
      case "unicode-90":
        return _getUnicode90Reason (aCase);
      default:
        return null;
    }
  }
}
