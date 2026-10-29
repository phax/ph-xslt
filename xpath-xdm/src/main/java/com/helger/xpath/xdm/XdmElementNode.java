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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * An element node.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmElementNode extends AbstractXdmParentNode
{
  // Guards the lazy creation of the namespace nodes of all elements (one lock
  // per node would cost memory on every node)
  private static final SimpleReadWriteLock NAMESPACE_NODES_LOCK = new SimpleReadWriteLock ();

  private final XdmQName m_aName;
  private ICommonsList <AbstractXdmNode> m_aAttributes;
  private EIDKind m_eIDKind = EIDKind.NONE;
  // Only set on nodes of a schema-validated tree; shared between nodes
  private XdmSchemaType m_aSchemaType;
  // Lazily built namespace axis. Written after the tree was built, guarded by
  // NAMESPACE_NODES_LOCK
  private ICommonsList <IXdmNode> m_aNamespaceNodes;

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

  @Override
  @NonNull
  public EIDKind getIDKind ()
  {
    return m_eIDKind;
  }

  @Override
  public void setIDKind (@NonNull final EIDKind eIDKind)
  {
    ValueEnforcer.notNull (eIDKind, "IDKind");
    m_eIDKind = eIDKind;
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

  @Override
  public void addAttribute (@NonNull final IXdmNode aAttribute)
  {
    ValueEnforcer.notNull (aAttribute, "Attribute");
    if (!(aAttribute instanceof final XdmAttributeNode aRealAttribute))
      throw new IllegalArgumentException ("Not an attribute node");
    if (aRealAttribute.getParent () != null)
      throw new IllegalArgumentException ("The attribute node already has a parent");
    aRealAttribute.internalSetParent (this);
    if (m_aAttributes == null)
      m_aAttributes = new CommonsArrayList <> ();
    m_aAttributes.add (aRealAttribute);
  }

  @Override
  public void declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    internalDeclareNamespace (sPrefix, sNamespaceUri);
    m_aNamespaceNodes = null;
  }

  @Override
  @Nonnegative
  public int getAttributeCount ()
  {
    return m_aAttributes == null ? 0 : m_aAttributes.size ();
  }

  @Override
  @NonNull
  public AbstractXdmNode getAttributeAtIndex (@Nonnegative final int nIndex)
  {
    if (m_aAttributes == null)
      throw new IndexOutOfBoundsException ("No attributes");
    return m_aAttributes.get (nIndex);
  }

  @Override
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getAllAttributes ()
  {
    return m_aAttributes == null ? new CommonsArrayList <> () : new CommonsArrayList <> (m_aAttributes);
  }

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
  public ICommonsList <IXdmNode> getNamespaceNodes ()
  {
    final ICommonsList <IXdmNode> aExisting = NAMESPACE_NODES_LOCK.readLockedGet ( () -> m_aNamespaceNodes);
    if (aExisting != null)
      return aExisting.getClone ();
    return NAMESPACE_NODES_LOCK.writeLockedGet ( () -> {
      if (m_aNamespaceNodes == null)
      {
        final ICommonsList <IXdmNode> aList = new CommonsArrayList <> ();
        for (final var aEntry : getInScopeNamespaces ().entrySet ())
        {
          final XdmNamespaceNode aNS = new XdmNamespaceNode (aEntry.getKey (), aEntry.getValue (), aList.size ());
          aNS.internalSetParent (this);
          aList.add (aNS);
        }
        m_aNamespaceNodes = aList;
      }
      return m_aNamespaceNodes.getClone ();
    });
  }
}
