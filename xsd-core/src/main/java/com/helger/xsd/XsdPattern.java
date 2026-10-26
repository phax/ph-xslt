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

import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.regex.XPathRegex;

/**
 * A compiled XSD pattern facet (regular expression of XML Schema Part 2,
 * Appendix F; go-xslt <code>compileXSDPattern</code> /
 * <code>validateXSDRegex</code>). The grammar check is
 * {@link XsdRegexGrammar}; the matcher is the XPath regular expression
 * translator of xpath-core, fed with the pattern after the XSD adjustments.
 *
 * @author Philip Helger
 */
final class XsdPattern
{
  private final String m_sSource;
  private final Pattern m_aPattern;

  private XsdPattern (@NonNull final String sSource, @NonNull final Pattern aPattern)
  {
    m_sSource = sSource;
    m_aPattern = aPattern;
  }

  /**
   * Replace the top-level <code>\p{IsX}</code> and <code>\P{IsX}</code> atoms
   * naming a block the engine does not know by a match-anything atom, as XSD
   * 1.1 reads an unrecognised block name (go-xslt
   * <code>rewriteUnknownBlocks</code>).
   */
  @NonNull
  private static String _rewriteUnknownBlocks (@NonNull final String sPattern)
  {
    final int [] a = sPattern.codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder (sPattern.length ());
    boolean bInClass = false;
    for (int i = 0; i < a.length; i++)
    {
      final int r = a[i];
      if (r == '\\' && i + 1 < a.length)
      {
        final int n = a[i + 1];
        if ((n == 'p' || n == 'P') && !bInClass && i + 2 < a.length && a[i + 2] == '{')
        {
          int j = i + 3;
          while (j < a.length && a[j] != '}')
            j++;
          if (j < a.length)
          {
            final String sBody = new String (a, i + 3, j - (i + 3));
            if (sBody.startsWith ("Is") && !XsdRegexGrammar.isBlockKnown (sBody.substring (2)))
            {
              // XSD-syntax match-anything atom
              aSB.append ("[\\s\\S]");
              i = j;
              continue;
            }
          }
        }
        aSB.appendCodePoint (r).appendCodePoint (n);
        i++;
        continue;
      }
      if (r == '[')
        bInClass = true;
      else
        if (r == ']')
          bInClass = false;
      aSB.appendCodePoint (r);
    }
    return aSB.toString ();
  }

  /**
   * Escape '^' and '$' outside character classes: XSD patterns have no anchors,
   * both are ordinary characters (go-xslt <code>escapeXSDAnchors</code>).
   */
  @NonNull
  private static String _escapeAnchors (@NonNull final String sPattern)
  {
    final int [] a = sPattern.codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder (sPattern.length () + 4);
    boolean bInClass = false;
    for (int i = 0; i < a.length; i++)
    {
      final int r = a[i];
      if (r == '\\' && i + 1 < a.length)
      {
        aSB.appendCodePoint (r).appendCodePoint (a[i + 1]);
        i++;
        continue;
      }
      switch (r)
      {
        case '[':
          bInClass = true;
          break;
        case ']':
          bInClass = false;
          break;
        case '^':
        case '$':
          if (!bInClass)
            aSB.append ('\\');
          break;
        default:
          break;
      }
      aSB.appendCodePoint (r);
    }
    return aSB.toString ();
  }

  /** @return The pattern as written in the schema. */
  @NonNull
  String getSource ()
  {
    return m_sSource;
  }

  /**
   * Check a pattern against the XSD regular expression grammar of the given
   * version (go-xslt <code>validateXSDRegex</code>).
   *
   * @param sPattern
   *        The facet value.
   * @param eVersion
   *        The XSD version.
   * @throws XsdInvalidException
   *         if the pattern is not a valid XSD regular expression (the schema
   *         is then invalid)
   */
  static void validateSyntax (@NonNull final String sPattern, @NonNull final EXsdVersion eVersion)
  {
    XsdRegexGrammar.validate (sPattern, eVersion == EXsdVersion.V11);
  }

  /**
   * Compile a pattern that passed {@link #validateSyntax(String, EXsdVersion)}
   * (go-xslt <code>compileXSDPattern</code>). A pattern the engine cannot
   * execute throws {@link XsdUnsupportedException}, never an invalid verdict.
   *
   * @param sPattern
   *        The facet value.
   * @param eVersion
   *        The XSD version.
   * @return The compiled pattern, anchored at both ends as XSD requires.
   */
  @NonNull
  static XsdPattern compile (@NonNull final String sPattern, @NonNull final EXsdVersion eVersion)
  {
    final boolean bXsd11 = eVersion == EXsdVersion.V11;
    final String sAdjusted = _escapeAnchors (bXsd11 ? _rewriteUnknownBlocks (sPattern) : sPattern);
    try
    {
      return new XsdPattern (sPattern, XPathRegex.compile (sAdjusted, "", bXsd11).getPattern ());
    }
    catch (final XPathException ex)
    {
      throw new XsdUnsupportedException ("The pattern " + XsdLexHelper.quote (sPattern) + " cannot be compiled: " + ex.getMessage (),
                                         ex);
    }
  }

  /**
   * @param sValue
   *        The (whitespace-normalised) value.
   * @return <code>true</code> if the whole value matches.
   * @throws XsdUnsupportedException
   *         if the regular expression is too complex for the value
   */
  boolean matches (@NonNull final String sValue)
  {
    try
    {
      return XPathRegex.withStackFallback ( () -> Boolean.valueOf (m_aPattern.matcher (sValue).matches ())).booleanValue ();
    }
    catch (final XPathException ex)
    {
      throw new XsdUnsupportedException ("The pattern " + XsdLexHelper.quote (m_sSource) + " is too complex for the value", ex);
    }
  }
}
