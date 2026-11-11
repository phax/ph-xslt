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

import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmSchemaType;

/**
 * The type of an element or attribute of an {@link XdmDomView}: the name of its XDM type annotation, none for an
 * unvalidated node. {@link #isDerivedFrom(String, String, int)} uses the schema type of a validated node and does not
 * tell the derivation methods apart: any derivation by restriction or extension counts.
 *
 * @author Philip Helger
 */
@Immutable
final class XdmDomTypeInfo implements TypeInfo
{
  private final XdmQName m_aTypeName;
  private final XdmSchemaType m_aSchemaType;

  /**
   * @param aTypeName
   *        The type annotation, <code>null</code> for none.
   * @param aSchemaType
   *        The schema type of a validated node, <code>null</code> for none.
   */
  XdmDomTypeInfo (@Nullable final XdmQName aTypeName, @Nullable final XdmSchemaType aSchemaType)
  {
    m_aTypeName = aTypeName;
    m_aSchemaType = aSchemaType;
  }

  @Nullable
  public String getTypeName ()
  {
    return m_aTypeName == null ? null : m_aTypeName.localName ();
  }

  @Nullable
  public String getTypeNamespace ()
  {
    return m_aTypeName == null ? null : AbstractXdmDomNode.nullIfEmpty (m_aTypeName.namespaceUri ());
  }

  public boolean isDerivedFrom (final String sTypeNamespaceArg, final String sTypeNameArg, final int nDerivationMethod)
  {
    if (m_aTypeName == null || sTypeNameArg == null)
      return false;
    final String sNs = sTypeNamespaceArg == null ? "" : sTypeNamespaceArg;
    if (m_aSchemaType != null)
      return m_aSchemaType.isDerivedFrom (sNs, sTypeNameArg);
    return m_aTypeName.isSameName (sNs, sTypeNameArg);
  }
}
