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

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;

/**
 * Unique Particle Attribution (go-xslt <code>upa.go</code>). A content model
 * must be deterministic: at any point an element must be attributable to a
 * single particle without lookahead. It is checked with a Glushkov position
 * automaton; each element or wildcard particle is a "position". The model is
 * UPA-valid if no reachable decision set (the first set of the model, or the
 * follow set of any position) contains two distinct element positions with the
 * same expanded name.
 * <p>
 * The check is deliberately conservative: it flags collisions between element
 * positions whose match-name sets overlap (the declaration's own name plus its
 * transitive substitution-group members, honouring abstract and blocking) and,
 * in 1.0, wildcard against element overlap.
 * </p>
 *
 * @author Philip Helger
 */
@Immutable
final class XsdUpa
{
  /** Node budget of the unfolding, as in go-xslt. */
  private static final int UNFOLD_BUDGET = 1 << 17;

  /**
   * One unfolding run: a structurally identical copy of the content model in
   * which every particle and model group is a fresh object, so that one named
   * model group reached through two group references contributes two distinct
   * sets of positions (go-xslt <code>upaUnfold</code>). Only the particle and
   * group spine is duplicated; element and wildcard terms stay shared. A cyclic
   * group reference keeps its cycle.
   */
  private static final class Unfolder
  {
    private final ICommonsMap <XsdModelGroup, XsdParticle> m_aActive = new CommonsHashMap <> ();
    private int m_nBudget = UNFOLD_BUDGET;

    @Nullable
    XsdParticle unfold (@Nullable final XsdParticle aParticle, final int nDepth)
    {
      if (aParticle == null)
        return null;
      if (m_nBudget <= 0)
        return aParticle;
      XsdModelLimits.checkDepth (nDepth);
      m_nBudget--;
      final XsdParticle aNew = new XsdParticle (aParticle.m_nMin, aParticle.m_nMax, aParticle.m_aTerm);
      if (!(aParticle.m_aTerm instanceof final XsdModelGroup aMG))
        return aNew;
      final XsdParticle aPrev = m_aActive.get (aMG);
      if (aPrev != null)
        return aPrev;
      m_aActive.put (aMG, aNew);
      try
      {
        final XsdModelGroup aNewMG = new XsdModelGroup (aMG.m_eCompositor);
        aNew.m_aTerm = aNewMG;
        for (final XsdParticle aSub : aMG.m_aParticles)
          aNewMG.m_aParticles.add (unfold (aSub, nDepth + 1));
        return aNew;
      }
      finally
      {
        m_aActive.remove (aMG);
      }
    }
  }

  @PresentForCodeCoverage
  private static final XsdUpa INSTANCE = new XsdUpa ();

  private XsdUpa ()
  {}

  private static boolean _nullableSeen (@Nullable final XsdParticle aParticle,
                                        @NonNull final Set <XsdModelGroup> aSeen,
                                        final int nDepth)
  {
    if (aParticle == null || aParticle.m_nMin == 0)
      return true;
    if (!(aParticle.m_aTerm instanceof final XsdModelGroup aMG))
      return false;
    XsdModelLimits.checkDepth (nDepth);
    // A cyclic model group is not finitely nullable
    if (!aSeen.add (aMG))
      return false;
    try
    {
      if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
      {
        for (final XsdParticle aSub : aMG.m_aParticles)
          if (_nullableSeen (aSub, aSeen, nDepth + 1))
            return true;
        // An empty choice matches nothing, not even the empty sequence
        return false;
      }
      for (final XsdParticle aSub : aMG.m_aParticles)
        if (!_nullableSeen (aSub, aSeen, nDepth + 1))
          return false;
      return true;
    }
    finally
    {
      aSeen.remove (aMG);
    }
  }

  @NonNull
  private static XsdParticle _unfold (@NonNull final XsdParticle aRoot)
  {
    final Unfolder aUnfolder = new Unfolder ();
    final XsdParticle ret = aUnfolder.unfold (aRoot, 0);
    // Budget exhausted: check the shared tree, as go-xslt does
    return aUnfolder.m_nBudget <= 0 || ret == null ? aRoot : ret;
  }

  private static void _first (@Nullable final XsdParticle aParticle,
                              @NonNull final ICommonsList <XsdParticle> aOut,
                              @NonNull final Set <XsdModelGroup> aSeen,
                              final int nDepth)
  {
    // A maxOccurs=0 particle matches nothing
    if (aParticle == null || aParticle.m_nMax == 0)
      return;
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
    {
      XsdModelLimits.checkDepth (nDepth);
      if (!aSeen.add (aMG))
        return;
      try
      {
        if (aMG.m_eCompositor == EXsdCompositor.SEQUENCE)
        {
          for (final XsdParticle aSub : aMG.m_aParticles)
          {
            _first (aSub, aOut, aSeen, nDepth + 1);
            if (!upaNullable (aSub))
              break;
          }
        }
        else
        {
          for (final XsdParticle aSub : aMG.m_aParticles)
            _first (aSub, aOut, aSeen, nDepth + 1);
        }
      }
      finally
      {
        aSeen.remove (aMG);
      }
    }
    else
      aOut.add (aParticle);
  }

