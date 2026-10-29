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
package com.helger.xpath.xdm;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * Base class of the node kinds that have a raw value: attributes, text,
 * comments, processing instructions and namespace nodes.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract sealed class AbstractXdmValueNode extends AbstractXdmNode permits XdmAttributeNode,
                                                                           XdmTextNode,
                                                                           XdmCommentNode,
                                                                           XdmProcessingInstructionNode,
                                                                           XdmNamespaceNode
{
  private String m_sValue;

  AbstractXdmValueNode (@NonNull final String sValue)
  {
    m_sValue = sValue;
  }

  @Override
  @NonNull
  public final String getValue ()
  {
    return m_sValue;
  }

  @Override
  public final void setValue (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    m_sValue = sValue;
  }

  @NonNull
  public final String getStringValue ()
  {
    return m_sValue;
  }
}
