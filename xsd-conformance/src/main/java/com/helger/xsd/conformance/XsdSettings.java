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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * The knobs of an xsdtests run, read from system properties (the Go harness'
 * <code>XSD_*</code> environment variables):
 * <ul>
 * <li><code>xsd.dir</code> - suite directory (default
 * {@value #DEFAULT_SUITE_DIR})</li>
 * <li><code>xsd.only</code> - run only test sets whose <code>suite.xml</code>
 * reference contains this</li>
 * <li><code>xsd.version</code> - <code>1.0</code> (default), <code>1.1</code>,
 * or both as <code>1.0,1.1</code></li>
 * <li><code>xsd.progress=true</code> - print every test set as it starts</li>
 * <li><code>xsd.faildump=true</code> - print a line per failing case with the
 * validator's first error (implied by <code>xsd.only</code>)</li>
 * <li><code>xsd.unsup=true</code> - also print a line per unsupported
 * case</li>
 * <li><code>xsd.seq=true</code> - run the test sets sequentially</li>
 * <li><code>xsd.checkAccepted=false</code> - do not fail on failing cases not
 * named in the accepted-failures file (the check is on by default)</li>
 * <li><code>xsd.out</code> - output directory (default <code>target</code>)</li>
 * <li><code>xsd.accepted</code> - accepted-failures file (default
 * {@value #DEFAULT_ACCEPTED_FILE})</li>
 * </ul>
 *
 * @param suiteDir
 *        The xsdtests directory.
 * @param only
 *        Test-set reference filter, empty for all.
 * @param versions
 *        The versions to run, in this order.
 * @param progress
 *        Print progress.
 * @param failDump
 *        Collect and print fail lines.
 * @param unsupDump
 *        Collect and print lines for unsupported cases.
 * @param sequential
 *        Run test sets one after the other.
 * @param checkAccepted
 *        Check fails against the accepted-failures file.
 * @param outDir
 *        Output directory.
 * @param acceptedFile
 *        The accepted-failures markdown file.
 * @author Philip Helger
 */
public record XsdSettings (@NonNull Path suiteDir,
                           @NonNull String only,
                           @NonNull ICommonsList <EXsdVersion> versions,
                           boolean progress,
                           boolean failDump,
                           boolean unsupDump,
                           boolean sequential,
                           boolean checkAccepted,
                           @NonNull Path outDir,
                           @NonNull Path acceptedFile)
{
  public static final String DEFAULT_SUITE_DIR = "/Users/philip/dev/git-thirdparty/xsdtests";
  public static final String DEFAULT_ACCEPTED_FILE = "docs/accepted-failures/xsd.md";
  public static final String DEFAULT_VERSION = "1.0";

  /** @return The <code>suite.xml</code> file. */
  @NonNull
  public Path getSuiteFile ()
  {
    return suiteDir.resolve ("suite.xml");
  }

  /** @return Whether fail lines are collected (fail dump or a filtered run). */
  public boolean isCollectFailLines ()
  {
    return failDump || !only.isEmpty ();
  }

  /**
   * @param sValue
   *        Comma separated version tokens.
   * @return The versions.
   * @throws IllegalArgumentException
   *         for an unknown token
   */
  @NonNull
  public static ICommonsList <EXsdVersion> parseVersions (@NonNull final String sValue)
  {
    final ICommonsList <EXsdVersion> ret = new CommonsArrayList <> ();
    for (final String sPart : sValue.split (","))
    {
      final String sToken = sPart.trim ();
      if (sToken.isEmpty ())
        continue;
      final EXsdVersion eVersion = EXsdVersion.getFromIDOrNull (sToken);
      if (eVersion == null)
        throw new IllegalArgumentException ("Unknown XSD version '" + sToken + "' (use 1.0, 1.1 or 1.0,1.1)");
      if (!ret.contains (eVersion))
        ret.add (eVersion);
    }
    if (ret.isEmpty ())
      throw new IllegalArgumentException ("No XSD version in '" + sValue + "'");
    return ret;
  }

  /** @return The settings from the system properties. */
  @NonNull
  public static XsdSettings fromSystemProperties ()
  {
    return new XsdSettings (Path.of (System.getProperty ("xsd.dir", DEFAULT_SUITE_DIR)),
                            System.getProperty ("xsd.only", ""),
                            parseVersions (System.getProperty ("xsd.version", DEFAULT_VERSION)),
                            Boolean.getBoolean ("xsd.progress"),
                            Boolean.getBoolean ("xsd.faildump"),
                            Boolean.getBoolean ("xsd.unsup"),
                            Boolean.getBoolean ("xsd.seq"),
                            !"false".equals (System.getProperty ("xsd.checkAccepted")),
                            Path.of (System.getProperty ("xsd.out", "target")),
                            Path.of (System.getProperty ("xsd.accepted", DEFAULT_ACCEPTED_FILE)));
  }
}
