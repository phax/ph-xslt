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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Test;

import com.helger.collection.commons.ICommonsSortedSet;
import com.helger.xpath.conformance.QT3AcceptedFailures;

/**
 * Runs the W3C xslt30-test suite against the engine and prints the summary.
 * Skipped when the suite is absent (<code>-Dxslt30.dir=...</code>). While the
 * engine is being written this never fails the build; with
 * <code>-Dxslt30.checkAccepted=true</code> a failing case that is not named in
 * <code>docs/accepted-failures/xslt30.md</code> fails it.
 * <p>
 * Named after the Go test <code>TestXSLT30</code>, not after a class.
 *
 * @author Philip Helger
 */
public final class XSLT30Test
{
  @Test
  public void testXSLT30 () throws IOException
  {
    final XSLT30Settings aSettings = XSLT30Settings.fromSystemProperties ();
    Assume.assumeTrue ("xslt30-test suite not present at " + aSettings.suiteDir (),
                       Files.isRegularFile (aSettings.getCatalogFile ()));

    final XSLT30RunResult aResult = new XSLT30Runner (aSettings).runAndReport ();
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
