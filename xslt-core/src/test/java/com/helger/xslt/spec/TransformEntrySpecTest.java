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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;

/**
 * Port of go-xslt's <code>internal/xslt/transform_entry_test.go</code>: the
 * entry points of XSLT 3.0 §2.3 through {@link XsltTransformRequest}. go-xslt
 * passes parameters, context items and match selections as XPath expressions;
 * here they are evaluated up front (against the source document, if any).
 *
 * @author Philip Helger
 */
public final class TransformEntrySpecTest
{
  private static final String ENTRY_SHEET = "<xsl:stylesheet version=\"3.0\" " + SPEC_NS + ">\n" + """
      <xsl:output method="text"/>
      <xsl:param name="g" select="'G'"/>
      <xsl:param name="typed" as="xs:integer" select="0"/>
      <xsl:template name="main"><xsl:param name="p" select="'dp'"/><xsl:param name="t" tunnel="yes" select="'dt'"/>main:<xsl:value-of select="$p, $g"/>:<xsl:call-template name="inner"/></xsl:template>
      <xsl:template name="inner"><xsl:param name="t" tunnel="yes" select="'none'"/><xsl:value-of select="$t"/></xsl:template>
      <xsl:template name="f:main">ns-main</xsl:template>
      <xsl:template name="req"><xsl:param name="r" required="yes"/><xsl:value-of select="$r"/></xsl:template>
      <xsl:template name="ctx"><xsl:value-of select="name(.)"/></xsl:template>
      <xsl:template match="/">root:<xsl:value-of select="$g"/></xsl:template>
      <xsl:template match="*" mode="m">m:<xsl:value-of select="name()"/></xsl:template>
      <xsl:template match=".[. instance of xs:integer]">i<xsl:value-of select="."/></xsl:template>
      <xsl:template match="b">B</xsl:template>
      <xsl:function name="f:add" visibility="public"><xsl:param name="a"/><xsl:param name="b"/><xsl:sequence select="$a + $b"/></xsl:function>
      <xsl:function name="f:nodes" visibility="public"><e>1</e><e>2</e></xsl:function>
      <xsl:function name="f:private"><xsl:sequence select="1"/></xsl:function>
      <xsl:template name="typed"><xsl:value-of select="$typed + 1"/></xsl:template>
      </xsl:stylesheet>""";

  /** One entry-point case. */
  private record EntryCase (@NonNull String name,
                            @Nullable String src,
                            @NonNull Consumer <Entry> setup,
                            @Nullable String want,
                            @Nullable String code)
  {}

  /** The request being built, with access to the parsed source. */
  private static final class Entry
  {
    private final XsltTransformRequest m_aRequest = new XsltTransformRequest ();
    private final IXdmNode m_aSource;

    Entry (@Nullable final IXdmNode aSource)
    {
      m_aSource = aSource;
      if (aSource != null)
        m_aRequest.setGlobalContextItem (aSource);
    }

    /**
     * @param sExpr
     *        An XPath expression, evaluated with the source document (if any)
     *        as context item.
     * @return Its value.
     */
    @NonNull
    XdmSequence eval (@NonNull final String sExpr)
    {
      final StaticContext aSC = new StaticContext ();
      final CompiledXPath aXP = XPathCompiler.compile (sExpr, aSC);
      return aXP.evaluate (aXP.createDynamicContext ().withFocus (m_aSource, 1, 1));
    }

    @NonNull
    IXdmItem evalItem (@NonNull final String sExpr)
    {
      return eval (sExpr).get (0);
    }
  }

  @NonNull
  private static XdmQName _qn (@NonNull final String sClark)
  {
    final int nClose = sClark.indexOf ('}');
    return nClose > 0 ? new XdmQName (sClark.substring (1, nClose), sClark.substring (nClose + 1)) : new XdmQName (sClark);
  }

  @NonNull
  private static EntryCase _ok (@NonNull final String sName,
                                @Nullable final String sSrc,
                                @NonNull final Consumer <Entry> aSetup,
                                @NonNull final String sWant)
  {
    return new EntryCase (sName, sSrc, aSetup, sWant, null);
  }

  @NonNull
  private static EntryCase _err (@NonNull final String sName,
                                 @Nullable final String sSrc,
                                 @NonNull final Consumer <Entry> aSetup,
                                 @NonNull final String sCode)
  {
    return new EntryCase (sName, sSrc, aSetup, null, sCode);
  }

