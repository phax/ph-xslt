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
package com.helger.phoxtrot.xpath.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.numeric.mutable.MutableBoolean;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.fn.IXPathFunction;
import com.helger.phoxtrot.xpath.parser.XPathParser;
import com.helger.phoxtrot.xpath.pattern.CompiledXsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPatternParser;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for {@link XPathStreamability}: a port of go-xslt's
 * <code>internal/xpath/streamability_test.go</code> and
 * <code>streamability_rules_test.go</code>, same cases and expectations.
 *
 * @author Philip Helger
 */
public final class XPathStreamabilityTest
{
  private static final String NS_MY = "urn:my";
  private static final String NS_F = "urn:f";
  private static final String NS_G = "urn:g";

  private static final String GM = "grounded/motionless";
  private static final String GC = "grounded/consuming";
  private static final String GF = "grounded/free-ranging";
  private static final String SM = "striding/motionless";
  private static final String SC = "striding/consuming";
  private static final String CM = "crawling/motionless";
  private static final String CC = "crawling/consuming";
  private static final String LM = "climbing/motionless";
  private static final String LC = "climbing/consuming";
  private static final String RF = "roaming/free-ranging";

  private static final XdmQName VAR_N = new XdmQName ("n");
  private static final XdmQName VAR_M = new XdmQName ("m");

  private static final StaticContext SCTX = _createStaticContext ();

