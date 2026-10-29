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
package com.helger.xslt.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;

/**
 * Test class for class {@link XSLT30CaseRunner}, with a fake compiler in place
 * of the engine.
 *
 * @author Philip Helger
 */
public final class XSLT30CaseRunnerTest
{
  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  private Path m_aDir;

  /** Fake engine: records what it gets and answers with a fixed function. */
  private static class FakeCompiler implements IXSLT30Compiler
  {
    private final Function <XsltTransformRequest, XsltTransformResult> m_aTransform;
    private final Wrapper <XsltCompileOptions> m_aOptions = new Wrapper <> ();
    private final Wrapper <String> m_aText = new Wrapper <> ();
    private final Wrapper <IXdmNode> m_aNode = new Wrapper <> ();
    private final Wrapper <XsltTransformRequest> m_aRequest = new Wrapper <> ();

    FakeCompiler (final Function <XsltTransformRequest, XsltTransformResult> aTransform)
    {
      m_aTransform = aTransform;
    }

    private XsltExecutable _executable ()
    {
      return aRequest -> {
        m_aRequest.set (aRequest);
        return m_aTransform.apply (aRequest);
      };
    }

    public XsltExecutable compile (final String sStylesheet, final XsltCompileOptions aOptions)
    {
      m_aText.set (sStylesheet);
      m_aOptions.set (aOptions);
      return _executable ();
    }

    public XsltExecutable compile (final IXdmNode aStylesheet, final XsltCompileOptions aOptions)
    {
      m_aNode.set (aStylesheet);
      m_aOptions.set (aOptions);
      return _executable ();
    }
  }

  private static XsltTransformResult _result (final String sSerialized)
  {
    return new XsltTransformResult (XdmSequence.EMPTY,
                                    null,
                                    sSerialized,
                                    new CommonsArrayList <> (),
                                    new CommonsArrayList <> ());
  }

  private static void _assertAtomic (final EAtomicType eType, final String sLexical, final XdmSequence aSeq)
  {
    assertTrue (aSeq.isSingleton ());
    final XdmAtomic aAtomic = (XdmAtomic) aSeq.getFirst ();
    assertEquals (eType, aAtomic.getType ());
    assertEquals (sLexical, aAtomic.getLexical ());
  }

  @Before
  public void setUp () throws Exception
  {
    m_aDir = m_aTmp.getRoot ().toPath ().toRealPath ();
    Files.writeString (m_aDir.resolve ("t.xsl"),
                       "<?xml version='1.0' encoding='ISO-8859-1'?><xsl:stylesheet/>",
                       StandardCharsets.ISO_8859_1);
    Files.writeString (m_aDir.resolve ("src.xml"), "<doc><item>1</item><item>2</item></doc>", StandardCharsets.UTF_8);
    Files.writeString (m_aDir.resolve ("bad.xml"), "<doc>", StandardCharsets.UTF_8);
    Files.writeString (m_aDir.resolve ("embedded.xml"),
                       "<?xml-stylesheet type='text/xsl' href='#style'?><doc><xsl:stylesheet id='style' " +
                                                        "xmlns:xsl='http://www.w3.org/1999/XSL/Transform'/></doc>",
                       StandardCharsets.UTF_8);
  }

  private XSLT30CaseRunner.CaseResult _run (final IXSLT30Compiler aCompiler, final String sTestCases) throws Exception
  {
    final XSLT30TestSet aSet = XSLT30CatalogReader.readTestSet ("<test-set xmlns='http://www.w3.org/2012/10/xslt-test-catalog' " +
                                                                "xmlns:xs='http://www.w3.org/2001/XMLSchema' name='t'>" +
                                                                "<environment name='src'><source role='.' file='src.xml'/></environment>" +
                                                                sTestCases +
                                                                "</test-set>",
                                                                m_aDir);
    final XSLT30Catalog aCatalog = new XSLT30Catalog (m_aDir, new CommonsHashMap <> (), new CommonsArrayList <> ());
    final XSLT30CaseRunner aRunner = new XSLT30CaseRunner (aCatalog,
                                                           aCompiler,
                                                           new XSLT30StylesheetCache (),
                                                           OffsetDateTime.now (),
                                                           false,
                                                           false);
    return aRunner.run (aSet, aSet.testCases ().getFirstOrNull (), new XSLT30DocumentCache ());
  }

