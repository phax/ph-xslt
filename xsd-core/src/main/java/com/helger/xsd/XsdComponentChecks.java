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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

/**
 * The whole-schema component checks of go-xslt <code>structure.go</code>
 * (element declarations consistent, xs:all placement, derivation of content
 * and attributes, substitution groups, cycles, identity-constraint names). The
 * particle-restriction and UPA algorithms they call are phase V4
 * ({@link XsdRestrictionChecker}, {@link XsdUpa}). Every walk over
 * input-controlled structure is iterative or bounded.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XsdComponentChecks
{
  private static final int MAX_CHAIN = 64;

  private final XsdCompiler m_aC;

  /** One entry of the XSD 1.1 consistency table. */
  private record EdcEntry (@NonNull Object sig, int alts, @NonNull XsdElementDecl decl)
  {}

  XsdComponentChecks (@NonNull final XsdCompiler aCompiler)
  {
    m_aC = aCompiler;
  }

  /** Push the particles of a group in reverse, so that they pop in order. */
  private static void _pushReversed (@NonNull final ICommonsList <XsdParticle> aStack,
                                     @NonNull final ICommonsList <XsdParticle> aParticles)
  {
    for (int i = aParticles.size () - 1; i >= 0; i--)
      aStack.add (aParticles.get (i));
  }

  /**
   * go-xslt <code>typeTablesEquivalent</code>: equivalent XSD 1.1 {type
   * table}s (same alternatives, pairwise identical test text and type).
   */
  private static boolean _typeTablesEquivalent (@NonNull final XsdElementDecl aA, @NonNull final XsdElementDecl aB)
  {
    if (aA.m_aAlternatives.size () != aB.m_aAlternatives.size ())
      return false;
    for (int i = 0; i < aA.m_aAlternatives.size (); ++i)
    {
      final XsdTypeAlternative x = aA.m_aAlternatives.get (i);
      final XsdTypeAlternative y = aB.m_aAlternatives.get (i);
      if (!x.m_sRawTest.equals (y.m_sRawTest) || !typeSignature (x.m_aType).equals (typeSignature (y.m_aType)))
        return false;
    }
    return true;
  }

  private static void _addEdc (@NonNull final ICommonsOrderedMap <XsdName, EdcEntry> aSigs,
                               @NonNull final XsdName aName,
                               @NonNull final XsdElementDecl aDecl)
  {
    final Object aSig = typeSignature (aDecl.m_aType);
    final int nAlts = aDecl.m_aAlternatives.size ();
    final EdcEntry aPrev = aSigs.get (aName);
    if (aPrev != null && (!aPrev.sig ().equals (aSig) || aPrev.alts () != nAlts))
      throw XsdCompiler.invalid ("cos-element-consistent",
                                 "inconsistent element declarations for " + aName + " in one content model");
    aSigs.put (aName, new EdcEntry (aSig, nAlts, aDecl));
  }

  /**
   * go-xslt <code>checkElementConsistency11</code>: the XSD 1.1 half of
   * cos-element-consistent - substitution members (abstract ones included)
   * take part, and a non-skip wildcard admitting a local declaration's name
   * requires equivalent type tables of the local and the global declaration.
   */
  private void _checkElementConsistency11 (@Nullable final XsdParticle aRoot)
  {
    if (aRoot == null)
      return;
    final XsdSchema aSchema = m_aC.m_aSchema;
    final ICommonsOrderedMap <XsdName, EdcEntry> aSigs = new CommonsLinkedHashMap <> ();
    final ICommonsList <XsdWildcard> aWCs = new CommonsArrayList <> ();
    final ICommonsSet <XsdModelGroup> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdParticle> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (aStack.isNotEmpty ())
    {
      final XsdParticle p = aStack.removeLast ();
      if (p.m_aTerm instanceof final XsdElementDecl aDecl)
      {
        _addEdc (aSigs, aDecl.m_aName, aDecl);
        if (aSchema.m_aElements.get (aDecl.m_aName) == aDecl)
        {
          // Substitution members take part, abstract ones included
          final ICommonsSet <XsdName> aSeenMembers = new CommonsHashSet <> ();
          final ICommonsList <XsdName> aHeads = new CommonsArrayList <> ();
          aHeads.add (aDecl.m_aName);
          while (aHeads.isNotEmpty ())
          {
            final XsdName aHead = aHeads.removeLast ();
            for (final XsdElementDecl aMember : aSchema.substMembersOf (aHead))
              if (aSeenMembers.add (aMember.m_aName))
              {
                _addEdc (aSigs, aMember.m_aName, aMember);
                aHeads.add (aMember.m_aName);
              }
          }
        }
      }
      else
        if (p.m_aTerm instanceof final XsdWildcard aWC)
          aWCs.add (aWC);
        else
          if (p.m_aTerm instanceof final XsdModelGroup aMG && aSeen.add (aMG))
            _pushReversed (aStack, aMG.m_aParticles);
    }
    for (final XsdWildcard aWC : aWCs)
    {
      if (XsdWildcard.PROCESS_SKIP.equals (aWC.m_sProcess))
        continue;
      for (final var aEntry : aSigs.entrySet ())
      {
        if (!aWC.matches (aEntry.getKey ()))
          continue;
        final XsdElementDecl aGlobal = aSchema.m_aElements.get (aEntry.getKey ());
        if (aGlobal == null || aGlobal == aEntry.getValue ().decl ())
          continue;
        if (!_typeTablesEquivalent (aEntry.getValue ().decl (), aGlobal))
          throw XsdCompiler.invalid ("cos-element-consistent",
                                     "element " +
                                                             aEntry.getKey () +
                                                             ": type table differs from the global declaration a wildcard match would be assessed against");
      }
    }
  }

  /**
   * go-xslt <code>checkContentBaseKind</code>: src-ct.1/2, the derivation
   * rules about what a derivation may take as its base (run as a post-pass,
   * when forward-referenced bases are complete).
   */
  private void _checkContentBaseKind (@NonNull final XsdComplexType aCT)
  {
    if (aCT.m_aBaseType == null)
      return;
    final XsdSchema aSchema = m_aC.m_aSchema;
    if (aCT.m_eKind != EXsdContentKind.SIMPLE)
    {
      // src-ct.1: a complexContent derivation must start from a complex type
      if (aCT.m_aBaseType instanceof XsdSimpleType && !aCT.m_sDerivation.isEmpty ())
        throw XsdCompiler.invalid ("src-ct.1", "a complexContent derivation requires a complex-type base");
      if (aCT.m_aBaseType instanceof final XsdComplexType b)
      {
        // derivation-ok-restriction 5.2/5.3: a complexContent restriction of a
        // simple-content base
        if (XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation) && b.m_eKind == EXsdContentKind.SIMPLE)
          throw XsdCompiler.invalid ("derivation-ok-restriction.5.2",
                                     "a complexContent restriction cannot derive from the simple-content type " + b.m_aName);
        // cos-ct-extends 1.4.2.2: a complexContent extension of a
        // simple-content base may only add attributes (1.1: not even that)
        if (XsdDerivation.EXTENSION.equals (aCT.m_sDerivation) &&
            b.m_eKind == EXsdContentKind.SIMPLE &&
            (!isEmptyParticle (aCT.m_aParticle) || m_aC.isXsd11 ()))
          throw XsdCompiler.invalid ("cos-ct-extends.1.4.2.2",
                                     "an extension of the simple-content type " + b.m_aName + " cannot add element content");
        // cos-ct-extends.1.4.3.2.2.1: an extension may not change mixedness
        if (XsdDerivation.EXTENSION.equals (aCT.m_sDerivation) &&
            !b.isAnyType () &&
            !isEmptyParticle (aSchema.effectiveParticle (b)))
        {
          final boolean bDerivedMixed = aCT.m_eKind == EXsdContentKind.MIXED;
          final boolean bBaseMixed = b.m_eKind == EXsdContentKind.MIXED;
          if (!isEmptyParticle (aCT.m_aParticle) && bDerivedMixed != bBaseMixed)
            throw XsdCompiler.invalid ("cos-ct-extends.1.4.3.2.2.1", "an extension may not change the mixedness of " + b.m_aName);
          // Declaring mixed over an element-only base with an empty body is
          // not allowed either
          if (isEmptyParticle (aCT.m_aParticle) && bDerivedMixed && !bBaseMixed)
            throw XsdCompiler.invalid ("cos-ct-extends.1.4.3.2.2.1", "an extension may not change the mixedness of " + b.m_aName);
        }
        // derivation-ok-restriction 5.4.1.2, one direction: a mixed restriction
        // requires a mixed base
        if (XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation) &&
            !b.isAnyType () &&
            aCT.m_eKind == EXsdContentKind.MIXED &&
            b.m_eKind != EXsdContentKind.MIXED)
          throw XsdCompiler.invalid ("derivation-ok-restriction.5.4.1.2", "a mixed restriction requires a mixed base (" + b.m_aName + ")");
      }
      return;
    }
    if (aCT.m_aBaseType instanceof final XsdComplexType b)
    {
      if (b.m_eKind == EXsdContentKind.SIMPLE)
        return;
      // src-ct.2.1.2: a restriction may also start from a mixed complex type
      // whose particle is emptiable
      if (XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation) &&
          b.m_eKind == EXsdContentKind.MIXED &&
          particleEmptiable (aSchema.effectiveParticle (b), 0))
        return;
      throw XsdCompiler.invalid ("src-ct.2.1", "simpleContent base " + b.m_aName + " does not have simple content");
    }
    if (aCT.m_aBaseType instanceof XsdSimpleType && XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation))
      throw XsdCompiler.invalid ("src-ct.2.1", "a simpleContent restriction requires a complex-type base");
  }

  /**
   * go-xslt <code>checkAllPlacement</code>: the placement half of
   * cos-all-limited.
   */
  private void _checkAllPlacement (@NonNull final XsdComplexType aCT)
  {
    final XsdParticle aRoot = aCT.m_aParticle;
    if (aRoot == null)
      return;
    if (aRoot.m_aTerm instanceof final XsdModelGroup aRootMG && aRootMG.m_eCompositor == EXsdCompositor.ALL)
    {
      final boolean bMaxOK = aRoot.m_nMax == 1 || !m_aC.isXsd10 () && aRoot.m_nMax == 0;
      if (!bMaxOK || aRoot.m_nMin > 1)
        throw XsdCompiler.invalid ("cos-all-limited.2", "an xs:all group must have minOccurs 0 or 1 and maxOccurs 1");
    }
    final ICommonsSet <XsdModelGroup> aSeen = new CommonsHashSet <> ();
    // Stack of (particle, parent group) pairs
    final ICommonsList <XsdParticle> aStack = new CommonsArrayList <> ();
    final ICommonsList <XsdModelGroup> aParents = new CommonsArrayList <> ();
    aStack.add (aRoot);
    aParents.add (null);
    while (aStack.isNotEmpty ())
    {
      final XsdParticle p = aStack.removeLast ();
      final XsdModelGroup aParent = aParents.removeLast ();
      if (!(p.m_aTerm instanceof final XsdModelGroup aMG) || !aSeen.add (aMG))
        continue;
      if (aParent != null)
      {
        if (m_aC.isXsd10 ())
        {
          if (aMG.m_eCompositor == EXsdCompositor.ALL)
            throw XsdCompiler.invalid ("cos-all-limited.1", "an xs:all group must be the whole content model");
        }
        else
          if (aParent.m_eCompositor == EXsdCompositor.ALL)
          {
            // 1.1: inside an xs:all only a 1..1 reference to another all group
            if (aMG.m_eCompositor != EXsdCompositor.ALL)
              throw XsdCompiler.invalid ("cos-all-limited.1", "a sequence/choice group may not appear inside an xs:all");
            if (p.m_nMin != 1 || p.m_nMax != 1)
              throw XsdCompiler.invalid ("cos-all-limited.1",
                                         "an xs:all inside an xs:all must have minOccurs and maxOccurs 1");
          }
          else
            if (aMG.m_eCompositor == EXsdCompositor.ALL)
              throw XsdCompiler.invalid ("cos-all-limited.1", "an xs:all group must be the whole content model");
      }
      for (int i = aMG.m_aParticles.size () - 1; i >= 0; i--)
      {
        aStack.add (aMG.m_aParticles.get (i));
        aParents.add (aMG);
      }
    }
  }

  /**
   * go-xslt <code>checkAllLimited</code>: the extension half of
   * cos-all-limited.
   */
  private void _checkAllLimited (@NonNull final XsdComplexType aCT)
  {
    if (!XsdDerivation.EXTENSION.equals (aCT.m_sDerivation))
      return;
    if (!(aCT.m_aBaseType instanceof final XsdComplexType aBase))
      return;
    final XsdParticle aBaseP = m_aC.m_aSchema.effectiveParticle (aBase);
    if (isEmptyParticle (aBaseP) || isEmptyParticle (aCT.m_aParticle))
      return;
    final boolean bBaseAll = isAllParticle (aBaseP);
    final boolean bDerAll = isAllParticle (aCT.m_aParticle);
    if (m_aC.isXsd10 ())
    {
      if (bBaseAll || bDerAll)
        throw XsdCompiler.invalid ("cos-all-limited.1.2", "an xs:all group may not be extended with further content");
      return;
    }
    if (bBaseAll && bDerAll)
    {
      if (aBaseP.m_nMin != aCT.m_aParticle.m_nMin)
        throw XsdCompiler.invalid ("cos-all-limited", "an xs:all extending an xs:all must repeat its minOccurs");
    }
    else
      if (bBaseAll != bDerAll)
        throw XsdCompiler.invalid ("cos-all-limited.1.2", "an extension may not combine xs:all with another compositor");
  }

  /**
   * go-xslt <code>directBuiltinPrim</code>: the primitive of a direct reference
   * to a built-in atomic type (no user derivation, no facets), or
   * <code>null</code>.
   */
  @Nullable
  private static EAtomicType _directBuiltinPrim (@Nullable final XsdSimpleType aType)
  {
    if (aType == null ||
        !aType.m_bBaseBuiltin ||
        aType.m_aBase != null ||
        !aType.m_aName.local ().isEmpty () ||
        aType.m_eVariety != EXsdVariety.ATOMIC ||
        aType.m_aFacets.any ())
      return null;
    final EAtomicType p = xsdPrimitiveOf (aType.resolvePrim ());
    if (p == EAtomicType.ANY_ATOMIC_TYPE || p == EAtomicType.UNTYPED_ATOMIC)
      return null;
    return p;
  }

  /**
   * go-xslt <code>wcNameSubsetOK</code>: the QName half of Wildcard Subset
   * (1.1).
   */
  static boolean _wcNameSubsetOK (@NonNull final XsdWildcard aA, @NonNull final XsdWildcard aB)
  {
    if (aB.m_bNotDefined && !aA.m_bNotDefined)
      return false;
    for (final XsdName aName : aB.m_aNotNames)
      if (aA.matches (aName))
        return false;
    return true;
  }

  private static boolean _inexpressible (@NonNull final XsdWildcard aOther, @NonNull final XsdWildcard aList)
  {
    if (!XsdWildcard.NS_OTHER.equals (aOther.m_sNsMode) || !XsdWildcard.NS_LIST.equals (aList.m_sNsMode))
      return false;
    return aList.m_aNamespaces.contains ("") && !aList.m_aNamespaces.contains (aOther.m_sTargetNS);
  }

  /**
   * go-xslt <code>wcUnionExpressible10</code>: whether the union of two
   * attribute wildcards has an XSD 1.0 representation (errata E1-10).
   */
  private static boolean _wcUnionExpressible10 (@NonNull final XsdWildcard aA, @NonNull final XsdWildcard aB)
  {
    if (aA.m_aParts.isNotEmpty () || aB.m_aParts.isNotEmpty ())
      return true;
    return !_inexpressible (aA, aB) && !_inexpressible (aB, aA);
  }

  private static boolean _covers (@NonNull final XsdParticle q, @NonNull final XsdOpenContent aOwn)
  {
    return q.m_aTerm instanceof final XsdWildcard w &&
           q.m_nMax == XsdConstants.UNBOUNDED &&
           XsdRestrictionChecker.nsSubset (aOwn.m_aWildcard, w) &&
           _wcNameSubsetOK (aOwn.m_aWildcard, w) &&
           XsdWildcard.processRank (aOwn.m_aWildcard.m_sProcess) >= XsdWildcard.processRank (w.m_sProcess);
  }

  /**
   * go-xslt <code>baseAdmitsOpenWildcard</code>: whether a base without open
   * content already admits every child the derived type's open content lets
   * through (deliberately narrow).
   */
  private boolean _baseAdmitsOpenWildcard (@NonNull final XsdComplexType aBase, @Nullable final XsdOpenContent aOwn)
  {
    final XsdParticle p = m_aC.m_aSchema.effectiveParticle (aBase);
    if (p == null || aOwn == null || aOwn.m_aWildcard == null)
      return false;
    ICommonsList <XsdParticle> aItems = new CommonsArrayList <> (p);
    if (p.m_aTerm instanceof final XsdModelGroup aMG && p.m_nMax == 1 && aMG.m_eCompositor != EXsdCompositor.ALL)
      aItems = aMG.m_aParticles;
    if (aItems.isEmpty ())
      return false;
    if (XsdOpenContent.MODE_SUFFIX.equals (aOwn.m_sMode))
      return _covers (aItems.getLastOrNull (), aOwn);
    for (final XsdParticle q : aItems)
      if (!_covers (q, aOwn))
        return false;
    return true;
  }

  /**
   * go-xslt <code>checkOpenContentDerivation</code>: the {open content} halves
   * of derivation-ok-restriction / cos-ct-extends (1.1).
   */
  private void _checkOpenContentDerivation (@NonNull final XsdComplexType aCT)
  {
    if (!(aCT.m_aBaseType instanceof final XsdComplexType aBase))
      return;
    final XsdOpenContent aBaseEff = m_aC.m_aSchema.effectiveOpenContent (aBase);
    final XsdOpenContent aOwn = aCT.m_aOpenContent;
    if (aOwn == null || XsdOpenContent.MODE_NONE.equals (aOwn.m_sMode) || aOwn.m_aWildcard == null)
      return;
    if (XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation))
    {
      // With an empty derived content model no open-content clause applies
      if (isEmptyParticle (aCT.m_aParticle))
        return;
      if (aBaseEff == null || aBaseEff.m_aWildcard == null)
      {
        if (aBase.isAnyType () || _baseAdmitsOpenWildcard (aBase, aOwn))
          return;
        throw XsdCompiler.invalid ("derivation-ok-restriction",
                                   "a restriction cannot introduce open content over a closed base (" + aBase.m_aName + ")");
      }
      if (XsdOpenContent.MODE_SUFFIX.equals (aBaseEff.m_sMode) && XsdOpenContent.MODE_INTERLEAVE.equals (aOwn.m_sMode))
        throw XsdCompiler.invalid ("derivation-ok-restriction",
                                   "open content mode cannot loosen suffix to interleave in a restriction of " + aBase.m_aName);
      if (!XsdRestrictionChecker.nsSubset (aOwn.m_aWildcard, aBaseEff.m_aWildcard) ||
          !_wcNameSubsetOK (aOwn.m_aWildcard, aBaseEff.m_aWildcard) ||
          XsdWildcard.processRank (aOwn.m_aWildcard.m_sProcess) < XsdWildcard.processRank (aBaseEff.m_aWildcard.m_sProcess))
        throw XsdCompiler.invalid ("derivation-ok-restriction",
                                   "open-content wildcard is not a valid restriction of the base's (" + aBase.m_aName + ")");
    }
    else
      if (XsdDerivation.EXTENSION.equals (aCT.m_sDerivation))
      {
        if (aBaseEff == null || aBaseEff.m_aWildcard == null)
          return;
        if (XsdOpenContent.MODE_INTERLEAVE.equals (aBaseEff.m_sMode) && XsdOpenContent.MODE_SUFFIX.equals (aOwn.m_sMode))
          throw XsdCompiler.invalid ("cos-ct-extends",
                                     "open content mode cannot narrow interleave to suffix in an extension of " + aBase.m_aName);
      }
  }

  private static boolean _typeRestrictsFromDepth (@Nullable final IXsdType aDerived,
                                                  @Nullable final IXsdType aBase,
                                                  final int nDepth,
                                                  final boolean bRestrictionOnly)
  {
    if (nDepth > 32)
      return true;
    final XsdSimpleType aBS = aBase instanceof final XsdSimpleType x ? x : null;
    final XsdSimpleType aDS = aDerived instanceof final XsdSimpleType x ? x : null;
    if (aBS == null && aDS == null)
    {
      // Complex vs complex: the named derived chain must reach the named base
      if (!(aBase instanceof final XsdComplexType aBC) ||
          !(aDerived instanceof final XsdComplexType aDC) ||
          aBC.m_aName.isZero () ||
          aDC.m_aName.isZero ())
        return true;
      if (aBC.isAnyType ())
        return true;
      XsdComplexType aCur = aDC;
      for (int n = 0; aCur != null && n < MAX_CHAIN; n++)
      {
        if (aCur == aBC || aCur.m_aName.equals (aBC.m_aName))
          return true;
        if (bRestrictionOnly && XsdDerivation.EXTENSION.equals (aCur.m_sDerivation))
          return false;
        if (!(aCur.m_aBaseType instanceof final XsdComplexType aNext))
          return false;
        aCur = aNext;
      }
      return false;
    }
    if (aBS != null && aDS == null)
    {
      // Base simple, derived complex: the complex chain must reach a simple
      // type deriving from the base
      if (aBS.m_aName.isZero ())
        return true;
      IXsdType aCur = aDerived;
      for (int n = 0; n < MAX_CHAIN; n++)
      {
        if (aCur instanceof final XsdComplexType aCT)
        {
          if (aCT.m_aBaseType == null)
            return false;
          aCur = aCT.m_aBaseType;
        }
        else
          if (aCur instanceof final XsdSimpleType aST)
            return _typeRestrictsFromDepth (aST, aBS, nDepth + 1, bRestrictionOnly);
          else
            return true;
      }
      return true;
    }
    if (aBS == null)
    {
      // Base complex, derived simple: only anyType
      if (!(aBase instanceof final XsdComplexType aBC) || aBC.m_aName.isZero ())
        return true;
      return aBC.isAnyType ();
    }
    // Both simple: the base itself on the derived restriction chain
    XsdSimpleType s = aDS;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      if (s == aBS || !s.m_aName.isZero () && s.m_aName.equals (aBS.m_aName))
        return true;
      s = s.m_aBase;
    }
    // anySimpleType/anyAtomicType admits any simple type
    if (aBS.m_bBaseBuiltin && aBS.m_aBase == null && aBS.m_aName.isZero () && aBS.resolvePrim () == EAtomicType.ANY_ATOMIC_TYPE)
      return true;
    if (aBS.m_eVariety == EXsdVariety.UNION && aBS.m_aBase == null)
    {
      // Union member substitutability, only for a pristine union declaration
      for (final XsdSimpleType aMember : aBS.m_aMembers)
        if (_typeRestrictsFromDepth (aDS, aMember, nDepth + 1, bRestrictionOnly))
          return true;
      return aBS.m_aName.isZero () && aBS.m_aMembers.isEmpty ();
    }
    if (aDS.m_eVariety != aBS.m_eVariety)
      return false;
    if (aBS.m_eVariety != EXsdVariety.ATOMIC)
      return aBS.m_aName.isZero ();
    final EAtomicType eBP = aBS.resolvePrim ();
    if (eBP == EAtomicType.ANY_ATOMIC_TYPE)
      return true;
    // A user-defined base is reached only through the chain; the built-in
    // hierarchy applies only to a plain built-in reference
    if (!(aBS.m_bBaseBuiltin && aBS.m_aBase == null && aBS.m_aName.isZero () && !aBS.m_aFacets.any () && !aBS.m_bDeclared))
      return false;
    return XsdDerivation.atomDerivesFrom (aDS.resolvePrim (), eBP);
  }

  /** The attribute-use checks of a complex-content restriction against its base. */
  private void _checkRestrictionAttributes (@NonNull final XsdComplexType aCT, @NonNull final XsdComplexType aBase)
  {
    final XsdSchema aSchema = m_aC.m_aSchema;
    if (aCT.m_aAttrUses.isNotEmpty ())
    {
      final XsdEffectiveAttributes aBaseAttrs = aSchema.effectiveAttrs (aBase);
      final ICommonsMap <XsdName, XsdAttributeUse> aByName = new CommonsHashMap <> ();
      for (final XsdAttributeUse aBU : aBaseAttrs.uses ())
        aByName.put (aBU.m_aDecl.m_aName, aBU);
      for (final XsdAttributeUse aUse : aCT.m_aAttrUses)
      {
        final XsdAttributeUse aBU = aByName.get (aUse.m_aDecl.m_aName);
        if (aUse.m_bProhibited)
        {
          // derivation-ok-restriction.3: a required base use may not be removed
          if (aBU != null && aBU.m_bRequired)
            throw XsdCompiler.invalid ("derivation-ok-restriction.3",
                                       "required attribute " +
                                                                      aUse.m_aDecl.m_aName +
                                                                      " cannot be prohibited in a restriction of " +
                                                                      aBase.m_aName);
          continue;
        }
        if (aUse.m_aDecl.m_aType == null)
          continue;
        // derivation-ok-restriction 2.1.2: a redeclared use's type must be
        // validly derived from the base use's (provable cases only)
        if (aBU != null && aBU.m_aDecl.m_aType != null && !typeRestrictsFrom (aUse.m_aDecl.m_aType, aBU.m_aDecl.m_aType))
          throw XsdCompiler.invalid ("derivation-ok-restriction",
                                     "attribute " + aUse.m_aDecl.m_aName + ": type is not derived from its type in base " + aBase.m_aName);
        // 1.1: a restriction may not change {inheritable}
        if (aBU != null && m_aC.isXsd11 () && aBU.m_bInheritable != aUse.m_bInheritable)
          throw XsdCompiler.invalid ("derivation-ok-restriction",
                                     "attribute " + aUse.m_aDecl.m_aName + ": {inheritable} must not change in a restriction");
        // derivation-ok-restriction 2.2: a use with no corresponding base use
        // must be admitted by the base's attribute wildcard
        if (aBU == null)
        {
          final XsdWildcard aBW = aBaseAttrs.wildcard ();
          if (aBW == null || !aBW.matches (aUse.m_aDecl.m_aName))
            throw XsdCompiler.invalid ("derivation-ok-restriction.2.2",
                                       "attribute " + aUse.m_aDecl.m_aName + " is not admitted by the base type's attribute wildcard");
        }
      }
    }
    // The restriction's own attribute wildcard may only narrow the base's
    final XsdWildcard aRW = aCT.m_aAttrWildcard;
    if (aRW != null)
    {
      final XsdWildcard aBW = aSchema.effectiveAttrs (aBase).wildcard ();
      if (aBW == null ||
          !XsdRestrictionChecker.nsSubset (aRW, aBW) ||
          XsdWildcard.processRank (aRW.m_sProcess) < XsdWildcard.processRank (aBW.m_sProcess) ||
          !_wcNameSubsetOK (aRW, aBW))
        throw XsdCompiler.invalid ("", "attribute wildcard is not a valid restriction of base type " + aBase.m_aName);
    }
  }

  /**
   * go-xslt <code>fillWildcardSiblings</code>: resolve
   * <code>##definedSibling</code> for every wildcard of a content model (and
   * of its open content): the excluded names are the element declarations of
   * the model and their allowed substitutes.
   */
  private void _fillWildcardSiblings (@Nullable final XsdParticle aRoot, @Nullable final XsdOpenContent aOC)
  {
    final XsdSchema aSchema = m_aC.m_aSchema;
    final ICommonsList <XsdWildcard> aWCs = new CommonsArrayList <> ();
    final ICommonsList <XsdName> aNames = new CommonsArrayList <> ();
    if (aOC != null && aOC.m_aWildcard != null && aOC.m_aWildcard.m_bNotSibling)
      aWCs.add (aOC.m_aWildcard);
    if (aRoot != null)
    {
      final ICommonsSet <XsdModelGroup> aSeen = new CommonsHashSet <> ();
      final ICommonsList <XsdParticle> aStack = new CommonsArrayList <> ();
      aStack.add (aRoot);
      while (aStack.isNotEmpty ())
      {
        final XsdParticle q = aStack.removeLast ();
        if (q.m_aTerm instanceof final XsdElementDecl aDecl)
        {
          aNames.add (aDecl.m_aName);
          if (aSchema.m_aElements.get (aDecl.m_aName) == aDecl)
          {
            // A global reference admits its substitutes too
            final ICommonsSet <XsdName> aVisited = new CommonsHashSet <> ();
            final ICommonsList <XsdName> aHeads = new CommonsArrayList <> ();
            aHeads.add (aDecl.m_aName);
            while (aHeads.isNotEmpty ())
            {
              final XsdName aHead = aHeads.removeLast ();
              for (final XsdElementDecl aMember : aSchema.substMembersOf (aHead))
                if (aVisited.add (aMember.m_aName))
                {
                  if (aSchema.substitutionAllowed (aDecl, aMember))
                    aNames.add (aMember.m_aName);
                  aHeads.add (aMember.m_aName);
                }
            }
          }
        }
        else
          if (q.m_aTerm instanceof final XsdWildcard aWC)
          {
            if (aWC.m_bNotSibling)
              aWCs.add (aWC);
          }
          else
            if (q.m_aTerm instanceof final XsdModelGroup aMG && aSeen.add (aMG))
              _pushReversed (aStack, aMG.m_aParticles);
      }
    }
    for (final XsdWildcard aWC : aWCs)
    {
      aWC.m_aNotNames.addAll (aNames);
      // Filled once; a shared group keeps its first scope
      aWC.m_bNotSibling = false;
    }
  }

  /**
   * go-xslt <code>typeSignature</code>: a comparison key that treats two
   * references to the same built-in or named type as equal; an anonymous type
   * is its own key (identity).
   */
  @NonNull
  static Object typeSignature (@Nullable final IXsdType aType)
  {
    if (aType instanceof final XsdSimpleType aST)
    {
      if (aST.m_bBaseBuiltin && aST.m_aName.isZero () && !aST.m_aFacets.any ())
        return "b:" + (aST.m_ePrim == null ? "" : aST.m_ePrim.getLocalName ());
      if (!aST.m_aName.isZero ())
        return "s:" + aST.m_aName;
      return aST;
    }
    if (aType instanceof final XsdComplexType aCT)
    {
      if (!aCT.m_aName.isZero ())
        return "c:" + aCT.m_aName;
      return aCT;
    }
    return "?";
  }

  /**
   * go-xslt <code>checkElementConsistency</code>: two element particles with
   * the same expanded name in one content model must have the same type.
   */
  static void checkElementConsistency (@Nullable final XsdParticle aRoot)
  {
    if (aRoot == null)
      return;
    final ICommonsMap <XsdName, Object> aSigs = new CommonsHashMap <> ();
    final ICommonsSet <XsdModelGroup> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdParticle> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (aStack.isNotEmpty ())
    {
      final XsdParticle p = aStack.removeLast ();
      if (p.m_aTerm instanceof final XsdElementDecl aDecl)
      {
        final Object aSig = typeSignature (aDecl.m_aType);
        final Object aPrev = aSigs.get (aDecl.m_aName);
        if (aPrev != null && !aPrev.equals (aSig))
          throw XsdCompiler.invalid ("", "inconsistent element declarations for " + aDecl.m_aName + " in one content model");
        aSigs.put (aDecl.m_aName, aSig);
      }
      else
        if (p.m_aTerm instanceof final XsdModelGroup aMG && aSeen.add (aMG))
          _pushReversed (aStack, aMG.m_aParticles);
    }
  }

  /**
   * go-xslt <code>checkAttrGroupCycles</code>: named attribute groups may not
   * reference one another cyclically (XSD 1.0 only).
   */
  static void checkAttrGroupCycles (@NonNull final ICommonsList <XsdSchemaDocument> aDocs,
                                    @NonNull final EXsdVersion eVersion)
  {
    if (eVersion != EXsdVersion.V10)
      return;
    final ICommonsOrderedMap <XsdName, ICommonsList <XsdName>> aGraph = new CommonsLinkedHashMap <> ();
    for (final XsdSchemaDocument aSD : aDocs)
      for (final XdmNode aChild : XsdNodes.children (aSD.m_aElement))
      {
        if (!XsdNodes.isXS (aChild, "attributeGroup"))
          continue;
        final String sName = XsdNodes.attr (aChild, "name");
        if (sName == null)
          continue;
        final XsdName aSelf = new XsdName (aSD.m_sTNS, XsdWhiteSpace.collapse (sName));
        for (final XdmNode aSub : XsdNodes.children (aChild))
          if (XsdNodes.isXS (aSub, "attributeGroup"))
          {
            final String sRef = XsdNodes.attr (aSub, "ref");
            if (sRef != null)
              aGraph.computeIfAbsent (aSelf, k -> new CommonsArrayList <> ()).add (XsdNodes.resolveQName (aSub, sRef));
          }
      }
    // Iterative DFS with colours: 1 on the stack, 2 done
    final ICommonsMap <XsdName, Integer> aColour = new CommonsHashMap <> ();
    for (final XsdName aStart : aGraph.keySet ())
    {
      if (aColour.containsKey (aStart))
        continue;
      final ICommonsList <XsdName> aPath = new CommonsArrayList <> ();
      final ICommonsList <Integer> aNext = new CommonsArrayList <> ();
      aColour.put (aStart, Integer.valueOf (1));
      aPath.add (aStart);
      aNext.add (Integer.valueOf (0));
      while (aPath.isNotEmpty ())
      {
        final int nTop = aPath.size () - 1;
        final XsdName aCur = aPath.get (nTop);
        final ICommonsList <XsdName> aEdges = aGraph.get (aCur);
        final int nIndex = aNext.get (nTop).intValue ();
        if (aEdges == null || nIndex >= aEdges.size ())
        {
          aColour.put (aCur, Integer.valueOf (2));
          aPath.removeLast ();
          aNext.removeLast ();
          continue;
        }
        aNext.set (nTop, Integer.valueOf (nIndex + 1));
        final XsdName aTarget = aEdges.get (nIndex);
        final Integer aState = aColour.get (aTarget);
        if (aState != null && aState.intValue () == 1)
          throw XsdCompiler.invalid ("", "circular attributeGroup reference");
        if (aState == null)
        {
          aColour.put (aTarget, Integer.valueOf (1));
          aPath.add (aTarget);
          aNext.add (Integer.valueOf (0));
        }
      }
    }
  }

  /**
   * go-xslt <code>checkAttrDerivation</code>: in a restriction an attribute
   * carried over from the base keeps the base's fixed value, and a required
   * base attribute may not become optional.
   */
  void checkAttrDerivation ()
  {
    for (final IXsdType aType : m_aC.m_aSchema.m_aTypes.values ())
    {
      if (!(aType instanceof final XsdComplexType aCT) || !XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation))
        continue;
      if (!(aCT.m_aBaseType instanceof final XsdComplexType aBase))
        continue;
      final ICommonsMap <XsdName, XsdAttributeUse> aByName = new CommonsHashMap <> ();
      for (final XsdAttributeUse aUse : m_aC.m_aSchema.effectiveAttrs (aBase).uses ())
        aByName.put (aUse.m_aDecl.m_aName, aUse);
      for (final XsdAttributeUse aDU : aCT.m_aAttrUses)
      {
        final XsdAttributeUse aBU = aByName.get (aDU.m_aDecl.m_aName);
        if (aBU == null)
          continue;
        if (aBU.m_bHasFixed &&
            (!aDU.m_bHasFixed || !XsdWhiteSpace.collapse (aDU.m_sFixed).equals (XsdWhiteSpace.collapse (aBU.m_sFixed))))
          throw XsdCompiler.invalid ("", "attribute " + aDU.m_aDecl.m_aName.local () + " must keep the base fixed value");
        if (aBU.m_bRequired && !aDU.m_bRequired)
          throw XsdCompiler.invalid ("",
                                     "required attribute " + aDU.m_aDecl.m_aName.local () + " cannot be relaxed by restriction");
      }
    }
  }

  /**
   * go-xslt <code>checkFinalDerivation</code>: a complex type may not derive
   * from a base whose {final} forbids that method.
   */
  void checkFinalDerivation ()
  {
    for (final IXsdType aType : m_aC.m_aSchema.m_aTypes.values ())
    {
      if (!(aType instanceof final XsdComplexType aCT) || aCT.m_sDerivation.isEmpty ())
        continue;
      if (aCT.m_aBaseType instanceof final XsdComplexType aBase && aBase.m_aFinal.contains (aCT.m_sDerivation))
        throw XsdCompiler.invalid ("", "derivation by " + aCT.m_sDerivation + " from a final base type is not allowed");
    }
  }

  /**
   * go-xslt <code>checkDuplicateConstraints</code>: identity constraints are
   * unique by name across the schema, and a keyref may not refer to a keyref.
   */
  void checkDuplicateConstraints ()
  {
    final ICommonsMap <XsdName, String> aByName = new CommonsHashMap <> ();
    final ICommonsList <XsdIdentityConstraint> aKeyrefs = new CommonsArrayList <> ();
    final ICommonsSet <XsdElementDecl> aVisitedDecl = new CommonsHashSet <> ();
    final ICommonsSet <XsdModelGroup> aVisitedGroup = new CommonsHashSet <> ();
    for (final XsdElementDecl aGlobal : m_aC.m_aSchema.m_aElements.values ())
    {
      // Iterative walk over the declarations reachable through content models
      final ICommonsList <Object> aStack = new CommonsArrayList <> ();
      aStack.add (aGlobal);
      while (aStack.isNotEmpty ())
      {
        final Object aItem = aStack.removeLast ();
        if (aItem instanceof final XsdElementDecl aDecl)
        {
          if (!aVisitedDecl.add (aDecl))
            continue;
          for (final XsdIdentityConstraint aIC : aDecl.m_aConstraints)
          {
            if (aIC.m_bRefPending)
            {
              // An XSD 1.1 @ref use is not a re-declaration
              continue;
            }
            if (aByName.containsKey (aIC.m_aName))
              throw XsdCompiler.invalid ("", "duplicate identity constraint " + aIC.m_aName);
            aByName.put (aIC.m_aName, aIC.m_sKind);
            if ("keyref".equals (aIC.m_sKind))
              aKeyrefs.add (aIC);
          }
          if (aDecl.m_aType instanceof final XsdComplexType aCT && aCT.m_aParticle != null)
            aStack.add (aCT.m_aParticle);
        }
        else
          if (aItem instanceof final XsdParticle p)
          {
            if (p.m_aTerm instanceof final XsdElementDecl aDecl)
              aStack.add (aDecl);
            else
              if (p.m_aTerm instanceof final XsdModelGroup aMG && aVisitedGroup.add (aMG))
                for (int i = aMG.m_aParticles.size () - 1; i >= 0; i--)
                  aStack.add (aMG.m_aParticles.get (i));
          }
      }
    }
    // A keyref's @refer must name a key or unique; only the clear case of
    // another keyref is flagged here
    for (final XsdIdentityConstraint aKR : aKeyrefs)
      if ("keyref".equals (aByName.get (aKR.m_aRefer)))
        throw XsdCompiler.invalid ("", "keyref " + aKR.m_aName + " refers to " + aKR.m_aRefer + " which is itself a keyref");
  }

  /** go-xslt <code>isEmptyParticle</code>. */
  static boolean isEmptyParticle (@Nullable final XsdParticle aParticle)
  {
    if (aParticle == null)
      return true;
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
      return aMG.m_aParticles.isEmpty ();
    return false;
  }

  /** go-xslt <code>isAllParticle</code>. */
  static boolean isAllParticle (@Nullable final XsdParticle aParticle)
  {
    return aParticle != null &&
           aParticle.m_aTerm instanceof final XsdModelGroup aMG &&
           aMG.m_eCompositor == EXsdCompositor.ALL;
  }

  /**
   * go-xslt <code>particleEmptiable</code>: whether a particle can match the
   * empty sequence (depth-bounded, optimistic beyond the bound).
   */
  static boolean particleEmptiable (@Nullable final XsdParticle aParticle, final int nDepth)
  {
    if (aParticle == null || nDepth > 32)
      return true;
    if (aParticle.m_nMin == 0)
      return true;
    if (!(aParticle.m_aTerm instanceof final XsdModelGroup aMG))
      return false;
    if (aMG.m_aParticles.isEmpty ())
      return true;
    if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
    {
      for (final XsdParticle aSub : aMG.m_aParticles)
        if (particleEmptiable (aSub, nDepth + 1))
          return true;
      return false;
    }
    for (final XsdParticle aSub : aMG.m_aParticles)
      if (!particleEmptiable (aSub, nDepth + 1))
        return false;
    return true;
  }

  /**
   * go-xslt <code>xsdPrimitiveOf</code>: the XSD primitive at the root of a
   * built-in type's branch.
   */
  @NonNull
  static EAtomicType xsdPrimitiveOf (@NonNull final EAtomicType eType)
  {
    if (eType.isIntegerFamily ())
      return EAtomicType.DECIMAL;
    switch (eType)
    {
      case NORMALIZED_STRING:
      case TOKEN:
      case LANGUAGE:
      case NAME:
      case NCNAME:
      case ID:
      case IDREF:
      case ENTITY:
      case NMTOKEN:
        return EAtomicType.STRING;
      case DATE_TIME_STAMP:
        return EAtomicType.DATE_TIME;
      default:
        return eType;
    }
  }

  /**
   * go-xslt <code>typeRestrictsFrom</code>: whether derived is a valid
   * restriction of base. Conservative: true unless provably not.
   */
  static boolean typeRestrictsFrom (@Nullable final IXsdType aDerived, @Nullable final IXsdType aBase)
  {
    return _typeRestrictsFromDepth (aDerived, aBase, 0, false);
  }

  /**
   * go-xslt <code>typeRestrictsFromStrict</code>: {@link #typeRestrictsFrom}
   * where an extension step anywhere in the complex chain disqualifies
   * (rcase-NameAndTypeOK).
   */
  static boolean typeRestrictsFromStrict (@Nullable final IXsdType aDerived, @Nullable final IXsdType aBase)
  {
    return _typeRestrictsFromDepth (aDerived, aBase, 0, true);
  }

  /**
   * go-xslt <code>checkAllRestrictions</code>: the per-type derivation checks
   * over every complex type, named and anonymous.
   */
  void checkAllRestrictions ()
  {
    final XsdSchema aSchema = m_aC.m_aSchema;
    for (final XsdComplexType aCT : m_aC.m_aAllCTs)
    {
      _checkAllLimited (aCT);
      _checkAllPlacement (aCT);
      _checkContentBaseKind (aCT);
      // Open-content derivation validity (1.1)
      if (m_aC.isXsd11 ())
        _checkOpenContentDerivation (aCT);
      // EDC must also hold on the post-extension content model
      if (XsdDerivation.EXTENSION.equals (aCT.m_sDerivation))
        checkElementConsistency (aSchema.effectiveParticle (aCT));
      // 1.1 EDC additions over the effective model
      if (m_aC.isXsd11 ())
        _checkElementConsistency11 (aSchema.effectiveParticle (aCT));
      // XSD 1.0 Attribute Wildcard Union expressibility (errata E1-10)
      if (m_aC.isXsd10 () &&
          XsdDerivation.EXTENSION.equals (aCT.m_sDerivation) &&
          aCT.m_aAttrWildcard != null &&
          aCT.m_aBaseType instanceof final XsdComplexType aExtBase)
      {
        final XsdWildcard aBW = aSchema.effectiveAttrs (aExtBase).wildcard ();
        if (aBW != null && !_wcUnionExpressible10 (aCT.m_aAttrWildcard, aBW))
          throw XsdCompiler.invalid ("src-ct",
                                     "the attribute wildcard union of " + aCT.m_aName + " and its base is not expressible in XSD 1.0");
      }
      if (!XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation))
        continue;
      if (!(aCT.m_aBaseType instanceof final XsdComplexType aBase))
        continue;
      final XsdParticle aBaseP = aSchema.effectiveParticle (aBase);
      final XsdRestrictionChecker aRC = new XsdRestrictionChecker (aSchema);
      if (!aRC.particleRestrictsOK (aCT.m_aParticle, aBaseP))
        throw XsdCompiler.invalid ("", "content model is not a valid restriction of base type " + aBase.m_aName);
      _checkRestrictionAttributes (aCT, aBase);
    }
  }

  /**
   * go-xslt <code>checkGroupCycles</code>: a model group may not contain
   * itself, directly or transitively.
   */
  void checkGroupCycles ()
  {
    final ICommonsSet <XsdModelGroup> aDone = new CommonsHashSet <> ();
    for (final XsdModelGroup aStart : m_aC.m_aGroups.values ())
    {
      if (aDone.contains (aStart))
        continue;
      // Iterative DFS: the groups on the current path
      final ICommonsSet <XsdModelGroup> aInPath = new CommonsHashSet <> ();
      final ICommonsList <XsdModelGroup> aPath = new CommonsArrayList <> ();
      final ICommonsList <Integer> aNext = new CommonsArrayList <> ();
      aInPath.add (aStart);
      aPath.add (aStart);
      aNext.add (Integer.valueOf (0));
      while (aPath.isNotEmpty ())
      {
        final int nTop = aPath.size () - 1;
        final XsdModelGroup aCur = aPath.get (nTop);
        final int nIndex = aNext.get (nTop).intValue ();
        if (nIndex >= aCur.m_aParticles.size ())
        {
          aInPath.remove (aCur);
          aDone.add (aCur);
          aPath.removeLast ();
          aNext.removeLast ();
          continue;
        }
        aNext.set (nTop, Integer.valueOf (nIndex + 1));
        if (!(aCur.m_aParticles.get (nIndex).m_aTerm instanceof final XsdModelGroup aSub))
          continue;
        if (aInPath.contains (aSub))
          throw XsdCompiler.invalid ("", "circular group reference");
        if (aDone.contains (aSub))
          continue;
        aInPath.add (aSub);
        aPath.add (aSub);
        aNext.add (Integer.valueOf (0));
      }
    }
  }

  /**
   * go-xslt <code>fillAllWildcardSiblings</code>: {@link #_fillWildcardSiblings}
   * over every complex type, once substitution groups are known.
   */
  void fillAllWildcardSiblings ()
  {
    for (final XsdComplexType aCT : m_aC.m_aAllCTs)
      _fillWildcardSiblings (aCT.m_aParticle, aCT.m_aOpenContent);
  }

  /**
   * go-xslt <code>checkSubstitutionGroups</code>: a member's type must be
   * validly derived from the head's (provably impossible cases only), and the
   * head's {final} bars a member reaching the head's type by an excluded
   * method.
   */
  void checkSubstitutionGroups ()
  {
    final XsdSchema aSchema = m_aC.m_aSchema;
    for (final var aEntry : aSchema.m_aSubstMembers.entrySet ())
    {
      final XsdName aHeadName = aEntry.getKey ();
      final XsdElementDecl aHead = aSchema.m_aElements.get (aHeadName);
      if (aHead == null || aHead.m_aType == null)
        continue;
      for (final XsdElementDecl aMember : aEntry.getValue ())
      {
        if (aMember.m_aType == null || aMember.m_aType == aHead.m_aType)
          continue;
        if (aMember.m_aType instanceof final XsdSimpleType aST)
        {
          if (aHead.m_aType instanceof final XsdSimpleType aHT)
          {
            final boolean bAtomicHead = aHT.m_eVariety == EXsdVariety.ATOMIC &&
                                        aHT.resolvePrim () != EAtomicType.ANY_ATOMIC_TYPE;
            if ((aST.m_eVariety == EXsdVariety.UNION || aST.m_eVariety == EXsdVariety.LIST) && bAtomicHead ||
                aHT.m_eVariety == EXsdVariety.LIST && aST.m_eVariety != EXsdVariety.LIST)
              throw XsdCompiler.invalid ("e-props-correct.4",
                                         "element " +
                                                             aMember.m_aName +
                                                             ": type is not derived from the type of substitution-group head " +
                                                             aHeadName);
            // Two direct built-in atomic types with different primitives can
            // never stand in a derivation chain
            final EAtomicType eSP = _directBuiltinPrim (aST);
            final EAtomicType eHP = _directBuiltinPrim (aHT);
            if (eSP != null && eHP != null && eSP != eHP)
              throw XsdCompiler.invalid ("e-props-correct.4",
                                         "element " +
                                                             aMember.m_aName +
                                                             ": type is not derived from the type of substitution-group head " +
                                                             aHeadName);
          }
          if (aHead.m_aType instanceof final XsdComplexType aHC && !aHC.m_aName.isZero () && !aHC.isAnyType ())
            throw XsdCompiler.invalid ("e-props-correct.4",
                                       "element " + aMember.m_aName + ": a simple type cannot derive from complex head type of " + aHeadName);
        }
        if (aMember.m_aType instanceof final XsdComplexType aMC &&
            aMC.m_eKind != EXsdContentKind.SIMPLE &&
            aHead.m_aType instanceof XsdSimpleType)
          throw XsdCompiler.invalid ("e-props-correct.4",
                                     "element " + aMember.m_aName + ": a complex type cannot derive from the simple head type of " + aHeadName);
        for (final String sMethod : XsdDerivation.derivationMethodsTo (aMember.m_aType, aHead.m_aType))
          if (aHead.m_aFinal.contains (sMethod))
            throw XsdCompiler.invalid ("cos-equiv-derived-ok-rec",
                                       "element " +
                                                                     aMember.m_aName +
                                                                     " may not join the substitution group of " +
                                                                     aHeadName +
                                                                     ": " +
                                                                     sMethod +
                                                                     " is final there");
      }
    }
  }
}
