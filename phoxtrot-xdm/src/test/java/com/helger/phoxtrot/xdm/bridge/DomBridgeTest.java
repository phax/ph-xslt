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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xerces.jaxp.DocumentBuilderFactoryImpl;
import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmDocumentNode;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmTreeBuilder;

/**
 * Test class for {@link DomToXdm} and {@link XdmToDom}: documents parsed
 * into XDM directly and through a JDK DOM must give the same tree, and an XDM
 * tree written to a DOM and read back must be unchanged.
 *
 * @author Philip Helger
 */
public final class DomBridgeTest
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

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * @return One line per node of the XDM tree in document order: kind, name,
   *         sorted attributes with ID kind, sorted in-scope namespaces, value,
   *         explicit base URI.
   */
  @NonNull
  static ICommonsList <String> describe (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    ret.add ("ROOT " + aRoot.getKind () + " uri=" + aRoot.getDocumentUri ());
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (int i = aRoot.getChildCount () - 1; i >= 0; --i)
      aStack.push (aRoot.getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      final String sBase = aCur.getBaseUri () == null ? "" : " base=" + aCur.getBaseUri ();
      switch (aCur.getKind ())
      {
        case ELEMENT:
        {
          final Map <String, String> aAttrs = new TreeMap <> ();
          for (int i = 0; i < aCur.getAttributeCount (); ++i)
          {
            final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
            aAttrs.put ("{" + aAttr.getName ().namespaceUri () + "}" + aAttr.getName ().getLexicalName (),
                        aAttr.getValue () + "/" + aAttr.getIDKind ());
          }
          ret.add ("E {" +
                   aCur.getName ().namespaceUri () +
                   "}" +
                   aCur.getName ().getLexicalName () +
                   " " +
                   aAttrs +
                   " ns=" +
                   new TreeMap <> (aCur.getInScopeNamespaces ()) +
                   sBase);
          for (int i = aCur.getChildCount () - 1; i >= 0; --i)
            aStack.push (aCur.getChildAtIndex (i));
          break;
        }
        case PROCESSING_INSTRUCTION:
          ret.add ("P " + aCur.getName ().localName () + " " + aCur.getValue () + sBase);
          break;
        default:
          ret.add (aCur.getKind ().name ().charAt (0) + " " + aCur.getValue () + sBase);
          break;
      }
    }
    return ret;
  }

  /**
   * Check that the document is numbered in document order: every node after
   * its predecessor, attributes after their element.
   */
  private static void _assertNumbered (@NonNull final IXdmNode aDoc)
  {
    assertTrue (aDoc instanceof XdmDocumentNode);
    assertTrue ("numbering kept while building", ((XdmDocumentNode) aDoc).getMaxElementDepth () >= 0);
    int nLast = aDoc.getOrder ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (int i = aDoc.getChildCount () - 1; i >= 0; --i)
      aStack.push (aDoc.getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      assertTrue (aCur.getOrder () > nLast);
      nLast = aCur.getOrder ();
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
      {
        assertTrue (aCur.getAttributeAtIndex (i).getOrder () > nLast);
        nLast = aCur.getAttributeAtIndex (i).getOrder ();
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    assertEquals (nLast, aDoc.getEndOrder ());
  }

  @NonNull
  private static DocumentBuilder _createBuilder (final boolean bNamespaceAware, final boolean bExpandEntities) throws Exception
  {
    // The JDK DOM leaves entity reference nodes empty, Xerces-J fills them
    final DocumentBuilderFactory aFactory = bExpandEntities ? DocumentBuilderFactory.newDefaultInstance ()
                                                            : new DocumentBuilderFactoryImpl ();
    aFactory.setNamespaceAware (bNamespaceAware);
    aFactory.setExpandEntityReferences (bExpandEntities);
    return aFactory.newDocumentBuilder ();
  }

  @NonNull
  private static Document _parseDom (@NonNull final String sXml, final boolean bNamespaceAware, final boolean bExpandEntities) throws Exception
  {
    return _createBuilder (bNamespaceAware, bExpandEntities).parse (new InputSource (new StringReader (sXml)));
  }

  @Test
  public void testDomToXdmMatchesParser () throws Exception
  {
    for (final String sXml : DOCUMENTS)
    {
      final ICommonsList <String> aExpected = describe (new XdmParser ().parse (sXml));
      for (final boolean bNamespaceAware : new boolean [] { true, false })
        for (final boolean bExpand : new boolean [] { true, false })
        {
          final IXdmNode aDoc = DomToXdm.convertDocument (_parseDom (sXml, bNamespaceAware, bExpand), null);
          assertEquals (sXml + " nsAware=" + bNamespaceAware + " expand=" + bExpand, aExpected, describe (aDoc));
          _assertNumbered (aDoc);
        }
    }
  }

  @Test
  public void testXdmToDomRoundTrip () throws Exception
  {
    for (final String sXml : DOCUMENTS)
    {
      final IXdmNode aOriginal = new XdmParser ().parse (sXml);
      final Document aDom = XdmToDom.toDocument (aOriginal);
      final IXdmNode aBack = DomToXdm.convertDocument (aDom, null);
      assertEquals (sXml, describe (aOriginal), describe (aBack));
      _assertNumbered (aBack);
    }
  }

  @Test
  public void testXdmToSaxRoundTrip () throws Exception
  {
    for (final String sXml : DOCUMENTS)
    {
      final IXdmNode aOriginal = new XdmParser ().parse (sXml);
      final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
      XdmToSax.emit (aOriginal, aBuilder, aBuilder);
      assertEquals (sXml, describe (aOriginal), describe (aBuilder.getDocument ()));
    }
    // A parentless element with an attribute in a namespace without prefix
    final IXdmNode aE = IXdmNode.createElement (new XdmQName ("urn:x", "e", ""));
    aE.addAttribute (IXdmNode.createAttribute (new XdmQName ("urn:a", "a", ""), "1"));
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
    XdmToSax.emit (aE, aBuilder, null);
    final IXdmNode aBack = aBuilder.getDocument ().getChildAtIndex (0);
    assertEquals ("urn:x", aBack.lookupNamespaceUri (""));
    assertEquals ("ns0", aBack.getAttribute ("urn:a", "a").getName ().prefix ());
  }

  @Test
  public void testDocumentUri () throws Exception
  {
    final Document aDom = _parseDom ("<r/>", true, true);
    aDom.setDocumentURI ("http://example.org/dom.xml");
    assertEquals ("http://example.org/dom.xml", DomToXdm.convertDocument (aDom, null).getDocumentUri ());
    assertEquals ("urn:explicit", DomToXdm.convertDocument (aDom, "urn:explicit").getDocumentUri ());
  }

  @Test
  public void testDocumentUriToDom () throws Exception
  {
    final IXdmNode aXdm = new XdmParser ().parse ("<r/>");
    aXdm.setDocumentUri ("http://example.org/x.xml");
    assertEquals ("http://example.org/x.xml", XdmToDom.toDocument (aXdm).getDocumentURI ());
  }

  @Test
  public void testExternalEntityBase () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.createDirectories (aDir.resolve ("sub"));
    final Path aMain = aDir.resolve ("main.xml");
    Files.writeString (aMain, "<!DOCTYPE r [ <!ENTITY e SYSTEM 'sub/ent.xml'> ]><r>&e;<x/></r>", StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("sub/ent.xml"), "<a><b/></a><?p x?>", StandardCharsets.UTF_8);
    final String sMainUri = aMain.toUri ().toString ();

    final IXdmNode aExpected = new XdmParser ().setLoadExternalEntities (true).parse (aMain);
    for (final boolean bExpand : new boolean [] { true, false })
    {
      final Document aDom = _createBuilder (true, bExpand).parse (sMainUri);
      final IXdmNode aDoc = DomToXdm.convertDocument (aDom, null);
      final IXdmNode aRoot = aDoc.getChildAtIndex (0);
      final IXdmNode aA = aRoot.getChildAtIndex (0);
      assertEquals ("a", aA.getName ().localName ());
      final String sEntityUri = aDir.resolve ("sub/ent.xml").toUri ().toString ();
      if (bExpand)
      {
        // The JDK DOM marks the expanded content with an xml:base attribute
        assertEquals (sEntityUri, aA.getAttribute (XdmConstants.NS_XML, "base").getValue ());
      }
      else
      {
        // The entity reference is kept: its content gets the entity's base
        assertEquals (sEntityUri, aA.getBaseUri ());
        assertEquals (describe (aExpected), describe (aDoc));
      }
      assertEquals (sMainUri, aDoc.getDocumentUri ());
    }
  }

  @Test
  public void testProgrammaticDomGetsNamespaceFixup () throws Exception
  {
    final Document aDom = _createBuilder (true, true).newDocument ();
    final Element aRoot = aDom.createElementNS ("urn:r", "p:root");
    aDom.appendChild (aRoot);
    // An attribute in a namespace without prefix, and one with a prefix that
    // nothing declares
    aRoot.setAttributeNS ("urn:a", "plain", "1");
    aRoot.setAttributeNS ("urn:b", "b:pre", "2");
    final Element aChild = aDom.createElementNS (null, "child");
    aRoot.appendChild (aChild);
    final Element aDefault = aDom.createElementNS ("urn:d", "d");
    aChild.appendChild (aDefault);
    aDefault.appendChild (aDom.createElementNS (null, "nons"));

    final IXdmNode aDoc = DomToXdm.convertDocument (aDom, null);
    final IXdmNode aXRoot = aDoc.getChildAtIndex (0);
    assertEquals (new XdmQName ("urn:r", "root", "p"), aXRoot.getName ());
    assertEquals ("p", aXRoot.getName ().prefix ());
    assertEquals ("urn:r", aXRoot.lookupNamespaceUri ("p"));
    final IXdmNode aPlain = aXRoot.getAttribute ("urn:a", "plain");
    assertNotNull (aPlain);
    assertEquals ("ns0", aPlain.getName ().prefix ());
    assertEquals ("urn:a", aXRoot.lookupNamespaceUri ("ns0"));
    assertEquals ("b", aXRoot.getAttribute ("urn:b", "pre").getName ().prefix ());
    assertEquals ("urn:b", aXRoot.lookupNamespaceUri ("b"));
    final IXdmNode aXDefault = aXRoot.getChildAtIndex (0).getChildAtIndex (0);
    assertEquals ("urn:d", aXDefault.lookupNamespaceUri (""));
    // The element in no namespace below a default namespace undeclares it
    assertNull (aXDefault.getChildAtIndex (0).lookupNamespaceUri (""));
    assertEquals ("", aXDefault.getChildAtIndex (0).getName ().namespaceUri ());
    _assertNumbered (aDoc);

    // And back: the DOM declares what its names need
    final Document aDom2 = XdmToDom.toDocument (aDoc);
    final Element aRoot2 = aDom2.getDocumentElement ();
    assertEquals ("urn:r", aRoot2.getAttributeNS (XdmConstants.NS_XMLNS, "p"));
    assertEquals ("urn:a", aRoot2.getAttributeNS (XdmConstants.NS_XMLNS, "ns0"));
    assertEquals (describe (aDoc), describe (DomToXdm.convertDocument (aDom2, null)));
  }

  @Test
  public void testUndeclaredDefaultNamespaceToDom () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<r xmlns='urn:d'><e xmlns=''><f/></e></r>");
    final Document aDom = XdmToDom.toDocument (aDoc);
    final Element aE = (Element) aDom.getDocumentElement ().getFirstChild ();
    assertNull (aE.getNamespaceURI ());
    assertTrue (aE.hasAttributeNS (XdmConstants.NS_XMLNS, "xmlns"));
    assertEquals ("", aE.getAttributeNS (XdmConstants.NS_XMLNS, "xmlns"));
    assertNull (aE.getFirstChild ().getNamespaceURI ());
  }

  @Test
  public void testConvertCounterpart () throws Exception
  {
    final Document aDom = _parseDom ("<!--c--><r><e a='1'/><?pi d?></r>", true, true);
    final Element aE = (Element) aDom.getDocumentElement ().getFirstChild ();
    final IXdmNode aXE = DomToXdm.convert (aE, "urn:doc");
    assertEquals ("e", aXE.getName ().localName ());
    assertEquals (ENodeKind.DOCUMENT, aXE.getRoot ().getKind ());
    assertEquals ("urn:doc", aXE.getRoot ().getDocumentUri ());
    // The whole document was converted
    assertEquals (2, aXE.getRoot ().getChildCount ());

    final IXdmNode aXA = DomToXdm.convert (aE.getAttributeNode ("a"), null);
    assertEquals (ENodeKind.ATTRIBUTE, aXA.getKind ());
    assertSame (aXA.getParent (), aXA.getParent ().getParent ().getChildAtIndex (0));
    assertEquals ("1", aXA.getValue ());

    final IXdmNode aXPI = DomToXdm.convert (aE.getNextSibling (), null);
    assertEquals (ENodeKind.PROCESSING_INSTRUCTION, aXPI.getKind ());
    final IXdmNode aXC = DomToXdm.convert (aDom.getFirstChild (), null);
    assertEquals (ENodeKind.COMMENT, aXC.getKind ());
    assertEquals (ENodeKind.DOCUMENT, DomToXdm.convert (aDom, null).getKind ());

    try
    {
      DomToXdm.convert (aDom.createTextNode ("t"), null);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }

    final Attr aParentless = aDom.createAttributeNS ("urn:x", "x:a");
    aParentless.setValue ("v");
    final IXdmNode aXParentless = DomToXdm.convert (aParentless, null);
    assertNull (aXParentless.getParent ());
    assertEquals (new XdmQName ("urn:x", "a", "x"), aXParentless.getName ());
  }

  @Test
  public void testParentlessDomSubtree () throws Exception
  {
    final Document aDom = _createBuilder (true, true).newDocument ();
    final Element aE = aDom.createElementNS ("urn:x", "x:e");
    aE.appendChild (aDom.createTextNode ("t"));
    final IXdmNode aXE = DomToXdm.convert (aE, null);
    assertEquals ("e", aXE.getName ().localName ());
    assertEquals (ENodeKind.DOCUMENT, aXE.getParent ().getKind ());
    assertEquals ("t", aXE.getStringValue ());
  }

  @Test
  public void testDocumentFragment () throws Exception
  {
    final Document aDom = _createBuilder (true, true).newDocument ();
    final DocumentFragment aFrag = aDom.createDocumentFragment ();
    aFrag.appendChild (aDom.createTextNode ("a"));
    aFrag.appendChild (aDom.createElementNS (null, "e"));
    aFrag.appendChild (aDom.createElementNS (null, "f"));
    final IXdmNode aDoc = DomToXdm.convertDocument (aFrag, null);
    assertEquals (3, aDoc.getChildCount ());

    // XDM document with text and several elements into a fragment
    final DocumentFragment aBack = XdmToDom.toDocumentFragment (aDoc, aDom);
    assertEquals (3, aBack.getChildNodes ().getLength ());
    assertEquals ("a", aBack.getFirstChild ().getNodeValue ());
  }

  @Test
  public void testParentlessXdmToDom () throws Exception
  {
    final IXdmNode aE = IXdmNode.createElement (new XdmQName ("urn:x", "e", "x"));
    aE.addAttribute (IXdmNode.createAttribute (new XdmQName ("urn:y", "a", "y"), "1"));
    aE.appendChild (IXdmNode.createText ("text"));
    aE.appendChild (IXdmNode.createComment ("c"));
    aE.appendChild (IXdmNode.createProcessingInstruction ("t", "d"));
    final Document aDom = XdmToDom.toDocument (aE);
    final Element aRoot = aDom.getDocumentElement ();
    assertEquals ("urn:x", aRoot.getNamespaceURI ());
    assertEquals ("x:e", aRoot.getTagName ());
    assertEquals ("1", aRoot.getAttributeNS ("urn:y", "a"));
    assertEquals ("urn:y", aRoot.lookupNamespaceURI ("y"));
    assertEquals (3, aRoot.getChildNodes ().getLength ());
    assertEquals (Node.PROCESSING_INSTRUCTION_NODE, aRoot.getLastChild ().getNodeType ());
  }

  @Test
  public void testAppendToExistingDom () throws Exception
  {
    final Document aDom = _parseDom ("<host xmlns='urn:d' xmlns:p='urn:p'><first/></host>", true, true);
    final Element aHost = aDom.getDocumentElement ();
    final IXdmNode aDoc = new XdmParser ().parse ("<p:a xmlns:p='urn:p' xmlns:q='urn:q'><b/><q:c/></p:a>");
    XdmToDom.appendTo (aDoc, aHost, aHost.getFirstChild ());
    final Element aA = (Element) aHost.getFirstChild ();
    assertEquals ("p:a", aA.getTagName ());
    // urn:p is bound by the host already, urn:q is not
    assertTrue (!aA.hasAttributeNS (XdmConstants.NS_XMLNS, "p"));
    assertEquals ("urn:q", aA.getAttributeNS (XdmConstants.NS_XMLNS, "q"));
    // b is in no namespace, the host has a default namespace
    final Element aB = (Element) aA.getFirstChild ();
    assertNull (aB.getNamespaceURI ());
    assertEquals ("", aB.getAttributeNS (XdmConstants.NS_XMLNS, "xmlns"));
    assertEquals ("first", aA.getNextSibling ().getLocalName ());
  }

  @Test
  public void testIdAttributeToDom () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse ("<!DOCTYPE r [<!ATTLIST e id ID #IMPLIED>]><r><e id='x1'/><f xml:id='x2'/></r>");
    final Document aDom = XdmToDom.toDocument (aDoc);
    assertEquals ("e", aDom.getElementById ("x1").getTagName ());
    assertEquals ("f", aDom.getElementById ("x2").getTagName ());
  }

  @Test
  public void testDeepTreesAreIterative () throws Exception
  {
    final int nDepth = 50_000;
    final Document aDom = _createBuilder (true, true).newDocument ();
    Node aParent = aDom;
    for (int i = 0; i < nDepth; ++i)
    {
      final Element aE = aDom.createElementNS ("urn:deep", "d");
      aParent.appendChild (aE);
      aParent = aE;
    }
    aParent.appendChild (aDom.createTextNode ("bottom"));
    final IXdmNode aDoc = DomToXdm.convertDocument (aDom, null);
    assertEquals (nDepth, ((XdmDocumentNode) aDoc).getMaxElementDepth ());
    final Document aBack = XdmToDom.toDocument (aDoc);
    Node aCur = aBack.getDocumentElement ();
    int nCount = 0;
    while (aCur.getNodeType () == Node.ELEMENT_NODE)
    {
      ++nCount;
      aCur = aCur.getFirstChild ();
    }
    assertEquals (nDepth, nCount);
    assertEquals ("bottom", aCur.getNodeValue ());
  }
}
