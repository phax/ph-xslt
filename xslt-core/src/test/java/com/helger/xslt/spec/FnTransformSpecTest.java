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
 * Port of go-xslt <code>internal/xslt/fn_transform_test.go</code>: fn:transform (F&amp;O 3.1
 * §16.3.2), called from inside a stylesheet. The SOURCE document of each run is the INNER
 * stylesheet, so the outer stylesheet hands it on as "stylesheet-node": / (or "stylesheet-text":
 * serialize(/)) and no file is ever read.
 *
 * @author Philip Helger
 */
public final class FnTransformSpecTest
{
  private static final String INNER_HEAD = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
                                           "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:my=\"urn:my\" exclude-result-prefixes=\"xs my\">\n" +
                                           "  <xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>\n";

  /**
   * The inner stylesheet: a root template copying doc's text into &lt;out&gt;, plus the named
   * template, mode, function, and parameters the cases select.
   */
  private static final String INNER = INNER_HEAD +
                                      "\n" +
                                      "  <xsl:param name=\"g\" select=\"'default-g'\"/>\n" +
                                      "  <xsl:param name=\"st\" static=\"yes\" select=\"'static-default'\"/>\n" +
                                      "  <xsl:template match=\"/\"><out><xsl:value-of select=\"doc\"/></out></xsl:template>\n" +
                                      "  <xsl:template match=\"/\" mode=\"m\"><out>mode-m:<xsl:value-of select=\"doc\"/></out></xsl:template>\n" +
                                      "  <xsl:template match=\"item\"><i><xsl:value-of select=\".\"/></i></xsl:template>\n" +
                                      "  <xsl:template name=\"main\">\n" +
                                      "    <xsl:param name=\"p\" select=\"'default-p'\"/>\n" +
                                      "    <out>main:<xsl:value-of select=\"$p\"/></out>\n" +
                                      "  </xsl:template>\n" +
                                      "  <xsl:template name=\"tun\">\n" +
                                      "    <xsl:call-template name=\"tun-inner\"/>\n" +
                                      "  </xsl:template>\n" +
                                      "  <xsl:template name=\"tun-inner\">\n" +
                                      "    <xsl:param name=\"tp\" tunnel=\"yes\" select=\"'no-tunnel'\"/>\n" +
                                      "    <out>tunnel:<xsl:value-of select=\"$tp\"/></out>\n" +
                                      "  </xsl:template>\n" +
                                      "  <xsl:template name=\"global\"><out><xsl:value-of select=\"$g\"/></out></xsl:template>\n" +
                                      "  <xsl:template name=\"static\"><out><xsl:value-of select=\"$st\"/></out></xsl:template>\n" +
                                      "  <xsl:template name=\"req\">\n" +
                                      "    <xsl:param name=\"r\" required=\"yes\"/>\n" +
                                      "    <out><xsl:value-of select=\"$r\"/></out>\n" +
                                      "  </xsl:template>\n" +
                                      "  <xsl:template name=\"merge\"><out><xsl:value-of select=\"'a'\"/><xsl:value-of select=\"'b'\"/><xsl:value-of select=\"'c'\"/></out></xsl:template>\n" +
                                      "  <xsl:template name=\"fail\"><xsl:message terminate=\"yes\">stop</xsl:message></xsl:template>\n" +
                                      "  <xsl:template name=\"secondary\">\n" +
                                      "    <xsl:result-document href=\"sec.xml\"><s>second</s></xsl:result-document>\n" +
                                      "    <p>primary</p>\n" +
                                      "  </xsl:template>\n" +
                                      "  <xsl:template name=\"only-secondary\">\n" +
                                      "    <xsl:result-document href=\"a.xml\"><a/></xsl:result-document>\n" +
                                      "  </xsl:template>\n" +
                                      "  <xsl:function name=\"my:add\" as=\"xs:integer\" visibility=\"public\">\n" +
                                      "    <xsl:param name=\"a\" as=\"xs:integer\"/>\n" +
                                      "    <xsl:param name=\"b\" as=\"xs:integer\"/>\n" +
                                      "    <xsl:sequence select=\"$a + $b\"/>\n" +
                                      "  </xsl:function>\n" +
                                      "  <xsl:function name=\"my:seq\" as=\"xs:integer*\" visibility=\"public\">\n" +
                                      "    <xsl:sequence select=\"1 to 3\"/>\n" +
                                      "  </xsl:function>\n" +
                                      "</xsl:stylesheet>";

