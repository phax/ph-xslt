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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: fn:true, fn:false, fn:not, fn:boolean.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnBoolean
{
  private static final XdmSequence TRUE_SEQ = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE_SEQ = XdmSequence.of (XdmAtomic.FALSE);

  /**
   * The implementation of <code>fn:boolean</code>. The evaluator recognises it
   * in a static call and evaluates the argument only as far as its effective
   * boolean value needs.
   */
  public static final IXPathFunction BOOLEAN = (aCtx,
                                                aArgs) -> Atomizer.getEffectiveBooleanValue (aArgs.get (0)) ? TRUE_SEQ
                                                                                                            : FALSE_SEQ;

  /**
   * The implementation of <code>fn:not</code>. The evaluator recognises it in
   * a static call and evaluates the argument only as far as its effective
   * boolean value needs.
   */
  public static final IXPathFunction NOT = (aCtx,
                                            aArgs) -> Atomizer.getEffectiveBooleanValue (aArgs.get (0)) ? FALSE_SEQ
                                                                                                        : TRUE_SEQ;

  @PresentForCodeCoverage
  private static final FnBoolean INSTANCE = new FnBoolean ();

  private FnBoolean ()
  {}

  /**
   * Register <code>fn:true()</code>, <code>fn:false()</code> (F&amp;O 3.1
   * section 7.1), <code>fn:boolean($arg as item()*) as xs:boolean</code> and
   * <code>fn:not($arg as item()*) as xs:boolean</code> (section 7.3), the last
   * two as {@link #BOOLEAN} and {@link #NOT}. The effective boolean value
   * raises FORG0006 for a sequence that has none.
   *
   * @param aLib
   *        The library to register into.
   */
  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "true", "() as xs:boolean", (aCtx, aArgs) -> TRUE_SEQ);
    aLib.register (sNS, "false", "() as xs:boolean", (aCtx, aArgs) -> FALSE_SEQ);
    aLib.register (sNS, "boolean", "(item()*) as xs:boolean", BOOLEAN);
    aLib.register (sNS, "not", "(item()*) as xs:boolean", NOT);
  }
}
