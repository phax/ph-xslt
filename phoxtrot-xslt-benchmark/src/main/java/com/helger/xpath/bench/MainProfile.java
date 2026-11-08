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
package com.helger.xpath.bench;

import java.util.Locale;

import org.jspecify.annotations.NonNull;

/**
 * Runs one workload repeatedly for a profiler, e.g. with
 * <code>-XX:StartFlightRecording=filename=x.jfr,settings=profile -XX:+DebugNonSafepoints</code>.
 * Arguments: group, case, number of lines (default 10000), seconds (default 20). The first fifth
 * of the time is a warm-up whose runs are not counted in the printed mean.
 * <p>
 * <code>verify [lines]</code> instead prepares and checks every case of every group once (default
 * 1000 lines) and exits with 1 if one fails.
 *
 * @author Philip Helger
 */
public final class MainProfile
{
  private MainProfile ()
  {}

  private static int _verifyAll (final int nSize)
  {
    int nFailed = 0;
    for (final EWorkloadGroup eGroup : EWorkloadGroup.values ())
      for (final String sCase : eGroup.getAllCaseIDs ())
      {
        final long nStart = System.nanoTime ();
        try
        {
          eGroup.createVerified (sCase, nSize);
          System.out.println ("OK   " + eGroup.getID () + " " + sCase + " (" + (System.nanoTime () - nStart) / 1_000_000 + " ms)");
        }
        catch (final Exception | Error ex)
        {
          nFailed++;
          System.out.println ("FAIL " + eGroup.getID () + " " + sCase + ": " + ex);
        }
      }
    return nFailed;
  }

  private static void _profile (@NonNull final EWorkloadGroup eGroup,
                                @NonNull final String sCase,
                                final int nSize,
                                final int nSeconds) throws Exception
  {
    final IWorkload aWorkload = eGroup.createVerified (sCase, nSize);
    final long nStart = System.nanoTime ();
    final long nWarmupEnd = nStart + nSeconds * 200_000_000L;
    final long nEnd = nStart + nSeconds * 1_000_000_000L;
    long nMeasuredRuns = 0;
    long nMeasuredNanos = 0;
    long nNow = nStart;
    int nHash = 0;
    while (nNow < nEnd)
    {
      nHash += System.identityHashCode (aWorkload.run ());
      final long nAfter = System.nanoTime ();
      if (nNow >= nWarmupEnd)
      {
        nMeasuredRuns++;
        nMeasuredNanos += nAfter - nNow;
      }
      nNow = nAfter;
    }
    final double dMeanMillis = nMeasuredRuns == 0 ? 0 : nMeasuredNanos / 1e6 / nMeasuredRuns;
    System.out.println (eGroup.getID () +
                        " " +
                        sCase +
                        " lines=" +
                        nSize +
                        ": " +
                        nMeasuredRuns +
                        " measured runs, mean " +
                        String.format (Locale.ROOT, "%.3f", Double.valueOf (dMeanMillis)) +
                        " ms (" +
                        nHash +
                        ")");
  }

  public static void main (final String [] aArgs) throws Exception
  {
    if (aArgs.length >= 1 && "verify".equals (aArgs[0]))
    {
      final int nSize = aArgs.length > 1 ? Integer.parseInt (aArgs[1]) : 1000;
      System.exit (_verifyAll (nSize) == 0 ? 0 : 1);
    }
    if (aArgs.length < 2)
    {
      System.err.println ("Usage: MainProfile <group> <case> [lines] [seconds]  or  MainProfile verify [lines]");
      for (final EWorkloadGroup eGroup : EWorkloadGroup.values ())
        System.err.println ("  " + eGroup.getID () + ": " + eGroup.getAllCaseIDs ());
      System.exit (2);
    }
    final EWorkloadGroup eGroup = EWorkloadGroup.getFromIDOrNull (aArgs[0]);
    if (eGroup == null)
      throw new IllegalArgumentException ("Unknown group '" + aArgs[0] + "'");
    final int nSize = aArgs.length > 2 ? Integer.parseInt (aArgs[2]) : 10_000;
    final int nSeconds = aArgs.length > 3 ? Integer.parseInt (aArgs[3]) : 20;
    _profile (eGroup, aArgs[1], nSize, nSeconds);
  }
}
