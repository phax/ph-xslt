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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Test class for class {@link XdmStreamReader}. Ported from go-xslt's
 * <code>internal/xmltree/stream_test.go</code>: a stream that drops nothing
 * must build the tree {@link XdmParser} builds, numbered the same way.
 *
 * @author Philip Helger
 */
public final class XdmStreamReaderTest
{
  @NonNull
  private static InputSource _source (@NonNull final String sXml)
  {
    final InputSource ret = new InputSource (new NonBlockingStringReader (sXml));
    ret.setSystemId ("file:/tmp/doc.xml");
    return ret;
  }

  /** Describe a tree with everything a streamed and a parsed tree must share. */
  @NonNull
  private static String _describe (@NonNull final IXdmNode aRoot)
  {
    final StringBuilder aSB = new StringBuilder ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      aSB.append (aCur.getKind ()).append (' ').append (aCur.getOrder ()).append (' ');
      if (aCur.getName () != null)
        aSB.append (aCur.getName ().getEQName ()).append (" pfx=").append (aCur.getName ().prefix ()).append (' ');
      if (aCur.getValue () != null)
        aSB.append ('[').append (aCur.getValue ()).append ("] ");
      if (aCur.isDocument () || aCur.isElement ())
        aSB.append ("end=").append (aCur.getEndOrder ()).append (' ');
      if (aCur.isElement ())
      {
        aSB.append ("ns=").append (aCur.getDeclaredNamespaces ()).append (' ');
        for (int i = 0; i < aCur.getAttributeCount (); ++i)
        {
          final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
          aSB.append ("@")
             .append (aAttr.getName ().getEQName ())
             .append ('=')
             .append (aAttr.getValue ())
             .append ('/')
             .append (aAttr.getIDKind ())
             .append ('/')
             .append (aAttr.getOrder ())
             .append (' ');
        }
      }
      aSB.append ('\n');
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return aSB.toString ();
  }

