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

  @PresentForCodeCoverage
  private static final FnBoolean INSTANCE = new FnBoolean ();

  private FnBoolean ()
  {}

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "true", "() as xs:boolean", (aCtx, aArgs) -> TRUE_SEQ);
    aLib.register (sNS, "false", "() as xs:boolean", (aCtx, aArgs) -> FALSE_SEQ);
    aLib.register (sNS,
                   "boolean",
                   "(item()*) as xs:boolean",
                   (aCtx, aArgs) -> Atomizer.getEffectiveBooleanValue (aArgs.get (0)) ? TRUE_SEQ : FALSE_SEQ);
    aLib.register (sNS,
                   "not",
                   "(item()*) as xs:boolean",
                   (aCtx, aArgs) -> Atomizer.getEffectiveBooleanValue (aArgs.get (0)) ? FALSE_SEQ : TRUE_SEQ);
  }
}
