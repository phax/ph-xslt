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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * The knobs of an XSLT 3.0 run, read from system properties:
 * <ul>
 * <li><code>xslt30.dir</code> - suite directory (default
 * {@value #DEFAULT_SUITE_DIR})</li>
 * <li><code>xslt30.only</code> - run only test sets whose name contains
 * this</li>
 * <li><code>xslt30.skip</code> - comma separated; leave out the test sets whose
 * name contains one of the entries (e.g. <code>unicode-90,regex-classes</code>
 * for a quick run)</li>
 * <li><code>xslt30.progress=true</code> - print every test set as it
 * starts</li>
 * <li><code>xslt30.faildump=true</code> - print a line per failing case
 * (implied by <code>xslt30.only</code>)</li>
 * <li><code>xslt30.seq=true</code> - run the test sets sequentially</li>
 * <li><code>xslt30.checkAccepted=false</code> - do not fail on failing cases
 * not named in the accepted-failures file</li>
 * <li><code>xslt30.strictCode=true</code> - an <code>error</code> assertion
 * must match the expected error code, not just any error</li>
 * <li><code>xslt30.u90=true</code> - also run the Unicode-version cases that
 * are normally excluded with a reason</li>
 * <li><code>xslt30.out</code> - output directory (default
 * <code>target</code>)</li>
 * <li><code>xslt30.accepted</code> - accepted-failures file (default
 * {@value #DEFAULT_ACCEPTED_FILE})</li>
 * </ul>
 *
 * @param suiteDir
 *        The xslt30-test directory.
 * @param only
 *        Test-set name filter, empty for all.
 * @param skipSets
 *        Test-set name fragments to leave out.
 * @param progress
 *        Print progress.
 * @param failDump
 *        Collect and print fail lines.
 * @param sequential
 *        Run test sets one after the other.
 * @param checkAccepted
 *        Check fails against the accepted-failures file.
 * @param strictErrorCodes
 *        Require matching error codes.
 * @param runUnicodeExcluded
 *        Do not apply the Unicode-version exclusions.
 * @param outDir
 *        Output directory.
 * @param acceptedFile
 *        The accepted-failures markdown file.
 * @author Philip Helger
 */
public record XSLT30Settings (@NonNull Path suiteDir,
                              @NonNull String only,
                              @NonNull ICommonsList <String> skipSets,
                              boolean progress,
                              boolean failDump,
                              boolean sequential,
                              boolean checkAccepted,
                              boolean strictErrorCodes,
                              boolean runUnicodeExcluded,
                              @NonNull Path outDir,
                              @NonNull Path acceptedFile,
                              boolean schemaAware)
{
  public static final String DEFAULT_SUITE_DIR = "/Users/philip/dev/git-thirdparty/xslt30-test";
  public static final String DEFAULT_ACCEPTED_FILE = "docs/accepted-failures/xslt30.md";
  /** The accepted failures of a run that claims schema-awareness. */
  public static final String DEFAULT_ACCEPTED_FILE_SCHEMA_AWARE = "docs/accepted-failures/xslt30-schema-aware.md";

  /** @return The catalog file. */
  @NonNull
  public Path getCatalogFile ()
  {
    return suiteDir.resolve ("catalog.xml");
  }

  /** @return Whether fail lines are collected (fail dump or a filtered run). */
  public boolean isCollectFailLines ()
  {
    return failDump || !only.isEmpty ();
  }

  /**
   * @param sSetName
   *        A test-set name.
   * @return <code>true</code> if the test set is selected by
   *         <code>only</code> and <code>skipSets</code>.
   */
  public boolean isSetSelected (@NonNull final String sSetName)
  {
    if (!only.isEmpty () && !sSetName.contains (only))
      return false;
    for (final String sSkip : skipSets)
      if (sSetName.contains (sSkip))
        return false;
    return true;
  }

  /** @return The settings from the system properties. */
  @NonNull
  public static XSLT30Settings fromSystemProperties ()
  {
    // -Dxslt30.schemaAware=true claims schema-awareness (needs Xerces-J)
    final boolean bSchemaAware = Boolean.getBoolean ("xslt30.schemaAware");
    final ICommonsList <String> aSkipSets = new CommonsArrayList <> ();
    for (final String sPart : System.getProperty ("xslt30.skip", "").split (","))
      if (!sPart.isBlank ())
        aSkipSets.add (sPart.strip ());
    return new XSLT30Settings (Path.of (System.getProperty ("xslt30.dir", DEFAULT_SUITE_DIR)),
                               System.getProperty ("xslt30.only", ""),
                               aSkipSets,
                               Boolean.getBoolean ("xslt30.progress"),
                               Boolean.getBoolean ("xslt30.faildump"),
                               Boolean.getBoolean ("xslt30.seq"),
                               !"false".equals (System.getProperty ("xslt30.checkAccepted")),
                               Boolean.getBoolean ("xslt30.strictCode"),
                               Boolean.getBoolean ("xslt30.u90"),
                               Path.of (System.getProperty ("xslt30.out", bSchemaAware ? "target/schema-aware" : "target")),
                               Path.of (System.getProperty ("xslt30.accepted",
                                                            bSchemaAware ? DEFAULT_ACCEPTED_FILE_SCHEMA_AWARE : DEFAULT_ACCEPTED_FILE)),
                               bSchemaAware);
  }
}
