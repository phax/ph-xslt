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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

/**
 * The knobs of a QT3 run, read from system properties:
 * <ul>
 * <li><code>qt3.dir</code> - suite directory (default
 * {@value #DEFAULT_SUITE_DIR})</li>
 * <li><code>qt3.only</code> - run only test sets whose name contains this</li>
 * <li><code>qt3.progress=true</code> - print every test set as it starts</li>
 * <li><code>qt3.faildump=true</code> - print a line per failing case (implied
 * by <code>qt3.only</code>)</li>
 * <li><code>qt3.seq=true</code> - run the test sets sequentially</li>
 * <li><code>qt3.checkAccepted=false</code> - do not fail on failing cases not
 * named in the accepted-failures file (the check is on by default)</li>
 * <li><code>qt3.out</code> - output directory (default <code>target</code>)</li>
 * <li><code>qt3.accepted</code> - accepted-failures file (default
 * {@value #DEFAULT_ACCEPTED_FILE})</li>
 * </ul>
 *
 * @param suiteDir
 *        The qt3tests directory.
 * @param only
 *        Test-set name filter, empty for all.
 * @param progress
 *        Print progress.
 * @param failDump
 *        Collect and print fail lines.
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
public record QT3Settings (@NonNull Path suiteDir,
                           @NonNull String only,
                           boolean progress,
                           boolean failDump,
                           boolean sequential,
                           boolean checkAccepted,
                           @NonNull Path outDir,
                           @NonNull Path acceptedFile)
{
  public static final String DEFAULT_SUITE_DIR = "/Users/philip/dev/git-thirdparty/qt3tests";
  public static final String DEFAULT_ACCEPTED_FILE = "docs/accepted-failures/qt3.md";

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

  /** @return The settings from the system properties. */
  @NonNull
  public static QT3Settings fromSystemProperties ()
  {
    return new QT3Settings (Path.of (System.getProperty ("qt3.dir", DEFAULT_SUITE_DIR)),
                            System.getProperty ("qt3.only", ""),
                            Boolean.getBoolean ("qt3.progress"),
                            Boolean.getBoolean ("qt3.faildump"),
                            Boolean.getBoolean ("qt3.seq"),
                            !"false".equals (System.getProperty ("qt3.checkAccepted")),
                            Path.of (System.getProperty ("qt3.out", "target")),
                            Path.of (System.getProperty ("qt3.accepted", DEFAULT_ACCEPTED_FILE)));
  }
}
