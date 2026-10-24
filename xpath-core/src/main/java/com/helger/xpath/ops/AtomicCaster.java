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
package com.helger.xpath.ops;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.DateTimeOps;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmDurationValue;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Casting between atomic types (F&amp;O 3.1 section 19), including the
 * whitespace handling and value-space checks of derived types. Owner:
 * atomic-values workstream; casts from and to date/time and duration types
 * delegate to the date/time workstream's classes.
 * <p>
 * The lexical spaces are those of XML Schema 1.1 (so <code>+INF</code> is a
 * valid xs:double), unless the static context asks for XSD 1.0: then
 * <code>+INF</code> and the year <code>0000</code> of xs:gYear and
 * xs:gYearMonth are rejected. Without a static context the XSD 1.1 rules
 * apply.
 *
 * @author Philip Helger
 */
@Immutable
public final class AtomicCaster
{
  /** Inclusive bounds of a derived integer type; <code>null</code> is unbounded. */
  private record IntRange (@Nullable BigInteger lo, @Nullable BigInteger hi)
  {}

  private static final Map <EAtomicType, IntRange> INT_RANGES = new EnumMap <> (EAtomicType.class);
  private static final double TWO_POW_63 = 9.223372036854775808E18;

  static
  {
    INT_RANGES.put (EAtomicType.BYTE, new IntRange (BigInteger.valueOf (-128), BigInteger.valueOf (127)));
    INT_RANGES.put (EAtomicType.SHORT, new IntRange (BigInteger.valueOf (-32768), BigInteger.valueOf (32767)));
    INT_RANGES.put (EAtomicType.INT,
                    new IntRange (BigInteger.valueOf (Integer.MIN_VALUE), BigInteger.valueOf (Integer.MAX_VALUE)));
    INT_RANGES.put (EAtomicType.LONG, new IntRange (BigInteger.valueOf (Long.MIN_VALUE), BigInteger.valueOf (Long.MAX_VALUE)));
    INT_RANGES.put (EAtomicType.UNSIGNED_BYTE, new IntRange (BigInteger.ZERO, BigInteger.valueOf (255)));
    INT_RANGES.put (EAtomicType.UNSIGNED_SHORT, new IntRange (BigInteger.ZERO, BigInteger.valueOf (65535)));
    INT_RANGES.put (EAtomicType.UNSIGNED_INT, new IntRange (BigInteger.ZERO, BigInteger.valueOf (4294967295L)));
    INT_RANGES.put (EAtomicType.UNSIGNED_LONG,
                    new IntRange (BigInteger.ZERO, new BigInteger ("18446744073709551615")));
    INT_RANGES.put (EAtomicType.NON_NEGATIVE_INTEGER, new IntRange (BigInteger.ZERO, null));
    INT_RANGES.put (EAtomicType.POSITIVE_INTEGER, new IntRange (BigInteger.ONE, null));
    INT_RANGES.put (EAtomicType.NON_POSITIVE_INTEGER, new IntRange (null, BigInteger.ZERO));
    INT_RANGES.put (EAtomicType.NEGATIVE_INTEGER, new IntRange (null, BigInteger.valueOf (-1)));
  }

  @PresentForCodeCoverage
  private static final AtomicCaster INSTANCE = new AtomicCaster ();

  private AtomicCaster ()
  {}

  @NonNull
  private static XPathException _notAllowed (@NonNull final EAtomicType eSource, @NonNull final EAtomicType eTarget)
  {
    return XPathException.type ("Cannot cast " + eSource.getQualifiedName () + " to " + eTarget.getQualifiedName ());
  }

  @NonNull
  private static XPathException _invalid (@NonNull final String sValue, @NonNull final EAtomicType eTarget)
  {
    return XPathException.invalidValue ("'" + sValue + "' is not a valid " + eTarget.getQualifiedName ());
  }

  /** @return <code>true</code> for string family and xs:untypedAtomic. */
  private static boolean _isStringSource (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.UNTYPED_ATOMIC || eType.isStringFamily ();
  }

