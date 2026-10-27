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

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.XdmNode;

/**
 * Explaining a content-model mismatch (go-xslt <code>explain.go</code>).
 * <p>
 * The matcher ({@link XsdContentMatcher}) decides whether children satisfy a
 * content model; it cannot say what is wrong. When it has said "no" (and only
 * then), this class replays the children through a small position automaton
 * built from the same particle tree and names the defects one by one: a
 * required element that is missing, an element that occurs more often than
 * allowed, one that is not allowed at all, one that stands in the wrong place.
 * The repair it assumes is always the cheapest one - fewest missing elements (a
 * shortest path through the automaton) - so that independent defects are
 * reported independently.
 * <p>
 * Nothing here decides validity. A model it cannot express (<code>xs:all</code>,
 * open content, huge occurrence bounds) yields no explanation, and the caller
 * keeps the generic diagnostic. Every walk is iterative or bounded: the group
 * nesting that is followed is limited to {@link #MAX_GROUP_DEPTH}.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdExplain
{
  /** Beyond this many automaton states the model is not explained. */
  private static final int NFA_MAX_STATES = 60000;
  /** The largest min / (max-min) that is unrolled copy by copy. */
  private static final int NFA_MAX_UNROLL = 120;
  /** Defects reported per parent. */
  private static final int NFA_MAX_ERRORS = 100;
  /** The deepest group nesting the automaton follows. */
  private static final int MAX_GROUP_DEPTH = 100;
  /** The most children explained. */
  private static final int MAX_KIDS = 5000;
  private static final int INF = Integer.MAX_VALUE >> 1;

  /** The kinds of {@link XsdError#kind()}. */
  static final String KIND_MISSING = "missing";
  static final String KIND_TOO_MANY = "too-many";
  static final String KIND_UNEXPECTED = "unexpected";
  static final String KIND_ORDER = "order";

  @PresentForCodeCoverage
  private static final XsdExplain INSTANCE = new XsdExplain ();

  private XsdExplain ()
  {}

  /** An enclosing group together with the particle that holds it (the group's own occurrence bounds). */
  private record AncEntry (@NonNull XsdModelGroup group, @NonNull XsdParticle particle)
  {}

  /** An edge label: an element declaration or a wildcard with the context it stands in. */
  private static final class Label
  {
    final XsdElementDecl m_aDecl;
    final XsdWildcard m_aWildcard;
    final XsdParticle m_aPart;
    final ICommonsList <AncEntry> m_aAnc;

    Label (@Nullable final XsdElementDecl aDecl,
           @Nullable final XsdWildcard aWildcard,
           @NonNull final XsdParticle aPart,
           @NonNull final ICommonsList <AncEntry> aAnc)
    {
      m_aDecl = aDecl;
      m_aWildcard = aWildcard;
      m_aPart = aPart;
      m_aAnc = aAnc;
    }
  }

  private record Edge (int to, @NonNull Label label)
  {}

  /** A growable int array. */
  private static final class IntList
  {
    private int [] m_aData = new int [4];
    private int m_nSize;

    void add (final int n)
    {
      if (m_nSize == m_aData.length)
        m_aData = Arrays.copyOf (m_aData, m_nSize * 2);
      m_aData[m_nSize++] = n;
    }

    int get (final int i)
    {
      return m_aData[i];
    }

    int size ()
    {
      return m_nSize;
    }
  }

  /** The position automaton: states, epsilon edges, labelled edges and both reversed. */
  private static final class Nfa
  {
    final ICommonsList <IntList> m_aEps = new CommonsArrayList <> ();
    final ICommonsList <ICommonsList <Edge>> m_aOut = new CommonsArrayList <> ();
    final ICommonsList <IntList> m_aRevEps = new CommonsArrayList <> ();
    /** Reverse labelled edges: source states. */
    final ICommonsList <IntList> m_aRevOut = new CommonsArrayList <> ();
    int m_nStart;
    int m_nAccept;

    int size ()
    {
      return m_aEps.size ();
    }

    @NonNull
    BitSet closure (@NonNull final BitSet aSet)
    {
      final BitSet ret = (BitSet) aSet.clone ();
      int [] aStack = new int [16];
      int nSP = 0;
      for (int q = aSet.nextSetBit (0); q >= 0; q = aSet.nextSetBit (q + 1))
      {
        if (nSP == aStack.length)
          aStack = Arrays.copyOf (aStack, nSP * 2);
        aStack[nSP++] = q;
      }
      while (nSP > 0)
      {
        final IntList aEps = m_aEps.get (aStack[--nSP]);
        for (int i = 0; i < aEps.size (); i++)
        {
          final int e = aEps.get (i);
          if (!ret.get (e))
          {
            ret.set (e);
            if (nSP == aStack.length)
              aStack = Arrays.copyOf (aStack, nSP * 2);
            aStack[nSP++] = e;
          }
        }
      }
      return ret;
    }
  }

  /** Builds the automaton of a particle with its occurrence bounds, unrolled. */
  private static final class NfaBuilder
  {
    final Nfa m_aNfa = new Nfa ();
    ICommonsList <AncEntry> m_aAnc = new CommonsArrayList <> ();
    int m_nDepth;
    boolean m_bFail;

    int state ()
    {
      if (m_aNfa.size () >= NFA_MAX_STATES)
      {
        m_bFail = true;
        return 0;
      }
      m_aNfa.m_aEps.add (new IntList ());
      m_aNfa.m_aOut.add (new CommonsArrayList <> ());
      m_aNfa.m_aRevEps.add (new IntList ());
      m_aNfa.m_aRevOut.add (new IntList ());
      return m_aNfa.size () - 1;
    }

    void epsEdge (final int nFrom, final int nTo)
    {
      if (m_bFail)
        return;
      m_aNfa.m_aEps.get (nFrom).add (nTo);
      m_aNfa.m_aRevEps.get (nTo).add (nFrom);
    }

    void edge (final int nFrom, final int nTo, @NonNull final Label aLabel)
    {
      if (m_bFail)
        return;
      m_aNfa.m_aOut.get (nFrom).add (new Edge (nTo, aLabel));
      m_aNfa.m_aRevOut.get (nTo).add (nFrom);
    }

    /** @return in and out state of the fragment of p with its occurrence bounds */
    @NonNull
    int [] particle (@NonNull final XsdParticle aParticle)
    {
      final int nIn = state ();
      int nCur = nIn;
      if (aParticle.m_nMin > NFA_MAX_UNROLL)
      {
        m_bFail = true;
        return new int [] { nIn, nIn };
      }
      for (int i = 0; i < aParticle.m_nMin && !m_bFail; i++)
      {
        final int [] aTerm = term (aParticle);
        epsEdge (nCur, aTerm[0]);
        nCur = aTerm[1];
      }
      if (aParticle.m_nMax == XsdConstants.UNBOUNDED)
      {
        final int nHub = state ();
        epsEdge (nCur, nHub);
        final int [] aTerm = term (aParticle);
        epsEdge (nHub, aTerm[0]);
        epsEdge (aTerm[1], nHub);
        return new int [] { nIn, nHub };
      }
      final long nOpt = (long) aParticle.m_nMax - aParticle.m_nMin;
      if (nOpt > NFA_MAX_UNROLL)
      {
        m_bFail = true;
        return new int [] { nIn, nIn };
      }
      final int nExit = state ();
      for (int i = 0; i < nOpt && !m_bFail; i++)
      {
        epsEdge (nCur, nExit);
        final int [] aTerm = term (aParticle);
        epsEdge (nCur, aTerm[0]);
        nCur = aTerm[1];
      }
      epsEdge (nCur, nExit);
      return new int [] { nIn, nExit };
    }

    /** @return in and out state of one occurrence of the particle's term */
    @NonNull
    int [] term (@NonNull final XsdParticle aParticle)
    {
      final int nIn = state ();
      final int nOut = state ();
      final int [] ret = new int [] { nIn, nOut };
      if (m_bFail)
        return ret;
      if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
        edge (nIn, nOut, new Label (aDecl, null, aParticle, m_aAnc));
      else
        if (aParticle.m_aTerm instanceof final XsdWildcard aWC)
          edge (nIn, nOut, new Label (null, aWC, aParticle, m_aAnc));
        else
          if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
          {
            if (aMG.m_eCompositor == EXsdCompositor.ALL || m_nDepth > MAX_GROUP_DEPTH)
            {
              m_bFail = true;
              return ret;
            }
            final ICommonsList <AncEntry> aSaved = m_aAnc;
            final ICommonsList <AncEntry> aNew = new CommonsArrayList <> (aSaved.size () + 1);
            aNew.add (new AncEntry (aMG, aParticle));
            aNew.addAll (aSaved);
            m_aAnc = aNew;
            m_nDepth++;
            if (aMG.m_eCompositor == EXsdCompositor.CHOICE)
            {
              for (final XsdParticle aSub : aMG.m_aParticles)
              {
                final int [] aFrag = particle (aSub);
                epsEdge (nIn, aFrag[0]);
                epsEdge (aFrag[1], nOut);
              }
            }
            else
            {
              int nCur = nIn;
              for (final XsdParticle aSub : aMG.m_aParticles)
              {
                final int [] aFrag = particle (aSub);
                epsEdge (nCur, aFrag[0]);
                nCur = aFrag[1];
              }
              epsEdge (nCur, nOut);
            }
            m_nDepth--;
            m_aAnc = aSaved;
          }
          else
            m_bFail = true;
      return ret;
    }
  }

  @Nullable
  private static Nfa _buildNfa (@NonNull final XsdParticle aParticle)
  {
    final NfaBuilder aBuilder = new NfaBuilder ();
    final int [] aFrag = aBuilder.particle (aParticle);
    if (aBuilder.m_bFail)
      return null;
    aBuilder.m_aNfa.m_nStart = aFrag[0];
    aBuilder.m_aNfa.m_nAccept = aFrag[1];
    return aBuilder.m_aNfa;
  }

  private static boolean _labelMatches (@NonNull final XsdSchema aSchema,
                                        @NonNull final Label aLabel,
                                        @NonNull final XsdName aName)
  {
    if (aLabel.m_aDecl != null)
      return aSchema.matchesElementName (aLabel.m_aDecl, aName);
    return aLabel.m_aWildcard.matches (aName);
  }

  /** The new (closed) state set after a child, and the labels the child was matched by. */
  private record StepResult (@NonNull BitSet next, @NonNull ICommonsList <Label> labels)
  {}

  /** Consume one child from the state set cur. */
  @NonNull
  private static StepResult _step (@NonNull final XsdSchema aSchema,
                                   @NonNull final Nfa aNfa,
                                   @NonNull final BitSet aCur,
                                   @NonNull final XsdName aName)
  {
    final BitSet aNext = new BitSet ();
    final ICommonsList <Label> aLabels = new CommonsArrayList <> ();
    for (int q = aCur.nextSetBit (0); q >= 0; q = aCur.nextSetBit (q + 1))
      for (final Edge aEdge : aNfa.m_aOut.get (q))
        if (_labelMatches (aSchema, aEdge.label (), aName))
        {
          aNext.set (aEdge.to ());
          aLabels.add (aEdge.label ());
        }
    return new StepResult (aNfa.closure (aNext), aLabels);
  }

  /**
   * For every state, the fewest labelled edges (elements that would have to be
   * inserted) on a path to a goal state; epsilon edges are free.
   */
  @NonNull
  private static int [] _distToGoal (@NonNull final Nfa aNfa, @NonNull final IntPredicate aGoal)
  {
    final int [] aDist = new int [aNfa.size ()];
    Arrays.fill (aDist, INF);
    // 0-1 BFS on the reverse graph, as two alternating frontiers
    IntList aCur = new IntList ();
    IntList aNext = new IntList ();
    for (int q = 0; q < aDist.length; q++)
      if (aGoal.test (q))
      {
        aDist[q] = 0;
        aCur.add (q);
      }
    for (int d = 0; aCur.size () > 0; d++)
    {
      for (int i = 0; i < aCur.size (); i++) // aCur grows while epsilon edges are followed
      {
        final int q = aCur.get (i);
        if (aDist[q] != d)
          continue;
        final IntList aRevEps = aNfa.m_aRevEps.get (q);
        for (int j = 0; j < aRevEps.size (); j++)
        {
          final int f = aRevEps.get (j);
          if (aDist[f] > d)
          {
            aDist[f] = d;
            aCur.add (f);
          }
        }
        final IntList aRevOut = aNfa.m_aRevOut.get (q);
        for (int j = 0; j < aRevOut.size (); j++)
        {
          final int f = aRevOut.get (j);
          if (aDist[f] > d + 1)
          {
            aDist[f] = d + 1;
            aNext.add (f);
          }
        }
      }
      aCur = aNext;
      aNext = new IntList ();
    }
    return aDist;
  }

  /** The alternative labels at each step of a cheapest insertion path, and the closed state set reached. */
  private record Insertions (@NonNull ICommonsList <ICommonsList <Label>> steps, @NonNull BitSet reached)
  {}

  /**
   * Walk a cheapest insertion path from cur (closed) to a goal state given
   * dist.
   */
  @NonNull
  private static Insertions _insertions (@NonNull final Nfa aNfa, @NonNull final BitSet aCurIn, @NonNull final int [] aDist)
  {
    BitSet aCur = aCurIn;
    int d = INF;
    for (int q = aCur.nextSetBit (0); q >= 0; q = aCur.nextSetBit (q + 1))
      if (aDist[q] < d)
        d = aDist[q];
    final ICommonsList <ICommonsList <Label>> aSteps = new CommonsArrayList <> ();
    if (d >= INF)
      return new Insertions (aSteps, aCur);
    for (; d > 0; d--)
    {
      final ICommonsList <Label> aAlts = new CommonsArrayList <> ();
      final BitSet aTargets = new BitSet ();
      for (int q = aCur.nextSetBit (0); q >= 0; q = aCur.nextSetBit (q + 1))
      {
        if (aDist[q] != d)
          continue;
        for (final Edge aEdge : aNfa.m_aOut.get (q))
          if (aDist[aEdge.to ()] == d - 1)
          {
            aAlts.add (aEdge.label ());
            aTargets.set (aEdge.to ());
          }
      }
      aSteps.add (aAlts);
      aCur = aNfa.closure (aTargets);
    }
    return new Insertions (aSteps, aCur);
  }

  /** One finding of {@link #_explainContent}. */
  private static final class CardDefect
  {
    final String m_sKind;
    final XdmNode m_aKid;
    /** missing: the alternatives; order: what was expected */
    final ICommonsList <Label> m_aAlts;
    /** too-many: the label the earlier occurrences used */
    final Label m_aPrev;
    /** too-many: the group that occurs more often than allowed */
    final AncEntry m_aGroup;

    CardDefect (@NonNull final String sKind,
                @Nullable final XdmNode aKid,
                @Nullable final ICommonsList <Label> aAlts,
                @Nullable final Label aPrev,
                @Nullable final AncEntry aGroup)
    {
      m_sKind = sKind;
      m_aKid = aKid;
      m_aAlts = aAlts;
      m_aPrev = aPrev;
      m_aGroup = aGroup;
    }
  }

  /** The labels that could be consumed next from cur. */
  @NonNull
  private static ICommonsList <Label> _expectedFrom (@NonNull final Nfa aNfa, @NonNull final BitSet aCur)
  {
    final ICommonsList <Label> ret = new CommonsArrayList <> ();
    final ICommonsSet <XsdElementDecl> aSeen = new CommonsHashSet <> ();
    for (int q = aCur.nextSetBit (0); q >= 0; q = aCur.nextSetBit (q + 1))
      for (final Edge aEdge : aNfa.m_aOut.get (q))
      {
        if (aEdge.label ().m_aDecl != null && !aSeen.add (aEdge.label ().m_aDecl))
          continue;
        ret.add (aEdge.label ());
      }
    return ret;
  }

  /**
   * Find the enclosing group of the child just consumed that has used up its
   * occurrences although the next child would be valid as the start of one more
   * iteration of it - a second alternative of a choice that may occur once,
   * say. That child is "too many" of the group, not out of order.
   */
  @Nullable
  private static AncEntry _exhaustedGroup (@NonNull final XsdSchema aSchema,
                                           @NonNull final XsdParticle aRoot,
                                           @NonNull final ICommonsList <Label> aLast,
                                           @NonNull final XsdName aName)
  {
    for (final Label aLabel : aLast)
      for (final AncEntry aAnc : aLabel.m_aAnc)
      {
        if (aAnc.particle () == aRoot || aAnc.particle ().m_nMax == XsdConstants.UNBOUNDED)
          continue;
        final Nfa aNfa = _buildNfa (new XsdParticle (1, 1, aAnc.group ()));
        if (aNfa == null)
          continue;
        final BitSet aStart = new BitSet ();
        aStart.set (aNfa.m_nStart);
        if (!_step (aSchema, aNfa, aNfa.closure (aStart), aName).next ().isEmpty ())
          return aAnc;
      }
    return null;
  }

  /**
   * Name what keeps kids from satisfying p.
   *
   * @return the defects, or <code>null</code> when it cannot (an unsupported
   *         model, or the automaton accepts what the matcher refused - in both
   *         cases the caller reports the generic mismatch)
   */
  @Nullable
  private static ICommonsList <CardDefect> _explainContent (@NonNull final XsdSchema aSchema,
                                                            @Nullable final XsdParticle aParticle,
                                                            @NonNull final ICommonsList <XdmNode> aKids)
  {
    if (aParticle == null || aKids.size () > MAX_KIDS)
      return null;
    final Nfa aNfa = _buildNfa (aParticle);
    if (aNfa == null)
      return null;
    final ICommonsList <CardDefect> aDefects = new CommonsArrayList <> ();
    final ICommonsSet <XsdElementDecl> aDeclared = new CommonsHashSet <> ();
    for (final ICommonsList <Edge> aEdges : aNfa.m_aOut)
      for (final Edge aEdge : aEdges)
        if (aEdge.label ().m_aDecl != null)
          aDeclared.add (aEdge.label ().m_aDecl);
    final BitSet aStart = new BitSet ();
    aStart.set (aNfa.m_nStart);
    BitSet aCur = aNfa.closure (aStart);
    final ICommonsList <Label> aConsumed = new CommonsArrayList <> ();
    ICommonsList <Label> aLastLabels = new CommonsArrayList <> (); // the labels the previous child was matched by
    // elements reported as misplaced, expected to turn up later
    final ICommonsMap <XsdElementDecl, Integer> aMoved = new CommonsLinkedHashMap <> ();
    for (int i = 0; i < aKids.size (); i++)
    {
      if (aDefects.size () >= NFA_MAX_ERRORS)
        return aDefects;
      final XdmNode aKid = aKids.get (i);
      final XsdName aName = XsdInstanceValidator.nameOf (aKid);
      final StepResult aStep = _step (aSchema, aNfa, aCur, aName);
      if (!aStep.next ().isEmpty ())
      {
        aCur = aStep.next ();
        aConsumed.addAll (aStep.labels ());
        aLastLabels = aStep.labels ();
        continue;
      }
      // a misplaced element reported earlier turns up where it belongs
      XsdElementDecl aMovedDecl = null;
      for (final var aEntry : aMoved.entrySet ())
        if (aEntry.getValue ().intValue () > 0 && aSchema.matchesElementName (aEntry.getKey (), aName))
          aMovedDecl = aEntry.getKey ();
      if (aMovedDecl != null)
      {
        aMoved.put (aMovedDecl, Integer.valueOf (aMoved.get (aMovedDecl).intValue () - 1));
        continue;
      }
      final int [] aDist = _distToGoal (aNfa, q -> {
        for (final Edge aEdge : aNfa.m_aOut.get (q))
          if (_labelMatches (aSchema, aEdge.label (), aName))
            return true;
        return false;
      });
      int nBest = INF;
      for (int q = aCur.nextSetBit (0); q >= 0; q = aCur.nextSetBit (q + 1))
        if (aDist[q] < nBest)
          nBest = aDist[q];
      if (nBest < INF)
      {
        final Insertions aIns = _insertions (aNfa, aCur, aDist);
        boolean bLater = false;
        if (!aIns.steps ().isEmpty ())
          for (final Label aAlt : aIns.steps ().get (0))
            for (int k = i + 1; k < aKids.size (); k++)
              if (_labelMatches (aSchema, aAlt, XsdInstanceValidator.nameOf (aKids.get (k))))
                bLater = true;
        if (bLater)
        {
          // the element that should come first is there, just later: this
          // child is out of order rather than the other missing
          aDefects.add (new CardDefect (KIND_ORDER, aKid, aIns.steps ().get (0), null, null));
          for (final Label aAlt : aIns.steps ().get (0))
            if (aAlt.m_aDecl != null)
            {
              final Integer aCount = aMoved.get (aAlt.m_aDecl);
              aMoved.put (aAlt.m_aDecl, Integer.valueOf (aCount == null ? 1 : aCount.intValue () + 1));
            }
          for (int s = 1; s < aIns.steps ().size (); s++)
            aDefects.add (new CardDefect (KIND_MISSING, null, aIns.steps ().get (s), null, null));
        }
        else
          for (final ICommonsList <Label> aAlts : aIns.steps ())
            aDefects.add (new CardDefect (KIND_MISSING, null, aAlts, null, null));
        final StepResult aAfter = _step (aSchema, aNfa, aIns.reached (), aName);
        aCur = aAfter.next ();
        aConsumed.addAll (aAfter.labels ());
        aLastLabels = aAfter.labels ();
        continue;
      }
      // nothing can be inserted to make the child acceptable: it is surplus
      Label aPrev = null;
      for (int j = aConsumed.size () - 1; j >= 0; j--)
        if (_labelMatches (aSchema, aConsumed.get (j), aName))
        {
          aPrev = aConsumed.get (j);
          break;
        }
      AncEntry aGroup = null;
      // The element's own wording wins; otherwise a group that has used up its
      // occurrences (below the content model itself) is the better culprit.
      if (aPrev == null || _cardMessages (KIND_TOO_MANY, new CommonsArrayList <> (aPrev)).isEmpty ())
        aGroup = _exhaustedGroup (aSchema, aParticle, aLastLabels, aName);
      if (aPrev != null && aGroup == null)
        aDefects.add (new CardDefect (KIND_TOO_MANY, aKid, null, aPrev, null));
      else
        if (aGroup != null)
          aDefects.add (new CardDefect (KIND_TOO_MANY, aKid, null, aPrev, aGroup));
        else
        {
          boolean bKnown = false;
          for (final XsdElementDecl aDecl : aDeclared)
            if (aSchema.matchesElementName (aDecl, aName))
            {
              bKnown = true;
              break;
            }
          if (bKnown)
            aDefects.add (new CardDefect (KIND_ORDER, aKid, _expectedFrom (aNfa, aCur), null, null));
          else
            aDefects.add (new CardDefect (KIND_UNEXPECTED, aKid, null, null, null));
        }
    }
    if (!aCur.get (aNfa.m_nAccept))
    {
      final int nAccept = aNfa.m_nAccept;
      final int [] aDist = _distToGoal (aNfa, q -> q == nAccept);
      for (final ICommonsList <Label> aAlts : _insertions (aNfa, aCur, aDist).steps ())
        aDefects.add (new CardDefect (KIND_MISSING, null, aAlts, null, null));
    }
    return aDefects;
  }

  /**
   * Render an expanded name the way the instance writes it: the local name when
   * the namespace is the default one there, prefix:local when a prefix is bound
   * to it, Clark notation otherwise.
   */
  @NonNull
  private static String _displayName (@NonNull final XdmNode aElement, @NonNull final XsdName aName)
  {
    if (aName.space ().isEmpty ())
      return aName.local ();
    final ICommonsMap <String, String> aNS = aElement.getInScopeNamespaces ();
    if (aName.space ().equals (aNS.get ("")))
      return aName.local ();
    String sBest = null;
    for (final var aEntry : aNS.entrySet ())
      if (!aEntry.getKey ().isEmpty () &&
          aEntry.getValue ().equals (aName.space ()) &&
          (sBest == null || aEntry.getKey ().compareTo (sBest) < 0))
        sBest = aEntry.getKey ();
    if (sBest != null)
      return sBest + ":" + aName.local ();
    return _clark (aName);
  }

  @NonNull
  private static String _display (@NonNull final Label aLabel, @NonNull final XdmNode aElement)
  {
    if (aLabel.m_aDecl != null)
      return _displayName (aElement, aLabel.m_aDecl.m_aName);
    return "(any element)";
  }

  @NonNull
  private static String _joinAlts (@NonNull final XdmNode aElement,
                                   @NonNull final ICommonsList <Label> aAlts,
                                   final int nMax)
  {
    final ICommonsList <String> aNames = new CommonsArrayList <> ();
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    for (final Label aAlt : aAlts)
    {
      final String sName = _display (aAlt, aElement);
      if (aSeen.add (sName))
        aNames.add (sName);
    }
    boolean bMore = false;
    ICommonsList <String> aShown = aNames;
    if (nMax > 0 && aNames.size () > nMax)
    {
      aShown = new CommonsArrayList <> (aNames.subList (0, nMax));
      bMore = true;
    }
    final String ret = String.join (" or ", aShown);
    return bMore ? ret + " or …" : ret;
  }

  /**
   * Pick the msg:message entries that apply to a cardinality error of kind on
   * the given labels: the declaration's own, else those of the innermost group
   * that holds them all (an xs:choice's appinfo speaks for the alternatives).
   */
  @NonNull
  private static ICommonsList <XsdMessage> _cardMessages (@NonNull final String sKind,
                                                          @NonNull final ICommonsList <Label> aLabels)
  {
    if (aLabels.isEmpty ())
      return new CommonsArrayList <> ();
    final ICommonsSet <XsdElementDecl> aDistinct = new CommonsHashSet <> ();
    for (final Label aLabel : aLabels)
      aDistinct.add (aLabel.m_aDecl);
    final Label aFirst = aLabels.getFirstOrNull ();
    if (aDistinct.size () == 1 && aFirst.m_aDecl != null)
    {
      final ICommonsList <XsdMessage> aMsgs = _filter (aFirst.m_aDecl.m_aMsgs, sKind);
      if (aMsgs.isNotEmpty ())
        return aMsgs;
    }
    for (final AncEntry aAnc : aFirst.m_aAnc)
    {
      boolean bInAll = true;
      for (int i = 1; i < aLabels.size () && bInAll; i++)
      {
        boolean bFound = false;
        for (final AncEntry aOther : aLabels.get (i).m_aAnc)
          if (aOther.group () == aAnc.group ())
          {
            bFound = true;
            break;
          }
        bInAll = bFound;
      }
      if (bInAll)
      {
        final ICommonsList <XsdMessage> aMsgs = _filter (aAnc.group ().m_aMsgs, sKind);
        if (aMsgs.isNotEmpty ())
          return aMsgs;
      }
    }
    return new CommonsArrayList <> ();
  }

  @NonNull
  private static ICommonsList <XsdMessage> _filter (@NonNull final ICommonsList <XsdMessage> aMsgs,
                                                    @NonNull final String sKind)
  {
    final ICommonsList <XsdMessage> ret = new CommonsArrayList <> ();
    for (final XsdMessage aMsg : aMsgs)
      if (aMsg.on ().isEmpty () || aMsg.on ().equals (sKind))
        ret.add (aMsg);
    return ret;
  }

  @NonNull
  private static String _clark (@NonNull final XsdName aName)
  {
    if (aName.space ().isEmpty ())
      return aName.local ();
    return "{" + aName.space () + "}" + aName.local ();
  }

  /**
   * The element names a group is made of, depth first, for "the choice of A,
   * B"; iterative, a group reached twice is listed once.
   */
  @NonNull
  private static ICommonsList <String> _groupElementNames (@NonNull final XdmNode aElement,
                                                           @NonNull final XsdModelGroup aGroup)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ICommonsSet <XsdModelGroup> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdModelGroup> aGroups = new CommonsArrayList <> ();
    final ICommonsList <Integer> aIndexes = new CommonsArrayList <> ();
    aSeen.add (aGroup);
    aGroups.add (aGroup);
    aIndexes.add (Integer.valueOf (0));
    while (aGroups.isNotEmpty ())
    {
      final int nTop = aGroups.size () - 1;
      final XsdModelGroup aCur = aGroups.get (nTop);
      final int nIndex = aIndexes.get (nTop).intValue ();
      if (nIndex >= aCur.m_aParticles.size ())
      {
        aGroups.remove (nTop);
        aIndexes.remove (nTop);
        continue;
      }
      aIndexes.set (nTop, Integer.valueOf (nIndex + 1));
      final IXsdTerm aTerm = aCur.m_aParticles.get (nIndex).m_aTerm;
      if (aTerm instanceof final XsdElementDecl aDecl)
        ret.add (_displayName (aElement, aDecl.m_aName));
      else
        if (aTerm instanceof final XsdModelGroup aSub && aSeen.add (aSub))
        {
          aGroups.add (aSub);
          aIndexes.add (Integer.valueOf (0));
        }
    }
    return ret;
  }

  @NonNull
  private static String _groupKindWord (@NonNull final XsdModelGroup aGroup)
  {
    return switch (aGroup.m_eCompositor)
    {
      case CHOICE -> "choice";
      case ALL -> "all group";
      default -> "sequence";
    };
  }

  /**
   * Turn the findings for the children of an element into diagnostics.
   *
   * @param aSchema
   *        The schema.
   * @param aElement
   *        The parent element.
   * @param aParticle
   *        Its effective content model (no open content).
   * @param aKids
   *        Its child elements.
   * @return The diagnostics in the order the defects were found; empty if there
   *         is nothing to report.
   */
  @NonNull
  static ICommonsList <XsdErrorCollector.Entry> cardinalityErrors (@NonNull final XsdSchema aSchema,
                                                                   @NonNull final XdmNode aElement,
                                                                   @Nullable final XsdParticle aParticle,
                                                                   @NonNull final ICommonsList <XdmNode> aKids)
  {
    final ICommonsList <XsdErrorCollector.Entry> ret = new CommonsArrayList <> ();
    final ICommonsList <CardDefect> aDefects = _explainContent (aSchema, aParticle, aKids);
    if (aDefects == null || aDefects.isEmpty ())
      return ret;
    final String sParent = _displayName (aElement, XsdInstanceValidator.nameOf (aElement));
    final String sCode = "cvc-complex-type.2.4";
    for (final CardDefect aDefect : aDefects)
    {
      int nLine = 0;
      int nCol = 0;
      String sPath = "";
      String sParticle = "";
      String sMessage = "";
      ICommonsList <XsdMessage> aMsgs = new CommonsArrayList <> ();
      final XdmNode aKid = aDefect.m_aKid;
      switch (aDefect.m_sKind)
      {
        case KIND_MISSING:
          nLine = aElement.getLine ();
          nCol = aElement.getColumn ();
          sPath = XsdInstanceValidator.elementPath (aElement);
          sMessage = "element " + _joinAlts (aElement, aDefect.m_aAlts, 6) + " is missing in " + sParent;
          if (aDefect.m_aAlts.isNotEmpty () && aDefect.m_aAlts.getFirstOrNull ().m_aDecl != null)
            sParticle = _clark (aDefect.m_aAlts.getFirstOrNull ().m_aDecl.m_aName);
          aMsgs = _cardMessages (KIND_MISSING, aDefect.m_aAlts);
          break;
        case KIND_TOO_MANY:
          nLine = aKid.getLine ();
          nCol = aKid.getColumn ();
          sPath = XsdInstanceValidator.elementPath (aKid);
          sParticle = _clark (XsdInstanceValidator.nameOf (aKid));
          if (aDefect.m_aGroup != null)
          {
            ICommonsList <String> aNames = _groupElementNames (aElement, aDefect.m_aGroup.group ());
            if (aNames.size () > 6)
            {
              aNames = new CommonsArrayList <> (aNames.subList (0, 6));
              aNames.add ("…");
            }
            sMessage = "element " +
                       _displayName (aElement, XsdInstanceValidator.nameOf (aKid)) +
                       ": the " +
                       _groupKindWord (aDefect.m_aGroup.group ()) +
                       " of " +
                       String.join (", ", aNames) +
                       " occurs more often than allowed (maximum " +
                       aDefect.m_aGroup.particle ().m_nMax +
                       ") in " +
                       sParent;
            aMsgs = _filter (aDefect.m_aGroup.group ().m_aMsgs, KIND_TOO_MANY);
            break;
          }
          final String sLimit = aDefect.m_aPrev.m_aPart != null &&
                                aDefect.m_aPrev.m_aPart.m_nMax != XsdConstants.UNBOUNDED ? " (maximum " +
                                                                                           aDefect.m_aPrev.m_aPart.m_nMax +
                                                                                           ")" : "";
          sMessage = "element " +
                     _displayName (aElement, XsdInstanceValidator.nameOf (aKid)) +
                     " occurs more often than allowed" +
                     sLimit +
                     " in " +
                     sParent;
          aMsgs = _cardMessages (KIND_TOO_MANY, new CommonsArrayList <> (aDefect.m_aPrev));
          break;
        case KIND_UNEXPECTED:
          nLine = aKid.getLine ();
          nCol = aKid.getColumn ();
          sPath = XsdInstanceValidator.elementPath (aKid);
          sParticle = _clark (XsdInstanceValidator.nameOf (aKid));
          sMessage = "element " + _displayName (aElement, XsdInstanceValidator.nameOf (aKid)) + " is not allowed in " + sParent;
          break;
        default:
          nLine = aKid.getLine ();
          nCol = aKid.getColumn ();
          sPath = XsdInstanceValidator.elementPath (aKid);
          sParticle = _clark (XsdInstanceValidator.nameOf (aKid));
          sMessage = "element " +
                     _displayName (aElement, XsdInstanceValidator.nameOf (aKid)) +
                     " is not allowed at this position in " +
                     sParent;
          if (aDefect.m_aAlts != null && aDefect.m_aAlts.isNotEmpty ())
            sMessage += ", expected " + _joinAlts (aElement, aDefect.m_aAlts, 6);
          break;
      }
      ret.add (new XsdErrorCollector.Entry (new XsdError (nLine, nCol, sCode, sMessage, "", sPath, aDefect.m_sKind, sParticle),
                                            aMsgs));
    }
    return ret;
  }
}
