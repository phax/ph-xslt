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

import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSObjectList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * {@link IXdmElementDeclaration} over a Xerces-J element declaration.
 *
 * @author Philip Helger
 */
@Immutable
final class XercesElementDeclaration implements IXdmElementDeclaration
{
  private final XSModel m_aModel;
  private final XSElementDeclaration m_aDecl;

  XercesElementDeclaration (@NonNull final XSModel aModel, @NonNull final XSElementDeclaration aDecl)
  {
    m_aModel = aModel;
    m_aDecl = aDecl;
  }

  @NonNull
  static XdmQName getName (@NonNull final XSElementDeclaration aDecl)
  {
    final String sNamespace = aDecl.getNamespace ();
    return new XdmQName (sNamespace == null ? "" : sNamespace, aDecl.getName ());
  }

  @NonNull
  public XdmQName getName ()
  {
    return getName (m_aDecl);
  }

  @NonNull
  public IXdmTypeDefinition getTypeDefinition ()
  {
    return new XercesTypeDefinition (m_aDecl.getTypeDefinition ());
  }

  public boolean isNillable ()
  {
    return m_aDecl.getNillable ();
  }

  public boolean isAbstract ()
  {
    return m_aDecl.getAbstract ();
  }

  @Nullable
  public IXdmElementDeclaration getSubstitutionMember (@NonNull final XdmQName aName)
  {
    if (getName ().equals (aName))
      return this;
    // The substitution group of the model is transitive already
    final XSObjectList aGroup = m_aModel.getSubstitutionGroup (m_aDecl);
    if (aGroup != null)
      for (int i = 0; i < aGroup.getLength (); ++i)
      {
        final XSElementDeclaration aMember = (XSElementDeclaration) aGroup.item (i);
        if (getName (aMember).equals (aName))
          return new XercesElementDeclaration (m_aModel, aMember);
      }
    return null;
  }

  @Override
  public String toString ()
  {
    return "XercesElementDeclaration[" + getName () + "]";
  }
}
