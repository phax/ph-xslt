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
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.conformance.QT3ResourceResolver;
import com.helger.phoxtrot.xpath.context.IResourceResolver;

/**
 * Resolves what a stylesheet reads at run time (<code>document()</code>,
 * <code>fn:doc</code>, <code>fn:collection</code>,
 * <code>fn:unparsed-text</code>) for one test case. Sources, resources and
 * collection members declared by the environment are found under their
 * declared URI, that URI resolved against the test-set directory, and their
 * file URI. Any other <code>file:</code> URI of an existing file is read
 * directly, the way the Go engine reads a relative reference resolved against
 * the stylesheet. Network URIs are never fetched (the Go harness is offline by
 * default too).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XSLT30ResourceResolver implements IResourceResolver
{
  private final XSLT30DocumentCache m_aDocs;
  private final ICommonsMap <Path, IXdmNode> m_aPrepared = new CommonsHashMap <> ();
  private final ICommonsMap <String, Path> m_aByUri = new CommonsHashMap <> ();
  private final ICommonsMap <String, String> m_aEncodingByUri = new CommonsHashMap <> ();
  private final ICommonsMap <String, ICommonsList <Path>> m_aCollections = new CommonsHashMap <> ();
  // Per declared collection URI: the fragment identifier of each member
  // (null for a whole document), parallel to m_aCollections
  private final ICommonsMap <String, ICommonsList <String>> m_aCollectionFragments = new CommonsHashMap <> ();

  /**
   * @param aEnv
   *        The resolved environment (not a reference), may be
   *        <code>null</code>.
   * @param aTestSetDir
   *        The directory of the test set.
   * @param aDocs
   *        The document cache of the test set.
   */
  public XSLT30ResourceResolver (@Nullable final XSLT30Environment aEnv,
                                 @NonNull final Path aTestSetDir,
                                 @NonNull final XSLT30DocumentCache aDocs)
  {
    m_aDocs = aDocs;
    if (aEnv == null)
      return;
    final Path aBaseDir = aEnv.baseDir ().toAbsolutePath ().normalize ();
    final Path aSetDir = aTestSetDir.toAbsolutePath ().normalize ();
    for (final XSLT30Environment.Resource aRes : aEnv.resources ())
    {
      if (aRes.uri ().isEmpty () || aRes.file ().isEmpty ())
        continue;
      final Path aPath = getAbsolutePath (aBaseDir, aRes.file ());
      for (final String sUri : _getUriForms (aRes.uri (), aSetDir))
      {
        m_aByUri.putIfAbsent (sUri, aPath);
        if (!aRes.encoding ().isEmpty ())
          m_aEncodingByUri.putIfAbsent (sUri, aRes.encoding ());
      }
    }
    for (final XSLT30Environment.Source aSrc : aEnv.sources ())
    {
      if (aSrc.file ().isEmpty ())
        continue;
      final Path aPath = getAbsolutePath (aBaseDir, aSrc.file ());
      if (!aSrc.uri ().isEmpty ())
        for (final String sUri : _getUriForms (aSrc.uri (), aSetDir))
          m_aByUri.putIfAbsent (sUri, aPath);
      m_aByUri.putIfAbsent (getFileUri (aPath), aPath);
    }
    for (final XSLT30Environment.Collection aCol : aEnv.collections ())
    {
      final ICommonsList <Path> aPaths = new CommonsArrayList <> ();
      final ICommonsList <String> aFragments = new CommonsArrayList <> ();
      for (final XSLT30Environment.Source aSrc : aCol.sources ())
      {
        // The content lives in @file; @uri is what the stylesheet sees
        String sFile = aSrc.file ();
        if (sFile.isEmpty ())
          sFile = aSrc.uri ();
        if (sFile.isEmpty () || sFile.indexOf (':') >= 0)
          continue;
        final int nHash = sFile.indexOf ('#');
        String sFragment = null;
        if (nHash >= 0)
        {
          sFragment = sFile.substring (nHash + 1);
          sFile = sFile.substring (0, nHash);
        }
        final Path aPath = getAbsolutePath (aBaseDir, sFile);
        aPaths.add (aPath);
        aFragments.add (sFragment);
        m_aByUri.putIfAbsent (getFileUri (aPath), aPath);
      }
      for (final String sUri : _getUriForms (aCol.uri (), aSetDir))
      {
        m_aCollections.putIfAbsent (sUri, aPaths);
        m_aCollectionFragments.putIfAbsent (sUri, aFragments);
      }
    }
  }

  /** The URI as declared and, if relative, resolved against the directory. */
  @NonNull
  private static ICommonsList <String> _getUriForms (@NonNull final String sUri, @NonNull final Path aDir)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> (sUri);
    if (!sUri.isEmpty () && sUri.indexOf (':') < 0)
      try
      {
        ret.add (getFileUri (aDir.resolve (sUri).normalize ()));
      }
      catch (final InvalidPathException ex)
      {
        // not a usable relative path
      }
    return ret;
  }

  /**
   * @return The absolute, normalised path of a <code>file:</code> URI, with a
   *         fragment identifier removed, or <code>null</code> if the URI is not
   *         a <code>file:</code> URI or cannot be converted. The file need not
   *         exist.
   */
  @Nullable
  private static Path _fileUriToPath (@NonNull final String sUri)
  {
    if (!sUri.startsWith ("file:"))
      return null;
    try
    {
      final String sNoFragment = sUri.indexOf ('#') >= 0 ? sUri.substring (0, sUri.indexOf ('#')) : sUri;
      return Path.of (new URI (sNoFragment)).toAbsolutePath ().normalize ();
    }
    catch (final Exception ex)
    {
      // URISyntaxException, IllegalArgumentException, FileSystemNotFoundException
      return null;
    }
  }

  /**
   * Map an absolute URI to a file: a URI declared by the environment (in any
   * of its forms, see the constructor) first; else, for a <code>file:</code>
   * URI, the declared file with the same path (<code>file:/x</code> and
   * <code>file:///x</code> are the same file), else that file itself if it
   * exists. Any other URI gives <code>null</code>.
   */
  @Nullable
  private Path _getPath (@NonNull final String sAbsoluteUri)
  {
    final Path aDeclared = m_aByUri.get (sAbsoluteUri);
    if (aDeclared != null)
      return aDeclared;
    final Path aCandidate = _fileUriToPath (sAbsoluteUri);
    if (aCandidate == null)
      return null;
    // file:/x and file:///x name the same file
    final Path aDeclaredByPath = m_aByUri.get (getFileUri (aCandidate));
    if (aDeclaredByPath != null)
      return aDeclaredByPath;
    return Files.isRegularFile (aCandidate) ? aCandidate : null;
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
    return QT3ResourceResolver.getFileUri (aPath);
  }

  /**
   * Use a prepared document (a validated secondary source) for a file.
   *
   * @param aPath
   *        The absolute normalised path.
   * @param aDoc
   *        The document.
   */
  public void putDocument (@NonNull final Path aPath, @NonNull final IXdmNode aDoc)
  {
    m_aPrepared.put (aPath, aDoc);
  }

  /**
   * The document for <code>fn:doc</code>/<code>document()</code>: a prepared
   * (validated) document if one was registered for the file, else the parsed
   * document of the test set's cache (so the same file is always the same
   * node).
   *
   * @return <code>null</code> if the URI maps to no file, or the file is not
   *         well-formed.
   */
  @Nullable
  public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    final Path aPath = _getPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    final IXdmNode aPrepared = m_aPrepared.get (aPath);
    return aPrepared != null ? aPrepared : m_aDocs.getDocument (aPath);
  }

  /**
   * The stylesheet module for <code>xsl:include</code>,
   * <code>xsl:import</code>, a package or <code>fn:transform</code>: the file
   * the URI maps to, parsed with node locations by the test set's cache (a
   * different node than {@link #resolveDocument(String)} returns for the same
   * file). Prepared (validated) documents are source documents and not used
   * here.
   *
   * @return <code>null</code> if the URI maps to no file, or the file is not
   *         well-formed.
   */
  @Override
  @Nullable
  public IXdmNode resolveStylesheetModule (@NonNull final String sAbsoluteUri)
  {
    final Path aPath = _getPath (sAbsoluteUri);
    return aPath == null ? null : m_aDocs.getStylesheetModule (aPath);
  }

  /**
   * Open the file the URI maps to as a stream for streamed processing, with
   * external entities read like the document cache does. A file with a
   * prepared (validated) document is not streamed, so its type annotations
   * are kept.
   *
   * @return <code>null</code> if the URI maps to no file or the file has a
   *         prepared document; the engine then uses
   *         {@link #resolveDocument(String)}.
   */
  @Override
  @Nullable
  public XdmStreamReader openDocumentStream (@NonNull final String sAbsoluteUri,
                                             @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
  {
    final Path aPath = _getPath (sAbsoluteUri);
    // A prepared (validated) document is used as it is
    if (aPath == null || m_aPrepared.containsKey (aPath))
      return null;
    // The settings of the document cache: external entities are read, so a
    // document with an external DTD subset is read in full
    return XdmStreamReader.open (new InputSource (getFileUri (aPath)),
                                 true,
                                 XdmParser.DEFAULT_MAX_ELEMENT_DEPTH,
                                 aStripWhitespace);
  }

  /**
   * The text for <code>fn:unparsed-text</code> and friends: the file is read
   * and decoded like the QT3 harness does, with the encoding declared by the
   * environment's <code>&lt;resource&gt;</code> for that exact URI taking
   * precedence over the requested one.
   *
   * @return <code>null</code> if the URI maps to no file or the file cannot
   *         be read.
   * @throws XPathException
   *         FOUT1190 if the content cannot be decoded
   */
  @Nullable
  public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
  {
    final Path aPath = _getPath (sAbsoluteUri);
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
    final String ret = QT3ResourceResolver.decodeText (aBytes, m_aEncodingByUri.get (sAbsoluteUri), sEncoding);
    if (ret == null)
      throw XPathException.of ("FOUT1190", "The resource '" + sAbsoluteUri + "' cannot be decoded");
    return ret;
  }

  /**
   * A directory collection <code>dir/?select=pattern</code>: the files of
   * the directory whose names match the glob pattern, sorted by name.
   */
  @Nullable
  private static ICommonsList <Path> _getDirectoryCollection (@NonNull final String sUri)
  {
    final int nQuery = sUri.indexOf ("?select=");
    if (nQuery < 0)
      return null;
    final Path aDir = _fileUriToPath (sUri.substring (0, nQuery));
    if (aDir == null || !Files.isDirectory (aDir))
      return null;
    final PathMatcher aMatcher;
    try
    {
      aMatcher = FileSystems.getDefault ().getPathMatcher ("glob:" + sUri.substring (nQuery + 8));
    }
    catch (final IllegalArgumentException ex)
    {
      return null;
    }
    final ICommonsList <Path> ret = new CommonsArrayList <> ();
    try (final Stream <Path> aStream = Files.list (aDir))
    {
      aStream.filter (aPath -> Files.isRegularFile (aPath) && aMatcher.matches (aPath.getFileName ())).sorted ().forEach (ret::add);
    }
    catch (final IOException ex)
    {
      return null;
    }
    return ret;
  }

  /**
   * The paths of a collection: a declared one (a directory URI matches with
   * or without the trailing slash) or a directory collection.
   */
  @Nullable
  private ICommonsList <Path> _getCollectionPaths (@NonNull final String sUri)
  {
    ICommonsList <Path> ret = m_aCollections.get (sUri);
    if (ret == null)
      ret = m_aCollections.get (sUri.endsWith ("/") ? sUri.substring (0, sUri.length () - 1) : sUri + "/");
    if (ret == null)
      ret = _getDirectoryCollection (sUri);
    return ret;
  }

  /** The element with the given xml:id or id attribute, or <code>null</code>. */
  @Nullable
  private static IXdmNode _findById (@NonNull final IXdmNode aRoot, @NonNull final String sID)
  {
    final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> (aRoot);
    while (aStack.isNotEmpty ())
    {
      final IXdmNode aCur = aStack.removeLast ();
      if (aCur.isElement ())
      {
        final IXdmNode aXmlId = aCur.getAttribute (XdmConstants.NS_XML, "id");
        final IXdmNode aId = aCur.getAttribute ("", "id");
        if ((aXmlId != null && sID.equals (aXmlId.getValue ().trim ())) || (aId != null && sID.equals (aId.getValue ().trim ())))
          return aCur;
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.add (aCur.getChildAtIndex (i));
    }
    return null;
  }

  /**
   * The documents of a collection, in declaration order (or file name order
   * for a directory collection). A member declared as <code>file#id</code>
   * contributes the element with that <code>xml:id</code> or <code>id</code>
   * instead of the document.
   *
   * @return <code>null</code> if the URI names no declared collection and no
   *         existing directory with <code>?select=</code>.
   * @throws XPathException
   *         FODC0002 if a member cannot be read or parsed, or a fragment
   *         member's ID is not found (a member is never left out silently)
   */
  @Nullable
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> resolveCollection (@NonNull final String sUri)
  {
    final ICommonsList <Path> aPaths = _getCollectionPaths (sUri);
    if (aPaths == null)
      return null;
    ICommonsList <String> aFragments = m_aCollectionFragments.get (sUri);
    if (aFragments == null)
      aFragments = m_aCollectionFragments.get (sUri.endsWith ("/") ? sUri.substring (0, sUri.length () - 1) : sUri + "/");
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (int i = 0; i < aPaths.size (); ++i)
    {
      final IXdmNode aDoc;
      try
      {
        aDoc = m_aDocs.getDocumentOrThrow (aPaths.get (i));
      }
      catch (final XdmParseException ex)
      {
        throw new XPathException ("FODC0002",
                                  "Member '" + aPaths.get (i) + "' of collection '" + sUri + "' cannot be parsed",
                                  ex);
      }
      // A member "file#id" is the element with that ID (collection-004)
      final String sFragment = aFragments != null && aFragments.size () == aPaths.size () ? aFragments.get (i) : null;
      if (sFragment == null)
        ret.add (aDoc);
      else
      {
        final IXdmNode aElement = _findById (aDoc, sFragment);
        if (aElement == null)
          throw new XPathException ("FODC0002",
                                    "Member '" + aPaths.get (i) + "#" + sFragment + "' of collection '" + sUri + "' not found");
        ret.add (aElement);
      }
    }
    return ret;
  }

  /**
   * The <code>file:</code> URIs of the members of a collection (for
   * <code>fn:uri-collection</code>), in the order of
   * {@link #resolveCollection(String)}; fragments of members are not part of
   * them.
   *
   * @return <code>null</code> if the collection is unknown.
   */
  @Nullable
  @ReturnsMutableCopy
  public ICommonsList <String> resolveUriCollection (@NonNull final String sUri)
  {
    final ICommonsList <Path> aPaths = _getCollectionPaths (sUri);
    if (aPaths == null)
      return null;
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final Path aPath : aPaths)
      ret.add (getFileUri (aPath));
    return ret;
  }
}
