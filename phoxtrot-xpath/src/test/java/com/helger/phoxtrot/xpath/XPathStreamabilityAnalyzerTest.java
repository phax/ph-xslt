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
package com.helger.phoxtrot.xpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.stream.EStreamFunctionCategory;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for {@link XPathStreamabilityAnalyzer}.
 *
 * @author Philip Helger
 */
public final class XPathStreamabilityAnalyzerTest
{
  private static final String NS_F = "urn:f";
  private static final XdmQName CHECK = new XdmQName (NS_F, "check", "f");
  private static final SeqType STRING_OPT = SeqType.of (ItemType.atomic (EAtomicType.STRING), EOccurrence.ZERO_OR_ONE);

  @NonNull
  private static StaticContext _createStaticContext ()
  {
    final FunctionLibrary aLib = FunctionLibrary.getDefault ().createChild ();
    aLib.register (CHECK, List.of (STRING_OPT), STRING_OPT, (aCtx, aArgs) -> XdmSequence.EMPTY);
    final StaticContext ret = new StaticContext ();
    ret.declareNamespace ("f", NS_F);
    ret.declareNamespace ("cac", "urn:cac");
    ret.declareNamespace ("cbc", "urn:cbc");
    ret.declareVariable (new XdmQName ("v"));
    ret.declareVariable (new XdmQName ("ctx"));
    ret.setFunctionLibrary (aLib);
    return ret;
  }

  private static void _assert (@NonNull final XPathStreamabilityResult aResult,
                               @NonNull final EStreamabilityVerdict eVerdict,
                               @Nullable final EStreamabilityConstruct eConstruct,
                               @Nullable final String sName)
  {
    assertEquals (aResult.toString (), eVerdict, aResult.getVerdict ());
    if (eConstruct == null)
      assertNull (aResult.toString (), aResult.getReason ());
    else
    {
      assertEquals (aResult.toString (), eConstruct, aResult.getReason ().construct ());
      assertEquals (aResult.toString (), sName, aResult.getReason ().name ());
    }
  }

