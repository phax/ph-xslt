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
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;

/**
 * Type derivation relations of the component model (go-xslt
 * <code>instance.go</code>: <code>parseBlockSet</code>,
 * <code>derivationMethodsTo</code>, <code>typeIdentityEqual</code>,
 * <code>derivesFrom</code>, <code>simpleDerivesFrom</code>). Every walk is
 * bounded.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdDerivation
{
  static final String EXTENSION = "extension";
  static final String RESTRICTION = "restriction";
  static final String SUBSTITUTION = "substitution";
  static final String LIST = "list";
  static final String UNION = "union";

  private static final int MAX_CHAIN = 64;
  private static final int MAX_UNION_DEPTH = 64;

  @PresentForCodeCoverage
  private static final XsdDerivation INSTANCE = new XsdDerivation ();

  private XsdDerivation ()
  {}

  private static boolean _simpleDerivesFromMode (@NonNull final XsdSimpleType aType,
                                                 @NonNull final XsdSimpleType aBase,
                                                 final boolean bUnionSubst,
                                                 final int nDepth)
  {
    if (nDepth > MAX_UNION_DEPTH)
      return false;
    XsdSimpleType s = aType;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      if (s == aBase || !s.m_aName.isZero () && s.m_aName.equals (aBase.m_aName))
        return true;
      s = s.m_aBase;
    }
    // A member of a union is substitutable for the union while the union
    // carries no facets of its own (cos-st-derived-ok 2.2.4)
    if (aBase.m_eVariety == EXsdVariety.UNION && !aBase.m_aFacets.any ())
    {
      if (!bUnionSubst)
        return false;
      for (final XsdSimpleType aMember : aBase.m_aMembers)
        if (_simpleDerivesFromMode (aType, aMember, true, nDepth + 1))
          return true;
      return false;
    }
    // The atomic-hierarchy fallback applies only to a built-in base
    if (aBase.m_bBaseBuiltin && aBase.m_aName.isZero ())
    {
      final EAtomicType eBasePrim = aBase.resolvePrim ();
      if (eBasePrim == EAtomicType.ANY_ATOMIC_TYPE)
        return true;
      return aType.m_eVariety == EXsdVariety.ATOMIC && atomDerivesFrom (aType.resolvePrim (), eBasePrim);
    }
    return false;
  }

  /**
   * go-xslt <code>xpath.AtomDerivesFrom</code>: xs:anyAtomicType admits
   * everything, xs:numeric its members, xs:error nothing.
   */
  static boolean atomDerivesFrom (@Nullable final EAtomicType eType, @Nullable final EAtomicType eBase)
  {
    if (eBase == EAtomicType.ANY_ATOMIC_TYPE)
      return true;
    if (eType == null || eBase == null)
      return false;
    if (eBase == EAtomicType.NUMERIC)
      return eType.isNumeric ();
    if (eBase == EAtomicType.ERROR)
      return false;
    return eType.isDerivedFrom (eBase);
  }

  /**
   * Parse a block/final value into the set of derivation methods.
   * <code>#all</code> stands for restriction, extension and substitution.
   */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsSet <String> parseBlockSet (@NonNull final String sValue)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (final String sToken : XsdWhiteSpace.collapse (sValue).split (" "))
    {
      if (sToken.isEmpty ())
        continue;
      if ("#all".equals (sToken))
      {
        ret.add (RESTRICTION);
        ret.add (EXTENSION);
        ret.add (SUBSTITUTION);
      }
      else
        ret.add (sToken);
    }
    return ret;
  }

  /**
   * @return <code>true</code> if two type references denote the same named
   *         type, or are the same instance. Two bare references to the same
   *         built-in are equal.
   */
  static boolean typeIdentityEqual (@Nullable final IXsdType aA, @Nullable final IXsdType aB)
  {
    if (aA == aB)
      return true;
    if (aA instanceof final XsdComplexType aCA)
    {
      if (aB instanceof final XsdComplexType aCB)
        return !aCA.m_aName.isZero () && aCA.m_aName.equals (aCB.m_aName);
      return false;
    }
    if (aA instanceof final XsdSimpleType aSA && aB instanceof final XsdSimpleType aSB)
    {
      if (!aSA.m_aName.isZero () && aSA.m_aName.equals (aSB.m_aName))
        return true;
      return aSA.m_aName.isZero () &&
             aSB.m_aName.isZero () &&
             aSA.m_bBaseBuiltin &&
             aSB.m_bBaseBuiltin &&
             aSA.m_aBase == null &&
             aSB.m_aBase == null &&
             !aSA.m_aFacets.any () &&
             !aSB.m_aFacets.any () &&
             aSA.m_ePrim == aSB.m_ePrim;
    }
    return false;
  }

  /**
   * go-xslt <code>simpleDerivesFromMode</code>.
   *
   * @param aType
   *        The derived candidate.
   * @param aBase
   *        The base.
   * @param bUnionSubst
   *        Whether a union's member type counts as derived from the union.
   * @return <code>true</code> if the type is, or restricts, the base.
   */
  static boolean simpleDerivesFromMode (@NonNull final XsdSimpleType aType,
                                        @NonNull final XsdSimpleType aBase,
                                        final boolean bUnionSubst)
  {
    return _simpleDerivesFromMode (aType, aBase, bUnionSubst, 0);
  }

  /** go-xslt <code>simpleDerivesFrom</code>. */
  static boolean simpleDerivesFrom (@NonNull final XsdSimpleType aType, @NonNull final XsdSimpleType aBase)
  {
    return simpleDerivesFromMode (aType, aBase, true);
  }

  /**
   * go-xslt <code>derivesFromMode</code>.
   *
   * @param aType
   *        The candidate.
   * @param aBase
   *        The base; <code>null</code> or xs:anyType matches anything.
   * @param bUnionSubst
   *        Whether a union's member type counts as derived from the union.
   * @return <code>true</code> if the type is the base or derives from it.
   */
  static boolean derivesFromMode (@Nullable final IXsdType aType,
                                  @Nullable final IXsdType aBase,
                                  final boolean bUnionSubst)
  {
    if (aBase == null)
      return true;
    if (aBase instanceof final XsdComplexType aCBase && aCBase.isAnyType ())
      return true;
    if (aBase instanceof final XsdSimpleType aSBase)
    {
      // Every type derives from xs:anySimpleType, a fresh unnamed wrapper on
      // each reference ...
      if (aSBase.m_aName.isZero () &&
          aSBase.m_bBaseBuiltin &&
          aSBase.m_aBase == null &&
          !aSBase.m_aFacets.any () &&
          aSBase.m_eVariety == EXsdVariety.ATOMIC &&
          aSBase.m_ePrim == EAtomicType.ANY_ATOMIC_TYPE)
      {
        // ... except xs:anyType, which sits above it
        if (aType instanceof final XsdComplexType aCType && aCType.isAnyType ())
          return false;
        return true;
      }
      if (aType instanceof final XsdSimpleType aSType)
        return simpleDerivesFromMode (aSType, aSBase, bUnionSubst);
    }
    IXsdType aCur = aType;
    for (int i = 0; aCur != null && i < MAX_CHAIN; i++)
    {
      if (typeIdentityEqual (aCur, aBase))
        return true;
      if (aCur instanceof final XsdComplexType aCT)
        aCur = aCT.m_aBaseType;
      else
        if (aCur instanceof final XsdSimpleType aST)
          aCur = aST.m_aBase;
        else
          aCur = null;
    }
    return false;
  }

  /**
   * go-xslt <code>derivesFrom</code>: type is base, or derives from it through
   * extension/restriction (union members substitutable).
   */
  static boolean derivesFrom (@Nullable final IXsdType aType, @Nullable final IXsdType aBase)
  {
    return derivesFromMode (aType, aBase, true);
  }

  /**
   * go-xslt <code>derivationMethodsTo</code>.
   *
   * @return The derivation methods used walking the type chain down to the
   *         base; empty if the base is not reached.
   */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsSet <String> derivationMethodsTo (@Nullable final IXsdType aType, @Nullable final IXsdType aBase)
  {
    final boolean bAnyTypeBase = aBase instanceof final XsdComplexType aCB && aCB.isAnyType ();
    final ICommonsSet <String> aMethods = new CommonsHashSet <> ();
    IXsdType aCur = aType;
    for (int i = 0; aCur != null && i < MAX_CHAIN; i++)
    {
      if (typeIdentityEqual (aCur, aBase))
        return aMethods;
      if (aCur instanceof final XsdComplexType aC)
      {
        if (!aC.m_sDerivation.isEmpty ())
        {
          aMethods.add (aC.m_sDerivation);
          aCur = aC.m_aBaseType;
        }
        else
          if (!aC.isAnyType ())
          {
            // A complexType with no explicit derivation is an implicit
            // restriction of xs:anyType
            aMethods.add (RESTRICTION);
            if (bAnyTypeBase)
              return aMethods;
            aCur = null;
          }
          else
            aCur = aC.m_aBaseType;
      }
      else
        if (aCur instanceof final XsdSimpleType aS)
        {
          aMethods.add (RESTRICTION);
          if (aS.m_aBase != null)
            aCur = aS.m_aBase;
          else
          {
            // The user chain bottomed out on a built-in base
            if (aBase instanceof final XsdSimpleType aBS &&
                aBS.m_aBase == null &&
                aBS.m_bBaseBuiltin &&
                aBS.m_aName.isZero () &&
                aS.m_bBaseBuiltin &&
                atomDerivesFrom (aS.m_ePrim, aBS.m_ePrim))
              return aMethods;
            // A simple-type root's ultimate base is xs:anySimpleType -> xs:anyType
            if (bAnyTypeBase)
              return aMethods;
            aCur = null;
          }
        }
        else
          aCur = null;
    }
    // Union-member substitutability counts as derivation by restriction
    if (aBase instanceof final XsdSimpleType aBS && aBS.m_eVariety == EXsdVariety.UNION)
      if (aType instanceof final XsdSimpleType aTS && simpleDerivesFrom (aTS, aBS))
      {
        final ICommonsSet <String> ret = new CommonsHashSet <> ();
        ret.add (RESTRICTION);
        return ret;
      }
    return new CommonsHashSet <> ();
  }
}
