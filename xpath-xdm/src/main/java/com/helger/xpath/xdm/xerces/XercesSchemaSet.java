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

import java.util.function.IntFunction;

import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

import org.apache.xerces.xs.AttributePSVI;
import org.apache.xerces.xs.PSVIProvider;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSTypeDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.jaxp.AbstractJaxpSchemaSet;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * {@link IXdmSchemaSet} over a schema compiled by Xerces-J: the JAXP schema
 * for validation and the schema component model of the same grammars, so that
 * the types a validator reports are the components of the model.
 *
 * @author Philip Helger
 */
@Immutable
final class XercesSchemaSet extends AbstractJaxpSchemaSet
{
  private final XSModel m_aModel;

  XercesSchemaSet (@NonNull final Schema aSchema, @NonNull final XSModel aModel)
  {
    super (aSchema);
    m_aModel = aModel;
  }

  @NonNull
  @Override
  protected IXdmTypeDefinition getDefinition (@NonNull final TypeInfo aTypeInfo)
  {
    if (aTypeInfo instanceof final XSTypeDefinition aType)
      return new XercesTypeDefinition (aType);
    throw new IllegalStateException ("Not a Xerces-J type: " + aTypeInfo);
  }

  @Nullable
  @Override
  protected IntFunction <TypeInfo> getDeclaredAttributeTypes (@NonNull final ValidatorHandler aValidator)
  {
    if (!(aValidator instanceof final PSVIProvider aPSVI))
      return null;
    return nIndex -> {
      final AttributePSVI aAttr = aPSVI.getAttributePSVI (nIndex);
      return aAttr != null && aAttr.getTypeDefinition () instanceof final TypeInfo aTypeInfo ? aTypeInfo : null;
    };
  }

  @Nullable
  private static String _ns (@NonNull final XdmQName aName)
  {
    return aName.namespaceUri ().isEmpty () ? null : aName.namespaceUri ();
  }

  @Nullable
  public IXdmTypeDefinition getTypeDefinition (@NonNull final XdmQName aName)
  {
    return XercesTypeDefinition.wrap (m_aModel.getTypeDefinition (aName.localName (), _ns (aName)));
  }

  @Nullable
  public IXdmElementDeclaration getElementDeclaration (@NonNull final XdmQName aName)
  {
    final XSElementDeclaration aDecl = m_aModel.getElementDeclaration (aName.localName (), _ns (aName));
    return aDecl == null ? null : new XercesElementDeclaration (m_aModel, aDecl);
  }

  @Nullable
  public IXdmAttributeDeclaration getAttributeDeclaration (@NonNull final XdmQName aName)
  {
    final XSAttributeDeclaration aDecl = m_aModel.getAttributeDeclaration (aName.localName (), _ns (aName));
    return aDecl == null ? null : new XercesAttributeDeclaration (aDecl);
  }
}
