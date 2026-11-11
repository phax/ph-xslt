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
package com.helger.phoxtrot.xdm.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * Test class for {@link XdmDomView}.
 *
 * @author Philip Helger
 */
public final class XdmDomViewTest
{
  private static final String [] DOCUMENTS = { "<r/>",
                                               "<?pi before?><!-- c1 --><r a='1' b='x &amp; y'>text<e/>tail</r><!-- after --><?pi after?>",
                                               "<r xmlns='urn:d' xmlns:p='urn:p'><p:e p:a='1' b='2'><x xmlns=''><y/></x></p:e></r>",
                                               "<p:r xmlns:p='urn:p' xmlns:q='urn:q'><q:e q:a='v' xml:lang='de'/><e xmlns:p='urn:other'><p:f/></e></p:r>",
                                               "<r xml:base='http://example.org/base/'><e xml:base='sub/'><f/></e></r>",
                                               "<r>x<![CDATA[<cdata> & ]]>y<!--c-->z</r>",
                                               "<!DOCTYPE r [<!ENTITY ent 'expanded <b>bold</b> text'><!ATTLIST r id ID #IMPLIED>]><r id='i1'>a &ent; b</r>",
                                               "<r>\n  <e>\n    <f/>\n  </e>\n</r>",
                                               "<r xmlns:a='urn:a'><a:e xmlns:a='urn:a2' a:x='1'/></r>" };

  private static void _assertDomException (final short nCode, @NonNull final Runnable aAction)
  {
    try
    {
      aAction.run ();
      fail ("DOMException expected");
    }
    catch (final DOMException ex)
    {
      assertEquals (nCode, ex.code);
    }
  }

  @NonNull
  private static String _serialize (@NonNull final Node aNode) throws Exception
  {
    final Transformer aTransformer = TransformerFactory.newDefaultInstance ().newTransformer ();
    final StringWriter aSW = new StringWriter ();
    aTransformer.transform (new DOMSource (aNode), new StreamResult (aSW));
    return aSW.toString ();
  }

  /**
   * @return A document with nested elements of the depth, built without a parser (which limits the depth).
   */
  @NonNull
  private static IXdmNode _createDeepDocument (final int nDepth)
  {
    final IXdmNode ret = IXdmNode.createDocument ();
    IXdmNode aParent = ret;
    for (int i = 0; i < nDepth; ++i)
    {
      final IXdmNode aElement = IXdmNode.createElement (new XdmQName ("d"));
      aParent.appendChild (aElement);
      aParent = aElement;
    }
    aParent.appendChild (IXdmNode.createText ("bottom"));
    return ret;
  }

  @Test
  public void testConvertedBackMatchesOriginal () throws Exception
  {
    for (final String sXml : DOCUMENTS)
    {
      final IXdmNode aOriginal = new XdmParser ().parse (sXml);
      final Document aView = new XdmDomView (aOriginal).getDocument ();
      assertNotNull (aView);
      // DomToXdm reads the view through the DOM API only
      assertEquals (sXml, DomBridgeTest.describe (aOriginal), DomBridgeTest.describe (DomToXdm.convertDocument (aView, null)));
    }
  }

  @Test
  public void testEqualToCopiedDom () throws Exception
  {
    for (final String sXml : DOCUMENTS)
    {
      final IXdmNode aOriginal = new XdmParser ().parse (sXml);
      final Document aView = new XdmDomView (aOriginal).getDocument ();
      final Document aCopy = XdmToDom.toDocument (aOriginal);
      assertTrue (sXml, aView.isEqualNode (aCopy));
      assertTrue (sXml, aCopy.isEqualNode (aView));
      // The JDK serialiser reads the view through the DOM API only; the view has no document type, so the ID
      // attributes of a DTD are not IDs after parsing the output again
      if (!sXml.startsWith ("<!DOCTYPE"))
        assertEquals (sXml,
                      DomBridgeTest.describe (aOriginal),
                      DomBridgeTest.describe (new XdmParser ().parse (_serialize (aView))));
    }
    final IXdmNode aOne = new XdmParser ().parse ("<r a='1'><e/></r>");
    final IXdmNode aOther = new XdmParser ().parse ("<r a='2'><e/></r>");
    assertFalse (new XdmDomView (aOne).getDocument ().isEqualNode (new XdmDomView (aOther).getDocument ()));
  }

