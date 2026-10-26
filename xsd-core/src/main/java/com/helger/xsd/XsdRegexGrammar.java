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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.regex.XPathRegex;

/**
 * The XSD pattern-facet grammar check (go-xslt <code>ValidateRegexGrammar</code>
 * for <code>GrammarXSD10</code> and <code>GrammarXSD11</code>, in
 * <code>internal/xpath/regex_grammar.go</code>): it flags the constructs the
 * regular expression engine accepts but XML Schema Part 2 Appendix F (1.0) or G
 * (1.1) forbids, and only unambiguously invalid ones. The scan is iterative
 * apart from the class-subtraction nesting, which is bounded.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdRegexGrammar
{
  /** The deepest nesting of class subtractions accepted. */
  private static final int MAX_CLASS_NESTING = 1_000;

  /** The closed vocabulary of catEsc general categories (Appendix F charProp). */
  private static final ICommonsSet <String> CATEGORIES = new CommonsHashSet <> ("L",
                                                                              "Lu",
                                                                              "Ll",
                                                                              "Lt",
                                                                              "Lm",
                                                                              "Lo",
                                                                              "M",
                                                                              "Mn",
                                                                              "Mc",
                                                                              "Me",
                                                                              "N",
                                                                              "Nd",
                                                                              "Nl",
                                                                              "No",
                                                                              "P",
                                                                              "Pc",
                                                                              "Pd",
                                                                              "Ps",
                                                                              "Pe",
                                                                              "Pi",
                                                                              "Pf",
                                                                              "Po",
                                                                              "Z",
                                                                              "Zs",
                                                                              "Zl",
                                                                              "Zp",
                                                                              "S",
                                                                              "Sm",
                                                                              "Sc",
                                                                              "Sk",
                                                                              "So",
                                                                              "C",
                                                                              "Cc",
                                                                              "Cf",
                                                                              "Co",
                                                                              "Cn");

  @PresentForCodeCoverage
  private static final XsdRegexGrammar INSTANCE = new XsdRegexGrammar ();

  private XsdRegexGrammar ()
  {}

  /** An error of the grammar check, turned into the invalid verdict on top. */
  private static final class GrammarError extends RuntimeException
  {
    GrammarError (@NonNull final String sMessage)
    {
      super (sMessage, null, false, false);
    }
  }

  /**
   * @param sName
   *        A block name without the "Is" prefix.
   * @return <code>true</code> if the regular expression engine knows the
   *         Unicode block (go-xslt <code>XSDBlockKnown</code>).
   */
  static boolean isBlockKnown (@NonNull final String sName)
  {
    try
    {
      XPathRegex.compile ("\\p{Is" + sName + "}", "", true);
      return true;
    }
    catch (final XPathException ex)
    {
      return false;
    }
  }

  private static boolean _isDigit (final int c)
  {
    return c >= '0' && c <= '9';
  }

  private static boolean _allDigits (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    for (int i = 0; i < s.length (); i++)
      if (!_isDigit (s.charAt (i)))
        return false;
    return true;
  }

  /**
   * @return <code>true</code> if s is the body of a well-formed quantifier:
   *         "n", "n," or "n,m" with n, m sequences of digits and n not above m.
   */
  private static boolean _validQuantBody (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    final int nComma = s.indexOf (',');
    final String sMin = nComma < 0 ? s : s.substring (0, nComma);
    if (!_allDigits (sMin))
      return false;
    if (nComma >= 0 && nComma < s.length () - 1)
    {
      final String sMax = s.substring (nComma + 1);
      if (!_allDigits (sMax))
        return false;
      // Compared as unbounded naturals: strip leading zeros, longer wins,
      // else lexicographic
      final String n = _trimLeftZeros (sMin);
      final String m = _trimLeftZeros (sMax);
      if (n.length () > m.length () || (n.length () == m.length () && n.compareTo (m) > 0))
        return false;
    }
    return true;
  }

  @NonNull
  private static String _trimLeftZeros (@NonNull final String s)
  {
    int i = 0;
    while (i < s.length () && s.charAt (i) == '0')
      i++;
    return s.substring (i);
  }

  @NonNull
  private static String _slice (final int [] a, final int nFrom, final int nTo)
  {
    return new String (a, nFrom, nTo - nFrom);
  }

  private static void _validCharProp (@NonNull final String sBody, final boolean bXsd11)
  {
    if (sBody.startsWith ("Is"))
    {
      final String sName = sBody.substring (2);
      if (sName.isEmpty ())
        throw new GrammarError ("\\p{Is}: missing block name");
      for (int i = 0; i < sName.length (); i++)
      {
        final char ch = sName.charAt (i);
        if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-'))
          throw new GrammarError ("\\p{" + sBody + "}: invalid block name");
      }
      // An unknown block name is an error in XSD 1.0; 1.1 tolerates it, the
      // block then matches every character
      if (!bXsd11 && !isBlockKnown (sName))
        throw new GrammarError ("\\p{" + sBody + "}: unknown block");
      return;
    }
    if (!CATEGORIES.contains (sBody))
      throw new GrammarError ("\\p{" + sBody + "}: unknown category");
  }

  /**
   * @return The index just past the atom starting at a[j]: a single character
   *         or a backslash escape (including \p{...}).
   */
  private static int _classAtomEnd (final int [] a, final int j, final boolean bXsd11)
  {
    if (a[j] != '\\')
      return j + 1;
    if (j + 1 >= a.length)
      throw new GrammarError ("trailing backslash");
    final int n = a[j + 1];
    if (n >= '0' && n <= '9')
      throw new GrammarError ("back-reference/octal escape not allowed in XSD");
    if ((n == 'p' || n == 'P') && j + 2 < a.length && a[j + 2] == '{')
    {
      int k = j + 3;
      while (k < a.length && a[k] != '}')
        k++;
      if (k >= a.length)
        throw new GrammarError ("unclosed \\p{...}");
      _validCharProp (_slice (a, j + 3, k), bXsd11);
      return k + 1;
    }
    // The XSD escape repertoire is closed: SingleCharEsc, MultiCharEsc and
    // catEsc
    switch (n)
    {
      case 'n':
      case 'r':
      case 't':
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
      case 's':
      case 'S':
      case 'i':
      case 'I':
      case 'c':
      case 'C':
      case 'd':
      case 'D':
      case 'w':
      case 'W':
        break;
      case 'p':
      case 'P':
        throw new GrammarError ("\\" + (char) n + " requires a {...} property");
      case '$':
        // '$' is a NormalChar in XSD, so "\$" is not a SingleCharEsc there
        throw new GrammarError ("\\$ is not a valid XSD escape");
      default:
        throw new GrammarError ("\\" + new String (Character.toChars (n)) + " is not a valid XSD escape");
    }
    return j + 2;
  }

  /**
   * @return The single character a range endpoint denotes, or -1 for a
   *         multi-character escape (a set).
   */
  private static int _rangeEndpoint (final int [] a, final int j, final int nEnd)
  {
    if (a[j] != '\\')
      return a[j];
    if (nEnd - j != 2)
      return -1;
    final int n = a[j + 1];
    switch (n)
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
        return n;
      default:
        return -1;
    }
  }

  /**
   * Validate the character class expression starting at a[nStart] == '[' and
   * return the index of its closing ']'.
   */
  private static int _validateCharClass (final int [] a, final int nStart, final boolean bXsd11, final int nDepth)
  {
    if (nDepth > MAX_CLASS_NESTING)
      throw new XsdUnsupportedException ("Character class subtractions nested more than " + MAX_CLASS_NESTING + " levels");
    int j = nStart + 1;
    if (j < a.length && a[j] == '^')
      j++;
    boolean bFirst = true;
    boolean bPrevRange = false;
    while (j < a.length)
    {
      final int c = a[j];
      if (c == ']')
      {
        if (bFirst)
          throw new GrammarError ("empty character class");
        return j;
      }
      if (c == '[')
        throw new GrammarError ("unescaped '[' in character class");
      if (c == '-')
      {
        if (j + 1 < a.length && a[j + 1] == '[')
        {
          // Class subtraction, must be last
          if (bFirst)
            throw new GrammarError ("class subtraction requires a non-empty base");
          final int nEnd = _validateCharClass (a, j + 1, bXsd11, nDepth + 1);
          if (nEnd + 1 >= a.length || a[nEnd + 1] != ']')
            throw new GrammarError ("class subtraction must be last");
          return nEnd + 1;
        }
        // A bare '-' right after a completed range is a legal literal under
        // strict XSD 1.0 only as the class's trailing hyphen or the base
        // group's trailing literal before a subtraction; XSD 1.1 reads it as a
        // literal either way
        final boolean bTrailingHyphen = j + 1 < a.length && a[j + 1] == ']';
        final boolean bSubtractionLeadIn = j + 2 < a.length && a[j + 1] == '-' && a[j + 2] == '[';
        if (!bXsd11 && bPrevRange && !bTrailingHyphen && !bSubtractionLeadIn)
          throw new GrammarError ("chained range in character class");
        // An unescaped '-' may not be a range's left endpoint (both versions)
        if (j + 2 < a.length && a[j + 1] == '-' && a[j + 2] != '[' && a[j + 2] != ']')
          throw new GrammarError ("unescaped '-' as range endpoint");
        j++;
        bFirst = false;
        bPrevRange = false;
        continue;
      }
      final int nAEnd = _classAtomEnd (a, j, bXsd11);
      bFirst = false;
      if (nAEnd < a.length && a[nAEnd] == '-' && !(nAEnd + 1 < a.length && (a[nAEnd + 1] == '[' || a[nAEnd + 1] == ']')))
      {
        if (nAEnd + 1 >= a.length)
          throw new GrammarError ("unclosed character class");
        // Nor may an unescaped '-' be the right endpoint
        if (a[nAEnd + 1] == '-')
          throw new GrammarError ("unescaped '-' as range endpoint");
        // A range: atom '-' atom
        final int nBEnd = _classAtomEnd (a, nAEnd + 1, bXsd11);
        // Each endpoint must denote a single character, and the range may not
        // be reversed
        final int nLo = _rangeEndpoint (a, j, nAEnd);
        final int nHi = _rangeEndpoint (a, nAEnd + 1, nBEnd);
        if (nLo < 0 || nHi < 0)
          throw new GrammarError ("a multi-character escape cannot be a range endpoint");
        if (nLo > nHi)
          throw new GrammarError ("reversed character range");
        j = nBEnd;
        bPrevRange = true;
        continue;
      }
      j = nAEnd;
      bPrevRange = false;
    }
    throw new GrammarError ("unclosed character class");
  }

  private static void _validate (@NonNull final String sPattern, final boolean bXsd11)
  {
    final int [] a = sPattern.codePoints ().toArray ();
    boolean bPrevQuant = false;
    boolean bPrevAtom = false;
    int nDepth = 0;
    for (int i = 0; i < a.length; i++)
    {
      final int c = a[i];
      if (c == '\\')
      {
        final int nEnd = _classAtomEnd (a, i, bXsd11);
        i = nEnd - 1;
        bPrevQuant = false;
        bPrevAtom = true;
        continue;
      }
      switch (c)
      {
        case '[':
          i = _validateCharClass (a, i, bXsd11, 0);
          bPrevQuant = false;
          bPrevAtom = true;
          break;
        case ']':
          throw new GrammarError ("unescaped ']' outside a character class");
        case '*':
        case '+':
          // piece ::= atom quantifier?: a quantifier needs a preceding atom
          // and at most one may appear
          if (bPrevQuant)
            throw new GrammarError ("double quantifier");
          if (!bPrevAtom)
            throw new GrammarError ("quantifier with no preceding atom");
          bPrevQuant = true;
          break;
        case '?':
          if (bPrevQuant)
            throw new GrammarError ("reluctant quantifiers are not allowed in XSD");
          if (!bPrevAtom)
            throw new GrammarError ("quantifier with no preceding atom");
          bPrevQuant = true;
          break;
        case '{':
        {
          int j = i + 1;
          while (j < a.length && a[j] != '}')
            j++;
          if (j >= a.length || !_validQuantBody (_slice (a, i + 1, j)))
            throw new GrammarError ("malformed quantifier");
          if (bPrevQuant)
            throw new GrammarError ("double quantifier");
          if (!bPrevAtom)
            throw new GrammarError ("quantifier with no preceding atom");
          i = j;
          bPrevQuant = true;
          break;
        }
        case '(':
          if (i + 1 < a.length && a[i + 1] == '?')
            throw new GrammarError ("(?...) group syntax is not allowed in XSD");
          nDepth++;
          bPrevQuant = false;
          bPrevAtom = false;
          break;
        case ')':
          // An unmatched ')' is not a NormalChar
          if (nDepth == 0)
            throw new GrammarError ("unbalanced ')'");
          nDepth--;
          bPrevQuant = false;
          bPrevAtom = true;
          break;
        case '|':
          // A branch may be empty, so '|' only resets the piece state
          bPrevQuant = false;
          bPrevAtom = false;
          break;
        default:
          bPrevQuant = false;
          bPrevAtom = true;
          break;
      }
    }
    if (nDepth > 0)
      throw new GrammarError ("unterminated group");
  }

  /**
   * @param sPattern
   *        The pattern.
   * @param bXsd11
   *        <code>true</code> for the XSD 1.1 grammar.
   * @throws XsdInvalidException
   *         if the pattern is not in the grammar
   */
  static void validate (@NonNull final String sPattern, final boolean bXsd11)
  {
    try
    {
      _validate (sPattern, bXsd11);
    }
    catch (final GrammarError ex)
    {
      throw XsdCompiler.invalid ("", "invalid pattern " + XsdLexHelper.quote (sPattern) + ": " + ex.getMessage ());
    }
  }
}
