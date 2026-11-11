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

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * The result of {@link DomToXdm#convertWithMapping(Node, String)}: the XDM
 * counterpart of a DOM node, and the way back from every XDM node of the built
 * tree to the DOM node it was built from, e.g. to report a node matched by an
 * XPath expression as the node of the DOM the caller owns.
 * <ul>
 * <li>Elements, attributes (not namespace declarations), comments and
 * processing instructions map to their DOM node.</li>
 * <li>A text node maps to the first DOM text or CDATA section node of the run
 * it was merged from (adjacent text, CDATA sections and the text of entity
 * references become one XDM text node).</li>
 * <li>The document node maps to the DOM <code>Document</code> or
 * <code>DocumentFragment</code> at the top of the converted tree; for a DOM
 * tree without one (a detached element) it maps to nothing.</li>
 * </ul>
 * The mapping holds one entry per mapped node, so it keeps the DOM alive as
 * long as it is referenced. Immutable.
 *
 * @author Philip Helger
 */
@Immutable
public final class DomNodeMapping
{
  private final IXdmNode m_aXdmNode;
  private final Map <IXdmNode, Node> m_aDomNodes;

  /**
   * @param aXdmNode
   *        The XDM counterpart of the converted DOM node.
   * @param aDomNodes
   *        The DOM node of each mapped XDM node; taken over, not copied.
   */
  DomNodeMapping (@NonNull final IXdmNode aXdmNode, @NonNull final Map <IXdmNode, Node> aDomNodes)
  {
    m_aXdmNode = aXdmNode;
    m_aDomNodes = aDomNodes;
  }

  /**
   * @return The XDM counterpart of the converted DOM node, as
   *         {@link DomToXdm#convert(Node, String)} gives it. Never
   *         <code>null</code>.
   */
  @NonNull
  public IXdmNode getXdmNode ()
  {
    return m_aXdmNode;
  }

  /**
   * @param aXdmNode
   *        A node of the built XDM tree.
   * @return The DOM node it was built from, or <code>null</code> for a node
   *         without one (the document node of a detached element, or a node
   *         of another tree).
   */
  @Nullable
  public Node getDomNode (@Nullable final IXdmNode aXdmNode)
  {
    return aXdmNode == null ? null : m_aDomNodes.get (aXdmNode);
  }

  /** @return The number of mapped XDM nodes. */
  public int getMappedNodeCount ()
  {
    return m_aDomNodes.size ();
  }
}
