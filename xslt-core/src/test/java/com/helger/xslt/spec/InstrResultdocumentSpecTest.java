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
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunIn;
import static com.helger.xslt.spec.XsltSpecTestHelper.specSheet;
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt's <code>internal/xslt/instr_resultdocument_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrResultdocumentSpecTest
{
  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * @param aResult
   *        Transformation result.
   * @return The secondary result documents by href.
   */
  @NonNull
  private static ICommonsOrderedMap <String, String> _secondary (@NonNull final XsltTransformResult aResult)
  {
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    for (final XsltTransformResult.SecondaryResult aSec : aResult.secondaryResults ())
      ret.put (aSec.href (), aSec.serialized ());
    return ret;
  }

  /**
   * XSLT 3.0 §25.1 xsl:result-document and its serialization attributes (§26 /
   * Serialization 3.1).
   */
  @Test
  public void instrSpecResultDocumentSerialization ()
  {
    final String [] [] aCases = { { "named output definition", """
        <xsl:output name="f:t" method="text"/>
        <xsl:template match="/"><xsl:result-document href="a.txt" format="f:t"><e>x</e></xsl:result-document></xsl:template>""", "a.txt", "x" },
                                  { "format as AVT", """
                                      <xsl:output name="t" method="text"/>
                                      <xsl:template match="/"><xsl:result-document href="a.txt" format="{'t'}"><e>y</e></xsl:result-document></xsl:template>""", "a.txt", "y" },
                                  { "method attribute",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"text\"><e>z</e></xsl:result-document></xsl:template>",
                                    "a",
                                    "z" },
                                  { "method AVT",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"{'text'}\"><e>z</e></xsl:result-document></xsl:template>",
                                    "a",
                                    "z" },
                                  { "xml with omitted declaration",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"xml\" omit-xml-declaration=\"yes\"><e/></xsl:result-document></xsl:template>",
                                    "a",
                                    "<e/>" },
                                  { "xml with declaration and standalone",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"xml\" standalone=\"yes\"><e/></xsl:result-document></xsl:template>",
                                    "a",
                                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><e/>" },
                                  { "doctype",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"xml\" omit-xml-declaration=\"yes\" doctype-system=\"e.dtd\"><e/></xsl:result-document></xsl:template>",
                                    "a",
                                    "<!DOCTYPE e SYSTEM \"e.dtd\"><e/>" },
                                  { "cdata-section-elements",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"xml\" omit-xml-declaration=\"yes\" cdata-section-elements=\"e\"><e>a&lt;b</e></xsl:result-document></xsl:template>",
                                    "a",
                                    "<e><![CDATA[a<b]]></e>" },
                                  { "item-separator",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"text\" item-separator=\"|\"><xsl:sequence select=\"1, 2, 3\"/></xsl:result-document></xsl:template>",
                                    "a",
                                    "1|2|3" },
                                  { "json method",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a.json\" method=\"json\"><xsl:sequence select=\"map{'a': 1}\"/></xsl:result-document></xsl:template>",
                                    "a.json",
                                    "{\"a\":1}" },
                                  { "adaptive method",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a\" method=\"adaptive\"><xsl:sequence select=\"'s'\"/></xsl:result-document></xsl:template>",
                                    "a",
                                    "\"s\"" },
                                  { "character map", """
                                      <xsl:character-map name="m"><xsl:output-character character="x" string="[X]"/></xsl:character-map>
                                      <xsl:template match="/"><xsl:result-document href="a" method="text" use-character-maps="m">axb</xsl:result-document></xsl:template>""", "a", "a[X]b" },
                                  { "html method",
                                    "<xsl:template match=\"/\"><xsl:result-document href=\"a.html\" method=\"html\" include-content-type=\"no\"><html><body><br/></body></html></xsl:result-document></xsl:template>",
                                    "a.html",
                                    "<html><body><br></body></html>" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final ICommonsOrderedMap <String, String> aSec;
      try
      {
        aSec = _secondary (specRunIn (null, aCase[1], "<doc/>"));
      }
      catch (final XPathException ex)
      {
        aErrors.add (aCase[0] + ": " + ex.getCode () + " " + ex.getMessage ());
        continue;
      }
      final String sGot = aSec.get (aCase[2]);
      if (sGot == null)
        aErrors.add (aCase[0] + ": no secondary result '" + aCase[2] + "' (have " + aSec + ")");
      else
        // XSLT 3.0 §26.1: indent defaults to yes for the html method, and the
        // whitespace it adds is implementation-dependent, so whitespace between
        // tags is ignored (go-xslt does not indent)
        if (!sGot.strip ().replaceAll (">\\s+<", "><").equals (aCase[3]))
          aErrors.add (aCase[0] + ":\n got  '" + sGot + "'\n want '" + aCase[3] + "'");
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  @Test
  public void instrSpecResultDocumentPrincipal ()
  {
    // An absent href targets the principal result, with the instruction's
    // own serialization attributes.
    XsltTransformResult aResult = specRunIn (null,
                                             "<xsl:template match=\"/\"><xsl:result-document method=\"text\" item-separator=\"-\"><xsl:sequence select=\"1, 2\"/></xsl:result-document></xsl:template>",
                                             "<doc/>");
    assertEquals ("1-2", aResult.principalSerialized ());
    assertTrue (aResult.secondaryResults ().isEmpty ());
    // Secondary documents leave the principal output alone.
    aResult = specRunIn (null,
                         "<xsl:template match=\"/\">main<xsl:result-document href=\"one.txt\" method=\"text\">1</xsl:result-document><xsl:result-document href=\"two.txt\" method=\"text\">2</xsl:result-document></xsl:template>",
                         "<doc/>");
    assertEquals ("main", aResult.principalSerialized ());
    final ICommonsOrderedMap <String, String> aSec = _secondary (aResult);
    assertEquals ("1", aSec.get ("one.txt"));
    assertEquals ("2", aSec.get ("two.txt"));
  }

  @Test
  public void instrSpecResultDocumentErrors ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "in.xml", "<in/>");
    specRunAll (aDir,
                // XTDE1490: two final result trees with the same URI.
                err ("same href twice", """
                    <xsl:template match="/"><xsl:result-document href="a.txt">1</xsl:result-document><xsl:result-document href="a.txt">2</xsl:result-document></xsl:template>""",
                     null,
                     "XTDE1490"),
                err ("empty href after principal content", """
                    <xsl:template match="/">x<xsl:result-document>1</xsl:result-document></xsl:template>""", null, "XTDE1490"),
                // XTDE1480: evaluated in temporary output state.
                err ("inside a variable", """
                    <xsl:template match="/"><xsl:variable name="v"><xsl:result-document href="a.txt">1</xsl:result-document></xsl:variable><xsl:value-of select="$v"/></xsl:template>""",
                     null,
                     "XTDE1480"),
                // XTDE1460: @format does not name an output definition.
                err ("unknown format", """
                    <xsl:template match="/"><xsl:result-document href="a" format="{'nope'}">1</xsl:result-document></xsl:template>""",
                     null,
                     "XTDE1460"),
                err ("format with unbound prefix", """
                    <xsl:template match="/"><xsl:result-document href="a" format="{'zz:o'}">1</xsl:result-document></xsl:template>""",
                     null,
                     "XTDE1460"),
                // SEPM0016: invalid serialization parameter values.
                err ("bad boolean", """
                    <xsl:template match="/"><xsl:result-document href="a" indent="{'maybe'}">1</xsl:result-document></xsl:template>""",
                     null,
                     "SEPM0016"),
                err ("bad standalone", """
                    <xsl:template match="/"><xsl:result-document href="a" standalone="{'perhaps'}">1</xsl:result-document></xsl:template>""",
                     null,
                     "SEPM0016"),
                // go-xslt expects SEPM0016 for the next two, but §2.4 (XTSE0020)
                // makes an invalid value written without curly brackets a static
                // error
                err ("bad escape-uri-attributes", """
                    <xsl:template match="/"><xsl:result-document href="a" escape-uri-attributes="y e s">1</xsl:result-document></xsl:template>""",
                     null,
                     "XTSE0020"),
                err ("bad include-content-type", """
                    <xsl:template match="/"><xsl:result-document href="a" include-content-type="maybe">1</xsl:result-document></xsl:template>""",
                     null,
                     "XTSE0020"),
                ok ("standalone omit is valid", """
                    <xsl:template match="/"><xsl:result-document href="a" standalone="omit">1</xsl:result-document></xsl:template>""",
                    null,
                    ""));

    // XTDE1500: writing a resource read in the same transformation. The href
    // is resolved against the base output URI (§25.1), which is
    // implementation-defined unless the caller supplies one, so supply the
    // directory of in.xml.
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ());
    final XsltExecutable aExec = XsltCompiler.compile (specSheet ("""
        <xsl:template match="/"><xsl:value-of select="count(doc('in.xml'))"/><xsl:result-document href="in.xml">1</xsl:result-document></xsl:template>"""),
                                                       aOptions);
    try
    {
      aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse ("<doc/>"))
                                                  .setResolver (FileResourceResolver.INSTANCE)
                                                  .setBaseOutputUri (aDir.resolve ("out.xml").toUri ().toString ()));
      fail ("write after read: want XTDE1500");
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTDE1500", ex.getCode ());
    }
  }
}
