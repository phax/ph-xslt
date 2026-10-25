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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

import javax.xml.validation.Schema;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;

/**
 * Resolves the URIs an environment declares (sources with a <code>uri</code>,
 * resources, collections) to local files, a port of Go's
 * <code>fotsResolver</code>. A URI not declared is unavailable, with two
 * exceptions taken over from Go: a <code>file:</code> URI of an existing file
 * (fn:doc requires it to be well-formed XML), and, for unparsed text only, a
 * relative reference resolved against the environment's base directory.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class QT3ResourceResolver implements IResourceResolver
{
  private final Path m_aBaseDir;
  private final QT3DocumentCache m_aDocs;
  private final ICommonsMap <String, Path> m_aByUri = new CommonsHashMap <> ();
  private final ICommonsMap <String, String> m_aEncodingByUri = new CommonsHashMap <> ();
  private final ICommonsMap <String, ICommonsList <Path>> m_aCollections = new CommonsHashMap <> ();
  private final ICommonsList <Path> m_aSchemaFiles = new CommonsArrayList <> ();
  // Sources of this environment that were validated or carry a declared URI, by file
  private final ICommonsMap <Path, XdmNode> m_aValidated = new CommonsHashMap <> ();

  /**
   * @param aEnv
   *        The resolved environment (not a reference).
   * @param aDocs
   *        The document cache of the test set.
   */
  public QT3ResourceResolver (@NonNull final QT3Environment aEnv, @NonNull final QT3DocumentCache aDocs)
  {
    m_aBaseDir = aEnv.getBaseDir ().toAbsolutePath ().normalize ();
    m_aDocs = aDocs;
    for (final QT3Environment.Resource aRes : aEnv.getAllResources ())
    {
      if (aRes.uri ().isEmpty () || aRes.file ().isEmpty ())
        continue;
      final Path aPath = getAbsolutePath (m_aBaseDir, aRes.file ());
      m_aByUri.put (aRes.uri (), aPath);
      m_aEncodingByUri.put (aRes.uri (), aRes.encoding ().toLowerCase (Locale.ROOT));
      // A relative resource URI is also found after resolution against the
      // static base URI (the test set directory), as fn:json-doc does it
      if (aRes.uri ().indexOf (':') < 0)
        try
        {
          final String sResolved = getFileUri (m_aBaseDir.resolve (aRes.uri ()).normalize ());
          m_aByUri.putIfAbsent (sResolved, aPath);
          m_aEncodingByUri.putIfAbsent (sResolved, aRes.encoding ().toLowerCase (Locale.ROOT));
        }
        catch (final InvalidPathException ex)
        {
          // not a usable relative path
        }
    }
    // A source is retrievable under its declared uri and under its own file URI
    for (final QT3Environment.Source aSrc : aEnv.getAllSources ())
    {
      if (aSrc.file ().isEmpty ())
        continue;
      final Path aPath = getAbsolutePath (m_aBaseDir, aSrc.file ());
      if (!aSrc.uri ().isEmpty ())
        m_aByUri.put (aSrc.uri (), aPath);
      m_aByUri.put (getFileUri (aPath), aPath);
    }
    for (final QT3Environment.Collection aCol : aEnv.getAllCollections ())
    {
      final ICommonsList <Path> aPaths = new CommonsArrayList <> ();
      for (final QT3Environment.Source aSrc : aCol.sources ())
      {
        if (aSrc.file ().isEmpty ())
          continue;
        final Path aPath = getAbsolutePath (m_aBaseDir, aSrc.file ());
        aPaths.add (aPath);
        m_aByUri.put (getFileUri (aPath), aPath);
      }
      m_aCollections.put (aCol.uri (), aPaths);
    }
    for (final QT3Environment.Schema aSchema : aEnv.getAllSchemas ())
      if (!aSchema.file ().isEmpty ())
        m_aSchemaFiles.add (getAbsolutePath (m_aBaseDir, aSchema.file ()));
      else
        if (XdmConstants.NS_FN.equals (aSchema.uri ()) && QT3SchemaCache.isXerces ())
        {
          // The schema of the XML representation of JSON, which the test
          // driver is expected to know by its namespace; the suite has a copy
          final Path aJsonSchema = aEnv.getBaseDir ().resolve ("json-to-xml/schema-for-json.xsd").toAbsolutePath ().normalize ();
          if (Files.isRegularFile (aJsonSchema))
            m_aSchemaFiles.add (aJsonSchema);
        }
  }

  /**
   * @return The in-scope schema definitions of the environment (Xerces-J
   *         only), or <code>null</code>.
   */
  @Nullable
  public IXdmSchemaSet getSchemaSet ()
  {
    return m_aSchemaFiles.isEmpty () ? null : QT3SchemaCache.getSchemaSet (m_aSchemaFiles);
  }

  @Nullable
  private static Path _fileUriToPath (@NonNull final String sUri)
  {
    if (!sUri.startsWith ("file:"))
      return null;
    try
    {
      return Path.of (new URI (sUri)).toAbsolutePath ().normalize ();
    }
    catch (final Exception ex)
    {
      // URISyntaxException, IllegalArgumentException, FileSystemNotFoundException
      return null;
    }
  }

  @Nullable
  private Path _getTextPath (@NonNull final String sUri)
  {
    final Path aDeclared = m_aByUri.get (sUri);
    if (aDeclared != null)
      return aDeclared;
    Path aCandidate = _fileUriToPath (sUri);
    if (aCandidate != null)
    {
      // file:/x and file:///x name the same file
      final Path aDeclaredByPath = m_aByUri.get (getFileUri (aCandidate));
      if (aDeclaredByPath != null)
        return aDeclaredByPath;
    }
    if (aCandidate == null && !sUri.isEmpty () && sUri.indexOf (':') < 0 && !sUri.startsWith ("/"))
    {
      try
      {
        aCandidate = m_aBaseDir.resolve (sUri).normalize ();
      }
      catch (final InvalidPathException ex)
      {
        return null;
      }
    }
    return aCandidate != null && Files.isRegularFile (aCandidate) ? aCandidate : null;
  }

  @Nullable
  private static Charset _getCharset (@Nullable final String sName)
  {
    if (sName == null || sName.isEmpty ())
      return null;
    try
    {
      return Charset.forName (sName);
    }
    catch (final IllegalCharsetNameException | UnsupportedCharsetException ex)
    {
      return null;
    }
  }

  @Nullable
  private static String _decodeStrict (@NonNull final byte [] aBytes,
                                       final int nOffset,
                                       @NonNull final Charset aCharset)
  {
    try
    {
      return aCharset.newDecoder ()
                     .onMalformedInput (CodingErrorAction.REPORT)
                     .onUnmappableCharacter (CodingErrorAction.REPORT)
                     .decode (ByteBuffer.wrap (aBytes, nOffset, aBytes.length - nOffset))
                     .toString ();
    }
    catch (final CharacterCodingException ex)
    {
      return null;
    }
  }

  /**
   * Decode unparsed text: a declared ISO-8859-1 resource encoding (the "HTTP
   * header") wins, then a byte order mark, then the requested encoding, then
   * UTF-8.
   *
   * @param aBytes
   *        The raw bytes.
   * @param sDeclaredEncoding
   *        The resource's declared encoding, may be <code>null</code>.
   * @param sRequestedEncoding
   *        The encoding argument of the function, may be <code>null</code>.
   * @return The text or <code>null</code> if the bytes cannot be decoded.
   */
  @Nullable
  public static String decodeText (@NonNull final byte [] aBytes,
                                   @Nullable final String sDeclaredEncoding,
                                   @Nullable final String sRequestedEncoding)
  {
    if (sDeclaredEncoding != null)
      switch (sDeclaredEncoding.toLowerCase (Locale.ROOT))
      {
        case "iso-8859-1":
        case "latin1":
        case "latin-1":
          return new String (aBytes, StandardCharsets.ISO_8859_1);
        default:
          break;
      }
    final int n = aBytes.length;
    if (n >= 2 && (aBytes[0] & 0xff) == 0xfe && (aBytes[1] & 0xff) == 0xff)
      return _decodeStrict (aBytes, 2, StandardCharsets.UTF_16BE);
    if (n >= 2 && (aBytes[0] & 0xff) == 0xff && (aBytes[1] & 0xff) == 0xfe)
      return _decodeStrict (aBytes, 2, StandardCharsets.UTF_16LE);
    if (n >= 3 && (aBytes[0] & 0xff) == 0xef && (aBytes[1] & 0xff) == 0xbb && (aBytes[2] & 0xff) == 0xbf)
      return _decodeStrict (aBytes, 3, StandardCharsets.UTF_8);
    final Charset aRequested = _getCharset (sRequestedEncoding);
    return _decodeStrict (aBytes, 0, aRequested != null ? aRequested : StandardCharsets.UTF_8);
  }

  /**
   * @param aBaseDir
   *        Base directory.
   * @param sRelative
   *        Relative file name from the catalog.
   * @return The absolute normalised path.
   */
  @NonNull
  public static Path getAbsolutePath (@NonNull final Path aBaseDir, @NonNull final String sRelative)
  {
    return aBaseDir.resolve (sRelative).toAbsolutePath ().normalize ();
  }

  /**
   * @param aPath
   *        An absolute path.
   * @return Its <code>file:</code> URI, the same form the XDM parser reports as
   *         document URI.
   */
  @NonNull
  public static String getFileUri (@NonNull final Path aPath)
  {
    return aPath.toUri ().toString ();
  }

  /**
   * @param aSource
   *        A source of the environment.
   * @return The parsed source document or <code>null</code> if it cannot be
   *         loaded (a deliberately malformed source is left to the engine). A
   *         source with a <code>validation</code> attribute is validated
   *         against the environment's schemas, if it has any.
   */
  @Nullable
  public XdmNode getSourceDocument (final QT3Environment.@NonNull Source aSource)
  {
    if (aSource.file ().isEmpty ())
      return null;
    final Path aPath = getAbsolutePath (m_aBaseDir, aSource.file ());
    if (!aSource.validation ().isEmpty () && m_aSchemaFiles.isNotEmpty ())
    {
      // validation="strict" or "lax": both are run strict. A schema that does
      // not compile or a source that is not valid leaves the source
      // unvalidated, like Go
      final Schema aSchema = QT3SchemaCache.getSchema (m_aSchemaFiles);
      if (aSchema != null)
      {
        final XdmNode aDoc = m_aDocs.getValidatedDocument (aPath, aSchema, m_aSchemaFiles.toString ());
        if (aDoc != null)
        {
          m_aValidated.put (aPath, aDoc);
          return aDoc;
        }
      }
    }
    if (aSource.role ().startsWith ("$") && !aSource.uri ().isEmpty ())
    {
      // A variable-bound source with a declared URI is the document known by
      // that URI: its document URI is the declared one (fn:transform takes the
      // static base URI of a stylesheet node from it, fn-transform-42), and
      // fn:doc of either URI returns this same node. Like Go.
      final XdmNode aCached = m_aDocs.getDocument (aPath);
      if (aCached == null)
        return null;
      try
      {
        final XdmNode aDoc = new XdmParser ().parse (aPath);
        aDoc.setDocumentUri (aSource.uri ());
        m_aValidated.put (aPath, aDoc);
        return aDoc;
      }
      catch (final XdmParseException ex)
      {
        return null;
      }
    }
    return m_aDocs.getDocument (aPath);
  }

  @Nullable
  public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    Path aFile = m_aByUri.get (sAbsoluteUri);
    if (aFile == null)
      aFile = _fileUriToPath (sAbsoluteUri);
    if (aFile == null)
      return null;
    // fn:doc on the URI of a validated source returns that same document
    final XdmNode aValidated = m_aValidated.get (aFile);
    return aValidated != null ? aValidated : m_aDocs.getDocument (aFile);
  }

  @Nullable
  public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
  {
    final Path aPath = _getTextPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    final byte [] aBytes;
    try
    {
      aBytes = Files.readAllBytes (aPath);
    }
    catch (final IOException ex)
    {
      return null;
    }
    final String ret = decodeText (aBytes, m_aEncodingByUri.get (sAbsoluteUri), sEncoding);
    if (ret == null)
      throw XPathException.of ("FOUT1190", "The resource '" + sAbsoluteUri + "' cannot be decoded");
    return ret;
  }

  /**
   *
   * @param sUri
   *        Collection URI; empty for the default collection.
   * @return The member documents or <code>null</code> if the collection is
   *         not declared.
   */
  @Nullable
  @ReturnsMutableCopy
  public ICommonsList <XdmNode> resolveCollection (@NonNull final String sUri)
  {
    final ICommonsList <Path> aPaths = m_aCollections.get (sUri);
    if (aPaths == null)
      return null;
    final ICommonsList <XdmNode> ret = new CommonsArrayList <> ();
    for (final Path aPath : aPaths)
    {
      final XdmNode aDoc = m_aDocs.getDocument (aPath);
      if (aDoc != null)
        ret.add (aDoc);
    }
    return ret;
  }

  /**
   * @param sUri
   *        Collection URI; empty for the default collection.
   * @return The file URIs of the member documents or <code>null</code> if the
   *         collection is not declared.
   */
  @Nullable
  @ReturnsMutableCopy
  public ICommonsList <String> resolveUriCollection (@NonNull final String sUri)
  {
    final ICommonsList <Path> aPaths = m_aCollections.get (sUri);
    if (aPaths == null)
      return null;
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final Path aPath : aPaths)
      ret.add (getFileUri (aPath));
    return ret;
  }
}
