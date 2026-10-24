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

import java.io.IOException;
import java.nio.file.Files;

import com.helger.collection.commons.ICommonsSortedSet;
import com.helger.xpath.conformance.QT3AcceptedFailures;

/**
 * Command line runner for the xslt30-test suite; configured with the same
 * system properties as {@link XSLT30Settings}. Exits with 1 if the suite is
 * missing or, with <code>xslt30.checkAccepted=true</code>, if a failing case is
 * not documented.
 *
 * @author Philip Helger
 */
public final class XSLT30Main
{
  private XSLT30Main ()
  {}

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
