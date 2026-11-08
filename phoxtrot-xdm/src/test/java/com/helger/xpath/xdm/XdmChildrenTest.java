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
package com.helger.xpath.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.ICommonsList;

/**
 * Test class for the child storage of {@link AbstractXdmParentNode}: appending
 * beyond every capacity step, removing at the start, in the middle and at the
 * end, and the index checks.
 *
 * @author Philip Helger
 */
public final class XdmChildrenTest
{
  private static void _assertChildren (final IXdmNode aParent, final List <IXdmNode> aExpected)
  {
    assertEquals (aExpected.size (), aParent.getChildCount ());
    for (int i = 0; i < aExpected.size (); ++i)
    {
      assertSame (aExpected.get (i), aParent.getChildAtIndex (i));
      assertSame (aParent, aExpected.get (i).getParent ());
    }
    final ICommonsList <IXdmNode> aAll = aParent.getAllChildren ();
    assertEquals (aExpected, aAll);
    // A copy: changing it does not change the node
    aAll.clear ();
    assertEquals (aExpected.size (), aParent.getChildCount ());
  }

  private static void _assertIndexFails (final IXdmNode aParent, final int nIndex)
  {
    try
    {
      aParent.getChildAtIndex (nIndex);
      fail ("Index " + nIndex + " must fail");
    }
    catch (final IndexOutOfBoundsException ex)
    {
      // expected
    }
  }

  @Test
  public void testAppendAndRemove ()
  {
    final IXdmNode aParent = IXdmNode.createElement (new XdmQName ("p"));
    final List <IXdmNode> aExpected = new ArrayList <> ();
    _assertChildren (aParent, aExpected);
    _assertIndexFails (aParent, 0);
    _assertIndexFails (aParent, -1);
    assertEquals ("", ((XdmElementNode) aParent).getStringValue ());

    // Across the growth steps 1, 4, 6, 9, 13, ...
    for (int i = 0; i < 40; ++i)
    {
      final IXdmNode aChild = i % 3 == 0 ? IXdmNode.createText (Integer.toString (i))
                                         : IXdmNode.createElement (new XdmQName ("c" + i));
      aParent.appendChild (aChild);
      aExpected.add (aChild);
      _assertChildren (aParent, aExpected);
      _assertIndexFails (aParent, aExpected.size ());
    }

    // First, middle, last
    for (final int nIndex : new int [] { 0, 17, 37 })
    {
      final IXdmNode aChild = aExpected.remove (nIndex);
      aParent.removeChild (aChild);
      assertNull (aChild.getParent ());
      _assertChildren (aParent, aExpected);
      _assertIndexFails (aParent, aExpected.size ());
      // Not a child any more
      try
      {
        aParent.removeChild (aChild);
        fail ();
      }
      catch (final IllegalArgumentException ex)
      {
        // expected
      }
    }

    // Remove all, then append again
    while (!aExpected.isEmpty ())
      aParent.removeChild (aExpected.remove (aExpected.size () - 1));
    _assertChildren (aParent, aExpected);
    _assertIndexFails (aParent, 0);
    final IXdmNode aText = IXdmNode.createText ("t");
    aParent.appendChild (aText);
    aExpected.add (aText);
    _assertChildren (aParent, aExpected);
    assertEquals ("t", ((XdmElementNode) aParent).getStringValue ());
  }

  @Test
  public void testDetachNumbered ()
  {
    final XdmDocumentNode aDoc = (XdmDocumentNode) IXdmNode.createDocument ();
    final AbstractXdmNode aA = (AbstractXdmNode) IXdmNode.createElement (new XdmQName ("a"));
    final AbstractXdmNode aB = (AbstractXdmNode) IXdmNode.createElement (new XdmQName ("b"));
    final AbstractXdmNode aC = (AbstractXdmNode) IXdmNode.createElement (new XdmQName ("c"));
    aDoc.internalAppendNumbered (aA);
    aDoc.internalAppendNumbered (aB);
    aDoc.internalAppendNumbered (aC);
    assertTrue (aDoc.internalDetachNumbered (aB));
    assertFalse (aDoc.internalDetachNumbered (aB));
    assertNull (aB.getParent ());
    _assertChildren (aDoc, List.of (aA, aC));
    assertTrue (aDoc.internalDetachNumbered (aC));
    assertTrue (aDoc.internalDetachNumbered (aA));
    _assertChildren (aDoc, List.of ());
    assertFalse (((XdmDocumentNode) IXdmNode.createDocument ()).internalDetachNumbered (aA));
  }

  @Test
  public void testAppendAfterParse () throws XdmParseException
  {
    // The builder trims the child array of r (7 children) to its size
    final IXdmNode aDoc = new XdmParser ().parse ("<r><a/><a/><a/><a/><a/><a/><a/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    final List <IXdmNode> aExpected = new ArrayList <> (aR.getAllChildren ());
    assertEquals (7, aExpected.size ());
    final IXdmNode aNew = IXdmNode.createElement (new XdmQName ("b"));
    aR.appendChild (aNew);
    aExpected.add (aNew);
    _assertChildren (aR, aExpected);
  }

  @Test
  public void testStringValue () throws XdmParseException
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r>a<x>b<!--c--><y>d</y></x>e<?p f?></r>");
    assertEquals ("abde", ((XdmDocumentNode) aDoc).getStringValue ());
    assertEquals ("bd", ((XdmElementNode) aDoc.getChildAtIndex (0).getChildAtIndex (1)).getStringValue ());
  }
}
