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
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * An output that records what a sequence constructor writes, including which
 * nodes are new, so it can be inspected and then replayed into the real
 * output (xsl:on-empty, xsl:where-populated, xsl:try with rollback, ...). New
 * nodes are not finished until they are replayed or {@link #getSequence()} is
 * called.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class BufferedOutput implements IXsltOutput
{
  private final ICommonsList <IXdmItem> m_aItems = new CommonsArrayList <> ();
  private final ICommonsList <Boolean> m_aNew = new CommonsArrayList <> ();

  public void append (@NonNull final IXdmItem aItem)
  {
    m_aItems.add (aItem);
    m_aNew.add (Boolean.FALSE);
  }

  public void appendNew (@NonNull final XdmNode aNode)
  {
    m_aItems.add (aNode);
    m_aNew.add (Boolean.TRUE);
  }

  public int size ()
  {
    return m_aItems.size ();
  }

  @NonNull
  public IXdmItem get (final int nIndex)
  {
    return m_aItems.get (nIndex);
  }

  /**
   * @param nIndex
   *        Index.
   * @return <code>true</code> if the item at that index is a new node.
   */
  public boolean isNew (final int nIndex)
  {
    return m_aNew.get (nIndex).booleanValue ();
  }

  /**
   * Replay one recorded item into another output.
   *
   * @param nIndex
   *        Index.
   * @param aTarget
   *        Target output.
   */
  public void replay (final int nIndex, @NonNull final IXsltOutput aTarget)
  {
    if (isNew (nIndex))
      aTarget.appendNew ((XdmNode) m_aItems.get (nIndex));
    else
      aTarget.append (m_aItems.get (nIndex));
  }

  /**
   * Replay everything recorded into another output. Call at most once.
   *
   * @param aTarget
   *        Target output.
   */
  public void replayAll (@NonNull final IXsltOutput aTarget)
  {
    for (int i = 0; i < m_aItems.size (); ++i)
      replay (i, aTarget);
  }

  /**
   * @return The recorded items as a sequence; new nodes are finished. Use
   *         either this or the replay methods.
   */
  @NonNull
  public XdmSequence getSequence ()
  {
    for (int i = 0; i < m_aItems.size (); ++i)
      if (isNew (i))
        NamespaceFixup.finish ((XdmNode) m_aItems.get (i));
    return XdmSequence.of (m_aItems);
  }
}
