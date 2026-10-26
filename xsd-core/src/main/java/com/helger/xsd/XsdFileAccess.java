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
package com.helger.xsd;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * The file-access rule of a compile (go-xslt's <code>fsaccess.Guard</code>):
 * every schema document the validator reads on its own - the targets of
 * <code>xs:include</code>, <code>xs:import</code>, <code>xs:redefine</code>,
 * <code>xs:override</code> and of an instance's
 * <code>xsi:schemaLocation</code> hints - goes through
 * {@link #readFile(Path)}. The network is never used: a location with a URI
 * scheme other than <code>file:</code> is unavailable, exactly like a missing
 * file. The schema documents handed to the compiler are trusted and not
 * checked.
 * <p>
 * {@link #LOCAL_FILES} reads any local file; {@link #confinedTo(Path...)}
 * reads only files below the given directories (symbolic links resolved before
 * the check).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdFileAccess
{
  /** Any local file may be read; never the network. */
  public static final XsdFileAccess LOCAL_FILES = new XsdFileAccess (null);

  private static final Pattern SCHEME = Pattern.compile ("^([A-Za-z][A-Za-z0-9+.-]*):");

  /** The allowed directories as real paths; <code>null</code> for any. */
  private final ICommonsList <Path> m_aAllowedDirs;

  private XsdFileAccess (@Nullable final ICommonsList <Path> aAllowedDirs)
  {
    m_aAllowedDirs = aAllowedDirs;
  }

  /**
   * @param aDirs
   *        The directories whose files (recursively) may be read.
   * @return A confined file access. A directory that does not exist allows
   *         nothing.
   */
  @NonNull
  public static XsdFileAccess confinedTo (@NonNull final Path... aDirs)
  {
    final ICommonsList <Path> aReal = new CommonsArrayList <> ();
    for (final Path aDir : aDirs)
      try
      {
        aReal.add (aDir.toRealPath ());
      }
      catch (final IOException ex)
      {
        // Not existing: allows nothing
      }
    return new XsdFileAccess (aReal);
  }

  /** @return <code>true</code> if any local file may be read. */
  public boolean isUnrestricted ()
  {
    return m_aAllowedDirs == null;
  }

  /**
   * @param aPath
   *        The file.
   * @return <code>true</code> if the file may be read (it need not exist).
   */
  public boolean isAllowed (@NonNull final Path aPath)
  {
    if (m_aAllowedDirs == null)
      return true;
    final Path aReal;
    try
    {
      aReal = aPath.toRealPath ();
    }
    catch (final IOException ex)
    {
      return false;
    }
    for (final Path aDir : m_aAllowedDirs)
      if (aReal.startsWith (aDir))
        return true;
    return false;
  }

  /**
   * @param aPath
   *        The file to read.
   * @return The bytes, or <code>null</code> if the file is not allowed, not a
   *         regular file or cannot be read.
   */
  public byte @Nullable [] readFile (@NonNull final Path aPath)
  {
    if (!isAllowed (aPath) || !Files.isRegularFile (aPath))
      return null;
    try
    {
      return Files.readAllBytes (aPath);
    }
    catch (final IOException ex)
    {
      return null;
    }
  }

  /**
   * Resolve a <code>schemaLocation</code> to a local path.
   *
   * @param aBaseDir
   *        The directory of the referring document; <code>null</code> if it
   *        has none (then only absolute <code>file:</code> URIs resolve).
   * @param sLocation
   *        The location as written.
   * @return The normalised path, or <code>null</code> if the location is not
   *         local (another URI scheme) or cannot be a path.
   */
  @Nullable
  public static Path resolveLocation (@Nullable final Path aBaseDir, @NonNull final String sLocation)
  {
    final var aMatcher = SCHEME.matcher (sLocation);
    // A one-letter scheme is a Windows drive letter
    if (aMatcher.find () && aMatcher.group (1).length () > 1)
    {
      if (!"file".equalsIgnoreCase (aMatcher.group (1)))
        return null;
      try
      {
        return Paths.get (URI.create (sLocation)).normalize ();
      }
      catch (final RuntimeException ex)
      {
        return null;
      }
    }
    try
    {
      final Path aLoc = Paths.get (sLocation);
      if (aLoc.isAbsolute ())
        return aLoc.normalize ();
      if (aBaseDir == null)
        return null;
      return aBaseDir.resolve (aLoc).normalize ();
    }
    catch (final InvalidPathException ex)
    {
      return null;
    }
  }

  @Override
  public String toString ()
  {
    return m_aAllowedDirs == null ? "XsdFileAccess[local files]" : "XsdFileAccess" + m_aAllowedDirs;
  }
}
