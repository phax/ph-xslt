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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import org.junit.Test;
import org.xml.sax.InputSource;

import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * Test class for classes {@link XdmParser}, {@link XdmTreeBuilder} and
 * {@link XdmNode}.
 *
 * @author Philip Helger
 */
public final class XdmParserTest
{
  private static XdmNode _parse (final String s) throws XdmParseException
  {
    return new XdmParser ().parse (s);
  }

  @Test
  public void testStructure () throws XdmParseException
  {
    final XdmNode aDoc = _parse ("<!--c1--><?pi data?>\n<a xmlns='urn:d' xmlns:p='urn:p' x='1' p:y='2'>t1<b/>t2<!--c2--></a>");
    assertTrue (aDoc.isDocument ());
    assertEquals (3, aDoc.getChildCount ());
    assertEquals (ENodeKind.COMMENT, aDoc.getChildAtIndex (0).getKind ());
    assertEquals ("c1", aDoc.getChildAtIndex (0).getValue ());
    assertEquals (ENodeKind.PROCESSING_INSTRUCTION, aDoc.getChildAtIndex (1).getKind ());
    assertEquals ("pi", aDoc.getChildAtIndex (1).getName ().localName ());
    assertEquals ("data", aDoc.getChildAtIndex (1).getValue ());

    final XdmNode aA = aDoc.getChildAtIndex (2);
    assertEquals (new XdmQName ("urn:d", "a"), aA.getName ());
    assertEquals (4, aA.getChildCount ());
    assertEquals (2, aA.getAttributeCount ());
    assertEquals ("1", aA.getAttribute ("", "x").getValue ());
    assertEquals ("2", aA.getAttribute ("urn:p", "y").getValue ());
    assertEquals ("p", aA.getAttribute ("urn:p", "y").getName ().prefix ());
    assertNull (aA.getAttribute ("urn:p", "x"));
    assertSame (aDoc, aA.getParent ());
    assertSame (aDoc, aA.getOwnerDocument ());
    assertEquals ("t1t2", aA.getStringValue ());
    assertEquals ("t1t2", aDoc.getStringValue ());
  }

  @Test
  public void testNamespaces () throws XdmParseException
  {
    final XdmNode aA = _parse ("<a xmlns='urn:d' xmlns:p='urn:p'><b xmlns='' xmlns:q='urn:q'><c/></b></a>").getChildAtIndex (0);
    final XdmNode aC = aA.getChildAtIndex (0).getChildAtIndex (0);

    assertEquals (2, aA.getDeclaredNamespaces ().size ());
    assertEquals ("urn:d", aA.lookupNamespaceUri (""));
    assertNull (aC.lookupNamespaceUri (""));
    assertEquals ("urn:p", aC.lookupNamespaceUri ("p"));
    assertEquals ("urn:q", aC.lookupNamespaceUri ("q"));
    assertNull (aA.lookupNamespaceUri ("q"));
    assertEquals (XdmConstants.NS_XML, aC.lookupNamespaceUri ("xml"));

    final ICommonsOrderedMap <String, String> aScope = aC.getInScopeNamespaces ();
    assertEquals (3, aScope.size ());
    assertTrue (aScope.containsKey ("xml"));
    assertTrue (aScope.containsKey ("p"));
    assertTrue (aScope.containsKey ("q"));
    // The default namespace was undeclared on b
    assertTrue (!aScope.containsKey (""));
  }

  @Test
  public void testTextIsMergedAndCDATAIsText () throws XdmParseException
  {
    final XdmNode aA = _parse ("<a>one<![CDATA[two]]>three&amp;&#x41;</a>").getChildAtIndex (0);
    assertEquals (1, aA.getChildCount ());
    assertEquals ("onetwothree&A", aA.getChildAtIndex (0).getValue ());
  }

  @Test
  public void testWhitespaceIsKept () throws XdmParseException
  {
    final XdmNode aA = _parse ("<a> <b/> </a>").getChildAtIndex (0);
    assertEquals (3, aA.getChildCount ());
    assertEquals (" ", aA.getChildAtIndex (0).getValue ());
  }

  @Test
  public void testDtdCommentsAreDropped () throws XdmParseException
  {
    final XdmNode aDoc = _parse ("<!DOCTYPE a [<!-- in dtd --><!ELEMENT a ANY>]><a/>");
    assertEquals (1, aDoc.getChildCount ());
    assertTrue (aDoc.getChildAtIndex (0).isElement ());
  }

  @Test
  public void testInternalEntity () throws XdmParseException
  {
    final XdmNode aA = _parse ("<!DOCTYPE a [<!ENTITY e 'text'>]><a>&e;</a>").getChildAtIndex (0);
    assertEquals ("text", aA.getStringValue ());
  }

