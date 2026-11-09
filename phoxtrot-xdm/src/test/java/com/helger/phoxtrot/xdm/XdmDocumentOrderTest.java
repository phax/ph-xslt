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
package com.helger.phoxtrot.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Test class for the document order numbering of {@link AbstractXdmNode} and the name index of
 * {@link XdmDocumentNode}.
 *
 * @author Philip Helger
 */
public final class XdmDocumentOrderTest
{
  private static final XdmQName X = new XdmQName ("x");

  private static XdmDocumentNode _parse (final String s) throws XdmParseException
  {
    return (XdmDocumentNode) new XdmParser ().parse (s);
  }

  @Test
  public void testOrderBetweenTrees () throws XdmParseException
  {
    final XdmDocumentNode aFirst = _parse ("<a><b/></a>");
    final XdmDocumentNode aSecond = _parse ("<a><b/></a>");
    final AbstractXdmNode aFirstB = aFirst.getChildAtIndex (0).getChildAtIndex (0);
    final AbstractXdmNode aSecondA = aSecond.getChildAtIndex (0);
    // Same document order numbers, but different trees: ordered by numbering
    assertEquals (aFirstB.getOrder (), aSecondA.getOrder () + 1);
    assertTrue (IXdmNode.compareDocumentOrder (aFirstB, aSecondA) < 0);
    assertTrue (IXdmNode.compareDocumentOrder (aSecondA, aFirstB) > 0);
    assertTrue (IXdmNode.compareDocumentOrder (aFirst, aSecond) < 0);
    // Nodes of one numbering share the stamp
    assertSame (aFirst.internalGetTree (), aFirstB.internalGetTree ());
    // A tree numbered again sorts after the trees numbered before
    aFirst.assignDocumentOrder ();
    assertTrue (IXdmNode.compareDocumentOrder (aFirstB, aSecondA) > 0);
    assertTrue (IXdmNode.compareDocumentOrder (aFirst.getChildAtIndex (0), aFirstB) < 0);
  }

