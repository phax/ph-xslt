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
package com.helger.phoxtrot.xslt.conformance;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.VisibleForTesting;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSortedMap;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.conformance.QT3EngineEvaluator;
import com.helger.phoxtrot.xpath.conformance.QT3Outcome;
import com.helger.phoxtrot.xpath.context.ResourceUri;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.IXsltMessageListener;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory;
import com.helger.phoxtrot.xslt.jaxp.XdmSource;

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
   * <code>-Dxslt30.writeTo=true</code>: every transformation is run a second
   * time writing to a writer, which must give the same characters (or the
   * same error code); a difference fails the case with "harness".
   */
  static final boolean WRITE_TO = Boolean.getBoolean ("xslt30.writeTo");
  /** The number of transformations compared by the writeTo check. */
  static final AtomicLong WRITE_TO_COMPARED = new AtomicLong (0);

  /**
   * <code>-Dxslt30.jaxp=true</code>: every transformation whose shape JAXP
   * can express (a stylesheet file without packages or static parameters, a
   * principal source document as global context item, stylesheet
   * parameters, the serialized principal result; no initial template,
   * function or mode, not schema-aware, not streamed) is run a second time
   * through the JAXP adapter (<code>PhoxtrotTransformerFactory</code>,
   * <code>StreamSource</code>, <code>StreamResult</code>), which must give
   * the same characters or the same error code; a difference fails the case
   * with "harness".
   */
  static final boolean JAXP = Boolean.getBoolean ("xslt30.jaxp");
  /** The number of transformations compared by the JAXP check. */
  static final AtomicLong JAXP_COMPARED = new AtomicLong (0);

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
  private final boolean m_bStreaming;
  private final boolean m_bBackwardsCompatible;

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
    this (aCatalog, aCompiler, aCache, aNow, bStrictErrorCodes, bRunUnicodeExcluded, bSchemaAware, false);
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
   * @param bStreaming
   *        <code>true</code> to claim streaming: stylesheets are compiled with
   *        the streaming claim and binding streamability declarations, and a
   *        principal source with <code>streaming="true"</code> is declared
   *        streamed.
   */
  public XSLT30CaseRunner (@NonNull final XSLT30Catalog aCatalog,
                           @NonNull final IXSLT30Compiler aCompiler,
                           @NonNull final XSLT30StylesheetCache aCache,
                           @NonNull final OffsetDateTime aNow,
                           final boolean bStrictErrorCodes,
                           final boolean bRunUnicodeExcluded,
                           final boolean bSchemaAware,
                           final boolean bStreaming)
  {
    this (aCatalog, aCompiler, aCache, aNow, bStrictErrorCodes, bRunUnicodeExcluded, bSchemaAware, bStreaming, false);
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
   *        <code>true</code> to claim schema-awareness, see
   *        {@link #XSLT30CaseRunner(XSLT30Catalog, IXSLT30Compiler, XSLT30StylesheetCache, OffsetDateTime, boolean, boolean, boolean)}.
   * @param bStreaming
   *        <code>true</code> to claim streaming, see
   *        {@link #XSLT30CaseRunner(XSLT30Catalog, IXSLT30Compiler, XSLT30StylesheetCache, OffsetDateTime, boolean, boolean, boolean, boolean)}.
   * @param bBackwardsCompatible
   *        <code>true</code> to claim backwards compatibility: stylesheets are
   *        compiled with the XSLT 1.0 compatibility feature
   *        ({@link XsltCompileOptions#setBackwardsCompatibleClaim(boolean)}),
   *        also in the JAXP check.
   */
  public XSLT30CaseRunner (@NonNull final XSLT30Catalog aCatalog,
                           @NonNull final IXSLT30Compiler aCompiler,
                           @NonNull final XSLT30StylesheetCache aCache,
                           @NonNull final OffsetDateTime aNow,
                           final boolean bStrictErrorCodes,
                           final boolean bRunUnicodeExcluded,
                           final boolean bSchemaAware,
                           final boolean bStreaming,
                           final boolean bBackwardsCompatible)
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
    m_bStreaming = bStreaming;
    m_bBackwardsCompatible = bBackwardsCompatible;
  }

  /**
   * @return The text trimmed, on one line (CR and LF become spaces) and cut
   *         to {@link #MAX_DETAIL_LEN} characters, for the fail dump; empty for
   *         <code>null</code>.
   */
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
   * Run the transformation again, writing to a writer, and compare.
   */
  private static void _compareWriteTo (@NonNull final XsltExecutable aExecutable,
                                       @NonNull final XsltTransformRequest aRequest,
                                       @Nullable final String sExpected,
                                       @Nullable final String sExpectedCode)
  {
    WRITE_TO_COMPARED.incrementAndGet ();
    final StringWriter aSW = new StringWriter ();
    String sCode = null;
    try
    {
      aExecutable.transform (aRequest, aSW);
    }
    catch (final XPathException ex)
    {
      sCode = ex.getCode ();
    }
    if (sExpectedCode != null || sCode != null)
    {
      if (sExpectedCode == null || !sExpectedCode.equals (sCode))
        throw new HarnessException ("writeTo: error " + sCode + " instead of " + sExpectedCode);
    }
    else
      if (!aSW.toString ().equals (sExpected))
        throw new HarnessException ("writeTo: different output: " + _cut (aSW.toString ()));
  }

  /** Ignores warnings and lets fatal errors be thrown by the adapter. */
  private static final ErrorListener QUIET_ERROR_LISTENER = new ErrorListener ()
  {
    public void warning (@NonNull final TransformerException ex)
    {}

    public void error (@NonNull final TransformerException ex)
    {}

    public void fatalError (@NonNull final TransformerException ex)
    {}
  };

  /**
   * Run the transformation again through the JAXP adapter and compare: the
   * stylesheet compiled from its URI, the principal source from its file
   * (or as XDM source), the parameters set as XDM values, the documents and
   * texts of the case given by a URI resolver over the case's resolver, and
   * the backwards compatibility claim of the run as factory attribute.
   */
  private static void _compareJaxp (final boolean bBackwardsCompatible,
                                    @NonNull final String sStylesheetUri,
                                    @NonNull final IXdmNode aSource,
                                    @Nullable final Path aSourcePath,
                                    @NonNull final XsltTransformRequest aRequest,
                                    @NonNull final XSLT30ResourceResolver aResolver,
                                    @Nullable final String sExpected,
                                    @Nullable final String sExpectedCode)
  {
    JAXP_COMPARED.incrementAndGet ();
    final URIResolver aURIResolver = (sHref, sBase) -> {
      final IXdmNode aDoc = aResolver.resolveDocument (ResourceUri.of (sHref));
      if (aDoc != null)
        return new XdmSource (aDoc);
      final String sText = aResolver.resolveText (ResourceUri.of (sHref), null);
      return sText == null ? null : new StreamSource (new NonBlockingStringReader (sText), sHref);
    };
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setURIResolver (aURIResolver);
    aTF.setErrorListener (QUIET_ERROR_LISTENER);
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_LOAD_EXTERNAL_ENTITIES, Boolean.TRUE);
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_BACKWARDS_COMPATIBLE, Boolean.valueOf (bBackwardsCompatible));
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MESSAGE_LISTENER, (IXsltMessageListener) aMessage -> {});
    final StringWriter aSW = new StringWriter ();
    String sCode = null;
    try
    {
      final Transformer aTransformer = aTF.newTemplates (new StreamSource (sStylesheetUri)).newTransformer ();
      aTransformer.setErrorListener (QUIET_ERROR_LISTENER);
      for (final Map.Entry <XdmQName, XdmSequence> aEntry : aRequest.params ().entrySet ())
        aTransformer.setParameter (aEntry.getKey ().getEQName (), aEntry.getValue ());
      final Source aJaxpSource = aSourcePath != null ? new StreamSource (XSLT30ResourceResolver.getFileUri (aSourcePath))
                                                     : new XdmSource (aSource);
      final StreamResult aResult = new StreamResult (aSW);
      if (aRequest.getBaseOutputUri () != null)
        aResult.setSystemId (aRequest.getBaseOutputUri ());
      aTransformer.transform (aJaxpSource, aResult);
    }
    catch (final TransformerException ex)
    {
      sCode = ex.getCause () instanceof final XPathException aXEx ? aXEx.getCode () : "jaxp:" + ex.getMessage ();
    }
    if (sExpectedCode != null || sCode != null)
    {
      if (sExpectedCode == null || !sExpectedCode.equals (sCode))
        throw new HarnessException ("jaxp: error " + sCode + " instead of " + sExpectedCode);
    }
    else
      if (!aSW.toString ().equals (sExpected))
        throw new HarnessException ("jaxp: different output: " + _cut (aSW.toString ()));
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

  /**
   * @return The expanded name of the parameter.
   * @throws HarnessException
   *         if the prefix of its name is not bound in the catalog
   */
  @NonNull
  private static XdmQName _requireName (@NonNull final XSLT30Param aParam)
  {
    final XdmQName ret = aParam.name ();
    if (ret == null)
      throw new HarnessException ("Unbound prefix in catalog parameter name '" + aParam.lexicalName () + "'");
    return ret;
  }

  /**
   * Check the case's assertion against the outcome, with a checker for the
   * test-set directory and, in a schema-aware run, the stylesheet's schema
   * set.
   */
  @NonNull
  private QT3Outcome _check (@NonNull final XSLT30TestSet aSet,
                             @NonNull final XSLT30TestCase aCase,
                             @NonNull final XSLT30Actual aActual,
                             @Nullable final IXdmSchemaSet aSchemaSet)
  {
    return new XSLT30AssertionChecker (aSet.dir (), m_aNow, m_bStrictErrorCodes, aSchemaSet).check (aCase.result (), aActual);
  }

  /**
   * The part of the compile cache key for the static parameters: per
   * parameter (sorted by expanded name) its expanded name, <code>as</code>,
   * <code>select</code> and the namespaces in scope on the catalogue element
   * (which resolve the prefixes in <code>select</code> and <code>as</code>);
   * and, if there is any static parameter, the environment the values are
   * evaluated in (its sources, collections and resources are what
   * <code>doc()</code> and friends in a <code>select</code> see).
   *
   * @param aStaticParams
   *        The static parameters.
   * @param aEnv
   *        The environment of the case, may be <code>null</code>.
   * @return The key part, empty without static parameters.
   */
  @NonNull
  @VisibleForTesting
  static String getStaticParamKey (@NonNull final Iterable <XSLT30Param> aStaticParams,
                                   @Nullable final XSLT30Environment aEnv)
  {
    final ICommonsSortedMap <String, String> aKey = new CommonsTreeMap <> ();
    for (final XSLT30Param aParam : aStaticParams)
      aKey.put (String.valueOf (aParam.name ()),
                aParam.as () + "\u0001" + aParam.select () + "\u0001" + aParam.namespaces ());
    if (aKey.isEmpty ())
      return "";
    return aKey + "\u0002" + aEnv;
  }

  /**
   * Register a library package file for <code>xsl:use-package</code>: under
   * the name and version the catalog gives (if it gives a name; a blank
   * version means none), and in any case without a name, so the compiler
   * registers it under the identity its own <code>xsl:package</code>
   * declares.
   */
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

  /**
   * Compile (through the shared cache) and run one case:
   * <ol>
   * <li>Parameters: those of the environment, then those of the test, which
   * override by name; static ones go to the compiler, the others to the
   * request.</li>
   * <li>Library packages: those of the environment and the secondary
   * <code>&lt;package&gt;</code> elements of the test.</li>
   * <li>Compilation, with external entities read, the stylesheet's file URI as
   * base URI and the claimed options (schema-aware with the host schemas,
   * streaming with binding streamability). The cache key is the file,
   * embedded or not, the static parameters (see
   * {@link #getStaticParamKey}), the packages and the host schemas.</li>
   * <li>The principal source: the environment source with role
   * <code>.</code>, or one with neither role nor URI if no role
   * <code>.</code> exists (the last matching one wins). It is inline content
   * (with the test-set file as base URI unless declared in the catalog), a
   * file (from the document cache, or freshly parsed and validated in a
   * schema-aware run), or built by its <code>select</code> alone (e.g.
   * <code>parse-xml(...)</code>). A file that does not exist is a harness
   * failure. Secondary
   * sources with <code>validation</code> are validated and registered with
   * the resolver in a schema-aware run.</li>
   * <li>The global context item: the source, or the first item of the
   * <code>&lt;context-item select&gt;</code> (or the source's
   * <code>select</code>) evaluated with the source as focus.</li>
   * <li>The invocation: an initial template (default
   * <code>xsl:initial-template</code>), else an initial function with its
   * arguments, else an initial mode with its initial match selection; the
   * template or mode parameters, tunnel or not.</li>
   * <li><code>&lt;output file&gt;</code> sets the base output URI (an empty
   * file the test-set directory, <code>#absent</code> none);
   * <code>result-var</code> names the result variable.</li>
   * <li>In a streaming run, a source declared streamed that is nothing but
   * the initial match selection of the (default) initial mode is passed by
   * URI to be read as a stream.</li>
   * </ol>
   * With <code>-Dxslt30.writeTo=true</code> the transformation is repeated
   * into a writer and compared ({@link #_compareWriteTo}).
   *
   * @param aSchemaSetOut
   *        Receives the schema set of the compiled stylesheet, for assertions
   *        that name schema components.
   * @return The outcome to check the assertion against. The raw result is the
   *         returned sequence for an initial function or
   *         <code>&lt;output tree="no"/&gt;</code>; the output is the
   *         serialisation if <code>&lt;output serialize="yes"/&gt;</code> asks
   *         for it (see {@link XSLT30Actual#ofResult}).
   * @throws XPathException
   *         from compiling, preparing or transforming (FODC0002 for a source
   *         that is not well-formed, XTSE0010 for an embedded stylesheet that
   *         is not found, XTSE0165 for a stylesheet file that cannot be read)
   * @throws HarnessException
   *         for an unbound prefix in the catalog, a principal source file
   *         that does not exist or a writeTo difference
   */
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
    final String sStaticKey = getStaticParamKey (aStaticParams.values (), aEnv);
    // Schema-aware: the schemas of the environment are offered to
    // xsl:import-schema
    final ICommonsList <XsltCompileOptions.HostSchema> aHostSchemas = m_bSchemaAware ? XSLT30SchemaSupport.getHostSchemas (aEnv,
                                                                                                                         m_aCatalog.rootDir ())
                                                                                     : new CommonsArrayList <> ();
    final String sKey = aStylesheet.toString () +
                        (bEmbedded ? "\u0003embedded" : "") +
                        "\u0000" +
                        sStaticKey +
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
      if (m_bStreaming)
        aOptions.setStreamingClaim (true).setEnforceStreamability (true);
      // Explicit in both directions: the engine claims it by default
      aOptions.setBackwardsCompatibleClaim (m_bBackwardsCompatible);
      for (final XSLT30Param aParam : aStaticParams.values ())
        aOptions.setStaticParam (aParam.name (), _evaluateParam (aParam, null, aResolver, sBaseUri));
      if (bEmbedded)
      {
        // The source carries the stylesheet (defines-stylesheet="true")
        aOptions.setBaseUri (sBaseUri);
        final IXdmNode aDoc;
        try
        {
          // A stylesheet: with node locations, as the compiler's own parse
          aDoc = new XdmParser ().setRecordLocations (true).setLoadExternalEntities (true).parse (aStylesheet);
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
    boolean bSourceStreamed = false;
    // The file of the principal source, if it can be read as a stream
    Path aStreamablePath = null;
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
          // <source streaming="true"/> declares the principal source streamed
          final String sStreaming = aSrc.streaming ().strip ();
          bSourceStreamed = "true".equals (sStreaming) || "yes".equals (sStreaming);
          aStreamablePath = null;
          if (aSrc.content () != null && !aSrc.content ().isBlank ())
          {
            // Inline content has the base URI of the file it is written in
            final InputSource aIS = new InputSource (new NonBlockingStringReader (aSrc.content ()));
            if (!aEnv.catalogLevel () && aSet.file () != null)
              aIS.setSystemId (XSLT30ResourceResolver.getFileUri (aSet.file ()));
            aSource = new XdmParser ().setRecordLocations (false).setLoadExternalEntities (true).parse (aIS);
            sSourceSelect = aSrc.select ().strip ();
            if (sValidation != null)
              XSLT30SchemaSupport.validateSource (aSource, sValidation, aExecutable, aEnv);
          }
          else
            if (!aSrc.file ().isEmpty ())
            {
              final Path aPath = XSLT30ResourceResolver.getAbsolutePath (aEnv.baseDir (), aSrc.file ());
              if (!Files.isRegularFile (aPath))
                throw new HarnessException ("Principal source file '" + aPath + "' does not exist");
              if (sValidation != null)
              {
                // A fresh tree, as validation annotates it in place
                aSource = new XdmParser ().setRecordLocations (false).setLoadExternalEntities (true).parse (aPath);
                XSLT30SchemaSupport.validateSource (aSource, sValidation, aExecutable, aEnv);
              }
              else
              {
                aSource = aDocs.getDocumentOrThrow (aPath);
                aStreamablePath = aPath;
              }
              sSourceSelect = aSrc.select ().strip ();
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
          final IXdmNode aDoc = new XdmParser ().setRecordLocations (false).setLoadExternalEntities (true).parse (aPath);
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
    if (m_bStreaming && bSourceStreamed)
      aRequest.setSourceStreamed (true);

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

    // A streamed principal source that is only the initial match selection of
    // the initial mode is read as a stream (the engine falls back to the
    // parsed document when it does not stream it)
    if (m_bStreaming &&
        bSourceStreamed &&
        aStreamablePath != null &&
        sContextSelect.isBlank () &&
        aParams.isEmpty () &&
        aTemplate == null &&
        aFunction == null &&
        (aMode == null || (aMode.select ().isBlank () && aMode.params ().isEmpty ())))
    {
      aRequest.setGlobalContextItem (null);
      aRequest.setStreamedSourceUri (XSLT30ResourceResolver.getFileUri (aStreamablePath));
    }

    // The shape JAXP can express, see JAXP
    final boolean bJaxp = JAXP &&
                          !bEmbedded &&
                          aPackages.isEmpty () &&
                          aStaticParams.isEmpty () &&
                          !m_bSchemaAware &&
                          !m_bStreaming &&
                          aSource != null &&
                          aSource.isDocument () &&
                          aContextItem == aSource &&
                          aTemplate == null &&
                          aFunction == null &&
                          aMode == null &&
                          !aRequest.isRawDelivery ();
    final XsltTransformResult aResult;
    try
    {
      aResult = aExecutable.transform (aRequest);
    }
    catch (final XPathException ex)
    {
      if (WRITE_TO)
        _compareWriteTo (aExecutable, aRequest, null, ex.getCode ());
      if (bJaxp)
        _compareJaxp (m_bBackwardsCompatible, sBaseUri, aSource, aStreamablePath, aRequest, aResolver, null, ex.getCode ());
      throw ex;
    }
    if (WRITE_TO && !aRequest.isRawDelivery ())
      _compareWriteTo (aExecutable, aRequest, aResult.principalSerialized (), null);
    if (bJaxp)
      _compareJaxp (m_bBackwardsCompatible,
                    sBaseUri,
                    aSource,
                    aStreamablePath,
                    aRequest,
                    aResolver,
                    aResult.principalSerialized (),
                    null);
    return XSLT30Actual.ofResult (aResult,
                                  aFunction != null || (aOutput != null && "no".equals (aOutput.tree ())),
                                  sResultVar,
                                  aOutput != null && "yes".equals (aOutput.serialize ()),
                                  aRequest.getBaseOutputUri ());
  }

  /**
   * Run one test case.
   * <p>
   * The order: the dependency filter ({@link XSLT30Dependencies}), the
   * Unicode exclusions (unless switched off) and a missing assertion each
   * give a skip. The principal module is the last non-secondary
   * <code>&lt;stylesheet&gt;</code> of the test, overridden by the last
   * non-secondary <code>&lt;package&gt;</code>; else the last non-secondary
   * stylesheet of the environment; else the last source with
   * <code>defines-stylesheet="true"</code> (embedded). It is resolved against
   * the test-set directory; no module at all is a skip
   * (<code>no-stylesheet</code>). An {@link XPathException} from the
   * transformation becomes the error the assertion sees; a harness problem
   * fails the case with <code>harness</code>, any other throwable (also from
   * the check) with <code>crash:&lt;class&gt;</code>. The detail of a fail
   * is the error code and message, or the start of the output.
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
    final String sSkip = XSLT30Dependencies.getSkipReason (aSet.dependencies (),
                                                           aCase.dependencies (),
                                                           m_bSchemaAware,
                                                           m_bStreaming,
                                                           m_bBackwardsCompatible);
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