  @NonNull
  private static IXdmNode _streamAll (@NonNull final String sXml, final boolean bExternal) throws XdmParseException
  {
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source (sXml),
                                                               bExternal,
                                                               XdmParser.DEFAULT_MAX_ELEMENT_DEPTH,
                                                               null))
    {
      while (aReader.next () != XdmStreamReader.EEvent.END)
      {
        // Keep everything
      }
      return aReader.getDocument ();
    }
  }

  private static void _assertSameAsParser (@NonNull final String sXml) throws XdmParseException
  {
    final IXdmNode aParsed = new XdmParser ().parse (_source (sXml));
    aParsed.assignDocumentOrder ();
    final IXdmNode aStreamed = _streamAll (sXml, false);
    assertEquals (_describe (aParsed), _describe (aStreamed));
    assertEquals (aParsed.getDocumentUri (), aStreamed.getDocumentUri ());
  }

  @NonNull
  private static String _events (@NonNull final String sXml) throws XdmParseException
  {
    final StringBuilder aSB = new StringBuilder ();
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source (sXml)))
    {
      XdmStreamReader.EEvent e;
      do
      {
        e = aReader.next ();
        final IXdmNode aNode = aReader.getNode ();
        aSB.append (e).append (aReader.getDepth ());
        if (aNode.isElement ())
          aSB.append (':').append (aNode.getName ().localName ());
        else
          if (!aNode.isDocument ())
            aSB.append (':').append (aNode.getStringValue ());
        aSB.append (' ');
      } while (e != XdmStreamReader.EEvent.END);
    }
    return aSB.toString ().trim ();
  }

  @Test
  public void testSameTreeAsParser () throws XdmParseException
  {
    _assertSameAsParser ("<a/>");
    _assertSameAsParser ("<!--c1--><?pi data?>\n<a xmlns='urn:d' xmlns:p='urn:p' x='1' p:y='2'>t1<b/>t2<!--c2--></a>\n<!--c3-->");
    _assertSameAsParser ("<a>x<![CDATA[<y>]]>z&amp;&#x41;<b xml:space='preserve'>  </b>  <c/></a>");
    _assertSameAsParser ("<r><x xmlns=''><y xmlns='urn:y'><z/></y></x><p:q xmlns:p='urn:p'/></r>");
    _assertSameAsParser ("<a t='a&#10;b\tc' id='  x  ' xml:id=' i1 '/>");
  }

  @Test
  public void testInternalSubset () throws XdmParseException
  {
    final String sXml = "<?xml version='1.0'?>\n" +
                        "<!DOCTYPE doc [<!ELEMENT doc (e*)><!ELEMENT e (#PCDATA)>" +
                        "<!ATTLIST e id ID #IMPLIED d CDATA 'dflt' r IDREFS #IMPLIED>" +
                        "<!ENTITY ent 'EV'><!ENTITY u SYSTEM 'u.gif' NDATA gif><!NOTATION gif SYSTEM 'g'>]>\n" +
                        "<doc>\n  <e id=' a1 ' r=' x  y '>t&ent;</e>\n  <!--c--><?pi data?>\n</doc>";
    _assertSameAsParser (sXml);
    final IXdmNode aDoc = _streamAll (sXml, false);
    final IXdmNode aE = aDoc.getChildAtIndex (0).getChildAtIndex (0);
    assertEquals (EIDKind.ID, aE.getAttribute ("", "id").getIDKind ());
    assertEquals ("a1", aE.getAttribute ("", "id").getValue ());
    assertEquals ("dflt", aE.getAttribute ("", "d").getValue ());
    assertEquals ("tEV", aE.getStringValue ());
    assertEquals ("file:/tmp/u.gif", aDoc.getUnparsedEntity ("u")[0]);
    assertTrue (aDoc.getAllParsedEntityNames ().contains ("ent"));
  }

  @Test
  public void testEvents () throws XdmParseException
  {
    assertEquals ("LEAF1:c ENTER1:a LEAF2:t ENTER2:b LEAF3:u EXIT2:b LEAF2:v EXIT1:a END0",
                  _events ("<!--c--><a>t<b>u</b>v</a>"));
    // Adjacent character data is one text node
    assertEquals ("ENTER1:a LEAF2:xyz EXIT1:a END0", _events ("<a>x<![CDATA[y]]>&#x7A;</a>"));
  }

  @Test
  public void testDropAndSkip () throws XdmParseException
  {
    final String sXml = "<doc><skip><deep><x/></deep></skip><rec n='1'>a</rec><rec n='2'>b</rec><tail/></doc>";
    final ICommonsList <String> aSeen = new CommonsArrayList <> ();
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source (sXml)))
    {
      IXdmNode aDocElement = null;
      XdmStreamReader.EEvent e;
      while ((e = aReader.next ()) != XdmStreamReader.EEvent.END)
      {
        final IXdmNode aNode = aReader.getNode ();
        if (e == XdmStreamReader.EEvent.ENTER && aReader.getDepth () == 1)
          aDocElement = aNode;
        if (e == XdmStreamReader.EEvent.ENTER && "skip".equals (aNode.getName ().localName ()))
          aReader.skipSubtree ();
        else
          if (e == XdmStreamReader.EEvent.EXIT && aReader.getDepth () == 2)
          {
            aSeen.add (aNode.getName ().localName () + "=" + aNode.getStringValue ());
            // Only the record and the spine are attached
            assertEquals (1, aDocElement.getChildCount ());
            assertSame (aDocElement, aNode.getParent ());
            aReader.drop (aNode);
            assertNull (aNode.getParent ());
            assertTrue (aReader.isIncomplete (aDocElement));
          }
      }
      assertNotNull (aDocElement);
      assertEquals (0, aDocElement.getChildCount ());
      assertEquals ("[rec=a, rec=b, tail=]", aSeen.toString ());
    }
  }

  @Test
  public void testDocumentOrderWhileStreaming () throws XdmParseException
  {
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source ("<a x='1'><b/><c/></a>")))
    {
      assertEquals (XdmStreamReader.EEvent.ENTER, aReader.next ());
      final IXdmNode aA = aReader.getNode ();
      assertEquals (XdmStreamReader.EEvent.ENTER, aReader.next ());
      final IXdmNode aB = aReader.getNode ();
      // The open element covers what is read later
      assertTrue (aA.getEndOrder () > aB.getOrder ());
      assertTrue (IXdmNode.compareDocumentOrder (aA, aB) < 0);
      assertTrue (IXdmNode.compareDocumentOrder (aA.getAttributeAtIndex (0), aB) < 0);
      assertEquals (XdmStreamReader.EEvent.EXIT, aReader.next ());
      aReader.drop (aB);
      assertEquals (XdmStreamReader.EEvent.ENTER, aReader.next ());
      final IXdmNode aC = aReader.getNode ();
      // Dropping does not renumber
      assertTrue (IXdmNode.compareDocumentOrder (aB, aC) < 0);
      assertEquals (4, aC.getOrder ());
      // The name index is not used for a streamed document
      assertTrue (aA.getRoot () instanceof final XdmDocumentNode aDoc && aDoc.isStreamed ());
      assertNull (((XdmDocumentNode) aA.getRoot ()).getElementsByName (aC.getName ()));
    }
  }

  @Test
  public void testWhitespaceStripping () throws XdmParseException
  {
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source ("<a> <b> </b><c xml:space='preserve'> <b> </b></c> x </a>"),
                                                               false,
                                                               0,
                                                               aName -> !"c".equals (aName.localName ())))
    {
      while (aReader.next () != XdmStreamReader.EEvent.END)
      {
        // Keep everything
      }
      final IXdmNode aA = aReader.getDocument ().getChildAtIndex (0);
      // " " in a and b stripped; inside c (preserve) kept; " x " is no whitespace
      assertEquals (3, aA.getChildCount ());
      assertEquals (0, aA.getChildAtIndex (0).getChildCount ());
      assertEquals (2, aA.getChildAtIndex (1).getChildCount ());
      assertEquals (1, aA.getChildAtIndex (1).getChildAtIndex (1).getChildCount ());
      assertEquals (" x ", aA.getChildAtIndex (2).getValue ());
    }
  }

  @Test
  public void testDeepDocument () throws XdmParseException
  {
    final int nDepth = 100_000;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("<e>");
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("</e>");
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source (aSB.toString ()), false, 0, null))
    {
      int nMax = 0;
      while (aReader.next () != XdmStreamReader.EEvent.END)
        nMax = Math.max (nMax, aReader.getOpenDepth ());
      assertEquals (nDepth, nMax);
    }
    // The depth limit applies
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source (aSB.toString ()), false, 50, null))
    {
      while (aReader.next () != XdmStreamReader.EEvent.END)
      {
        // Read on
      }
      fail ();
    }
    catch (final XdmParseException ex)
    {
      assertFalse (ex instanceof XdmStreamUnsupportedException);
    }
  }

  private static void _assertUnsupported (@NonNull final String sXml, final boolean bExternal)
  {
    try
    {
      XdmStreamReader.open (_source (sXml), bExternal, 0, null).close ();
      fail ("Expected to be refused: " + sXml);
    }
    catch (final XdmStreamUnsupportedException ex)
    {
      // expected
    }
    catch (final XdmParseException ex)
    {
      fail ("Wrong exception " + ex);
    }
  }

  @Test
  public void testRefused ()
  {
    _assertUnsupported ("<?xml version='1.1'?><a/>", false);
    _assertUnsupported ("<!DOCTYPE a [<!ENTITY e SYSTEM 'e.xml'>]><a>&e;</a>", false);
    _assertUnsupported ("<!DOCTYPE a SYSTEM 'a.dtd'><a/>", true);
  }

  @Nullable
  private static XdmParseException _parseError (@NonNull final String sXml)
  {
    try (final XdmStreamReader aReader = XdmStreamReader.open (_source (sXml)))
    {
      while (aReader.next () != XdmStreamReader.EEvent.END)
      {
        // Read on
      }
      return null;
    }
    catch (final XdmParseException ex)
    {
      return ex;
    }
  }

  @Test
  public void testMalformed ()
  {
    assertNotNull (_parseError ("<a><b></a>"));
    assertNotNull (_parseError ("<a>"));
    assertNotNull (_parseError ("<a/><b/>"));
    // Billion laughs is refused by the entity limits
    final StringBuilder aSB = new StringBuilder ("<!DOCTYPE a [<!ENTITY e0 'xxxxxxxxxx'>");
    for (int i = 1; i < 10; ++i)
      aSB.append ("<!ENTITY e" + i + " '" + ("&e" + (i - 1) + ";").repeat (10) + "'>");
    aSB.append ("]><a>&e9;</a>");
    assertNotNull (_parseError (aSB.toString ()));
    assertNull (_parseError ("<a/>"));
  }

  @Test
  public void testByteStream () throws XdmParseException
  {
    final byte [] aBytes = "<?xml version='1.0' encoding='ISO-8859-1'?><a>ä</a>".getBytes (StandardCharsets.ISO_8859_1);
    final InputSource aIS = new InputSource (new ByteArrayInputStream (aBytes));
    try (final XdmStreamReader aReader = XdmStreamReader.open (aIS))
    {
      while (aReader.next () != XdmStreamReader.EEvent.END)
      {
        // Keep everything
      }
      assertEquals ("ä", aReader.getDocument ().getStringValue ());
    }
  }
}
