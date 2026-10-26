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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assume;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedSet;
import com.helger.xpath.conformance.QT3AcceptedFailures;

/**
 * Runs the W3C xsdtests suite against the native validator of
 * <code>xsd-core</code> whatever <code>xsd.validator</code> says (that selects
 * the validator of {@link XsdTest}) and gates on
 * <code>docs/accepted-failures/xsd-native.md</code>: a failing case that is not
 * named there fails the build. Skipped when the suite is absent
 * (<code>-Dxsd.dir=...</code>); <code>-Dxsd.checkAccepted=false</code>
 * switches the gate off.
 *
 * @author Philip Helger
 */
public final class XsdNativeTest
{
  @Test
  public void testNativeXSD () throws IOException
  {
    final XsdSettings aEnv = XsdSettings.fromSystemProperties ();
    Assume.assumeTrue ("xsdtests suite not present at " + aEnv.suiteDir (), Files.isRegularFile (aEnv.getSuiteFile ()));

    final EXsdTestValidatorKind eKind = EXsdTestValidatorKind.NATIVE;
    final String sCheckAccepted = System.getProperty ("xsd.checkAccepted");
    final XsdSettings aSettings = new XsdSettings (eKind,
                                                   aEnv.suiteDir (),
                                                   aEnv.only (),
                                                   aEnv.versions (),
                                                   aEnv.progress (),
                                                   aEnv.failDump (),
                                                   aEnv.unsupDump (),
                                                   aEnv.sequential (),
                                                   !"false".equals (sCheckAccepted),
                                                   aEnv.outDir (),
                                                   Path.of (eKind.getAcceptedFile ()));
    final ICommonsList <XsdRunResult> aResults = new XsdRunner (aSettings, eKind.getFactory ()).runAndReport ();
    if (aSettings.checkAccepted ())
    {
      final ICommonsList <String> aFails = new CommonsArrayList <> ();
      for (final XsdRunResult aResult : aResults)
        aFails.addAll (aResult.getFailNames ());
      final ICommonsSortedSet <String> aMissing = QT3AcceptedFailures.getUndocumented (aSettings.acceptedFile (), aFails);
      assertTrue ("undocumented failures: fix them, or add them with a reason to " +
                  aSettings.acceptedFile () +
                  ": " +
                  String.join (", ", aMissing),
                  aMissing.isEmpty ());
    }
  }
}
