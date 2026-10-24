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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Runs one test case against the engine, a port of Go's <code>runCase</code>
 * plus its panic guard: binds the environment, evaluates the test expression
 * and checks the first assertion. Any {@link Throwable} that is not an
 * {@link XPathException} counts as a failure ("crash:" + class name) and never
 * aborts the run.
 * <p>
 * Environment parts the engine has no hook for yet are read but not applied:
 * decimal formats, collections, schemas (no
 * schema-awareness: such cases run unvalidated, as in Go they are attempted),
 * <code>xml-version</code>/<code>xsd-version</code>/<code>default-language</code>
 * dependencies and the optional static typing feature.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3CaseRunner
{
  /** The outcome of one case plus a short description of what was got. */
  public record CaseResult (@NonNull QT3Outcome outcome, @NonNull String got)
  {}

  private static final String UNDEFINED_BASE_URI = "#UNDEFINED";
  private static final int MAX_GOT_LEN = 200;

  private final OffsetDateTime m_aNow;

  /**
   * @param aNow
   *        The fixed current date/time of the run.
   */
  public QT3CaseRunner (@NonNull final OffsetDateTime aNow)
  {
    ValueEnforcer.notNull (aNow, "Now");
    m_aNow = aNow;
  }

  /**
   * Resolve a lexical variable name like <code>x</code> or
   * <code>test:x</code>.
   */
  @NonNull
  private static XdmQName _varName (@NonNull final String sLexical,
                                    @NonNull final String sBoundUri,
                                    @NonNull final QT3EngineEvaluator aEval)
  {
    final int nColon = sLexical.indexOf (':');
    if (nColon <= 0)
      return new XdmQName (sLexical);
    final String sPrefix = sLexical.substring (0, nColon);
    String sUri = sBoundUri;
    if (sUri.isEmpty ())
    {
      final String sEnvUri = aEval.getNamespaceUri (sPrefix);
      sUri = sEnvUri == null ? "" : sEnvUri;
    }
    return new XdmQName (sUri, sLexical.substring (nColon + 1), sPrefix);
  }

  @Nullable
  private static String _getBaseUri (@NonNull final QT3Environment aEnv)
  {
    final String sExplicit = aEnv.getStaticBaseUri ();
    if (sExplicit != null && !sExplicit.isEmpty ())
      return UNDEFINED_BASE_URI.equals (sExplicit) ? null : sExplicit;
    String ret = null;
    for (final QT3Environment.Source aSrc : aEnv.getAllSources ())
      if (".".equals (aSrc.role ()) && !aSrc.file ().isEmpty ())
        ret = QT3ResourceResolver.getFileUri (QT3ResourceResolver.getAbsolutePath (aEnv.getBaseDir (), aSrc.file ()));
    if (ret == null)
    {
      // Neither an explicit static base URI nor a "." source: the directory of
      // the file the environment comes from, like Go
      ret = aEnv.getBaseDir ().toAbsolutePath ().normalize ().toUri ().toString ();
      if (!ret.endsWith ("/"))
        ret += "/";
    }
    return ret;
  }

  private static boolean _hasDependency (@NonNull final List <QT3Dependency> aDeps,
                                         @NonNull final String sType,
                                         @NonNull final String sValue)
  {
    for (final QT3Dependency aDep : aDeps)
      if (sType.equals (aDep.type ()) && sValue.equals (aDep.value ()) && aDep.isSatisfied ())
        return true;
    return false;
  }

  @NonNull
  private static String _describeError (@NonNull final XPathException ex)
  {
    final String sMsg = ex.getMessage ();
    return "error " + (sMsg == null ? ex.getCode () : sMsg);
  }

  @NonNull
  private CaseResult _run (@NonNull final QT3TestCase aCase,
                           @NonNull final List <QT3Dependency> aAllDeps,
                           @NonNull final QT3Environment aEnv,
                           @NonNull final Path aSetDir,
                           @NonNull final QT3DocumentCache aDocs)
  {
    final QT3Assertion aAssertion = aCase.result ();
    if (aAssertion == null)
      return new CaseResult (QT3Outcome.skip ("no-assertion"), "");

    String sExpr = aCase.testExpr ();
    if (!aCase.testFile ().isEmpty ())
    {
      try
      {
        sExpr = Files.readString (aSetDir.resolve (aCase.testFile ()), StandardCharsets.UTF_8);
      }
      catch (final IOException ex)
      {
        return new CaseResult (QT3Outcome.skip ("test-file"), "");
      }
    }

    final QT3EngineEvaluator aEval = new QT3EngineEvaluator (m_aNow);
    final QT3EngineEvaluator aAssertEval = new QT3EngineEvaluator (m_aNow);
    for (final QT3Environment.Namespace aNs : aEnv.getAllNamespaces ())
    {
      if (aNs.prefix ().isEmpty ())
        aEval.setDefaultElementNamespace (aNs.uri ());
      else
      {
        aEval.declareNamespace (aNs.prefix (), aNs.uri ());
        aAssertEval.declareNamespace (aNs.prefix (), aNs.uri ());
      }
    }
    aEval.setBaseUri (_getBaseUri (aEnv));
    for (final QT3Environment.Collation aColl : aEnv.getAllCollations ())
      if (aColl.isDefault ())
        aEval.setDefaultCollation (aColl.uri ());
    aEval.setXPath10Compatibility (_hasDependency (aAllDeps, "feature", "xpath-1.0-compatibility"));

    final QT3ResourceResolver aResolver = new QT3ResourceResolver (aEnv, aDocs);
    aEval.setResourceResolver (aResolver);

    for (final QT3Environment.Source aSrc : aEnv.getAllSources ())
    {
      // A deliberately malformed source is not loaded; reading it is the
      // engine's job (FODC0002)
      final XdmNode aDoc = aResolver.getSourceDocument (aSrc);
      if (aDoc == null)
        continue;
      if (".".equals (aSrc.role ()))
        aEval.setContextItem (aDoc);
      else
        if (aSrc.role ().startsWith ("$"))
          aEval.setVariable (_varName (aSrc.role ().substring (1), "", aEval), XdmSequence.of (aDoc));
    }

    final String sContextItemSelect = aEnv.getContextItemSelect ();
    if (aEval.getContextItem () == null && sContextItemSelect != null && !sContextItemSelect.isEmpty ())
    {
      try
      {
        final XdmSequence aItem = aEval.evaluate (sContextItemSelect, Map.of ());
        if (aItem.isSingleton ())
          aEval.setContextItem (aItem.getFirst ());
      }
      catch (final XPathException ex)
      {
        // No focus then
      }
    }

    for (final QT3Environment.Param aParam : aEnv.getAllParams ())
    {
      if (aParam.select ().isEmpty ())
        continue;
      try
      {
        aEval.setVariable (_varName (aParam.name (), aParam.namespaceUri (), aEval),
                           aEval.evaluate (aParam.select (), Map.of ()));
      }
      catch (final XPathException ex)
      {
        // Like Go: a param that fails to evaluate stays unbound
      }
    }

    XdmSequence aResult = null;
    XPathException aError = null;
    try
    {
      aResult = aEval.evaluate (sExpr, Map.of ());
    }
    catch (final XPathException ex)
    {
      aError = ex;
    }

    // Assertions see the namespaces and variables, but no focus, base URI or
    // resolver (like Go's assertion context)
    for (final Map.Entry <XdmQName, XdmSequence> aEntry : aEval.getAllVariables ().entrySet ())
      aAssertEval.setVariable (aEntry.getKey (), aEntry.getValue ());
    final QT3AssertionChecker aChecker = new QT3AssertionChecker (aAssertEval, aSetDir);
    final QT3Outcome aOutcome = aChecker.check (aAssertion, aResult, aError);
    final String sGot = aError != null ? _describeError (aError) : QT3XmlSerializer.describe (aResult,
                                                                                               MAX_GOT_LEN);
    return new CaseResult (aOutcome, sGot);
  }

  /**
   * Run one test case. Never throws.
   *
   * @param aCase
   *        The test case.
   * @param aAllDeps
   *        Test-set plus test-case dependencies.
   * @param aEnv
   *        The resolved environment (not a reference).
   * @param aSetDir
   *        Directory of the test set file.
   * @param aDocs
   *        Document cache of the test set.
   * @return The result.
   */
  @NonNull
  public CaseResult run (@NonNull final QT3TestCase aCase,
                         @NonNull final List <QT3Dependency> aAllDeps,
                         @NonNull final QT3Environment aEnv,
                         @NonNull final Path aSetDir,
                         @NonNull final QT3DocumentCache aDocs)
  {
    try
    {
      return _run (aCase, aAllDeps, aEnv, aSetDir, aDocs);
    }
    catch (final Throwable t)
    {
      // Includes errors like StackOverflowError: a crash fails the case only
      return new CaseResult (QT3Outcome.fail ("crash:" + t.getClass ().getSimpleName ()),
                             "crash " + t.getClass ().getName () + ": " + t.getMessage ());
    }
  }
}
