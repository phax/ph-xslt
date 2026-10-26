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
package com.helger.xsd.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * A resource resolver that keeps a validator offline: references that resolve
 * to a <code>file:</code> URI (or to a relative path) are left to the
 * validator's default resolution, every other URI (<code>http:</code>,
 * <code>https:</code>, <code>jar:</code>, ...) is answered with a stream that
 * fails on the first read. The validator then sees the resource as
 * unavailable, exactly as an offline processor would.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsdLocalOnlyResourceResolver implements LSResourceResolver
{
  private static final Pattern SCHEME = Pattern.compile ("^([A-Za-z][A-Za-z0-9+.-]*):");

  private final ICommonsList <String> m_aBlocked = new CommonsArrayList <> ();

  /** An input whose byte stream refuses to be read. */
  private static final class BlockedInput implements LSInput
  {
    private final String m_sURI;
    private String m_sPublicID;
    private String m_sBaseURI;

    BlockedInput (@NonNull final String sURI, @Nullable final String sPublicID, @Nullable final String sBaseURI)
    {
      m_sURI = sURI;
      m_sPublicID = sPublicID;
      m_sBaseURI = sBaseURI;
    }

    public Reader getCharacterStream ()
    {
      return null;
    }

    public void setCharacterStream (final Reader aCharacterStream)
    {
      // ignored
    }

    public InputStream getByteStream ()
    {
      return new InputStream ()
      {
        @Override
        public int read () throws IOException
        {
          throw new IOException ("Network access is disabled: '" + m_sURI + "'");
        }
      };
    }

    public void setByteStream (final InputStream aByteStream)
    {
      // ignored
    }

    public String getStringData ()
    {
      return null;
    }

    public void setStringData (final String sStringData)
    {
      // ignored
    }

    public String getSystemId ()
    {
      return m_sURI;
    }

    public void setSystemId (final String sSystemID)
    {
      // ignored: the system ID must never be opened
    }

    public String getPublicId ()
    {
      return m_sPublicID;
    }

    public void setPublicId (final String sPublicID)
    {
      m_sPublicID = sPublicID;
    }

    public String getBaseURI ()
    {
      return m_sBaseURI;
    }

    public void setBaseURI (final String sBaseURI)
    {
      m_sBaseURI = sBaseURI;
    }

    public String getEncoding ()
    {
      return null;
    }

    public void setEncoding (final String sEncoding)
    {
      // ignored
    }

    public boolean getCertifiedText ()
    {
      return false;
    }

    public void setCertifiedText (final boolean bCertifiedText)
    {
      // ignored
    }
  }

  /**
   * @return The scheme of the absolute reference in lower case, or
   *         <code>null</code> for a relative reference.
   */
  @Nullable
  private static String _getScheme (@NonNull final String sSystemID, @Nullable final String sBaseURI)
  {
    try
    {
      final URI aRef = new URI (sSystemID);
      final URI aAbs = aRef.isAbsolute () || sBaseURI == null ? aRef : new URI (sBaseURI).resolve (aRef);
      final String sScheme = aAbs.getScheme ();
      return sScheme == null ? null : sScheme.toLowerCase (Locale.ROOT);
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      // Not a valid URI: decide by the literal text of the reference and the base
      final String sText = SCHEME.matcher (sSystemID).find () || sBaseURI == null ? sSystemID : sBaseURI;
      final Matcher aMatcher = SCHEME.matcher (sText);
      return aMatcher.find () ? aMatcher.group (1).toLowerCase (Locale.ROOT) : null;
    }
  }

  @Nullable
  public LSInput resolveResource (@Nullable final String sType,
                                  @Nullable final String sNamespaceURI,
                                  @Nullable final String sPublicID,
                                  @Nullable final String sSystemID,
                                  @Nullable final String sBaseURI)
  {
    if (sSystemID == null)
      return null;
    final String sScheme = _getScheme (sSystemID, sBaseURI);
    if (sScheme == null || "file".equals (sScheme))
    {
      // Local: the validator's default resolution
      return null;
    }
    m_aBlocked.add (sSystemID);
    return new BlockedInput (sSystemID, sPublicID, sBaseURI);
  }

  /** @return Every reference that was refused, in order. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getBlocked ()
  {
    return m_aBlocked.getClone ();
  }
}
