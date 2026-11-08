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
import com.helger.base.enforce.ValueEnforcer;

/**
 * An attribute node.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmAttributeNode extends AbstractXdmValueNode
{
  private final XdmQName m_aName;
  private EIDKind m_eIDKind = EIDKind.NONE;
  // Only set on nodes of a schema-validated tree; shared between nodes
  private XdmSchemaType m_aSchemaType;

  XdmAttributeNode (@NonNull final XdmQName aName, @NonNull final String sValue)
  {
    super (sValue);
    m_aName = aName;
  }

  @NonNull
  public ENodeKind getKind ()
  {
    return ENodeKind.ATTRIBUTE;
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
}
