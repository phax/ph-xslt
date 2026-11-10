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
package com.helger.phoxtrot.xpath.pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;

/**
 * The quick rejection of {@link XsltPattern#canMatch(com.helger.phoxtrot.xdm.IXdmItem)} and of the
 * path search changes no match result, and {@link XsltPattern#getFinalNames()} names every node a
 * pattern can match. The expected match lists were taken from the implementation before the
 * quick rejection.
 *
 * @author Philip Helger
 */
public final class PatternQuickRejectTest
{
  private static final String DOC = "<r xmlns:p='urn:p' x='0'>" +
                                    "<a x='1'><b x='2'>t1</b><b><c/></b></a>" +
                                    "<b y='3'><c x='4'/><p:c/></b>" +
                                    "<a><a><c/></a></a>" +
                                    "<!--k--><?pi d?>t2" +
                                    "</r>";

  /** Pattern and the indexes of the matching nodes in {@link #_collectNodes(IXdmNode)}. */
  private static final String [] [] CASES = { { "a", "3 15 16" },
                                              { "b", "5 8 10" },
                                              { "a/b", "5 8" },
                                              { "//b", "5 8 10" },
                                              { "/", "0" },
                                              { "/r", "1" },
                                              { "/r/b", "10" },
                                              { "@x", "2 4 6 13" },
                                              { "a/@x", "4" },
                                              { "@*", "2 4 6 11 13" },
                                              { "b | c", "5 8 9 10 12 17" },
                                              { "a | @y", "3 11 15 16" },
                                              { "b[1]", "5 10" },
                                              { "b[last()]", "8 10" },
                                              { "b[@x = '2']", "5" },
                                              { "b[c][2]", "" },
                                              { "*", "1 3 5 8 9 10 12 14 15 16 17" },
                                              { "node()", "1 3 5 7 8 9 10 12 14 15 16 17 18 19 20" },
                                              { "text()", "7 20" },
                                              { "comment()", "18" },
                                              { "processing-instruction()", "19" },
                                              { "document-node()", "0" },
                                              { "document-node(element(r))", "0" },
                                              { "element(b)", "5 8 10" },
                                              { "attribute(x)", "2 4 6 13" },
                                              { "p:*", "14" },
                                              { "*:c", "9 12 14 17" },
                                              { "a//c", "9 17" },
                                              { "(a | b)/c", "9 12 17" },
                                              { "b except b[1]", "8" },
                                              { "b intersect a/b", "" },
                                              { "descendant::c", "9 12 17" },
                                              { "r//a/a", "16" },
                                              { "self::b", "5 8 10" } };

