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
package com.helger.phoxtrot.xslt.runtime;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xpath.XPathException;

/**
 * The execution limits of a transformation: the number of executed
 * instructions and the nesting depth of templates and functions, both
 * XPDY0130 when exceeded. A transformation started by
 * <code>fn:transform</code> spends from the budget of its caller (see
 * {@link com.helger.phoxtrot.xslt.XsltTransformRequest#setSharedBudget(ExecutionBudget)}),
 * so nesting transformations cannot multiply what one request may execute.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class ExecutionBudget
{
  private final long m_nMaxSteps;
  private final int m_nMaxDepth;
  private long m_nSteps;
  private int m_nDepth;

  /**
   * @param nMaxSteps
   *        The maximum number of instructions to execute;
   *        {@link Long#MAX_VALUE}
   *        ({@link com.helger.phoxtrot.xslt.XsltTransformRequest#UNLIMITED_STEPS})
   *        is never exceeded, so it means no step limit.
   * @param nMaxDepth
   *        The maximum nesting depth.
   */
  public ExecutionBudget (final long nMaxSteps, final int nMaxDepth)
  {
    m_nMaxSteps = nMaxSteps;
    m_nMaxDepth = nMaxDepth;
  }

  /**
   * Count one executed instruction.
   *
   * @return <code>true</code> every 1024 steps, when the caller should poll
   *         for cancellation.
   * @throws XPathException
   *         XPDY0130 when the budget is used up
   */
  public boolean step ()
  {
    // With Long.MAX_VALUE as limit the counter would wrap only after 2^63 steps
    if (++m_nSteps > m_nMaxSteps)
      throw new XPathException ("XPDY0130", "The transformation executed more than " + m_nMaxSteps + " instructions");
    return (m_nSteps & 1023) == 0;
  }

  /**
   * Enter a template or function invocation (or a nested transformation).
   *
   * @throws XPathException
   *         XPDY0130 when nested too deeply
   */
  public void enterDepth ()
  {
    if (++m_nDepth > m_nMaxDepth)
    {
      m_nDepth--;
      throw new XPathException ("XPDY0130", "Templates and functions are nested more than " + m_nMaxDepth + " levels deep");
    }
  }

  /**
   * Leave a template or function invocation: the counterpart of
   * {@link #enterDepth()}, called once for each call of it that did not
   * throw.
   */
  public void leaveDepth ()
  {
    m_nDepth--;
  }

  /**
   * @return The maximum number of instructions to execute,
   *         {@link Long#MAX_VALUE} for no limit.
   */
  public long getMaxSteps ()
  {
    return m_nMaxSteps;
  }

  /** @return The number of instructions executed so far. */
  public long getSteps ()
  {
    return m_nSteps;
  }

  /** @return The current nesting depth. */
  public int getDepth ()
  {
    return m_nDepth;
  }
}
