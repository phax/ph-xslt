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
package com.helger.xpath.parser;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;

/**
 * The XPath 3.1 tokenizer (a port of go-xslt's <code>lexer.go</code>). It
 * applies the terminal delimitation rules of XPath 3.1 A.2 but leaves the
 * operator-versus-name decision (<code>div</code>, <code>*</code>, ...) to the
 * parser, which always knows whether it expects an operand or an operator.
 *
 * @author Philip Helger
 */
final class XPathLexer
{
  private final String m_sSrc;
  private final int m_nLen;
  private final ICommonsList <XPathToken> m_aTokens = new CommonsArrayList <> ();
  private int m_nPos;

  private XPathLexer (@NonNull final String sSrc)
  {
    m_sSrc = sSrc;
    m_nLen = sSrc.length ();
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is an XML 1.0 (5th edition) NameStartChar
   *         without the colon.
   */
  static boolean isNCNameStartChar (final int c)
  {
    return (c >= 'A' && c <= 'Z') ||
           c == '_' ||
           (c >= 'a' && c <= 'z') ||
           (c >= 0xC0 && c <= 0xD6) ||
           (c >= 0xD8 && c <= 0xF6) ||
           (c >= 0xF8 && c <= 0x2FF) ||
           (c >= 0x370 && c <= 0x37D) ||
           (c >= 0x37F && c <= 0x1FFF) ||
           (c >= 0x200C && c <= 0x200D) ||
           (c >= 0x2070 && c <= 0x218F) ||
           (c >= 0x2C00 && c <= 0x2FEF) ||
           (c >= 0x3001 && c <= 0xD7FF) ||
           (c >= 0xF900 && c <= 0xFDCF) ||
           (c >= 0xFDF0 && c <= 0xFFFD) ||
           (c >= 0x10000 && c <= 0xEFFFF);
  }

  /**
   * @param c
   *        Code point.
   * @return <code>true</code> if it is an XML 1.0 (5th edition) NameChar
   *         without the colon.
   */
  static boolean isNCNameChar (final int c)
  {
    return isNCNameStartChar (c) ||
           c == '-' ||
           c == '.' ||
           (c >= '0' && c <= '9') ||
           c == 0xB7 ||
           (c >= 0x300 && c <= 0x36F) ||
           (c >= 0x203F && c <= 0x2040);
  }

  /**
   * @param s
   *        String to check.
   * @return <code>true</code> if the string is a valid NCName.
   */
  static boolean isNCName (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    int i = 0;
    while (i < s.length ())
    {
      final int c = s.codePointAt (i);
      if (i == 0 ? !isNCNameStartChar (c) : !isNCNameChar (c))
        return false;
      i += Character.charCount (c);
    }
    return true;
  }

  private static boolean _isWhitespace (final int c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  private static boolean _isDigit (final int c)
  {
    return c >= '0' && c <= '9';
  }

  @NonNull
  private XPathException _error (@NonNull final String sMsg, final int nPos)
  {
    return XPathException.syntax (sMsg + " at offset " + nPos + " in '" + XPathParserImpl.abbreviate (m_sSrc) + "'");
  }

  private int _peek (final int nOffset)
  {
    final int nIdx = m_nPos + nOffset;
    return nIdx < m_nLen ? m_sSrc.charAt (nIdx) : -1;
  }

  private int _codePointAt (final int nIdx)
  {
    return nIdx < m_nLen ? m_sSrc.codePointAt (nIdx) : -1;
  }

  private void _push (@NonNull final ETokenKind eKind, @NonNull final String sText, final int nStart)
  {
    m_aTokens.add (new XPathToken (eKind, sText, null, nStart));
  }

  private void _emit (@NonNull final ETokenKind eKind, @NonNull final String sText)
  {
    _push (eKind, sText, m_nPos);
    m_nPos += sText.length ();
  }

  private void _skipComment ()
  {
    final int nStart = m_nPos;
    int nDepth = 0;
    while (m_nPos < m_nLen)
    {
      final char c = m_sSrc.charAt (m_nPos);
      if (c == '(' && _peek (1) == ':')
      {
        nDepth++;
        m_nPos += 2;
        continue;
      }
      if (c == ':' && _peek (1) == ')')
      {
        nDepth--;
        m_nPos += 2;
        if (nDepth == 0)
          return;
        continue;
      }
      m_nPos++;
    }
    throw _error ("Unterminated comment", nStart);
  }

  /** Reads an NCName starting at the current position; the caller has checked the start char. */
  @NonNull
  private String _readNCName ()
  {
    final int nStart = m_nPos;
    while (m_nPos < m_nLen)
    {
      final int c = m_sSrc.codePointAt (m_nPos);
      if (!isNCNameChar (c))
        break;
      m_nPos += Character.charCount (c);
    }
    return m_sSrc.substring (nStart, m_nPos);
  }

  /**
   * Collapse whitespace of a BracedURILiteral like xs:anyURI whitespace
   * normalisation does.
   */
  @NonNull
  private static String _collapseWhitespace (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length ());
    boolean bPendingSpace = false;
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (_isWhitespace (c))
        bPendingSpace = true;
      else
      {
        if (bPendingSpace && aSB.length () > 0)
          aSB.append (' ');
        bPendingSpace = false;
        aSB.append (c);
      }
    }
    return aSB.toString ();
  }

