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
package com.helger.phoxtrot.xpath.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.StaticContext;

/**
 * Test class for the static errors raised by {@link XPathParser}.
 *
 * @author Philip Helger
 */
public final class XPathParserErrorTest
{
  private static void _expect (@NonNull final String sCode, @NonNull final String... aExprs)
  {
    for (final String sExpr : aExprs)
    {
      try
      {
        XPathParser.parse (sExpr, ParserTestHelper.createContext ());
        fail ("Expected " + sCode + " for: " + sExpr);
      }
      catch (final XPathException ex)
      {
        assertEquals (sExpr + " -> " + ex.getMessage (), sCode, ex.getCode ());
      }
    }
  }

  @NonNull
  private static XPathException _error (@NonNull final String sExpr)
  {
    try
    {
      XPathParser.parse (sExpr, ParserTestHelper.createContext ());
      fail ("Expected an error for: " + sExpr);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex;
    }
  }

  private static void _expectPosition (@NonNull final String sExpr,
                                       @NonNull final String sCode,
                                       final int nOffset,
                                       final int nLine,
                                       final int nColumn)
  {
    final XPathException ex = _error (sExpr);
    assertEquals (sExpr, sCode, ex.getCode ());
    assertTrue (ex.hasExpressionPosition ());
    assertEquals (sExpr, nOffset, ex.getExpressionOffset ());
    assertEquals (sExpr, nLine, ex.getExpressionLine ());
    assertEquals (sExpr, nColumn, ex.getExpressionColumn ());
  }

  @Test
  public void testErrorPosition ()
  {
    // Parser: unexpected token
    _expectPosition ("1 + )", "XPST0003", 4, 1, 5);
    _expectPosition ("1 +\n  )", "XPST0003", 6, 2, 3);
    _expectPosition ("1 +\r\n\r\n )", "XPST0003", 8, 3, 2);
    _expectPosition ("1 +\r)", "XPST0003", 4, 2, 1);
    // Lexer: unterminated string literal
    final XPathException aLex = _error ("1,\n 'abc");
    assertEquals ("XPST0003", aLex.getCode ());
    assertEquals (2, aLex.getExpressionLine ());
    // Unknown variable (at the name after the '$') and function (at the name)
    _expectPosition ("1 + $unknown", "XPST0008", 5, 1, 6);
    _expectPosition ("count(1),\n  no-such-function(2)", "XPST0017", 12, 2, 3);
  }

  @Test
  public void testNoPositionByDefault ()
  {
    final XPathException ex = new XPathException ("FOAR0001", "Division by zero");
    assertFalse (ex.hasExpressionPosition ());
    assertEquals (-1, ex.getExpressionOffset ());
    assertEquals (-1, ex.getExpressionLine ());
    assertEquals (-1, ex.getExpressionColumn ());
    // The first position set is kept
    ex.setExpressionPosition ("a\nb", 2).setExpressionPosition ("abc", 0);
    assertEquals (2, ex.getExpressionLine ());
    assertEquals (1, ex.getExpressionColumn ());
  }

  @Test
  public void testSyntaxErrors ()
  {
    _expect ("XPST0003",
             "",
             "   ",
             "(: only a comment :)",
             "1 +",
             "(1",
             "1)",
             "[1",
             "count(",
             "count(1,)",
             "a b",
             "//",
             "a/",
             "a//",
             "@",
             "$",
             "1 => 2",
             "*:*",
             "p:b:c",
             "map{p:b}",
             "map{'a' 1}",
             "map{'a': 1,}",
             "[1,]",
             "array{",
             "function($a) $a",
             "function(a) { 1 }",
             "concat#x",
             "-",
             "1 2");
  }

  @Test
  public void testLexicalErrors ()
  {
    _expect ("XPST0003",
             "'abc",
             "\"abc",
             "'it''s",
             "(: unterminated",
             "(: (: nested :)",
             "10div 3",
             "1.2.3",
             "1e",
             "1e+",
             "1ea",
             "3.0a",
             "µ",
             "a b",
             "Q{urn:x",
             "Q{a{b}c",
             "Q{urn:x}",
             "Q{urn:x}1",
             "#",
             "a ; b",
             "1 & 2");
  }