  @NonNull
  private static XdmAtomic _checkIntegerRange (@NonNull final BigInteger aValue, @NonNull final EAtomicType eTarget)
  {
    final IntRange aRange = INT_RANGES.get (eTarget);
    if (aRange != null &&
        (aRange.lo () != null && aValue.compareTo (aRange.lo ()) < 0 ||
         aRange.hi () != null && aValue.compareTo (aRange.hi ()) > 0))
      throw XPathException.invalidValue ("Value " + aValue + " is outside the range of " + eTarget.getQualifiedName ());
    return XdmAtomic.of (eTarget, aValue);
  }

  /**
   * Convert a finite double to the integer obtained by truncation.
   */
  @NonNull
  static BigInteger truncateToBigInteger (final double d)
  {
    if (d > -TWO_POW_63 && d < TWO_POW_63)
      return BigInteger.valueOf ((long) d);
    return new BigDecimal (d).toBigInteger ();
  }

  /**
   * Apply the whitespace facet of a type derived from xs:string and check its
   * lexical space.
   */
  @NonNull
  private static XdmAtomic _castToDerivedString (@NonNull final String sLexical, @NonNull final EAtomicType eTarget)
  {
    switch (eTarget)
    {
      case STRING:
        return XdmAtomic.string (sLexical);
      case NORMALIZED_STRING:
        return XdmAtomic.of (eTarget, XsdLexical.replaceWhitespace (sLexical));
      default:
        break;
    }
    final String s = XsdLexical.collapseWhitespace (sLexical);
    final boolean bValid = switch (eTarget)
    {
      case TOKEN -> true;
      case LANGUAGE -> XsdLexical.isLanguage (s);
      case NMTOKEN -> XsdLexical.isNmtoken (s);
      case NAME -> XsdLexical.isName (s);
      case NCNAME, ID, IDREF, ENTITY -> XsdLexical.isNCName (s);
      default -> throw new IllegalStateException ("Not a string type: " + eTarget);
    };
    if (!bValid)
      throw _invalid (sLexical, eTarget);
    return XdmAtomic.of (eTarget, s);
  }

  @NonNull
  private static XdmAtomic _castStringToQName (@NonNull final String sValue,
                                              @Nullable final StaticContext aStaticCtx)
  {
    final String s = XsdLexical.collapseWhitespace (sValue);
    if (!XsdLexical.isQNameLexical (s))
      throw _invalid (sValue, EAtomicType.QNAME);
    final int nColon = s.indexOf (':');
    if (nColon < 0)
    {
      final String sNS = aStaticCtx == null ? "" : aStaticCtx.getDefaultElementNamespace ();
      return XdmAtomic.qname (new XdmQName (sNS, s, ""));
    }
    final String sPrefix = s.substring (0, nColon);
    String sNS = aStaticCtx == null ? null : aStaticCtx.getNamespaceUri (sPrefix);
    if (sNS == null && "xml".equals (sPrefix))
      sNS = XdmConstants.NS_XML;
    if (sNS == null)
      throw XPathException.of ("FONS0004", "No namespace is bound to the prefix '" + sPrefix + "'");
    return XdmAtomic.qname (new XdmQName (sNS, s.substring (nColon + 1), sPrefix));
  }

  private static boolean _isXsd10 (@Nullable final StaticContext aStaticCtx)
  {
    return aStaticCtx != null && aStaticCtx.getXsdVersion () == EXsdVersion.V10;
  }

  private static boolean _hasYearZero (@NonNull final String s)
  {
    // The year of a gYear/gYearMonth lexical: optional '-', then digits
    int i = s.startsWith ("-") ? 1 : 0;
    final int nStart = i;
    while (i < s.length () && s.charAt (i) >= '0' && s.charAt (i) <= '9')
    {
      if (s.charAt (i) != '0')
        return false;
      i++;
    }
    return i > nStart;
  }