  @Test
  public void testRequest () throws Exception
  {
    final FakeCompiler aFake = new FakeCompiler (r -> _result ("<out/>"));
    final XSLT30CaseRunner.CaseResult aResult = _run (aFake,
                                                      "<test-case name='c'><environment ref='src'/><test>" +
                                                             "<stylesheet file='t.xsl'/>" +
                                                             "<param name='g' select='12'/>" +
                                                             "<param name='s' static='yes' select='111' as='xs:string'/>" +
                                                             "<initial-template xmlns:my='urn:my' name='my:start'>" +
                                                             "<param xmlns:my='urn:my' name='my:t' tunnel='yes' select=\"'v'\"/>" +
                                                             "</initial-template>" +
                                                             "<output file='out/result.xml'/>" +
                                                             "</test><result><assert-xml><![CDATA[<out/>]]></assert-xml></result></test-case>");
    assertTrue (aResult.toString (), aResult.outcome ().isPass ());

    // Compiled from text (decoded from ISO-8859-1) with the file URI as base
    assertNotNull (aFake.m_aText.get ());
    assertEquals (m_aDir.resolve ("t.xsl").toUri ().toString (), aFake.m_aOptions.get ().getBaseUri ());
    _assertAtomic (EAtomicType.STRING, "111", aFake.m_aOptions.get ().staticParams ().get (new XdmQName ("s")));

    final XsltTransformRequest aReq = aFake.m_aRequest.get ();
    assertEquals (new XdmQName ("urn:my", "start"), aReq.getInitialTemplate ());
    _assertAtomic (EAtomicType.INTEGER, "12", aReq.params ().get (new XdmQName ("g")));
    assertNull (aReq.params ().get (new XdmQName ("s")));
    assertEquals (1, aReq.templateParams ().size ());
    assertEquals (new XdmQName ("urn:my", "t"), aReq.templateParams ().getFirstOrNull ().name ());
    assertTrue (aReq.templateParams ().getFirstOrNull ().tunnel ());
    final IXdmNode aContext = (IXdmNode) aReq.getGlobalContextItem ();
    assertTrue (aContext.isDocument ());
    assertEquals (m_aDir.resolve ("src.xml").toUri ().toString (), aContext.getDocumentUri ());
    assertEquals (m_aDir.resolve ("out/result.xml").toUri ().toString (), aReq.getBaseOutputUri ());
    assertNotNull (aReq.getResolver ());
    assertNotNull (aReq.getCurrentDateTime ());
  }

  @Test
  public void testInitialModeAndContextItem () throws Exception
  {
    final FakeCompiler aFake = new FakeCompiler (r -> _result ("<out/>"));
    final XSLT30CaseRunner.CaseResult aResult = _run (aFake,
                                                      "<test-case name='c'><environment ref='src'/><test>" +
                                                             "<stylesheet file='t.xsl'/>" +
                                                             "<initial-mode name='m' select='//item'/>" +
                                                             "<context-item select='/doc'/>" +
                                                             "</test><result><assert-xml><![CDATA[<out/>]]></assert-xml></result></test-case>");
    assertTrue (aResult.outcome ().isPass ());
    final XsltTransformRequest aReq = aFake.m_aRequest.get ();
    assertEquals (new XdmQName ("m"), aReq.getInitialMode ());
    assertEquals (2, aReq.getInitialMatchSelection ().size ());
    assertEquals ("doc", ((IXdmNode) aReq.getGlobalContextItem ()).getName ().localName ());
    assertNull (aReq.getInitialTemplate ());
  }

  @Test
  public void testInitialFunction () throws Exception
  {
    final FakeCompiler aFake = new FakeCompiler (r -> new XsltTransformResult (XdmSequence.of (XdmAtomic.integer (4)),
                                                                                null,
                                                                                "4",
                                                                                new CommonsArrayList <> (),
                                                                                new CommonsArrayList <> ()));
    final XSLT30CaseRunner.CaseResult aResult = _run (aFake,
                                                      "<test-case name='c'><test><stylesheet file='t.xsl'/>" +
                                                             "<initial-function xmlns:f='urn:f' name='f:sq'><param select='2'/></initial-function>" +
                                                             "</test><result><all-of><assert-type>xs:integer</assert-type>" +
                                                             "<assert-count>1</assert-count></all-of></result></test-case>");
    assertTrue (aResult.toString (), aResult.outcome ().isPass ());
    final XsltTransformRequest aReq = aFake.m_aRequest.get ();
    assertEquals (new XdmQName ("urn:f", "sq"), aReq.getInitialFunction ());
    _assertAtomic (EAtomicType.INTEGER, "2", aReq.functionArgs ().getFirstOrNull ());
    assertNull (aReq.getGlobalContextItem ());
  }

