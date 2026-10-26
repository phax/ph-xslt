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
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Runs the W3C XML Schema test suite (xsdtests) against an
 * {@link IXsdTestValidator} and checks the top-level valid/invalid verdict, the
 * only thing the suite asserts. A port of the Go harness
 * <code>test/conformance/xsd_test.go</code>.
 * <p>
 * The suite groups tests as: <code>&lt;testGroup&gt;</code> with at most one
 * <code>&lt;schemaTest&gt;</code> (compile a schema: valid/invalid) plus zero
 * or more <code>&lt;instanceTest&gt;</code> (validate an instance against that
 * schema: valid/invalid). Test sets, groups, tests and expected verdicts may
 * carry a <code>version</code> token list; absent means both versions. Each
 * version is a separate run. The schema of a group is compiled once and
 * reused for all its instances.
 * <p>
 * Counting, as in Go: a case the validator cannot decide is
 * <em>unsupported</em> (neither pass nor fail); a case whose latest
 * <code>&lt;current&gt;</code> status is <code>queried</code> is counted as
 * <em>queried</em>; a case without a definite verdict or with a missing file
 * is <em>skipped</em>. One difference on purpose: where Go records a panic as
 * an anonymous fail and drops the rest of the group, an exception thrown by
 * the validator fails the case by its name (so the accepted-failures check
 * sees it); a crash while compiling still drops the group's instance tests.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdRunner
{
  private final XsdSettings m_aSettings;
  private final Supplier <? extends IXsdTestValidator> m_aValidatorFactory;

  /**
   * @param aSettings
   *        The settings.
   * @param aValidatorFactory
   *        Creates the validator under test; called once per test set, so a
   *        validator is only ever used by one thread.
   */
  public XsdRunner (@NonNull final XsdSettings aSettings,
                    @NonNull final Supplier <? extends IXsdTestValidator> aValidatorFactory)
  {
    ValueEnforcer.notNull (aSettings, "Settings");
    ValueEnforcer.notNull (aValidatorFactory, "ValidatorFactory");
    m_aSettings = aSettings;
    m_aValidatorFactory = aValidatorFactory;
  }

  @NonNull
  private static String _crashMessage (@NonNull final Throwable aThrowable)
  {
    return "validator crashed: " +
           aThrowable.getClass ().getName () +
           (aThrowable.getMessage () == null ? "" : ": " + aThrowable.getMessage ());
  }

  @NonNull
  private static String _oneLine (@Nullable final String sText)
  {
    return sText == null ? "" : sText.strip ().replace ('\n', ' ').replace ('\r', ' ');
  }

  private void _fail (@NonNull final XsdSetResult aResult,
                      @NonNull final String sName,
                      @NonNull final String sExpected,
                      @NonNull final String sHref,
                      @Nullable final String sMessage)
  {
    aResult.addFail (sName,
                     m_aSettings.isCollectFailLines () ? "FAIL exp=" +
                                                         sExpected +
                                                         " " +
                                                         sName +
                                                         " <<" +
                                                         sHref +
                                                         ">>" +
                                                         (sMessage == null ? "" : " || " + _oneLine (sMessage))
                                                       : null);
  }

  private void _unsupported (@NonNull final XsdSetResult aResult,
                             @NonNull final String sName,
                             @NonNull final String sExpected,
                             @Nullable final String sMessage)
  {
    aResult.addUnsupported (m_aSettings.unsupDump () ? "UNSUP exp=" +
                                                       sExpected +
                                                       " " +
                                                       sName +
                                                       (sMessage == null ? "" : " || " + _oneLine (sMessage))
                                                     : null);
  }

  /** Score one decided verdict against the expected one. */
  private void _score (@NonNull final XsdSetResult aResult,
                       @NonNull final String sName,
                       @NonNull final String sExpected,
                       @NonNull final String sHref,
                       @NonNull final XsdVerdict aVerdict)
  {
    if (aVerdict.isUnsupported ())
      _unsupported (aResult, sName, sExpected, aVerdict.message ());
    else
      if (XsdSuiteRules.isVerdictOK (aVerdict.isValid (), sExpected))
        aResult.addPass ();
      else
        _fail (aResult, sName, sExpected, sHref, aVerdict.isValid () ? "validator says valid" : aVerdict.message ());
  }

  /**
   * The applicable expected verdict of an instance test, or <code>null</code>
   * after recording why it does not run (version, verdict, queried, file).
   */
  @Nullable
  private static String _getInstanceExpected (@NonNull final XsdInstanceTest aTest,
                                              @NonNull final Path aDir,
                                              @NonNull final EXsdVersion eVersion,
                                              @NonNull final XsdSetResult aResult)
  {
    if (!XsdSuiteRules.isVersionApplicable (aTest.version (), eVersion))
    {
      // Test-level version gate (e.g. a 1.1-only instance twin)
      return null;
    }
    final String sExpected = XsdSuiteRules.getExpectedValidity (aTest.expected (), eVersion);
    if (sExpected == null || XsdSuiteRules.INDETERMINATE.equals (sExpected))
    {
      aResult.addSkip ("no-instance-verdict");
      return null;
    }
    if (XsdSuiteRules.isQueried (aTest.currentStatuses ()))
    {
      // W3C-disputed verdict: excluded from the denominator
      aResult.addQueried ();
      return null;
    }
    if (!Files.isRegularFile (aDir.resolve (aTest.document ()).normalize ()))
    {
      aResult.addSkip ("missing-instance-file");
      return null;
    }
    return sExpected;
  }

  private void _validate (@NonNull final IXsdTestSchema aSchema,
                          @NonNull final XsdInstanceTest aTest,
                          @NonNull final String sExpected,
                          @NonNull final Path aDir,
                          @NonNull final XsdSetResult aResult)
  {
    final XsdVerdict aVerdict;
    try
    {
      aVerdict = aSchema.validate (aDir.resolve (aTest.document ()).normalize ());
    }
    catch (final RuntimeException | StackOverflowError ex)
    {
      _fail (aResult, aTest.name (), sExpected, aTest.document (), _crashMessage (ex));
      return;
    }
    _score (aResult, aTest.name (), sExpected, aTest.document (), aVerdict);
  }

  /**
   * The instance tests of a group without schema test (msMeta's attMd*,
   * addB*, stZ*, ...): each instance is validated against the empty schema
   * set, so only the schema documents its own hints name and the built-in
   * types govern it.
   */
  private void _runSchemalessInstances (@NonNull final XsdTestGroup aGroup,
                                        @NonNull final Path aDir,
                                        @NonNull final EXsdVersion eVersion,
                                        @NonNull final IXsdTestValidator aValidator,
                                        @NonNull final XsdSetResult aResult)
  {
    for (final XsdInstanceTest aTest : aGroup.instanceTests ())
    {
      final String sExpected = _getInstanceExpected (aTest, aDir, eVersion, aResult);
      if (sExpected == null)
        continue;
      final XsdCompileResult aCompiled;
      try
      {
        aCompiled = aValidator.compile (new CommonsArrayList <> (), eVersion);
      }
      catch (final RuntimeException | StackOverflowError ex)
      {
        _fail (aResult, aTest.name (), sExpected, aTest.document (), _crashMessage (ex));
        continue;
      }
      if (aCompiled.schema () == null)
      {
        if (aCompiled.verdict ().isUnsupported ())
          _unsupported (aResult, aTest.name (), sExpected, aCompiled.verdict ().message ());
        else
          _fail (aResult, aTest.name (), sExpected, aTest.document (), aCompiled.verdict ().message ());
        continue;
      }
      _validate (aCompiled.schema (), aTest, sExpected, aDir, aResult);
    }
  }

  private void _runGroup (@NonNull final XsdTestGroup aGroup,
                          @NonNull final Path aDir,
                          @NonNull final EXsdVersion eVersion,
                          @NonNull final IXsdTestValidator aValidator,
                          @NonNull final XsdSetResult aResult)
  {
    if (!XsdSuiteRules.isVersionApplicable (aGroup.version (), eVersion))
    {
      // Not applicable to this version: excluded from the denominator
      return;
    }
    final XsdSchemaTest aSchemaTest = aGroup.schemaTest ();
    if (aSchemaTest == null)
    {
      _runSchemalessInstances (aGroup, aDir, eVersion, aValidator, aResult);
      return;
    }
    if (!XsdSuiteRules.isVersionApplicable (aSchemaTest.version (), eVersion))
    {
      // Schema test out of scope: the instance verdicts have nothing to stand on
      return;
    }
    final String sSchemaExpected = XsdSuiteRules.getExpectedValidity (aSchemaTest.expected (), eVersion);
    if (sSchemaExpected == null || XsdSuiteRules.INDETERMINATE.equals (sSchemaExpected))
    {
      aResult.addSkip ("no-schema-verdict");
      return;
    }

    final ICommonsList <Path> aDocs = new CommonsArrayList <> ();
    for (final String sHref : aSchemaTest.documents ())
    {
      final Path aDoc = aDir.resolve (sHref).normalize ();
      if (!Files.isRegularFile (aDoc))
      {
        aResult.addSkip ("missing-schema-file");
        return;
      }
      aDocs.add (aDoc);
    }
    if (aDocs.isEmpty ())
    {
      aResult.addSkip ("no-schema-doc");
      return;
    }
    final String sSchemaHref = aSchemaTest.documents ().getFirstOrNull ();

    final int nPassBefore = aResult.getPass ();
    final int nFailBefore = aResult.getFail ();
    final int nUnsupportedBefore = aResult.getUnsupported ();
    final XsdCompileResult aCompiled;
    try
    {
      aCompiled = aValidator.compile (aDocs, eVersion);
    }
    catch (final RuntimeException | StackOverflowError ex)
    {
      _fail (aResult, aSchemaTest.name (), sSchemaExpected, sSchemaHref, _crashMessage (ex));
      aResult.addSchemaTestOutcome (0, 1, 0);
      return;
    }
    if (XsdSuiteRules.isQueried (aSchemaTest.currentStatuses ()))
    {
      // W3C-disputed verdict: excluded from the denominator
      aResult.addQueried ();
    }
    else
      _score (aResult, aSchemaTest.name (), sSchemaExpected, sSchemaHref, aCompiled.verdict ());
    aResult.addSchemaTestOutcome (aResult.getPass () - nPassBefore,
                                  aResult.getFail () - nFailBefore,
                                  aResult.getUnsupported () - nUnsupportedBefore);

    // Instance tests only make sense when the schema is expected to be valid
    if (!XsdSuiteRules.VALID.equals (sSchemaExpected))
      return;
    for (final XsdInstanceTest aTest : aGroup.instanceTests ())
    {
      final String sExpected = _getInstanceExpected (aTest, aDir, eVersion, aResult);
      if (sExpected == null)
        continue;
      final IXsdTestSchema aSchema = aCompiled.schema ();
      if (aSchema != null)
        _validate (aSchema, aTest, sExpected, aDir, aResult);
      else
        if (aCompiled.verdict ().isUnsupported ())
        {
          // The validator cannot compile the schema
          _unsupported (aResult, aTest.name (), sExpected, aCompiled.verdict ().message ());
        }
        else
        {
          // A schema the suite says is valid was rejected; the instance verdict is unreachable
          _fail (aResult, aTest.name (), sExpected, aTest.document (), "schema rejected: " + aCompiled.verdict ().message ());
        }
    }
  }

  @NonNull
  private XsdSetResult _runSet (@NonNull final String sHref, @NonNull final EXsdVersion eVersion)
  {
    if (m_aSettings.progress ())
      System.err.println ("XSD " + eVersion.getID () + " SET " + sHref);
    final XsdSetResult ret = new XsdSetResult (sHref);
    final XsdTestSet aSet;
    try
    {
      aSet = XsdCatalogReader.readTestSet (m_aSettings.suiteDir ().resolve (sHref));
    }
    catch (final IOException ex)
    {
      ret.setLoadError (ex.getMessage ());
      return ret;
    }
    if (!XsdSuiteRules.isVersionApplicable (aSet.version (), eVersion))
    {
      // The whole set is out of scope for this version (e.g. the 1.1-only sets)
      return ret;
    }
    final IXsdTestValidator aValidator = m_aValidatorFactory.get ();
    for (final XsdTestGroup aGroup : aSet.groups ())
      _runGroup (aGroup, aSet.dir (), eVersion, aValidator, ret);
    return ret;
  }

  /**
   * Run one version of the suite.
   *
   * @param eVersion
   *        The version.
   * @return The result.
   * @throws IOException
   *         if <code>suite.xml</code> cannot be read
   */
  @NonNull
  public XsdRunResult run (@NonNull final EXsdVersion eVersion) throws IOException
  {
    final ICommonsList <String> aRefs = new CommonsArrayList <> ();
    for (final String sHref : XsdCatalogReader.readSuite (m_aSettings.getSuiteFile ()))
      if (m_aSettings.only ().isEmpty () || sHref.contains (m_aSettings.only ()))
        aRefs.add (sHref);

    final IXsdTestValidator aInfo = m_aValidatorFactory.get ();
    final ICommonsList <XsdSetResult> aResults = new CommonsArrayList <> ();
    final int nThreads = m_aSettings.sequential () ? 1 : Math.max (1, Runtime.getRuntime ().availableProcessors ());
    final ExecutorService aExecutor = Executors.newFixedThreadPool (nThreads);
    try
    {
      final ICommonsList <Future <XsdSetResult>> aFutures = new CommonsArrayList <> ();
      for (final String sHref : aRefs)
        aFutures.add (aExecutor.submit ( () -> _runSet (sHref, eVersion)));
      for (int i = 0; i < aFutures.size (); ++i)
      {
        try
        {
          aResults.add (aFutures.get (i).get ());
        }
        catch (final ExecutionException ex)
        {
          // _runSet guards every validator call, so this is a harness bug
          final XsdSetResult aBroken = new XsdSetResult (aRefs.get (i));
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
    return new XsdRunResult (eVersion,
                             m_aSettings.validator ().getFilePrefix (),
                             aInfo.getDisplayName (),
                             aInfo.isVersionSupported (eVersion),
                             aResults);
  }

  /**
   * Run every configured version, print the summary (and the collected lines
   * if requested) and write the output files.
   *
   * @return One result per version, in the configured order.
   * @throws IOException
   *         if the suite cannot be read or the output cannot be written
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XsdRunResult> runAndReport () throws IOException
  {
    final ICommonsList <XsdRunResult> ret = new CommonsArrayList <> ();
    for (final EXsdVersion eVersion : m_aSettings.versions ())
    {
      final XsdRunResult aResult = run (eVersion);
      for (final XsdSetResult aSet : aResult.getAllSets ())
        for (final String sLine : aSet.getLines ())
          System.out.println ("[" + eVersion.getID () + "] " + sLine);
      System.out.print (aResult.getSummary ());
      aResult.writeFiles (m_aSettings.outDir ());
      System.out.println ("fail list written to " +
                          m_aSettings.outDir ().resolve (aResult.getFailFileName ()).toAbsolutePath ());
      ret.add (aResult);
    }
    return ret;
  }
}
