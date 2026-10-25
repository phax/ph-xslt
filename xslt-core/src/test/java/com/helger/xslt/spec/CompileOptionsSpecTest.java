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
import static com.helger.xslt.spec.XsltSpecTestHelper.specSheet;
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Port of go-xslt <code>internal/xslt/compile_options_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class CompileOptionsSpecTest
{
  private static final String CI = "http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive";

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

  @NonNull
  private static String _runWithStatic (@NonNull final String sSheet, @Nullable final XdmAtomic aValue)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    if (aValue != null)
      aOptions.setStaticParam (new XdmQName ("s"), XdmSequence.of (aValue));
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, aOptions);
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setGlobalContextItem (XsltTestHelper.parse ("<doc/>"));
    return aExec.transform (aRequest).principalSerialized ();
  }

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  /**
   * More serialization parameters on xsl:output (Serialization 3.1) and in a
   * parameter document, including inline character maps.
   */
  @Test
  public void outputSpecMoreParameters () throws IOException
  {
    final Path aDir = m_aTmp.newFolder ().toPath ();
    specWrite (aDir, "cm.xml", "<output:serialization-parameters xmlns:output=\"http://www.w3.org/2010/xslt-xquery-serialization\">\n" +
        "  <output:method value=\"text\"/>\n" +
        "  <output:use-character-maps><output:character-map character=\"x\" map-string=\"[X]\"/></output:use-character-maps>\n" +
        "</output:serialization-parameters>");
    specWrite (aDir, "badcm.xml", "<output:serialization-parameters xmlns:output=\"http://www.w3.org/2010/xslt-xquery-serialization\">\n" +
        "  <output:use-character-maps><output:character-map character=\"xy\" map-string=\"z\"/></output:use-character-maps>\n" +
        "</output:serialization-parameters>");
    specRunAll (aDir,
      ok ("parameter document character map", _outSheet ("parameter-document=\"cm.xml\"", "axb"), null, "a[X]b"),
      // go-xslt expects XTSE0020; §26.1 says "A serialization error occurs if
      // the result of dereferencing the URI is ill-formed or invalid", which
      // is SEPM0017 (Serialization 3.1 §3.1).
      err ("parameter document character map of two characters", _outSheet ("parameter-document=\"badcm.xml\"", "x"), null, "SEPM0017"),
      // The byte order mark is serialized first.
      ok ("byte-order-mark", _outSheet ("method=\"text\" byte-order-mark=\"yes\"", "x"), null, "\ufeffx"),
      // suppress-indentation keeps the content of the named element on one line.
      ok ("suppress-indentation", _outSheet ("method=\"xml\" omit-xml-declaration=\"yes\" indent=\"yes\" suppress-indentation=\"p\"", "<r><p><b>x</b></p></r>"), null, "~<p><b>x</b></p>"),
      // The html method %-escapes non-ASCII characters in URI attributes
      // (fn:escape-html-uri) unless escape-uri-attributes="no".
      ok ("escape-uri-attributes default", _outSheet ("method=\"html\"", "<html><body><a href=\"ä\">x</a></body></html>"), null, "~href=\"%C3%A4\""),
      ok ("escape-uri-attributes no", _outSheet ("method=\"html\" escape-uri-attributes=\"no\"", "<html><body><a href=\"ä\">x</a></body></html>"), null, "~href=\"ä\""),
      ok ("media-type and include-content-type", _outSheet ("method=\"html\" media-type=\"text/html\" include-content-type=\"yes\" html-version=\"5\"", "<html><head/><body/></html>"), null, "~text/html"),
      ok ("json allow-duplicate-names", _outSheet ("method=\"json\" allow-duplicate-names=\"no\" json-node-output-method=\"xml\"", "<xsl:sequence select=\"map{'a': 1}\"/>"), null, "{\"a\":1}"),
      ok ("xml version 1.1 output", _outSheet ("method=\"xml\" version=\"1.1\"", "<e/>"), null, "~version=\"1.1\""),
      ok ("undeclare-prefixes at XML 1.1", _outSheet ("method=\"xml\" version=\"1.1\" undeclare-prefixes=\"yes\"", "<e/>"), null, "~<e"));
  }

  /**
   * XSLT 3.0 §3.13.? static parameters supplied by the host, and default
   * collations (§5.3.?) used by comparisons and keys.
   */
  @Test
  public void compileSpecStaticParamsAndCollations ()
  {
    final String sSheet = specSheet ("<xsl:param name=\"s\" static=\"yes\" select=\"1\"/>\n" +
                                     "<xsl:variable name=\"v\" select=\"$s * 10\"/>\n" +
                                     "<xsl:template match=\"/\" use-when=\"$s gt 2\"><xsl:value-of select=\"'big', $v\"/></xsl:template>\n" +
                                     "<xsl:template match=\"/\" use-when=\"$s le 2\"><xsl:value-of select=\"'small', $v\"/></xsl:template>");
    // No static parameter supplied: the default applies
    assertEquals ("small 10", _runWithStatic (sSheet, null));
    // A host-supplied value (go-xslt passes the string "5"; a typed integer
    // here, since "gt" would compare an untyped value as a string)
    assertEquals ("big 50", _runWithStatic (sSheet, XdmAtomic.integer (5)));
    specRunAll (
      ok ("default-collation on the stylesheet", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " default-collation=\"" + CI + "\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"'abc' = 'ABC'\"/></xsl:template></xsl:stylesheet>", null, "true"),
      ok ("default-collation falls back to the first supported URI", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " default-collation=\"http://example.com/unknown " + CI + "\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"'abc' = 'ABC'\"/></xsl:template></xsl:stylesheet>", null, "true"),
      // XTSE0125: no supported collation in default-collation.
      err ("default-collation unsupported", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " default-collation=\"http://example.com/unknown\"><xsl:output method=\"text\"/>\n" +
        "<xsl:template match=\"/\">x</xsl:template></xsl:stylesheet>", null, "XTSE0125"),
      ok ("key with a collation", "<xsl:key name=\"k\" match=\"a\" use=\"@k\" collation=\"" + CI + "\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k', 'ABC')\"/></xsl:template>", "<r><a k=\"abc\">1</a></r>", "1"),
      ok ("key under the default collation", "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + " default-collation=\"" + CI + "\"><xsl:output method=\"text\"/>\n" +
        "<xsl:key name=\"k\" match=\"a\" use=\"@k\"/>\n" +
        "<xsl:template match=\"/\"><xsl:value-of select=\"key('k', 'ABC')\"/></xsl:template></xsl:stylesheet>", "<r><a k=\"abc\">1</a></r>", "1"));
  }

}