  @Test
  public void testIDKinds () throws XdmParseException
  {
    final XdmNode aA = _parse ("<!DOCTYPE a [<!ATTLIST a i ID #IMPLIED r IDREF #IMPLIED rs IDREFS #IMPLIED n CDATA #IMPLIED>]>" +
                               "<a i=' x ' r='y' rs=' p   q ' n=' keep  me ' xml:id='  z  '/>").getChildAtIndex (0);
    assertEquals (EIDKind.ID, aA.getAttribute ("", "i").getIDKind ());
    assertEquals ("x", aA.getAttribute ("", "i").getValue ());
    assertEquals (EIDKind.IDREF, aA.getAttribute ("", "r").getIDKind ());
    assertEquals (EIDKind.IDREFS, aA.getAttribute ("", "rs").getIDKind ());
    assertEquals ("p q", aA.getAttribute ("", "rs").getValue ());
    assertEquals (EIDKind.NONE, aA.getAttribute ("", "n").getIDKind ());
    assertEquals (" keep  me ", aA.getAttribute ("", "n").getValue ());
    assertEquals (EIDKind.ID, aA.getAttribute (XdmConstants.NS_XML, "id").getIDKind ());
    assertEquals ("z", aA.getAttribute (XdmConstants.NS_XML, "id").getValue ());
  }

  @Test
  public void testLocation () throws XdmParseException
  {
    final XdmNode aA = _parse ("<a>\n  <b/>\n</a>").getChildAtIndex (0);
    assertEquals (1, aA.getLine ());
    // The location is the end of the start tag
    assertEquals (2, aA.getChildAtIndex (1).getLine ());
  }

  @Test
  public void testDocumentOrder () throws XdmParseException
  {
    final XdmNode aDoc = _parse ("<a x='1' y='2'><b z='3'/><c/></a>");
    final XdmNode aA = aDoc.getChildAtIndex (0);
    final XdmNode aB = aA.getChildAtIndex (0);
    final XdmNode aC = aA.getChildAtIndex (1);
    assertTrue (XdmNode.compareDocumentOrder (aDoc, aA) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aA, aA.getAttributeAtIndex (0)) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aA.getAttributeAtIndex (0), aA.getAttributeAtIndex (1)) < 0);
    // Attributes come before the children of their element
    assertTrue (XdmNode.compareDocumentOrder (aA.getAttributeAtIndex (1), aB) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aB, aB.getAttributeAtIndex (0)) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aB.getAttributeAtIndex (0), aC) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aC, aB) > 0);
    assertEquals (0, XdmNode.compareDocumentOrder (aC, aC));
  }

  @Test
  public void testDocumentOrderAcrossTrees () throws XdmParseException
  {
    final XdmNode aFirst = _parse ("<a/>");
    final XdmNode aSecond = _parse ("<a/>");
    assertTrue (XdmNode.compareDocumentOrder (aFirst, aSecond) < 0);
    assertTrue (XdmNode.compareDocumentOrder (aSecond.getChildAtIndex (0), aFirst.getChildAtIndex (0)) > 0);
  }

  @Test
  public void testUnnumberedTreeIsNumberedOnDemand ()
  {
    final XdmNode aA = XdmNode.createElement (new XdmQName ("a"));
    final XdmNode aB = XdmNode.createElement (new XdmQName ("b"));
    final XdmNode aC = XdmNode.createElement (new XdmQName ("c"));
    aA.appendChild (aB);
    aA.appendChild (aC);
    assertTrue (XdmNode.compareDocumentOrder (aC, aB) > 0);
  }

  @Test
  public void testDeepTreeDoesNotOverflowTheStack () throws XdmParseException
  {
    final int nDepth = 9000;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("<e>");
    aSB.append ("x");
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("</e>");
    final XdmNode aDoc = _parse (aSB.toString ());
    assertEquals ("x", aDoc.getStringValue ());
  }

  @Test(expected = XdmParseException.class)
  public void testElementDepthLimit () throws XdmParseException
  {
    new XdmParser ().setMaxElementDepth (3).parse ("<a><b><c><d/></c></b></a>");
  }

  @Test
  public void testDocumentUri () throws XdmParseException
  {
    final InputSource aSource = new InputSource (new StringReader ("<a/>"));
    aSource.setSystemId ("urn:test:doc");
    final XdmNode aDoc = new XdmParser ().parse (aSource);
    assertEquals ("urn:test:doc", aDoc.getDocumentUri ());
  }

  @Test(expected = XdmParseException.class)
  public void testNotWellFormed () throws XdmParseException
  {
    _parse ("<a><b></a>");
  }

  @Test
  public void testExternalEntityIsNotLoadedByDefault () throws XdmParseException
  {
    final XdmNode aDoc = _parse ("<!DOCTYPE a [<!ENTITY e SYSTEM 'file:///etc/hostname'>]><a>&e;</a>");
    // A skipped external entity must not leak file content
    assertEquals ("", aDoc.getStringValue ());
  }
}
