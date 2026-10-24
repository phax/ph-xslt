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
package com.helger.xpath.collation;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;

/**
 * The UCA collation family <code>http://www.w3.org/2013/collation/UCA</code>
 * (F&amp;O 3.1 section 5.3.4), built on {@link Collator} of the JDK (no ICU).
 * What the JDK collators cannot express is layered on top, as in go-xslt:
 * caseFirst=upper, caseLevel=yes and backwards=yes compare level by level
 * with collators of different strength; numeric=yes rewrites digit runs so
 * that they sort by value; alternate=shifted/blanked removes the variable
 * characters (maxVariable) below the quaternary level. A parameter that cannot
 * be honoured (reorder, an unknown keyword or value) is ignored with
 * fallback=yes and is FOCH0002 with fallback=no.
 * <p>
 * Every comparison is the comparison of collation keys (int arrays), so
 * {@link #compare(String, String)} and the keys always agree.
 *
 * @author Philip Helger
 */
@Immutable
final class UcaCollation implements ICollation
{
  static final String UCA_URI = "http://www.w3.org/2013/collation/UCA";

  /** Offset of the JDK primary weights above the weights of variable characters */
  private static final int PRIMARY_OFFSET = 0xA00000;

  private static final ICommonsMap <String, ICommonsSet <String>> VALUES = new CommonsHashMap <> ();
  static
  {
    VALUES.put ("fallback", new CommonsHashSet <> ("yes", "no"));
    VALUES.put ("strength",
                new CommonsHashSet <> ("primary",
                                       "secondary",
                                       "tertiary",
                                       "quaternary",
                                       "identical",
                                       "1",
                                       "2",
                                       "3",
                                       "4",
                                       "5"));
    VALUES.put ("maxVariable", new CommonsHashSet <> ("space", "punct", "symbol", "currency"));
    VALUES.put ("alternate", new CommonsHashSet <> ("non-ignorable", "shifted", "shift-trimmed", "blanked"));
    VALUES.put ("backwards", new CommonsHashSet <> ("yes", "no"));
    VALUES.put ("normalization", new CommonsHashSet <> ("yes", "no"));
    VALUES.put ("caseLevel", new CommonsHashSet <> ("yes", "no"));
    VALUES.put ("caseFirst", new CommonsHashSet <> ("upper", "lower"));
    VALUES.put ("numeric", new CommonsHashSet <> ("yes", "no"));
  }

  private final String m_sUri;
  private final Collator m_aPrimary;
  private final Collator m_aSecondary;
  private final Collator m_aTertiary;
  private final int m_nLevel;
  private final boolean m_bUpperFirst;
  private final boolean m_bCaseLevel;
  private final boolean m_bBackwards;
  private final boolean m_bNumeric;
  // 0: no alternate handling, otherwise the highest variable group
  private final int m_nMaxVariable;
  private final boolean m_bQuaternary;
  private final boolean m_bIdentical;

  private UcaCollation (@NonNull final String sUri,
                        @NonNull final Locale aLocale,
                        final int nLevel,
                        final boolean bUpperFirst,
                        final boolean bCaseLevel,
                        final boolean bBackwards,
                        final boolean bNumeric,
                        final int nMaxVariable,
                        final boolean bBlanked)
  {
    m_sUri = sUri;
    m_nLevel = nLevel;
    m_bUpperFirst = bUpperFirst;
    m_bCaseLevel = bCaseLevel;
    m_bBackwards = bBackwards;
    m_bNumeric = bNumeric;
    m_nMaxVariable = nMaxVariable;
    m_bQuaternary = nMaxVariable > 0 && nLevel >= 4 && !bBlanked;
    m_bIdentical = nLevel >= 5;
    // The only French tailoring of the JDK is the backwards secondary
    // ordering, which CLDR applies to Canadian French only
    final Locale aJdkLocale = "fr".equals (aLocale.getLanguage ()) ? Locale.ROOT : aLocale;
    m_aPrimary = _createCollator (aJdkLocale, Collator.PRIMARY);
    m_aSecondary = _createCollator (aJdkLocale, Collator.SECONDARY);
    m_aTertiary = _createCollator (aJdkLocale, Collator.TERTIARY);
  }

  @NonNull
  private static Collator _createCollator (@NonNull final Locale aLocale, final int nStrength)
  {
    final Collator ret = Collator.getInstance (aLocale);
    ret.setStrength (nStrength);
    ret.setDecomposition (Collator.CANONICAL_DECOMPOSITION);
    return ret;
  }

