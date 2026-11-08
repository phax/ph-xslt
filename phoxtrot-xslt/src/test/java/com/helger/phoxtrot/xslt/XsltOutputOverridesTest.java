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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.StringWriter;

import org.junit.Test;

import com.helger.phoxtrot.xslt.runtime.ResultDrain;

/**
 * Test class for {@link XsltTransformRequest#outputOverrides()}: serialization
 * parameters supplied by the API override the output definition of the
 * principal result.
 *
 * @author Philip Helger
 */
public final class XsltOutputOverridesTest
{
  private static final String SHEET = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>";
  private static final String END = "</xsl:stylesheet>";
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";

  @Test
  public void testOverridesApplyToBothPaths ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
                                                       IT +
                                                       "<r><e>a&lt;b</e></r>" +
                                                       TE +
                                                       END,
                                                       new XsltCompileOptions ());
    assertEquals ("<r><e>a&lt;b</e></r>", aExec.transform (new XsltTransformRequest ()).principalSerialized ());

    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.outputOverrides ().put ("cdata-section-elements", "e");
    aRequest.outputOverrides ().put ("omit-xml-declaration", "no");
    final String sExpected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><r><e><![CDATA[a<b]]></e></r>";
    assertEquals (sExpected, aExec.transform (aRequest).principalSerialized ());

    final StringWriter aSW = new StringWriter ();
    aExec.transform (aRequest, aSW);
    assertEquals (sExpected, aSW.toString ());

    // The method is overridden as well, also for the decision to write while
    // building
    final XsltTransformRequest aText = new XsltTransformRequest ();
    aText.outputOverrides ().put ("method", "text");
    final long nDrained = ResultDrain.getDrainedRunCount ();
    final StringWriter aSW2 = new StringWriter ();
    final XsltTransformResult aResult = aExec.transform (aText, aSW2);
    assertEquals ("a<b", aSW2.toString ());
    assertEquals ("text", aResult.principalMethod ());
    assertEquals (nDrained + 1, ResultDrain.getDrainedRunCount ());
  }

  @Test
  public void testExpandedNames ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET +
                                                       "<xsl:output omit-xml-declaration='yes'/>" +
                                                       IT +
                                                       "<r xmlns='urn:x'><e>1</e></r>" +
                                                       TE +
                                                       END,
                                                       new XsltCompileOptions ());
    for (final String sName : new String [] { "{urn:x}e", "Q{urn:x}e" })
    {
      final XsltTransformRequest aRequest = new XsltTransformRequest ();
      aRequest.outputOverrides ().put ("cdata-section-elements", sName);
      assertEquals ("<r xmlns=\"urn:x\"><e><![CDATA[1]]></e></r>", aExec.transform (aRequest).principalSerialized ());
    }
  }

  @Test
  public void testInvalidOverride ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (SHEET + IT + "<r/>" + TE + END, new XsltCompileOptions ());
    for (final String sName : new String [] { "unknown", "use-character-maps" })
    {
      final XsltTransformRequest aRequest = new XsltTransformRequest ();
      aRequest.outputOverrides ().put (sName, "x");
      try
      {
        aExec.transform (aRequest);
        fail (sName);
      }
      catch (final XsltException ex)
      {
        assertEquals ("SEPM0016", ex.getCode ());
      }
    }
  }
}
