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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.collection.commons.ICommonsList;

/**
 * Test class for the attribute storage of {@link XdmElementNode}: attributes
 * set while parsing, appended afterwards, and the index checks.
 *
 * @author Philip Helger
 */
public final class XdmAttributesTest
{
  private static void _assertIndexFails (final IXdmNode aElement, final int nIndex)
  {
    try
    {
      aElement.getAttributeAtIndex (nIndex);
      fail ("Index " + nIndex + " must fail");
    }
    catch (final IndexOutOfBoundsException ex)
    {
      // expected
    }
  }

  @Test
  public void testParsed () throws XdmParseException
  {
    // More attributes than the initial buffer of the tree builder, and
    // namespace declarations in between that are no attributes
    final StringBuilder aSB = new StringBuilder ("<r xmlns='urn:d'");
    for (int i = 0; i < 20; ++i)
    {
      aSB.append (" a").append (i).append ("='").append (i).append ('\'');
      if (i == 5)
        aSB.append (" xmlns:p='urn:p'");
    }
    aSB.append ("><e/><f b='x'/></r>");
    final IXdmNode aDoc = new XdmParser ().parse (aSB.toString ());
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertEquals (20, aR.getAttributeCount ());
    for (int i = 0; i < 20; ++i)
    {
      final IXdmNode aAttr = aR.getAttributeAtIndex (i);
      assertEquals ("a" + i, aAttr.getName ().localName ());
      assertEquals (Integer.toString (i), aAttr.getValue ());
      assertSame (aR, aAttr.getParent ());
      // Numbered directly after the element, in order
      assertEquals (aR.getOrder () + 1 + i, aAttr.getOrder ());
    }
    _assertIndexFails (aR, 20);
    _assertIndexFails (aR, -1);

    // No attributes, after an element with many: the buffer is not carried over
    final IXdmNode aE = aR.getChildAtIndex (0);
    assertEquals (0, aE.getAttributeCount ());
    assertEquals (0, aE.getAllAttributes ().size ());
    _assertIndexFails (aE, 0);
    assertNull (aE.getAttribute (null, "a0"));
    final IXdmNode aF = aR.getChildAtIndex (1);
    assertEquals (1, aF.getAttributeCount ());
    assertEquals ("x", aF.getAttribute (null, "b").getValue ());
    assertSame (aF, aF.getAttributeAtIndex (0).getParent ());
  }

  @Test
  public void testAddAfterParsing () throws XdmParseException
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r a='1' b='2'><e/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    final IXdmNode aE = aR.getChildAtIndex (0);
    assertEquals (4, aE.getOrder ());

    final IXdmNode aC = IXdmNode.createAttribute (new XdmQName ("c"), "3");
    aR.addAttribute (aC);
    assertEquals (3, aR.getAttributeCount ());
    assertSame (aC, aR.getAttributeAtIndex (2));
    assertSame (aR, aC.getParent ());
    assertSame (aC, aR.getAttribute (null, "c"));
    assertEquals ("1", aR.getAttribute (null, "a").getValue ());
    // The numbering was dropped and is assigned again with the new attribute
    assertNull (((AbstractXdmNode) aE).internalGetTree ());
    aDoc.assignDocumentOrder ();
    assertEquals (4, aC.getOrder ());
    assertEquals (5, aE.getOrder ());

    // An element without attributes gets its first one
    aE.addAttribute (IXdmNode.createAttribute (new XdmQName ("d"), "4"));
    assertEquals (1, aE.getAttributeCount ());
    assertEquals ("4", aE.getAttributeAtIndex (0).getValue ());

    // The list of all attributes is a copy
    final ICommonsList <IXdmNode> aAll = aR.getAllAttributes ();
    assertEquals (3, aAll.size ());
    assertSame (aC, aAll.get (2));
    aAll.clear ();
    assertEquals (3, aR.getAttributeCount ());
  }

  @Test
  public void testAddToCreatedElement ()
  {
    final IXdmNode aElement = IXdmNode.createElement (new XdmQName ("e"));
    _assertIndexFails (aElement, 0);
    for (int i = 0; i < 10; ++i)
      aElement.addAttribute (IXdmNode.createAttribute (new XdmQName ("a" + i), Integer.toString (i)));
    assertEquals (10, aElement.getAttributeCount ());
    for (int i = 0; i < 10; ++i)
      assertEquals (Integer.toString (i), aElement.getAttributeAtIndex (i).getValue ());
    _assertIndexFails (aElement, 10);

    // An attribute with a parent is rejected and leaves the element unchanged
    try
    {
      aElement.addAttribute (aElement.getAttributeAtIndex (0));
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
    assertEquals (10, aElement.getAttributeCount ());
  }
}
