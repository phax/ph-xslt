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
package com.helger.xpath.ast;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * A parsed ItemType.
 *
 * @param kind
 *        Which kind of item type.
 * @param atomicType
 *        For ATOMIC the type.
 * @param nodeTest
 *        For NODE the kind test.
 * @param wildcard
 *        For FUNCTION, MAP, ARRAY: the wildcard form <code>function(*)</code>,
 *        <code>map(*)</code>, <code>array(*)</code>.
 * @param paramTypes
 *        For a typed FUNCTION test the parameter types.
 * @param returnType
 *        For a typed FUNCTION test the return type.
 * @param mapKeyType
 *        For a typed MAP test the key type.
 * @param valueType
 *        For a typed MAP test the value type; for a typed ARRAY test the
 *        member type.
 * @param schemaType
 *        For ATOMIC a user-defined simple type of the in-scope schema
 *        definitions (an atomic or union type; in a cast also a list type);
 *        <code>atomicType</code> is then its built-in base, or
 *        <code>xs:anyAtomicType</code>. <code>null</code> for built-in types.
 * @author Philip Helger
 */
public record ItemType (@NonNull Kind kind,
                        @Nullable EAtomicType atomicType,
                        @Nullable NodeTest nodeTest,
                        boolean wildcard,
                        @Nullable List <SeqType> paramTypes,
                        @Nullable SeqType returnType,
                        @Nullable EAtomicType mapKeyType,
                        @Nullable SeqType valueType,
                        @Nullable IXdmTypeDefinition schemaType)
{
  /** The ItemType kinds. */
  public enum Kind
  {
    /** <code>item()</code> */
    ITEM,
    ATOMIC,
    NODE,
    FUNCTION,
    MAP,
    ARRAY
  }

  /**
   * An item type without a user-defined schema type.
   *
   * @param kind
   *        Which kind of item type.
   * @param atomicType
   *        For ATOMIC the type.
   * @param nodeTest
   *        For NODE the kind test.
   * @param wildcard
   *        The wildcard form.
   * @param paramTypes
   *        Function parameter types.
   * @param returnType
   *        Function return type.
   * @param mapKeyType
   *        Map key type.
   * @param valueType
   *        Map value or array member type.
   */
  public ItemType (@NonNull final Kind kind,
                   @Nullable final EAtomicType atomicType,
                   @Nullable final NodeTest nodeTest,
                   final boolean wildcard,
                   @Nullable final List <SeqType> paramTypes,
                   @Nullable final SeqType returnType,
                   @Nullable final EAtomicType mapKeyType,
                   @Nullable final SeqType valueType)
  {
    this (kind, atomicType, nodeTest, wildcard, paramTypes, returnType, mapKeyType, valueType, null);
  }

  public static final ItemType ITEM = new ItemType (Kind.ITEM, null, null, false, null, null, null, null);

  @NonNull
  public static ItemType atomic (@NonNull final EAtomicType eType)
  {
    return new ItemType (Kind.ATOMIC, eType, null, false, null, null, null, null);
  }

  /**
   * @param eBuiltin
   *        The built-in base type, or <code>xs:anyAtomicType</code> for a union
   *        or list type.
   * @param aSchemaType
   *        The user-defined simple type.
   * @return An atomic item type of a user-defined simple type.
   */
  @NonNull
  public static ItemType schemaAtomic (@NonNull final EAtomicType eBuiltin, @NonNull final IXdmTypeDefinition aSchemaType)
  {
    return new ItemType (Kind.ATOMIC, eBuiltin, null, false, null, null, null, null, aSchemaType);
  }

  @NonNull
  public static ItemType node (@NonNull final NodeTest aTest)
  {
    return new ItemType (Kind.NODE, null, aTest, false, null, null, null, null);
  }
}
