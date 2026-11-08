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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Test class for the streaming claim
 * ({@link XsltCompileOptions#setStreamingClaim(boolean)}), the binding
 * streamability option and the streamed-source flag of the request.
 *
 * @author Philip Helger
 */
public final class StreamingClaimTest
{
  private static final String SHEET = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                                      "<xsl:output method='text'/>" +
                                      "<xsl:template name='xsl:initial-template'>" +
                                      "<xsl:value-of select=\"system-property('xsl:supports-streaming')\"/>" +
                                      "<xsl:text use-when=\"system-property('xsl:supports-streaming') = 'yes'\">+</xsl:text>" +
                                      "</xsl:template>" +
                                      "</xsl:stylesheet>";

  @NonNull
  private static String _run (@NonNull final XsltCompileOptions aOptions)
  {
    return XsltCompiler.compile (SHEET, aOptions).transform (new XsltTransformRequest ()).principalSerialized ();
  }

  @Test
  public void testDefaults ()
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    assertFalse (aOptions.isStreamingClaim ());
    assertFalse (aOptions.isEnforceStreamability ());
    assertFalse (new XsltTransformRequest ().isSourceStreamed ());
  }

  @Test
  public void testSystemProperty ()
  {
    // At run time and in static expressions
    assertEquals ("no", _run (new XsltCompileOptions ()));
    assertEquals ("yes+", _run (new XsltCompileOptions ().setStreamingClaim (true)));
    // Binding streamability declarations alone claim nothing
    assertEquals ("no", _run (new XsltCompileOptions ().setEnforceStreamability (true)));
  }

  @Test
  public void testSourceStreamed ()
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setSourceStreamed (true);
    assertTrue (aRequest.isSourceStreamed ());
    // Recorded only: the transformation runs as before
    assertEquals ("no",
                  XsltCompiler.compile (SHEET, new XsltCompileOptions ()).transform (aRequest).principalSerialized ());
  }
}
