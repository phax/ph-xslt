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

import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt <code>internal/xslt/instr_accumulator_spec_test.go</code>:
 * xsl:accumulator (XSLT 3.0 §18.2). Expected values follow the informal and
 * formal models of §18.2.3/§18.2.4: every node other than attributes and
 * namespaces is visited twice; the pre-descent value is the accumulator after
 * the node's start visit, the post-descent value after its end visit; when
 * several rules match, the last in document order is used.
 *
 * @author Philip Helger
 */
public final class InstrAccumulatorSpecTest
{
  private static final String ACC_SRC = "<doc><sec><p>a</p><p>b</p></sec><sec><p>c</p></sec></doc>";

  /** Counts p elements: before = after the start rule. */
  private static final String COUNT = "<xsl:accumulator name=\"n\" initial-value=\"0\" as=\"xs:integer\">\n" +
                                      "    <xsl:accumulator-rule match=\"p\" select=\"$value + 1\"/>\n" +
                                      "  </xsl:accumulator>";

  /**
   * Compile and run a stylesheet over a source.
   *
   * @return The serialized output, or "ERR:" and the error code.
   */
  @NonNull
  private static String _run (@NonNull final String sSheet, @Nullable final String sSrc)
  {
    try
    {
      final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
      final XsltTransformRequest aRequest = new XsltTransformRequest ();
      if (sSrc != null)
        aRequest.setGlobalContextItem (XsltTestHelper.parse (sSrc));
      return aExec.transform (aRequest).principalSerialized ();
    }
    catch (final XPathException ex)
    {
      return "ERR:" + ex.getCode ();
    }
  }

  /**
   * Compare a result of {@link #_run(String, String)} with the expected output,
   * or with one of the expected error codes ("ERR:" followed by codes
   * separated by "|").
   */
  private static void _check (@NonNull final ICommonsList <String> aErrors,
                              @NonNull final String sName,
                              @NonNull final String sGot,
                              @NonNull final String sWant)
  {
    if (sWant.startsWith ("ERR:"))
    {
      for (final String sCode : sWant.substring (4).split ("\\|"))
        if (sGot.equals ("ERR:" + sCode))
          return;
      aErrors.add (sName + ": got '" + sGot + "', want one of " + sWant);
    }
    else
      if (!sGot.equals (sWant))
        aErrors.add (sName + ": got '" + sGot + "', want '" + sWant + "'");
  }

