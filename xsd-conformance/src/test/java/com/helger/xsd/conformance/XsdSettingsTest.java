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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

/**
 * Test class for class {@link XsdSettings}.
 *
 * @author Philip Helger
 */
public final class XsdSettingsTest
{
  @Test
  public void testParseVersions ()
  {
    assertEquals (List.of (EXsdVersion.XSD_10), XsdSettings.parseVersions ("1.0"));
    assertEquals (List.of (EXsdVersion.XSD_11), XsdSettings.parseVersions (" 1.1 "));
    assertEquals (List.of (EXsdVersion.XSD_10, EXsdVersion.XSD_11), XsdSettings.parseVersions ("1.0,1.1,1.0"));
    assertEquals (List.of (EXsdVersion.XSD_11, EXsdVersion.XSD_10), XsdSettings.parseVersions ("1.1, 1.0"));
  }

  @Test (expected = IllegalArgumentException.class)
  public void testParseVersionsUnknown ()
  {
    XsdSettings.parseVersions ("1.2");
  }

  @Test (expected = IllegalArgumentException.class)
  public void testParseVersionsEmpty ()
  {
    XsdSettings.parseVersions (" , ");
  }
}
