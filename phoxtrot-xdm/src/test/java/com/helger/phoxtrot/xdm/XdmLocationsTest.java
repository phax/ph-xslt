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
 * Test class for node locations: recorded with
 * {@link XdmParser#setRecordLocations(boolean)}, left out by default, the
 * located node variants,
 * the location of a node created without one and attributes and text nodes,
 * which report the location of their parent.
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
  public void testRecordLocations () throws XdmParseException
  {
    final XdmParser aParser = new XdmParser ().setRecordLocations (true);
    assertTrue (aParser.isRecordLocations ());
    final IXdmNode aDoc = aParser.parse (XML);
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertEquals (2, aR.getLine ());
    assertTrue (aR.getColumn () > 0);
    // An attribute has the location of its element
    assertEquals (2, aR.getAttributeAtIndex (0).getLine ());
    assertEquals (aR.getColumn (), aR.getAttributeAtIndex (0).getColumn ());
    final IXdmNode aE = aR.getChildAtIndex (1);
    assertEquals (3, aE.getLine ());
    // A text node has the location of its parent element
    assertEquals (3, aE.getChildAtIndex (0).getLine ());
    assertEquals (aE.getColumn (), aE.getChildAtIndex (0).getColumn ());
    assertEquals (aR.getColumn (), aR.getChildAtIndex (0).getColumn ());
    // Every node has a location; only elements, comments and processing
    // instructions are located variants
    for (final IXdmNode aNode : _allNodes (aDoc))
    {
      assertTrue (aNode.toString (), aNode.getLine () > 0);
      final boolean bOwnLocation = !aNode.isAttribute () && !aNode.isText ();
      assertEquals (aNode.getClass ().getName (), Boolean.valueOf (bOwnLocation),
                    Boolean.valueOf (aNode.getClass ().getName ().endsWith ("$Located")));
    }
  }

  @Test
  public void testWithoutLocations () throws XdmParseException
  {
    // The default
    assertFalse (XdmParser.DEFAULT_RECORD_LOCATIONS);
    assertFalse (new XdmParser ().isRecordLocations ());
    final XdmParser aParser = new XdmParser ();
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
    final Deque <IXdmNode> aLocated = _allNodes (new XdmParser ().setRecordLocations (true).parse (XML));
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
    final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    // Without locations, explicitly and by default
    for (final XdmTreeBuilder aBuilder : new XdmTreeBuilder [] { new XdmTreeBuilder (false), new XdmTreeBuilder () })
    {
      aFactory.newSAXParser ().parse (new ByteArrayInputStream (aBytes), aBuilder);
      for (final IXdmNode aNode : _allNodes (aBuilder.getDocument ()))
        assertEquals (0, aNode.getLine ());
    }
    final XdmTreeBuilder aLocated = new XdmTreeBuilder (true);
    aFactory.newSAXParser ().parse (new ByteArrayInputStream (aBytes), aLocated);
    for (final IXdmNode aNode : _allNodes (aLocated.getDocument ()))
      assertTrue (aNode.getLine () > 0);
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
    // Location 0:0 gives the plain node
    assertFalse (IXdmNode.createElement (new XdmQName ("e"), 0, 0).getClass ().getName ().endsWith ("$Located"));
    assertTrue (aLocated.getClass ().getName ().endsWith ("$Located"));

    // A parentless attribute or text node keeps its location ...
    final IXdmNode aAttr = IXdmNode.createAttribute (new XdmQName ("a"), "v", 2, 9);
    assertEquals (2, aAttr.getLine ());
    assertEquals (9, aAttr.getColumn ());
    assertEquals ("v", aAttr.getValue ());
    final IXdmNode aText = IXdmNode.createText ("t", 1, 1);
    assertEquals (1, aText.getLine ());
    assertEquals (1, aText.getColumn ());
    assertEquals ("t", aText.getValue ());
    assertTrue (aText instanceof XdmTextNode);
    assertEquals (0, IXdmNode.createText ("t", 0, 0).getLine ());

    // ... and reports the one of its parent once it has one
    aLocated.addAttribute (aAttr);
    aLocated.appendChild (aText);
    assertEquals (6, aAttr.getLine ());
    assertEquals (8, aAttr.getColumn ());
    assertEquals (6, aText.getLine ());
    assertEquals (8, aText.getColumn ());
    aLocated.removeChild (aText);
    assertEquals (1, aText.getLine ());
    final IXdmNode aPlainText = IXdmNode.createText ("u");
    aElement.appendChild (aPlainText);
    assertEquals (0, aPlainText.getLine ());
  }

  @Test
  public void testRecordLocationsScope () throws XdmParseException
  {
    assertFalse (XdmParser.isRecordLocationsByDefault ());
    final IXdmNode aDoc = XdmParser.getWithRecordLocations ( () -> {
      assertTrue (XdmParser.isRecordLocationsByDefault ());
      assertTrue (new XdmParser ().isRecordLocations ());
      // An explicit setting wins
      assertFalse (new XdmParser ().setRecordLocations (false).isRecordLocations ());
      // Nested scopes restore the outer one
      XdmParser.getWithRecordLocations ( () -> Boolean.TRUE);
      assertTrue (XdmParser.isRecordLocationsByDefault ());
      try
      {
        return new XdmParser ().parse (XML);
      }
      catch (final XdmParseException ex)
      {
        throw new IllegalStateException (ex);
      }
    });
    assertEquals (2, aDoc.getChildAtIndex (0).getLine ());
    // Restored afterwards, also after an exception
    assertFalse (XdmParser.isRecordLocationsByDefault ());
    assertFalse (new XdmParser ().isRecordLocations ());
    try
    {
      XdmParser.getWithRecordLocations ( () -> {
        throw new IllegalStateException ("expected");
      });
    }
    catch (final IllegalStateException ex)
    {
      assertEquals ("expected", ex.getMessage ());
    }
    assertFalse (XdmParser.isRecordLocationsByDefault ());
    assertEquals (0, new XdmParser ().parse (XML).getChildAtIndex (0).getLine ());
  }
}
