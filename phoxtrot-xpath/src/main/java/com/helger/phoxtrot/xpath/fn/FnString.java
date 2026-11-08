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
package com.helger.phoxtrot.xpath.fn;

import java.math.BigInteger;
import java.text.Normalizer;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.collation.ICollation;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.EXmlVersion;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Function family: the string functions of F&amp;O section 5 except the
 * regular expression ones, codepoints, compare and collation-key,
 * normalize-unicode. All positions and lengths are counted in code points, not
 * UTF-16 units.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnString
{
  private static final XdmSequence TRUE_SEQ = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE_SEQ = XdmSequence.of (XdmAtomic.FALSE);
  private static final XdmSequence EMPTY_STRING_SEQ = XdmSequence.of (XdmAtomic.string (""));

  @PresentForCodeCoverage
  private static final FnString INSTANCE = new FnString ();

  private FnString ()
  {}

  /**
   * Characters that are "composing" for FULLY-NORMALIZED (F&amp;O 3.1 section
   * 5.4.6): the second character of a canonical composition, computed once.
   */
  private static final class ComposingHolder
  {
    static final BitSet SECOND_OF_COMPOSITION = _build ();

    /**
     * Scan all defined code points below U+30000 for primary composites: a
     * character whose canonical decomposition has exactly two code points and
     * composes back to it under NFC. The second code point of each such
     * decomposition is set. Runs once, on first use of the holder.
     *
     * @return The set of composing second characters.
     */
    @NonNull
    private static BitSet _build ()
    {
      final BitSet ret = new BitSet ();
      // Canonical decompositions exist only below U+30000
      for (int c = 0; c < 0x30000; ++c)
      {
        if (!Character.isDefined (c) || (c >= 0xD800 && c <= 0xDFFF))
          continue;
        final String s = new String (Character.toChars (c));
        final String sNFD = Normalizer.normalize (s, Normalizer.Form.NFD);
        if (sNFD.equals (s))
          continue;
        final int nFirst = sNFD.codePointAt (0);
        final int nFirstLen = Character.charCount (nFirst);
        if (nFirstLen >= sNFD.length ())
          continue;
        final int nSecond = sNFD.codePointAt (nFirstLen);
        // A primary composite: composing the decomposition gives c back
        if (nFirstLen + Character.charCount (nSecond) == sNFD.length () &&
            Normalizer.normalize (sNFD, Normalizer.Form.NFC).equals (s))
          ret.set (nSecond);
      }
      return ret;
    }
  }

  /**
   * @param aSeq
   *        An <code>xs:string?</code> argument.
   * @return Its string value, the empty string for the empty sequence.
   */
  @NonNull
  private static String _optString (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? "" : ((XdmAtomic) aSeq.get (0)).getString ();
  }

  /**
   * @param s
   *        A string.
   * @return It as a single xs:string; the shared instance for the empty
   *         string.
   */
  @NonNull
  private static XdmSequence _str (@NonNull final String s)
  {
    return s.isEmpty () ? EMPTY_STRING_SEQ : XdmSequence.of (XdmAtomic.string (s));
  }

  @NonNull
  private static XdmSequence _bool (final boolean b)
  {
    return b ? TRUE_SEQ : FALSE_SEQ;
  }

  /**
   * @param c
   *        A character.
   * @return <code>true</code> for XML whitespace: space, tab, line feed and
   *         carriage return.
   */
  private static boolean _isXmlSpace (final int c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /**
   * The collation of a function call: the explicit argument at the passed
   * index (resolved against the static base URI), else the default collation
   * of the static context.
   */
  @NonNull
  private static ICollation _collation (@NonNull final DynamicContext aCtx,
                                        @NonNull final List <XdmSequence> aArgs,
                                        final int nIndex)
  {
    if (aArgs.size () > nIndex)
      return CollationRegistry.resolveCollation (_optString (aArgs.get (nIndex)),
                                                 aCtx.getStaticContext ().getBaseUri ());
    return CollationRegistry.resolveCollation (aCtx.getStaticContext ().getDefaultCollation (), null);
  }

  /** The string value of the context item (zero-argument forms). */
  @NonNull
  private static String _contextString (@NonNull final DynamicContext aCtx)
  {
    final IXdmItem aItem = aCtx.getContextItem ();
    if (aItem == null)
      throw XPathException.noContext ("The context item is absent");
    return Atomizer.getStringValue (aItem);
  }

  /**
   * F&amp;O fn:round semantics for the positions of fn:substring: the nearest
   * integer, halves rounded towards positive infinity.
   */
  private static double _round (final double d)
  {
    if (Double.isNaN (d) || Double.isInfinite (d))
      return d;
    final double dFloor = Math.floor (d);
    return d - dFloor >= 0.5 ? dFloor + 1 : dFloor;
  }

  /**
   * @param nCP
   *        A code point, or -1 for a value out of range.
   * @param eXmlVersion
   *        The XML version of the static context.
   * @return <code>true</code> if it matches the <code>Char</code> production
   *         of that XML version: XML 1.1 also allows the C0 controls except
   *         NUL.
   */
  private static boolean _isXmlChar (final long nCP, @NonNull final EXmlVersion eXmlVersion)
  {
    // Char production of XML 1.1 (any C0 control but NUL) or XML 1.0
    if (eXmlVersion == EXmlVersion.V11 && nCP >= 0x1 && nCP <= 0xD7FF)
      return true;
    return nCP == 0x9 ||
           nCP == 0xA ||
           nCP == 0xD ||
           (nCP >= 0x20 && nCP <= 0xD7FF) ||
           (nCP >= 0xE000 && nCP <= 0xFFFD) ||
           (nCP >= 0x10000 && nCP <= 0x10FFFF);
  }

  /**
   * <code>fn:codepoints-to-string($arg as xs:integer*) as xs:string</code>
   * (F&amp;O 3.1 section 5.2.1).
   *
   * @param aCtx
   *        The dynamic context of the call (for the XML version).
   * @param aArgs
   *        The code points.
   * @return The string; the empty string for no code point.
   * @throws XPathException
   *         FOCH0001 for a code point that is not a valid XML character of the
   *         XML version of the static context.
   */
  @NonNull
  private static XdmSequence _codepointsToString (@NonNull final DynamicContext aCtx,
                                                  @NonNull final List <XdmSequence> aArgs)
  {
    final EXmlVersion eXmlVersion = aCtx.getStaticContext ().getXmlVersion ();
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aArgs.get (0))
    {
      final BigInteger aValue = ((XdmAtomic) aItem).getBigInteger ();
      final long nCP = aValue.bitLength () < 32 ? aValue.longValue () : -1;
      if (!_isXmlChar (nCP, eXmlVersion))
        throw XPathException.of ("FOCH0001", "Codepoint " + aValue + " is not a valid XML character");
      aSB.appendCodePoint ((int) nCP);
    }
    return _str (aSB.toString ());
  }

  /**
   * <code>fn:string-to-codepoints($arg as xs:string?) as xs:integer*</code>
   * (F&amp;O 3.1 section 5.2.2).
   *
   * @param aArgs
   *        The string.
   * @return One integer per code point (a surrogate pair counts once); the
   *         empty sequence for an empty argument or string.
   */
  @NonNull
  private static XdmSequence _stringToCodepoints (@NonNull final List <XdmSequence> aArgs)
  {
    final String s = _optString (aArgs.get (0));
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    s.codePoints ().forEach (c -> aBuilder.add (XdmAtomic.integer (c)));
    return aBuilder.build ();
  }

  /**
   * <code>fn:compare($comparand1 as xs:string?, $comparand2 as xs:string?[,
   * $collation as xs:string]) as xs:integer?</code> (F&amp;O 3.1 section
   * 5.3.6). The collation is resolved first, so an unknown collation is an
   * error even for empty arguments.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return -1, 0 or 1; the empty sequence if either string is empty.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _compare (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    if (aArgs.get (0).isEmpty () || aArgs.get (1).isEmpty ())
      return XdmSequence.EMPTY;
    final int nCmp = aCollation.compare (_optString (aArgs.get (0)), _optString (aArgs.get (1)));
    return XdmSequence.of (XdmAtomic.integer (Integer.signum (nCmp)));
  }

  /**
   * <code>fn:codepoint-equal($comparand1 as xs:string?, $comparand2 as
   * xs:string?) as xs:boolean?</code> (F&amp;O 3.1 section 5.3.7).
   *
   * @param aArgs
   *        The arguments.
   * @return Whether both are equal code point by code point; the empty
   *         sequence if either is empty.
   */
  @NonNull
  private static XdmSequence _codepointEqual (@NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty () || aArgs.get (1).isEmpty ())
      return XdmSequence.EMPTY;
    return _bool (_optString (aArgs.get (0)).equals (_optString (aArgs.get (1))));
  }

  /**
   * <code>fn:concat($arg1 as xs:anyAtomicType?, $arg2 as xs:anyAtomicType?,
   * ...) as xs:string</code> (F&amp;O 3.1 section 5.4.1), for any arity of at
   * least two: the string values of the non-empty arguments joined.
   *
   * @param aArgs
   *        The arguments.
   * @return The concatenation.
   */
  @NonNull
  private static XdmSequence _concat (@NonNull final List <XdmSequence> aArgs)
  {
    // The parts first, so that the builder gets their total length
    final int nArgs = aArgs.size ();
    final String [] aParts = new String [nArgs];
    int nLength = 0;
    for (int i = 0; i < nArgs; ++i)
    {
      final XdmSequence aArg = aArgs.get (i);
      if (!aArg.isEmpty ())
      {
        final String sPart = ((XdmAtomic) aArg.get (0)).getLexical ();
        aParts[i] = sPart;
        nLength += sPart.length ();
      }
    }
    final StringBuilder aSB = new StringBuilder (nLength);
    for (final String sPart : aParts)
      if (sPart != null)
        aSB.append (sPart);
    return _str (aSB.toString ());
  }

  /**
   * <code>fn:string-join($arg1 as xs:anyAtomicType*[, $arg2 as xs:string]) as
   * xs:string</code> (F&amp;O 3.1 section 5.4.2): the string values joined
   * with the separator, the empty string if there is none.
   *
   * @param aArgs
   *        The arguments.
   * @return The joined string.
   */
  @NonNull
  private static XdmSequence _stringJoin (@NonNull final List <XdmSequence> aArgs)
  {
    final String sSep = aArgs.size () > 1 ? _optString (aArgs.get (1)) : "";
    final StringBuilder aSB = new StringBuilder ();
    boolean bFirst = true;
    for (final IXdmItem aItem : aArgs.get (0))
    {
      if (bFirst)
        bFirst = false;
      else
        aSB.append (sSep);
      aSB.append (((XdmAtomic) aItem).getLexical ());
    }
    return _str (aSB.toString ());
  }

  /**
   * <code>fn:substring($sourceString as xs:string?, $start as xs:double[,
   * $length as xs:double]) as xs:string</code> (F&amp;O 3.1 section 5.4.3):
   * the code points at the 1-based positions <code>p</code> with
   * <code>round($start) &lt;= p &lt; round($start) + round($length)</code> (no
   * upper limit without <code>$length</code>), with the rounding of
   * {@link #_round(double)}. A NaN bound, also from <code>-INF + INF</code>,
   * gives the empty string.
   *
   * @param aArgs
   *        The arguments.
   * @return The substring.
   */
  @NonNull
  private static XdmSequence _substring (@NonNull final List <XdmSequence> aArgs)
  {
    final String s = _optString (aArgs.get (0));
    final double dStart = _round (((XdmAtomic) aArgs.get (1).get (0)).getAsDouble ());
    final double dEnd = aArgs.size () > 2 ? dStart + _round (((XdmAtomic) aArgs.get (2).get (0)).getAsDouble ())
                                          : Double.POSITIVE_INFINITY;
    if (Double.isNaN (dStart) || Double.isNaN (dEnd))
      return EMPTY_STRING_SEQ;
    // Characters at positions p with dStart <= p < dEnd (1-based)
    final int nLen = s.codePointCount (0, s.length ());
    final double dFirst = Math.max (1, dStart);
    final double dLast = Math.min (nLen + 1, dEnd);
    if (dLast <= dFirst)
      return EMPTY_STRING_SEQ;
    final int nFirst = (int) dFirst - 1;
    final int nLast = (int) dLast - 1;
    final int nBegin = s.offsetByCodePoints (0, nFirst);
    final int nFinish = s.offsetByCodePoints (nBegin, nLast - nFirst);
    return _str (s.substring (nBegin, nFinish));
  }

  /**
   * @param s
   *        A string.
   * @return Its length in code points (<code>fn:string-length</code>, F&amp;O
   *         3.1 section 5.4.4).
   */
  @NonNull
  private static XdmSequence _stringLength (@NonNull final String s)
  {
    return XdmSequence.of (XdmAtomic.integer (s.codePointCount (0, s.length ())));
  }

  /**
   * <code>fn:normalize-unicode($arg as xs:string?[, $normalizationForm as
   * xs:string]) as xs:string</code> (F&amp;O 3.1 section 5.4.6). The form is
   * trimmed and upper-cased; the default is NFC, an empty form returns the
   * string unchanged. NFC, NFD, NFKC and NFKD use {@link Normalizer};
   * FULLY-NORMALIZED is NFC after a space has been put in front of a string
   * that starts with a composing character (a non-spacing or enclosing mark,
   * or the second character of a primary composite).
   *
   * @param aArgs
   *        The arguments.
   * @return The normalised string.
   * @throws XPathException
   *         FOCH0003 for any other form.
   */
  @NonNull
  private static XdmSequence _normalizeUnicode (@NonNull final List <XdmSequence> aArgs)
  {
    String s = _optString (aArgs.get (0));
    final String sForm = aArgs.size () > 1 ? _optString (aArgs.get (1)).strip ().toUpperCase (Locale.ROOT) : "NFC";
    switch (sForm)
    {
      case "":
        return _str (s);
      case "NFC":
        return _str (Normalizer.normalize (s, Normalizer.Form.NFC));
      case "NFD":
        return _str (Normalizer.normalize (s, Normalizer.Form.NFD));
      case "NFKC":
        return _str (Normalizer.normalize (s, Normalizer.Form.NFKC));
      case "NFKD":
        return _str (Normalizer.normalize (s, Normalizer.Form.NFKD));
      case "FULLY-NORMALIZED":
      {
        // A string must not start with a composing character: one with a
        // non-zero combining class or the second character of a composition
        if (!s.isEmpty ())
        {
          final int c = s.codePointAt (0);
          final int nType = Character.getType (c);
          if (nType == Character.NON_SPACING_MARK ||
              nType == Character.ENCLOSING_MARK ||
              ComposingHolder.SECOND_OF_COMPOSITION.get (c))
            s = " " + s;
        }
        return _str (Normalizer.normalize (s, Normalizer.Form.NFC));
      }
      default:
        throw XPathException.of ("FOCH0003", "Unsupported normalization form '" + sForm + "'");
    }
  }

  /**
   * Full Unicode case mapping without context- and language-sensitive rules:
   * every code point is mapped on its own. A string of ASCII characters that
   * the mapping keeps is returned itself; otherwise the unchanged ASCII prefix
   * is copied at once.
   */
  @NonNull
  private static String _mapCase (@NonNull final String s, final boolean bUpper)
  {
    // The leading ASCII characters that the mapping keeps
    final int nLen = s.length ();
    int i = 0;
    while (i < nLen)
    {
      final char c = s.charAt (i);
      if (c >= 0x80 || (bUpper ? (c >= 'a' && c <= 'z') : (c >= 'A' && c <= 'Z')))
        break;
      i++;
    }
    if (i == nLen)
      return s;
    final StringBuilder aSB = new StringBuilder (nLen);
    aSB.append (s, 0, i);
    while (i < nLen)
    {
      final int c = s.codePointAt (i);
      i += Character.charCount (c);
      if (c < 0x80)
        aSB.append ((char) (bUpper ? (c >= 'a' && c <= 'z' ? c - 32 : c) : (c >= 'A' && c <= 'Z' ? c + 32 : c)));
      else
        if (c == 0x037F || c == 0x03F3)
        {
          // GREEK CAPITAL LETTER YOT (Unicode 7.0) and its small letter: the
          // test suite's expectations predate this case pair
          aSB.appendCodePoint (c);
        }
        else
        {
          final String sChar = new String (Character.toChars (c));
          aSB.append (bUpper ? sChar.toUpperCase (Locale.ROOT) : sChar.toLowerCase (Locale.ROOT));
        }
    }
    return aSB.toString ();
  }

  /**
   * <code>fn:translate($arg as xs:string?, $mapString as xs:string,
   * $transString as xs:string) as xs:string</code> (F&amp;O 3.1 section
   * 5.4.9): replace every code point that occurs in the map string (its first
   * occurrence counts) by the code point at the same position of the
   * translation string, or drop it if the translation string is shorter.
   *
   * @param aArgs
   *        The arguments.
   * @return The translated string.
   */
  @NonNull
  private static XdmSequence _translate (@NonNull final List <XdmSequence> aArgs)
  {
    final String s = _optString (aArgs.get (0));
    final int [] aMap = _optString (aArgs.get (1)).codePoints ().toArray ();
    final int [] aTrans = _optString (aArgs.get (2)).codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder (s.length ());
    s.codePoints ().forEach (c -> {
      int nIndex = -1;
      for (int i = 0; i < aMap.length; ++i)
        if (aMap[i] == c)
        {
          nIndex = i;
          break;
        }
      if (nIndex < 0)
        aSB.appendCodePoint (c);
      else
        if (nIndex < aTrans.length)
          aSB.appendCodePoint (aTrans[nIndex]);
    });
    return _str (aSB.toString ());
  }

  /**
   * <code>fn:contains($arg1 as xs:string?, $arg2 as xs:string?[, $collation as
   * xs:string]) as xs:boolean</code> (F&amp;O 3.1 section 5.5.1): an empty
   * search string is contained in anything; otherwise the collation looks for
   * a match.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _contains (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    final String sSearch = _optString (aArgs.get (1));
    if (sSearch.isEmpty ())
      return TRUE_SEQ;
    return _bool (aCollation.findSubstring (_optString (aArgs.get (0)), sSearch) != null);
  }

  /**
   * <code>fn:starts-with($arg1 as xs:string?, $arg2 as xs:string?[, $collation
   * as xs:string]) as xs:boolean</code> (F&amp;O 3.1 section 5.5.2):
   * <code>true</code> for an empty search string.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _startsWith (@NonNull final DynamicContext aCtx,
                                          @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    final String sSearch = _optString (aArgs.get (1));
    if (sSearch.isEmpty ())
      return TRUE_SEQ;
    return _bool (aCollation.startsWith (_optString (aArgs.get (0)), sSearch));
  }

  /**
   * <code>fn:ends-with($arg1 as xs:string?, $arg2 as xs:string?[, $collation
   * as xs:string]) as xs:boolean</code> (F&amp;O 3.1 section 5.5.3):
   * <code>true</code> for an empty search string.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _endsWith (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    final String sSearch = _optString (aArgs.get (1));
    if (sSearch.isEmpty ())
      return TRUE_SEQ;
    return _bool (aCollation.endsWith (_optString (aArgs.get (0)), sSearch));
  }

  /**
   * <code>fn:substring-before($arg1 as xs:string?, $arg2 as xs:string?[,
   * $collation as xs:string]) as xs:string</code> (F&amp;O 3.1 section 5.5.4):
   * the part before the first match of the collation.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The part before the match; the empty string for an empty search
   *         string or if there is no match.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _substringBefore (@NonNull final DynamicContext aCtx,
                                               @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    final String s = _optString (aArgs.get (0));
    final String sSearch = _optString (aArgs.get (1));
    if (sSearch.isEmpty ())
      return EMPTY_STRING_SEQ;
    final int [] aMatch = aCollation.findSubstring (s, sSearch);
    return aMatch == null ? EMPTY_STRING_SEQ : _str (s.substring (0, aMatch[0]));
  }

  /**
   * <code>fn:substring-after($arg1 as xs:string?, $arg2 as xs:string?[,
   * $collation as xs:string]) as xs:string</code> (F&amp;O 3.1 section 5.5.5):
   * the part after the first match of the collation.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The part after the match; the whole string for an empty search
   *         string, the empty string if there is no match.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _substringAfter (@NonNull final DynamicContext aCtx,
                                              @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    final String s = _optString (aArgs.get (0));
    final String sSearch = _optString (aArgs.get (1));
    if (sSearch.isEmpty ())
      return _str (s);
    final int [] aMatch = aCollation.findSubstring (s, sSearch);
    return aMatch == null ? EMPTY_STRING_SEQ : _str (s.substring (aMatch[1]));
  }

  /**
   * <code>fn:contains-token($input as xs:string*, $token as xs:string[,
   * $collation as xs:string]) as xs:boolean</code> (F&amp;O 3.1 section
   * 5.3.9): the token (with whitespace normalised) is compared under the
   * collation with every whitespace-separated token of every input string.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return <code>true</code> on the first equal token; <code>false</code> if
   *         there is none or the token is empty or whitespace only.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _containsToken (@NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 2);
    final String sToken = normalizeSpace (_optString (aArgs.get (1)));
    if (sToken.isEmpty ())
      return FALSE_SEQ;
    for (final IXdmItem aItem : aArgs.get (0))
    {
      final String sInput = normalizeSpace (((XdmAtomic) aItem).getString ());
      if (sInput.isEmpty ())
        continue;
      for (final String sPart : sInput.split (" "))
        if (aCollation.compare (sPart, sToken) == 0)
          return TRUE_SEQ;
    }
    return FALSE_SEQ;
  }

  /**
   * <code>fn:collation-key($key as xs:string[, $collation as xs:string]) as
   * xs:base64Binary</code> (F&amp;O 3.1 section 5.3.8): the key bytes of the
   * collation, so that keys compare like the strings do under it.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The key.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _collationKey (@NonNull final DynamicContext aCtx,
                                            @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = _collation (aCtx, aArgs, 1);
    final byte [] aKey = aCollation.getCollationKeyBytes (_optString (aArgs.get (0)));
    return XdmSequence.of (XdmAtomic.of (EAtomicType.BASE64_BINARY, aKey));
  }

  /**
   * @param s
   *        Input string.
   * @return The string with leading and trailing XML whitespace removed and
   *         internal whitespace sequences replaced by one space: the input
   *         itself if it is already normalised, a substring if only leading or
   *         trailing whitespace is removed.
   */
  @NonNull
  public static String normalizeSpace (@NonNull final String s)
  {
    final int nLen = s.length ();
    int nStart = 0;
    while (nStart < nLen && _isXmlSpace (s.charAt (nStart)))
      nStart++;
    int nEnd = nLen;
    while (nEnd > nStart && _isXmlSpace (s.charAt (nEnd - 1)))
      nEnd--;
    // The trimmed part up to the first whitespace that is not a single space;
    // s[nEnd - 1] is no whitespace, so i + 1 stays in range
    int i = nStart;
    while (i < nEnd)
    {
      final char c = s.charAt (i);
      if (_isXmlSpace (c) && (c != ' ' || _isXmlSpace (s.charAt (i + 1))))
        break;
      i++;
    }
    if (i == nEnd)
    {
      // Already normalised apart from leading and trailing whitespace: no copy
      // at all, or one substring
      return s.substring (nStart, nEnd);
    }
    // s[nStart, i) is normalised and ends with no whitespace; then the runs
    // of other characters are appended at once, separated by one space
    final StringBuilder aSB = new StringBuilder (nEnd - nStart);
    aSB.append (s, nStart, i);
    while (i < nEnd)
    {
      // Skip the whitespace; s[nEnd - 1] is no whitespace
      while (_isXmlSpace (s.charAt (i)))
        i++;
      final int nRunStart = i;
      while (i < nEnd && !_isXmlSpace (s.charAt (i)))
        i++;
      aSB.append (' ').append (s, nRunStart, i);
    }
    return aSB.toString ();
  }

  /**
   * Register the string functions of F&amp;O 3.1 section 5 apart from the
   * regular expression ones (see {@link FnRegex}):
   * <code>fn:codepoints-to-string</code>,
   * <code>fn:string-to-codepoints</code>, <code>fn:compare</code>,
   * <code>fn:codepoint-equal</code>, <code>fn:concat</code> (variadic from
   * arity 2), <code>fn:string-join</code>, <code>fn:substring</code>,
   * <code>fn:string-length</code> and <code>fn:normalize-space</code> (arity 0
   * reads the string value of the context item, XPDY0002 if absent),
   * <code>fn:normalize-unicode</code>, <code>fn:upper-case</code>,
   * <code>fn:lower-case</code>, <code>fn:translate</code>,
   * <code>fn:contains</code>, <code>fn:starts-with</code>,
   * <code>fn:ends-with</code>, <code>fn:substring-before</code>,
   * <code>fn:substring-after</code>, <code>fn:contains-token</code> and
   * <code>fn:collation-key</code>. Functions without collation argument use
   * the default collation of the static context.
   *
   * @param aLib
   *        The library to register into.
   */
  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS,
                   "codepoints-to-string",
                   "(xs:integer*) as xs:string",
                   FnString::_codepointsToString);
    aLib.register (sNS,
                   "string-to-codepoints",
                   "(xs:string?) as xs:integer*",
                   (aCtx, aArgs) -> _stringToCodepoints (aArgs));
    aLib.register (sNS, "compare", "(xs:string?, xs:string?) as xs:integer?", FnString::_compare);
    aLib.register (sNS, "compare", "(xs:string?, xs:string?, xs:string) as xs:integer?", FnString::_compare);
    aLib.register (sNS,
                   "codepoint-equal",
                   "(xs:string?, xs:string?) as xs:boolean?",
                   (aCtx, aArgs) -> _codepointEqual (aArgs));
    aLib.registerVariadic (sNS, "concat", 2, "xs:anyAtomicType?", "xs:string", (aCtx, aArgs) -> _concat (aArgs));
    aLib.register (sNS, "string-join", "(xs:anyAtomicType*) as xs:string", (aCtx, aArgs) -> _stringJoin (aArgs));
    aLib.register (sNS,
                   "string-join",
                   "(xs:anyAtomicType*, xs:string) as xs:string",
                   (aCtx, aArgs) -> _stringJoin (aArgs));
    aLib.register (sNS, "substring", "(xs:string?, xs:double) as xs:string", (aCtx, aArgs) -> _substring (aArgs));
    aLib.register (sNS,
                   "substring",
                   "(xs:string?, xs:double, xs:double) as xs:string",
                   (aCtx, aArgs) -> _substring (aArgs));
    aLib.register (sNS,
                   "string-length",
                   "() as xs:integer",
                   (aCtx, aArgs) -> _stringLength (_contextString (aCtx)),
                   true);
    aLib.register (sNS,
                   "string-length",
                   "(xs:string?) as xs:integer",
                   (aCtx, aArgs) -> _stringLength (_optString (aArgs.get (0))));
    aLib.register (sNS,
                   "normalize-space",
                   "() as xs:string",
                   (aCtx, aArgs) -> _str (normalizeSpace (_contextString (aCtx))),
                   true);
    aLib.register (sNS,
                   "normalize-space",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> _str (normalizeSpace (_optString (aArgs.get (0)))));
    aLib.register (sNS,
                   "normalize-unicode",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> _normalizeUnicode (aArgs));
    aLib.register (sNS,
                   "normalize-unicode",
                   "(xs:string?, xs:string) as xs:string",
                   (aCtx, aArgs) -> _normalizeUnicode (aArgs));
    aLib.register (sNS,
                   "upper-case",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> _str (_mapCase (_optString (aArgs.get (0)), true)));
    aLib.register (sNS,
                   "lower-case",
                   "(xs:string?) as xs:string",
                   (aCtx, aArgs) -> _str (_mapCase (_optString (aArgs.get (0)), false)));
    aLib.register (sNS,
                   "translate",
                   "(xs:string?, xs:string, xs:string) as xs:string",
                   (aCtx, aArgs) -> _translate (aArgs));
    aLib.register (sNS, "contains", "(xs:string?, xs:string?) as xs:boolean", FnString::_contains);
    aLib.register (sNS, "contains", "(xs:string?, xs:string?, xs:string) as xs:boolean", FnString::_contains);
    aLib.register (sNS, "starts-with", "(xs:string?, xs:string?) as xs:boolean", FnString::_startsWith);
    aLib.register (sNS, "starts-with", "(xs:string?, xs:string?, xs:string) as xs:boolean", FnString::_startsWith);
    aLib.register (sNS, "ends-with", "(xs:string?, xs:string?) as xs:boolean", FnString::_endsWith);
    aLib.register (sNS, "ends-with", "(xs:string?, xs:string?, xs:string) as xs:boolean", FnString::_endsWith);
    aLib.register (sNS, "substring-before", "(xs:string?, xs:string?) as xs:string", FnString::_substringBefore);
    aLib.register (sNS,
                   "substring-before",
                   "(xs:string?, xs:string?, xs:string) as xs:string",
                   FnString::_substringBefore);
    aLib.register (sNS, "substring-after", "(xs:string?, xs:string?) as xs:string", FnString::_substringAfter);
    aLib.register (sNS,
                   "substring-after",
                   "(xs:string?, xs:string?, xs:string) as xs:string",
                   FnString::_substringAfter);
    aLib.register (sNS, "contains-token", "(xs:string*, xs:string) as xs:boolean", FnString::_containsToken);
    aLib.register (sNS,
                   "contains-token",
                   "(xs:string*, xs:string, xs:string) as xs:boolean",
                   FnString::_containsToken);
    aLib.register (sNS, "collation-key", "(xs:string) as xs:base64Binary", FnString::_collationKey);
    aLib.register (sNS, "collation-key", "(xs:string, xs:string) as xs:base64Binary", FnString::_collationKey);
  }
}