  @Test
  public void testEndOrder () throws XdmParseException
  {
    // 0 doc, 1 r, 2 @a, 3 x, 4 @b, 5 y, 6 x, 7 @c, 8 text, 9 z
    final XdmDocumentNode aDoc = _parse ("<r a='1'><x b='2'><y/></x><x c='3'/>t<z/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    final IXdmNode aX1 = aR.getChildAtIndex (0);
    final IXdmNode aX2 = aR.getChildAtIndex (1);
    assertEquals (0, aDoc.getOrder ());
    assertEquals (9, aDoc.getEndOrder ());
    assertEquals (1, aR.getOrder ());
    assertEquals (9, aR.getEndOrder ());
    assertEquals (2, aR.getAttributeAtIndex (0).getOrder ());
    assertEquals (3, aX1.getOrder ());
    assertEquals (5, aX1.getEndOrder ());
    // An element without children ends with its last attribute
    assertEquals (6, aX2.getOrder ());
    assertEquals (7, aX2.getEndOrder ());
    // Leaves end with themselves
    assertEquals (8, aR.getChildAtIndex (2).getEndOrder ());
    assertEquals (7, aX2.getAttributeAtIndex (0).getEndOrder ());
    assertEquals (9, aR.getChildAtIndex (3).getOrder ());
    assertEquals (9, aR.getChildAtIndex (3).getEndOrder ());
  }

  @Test
  public void testNameIndex () throws XdmParseException
  {
    final XdmDocumentNode aDoc = _parse ("<r xmlns:p='urn:p'><x><x/></x><p:x/><y><x/></y></r>");
    final List <IXdmNode> aXs = aDoc.getElementsByName (X);
    assertNotNull (aXs);
    assertEquals (3, aXs.size ());
    for (int i = 1; i < aXs.size (); ++i)
      assertTrue (aXs.get (i - 1).getOrder () < aXs.get (i).getOrder ());
    assertEquals (1, aDoc.getElementsByName (new XdmQName ("urn:p", "x")).size ());
    assertEquals (0, aDoc.getElementsByName (new XdmQName ("none")).size ());
    // The index is kept
    assertSame (aXs, aDoc.getElementsByName (X));
  }

  @Test
  public void testUnnumbered ()
  {
    final IXdmNode aDoc = IXdmNode.createDocument ();
    aDoc.appendChild (IXdmNode.createElement (X));
    assertNull (((XdmDocumentNode) aDoc).getElementsByName (X));
    aDoc.assignDocumentOrder ();
    assertEquals (1, ((XdmDocumentNode) aDoc).getElementsByName (X).size ());
  }

  @Test
  public void testChangeRemovesNumbering () throws XdmParseException
  {
    final XdmDocumentNode aDoc = _parse ("<r><x/><y><x/></y></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertEquals (2, aDoc.getElementsByName (X).size ());

    // Appending
    aR.appendChild (IXdmNode.createElement (X));
    assertNull (aDoc.getElementsByName (X));
    aDoc.assignDocumentOrder ();
    assertEquals (3, aDoc.getElementsByName (X).size ());
    assertEquals (aR.getChildAtIndex (2).getOrder (), aR.getEndOrder ());

    // Removing: the removed subtree is no longer numbered either
    final IXdmNode aY = aR.getChildAtIndex (1);
    aR.removeChild (aY);
    assertNull (aDoc.getElementsByName (X));
    final IXdmNode aOther = IXdmNode.createDocument ();
    aOther.appendChild (aY);
    aOther.assignDocumentOrder ();
    aDoc.assignDocumentOrder ();
    assertEquals (2, aDoc.getElementsByName (X).size ());
    assertEquals (1, ((XdmDocumentNode) aOther).getElementsByName (X).size ());

    // Adding an attribute
    aR.addAttribute (IXdmNode.createAttribute (new XdmQName ("a"), "1"));
    assertNull (aDoc.getElementsByName (X));
    aDoc.assignDocumentOrder ();
    assertEquals (2, aR.getAttributeAtIndex (0).getOrder ());
  }

  @Test
  public void testAppendNumberedSubtree ()
  {
    // A subtree numbered as a tree of its own, appended to an unnumbered tree
    final IXdmNode aSub = IXdmNode.createElement (X);
    aSub.appendChild (IXdmNode.createElement (X));
    aSub.assignDocumentOrder ();
    assertNotNull (((AbstractXdmNode) aSub.getChildAtIndex (0)).internalGetTree ());
    final IXdmNode aDoc = IXdmNode.createDocument ();
    aDoc.appendChild (aSub);
    assertNull (((AbstractXdmNode) aSub).internalGetTree ());
    assertNull (((AbstractXdmNode) aSub.getChildAtIndex (0)).internalGetTree ());
    aDoc.assignDocumentOrder ();
    assertEquals (1, aSub.getOrder ());
    assertEquals (2, aSub.getChildAtIndex (0).getOrder ());

    // The same for an attribute numbered on its own
    final IXdmNode aAttr = IXdmNode.createAttribute (new XdmQName ("a"), "1");
    aAttr.assignDocumentOrder ();
    assertNotNull (((AbstractXdmNode) aAttr).internalGetTree ());
    final IXdmNode aElement = IXdmNode.createElement (X);
    aElement.addAttribute (aAttr);
    assertNull (((AbstractXdmNode) aAttr).internalGetTree ());
  }

  @Test
  public void testAttributeIndex () throws XdmParseException
  {
    final XdmQName aNameA = new XdmQName ("a");
    final XdmDocumentNode aDoc = _parse ("<r a='1'><x a='2' b='3'><y a='4'/></x><x xmlns:p='urn:p' p:a='5'/></r>");
    final List <IXdmNode> aAs = aDoc.getAttributesByName (aNameA);
    assertNotNull (aAs);
    assertEquals (3, aAs.size ());
    assertEquals ("1", aAs.get (0).getStringValue ());
    assertEquals ("2", aAs.get (1).getStringValue ());
    assertEquals ("4", aAs.get (2).getStringValue ());
    assertEquals (1, aDoc.getAttributesByName (new XdmQName ("urn:p", "a")).size ());
    assertEquals (0, aDoc.getAttributesByName (X).size ());
    assertSame (aAs, aDoc.getAttributesByName (aNameA));

    // A change drops the attribute index with the numbering
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    aR.getChildAtIndex (1).addAttribute (IXdmNode.createAttribute (aNameA, "6"));
    assertNull (aDoc.getAttributesByName (aNameA));
    aDoc.assignDocumentOrder ();
    assertEquals (4, aDoc.getAttributesByName (aNameA).size ());
  }

  @Test
  public void testChildElementIndex () throws XdmParseException
  {
    final XdmDocumentNode aDoc = _parse ("<r><x n='1'><x n='1.1'/></x><y><x n='2.1'/></y><x n='3'/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    final List <IXdmNode> aXs = aDoc.getChildElementsByName (aR, X);
    assertNotNull (aXs);
    assertEquals (2, aXs.size ());
    assertEquals ("1", aXs.get (0).getAttributeAtIndex (0).getStringValue ());
    assertEquals ("3", aXs.get (1).getAttributeAtIndex (0).getStringValue ());
    // Kept with the index
    assertSame (aXs, aDoc.getChildElementsByName (aR, X));
    assertEquals (1, aDoc.getChildElementsByName (aR, new XdmQName ("y")).size ());
    assertEquals (0, aDoc.getChildElementsByName (aR, new XdmQName ("none")).size ());
    assertEquals (1, aDoc.getChildElementsByName (aXs.get (0), X).size ());

    // A change drops the kept children with the numbering
    aR.appendChild (IXdmNode.createElement (X));
    assertNull (aDoc.getChildElementsByName (aR, X));
    aDoc.assignDocumentOrder ();
    assertEquals (3, aDoc.getChildElementsByName (aR, X).size ());
  }

  @Test
  public void testTextAppendValue ()
  {
    final IXdmNode aElement = IXdmNode.createElement (X);
    final XdmTextNode aText = (XdmTextNode) IXdmNode.createText ("a");
    aElement.appendChild (aText);
    aText.appendValue ("b");
    aText.appendValue ("");
    aText.appendValue ("c");
    assertEquals ("abc", aText.getValue ());
    assertEquals ("abc", aElement.getStringValue ());
    // Appending after a read, and reading again
    aText.appendValue ("d");
    assertEquals ("abcd", aText.getStringValue ());
    // Setting the value drops what is pending
    aText.appendValue ("e");
    aText.setValue ("x");
    assertEquals ("x", aText.getValue ());
    final StringBuilder aExpected = new StringBuilder ("x");
    for (int i = 0; i < 100_000; ++i)
    {
      aText.appendValue ("y");
      aExpected.append ('y');
    }
    assertEquals (aExpected.toString (), aText.getValue ());
  }
}
