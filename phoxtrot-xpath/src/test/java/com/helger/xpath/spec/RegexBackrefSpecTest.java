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

import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

/**
 * Port of go-xslt's <code>regex_backref_test.go</code>: back-references in
 * regular expressions (F&amp;O 3.1 §5.6.1.4).
 *
 * @author Philip Helger
 */
public final class RegexBackrefSpecTest
{
  /**
   * Back-references (F&amp;O 3.1 §5.6.1.4) together with quantifiers (greedy and
   * reluctant, §5.6.1.2), alternation, capturing and non-capturing groups
   * (§5.6.1.3) and the anchors (§5.6.1.1).
   */
  @Test
  public void testRxBackrefMatching ()
  {
    run (// The back-reference matches the string the group matched.
         ok ("matches('abcabc', '^(abc)\\1$')", "true"),
         ok ("matches('abcabd', '^(abc)\\1$')", "false"),
         ok ("matches('xabcabcy', '(abc)\\1')", "true"),
         ok ("matches('abab', '(ab)\\1')", "true"),
         ok ("matches('abba', '^(a)(b)\\2\\1$')", "true"),
         ok ("matches('abab', '^(a)(b)\\2\\1$')", "false"),
         // A back-reference longer than the rest of the input fails.
         ok ("matches('aba', '^(ab)\\1$')", "false"),
         // "If the Nth group has not matched anything, the back-reference
         // matches a zero-length string."
         ok ("matches('b', '^(a)?b\\1$')", "true"),
         ok ("matches('ab', '^(a)?b\\1$')", "false"),
         ok ("matches('aba', '^(a)?b\\1$')", "true"),
         // Alternation inside and outside groups.
         ok ("matches('xx', '^(x|y)\\1$')", "true"),
         ok ("matches('yy', '^(x|y)\\1$')", "true"),
         ok ("matches('xy', '^(x|y)\\1$')", "false"),
         ok ("matches('zz', '^(a)\\1$|^zz$')", "true"),
         ok ("matches('aa', '^(a)\\1$|^zz$')", "true"),
         ok ("matches('az', '^(a)\\1$|^zz$')", "false"),
         // Quantified back-references: *, +, ?, {n}, {n,}, {n,m}.
         ok ("matches('a', '^(a)\\1*$')", "true"),
         ok ("matches('aaaa', '^(a)\\1*$')", "true"),
         ok ("matches('a', '^(a)\\1+$')", "false"),
         ok ("matches('aaa', '^(a)\\1+$')", "true"),
         ok ("matches('a', '^(a)\\1?$')", "true"),
         ok ("matches('aa', '^(a)\\1?$')", "true"),
         ok ("matches('aaa', '^(a)\\1?$')", "false"),
         ok ("matches('aaa', '^(a)\\1{2}$')", "true"),
         ok ("matches('aa', '^(a)\\1{2}$')", "false"),
         ok ("matches('aaaa', '^(a)\\1{2}$')", "false"),
         ok ("matches('aa', '^(a)\\1{2,}$')", "false"),
         ok ("matches('aaaaaa', '^(a)\\1{2,}$')", "true"),
         ok ("matches('aaa', '^(a)\\1{2,3}$')", "true"),
         ok ("matches('aaaa', '^(a)\\1{2,3}$')", "true"),
         ok ("matches('aaaaa', '^(a)\\1{2,3}$')", "false"),
         ok ("matches('aaaa', '^(a)\\1{2,3}?$')", "true"),
         // A quantified group followed by its back-reference: the reference
         // is to the LAST iteration's match (§5.6.1.3).
         ok ("matches('abb', '^(a|b)+\\1$')", "true"),
         ok ("matches('aba', '^(a|b)+\\1$')", "false"),
         // Non-capturing groups do not count for the numbering.
         ok ("matches('xyy', '^(?:x)(y)\\1$')", "true"),
         ok ("matches('xyx', '^(?:x)(y)\\1$')", "false"),
         // Leaves: character classes, multi-character and category escapes.
         ok ("matches('77', '^([0-9])\\1$')", "true"),
         ok ("matches('78', '^([0-9])\\1$')", "false"),
         ok ("matches('55', '^(\\d)\\1$')", "true"),
         ok ("matches('ÄÄ', '^(\\p{Lu})\\1$')", "true"),
         ok ("matches('ää', '^(\\p{Lu})\\1$')", "false"),
         ok ("matches('ää', '^(\\P{Lu})\\1$')", "true"),
         ok ("matches('aa', '^(\\p{IsBasicLatin})\\1$')", "true"),
         // '.' excludes #xA and #xD outside dot-all mode (§5.6.2 's').
         ok ("matches('qq', '^(.)\\1$')", "true"),
         ok ("matches(codepoints-to-string((10, 10)), '^(.)\\1$')", "false"),
         ok ("matches('a.a.', '^(a\\.)\\1$')", "true"),
         ok ("matches('abab', '^(a\\.)\\1$')", "false"));
  }

  /**
   * Under 's' the '.' matches every character, #xA included (F&amp;O 3.1
   * §5.6.2), also in a pattern that uses a back-reference.
   */
  @Test
  public void testRxBackrefDotAll ()
  {
    run (ok ("matches(codepoints-to-string((10, 10)), '^(.)\\1$', 's')", "true"),
         // '.' stays a wildcard under 's', not a literal full stop.
         ok ("matches('qq', '^(.)\\1$', 's')", "true"),
         ok ("matches('qr', '^(.)\\1$', 's')", "false"),
         ok ("matches('..', '^(.)\\1$', 's')", "true"));
  }

