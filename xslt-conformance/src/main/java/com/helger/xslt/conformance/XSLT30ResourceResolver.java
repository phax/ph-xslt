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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.conformance.QT3ResourceResolver;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;

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

  @Nullable
  public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    final Path aPath = _getPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    final IXdmNode aPrepared = m_aPrepared.get (aPath);
    return aPrepared != null ? aPrepared : m_aDocs.getDocument (aPath);
  }

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
        if (aXmlId != null && sID.equals (aXmlId.getValue ().trim ()) || aId != null && sID.equals (aId.getValue ().trim ()))
          return aCur;
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.add (aCur.getChildAtIndex (i));
    }
    return null;
  }

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
      final IXdmNode aDoc = m_aDocs.getDocument (aPaths.get (i));
      if (aDoc == null)
        continue;
      // A member "file#id" is the element with that ID (collection-004)
      final String sFragment = aFragments != null && aFragments.size () == aPaths.size () ? aFragments.get (i) : null;
      if (sFragment == null)
        ret.add (aDoc);
      else
      {
        final IXdmNode aElement = _findById (aDoc, sFragment);
        if (aElement != null)
          ret.add (aElement);
      }
    }
    return ret;
  }

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