  private static void _assertNoErrors (@NonNull final ICommonsList <String> aErrors, final int nCases)
  {
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + nCases + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  /**
   * A stylesheet with the given top-level declarations and a root template
   * body. The unnamed mode makes every accumulator applicable to the source
   * document (§18.2.2: for the initial match selection the applicable
   * accumulators are those of the initial mode's xsl:mode).
   */
  @NonNull
  private static String _accSheet (@NonNull final String sDecls, @NonNull final String sBody)
  {
    return "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
           "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:map=\"http://www.w3.org/2005/xpath-functions/map\"\n" +
           "  exclude-result-prefixes=\"xs map\">\n" +
           "  <xsl:output method=\"text\"/>\n" +
           "  <xsl:mode use-accumulators=\"#all\"/>\n" +
           "  " +
           sDecls +
           "\n" +
           "  <xsl:template match=\"/\">" +
           sBody +
           "</xsl:template>\n" +
           "</xsl:stylesheet>";
  }

  private static void _runAll (@NonNull final String [] [] aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
      _check (aErrors, aCase[0], _run (_accSheet (aCase[1], aCase[2]), ACC_SRC), aCase[3]);
    _assertNoErrors (aErrors, aCases.length);
  }

  @Test
  public void accumulatorSpec ()
  {
    _runAll (new String [] [] {
      { "before-after",
        COUNT,
        "<xsl:for-each select=\"//sec\">[<xsl:value-of select=\"accumulator-before('n'), accumulator-after('n')\"/>]</xsl:for-each>",
        "[0 2][2 3]" },
      { "document-node",
        COUNT,
        "<xsl:value-of select=\"accumulator-before('n'), accumulator-after('n')\"/>",
        "0 3" },
      // An end-phase rule changes the post-descent value of its node and
      // the values seen after it.
      { "end-phase",
        "<xsl:accumulator name=\"d\" initial-value=\"()\">\n" +
        "    <xsl:accumulator-rule match=\"sec\" phase=\"start\" select=\"($value, 's')\"/>\n" +
        "    <xsl:accumulator-rule match=\"sec\" phase=\"end\" select=\"($value, 'e')\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:for-each select=\"//p\">[<xsl:value-of select=\"accumulator-before('d')\" separator=\"\"/>]</xsl:for-each>",
        "[s][s][ses]" },
      // Several matching rules: the last in document order wins.
      { "last-rule-wins",
        "<xsl:accumulator name=\"w\" initial-value=\"''\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"'first'\"/>\n" +
        "    <xsl:accumulator-rule match=\"*\" select=\"'second'\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"(//p)[1]/accumulator-before('w')\"/>",
        "second" },
      // Text nodes are visited; the rule's context item is the node.
      { "text-nodes",
        "<xsl:accumulator name=\"t\" initial-value=\"''\">\n" +
        "    <xsl:accumulator-rule match=\"text()\" select=\"$value || .\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-after('t')\"/>",
        "abc" },
      // Rules whose value comes from a sequence constructor: a pure value
      // (xsl:sequence, xsl:choose, xsl:if), a map, and a node-building body.
      { "body-choose",
        "<xsl:accumulator name=\"c\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p\">\n" +
        "      <xsl:choose>\n" +
        "        <xsl:when test=\". = 'a'\"><xsl:sequence select=\"$value + 10\"/></xsl:when>\n" +
        "        <xsl:otherwise><xsl:sequence select=\"$value + 1\"/></xsl:otherwise>\n" +
        "      </xsl:choose>\n" +
        "    </xsl:accumulator-rule>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-after('c')\"/>",
        "12" },
      { "body-choose-no-otherwise",
        "<xsl:accumulator name=\"c\" initial-value=\"5\">\n" +
        "    <xsl:accumulator-rule match=\"p\">\n" +
        "      <xsl:choose><xsl:when test=\". = 'z'\"><xsl:sequence select=\"1\"/></xsl:when></xsl:choose>\n" +
        "    </xsl:accumulator-rule>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"count(accumulator-after('c'))\"/>",
        "0" },
      // The false xsl:if yields (), and () + 1 is () again.
      { "body-if",
        "<xsl:accumulator name=\"i\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p\"><xsl:if test=\". != 'b'\"><xsl:sequence select=\"$value + 1\"/></xsl:if></xsl:accumulator-rule>\n" +
        "  </xsl:accumulator>",
        "<xsl:for-each select=\"//p\">[<xsl:value-of select=\"count(accumulator-before('i'))\"/>]</xsl:for-each>",
        "[1][0][0]" },
      { "map-value",
        "<xsl:accumulator name=\"m\" initial-value=\"map{}\" as=\"map(xs:string, xs:integer)\">\n" +
        "    <xsl:accumulator-rule match=\"p\">\n" +
        "      <xsl:variable name=\"k\" select=\"string(.)\"/>\n" +
        "      <xsl:sequence select=\"map:put($value, $k, map:size($value) + 1)\"/>\n" +
        "    </xsl:accumulator-rule>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"let $m := accumulator-after('m') return ($m?a, $m?b, $m?c)\"/>",
        "1 2 3" },
      { "node-body",
        "<xsl:accumulator name=\"f\" initial-value=\"()\">\n" +
        "    <xsl:accumulator-rule match=\"p\"><v><xsl:value-of select=\".\"/></v></xsl:accumulator-rule>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"(//p)[2]/accumulator-before('f')\"/>",
        "b" },
      // A path step's context item is the node asked about, not the
      // template's current node.
      { "path-context",
        COUNT,
        "<xsl:value-of select=\"//p/accumulator-before('n')\"/>",
        "1 2 3" },
      // A temporary tree is a tree to which all accumulators apply.
      { "temporary-tree",
        COUNT,
        "<xsl:variable name=\"t\"><x><p/><p/></x></xsl:variable><xsl:value-of select=\"$t/x/accumulator-after('n')\"/>",
        "2" },
      // fn:copy-of keeps the accumulator values of the original (§18.2.2).
      { "copy-of-preserves",
        COUNT,
        "<xsl:value-of select=\"copy-of((//p)[3])/accumulator-before('n')\"/>",
        "3" },
      // The accumulator name is an EQName.
      { "eqname",
        "<xsl:accumulator name=\"Q{urn:x}n\" initial-value=\"7\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"$value\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-before('Q{urn:x}n')\"/>",
        "7" },
      // $value is not in scope in @match; text that merely looks like it
      // inside a string literal or comment is not a reference.
      { "value-in-literal",
        "<xsl:accumulator name=\"s\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p[. != '$value'] (: $value :)\" select=\"$value + 1\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-after('s')\"/>",
        "3" },
      // A dynamic error in a rule is reported only by a call that depends
      // on it (§18.2.5); with no call it goes unreported.
      { "unreported-error",
        "<xsl:accumulator name=\"e\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"1 idiv 0\"/>\n" +
        "  </xsl:accumulator>",
        "ok",
        "ok" },
      // A mode whose use-accumulators is #all can read the accumulator.
      { "mode-names-accumulator",
        COUNT + "<xsl:mode name=\"m\" use-accumulators=\"#all\"/>",
        "<xsl:apply-templates select=\"//sec[2]\" mode=\"m\"/></xsl:template><xsl:template match=\"sec\" mode=\"m\"><xsl:value-of select=\"accumulator-before('n')\"/>",
        "2" } });
  }

  /** Error cases: any of the listed codes is accepted. */
  @Test
  public void accumulatorSpecErrors ()
  {
    _runAll (new String [] [] {
      // XTDE3340: no accumulator of that name.
      { "XTDE3340",
        COUNT,
        "<xsl:value-of select=\"accumulator-before('nope')\"/>",
        "ERR:XTDE3340" },
      // XTTE3360: the context item is an attribute, or not a node.
      { "XTTE3360-attribute",
        COUNT + "",
        "<xsl:value-of select=\"parse-xml('&lt;a b=&quot;1&quot;/&gt;')/a/@b/accumulator-before('n')\"/>",
        "ERR:XTTE3360" },
      { "XTTE3360-atomic",
        COUNT,
        "<xsl:for-each select=\"1 to 2\"><xsl:value-of select=\"accumulator-before('n')\"/></xsl:for-each>",
        "ERR:XTTE3360" },
      // go-xslt expects XTDE3362 because the mode's use-accumulators list does
      // not include the accumulator. But §18.2.2 makes applicability a
      // property of the tree: "For a document containing nodes supplied in
      // the initial match selection, the accumulators that are applicable are
      // those determined by the xsl:mode declaration of the initial mode"
      // (here the unnamed mode, #all); the mode applied later does not change
      // it. So the call succeeds.
      { "XTDE3362-mode",
        COUNT + "<xsl:mode name=\"none\" use-accumulators=\"\"/>",
        "<xsl:apply-templates select=\"doc\" mode=\"none\"/></xsl:template><xsl:template match=\"doc\" mode=\"none\"><xsl:value-of select=\"accumulator-before('n')\"/>",
        "0" },
      // The declared type applies to the initial value and every rule
      // result (function conversion rules; XPTY0004 when impossible).
      { "as-initial",
        "<xsl:accumulator name=\"x\" initial-value=\"'s'\" as=\"xs:integer\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"1\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-before('x')\"/>",
        "ERR:XPTY0004" },
      { "as-rule",
        "<xsl:accumulator name=\"x\" initial-value=\"0\" as=\"xs:integer\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"'s'\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-after('x')\"/>",
        "ERR:XPTY0004" },
      // A dynamic error in a rule surfaces at a dependent call (§18.2.5).
      { "rule-error-reported",
        "<xsl:accumulator name=\"e\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"1 idiv 0\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-after('e')\"/>",
        "ERR:FOAR0001" },
      // XTDE3400: the value depends on itself.
      { "XTDE3400",
        "<xsl:accumulator name=\"c\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"$value + accumulator-before('c')\"/>\n" +
        "  </xsl:accumulator>",
        "<xsl:value-of select=\"accumulator-after('c')\"/>",
        "ERR:XTDE3400" },
      // XTSE3350: two declarations with one name at one precedence.
      { "XTSE3350",
        COUNT + COUNT,
        "x",
        "ERR:XTSE3350" },
      // XTSE0010: an accumulator needs at least one rule.
      { "XTSE0010-no-rules",
        "<xsl:accumulator name=\"x\" initial-value=\"0\"/>",
        "x",
        "ERR:XTSE0010" },
      // XTSE0010: a required attribute is missing.
      { "XTSE0010-no-name",
        "<xsl:accumulator initial-value=\"0\"><xsl:accumulator-rule match=\"p\" select=\"1\"/></xsl:accumulator>",
        "x",
        "ERR:XTSE0010" },
      { "XTSE0010-no-initial-value",
        "<xsl:accumulator name=\"a\"><xsl:accumulator-rule match=\"p\" select=\"1\"/></xsl:accumulator>",
        "x",
        "ERR:XTSE0010" },
      { "XTSE0010-no-match",
        "<xsl:accumulator name=\"a\" initial-value=\"0\"><xsl:accumulator-rule select=\"1\"/></xsl:accumulator>",
        "x",
        "ERR:XTSE0010" },
      // XTSE0020: streamable is an XSLT boolean.
      { "XTSE0020-streamable",
        "<xsl:accumulator name=\"x\" initial-value=\"0\" streamable=\"No\">\n" +
        "    <xsl:accumulator-rule match=\"p\" select=\"1\"/>\n" +
        "  </xsl:accumulator>",
        "x",
        "ERR:XTSE0020" },
      // XPST0008: $value is not in scope in @match.
      { "XPST0008-match",
        "<xsl:accumulator name=\"x\" initial-value=\"0\">\n" +
        "    <xsl:accumulator-rule match=\"p[$value = 0]\" select=\"1\"/>\n" +
        "  </xsl:accumulator>",
        "x",
        "ERR:XPST0008" } });
  }
}