  private static void _last (@Nullable final XsdParticle aParticle,
                             @NonNull final ICommonsList <XsdParticle> aOut,
                             @NonNull final Set <XsdModelGroup> aSeen,
                             final int nDepth)
  {
    // A maxOccurs=0 particle matches nothing
    if (aParticle == null || aParticle.m_nMax == 0)
      return;
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
    {
      XsdModelLimits.checkDepth (nDepth);
      if (!aSeen.add (aMG))
        return;
      try
      {
        if (aMG.m_eCompositor == EXsdCompositor.SEQUENCE)
        {
          for (int i = aMG.m_aParticles.size () - 1; i >= 0; i--)
          {
            final XsdParticle aSub = aMG.m_aParticles.get (i);
            _last (aSub, aOut, aSeen, nDepth + 1);
            if (!upaNullable (aSub))
              break;
          }
        }
        else
        {
          for (final XsdParticle aSub : aMG.m_aParticles)
            _last (aSub, aOut, aSeen, nDepth + 1);
        }
      }
      finally
      {
        aSeen.remove (aMG);
      }
    }
    else
      aOut.add (aParticle);
  }

  @NonNull
  private static ICommonsList <XsdParticle> _firstOf (@NonNull final XsdParticle aParticle)
  {
    final ICommonsList <XsdParticle> ret = new CommonsArrayList <> ();
    _first (aParticle, ret, new CommonsHashSet <> (), 0);
    return ret;
  }

  @NonNull
  private static ICommonsList <XsdParticle> _lastOf (@NonNull final XsdParticle aParticle)
  {
    final ICommonsList <XsdParticle> ret = new CommonsArrayList <> ();
    _last (aParticle, ret, new CommonsHashSet <> (), 0);
    return ret;
  }

  private static void _addFollow (@NonNull final ICommonsMap <XsdParticle, ICommonsList <XsdParticle>> aFollow,
                                  @NonNull final XsdParticle aFrom,
                                  @NonNull final ICommonsList <XsdParticle> aTo)
  {
    aFollow.computeIfAbsent (aFrom, k -> new CommonsArrayList <> ()).addAll (aTo);
  }

  private static void _follow (@Nullable final XsdParticle aParticle,
                               @NonNull final ICommonsMap <XsdParticle, ICommonsList <XsdParticle>> aFollow,
                               @NonNull final Set <XsdModelGroup> aSeen,
                               final int nDepth)
  {
    // A maxOccurs=0 particle contributes no positions
    if (aParticle == null || aParticle.m_nMax == 0)
      return;
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
    {
      XsdModelLimits.checkDepth (nDepth);
      if (!aSeen.add (aMG))
        return;
      try
      {
        final ICommonsList <XsdParticle> aParts = aMG.m_aParticles;
        switch (aMG.m_eCompositor)
        {
          case SEQUENCE:
            for (int i = 0; i < aParts.size (); i++)
            {
              final ICommonsList <XsdParticle> aLastI = _lastOf (aParts.get (i));
              final ICommonsList <XsdParticle> aFirstNext = new CommonsArrayList <> ();
              for (int j = i + 1; j < aParts.size (); j++)
              {
                _first (aParts.get (j), aFirstNext, new CommonsHashSet <> (), 0);
                if (!upaNullable (aParts.get (j)))
                  break;
              }
              for (final XsdParticle aLast : aLastI)
                _addFollow (aFollow, aLast, aFirstNext);
            }
            break;
          case ALL:
            // In an all group any member may follow any other
            for (int i = 0; i < aParts.size (); i++)
            {
              final ICommonsList <XsdParticle> aLastI = _lastOf (aParts.get (i));
              for (int j = 0; j < aParts.size (); j++)
              {
                if (i == j)
                  continue;
                final ICommonsList <XsdParticle> aFirstJ = _firstOf (aParts.get (j));
                for (final XsdParticle aLast : aLastI)
                  _addFollow (aFollow, aLast, aFirstJ);
              }
            }
            break;
          default:
            break;
        }
        for (final XsdParticle aSub : aParts)
          _follow (aSub, aFollow, aSeen, nDepth + 1);
      }
      finally
      {
        aSeen.remove (aMG);
      }
    }
    // A repeatable particle can loop: its last positions are followed by its
    // first. A counted particle with min == max never offers a loop-versus-exit
    // choice, so no loop edge there (mgZ005: b{2,2} followed by b{1,1} is valid)
    if (aParticle.m_nMax == XsdConstants.UNBOUNDED ||
        (aParticle.m_nMax > 1 && aParticle.m_nMin != aParticle.m_nMax))
    {
      final ICommonsList <XsdParticle> aFirst = _firstOf (aParticle);
      for (final XsdParticle aLast : _lastOf (aParticle))
        _addFollow (aFollow, aLast, aFirst);
    }
  }

