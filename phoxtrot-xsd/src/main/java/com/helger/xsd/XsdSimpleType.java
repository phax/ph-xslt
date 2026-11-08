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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;

/**
 * A compiled simple type (atomic, list or union) - go-xslt
 * <code>SimpleType</code>. Fields are filled by the compiler and must not be
 * changed once {@link XsdSchema} is built.
 *
 * @author Philip Helger
 */
final class XsdSimpleType implements IXsdType
{
  /**
   * The sentinel bound where an element/attribute <code>@type</code> or a list
   * <code>@itemType</code> names a component the schema set does not contain:
   * XSD 1.0's missing-sub-components rules keep such a schema usable, and the
   * error surfaces only when a declaration carrying the absent type is
   * exercised at validation time.
   */
  static final XsdSimpleType ABSENT = new XsdSimpleType (new XsdName ("", "\u0000absent"));

  /** XSD 1.1's xs:error: the type with an empty value space. */
  static final XsdSimpleType ERROR = new XsdSimpleType (new XsdName (XsdConstants.NS_XS, "error"));

  /** The empty name for anonymous types. */
  XsdName m_aName = XsdName.NONE;

  EXsdVariety m_eVariety = EXsdVariety.ATOMIC;

  /** The resolved built-in primitive at the bottom of the base chain, <code>null</code> if unset. */
  EAtomicType m_ePrim;
  /** The immediate base simple type, <code>null</code> when the base is a built-in. */
  XsdSimpleType m_aBase;
  /** <code>true</code> when the base is a built-in ({@link #m_ePrim} is authoritative). */
  boolean m_bBaseBuiltin;
  XsdFacetSet m_aFacets = new XsdFacetSet ();
  ICommonsList <XsdDocumentation> m_aDocs = new CommonsArrayList <> ();

  /** The item type of a list. */
  XsdSimpleType m_aItem;
  /** The member types of a union. */
  ICommonsList <XsdSimpleType> m_aMembers = new CommonsArrayList <> ();

  /**
   * Marks a user-defined type (global or inline) as opposed to the sentinel a
   * bare built-in reference materialises.
   */
  boolean m_bDeclared;

  /** The derivation methods this type forbids (restriction/list/union/extension). */
  ICommonsSet <String> m_aFinal = new CommonsHashSet <> ();

  /** XSD 1.1 xs:assertion facets declared at this restriction step. */
  ICommonsList <XsdAssertion> m_aAssertions = new CommonsArrayList <> ();

  XsdSimpleType ()
  {}

  XsdSimpleType (@NonNull final XsdName aName)
  {
    m_aName = aName;
  }

  /**
   * @param ePrim
   *        The built-in primitive.
   * @return A bare reference to a built-in atomic type.
   */
  @NonNull
  static XsdSimpleType builtin (@NonNull final EAtomicType ePrim)
  {
    final XsdSimpleType ret = new XsdSimpleType ();
    ret.m_bBaseBuiltin = true;
    ret.m_ePrim = ePrim;
    return ret;
  }

  /** @return A bare reference to xs:anySimpleType. */
  @NonNull
  static XsdSimpleType anySimpleType ()
  {
    return builtin (EAtomicType.ANY_ATOMIC_TYPE);
  }

  /** Copy every field of another type into this one (Go <code>*st = *o</code>). */
  void assignFrom (@NonNull final XsdSimpleType aOther)
  {
    m_aName = aOther.m_aName;
    m_eVariety = aOther.m_eVariety;
    m_ePrim = aOther.m_ePrim;
    m_aBase = aOther.m_aBase;
    m_bBaseBuiltin = aOther.m_bBaseBuiltin;
    m_aFacets = aOther.m_aFacets.getClone ();
    m_aDocs = aOther.m_aDocs;
    m_aItem = aOther.m_aItem;
    m_aMembers = aOther.m_aMembers;
    m_bDeclared = aOther.m_bDeclared;
    m_aFinal = aOther.m_aFinal;
    m_aAssertions = aOther.m_aAssertions;
  }

  /** @return A shallow copy (Go <code>cp := *o</code>). */
  @NonNull
  XsdSimpleType getShallowCopy ()
  {
    final XsdSimpleType ret = new XsdSimpleType ();
    ret.assignFrom (this);
    return ret;
  }

  /**
   * Reset to a fresh type with the given name (Go
   * <code>*st = SimpleType{name: k}</code>).
   */
  void reset (@NonNull final XsdName aName)
  {
    assignFrom (new XsdSimpleType (aName));
  }

  /** @return <code>true</code> for an anonymous type. */
  boolean isAnonymous ()
  {
    return m_aName.isZero ();
  }

  /**
   * Walk the base chain to the built-in primitive (cycle-guarded).
   *
   * @return The primitive, xs:anyAtomicType if none is known.
   */
  @NonNull
  EAtomicType resolvePrim ()
  {
    XsdSimpleType s = this;
    for (int n = 0; s != null && n < 64; n++)
    {
      if (s.m_bBaseBuiltin)
        return s.m_ePrim != null ? s.m_ePrim : EAtomicType.ANY_ATOMIC_TYPE;
      if (s.m_aBase == null)
        return s.m_ePrim != null ? s.m_ePrim : EAtomicType.ANY_ATOMIC_TYPE;
      s = s.m_aBase;
    }
    return EAtomicType.ANY_ATOMIC_TYPE;
  }

  /**
   * @return The strongest of the primitive default and any explicit whiteSpace
   *         facet down the base chain (preserve &lt; replace &lt; collapse).
   */
  @NonNull
  String effectiveWhiteSpace ()
  {
    String sBest = XsdWhiteSpace.primitiveWhiteSpace (resolvePrim (), m_eVariety);
    XsdSimpleType s = this;
    for (int n = 0; s != null && n < 64; n++)
    {
      if (!s.m_aFacets.m_sWhiteSpace.isEmpty ())
        sBest = XsdWhiteSpace.stronger (sBest, s.m_aFacets.m_sWhiteSpace);
      if (s.m_bBaseBuiltin || s.m_aBase == null)
        break;
      s = s.m_aBase;
    }
    return sBest;
  }

  /**
   * @return <code>true</code> if this is the bare ur-type sentinel a reference to
   *         xs:anySimpleType or xs:anyAtomicType produces.
   */
  boolean isURType ()
  {
    return m_bBaseBuiltin &&
           m_aBase == null &&
           m_aName.isZero () &&
           m_eVariety == EXsdVariety.ATOMIC &&
           !m_aFacets.any () &&
           (m_ePrim == EAtomicType.ANY_ATOMIC_TYPE || m_ePrim == EAtomicType.UNTYPED_ATOMIC);
  }

  /**
   * @param aType
   *        The simple type. May be <code>null</code>.
   * @return <code>true</code> if the type is the bare ur-type sentinel.
   */
  static boolean isURType (@Nullable final XsdSimpleType aType)
  {
    return aType != null && aType.isURType ();
  }

  @Override
  public String toString ()
  {
    return "XsdSimpleType[" + (m_aName.isZero () ? "anonymous" : m_aName.toString ()) + "]";
  }
}
