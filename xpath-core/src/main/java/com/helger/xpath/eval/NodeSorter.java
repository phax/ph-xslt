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
package com.helger.xpath.eval;

import java.util.Arrays;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * Sorting node sequences into document order. Owner: evaluator workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class NodeSorter
{
  @PresentForCodeCoverage
  private static final NodeSorter INSTANCE = new NodeSorter ();

  private NodeSorter ()
  {}

  /**
   * @param aSeq
   *        A sequence of nodes only.
   * @return The nodes in document order without duplicates (by identity).
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 if the sequence contains a non-node.
   */
  @NonNull
  public static XdmSequence sortAndDeduplicate (@NonNull final XdmSequence aSeq)
  {
    final int nSize = aSeq.size ();
    final XdmNode [] aNodes = new XdmNode [nSize];
    boolean bSorted = true;
    for (int i = 0; i < nSize; ++i)
    {
      final IXdmItem aItem = aSeq.get (i);
      if (!(aItem instanceof final XdmNode aNode))
        throw XPathException.type ("Expected a sequence of nodes, but found a non-node item");
      aNodes[i] = aNode;
      if (bSorted && i > 0 && XdmNode.compareDocumentOrder (aNodes[i - 1], aNode) >= 0)
        bSorted = false;
    }
    if (bSorted)
      return aSeq;

    Arrays.sort (aNodes, XdmNode::compareDocumentOrder);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    XdmNode aPrev = null;
    for (final XdmNode aNode : aNodes)
    {
      if (aNode != aPrev)
        aBuilder.add (aNode);
      aPrev = aNode;
    }
    return aBuilder.build ();
  }
}
