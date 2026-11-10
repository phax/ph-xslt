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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The direct matcher of simple path patterns and the predicates distributed over a parenthesised
 * union change no match result. The reference is the same pattern with an additional, always true
 * predicate <code>[position() ge 1]</code>, which keeps it on the general search.
 *
 * @author Philip Helger
 */
public final class PatternDirectMatchTest
{
  private static final XdmQName VAR_N = new XdmQName ("n");

  private static final String DOC = "<r x='0'>" +
                                    "<a x='1'><b x='2'>t</b><b/><c x='3'><b x='4'/></c></a>" +
                                    "<b x='5'><a/></b>" +
                                    "<d><a x='6'><b/><b x='7'/></a></d>" +
                                    "</r>";

  /** Pattern and its reference on the general search. */
  private static final String [] [] CASES = { { "b[@x]", "b[@x][position() ge 1]" },
                                              { "a/b", "a/b[position() ge 1]" },
                                              { "a/b[@x = '2']", "a/b[@x = '2'][position() ge 1]" },
                                              { "a/b[1]", "a/b[1][position() ge 1]" },
                                              { "a/b[$n]", "a/b[$n][position() ge 1]" },
                                              { "/r/a/b", "/r/a/b[position() ge 1]" },
                                              { "/r/b/a", "/r/b/a[position() ge 1]" },
                                              { "/b", "/b[position() ge 1]" },
                                              { "/r", "/r[position() ge 1]" },
                                              { "r", "r[position() ge 1]" },
                                              { "r/b", "r/b[position() ge 1]" },
                                              { "a/@x", "a/@x[position() ge 1]" },
                                              { "@x[. = '1']", "@x[. = '1'][position() ge 1]" },
                                              { "a/b/text()", "a/b/text()[position() ge 1]" },
                                              { "*/b[@x]", "*/b[@x][position() ge 1]" },
                                              { "(a/b | c/b)[@x]", "(a/b | c/b)[@x][position() ge 1]" },
                                              { "(/r/a/b | /r/d/a/b)[not(@x)]",
                                                "(/r/a/b | /r/d/a/b)[not(@x)][position() ge 1]" },
                                              { "(a | b)[@x][c or b]", "(a | b)[@x][c or b][position() ge 1]" },
                                              { "((a | b)[@x] | d)[*]", "((a | b)[@x] | d)[*][position() ge 1]" },
                                              { "(b)[@x = ('2', '5')]", "(b)[@x = ('2', '5')][position() ge 1]" },
                                              { "(a/b[1] | c/b)[@x]", "(a/b[1] | c/b)[@x][position() ge 1]" },
                                              { "(a//b | @x)[. != '1']", "(a//b | @x)[. != '1'][position() ge 1]" },
                                              { "(a | b)", "(a | b)[position() ge 1]" },
                                              { "(a/b | c/b)[$n]", "(a/b | c/b)[$n][position() ge 1]" } };

  /** All nodes in document order: the node, its attributes, then children. */
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

  /** A document and a parentless element tree <code>&lt;r x='8'>&lt;b x='9'/>&lt;/r></code>. */
  @NonNull
  private static ICommonsList <IXdmNode> _allNodes () throws Exception
  {
    final ICommonsList <IXdmNode> ret = _collectNodes (new XdmParser ().parse (DOC));
    final IXdmNode aR = IXdmNode.createElement (new XdmQName ("r"));
    aR.addAttribute (IXdmNode.createAttribute (new XdmQName ("x"), "8"));
    final IXdmNode aB = IXdmNode.createElement (new XdmQName ("b"));
    aB.addAttribute (IXdmNode.createAttribute (new XdmQName ("x"), "9"));
    aR.appendChild (aB);
    ret.addAll (_collectNodes (aR));
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
  public void testSameAsSearch () throws Exception
  {
    final ICommonsList <IXdmNode> aNodes = _allNodes ();
    final StaticContext aSC = new StaticContext ().declareVariable (VAR_N);
    for (final long nValue : new long [] { 1, 2 })
    {
      final DynamicContext aCtx = new DynamicContext (aSC).withVariable (VAR_N,
                                                                          XdmSequence.of (XdmAtomic.integer (nValue)));
      for (final String [] aCase : CASES)
      {
        final String sExpected = _matches (XsltPatternParser.parse (aCase[1], aSC), aNodes, aCtx);
        assertEquals (aCase[0] + " with $n = " + nValue,
                      sExpected,
                      _matches (XsltPatternParser.parse (aCase[0], aSC), aNodes, aCtx));
      }
    }
    // A boolean variable is decided by the direct matcher
    final DynamicContext aCtx = new DynamicContext (aSC).withVariable (VAR_N, XdmSequence.of (XdmAtomic.TRUE));
    assertEquals (_matches (XsltPatternParser.parse ("a/b", aSC), aNodes, aCtx),
                  _matches (XsltPatternParser.parse ("a/b[$n]", aSC), aNodes, aCtx));
  }

  private static IExpr.@NonNull PathExpr _path (@NonNull final String sPattern)
  {
    final StaticContext aSC = new StaticContext ().declareVariable (VAR_N);
    return (IExpr.PathExpr) ((CompiledXsltPattern) XsltPatternParser.parse (sPattern, aSC)).getPatternExpr ();
  }

  @Test
  public void testDirectPathShapes ()
  {
    for (final String s : new String [] { "a", "a/b[@x]", "/r/a/@x", "@x", "a/b[1]", "a/b[$n]", "a/text()", "child::a/attribute::x" })
      assertNotNull (s, PatternMatcher.getDirectPath (_path (s)));
    for (final String s : new String [] { "/", "a//b", "//a", "descendant::a", "self::a", "a/b[position() = 1]", "a[last()]", "a[function-lookup(xs:QName('fn:true'), 0)()]", "a/(b)", "(a)", "$n/a", "document-node()", "document-node()/a" })
      assertNull (s, PatternMatcher.getDirectPath (_path (s)));
  }
}
