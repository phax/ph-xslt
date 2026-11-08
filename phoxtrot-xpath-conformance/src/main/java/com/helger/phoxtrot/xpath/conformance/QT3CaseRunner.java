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
package com.helger.phoxtrot.xpath.conformance;

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
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.EXmlVersion;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xpath.fn.FormatDecimalFormat;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Runs one test case against the engine, a port of Go's <code>runCase</code>
 * plus its panic guard: binds the environment, evaluates the test expression
 * and checks the first assertion. Any {@link Throwable} that is not an
 * {@link XPathException} counts as a failure ("crash:" + class name) and never
 * aborts the run.
 * <p>
 * A source with <code>validation="strict"</code> (or <code>lax</code>, run
 * strict) is validated against the environment's schemas with the schema
 * processor of the run (see {@link QT3SchemaCache} and
 * {@link QT3ResourceResolver#getSourceDocument}). Unless that is the JDK
 * validator, the compiled schemas are also the in-scope schema definitions
 * of the test expression and of the assertions.
 * <p>
 * A case (or its test set) with the dependency <code>feature staticTyping</code>
 * opts in to the engine's narrow part of the optional Static Typing Feature,
 * for the test expression only, like Go's <code>ParseStaticTyping</code>.
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
  private static final XdmQName PARAM_VAR = new XdmQName ("qt3-param");

  private final OffsetDateTime m_aNow;
  private final boolean m_bStrictErrorCodes;

  /**
   * Constructor without strict error codes.
   *
   * @param aNow
   *        The fixed current date/time of the run.
   */
  public QT3CaseRunner (@NonNull final OffsetDateTime aNow)
  {
    this (aNow, false);
  }

  /**
   * @param aNow
   *        The fixed current date/time of the run.
   * @param bStrictErrorCodes
   *        <code>true</code> to require the expected error codes
   *        (<code>qt3.strictCode</code>).
   */
  public QT3CaseRunner (@NonNull final OffsetDateTime aNow, final boolean bStrictErrorCodes)
  {
    ValueEnforcer.notNull (aNow, "Now");
    m_aNow = aNow;
    m_bStrictErrorCodes = bStrictErrorCodes;
  }

  /**
   * Resolve a lexical variable name like <code>x</code> or
   * <code>test:x</code>.
   *
   * @param sLexical
   *        The lexical name.
   * @param sBoundUri
   *        The namespace URI bound to the prefix in the catalogue file, empty
   *        if none; it wins over the environment's namespaces.
   * @param aEval
   *        The evaluator whose declared namespaces resolve the prefix
   *        otherwise.
   * @return The name; an unprefixed name has no namespace, a prefix bound
   *         nowhere gives the empty namespace URI (no error).
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

  /**
   * Determine the static base URI of the test expression: an explicit
   * <code>static-base-uri</code> of the environment
   * (<code>#UNDEFINED</code> means none), else the file URI of the (last)
   * source with role "." and a file, else the directory of the file the
   * environment comes from, with a trailing slash.
   *
   * @param aEnv
   *        The resolved environment.
   * @return The base URI, or <code>null</code> for an undefined one.
   */
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

  /**
   * @param aDeps
   *        Test-set plus test-case dependencies.
   * @param sType
   *        The dependency type, e.g. <code>feature</code>.
   * @param sValue
   *        The exact (untrimmed) value, e.g. <code>staticTyping</code>.
   * @return <code>true</code> if a dependency with that type and value is
   *         present and not <code>satisfied="false"</code>.
   */
  private static boolean _hasDependency (@NonNull final List <QT3Dependency> aDeps,
                                         @NonNull final String sType,
                                         @NonNull final String sValue)
  {
    for (final QT3Dependency aDep : aDeps)
      if (sType.equals (aDep.type ()) && sValue.equals (aDep.value ()) && aDep.isSatisfied ())
        return true;
    return false;
  }

  /**
   * @return The value of the last satisfied dependency of the given type, or
   *         <code>null</code> if there is none.
   */
  @Nullable
  private static String _getDependencyValue (@NonNull final List <QT3Dependency> aDeps, @NonNull final String sType)
  {
    String ret = null;
    for (final QT3Dependency aDep : aDeps)
      if (sType.equals (aDep.type ()) && aDep.isSatisfied ())
        ret = aDep.value ().trim ();
    return ret;
  }

  /**
   * Resolve the name of a decimal format: a prefix binds from the element's
   * own namespace declarations first, then from the environment's ones.
   */
  @NonNull
  private static XdmQName _decimalFormatName (@NonNull final String sName,
                                              @NonNull final Map <String, String> aAttrs,
                                              @NonNull final QT3Environment aEnv)
  {
    final int nColon = sName.indexOf (':');
    if (nColon < 0)
      return new XdmQName (sName);
    final String sPrefix = sName.substring (0, nColon);
    String sUri = aAttrs.get ("xmlns:" + sPrefix);
    if (sUri == null)
      for (final QT3Environment.Namespace aNs : aEnv.getAllNamespaces ())
        if (aNs.prefix ().equals (sPrefix))
          sUri = aNs.uri ();
    return new XdmQName (sUri == null ? "" : sUri, sName.substring (nColon + 1), sPrefix);
  }

  /**
   * @param sValue
   *        A decimal-format attribute value, may be <code>null</code>.
   * @param nDefault
   *        The code point of the default format.
   * @return The first code point of the value (further characters are
   *         ignored), or the default if it is absent or empty.
   */
  private static int _firstCodePoint (@Nullable final String sValue, final int nDefault)
  {
    return sValue == null || sValue.isEmpty () ? nDefault : sValue.codePointAt (0);
  }

  /**
   * Pass the environment's decimal formats to the evaluator: the unnamed one
   * replaces the default format, named ones are added.
   */
  private static void _applyDecimalFormats (@NonNull final QT3Environment aEnv, @NonNull final QT3EngineEvaluator aEval)
  {
    for (final QT3Environment.DecimalFormat aDF : aEnv.getAllDecimalFormats ())
    {
      final Map <String, String> aAttrs = aDF.attributes ();
      final FormatDecimalFormat aDefault = FormatDecimalFormat.DEFAULT;
      final FormatDecimalFormat.Builder aBuilder = FormatDecimalFormat.builder ();
      aBuilder.decimalSeparator (_firstCodePoint (aAttrs.get ("decimal-separator"), aDefault.getDecimalSeparator ()));
      aBuilder.groupingSeparator (_firstCodePoint (aAttrs.get ("grouping-separator"), aDefault.getGroupingSeparator ()));
      aBuilder.exponentSeparator (_firstCodePoint (aAttrs.get ("exponent-separator"), aDefault.getExponentSeparator ()));
      aBuilder.percent (_firstCodePoint (aAttrs.get ("percent"), aDefault.getPercent ()));
      aBuilder.perMille (_firstCodePoint (aAttrs.get ("per-mille"), aDefault.getPerMille ()));
      aBuilder.zeroDigit (_firstCodePoint (aAttrs.get ("zero-digit"), aDefault.getZeroDigit ()));
      aBuilder.digit (_firstCodePoint (aAttrs.get ("digit"), aDefault.getDigit ()));
      aBuilder.patternSeparator (_firstCodePoint (aAttrs.get ("pattern-separator"), aDefault.getPatternSeparator ()));
      aBuilder.minusSign (_firstCodePoint (aAttrs.get ("minus-sign"), aDefault.getMinusSign ()));
      final String sInfinity = aAttrs.get ("infinity");
      if (sInfinity != null)
        aBuilder.infinity (sInfinity);
      final String sNaN = aAttrs.get ("NaN");
      if (sNaN != null)
        aBuilder.nan (sNaN);
      final String sName = aAttrs.get ("name");
      if (sName == null || sName.isEmpty ())
        aEval.setDefaultDecimalFormat (aBuilder.build ());
      else
        aEval.addDecimalFormat (_decimalFormatName (sName.trim (), aAttrs, aEnv), aBuilder.build ());
    }
  }

  /**
   * @param ex
   *        The error the test raised.
   * @return <code>error</code> plus the message, or the error code if there
   *         is no message, for the "got" part of a fail line.
   */
  @NonNull
  private static String _describeError (@NonNull final XPathException ex)
  {
    final String sMsg = ex.getMessage ();
    return "error " + (sMsg == null ? ex.getCode () : sMsg);
  }

  /**
   * Evaluate the value of an environment <code>param</code> and bind it.
   * FOTS says that the variable is declared (in XQuery terms) with the
   * <code>as</code> type; the value must then match that sequence type
   * (XQuery 3.1 section 4.16: SequenceType matching, no conversion), which
   * is checked with <code>instance of</code>. <code>declared</code> only
   * says whether an XQuery prolog declares the variable itself and has no
   * meaning for XPath; the variable is always bound.
   *
   * @param aParam
   *        The parameter, with a <code>select</code>.
   * @param aEval
   *        The evaluator of the test; the parameter is bound in it.
   * @return <code>null</code> if it was bound, else the reason why not.
   */
  @Nullable
  private static String _bindParam (final QT3Environment.@NonNull Param aParam, @NonNull final QT3EngineEvaluator aEval)
  {
    final XdmSequence aValue;
    try
    {
      aValue = aEval.evaluate (aParam.select (), Map.of ());
    }
    catch (final XPathException ex)
    {
      return "param $" + aParam.name () + " select failed: " + ex.getMessage ();
    }
    final String sAs = aParam.as ().strip ();
    if (!sAs.isEmpty ())
    {
      final Boolean aMatches;
      try
      {
        aMatches = QT3AssertionChecker.getEffectiveBooleanValue (aEval.evaluate ("$qt3-param instance of " + sAs,
                                                                                 Map.of (PARAM_VAR, aValue)));
      }
      catch (final XPathException ex)
      {
        return "param $" + aParam.name () + " as '" + sAs + "' cannot be checked: " + ex.getMessage ();
      }
      if (aMatches == null || !aMatches.booleanValue ())
        return "param $" + aParam.name () + " does not match as '" + sAs + "'";
    }
    aEval.setVariable (_varName (aParam.name (), aParam.namespaceUri (), aEval), aValue);
    return null;
  }

  /**
   * Run one case without the crash guard. Steps:
   * <ol>
   * <li>Fail with <code>harness:no-assertion</code> if the case has no
   * assertion; take the expression from the <code>file</code> of
   * <code>test</code> if given (relative to the set directory; unreadable:
   * fail with <code>harness:test-file</code>).</li>
   * <li>Configure the evaluator of the test: namespaces (an empty prefix sets
   * the default element namespace), static base URI, the default collation,
   * XPath 1.0 compatibility for <code>feature xpath-1.0-compatibility</code>,
   * decimal formats, the XML and XSD versions of the last
   * <code>xml-version</code>/<code>xsd-version</code> dependency, the default
   * language of a <code>default-language</code> dependency, the
   * {@link QT3ResourceResolver} and the environment's schema set.</li>
   * <li>Bind the sources: role "." becomes the context item, "$name" a
   * variable; a source that cannot be loaded is left out silently, so an
   * expression reading it raises the error itself.</li>
   * <li>Without a "." source, evaluate <code>context-item select</code>; a
   * single item becomes the context item, an error or any other result
   * leaves no focus.</li>
   * <li>Evaluate every <code>param</code> with a <code>select</code> in
   * order (a param can use the earlier ones), check it against its
   * <code>as</code> and bind it (see {@link #_bindParam}); one that raises an
   * error or does not match fails the case with
   * <code>harness:param</code>.</li>
   * <li>Turn on static typing for <code>feature staticTyping</code> and
   * evaluate the test expression, keeping either its result or its
   * {@link XPathException}.</li>
   * <li>Check the assertion with a second evaluator that has the prefixed
   * namespaces, the versions, the schema set and the variables, but no
   * default element namespace, focus, base URI, resolver, collation or
   * decimal formats, and with the strict error code setting of the
   * runner.</li>
   * </ol>
   *
   * @param aCase
   *        The test case.
   * @param aAllDeps
   *        Test-set plus test-case dependencies.
   * @param aEnv
   *        The resolved environment.
   * @param aSetDir
   *        Directory of the test-set file.
   * @param aDocs
   *        Document cache of the test set.
   * @return The outcome and a description of the result or error.
   */
  @NonNull
  private CaseResult _run (@NonNull final QT3TestCase aCase,
                           @NonNull final List <QT3Dependency> aAllDeps,
                           @NonNull final QT3Environment aEnv,
                           @NonNull final Path aSetDir,
                           @NonNull final QT3DocumentCache aDocs)
  {
    final QT3Assertion aAssertion = aCase.result ();
    if (aAssertion == null)
      return new CaseResult (QT3Outcome.failInconclusive ("harness:no-assertion"), "");

    String sExpr = aCase.testExpr ();
    if (!aCase.testFile ().isEmpty ())
    {
      try
      {
        sExpr = Files.readString (aSetDir.resolve (aCase.testFile ()), StandardCharsets.UTF_8);
      }
      catch (final IOException ex)
      {
        return new CaseResult (QT3Outcome.failInconclusive ("harness:test-file"), ex.getMessage () == null ? "" : ex.getMessage ());
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
    _applyDecimalFormats (aEnv, aEval);
    // The case runs under its own xml-version/xsd-version dependency (like
    // Go); the assertions are evaluated under the same versions
    final EXmlVersion eXmlVersion = EXmlVersion.getFromIDOrNull (_getDependencyValue (aAllDeps, "xml-version"));
    final EXsdVersion eXsdVersion = EXsdVersion.getFromIDOrNull (_getDependencyValue (aAllDeps, "xsd-version"));
    aEval.setXmlVersion (eXmlVersion).setXsdVersion (eXsdVersion);
    aAssertEval.setXmlVersion (eXmlVersion).setXsdVersion (eXsdVersion);
    // A host configuration the catalog asks for (default-language-006)
    aEval.setDefaultLanguage (_getDependencyValue (aAllDeps, "default-language"));

    final QT3ResourceResolver aResolver = new QT3ResourceResolver (aEnv, aDocs);
    aEval.setResourceResolver (aResolver);
    // With Xerces-J, the schemas of the environment are the in-scope schema
    // definitions of the test and its assertions
    final IXdmSchemaSet aSchemaSet = aResolver.getSchemaSet ();
    aEval.setSchemaSet (aSchemaSet);
    aAssertEval.setSchemaSet (aSchemaSet);

    for (final QT3Environment.Source aSrc : aEnv.getAllSources ())
    {
      // A deliberately malformed source is not loaded; reading it is the
      // engine's job (FODC0002)
      final IXdmNode aDoc = aResolver.getSourceDocument (aSrc);
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
      final String sParamProblem = _bindParam (aParam, aEval);
      if (sParamProblem != null)
        return new CaseResult (QT3Outcome.failInconclusive ("harness:param"), sParamProblem);
    }

    // Only the test expression itself, not the environment or the assertions
    aEval.setStaticTyping (_hasDependency (aAllDeps, "feature", "staticTyping"));

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
    final QT3AssertionChecker aChecker = new QT3AssertionChecker (aAssertEval, aSetDir, m_bStrictErrorCodes);
    final QT3Outcome aOutcome = aChecker.check (aAssertion, aResult, aError);
    final String sGot = aError != null ? _describeError (aError) : QT3XmlSerializer.describe (aResult,
                                                                                               MAX_GOT_LEN);
    return new CaseResult (aOutcome, sGot);
  }

  /**
   * Run one test case. Never throws: any {@link Throwable} (including
   * errors such as {@link StackOverflowError}) becomes a fail with the reason
   * <code>crash:</code> plus the simple class name.
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
