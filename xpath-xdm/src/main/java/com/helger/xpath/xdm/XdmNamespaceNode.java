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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;

/**
 * A namespace node. Its name is the prefix (none for the default namespace),
 * its value the namespace URI.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmNamespaceNode extends AbstractXdmValueNode
{
  private final XdmQName m_aName;
  // Position among the namespace nodes of the parent element
  private final int m_nNamespaceIndex;

  XdmNamespaceNode (@NonNull final String sPrefix,
                    @NonNull final String sNamespaceUri,
                    @Nonnegative final int nNamespaceIndex)
  {
    super (sNamespaceUri);
    m_aName = sPrefix.isEmpty () ? null : new XdmQName (sPrefix);
    m_nNamespaceIndex = nNamespaceIndex;
  }

  @Nonnegative
  int getNamespaceIndex ()
  {
    return m_nNamespaceIndex;
  }

  @NonNull
  public ENodeKind getKind ()
  {
    return ENodeKind.NAMESPACE;
  }

  @Override
  @Nullable
  public XdmQName getName ()
  {
    return m_aName;
  }
}