  /** All nodes in document order: the node, its namespace nodes, attributes, then children. */
  @NonNull
  private static ICommonsList <IXdmNode> _collectNodes (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
      ret.add (aNode);
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        ret.add (aNode.getAttributeAtIndex (i));
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  @NonNull
  private static String _matches (@NonNull final XsltPattern aPattern,
                                  @NonNull final List <IXdmNode> aNodes,
                                  @NonNull final DynamicContext aCtx)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aNodes.size (); ++i)
      if (aPattern.matches (aNodes.get (i), aCtx))
      {
        if (aSB.length () > 0)
          aSB.append (' ');
        aSB.append (i);
      }
    return aSB.toString ();
  }

  @Test
  public void testSameMatches () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse (DOC);
    final ICommonsList <IXdmNode> aNodes = _collectNodes (aDoc);
    final StaticContext aSC = new StaticContext ().declareNamespace ("p", "urn:p");
    final DynamicContext aCtx = new DynamicContext (aSC);
    for (final String [] aCase : CASES)
    {
      final XsltPattern aPattern = XsltPatternParser.parse (aCase[0], aSC);
      assertEquals (aCase[0], aCase[1], _matches (aPattern, aNodes, aCtx));
    }
  }

  /** A node that canMatch rejects never matches. */
  @Test
  public void testRejectedNeverMatches () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse (DOC);
    final ICommonsList <IXdmNode> aNodes = _collectNodes (aDoc);
    final StaticContext aSC = new StaticContext ().declareNamespace ("p", "urn:p");
    final DynamicContext aCtx = new DynamicContext (aSC);
    for (final String [] aCase : CASES)
    {
      final XsltPattern aPattern = XsltPatternParser.parse (aCase[0], aSC);
      for (final IXdmNode aNode : aNodes)
        if (!aPattern.canMatch (aNode))
          assertFalse (aCase[0], aPattern.matches (aNode, aCtx));
    }
  }

  /**
   * The final names are given only where every alternative ends in an exact element or attribute
   * name, and every matching node has one of them.
   */
  @Test
  public void testFinalNames () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse (DOC);
    final ICommonsList <IXdmNode> aNodes = _collectNodes (aDoc);
    final StaticContext aSC = new StaticContext ().declareNamespace ("p", "urn:p");
    final DynamicContext aCtx = new DynamicContext (aSC);
    final XdmQName aA = new XdmQName ("a");
    final XdmQName aB = new XdmQName ("b");
    final XdmQName aY = new XdmQName ("y");
    final PatternFinalNames aNames = XsltPatternParser.parse ("a | b[1] | //b | x/@y | p:c", aSC).getFinalNames ();
    assertNotNull (aNames);
    assertEquals (List.of (aA, aB, new XdmQName ("urn:p", "c")), aNames.elementNames ());
    assertEquals (List.of (aY), aNames.attributeNames ());
    for (final String s : new String [] { "*", "p:*", "*:c", "node()", "text()", "element(b)", "attribute(x)", "/", "document-node()", "b except b[1]", "b intersect a/b", "(a | b)/c[1]/(d)", "(a | b)[1]", "(a | b)[position() = 1]", "(a | /)[@x]", "((a)/(b) | c)[@x]", "@*", "namespace::x" })
      assertNull (s, XsltPatternParser.parse (s, aSC).getFinalNames ());
    // Parenthesised unions with boolean predicates count as their paths
    final PatternFinalNames aParenNames = XsltPatternParser.parse ("(a | (b[1] | x/@y)[@z = '1'])[c]", aSC)
                                                           .getFinalNames ();
    assertNotNull (aParenNames);
    assertEquals (List.of (aA, aB), aParenNames.elementNames ());
    assertEquals (List.of (aY), aParenNames.attributeNames ());
    assertEquals (List.of (aA, aB), XsltPatternParser.parse ("(a | b)", aSC).getFinalNames ().elementNames ());
    for (final String [] aCase : CASES)
    {
      final XsltPattern aPattern = XsltPatternParser.parse (aCase[0], aSC);
      final PatternFinalNames aFinal = aPattern.getFinalNames ();
      if (aFinal != null)
        for (final IXdmNode aNode : aNodes)
          if (aPattern.matches (aNode, aCtx))
            assertEquals (aCase[0],
                          Boolean.TRUE,
                          Boolean.valueOf (aNode.isElement () ? aFinal.elementNames ().contains (aNode.getName ())
                                                              : aFinal.attributeNames ().contains (aNode.getName ())));
    }
  }

  /** The union paths of a pattern from left to right, or an empty list for any other pattern. */
  @NonNull
  private static ICommonsList <IExpr.PathExpr> _getUnionPaths (@NonNull final IExpr aPattern)
  {
    final ICommonsList <IExpr.PathExpr> ret = new CommonsArrayList <> ();
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aPattern);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      if (aCur instanceof final IExpr.SetExpr aSet && aSet.op () == IExpr.SetExpr.ESetOp.UNION)
      {
        aStack.push (aSet.right ());
        aStack.push (aSet.left ());
      }
      else
        if (aCur instanceof final IExpr.PathExpr aPath)
          ret.add (aPath);
        else
          return new CommonsArrayList <> ();
    }
    return ret;
  }

  /**
   * The last step computed once per path (canMatch and the union match) gives the same answer as
   * computing it from the path for every node. A pattern that is one parenthesised union is opened
   * into its paths when it is compiled, so its canMatch may reject more than the pre-test of the
   * parenthesised path, never less.
   */
  @Test
  public void testPrecomputedLastStep () throws Exception
  {
    final IXdmNode aDoc = new XdmParser ().parse (DOC);
    final ICommonsList <IXdmNode> aNodes = _collectNodes (aDoc);
    final StaticContext aSC = new StaticContext ().declareNamespace ("p", "urn:p");
    final ICommonsList <String> aPatterns = new CommonsArrayList <> ();
    for (final String [] aCase : CASES)
      aPatterns.add (aCase[0]);
    aPatterns.addAll ("(a)",
                      "a | (b)/c",
                      "child::document-node()",
                      "self::document-node()",
                      "attribute::x | namespace::p",
                      "descendant::node()",
                      "descendant-or-self::*",
                      "/descendant::b",
                      "a/document-node()",
                      "a/b/@x | /r/text()");
    for (final String sPattern : aPatterns)
    {
      final CompiledXsltPattern aPattern = (CompiledXsltPattern) XsltPatternParser.parse (sPattern, aSC);
      final ICommonsList <IExpr.PathExpr> aPaths = _getUnionPaths (aPattern.getPatternExpr ());
      for (final IXdmNode aNode : aNodes)
      {
        boolean bAny = aPaths.isEmpty ();
        for (final IExpr.PathExpr aPath : aPaths)
        {
          final boolean bOld = PatternMatcher.canLastStepSelect (aPath, aNode);
          final PatternMatcher.LastStep aLast = PatternMatcher.getLastStep (aPath);
          assertEquals (sPattern, Boolean.valueOf (bOld), Boolean.valueOf (aLast == null || aLast.canSelect (aNode)));
          bAny |= bOld;
        }
        final boolean bParenthesised = aPaths.size () == 1 &&
                                       aPaths.getFirst ().steps ().size () == 1 &&
                                       aPaths.getFirst ().steps ().getFirst ().postfix () != null;
        if (bParenthesised)
        {
          if (aPattern.canMatch (aNode))
            assertTrue (sPattern, bAny);
        }
        else
          assertEquals (sPattern, Boolean.valueOf (bAny), Boolean.valueOf (aPattern.canMatch (aNode)));
      }
    }
  }
}
