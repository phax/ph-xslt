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
package com.helger.phoxtrot.xpath.eval;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.value.XdmArray;
import com.helger.phoxtrot.xpath.value.XdmMap;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Presents a map (signature <code>function(xs:anyAtomicType) as item()*</code>)
 * or an array (<code>function(xs:integer) as item()*</code>) as a function,
 * e.g. as the base of a partial application.
 *
 * @author Philip Helger
 */
final class MapArrayFunctionItem extends AbstractFunctionItem
{
  private final IXdmItem m_aMapOrArray;

  /**
   * @param aMapOrArray
   *        The map or array to present.
   * @throws IllegalArgumentException
   *         if the item is neither a map nor an array
   */
  MapArrayFunctionItem (@NonNull final IXdmItem aMapOrArray)
  {
    if (!(aMapOrArray instanceof XdmMap) && !(aMapOrArray instanceof XdmArray))
      throw new IllegalArgumentException ("Neither a map nor an array");
    m_aMapOrArray = aMapOrArray;
  }

  @Nullable
  public XdmQName getName ()
  {
    return null;
  }

  public int getArity ()
  {
    return 1;
  }

  /**
   * @return <code>xs:anyAtomicType</code> for a map, <code>xs:integer</code>
   *         for an array.
   */
  @NonNull
  public List <SeqType> getParamTypes ()
  {
    return List.of (m_aMapOrArray instanceof XdmMap ? SequenceTypeMatcher.ANY_ATOMIC_ONE
                                                    : SequenceTypeMatcher.INTEGER_ONE);
  }

  @NonNull
  public SeqType getReturnType ()
  {
    return SeqType.ANY;
  }

  @Override
  boolean isResultTrusted ()
  {
    return true;
  }

  /**
   * Look the argument up in the map (the empty sequence for an absent key) or
   * the array (FOAY0001 for an index out of bounds), through
   * {@link XPathEvaluator#callFunction(DynamicContext, IXdmItem, List)}.
   */
  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    return XPathEvaluator.callFunction (aCtx, m_aMapOrArray, aArgs);
  }
}
