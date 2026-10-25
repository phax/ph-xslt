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

import static com.helger.xslt.spec.XsltSpecTestHelper.SPEC_NS;
import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunIn;
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static com.helger.xslt.spec.XsltSpecTestHelper.specXml;
import static org.junit.Assert.fail;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;

/**
 * Port of go-xslt's <code>internal/xslt/instr_message_more_test.go</code>:
 * XSLT 3.0 §23.1 xsl:message content (constructed like the content of a
 * document node), §8.4.3 xsl:where-populated inside element content, and §6.8
 * xsl:next-match / xsl:apply-imports with parameters.
 *
 * @author Philip Helger
 */
public final class InstrMessageMoreSpecTest
{
  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  @Test
  public void instrSpecMessageContent ()
  {
    final String [] [] aCases = { { "element content is serialized", "<xsl:message><a x=\"1\"/></xsl:message>", "", "<a x=\"1\"/>" },
                                  // go-xslt also expects "v" for <xsl:message select="/r/@a"/>,
                                  // but §23.1 makes the message content after a
                                  // free-standing attribute implementation-dependent
                                  // (its note names exactly this case), so that case is
                                  // only checked not to fail the transformation, below
                                  { "select and body together", "<xsl:message select=\"'s'\">b</xsl:message>", "", "sb" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final String sSrc = aCase[2].isEmpty () ? "<doc/>" : aCase[2];
      try
      {
        final ICommonsList <String> aMessages = specRunIn (null,
                                                           "<xsl:template match=\"/\">" + aCase[1] + "</xsl:template>",
                                                           sSrc).messages ();
        if (aMessages.size () != 1 || !aMessages.getFirstOrNull ().equals (aCase[3]))
          aErrors.add (aCase[0] + ": messages " + aMessages + ", want [" + aCase[3] + "]");
      }
      catch (final XPathException ex)
      {
        aErrors.add (aCase[0] + ": " + ex.getCode () + " " + ex.getMessage ());
      }
    }
    try
    {
      if (specRunIn (null, "<xsl:template match=\"/\"><xsl:message select=\"/r/@a\"/></xsl:template>", "<r a=\"v\"/>").messages ()
                                                                                                                   .size () > 1)
        aErrors.add ("attribute node: more than one message");
    }
    catch (final XPathException ex)
    {
      aErrors.add ("attribute node: " + ex.getCode () + " " + ex.getMessage ());
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));

    specRunAll (ok ("terminating message value in the catch",
                    "<xsl:template match=\"/\"><xsl:try><xsl:message terminate=\"yes\"><m>x</m></xsl:message><xsl:catch xmlns:err=\"http://www.w3.org/2005/xqt-errors\"><xsl:value-of select=\"string-join($err:value ! string())\"/></xsl:catch></xsl:try></xsl:template>",
                    null,
                    "x"),
                ok ("where-populated keeps a populated document",
                    specXml ("<xsl:template match=\"/\"><e><xsl:where-populated><xsl:document><n/></xsl:document></xsl:where-populated></e></xsl:template>"),
                    null,
                    "<e><n/></e>"),
                ok ("where-populated joins surviving atomic values",
                    specXml ("<xsl:template match=\"/\"><e><xsl:where-populated><xsl:sequence select=\"'a', '', 'b'\"/></xsl:where-populated></e></xsl:template>"),
                    null,
                    "<e>a b</e>"));
  }

  @Test
  public void applyImportsSpecParams ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir,
               "base.xsl",
               "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                     "<xsl:template match=\"a\"><xsl:param name=\"p\" select=\"'none'\"/><xsl:param name=\"t\" tunnel=\"yes\" select=\"'none'\"/>base(<xsl:value-of select=\"$p, $t\"/>)</xsl:template>\n" +
                     "</xsl:stylesheet>");
    specRunAll (aDir,
                ok ("apply-imports with parameters",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                      SPEC_NS +
                                                      "><xsl:import href=\"base.xsl\"/><xsl:output method=\"text\"/>\n" +
                                                      "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/a\"/></xsl:template>\n" +
                                                      "<xsl:template match=\"a\">main[<xsl:apply-imports><xsl:with-param name=\"p\" select=\"'P'\"/><xsl:with-param name=\"t\" select=\"'T'\" tunnel=\"yes\"/></xsl:apply-imports>]</xsl:template></xsl:stylesheet>",
                    "<r><a/></r>",
                    "main[base(P T)]"),
                ok ("next-match with parameters",
                    "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/a\"/></xsl:template>\n" +
                                                   "<xsl:template match=\"a\" priority=\"2\">1[<xsl:next-match><xsl:with-param name=\"p\" select=\"'P'\"/></xsl:next-match>]</xsl:template>\n" +
                                                   "<xsl:template match=\"a\" priority=\"1\"><xsl:param name=\"p\"/>2(<xsl:value-of select=\"$p\"/>)</xsl:template>",
                    "<r><a/></r>",
                    "1[2(P)]"),
                ok ("next-match falls back to the built-in rule",
                    "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/a\"/></xsl:template>\n" +
                                                                   "<xsl:template match=\"a\">[<xsl:next-match/>]</xsl:template>",
                    "<r><a>text</a></r>",
                    "[text]"),
                ok ("apply-imports without imports uses the built-in rule",
                    "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/a\"/></xsl:template>\n" +
                                                                             "<xsl:template match=\"a\">(<xsl:apply-imports/>)</xsl:template>",
                    "<r><a>t</a></r>",
                    "(t)"),
                // XTDE0560: apply-imports / next-match with no current template
                // rule.
                err ("apply-imports inside for-each",
                     "<xsl:template match=\"/\"><xsl:for-each select=\"r\"><xsl:apply-imports/></xsl:for-each></xsl:template>",
                     "<r><a/></r>",
                     "XTDE0560"));
  }

  /**
   * §6.8: the current template rule is cleared inside xsl:function, so
   * xsl:next-match there is XTDE0560.
   */
  @Test
  public void applyImportsSpecNextMatchInFunction ()
  {
    specRunAll (err ("next-match in a function",
                     "<xsl:function name=\"f:n\"><xsl:next-match/></xsl:function>\n" +
                                                 "<xsl:template match=\"/\"><xsl:sequence select=\"f:n()\"/></xsl:template>",
                     null,
                     "XTDE0560"));
  }
}