  @Test
  public void testNavigationAndIdentity () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<?pi d?><r a='1' b='2'>t1<e>t2</e><!--c--><f/></r>");
    final XdmDomView aView = new XdmDomView (aDoc);
    final Document aDom = aView.getDocument ();
    assertSame (aDom, aView.getDocument ());
    assertSame (aDom, aView.getNode (aDoc));
    assertNull (aDom.getOwnerDocument ());
    assertNull (aDom.getParentNode ());
    assertEquals ("#document", aDom.getNodeName ());
    assertNull (aDom.getTextContent ());

    final ProcessingInstruction aPI = (ProcessingInstruction) aDom.getFirstChild ();
    assertEquals ("pi", aPI.getTarget ());
    assertEquals ("d", aPI.getData ());
    final Element aR = aDom.getDocumentElement ();
    assertSame (aR, aPI.getNextSibling ());
    assertSame (aPI, aR.getPreviousSibling ());
    assertSame (aR, aDom.getLastChild ());
    assertNull (aR.getNextSibling ());
    assertSame (aDom, aR.getParentNode ());
    assertSame (aDom, aR.getOwnerDocument ());
    assertEquals ("t1t2", aR.getTextContent ());

    final NodeList aChildren = aR.getChildNodes ();
    assertEquals (4, aChildren.getLength ());
    assertEquals (Node.TEXT_NODE, aChildren.item (0).getNodeType ());
    assertEquals ("t1", ((Text) aChildren.item (0)).getWholeText ());
    assertEquals ("e", aChildren.item (1).getNodeName ());
    assertEquals (Node.COMMENT_NODE, aChildren.item (2).getNodeType ());
    assertEquals ("c", aChildren.item (2).getNodeValue ());
    assertNull (aChildren.item (4));
    for (int i = 0; i < aChildren.getLength (); ++i)
    {
      assertSame (aChildren.item (i), aChildren.item (i));
      assertSame (aR, aChildren.item (i).getParentNode ());
    }
    // A node reached directly finds its siblings
    final IXdmNode aXdmE = aDoc.getChildAtIndex (1).getChildAtIndex (1);
    final Node aE = aView.getNode (aXdmE);
    assertSame (aChildren.item (1), aE);
    assertSame (aChildren.item (0), aE.getPreviousSibling ());
    assertSame (aChildren.item (2), aE.getNextSibling ());
    assertSame (aXdmE, XdmDomView.getXdmNode (aE));
    assertSame (aXdmE, ((AbstractXdmDomNode) aE).getXdmNode ());
    assertTrue (aE.isSameNode (aChildren.item (1)));
    assertNull (XdmDomView.getXdmNode (XdmToDom.createEmptyDocument ()));
    assertNull (XdmDomView.getXdmNode (null));

    // Substrings of character data
    final Text aT = (Text) aChildren.item (0);
    assertEquals ("1", aT.substringData (1, 5));
    assertEquals (2, aT.getLength ());
    _assertDomException (DOMException.INDEX_SIZE_ERR, () -> aT.substringData (3, 1));

