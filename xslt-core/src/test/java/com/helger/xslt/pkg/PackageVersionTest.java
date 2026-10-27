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
package com.helger.xslt.pkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for {@link PackageVersion} and {@link PackageVersionRanges}.
 *
 * @author Philip Helger
 */
public final class PackageVersionTest
{
  private static PackageVersion _v (final String s)
  {
    final PackageVersion ret = PackageVersion.parse (s);
    assertNotNull (s, ret);
    return ret;
  }

  private static boolean _matches (final String sRanges, final String sVersion)
  {
    final PackageVersionRanges aRanges = PackageVersionRanges.parse (sRanges);
    assertNotNull (sRanges, aRanges);
    return aRanges.matches (_v (sVersion));
  }

  @Test
  public void testParse ()
  {
    assertNull (PackageVersion.parse (""));
    assertNull (PackageVersion.parse ("1."));
    assertNull (PackageVersion.parse ("a.b"));
    assertNull (PackageVersion.parse ("2.0.0-alpha:beta"));
    assertEquals ("1.2.3-beta", _v ("1.2.3-beta").toString ());
  }

  @Test
  public void testCompare ()
  {
    assertEquals (0, _v ("2.0").compareTo (_v ("2.0.0")));
    assertEquals (_v ("2.0"), _v ("2.0.0"));
    assertEquals (_v ("2.0").hashCode (), _v ("2.0.0").hashCode ());
    assertTrue (_v ("2.0").compareTo (_v ("2.0-alpha")) > 0);
    assertTrue (_v ("2.0-alpha").compareTo (_v ("2.0-beta")) < 0);
    assertTrue (_v ("1.10").compareTo (_v ("1.9")) > 0);
  }

  @Test
  public void testRanges ()
  {
    assertTrue (_matches ("*", "7.1"));
    assertTrue (_matches ("1.0", "1.0.0"));
    assertFalse (_matches ("1.0", "1.0.1"));
    assertTrue (_matches ("3.5.*", "3.5.2"));
    assertFalse (_matches ("3.5.*", "3.6"));
    assertTrue (_matches ("2.0+", "2.1"));
    assertFalse (_matches ("2.0+", "1.9"));
    assertTrue (_matches ("1.0 to 2.0", "1.5"));
    assertFalse (_matches ("1.0 to 2.0", "2.1"));
    assertTrue (_matches ("to 2.0", "0.1"));
    assertTrue (_matches ("1.0, 3.0", "3.0"));
    assertTrue (_matches ("1.2.3.0", "1.2.3"));
    assertTrue (_matches ("1.*", "1.2.3"));
    assertTrue (_matches ("to 1.2.*", "1.2.3"));
    assertFalse (_matches ("to 1.2", "1.2.3"));
    // The examples of XSLT 3.0 section 3.5.1
    assertTrue (_matches ("to 4.0", "4.0-beta"));
    assertFalse (_matches ("to 4.0", "4.0.1"));
    assertTrue (_matches ("to 3.3.*", "3.3.4621"));
    assertTrue (_matches ("to 3.3.*", "2.0.6"));
    assertFalse (_matches ("to 3.3.*", "3.4.0"));
    assertFalse (_matches ("to 3.3.*", "3.4.0-beta"));
    assertFalse (_matches ("1 to 5", "5.1"));
    assertTrue (_matches ("1 to 5.*", "5.7.2"));
    assertFalse (_matches ("1 to 5.*", "6.0-beta"));
    assertTrue (_matches ("to 3.3.*", "1.5"));
    assertTrue (_matches ("to 3.3.*", "3.3"));
    assertTrue (_matches ("to 3.3.*", "3.3-beta"));
    assertTrue (_matches ("1 to 5", "1.1"));
    assertTrue (_matches ("1 to 5", "3.1"));
    assertTrue (_matches ("1 to 5", "5.0"));
    assertTrue (_matches ("1 to 5.*", "1.1"));
    assertTrue (_matches ("1 to 5.*", "5.0"));
    assertTrue (_matches ("1 to 5.*", "5.1"));
    assertFalse (_matches ("1 to 5.*", "6.0"));
    assertFalse (_matches ("1 to 5.*", "0.9"));
    assertFalse (_matches ("1.3+", "1.2.3"));
    assertTrue (_matches ("2.0, 1.2.3", "1.2.3"));
    assertNull (PackageVersionRanges.parse (""));
    assertNull (PackageVersionRanges.parse ("to"));
    assertNull (PackageVersionRanges.parse ("x+"));
  }
}