  @Test
  public void testExpressionsWithStreamedContext ()
  {
    final StaticContext aSC = _createStaticContext ();
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ();
    assertTrue (aAnalyzer.isContextStreamed ());

    final XPathStreamabilityResult aAttr = aAnalyzer.analyze ("@id = 'x'", aSC);
    _assert (aAttr, EStreamabilityVerdict.MOTIONLESS, null, null);
    assertTrue (aAttr.isMotionless ());
    assertTrue (aAttr.isStreamable ());

    _assert (aAnalyzer.analyze ("exists(cbc:ID)", aSC),
             EStreamabilityVerdict.CONSUMING,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:ID");
    _assert (aAnalyzer.analyze ("ancestor::cac:Invoice/@id", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyze ("$v + 1", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);

    final XPathStreamabilityResult aSibling = aAnalyzer.analyze ("preceding-sibling::cac:Line", aSC);
    _assert (aSibling,
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "preceding-sibling::cac:Line");
    assertFalse (aSibling.isStreamable ());

    // Two consuming operands: the operator is the reason
    _assert (aAnalyzer.analyze ("count(cac:A) + count(cac:B)", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.EXPRESSION,
             "operator add");
    // The reason is found inside a predicate
    _assert (aAnalyzer.analyze ("cac:Line[@id = following::cac:Line/@id]", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "following::cac:Line");
    // An extension function that is not declared fails closed
    _assert (aAnalyzer.analyze ("f:check(@id)", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "f:check#1");
  }

  @Test
  public void testRootedPaths ()
  {
    final StaticContext aSC = _createStaticContext ();
    final XPathStreamabilityResult aResult = new XPathStreamabilityAnalyzer ().analyze ("count(//cac:Line)", aSC);
    _assert (aResult, EStreamabilityVerdict.FREE_RANGING, EStreamabilityConstruct.AXIS_STEP, "//");
    // The first step from which the path is free-ranging
    _assert (new XPathStreamabilityAnalyzer ().analyze ("cac:Line/preceding::cac:X/@id", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "preceding::cac:X");
  }

  @Test
  public void testGroundedContext ()
  {
    final StaticContext aSC = _createStaticContext ();
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ().setContextStreamed (false);
    assertFalse (aAnalyzer.isContextStreamed ());
    // A buffered context node may be navigated freely
    _assert (aAnalyzer.analyze ("count(cac:A) + count(cac:B)", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyze ("preceding-sibling::cac:Line", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
  }

  @Test
  public void testStreamedVariables ()
  {
    final StaticContext aSC = _createStaticContext ();
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ().addStreamedVariable (new XdmQName ("ctx"));
    _assert (aAnalyzer.analyze ("$ctx/@id", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyze ("$ctx/cbc:ID", aSC),
             EStreamabilityVerdict.CONSUMING,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:ID");
    // Not declared as streamed: grounded
    _assert (new XPathStreamabilityAnalyzer ().analyze ("$ctx/cbc:ID", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
  }

  @Test
  public void testDeclaredFunctions ()
  {
    final StaticContext aSC = _createStaticContext ();
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ().addFunction (CHECK,
                                                                                                 EStreamFunctionCategory.UNCLASSIFIED,
                                                                                                 List.of (STRING_OPT));
    // The string parameter atomises the attribute
    _assert (aAnalyzer.analyze ("f:check(@id)", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyze ("f:check(cbc:ID)", aSC),
             EStreamabilityVerdict.CONSUMING,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:ID");
  }

  @Test
  public void testPatterns ()
  {
    final StaticContext aSC = _createStaticContext ();
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ();
    _assert (aAnalyzer.analyzePattern ("cac:InvoiceLine", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyzePattern ("cac:InvoiceLine[@id = 'x']", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyzePattern ("cac:InvoiceLine[cbc:Quantity < 0]", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:Quantity");
    _assert (aAnalyzer.analyzePattern ("cac:InvoiceLine[1]", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.POSITIONAL_PREDICATE,
             "predicate");
    _assert (aAnalyzer.analyzePattern ("cac:A | cac:B[cbc:Quantity]", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:Quantity");
    // A rooted path: the start is the reason
    _assert (aAnalyzer.analyzePattern ("$v/cac:A", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.VARIABLE,
             "$v");
    // With a buffered context, the predicates may read the subtree
    _assert (new XPathStreamabilityAnalyzer ().setContextStreamed (false)
                                              .analyzePattern ("cac:InvoiceLine[cbc:Quantity < 0]", aSC),
             EStreamabilityVerdict.MOTIONLESS,
             null,
             null);
  }
  @NonNull
  private static StaticContext _createXsltStaticContext ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefaultWithXsltSignatures ();
    aLib.register (CHECK, List.of (STRING_OPT), STRING_OPT, (aCtx, aArgs) -> XdmSequence.EMPTY);
    final StaticContext ret = new StaticContext ();
    ret.declareNamespace ("f", NS_F);
    ret.declareNamespace ("cac", "urn:cac");
    ret.declareNamespace ("cbc", "urn:cbc");
    ret.declareVariable (new XdmQName ("v"));
    ret.declareVariable (new XdmQName ("ext"));
    ret.declareVariable (new XdmQName ("node"));
    ret.setFunctionLibrary (aLib);
    return ret;
  }

  @Test
  public void testBurstContext ()
  {
    final StaticContext aSC = _createXsltStaticContext ();
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ().setContextMode (EStreamContextMode.BURST);
    assertEquals (EStreamContextMode.BURST, aAnalyzer.getContextMode ());
    assertFalse (aAnalyzer.isContextStreamed ());

    // Motionless when streamed stays motionless
    _assert (aAnalyzer.analyze ("@id = 'x'", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyze ("ancestor::cac:X/@id", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
    _assert (aAnalyzer.analyze ("ancestor::cac:X/@id = cbc:ID", aSC),
             EStreamabilityVerdict.BURST,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:ID");

    // Needs only the subtree: burst, with the reason of the streamed analysis
    for (final String sExpr : new String [] { "cbc:ID",
                                              "count(cac:Line) > 0",
                                              "cbc:A = 'x' or cbc:B = 'y'",
                                              "exists(cbc:A) and exists(cbc:B)",
                                              "sum(cac:L/cbc:A) = cbc:Total",
                                              "let $x := @id return $x = 'a'",
                                              "every $l in cac:Line satisfies $l/cbc:Q > 0",
                                              "cac:Line[cbc:Q > 0]/following-sibling::cac:Line",
                                              "cac:Line/cbc:A/../cbc:B",
                                              "string(.) = 'x'",
                                              "current()/cbc:ID = cbc:ID",
                                              "reverse(cac:Line)[1]/cbc:ID",
                                              "key('k', @id, .)",
                                              "count(document('x.xml')//cbc:ID) = count(cbc:ID)",
                                              "cac:Line[last()]/cbc:ID = cac:Line[1]/cbc:ID" })
    {
      final XPathStreamabilityResult aResult = aAnalyzer.analyze (sExpr, aSC);
      assertEquals (sExpr + ": " + aResult, EStreamabilityVerdict.BURST, aResult.getVerdict ());
      assertTrue (sExpr, aResult.isBurstStreamable ());
      assertFalse (sExpr, aResult.isStreamable ());
      assertTrue (sExpr, aResult.getReason () != null);
    }

    // Leaves the subtree
    _assert (aAnalyzer.analyze ("../cbc:ID", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "parent::node()");
    _assert (aAnalyzer.analyze ("//cbc:ID", aSC), EStreamabilityVerdict.FREE_RANGING, EStreamabilityConstruct.AXIS_STEP, "//");
    _assert (aAnalyzer.analyze ("/Invoice/cbc:ID", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.ROOT,
             "/");
    _assert (aAnalyzer.analyze ("preceding-sibling::cac:Line", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "preceding-sibling::cac:Line");
    _assert (aAnalyzer.analyze ("cbc:A/preceding::cbc:B", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "preceding::cbc:B");
    _assert (aAnalyzer.analyze ("key('k', @id)", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "key#2");
    _assert (aAnalyzer.analyze ("key('k', @id, ..)", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "parent::node()");
    _assert (aAnalyzer.analyze ("id('x')", aSC), EStreamabilityVerdict.FREE_RANGING, EStreamabilityConstruct.FUNCTION, "id#1");
    _assert (aAnalyzer.analyze ("count(root()//cbc:ID)", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "root#0");
    _assert (aAnalyzer.analyze ("path(cbc:ID) = 'x'", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "path#1");
    // The string value of an ancestor needs its whole subtree
    _assert (aAnalyzer.analyze ("string(ancestor::cac:X) = cbc:ID", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "ancestor::cac:X");
    _assert (aAnalyzer.analyze ("let $p := .. return $p/cbc:X", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "parent::node()");
    _assert (aAnalyzer.analyze ("ancestor::cac:X[cbc:ID]/@id = cbc:ID", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "ancestor::cac:X");
    // Undeclared extension functions fail closed
    _assert (aAnalyzer.analyze ("f:check(@id)", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "f:check#1");
    // Function items that may carry nodes of the document fail closed
    _assert (aAnalyzer.analyze ("filter(cac:Line, function ($l) { $l/cbc:Q > 0 })", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "filter#2");
    _assert (aAnalyzer.analyze ("map { 'a' : cbc:A }?a/../..", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.EXPRESSION,
             "mapconstructor");
  }

  @Test
  public void testBurstVariablesAndFunctions ()
  {
    final StaticContext aSC = _createXsltStaticContext ();
    final XdmQName aExt = new XdmQName ("ext");
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ().setContextMode (EStreamContextMode.BURST)
                                                                                  .addStreamedVariable (new XdmQName ("node"))
                                                                                  .addExternalNodeVariable (aExt);
    // An undeclared variable is a grounded value
    _assert (aAnalyzer.analyze ("$v/cbc:X = cbc:Y", aSC),
             EStreamabilityVerdict.BURST,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:Y");
    // A variable of the subtree
    assertEquals (EStreamabilityVerdict.BURST, aAnalyzer.analyze ("$node/cbc:X = cbc:Y", aSC).getVerdict ());
    // An external node variable can be compared, not navigated from
    assertEquals (EStreamabilityVerdict.BURST, aAnalyzer.analyze ("$ext = cbc:ID", aSC).getVerdict ());
    _assert (aAnalyzer.analyze ("$ext/cbc:X = cbc:ID", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.VARIABLE,
             "$ext");
    // A local variable shadows the external one
    assertEquals (EStreamabilityVerdict.BURST,
                  aAnalyzer.analyze ("for $ext in cac:Line return $ext/cbc:X = cbc:ID", aSC).getVerdict ());

    // Declared functions
    final SeqType aNodes = SeqType.of (ItemType.node (NodeTest.ANY_NODE), EOccurrence.ZERO_OR_MORE);
    aAnalyzer.addFunction (CHECK, EStreamFunctionCategory.ABSORBING, List.of (aNodes));
    assertEquals (EStreamabilityVerdict.BURST, aAnalyzer.analyze ("f:check(cbc:ID) = cbc:X", aSC).getVerdict ());
    _assert (aAnalyzer.analyze ("f:check(..) = cbc:X", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "parent::node()");
    aAnalyzer.addFunction (CHECK, EStreamFunctionCategory.UNCLASSIFIED, List.of (aNodes));
    _assert (aAnalyzer.analyze ("f:check(cbc:ID) = cbc:X", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.FUNCTION,
             "f:check#1");

    // Patterns are not changed by the burst mode
    _assert (aAnalyzer.analyzePattern ("cac:InvoiceLine[cbc:Quantity < 0]", aSC),
             EStreamabilityVerdict.FREE_RANGING,
             EStreamabilityConstruct.AXIS_STEP,
             "child::cbc:Quantity");
    _assert (aAnalyzer.analyzePattern ("cac:InvoiceLine[@id]", aSC), EStreamabilityVerdict.MOTIONLESS, null, null);
  }

  @Test
  public void testContextModes ()
  {
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ();
    assertEquals (EStreamContextMode.STREAMED, aAnalyzer.getContextMode ());
    aAnalyzer.setContextStreamed (false);
    assertEquals (EStreamContextMode.GROUNDED, aAnalyzer.getContextMode ());
    aAnalyzer.setContextStreamed (true);
    assertEquals (EStreamContextMode.STREAMED, aAnalyzer.getContextMode ());
    // Without burst mode, external node variables are grounded
    aAnalyzer.addExternalNodeVariable (new XdmQName ("ext"));
    assertEquals (EStreamabilityVerdict.MOTIONLESS,
                  aAnalyzer.analyze ("$ext/cbc:X = 'a'", _createXsltStaticContext ()).getVerdict ());
  }
}