    try
    {
      aView.getNode (new XdmParser ().parse ("<r/>"));
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  @Test
  public void testAttributesAndNamespaces () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<p:r xmlns:p='urn:p' xmlns='urn:d' p:a='1' b='2'><e xmlns='' c='3'/></p:r>");
    final XdmDomView aView = new XdmDomView (aDoc);
    final Element aR = aView.getDocument ().getDocumentElement ();
    assertEquals ("p:r", aR.getTagName ());
    assertEquals ("urn:p", aR.getNamespaceURI ());
    assertEquals ("p", aR.getPrefix ());
    assertEquals ("r", aR.getLocalName ());
    assertTrue (aR.hasAttributes ());

    final NamedNodeMap aAttrs = aR.getAttributes ();
    assertSame (aAttrs, aR.getAttributes ());
    assertEquals (4, aAttrs.getLength ());
    final Attr aXmlnsP = (Attr) aAttrs.item (0);
    assertEquals ("xmlns:p", aXmlnsP.getName ());
    assertEquals (XdmConstants.NS_XMLNS, aXmlnsP.getNamespaceURI ());
    assertEquals ("xmlns", aXmlnsP.getPrefix ());
    assertEquals ("p", aXmlnsP.getLocalName ());
    assertEquals ("urn:p", aXmlnsP.getValue ());
    assertSame (aR, aXmlnsP.getOwnerElement ());
    final Attr aXmlns = (Attr) aAttrs.getNamedItemNS (XdmConstants.NS_XMLNS, "xmlns");
    assertEquals ("xmlns", aXmlns.getName ());
    assertNull (aXmlns.getPrefix ());
    assertEquals ("urn:d", aXmlns.getValue ());

    assertEquals ("1", aR.getAttributeNS ("urn:p", "a"));
    assertEquals ("1", aR.getAttribute ("p:a"));
    assertEquals ("2", aR.getAttributeNS (null, "b"));
    assertEquals ("2", aR.getAttributeNS ("", "b"));
    assertEquals ("", aR.getAttribute ("missing"));
    assertTrue (aR.hasAttributeNS ("urn:p", "a"));
    assertFalse (aR.hasAttribute ("a"));
    final Attr aA = aR.getAttributeNodeNS ("urn:p", "a");
    assertSame (aA, aR.getAttributeNode ("p:a"));
    assertSame (aR, aA.getOwnerElement ());
    assertNull (aA.getParentNode ());
    assertNull (aA.getNextSibling ());
    assertTrue (aA.getSpecified ());
    assertFalse (aA.isId ());
    assertEquals ("1", aA.getTextContent ());
    assertSame (aA, aView.getNode (aDoc.getChildAtIndex (0).getAttributeAtIndex (0)));

    assertEquals ("urn:p", aR.lookupNamespaceURI ("p"));
    assertEquals ("urn:d", aR.lookupNamespaceURI (null));
    assertEquals ("p", aR.lookupPrefix ("urn:p"));
    assertTrue (aR.isDefaultNamespace ("urn:d"));
    assertEquals ("urn:p", aA.lookupNamespaceURI ("p"));
    final Element aE = (Element) aR.getFirstChild ();
    assertNull (aE.getNamespaceURI ());
    assertNull (aE.lookupNamespaceURI (null));
    assertTrue (aE.isDefaultNamespace (null));
    assertEquals ("", ((Attr) aE.getAttributes ().getNamedItem ("xmlns")).getValue ());
    assertEquals ("urn:d", aView.getDocument ().lookupNamespaceURI (null));

    // The namespace axis
    final IXdmNode aNsNode = aDoc.getChildAtIndex (0)
                                 .getNamespaceNodes ()
                                 .findFirst (x -> x.getName () != null && x.getName ().localName ().equals ("p"));
    final Attr aNsAttr = (Attr) aView.getNode (aNsNode);
    assertEquals ("xmlns:p", aNsAttr.getName ());
    assertEquals ("urn:p", aNsAttr.getValue ());
    assertSame (aR, aNsAttr.getOwnerElement ());

    // The DOM bridge finds the counterpart of a view attribute by identity
    assertTrue (DomToXdm.convert (aA, null).getName ().isSameName ("urn:p", "a"));
  }

  @Test
  public void testIdsAndTypes () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<!DOCTYPE r [<!ATTLIST e id ID #IMPLIED>]><r><e id='x1'/><e id='x2'/></r>");
    final Document aDom = new XdmDomView (aDoc).getDocument ();
    final Element aX2 = aDom.getElementById ("x2");
    assertNotNull (aX2);
    assertSame (aDom.getDocumentElement ().getLastChild (), aX2);
    assertTrue (aX2.getAttributeNode ("id").isId ());
    assertNull (aDom.getElementById ("x3"));
    assertNull (aDom.getDoctype ());
    assertNull (aX2.getSchemaTypeInfo ().getTypeName ());
    assertFalse (aX2.getSchemaTypeInfo ().isDerivedFrom ("urn:x", "t", 0));
    assertTrue (aDom.getImplementation ().hasFeature ("Core", "3.0"));
    assertFalse (aDom.getImplementation ().hasFeature ("LS", "3.0"));
  }

