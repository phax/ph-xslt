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

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * A node of the XPath data model. Nodes are mutable while a tree is built and
 * must be treated as read-only afterwards. Document order is only defined
 * after {@link #assignDocumentOrder()} was called on the root, which
 * {@link XdmTreeBuilder} does; {@link #compareDocumentOrder(XdmNode, XdmNode)}
 * numbers an unnumbered tree on demand.<br>
 * Namespace nodes are not stored: an element only keeps the namespace
 * declarations it carries itself, see {@link #getInScopeNamespaces()}. The
 * namespace axis is materialised on demand by {@link #getNamespaceNodes()}.
 * <p>
 * All traversals are iterative, so the depth of a tree never threatens the
 * Java stack.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmNode implements IXdmItem
{
  private static final AtomicLong TREE_COUNTER = new AtomicLong (0);

  private final ENodeKind m_eKind;
  private int m_nOrder;
  private int m_nLine;
  private int m_nColumn;
  private final XdmQName m_aName;
  private String m_sValue;
  private XdmNode m_aParent;
  private ICommonsList <XdmNode> m_aChildren;
  private ICommonsList <XdmNode> m_aAttributes;
  private ICommonsOrderedMap <String, String> m_aNamespaces;
  private EIDKind m_eIDKind = EIDKind.NONE;
  private XdmQName m_aTypeAnnotation;
  private String m_sDocumentUri;
  private String m_sBaseUri;
  // 0 = not numbered yet
  private long m_nTree;
  // Position of a namespace node among the namespace nodes of its parent
  private int m_nNamespaceIndex;
  // Lazily built namespace axis of an element
  private ICommonsList <XdmNode> m_aNamespaceNodes;

  private XdmNode (@NonNull final ENodeKind eKind, @Nullable final XdmQName aName, @Nullable final String sValue)
  {
    m_eKind = eKind;
    m_aName = aName;
    m_sValue = sValue;
  }

  @NonNull
  public static XdmNode createDocument ()
  {
    return new XdmNode (ENodeKind.DOCUMENT, null, null);
  }

  @NonNull
  public static XdmNode createElement (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    return new XdmNode (ENodeKind.ELEMENT, aName, null);
  }

  @NonNull
  public static XdmNode createAttribute (@NonNull final XdmQName aName, @NonNull final String sValue)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (sValue, "Value");
    return new XdmNode (ENodeKind.ATTRIBUTE, aName, sValue);
  }

  @NonNull
  public static XdmNode createText (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    return new XdmNode (ENodeKind.TEXT, null, sValue);
  }

  @NonNull
  public static XdmNode createComment (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    return new XdmNode (ENodeKind.COMMENT, null, sValue);
  }

  /**
   * @param sTarget
   *        The PI target; becomes the local name of the node name. Never empty.
   * @param sData
   *        The PI data. Never <code>null</code>.
   * @return A new processing instruction node.
   */
  @NonNull
  public static XdmNode createProcessingInstruction (@NonNull final String sTarget, @NonNull final String sData)
  {
    ValueEnforcer.notNull (sData, "Data");
    return new XdmNode (ENodeKind.PROCESSING_INSTRUCTION, new XdmQName (sTarget), sData);
  }

  /**
   * Create a parentless namespace node. The namespace nodes of an element are
   * obtained with {@link #getNamespaceNodes()}, which links them to the
   * element.
   *
   * @param sPrefix
   *        The prefix; the empty string for the default namespace. It becomes
   *        the local name of the node name (no namespace); the default
   *        namespace node has no name.
   * @param sNamespaceUri
   *        The namespace URI, the string value of the node. Never
   *        <code>null</code>.
   * @return A new namespace node.
   */
  @NonNull
  public static XdmNode createNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    return new XdmNode (ENodeKind.NAMESPACE, sPrefix.isEmpty () ? null : new XdmQName (sPrefix), sNamespaceUri);
  }

  private void _checkParentKind ()
  {
    if (m_eKind != ENodeKind.DOCUMENT && m_eKind != ENodeKind.ELEMENT)
      throw new IllegalStateException ("A " + m_eKind + " node cannot have children");
  }

  @NonNull
  public ENodeKind getKind ()
  {
    return m_eKind;
  }

  public boolean isDocument ()
  {
    return m_eKind == ENodeKind.DOCUMENT;
  }

  public boolean isElement ()
  {
    return m_eKind == ENodeKind.ELEMENT;
  }

  public boolean isAttribute ()
  {
    return m_eKind == ENodeKind.ATTRIBUTE;
  }

  public boolean isText ()
  {
    return m_eKind == ENodeKind.TEXT;
  }

  /**
   * @return The node name. Only elements, attributes and processing
   *         instructions have one (for the latter the local name is the
   *         target); <code>null</code> for all other kinds.
   */
  @Nullable
  public XdmQName getName ()
  {
    return m_aName;
  }

  /**
   * @return The raw value: the content of attributes, text, comments and
   *         processing instructions. <code>null</code> for documents and
   *         elements, use {@link #getStringValue()} for those.
   */
  @Nullable
  public String getValue ()
  {
    return m_sValue;
  }

  public void setValue (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    if (m_sValue == null)
      throw new IllegalStateException ("A " + m_eKind + " node has no raw value");
    m_sValue = sValue;
  }

  @Nullable
  public XdmNode getParent ()
  {
    return m_aParent;
  }

  @Nonnegative
  public int getLine ()
  {
    return m_nLine;
  }

  @Nonnegative
  public int getColumn ()
  {
    return m_nColumn;
  }

  public void setLocation (@Nonnegative final int nLine, @Nonnegative final int nColumn)
  {
    m_nLine = nLine;
    m_nColumn = nColumn;
  }

  /**
   * @return The DTD type of an attribute node; {@link EIDKind#NONE} for all
   *         other nodes.
   */
  @NonNull
  public EIDKind getIDKind ()
  {
    return m_eIDKind;
  }

  public void setIDKind (@NonNull final EIDKind eIDKind)
  {
    ValueEnforcer.notNull (eIDKind, "IDKind");
    m_eIDKind = eIDKind;
  }

  /**
   * @return The name of the type annotation of an element or attribute node,
   *         or <code>null</code> for the default of an unvalidated node
   *         (<code>xs:untyped</code> for elements,
   *         <code>xs:untypedAtomic</code> for attributes). Only built-in
   *         schema types are used, e.g. by the result tree of
   *         fn:analyze-string.
   */
  @Nullable
  public XdmQName getTypeAnnotation ()
  {
    return m_aTypeAnnotation;
  }

  public void setTypeAnnotation (@Nullable final XdmQName aTypeAnnotation)
  {
    m_aTypeAnnotation = aTypeAnnotation;
  }

  /**
   * @return The URI the document was read from. Only set on document nodes.
   */
  @Nullable
  public String getDocumentUri ()
  {
    return m_sDocumentUri;
  }

  public void setDocumentUri (@Nullable final String sDocumentUri)
  {
    m_sDocumentUri = sDocumentUri;
  }

  /**
   * @return The explicit base URI of this node, or <code>null</code>. It is set
   *         where the base URI does not follow from the parent: on nodes read
   *         from an external entity (the entity's URI) and on document nodes
   *         whose base URI differs from their document URI. An
   *         <code>xml:base</code> attribute of the node itself is resolved
   *         against it.
   */
  @Nullable
  public String getBaseUri ()
  {
    return m_sBaseUri;
  }

  public void setBaseUri (@Nullable final String sBaseUri)
  {
    m_sBaseUri = sBaseUri;
  }

  public void appendChild (@NonNull final XdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    _checkParentKind ();
    if (aChild.m_aParent != null)
      throw new IllegalArgumentException ("The child node already has a parent");
    switch (aChild.m_eKind)
    {
      case ATTRIBUTE:
      case NAMESPACE:
      case DOCUMENT:
        throw new IllegalArgumentException ("A " + aChild.m_eKind + " node cannot be a child");
      default:
        break;
    }
    aChild.m_aParent = this;
    if (m_aChildren == null)
      m_aChildren = new CommonsArrayList <> ();
    m_aChildren.add (aChild);
  }

  public void addAttribute (@NonNull final XdmNode aAttribute)
  {
    ValueEnforcer.notNull (aAttribute, "Attribute");
    if (m_eKind != ENodeKind.ELEMENT)
      throw new IllegalStateException ("Only an element can have attributes");
    if (!aAttribute.isAttribute ())
      throw new IllegalArgumentException ("Not an attribute node");
    if (aAttribute.m_aParent != null)
      throw new IllegalArgumentException ("The attribute node already has a parent");
    aAttribute.m_aParent = this;
    if (m_aAttributes == null)
      m_aAttributes = new CommonsArrayList <> ();
    m_aAttributes.add (aAttribute);
  }

  /**
   * Declare a namespace on this element.
   *
   * @param sPrefix
   *        The prefix; the empty string for the default namespace.
   * @param sNamespaceUri
   *        The namespace URI. The empty string undeclares the default
   *        namespace.
   */
  public void declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    if (m_eKind != ENodeKind.ELEMENT)
      throw new IllegalStateException ("Only an element can declare namespaces");
    if (m_aNamespaces == null)
      m_aNamespaces = new CommonsLinkedHashMap <> ();
    m_aNamespaces.put (sPrefix, sNamespaceUri);
    m_aNamespaceNodes = null;
  }

  @Nonnegative
  public int getChildCount ()
  {
    return m_aChildren == null ? 0 : m_aChildren.size ();
  }

  @NonNull
  public XdmNode getChildAtIndex (@Nonnegative final int nIndex)
  {
    if (m_aChildren == null)
      throw new IndexOutOfBoundsException ("No children");
    return m_aChildren.get (nIndex);
  }

  @Nonnegative
  public int getAttributeCount ()
  {
    return m_aAttributes == null ? 0 : m_aAttributes.size ();
  }

  @NonNull
  public XdmNode getAttributeAtIndex (@Nonnegative final int nIndex)
  {
    if (m_aAttributes == null)
      throw new IndexOutOfBoundsException ("No attributes");
    return m_aAttributes.get (nIndex);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmNode> getAllChildren ()
  {
    return m_aChildren == null ? new CommonsArrayList <> () : m_aChildren.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmNode> getAllAttributes ()
  {
    return m_aAttributes == null ? new CommonsArrayList <> () : m_aAttributes.getClone ();
  }

  /**
   * Find an attribute by expanded name.
   *
   * @param sNamespaceUri
   *        The namespace URI. <code>null</code> or empty for no namespace.
   * @param sLocalName
   *        The local name to search.
   * @return <code>null</code> if no such attribute exists.
   */
  @Nullable
  public XdmNode getAttribute (@Nullable final String sNamespaceUri, @NonNull final String sLocalName)
  {
    if (m_aAttributes != null)
      for (final XdmNode aAttr : m_aAttributes)
        if (aAttr.m_aName.isSameName (sNamespaceUri, sLocalName))
          return aAttr;
    return null;
  }

  /**
   * @return The namespace declarations of this element only, prefix to URI,
   *         in declaration order.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getDeclaredNamespaces ()
  {
    return m_aNamespaces == null ? new CommonsLinkedHashMap <> () : m_aNamespaces.getClone ();
  }

  /**
   * Resolve a prefix against the declarations of this node and its ancestors.
   *
   * @param sPrefix
   *        The prefix, empty for the default namespace.
   * @return The namespace URI, or <code>null</code> if the prefix is not
   *         bound. The <code>xml</code> prefix is always bound. A default
   *         namespace that was undeclared yields <code>null</code>.
   */
  @Nullable
  public String lookupNamespaceUri (@NonNull final String sPrefix)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    if ("xml".equals (sPrefix))
      return XdmConstants.NS_XML;
    for (XdmNode aCur = this; aCur != null; aCur = aCur.m_aParent)
      if (aCur.m_aNamespaces != null)
      {
        final String sUri = aCur.m_aNamespaces.get (sPrefix);
        if (sUri != null)
          return sUri.isEmpty () ? null : sUri;
      }
    return null;
  }

  /**
   * @return All namespaces in scope for this node (the <code>xml</code> prefix
   *         included), prefix to URI. Closer declarations win and undeclared
   *         default namespaces are left out.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getInScopeNamespaces ()
  {
    // Collect from the root downwards so that closer declarations overwrite
    final ArrayDeque <XdmNode> aPath = new ArrayDeque <> ();
    for (XdmNode aCur = this; aCur != null; aCur = aCur.m_aParent)
      aPath.push (aCur);
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    ret.put ("xml", XdmConstants.NS_XML);
    for (final XdmNode aNode : aPath)
      if (aNode.m_aNamespaces != null)
        for (final var aEntry : aNode.m_aNamespaces.entrySet ())
          if (aEntry.getValue ().isEmpty ())
            ret.remove (aEntry.getKey ());
          else
            ret.put (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }

  /**
   * The namespace axis of an element: one namespace node per in-scope
   * namespace (see {@link #getInScopeNamespaces()}), each with this element as
   * parent. The nodes are created on first access and then kept, so repeated
   * calls return identical nodes (node identity matters for <code>is</code>
   * and duplicate elimination). In document order they come after the element
   * and before its attributes. Call only once the tree is complete.
   *
   * @return The namespace nodes in a stable order; empty for nodes other than
   *         elements.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmNode> getNamespaceNodes ()
  {
    if (m_eKind != ENodeKind.ELEMENT)
      return new CommonsArrayList <> ();
    synchronized (this)
    {
      if (m_aNamespaceNodes == null)
      {
        final ICommonsList <XdmNode> aList = new CommonsArrayList <> ();
        for (final var aEntry : getInScopeNamespaces ().entrySet ())
        {
          final XdmNode aNS = createNamespace (aEntry.getKey (), aEntry.getValue ());
          aNS.m_aParent = this;
          aNS.m_nNamespaceIndex = aList.size ();
          aList.add (aNS);
        }
        m_aNamespaceNodes = aList;
      }
      return m_aNamespaceNodes.getClone ();
    }
  }

  @NonNull
  public XdmNode getRoot ()
  {
    XdmNode ret = this;
    while (ret.m_aParent != null)
      ret = ret.m_aParent;
    return ret;
  }

  /**
   * @return The document this node belongs to, or <code>null</code> if the tree
   *         is rooted in a parentless node of another kind.
   */
  @Nullable
  public XdmNode getOwnerDocument ()
  {
    final XdmNode aRoot = getRoot ();
    return aRoot.isDocument () ? aRoot : null;
  }

  /**
   * @return The XDM string value: for documents and elements the concatenation
   *         of all descendant text nodes in document order, otherwise the raw
   *         value.
   */
  @NonNull
  public String getStringValue ()
  {
    if (m_eKind != ENodeKind.DOCUMENT && m_eKind != ENodeKind.ELEMENT)
      return m_sValue;
    if (m_aChildren == null)
      return "";
    // Do not recurse - trees may be very deep
    final StringBuilder aSB = new StringBuilder ();
    final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
    for (int i = m_aChildren.size () - 1; i >= 0; --i)
      aStack.push (m_aChildren.get (i));
    while (!aStack.isEmpty ())
    {
      final XdmNode aCur = aStack.pop ();
      if (aCur.m_eKind == ENodeKind.TEXT)
        aSB.append (aCur.m_sValue);
      else
        if (aCur.m_eKind == ENodeKind.ELEMENT && aCur.m_aChildren != null)
          for (int i = aCur.m_aChildren.size () - 1; i >= 0; --i)
            aStack.push (aCur.m_aChildren.get (i));
    }
    return aSB.toString ();
  }

  /**
   * Number all nodes of the tree this node belongs to in document order: an
   * element comes first, then its attributes, then its children. Numbering
   * starts at the root, so calling this on any node is fine. It must be called
   * again after the structure was changed.
   */
  public void assignDocumentOrder ()
  {
    final XdmNode aRoot = getRoot ();
    final long nTree = TREE_COUNTER.incrementAndGet ();
    int nOrder = 0;
    final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final XdmNode aCur = aStack.pop ();
      aCur.m_nTree = nTree;
      aCur.m_nOrder = nOrder++;
      if (aCur.m_aAttributes != null)
        for (final XdmNode aAttr : aCur.m_aAttributes)
        {
          aAttr.m_nTree = nTree;
          aAttr.m_nOrder = nOrder++;
        }
      if (aCur.m_aChildren != null)
        for (int i = aCur.m_aChildren.size () - 1; i >= 0; --i)
          aStack.push (aCur.m_aChildren.get (i));
    }
  }

  /**
   * Compare two nodes in document order. Nodes of different trees are ordered
   * by the creation order of their trees, which is stable but otherwise
   * implementation dependent as XPath allows.
   *
   * @param aLeft
   *        First node. May not be <code>null</code>.
   * @param aRight
   *        Second node. May not be <code>null</code>.
   * @return negative, 0 or positive like {@link Comparable#compareTo(Object)}.
   */
  public static int compareDocumentOrder (@NonNull final XdmNode aLeft, @NonNull final XdmNode aRight)
  {
    if (aLeft == aRight)
      return 0;
    // A namespace node with a parent is not numbered itself: it sorts directly
    // after its parent element, before the attributes of that element
    final XdmNode aLeftAnchor = aLeft.m_eKind == ENodeKind.NAMESPACE && aLeft.m_aParent != null ? aLeft.m_aParent
                                                                                                 : aLeft;
    final XdmNode aRightAnchor = aRight.m_eKind == ENodeKind.NAMESPACE && aRight.m_aParent != null ? aRight.m_aParent
                                                                                                   : aRight;
    if (aLeftAnchor.m_nTree == 0)
      aLeftAnchor.assignDocumentOrder ();
    if (aRightAnchor.m_nTree == 0)
      aRightAnchor.assignDocumentOrder ();
    if (aLeftAnchor.m_nTree != aRightAnchor.m_nTree)
      return Long.compare (aLeftAnchor.m_nTree, aRightAnchor.m_nTree);
    if (aLeftAnchor.m_nOrder != aRightAnchor.m_nOrder)
      return Integer.compare (aLeftAnchor.m_nOrder, aRightAnchor.m_nOrder);
    final int nLeftSub = aLeftAnchor == aLeft ? 0 : 1 + aLeft.m_nNamespaceIndex;
    final int nRightSub = aRightAnchor == aRight ? 0 : 1 + aRight.m_nNamespaceIndex;
    return Integer.compare (nLeftSub, nRightSub);
  }

  @Override
  public String toString ()
  {
    return m_eKind + (m_aName != null ? " " + m_aName : "");
  }
}
