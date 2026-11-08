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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt's <code>internal/xslt/usewhen_test.go</code>: conditional
 * element inclusion ([xsl:]use-when, XSLT 3.0 §3.13.1), shadow attributes
 * (§3.13.2), static variables/parameters (§9.6) and the restricted context
 * static expressions are evaluated in (§9.7), all within a single stylesheet
 * module. Multi-module behaviour is in {@link StaticdeclSpecTest}.
 * <p>
 * go-xslt checks some errors only for "an error without another code"
 * (TestUseWhenStaticErrorsUncoded) because its XPath errors had no code; the
 * Java engine always has one, so the exact code is required here.
 *
 * @author Philip Helger
 */
public final class UsewhenSpecTest
{
  static final String SWB_HEAD = """
      <xsl:stylesheet version="3.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:my="urn:my">
      <xsl:output method="text"/>
      """;

  /**
   * One case: name, declarations, expected output or error code, and the host
   * static parameters (name to XPath expression).
   */
  record Case (@NonNull String name,
               @NonNull String decls,
               @Nullable String want,
               @Nullable String code,
               @Nullable Map <XdmQName, String> statics)
  {}

  @NonNull
  static Case ok (@NonNull final String sName, @NonNull final String sDecls, @NonNull final String sWant)
  {
    return new Case (sName, sDecls, sWant, null, null);
  }

  @NonNull
  static Case ok (@NonNull final String sName,
                  @NonNull final String sDecls,
                  @NonNull final Map <XdmQName, String> aStatics,
                  @NonNull final String sWant)
  {
    return new Case (sName, sDecls, sWant, null, aStatics);
  }

  @NonNull
  static Case err (@NonNull final String sName, @NonNull final String sDecls, @NonNull final String sCode)
  {
    return new Case (sName, sDecls, null, sCode, null);
  }

  @NonNull
  static Case err (@NonNull final String sName,
                   @NonNull final String sDecls,
                   @NonNull final Map <XdmQName, String> aStatics,
                   @NonNull final String sCode)
  {
    return new Case (sName, sDecls, null, sCode, aStatics);
  }

  /**
   * Wrap declarations in a 3.0 stylesheet with text output.
   *
   * @param sDecls
   *        Declarations.
   * @return The stylesheet.
   */
  @NonNull
  static String swBSheet (@NonNull final String sDecls)
  {
    return SWB_HEAD + sDecls + "\n</xsl:stylesheet>";
  }

