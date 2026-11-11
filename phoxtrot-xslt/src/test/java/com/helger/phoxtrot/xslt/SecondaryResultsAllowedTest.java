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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.phoxtrot.xpath.XPathException;

/**
 * Test class for {@link XsltCompileOptions#setSecondaryResultsAllowed(boolean)}:
 * without secondary results every <code>xsl:result-document</code> with an
 * <code>href</code> attribute is the static error XTSE0010, as in Saxon under
 * secure processing.
 *
 * @author Philip Helger
 */
public final class SecondaryResultsAllowedTest
{
  private static final String XSL_START = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>";
  private static final String XSL_END = "</xsl:stylesheet>";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  @NonNull
  private static String _sheet (@NonNull final String sBody)
  {
    return XSL_START + "<xsl:template match='/'>" + sBody + "</xsl:template>" + XSL_END;
  }

  /** @return The error code of compiling, <code>null</code> if it compiles. */
  @Nullable
  private static String _compileCode (@NonNull final String sSheet, final boolean bAllowed)
  {
    try
    {
      XsltCompiler.compile (sSheet, new XsltCompileOptions ().setSecondaryResultsAllowed (bAllowed));
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  @Test
  public void testDefaults ()
  {
    assertTrue (XsltCompileOptions.DEFAULT_SECONDARY_RESULTS_ALLOWED);
    assertTrue (new XsltCompileOptions ().isSecondaryResultsAllowed ());
    assertFalse (new XsltCompileOptions ().setSecondaryResultsAllowed (false).isSecondaryResultsAllowed ());
  }

  @Test
  public void testStaticCheck ()
  {
    // Rejected for any href, whether or not the instruction is reached
    final String [] aRejected = { _sheet ("<xsl:result-document href='secondary.xml'><a/></xsl:result-document>"),
                                  _sheet ("<xsl:result-document href=''><a/></xsl:result-document>"),
                                  _sheet ("<xsl:result-document href=\"{'x' || '.xml'}\"><a/></xsl:result-document>"),
                                  XSL_START +
                                                                                                         "<xsl:template match='/'><a/></xsl:template>" +
                                                                                                         "<xsl:template name='never'><xsl:result-document href='n.xml'><a/></xsl:result-document></xsl:template>" +
                                                                                                         XSL_END,
                                  _sheet ("<xsl:if test='false()'><xsl:result-document href='f.xml'><a/></xsl:result-document></xsl:if>") };
    for (final String sSheet : aRejected)
    {
      assertEquals (sSheet, "XTSE0010", _compileCode (sSheet, false));
      assertEquals (sSheet, null, _compileCode (sSheet, true));
    }
    // Allowed without href or when excluded by use-when
    final String [] aAllowed = { _sheet ("<xsl:result-document use-when='false()' href='u.xml'><a/></xsl:result-document>"),
                                 _sheet ("<xsl:result-document><a/></xsl:result-document>"),
                                 _sheet ("<xsl:result-document format='f'><a/></xsl:result-document>").replace ("<xsl:template",
                                                                                                                  "<xsl:output name='f' method='xml'/><xsl:template") };
    for (final String sSheet : aAllowed)
    {
      assertEquals (sSheet, null, _compileCode (sSheet, false));
      assertEquals (sSheet, null, _compileCode (sSheet, true));
    }
    // Located at the element
    try
    {
      XsltCompiler.compile (aRejected[0], new XsltCompileOptions ().setSecondaryResultsAllowed (false));
      throw new IllegalStateException ("No compile error");
    }
    catch (final XPathException ex)
    {
      assertTrue (ex instanceof final XsltException aXsltEx && aXsltEx.isLocated () && aXsltEx.getLine () > 0);
    }
  }

  @Test
  public void testIncludedModule () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("inc.xsl"),
                       XSL_START + "<xsl:template name='t'><xsl:result-document href='x.xml'><a/></xsl:result-document></xsl:template>" + XSL_END);
    final Path aMain = aDir.resolve ("main.xsl");
    Files.writeString (aMain, XSL_START + "<xsl:include href='inc.xsl'/><xsl:template match='/'><a/></xsl:template>" + XSL_END);
    final String sMain = Files.readString (aMain);
    final String sBase = aMain.toUri ().toString ();
    assertNotNull (XsltCompiler.compile (sMain, new XsltCompileOptions ().setBaseUri (sBase)));
    try
    {
      XsltCompiler.compile (sMain, new XsltCompileOptions ().setBaseUri (sBase).setSecondaryResultsAllowed (false));
      throw new IllegalStateException ("No compile error");
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTSE0010", ex.getCode ());
    }
  }

  @Test
  public void testTransformInherits ()
  {
    final String sInner = XSL_START +
                          "<xsl:template name='main'><xsl:result-document href='x.xml'><a/></xsl:result-document><out/></xsl:template>" +
                          XSL_END;
    final String sVar = "<xsl:variable name='t'><![CDATA[" + sInner + "]]></xsl:variable>";
    final String sCall = "transform(map{'stylesheet-text': string($t), 'initial-template': QName('', 'main')," +
                         " 'delivery-format': 'serialized'})?output";
    final String sOuter = XSL_START +
                          "<xsl:output method='text'/><xsl:template match='/'>" +
                          sVar +
                          "<xsl:value-of select=\"" +
                          sCall +
                          "\"/></xsl:template>" +
                          XSL_END;
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<x/>"));
    assertTrue (XsltCompiler.compile (sOuter, new XsltCompileOptions ())
                            .transform (aRequest)
                            .principalSerialized ()
                            .endsWith ("<out/>"));
    try
    {
      XsltCompiler.compile (sOuter, new XsltCompileOptions ().setSecondaryResultsAllowed (false))
                  .transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<x/>")));
      throw new IllegalStateException ("No error");
    }
    catch (final XPathException ex)
    {
      // The static error of the inner stylesheet
      assertEquals ("XTSE0010", ex.getCode ());
    }

    // Also from a static expression of the outer stylesheet
    final String sStatic = XSL_START +
                           "<xsl:param name='s' static='yes' select=\"" +
                           "transform(map{'stylesheet-text': '" +
                           sInner.replace ("<", "&lt;").replace ("'", "&quot;") +
                           "', 'initial-template': QName('', 'main'), 'delivery-format': 'serialized'})?output\"/>" +
                           "<xsl:template match='/'><a/></xsl:template>" +
                           XSL_END;
    assertEquals (null, _compileCode (sStatic, true));
    assertEquals ("XTSE0010", _compileCode (sStatic, false));
  }
}
