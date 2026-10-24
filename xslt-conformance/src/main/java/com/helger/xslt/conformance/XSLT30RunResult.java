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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedMap;
import com.helger.xpath.conformance.QT3SetResult;

/**
 * The aggregated result of an XSLT 3.0 run. The per test-set counts are kept
 * in the QT3 harness's {@link QT3SetResult}, which is not specific to QT3.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30RunResult
{
  private static final int TOP_SETS = 25;

  private final int m_nCatalogTestSets;
  private final ICommonsList <QT3SetResult> m_aSets;

  /**
   * @param nCatalogTestSets
   *        Number of test sets in the catalog.
   * @param aSets
   *        The per test-set results in catalog order.
   */
  public XSLT30RunResult (final int nCatalogTestSets, @NonNull final ICommonsList <QT3SetResult> aSets)
  {
    m_nCatalogTestSets = nCatalogTestSets;
    m_aSets = aSets.getClone ();
  }

  private static void _add (@NonNull final ICommonsSortedMap <String, Integer> aTarget,
                            @NonNull final Map <String, Integer> aSource)
  {
    for (final Map.Entry <String, Integer> aEntry : aSource.entrySet ())
    {
      final Integer aOld = aTarget.get (aEntry.getKey ());
      aTarget.put (aEntry.getKey (),
                   Integer.valueOf ((aOld == null ? 0 : aOld.intValue ()) + aEntry.getValue ().intValue ()));
    }
  }

  /** Format as "a=3 b=1", largest count first, then by name. */
  @NonNull
  private static String _formatCounts (@NonNull final Map <String, Integer> aCounts, final int nMax)
  {
    final ICommonsList <Map.Entry <String, Integer>> aEntries = new CommonsArrayList <> (aCounts.entrySet ());
    aEntries.sort (Comparator.comparing ((final Map.Entry <String, Integer> e) -> e.getValue ())
                             .reversed ()
                             .thenComparing (Map.Entry::getKey));
    final StringBuilder aSB = new StringBuilder ();
    int n = 0;
    for (final Map.Entry <String, Integer> aEntry : aEntries)
    {
      if (n++ >= nMax)
        break;
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (aEntry.getKey ()).append ('=').append (aEntry.getValue ().intValue ());
    }
    return aSB.toString ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <QT3SetResult> getAllSets ()
  {
    return m_aSets.getClone ();
  }

  public int getPass ()
  {
    return m_aSets.stream ().mapToInt (QT3SetResult::getPass).sum ();
  }

  public int getFail ()
  {
    return m_aSets.stream ().mapToInt (QT3SetResult::getFail).sum ();
  }

  public int getSkip ()
  {
    return m_aSets.stream ().mapToInt (QT3SetResult::getSkip).sum ();
  }

  /** @return All skip reasons with their counts. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getSkipReasons ()
  {
    final ICommonsSortedMap <String, Integer> ret = new CommonsTreeMap <> ();
    for (final QT3SetResult aSet : m_aSets)
      _add (ret, aSet.getSkipReasons ());
    return ret;
  }

  /** @return All fail reasons with their counts. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getFailReasons ()
  {
    final ICommonsSortedMap <String, Integer> ret = new CommonsTreeMap <> ();
    for (final QT3SetResult aSet : m_aSets)
      _add (ret, aSet.getFailReasons ());
    return ret;
  }

  /** @return All failing case names, sorted. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getFailNames ()
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final QT3SetResult aSet : m_aSets)
      ret.addAll (aSet.getFailNames ());
    ret.sort (Comparator.naturalOrder ());
    return ret;
  }

  /** @return The human readable summary. */
  @NonNull
  public String getSummary ()
  {
    final int nPass = getPass ();
    final int nFail = getFail ();
    final int nSkip = getSkip ();
    final int nRan = nPass + nFail;
    final double dRate = nRan > 0 ? 100.0 * nPass / nRan : 0;
    final StringBuilder aSB = new StringBuilder ();
    aSB.append (String.format (Locale.ROOT,
                               "XSLT30 results: %d cases | ran %d (pass %d, fail %d = %.1f%% pass) | skipped %d%n",
                               Integer.valueOf (nPass + nFail + nSkip),
                               Integer.valueOf (nRan),
                               Integer.valueOf (nPass),
                               Integer.valueOf (nFail),
                               Double.valueOf (dRate),
                               Integer.valueOf (nSkip)));
    aSB.append ("test sets: ").append (m_aSets.size ()).append (" of ").append (m_nCatalogTestSets).append ('\n');
    aSB.append ("skip reasons: ").append (_formatCounts (getSkipReasons (), Integer.MAX_VALUE)).append ('\n');
    aSB.append ("fail reasons: ").append (_formatCounts (getFailReasons (), Integer.MAX_VALUE)).append ('\n');
    final ICommonsSortedMap <String, Integer> aFailBySet = new CommonsTreeMap <> ();
    for (final QT3SetResult aSet : m_aSets)
    {
      if (aSet.getFail () > 0)
        aFailBySet.put (aSet.getName (), Integer.valueOf (aSet.getFail ()));
      if (aSet.getLoadError () != null)
        aSB.append ("test set not loaded: ")
           .append (aSet.getName ())
           .append (": ")
           .append (aSet.getLoadError ())
           .append ('\n');
    }
    aSB.append ("top failing test sets: ").append (_formatCounts (aFailBySet, TOP_SETS)).append ('\n');
    return aSB.toString ();
  }

  /** @return The per test-set counts as CSV. */
  @NonNull
  public String getBySetCsv ()
  {
    final ICommonsList <QT3SetResult> aSorted = m_aSets.getClone ();
    aSorted.sort (Comparator.comparing (QT3SetResult::getName));
    final StringBuilder aSB = new StringBuilder ("set,pass,fail,skip\n");
    for (final QT3SetResult aSet : aSorted)
      aSB.append (aSet.getName ())
         .append (',')
         .append (aSet.getPass ())
         .append (',')
         .append (aSet.getFail ())
         .append (',')
         .append (aSet.getSkip ())
         .append ('\n');
    return aSB.toString ();
  }

  /**
   * Write <code>xslt30-fails.txt</code> (sorted fail names) and
   * <code>xslt30-byset.csv</code> (per test-set counts).
   *
   * @param aOutDir
   *        Output directory; created if missing.
   * @throws IOException
   *         on write errors
   */
  public void writeFiles (@NonNull final Path aOutDir) throws IOException
  {
    Files.createDirectories (aOutDir);
    final StringBuilder aSB = new StringBuilder ();
    for (final String sName : getFailNames ())
      aSB.append (sName).append ('\n');
    Files.writeString (aOutDir.resolve ("xslt30-fails.txt"), aSB.toString (), StandardCharsets.UTF_8);
    Files.writeString (aOutDir.resolve ("xslt30-byset.csv"), getBySetCsv (), StandardCharsets.UTF_8);
  }
}
