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
package com.helger.phoxtrot.xslt.jaxp;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.io.nonblocking.NonBlockingStringWriter;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xslt.compile.FileResourceResolver;

/**
 * The resource resolver of the JAXP adapter, for <code>xsl:include</code>,
 * <code>xsl:import</code>, <code>document()</code>, <code>fn:doc</code> and
 * <code>fn:unparsed-text</code>. A JAXP {@link URIResolver}, if set, is asked
 * first, with the absolute URI the engine resolved as <code>href</code> and
 * as <code>base</code> (an absolute URI resolves to itself against any
 * base). If it returns <code>null</code> (or there is none), the URI is read
 * directly, but only if its scheme is allowed by the value of
 * {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET} (<code>all</code>, or a
 * comma-separated list of schemes such as <code>file,jar</code>; the empty
 * string allows nothing): <code>file:</code> URIs as files, any other URI
 * through the JDK URL handlers. Collections are not supported. Stylesheet
 * modules ({@link #resolveStylesheetModule(String)}) are parsed with node
 * locations, documents without.
 *
 * @author Philip Helger
 */
@Immutable
final class JaxpResourceResolver implements IResourceResolver
{
  private static final Logger LOGGER = LoggerFactory.getLogger (JaxpResourceResolver.class);

  private final URIResolver m_aURIResolver;
  private final String m_sAllowedSchemes;
  private final boolean m_bLoadExternalEntities;

  /**
   * @param aURIResolver
   *        The JAXP resolver to ask first, may be <code>null</code>.
   * @param sAllowedSchemes
   *        The value of {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET} for
   *        the direct access.
   * @param bLoadExternalEntities
   *        Whether documents read here load external DTDs and entities.
   */
  JaxpResourceResolver (@Nullable final URIResolver aURIResolver,
                        @NonNull final String sAllowedSchemes,
                        final boolean bLoadExternalEntities)
  {
    m_aURIResolver = aURIResolver;
    m_sAllowedSchemes = sAllowedSchemes;
    m_bLoadExternalEntities = bLoadExternalEntities;
  }

  /**
   * @return <code>true</code> if the scheme of the URI is in the allowed
   *         list (case-insensitive), or the list is <code>all</code>.
   */
  static boolean isAccessAllowed (@NonNull final String sAbsoluteUri, @NonNull final String sAllowedSchemes)
  {
    final String sAllowed = sAllowedSchemes.trim ();
    if ("all".equalsIgnoreCase (sAllowed))
      return true;
    final int nColon = sAbsoluteUri.indexOf (':');
    if (nColon <= 0)
      return false;
    final String sScheme = sAbsoluteUri.substring (0, nColon).toLowerCase (Locale.ROOT);
    for (final String sEntry : sAllowed.split (","))
      if (sEntry.trim ().toLowerCase (Locale.ROOT).equals (sScheme))
        return true;
    return false;
  }

  /**
   * @return The source the URI resolver gives, <code>null</code> if there is
   *         no resolver or it gives none.
   * @throws XPathException
   *         FODC0002 if the resolver throws
   */
  @Nullable
  private Source _resolveWithURIResolver (@NonNull final String sAbsoluteUri)
  {
    if (m_aURIResolver == null)
      return null;
    try
    {
      return m_aURIResolver.resolve (sAbsoluteUri, sAbsoluteUri);
    }
    catch (final TransformerException ex)
    {
      throw new XPathException ("FODC0002", "The URIResolver failed for '" + sAbsoluteUri + "': " + ex.getMessage (), ex);
    }
  }

  /**
   * @return <code>true</code> if direct access is allowed; a refusal is
   *         logged.
   */
  private boolean _checkAccess (@NonNull final String sAbsoluteUri)
  {
    if (isAccessAllowed (sAbsoluteUri, m_sAllowedSchemes))
      return true;
    LOGGER.warn ("Access to '" +
                 sAbsoluteUri +
                 "' is not allowed by " +
                 XMLConstants.ACCESS_EXTERNAL_STYLESHEET +
                 " ('" +
                 m_sAllowedSchemes +
                 "') and no URIResolver resolved it");
    return false;
  }

