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
package com.helger.phoxtrot.xslt.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;

/**
 * Where the serialized principal result of a transformation goes: a writer
 * (characters) or an output stream (bytes in the output encoding). Neither is
 * closed.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class ResultTarget
{
  private final OutputStream m_aOS;
  private Writer m_aWriter;

  private ResultTarget (@Nullable final OutputStream aOS, @Nullable final Writer aWriter)
  {
    m_aOS = aOS;
    m_aWriter = aWriter;
  }

  @NonNull
  static ResultTarget of (@NonNull final OutputStream aOS)
  {
    return new ResultTarget (aOS, null);
  }

  @NonNull
  static ResultTarget of (@NonNull final Writer aWriter)
  {
    return new ResultTarget (null, aWriter);
  }

  /**
   * Get the writer for the serialized characters. For an output stream they
   * are encoded with the encoding parameter. Java's UTF-16 encoder writes a
   * byte order mark of its own, so UTF-16 is written big-endian with exactly
   * one byte order mark (which XML 1.0 section 4.3.3 requires for UTF-16),
   * whether or not byte-order-mark is set.
   *
   * @param aParams
   *        The serialization parameters of the result.
   * @return The writer. Never <code>null</code>.
   * @throws com.helger.phoxtrot.xpath.XPathException
   *         SESU0007 for an unsupported encoding
   */
  @NonNull
  Writer open (@NonNull final SerializationParams aParams)
  {
    if (m_aWriter == null)
    {
      final Charset aCharset = XdmSerializer.getEncodingCharset (aParams);
      if (aCharset.equals (StandardCharsets.UTF_16))
      {
        m_aWriter = new OutputStreamWriter (m_aOS, StandardCharsets.UTF_16BE);
        if (!aParams.isByteOrderMark ())
          try
          {
            m_aWriter.write ('﻿');
          }
          catch (final IOException ex)
          {
            throw new UncheckedIOException (ex);
          }
      }
      else
        m_aWriter = new OutputStreamWriter (m_aOS, aCharset);
    }
    return m_aWriter;
  }

  /** Flush what was written so far, ignoring errors (after a failure). */
  void flushQuietly ()
  {
    if (m_aWriter != null)
      try
      {
        m_aWriter.flush ();
      }
      catch (final IOException ex)
      {
        // The original error is reported
      }
  }
}
