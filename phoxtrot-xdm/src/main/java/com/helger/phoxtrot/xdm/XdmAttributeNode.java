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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * An attribute node.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public sealed class XdmAttributeNode extends AbstractXdmValueNode
{
  private final XdmQName m_aName;
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

  /**
   * The ID kind is kept in the details holder: only ID, IDREF and IDREFS
   * attributes (DTD, <code>xml:id</code> or schema type) have one.
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
   * An attribute with the location of its source, created by the tree builders
   * when they record locations ({@link XdmParser#setRecordLocations(boolean)})
   * and by the factories of {@link IXdmNode} that take a location. The
   * location is kept in two fields of its own instead of the details holder.
   */
  static final class Located extends XdmAttributeNode
  {
    private int m_nLine;
    private int m_nColumn;

    Located (@NonNull final XdmQName aName, @NonNull final String sValue, @Nonnegative final int nLine, @Nonnegative final int nColumn)
    {
      super (aName, sValue);
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
