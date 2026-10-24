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
package com.helger.xpath.fn;

import static com.helger.xpath.fn.FnStringTest.assertError;
import static com.helger.xpath.fn.FnStringTest.eval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;

/**
 * Test class for class {@link FnRegex}.
 *
 * @author Philip Helger
 */
public final class FnRegexTest
{
  /** Serialise an element tree compactly: name, attributes and content. */
  private static void _dump (final XdmNode aNode, final StringBuilder aSB)
  {
    if (aNode.isText ())
    {
      aSB.append (aNode.getValue ());
      return;
    }
    aSB.append ('<').append (aNode.getName ().localName ());
    for (final XdmNode aAttr : aNode.getAllAttributes ())
      aSB.append (' ').append (aAttr.getName ().localName ()).append ('=').append (aAttr.getValue ());
    aSB.append ('>');
    for (final XdmNode aChild : aNode.getAllChildren ())
      _dump (aChild, aSB);
    aSB.append ("</").append (aNode.getName ().localName ()).append ('>');
  }

  private static String _analyze (final String sExpr)
  {
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
    final XdmSequence aResult = aCompiled.evaluate (aCompiled.createDynamicContext ());
    final XdmNode aRoot = (XdmNode) aResult.get (0);
    assertEquals ("http://www.w3.org/2005/xpath-functions", aRoot.getName ().namespaceUri ());
    final StringBuilder aSB = new StringBuilder ();
    for (final XdmNode aChild : aRoot.getAllChildren ())
      _dump (aChild, aSB);
    return aSB.toString ();
  }

  @Test
  public void testMatches ()
  {
    assertEquals ("true", eval ("matches('abracadabra', 'bra')"));
    assertEquals ("true", eval ("matches('abracadabra', '^a.*a$')"));
    assertEquals ("false", eval ("matches('abracadabra', '^bra')"));
    assertEquals ("false", eval ("matches((), 'a')"));
    assertEquals ("true", eval ("matches('', '')"));
    // '$' without 'm' matches only at the very end
    assertEquals ("false", eval ("matches('abc' || codepoints-to-string(10), 'c$')"));
    // '.' excludes CR and LF unless 's' is given
    assertEquals ("false", eval ("matches(codepoints-to-string(13), '^.$')"));
    assertEquals ("true", eval ("matches(codepoints-to-string(13), '^.$', 's')"));
    // A surrogate pair is one character
    assertEquals ("true", eval ("matches('x' || codepoints-to-string(65536) || 'y', '^x.y$')"));
  }

  @Test
  public void testMultiLine ()
  {
    final String sInput = "concat('abcd', codepoints-to-string(10), 'defg', codepoints-to-string(10))";
    assertEquals ("true", eval ("matches(" + sInput + ", '^defg$', 'm')"));
    assertEquals ("false", eval ("matches(" + sInput + ", '^defg$')"));
    // Neither anchor matches after a trailing newline
    assertEquals ("false", eval ("matches(" + sInput + ", '^$', 'm')"));
  }

  @Test
  public void testFlags ()
  {
    assertEquals ("true", eval ("matches('ABC', '[a-c]+', 'i')"));
    assertEquals ("true", eval ("matches(codepoints-to-string(8490), 'k', 'i')"));
    assertEquals ("false", eval ("matches('A', '[^a]', 'i')"));
    // Category escapes are not affected by 'i'
    assertEquals ("false", eval ("matches('abc', '\\p{Lu}', 'i')"));
    assertEquals ("true", eval ("matches('hello world', 'hello\\ sworld', 'x')"));
    assertEquals ("true", eval ("matches('hello world', 'hello[ ]world', 'x')"));
    assertEquals ("true", eval ("matches('a.b', 'a.b', 'q')"));
    assertEquals ("false", eval ("matches('axb', 'a.b', 'q')"));
    assertError ("FORX0001", "matches('a', 'a', 'g')");
  }

  @Test
  public void testEscapesAndClasses ()
  {
    assertEquals ("true", eval ("matches('٣', '^\\d$')"));
    assertEquals ("false", eval ("matches('_', '^\\w$')"));
    assertEquals ("true", eval ("matches(' ', '^\\S$')"));
    assertEquals ("true", eval ("matches('ab', '^[\\i-[:]]+$')"));
    assertEquals ("false", eval ("matches(':', '^[\\i-[:]]$')"));
    assertEquals ("true", eval ("matches('-', '^\\c$')"));
    assertEquals ("true", eval ("matches('b', '^[a-z-[aeiou]]$')"));
    assertEquals ("false", eval ("matches('e', '^[a-z-[aeiou]]$')"));
    assertEquals ("false", eval ("matches('A', '^[^a-z-[A-Z]]$')"));
    assertEquals ("true", eval ("matches('a', '^\\p{IsBasicLatin}$')"));
    assertEquals ("true", eval ("matches('Α', '^\\p{IsGreek}$')"));
    assertEquals ("true", eval ("matches('͸', '^\\p{Cn}$')"));
    assertEquals ("true", eval ("matches('a', '^[a-c-]$')"));
  }

  @Test
  public void testInvalidPatterns ()
  {
    for (final String sPattern : new String [] { "(",
                                                 ")",
                                                 "a]",
                                                 "a}",
                                                 "a{",
                                                 "a{2,1}",
                                                 "a**",
                                                 "*a",
                                                 "[]",
                                                 "[a",
                                                 "[[a]]",
                                                 "[a-\\d]",
                                                 "[z-a]",
                                                 "[--z]",
                                                 "[a-c-1-4]",
                                                 "[-[a]]",
                                                 "[a-z-[aeiou]b]",
                                                 "\\b",
                                                 "\\x41",
                                                 "\\p{Foo}",
                                                 "\\p{IsUnknownBlock}",
                                                 "\\p{L",
                                                 "(?i)a",
                                                 "(?=a)",
                                                 "\\1",
                                                 "(a\\1)",
                                                 "\\0",
                                                 "a\\" })
      assertError ("FORX0002", "matches('a', '" + sPattern + "')");
  }

