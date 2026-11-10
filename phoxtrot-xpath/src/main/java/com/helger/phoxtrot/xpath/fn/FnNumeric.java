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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.collation.ICollation;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.AtomicArithmetic;
import com.helger.phoxtrot.xpath.ops.AtomicCaster;
import com.helger.phoxtrot.xpath.ops.AtomicComparator;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.parser.XPathParser;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.IXdmFunction;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmMap;
import com.helger.phoxtrot.xpath.value.XdmSequence;

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

  /**
   * @param aSeq
   *        An optional atomic argument.
   * @return The value, or <code>null</code> for the empty sequence.
   */
  @Nullable
  private static XdmAtomic _optAtomic (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? null : (XdmAtomic) aSeq.get (0);
  }

  /**
   * <code>fn:abs($arg as xs:numeric?) as xs:numeric?</code> (F&amp;O 3.1
   * section 4.4.1): the absolute value in the type of the argument (an
   * xs:integer stays xs:integer, and so on).
   *
   * @param aArgs
   *        The argument.
   * @return The result, empty for an empty argument.
   */
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

  /**
   * <code>fn:floor</code> and <code>fn:ceiling</code> (F&amp;O 3.1 sections
   * 4.4.3 and 4.4.2): an xs:integer is returned unchanged, an xs:decimal is
   * rounded to scale 0, a float or double keeps its type (NaN, the infinities
   * and negative zero stay as they are).
   *
   * @param aArgs
   *        The argument.
   * @param bFloor
   *        <code>true</code> for floor, <code>false</code> for ceiling.
   * @return The result, empty for an empty argument.
   */
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

  /**
   * Round a double for <code>fn:round</code> or
   * <code>fn:round-half-to-even</code>. NaN, the infinities, zero and integral
   * values with a non-negative precision are returned unchanged, as is any
   * value for a precision above 1100 (more fraction digits than a double has).
   * Otherwise the exact binary value is rounded as a decimal with
   * {@link #_roundDecimal(BigDecimal, long, boolean)}, except the common case
   * of <code>fn:round</code> to precision 0, which is computed directly. A
   * zero result keeps the sign of the input.
   *
   * @param d
   *        The value.
   * @param nPrecision
   *        Number of fraction digits; negative to round to a power of ten.
   * @param bHalfEven
   *        <code>true</code> for round-half-to-even.
   * @return The rounded value.
   */
  private static double _roundDouble (final double d, final long nPrecision, final boolean bHalfEven)
  {
    if (Double.isNaN (d) || Double.isInfinite (d) || d == 0)
      return d;
    if (nPrecision >= 0 && d == Math.rint (d))
      return d;
    if (nPrecision > 1100)
      return d;
    final double ret;
    if (nPrecision == 0 && !bHalfEven)
    {
      // d is not integral here, so floor(d) and the fraction are both exact
      final double dFloor = Math.floor (d);
      ret = d - dFloor >= 0.5 ? dFloor + 1 : dFloor;
    }
    else
      ret = _roundDecimal (new BigDecimal (d), nPrecision, bHalfEven).doubleValue ();
    // A zero result keeps the sign of the input
    return ret == 0 ? Math.copySign (0.0, d) : ret;
  }

  /**
   * Round a float like {@link #_roundDouble(double, long, boolean)}: NaN, the
   * infinities, zero, integral values with a non-negative precision and any
   * precision above 200 return the value unchanged; otherwise its exact binary
   * value is rounded as a decimal. A zero result keeps the sign of the input.
   *
   * @param f
   *        The value.
   * @param nPrecision
   *        Number of fraction digits; negative to round to a power of ten.
   * @param bHalfEven
   *        <code>true</code> for round-half-to-even.
   * @return The rounded value.
   */
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

  /**
   * @param aArgs
   *        The arguments of <code>fn:round</code> or
   *        <code>fn:round-half-to-even</code>.
   * @return The <code>$precision</code> argument, 0 if absent, clamped to
   *         -100,000 to 100,000 (no value has more digits, so the result does
   *         not change).
   */
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

  /**
   * <code>fn:round($arg as xs:numeric?[, $precision as xs:integer]) as
   * xs:numeric?</code> and <code>fn:round-half-to-even</code> with the same
   * signature (F&amp;O 3.1 sections 4.4.4 and 4.4.5). The result has the type
   * of the argument; an xs:integer only changes with a negative precision.
   *
   * @param aArgs
   *        The arguments.
   * @param bHalfEven
   *        <code>true</code> for round-half-to-even, <code>false</code> for
   *        halves rounded towards positive infinity.
   * @return The result, empty for an empty argument.
   */
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

  /**
   * <code>fn:number([$arg as xs:anyAtomicType?]) as xs:double</code> (F&amp;O
   * 3.1 section 4.5.1). Without an argument the context item is atomised. A
   * numeric, string-like (except xs:anyURI) or boolean value is cast to
   * xs:double; an empty sequence, any other type and a failed cast give NaN.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        No argument or the value.
   * @return The double.
   * @throws XPathException
   *         XPDY0002 without argument and context item, XPTY0004 if the
   *         context item atomises to more than one value.
   */
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
    if (aAtomic.isNumeric () || (aAtomic.isStringLike () && eType != EAtomicType.ANY_URI) || eType == EAtomicType.BOOLEAN)
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

  /**
   * One step of the SplitMix64 generator: advance the state by the golden
   * gamma and mix it.
   *
   * @param nState
   *        The state.
   * @return A pseudo-random 64-bit value; a pure function of the state.
   */
  private static long _splitMix64 (final long nState)
  {
    long z = nState + GOLDEN_GAMMA;
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  /**
   * @param s
   *        A string.
   * @return The 64-bit FNV-1a hash of its UTF-8 bytes, stable across runs and
   *         platforms.
   */
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

    /**
     * @param nSeed
     *        The seed of the generator the function belongs to.
     * @param bPermute
     *        <code>true</code> for <code>permute</code>, <code>false</code> for
     *        <code>next</code>.
     */
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

    /**
     * <code>next</code> returns a new generator for the seed plus the golden
     * gamma; <code>permute</code> returns the items of its argument shuffled
     * with Fisher-Yates, driven by SplitMix64 from the seed. Both are
     * deterministic: the same generator always gives the same result.
     *
     * @param aCtx
     *        The dynamic context of the call (unused).
     * @param aArgs
     *        No argument for <code>next</code>, the sequence for
     *        <code>permute</code>.
     * @return The new generator or the permuted sequence.
     */
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

  /**
   * Create the result map of <code>fn:random-number-generator</code>:
   * <code>number</code> is a double in [0, 1) from the upper 53 bits of
   * SplitMix64 of the seed, <code>next</code> and <code>permute</code> are
   * anonymous function items over the same seed.
   *
   * @param nSeed
   *        The seed.
   * @return The map.
   */
  @NonNull
  private static XdmMap _createGenerator (final long nSeed)
  {
    final double dNumber = (_splitMix64 (nSeed) >>> 11) / (double) (1L << 53);
    return XdmMap.EMPTY.put (XdmAtomic.string ("number"), XdmSequence.of (XdmAtomic.dbl (dNumber)))
                       .put (XdmAtomic.string ("next"), XdmSequence.of (new RngFunction (nSeed, false)))
                       .put (XdmAtomic.string ("permute"), XdmSequence.of (new RngFunction (nSeed, true)));
  }

  /**
   * <code>fn:random-number-generator([$seed as xs:anyAtomicType?]) as
   * map(xs:string, item())</code> (F&amp;O 3.1 section 4.9.1). The seed is the
   * hash of the type name and the lexical form of the seed value, so equal
   * seeds give equal sequences in every run. Without a seed (or with an empty
   * one) it is the hash of the current date/time of the run, which stays fixed
   * for the whole execution.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        No argument or the seed.
   * @return The generator map.
   */
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
      // Optimisation: the untyped values of an untyped tree are cast without the type dispatch of AtomicCaster.cast
      ret[i] = aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC ? AtomicCaster.castStringToDouble (aAtomic.getString (),
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

  /**
   * Add the values from left to right with the <code>+</code> operator; checks
   * for interruption every 1024 values.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aValues
   *        At least one summable value.
   * @return The sum.
   * @throws XPathException
   *         The errors of the addition, e.g. FODT0002 for a duration
   *         overflow.
   */
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

  /**
   * <code>fn:sum($arg as xs:anyAtomicType*[, $zero as
   * xs:anyAtomicType?])</code> (F&amp;O 3.1 section 14.4.5). xs:untypedAtomic
   * values are cast to xs:double first. An empty input gives the zero argument
   * (possibly the empty sequence) or the xs:integer 0.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The sum.
   * @throws XPathException
   *         FORG0001 if an untyped value is not a number, FORG0006 for values
   *         that cannot be summed or mix numbers and durations or the two
   *         duration types.
   */
  @NonNull
  private static XdmSequence _sum (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic [] aValues = _getAggregateInput (aArgs.get (0));
    if (aValues.length == 0)
      return aArgs.size () > 1 ? aArgs.get (1) : _one (XdmAtomic.integer (0));
    _checkSummable (aValues, "sum");
    return _one (_sumOf (aCtx, aValues));
  }

  /**
   * <code>fn:avg($arg as xs:anyAtomicType*) as xs:anyAtomicType?</code>
   * (F&amp;O 3.1 section 14.4.2): the sum divided by the count with the
   * <code>div</code> operator, so an average of integers is an xs:decimal.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The values.
   * @return The average, empty for an empty input.
   * @throws XPathException
   *         FORG0001 or FORG0006 as for <code>fn:sum</code>.
   */
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

  /**
   * @param aValue
   *        An atomic value.
   * @return <code>true</code> if it is a float or double NaN.
   */
  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return (aPayload instanceof final Double aD && aD.isNaN ()) || (aPayload instanceof final Float aF && aF.isNaN ());
  }

  /**
   * <code>fn:min</code> and <code>fn:max</code> (<code>($arg as
   * xs:anyAtomicType*[, $collation as xs:string]) as xs:anyAtomicType?</code>,
   * F&amp;O 3.1 sections 14.4.3 and 14.4.4). xs:untypedAtomic values are cast
   * to xs:double; the values are compared with
   * {@link AtomicComparator#compareForOrder} under the collation (resolved
   * even for an empty input), and the first of equal extremes wins. For
   * numbers, any NaN gives NaN (xs:double if any value is a double, else
   * xs:float), and the result is promoted to xs:double or xs:float if any
   * value has that type. An xs:anyURI result among strings is returned as
   * xs:string.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @param bMax
   *        <code>true</code> for max, <code>false</code> for min.
   * @return The extreme value, empty for an empty input.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation, FORG0001 if an untyped
   *         value is not a number, FORG0006 if the values are not comparable
   *         or of a type without order.
   */
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

  /**
   * Register <code>fn:abs</code>, <code>fn:ceiling</code>,
   * <code>fn:floor</code>, <code>fn:round</code> and
   * <code>fn:round-half-to-even</code> (arity 1 and 2), <code>fn:number</code>
   * (arity 0 focus-dependent, and 1), <code>fn:random-number-generator</code>
   * (arity 0 and 1) and the aggregate functions <code>fn:count</code>,
   * <code>fn:sum</code> (arity 1 and 2), <code>fn:avg</code>,
   * <code>fn:max</code> and <code>fn:min</code> (arity 1 and 2).
   *
   * @param aLib
   *        The library to register into.
   */
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
