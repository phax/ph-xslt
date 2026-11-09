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
 * Base class of all node kinds. It holds what every node has (parent, location, document order) and
 * the default for every property a kind does not have. The subclasses only add the fields of their
 * kind.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract sealed class AbstractXdmNode implements IXdmNode permits AbstractXdmParentNode, AbstractXdmValueNode
{
  private static final AtomicLong TREE_COUNTER = new AtomicLong (0);

  /**
   * One numbering of a tree. All nodes numbered together reference the same instance, so "the same
   * tree" is an identity check, and a node costs one reference instead of a <code>long</code>
   * number. A plain <code>int</code> counter would wrap after 2^32 numberings, which a long-running
   * process reaches, and could then give a live tree's number to a new tree; an identity cannot be
   * reused while a node still references it. The sequence number orders different trees by the
   * moment they were numbered and never wraps in practice (2^63).
   */
  static final class TreeStamp
  {
    private final long m_nSequence;

    private TreeStamp (final long nSequence)
    {
      m_nSequence = nSequence;
    }
  }

  /**
   * The properties only few nodes have. They live in a separate object that is created on the first
   * non-default value, which keeps every node small. All of them but the namespace nodes of an
   * element are set while a tree is built. The namespace nodes are built on demand under the lock
   * of {@link XdmElementNode}; a reader without the lock that sees a holder created for them sees
   * default values in all other fields, which is what a node without holder has.
   */
  private static final class Details
  {
    // Namespace declarations of an element
    private ICommonsOrderedMap <String, String> m_aNamespaces;
    // Built-in type annotation of a node without schema type
    private XdmQName m_aTypeAnnotation;
    private boolean m_bNilled;
    private String m_sDocumentUri;
    private String m_sBaseUri;
    // XSLT disable-output-escaping on a text node
    private boolean m_bDisableOutputEscaping;
    // Location of a node that is not located itself (see setLocation)
    private int m_nLine;
    private int m_nColumn;
    // ID kind of an element or attribute; null for EIDKind.NONE
    private EIDKind m_eIDKind;
    // Lazily built namespace axis of an element, see XdmElementNode
    private ICommonsList <IXdmNode> m_aNamespaceNodes;
  }

  private AbstractXdmNode m_aParent;
  private int m_nOrder;
  // null = not numbered yet
  private TreeStamp m_aTree;
  // Created on demand
  private Details m_aDetails;

  protected AbstractXdmNode ()
  {}

  /**
   * @return The {@link Details} of this node, created on the first call. Only called to store a
   *         value; reading goes through the field directly, so that a node that only ever has
   *         default values stays without details.
   */
  @NonNull
  private Details _details ()
  {
    if (m_aDetails == null)
      m_aDetails = new Details ();
    return m_aDetails;
  }

  /**
   * @return The namespace declarations of this node itself (prefix to URI, in declaration order),
   *         or <code>null</code> if it has none. The internal map, not a copy.
   */
  @Nullable
  private ICommonsOrderedMap <String, String> _getNamespaces ()
  {
    return m_aDetails == null ? null : m_aDetails.m_aNamespaces;
  }

  /**
   * @return The numbering this node was numbered in, <code>null</code> if it is not numbered.
   */
  @Nullable
  final TreeStamp internalGetTree ()
  {
    return m_aTree;
  }

  /** @return A new numbering, for a tree that is numbered while it is built. */
  @NonNull
  static TreeStamp internalNewTree ()
  {
    return new TreeStamp (TREE_COUNTER.incrementAndGet ());
  }

  /**
   * Number a node as it is created, in the order {@link #assignDocumentOrder()} would give it in
   * the finished tree (used by {@link XdmStreamReader}).
   */
  final void internalStamp (@NonNull final TreeStamp aTree, @Nonnegative final int nOrder)
  {
    m_aTree = aTree;
    m_nOrder = nOrder;
  }

  /**
   * Remove the numbering of the whole tree after a structural change, so that it is assigned again
   * on demand. A no-op for an unnumbered tree, so the cost of a series of changes is one pass over
   * the tree.
   */
  final void internalInvalidateOrder ()
  {
    if (m_aTree == null)
      return;
    final AbstractXdmNode aRoot = getRoot ();
    final ArrayDeque <AbstractXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final AbstractXdmNode aCur = aStack.pop ();
      aCur.m_aTree = null;
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
        aCur.getAttributeAtIndex (i).m_aTree = null;
      for (int i = 0; i < aCur.getChildCount (); ++i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    if (aRoot instanceof final XdmDocumentNode aDoc)
      aDoc.internalDropNameIndex ();
  }

  /**
   * @return The ID kind stored for this node (elements and attributes), {@link EIDKind#NONE} if
   *         none was stored.
   */
  @NonNull
  final EIDKind internalGetIDKind ()
  {
    final EIDKind ret = m_aDetails == null ? null : m_aDetails.m_eIDKind;
    return ret == null ? EIDKind.NONE : ret;
  }

  /**
   * Store the ID kind of an element or attribute. {@link EIDKind#NONE}, the value of almost every
   * node, creates no details holder.
   */
  final void internalSetIDKind (@NonNull final EIDKind eIDKind)
  {
    if (eIDKind != EIDKind.NONE || m_aDetails != null)
      _details ().m_eIDKind = eIDKind == EIDKind.NONE ? null : eIDKind;
  }

  /**
   * @return The namespace nodes built for this element, <code>null</code> if there are none (yet).
   *         The caller guards the access.
   */
  @Nullable
  final ICommonsList <IXdmNode> internalGetNamespaceNodes ()
  {
    return m_aDetails == null ? null : m_aDetails.m_aNamespaceNodes;
  }

  /**
   * Keep the namespace nodes built for this element, or drop them with <code>null</code> (which
   * creates no details holder). The caller guards the access.
   */
  final void internalSetNamespaceNodes (@Nullable final ICommonsList <IXdmNode> aNamespaceNodes)
  {
    if (aNamespaceNodes != null || m_aDetails != null)
      _details ().m_aNamespaceNodes = aNamespaceNodes;
  }

  final void internalSetParent (@Nullable final AbstractXdmNode aParent)
  {
    m_aParent = aParent;
  }

  /**
   * Record a namespace declaration on this node, without any check. A second declaration of the
   * same prefix replaces the URI but keeps the position of the first one. Cached namespace nodes
   * are not dropped; that is up to the caller
   * ({@link XdmElementNode#declareNamespace(String, String)}).
   *
   * @param sPrefix
   *        The prefix, the empty string for the default namespace.
   * @param sNamespaceUri
   *        The namespace URI; the empty string undeclares the prefix.
   */
  final void internalDeclareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    final Details aDetails = _details ();
    if (aDetails.m_aNamespaces == null)
      aDetails.m_aNamespaces = new CommonsLinkedHashMap <> ();
    aDetails.m_aNamespaces.put (sPrefix, sNamespaceUri);
  }

  @Nullable
  public XdmQName getName ()
  {
    return null;
  }

  @Nullable
  public String getValue ()
  {
    return null;
  }

  /**
   * Default for the kinds without a raw value (documents and elements): always fails.
   *
   * @throws IllegalStateException
   *         always, after the <code>null</code> check
   */
  public void setValue (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    throw new IllegalStateException ("A " + getKind () + " node has no raw value");
  }

  @Nullable
  public final AbstractXdmNode getParent ()
  {
    return m_aParent;
  }

  /**
   * The location of a node without location fields of its own is kept in the details holder: 0 if
   * none was set.
   */
  @Nonnegative
  public int getLine ()
  {
    return m_aDetails == null ? 0 : m_aDetails.m_nLine;
  }

  /**
   * The location of a node without location fields of its own is kept in the details holder: 0 if
   * none was set.
   */
  @Nonnegative
  public int getColumn ()
  {
    return m_aDetails == null ? 0 : m_aDetails.m_nColumn;
  }

  /**
   * A node created without a location (all nodes of a tree parsed without recording locations, and
   * nodes of the factories without location) keeps a location set later in its details holder,
   * which is only created for a location other than 0:0. The located nodes of the tree builders
   * override this with fields of their own.
   */
  public void setLocation (@Nonnegative final int nLine, @Nonnegative final int nColumn)
  {
    if (nLine != 0 || nColumn != 0 || m_aDetails != null)
    {
      final Details aDetails = _details ();
      aDetails.m_nLine = nLine;
      aDetails.m_nColumn = nColumn;
    }
  }

  @NonNull
  public EIDKind getIDKind ()
  {
    return EIDKind.NONE;
  }

  /**
   * Default for the kinds without an ID kind: {@link EIDKind#NONE} is accepted and ignored, any
   * other value fails.
   *
   * @throws IllegalStateException
   *         for a value other than {@link EIDKind#NONE}
   */
  public void setIDKind (@NonNull final EIDKind eIDKind)
  {
    ValueEnforcer.notNull (eIDKind, "IDKind");
    if (eIDKind != EIDKind.NONE)
      throw new IllegalStateException ("A " + getKind () + " node has no ID kind");
  }

  /**
   * @return The name of the {@link #getSchemaType() schema type} if there is one, otherwise the
   *         built-in annotation stored with {@link #setTypeAnnotation(XdmQName)}, <code>null</code>
   *         if none was stored.
   */
  @Nullable
  public final XdmQName getTypeAnnotation ()
  {
    final XdmSchemaType aSchemaType = getSchemaType ();
    if (aSchemaType != null)
      return aSchemaType.getName ();
    return m_aDetails == null ? null : m_aDetails.m_aTypeAnnotation;
  }

  public final void setTypeAnnotation (@Nullable final XdmQName aTypeAnnotation)
  {
    if (aTypeAnnotation != null || m_aDetails != null)
      _details ().m_aTypeAnnotation = aTypeAnnotation;
  }

  @Nullable
  public XdmSchemaType getSchemaType ()
  {
    return null;
  }

  /**
   * Default for the kinds without a schema type: <code>null</code> is accepted and ignored, any
   * other value fails.
   *
   * @throws IllegalStateException
   *         for a value other than <code>null</code>
   */
  public void setSchemaType (@Nullable final XdmSchemaType aSchemaType)
  {
    if (aSchemaType != null)
      throw new IllegalStateException ("A " + getKind () + " node has no schema type");
  }

  public final boolean isNilled ()
  {
    return m_aDetails != null && m_aDetails.m_bNilled;
  }

  public final void setNilled (final boolean bNilled)
  {
    if (bNilled || m_aDetails != null)
      _details ().m_bNilled = bNilled;
  }

  @Nullable
  public final String getDocumentUri ()
  {
    return m_aDetails == null ? null : m_aDetails.m_sDocumentUri;
  }

  public final void setDocumentUri (@Nullable final String sDocumentUri)
  {
    if (sDocumentUri != null || m_aDetails != null)
      _details ().m_sDocumentUri = sDocumentUri;
  }

  @Nullable
  public final String getBaseUri ()
  {
    return m_aDetails == null ? null : m_aDetails.m_sBaseUri;
  }

  public final void setBaseUri (@Nullable final String sBaseUri)
  {
    if (sBaseUri != null || m_aDetails != null)
      _details ().m_sBaseUri = sBaseUri;
  }

  public final boolean isDisableOutputEscaping ()
  {
    return m_aDetails != null && m_aDetails.m_bDisableOutputEscaping;
  }

  public final void setDisableOutputEscaping (final boolean bDisableOutputEscaping)
  {
    if (bDisableOutputEscaping || m_aDetails != null)
      _details ().m_bDisableOutputEscaping = bDisableOutputEscaping;
  }

  /**
   * Default for all kinds but the document: always fails.
   *
   * @throws IllegalStateException
   *         always, after the <code>null</code> checks
   */
  public void addUnparsedEntity (@NonNull final String sName,
                                 @NonNull final String sSystemId,
                                 @Nullable final String sPublicId)
  {
    ValueEnforcer.notNull (sName, "Name");
    ValueEnforcer.notNull (sSystemId, "SystemId");
    throw new IllegalStateException ("Only a document node has unparsed entities");
  }

  @Nullable
  public String [] getUnparsedEntity (@NonNull final String sName)
  {
    return null;
  }

  /**
   * Default for all kinds but the document: always fails.
   *
   * @throws IllegalStateException
   *         always, after the <code>null</code> check
   */
  public void addParsedEntityName (@NonNull final String sName)
  {
    ValueEnforcer.notNull (sName, "Name");
    throw new IllegalStateException ("Only a document node has entities");
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllParsedEntityNames ()
  {
    return new CommonsArrayList <> ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllUnparsedEntityNames ()
  {
    return new CommonsArrayList <> ();
  }

  /**
   * Default for the kinds without children: always fails.
   *
   * @throws IllegalStateException
   *         always, after the <code>null</code> check
   */
  public void appendChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    throw new IllegalStateException ("A " + getKind () + " node cannot have children");
  }

  /**
   * Default for the kinds without children: nothing can be a child, so this always fails.
   *
   * @throws IllegalArgumentException
   *         always, after the <code>null</code> check
   */
  public void removeChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    throw new IllegalArgumentException ("Not a child of this node");
  }

  /**
   * Default for all kinds but the element: always fails.
   *
   * @throws IllegalStateException
   *         always, after the <code>null</code> check
   */
  public void addAttribute (@NonNull final IXdmNode aAttribute)
  {
    ValueEnforcer.notNull (aAttribute, "Attribute");
    throw new IllegalStateException ("Only an element can have attributes");
  }

  /**
   * Default for all kinds but the element: always fails.
   *
   * @throws IllegalStateException
   *         always, after the <code>null</code> checks
   */
  public void declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    throw new IllegalStateException ("Only an element can declare namespaces");
  }

  @Nonnegative
  public int getChildCount ()
  {
    return 0;
  }

  @NonNull
  public AbstractXdmNode getChildAtIndex (@Nonnegative final int nIndex)
  {
    throw new IndexOutOfBoundsException ("No children");
  }

  @Nonnegative
  public int getAttributeCount ()
  {
    return 0;
  }

  @NonNull
  public AbstractXdmNode getAttributeAtIndex (@Nonnegative final int nIndex)
  {
    throw new IndexOutOfBoundsException ("No attributes");
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getAllChildren ()
  {
    return new CommonsArrayList <> ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getAllAttributes ()
  {
    return new CommonsArrayList <> ();
  }

  @Nullable
  public IXdmNode getAttribute (@Nullable final String sNamespaceUri, @NonNull final String sLocalName)
  {
    return null;
  }

  @NonNull
  @ReturnsMutableCopy
  public final ICommonsOrderedMap <String, String> getDeclaredNamespaces ()
  {
    final ICommonsOrderedMap <String, String> aNamespaces = _getNamespaces ();
    return aNamespaces == null ? new CommonsLinkedHashMap <> () : aNamespaces.getClone ();
  }

  public final boolean hasDeclaredNamespaces ()
  {
    final ICommonsOrderedMap <String, String> aNamespaces = _getNamespaces ();
    return aNamespaces != null && !aNamespaces.isEmpty ();
  }

  @Nullable
  public final String getDeclaredNamespaceUri (@NonNull final String sPrefix)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    final ICommonsOrderedMap <String, String> aNamespaces = _getNamespaces ();
    return aNamespaces == null ? null : aNamespaces.get (sPrefix);
  }

  /**
   * The <code>xml</code> prefix resolves to {@link XdmConstants#NS_XML} without looking at the
   * tree. Otherwise the declarations of this node and then of each ancestor are searched; the
   * closest declaration of the prefix decides, and an empty URI there (an undeclaration) yields
   * <code>null</code> without searching further up.
   */
  @Nullable
  public final String lookupNamespaceUri (@NonNull final String sPrefix)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    if ("xml".equals (sPrefix))
      return XdmConstants.NS_XML;
    for (AbstractXdmNode aCur = this; aCur != null; aCur = aCur.m_aParent)
    {
      final ICommonsOrderedMap <String, String> aNamespaces = aCur._getNamespaces ();
      if (aNamespaces != null)
      {
        final String sUri = aNamespaces.get (sPrefix);
        if (sUri != null)
          return sUri.isEmpty () ? null : sUri;
      }
    }
    return null;
  }

  /**
   * Starts with the <code>xml</code> prefix and applies the declarations of the root first and of
   * this node last, so that a closer declaration overwrites a farther one and an empty URI removes
   * the prefix again. The result keeps the order in which the prefixes were first added.
   */
  @NonNull
  @ReturnsMutableCopy
  public final ICommonsOrderedMap <String, String> getInScopeNamespaces ()
  {
    // Collect from the root downwards so that closer declarations overwrite
    final ArrayDeque <AbstractXdmNode> aPath = new ArrayDeque <> ();
    for (AbstractXdmNode aCur = this; aCur != null; aCur = aCur.m_aParent)
      aPath.push (aCur);

    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    ret.put ("xml", XdmConstants.NS_XML);
    for (final AbstractXdmNode aNode : aPath)
    {
      final ICommonsOrderedMap <String, String> aNamespaces = aNode._getNamespaces ();
      if (aNamespaces != null)
        for (final var aEntry : aNamespaces.entrySet ())
          if (aEntry.getValue ().isEmpty ())
            ret.remove (aEntry.getKey ());
          else
            ret.put (aEntry.getKey (), aEntry.getValue ());
    }
    return ret;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getNamespaceNodes ()
  {
    return new CommonsArrayList <> ();
  }

  @Nonnegative
  public final int getOrder ()
  {
    return m_nOrder;
  }

  @NonNull
  public final AbstractXdmNode getRoot ()
  {
    AbstractXdmNode ret = this;
    while (ret.m_aParent != null)
      ret = ret.m_aParent;
    return ret;
  }

  @Nullable
  public final AbstractXdmNode getOwnerDocument ()
  {
    final AbstractXdmNode aRoot = getRoot ();
    return aRoot.isDocument () ? aRoot : null;
  }

  /**
   * Numbers the whole tree under a new tree number, iteratively in pre-order from the root: each
   * node gets the next number, then its attributes, then its children in order. When a node is
   * reached, every open parent node that is not its parent is finished and receives the last number
   * given so far as its {@link #getEndOrder() end order}. Namespace nodes are not numbered. The
   * name indexes of a {@link XdmDocumentNode} belong to the previous numbering and are rebuilt on
   * their next use.
   */
  public final void assignDocumentOrder ()
  {
    final AbstractXdmNode aRoot = getRoot ();
    final TreeStamp aTree = internalNewTree ();
    int nOrder = 0;
    final ArrayDeque <AbstractXdmNode> aStack = new ArrayDeque <> ();
    // The parent nodes whose subtree is not finished yet, innermost first
    final ArrayDeque <AbstractXdmParentNode> aOpen = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final AbstractXdmNode aCur = aStack.pop ();
      // Every open node that is no ancestor of this one is finished
      while (!aOpen.isEmpty () && aOpen.peek () != aCur.m_aParent)
        aOpen.pop ().internalSetEndOrder (nOrder - 1);
      aCur.m_aTree = aTree;
      aCur.m_nOrder = nOrder++;
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
      {
        final AbstractXdmNode aAttr = aCur.getAttributeAtIndex (i);
        aAttr.m_aTree = aTree;
        aAttr.m_nOrder = nOrder++;
      }
      if (aCur instanceof final AbstractXdmParentNode aParent)
      {
        aOpen.push (aParent);
        for (int i = aParent.getChildCount () - 1; i >= 0; --i)
          aStack.push (aParent.getChildAtIndex (i));
      }
    }
    while (!aOpen.isEmpty ())
      aOpen.pop ().internalSetEndOrder (nOrder - 1);
  }

  /**
   * Implementation of {@link IXdmNode#compareDocumentOrder(IXdmNode, IXdmNode)}. A namespace node
   * with a parent is compared through its parent element and sorts directly after it, before the
   * attributes, in the order of its {@link XdmNamespaceNode#getNamespaceIndex() namespace index}. A
   * tree that is not numbered is numbered first ({@link #assignDocumentOrder()}), so the call may
   * number either tree. Nodes of different trees compare by tree number, i.e. by the order in which
   * the trees were numbered.
   *
   * @param aLeft
   *        First node. May not be <code>null</code>.
   * @param aRight
   *        Second node. May not be <code>null</code>.
   * @return negative, 0 or positive like {@link Comparable#compareTo(Object)}.
   */
  static int compareDocumentOrder (@NonNull final AbstractXdmNode aLeft, @NonNull final AbstractXdmNode aRight)
  {
    if (aLeft == aRight)
      return 0;
    // A namespace node with a parent is not numbered itself: it sorts directly
    // after its parent element, before the attributes of that element
    final AbstractXdmNode aLeftAnchor = aLeft instanceof XdmNamespaceNode && aLeft.m_aParent != null ? aLeft.m_aParent
                                                                                                     : aLeft;
    final AbstractXdmNode aRightAnchor = aRight instanceof XdmNamespaceNode && aRight.m_aParent != null
                                                                                                        ? aRight.m_aParent
                                                                                                        : aRight;
    if (aLeftAnchor.m_aTree == null)
      aLeftAnchor.assignDocumentOrder ();
    if (aRightAnchor.m_aTree == null)
      aRightAnchor.assignDocumentOrder ();
    if (aLeftAnchor.m_aTree != aRightAnchor.m_aTree)
      return Long.compare (aLeftAnchor.m_aTree.m_nSequence, aRightAnchor.m_aTree.m_nSequence);
    if (aLeftAnchor.m_nOrder != aRightAnchor.m_nOrder)
      return Integer.compare (aLeftAnchor.m_nOrder, aRightAnchor.m_nOrder);
    final int nLeftSub = aLeftAnchor != aLeft && aLeft instanceof final XdmNamespaceNode aLeftNS ? 1 +
                                                                                                   aLeftNS.getNamespaceIndex ()
                                                                                                 : 0;
    final int nRightSub = aRightAnchor != aRight && aRight instanceof final XdmNamespaceNode aRightNS ? 1 +
                                                                                                        aRightNS.getNamespaceIndex ()
                                                                                                      : 0;
    return Integer.compare (nLeftSub, nRightSub);
  }

  @Override
  public String toString ()
  {
    final XdmQName aName = getName ();
    return getKind () + (aName != null ? " " + aName : "");
  }
}
