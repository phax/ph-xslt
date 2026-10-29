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
package com.helger.xpath.pattern;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.IXdmNode;

/**
 * A set of anchor nodes of a selection pattern: the context nodes from which
 * the equivalent expression of the pattern selects a given candidate node. It
 * is either a finite set or the complement of a finite set (patterns that do
 * not depend on the context node, such as <code>/a</code> or
 * <code>$v</code>, select the candidate from every anchor or from none).
 * Nodes have identity semantics.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class AnchorSet
{
  private final boolean m_bComplement;
  private final ICommonsSet <IXdmNode> m_aNodes;

  private AnchorSet (final boolean bComplement, @NonNull final ICommonsSet <IXdmNode> aNodes)
  {
    m_bComplement = bComplement;
    m_aNodes = aNodes;
  }

  @NonNull
  static AnchorSet createEmpty ()
  {
    return new AnchorSet (false, new CommonsHashSet <> ());
  }

  @NonNull
  static AnchorSet createAll ()
  {
    return new AnchorSet (true, new CommonsHashSet <> ());
  }

  @NonNull
  static AnchorSet createComplementOf (@NonNull final ICommonsSet <IXdmNode> aExcluded)
  {
    return new AnchorSet (true, new CommonsHashSet <> (aExcluded));
  }

  /**
   * @return <code>true</code> if this is the complement of
   *         {@link #getNodes()}, <code>false</code> if it is {@link #getNodes()}
   *         itself.
   */
  boolean isComplement ()
  {
    return m_bComplement;
  }

  /** @return The finite part: the members, or the excluded nodes of a complement. Not a copy. */
  @NonNull
  ICommonsSet <IXdmNode> getNodes ()
  {
    return m_aNodes;
  }

  boolean isDefinitelyEmpty ()
  {
    return !m_bComplement && m_aNodes.isEmpty ();
  }

  /** Add one node in place. */
  void add (@NonNull final IXdmNode aNode)
  {
    if (m_bComplement)
      m_aNodes.remove (aNode);
    else
      m_aNodes.add (aNode);
  }

  @NonNull
  AnchorSet getUnion (@NonNull final AnchorSet aOther)
  {
    final ICommonsSet <IXdmNode> aSet = new CommonsHashSet <> ();
    if (!m_bComplement && !aOther.m_bComplement)
    {
      aSet.addAll (m_aNodes);
      aSet.addAll (aOther.m_aNodes);
      return new AnchorSet (false, aSet);
    }
    if (m_bComplement && aOther.m_bComplement)
    {
      // co(A) | co(B) = co(A & B)
      for (final IXdmNode aNode : m_aNodes)
        if (aOther.m_aNodes.contains (aNode))
          aSet.add (aNode);
      return new AnchorSet (true, aSet);
    }
    // F | co(E) = co(E \ F)
    final AnchorSet aFinite = m_bComplement ? aOther : this;
    final AnchorSet aCo = m_bComplement ? this : aOther;
    for (final IXdmNode aNode : aCo.m_aNodes)
      if (!aFinite.m_aNodes.contains (aNode))
        aSet.add (aNode);
    return new AnchorSet (true, aSet);
  }

  @NonNull
  AnchorSet getIntersection (@NonNull final AnchorSet aOther)
  {
    final ICommonsSet <IXdmNode> aSet = new CommonsHashSet <> ();
    if (m_bComplement && aOther.m_bComplement)
    {
      // co(A) & co(B) = co(A | B)
      aSet.addAll (m_aNodes);
      aSet.addAll (aOther.m_aNodes);
      return new AnchorSet (true, aSet);
    }
    if (!m_bComplement && !aOther.m_bComplement)
    {
      for (final IXdmNode aNode : m_aNodes)
        if (aOther.m_aNodes.contains (aNode))
          aSet.add (aNode);
      return new AnchorSet (false, aSet);
    }
    // F & co(E) = F \ E
    final AnchorSet aFinite = m_bComplement ? aOther : this;
    final AnchorSet aCo = m_bComplement ? this : aOther;
    for (final IXdmNode aNode : aFinite.m_aNodes)
      if (!aCo.m_aNodes.contains (aNode))
        aSet.add (aNode);
    return new AnchorSet (false, aSet);
  }

  @NonNull
  AnchorSet getDifference (@NonNull final AnchorSet aOther)
  {
    final ICommonsSet <IXdmNode> aSet = new CommonsHashSet <> ();
    if (!m_bComplement && !aOther.m_bComplement)
    {
      for (final IXdmNode aNode : m_aNodes)
        if (!aOther.m_aNodes.contains (aNode))
          aSet.add (aNode);
      return new AnchorSet (false, aSet);
    }
    if (!m_bComplement)
    {
      // F \ co(E) = F & E
      for (final IXdmNode aNode : m_aNodes)
        if (aOther.m_aNodes.contains (aNode))
          aSet.add (aNode);
      return new AnchorSet (false, aSet);
    }
    if (!aOther.m_bComplement)
    {
      // co(E) \ F = co(E | F)
      aSet.addAll (m_aNodes);
      aSet.addAll (aOther.m_aNodes);
      return new AnchorSet (true, aSet);
    }
    // co(A) \ co(B) = B \ A
    for (final IXdmNode aNode : aOther.m_aNodes)
      if (!m_aNodes.contains (aNode))
        aSet.add (aNode);
    return new AnchorSet (false, aSet);
  }
}
