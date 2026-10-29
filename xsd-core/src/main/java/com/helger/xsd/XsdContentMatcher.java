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

import java.util.BitSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.xdm.IXdmNode;

/**
 * The grammar matcher (go-xslt <code>content.go</code>): does the child
 * element sequence satisfy a content model? The matcher is a pure function of
 * (particle, start position) over a fixed child list, so its results are
 * memoised, which turns the otherwise exponential backtracking over nested and
 * repeating groups into polynomial time. A step budget is a final backstop
 * against pathological models. The effective content model and attribute
 * helpers of go-xslt's <code>content.go</code> live in {@link XsdSchema}
 * (<code>effectiveParticle</code>, <code>effectiveAttrs</code>,
 * <code>effectiveOpenContent</code>, <code>matchesElementName</code>,
 * <code>substitutionAllowed</code>, <code>contentDecls</code>) and
 * {@link XsdWildcard} (<code>wildcardMatches</code>,
 * <code>wildcardAdmits</code>).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdContentMatcher
{
  /** Above this many children the linear counting matcher takes over. */
  private static final int MAX_NFA_CHILDREN = 600;
  private static final int STEP_BUDGET = 120_000;

  private record MKey (XsdParticle particle, int start)
  {}

  /**
   * One matching run over a fixed child list. The result sets of its methods
   * are memoised and shared: they must not be modified.
   */
  private static final class Matcher
  {
    private final XsdSchema m_aSchema;
    private final XsdName [] m_aNames;
    private final ICommonsMap <MKey, BitSet> m_aMemo = new CommonsHashMap <> ();
    /** Recursion stack, to break cyclic group references. */
    private final Set <MKey> m_aActive = new CommonsHashSet <> ();
    private int m_nBudget = STEP_BUDGET;
    private int m_nDepth;
    /** Budget (or depth) ran out: the verdict is undetermined. */
    private boolean m_bExhausted;
    /**
     * When set, an interleave open-content wildcard (1.1): at any position,
     * children it admits may be skipped over.
     */
    private final XsdWildcard m_aOpen;
    /**
     * Pin an attribution per child index during the greedy left-to-right
     * assignment (see {@link XsdContentMatcher#greedyOpenAttribution}).
     */
    private final BitSet m_aForceOpen;
    private final BitSet m_aForceModel;

    Matcher (@NonNull final XsdSchema aSchema,
             @NonNull final XsdName [] aNames,
             @Nullable final XsdWildcard aOpen,
             @NonNull final BitSet aForceOpen,
             @NonNull final BitSet aForceModel)
    {
      m_aSchema = aSchema;
      m_aNames = aNames;
      m_aOpen = aOpen;
      m_aForceOpen = aForceOpen;
      m_aForceModel = aForceModel;
    }

    boolean isExhausted ()
    {
      return m_bExhausted;
    }

    /**
     * @return The position plus every position reachable by skipping
     *         open-content children; just the position when no interleave
     *         wildcard is active.
     */
    @NonNull
    private BitSet _clo (final int nPos)
    {
      final BitSet ret = new BitSet ();
      ret.set (nPos);
      for (int i = nPos; m_aOpen != null &&
                         i < m_aNames.length &&
                         !m_aForceModel.get (i) &&
                         m_aOpen.matches (m_aNames[i]); i++)
        ret.set (i + 1);
      return ret;
    }

    @NonNull
    private BitSet _cloSet (@NonNull final BitSet aPositions)
    {
      if (m_aOpen == null)
        return aPositions;
      final BitSet ret = new BitSet ();
      for (int p = aPositions.nextSetBit (0); p >= 0; p = aPositions.nextSetBit (p + 1))
        ret.or (_clo (p));
      return ret;
    }

    private boolean _partNullable (@NonNull final XsdParticle aParticle,
                                   @NonNull final Set <XsdModelGroup> aSeen,
                                   final int nDepth)
    {
      if (aParticle.m_nMin == 0)
        return true;
      if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
        return _groupNullable (aMG, aSeen, nDepth);
      return false;
    }

    private boolean _groupNullable (@NonNull final XsdModelGroup aMG,
                                    @NonNull final Set <XsdModelGroup> aSeen,
                                    final int nDepth)
    {
      if (nDepth > XsdModelLimits.MAX_DEPTH)
      {
        m_bExhausted = true;
        return false;
      }
      // Cyclic group references are non-nullable, to break the recursion
      if (!aSeen.add (aMG))
        return false;
      try
      {
        if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
        {
          for (final XsdParticle aSub : aMG.m_aParticles)
            if (_partNullable (aSub, aSeen, nDepth + 1))
              return true;
          // An empty choice matches nothing, not even the empty sequence, so a
          // required empty choice is unsatisfiable (complex022)
          return false;
        }
        // Sequence or all
        for (final XsdParticle aSub : aMG.m_aParticles)
          if (!_partNullable (aSub, aSeen, nDepth + 1))
            return false;
        return true;
      }
      finally
      {
        aSeen.remove (aMG);
      }
    }

    /**
     * @return Whether the term can match zero child elements. Element and
     *         wildcard terms always consume one child.
     */
    private boolean _nullable (@NonNull final IXsdTerm aTerm)
    {
      if (aTerm instanceof final XsdModelGroup aMG)
        return _groupNullable (aMG, new CommonsHashSet <> (), 0);
      return false;
    }

    private void _allMembers (@NonNull final XsdModelGroup aMG,
                              @NonNull final Set <XsdModelGroup> aSeen,
                              @NonNull final ICommonsList <XsdParticle> aOut,
                              final int nDepth)
    {
      if (nDepth > XsdModelLimits.MAX_DEPTH)
      {
        m_bExhausted = true;
        return;
      }
      // Cyclic group reference: stop rather than recurse forever
      if (!aSeen.add (aMG))
        return;
      try
      {
        for (final XsdParticle aSub : aMG.m_aParticles)
        {
          if (aSub.m_aTerm instanceof final XsdModelGroup aInner &&
              aInner.m_eCompositor == EXsdCompositor.ALL &&
              aSub.m_nMin == 1 &&
              aSub.m_nMax == 1)
            _allMembers (aInner, aSeen, aOut, nDepth + 1);
          else
            aOut.add (aSub);
        }
      }
      finally
      {
        aSeen.remove (aMG);
      }
    }

    /**
     * Flatten the member particles of an xs:all: a 1..1 reference to a named
     * group holding another xs:all contributes that group's members directly
     * (XSD 1.1 allows such a reference; the wrapper particle carries no
     * occurrence of its own).
     */
    @NonNull
    private ICommonsList <XsdParticle> _allMembers (@NonNull final XsdModelGroup aMG)
    {
      final ICommonsList <XsdParticle> ret = new CommonsArrayList <> ();
      _allMembers (aMG, new CommonsHashSet <> (), ret, 0);
      return ret;
    }

    /**
     * @return The index of the member that should consume a child with the given
     *         name, or -1. Element members are preferred over wildcards, so a
     *         wildcard alongside named members only picks up what the names do
     *         not cover.
     */
    private int _allPick (@NonNull final ICommonsList <XsdParticle> aMembers,
                          @NonNull final int [] aCounts,
                          @NonNull final XsdName aName)
    {
      for (int i = 0; i < aMembers.size (); i++)
      {
        final XsdParticle aSub = aMembers.get (i);
        if (aSub.m_aTerm instanceof final XsdElementDecl aDecl &&
            _room (aSub, aCounts[i]) &&
            m_aSchema.matchesElementName (aDecl, aName))
          return i;
      }
      for (int i = 0; i < aMembers.size (); i++)
      {
        final XsdParticle aSub = aMembers.get (i);
        if (aSub.m_aTerm instanceof final XsdWildcard aWC && _room (aSub, aCounts[i]) && aWC.matches (aName))
          return i;
      }
      return -1;
    }

    private static boolean _room (@NonNull final XsdParticle aMember, final int nCount)
    {
      return aMember.m_nMax == XsdConstants.UNBOUNDED || nCount < aMember.m_nMax;
    }

    /**
     * Match an xs:all group: each member may appear in any order, up to its own
     * occurrence limits (1.0 caps every member at 0..1; 1.1 allows any bounds,
     * wildcard members, and a group reference to another xs:all). Members are
     * distinguished by name, so a greedy assignment, most specific member
     * first, is enough.
     */
    @NonNull
    private BitSet _all (@NonNull final XsdModelGroup aMG, final int nPos)
    {
      final ICommonsList <XsdParticle> aMembers = _allMembers (aMG);
      final int [] aCounts = new int [aMembers.size ()];
      int nCur = nPos;
      while (nCur < m_aNames.length)
      {
        final int nPicked = _allPick (aMembers, aCounts, m_aNames[nCur]);
        if (nPicked < 0)
        {
          // Interleave open content admits extra children between the members
          // of the all group (open008)
          if (m_aOpen != null && m_aOpen.matches (m_aNames[nCur]))
          {
            nCur++;
            continue;
          }
          break;
        }
        aCounts[nPicked]++;
        nCur++;
      }
      for (int i = 0; i < aMembers.size (); i++)
        if (aCounts[i] < aMembers.get (i).m_nMin)
          return new BitSet ();
      final BitSet ret = new BitSet ();
      ret.set (nCur);
      return ret;
    }

    @NonNull
    private BitSet _group (@NonNull final XsdModelGroup aMG, final int nPos)
    {
      switch (aMG.m_eCompositor)
      {
        case CHOICE:
        {
          final BitSet aOut = new BitSet ();
          for (final XsdParticle aSub : aMG.m_aParticles)
            aOut.or (particle (aSub, nPos));
          return aOut;
        }
        case ALL:
          return _all (aMG, nPos);
        default:
        {
          BitSet aCur = new BitSet ();
          aCur.set (nPos);
          for (final XsdParticle aSub : aMG.m_aParticles)
          {
            final BitSet aNext = new BitSet ();
            for (int p = aCur.nextSetBit (0); p >= 0; p = aCur.nextSetBit (p + 1))
              aNext.or (particle (aSub, p));
            if (aNext.isEmpty ())
              return aNext;
            aCur = aNext;
          }
          return aCur;
        }
      }
    }

    @NonNull
    private BitSet _termOnce (@NonNull final IXsdTerm aTerm, final int nPos)
    {
      if (aTerm instanceof final XsdElementDecl aDecl)
      {
        if (nPos < m_aNames.length && !m_aForceOpen.get (nPos) && m_aSchema.matchesElementName (aDecl, m_aNames[nPos]))
        {
          final BitSet ret = new BitSet ();
          ret.set (nPos + 1);
          return ret;
        }
      }
      else
        if (aTerm instanceof final XsdWildcard aWC)
        {
          if (nPos < m_aNames.length && !m_aForceOpen.get (nPos) && aWC.matches (m_aNames[nPos]))
          {
            final BitSet ret = new BitSet ();
            ret.set (nPos + 1);
            return ret;
          }
        }
        else
          if (aTerm instanceof final XsdModelGroup aMG)
            return _group (aMG, nPos);
      return new BitSet ();
    }

    /**
     * @return The end positions reachable by matching the particle from a start
     *         position.
     */
    @NonNull
    BitSet particle (@NonNull final XsdParticle aParticle, final int nStart)
    {
      final MKey aKey = new MKey (aParticle, nStart);
      final BitSet aMemo = m_aMemo.get (aKey);
      if (aMemo != null)
        return aMemo;
      // A cyclic (invalid) group reference: treat as no match
      if (m_aActive.contains (aKey))
        return new BitSet ();
      if (m_nBudget <= 0 || m_nDepth >= XsdModelLimits.MAX_DEPTH)
      {
        m_bExhausted = true;
        return new BitSet ();
      }
      m_aActive.add (aKey);
      m_nBudget--;
      m_nDepth++;
      try
      {
        // A nullable term (an xs:all or group whose members are all optional)
        // can satisfy an occurrence with zero width, so the minimum required
        // occurrences can be padded with empty matches. Such a particle
        // therefore behaves like min=0 for the reachable end positions.
        final boolean bTermNull = _nullable (aParticle.m_aTerm);
        final BitSet aEnds = new BitSet ();
        if (aParticle.m_nMin == 0 || bTermNull)
          aEnds.or (_clo (nStart));
        BitSet aPositions = _clo (nStart);
        for (int nReps = 1;; nReps++)
        {
          if (aParticle.m_nMax != XsdConstants.UNBOUNDED && nReps > aParticle.m_nMax)
            break;
          // Only advancing matches count, so this bounds the repetition
          if (nReps > m_aNames.length + 1)
            break;
          final BitSet aNext = new BitSet ();
          for (int nPos = aPositions.nextSetBit (0); nPos >= 0; nPos = aPositions.nextSetBit (nPos + 1))
          {
            final BitSet aOnce = _termOnce (aParticle.m_aTerm, nPos);
            for (int e = aOnce.nextSetBit (0); e >= 0; e = aOnce.nextSetBit (e + 1))
              if (e > nPos)
                aNext.set (e);
          }
          if (aNext.isEmpty ())
            break;
          aPositions = _cloSet (aNext);
          if (nReps >= aParticle.m_nMin || bTermNull)
            aEnds.or (aPositions);
        }
        m_aMemo.put (aKey, aEnds);
        return aEnds;
      }
      finally
      {
        m_nDepth--;
        m_aActive.remove (aKey);
      }
    }
  }

  @PresentForCodeCoverage
  private static final XsdContentMatcher INSTANCE = new XsdContentMatcher ();

  private XsdContentMatcher ()
  {}

  /**
   * @param aKids
   *        The element children of an instance element.
   * @return Their expanded names, in order.
   */
  @NonNull
  static XsdName [] namesOf (@NonNull final List <IXdmNode> aKids)
  {
    final XsdName [] ret = new XsdName [aKids.size ()];
    for (int i = 0; i < ret.length; i++)
      ret[i] = XsdName.of (aKids.get (i).getName ());
    return ret;
  }

  /**
   * go-xslt <code>contentMatches</code>: whether the children satisfy the
   * particle. <code>decided</code> is false when the matcher's step budget (or
   * depth bound) was exhausted, meaning the verdict is undetermined rather than
   * "does not match".
   *
   * @param aSchema
   *        The schema (substitution groups).
   * @param aParticle
   *        The effective content model; <code>null</code> for empty content.
   * @param aKids
   *        The element children of the instance element, in order.
   * @return Whether they match, and whether that was decided.
   */
  @NonNull
  static XsdMatchResult contentMatches (@NonNull final XsdSchema aSchema,
                                        @Nullable final XsdParticle aParticle,
                                        @NonNull final List <IXdmNode> aKids)
  {
    if (aParticle == null)
      return new XsdMatchResult (aKids.isEmpty (), true);
    // The matching cost of the position-set matcher scales ~O(P*N^2), so very
    // large instances go to the linear counting matcher instead: it decides
    // every name-deterministic model and returns no verdict for the rest
    if (aKids.size () > MAX_NFA_CHILDREN)
      return XsdBigMatch.countMatch (aSchema, aParticle, aKids);
    // A name-deterministic model goes to the linear counting matcher first.
    // Only its positive verdict is taken (that is a witness parse, so the exact
    // matcher below would agree), while a negative one still runs the exact
    // matcher. It allocates several sets per particle per element; for the
    // common valid, deterministic case this skips all of it.
    if (XsdBigMatch.particleDeterministic (aSchema, aParticle))
    {
      final XsdBigMatch.Run aRun = new XsdBigMatch.Run (aSchema, aKids);
      final int nPos = aRun.particle (aParticle, 0);
      if (nPos >= 0 && nPos == aKids.size ())
        return new XsdMatchResult (true, true);
    }
    final Matcher aMatcher = new Matcher (aSchema, namesOf (aKids), null, new BitSet (), new BitSet ());
    final boolean bMatched = aMatcher.particle (aParticle, 0).get (aKids.size ());
    return new XsdMatchResult (bMatched, !aMatcher.isExhausted ());
  }

  /**
   * go-xslt <code>contentMatchesOpen</code>: {@link #contentMatches} with XSD
   * 1.1 open content. Interleave lets wildcard-admitted children appear
   * anywhere (position closure), suffix only after a complete match of the
   * model.
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The effective content model.
   * @param aKids
   *        The element children.
   * @param aOpenContent
   *        The effective open content; <code>null</code> for none.
   * @return Whether they match, and whether that was decided.
   */
  @NonNull
  static XsdMatchResult contentMatchesOpen (@NonNull final XsdSchema aSchema,
                                            @Nullable final XsdParticle aParticle,
                                            @NonNull final List <IXdmNode> aKids,
                                            @Nullable final XsdOpenContent aOpenContent)
  {
    if (aOpenContent == null || aOpenContent.m_aWildcard == null)
      return contentMatches (aSchema, aParticle, aKids);
    if (aKids.size () > MAX_NFA_CHILDREN)
      return new XsdMatchResult (false, false);
    final XsdWildcard aWC = aOpenContent.m_aWildcard;
    final XsdName [] aNames = namesOf (aKids);
    final Matcher aMatcher = new Matcher (aSchema,
                                          aNames,
                                          XsdOpenContent.MODE_INTERLEAVE.equals (aOpenContent.m_sMode) ? aWC : null,
                                          new BitSet (),
                                          new BitSet ());
    final BitSet aEnds;
    if (aParticle == null)
    {
      final BitSet aStart = new BitSet ();
      aStart.set (0);
      aEnds = aMatcher._cloSet (aStart);
    }
    else
      aEnds = aMatcher.particle (aParticle, 0);
    if (aEnds.get (aKids.size ()))
      return new XsdMatchResult (true, !aMatcher.isExhausted ());
    if (XsdOpenContent.MODE_SUFFIX.equals (aOpenContent.m_sMode))
    {
      for (int nEnd = aEnds.nextSetBit (0); nEnd >= 0; nEnd = aEnds.nextSetBit (nEnd + 1))
      {
        boolean bAll = true;
        for (int i = nEnd; i < aNames.length; i++)
          if (!aWC.matches (aNames[i]))
          {
            bAll = false;
            break;
          }
        if (bAll)
          return new XsdMatchResult (true, !aMatcher.isExhausted ());
      }
    }
    return new XsdMatchResult (false, !aMatcher.isExhausted ());
  }

  private static boolean _try (@NonNull final XsdSchema aSchema,
                               @NonNull final XsdParticle aParticle,
                               @NonNull final XsdName [] aNames,
                               @NonNull final XsdWildcard aOpen,
                               @NonNull final BitSet aForceOpen,
                               @NonNull final BitSet aForceModel)
  {
    final Matcher aMatcher = new Matcher (aSchema, aNames, aOpen, aForceOpen, aForceModel);
    return aMatcher.particle (aParticle, 0).get (aNames.length);
  }

  /**
   * go-xslt <code>greedyOpenAttribution</code>: decide, left to right, which
   * children the model takes and which fall to the interleave open wildcard. At
   * each index the model consumes the child if a full parse with all earlier
   * pins still exists (open025: a repeatable model element takes the repeat;
   * open047: an earlier same-named child pinned to the model leaves the later
   * one to the wildcard).
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The effective content model.
   * @param aKids
   *        The element children.
   * @param aOpenContent
   *        The open content.
   * @return The indices of the children attributed to the open content;
   *         <code>null</code> when not applicable (no interleave wildcard, no
   *         model, too many children), like go-xslt's nil map.
   */
  @Nullable
  static BitSet greedyOpenAttribution (@NonNull final XsdSchema aSchema,
                                       @Nullable final XsdParticle aParticle,
                                       @NonNull final List <IXdmNode> aKids,
                                       @NonNull final XsdOpenContent aOpenContent)
  {
    if (aOpenContent.m_aWildcard == null ||
        !XsdOpenContent.MODE_INTERLEAVE.equals (aOpenContent.m_sMode) ||
        aParticle == null ||
        aKids.size () > MAX_NFA_CHILDREN)
      return null;
    final BitSet aOpen = new BitSet ();
    final XsdName [] aNames = namesOf (aKids);
    final BitSet aForceOpen = new BitSet ();
    final BitSet aForceModel = new BitSet ();
    for (int i = 0; i < aNames.length; i++)
    {
      aForceModel.set (i);
      // The model takes it
      if (_try (aSchema, aParticle, aNames, aOpenContent.m_aWildcard, aForceOpen, aForceModel))
        continue;
      aForceModel.clear (i);
      aForceOpen.set (i);
      if (!_try (aSchema, aParticle, aNames, aOpenContent.m_aWildcard, aForceOpen, aForceModel))
      {
        // Neither pin completes a parse: leave the rest unpinned; the caller's
        // overall match already decided validity
        aForceOpen.clear (i);
        break;
      }
      aOpen.set (i);
    }
    return aOpen;
  }
}
