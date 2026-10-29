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
package com.helger.xslt.compile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * A resolver that reads <code>file:</code> URIs and nothing else. Used for
 * stylesheet modules when the compile options give no resolver.
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

  @Nullable
  private static Path _getPath (@NonNull final String sAbsoluteUri)
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

  @Nullable
  public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
  {
    final Path aPath = _getPath (sAbsoluteUri);
    if (aPath == null)
      return null;
    try
    {
      final IXdmNode ret = new XdmParser ().setLoadExternalEntities (m_bLoadExternalEntities).parse (aPath);
      if (ret.getDocumentUri () == null)
        ret.setDocumentUri (sAbsoluteUri);
      return ret;
    }
    catch (final XdmParseException ex)
    {
      return null;
    }
  }

  @Nullable
  public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
  {
    final Path aPath = _getPath (sAbsoluteUri);
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
