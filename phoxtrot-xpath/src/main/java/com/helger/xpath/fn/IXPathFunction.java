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

import org.jspecify.annotations.NonNull;

import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmSequence;

/**
 * Implementation of a built-in function. The evaluator has already applied the
 * function conversion rules of the registered signature to every argument
 * (atomisation, untypedAtomic casting, numeric promotion, cardinality check),
 * so an implementation can rely on the declared parameter types.
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IXPathFunction
{
  /**
   * @param aCtx
   *        Dynamic context of the call (focus = the caller's focus).
   * @param aArgs
   *        The converted arguments, one per parameter.
   * @return The result; it is not checked against the declared return type.
   */
  @NonNull
  XdmSequence call (@NonNull DynamicContext aCtx, @NonNull List <XdmSequence> aArgs);
}
