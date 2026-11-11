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

import java.util.Arrays;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * An element node.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public sealed class XdmElementNode extends AbstractXdmParentNode
{
  // Guards the lazy creation of the namespace nodes of all elements (one lock
  // per node would cost memory on every node)
  private static final SimpleReadWriteLock NAMESPACE_NODES_LOCK = new SimpleReadWriteLock ();

  private final XdmQName m_aName;
  // Exactly as long as the number of attributes; null if there are none
  private AbstractXdmNode [] m_aAttributes;
  // Only set on nodes of a schema-validated tree; shared between nodes. A
  // field and not in the details holder: a validated tree has it on every
  // node, and it is read whenever a typed value is needed
  private XdmSchemaType m_aSchemaType;

  XdmElementNode (@NonNull final XdmQName aName)
  {
    m_aName = aName;
  }

  @NonNull
  public ENodeKind getKind ()
  {
    return ENodeKind.ELEMENT;
  }

  @Override
  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  /**
   * The ID kind is kept in the details holder: only the elements of an ID type in a
   * schema-validated tree have one.
   */
  @Override
  @NonNull
  public EIDKind getIDKind ()
  {
    return internalGetIDKind ();
  }

  @Override
  public void setIDKind (@NonNull final EIDKind eIDKind)
  {
    ValueEnforcer.notNull (eIDKind, "IDKind");
    internalSetIDKind (eIDKind);
  }

  @Override
  @Nullable
  public XdmSchemaType getSchemaType ()
  {
    return m_aSchemaType;
  }

  @Override
  public void setSchemaType (@Nullable final XdmSchemaType aSchemaType)
  {
    m_aSchemaType = aSchemaType;
  }

  /**
   * Append a parentless attribute node as the last attribute of this element. There is no check for
   * an attribute of the same name. A numbering of the attribute on its own is removed. If the tree
   * was numbered, the numbering of the whole tree and the name indexes of its document are removed
   * and assigned again on demand.
   *
   * @throws IllegalArgumentException
   *         if the node is no attribute node or already has a parent
   */
  @Override
  public void addAttribute (@NonNull final IXdmNode aAttribute)
  {
    ValueEnforcer.notNull (aAttribute, "Attribute");
    if (!(aAttribute instanceof final XdmAttributeNode aRealAttribute))
      throw new IllegalArgumentException ("Not an attribute node");
    if (aRealAttribute.getParent () != null)
      throw new IllegalArgumentException ("The attribute node already has a parent");

    // An attribute numbered on its own must not keep that numbering
    aRealAttribute.internalInvalidateOrder ();
    aRealAttribute.internalSetParent (this);
    if (m_aAttributes == null)
      m_aAttributes = new AbstractXdmNode [] { aRealAttribute };
    else
    {
      // The array stays exactly as long as the attribute count
      final int nCount = m_aAttributes.length;
      m_aAttributes = Arrays.copyOf (m_aAttributes, nCount + 1);
      m_aAttributes[nCount] = aRealAttribute;
    }
    internalInvalidateOrder ();
  }

  /**
   * Set all attributes of an element that has none yet and is not numbered, while its tree is built
   * ({@link XdmTreeBuilder}, {@link XdmStreamReader}): the array is taken over as it is and each
   * attribute gets this element as parent. The attributes must be parentless attribute nodes; this
   * is not checked.
   *
   * @param aAttributes
   *        The attributes, at least one, in document order. The array must not be changed
   *        afterwards.
   */
  void internalSetAttributes (@NonNull final AbstractXdmNode [] aAttributes)
  {
    for (final AbstractXdmNode aAttr : aAttributes)
      aAttr.internalSetParent (this);
    m_aAttributes = aAttributes;
  }

  /**
   * Record the declaration and drop the namespace nodes built so far for this element, so that
   * {@link #getNamespaceNodes()} builds them again. The namespace nodes of descendant elements are
   * not dropped.
   */
  @Override
  public void declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    internalDeclareNamespace (sPrefix, sNamespaceUri);
    internalSetNamespaceNodes (null);
  }

  /**
   * Like {@link #declareNamespace(String, String)} for each entry. An element without declarations
   * of its own takes the map itself instead of a copy.
   */
  @Override
  public void declareSharedNamespaces (@NonNull final ICommonsOrderedMap <String, String> aNamespaces)
  {
    ValueEnforcer.notNull (aNamespaces, "Namespaces");
    if (aNamespaces.isEmpty ())
      return;
    if (hasDeclaredNamespaces ())
    {
      for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
        internalDeclareNamespace (aEntry.getKey (), aEntry.getValue ());
    }
    else
    {
      // Optimisation (memory): many elements built by one instruction share one map
      internalShareNamespaces (aNamespaces);
    }
    internalSetNamespaceNodes (null);
  }

  @Override
  @Nonnegative
  public int getAttributeCount ()
  {
    return m_aAttributes == null ? 0 : m_aAttributes.length;
  }

  @Override
  @NonNull
  public AbstractXdmNode getAttributeAtIndex (@Nonnegative final int nIndex)
  {
    if (m_aAttributes == null)
      throw new IndexOutOfBoundsException ("No attributes");
    return m_aAttributes[nIndex];
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getAllAttributes ()
  {
    return m_aAttributes == null ? new CommonsArrayList <> () : new CommonsArrayList <> (m_aAttributes);
  }

  /**
   * Linear search through the attributes in order; the first attribute with the expanded name wins.
   */
  @Override
  @Nullable
  public IXdmNode getAttribute (@Nullable final String sNamespaceUri, @NonNull final String sLocalName)
  {
    if (m_aAttributes != null)
      for (final AbstractXdmNode aAttr : m_aAttributes)
        if (aAttr.getName ().isSameName (sNamespaceUri, sLocalName))
          return aAttr;
    return null;
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  /**
   * Built on the first call from {@link #getInScopeNamespaces()}, one {@link XdmNamespaceNode} per
   * prefix with this element as parent and its position as namespace index, and then kept until the
   * next {@link #declareNamespace(String, String)} on this element. Creation is guarded by a lock
   * shared by all elements, so concurrent readers of a finished tree get the identical nodes.
   */
  public ICommonsList <IXdmNode> getNamespaceNodes ()
  {
    final ICommonsList <IXdmNode> aExisting = NAMESPACE_NODES_LOCK.readLockedGet (this::internalGetNamespaceNodes);
    if (aExisting != null)
      return aExisting.getClone ();
    return NAMESPACE_NODES_LOCK.writeLockedGet (() -> {
      ICommonsList <IXdmNode> aNodes = internalGetNamespaceNodes ();
      if (aNodes == null)
      {
        final ICommonsList <IXdmNode> aList = new CommonsArrayList <> ();
        for (final var aEntry : getInScopeNamespaces ().entrySet ())
        {
          final XdmNamespaceNode aNS = new XdmNamespaceNode (aEntry.getKey (), aEntry.getValue (), aList.size ());
          aNS.internalSetParent (this);
          aList.add (aNS);
        }
        internalSetNamespaceNodes (aList);
        aNodes = aList;
      }
      return aNodes.getClone ();
    });
  }

  /**
   * An element with the location of its source, created by the tree builders when they record
   * locations ({@link XdmParser#setRecordLocations(boolean)}) and by the factories of
   * {@link IXdmNode} that take a location. The location is kept in two fields of its own instead of
   * the details holder.
   */
  static final class Located extends XdmElementNode
  {
    private int m_nLine;
    private int m_nColumn;

    Located (@NonNull final XdmQName aName, @Nonnegative final int nLine, @Nonnegative final int nColumn)
    {
      super (aName);
      m_nLine = nLine;
      m_nColumn = nColumn;
    }

    @Override
    @Nonnegative
    public int getLine ()
    {
      return m_nLine;
    }

    @Override
    @Nonnegative
    public int getColumn ()
    {
      return m_nColumn;
    }

    @Override
    public void setLocation (@Nonnegative final int nLine, @Nonnegative final int nColumn)
    {
      m_nLine = nLine;
      m_nColumn = nColumn;
    }
  }
}
