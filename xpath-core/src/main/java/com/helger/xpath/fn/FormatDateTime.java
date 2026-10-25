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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.DateTimeCalendar;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmDateTimeValue;

/**
 * The picture-string language of <code>fn:format-date</code>,
 * <code>fn:format-time</code> and <code>fn:format-dateTime</code> (F&amp;O 3.1
 * section 9.8): components, presentation and width modifiers, languages
 * (English plus German, French and Italian names and words), calendars (AD and
 * ISO; other known calendars fall back to AD with a "[Calendar: AD]" marker)
 * and the <code>$place</code> argument (Olson timezone names, DST included).
 *
 * @author Philip Helger
 */
@Immutable
public final class FormatDateTime
{
  private static final String COMPONENT_LETTERS = "YMDdFWwHhPmsfZzCE";
  /** Width modifiers are clamped to this, so a crafted picture cannot exhaust memory. */
  private static final int MAX_WIDTH = 1000;

  private static final Set <String> KNOWN_CALENDARS = Set.of ("AD",
                                                              "AH",
                                                              "AME",
                                                              "AM",
                                                              "AP",
                                                              "AS",
                                                              "BE",
                                                              "CB",
                                                              "CE",
                                                              "CL",
                                                              "CS",
                                                              "EE",
                                                              "FE",
                                                              "ISO",
                                                              "JE",
                                                              "KE",
                                                              "KY",
                                                              "ME",
                                                              "MS",
                                                              "NS",
                                                              "OS",
                                                              "RS",
                                                              "SE",
                                                              "SH",
                                                              "SS",
                                                              "TE",
                                                              "VE",
                                                              "VS");

  private static final String [] MONTHS_EN = { "January",
                                               "February",
                                               "March",
                                               "April",
                                               "May",
                                               "June",
                                               "July",
                                               "August",
                                               "September",
                                               "October",
                                               "November",
                                               "December" };
  private static final String [] DAYS_EN = { "Monday",
                                             "Tuesday",
                                             "Wednesday",
                                             "Thursday",
                                             "Friday",
                                             "Saturday",
                                             "Sunday" };
  private static final String [] MONTHS_DE = { "Januar",
                                               "Februar",
                                               "März",
                                               "April",
                                               "Mai",
                                               "Juni",
                                               "Juli",
                                               "August",
                                               "September",
                                               "Oktober",
                                               "November",
                                               "Dezember" };
  private static final String [] DAYS_DE = { "Montag",
                                             "Dienstag",
                                             "Mittwoch",
                                             "Donnerstag",
                                             "Freitag",
                                             "Samstag",
                                             "Sonntag" };
  private static final String [] MONTHS_FR = { "janvier",
                                               "février",
                                               "mars",
                                               "avril",
                                               "mai",
                                               "juin",
                                               "juillet",
                                               "août",
                                               "septembre",
                                               "octobre",
                                               "novembre",
                                               "décembre" };
  private static final String [] DAYS_FR = { "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche" };
  private static final String [] MONTHS_IT = { "gennaio",
                                               "febbraio",
                                               "marzo",
                                               "aprile",
                                               "maggio",
                                               "giugno",
                                               "luglio",
                                               "agosto",
                                               "settembre",
                                               "ottobre",
                                               "novembre",
                                               "dicembre" };
  private static final String [] DAYS_IT = { "lunedì",
                                             "martedì",
                                             "mercoledì",
                                             "giovedì",
                                             "venerdì",
                                             "sabato",
                                             "domenica" };

  private static final DateTimeFormatter ZONE_NAME_FORMATTER = DateTimeFormatter.ofPattern ("zzz", Locale.US);

  @PresentForCodeCoverage
  private static final FormatDateTime INSTANCE = new FormatDateTime ();

  /** The per-call decisions for $language, $calendar and $place. */
  private static final class Options
  {
    private boolean m_bLangFallback;
    private String m_sCalPrefix = "";
    private boolean m_bUsedLang;
    // null = English
    private String m_sLang;
    // the zone abbreviation if a $place moved the value
    private String m_sZoneName;
  }

