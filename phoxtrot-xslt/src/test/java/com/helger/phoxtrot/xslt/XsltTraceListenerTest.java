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
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.model.Mode;
import com.helger.phoxtrot.xslt.runtime.ResultDrain;

/**
 * Test class for {@link IXsltTraceListener}.
 *
 * @author Philip Helger
 */
public final class XsltTraceListenerTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";
  private static final String SOURCE = "<r><a/><b/></r>";
  /**
   * Template rules with next-match, a named template with xsl:try around a
   * failing named template, and a stylesheet function.
   */
  private static final String RULES = SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet xmlns:my='urn:my' exclude-result-prefixes='my xs' ") +
                                      "\n<xsl:function name='my:f' as='xs:integer'><xsl:param name='n'/><xsl:sequence select='$n * 2'/></xsl:function>" +
                                      "\n<xsl:template match='/'><out><xsl:apply-templates select='r/*'/><xsl:call-template name='t'/></out></xsl:template>" +
                                      "\n<xsl:template match='a'><xsl:value-of select='my:f(xs:integer(string-length(name())))'/></xsl:template>" +
                                      "\n<xsl:template match='b' priority='2'><xsl:next-match/></xsl:template>" +
                                      "\n<xsl:template match='*'>[<xsl:value-of select='local-name()'/>]</xsl:template>" +
                                      "\n<xsl:template name='t'><xsl:try><xsl:call-template name='boom'/><xsl:catch>caught</xsl:catch></xsl:try></xsl:template>" +
                                      "\n<xsl:template name='boom'><xsl:sequence select=\"error(QName('', 'BOOM'), 'boom')\"/></xsl:template>" +
                                      END;

  /**
   * Records every call as a short text and checks with a stack that each
   * leave call matches the latest open enter call.
   */
  private static final class RecordingTrace implements IXsltTraceListener
  {
    private final boolean m_bInstructions;
    private final ICommonsList <String> m_aEvents = new CommonsArrayList <> ();
    private final ICommonsList <Object> m_aOpen = new CommonsArrayList <> ();
    private final ICommonsList <XsltTraceTemplate> m_aTemplates = new CommonsArrayList <> ();
    private final ICommonsList <IXdmItem> m_aContextItems = new CommonsArrayList <> ();
    private final ICommonsList <XsltTraceFunction> m_aFunctions = new CommonsArrayList <> ();
    private Thread m_aThread;

    RecordingTrace (final boolean bInstructions)
    {
      m_bInstructions = bInstructions;
    }

    private void _enter (@NonNull final String sEvent, @NonNull final Object aKey)
    {
      m_aEvents.add ("+" + sEvent);
      m_aOpen.add (aKey);
      m_aThread = Thread.currentThread ();
    }

    private void _leave (@NonNull final String sEvent, @NonNull final Object aKey)
    {
      m_aEvents.add ("-" + sEvent);
      assertFalse ("Unbalanced leave " + sEvent, m_aOpen.isEmpty ());
      assertEquals (m_aOpen.removeLast (), aKey);
    }

    @NonNull
    private static String _rule (@NonNull final XsltTraceTemplate aTemplate)
    {
      final String ret = "rule " + aTemplate.matchPattern ();
      if (Mode.UNNAMED.equals (aTemplate.mode ()))
        return ret;
      return ret + " mode=" + aTemplate.mode ().localName ();
    }

    public void onTransformStart ()
    {
      _enter ("transform", "transform");
    }

    public void onTransformEnd ()
    {
      _leave ("transform", "transform");
    }

    public void onTemplateRuleEnter (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
    {
      m_aTemplates.add (aTemplate);
      m_aContextItems.add (aContextItem);
      _enter (_rule (aTemplate), aTemplate);
    }

    public void onTemplateRuleLeave (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
    {
      _leave (_rule (aTemplate), aTemplate);
    }

    public void onNamedTemplateEnter (@NonNull final XsltTraceTemplate aTemplate, @Nullable final IXdmItem aContextItem)
    {
      m_aTemplates.add (aTemplate);
      m_aContextItems.add (aContextItem);
      _enter ("call " + aTemplate.name ().localName (), aTemplate);
    }

    public void onNamedTemplateLeave (@NonNull final XsltTraceTemplate aTemplate, @Nullable final IXdmItem aContextItem)
    {
      _leave ("call " + aTemplate.name ().localName (), aTemplate);
    }

    public void onFunctionEnter (@NonNull final XsltTraceFunction aFunction)
    {
      m_aFunctions.add (aFunction);
      _enter ("fn " + aFunction.name ().localName () + "#" + aFunction.arity (), aFunction);
    }

    public void onFunctionLeave (@NonNull final XsltTraceFunction aFunction)
    {
      _leave ("fn " + aFunction.name ().localName () + "#" + aFunction.arity (), aFunction);
    }

    public boolean isTraceInstructions ()
    {
      return m_bInstructions;
    }

    public void onInstructionEnter (@NonNull final XsltTraceInstruction aInstruction)
    {
      _enter ("i " + aInstruction.name ().localName (), aInstruction);
    }

    public void onInstructionLeave (@NonNull final XsltTraceInstruction aInstruction)
    {
      _leave ("i " + aInstruction.name ().localName (), aInstruction);
    }

    /** @return The events without the instructions. */
    @NonNull
    ICommonsList <String> templateEvents ()
    {
      return m_aEvents.getAll (sEvent -> !sEvent.startsWith ("+i ") && !sEvent.startsWith ("-i "));
    }

    void assertBalanced ()
    {
      assertTrue ("Still open: " + m_aOpen, m_aOpen.isEmpty ());
    }
  }

  @NonNull
  private static XsltTransformRequest _sourceRequest ()
  {
    return new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (SOURCE));
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
  public void testBalancedTemplatesAndFunctions ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (RULES, new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (false);
    final XsltTransformResult aResult = aExec.transform (_sourceRequest ().setTraceListener (aTrace));
    assertEquals (new CommonsArrayList <> ("+transform",
                                           "+rule /",
                                           "+rule a",
                                           "+fn f#1",
                                           "-fn f#1",
                                           "-rule a",
                                           "+rule b",
                                           "+rule *",
                                           "-rule *",
                                           "-rule b",
                                           "+call t",
                                           "+call boom",
                                           "-call boom",
                                           "-call t",
                                           "-rule /",
                                           "-transform"),
                  aTrace.m_aEvents);
    aTrace.assertBalanced ();
    // Called on the worker thread
    assertNotEquals (Thread.currentThread (), aTrace.m_aThread);

    // Without a listener the result is the same
    final XsltTransformResult aPlain = aExec.transform (_sourceRequest ());
    assertEquals (aPlain.principalSerialized (), aResult.principalSerialized ());
    assertTrue (aResult.principalSerialized ().endsWith ("<out>2[b]caught</out>"));
  }

  @Test
  public void testTemplateDetails ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (RULES, new XsltCompileOptions ().setBaseUri ("urn:test:rules.xsl"));
    final RecordingTrace aTrace = new RecordingTrace (false);
    aExec.transform (_sourceRequest ().setTraceListener (aTrace));
    final ICommonsList <XsltTraceTemplate> aTemplates = aTrace.m_aTemplates;
    assertEquals (6, aTemplates.size ());

    // match='/' on line 3
    final XsltTraceTemplate aRoot = aTemplates.get (0);
    assertTrue (aRoot.isTemplateRule ());
    assertEquals ("/", aRoot.matchPattern ());
    assertEquals (Mode.UNNAMED, aRoot.mode ());
    assertNull (aRoot.name ());
    assertEquals ("urn:test:rules.xsl", aRoot.moduleUri ());
    assertEquals (3, aRoot.line ());
    assertTrue (aRoot.column () > 0);
    assertTrue (((IXdmNode) aTrace.m_aContextItems.get (0)).isDocument ());

    // Default and explicit priorities
    final XsltTraceTemplate aA = aTemplates.get (1);
    assertEquals ("a", aA.matchPattern ());
    assertEquals (0, Double.compare (0.0, aA.priority ()));
    assertEquals (4, aA.line ());
    assertEquals ("a", ((IXdmNode) aTrace.m_aContextItems.get (1)).getName ().localName ());
    assertEquals (0, Double.compare (2.0, aTemplates.get (2).priority ()));
    // next-match: the same context item
    assertEquals ("*", aTemplates.get (3).matchPattern ());
    assertEquals (0, Double.compare (-0.5, aTemplates.get (3).priority ()));
    assertSame (aTrace.m_aContextItems.get (2), aTrace.m_aContextItems.get (3));

    // Named template: no pattern, mode and priority, the caller's focus
    final XsltTraceTemplate aT = aTemplates.get (4);
    assertFalse (aT.isTemplateRule ());
    assertNull (aT.matchPattern ());
    assertNull (aT.mode ());
    assertEquals (new XdmQName ("t"), aT.name ());
    assertTrue (Double.isNaN (aT.priority ()));
    assertEquals (7, aT.line ());
    assertSame (aTrace.m_aContextItems.get (0), aTrace.m_aContextItems.get (4));

    // Function
    assertEquals (1, aTrace.m_aFunctions.size ());
    final XsltTraceFunction aF = aTrace.m_aFunctions.getFirstOrNull ();
    assertEquals (new XdmQName ("urn:my", "f"), aF.name ());
    assertEquals (1, aF.arity ());
    assertEquals ("urn:test:rules.xsl", aF.moduleUri ());
    assertEquals (2, aF.line ());
    assertTrue (aF.column () > 0);
  }

  @Test
  public void testModeAndUnionPattern ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       "<xsl:template match='/'><xsl:apply-templates select='r/*' mode='m'/></xsl:template>" +
                                                       "<xsl:template match='a | b' mode='m' name='ab'><xsl:value-of select='name()'/></xsl:template>" +
                                                       END,
                                                       new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (false);
    aExec.transform (_sourceRequest ().setTraceListener (aTrace));
    // One rule per alternative of the union, the name is kept
    assertEquals (new CommonsArrayList <> ("+transform",
                                           "+rule /",
                                           "+rule a mode=m",
                                           "-rule a mode=m",
                                           "+rule b mode=m",
                                           "-rule b mode=m",
                                           "-rule /",
                                           "-transform"),
                  aTrace.m_aEvents);
    assertEquals (new XdmQName ("ab"), aTrace.m_aTemplates.get (1).name ());
    assertTrue (aTrace.m_aTemplates.get (1).isTemplateRule ());
  }

  @Test
  public void testBuiltInRuleNotReported ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET + "<xsl:template match='b'>B</xsl:template>" + END, new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (false);
    aExec.transform (_sourceRequest ().setTraceListener (aTrace));
    // The built-in rules for the document node, r and a are not reported
    assertEquals (new CommonsArrayList <> ("+transform", "+rule b", "-rule b", "-transform"), aTrace.m_aEvents);
  }

  @Test
  public void testTerminatingMessage ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       IT +
                                                       "<xsl:call-template name='inner'/>" +
                                                       TE +
                                                       "<xsl:template name='inner'><xsl:for-each select='1 to 3'>" +
                                                       "<xsl:message terminate='{if (. = 2) then \"yes\" else \"no\"}' select='.'/>" +
                                                       "</xsl:for-each></xsl:template>" +
                                                       END,
                                                       new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (true);
    final XsltException ex = _fail (aExec, new XsltTransformRequest ().setTraceListener (aTrace));
    assertEquals ("XTMM9000", ex.getCode ());
    aTrace.assertBalanced ();
    assertEquals (new CommonsArrayList <> ("+transform",
                                           "+call initial-template",
                                           "+i call-template",
                                           "+call inner",
                                           "+i for-each",
                                           "+i message",
                                           "-i message",
                                           "+i message",
                                           "-i message",
                                           "-i for-each",
                                           "-call inner",
                                           "-i call-template",
                                           "-call initial-template",
                                           "-transform"),
                  aTrace.m_aEvents);
  }

  @Test
  public void testErrorInFunctionCaught ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet xmlns:my='urn:my' ") +
                                                       "<xsl:output method='text'/>" +
                                                       "<xsl:function name='my:fail'><xsl:param name='n'/>" +
                                                       "<xsl:sequence select=\"if ($n gt 0) then error() else 0\"/></xsl:function>" +
                                                       IT +
                                                       "<xsl:try select='my:fail(1)'><xsl:catch select=\"'caught'\"/></xsl:try>" +
                                                       TE +
                                                       END,
                                                       new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (false);
    assertEquals ("caught", aExec.transform (new XsltTransformRequest ().setTraceListener (aTrace)).principalSerialized ());
    assertEquals (new CommonsArrayList <> ("+transform",
                                           "+call initial-template",
                                           "+fn fail#1",
                                           "-fn fail#1",
                                           "-call initial-template",
                                           "-transform"),
                  aTrace.m_aEvents);
  }

  @Test
  public void testInstructions ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       IT +
                                                       "<xsl:variable name='v' select='1'/>" +
                                                       "<r>text <xsl:value-of select='$v'/>" +
                                                       "<e><xsl:sequence select='()'/><xsl:on-empty>empty</xsl:on-empty></e></r>" +
                                                       TE +
                                                       END,
                                                       new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (true);
    final XsltTransformResult aResult = aExec.transform (new XsltTransformRequest ().setTraceListener (aTrace));
    aTrace.assertBalanced ();
    // Text is no instruction; xsl:on-empty is reported where it runs
    assertEquals (new CommonsArrayList <> ("+transform",
                                           "+call initial-template",
                                           "+i variable",
                                           "-i variable",
                                           "+i r",
                                           "+i value-of",
                                           "-i value-of",
                                           "+i e",
                                           "+i sequence",
                                           "-i sequence",
                                           "+i on-empty",
                                           "-i on-empty",
                                           "-i e",
                                           "-i r",
                                           "-call initial-template",
                                           "-transform"),
                  aTrace.m_aEvents);
    assertEquals (aExec.transform (new XsltTransformRequest ()).principalSerialized (), aResult.principalSerialized ());

    // Without instructions only the templates
    final RecordingTrace aTemplatesOnly = new RecordingTrace (false);
    aExec.transform (new XsltTransformRequest ().setTraceListener (aTemplatesOnly));
    assertEquals (aTrace.templateEvents (), aTemplatesOnly.m_aEvents);
  }

  @Test
  public void testInstructionsSameResult ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (RULES, new XsltCompileOptions ());
    final RecordingTrace aTrace = new RecordingTrace (true);
    final XsltTransformResult aResult = aExec.transform (_sourceRequest ().setTraceListener (aTrace));
    aTrace.assertBalanced ();
    assertEquals (aExec.transform (_sourceRequest ()).principalSerialized (), aResult.principalSerialized ());
    final RecordingTrace aTemplatesOnly = new RecordingTrace (false);
    aExec.transform (_sourceRequest ().setTraceListener (aTemplatesOnly));
    assertEquals (aTemplatesOnly.m_aEvents, aTrace.templateEvents ());
    assertTrue (aTrace.m_aEvents.contains ("+i try"));
    assertTrue (aTrace.m_aEvents.contains ("+i next-match"));
  }

  @Test
  public void testFnTransform ()
  {
    final String sInner = SHEET + IT + "<xsl:call-template name='x'/>" + TE + "<xsl:template name='x'>inner</xsl:template>" + END;
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       "<xsl:param name='inner'/>" +
                                                       IT +
                                                       "<xsl:sequence select=\"transform(map{'stylesheet-text': $inner})?output\"/>" +
                                                       TE +
                                                       END,
                                                       new XsltCompileOptions ());
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.params ().put (new XdmQName ("inner"), XdmSequence.of (XdmAtomic.string (sInner)));
    final RecordingTrace aTrace = new RecordingTrace (false);
    aExec.transform (aRequest.setTraceListener (aTrace));
    // The nested run reports to the same listener, inside the caller
    assertEquals (new CommonsArrayList <> ("+transform",
                                           "+call initial-template",
                                           "+transform",
                                           "+call initial-template",
                                           "+call x",
                                           "-call x",
                                           "-call initial-template",
                                           "-transform",
                                           "-call initial-template",
                                           "-transform"),
                  aTrace.m_aEvents);
  }

  @Test
  public void testWriterPath ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (RULES.replace ("\n<xsl:function", "<xsl:output method='xml'/>\n<xsl:function"),
                                                       new XsltCompileOptions ());
    final RecordingTrace aBuffered = new RecordingTrace (true);
    final String sBuffered = aExec.transform (_sourceRequest ().setTraceListener (aBuffered)).principalSerialized ();
    final RecordingTrace aDrained = new RecordingTrace (true);
    final long nDrained = ResultDrain.getDrainedRunCount ();
    final StringWriter aSW = new StringWriter ();
    aExec.transform (_sourceRequest ().setTraceListener (aDrained), aSW);
    assertEquals (nDrained + 1, ResultDrain.getDrainedRunCount ());
    assertEquals (sBuffered, aSW.toString ());
    aDrained.assertBalanced ();
    assertEquals (aBuffered.m_aEvents, aDrained.m_aEvents);
  }

  @Test
  public void testListenerException ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (RULES, new XsltCompileOptions ());
    try
    {
      aExec.transform (_sourceRequest ().setTraceListener (new IXsltTraceListener ()
      {
        public void onFunctionEnter (@NonNull final XsltTraceFunction aFunction)
        {
          throw new IllegalStateException ("listener " + aFunction.name ().localName ());
        }
      }));
      fail ();
    }
    catch (final IllegalStateException ex)
    {
      assertEquals ("listener f", ex.getMessage ());
    }
  }
}
