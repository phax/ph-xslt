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
package com.helger.xpath.xdm.schema;

import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmValidationException;

/**
 * A schema type definition of the schema component model, simple or complex.
 * Two instances describing the same component are equal.
 *
 * @author Philip Helger
 */
public interface IXdmTypeDefinition
{
  /**
   * @return The type name, <code>null</code> for an anonymous type.
   */
  @Nullable
  XdmQName getName ();

  /**
   * @return <code>true</code> if the type is anonymous.
   */
  default boolean isAnonymous ()
  {
    return getName () == null;
  }

  /**
   * @return <code>true</code> for a complex type, <code>false</code> for a
   *         simple type.
   */
  boolean isComplex ();

  /**
   * @return The content type. A simple type has {@link EXdmContentType#SIMPLE}
   *         content.
   */
  @NonNull
  EXdmContentType getContentType ();

  /**
   * @return The simple type of the content: the type itself for a simple type,
   *         the content simple type for a complex type with simple content,
   *         otherwise <code>null</code>.
   */
  @Nullable
  IXdmTypeDefinition getSimpleContentType ();

  /**
   * @return The variety of a simple type, <code>null</code> for a complex type
   *         and for <code>xs:anySimpleType</code>.
   */
  @Nullable
  EXdmSimpleVariety getVariety ();

  /**
   * @return The item type of a list type, otherwise <code>null</code>.
   */
  @Nullable
  IXdmTypeDefinition getItemType ();

  /**
   * @return The member types of a union type (empty for other types).
   */
  @NonNull
  ICommonsList <IXdmTypeDefinition> getMemberTypes ();

  /**
   * @return The most specific built-in atomic type an atomic type is, or is
   *         derived from; <code>null</code> for other types.
   */
  @Nullable
  XdmQName getBuiltinAtomicType ();

  /**
   * @return The base type definition, <code>null</code> for
   *         <code>xs:anyType</code>.
   */
  @Nullable
  IXdmTypeDefinition getBaseType ();

  /**
   * The schema derivation check: is this type the given type, or derived from
   * it by restriction or extension (for a simple type also as a member of a
   * union is not counted)?
   *
   * @param aBase
   *        The candidate base type.
   * @return <code>true</code> if derived.
   */
  boolean isDerivedFrom (@NonNull IXdmTypeDefinition aBase);

  /**
   * @return <code>true</code> if the type is <code>xs:QName</code> or
   *         <code>xs:NOTATION</code> or derived from one of them, or a list or
   *         union with such an item or member type.
   */
  boolean isNamespaceSensitive ();

  /**
   * Validate a lexical value against this simple type (or the simple content
   * type of a complex type).
   *
   * @param sLexical
   *        The value.
   * @param aNamespaceResolver
   *        Prefix to namespace URI (the empty prefix gives the default
   *        namespace), for namespace-sensitive types. May be
   *        <code>null</code>.
   * @return The atomic values: one for an atomic type, one per item for a
   *         list type.
   * @throws XdmValidationException
   *         if the value is not valid
   */
  @NonNull
  ICommonsList <XdmTypedToken> validate (@NonNull String sLexical,
                                         @Nullable UnaryOperator <String> aNamespaceResolver) throws XdmValidationException;

  /**
   * @return The definition as a DOM {@link TypeInfo} (the object a validator
   *         reports), for {@link com.helger.xpath.xdm.XdmSchemaType}.
   */
  @NonNull
  TypeInfo getTypeInfo ();
}
