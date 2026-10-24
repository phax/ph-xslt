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
package com.helger.xpath.value;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

/**
 * A function item: a named function reference, an inline function, a partial
 * application or a map or array used as a function.
 *
 * @author Philip Helger
 */
public interface IXdmFunction extends IXdmItem
{
  /** @return The function name, or <code>null</code> for an anonymous function. */
  @Nullable
  XdmQName getName ();

  int getArity ();

  /**
   * @return The declared parameter types, always {@link #getArity()} entries;
   *         an entry is <code>item()*</code> where nothing was declared.
   */
  @NonNull
  List <SeqType> getParamTypes ();

  @NonNull
  SeqType getReturnType ();

  /**
   * Invoke the function. The arguments are already converted to the parameter
   * types (function conversion rules); the result is not yet checked against
   * the return type.
   *
   * @param aCtx
   *        The caller's dynamic context; may be ignored by closures.
   * @param aArgs
   *        Exactly {@link #getArity()} arguments.
   * @return The result sequence
   */
  @NonNull
  XdmSequence call (@NonNull DynamicContext aCtx, @NonNull List <XdmSequence> aArgs);
}