  private static boolean _anyNSOutside (@NonNull final ICommonsList <String> aList,
                                        @NonNull final ICommonsList <String> aExcluded)
  {
    for (final String sNS : aList)
      if (!aExcluded.contains (sNS))
        return true;
    return false;
  }

  /**
   * go-xslt <code>upaMatchNames</code>: the set of element names a position can
   * match: its own name (unless abstract) plus every transitive
   * substitution-group member it permits. Expansion applies only to the global
   * declaration a head actually is.
   *
   * @param aSchema
   *        The schema.
   * @param aDecl
   *        The element declaration.
   * @param aCache
   *        Cache per declaration (identity).
   * @return The match names, never <code>null</code>; do not modify.
   */
  @NonNull
  static ICommonsSet <XsdName> upaMatchNames (@NonNull final XsdSchema aSchema,
                                              @NonNull final XsdElementDecl aDecl,
                                              @NonNull final Map <XsdElementDecl, ICommonsSet <XsdName>> aCache)
  {
    final ICommonsSet <XsdName> aCached = aCache.get (aDecl);
    if (aCached != null)
      return aCached;
    final ICommonsSet <XsdName> aNames = new CommonsHashSet <> ();
    aCache.put (aDecl, aNames);
    if (!aDecl.m_bAbstract)
      aNames.add (aDecl.m_aName);
    if (aSchema.m_aElements.get (aDecl.m_aName) != aDecl)
      return aNames;
    final ICommonsSet <XsdName> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdName> aStack = new CommonsArrayList <> ();
    aStack.add (aDecl.m_aName);
    while (aStack.isNotEmpty ())
    {
      final XsdName aHead = aStack.removeLast ();
      for (final XsdElementDecl aMember : aSchema.substMembersOf (aHead))
      {
        if (!aSeen.add (aMember.m_aName))
          continue;
        // 1.1 counts abstract members for schema-time UPA too (bug 4337);
        // 1.0 keeps the exclusion (elemZ028 family)
        if ((!aMember.m_bAbstract || aSchema.m_eVersion == EXsdVersion.V11) &&
            aSchema.substitutionAllowed (aDecl, aMember))
          aNames.add (aMember.m_aName);
        aStack.add (aMember.m_aName);
      }
    }
    return aNames;
  }

  /**
   * go-xslt <code>wildcardsOverlap</code>: whether two wildcards can admit a
   * common name. Deliberately conservative: only the namespace constraint is
   * considered, and a union wildcard counts as overlapping nothing.
   *
   * @param aA
   *        First wildcard.
   * @param aB
   *        Second wildcard.
   * @return <code>true</code> if they may overlap.
   */
  static boolean wildcardsOverlap (@NonNull final XsdWildcard aA, @NonNull final XsdWildcard aB)
  {
    if (aA.m_aParts.isNotEmpty () || aB.m_aParts.isNotEmpty ())
      return false;
    final boolean bAAny = XsdWildcard.NS_ANY.equals (aA.m_sNsMode);
    final boolean bBAny = XsdWildcard.NS_ANY.equals (aB.m_sNsMode);
    final boolean bAList = XsdWildcard.NS_LIST.equals (aA.m_sNsMode);
    final boolean bBList = XsdWildcard.NS_LIST.equals (aB.m_sNsMode);
    final boolean bAOther = XsdWildcard.NS_OTHER.equals (aA.m_sNsMode);
    final boolean bBOther = XsdWildcard.NS_OTHER.equals (aB.m_sNsMode);
    if (bAAny || bBAny)
    {
      // Refinement for 1.1 notNamespace: an ##any wildcard excluding a finite
      // namespace list still shares infinitely many namespaces with anything
      // except another finite list; only there must a listed namespace survive
      if (bAAny && bBList)
        return _anyNSOutside (aB.m_aNamespaces, aA.m_aNotNS);
      if (bBAny && bAList)
        return _anyNSOutside (aA.m_aNamespaces, aB.m_aNotNS);
      return true;
    }
    if (bAList && bBList)
    {
      for (final String sNS : aA.m_aNamespaces)
        if (aB.m_aNamespaces.contains (sNS))
          return true;
      return false;
    }
    if (bAOther && bBOther)
      return true;
    if (bAOther)
    {
      for (final String sNS : aB.m_aNamespaces)
        if (!sNS.equals (aA.m_sTargetNS) && !sNS.isEmpty ())
          return true;
      return false;
    }
    // b is "other", a is "list"
    for (final String sNS : aA.m_aNamespaces)
      if (!sNS.equals (aB.m_sTargetNS) && !sNS.isEmpty ())
        return true;
    return false;
  }

