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
package com.helger.phoxtrot.xdm.bridge;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.helger.annotation.concurrent.Immutable;

/**
 * A node list of an {@link XdmDomView}: the children of a node (read from the XDM node on each access) or a fixed
 * list of nodes (a read-only tree makes a live list and a snapshot the same).
 *
 * @author Philip Helger
 */
@Immutable
final class XdmDomNodeList implements NodeList
{
  private final AbstractXdmDomNode m_aParent;
  private final List <Node> m_aNodes;

  /**
   * @param aParent
   *        The node whose children are listed.
   */
  XdmDomNodeList (@NonNull final AbstractXdmDomNode aParent)
  {
    m_aParent = aParent;
    m_aNodes = null;
  }

  /**
   * @param aNodes
   *        The nodes to list; taken over, not copied.
   */
  XdmDomNodeList (@NonNull final List <Node> aNodes)
  {
    m_aParent = null;
    m_aNodes = aNodes;
  }

  @Nullable
  public Node item (final int nIndex)
  {
    if (nIndex < 0 || nIndex >= getLength ())
      return null;
    return m_aNodes != null ? m_aNodes.get (nIndex) : m_aParent.getChildAt (nIndex);
  }

  public int getLength ()
  {
    return m_aNodes != null ? m_aNodes.size () : m_aParent.getXdmNode ().getChildCount ();
  }
}
