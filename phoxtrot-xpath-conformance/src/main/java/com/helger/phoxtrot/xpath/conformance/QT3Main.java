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
package com.helger.phoxtrot.xpath.conformance;

import java.io.IOException;
import java.nio.file.Files;

import com.helger.collection.commons.ICommonsSortedSet;

/**
 * Command line runner for the QT3 suite; configured with the same system
 * properties as {@link QT3Settings}. Exits with 1 if the suite is missing or,
 * unless <code>qt3.checkAccepted=false</code>, if a failing case is not
 * documented.
 *
 * @author Philip Helger
 */
public final class QT3Main
{
  private QT3Main ()
  {}

  /**
   * Read the {@link QT3Settings} from the system properties, run the catalog
   * with {@link QT3Runner#runAndReport()} (summary on stdout, fail list and
   * per-set CSV in the output directory) and then, unless
   * <code>qt3.checkAccepted=false</code>, compare the failing case names with
   * the accepted-failures file. Exits the JVM with status 1 if
   * <code>catalog.xml</code> is not found below <code>qt3.dir</code> or if a
   * failing case is not documented (the names are printed to stderr);
   * otherwise returns normally.
   *
   * @param aArgs
   *        Ignored.
   * @throws IOException
   *         if the catalog, a result file or the accepted-failures file
   *         cannot be read or written
   */
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
