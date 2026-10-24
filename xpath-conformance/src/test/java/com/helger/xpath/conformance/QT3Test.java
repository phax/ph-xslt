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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Test;

import com.helger.collection.commons.ICommonsSortedSet;

/**
 * Runs the W3C QT3 (FOTS) suite against the engine and prints the summary.
 * Skipped when the suite is absent (<code>-Dqt3.dir=...</code>). It does not
 * fail the build on failing cases; only with
 * <code>-Dqt3.checkAccepted=true</code> does a failing case not named in the
 * accepted-failures file fail it.
 *
 * @author Philip Helger
 */
public final class QT3Test
{
  @Test
  public void testQT3 () throws IOException
  {
    final QT3Settings aSettings = QT3Settings.fromSystemProperties ();
    Assume.assumeTrue ("QT3 suite not present at " + aSettings.suiteDir (),
                       Files.isRegularFile (aSettings.getCatalogFile ()));

    final QT3RunResult aResult = new QT3Runner (aSettings).runAndReport ();
    if (aSettings.checkAccepted ())
    {
      final ICommonsSortedSet <String> aMissing = QT3AcceptedFailures.getUndocumented (aSettings.acceptedFile (),
                                                                                       aResult.getFailNames ());
      assertTrue ("undocumented failures: fix them, or add them with a reason to " +
                  aSettings.acceptedFile () +
                  ": " +
                  String.join (", ", aMissing),
                  aMissing.isEmpty ());
    }
  }
}