  /** A width modifier: 0 means unspecified. */
  private record Width (String modifier, int min, int max)
  {}

  /** A parsed timezone digit pattern. */
  private record TzPattern (int hourDigits, int minuteDigits, String separator, int zero)
  {}

  /** A parsed fractional-seconds pattern. */
  private static final class FracPattern
  {
    private int m_nMandatory;
    private int m_nTotal;
    private int m_nZero = '0';
    private final ICommonsMap <Integer, Integer> m_aSeps = new CommonsHashMap <> ();
  }

  private FormatDateTime ()
  {}

  @NonNull
  private static XPathException _err (@NonNull final String sMsg)
  {
    return new XPathException ("FOFD1340", sMsg);
  }

  private static boolean _isNCNameStart (final int c)
  {
    return c == '_' ||
           c >= 'A' && c <= 'Z' ||
           c >= 'a' && c <= 'z' ||
           c >= 0xC0 && c <= 0xD6 ||
           c >= 0xD8 && c <= 0xF6 ||
           c >= 0xF8 && c <= 0x2FF ||
           c >= 0x370 && c <= 0x37D ||
           c >= 0x37F && c <= 0x1FFF ||
           c >= 0x200C && c <= 0x200D ||
           c >= 0x2070 && c <= 0x218F ||
           c >= 0x2C00 && c <= 0x2FEF ||
           c >= 0x3001 && c <= 0xD7FF ||
           c >= 0xF900 && c <= 0xFDCF ||
           c >= 0xFDF0 && c <= 0xFFFD ||
           c >= 0x10000 && c <= 0xEFFFF;
  }

  private static boolean _isNCNameChar (final int c)
  {
    return _isNCNameStart (c) ||
           c == '-' ||
           c == '.' ||
           c >= '0' && c <= '9' ||
           c == 0xB7 ||
           c >= 0x300 && c <= 0x36F ||
           c >= 0x203F && c <= 0x2040;
  }

  /**
   * @param s
   *        Candidate.
   * @return <code>true</code> if it is a valid XML NCName.
   */
  static boolean isNCName (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    final int [] aCPs = s.codePoints ().toArray ();
    if (!_isNCNameStart (aCPs[0]))
      return false;
    for (int i = 1; i < aCPs.length; ++i)
      if (!_isNCNameChar (aCPs[i]))
        return false;
    return true;
  }

  /**
   * Validate the $calendar argument (a lexical QName or Q{uri}local) and
   * return the "[Calendar: AD]" prefix for a calendar that is not implemented.
   */
  @NonNull
  private static String _calendarPrefix (@NonNull final String sCalendar)
  {
    final String sCal = sCalendar.strip ();
    boolean bNamespaced = false;
    String sLocal = sCal;
    if (sCal.startsWith ("Q{"))
    {
      final int i = sCal.indexOf ('}');
      if (i < 0)
        throw _err ("Invalid calendar name '" + sCalendar + "'");
      bNamespaced = i > 2;
      sLocal = sCal.substring (i + 1);
    }
    else
    {
      final int i = sCal.indexOf (':');
      if (i >= 0)
      {
        if (!isNCName (sCal.substring (0, i)))
          throw _err ("Invalid calendar name '" + sCalendar + "'");
        bNamespaced = true;
        sLocal = sCal.substring (i + 1);
      }
    }
    if (!isNCName (sLocal))
      throw _err ("Invalid calendar name '" + sCalendar + "'");
    if (bNamespaced)
      return "[Calendar: AD]";
    if (sLocal.equals ("AD") || sLocal.equals ("ISO"))
      return "";
    if (KNOWN_CALENDARS.contains (sLocal))
      return "[Calendar: AD]";
    throw _err ("Unknown calendar '" + sCalendar + "'");
  }

  @NonNull
  private static String [] _monthNames (@NonNull final Options aOpts)
  {
    if (aOpts.m_sLang == null)
      return MONTHS_EN;
    switch (aOpts.m_sLang)
    {
      case "de":
        return MONTHS_DE;
      case "fr":
        return MONTHS_FR;
      default:
        return MONTHS_IT;
    }
  }

