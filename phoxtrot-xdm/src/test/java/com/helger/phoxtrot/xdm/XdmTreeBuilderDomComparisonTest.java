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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Test class for {@link XdmTreeBuilder}: the trees it builds are compared
 * node by node with the DOM the JDK builds from the same documents (names
 * with prefixes, attributes, namespace declarations, text, comments and
 * processing instructions), on documents whose text arrives in one or in
 * several character events (entity and character references, CDATA sections,
 * text longer than the parser's buffer, whitespace of many lengths).
 *
 * @author Philip Helger
 */
public final class XdmTreeBuilderDomComparisonTest
{
  /**
   * @return One line per node of the XDM tree in document order: kind, name,
   *         sorted attributes, sorted namespace declarations or value.
   */
  @NonNull
  private static ICommonsList <String> _describeXdm (@NonNull final IXdmNode aDoc)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (int i = aDoc.getChildCount () - 1; i >= 0; --i)
      aStack.push (aDoc.getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      switch (aCur.getKind ())
      {
        case ELEMENT:
        {
          final Map <String, String> aAttrs = new TreeMap <> ();
          for (int i = 0; i < aCur.getAttributeCount (); ++i)
          {
            final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
            aAttrs.put ("{" + aAttr.getName ().namespaceUri () + "}" + aAttr.getName ().getLexicalName (),
                        aAttr.getValue ());
          }
          ret.add ("E {" +
                   aCur.getName ().namespaceUri () +
                   "}" +
                   aCur.getName ().getLexicalName () +
                   " " +
                   aAttrs +
                   " " +
                   new TreeMap <> (aCur.getDeclaredNamespaces ()) +
                   " " +
                   aCur.getChildCount ());
          for (int i = aCur.getChildCount () - 1; i >= 0; --i)
            aStack.push (aCur.getChildAtIndex (i));
          break;
        }
        case TEXT:
          ret.add ("T " + aCur.getValue ());
          break;
        case COMMENT:
          ret.add ("C " + aCur.getValue ());
          break;
        case PROCESSING_INSTRUCTION:
          ret.add ("P " + aCur.getName ().localName () + " " + aCur.getValue ());
          break;
        default:
          throw new IllegalStateException (aCur.getKind ().toString ());
      }
    }
    return ret;
  }

  /**
   * @return The lines of {@link #_describeXdm(IXdmNode)} for a DOM tree built
   *         by the JDK with CDATA sections merged into the text.
   */
  @NonNull
  private static ICommonsList <String> _describeDom (@NonNull final String sXml) throws Exception
  {
    final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    aFactory.setCoalescing (true);
    final Node aDoc = aFactory.newDocumentBuilder ().parse (new InputSource (new StringReader (sXml)));
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <Node> aStack = new ArrayDeque <> ();
    for (Node aChild = aDoc.getLastChild (); aChild != null; aChild = aChild.getPreviousSibling ())
      if (aChild.getNodeType () != Node.DOCUMENT_TYPE_NODE)
        aStack.push (aChild);
    while (!aStack.isEmpty ())
    {
      final Node aCur = aStack.pop ();
      switch (aCur.getNodeType ())
      {
        case Node.ELEMENT_NODE:
        {
          final Map <String, String> aAttrs = new TreeMap <> ();
          final Map <String, String> aNamespaces = new TreeMap <> ();
          final NamedNodeMap aMap = aCur.getAttributes ();
          for (int i = 0; i < aMap.getLength (); ++i)
          {
            final Attr aAttr = (Attr) aMap.item (i);
            if (XdmConstants.NS_XMLNS.equals (aAttr.getNamespaceURI ()))
              aNamespaces.put (aAttr.getPrefix () == null ? "" : aAttr.getLocalName (), aAttr.getValue ());
            else
              aAttrs.put ("{" + (aAttr.getNamespaceURI () == null ? "" : aAttr.getNamespaceURI ()) + "}" + aAttr.getName (),
                          aAttr.getValue ());
          }
          int nChildren = 0;
          for (Node aChild = aCur.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
            nChildren++;
          ret.add ("E {" +
                   (aCur.getNamespaceURI () == null ? "" : aCur.getNamespaceURI ()) +
                   "}" +
                   aCur.getNodeName () +
                   " " +
                   aAttrs +
                   " " +
                   aNamespaces +
                   " " +
                   nChildren);
          for (Node aChild = aCur.getLastChild (); aChild != null; aChild = aChild.getPreviousSibling ())
            aStack.push (aChild);
          break;
        }
        case Node.TEXT_NODE:
        case Node.CDATA_SECTION_NODE:
          ret.add ("T " + aCur.getNodeValue ());
          break;
        case Node.COMMENT_NODE:
          ret.add ("C " + aCur.getNodeValue ());
          break;
        case Node.PROCESSING_INSTRUCTION_NODE:
          ret.add ("P " + aCur.getNodeName () + " " + aCur.getNodeValue ());
          break;
        default:
          throw new IllegalStateException ("Node type " + aCur.getNodeType ());
      }
    }
    return ret;
  }

  private static void _assertSameAsDom (@NonNull final String sXml) throws Exception
  {
    assertEquals (_describeDom (sXml), _describeXdm (new XdmParser ().parse (sXml)));
  }

  @Test
  public void testVariedDocuments () throws Exception
  {
    _assertSameAsDom ("<r/>");
    _assertSameAsDom ("<?pi before?><!-- c0 --><r xmlns='urn:d' xmlns:p='urn:p' p:a='1' a='2'>" +
                      "text<p:x b='3'><y xmlns=''>in<!-- c1 --><?pi in?></y>tail</p:x>" +
                      "<![CDATA[<cdata>]]>more<z/>\n  </r><!-- c2 --><?pi after?>");
    // Text from several events: references, CDATA next to text
    _assertSameAsDom ("<r>a&amp;b&lt;c&#65;&#x42;d<x>&#10;&#13;&#9; </x><![CDATA[e]]>f<![CDATA[]]><y/>\t\n</r>");
    _assertSameAsDom ("<!DOCTYPE r [<!ENTITY ent 'in &#32; ent'><!ENTITY m '<x>m</x>'>]><r>a&ent;b&m;c&m;&ent;</r>");
    // Attributes: many, namespaced, with whitespace and references
    final StringBuilder aSB = new StringBuilder ("<r xmlns:q='urn:q'");
    for (int i = 0; i < 40; ++i)
      aSB.append (' ').append (i % 2 == 0 ? "a" : "q:a").append (i).append ("='v ").append (i).append (" &amp;'");
    aSB.append ("/>");
    _assertSameAsDom (aSB.toString ());
    // Indentation of every length up to beyond the shared whitespace, with
    // tabs and line ends, and the same lengths again
    aSB.setLength (0);
    aSB.append ("<r>");
    for (int nPass = 0; nPass < 2; ++nPass)
      for (int i = 0; i < 80; ++i)
      {
        aSB.append ('\n');
        for (int j = 0; j < i; ++j)
          aSB.append (j % 7 == 0 ? '\t' : ' ');
        aSB.append ("<e").append (i % 5).append ("/>");
        if (i % 9 == 0)
          aSB.append ("  x  ");
      }
    aSB.append ("\n</r>");
    _assertSameAsDom (aSB.toString ());
    // Text longer than the parser's buffer, and long whitespace
    aSB.setLength (0);
    aSB.append ("<r><t>");
    for (int i = 0; i < 20_000; ++i)
      aSB.append ((char) ('a' + i % 26));
    aSB.append ("</t><w>");
    for (int i = 0; i < 20_000; ++i)
      aSB.append (i % 10 == 0 ? '\n' : ' ');
    aSB.append ("</w>").append ("<u>ä€😀</u></r>");
    _assertSameAsDom (aSB.toString ());
    // Many distinct names, beyond the first sizes of the name table
    aSB.setLength (0);
    aSB.append ("<r xmlns:p='urn:p' xmlns:q='urn:q'>");
    for (int i = 0; i < 300; ++i)
      aSB.append ('<').append (i % 3 == 0 ? "p:" : i % 3 == 1 ? "q:" : "").append ("n").append (i % 150).append ("/>");
    aSB.append ("</r>");
    _assertSameAsDom (aSB.toString ());
  }

  @Test
  public void testSharedWhitespace () throws Exception
  {
    final IXdmNode aR = new XdmParser ().parse ("<r>\n  <a/>\n  <b/>\n    <c/>\n  <d>  x</d>\n  </r>")
                                         .getChildAtIndex (0);
    // The same indentation is one string, also in the last text node
    assertSame (aR.getChildAtIndex (0).getValue (), aR.getChildAtIndex (2).getValue ());
    assertSame (aR.getChildAtIndex (0).getValue (), aR.getChildAtIndex (6).getValue ());
    assertEquals ("\n    ", aR.getChildAtIndex (4).getValue ());
    assertNotSame (aR.getChildAtIndex (0).getValue (), aR.getChildAtIndex (4).getValue ());
    assertEquals ("  x", aR.getChildAtIndex (7).getChildAtIndex (0).getValue ());
    assertEquals ("\n  ", aR.getChildAtIndex (8).getValue ());
  }
}
