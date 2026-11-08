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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import static com.helger.xpath.eval.ExprTestHelper.assertError;
import static com.helger.xpath.eval.ExprTestHelper.names;
import static com.helger.xpath.eval.ExprTestHelper.parse;

import org.junit.Test;

import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Test class for class {@link NodeSorter}.
 *
 * @author Philip Helger
 */
public final class NodeSorterTest
{
  @Test
  public void testSort ()
  {
    final IXdmNode aDoc = parse ("<r><a/><b/><c/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    final IXdmNode aA = aR.getChildAtIndex (0);
    final IXdmNode aB = aR.getChildAtIndex (1);
    final IXdmNode aC = aR.getChildAtIndex (2);
    assertEquals ("r a b c", names (NodeSorter.sortAndDeduplicate (XdmSequence.of (aC, aA, aR, aB, aC, aA))));
    final XdmSequence aSorted = XdmSequence.of (aA, aB);
    assertSame (aSorted, NodeSorter.sortAndDeduplicate (aSorted));
    assertSame (XdmSequence.EMPTY, NodeSorter.sortAndDeduplicate (XdmSequence.EMPTY));
    assertEquals ("a", names (NodeSorter.sortAndDeduplicate (XdmSequence.of (aA, aA))));
    assertError ("XPTY0004", () -> NodeSorter.sortAndDeduplicate (XdmSequence.of (aA, XdmAtomic.integer (1))));
  }

  @Test
  public void testSortAcrossTrees ()
  {
    final IXdmNode aDoc1 = parse ("<a/>");
    final IXdmNode aDoc2 = parse ("<b/>");
    final XdmSequence aRes = NodeSorter.sortAndDeduplicate (XdmSequence.of (aDoc2.getChildAtIndex (0),
                                                                           aDoc1.getChildAtIndex (0),
                                                                           aDoc2.getChildAtIndex (0)));
    assertEquals (2, aRes.size ());
    // stable: the same order again
    assertEquals (names (aRes),
                  names (NodeSorter.sortAndDeduplicate (XdmSequence.of (aDoc1.getChildAtIndex (0), aDoc2.getChildAtIndex (0)))));
  }
}
