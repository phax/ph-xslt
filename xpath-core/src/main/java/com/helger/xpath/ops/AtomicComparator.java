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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmDurationValue;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Comparison of atomic values: value comparisons, general comparisons,
 * ordering for sort/min/max, the eq-equality of distinct-values and index-of,
 * op:same-key for maps, and fn:deep-equal. Owner: atomic-values workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class AtomicComparator
{
  /** The comparison classes; only values of the same class are comparable. */
  private enum ECmpClass
  {
    NUMERIC,
    STRING,
    BOOLEAN,
    DATETIME,
    DURATION,
    QNAME,
    NOTATION,
    HEX_BINARY,
    BASE64_BINARY
  }

  /** Result of a comparison involving NaN: unordered. */
  private static final int UNORDERED = Integer.MIN_VALUE;
  private static final double TWO_POW_63 = 9.223372036854775808E18;
  private static final Double NAN_KEY = Double.valueOf (Double.NaN);

  private record NumericKey (@NonNull Object value)
  {}

  private record StringKey (@NonNull String value)
  {}

  private record BooleanKey (boolean value)
  {}

  private record QNameKey (boolean notation, @NonNull XdmQName name)
  {}

  private record DurationKey (long months, @NonNull BigDecimal seconds)
  {}

  private static final class BinaryKey
  {
    private final boolean m_bBase64;
    private final byte [] m_aBytes;

    BinaryKey (final boolean bBase64, final byte @NonNull [] aBytes)
    {
      m_bBase64 = bBase64;
      m_aBytes = aBytes;
    }

    @Override
    public boolean equals (final Object o)
    {
      return o instanceof final BinaryKey aOther && m_bBase64 == aOther.m_bBase64 && Arrays.equals (m_aBytes, aOther.m_aBytes);
    }

    @Override
    public int hashCode ()
    {
      return Arrays.hashCode (m_aBytes) * 31 + (m_bBase64 ? 1 : 0);
    }
  }

  /**
   * Date/time map key. Equality is eq with timezone presence taken into
   * account. The payload class offers no normalised form, so the hash code is
   * only per type and timezone presence.
   */
  private static final class DateTimeKey
  {
    private final EAtomicType m_ePrim;
    private final boolean m_bHasTZ;
    private final XdmDateTimeValue m_aValue;

    DateTimeKey (@NonNull final EAtomicType ePrim, final boolean bHasTZ, @NonNull final XdmDateTimeValue aValue)
    {
      m_ePrim = ePrim;
      m_bHasTZ = bHasTZ;
      m_aValue = aValue;
    }

    @Override
    public boolean equals (final Object o)
    {
      if (this == o)
        return true;
      if (!(o instanceof final DateTimeKey aOther))
        return false;
      // Both with or both without timezone: the implicit timezone cancels out
      return m_ePrim == aOther.m_ePrim &&
             m_bHasTZ == aOther.m_bHasTZ &&
             m_aValue.compareTo (aOther.m_aValue, m_ePrim, 0) == 0;
    }

    @Override
    public int hashCode ()
    {
      return m_ePrim.hashCode () * 31 + (m_bHasTZ ? 1 : 0);
    }
  }

  /** A pair of items or sequences still to compare in fn:deep-equal. */
  private record DeepPair (@NonNull Object left, @NonNull Object right)
  {}

  @PresentForCodeCoverage
  private static final AtomicComparator INSTANCE = new AtomicComparator ();

  private AtomicComparator ()
  {}

  @NonNull
  private static ECmpClass _getClass (@NonNull final EAtomicType eType)
  {
    if (eType.isNumeric ())
      return ECmpClass.NUMERIC;
    if (eType.isStringFamily () || eType == EAtomicType.UNTYPED_ATOMIC || eType == EAtomicType.ANY_URI)
      return ECmpClass.STRING;
    if (eType.isDateTimeFamily ())
      return ECmpClass.DATETIME;
    if (eType.isDurationFamily ())
      return ECmpClass.DURATION;
    return switch (eType)
    {
      case BOOLEAN -> ECmpClass.BOOLEAN;
      case QNAME -> ECmpClass.QNAME;
      case NOTATION -> ECmpClass.NOTATION;
      case HEX_BINARY -> ECmpClass.HEX_BINARY;
      case BASE64_BINARY -> ECmpClass.BASE64_BINARY;
      default -> throw new IllegalStateException ("No comparison class for " + eType);
    };
  }

  /** @return The primitive date/time type (xs:dateTimeStamp is xs:dateTime). */
  @NonNull
  private static EAtomicType _getDateTimePrimitive (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.DATE_TIME_STAMP ? EAtomicType.DATE_TIME : eType;
  }

  private static boolean _isOrdering (@NonNull final EComparisonOp eOp)
  {
    return switch (eOp)
    {
      case VALUE_LT, VALUE_LE, VALUE_GT, VALUE_GE, GENERAL_LT, GENERAL_LE, GENERAL_GT, GENERAL_GE -> true;
      default -> false;
    };
  }

  /**
   * @return <code>true</code> if the two values can be compared at all (with
   *         <code>bOrder</code>: also ordered).
   */
  private static boolean _isComparable (@NonNull final XdmAtomic aLeft,
                                        @NonNull final XdmAtomic aRight,
                                        final boolean bOrder)
  {
    final EAtomicType eLeft = aLeft.getType ();
    final EAtomicType eRight = aRight.getType ();
    final ECmpClass eClass = _getClass (eLeft);
    if (eClass != _getClass (eRight))
      return false;
    switch (eClass)
    {
      case DATETIME:
      {
        final EAtomicType ePrim = _getDateTimePrimitive (eLeft);
        if (ePrim != _getDateTimePrimitive (eRight))
          return false;
        return !bOrder || ePrim == EAtomicType.DATE_TIME || ePrim == EAtomicType.DATE || ePrim == EAtomicType.TIME;
      }
      case DURATION:
        return !bOrder ||
               eLeft == EAtomicType.YEAR_MONTH_DURATION && eRight == EAtomicType.YEAR_MONTH_DURATION ||
               eLeft == EAtomicType.DAY_TIME_DURATION && eRight == EAtomicType.DAY_TIME_DURATION;
      case QNAME:
      case NOTATION:
        return !bOrder;
      default:
        return true;
    }
  }

  private static int _compareDoubles (final double a, final double b)
  {
    if (Double.isNaN (a) || Double.isNaN (b))
      return UNORDERED;
    // Not Double.compare: -0 and 0 are equal
    return a < b ? -1 : a > b ? 1 : 0;
  }

  @NonNull
  private static BigDecimal _toBigDecimal (@NonNull final Object aPayload)
  {
    return aPayload instanceof final BigDecimal aBD ? aBD : new BigDecimal ((BigInteger) aPayload);
  }

  private static int _compareNumeric (@NonNull final XdmAtomic aLeft, @NonNull final XdmAtomic aRight)
  {
    final Object aL = aLeft.getValue ();
    final Object aR = aRight.getValue ();
    if (aL instanceof final BigInteger aLI && aR instanceof final BigInteger aRI)
      return aLI.compareTo (aRI);
    final boolean bLExact = aL instanceof BigInteger || aL instanceof BigDecimal;
    final boolean bRExact = aR instanceof BigInteger || aR instanceof BigDecimal;
    if (bLExact && bRExact)
      return _toBigDecimal (aL).compareTo (_toBigDecimal (aR));
    if (aL instanceof Double || aR instanceof Double)
      return _compareDoubles (aLeft.getAsDouble (), aRight.getAsDouble ());
    // At least one float and no double: compare in float space
    return _compareDoubles (AtomicCaster.toFloat (aLeft), AtomicCaster.toFloat (aRight));
  }

  private static int _compareStrings (@NonNull final String sLeft,
                                      @NonNull final String sRight,
                                      @Nullable final ICollation aCollation)
  {
    return (aCollation == null ? CollationRegistry.CODEPOINT : aCollation).compare (sLeft, sRight);
  }

  private static boolean _stringsEqual (@NonNull final String sLeft,
                                        @NonNull final String sRight,
                                        @Nullable final ICollation aCollation)
  {
    if (aCollation == null || aCollation == CollationRegistry.CODEPOINT)
      return sLeft.equals (sRight);
    return aCollation.compare (sLeft, sRight) == 0;
  }

  private static int _compareDurations (@NonNull final XdmAtomic aLeft, @NonNull final XdmAtomic aRight)
  {
    final XdmDurationValue aL = (XdmDurationValue) aLeft.getValue ();
    final XdmDurationValue aR = (XdmDurationValue) aRight.getValue ();
    final EAtomicType eLeft = aLeft.getType ();
    final EAtomicType eRight = aRight.getType ();
    if (eLeft == EAtomicType.YEAR_MONTH_DURATION && eRight == EAtomicType.YEAR_MONTH_DURATION)
      return Long.compare (aL.getMonths (), aR.getMonths ());
    if (eLeft == EAtomicType.DAY_TIME_DURATION && eRight == EAtomicType.DAY_TIME_DURATION)
      return aL.getSeconds ().compareTo (aR.getSeconds ());
    // Only equality is defined
    return aL.getMonths () == aR.getMonths () && aL.getSeconds ().compareTo (aR.getSeconds ()) == 0 ? 0 : 1;
  }

  /**
   * Compare two values already known to be comparable.
   *
   * @return negative, 0, positive, or {@link #UNORDERED} for NaN. For types
   *         with equality only, any non-zero value means "not equal".
   */
  private static int _compareComparable (@NonNull final XdmAtomic aLeft,
                                         @NonNull final XdmAtomic aRight,
                                         @Nullable final ICollation aCollation,
                                         @NonNull final DynamicContext aCtx)
  {
    switch (_getClass (aLeft.getType ()))
    {
      case NUMERIC:
        return _compareNumeric (aLeft, aRight);
      case STRING:
        return _compareStrings (aLeft.getString (), aRight.getString (), aCollation);
      case BOOLEAN:
        return Boolean.compare (aLeft.getBoolean (), aRight.getBoolean ());
      case DATETIME:
        return ((XdmDateTimeValue) aLeft.getValue ()).compareTo ((XdmDateTimeValue) aRight.getValue (),
                                                                 _getDateTimePrimitive (aLeft.getType ()),
                                                                 aCtx.getImplicitTimezoneMinutes ());
      case DURATION:
        return _compareDurations (aLeft, aRight);
      case QNAME:
      case NOTATION:
        return aLeft.getValue ().equals (aRight.getValue ()) ? 0 : 1;
      case HEX_BINARY:
      case BASE64_BINARY:
        return Arrays.compareUnsigned ((byte []) aLeft.getValue (), (byte []) aRight.getValue ());
      default:
        throw new IllegalStateException ();
    }
  }

  private static boolean _applyOp (@NonNull final EComparisonOp eOp, final int nCmp)
  {
    if (nCmp == UNORDERED)
      return eOp == EComparisonOp.VALUE_NE || eOp == EComparisonOp.GENERAL_NE;
    return switch (eOp)
    {
      case VALUE_EQ, GENERAL_EQ -> nCmp == 0;
      case VALUE_NE, GENERAL_NE -> nCmp != 0;
      case VALUE_LT, GENERAL_LT -> nCmp < 0;
      case VALUE_LE, GENERAL_LE -> nCmp <= 0;
      case VALUE_GT, GENERAL_GT -> nCmp > 0;
      case VALUE_GE, GENERAL_GE -> nCmp >= 0;
      default -> throw new IllegalArgumentException ("Not a value or general comparison: " + eOp);
    };
  }

  @NonNull
  private static XPathException _notComparable (@NonNull final XdmAtomic aLeft,
                                                @NonNull final XdmAtomic aRight,
                                                @NonNull final String sWhat)
  {
    return XPathException.type ("Values of type " +
                                aLeft.getType ().getQualifiedName () +
                                " and " +
                                aRight.getType ().getQualifiedName () +
                                " cannot be compared " +
                                sWhat);
  }

  /** Value comparison of two atomic values, any operator flavour. */
  private static boolean _compareAtomics (@NonNull final EComparisonOp eOp,
                                          @NonNull final XdmAtomic aLeft,
                                          @NonNull final XdmAtomic aRight,
                                          @Nullable final ICollation aCollation,
                                          @NonNull final DynamicContext aCtx)
  {
    if (!_isComparable (aLeft, aRight, _isOrdering (eOp)))
      throw _notComparable (aLeft, aRight, "with " + eOp);
    return _applyOp (eOp, _compareComparable (aLeft, aRight, aCollation, aCtx));
  }

  /** @return The primitive type an untypedAtomic is cast to when compared with T. */
  @NonNull
  private static EAtomicType _getUntypedTarget (@NonNull final EAtomicType eOther)
  {
    if (eOther.isNumeric ())
      return EAtomicType.DOUBLE;
    if (eOther.isDerivedFrom (EAtomicType.DAY_TIME_DURATION))
      return EAtomicType.DAY_TIME_DURATION;
    if (eOther.isDerivedFrom (EAtomicType.YEAR_MONTH_DURATION))
      return EAtomicType.YEAR_MONTH_DURATION;
    EAtomicType e = eOther;
    while (e.getParent () != null && e.getParent () != EAtomicType.ANY_ATOMIC_TYPE)
      e = e.getParent ();
    return e;
  }

  @NonNull
  private static XdmAtomic _castUntyped (@NonNull final XdmAtomic aUntyped,
                                         @NonNull final EAtomicType eTarget,
                                         @NonNull final DynamicContext aCtx)
  {
    try
    {
      return AtomicCaster.cast (aUntyped, eTarget, aCtx.getStaticContext ());
    }
    catch (final XPathException ex)
    {
      // A prefix not bound in the static context stays FONS0004 (F&O 3.1, casting to xs:QName)
      if ("FORG0001".equals (ex.getCode ()) || "FONS0004".equals (ex.getCode ()))
        throw ex;
      throw new XPathException ("FORG0001",
                                "Cannot convert '" +
                                            aUntyped.getString () +
                                            "' to " +
                                            eTarget.getQualifiedName () +
                                            " for a general comparison",
                                ex);
    }
  }

  /** One pair of a general comparison, XPath 3.1 rules. */
  private static boolean _generalPair (@NonNull final EComparisonOp eOp,
                                       @NonNull final XdmAtomic aLeft,
                                       @NonNull final XdmAtomic aRight,
                                       @Nullable final ICollation aCollation,
                                       @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aL = aLeft;
    XdmAtomic aR = aRight;
    final boolean bLU = aL.getType () == EAtomicType.UNTYPED_ATOMIC;
    final boolean bRU = aR.getType () == EAtomicType.UNTYPED_ATOMIC;
    if (bLU != bRU)
    {
      // Exactly one is untyped; two untyped values compare as strings
      if (bLU)
      {
        final EAtomicType eTarget = _getUntypedTarget (aR.getType ());
        if (eTarget != EAtomicType.STRING)
          aL = _castUntyped (aL, eTarget, aCtx);
      }
      else
      {
        final EAtomicType eTarget = _getUntypedTarget (aL.getType ());
        if (eTarget != EAtomicType.STRING)
          aR = _castUntyped (aR, eTarget, aCtx);
      }
    }
    return _compareAtomics (eOp, aL, aR, aCollation, aCtx);
  }

  /**
   * fn:number on an atomic value.
   */
  private static double _toNumber (@NonNull final XdmAtomic aValue)
  {
    if (aValue.isNumeric ())
      return aValue.getAsDouble ();
    final EAtomicType eType = aValue.getType ();
    if (eType == EAtomicType.BOOLEAN)
      return aValue.getBoolean () ? 1 : 0;
    if (aValue.isStringLike ())
    {
      final String s = XsdLexical.collapseWhitespace (aValue.getString ());
      if (XsdLexical.isDoubleLexical (s))
        return AtomicCaster.cast (XdmAtomic.string (s), EAtomicType.DOUBLE, null).getAsDouble ();
    }
    return Double.NaN;
  }

  private static boolean _applyDoubleOp (@NonNull final EComparisonOp eOp, final double a, final double b)
  {
    return _applyOp (eOp, _compareDoubles (a, b));
  }

  /**
   * One pair of a general comparison for = and != in XPath 1.0 compatibility
   * mode.
   */
  private static boolean _generalPair10 (@NonNull final EComparisonOp eOp,
                                         @NonNull final XdmAtomic aLeft,
                                         @NonNull final XdmAtomic aRight,
                                         @Nullable final ICollation aCollation,
                                         @NonNull final DynamicContext aCtx)
  {
    if (aLeft.isNumeric () || aRight.isNumeric ())
      return _applyDoubleOp (eOp, _toNumber (aLeft), _toNumber (aRight));
    final EAtomicType eLeft = aLeft.getType ();
    final EAtomicType eRight = aRight.getType ();
    final boolean bLU = eLeft == EAtomicType.UNTYPED_ATOMIC;
    final boolean bRU = eRight == EAtomicType.UNTYPED_ATOMIC;
    if (eLeft.isStringFamily () || eRight.isStringFamily () || bLU && bRU)
      return _applyOp (eOp, _compareStrings (aLeft.getLexical (), aRight.getLexical (), aCollation));
    if (bLU)
      return _compareAtomics (eOp, _castUntyped (aLeft, eRight, aCtx), aRight, aCollation, aCtx);
    if (bRU)
      return _compareAtomics (eOp, aLeft, _castUntyped (aRight, eLeft, aCtx), aCollation, aCtx);
    return _compareAtomics (eOp, aLeft, aRight, aCollation, aCtx);
  }

  private static boolean _isSingleBoolean (@NonNull final XdmSequence aSeq)
  {
    return aSeq.size () == 1 &&
           aSeq.get (0) instanceof final XdmAtomic aAtomic &&
           aAtomic.getType () == EAtomicType.BOOLEAN;
  }

  /**
   * The boolean value of an operand in rule 1 of the XPath 1.0 compatibility
   * rules. A sequence of more than one item can only stem from a node-set
   * (whose boolean value is <code>true</code> when non-empty), so it is not
   * treated as an error.
   */
  private static boolean _toBoolean10 (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.size () > 1)
      return true;
    return Atomizer.getEffectiveBooleanValue (aSeq);
  }

  private static boolean _generalCompare10 (@NonNull final EComparisonOp eOp,
                                            @NonNull final XdmSequence aLeft,
                                            @NonNull final XdmSequence aRight,
                                            @Nullable final ICollation aCollation,
                                            @NonNull final DynamicContext aCtx)
  {
    if (_isSingleBoolean (aLeft) || _isSingleBoolean (aRight))
    {
      final boolean bL = _toBoolean10 (aLeft);
      final boolean bR = _toBoolean10 (aRight);
      return _applyOp (eOp, Boolean.compare (bL, bR));
    }
    if (_isOrdering (eOp))
    {
      final double [] aRightNums = new double [aRight.size ()];
      for (int j = 0; j < aRightNums.length; ++j)
        aRightNums[j] = _toNumber ((XdmAtomic) aRight.get (j));
      for (final IXdmItem aL : aLeft)
      {
        final double dL = _toNumber ((XdmAtomic) aL);
        for (final double dR : aRightNums)
          if (_applyDoubleOp (eOp, dL, dR))
            return true;
      }
      return false;
    }
    int nPairs = 0;
    for (final IXdmItem aL : aLeft)
      for (final IXdmItem aR : aRight)
      {
        if ((++nPairs & 0x3ff) == 0)
          aCtx.checkInterrupt ();
        if (_generalPair10 (eOp, (XdmAtomic) aL, (XdmAtomic) aR, aCollation, aCtx))
          return true;
      }
    return false;
  }

  /**
   * @return A canonical representation of the exact value of a number:
   *         BigInteger for integral values, a stripped BigDecimal otherwise,
   *         and a Double for NaN and the infinities.
   */
  @NonNull
  private static Object _getCanonicalNumber (@NonNull final Object aPayload)
  {
    if (aPayload instanceof final BigInteger aBI)
      return aBI;
    final BigDecimal aBD;
    if (aPayload instanceof final BigDecimal aDec)
      aBD = aDec;
    else
    {
      final double d = ((Number) aPayload).doubleValue ();
      if (Double.isNaN (d))
        return NAN_KEY;
      if (Double.isInfinite (d))
        return Double.valueOf (d);
      if (d == Math.rint (d) && d > -TWO_POW_63 && d < TWO_POW_63)
        return BigInteger.valueOf ((long) d);
      aBD = new BigDecimal (d);
    }
    if (aBD.signum () == 0)
      return BigInteger.ZERO;
    final BigDecimal aStripped = aBD.stripTrailingZeros ();
    return aStripped.scale () <= 0 ? aStripped.toBigInteger () : aStripped;
  }

  /**
   * @param aKey
   *        A map key.
   * @return An object whose equals/hashCode implement op:same-key (XPath 3.1
   *         section 17.1.1): all string-like keys by codepoints, numeric keys
   *         by exact mathematical value (1 = 1.0 = 1e0, NaN = NaN, but
   *         xs:double('1.1') != xs:decimal('1.1')), date/time keys by eq
   *         including timezone presence, durations by value.
   */
  @NonNull
  public static Object getMapKey (@NonNull final XdmAtomic aKey)
  {
    final Object aPayload = aKey.getValue ();
    switch (_getClass (aKey.getType ()))
    {
      case NUMERIC:
        return new NumericKey (_getCanonicalNumber (aPayload));
      case STRING:
        return new StringKey ((String) aPayload);
      case BOOLEAN:
        return new BooleanKey (((Boolean) aPayload).booleanValue ());
      case QNAME:
        return new QNameKey (false, (XdmQName) aPayload);
      case NOTATION:
        return new QNameKey (true, (XdmQName) aPayload);
      case HEX_BINARY:
        return new BinaryKey (false, (byte []) aPayload);
      case BASE64_BINARY:
        return new BinaryKey (true, (byte []) aPayload);
      case DURATION:
      {
        final XdmDurationValue aDur = (XdmDurationValue) aPayload;
        final BigDecimal aSecs = aDur.getSeconds ();
        return new DurationKey (aDur.getMonths (), aSecs.signum () == 0 ? BigDecimal.ZERO : aSecs.stripTrailingZeros ());
      }
      case DATETIME:
      {
        final XdmDateTimeValue aDT = (XdmDateTimeValue) aPayload;
        return new DateTimeKey (_getDateTimePrimitive (aKey.getType ()), aDT.hasTimezone (), aDT);
      }
      default:
        throw new IllegalStateException ();
    }
  }

  /**
   * Value comparison (eq, ne, lt, le, gt, ge) of two atomic values after
   * untypedAtomic was cast to xs:string.
   *
   * @param eOp
   *        A VALUE_* operator.
   * @param aLeft
   *        Left operand.
   * @param aRight
   *        Right operand.
   * @param aCollation
   *        Collation for strings, <code>null</code> for codepoint.
   * @param aCtx
   *        For the implicit timezone.
   * @return The result.
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 if the types are not comparable.
   */
  public static boolean valueCompare (@NonNull final EComparisonOp eOp,
                                      @NonNull final XdmAtomic aLeft,
                                      @NonNull final XdmAtomic aRight,
                                      @Nullable final ICollation aCollation,
                                      @NonNull final DynamicContext aCtx)
  {
    return _compareAtomics (eOp, aLeft, aRight, aCollation, aCtx);
  }

  /**
   * General comparison (=, !=, &lt;, ...) of two already atomised sequences,
   * with the XPath 3.1 rules for untypedAtomic operands (and the XPath 1.0
   * rules when the static context asks for compatibility mode).
   * <p>
   * Note for compatibility mode: rule 1 (an xs:boolean operand turns the other
   * operand into its effective boolean value) is applied to the atomised
   * operand here. A caller that has the original operand at hand and it
   * contains nodes should apply that rule itself before atomising, since the
   * effective boolean value of a node is always <code>true</code>.
   *
   * @param eOp
   *        A GENERAL_* operator.
   * @param aLeft
   *        Atomised left operand.
   * @param aRight
   *        Atomised right operand.
   * @param aCollation
   *        Collation for strings, <code>null</code> for codepoint.
   * @param aCtx
   *        Context.
   * @return The existential result.
   */
  public static boolean generalCompare (@NonNull final EComparisonOp eOp,
                                        @NonNull final XdmSequence aLeft,
                                        @NonNull final XdmSequence aRight,
                                        @Nullable final ICollation aCollation,
                                        @NonNull final DynamicContext aCtx)
  {
    if (aCtx.getStaticContext ().isXPath10Compatibility ())
      return _generalCompare10 (eOp, aLeft, aRight, aCollation, aCtx);
    int nPairs = 0;
    for (final IXdmItem aL : aLeft)
      for (final IXdmItem aR : aRight)
      {
        if ((++nPairs & 0x3ff) == 0)
          aCtx.checkInterrupt ();
        if (_generalPair (eOp, (XdmAtomic) aL, (XdmAtomic) aR, aCollation, aCtx))
          return true;
      }
    return false;
  }

  /**
   * Order for fn:sort, fn:min, fn:max and order-by: like lt, with NaN sorting
   * before every other number (and equal to NaN).
   *
   * @param aLeft
   *        Left value.
   * @param aRight
   *        Right value.
   * @param aCollation
   *        Collation for strings, <code>null</code> for codepoint.
   * @param aCtx
   *        For the implicit timezone.
   * @return negative, 0, positive
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 if the values are not comparable.
   */
  public static int compareForOrder (@NonNull final XdmAtomic aLeft,
                                     @NonNull final XdmAtomic aRight,
                                     @Nullable final ICollation aCollation,
                                     @NonNull final DynamicContext aCtx)
  {
    if (!_isComparable (aLeft, aRight, true))
      throw _notComparable (aLeft, aRight, "for ordering");
    final int nCmp = _compareComparable (aLeft, aRight, aCollation, aCtx);
    if (nCmp != UNORDERED)
      return Integer.signum (nCmp);
    final boolean bLeftNaN = Double.isNaN (aLeft.getAsDouble ());
    final boolean bRightNaN = Double.isNaN (aRight.getAsDouble ());
    if (bLeftNaN && bRightNaN)
      return 0;
    return bLeftNaN ? -1 : 1;
  }

  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return aPayload instanceof final Double aD && aD.isNaN () || aPayload instanceof final Float aF && aF.isNaN ();
  }

  /**
   * Equality as used by fn:distinct-values and fn:index-of (eq, but NaN equals
   * NaN and incomparable types are simply unequal, never an error).
   *
   * @param aLeft
   *        Left value.
   * @param aRight
   *        Right value.
   * @param aCollation
   *        Collation for strings, <code>null</code> for codepoint.
   * @param aCtx
   *        For the implicit timezone.
   * @return <code>true</code> if equal.
   */
  public static boolean isEqualForDistinct (@NonNull final XdmAtomic aLeft,
                                            @NonNull final XdmAtomic aRight,
                                            @Nullable final ICollation aCollation,
                                            @NonNull final DynamicContext aCtx)
  {
    if (!_isComparable (aLeft, aRight, false))
      return false;
    final int nCmp = _compareComparable (aLeft, aRight, aCollation, aCtx);
    if (nCmp == UNORDERED)
      return _isNaN (aLeft) && _isNaN (aRight);
    return nCmp == 0;
  }

  /**
   * Fail with FOTY0015 if a function item (other than a map or array) occurs
   * anywhere in the sequence, including inside maps and arrays.
   */
  private static void _checkNoFunctionItems (@NonNull final XdmSequence aSeq)
  {
    final ArrayDeque <XdmSequence> aStack = new ArrayDeque <> ();
    aStack.push (aSeq);
    while (!aStack.isEmpty ())
      for (final IXdmItem aItem : aStack.pop ())
        if (aItem instanceof final XdmArray aArray)
        {
          for (int i = 0; i < aArray.size (); ++i)
            aStack.push (aArray.get (i));
        }
        else
          if (aItem instanceof final XdmMap aMap)
          {
            for (final XdmMap.Entry aEntry : aMap.getAllEntries ())
              aStack.push (aEntry.value ());
          }
          else
            if (aItem instanceof IXdmFunction)
              throw XPathException.of ("FOTY0015", "fn:deep-equal cannot compare function items");
  }

  /** @return The children taking part in deep-equal: elements and text nodes. */
  @NonNull
  private static ICommonsList <IXdmNode> _getDeepEqualChildren (@NonNull final IXdmNode aNode)
  {
    final int nCount = aNode.getChildCount ();
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> (nCount);
    for (int i = 0; i < nCount; ++i)
    {
      final IXdmNode aChild = aNode.getChildAtIndex (i);
      if (aChild.getKind () == ENodeKind.ELEMENT || aChild.getKind () == ENodeKind.TEXT)
        ret.add (aChild);
    }
    return ret;
  }

  private static boolean _attributesDeepEqual (@NonNull final IXdmNode aLeft,
                                               @NonNull final IXdmNode aRight,
                                               @Nullable final ICollation aCollation)
  {
    final int nCount = aLeft.getAttributeCount ();
    if (nCount != aRight.getAttributeCount ())
      return false;
    for (int i = 0; i < nCount; ++i)
    {
      final IXdmNode aAttr = aLeft.getAttributeAtIndex (i);
      final XdmQName aName = aAttr.getName ();
      final IXdmNode aOther = aRight.getAttribute (aName.namespaceUri (), aName.localName ());
      if (aOther == null || !_stringsEqual (aAttr.getStringValue (), aOther.getStringValue (), aCollation))
        return false;
    }
    return true;
  }

  /**
   * Compare two nodes locally; child pairs that still need comparing are
   * pushed onto the stack.
   */
  private static boolean _nodesDeepEqual (@NonNull final IXdmNode aLeft,
                                          @NonNull final IXdmNode aRight,
                                          @Nullable final ICollation aCollation,
                                          @NonNull final ArrayDeque <DeepPair> aStack)
  {
    if (aLeft == aRight)
      return true;
    final ENodeKind eKind = aLeft.getKind ();
    if (eKind != aRight.getKind ())
      return false;
    switch (eKind)
    {
      case ELEMENT:
        if (!aLeft.getName ().equals (aRight.getName ()) || !_attributesDeepEqual (aLeft, aRight, aCollation))
          return false;
        // fall through
      case DOCUMENT:
      {
        final ICommonsList <IXdmNode> aLC = _getDeepEqualChildren (aLeft);
        final ICommonsList <IXdmNode> aRC = _getDeepEqualChildren (aRight);
        if (aLC.size () != aRC.size ())
          return false;
        for (int i = 0; i < aLC.size (); ++i)
          aStack.push (new DeepPair (aLC.get (i), aRC.get (i)));
        return true;
      }
      case ATTRIBUTE:
      case PROCESSING_INSTRUCTION:
      case NAMESPACE:
        return Objects.equals (aLeft.getName (), aRight.getName ()) &&
               _stringsEqual (aLeft.getStringValue (), aRight.getStringValue (), aCollation);
      default:
        // text, comment
        return _stringsEqual (aLeft.getStringValue (), aRight.getStringValue (), aCollation);
    }
  }

  private static boolean _atomicsDeepEqual (@NonNull final XdmAtomic aLeft,
                                            @NonNull final XdmAtomic aRight,
                                            @Nullable final ICollation aCollation,
                                            @NonNull final DynamicContext aCtx)
  {
    if (!_isComparable (aLeft, aRight, false))
      return false;
    if (_getClass (aLeft.getType ()) == ECmpClass.STRING)
      return _stringsEqual (aLeft.getString (), aRight.getString (), aCollation);
    final int nCmp = _compareComparable (aLeft, aRight, aCollation, aCtx);
    if (nCmp == UNORDERED)
      return _isNaN (aLeft) && _isNaN (aRight);
    return nCmp == 0;
  }

  /**
   * Compare two items locally; nested pairs are pushed onto the stack.
   */
  private static boolean _itemsDeepEqual (@NonNull final IXdmItem aLeft,
                                          @NonNull final IXdmItem aRight,
                                          @Nullable final ICollation aCollation,
                                          @NonNull final DynamicContext aCtx,
                                          @NonNull final ArrayDeque <DeepPair> aStack)
  {
    if (aLeft instanceof final XdmAtomic aLA)
      return aRight instanceof final XdmAtomic aRA && _atomicsDeepEqual (aLA, aRA, aCollation, aCtx);
    if (aLeft instanceof final IXdmNode aLN)
      return aRight instanceof final IXdmNode aRN && _nodesDeepEqual (aLN, aRN, aCollation, aStack);
    if (aLeft instanceof final XdmMap aLM)
    {
      if (!(aRight instanceof final XdmMap aRM) || aLM.size () != aRM.size ())
        return false;
      for (final XdmMap.Entry aEntry : aLM.getAllEntries ())
      {
        final XdmSequence aOther = aRM.get (aEntry.key ());
        if (aOther == null)
          return false;
        aStack.push (new DeepPair (aEntry.value (), aOther));
      }
      return true;
    }
    if (aLeft instanceof final XdmArray aLArr)
    {
      if (!(aRight instanceof final XdmArray aRArr) || aLArr.size () != aRArr.size ())
        return false;
      for (int i = 0; i < aLArr.size (); ++i)
        aStack.push (new DeepPair (aLArr.get (i), aRArr.get (i)));
      return true;
    }
    throw XPathException.of ("FOTY0015", "fn:deep-equal cannot compare function items");
  }

  /**
   * fn:deep-equal (F&amp;O 3.1 section 14.2.1) over items of any kind.
   *
   * @param aLeft
   *        First sequence.
   * @param aRight
   *        Second sequence.
   * @param aCollation
   *        Collation for strings, <code>null</code> for codepoint.
   * @param aCtx
   *        For the implicit timezone and interruption.
   * @return <code>true</code> if deep-equal.
   * @throws com.helger.xpath.XPathException
   *         FOTY0015 if a function item (other than a map or array) occurs
   *         in either sequence.
   */
  public static boolean deepEqual (@NonNull final XdmSequence aLeft,
                                   @NonNull final XdmSequence aRight,
                                   @Nullable final ICollation aCollation,
                                   @NonNull final DynamicContext aCtx)
  {
    _checkNoFunctionItems (aLeft);
    _checkNoFunctionItems (aRight);
    final ArrayDeque <DeepPair> aStack = new ArrayDeque <> ();
    aStack.push (new DeepPair (aLeft, aRight));
    int nSteps = 0;
    while (!aStack.isEmpty ())
    {
      if ((++nSteps & 0x3ff) == 0)
        aCtx.checkInterrupt ();
      final DeepPair aPair = aStack.pop ();
      if (aPair.left () instanceof final XdmSequence aLS)
      {
        final XdmSequence aRS = (XdmSequence) aPair.right ();
        if (aLS.size () != aRS.size ())
          return false;
        for (int i = 0; i < aLS.size (); ++i)
          if (!_itemsDeepEqual (aLS.get (i), aRS.get (i), aCollation, aCtx, aStack))
            return false;
      }
      else
        if (!_nodesDeepEqual ((IXdmNode) aPair.left (), (IXdmNode) aPair.right (), aCollation, aStack))
          return false;
    }
    return true;
  }

  /**
   * The fn:number conversion of XPath 1.0 compatibility mode: numbers as
   * double, booleans as 0 or 1, strings and untyped values by their double
   * lexical form, everything else (and every invalid string) NaN.
   *
   * @param aValue
   *        The atomic value.
   * @return The value as double.
   */
  public static double toNumber (@NonNull final XdmAtomic aValue)
  {
    return _toNumber (aValue);
  }
}
