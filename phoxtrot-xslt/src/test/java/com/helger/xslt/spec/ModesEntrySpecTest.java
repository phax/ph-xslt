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
package com.helger.xslt.spec;

import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;
import static com.helger.xslt.spec.XsltSpecTestHelper.specSheet;
import static com.helger.xslt.spec.XsltSpecTestHelper.specXml;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt's <code>internal/xslt/modes_entry_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ModesEntrySpecTest
{
  /**
   * XSLT 3.0 §2.3.3 initial modes named by the host, and §6.6 mode attributes
   * that do not change the result.
   */
  @Test
  public void modeSpecInitialModes ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (specSheet ("""
        <xsl:mode name="declared" typed="false" warning-on-no-match="yes" warning-on-multiple-match="no"/>
        <xsl:template match="/" mode="declared">declared</xsl:template>
        <xsl:template match="/" mode="f:onlytemplate">only-template</xsl:template>
        <xsl:template match="/" mode="a b">list</xsl:template>
        <xsl:template match="/">unnamed</xsl:template>"""), new XsltCompileOptions ());
    final Object [] [] aCases = { { new XdmQName ("declared"), "declared", null },
                                  // A mode named only by a template's @mode is a
                                  // known mode.
                                  { new XdmQName ("urn:f", "onlytemplate"), "only-template", null },
                                  { new XdmQName ("b"), "list", null },
                                  { new XdmQName ("#default"), "unnamed", null },
                                  { new XdmQName ("missing"), null, "XTDE0045" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Object [] aCase : aCases)
    {
      final XdmQName aMode = (XdmQName) aCase[0];
      try
      {
        final String sOut = aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<doc/>"))
                                                                        .setInitialMode (aMode))
                                 .principalSerialized ();
        if (aCase[2] != null)
          aErrors.add (aMode + ": want " + aCase[2] + ", got output '" + sOut + "'");
        else
          if (!sOut.equals (aCase[1]))
            aErrors.add (aMode + ": got '" + sOut + "' want '" + aCase[1] + "'");
      }
      catch (final XPathException ex)
      {
        if (aCase[2] == null || !aCase[2].equals (ex.getCode ()))
          aErrors.add (aMode + ": want " + aCase[2] + ", got " + ex.getCode () + ": " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));

    specRunAll (err ("typed=yes spelled true", """
        <xsl:mode name="t" typed="true"/>
        <xsl:template match="/"><xsl:apply-templates select="*" mode="t"/></xsl:template>
        <xsl:template match="*" mode="t">x</xsl:template>""", null, "XTTE3100"),
                // typed="unspecified" (the default) accepts any node.
                ok ("typed unspecified", """
                    <xsl:mode name="t" typed="unspecified"/>
                    <xsl:template match="/"><xsl:apply-templates select="*" mode="t"/></xsl:template>
                    <xsl:template match="*" mode="t">x</xsl:template>""", null, "x"),
                ok ("mode-specific on-no-match", specXml ("""
                    <xsl:mode name="copy" on-no-match="shallow-copy"/>
                    <xsl:template match="/"><R><xsl:apply-templates select="r/node()" mode="copy"/></R></xsl:template>"""),
                    "<r><a>t</a></r>",
                    "<R><a>t</a></R>"));
  }
}