  @Test
  public void testNonAssociativeOperators ()
  {
    _expect ("XPST0003",
             "1 = 2 = 3",
             "1 eq 2 eq 3",
             "1 < 2 > 3",
             "1 = 2 + 3 = 4",
             "1 is 1 is 1",
             "1 to 2 to 3",
             "1 << 2 >> 3");
  }

  @Test
  public void testKeywordStructures ()
  {
    _expect ("XPST0003",
             "for $x in 1",
             "for $x return 1",
             "for $x at $i in 1 return 1",
             "for $x as xs:integer in 1 return 1",
             "let $x = 1 return $x",
             "let $x := 1",
             "some $x in 1",
             "every $x in 1 return 1",
             "if (1) then 2",
             "if (1) 2 else 3",
             "if 1 then 2 else 3",
             "1 instance xs:integer",
             "1 instance of",
             "1 treat xs:integer",
             "1 cast xs:string",
             "1 castable xs:string",
             "1 cast as xs:string cast as xs:integer",
             "'string' cast as xs:string*",
             "'string' cast as xs:string+",
             "'string' castable as xs:string*",
             "('one', 'two') castable as xs:string+",
             "1 instance of xs:integer + 2",
             "3 treat as item(",
             "fn:root(self::node()) treat as document-node()/X",
             "1 instance of empty-sequence()?");
  }

  @Test
  public void testLookupKeys ()
  {
    _expect ("XPST0003", "$m?1.5", "$m?1e2", "$m?p:a", "$m?Q{urn:x}a", "?2.2", "$m?", "$m?-1");
  }

  @Test
  public void testKindTestSyntax ()
  {
    _expect ("XPST0003",
             "processing-instruction(p:x)",
             "processing-instruction(Q{u}x)",
             "document-node(text())",
             "document-node(node())",
             "attribute(a, xs:string?)",
             "element(p:*)",
             "element(a,)",
             "text(1)",
             "node(*)",
             "comment('x')",
             "namespace-node(a)");
    _expect ("XPTY0004", "processing-instruction('1x')", "processing-instruction('a b')");
  }

  @Test
  public void testAxes ()
  {
    _expect ("XPST0003", "foo::a", "preceeding::a", "preceding-or-ancestor::*", "p:child::a", "child::");
  }

  @Test
  public void testReservedFunctionNames ()
  {
    _expect ("XPST0003",
             "fn:exists(attribute#0)",
             "fn:exists(comment#0)",
             "fn:exists(document-node#0)",
             "fn:exists(element#0)",
             "fn:exists(empty-sequence#0)",
             "fn:exists(function#0)",
             "fn:exists(if#0)",
             "fn:exists(item#0)",
             "fn:exists(namespace-node#0)",
             "fn:exists(node#0)",
             "fn:exists(processing-instruction#0)",
             "fn:exists(schema-attribute#0)",
             "fn:exists(schema-element#0)",
             "fn:exists(switch#0)",
             "fn:exists(text#0)",
             "fn:exists(typeswitch#0)",
             "fn:exists(array#0)",
             "fn:exists(map#0)",
             "item()",
             "switch(1)",
             "typeswitch(1)",
             "map(1)",
             "empty-sequence()",
             "1 => if()",
             "1 => item()");
  }

  @Test
  public void testUnboundPrefix ()
  {
    _expect ("XPST0081",
             "p2:a",
             "p2:*",
             "@p2:a",
             "$p2:v",
             "p2:f()",
             "p2:f#1",
             "1 => p2:f()",
             "3 instance of prefixDoesNotExist:integer",
             "3 cast as prefixDoesNotExist:integer",
             "3 treat as prefixDoesNotExist:integer",
             "xmlns:a",
             "element(p2:a)",
             "attribute(a, p2:t)",
             "schema-element(p2:a)",
             "for $p2:x in 1 return 1",
             "function($p2:x) { 1 }");
  }

  @Test
  public void testXmlnsNamespace ()
  {
    _expect ("XQST0070", "Q{http://www.w3.org/2000/xmlns/}a", "Q{http://www.w3.org/2000/xmlns/}*");
  }

