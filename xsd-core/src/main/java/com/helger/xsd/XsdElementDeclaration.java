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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * {@link IXdmElementDeclaration} over a global {@link XsdElementDecl}.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdElementDeclaration implements IXdmElementDeclaration
{
  private final XsdBridge m_aBridge;
  private final XsdElementDecl m_aDecl;

  XsdElementDeclaration (@NonNull final XsdBridge aBridge, @NonNull final XsdElementDecl aDecl)
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

  public boolean isNillable ()
  {
    return m_aDecl.m_bNillable;
  }

  public boolean isAbstract ()
  {
    return m_aDecl.m_bAbstract;
  }

  @Nullable
  public IXdmElementDeclaration getSubstitutionMember (@NonNull final XdmQName aName)
  {
    final XsdName aWanted = XsdName.of (aName);
    if (m_aDecl.m_aName.equals (aWanted))
      return this;
    // Go-xslt SubstitutesFor: the substitution group of the global
    // declaration, honouring abstract and block
    final XsdSchema aSchema = m_aBridge.getSchema ();
    if (aSchema.m_aElements.get (m_aDecl.m_aName) != m_aDecl || !aSchema.matchesElementName (m_aDecl, aWanted))
      return null;
    final XsdElementDecl aMember = aSchema.m_aElements.get (aWanted);
    return aMember == null ? null : new XsdElementDeclaration (m_aBridge, aMember);
  }

  @Override
  public String toString ()
  {
    return "XsdElementDeclaration[" + m_aDecl.m_aName + "]";
  }
}
