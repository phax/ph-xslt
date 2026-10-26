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

import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;

/**
 * Particle Valid (Restriction), XSD 1.0 section 3.9.6 and 1.1 section 3.9.6
 * (go-xslt <code>restriction.go</code>). When a complex type derives another
 * by restriction, the derived content model must be a valid restriction of the
 * base's: an order and structure preserving mapping where each derived particle
 * restricts a corresponding base particle and every unmapped base particle is
 * emptiable.
 * <p>
 * {@link #particleRestrictsOK} reports whether a derived particle is a valid
 * restriction of a base particle. Where a case is genuinely undecidable with
 * the information modelled (unresolved types, exotic wildcard namespace
 * algebra) it errs toward "valid", so it never rejects a schema it cannot prove
 * invalid. The checker carries the schema through the recursion so element
 * comparisons can consult substitution groups and their blocking rules.
 * </p>
 *
 * @author Philip Helger
 */
final class XsdRestrictionChecker
{
  /** Element count range of a particle; <code>max</code> is -1 if unbounded. */
  private record Bounds (long min, long max)
  {}

  /** Summed occurrences of derived particles mapped onto one base particle. */
  private static final class Acc
  {
    long m_nLo;
    long m_nHi;
    boolean m_bUnbounded;
  }

  private final XsdSchema m_aSchema;
  /**
   * The comparison sits inside a MapAndSum mapping (sequence restricting a
   * choice): XSD 1.0 grants no substitution-group equivalence there
   * (particlesZ028 is invalid in 1.0, valid in 1.1).
   */
  private boolean m_bInMapAndSum;
  private int m_nDepth;

  XsdRestrictionChecker (@NonNull final XsdSchema aSchema)
  {
    this (aSchema, false);
  }

