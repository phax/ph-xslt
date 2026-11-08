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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for {@link IXdmNode#hasDeclaredNamespaces()} and
 * {@link IXdmNode#getDeclaredNamespaceUri(String)}, which must agree with
 * {@link IXdmNode#getDeclaredNamespaces()}.
 *
 * @author Philip Helger
 */
public final class XdmDeclaredNamespacesTest
{
  private static void _assertConsistent (final IXdmNode aNode, final String... aPrefixes)
  {
    assertEquals (!aNode.getDeclaredNamespaces ().isEmpty (), aNode.hasDeclaredNamespaces ());
    for (final String sPrefix : aPrefixes)
      assertEquals (aNode.getDeclaredNamespaces ().get (sPrefix), aNode.getDeclaredNamespaceUri (sPrefix));
  }

  @Test
  public void testParsed () throws XdmParseException
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r xmlns='urn:d' xmlns:p='urn:p'><x/><y xmlns=''/><p:z xmlns:q='urn:q'/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertFalse (aDoc.hasDeclaredNamespaces ());
    assertTrue (aR.hasDeclaredNamespaces ());
    assertEquals ("urn:d", aR.getDeclaredNamespaceUri (""));
    assertEquals ("urn:p", aR.getDeclaredNamespaceUri ("p"));
    assertNull (aR.getDeclaredNamespaceUri ("q"));
    _assertConsistent (aR, "", "p", "q", "xml");
    final IXdmNode aX = aR.getChildAtIndex (0);
    assertFalse (aX.hasDeclaredNamespaces ());
    assertNull (aX.getDeclaredNamespaceUri ("p"));
    _assertConsistent (aX, "", "p");
    // An undeclaration counts as a declaration and yields the empty string
    final IXdmNode aY = aR.getChildAtIndex (1);
    assertTrue (aY.hasDeclaredNamespaces ());
    assertEquals ("", aY.getDeclaredNamespaceUri (""));
    _assertConsistent (aY, "", "p");
    _assertConsistent (aR.getChildAtIndex (2), "", "p", "q");
    // Kinds without declarations
    final IXdmNode aText = IXdmNode.createText ("t");
    assertFalse (aText.hasDeclaredNamespaces ());
    assertNull (aText.getDeclaredNamespaceUri (""));
  }

  @Test
  public void testDeclared ()
  {
    final IXdmNode aElement = IXdmNode.createElement (new XdmQName ("e"));
    // Other properties create the details holder, still without declarations
    aElement.setBaseUri ("urn:base");
    assertFalse (aElement.hasDeclaredNamespaces ());
    assertNull (aElement.getDeclaredNamespaceUri ("a"));
    aElement.declareNamespace ("a", "urn:a");
    assertTrue (aElement.hasDeclaredNamespaces ());
    assertEquals ("urn:a", aElement.getDeclaredNamespaceUri ("a"));
    _assertConsistent (aElement, "a", "b", "");
  }
}
