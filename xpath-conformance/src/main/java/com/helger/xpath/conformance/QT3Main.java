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

import java.io.IOException;
import java.nio.file.Files;

import com.helger.collection.commons.ICommonsSortedSet;

/**
 * Command line runner for the QT3 suite; configured with the same system
 * properties as {@link QT3Settings}. Exits with 1 if the suite is missing or,
 * with <code>qt3.checkAccepted=true</code>, if a failing case is not
 * documented.
 *
 * @author Philip Helger
 */
public final class QT3Main
{
  private QT3Main ()
  {}

  public static void main (final String [] aArgs) throws IOException
  {
    final QT3Settings aSettings = QT3Settings.fromSystemProperties ();
    if (!Files.isRegularFile (aSettings.getCatalogFile ()))
    {
      System.err.println ("QT3 suite not found at " + aSettings.suiteDir ().toAbsolutePath () + " (set -Dqt3.dir)");
      System.exit (1);
    }
    final QT3RunResult aResult = new QT3Runner (aSettings).runAndReport ();
    if (aSettings.checkAccepted ())
    {
      final ICommonsSortedSet <String> aMissing = QT3AcceptedFailures.getUndocumented (aSettings.acceptedFile (),
                                                                                       aResult.getFailNames ());
      if (aMissing.isNotEmpty ())
      {
        System.err.println ("undocumented failures: fix them, or add them with a reason to " +
                            aSettings.acceptedFile () +
                            ": " +
                            String.join (", ", aMissing));
        System.exit (1);
      }
    }
  }
}
