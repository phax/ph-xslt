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
package com.helger.xpath.fn;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: fn:doc, doc-available, unparsed-text, unparsed-text-lines,
 * unparsed-text-available, collection, uri-collection, environment-variable,
 * available-environment-variables. Resources are obtained through the
 * {@link com.helger.xpath.context.IResourceResolver} of the dynamic context;
 * relative URIs are resolved against the static base URI. Documents, texts and
 * collections are cached per run, so the same URI yields the same result
 * within one evaluation (stability).
 *
 * @author Philip Helger
 */
@Immutable
public final class FnResource
{
  private static final String RUN_CACHE_DOC = "fn:doc#";
  private static final String RUN_CACHE_TEXT = "fn:unparsed-text#";
  private static final String RUN_CACHE_COLLECTION = "fn:collection#";
  private static final String RUN_CACHE_URI_COLLECTION = "fn:uri-collection#";

  @PresentForCodeCoverage
  private static final FnResource INSTANCE = new FnResource ();

  private FnResource ()
  {}

  /**
   * @param aSeq
   *        An <code>xs:string?</code> argument.
   * @return Its string value, or <code>null</code> for the empty sequence.
   */
  @Nullable
  private static String _getOptionalString (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? null : ((XdmAtomic) aSeq.get (0)).getString ();
  }

  /**
   * Resolve a reference against the static base URI.
   *
   * @param sInvalidCode
   *        Error code if the reference is not a valid URI.
   * @param sUnavailableCode
   *        Error code if the reference is relative and there is no absolute
   *        static base URI.
   * @return The absolute URI.
   */
  @NonNull
  private static String _resolve (@NonNull final DynamicContext aCtx,
                                  @NonNull final String sHref,
                                  @NonNull final String sInvalidCode,
                                  @NonNull final String sUnavailableCode)
  {
    if (!FnUri.isValidUriReference (sHref))
      throw XPathException.of (sInvalidCode, "'" + sHref + "' is not a valid URI");
    if (FnUri.isAbsoluteUri (sHref))
      return FnUri.resolveUri (sHref, sHref);
    final String sBase = aCtx.getStaticContext ().getBaseUri ();
    if (sBase == null || !FnUri.isAbsoluteUri (sBase) || FnUri.hasFragment (sBase))
      throw XPathException.of (sUnavailableCode,
                               "The relative URI '" + sHref + "' cannot be resolved without a static base URI");
    return FnUri.resolveUri (sHref, sBase);
  }

  /**
   * The common part of <code>fn:doc</code> and <code>fn:doc-available</code>
   * (F&amp;O 3.1 sections 14.6.1 and 14.6.2): resolve the reference against
   * the static base URI and return the document the resource resolver delivers
   * for it. A document is cached in the run cache under its absolute URI, so
   * the same URI gives the same node within a run; failures are not cached.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param sHref
   *        The URI reference as given.
   * @return The document node.
   * @throws XPathException
   *         FODC0005 if the reference is not a valid URI, FODC0002 if a
   *         relative reference has no usable base URI or the resolver delivers
   *         nothing (and any error the resolver raises itself).
   */
  @NonNull
  private static IXdmNode _doc (@NonNull final DynamicContext aCtx, @NonNull final String sHref)
  {
    final String sUri = _resolve (aCtx, sHref, "FODC0005", "FODC0002");
    final Map <String, Object> aCache = aCtx.getRunCache ();
    final String sKey = RUN_CACHE_DOC + sUri;
    if (aCache.get (sKey) instanceof final IXdmNode aCached)
      return aCached;
    final IXdmNode aDoc = aCtx.getResourceResolver ().resolveDocument (sUri);
    if (aDoc == null)
      throw XPathException.of ("FODC0002", "The document '" + sUri + "' cannot be retrieved");
    aCache.put (sKey, aDoc);
    return aDoc;
  }

