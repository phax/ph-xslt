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
package com.helger.xpath.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FnAccessor;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * Test class for the early exit of existence tests and effective boolean values
 * in {@link XPathEvaluator} (XPath 3.1 section 2.3.4).
 *
 * @author Philip Helger
 */
public final class EarlyExitTest
{
  private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
  private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

  private static final String LINE_BV = "<cac:LineResponse><cac:Response><cac:Status>" +
                                        "<cbc:StatusReasonCode>BV</cbc:StatusReasonCode>" +
                                        "</cac:Status></cac:Response></cac:LineResponse>";
  // A duplicated cac:Status: normalize-space gets two items (XPTY0004)
  private static final String LINE_DUP = "<cac:LineResponse><cac:Response><cac:Status>" +
                                         "<cbc:StatusReasonCode>BV</cbc:StatusReasonCode>" +
                                         "</cac:Status><cac:Status>" +
                                         "<cbc:StatusReasonCode>BW</cbc:StatusReasonCode>" +
                                         "</cac:Status></cac:Response></cac:LineResponse>";

  private static final String MLS_TEST = "exists(cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']])";

  private static final String DOC = "<r><x a='1'>p</x><x>q</x><x a='3'>r</x><y/></r>";

  @NonNull
  private static IXdmNode _parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException (ex);
    }
  }

  @NonNull
  private static IXdmNode _mls (@NonNull final String sLines)
  {
    final IXdmNode aDoc = _parse ("<ApplicationResponse xmlns:cac='" +
                                  NS_CAC +
                                  "' xmlns:cbc='" +
                                  NS_CBC +
                                  "'><cac:DocumentResponse>" +
                                  sLines +
                                  "</cac:DocumentResponse></ApplicationResponse>");
    // The cac:DocumentResponse element
    return aDoc.getChildAtIndex (0).getChildAtIndex (0);
  }

  @NonNull
  private static StaticContext _createStaticContext ()
  {
    final StaticContext ret = new StaticContext ();
    ret.declareNamespace ("cac", NS_CAC);
    ret.declareNamespace ("cbc", NS_CBC);
    return ret;
  }

  @NonNull
  private static String _eval (@NonNull final String sExpr, @NonNull final IXdmItem aContext)
  {
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, _createStaticContext ());
    final DynamicContext aCtx = aCompiled.createDynamicContext ().withFocus (aContext, 1, 1);
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aCompiled.evaluate (aCtx))
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (FnAccessor.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  private static boolean _ebv (@NonNull final String sExpr, @NonNull final IXdmItem aContext)
  {
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, _createStaticContext ());
    return aCompiled.evaluateEffectiveBooleanValue (aCompiled.createDynamicContext ().withFocus (aContext, 1, 1));
  }

  private static void _assertError (@NonNull final String sCode,
                                    @NonNull final String sExpr,
                                    @NonNull final IXdmItem aContext)
  {
    try
    {
      _eval (sExpr, aContext);
      fail (sExpr + " should raise " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr, sCode, ex.getCode ());
    }
  }

  private static void _assertEbvError (@NonNull final String sCode,
                                       @NonNull final String sExpr,
                                       @NonNull final IXdmItem aContext)
  {
    try
    {
      _ebv (sExpr, aContext);
      fail (sExpr + " should raise " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr, sCode, ex.getCode ());
    }
  }

  @Test
  public void testMlsDuplicatedStatus ()
  {
    // The matching line comes first: the line with the duplicate is not looked at
    final IXdmNode aMatchFirst = _mls (LINE_BV + LINE_DUP);
    assertEquals ("true", _eval (MLS_TEST, aMatchFirst));
    assertEquals ("false", _eval ("empty(cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']])",
                                  aMatchFirst));
    assertEquals ("true", _eval ("boolean(cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']])",
                                 aMatchFirst));
    assertEquals ("false", _eval ("not(cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']])",
                                  aMatchFirst));
    assertTrue (_ebv ("cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']]",
                      aMatchFirst));
    assertTrue (_ebv ("//cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']]",
                      aMatchFirst));
    // A full evaluation still sees the duplicate
    _assertError ("XPTY0004",
                  "count(cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']])",
                  aMatchFirst);

    // The line with the duplicate comes first: the error is raised before a match
    final IXdmNode aDupFirst = _mls (LINE_DUP + LINE_BV);
    _assertError ("XPTY0004", MLS_TEST, aDupFirst);
  }

  @Test
  public void testBooleanContexts ()
  {
    final IXdmNode aMatchFirst = _mls (LINE_BV + LINE_DUP);
    final String sLines = "cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']]";
    assertEquals ("yes", _eval ("if (" + sLines + ") then 'yes' else 'no'", aMatchFirst));
    assertEquals ("true", _eval ("true() and " + sLines, aMatchFirst));
    assertEquals ("true", _eval ("false() or " + sLines, aMatchFirst));
    assertEquals ("true", _eval ("some $d in . satisfies $d/" + sLines, aMatchFirst));
    // A predicate whose value is a node sequence
    assertEquals ("1", _eval ("count(.[" + sLines + "])", aMatchFirst));
    // Parenthesised
    assertEquals ("true", _eval ("exists((" + sLines + "))", aMatchFirst));
    // Left-to-right: the left operand is evaluated first and raises
    _assertError ("XPTY0004",
                  "normalize-space(cac:LineResponse/cac:Response/cac:Status/cbc:StatusReasonCode) = 'BV' or " + sLines,
                  aMatchFirst);
  }

  @Test
  public void testResults ()
  {
    final IXdmNode aDoc = _parse (DOC);
    final IXdmNode aRoot = aDoc.getChildAtIndex (0);
    assertEquals ("true", _eval ("exists(x)", aRoot));
    assertEquals ("false", _eval ("exists(z)", aRoot));
    assertEquals ("true", _eval ("exists(x[@a = 3])", aRoot));
    assertEquals ("false", _eval ("exists(x[@a = 2])", aRoot));
    assertEquals ("true", _eval ("exists(//x[@a])", aRoot));
    assertEquals ("true", _eval ("exists(/r/x[. = 'q'])", aRoot));
    assertEquals ("false", _eval ("exists(/r/x[. = 'z'])", aRoot));
    assertEquals ("true", _eval ("empty(/r/x[. = 'z'])", aRoot));
    assertEquals ("true", _eval ("exists(x/@a)", aRoot));
    assertEquals ("true", _eval ("exists(y/preceding-sibling::x[1][@a = 3])", aRoot));
    assertEquals ("false", _eval ("exists(y/preceding-sibling::x[1][@a = 1])", aRoot));
    assertEquals ("true", _eval ("exists(y/preceding-sibling::x[last()][@a = 1])", aRoot));
    // Several context nodes, also through the name index
    assertEquals ("true", _eval ("exists(/r/x/following-sibling::y)", aRoot));
    assertEquals ("true", _eval ("exists((x, y)//text()[. = 'r'])", aRoot));
    assertEquals ("true", _eval ("exists(/descendant::x//text())", aRoot));
  }

  @Test
  public void testPositionalPredicates ()
  {
    final IXdmNode aRoot = _parse (DOC).getChildAtIndex (0);
    // Earlier predicates filter completely, the last one counts on their result
    assertEquals ("true", _eval ("exists(x[@a][2][. = 'r'])", aRoot));
    assertEquals ("false", _eval ("exists(x[@a][2][. = 'q'])", aRoot));
    assertEquals ("true", _eval ("exists(x[2])", aRoot));
    assertEquals ("false", _eval ("exists(x[4])", aRoot));
    assertEquals ("true", _eval ("exists(x[last()][. = 'r'])", aRoot));
    assertEquals ("false", _eval ("exists(x[last()][. = 'p'])", aRoot));
    assertEquals ("true", _eval ("exists(x[position() = 3])", aRoot));
    assertEquals ("true", _eval ("exists(x[@a][last()])", aRoot));
    assertEquals ("false", _eval ("exists(x[@a][3])", aRoot));
    assertEquals ("true", _eval ("let $n := 2 return exists(x[$n])", aRoot));
    assertEquals ("false", _eval ("let $n := 5 return exists(x[$n])", aRoot));
    // Atomic sequences
    assertEquals ("true", _eval ("exists((1, 2, 3)[. = 2])", aRoot));
    assertEquals ("false", _eval ("exists((1, 2, 3)[. = 4])", aRoot));
    assertEquals ("true", _eval ("exists((1, 2, 3)[3])", aRoot));
    assertEquals ("false", _eval ("exists((1, 2, 3)[4])", aRoot));
    assertEquals ("true", _eval ("exists((5, 6, 7)[last() - 1][. = 6])", aRoot));
  }

  @Test
  public void testEffectiveBooleanValue ()
  {
    final IXdmNode aRoot = _parse (DOC).getChildAtIndex (0);
    // One atomic value decides by its value
    assertTrue (_ebv ("(0, 1, 2)[. = 2]", aRoot));
    assertFalse (_ebv ("(0, 1, 2)[. = 0]", aRoot));
    assertFalse (_ebv ("(0, 1, 2)[1]", aRoot));
    assertTrue (_ebv ("(0, 1, 2)[2]", aRoot));
    assertFalse (_ebv ("('', 'a')[1]", aRoot));
    assertFalse (_ebv ("(0, 1, 2)[. = 5]", aRoot));
    assertEquals ("false", _eval ("boolean((0, 1)[. = 0])", aRoot));
    assertEquals ("true", _eval ("not((0, 1)[. = 0])", aRoot));
    // A node first: true
    assertTrue (_ebv ("(x, 1)[true()]", aRoot));
    assertEquals ("true", _eval ("boolean((x, 1, 2)[true()])", aRoot));
    // An atomic value first and a second item: FORG0006
    _assertEbvError ("FORG0006", "(1, 2)", aRoot);
    _assertEbvError ("FORG0006", "(1, 2)[. > 0]", aRoot);
    _assertEbvError ("FORG0006", "(1, x)[true()]", aRoot);
    _assertError ("FORG0006", "boolean((1, 2)[. > 0])", aRoot);
    _assertError ("FORG0006", "not((1, 2)[. > 0])", aRoot);
    _assertError ("FORG0006", "if ((1, 2)[. > 0]) then 1 else 0", aRoot);
    _assertError ("FORG0006", "(1, 2)[. > 0] and true()", aRoot);
    // Not a node and not a single atomic value
    _assertEbvError ("FORG0006", "(map { }, 1)[1]", aRoot);
    // Errors in parts that decide the result are raised
    _assertError ("FOER0000", "exists(x[error()])", aRoot);
    _assertEbvError ("FOER0000", "x[if (@a = 3) then error() else false()]", aRoot);
    // A context item that is not a node is an error before any match
    _assertError ("XPTY0019", "exists((x, 1)/@a)", aRoot);
    _assertError ("XPTY0019", "exists((1, x)/@a)", aRoot);
  }
}
