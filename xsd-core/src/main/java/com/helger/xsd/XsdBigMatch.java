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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Large-instance counting matcher (go-xslt <code>bigmatch.go</code>). The
 * memoised position-set matcher of {@link XsdContentMatcher} costs roughly
 * O(P*N^2) and gives no verdict above 600 children; this is a linear greedy
 * interpreter with occurrence counters. It is sound only for name-deterministic
 * models, so a static pre-scan declines everything it cannot prove (xs:all,
 * cyclic group references, overlapping choice branches, wildcard against
 * element competition); a declined model gets no verdict from here.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdBigMatch
{
  /** The names and wildcards an iteration of a particle can start with. */
  private static final class First
  {
    final ICommonsSet <XsdName> m_aNames = new CommonsHashSet <> ();
    final ICommonsList <XsdWildcard> m_aWilds = new CommonsArrayList <> ();
  }

  /**
   * The static pre-scan (go-xslt <code>bigDeterministic</code>): it accepts
   * only models the greedy interpreter is provably exact on.
   */
  private static final class Scan
  {
    private final XsdSchema m_aSchema;
    private final ICommonsList <XsdElementDecl> m_aElems = new CommonsArrayList <> ();
    private final ICommonsList <XsdWildcard> m_aWilds = new CommonsArrayList <> ();
    private final Map <XsdElementDecl, ICommonsSet <XsdName>> m_aCache = new CommonsHashMap <> ();
    private final Set <XsdModelGroup> m_aSeen = new CommonsHashSet <> ();
    private boolean m_bOK = true;

    Scan (@NonNull final XsdSchema aSchema)
    {
      m_aSchema = aSchema;
    }

    private void _collectFirst (@Nullable final XsdParticle aParticle,
                                @NonNull final First aFirst,
                                @NonNull final Set <XsdModelGroup> aSeen,
                                final int nDepth)
    {
      if (aParticle == null || aParticle.m_nMax == 0)
        return;
      if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
        aFirst.m_aNames.addAll (XsdUpa.upaMatchNames (m_aSchema, aDecl, m_aCache));
      else
        if (aParticle.m_aTerm instanceof final XsdWildcard aWC)
          aFirst.m_aWilds.add (aWC);
        else
          if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
          {
            XsdModelLimits.checkDepth (nDepth);
            if (!aSeen.add (aMG))
              return;
            try
            {
              if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
              {
                for (final XsdParticle aBranch : aMG.m_aParticles)
                  _collectFirst (aBranch, aFirst, aSeen, nDepth + 1);
                return;
              }
              // Sequence: the nullable prefix
              for (final XsdParticle aSub : aMG.m_aParticles)
              {
                _collectFirst (aSub, aFirst, aSeen, nDepth + 1);
                if (!XsdUpa.upaNullable (aSub))
                  return;
              }
            }
            finally
            {
              aSeen.remove (aMG);
            }
          }
    }

    private boolean _choiceBranchesDisjoint (@NonNull final XsdModelGroup aMG, final int nDepth)
    {
      final ICommonsList <First> aFirsts = new CommonsArrayList <> ();
      for (final XsdParticle aBranch : aMG.m_aParticles)
      {
        final First aFirst = new First ();
        _collectFirst (aBranch, aFirst, new CommonsHashSet <> (), nDepth);
        aFirsts.add (aFirst);
      }
      for (int i = 0; i < aFirsts.size (); i++)
      {
        final First aI = aFirsts.get (i);
        for (int j = i + 1; j < aFirsts.size (); j++)
        {
          final First aJ = aFirsts.get (j);
          for (final XsdName aName : aI.m_aNames)
            if (aJ.m_aNames.contains (aName))
              return false;
          // Wildcard against wildcard first overlap: stay conservative
          if (aI.m_aWilds.isNotEmpty () && aJ.m_aWilds.isNotEmpty ())
            return false;
          for (final XsdWildcard aWC : aI.m_aWilds)
            for (final XsdName aName : aJ.m_aNames)
              if (aWC.matches (aName))
                return false;
          for (final XsdWildcard aWC : aJ.m_aWilds)
            for (final XsdName aName : aI.m_aNames)
              if (aWC.matches (aName))
                return false;
        }
      }
      return true;
    }

    private void _walk (@Nullable final XsdParticle aParticle, final int nDepth)
    {
      if (aParticle == null || aParticle.m_nMax == 0 || !m_bOK)
        return;
      if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
        m_aElems.add (aDecl);
      else
        if (aParticle.m_aTerm instanceof final XsdWildcard aWC)
          m_aWilds.add (aWC);
        else
          if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
          {
            XsdModelLimits.checkDepth (nDepth);
            if (aMG.m_eCompositor == EXsdCompositor.ALL)
            {
              m_bOK = false;
              return;
            }
            // Cyclic group reference: not provably deterministic
            if (!m_aSeen.add (aMG))
            {
              m_bOK = false;
              return;
            }
            if (aMG.m_eCompositor == EXsdCompositor.CHOICE && !_choiceBranchesDisjoint (aMG, nDepth + 1))
            {
              m_bOK = false;
              return;
            }
            for (final XsdParticle aSub : aMG.m_aParticles)
              _walk (aSub, nDepth + 1);
            m_aSeen.remove (aMG);
          }
    }

    boolean scan (@NonNull final XsdParticle aRoot)
    {
      _walk (aRoot, 0);
      if (!m_bOK)
        return false;
      for (final XsdWildcard aWC : m_aWilds)
        for (final XsdElementDecl aDecl : m_aElems)
          for (final XsdName aName : XsdUpa.upaMatchNames (m_aSchema, aDecl, m_aCache))
            if (aWC.matches (aName))
              return false;
      return true;
    }
  }

  /**
   * One greedy counting run (go-xslt <code>bigRun</code>); unsure records that
   * an invalid outcome might be an artefact of the greedy iteration allocation.
   */
  static final class Run
  {
    private final XsdSchema m_aSchema;
    private final XsdName [] m_aNames;
    private boolean m_bUnsure;

    Run (@NonNull final XsdSchema aSchema, @NonNull final List <IXdmNode> aKids)
    {
      m_aSchema = aSchema;
      m_aNames = XsdContentMatcher.namesOf (aKids);
    }

    boolean isUnsure ()
    {
      return m_bUnsure;
    }

    /**
     * Run one iteration of a term at a position.
     *
     * @return The new position, or -1 if it does not match.
     */
    private int _term (@NonNull final IXsdTerm aTerm, final int nPos)
    {
      if (aTerm instanceof final XsdElementDecl aDecl)
        return nPos < m_aNames.length && m_aSchema.matchesElementName (aDecl, m_aNames[nPos]) ? nPos + 1 : -1;
      if (aTerm instanceof final XsdWildcard aWC)
        return nPos < m_aNames.length && aWC.matches (m_aNames[nPos]) ? nPos + 1 : -1;
      if (aTerm instanceof final XsdModelGroup aMG)
      {
        if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
        {
          // The pre-scan guarantees at most one branch can start with the
          // current name
          if (nPos < m_aNames.length)
          {
            final XsdName aName = m_aNames[nPos];
            for (final XsdParticle aBranch : aMG.m_aParticles)
              if (aBranch.m_nMax != 0 && firstAcceptsTerm (m_aSchema, aBranch.m_aTerm, aName, 0))
                return particle (aBranch, nPos);
          }
          // No branch starts here: the choice matches the empty sequence if
          // some branch can
          for (final XsdParticle aBranch : aMG.m_aParticles)
            if (aBranch.m_nMin == 0 || (aBranch.m_nMax != 0 && nullableTerm (aBranch.m_aTerm)))
              return nPos;
          return -1;
        }
        // Sequence (xs:all is declined by the pre-scan)
        int nCur = nPos;
        for (final XsdParticle aSub : aMG.m_aParticles)
        {
          nCur = particle (aSub, nCur);
          if (nCur < 0)
            return -1;
        }
        return nCur;
      }
      return -1;
    }

    /**
     * Consume the children from a position greedily for a particle: while
     * another iteration of the term can start with the current child's name
     * (and the counter allows), run it; a started iteration that fails is a
     * definitive mismatch, deterministic models commit on the first symbol.
     *
     * @return The new position, or -1 if it does not match.
     */
    int particle (@Nullable final XsdParticle aParticle, final int nStartPos)
    {
      // A maxOccurs=0 particle matches nothing
      if (aParticle == null || aParticle.m_nMax == 0)
        return nStartPos;
      int nPos = nStartPos;
      int nCount = 0;
      while (true)
      {
        if (aParticle.m_nMax != XsdConstants.UNBOUNDED && nCount >= aParticle.m_nMax)
        {
          // Cut off at maxOccurs while the next child could still start another
          // iteration: with max > 1 a different allocation could have left
          // room (max == 1 has nothing to reallocate, UPA guarantees no other
          // position wants the name, particlesZ036_a)
          if (aParticle.m_nMax > 1 &&
              nPos < m_aNames.length &&
              firstAcceptsTerm (m_aSchema, aParticle.m_aTerm, m_aNames[nPos], 0))
            m_bUnsure = true;
          break;
        }
        if (nPos >= m_aNames.length || !firstAcceptsTerm (m_aSchema, aParticle.m_aTerm, m_aNames[nPos], 0))
          break;
        final int nNew = _term (aParticle.m_aTerm, nPos);
        if (nNew < 0)
          return -1;
        // A nullable term consumed nothing; more iterations are pointless
        if (nNew == nPos)
          break;
        nPos = nNew;
        nCount++;
      }
      if (nCount < aParticle.m_nMin && !nullableTerm (aParticle.m_aTerm))
      {
        // Shorter earlier iterations might have fed more
        if (nCount >= 1)
          m_bUnsure = true;
        // minOccurs unsatisfied and no vacuous iterations possible
        return -1;
      }
      return nPos;
    }
  }

  @PresentForCodeCoverage
  private static final XsdBigMatch INSTANCE = new XsdBigMatch ();

  private XsdBigMatch ()
  {}

  private static boolean _bigDeterministic (@NonNull final XsdSchema aSchema, @NonNull final XsdParticle aParticle)
  {
    try
    {
      return new Scan (aSchema).scan (aParticle);
    }
    catch (final XsdUnsupportedException ex)
    {
      // Nested too deeply to prove anything: decline
      return false;
    }
  }

  /**
   * go-xslt <code>bigNullableTerm</code>.
   *
   * @param aTerm
   *        The term.
   * @return <code>true</code> if one iteration of the term can match the empty
   *         sequence.
   */
  static boolean nullableTerm (@NonNull final IXsdTerm aTerm)
  {
    return XsdUpa.upaNullable (new XsdParticle (1, 1, aTerm));
  }

  /**
   * go-xslt <code>firstAcceptsTerm</code>: whether an iteration of a term can
   * start by consuming an element with the given name.
   *
   * @param aSchema
   *        The schema.
   * @param aTerm
   *        The term.
   * @param aName
   *        The element name.
   * @param nDepth
   *        Current recursion depth (0 at the top).
   * @return <code>true</code> if it can.
   */
  static boolean firstAcceptsTerm (@NonNull final XsdSchema aSchema,
                                   @NonNull final IXsdTerm aTerm,
                                   @NonNull final XsdName aName,
                                   final int nDepth)
  {
    if (aTerm instanceof final XsdElementDecl aDecl)
      return aSchema.matchesElementName (aDecl, aName);
    if (aTerm instanceof final XsdWildcard aWC)
      return aWC.matches (aName);
    if (aTerm instanceof final XsdModelGroup aMG)
    {
      XsdModelLimits.checkDepth (nDepth);
      if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
      {
        for (final XsdParticle aBranch : aMG.m_aParticles)
          if (aBranch.m_nMax != 0 && firstAcceptsTerm (aSchema, aBranch.m_aTerm, aName, nDepth + 1))
            return true;
        return false;
      }
      // Sequence: the nullable prefix
      for (final XsdParticle aSub : aMG.m_aParticles)
      {
        if (aSub.m_nMax == 0)
          continue;
        if (firstAcceptsTerm (aSchema, aSub.m_aTerm, aName, nDepth + 1))
          return true;
        if (!XsdUpa.upaNullable (aSub))
          return false;
      }
      return false;
    }
    return false;
  }

  /**
   * go-xslt <code>countMatch</code>. It never returns a guessed verdict:
   * <code>decided == false</code> means "use another engine or give no
   * verdict". A match is always trustworthy (the greedy run found a witness
   * parse). A mismatch is trustworthy only when no counter-splitting ambiguity
   * was met on the way: UPA cannot forbid a position competing with itself
   * across loop iterations, so whenever an iteration was cut at maxOccurs with
   * the next child still acceptable, or a repeat failed its minOccurs after
   * consuming something, a different iteration allocation might have succeeded
   * and the run reports undecided.
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The content model.
   * @param aKids
   *        The element children.
   * @return Whether they match, and whether the counting matcher could decide.
   */
  @NonNull
  static XsdMatchResult countMatch (@NonNull final XsdSchema aSchema,
                                    @NonNull final XsdParticle aParticle,
                                    @NonNull final List <IXdmNode> aKids)
  {
    if (!_bigDeterministic (aSchema, aParticle))
      return new XsdMatchResult (false, false);
    final Run aRun = new Run (aSchema, aKids);
    final int nPos = aRun.particle (aParticle, 0);
    final boolean bMatched = nPos >= 0 && nPos == aKids.size ();
    if (!bMatched && aRun.isUnsure ())
      return new XsdMatchResult (false, false);
    return new XsdMatchResult (bMatched, true);
  }

  /**
   * go-xslt <code>particleDeterministic</code>: the pre-scan memoised on the
   * particle (its answer depends only on the particle tree and the substitution
   * groups, both immutable after compile; memoised in
   * {@link XsdParticle#m_aDet}).
   *
   * @param aSchema
   *        The schema.
   * @param aParticle
   *        The content model.
   * @return <code>true</code> if the counting matcher may be used.
   */
  static boolean particleDeterministic (@NonNull final XsdSchema aSchema, @NonNull final XsdParticle aParticle)
  {
    switch (aParticle.m_aDet.get ())
    {
      case 1:
        return true;
      case 2:
        return false;
      default:
        break;
    }
    final boolean bDeterministic = _bigDeterministic (aSchema, aParticle);
    aParticle.m_aDet.set (bDeterministic ? 1 : 2);
    return bDeterministic;
  }
}
