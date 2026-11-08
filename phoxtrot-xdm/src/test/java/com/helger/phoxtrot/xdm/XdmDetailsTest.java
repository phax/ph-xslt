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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.helger.collection.commons.ICommonsList;

/**
 * Test class for the properties of elements and attributes that live in the
 * details holder: the ID kind and the namespace nodes of an element.
 *
 * @author Philip Helger
 */
public final class XdmDetailsTest
{
  @Test
  public void testIDKind () throws XdmParseException
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<!DOCTYPE r [<!ATTLIST r i ID #IMPLIED r IDREFS #IMPLIED>]>" +
                                                  "<r i='a' r=' a  b ' xml:id=' x ' o='1'/>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertEquals (EIDKind.NONE, aR.getIDKind ());
    assertEquals (EIDKind.ID, aR.getAttribute (null, "i").getIDKind ());
    assertEquals (EIDKind.IDREFS, aR.getAttribute (null, "r").getIDKind ());
    assertEquals ("a b", aR.getAttribute (null, "r").getValue ());
    assertEquals (EIDKind.ID, aR.getAttribute (XdmConstants.NS_XML, "id").getIDKind ());
    assertEquals (EIDKind.NONE, aR.getAttribute (null, "o").getIDKind ());

    // Set and reset on an element and an attribute
    for (final IXdmNode aNode : new IXdmNode [] { IXdmNode.createElement (new XdmQName ("e")),
                                                  IXdmNode.createAttribute (new XdmQName ("a"), "v") })
    {
      assertEquals (EIDKind.NONE, aNode.getIDKind ());
      aNode.setIDKind (EIDKind.NONE);
      assertEquals (EIDKind.NONE, aNode.getIDKind ());
      aNode.setIDKind (EIDKind.IDREF);
      assertEquals (EIDKind.IDREF, aNode.getIDKind ());
      aNode.setBaseUri ("urn:b");
      assertEquals (EIDKind.IDREF, aNode.getIDKind ());
      aNode.setIDKind (EIDKind.NONE);
      assertEquals (EIDKind.NONE, aNode.getIDKind ());
      assertEquals ("urn:b", aNode.getBaseUri ());
    }
  }

  @Test
  public void testNamespaceNodes () throws XdmParseException
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r xmlns:p='urn:p'><e/></r>");
    final IXdmNode aE = aDoc.getChildAtIndex (0).getChildAtIndex (0);
    final ICommonsList <IXdmNode> aFirst = aE.getNamespaceNodes ();
    // xml and p
    assertEquals (2, aFirst.size ());
    for (final IXdmNode aNS : aFirst)
      assertSame (aE, aNS.getParent ());
    // The same nodes on the next call, in a new list
    final ICommonsList <IXdmNode> aSecond = aE.getNamespaceNodes ();
    assertNotSame (aFirst, aSecond);
    assertEquals (aFirst, aSecond);
    for (int i = 0; i < aFirst.size (); ++i)
      assertSame (aFirst.get (i), aSecond.get (i));

    // A declaration drops them, and they are built again
    aE.declareNamespace ("q", "urn:q");
    final ICommonsList <IXdmNode> aThird = aE.getNamespaceNodes ();
    assertEquals (3, aThird.size ());
    assertNotSame (aFirst.get (0), aThird.get (0));
  }
}
