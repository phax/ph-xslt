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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.conformance.QT3AssertionChecker;
import com.helger.xpath.conformance.QT3EngineEvaluator;
import com.helger.xpath.conformance.QT3Outcome;
import com.helger.xpath.conformance.QT3ResourceResolver;
import com.helger.xpath.conformance.QT3XmlCompare;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.runtime.NodeCopier;

/**
 * Evaluates xslt30-test assertions against the outcome of a transformation, a
 * port of Go's <code>checkXSLTAssert</code>. Most assertions look at the
 * serialised principal result, reparsed where they need a tree; the ones that
 * inspect a sequence (<code>assert-count</code>, <code>assert-type</code>,
 * ...) and type-annotation questions use the raw result. Expressions are
 * evaluated with the XPath engine, with the namespaces in scope on the
 * assertion in the catalog.
 * <p>
 * Rules taken over from Go: <code>error</code> passes for any error unless
 * strict error codes are requested; every other assertion fails when the
 * transformation raised an error (except <code>not</code>,
 * <code>any-of</code> and <code>assert-serialization-error</code>, which look
 * at their children or the error); <code>assert-warning</code> always passes,
 * as in the W3C reference runner; an assertion on the raw result when there
 * is none is a skip. An {@link XPathException} while evaluating an assertion
 * fails it; any other {@link Throwable} propagates, and the case runner
 * reports it as a crash.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30AssertionChecker
{
  private static final XdmQName VAR_S = new XdmQName ("xslt30-s");
  private static final XdmQName VAR_P = new XdmQName ("xslt30-p");
  private static final XdmQName VAR_F = new XdmQName ("xslt30-f");
  private static final XdmQName VAR_RESULT = new XdmQName ("xslt30-result");

  /** An unclosed HTML void element start tag. */
  private static final Pattern HTML_VOID_TAG = Pattern.compile ("<(area|base|basefont|br|col|embed|frame|hr|img|input|isindex|link|meta|param|source|track|wbr)(\\s[^<>]*[^<>/]|\\s*)>",
                                                                Pattern.CASE_INSENSITIVE);

  /** A whitespace-only run between two tags. */
  private static final Pattern INDENTATION = Pattern.compile (">[ \\t\\r\\n]+<");
  /** A run of XML whitespace. */
  private static final Pattern WHITESPACE_RUN = Pattern.compile ("[ \\t\\r\\n]+");

  /** Expression fragments only a type-annotated tree can answer. */
  private static final String [] PSVI_MARKERS = { "instance of element(",
                                                  "instance of attribute(",
                                                  "instance of document-node(",
                                                  "instance of schema-element(",
                                                  "schema-element(",
                                                  "schema-attribute(",
                                                  "nilled(",
                                                  "xs:untyped" };

  private final Path m_aDir;
  private final OffsetDateTime m_aNow;
  private final boolean m_bStrictErrorCodes;
  private final IXdmSchemaSet m_aSchemaSet;

  /**
   * @param aDir
   *        Directory of the test set (an assertion's <code>file</code> and a
   *        relative result document href are relative to it).
   * @param aNow
   *        The current date/time of the run.
   * @param bStrictErrorCodes
   *        <code>true</code> to require the error code of an
   *        <code>error</code> assertion to match.
   */
  public XSLT30AssertionChecker (@NonNull final Path aDir,
                                 @NonNull final OffsetDateTime aNow,
                                 final boolean bStrictErrorCodes)
  {
    this (aDir, aNow, bStrictErrorCodes, null);
  }

  /**
   * @param aDir
   *        Directory of the test set.
   * @param aNow
   *        The current date/time of the run.
   * @param bStrictErrorCodes
   *        <code>true</code> to require the error code of an
   *        <code>error</code> assertion to match.
   * @param aSchemaSet
   *        The in-scope schema definitions of the stylesheet (schema-aware
   *        run), for assertions that name schema components; may be
   *        <code>null</code>.
   */
  public XSLT30AssertionChecker (@NonNull final Path aDir,
                                 @NonNull final OffsetDateTime aNow,
                                 final boolean bStrictErrorCodes,
                                 @Nullable final IXdmSchemaSet aSchemaSet)
  {
    ValueEnforcer.notNull (aDir, "Dir");
    ValueEnforcer.notNull (aNow, "Now");
    m_aDir = aDir;
    m_aNow = aNow;
    m_bStrictErrorCodes = bStrictErrorCodes;
    m_aSchemaSet = aSchemaSet;
  }

  /**
   * @return The text trimmed, without a leading XML declaration and the
   *         whitespace after it. Any leading <code>&lt;?xml</code>... up to
   *         the first <code>?&gt;</code> is taken as the declaration, so a
   *         leading <code>&lt;?xml-stylesheet ...?&gt;</code> processing
   *         instruction is removed as well.
   */
  @NonNull
  private static String _stripDecl (@NonNull final String s)
  {
    final String ret = XSLT30XmlCompare.trimSpace (s);
    if (ret.startsWith ("<?xml"))
    {
      final int i = ret.indexOf ("?>");
      if (i >= 0)
        return XSLT30XmlCompare.trimSpace (ret.substring (i + 2));
    }
    return ret;
  }

  /**
   * @return The text without its XML declaration, wrapped in a
   *         <code>&lt;_x_&gt;</code> element so that a fragment with several
   *         top-level nodes parses.
   */
  @NonNull
  private static String _wrapFragment (@NonNull final String s)
  {
    return "<_x_>" + _stripDecl (s) + "</_x_>";
  }

  /**
   * @return The parsed document, or <code>null</code> if the text is not
   *         well-formed.
   */
  @Nullable
  private static IXdmNode _tryParse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      return null;
    }
  }

  /**
   * Parse an xsl:message text as a document node: it may have several element
   * children, or only text, comments or processing instructions.
   *
   * @return The document or <code>null</code> if the text is not a
   *         well-formed fragment.
   */
  @Nullable
  private static IXdmNode _parseMessageDocument (@NonNull final String sMessage)
  {
    final IXdmNode aWrapped = QT3XmlCompare.parseFragment (sMessage);
    if (aWrapped == null || aWrapped.getChildCount () != 1)
      return null;
    final IXdmNode aWrapper = aWrapped.getChildAtIndex (0);
    final IXdmNode ret = IXdmNode.createDocument ();
    for (final IXdmNode aChild : aWrapper.getAllChildren ())
      ret.appendChild (NodeCopier.copyDeep (aChild, true));
    ret.assignDocumentOrder ();
    return ret;
  }

  /**
   * Remove the whitespace that indent="yes" leaves to the serializer of the
   * given method: whitespace-only runs between tags, and for xhtml and html
   * also the length of every other whitespace run.
   */
  @NonNull
  private static String _stripIndentation (@NonNull final String s, @NonNull final String sMethod)
  {
    String ret = s;
    if (SerializationParams.METHOD_XHTML.equals (sMethod) || SerializationParams.METHOD_HTML.equals (sMethod))
      ret = WHITESPACE_RUN.matcher (ret).replaceAll (" ");
    return INDENTATION.matcher (ret).replaceAll ("><");
  }

  /**
   * Make an HTML-serialised result parsable as XML: void element start tags
   * become self-closing and <code>&amp;nbsp;</code> a character reference.
   */
  @NonNull
  private static String _closeHtmlVoidTags (@NonNull final String s)
  {
    return HTML_VOID_TAG.matcher (s.replace ("&nbsp;", "&#xa0;")).replaceAll ("<$1$2/>");
  }

  /**
   * @return <code>true</code> if the expression, whitespace normalised,
   *         contains one of {@link #PSVI_MARKERS}: a plain text test for
   *         expressions that ask about type annotations, which a reparsed
   *         serialisation cannot answer.
   */
  private static boolean _isAssertNeedingPsvi (@NonNull final String sExpr)
  {
    final String sNorm = XSLT30XmlCompare.normalizeSpace (sExpr);
    for (final String sMarker : PSVI_MARKERS)
      if (sNorm.contains (sMarker))
        return true;
    return false;
  }

  /**
   * @return <code>true</code> if the assertion or any nested assertion is an
   *         <code>assert</code> whose expression needs type annotations (see
   *         {@link #_isAssertNeedingPsvi(String)}).
   */
  private static boolean _isTreeNeedingPsvi (@NonNull final XSLT30Assertion aAssertion)
  {
    if ("assert".equals (aAssertion.kind ()) && _isAssertNeedingPsvi (aAssertion.value ()))
      return true;
    for (final XSLT30Assertion aChild : aAssertion.children ())
      if (_isTreeNeedingPsvi (aChild))
        return true;
    return false;
  }

  /** @return The single document node of the raw result or <code>null</code>. */
  @Nullable
  private static IXdmNode _getRawDocument (@NonNull final XSLT30Actual aActual)
  {
    final XdmSequence aRaw = aActual.rawValue ();
    if (aRaw != null && aRaw.isSingleton () && aRaw.getFirst () instanceof final IXdmNode aNode && aNode.isDocument ())
      return aNode;
    return null;
  }

  /**
   * Create the XPath evaluator for an assertion expression: the run's current
   * date/time, the prefixed namespaces in scope on the assertion in the
   * catalog (except <code>xml</code>), the given context item and the
   * in-scope schema of the stylesheet, if any. No base URI and no resource
   * resolver are set.
   */
  @NonNull
  private QT3EngineEvaluator _createEvaluator (@NonNull final XSLT30Assertion aAssertion,
                                               @Nullable final IXdmItem aContextItem)
  {
    final QT3EngineEvaluator ret = new QT3EngineEvaluator (m_aNow);
    for (final Map.Entry <String, String> aEntry : aAssertion.namespaces ().entrySet ())
      if (!"xml".equals (aEntry.getKey ()))
        ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    ret.setContextItem (aContextItem);
    ret.setSchemaSet (m_aSchemaSet);
    return ret;
  }

  /**
   * Evaluate a predicate.
   *
   * @return The effective boolean value, or <code>null</code> if the engine
   *         raised an error or the value is undefined.
   */
  @Nullable
  private Boolean _evalBool (@NonNull final XSLT30Assertion aAssertion,
                             @Nullable final IXdmItem aContextItem,
                             @NonNull final String sExpr,
                             @NonNull final Map <XdmQName, XdmSequence> aVars)
  {
    try
    {
      return QT3AssertionChecker.getEffectiveBooleanValue (_createEvaluator (aAssertion, aContextItem).evaluate (sExpr,
                                                                                                              aVars));
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  /**
   * @return The expected text: the referenced file read like
   *         fn:unparsed-text (byte order mark dropped, declared encoding
   *         honoured) with every CR removed, as the reference runner does; or
   *         the inline value.
   */
  @NonNull
  private String _getExpected (@NonNull final XSLT30Assertion aAssertion)
  {
    final String sFile = aAssertion.getAttribute ("file");
    if (!sFile.isEmpty ())
      try
      {
        final byte [] aBytes = Files.readAllBytes (m_aDir.resolve (sFile));
        final String sEncoding = aAssertion.getAttribute ("encoding");
        String sText = QT3ResourceResolver.decodeText (aBytes, null, sEncoding.isEmpty () ? null : sEncoding);
        if (sText == null)
          sText = new String (aBytes, StandardCharsets.UTF_8);
        return sText.replace ("\r", "");
      }
      catch (final IOException | InvalidPathException ex)
      {
        // fall through to the inline value, like Go
      }
    return aAssertion.value ();
  }

  /**
   * Find the secondary result an <code>assert-result-document</code> names.
   * The first result whose href equals the URI, ends with
   * <code>/</code>URI, or merely has the same last path segment matches.
   *
   * @return The result or <code>null</code> if none matches.
   */
  private static XsltTransformResult.@Nullable SecondaryResult _findSecondary (@NonNull final XSLT30Actual aActual,
                                                                               @NonNull final String sUri)
  {
    final String sBaseName = sUri.substring (sUri.lastIndexOf ('/') + 1);
    for (final XsltTransformResult.SecondaryResult aSec : aActual.secondaries ())
    {
      final String sHref = aSec.href ();
      if (sHref.equals (sUri) ||
          sHref.endsWith ("/" + sUri) ||
          sHref.substring (sHref.lastIndexOf ('/') + 1).equals (sBaseName))
        return aSec;
    }
    return null;
  }

  /**
   * @return An absolute href as it is; otherwise (also for a href that is not
   *         a valid URI) the <code>file:</code> URI of the href resolved against
   *         the test-set directory; <code>null</code> if that is no valid path.
   *         Used as base URI of a reparsed secondary result.
   */
  @Nullable
  private String _resolveHref (@NonNull final String sHref)
  {
    try
    {
      final URI aUri = new URI (sHref);
      if (aUri.isAbsolute ())
        return aUri.toString ();
    }
    catch (final URISyntaxException ex)
    {
      // treat as a file name
    }
    try
    {
      return XSLT30ResourceResolver.getFileUri (m_aDir.resolve (sHref).toAbsolutePath ().normalize ());
    }
    catch (final InvalidPathException ex)
    {
      return null;
    }
  }

  /**
   * Evaluate the expression of an <code>assert</code> with the document as
   * context item. The raw result is bound to the result variable (empty
   * sequence if there is none) unless the variable name is empty.
   *
   * @return Pass or fail with reason <code>assert</code>, or fail with
   *         <code>assert-eval</code> if the evaluation raised an error.
   */
  @NonNull
  private QT3Outcome _evalAssertOn (@NonNull final XSLT30Assertion aAssertion,
                                    @NonNull final IXdmNode aDoc,
                                    @NonNull final XSLT30Actual aActual)
  {
    final ICommonsMap <XdmQName, XdmSequence> aVars = new CommonsHashMap <> ();
    if (!aActual.resultVar ().isEmpty ())
    {
      final XdmSequence aRaw = aActual.rawValue ();
      aVars.put (new XdmQName (aActual.resultVar ()), aRaw != null ? aRaw : XdmSequence.EMPTY);
    }
    final Boolean aValue = _evalBool (aAssertion, aDoc, aAssertion.value ().strip (), aVars);
    if (aValue == null)
      return QT3Outcome.fail ("assert-eval");
    return QT3Outcome.of (aValue.booleanValue (), "assert");
  }

  /**
   * Check an <code>assert</code>: find the document the expression is
   * evaluated on, then evaluate it. For a message, that is the message parsed
   * as a document node. Otherwise the raw principal result tree is used if
   * the expression needs type annotations, or if the output is no markup at
   * all (a text result). Else the serialised output is reparsed, trying in
   * turn: as a document; as a document node with several top-level children;
   * wrapped in <code>&lt;_x_&gt;</code>; with HTML void tags closed (unwrapped,
   * then wrapped); as XML 1.1 (unwrapped, then wrapped). A reparsed document
   * gets the base URI of the outcome if it has none. If nothing parses, the
   * raw tree is the last resort.
   *
   * @return The outcome; fail with <code>assert-parse</code> if there is no
   *         document to evaluate on.
   */
  @NonNull
  private QT3Outcome _checkAssert (@NonNull final XSLT30Assertion aAssertion, @NonNull final XSLT30Actual aActual)
  {
    final IXdmNode aRawDoc = _getRawDocument (aActual);
    if (aActual.message ())
    {
      // The message itself, as a document node
      if (aRawDoc == null)
        return QT3Outcome.fail ("assert-parse");
      return _evalAssertOn (aAssertion, aRawDoc, aActual);
    }
    // Serialising discards type annotations: only the raw tree can answer
    if (aRawDoc != null && _isAssertNeedingPsvi (aAssertion.value ()))
      return _evalAssertOn (aAssertion, aRawDoc, aActual);
    // No markup at all (a text result): the raw tree is that document
    if (aRawDoc != null && !_stripDecl (aActual.output ()).startsWith ("<"))
      return _evalAssertOn (aAssertion, aRawDoc, aActual);

    final String sOutput = aActual.output ();
    IXdmNode aDoc = _tryParse (_stripDecl (sOutput));
    if (aDoc == null)
    {
      // Several top-level elements (or top-level text): a document node with
      // these children, as the result tree has
      aDoc = _parseMessageDocument (_stripDecl (sOutput));
    }
    if (aDoc == null)
      aDoc = _tryParse (_wrapFragment (sOutput));
    if (aDoc == null)
    {
      // An HTML result: close the void tags, unwrapped first
      aDoc = _tryParse (_stripDecl (_closeHtmlVoidTags (sOutput)));
      if (aDoc == null)
        aDoc = _tryParse (_wrapFragment (_closeHtmlVoidTags (sOutput)));
    }
    if (aDoc == null)
    {
      // XML 1.1 only content
      aDoc = _tryParse ("<?xml version=\"1.1\"?>" + _stripDecl (sOutput));
      if (aDoc == null)
        aDoc = _tryParse ("<?xml version=\"1.1\"?>" + _wrapFragment (sOutput));
    }
    if (aDoc != null)
    {
      if (aActual.baseUri () != null && aDoc.getBaseUri () == null)
        aDoc.setBaseUri (aActual.baseUri ());
    }
    else
      aDoc = aRawDoc;
    if (aDoc == null)
      return QT3Outcome.fail ("assert-parse");
    return _evalAssertOn (aAssertion, aDoc, aActual);
  }

  /**
   * Check an assertion on the raw result sequence:
   * <ul>
   * <li><code>assert-empty</code>: the sequence is empty;</li>
   * <li><code>assert-count</code>: its length equals the (trimmed) integer
   * text; a text that is no integer is a skip;</li>
   * <li><code>assert-true</code>/<code>assert-false</code>: a single
   * <code>xs:boolean</code> (or derived) with that value;</li>
   * <li><code>assert-deep-eq</code>: <code>fn:deep-equal</code> of the result
   * and the expression;</li>
   * <li><code>assert-type</code>: <code>$result instance of T</code>;</li>
   * <li><code>assert-permutation</code>: the same number of items, and every
   * item of the result occurs equally often (by <code>fn:deep-equal</code>) in
   * the result and the expected sequence.</li>
   * </ul>
   * The expressions are evaluated without a context item, with the result
   * bound to an internal variable.
   *
   * @return The outcome; fail with <code>&lt;kind&gt;-eval</code> if the
   *         evaluation raised an error, skip for any other kind.
   */
  @NonNull
  private QT3Outcome _checkValueAssert (@NonNull final XSLT30Assertion aAssertion,
                                        @NonNull final XdmSequence aRaw)
  {
    final String sKind = aAssertion.kind ();
    final String sText = aAssertion.value ().strip ();
    switch (sKind)
    {
      case "assert-empty":
        return QT3Outcome.of (aRaw.isEmpty (), sKind);
      case "assert-count":
        try
        {
          return QT3Outcome.of (aRaw.size () == Integer.parseInt (sText), sKind);
        }
        catch (final NumberFormatException ex)
        {
          return QT3Outcome.skip ("assert:" + sKind);
        }
      case "assert-true":
      case "assert-false":
      {
        final boolean bExpected = "assert-true".equals (sKind);
        return QT3Outcome.of (aRaw.isSingleton () &&
                              aRaw.getFirst () instanceof final XdmAtomic aAtomic &&
                              aAtomic.getType ().isDerivedFrom (EAtomicType.BOOLEAN) &&
                              aAtomic.getBoolean () == bExpected,
                              sKind);
      }
      default:
        break;
    }
    final String sExpr;
    switch (sKind)
    {
      case "assert-deep-eq":
        sExpr = "deep-equal($xslt30-result, (" + sText + "))";
        break;
      case "assert-type":
        // Like the W3C reference runner: "$result instance of T"
        sExpr = "$xslt30-result instance of " + sText;
        break;
      case "assert-permutation":
        sExpr = "let $e := (" +
                sText +
                ") return count($xslt30-result) eq count($e) and " +
                "(every $r in $xslt30-result satisfies " +
                "count($xslt30-result[deep-equal(., $r)]) eq count($e[deep-equal(., $r)]))";
        break;
      default:
        return QT3Outcome.skip ("assert:" + sKind);
    }
    final ICommonsMap <XdmQName, XdmSequence> aVars = new CommonsHashMap <> ();
    aVars.put (VAR_RESULT, aRaw);
    final Boolean aValue = _evalBool (aAssertion, null, sExpr, aVars);
    if (aValue == null)
      return QT3Outcome.fail (sKind + "-eval");
    return QT3Outcome.of (aValue.booleanValue (), sKind);
  }

  /**
   * Check one assertion.
   * <p>
   * The combinators and the assertions that look at the error come first:
   * <code>error</code> passes on any error (with strict error codes the code
   * must match, unless it is empty or <code>*</code>);
   * <code>assert-message</code> passes if any <code>xsl:message</code> text
   * satisfies the nested assertion (which sees the message text as output;
   * an <code>assert</code> is evaluated on it as a document node), or
   * without a nested assertion if there is a message at all;
   * <code>assert-serialization-error</code> passes on any error, whatever
   * its code; <code>all-of</code> passes if every child passes (a skipping
   * child fails it); <code>any-of</code> if one child passes;
   * <code>not</code> if its child does not pass, so a child that fails
   * because the transformation raised an error, cannot be evaluated or is
   * skipped makes <code>not</code> pass.
   * <p>
   * Every other assertion fails with <code>errored:&lt;code&gt;</code> after
   * an error. Then:
   * <ul>
   * <li><code>assert-xml</code>: the expected XML (inline or from
   * <code>file</code>) and the output compared by
   * {@link XSLT30XmlCompare#isXmlEqual(String, String, boolean)}; failing
   * that, against the result tree serialised as XML (html and xhtml
   * output).</li>
   * <li><code>assert-serialization</code>: the texts compared trimmed (or
   * whitespace normalised with <code>normalize-space="true"</code>); failing
   * that, after indent="yes" output, with the whitespace the serializer may
   * add removed from both.</li>
   * <li><code>serialization-matches</code>: <code>fn:matches</code> of the
   * output, the trimmed regex and the <code>flags</code>; an invalid regex
   * fails with <code>bad-regex</code>.</li>
   * <li><code>assert-string-value</code>: the string value of the output
   * (reparsed as a fragment if possible, else the text) equals the expected
   * text, both whitespace normalised unless
   * <code>normalize-space="false"</code>.</li>
   * <li><code>assert-result-document</code>: the secondary result named by
   * <code>uri</code> exists, and the nested assertion holds for its
   * serialisation (or its tree, if the nested assertion needs type
   * annotations).</li>
   * <li><code>assert-eq</code>: the expected expression's items, as strings
   * joined by a space, equal the string value of the output, exactly or
   * after trimming. Types and the number of items are not compared.</li>
   * <li><code>assert</code>: see {@link #_checkAssert}.</li>
   * <li><code>assert-count</code>, <code>assert-deep-eq</code>,
   * <code>assert-type</code>, <code>assert-empty</code>,
   * <code>assert-true</code>, <code>assert-false</code>,
   * <code>assert-permutation</code>: on the raw result (skip if there is
   * none), see {@link #_checkValueAssert}.</li>
   * </ul>
   * Any other kind is a skip.
   *
   * @param aAssertion
   *        The assertion.
   * @param aActual
   *        The outcome of the transformation.
   * @return The outcome of the check.
   */
  @NonNull
  public QT3Outcome check (@NonNull final XSLT30Assertion aAssertion, @NonNull final XSLT30Actual aActual)
  {
    final String sKind = aAssertion.kind ();
    final XPathException aError = aActual.error ();
    switch (sKind)
    {
      case "error":
      {
        if (aError == null)
          return QT3Outcome.fail ("expected-error");
        final String sWant = aAssertion.getAttribute ("code").strip ();
        if (m_bStrictErrorCodes && !sWant.isEmpty () && !"*".equals (sWant) && !sWant.equals (aError.getCode ()))
          return QT3Outcome.fail ("error-code");
        return QT3Outcome.pass ();
      }
      case "assert-message":
        if (aAssertion.children ().isEmpty ())
          return QT3Outcome.of (aActual.messages ().isNotEmpty (), "no-message");
        for (final String sMessage : aActual.messages ())
        {
          final IXdmNode aMessageDoc = _parseMessageDocument (sMessage);
          final XSLT30Actual aMessage = new XSLT30Actual (sMessage,
                                                          null,
                                                          aActual.secondaries (),
                                                          aActual.messages (),
                                                          aMessageDoc == null ? null : XdmSequence.of (aMessageDoc),
                                                          "",
                                                          aActual.baseUri (),
                                                          null,
                                                          true,
                                                          null);
          if (check (aAssertion.children ().getFirstOrNull (), aMessage).isPass ())
            return QT3Outcome.pass ();
        }
        return QT3Outcome.fail ("assert-message");
      case "assert-warning":
        // As in the W3C reference runner: warnings cannot be tested
        return QT3Outcome.pass ();
      case "assert-serialization-error":
        return QT3Outcome.of (aError != null, "expected-serialization-error");
      case "all-of":
        for (final XSLT30Assertion aChild : aAssertion.children ())
        {
          final QT3Outcome aOutcome = check (aChild, aActual);
          if (!aOutcome.isPass ())
            return QT3Outcome.fail (aOutcome.isSkip () ? "all-of" : aOutcome.reason ());
        }
        return QT3Outcome.pass ();
      case "any-of":
        for (final XSLT30Assertion aChild : aAssertion.children ())
          if (check (aChild, aActual).isPass ())
            return QT3Outcome.pass ();
        return QT3Outcome.fail ("any-of");
      case "not":
        if (aAssertion.children ().isEmpty ())
          return QT3Outcome.fail ("not");
        return QT3Outcome.of (!check (aAssertion.children ().getFirstOrNull (), aActual).isPass (), "not");
      default:
        break;
    }

    if (aError != null)
      return QT3Outcome.fail ("errored:" + aError.getCode ());

    final String sOutput = aActual.output ();
    final boolean bNormalizeSpace = "true".equals (aAssertion.getAttribute ("normalize-space"));
    switch (sKind)
    {
      case "assert-xml":
      {
        final String sExpected = _getExpected (aAssertion);
        if (XSLT30XmlCompare.isXmlEqual (sExpected, sOutput, bNormalizeSpace))
          return QT3Outcome.pass ();
        // The result tree, without what the html/xhtml serializer adds
        return QT3Outcome.of (aActual.treeXml () != null &&
                              XSLT30XmlCompare.isXmlEqual (sExpected, aActual.treeXml (), bNormalizeSpace),
                              sKind);
      }
      case "assert-serialization":
      {
        final String sExpected = _getExpected (aAssertion);
        if (XSLT30XmlCompare.isSerialEqual (sExpected, sOutput, bNormalizeSpace))
          return QT3Outcome.pass ();
        // With indent="yes" Serialization 3.1 leaves some whitespace to the
        // serializer; only that is ignored
        final String sIndented = aActual.indentedMethod ();
        return QT3Outcome.of (sIndented != null &&
                              XSLT30XmlCompare.isSerialEqual (_stripIndentation (sExpected, sIndented),
                                                              _stripIndentation (sOutput, sIndented),
                                                              false),
                              sKind);
      }
      case "serialization-matches":
      {
        final ICommonsMap <XdmQName, XdmSequence> aVars = new CommonsHashMap <> ();
        aVars.put (VAR_S, XdmSequence.of (XdmAtomic.string (sOutput)));
        aVars.put (VAR_P, XdmSequence.of (XdmAtomic.string (XSLT30XmlCompare.trimSpace (aAssertion.value ()))));
        aVars.put (VAR_F, XdmSequence.of (XdmAtomic.string (aAssertion.getAttribute ("flags"))));
        final Boolean aMatch = _evalBool (aAssertion, null, "matches($xslt30-s, $xslt30-p, $xslt30-f)", aVars);
        if (aMatch == null)
          return QT3Outcome.fail ("bad-regex");
        return QT3Outcome.of (aMatch.booleanValue (), sKind);
      }
      case "assert-string-value":
      {
        String sWant = aAssertion.value ();
        String sGot = sOutput;
        final IXdmNode aDoc = QT3XmlCompare.parseFragment (sOutput);
        if (aDoc != null)
          sGot = aDoc.getStringValue ();
        // normalize-space defaults to true for this assertion
        if (!"false".equals (aAssertion.getAttribute ("normalize-space")))
        {
          sWant = XSLT30XmlCompare.normalizeSpace (sWant);
          sGot = XSLT30XmlCompare.normalizeSpace (sGot);
        }
        return QT3Outcome.of (sWant.equals (sGot), sKind);
      }
      case "assert-result-document":
      {
        final XsltTransformResult.SecondaryResult aSec = _findSecondary (aActual, aAssertion.getAttribute ("uri"));
        if (aSec == null)
          return QT3Outcome.fail ("result-document-missing");
        if (aAssertion.children ().isEmpty ())
          return QT3Outcome.pass ();
        final XSLT30Assertion aChild = aAssertion.children ().getFirstOrNull ();
        XdmSequence aSecRaw = null;
        if (aSec.tree () != null && aSec.tree ().isDocument () && _isTreeNeedingPsvi (aChild))
          aSecRaw = XdmSequence.of (aSec.tree ());
        return check (aChild,
                      new XSLT30Actual (aSec.serialized (),
                                        null,
                                        aActual.secondaries (),
                                        aActual.messages (),
                                        aSecRaw,
                                        "",
                                        _resolveHref (aSec.href ())));
      }
      case "assert-eq":
      {
        final String sWant;
        try
        {
          final XdmSequence aWant = _createEvaluator (aAssertion, null).evaluate ("string-join((" +
                                                                                  aAssertion.value ().strip () +
                                                                                  ") ! string(.), ' ')",
                                                                                  new CommonsHashMap <> ());
          sWant = aWant.isSingleton () && aWant.getFirst () instanceof final XdmAtomic aAtomic ? aAtomic.getString ()
                                                                                                : "";
        }
        catch (final XPathException ex)
        {
          return QT3Outcome.fail ("assert-eq-eval");
        }
        String sGot = _stripDecl (sOutput);
        final IXdmNode aDoc = QT3XmlCompare.parseFragment (sOutput);
        if (aDoc != null)
          sGot = aDoc.getStringValue ();
        return QT3Outcome.of (sWant.equals (sGot) ||
                              XSLT30XmlCompare.trimSpace (sWant).equals (XSLT30XmlCompare.trimSpace (sGot)),
                              sKind);
      }
      case "assert":
        return _checkAssert (aAssertion, aActual);
      case "assert-count":
      case "assert-deep-eq":
      case "assert-type":
      case "assert-empty":
      case "assert-true":
      case "assert-false":
      case "assert-permutation":
      {
        // These inspect the raw result sequence; the serialised output
        // cannot answer them
        final XdmSequence aRaw = aActual.rawValue ();
        if (aRaw == null)
          return QT3Outcome.skip ("assert:" + sKind);
        return _checkValueAssert (aAssertion, aRaw);
      }
      default:
        return QT3Outcome.skip ("assert:" + sKind);
    }
  }
}
