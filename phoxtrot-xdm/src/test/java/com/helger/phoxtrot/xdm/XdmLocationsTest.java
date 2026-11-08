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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.parsers.SAXParserFactory;

import org.junit.Test;

/**
 * Test class for node locations: recorded by default, left out with
 * {@link XdmParser#setRecordLocations(boolean)}, the located node variants
 * and the location of a node created without one.
 *
 * @author Philip Helger
 */
public final class XdmLocationsTest
{
  private static final String XML = "<?xml version='1.0'?>\n" +
                                    "<r a='1'>\n" +
                                    "  <e b='2'>text</e>\n" +
                                    "  <!-- c --><?pi data?>\n" +
                                    "</r>";

  /**
   * @return All nodes of the tree below the root (attributes included), in
   *         document order.
   */
  private static Deque <IXdmNode> _allNodes (final IXdmNode aRoot)
  {
    final Deque <IXdmNode> ret = new ArrayDeque <> ();
    final Deque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
      if (aNode != aRoot)
        ret.add (aNode);
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        ret.add (aNode.getAttributeAtIndex (i));
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  @Test
  public void testDefaultRecordsLocations () throws XdmParseException
  {
    assertTrue (XdmParser.DEFAULT_RECORD_LOCATIONS);
    assertTrue (new XdmParser ().isRecordLocations ());
    final IXdmNode aDoc = new XdmParser ().parse (XML);
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertEquals (2, aR.getLine ());
    assertTrue (aR.getColumn () > 0);
    // An attribute has the location of its element
    assertEquals (2, aR.getAttributeAtIndex (0).getLine ());
    assertEquals (aR.getColumn (), aR.getAttributeAtIndex (0).getColumn ());
    final IXdmNode aE = aR.getChildAtIndex (1);
    assertEquals (3, aE.getLine ());
    assertEquals (3, aE.getChildAtIndex (0).getLine ());
    // Every node kind with a location is a located variant
    for (final IXdmNode aNode : _allNodes (aDoc))
    {
      assertTrue (aNode.toString (), aNode.getLine () > 0);
      assertTrue (aNode.getClass ().getName (), aNode.getClass ().getName ().endsWith ("$Located"));
    }
  }

  @Test
  public void testWithoutLocations () throws XdmParseException
  {
    final XdmParser aParser = new XdmParser ().setRecordLocations (false);
    assertFalse (aParser.isRecordLocations ());
    final IXdmNode aDoc = aParser.parse (XML);
    int nNodes = 0;
    for (final IXdmNode aNode : _allNodes (aDoc))
    {
      assertEquals (0, aNode.getLine ());
      assertEquals (0, aNode.getColumn ());
      assertFalse (aNode.getClass ().getName ().endsWith ("$Located"));
      nNodes++;
    }
    // r, a, whitespace, e, b, text, whitespace, comment, PI, whitespace
    assertEquals (10, nNodes);

    // The same tree otherwise: the same values in the same order
    final Deque <IXdmNode> aLocated = _allNodes (new XdmParser ().parse (XML));
    for (final IXdmNode aNode : _allNodes (aDoc))
    {
      final IXdmNode aOther = aLocated.pop ();
      assertEquals (aOther.getKind (), aNode.getKind ());
      assertEquals (aOther.getName (), aNode.getName ());
      assertEquals (aOther.getStringValue (), aNode.getStringValue ());
      assertEquals (aOther.getOrder (), aNode.getOrder ());
    }
  }

  @Test
  public void testTreeBuilderOption () throws Exception
  {
    final byte [] aBytes = XML.getBytes (StandardCharsets.UTF_8);
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder (false);
    final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    aFactory.newSAXParser ().parse (new ByteArrayInputStream (aBytes), aBuilder);
    for (final IXdmNode aNode : _allNodes (aBuilder.getDocument ()))
      assertEquals (0, aNode.getLine ());
  }

  @Test
  public void testSetLocation ()
  {
    // A node without location fields keeps a set location in its details
    final IXdmNode aElement = IXdmNode.createElement (new XdmQName ("e"));
    assertEquals (0, aElement.getLine ());
    aElement.setLocation (0, 0);
    assertEquals (0, aElement.getLine ());
    aElement.setLocation (4, 7);
    assertEquals (4, aElement.getLine ());
    assertEquals (7, aElement.getColumn ());
    aElement.setLocation (0, 0);
    assertEquals (0, aElement.getLine ());
    assertEquals (0, aElement.getColumn ());

    // The located factories
    final IXdmNode aLocated = IXdmNode.createElement (new XdmQName ("e"), 3, 5);
    assertEquals (3, aLocated.getLine ());
    assertEquals (5, aLocated.getColumn ());
    aLocated.setLocation (6, 8);
    assertEquals (6, aLocated.getLine ());
    assertEquals (8, aLocated.getColumn ());
    final IXdmNode aAttr = IXdmNode.createAttribute (new XdmQName ("a"), "v", 2, 9);
    assertEquals (2, aAttr.getLine ());
    assertEquals (9, aAttr.getColumn ());
    assertEquals ("v", aAttr.getValue ());
    final IXdmNode aText = IXdmNode.createText ("t", 1, 1);
    assertEquals (1, aText.getLine ());
    assertEquals ("t", aText.getValue ());
    // Location 0:0 gives the plain node
    assertFalse (IXdmNode.createText ("t", 0, 0).getClass ().getName ().endsWith ("$Located"));
    assertTrue (aText.getClass ().getName ().endsWith ("$Located"));
    assertTrue (aText instanceof XdmTextNode);
  }
}
