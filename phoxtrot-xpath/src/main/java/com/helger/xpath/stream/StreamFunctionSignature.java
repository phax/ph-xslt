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
package com.helger.xpath.stream;

import java.util.List;

import org.jspecify.annotations.NonNull;

/**
 * What XSLT 3.0 section 19.8.5 needs to know about a stylesheet function at a
 * call site: its category and the usages its signature gives each argument.
 * The constraints each category places on the function body are checked where
 * the declaration itself is analysed, not here.
 *
 * @param category
 *        The declared streamability category.
 * @param paramUsages
 *        The type-determined usage (section 19.4, see
 *        {@link XPathStreamability#getTypeDeterminedUsage(boolean, boolean)})
 *        of each declared parameter, in declaration order.
 * @param firstAdjust
 *        The usage that applies the function conversion rules to the first
 *        argument - what sections 19.8.5.5/.6 call its type-adjusted posture
 *        and sweep. It differs from the first parameter usage: a required type
 *        that triggers no conversion at all leaves the posture and sweep of the
 *        argument untouched ({@link EStreamUsage#TRANSMISSION}), where the
 *        type-determined usage would say navigation. That is the reading the
 *        worked shallow-descent example of the specification demands (its first
 *        parameter is <code>element()*</code> and it is stated to be
 *        streamable over a striding, consuming argument).
 * @param firstParamNodes
 *        The declared type of the first parameter admits document or element
 *        nodes ("the intersection of T0 with U{document-node(), element()}" of
 *        section 19.8.5.5), which decides the sweep of a descent call.
 * @param streamable
 *        The category is other than unclassified - what section 19.8.5 calls
 *        declared-streamable.
 * @author Philip Helger
 */
public record StreamFunctionSignature (@NonNull EStreamFunctionCategory category,
                                       @NonNull List <EStreamUsage> paramUsages,
                                       @NonNull EStreamUsage firstAdjust,
                                       boolean firstParamNodes,
                                       boolean streamable)
{
  /**
   * Constructor that copies the parameter usages.
   *
   * @param category
   *        The declared streamability category.
   * @param paramUsages
   *        The type-determined usage of each declared parameter.
   * @param firstAdjust
   *        The usage that applies the function conversion rules to the first
   *        argument.
   * @param firstParamNodes
   *        The declared type of the first parameter admits document or element
   *        nodes.
   * @param streamable
   *        The category is other than unclassified.
   */
  public StreamFunctionSignature
  {
    paramUsages = List.copyOf (paramUsages);
  }

  /**
   * @param nIndex
   *        0-based argument index.
   * @return The type-determined usage of that argument. The resolver matched on
   *         arity, so an index past the declared parameters cannot arise;
   *         navigation is the fail-closed answer if it ever does.
   */
  @NonNull
  public EStreamUsage getUsage (final int nIndex)
  {
    if (nIndex >= 0 && nIndex < paramUsages.size ())
      return paramUsages.get (nIndex);
    return EStreamUsage.NAVIGATION;
  }
}