  /**
   * @param s
   *        An encoding name.
   * @return <code>true</code> if it matches the XML production
   *         <code>EncName</code>: an ASCII letter followed by ASCII letters,
   *         digits, <code>.</code>, <code>_</code> or <code>-</code>.
   */
  private static boolean _isEncodingName (@NonNull final String s)
  {
    // XML EncName: [A-Za-z] ([A-Za-z0-9._] | '-')*
    if (s.isEmpty ())
      return false;
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      final boolean bLetter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
      if (!bLetter && (i == 0 || !((c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-')))
        return false;
    }
    return true;
  }

  /**
   * @param s
   *        An encoding name from an <code>$encoding</code> argument.
   * @return <code>true</code> if it is a syntactically valid XML encoding name
   *         that the Java runtime supports as a {@link Charset}.
   */
  private static boolean _isSupportedEncoding (@NonNull final String s)
  {
    if (!_isEncodingName (s))
      return false;
    try
    {
      return Charset.isSupported (s);
    }
    catch (final IllegalArgumentException ex)
    {
      return false;
    }
  }

  /**
   * @param c
   *        A code point.
   * @return <code>true</code> if it matches the XML 1.0 production
   *         <code>Char</code>: tab, line feed, carriage return, U+0020 to
   *         U+D7FF, U+E000 to U+FFFD or U+10000 to U+10FFFF.
   */
  private static boolean _isXmlChar (final int c)
  {
    return c == 0x9 ||
           c == 0xA ||
           c == 0xD ||
           (c >= 0x20 && c <= 0xD7FF) ||
           (c >= 0xE000 && c <= 0xFFFD) ||
           (c >= 0x10000 && c <= 0x10FFFF);
  }

  /**
   * @param s
   *        A text read by <code>fn:unparsed-text</code>.
   * @return <code>true</code> if every code point is an XML character; an
   *         unpaired surrogate counts as invalid.
   */
  private static boolean _hasOnlyXmlChars (@NonNull final String s)
  {
    int i = 0;
    while (i < s.length ())
    {
      final char c = s.charAt (i);
      int nCodePoint = c;
      if (Character.isHighSurrogate (c))
      {
        if (i + 1 >= s.length () || !Character.isLowSurrogate (s.charAt (i + 1)))
          return false;
        nCodePoint = Character.toCodePoint (c, s.charAt (i + 1));
        i += 2;
      }
      else
      {
        if (Character.isLowSurrogate (c))
          return false;
        i++;
      }
      if (!_isXmlChar (nCodePoint))
        return false;
    }
    return true;
  }

  /**
   * The common part of the unparsed-text functions.
   *
   * @return The text, never <code>null</code>.
   * @throws XPathException
   *         FOUT1170 or FOUT1190.
   */
  @NonNull
  private static String _unparsedText (@NonNull final DynamicContext aCtx,
                                       @NonNull final String sHref,
                                       @Nullable final String sEncoding)
  {
    if (sEncoding != null && !_isSupportedEncoding (sEncoding))
      throw XPathException.of ("FOUT1190", "The encoding '" + sEncoding + "' is not supported");
    final String sUri = _resolve (aCtx, sHref, "FOUT1170", "FOUT1170");
    if (FnUri.hasFragment (sUri))
      throw XPathException.of ("FOUT1170", "The URI '" + sUri + "' contains a fragment identifier");
    final Map <String, Object> aCache = aCtx.getRunCache ();
    final String sKey = RUN_CACHE_TEXT + (sEncoding == null ? "" : sEncoding) + "#" + sUri;
    if (aCache.get (sKey) instanceof final String sCached)
      return sCached;
    final String sText = aCtx.getResourceResolver ().resolveText (sUri, sEncoding);
    if (sText == null)
      throw XPathException.of ("FOUT1170", "The resource '" + sUri + "' cannot be retrieved");
    if (!_hasOnlyXmlChars (sText))
      throw XPathException.of ("FOUT1190", "The resource '" + sUri + "' contains characters not permitted in XML");
    aCache.put (sKey, sText);
    return sText;
  }

  /**
   * <code>fn:unparsed-text-lines($href as xs:string?[, $encoding as
   * xs:string]) as xs:string*</code> (F&amp;O 3.1 section 14.6.6): read the
   * text like <code>fn:unparsed-text</code> and split it into lines at line
   * feed, carriage return or carriage return plus line feed. A line terminator
   * at the very end does not start another, empty line. An empty
   * <code>$href</code> gives the empty sequence, but the encoding is still
   * checked.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The lines without their terminators.
   * @throws XPathException
   *         FOUT1170 or FOUT1190 as for <code>fn:unparsed-text</code>.
   */
  @NonNull
  private static XdmSequence _unparsedTextLines (@NonNull final DynamicContext aCtx,
                                                 @NonNull final List <XdmSequence> aArgs)
  {
    final String sHref = _getOptionalString (aArgs.get (0));
    final String sEncoding = aArgs.size () > 1 ? _getOptionalString (aArgs.get (1)) : null;
    if (sHref == null)
    {
      if (sEncoding != null && !_isSupportedEncoding (sEncoding))
        throw XPathException.of ("FOUT1190", "The encoding '" + sEncoding + "' is not supported");
      return XdmSequence.EMPTY;
    }
    final String sText = _unparsedText (aCtx, sHref, sEncoding);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    int nStart = 0;
    int i = 0;
    final int nLen = sText.length ();
    while (i < nLen)
    {
      final char c = sText.charAt (i);
      if (c == '\n' || c == '\r')
      {
        aBuilder.add (XdmAtomic.string (sText.substring (nStart, i)));
        if (c == '\r' && i + 1 < nLen && sText.charAt (i + 1) == '\n')
          i++;
        nStart = i + 1;
      }
      i++;
    }
    if (nStart < nLen)
      aBuilder.add (XdmAtomic.string (sText.substring (nStart)));
    return aBuilder.build ();
  }

  /**
   * @return The absolute collection URI; the empty string for the default
   *         collection.
   */
  @NonNull
  private static String _getCollectionUri (@NonNull final DynamicContext aCtx,
                                           @NonNull final List <XdmSequence> aArgs)
  {
    final String sHref = aArgs.isEmpty () ? null : _getOptionalString (aArgs.get (0));
    if (sHref == null)
      return "";
    return _resolve (aCtx, sHref, "FODC0004", "FODC0002");
  }

  /**
   * <code>fn:collection([$arg as xs:string?]) as item()*</code> (F&amp;O 3.1
   * section 14.6.3): the items the resource resolver delivers for the absolute
   * collection URI, or for the default collection (empty string) if the
   * argument is absent or empty. The result is cached per URI in the run
   * cache.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        No argument or the collection URI.
   * @return The items of the collection.
   * @throws XPathException
   *         FODC0004 for an invalid URI, FODC0002 if a relative URI cannot be
   *         resolved or the resolver knows no such collection.
   */
  @NonNull
  private static XdmSequence _collection (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sUri = _getCollectionUri (aCtx, aArgs);
    final Map <String, Object> aCache = aCtx.getRunCache ();
    final String sKey = RUN_CACHE_COLLECTION + sUri;
    if (aCache.get (sKey) instanceof final XdmSequence aCached)
      return aCached;
    final ICommonsList <? extends IXdmItem> aItems = aCtx.getResourceResolver ().resolveCollection (sUri);
    if (aItems == null)
      throw XPathException.of ("FODC0002",
                               sUri.isEmpty () ? "There is no default collection"
                                               : "The collection '" + sUri + "' cannot be retrieved");
    final XdmSequence ret = XdmSequence.of (aItems);
    aCache.put (sKey, ret);
    return ret;
  }

  /**
   * <code>fn:uri-collection([$arg as xs:string?]) as xs:anyURI*</code>
   * (F&amp;O 3.1 section 14.6.4): the URIs the resource resolver delivers for
   * the absolute collection URI, or for the default collection (empty string)
   * if the argument is absent or empty. The result is cached per URI in the
   * run cache.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        No argument or the collection URI.
   * @return The URIs as xs:anyURI.
   * @throws XPathException
   *         FODC0004 for an invalid URI, FODC0002 if a relative URI cannot be
   *         resolved or the resolver knows no such URI collection.
   */
  @NonNull
  private static XdmSequence _uriCollection (@NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs)
  {
    final String sUri = _getCollectionUri (aCtx, aArgs);
    final Map <String, Object> aCache = aCtx.getRunCache ();
    final String sKey = RUN_CACHE_URI_COLLECTION + sUri;
    if (aCache.get (sKey) instanceof final XdmSequence aCached)
      return aCached;
    final ICommonsList <String> aUris = aCtx.getResourceResolver ().resolveUriCollection (sUri);
    if (aUris == null)
      throw XPathException.of ("FODC0002",
                               sUri.isEmpty () ? "There is no default URI collection"
                                               : "The URI collection '" + sUri + "' cannot be retrieved");
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final String s : aUris)
      aBuilder.add (XdmAtomic.anyUri (s));
    final XdmSequence ret = aBuilder.build ();
    aCache.put (sKey, ret);
    return ret;
  }

  /**
   * <code>fn:doc-available</code>: <code>true</code> if
   * {@link #_doc(DynamicContext, String)} succeeds. A successfully loaded
   * document stays in the run cache, so a following <code>fn:doc</code>
   * returns the same node.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param sHref
   *        The URI reference, <code>null</code> for the empty sequence.
   * @return <code>false</code> for <code>null</code> or if loading raises any
   *         {@link XPathException}.
   */
  private static boolean _isDocAvailable (@NonNull final DynamicContext aCtx, @Nullable final String sHref)
  {
    if (sHref == null)
      return false;
    try
    {
      _doc (aCtx, sHref);
      return true;
    }
    catch (final XPathException ex)
    {
      return false;
    }
  }

  /**
   * <code>fn:unparsed-text-available($href as xs:string?[, $encoding as
   * xs:string]) as xs:boolean</code> (F&amp;O 3.1 section 14.6.7):
   * <code>true</code> if the text can be read and decoded. A text that is read
   * stays in the run cache.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return <code>false</code> for an empty <code>$href</code> or if reading
   *         raises any {@link XPathException}.
   */
  private static boolean _isUnparsedTextAvailable (@NonNull final DynamicContext aCtx,
                                                   @NonNull final List <XdmSequence> aArgs)
  {
    final String sHref = _getOptionalString (aArgs.get (0));
    if (sHref == null)
      return false;
    try
    {
      _unparsedText (aCtx, sHref, aArgs.size () > 1 ? _getOptionalString (aArgs.get (1)) : null);
      return true;
    }
    catch (final XPathException ex)
    {
      return false;
    }
  }

  /**
   * <code>fn:unparsed-text($href as xs:string?[, $encoding as xs:string]) as
   * xs:string?</code> (F&amp;O 3.1 section 14.6.5). An empty
   * <code>$href</code> gives the empty sequence, but the encoding is still
   * checked.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The text or the empty sequence.
   * @throws XPathException
   *         FOUT1170 if the text cannot be retrieved, FOUT1190 for an
   *         unsupported encoding or a character that is not allowed in XML.
   */
  @NonNull
  private static XdmSequence _unparsedTextCall (@NonNull final DynamicContext aCtx,
                                                @NonNull final List <XdmSequence> aArgs)
  {
    final String sHref = _getOptionalString (aArgs.get (0));
    final String sEncoding = aArgs.size () > 1 ? _getOptionalString (aArgs.get (1)) : null;
    if (sHref == null)
    {
      if (sEncoding != null && !_isSupportedEncoding (sEncoding))
        throw XPathException.of ("FOUT1190", "The encoding '" + sEncoding + "' is not supported");
      return XdmSequence.EMPTY;
    }
    return XdmSequence.of (XdmAtomic.string (_unparsedText (aCtx, sHref, sEncoding)));
  }

  /**
   * Register the resource functions of F&amp;O 3.1 section 14.6:
   * <code>fn:doc</code> (empty for an empty argument),
   * <code>fn:doc-available</code>, <code>fn:unparsed-text</code>,
   * <code>fn:unparsed-text-lines</code> and
   * <code>fn:unparsed-text-available</code> (arity 1 and 2),
   * <code>fn:collection</code> and <code>fn:uri-collection</code> (arity 0 and
   * 1), <code>fn:environment-variable</code> and
   * <code>fn:available-environment-variables</code>, which read the
   * environment variables of the dynamic context (not necessarily those of the
   * process).
   *
   * @param aLib
   *        The library to register into.
   */
  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "doc", "(xs:string?) as document-node()?", (aCtx, aArgs) -> {
      final String sHref = _getOptionalString (aArgs.get (0));
      return sHref == null ? XdmSequence.EMPTY : XdmSequence.of (_doc (aCtx, sHref));
    });
    aLib.register (sNS,
                   "doc-available",
                   "(xs:string?) as xs:boolean",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.bool (_isDocAvailable (aCtx,
                                                                                    _getOptionalString (aArgs.get (0))))));
    aLib.register (sNS, "unparsed-text", "(xs:string?) as xs:string?", FnResource::_unparsedTextCall);
    aLib.register (sNS, "unparsed-text", "(xs:string?, xs:string) as xs:string?", FnResource::_unparsedTextCall);
    aLib.register (sNS, "unparsed-text-lines", "(xs:string?) as xs:string*", FnResource::_unparsedTextLines);
    aLib.register (sNS,
                   "unparsed-text-lines",
                   "(xs:string?, xs:string) as xs:string*",
                   FnResource::_unparsedTextLines);
    aLib.register (sNS,
                   "unparsed-text-available",
                   "(xs:string?) as xs:boolean",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.bool (_isUnparsedTextAvailable (aCtx, aArgs))));
    aLib.register (sNS,
                   "unparsed-text-available",
                   "(xs:string?, xs:string) as xs:boolean",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.bool (_isUnparsedTextAvailable (aCtx, aArgs))));
    aLib.register (sNS, "collection", "() as item()*", FnResource::_collection);
    aLib.register (sNS, "collection", "(xs:string?) as item()*", FnResource::_collection);
    aLib.register (sNS, "uri-collection", "() as xs:anyURI*", FnResource::_uriCollection);
    aLib.register (sNS, "uri-collection", "(xs:string?) as xs:anyURI*", FnResource::_uriCollection);
    aLib.register (sNS, "environment-variable", "(xs:string) as xs:string?", (aCtx, aArgs) -> {
      final String sValue = aCtx.getEnvironmentVariables ().get (((XdmAtomic) aArgs.get (0).get (0)).getString ());
      return sValue == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.string (sValue));
    });
    aLib.register (sNS, "available-environment-variables", "() as xs:string*", (aCtx, aArgs) -> {
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final String sName : aCtx.getEnvironmentVariables ().keySet ())
        aBuilder.add (XdmAtomic.string (sName));
      return aBuilder.build ();
    });
  }
}
