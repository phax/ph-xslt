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
package com.helger.xpath.serialize;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.Normalizer;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.XPathException;

/**
 * The character repertoire of the output encoding plus the character map and
 * Unicode normalisation step shared by all output methods. The serializer
 * produces a Java string; the encoding only decides which characters must be
 * written as character references (or JSON escapes).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class OutputCharset
{
  private final CharsetEncoder m_aEncoder;
  private final Normalizer.Form m_eNormForm;
  private final SerializationParams m_aParams;

  OutputCharset (@NonNull final SerializationParams aParams)
  {
    m_aParams = aParams;
    final String sEncoding = aParams.getEncoding ().trim ();
    final Charset aCharset;
    try
    {
      aCharset = Charset.forName (sEncoding);
    }
    catch (final IllegalCharsetNameException | UnsupportedCharsetException ex)
    {
      throw XPathException.of ("SESU0007", "Unsupported encoding '" + sEncoding + "'");
    }
    final String sCanonical = aCharset.name ().toUpperCase (Locale.ROOT);
    if (aCharset.equals (StandardCharsets.UTF_8) || sCanonical.startsWith ("UTF-") || sCanonical.equals ("GB18030"))
      m_aEncoder = null;
    else
      m_aEncoder = aCharset.canEncode () ? aCharset.newEncoder () : null;
    switch (aParams.getNormalizationForm ())
    {
      case "NFC":
        m_eNormForm = Normalizer.Form.NFC;
        break;
      case "NFD":
        m_eNormForm = Normalizer.Form.NFD;
        break;
      case "NFKC":
        m_eNormForm = Normalizer.Form.NFKC;
        break;
      case "NFKD":
        m_eNormForm = Normalizer.Form.NFKD;
        break;
      case "none":
        m_eNormForm = null;
        break;
      default:
        throw XPathException.of ("SESU0011", "Unsupported normalization-form '" + aParams.getNormalizationForm () + "'");
    }
  }

  /**
   * @param nCodePoint
   *        A code point.
   * @return <code>true</code> if the output encoding can represent it.
   */
  boolean canEncode (final int nCodePoint)
  {
    if (m_aEncoder == null || nCodePoint < 0x80)
      return true;
    if (Character.isSupplementaryCodePoint (nCodePoint))
      return m_aEncoder.canEncode (new String (Character.toChars (nCodePoint)));
    return m_aEncoder.canEncode ((char) nCodePoint);
  }

  @NonNull
  String normalize (@NonNull final String s)
  {
    if (m_eNormForm == null || s.isEmpty ())
      return s;
    return Normalizer.normalize (s, m_eNormForm);
  }

  /**
   * @param nCodePoint
   *        A code point.
   * @return The character map replacement or <code>null</code>.
   */
  @Nullable
  String getMapping (final int nCodePoint)
  {
    return m_aParams.getCharacterMapping (nCodePoint);
  }

  boolean hasCharacterMap ()
  {
    return m_aParams.hasCharacterMap ();
  }

  /** Escapes a run of unmapped characters. */
  @FunctionalInterface
  interface IEscaper
  {
    void escape (@NonNull StringBuilder aSB, @NonNull String sRun);
  }

  /**
   * Apply the character map: mapped characters are written verbatim, the runs
   * between them are normalised and passed to the escaper.
   *
   * @param aSB
   *        Target.
   * @param s
   *        The text.
   * @param aEscaper
   *        Escaper for unmapped runs.
   */
  void mapAndEscape (@NonNull final StringBuilder aSB, @NonNull final String s, @NonNull final IEscaper aEscaper)
  {
    if (!hasCharacterMap ())
    {
      aEscaper.escape (aSB, normalize (s));
      return;
    }
    int nRunStart = 0;
    int i = 0;
    while (i < s.length ())
    {
      final int cp = s.codePointAt (i);
      final int nLen = Character.charCount (cp);
      final String sRep = getMapping (cp);
      if (sRep != null)
      {
        if (i > nRunStart)
          aEscaper.escape (aSB, normalize (s.substring (nRunStart, i)));
        aSB.append (sRep);
        nRunStart = i + nLen;
      }
      i += nLen;
    }
    if (nRunStart < s.length ())
      aEscaper.escape (aSB, normalize (s.substring (nRunStart)));
  }
}