  private static void _runAll (@NonNull final XsltExecutable aExec, @NonNull final EntryCase... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final EntryCase aCase : aCases)
    {
      try
      {
        final Entry aEntry = new Entry (aCase.src () == null ? null : XsltTestHelper.parse (aCase.src ()));
        aCase.setup ().accept (aEntry);
        final String sOut = aExec.transform (aEntry.m_aRequest).principalSerialized ();
        if (aCase.code () != null)
          aErrors.add (aCase.name () + ": want " + aCase.code () + ", got output '" + sOut + "'");
        else
          if (!sOut.equals (aCase.want ()))
            aErrors.add (aCase.name () + ": got '" + sOut + "' want '" + aCase.want () + "'");
      }
      catch (final XPathException ex)
      {
        if (aCase.code () == null || !aCase.code ().equals (ex.getCode ()))
          aErrors.add (aCase.name () + ": want " + aCase.code () + ", got " + ex.getCode () + ": " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  @Test
  public void entrySpec ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (ENTRY_SHEET, new XsltCompileOptions ());
    final String sSrc = "<a><b/><c/></a>";
    _runAll (aExec,
             _ok ("initial template, no source", null, e -> e.m_aRequest.setInitialTemplate (_qn ("main")), "main:dp G:none"),
             _ok ("initial template with parameters", null, e -> {
               e.m_aRequest.setInitialTemplate (_qn ("main"));
               e.m_aRequest.templateParams ().add (new XsltTransformRequest.TemplateParam (_qn ("p"), e.eval ("'x'"), false));
               e.m_aRequest.templateParams ().add (new XsltTransformRequest.TemplateParam (_qn ("t"), e.eval ("'tun'"), true));
               e.m_aRequest.params ().put (_qn ("g"), XdmSequence.of (XdmAtomic.untyped ("host")));
             }, "main:x host:tun"),
             _ok ("initial template by Clark name", null, e -> e.m_aRequest.setInitialTemplate (_qn ("{urn:f}main")), "ns-main"),
             _err ("unknown initial template", null, e -> e.m_aRequest.setInitialTemplate (_qn ("nope")), "XTDE0040"),
             // XTDE0700: a required parameter not supplied.
             _err ("required template parameter missing", null, e -> e.m_aRequest.setInitialTemplate (_qn ("req")), "XTDE0700"),
             _ok ("required template parameter supplied", null, e -> {
               e.m_aRequest.setInitialTemplate (_qn ("req"));
               e.m_aRequest.templateParams ().add (new XsltTransformRequest.TemplateParam (_qn ("r"), e.eval ("42"), false));
             }, "42"),
             _ok ("initial template with a context item", sSrc, e -> {
               e.m_aRequest.setInitialTemplate (_qn ("ctx"));
               e.m_aRequest.setGlobalContextItem (e.evalItem ("/a/b"));
             }, "b"),
             // §2.3 initial mode.
             _ok ("default entry", sSrc, e -> {}, "root:G"),
             _ok ("initial mode", sSrc, e -> e.m_aRequest.setInitialMode (_qn ("m")), "m:a"),
             _ok ("initial mode with context item", sSrc, e -> {
               e.m_aRequest.setInitialMode (_qn ("m"));
               e.m_aRequest.setInitialMatchSelection (e.eval ("/a/c"));
             }, "m:c"),
             _ok ("#unnamed initial mode", sSrc, e -> e.m_aRequest.setInitialMode (_qn ("#unnamed")), "root:G"),
             _err ("undeclared initial mode", sSrc, e -> e.m_aRequest.setInitialMode (_qn ("zz")), "XTDE0045"),
             _ok ("initial match selection of atomic values", null, e -> e.m_aRequest.setInitialMatchSelection (e.eval ("(1, 2)")), "i1i2"),
             _ok ("initial match selection of nodes", sSrc, e -> e.m_aRequest.setInitialMatchSelection (e.eval ("//b")), "B"),
             // XTDE0044: no initial match selection and no
             // xsl:initial-template.
             _err ("no source and no initial template", null, e -> {}, "XTDE0044"),
             // §2.3 initial function.
             _ok ("initial function", null, e -> {
               e.m_aRequest.setInitialFunction (_qn ("{urn:f}add"));
               e.m_aRequest.functionArgs ().add (e.eval ("1"));
               e.m_aRequest.functionArgs ().add (e.eval ("2"));
             }, "3"),
             _ok ("initial function returning nodes", null, e -> e.m_aRequest.setInitialFunction (_qn ("{urn:f}nodes")), "12"),
             _err ("initial function wrong arity", null, e -> {
               e.m_aRequest.setInitialFunction (_qn ("{urn:f}add"));
               e.m_aRequest.functionArgs ().add (e.eval ("1"));
             }, "XTDE0041"),
             // Only a public or final function is an entry point.
             _err ("private initial function", null, e -> e.m_aRequest.setInitialFunction (_qn ("{urn:f}private")), "XTDE0041"),
             // Global parameters supplied as typed values keep their type.
             _ok ("typed global parameter", null, e -> {
               e.m_aRequest.setInitialTemplate (_qn ("typed"));
               e.m_aRequest.params ().put (_qn ("typed"), e.eval ("41"));
             }, "42"));
  }

  @Test
  public void entrySpecFunctionValue ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (ENTRY_SHEET, new XsltCompileOptions ());
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setInitialFunction (_qn ("{urn:f}add"));
    aRequest.functionArgs ().add (XdmSequence.of (XdmAtomic.integer (40)));
    aRequest.functionArgs ().add (XdmSequence.of (XdmAtomic.integer (2)));
    final XsltTransformResult aResult = aExec.transform (aRequest);
    final XdmSequence aValue = aResult.principalRaw ();
    assertEquals (1, aValue.size ());
    if (!(aValue.get (0) instanceof final XdmAtomic aAtomic))
      throw new AssertionError ("Not an atomic value: " + aValue.get (0));
    assertTrue (aAtomic.getType ().isNumeric ());
    assertEquals ("42", aAtomic.getLexical ());
  }

  /**
   * XSLT 3.0 §5.3 xsl:global-context-item and §2.3 xsl:initial-template,
   * through the entry points.
   */
  @Test
  public void entrySpecGlobalContextItem ()
  {
    final Object [] [] aCases = { { "xsl:initial-template runs without a source",
                                    "<xsl:template name=\"xsl:initial-template\">init</xsl:template>",
                                    null,
                                    null,
                                    "init",
                                    null },
                                  { "global context item as element()", """
                                      <xsl:global-context-item as="element()"/><xsl:template match="/">x</xsl:template>""", "<doc/>", null, null, "XTTE0590" },
                                  { "global context item as document-node()", """
                                      <xsl:global-context-item as="document-node()" use="required"/><xsl:template match="/">x</xsl:template>""", "<doc/>", null, "x", null },
                                  { "required global context item absent", """
                                      <xsl:global-context-item use="required"/><xsl:template name="main">x</xsl:template>""", null, "main", null, "XTDE3086" },
                                  { "global variables see the global context item", """
                                      <xsl:global-context-item use="required"/><xsl:variable name="n" select="string(/doc/@n)"/><xsl:template name="main"><xsl:value-of select="$n"/></xsl:template>""", "<doc n='3'/>", "main", "3", null } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Object [] aCase : aCases)
    {
      try
      {
        final XsltExecutable aExec = XsltCompiler.compile (specSheet ((String) aCase[1]), new XsltCompileOptions ());
        final XsltTransformRequest aRequest = new XsltTransformRequest ();
        if (aCase[2] != null)
          aRequest.setGlobalContextItem (XsltTestHelper.parse ((String) aCase[2]));
        if (aCase[3] != null)
          aRequest.setInitialTemplate (_qn ((String) aCase[3]));
        final String sOut = aExec.transform (aRequest).principalSerialized ();
        if (aCase[5] != null)
          aErrors.add (aCase[0] + ": want " + aCase[5] + ", got output '" + sOut + "'");
        else
          if (!sOut.equals (aCase[4]))
            aErrors.add (aCase[0] + ": got '" + sOut + "' want '" + aCase[4] + "'");
      }
      catch (final XPathException ex)
      {
        if (aCase[5] == null || !aCase[5].equals (ex.getCode ()))
          aErrors.add (aCase[0] + ": want " + aCase[5] + ", got " + ex.getCode () + ": " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * XSLT 3.0 §9.5 global variables and parameters: circularity and required
   * parameters.
   */
  @Test
  public void entrySpecGlobals ()
  {
    specRunAll (ok ("forward reference between globals", """
        <xsl:variable name="a" select="$b + 1"/><xsl:variable name="b" select="2"/>
        <xsl:template match="/"><xsl:value-of select="$a"/></xsl:template>""", null, "3"),
                // XTDE0640: a circular definition that is actually evaluated.
                err ("circular globals", """
                    <xsl:variable name="a" select="$b"/><xsl:variable name="b" select="$a"/>
                    <xsl:template match="/"><xsl:value-of select="$a"/></xsl:template>""", null, "XTDE0640"),
                err ("circular through a function", """
                    <xsl:variable name="a" select="f:g()"/>
                    <xsl:function name="f:g"><xsl:sequence select="$a"/></xsl:function>
                    <xsl:template match="/"><xsl:value-of select="$a"/></xsl:template>""", null, "XTDE0640"),
                // XTTE0570: a global's value does not match its type.
                err ("global variable type mismatch", """
                    <xsl:variable name="v" as="xs:integer" select="'abc'"/>
                    <xsl:template match="/"><xsl:value-of select="$v"/></xsl:template>""", null, "XTTE0570"),
                ok ("global variable with content and as", """
                    <xsl:variable name="v" as="xs:integer"><xsl:value-of select="2"/></xsl:variable>
                    <xsl:template match="/"><xsl:value-of select="$v"/></xsl:template>""", null, "2"),
                ok ("global variable from the source document", """
                    <xsl:variable name="n" select="/r/@n"/>
                    <xsl:template match="/"><xsl:value-of select="$n"/></xsl:template>""", "<r n=\"5\"/>", "5"),
                ok ("local variable shadows a global", """
                    <xsl:variable name="v" select="'global'"/>
                    <xsl:template match="/"><xsl:variable name="v" select="'local'"/><xsl:value-of select="$v"/></xsl:template>""", null, "local"));
  }
}
