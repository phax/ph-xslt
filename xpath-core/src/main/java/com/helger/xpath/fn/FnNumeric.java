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
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.AtomicArithmetic;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:abs, ceiling, floor, round, round-half-to-even, number,
 * random-number-generator, and the aggregate functions count, sum, avg, min,
 * max.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnNumeric
{
  private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
  private static final String SIG_NEXT = "() as map(xs:string, item())";
  private static final String SIG_PERMUTE = "(item()*) as item()*";

  @PresentForCodeCoverage
  private static final FnNumeric INSTANCE = new FnNumeric ();

  private FnNumeric ()
  {}

  @NonNull
  private static XdmSequence _one (@NonNull final XdmAtomic aValue)
  {
    return XdmSequence.of (aValue);
  }

  @Nullable
  private static XdmAtomic _optAtomic (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? null : (XdmAtomic) aSeq.get (0);
  }

  @NonNull
  private static XdmSequence _abs (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic aArg = _optAtomic (aArgs.get (0));
    if (aArg == null)
      return XdmSequence.EMPTY;
    final Object aPayload = aArg.getValue ();
    if (aPayload instanceof final BigInteger aBI)
      return _one (XdmAtomic.integer (aBI.abs ()));
    if (aPayload instanceof final BigDecimal aBD)
      return _one (XdmAtomic.decimal (aBD.abs ()));
    if (aPayload instanceof final Float aF)
      return _one (XdmAtomic.flt (Math.abs (aF.floatValue ())));
    return _one (XdmAtomic.dbl (Math.abs (aArg.getAsDouble ())));
  }

  @NonNull
  private static XdmSequence _floorCeiling (@NonNull final List <XdmSequence> aArgs, final boolean bFloor)
  {
    final XdmAtomic aArg = _optAtomic (aArgs.get (0));
    if (aArg == null)
      return XdmSequence.EMPTY;
    final Object aPayload = aArg.getValue ();
    if (aPayload instanceof final BigInteger aBI)
      return _one (XdmAtomic.integer (aBI));
    if (aPayload instanceof final BigDecimal aBD)
      return _one (XdmAtomic.decimal (aBD.setScale (0, bFloor ? RoundingMode.FLOOR : RoundingMode.CEILING)));
    if (aPayload instanceof final Float aF)
    {
      final double d = aF.doubleValue ();
      return _one (XdmAtomic.flt ((float) (bFloor ? Math.floor (d) : Math.ceil (d))));
    }
    final double d = aArg.getAsDouble ();
    return _one (XdmAtomic.dbl (bFloor ? Math.floor (d) : Math.ceil (d)));
  }

  /**
   * Round an exact decimal to the given number of fraction digits (negative:
   * to a power of ten).
   *
   * @param aValue
   *        The value.
   * @param nPrecision
   *        Number of fraction digits.
   * @param bHalfEven
   *        <code>true</code> for round-half-to-even, <code>false</code> for
   *        round half towards positive infinity.
   * @return The rounded value.
   */
  @NonNull
  private static BigDecimal _roundDecimal (@NonNull final BigDecimal aValue,
                                           final long nPrecision,
                                           final boolean bHalfEven)
  {
    if (aValue.signum () == 0 || aValue.scale () <= nPrecision)
      return aValue;
    // Number of integer digits (may be negative for values below 0.1)
    final long nMagnitude = (long) aValue.precision () - aValue.scale ();
    if (-nPrecision > nMagnitude)
    {
      // |value| < 10^-precision / 10: always rounds to zero
      return BigDecimal.ZERO;
    }
    final RoundingMode eMode;
    if (bHalfEven)
      eMode = RoundingMode.HALF_EVEN;
    else
      eMode = aValue.signum () > 0 ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN;
    return aValue.setScale ((int) nPrecision, eMode);
  }

  private static double _roundDouble (final double d, final long nPrecision, final boolean bHalfEven)
  {
    if (Double.isNaN (d) || Double.isInfinite (d) || d == 0)
      return d;
    if (nPrecision >= 0 && d == Math.rint (d))
      return d;
    if (nPrecision > 1100)
      return d;
    final double ret = _roundDecimal (new BigDecimal (d), nPrecision, bHalfEven).doubleValue ();
    // A zero result keeps the sign of the input
    return ret == 0 ? Math.copySign (0.0, d) : ret;
  }

  private static float _roundFloat (final float f, final long nPrecision, final boolean bHalfEven)
  {
    if (Float.isNaN (f) || Float.isInfinite (f) || f == 0)
      return f;
    if (nPrecision >= 0 && f == Math.rint (f))
      return f;
    if (nPrecision > 200)
      return f;
    final float ret = _roundDecimal (new BigDecimal (f), nPrecision, bHalfEven).floatValue ();
    return ret == 0 ? Math.copySign (0.0f, f) : ret;
  }

  private static long _getPrecision (@NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.size () < 2)
      return 0;
    final BigInteger aBI = ((XdmAtomic) aArgs.get (1).get (0)).getBigInteger ();
    // Clamp: no value has more digits than this, so the result is the same
    if (aBI.compareTo (BigInteger.valueOf (100_000)) > 0)
      return 100_000;
    if (aBI.compareTo (BigInteger.valueOf (-100_000)) < 0)
      return -100_000;
    return aBI.longValue ();
  }

  @NonNull
  private static XdmSequence _round (@NonNull final List <XdmSequence> aArgs, final boolean bHalfEven)
  {
    final XdmAtomic aArg = _optAtomic (aArgs.get (0));
    if (aArg == null)
      return XdmSequence.EMPTY;
    final long nPrecision = _getPrecision (aArgs);
    final Object aPayload = aArg.getValue ();
    if (aPayload instanceof final BigInteger aBI)
    {
      if (nPrecision >= 0)
        return _one (XdmAtomic.integer (aBI));
      return _one (XdmAtomic.integer (_roundDecimal (new BigDecimal (aBI), nPrecision, bHalfEven).toBigInteger ()));
    }
    if (aPayload instanceof final BigDecimal aBD)
      return _one (XdmAtomic.decimal (_roundDecimal (aBD, nPrecision, bHalfEven)));
    if (aPayload instanceof final Float aF)
      return _one (XdmAtomic.flt (_roundFloat (aF.floatValue (), nPrecision, bHalfEven)));
    return _one (XdmAtomic.dbl (_roundDouble (aArg.getAsDouble (), nPrecision, bHalfEven)));
  }

  @NonNull
  private static XdmSequence _number (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aValue;
    if (aArgs.isEmpty ())
    {
      final IXdmItem aCtxItem = aCtx.getContextItem ();
      if (aCtxItem == null)
        throw XPathException.noContext ("fn:number() has no context item");
      aValue = Atomizer.atomize (XdmSequence.of (aCtxItem));
      if (aValue.size () > 1)
        throw XPathException.type ("fn:number() requires at most one atomic value");
    }
    else
      aValue = aArgs.get (0);
    if (aValue.isEmpty ())
      return _one (XdmAtomic.dbl (Double.NaN));
    final XdmAtomic aAtomic = (XdmAtomic) aValue.get (0);
    final EAtomicType eType = aAtomic.getType ();
    if (aAtomic.isNumeric () || aAtomic.isStringLike () && eType != EAtomicType.ANY_URI || eType == EAtomicType.BOOLEAN)
    {
      try
      {
        return _one (AtomicCaster.cast (aAtomic, EAtomicType.DOUBLE, null));
      }
      catch (final XPathException ex)
      {
        // fall through to NaN
      }
    }
    return _one (XdmAtomic.dbl (Double.NaN));
  }

  private static long _splitMix64 (final long nState)
  {
    long z = nState + GOLDEN_GAMMA;
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  private static long _hashString (@NonNull final String s)
  {
    // FNV-1a over the UTF-8 bytes: stable across runs and platforms
    long h = 0xcbf29ce484222325L;
    for (final byte b : s.getBytes (StandardCharsets.UTF_8))
    {
      h ^= b & 0xff;
      h *= 0x100000001b3L;
    }
    return h;
  }

  /** The signatures of the generator's functions, parsed on first use. */
  private static final class RngSignatures
  {
    static final XPathParser.FunctionSignature NEXT = XPathParser.parseFunctionSignature (SIG_NEXT,
                                                                                          new StaticContext ());
    static final XPathParser.FunctionSignature PERMUTE = XPathParser.parseFunctionSignature (SIG_PERMUTE,
                                                                                             new StaticContext ());
  }

  /** The <code>next</code> and <code>permute</code> functions of a generator. */
  private static final class RngFunction implements IXdmFunction
  {
    private final long m_nSeed;
    private final boolean m_bPermute;
    private final List <SeqType> m_aParamTypes;
    private final SeqType m_aReturnType;

    RngFunction (final long nSeed, final boolean bPermute)
    {
      m_nSeed = nSeed;
      m_bPermute = bPermute;
      final XPathParser.FunctionSignature aSig = bPermute ? RngSignatures.PERMUTE : RngSignatures.NEXT;
      m_aParamTypes = aSig.paramTypes ();
      m_aReturnType = aSig.returnType ();
    }

    @Nullable
    public XdmQName getName ()
    {
      return null;
    }

    public int getArity ()
    {
      return m_bPermute ? 1 : 0;
    }

    @NonNull
    public List <SeqType> getParamTypes ()
    {
      return m_aParamTypes;
    }

    @NonNull
    public SeqType getReturnType ()
    {
      return m_aReturnType;
    }

    @NonNull
    public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
    {
      if (!m_bPermute)
        return XdmSequence.of (_createGenerator (m_nSeed + GOLDEN_GAMMA));
      final IXdmItem [] aItems = aArgs.get (0).getAsList ().toArray (new IXdmItem [0]);
      // Fisher-Yates driven by a stream derived from this generator's seed
      long nState = m_nSeed;
      for (int i = aItems.length - 1; i > 0; --i)
      {
        nState = _splitMix64 (nState);
        final int j = (int) Long.remainderUnsigned (nState, i + 1L);
        final IXdmItem aTmp = aItems[i];
        aItems[i] = aItems[j];
        aItems[j] = aTmp;
      }
      return XdmSequence.of (aItems);
    }
  }

  @NonNull
  private static XdmMap _createGenerator (final long nSeed)
  {
    final double dNumber = (_splitMix64 (nSeed) >>> 11) / (double) (1L << 53);
    return XdmMap.EMPTY.put (XdmAtomic.string ("number"), XdmSequence.of (XdmAtomic.dbl (dNumber)))
                       .put (XdmAtomic.string ("next"), XdmSequence.of (new RngFunction (nSeed, false)))
                       .put (XdmAtomic.string ("permute"), XdmSequence.of (new RngFunction (nSeed, true)));
  }

  @NonNull
  private static XdmSequence _randomNumberGenerator (@NonNull final DynamicContext aCtx,
                                                     @NonNull final List <XdmSequence> aArgs)
  {
    final long nSeed;
    if (aArgs.isEmpty () || aArgs.get (0).isEmpty ())
    {
      // Implementation-dependent, but fixed for the whole execution
      nSeed = _hashString (aCtx.getCurrentDateTime ().toString ());
    }
    else
    {
      final XdmAtomic aSeed = (XdmAtomic) aArgs.get (0).get (0);
      nSeed = _hashString (aSeed.getType ().getLocalName () + "|" + aSeed.getLexical ());
    }
    return XdmSequence.of (_createGenerator (nSeed));
  }

  /**
   * Apply rule 1 of the aggregate functions: xs:untypedAtomic becomes
   * xs:double.
   */
  @NonNull
  private static XdmAtomic [] _getAggregateInput (@NonNull final XdmSequence aSeq)
  {
    final XdmAtomic [] ret = new XdmAtomic [aSeq.size ()];
    for (int i = 0; i < ret.length; ++i)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aSeq.get (i);
      ret[i] = aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC ? AtomicCaster.cast (aAtomic,
                                                                                      EAtomicType.DOUBLE,
                                                                                      null) : aAtomic;
    }
    return ret;
  }

  /**
   * Check the input of sum and avg: all numeric, or all xs:yearMonthDuration,
   * or all xs:dayTimeDuration.
   */
  private static void _checkSummable (@NonNull final XdmAtomic [] aValues, @NonNull final String sFunc)
  {
    final XdmAtomic aFirst = aValues[0];
    if (aFirst.isNumeric ())
    {
      for (final XdmAtomic aValue : aValues)
        if (!aValue.isNumeric ())
          throw XPathException.of ("FORG0006", "fn:" + sFunc + " cannot mix numeric and non-numeric values");
      return;
    }
    final EAtomicType eType = aFirst.getType ();
    if (!eType.isDerivedFrom (EAtomicType.YEAR_MONTH_DURATION) && !eType.isDerivedFrom (EAtomicType.DAY_TIME_DURATION))
      throw XPathException.of ("FORG0006", "fn:" + sFunc + " is not defined for " + eType.getQualifiedName ());
    final EAtomicType eFamily = eType.isDerivedFrom (EAtomicType.YEAR_MONTH_DURATION) ? EAtomicType.YEAR_MONTH_DURATION
                                                                                      : EAtomicType.DAY_TIME_DURATION;
    for (final XdmAtomic aValue : aValues)
      if (!aValue.getType ().isDerivedFrom (eFamily))
        throw XPathException.of ("FORG0006",
                                 "fn:" +
                                             sFunc +
                                             " cannot mix " +
                                             eFamily.getQualifiedName () +
                                             " and " +
                                             aValue.getType ().getQualifiedName ());
  }

  @NonNull
  private static XdmAtomic _sumOf (@NonNull final DynamicContext aCtx, @NonNull final XdmAtomic [] aValues)
  {
    XdmAtomic ret = aValues[0];
    for (int i = 1; i < aValues.length; ++i)
    {
      if ((i & 0x3ff) == 0)
        aCtx.checkInterrupt ();
      ret = AtomicArithmetic.arithmetic (EBinaryOp.ADD, ret, aValues[i], aCtx);
    }
    return ret;
  }

  @NonNull
  private static XdmSequence _sum (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic [] aValues = _getAggregateInput (aArgs.get (0));
    if (aValues.length == 0)
      return aArgs.size () > 1 ? aArgs.get (1) : _one (XdmAtomic.integer (0));
    _checkSummable (aValues, "sum");
    return _one (_sumOf (aCtx, aValues));
  }

  @NonNull
  private static XdmSequence _avg (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic [] aValues = _getAggregateInput (aArgs.get (0));
    if (aValues.length == 0)
      return XdmSequence.EMPTY;
    _checkSummable (aValues, "avg");
    final XdmAtomic aSum = _sumOf (aCtx, aValues);
    return _one (AtomicArithmetic.arithmetic (EBinaryOp.DIVIDE, aSum, XdmAtomic.integer (aValues.length), aCtx));
  }

  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return aPayload instanceof final Double aD && aD.isNaN () || aPayload instanceof final Float aF && aF.isNaN ();
  }

  @NonNull
  private static XdmSequence _minMax (@NonNull final DynamicContext aCtx,
                                      @NonNull final List <XdmSequence> aArgs,
                                      final boolean bMax)
  {
    final String sFunc = bMax ? "max" : "min";
    final XdmAtomic [] aValues = _getAggregateInput (aArgs.get (0));
    final ICollation aCollation = aArgs.size () > 1 ? FnSequence.getCollation (aCtx, aArgs.get (1))
                                                    : FnSequence.getDefaultCollation (aCtx);
    if (aValues.length == 0)
      return XdmSequence.EMPTY;

    boolean bAllNumeric = true;
    boolean bAnyDouble = false;
    boolean bAnyFloat = false;
    boolean bAnyNaN = false;
    boolean bAnyString = false;
    for (final XdmAtomic aValue : aValues)
    {
      final EAtomicType eType = aValue.getType ();
      if (aValue.isNumeric ())
      {
        if (eType == EAtomicType.DOUBLE)
          bAnyDouble = true;
        else
          if (eType == EAtomicType.FLOAT)
            bAnyFloat = true;
        if (_isNaN (aValue))
          bAnyNaN = true;
      }
      else
        bAllNumeric = false;
      if (eType.isStringFamily ())
        bAnyString = true;
    }

    XdmAtomic aBest = aValues[0];
    try
    {
      // Checks that the type is ordered at all (e.g. xs:duration is not)
      AtomicComparator.compareForOrder (aBest, aBest, aCollation, aCtx);
      for (int i = 1; i < aValues.length; ++i)
      {
        if ((i & 0x3ff) == 0)
          aCtx.checkInterrupt ();
        final XdmAtomic aValue = aValues[i];
        final int nCmp = AtomicComparator.compareForOrder (aValue, aBest, aCollation, aCtx);
        if (bMax ? nCmp > 0 : nCmp < 0)
          aBest = aValue;
      }
    }
    catch (final XPathException ex)
    {
      if ("XPTY0004".equals (ex.getCode ()))
        throw XPathException.of ("FORG0006", "fn:" + sFunc + ": " + ex.getDescription ());
      throw ex;
    }

    if (bAllNumeric)
    {
      if (bAnyNaN)
        return _one (bAnyDouble ? XdmAtomic.dbl (Double.NaN) : XdmAtomic.flt (Float.NaN));
      if (bAnyDouble)
        return _one (AtomicCaster.cast (aBest, EAtomicType.DOUBLE, null));
      if (bAnyFloat)
        return _one (AtomicCaster.cast (aBest, EAtomicType.FLOAT, null));
      return _one (aBest);
    }
    if (aBest.getType () == EAtomicType.ANY_URI && bAnyString)
      return _one (XdmAtomic.string (aBest.getString ()));
    return _one (aBest);
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "abs", "(xs:numeric?) as xs:numeric?", (aCtx, aArgs) -> _abs (aArgs));
    aLib.register (sNS, "ceiling", "(xs:numeric?) as xs:numeric?", (aCtx, aArgs) -> _floorCeiling (aArgs, false));
    aLib.register (sNS, "floor", "(xs:numeric?) as xs:numeric?", (aCtx, aArgs) -> _floorCeiling (aArgs, true));
    aLib.register (sNS, "round", "(xs:numeric?) as xs:numeric?", (aCtx, aArgs) -> _round (aArgs, false));
    aLib.register (sNS, "round", "(xs:numeric?, xs:integer) as xs:numeric?", (aCtx, aArgs) -> _round (aArgs, false));
    aLib.register (sNS,
                   "round-half-to-even",
                   "(xs:numeric?) as xs:numeric?",
                   (aCtx, aArgs) -> _round (aArgs, true));
    aLib.register (sNS,
                   "round-half-to-even",
                   "(xs:numeric?, xs:integer) as xs:numeric?",
                   (aCtx, aArgs) -> _round (aArgs, true));
    aLib.register (sNS, "number", "() as xs:double", FnNumeric::_number, true);
    aLib.register (sNS, "number", "(xs:anyAtomicType?) as xs:double", FnNumeric::_number);
    aLib.register (sNS,
                   "random-number-generator",
                   "() as map(xs:string, item())",
                   FnNumeric::_randomNumberGenerator);
    aLib.register (sNS,
                   "random-number-generator",
                   "(xs:anyAtomicType?) as map(xs:string, item())",
                   FnNumeric::_randomNumberGenerator);
    aLib.register (sNS,
                   "count",
                   "(item()*) as xs:integer",
                   (aCtx, aArgs) -> _one (XdmAtomic.integer (aArgs.get (0).size ())));
    aLib.register (sNS, "sum", "(xs:anyAtomicType*) as xs:anyAtomicType", FnNumeric::_sum);
    aLib.register (sNS, "sum", "(xs:anyAtomicType*, xs:anyAtomicType?) as xs:anyAtomicType?", FnNumeric::_sum);
    aLib.register (sNS, "avg", "(xs:anyAtomicType*) as xs:anyAtomicType?", FnNumeric::_avg);
    aLib.register (sNS,
                   "max",
                   "(xs:anyAtomicType*) as xs:anyAtomicType?",
                   (aCtx, aArgs) -> _minMax (aCtx, aArgs, true));
    aLib.register (sNS,
                   "max",
                   "(xs:anyAtomicType*, xs:string) as xs:anyAtomicType?",
                   (aCtx, aArgs) -> _minMax (aCtx, aArgs, true));
    aLib.register (sNS,
                   "min",
                   "(xs:anyAtomicType*) as xs:anyAtomicType?",
                   (aCtx, aArgs) -> _minMax (aCtx, aArgs, false));
    aLib.register (sNS,
                   "min",
                   "(xs:anyAtomicType*, xs:string) as xs:anyAtomicType?",
                   (aCtx, aArgs) -> _minMax (aCtx, aArgs, false));
  }
}
