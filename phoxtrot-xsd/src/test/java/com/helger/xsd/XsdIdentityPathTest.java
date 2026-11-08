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
package com.helger.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * {@link XsdIdentityPath} selects exactly the nodes of the equivalent XPath
 * expression, in the same order.
 *
 * @author Philip Helger
 */
public final class XsdIdentityPathTest
{
  private static final String DOC = "<r xmlns:p=\"urn:p\" xmlns:q=\"urn:q\" x=\"1\" p:x=\"2\">" +
                                    "<a id=\"1\"><b>t</b><!--c--><a id=\"2\"><b x=\"3\"/><c/><a id=\"3\"><b/></a></a><b/></a>" +
                                    "<p:a id=\"4\" p:y=\"5\"><p:b/><b/></p:a>" +
                                    "text<?pi d?>" +
                                    "<c><a id=\"5\"><b/><b/></a></c>" +
                                    "<q:a xmlns=\"urn:d\"><b/></q:a>" +
                                    "</r>";

  private static final String [] EXPRESSIONS = { ".",
                                                 "a",
                                                 "a/b",
                                                 "./a/./b",
                                                 "*",
                                                 "*/*",
                                                 "p:a",
                                                 "p:*",
                                                 "p:a/p:b",
                                                 "p:*/b",
                                                 "child::a/child::b",
                                                 ".//a",
                                                 ".//b",
                                                 ".//a/b",
                                                 ".//a/a/b",
                                                 ".//.",
                                                 ".//./b",
                                                 ".//*",
                                                 ".//p:*",
                                                 "a | a",
                                                 "c/a | a",
                                                 ".//b | a/b | .",
                                                 ".//a | .//p:a",
                                                 " a / b ",
                                                 "@x",
                                                 "@*",
                                                 "@p:x",
                                                 "@p:*",
                                                 "attribute::x",
                                                 "a/@id",
                                                 ".//a/@id",
                                                 ".//@id",
                                                 ".//b/@x | @x",
                                                 ".//@*",
                                                 "p:a/@p:y",
                                                 "a/b/@x | .//b/@x",
                                                 "nothing",
                                                 ".//nothing" };

  private static CompiledXPath _compile (final String sExpr,
                                         final IXdmNode aHost,
                                         final String sDefaultNS) throws XPathException
  {
    final StaticContext aSC = new StaticContext ();
    for (final var aEntry : XsdNodes.inScopeNamespaces (aHost).entrySet ())
      if (!aEntry.getKey ().isEmpty ())
        aSC.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    aSC.setDefaultElementNamespace (sDefaultNS);
    return XPathCompiler.compile (sExpr, aSC);
  }

  private static ICommonsList <IXdmNode> _evalXPath (final CompiledXPath aXPath,
                                                     final IXdmNode aContext) throws XPathException
  {
    final XdmSequence aSeq = aXPath.evaluate (aXPath.createDynamicContext ().withFocus (aContext, 1, 1));
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (final IXdmItem aItem : aSeq)
      ret.add ((IXdmNode) aItem);
    return ret;
  }

  private static void _assertSame (final String sExpr,
                                   final IXdmNode aHost,
                                   final String sDefaultNS,
                                   final IXdmNode aContext) throws XPathException
  {
    final XsdIdentityPath aPath = XsdIdentityPath.parse (sExpr, aHost, sDefaultNS);
    assertNotNull (sExpr, aPath);
    final ICommonsList <IXdmNode> aExpected = _evalXPath (_compile (sExpr, aHost, sDefaultNS), aContext);
    final ICommonsList <IXdmNode> aActual = aPath.evaluate (aContext);
    assertEquals (sExpr + " from " + aContext, aExpected.size (), aActual.size ());
    for (int i = 0; i < aExpected.size (); i++)
      assertSame (sExpr + " #" + i, aExpected.get (i), aActual.get (i));
  }

  @Test
  public void testSameAsXPath () throws XdmParseException, XPathException
  {
    final IXdmNode aHost = XsdNodes.rootElement (new XdmParser ().parse ("<x xmlns:p=\"urn:p\"/>"));
    final IXdmNode aRoot = XsdNodes.rootElement (new XdmParser ().parse (DOC));
    final IXdmNode aInner = aRoot.getChildAtIndex (0);
    int nNonEmpty = 0;
    for (final String sExpr : EXPRESSIONS)
    {
      for (final String sDefaultNS : new String [] { "", "urn:p", "urn:d" })
      {
        _assertSame (sExpr, aHost, sDefaultNS, aRoot);
        _assertSame (sExpr, aHost, sDefaultNS, aInner);
      }
      if (XsdIdentityPath.parse (sExpr, aHost, "").evaluate (aRoot).isNotEmpty ())
        nNonEmpty++;
    }
    // The cases select something, not only empty sequences
    assertTrue (nNonEmpty >= 30);
  }

  @Test
  public void testFromNonElementContext () throws XdmParseException, XPathException
  {
    // A selector like .//. also selects text, comment and processing
    // instruction nodes, which then are the context of the fields
    final IXdmNode aHost = XsdNodes.rootElement (new XdmParser ().parse ("<x/>"));
    final IXdmNode aRoot = XsdNodes.rootElement (new XdmParser ().parse (DOC));
    for (final IXdmNode aNode : XsdIdentityPath.parse (".//.", aHost, "").evaluate (aRoot))
      for (final String sExpr : new String [] { ".", "a", "@x", ".//b", ".//." })
        _assertSame (sExpr, aHost, "", aNode);
  }

  @Test
  public void testUnhandledForms () throws XdmParseException
  {
    final IXdmNode aHost = XsdNodes.rootElement (new XdmParser ().parse ("<x xmlns:p=\"urn:p\"/>"));
    // Left to the compiled XPath: forms the subset check rejects anyway
    assertNull (XsdIdentityPath.parse ("@x/a", aHost, ""));
    assertNull (XsdIdentityPath.parse ("a//b", aHost, ""));
    assertNull (XsdIdentityPath.parse ("q:a", aHost, ""));
    assertNull (XsdIdentityPath.parse ("descendant::a", aHost, ""));
    assertNull (XsdIdentityPath.parse ("a|", aHost, ""));
    assertNull (XsdIdentityPath.parse ("..", aHost, ""));
  }
}
