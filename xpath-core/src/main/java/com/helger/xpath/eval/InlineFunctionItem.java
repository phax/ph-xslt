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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * The function item of an inline function expression: a closure over the
 * variables in scope where it was created. The focus is absent in its body
 * (XPath 3.1 section 3.1.7).
 *
 * @author Philip Helger
 */
public final class InlineFunctionItem extends AbstractFunctionItem
{
  private final IExpr.InlineFunctionExpr m_aExpr;
  private final ICommonsList <SeqType> m_aParamTypes;
  private final SeqType m_aReturnType;
  private final DynamicContext m_aClosure;

  /**
   * @param aExpr
   *        The inline function expression.
   * @param aCtx
   *        The context at creation; its variables are captured.
   */
  public InlineFunctionItem (final IExpr.@NonNull InlineFunctionExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    m_aExpr = aExpr;
    m_aParamTypes = new CommonsArrayList <> ();
    for (final IExpr.Param aParam : aExpr.params ())
      m_aParamTypes.add (aParam.type () != null ? aParam.type () : SeqType.ANY);
    m_aReturnType = aExpr.returnType () != null ? aExpr.returnType () : SeqType.ANY;
    m_aClosure = aCtx.withFocus (null, 0, 0);
  }

  @Nullable
  public XdmQName getName ()
  {
    return null;
  }

  public int getArity ()
  {
    return m_aParamTypes.size ();
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
    DynamicContext aBodyCtx = m_aClosure;
    final List <IExpr.Param> aParams = m_aExpr.params ();
    for (int i = 0; i < aParams.size (); ++i)
      aBodyCtx = aBodyCtx.withVariable (aParams.get (i).name (), aArgs.get (i));
    return XPathEvaluator.evaluateInternal (m_aExpr.body (), aBodyCtx);
  }

  @Override
  public String toString ()
  {
    return "function#" + getArity ();
  }
}
