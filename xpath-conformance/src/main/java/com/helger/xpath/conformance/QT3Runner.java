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

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Runs the whole QT3 catalog (or the test sets selected by
 * <code>qt3.only</code>), a port of Go's <code>TestQT3</code>/<code>runSet</code>.
 * Test sets run concurrently, each with its own document cache; the result
 * is independent of the scheduling.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3Runner
{
  private static final int MAX_EXPR_LEN = 90;

  private final QT3Settings m_aSettings;
  private final OffsetDateTime m_aNow = OffsetDateTime.now ();

  public QT3Runner (@NonNull final QT3Settings aSettings)
  {
    ValueEnforcer.notNull (aSettings, "Settings");
    m_aSettings = aSettings;
  }

  @NonNull
  private static String _cut (@NonNull final String s, final int nMax)
  {
    final String sOneLine = s.strip ().replace ('\n', ' ').replace ('\r', ' ');
    return sOneLine.length () > nMax ? sOneLine.substring (0, nMax) : sOneLine;
  }

  @NonNull
  private static String _describeWant (@NonNull final QT3TestCase aCase)
  {
    final QT3Assertion aAssertion = aCase.result ();
    if (aAssertion == null)
      return "";
    if (aAssertion.children ().isNotEmpty ())
    {
      final StringBuilder aSB = new StringBuilder (aAssertion.kind ()).append ('(');
      for (final QT3Assertion aChild : aAssertion.children ())
      {
        if (aSB.charAt (aSB.length () - 1) != '(')
          aSB.append (", ");
        aSB.append (aChild.kind ()).append (':').append (_cut (aChild.value (), MAX_EXPR_LEN));
      }
      return aSB.append (')').toString ();
    }
    return aAssertion.kind () + ":" + _cut (aAssertion.value (), MAX_EXPR_LEN);
  }

  @NonNull
  private QT3SetResult _runSet (@NonNull final QT3Catalog aCatalog, final QT3Catalog.@NonNull TestSetRef aRef)
  {
    if (m_aSettings.progress ())
      System.err.println ("SET " + aRef.file ());
    final QT3TestSet aSet;
    try
    {
      aSet = QT3CatalogReader.readTestSet (aCatalog.rootDir ().resolve (aRef.file ()));
    }
    catch (final IOException ex)
    {
      final QT3SetResult ret = new QT3SetResult (aRef.name ());
      ret.setLoadError (ex.getMessage ());
      return ret;
    }
    final QT3SetResult ret = new QT3SetResult (aSet.name ().isEmpty () ? aRef.name () : aSet.name ());
    final QT3DocumentCache aDocs = new QT3DocumentCache ();
    final QT3CaseRunner aCaseRunner = new QT3CaseRunner (m_aNow);
    final boolean bCollect = m_aSettings.isCollectFailLines ();

    for (final QT3TestCase aCase : aSet.testCases ())
    {
      final ICommonsList <QT3Dependency> aDeps = new CommonsArrayList <> (aSet.dependencies ());
      aDeps.addAll (aCase.dependencies ());
      final String sSkip = QT3Dependencies.getSkipReason (aDeps);
      if (sSkip != null)
      {
        ret.addSkip (sSkip);
        continue;
      }

      QT3Environment aEnv = aCase.environment ();
      if (aEnv == null)
        aEnv = new QT3Environment ("", "", aSet.dir ());
      else
        if (aEnv.isReference ())
        {
          QT3Environment aFound = aSet.environments ().get (aEnv.getRef ());
          if (aFound == null)
            aFound = aCatalog.environments ().get (aEnv.getRef ());
          if (aFound == null)
          {
            ret.addSkip ("env-missing");
            continue;
          }
          aEnv = aFound;
        }

      final QT3CaseRunner.CaseResult aResult = aCaseRunner.run (aCase, aDeps, aEnv, aSet.dir (), aDocs);
      final QT3Outcome aOutcome = aResult.outcome ();
      switch (aOutcome.status ())
      {
        case PASS:
          ret.addPass ();
          break;
        case SKIP:
          ret.addSkip (aOutcome.reason ());
          break;
        default:
          ret.addFail (aCase.name (),
                       aOutcome.reason (),
                       bCollect ? "FAIL " +
                                  aCase.name () +
                                  " [" +
                                  aOutcome.reason () +
                                  "] expr=" +
                                  _cut (aCase.testExpr (), MAX_EXPR_LEN) +
                                  " want=" +
                                  _describeWant (aCase) +
                                  " got=" +
                                  _cut (aResult.got (), 2 * MAX_EXPR_LEN) : null);
          break;
      }
    }
    return ret;
  }

  /**
   * Run the catalog.
   *
   * @return The result.
   * @throws IOException
   *         if the catalog cannot be read
   */
  @NonNull
  public QT3RunResult run () throws IOException
  {
    final QT3Catalog aCatalog = QT3CatalogReader.readCatalog (m_aSettings.getCatalogFile ());
    final ICommonsList <QT3Catalog.TestSetRef> aRefs = new CommonsArrayList <> ();
    for (final QT3Catalog.TestSetRef aRef : aCatalog.testSets ())
      if (m_aSettings.only ().isEmpty () || aRef.name ().contains (m_aSettings.only ()))
        aRefs.add (aRef);

    final ICommonsList <QT3SetResult> aResults = new CommonsArrayList <> ();
    final int nThreads = m_aSettings.sequential () ? 1 : Math.max (1, Runtime.getRuntime ().availableProcessors ());
    final ExecutorService aExecutor = Executors.newFixedThreadPool (nThreads);
    try
    {
      final ICommonsList <Future <QT3SetResult>> aFutures = new CommonsArrayList <> ();
      for (final QT3Catalog.TestSetRef aRef : aRefs)
        aFutures.add (aExecutor.submit ( () -> _runSet (aCatalog, aRef)));
      for (int i = 0; i < aFutures.size (); ++i)
      {
        try
        {
          aResults.add (aFutures.get (i).get ());
        }
        catch (final ExecutionException ex)
        {
          // _runSet guards every case, so this is a harness bug
          final QT3SetResult aBroken = new QT3SetResult (aRefs.get (i).name ());
          aBroken.setLoadError ("harness error: " + ex.getCause ());
          aResults.add (aBroken);
        }
        catch (final InterruptedException ex)
        {
          Thread.currentThread ().interrupt ();
          throw new IOException ("Interrupted", ex);
        }
      }
    }
    finally
    {
      aExecutor.shutdownNow ();
    }
    return new QT3RunResult (aResults);
  }

  /**
   * Run, print the summary (and fail lines if requested) and write the
   * output files.
   *
   * @return The result.
   * @throws IOException
   *         if the catalog cannot be read or the output cannot be written
   */
  @NonNull
  public QT3RunResult runAndReport () throws IOException
  {
    final QT3RunResult ret = run ();
    if (m_aSettings.isCollectFailLines ())
      for (final QT3SetResult aSet : ret.getAllSets ())
        for (final String sLine : aSet.getFailLines ())
          System.out.println (sLine);
    System.out.print (ret.getSummary ());
    ret.writeFiles (m_aSettings.outDir ());
    System.out.println ("fail list written to " + m_aSettings.outDir ().resolve ("qt3-fails.txt").toAbsolutePath ());
    return ret;
  }
}