  /**
   * Resolve a document: the source of the URI resolver turned into XDM (with
   * the URI as document URI if it has none), or else the URI read directly
   * if access is allowed. A source that is parsed here gets node locations
   * as requested; a DOM source or an {@link XdmSource} keeps what it has.
   *
   * @param bRecordLocations
   *        Whether a parsed document gets node locations
   *        ({@link XdmParser#setRecordLocations(boolean)}).
   * @return The document, or <code>null</code> if it is unavailable.
   * @throws XPathException
   *         FODC0002 if the URI resolver fails or its source cannot be read
   */
  @Nullable
  private IXdmNode _resolve (@NonNull final String sAbsoluteUri, final boolean bRecordLocations)
  {
    final Source aSource = _resolveWithURIResolver (sAbsoluteUri);
    if (aSource != null)
    {
      final IXdmNode ret;
      try
      {
        ret = JaxpSources.toXdm (aSource, m_bLoadExternalEntities, bRecordLocations);
      }
      catch (final TransformerException ex)
      {
        throw new XPathException ("FODC0002", "The document '" + sAbsoluteUri + "' cannot be read: " + ex.getMessage (), ex);
      }
      final IXdmNode aRoot = ret.getRoot ();
      if (aRoot.isDocument () && aRoot.getDocumentUri () == null)
        aRoot.setDocumentUri (aSource.getSystemId () != null ? aSource.getSystemId () : sAbsoluteUri);
      return ret;
    }
    if (!_checkAccess (sAbsoluteUri))
      return null;
    if (sAbsoluteUri.startsWith ("file:"))
    {
      final FileResourceResolver aFileResolver = new FileResourceResolver (m_bLoadExternalEntities);
      return bRecordLocations ? aFileResolver.resolveStylesheetModule (sAbsoluteUri)
                              : aFileResolver.resolveDocument (sAbsoluteUri);
    }
    try
    {
      final IXdmNode ret = new XdmParser ().setLoadExternalEntities (m_bLoadExternalEntities)
                                           .setRecordLocations (bRecordLocations)
                                           .parse (new InputSource (sAbsoluteUri));
      if (ret.getDocumentUri () == null)
        ret.setDocumentUri (sAbsoluteUri);
      return ret;
    }
    catch (final XdmParseException ex)
    {
      return null;
    }
  }

  /**
   * Resolve a document (see {@link #_resolve(String, boolean)}) with the
   * location default of {@link XdmParser} on the calling thread
   * ({@link XdmParser#isRecordLocationsByDefault()}, without locations
   * unless a stylesheet module is resolved through a delegating resolver).
   *
   * @return The document, or <code>null</code> if it is unavailable.
   * @throws XPathException
   *         FODC0002 if the URI resolver fails or its source cannot be read
   */
  @Nullable
  public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    return _resolve (sAbsoluteUri, XdmParser.isRecordLocationsByDefault ());
  }

  /**
   * Resolve a stylesheet module (see {@link #_resolve(String, boolean)}),
   * parsed with node locations. A DOM source or an {@link XdmSource} from the
   * URI resolver is used as it is, with the locations it has (none for DOM).
   *
   * @return The document, or <code>null</code> if it is unavailable.
   * @throws XPathException
   *         FODC0002 if the URI resolver fails or its source cannot be read
   */
  @Override
  @Nullable
  public IXdmNode resolveStylesheetModule (@NonNull final String sAbsoluteUri)
  {
    return _resolve (sAbsoluteUri, true);
  }

  /**
   * @return All characters of the reader; it is closed.
   */
  @NonNull
  private static String _readAll (@NonNull final Reader aReader) throws IOException
  {
    try (final Reader aR = aReader; final NonBlockingStringWriter aSW = new NonBlockingStringWriter ())
    {
      aR.transferTo (aSW);
      return aSW.getAsString ();
    }
  }

  /**
   * @return The content of the stream decoded with the encoding (UTF-8 if
   *         <code>null</code>); it is closed.
   */
  @NonNull
  private static String _readAll (@NonNull final InputStream aIS, @Nullable final String sEncoding) throws IOException
  {
    try (final InputStream aStream = aIS)
    {
      final Charset aCharset = sEncoding == null ? StandardCharsets.UTF_8 : Charset.forName (sEncoding);
      return new String (aStream.readAllBytes (), aCharset);
    }
  }

  /**
   * Resolve a text resource: from the source of the URI resolver (the reader
   * of a stream source, its byte stream decoded with the encoding, or else
   * the system ID of the source read through the JDK URL handlers), or else
   * the URI read directly if access is allowed.
   *
   * @return The text, or <code>null</code> if it is unavailable.
   */
  @Nullable
  public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
  {
    final Source aSource = _resolveWithURIResolver (sAbsoluteUri);
    try
    {
      if (aSource != null)
      {
        if (aSource instanceof final StreamSource aStreamSource)
        {
          if (aStreamSource.getReader () != null)
            return _readAll (aStreamSource.getReader ());
          if (aStreamSource.getInputStream () != null)
            return _readAll (aStreamSource.getInputStream (), sEncoding);
        }
        if (aSource.getSystemId () == null)
          return null;
        return _readAll (URI.create (aSource.getSystemId ()).toURL ().openStream (), sEncoding);
      }
      if (!_checkAccess (sAbsoluteUri))
        return null;
      if (sAbsoluteUri.startsWith ("file:"))
        return new FileResourceResolver (false).resolveText (sAbsoluteUri, sEncoding);
      return _readAll (URI.create (sAbsoluteUri).toURL ().openStream (), sEncoding);
    }
    catch (final IOException | RuntimeException ex)
    {
      // Unreadable, an unknown encoding or a URI without URL handler
      return null;
    }
  }
}
