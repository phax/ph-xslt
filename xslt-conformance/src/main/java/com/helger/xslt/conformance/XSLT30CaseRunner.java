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
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSortedMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.conformance.QT3EngineEvaluator;
import com.helger.xpath.conformance.QT3Outcome;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;

/**
 * Runs one xslt30-test case, a port of Go's <code>runXSLTCase</code>: applies
 * the dependency filter and the Unicode exclusions, finds the principal
 * stylesheet (or package, or a stylesheet embedded in the source), compiles it
 * with its file URI as base URI (so <code>xsl:import</code>/
 * <code>xsl:include</code> resolve), builds the transformation request from the
 * environment and the <code>&lt;test&gt;</code> element, transforms, and
 * checks the assertion.
 * <p>
 * An {@link XPathException} (including <code>XsltException</code>) from
 * compiling, preparing or transforming is the error the assertions see. Any
 * other {@link Throwable} fails the case as <code>crash:&lt;Class&gt;</code>.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30CaseRunner
{
  private static final XdmQName INITIAL_TEMPLATE = new XdmQName (XdmConstants.NS_XSL, "initial-template", "xsl");
  private static final Pattern XML_DECL_ENCODING = Pattern.compile ("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']");
  private static final Pattern PSEUDO_ATTR_HREF = Pattern.compile ("href\\s*=\\s*[\"']([^\"']*)[\"']");
  private static final int MAX_DETAIL_LEN = 400;

  /**
   * The outcome of one case.
   *
   * @param outcome
   *        Pass, fail or skip with a short reason.
   * @param detail
   *        More detail for the fail dump (e.g. an exception message), may be
   *        empty.
   */
  public record CaseResult (@NonNull QT3Outcome outcome, @NonNull String detail)
  {
    @NonNull
    static CaseResult skip (@NonNull final String sReason)
    {
      return new CaseResult (QT3Outcome.skip (sReason), "");
    }
  }

  /** A case the harness cannot set up (e.g. an unbound prefix in the catalog). */
  private static final class HarnessException extends RuntimeException
  {
    HarnessException (@NonNull final String sMessage)
    {
      super (sMessage);
    }
  }

  private final XSLT30Catalog m_aCatalog;
  private final IXSLT30Compiler m_aCompiler;
  private final XSLT30StylesheetCache m_aCache;
  private final OffsetDateTime m_aNow;
  private final boolean m_bStrictErrorCodes;
  private final boolean m_bRunUnicodeExcluded;
  private final boolean m_bSchemaAware;

  /**
   * @param aCatalog
   *        The catalog (for catalog level environments).
   * @param aCompiler
   *        The stylesheet compiler, normally {@link IXSLT30Compiler#ENGINE}.
   * @param aCache
   *        The compile cache of the run.
   * @param aNow
   *        The current date/time of the run.
   * @param bStrictErrorCodes
   *        <code>true</code> to require matching error codes.
   * @param bRunUnicodeExcluded
   *        <code>true</code> to run the cases excluded for their Unicode
   *        version.
   */
  public XSLT30CaseRunner (@NonNull final XSLT30Catalog aCatalog,
                           @NonNull final IXSLT30Compiler aCompiler,
                           @NonNull final XSLT30StylesheetCache aCache,
                           @NonNull final OffsetDateTime aNow,
                           final boolean bStrictErrorCodes,
                           final boolean bRunUnicodeExcluded)
  {
    this (aCatalog, aCompiler, aCache, aNow, bStrictErrorCodes, bRunUnicodeExcluded, false);
  }

  /**
   * @param aCatalog
   *        The catalog (for catalog level environments).
   * @param aCompiler
   *        The stylesheet compiler, normally {@link IXSLT30Compiler#ENGINE}.
   * @param aCache
   *        The compile cache of the run.
   * @param aNow
   *        The current date/time of the run.
   * @param bStrictErrorCodes
   *        <code>true</code> to require matching error codes.
   * @param bRunUnicodeExcluded
   *        <code>true</code> to run the cases excluded for their Unicode
   *        version.
   * @param bSchemaAware
   *        <code>true</code> to claim schema-awareness: stylesheets are
   *        compiled schema-aware, the environment schemas are offered to
   *        xsl:import-schema and sources are validated as the catalog asks.
   */
  public XSLT30CaseRunner (@NonNull final XSLT30Catalog aCatalog,
                           @NonNull final IXSLT30Compiler aCompiler,
                           @NonNull final XSLT30StylesheetCache aCache,
                           @NonNull final OffsetDateTime aNow,
                           final boolean bStrictErrorCodes,
                           final boolean bRunUnicodeExcluded,
                           final boolean bSchemaAware)
  {
    ValueEnforcer.notNull (aCatalog, "Catalog");
    ValueEnforcer.notNull (aCompiler, "Compiler");
    ValueEnforcer.notNull (aCache, "Cache");
    ValueEnforcer.notNull (aNow, "Now");
    m_aCatalog = aCatalog;
    m_aCompiler = aCompiler;
    m_aCache = aCache;
    m_aNow = aNow;
    m_bStrictErrorCodes = bStrictErrorCodes;
    m_bRunUnicodeExcluded = bRunUnicodeExcluded;
    m_bSchemaAware = bSchemaAware;
  }

  @NonNull
  private static String _cut (@Nullable final String s)
  {
    if (s == null)
      return "";
    final String sOneLine = s.strip ().replace ('\n', ' ').replace ('\r', ' ');
    return sOneLine.length () > MAX_DETAIL_LEN ? sOneLine.substring (0, MAX_DETAIL_LEN) : sOneLine;
  }

  /**
   * @return The case's environment: the inline one, or the named one it
   *         references (test set first, then catalog). An unknown reference
   *         gives an empty environment, like Go.
   */
  @Nullable
  private XSLT30Environment _resolveEnvironment (@NonNull final XSLT30TestSet aSet,
                                                 @NonNull final XSLT30TestCase aCase)
  {
    final XSLT30Environment aEnv = aCase.environment ();
    if (aEnv == null || !aEnv.isReference ())
      return aEnv;
    final XSLT30Environment aFound = aSet.environments ().get (aEnv.ref ());
    return aFound != null ? aFound : m_aCatalog.environments ().get (aEnv.ref ());
  }

  /**
   * Read an XML file as text, honouring a byte order mark and the encoding
   * declaration, so that the compiler can parse it from a string.
   */
  @NonNull
  static String readXmlText (@NonNull final Path aFile) throws IOException
  {
    final byte [] aBytes = Files.readAllBytes (aFile);
    final int n = aBytes.length;
    if (n >= 3 && (aBytes[0] & 0xff) == 0xef && (aBytes[1] & 0xff) == 0xbb && (aBytes[2] & 0xff) == 0xbf)
      return new String (aBytes, 3, n - 3, StandardCharsets.UTF_8);
    if (n >= 2 && (aBytes[0] & 0xff) == 0xfe && (aBytes[1] & 0xff) == 0xff)
      return new String (aBytes, 2, n - 2, StandardCharsets.UTF_16BE);
    if (n >= 2 && (aBytes[0] & 0xff) == 0xff && (aBytes[1] & 0xff) == 0xfe)
      return new String (aBytes, 2, n - 2, StandardCharsets.UTF_16LE);
    final String sHead = new String (aBytes, 0, Math.min (n, 200), StandardCharsets.ISO_8859_1);
    final Matcher aMatcher = XML_DECL_ENCODING.matcher (sHead);
    Charset aCharset = StandardCharsets.UTF_8;
    if (aMatcher.find ())
      try
      {
        aCharset = Charset.forName (aMatcher.group (1));
      }
      catch (final IllegalArgumentException ex)
      {
        // Unknown encoding: leave it to the compiler to complain about UTF-8
      }
    return new String (aBytes, aCharset);
  }

  /**
   * Find the stylesheet element embedded in a source document, named by its
   * <code>&lt;?xml-stylesheet href="#id"?&gt;</code> (XSLT 3.0 section 3.13).
   */
  @NonNull
  private static IXdmNode _findEmbeddedStylesheet (@NonNull final IXdmNode aDoc)
  {
    String sID = null;
    for (final IXdmNode aChild : aDoc.getAllChildren ())
      if (aChild.getKind () == ENodeKind.PROCESSING_INSTRUCTION &&
          "xml-stylesheet".equals (aChild.getName ().localName ()))
      {
        final Matcher aMatcher = PSEUDO_ATTR_HREF.matcher (aChild.getValue ());
        if (aMatcher.find () && aMatcher.group (1).startsWith ("#"))
        {
          sID = aMatcher.group (1).substring (1);
          break;
        }
      }
    if (sID != null)
    {
      // Iterative walk in document order
      final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> (aDoc);
      while (aStack.isNotEmpty ())
      {
        final IXdmNode aNode = aStack.removeLast ();
        if (aNode.isElement ())
        {
          final IXdmNode aXmlID = aNode.getAttribute (XdmConstants.NS_XML, "id");
          final IXdmNode aID = aNode.getAttribute ("", "id");
          if ((aXmlID != null && sID.equals (aXmlID.getValue ())) || (aID != null && sID.equals (aID.getValue ())))
            return aNode;
        }
        for (int i = aNode.getChildCount () - 1; i >= 0; --i)
          aStack.add (aNode.getChildAtIndex (i));
      }
    }
    throw XPathException.of ("XTSE0010", "No embedded stylesheet found via <?xml-stylesheet href=\"#id\"?>");
  }

  /**
   * Evaluate a catalog expression (parameter value, context item, initial
   * match selection) with the namespaces of its catalog element, the
   * stylesheet URI as static base URI (as if the stylesheet evaluated it) and
   * the source document as focus.
   */
  @NonNull
  private XdmSequence _evaluate (@NonNull final String sExpr,
                                 @NonNull final Map <String, String> aNamespaces,
                                 @Nullable final IXdmItem aFocus,
                                 @NonNull final XSLT30ResourceResolver aResolver,
                                 @NonNull final String sBaseUri)
  {
    final QT3EngineEvaluator aEval = new QT3EngineEvaluator (m_aNow);
    aEval.setBaseUri (sBaseUri);
    for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
      if (!"xml".equals (aEntry.getKey ()))
        aEval.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    aEval.setContextItem (aFocus);
    aEval.setResourceResolver (aResolver);
    return aEval.evaluate (sExpr, new CommonsLinkedHashMap <> ());
  }

  /**
   * @return The parameter value; a catalog <code>as</code> that names an
   *         atomic type is applied by constructing it (the value is supplied
   *         as that type), like Go does.
   */
  @NonNull
  private XdmSequence _evaluateParam (@NonNull final XSLT30Param aParam,
                                      @Nullable final IXdmItem aFocus,
                                      @NonNull final XSLT30ResourceResolver aResolver,
                                      @NonNull final String sBaseUri)
  {
    String sExpr = aParam.select ();
    final String sAs = aParam.as ();
    if (!sAs.isEmpty () && sAs.matches ("[\\p{L}_][\\p{L}\\p{N}._:-]*") && !sAs.contains ("("))
      sExpr = sAs + "((" + sExpr + "))";
    return _evaluate (sExpr, aParam.namespaces (), aFocus, aResolver, sBaseUri);
  }

  @NonNull
  private static XdmQName _requireName (@NonNull final XSLT30Param aParam)
  {
    final XdmQName ret = aParam.name ();
    if (ret == null)
      throw new HarnessException ("Unbound prefix in catalog parameter name '" + aParam.lexicalName () + "'");
    return ret;
  }

  @NonNull
  private QT3Outcome _check (@NonNull final XSLT30TestSet aSet,
                             @NonNull final XSLT30TestCase aCase,
                             @NonNull final XSLT30Actual aActual,
                             @Nullable final IXdmSchemaSet aSchemaSet)
  {
    return new XSLT30AssertionChecker (aSet.dir (), m_aNow, m_bStrictErrorCodes, aSchemaSet).check (aCase.result (), aActual);
  }

  private static void _addPackage (@NonNull final ICommonsList <XsltCompileOptions.PackageSource> aPackages,
                                   @NonNull final Path aPath,
                                   final XSLT30Environment.@NonNull Module aModule)
  {
    final String sUri = XSLT30ResourceResolver.getFileUri (aPath);
    if (!aModule.uri ().isEmpty ())
      aPackages.add (new XsltCompileOptions.PackageSource (null,
                                                           null,
                                                           sUri,
                                                           aModule.uri ().strip (),
                                                           aModule.packageVersion ().isBlank () ? null : aModule.packageVersion ().strip ()));
    aPackages.add (new XsltCompileOptions.PackageSource (null, null, sUri));
  }

  @NonNull
  private XSLT30Actual _transform (@NonNull final XSLT30TestSet aSet,
                                   @NonNull final XSLT30TestCase aCase,
                                   @Nullable final XSLT30Environment aEnv,
                                   @NonNull final Path aStylesheet,
                                   final boolean bEmbedded,
                                   @NonNull final XSLT30DocumentCache aDocs,
                                   @NonNull final Wrapper <IXdmSchemaSet> aSchemaSetOut)
  {
    final Path aDir = aSet.dir ().toAbsolutePath ().normalize ();
    final XSLT30Invocation aTest = aCase.test ();
    final XSLT30ResourceResolver aResolver = new XSLT30ResourceResolver (aEnv, aDir, aDocs);
    final String sBaseUri = XSLT30ResourceResolver.getFileUri (aStylesheet);

    // Parameters: the environment's first, the test's override them
    final ICommonsOrderedMap <XdmQName, XSLT30Param> aStaticParams = new CommonsLinkedHashMap <> ();
    final ICommonsOrderedMap <XdmQName, XSLT30Param> aParams = new CommonsLinkedHashMap <> ();
    final ICommonsList <XSLT30Param> aAllParams = new CommonsArrayList <> ();
    if (aEnv != null)
      aAllParams.addAll (aEnv.params ());
    aAllParams.addAll (aTest.params ());
    for (final XSLT30Param aParam : aAllParams)
      (aParam.isStatic () ? aStaticParams : aParams).put (_requireName (aParam), aParam);

    // Library packages for xsl:use-package: those of the environment and the
    // secondary <package> elements of the test. The catalogue's uri and
    // package-version are only a hint, so each file is also registered with
    // the identity its own xsl:package declares.
    final ICommonsList <XsltCompileOptions.PackageSource> aPackages = new CommonsArrayList <> ();
    if (aEnv != null)
      for (final XSLT30Environment.Module aModule : aEnv.packages ())
        if (!aModule.file ().isEmpty ())
          _addPackage (aPackages, XSLT30ResourceResolver.getAbsolutePath (aEnv.baseDir (), aModule.file ()), aModule);
    for (final XSLT30Environment.Module aModule : aTest.packages ())
      if (aModule.isSecondary () && !aModule.file ().isEmpty ())
        _addPackage (aPackages, XSLT30ResourceResolver.getAbsolutePath (aDir, aModule.file ()), aModule);
    final StringBuilder aPackageKey = new StringBuilder ();
    for (final XsltCompileOptions.PackageSource aPackage : aPackages)
      aPackageKey.append ('\u0001').append (aPackage.name ()).append ('@').append (aPackage.version ()).append ('=').append (aPackage.baseUri ());

    // Compile
    final ICommonsSortedMap <String, String> aStaticKey = new CommonsTreeMap <> ();
    for (final XSLT30Param aParam : aStaticParams.values ())
      aStaticKey.put (aParam.name ().toString (), aParam.as () + "\u0001" + aParam.select ());
    // Schema-aware: the schemas of the environment are offered to
    // xsl:import-schema
    final ICommonsList <XsltCompileOptions.HostSchema> aHostSchemas = m_bSchemaAware ? XSLT30SchemaSupport.getHostSchemas (aEnv,
                                                                                                                         m_aCatalog.rootDir ())
                                                                                     : new CommonsArrayList <> ();
    final String sKey = aStylesheet.toString () +
                        (bEmbedded ? "\u0003embedded" : "") +
                        "\u0000" +
                        aStaticKey +
                        aPackageKey +
                        (m_bSchemaAware ? "\u0004" + aHostSchemas : "");
    final XsltExecutable aExecutable = m_aCache.getOrCompile (sKey, () -> {
      final XsltCompileOptions aOptions = new XsltCompileOptions ().setLoadExternalEntities (true);
      aOptions.packages ().addAll (aPackages);
      if (m_bSchemaAware)
      {
        aOptions.setSchemaAware (true);
        aOptions.hostSchemas ().addAll (aHostSchemas);
      }
      for (final XSLT30Param aParam : aStaticParams.values ())
        aOptions.setStaticParam (aParam.name (), _evaluateParam (aParam, null, aResolver, sBaseUri));
      if (bEmbedded)
      {
        // The source carries the stylesheet (defines-stylesheet="true")
        aOptions.setBaseUri (sBaseUri);
        final IXdmNode aDoc;
        try
        {
          aDoc = new XdmParser ().setLoadExternalEntities (true).parse (aStylesheet);
        }
        catch (final XdmParseException ex)
        {
          throw XPathException.of ("XTSE0010", "Cannot read the source with the embedded stylesheet: " + ex.getMessage ());
        }
        return m_aCompiler.compile (_findEmbeddedStylesheet (aDoc), aOptions);
      }
      final String sText;
      try
      {
        sText = readXmlText (aStylesheet);
      }
      catch (final IOException ex)
      {
        throw XPathException.of ("XTSE0165", "Cannot read stylesheet " + aStylesheet + ": " + ex.getMessage ());
      }
      aOptions.setBaseUri (sBaseUri);
      return m_aCompiler.compile (sText, aOptions);
    });

    // The principal source: role ".", or a source with neither role nor uri
    // when there is no role "." source; the last one found wins, like Go
    IXdmNode aSource = null;
    String sSourceSelect = "";
    if (aEnv != null)
    {
      boolean bHasExplicitPrimary = false;
      for (final XSLT30Environment.Source aSrc : aEnv.sources ())
        if (".".equals (aSrc.role ()))
          bHasExplicitPrimary = true;
      for (final XSLT30Environment.Source aSrc : aEnv.sources ())
      {
        if (!".".equals (aSrc.role ()) && !(aSrc.role ().isEmpty () && aSrc.uri ().isEmpty () && !bHasExplicitPrimary))
          continue;
        try
        {
          final String sValidation = m_bSchemaAware ? XSLT30SchemaSupport.getValidationMode (aEnv, aSrc) : null;
          if (aSrc.content () != null && !aSrc.content ().isBlank ())
          {
            // Inline content has the base URI of the file it is written in
            final InputSource aIS = new InputSource (new StringReader (aSrc.content ()));
            if (!aEnv.catalogLevel () && aSet.file () != null)
              aIS.setSystemId (XSLT30ResourceResolver.getFileUri (aSet.file ()));
            aSource = new XdmParser ().setLoadExternalEntities (true).parse (aIS);
            sSourceSelect = aSrc.select ().strip ();
            if (sValidation != null)
              XSLT30SchemaSupport.validateSource (aSource, sValidation, aExecutable, aEnv);
          }
          else
            if (!aSrc.file ().isEmpty ())
            {
              final Path aPath = XSLT30ResourceResolver.getAbsolutePath (aEnv.baseDir (), aSrc.file ());
              if (Files.isRegularFile (aPath))
              {
                if (sValidation != null)
                {
                  // A fresh tree, as validation annotates it in place
                  aSource = new XdmParser ().setLoadExternalEntities (true).parse (aPath);
                  XSLT30SchemaSupport.validateSource (aSource, sValidation, aExecutable, aEnv);
                }
                else
                  aSource = aDocs.getDocumentOrThrow (aPath);
                sSourceSelect = aSrc.select ().strip ();
              }
            }
            else
              if (!aSrc.select ().isBlank ())
              {
                // Wholly specified by select="parse-xml('...')"
                final XdmSequence aSel = _evaluate (aSrc.select (),
                                                    new CommonsLinkedHashMap <> (),
                                                    null,
                                                    aResolver,
                                                    sBaseUri);
                if (aSel.isSingleton () && aSel.getFirst () instanceof final IXdmNode aNode)
                  aSource = aNode;
                sSourceSelect = "";
              }
        }
        catch (final XdmParseException ex)
        {
          throw XPathException.of ("FODC0002", "Source document is not well-formed: " + ex.getMessage ());
        }
      }
    }

    // Secondary sources the catalog wants validated (read by document()/doc())
    if (m_bSchemaAware && aEnv != null)
      for (final XSLT30Environment.Source aSrc : aEnv.sources ())
      {
        if (".".equals (aSrc.role ()) || aSrc.file ().isEmpty ())
          continue;
        final String sMode = aSrc.validation ().strip ();
        if (!"strict".equals (sMode) && !"lax".equals (sMode))
          continue;
        final Path aPath = XSLT30ResourceResolver.getAbsolutePath (aEnv.baseDir (), aSrc.file ());
        try
        {
          final IXdmNode aDoc = new XdmParser ().setLoadExternalEntities (true).parse (aPath);
          XSLT30SchemaSupport.validateSource (aDoc, sMode, aExecutable, aEnv);
          aResolver.putDocument (aPath, aDoc);
        }
        catch (final XdmParseException ex)
        {
          // left to the engine
        }
      }

    // Assertions may name schema components of the stylesheet
    aSchemaSetOut.set (aExecutable.getSchemaSet ());

    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setResolver (aResolver);
    aRequest.setCurrentDateTime (m_aNow);

    // Global context item
    IXdmItem aContextItem = aSource;
    final String sContextSelect = aTest.contextItemSelect () != null ? aTest.contextItemSelect () : sSourceSelect;
    if (!sContextSelect.isBlank ())
    {
      final XdmSequence aSel = _evaluate (sContextSelect, aTest.namespaces (), aSource, aResolver, sBaseUri);
      aContextItem = aSel.isEmpty () ? null : aSel.getFirst ();
    }
    aRequest.setGlobalContextItem (aContextItem);

    for (final XSLT30Param aParam : aParams.values ())
      aRequest.params ().put (aParam.name (), _evaluateParam (aParam, aSource, aResolver, sBaseUri));

    final XSLT30Invocation.Entry aTemplate = aTest.initialTemplate ();
    final XSLT30Invocation.Entry aFunction = aTest.initialFunction ();
    final XSLT30Invocation.Entry aMode = aTest.initialMode ();
    XSLT30Invocation.Entry aEntryWithParams = null;
    if (aTemplate != null)
    {
      if (aTemplate.isNameUnresolved ())
        throw new HarnessException ("Unbound prefix in initial template name '" + aTemplate.lexicalName () + "'");
      aRequest.setInitialTemplate (aTemplate.name () != null ? aTemplate.name () : INITIAL_TEMPLATE);
      aEntryWithParams = aTemplate;
    }
    else
      if (aFunction != null)
      {
        if (aFunction.name () == null)
          throw new HarnessException ("Unbound or missing initial function name '" +
                                           aFunction.lexicalName () +
                                           "'");
        aRequest.setInitialFunction (aFunction.name ());
        for (final XSLT30Param aParam : aFunction.params ())
          aRequest.functionArgs ().add (_evaluateParam (aParam, aSource, aResolver, sBaseUri));
      }
      else
        if (aMode != null)
        {
          if (aMode.isNameUnresolved ())
            throw new HarnessException ("Unbound prefix in initial mode name '" + aMode.lexicalName () + "'");
          aRequest.setInitialMode (aMode.name ());
          if (!aMode.select ().isBlank ())
            aRequest.setInitialMatchSelection (_evaluate (aMode.select (),
                                                          aMode.namespaces (),
                                                          aSource,
                                                          aResolver,
                                                          sBaseUri));
          aEntryWithParams = aMode;
        }
    if (aEntryWithParams != null)
      for (final XSLT30Param aParam : aEntryWithParams.params ())
        aRequest.templateParams ()
                .add (new XsltTransformRequest.TemplateParam (_requireName (aParam),
                                                              _evaluateParam (aParam, aSource, aResolver, sBaseUri),
                                                              aParam.tunnel ()));

    // <output file> establishes the base output URI; "#absent" means none
    String sResultVar = XSLT30Actual.DEFAULT_RESULT_VAR;
    final XSLT30Invocation.Output aOutput = aTest.output ();
    if (aOutput != null)
    {
      if (!"#absent".equals (aOutput.file ()))
      {
        String sUri = XSLT30ResourceResolver.getFileUri (XSLT30ResourceResolver.getAbsolutePath (aDir, aOutput.file ()));
        if (aOutput.file ().isEmpty () && !sUri.endsWith ("/"))
          sUri += "/";
        aRequest.setBaseOutputUri (sUri);
      }
      if (!aOutput.resultVar ().isEmpty ())
        sResultVar = aOutput.resultVar ();
    }

    final XsltTransformResult aResult = aExecutable.transform (aRequest);
    return XSLT30Actual.ofResult (aResult,
                                  aFunction != null || aOutput != null && "no".equals (aOutput.tree ()),
                                  sResultVar,
                                  aOutput != null && "yes".equals (aOutput.serialize ()));
  }

  /**
   * Run one test case.
   *
   * @param aSet
   *        The test set.
   * @param aCase
   *        The test case.
   * @param aDocs
   *        The document cache of the test set.
   * @return The result.
   */
  @NonNull
  public CaseResult run (@NonNull final XSLT30TestSet aSet,
                         @NonNull final XSLT30TestCase aCase,
                         @NonNull final XSLT30DocumentCache aDocs)
  {
    final String sSkip = XSLT30Dependencies.getSkipReason (aSet.dependencies (), aCase.dependencies (), m_bSchemaAware);
    if (sSkip != null)
      return CaseResult.skip (sSkip);
    if (!m_bRunUnicodeExcluded)
    {
      final String sExclusion = XSLT30UnicodeExclusions.getExclusionReason (aSet.name (), aCase);
      if (sExclusion != null)
        return CaseResult.skip (sExclusion);
    }
    if (aCase.result () == null)
      return CaseResult.skip ("no-assertion");

    // The principal module: a <stylesheet> or <package> of the test that is
    // not secondary, else one of the environment, else a stylesheet embedded
    // in a source. All resolve against the test-set directory, like Go.
    final XSLT30Environment aEnv = _resolveEnvironment (aSet, aCase);
    String sFile = "";
    for (final XSLT30Environment.Module aModule : aCase.test ().stylesheets ())
      if (!aModule.isSecondary ())
        sFile = aModule.file ();
    for (final XSLT30Environment.Module aModule : aCase.test ().packages ())
      if (!aModule.isSecondary ())
        sFile = aModule.file ();
    if (sFile.isEmpty () && aEnv != null)
      for (final XSLT30Environment.Module aModule : aEnv.stylesheets ())
        if (!aModule.isSecondary ())
          sFile = aModule.file ();
    boolean bEmbedded = false;
    if (sFile.isEmpty () && aEnv != null)
      for (final XSLT30Environment.Source aSrc : aEnv.sources ())
        if (aSrc.definesStylesheet () && !aSrc.file ().isEmpty ())
        {
          sFile = aSrc.file ();
          bEmbedded = true;
        }
    if (sFile.isEmpty ())
      return CaseResult.skip ("no-stylesheet");
    final Path aStylesheet = XSLT30ResourceResolver.getAbsolutePath (aSet.dir (), sFile);

    XSLT30Actual aActual;
    String sDetail = "";
    final Wrapper <IXdmSchemaSet> aSchemaSet = new Wrapper <> ();
    try
    {
      aActual = _transform (aSet, aCase, aEnv, aStylesheet, bEmbedded, aDocs, aSchemaSet);
    }
    catch (final XPathException ex)
    {
      aActual = XSLT30Actual.ofError (ex);
      sDetail = ex.getCode () + " " + _cut (ex.getMessage ());
    }
    catch (final HarnessException ex)
    {
      return new CaseResult (QT3Outcome.fail ("harness"), _cut (ex.getMessage ()));
    }
    catch (final Throwable t)
    {
      return new CaseResult (QT3Outcome.fail ("crash:" + t.getClass ().getSimpleName ()), _cut (t.getMessage ()));
    }

    try
    {
      final QT3Outcome aOutcome = _check (aSet, aCase, aActual, aSchemaSet.get ());
      if (aOutcome.isFail () && sDetail.isEmpty () && aActual.error () == null)
        sDetail = "got: " + _cut (aActual.output ());
      return new CaseResult (aOutcome, aOutcome.isFail () ? sDetail : "");
    }
    catch (final Throwable t)
    {
      return new CaseResult (QT3Outcome.fail ("crash:" + t.getClass ().getSimpleName ()), _cut (t.getMessage ()));
    }
  }
}
