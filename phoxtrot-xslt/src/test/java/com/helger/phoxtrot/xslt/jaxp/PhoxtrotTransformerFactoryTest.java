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
package com.helger.phoxtrot.xslt.jaxp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.EThreading;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xslt.IXsltMessageListener;
import com.helger.phoxtrot.xslt.IXsltTraceListener;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.XsltMessage;
import com.helger.phoxtrot.xslt.XsltTraceTemplate;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;

/**
 * Test class for the JAXP adapter: {@link PhoxtrotTransformerFactory}, {@link PhoxtrotTemplates}
 * and {@link PhoxtrotTransformer}.
 *
 * @author Philip Helger
 */
public final class PhoxtrotTransformerFactoryTest
{
  private static final String XSL_START = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:xs='http://www.w3.org/2001/XMLSchema' exclude-result-prefixes='xs'>";
  private static final String XSL_END = "</xsl:stylesheet>";
  /** Copies the document element with a count of its children. */
  private static final String COUNT_SHEET = XSL_START +
                                            "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
                                            "<xsl:template match='/*'><out name='{local-name()}' n='{count(*)}'/></xsl:template>" +
                                            XSL_END;
  private static final String SOURCE = "<root><a/><b/><c/></root>";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /** Records everything an error listener receives. */
  private static final class RecordingErrorListener implements ErrorListener
  {
    private final ICommonsList <TransformerException> m_aWarnings = new CommonsArrayList <> ();
    private final ICommonsList <TransformerException> m_aFatals = new CommonsArrayList <> ();

    public void warning (@NonNull final TransformerException ex)
    {
      m_aWarnings.add (ex);
    }

    public void error (@NonNull final TransformerException ex)
    {
      fail ("No recoverable errors expected: " + ex);
    }

    public void fatalError (@NonNull final TransformerException ex)
    {
      m_aFatals.add (ex);
    }
  }

  /** Records SAX events as text. */
  private static final class RecordingHandler extends DefaultHandler2
  {
    private final StringBuilder m_aSB = new StringBuilder ();

    @Override
    public void startDocument ()
    {
      m_aSB.append ("[doc]");
    }

    @Override
    public void endDocument ()
    {
      m_aSB.append ("[/doc]");
    }

    @Override
    public void startPrefixMapping (final String sPrefix, final String sUri)
    {
      m_aSB.append ("[ns ").append (sPrefix).append ('=').append (sUri).append (']');
    }

    @Override
    public void startElement (final String sUri, final String sLocal, final String sQName, final Attributes aAttrs)
    {
      m_aSB.append ('<').append ('{').append (sUri).append ('}').append (sQName);
      for (int i = 0; i < aAttrs.getLength (); ++i)
        m_aSB.append (' ').append (aAttrs.getQName (i)).append ('=').append (aAttrs.getValue (i));
      m_aSB.append ('>');
    }

    @Override
    public void endElement (final String sUri, final String sLocal, final String sQName)
    {
      m_aSB.append ("</").append (sQName).append ('>');
    }

    @Override
    public void characters (final char [] aChars, final int nStart, final int nLength)
    {
      m_aSB.append (aChars, nStart, nLength);
    }

    @Override
    public void comment (final char [] aChars, final int nStart, final int nLength)
    {
      m_aSB.append ("<!--").append (aChars, nStart, nLength).append ("-->");
    }

    @Override
    public void processingInstruction (final String sTarget, final String sData)
    {
      m_aSB.append ("<?").append (sTarget).append (' ').append (sData).append ("?>");
    }

    @Override
    public String toString ()
    {
      return m_aSB.toString ();
    }
  }

  @NonNull
  private static StreamSource _source (@NonNull final String sXml)
  {
    return new StreamSource (new StringReader (sXml));
  }

  @NonNull
  private static Templates _compile (@NonNull final String sXsl) throws TransformerConfigurationException
  {
    return new PhoxtrotTransformerFactory ().newTemplates (_source (sXsl));
  }

  @NonNull
  private static String _transform (@NonNull final Transformer aTransformer,
                                    @NonNull final Source aSource) throws TransformerException
  {
    final StringWriter aSW = new StringWriter ();
    aTransformer.transform (aSource, new StreamResult (aSW));
    return aSW.toString ();
  }

  @NonNull
  private static Document _parseDom (@NonNull final String sXml) throws Exception
  {
    final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    return aFactory.newDocumentBuilder ().parse (new InputSource (new StringReader (sXml)));
  }

  @Test
  public void testNewInstanceByName () throws Exception
  {
    final TransformerFactory aTF = TransformerFactory.newInstance (PhoxtrotTransformerFactory.class.getName (),
                                                                   getClass ().getClassLoader ());
    assertTrue (aTF instanceof PhoxtrotTransformerFactory);
    // The JDK lookup is not changed: no service file is registered
    assertFalse (TransformerFactory.newDefaultInstance () instanceof PhoxtrotTransformerFactory);
    assertFalse (TransformerFactory.newInstance () instanceof PhoxtrotTransformerFactory);
  }