  XsdRestrictionChecker (@NonNull final XsdSchema aSchema, final boolean bInMapAndSum)
  {
    m_aSchema = aSchema;
    m_bInMapAndSum = bInMapAndSum;
  }

  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }

  boolean isInMapAndSum ()
  {
    return m_bInMapAndSum;
  }

  private boolean _is11 ()
  {
    return m_aSchema.m_eVersion == EXsdVersion.V11;
  }

  /**
   * go-xslt <code>normalizeParticle</code>: collapse "pointless" particles. A
   * model group occurring exactly once with a single child particle is
   * equivalent to that child (this is how a group reference wrapping one element
   * compares as the element itself).
   */
  @Nullable
  private static XsdParticle _normalizeParticle (@Nullable final XsdParticle aParticle)
  {
    XsdParticle aCur = aParticle;
    for (int n = 0; aCur != null && aCur.m_nMin == 1 && aCur.m_nMax == 1; n++)
    {
      XsdModelLimits.checkDepth (n);
      if (!(aCur.m_aTerm instanceof final XsdModelGroup aMG) || aMG.m_aParticles.size () != 1)
        return aCur;
      aCur = aMG.m_aParticles.get (0);
    }
    return aCur;
  }

  /**
   * go-xslt <code>flattenSeq</code>: splice a pointless sequence wrapper into
   * its parent (XSD 1.1 section 3.9.6.3). This is exactly the shape
   * xs:complexContent/xs:extension produces: sequence(BASE-CONTENT,
   * EXTENSION-CONTENT) where BASE-CONTENT is itself a 1..1 sequence. A derived
   * type that restricts such an extension writes the same content flat.
   * Language preserving by construction, so using it only as an extra acceptance
   * route cannot approve a model the clause tables would reject for any other
   * reason.
   */
  @Nullable
  private static XsdParticle _flattenSeq (@Nullable final XsdParticle aParticle)
  {
    XsdParticle aCur = aParticle;
    for (int n = 0; aCur != null; n++)
    {
      XsdModelLimits.checkDepth (n);
      if (!(aCur.m_aTerm instanceof final XsdModelGroup aMG) || aMG.m_eCompositor != EXsdCompositor.SEQUENCE)
        return aCur;
      final ICommonsList <XsdParticle> aOut = new CommonsArrayList <> ();
      boolean bChanged = false;
      for (final XsdParticle aChild : aMG.m_aParticles)
      {
        if (aChild != null &&
            aChild.m_nMin == 1 &&
            aChild.m_nMax == 1 &&
            aChild.m_aTerm instanceof final XsdModelGroup aChildMG &&
            aChildMG.m_eCompositor == EXsdCompositor.SEQUENCE)
        {
          aOut.addAll (aChildMG.m_aParticles);
          bChanged = true;
        }
        else
          aOut.add (aChild);
      }
      if (!bChanged)
        return aCur;
      final XsdModelGroup aNewMG = aMG.getShallowCopy ();
      aNewMG.m_aParticles = aOut;
      // Repeat: splicing may expose a further nested 1..1 sequence
      aCur = new XsdParticle (aCur.m_nMin, aCur.m_nMax, aNewMG);
    }
    return null;
  }

  /** go-xslt <code>occursOK</code>: R's occurrence range is within B's. */
  private static boolean _occursOK (@NonNull final XsdParticle aR, @NonNull final XsdParticle aB)
  {
    if (aR.m_nMin < aB.m_nMin)
      return false;
    if (aB.m_nMax == XsdConstants.UNBOUNDED)
      return true;
    if (aR.m_nMax == XsdConstants.UNBOUNDED)
      return false;
    return aR.m_nMax <= aB.m_nMax;
  }

  /**
   * go-xslt <code>sumOccursOK</code>: the MapAndSum occurrence-sum step. The
   * derived sequence's total range (occurs multiplied by its particle count)
   * must be within the base choice particle's range. The factor is the number of
   * particles (section 3.9.6 MapAndSum 2), verified against particlesV001-V020.
   */
  private static boolean _sumOccursOK (@NonNull final XsdParticle aR,
                                       @NonNull final XsdModelGroup aRMG,
                                       @NonNull final XsdParticle aB)
  {
    final long nCount = aRMG.m_aParticles.size ();
    if (XsdModelLimits.mul (aR.m_nMin, nCount) < aB.m_nMin)
      return false;
    if (aB.m_nMax == XsdConstants.UNBOUNDED)
      return true;
    if (aR.m_nMax == XsdConstants.UNBOUNDED)
      return false;
    return XsdModelLimits.mul (aR.m_nMax, nCount) <= aB.m_nMax;
  }

  private static boolean _emptiable (@Nullable final XsdParticle aParticle)
  {
    return XsdUpa.upaNullable (aParticle);
  }

  private static boolean _matchesOnlyEmpty (@Nullable final XsdParticle aParticle, final int nDepth)
  {
    if (aParticle == null || aParticle.m_nMax == 0)
      return true;
    // An element or wildcard contributes content
    if (!(aParticle.m_aTerm instanceof final XsdModelGroup aMG))
      return false;
    XsdModelLimits.checkDepth (nDepth);
    for (final XsdParticle aSub : aMG.m_aParticles)
      if (!_matchesOnlyEmpty (aSub, nDepth + 1))
        return false;
    return true;
  }

  /**
   * go-xslt <code>matchesOnlyEmpty</code>: whether a particle can match nothing
   * but the empty sequence, i.e. contributes no element content at all. An empty
   * model group (an empty sequence) qualifies; any element or wildcard that can
   * occur does not.
   *
   * @param aParticle
   *        The particle; <code>null</code> counts as empty.
   * @return <code>true</code> if it matches only empty.
   */
  static boolean matchesOnlyEmpty (@Nullable final XsdParticle aParticle)
  {
    return _matchesOnlyEmpty (aParticle, 0);
  }

  @NonNull
  private static Bounds _elementBounds (@NonNull final XsdParticle aParticle, final int nDepth)
  {
    // An element or wildcard contributes min..max elements
    if (!(aParticle.m_aTerm instanceof final XsdModelGroup aMG))
      return new Bounds (aParticle.m_nMin, aParticle.m_nMax);
    XsdModelLimits.checkDepth (nDepth);
    long nChildMin = 0;
    long nChildMax = 0;
    if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
    {
      nChildMin = -1;
      for (final XsdParticle aSub : aMG.m_aParticles)
      {
        final Bounds aSubBounds = _elementBounds (aSub, nDepth + 1);
        if (nChildMin < 0 || aSubBounds.min () < nChildMin)
          nChildMin = aSubBounds.min ();
        if (aSubBounds.max () == XsdConstants.UNBOUNDED)
          nChildMax = XsdConstants.UNBOUNDED;
        else
          if (nChildMax != XsdConstants.UNBOUNDED && aSubBounds.max () > nChildMax)
            nChildMax = aSubBounds.max ();
      }
      if (nChildMin < 0)
        nChildMin = 0;
    }
    else
    {
      // Sequence or all
      for (final XsdParticle aSub : aMG.m_aParticles)
      {
        final Bounds aSubBounds = _elementBounds (aSub, nDepth + 1);
        nChildMin = XsdModelLimits.add (nChildMin, aSubBounds.min ());
        if (aSubBounds.max () == XsdConstants.UNBOUNDED || nChildMax == XsdConstants.UNBOUNDED)
          nChildMax = XsdConstants.UNBOUNDED;
        else
          nChildMax = XsdModelLimits.add (nChildMax, aSubBounds.max ());
      }
    }
    final long nMin = XsdModelLimits.mul (nChildMin, aParticle.m_nMin);
    final long nMax = aParticle.m_nMax == XsdConstants.UNBOUNDED || nChildMax == XsdConstants.UNBOUNDED ? XsdConstants.UNBOUNDED
                                                                                                         : XsdModelLimits.mul (nChildMax,
                                                                                                                               aParticle.m_nMax);
    return new Bounds (nMin, nMax);
  }

  /**
   * go-xslt <code>nsSubset</code>: whether wildcard a's namespace constraint is
   * a subset of b's. Conservative: <code>true</code> unless it can show that a
   * admits a namespace b forbids.
   *
   * @param aA
   *        The derived wildcard.
   * @param aB
   *        The base wildcard.
   * @return <code>true</code> unless a admits a namespace b forbids.
   */
  static boolean nsSubset (@NonNull final XsdWildcard aA, @NonNull final XsdWildcard aB)
  {
    // XSD 1.1 negated constraints ("everything but these namespaces"): a larger
    // exclusion list is the smaller set, so the subset direction inverts
    if (aB.m_aNotNS.isNotEmpty ())
    {
      switch (aA.m_sNsMode)
      {
        case XsdWildcard.NS_LIST:
          for (final String sNS : aA.m_aNamespaces)
            if (aB.m_aNotNS.contains (sNS))
              return false;
          return true;
        case XsdWildcard.NS_OTHER:
          // a admits everything but its target namespace and the absent one
          for (final String sNS : aB.m_aNotNS)
            if (!sNS.equals (aA.m_sTargetNS) && !sNS.isEmpty ())
              return false;
          return true;
        default:
          // "any", possibly itself negated
          for (final String sNS : aB.m_aNotNS)
            if (!aA.m_aNotNS.contains (sNS))
              return false;
          return true;
      }
    }
    if (aA.m_aNotNS.isNotEmpty ())
    {
      switch (aB.m_sNsMode)
      {
        case XsdWildcard.NS_ANY:
          return true;
        case XsdWildcard.NS_OTHER:
          return aA.m_aNotNS.contains (aB.m_sTargetNS) && aA.m_aNotNS.contains ("");
        default:
          // a is infinite, b is a finite list
          return false;
      }
    }
    switch (aB.m_sNsMode)
    {
      case XsdWildcard.NS_ANY:
        return true;
      case XsdWildcard.NS_OTHER:
        // b admits anything except its targetNS (and absent)
        switch (aA.m_sNsMode)
        {
          case XsdWildcard.NS_ANY:
            return false;
          case XsdWildcard.NS_OTHER:
            return aA.m_sTargetNS.equals (aB.m_sTargetNS);
          case XsdWildcard.NS_LIST:
            for (final String sNS : aA.m_aNamespaces)
              if (sNS.equals (aB.m_sTargetNS) || sNS.isEmpty ())
                return false;
            return true;
          default:
            break;
        }
        break;
      case XsdWildcard.NS_LIST:
        if (!XsdWildcard.NS_LIST.equals (aA.m_sNsMode))
          return false;
        for (final String sNS : aA.m_aNamespaces)
          if (!aB.m_aNamespaces.contains (sNS))
            return false;
        return true;
      default:
        break;
    }
    return true;
  }

  private static boolean _leavesMatchWildcard (@Nullable final XsdParticle aParticle,
                                               @NonNull final XsdWildcard aWC,
                                               @NonNull final Set <XsdModelGroup> aSeen,
                                               final int nDepth)
  {
    if (aParticle == null)
      return true;
    if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
      return aWC.matches (aDecl.m_aName);
    if (aParticle.m_aTerm instanceof final XsdWildcard aLeaf)
    {
      // A restricting wildcard may neither widen the namespace set nor weaken
      // processContents (wildZ008/009), nor drop the base's notQName
      // exclusions (wild050)
      return nsSubset (aLeaf, aWC) &&
             XsdWildcard.processRank (aLeaf.m_sProcess) >= XsdWildcard.processRank (aWC.m_sProcess) &&
             XsdComponentChecks._wcNameSubsetOK (aLeaf, aWC);
    }
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
    {
      XsdModelLimits.checkDepth (nDepth);
      if (!aSeen.add (aMG))
        return true;
      for (final XsdParticle aSub : aMG.m_aParticles)
        if (!_leavesMatchWildcard (aSub, aWC, aSeen, nDepth + 1))
          return false;
    }
    return true;
  }

  /**
   * go-xslt <code>leavesMatchWildcard</code>: whether every element and
   * wildcard leaf of a particle is admitted by a wildcard.
   */
  private static boolean _leavesMatchWildcard (@NonNull final XsdParticle aParticle, @NonNull final XsdWildcard aWC)
  {
    return _leavesMatchWildcard (aParticle, aWC, new CommonsHashSet <> (), 0);
  }

  /**
   * go-xslt <code>coveredByWildcardUnion</code>: whether a derived wildcard
   * particle is admitted by the combination of the base's wildcard members. No
   * single base particle subsumes it then, but the base may still accept
   * everything it does, e.g. an xs:all carrying one ##local and one
   * notNamespace="##local" wildcard, restricted to a single ##any wildcard. The
   * subset test is decided over a finite witness alphabet: wildcard admission
   * depends only on which of the mentioned namespaces a name's namespace is and
   * whether the exact QName sits on a notQName list, so every mentioned
   * namespace plus the absent one plus one fresh namespace, crossed with every
   * mentioned local name plus one fresh one, covers all equivalence classes
   * (wild048).
   */
  private static boolean _coveredByWildcardUnion (@NonNull final XsdParticle aRP,
                                                  @NonNull final ICommonsList <XsdParticle> aBs)
  {
    if (!(aRP.m_aTerm instanceof final XsdWildcard aRW))
      return false;
    final ICommonsList <XsdWildcard> aWs = new CommonsArrayList <> ();
    for (final XsdParticle aBP : aBs)
      if (aBP.m_aTerm instanceof final XsdWildcard aW)
        aWs.add (aW);
    if (aWs.size () < 2)
      return false;
    final ICommonsSet <String> aNsSet = new CommonsHashSet <> ();
    aNsSet.add ("");
    aNsSet.add ("\u0001fresh-ns");
    final ICommonsSet <String> aLocalSet = new CommonsHashSet <> ();
    aLocalSet.add ("\u0001fresh");
    final ICommonsList <XsdWildcard> aToCollect = new CommonsArrayList <> ();
    aToCollect.add (aRW);
    aToCollect.addAll (aWs);
    while (aToCollect.isNotEmpty ())
    {
      final XsdWildcard aW = aToCollect.removeLast ();
      aToCollect.addAll (aW.m_aParts);
      aNsSet.addAll (aW.m_aNamespaces);
      if (XsdWildcard.NS_OTHER.equals (aW.m_sNsMode))
        aNsSet.add (aW.m_sTargetNS);
      aNsSet.addAll (aW.m_aNotNS);
      for (final XsdName aName : aW.m_aNotNames)
      {
        aNsSet.add (aName.space ());
        aLocalSet.add (aName.local ());
      }
      for (final XsdName aName : aW.m_aNotDefNames)
      {
        aNsSet.add (aName.space ());
        aLocalSet.add (aName.local ());
      }
    }
    for (final String sNS : aNsSet)
      for (final String sLocal : aLocalSet)
      {
        final XsdName aName = new XsdName (sNS, sLocal);
        if (!aRW.matches (aName))
          continue;
        boolean bAdmitted = false;
        for (final XsdWildcard aW : aWs)
          if (aW.matches (aName))
          {
            bAdmitted = true;
            break;
          }
        if (!bAdmitted)
          return false;
      }
    return true;
  }

  /**
   * go-xslt <code>elementRestrictsElement</code>: the NameAndTypeOK clauses
   * that concern the two element declarations themselves (nillable, blocking,
   * fixed values, type derivation); name and occurrence are the caller's
   * business.
   */
  private boolean _elementRestrictsElement (@NonNull final XsdElementDecl aRE, @NonNull final XsdElementDecl aBT)
  {
    // NameAndTypeOK 2: nillable may only narrow (true to false)
    if (aRE.m_bNillable && !aBT.m_bNillable)
      return false;
    // NameAndTypeOK 5: disallowed substitutions must not shrink
    for (final String sBlocked : aBT.m_aBlocked)
      if (!aRE.m_aBlocked.contains (sBlocked))
        return false;
    if (aBT.m_bHasFixed)
    {
      // NameAndTypeOK 4: a base fixed value constraint must be preserved with
      // the same value (compared in the value space when possible)
      if (!aRE.m_bHasFixed)
        return false;
      if (!XsdLexHelper.lexEqualCollapsed (aRE.m_sFixed, aBT.m_sFixed))
      {
        final XsdSimpleType aST = XsdComponentParser.simpleContentType (aBT.m_aType);
        if (aST == null || !XsdSimpleTypeValidator.fixedEqual (aST, aRE.m_sFixed, aBT.m_sFixed))
          return false;
      }
    }
    // 1.1 NameAndTypeOK also demands the {type table}s be equivalent (bug 12185,
    // cta0043): same alternative count, pairwise identical test text and type
    // (the trailing default alternative compares as the empty test)
    if (_is11 ())
    {
      if (aRE.m_aAlternatives.size () != aBT.m_aAlternatives.size ())
        return false;
      for (int i = 0; i < aRE.m_aAlternatives.size (); i++)
      {
        final XsdTypeAlternative aA = aRE.m_aAlternatives.get (i);
        final XsdTypeAlternative aB = aBT.m_aAlternatives.get (i);
        if (!aA.m_sRawTest.equals (aB.m_sRawTest) || aA.m_aType != aB.m_aType)
          return false;
      }
    }
    return XsdComponentChecks.typeRestrictsFromStrict (aRE.m_aType, aBT.m_aType);
  }

  /**
   * go-xslt <code>substitutableFor</code>: whether derived element re may stand
   * in for base element bt through bt's substitution group (transitive,
   * honouring block and abstract). In XSD 1.0 this equivalence is not granted
   * inside a MapAndSum mapping (particlesZ028).
   */
  private boolean _substitutableFor (@NonNull final XsdElementDecl aRE, @NonNull final XsdElementDecl aBT)
  {
    if (m_bInMapAndSum && m_aSchema.m_eVersion == EXsdVersion.V10)
      return false;
    return m_aSchema.matchesElementName (aBT, aRE.m_aName);
  }

  private boolean _leavesSubstitutableFor (@Nullable final XsdParticle aParticle,
                                           @NonNull final XsdElementDecl aBT,
                                           @NonNull final Set <XsdModelGroup> aSeen,
                                           final int nDepth)
  {
    if (aParticle == null || aParticle.m_nMax == 0)
      return true;
    if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
    {
      if (aDecl.m_aName.equals (aBT.m_aName))
      {
        // The base element itself as a leaf: the 1.0 case table holds group:elt
        // forbidden unless the group is the head's substitution expansion
        // (particlesHb011 invalid, elemZ027a valid); 1.1 subsumption admits it
        if (!_is11 ())
          return false;
        return XsdComponentChecks.typeRestrictsFrom (aDecl.m_aType, aBT.m_aType);
      }
      return _substitutableFor (aDecl, aBT) && XsdComponentChecks.typeRestrictsFrom (aDecl.m_aType, aBT.m_aType);
    }
    if (aParticle.m_aTerm instanceof XsdWildcard)
      return false;
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
    {
      XsdModelLimits.checkDepth (nDepth);
      if (!aSeen.add (aMG))
        return true;
      for (final XsdParticle aSub : aMG.m_aParticles)
        if (!_leavesSubstitutableFor (aSub, aBT, aSeen, nDepth + 1))
          return false;
    }
    return true;
  }

  /**
   * go-xslt <code>leavesSubstitutableFor</code>: whether every element leaf of
   * r is the base element itself or substitutable for it (wildcard leaves
   * disqualify).
   */
  private boolean _leavesSubstitutableFor (@NonNull final XsdParticle aR, @NonNull final XsdElementDecl aBT)
  {
    return _leavesSubstitutableFor (aR, aBT, new CommonsHashSet <> (), 0);
  }

  /**
   * go-xslt <code>choiceRunFits</code>: the end index of the shortest run
   * bs[bi:end] of base particles such that every branch of the derived choice
   * restricts the run as a 1..1 sequence, or 0 when no run works.
   */
  private int _choiceRunFits (@NonNull final XsdModelGroup aMG,
                              @NonNull final ICommonsList <XsdParticle> aBs,
                              final int nBI)
  {
    for (int nEnd = nBI + 1; nEnd <= aBs.size (); nEnd++)
    {
      final XsdModelGroup aRunGroup = new XsdModelGroup (EXsdCompositor.SEQUENCE);
      aRunGroup.m_aParticles = new CommonsArrayList <> (aBs.subList (nBI, nEnd));
      final XsdParticle aRun = new XsdParticle (1, 1, aRunGroup);
      boolean bOK = true;
      for (final XsdParticle aBranch : aMG.m_aParticles)
        if (!particleRestrictsOK (aBranch, aRun))
        {
          bOK = false;
          break;
        }
      if (bOK)
        return nEnd;
    }
    return 0;
  }

  /**
   * go-xslt <code>recurseSeq</code>: an order-preserving one-to-one mapping of
   * R's particles onto B's, skipping only emptiable base particles.
   */
  private boolean _recurseSeq (@NonNull final ICommonsList <XsdParticle> aRs,
                               @NonNull final ICommonsList <XsdParticle> aBs)
  {
    int nBI = 0;
    for (final XsdParticle aRP : aRs)
    {
      // A removed particle (maxOccurs=0) contributes nothing
      if (matchesOnlyEmpty (aRP))
        continue;
      boolean bMatched = false;
      while (nBI < aBs.size ())
      {
        if (particleRestrictsOK (aRP, aBs.get (nBI)))
        {
          nBI++;
          bMatched = true;
          break;
        }
        // 1.1 subsumption: a 1..1 choice may cover a contiguous run of base
        // particles when every branch restricts the run-as-sequence on its own;
        // only one branch materialises, and the members it leaves unused must
        // be emptiable, which the per-branch sequence check enforces
        // (particlesHb008)
        if (_is11 () &&
            aRP.m_aTerm instanceof final XsdModelGroup aMG &&
            aMG.m_eCompositor == EXsdCompositor.CHOICE &&
            aRP.m_nMin == 1 &&
            aRP.m_nMax == 1)
        {
          final int nEnd = _choiceRunFits (aMG, aBs, nBI);
          if (nEnd > nBI)
          {
            nBI = nEnd;
            bMatched = true;
            break;
          }
        }
        if (!_emptiable (aBs.get (nBI)))
          return false;
        nBI++;
      }
      if (!bMatched)
        return false;
    }
    for (; nBI < aBs.size (); nBI++)
      if (!_emptiable (aBs.get (nBI)))
        return false;
    return true;
  }

  /**
   * go-xslt <code>recurseSeqLenient</code>: a fallback tried only after the
   * strict one-to-one mapping fails. It lets a repeatable base particle (e.g. a
   * wildcard with minOccurs=2 maxOccurs=3) absorb several consecutive derived
   * particles. Because it runs only when the strict mapping already rejected, it
   * can only accept more.
   */
  private boolean _recurseSeqLenient (@NonNull final ICommonsList <XsdParticle> aRs,
                                      @NonNull final ICommonsList <XsdParticle> aBs)
  {
    int nRI = 0;
    for (final XsdParticle aBP : aBs)
    {
      if (aBP.m_aTerm instanceof final XsdWildcard aW)
      {
        // A repeatable base wildcard absorbs the run of derived particles it can
        // match; their summed occurrence range must fit within the wildcard's
        long nSumMin = 0;
        long nSumMax = 0;
        boolean bUnboundedMax = false;
        while (nRI < aRs.size () && _leavesMatchWildcard (aRs.get (nRI), aW))
        {
          final Bounds aBounds = _elementBounds (aRs.get (nRI), 0);
          nSumMin = XsdModelLimits.add (nSumMin, aBounds.min ());
          if (aBounds.max () == XsdConstants.UNBOUNDED)
            bUnboundedMax = true;
          else
            nSumMax = XsdModelLimits.add (nSumMax, aBounds.max ());
          nRI++;
        }
        if (nSumMin < aBP.m_nMin)
          return false;
        if (aBP.m_nMax != XsdConstants.UNBOUNDED && (bUnboundedMax || nSumMax > aBP.m_nMax))
          return false;
        continue;
      }
      if (nRI < aRs.size () && particleRestrictsOK (aRs.get (nRI), aBP))
        nRI++;
      else
        if (!_emptiable (aBP))
          return false;
    }
    return nRI == aRs.size ();
  }

  /**
   * go-xslt <code>recurseChoiceOrdered</code>: every R branch restricts some B
   * branch, in an order-preserving mapping where each base branch is used at
   * most once (XSD 1.0 rcase-RecurseLax).
   */
  private boolean _recurseChoiceOrdered (@NonNull final ICommonsList <XsdParticle> aRs,
                                         @NonNull final ICommonsList <XsdParticle> aBs)
  {
    int nBI = 0;
    for (final XsdParticle aRP : aRs)
    {
      // A removed branch just drops that option
      if (matchesOnlyEmpty (aRP))
        continue;
      boolean bMatched = false;
      while (nBI < aBs.size ())
      {
        if (particleRestrictsOK (aRP, aBs.get (nBI)))
        {
          bMatched = true;
          nBI++;
          break;
        }
        nBI++;
      }
      if (!bMatched)
        return false;
    }
    return true;
  }

  /**
   * go-xslt <code>recurseChoice</code>: every R branch restricts some B branch
   * (order independent).
   */
  private boolean _recurseChoice (@NonNull final ICommonsList <XsdParticle> aRs,
                                  @NonNull final ICommonsList <XsdParticle> aBs)
  {
    for (final XsdParticle aRP : aRs)
    {
      // A removed branch (maxOccurs=0) just drops that option
      if (matchesOnlyEmpty (aRP))
        continue;
      boolean bOK = false;
      for (final XsdParticle aBP : aBs)
        if (particleRestrictsOK (aRP, aBP))
        {
          bOK = true;
          break;
        }
      if (!bOK)
        return false;
    }
    return true;
  }

  private static void _accumulate (@NonNull final Acc [] aSums,
                                   @NonNull final boolean [] aUsed,
                                   final int nTarget,
                                   @NonNull final XsdParticle aRP)
  {
    Acc aAcc = aSums[nTarget];
    if (aAcc == null)
    {
      aAcc = new Acc ();
      aSums[nTarget] = aAcc;
    }
    final Bounds aBounds = _elementBounds (aRP, 0);
    aAcc.m_nLo = XsdModelLimits.add (aAcc.m_nLo, aBounds.min ());
    if (aBounds.max () == XsdConstants.UNBOUNDED)
      aAcc.m_bUnbounded = true;
    else
      aAcc.m_nHi = XsdModelLimits.add (aAcc.m_nHi, aBounds.max ());
    aUsed[nTarget] = true;
  }

  /**
   * go-xslt <code>recurseUnordered</code> (all, and sequence against all): each
   * R particle maps to a distinct B particle; unmapped base particles must be
   * emptiable.
   */
  private boolean _recurseUnordered (@NonNull final ICommonsList <XsdParticle> aRs,
                                     @NonNull final ICommonsList <XsdParticle> aBs)
  {
    final boolean b11 = _is11 ();
    final boolean [] aUsed = new boolean [aBs.size ()];
    final boolean [] aReserved = new boolean [aBs.size ()];
    final Acc [] aSums = new Acc [aBs.size ()];
    final ICommonsList <XsdWildcard> aFanWCs = new CommonsArrayList <> ();
    for (final XsdParticle aRP : aRs)
    {
      // A removed particle contributes nothing
      if (matchesOnlyEmpty (aRP))
        continue;
      // 1.1 subsumption: every derived wildcard particle a base wildcard
      // subsumes (namespace, notQName and processContents wise) accumulates onto
      // it, exact 1:1 fits included, so the occurrence-sum check at the end
      // sees all contributors (all237/wild050 valid; all235.n hi 3+3>5 and
      // all236.n lo 2+2<5 stay invalid). A derived wildcard can never restrict
      // a base element, so this pre-route skips nothing else.
      if (b11 && aRP.m_aTerm instanceof final XsdWildcard aRW)
      {
        int nTarget = -1;
        for (int i = 0; i < aBs.size (); i++)
          if (aBs.get (i).m_aTerm instanceof final XsdWildcard aBW && _leavesMatchWildcard (aRP, aBW))
          {
            nTarget = i;
            break;
          }
        if (nTarget >= 0)
        {
          _accumulate (aSums, aUsed, nTarget, aRP);
          aFanWCs.add (aRW);
          continue;
        }
      }
      boolean bMatched = false;
      for (int i = 0; i < aBs.size (); i++)
      {
        if (aUsed[i] || aReserved[i])
          continue;
        if (particleRestrictsOK (aRP, aBs.get (i)))
        {
          aUsed[i] = true;
          bMatched = true;
          break;
        }
      }
      if (bMatched)
        continue;
      // A 1..1 choice member of the derived model: only one branch
      // materialises, so it fits when every branch restricts a distinct
      // still-free base member (all234). The targets are only reserved, not
      // used: the final emptiability check still applies to them, exactly
      // because the unpicked branches leave their members absent.
      if (b11 &&
          aRP.m_aTerm instanceof final XsdModelGroup aMG &&
          aMG.m_eCompositor == EXsdCompositor.CHOICE &&
          aRP.m_nMin == 1 &&
          aRP.m_nMax == 1)
      {
        final boolean [] aTaken = new boolean [aBs.size ()];
        boolean bAllOK = true;
        for (final XsdParticle aBranch : aMG.m_aParticles)
        {
          int nFound = -1;
          for (int i = 0; i < aBs.size (); i++)
          {
            if (aUsed[i] || aReserved[i] || aTaken[i])
              continue;
            if (particleRestrictsOK (aBranch, aBs.get (i)))
            {
              nFound = i;
              break;
            }
          }
          if (nFound < 0)
          {
            bAllOK = false;
            break;
          }
          aTaken[nFound] = true;
        }
        if (bAllOK)
        {
          for (int i = 0; i < aTaken.length; i++)
            if (aTaken[i])
              aReserved[i] = true;
          continue;
        }
      }
      // Substitution fan-in: several derived element members substitutable for
      // the same base element particle map onto it together, their occurrence
      // ranges summed against its range (all221: A1{6,8} and A2{6,8}
      // restricting a{10,20})
      if (aRP.m_aTerm instanceof final XsdElementDecl aRE)
      {
        int nTarget = -1;
        for (int i = 0; i < aBs.size (); i++)
          if (aBs.get (i).m_aTerm instanceof final XsdElementDecl aBT &&
              (aRE.m_aName.equals (aBT.m_aName) || _substitutableFor (aRE, aBT)) &&
              _elementRestrictsElement (aRE, aBT))
          {
            nTarget = i;
            break;
          }
        if (nTarget >= 0)
        {
          Acc aAcc = aSums[nTarget];
          if (aAcc == null)
          {
            aAcc = new Acc ();
            aSums[nTarget] = aAcc;
          }
          aAcc.m_nLo = XsdModelLimits.add (aAcc.m_nLo, aRP.m_nMin);
          if (aRP.m_nMax == XsdConstants.UNBOUNDED)
            aAcc.m_bUnbounded = true;
          else
            aAcc.m_nHi = XsdModelLimits.add (aAcc.m_nHi, aRP.m_nMax);
          aUsed[nTarget] = true;
          continue;
        }
      }
      // Group-shaped stragglers a single base wildcard subsumes fan in the same
      // way (their element-count bounds are group totals)
      if (b11)
      {
        int nTarget = -1;
        for (int i = 0; i < aBs.size (); i++)
          if (aBs.get (i).m_aTerm instanceof final XsdWildcard aBW && _leavesMatchWildcard (aRP, aBW))
          {
            nTarget = i;
            break;
          }
        if (nTarget >= 0)
        {
          _accumulate (aSums, aUsed, nTarget, aRP);
          continue;
        }
      }
      if (!_coveredByWildcardUnion (aRP, aBs))
        return false;
    }
    for (int i = 0; i < aSums.length; i++)
    {
      final Acc aAcc = aSums[i];
      if (aAcc == null)
        continue;
      final XsdParticle aBP = aBs.get (i);
      if (aAcc.m_nLo < aBP.m_nMin)
        return false;
      if (aBP.m_nMax != XsdConstants.UNBOUNDED && (aAcc.m_bUnbounded || aAcc.m_nHi > aBP.m_nMax))
        return false;
    }
    for (int i = 0; i < aBs.size (); i++)
    {
      final XsdParticle aBP = aBs.get (i);
      if (!aUsed[i] && !_emptiable (aBP))
        return false;
      // 1.1 all-group competition: a removed (emptiable, unused) base element
      // whose name a derived wildcard admits flips the attribution, the base
      // element-assesses that name, the derived wildcard-assesses it, so the
      // derived only restricts the base if the wildcard route is at most as
      // permissive: the name's global declaration type must derive from the
      // removed particle's type; a lax or skip wildcard with no declaration
      // admits anything and cannot restrict it (wild069 invalid versus wild068's
      // sequence twin, where order shields the removed slot)
      if (!aUsed[i] && b11 && aBP.m_aTerm instanceof final XsdElementDecl aBT)
      {
        for (final XsdWildcard aRW : aFanWCs)
        {
          if (!aRW.matches (aBT.m_aName))
            continue;
          final XsdElementDecl aGlobal = m_aSchema.m_aElements.get (aBT.m_aName);
          if (aGlobal == null)
          {
            // Strict without a declaration rejects the name outright
            if (XsdWildcard.PROCESS_STRICT.equals (aRW.m_sProcess))
              continue;
            return false;
          }
          if (!XsdComponentChecks.typeRestrictsFrom (aGlobal.m_aType, aBT.m_aType))
            return false;
        }
      }
    }
    return true;
  }

  /**
   * go-xslt <code>mapAndSumMapping</code>: every non-empty derived particle
   * must be a valid restriction of some base choice branch. (The
   * occurrence-sum step is enforced separately via {@link #_sumOccursOK}.)
   */
  private boolean _mapAndSumMapping (@NonNull final XsdModelGroup aR, @NonNull final XsdModelGroup aB)
  {
    for (final XsdParticle aRP : aR.m_aParticles)
    {
      if (matchesOnlyEmpty (aRP))
        continue;
      boolean bOK = false;
      for (final XsdParticle aBP : aB.m_aParticles)
        if (particleRestrictsOK (aRP, aBP))
        {
          bOK = true;
          break;
        }
      if (!bOK)
        return false;
    }
    return true;
  }

  /**
   * go-xslt <code>groupRestrictsGroup</code>: the same-compositor recursions
   * and sequence against all.
   */
  private boolean _groupRestrictsGroup (@NonNull final XsdModelGroup aR, @NonNull final XsdModelGroup aB)
  {
    if (aR.m_eCompositor == aB.m_eCompositor)
    {
      switch (aR.m_eCompositor)
      {
        case SEQUENCE:
          return _recurseSeq (aR.m_aParticles, aB.m_aParticles) ||
                 _recurseSeqLenient (aR.m_aParticles, aB.m_aParticles);
        case CHOICE:
          // rcase-RecurseLax is order preserving in 1.0 (particlesT002/T009);
          // 1.1 subsumption is order free
          if (!_is11 ())
            return _recurseChoiceOrdered (aR.m_aParticles, aB.m_aParticles);
          return _recurseChoice (aR.m_aParticles, aB.m_aParticles);
        case ALL:
          // rcase-Recurse (all:all) is order preserving in 1.0 too
          // (particlesS002); 1.1 keeps the unordered mapping (all221 fan-in)
          if (!_is11 ())
            return _recurseSeq (aR.m_aParticles, aB.m_aParticles);
          return _recurseUnordered (aR.m_aParticles, aB.m_aParticles);
        default:
          break;
      }
    }
    if (aR.m_eCompositor == EXsdCompositor.SEQUENCE && aB.m_eCompositor == EXsdCompositor.ALL)
      return _recurseUnordered (aR.m_aParticles, aB.m_aParticles);
    if (aR.m_eCompositor == EXsdCompositor.SEQUENCE && aB.m_eCompositor == EXsdCompositor.CHOICE)
    {
      final boolean bPrev = m_bInMapAndSum;
      m_bInMapAndSum = true;
      try
      {
        return _mapAndSumMapping (aR, aB);
      }
      finally
      {
        m_bInMapAndSum = bPrev;
      }
    }
    // Every other compositor pairing (choice:seq, all:seq, all:choice,
    // choice:all) is forbidden by the Particle Valid (Restriction) case table
    return false;
  }

  /**
   * go-xslt <code>particleRestrictsGroup</code>: a single element or wildcard R
   * restricting a group B.
   */
  private boolean _particleRestrictsGroup (@NonNull final XsdParticle aR, @NonNull final XsdModelGroup aB)
  {
    if (aB.m_eCompositor == EXsdCompositor.CHOICE)
    {
      for (final XsdParticle aBP : aB.m_aParticles)
        if (particleRestrictsOK (aR, aBP))
          return true;
      return false;
    }
    // Sequence or all
    boolean bMatched = false;
    for (final XsdParticle aBP : aB.m_aParticles)
    {
      if (!bMatched && particleRestrictsOK (aR, aBP))
      {
        bMatched = true;
        continue;
      }
      if (!_emptiable (aBP))
        return false;
    }
    return bMatched;
  }

  private boolean _particleRestrictsOKRaw (@Nullable final XsdParticle aR, @Nullable final XsdParticle aB)
  {
    if (aR == null)
      return aB == null || _emptiable (aB);
    // Restricting an empty (no-particle) base: the derived particle is valid
    // only if it matches nothing but empty (an empty sequence); an optional
    // element would add content and is invalid
    if (aB == null)
      return matchesOnlyEmpty (aR);
    if (aB.m_aTerm instanceof final XsdWildcard aBT)
    {
      // NSRecurseCheckCardinality: a group restricting a wildcard compares its
      // total element-count range (not the group particle's own occurs) against
      // the wildcard's occurrence range; every leaf must be wildcard-admitted
      if (aR.m_aTerm instanceof XsdModelGroup)
      {
        final Bounds aBounds = _elementBounds (aR, 0);
        if (aBounds.min () < aB.m_nMin)
          return false;
        if (aB.m_nMax != XsdConstants.UNBOUNDED &&
            (aBounds.max () == XsdConstants.UNBOUNDED || aBounds.max () > aB.m_nMax))
          return false;
        return _leavesMatchWildcard (aR, aBT);
      }
      // Element or wildcard R: occurrences fit and its namespace is admitted. A
      // derived wildcard additionally must not weaken processContents
      // (strict > lax > skip).
      if (aR.m_aTerm instanceof final XsdWildcard aRW &&
          XsdWildcard.processRank (aRW.m_sProcess) < XsdWildcard.processRank (aBT.m_sProcess))
        return false;
      return _occursOK (aR, aB) && _leavesMatchWildcard (aR, aBT);
    }
    if (aB.m_aTerm instanceof final XsdElementDecl aBT)
    {
      if (!(aR.m_aTerm instanceof final XsdElementDecl aRE))
      {
        // A wildcard cannot restrict an element. A group can when every element
        // leaf is the base element itself or substitutable for it (elemZ027a/b:
        // choice{m1,m2} restricting the head), compared on the element-count
        // basis like NSRecurseCheckCardinality. Value constraints or blocking
        // on the base stay conservative rejects.
        if (aR.m_aTerm instanceof XsdModelGroup && !aBT.m_bHasFixed && aBT.m_aBlocked.isEmpty ())
        {
          final Bounds aBounds = _elementBounds (aR, 0);
          if (aBounds.min () >= aB.m_nMin &&
              (aB.m_nMax == XsdConstants.UNBOUNDED ||
               (aBounds.max () != XsdConstants.UNBOUNDED && aBounds.max () <= aB.m_nMax)) &&
              _leavesSubstitutableFor (aR, aBT))
            return true;
        }
        return false;
      }
      if (!_occursOK (aR, aB) || !_elementRestrictsElement (aRE, aBT))
        return false;
      if (aRE.m_aName.equals (aBT.m_aName))
        return true;
      // NameAndTypeOK via substitution: the derived element may be a transitive
      // substitution-group member of the base element that the base permits
      // (abstract members and blocked derivations excluded)
      return _substitutableFor (aRE, aBT);
    }
    if (aB.m_aTerm instanceof final XsdModelGroup aBT)
    {
      if (aR.m_aTerm instanceof final XsdModelGroup aRT)
      {
        boolean bOcc = _occursOK (aR, aB);
        if (aRT.m_eCompositor == EXsdCompositor.SEQUENCE && aBT.m_eCompositor == EXsdCompositor.CHOICE)
        {
          // MapAndSum clause 2 is the occurrence rule for sequence to choice:
          // the sequence's summed range (min/max times the particle count) must
          // fit the base choice's range. It both accepts what the plain range
          // check rejects (particlesV003) and rejects what it accepts
          // (particlesV005: (1,2)x2=(2,4) not within (0,2)).
          bOcc = _sumOccursOK (aR, aRT, aB);
        }
        if (bOcc && _groupRestrictsGroup (aRT, aBT))
          return true;
        // XSD 1.1 subsumption, choice over an all or a sequence: L(choice) is
        // the union of its branches, so a 1..1 choice fits if every branch
        // restricts the whole base group on its own (all231/232 valid; all233.n's
        // a{1,8} branch still fails; choice(a,b) restricts seq(a?,b?) by
        // section 3.4.6.4's language inclusion; the route stays out of 1.0,
        // particlesHb009, where the case table forbids choice:seq). Gated on
        // exact 1..1 occurs: a repeating choice concatenates branch picks and
        // cannot be decomposed per branch.
        if (_is11 () &&
            aRT.m_eCompositor == EXsdCompositor.CHOICE &&
            (aBT.m_eCompositor == EXsdCompositor.ALL || aBT.m_eCompositor == EXsdCompositor.SEQUENCE) &&
            aR.m_nMin == 1 &&
            aR.m_nMax == 1 &&
            aRT.m_aParticles.isNotEmpty ())
        {
          boolean bOK = true;
          for (final XsdParticle aBranch : aRT.m_aParticles)
            if (!particleRestrictsOK (aBranch, aB))
            {
              bOK = false;
              break;
            }
          if (bOK)
            return true;
        }
        return false;
      }
      // Element or wildcard R. RecurseAsIfGroup is ambiguous about whether the
      // synthesised wrapper group carries R's occurrence range (with R inside at
      // 1..1) or 1..1 (with R keeping its occurs). The suite grants the
      // permissive reading only when the base group repeats without bound
      // (particlesZ001), and holds the strict one elsewhere
      // (particlesHa161/K006), so the wrapper-carries-occurs route is gated on
      // b.max == unbounded.
      if (aB.m_nMax == XsdConstants.UNBOUNDED && _occursOK (aR, aB))
      {
        final XsdParticle aR1 = new XsdParticle (1, 1, aR.m_aTerm);
        if (_particleRestrictsGroup (aR1, aBT))
          return true;
      }
      // Legacy route kept as an additional acceptance path: R with its own
      // occurs against a base group admitting exactly one iteration
      if (aB.m_nMin > 1 || aB.m_nMax == 0)
        return false;
      return _particleRestrictsGroup (aR, aBT);
    }
    return true;
  }

  /**
   * go-xslt <code>particleRestrictsOK</code>: first tries the particles as they
   * are; when that rejects, it retries with the derived side's pointless
   * wrappers collapsed. Normalisation is thus purely an additional acceptance
   * route (monotone accept-more): it can fix a false rejection (a 1..1 group
   * reference wrapping one element restricting that element) but can never newly
   * reject a mapping the raw pass accepted.
   *
   * @param aR
   *        The derived particle; <code>null</code> for empty content.
   * @param aB
   *        The base particle; <code>null</code> for empty content.
   * @return <code>true</code> if the derived particle is a valid restriction of
   *         the base (errs towards "valid" where undecidable).
   * @throws XsdUnsupportedException
   *         if the content models are nested too deeply to be compared
   */
  boolean particleRestrictsOK (@Nullable final XsdParticle aR, @Nullable final XsdParticle aB)
  {
    if (m_nDepth >= XsdModelLimits.MAX_DEPTH)
      throw XsdModelLimits.tooDeep ();
    m_nDepth++;
    try
    {
      if (_particleRestrictsOKRaw (aR, aB))
        return true;
      final XsdParticle aNR = _normalizeParticle (aR);
      if (aNR != aR && _particleRestrictsOKRaw (aNR, aB))
        return true;
      // Base-side collapse is retried only when the base reduces to a wildcard:
      // that routes into the strict NSRecurseCheckCardinality/occurs check
      // (which cannot blanket-accept), fixing e.g. all{e1,e2,e3} restricting
      // seq{any 3..3}. A general base collapse would instead reach lenient group
      // paths and has been shown to accept invalid restrictions.
      final XsdParticle aNB = _normalizeParticle (aB);
      if (aNB != aB && aNB != null && aNB.m_aTerm instanceof XsdWildcard)
      {
        if (_particleRestrictsOKRaw (_normalizeParticle (aR), aNB))
          return true;
      }
      // Pointless-sequence elimination on both sides, tried as one more
      // accept-more route so a flat restriction of an xs:extension's nested
      // base-content sequence maps particle for particle
      final XsdParticle aFR = _flattenSeq (aR);
      final XsdParticle aFB = _flattenSeq (aB);
      if ((aFR != aR || aFB != aB) && _particleRestrictsOKRaw (aFR, aFB))
        return true;
      // XSD 1.1 subsumption, min-0 lifting: with an emptiable base, L(R{min:0})
      // is {empty} union L(R{min:1}) and the empty sequence is in L(B), so R fits
      // if its min:1 twin does (particlesHa161, addB118; in 1.0 the same pairs
      // stay invalid under the clause tables, Ha162 pins that)
      if (aR != null && _is11 () && aR.m_nMin == 0 && _emptiable (aB))
      {
        final XsdParticle aR1 = new XsdParticle (1, aR.m_nMax, aR.m_aTerm);
        if (_particleRestrictsOKRaw (aR1, aB))
          return true;
        final XsdParticle aNR1 = _normalizeParticle (aR1);
        if (aNR1 != aR1 && _particleRestrictsOKRaw (aNR1, aB))
          return true;
      }
      return false;
    }
    finally
    {
      m_nDepth--;
    }
  }
}