  private void _bracedName ()
  {
    final int nStart = m_nPos;
    m_nPos += 2;
    final int nUriStart = m_nPos;
    while (m_nPos < m_nLen && m_sSrc.charAt (m_nPos) != '}')
    {
      if (m_sSrc.charAt (m_nPos) == '{')
        throw _error ("'{' is not allowed in a braced URI literal", m_nPos);
      m_nPos++;
    }
    if (m_nPos >= m_nLen)
      throw _error ("Unterminated braced URI literal", nStart);
    final String sUri = _collapseWhitespace (m_sSrc.substring (nUriStart, m_nPos));
    m_nPos++;
    if (_peek (0) == '*')
    {
      m_nPos++;
      m_aTokens.add (new XPathToken (ETokenKind.BRACED_WILDCARD, "*", sUri, nStart));
      return;
    }
    if (!isNCNameStartChar (_codePointAt (m_nPos)))
      throw _error ("Braced URI literal must be followed by a local name", nStart);
    final String sLocal = _readNCName ();
    m_aTokens.add (new XPathToken (ETokenKind.BRACED_NAME, sLocal, sUri, nStart));
  }

  private void _literal (final char cQuote)
  {
    final int nStart = m_nPos;
    int nPos = m_nPos + 1;
    final StringBuilder aSB = new StringBuilder ();
    while (true)
    {
      final int nEnd = m_sSrc.indexOf (cQuote, nPos);
      if (nEnd < 0)
        throw _error ("Unterminated string literal", nStart);
      aSB.append (m_sSrc, nPos, nEnd);
      nPos = nEnd + 1;
      if (nPos < m_nLen && m_sSrc.charAt (nPos) == cQuote)
      {
        // Doubled delimiter stands for one quote character
        aSB.append (cQuote);
        nPos++;
        continue;
      }
      break;
    }
    m_nPos = nPos;
    _push (ETokenKind.STRING, aSB.toString (), nStart);
  }

  private void _number ()
  {
    final int nStart = m_nPos;
    boolean bDot = false;
    boolean bExp = false;
    while (m_nPos < m_nLen && _isDigit (m_sSrc.charAt (m_nPos)))
      m_nPos++;
    if (m_nPos < m_nLen && m_sSrc.charAt (m_nPos) == '.')
    {
      bDot = true;
      m_nPos++;
      while (m_nPos < m_nLen && _isDigit (m_sSrc.charAt (m_nPos)))
        m_nPos++;
    }
    if (m_nPos < m_nLen && (m_sSrc.charAt (m_nPos) == 'e' || m_sSrc.charAt (m_nPos) == 'E'))
    {
      bExp = true;
      m_nPos++;
      if (m_nPos < m_nLen && (m_sSrc.charAt (m_nPos) == '+' || m_sSrc.charAt (m_nPos) == '-'))
        m_nPos++;
      final int nDigits = m_nPos;
      while (m_nPos < m_nLen && _isDigit (m_sSrc.charAt (m_nPos)))
        m_nPos++;
      if (nDigits == m_nPos)
        throw _error ("Missing exponent digits in numeric literal '" + m_sSrc.substring (nStart, m_nPos) + "'",
                      nStart);
    }
    if (m_nPos < m_nLen)
    {
      // A numeric literal must be delimited: "10div 3" and "1.2.3" are errors.
      // ':' is a delimiter (map constructor key separator).
      final int c = m_sSrc.codePointAt (m_nPos);
      if (c == '.' || isNCNameStartChar (c))
        throw _error ("Numeric literal '" + m_sSrc.substring (nStart, m_nPos) + "' must be followed by a delimiter",
                      nStart);
    }
    final ETokenKind eKind = bExp ? ETokenKind.DOUBLE : bDot ? ETokenKind.DECIMAL : ETokenKind.INTEGER;
    _push (eKind, m_sSrc.substring (nStart, m_nPos), nStart);
  }

  private void _star ()
  {
    final int nStart = m_nPos;
    // "*:local" (no whitespace allowed)
    if (_peek (1) == ':' && isNCNameStartChar (_codePointAt (m_nPos + 2)))
    {
      m_nPos += 2;
      final String sLocal = _readNCName ();
      _push (ETokenKind.LOCAL_WILDCARD, sLocal, nStart);
      return;
    }
    _emit (ETokenKind.STAR, "*");
  }

