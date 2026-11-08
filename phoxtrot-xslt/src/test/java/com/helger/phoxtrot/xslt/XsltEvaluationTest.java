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
package com.helger.phoxtrot.xslt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.StringJoiner;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.pattern.XsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPatternParser;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for standalone XPath evaluation against the declarations of a
 * compiled stylesheet ({@link XsltExecutable#createStaticContext()} and
 * {@link XsltEvaluation}).
 *
 * @author Philip Helger
 */
public final class XsltEvaluationTest
{
  private static final String SHEET = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                      " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:f='urn:f'>";
  private static final String DOC = "<r><item id='a'>1</item><item id='b'>2</item><item id='c'>3</item></r>";

  @NonNull
  private static XsltExecutable _compile (@NonNull final String sDeclarations)
  {
    return XsltCompiler.compile (SHEET + sDeclarations + XsltTestHelper.END, new XsltCompileOptions ());
  }

  @NonNull
  private static String _string (@NonNull final XdmSequence aSeq)
  {
    final StringJoiner aSJ = new StringJoiner (" ");
    for (final IXdmItem aItem : aSeq)
      aSJ.add (Atomizer.getStringValue (aItem));
    return aSJ.toString ();
  }

  @NonNull
  private static String _eval (@NonNull final XsltExecutable aSheet,
                               @NonNull final String sExpr,
                               @NonNull final XsltEvaluation aEval)
  {
    return _string (XPathCompiler.compile (sExpr, aSheet.createStaticContext ()).evaluate (aEval.createDynamicContext ()));
  }

  @NonNull
  private static XsltEvaluation _evaluation (@NonNull final XsltExecutable aSheet)
  {
    return aSheet.createEvaluation (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (DOC)));
  }

  @Test
  public void testFunctionsAndNamespaces ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:function name='f:double' as='xs:integer'><xsl:param name='n' as='xs:integer'/>" +
                                            "<xsl:sequence select='$n * 2'/></xsl:function>");
    final XsltEvaluation aEval = _evaluation (aSheet);
    // The prefix "f" is declared on the stylesheet element only
    assertEquals ("4", _eval (aSheet, "f:double(2)", aEval));
    assertEquals ("2 4 6", _eval (aSheet, "//item ! f:double(xs:integer(.))", aEval));
    // A function with another arity is a static error
    try
    {
      XPathCompiler.compile ("f:double(1, 2)", aSheet.createStaticContext ());
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPST0017", ex.getCode ());
    }
  }

  @Test
  public void testKeys ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:key name='k' match='item' use='@id'/>");
    final XsltEvaluation aEval = _evaluation (aSheet);
    assertEquals ("2", _eval (aSheet, "key('k', 'b')", aEval));
    assertEquals ("1 3", _eval (aSheet, "key('k', ('c', 'a'))", aEval));
    assertEquals ("", _eval (aSheet, "key('k', 'x')", aEval));
  }

  @Test
  public void testGlobalVariablesAndParameters ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:variable name='count' select='count(//item)'/>" +
                                            "<xsl:param name='p' select=\"'default'\"/>" +
                                            "<xsl:variable name='err' select='1 div 0'/>");
    // Global variables use the global context item
    assertEquals ("3 default", _eval (aSheet, "$count, $p", _evaluation (aSheet)));
    // A global variable that is never used raises nothing
    assertEquals ("ok", _eval (aSheet, "'ok'", _evaluation (aSheet)));

    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (DOC));
    aRequest.params ().put (new XdmQName ("p"), XdmSequence.of (XdmAtomic.untyped ("given")));
    assertEquals ("given", _eval (aSheet, "$p", aSheet.createEvaluation (aRequest)));
  }

  @Test
  public void testRequiredParameterMissing ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:param name='p' required='yes'/>");
    try
    {
      _evaluation (aSheet);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTDE0050", ex.getCode ());
    }
  }

  @Test
  public void testDecimalFormats ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:decimal-format name='de' decimal-separator=',' grouping-separator='.'/>");
    assertEquals ("1.234,5", _eval (aSheet, "format-number(1234.5, '#.##0,0', 'de')", _evaluation (aSheet)));
  }

  @Test
  public void testFocusCurrentAndHostDeclarations ()
  {
    final XsltExecutable aSheet = _compile ("");
    final XsltEvaluation aEval = _evaluation (aSheet);
    final IXdmNode aDoc = (IXdmNode) aEval.getGlobalContextItem ();
    // The global context item is the context item and the current item
    assertEquals ("3", _eval (aSheet, "count(current()//item)", aEval));

    // The host adds a namespace and a variable of its own
    final StaticContext aSC = aSheet.createStaticContext ();
    aSC.declareNamespace ("h", "urn:host");
    final XdmQName aVar = new XdmQName ("urn:host", "limit", "h");
    aSC.declareVariable (aVar);
    final IXdmItem aSecond = XPathCompiler.compile ("//item[2]", aSC).evaluate (aEval.createDynamicContext ()).getFirst ();
    final DynamicContext aCtx = aEval.createDynamicContext ()
                                     .withFocus (aSecond, 1, 1)
                                     .withCurrentItem (aSecond)
                                     .withVariable (aVar, XdmSequence.of (XdmAtomic.integer (1)));
    assertEquals ("b true",
                  _string (XPathCompiler.compile ("current()/@id, xs:integer(.) > $h:limit", aSC).evaluate (aCtx)));
    assertSame (aDoc, ((IXdmNode) aSecond).getRoot ());
  }

  @Test
  public void testPatterns ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:key name='k' match='item' use='@id'/><xsl:variable name='wanted' select=\"'b'\"/>");
    final XsltEvaluation aEval = _evaluation (aSheet);
    final XsltPattern aPattern = XsltPatternParser.parse ("item[@id = $wanted] | item[key('k', 'c') is .]",
                                                          aSheet.createStaticContext ());
    final XdmSequence aItems = XPathCompiler.compile ("//item", aSheet.createStaticContext ())
                                            .evaluate (aEval.createDynamicContext ());
    final DynamicContext aCtx = aEval.createDynamicContext ();
    assertFalse (aPattern.matches (aItems.get (0), aCtx));
    assertTrue (aPattern.matches (aItems.get (1), aCtx));
    assertTrue (aPattern.matches (aItems.get (2), aCtx));
  }

  @Test
  public void testWhitespaceStripping ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:strip-space elements='*'/>");
    final IXdmNode aDoc = XsltTestHelper.parse ("<r> <a/> </r>");
    final XsltEvaluation aEval = aSheet.createEvaluation (new XsltTransformRequest ().setGlobalContextItem (aDoc));
    // The stylesheet works on the stripped copy
    assertNotSame (aDoc, aEval.getGlobalContextItem ());
    assertEquals ("1", _eval (aSheet, "count(/r/node())", aEval));
  }

  @Test
  public void testNoGlobalContextItem ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:variable name='v' select='42'/>");
    final XsltEvaluation aEval = aSheet.createEvaluation (new XsltTransformRequest ());
    assertNull (aEval.getGlobalContextItem ());
    assertEquals ("42", _eval (aSheet, "$v", aEval));
    try
    {
      _eval (aSheet, "count(//item)", aEval);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0002", ex.getCode ());
    }
  }

  @Test
  public void testMessages ()
  {
    final XsltExecutable aSheet = _compile ("<xsl:function name='f:log'><xsl:param name='s'/>" +
                                            "<xsl:message select='$s'/><xsl:sequence select='$s'/></xsl:function>");
    final XsltEvaluation aEval = _evaluation (aSheet);
    assertEquals ("x", _eval (aSheet, "f:log('x')", aEval));
    assertEquals (1, aEval.getMessages ().size ());
  }
}