  @NonNull
  private static String [] _dayNames (@NonNull final Options aOpts)
  {
    if (aOpts.m_sLang == null)
      return DAYS_EN;
    switch (aOpts.m_sLang)
    {
      case "de":
        return DAYS_DE;
      case "fr":
        return DAYS_FR;
      default:
        return DAYS_IT;
    }
  }

  private static int _parsePositiveInt (@NonNull final String s)
  {
    if (s.isEmpty () || s.length () > 9)
      return -1;
    for (final char c : s.toCharArray ())
      if (c < '0' || c > '9')
        return -1;
    return Integer.parseInt (s);
  }

  /**
   * Separate the presentation modifier from an optional trailing ",min-max"
   * width modifier, split at the last comma whose tail parses as a width.
   */
  @NonNull
  private static Width _splitWidth (@NonNull final String sRest)
  {
    final int k = sRest.lastIndexOf (',');
    if (k < 0)
      return new Width (sRest, 0, 0);
    final String sTail = sRest.substring (k + 1);
    final int nDash = sTail.indexOf ('-');
    final String [] aParts = nDash < 0 ? new String [] { sTail }
                                       : new String [] { sTail.substring (0, nDash), sTail.substring (nDash + 1) };
    final int [] aVals = new int [aParts.length];
    for (int i = 0; i < aParts.length; ++i)
    {
      final String p = aParts[i];
      if (p.equals ("*"))
        continue;
      final int n = _parsePositiveInt (p);
      if (n < 0)
        return new Width (sRest, 0, 0);
      if (n == 0)
        throw _err ("Zero width modifier");
      aVals[i] = Math.min (n, MAX_WIDTH);
    }
    final int nMin = aVals[0];
    final int nMax = aVals.length == 2 ? aVals[1] : 0;
    if (nMin > 0 && nMax > 0 && nMin > nMax)
      throw _err ("Width modifier minimum " + nMin + " exceeds maximum " + nMax);
    return new Width (sRest.substring (0, k), nMin, nMax);
  }

  @NonNull
  private static String _padRight (@NonNull final String s, final int nMin)
  {
    final int n = s.codePointCount (0, s.length ());
    return n < nMin ? s + " ".repeat (nMin - n) : s;
  }

  private static int _countDigits (@NonNull final String s)
  {
    return (int) s.codePoints ().filter (FormatInteger::isDigit).count ();
  }

  private static int _countChar (@NonNull final String s, final char c)
  {
    int ret = 0;
    for (final char x : s.toCharArray ())
      if (x == c)
        ret++;
    return ret;
  }

  /** @return <code>[min, max]</code>, max 0 = unbounded. */
  @NonNull
  private static int [] _effectiveWidths (final int nMandatory, final int nTotal, final int nWMin, final int nWMax)
  {
    int nMin = Math.max (nMandatory, nWMin);
    int nMax = 0;
    if (nTotal > 1)
      nMax = nTotal;
    if (nWMax > 0)
      nMax = nWMax;
    if (nMax > 0 && nMax < nMin)
      nMax = nMin;
    return new int [] { nMin, nMax };
  }

  @NonNull
  private static String _applyNameModifier (@NonNull final String sName, @NonNull final String sMod)
  {
    if (sMod.startsWith ("Nn"))
      return FormatWords.titleCase (sName);
    if (sMod.startsWith ("N"))
      return sName.toUpperCase (Locale.ROOT);
    if (sMod.startsWith ("n"))
      return sName.toLowerCase (Locale.ROOT);
    return FormatWords.titleCase (sName);
  }

