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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.xdm.XdmQName;

/**
 * Specification tests of the narrow static-emptiness axis analysis of the
 * optional XPath 3.1 Static Typing Feature, ported from go-xslt's
 * <code>static_axes_test.go</code> (see also <code>XPathStaticAxesTest</code>).
 * With static typing on, "it is a static error if the static type assigned to
 * an expression other than () or data(()) is empty-sequence()" (XPath 3.1
 * §2.4.1, XPST0005).
 *
 * @author Philip Helger
 */
public final class StaticAxesSpecTest
{
  /**
   * @return <code>true</code> if the expression is rejected with XPST0005 when
   *         static typing is on.
   */
  private static boolean _isStaticallyEmpty (@NonNull final String sExpr)
  {
    try
    {
      // go-xslt resolves no names at parse time; p, $x and $f are declared here
      final StaticContext aSC = new StaticContext ().declareNamespace ("p", "urn:p").setStaticTyping (true);
      aSC.declareVariable (new XdmQName ("x"));
      aSC.declareVariable (new XdmQName ("f"));
      XPathCompiler.compile (sExpr, aSC);
      return false;
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr + ": " + ex.getMessage (), "XPST0005", ex.getCode ());
      return true;
    }
  }

  private static void _check (@NonNull final String [] aExprs, final boolean bWantEmpty)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sExpr : aExprs)
      if (_isStaticallyEmpty (sExpr) != bWantEmpty)
        aErrors.add (sExpr + (bWantEmpty ? ": want XPST0005" : ": unexpected XPST0005"));
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * Axis steps whose static type is empty-sequence() by the XDM's structural
   * rules alone (XDM 3.1 §6: document nodes have no parent and no attributes;
   * attribute, text, comment, PI and namespace nodes have no children; a node
   * has exactly one kind and name).
   */
  @Test
  public void spStaticAxesEmpty ()
  {
    _check (new String [] { "/attribute::a",
                            "/..",
                            "/self::a",
                            "self::a/self::b",
                            "@a/child::b",
                            "@a/descendant::node()",
                            "text()/child::node()",
                            "comment()/b",
                            "processing-instruction()/b",
                            "text()/descendant-or-self::b",
                            "@a/descendant-or-self::b",
                            "@a/@b",
                            "text()/@b",
                            "parse-xml-fragment('<a/>')/..",
                            "/a/parse-xml-fragment('<a/>')/..",
                            "b/self::element(b)/self::c",
                            "document-node()/self::text()",
                            "attribute(a)/b" }, true);
  }

  /**
   * Shapes the structural analysis must NOT reject, since each can select
   * something on some document.
   */
  @Test
  public void spStaticAxesNotEmpty ()
  {
    _check (new String [] { "/a",
                            "//a",
                            "a/b/@c",
                            "self::node()/a",
                            "self::a/self::a",
                            "self::a/self::*",
                            "@a/..",
                            "text()/..",
                            "a/ancestor::b",
                            "@a/following-sibling::b",
                            "a/preceding::b",
                            "a/namespace::*",
                            "a/descendant-or-self::node()",
                            "a/descendant-or-self::a",
                            "text()/descendant-or-self::text()",
                            "@a/self::attribute()",
                            "self::a/descendant-or-self::b",
                            "//text()",
                            "/node()",
                            "/descendant-or-self::node()/@a",
                            "p:a/self::b",
                            "*:a/self::b",
                            "a/self::element()",
                            "a/element(x, xs:string)/c",
                            "$x/..",
                            "(a)/..",
                            "a/string()",
                            "a/@b/self::attribute(b)",
                            "a/descendant-or-self::node()/self::a",
                            "()",
                            "1 + 2" }, false);
    // Without static typing the check is never performed
    XPathCompiler.compile ("/..", new StaticContext ());
  }

  /**
   * The check reaches every expression that can contain a path, so a
   * provably-empty step nested anywhere is still found.
   */
  @Test
  public void spStaticAxesNested ()
  {
    // Attributes have no children
    final String sEmpty = "@a/b";
    final String [] aWraps = { "count(%s)",
                               "1 + count(%s)",
                               "-count(%s)",
                               "x | %s",
                               "%s | x",
                               "x[%s]",
                               "x/y[%s]",
                               "(x)[%s]",
                               "(%s)[1]",
                               "(1, %s)",
                               "1 to count(%s)",
                               "count(%s) to 1",
                               "for $v in %s return 1",
                               "for $v in 1 return %s",
                               "let $v := %s return 1",
                               "let $v := 1 return %s",
                               "some $v in %s satisfies true()",
                               "every $v in 1 satisfies %s",
                               "if (%s) then 1 else 2",
                               "if (1) then %s else 2",
                               "if (1) then 2 else %s",
                               "map{(%s): 1}",
                               "map{1: %s}",
                               "[%s]",
                               "array{%s}",
                               "function() {%s}",
                               "(%s)?1",
                               "map{}?(%s)",
                               "$f(%s)",
                               "(%s)(1)",
                               "%s = 1",
                               "1 eq count(%s)",
                               "'a' || %s",
                               "x intersect %s",
                               "%s except x",
                               "%s instance of node()",
                               "%s treat as node()*",
                               "%s cast as xs:string?",
                               "%s => count()",
                               // go-xslt uses f, which it does not resolve at parse time
                               "1 => concat(%s)",
                               "x ! %s",
                               "x/(%s)" };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sWrap : aWraps)
    {
      final String sExpr = sWrap.replace ("%s", sEmpty);
      if (!_isStaticallyEmpty (sExpr))
        aErrors.add (sExpr + ": want XPST0005 from the nested " + sEmpty);
    }
    assertTrue (String.join ("\n", aErrors), aErrors.isEmpty ());
  }
}
