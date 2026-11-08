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
package com.helger.phoxtrot.xslt.conformance;

import java.io.IOException;
import java.nio.file.Files;

import com.helger.collection.commons.ICommonsSortedSet;
import com.helger.phoxtrot.xpath.conformance.QT3AcceptedFailures;

/**
 * Command line runner for the xslt30-test suite; configured with the same
 * system properties as {@link XSLT30Settings}. Exits with 1 if the suite is
 * missing or, unless <code>xslt30.checkAccepted=false</code>, if a failing
 * case is not documented.
 *
 * @author Philip Helger
 */
public final class XSLT30Main
{
  private XSLT30Main ()
  {}

  /**
   * Run the suite from the command line: the settings come from the system
   * properties ({@link XSLT30Settings#fromSystemProperties()}), the run prints
   * the summary (and the fail lines if requested) and writes
   * <code>xslt30-fails.txt</code> and <code>xslt30-byset.csv</code> to the
   * output directory ({@link XSLT30Runner#runAndReport()}). Then, if
   * {@link XSLT30Settings#checkAccepted()} is on, every failing case name must
   * appear in backticks in the accepted-failures file; the undocumented ones
   * are printed to stderr and the JVM exits with 1. A missing
   * <code>catalog.xml</code> also exits with 1, before anything runs.
   *
   * @param aArgs
   *        Ignored.
   * @throws IOException
   *         if the catalog, a test set or the accepted-failures file cannot be
   *         read, or the output files cannot be written
   */
  public static void main (final String [] aArgs) throws IOException
  {
    final XSLT30Settings aSettings = XSLT30Settings.fromSystemProperties ();
    if (!Files.isRegularFile (aSettings.getCatalogFile ()))
    {
      System.err.println ("xslt30-test suite not found at " +
                          aSettings.suiteDir ().toAbsolutePath () +
                          " (set -Dxslt30.dir)");
      System.exit (1);
    }
    final XSLT30RunResult aResult = new XSLT30Runner (aSettings).runAndReport ();
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
