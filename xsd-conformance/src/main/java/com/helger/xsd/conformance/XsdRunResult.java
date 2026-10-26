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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedMap;

/**
 * The aggregated result of one version run of the xsdtests suite.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdRunResult
{
  private static final int TOP_SETS = 25;

  private final EXsdVersion m_eVersion;
  private final String m_sValidatorName;
  private final boolean m_bVersionSupported;
  private final ICommonsList <XsdSetResult> m_aSets;

  /**
   * @param eVersion
   *        The version that was run.
   * @param sValidatorName
   *        Display name of the validator.
   * @param bVersionSupported
   *        Whether the validator implements the version.
   * @param aSets
   *        The per test-set results in suite order.
   */
  public XsdRunResult (@NonNull final EXsdVersion eVersion,
                       @NonNull final String sValidatorName,
                       final boolean bVersionSupported,
                       @NonNull final ICommonsList <XsdSetResult> aSets)
  {
    m_eVersion = eVersion;
    m_sValidatorName = sValidatorName;
    m_bVersionSupported = bVersionSupported;
    m_aSets = aSets.getClone ();
  }

  private int _sum (@NonNull final ToIntFunction <XsdSetResult> aGetter)
  {
    return m_aSets.stream ().mapToInt (aGetter).sum ();
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
  public EXsdVersion getVersion ()
  {
    return m_eVersion;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XsdSetResult> getAllSets ()
  {
    return m_aSets.getClone ();
  }

  public int getPass ()
  {
    return _sum (XsdSetResult::getPass);
  }

  public int getFail ()
  {
    return _sum (XsdSetResult::getFail);
  }

  public int getSkip ()
  {
    return _sum (XsdSetResult::getSkip);
  }

  public int getUnsupported ()
  {
    return _sum (XsdSetResult::getUnsupported);
  }

  public int getQueried ()
  {
    return _sum (XsdSetResult::getQueried);
  }

  /** @return All skip reasons with their counts. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getSkipReasons ()
  {
    final ICommonsSortedMap <String, Integer> ret = new CommonsTreeMap <> ();
    for (final XsdSetResult aSet : m_aSets)
      for (final Map.Entry <String, Integer> aEntry : aSet.getSkipReasons ().entrySet ())
      {
        final Integer aOld = ret.get (aEntry.getKey ());
        ret.put (aEntry.getKey (),
                 Integer.valueOf ((aOld == null ? 0 : aOld.intValue ()) + aEntry.getValue ().intValue ()));
      }
    return ret;
  }

  /** @return All failing case names, sorted (duplicates kept). */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getFailNames ()
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final XsdSetResult aSet : m_aSets)
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
    final int nUnsupported = getUnsupported ();
    final int nRan = nPass + nFail;
    final double dRate = nRan > 0 ? 100.0 * nPass / nRan : 0;
    final StringBuilder aSB = new StringBuilder ();
    aSB.append (String.format (Locale.ROOT,
                               "XSD %s results (%s): %d applicable | ran %d (pass %d, fail %d = %.2f%% pass) | unsupported %d | skipped %d | queried %d%n",
                               m_eVersion.getID (),
                               m_sValidatorName,
                               Integer.valueOf (nRan + nUnsupported),
                               Integer.valueOf (nRan),
                               Integer.valueOf (nPass),
                               Integer.valueOf (nFail),
                               Double.valueOf (dRate),
                               Integer.valueOf (nUnsupported),
                               Integer.valueOf (getSkip ()),
                               Integer.valueOf (getQueried ())));
    if (!m_bVersionSupported)
      aSB.append ("XSD ")
         .append (m_eVersion.getID ())
         .append (" is not implemented by ")
         .append (m_sValidatorName)
         .append (": every applicable case is reported as unsupported, outside the pass/fail denominator\n");
    aSB.append ("test sets: ").append (m_aSets.size ()).append ('\n');
    aSB.append ("skip reasons: ").append (_formatCounts (getSkipReasons (), Integer.MAX_VALUE)).append ('\n');
    final ICommonsSortedMap <String, Integer> aFailBySet = new CommonsTreeMap <> ();
    for (final XsdSetResult aSet : m_aSets)
    {
      if (aSet.getFail () > 0)
        aFailBySet.put (aSet.getName (), Integer.valueOf (aSet.getFail ()));
      if (aSet.getLoadError () != null)
        aSB.append ("test set not loaded: ").append (aSet.getName ()).append (": ").append (aSet.getLoadError ()).append ('\n');
    }
    aSB.append ("top failing test sets: ").append (_formatCounts (aFailBySet, TOP_SETS)).append ('\n');
    return aSB.toString ();
  }

  /** @return The per test-set counts as CSV, in suite order. */
  @NonNull
  public String getBySetCsv ()
  {
    final StringBuilder aSB = new StringBuilder ("set,pass,fail,skip,unsupported,queried\n");
    for (final XsdSetResult aSet : m_aSets)
      aSB.append (aSet.getName ())
         .append (',')
         .append (aSet.getPass ())
         .append (',')
         .append (aSet.getFail ())
         .append (',')
         .append (aSet.getSkip ())
         .append (',')
         .append (aSet.getUnsupported ())
         .append (',')
         .append (aSet.getQueried ())
         .append ('\n');
    return aSB.toString ();
  }

  /** @return The name of the fail list file of this version. */
  @NonNull
  public String getFailFileName ()
  {
    return "xsd" + m_eVersion.getFileSuffix () + "-fails.txt";
  }

  /**
   * Write <code>xsd-fails.txt</code> (sorted fail names) and
   * <code>xsd-byset.csv</code> (per test-set counts) for XSD 1.0, resp.
   * <code>xsd11-fails.txt</code> and <code>xsd11-byset.csv</code> for XSD 1.1.
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
    Files.writeString (aOutDir.resolve (getFailFileName ()), aSB.toString (), StandardCharsets.UTF_8);
    Files.writeString (aOutDir.resolve ("xsd" + m_eVersion.getFileSuffix () + "-byset.csv"),
                       getBySetCsv (),
                       StandardCharsets.UTF_8);
  }
}