  private static void _noCollision (@NonNull final XsdSchema aSchema,
                                    @NonNull final ICommonsList <XsdParticle> aSet,
                                    @NonNull final Map <XsdElementDecl, ICommonsSet <XsdName>> aNamesCache)
  {
    final ICommonsMap <XsdName, XsdParticle> aByName = new CommonsHashMap <> ();
    final ICommonsList <XsdParticle> aWilds = new CommonsArrayList <> ();
    for (final XsdParticle aPos : aSet)
    {
      if (aPos.m_aTerm instanceof final XsdElementDecl aDecl)
      {
        for (final XsdName aName : upaMatchNames (aSchema, aDecl, aNamesCache))
        {
          final XsdParticle aPrev = aByName.get (aName);
          if (aPrev != null && aPrev != aPos)
            throw XsdInvalidException.invalid ("cos-nonambig",
                                               "content model is not deterministic (UPA): element " + aName);
          aByName.put (aName, aPos);
        }
      }
      else
        if (aPos.m_aTerm instanceof final XsdWildcard aWC)
        {
          // Wildcard against wildcard competition violates UPA in both
          // versions; 1.1 only made wildcards weak against element declarations
          for (final XsdParticle aOther : aWilds)
          {
            if (aOther == aPos)
              continue;
            if (wildcardsOverlap ((XsdWildcard) aOther.m_aTerm, aWC))
              throw XsdInvalidException.invalid ("cos-nonambig",
                                                 "content model is not deterministic (UPA): overlapping wildcards");
          }
          if (aSchema.m_eVersion == EXsdVersion.V10)
          {
            // Element against wildcard overlap (1.0 only; in 1.1 the element
            // declaration wins the competition): a wildcard admitting a sibling
            // element position's name competes with it, substitution members
            // included
            for (final XsdParticle aEPos : aSet)
            {
              if (!(aEPos.m_aTerm instanceof final XsdElementDecl aED) || aEPos == aPos)
                continue;
              for (final XsdName aName : upaMatchNames (aSchema, aED, aNamesCache))
                if (aWC.matches (aName))
                  throw XsdInvalidException.invalid ("cos-nonambig",
                                                     "content model is not deterministic (UPA): element " +
                                                                    aName +
                                                                    " vs wildcard");
            }
          }
          aWilds.add (aPos);
        }
    }
  }

  /**
   * go-xslt <code>checkUPA</code> for one effective content model. The loader
   * calls it for every complex type, named and anonymous, with
   * {@link XsdSchema#effectiveParticle(XsdComplexType)} (go-xslt
   * <code>checkAllUPA</code>).
   *
   * @param aSchema
   *        The schema (substitution groups, version).
   * @param aRoot
   *        The effective particle; <code>null</code> for empty content.
   * @throws XsdInvalidException
   *         (code <code>cos-nonambig</code>) if the model violates UPA
   * @throws XsdUnsupportedException
   *         if the model is nested too deeply to be walked
   */
  static void checkUPA (@NonNull final XsdSchema aSchema, @Nullable final XsdParticle aRoot)
  {
    if (aRoot == null)
      return;
    final XsdParticle aUnfolded = _unfold (aRoot);
    final ICommonsMap <XsdParticle, ICommonsList <XsdParticle>> aFollow = new CommonsLinkedHashMap <> ();
    _follow (aUnfolded, aFollow, new CommonsHashSet <> (), 0);

    final ICommonsList <XsdParticle> aFirst = _firstOf (aUnfolded);
    final ICommonsMap <XsdElementDecl, ICommonsSet <XsdName>> aNamesCache = new CommonsHashMap <> ();
    _noCollision (aSchema, aFirst, aNamesCache);
    for (final ICommonsList <XsdParticle> aSet : aFollow.values ())
      _noCollision (aSchema, aSet, aNamesCache);
  }

  /**
   * go-xslt <code>upaNullable</code>.
   *
   * @param aParticle
   *        The particle.
   * @return <code>true</code> if the particle can match the empty sequence.
   * @throws XsdUnsupportedException
   *         if the particle is nested too deeply to be walked
   */
  static boolean upaNullable (@Nullable final XsdParticle aParticle)
  {
    return _nullableSeen (aParticle, new CommonsHashSet <> (), 0);
  }
}
