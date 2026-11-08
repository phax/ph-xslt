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
package com.helger.phoxtrot.xslt;

import static com.helger.phoxtrot.xslt.XsltTestHelper.END;
import static com.helger.phoxtrot.xslt.XsltTestHelper.SHEET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringWriter;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.runtime.ResultDrain;

/**
 * Test class for {@link IXsltMessageListener} and
 * {@link XsltException#getCollectedMessages()}.
 *
 * @author Philip Helger
 */
public final class XsltMessageListenerTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";

  /** Records everything it receives, with the thread it was called on. */
  private static final class RecordingListener implements IXsltMessageListener
  {
    private final ICommonsList <XsltMessage> m_aMessages = new CommonsArrayList <> ();
    private final ICommonsList <XsltMessage> m_aWarnings = new CommonsArrayList <> ();
    private Thread m_aThread;

    public void onMessage (@NonNull final XsltMessage aMessage)
    {
      m_aMessages.add (aMessage);
      m_aThread = Thread.currentThread ();
    }

    @Override
    public void onWarning (@NonNull final XsltMessage aWarning)
    {
      m_aWarnings.add (aWarning);
    }

    @NonNull
    ICommonsList <String> texts ()
    {
      return m_aMessages.getAllMapped (XsltMessage::text);
    }
  }

  @NonNull
  private static XsltExecutable _compile (@NonNull final String sBody)
  {
    return XsltCompiler.compile (SHEET + sBody + END, new XsltCompileOptions ());
  }

  @NonNull
  private static XsltException _fail (@NonNull final XsltExecutable aExec, @NonNull final XsltTransformRequest aRequest)
  {
    try
    {
      aExec.transform (aRequest);
    }
    catch (final XsltException ex)
    {
      return ex;
    }
    throw new IllegalStateException ("no error");
  }

  @Test
  public void testLiveInOrder ()
  {
    final XsltExecutable aExec = _compile ("<xsl:output method='text'/>" +
                                           IT +
                                           "<xsl:message select=\"'one'\"/>" +
                                           "<xsl:for-each select='1 to 2'><xsl:message>item <xsl:value-of select='.'/></xsl:message></xsl:for-each>" +
                                           "<xsl:message><e a='1' xsl:exclude-result-prefixes='xs'/></xsl:message>" +
                                           "done" +
                                           TE);
    final RecordingListener aListener = new RecordingListener ();
    final XsltTransformResult aResult = aExec.transform (new XsltTransformRequest ().setMessageListener (aListener));
    assertEquals ("done", aResult.principalSerialized ());
    assertEquals (new CommonsArrayList <> ("one", "item 1", "item 2", "<e a=\"1\"/>"), aListener.texts ());
    // The collected texts are unchanged
    assertEquals (aListener.texts (), aResult.messages ());
    final XsltMessage aFirst = aListener.m_aMessages.getFirstOrNull ();
    assertSame (EXsltMessageKind.MESSAGE, aFirst.kind ());
    assertFalse (aFirst.terminate ());
    assertNull (aFirst.errorCode ());
    assertEquals (1, aFirst.line ());
    assertTrue (aFirst.column () > 0);
    // The content keeps the nodes
    final XsltMessage aLast = aListener.m_aMessages.getLastOrNull ();
    assertEquals (1, aLast.content ().size ());
    assertEquals ("e", ((IXdmNode) aLast.content ().getFirst ()).getName ().localName ());
    // Called on the worker thread
    assertNotEquals (Thread.currentThread (), aListener.m_aThread);
    assertTrue (aListener.m_aWarnings.isEmpty ());
  }

  @Test
  public void testTerminate ()
  {
    final XsltExecutable aExec = _compile ("<xsl:template name='xsl:initial-template' xmlns:my='urn:my'>" +
                                           "<xsl:message>before</xsl:message>" +
                                           "<xsl:message terminate='yes' error-code='my:stop'>stop</xsl:message>" +
                                           "<xsl:message>never</xsl:message>" +
                                           TE);
    final RecordingListener aListener = new RecordingListener ();
    final XsltException ex = _fail (aExec, new XsltTransformRequest ().setMessageListener (aListener));
    assertEquals ("stop", ex.getCodeName ().localName ());
    assertEquals (new CommonsArrayList <> ("before", "stop"), aListener.texts ());
    final ICommonsList <XsltMessage> aCollected = ex.getCollectedMessages ();
    assertEquals (aListener.m_aMessages, aCollected);
    final XsltMessage aStop = aCollected.getLastOrNull ();
    assertTrue (aStop.terminate ());
    assertEquals ("urn:my", aStop.errorCode ().namespaceUri ());
    // A copy
    aCollected.clear ();
    assertEquals (2, ex.getCollectedMessages ().size ());

    // Without listener the messages are attached as well; default code
    final XsltException ex2 = _fail (_compile (IT + "<xsl:message>a</xsl:message><xsl:message terminate='yes'/>" + TE),
                                     new XsltTransformRequest ());
    assertEquals ("XTMM9000", ex2.getCode ());
    assertEquals (2, ex2.getCollectedMessages ().size ());
    assertEquals ("XTMM9000", ex2.getCollectedMessages ().getLastOrNull ().errorCode ().localName ());
  }

  @Test
  public void testLaterDynamicError ()
  {
    final XsltException ex = _fail (_compile (IT +
                                              "<xsl:message>explanation</xsl:message>" +
                                              "<xsl:value-of select=\"error(QName('urn:e', 'e:boom'))\"/>" +
                                              TE),
                                    new XsltTransformRequest ());
    assertEquals ("boom", ex.getCodeName ().localName ());
    assertEquals (1, ex.getCollectedMessages ().size ());
    assertEquals ("explanation", ex.getCollectedMessages ().getFirstOrNull ().text ());
    assertFalse (ex.getCollectedMessages ().getFirstOrNull ().terminate ());

    // An error before any message
    assertTrue (_fail (_compile (IT + "<xsl:value-of select='1 idiv 0'/>" + TE), new XsltTransformRequest ())
                                                                                                         .getCollectedMessages ()
                                                                                                         .isEmpty ());
  }

  @Test
  public void testInvalidTerminate ()
  {
    // The message is output before XTDE0030
    final RecordingListener aListener = new RecordingListener ();
    final XsltException ex = _fail (_compile (IT + "<xsl:message terminate='{\"maybe\"}'>m</xsl:message>" + TE),
                                    new XsltTransformRequest ().setMessageListener (aListener));
    assertEquals ("XTDE0030", ex.getCode ());
    assertEquals (new CommonsArrayList <> ("m"), aListener.texts ());
    assertFalse (aListener.m_aMessages.getFirstOrNull ().terminate ());
    assertEquals (1, ex.getCollectedMessages ().size ());
  }

  @Test
  public void testTryCatch ()
  {
    // Messages are not rolled back: a terminating message caught by xsl:try
    // stays reported and collected
    final XsltExecutable aExec = _compile ("<xsl:output method='text'/>" +
                                           IT +
                                           "<xsl:try rollback-output='yes'>x<xsl:message>inside</xsl:message>" +
                                           "<xsl:message terminate='yes'>stop</xsl:message>" +
                                           "<xsl:catch>caught</xsl:catch></xsl:try>" +
                                           TE);
    final RecordingListener aListener = new RecordingListener ();
    final XsltTransformResult aResult = aExec.transform (new XsltTransformRequest ().setMessageListener (aListener));
    assertEquals ("caught", aResult.principalSerialized ());
    assertEquals (new CommonsArrayList <> ("inside", "stop"), aListener.texts ());
    assertEquals (aListener.texts (), aResult.messages ());
  }

  @Test
  public void testAssert ()
  {
    final RecordingListener aListener = new RecordingListener ();
    final XsltException ex = _fail (_compile (IT +
                                              "<xsl:assert test='true()'>fine</xsl:assert>" +
                                              "<xsl:assert test='false()'>broken</xsl:assert>" +
                                              TE),
                                    new XsltTransformRequest ().setMessageListener (aListener));
    assertEquals ("XTMM9001", ex.getCode ());
    assertEquals (1, aListener.m_aMessages.size ());
    final XsltMessage aMessage = aListener.m_aMessages.getFirstOrNull ();
    assertSame (EXsltMessageKind.ASSERTION, aMessage.kind ());
    assertEquals ("broken", aMessage.text ());
    assertTrue (aMessage.terminate ());
    assertEquals ("XTMM9001", aMessage.errorCode ().localName ());
    assertEquals (aListener.m_aMessages, ex.getCollectedMessages ());
  }

  @Test
  public void testWarnings ()
  {
    final XsltExecutable aExec = _compile ("<xsl:mode warning-on-no-match='yes' warning-on-multiple-match='yes'/>" +
                                           "<xsl:output method='text'/>" +
                                           "<xsl:template match='b'>1</xsl:template>" +
                                           "<xsl:template match='b'>2</xsl:template>" +
                                           "<xsl:template match='/'><xsl:apply-templates select='*/*'/></xsl:template>");
    final RecordingListener aListener = new RecordingListener ();
    final XsltTransformResult aResult = aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<r><a/><b/></r>"))
                                                                                    .setMessageListener (aListener));
    assertEquals ("2", aResult.principalSerialized ());
    assertEquals (2, aListener.m_aWarnings.size ());
    assertTrue (aListener.m_aWarnings.get (0).text (), aListener.m_aWarnings.get (0).text ().startsWith ("No template rule"));
    assertTrue (aListener.m_aWarnings.get (1).text (), aListener.m_aWarnings.get (1).text ().startsWith ("Several template rules"));
    for (final XsltMessage aWarning : aListener.m_aWarnings)
    {
      assertSame (EXsltMessageKind.WARNING, aWarning.kind ());
      assertTrue (aWarning.line () > 0);
    }
    // Warnings are not messages
    assertTrue (aListener.m_aMessages.isEmpty ());
    assertTrue (aResult.messages ().isEmpty ());
  }

  @NonNull
  private static XsltTransformRequest _outerRequest (@NonNull final String sInner, final boolean bFail, final boolean bEnable)
  {
    final XsltTransformRequest ret = new XsltTransformRequest ();
    ret.params ().put (new XdmQName ("inner"), XdmSequence.of (XdmAtomic.string (sInner)));
    ret.params ().put (new XdmQName ("fail"), XdmSequence.of (XdmAtomic.bool (bFail)));
    ret.params ().put (new XdmQName ("enable"), XdmSequence.of (XdmAtomic.bool (bEnable)));
    return ret;
  }

  @Test
  public void testFnTransform ()
  {
    final String sInner = SHEET +
                          IT +
                          "<xsl:message>inner</xsl:message>" +
                          "<xsl:if test='$fail'><xsl:message terminate='yes'>inner stop</xsl:message></xsl:if>" +
                          TE +
                          "<xsl:param name='fail' select='false()'/>" +
                          END;
    final String sOuter = "<xsl:param name='inner'/><xsl:param name='fail'/><xsl:param name='enable'/>" +
                          IT +
                          "<xsl:message>outer</xsl:message>" +
                          "<xsl:sequence select=\"transform(map{'stylesheet-text': $inner, 'enable-messages': $enable," +
                          " 'stylesheet-params': map{QName('', 'fail'): $fail}})?output\"/>" +
                          TE;
    final XsltExecutable aExec = _compile (sOuter);

    // The inner messages reach the caller's listener, but not its result
    RecordingListener aListener = new RecordingListener ();
    final XsltTransformResult aResult = aExec.transform (_outerRequest (sInner, false, true).setMessageListener (aListener));
    assertEquals (new CommonsArrayList <> ("outer", "inner"), aListener.texts ());
    assertEquals (new CommonsArrayList <> ("outer"), aResult.messages ());

    // enable-messages false: not forwarded
    aListener = new RecordingListener ();
    aExec.transform (_outerRequest (sInner, false, false).setMessageListener (aListener));
    assertEquals (new CommonsArrayList <> ("outer"), aListener.texts ());

    // The inner run fails: the outer messages first, then the inner ones
    aListener = new RecordingListener ();
    final XsltException ex = _fail (aExec, _outerRequest (sInner, true, true).setMessageListener (aListener));
    assertEquals ("XTMM9000", ex.getCode ());
    assertEquals (new CommonsArrayList <> ("outer", "inner", "inner stop"), aListener.texts ());
    assertEquals (aListener.m_aMessages, ex.getCollectedMessages ());
  }

  @Test
  public void testWriterPath ()
  {
    // The drained output path: messages are reported live and attached
    final XsltExecutable aExec = _compile ("<xsl:output method='xml' omit-xml-declaration='yes'/>" +
                                           IT +
                                           "<r><xsl:for-each select='1 to 3'><i/><xsl:message select='.'/></xsl:for-each>" +
                                           "<xsl:message terminate='{if (true()) then \"yes\" else \"no\"}'>end</xsl:message></r>" +
                                           TE);
    final RecordingListener aListener = new RecordingListener ();
    final long nDrained = ResultDrain.getDrainedRunCount ();
    try
    {
      aExec.transform (new XsltTransformRequest ().setMessageListener (aListener), new StringWriter ());
      fail ();
    }
    catch (final XsltException ex)
    {
      assertEquals ("XTMM9000", ex.getCode ());
      assertEquals (new CommonsArrayList <> ("1", "2", "3", "end"), aListener.texts ());
      assertEquals (aListener.m_aMessages, ex.getCollectedMessages ());
    }
    assertEquals (nDrained + 1, ResultDrain.getDrainedRunCount ());
  }

  @Test
  public void testListenerException ()
  {
    final XsltExecutable aExec = _compile (IT + "<xsl:message>m</xsl:message>" + TE);
    try
    {
      aExec.transform (new XsltTransformRequest ().setMessageListener (aMessage -> {
        throw new IllegalStateException ("listener " + aMessage.text ());
      }));
      fail ();
    }
    catch (final IllegalStateException ex)
    {
      assertEquals ("listener m", ex.getMessage ());
    }
  }
}
