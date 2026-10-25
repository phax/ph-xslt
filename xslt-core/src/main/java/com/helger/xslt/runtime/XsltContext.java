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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

/**
 * The run-time context of an instruction: the XPath dynamic context (focus,
 * current item, variables, run-wide settings) whose host state is the
 * {@link XsltState}, plus the {@link Engine} of the transformation. Immutable;
 * every change creates a derived context.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltContext
{
  private final Engine m_aEngine;
  private final DynamicContext m_aDyn;

  public XsltContext (@NonNull final Engine aEngine, @NonNull final DynamicContext aDyn)
  {
    m_aEngine = aEngine;
    m_aDyn = aDyn;
  }

  /**
   * Get the XSLT context of an XPath function call made during a
   * transformation (for XSLT functions such as current-group()).
   *
   * @param aDyn
   *        The dynamic context the function was called with.
   * @return The XSLT context, or <code>null</code> outside a transformation
   *         (e.g. in a use-when expression).
   */
  @Nullable
  public static XsltContext of (@NonNull final DynamicContext aDyn)
  {
    if (aDyn.getHost () instanceof final Engine aEngine)
      return new XsltContext (aEngine, aDyn);
    return null;
  }

  @NonNull
  public Engine getEngine ()
  {
    return m_aEngine;
  }

  @NonNull
  public DynamicContext getDynamicContext ()
  {
    return m_aDyn;
  }

  /** @return The XSLT state, never <code>null</code>. */
  @NonNull
  public XsltState getState ()
  {
    return m_aDyn.getHostState () instanceof final XsltState aState ? aState : XsltState.EMPTY;
  }

  /** @return The context item or <code>null</code> if the focus is absent. */
  @Nullable
  public IXdmItem getContextItem ()
  {
    return m_aDyn.getContextItem ();
  }

  public int getPosition ()
  {
    return m_aDyn.getPosition ();
  }

  public int getSize ()
  {
    return m_aDyn.getSize ();
  }

  /**
   * Change the focus; the XSLT current item (fn:current()) follows the
   * context item.
   *
   * @param aItem
   *        New context item, <code>null</code> for an absent focus.
   * @param nPosition
   *        1-based position.
   * @param nSize
   *        Size.
   * @return The derived context.
   */
  @NonNull
  public XsltContext withFocus (@Nullable final IXdmItem aItem, final int nPosition, final int nSize)
  {
    return new XsltContext (m_aEngine, m_aDyn.withFocus (aItem, nPosition, nSize).withCurrentItem (aItem));
  }

  @NonNull
  public XsltContext withVariable (@NonNull final XdmQName aName, @NonNull final XdmSequence aValue)
  {
    return new XsltContext (m_aEngine, m_aDyn.withVariable (aName, aValue));
  }

  @NonNull
  public XsltContext withState (@NonNull final XsltState aState)
  {
    return new XsltContext (m_aEngine, m_aDyn.withHostState (aState));
  }

  /**
   * @return A context with the same state but without focus and without local
   *         variables (global variables remain visible).
   */
  @NonNull
  public XsltContext withoutFocusAndVariables ()
  {
    return new XsltContext (m_aEngine, m_aDyn.withoutFocusAndVariables ());
  }

  /**
   * @param aKey
   *        Slot key.
   * @param <T>
   *        Value type.
   * @return The value of the slot in the current XSLT state.
   */
  @Nullable
  public <T> T get (@NonNull final XsltStateKey <T> aKey)
  {
    return getState ().get (aKey);
  }

  /**
   * @param aKey
   *        Slot key.
   * @param aValue
   *        New value.
   * @param <T>
   *        Value type.
   * @return The derived context.
   */
  @NonNull
  public <T> XsltContext with (@NonNull final XsltStateKey <T> aKey, @Nullable final T aValue)
  {
    return withState (getState ().with (aKey, aValue));
  }
}