  @NonNull
  private static String _presentNumberOrName (final char cComp,
                                              final long nNum,
                                              @Nullable final String sName,
                                              @NonNull final String sModifier,
                                              final int nWMin,
                                              final int nWMax,
                                              @NonNull final Options aOpts)
  {
    String sMod = sModifier;
    boolean bOrdinal = false;
    final int nLen = sMod.length ();
    if (nLen > 1)
    {
      final char cLast = sMod.charAt (nLen - 1);
      if (cLast == 'o')
      {
        bOrdinal = true;
        sMod = sMod.substring (0, nLen - 1);
      }
      else
        if (cLast == 't' || cLast == 'c')
          sMod = sMod.substring (0, nLen - 1);
        else
          if (cLast == 'a')
          {
            final char p = sMod.charAt (nLen - 2);
            if (p == '#' || p >= '0' && p <= '9')
              sMod = sMod.substring (0, nLen - 1);
          }
    }
    long nValue = nNum;
    // A maximum width on the year keeps its low-order digits whatever the
    // numbering
    if (cComp == 'Y' && nWMax > 0 && nWMax < 10)
    {
      long p = 1;
      for (int i = 0; i < nWMax; ++i)
        p *= 10;
      nValue %= p;
    }
    if ((sMod.indexOf ('N') >= 0 || sMod.indexOf ('n') >= 0) && sName != null)
    {
      aOpts.m_bUsedLang = true;
      String s = _applyNameModifier (sName, sMod);
      if (nWMax > 0 && s.codePointCount (0, s.length ()) > nWMax)
      {
        // A name that must be shortened takes its conventional three-letter
        // abbreviation when that fits the width, else it is truncated
        final int nCut = nWMin <= 3 && 3 <= nWMax ? 3 : nWMax;
        s = s.substring (0, s.offsetByCodePoints (0, nCut));
      }
      // Section 9.8.4.3: "If FN is shorter than the minimum width, then it is
      // padded by appending spaces to the end of the name." That section
      // covers the integer-valued components only, so of the named ones M
      // and F, not the am/pm marker P
      if (cComp == 'M' || cComp == 'F')
        s = _padRight (s, nWMin);
      return s;
    }
    if (sMod.equals ("I") || sMod.equals ("i"))
    {
      if (nValue > 0)
        return _padRight (FormatInteger.toRoman (nValue, sMod.equals ("I")), nWMin);
    }
    else
      if (sMod.equals ("a") || sMod.equals ("A"))
      {
        if (nValue > 0)
          return _padRight (FormatInteger.toAlphabetic (nValue, FormatInteger.getLatinAlphabet (sMod.equals ("A"))),
                            nWMin);
      }
      else
        if (sMod.equals ("w") || sMod.equals ("W") || sMod.equals ("Ww"))
        {
          aOpts.m_bUsedLang = true;
          return FormatWords.spell (nValue, sMod, bOrdinal, aOpts.m_sLang, "");
        }
    if (bOrdinal)
      aOpts.m_bUsedLang = true;
    if (sMod.isEmpty ())
      sMod = "1";
    FormatInteger.DigitPattern aDP = FormatInteger.parseDigitPattern (sMod);
    if (aDP == null)
    {
      if (FormatInteger.isDigitPatternPicture (sMod) || sMod.indexOf ('#') >= 0)
        throw _err ("Invalid presentation modifier [" + cComp + sMod + "]");
      aDP = FormatInteger.parseDigitPattern ("1");
      sMod = "1";
    }
    final int [] aW = _effectiveWidths (aDP.getMandatory (),
                                        _countDigits (sMod) + _countChar (sMod, '#'),
                                        nWMin,
                                        nWMax);
    final boolean bNeg = nValue < 0;
    String s = Long.toString (Math.abs (nValue));
    // Only the year is cut to its low-order digits (section 9.8.4.4); for
    // every other component "The maximum width, if specified, is ignored"
    // (section 9.8.4.3)
    if (cComp == 'Y' && aW[1] > 0 && s.length () > aW[1])
      s = s.substring (s.length () - aW[1]);
    if (s.length () < aW[0])
      s = "0".repeat (aW[0] - s.length ()) + s;
    s = aDP.render (s);
    if (bOrdinal)
      s += FormatWords.getOrdinalSuffix (nValue);
    return bNeg ? "-" + s : s;
  }