  @Test
  public void testStreamSourceAndResultKinds () throws Exception
  {
    final Templates aTemplates = _compile (COUNT_SHEET);
    final String sExpected = "<out name=\"root\" n=\"3\"/>";
    final Transformer aT = aTemplates.newTransformer ();
    // Reader
    assertEquals (sExpected, _transform (aT, _source (SOURCE)));
    // Input stream
    assertEquals (sExpected,
                  _transform (aT,
                              new StreamSource (new ByteArrayInputStream (SOURCE.getBytes (StandardCharsets.UTF_8)))));
    // System ID
    final Path aSrcFile = m_aTempDir.getRoot ().toPath ().resolve ("src.xml");
    Files.writeString (aSrcFile, SOURCE, StandardCharsets.UTF_8);
    assertEquals (sExpected, _transform (aT, new StreamSource (aSrcFile.toUri ().toString ())));
    assertEquals (sExpected, _transform (aT, new StreamSource (aSrcFile.toFile ())));

    // Output stream
    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    aT.transform (_source (SOURCE), new StreamResult (aBAOS));
    assertEquals (sExpected, aBAOS.toString (StandardCharsets.UTF_8));

    // File and system ID
    final Path aOut = m_aTempDir.getRoot ().toPath ().resolve ("out.xml");
    aT.transform (_source (SOURCE), new StreamResult (aOut.toFile ()));
    assertEquals (sExpected, Files.readString (aOut, StandardCharsets.UTF_8));
    final Path aOut2 = m_aTempDir.getRoot ().toPath ().resolve ("out2.xml");
    aT.transform (_source (SOURCE), new StreamResult (aOut2.toUri ().toString ()));
    assertEquals (sExpected, Files.readString (aOut2, StandardCharsets.UTF_8));

    try
    {
      aT.transform (_source (SOURCE), new StreamResult ("http://example.org/out.xml"));
      fail ();
    }
    catch (final TransformerException ex)
    {
      // only files
    }
  }

