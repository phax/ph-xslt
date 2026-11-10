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
package com.helger.phoxtrot.xdm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Test class for class {@link PhoxtrotVersion}.
 *
 * @author Philip Helger
 */
public final class PhoxtrotVersionTest
{
  @Test
  public void testBasic ()
  {
    // Both values must have been filtered by Maven
    assertNotEquals ("undefined", PhoxtrotVersion.BUILD_VERSION);
    assertFalse (PhoxtrotVersion.BUILD_VERSION.contains ("${"));
    assertNotEquals ("undefined", PhoxtrotVersion.BUILD_TIMESTAMP);
    assertFalse (PhoxtrotVersion.BUILD_TIMESTAMP.contains ("${"));
  }
}