  /**
   * Cast from a string-like value (string family or untypedAtomic) to a target
   * that is not xs:string/xs:untypedAtomic or a type derived from xs:string.
   */
  @NonNull
  private static XdmAtomic _castFromStringLike (@NonNull final String sValue,
                                               @NonNull final EAtomicType eTarget,
                                               @Nullable final StaticContext aStaticCtx)
  {
    if (eTarget == EAtomicType.QNAME)
      return _castStringToQName (sValue, aStaticCtx);

    final String s = XsdLexical.collapseWhitespace (sValue);
    if (eTarget.isIntegerFamily ())
    {
      if (!XsdLexical.isIntegerLexical (s))
        throw _invalid (sValue, eTarget);
      final BigInteger aValue;
      if (s.length () <= 18)
        aValue = BigInteger.valueOf (Long.parseLong (s));
      else
        aValue = new BigInteger (s);
      return _checkIntegerRange (aValue, eTarget);
    }
    if (eTarget.isDateTimeFamily ())
    {
      // XSD 1.0 prohibits the year 0000; XSD 1.1 reads it as 1 BCE
      if ((eTarget == EAtomicType.G_YEAR || eTarget == EAtomicType.G_YEAR_MONTH) &&
          _isXsd10 (aStaticCtx) &&
          _hasYearZero (s))
        throw XPathException.invalidValue ("The year 0000 is not allowed in XSD 1.0: '" + sValue + "'");
      final XdmDateTimeValue aDT = XdmDateTimeValue.parse (eTarget, s);
      if (eTarget == EAtomicType.DATE_TIME_STAMP && !aDT.hasTimezone ())
        throw XPathException.invalidValue ("An xs:dateTimeStamp requires a timezone: '" + sValue + "'");
      return XdmAtomic.of (eTarget, aDT);
    }
    if (eTarget.isDurationFamily ())
      return XdmAtomic.of (eTarget, XdmDurationValue.parse (eTarget, s));

    switch (eTarget)
    {
      case ANY_URI:
        if (!XsdLexical.isAnyUriLexical (s))
          throw _invalid (sValue, eTarget);
        return XdmAtomic.anyUri (s);
      case BOOLEAN:
        switch (s)
        {
          case "true":
          case "1":
            return XdmAtomic.TRUE;
          case "false":
          case "0":
            return XdmAtomic.FALSE;
          default:
            throw _invalid (sValue, eTarget);
        }
      case DECIMAL:
        if (!XsdLexical.isDecimalLexical (s))
          throw _invalid (sValue, eTarget);
        return XdmAtomic.decimal (new BigDecimal (s));
      case DOUBLE:
        // "+INF" was added in XSD 1.1
        if (!XsdLexical.isDoubleLexical (s) || (_isXsd10 (aStaticCtx) && s.equals ("+INF")))
          throw _invalid (sValue, eTarget);
        return XdmAtomic.dbl (_parseDouble (s));
      case FLOAT:
        // "+INF" was added in XSD 1.1
        if (!XsdLexical.isDoubleLexical (s) || (_isXsd10 (aStaticCtx) && s.equals ("+INF")))
          throw _invalid (sValue, eTarget);
        return XdmAtomic.flt (_parseFloat (s));
      case HEX_BINARY:
      {
        final byte [] aBytes = XsdLexical.decodeHex (s);
        if (aBytes == null)
          throw _invalid (sValue, eTarget);
        return XdmAtomic.of (eTarget, aBytes);
      }
      case BASE64_BINARY:
      {
        final byte [] aBytes = XsdLexical.decodeBase64 (s);
        if (aBytes == null)
          throw _invalid (sValue, eTarget);
        return XdmAtomic.of (eTarget, aBytes);
      }
      default:
        throw new IllegalStateException ("Unhandled target type " + eTarget);
    }
  }

  private static double _parseDouble (@NonNull final String s)
  {
    switch (s)
    {
      case "INF":
      case "+INF":
        return Double.POSITIVE_INFINITY;
      case "-INF":
        return Double.NEGATIVE_INFINITY;
      case "NaN":
        return Double.NaN;
      default:
        return Double.parseDouble (s);
    }
  }