  @Nullable
  private static FracPattern _parseFracPattern (@NonNull final String sPattern)
  {
    final FracPattern ret = new FracPattern ();
    final int [] aCPs = sPattern.codePoints ().toArray ();
    int nFamZero = -1;
    boolean bSawOptional = false;
    boolean bLastWasSep = false;
    for (int i = 0; i < aCPs.length; ++i)
    {
      final int c = aCPs[i];
      if (c == '#')
      {
        bSawOptional = true;
        ret.m_nTotal++;
        bLastWasSep = false;
      }
      else
        if (FormatInteger.isDigit (c))
        {
          if (bSawOptional)
            return null;
          final int z = c - FormatInteger.getDigitValue (c);
          if (nFamZero < 0)
            nFamZero = z;
          else
            if (z != nFamZero)
              return null;
          ret.m_nMandatory++;
          ret.m_nTotal++;
          bLastWasSep = false;
        }
        else
        {
          if (i == 0 || i == aCPs.length - 1 || bLastWasSep || Character.isLetter (c) || FormatInteger.isNumber (c))
            return null;
          ret.m_aSeps.put (Integer.valueOf (ret.m_nTotal), Integer.valueOf (c));
          bLastWasSep = true;
        }
    }
    if (ret.m_nTotal == 0)
      return null;
    if (nFamZero >= 0)
      ret.m_nZero = nFamZero;
    return ret;
  }

