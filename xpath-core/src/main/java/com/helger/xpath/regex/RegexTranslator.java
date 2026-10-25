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
package com.helger.xpath.regex;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Locale;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;

/**
 * Validates an XPath 3.1 regular expression (F&amp;O 3.1 section 5.6.1, based on
 * XML Schema Part 2 Appendix F) and translates it into the syntax of
 * {@link java.util.regex.Pattern}. Everything the XSD grammar forbids but Java
 * would accept is rejected with FORX0002. The translation never relies on Java
 * flags: character classes are emitted with explicit code point ranges and
 * Unicode general categories, the 'i' flag is applied by expanding literal
 * characters and ranges to their case variants, and the anchors are emitted
 * with the exact F&amp;O meaning (also under the 'm' flag).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class RegexTranslator
{
  /** Maximum nesting of parenthesised groups and character classes */
  static final int MAX_NESTING = 1_000;

  /** XML 1.0 (5th edition) NameStartChar as ranges */
  private static final int [] NAME_START = { ':',
                                             ':',
                                             'A',
                                             'Z',
                                             '_',
                                             '_',
                                             'a',
                                             'z',
                                             0xC0,
                                             0xD6,
                                             0xD8,
                                             0xF6,
                                             0xF8,
                                             0x2FF,
                                             0x370,
                                             0x37D,
                                             0x37F,
                                             0x1FFF,
                                             0x200C,
                                             0x200D,
                                             0x2070,
                                             0x218F,
                                             0x2C00,
                                             0x2FEF,
                                             0x3001,
                                             0xD7FF,
                                             0xF900,
                                             0xFDCF,
                                             0xFDF0,
                                             0xFFFD,
                                             0x10000,
                                             0xEFFFF };
  /** The characters a NameChar may have beyond a NameStartChar */
  private static final int [] NAME_EXTRA = { '-', '-', '.', '.', '0', '9', 0xB7, 0xB7, 0x300, 0x36F, 0x203F, 0x2040 };

  private static final String ALL_CHARS = "\\x{0}-\\x{10FFFF}";
  private static final String XML_SPACE = "\\x{20}\\x{9}\\x{A}\\x{D}";

  /**
   * The result of a translation.
   *
   * @param javaPattern
   *        The Java regular expression.
   * @param groupCount
   *        The number of capturing groups of the XPath expression.
   * @param groupParents
   *        Per XPath group (index 1..groupCount) the group it is nested in.
   * @param javaGroups
   *        Per XPath group (index 1..groupCount) the Java group number.
   */
  record Result (@NonNull String javaPattern,
                 int groupCount,
                 @NonNull int [] groupParents,
                 @NonNull int [] javaGroups)
  {}

  /** A parsed character class expression. */
  private static final class CharClass
  {
    boolean m_bNegated;
    // Literal characters and ranges as pairs - affected by the 'i' flag
    final ICommonsList <int []> m_aRanges = new CommonsArrayList <> ();
    // Java class syntax for escapes - not affected by the 'i' flag
    final StringBuilder m_aEscapes = new StringBuilder ();
    CharClass m_aSubtraction;
  }

  private final String m_sPattern;
  private final int [] m_aCP;
  private final boolean m_bCaseInsensitive;
  private final boolean m_bDotAll;
  private final boolean m_bMultiLine;
  private final boolean m_bXsd11Classes;
  private final StringBuilder m_aSB = new StringBuilder ();
  private int m_nPos;
  private final int m_nTotalGroups;
  private int m_nGroups;
  private final ICommonsList <Integer> m_aParents = new CommonsArrayList <> ();
  private final BitSet m_aClosed = new BitSet ();
  // The groups a back-reference refers to
  private final BitSet m_aReferenced = new BitSet ();
  // Groups that get an empty marker group after them (see _parseBackReference)
  private final BitSet m_aWithMarker;
  private int m_nJavaGroups;
  private final ICommonsList <Integer> m_aJavaGroups = new CommonsArrayList <> ();
  private final ICommonsList <Integer> m_aMarkerGroups = new CommonsArrayList <> ();

  private RegexTranslator (@NonNull final String sPattern,
                           final boolean bCaseInsensitive,
                           final boolean bDotAll,
                           final boolean bMultiLine,
                           final boolean bXsd11Classes,
                           @NonNull final BitSet aWithMarker)
  {
    m_sPattern = sPattern;
    m_aCP = sPattern.codePoints ().toArray ();
    m_bCaseInsensitive = bCaseInsensitive;
    m_bDotAll = bDotAll;
    m_bMultiLine = bMultiLine;
    m_bXsd11Classes = bXsd11Classes;
    m_nTotalGroups = _countGroups (m_aCP);
    m_aWithMarker = aWithMarker;
    m_aParents.add (Integer.valueOf (0));
    m_aJavaGroups.add (Integer.valueOf (0));
    m_aMarkerGroups.add (Integer.valueOf (0));
  }

  /**
   * Count the capturing groups: every '(' that is not escaped, not inside a
   * character class and not followed by '?'.
   */
  private static int _countGroups (@NonNull final int [] aCP)
  {
    int ret = 0;
    int nClassDepth = 0;
    for (int i = 0; i < aCP.length; ++i)
    {
      final int c = aCP[i];
      if (c == '\\')
        i++;
      else
        if (c == '[')
          nClassDepth++;
        else
          if (c == ']')
          {
            if (nClassDepth > 0)
              nClassDepth--;
          }
          else
            if (c == '(' && nClassDepth == 0 && !(i + 1 < aCP.length && aCP[i + 1] == '?'))
              ret++;
    }
    return ret;
  }

  @NonNull
  private XPathException _error (@NonNull final String sMessage)
  {
    return XPathException.of ("FORX0002", "Invalid regular expression '" + m_sPattern + "': " + sMessage);
  }

  private static void _appendHex (@NonNull final StringBuilder aSB, final int c)
  {
    aSB.append ("\\x{").append (Integer.toHexString (c).toUpperCase (Locale.ROOT)).append ('}');
  }

  /**
   * Append a code point range in Java class syntax.
   *
   * @param aSB
   *        The target.
   * @param nLow
   *        First code point.
   * @param nHigh
   *        Last code point.
   */
  static void appendRange (@NonNull final StringBuilder aSB, final int nLow, final int nHigh)
  {
    _appendHex (aSB, nLow);
    if (nHigh != nLow)
    {
      aSB.append ('-');
      _appendHex (aSB, nHigh);
    }
  }

  private static void _appendRanges (@NonNull final StringBuilder aSB, @NonNull final int [] aRanges)
  {
    for (int i = 0; i < aRanges.length; i += 2)
      appendRange (aSB, aRanges[i], aRanges[i + 1]);
  }

  /**
   * Sort and merge a list of ranges into a flat (low, high) array.
   */
  @NonNull
  private static int [] _normalise (@NonNull final ICommonsList <int []> aRanges)
  {
    final int [] [] aSorted = aRanges.toArray (new int [0] []);
    Arrays.sort (aSorted, (a, b) -> Integer.compare (a[0], b[0]));
    final int [] ret = new int [aSorted.length * 2];
    int n = 0;
    for (final int [] aRange : aSorted)
    {
      if (n > 0 && aRange[0] <= ret[n - 1] + 1)
      {
        if (aRange[1] > ret[n - 1])
          ret[n - 1] = aRange[1];
      }
      else
      {
        ret[n++] = aRange[0];
        ret[n++] = aRange[1];
      }
    }
    return Arrays.copyOf (ret, n);
  }

  /**
   * Add the case variants of all characters of the passed ranges ('i' flag).
   */
  @NonNull
  private static ICommonsList <int []> _addCaseVariants (@NonNull final ICommonsList <int []> aRanges)
  {
    final ICommonsList <int []> ret = new CommonsArrayList <> (aRanges);
    for (final int [] aRange : aRanges)
      for (int c = aRange[0]; c <= aRange[1]; ++c)
        for (final int v : CaseVariants.getVariants (c))
          if (v < aRange[0] || v > aRange[1])
            ret.add (new int [] { v, v });
    return ret;
  }

  /** Emit a character class as a complete bracketed Java class. */
  private void _emitClass (@NonNull final StringBuilder aSB, @NonNull final CharClass aClass)
  {
    final StringBuilder aBody = new StringBuilder ();
    if (aClass.m_aRanges.isNotEmpty ())
      _appendRanges (aBody,
                     _normalise (m_bCaseInsensitive ? _addCaseVariants (aClass.m_aRanges) : aClass.m_aRanges));
    aBody.append (aClass.m_aEscapes);

    final String sBase;
    if (aBody.isEmpty ())
      sBase = aClass.m_bNegated ? "[" + ALL_CHARS + "]" : "[^" + ALL_CHARS + "]";
    else
      sBase = "[" + (aClass.m_bNegated ? "^" : "") + aBody + "]";
    if (aClass.m_aSubtraction == null)
      aSB.append (sBase);
    else
    {
      aSB.append ('[').append (sBase).append ("&&[^");
      _emitClass (aSB, aClass.m_aSubtraction);
      aSB.append ("]]");
    }
  }

  /**
   * Parse the name of a <code>\p{...}</code> escape at the current position
   * (just after the 'p' or 'P') and append its Java class syntax.
   */
  private void _parseProperty (@NonNull final StringBuilder aSB, final boolean bNegated)
  {
    if (m_nPos >= m_aCP.length || m_aCP[m_nPos] != '{')
      throw _error ("\\" + (bNegated ? 'P' : 'p') + " requires a {...} property");
    final int nStart = m_nPos + 1;
    int nEnd = nStart;
    while (nEnd < m_aCP.length && m_aCP[nEnd] != '}')
      nEnd++;
    if (nEnd >= m_aCP.length)
      throw _error ("unclosed \\p{...}");
    final String sName = new String (m_aCP, nStart, nEnd - nStart);
    m_nPos = nEnd + 1;
    if (sName.startsWith ("Is"))
    {
      final String sBlock = sName.substring (2);
      if (sBlock.isEmpty ())
        throw _error ("\\p{Is}: missing block name");
      for (int i = 0; i < sBlock.length (); ++i)
      {
        final char c = sBlock.charAt (i);
        if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-'))
          throw _error ("\\p{" + sName + "}: invalid block name");
      }
      final int [] aRanges = UnicodeBlocks.getBlockRanges (sBlock);
      if (aRanges == null)
        throw _error ("\\p{" + sName + "}: unknown Unicode block");
      if (aRanges.length == 0)
      {
        // A surrogate block contains no character
        if (bNegated)
          aSB.append (ALL_CHARS);
        return;
      }
      if (bNegated)
        aSB.append ("[^");
      _appendRanges (aSB, aRanges);
      if (bNegated)
        aSB.append (']');
      return;
    }
    if (!UnicodeCategories.isCategory (sName))
      throw _error ("\\p{" + sName + "}: unknown category");
    aSB.append (UnicodeCategories.getClassExpression (sName, bNegated));
  }

  /**
   * @return The code point of a single character escape (the character after
   *         the backslash), or -1 if it is none.
   */
  private static int _getSingleCharEscape (final int c)
  {
    switch (c)
    {
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 't':
        return '\t';
      case '\\':
      case '|':
      case '.':
      case '?':
      case '*':
      case '+':
      case '(':
      case ')':
      case '{':
      case '}':
      case '-':
      case '[':
      case ']':
      case '^':
      case '$':
        return c;
      default:
        return -1;
    }
  }

  /**
   * Append the Java class syntax of a multi-character escape (\s, \d, \w, \i,
   * \c and their complements), usable inside a class.
   *
   * @return <code>false</code> if the character is no multi-character escape
   */
  private static boolean _appendMultiCharEscape (@NonNull final StringBuilder aSB, final int c)
  {
    switch (c)
    {
      case 's':
        aSB.append (XML_SPACE);
        return true;
      case 'S':
        aSB.append ("[^" + XML_SPACE + "]");
        return true;
      case 'd':
        aSB.append (UnicodeCategories.getClassExpression ("Nd", false));
        return true;
      case 'D':
        aSB.append (UnicodeCategories.getClassExpression ("Nd", true));
        return true;
      case 'w':
        aSB.append ("[^")
           .append (UnicodeCategories.getClassExpression ("P", false))
           .append (UnicodeCategories.getClassExpression ("Z", false))
           .append (UnicodeCategories.getClassExpression ("C", false))
           .append (']');
        return true;
      case 'W':
        aSB.append (UnicodeCategories.getClassExpression ("P", false))
           .append (UnicodeCategories.getClassExpression ("Z", false))
           .append (UnicodeCategories.getClassExpression ("C", false));
        return true;
      case 'i':
        _appendRanges (aSB, NAME_START);
        return true;
      case 'I':
        aSB.append ("[^");
        _appendRanges (aSB, NAME_START);
        aSB.append (']');
        return true;
      case 'c':
        _appendRanges (aSB, NAME_START);
        _appendRanges (aSB, NAME_EXTRA);
        return true;
      case 'C':
        aSB.append ("[^");
        _appendRanges (aSB, NAME_START);
        _appendRanges (aSB, NAME_EXTRA);
        aSB.append (']');
        return true;
      default:
        return false;
    }
  }

  /**
   * Parse one atom of a character class at the current position: a character
   * or an escape.
   *
   * @param aClass
   *        The class a multi-character escape is added to.
   * @return The code point of a single character (usable as range endpoint)
   *         or -1 if a multi-character escape was added to the class.
   */
  private int _parseClassAtom (@NonNull final CharClass aClass)
  {
    final int c = m_aCP[m_nPos];
    if (c != '\\')
    {
      m_nPos++;
      return c;
    }
    if (m_nPos + 1 >= m_aCP.length)
      throw _error ("trailing backslash");
    final int n = m_aCP[m_nPos + 1];
    m_nPos += 2;
    final int nSingle = _getSingleCharEscape (n);
    if (nSingle >= 0)
      return nSingle;
    if (n == 'p' || n == 'P')
    {
      _parseProperty (aClass.m_aEscapes, n == 'P');
      return -1;
    }
    if (_appendMultiCharEscape (aClass.m_aEscapes, n))
      return -1;
    if (n >= '0' && n <= '9')
      throw _error ("back-references are not allowed in a character class");
    throw _error ("\\" + new String (Character.toChars (n)) + " is not a valid escape");
  }

  /**
   * Parse a character class expression; the current position is at its '['.
   * By default the XSD 1.0 character group grammar applies, which F&amp;O 3.1
   * references: a '-' directly after a range is only allowed as the last
   * character of the class or before a subtraction ("[a-c-1-4]" and "[0-9-.]"
   * are invalid). The XSD 1.1 grammar reads such a '-' as a literal.
   */
  @NonNull
  private CharClass _parseClass (final int nDepth)
  {
    if (nDepth > MAX_NESTING)
      throw XPathException.of ("XPDY0130", "Character class subtraction is nested too deeply");
    final CharClass ret = new CharClass ();
    m_nPos++;
    if (m_nPos < m_aCP.length && m_aCP[m_nPos] == '^')
    {
      ret.m_bNegated = true;
      m_nPos++;
    }
    boolean bFirst = true;
    boolean bPrevRange = false;
    while (true)
    {
      if (m_nPos >= m_aCP.length)
        throw _error ("unclosed character class");
      final int c = m_aCP[m_nPos];
      if (c == ']')
      {
        if (bFirst)
          throw _error ("empty character class");
        m_nPos++;
        return ret;
      }
      if (c == '[')
        throw _error ("unescaped '[' in a character class");
      if (c == '-')
      {
        if (m_nPos + 1 < m_aCP.length && m_aCP[m_nPos + 1] == '[')
        {
          // Class subtraction - must be the last part
          if (bFirst)
            throw _error ("class subtraction requires a non-empty base");
          m_nPos++;
          ret.m_aSubtraction = _parseClass (nDepth + 1);
          if (m_nPos >= m_aCP.length || m_aCP[m_nPos] != ']')
            throw _error ("class subtraction must be the last part of a character class");
          m_nPos++;
          return ret;
        }
        // A literal '-'
        if (!m_bXsd11Classes && bPrevRange)
        {
          final boolean bTrailing = m_nPos + 1 < m_aCP.length && m_aCP[m_nPos + 1] == ']';
          final boolean bBeforeSubtraction = m_nPos + 2 < m_aCP.length &&
                                             m_aCP[m_nPos + 1] == '-' &&
                                             m_aCP[m_nPos + 2] == '[';
          if (!bTrailing && !bBeforeSubtraction)
            throw _error ("'-' directly after a range");
        }
        // It may not be the left endpoint of a range
        if (m_nPos + 2 < m_aCP.length &&
            m_aCP[m_nPos + 1] == '-' &&
            m_aCP[m_nPos + 2] != '[' &&
            m_aCP[m_nPos + 2] != ']')
          throw _error ("unescaped '-' as range endpoint");
        ret.m_aRanges.add (new int [] { '-', '-' });
        m_nPos++;
        bFirst = false;
        bPrevRange = false;
        continue;
      }
      final int nLow = _parseClassAtom (ret);
      bFirst = false;
      if (m_nPos < m_aCP.length &&
          m_aCP[m_nPos] == '-' &&
          !(m_nPos + 1 < m_aCP.length && (m_aCP[m_nPos + 1] == '[' || m_aCP[m_nPos + 1] == ']')))
      {
        // A range
        if (m_nPos + 1 >= m_aCP.length)
          throw _error ("unclosed character class");
        if (m_aCP[m_nPos + 1] == '-')
          throw _error ("unescaped '-' as range endpoint");
        m_nPos++;
        final int nHigh = _parseClassAtom (ret);
        if (nLow < 0 || nHigh < 0)
          throw _error ("a multi-character escape cannot be a range endpoint");
        if (nLow > nHigh)
          throw _error ("reversed character range");
        ret.m_aRanges.add (new int [] { nLow, nHigh });
        bPrevRange = true;
        continue;
      }
      bPrevRange = false;
      if (nLow >= 0)
        ret.m_aRanges.add (new int [] { nLow, nLow });
    }
  }

  /** Emit a literal character outside a class. */
  private void _emitLiteral (final int c)
  {
    if (m_bCaseInsensitive)
    {
      final int [] aVariants = CaseVariants.getVariants (c);
      if (aVariants.length > 1)
      {
        m_aSB.append ('[');
        for (final int v : aVariants)
          _appendHex (m_aSB, v);
        m_aSB.append (']');
        return;
      }
    }
    if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9')
      m_aSB.appendCodePoint (c);
    else
      _appendHex (m_aSB, c);
  }

  private static boolean _isDigits (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    for (int i = 0; i < s.length (); ++i)
      if (s.charAt (i) < '0' || s.charAt (i) > '9')
        return false;
    return true;
  }

  @NonNull
  private static String _stripZeros (@NonNull final String s)
  {
    int i = 0;
    while (i < s.length () - 1 && s.charAt (i) == '0')
      i++;
    return s.substring (i);
  }

  /**
   * Java counts repetitions in an int; a count that does not fit can never be
   * reached by a Java string anyway.
   */
  @NonNull
  private static String _clamp (@NonNull final String sDigits)
  {
    final String sMax = Integer.toString (Integer.MAX_VALUE - 1);
    if (sDigits.length () > sMax.length () ||
        (sDigits.length () == sMax.length () && sDigits.compareTo (sMax) > 0))
      return sMax;
    return sDigits;
  }

  /**
   * Parse a quantifier body after '{' up to and including '}'.
   */
  private void _parseBraces ()
  {
    final int nStart = m_nPos + 1;
    int nEnd = nStart;
    while (nEnd < m_aCP.length && m_aCP[nEnd] != '}')
      nEnd++;
    if (nEnd >= m_aCP.length)
      throw _error ("unescaped '{'");
    final String sBody = new String (m_aCP, nStart, nEnd - nStart);
    final int nComma = sBody.indexOf (',');
    final String sMin = nComma < 0 ? sBody : sBody.substring (0, nComma);
    final String sMax = nComma < 0 ? null : sBody.substring (nComma + 1);
    if (!_isDigits (sMin) || (sMax != null && !sMax.isEmpty () && !_isDigits (sMax)))
      throw _error ("malformed quantifier {" + sBody + "}");
    final String sMinNorm = _stripZeros (sMin);
    if (sMax != null && !sMax.isEmpty ())
    {
      final String sMaxNorm = _stripZeros (sMax);
      if (sMinNorm.length () > sMaxNorm.length () ||
          (sMinNorm.length () == sMaxNorm.length () && sMinNorm.compareTo (sMaxNorm) > 0))
        throw _error ("quantifier minimum exceeds maximum");
    }
    m_aSB.append ('{').append (_clamp (sMinNorm));
    if (sMax != null)
    {
      m_aSB.append (',');
      if (!sMax.isEmpty ())
        m_aSB.append (_clamp (_stripZeros (sMax)));
    }
    m_aSB.append ('}');
    m_nPos = nEnd + 1;
  }

  /** Parse a back-reference; the current position is at the first digit. */
  private void _parseBackReference ()
  {
    int nEnd = m_nPos;
    while (nEnd < m_aCP.length && m_aCP[nEnd] >= '0' && m_aCP[nEnd] <= '9')
      nEnd++;
    // The longest digit sequence that names an existing group
    int nLen = nEnd - m_nPos;
    int nGroup = -1;
    while (nLen > 0)
    {
      final String sDigits = new String (m_aCP, m_nPos, nLen);
      if (sDigits.length () <= 9)
      {
        final int n = Integer.parseInt (sDigits);
        // A further digit joins the reference only if the group's opening
        // parenthesis precedes it (F&O 3.1 section 5.6.1.4)
        if (n <= m_nTotalGroups && (nLen == 1 || n <= m_nGroups))
        {
          nGroup = n;
          break;
        }
      }
      if (nLen == 1)
        break;
      nLen--;
    }
    if (nGroup < 1)
      throw _error ("back-reference to a non-existing group");
    if (!m_aClosed.get (nGroup))
      throw _error ("back-reference \\" + nGroup + " to a group that is not closed before it");
    m_nPos += nLen;
    m_aReferenced.set (nGroup);
    final int nJavaGroup = m_aJavaGroups.get (nGroup).intValue ();
    final int nMarker = m_aMarkerGroups.get (nGroup).intValue ();
    if (nMarker > 0)
    {
      // A back-reference to a group that did not take part in the match
      // matches the empty string (F&O 3.1 section 5.6.1), whereas Java fails.
      // The empty marker group after the group is set exactly when the group
      // is, so "(?!\marker)" tests for a group that is not set.
      m_aSB.append ("(?:")
           .append (m_bCaseInsensitive ? "(?iu:\\" : "(?:\\")
           .append (nJavaGroup)
           .append (")|(?!\\")
           .append (nMarker)
           .append ("))");
    }
    else
      m_aSB.append (m_bCaseInsensitive ? "(?iu:\\" : "(?:\\").append (nJavaGroup).append (')');
  }

  @NonNull
  private Result _translate ()
  {
    // Stack of open groups: the capturing group number, or 0 for (?:...)
    final ICommonsList <Integer> aOpen = new CommonsArrayList <> ();
    boolean bPrevAtom = false;
    boolean bPrevQuant = false;
    while (m_nPos < m_aCP.length)
    {
      final int c = m_aCP[m_nPos];
      switch (c)
      {
        case '\\':
        {
          if (m_nPos + 1 >= m_aCP.length)
            throw _error ("trailing backslash");
          final int n = m_aCP[m_nPos + 1];
          if (n >= '1' && n <= '9')
          {
            m_nPos++;
            _parseBackReference ();
          }
          else
          {
            m_nPos += 2;
            final int nSingle = _getSingleCharEscape (n);
            if (nSingle >= 0)
              _emitLiteral (nSingle);
            else
              if (n == 'p' || n == 'P')
              {
                m_aSB.append ('[');
                final int nLen = m_aSB.length ();
                _parseProperty (m_aSB, n == 'P');
                if (m_aSB.length () == nLen)
                  m_aSB.append ("^" + ALL_CHARS);
                m_aSB.append (']');
              }
              else
              {
                m_aSB.append ('[');
                if (!_appendMultiCharEscape (m_aSB, n))
                {
                  if (n == '0')
                    throw _error ("\\0 is not a valid escape");
                  throw _error ("\\" + new String (Character.toChars (n)) + " is not a valid escape");
                }
                m_aSB.append (']');
              }
          }
          bPrevAtom = true;
          bPrevQuant = false;
          break;
        }
        case '[':
          _emitClass (m_aSB, _parseClass (0));
          bPrevAtom = true;
          bPrevQuant = false;
          break;
        case ']':
          throw _error ("unescaped ']' outside a character class");
        case '}':
          throw _error ("unescaped '}'");
        case '*':
        case '+':
          if (bPrevQuant)
            throw _error ("double quantifier");
          if (!bPrevAtom)
            throw _error ("quantifier with no preceding atom");
          m_aSB.appendCodePoint (c);
          m_nPos++;
          bPrevQuant = true;
          break;
        case '?':
          if (bPrevQuant)
          {
            // Reluctant quantifier; the piece is complete
            m_aSB.append ('?');
            m_nPos++;
            bPrevQuant = false;
            bPrevAtom = false;
            break;
          }
          if (!bPrevAtom)
            throw _error ("quantifier with no preceding atom");
          m_aSB.append ('?');
          m_nPos++;
          bPrevQuant = true;
          break;
        case '{':
          if (bPrevQuant)
            throw _error ("double quantifier");
          if (!bPrevAtom)
            throw _error ("quantifier with no preceding atom");
          _parseBraces ();
          bPrevQuant = true;
          break;
        case '(':
        {
          if (aOpen.size () >= MAX_NESTING)
            throw XPathException.of ("XPDY0130", "Regular expression groups are nested too deeply");
          if (m_nPos + 1 < m_aCP.length && m_aCP[m_nPos + 1] == '?')
          {
            if (m_nPos + 2 < m_aCP.length && m_aCP[m_nPos + 2] == ':')
            {
              aOpen.add (Integer.valueOf (0));
              m_aSB.append ("(?:");
              m_nPos += 3;
            }
            else
              throw _error ("(?...) group syntax other than (?:...) is not allowed");
          }
          else
          {
            int nEnclosing = 0;
            for (int i = aOpen.size () - 1; i >= 0; --i)
              if (aOpen.get (i).intValue () != 0)
              {
                nEnclosing = aOpen.get (i).intValue ();
                break;
              }
            m_nGroups++;
            m_aParents.add (Integer.valueOf (nEnclosing));
            aOpen.add (Integer.valueOf (m_nGroups));
            if (m_aWithMarker.get (m_nGroups))
              m_aSB.append ("(?:");
            m_aSB.append ('(');
            m_nJavaGroups++;
            m_aJavaGroups.add (Integer.valueOf (m_nJavaGroups));
            m_aMarkerGroups.add (Integer.valueOf (0));
            m_nPos++;
          }
          bPrevAtom = false;
          bPrevQuant = false;
          break;
        }
        case ')':
        {
          if (aOpen.isEmpty ())
            throw _error ("unbalanced ')'");
          final int nGroup = aOpen.removeLast ().intValue ();
          if (nGroup > 0)
            m_aClosed.set (nGroup);
          m_aSB.append (')');
          if (nGroup > 0 && m_aWithMarker.get (nGroup))
          {
            m_aSB.append ("())");
            m_nJavaGroups++;
            m_aMarkerGroups.set (nGroup, Integer.valueOf (m_nJavaGroups));
          }
          m_nPos++;
          bPrevAtom = true;
          bPrevQuant = false;
          break;
        }
        case '|':
          m_aSB.append ('|');
          m_nPos++;
          bPrevAtom = false;
          bPrevQuant = false;
          break;
        case '.':
          m_aSB.append (m_bDotAll ? "[" + ALL_CHARS + "]" : "[^\\x{A}\\x{D}]");
          m_nPos++;
          bPrevAtom = true;
          bPrevQuant = false;
          break;
        case '^':
          // F&O: with 'm', also after a newline that is not the last character
          m_aSB.append (m_bMultiLine ? "(?:\\A|(?<=\\x{A})(?!\\z))" : "(?:\\A)");
          m_nPos++;
          bPrevAtom = true;
          bPrevQuant = false;
          break;
        case '$':
          // F&O: with 'm', before every newline, and at the end only if the
          // string does not end with a newline
          m_aSB.append (m_bMultiLine ? "(?:(?=\\x{A})|\\z(?<!\\x{A}))" : "(?:\\z)");
          m_nPos++;
          bPrevAtom = true;
          bPrevQuant = false;
          break;
        default:
          _emitLiteral (c);
          m_nPos++;
          bPrevAtom = true;
          bPrevQuant = false;
          break;
      }
    }
    if (aOpen.isNotEmpty ())
      throw _error ("unterminated group");
    final int [] aParents = new int [m_aParents.size ()];
    final int [] aJavaGroups = new int [m_aJavaGroups.size ()];
    for (int i = 0; i < aParents.length; ++i)
    {
      aParents[i] = m_aParents.get (i).intValue ();
      aJavaGroups[i] = m_aJavaGroups.get (i).intValue ();
    }
    return new Result (m_aSB.toString (), m_nGroups, aParents, aJavaGroups);
  }

  /**
   * Remove the whitespace the 'x' flag ignores: #x9, #xA, #xD and #x20 outside
   * character classes. This is a textual pass: F&amp;O's own example turns
   * <code>hello\ sworld</code> into <code>hello\sworld</code>, so a backslash
   * does not protect a following whitespace character.
   *
   * @param sPattern
   *        Pattern
   * @return The stripped pattern
   */
  @NonNull
  static String stripWhitespace (@NonNull final String sPattern)
  {
    final StringBuilder aSB = new StringBuilder (sPattern.length ());
    boolean bInClass = false;
    boolean bEscaped = false;
    for (int i = 0; i < sPattern.length (); ++i)
    {
      final char c = sPattern.charAt (i);
      final boolean bWS = c == ' ' || c == '\t' || c == '\n' || c == '\r';
      if (bEscaped)
      {
        bEscaped = false;
        if (!bInClass && bWS)
          continue;
        aSB.append (c);
        continue;
      }
      if (c == '\\')
      {
        bEscaped = true;
        aSB.append (c);
        continue;
      }
      if (c == '[')
        bInClass = true;
      else
        if (c == ']')
          bInClass = false;
      if (!bInClass && bWS)
        continue;
      aSB.append (c);
    }
    return aSB.toString ();
  }

  /**
   * Validate and translate an XPath regular expression.
   *
   * @param sPattern
   *        The XPath regular expression (after 'x' processing).
   * @param bCaseInsensitive
   *        'i' flag
   * @param bDotAll
   *        's' flag
   * @param bMultiLine
   *        'm' flag
   * @param bXsd11Classes
   *        <code>true</code> to use the XSD 1.1 character class grammar
   *        instead of the XSD 1.0 one.
   * @return The translation.
   * @throws XPathException
   *         FORX0002 if the expression is invalid
   */
  @NonNull
  static Result translate (@NonNull final String sPattern,
                           final boolean bCaseInsensitive,
                           final boolean bDotAll,
                           final boolean bMultiLine,
                           final boolean bXsd11Classes)
  {
    final RegexTranslator aFirst = new RegexTranslator (sPattern,
                                                        bCaseInsensitive,
                                                        bDotAll,
                                                        bMultiLine,
                                                        bXsd11Classes,
                                                        new BitSet ());
    final Result ret = aFirst._translate ();
    if (aFirst.m_aReferenced.isEmpty ())
      return ret;
    // Again, with marker groups after the groups that are referenced
    return new RegexTranslator (sPattern,
                                bCaseInsensitive,
                                bDotAll,
                                bMultiLine,
                                bXsd11Classes,
                                aFirst.m_aReferenced)._translate ();
  }

  /**
   * Translate a string to be matched literally ('q' flag).
   *
   * @param sLiteral
   *        The string.
   * @param bCaseInsensitive
   *        'i' flag
   * @return The translation, without groups.
   */
  @NonNull
  static Result translateLiteral (@NonNull final String sLiteral, final boolean bCaseInsensitive)
  {
    final RegexTranslator aTranslator = new RegexTranslator ("", bCaseInsensitive, false, false, false, new BitSet ());
    sLiteral.codePoints ().forEach (aTranslator::_emitLiteral);
    return new Result (aTranslator.m_aSB.toString (), 0, new int [] { 0 }, new int [] { 0 });
  }
}
