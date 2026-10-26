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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;

/**
 * A schema document handed to the compiler: the content plus the directory its
 * relative references resolve against.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdSchemaSource
{
  private final String m_sText;
  private final byte [] m_aBytes;
  private final Path m_aPath;
  private final Path m_aBaseDir;

  private XsdSchemaSource (@Nullable final String sText,
                           final byte @Nullable [] aBytes,
                           @Nullable final Path aPath,
                           @Nullable final Path aBaseDir)
  {
    m_sText = sText;
    m_aBytes = aBytes;
    m_aPath = aPath;
    m_aBaseDir = aBaseDir;
  }

  /**
   * @param sXml
   *        The schema document text.
   * @param aBaseDir
   *        The directory relative references resolve against;
   *        <code>null</code> if there is none (relative references are then
   *        unavailable).
   * @return The source.
   */
  @NonNull
  public static XsdSchemaSource ofText (@NonNull final String sXml, @Nullable final Path aBaseDir)
  {
    ValueEnforcer.notNull (sXml, "Xml");
    return new XsdSchemaSource (sXml, null, null, aBaseDir);
  }

  /**
   * @param aPath
   *        The schema document file. Its directory is the base directory.
   * @return The source.
   * @throws IOException
   *         if the file cannot be read
   */
  @NonNull
  public static XsdSchemaSource ofFile (@NonNull final Path aPath) throws IOException
  {
    ValueEnforcer.notNull (aPath, "Path");
    final Path aAbs = aPath.toAbsolutePath ().normalize ();
    return new XsdSchemaSource (null, Files.readAllBytes (aAbs), aAbs, aAbs.getParent ());
  }

  /**
   * @param aBytes
   *        The raw document bytes (the encoding is detected from them).
   * @param aPath
   *        The document location, if any.
   * @param aBaseDir
   *        The base directory, if any.
   * @return The source.
   */
  @NonNull
  static XsdSchemaSource ofBytes (final byte @NonNull [] aBytes,
                                  @Nullable final Path aPath,
                                  @Nullable final Path aBaseDir)
  {
    return new XsdSchemaSource (null, aBytes, aPath, aBaseDir);
  }

  /** @return The document file, <code>null</code> for a text source. */
  @Nullable
  public Path getPath ()
  {
    return m_aPath;
  }

  /** @return The base directory, may be <code>null</code>. */
  @Nullable
  public Path getBaseDir ()
  {
    return m_aBaseDir;
  }

  /**
   * @return The identity of the content, used to recognise a document that is
   *         reached twice (go-xslt keys on the raw content).
   */
  @NonNull
  String getContentKey ()
  {
    return m_sText != null ? m_sText : new String (m_aBytes, StandardCharsets.ISO_8859_1);
  }

  /** @return A new SAX input source of the content. */
  @NonNull
  InputSource createInputSource ()
  {
    final InputSource ret = m_sText != null ? new InputSource (new StringReader (m_sText))
                                            : new InputSource (new ByteArrayInputStream (m_aBytes));
    if (m_aPath != null)
      ret.setSystemId (m_aPath.toUri ().toString ());
    return ret;
  }

  @Override
  public String toString ()
  {
    return m_aPath != null ? m_aPath.toString () : "XsdSchemaSource[text]";
  }
}
