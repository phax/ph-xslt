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
package com.helger.xpath.spec;

import static com.helger.xpath.spec.SpecTestHelper.parse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.pattern.CompiledXsltPattern;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.pattern.XsltPatternParser;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Specification tests of XSLT 3.0 match patterns, ported from go-xslt's
 * <code>pattern_match_test.go</code>.
 *
 * @author Philip Helger
 */
public final class PatternMatchSpecTest
{
  private static final XdmQName VAR_V = new XdmQName ("v");
  private static final XdmQName VAR_AS = new XdmQName ("as");
  private static final XdmQName VAR_F = new XdmQName ("f");

  private static final String PAT_DOC = "<r xmlns:p=\"urn:p\"><a xml:id=\"a1\"><b n=\"1\"/><c/><b n=\"2\"/><b n=\"3\"/></a>" +
                                        "<a xml:id=\"a2\"><b n=\"4\"/>t<?pi x?><!--k--></a><p:q/></r>";

  /**
   * The static context of the patterns: namespace p, the variables v, as and
   * f, and stand-ins for the XSLT functions and for the unknown functions f,
   * g and h that go-xslt leaves unresolved at parse time.
   */
  @NonNull
  private static StaticContext _createStaticContext ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    aLib.register (XdmConstants.NS_FN, "key", "(xs:string, xs:anyAtomicType*) as node()*", (aCtx, aArgs) -> XdmSequence.EMPTY, true);
    aLib.register (XdmConstants.NS_FN, "current-group", "() as item()*", (aCtx, aArgs) -> XdmSequence.EMPTY);
    aLib.register (XdmConstants.NS_FN, "current-grouping-key", "() as xs:anyAtomicType*", (aCtx, aArgs) -> XdmSequence.EMPTY);
    aLib.register (XdmConstants.NS_FN, "f", "(item()*) as item()*", (aCtx, aArgs) -> aArgs.get (0));
    aLib.register (XdmConstants.NS_FN, "g", "() as item()*", (aCtx, aArgs) -> XdmSequence.EMPTY);
    aLib.register (XdmConstants.NS_FN, "f", "(item()*, item()*) as item()*", (aCtx, aArgs) -> aArgs.get (0));
    aLib.register (XdmConstants.NS_FN, "h", "(item()*, item()*) as item()*", (aCtx, aArgs) -> aArgs.get (0));
    aLib.register ("urn:x", "f", "() as item()*", (aCtx, aArgs) -> XdmSequence.EMPTY);
    final StaticContext ret = new StaticContext ();
    ret.setFunctionLibrary (aLib);
    ret.declareNamespace ("p", "urn:p");
    ret.declareNamespace ("x", "urn:x");
    ret.declareVariable (VAR_V);
    ret.declareVariable (VAR_AS);
    ret.declareVariable (VAR_F);
    return ret;
  }

  @NonNull
  private static XsltPattern _pattern (@NonNull final String sPattern)
  {
    return XsltPatternParser.parse (sPattern, _createStaticContext ());
  }

  /** @return The single node the expression selects from the document. */
  @NonNull
  private static XdmNode _select (@NonNull final XdmNode aDoc, @NonNull final String sExpr)
  {
    final StaticContext aSC = _createStaticContext ();
    final XdmSequence aSeq = XPathCompiler.compile (sExpr, aSC)
                                          .evaluate (new DynamicContext (aSC).withFocus (aDoc, 1, 1)
                                                                             .withVariable (VAR_V, XdmSequence.EMPTY)
                                                                             .withVariable (VAR_AS, XdmSequence.EMPTY)
                                                                             .withVariable (VAR_F, XdmSequence.EMPTY));
    assertEquals (sExpr, 1, aSeq.size ());
    return (XdmNode) aSeq.get (0);
  }

  private static void _checkMatches (@NonNull final XdmNode aDoc,
                                     @NonNull final DynamicContext aCtx,
                                     @NonNull final Object [] [] aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Object [] aCase : aCases)
    {
      final String sPattern = (String) aCase[0];
      final String sNode = (String) aCase[1];
      final boolean bWant = ((Boolean) aCase[2]).booleanValue ();
      try
      {
        final XsltPattern aPattern = _pattern (sPattern);
        if (!sPattern.equals (aPattern.getSource ()))
          aErrors.add ("getSource () = '" + aPattern.getSource () + "', want '" + sPattern + "'");
        if (aPattern.matches (_select (aDoc, sNode), aCtx) != bWant)
          aErrors.add ("'" + sPattern + "' on " + sNode + " = " + !bWant + ", want " + bWant);
      }
      catch (final XPathException ex)
      {
        aErrors.add ("'" + sPattern + "' on " + sNode + ": " + ex.getCode () + " " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  /**
   * Checks XSLT 3.0 §5.5.3 "The Meaning of a Pattern": a node N matches
   * pattern P if N is in the result of evaluating P (as an expression, with
   * the pattern-specific axis rules) relative to some ancestor-or-self of N.
   */
  @Test
  public void spPatternMatch ()
  {
    final XdmNode aDoc = parse (PAT_DOC);
    final XdmSequence aAs = XdmSequence.of (_select (aDoc, "/r/a[1]"), _select (aDoc, "/r/a[2]"));
    final DynamicContext aCtx = new DynamicContext (_createStaticContext ()).withFocus (aDoc, 1, 1)
                                                                            .withVariable (VAR_V,
                                                                                           XdmSequence.of (_select (aDoc,
                                                                                                                    "/r/a[1]/b[1]")))
                                                                            .withVariable (VAR_AS, aAs)
                                                                            .withVariable (VAR_F, XdmSequence.EMPTY);
    final Boolean T = Boolean.TRUE;
    final Boolean F = Boolean.FALSE;
    _checkMatches (aDoc,
                   aCtx,
                   new Object [] [] { { "b", "/r/a[1]/b[1]", T },
                                      { "b", "/r/a[1]/c", F },
                                      { "a/b", "/r/a[1]/b[1]", T },
                                      { "r/b", "/r/a[1]/b[1]", F },
                                      { "/r", "/r", T },
                                      { "/r", "/r/a[1]", F },
                                      { "/r/a/b", "/r/a[2]/b", T },
                                      { "/", "/", T },
                                      { "/", "/r", F },
                                      { "//b", "/r/a[2]/b", T },
                                      { "r//b", "/r/a[2]/b", T },
                                      { "c//b", "/r/a[2]/b", F },
                                      { "b[2]", "/r/a[1]/b[@n='2']", T },
                                      { "b[2]", "/r/a[1]/b[@n='3']", F },
                                      { "b[@n='3']", "/r/a[1]/b[@n='3']", T },
                                      // Second predicate: position() among the survivors of the first.
                                      { "b[@n!='1'][2]", "/r/a[1]/b[@n='3']", T },
                                      { "b[@n!='1'][2]", "/r/a[1]/b[@n='2']", F },
                                      { "b[position() = last()]", "/r/a[1]/b[@n='3']", T },
                                      { "b[position() = last()]", "/r/a[1]/b[@n='2']", F },
                                      { "@n", "/r/a[1]/b[1]/@n", T },
                                      { "b", "/r/a[1]/b[1]/@n", F },
                                      { "b/@n", "/r/a[1]/b[1]/@n", T },
                                      { "attribute::element()", "/r/a[1]/b[1]", F },
                                      { "attribute::node()", "/r/a[1]/b[1]/@n", T },
                                      { "node()", "/", F },
                                      { "node()", "/r/a[1]/b[1]/@n", F },
                                      { "node()", "/r/a[2]/text()", T },
                                      { "text()", "/r/a[2]/text()", T },
                                      { "comment()", "/r/a[2]/comment()", T },
                                      { "processing-instruction('pi')", "/r/a[2]/processing-instruction()", T },
                                      { "processing-instruction('zz')", "/r/a[2]/processing-instruction()", F },
                                      { "p:q", "/r/p:q", T },
                                      { "p:*", "/r/p:q", T },
                                      { "*:q", "/r/p:q", T },
                                      { "q", "/r/p:q", F },
                                      { "document-node()", "/", T },
                                      { "child::document-node()", "/", F },
                                      { "document-node(element(r))", "/", T },
                                      { "document-node(element(a))", "/", F },
                                      { ".", "/r/a[1]/b[1]/@n", T },
                                      { ".[@n='2']", "/r/a[1]/b[@n='2']", T },
                                      { ".[@n='2']", "/r/a[1]/b[@n='3']", F },
                                      { "id('a2')", "/r/a[2]", T },
                                      { "id('a2')", "/r/a[1]", F },
                                      { "id('a2')/b", "/r/a[2]/b", T },
                                      { "id('a2')/b", "/r/a[1]/b[1]", F },
                                      { "id('a1')//b", "/r/a[1]/b[2]", T },
                                      { "id('a1')//b", "/r/a[2]/b", F },
                                      { "$v", "/r/a[1]/b[1]", T },
                                      { "$v", "/r/a[1]/b[2]", F },
                                      { "$as/b", "/r/a[2]/b", T },
                                      { "$as//b", "/r/a[1]/b[3]", T },
                                      { "$v//b", "/r/a[1]/b[3]", F },
                                      // Expression patterns, anchored at each ancestor-or-self.
                                      { "(b|c)[1]", "/r/a[1]/b[1]", T },
                                      { "(b|c)[1]", "/r/a[1]/c", F },
                                      { "(b|c)[1]", "/r/a[2]/b", T },
                                      { "a/(b|c)", "/r/a[1]/c", T },
                                      { "r/(b|c)", "/r/a[1]/c", F },
                                      { "a/descendant::b", "/r/a[1]/b[1]", T },
                                      { "a[1]/descendant::b[1]", "/r/a[1]/b[2]", F },
                                      { "b except b[@n='2']", "/r/a[1]/b[@n='2']", F },
                                      { "b except b[@n='2']", "/r/a[1]/b[@n='1']", T },
                                      { "b intersect b[@n='2']", "/r/a[1]/b[@n='2']", T },
                                      { "b intersect b[@n='2']", "/r/a[1]/b[@n='1']", F },
                                      { "c | b[@n='4']", "/r/a[2]/b", T },
                                      { "c | b[@n='4']", "/r/a[1]/b[1]", F },
                                      { "descendant::b", "/r/a[1]/b[1]", T },
                                      { "descendant-or-self::a", "/r/a[1]", T },
                                      { "self::b", "/r/a[1]/b[1]", T },
                                      { "self::b", "/r/a[1]/c", F } });
  }

  /**
   * A dynamic error inside a pattern's predicate. go-xslt expects the
   * FOAR0001 to propagate; XSLT 3.0 §5.5.4 says such an error "has the effect
   * that the item being tested is treated as not matching the pattern", so
   * the item does not match here.
   */
  @Test
  public void spPatternMatchError ()
  {
    final XdmNode aDoc = parse (PAT_DOC);
    final DynamicContext aCtx = new DynamicContext (_createStaticContext ()).withFocus (aDoc, 1, 1);
    assertFalse (_pattern ("b[1 idiv 0]").matches (_select (aDoc, "/r/a[1]/b[1]"), aCtx));
  }

  /**
   * XPath expressions outside XSLT 3.0's Pattern grammar (§5.5.2) are static
   * errors XTSE0340.
   */
  @Test
  public void spPatternSyntaxErrors ()
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sPattern : new String [] { // a PredicatePattern is never a union operand
                                                 "b | .[1]",
                                                 // ... nor parenthesized
                                                 "(.[1])",
                                                 ".[1] | .[2]",
                                                 // a literal as a step
                                                 "a/'x'",
                                                 // an array constructor as a step
                                                 "/[1]",
                                                 "a/map{}",
                                                 "a/function(){1}",
                                                 // reverse axes are not PatternAxis
                                                 "parent::a",
                                                 "ancestor::a",
                                                 "a/following-sibling::b",
                                                 "a/preceding::b" })
    {
      try
      {
        _pattern (sPattern);
        aErrors.add ("'" + sPattern + "' succeeded, want XTSE0340");
      }
      catch (final XPathException ex)
      {
        if (!XsltPatternParser.ERR_INVALID_PATTERN.equals (ex.getCode ()))
          aErrors.add ("'" + sPattern + "': " + ex.getCode () + ", want XTSE0340");
      }
    }
    // Plain syntax errors, with any code
    for (final String sPattern : new String [] { "2+2", "'lit'", "a = 1", "for $x in a return $x", "a b", "a[", ")", "a/" })
    {
      try
      {
        _pattern (sPattern);
        aErrors.add ("'" + sPattern + "' succeeded");
      }
      catch (final XPathException ex)
      {
        // expected
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * §5.5.2's FunctionCallP: only doc, id, element-with-id, key and root, only
   * as the LEADING primary, and with literal or variable-reference arguments.
   * go-xslt checks this in a separate CheckGrammar step; here the parser does
   * it.
   */
  @Test
  public void spPatternCheckGrammar ()
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sPattern : new String [] { "key('k', 42)//a",
                                                 "key('k', $v)/a",
                                                 "id('x')",
                                                 "fn:id('x')/a",
                                                 "element-with-id('x')",
                                                 "doc('x.xml')//a",
                                                 "a[f(1 + 2)]",
                                                 "a | id('x')" })
    {
      try
      {
        _pattern (sPattern);
      }
      catch (final XPathException ex)
      {
        aErrors.add ("'" + sPattern + "': " + ex.getCode () + " " + ex.getMessage ());
      }
    }
    for (final String sPattern : new String [] { // not an OuterFunctionName
                                                 "copy-of(.)//a",
                                                 // argument is not a literal or variable
                                                 "key('k', 40+2)//a",
                                                 // a function call that is not the leading primary
                                                 "/key('k', 42)//a",
                                                 // a prefixed, non-fn function
                                                 "x:f()//a",
                                                 "count(a)",
                                                 // go-xslt accepts root($v)/a; XSLT 3.0 §5.5.2 allows only the
                                                 // zero-arity form of fn:root in a FunctionCallP
                                                 "root($v)/a" })
    {
      try
      {
        _pattern (sPattern);
        aErrors.add ("'" + sPattern + "' succeeded, want XTSE0340");
      }
      catch (final XPathException ex)
      {
        if (!XsltPatternParser.ERR_INVALID_PATTERN.equals (ex.getCode ()))
          aErrors.add ("'" + sPattern + "': " + ex.getCode () + ", want XTSE0340");
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * @return The default priority; for a union the highest of its alternatives
   *         (go-xslt's DefaultPriority reports that, as a union is a set of
   *         rules).
   */
  private static double _getDefaultPriority (@NonNull final String sPattern)
  {
    double ret = Double.NEGATIVE_INFINITY;
    for (final XsltPattern aAlt : _pattern (sPattern).getAlternatives ())
      ret = Math.max (ret, aAlt.getDefaultPriority ());
    return ret;
  }

  /**
   * Checks the table of XSLT 3.0 §6.5.
   */
  @Test
  public void spPatternDefaultPriority ()
  {
    final Object [] [] aCases = { { "a", "0" },
                                  { "@a", "0" },
                                  { "child::a", "0" },
                                  { "attribute::a", "0" },
                                  { "p:a", "0" },
                                  { "processing-instruction('x')", "0" },
                                  { "processing-instruction(x)", "0" },
                                  { "*", "-0.5" },
                                  { "@*", "-0.5" },
                                  { "p:*", "-0.25" },
                                  { "*:a", "-0.25" },
                                  { "@p:*", "-0.25" },
                                  { "node()", "-0.5" },
                                  { "text()", "-0.5" },
                                  { "comment()", "-0.5" },
                                  { "processing-instruction()", "-0.5" },
                                  { "namespace-node()", "-0.5" },
                                  { "element()", "-0.5" },
                                  { "element(*)", "-0.5" },
                                  { "attribute()", "-0.5" },
                                  { "attribute(*)", "-0.5" },
                                  { "element(a)", "0" },
                                  { "element(*, xs:integer)", "0" },
                                  { "attribute(a)", "0" },
                                  { "attribute(*, xs:integer)", "0" },
                                  { "element(a, xs:integer)", "0.25" },
                                  // go-xslt writes attribute(a, xs:integer?), which the AttributeTest grammar
                                  // (XPath 3.1 §A.1, [95]) does not allow: only ElementTest takes "?".
                                  { "attribute(a, xs:integer)", "0.25" },
                                  { "document-node()", "-0.5" },
                                  { "document-node(element(a))", "0" },
                                  { "document-node(element(a, xs:integer))", "0.25" },
                                  { "document-node(element(*))", "-0.5" },
                                  { "/", "-0.5" },
                                  { "a/b", "0.5" },
                                  { "/a", "0.5" },
                                  { "//a", "0.5" },
                                  { "a//b", "0.5" },
                                  { "a[1]", "0.5" },
                                  { "@a[. = 1]", "0.5" },
                                  { "id('x')", "0.5" },
                                  { "id('x')/a", "0.5" },
                                  { "$v", "0.5" },
                                  { "(a|b)[1]", "0.5" },
                                  { ".[1]", "1" },
                                  { ".[1][2]", "1" },
                                  // "." is a PredicatePattern with an empty predicate list: -1.
                                  { ".", "-1" },
                                  // An IntersectExceptExprP takes the priority of its first operand.
                                  { "a except b", "0" },
                                  { "a intersect b", "0" },
                                  { "* except b", "-0.5" },
                                  { "p:* except b", "-0.25" },
                                  { "a/b except c", "0.5" },
                                  { "@a except @b", "0" },
                                  { "a except b except c", "0" },
                                  { "a intersect b except c", "0" },
                                  { "element(a, xs:integer) except b", "0.25" },
                                  { "(a|b) except c", "0.5" },
                                  { "a[1] except b", "0.5" },
                                  { "$v except a", "0.5" },
                                  // A union is a set of rules; the highest is reported.
                                  { "a | *", "0" },
                                  { "(a | b)", "0" },
                                  { "* | a/b", "0.5" },
                                  { "* | node()", "-0.5" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Object [] aCase : aCases)
    {
      final String sPattern = (String) aCase[0];
      final double dWant = Double.parseDouble ((String) aCase[1]);
      final double dGot = _getDefaultPriority (sPattern);
      if (dGot != dWant)
        aErrors.add ("'" + sPattern + "' = " + dGot + ", want " + dWant);
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  /**
   * §6.5 splits a top-level union (also inside outer parentheses) into one
   * rule per alternative, in order.
   */
  @Test
  public void spPatternAlternatives ()
  {
    final String [] [] aCases = { { "a", "a" },
                                  { "a | b", "a", "b" },
                                  { "(a | b) | c", "a", "b", "c" },
                                  { "((a|b))", "a", "b" },
                                  { "a[b|c] | d", "a[b|c]", "d" },
                                  { "(a)[1] | (b)", "(a)[1]", "b" },
                                  { "(a) | (b)[1]", "a", "(b)[1]" },
                                  { "(:c:) a", "(:c:) a" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      final ICommonsList <String> aGot = new CommonsArrayList <> ();
      for (final XsltPattern aAlt : _pattern (aCase[0]).getAlternatives ())
        aGot.add (aAlt.getSource ());
      final ICommonsList <String> aWant = new CommonsArrayList <> ();
      for (int i = 1; i < aCase.length; ++i)
        aWant.add (aCase[i]);
      if (!aGot.equals (aWant))
        aErrors.add ("'" + aCase[0] + "' = " + aGot + ", want " + aWant);
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
    // A leading comment is not a parenthesis: ".[1]" stays a valid
    // PredicatePattern after one.
    _pattern ("(: note :) .[1]");
    _pattern ("(: a (: nested :) :) .[1]");
  }

  /**
   * A namespace node is never a child, attribute or descendant (XDM 3.1 §6.4),
   * so patterns ending on those axes without a namespace-node() test cannot
   * match one. go-xslt tests its static MayMatchNamespaceNode answer; here the
   * patterns it rules out are matched against every namespace node.
   */
  @Test
  public void spPatternMayMatchNamespace ()
  {
    final XdmNode aDoc = parse (PAT_DOC);
    final StaticContext aSC = _createStaticContext ();
    final XdmSequence aNamespaces = XPathCompiler.compile ("//namespace-node()", aSC)
                                                 .evaluate (new DynamicContext (aSC).withFocus (aDoc, 1, 1));
    final DynamicContext aCtx = new DynamicContext (aSC).withFocus (aDoc, 1, 1)
                                                        .withVariable (VAR_V, XdmSequence.EMPTY)
                                                        .withVariable (VAR_AS, XdmSequence.EMPTY)
                                                        .withVariable (VAR_F, XdmSequence.EMPTY);
    for (final String sPattern : new String [] { "a", "@a", "//a", "/", "a/b | @c", "node()", "descendant::x" })
    {
      final XsltPattern aPattern = _pattern (sPattern);
      for (final IXdmItem aNS : aNamespaces)
        assertFalse (sPattern + " on " + aNS, aPattern.matches (aNS, aCtx));
    }
    // A predicate pattern matches any item
    for (final IXdmItem aNS : aNamespaces)
      assertEquals (". on " + aNS, Boolean.TRUE, Boolean.valueOf (_pattern (".").matches (aNS, aCtx)));
  }

  /**
   * Covers the call walk behind XTSE1060/XTSE1070 (current-group() /
   * current-grouping-key() in a pattern) and XPST0017 checking: a call is found
   * wherever it is nested.
   */
  @Test
  public void spPatternFunctionUse ()
  {
    final String [] aWraps = { "a[%s]",
                               "a[1 + %s]",
                               "a[-%s]",
                               "a[x | %s]",
                               "a[x/y[%s]]",
                               "a[(x)[%s]]",
                               "a[(1, %s)]",
                               "a[1 to %s]",
                               "a[for $w in %s return 1]",
                               "a[for $w in 1 return %s]",
                               "a[let $w := %s return 1]",
                               "a[let $w := 1 return %s]",
                               "a[some $w in %s satisfies 1]",
                               "a[every $w in 1 satisfies %s]",
                               "a[if (%s) then 1 else 2]",
                               "a[if (1) then 2 else %s]",
                               "a[map{(%s): 1}]",
                               "a[map{1: %s}]",
                               "a[[%s]]",
                               "a[array{%s}]",
                               "a[function() {%s}]",
                               "a[(1)?(%s)]",
                               "a[$f(%s)]",
                               "a[(%s)(1)]",
                               "a[%s = 1]",
                               "a['x' || %s]",
                               "a[x intersect %s]",
                               "a[%s instance of item()]",
                               "a[%s treat as item()*]",
                               "a[%s cast as xs:string?]",
                               "a[%s => count()]",
                               "a[1 => f(%s)]",
                               "a[x ! %s]",
                               "a[x/(%s)]",
                               "a[count(%s)]",
                               "b | (a|c)[%s]" };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sWrap : aWraps)
    {
      final String sPattern = sWrap.replace ("%s", "current-group()");
      final CompiledXsltPattern aPattern = (CompiledXsltPattern) _pattern (sPattern);
      if (!aPattern.containsFunction (x -> "current-group".equals (x.localName ())))
        aErrors.add ("'" + sPattern + "': current-group() not found");
      if (aPattern.containsFunction (x -> "current-grouping-key".equals (x.localName ())))
        aErrors.add ("'" + sPattern + "': current-grouping-key() falsely found");
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
    // go-xslt lists the calls (FunctionCalls); here each is looked up
    final CompiledXsltPattern aPattern = (CompiledXsltPattern) _pattern ("a[f(1)][g()] | (b|c)[h(1, 2)]");
    for (final String sName : new String [] { "f", "g", "h" })
      assertEquals (sName, Boolean.TRUE, Boolean.valueOf (aPattern.containsFunction (x -> sName.equals (x.localName ()))));
    assertFalse (aPattern.containsFunction (x -> "count".equals (x.localName ())));
  }
}
