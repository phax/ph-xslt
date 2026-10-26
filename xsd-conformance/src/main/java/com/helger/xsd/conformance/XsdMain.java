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

import java.io.IOException;
import java.nio.file.Files;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedSet;
import com.helger.xpath.conformance.QT3AcceptedFailures;

/**
 * Command line entry point of the xsdtests harness with the validator chosen
 * by <code>xsd.validator</code> (Xerces-J by default); the knobs are the system
 * properties of {@link XsdSettings}.
 * Exits with 1 if the suite is missing or a failing case is not documented.
 *
 * @author Philip Helger
 */
public final class XsdMain
{
  private XsdMain ()
  {}

  public static void main (final String [] aArgs) throws IOException
  {
    final XsdSettings aSettings = XsdSettings.fromSystemProperties ();
    if (!Files.isRegularFile (aSettings.getSuiteFile ()))
    {
      System.err.println ("xsdtests suite not found at " + aSettings.suiteDir ().toAbsolutePath () + " (set -Dxsd.dir)");
      System.exit (1);
    }
    final ICommonsList <XsdRunResult> aResults = new XsdRunner (aSettings, aSettings.validator ().getFactory ()).runAndReport ();
    if (aSettings.checkAccepted ())
    {
      final ICommonsList <String> aFails = new CommonsArrayList <> ();
      for (final XsdRunResult aResult : aResults)
        aFails.addAll (aResult.getFailNames ());
      final ICommonsSortedSet <String> aMissing = QT3AcceptedFailures.getUndocumented (aSettings.acceptedFile (), aFails);
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
