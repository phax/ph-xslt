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
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;

/**
 * Port of go-xslt <code>internal/xslt/functions_document_test.go</code>.
 *
 * @author Philip Helger
 */
public final class FunctionsDocumentSpecTest
{
  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  /**
   * XSLT 3.0 §20.1 fn:document, and fn:stream-available (§18.1.3).
   */
  @Test
  public void xsltFuncDocument () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir, "a.xml", "<a xml:id=\"top\"><b xml:id=\"bee\">B</b></a>");
    specWrite (aDir, "sub/c.xml", "<c>C</c>");
    specWrite (aDir, "sub/list.xml", "<l><h>c.xml</h></l>");
    specWrite (aDir, "prolog.xml", "\ufeff<?xml version=\"1.0\"?>\n<!-- c --><?pi x?>\n<!DOCTYPE p [<!ELEMENT p ANY>]>\n<p/>");
    specWrite (aDir, "dtdonly.xml", "<!DOCTYPE p [<!ELEMENT p ANY>]>");
    specWrite (aDir, "text.txt", "just text");
    specWrite (aDir, "trunc.xml", "<p><q>");
    // The source document of the specification helper has the URI
    // dir/source.xml; document('#id', /) reads it again through fn:doc
    specWrite (aDir, "source.xml", "<r xml:id=\"r1\"><x xml:id=\"x1\">X</x></r>");
    specRunAll (aDir,
      ok ("relative URI against the stylesheet base", "<xsl:template match=\"/\"><xsl:value-of select=\"document('a.xml')/a/b\"/></xsl:template>", null, "B"),
      ok ("same URI twice gives one document", "<xsl:template match=\"/\"><xsl:value-of select=\"count(document(('a.xml','a.xml')))\"/></xsl:template>", null, "1"),
      ok ("two documents", "<xsl:template match=\"/\"><xsl:value-of select=\"count(document(('a.xml','sub/c.xml')))\"/></xsl:template>", null, "2"),
      ok ("fragment identifier selects an element by ID", "<xsl:template match=\"/\"><xsl:value-of select=\"name(document('a.xml#bee'))\"/></xsl:template>", null, "b"),
      ok ("unknown fragment selects nothing", "<xsl:template match=\"/\"><xsl:value-of select=\"count(document('a.xml#none'))\"/></xsl:template>", null, "0"),
      // A node in the first argument resolves against that node's base URI.
      ok ("node argument resolves against its own base URI", "<xsl:template match=\"/\"><xsl:value-of select=\"document(document('sub/list.xml')/l/h)\"/></xsl:template>", null, "C"),
      // The second argument supplies the base URI for every item.
      ok ("base-node argument", "<xsl:template match=\"/\"><xsl:value-of select=\"document('c.xml', document('sub/list.xml'))\"/></xsl:template>", null, "C"),
      ok ("empty sequence argument", "<xsl:template match=\"/\"><xsl:value-of select=\"count(document(()))\"/></xsl:template>", null, "0"),
      // document('') is the stylesheet module itself.
      ok ("document('') is the stylesheet", "<xsl:variable name=\"m\" select=\"'unused'\"/><f:marker/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"name(document('')/*/f:marker)\"/></xsl:template>", null, "f:marker"),
      // The fragment of an empty URI with a base node names an element of
      // that node's document.
      ok ("document('#id', node)", "<xsl:template match=\"/\"><xsl:value-of select=\"document('#x1', /)\"/></xsl:template>", "<r xml:id=\"r1\"><x xml:id=\"x1\">X</x></r>", "X"),
      ok ("stream-available", "<xsl:template match=\"/\"><xsl:value-of select=\"stream-available('a.xml'), stream-available('prolog.xml'), stream-available('missing.xml'), stream-available('text.txt'), stream-available('dtdonly.xml'), stream-available('trunc.xml'), stream-available('')\"/></xsl:template>", null, "true true false false false true false"));
  }

  /**
   * XSLT 3.0 §20.? fn:current-output-uri: the base output URI in the principal
   * output, the resolved href inside xsl:result-document, and the empty
   * sequence in temporary output state (a variable or function body).
   */
  @Test
  public void xsltFuncCurrentOutputUri ()
  {
    final String sSheet = specSheet ("\n" +
                                     "<xsl:function name=\"f:u\"><xsl:sequence select=\"current-output-uri()\"/></xsl:function>\n" +
                                     "<xsl:template match=\"/\">\n" +
                                     "  <xsl:variable name=\"v\"><xsl:value-of select=\"count(current-output-uri())\"/></xsl:variable>\n" +
                                     "  <xsl:value-of select=\"current-output-uri(), $v, count(f:u())\" separator=\"|\"/>\n" +
                                     "  <xsl:result-document href=\"sub.txt\" method=\"text\"><xsl:value-of select=\"current-output-uri()\"/></xsl:result-document>\n" +
                                     "</xsl:template>");
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
    final String sBase = "file:///tmp/out/main.txt";
    XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (XsltTestHelper.parse ("<doc/>"));
    aRequest.setBaseOutputUri (sBase);
    XsltTransformResult aResult = aExec.transform (aRequest);
    assertEquals ("principal", sBase + "|0|0", aResult.principalSerialized ());
    assertEquals (1, aResult.secondaryResults ().size ());
    assertEquals ("secondary", "file:///tmp/out/sub.txt", aResult.secondaryResults ().getFirst ().serialized ());
    // Without a base output URI the function returns the empty sequence
    aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (XsltTestHelper.parse ("<doc/>"));
    aResult = aExec.transform (aRequest);
    assertEquals ("no base output URI", "0|0", aResult.principalSerialized ());
  }

  /**
   * XSLT 3.0 §10.3 stylesheet functions: the result of the body is converted
   * to the declared type by the function conversion rules.
   */
  @Test
  public void xsltFuncFunctionBodies ()
  {
    specRunAll (
      ok ("value-of body joins with separator", "<xsl:function name=\"f:j\"><xsl:value-of select=\"1 to 3\" separator=\"-\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:j()\"/></xsl:template>", null, "1-2-3"),
      ok ("value-of body default separator", "<xsl:function name=\"f:j\"><xsl:value-of select=\"'a','b'\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:j()\"/></xsl:template>", null, "a b"),
      // A text node is atomized to xs:untypedAtomic, then cast to xs:integer.
      ok ("value-of body cast to declared atomic type", "<xsl:function name=\"f:n\" as=\"xs:integer\"><xsl:value-of select=\"40 + 2\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:n(), f:n() instance of xs:integer\"/></xsl:template>", null, "42 true"),
      ok ("value-of body as a text node", "<xsl:function name=\"f:t\" as=\"text()\"><xsl:value-of select=\"'x'\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:t() instance of text(), f:t()\"/></xsl:template>", null, "true x"),
      // Atomizing a map is FOTY0013.
      err ("value-of a map in a function body", "<xsl:function name=\"f:m\"><xsl:value-of select=\"map{1:2}\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:m()\"/></xsl:template>", null, "FOTY0013"),
      ok ("sequence body keeps items", "<xsl:function name=\"f:s\"><xsl:sequence select=\"1, 'a', 2.5\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:s())\"/></xsl:template>", null, "3"),
      ok ("perform-sort body returns a sequence", "<xsl:function name=\"f:p\"><xsl:perform-sort select=\"(3,1,2)\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:p()), f:p()[1]\" separator=\"\"/></xsl:template>", null, "31"),
      ok ("evaluate body returns the sequence", "<xsl:function name=\"f:e\"><xsl:evaluate xpath=\"'(1,2,3)'\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:e())\"/></xsl:template>", null, "3"),
      ok ("where-populated body drops empty element", "<xsl:function name=\"f:w\" as=\"element()*\"><xsl:param name=\"x\"/><xsl:where-populated><e><xsl:value-of select=\"$x\"/></e></xsl:where-populated></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:w('')), count(f:w('a'))\"/></xsl:template>", null, "0 1"),
      // XTTE0780: the result does not match the declared type.
      err ("result type mismatch", "<xsl:function name=\"f:w\" as=\"element()\"><xsl:where-populated><e/></xsl:where-populated></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"count(f:w())\"/></xsl:template>", null, "XTTE0780"),
      ok ("constructed content body", "<xsl:function name=\"f:c\"><xsl:param name=\"n\"/><e><xsl:value-of select=\"$n\"/></e></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"name(f:c(1)), f:c(1)\"/></xsl:template>", null, "e 1"),
      ok ("recursion", "<xsl:function name=\"f:fact\" as=\"xs:integer\"><xsl:param name=\"n\" as=\"xs:integer\"/><xsl:sequence select=\"if ($n le 1) then 1 else $n * f:fact($n - 1)\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:fact(5)\"/></xsl:template>", null, "120"),
      // An argument that does not match the declared parameter type. go-xslt
      // expects XTTE0790, an XSLT 2.0 code that the XSLT 3.0 REC no longer
      // defines: §10.3.2 says the conversion "and the errors that can occur,
      // are defined in [XPath 3.0]", which is XPTY0004.
      err ("argument type mismatch", "<xsl:function name=\"f:i\" as=\"xs:integer\"><xsl:param name=\"n\" as=\"xs:integer\"/><xsl:sequence select=\"$n\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"f:i('abc')\"/></xsl:template>", null, "XPTY0004"),
      ok ("function item and dynamic call", "<xsl:function name=\"f:d\"><xsl:param name=\"n\"/><xsl:sequence select=\"$n * 2\"/></xsl:function>\n" +
        "<xsl:template match=\"/\"><xsl:variable name=\"g\" select=\"f:d#1\"/><xsl:value-of select=\"$g(3)\"/></xsl:template>", null, "6"),
      ok ("fn:-prefixed XSLT function", "<xsl:template match=\"/r\"><xsl:value-of select=\"fn:generate-id(a) = generate-id(a)\"/></xsl:template>", "<r><a/></r>", "true"));
  }

}