  /**
   * Reads the global context item in a global variable; a separate stylesheet because a global
   * variable whose context item is absent may be reported as XPDY0002 even when nothing references
   * it.
   */
  private static final String INNER_CTX = INNER_HEAD +
                                          "\n" +
                                          "  <xsl:variable name=\"ctx\" select=\"if (. instance of document-node()) then 'document' else if (. instance of node()) then name(.) else string(.)\"/>\n" +
                                          "  <xsl:template name=\"ctx\"><out><xsl:value-of select=\"$ctx\"/></out></xsl:template>\n" +
                                          "</xsl:stylesheet>";

  private static final String SRC = "parse-xml('&lt;doc&gt;this&lt;/doc&gt;')";

  /**
   * The calling stylesheet: its root template writes the string value of the expression as text.
   */
  @NonNull
  private static String _outer (@NonNull final String sExpr)
  {
    return "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
           "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
           "  xmlns:map=\"http://www.w3.org/2005/xpath-functions/map\">\n" +
           "  <xsl:output method=\"text\"/>\n" +
           "  <xsl:template match=\"/\"><xsl:value-of select=\"" +
           sExpr +
           "\"/></xsl:template>\n" +
           "</xsl:stylesheet>";
  }

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
   * Compare a result of {@link #_run(String, String)} with the expected output, or with one of the
   * expected error codes ("ERR:" followed by codes separated by "|").
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

  private static void _runAll (@NonNull final String sInner, @NonNull final String [] [] aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
      _check (aErrors, aCase[0], _run (_outer (aCase[1]), sInner), aCase[2]);
    _assertNoErrors (aErrors, aCases.length);
  }

  @Test
  public void fnTransformOptions ()
  {
    _runAll (INNER,
             new String [] [] {
                                // stylesheet-node / stylesheet-text with source-node; the default
                                // delivery-format is "document", so ?output is a document node.
                                { "node",
                                  "transform(map{'stylesheet-node': /, 'source-node': " + SRC + "})?output/out",
                                  "this" },
                                { "text",
                                  "transform(map{'stylesheet-text': serialize(/), 'source-node': " +
                                          SRC +
                                          "})?output/out",
                                  "this" },
                                { "document-is-node",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                      SRC +
                                                      "})?output instance of document-node()",
                                  "true" },
                                // delivery-format "serialized": the output serialized per the
                                // stylesheet's own xsl:output.
                                { "serialized",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                SRC +
                                                ", 'delivery-format': 'serialized'})?output",
                                  "<out>this</out>" },
                                // serialization-params override the unnamed xsl:output.
                                { "serialization-params-text",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                               SRC +
                                                               ", 'delivery-format': 'serialized', 'serialization-params': map{'method': 'text'}})?output",
                                  "this" },
                                { "serialization-params-decl",
                                  "starts-with(transform(map{'stylesheet-node': /, 'source-node': " +
                                                               SRC +
                                                               ", 'delivery-format': 'serialized', 'serialization-params': map{'omit-xml-declaration': false()}})?output, '&lt;?xml')",
                                  "true" },
                                // initial-mode selects the mode the initial apply-templates uses.
                                { "initial-mode",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                  SRC +
                                                  ", 'initial-mode': QName('', 'm')})?output/out",
                                  "mode-m:this" },
                                // initial-template, with and without template-params.
                                { "initial-template",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'main')})?output/out",
                                  "main:default-p" },
                                { "template-params",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'main'), 'template-params': map{QName('', 'p'): 'given'}})?output/out",
                                  "main:given" },
                                { "tunnel-params",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'tun'), 'tunnel-params': map{QName('', 'tp'): 'carried'}})?output/out",
                                  "tunnel:carried" },
                                // stylesheet-params set global parameters; static-params static
                                // ones.
                                { "stylesheet-params",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'global'), 'stylesheet-params': map{QName('', 'g'): 'set-g'}})?output/out",
                                  "set-g" },
                                { "stylesheet-params-default",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'global')})?output/out",
                                  "default-g" },
                                { "static-params",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'static'), 'static-params': map{QName('', 'st'): 'static-set'}})?output/out",
                                  "static-set" },
                                // initial-function with function-params; "raw" delivers the
                                // function's own result sequence, not a document.
                                { "initial-function-raw",
                                  "transform(map{'stylesheet-node': /, 'initial-function': QName('urn:my', 'add'), 'function-params': [2, 3], 'delivery-format': 'raw'})?output",
                                  "5" },
                                { "raw-integer",
                                  "transform(map{'stylesheet-node': /, 'initial-function': QName('urn:my', 'add'), 'function-params': [2, 3], 'delivery-format': 'raw'})?output instance of xs:integer",
                                  "true" },
                                { "raw-sequence",
                                  "string-join(transform(map{'stylesheet-node': /, 'initial-function': QName('urn:my', 'seq'), 'function-params': [], 'delivery-format': 'raw'})?output, ',')",
                                  "1,2,3" },
                                // initial-match-selection: apply-templates to each item in turn.
                                { "initial-match-selection",
                                  "string-join(transform(map{'stylesheet-node': /, 'initial-match-selection': parse-xml('&lt;r&gt;&lt;item&gt;a&lt;/item&gt;&lt;item&gt;b&lt;/item&gt;&lt;/r&gt;')//item})?output/i, ',')",
                                  "a,b" },
                                // post-process is applied to every result, with its key.
                                { "post-process",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                  SRC +
                                                  ", 'post-process': function($k, $v) { $k || '=' || $v/out }})?output",
                                  "output=this" },
                                // base-output-uri keys the principal result; a secondary result
                                // document is keyed by its absolute URI (resolved against it).
                                { "base-output-uri",
                                  "string-join(sort(map:keys(transform(map{'stylesheet-node': /, 'initial-template': QName('', 'secondary'), 'base-output-uri': 'http://example.com/out/main.xml'}))), ' ')",
                                  "http://example.com/out/main.xml http://example.com/out/sec.xml" },
                                { "secondary-value",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'secondary'), 'base-output-uri': 'http://example.com/out/main.xml'})('http://example.com/out/sec.xml')/s",
                                  "second" },
                                { "secondary-serialized",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'secondary'), 'base-output-uri': 'http://example.com/out/main.xml', 'delivery-format': 'serialized'})('http://example.com/out/sec.xml')",
                                  "<s>second</s>" },
                                // An absent principal result has no "output" entry (bug 30209,
                                // fn-transform-13a).
                                { "no-principal",
                                  "map:contains(transform(map{'stylesheet-node': /, 'initial-template': QName('', 'only-secondary'), 'base-output-uri': 'http://example.com/o.xml'}), 'http://example.com/o.xml')",
                                  "false" },
                                // xslt-version is accepted when it is a single number.
                                { "xslt-version",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                  SRC +
                                                  ", 'xslt-version': 3.0})?output/out",
                                  "this" },
                                // "raw" for a template delivers its result without a document node.
                                { "raw-template",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'main'), 'delivery-format': 'raw'})?output instance of element(out)",
                                  "true" },
                                // Adjacent text nodes in constructed content are merged into one
                                // (XSLT 3.0 §5.7.1).
                                { "merged-text",
                                  "count(transform(map{'stylesheet-node': /, 'initial-template': QName('', 'merge')})?output/out/text())",
                                  "1" },
                                // Serialization 3.1: character maps apply to text content,
                                // cdata-section-elements wrap the listed element's text, and the
                                // declaration carries standalone when asked to.
                                { "ser-character-map",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                       SRC +
                                                       ", 'delivery-format': 'serialized', 'serialization-params': map{'use-character-maps': map{'t': 'T'}}})?output",
                                  "<out>This</out>" },
                                { "ser-cdata",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                               SRC +
                                               ", 'delivery-format': 'serialized', 'serialization-params': map{'cdata-section-elements': QName('', 'out')}})?output",
                                  "<out><![CDATA[this]]></out>" },
                                // go-xslt passes 'standalone': 'yes'; serialization-params follow
                                // the
                                // rules of fn:serialize (F&O 3.1 §16.3.2), where standalone is
                                // "xs:boolean?", so true() is used here and the string is XPTY0004.
                                { "ser-standalone",
                                  "contains(transform(map{'stylesheet-node': /, 'source-node': " +
                                                    SRC +
                                                    ", 'delivery-format': 'serialized', 'serialization-params': map{'omit-xml-declaration': false(), 'standalone': true()}})?output, 'standalone=&quot;yes&quot;')",
                                  "true" },
                                { "ser-standalone-string",
                                  "contains(transform(map{'stylesheet-node': /, 'source-node': " +
                                                           SRC +
                                                           ", 'delivery-format': 'serialized', 'serialization-params': map{'omit-xml-declaration': false(), 'standalone': 'yes'}})?output, 'standalone=&quot;yes&quot;')",
                                  "ERR:XPTY0004" },
                                // requested-properties that this processor satisfies run normally.
                                { "requested-satisfied",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                         SRC +
                                                         ", 'requested-properties': map{QName('http://www.w3.org/1999/XSL/Transform', 'supports-namespace-axis'): true()}})?output/out",
                                  "this" } });
  }

  /** The global-context-item option and its default under source-node. */
  @Test
  public void fnTransformGlobalContextItem ()
  {
    _runAll (INNER_CTX,
             new String [] [] {
                                // global-context-item: an explicit item for global variables.
                                { "global-context-item",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'ctx'), 'global-context-item': 'atomic'})?output/out",
                                  "atomic" },
                                // With source-node and no global-context-item, the global context
                                // item is the ROOT of the tree containing source-node
                                // (fn-transform-82b), not source-node itself.
                                { "source-node-root-is-global",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                                SRC +
                                                                "/doc, 'initial-template': QName('', 'ctx')})?output/out",
                                  "document" } });
  }

  @Test
  public void fnTransformErrors ()
  {
    _runAll (INNER,
             new String [] [] {
                                // FOXT0002: invalid or inconsistent options (§16.3.2).
                                { "two-stylesheets",
                                  "transform(map{'stylesheet-node': /, 'stylesheet-text': serialize(/)})",
                                  "ERR:FOXT0002" },
                                { "no-stylesheet", "transform(map{'source-node': " + SRC + "})", "ERR:FOXT0002" },
                                { "template-and-mode",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'main'), 'initial-mode': QName('', 'm')})",
                                  "ERR:FOXT0002" },
                                { "source-and-selection",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                          SRC +
                                                          ", 'initial-match-selection': " +
                                                          SRC +
                                                          "})",
                                  "ERR:FOXT0002" },
                                // fn-transform-err-13
                                { "bad-delivery-format",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                         SRC +
                                                         ", 'delivery-format': 'doc'})",
                                  "ERR:FOXT0002" },
                                // fn-transform-err-18. go-xslt expects FOXT0002 only; the option
                                // parameter conventions (F&O 3.1 §1.5) convert the option value to
                                // its required type map(xs:QName, item()*), which "will result in
                                // an
                                // error (typically [err:XPTY0004])", so both codes are accepted.
                                { "param-key-not-qname",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                         SRC +
                                                         ", 'stylesheet-params': map{'g': 1}})",
                                  "ERR:FOXT0002|XPTY0004" },
                                // The same: () does not convert to the required type node()
                                { "empty-stylesheet-node",
                                  "transform(map{'stylesheet-node': ()})",
                                  "ERR:FOXT0002|XPTY0004" },
                                { "unknown-package",
                                  "transform(map{'package-name': 'urn:no-such-package'})",
                                  "ERR:FOXT0002" },
                                // xslt-version is xs:decimal: a string is a type error
                                // (fn-transform-err-4).
                                { "xslt-version-string",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                         SRC +
                                                         ", 'xslt-version': '3.0'})",
                                  "ERR:XPTY0004" },
                                // The option values must match their declared map types.
                                { "stylesheet-params-not-map",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                               SRC +
                                                               ", 'stylesheet-params': 'x'})",
                                  "ERR:XPTY0004" },
                                { "static-params-not-map",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                           SRC +
                                                           ", 'static-params': 'x'})",
                                  "ERR:XPTY0004" },
                                // $options is map(*): anything else is a type error.
                                { "arg-not-map", "transform('x')", "ERR:XPTY0004" },
                                { "arg-two-maps", "transform((map{}, map{}))", "ERR:XPTY0004" },
                                // An error raised by the invoked transformation keeps its own code
                                // (fn-transform-err-15: missing required template parameter).
                                // function-params defaults to the empty array and the arity of the
                                // initial function is inferred from its length, so omitting it asks
                                // for my:add#0, which does not exist: XSLT's XTDE0041, whose code
                                // fn:transform retains.
                                { "function-without-params",
                                  "transform(map{'stylesheet-node': /, 'initial-function': QName('urn:my', 'add')})",
                                  "ERR:XTDE0041" },
                                { "inner-required-param",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'req')})",
                                  "ERR:XTDE0700" },
                                { "inner-terminate",
                                  "transform(map{'stylesheet-node': /, 'initial-template': QName('', 'fail')})",
                                  "ERR:XTMM9000" },
                                // A static error in the supplied stylesheet text.
                                { "inner-static-error",
                                  "transform(map{'stylesheet-text': '&lt;xsl:stylesheet version=&quot;3.0&quot; xmlns:xsl=&quot;http://www.w3.org/1999/XSL/Transform&quot;&gt;&lt;xsl:bogus/&gt;&lt;/xsl:stylesheet&gt;'})",
                                  "ERR:XTSE0010" },
                                // requested-properties this processor cannot honor: F&O 3.1 lets a
                                // processor either ignore the request or raise FOXT0001; this one
                                // documents the error (fn_transform.go,
                                // xfCheckRequestedProperties).
                                { "requested-unavailable",
                                  "transform(map{'stylesheet-node': /, 'source-node': " +
                                                           SRC +
                                                           ", 'requested-properties': map{QName('http://www.w3.org/1999/XSL/Transform', 'supports-namespace-axis'): false()}})",
                                  "ERR:FOXT0001" } });
  }
}
