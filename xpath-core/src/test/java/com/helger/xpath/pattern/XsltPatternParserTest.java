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
package com.helger.xpath.pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for {@link XsltPatternParser} and {@link CompiledXsltPattern}.
 *
 * @author Philip Helger
 */
public final class XsltPatternParserTest
{
  private static final String NS_P = "urn:p";
  private static final XdmQName VAR_V = new XdmQName ("v");

  private static final String DOC = "<doc xmlns:p='urn:p'>" +
                                    "<olist><entry n='1'/><entry n='2'/></olist>" +
                                    "<appendix><sect><para id='a1'>x</para></sect></appendix>" +
                                    "<para class='c' n='1'>e1</para>" +
                                    "<para n='2'>x2</para>" +
                                    "<bullet n='1'/><bullet n='2'/><bullet n='3'/><bullet n='4'/>" +
                                    "<a n='o'><a n='i1'/><b><a n='i2'/></b></a>" +
                                    "<p:x n='px'/>" +
                                    "<item xml:id='W33' k='kv'/>" +
                                    "<!--c--><?pi data?>" +
                                    "</doc>";

  @NonNull
  private static XdmNode _parse (@NonNull final String sXml)
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
  private static StaticContext _createStaticContext ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    // Stand-ins for the XSLT functions the engine registers
    aLib.register (XdmConstants.NS_FN,
                   "current",
                   "() as item()",
                   (aCtx, aArgs) -> XdmSequence.of (aCtx.getCurrentItem ()));
    aLib.register (XdmConstants.NS_FN, "key", "(xs:string, xs:anyAtomicType*) as node()*", (aCtx, aArgs) -> {
      final XdmNode aRoot = ((XdmNode) aCtx.getContextItem ()).getRoot ();
      final String sValue = String.valueOf (((XdmAtomic) aArgs.get (1).get (0)).getValue ());
      return XPathCompiler.compile ("//*[@k = '" + sValue + "']", new StaticContext ())
                          .evaluate (new DynamicContext (new StaticContext ()).withFocus (aRoot, 1, 1));
    }, true);
    final StaticContext ret = new StaticContext ();
    ret.setFunctionLibrary (aLib);
    ret.declareNamespace ("p", NS_P);
    ret.declareVariable (VAR_V);
    return ret;
  }

  @NonNull
  private static XsltPattern _pattern (@NonNull final String sPattern)
  {
    return XsltPatternParser.parse (sPattern, _createStaticContext ());
  }

  @NonNull
  private static ICommonsList <XdmNode> _select (@NonNull final XdmNode aDoc, @NonNull final String sExpr)
  {
    final StaticContext aSC = _createStaticContext ();
    final XdmSequence aSeq = XPathCompiler.compile (sExpr, aSC)
                                          .evaluate (new DynamicContext (aSC).withFocus (aDoc, 1, 1)
                                                                             .withVariable (VAR_V, XdmSequence.EMPTY));
    final ICommonsList <XdmNode> ret = new CommonsArrayList <> ();
    for (final IXdmItem aItem : aSeq)
      ret.add ((XdmNode) aItem);
    return ret;
  }

  /** All nodes of the tree, including attributes and namespace nodes. */
  @NonNull
  private static ICommonsList <XdmNode> _allNodes (@NonNull final XdmNode aDoc)
  {
    final ICommonsList <XdmNode> ret = new CommonsArrayList <> ();
    ret.add (aDoc);
    ret.addAll (_select (aDoc, "//node() | //@* | //namespace-node()"));
    return ret;
  }

  @NonNull
  private static DynamicContext _ctx (@NonNull final XdmSequence aVarValue)
  {
    return new DynamicContext (_createStaticContext ()).withVariable (VAR_V, aVarValue);
  }

  /**
   * Check that exactly the nodes of the selection expression match the
   * pattern, among all nodes of the document.
   */
  private static void _check (@NonNull final String sPattern, @NonNull final String sExpected)
  {
    _check (DOC, sPattern, sExpected, XdmSequence.EMPTY);
  }

  private static void _check (@NonNull final String sXml,
                              @NonNull final String sPattern,
                              @NonNull final String sExpected,
                              @NonNull final XdmSequence aVar)
  {
    _check (_parse (sXml), sPattern, sExpected, aVar);
  }

  private static void _check (@NonNull final XdmNode aDoc,
                              @NonNull final String sPattern,
                              @NonNull final String sExpected,
                              @NonNull final XdmSequence aVar)
  {
    final XsltPattern aPattern = _pattern (sPattern);
    final List <XdmNode> aExpected = _select (aDoc, sExpected);
    final DynamicContext aCtx = _ctx (aVar);
    for (final XdmNode aNode : _allNodes (aDoc))
    {
      final boolean bExpected = aExpected.contains (aNode);
      assertEquals ("Pattern '" + sPattern + "' on " + aNode, Boolean.valueOf (bExpected),
                    Boolean.valueOf (aPattern.matches (aNode, aCtx)));
    }
  }

  private static void _checkError (@NonNull final String sPattern, @NonNull final String sCode)
  {
    try
    {
      _pattern (sPattern);
      fail ("Pattern '" + sPattern + "' should fail with " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals ("Pattern '" + sPattern + "': " + ex.getMessage (), sCode, ex.getCode ());
    }
  }

  private static void _checkPriority (@NonNull final String sPattern, final double dExpected)
  {
    assertEquals (sPattern, dExpected, _pattern (sPattern).getDefaultPriority (), 0);
  }

  @Test
  public void testBasicPaths ()
  {
    _check ("para", "//para");
    _check ("child::para", "//para");
    _check ("*", "//*");
    _check ("olist/entry", "//olist/entry");
    _check ("appendix//para", "//appendix//para");
    _check ("doc/para", "/doc/para");
    _check ("/", "/");
    _check ("/doc", "/doc");
    _check ("//para", "//para");
    _check ("/doc//a", "//a");
    _check ("/doc/a/b/a", "//b/a");
    _check ("doc//sect/para", "//sect/para");
    _check ("p:x", "//p:x");
    _check ("p:*", "//p:*");
    _check ("*:x", "//p:x");
    _check ("Q{urn:p}*", "//p:x");
    _check ("text()", "//text()");
    _check ("comment()", "//comment()");
    _check ("processing-instruction()", "//processing-instruction()");
    _check ("processing-instruction('pi')", "//processing-instruction()");
    _check ("processing-instruction(other)", "()");
    _check ("node()", "//node()");
    _check ("element()", "//*");
    _check ("element(para)", "//para");
    _check ("descendant::a", "//a");
    _check ("a/descendant::a", "//a//a");
    _check ("self::a", "//a");
    _check ("descendant-or-self::a", "//a");
    _check ("a//a", "//a//a");
    _check ("doc/descendant-or-self::node()/a", "//a");
  }

  @Test
  public void testAttributesAndNamespaces ()
  {
    _check ("@n", "//@n");
    _check ("@*", "//@*");
    _check ("attribute::n", "//@n");
    _check ("attribute(n)", "//@n");
    _check ("attribute()", "//@*");
    _check ("entry/@n", "//entry/@n");
    _check ("olist//@n", "//olist//@n");
    _check ("para[@class]", "//para[@class]");
    _check ("namespace-node()", "//namespace-node()");
    _check ("namespace::p", "//namespace::p");
    // Kind tests on axes that cannot select such nodes
    _check ("attribute::element()", "()");
    _check ("attribute::text()", "()");
    _check ("child::attribute()", "()");
  }

  @Test
  public void testDocumentNode ()
  {
    _check ("document-node()", "/");
    _check ("document-node(element(doc))", "/");
    _check ("document-node(element(other))", "()");
    _check ("child::document-node()", "()");
    _check ("self::node()", "/ | //node()");
    _check ("node()", "//node()");
  }

  @Test
  public void testPositional ()
  {
    _check ("para[1]", "//para[1]");
    _check ("bullet[position() mod 2 = 0]", "//bullet[@n = (2, 4)]");
    _check ("bullet[last()]", "//bullet[@n = 4]");
    _check ("bullet[2]", "//bullet[@n = 2]");
    _check ("bullet[@n > 1][2]", "//bullet[@n = 3]");
    _check ("bullet[(position() mod 2) = 1][position() > 1]", "//bullet[@n = 3]");
    _check ("*[3]", "//*[3]");
    _check ("@*[1]", "//@*[1]");
    _check ("a[1]", "//a[1]");
    _check ("descendant::a[2]", "//a[@n = ('i1', 'i2')]");
    _check ("doc/descendant::a[1]", "//a[@n = 'o']");
    _check ("a[if (@n = 'o') then 1 else true()]", "//a");
  }

  @Test
  public void testParentlessTrees ()
  {
    final DynamicContext aCtx = _ctx (XdmSequence.EMPTY);
    final XdmNode aElement = XdmNode.createElement (new XdmQName ("para"));
    assertTrue (_pattern ("para").matches (aElement, aCtx));
    assertTrue (_pattern ("para[1]").matches (aElement, aCtx));
    assertTrue (_pattern ("node()").matches (aElement, aCtx));
    assertTrue (_pattern ("*").matches (aElement, aCtx));
    assertFalse (_pattern ("//para").matches (aElement, aCtx));
    assertFalse (_pattern ("/para").matches (aElement, aCtx));
    assertFalse (_pattern ("x/para").matches (aElement, aCtx));
    assertTrue (_pattern ("root()/self::para").matches (aElement, aCtx));
    assertTrue (_pattern ("root(.)/self::para").matches (aElement, aCtx));

    final XdmNode aChild = XdmNode.createElement (new XdmQName ("child"));
    aElement.appendChild (aChild);
    assertTrue (_pattern ("child").matches (aChild, aCtx));
    assertTrue (_pattern ("para/child").matches (aChild, aCtx));
    assertFalse (_pattern ("root()/self::child").matches (aChild, aCtx));

    final XdmNode aAttr = XdmNode.createAttribute (new XdmQName ("width"), "1");
    assertTrue (_pattern ("@width").matches (aAttr, aCtx));
    assertTrue (_pattern ("@*").matches (aAttr, aCtx));
    assertFalse (_pattern ("node()").matches (aAttr, aCtx));
    assertFalse (_pattern ("x/@width").matches (aAttr, aCtx));

    // In a parentless tree only an a two levels below another node matches
    final XdmNode aA1 = XdmNode.createElement (new XdmQName ("a"));
    final XdmNode aA2 = XdmNode.createElement (new XdmQName ("a"));
    final XdmNode aB = XdmNode.createElement (new XdmQName ("b"));
    final XdmNode aA3 = XdmNode.createElement (new XdmQName ("a"));
    aA1.appendChild (aA2);
    aA1.appendChild (aB);
    aB.appendChild (aA3);
    final XsltPattern aExcept = _pattern ("descendant::a except child::a");
    assertFalse (aExcept.matches (aA1, aCtx));
    assertFalse (aExcept.matches (aA2, aCtx));
    assertTrue (aExcept.matches (aA3, aCtx));

    final XdmNode aText = XdmNode.createText ("t");
    assertTrue (_pattern ("text()").matches (aText, aCtx));
  }

  @Test
  public void testPredicatePatterns ()
  {
    final DynamicContext aCtx = _ctx (XdmSequence.EMPTY);
    final XdmAtomic aInt = XdmAtomic.integer (BigInteger.valueOf (5));
    assertTrue (_pattern (".").matches (aInt, aCtx));
    assertTrue (_pattern (".[. instance of xs:integer]").matches (aInt, aCtx));
    assertFalse (_pattern (".[. instance of xs:string]").matches (aInt, aCtx));
    assertTrue (_pattern (".[. gt 3][. lt 6]").matches (aInt, aCtx));
    assertTrue (_pattern (".[1]").matches (aInt, aCtx));
    assertFalse (_pattern (".[2]").matches (aInt, aCtx));
    assertTrue (_pattern (".[starts-with(., 'e')]").matches (XdmAtomic.string ("eins"), aCtx));
    // Selection patterns never match atomic values
    assertFalse (_pattern ("node()").matches (aInt, aCtx));
    // Predicate patterns also match nodes, including attributes and documents
    _check (".[. instance of node()]", "/ | //node() | //@* | //namespace-node()");
    _check (".[self::para]", "//para");
    _check (".[starts-with(., 'e')]", "//para[@class] | //para[@class]/text()");
    assertEquals (1, _pattern (".[a]").getAlternatives ().size ());
  }

  @Test
  public void testRooted ()
  {
    final XdmNode aDoc = _parse (DOC);
    final XdmSequence aParas = XdmSequence.of (new CommonsArrayList <IXdmItem> (_select (aDoc, "/doc/para")));
    _check (aDoc, "$v", "/doc/para", aParas);
    _check (aDoc, "$v[1]", "/doc/para[1]", aParas);
    _check (aDoc, "$v/@n", "/doc/para/@n", aParas);
    _check (aDoc, "$v//node()", "/doc/para/text()", aParas);
    _check (aDoc, "$v | entry", "/doc/para | //entry", aParas);
    _check (aDoc, "$v except $v[@class]", "/doc/para[2]", aParas);
    _check (aDoc, "doc/($v)", "/doc/para", aParas);
    _check (aDoc, "($v)/text()", "/doc/para/text()", aParas);
    _check (aDoc, "$v/self::*", "()", XdmSequence.of (XdmAtomic.string ("x")));
    _check ("id('W33')", "//item");
    _check ("id('W33')/@k", "//item/@k");
    _check ("element-with-id('W33')", "//item");
    _check ("fn:id('W33')", "//item");
    _check ("Q{http://www.w3.org/2005/xpath-functions}id('W33')", "//item");
    _check ("key('k', 'kv')", "//item");
    _check ("key('k', 'kv')/@n", "()");
    _check ("key('k', 'kv')//node()", "()");
    _check ("root()", "/");
    _check ("root()/doc", "/doc");
    _check ("root()//para", "//para");
  }

  @Test
  public void testUnionIntersectExcept ()
  {
    _check ("entry | bullet", "//entry | //bullet");
    _check ("entry union bullet", "//entry | //bullet");
    _check ("(entry | bullet)", "//entry | //bullet");
    _check ("(entry | bullet)/@n", "//entry/@n | //bullet/@n");
    _check ("doc/(olist | appendix)", "/doc/olist | /doc/appendix");
    _check ("doc/(olist | appendix)/*", "/doc/olist/* | /doc/appendix/*");
    _check ("(entry | bullet)[1]", "//entry[1] | //bullet[1]");
    _check ("(bullet | entry)[last()]", "//entry[2] | //bullet[4]");
    _check ("a except a[@n = 'o']", "//a[@n != 'o']");
    _check ("* intersect a", "//a");
    _check ("bullet except bullet[1]", "//bullet[position() > 1]");
    // The formal semantics with anchors: from the document node every a is a
    // descendant and none a child
    _check ("descendant::a except child::a", "//a");
    _check ("a/descendant::a except a/child::a", "//b/a");
    _check ("(a/descendant::a except a/child::a) | entry", "//b/a | //entry");
    _check ("para except //sect/para", "/doc/para");
    _check ("(/) except /doc", "/");
    _check ("(/doc | /doc/para) except /doc", "/doc/para");
    _check ("$v | entry", "//entry");
  }

  @Test
  public void testCurrent ()
  {
    _check ("a[current() is .]", "//a");
    _check ("a[*[current()/@n = 'o']]", "//a[@n = 'o']");
    _check ("a[b/a[current()/@n = 'o']]", "//a[@n = 'o']");
    _check ("entry[current()/@n = 2]", "//entry[2]");
  }

  @Test
  public void testDynamicErrorsDoNotMatch ()
  {
    _check ("para[error()]", "()");
    _check ("para[1 idiv 0]", "()");
    _check ("para | bullet[error()]", "//para");
    _check (".[xs:integer(.) = 1]", "//entry[1]/@n | /doc/para[1]/@n | //bullet[1]/@n");
  }

  @Test
  public void testAlternatives ()
  {
    XsltPattern aPattern = _pattern ("a | @b | (c | d[1]) | /e");
    ICommonsList <XsltPattern> aAlts = aPattern.getAlternatives ();
    assertEquals (5, aAlts.size ());
    assertEquals ("a", aAlts.get (0).getSource ());
    assertEquals ("@b", aAlts.get (1).getSource ());
    assertEquals ("c", aAlts.get (2).getSource ());
    assertEquals ("d[1]", aAlts.get (3).getSource ());
    assertEquals ("/e", aAlts.get (4).getSource ());
    assertEquals (0, aAlts.get (0).getDefaultPriority (), 0);
    assertEquals (0, aAlts.get (1).getDefaultPriority (), 0);
    assertEquals (0, aAlts.get (2).getDefaultPriority (), 0);
    assertEquals (0.5, aAlts.get (3).getDefaultPriority (), 0);
    assertEquals (0.5, aAlts.get (4).getDefaultPriority (), 0);
    assertEquals (0.5, aPattern.getDefaultPriority (), 0);

    aPattern = _pattern ("((a | b))");
    aAlts = aPattern.getAlternatives ();
    assertEquals (2, aAlts.size ());
    assertEquals ("a", aAlts.get (0).getSource ());
    assertEquals ("b", aAlts.get (1).getSource ());
    assertEquals (0, aPattern.getDefaultPriority (), 0);

    aPattern = _pattern ("a except b");
    assertEquals (1, aPattern.getAlternatives ().size ());
    assertTrue (aPattern.getAlternatives ().get (0) == aPattern);

    // "union" after a lone "/" is an element name
    aPattern = _pattern ("/ union /*");
    assertEquals (1, aPattern.getAlternatives ().size ());
    _check ("<union><x/></union>", "/ union /*", "/union/*", XdmSequence.EMPTY);
    assertEquals (2, _pattern ("(/) union (/*)").getAlternatives ().size ());
    assertEquals (2, _pattern ("(: c :) a | (: d :) b").getAlternatives ().size ());
  }

  @Test
  public void testDefaultPriorities ()
  {
    _checkPriority ("a", 0);
    _checkPriority ("child::a", 0);
    _checkPriority ("descendant::a", 0);
    _checkPriority ("@a", 0);
    _checkPriority ("attribute::a", 0);
    _checkPriority ("p:a", 0);
    _checkPriority ("Q{urn:p}a", 0);
    _checkPriority ("*", -0.5);
    _checkPriority ("@*", -0.5);
    _checkPriority ("p:*", -0.25);
    _checkPriority ("*:a", -0.25);
    _checkPriority ("@p:*", -0.25);
    _checkPriority ("Q{urn:p}*", -0.25);
    _checkPriority ("node()", -0.5);
    _checkPriority ("text()", -0.5);
    _checkPriority ("comment()", -0.5);
    _checkPriority ("namespace-node()", -0.5);
    _checkPriority ("processing-instruction()", -0.5);
    _checkPriority ("processing-instruction('x')", 0);
    _checkPriority ("processing-instruction(x)", 0);
    _checkPriority ("element()", -0.5);
    _checkPriority ("element(*)", -0.5);
    _checkPriority ("element(a)", 0);
    _checkPriority ("element(*, xs:untyped)", 0);
    _checkPriority ("element(a, xs:untyped)", 0.25);
    _checkPriority ("element(a, xs:untyped?)", 0.25);
    _checkPriority ("attribute()", -0.5);
    _checkPriority ("attribute(*)", -0.5);
    _checkPriority ("attribute(a)", 0);
    _checkPriority ("attribute(*, xs:date)", 0);
    _checkPriority ("attribute(a, xs:date)", 0.25);
    _checkPriority ("document-node()", -0.5);
    _checkPriority ("document-node(element(a))", 0);
    _checkPriority ("document-node(element(a, xs:untyped))", 0.25);
    _checkPriority ("document-node(element())", -0.5);
    _checkPriority ("/", -0.5);
    _checkPriority ("//a", 0.5);
    _checkPriority ("/a", 0.5);
    _checkPriority ("a/b", 0.5);
    _checkPriority ("a//b", 0.5);
    _checkPriority ("a[1]", 0.5);
    _checkPriority ("*[@x]", 0.5);
    _checkPriority ("node()[self::*]", 0.5);
    _checkPriority (".", -1);
    _checkPriority (".[1]", 1);
    _checkPriority (".[a][b]", 1);
    _checkPriority ("$v", 0.5);
    _checkPriority ("id('x')", 0.5);
    _checkPriority ("key('k', 'v')/a", 0.5);
    _checkPriority ("(a)", 0);
    _checkPriority ("(a)[1]", 0.5);
    _checkPriority ("(a|b)[1]", 0.5);
    _checkPriority ("a except b", 0);
    _checkPriority ("* except a", -0.5);
    _checkPriority ("(/) intersect a", 0.5);
    _checkPriority ("(a|b) except c", 0.5);
    _checkPriority ("a | *", 0);
  }

  @Test
  public void testSyntaxErrors ()
  {
    for (final String s : new String [] { "",
                                          "..",
                                          "a/..",
                                          "parent::a",
                                          "ancestor::a",
                                          "following-sibling::a",
                                          "a/preceding::b",
                                          "name/1223",
                                          "2+2",
                                          "'a'",
                                          "1",
                                          "/[doc]",
                                          "./a",
                                          ". | a",
                                          "a | .[x]",
                                          "(.[x])",
                                          "(.)",
                                          "a/.",
                                          "a/$v",
                                          "/$v",
                                          "a/id('x')",
                                          "/key('k', 42)//a",
                                          "key('k', 40+2)//a",
                                          "id(@x)",
                                          "concat('a', 'b')",
                                          "string(.)",
                                          "root($v)",
                                          "root('x')",
                                          "$v => count()",
                                          "a, b",
                                          "a[",
                                          "a]",
                                          "for $x in a return $x",
                                          "a | ",
                                          "@",
                                          "a[1] b",
                                          "()",
                                          "(a",
                                          "a!b",
                                          "a/(1)",
                                          "a/[1, 2]",
                                          "map{}",
                                          "child::",
                                          "a[1 +]",
                                          "/ intersect a" })
      _checkError (s, "XTSE0340");
  }

  @Test
  public void testOtherStaticErrors ()
  {
    _checkError ("$undeclared", "XPST0008");
    _checkError ("a[$undeclared]", "XPST0008");
    _checkError ("q:a", "XPST0081");
    _checkError ("a[unknown-function()]", "XPST0017");
    _checkError ("id()", "XPST0017");
  }

  @Test
  public void testSources ()
  {
    final XsltPattern aPattern = _pattern ("  a | b  ");
    assertEquals ("  a | b  ", aPattern.getSource ());
    assertTrue (aPattern instanceof CompiledXsltPattern);
    assertFalse (((CompiledXsltPattern) aPattern).isPredicatePattern ());
    assertTrue (((CompiledXsltPattern) _pattern (".[1]")).isPredicatePattern ());
    final CompiledXsltPattern aCompiled = (CompiledXsltPattern) _pattern ("a[current()/b]");
    assertTrue (aCompiled.containsFunction (x -> x.localName ().equals ("current")));
    assertFalse (aCompiled.containsFunction (x -> x.localName ().equals ("current-group")));
  }
}
