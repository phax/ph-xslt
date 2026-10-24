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
package com.helger.xpath.fn;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.XPathException;

/**
 * A strict JSON (RFC 7159) parser for fn:parse-json, fn:json-doc and
 * fn:json-to-xml. Nesting is handled with an explicit stack, and strings are
 * decoded with the escape and fallback rules of F&amp;O 3.1 section 17.5.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class JsonParser
{
  /** Receives the parse events. */
  interface IHandler
  {
    void startObject ();

    /**
     * @param sKey
     *        The decoded key.
     * @param bEscaped
     *        <code>true</code> if the key contains JSON escape sequences
     *        (only with the escape option).
     */
    void key (@NonNull String sKey, boolean bEscaped);

    void endObject ();

    void startArray ();

    void endArray ();

    /**
     * @param sValue
     *        The decoded string.
     * @param bEscaped
     *        <code>true</code> if the string contains JSON escape sequences
     *        (only with the escape option).
     */
    void string (@NonNull String sValue, boolean bEscaped);

    /**
     * @param sLexical
     *        The number as written in the input.
     */
    void number (@NonNull String sLexical);

    void bool (boolean bValue);

    void nul ();
  }

  private final String m_sInput;
  private final boolean m_bEscape;
  private final UnaryOperator <String> m_aFallback;
  private int m_nPos;
  // Whether the last string token contained an escape sequence (escape option)
  private boolean m_bLastEscaped;

  /**
   * @param sInput
   *        The JSON text; a leading byte order mark is ignored.
   * @param bEscape
   *        The escape option.
   * @param aFallback
   *        The fallback function (called with the escape sequence of a
   *        character that is not a valid XML character), or <code>null</code>
   *        for the default (U+FFFD).
   */
  JsonParser (@NonNull final String sInput, final boolean bEscape, @Nullable final UnaryOperator <String> aFallback)
  {
    m_sInput = sInput;
    m_bEscape = bEscape;
    m_aFallback = aFallback;
    m_nPos = sInput.startsWith ("﻿") ? 1 : 0;
  }

  @NonNull
  private XPathException _error (@NonNull final String sMessage)
  {
    return XPathException.of ("FOJS0001", "Invalid JSON at offset " + m_nPos + ": " + sMessage);
  }

  private void _skipWhitespace ()
  {
    while (m_nPos < m_sInput.length ())
    {
      final char c = m_sInput.charAt (m_nPos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
        m_nPos++;
      else
        break;
    }
  }

  private char _peek ()
  {
    _skipWhitespace ();
    if (m_nPos >= m_sInput.length ())
      throw _error ("unexpected end of input");
    return m_sInput.charAt (m_nPos);
  }

  private void _expect (final char c)
  {
    if (_peek () != c)
      throw _error ("expected '" + c + "'");
    m_nPos++;
  }

  private void _literal (@NonNull final String sLiteral)
  {
    if (!m_sInput.startsWith (sLiteral, m_nPos))
      throw _error ("invalid token");
    m_nPos += sLiteral.length ();
  }

  private static boolean _isDigit (final char c)
  {
    return c >= '0' && c <= '9';
  }

  @NonNull
  private String _number ()
  {
    final int nStart = m_nPos;
    final String s = m_sInput;
    if (m_nPos < s.length () && s.charAt (m_nPos) == '-')
      m_nPos++;
    if (m_nPos >= s.length () || !_isDigit (s.charAt (m_nPos)))
      throw _error ("invalid number");
    if (s.charAt (m_nPos) == '0')
      m_nPos++;
    else
      while (m_nPos < s.length () && _isDigit (s.charAt (m_nPos)))
        m_nPos++;
    if (m_nPos < s.length () && s.charAt (m_nPos) == '.')
    {
      m_nPos++;
      if (m_nPos >= s.length () || !_isDigit (s.charAt (m_nPos)))
        throw _error ("invalid number");
      while (m_nPos < s.length () && _isDigit (s.charAt (m_nPos)))
        m_nPos++;
    }
    if (m_nPos < s.length () && (s.charAt (m_nPos) == 'e' || s.charAt (m_nPos) == 'E'))
    {
      m_nPos++;
      if (m_nPos < s.length () && (s.charAt (m_nPos) == '+' || s.charAt (m_nPos) == '-'))
        m_nPos++;
      if (m_nPos >= s.length () || !_isDigit (s.charAt (m_nPos)))
        throw _error ("invalid number");
      while (m_nPos < s.length () && _isDigit (s.charAt (m_nPos)))
        m_nPos++;
    }
    return s.substring (nStart, m_nPos);
  }

  static boolean isXmlChar (final int cp)
  {
    return cp == 0x9 ||
           cp == 0xA ||
           cp == 0xD ||
           cp >= 0x20 && cp <= 0xD7FF ||
           cp >= 0xE000 && cp <= 0xFFFD ||
           cp >= 0x10000 && cp <= 0x10FFFF;
  }

  private static void _appendHex (@NonNull final StringBuilder aSB, final int c)
  {
    aSB.append ("\\u").append (String.format (Locale.ROOT, "%04X", Integer.valueOf (c)));
  }

  /** Emit one decoded character with the escape/fallback rules. */
  private void _emit (@NonNull final StringBuilder aSB, final int cp, @Nullable final String sSeq)
  {
    if (m_bEscape)
    {
      switch (cp)
      {
        case '\\':
          aSB.append ("\\\\");
          return;
        case '\b':
          aSB.append ("\\b");
          return;
        case '\f':
          aSB.append ("\\f");
          return;
        case '\n':
          aSB.append ("\\n");
          return;
        case '\r':
          aSB.append ("\\r");
          return;
        case '\t':
          aSB.append ("\\t");
          return;
        default:
          if (cp < 0x20 || cp >= 0x7F && cp <= 0x9F || !isXmlChar (cp))
          {
            if (Character.isSupplementaryCodePoint (cp))
            {
              _appendHex (aSB, Character.highSurrogate (cp));
              _appendHex (aSB, Character.lowSurrogate (cp));
            }
            else
              _appendHex (aSB, cp);
          }
          else
            aSB.appendCodePoint (cp);
          return;
      }
    }
    if (isXmlChar (cp))
      aSB.appendCodePoint (cp);
    else
      if (m_aFallback == null)
        aSB.append ('�');
      else
      {
        String sEscape = sSeq;
        if (sEscape == null)
        {
          final StringBuilder aTmp = new StringBuilder ();
          _appendHex (aTmp, cp);
          sEscape = aTmp.toString ();
        }
        aSB.append (m_aFallback.apply (sEscape));
      }
  }

  private int _hex4 (final int nStart)
  {
    if (nStart + 4 > m_sInput.length ())
      throw _error ("truncated \\u escape");
    int ret = 0;
    for (int i = nStart; i < nStart + 4; ++i)
    {
      final int d = Character.digit (m_sInput.charAt (i), 16);
      if (d < 0)
        throw _error ("invalid \\u escape");
      ret = ret * 16 + d;
    }
    return ret;
  }

  /**
   * Parse a string token. m_nPos is at the opening quote.
   * Sets m_bLastEscaped when the result contains a backslash escape.
   */
  @NonNull
  private String _string ()
  {
    m_nPos++;
    final StringBuilder aSB = new StringBuilder ();
    final String s = m_sInput;
    while (true)
    {
      if (m_nPos >= s.length ())
        throw _error ("unterminated string");
      final char c = s.charAt (m_nPos);
      if (c == '"')
      {
        m_nPos++;
        break;
      }
      if (c < 0x20)
        throw _error ("unescaped control character in string");
      if (c != '\\')
      {
        final int cp = s.codePointAt (m_nPos);
        m_nPos += Character.charCount (cp);
        _emit (aSB, cp, null);
        continue;
      }
      if (m_nPos + 1 >= s.length ())
        throw _error ("unterminated escape");
      final char cEsc = s.charAt (m_nPos + 1);
      int cp;
      int nLen = 2;
      switch (cEsc)
      {
        case '"':
          cp = '"';
          break;
        case '\\':
          cp = '\\';
          break;
        case '/':
          cp = '/';
          break;
        case 'b':
          cp = '\b';
          break;
        case 'f':
          cp = '\f';
          break;
        case 'n':
          cp = '\n';
          break;
        case 'r':
          cp = '\r';
          break;
        case 't':
          cp = '\t';
          break;
        case 'u':
        {
          cp = _hex4 (m_nPos + 2);
          nLen = 6;
          if (Character.isHighSurrogate ((char) cp) &&
              m_nPos + 12 <= s.length () &&
              s.charAt (m_nPos + 6) == '\\' &&
              s.charAt (m_nPos + 7) == 'u')
          {
            final int nLow = _hex4 (m_nPos + 8);
            if (Character.isLowSurrogate ((char) nLow))
            {
              cp = Character.toCodePoint ((char) cp, (char) nLow);
              nLen = 12;
            }
          }
          break;
        }
        default:
          throw _error ("invalid escape \\" + cEsc);
      }
      final String sSeq = s.substring (m_nPos, m_nPos + nLen);
      m_nPos += nLen;
      _emit (aSB, cp, sSeq);
    }
    final String ret = aSB.toString ();
    m_bLastEscaped = m_bEscape && ret.indexOf ('\\') >= 0;
    return ret;
  }

  /**
   * Parse the whole input.
   *
   * @param aHandler
   *        The event receiver.
   */
  void parse (@NonNull final IHandler aHandler)
  {
    // Container stack: true = object, false = array
    final ArrayDeque <Boolean> aStack = new ArrayDeque <> ();
    // State: expecting a value
    boolean bExpectValue = true;
    while (true)
    {
      if (bExpectValue)
      {
        final char c = _peek ();
        switch (c)
        {
          case '{':
            m_nPos++;
            aHandler.startObject ();
            if (_peek () == '}')
            {
              m_nPos++;
              aHandler.endObject ();
              bExpectValue = false;
            }
            else
            {
              aStack.push (Boolean.TRUE);
              if (_peek () != '"')
                throw _error ("expected an object key");
              final String sKey = _string ();
              aHandler.key (sKey, m_bLastEscaped);
              _expect (':');
            }
            continue;
          case '[':
            m_nPos++;
            aHandler.startArray ();
            if (_peek () == ']')
            {
              m_nPos++;
              aHandler.endArray ();
              bExpectValue = false;
            }
            else
              aStack.push (Boolean.FALSE);
            continue;
          case '"':
          {
            final String sValue = _string ();
            aHandler.string (sValue, m_bLastEscaped);
            break;
          }
          case 't':
            _literal ("true");
            aHandler.bool (true);
            break;
          case 'f':
            _literal ("false");
            aHandler.bool (false);
            break;
          case 'n':
            _literal ("null");
            aHandler.nul ();
            break;
          default:
            if (c == '-' || _isDigit (c))
              aHandler.number (_number ());
            else
              throw _error ("unexpected character '" + c + "'");
        }
        bExpectValue = false;
        continue;
      }
      // After a value
      if (aStack.isEmpty ())
      {
        _skipWhitespace ();
        if (m_nPos < m_sInput.length ())
          throw _error ("unexpected content after the JSON value");
        return;
      }
      final boolean bObject = aStack.peek ().booleanValue ();
      final char c = _peek ();
      if (c == ',')
      {
        m_nPos++;
        if (bObject)
        {
          if (_peek () != '"')
            throw _error ("expected an object key");
          final String sKey = _string ();
          aHandler.key (sKey, m_bLastEscaped);
          _expect (':');
        }
        bExpectValue = true;
      }
      else
        if (bObject && c == '}')
        {
          m_nPos++;
          aStack.pop ();
          aHandler.endObject ();
        }
        else
          if (!bObject && c == ']')
          {
            m_nPos++;
            aStack.pop ();
            aHandler.endArray ();
          }
          else
            throw _error ("unexpected character '" + c + "'");
    }
  }
}