  /**
   * Compile a stylesheet (optionally located at a base URI and with host
   * static parameters given as XPath expressions) and run it against
   * <code>&lt;doc&gt;&lt;item/&gt;&lt;/doc&gt;</code>.
   *
   * @param sSheet
   *        Stylesheet.
   * @param sBaseUri
   *        Base URI of the stylesheet or <code>null</code>.
   * @param aStatics
   *        Static parameters or <code>null</code>.
   * @param sSrc
   *        Source document.
   * @return The serialized result.
   */
  @NonNull
  static String swBRun (@NonNull final String sSheet,
                        @Nullable final String sBaseUri,
                        @Nullable final Map <XdmQName, String> aStatics,
                        @NonNull final String sSrc)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    if (sBaseUri != null)
    {
      aOptions.setBaseUri (sBaseUri);
      aOptions.setResolver (FileResourceResolver.INSTANCE);
    }
    if (aStatics != null)
      for (final Map.Entry <XdmQName, String> aEntry : aStatics.entrySet ())
      {
        final CompiledXPath aXP = XPathCompiler.compile (aEntry.getValue (), new StaticContext ());
        aOptions.setStaticParam (aEntry.getKey (), aXP.evaluate (aXP.createDynamicContext ()));
      }
    final XsltExecutable aExec = XsltCompiler.compile (sSheet, aOptions);
    return aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSrc))).principalSerialized ();
  }

  /**
   * Run all cases (declarations wrapped with {@link #swBSheet(String)} unless
   * they are a complete stylesheet) and fail with all mismatches.
   *
   * @param sBaseUri
   *        Base URI of the stylesheet or <code>null</code>.
   * @param aCases
   *        Cases.
   */
  static void swBRunAll (@Nullable final String sBaseUri, @NonNull final Case... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Case aCase : aCases)
    {
      final String sSheet = aCase.decls ().startsWith ("<xsl:stylesheet") ? aCase.decls () : swBSheet (aCase.decls ());
      try
      {
        final String sGot = swBRun (sSheet, sBaseUri, aCase.statics (), "<doc><item/></doc>");
        if (aCase.code () != null)
          aErrors.add (aCase.name () + ": no error, want " + aCase.code () + " (output '" + sGot + "')");
        else
          if (!sGot.equals (aCase.want ()))
            aErrors.add (aCase.name () + ": got '" + sGot + "', want '" + aCase.want () + "'");
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

  @NonNull
  static Map <XdmQName, String> statics (@NonNull final String... aNameExprPairs)
  {
    final ICommonsOrderedMap <XdmQName, String> ret = new CommonsLinkedHashMap <> ();
    for (int i = 0; i < aNameExprPairs.length; i += 2)
    {
      final String sName = aNameExprPairs[i];
      // "{uri}local" stands for a name in a namespace
      final int nClose = sName.indexOf ('}');
      ret.put (nClose > 0 ? new XdmQName (sName.substring (1, nClose), sName.substring (nClose + 1)) : new XdmQName (sName),
               aNameExprPairs[i + 1]);
    }
    return ret;
  }

  @Test
  public void useWhenInclusion ()
  {
    swBRunAll (null,
               // §3.13.1: an element whose use-when is false is excluded with
               // its subtree; the remaining template rule handles "/".
               ok ("template excluded", """
                   <xsl:template match="/" priority="2" use-when="false()">X</xsl:template>
                   <xsl:template match="/">Y</xsl:template>""", "Y"),
               ok ("template kept", """
                   <xsl:template match="/" priority="2" use-when="true()">X</xsl:template>
                   <xsl:template match="/">Y</xsl:template>""", "X"),
               // The effective boolean value decides: a non-empty string is
               // true, the empty sequence false.
               ok ("effective boolean value", """
                   <xsl:template match="/"><xsl:text use-when="'a'">1</xsl:text><xsl:text use-when="()">2</xsl:text></xsl:template>""", "1"),
               // xsl:use-when on a literal result element.
               ok ("literal result element", """
                   <xsl:template match="/"><a xsl:use-when="false()">X</a><b xsl:use-when="true()">Z</b></xsl:template>""", "Z"),
               // "no static or dynamic errors will be reported in respect of the
               // element and its contents"
               ok ("errors inside excluded element", """
                   <xsl:template match="/" priority="2" use-when="false()"><xsl:no-such-instruction/><xsl:value-of select="(("/></xsl:template>
                   <xsl:template match="/">ok</xsl:template>""", "ok"),
               // Nested exclusion inside a kept element.
               ok ("nested", """
                   <xsl:template match="/"><xsl:if test="true()">a<xsl:if test="true()" use-when="false()">b</xsl:if>c</xsl:if></xsl:template>""", "ac"),
               // A static parameter is in scope for a later use-when (§9.6).
               ok ("static param in use-when", """
                   <xsl:param name="debug" static="yes" select="true()"/>
                   <xsl:template match="/"><xsl:text use-when="$debug">D</xsl:text><xsl:text use-when="not($debug)">N</xsl:text></xsl:template>""", "D"),
               // A prefixed static variable name resolves through the in-scope
               // namespaces of the referring element.
               ok ("prefixed static variable", """
                   <xsl:variable name="my:x" static="yes" select="3"/>
                   <xsl:template match="/"><xsl:text use-when="$my:x = 3">three</xsl:text></xsl:template>""", "three"),
               // A later static variable sees an earlier one; both are also
               // ordinary global variables at run time (§9.6).
               ok ("chained statics, runtime value", """
                   <xsl:variable name="a" static="yes" select="2"/>
                   <xsl:variable name="b" static="yes" select="$a * 10"/>
                   <xsl:template match="/"><xsl:value-of select="$b"/></xsl:template>""", "20"),
               // A static declaration's own use-when: false excludes it.
               ok ("use-when on static declaration", """
                   <xsl:variable name="v" static="yes" select="'one'" use-when="false()"/>
                   <xsl:variable name="v" static="yes" select="'two'" use-when="true()"/>
                   <xsl:template match="/"><xsl:value-of select="$v"/></xsl:template>""", "two"),
               // The as attribute applies the function conversion rules to the
               // static value: xs:integer is promoted to xs:double.
               ok ("as conversion", """
                   <xsl:variable name="d" static="yes" as="xs:double" select="1"/>
                   <xsl:template match="/"><xsl:value-of select="$d instance of xs:double"/></xsl:template>""", "true"),
               // A static param with no select and empty content defaults to the
               // zero-length string (§9.3), also inside a shadow attribute.
               ok ("param without select defaults to empty string", """
                   <xsl:param name="p" static="yes"/>
                   <xsl:template match="/"><xsl:value-of _select="'[{$p}]'" select="'fallback'"/></xsl:template>""", "[]"));
  }

  /**
   * The functions §9.7 puts in the static context, and their answers, from
   * inside use-when expressions. Each case is a boolean expression that must
   * be true; it selects "T" over "F".
   */
  @Test
  public void useWhenStaticFunctions ()
  {
    final String [] aExprs = {
                               // system-property (§20.4.3)
                               "system-property('xsl:version') = '3.0'",
                               "system-property('Q{http://www.w3.org/1999/XSL/Transform}version') = '3.0'",
                               "system-property('Q{urn:not-xslt}version') = ''",
                               "system-property('my:anything') = ''",
                               "system-property('xsl:supports-dynamic-evaluation') = ('yes', 'no')",
                               // element-available
                               "element-available('xsl:for-each')",
                               "element-available('Q{http://www.w3.org/1999/XSL/Transform}iterate')",
                               "not(element-available('xsl:no-such-instruction'))",
                               "not(element-available('my:extension'))",
                               // function-available: stylesheet functions and the
                               // XSLT functions needing a dynamic XSLT context are
                               // not in the static context (§9.7)
                               "function-available('concat')",
                               "function-available('fn:abs', 1)",
                               "not(function-available('fn:abs', 2))",
                               "function-available('system-property')",
                               "function-available('element-available', 1)",
                               "not(function-available('current'))",
                               "not(function-available('key'))",
                               "not(function-available('my:f'))",
                               "not(function-available('no-such-function'))",
                               // type-available: only built-in types
                               "type-available('xs:integer')",
                               "type-available('xs:untypedAtomic')",
                               "not(type-available('xs:no-such-type'))",
                               "not(type-available('my:t'))",
                               // available-system-properties includes the
                               // mandatory XSLT ones.
                               "QName('http://www.w3.org/1999/XSL/Transform', 'version') = available-system-properties()",
                               "QName('http://www.w3.org/1999/XSL/Transform', 'vendor') = available-system-properties()",
                               // The F&O library is there.
                               "string-join(('a','b'), '-') = 'a-b' and math:pi() gt 3",
                               "map:size(map{1:2}) = 1 and array:size([1,2]) = 2" };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sExpr : aExprs)
    {
      final String sSheet = swBSheet ("\n<xsl:function name=\"my:f\"><xsl:sequence select=\"1\"/></xsl:function>\n" +
                                      "<xsl:template match=\"/\"><xsl:text use-when=\"" +
                                      sExpr +
                                      "\">T</xsl:text><xsl:text use-when=\"not(" +
                                      sExpr +
                                      ")\">F</xsl:text></xsl:template>").replace ("xmlns:my=\"urn:my\"",
                                                                                   "xmlns:my=\"urn:my\" xmlns:math=\"http://www.w3.org/2005/xpath-functions/math\" xmlns:map=\"http://www.w3.org/2005/xpath-functions/map\" xmlns:array=\"http://www.w3.org/2005/xpath-functions/array\" xmlns:fn=\"http://www.w3.org/2005/xpath-functions\"");
      try
      {
        final String sGot = swBRun (sSheet, null, null, "<doc><item/></doc>");
        if (!sGot.equals ("T"))
          aErrors.add ("use-when='" + sExpr + "': got '" + sGot + "', want T");
      }
      catch (final XPathException ex)
      {
        aErrors.add (sExpr + ": " + ex.getCode () + " " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * Errors in static expressions are reported (§9.7: a dynamic error is a
   * static error that keeps its code).
   */
  @Test
  public void useWhenStaticErrors ()
  {
    swBRunAll (null,
               // §9.7: the context item is absent.
               err ("context item", "<xsl:template match=\"/\" use-when=\".\">x</xsl:template>", "XPDY0002"),
               err ("position", "<xsl:template match=\"/\" use-when=\"position() = 1\">x</xsl:template>", "XPDY0002"),
               // Dynamic errors keep their own code.
               err ("dynamic error in use-when", "<xsl:template match=\"/\" use-when=\"1 idiv 0\">x</xsl:template>", "FOAR0001"),
               err ("dynamic error in static select", """
                   <xsl:variable name="v" static="yes" select="1 idiv 0"/>
                   <xsl:template match="/">x</xsl:template>""", "FOAR0001"),
               // §9.6: static="yes" requires empty content.
               err ("static param with content", """
                   <xsl:param name="p" static="yes">text</xsl:param>
                   <xsl:template match="/">x</xsl:template>""", "XTSE0010"),
               err ("static variable with element content", """
                   <xsl:variable name="v" static="yes"><xsl:sequence select="1"/></xsl:variable>
                   <xsl:template match="/">x</xsl:template>""", "XTSE0010"),
               // Invalid names passed to the static-context XSLT functions.
               err ("system-property bad name", "<xsl:template match=\"/\" use-when=\"system-property('1bad')\">x</xsl:template>", "XTDE1390"),
               err ("function-available bad name",
                    "<xsl:template match=\"/\" use-when=\"function-available('1bad')\">x</xsl:template>",
                    "XTDE1400"),
               err ("function-available unbound prefix",
                    "<xsl:template match=\"/\" use-when=\"function-available('nope:f')\">x</xsl:template>",
                    "XTDE1400"),
               err ("element-available bad name", "<xsl:template match=\"/\" use-when=\"element-available('1bad')\">x</xsl:template>", "XTDE1440"),
               // fn:transform's argument must be a single map (function
               // conversion rules, XPath 3.1 §3.1.5.2).
               err ("transform non-map", """
                   <xsl:variable name="r" static="yes" select="transform(1)"/>
                   <xsl:template match="/">x</xsl:template>""", "XPTY0004"));
  }

  /**
   * Static errors for which go-xslt only checks that an error without another
   * code is raised; here the exact code is required.
   */
  @Test
  public void useWhenStaticErrorsUncoded ()
  {
    swBRunAll (null,
               // Only static variables are in scope; an ordinary global is not.
               err ("non-static global", """
                   <xsl:variable name="g" select="1"/>
                   <xsl:template match="/" use-when="$g">x</xsl:template>""", "XPST0008"),
               // A static variable is not in scope within its own declaration,
               // nor before it in tree order.
               err ("self reference", """
                   <xsl:param name="p" static="yes" select="1" use-when="$p"/>
                   <xsl:template match="/">x</xsl:template>""", "XPST0008"),
               err ("forward reference in select", """
                   <xsl:variable name="a" static="yes" select="$b"/>
                   <xsl:variable name="b" static="yes" select="1"/>
                   <xsl:template match="/">x</xsl:template>""", "XPST0008"),
               // "The static context for evaluation of a static expression only
               // contains those static variables ... whose declarations occur
               // prior to the element containing the static expression" (§9.6).
               err ("forward reference in use-when", """
                   <xsl:template match="/" use-when="$late">x</xsl:template>
                   <xsl:variable name="late" static="yes" select="true()"/>""", "XPST0008"),
               err ("forward reference in a nested use-when", """
                   <xsl:template match="/"><xsl:text use-when="$late">x</xsl:text></xsl:template>
                   <xsl:variable name="late" static="yes" select="true()"/>""", "XPST0008"),
               err ("excluded declaration", """
                   <xsl:variable name="v" static="yes" select="1" use-when="false()"/>
                   <xsl:template match="/" use-when="$v">x</xsl:template>""", "XPST0008"),
               // Stylesheet functions are not in the static context.
               err ("stylesheet function", """
                   <xsl:function name="my:f"><xsl:sequence select="true()"/></xsl:function>
                   <xsl:template match="/" use-when="my:f()">x</xsl:template>""", "XPST0017"),
               err ("syntax error", "<xsl:template match=\"/\" use-when=\"1 +\">x</xsl:template>", "XPST0003"));
  }

  /**
   * Externally supplied static parameter values (§9.7 dynamic context: "For
   * static parameters where an external value is supplied: the
   * externally-supplied value of the parameter"). go-xslt passes the host
   * values as XPath expressions and names as lexical QNames; here they are
   * evaluated up front and the names are expanded QNames.
   */
  @Test
  public void staticHostParams ()
  {
    swBRunAll (null,
               ok ("host value replaces default, seen by use-when and at run time", """
                   <xsl:param name="mode" static="yes" select="'default'"/>
                   <xsl:template match="/"><xsl:text use-when="$mode = 'host'">H:</xsl:text><xsl:value-of select="$mode"/></xsl:template>""",
                   statics ("mode", "'host'"),
                   "H:host"),
               ok ("prefixed name resolved against the declaration", """
                   <xsl:param name="my:p" static="yes" select="0"/>
                   <xsl:template match="/"><xsl:value-of select="$my:p"/></xsl:template>""", statics ("{urn:my}p", "1 + 1"), "2"),
               // Only parameters are externally settable; a static variable
               // keeps its own value.
               ok ("static variable is not settable", """
                   <xsl:variable name="v" static="yes" select="'decl'"/>
                   <xsl:template match="/"><xsl:value-of select="$v"/></xsl:template>""", statics ("v", "'host'"), "decl"),
               // A supplied value is converted to the declared type with the
               // function conversion rules (xs:integer is an xs:decimal).
               ok ("supplied value converted to as", """
                   <xsl:param name="n" static="yes" as="xs:decimal" select="0.5"/>
                   <xsl:template match="/"><xsl:value-of select="$n instance of xs:decimal, $n"/></xsl:template>""",
                   statics ("n", "3"),
                   "true 3"),
               // The supplied value reaches the shadow attribute: the §3.13.2
               // "parameterize selection" example.
               ok ("host value in shadow attribute", """
                   <xsl:param name="filter" static="yes" as="xs:string" select="'true()'"/>
                   <xsl:template match="/"><xsl:value-of _select="count(//*[{$filter}])"/></xsl:template>""",
                   statics ("filter", "'self::item'"),
                   "1"),
               // Unknown host names are ignored.
               ok ("undeclared name ignored", "<xsl:template match=\"/\">ok</xsl:template>", statics ("nobody", "1"), "ok"),
               // §9.2.1: the conversion of a supplied parameter value to its
               // required type failing is XTTE0590.
               err ("type mismatch",
                    "<xsl:param name=\"n\" static=\"yes\" as=\"xs:integer\" select=\"1\"/><xsl:template match=\"/\">x</xsl:template>",
                    statics ("n", "'abc'"),
                    "XTTE0590"),
               // XTSE0010 is a property of the declaration: a supplied value does
               // not excuse non-empty content.
               err ("content despite host value",
                    "<xsl:param name=\"n\" static=\"yes\">1</xsl:param><xsl:template match=\"/\">x</xsl:template>",
                    statics ("n", "1"),
                    "XTSE0010"));
    // go-xslt's "supplied expression fails" (FOAR0001 from the host value
    // '1 idiv 0') is a property of its expression-valued host API; here the
    // host supplies values, so the case is not ported.
  }

  /** §3.13.2 shadow attributes. */
  @Test
  public void shadowAttributes ()
  {
    swBRunAll (null,
               // The shadow value template supplies the expression text.
               ok ("select from static param", """
                   <xsl:param name="expr" static="yes" select="'1+2'"/>
                   <xsl:template match="/"><xsl:value-of _select="{$expr}"/></xsl:template>""", "3"),
               // "If a shadow attribute is present, then any attribute node with
               // name N ... is ignored" - even an invalid one.
               ok ("non-shadow attribute ignored, even if invalid",
                   "<xsl:template match=\"/\"><xsl:value-of select=\"((\" _select=\"'ok'\"/></xsl:template>",
                   "ok"),
               // A shadow attribute without curly braces is a plain value.
               ok ("constant shadow", "<xsl:template match=\"/\"><xsl:value-of _select=\"'lit'\"/></xsl:template>", "lit"),
               // A sequence in an AVT part is joined with single spaces.
               ok ("sequence joined", "<xsl:template match=\"/\"><xsl:value-of _select=\"'{(1, 2, 3)}'\"/></xsl:template>", "1 2 3"),
               // _use-when is itself a shadow attribute.
               ok ("shadow use-when", """
                   <xsl:param name="on" static="yes" select="'false()'"/>
                   <xsl:template match="/"><xsl:text _use-when="{$on}">X</xsl:text>Y</xsl:template>""", "Y"),
               // _static on xsl:variable (explicitly named in §3.13.2).
               ok ("shadow static", """
                   <xsl:param name="mk" static="yes" select="'yes'"/>
                   <xsl:variable name="v" _static="{$mk}" select="true()"/>
                   <xsl:template match="/"><xsl:text use-when="$v">S</xsl:text></xsl:template>""", "S"),
               // An excluded element's shadow attributes are never evaluated.
               ok ("shadow inside excluded element",
                   "<xsl:template match=\"/\"><xsl:if test=\"true()\" use-when=\"false()\"><xsl:value-of _select=\"{1 idiv 0}\"/></xsl:if>ok</xsl:template>",
                   "ok"),
               // On a literal result element an underscore attribute is an
               // ordinary attribute copied to the result.
               ok ("literal result element keeps underscore attribute", """
                   <xsl:variable name="x" as="element()"><out _one="1"/></xsl:variable>
                   <xsl:template match="/"><xsl:value-of select="name($x/@*), string($x/@*)"/></xsl:template>""", "_one 1"),
               err ("dynamic error in shadow", "<xsl:template match=\"/\"><xsl:value-of _select=\"{1 idiv 0}\"/></xsl:template>", "FOAR0001"),
               err ("context item in shadow", "<xsl:template match=\"/\"><xsl:value-of _select=\"{.}\"/></xsl:template>", "XPDY0002"),
               // Only static variables are in scope (§9.7).
               err ("non-static variable in shadow", """
                   <xsl:variable name="g" select="'1'"/>
                   <xsl:template match="/"><xsl:value-of _select="{$g}"/></xsl:template>""", "XPST0008"),
               // The principal module's root element is expanded before any of
               // its declarations: a static parameter declared inside is not yet
               // in scope there (§3.13.2).
               err ("root shadow sees no inner static", """
                   <xsl:stylesheet _version="{$v}" version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                   <xsl:param name="v" static="yes" select="'3.0'"/>
                   <xsl:template match="/">x</xsl:template></xsl:stylesheet>""", "XPST0008"));
  }

  /**
   * fn:transform is an F&amp;O 3.1 function, so it is in the static context
   * (§9.7) and a static variable may run one.
   */
  @Test
  public void useWhenStaticTransform ()
  {
    swBRunAll (null, ok ("static transform", """
        <xsl:variable name="inner" static="yes" select="'&lt;xsl:stylesheet version=&quot;3.0&quot; xmlns:xsl=&quot;http://www.w3.org/1999/XSL/Transform&quot;&gt;&lt;xsl:template name=&quot;main&quot;&gt;hi&lt;/xsl:template&gt;&lt;/xsl:stylesheet&gt;'"/>
        <xsl:variable name="r" static="yes" select="string(transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'main')})?output)"/>
        <xsl:template match="/"><xsl:text use-when="$r = 'hi'">yes</xsl:text><xsl:text use-when="$r != 'hi'">no</xsl:text></xsl:template>""", "yes"));
  }

  /**
   * "If the xsl:package, xsl:stylesheet or xsl:transform element itself is
   * effectively excluded, the effect is to exclude all the children of the
   * xsl:stylesheet or xsl:transform element, but not the xsl:stylesheet or
   * xsl:transform element or its attributes" (XSLT 3.0 §3.13.1) - for the
   * principal module too. With every template gone the built-in rules copy the
   * text through.
   */
  @Test
  public void useWhenOnPrincipalRoot ()
  {
    final String sGot = swBRun ("""
        <xsl:stylesheet version="3.0" use-when="false()" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
        <xsl:output method="text"/>
        <xsl:template match="/">template</xsl:template>
        </xsl:stylesheet>""", null, null, "<doc>text</doc>");
    assertTrue (sGot, sGot.endsWith ("text"));
    assertFalse (sGot, sGot.contains ("template"));
    // A true use-when keeps the children.
    assertEquals ("kept",
                  swBRun (swBSheet ("<xsl:template match=\"/\">kept</xsl:template>").replaceFirst ("version=\"3.0\"",
                                                                                                    "version=\"3.0\" use-when=\"true()\""),
                          null,
                          null,
                          "<doc><item/></doc>"));
  }
}