  private static float _parseFloat (@NonNull final String s)
  {
    switch (s)
    {
      case "INF":
      case "+INF":
        return Float.POSITIVE_INFINITY;
      case "-INF":
        return Float.NEGATIVE_INFINITY;
      case "NaN":
        return Float.NaN;
      default:
        // Parsing directly at single precision avoids double rounding
        return Float.parseFloat (s);
    }
  }

  @NonNull
  private static XdmAtomic _castNumericOrBoolean (@NonNull final XdmAtomic aValue, @NonNull final EAtomicType eTarget)
  {
    final EAtomicType eSource = aValue.getType ();
    final Object aPayload = aValue.getValue ();
    if (eTarget == EAtomicType.BOOLEAN)
    {
      if (aPayload instanceof final BigInteger aBI)
        return XdmAtomic.bool (aBI.signum () != 0);
      if (aPayload instanceof final BigDecimal aBD)
        return XdmAtomic.bool (aBD.signum () != 0);
      final double d = aValue.getAsDouble ();
      return XdmAtomic.bool (d != 0 && !Double.isNaN (d));
    }
    if (eTarget.isIntegerFamily ())
    {
      final BigInteger aInt;
      if (aPayload instanceof final BigInteger aBI)
        aInt = aBI;
      else
        if (aPayload instanceof final BigDecimal aBD)
          aInt = aBD.toBigInteger ();
        else
          if (aPayload instanceof final Boolean aBool)
            aInt = aBool.booleanValue () ? BigInteger.ONE : BigInteger.ZERO;
          else
          {
            final double d = aValue.getAsDouble ();
            if (Double.isNaN (d) || Double.isInfinite (d))
              throw XPathException.of ("FOCA0002",
                                       "Cannot cast " + aValue.getLexical () + " to " + eTarget.getQualifiedName ());
            aInt = truncateToBigInteger (d);
          }
      return _checkIntegerRange (aInt, eTarget);
    }
    switch (eTarget)
    {
      case DECIMAL:
        if (aPayload instanceof final BigInteger aBI)
          return XdmAtomic.decimal (new BigDecimal (aBI));
        if (aPayload instanceof BigDecimal)
          return XdmAtomic.of (EAtomicType.DECIMAL, aPayload);
        if (aPayload instanceof final Boolean aBool)
          return XdmAtomic.decimal (aBool.booleanValue () ? BigDecimal.ONE : BigDecimal.ZERO);
        {
          final double d = aValue.getAsDouble ();
          if (Double.isNaN (d) || Double.isInfinite (d))
            throw XPathException.of ("FOCA0002", "Cannot cast " + aValue.getLexical () + " to xs:decimal");
          // Exact conversion: the numerically closest decimal is the value
          // itself
          return XdmAtomic.decimal (d == 0 ? BigDecimal.ZERO : new BigDecimal (d));
        }
      case DOUBLE:
        if (aPayload instanceof final Boolean aBool)
          return XdmAtomic.dbl (aBool.booleanValue () ? 1 : 0);
        return XdmAtomic.dbl (aValue.getAsDouble ());
      case FLOAT:
        if (aPayload instanceof final Boolean aBool)
          return XdmAtomic.flt (aBool.booleanValue () ? 1 : 0);
        return XdmAtomic.flt (toFloat (aValue));
      default:
        throw _notAllowed (eSource, eTarget);
    }
  }

