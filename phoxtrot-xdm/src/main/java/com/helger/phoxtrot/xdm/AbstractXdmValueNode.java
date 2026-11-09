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
package com.helger.phoxtrot.xdm;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * Base class of the node kinds that have a raw value: attributes, text, comments, processing
 * instructions and namespace nodes.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract sealed class AbstractXdmValueNode extends AbstractXdmNode permits
                                                  XdmAttributeNode,
                                                  XdmTextNode,
                                                  XdmCommentNode,
                                                  XdmProcessingInstructionNode,
                                                  XdmNamespaceNode
{
  // The value: a String, except for a text node with appended text that was
  // not joined yet, which holds a StringBuilder (see XdmTextNode). One field
  // for both, so that a text node needs no field for its pending text
  private Object m_aValue;

  protected AbstractXdmValueNode (@NonNull final String sValue)
  {
    m_aValue = sValue;
  }

  /**
   * @return The value field as it is: a {@link String}, or for a text node with pending text a
   *         {@link StringBuilder}.
   */
  @NonNull
  final Object internalGetRawValue ()
  {
    return m_aValue;
  }

  /**
   * Store the value field as it is, without a check.
   *
   * @param aValue
   *        A {@link String}, or for a text node with pending text a {@link StringBuilder}.
   */
  final void internalSetRawValue (@NonNull final Object aValue)
  {
    m_aValue = aValue;
  }

  /**
   * The value field cast to {@link String}. A text node overrides this and joins pending text
   * first, so the cast cannot fail.
   */
  @Override
  @NonNull
  public String getValue ()
  {
    return (String) m_aValue;
  }

  @Override
  public void setValue (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    m_aValue = sValue;
  }

  @NonNull
  public final String getStringValue ()
  {
    return getValue ();
  }
}
