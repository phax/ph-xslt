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

import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

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

  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    return XPathEvaluator.callFunction (aCtx, m_aMapOrArray, aArgs);
  }
}