  @Test
  public void testDomSource () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
                                           "<xsl:template match='/'><doc base='{base-uri(.)}'/></xsl:template>" +
                                           "<xsl:template match='b'><elem anc='{count(ancestor::*)}' root='{exists(/)}'/></xsl:template>" +
                                           XSL_END);
    final Document aDom = _parseDom (SOURCE);
    final DOMSource aDocSource = new DOMSource (aDom, "http://example.org/src.xml");
    assertEquals ("<doc base=\"http://example.org/src.xml\"/>", _transform (aTemplates.newTransformer (), aDocSource));
    // An element: it is the context item within its whole document
    final Element aB = (Element) aDom.getDocumentElement ().getChildNodes ().item (1);
    assertEquals ("<elem anc=\"1\" root=\"true\"/>", _transform (aTemplates.newTransformer (), new DOMSource (aB)));
    // No node: an empty document
    assertEquals ("<doc base=\"\"/>", _transform (aTemplates.newTransformer (), new DOMSource ()));
  }

  @Test
  public void testSaxSource () throws Exception
  {
    final Templates aTemplates = _compile (COUNT_SHEET);
    final String sExpected = "<out name=\"root\" n=\"3\"/>";
    // Without reader: phoxtrot's parser
    assertEquals (sExpected,
                  _transform (aTemplates.newTransformer (),
                              new SAXSource (new InputSource (new StringReader (SOURCE)))));
    // With a reader of the caller
    final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    final XMLReader aReader = aFactory.newSAXParser ().getXMLReader ();
    assertEquals (sExpected,
                  _transform (aTemplates.newTransformer (),
                              new SAXSource (aReader,
                                             new InputSource (new StringReader ("<!--c--><root><x/><y/><z/></root>")))));
  }

  @Test
  public void testXdmSource () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse (SOURCE);
    assertEquals ("<out name=\"root\" n=\"3\"/>",
                  _transform (_compile (COUNT_SHEET).newTransformer (), new XdmSource (aDoc)));
    // As stylesheet too
    final IXdmNode aSheet = new XdmParser ().parse (COUNT_SHEET);
    final Templates aTemplates = new PhoxtrotTransformerFactory ().newTemplates (new XdmSource (aSheet));
    assertEquals ("<out name=\"root\" n=\"3\"/>", _transform (aTemplates.newTransformer (), _source (SOURCE)));
  }

  @Test
  public void testDomResult () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:template match='/'><xsl:comment>before</xsl:comment><p:r xmlns:p='urn:p' p:a='1'><e>t</e><xsl:processing-instruction name='pi'>d</xsl:processing-instruction></p:r></xsl:template>" +
                                           XSL_END);
    // A new document
    final DOMResult aResult = new DOMResult ();
    aResult.setSystemId ("http://example.org/result.xml");
    aTemplates.newTransformer ().transform (_source (SOURCE), aResult);
    final Document aDoc = (Document) aResult.getNode ();
    assertEquals (Node.COMMENT_NODE, aDoc.getFirstChild ().getNodeType ());
    final Element aRoot = aDoc.getDocumentElement ();
    assertEquals ("urn:p", aRoot.getNamespaceURI ());
    assertEquals ("p:r", aRoot.getTagName ());
    assertEquals ("1", aRoot.getAttributeNS ("urn:p", "a"));
    assertEquals ("t", aRoot.getFirstChild ().getTextContent ());
    assertEquals ("http://example.org/result.xml", aDoc.getDocumentURI ());

    // Into an existing element, before a sibling
    final Document aHostDoc = _parseDom ("<host><last/></host>");
    final Element aHost = aHostDoc.getDocumentElement ();
    aTemplates.newTransformer ().transform (_source (SOURCE), new DOMResult (aHost, aHost.getFirstChild ()));
    assertEquals (Node.COMMENT_NODE, aHost.getFirstChild ().getNodeType ());
    assertEquals ("r", aHost.getChildNodes ().item (1).getLocalName ());
    assertEquals ("last", aHost.getLastChild ().getLocalName ());
  }

  @Test
  public void testSaxResult () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:template match='/'><xsl:comment>c</xsl:comment><p:r xmlns:p='urn:p' a='1'>t<e/></p:r><xsl:processing-instruction name='pi'>d</xsl:processing-instruction></xsl:template>" +
                                           XSL_END);
    final RecordingHandler aHandler = new RecordingHandler ();
    aTemplates.newTransformer ().transform (_source (SOURCE), new SAXResult (aHandler));
    assertEquals ("[doc]<!--c-->[ns p=urn:p]<{urn:p}p:r a=1>t<{}e></e></p:r><?pi d?>[/doc]", aHandler.toString ());
  }

  @Test
  public void testParameters () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:output method='text'/>" +
                                           "<xsl:param name='s'/>" +
                                           "<xsl:param name='i' as='xs:integer' select='0'/>" +
                                           "<xsl:param name='fromString' as='xs:integer' select='0'/>" +
                                           "<xsl:param name='b' select='false()'/>" +
                                           "<xsl:param name='d' select='0'/>" +
                                           "<xsl:param name='seq' select='()'/>" +
                                           "<xsl:param name='node' select='()'/>" +
                                           "<xsl:param name='xdm' select='()'/>" +
                                           "<xsl:param name='q:ns' xmlns:q='urn:q' select='()'/>" +
                                           "<xsl:template match='/'>" +
                                           "<xsl:value-of select='$s, $i + 1, $fromString * 2, $b, $d instance of xs:double, count($seq), sum($seq), $node/local-name(), $xdm/name(*), $Q{urn:q}ns' separator='|'/>" +
                                           "</xsl:template>" +
                                           XSL_END);
    final Transformer aT = aTemplates.newTransformer ();
    aT.setParameter ("s", "text");
    aT.setParameter ("i", Integer.valueOf (41));
    aT.setParameter ("fromString", "21");
    aT.setParameter ("b", Boolean.TRUE);
    aT.setParameter ("d", Double.valueOf (1.5));
    aT.setParameter ("seq", List.of (Integer.valueOf (1), Integer.valueOf (2), new Object [] { Long.valueOf (3) }));
    aT.setParameter ("node", _parseDom ("<domroot/>").getDocumentElement ());
    aT.setParameter ("xdm", new XdmParser ().parse ("<xdmroot/>"));
    aT.setParameter ("{urn:q}ns", XdmAtomic.string ("qualified"));
    assertEquals ("text|42|42|true|true|3|6|domroot|xdmroot|qualified", _transform (aT, _source (SOURCE)));
    assertEquals ("21", aT.getParameter ("fromString"));
    assertEquals (Integer.valueOf (41), aT.getParameter ("i"));
    assertNotNull (aT.getParameter ("Q{urn:q}ns"));
    assertNull (aT.getParameter ("unknown"));

    aT.clearParameters ();
    assertNull (aT.getParameter ("s"));
    // Empty values give no item, so no separator
    assertEquals ("|1|0|false|false|0|0", _transform (aT, _source (SOURCE)));

    try
    {
      aT.setParameter ("s", null);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
    try
    {
      aT.setParameter ("s", new StringBuilder ("unsupported"));
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  @Test
  public void testParameterTypeError () throws Exception
  {
    final Transformer aT = _compile (XSL_START +
                                     "<xsl:param name='i' as='xs:integer'/><xsl:template match='/'/>" +
                                     XSL_END).newTransformer ();
    aT.setParameter ("i", "no number");
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aT.setErrorListener (aEL);
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (),
                  ex.getMessage ().startsWith ("FORG0001") || ex.getMessage ().startsWith ("XTTE0590"));
      assertEquals (1, aEL.m_aFatals.size ());
    }
  }

  @Test
  public void testUriResolverForInclude () throws Exception
  {
    final ICommonsList <String> aCalls = new CommonsArrayList <> ();
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setURIResolver ((sHref, sBase) -> {
      aCalls.add (sHref + "|" + sBase);
      if ("inc.xsl".equals (sHref))
        return _source (XSL_START + "<xsl:template match='/'><included/></xsl:template>" + XSL_END);
      return null;
    });
    final String sMain = XSL_START + "<xsl:output omit-xml-declaration='yes'/><xsl:include href='inc.xsl'/>" + XSL_END;
    final StreamSource aMain = _source (sMain);
    aMain.setSystemId ("http://example.org/xsl/main.xsl");
    final Templates aTemplates = aTF.newTemplates (aMain);
    // As JAXP specifies: the href as written and the base URI
    assertEquals ("[inc.xsl|http://example.org/xsl/main.xsl]", aCalls.toString ());
    assertEquals ("<included/>", _transform (aTemplates.newTransformer (), _source (SOURCE)));

    // Without a system ID the base is the empty string, so that the resolver
    // can use its own default base
    aCalls.clear ();
    aTF.newTemplates (_source (sMain));
    assertEquals ("[inc.xsl|]", aCalls.toString ());
  }

  @Test
  public void testUriResolverForDocument () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setURIResolver ((sHref, sBase) -> sHref.endsWith ("factory.xml") ? _source ("<from-factory/>") : null);
    final StreamSource aMain = _source (XSL_START +
                                        "<xsl:output method='text'/>" +
                                        "<xsl:template match='/'><xsl:value-of select=\"document('factory.xml')/*/name(), doc('transformer.xml')/*/name(), unparsed-text('text.txt')\"/></xsl:template>" +
                                        XSL_END);
    aMain.setSystemId ("http://example.org/xsl/main.xsl");
    final Templates aTemplates = aTF.newTemplates (aMain);
    final Transformer aT = aTemplates.newTransformer ();
    // The transformer starts with the factory's resolver
    assertNotNull (aT.getURIResolver ());
    final Document aTransformerDoc = _parseDom ("<from-transformer/>");
    aT.setURIResolver ((sHref, sBase) -> {
      assertEquals ("http://example.org/xsl/main.xsl", sBase);
      if (sHref.equals ("factory.xml"))
        return _source ("<from-factory/>");
      if (sHref.equals ("transformer.xml"))
        return new DOMSource (aTransformerDoc);
      if (sHref.equals ("text.txt"))
        return new StreamSource (new StringReader ("some text"));
      return null;
    });
    assertEquals ("from-factory from-transformer some text", _transform (aT, _source (SOURCE)));
  }

  @Test
  public void testSecureProcessingRejectsResultDocumentHref () throws Exception
  {
    final String sWithHref = XSL_START +
                             "<xsl:template match='/'><xsl:result-document href='secondary.xml'><a/></xsl:result-document><main/></xsl:template>" +
                             XSL_END;
    final String sWithoutHref = XSL_START +
                                "<xsl:template match='/'><xsl:result-document><main/></xsl:result-document></xsl:template>" +
                                XSL_END;
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    assertNotNull (aTF.newTemplates (_source (sWithHref)));

    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aTF.setErrorListener (aEL);
    try
    {
      aTF.newTemplates (_source (sWithHref));
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XTSE0010"));
      assertEquals (1, aEL.m_aFatals.size ());
      assertNotNull (ex.getLocator ());
      assertTrue (ex.getLocator ().getLineNumber () > 0);
    }
    // Without href the content goes to the principal result
    assertEquals ("<main/>", _transform (aTF.newTemplates (_source (sWithoutHref)).newTransformer (), _source ("<x/>")).replaceFirst ("^<\\?xml[^>]*>", ""));

    // Switched off again
    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, false);
    assertNotNull (aTF.newTemplates (_source (sWithHref)));
  }

  @Test
  public void testSecureProcessingBlocksDirectAccess () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("inc.xsl"), XSL_START + "<xsl:template match='/'><inc/></xsl:template>" + XSL_END);
    Files.writeString (aDir.resolve ("data.xml"), "<data/>");
    final String sMain = XSL_START + "<xsl:include href='inc.xsl'/>" + XSL_END;
    final String sMainUri = aDir.resolve ("main.xsl").toUri ().toString ();

    // Default: direct access to files
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    assertFalse (aTF.getFeature (XMLConstants.FEATURE_SECURE_PROCESSING));
    final StreamSource aSrc1 = _source (sMain);
    aSrc1.setSystemId (sMainUri);
    assertNotNull (aTF.newTemplates (aSrc1));

    // Secure processing: nothing without a resolver
    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    assertEquals ("", aTF.getAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET));
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aTF.setErrorListener (aEL);
    final StreamSource aSrc2 = _source (sMain);
    aSrc2.setSystemId (sMainUri);
    try
    {
      aTF.newTemplates (aSrc2);
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XTSE0165"));
      assertEquals (1, aEL.m_aFatals.size ());
      assertNotNull (ex.getLocator ());
      assertEquals (sMainUri, ex.getLocator ().getSystemId ());
      assertTrue (ex.getLocator ().getLineNumber () > 0);
      assertTrue (ex.getCause () instanceof XsltException);
    }

    // A list of schemes
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "jar, file");
    final StreamSource aSrc3 = _source (XSL_START +
                                        "<xsl:template match='/'><xsl:copy-of select=\"document('" +
                                        aDir.resolve ("data.xml").toUri () +
                                        "')\"/></xsl:template>" +
                                        XSL_END);
    final Transformer aT = aTF.newTemplates (aSrc3).newTransformer ();
    assertTrue (_transform (aT, _source (SOURCE)).endsWith ("<data/>"));
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "jar");
    final Transformer aT2 = aTF.newTemplates (_source (XSL_START +
                                                       "<xsl:template match='/'><xsl:copy-of select=\"document('" +
                                                       aDir.resolve ("data.xml").toUri () +
                                                       "')\"/></xsl:template>" +
                                                       XSL_END)).newTransformer ();
    try
    {
      _transform (aT2, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("FODC0002"));
    }
  }

  @Test
  public void testCompileError () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aTF.setErrorListener (aEL);
    final StreamSource aSrc = _source (XSL_START +
                                       "\n  <xsl:template match='/'>\n    <xsl:value-of select='1 +'/>\n  </xsl:template>\n" +
                                       XSL_END);
    aSrc.setSystemId ("http://example.org/bad.xsl");
    try
    {
      aTF.newTemplates (aSrc);
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XPST0003"));
      assertEquals (1, aEL.m_aFatals.size ());
      assertSame (ex, aEL.m_aFatals.getFirstOrNull ());
      assertEquals ("http://example.org/bad.xsl", ex.getLocator ().getSystemId ());
      assertEquals (3, ex.getLocator ().getLineNumber ());
    }

    // Not well-formed
    try
    {
      aTF.newTemplates (_source ("<xsl:stylesheet"));
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XTSE0010"));
    }

    // The listener decides what is thrown
    final TransformerException aOwn = new TransformerException ("own");
    aTF.setErrorListener (new ErrorListener ()
    {
      public void warning (final TransformerException ex)
      {}

      public void error (final TransformerException ex)
      {}

      public void fatalError (final TransformerException ex) throws TransformerException
      {
        throw aOwn;
      }
    });
    try
    {
      aTF.newTemplates (_source (XSL_START + "<xsl:unknown/>" + XSL_END));
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertSame (aOwn, ex.getCause ());
    }
  }

  @Test
  public void testDynamicError () throws Exception
  {
    final Transformer aT = _compile (XSL_START +
                                     "<xsl:template match='/'>\n<xsl:sequence select=\"error(QName('urn:e', 'e:CODE1'), 'custom')\"/></xsl:template>" +
                                     XSL_END).newTransformer ();
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aT.setErrorListener (aEL);
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertEquals ("CODE1: custom", ex.getMessage ());
      assertTrue (ex.getCause () instanceof XsltException);
      assertEquals (1, aEL.m_aFatals.size ());
    }
  }

  @Test
  public void testMessagesToErrorListener () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:template match='/'>\n" +
                                           "<xsl:message>first</xsl:message>\n" +
                                           "<xsl:message select='count(//*)'/>\n" +
                                           "<xsl:message terminate='yes'>stop</xsl:message>\n" +
                                           "</xsl:template>" +
                                           XSL_END);
    final Transformer aT = aTemplates.newTransformer ();
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aT.setErrorListener (aEL);
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XTMM9000"));
    }
    assertEquals (3, aEL.m_aWarnings.size ());
    assertEquals ("first", aEL.m_aWarnings.get (0).getMessage ());
    assertEquals ("4", aEL.m_aWarnings.get (1).getMessage ());
    assertEquals ("stop", aEL.m_aWarnings.get (2).getMessage ());
    assertEquals (2, aEL.m_aWarnings.get (0).getLocator ().getLineNumber ());
    assertEquals (1, aEL.m_aFatals.size ());

    // A listener that throws on a warning ends the transformation
    final TransformerException aStop = new TransformerException ("listener stop");
    aT.setErrorListener (new ErrorListener ()
    {
      public void warning (final TransformerException ex) throws TransformerException
      {
        throw aStop;
      }

      public void error (final TransformerException ex)
      {}

      public void fatalError (final TransformerException ex)
      {}
    });
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertSame (aStop, ex);
    }
  }

  @Test
  public void testMessageListener () throws Exception
  {
    final ICommonsList <String> aTexts = new CommonsArrayList <> ();
    final IXsltMessageListener aListener = (final XsltMessage aMsg) -> aTexts.add (aMsg.text ());
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MESSAGE_LISTENER, aListener);
    assertSame (aListener, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_MESSAGE_LISTENER));
    final PhoxtrotTransformer aT = aTF.newTemplates (_source (XSL_START +
                                                              "<xsl:template match='/'><xsl:message>m1</xsl:message><r/></xsl:template>" +
                                                              XSL_END)).newTransformer ();
    assertSame (aListener, aT.getMessageListener ());
    final RecordingErrorListener aEL = new RecordingErrorListener ();
    aT.setErrorListener (aEL);
    _transform (aT, _source (SOURCE));
    assertEquals ("[m1]", aTexts.toString ());
    assertTrue (aEL.m_aWarnings.isEmpty ());
    assertEquals ("[m1]", aT.getLastResult ().messages ().toString ());
  }

  @Test
  public void testTraceListener () throws Exception
  {
    final ICommonsList <String> aEvents = new CommonsArrayList <> ();
    final IXsltTraceListener aListener = new IXsltTraceListener ()
    {
      public void onTemplateRuleEnter (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
      {
        aEvents.add ("+" + aTemplate.matchPattern ());
      }

      public void onTemplateRuleLeave (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
      {
        aEvents.add ("-" + aTemplate.matchPattern ());
      }
    };
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    assertNull (aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER));
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER, aListener);
    assertSame (aListener, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER));
    final PhoxtrotTransformer aT = aTF.newTemplates (_source (COUNT_SHEET)).newTransformer ();
    assertSame (aListener, aT.getTraceListener ());
    assertEquals ("<out name=\"root\" n=\"3\"/>", _transform (aT, _source (SOURCE)));
    assertEquals ("[+/*, -/*]", aEvents.toString ());

    // Switched off on the transformer, back after reset
    aT.setTraceListener (null);
    _transform (aT, _source (SOURCE));
    assertEquals (2, aEvents.size ());
    aT.reset ();
    assertSame (aListener, aT.getTraceListener ());

    try
    {
      aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER, "no listener");
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  @Test
  public void testOutputProperties () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:output method='xml' encoding='ISO-8859-1' xmlns:c='urn:c' cdata-section-elements='c:x'/>" +
                                           "<xsl:template match='/'><r><e>é</e></r></xsl:template>" +
                                           XSL_END);
    final Properties aProps = aTemplates.getOutputProperties ();
    assertEquals ("xml", aProps.getProperty (OutputKeys.METHOD));
    assertEquals ("ISO-8859-1", aProps.getProperty (OutputKeys.ENCODING));
    assertEquals ("{urn:c}x", aProps.getProperty (OutputKeys.CDATA_SECTION_ELEMENTS));
    // A default
    assertEquals ("no", aProps.getProperty (OutputKeys.INDENT));
    assertNull (aProps.get (OutputKeys.INDENT));

    final Transformer aT = aTemplates.newTransformer ();
    assertEquals ("ISO-8859-1", aT.getOutputProperty (OutputKeys.ENCODING));
    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    aT.transform (_source (SOURCE), new StreamResult (aBAOS));
    assertEquals ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r><e>é</e></r>",
                  aBAOS.toString (StandardCharsets.ISO_8859_1));

    aT.setOutputProperty (OutputKeys.OMIT_XML_DECLARATION, "yes");
    aT.setOutputProperty (OutputKeys.INDENT, "yes");
    aT.setOutputProperty (OutputKeys.ENCODING, "US-ASCII");
    // Namespaced names are accepted without effect
    aT.setOutputProperty ("{http://xml.apache.org/xalan}indent-amount", "4");
    assertEquals ("yes", aT.getOutputProperty (OutputKeys.INDENT));
    assertEquals ("yes", aT.getOutputProperties ().getProperty (OutputKeys.INDENT));
    assertEquals ("xml", aT.getOutputProperties ().getProperty (OutputKeys.METHOD));
    final StringWriter aSW = new StringWriter ();
    aT.transform (_source (SOURCE), new StreamResult (aSW));
    final String sIndented = aSW.toString ();
    assertTrue (sIndented, sIndented.startsWith ("<r>\n"));
    assertTrue (sIndented, sIndented.contains ("<e>&#"));

    // The text method cannot escape: a character outside the encoding is SERE0008
    aT.setOutputProperty (OutputKeys.METHOD, "text");
    aT.setErrorListener (new RecordingErrorListener ());
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("SERE0008"));
    }
    aT.setOutputProperty (OutputKeys.ENCODING, "UTF-8");
    assertEquals ("é", _transform (aT, _source (SOURCE)));

    aT.setOutputProperties (null);
    assertEquals ("no", aT.getOutputProperty (OutputKeys.INDENT));
    try
    {
      aT.setOutputProperty ("unknown", "x");
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
    try
    {
      aT.setOutputProperty ("use-character-maps", "x");
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
    try
    {
      aT.getOutputProperty ("unknown");
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  @Test
  public void testCdataSectionElementsOverride () throws Exception
  {
    final Transformer aT = _compile (XSL_START +
                                     "<xsl:output omit-xml-declaration='yes'/>" +
                                     "<xsl:template match='/'><r><e xmlns='urn:x'>a&lt;b</e><f>c</f></r></xsl:template>" +
                                     XSL_END).newTransformer ();
    aT.setOutputProperty (OutputKeys.CDATA_SECTION_ELEMENTS, "{urn:x}e f");
    assertEquals ("<r><e xmlns=\"urn:x\"><![CDATA[a<b]]></e><f><![CDATA[c]]></f></r>",
                  _transform (aT, _source (SOURCE)));
  }

  @Test
  public void testIdentityTransformer () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    final String sXml = "<!--c--><p:r xmlns:p='urn:p' a='1'><e>t</e><?pi d?></p:r>";
    final Transformer aT = aTF.newTransformer ();
    assertEquals ("xml", aT.getOutputProperty (OutputKeys.METHOD));
    aT.setOutputProperty (OutputKeys.OMIT_XML_DECLARATION, "yes");
    assertEquals ("<!--c--><p:r xmlns:p=\"urn:p\" a=\"1\"><e>t</e><?pi d?></p:r>", _transform (aT, _source (sXml)));

    // DOM in, DOM out
    final DOMResult aResult = new DOMResult ();
    aTF.newTransformer ().transform (new DOMSource (_parseDom (sXml)), aResult);
    final Document aDoc = (Document) aResult.getNode ();
    assertEquals ("urn:p", aDoc.getDocumentElement ().getNamespaceURI ());
    assertEquals ("1", aDoc.getDocumentElement ().getAttribute ("a"));

    // SAX in, SAX out
    final RecordingHandler aHandler = new RecordingHandler ();
    aTF.newTransformer ()
       .transform (new SAXSource (new InputSource (new StringReader (sXml))), new SAXResult (aHandler));
    assertEquals ("[doc]<!--c-->[ns p=urn:p]<{urn:p}p:r a=1><{}e>t</e><?pi d?></p:r>[/doc]", aHandler.toString ());

    // An HTML document stays XML
    final Transformer aT2 = aTF.newTransformer ();
    aT2.setOutputProperty (OutputKeys.OMIT_XML_DECLARATION, "yes");
    assertEquals ("<html><br/></html>", _transform (aT2, _source ("<html><br/></html>")));
    // Element as source
    final Document aDom = _parseDom ("<r><x a='1'/></r>");
    assertEquals ("<x a=\"1\"/>", _transform (aT2, new DOMSource (aDom.getDocumentElement ().getFirstChild ())));
  }

  @Test
  public void testTemplatesFromSeveralThreads () throws Exception
  {
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:output method='text'/><xsl:param name='p'/>" +
                                           "<xsl:template match='/'><xsl:value-of select='$p, count(//*)'/></xsl:template>" +
                                           XSL_END);
    final ExecutorService aES = Executors.newFixedThreadPool (8);
    try
    {
      final ICommonsList <Future <String>> aFutures = new CommonsArrayList <> ();
      for (int i = 0; i < 64; ++i)
      {
        final int nIndex = i;
        aFutures.add (aES.submit (() -> {
          final Transformer aT = aTemplates.newTransformer ();
          aT.setParameter ("p", Integer.valueOf (nIndex));
          final StringBuilder aSB = new StringBuilder ("<r>");
          for (int j = 0; j < nIndex; ++j)
            aSB.append ("<e/>");
          aSB.append ("</r>");
          return _transform (aT, _source (aSB.toString ()));
        }));
      }
      for (int i = 0; i < aFutures.size (); ++i)
        assertEquals (i + " " + (i + 1), aFutures.get (i).get ());
    }
    finally
    {
      aES.shutdown ();
    }
  }

  @Test
  public void testFeaturesAndAttributes () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    for (final String sFeature : new String [] { StreamSource.FEATURE,
                                                 StreamResult.FEATURE,
                                                 DOMSource.FEATURE,
                                                 DOMResult.FEATURE,
                                                 SAXSource.FEATURE,
                                                 SAXResult.FEATURE,
                                                 XdmSource.FEATURE })
      assertTrue (sFeature, aTF.getFeature (sFeature));
    assertFalse (aTF.getFeature (SAXTransformerFactory.FEATURE));
    assertFalse (aTF.getFeature ("http://javax.xml.transform.stax.StAXSource/feature"));
    try
    {
      aTF.setFeature ("http://apache.org/xml/features/disallow-doctype-decl", true);
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      // expected
    }
    try
    {
      aTF.setFeature (DOMSource.FEATURE, false);
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      // expected
    }
    aTF.setFeature (DOMSource.FEATURE, true);

    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MAX_STEPS, "1000");
    assertEquals (Long.valueOf (1000), aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_MAX_STEPS));
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MAX_DEPTH, "50000");
    assertEquals (Integer.valueOf (50_000), aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_MAX_DEPTH));
    for (final Object aBad : new Object [] { "-1", "x", Long.valueOf (1L + Integer.MAX_VALUE), Boolean.TRUE })
      try
      {
        aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MAX_DEPTH, aBad);
        fail ();
      }
      catch (final IllegalArgumentException ex)
      {
        // expected
      }
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_LOAD_EXTERNAL_ENTITIES, Boolean.TRUE);
    assertEquals (Boolean.TRUE, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_LOAD_EXTERNAL_ENTITIES));
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_DTD, "file");
    assertEquals ("file", aTF.getAttribute (XMLConstants.ACCESS_EXTERNAL_DTD));
    try
    {
      aTF.setAttribute ("http://saxon.sf.net/feature/linenumbering", Boolean.TRUE);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
    try
    {
      aTF.setErrorListener (null);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }

  /** A stylesheet that includes "inc.xsl", with a base URI to resolve it against. */
  @NonNull
  private static StreamSource _mainWithInclude ()
  {
    final StreamSource ret = _source (XSL_START + "<xsl:include href='inc.xsl'/>" + XSL_END);
    ret.setSystemId ("http://example.org/xsl/main.xsl");
    return ret;
  }

  @Test
  public void testThreading () throws Exception
  {
    final Thread aCaller = Thread.currentThread ();
    final ICommonsList <Thread> aCompileThreads = new CommonsArrayList <> ();
    final ICommonsList <Thread> aRunThreads = new CommonsArrayList <> ();
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    assertSame (EThreading.DEFAULT, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_THREADING));
    // The include is resolved on the compiling thread
    aTF.setURIResolver ( (sHref, sBase) -> {
      aCompileThreads.add (Thread.currentThread ());
      return _source (XSL_START + "<xsl:template match='/'><xsl:message>m</xsl:message><r/></xsl:template>" + XSL_END);
    });
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MESSAGE_LISTENER,
                      (IXsltMessageListener) aMsg -> aRunThreads.add (Thread.currentThread ()));
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_THREADING, "CALLING_THREAD");
    assertSame (EThreading.CALLING_THREAD, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_THREADING));
    final PhoxtrotTransformer aT = aTF.newTemplates (_mainWithInclude ()).newTransformer ();
    assertSame (EThreading.CALLING_THREAD, aT.getThreading ());
    _transform (aT, _source (SOURCE));
    assertEquals (1, aCompileThreads.size ());
    assertSame (aCaller, aCompileThreads.get (0));
    assertEquals (1, aRunThreads.size ());
    assertSame (aCaller, aRunThreads.get (0));

    // The transformer's own choice, back to the factory's after reset
    aT.setThreading (EThreading.NEW_THREAD);
    _transform (aT, _source (SOURCE));
    assertEquals (2, aRunThreads.size ());
    assertTrue (aRunThreads.get (1) != aCaller);
    aT.reset ();
    assertSame (EThreading.CALLING_THREAD, aT.getThreading ());

    // Compilation on another thread
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_THREADING, EThreading.THREAD_POOL);
    aTF.newTemplates (_mainWithInclude ());
    assertEquals (2, aCompileThreads.size ());
    assertTrue (aCompileThreads.get (1) != aCaller);

    for (final Object aValue : new Object [] { "calling_thread", null, Boolean.TRUE })
      try
      {
        aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_THREADING, aValue);
        fail ();
      }
      catch (final IllegalArgumentException ex)
      {
        // expected
      }
    assertSame (EThreading.THREAD_POOL, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_THREADING));
  }

  @Test
  public void testStepLimit () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MAX_STEPS, Long.valueOf (1000));
    final Transformer aT = aTF.newTemplates (_source (XSL_START +
                                                      "<xsl:template match='/'><xsl:for-each select='1 to 100000'><x/></xsl:for-each></xsl:template>" +
                                                      XSL_END)).newTransformer ();
    aT.setErrorListener (new RecordingErrorListener ());
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XPDY0130"));
    }
  }

  @Test
  public void testDepthLimit () throws Exception
  {
    // A named template that recurses 50,000 times, as the ISO Schematron
    // include step does once per character of a long expression
    final String sXsl = XSL_START +
                        "<xsl:template name='rec'><xsl:param name='n'/>" +
                        "<xsl:choose><xsl:when test='$n = 0'><r/></xsl:when>" +
                        "<xsl:otherwise><xsl:call-template name='rec'><xsl:with-param name='n' select='$n - 1'/></xsl:call-template></xsl:otherwise>" +
                        "</xsl:choose></xsl:template>" +
                        "<xsl:template match='/'><xsl:call-template name='rec'><xsl:with-param name='n' select='50000'/></xsl:call-template></xsl:template>" +
                        XSL_END;

    // The default of 10,000 levels stops it
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_THREADING, EThreading.THREAD_POOL);
    PhoxtrotTransformer aT = (PhoxtrotTransformer) aTF.newTemplates (_source (sXsl)).newTransformer ();
    assertEquals (0, aT.getMaxDepth ());
    aT.setErrorListener (new RecordingErrorListener ());
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XPDY0130"));
    }

    // Raised above the default, and without a limit
    for (final Integer aMaxDepth : new Integer [] { Integer.valueOf (60_000),
                                                    Integer.valueOf (XsltTransformRequest.UNLIMITED_DEPTH) })
    {
      aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_MAX_DEPTH, aMaxDepth);
      aT = (PhoxtrotTransformer) aTF.newTemplates (_source (sXsl)).newTransformer ();
      assertEquals (aMaxDepth.intValue (), aT.getMaxDepth ());
      assertTrue (_transform (aT, _source (SOURCE)).contains ("<r"));
    }

    // The transformer lowers the limit of the factory, reset restores it
    aT.setMaxDepth (1_000);
    aT.setErrorListener (new RecordingErrorListener ());
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XPDY0130"));
    }
    aT.reset ();
    assertEquals (XsltTransformRequest.UNLIMITED_DEPTH, aT.getMaxDepth ());
  }

  @Test
  public void testInterruptedCaller () throws Exception
  {
    final int nIterations = 10_000_000;
    final Templates aTemplates = _compile (XSL_START +
                                           "<xsl:template match='/'><xsl:iterate select='1 to " +
                                           nIterations +
                                           "'><xsl:message>m</xsl:message></xsl:iterate><r/></xsl:template>" +
                                           XSL_END);
    for (final EThreading eMode : EThreading.values ())
    {
      final Thread aCaller = Thread.currentThread ();
      final AtomicInteger aCount = new AtomicInteger ();
      final PhoxtrotTransformer aT = (PhoxtrotTransformer) aTemplates.newTransformer ();
      aT.setThreading (eMode);
      aT.setErrorListener (new RecordingErrorListener ());
      // The first message interrupts the caller, as a cancelling application would
      aT.setMessageListener (aMsg -> {
        if (aCount.incrementAndGet () == 1)
          aCaller.interrupt ();
      });
      try
      {
        _transform (aT, _source (SOURCE));
        fail (eMode.name ());
      }
      catch (final TransformerException ex)
      {
        assertTrue (eMode + ": " + ex.getMessage (), ex.getMessage ().startsWith ("FOER0000"));
      }
      // The flag stays set; the caller clears it and goes on
      assertTrue (eMode.name (), Thread.interrupted ());

      // A run on a worker thread stops soon after, instead of running to its end
      int nLast = -1;
      final long nEnd = System.currentTimeMillis () + 5_000;
      while (aCount.get () != nLast && System.currentTimeMillis () < nEnd)
      {
        nLast = aCount.get ();
        Thread.sleep (100);
      }
      assertEquals (eMode.name (), nLast, aCount.get ());
      assertTrue (eMode + ": " + nLast, nLast < nIterations);
    }
  }

  @Test
  public void testAssociatedStylesheet () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    final StreamSource aDoc = _source ("<?xml-stylesheet href='a.xsl' type='text/xsl'?>" +
                                       "<?xml-stylesheet href='b.css' type='text/css'?>" +
                                       "<?xml-stylesheet href='alt.xsl' type='text/xsl' alternate='yes' title='alt'?>" +
                                       "<r/>");
    aDoc.setSystemId ("http://example.org/doc/d.xml");
    final Source aSheet = aTF.getAssociatedStylesheet (aDoc, null, null, null);
    assertEquals ("http://example.org/doc/a.xsl", aSheet.getSystemId ());

    // Selected by media
    final StreamSource aDoc2 = _source ("<?xml-stylesheet href='screen.xsl' type='text/xsl' media='screen'?>" +
                                        "<?xml-stylesheet href='print.xsl' type='text/xsl' media='print'?><r/>");
    aDoc2.setSystemId ("http://example.org/doc/d.xml");
    assertEquals ("http://example.org/doc/print.xsl",
                  aTF.getAssociatedStylesheet (aDoc2, "print", null, null).getSystemId ());

    // Several match: a stylesheet that imports them
    final StreamSource aDoc3 = _source ("<?xml-stylesheet href='one.xsl' type='text/xsl'?>" +
                                        "<?xml-stylesheet href='two.xsl' type='application/xslt+xml'?><r/>");
    aDoc3.setSystemId ("http://example.org/doc/d.xml");
    final StreamSource aCombined = (StreamSource) aTF.getAssociatedStylesheet (aDoc3, null, null, null);
    assertEquals ("http://example.org/doc/d.xml", aCombined.getSystemId ());
    final StringWriter aCombinedText = new StringWriter ();
    aCombined.getReader ().transferTo (aCombinedText);
    final String sCombined = aCombinedText.toString ();
    assertTrue (sCombined,
                sCombined.contains ("<xsl:import href='http://example.org/doc/one.xsl'/><xsl:import href='http://example.org/doc/two.xsl'/>"));

    assertNull (aTF.getAssociatedStylesheet (_source ("<r/>"), null, null, null));
  }

  @Test
  public void testSecondaryResultsAndReset () throws Exception
  {
    final PhoxtrotTransformer aT = new PhoxtrotTransformerFactory ().newTemplates (_source (XSL_START +
                                                                                            "<xsl:template match='/'><main/><xsl:result-document href='second.xml'><second/></xsl:result-document></xsl:template>" +
                                                                                            XSL_END)).newTransformer ();
    final DOMResult aResult = new DOMResult ();
    aResult.setSystemId ("http://example.org/out/main.xml");
    aT.transform (_source (SOURCE), aResult);
    final XsltTransformResult aLast = aT.getLastResult ();
    assertNotNull (aLast);
    assertEquals (1, aLast.secondaryResults ().size ());
    assertEquals ("second.xml", aLast.secondaryResults ().getFirstOrNull ().href ());
    // A DOM result is written from the tree, the principal result is not serialised
    assertNotNull (aLast.principalTree ());
    assertEquals ("", aLast.principalSerialized ());
    assertEquals ("main", ((Document) aResult.getNode ()).getDocumentElement ().getLocalName ());

    aT.setParameter ("x", "y");
    aT.setOutputProperty (OutputKeys.INDENT, "yes");
    aT.reset ();
    assertNull (aT.getLastResult ());
    assertNull (aT.getParameter ("x"));
    assertEquals ("no", aT.getOutputProperty (OutputKeys.INDENT));
  }
}
