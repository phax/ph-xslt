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
package com.helger.xpath.xdm.xerces;

import org.apache.xerces.xs.XSAttributeDeclaration;
import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * {@link IXdmAttributeDeclaration} over a Xerces-J attribute declaration.
 *
 * @author Philip Helger
 */
@Immutable
final class XercesAttributeDeclaration implements IXdmAttributeDeclaration
{
  private final XSAttributeDeclaration m_aDecl;

  XercesAttributeDeclaration (@NonNull final XSAttributeDeclaration aDecl)
  {
    m_aDecl = aDecl;
  }

  @NonNull
  public XdmQName getName ()
  {
    final String sNamespace = m_aDecl.getNamespace ();
    return new XdmQName (sNamespace == null ? "" : sNamespace, m_aDecl.getName ());
  }

  @NonNull
  public IXdmTypeDefinition getTypeDefinition ()
  {
    return new XercesTypeDefinition (m_aDecl.getTypeDefinition ());
  }

  @Override
  public String toString ()
  {
    return "XercesAttributeDeclaration[" + getName () + "]";
  }
}
