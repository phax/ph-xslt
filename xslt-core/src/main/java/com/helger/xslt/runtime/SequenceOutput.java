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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;

/**
 * An output that keeps the items of a sequence constructor as they are. New
 * nodes are finished (namespace fixup, document order) when they are appended,
 * since they become visible as items immediately.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class SequenceOutput implements IXsltOutput
{
  private final XdmSequence.Builder m_aItems = new XdmSequence.Builder ();

  public void append (@NonNull final IXdmItem aItem)
  {
    m_aItems.add (aItem);
  }

  public void appendNew (@NonNull final IXdmNode aNode)
  {
    NamespaceFixup.finish (aNode);
    m_aItems.add (aNode);
  }

  /** @return The number of items so far. */
  public int size ()
  {
    return m_aItems.size ();
  }

  /** @return The collected items. */
  @NonNull
  public XdmSequence getSequence ()
  {
    return m_aItems.build ();
  }
}
