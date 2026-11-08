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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xslt.spec.XsltSpecTestHelper.SpecCase;

/**
 * Port of go-xslt's <code>internal/xslt/instr_number_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class InstrNumberSpecTest
{
  /**
   * Wrap one xsl:number usage in a template matching the document.
   *
   * @param sName
   *        Case name.
   * @param sBody
   *        Template body.
   * @param sWant
   *        Expected output.
   * @return The case.
   */
  @NonNull
  static SpecCase numSpec (@NonNull final String sName, @NonNull final String sBody, @NonNull final String sWant)
  {
    return ok (sName, "<xsl:template match=\"/\">" + sBody + "</xsl:template>", null, sWant);
  }

  @NonNull
  private static SpecCase _numErr (@NonNull final String sName, @NonNull final String sBody, @NonNull final String sCode)
  {
    return err (sName, "<xsl:template match=\"/\">" + sBody + "</xsl:template>", null, sCode);
  }

  /**
   * XSLT 3.0 §12.3 formatting a number sequence (format tokens per F&amp;O 3.1
   * §4.6.1 fn:format-integer, which §12.4 defers to).
   */
  @Test
  public void numberSpecFormatting ()
  {
    specRunAll (numSpec ("several values, several tokens", "<xsl:number value=\"1,2,3\" format=\"1.a.i\"/>", "1.b.iii"),
                // More numbers than tokens: the last token and separator are
                // reused.
                numSpec ("tokens reused", "<xsl:number value=\"1,2,3\" format=\"1.a\"/>", "1.b.c"),
                // One token, no separator: the default separator is ".".
                numSpec ("default separator and suffix", "<xsl:number value=\"1,2\" format=\"1.\"/>", "1.2."),
                numSpec ("prefix and suffix", "<xsl:number value=\"3\" format=\"(1)\"/>", "(3)"),
                numSpec ("zero padding wider than value", "<xsl:number value=\"1234\" format=\"01\"/>", "1234"),
                numSpec ("empty format means 1", "<xsl:number value=\"5\" format=\"{''}\"/>", "5"),
                numSpec ("alphabetic", "<xsl:number value=\"26, 28, 703\" format=\"a\"/>", "z.ab.aaa"),
                numSpec ("upper alphabetic", "<xsl:number value=\"28\" format=\"A\"/>", "AB"),
                numSpec ("roman", "<xsl:number value=\"1999\" format=\"i\"/>", "mcmxcix"),
                numSpec ("upper roman", "<xsl:number value=\"3888\" format=\"I\"/>", "MMMDCCCLXXXVIII"),
                // F&O §4.6.1: "w" words, "W" upper case, "Ww" title case.
                numSpec ("words", "<xsl:number value=\"3\" format=\"w\"/>", "three"),
                numSpec ("upper words", "<xsl:number value=\"3\" format=\"W\"/>", "THREE"),
                numSpec ("title words", "<xsl:number value=\"3\" format=\"Ww\"/>", "Three"),
                numSpec ("words for zero", "<xsl:number value=\"0\" format=\"w\"/>", "zero"),
                // F&O example: format-integer(14, 'Ww;o') = "Fourteenth".
                numSpec ("ordinal title words", "<xsl:number value=\"14\" format=\"Ww\" ordinal=\"yes\"/>", "Fourteenth"),
                numSpec ("ordinal words",
                         "<xsl:number value=\"1, 2, 3, 20\" format=\"w\" ordinal=\"yes\"/>",
                         "first.second.third.twentieth"),
                numSpec ("decimal digit family", "<xsl:number value=\"12\" format=\"&#x661;\"/>", "١٢"),
                numSpec ("padded digit family", "<xsl:number value=\"7\" format=\"&#x660;&#x661;\"/>", "٠٧"),
                numSpec ("fullwidth digits", "<xsl:number value=\"305\" format=\"&#xFF11;\"/>", "３０５"),
                numSpec ("grouping",
                         "<xsl:number value=\"1234567\" grouping-separator=\".\" grouping-size=\"2\"/>",
                         "1.23.45.67"),
                numSpec ("grouping AVT",
                         "<xsl:number value=\"1234567\" grouping-separator=\"{' '}\" grouping-size=\"{3}\"/>",
                         "1 234 567"),
                numSpec ("rounding of a non-integer value", "<xsl:number value=\"2.5, 3.4\"/>", "3.3"),
                numSpec ("untypedAtomic value",
                         "<xsl:variable name=\"v\"><v>12</v></xsl:variable><xsl:number value=\"$v/v\"/>",
                         "12"),
                // The integer is exact, not routed through xs:double.
                numSpec ("big integer value",
                         "<xsl:number value=\"xs:integer('123456789012345678901234')\"/>",
                         "123456789012345678901234"),
                numSpec ("big integer with grouping",
                         "<xsl:number value=\"xs:integer('123456789012345678901')\" grouping-separator=\",\" grouping-size=\"3\"/>",
                         "123,456,789,012,345,678,901"),
                numSpec ("zero", "<xsl:number value=\"0\"/>", "0"),
                // §12.3 start-at.
                numSpec ("start-at", "<xsl:number value=\"1, 2\" start-at=\"0\"/>", "0.1"),
                numSpec ("start-at per level", "<xsl:number value=\"1, 1, 1\" start-at=\"10 20\"/>", "10.20.20"),
                // §12.3 lang: German words.
                numSpec ("lang de", "<xsl:number value=\"3\" format=\"w\" lang=\"de\"/>", "drei"),
                // XTDE0980: not convertible to a non-negative integer.
                _numErr ("negative value", "<xsl:number value=\"-1\"/>", "XTDE0980"),
                _numErr ("big negative value",
                         "<xsl:number value=\"xs:integer('-123456789012345678901234')\"/>",
                         "XTDE0980"),
                _numErr ("NaN value", "<xsl:number value=\"number('x')\"/>", "XTDE0980"),
                _numErr ("string value", "<xsl:number value=\"'fizz'\"/>", "XTDE0980"));
  }

  /**
   * XSLT 3.0 §12.2 numbering based on position in a document: level, count,
   * from, select (the REC's own examples).
   */
  @Test
  public void numberSpecLevels ()
  {
    final String sBook = "<book><chapter><title/><section><title/><note/></section><section><title/><note/><note/></section></chapter><chapter><title/><note/><section><title/><note/></section></chapter></book>";
    specRunAll (
                // §12.2 example: multi-level heading numbers.
                ok ("level multiple", """
                    <xsl:template match="/"><xsl:apply-templates select="//title"/></xsl:template>
                    <xsl:template match="title"><xsl:number level="multiple" count="chapter|section" format="1.1 "/></xsl:template>""",
                    sBook,
                    "1 1.1 1.2 2 2.1 "),
                // §12.2 example: notes numbered per chapter with level="any"
                // from="chapter".
                ok ("level any from", """
                    <xsl:template match="/"><xsl:apply-templates select="//note"/></xsl:template>
                    <xsl:template match="note"><xsl:number level="any" from="chapter" format="(1)"/></xsl:template>""",
                    sBook,
                    "(1)(2)(3)(1)(2)"),
                ok ("level any without from", """
                    <xsl:template match="/"><xsl:apply-templates select="//note"/></xsl:template>
                    <xsl:template match="note"><xsl:number level="any" format="1 "/></xsl:template>""", sBook, "1 2 3 4 5 "),
                ok ("level any with count", """
                    <xsl:template match="/"><xsl:apply-templates select="//section[note][1]/note[1] | (//note)[last()]"/></xsl:template>
                    <xsl:template match="note"><xsl:number level="any" count="title|note" format="1 "/></xsl:template>""",
                    sBook,
                    "3 10 "),
                ok ("level single with count ancestor", """
                    <xsl:template match="/"><xsl:apply-templates select="//section/note[1]"/></xsl:template>
                    <xsl:template match="note"><xsl:number count="section" format="1."/></xsl:template>""", sBook, "1.2.1."),
                ok ("level single with from", """
                    <xsl:template match="/"><xsl:apply-templates select="(//b)[2]"/></xsl:template>
                    <xsl:template match="b"><xsl:number from="a"/></xsl:template>""", "<r><a><x/><b/><b/></a><a><b/></a></r>", "2"),
                // No node matches @count: the empty sequence formats as "".
                ok ("nothing counted", """
                    <xsl:template match="/">[<xsl:for-each select="/book"><xsl:number count="nonexistent"/></xsl:for-each>]</xsl:template>""",
                    sBook,
                    "[]"),
                ok ("select attribute", """
                    <xsl:template match="/"><xsl:number select="/book/chapter[2]"/></xsl:template>""", sBook, "2"),
                // XTTE1000: @select is not a single node.
                err ("select two nodes", """
                    <xsl:template match="/"><xsl:number select="//chapter"/></xsl:template>""", sBook, "XTTE1000"),
                err ("select an atomic value", """
                    <xsl:template match="/"><xsl:number select="1"/></xsl:template>""", sBook, "XTTE1000"),
                // XTTE0990: the context item is not a node.
                err ("atomic context item", """
                    <xsl:template match="/"><xsl:for-each select="1"><xsl:number/></xsl:for-each></xsl:template>""", sBook, "XTTE0990"),
                // XTSE0975: @value with @select/@level/@count/@from.
                err ("value with level", """
                    <xsl:template match="/"><xsl:number value="1" level="any"/></xsl:template>""", null, "XTSE0975"),
                err ("value with count", """
                    <xsl:template match="/"><xsl:number value="1" count="x"/></xsl:template>""", null, "XTSE0975"),
                err ("value with select", """
                    <xsl:template match="/"><xsl:number value="1" select="."/></xsl:template>""", null, "XTSE0975"));
  }

  /**
   * §12.2: an omitted @count matches nodes of the same kind as the selected
   * node and, if it has an expanded QName, the same expanded QName.
   */
  @Test
  public void numberSpecDefaultCount ()
  {
    specRunAll (
                // An attribute matches itself; it has no preceding siblings.
                ok ("attribute",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"/r/@b\"><xsl:number/></xsl:for-each></xsl:template>",
                    "<r a=\"1\" b=\"2\"/>",
                    "1"),
                ok ("attribute level any",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"//s/@b\"><xsl:number level=\"any\"/></xsl:for-each></xsl:template>",
                    "<r a=\"1\" b=\"2\"><s b=\"3\"/></r>",
                    "1"),
                // A processing instruction counts only those with its own name.
                ok ("pi",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"/r/processing-instruction('p')[2]\"><xsl:number/></xsl:for-each></xsl:template>",
                    "<r><?p?><?q?><?p?></r>",
                    "2"),
                ok ("pi level any",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"/r/processing-instruction('p')[2]\"><xsl:number level=\"any\"/></xsl:for-each></xsl:template>",
                    "<r><?p?><x><?q?></x><?p?></r>",
                    "2"));
  }

  /** Behaviour that disagrees with the REC in go-xslt (skipped there). */
  @Test
  public void numberSpecKnownBugs ()
  {
    specRunAll (_numErr ("start-at", "<xsl:number value=\"1\" start-at=\"{'x'}\"/>", "XTDE0030"),
                _numErr ("lang", "<xsl:number value=\"1\" lang=\"{'42'}\"/>", "XTDE0030"),
                _numErr ("level", "<xsl:number level=\"all\"/>", "XTSE0020"));
  }
}
