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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;

/**
 * The base of every DOM node of an {@link XdmDomView}: one XDM node, read-only. See {@link XdmDomView} for what the
 * view shows and what it refuses.
 *
 * @author Philip Helger
 */
public abstract sealed class AbstractXdmDomNode implements Node permits XdmDomDocument, XdmDomElement, XdmDomAttr,
                                                    XdmDomNamespaceAttr, AbstractXdmDomCharacterData,
                                                    XdmDomProcessingInstruction
{
  /** One pair of nodes compared by {@link #isEqualNode(Node)}. */
  private record Pair (@NonNull Node left, @NonNull Node right)
  {}

  private final XdmDomView m_aView;
  private final IXdmNode m_aNode;
  // The position among the children of the parent, -1 if not known yet. Every writer stores the same value.
  private int m_nIndex = -1;

  AbstractXdmDomNode (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode)
  {
    m_aView = aView;
    m_aNode = aNode;
  }

  /**
   * @return A new exception for a method that would change the tree.
   */
  @NonNull
  static DOMException createReadOnly ()
  {
    return new DOMException (DOMException.NO_MODIFICATION_ALLOWED_ERR, "The DOM view of an XDM tree is read-only");
  }

  /**
   * @param sWhat
   *        What is not supported.
   * @return A new exception for a method the view does not support.
   */
  @NonNull
  static DOMException createNotSupported (@NonNull final String sWhat)
  {
    return new DOMException (DOMException.NOT_SUPPORTED_ERR, sWhat + " is not supported by the DOM view of an XDM tree");
  }

  @Nullable
  static String nullIfEmpty (@Nullable final String s)
  {
    return s == null || s.isEmpty () ? null : s;
  }

  @NonNull
  private static String _nonNull (@Nullable final String s)
  {
    return s == null ? "" : s;
  }

  /**
   * @return The reference resolved against the base, <code>null</code> if either is no valid URI.
   */
  @Nullable
  private static String _resolve (@NonNull final String sRef, @NonNull final String sBase)
  {
    try
    {
      return new URI (sBase).resolve (new URI (sRef)).toString ();
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      return null;
    }
  }

  @Nullable
  private static String _getAbsoluteOrNull (@Nullable final String sUri)
  {
    if (sUri == null)
      return null;
    try
    {
      return new URI (sUri).isAbsolute () ? sUri : null;
    }
    catch (final URISyntaxException ex)
    {
      return null;
    }
  }

  /**
   * The base URI of an XDM node (XDM 3.1 section 5.2, XML Base): the <code>xml:base</code> attributes of the
   * ancestor-or-self elements, innermost resolved against outermost, all resolved against the explicit base URI of
   * the nearest node that has one, or the document URI. A reference that cannot be resolved keeps the base of the
   * parent.
   */
  @Nullable
  static String getBaseUri (@NonNull final IXdmNode aNode)
  {
    switch (aNode.getKind ())
    {
      case NAMESPACE:
        return null;
      case ATTRIBUTE:
      case TEXT:
      case COMMENT:
      case PROCESSING_INSTRUCTION:
        // Their base URI is that of their parent; with no parent there is none
        if (aNode.getParent () == null && aNode.getBaseUri () == null)
          return null;
        break;
      default:
        break;
    }
    final ICommonsList <String> aChain = new CommonsArrayList <> ();
    String sBase = null;
    for (IXdmNode aCur = aNode; aCur != null; aCur = aCur.getParent ())
    {
      if (aCur.isElement ())
      {
        final IXdmNode aXmlBase = aCur.getAttribute (XdmConstants.NS_XML, "base");
        if (aXmlBase != null)
          aChain.add (aXmlBase.getValue ());
      }
      if (aCur.getBaseUri () != null)
      {
        sBase = aCur.getBaseUri ();
        break;
      }
      if (aCur.isDocument ())
      {
        sBase = aCur.getDocumentUri ();
        break;
      }
    }
    for (int i = aChain.size () - 1; i >= 0; --i)
    {
      final String sRef = aChain.get (i);
      if (_getAbsoluteOrNull (sBase) == null)
        sBase = sRef;
      else
      {
        final String sResolved = _resolve (sRef, sBase);
        if (sResolved != null)
          sBase = sResolved;
      }
    }
    return sBase;
  }

  /**
   * @return <code>true</code> if the names, values and attributes of the two nodes are equal as
   *         {@link Node#isEqualNode(Node)} defines it, without looking at the children. Attribute pairs to compare in
   *         depth are pushed.
   */
  private static boolean _isShallowEqual (@NonNull final Node aLeft,
                                          @NonNull final Node aRight,
                                          @NonNull final ArrayDeque <Pair> aStack)
  {
    if (aLeft.getNodeType () != aRight.getNodeType () ||
        !Objects.equals (aLeft.getNodeName (), aRight.getNodeName ()) ||
        !Objects.equals (aLeft.getLocalName (), aRight.getLocalName ()) ||
        !Objects.equals (aLeft.getNamespaceURI (), aRight.getNamespaceURI ()) ||
        !Objects.equals (aLeft.getPrefix (), aRight.getPrefix ()) ||
        !Objects.equals (aLeft.getNodeValue (), aRight.getNodeValue ()))
      return false;
    final NamedNodeMap aLeftAttrs = aLeft.getAttributes ();
    final NamedNodeMap aRightAttrs = aRight.getAttributes ();
    if (aLeftAttrs == null || aRightAttrs == null)
      return aLeftAttrs == aRightAttrs;
    if (aLeftAttrs.getLength () != aRightAttrs.getLength ())
      return false;
    for (int i = 0; i < aLeftAttrs.getLength (); ++i)
    {
      final Node aAttr = aLeftAttrs.item (i);
      final Node aOther = aAttr.getLocalName () != null ? aRightAttrs.getNamedItemNS (aAttr.getNamespaceURI (),
                                                                                     aAttr.getLocalName ())
                                                        : aRightAttrs.getNamedItem (aAttr.getNodeName ());
      if (aOther == null)
        return false;
      aStack.push (new Pair (aAttr, aOther));
    }
    return true;
  }

  /**
   * Collect the descendant elements of an XDM node in document order, iteratively.
   */
  @NonNull
  final NodeList collectDescendantElements (@NonNull final Predicate <IXdmNode> aFilter)
  {
    final ICommonsList <Node> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (int i = m_aNode.getChildCount () - 1; i >= 0; --i)
      aStack.push (m_aNode.getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.isElement ())
      {
        if (aFilter.test (aCur))
          ret.add (m_aView.internalWrap (aCur));
        for (int i = aCur.getChildCount () - 1; i >= 0; --i)
          aStack.push (aCur.getChildAtIndex (i));
      }
    }
    return new XdmDomNodeList (ret);
  }

  /**
   * @return A filter for {@link #collectDescendantElements(Predicate)} that matches the DOM tag name, where
   *         <code>*</code> matches every element.
   */
  @NonNull
  static Predicate <IXdmNode> createTagNameFilter (@Nullable final String sName)
  {
    if ("*".equals (sName))
      return x -> true;
    return x -> x.getName ().getLexicalName ().equals (sName);
  }

  /**
   * @return A filter for {@link #collectDescendantElements(Predicate)} that matches the namespace URI and local name,
   *         where <code>*</code> matches every value.
   */
  @NonNull
  static Predicate <IXdmNode> createNameNSFilter (@Nullable final String sNamespaceUri, @Nullable final String sLocalName)
  {
    final boolean bAnyNamespace = "*".equals (sNamespaceUri);
    final boolean bAnyLocalName = "*".equals (sLocalName);
    final String sNs = _nonNull (sNamespaceUri);
    return x -> (bAnyNamespace || x.getName ().namespaceUri ().equals (sNs)) &&
                (bAnyLocalName || x.getName ().localName ().equals (sLocalName));
  }

  /**
   * @return The view this node belongs to. Never <code>null</code>.
   */
  @NonNull
  public final XdmDomView getView ()
  {
    return m_aView;
  }

  /**
   * @return The XDM node this DOM node reads. For the <code>xmlns</code> attribute of a namespace declaration it is a
   *         parentless namespace node. Never <code>null</code>.
   */
  @NonNull
  public final IXdmNode getXdmNode ()
  {
    return m_aNode;
  }

  /**
   * @return The DOM node of the child at the index, which learns its index on the way.
   */
  @NonNull
  final AbstractXdmDomNode getChildAt (final int nIndex)
  {
    final AbstractXdmDomNode ret = m_aView.internalWrap (m_aNode.getChildAtIndex (nIndex));
    ret.m_nIndex = nIndex;
    return ret;
  }

  /**
   * @return The position of this node among the children of its XDM parent, -1 for a node without parent and for
   *         attribute-like nodes. Found once by a scan of the parent's children if the node was not reached from its
   *         parent or a sibling.
   */
  private int _getIndexInParent ()
  {
    if (m_nIndex < 0)
    {
      final IXdmNode aParent = m_aNode.getParent ();
      if (aParent != null)
        for (int i = 0; i < aParent.getChildCount (); ++i)
          if (aParent.getChildAtIndex (i) == m_aNode)
          {
            m_nIndex = i;
            break;
          }
    }
    return m_nIndex;
  }

  /**
   * @return The sibling at the offset from this node, or <code>null</code>.
   */
  @Nullable
  private Node _getSibling (final int nOffset)
  {
    final IXdmNode aParent = m_aNode.getParent ();
    if (aParent == null || isAttributeLike ())
      return null;
    final int nIndex = _getIndexInParent () + nOffset;
    if (nIndex < 0 || nIndex >= aParent.getChildCount ())
      return null;
    return ((AbstractXdmDomNode) m_aView.internalWrap (aParent)).getChildAt (nIndex);
  }

  /**
   * @return <code>true</code> for attributes and namespace declarations, which have no parent and no siblings in
   *         DOM.
   */
  boolean isAttributeLike ()
  {
    return false;
  }

  /**
   * @return The XDM element whose namespaces are in scope for this node: the element itself, the owner element of an
   *         attribute, the parent element of other nodes, the document element of a document.
   */
  @Nullable
  IXdmNode getNamespaceContextElement ()
  {
    final IXdmNode aParent = m_aNode.getParent ();
    return aParent != null && aParent.isElement () ? aParent : null;
  }

  /**
   * @return The XDM node that stands for this node in document order and containment: the node itself, the owner
   *         element of a namespace declaration.
   */
  @NonNull
  IXdmNode getOrderNode ()
  {
    return m_aNode;
  }

  /**
   * @return The XDM element an attribute-like node belongs to, <code>null</code> for other nodes and for a parentless
   *         attribute.
   */
  @Nullable
  IXdmNode getOwnerXdmElement ()
  {
    return null;
  }

  /**
   * @return <code>true</code> if the XDM node is the order node of this node or one of its ancestors.
   */
  private boolean _hasAncestorOrSelf (@NonNull final IXdmNode aNode)
  {
    for (IXdmNode aCur = getOrderNode (); aCur != null; aCur = aCur.getParent ())
      if (aCur == aNode)
        return true;
    return false;
  }

  /**
   * @return The order of a node of another tree relative to this one: negative if it comes first.
   */
  private int _compareForeign (@NonNull final Node aOther)
  {
    if (aOther instanceof final AbstractXdmDomNode aOtherNode)
      return IXdmNode.compareDocumentOrder (aOtherNode.getOrderNode (), getOrderNode ());
    return Integer.compare (System.identityHashCode (aOther), System.identityHashCode (this));
  }

  public Node getParentNode ()
  {
    final IXdmNode aParent = m_aNode.getParent ();
    return aParent == null ? null : m_aView.internalWrap (aParent);
  }

  public String getNodeValue ()
  {
    return null;
  }

  public void setNodeValue (final String sNodeValue)
  {
    // Setting a value that is defined as null has no effect
    if (getNodeValue () != null)
      throw createReadOnly ();
  }

  public NodeList getChildNodes ()
  {
    return new XdmDomNodeList (this);
  }

  public Node getFirstChild ()
  {
    return m_aNode.getChildCount () == 0 ? null : getChildAt (0);
  }

  public Node getLastChild ()
  {
    final int nCount = m_aNode.getChildCount ();
    return nCount == 0 ? null : getChildAt (nCount - 1);
  }

  public Node getPreviousSibling ()
  {
    return _getSibling (-1);
  }

  public Node getNextSibling ()
  {
    return _getSibling (1);
  }

  public NamedNodeMap getAttributes ()
  {
    return null;
  }

  public Document getOwnerDocument ()
  {
    return m_aView.getDocument ();
  }

  public Node insertBefore (final Node aNewChild, final Node aRefChild)
  {
    throw createReadOnly ();
  }

  public Node replaceChild (final Node aNewChild, final Node aOldChild)
  {
    throw createReadOnly ();
  }

  public Node removeChild (final Node aOldChild)
  {
    throw createReadOnly ();
  }

  public Node appendChild (final Node aNewChild)
  {
    throw createReadOnly ();
  }

  public boolean hasChildNodes ()
  {
    return m_aNode.getChildCount () > 0;
  }

  public Node cloneNode (final boolean bDeep)
  {
    throw createNotSupported ("cloneNode");
  }

  public void normalize ()
  {
    // Nothing to do: XDM merges adjacent text already
  }

  public boolean isSupported (final String sFeature, final String sVersion)
  {
    return XdmDomImplementation.INSTANCE.hasFeature (sFeature, sVersion);
  }

  public String getNamespaceURI ()
  {
    return null;
  }

  public String getPrefix ()
  {
    return null;
  }

  public void setPrefix (final String sPrefix)
  {
    throw createReadOnly ();
  }

  public String getLocalName ()
  {
    return null;
  }

  public boolean hasAttributes ()
  {
    return false;
  }

  public String getBaseURI ()
  {
    return getBaseUri (m_aNode);
  }

  public short compareDocumentPosition (final Node aOther)
  {
    if (aOther == this)
      return 0;
    if (!(aOther instanceof final AbstractXdmDomNode aOtherNode) || aOtherNode.m_aView != m_aView)
    {
      // Another tree: disconnected, in an order that stays the same between calls
      final int nCmp = _compareForeign (aOther);
      return (short) (DOCUMENT_POSITION_DISCONNECTED |
                      DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC |
                      (nCmp < 0 ? DOCUMENT_POSITION_PRECEDING : DOCUMENT_POSITION_FOLLOWING));
    }
    // Attribute-like nodes contain nothing; for the others the XDM node is the order node
    if (!aOtherNode.isAttributeLike () && _hasAncestorOrSelf (aOtherNode.getXdmNode ()))
      return DOCUMENT_POSITION_CONTAINS | DOCUMENT_POSITION_PRECEDING;
    if (!isAttributeLike () && aOtherNode._hasAncestorOrSelf (m_aNode))
      return DOCUMENT_POSITION_CONTAINED_BY | DOCUMENT_POSITION_FOLLOWING;
    final IXdmNode aOwner = getOwnerXdmElement ();
    if (aOwner != null && aOwner == aOtherNode.getOwnerXdmElement ())
    {
      // Two attributes of one element: the order of the attribute map
      final XdmDomNamedNodeMap aAttrs = m_aView.internalGetAttributeMap ((XdmDomElement) m_aView.internalWrap (aOwner));
      final int nCmp = Integer.compare (aAttrs.indexOf (aOtherNode), aAttrs.indexOf (this));
      return (short) (DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC |
                      (nCmp < 0 ? DOCUMENT_POSITION_PRECEDING : DOCUMENT_POSITION_FOLLOWING));
    }
    return IXdmNode.compareDocumentOrder (aOtherNode.getOrderNode (), getOrderNode ()) < 0 ? DOCUMENT_POSITION_PRECEDING
                                                                                          : DOCUMENT_POSITION_FOLLOWING;
  }

  public String getTextContent ()
  {
    return m_aNode.getStringValue ();
  }

  public void setTextContent (final String sTextContent)
  {
    throw createReadOnly ();
  }

  public boolean isSameNode (final Node aOther)
  {
    // One DOM node per XDM node and view
    return aOther == this;
  }

  public String lookupPrefix (final String sNamespaceUri)
  {
    final IXdmNode aElement = getNamespaceContextElement ();
    if (aElement == null || sNamespaceUri == null || sNamespaceUri.isEmpty ())
      return null;
    for (final Map.Entry <String, String> aEntry : aElement.getInScopeNamespaces ().entrySet ())
      if (!aEntry.getKey ().isEmpty () && aEntry.getValue ().equals (sNamespaceUri))
        return aEntry.getKey ();
    return null;
  }

  public boolean isDefaultNamespace (final String sNamespaceUri)
  {
    return Objects.equals (lookupNamespaceURI (null), nullIfEmpty (sNamespaceUri));
  }

  public String lookupNamespaceURI (final String sPrefix)
  {
    final IXdmNode aElement = getNamespaceContextElement ();
    return aElement == null ? null : aElement.lookupNamespaceUri (_nonNull (sPrefix));
  }

  /**
   * Compares the trees iteratively, also against nodes of other DOM implementations. The children of attributes are
   * not compared (their values are).
   */
  public boolean isEqualNode (final Node aOther)
  {
    if (aOther == null)
      return false;
    final ArrayDeque <Pair> aStack = new ArrayDeque <> ();
    aStack.push (new Pair (this, aOther));
    while (!aStack.isEmpty ())
    {
      final Pair aPair = aStack.pop ();
      final Node aLeft = aPair.left ();
      final Node aRight = aPair.right ();
      if (!_isShallowEqual (aLeft, aRight, aStack))
        return false;
      if (aLeft.getNodeType () == ATTRIBUTE_NODE)
        continue;
      final NodeList aLeftChildren = aLeft.getChildNodes ();
      final NodeList aRightChildren = aRight.getChildNodes ();
      if (aLeftChildren.getLength () != aRightChildren.getLength ())
        return false;
      for (int i = 0; i < aLeftChildren.getLength (); ++i)
        aStack.push (new Pair (aLeftChildren.item (i), aRightChildren.item (i)));
    }
    return true;
  }

  public Object getFeature (final String sFeature, final String sVersion)
  {
    return isSupported (sFeature, sVersion) ? this : null;
  }

  public Object setUserData (final String sKey, final Object aData, final UserDataHandler aHandler)
  {
    throw createNotSupported ("User data");
  }

  public Object getUserData (final String sKey)
  {
    return null;
  }

  @Override
  public String toString ()
  {
    return getClass ().getSimpleName () + "[" + getNodeName () + "]";
  }
}
