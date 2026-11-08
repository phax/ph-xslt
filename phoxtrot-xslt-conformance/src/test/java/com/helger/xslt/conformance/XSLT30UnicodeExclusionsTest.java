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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link XSLT30UnicodeExclusions}.
 *
 * @author Philip Helger
 */
public final class XSLT30UnicodeExclusionsTest
{
  private static XSLT30TestCase _case (final String sName, @Nullable final String sMode)
  {
    final XSLT30Invocation.Entry aMode = sMode == null ? null
                                                       : new XSLT30Invocation.Entry (new XdmQName (sMode),
                                                                                     sMode,
                                                                                     "",
                                                                                     new CommonsArrayList <> (),
                                                                                     new CommonsLinkedHashMap <> ());
    return new XSLT30TestCase (sName,
                               new CommonsArrayList <> (),
                               null,
                               new XSLT30Invocation (new CommonsArrayList <> (),
                                                     new CommonsArrayList <> (),
                                                     null,
                                                     aMode,
                                                     null,
                                                     new CommonsArrayList <> (),
                                                     null,
                                                     null,
                                                     new CommonsLinkedHashMap <> ()),
                               null);
  }

  @Test
  public void testRegexClasses ()
  {
    assertEquals ("unicode-version",
                  XSLT30UnicodeExclusions.getExclusionReason ("regex-classes", _case ("regex-classes-001", null)));
    assertNull (XSLT30UnicodeExclusions.getExclusionReason ("regex-classes", _case ("regex-classes-002", null)));
    // Only in its own test set
    assertNull (XSLT30UnicodeExclusions.getExclusionReason ("other", _case ("regex-classes-001", null)));
  }

  @Test
  public void testUnicode90 ()
  {
    assertEquals ("suite-defect",
                  XSLT30UnicodeExclusions.getExclusionReason ("unicode-90", _case ("unicode90-003", null)));
    assertEquals ("suite-defect",
                  XSLT30UnicodeExclusions.getExclusionReason ("unicode-90", _case ("unicode90-Ll-033", "fn-matches1")));
    assertEquals ("unicode-version",
                  XSLT30UnicodeExclusions.getExclusionReason ("unicode-90", _case ("unicode90-Ll-005", "fn-matches1")));
    // The other Ll cases run
    assertNull (XSLT30UnicodeExclusions.getExclusionReason ("unicode-90", _case ("unicode90-Ll-001", "fn-matches1")));
    // charclass is never passed: category modes test Ll instead
    assertEquals ("suite-defect",
                  XSLT30UnicodeExclusions.getExclusionReason ("unicode-90", _case ("unicode90-Zs-001", "fn-matches1")));
    assertNull (XSLT30UnicodeExclusions.getExclusionReason ("unicode-90",
                                                             _case ("unicode90-Zs-002", "fn-string-length1")));
    assertNull (XSLT30UnicodeExclusions.getExclusionReason ("unicode-90", _case ("unicode90-Zs-003", null)));
  }
}
