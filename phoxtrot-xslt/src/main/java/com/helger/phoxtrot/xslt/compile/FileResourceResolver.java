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
package com.helger.phoxtrot.xslt.compile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.context.IResourceResolver;

/**
 * A resolver that reads <code>file:</code> URIs and nothing else. Used for
 * stylesheet modules when the compile options give no resolver. Stylesheet
 * modules ({@link #resolveStylesheetModule(String)}) are parsed with node
 * locations, documents ({@link #resolveDocument(String)},
 * {@link #openDocumentStream(String, Predicate)}) without.
 *
 * @author Philip Helger
 */
@Immutable
public final class FileResourceResolver implements IResourceResolver
{
  /** The shared instance, without external entities. */
  public static final FileResourceResolver INSTANCE = new FileResourceResolver (false);

  private final boolean m_bLoadExternalEntities;

  /**
   * @param bLoadExternalEntities
   *        <code>true</code> to load external DTDs and entities.
   */
  public FileResourceResolver (final boolean bLoadExternalEntities)
  {
    m_bLoadExternalEntities = bLoadExternalEntities;
  }

  /**
   * @param sAbsoluteUri
   *        An absolute URI.
   * @return The local file a <code>file:</code> URI names, with any fragment
   *         identifier ignored; <code>null</code> for any other scheme, a URI
   *         that cannot be converted to a path, or a path that is not an
   *         existing regular file.
   */
  @Nullable
  public static Path getLocalPath (@NonNull final String sAbsoluteUri)
  {
    if (!sAbsoluteUri.startsWith ("file:"))
      return null;
    try
    {
      String s = sAbsoluteUri;
      final int nHash = s.indexOf ('#');
      if (nHash >= 0)
        s = s.substring (0, nHash);
      final Path aPath = Paths.get (URI.create (s));
      return Files.isRegularFile (aPath) ? aPath : null;
    }
    catch (final RuntimeException ex)
    {
      return null;
    }
  }

  /**
   * Parse the local file as an XML document. The document URI is set to the
   * requested URI unless the parser already set one.
   *
   * @param sAbsoluteUri
   *        The absolute URI.
   * @param bRecordLocations
   *        <code>true</code> to give the nodes their locations
   *        ({@link XdmParser#setRecordLocations(boolean)}).
   * @return The document node, or <code>null</code> if the URI names no
   *         readable local file or the file is not well-formed.
   */
  @Nullable
  private IXdmNode _parse (@NonNull final String sAbsoluteUri, final boolean bRecordLocations)
  {
    final Path aPath = getLocalPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    try
    {
      final IXdmNode ret = new XdmParser ().setRecordLocations (bRecordLocations)
                                           .setLoadExternalEntities (m_bLoadExternalEntities)
                                           .parse (aPath);
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
   * Parse the local file as an XML document with the location default of
   * {@link XdmParser} on the calling thread
   * ({@link XdmParser#isRecordLocationsByDefault()}): without node locations,
   * unless a resolver that delegates here is asked for a stylesheet module
   * through the default implementation of
   * {@link IResourceResolver#resolveStylesheetModule(String)}. The document
   * URI is set to the requested URI unless the parser already set one.
   *
   * @param sAbsoluteUri
   *        The absolute URI.
   * @return The document node, or <code>null</code> if the URI names no
   *         readable local file or the file is not well-formed.
   */
  @Nullable
  public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    return _parse (sAbsoluteUri, XdmParser.isRecordLocationsByDefault ());
  }

  /**
   * Parse the local file as a stylesheet module, with node locations. The
   * document URI is set to the requested URI unless the parser already set
   * one.
   *
   * @param sAbsoluteUri
   *        The absolute URI.
   * @return The document node, or <code>null</code> if the URI names no
   *         readable local file or the file is not well-formed.
   */
  @Override
  @Nullable
  public IXdmNode resolveStylesheetModule (@NonNull final String sAbsoluteUri)
  {
    return _parse (sAbsoluteUri, true);
  }

  /**
   * Open the local file for streamed reading with the default element depth
   * limit and without node locations. Unlike {@link #resolveDocument(String)}, errors while opening the
   * stream are thrown, not turned into <code>null</code>.
   *
   * @param sAbsoluteUri
   *        The absolute URI.
   * @param aStripWhitespace
   *        Decides by element name whether whitespace-only text is stripped,
   *        or <code>null</code> to keep all text.
   * @return The reader, or <code>null</code> if the URI names no local file.
   * @throws XdmParseException
   *         if the stream cannot be opened
   */
  @Override
  @Nullable
  public XdmStreamReader openDocumentStream (@NonNull final String sAbsoluteUri,
                                             @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
  {
    final Path aPath = getLocalPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    final InputSource aSource = new InputSource (aPath.toUri ().toString ());
    final XdmStreamReader ret = XdmStreamReader.open (aSource,
                                                      m_bLoadExternalEntities,
                                                      XdmParser.DEFAULT_MAX_ELEMENT_DEPTH,
                                                      aStripWhitespace);
    if (ret.getDocument ().getDocumentUri () == null)
      ret.getDocument ().setDocumentUri (sAbsoluteUri);
    return ret;
  }

  /**
   * Read the local file as text.
   *
   * @param sAbsoluteUri
   *        The absolute URI.
   * @param sEncoding
   *        The encoding name, <code>null</code> for UTF-8.
   * @return The text, or <code>null</code> if the URI names no local file,
   *         the encoding is unknown or the content cannot be read or decoded
   *         with it.
   */
  @Nullable
  public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
  {
    final Path aPath = getLocalPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    try
    {
      final Charset aCharset = sEncoding == null ? StandardCharsets.UTF_8 : Charset.forName (sEncoding);
      return Files.readString (aPath, aCharset);
    }
    catch (final IOException | RuntimeException ex)
    {
      return null;
    }
  }
}
