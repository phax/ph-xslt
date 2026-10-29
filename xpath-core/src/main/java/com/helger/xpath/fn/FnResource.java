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

  private static boolean _isXmlChar (final int c)
  {
    return c == 0x9 ||
           c == 0xA ||
           c == 0xD ||
           (c >= 0x20 && c <= 0xD7FF) ||
           (c >= 0xE000 && c <= 0xFFFD) ||
           (c >= 0x10000 && c <= 0x10FFFF);
  }

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
   *         FOUT1170, FOUT1190 or FOUT1200.
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
