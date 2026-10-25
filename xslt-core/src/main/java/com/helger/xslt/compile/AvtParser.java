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
package com.helger.xslt.compile;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;

/**
 * Splits an attribute value template into fixed text and expression text
 * (a port of go-xslt's avt.go). Braces inside string literals and XPath
 * comments do not end an expression, nested braces (map constructors) are
 * balanced. An expression consisting of whitespace and comments only
 * contributes nothing.
 *
 * @author Philip Helger
 */
@Immutable
public final class AvtParser
{
  /** Marker for the text of an expression part. */
  public record ExprPart (@NonNull String text)
  {}

  @PresentForCodeCoverage
  private static final AvtParser INSTANCE = new AvtParser ();

  private AvtParser ()
  {}

  private static int _skipComment (@NonNull final String s, final int nStart)
  {
    int nDepth = 0;
    int i = nStart;
    while (i < s.length ())
    {
      if (s.startsWith ("(:", i))
      {
        nDepth++;
        i += 2;
      }
      else
        if (s.startsWith (":)", i))
        {
          nDepth--;
          i += 2;
          if (nDepth == 0)
            return i;
        }
        else
          i++;
    }
    throw new XPathException ("XTSE0350", "Unterminated XPath comment in attribute value template '" + s + "'");
  }

  private static boolean _isBlank (@NonNull final String s)
  {
    int i = 0;
    while (i < s.length ())
    {
      final char c = s.charAt (i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
        i++;
      else
        if (s.startsWith ("(:", i))
          i = _skipComment (s, i);
        else
          return false;
    }
    return true;
  }

  /**
   * @param s
   *        The template text.
   * @return The parts: {@link String} for fixed text, {@link ExprPart} for
   *         expressions.
   * @throws XPathException
   *         XTSE0350 for an unmatched left curly bracket, XTSE0370 for an
   *         unmatched right one.
   */
  @NonNull
  public static ICommonsList <Object> split (@NonNull final String s)
  {
    final ICommonsList <Object> ret = new CommonsArrayList <> ();
    final StringBuilder aLit = new StringBuilder ();
    int i = 0;
    final int nLen = s.length ();
    while (i < nLen)
    {
      final char c = s.charAt (i);
      if (c == '{')
      {
        if (i + 1 < nLen && s.charAt (i + 1) == '{')
        {
          aLit.append ('{');
          i += 2;
          continue;
        }
        // Scan the expression
        int j = i + 1;
        int nDepth = 1;
        int nEnd = -1;
        while (j < nLen)
        {
          final char d = s.charAt (j);
          if (d == '\'' || d == '"')
          {
            j++;
            while (j < nLen && s.charAt (j) != d)
              j++;
            if (j >= nLen)
              throw new XPathException ("XTSE0350",
                                        "Unterminated string literal in attribute value template '" + s + "'");
            j++;
          }
          else
            if (d == '(' && j + 1 < nLen && s.charAt (j + 1) == ':')
              j = _skipComment (s, j);
            else
              if (d == '{')
              {
                nDepth++;
                j++;
              }
              else
                if (d == '}')
                {
                  nDepth--;
                  if (nDepth == 0)
                  {
                    nEnd = j;
                    break;
                  }
                  j++;
                }
                else
                  j++;
        }
        if (nEnd < 0)
          throw new XPathException ("XTSE0350", "Unbalanced '{' in attribute value template '" + s + "'");
        final String sExpr = s.substring (i + 1, nEnd);
        if (!_isBlank (sExpr))
        {
          if (aLit.length () > 0)
          {
            ret.add (aLit.toString ());
            aLit.setLength (0);
          }
          ret.add (new ExprPart (sExpr));
        }
        i = nEnd + 1;
        continue;
      }
      if (c == '}')
      {
        if (i + 1 < nLen && s.charAt (i + 1) == '}')
        {
          aLit.append ('}');
          i += 2;
          continue;
        }
        throw new XPathException ("XTSE0370", "Unescaped '}' in attribute value template '" + s + "'");
      }
      aLit.append (c);
      i++;
    }
    if (aLit.length () > 0)
      ret.add (aLit.toString ());
    return ret;
  }
}
