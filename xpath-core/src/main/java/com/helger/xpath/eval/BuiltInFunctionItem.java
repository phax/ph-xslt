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
package com.helger.xpath.eval;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * A function item for a function of a {@link com.helger.xpath.fn.FunctionLibrary}:
 * the result of a named function reference or of
 * <code>fn:function-lookup</code>. A function that depends on the focus or on
 * other parts of the dynamic context runs with the context captured when the
 * item was created.
 *
 * @author Philip Helger
 */
public final class BuiltInFunctionItem extends AbstractFunctionItem
{
  private final FunctionDef m_aDef;
  private final DynamicContext m_aCapturedCtx;

  /**
   * @param aDef
   *        The function definition.
   * @param aCapturedCtx
   *        The context the function runs with; <code>null</code> to use the
   *        caller's context.
   */
  public BuiltInFunctionItem (@NonNull final FunctionDef aDef, @Nullable final DynamicContext aCapturedCtx)
  {
    ValueEnforcer.notNull (aDef, "Def");
    m_aDef = aDef;
    m_aCapturedCtx = aCapturedCtx;
  }

  @NonNull
  public FunctionDef getFunctionDef ()
  {
    return m_aDef;
  }

  @NonNull
  public XdmQName getName ()
  {
    return m_aDef.getName ();
  }

  public int getArity ()
  {
    return m_aDef.getArity ();
  }

  @NonNull
  public List <SeqType> getParamTypes ()
  {
    return m_aDef.getParamTypes ();
  }

  @NonNull
  public SeqType getReturnType ()
  {
    return m_aDef.getReturnType ();
  }

  @Override
  boolean isResultTrusted ()
  {
    return true;
  }

  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    return m_aDef.getImpl ().call (m_aCapturedCtx != null ? m_aCapturedCtx : aCtx, aArgs);
  }

  @Override
  public String toString ()
  {
    return m_aDef.getName ().getEQName () + "#" + getArity ();
  }
}