  /**
   * @param aValue
   *        A numeric value.
   * @return The value converted to xs:float with a single rounding.
   */
  static float toFloat (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final Float aFloat)
      return aFloat.floatValue ();
    if (aPayload instanceof final Double aDouble)
      return aDouble.floatValue ();
    if (aPayload instanceof final BigInteger aBI)
      return aBI.floatValue ();
    if (aPayload instanceof final BigDecimal aBD)
      return aBD.floatValue ();
    throw new IllegalStateException ("Not numeric: " + aValue.getType ());
  }

  /**
   * @param aValue
   *        Source value.
   * @param eTarget
   *        Target type (not NUMERIC, not ANY_ATOMIC_TYPE).
   * @param aStaticCtx
   *        Namespaces for casting a string to xs:QName/xs:NOTATION; may be
   *        <code>null</code>, then such a cast fails with FONS0004 for prefixed
   *        names.
   * @return The cast value.
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 if the cast is not allowed, FORG0001 for an invalid
   *         value, FOCA0002/FOAR0002 etc. as the spec says.
   */
  @NonNull
  public static XdmAtomic cast (@NonNull final XdmAtomic aValue,
                                @NonNull final EAtomicType eTarget,
                                @Nullable final StaticContext aStaticCtx)
  {
    final EAtomicType eSource = aValue.getType ();
    if (eSource == eTarget)
      return aValue;
    switch (eTarget)
    {
      case ANY_ATOMIC_TYPE:
      case NOTATION:
        throw XPathException.of ("XPST0080", "Cannot cast to the abstract type " + eTarget.getQualifiedName ());
      case ERROR:
        throw XPathException.invalidValue ("No value can be cast to xs:error");
      case NUMERIC:
        if (aValue.isNumeric ())
          return aValue;
        return cast (aValue, EAtomicType.DOUBLE, aStaticCtx);
      case UNTYPED_ATOMIC:
        return XdmAtomic.untyped (aValue.getLexical ());
      case STRING:
        return XdmAtomic.string (aValue.getLexical ());
      default:
        break;
    }
    if (eTarget.isStringFamily ())
      return _castToDerivedString (aValue.getLexical (), eTarget);

    if (_isStringSource (eSource))
      return _castFromStringLike (aValue.getString (), eTarget, aStaticCtx);

    if (eSource.isNumeric () || eSource == EAtomicType.BOOLEAN)
      return _castNumericOrBoolean (aValue, eTarget);

    if (eSource.isDateTimeFamily () && eTarget.isDateTimeFamily () ||
        eSource.isDurationFamily () && eTarget.isDurationFamily ())
      return DateTimeOps.cast (aValue, eTarget);

    if (eSource.isBinary () && eTarget.isBinary ())
      return XdmAtomic.of (eTarget, aValue.getValue ());

    if (eSource == EAtomicType.NOTATION && eTarget == EAtomicType.QNAME)
      return XdmAtomic.qname ((XdmQName) aValue.getValue ());

    throw _notAllowed (eSource, eTarget);
  }

  /**
   * @param aValue
   *        Source value.
   * @param eTarget
   *        Target type.
   * @param aStaticCtx
   *        Namespaces, may be <code>null</code>.
   * @return <code>true</code> if
   *         {@link #cast(XdmAtomic, EAtomicType, StaticContext)} would
   *         succeed. Static errors (XPST0080 for abstract target types) are
   *         still thrown.
   */
  public static boolean isCastable (@NonNull final XdmAtomic aValue,
                                    @NonNull final EAtomicType eTarget,
                                    @Nullable final StaticContext aStaticCtx)
  {
    try
    {
      cast (aValue, eTarget, aStaticCtx);
      return true;
    }
    catch (final XPathException ex)
    {
      if ("XPST0080".equals (ex.getCode ()))
        throw ex;
      return false;
    }
  }

  /**
   * Cast a string (as from xs:untypedAtomic or a constructor function) to the
   * target type.
   *
   * @param sValue
   *        Source string.
   * @param eTarget
   *        Target type.
   * @param aStaticCtx
   *        Namespaces, may be <code>null</code>.
   * @return The cast value.
   */
  @NonNull
  public static XdmAtomic castFromString (@NonNull final String sValue,
                                          @NonNull final EAtomicType eTarget,
                                          @Nullable final StaticContext aStaticCtx)
  {
    return cast (XdmAtomic.string (sValue), eTarget, aStaticCtx);
  }
}