  @Test
  public void testUnknownFunctions ()
  {
    _expect ("XPST0017",
             "nosuch()",
             "count()",
             "count(1, 2)",
             "abs#2",
             "p:f()",
             "fn:nosuch#0",
             "1 => abs(2)",
             "xs:NOTATION('a')",
             "xs:anyAtomicType(1)",
             "concat(1)",
             "Q{urn:x}f()",
             "math:pi(1)",
             "count(?, ?)");
    _expect ("FOAR0002", "concat#340282366920938463463374607431768211456");
  }

  @Test
  public void testUndeclaredVariables ()
  {
    _expect ("XPST0008",
             "$x",
             "$p:x",
             "$Q{urn:x}v",
             "for $x in 1 return $y",
             "for $x in $x return 1",
             "for $x in 1, $y in $z return 1",
             "let $x := $x return 1",
             "(for $x in 1 return $x) + $x",
             "for $x in 1 return $x, $x",
             "some $x in 1 satisfies $y",
             "function($a) { $b }",
             "function($a) { $a } , $a",
             "function($a as xs:integer) as xs:integer { $a } ($a)",
             "1 => $nosuch()",
             "$v[$w]");
  }

  @Test
  public void testUndeclaredSchemaComponents ()
  {
    _expect ("XPST0008",
             "schema-element(a)",
             "schema-attribute(a)",
             "$v instance of schema-element(a)",
             "$v instance of document-node(schema-element(a))",
             "element(a, xs:nosuch)",
             "element(a, p:t)",
             "attribute(*, nosuch)",
             "$v instance of element(*, xs:foo)");
  }

  @Test
  public void testUnknownTypes ()
  {
    _expect ("XPST0051",
             "3 instance of xs:doesNotExist",
             "3 instance of xs:qname",
             "3 instance of none",
             "3 instance of void",
             "3 instance of integer",
             "3 treat as xs:doesNotExist",
             "'abc' instance of xs:NMTOKENS",
             "1 instance of xs:anyType",
             "1 instance of xs:anySimpleType",
             "1 instance of xs:untyped",
             "1 instance of p:t",
             "$v instance of map(xs:nosuch, item())",
             "$v instance of map(item(), item())",
             "$v instance of array(xs:nosuch)",
             "$v instance of function(xs:nosuch) as item()",
             "function($a as xs:foo) { 1 }",
             "function() as xs:foo { 1 }");
  }

  @Test
  public void testInvalidCastTargets ()
  {
    _expect ("XPST0080",
             "'s' cast as xs:NOTATION",
             "'s' castable as xs:NOTATION?",
             "() castable as xs:NOTATION",
             "'s' cast as xs:anyAtomicType",
             "'s' castable as xs:anyAtomicType",
             "'s' cast as xs:anySimpleType");
    _expect ("XQST0052",
             "'s' cast as xs:anyType",
             "'s' cast as xs:untyped",
             "3 cast as xs:doesNotExist",
             "3 cast as p:t",
             "3 cast as integer",
             "'s' cast as Q{urn:x}t");
    _expect ("XPST0003",
             "'s' cast as item()",
             "'s' castable as node()",
             "'s' castable as empty-sequence()",
             "1 instance of document()",
             "1 instance of xs:integer()");
  }

  @Test
  public void testDuplicateParameters ()
  {
    _expect ("XQST0039", "function($a, $a) { 1 }", "function($a, $Q{}a) { 1 }", "function($p:a, $Q{urn:p}a) { 1 }");
  }

  @Test
  public void testLeadingSlash ()
  {
    // A token that can start a relative path makes "/" the start of a path
    _expect ("XPST0003", "/ * 5", "4 + / * 5", "/ div 5", "/ 5 5");
    final StaticContext aCtx = ParserTestHelper.createContext ();
    // ... but tokens that cannot leave a lone slash
    XPathParser.parse ("(/) * 5", aCtx);
    XPathParser.parse ("/ = /", aCtx);
    XPathParser.parse ("(/, 1)", aCtx);
    XPathParser.parse ("4 + (/) * 5", aCtx);
    XPathParser.parse ("/ | /", aCtx);
    XPathParser.parse ("count(/)", aCtx);
    XPathParser.parse ("[/]", aCtx);
  }

  @Test
  public void testErrorMessageContainsPosition ()
  {
    try
    {
      XPathParser.parse ("1 + + * 3", ParserTestHelper.createContext ());
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPST0003", ex.getCode ());
      assertTrue (ex.getMessage ().contains ("offset"));
    }
  }
}
