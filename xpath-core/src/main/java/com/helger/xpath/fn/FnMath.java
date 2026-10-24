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
import java.util.function.DoubleUnaryOperator;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;

/**
 * Function family: the trigonometric and exponential functions in the math
 * namespace (math:pi, exp, exp10, log, log10, pow, sqrt, sin, cos, tan, asin,
 * acos, atan, atan2). {@link StrictMath} is used throughout, so the results do
 * not depend on the platform.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnMath
{
  @PresentForCodeCoverage
  private static final FnMath INSTANCE = new FnMath ();

  private FnMath ()
  {}

  @NonNull
  private static XdmSequence _unary (@NonNull final List <XdmSequence> aArgs, @NonNull final DoubleUnaryOperator aOp)
  {
    final XdmSequence aArg = aArgs.get (0);
    if (aArg.isEmpty ())
      return XdmSequence.EMPTY;
    return XdmSequence.of (XdmAtomic.dbl (aOp.applyAsDouble (((XdmAtomic) aArg.get (0)).getAsDouble ())));
  }

  /**
   * math:pow with the special cases of F&amp;O 3.1 section 4.8.10, which follow
   * IEEE 754-2008 <code>pow</code> and differ from {@link StrictMath#pow} for a
   * base of +1 or -1.
   *
   * @param x
   *        Base.
   * @param y
   *        Exponent.
   * @return x<sup>y</sup>
   */
  static double pow (final double x, final double y)
  {
    if (y == 0)
      return 1;
    if (x == 1)
      return 1;
    if (x == -1 && Double.isInfinite (y))
      return 1;
    return StrictMath.pow (x, y);
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = FunctionLibrary.NS_MATH;
    aLib.register (sNS, "pi", "() as xs:double", (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.dbl (Math.PI)));
    aLib.register (sNS, "exp", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::exp));
    aLib.register (sNS,
                   "exp10",
                   "(xs:double?) as xs:double?",
                   (aCtx, aArgs) -> _unary (aArgs, d -> pow (10, d)));
    aLib.register (sNS, "log", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::log));
    aLib.register (sNS, "log10", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::log10));
    aLib.register (sNS, "sqrt", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::sqrt));
    aLib.register (sNS, "sin", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::sin));
    aLib.register (sNS, "cos", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::cos));
    aLib.register (sNS, "tan", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::tan));
    aLib.register (sNS, "asin", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::asin));
    aLib.register (sNS, "acos", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::acos));
    aLib.register (sNS, "atan", "(xs:double?) as xs:double?", (aCtx, aArgs) -> _unary (aArgs, StrictMath::atan));
    aLib.register (sNS, "pow", "(xs:double?, xs:numeric) as xs:double?", (aCtx, aArgs) -> {
      final XdmSequence aX = aArgs.get (0);
      if (aX.isEmpty ())
        return XdmSequence.EMPTY;
      final double x = ((XdmAtomic) aX.get (0)).getAsDouble ();
      final double y = ((XdmAtomic) aArgs.get (1).get (0)).getAsDouble ();
      return XdmSequence.of (XdmAtomic.dbl (pow (x, y)));
    });
    aLib.register (sNS, "atan2", "(xs:double, xs:double) as xs:double", (aCtx, aArgs) -> {
      final double y = ((XdmAtomic) aArgs.get (0).get (0)).getAsDouble ();
      final double x = ((XdmAtomic) aArgs.get (1).get (0)).getAsDouble ();
      return XdmSequence.of (XdmAtomic.dbl (StrictMath.atan2 (y, x)));
    });
  }
}