  /**
   * §5.6.1.4: a single digit is always a back-reference, a further digit is
   * part of it only when the larger number names an existing group.
   */
  @Test
  public void testRxBackrefMultiDigit ()
  {
    run (// One group: "\10" is \1 followed by the literal '0'.
         ok ("matches('aa0', '^(a)\\10$')", "true"),
         ok ("matches('aa', '^(a)\\10$')", "false"),
         // Eleven groups, all closed before the reference: "\11" is group 11.
         ok ("matches('abcdefghijkk', '^(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11$')", "true"),
         ok ("matches('abcdefghijkaa', '^(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11$')", "false"),
         // Two groups: "\21" is group 2 followed by '1'.
         ok ("matches('abb1', '^(a)(b)\\21$')", "true"));
  }

  /**
   * A further digit joins the reference only if the larger number names a group
   * "whose opening parenthesis precedes the back-reference" (F&amp;O 3.1
   * §5.6.1.4).
   */
  @Test
  public void testRxBackrefMultiDigitOpenAfter ()
  {
    run (ok ("matches('abcdefghia0j', '^(a)(b)(c)(d)(e)(f)(g)(h)(i)\\10(j)$')", "true"));
  }

  /**
   * Under the 'i' flag back-references are compared case-blind (§5.6.1.4),
   * while \p{Lu} keeps matching only upper-case letters (§5.6.2 'i').
   */
  @Test
  public void testRxBackrefCaseInsensitive ()
  {
    run (ok ("matches('aA', '^(a)\\1$', 'i')", "true"),
         ok ("matches('aA', '^(a)\\1$')", "false"),
         ok ("matches('ABac', '^(ab)\\1$', 'i')", "false"),
         ok ("matches('ABab', '^(ab)\\1$', 'i')", "true"),
         ok ("matches('Aa', '^(\\p{Lu})\\1$', 'i')", "true"),
         ok ("matches('aa', '^(\\p{Lu})\\1$', 'i')", "false"));
  }

  /**
   * ^ and $ match at the whole string's ends by default, and at line boundaries
   * (#xA only) under 'm' (§5.6.1.1, §5.6.2).
   */
  @Test
  public void testRxBackrefAnchors ()
  {
    run (ok ("matches(\"x\naa\ny\", '^(a)\\1$')", "false"),
         ok ("matches(\"x\naa\ny\", '^(a)\\1$', 'm')", "true"),
         ok ("matches(\"x\naab\ny\", '^(a)\\1$', 'm')", "false"),
         ok ("matches(\"baa\ny\", '^(a)\\1', 'm')", "false"),
         ok ("matches(\"aa\n\", '(a)\\1$', 'm')", "true"));
  }

  /**
   * Reluctant quantifiers (§5.6.1.2: the shortest possible match) through
   * fn:replace, where the captured text is visible.
   */
  @Test
  public void testRxBackrefReluctant ()
  {
    run (ok ("replace('aaaa', '(a)(a*)\\1', '[$2]')", "[aa]"),
         ok ("replace('aaaa', '(a)(a*?)\\1', '[$2]')", "[][]"),
         ok ("replace('aaaa', '(a)(a+?)\\1', '[$2]')", "[a]a"),
         ok ("replace('aaaa', '(a)(a??)\\1', '[$2]')", "[][]"),
         ok ("replace('aaaaa', '(a)(a{1,3}?)\\1', '[$2]')", "[a]aa"));
  }

  /**
   * The back-reference matcher through fn:replace (§5.6.4's $N rules),
   * fn:tokenize and fn:analyze-string.
   */
  @Test
  public void testRxBackrefFunctions ()
  {
    run (ok ("replace('abab', '(a)(b)\\1', 'X')", "Xb"),
         ok ("replace('xaay bbz', '(\\w)\\1', '<$1>')", "x<a>y <b>z"),
         ok ("replace('xaay', '(\\w)\\1', '$0$0')", "xaaaay"),
         ok ("replace('aa', '(a)\\1', '\\$')", "$"),
         ok ("replace('aa', '(a)\\1', '\\\\')", "\\"),
         // A group that did not participate becomes the zero-length string.
         ok ("replace('b', '(a)?b\\1', '[$1]')", "[]"),
         // S < N <= 9: zero-length string.
         ok ("replace('aa', '(a)\\1', '[$5]')", "[]"),
         // Two groups: "$23" is group 2 followed by '3'.
         ok ("replace('abab', '(a)(b)\\1\\2', '$23')", "b3"),
         ok ("replace('nomatch', '(a)\\1', 'X')", "nomatch"),
         ok ("string-join(tokenize('xaay bbz', '(\\w)\\1'), '|')", "x|y |z"),
         ok ("string-join(tokenize('aa', '(a)\\1'), '|')", "|"),
         ok ("string-join(analyze-string('xabay', '(a)(b)\\1')/*:match/*:group[@nr = 2], ',')", "b"),
         ok ("string-join(analyze-string('xabay', '(a)(b)\\1')/*:non-match, ',')", "x,y"),
         ok ("string-join(analyze-string('ééxéé', '(é)\\1')/*:match, ',')", "éé,éé"));
  }

  /**
   * Invalid back-references (§5.6.1.4): one to a group that does not exist, or
   * whose closing parenthesis comes after the reference, makes the pattern
   * invalid (FORX0002).
   */
  @Test
  public void testRxBackrefErrors ()
  {
    run (err ("matches('aa', '(a)\\2')", "FORX0002"),
         err ("matches('aa', '\\1(a)')", "FORX0002"),
         err ("matches('aa', '(a\\1)')", "FORX0002"),
         err ("matches('aa', '((a)\\1)')", "FORX0002"),
         err ("matches('aa', '\\1')", "FORX0002"),
         // Inside a character class a backslash before a digit is invalid.
         err ("matches('aa', '(a)[\\1]')", "FORX0002"));
  }
}
