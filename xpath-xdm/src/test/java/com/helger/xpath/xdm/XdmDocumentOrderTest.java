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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;

import org.junit.Test;

/**
 * Test class for the document order numbering of {@link AbstractXdmNode} and
 * the name index of {@link XdmDocumentNode}.
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
      assertEquals (true, aXs.get (i - 1).getOrder () < aXs.get (i).getOrder ());
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
