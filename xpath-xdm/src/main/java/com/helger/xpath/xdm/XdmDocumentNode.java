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
  /** The elements of a numbered tree by name, each list in document order. */
  private static final class NameIndex
  {
    // The numbering the index was built for
    private final long m_nTree;
    private final Map <XdmQName, List <IXdmNode>> m_aElements;

    private NameIndex (final long nTree, @NonNull final Map <XdmQName, List <IXdmNode>> aElements)
    {
      m_nTree = nTree;
      m_aElements = aElements;
    }
  }

  // Unparsed entities: name to {system ID, public ID}
  private ICommonsOrderedMap <String, String []> m_aUnparsedEntities;
  // Names of the parsed general entities declared in the DTD
  private ICommonsList <String> m_aParsedEntityNames;
  // Built on first use. Threads that race build equal indexes, so no lock
  private volatile NameIndex m_aNameIndex;

  XdmDocumentNode ()
  {}

  @NonNull
  private NameIndex _buildNameIndex (final long nTree)
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
        aCollected.computeIfAbsent (aElement.getName (), k -> new CommonsArrayList <> ()).add (aElement);
        for (int i = aElement.getChildCount () - 1; i >= 0; --i)
          aStack.push (aElement.getChildAtIndex (i));
      }
    }
    final Map <XdmQName, List <IXdmNode>> aElements = new HashMap <> (aCollected.size () * 2);
    for (final Map.Entry <XdmQName, ICommonsList <IXdmNode>> aEntry : aCollected.entrySet ())
      aElements.put (aEntry.getKey (), List.copyOf (aEntry.getValue ()));
    return new NameIndex (nTree, aElements);
  }

  void internalDropNameIndex ()
  {
    m_aNameIndex = null;
  }

  /**
   * The elements of this document with a name, from an index built on first
   * use. The index belongs to the current numbering of the tree; a structural
   * change removes the numbering and with it the index.
   *
   * @param aName
   *        The element name (namespace URI and local name).
   * @return The elements in document order as a read-only list, or
   *         <code>null</code> if the tree is not numbered.
   */
  @Nullable
  public List <IXdmNode> getElementsByName (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    final long nTree = internalGetTree ();
    if (nTree == 0)
      return null;
    NameIndex aIndex = m_aNameIndex;
    if (aIndex == null || aIndex.m_nTree != nTree)
    {
      aIndex = _buildNameIndex (nTree);
      m_aNameIndex = aIndex;
    }
    final List <IXdmNode> ret = aIndex.m_aElements.get (aName);
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
