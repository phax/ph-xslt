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
  // Unparsed entities: name to {system ID, public ID}
  private ICommonsOrderedMap <String, String []> m_aUnparsedEntities;
  // Names of the parsed general entities declared in the DTD
  private ICommonsList <String> m_aParsedEntityNames;

  XdmDocumentNode ()
  {}

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
