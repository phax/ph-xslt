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

import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

/**
 * The coerced function of XPath 3.1 section 3.1.5.3: it has the expected
 * signature and wraps a function item (function, map or array) whose own type
 * is not a subtype of the expected one. The caller converts the arguments to
 * the expected parameter types and the result to the expected return type; the
 * wrapped function then applies its own conversions.
 *
 * @author Philip Helger
 */
public final class CoercedFunctionItem extends AbstractFunctionItem
{
  private final IXdmItem m_aWrapped;
  private final ItemType m_aExpected;

  /**
   * @param aWrapped
   *        A function, map or array with the arity of the expected type.
   * @param aExpected
   *        A typed function test.
   */
  public CoercedFunctionItem (@NonNull final IXdmItem aWrapped, @NonNull final ItemType aExpected)
  {
    m_aWrapped = aWrapped;
    m_aExpected = aExpected;
  }

  @NonNull
  public IXdmItem getWrapped ()
  {
    return m_aWrapped;
  }

  @Nullable
  public XdmQName getName ()
  {
    return m_aWrapped instanceof final IXdmFunction aFunc ? aFunc.getName () : null;
  }

  public int getArity ()
  {
    return m_aExpected.paramTypes () == null ? 0 : m_aExpected.paramTypes ().size ();
  }

  @NonNull
  public List <SeqType> getParamTypes ()
  {
    return m_aExpected.paramTypes () == null ? List.of () : m_aExpected.paramTypes ();
  }

  @NonNull
  public SeqType getReturnType ()
  {
    return m_aExpected.returnType ();
  }

  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    return XPathEvaluator.callFunction (aCtx, m_aWrapped, aArgs);
  }

  @Override
  public String toString ()
  {
    return "coerced(" + m_aWrapped + ")";
  }
}
