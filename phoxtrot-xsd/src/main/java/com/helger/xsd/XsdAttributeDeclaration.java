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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * {@link IXdmAttributeDeclaration} over a global {@link XsdAttributeDecl}.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdAttributeDeclaration implements IXdmAttributeDeclaration
{
  private final XsdBridge m_aBridge;
  private final XsdAttributeDecl m_aDecl;

  XsdAttributeDeclaration (@NonNull final XsdBridge aBridge, @NonNull final XsdAttributeDecl aDecl)
  {
    m_aBridge = aBridge;
    m_aDecl = aDecl;
  }

  @NonNull
  public XdmQName getName ()
  {
    return new XdmQName (m_aDecl.m_aName.space (), m_aDecl.m_aName.local ());
  }

  @NonNull
  public IXdmTypeDefinition getTypeDefinition ()
  {
    return m_aBridge.definitionOf (m_aDecl.m_aType);
  }

  @Override
  public String toString ()
  {
    return "XsdAttributeDeclaration[" + m_aDecl.m_aName + "]";
  }
}