  /**
   * Render the fractional seconds: the significant digits truncated to the
   * maximum width and zero-filled on the right to the minimum.
   */
  @NonNull
  private static String _formatFractional (@NonNull final BigDecimal aSecond,
                                           @NonNull final String sModifier,
                                           final int nWMin,
                                           final int nWMax)
  {
    String sMod = sModifier.isEmpty () ? "1" : sModifier;
    FracPattern aFP = _parseFracPattern (sMod);
    if (aFP == null)
    {
      if (FormatInteger.isDigitPatternPicture (sMod) || sMod.indexOf ('#') >= 0)
        throw _err ("Invalid presentation modifier [f" + sMod + "]");
      sMod = "1";
      aFP = _parseFracPattern (sMod);
    }
    final int [] aW = _effectiveWidths (aFP.m_nMandatory, aFP.m_nTotal, nWMin, nWMax);
    final BigDecimal aFrac = aSecond.subtract (aSecond.setScale (0, RoundingMode.FLOOR));
    String s = "";
    if (aFrac.signum () != 0)
    {
      final String sPlain = aFrac.toPlainString ();
      s = sPlain.substring (sPlain.indexOf ('.') + 1);
    }
    if (s.length () < 9)
      s = s + "0".repeat (9 - s.length ());
    if (aW[1] > 0 && s.length () > aW[1])
      s = s.substring (0, aW[1]);
    int nEnd = s.length ();
    while (nEnd > aW[0] && s.charAt (nEnd - 1) == '0')
      nEnd--;
    s = s.substring (0, nEnd);
    if (s.length () < aW[0])
      s = s + "0".repeat (aW[0] - s.length ());
    final int [] aDigits = FormatInteger.mapDigitsToFamily (s, aFP.m_nZero).codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aDigits.length; ++i)
    {
      final Integer aSep = aFP.m_aSeps.get (Integer.valueOf (i));
      if (aSep != null && i > 0)
        aSB.appendCodePoint (aSep.intValue ());
      aSB.appendCodePoint (aDigits[i]);
    }
    return aSB.toString ();
  }

  /**
   * Split a timezone digit pattern into hour digits, minute digits, their
   * separator and the digit family; no digits at all means "00:00".
   */
  @NonNull
  private static TzPattern _parseTzPattern (@NonNull final String sMod)
  {
    int nH = 0;
    int nM = 0;
    int nZero = -1;
    boolean bInMinutes = false;
    final StringBuilder aSep = new StringBuilder ();
    for (final int c : sMod.codePoints ().toArray ())
    {
      if (FormatInteger.isDigit (c) || c == '#')
      {
        if (c != '#' && nZero < 0)
          nZero = c - FormatInteger.getDigitValue (c);
        if (bInMinutes)
          nM++;
        else
          nH++;
      }
      else
      {
        if (nH > 0)
          bInMinutes = true;
        if (bInMinutes && nM == 0)
          aSep.appendCodePoint (c);
      }
    }
    if (nH == 0)
      return new TzPattern (2, 2, ":", '0');
    return new TzPattern (nH, nM, nM > 0 ? aSep.toString () : "", nZero < 0 ? '0' : nZero);
  }

  @NonNull
  private static String _pad (final int n, final int nWidth)
  {
    final String s = Integer.toString (n);
    return s.length () < nWidth ? "0".repeat (nWidth - s.length ()) + s : s;
  }

  /** Render [Z]/[z] per F&amp;O 9.8.4.4. */
  @NonNull
  private static String _formatTimezone (@NonNull final XdmDateTimeValue aValue,
                                         final char cComp,
                                         @NonNull final String sModifier,
                                         final int nWMax)
  {
    String sMod = sModifier;
    boolean bTraditional = false;
    if (sMod.length () > 1 && sMod.charAt (sMod.length () - 1) == 't')
    {
      bTraditional = true;
      sMod = sMod.substring (0, sMod.length () - 1);
    }
    final boolean bMilitary = sMod.equals ("Z");
    if (!aValue.hasTimezone ())
      return bMilitary ? "J" : "";
    int nOff = aValue.getTimezoneMinutes ();
    final String sSign = nOff < 0 ? "-" : "+";
    nOff = Math.abs (nOff);
    final int nHH = nOff / 60;
    final int nMM = nOff % 60;
    if (bMilitary)
    {
      if (nMM == 0 && nHH <= 12)
      {
        if (nHH == 0)
          return "Z";
        if (sSign.equals ("+"))
          return String.valueOf ((char) (nHH <= 9 ? 'A' + nHH - 1 : 'K' + nHH - 10));
        return String.valueOf ((char) ('N' + nHH - 1));
      }
      sMod = "00:00";
    }
    if (bTraditional && nOff == 0 && cComp == 'Z')
      return "Z";
    final TzPattern aTz = _parseTzPattern (sMod);
    int nHDigits = aTz.hourDigits ();
    int nMDigits = aTz.minuteDigits ();
    String sSep = aTz.separator ();
    final int nZero = aTz.zero ();
    if (sMod.isEmpty () && nWMax >= 1 && nWMax <= 4)
    {
      nHDigits = 2;
      nMDigits = 0;
      sSep = "";
    }
    String s;
    if (sSep.isEmpty () && nHDigits <= 2)
    {
      s = _pad (nHH, nHDigits);
      if (nMM != 0)
        s += ":" + _pad (nMM, 2);
    }
    else
      if (sSep.isEmpty ())
        s = _pad (nHH * 100 + nMM, nHDigits);
      else
        s = _pad (nHH, nHDigits) + sSep + _pad (nMM, nMDigits);
    s = sSign + FormatInteger.mapDigitsToFamily (s, nZero);
    return cComp == 'z' ? "GMT" + s : s;
  }

  @NonNull
  private static String _formatComponent (@NonNull final XdmDateTimeValue aValue,
                                          @NonNull final EAtomicType eType,
                                          @NonNull final String sMarker,
                                          @NonNull final Options aOpts)
  {
    if (sMarker.isEmpty ())
      throw _err ("Empty variable marker in date/time picture");
    final int nComp = sMarker.codePointAt (0);
    if (nComp > 0x7F || COMPONENT_LETTERS.indexOf (nComp) < 0)
      throw _err ("Unknown component specifier [" + new String (Character.toChars (nComp)) + "] in date/time picture");
    final char cComp = (char) nComp;
    if (eType == EAtomicType.DATE && "HhPmsf".indexOf (cComp) >= 0)
      throw new XPathException ("FOFD1350", "Component [" + cComp + "] is not available in an xs:date");
    if (eType == EAtomicType.TIME && "YMDdFWwE".indexOf (cComp) >= 0)
      throw new XPathException ("FOFD1350", "Component [" + cComp + "] is not available in an xs:time");
    final Width aWidth = _splitWidth (sMarker.substring (1));
    String sMod = aWidth.modifier ();
    final int nWMin = aWidth.min ();
    final int nWMax = aWidth.max ();

    long nNum = 0;
    String sName = null;
    switch (cComp)
    {
      case 'Y':
        nNum = Math.abs (aValue.getYear ());
        break;
      case 'M':
        nNum = aValue.getMonth ();
        sName = _monthNames (aOpts)[aValue.getMonth () - 1];
        break;
      case 'D':
        nNum = aValue.getDay ();
        break;
      case 'd':
        nNum = DateTimeCalendar.getDayOfYear (aValue.getYear (), aValue.getMonth (), aValue.getDay ());
        break;
      case 'F':
      {
        final int nDow = DateTimeCalendar.getIsoDayOfWeek (aValue.getEpochDay ());
        nNum = nDow;
        sName = _dayNames (aOpts)[nDow - 1];
        // F&O 3.1 section 9.8.4.1: the default presentation of F is n (a
        // lower-case name)
        if (sMod.isEmpty ())
          sMod = "n";
        break;
      }
      case 'H':
        nNum = aValue.getHour ();
        break;
      case 'h':
        nNum = aValue.getHour () % 12;
        if (nNum == 0)
          nNum = 12;
        break;
      case 'P':
        sName = aValue.getHour () >= 12 ? "pm" : "am";
        if (sMod.isEmpty ())
          sMod = "n";
        break;
      case 'm':
        nNum = aValue.getMinute ();
        if (sMod.isEmpty ())
          sMod = "01";
        break;
      case 's':
        nNum = aValue.getSecond ().intValue ();
        if (sMod.isEmpty ())
          sMod = "01";
        break;
      case 'f':
        return _formatFractional (aValue.getSecond (), sMod, nWMin, nWMax);
      case 'Z':
      case 'z':
        if (sMod.equals ("N") && aOpts.m_sZoneName != null)
          return aOpts.m_sZoneName;
        return _formatTimezone (aValue, cComp, sMod, nWMax);
      case 'E':
        aOpts.m_bUsedLang = true;
        return aValue.getYear () < 0 ? "BC" : "AD";
      case 'C':
        aOpts.m_bUsedLang = true;
        return "Gregorian";
      case 'W':
        nNum = DateTimeCalendar.getIsoWeekOfYear (aValue.getYear (), aValue.getMonth (), aValue.getDay ());
        break;
      case 'w':
        nNum = DateTimeCalendar.getWeekOfMonth (aValue.getYear (), aValue.getMonth (), aValue.getDay ());
        break;
      default:
        break;
    }
    return _presentNumberOrName (cComp, nNum, sName, sMod, nWMin, nWMax, aOpts);
  }

  @NonNull
  private static String _stripWhitespace (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final int c : s.codePoints ().toArray ())
      if (!Character.isWhitespace (c) && !Character.isSpaceChar (c))
        aSB.appendCodePoint (c);
    return aSB.toString ();
  }

  @NonNull
  private static String _formatPicture (@NonNull final XdmDateTimeValue aValue,
                                        @NonNull final EAtomicType eType,
                                        @NonNull final String sPicture,
                                        @NonNull final Options aOpts)
  {
    final StringBuilder aSB = new StringBuilder (aOpts.m_sCalPrefix);
    final int nLen = sPicture.length ();
    int i = 0;
    while (i < nLen)
    {
      final char c = sPicture.charAt (i);
      if (c == '[')
      {
        if (i + 1 < nLen && sPicture.charAt (i + 1) == '[')
        {
          aSB.append ('[');
          i += 2;
          continue;
        }
        final int j = sPicture.indexOf (']', i + 1);
        if (j < 0)
          throw _err ("Unclosed '[' in date/time picture '" + sPicture + "'");
        final String sMarker = _stripWhitespace (sPicture.substring (i + 1, j));
        aSB.append (_formatComponent (aValue, eType, sMarker, aOpts));
        i = j + 1;
      }
      else
        if (c == ']')
        {
          if (i + 1 < nLen && sPicture.charAt (i + 1) == ']')
          {
            aSB.append (']');
            i += 2;
            continue;
          }
          aSB.append (']');
          i++;
        }
        else
        {
          aSB.append (c);
          i++;
        }
    }
    if (aOpts.m_bLangFallback && aOpts.m_bUsedLang)
      return "[Language: en]" + aSB.toString ();
    return aSB.toString ();
  }

  /**
   * Format a date/time value.
   *
   * @param aValue
   *        The value.
   * @param eType
   *        Its type (xs:date and xs:time restrict the components, FOFD1350).
   * @param sPicture
   *        The picture string.
   * @param sLanguage
   *        Language or <code>null</code> (English). German, French and Italian
   *        are supported natively; another language falls back to English and
   *        says so with a "[Language: en]" prefix.
   * @param sCalendar
   *        Calendar or <code>null</code> (AD).
   * @param sPlace
   *        Place or <code>null</code>: an Olson timezone name (Region/City)
   *        moves a value with timezone into that zone, DST included, and makes
   *        [ZN] show the zone's abbreviation.
   * @return The formatted string.
   * @throws XPathException
   *         FOFD1340 for an invalid picture or calendar, FOFD1350 for a
   *         component the type does not have.
   */
  @NonNull
  public static String format (@NonNull final XdmDateTimeValue aValue,
                               @NonNull final EAtomicType eType,
                               @NonNull final String sPicture,
                               @Nullable final String sLanguage,
                               @Nullable final String sCalendar,
                               @Nullable final String sPlace)
  {
    final Options aOpts = new Options ();
    if (sLanguage != null)
    {
      final String sLang = sLanguage.strip ().toLowerCase (Locale.ROOT);
      aOpts.m_sLang = FormatWords.getNativeLanguage (sLang);
      if (aOpts.m_sLang == null)
        aOpts.m_bLangFallback = !sLang.isEmpty () && !sLang.equals ("en") && !sLang.startsWith ("en-");
    }
    if (sCalendar != null)
      aOpts.m_sCalPrefix = _calendarPrefix (sCalendar);
    XdmDateTimeValue aEffective = aValue;
    if (sPlace != null && aValue.hasTimezone () && sPlace.indexOf ('/') >= 0)
    {
      try
      {
        final ZoneId aZone = ZoneId.of (sPlace.strip ());
        final BigDecimal aEpoch = aValue.getEpochSeconds (0);
        final long nEpochSec = aEpoch.setScale (0, RoundingMode.FLOOR).longValueExact ();
        final Instant aInstant = Instant.ofEpochSecond (nEpochSec);
        final int nOffsetMinutes = aZone.getRules ().getOffset (aInstant).getTotalSeconds () / 60;
        if (Math.abs (nOffsetMinutes) <= XdmDateTimeValue.MAX_TIMEZONE_MINUTES)
        {
          aEffective = XdmDateTimeValue.ofEpochSeconds (aEpoch, Integer.valueOf (nOffsetMinutes));
          final String sAbbrev = ZONE_NAME_FORMATTER.format (ZonedDateTime.ofInstant (aInstant, aZone));
          if (!sAbbrev.isEmpty () &&
              !sAbbrev.startsWith ("GMT+") &&
              !sAbbrev.startsWith ("GMT-") &&
              !sAbbrev.startsWith ("UTC+") &&
              !sAbbrev.startsWith ("UTC-") &&
              sAbbrev.charAt (0) != '+' &&
              sAbbrev.charAt (0) != '-')
            aOpts.m_sZoneName = sAbbrev;
        }
      }
      catch (final DateTimeException | ArithmeticException ex)
      {
        // Unknown place or instant out of range: format without moving
      }
    }
    return _formatPicture (aEffective, eType, sPicture, aOpts);
  }
}