  @Test
  public void testElementsByTagName () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r xmlns:p='urn:p'><p:e/><e><e/><p:e/></e></r>");
    final Document aDom = new XdmDomView (aDoc).getDocument ();
    assertEquals (5, aDom.getElementsByTagName ("*").getLength ());
    assertEquals (2, aDom.getElementsByTagName ("e").getLength ());
    assertEquals (2, aDom.getElementsByTagName ("p:e").getLength ());
    assertEquals (4, aDom.getElementsByTagNameNS ("*", "e").getLength ());
    assertEquals (2, aDom.getElementsByTagNameNS ("urn:p", "*").getLength ());
    assertEquals (2, aDom.getElementsByTagNameNS (null, "e").getLength ());
    final Element aR = aDom.getDocumentElement ();
    assertEquals (4, aR.getElementsByTagName ("*").getLength ());
    // Document order
    final NodeList aAll = aDom.getElementsByTagName ("*");
    assertSame (aR, aAll.item (0));
    assertEquals ("p:e", aAll.item (1).getNodeName ());
    assertEquals ("e", aAll.item (2).getNodeName ());
    assertEquals ("e", aAll.item (3).getNodeName ());
    assertEquals ("p:e", aAll.item (4).getNodeName ());
  }

  @Test
  public void testCompareDocumentPosition () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r xmlns:p='urn:p' a='1' b='2'><e><f/></e><g/></r>");
    final XdmDomView aView = new XdmDomView (aDoc);
    final Element aR = aView.getDocument ().getDocumentElement ();
    final Node aE = aR.getFirstChild ();
    final Node aF = aE.getFirstChild ();
    final Node aG = aR.getLastChild ();
    final Node aA = aR.getAttributeNode ("a");
    final Node aB = aR.getAttributeNode ("b");
    final Node aXmlns = aR.getAttributeNode ("xmlns:p");

    assertEquals (0, aR.compareDocumentPosition (aR));
    assertEquals (Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING,
                  aR.compareDocumentPosition (aF));
    assertEquals (Node.DOCUMENT_POSITION_CONTAINS | Node.DOCUMENT_POSITION_PRECEDING, aF.compareDocumentPosition (aR));
    assertEquals (Node.DOCUMENT_POSITION_FOLLOWING, aF.compareDocumentPosition (aG));
    assertEquals (Node.DOCUMENT_POSITION_PRECEDING, aG.compareDocumentPosition (aF));
    assertEquals (Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING,
                  aR.compareDocumentPosition (aA));
    assertEquals (Node.DOCUMENT_POSITION_CONTAINS | Node.DOCUMENT_POSITION_PRECEDING, aXmlns.compareDocumentPosition (aR));
    assertEquals (Node.DOCUMENT_POSITION_FOLLOWING, aA.compareDocumentPosition (aE));
    assertEquals (Node.DOCUMENT_POSITION_FOLLOWING, aXmlns.compareDocumentPosition (aE));
    assertEquals (Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC | Node.DOCUMENT_POSITION_FOLLOWING,
                  aA.compareDocumentPosition (aB));
    assertEquals (Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC | Node.DOCUMENT_POSITION_PRECEDING,
                  aA.compareDocumentPosition (aXmlns));

    final Node aForeign = new XdmDomView (new XdmParser ().parse ("<r/>")).getDocument ();
    final int nPos = aR.compareDocumentPosition (aForeign);
    assertTrue ((nPos & Node.DOCUMENT_POSITION_DISCONNECTED) != 0);
    assertEquals (nPos, aR.compareDocumentPosition (aForeign));
    final int nPosOther = aR.compareDocumentPosition (XdmToDom.createEmptyDocument ());
    assertTrue ((nPosOther & Node.DOCUMENT_POSITION_DISCONNECTED) != 0);
  }

  @Test
  public void testBaseUriAndParentless () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r xml:base='http://example.org/base/'><e xml:base='sub/'><f/></e></r>");
    aDoc.setDocumentUri ("http://example.org/doc.xml");
    final Document aDom = new XdmDomView (aDoc).getDocument ();
    assertEquals ("http://example.org/doc.xml", aDom.getDocumentURI ());
    assertEquals ("http://example.org/doc.xml", aDom.getBaseURI ());
    final Node aF = aDom.getElementsByTagName ("f").item (0);
    assertEquals ("http://example.org/base/sub/", aF.getBaseURI ());

    final IXdmNode aElement = IXdmNode.createElement (new XdmQName ("p"));
    aElement.appendChild (IXdmNode.createText ("t"));
    final XdmDomView aView = new XdmDomView (aElement.getChildAtIndex (0));
    assertNull (aView.getDocument ());
    final Node aP = aView.getNode (aElement);
    assertNull (aP.getOwnerDocument ());
    assertNull (aP.getParentNode ());
    assertEquals ("t", aP.getTextContent ());
    assertNull (aP.getBaseURI ());
  }

  @Test
  public void testReadOnly () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r a='1'>t<!--c--><?pi d?></r>");
    final Document aDom = new XdmDomView (aDoc).getDocument ();
    final Element aR = aDom.getDocumentElement ();
    final Text aT = (Text) aR.getFirstChild ();
    final Attr aA = aR.getAttributeNode ("a");
    final short nRO = DOMException.NO_MODIFICATION_ALLOWED_ERR;
    _assertDomException (nRO, () -> aDom.appendChild (aR));
    _assertDomException (nRO, () -> aR.removeChild (aT));
    _assertDomException (nRO, () -> aR.insertBefore (aT, aT));
    _assertDomException (nRO, () -> aR.replaceChild (aT, aT));
    _assertDomException (nRO, () -> aR.setAttribute ("b", "2"));
    _assertDomException (nRO, () -> aR.setAttributeNS (null, "b", "2"));
    _assertDomException (nRO, () -> aR.removeAttribute ("a"));
    _assertDomException (nRO, () -> aR.getAttributes ().removeNamedItem ("a"));
    _assertDomException (nRO, () -> aR.setTextContent ("x"));
    _assertDomException (nRO, () -> aR.setPrefix ("p"));
    _assertDomException (nRO, () -> aA.setValue ("2"));
    _assertDomException (nRO, () -> aA.setNodeValue ("2"));
    _assertDomException (nRO, () -> aT.setData ("x"));
    _assertDomException (nRO, () -> aT.appendData ("x"));
    _assertDomException (nRO, () -> aT.splitText (0));
    _assertDomException (nRO, () -> ((ProcessingInstruction) aR.getLastChild ()).setData ("x"));
    _assertDomException (nRO, () -> aDom.renameNode (aR, null, "x"));
    final short nNS = DOMException.NOT_SUPPORTED_ERR;
    _assertDomException (nNS, () -> aDom.createElement ("x"));
    _assertDomException (nNS, () -> aR.cloneNode (true));
    _assertDomException (nNS, () -> aDom.importNode (aR, true));
    _assertDomException (nNS, () -> aR.setUserData ("k", "v", null));
    // Values defined as null cannot be set, without error
    aR.setNodeValue ("x");
    aDom.setTextContent ("x");
    aR.normalize ();
    assertEquals ("t", aR.getTextContent ());
    assertEquals ("1", aDoc.getChildAtIndex (0).getAttributeAtIndex (0).getValue ());
  }

  @Test
  public void testDeepTreesAreIterative () throws Exception
  {
    final int nDepth = 50_000;
    final IXdmNode aDoc = _createDeepDocument (nDepth);
    final Document aDom = new XdmDomView (aDoc).getDocument ();
    final NodeList aAll = aDom.getElementsByTagName ("d");
    assertEquals (nDepth, aAll.getLength ());
    final Node aDeepest = aAll.item (nDepth - 1);
    assertEquals ("bottom", aDeepest.getTextContent ());
    assertEquals (Node.DOCUMENT_POSITION_CONTAINS | Node.DOCUMENT_POSITION_PRECEDING,
                  aDeepest.compareDocumentPosition (aDom.getDocumentElement ()));
    assertTrue (aDom.isEqualNode (new XdmDomView (_createDeepDocument (nDepth)).getDocument ()));
  }
}
