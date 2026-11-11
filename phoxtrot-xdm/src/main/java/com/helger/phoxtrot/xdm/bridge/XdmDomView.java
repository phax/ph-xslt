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
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * A read-only W3C DOM view over an XDM tree, so that a tree read with {@link com.helger.phoxtrot.xdm.XdmParser}
 * (or built otherwise) can be handed to code that expects <code>org.w3c.dom</code> nodes without building a DOM, e.g.
 * to report a node matched by an XPath expression. Unlike {@link XdmToDom} nothing is copied: each DOM node reads
 * its XDM node.
 * <ul>
 * <li>The XDM document, element, attribute, text, comment and processing instruction nodes appear as
 * {@link Document}, {@link org.w3c.dom.Element}, {@link org.w3c.dom.Attr}, {@link org.w3c.dom.Text},
 * {@link org.w3c.dom.Comment} and {@link org.w3c.dom.ProcessingInstruction}. Namespace declarations of an element are
 * <code>xmlns</code> attributes in its attribute map (before its attributes), as a namespace aware DOM parser gives
 * them; an XDM namespace node (namespace axis) is shown the same way, with its parent as owner element.</li>
 * <li>Every method that changes the tree throws a <code>DOMException</code> with
 * <code>NO_MODIFICATION_ALLOWED_ERR</code>; node creation, <code>cloneNode</code>, <code>importNode</code> and user
 * data throw one with <code>NOT_SUPPORTED_ERR</code>. <code>normalize</code> does nothing (adjacent text is merged in
 * XDM already).</li>
 * <li>The view gives one DOM node per XDM node, created on first access and then kept, so DOM nodes can be compared
 * by identity as in any DOM. The view therefore holds one object for every node visited so far, until it is
 * dropped.</li>
 * <li>There is no document type node: the entities and unparsed entities of the XDM document are not visible. CDATA
 * sections and entity references do not exist in XDM; their text is part of the text nodes.</li>
 * <li>A tree without document node (a parentless element) has no owner document.</li>
 * </ul>
 * The tree must not be changed while it is viewed. A view is thread-safe for reading. All walks (descendants,
 * <code>isEqualNode</code>, ancestors) are iterative.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XdmDomView
{
  private final IXdmNode m_aRoot;
  private final Map <IXdmNode, AbstractXdmDomNode> m_aNodes = new ConcurrentHashMap <> ();
  private final Map <IXdmNode, XdmDomNamedNodeMap> m_aAttributeMaps = new ConcurrentHashMap <> ();

  /**
   * Create a view over the whole tree a node belongs to.
   *
   * @param aNode
   *        Any node of the tree. May not be <code>null</code>.
   */
  public XdmDomView (@NonNull final IXdmNode aNode)
  {
    ValueEnforcer.notNull (aNode, "Node");
    m_aRoot = aNode.getRoot ();
  }

  /**
   * Create the DOM node of an XDM node of this view. Must not use the map of nodes itself (called from within its
   * <code>computeIfAbsent</code>).
   */
  @NonNull
  private AbstractXdmDomNode _create (@NonNull final IXdmNode aNode)
  {
    return switch (aNode.getKind ())
    {
      case DOCUMENT -> new XdmDomDocument (this, aNode);
      case ELEMENT -> new XdmDomElement (this, aNode);
      case ATTRIBUTE -> new XdmDomAttr (this, aNode);
      case TEXT -> new XdmDomText (this, aNode);
      case COMMENT -> new XdmDomComment (this, aNode);
      case PROCESSING_INSTRUCTION -> new XdmDomProcessingInstruction (this, aNode);
      case NAMESPACE -> new XdmDomNamespaceAttr (this, aNode, aNode.getParent ());
    };
  }

  /**
   * @param aNode
   *        A node of this view's tree.
   * @return The kept DOM node of the XDM node, created on first access.
   */
  @NonNull
  AbstractXdmDomNode internalWrap (@NonNull final IXdmNode aNode)
  {
    return m_aNodes.computeIfAbsent (aNode, this::_create);
  }

  /**
   * @param aElement
   *        The DOM node of an element of this view.
   * @return The kept attribute map of the element: its namespace declarations as <code>xmlns</code> attributes, then
   *         its attributes.
   */
  @NonNull
  XdmDomNamedNodeMap internalGetAttributeMap (@NonNull final XdmDomElement aElement)
  {
    return m_aAttributeMaps.computeIfAbsent (aElement.getXdmNode (), k -> XdmDomNamedNodeMap.createFor (aElement));
  }

  /**
   * @return The root of the viewed tree. Never <code>null</code>.
   */
  @NonNull
  public IXdmNode getRoot ()
  {
    return m_aRoot;
  }

  /**
   * @return The DOM document of the viewed tree, or <code>null</code> if the tree is not rooted in a document node.
   */
  @Nullable
  public Document getDocument ()
  {
    return m_aRoot.isDocument () ? (Document) internalWrap (m_aRoot) : null;
  }

  /**
   * Get the DOM node of an XDM node of the viewed tree. Repeated calls return the identical object.
   *
   * @param aNode
   *        A node of the viewed tree. May not be <code>null</code>.
   * @return The DOM node. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if the node belongs to another tree
   */
  @NonNull
  public Node getNode (@NonNull final IXdmNode aNode)
  {
    ValueEnforcer.notNull (aNode, "Node");
    if (aNode.getRoot () != m_aRoot)
      throw new IllegalArgumentException ("The node " + aNode + " does not belong to the viewed tree");
    return internalWrap (aNode);
  }

  /**
   * The way back from a DOM node of a view to its XDM node.
   *
   * @param aNode
   *        A DOM node. May be <code>null</code>.
   * @return The XDM node a DOM node of a view reads, <code>null</code> for <code>null</code> and for a DOM node that
   *         does not belong to a view. For the <code>xmlns</code> attribute of a namespace declaration it is a
   *         parentless namespace node.
   */
  @Nullable
  public static IXdmNode getXdmNode (@Nullable final Node aNode)
  {
    return aNode instanceof final AbstractXdmDomNode aViewNode ? aViewNode.getXdmNode () : null;
  }
}