  /**
   * The UCA reordering group of a character for maxVariable: 1 space, 2
   * punctuation, 3 symbol, 4 currency, 0 anything else.
   */
  private static int _getVariableGroup (final int c)
  {
    switch (Character.getType (c))
    {
      case Character.SPACE_SEPARATOR:
      case Character.LINE_SEPARATOR:
      case Character.PARAGRAPH_SEPARATOR:
        return 1;
      case Character.CONNECTOR_PUNCTUATION:
      case Character.DASH_PUNCTUATION:
      case Character.START_PUNCTUATION:
      case Character.END_PUNCTUATION:
      case Character.INITIAL_QUOTE_PUNCTUATION:
      case Character.FINAL_QUOTE_PUNCTUATION:
      case Character.OTHER_PUNCTUATION:
        return 2;
      case Character.CURRENCY_SYMBOL:
        return 4;
      case Character.MATH_SYMBOL:
      case Character.MODIFIER_SYMBOL:
      case Character.OTHER_SYMBOL:
        return 3;
      default:
        return Character.isWhitespace (c) ? 1 : 0;
    }
  }

  private boolean _isVariable (final int c)
  {
    final int nGroup = _getVariableGroup (c);
    return nGroup > 0 && nGroup <= m_nMaxVariable;
  }

  @NonNull
  private static String _lengthPrefix (final int nLength)
  {
    if (nLength <= 8)
      return Integer.toString (nLength);
    final String sLen = Integer.toString (nLength);
    return "9" + _lengthPrefix (sLen.length ()) + sLen;
  }