  @Test
  public void testBackReferences ()
  {
    assertEquals ("true", eval ("matches('abab', '^(ab)\\1$')"));
    assertEquals ("false", eval ("matches('abac', '^(ab)\\1$')"));
    assertEquals ("true", eval ("matches('aA', '^(a)\\1$', 'i')"));
    // \10 is group 1 followed by '0' when there are fewer than 10 groups
    assertEquals ("true", eval ("matches('aa0', '^(a)\\10$')"));
    assertEquals ("true", eval ("matches('aaa', '^(a)(?:\\1)*$')"));
    // A reference to a group that did not take part matches the empty string
    assertEquals ("true", eval ("matches('b', '^(a)?b\\1$')"));
    assertEquals ("true", eval ("matches('aba', '^(a)?b\\1$')"));
    assertEquals ("false", eval ("matches('abc', '^(a)?b\\1$')"));
    // Marker groups do not shift the group numbers
    assertEquals ("x[a]y", eval ("replace('xaay', '(a)\\1', '[$1]')"));
    assertEquals ("<match><group nr=1>a</group>a<group nr=2>b</group></match>",
                  _analyze ("analyze-string('aab', '(a)\\1(b)')"));
  }

  @Test
  public void testReplace ()
  {
    assertEquals ("a*cada*", eval ("replace('abracadabra', 'bra', '*')"));
    assertEquals ("*", eval ("replace('abracadabra', 'a.*a', '*')"));
    assertEquals ("*c*bra", eval ("replace('abracadabra', 'a.*?a', '*')"));
    assertEquals ("brcdbr", eval ("replace('abracadabra', 'a', '')"));
    assertEquals ("abbraccaddabbra", eval ("replace('abracadabra', 'a(.)', 'a$1$1')"));
    assertEquals ("b", eval ("replace('AAAA', 'A+', 'b')"));
    assertEquals ("bbbb", eval ("replace('AAAA', 'A+?', 'b')"));
    assertEquals ("[ab]c[ab]c", eval ("replace('abcabc', '(a)(b)?', '[$1$2$3]')"));
    assertEquals ("a$x\\c", eval ("replace('abc', 'b', '\\$x\\\\')"));
    assertEquals ("a$1c", eval ("replace('abc', 'b', '$1', 'q')"));
    assertEquals ("a-b-c", eval ("replace('a.b.c', '.', '-', 'q')"));
    assertError ("FORX0003", "replace('abc', '.*', 'x')");
    assertError ("FORX0003", "replace('abc', '', 'x')");
    assertError ("FORX0004", "replace('abc', 'b', '$')");
    assertError ("FORX0004", "replace('abc', 'b', '\\x')");
  }

  @Test
  public void testTokenize ()
  {
    assertEquals ("The|cat|sat|on|the|mat", eval ("tokenize('The cat sat on the mat', '\\s+')"));
    assertEquals ("a|b", eval ("tokenize('  a  b ')"));
    assertEquals ("", eval ("tokenize('')"));
    assertEquals ("1|15|24|50", eval ("tokenize('1, 15, 24, 50', ',\\s*')"));
    assertEquals ("1||15||24|50|", eval ("tokenize('1,,15,,24,50,', ',')"));
    assertEquals ("|r|c|d|r|", eval ("tokenize('abracadabra', '(ab)|(a)')"));
    assertEquals ("Some unparsed|HTML|text",
                  eval ("tokenize('Some unparsed <br> HTML <BR> text', '\\s*<br>\\s*', 'i')"));
    assertError ("FORX0003", "tokenize('abba', '.?')");
  }

  @Test
  public void testLongInput ()
  {
    // The Java matcher recurses per repetition of a group
    assertEquals ("true", eval ("matches(string-join(for $i in 1 to 50000 return 'ab'), '^(a|b)*$')"));
  }

  @Test
  public void testAnalyzeString ()
  {
    assertEquals ("", _analyze ("analyze-string('', 'abc')"));
    assertEquals ("<non-match>b</non-match><match>a</match><non-match>n</non-match><match>a</match>" +
                  "<non-match>n</non-match><match>a</match>",
                  _analyze ("analyze-string('banana', 'a')"));
    assertEquals ("<non-match>b</non-match><match><group nr=1>a<group nr=2>n</group></group></match>" +
                  "<match><group nr=1>a<group nr=2>n</group></group></match>" +
                  "<match><group nr=1>a<group nr=2></group></group></match>",
                  _analyze ("analyze-string('banana', '(a(n?))')"));
    // (a)(b?) and (a(b?)) capture the same spans, but nest differently
    assertEquals ("<match><group nr=1>b</group><group nr=2></group></match><non-match>anana</non-match>",
                  _analyze ("analyze-string('banana', '(b)(x?)')"));
    assertEquals ("<match>ban<group nr=1>an</group>a</match>",
                  _analyze ("analyze-string('banana', '(?:b(an)*a)')"));
    assertEquals ("<non-match>(</non-match><match>(banana)</match><non-match>)</non-match>",
                  _analyze ("analyze-string('((banana))', '(banana)', 'q')"));
    assertError ("FORX0003", "analyze-string('abc', 'x*')");
    assertTrue (_analyze ("analyze-string('how now', '(how)|(now)')").contains ("<group nr=2>now</group>"));
  }
}