  private void _name ()
  {
    final int nStart = m_nPos;
    final String sFirst = _readNCName ();
    if (_peek (0) == ':')
    {
      final int c = _codePointAt (m_nPos + 1);
      if (isNCNameStartChar (c))
      {
        m_nPos++;
        final String sLocal = _readNCName ();
        _push (ETokenKind.NAME, sFirst + ":" + sLocal, nStart);
        return;
      }
      if (c == '*')
      {
        m_nPos += 2;
        _push (ETokenKind.PREFIX_WILDCARD, sFirst, nStart);
        return;
      }
    }
    _push (ETokenKind.NAME, sFirst, nStart);
  }

  private void _run ()
  {
    while (true)
    {
      while (m_nPos < m_nLen && _isWhitespace (m_sSrc.charAt (m_nPos)))
        m_nPos++;
      if (m_nPos >= m_nLen)
      {
        _push (ETokenKind.EOF, "", m_nPos);
        return;
      }
      final char c = m_sSrc.charAt (m_nPos);
      final int c1 = _peek (1);
      switch (c)
      {
        case '(':
          if (c1 == ':')
            _skipComment ();
          else
            _emit (ETokenKind.LPAREN, "(");
          break;
        case ')':
          _emit (ETokenKind.RPAREN, ")");
          break;
        case '[':
          _emit (ETokenKind.LBRACKET, "[");
          break;
        case ']':
          _emit (ETokenKind.RBRACKET, "]");
          break;
        case '{':
          _emit (ETokenKind.LBRACE, "{");
          break;
        case '}':
          _emit (ETokenKind.RBRACE, "}");
          break;
        case '@':
          _emit (ETokenKind.AT, "@");
          break;
        case ',':
          _emit (ETokenKind.COMMA, ",");
          break;
        case '|':
          if (c1 == '|')
            _emit (ETokenKind.CONCAT, "||");
          else
            _emit (ETokenKind.PIPE, "|");
          break;
        case '+':
          _emit (ETokenKind.PLUS, "+");
          break;
        case '-':
          _emit (ETokenKind.MINUS, "-");
          break;
        case '=':
          if (c1 == '>')
            _emit (ETokenKind.ARROW, "=>");
          else
            _emit (ETokenKind.EQ, "=");
          break;
        case '!':
          if (c1 == '=')
            _emit (ETokenKind.NE, "!=");
          else
            _emit (ETokenKind.BANG, "!");
          break;
        case '<':
          if (c1 == '=')
            _emit (ETokenKind.LE, "<=");
          else
            if (c1 == '<')
              _emit (ETokenKind.PRECEDES, "<<");
            else
              _emit (ETokenKind.LT, "<");
          break;
        case '>':
          if (c1 == '=')
            _emit (ETokenKind.GE, ">=");
          else
            if (c1 == '>')
              _emit (ETokenKind.FOLLOWS, ">>");
            else
              _emit (ETokenKind.GT, ">");
          break;
        case '/':
          if (c1 == '/')
            _emit (ETokenKind.SLASHSLASH, "//");
          else
            _emit (ETokenKind.SLASH, "/");
          break;
        case ':':
          if (c1 == ':')
            _emit (ETokenKind.COLONCOLON, "::");
          else
            if (c1 == '=')
              _emit (ETokenKind.ASSIGN, ":=");
            else
              _emit (ETokenKind.COLON, ":");
          break;
        case '$':
          _emit (ETokenKind.DOLLAR, "$");
          break;
        case '?':
          _emit (ETokenKind.QUESTION, "?");
          break;
        case '#':
          _emit (ETokenKind.HASH, "#");
          break;
        case '.':
          if (c1 == '.')
            _emit (ETokenKind.DOTDOT, "..");
          else
            if (_isDigit (c1))
              _number ();
            else
              _emit (ETokenKind.DOT, ".");
          break;
        case '*':
          _star ();
          break;
        case '\'':
        case '"':
          _literal (c);
          break;
        default:
          if (_isDigit (c))
            _number ();
          else
            if (c == 'Q' && c1 == '{')
              _bracedName ();
            else
            {
              final int cp = m_sSrc.codePointAt (m_nPos);
              if (!isNCNameStartChar (cp))
                throw _error ("Unexpected character '" + new String (Character.toChars (cp)) + "'", m_nPos);
              _name ();
            }
          break;
      }
    }
  }

  /**
   * Tokenize an expression.
   *
   * @param sSrc
   *        The expression text.
   * @return The tokens, always terminated by an {@link ETokenKind#EOF} token.
   * @throws XPathException
   *         XPST0003 for lexical errors.
   */
  @NonNull
  static ICommonsList <XPathToken> tokenize (@NonNull final String sSrc)
  {
    final XPathLexer aLexer = new XPathLexer (sSrc);
    aLexer._run ();
    return aLexer.m_aTokens;
  }
}