  @Test
  public void testDefaultInitialTemplateAndEmbeddedStylesheet () throws Exception
  {
    final FakeCompiler aFake = new FakeCompiler (r -> _result ("<out/>"));
    final XSLT30CaseRunner.CaseResult aResult = _run (aFake,
                                                      "<test-case name='c'><environment><source role='.' file='embedded.xml' " +
                                                             "defines-stylesheet='true'/></environment><test><initial-template/></test>" +
                                                             "<result><assert-xml><![CDATA[<out/>]]></assert-xml></result></test-case>");
    assertTrue (aResult.toString (), aResult.outcome ().isPass ());
    assertEquals ("stylesheet", aFake.m_aNode.get ().getName ().localName ());
    assertEquals (new XdmQName (XdmConstants.NS_XSL, "initial-template"), aFake.m_aRequest.get ().getInitialTemplate ());
  }

  @Test
  public void testErrorsAndCrashes () throws Exception
  {
    final String sErrorCase = "<test-case name='c'><environment ref='src'/><test><stylesheet file='t.xsl'/></test>" +
                              "<result><error code='XTSE0010'/></result></test-case>";
    // A static error from the compiler satisfies an expected error
    final IXSLT30Compiler aStaticError = new FakeCompiler (r -> _result ("")) {
      @Override
      public XsltExecutable compile (final String sStylesheet, final XsltCompileOptions aOptions)
      {
        throw new XsltException ("XTSE0010", "bad", null, 1, 1);
      }
    };
    assertTrue (_run (aStaticError, sErrorCase).outcome ().isPass ());

    // Anything else is a crash, even for an expected error
    final IXSLT30Compiler aStub = new FakeCompiler (r -> _result ("")) {
      @Override
      public XsltExecutable compile (final String sStylesheet, final XsltCompileOptions aOptions)
      {
        throw new UnsupportedOperationException ("Not implemented yet");
      }
    };
    final XSLT30CaseRunner.CaseResult aCrash = _run (aStub, sErrorCase);
    assertEquals ("crash:UnsupportedOperationException", aCrash.outcome ().reason ());
    assertEquals ("Not implemented yet", aCrash.detail ());

    final FakeCompiler aRuntimeCrash = new FakeCompiler (r -> {
      throw new IllegalStateException ("boom");
    });
    assertEquals ("crash:IllegalStateException", _run (aRuntimeCrash, sErrorCase).outcome ().reason ());

    // A source that is not well-formed is an error the assertions see
    assertTrue (_run (new FakeCompiler (r -> _result ("")),
                      "<test-case name='c'><environment><source role='.' file='bad.xml'/></environment>" +
                                                             "<test><stylesheet file='t.xsl'/></test><result><error code='XTDE0000'/></result></test-case>").outcome ()
                                                                                                                                                          .isPass ());
  }

  @Test
  public void testSkips () throws Exception
  {
    final FakeCompiler aFake = new FakeCompiler (r -> _result (""));
    assertEquals ("feat:streaming",
                  _run (aFake,
                        "<test-case name='c'><dependencies><feature value='streaming'/></dependencies>" +
                               "<test><stylesheet file='t.xsl'/></test><result><error/></result></test-case>").outcome ()
                                                                                                         .reason ());
    assertEquals ("no-stylesheet",
                  _run (aFake, "<test-case name='c'><test/><result><error/></result></test-case>").outcome ().reason ());
    assertEquals ("no-assertion",
                  _run (aFake, "<test-case name='c'><test><stylesheet file='t.xsl'/></test><result/></test-case>").outcome ()
                                                                                                             .reason ());
    assertEquals ("harness",
                  _run (aFake,
                        "<test-case name='c'><test><stylesheet file='t.xsl'/><initial-template name='u:x'/></test>" +
                               "<result><error/></result></test-case>").outcome ().reason ());
  }

  @Test
  public void testReadXmlText () throws Exception
  {
    final Path aFile = m_aDir.resolve ("latin1.xsl");
    Files.write (aFile, "<?xml version='1.0' encoding='ISO-8859-1'?><a>ä</a>".getBytes (StandardCharsets.ISO_8859_1));
    assertTrue (XSLT30CaseRunner.readXmlText (aFile).endsWith ("<a>ä</a>"));
    Files.write (aFile, "﻿<a>ä</a>".getBytes (StandardCharsets.UTF_8));
    assertEquals ("<a>ä</a>", XSLT30CaseRunner.readXmlText (aFile));
  }
}