  @NonNull
  private static StaticContext _createStaticContext ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    final IXPathFunction aDummy = (aCtx, aArgs) -> XdmSequence.EMPTY;
    // The XSLT functions the expressions use; the XSLT layer registers them
    aLib.register (XdmConstants.NS_FN, "current", "() as item()", aDummy, true);
    aLib.register (XdmConstants.NS_FN, "current-group", "() as item()*", aDummy, true);
    aLib.register (XdmConstants.NS_FN, "current-grouping-key", "() as xs:anyAtomicType*", aDummy, true);
    aLib.register (XdmConstants.NS_FN, "copy-of", "() as item()*", aDummy, true);
    aLib.register (XdmConstants.NS_FN, "copy-of", "(item()*) as item()*", aDummy);
    aLib.register (XdmConstants.NS_FN, "accumulator-before", "(xs:string) as item()*", aDummy, true);
    aLib.register (XdmConstants.NS_FN, "accumulator-after", "(xs:string) as item()*", aDummy, true);
    aLib.register (XdmConstants.NS_FN, "key", "(xs:string, xs:anyAtomicType*) as node()*", aDummy, true);
    // Functions outside the catalogue of section 19.8.9
    aLib.register (XdmConstants.NS_FN, "unknown-function", "(item()*) as item()*", aDummy);
    aLib.register (NS_MY, "unknownFunction", "(item()*) as item()*", aDummy);
    aLib.register (NS_MY, "f", "(item()*) as item()*", aDummy);
    aLib.register (NS_MY, "current-group", "() as item()*", aDummy);
    aLib.register (NS_G, "other", "(item()*) as item()*", aDummy);
    for (final String sName : new String [] { "zero",
                                              "min",
                                              "nodes",
                                              "absorb",
                                              "inspect",
                                              "filter",
                                              "asc",
                                              "shallow",
                                              "deep",
                                              "attrs",
                                              "bad" })
    {
      aLib.register (NS_F, sName, "() as item()*", aDummy);
      aLib.register (NS_F, sName, "(item()*) as item()*", aDummy);
      aLib.register (NS_F, sName, "(item()*, item()*) as item()*", aDummy);
    }
    final StaticContext ret = new StaticContext ();
    ret.setFunctionLibrary (aLib);
    ret.declareNamespace ("my", NS_MY);
    ret.declareNamespace ("f", NS_F);
    ret.declareNamespace ("g", NS_G);
    for (final String sVar : new String [] { "v",
                                             "f",
                                             "g",
                                             "i",
                                             "x",
                                             "s",
                                             "n",
                                             "m",
                                             "other",
                                             "status-codes",
                                             "status",
                                             "doc",
                                             "pnum" })
      ret.declareVariable (new XdmQName (sVar));
    return ret;
  }

  @NonNull
  private static IExpr _parse (@NonNull final String sExpr)
  {
    return XPathParser.parse (sExpr, SCTX);
  }

  @NonNull
  private static XsltPattern _parsePattern (@NonNull final String sPattern)
  {
    return XsltPatternParser.parse (sPattern, SCTX);
  }

  @NonNull
  private static String _classify (@NonNull final String sExpr, @NonNull final StreamContext aCtx)
  {
    return XPathStreamability.getStreamability (_parse (sExpr), aCtx).toString ();
  }

  private static void _run (@NonNull final StreamContext aCtx, @NonNull final String... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (int i = 0; i < aCases.length; i += 2)
    {
      final String sExpr = aCases[i];
      final String sExpected = aCases[i + 1];
      final String sActual = _classify (sExpr, aCtx);
      if (!sExpected.equals (sActual))
        aErrors.add (sExpr + ": got " + sActual + ", want " + sExpected);
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  @NonNull
  private static StreamContext _striding ()
  {
    return new StreamContext (EPosture.STRIDING);
  }

  /**
   * The worked examples of sections 19.7, 19.8.8.7 and 19.8.8.8, evaluated with
   * the striding context posture of a streamable template rule.
   */
  @Test
  public void testPostureSweepTable ()
  {
    _run (_striding (),
          // The table of posture/sweep combinations of section 19.7
          "name()",
          GM,
          "string(title)",
          GC,
          "parent::*",
          LM,
          "child::x/ancestor::y",
          LC,
          "@status",
          SM,
          "child::*",
          SC,
          "descendant::*",
          CC,
          "preceding::*",
          RF,
          // The worked path examples of section 19.8.8.7
          "a/b/c",
          SC,
          "a/descendant::c",
          CC,
          "../@status",
          SM,
          "section//head",
          CC,
          "section//head[1]",
          RF,
          "copy-of(.)//a/following-sibling::*",
          GC,
          // The descendant-with-numeric-predicate clause of section 19.8.8.8
          "descendant::section[1]",
          SC,
          // Literals and the context item
          "3",
          GM,
          ".",
          SM,
          "()",
          GM,
          // Note of section 19.8.1: (@a, @b) is motionless and striding
          "(@a, @b)",
          SM,
          // Note of section 19.8.1: if (X) then @name else name is streamable
          "if (@x) then @name else name",
          SC,
          // Section 19.8.9.14: last() is roaming over a striding focus
          "last()",
          RF,
          "position()",
          GM,
          // Section 19.8.8.1: the "in" clause of a for expression must be
          // grounded
          "for $i in 1 to 3 return $i*2",
          GM,
          "for $x in child::section return $x/para",
          RF,
          // The rewrite advice of section 19.8.8.2
          "exists(child::section[has-children(.)])",
          GC,
          // The union examples of section 19.8.8.4
          ". | doc('abc.com')//x",
          SM,
          "parent::A | */ancestor::B",
          LC,
          "* | */*",
          CC,
          "child::div | parent::div",
          RF,
          // Section 19.8.9.17: reverse navigates, so it needs a grounded
          // operand
          "reverse(ancestor::*)/name()",
          RF,
          "reverse(ancestor::*/name())",
          GM,
          // Section 19.8.9.15: outermost lifts crawling to striding
          "outermost(descendant::para)",
          SC,
          // The atomization note of section 19.8.5
          "abs(discount)",
          GC,
          // The streamable example of section 16.2
          "transactions/transaction[@value >= 0]",
          SC);
  }

  /** Section 19.8.9.18: a leading "/" collapses only over a document node. */
  @Test
  public void testAbsolutePathNeedsDocumentContext ()
  {
    final StreamContext aDoc = _striding ().setContextDocument (true);
    final StreamContext aElem = _striding ();
    assertTrue (XPathStreamability.getStreamability (_parse ("/a/b"), aDoc).isStreamable ());
    assertFalse (XPathStreamability.getStreamability (_parse ("/a/b"), aElem).isStreamable ());
    assertTrue (XPathStreamability.getStreamability (_parse ("//a"), aDoc).isStreamable ());
  }

  /** The safety direction: constructs with no rule, or none provable, are not streamable. */
  @Test
  public void testFailsClosed ()
  {
    for (final String sExpr : new String [] { "my:unknownFunction(a)",
                                              "let $x := . return $x/a",
                                              "[a, b]",
                                              "map{'k': a}",
                                              "some $x in child::s satisfies has-children($x)",
                                              "a/following::b",
                                              // Note of section 19.8.1: two streamed operands
                                              "count((.., *))" })
      assertFalse (sExpr, XPathStreamability.getStreamability (_parse (sExpr), _striding ()).isStreamable ());
  }

  /** The two example lists of section 19.8.10. */
  @Test
  public void testPatterns ()
  {
    for (final String sPattern : new String [] { "/",
                                                 "*",
                                                 "/*",
                                                 "p",
                                                 "p|q",
                                                 "p/q",
                                                 "p[@status='red']",
                                                 "p[base-uri()]",
                                                 "p[@class or @style]",
                                                 "p[@status]",
                                                 "p[@class | @style]",
                                                 "p[contains(@class, ':')]",
                                                 "p[substring-after(@class, ':')]",
                                                 "p[ancestor::*[@xml:lang]]",
                                                 "text()[starts-with(., '$')]",
                                                 "@price",
                                                 "@price[starts-with(., '$')]",
                                                 "//p/text()[. = 'Introduction']",
                                                 "document-node(element(html))" })
      assertEquals (sPattern,
                    GM,
                    XPathStreamability.getPatternStreamability (_parsePattern (sPattern), _striding ()).toString ());
    for (final String sPattern : new String [] { "id('abc')",
                                                 "p[b]",
                                                 "p[. = 'Introduction']",
                                                 "p[starts-with(., '$')]",
                                                 "p[preceding-sibling::p[1] = '']",
                                                 "p[1]",
                                                 "p[position() gt 2]",
                                                 "p[last()]",
                                                 "p[data(@status)]" })
      assertEquals (sPattern,
                    RF,
                    XPathStreamability.getPatternStreamability (_parsePattern (sPattern), _striding ()).toString ());
  }

  /** The general streamability rules of section 19.8.1, one clause at a time. */
  @Test
  public void testGeneralRules ()
  {
    final EPosture eG = EPosture.GROUNDED;
    final EPosture eS = EPosture.STRIDING;
    final EPosture eC = EPosture.CRAWLING;
    final EPosture eL = EPosture.CLIMBING;
    final EPosture eR = EPosture.ROAMING;
    final ESweep eM = ESweep.MOTIONLESS;
    final ESweep eK = ESweep.CONSUMING;
    final ESweep eF = ESweep.FREE_RANGING;
    final EStreamUsage eA = EStreamUsage.ABSORPTION;
    final EStreamUsage eI = EStreamUsage.INSPECTION;
    final EStreamUsage eT = EStreamUsage.TRANSMISSION;
    final EStreamUsage eN = EStreamUsage.NAVIGATION;
    final ICommonsMap <String, List <StreamOperand>> aCases = new CommonsHashMap <> ();
    final ICommonsMap <String, String> aExpected = new CommonsHashMap <> ();
    aCases.put ("no operands", new CommonsArrayList <> ());
    aExpected.put ("no operands", GM);
    aCases.put ("free-ranging operand", new CommonsArrayList <> (new StreamOperand (eG, eF, eI)));
    aExpected.put ("free-ranging operand", RF);
    aCases.put ("roaming operand", new CommonsArrayList <> (new StreamOperand (eR, eM, eI)));
    aExpected.put ("roaming operand", RF);
    aCases.put ("grounded keeps its sweep", new CommonsArrayList <> (new StreamOperand (eG, eK, eN)));
    aExpected.put ("grounded keeps its sweep", GC);
    aCases.put ("climbing absorption", new CommonsArrayList <> (new StreamOperand (eL, eM, eA)));
    aExpected.put ("climbing absorption", RF);
    aCases.put ("climbing absorption of childless",
                new CommonsArrayList <> (new StreamOperand (eL, eM, eA).withChildless (true)));
    aExpected.put ("climbing absorption of childless", GM);
    aCases.put ("striding absorption", new CommonsArrayList <> (new StreamOperand (eS, eM, eA)));
    aExpected.put ("striding absorption", GC);
    aCases.put ("crawling absorption", new CommonsArrayList <> (new StreamOperand (eC, eK, eA)));
    aExpected.put ("crawling absorption", GC);
    aCases.put ("striding inspection consuming", new CommonsArrayList <> (new StreamOperand (eS, eK, eI)));
    aExpected.put ("striding inspection consuming", GC);
    aCases.put ("striding inspection motionless", new CommonsArrayList <> (new StreamOperand (eS, eM, eI)));
    aExpected.put ("striding inspection motionless", GM);
    aCases.put ("navigation of a streamed node", new CommonsArrayList <> (new StreamOperand (eS, eM, eN)));
    aExpected.put ("navigation of a streamed node", RF);
    aCases.put ("transmission keeps posture", new CommonsArrayList <> (new StreamOperand (eC, eK, eT)));
    aExpected.put ("transmission keeps posture", CC);
    aCases.put ("transmission motionless", new CommonsArrayList <> (new StreamOperand (eL, eM, eT)));
    aExpected.put ("transmission motionless", LM);
    aCases.put ("higher-order consuming", new CommonsArrayList <> (new StreamOperand (eS, eK, eT).withHigherOrder (true)));
    aExpected.put ("higher-order consuming", RF);
    aCases.put ("higher-order motionless",
                new CommonsArrayList <> (new StreamOperand (eS, eM, eI).withHigherOrder (true)));
    aExpected.put ("higher-order motionless", GM);
    aCases.put ("two consuming", new CommonsArrayList <> (new StreamOperand (eS, eK, eA), new StreamOperand (eS, eK, eA)));
    aExpected.put ("two consuming", RF);
    aCases.put ("two motionless transmissions, one posture",
                new CommonsArrayList <> (new StreamOperand (eS, eM, eT), new StreamOperand (eS, eM, eT)));
    aExpected.put ("two motionless transmissions, one posture", SM);
    aCases.put ("two motionless transmissions, two postures",
                new CommonsArrayList <> (new StreamOperand (eS, eM, eT), new StreamOperand (eL, eM, eT)));
    aExpected.put ("two motionless transmissions, two postures", RF);
    aCases.put ("choice group",
                new CommonsArrayList <> (new StreamOperand (eS, eK, eT).withChoice (1),
                                         new StreamOperand (eS, eM, eT).withChoice (1)));
    aExpected.put ("choice group", SC);
    aCases.put ("choice group with a grounded member",
                new CommonsArrayList <> (new StreamOperand (eC, eK, eT).withChoice (1),
                                         new StreamOperand (eG, eK, eA).withChoice (1)));
    aExpected.put ("choice group with a grounded member", CC);
    aCases.put ("choice group, different streamed postures",
                new CommonsArrayList <> (new StreamOperand (eS, eK, eT).withChoice (1),
                                         new StreamOperand (eL, eM, eT).withChoice (1)));
    aExpected.put ("choice group, different streamed postures", RF);
    aCases.put ("two choice groups",
                new CommonsArrayList <> (new StreamOperand (eS, eK, eT).withChoice (1),
                                         new StreamOperand (eS, eK, eT).withChoice (2)));
    aExpected.put ("two choice groups", RF);
    aCases.put ("one consuming among motionless",
                new CommonsArrayList <> (new StreamOperand (eG, eM, eA),
                                         new StreamOperand (eS, eK, eT),
                                         new StreamOperand (eS, eM, eI)));
    aExpected.put ("one consuming among motionless", SC);

    for (final Map.Entry <String, List <StreamOperand>> aEntry : aCases.entrySet ())
    {
      final String sGot = XPathStreamability.applyGeneralRules (aEntry.getValue ()).toString ();
      assertEquals (aEntry.getKey (), aExpected.get (aEntry.getKey ()), sGot);
      final MutableBoolean aDefinite = new MutableBoolean (false);
      final String sGot2 = XPathStreamability.applyGeneralRules (aEntry.getValue (),
                                                                 new StreamContext (eG).setDefiniteFlag (aDefinite))
                                             .toString ();
      assertEquals (aEntry.getKey (), sGot, sGot2);
    }

    assertTrue (PostureSweep.isStreamable (eC, eK));
    assertFalse (PostureSweep.isStreamable (eR, eM));
    assertFalse (PostureSweep.isStreamable (eG, eF));
  }

  /** Section 19.4 type-determined usage. */
  @Test
  public void testTypeDeterminedUsage ()
  {
    assertSame (EStreamUsage.INSPECTION, XPathStreamability.getTypeDeterminedUsage (true, false));
    assertSame (EStreamUsage.ABSORPTION, XPathStreamability.getTypeDeterminedUsage (false, true));
    assertSame (EStreamUsage.NAVIGATION, XPathStreamability.getTypeDeterminedUsage (false, false));

    // The SeqType overload (not in go-xslt, which does this on type strings in
    // its XSLT layer)
    assertSame (EStreamUsage.NAVIGATION, XPathStreamability.getTypeDeterminedUsage ((SeqType) null));
    assertSame (EStreamUsage.NAVIGATION, XPathStreamability.getTypeDeterminedUsage (SeqType.ANY));
    assertSame (EStreamUsage.ABSORPTION,
                XPathStreamability.getTypeDeterminedUsage (SeqType.of (ItemType.atomic (EAtomicType.STRING),
                                                                       EOccurrence.ZERO_OR_ONE)));
    assertSame (EStreamUsage.INSPECTION,
                XPathStreamability.getTypeDeterminedUsage (XPathParser.parseSequenceType ("map(*)", SCTX)));
    assertSame (EStreamUsage.INSPECTION,
                XPathStreamability.getTypeDeterminedUsage (XPathParser.parseSequenceType ("function(*)+", SCTX)));
    assertSame (EStreamUsage.NAVIGATION,
                XPathStreamability.getTypeDeterminedUsage (XPathParser.parseSequenceType ("element()*", SCTX)));
  }

  /**
   * The worked examples of section 19.8.2 and the proforma table of section
   * 19.8.8 (A = absorption, I = inspection, T = transmission, N = navigation),
   * striding element context.
   */
  @Test
  public void testOperatorProformas ()
  {
    _run (_striding (),
          // Section 19.8.2
          "2 + 2",
          GM,
          "price * 2",
          GC,
          "price - discount",
          RF,
          "price * @discount",
          GC,
          "count(a/b/c)",
          GC,
          "a//c",
          CC,
          // A + A, -A, A to A, A || A, A = A, A eq A, cast/castable
          "-price",
          GC,
          "+@a",
          GM,
          "1 to 3",
          GM,
          "1 to count(*)",
          GC,
          "'a' || b",
          GC,
          "@a || @b",
          GM,
          "a || b",
          RF,
          "@a = 'x'",
          GM,
          "a eq 'x'",
          GC,
          "a idiv @b",
          GC,
          "a cast as xs:integer",
          GC,
          "@a castable as xs:integer",
          GM,
          // I and I, I or I
          "@a and @b",
          GM,
          "exists(a) or @b",
          GC,
          "a or b",
          RF,
          // I is I (Note 1 of section 19.8.8)
          ". is ..",
          GM,
          ". << child::x",
          GC,
          // T, T
          "(@a, @b)",
          SM,
          "(1, a)",
          SC,
          "(a, @b)",
          RF,
          "()",
          GM,
          "'s'",
          GM,
          // Section 19.8.8.4
          "a | b",
          CC,
          "@a | @b",
          CM,
          ". | following-sibling::*",
          RF,
          ".. | *",
          RF,
          "(1, 2)[1] | a",
          SC,
          // Section 19.8.8.5: inspection, except a document-node(element())
          // test
          "a instance of element()*",
          GC,
          ". instance of element(E)*",
          GM,
          ". instance of document-node(element(a))",
          GC,
          // Section 19.8.8.6
          ". treat as document-node(element(a))",
          RF,
          "a treat as element()*",
          SC,
          // Section 19.8.8.3: a choice operand group of transmissions
          "if (@a) then b else c",
          SC,
          "if (@x) then @name else 'n'",
          SM,
          "if (a) then b else c",
          RF,
          "if (@x) then b else ..",
          RF,
          // Section 19.8.8.1
          "for $i in 1 to 3 return salary",
          RF,
          "for $i in 1 to 3 return name(ancestor::x[$i])",
          GM,
          "for $e in copy-of(emp) return $e/salary",
          GC,
          "for $x in child::section return $x/para",
          RF,
          // let $var := N return T
          "let $x := 1 return @a",
          SM,
          "let $x := @a return 1",
          RF,
          "let $x := count(a) return $x + 1",
          GC,
          // Section 19.8.8.2
          "some $i in 1 to 3 satisfies author[$i] eq \"Kay\"",
          RF,
          "some $i in 1 to 3 satisfies @grade = $i",
          GM,
          "every $e in emp/salary/number(.) satisfies $e gt 10000",
          GC,
          // Section 19.8.8.7: posture of the right operand, sweep the wider
          "a ! @b",
          SC,
          "@a ! string(.)",
          GM,
          "a ! (. + 1)",
          GC,
          "(1, 2) ! .",
          GM,
          // Section 19.8.8.16
          "function($x) { $x + 1 }",
          GM,
          // Section 19.8.8.15
          "name#0",
          RF,
          "upper-case#1",
          GM,
          "position#0",
          RF,
          // SquareArrayConstructor [N, N]
          "[1, 'x']",
          GM,
          "[@a]",
          RF,
          "array{@a}",
          RF,
          "array{}",
          GM,
          // Sections 19.8.8.17 / 19.8.4.24: key absorption, value navigation
          "map{'k': 1}",
          GM,
          "map{'k': @a}",
          RF,
          "map{}",
          GM,
          // Section 19.8.8.18
          "map{'a': 1}?a",
          GM,
          "map{'a': 1}?*",
          GM,
          "?k",
          GM,
          "map{'a': 1}?(@a)",
          GM,
          "map{'a': 1}?(a)",
          GC,
          // Section 19.8.8.11: no signature, navigation; a map, absorption
          "$f(@a)",
          RF,
          "$f(1)",
          GM,
          "map{'a': 1}(@a)",
          GM,
          "map{'a': 1}(a)",
          GC,
          "[1](1)",
          GM,
          // Section 19.8.8.14 ArrowExpr: X => F(Y) is F(X, Y)
          "@a => string()",
          GM,
          "a => string()",
          GC,
          "a => count() => string()",
          GC,
          "@a => $f()",
          RF,
          "1 => $f()",
          GM);
  }

  /** The table of section 19.8.8.9 with its special clauses, and the path rules of section 19.8.8.8. */
  @Test
  public void testAxisSteps ()
  {
    _run (_striding (),
          "self::x",
          SM,
          "@a",
          SM,
          "namespace::*",
          SM,
          "..",
          LM,
          "ancestor::x",
          LM,
          "ancestor-or-self::x",
          LM,
          "child::x",
          SC,
          "descendant::x",
          CC,
          "descendant-or-self::node()",
          CC,
          "descendant::text()",
          SC,
          "descendant::comment()",
          SC,
          "descendant::processing-instruction()",
          SC,
          "following-sibling::x",
          RF,
          "preceding-sibling::x",
          RF,
          "following::x",
          RF,
          // The numeric, focus-free predicate clause
          "descendant::section[1]",
          SC,
          "descendant::section[$i + 1]",
          SC,
          "descendant::section[count($x)]",
          SC,
          "descendant::section[-1]",
          SC,
          "descendant::section[1 to 2]",
          SC,
          "descendant::section[position()]",
          CC,
          "descendant::section[b]",
          RF,
          "descendant::section[@a]",
          CC,
          // Predicates must be motionless
          "*[@a = 1]",
          SC,
          "*[b]",
          RF,
          "*[1]",
          SC,
          "@a[. = 'x']",
          SM,
          "text()[. = 'x']",
          SC,
          // Section 19.8.8.8: climbing, then downwards
          "ancestor::x/self::y",
          LM,
          "ancestor::x/@y",
          SM,
          "ancestor::x/namespace::*",
          SM,
          "../b",
          RF,
          "ancestor::x/descendant::y",
          RF,
          // Crawling context for the next step
          "descendant::x/@a",
          SC,
          "descendant::x/self::y",
          CC,
          "descendant::x/self::text()",
          SC,
          "descendant::x/..",
          LC,
          "descendant::x/following::y",
          RF,
          // Worked notes of section 19.8.8.8
          "a/@code",
          SC,
          "./@code",
          SM,
          "./a",
          SC,
          "a/following::b",
          RF,
          "./.",
          SM,
          "text()/..",
          LC,
          // Scanning expressions
          "self::node()//section/head",
          CC,
          "descendant::section/head",
          CC,
          "section//head/text()",
          SC,
          "section//head[@a]",
          CC,
          "section//head[b]",
          RF,
          "section//head[position() = 1]",
          RF,
          "section//head/following::p",
          RF,
          "(a|b)//c",
          CC,
          // A RootedPath can never be a scanning expression
          "$v//section/head",
          GM,
          // Postfix steps
          "a/string()",
          GC,
          "a/(@b)",
          SC,
          "a/(b | c)",
          CC,
          "a/name()",
          GC,
          "copy-of(.)//a/following-sibling::*",
          GC,
          // Filter expressions of section 19.8.8.10
          "(descendant::x)[1]",
          SC,
          "(descendant::x)[$i + 1]",
          SC,
          "(descendant::x)[@a]",
          CC,
          "(descendant::x)[b]",
          RF,
          "(a)[position() = 1]",
          SC,
          "$s[child::*]",
          GM,
          "(1, 2, 3)[. > 1]",
          GM,
          // Grounded start
          "copy-of(.)/a/b",
          GC,
          "(1, 2)/string()",
          GM);
    // Context postures other than striding
    _run (new StreamContext (EPosture.GROUNDED),
          "a/b",
          GM,
          "following::x",
          GM,
          "/a",
          GM,
          "last()",
          GM,
          "root()",
          GM,
          "name#0",
          GM);
    _run (new StreamContext (EPosture.ROAMING), "a", RF, "last()", RF);
    _run (new StreamContext (EPosture.CLIMBING),
          "*",
          RF,
          "self::x",
          LM,
          "@a",
          SM,
          "..",
          LM,
          "last()",
          GM,
          "/a",
          RF);
    _run (new StreamContext (EPosture.CRAWLING),
          "*",
          RF,
          "descendant::x",
          RF,
          "self::x",
          CM,
          "self::text()",
          SM,
          "@a",
          SM,
          "ancestor::x",
          LM,
          "last()",
          RF,
          "(.)[1]",
          SM);
    // Section 19.8.9.18: a leading "/" over a document node in striding posture
    _run (_striding ().setContextDocument (true),
          "/",
          SM,
          "/a",
          SC,
          "//section/head",
          CC,
          "(//x)[3]",
          SC,
          "(//x)[1 to 5]",
          SC,
          "root()",
          SM,
          "(/a|/b)//c",
          CC);
  }

  /** The per-function rules of section 19.8.9. */
  @Test
  public void testBuiltinFunctions ()
  {
    _run (_striding (),
          // Inspection and absorption defaults of the zero-arity forms
          "name()",
          GM,
          "local-name(..)",
          GM,
          "string()",
          GC,
          "data()",
          GC,
          "string(@a)",
          GM,
          "normalize-space()",
          GC,
          "string-length()",
          GC,
          "generate-id()",
          GM,
          "has-children()",
          GM,
          "concat(@a, @b, @c)",
          GM,
          "concat(a, b)",
          RF,
          "xs:integer(a)",
          GC,
          "xs:integer(@a)",
          GM,
          "math:sqrt(a)",
          GC,
          "true()",
          GM,
          "count(a)",
          GC,
          "boolean(@a)",
          GM,
          "id('x')",
          RF,
          "key('k', 'v')",
          RF,
          // Sections 19.8.9.14/.16
          "last()",
          RF,
          "position()",
          GM,
          "current-grouping-key()",
          GM,
          // The maximum-cardinality-one clause of section 19.8.1
          "head(descendant::x)",
          SC,
          "exactly-one(descendant::x)",
          SC,
          "zero-or-one(descendant::x)",
          SC,
          "tail(descendant::x)",
          CC,
          "head(@a)",
          SM,
          "head(a)",
          SC,
          "subsequence(a, 1, 2)",
          SC,
          "insert-before(a, 1, 'x')",
          SC,
          "trace(a, 'x')",
          SC,
          "unordered(descendant::x)",
          CC,
          // Sections 19.8.9.15/.13/.17
          "outermost(descendant::para)",
          SC,
          "outermost(@a)",
          SM,
          "outermost(descendant::x/following::y)",
          RF,
          "innermost(descendant::para)",
          RF,
          "innermost(copy-of(.)//x)",
          GC,
          "reverse(*)",
          RF,
          "reverse((1, 2))",
          GM,
          // Higher-order functions: the first argument is navigation
          "filter(*, function($x) { true() })",
          RF,
          "for-each((1, 2), function($x) { $x })",
          GM,
          "fold-left((1, 2), 0, function($a, $b) { $a + $b })",
          GM,
          // Maps and arrays
          "map:get(map{}, @a)",
          GM,
          "map:get(map{}, a)",
          GC,
          "map:size(map{})",
          GM,
          "array:size([1])",
          GM,
          // Section 19.8.9.18
          "root()",
          LM,
          "root(.)",
          LM,
          "root(a)",
          LC,
          "root(copy-of(.))",
          GC,
          // Sections 19.8.9.1/.2
          "accumulator-before('a')",
          GM,
          "accumulator-after('a')",
          GC,
          // Section 19.8.9.4: no containing xsl:for-each-group
          "current-group()",
          RF,
          // fn:current() defaults to the striding focus of the instruction
          "current()",
          SM,
          // Unknown and extension functions fail closed
          "my:f(a)",
          RF,
          "unknown-function(1)",
          RF,
          // Partial applications of section 19.8.8.14
          "function-lookup(?, 0)",
          RF,
          "substring(?, 1)",
          GM,
          "substring(?, string(a))",
          GC);
    // xs:integer(1, 2) is rejected by the parser (XPST0017), so the AST is
    // built by hand: a constructor function takes exactly one argument
    final IExpr aBadConstructor = new IExpr.FunctionCallExpr (new XdmQName (XdmConstants.NS_XS, "integer"),
                                                              new CommonsArrayList <> (new IExpr.LiteralExpr (XdmAtomic.integer (BigInteger.ONE)),
                                                                                       new IExpr.LiteralExpr (XdmAtomic.integer (BigInteger.TWO))));
    assertEquals (RF, XPathStreamability.getStreamability (aBadConstructor, _striding ()).toString ());

    _run (new StreamContext (EPosture.GROUNDED), "function-lookup(?, 0)", GM, "accumulator-after('a')", GM);

    // accumulator-after per the later clauses of section 19.8.9.1
    assertEquals (GM, _classify ("accumulator-after('a')", _striding ().setContextChildless (true)));
    assertEquals (GF,
                  _classify ("accumulator-after('a')", _striding ().setAccumulatorPhase (EAccumulatorRulePhase.START)));
    assertEquals (GM,
                  _classify ("accumulator-after('a')", _striding ().setAccumulatorPhase (EAccumulatorRulePhase.END)));
    assertEquals (GM, _classify ("accumulator-after('a')", _striding ().setAccumulatorAfterConsumed (true)));
    assertEquals (GF, _classify ("accumulator-after('a')", _striding ().setFocusReset (true)));
    // Inside a predicate the focus-setting container is not the instruction's
    assertNotEquals (GM, _classify ("(a)[accumulator-after('x') = 1]", _striding ()));
    // The accumulator name must be motionless (both functions)
    for (final String sExpr : new String [] { "accumulator-before(string(a))", "accumulator-after(string(a))" })
      assertFalse (sExpr, XPathStreamability.getStreamability (_parse (sExpr), _striding ()).isStreamable ());

    // current-group(): the posture of the population (section 19.8.9.4)
    _run (_striding ().setGroupPosture (EPosture.GROUNDED), "current-group()", GM, "count(current-group())", GM);
    _run (_striding ().setGroupPosture (EPosture.STRIDING), "current-group()", SC);
    _run (_striding ().setCurrentPosture (EPosture.GROUNDED), "current()", GM, "(20 to 21)[. gt current()]", GM);
  }

  /** A stylesheet-function resolver for the call-site rules of section 19.8.5. */
  @NonNull
  private static IStreamFunctionResolver _createResolver ()
  {
    final ICommonsMap <String, StreamFunctionSignature> aMap = new CommonsHashMap <> ();
    aMap.put ("zero", _sig (EStreamFunctionCategory.UNCLASSIFIED, false));
    aMap.put ("min", _sig (EStreamFunctionCategory.UNCLASSIFIED, false, EStreamUsage.ABSORPTION, EStreamUsage.ABSORPTION));
    aMap.put ("nodes", _sig (EStreamFunctionCategory.UNCLASSIFIED, true, EStreamUsage.NAVIGATION));
    aMap.put ("absorb",
              _sig (EStreamFunctionCategory.ABSORBING, true, EStreamUsage.NAVIGATION, EStreamUsage.NAVIGATION));
    aMap.put ("inspect", _sig (EStreamFunctionCategory.INSPECTION, true, EStreamUsage.NAVIGATION));
    aMap.put ("filter", _sig (EStreamFunctionCategory.FILTER, true, EStreamUsage.NAVIGATION));
    aMap.put ("asc", _sig (EStreamFunctionCategory.ASCENT, true, EStreamUsage.NAVIGATION, EStreamUsage.ABSORPTION));
    aMap.put ("shallow",
              _sig (EStreamFunctionCategory.SHALLOW_DESCENT, true, EStreamUsage.NAVIGATION, EStreamUsage.ABSORPTION));
    aMap.put ("deep",
              _sig (EStreamFunctionCategory.DEEP_DESCENT, true, EStreamUsage.NAVIGATION, EStreamUsage.ABSORPTION));
    aMap.put ("attrs", _sig (EStreamFunctionCategory.SHALLOW_DESCENT, false, EStreamUsage.NAVIGATION));
    // Like the go resolver, the arity is not checked
    return (aName, nArity) -> NS_F.equals (aName.namespaceUri ()) ? aMap.get (aName.localName ()) : null;
  }

  @NonNull
  private static StreamFunctionSignature _sig (@NonNull final EStreamFunctionCategory eCategory,
                                               final boolean bNodes,
                                               @NonNull final EStreamUsage... aUsages)
  {
    return new StreamFunctionSignature (eCategory,
                                        new CommonsArrayList <> (aUsages),
                                        EStreamUsage.TRANSMISSION,
                                        bNodes,
                                        eCategory != EStreamFunctionCategory.UNCLASSIFIED);
  }

  /** The call-site rules of section 19.8.5 per category. */
  @Test
  public void testStylesheetFunctions ()
  {
    final IStreamFunctionResolver aFuncs = _createResolver ();
    final StreamContext aCtx = _striding ().setFunctionResolver (aFuncs);
    _run (aCtx,
          "f:zero()",
          GM,
          // The f:min example of section 19.8.5.1: two consuming arguments are
          // not streamable, two attributes are
          "f:min(price, discount)",
          RF,
          "f:min(@a, @b)",
          GM,
          "f:min(price, 1)",
          GC,
          "f:nodes(a)",
          RF,
          "f:nodes(copy-of(a))",
          GC,
          // Section 19.8.5.2: absorption of the first argument; crawling is out
          "f:absorb(a, 1)",
          GC,
          "f:absorb(descendant::a, 1)",
          RF,
          "f:absorb(copy-of(a), b)",
          RF,
          "f:absorb(., copy-of(b))",
          RF,
          "f:absorb(., 1)",
          GC,
          // Section 19.8.5.3
          "f:inspect(.)",
          GM,
          "f:inspect(a)",
          GC,
          // Section 19.8.5.4: the call has the posture and sweep of its argument
          "f:filter(a)",
          SC,
          "f:filter(@a)",
          SM,
          // Section 19.8.5.7
          "f:asc(.)",
          GM,
          "f:asc(a)",
          RF,
          "f:asc(., 1)",
          GM,
          // Sections 19.8.5.5/.6
          "f:shallow(., 1)",
          SC,
          "f:deep(., 1)",
          CC,
          "f:shallow(a, 1)",
          SC,
          "f:shallow(a, string(b))",
          RF,
          "f:shallow(descendant::a, 1)",
          RF,
          "f:shallow(., b)",
          SC,
          "f:shallow(., @b)",
          SC,
          "f:shallow(copy-of(.), 1)",
          GC,
          "f:shallow(., ..)",
          RF,
          "f:attrs(.)",
          SM,
          "f:shallow(@a, 1)",
          SM,
          // Partial applications of section 19.8.8.14
          "f:absorb(., ?)",
          RF,
          "f:absorb(?, 1)",
          GM,
          "f:min(?, @a)",
          GM,
          "f:min(?, a)",
          GC,
          // Not known to the resolver: an extension function
          "g:other(.)",
          RF,
          // Arrow form
          ". => f:filter()",
          SM);
    // go-xslt also checks that an unknown category (99) fails closed; the Java
    // enum has no such value, and the switch over it is exhaustive

    // Not in go-xslt: the result type of a stylesheet function is unknown, so
    // it is not childless, and string() of a filter call on the context
    // element has to read the subtree (go-xslt answers grounded/motionless)
    assertEquals (GC, _classify ("string(f:filter(.))", aCtx));
    assertFalse (XPathStreamability.isChildless (_parse ("f:filter(.)")));

    assertTrue (XPathStreamability.usesDeclaredStreamableFunction (_parse ("count(f:absorb(.)) + a => f:shallow(1)"),
                                                                   aFuncs));
    for (final String sExpr : new String [] { "a => f:shallow(1)", "f:deep(.)" })
      assertTrue (sExpr, XPathStreamability.usesDeclaredStreamableFunction (_parse (sExpr), aFuncs));
    for (final String sExpr : new String [] { "f:min(1, 2)", "a => count()", ". => $g()", "count(a)" })
      assertFalse (sExpr, XPathStreamability.usesDeclaredStreamableFunction (_parse (sExpr), aFuncs));
    assertFalse (XPathStreamability.usesDeclaredStreamableFunction (_parse ("count(f:absorb(.)) + a => f:shallow(1)"),
                                                                    null));
    assertFalse (XPathStreamability.usesDeclaredStreamableFunction (null, aFuncs));
    assertFalse (XPathStreamability.usesFunction (null, "x"));
    assertEquals (GM, XPathStreamability.getStreamability (null, aCtx).toString ());
  }

  @NonNull
  private static StreamContext _streamingParamContext ()
  {
    return _striding ().addStreamingParam (VAR_N)
                       .setStreamingParamPosture (EPosture.STRIDING)
                       .setStreamingParamSweep (ESweep.MOTIONLESS);
  }

  /**
   * References to a streaming parameter (sections 19.8.8.12 and 19.8.5) and
   * inline functions closing over one (section 19.8.8.16).
   */
  @Test
  public void testStreamingParameter ()
  {
    final StreamContext aCtx = _streamingParamContext ();
    _run (aCtx,
          "$n",
          SM,
          "$n/a",
          SC,
          "$other",
          GM,
          "function() { $n }",
          RF,
          "function() { $other }",
          GM,
          "for $i in 1 to 2 return $n",
          RF,
          // The counter-example of section 19.8.8.10
          "$s[$n = 2]",
          RF,
          // Note of section 19.8.8.8: $node//section/head is not a scanning
          // expression
          "$n/(descendant::section/head)",
          CC);
    // Ascent: references are climbing
    assertEquals (LM, _classify ("$n", _streamingParamContext ().setStreamingParamPosture (EPosture.CLIMBING)));
    // A repeatable reference in a higher-order operand stays motionless
    assertEquals (GM,
                  _classify ("for $i in 1 to 2 return name($n)",
                             _streamingParamContext ().setStreamingParamRepeatable (true)));
    // The pattern rule (section 19.8.10, third bullet)
    assertFalse (XPathStreamability.getPatternStreamability (_parsePattern ("p[@a = $n]"),
                                                             _streamingParamContext ().setStreamingParamRepeatable (true))
                                   .isStreamable ());
    // A parameter registered without a posture fails closed
    assertEquals (RF, _classify ("$n", _striding ().addStreamingParam (VAR_N)));
    assertEquals (RF,
                  _classify ("$n", _striding ().addStreamingParam (VAR_N).setStreamingParamPosture (EPosture.GROUNDED)));
    // A variable known to hold a map: the absorption note of section 19.8.8.11
    _run (_striding ().addMapArrayVariable (VAR_M), "$m(@type)", GM, "$m(a)", GC, "$f(@type)", RF);
    // getAsInHigherOrder marks the context as inside a repeated operand
    final StreamContext aHigherOrder = _streamingParamContext ().getAsInHigherOrder ();
    assertTrue (aHigherOrder.isInHigherOrder ());
    assertSame (aHigherOrder, aHigherOrder.getAsInHigherOrder ());
    assertEquals (RF, _classify ("$n", aHigherOrder));
  }

  /**
   * The definite flag is raised by rules the Recommendation states
   * unconditionally and left alone where the analysis merely declines.
   */
  @Test
  public void testDefinite ()
  {
    final Object [] aCases = { "last()",
                               Boolean.TRUE,
                               // two consuming operands
                               "price - discount",
                               Boolean.TRUE,
                               // Section 19.8.8.6
                               ". treat as document-node(element(a))",
                               Boolean.TRUE,
                               // non-motionless predicate
                               "*[b]",
                               Boolean.TRUE,
                               // Section 19.8.8.10
                               "(a)[b]",
                               Boolean.TRUE,
                               // Section 19.8.9.4
                               "current-group()",
                               Boolean.TRUE,
                               // navigation stated by section 19.8.9.17
                               "reverse(*)",
                               Boolean.TRUE,
                               // higher-order consuming
                               "for $i in 1 to 3 return salary",
                               Boolean.TRUE,
                               // climbing absorption
                               "string(..)",
                               Boolean.TRUE,
                               // grounded: nothing to report
                               "$v//section/head",
                               Boolean.FALSE,
                               "(1, 2)[1]//section/head",
                               Boolean.FALSE,
                               // unknown function
                               "my:f(a)",
                               Boolean.FALSE,
                               // table default
                               "a/following::b",
                               Boolean.FALSE,
                               // navigation fall-back
                               "let $x := @a return 1",
                               Boolean.FALSE,
                               // Section 19.8.4.24 navigation
                               "map{'k': @a}",
                               Boolean.TRUE };
    for (int i = 0; i < aCases.length; i += 2)
    {
      final String sExpr = (String) aCases[i];
      final MutableBoolean aDefinite = new MutableBoolean (false);
      XPathStreamability.getStreamability (_parse (sExpr), _striding ().setDefiniteFlag (aDefinite));
      assertEquals (sExpr, ((Boolean) aCases[i + 1]).booleanValue (), aDefinite.booleanValue ());
    }

    // A RootedPath start reports its non-scanning verdict when roaming
    final MutableBoolean aDefinite = new MutableBoolean (false);
    final StreamContext aCtx = _streamingParamContext ().setDefiniteFlag (aDefinite);
    assertEquals (RF, XPathStreamability.getStreamability (_parse ("$n//section/head[1]"), aCtx).toString ());
    assertTrue ("$n//section/head[1]: a RootedPath is never a scanning expression", aDefinite.booleanValue ());
    aDefinite.set (false);
    XPathStreamability.getStreamability (_parse ("(1 to 2, a)//section/head"), aCtx);
    // Must not fail without a flag
    _striding ().markDefinite ();
  }

  /** Extends the examples of section 19.8.10. */
  @Test
  public void testPatternsMore ()
  {
    for (final String sPattern : new String [] { "p[@status = $status-codes[1]]",
                                                 "p[@code = $status[last()]]",
                                                 "(p|q)[@a]",
                                                 "(p except q)[@a]",
                                                 "(p intersect q)",
                                                 "p/q[@a]",
                                                 "text()[. = current()]",
                                                 "@a[. = current()]",
                                                 "p//q",
                                                 "node()",
                                                 "p[@a][@b]" })
      assertEquals (sPattern,
                    GM,
                    XPathStreamability.getPatternStreamability (_parsePattern (sPattern), _striding ()).toString ());
    for (final String sPattern : new String [] { "$doc//p",
                                                 "(p|q)[1]",
                                                 "(p|q)[b]",
                                                 "p[function-lookup(xs:QName('fn:true'), 0)()]",
                                                 "p[position()]",
                                                 "p[. = current()]",
                                                 "key('k', 'v')",
                                                 "doc('x')//p",
                                                 "(p|$v)[@a]",
                                                 "p[@a][b]",
                                                 "p/q[last()]" })
      assertEquals (sPattern,
                    RF,
                    XPathStreamability.getPatternStreamability (_parsePattern (sPattern), _striding ()).toString ());
    assertEquals (GM, XPathStreamability.getPatternStreamability ((XsltPattern) null, _striding ()).toString ());
    assertEquals (GM, XPathStreamability.getPatternStreamability ((IExpr) null, _striding ()).toString ());
  }

  /** The predicate patterns ".[p]", which go-xslt maps to a self::node() step. */
  @Test
  public void testPredicatePatterns ()
  {
    for (final String sPattern : new String [] { ".", ".[@a]", ".[@a][name() = 'x']" })
      assertEquals (sPattern,
                    GM,
                    XPathStreamability.getPatternStreamability (_parsePattern (sPattern), _striding ()).toString ());
    for (final String sPattern : new String [] { ".[b]", ".[1]", ".[. = 'x']" })
      assertEquals (sPattern,
                    RF,
                    XPathStreamability.getPatternStreamability (_parsePattern (sPattern), _striding ()).toString ());
  }

  @NonNull
  private static IExpr _patternExpr (@NonNull final String sPattern)
  {
    return ((CompiledXsltPattern) _parsePattern (sPattern)).getPatternExpr ();
  }

  /** The static "cannot have children" test of section 19.8.1. */
  @Test
  public void testChildless ()
  {
    final Object [] aPatterns = { "@a",
                                  Boolean.TRUE,
                                  "text() | comment()",
                                  Boolean.TRUE,
                                  "a/@b",
                                  Boolean.TRUE,
                                  "processing-instruction()",
                                  Boolean.TRUE,
                                  "namespace::*",
                                  Boolean.TRUE,
                                  "a",
                                  Boolean.FALSE,
                                  "@a | b",
                                  Boolean.FALSE,
                                  ".",
                                  Boolean.FALSE,
                                  "/",
                                  Boolean.FALSE,
                                  "(a|@b)[1]",
                                  Boolean.FALSE,
                                  "$v",
                                  Boolean.FALSE,
                                  "(@a|@b)",
                                  Boolean.TRUE };
    for (int i = 0; i < aPatterns.length; i += 2)
    {
      final String sPattern = (String) aPatterns[i];
      assertEquals (sPattern,
                    ((Boolean) aPatterns[i + 1]).booleanValue (),
                    XPathStreamability.isPatternChildless (_patternExpr (sPattern)));
    }
    assertFalse (XPathStreamability.isPatternChildless (null));

    final Object [] aExprs = { "@a",
                               Boolean.TRUE,
                               "1",
                               Boolean.TRUE,
                               "name()",
                               Boolean.TRUE,
                               "1 + 2",
                               Boolean.TRUE,
                               "'a' || 'b'",
                               Boolean.TRUE,
                               "1 to 2",
                               Boolean.TRUE,
                               "@a = 1",
                               Boolean.TRUE,
                               "-1",
                               Boolean.TRUE,
                               "@a cast as xs:string",
                               Boolean.TRUE,
                               ". instance of node()",
                               Boolean.TRUE,
                               "map{}",
                               Boolean.TRUE,
                               "[]",
                               Boolean.TRUE,
                               "function() {1}",
                               Boolean.TRUE,
                               "name#1",
                               Boolean.TRUE,
                               "$v",
                               Boolean.TRUE,
                               "a!@b",
                               Boolean.TRUE,
                               "a!b",
                               Boolean.FALSE,
                               "@a except @b",
                               Boolean.TRUE,
                               "a except @b",
                               Boolean.FALSE,
                               "@a | text()",
                               Boolean.TRUE,
                               "@a | b",
                               Boolean.FALSE,
                               "if (a) then @b else 'x'",
                               Boolean.TRUE,
                               "if (a) then b else 'x'",
                               Boolean.FALSE,
                               "(@a, 1)",
                               Boolean.TRUE,
                               "(@a, b)",
                               Boolean.FALSE,
                               "(@a)[1]",
                               Boolean.TRUE,
                               "a",
                               Boolean.FALSE,
                               ".",
                               Boolean.FALSE,
                               "copy-of(.)",
                               Boolean.FALSE,
                               "head(@a)",
                               Boolean.FALSE,
                               "current()",
                               Boolean.FALSE,
                               "a/string()",
                               Boolean.FALSE,
                               "/",
                               Boolean.FALSE,
                               "()",
                               Boolean.FALSE,
                               "$f(1)",
                               Boolean.FALSE };
    for (int i = 0; i < aExprs.length; i += 2)
    {
      final String sExpr = (String) aExprs[i];
      assertEquals (sExpr, ((Boolean) aExprs[i + 1]).booleanValue (), XPathStreamability.isChildless (_parse (sExpr)));
    }
    final IExpr aDot = _parse (".");
    assertTrue (XPathStreamability.isChildless (aDot, new StreamContext (EPosture.GROUNDED).setContextChildless (true)));
    assertFalse (XPathStreamability.isChildless (aDot, new StreamContext (EPosture.GROUNDED)));
    assertTrue (XPathStreamability.isChildless (_parse ("current()"),
                                                new StreamContext (EPosture.GROUNDED).setCurrentChildless (true)));
    assertFalse ("a streaming parameter may hold elements",
                 XPathStreamability.isChildless (_parse ("$n"),
                                                 new StreamContext (EPosture.GROUNDED).addStreamingParam (VAR_N)));
    assertTrue (XPathStreamability.isChildless (null));
    assertTrue (XPathStreamability.isChildless (null, new StreamContext (EPosture.GROUNDED)));
  }

  /** The call search used for the grouping-function boundary rule of section 19.8.4. */
  @Test
  public void testUsesFunction ()
  {
    for (final String sExpr : new String [] { "current-group()",
                                              "count(current-group())",
                                              "current-group#0",
                                              "a[current-group()]",
                                              "(1)[current-group()]",
                                              "1 + current-group()",
                                              "-current-group()",
                                              "a | current-group()",
                                              "a except current-group()",
                                              "current-group() = 1",
                                              "'a' || current-group()",
                                              "(1, current-group())",
                                              "1 to current-group()",
                                              "for $x in 1 return current-group()",
                                              "let $x := 1 return current-group()",
                                              "some $x in 1 satisfies current-group()",
                                              "if (1) then current-group() else 2",
                                              "map{1: current-group()}",
                                              "[current-group()]",
                                              "function() { current-group() }",
                                              "map{}?(current-group())",
                                              "$f(current-group())",
                                              "current-group() instance of item()",
                                              "current-group() treat as item()*",
                                              "current-group() cast as xs:string",
                                              "current-group() => count()",
                                              "1 => $f(current-group())",
                                              "a ! current-group()",
                                              "a/current-group()" })
      assertTrue (sExpr, XPathStreamability.usesFunction (_parse (sExpr), "current-group"));
    for (final String sExpr : new String [] { "count(a)", "current-grouping-key()", "my:current-group()" })
      assertFalse (sExpr, XPathStreamability.usesFunction (_parse (sExpr), "current-group"));
  }

  @NonNull
  private static IExpr _parseNestedNegations (final int nDepth)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("-(");
    aSB.append ("@a");
    for (int i = 0; i < nDepth; ++i)
      aSB.append (')');
    return _parse (aSB.toString ());
  }

  /** Expressions nested deeper than {@link XPathStreamability#MAX_DEPTH} fail closed. */
  @Test
  public void testDeepNestingFailsClosed ()
  {
    final String sShallow = XPathStreamability.getStreamability (_parseNestedNegations (1), _striding ()).toString ();
    assertEquals (sShallow,
                  XPathStreamability.getStreamability (_parseNestedNegations (XPathStreamability.MAX_DEPTH - 10), _striding ())
                                    .toString ());
    // 4,000 nested negations stay below the parser limit, but not below the
    // depth limit of the walk: roaming and free-ranging, never a definite
    // verdict
    final MutableBoolean aDefinite = new MutableBoolean (false);
    assertEquals (RF,
                  XPathStreamability.getStreamability (_parseNestedNegations (4000), _striding ().setDefiniteFlag (aDefinite))
                                    .toString ());
    assertFalse (aDefinite.booleanValue ());
    // The depth count was reset
    assertEquals (sShallow, XPathStreamability.getStreamability (_parseNestedNegations (1), _striding ()).toString ());
  }
}
