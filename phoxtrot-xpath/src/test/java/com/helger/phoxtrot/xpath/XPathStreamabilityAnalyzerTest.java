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
}
