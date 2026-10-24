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
 * @author Philip Helger
 */
public record ItemType (@NonNull Kind kind,
                        @Nullable EAtomicType atomicType,
                        @Nullable NodeTest nodeTest,
                        boolean wildcard,
                        @Nullable List <SeqType> paramTypes,
                        @Nullable SeqType returnType,
                        @Nullable EAtomicType mapKeyType,
                        @Nullable SeqType valueType)
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

  public static final ItemType ITEM = new ItemType (Kind.ITEM, null, null, false, null, null, null, null);

  @NonNull
  public static ItemType atomic (@NonNull final EAtomicType eType)
  {
    return new ItemType (Kind.ATOMIC, eType, null, false, null, null, null, null);
  }

  @NonNull
  public static ItemType node (@NonNull final NodeTest aTest)
  {
    return new ItemType (Kind.NODE, null, aTest, false, null, null, null, null);
  }
}
