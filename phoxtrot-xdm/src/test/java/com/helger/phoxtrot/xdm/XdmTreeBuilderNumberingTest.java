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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.helpers.AttributesImpl;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.jaxp.JdkSchemaProvider;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSource;

/**
 * Test class for the document order numbering that {@link XdmTreeBuilder}
 * gives while it builds: it must be the numbering of
 * {@link IXdmNode#assignDocumentOrder()} on the finished tree.
 *
 * @author Philip Helger
 */
public final class XdmTreeBuilderNumberingTest
{
  private static final String XSD = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t' elementFormDefault='qualified'>" +
                                    "<xs:element name='root'><xs:complexType><xs:sequence>" +
                                    "<xs:element name='int' type='xs:int' maxOccurs='unbounded'/>" +
                                    "<xs:element name='dec' minOccurs='0'><xs:complexType><xs:simpleContent><xs:extension base='xs:decimal'>" +
                                    "<xs:attribute name='def' type='xs:int' default='3'/>" +
                                    "</xs:extension></xs:simpleContent></xs:complexType></xs:element>" +
                                    "</xs:sequence></xs:complexType></xs:element></xs:schema>";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * @return Kind, order and end order of every node and attribute of the tree
   *         in pre-order, one line each.
   */
  @NonNull
  private static ICommonsList <String> _getNumbering (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      ret.add (aCur + " " + aCur.getOrder () + "-" + aCur.getEndOrder ());
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
      {
        final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
        ret.add (aAttr + " " + aAttr.getOrder () + "-" + aAttr.getEndOrder ());
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * Check that the document is numbered as built and that a second numbering
   * of the finished tree gives the same numbers.
   */
  private static void _assertSameAsSecondPass (@NonNull final IXdmNode aDoc)
  {
    final AbstractXdmNode.TreeStamp aTree = ((AbstractXdmNode) aDoc).internalGetTree ();
    assertNotNull (aTree);
    final ICommonsList <String> aBuilt = _getNumbering (aDoc);
    aDoc.assignDocumentOrder ();
    assertNotSame (aTree, ((AbstractXdmNode) aDoc).internalGetTree ());
    assertEquals (aBuilt, _getNumbering (aDoc));
  }

  private static void _assertSameAsSecondPass (@NonNull final String sXml) throws XdmParseException
  {
    _assertSameAsSecondPass (new XdmParser ().parse (sXml));
  }

  @Test
  public void testVariedDocuments () throws XdmParseException
  {
    _assertSameAsSecondPass ("<r/>");
    _assertSameAsSecondPass ("<r a='1' b='2'/>");
    _assertSameAsSecondPass ("<?pi before?><!-- c0 --><r xmlns='urn:d' xmlns:p='urn:p' p:a='1' a='2'>" +
                             "text<p:x b='3'><y xmlns=''>in<!-- c1 --><?pi in?></y>tail</p:x>" +
                             "<![CDATA[<cdata>]]>more<z/>\n  </r><!-- c2 --><?pi after?>");
    _assertSameAsSecondPass ("<r>  <a>  </a>\n<b/>\t</r>");
    // DTD: comments in the DTD, default attributes, whitespace in element
    // content (reported as ignorable and dropped), entities with markup
    _assertSameAsSecondPass ("<!DOCTYPE r [<!-- in DTD --><!ELEMENT r (e*)><!ELEMENT e (#PCDATA|x)*><!ELEMENT x EMPTY>" +
                             "<!ATTLIST e d CDATA 'def' i ID #IMPLIED><!ATTLIST x f CDATA 'g'>" +
                             "<!ENTITY ent '<x/>t<x/>'>]>" +
                             "<r>\n  <e i='a1'>&ent;</e>\n  <e d='set'>v&ent;w</e>\n</r>");
    // Deep nesting
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < 300; ++i)
      aSB.append ("<n a='").append (i).append ("'>t");
    for (int i = 0; i < 300; ++i)
      aSB.append ("</n>");
    _assertSameAsSecondPass (aSB.toString ());
  }

  private static int _getMaxElementDepth (@NonNull final String sXml) throws XdmParseException
  {
    return ((XdmDocumentNode) new XdmParser ().parse (sXml)).getMaxElementDepth ();
  }

  @Test
  public void testMaxElementDepth () throws XdmParseException
  {
    assertEquals (1, _getMaxElementDepth ("<r/>"));
    assertEquals (1, _getMaxElementDepth ("<!-- c --><r a='1'>t<?p?></r><!-- d -->"));
    assertEquals (4, _getMaxElementDepth ("<a>t<b/>u<c><d>v</d></c><e><f><g/></f></e><h/></a>"));
    // Entity content counts like any other content
    assertEquals (3, _getMaxElementDepth ("<!DOCTYPE r [<!ENTITY ent '<x><y/></x>'>]><r>&ent;</r>"));
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (int i = 0; i < 300; ++i)
      aSB.append ("<n>t");
    for (int i = 0; i < 300; ++i)
      aSB.append ("</n>");
    aSB.append ("<x/></r>");
    assertEquals (301, _getMaxElementDepth (aSB.toString ()));

    // Dropped with the numbering: by a change, and by a new numbering
    final XdmDocumentNode aDoc = (XdmDocumentNode) new XdmParser ().parse ("<r><x/></r>");
    assertEquals (2, aDoc.getMaxElementDepth ());
    aDoc.getChildAtIndex (0).appendChild (IXdmNode.createText ("t"));
    assertEquals (-1, aDoc.getMaxElementDepth ());
    final XdmDocumentNode aDoc2 = (XdmDocumentNode) new XdmParser ().parse ("<r><x/></r>");
    aDoc2.assignDocumentOrder ();
    assertEquals (-1, aDoc2.getMaxElementDepth ());
    // A built document
    final IXdmNode aDoc3 = IXdmNode.createDocument ();
    aDoc3.appendChild (IXdmNode.createElement (new XdmQName ("r")));
    assertEquals (-1, ((XdmDocumentNode) aDoc3).getMaxElementDepth ());
  }

  @Test
  public void testEndOrders () throws XdmParseException
  {
    // The numbers of XdmDocumentOrderTest.testEndOrder, as built
    final IXdmNode aDoc = new XdmParser ().parse ("<r a='1'><x b='2'><y/></x><x c='3'/>t<z/></r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    assertEquals (0, aDoc.getOrder ());
    assertEquals (9, aDoc.getEndOrder ());
    assertEquals (1, aR.getOrder ());
    assertEquals (9, aR.getEndOrder ());
    assertEquals (5, aR.getChildAtIndex (0).getEndOrder ());
    assertEquals (6, aR.getChildAtIndex (1).getOrder ());
    assertEquals (7, aR.getChildAtIndex (1).getEndOrder ());
    // The name index belongs to the numbering of the finished tree
    assertEquals (2, ((XdmDocumentNode) aDoc).getElementsByName (new XdmQName ("x")).size ());
  }

  @Test
  public void testExternalEntity () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    final Path aMain = aDir.resolve ("main.xml");
    Files.writeString (aMain,
                       "<!DOCTYPE r [ <!ENTITY e SYSTEM 'ent.xml'> ]><r a='1'>&e;<x/>&e;</r>",
                       StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("ent.xml"), "<a b='c'>t<b/></a><?p x?>", StandardCharsets.UTF_8);
    _assertSameAsSecondPass (new XdmParser ().setLoadExternalEntities (true).parse (aMain));
  }

  @Test
  public void testSchemaValidated () throws Exception
  {
    // Validation adds the default attribute and drops whitespace in
    // element-only content after building
    final XdmParser aParser = new XdmParser ().setSchemaSet (new JdkSchemaProvider ().compile (new CommonsArrayList <> (new XdmSchemaSource (null,
                                                                                                                                              XSD))));
    final IXdmNode aDoc = aParser.parse ("<root xmlns='urn:t'>\n  <int>1</int>\n  <int>2</int>\n  <dec>1.5</dec>\n</root>");
    final IXdmNode aDec = aDoc.getChildAtIndex (0).getChildAtIndex (2);
    assertNotNull (aDec.getAttribute ("", "def"));
    _assertSameAsSecondPass (aDoc);
  }

  @Test
  public void testChangeWhileBuilding ()
  {
    // A wrapping handler (as JaxpTypeAnnotator with Apache Xerces-J) removes
    // a whitespace text node while the tree is built: the numbering made so
    // far is removed and the finished tree is numbered at the end
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
    aBuilder.startDocument ();
    aBuilder.startElement ("", "r", "r", new AttributesImpl ());
    aBuilder.characters ("  ".toCharArray (), 0, 2);
    final AttributesImpl aAttrs = new AttributesImpl ();
    aAttrs.addAttribute ("", "a", "a", "CDATA", "1");
    aBuilder.startElement ("", "x", "x", aAttrs);
    aBuilder.endElement ("", "x", "x");
    final IXdmNode aRoot = aBuilder.getCurrentNode ();
    aRoot.removeChild (aRoot.getChildAtIndex (0));
    aBuilder.characters ("t".toCharArray (), 0, 1);
    aBuilder.startElement ("", "y", "y", new AttributesImpl ());
    aBuilder.endElement ("", "y", "y");
    aBuilder.endElement ("", "r", "r");
    aBuilder.endDocument ();
    final IXdmNode aDoc = aBuilder.getDocument ();
    // doc, r, x, @a, text, y
    assertEquals (5, aDoc.getEndOrder ());
    assertEquals (2, aRoot.getChildAtIndex (0).getOrder ());
    assertEquals (4, aRoot.getChildAtIndex (1).getOrder ());
    assertTrue (aRoot.getChildAtIndex (2).getOrder () > aRoot.getChildAtIndex (1).getOrder ());
    // Numbered at the end: the depth is not known
    assertEquals (-1, ((XdmDocumentNode) aDoc).getMaxElementDepth ());
    _assertSameAsSecondPass (aDoc);
  }
}
