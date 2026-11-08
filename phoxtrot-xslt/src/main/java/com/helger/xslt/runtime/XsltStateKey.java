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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;

/**
 * Key of an extension slot of {@link XsltState}: instruction families keep
 * their per-context state (current group, current merge group, ...) there.
 * Keys are compared by identity, so declare each one once as a constant.
 *
 * @param <T>
 *        Type of the value.
 * @author Philip Helger
 */
@Immutable
public final class XsltStateKey <T>
{
  /** What happens to a slot value when control passes to another component. */
  public enum EScope
  {
    /** Kept in template invocations and stylesheet function calls. */
    KEPT,
    /**
     * Kept in template invocations, cleared in stylesheet function calls (and
     * for global variables and patterns, which start from an empty state).
     */
    CLEARED_IN_FUNCTIONS,
    /** Cleared in template invocations and stylesheet function calls. */
    CLEARED_IN_CALLS
  }

  private final String m_sName;
  private final EScope m_eScope;

  /**
   * @param sName
   *        A name for debugging.
   * @param eScope
   *        What happens to the value in calls.
   */
  public XsltStateKey (@NonNull final String sName, @NonNull final EScope eScope)
  {
    m_sName = sName;
    m_eScope = eScope;
  }

  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  @NonNull
  public EScope getScope ()
  {
    return m_eScope;
  }

  @Override
  public String toString ()
  {
    return m_sName;
  }
}
