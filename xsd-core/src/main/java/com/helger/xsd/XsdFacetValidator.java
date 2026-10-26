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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.xdm.XdmNode;

/**
 * Constraining facets (go-xslt <code>facets.go</code>): the compile-time check
 * of facet values against the base type, and the facet checks of a value. The
 * narrowing rules between a restriction step and its base
 * (<code>checkFacetConsistency</code> in go-xslt <code>loader.go</code>) are
 * part of the loader ({@link XsdSimpleTypeParser}).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdFacetValidator
{
  /**
   * The outcome of ordering a value against a bound.
   *
   * @param c
   *        The order, valid only if ok.
   * @param ok
   *        <code>false</code> means not decided, the bound passes.
   * @param incomparable
   *        <code>true</code> if the datatype's partial order holds no relation
   *        between the two, the bound fails.
   */
  private record BoundOrder (int c, boolean ok, boolean incomparable)
  {}

  @PresentForCodeCoverage
  private static final XsdFacetValidator INSTANCE = new XsdFacetValidator ();

  private XsdFacetValidator ()
  {}

  /**
   * @return <code>true</code> for the nine date/time primitives the
   *         explicitTimezone facet applies to.
   */
  private static boolean _isDateTimePrim (@NonNull final EAtomicType ePrim)
  {
    switch (ePrim)
    {
      case DATE_TIME:
      case DATE_TIME_STAMP:
      case DATE:
      case TIME:
      case G_YEAR:
      case G_YEAR_MONTH:
      case G_MONTH:
      case G_MONTH_DAY:
      case G_DAY:
        return true;
      default:
        return false;
    }
  }

  /** @return <code>true</code> if min/max In/Exclusive apply to the primitive. */
  private static boolean _boundsApplicable (@NonNull final EAtomicType ePrim)
  {
    return ePrim.isNumeric () || ePrim.isDateTimeFamily () || ePrim.isDurationFamily ();
  }

  /**
   * @return <code>true</code> if the length family is a legal facet for the
   *         primitive (string family, anyURI, binary, QName, NOTATION: length
   *         on QName/NOTATION is a legal but always satisfied constraint).
   */
  private static boolean _lengthFacetAllowed (@NonNull final EAtomicType ePrim)
  {
    return ePrim.isStringFamily () ||
           ePrim == EAtomicType.ANY_URI ||
           ePrim.isBinary () ||
           ePrim == EAtomicType.QNAME ||
           ePrim == EAtomicType.NOTATION ||
           ePrim == EAtomicType.ANY_ATOMIC_TYPE;
  }

  /** @return <code>true</code> for decimal and the integer tower. */
  private static boolean _digitsApplicable (@NonNull final EAtomicType ePrim)
  {
    return ePrim == EAtomicType.DECIMAL || ePrim.isIntegerFamily ();
  }

  /**
   * @return <code>true</code> if the length facets are measured for the
   *         primitive (string family, anyURI and the binary types).
   */
  private static boolean _lengthApplies (@NonNull final EAtomicType ePrim)
  {
    return ePrim.isStringFamily () || ePrim == EAtomicType.ANY_URI || ePrim.isBinary ();
  }

  /**
   * @return <code>true</code> if the date/time lexical ends in a timezone part,
   *         <code>Z</code> or <code>[+-]hh:mm</code>.
   */
  private static boolean _lexicalHasTZ (@NonNull final String sValue)
  {
    final String s = sValue.strip ();
    final int n = s.length ();
    if (n > 0 && s.charAt (n - 1) == 'Z')
      return true;
    if (n < 6)
      return false;
    final char cSign = s.charAt (n - 6);
    return (cSign == '+' || cSign == '-') &&
           _isDigit (s.charAt (n - 5)) &&
           _isDigit (s.charAt (n - 4)) &&
           s.charAt (n - 3) == ':' &&
           _isDigit (s.charAt (n - 2)) &&
           _isDigit (s.charAt (n - 1));
  }

  private static boolean _isDigit (final char c)
  {
    return c >= '0' && c <= '9';
  }

  private static boolean _isXmlSpace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /**
   * @return The length of the value in the unit of its primitive: octets for
   *         the binary types, characters otherwise.
   */
  private static int _valueLength (@NonNull final String sValue, @NonNull final EAtomicType ePrim)
  {
    switch (ePrim)
    {
      case HEX_BINARY:
        return _dropSpace (sValue).length () / 2;
      case BASE64_BINARY:
      {
        final String s = _dropSpace (sValue);
        int nEnd = s.length ();
        int n = nEnd / 4 * 3;
        while (nEnd > 0 && s.charAt (nEnd - 1) == '=')
        {
          n--;
          nEnd--;
        }
        return n;
      }
      default:
        return sValue.codePointCount (0, sValue.length ());
    }
  }

  @NonNull
  private static String _dropSpace (@NonNull final String sValue)
  {
    final StringBuilder aSB = new StringBuilder (sValue.length ());
    for (int i = 0; i < sValue.length (); i++)
    {
      final char c = sValue.charAt (i);
      if (!_isXmlSpace (c))
        aSB.append (c);
    }
    return aSB.toString ();
  }

  /**
   * @return The number of total and fractional significant digits of a decimal
   *         lexical (approximate: leading and trailing zeros stripped), as
   *         two ints.
   */
  @NonNull
  private static int [] _countDigits (@NonNull final String sValue)
  {
    String v = sValue.strip ();
    int nStart = 0;
    while (nStart < v.length () && (v.charAt (nStart) == '+' || v.charAt (nStart) == '-'))
      nStart++;
    v = v.substring (nStart);
    String sInt = v;
    String sFrac = "";
    final int nDot = v.indexOf ('.');
    if (nDot >= 0)
    {
      sInt = v.substring (0, nDot);
      sFrac = v.substring (nDot + 1);
    }
    int nIntStart = 0;
    while (nIntStart < sInt.length () && sInt.charAt (nIntStart) == '0')
      nIntStart++;
    sInt = sInt.substring (nIntStart);
    int nFracEnd = sFrac.length ();
    while (nFracEnd > 0 && sFrac.charAt (nFracEnd - 1) == '0')
      nFracEnd--;
    sFrac = sFrac.substring (0, nFracEnd);
    return new int [] { sInt.length () + sFrac.length (), sFrac.length () };
  }

  /**
   * Compare two lexicals cast to the primitive (go-xslt
   * <code>compareLexicals</code>).
   */
  @Nullable
  private static Integer _compareLexicals (@NonNull final String sA,
                                           @NonNull final String sB,
                                           @NonNull final EAtomicType ePrim)
  {
    final XdmAtomic aA = XsdValueCompare.castOrNull (sA.strip (), ePrim);
    final XdmAtomic aB = XsdValueCompare.castOrNull (sB.strip (), ePrim);
    if (aA == null || aB == null)
      return null;
    return XsdValueCompare.compare (aA, aB);
  }

  /**
   * Compare the value with a bound. Datatypes D.2.1 (timeOnTimeline): with
   * exactly one side timezoned, the other is compared with both extreme
   * offsets imputed; if that changes the result, the pair is incomparable.
   */
  @NonNull
  private static BoundOrder _order (@NonNull final String sValue,
                                    @NonNull final XdmAtomic aValue,
                                    @NonNull final String sBound,
                                    @NonNull final EAtomicType ePrim)
  {
    final XdmAtomic aBound = XsdValueCompare.castOrNull (sBound, ePrim);
    if (aBound == null)
      return new BoundOrder (0, false, false);
    if (ePrim.isDateTimeFamily ())
    {
      final boolean bValueTZ = _lexicalHasTZ (sValue);
      final boolean bBoundTZ = _lexicalHasTZ (sBound);
      if (bValueTZ != bBoundTZ)
      {
        final Integer aLo;
        final Integer aHi;
        if (bValueTZ)
        {
          aLo = _compareLexicals (sValue, sBound + "+14:00", ePrim);
          aHi = _compareLexicals (sValue, sBound + "-14:00", ePrim);
        }
        else
        {
          aLo = _compareLexicals (sValue + "+14:00", sBound, ePrim);
          aHi = _compareLexicals (sValue + "-14:00", sBound, ePrim);
        }
        if (aLo != null && aHi != null)
        {
          if (aLo.intValue () != aHi.intValue ())
            return new BoundOrder (0, false, true);
          return new BoundOrder (aLo.intValue (), true, false);
        }
      }
    }
    final Integer aC = XsdValueCompare.compare (aValue, aBound);
    if (aC == null)
    {
      // Only NaN leaves two numeric values unordered
      if (ePrim.isNumeric ())
        return new BoundOrder (0, false, true);
      return new BoundOrder (0, false, false);
    }
    return new BoundOrder (aC.intValue (), true, false);
  }

  /**
   * Reject a lower bound greater than the upper bound (an empty value space),
   * comparing the cast bound values exactly.
   */
  private static void _checkBoundOrder (@NonNull final XsdFacetSet aFacets, @NonNull final EAtomicType ePrim)
  {
    String sLo = aFacets.m_sMinIncl;
    boolean bLoSet = aFacets.m_bHasMinIncl;
    boolean bLoExcl = false;
    if (aFacets.m_bHasMinExcl)
    {
      sLo = aFacets.m_sMinExcl;
      bLoSet = true;
      bLoExcl = true;
    }
    String sHi = aFacets.m_sMaxIncl;
    boolean bHiSet = aFacets.m_bHasMaxIncl;
    boolean bHiExcl = false;
    if (aFacets.m_bHasMaxExcl)
    {
      sHi = aFacets.m_sMaxExcl;
      bHiSet = true;
      bHiExcl = true;
    }
    if (!bLoSet || !bHiSet)
      return;
    final XdmAtomic aLo = XsdValueCompare.castOrNull (XsdWhiteSpace.collapse (sLo), ePrim);
    final XdmAtomic aHi = XsdValueCompare.castOrNull (XsdWhiteSpace.collapse (sHi), ePrim);
    if (aLo == null || aHi == null)
      return;
    final Integer aC = XsdValueCompare.compare (aLo, aHi);
    if (aC == null)
      return;
    final int c = aC.intValue ();
    if (c > 0 || (c == 0 && (bLoExcl || bHiExcl)))
      throw XsdCompiler.invalid ("",
                                 "lower bound " +
                                    XsdLexHelper.quote (sLo) +
                                    " exceeds upper bound " +
                                    XsdLexHelper.quote (sHi));
  }

  /**
   * Compare two lexicals by value for a value-based enumeration; for
   * non-comparable types fall back to lexical equality.
   */
  private static boolean _valueEqual (@NonNull final String sValue,
                                      @NonNull final String sEnum,
                                      @NonNull final EAtomicType ePrim)
  {
    if (ePrim.isNumeric () || ePrim.isDateTimeFamily () || ePrim.isDurationFamily () || ePrim == EAtomicType.BOOLEAN)
    {
      final XdmAtomic aA = XsdValueCompare.castOrNull (sValue, ePrim);
      final XdmAtomic aB = XsdValueCompare.castOrNull (sEnum, ePrim);
      if (aA == null || aB == null)
        return sValue.equals (sEnum);
      final Integer aC = XsdValueCompare.compare (aA, aB);
      if (aC != null)
        return aC.intValue () == 0;
    }
    return sValue.equals (sEnum);
  }

  private static void _checkLength (@NonNull final XsdFacetSet aFacets,
                                    @NonNull final String sValue,
                                    @NonNull final EAtomicType ePrim)
  {
    if (!aFacets.m_bHasLength && !aFacets.m_bHasMinLength && !aFacets.m_bHasMaxLength)
      return;
    // The length family is only measured for the string family, anyURI and the
    // binary types; on other primitives the facet has no character-length
    // meaning and is treated as satisfied
    if (!_lengthApplies (ePrim))
      return;
    final int n = _valueLength (sValue, ePrim);
    if (aFacets.m_bHasLength && n != aFacets.m_nLength)
      throw XsdCompiler.invalid ("cvc-length-valid", "length " + n + " != " + aFacets.m_nLength);
    if (aFacets.m_bHasMinLength && n < aFacets.m_nMinLength)
      throw XsdCompiler.invalid ("cvc-minLength-valid", "length " + n + " < " + aFacets.m_nMinLength);
    if (aFacets.m_bHasMaxLength && n > aFacets.m_nMaxLength)
      throw XsdCompiler.invalid ("cvc-maxLength-valid", "length " + n + " > " + aFacets.m_nMaxLength);
  }

  private static void _checkBounds (@NonNull final XsdFacetSet aFacets,
                                    @NonNull final String sValue,
                                    @NonNull final EAtomicType ePrim)
  {
    if (!(aFacets.m_bHasMinIncl || aFacets.m_bHasMaxIncl || aFacets.m_bHasMinExcl || aFacets.m_bHasMaxExcl))
      return;
    final XdmAtomic aValue = XsdValueCompare.castOrNull (sValue, ePrim);
    if (aValue == null)
      throw XsdCompiler.invalid ("cvc-datatype-valid", XsdLexHelper.quote (sValue) + " is not a valid " + ePrim.getQualifiedName ());
    if (aFacets.m_bHasMinIncl)
    {
      final BoundOrder aOrder = _order (sValue, aValue, aFacets.m_sMinIncl, ePrim);
      if (aOrder.incomparable () || aOrder.ok () && aOrder.c () < 0)
        throw XsdCompiler.invalid ("cvc-minInclusive-valid",
                                   XsdLexHelper.quote (sValue) + " < minInclusive " + aFacets.m_sMinIncl);
    }
    if (aFacets.m_bHasMaxIncl)
    {
      final BoundOrder aOrder = _order (sValue, aValue, aFacets.m_sMaxIncl, ePrim);
      if (aOrder.incomparable () || aOrder.ok () && aOrder.c () > 0)
        throw XsdCompiler.invalid ("cvc-maxInclusive-valid",
                                   XsdLexHelper.quote (sValue) + " > maxInclusive " + aFacets.m_sMaxIncl);
    }
    if (aFacets.m_bHasMinExcl)
    {
      final BoundOrder aOrder = _order (sValue, aValue, aFacets.m_sMinExcl, ePrim);
      if (aOrder.incomparable () || aOrder.ok () && aOrder.c () <= 0)
        throw XsdCompiler.invalid ("cvc-minExclusive-valid",
                                   XsdLexHelper.quote (sValue) + " <= minExclusive " + aFacets.m_sMinExcl);
    }
    if (aFacets.m_bHasMaxExcl)
    {
      final BoundOrder aOrder = _order (sValue, aValue, aFacets.m_sMaxExcl, ePrim);
      if (aOrder.incomparable () || aOrder.ok () && aOrder.c () >= 0)
        throw XsdCompiler.invalid ("cvc-maxExclusive-valid",
                                   XsdLexHelper.quote (sValue) + " >= maxExclusive " + aFacets.m_sMaxExcl);
    }
  }

  private static void _checkDigits (@NonNull final XsdFacetSet aFacets, @NonNull final String sValue)
  {
    if (!aFacets.m_bHasTotalDigits && !aFacets.m_bHasFractionDigits)
      return;
    final int [] aCount = _countDigits (sValue);
    if (aFacets.m_bHasTotalDigits && aCount[0] > aFacets.m_nTotalDigits)
      throw XsdCompiler.invalid ("cvc-totalDigits-valid", aCount[0] + " total digits > " + aFacets.m_nTotalDigits);
    if (aFacets.m_bHasFractionDigits && aCount[1] > aFacets.m_nFractionDigits)
      throw XsdCompiler.invalid ("cvc-fractionDigits-valid", aCount[1] + " fraction digits > " + aFacets.m_nFractionDigits);
  }

  /**
   * go-xslt <code>validateFacetValues</code>: the facets declared at this
   * restriction step must be applicable to the type and their values valid
   * (e.g. a maxInclusive that is a valid value of the base, a length on a
   * type that has one, bounds in order). Called by the loader after a simple
   * type restriction is parsed.
   *
   * @param aType
   *        The simple type just parsed.
   * @param eVersion
   *        The XSD version.
   * @throws XsdInvalidException
   *         if a facet is not applicable or its value invalid (schema error)
   */
  static void validateFacetValues (@NonNull final XsdSimpleType aType, @NonNull final EXsdVersion eVersion)
  {
    final XsdFacetSet f = aType.m_aFacets;
    final boolean bHasLen = f.m_bHasLength || f.m_bHasMinLength || f.m_bHasMaxLength;
    final boolean bHasBound = f.m_bHasMinIncl || f.m_bHasMaxIncl || f.m_bHasMinExcl || f.m_bHasMaxExcl;
    final boolean bHasDigits = f.m_bHasTotalDigits || f.m_bHasFractionDigits;

    if (!f.m_sExplicitTZ.isEmpty ())
    {
      // 4.3.16: value vocabulary and applicability (only the date/time
      // primitives, atomic variety)
      switch (f.m_sExplicitTZ)
      {
        case "optional":
        case "required":
        case "prohibited":
          break;
        default:
          throw XsdCompiler.invalid ("", "invalid explicitTimezone value " + XsdLexHelper.quote (f.m_sExplicitTZ));
      }
      if (aType.m_eVariety != EXsdVariety.ATOMIC || !_isDateTimePrim (aType.resolvePrim ()))
        throw XsdCompiler.invalid ("", "explicitTimezone is not applicable here");
    }
    switch (aType.m_eVariety)
    {
      case LIST:
        if (bHasBound || bHasDigits)
          throw XsdCompiler.invalid ("", "bound/digit facets are not applicable to a list type");
        break;
      case UNION:
        if (bHasLen || bHasBound || bHasDigits)
          throw XsdCompiler.invalid ("", "length/bound/digit facets are not applicable to a union type");
        break;
      default:
      {
        // Atomic
        final EAtomicType ePrim = aType.resolvePrim ();
        if (bHasLen && !_lengthFacetAllowed (ePrim))
          throw XsdCompiler.invalid ("", "length facets are not applicable to " + ePrim.getQualifiedName ());
        if (bHasBound && !_boundsApplicable (ePrim))
          throw XsdCompiler.invalid ("", "bound facets are not applicable to " + ePrim.getQualifiedName ());
        if (bHasDigits && !_digitsApplicable (ePrim))
          throw XsdCompiler.invalid ("", "digit facets are not applicable to " + ePrim.getQualifiedName ());
        // xs:integer (and every type derived from it) fixes fractionDigits at
        // {value 0, fixed true}, so any fractionDigits > 0 in the integer
        // family is invalid
        if (f.m_bHasFractionDigits && f.m_nFractionDigits > 0 && ePrim.isIntegerFamily ())
          throw XsdCompiler.invalid ("",
                                     "fractionDigits must be 0 for integer-derived type " + ePrim.getQualifiedName ());
        if (f.m_bHasMinIncl)
          _checkBoundValue (ePrim, f.m_sMinIncl);
        if (f.m_bHasMaxIncl)
          _checkBoundValue (ePrim, f.m_sMaxIncl);
        if (f.m_bHasMinExcl)
          _checkBoundValue (ePrim, f.m_sMinExcl);
        if (f.m_bHasMaxExcl)
          _checkBoundValue (ePrim, f.m_sMaxExcl);
        if (f.m_bHasEnum)
        {
          final String sWS = aType.effectiveWhiteSpace ();
          for (final String sEnum : f.m_aEnumeration)
          {
            try
            {
              XsdSimpleTypeValidator.checkPrimitiveLexical (ePrim, XsdWhiteSpace.apply (sWS, sEnum));
            }
            catch (final XsdInvalidException ex)
            {
              throw XsdCompiler.invalid ("",
                                         "invalid enumeration value " +
                                            XsdLexHelper.quote (sEnum) +
                                            " for " +
                                            ePrim.getQualifiedName ());
            }
            // XSD 1.0 constrained xs:anyURI to RFC 2396 URI references; 1.1
            // dropped the constraint
            if (eVersion == EXsdVersion.V10 &&
                ePrim == EAtomicType.ANY_URI &&
                !XsdLexHelper.validAnyURI10 (XsdWhiteSpace.collapse (sEnum)))
              throw XsdCompiler.invalid ("",
                                         "enumeration value " +
                                            XsdLexHelper.quote (sEnum) +
                                            " is not a valid RFC 2396 URI reference");
          }
        }
        _checkBoundOrder (f, ePrim);
        break;
      }
    }

    // Mutual consistency (independent of variety)
    if (f.m_bHasMinIncl && f.m_bHasMinExcl)
      throw XsdCompiler.invalid ("", "minInclusive and minExclusive are mutually exclusive");
    if (f.m_bHasMaxIncl && f.m_bHasMaxExcl)
      throw XsdCompiler.invalid ("", "maxInclusive and maxExclusive are mutually exclusive");
    // length with minLength/maxLength: decided in checkFacetConsistency, where
    // the base chain is visible
    if (f.m_bHasMinLength && f.m_bHasMaxLength && f.m_nMinLength > f.m_nMaxLength)
      throw XsdCompiler.invalid ("", "minLength exceeds maxLength");
    if (f.m_bHasTotalDigits && f.m_bHasFractionDigits && f.m_nFractionDigits > f.m_nTotalDigits)
      throw XsdCompiler.invalid ("", "fractionDigits exceeds totalDigits");
  }

  private static void _checkBoundValue (@NonNull final EAtomicType ePrim, @NonNull final String sBound)
  {
    try
    {
      XsdSimpleTypeValidator.checkPrimitiveLexical (ePrim, XsdWhiteSpace.collapse (sBound));
    }
    catch (final XsdInvalidException ex)
    {
      throw XsdCompiler.invalid ("", "invalid facet value " + XsdLexHelper.quote (sBound) + " for " + ePrim.getQualifiedName ());
    }
  }

  /**
   * go-xslt <code>facetSet.checkIn</code>: check a normalised value against the
   * facets of one restriction step.
   *
   * @param aFacets
   *        The facets.
   * @param sValue
   *        The normalised value.
   * @param ePrim
   *        The primitive the value belongs to.
   * @param aAt
   *        The instance node for QName/NOTATION resolution; may be
   *        <code>null</code>.
   * @throws XsdInvalidException
   *         if a facet is violated
   */
  static void checkIn (@NonNull final XsdFacetSet aFacets,
                       @NonNull final String sValue,
                       @NonNull final EAtomicType ePrim,
                       @Nullable final XdmNode aAt)
  {
    checkPatternEnumIn (aFacets, sValue, ePrim, aAt);
    _checkLength (aFacets, sValue, ePrim);
    _checkBounds (aFacets, sValue, ePrim);
    if (!aFacets.m_sExplicitTZ.isEmpty () && _isDateTimePrim (ePrim))
    {
      final boolean bHas = _lexicalHasTZ (sValue);
      switch (aFacets.m_sExplicitTZ)
      {
        case "required":
          if (!bHas)
            throw XsdCompiler.invalid ("cvc-explicitTimezone-valid",
                                       "value " + XsdLexHelper.quote (sValue) + " must carry a timezone");
          break;
        case "prohibited":
          if (bHas)
            throw XsdCompiler.invalid ("cvc-explicitTimezone-valid",
                                       "value " + XsdLexHelper.quote (sValue) + " must not carry a timezone");
          break;
        default:
          break;
      }
    }
    _checkDigits (aFacets, sValue);
  }

  /**
   * go-xslt <code>facetSet.checkPatternEnum</code>: only the pattern and
   * enumeration facets (the bridge uses it).
   *
   * @param aFacets
   *        The facets.
   * @param sValue
   *        The normalised value.
   * @param ePrim
   *        The primitive.
   * @throws XsdInvalidException
   *         if a facet is violated
   */
  static void checkPatternEnum (@NonNull final XsdFacetSet aFacets,
                                @NonNull final String sValue,
                                @NonNull final EAtomicType ePrim)
  {
    checkPatternEnumIn (aFacets, sValue, ePrim, null);
  }

  /**
   * go-xslt <code>facetSet.checkPatternEnumIn</code>: the pattern and
   * enumeration facets, with the instance node an enumeration over
   * xs:QName/xs:NOTATION needs to expand the value.
   *
   * @param aFacets
   *        The facets.
   * @param sValue
   *        The normalised value.
   * @param ePrim
   *        The primitive.
   * @param aAt
   *        The instance node; may be <code>null</code>.
   * @throws XsdInvalidException
   *         if a facet is violated
   */
  static void checkPatternEnumIn (@NonNull final XsdFacetSet aFacets,
                                  @NonNull final String sValue,
                                  @NonNull final EAtomicType ePrim,
                                  @Nullable final XdmNode aAt)
  {
    // Pattern groups: match at least one alternative in every group (base
    // patterns are a separate group, checked at that level, so AND across
    // levels)
    for (final ICommonsList <XsdPattern> aGroup : aFacets.m_aPatterns)
    {
      boolean bOK = false;
      for (final XsdPattern aPattern : aGroup)
        if (aPattern.matches (sValue))
        {
          bOK = true;
          break;
        }
      if (!bOK)
        throw XsdCompiler.invalid ("cvc-pattern-valid",
                                   "value " + XsdLexHelper.quote (sValue) + " does not match the required pattern");
    }
    if (aFacets.m_bHasEnum)
    {
      boolean bOK = false;
      final boolean bQNameLike = ePrim == EAtomicType.QNAME || ePrim == EAtomicType.NOTATION;
      for (int i = 0; i < aFacets.m_aEnumeration.size (); i++)
      {
        if (_valueEqual (sValue, aFacets.m_aEnumeration.get (i), ePrim))
        {
          bOK = true;
          break;
        }
        // An enumeration over xs:QName/xs:NOTATION constrains the value space,
        // whose members are expanded names: the facet's prefix and the
        // instance's need not be spelled alike, only bound alike. Each side is
        // expanded against its own declaring element.
        if (bQNameLike &&
            aAt != null &&
            i < aFacets.m_aEnumQName.size () &&
            !aFacets.m_aEnumQName.get (i).isEmpty () &&
            XsdNodes.resolveQName (aAt, sValue).toString ().equals (aFacets.m_aEnumQName.get (i)))
        {
          bOK = true;
          break;
        }
      }
      if (!bOK)
        throw XsdCompiler.invalid ("cvc-enumeration-valid", "value " + XsdLexHelper.quote (sValue) + " is not in the enumeration");
    }
  }

  /**
   * go-xslt <code>facetSet.checkListLength</code>.
   *
   * @param aFacets
   *        The facets of a list type step.
   * @param nItems
   *        The number of list items.
   * @throws XsdInvalidException
   *         if a length facet is violated
   */
  static void checkListLength (@NonNull final XsdFacetSet aFacets, final int nItems)
  {
    if (aFacets.m_bHasLength && nItems != aFacets.m_nLength)
      throw XsdCompiler.invalid ("cvc-length-valid", "list length " + nItems + " != " + aFacets.m_nLength);
    if (aFacets.m_bHasMinLength && nItems < aFacets.m_nMinLength)
      throw XsdCompiler.invalid ("cvc-minLength-valid", "list length " + nItems + " < " + aFacets.m_nMinLength);
    if (aFacets.m_bHasMaxLength && nItems > aFacets.m_nMaxLength)
      throw XsdCompiler.invalid ("cvc-maxLength-valid", "list length " + nItems + " > " + aFacets.m_nMaxLength);
  }
}
