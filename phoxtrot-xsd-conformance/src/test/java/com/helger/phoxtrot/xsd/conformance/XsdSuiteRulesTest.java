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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Test class for class {@link XsdSuiteRules}.
 *
 * @author Philip Helger
 */
public final class XsdSuiteRulesTest
{
  @Test
  public void testVersionApplicable ()
  {
    assertTrue (XsdSuiteRules.isVersionApplicable ("", EXsdVersion.XSD_10));
    assertTrue (XsdSuiteRules.isVersionApplicable ("", EXsdVersion.XSD_11));
    assertTrue (XsdSuiteRules.isVersionApplicable ("1.0", EXsdVersion.XSD_10));
    assertFalse (XsdSuiteRules.isVersionApplicable ("1.0", EXsdVersion.XSD_11));
    assertTrue (XsdSuiteRules.isVersionApplicable (" 1.0  1.1 ", EXsdVersion.XSD_11));
    assertTrue (XsdSuiteRules.isVersionApplicable ("1.1 Unicode_4.0.0", EXsdVersion.XSD_11));
    // Alien tokens match neither version
    assertFalse (XsdSuiteRules.isVersionApplicable ("Unicode_4.0.0", EXsdVersion.XSD_10));
    assertFalse (XsdSuiteRules.isVersionApplicable ("Unicode_4.0.0", EXsdVersion.XSD_11));
  }

  @Test
  public void testExpectedValidity ()
  {
    final List <XsdExpected> aBoth = List.of (new XsdExpected ("valid", ""), new XsdExpected ("invalid", "1.1"));
    assertEquals ("valid", XsdSuiteRules.getExpectedValidity (aBoth, EXsdVersion.XSD_10));
    assertEquals ("invalid", XsdSuiteRules.getExpectedValidity (aBoth, EXsdVersion.XSD_11));
    // The version-specific one wins regardless of order
    final List <XsdExpected> aReverse = List.of (new XsdExpected ("invalid", "1.0"), new XsdExpected ("valid", ""));
    assertEquals ("invalid", XsdSuiteRules.getExpectedValidity (aReverse, EXsdVersion.XSD_10));
    assertEquals ("valid", XsdSuiteRules.getExpectedValidity (aReverse, EXsdVersion.XSD_11));
    assertNull (XsdSuiteRules.getExpectedValidity (List.of (new XsdExpected ("valid", "1.1")), EXsdVersion.XSD_10));
    assertNull (XsdSuiteRules.getExpectedValidity (List.of (), EXsdVersion.XSD_10));
  }

  @Test
  public void testClaimedTokens ()
  {
    final Set <String> aFull11 = XsdSuiteRules.getClaimedTokens (EXsdVersion.XSD_11, true);
    final Set <String> aRestricted11 = XsdSuiteRules.getClaimedTokens (EXsdVersion.XSD_11, false);
    final Set <String> a10 = XsdSuiteRules.getClaimedTokens (EXsdVersion.XSD_10, true);
    assertEquals (Set.of ("1.1", "full-xpath-in-CTA"), aFull11);
    assertEquals (Set.of ("1.1", "restricted-xpath-in-CTA"), aRestricted11);
    // XSD 1.0 has no conditional type assignment
    assertEquals (Set.of ("1.0"), a10);

    // A group marked full-xpath-in-CTA runs for a validator with full XPath
    assertTrue (XsdSuiteRules.isVersionApplicable ("full-xpath-in-CTA", aFull11));
    assertFalse (XsdSuiteRules.isVersionApplicable ("full-xpath-in-CTA", aRestricted11));
    assertFalse (XsdSuiteRules.isVersionApplicable ("full-xpath-in-CTA", a10));

    // cta0006: valid with full XPath, invalid with the required subset
    final List <XsdExpected> aCta = List.of (new XsdExpected ("valid", "full-xpath-in-CTA"),
                                             new XsdExpected ("invalid", "restricted-xpath-in-CTA"));
    assertEquals ("valid", XsdSuiteRules.getExpectedValidity (aCta, aFull11));
    assertEquals ("invalid", XsdSuiteRules.getExpectedValidity (aCta, aRestricted11));
    assertNull (XsdSuiteRules.getExpectedValidity (aCta, a10));
    // All tokens of an expected version must be claimed
    final List <XsdExpected> aEdition = List.of (new XsdExpected ("invalid", "1.0 1.0-1e"), new XsdExpected ("valid", ""));
    assertEquals ("valid", XsdSuiteRules.getExpectedValidity (aEdition, a10));
    assertEquals ("invalid", XsdSuiteRules.getExpectedValidity (aEdition, Set.of ("1.0", "1.0-1e")));
  }

  @Test
  public void testQueried ()
  {
    assertFalse (XsdSuiteRules.isQueried (List.of ()));
    assertTrue (XsdSuiteRules.isQueried (List.of ("accepted", "queried")));
    // Only the latest status counts
    assertFalse (XsdSuiteRules.isQueried (List.of ("queried", "accepted")));
  }

  @Test
  public void testVerdictOK ()
  {
    assertTrue (XsdSuiteRules.isVerdictOK (true, "valid"));
    assertFalse (XsdSuiteRules.isVerdictOK (false, "valid"));
    assertTrue (XsdSuiteRules.isVerdictOK (false, "invalid"));
    assertFalse (XsdSuiteRules.isVerdictOK (true, "invalid"));
    assertFalse (XsdSuiteRules.isVerdictOK (true, "indeterminate"));
    assertFalse (XsdSuiteRules.isVerdictOK (false, "indeterminate"));
  }
}
