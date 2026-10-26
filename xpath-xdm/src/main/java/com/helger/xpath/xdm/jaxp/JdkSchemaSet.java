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
package com.helger.xpath.xdm.jaxp;

import javax.xml.validation.Schema;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * Schema set of the JDK validator. It validates, but has no schema component
 * model: types, element and attribute declarations cannot be looked up by
 * name, and the type definitions of validated nodes only know their name and
 * derivation (see {@link JdkTypeDefinition}).
 *
 * @author Philip Helger
 */
@Immutable
final class JdkSchemaSet extends AbstractJaxpSchemaSet
{
  JdkSchemaSet (@NonNull final Schema aSchema)
  {
    super (aSchema);
  }

  @NonNull
  @Override
  protected IXdmTypeDefinition getDefinition (@NonNull final TypeInfo aTypeInfo)
  {
    return new JdkTypeDefinition (aTypeInfo, false);
  }

  /**
   * @return Always <code>null</code>: the JDK has no schema component model.
   */
  @Nullable
  public IXdmTypeDefinition getTypeDefinition (@NonNull final XdmQName aName)
  {
    return null;
  }

  /**
   * @return Always <code>null</code>: the JDK has no schema component model.
   */
  @Nullable
  public IXdmElementDeclaration getElementDeclaration (@NonNull final XdmQName aName)
  {
    return null;
  }

  /**
   * @return Always <code>null</code>: the JDK has no schema component model.
   */
  @Nullable
  public IXdmAttributeDeclaration getAttributeDeclaration (@NonNull final XdmQName aName)
  {
    return null;
  }
}
