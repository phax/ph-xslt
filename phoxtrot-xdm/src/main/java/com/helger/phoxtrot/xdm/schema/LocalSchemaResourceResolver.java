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
package com.helger.phoxtrot.xdm.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.helger.annotation.concurrent.Immutable;

/**
 * The resource rule of the JAXP-based schema providers (Xerces-J, JDK), the
 * same as the native validator's: every resource a schema processor reads on
 * its own (referenced schema documents, external DTDs and entities of schema
 * documents) is read only if it is a local <code>file:</code> URI and local
 * reads are allowed; the network is never used. A resource that is not
 * allowed is handed to the processor as an input that cannot be read, so it
 * is unavailable exactly like a missing file.
 *
 * @author Philip Helger
 */
@Immutable
public final class LocalSchemaResourceResolver implements LSResourceResolver
{
  /** An input whose stream fails, for a resource that is not allowed. */
  private static final class RefusedInput implements LSInput
  {
    private final String m_sSystemId;

    RefusedInput (@Nullable final String sSystemId)
    {
      m_sSystemId = sSystemId;
    }

    @Nullable
    public Reader getCharacterStream ()
    {
      return null;
    }

    public void setCharacterStream (final Reader aCharacterStream)
    {}

    @NonNull
    public InputStream getByteStream ()
    {
      return new InputStream ()
      {
        @Override
        public int read () throws IOException
        {
          throw new IOException ("Reading '" + m_sSystemId + "' is not allowed");
        }
      };
    }

    public void setByteStream (final InputStream aByteStream)
    {}

    @Nullable
    public String getStringData ()
    {
      return null;
    }

    public void setStringData (final String sStringData)
    {}

    @Nullable
    public String getSystemId ()
    {
      return m_sSystemId;
    }

    public void setSystemId (final String sSystemId)
    {}

    @Nullable
    public String getPublicId ()
    {
      return null;
    }

    public void setPublicId (final String sPublicId)
    {}

    @Nullable
    public String getBaseURI ()
    {
      return null;
    }

    public void setBaseURI (final String sBaseURI)
    {}

    @Nullable
    public String getEncoding ()
    {
      return null;
    }

    public void setEncoding (final String sEncoding)
    {}

    public boolean getCertifiedText ()
    {
      return false;
    }

    public void setCertifiedText (final boolean bCertifiedText)
    {}
  }

  private final boolean m_bReadLocalFiles;

  /**
   * @param bReadLocalFiles
   *        <code>true</code> to let the processor read local
   *        <code>file:</code> resources itself, <code>false</code> to refuse
   *        every resource.
   */
  public LocalSchemaResourceResolver (final boolean bReadLocalFiles)
  {
    m_bReadLocalFiles = bReadLocalFiles;
  }

  /**
   * @return The system ID resolved against the base URI, or the system ID as
   *         it is if it cannot be resolved.
   */
  @Nullable
  private static String _getAbsoluteUri (@Nullable final String sSystemId, @Nullable final String sBaseURI)
  {
    if (sSystemId == null)
      return null;
    try
    {
      final URI aUri = new URI (sSystemId);
      if (aUri.isAbsolute () || sBaseURI == null)
        return sSystemId;
      return new URI (sBaseURI).resolve (aUri).toString ();
    }
    catch (final Exception ex)
    {
      return sSystemId;
    }
  }

  /**
   * @return <code>null</code> (the processor reads the resource itself) for
   *         an allowed local file, otherwise an input that cannot be read.
   */
  @Nullable
  public LSInput resolveResource (final String sType,
                                  final String sNamespaceURI,
                                  final String sPublicId,
                                  final String sSystemId,
                                  final String sBaseURI)
  {
    final String sAbsolute = _getAbsoluteUri (sSystemId, sBaseURI);
    if (m_bReadLocalFiles && sAbsolute != null && sAbsolute.toLowerCase (Locale.ROOT).startsWith ("file:"))
      return null;
    return new RefusedInput (sAbsolute);
  }
}
