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
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * The result of a partial function application (XPath 3.1 section 3.1.5.1):
 * the bound arguments are already converted, the remaining parameters are the
 * placeholders in order. The result is anonymous.
 *
 * @author Philip Helger
 */
public final class PartialFunctionItem extends AbstractFunctionItem
{
  private final IXdmFunction m_aBase;
  // null entries are the placeholders
  private final XdmSequence [] m_aBound;
  private final ICommonsList <SeqType> m_aParamTypes = new CommonsArrayList <> ();

  /**
   * @param aBase
   *        The function being applied.
   * @param aBound
   *        One entry per parameter of the base function: the converted
   *        argument, or <code>null</code> for a placeholder.
   */
  public PartialFunctionItem (@NonNull final IXdmFunction aBase, @NonNull final XdmSequence [] aBound)
  {
    m_aBase = aBase;
    m_aBound = aBound.clone ();
    final List <SeqType> aBaseTypes = aBase.getParamTypes ();
    for (int i = 0; i < m_aBound.length; ++i)
      if (m_aBound[i] == null)
        m_aParamTypes.add (aBaseTypes.get (i));
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
    return m_aBase.getReturnType ();
  }

  @Override
  boolean isResultTrusted ()
  {
    return m_aBase instanceof final AbstractFunctionItem aBase && aBase.isResultTrusted ();
  }

  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final ICommonsList <XdmSequence> aFilled = new CommonsArrayList <> (m_aBound.length);
    int nSupplied = 0;
    for (final XdmSequence aBound : m_aBound)
      aFilled.add (aBound != null ? aBound : aArgs.get (nSupplied++));
    return m_aBase.call (aCtx, aFilled);
  }

  @Override
  public String toString ()
  {
    return "partial(" + m_aBase + ")#" + getArity ();
  }
}
