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
import com.helger.xpath.conformance.QT3Outcome;
import com.helger.xpath.conformance.QT3SetResult;

/**
 * Runs the whole xslt30-test catalog (or the test sets selected by
 * <code>xslt30.only</code>/<code>xslt30.skip</code>), a port of Go's
 * <code>TestXSLT30</code>/<code>runXSLTSet</code>. Test sets run
 * concurrently, each with its own document cache, sharing one compile cache;
 * the result is independent of the scheduling. There is no per-case timeout:
 * a verdict must not depend on how busy the machine is.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30Runner
{
  private final XSLT30Settings m_aSettings;
  private final OffsetDateTime m_aNow = OffsetDateTime.now ();

  public XSLT30Runner (@NonNull final XSLT30Settings aSettings)
  {
    ValueEnforcer.notNull (aSettings, "Settings");
    m_aSettings = aSettings;
  }

  @NonNull
  private QT3SetResult _runSet (@NonNull final XSLT30Catalog aCatalog,
                                final XSLT30Catalog.@NonNull TestSetRef aRef,
                                @NonNull final XSLT30CaseRunner aCaseRunner)
  {
    if (m_aSettings.progress ())
      System.err.println ("SET " + aRef.name ());
    final XSLT30TestSet aSet;
    try
    {
      aSet = XSLT30CatalogReader.readTestSet (aCatalog.rootDir ().resolve (aRef.file ()));
    }
    catch (final IOException ex)
    {
      final QT3SetResult ret = new QT3SetResult (aRef.name ());
      ret.setLoadError (ex.getMessage ());
      return ret;
    }
    final QT3SetResult ret = new QT3SetResult (aSet.name ().isEmpty () ? aRef.name () : aSet.name ());
    final XSLT30DocumentCache aDocs = new XSLT30DocumentCache ();
    final boolean bCollect = m_aSettings.isCollectFailLines ();
    for (final XSLT30TestCase aCase : aSet.testCases ())
    {
      final XSLT30CaseRunner.CaseResult aResult = aCaseRunner.run (aSet, aCase, aDocs);
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
                                  ret.getName () +
                                  "/" +
                                  aCase.name () +
                                  " [" +
                                  aOutcome.reason () +
                                  "]" +
                                  (aResult.detail ().isEmpty () ? "" : " " + aResult.detail ()) : null);
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
  public XSLT30RunResult run () throws IOException
  {
    final XSLT30Catalog aCatalog = XSLT30CatalogReader.readCatalog (m_aSettings.getCatalogFile ());
    final ICommonsList <XSLT30Catalog.TestSetRef> aRefs = new CommonsArrayList <> ();
    for (final XSLT30Catalog.TestSetRef aRef : aCatalog.testSets ())
      if (m_aSettings.isSetSelected (aRef.name ()))
        aRefs.add (aRef);

    final XSLT30CaseRunner aCaseRunner = new XSLT30CaseRunner (aCatalog,
                                                               IXSLT30Compiler.ENGINE,
                                                               new XSLT30StylesheetCache (),
                                                               m_aNow,
                                                               m_aSettings.strictErrorCodes (),
                                                               m_aSettings.runUnicodeExcluded (),
                                                               m_aSettings.schemaAware ());
    final ICommonsList <QT3SetResult> aResults = new CommonsArrayList <> ();
    final int nThreads = m_aSettings.sequential () ? 1 : Math.max (1, Runtime.getRuntime ().availableProcessors ());
    final ExecutorService aExecutor = Executors.newFixedThreadPool (nThreads);
    try
    {
      final ICommonsList <Future <QT3SetResult>> aFutures = new CommonsArrayList <> ();
      for (final XSLT30Catalog.TestSetRef aRef : aRefs)
        aFutures.add (aExecutor.submit ( () -> _runSet (aCatalog, aRef, aCaseRunner)));
      for (int i = 0; i < aFutures.size (); ++i)
      {
        try
        {
          aResults.add (aFutures.get (i).get ());
        }
        catch (final ExecutionException ex)
        {
          // The case runner guards every case, so this is a harness bug
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
    return new XSLT30RunResult (aCatalog.testSets ().size (), aResults);
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
  public XSLT30RunResult runAndReport () throws IOException
  {
    final XSLT30RunResult ret = run ();
    if (m_aSettings.isCollectFailLines ())
      for (final QT3SetResult aSet : ret.getAllSets ())
        for (final String sLine : aSet.getFailLines ())
          System.out.println (sLine);
    System.out.print (ret.getSummary ());
    ret.writeFiles (m_aSettings.outDir ());
    System.out.println ("fail list written to " +
                        m_aSettings.outDir ().resolve ("xslt30-fails.txt").toAbsolutePath ());
    return ret;
  }
}
