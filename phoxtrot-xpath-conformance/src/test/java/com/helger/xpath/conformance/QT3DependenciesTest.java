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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Test class for class {@link QT3Dependencies}.
 *
 * @author Philip Helger
 */
public final class QT3DependenciesTest
{
  @Test
  public void testParseSpecToken ()
  {
    assertEquals (new QT3Dependencies.SpecToken ("XP", 31, true), QT3Dependencies.parseSpecToken ("XP31+"));
    assertEquals (new QT3Dependencies.SpecToken ("XQ", 10, false), QT3Dependencies.parseSpecToken ("XQ10"));
    assertEquals (new QT3Dependencies.SpecToken ("XT", 30, true), QT3Dependencies.parseSpecToken ("XT30+"));
    assertEquals (new QT3Dependencies.SpecToken ("XP", 0, false), QT3Dependencies.parseSpecToken ("XP3x"));
  }

  @Test
  public void testSpecApplicable ()
  {
    // Open-ended tokens up to 3.1 admit the engine
    assertTrue (QT3Dependencies.isSpecApplicable ("XP31+"));
    assertTrue (QT3Dependencies.isSpecApplicable ("XP30+"));
    assertTrue (QT3Dependencies.isSpecApplicable ("XP20+"));
    assertTrue (QT3Dependencies.isSpecApplicable ("XP10+"));
    // Exact versions only when equal
    assertTrue (QT3Dependencies.isSpecApplicable ("XP31"));
    assertFalse (QT3Dependencies.isSpecApplicable ("XP30"));
    assertFalse (QT3Dependencies.isSpecApplicable ("XP20"));
    // Later versions do not apply
    assertFalse (QT3Dependencies.isSpecApplicable ("XP40+"));
    // XQuery never applies
    assertFalse (QT3Dependencies.isSpecApplicable ("XQ10+"));
    assertFalse (QT3Dependencies.isSpecApplicable ("XQ31"));
    // OR of tokens
    assertTrue (QT3Dependencies.isSpecApplicable ("XQ10+ XP30+"));
    assertTrue (QT3Dependencies.isSpecApplicable ("  XQ30+\tXP31+ "));
    // XSLT 3.0 like Go
    assertTrue (QT3Dependencies.isSpecApplicable ("XT30+"));
    assertFalse (QT3Dependencies.isSpecApplicable (""));
  }

  @Test
  public void testSkipReason ()
  {
    assertNull (QT3Dependencies.getSkipReason (List.of ()));
    assertNull (QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("spec", "XP31+ XQ31+", ""))));
    assertEquals (QT3Dependencies.SKIP_SPEC_INAPPLICABLE,
                  QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("spec", "XQ10+", ""))));
    // An unsatisfied spec dependency is not a reason to skip
    assertNull (QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("spec", "XQ10+", "false"))));
    // Other dependency types are attempted
    assertNull (QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("feature", "staticTyping", ""),
                                                        new QT3Dependency ("xml-version", "1.1", ""),
                                                        new QT3Dependency ("xsd-version", "1.0", ""),
                                                        new QT3Dependency ("limits", "big_integer", ""),
                                                        new QT3Dependency ("unicode-version", "6.0", ""))));
    assertEquals ("norm-form-not:FULLY-NORMALIZED",
                  QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("unicode-normalization-form",
                                                                             "FULLY-NORMALIZED",
                                                                             "false"))));
    assertNull (QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("unicode-normalization-form",
                                                                           "NFD",
                                                                           "false"))));
    // Test set dependency first in the union
    assertEquals (QT3Dependencies.SKIP_SPEC_INAPPLICABLE,
                  QT3Dependencies.getSkipReason (List.of (new QT3Dependency ("spec", "XQ30+", ""),
                                                          new QT3Dependency ("spec", "XP31+", ""))));
  }
}
