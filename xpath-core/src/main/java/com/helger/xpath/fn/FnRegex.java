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

import java.util.List;
import java.util.regex.Matcher;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.regex.XPathRegex;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:matches, fn:replace, fn:tokenize, fn:analyze-string.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnRegex
{
  private static final String FN_PREFIX = "fn";
  private static final XdmQName ANY_TYPE = new XdmQName (XdmConstants.NS_XS, "anyType", "xs");
  private static final XdmQName POSITIVE_INTEGER = new XdmQName (XdmConstants.NS_XS, "positiveInteger", "xs");

  @PresentForCodeCoverage
  private static final FnRegex INSTANCE = new FnRegex ();

  private FnRegex ()
  {}

  @NonNull
  private static String _optString (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? "" : ((XdmAtomic) aSeq.get (0)).getString ();
  }

  @NonNull
  private static String _flags (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    return aArgs.size () > nIndex ? _optString (aArgs.get (nIndex)) : "";
  }

  @NonNull
  private static XPathRegex _compile (@NonNull final DynamicContext aCtx,
                                      @NonNull final String sPattern,
                                      @Nullable final String sFlags)
  {
    // The character class grammar follows the XSD version in force
    return XPathRegex.compile (sPattern,
                               sFlags,
                               aCtx.getStaticContext ().getXsdVersion () == EXsdVersion.V11);
  }

  @NonNull
  private static XdmSequence _matches (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sInput = _optString (aArgs.get (0));
    final XPathRegex aRegex = _compile (aCtx, _optString (aArgs.get (1)), _flags (aArgs, 2));
    return XdmSequence.of (XdmAtomic.bool (aRegex.matches (aCtx, sInput)));
  }

  /**
   * Check a replacement string (FORX0004): a '\' must be followed by '\' or
   * '$', a '$' by a digit.
   */
  private static void _checkReplacement (@NonNull final String sReplacement)
  {
    final int nLen = sReplacement.length ();
    for (int i = 0; i < nLen; ++i)
    {
      final char c = sReplacement.charAt (i);
      if (c == '\\')
      {
        if (i + 1 >= nLen || (sReplacement.charAt (i + 1) != '\\' && sReplacement.charAt (i + 1) != '$'))
          throw XPathException.of ("FORX0004", "Invalid replacement string '" + sReplacement + "'");
        i++;
      }
      else
        if (c == '$')
        {
          if (i + 1 >= nLen || sReplacement.charAt (i + 1) < '0' || sReplacement.charAt (i + 1) > '9')
            throw XPathException.of ("FORX0004", "Invalid replacement string '" + sReplacement + "'");
        }
    }
  }

  /**
   * Append the expansion of a replacement string for one match. "$N" takes
   * the longest digit sequence that names an existing group; a single digit
   * beyond the group count is replaced by the empty string.
   */
  private static void _appendReplacement (@NonNull final StringBuilder aSB,
                                          @NonNull final String sReplacement,
                                          @NonNull final Matcher aMatcher,
                                          @NonNull final XPathRegex aRegex)
  {
    final int nGroupCount = aRegex.getGroupCount ();
    final int nLen = sReplacement.length ();
    for (int i = 0; i < nLen; ++i)
    {
      final char c = sReplacement.charAt (i);
      if (c == '\\')
      {
        aSB.append (sReplacement.charAt (i + 1));
        i++;
      }
      else
        if (c == '$')
        {
          int nEnd = i + 1;
          while (nEnd < nLen && sReplacement.charAt (nEnd) >= '0' && sReplacement.charAt (nEnd) <= '9')
            nEnd++;
          int nDigits = nEnd - i - 1;
          int nGroup = -1;
          while (nDigits > 0)
          {
            final String sDigits = sReplacement.substring (i + 1, i + 1 + nDigits);
            if (sDigits.length () <= 9 && Integer.parseInt (sDigits) <= nGroupCount)
            {
              nGroup = Integer.parseInt (sDigits);
              break;
            }
            if (nDigits == 1)
              break;
            nDigits--;
          }
          if (nGroup >= 0)
          {
            final String sGroup = aMatcher.group (aRegex.getJavaGroup (nGroup));
            if (sGroup != null)
              aSB.append (sGroup);
          }
          i += nDigits;
        }
        else
          aSB.append (c);
    }
  }

  @NonNull
  private static XdmSequence _replace (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sInput = _optString (aArgs.get (0));
    final String sPattern = _optString (aArgs.get (1));
    final String sReplacement = _optString (aArgs.get (2));
    final String sFlags = _flags (aArgs, 3);
    final XPathRegex aRegex = _compile (aCtx, sPattern, sFlags);
    if (!aRegex.isLiteral ())
      _checkReplacement (sReplacement);
    if (aRegex.matchesEmptyString ())
      throw XPathException.of ("FORX0003", "The regular expression '" + sPattern + "' matches a zero-length string");
    final String sResult = XPathRegex.withStackFallback ( () -> {
      final StringBuilder aSB = new StringBuilder (sInput.length ());
      final Matcher aMatcher = aRegex.matcher (aCtx, sInput);
      int nPos = 0;
      while (aMatcher.find ())
      {
        aSB.append (sInput, nPos, aMatcher.start ());
        if (aRegex.isLiteral ())
          aSB.append (sReplacement);
        else
          _appendReplacement (aSB, sReplacement, aMatcher, aRegex);
        nPos = aMatcher.end ();
      }
      aSB.append (sInput, nPos, sInput.length ());
      return aSB.toString ();
    });
    return XdmSequence.of (XdmAtomic.string (sResult));
  }

  @NonNull
  private static XdmSequence _tokenize (@NonNull final DynamicContext aCtx,
                                        @NonNull final String sInput,
                                        @NonNull final String sPattern,
                                        @NonNull final String sFlags)
  {
    final XPathRegex aRegex = _compile (aCtx, sPattern, sFlags);
    if (aRegex.matchesEmptyString ())
      throw XPathException.of ("FORX0003", "The regular expression '" + sPattern + "' matches a zero-length string");
    if (sInput.isEmpty ())
      return XdmSequence.EMPTY;
    return XPathRegex.withStackFallback ( () -> {
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      final Matcher aMatcher = aRegex.matcher (aCtx, sInput);
      int nPos = 0;
      while (aMatcher.find ())
      {
        aBuilder.add (XdmAtomic.string (sInput.substring (nPos, aMatcher.start ())));
        nPos = aMatcher.end ();
      }
      aBuilder.add (XdmAtomic.string (sInput.substring (nPos)));
      return aBuilder.build ();
    });
  }

  @NonNull
  private static XdmSequence _tokenize1 (@NonNull final List <XdmSequence> aArgs)
  {
    final String sInput = FnString.normalizeSpace (_optString (aArgs.get (0)));
    if (sInput.isEmpty ())
      return XdmSequence.EMPTY;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final String sToken : sInput.split (" ", -1))
      aBuilder.add (XdmAtomic.string (sToken));
    return aBuilder.build ();
  }

  @NonNull
  private static XdmNode _createElement (@NonNull final String sLocalName)
  {
    final XdmNode ret = XdmNode.createElement (new XdmQName (XdmConstants.NS_FN, sLocalName, FN_PREFIX));
    // The result elements are typed by the schema of F&O 3.1 section 5.6.5;
    // their complex types are approximated by xs:anyType
    ret.setTypeAnnotation (ANY_TYPE);
    return ret;
  }

  /**
   * Whether capturing group <code>nGroup</code> is (transitively) nested in
   * group <code>nCur</code> (0: the whole match).
   */
  private static boolean _isWithin (@NonNull final XPathRegex aRegex, final int nGroup, final int nCur)
  {
    if (nCur == 0)
      return true;
    int nCheck = nGroup;
    while (nCheck > 0)
    {
      if (nCheck == nCur)
        return true;
      nCheck = aRegex.getParentGroup (nCheck);
    }
    return false;
  }

  /**
   * Fill <code>aParent</code> (capturing group <code>nCur</code>, 0 for the
   * match) with the input between <code>nFrom</code> and <code>nTo</code>,
   * wrapping every participating capturing group nested in it into an
   * fn:group element. Groups are numbered by opening parenthesis, so the groups
   * nested in <code>nCur</code> follow it directly. Nesting comes from the
   * pattern, not from the positions. The nesting depth is bounded by the
   * group nesting limit of the regex translator.
   *
   * @return The number of the next group to consider.
   */
  private static int _fillGroups (@NonNull final XdmNode aParent,
                                   @NonNull final String sInput,
                                   @NonNull final Matcher aMatcher,
                                   @NonNull final XPathRegex aRegex,
                                   final int nFirst,
                                   final int nCur,
                                   final int nFrom,
                                   final int nTo)
  {
    int nPos = nFrom;
    int nNext = nFirst;
    final int nGroupCount = aRegex.getGroupCount ();
    while (nNext <= nGroupCount && _isWithin (aRegex, nNext, nCur))
    {
      final int nGroup = nNext;
      final int nStart = aMatcher.start (aRegex.getJavaGroup (nGroup));
      final int nEnd = aMatcher.end (aRegex.getJavaGroup (nGroup));
      if (nStart < 0 || nStart < nPos || nEnd > nTo)
      {
        nNext++;
        continue;
      }
      if (nStart > nPos)
        aParent.appendChild (XdmNode.createText (sInput.substring (nPos, nStart)));
      final XdmNode aGroup = _createElement ("group");
      final XdmNode aNr = XdmNode.createAttribute (new XdmQName ("", "nr"), Integer.toString (nGroup));
      aNr.setTypeAnnotation (POSITIVE_INTEGER);
      aGroup.addAttribute (aNr);
      nNext = _fillGroups (aGroup, sInput, aMatcher, aRegex, nNext + 1, nGroup, nStart, nEnd);
      aParent.appendChild (aGroup);
      nPos = nEnd;
    }
    if (nTo > nPos)
      aParent.appendChild (XdmNode.createText (sInput.substring (nPos, nTo)));
    return nNext;
  }

  @NonNull
  private static XdmNode _buildAnalyzeResult (@NonNull final DynamicContext aCtx,
                                              @NonNull final String sInput,
                                              @NonNull final XPathRegex aRegex)
  {
    final XdmNode aDoc = XdmNode.createDocument ();
    final XdmNode aRoot = _createElement ("analyze-string-result");
    aRoot.declareNamespace (FN_PREFIX, XdmConstants.NS_FN);
    aDoc.appendChild (aRoot);
    final Matcher aMatcher = aRegex.matcher (aCtx, sInput);
    int nPos = 0;
    while (aMatcher.find ())
    {
      if (aMatcher.start () > nPos)
      {
        final XdmNode aNonMatch = _createElement ("non-match");
        aNonMatch.appendChild (XdmNode.createText (sInput.substring (nPos, aMatcher.start ())));
        aRoot.appendChild (aNonMatch);
      }
      final XdmNode aMatch = _createElement ("match");
      _fillGroups (aMatch, sInput, aMatcher, aRegex, 1, 0, aMatcher.start (), aMatcher.end ());
      aRoot.appendChild (aMatch);
      nPos = aMatcher.end ();
    }
    if (nPos < sInput.length ())
    {
      final XdmNode aNonMatch = _createElement ("non-match");
      aNonMatch.appendChild (XdmNode.createText (sInput.substring (nPos)));
      aRoot.appendChild (aNonMatch);
    }
    aDoc.assignDocumentOrder ();
    return aRoot;
  }

  @NonNull
  private static XdmSequence _analyzeString (@NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs)
  {
    final String sInput = _optString (aArgs.get (0));
    final String sPattern = _optString (aArgs.get (1));
    final XPathRegex aRegex = _compile (aCtx, sPattern, _flags (aArgs, 2));
    if (aRegex.matchesEmptyString ())
      throw XPathException.of ("FORX0003", "The regular expression '" + sPattern + "' matches a zero-length string");

    final XdmNode aRoot = XPathRegex.withStackFallback ( () -> _buildAnalyzeResult (aCtx, sInput, aRegex));
    return XdmSequence.of (aRoot);
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "matches", "(xs:string?, xs:string) as xs:boolean", FnRegex::_matches);
    aLib.register (sNS, "matches", "(xs:string?, xs:string, xs:string) as xs:boolean", FnRegex::_matches);
    aLib.register (sNS, "replace", "(xs:string?, xs:string, xs:string) as xs:string", FnRegex::_replace);
    aLib.register (sNS, "replace", "(xs:string?, xs:string, xs:string, xs:string) as xs:string", FnRegex::_replace);
    aLib.register (sNS, "tokenize", "(xs:string?) as xs:string*", (aCtx, aArgs) -> _tokenize1 (aArgs));
    aLib.register (sNS,
                   "tokenize",
                   "(xs:string?, xs:string) as xs:string*",
                   (aCtx, aArgs) -> _tokenize (aCtx, _optString (aArgs.get (0)), _optString (aArgs.get (1)), ""));
    aLib.register (sNS,
                   "tokenize",
                   "(xs:string?, xs:string, xs:string) as xs:string*",
                   (aCtx, aArgs) -> _tokenize (aCtx,
                                               _optString (aArgs.get (0)),
                                               _optString (aArgs.get (1)),
                                               _optString (aArgs.get (2))));
    aLib.register (sNS,
                   "analyze-string",
                   "(xs:string?, xs:string) as element(fn:analyze-string-result)",
                   FnRegex::_analyzeString);
    aLib.register (sNS,
                   "analyze-string",
                   "(xs:string?, xs:string, xs:string) as element(fn:analyze-string-result)",
                   FnRegex::_analyzeString);
  }
}
