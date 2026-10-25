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
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Port of go-xslt's <code>internal/xslt/output_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class OutputSpecTest
{
  private static final String CHARMAPS = "<xsl:stylesheet version=\"3.0\" " +
                                         SPEC_NS +
                                         ">\n" +
                                         "<xsl:output method=\"text\" use-character-maps=\"m\"/>\n" +
                                         "<xsl:character-map name=\"m\" use-character-maps=\"m2\"><xsl:output-character character=\"x\" string=\"[X]\"/></xsl:character-map>\n" +
                                         "<xsl:character-map name=\"m2\"><xsl:output-character character=\"y\" string=\"Y\"/></xsl:character-map>\n";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * A stylesheet with the given xsl:output attributes and template body.
   *
   * @param sOutput
   *        xsl:output attributes.
   * @param sBody
   *        Template body.
   * @return The stylesheet.
   */
  @NonNull
  private static String _outSheet (@NonNull final String sOutput, @NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"3.0\" " +
           SPEC_NS +
           ">\n<xsl:output " +
           sOutput +
           "/>\n<xsl:template match=\"/\">" +
           sBody +
           "</xsl:template>\n</xsl:stylesheet>";
  }

  /**
   * XSLT 3.0 §26 xsl:output and Serialization 3.1: serialization parameters and
   * the serialization errors (SEPM*, SESU*, SERE*).
   */
  @Test
  public void outputSpecSerialization ()
  {
    specRunAll (ok ("xml declaration", _outSheet ("method=\"xml\"", "<e/>"), null, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e/>"),
                ok ("standalone",
                    _outSheet ("method=\"xml\" standalone=\"no\"", "<e/>"),
                    null,
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><e/>"),
                ok ("doctype",
                    _outSheet ("method=\"xml\" omit-xml-declaration=\"yes\" doctype-public=\"-//X//EN\" doctype-system=\"e.dtd\"", "<e/>"),
                    null,
                    "<!DOCTYPE e PUBLIC \"-//X//EN\" \"e.dtd\"><e/>"),
                ok ("cdata-section-elements",
                    _outSheet ("method=\"xml\" omit-xml-declaration=\"yes\" cdata-section-elements=\"e\"", "<e>&lt;x&gt;</e>"),
                    null,
                    "<e><![CDATA[<x>]]></e>"),
                ok ("item-separator", _outSheet ("method=\"text\" item-separator=\";\"", "<xsl:sequence select=\"1 to 3\"/>"), null, "1;2;3"),
                ok ("json output", _outSheet ("method=\"json\"", "<xsl:sequence select=\"[1, 'a', true()]\"/>"), null, "[1,\"a\",true]"),
                ok ("adaptive output", _outSheet ("method=\"adaptive\"", "<xsl:sequence select=\"'x'\"/>"), null, "\"x\""),
                ok ("html void element", _outSheet ("method=\"html\"", "<html><body><br/></body></html>"), null, "~<br>"),
                ok ("html5 doctype", _outSheet ("method=\"html\" html-version=\"5\"", "<html><body/></html>"), null, "~<!DOCTYPE html>"),
                ok ("xhtml method",
                    _outSheet ("method=\"xhtml\" omit-xml-declaration=\"yes\"",
                               "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><br/></body></html>"),
                    null,
                    "~<br />"),
                ok ("text method", _outSheet ("method=\"text\"", "<e>a&lt;b</e>"), null, "a<b"),
                ok ("indent", _outSheet ("method=\"xml\" omit-xml-declaration=\"yes\" indent=\"yes\"", "<e><f/></e>"), null, "~<e>\n"),
                ok ("character map", CHARMAPS + "<xsl:template match=\"/\">axb</xsl:template></xsl:stylesheet>", null, "a[X]b"),
                ok ("character map from a referenced map",
                    CHARMAPS + "<xsl:template match=\"/\">ayb</xsl:template></xsl:stylesheet>",
                    null,
                    "aYb"),
                // XTSE1590: use-character-maps names an undeclared map.
                err ("undeclared character map", _outSheet ("method=\"text\" use-character-maps=\"nope\"", "x"), null, "XTSE1590"),
                // XTSE1600: a character map that references itself.
                err ("circular character maps",
                     "<xsl:stylesheet version=\"3.0\" " +
                                                SPEC_NS +
                                                ">\n" +
                                                "<xsl:character-map name=\"a\" use-character-maps=\"b\"/><xsl:character-map name=\"b\" use-character-maps=\"a\"/>\n" +
                                                "<xsl:template match=\"/\">x</xsl:template></xsl:stylesheet>",
                     null,
                     "XTSE1600"),
                // XTSE1580: two character maps of the same name and precedence.
                err ("duplicate character map",
                     "<xsl:stylesheet version=\"3.0\" " +
                                                SPEC_NS +
                                                ">\n" +
                                                "<xsl:character-map name=\"a\"/><xsl:character-map name=\"a\"/>\n" +
                                                "<xsl:template match=\"/\">x</xsl:template></xsl:stylesheet>",
                     null,
                     "XTSE1580"),
                // SEPM0004: doctype-system with more than one top-level element.
                err ("doctype with two top-level elements", _outSheet ("method=\"xml\" doctype-system=\"e.dtd\"", "<a/><b/>"), null, "SEPM0004"),
                // SEPM0009: omit-xml-declaration with standalone.
                err ("omitted declaration with standalone",
                     _outSheet ("method=\"xml\" omit-xml-declaration=\"yes\" standalone=\"yes\"", "<e/>"),
                     null,
                     "SEPM0009"),
                // SEPM0010: undeclare-prefixes with XML 1.0.
                err ("undeclare-prefixes with XML 1.0", _outSheet ("method=\"xml\" undeclare-prefixes=\"yes\"", "<e/>"), null, "SEPM0010"),
                // SESU0007: unsupported encoding.
                err ("unsupported encoding", _outSheet ("method=\"text\" encoding=\"x-no-such-encoding\"", "x"), null, "SESU0007"),
                // SESU0011: unsupported normalization form.
                err ("unsupported normalization-form", _outSheet ("method=\"xml\" normalization-form=\"NFQ\"", "<e/>"), null, "SESU0011"),
                ok ("supported normalization-form", _outSheet ("method=\"text\" normalization-form=\"NFC\"", "é"), null, "é"),
                // SERE0015: ">" in a processing instruction with the html method.
                err ("html PI with >",
                     _outSheet ("method=\"html\"", "<html><xsl:processing-instruction name=\"p\">a>b</xsl:processing-instruction></html>"),
                     null,
                     "SERE0015"),
                // SERE0014: a C1 control character with HTML before version 5.
                err ("html4 C1 character",
                     _outSheet ("method=\"html\" version=\"4.01\"", "<html><xsl:value-of select=\"codepoints-to-string(150)\"/></html>"),
                     null,
                     "SERE0014"),
                // SESU0013: an unsupported html version.
                err ("unsupported html version", _outSheet ("method=\"html\" version=\"0.5\"", "<html/>"), null, "SESU0013"),
                // Static validation of xsl:output attributes: XTSE0020.
                err ("bad boolean on xsl:output", _outSheet ("method=\"xml\" indent=\"sometimes\"", "<e/>"), null, "XTSE0020"),
                err ("bad method", _outSheet ("method=\"pdf\"", "<e/>"), null, "XTSE1570"),
                // XTSE1560: conflicting xsl:output values at the same precedence.
                err ("conflicting outputs",
                     "<xsl:stylesheet version=\"3.0\" " +
                                            SPEC_NS +
                                            ">\n" +
                                            "<xsl:output method=\"xml\"/><xsl:output method=\"text\"/>\n" +
                                            "<xsl:template match=\"/\">x</xsl:template></xsl:stylesheet>",
                     null,
                     "XTSE1560"),
                ok ("outputs merge by attribute",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                  SPEC_NS +
                                                  ">\n" +
                                                  "<xsl:output method=\"xml\"/><xsl:output standalone=\"yes\"/>\n" +
                                                  "<xsl:template match=\"/\"><e/></xsl:template></xsl:stylesheet>",
                    null,
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><e/>"),
                ok ("build-tree no", _outSheet ("method=\"text\" build-tree=\"no\"", "<xsl:sequence select=\"1, 2\"/>"), null, "1 2"));
  }

  /**
   * XSLT 3.0 §26 xsl:output/@parameter-document and
   * xsl:result-document/@parameter-document (Serialization 3.1 §3.1).
   */
  @Test
  public void outputSpecParameterDocument ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "params.xml", """
        <output:serialization-parameters xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
          <output:method value="text"/>
          <output:item-separator value="|"/>
        </output:serialization-parameters>""");
    specRunAll (aDir,
                ok ("xsl:output parameter document",
                    _outSheet ("parameter-document=\"params.xml\"", "<xsl:sequence select=\"1, 2\"/>"),
                    null,
                    "1|2"),
                ok ("result-document parameter document",
                    "<xsl:stylesheet version=\"3.0\" " +
                                                          SPEC_NS +
                                                          "><xsl:output method=\"text\"/>\n" +
                                                          "<xsl:template match=\"/\"><xsl:result-document href=\"out.txt\" parameter-document=\"params.xml\"><xsl:sequence select=\"1, 2\"/></xsl:result-document></xsl:template></xsl:stylesheet>",
                    null,
                    ""),
                ok ("attribute overrides the parameter document",
                    _outSheet ("parameter-document=\"params.xml\" item-separator=\";\"", "<xsl:sequence select=\"1, 2\"/>"),
                    null,
                    "1;2"));
  }
}
