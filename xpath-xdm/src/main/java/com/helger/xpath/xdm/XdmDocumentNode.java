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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * A document node.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmDocumentNode extends AbstractXdmParentNode
{
  /** A parent node (by identity) and the name of its child elements. */
  private record ChildKey (@NonNull IXdmNode parent, @NonNull XdmQName name)
  {}

  /**
   * The elements (or attributes) of a numbered tree by name, each list in
   * document order.
   */
  private static final class NameIndex
  {
    // The numbering the index was built for
    private final long m_nTree;
    private final Map <XdmQName, List <IXdmNode>> m_aNodes;
    // The child elements by name of the parents asked for, filled on demand
    private final Map <ChildKey, List <IXdmNode>> m_aChildren = new ConcurrentHashMap <> ();

    private NameIndex (final long nTree, @NonNull final Map <XdmQName, List <IXdmNode>> aNodes)
    {
      m_nTree = nTree;
      m_aNodes = aNodes;
    }
  }

  // Unparsed entities: name to {system ID, public ID}
  private ICommonsOrderedMap <String, String []> m_aUnparsedEntities;
  // Names of the parsed general entities declared in the DTD
  private ICommonsList <String> m_aParsedEntityNames;
  // Built on first use. Threads that race build equal indexes, so no lock
  private volatile NameIndex m_aNameIndex;
  // The attributes by name, built on first use like the element index
  private volatile NameIndex m_aAttributeIndex;
  // Built by XdmStreamReader: only a part of the document is attached
  private boolean m_bStreamed;

  XdmDocumentNode ()
  {}

  void internalSetStreamed (final boolean bStreamed)
  {
    m_bStreamed = bStreamed;
    m_aNameIndex = null;
    m_aAttributeIndex = null;
  }

  /**
   * @return <code>true</code> if this document is read by an
   *         {@link XdmStreamReader}: only the part of it that the reader keeps
   *         is attached, so a question about the whole document cannot be
   *         answered from the tree.
   */
  public boolean isStreamed ()
  {
    return m_bStreamed;
  }

  @NonNull
  private NameIndex _buildNameIndex (final long nTree, final boolean bAttributes)
  {
    final Map <XdmQName, ICommonsList <IXdmNode>> aCollected = new HashMap <> ();
    final ArrayDeque <AbstractXdmNode> aStack = new ArrayDeque <> ();
    for (int i = getChildCount () - 1; i >= 0; --i)
      aStack.push (getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final AbstractXdmNode aCur = aStack.pop ();
      if (aCur instanceof final XdmElementNode aElement)
      {
        if (bAttributes)
        {
          // The attributes of an element follow it in document order
          final int nAttrs = aElement.getAttributeCount ();
          for (int i = 0; i < nAttrs; ++i)
          {
            final AbstractXdmNode aAttr = aElement.getAttributeAtIndex (i);
            aCollected.computeIfAbsent (aAttr.getName (), k -> new CommonsArrayList <> ()).add (aAttr);
          }
        }
        else
          aCollected.computeIfAbsent (aElement.getName (), k -> new CommonsArrayList <> ()).add (aElement);
        for (int i = aElement.getChildCount () - 1; i >= 0; --i)
          aStack.push (aElement.getChildAtIndex (i));
      }
    }
    final Map <XdmQName, List <IXdmNode>> aNodes = new HashMap <> (aCollected.size () * 2);
    for (final Map.Entry <XdmQName, ICommonsList <IXdmNode>> aEntry : aCollected.entrySet ())
      aNodes.put (aEntry.getKey (), List.copyOf (aEntry.getValue ()));
    return new NameIndex (nTree, aNodes);
  }

  /**
   * @return The element (or attribute) index of the current numbering, built
   *         if needed; <code>null</code> if the tree is not numbered or is
   *         streamed.
   */
  @Nullable
  private NameIndex _getIndex (final boolean bAttributes)
  {
    final long nTree = internalGetTree ();
    if (nTree == 0 || m_bStreamed)
      return null;
    NameIndex aIndex = bAttributes ? m_aAttributeIndex : m_aNameIndex;
    if (aIndex == null || aIndex.m_nTree != nTree)
    {
      aIndex = _buildNameIndex (nTree, bAttributes);
      if (bAttributes)
        m_aAttributeIndex = aIndex;
      else
        m_aNameIndex = aIndex;
    }
    return aIndex;
  }

  /**
   * Binary search in a list of nodes of one numbered tree.
   *
   * @param aSorted
   *        The nodes in document order.
   * @param nFrom
   *        The index to start from.
   * @param nOrder
   *        The document order number to look for.
   * @return The first index from nFrom on whose node has an order of at least
   *         nOrder; the size of the list if there is none.
   */
  public static int getLowerBound (@NonNull final List <IXdmNode> aSorted, final int nFrom, final int nOrder)
  {
    int nLow = nFrom;
    int nHigh = aSorted.size ();
    while (nLow < nHigh)
    {
      final int nMid = (nLow + nHigh) >>> 1;
      if (aSorted.get (nMid).getOrder () < nOrder)
        nLow = nMid + 1;
      else
        nHigh = nMid;
    }
    return nLow;
  }

  /**
   * The child elements of aParent with a name: the indexed elements of its
   * subtree whose parent it is, or its children with that name, whichever
   * list is shorter to go through.
   */
  @NonNull
  private static List <IXdmNode> _collectChildElements (@NonNull final List <IXdmNode> aElements,
                                                        @NonNull final IXdmNode aParent,
                                                        @NonNull final XdmQName aName)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final int nFrom = getLowerBound (aElements, 0, aParent.getOrder () + 1);
    final int nTo = getLowerBound (aElements, nFrom, aParent.getEndOrder () + 1);
    final int nCount = aParent.getChildCount ();
    if (nTo - nFrom < nCount)
    {
      for (int i = nFrom; i < nTo; ++i)
      {
        final IXdmNode aElement = aElements.get (i);
        if (aElement.getParent () == aParent)
          ret.add (aElement);
      }
    }
    else
      for (int i = 0; i < nCount; ++i)
      {
        final IXdmNode aChild = aParent.getChildAtIndex (i);
        if (aChild.getKind () == ENodeKind.ELEMENT && aName.equals (aChild.getName ()))
          ret.add (aChild);
      }
    return List.copyOf (ret);
  }

  void internalDropNameIndex ()
  {
    m_aNameIndex = null;
    m_aAttributeIndex = null;
  }

  /**
   * The elements of this document with a name, from an index built on first
   * use. The index belongs to the current numbering of the tree; a structural
   * change removes the numbering and with it the index.
   *
   * @param aName
   *        The element name (namespace URI and local name).
   * @return The elements in document order as a read-only list, or
   *         <code>null</code> if the tree is not numbered or is
   *         {@link #isStreamed() streamed}.
   */
  @Nullable
  public List <IXdmNode> getElementsByName (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    final NameIndex aIndex = _getIndex (false);
    if (aIndex == null)
      return null;
    final List <IXdmNode> ret = aIndex.m_aNodes.get (aName);
    return ret == null ? List.of () : ret;
  }

  /**
   * The child elements with a name of a node of this document, kept with the
   * element index once asked for, so that a repeated child step from a node
   * with many children does not go through them again. Meant for such nodes:
   * every pair of parent and name asked for stays in memory as long as the
   * index.
   *
   * @param aParent
   *        The parent node. Must belong to this document.
   * @param aName
   *        The element name (namespace URI and local name).
   * @return The child elements in document order as a read-only list, or
   *         <code>null</code> if the tree is not numbered or is
   *         {@link #isStreamed() streamed}.
   */
  @Nullable
  public List <IXdmNode> getChildElementsByName (@NonNull final IXdmNode aParent, @NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aParent, "Parent");
    ValueEnforcer.notNull (aName, "Name");
    final NameIndex aIndex = _getIndex (false);
    if (aIndex == null)
      return null;
    final List <IXdmNode> aElements = aIndex.m_aNodes.get (aName);
    if (aElements == null)
      return List.of ();
    return aIndex.m_aChildren.computeIfAbsent (new ChildKey (aParent, aName),
                                               k -> _collectChildElements (aElements, aParent, aName));
  }

  /**
   * The attributes of this document with a name, from an index built on first
   * use. Like {@link #getElementsByName(XdmQName)} it belongs to the current
   * numbering of the tree.
   *
   * @param aName
   *        The attribute name (namespace URI and local name).
   * @return The attributes in document order as a read-only list, or
   *         <code>null</code> if the tree is not numbered or is
   *         {@link #isStreamed() streamed}.
   */
  @Nullable
  public List <IXdmNode> getAttributesByName (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    final NameIndex aIndex = _getIndex (true);
    if (aIndex == null)
      return null;
    final List <IXdmNode> ret = aIndex.m_aNodes.get (aName);
    return ret == null ? List.of () : ret;
  }

  @NonNull
  public ENodeKind getKind ()
  {
    return ENodeKind.DOCUMENT;
  }

  @Override
  public void addUnparsedEntity (@NonNull final String sName,
                                 @NonNull final String sSystemId,
                                 @Nullable final String sPublicId)
  {
    ValueEnforcer.notNull (sName, "Name");
    ValueEnforcer.notNull (sSystemId, "SystemId");
    if (m_aUnparsedEntities == null)
      m_aUnparsedEntities = new CommonsLinkedHashMap <> ();
    // The first declaration of an entity is binding
    m_aUnparsedEntities.putIfAbsent (sName, new String [] { sSystemId, sPublicId });
  }

  @Override
  @Nullable
  public String [] getUnparsedEntity (@NonNull final String sName)
  {
    if (m_aUnparsedEntities == null)
      return null;
    final String [] ret = m_aUnparsedEntities.get (sName);
    return ret == null ? null : ret.clone ();
  }

  @Override
  public void addParsedEntityName (@NonNull final String sName)
  {
    ValueEnforcer.notNull (sName, "Name");
    if (m_aParsedEntityNames == null)
      m_aParsedEntityNames = new CommonsArrayList <> ();
    m_aParsedEntityNames.add (sName);
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllParsedEntityNames ()
  {
    return m_aParsedEntityNames == null ? new CommonsArrayList <> () : m_aParsedEntityNames.getClone ();
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllUnparsedEntityNames ()
  {
    return m_aUnparsedEntities == null ? new CommonsArrayList <> () : new CommonsArrayList <> (m_aUnparsedEntities.keySet ());
  }
}