  /**
   * Rewrite every run of decimal digits so that plain collation sorts it by
   * numeric value: leading zeros are dropped and the run is prefixed by its
   * length (lengths of 9 and more get a recursive length prefix after '9').
   */
  @NonNull
  private static String _numericTransform (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length () + 8);
    int i = 0;
    while (i < s.length ())
    {
      final int c = s.codePointAt (i);
      if (!Character.isDigit (c))
      {
        aSB.appendCodePoint (c);
        i += Character.charCount (c);
        continue;
      }
      final StringBuilder aDigits = new StringBuilder ();
      while (i < s.length ())
      {
        final int d = s.codePointAt (i);
        if (!Character.isDigit (d))
          break;
        aDigits.append ((char) ('0' + Character.digit (d, 10)));
        i += Character.charCount (d);
      }
      int nStart = 0;
      while (nStart < aDigits.length () - 1 && aDigits.charAt (nStart) == '0')
        nStart++;
      final String sDigits = aDigits.substring (nStart);
      aSB.append (_lengthPrefix (sDigits.length ())).append (sDigits);
    }
    return aSB.toString ();
  }

  @NonNull
  private String _strip (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length ());
    s.codePoints ().filter (c -> !_isVariable (c)).forEach (aSB::appendCodePoint);
    return aSB.toString ();
  }

  @NonNull
  private static String _reverse (@NonNull final String s)
  {
    final int [] aCP = s.codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder (s.length ());
    for (int i = aCP.length - 1; i >= 0; --i)
      aSB.appendCodePoint (aCP[i]);
    return aSB.toString ();
  }

  /** A growing int array. */
  private static final class IntList
  {
    private int [] m_aData = new int [32];
    private int m_nSize;

    void add (final int n)
    {
      if (m_nSize == m_aData.length)
        m_aData = Arrays.copyOf (m_aData, m_nSize * 2);
      m_aData[m_nSize++] = n;
    }

    @NonNull
    int [] toArray ()
    {
      return Arrays.copyOf (m_aData, m_nSize);
    }
  }

  /** Append a JDK collation key; every unit is shifted by one above 0. */
  private static void _appendJavaKey (@NonNull final IntList aList,
                                      @NonNull final Collator aCollator,
                                      @NonNull final String s)
  {
    final byte [] aBytes = aCollator.getCollationKey (s).toByteArray ();
    for (int i = 0; i + 1 < aBytes.length; i += 2)
      aList.add ((((aBytes[i] & 0xFF) << 8) | (aBytes[i + 1] & 0xFF)) + 1);
  }

  /** Append the primary weights the JDK gives a run of characters. */
  private void _appendJavaPrimary (@NonNull final IntList aList, @NonNull final String s)
  {
    if (s.isEmpty ())
      return;
    final byte [] aBytes = m_aPrimary.getCollationKey (s).toByteArray ();
    for (int i = 0; i + 1 < aBytes.length; i += 2)
    {
      final int nUnit = ((aBytes[i] & 0xFF) << 8) | (aBytes[i + 1] & 0xFF);
      if (nUnit == 0)
        break;
      aList.add (nUnit + PRIMARY_OFFSET);
    }
  }

  /**
   * The primary level. The JDK collators treat spaces and punctuation as
   * ignorable, whereas UCA gives them primary weights below the digits and
   * letters (alternate=non-ignorable, the default). So white space,
   * punctuation and symbols get their own low weights (by group, then code
   * point) and the runs between them are weighted by the JDK collator. With
   * alternate=shifted/blanked the variable characters are ignored here.
   */
  private void _appendPrimary (@NonNull final IntList aList, @NonNull final String s)
  {
    int nSegmentStart = 0;
    int i = 0;
    while (i < s.length ())
    {
      final int c = s.codePointAt (i);
      final int nGroup = _getVariableGroup (c);
      if (nGroup > 0)
      {
        _appendJavaPrimary (aList, s.substring (nSegmentStart, i));
        if (nGroup > m_nMaxVariable)
          aList.add ((nGroup << 21 | c) + 1);
        nSegmentStart = i + Character.charCount (c);
      }
      i += Character.charCount (c);
    }
    _appendJavaPrimary (aList, s.substring (nSegmentStart));
  }

  @NonNull
  private int [] _getKey (@NonNull final String sValue)
  {
    final String s1 = m_bNumeric ? _numericTransform (sValue) : sValue;
    final String sBase = m_nMaxVariable > 0 ? _strip (s1) : s1;
    final IntList aList = new IntList ();
    _appendPrimary (aList, s1);
    if (m_nLevel >= 2 || m_bCaseLevel)
    {
      aList.add (0);
      if (m_nLevel >= 2)
        _appendJavaKey (aList, m_aSecondary, m_bBackwards ? _reverse (sBase) : sBase);
      aList.add (0);
      if (m_bUpperFirst || m_bCaseLevel)
        sBase.codePoints ().forEach (c -> {
          if (Character.isUpperCase (c))
            aList.add (m_bUpperFirst ? 1 : 2);
          else
            if (Character.isLowerCase (c))
              aList.add (m_bUpperFirst ? 2 : 1);
        });
      aList.add (0);
      if (m_nLevel >= 3)
        _appendJavaKey (aList, m_aTertiary, sBase);
    }
    if (m_bQuaternary)
    {
      // Quaternary level of alternate=shifted: a variable character weighs by
      // its group and code point, every other one the maximum
      aList.add (0);
      s1.codePoints ().forEach (c -> aList.add (_isVariable (c) ? (_getVariableGroup (c) << 21 | c) + 1 : 0xFFFFFF));
    }
    if (m_bIdentical)
    {
      aList.add (0);
      sValue.codePoints ().forEach (c -> aList.add (c + 1));
    }
    return aList.toArray ();
  }

  /**
   * A UCA "version" parameter is plausible if its major number is one the
   * Unicode Collation Algorithm has been published for.
   */
  private static boolean _isVersionPlausible (@NonNull final String sVersion)
  {
    final int nDot = sVersion.indexOf ('.');
    final String sMajor = nDot < 0 ? sVersion : sVersion.substring (0, nDot);
    if (sMajor.isEmpty () || sMajor.length () > 2)
      return false;
    for (int i = 0; i < sMajor.length (); ++i)
      if (sMajor.charAt (i) < '0' || sMajor.charAt (i) > '9')
        return false;
    final int n = Integer.parseInt (sMajor);
    return n >= 1 && n <= 20;
  }

  @NonNull
  private static XPathException _unsupported (@NonNull final String sUri, @NonNull final String sWhat)
  {
    return XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "': " + sWhat);
  }

  @NonNull
  public String getUri ()
  {
    return m_sUri;
  }

  public int compare (@NonNull final String sLeft, @NonNull final String sRight)
  {
    return Arrays.compare (_getKey (sLeft), _getKey (sRight));
  }

  @NonNull
  public Object getCollationKey (@NonNull final String sValue)
  {
    return new CollationSupport.IntKey (_getKey (sValue));
  }

  @NonNull
  public byte [] getCollationKeyBytes (@NonNull final String sValue)
  {
    return CollationSupport.toBytes (_getKey (sValue));
  }

  @Nullable
  public int [] findSubstring (@NonNull final String sValue, @NonNull final String sSearch)
  {
    return CollationSupport.findSubstring (this, sValue, sSearch, m_bNumeric);
  }

  public boolean startsWith (@NonNull final String sValue, @NonNull final String sPrefix)
  {
    return CollationSupport.startsWith (this, sValue, sPrefix, m_bNumeric);
  }

  public boolean endsWith (@NonNull final String sValue, @NonNull final String sSuffix)
  {
    return CollationSupport.endsWith (this, sValue, sSuffix, m_bNumeric);
  }

  /**
   * Create a UCA collation from its URI.
   *
   * @param sUri
   *        The full URI starting with {@link #UCA_URI}.
   * @return The collation.
   * @throws XPathException
   *         FOCH0002 if a parameter is not supported and fallback=no
   */
  @NonNull
  static UcaCollation create (@NonNull final String sUri)
  {
    final int nQuery = sUri.indexOf ('?');
    final String sQuery = nQuery < 0 ? "" : sUri.substring (nQuery + 1);
    final ICommonsMap <String, String> aParams = new CommonsHashMap <> ();
    for (final String sPart : sQuery.split (";"))
    {
      if (sPart.isEmpty ())
        continue;
      final int nEq = sPart.indexOf ('=');
      if (nEq < 0)
        aParams.put (sPart, "");
      else
        aParams.put (sPart.substring (0, nEq), sPart.substring (nEq + 1));
    }
    boolean bFallback = true;
    final String sFallback = aParams.get ("fallback");
    if (sFallback != null)
    {
      if (!"yes".equals (sFallback) && !"no".equals (sFallback))
        throw _unsupported (sUri, "invalid fallback value '" + sFallback + "'");
      bFallback = "yes".equals (sFallback);
    }

    Locale aLocale = Locale.ROOT;
    int nLevel = 3;
    boolean bUpperFirst = false;
    boolean bCaseLevel = false;
    boolean bBackwards = false;
    boolean bBackwardsSet = false;
    boolean bNumeric = false;
    String sAlternate = "non-ignorable";
    for (final Map.Entry <String, String> aEntry : aParams.entrySet ())
    {
      final String sKey = aEntry.getKey ();
      final String sValue = aEntry.getValue ();
      switch (sKey)
      {
        case "fallback":
          break;
        case "lang":
        {
          final Locale aLang = sValue.isEmpty () ? null : Locale.forLanguageTag (sValue);
          if (aLang != null && !aLang.getLanguage ().isEmpty ())
            aLocale = aLang;
          else
            if (!bFallback)
              throw _unsupported (sUri, "invalid language '" + sValue + "'");
          break;
        }
        case "version":
          // Accepted, not applied; an impossible version is an error with
          // fallback=no
          if (!bFallback && !_isVersionPlausible (sValue))
            throw _unsupported (sUri, "UCA version '" + sValue + "'");
          break;
        case "reorder":
          if (!bFallback)
            throw _unsupported (sUri, "reorder is not supported");
          break;
        default:
        {
          final ICommonsSet <String> aAllowed = VALUES.get (sKey);
          if (aAllowed == null || !aAllowed.contains (sValue))
          {
            if (!bFallback)
              throw _unsupported (sUri, "parameter " + sKey + "=" + sValue);
            break;
          }
          switch (sKey)
          {
            case "strength":
              switch (sValue)
              {
                case "primary":
                case "1":
                  nLevel = 1;
                  break;
                case "secondary":
                case "2":
                  nLevel = 2;
                  break;
                case "tertiary":
                case "3":
                  nLevel = 3;
                  break;
                case "quaternary":
                case "4":
                  nLevel = 4;
                  break;
                default:
                  nLevel = 5;
                  break;
              }
              break;
            case "caseLevel":
              bCaseLevel = "yes".equals (sValue);
              break;
            case "backwards":
              bBackwards = "yes".equals (sValue);
              bBackwardsSet = true;
              break;
            case "alternate":
              sAlternate = sValue;
              break;
            case "numeric":
              bNumeric = "yes".equals (sValue);
              break;
            case "caseFirst":
              bUpperFirst = "upper".equals (sValue);
              break;
            default:
              // normalization: the JDK collators always decompose canonically
              break;
          }
          break;
        }
      }
    }
    // French as written in Canada orders accents from the end of the word
    if (!bBackwardsSet && "fr".equals (aLocale.getLanguage ()) && "CA".equals (aLocale.getCountry ()))
      bBackwards = true;

    int nMaxVariable = 0;
    if (!"non-ignorable".equals (sAlternate))
    {
      final String sMaxVariable = aParams.get ("maxVariable");
      nMaxVariable = 2;
      if ("space".equals (sMaxVariable))
        nMaxVariable = 1;
      else
        if ("symbol".equals (sMaxVariable))
          nMaxVariable = 3;
        else
          if ("currency".equals (sMaxVariable))
            nMaxVariable = 4;
    }
    return new UcaCollation (sUri,
                             aLocale,
                             nLevel,
                             bUpperFirst,
                             bCaseLevel,
                             bBackwards,
                             bNumeric,
                             nMaxVariable,
                             "blanked".equals (sAlternate));
  }
}
