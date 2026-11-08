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
package com.helger.phoxtrot.xpath.stream;

import org.jspecify.annotations.NonNull;

/**
 * One operand of a construct, already classified, as input to the general
 * streamability rules (XSLT 3.0 section 19.8.1, see
 * {@link XPathStreamability#applyGeneralRules(java.util.List, StreamContext)}).
 *
 * @param posture
 *        The posture of the operand.
 * @param sweep
 *        The sweep of the operand.
 * @param usage
 *        The usage of the operand role.
 * @param childless
 *        The static type of the operand cannot include nodes with children,
 *        which downgrades absorption to inspection (section 19.8.1). It is only
 *        ever set when childlessness is proven; unknown stays
 *        <code>false</code>, the conservative side.
 * @param higherOrder
 *        The operand is evaluated more than once, or under a function item: a
 *        single potentially consuming higher-order operand makes the construct
 *        roaming.
 * @param choice
 *        Groups operands of which at most one is evaluated (if/then/else,
 *        <code>xsl:choose</code> branches, <code>xsl:catch</code> clauses). 0
 *        means "not in a group"; operands sharing a positive value form one
 *        group.
 * @param recUsage
 *        The usage is the one the Recommendation itself states for this
 *        operand role, rather than one the analysis fell back to. It only
 *        matters for {@link EStreamUsage#NAVIGATION}: only a navigation usage
 *        stated by the Recommendation (for example <code>fn:reverse</code>,
 *        <code>fn:innermost</code>, the value of a map entry) licenses an
 *        XTSE3430 diagnostic, see {@link StreamContext#markDefinite()}.
 * @author Philip Helger
 */
public record StreamOperand (@NonNull EPosture posture,
                             @NonNull ESweep sweep,
                             @NonNull EStreamUsage usage,
                             boolean childless,
                             boolean higherOrder,
                             int choice,
                             boolean recUsage)
{
  /**
   * An operand that is not childless, not higher-order, in no choice group and
   * with a fall-back usage.
   *
   * @param posture
   *        The posture of the operand.
   * @param sweep
   *        The sweep of the operand.
   * @param usage
   *        The usage of the operand role.
   */
  public StreamOperand (@NonNull final EPosture posture, @NonNull final ESweep sweep, @NonNull final EStreamUsage usage)
  {
    this (posture, sweep, usage, false, false, 0, false);
  }

  /**
   * @param aPS
   *        Posture and sweep of the operand.
   * @param eUsage
   *        The usage of the operand role.
   * @return A new operand that is not childless, not higher-order, in no choice
   *         group and with a fall-back usage.
   */
  @NonNull
  public static StreamOperand of (@NonNull final PostureSweep aPS, @NonNull final EStreamUsage eUsage)
  {
    return new StreamOperand (aPS.posture (), aPS.sweep (), eUsage);
  }

  /**
   * @param bChildless
   *        The new childless flag.
   * @return A copy with the given childless flag.
   */
  @NonNull
  public StreamOperand withChildless (final boolean bChildless)
  {
    return new StreamOperand (posture, sweep, usage, bChildless, higherOrder, choice, recUsage);
  }

  /**
   * @param bHigherOrder
   *        The new higher-order flag.
   * @return A copy with the given higher-order flag.
   */
  @NonNull
  public StreamOperand withHigherOrder (final boolean bHigherOrder)
  {
    return new StreamOperand (posture, sweep, usage, childless, bHigherOrder, choice, recUsage);
  }

  /**
   * @param nChoice
   *        The new choice group, 0 for none.
   * @return A copy in the given choice group.
   */
  @NonNull
  public StreamOperand withChoice (final int nChoice)
  {
    return new StreamOperand (posture, sweep, usage, childless, higherOrder, nChoice, recUsage);
  }

  /**
   * @param bRecUsage
   *        The new flag.
   * @return A copy with the given "usage stated by the Recommendation" flag.
   */
  @NonNull
  public StreamOperand withRecUsage (final boolean bRecUsage)
  {
    return new StreamOperand (posture, sweep, usage, childless, higherOrder, choice, bRecUsage);
  }
}
