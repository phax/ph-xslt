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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.conformance.QT3AcceptedFailures;

/**
 * Checks the module's accepted-failures file with the shared
 * {@link QT3AcceptedFailures} check.
 *
 * @author Philip Helger
 */
public final class XsdAcceptedFailuresTest
{
  @Test
  public void testModuleFile () throws IOException
  {
    final Path aFile = Path.of (XsdSettings.DEFAULT_ACCEPTED_FILE);
    assertTrue (Files.isRegularFile (aFile));
    // A documented case and an undocumented one
    assertEquals (List.of ("no-such-case"),
                  new CommonsArrayList <> (QT3AcceptedFailures.getUndocumented (aFile,
                                                                                new CommonsArrayList <> ("particlesZ001.i",
                                                                                                         "no-such-case"))));
  }
}
