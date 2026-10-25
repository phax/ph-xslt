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
package com.helger.xslt.runtime;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.XPathException;

/**
 * The execution limits of a transformation: the number of executed
 * instructions and the nesting depth of templates and functions, both
 * XPDY0130 when exceeded. A transformation started by
 * <code>fn:transform</code> spends from the budget of its caller (see
 * {@link com.helger.xslt.XsltTransformRequest#setSharedBudget(ExecutionBudget)}),
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
   *        The maximum number of instructions to execute.
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

  public void leaveDepth ()
  {
    m_nDepth--;
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
