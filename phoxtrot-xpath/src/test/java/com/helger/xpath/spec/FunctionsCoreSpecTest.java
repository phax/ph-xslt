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

import static com.helger.xpath.spec.FnCoreTestHelper.coreRun;
import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.xpath.context.EXmlVersion;
import com.helger.xpath.context.StaticContext;

/**
 * Port of go-xslt <code>internal/xpath/functions_core_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FunctionsCoreSpecTest
{
  /**
   * Covers the F&O 3.1 §5.4/§5.5 string functions with
   * the REC's own examples.
   */
  @Test
  public void coreStringFunctions ()
  {
    coreRun (
      // §5.4.1 fn:concat
      ok ("concat('un', 'grateful')", "ungrateful"),
      ok ("concat('Thy ', (), 'old ', \"groans\", \"\", ' ring', ' yet', ' in', ' my', ' ancient',' ears.')", "Thy old groans ring yet in my ancient ears."),
      ok ("concat('Ciao!',())", "Ciao!"),
      ok ("concat(01, 02, 03, 04, true())", "1234true"),
      ok ("concat(10, '/', 3)", "10/3"),
      // Each argument is xs:anyAtomicType?: an array atomizes to its members.
      ok ("concat(['a'], 'b')", "ab"),
      ok ("concat([], 'b')", "b"),
      err ("concat(('a', 'b'), 'c')", "XPTY0004"),
      err ("concat(['a', 'b'], 'c')", "XPTY0004"),
      err ("concat(map{}, 'c')", "FOTY0013"),
      err ("concat(abs#1, 'c')", "FOTY0013"),

      // §5.4.3 fn:substring
      ok ("substring(\"motor car\", 6)", " car"),
      ok ("substring(\"metadata\", 4, 3)", "ada"),
      ok ("substring(\"12345\", 1.5, 2.6)", "234"),
      ok ("substring(\"12345\", 0, 3)", "12"),
      ok ("substring(\"12345\", 5, -3)", ""),
      ok ("substring(\"12345\", -3, 5)", "1"),
      ok ("substring(\"12345\", 0 div 0E0, 3)", ""),
      ok ("substring(\"12345\", 1, 0 div 0E0)", ""),
      ok ("substring((), 1, 3)", ""),
      ok ("substring(\"12345\", -42, 1 div 0E0)", "12345"),
      ok ("substring(\"12345\", -1 div 0E0, 1 div 0E0)", ""),

      // §5.4.4 fn:string-length
      ok ("string-length(\"Harp not on that string, madam; that is past.\")", "45"),
      ok ("string-length(())", "0"),
      err ("string-length((\"a\", \"b\"))", "XPTY0004"),
      err ("string-length(xs:date('2020-01-01'))", "XPTY0004"),
      ok ("string-length(xs:untypedAtomic('abc'))", "3"),

      // §5.4.5 fn:normalize-space
      ok ("normalize-space(\" The    wealthy curled darlings   of   our   nation. \")", "The wealthy curled darlings of our nation."),
      ok ("normalize-space(())", ""),
      err ("normalize-space(12)", "XPTY0004"),

      // §5.4.8 fn:translate
      ok ("translate(\"bar\",\"abc\",\"ABC\")", "BAr"),
      ok ("translate(\"--aaa--\",\"abc-\",\"ABC\")", "AAA"),
      ok ("translate(\"abcdabc\", \"abc\", \"AB\")", "ABdAB"),
      ok ("translate((), \"a\", \"b\")", ""),
      // The first occurrence of a repeated map character is the one used.
      ok ("translate(\"aa\", \"aa\", \"xy\")", "xx"),
      // A long map string (the indexed path) obeys the same rules.
      ok ("translate(\"zebra!\", \"abcdefghijklmnopqrstuvwxyzabcdefghijklmnop\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\")", "ZEBRA!"),
      ok ("translate(\"zebra\", \"abcdefghijklmnopqrstuvwxyzABCDEFGH\", \"\")", ""),
      err ("translate(\"a\", (), \"b\")", "XPTY0004"),
      err ("translate(\"a\", \"a\", ())", "XPTY0004"),
      err ("translate(1, \"a\", \"b\")", "XPTY0004"),

      // §5.4.7 fn:upper-case / §5.4.6 fn:lower-case (incl. one-to-many
      // unconditional SpecialCasing mappings).
      ok ("upper-case(\"abCd0\")", "ABCD0"),
      ok ("lower-case(\"ABc!D\")", "abc!d"),
      ok ("upper-case(\"ß\")", "SS"),
      ok ("string-to-codepoints(lower-case(\"İ\"))", "105|775"),
      ok ("upper-case(())", ""),
      err ("upper-case(12)", "XPTY0004"),
      err ("lower-case(12)", "XPTY0004"),

      // §5.5.1-§5.5.5 contains / starts-with / ends-with / substring-*.
      ok ("contains(\"tattoo\", \"t\")", "true"),
      ok ("contains(\"tattoo\", \"ttt\")", "false"),
      ok ("contains(\"\", ())", "true"),
      ok ("starts-with(\"tattoo\", \"tat\")", "true"),
      ok ("starts-with(\"tattoo\", \"att\")", "false"),
      ok ("starts-with((), ())", "true"),
      ok ("ends-with(\"tattoo\", \"tattoo\")", "true"),
      ok ("ends-with(\"tattoo\", \"atto\")", "false"),
      ok ("ends-with((), ())", "true"),
      ok ("substring-before(\"tattoo\", \"attoo\")", "t"),
      ok ("substring-before(\"tattoo\", \"tatto\")", ""),
      ok ("substring-before((), ())", ""),
      ok ("substring-before(\"abc\", \"x\")", ""),
      ok ("substring-after(\"tattoo\", \"tat\")", "too"),
      ok ("substring-after(\"tattoo\", \"tattoo\")", ""),
      ok ("substring-after((), ())", ""),
      ok ("substring-after(\"abc\", \"x\")", ""),
      // The codepoint collation named explicitly.
      ok ("contains(\"abc\", \"b\", \"http://www.w3.org/2005/xpath-functions/collation/codepoint\")", "true"),
      // §5.3.5 HTML ASCII case-insensitive collation.
      ok ("contains(\"abc\", \"B\", \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")", "true"),
      ok ("starts-with(\"Abc\", \"aB\", \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")", "true"),
      ok ("ends-with(\"abC\", \"Bc\", \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")", "true"),
      ok ("substring-before(\"xxABcyy\", \"abc\", \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")", "xx"),
      ok ("substring-after(\"xxABcyy\", \"abc\", \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")", "yy"),
      // Unsupported collation: FOCH0002.
      err ("contains(\"abc\", \"b\", \"http://example.com/no-such-collation\")", "FOCH0002"),
      err ("starts-with(\"abc\", \"b\", \"http://example.com/no-such-collation\")", "FOCH0002"),
      err ("ends-with(\"abc\", \"b\", \"http://example.com/no-such-collation\")", "FOCH0002"),
      err ("substring-before(\"abc\", \"b\", \"http://example.com/no-such-collation\")", "FOCH0002"),
      err ("substring-after(\"abc\", \"b\", \"http://example.com/no-such-collation\")", "FOCH0002"),
      // xs:string? operands: a number is a type error.
      err ("contains(\"ee\", 12)", "XPTY0004"),
      err ("starts-with(12, \"1\")", "XPTY0004"),
      err ("ends-with(\"12\", 2)", "XPTY0004"),
      err ("substring-before('Michael Kay', 2)", "XPTY0004"),
      err ("substring-after(1, 'a')", "XPTY0004"),
      err ("contains((\"a\", \"b\"), \"a\")", "XPTY0004"),

      // §5.4.2 fn:string-join
      ok ("string-join(('Now', 'is', 'the', 'time', '...'), ' ')", "Now is the time ..."),
      ok ("string-join(1 to 9)", "123456789"),
      ok ("string-join((), 'separator')", ""),
      err ("string-join(('a', 'b'), ())", "XPTY0004"),
      err ("string-join(('a', 'b'), 1)", "XPTY0004"),

      // §2.3 fn:string
      ok ("string(23)", "23"),
      ok ("string(false())", "false"),
      ok ("string(\"Paris\")", "Paris"),
      ok ("string(())", ""),
      err ("string((1, 2))", "XPTY0004"),
      err ("string(map{})", "FOTY0014"),
      err ("string([1])", "FOTY0014"),
      err ("string(abs#1)", "FOTY0014")
    );
  }

  /**
   * Covers the zero-argument forms that default to
   * the context item (F&O 3.1 §5.4.4, §5.4.5, §2.3, §4.5.1).
   */
  @Test
  public void coreStringContextForms ()
  {
    final String sDoc = "<r>  hello   world  </r>";
    coreRun (sDoc,
      ok ("/r/string-length()", "17"),
      ok ("/r/normalize-space()", "hello world"),
      ok ("/r/string()", "  hello   world  "),
      ok ("(1, 22)!string-length()", "1|2"),
      ok ("(xs:date('2020-01-01'))!normalize-space()", "2020-01-01"),
      ok ("(1, 2)!string()", "1|2"),
      // fn:number
      ok ("number('12')", "12"),
      ok ("number('abc')", "NaN"),
      ok ("number(())", "NaN"),
      ok ("number(true())", "1"),
      ok ("number(xs:date('2020-01-01'))", "NaN"),
      ok ("number(' 1e2 ') instance of xs:double", "true"),
      err ("number((1, 2))", "XPTY0004"),
      ok ("('7', '8')!number()", "7|8")
    );
    // With no focus at all the context-defaulting forms are XPDY0002.
    coreRun (env ().noContext (),
      err ("string-length()", "XPDY0002"),
      err ("normalize-space()", "XPDY0002"),
      err ("number()", "XPDY0002"),
      err ("string()", "XPDY0002"),
      err ("local-name()", "XPDY0002"),
      err ("name()", "XPDY0002"),
      err ("namespace-uri()", "XPDY0002"),
      err ("root()", "XPDY0002"),
      err ("lang('en')", "XPDY0002"),
      err ("id('a')", "XPDY0002"),
      err ("idref('a')", "XPDY0002"),
      err ("position()", "XPDY0002"),
      err ("last()", "XPDY0002")
    );
  }

  /**
   * Covers fn:name, fn:local-name, fn:namespace-uri
   * and fn:root (F&O 3.1 §2.1, §14.1-§14.3, §14.9).
   */
  @Test
  public void coreNodeNameFunctions ()
  {
    final String sDoc = "<p:a xmlns:p=\"urn:x\"><b c=\"1\"/></p:a>";
    coreRun (sDoc,
      ok ("name(/*)", "p:a"),
      ok ("local-name(/*)", "a"),
      ok ("namespace-uri(/*)", "urn:x"),
      ok ("namespace-uri(/*) instance of xs:anyURI", "true"),
      ok ("/*/*/name()", "b"),
      ok ("/*/*/local-name()", "b"),
      ok ("/*/*/namespace-uri()", ""),
      ok ("name(//@c)", "c"),
      ok ("name(/)", ""),
      ok ("name(())", ""),
      ok ("local-name(())", ""),
      ok ("namespace-uri(())", ""),
      err ("name(1)", "XPTY0004"),
      err ("local-name('a')", "XPTY0004"),
      err ("namespace-uri(1)", "XPTY0004"),
      err ("(1)!name()", "XPTY0004"),
      err ("(1)!local-name()", "XPTY0004"),
      ok ("root(//b) is /", "true"),
      ok ("//@c/root() is /", "true"),
      ok ("count(root(()))", "0"),
      err ("root(1)", "XPTY0004"),
      err ("(1)!root()", "XPTY0004")
    );
  }

  /**
   * Covers fn:lang with the F&O 3.1 §14.5.? examples.
   */
  @Test
  public void coreLang ()
  {
    final String [] [] aCases = {
      { "<para xml:lang=\"en\"/>", "/para/lang(\"en\")", "true" },
      { "<div xml:lang=\"en\"><para>And now, and forever!</para></div>", "/div/para/lang(\"en\")", "true" },
      { "<para xml:lang=\"EN\"/>", "/para/lang(\"en\")", "true" },
      { "<para xml:lang=\"en-us\"/>", "/para/lang(\"en\")", "true" },
      { "<para xml:lang=\"EN\"/>", "/para/lang(\"fr\")", "false" },
      { "<para xml:lang=\"english\"/>", "/para/lang(\"en\")", "false" },
      { "<para/>", "/para/lang(\"en\")", "false" },
      { "<div xml:lang=\"de\"><p xml:lang=\"en\"/></div>", "lang(\"de\", //p)", "false" },
      { "<div xml:lang=\"de\"><p xml:lang=\"en\"/></div>", "lang(\"EN\", //p)", "true" },
      { "<div xml:lang=\"de\"><p a=\"1\"/></div>", "lang(\"de\", //@a)", "true" },
      { "<div xml:lang=\"de\"/>", "(/div)!lang(\"de\")", "true" },
    };
    for (final String [] aCase : aCases)
      run (aCase[0], ok (aCase[1], aCase[2]));
    coreRun (
      err ("lang(\"en\", 1)", "XPTY0004"),
      err ("(1)!lang(\"en\")", "XPTY0004")
    );
  }

  /**
   * Covers fn:id, fn:element-with-id and fn:idref (F&O
   * 3.1 §14.5.1-§14.5.3) on a document whose ID/IDREF(S) attributes are typed by
   * its DTD (the attribute is deliberately not called "id").
   */
  @Test
  public void coreIDFunctions ()
  {
    final String sDoc = "<!DOCTYPE r [\n" +
                        "<!ATTLIST e k ID #IMPLIED ref IDREF #IMPLIED refs IDREFS #IMPLIED>\n" +
                        "]>\n" +
                        "<r><e k=\"a\" n=\"1\"/><e k=\"b\" n=\"2\" ref=\"a\"/><e k=\"c\" n=\"3\" refs=\"a  b\"/><f xml:id=\"x\" n=\"4\"/></r>";
    coreRun (sDoc,
      ok ("id('a')/@n/string()", "1"),
      ok ("id('c a')/@n/string()", "1|3"),
      ok ("id(('b', 'c', 'b'))/@n/string()", "2|3"),
      ok ("id('zz')", "()"),
      ok ("id(())", "()"),
      // xml:id is an ID attribute (xml:id Recommendation).
      ok ("id('x')/@n/string()", "4"),
      ok ("id('a', /r/e[3])/@n/string()", "1"),
      ok ("/r/e[2]/id('c')/@n/string()", "3"),
      ok ("element-with-id('b')/@n/string()", "2"),
      ok ("element-with-id('x a')/@n/string()", "1|4"),
      // fn:idref returns the IDREF(S) attribute nodes referring to the value.
      ok ("idref('a')/name()", "ref|refs"),
      ok ("idref('b')/../@n/string()", "3"),
      ok ("idref('c')", "()"),
      // An idref candidate is not tokenized: 'a b' is not a valid IDREF.
      ok ("idref('a b')", "()"),
      ok ("idref(('b', 'zz'), /r)/name()", "refs"),
      err ("id('a', 1)", "XPTY0004"),
      err ("idref('a', 'x')", "XPTY0004"),
      err ("(1)!id('a')", "XPTY0004"),
      err ("(1)!idref('a')", "XPTY0004")
    );
  }

  /**
   * Covers fn:tokenize (F&O 3.1 §5.6.5) with the REC examples.
   */
  @Test
  public void coreTokenize ()
  {
    coreRun (
      ok ("tokenize(\" red green blue \")", "red|green|blue"),
      ok ("tokenize(\"The cat sat on the mat\", \"\\s+\")", "The|cat|sat|on|the|mat"),
      ok ("tokenize(\" red green blue \", \"\\s+\")", "|red|green|blue|"),
      ok ("tokenize(\"1, 15, 24, 50\", \",\\s*\")", "1|15|24|50"),
      ok ("tokenize(\"1,15,,24,50,\", \",\")", "1|15||24|50|"),
      ok ("tokenize(\"Some unparsed <br> HTML <BR> text\", \"\\s*<br>\\s*\", \"i\")", "Some unparsed|HTML|text"),
      ok ("tokenize(\"abracadabra\", \"(ab)|(a)\")", "|r|c|d|r|"),
      ok ("tokenize(\"12.3.5.6\", \".\", \"q\")", "12|3|5|6"),
      ok ("tokenize(\"\", \"a\")", "()"),
      err ("tokenize(\"abba\", \".?\")", "FORX0003"),
      err ("tokenize(\"abc\", \"b\", \"k\")", "FORX0001"),
      err ("tokenize(\"abc\", \"(\")", "FORX0002"),
      err ("tokenize(\"abc\", \"b\", ())", "XPTY0004")
    );
  }

  /**
   * Covers fn:matches and the regular-expression syntax and
   * flags (F&O 3.1 §5.6.1, §5.6.2, §5.6.3).
   */
  @Test
  public void coreMatches ()
  {
    final String sPoem = "let $poem := 'Kaum hat dies der Hahn gesehen,\nFängt er auch schon an zu krähen:\nKikeriki! Kikikerikih!!\nTak, tak, tak! - da kommt er schon.' return ";
    coreRun (
      ok ("matches(\"abracadabra\", \"bra\")", "true"),
      ok ("matches(\"abracadabra\", \"^a.*a$\")", "true"),
      ok ("matches(\"abracadabra\", \"^bra\")", "false"),
      ok (sPoem + "matches($poem, \"Kaum.*krähen\")", "false"),
      ok (sPoem + "matches($poem, \"Kaum.*krähen\", \"s\")", "true"),
      ok (sPoem + "matches($poem, \"^Kaum.*gesehen,$\", \"m\")", "true"),
      ok (sPoem + "matches($poem, \"^Kaum.*gesehen,$\")", "false"),
      ok (sPoem + "matches($poem, \"kiki\", \"i\")", "true"),
      // 'x' flag examples (§5.6.2).
      ok ("matches(\"helloworld\", \"hello world\", \"x\")", "true"),
      ok ("matches(\"helloworld\", \"hello[ ]world\", \"x\")", "false"),
      ok ("matches(\"hello world\", \"hello\\ sworld\", \"x\")", "true"),
      ok ("matches(\"hello world\", \"hello world\", \"x\")", "false"),
      // 'q' flag examples (§5.6.2).
      ok ("matches(\"abcd\", \".*\", \"q\")", "false"),
      ok ("matches(\"Mr. B. Obama\", \"B. OBAMA\", \"iq\")", "true"),
      // 'm': neither anchor matches at the position after a final newline.
      ok ("matches(codepoints-to-string((97, 10, 98, 10)), \"^$\", \"m\")", "false"),
      ok ("matches(codepoints-to-string((97, 10, 10, 98)), \"^$\", \"m\")", "true"),
      // '.' matches neither #xA nor #xD outside dot-all mode.
      ok ("matches(codepoints-to-string(13), \"^.$\")", "false"),
      ok ("matches(codepoints-to-string(10), \"^.$\")", "false"),
      ok ("matches(codepoints-to-string(13), \"^.$\", \"s\")", "true"),
      // Back-references (§5.6.1).
      ok ("matches(\"abab\", \"^(ab)\\1$\")", "true"),
      ok ("matches(\"abac\", \"^(ab)\\1$\")", "false"),
      ok ("matches(\"ABab\", \"^(ab)\\1$\", \"i\")", "true"),
      // Multi-character escapes \i \I \c \C (XSD Part 2 App. F).
      ok ("matches(\"a1\", \"^\\i\\c*$\")", "true"),
      ok ("matches(\"1a\", \"^\\i\")", "false"),
      ok ("matches(\"1\", \"^\\I$\")", "true"),
      ok ("matches(\" \", \"^\\C$\")", "true"),
      ok ("matches(\"-\", \"^\\C$\")", "false"),
      ok ("matches(\"1\", \"^[\\i]$\")", "false"),
      ok ("matches(\"_\", \"^[\\i]$\")", "true"),
      ok ("matches(\"1\", \"^[\\I]$\")", "true"),
      ok ("matches(\" \", \"^[\\C]$\")", "true"),
      ok ("matches(\".\", \"^[\\c]$\")", "true"),
      // \d is \p{Nd}; \w excludes punctuation, separators and other.
      ok ("matches(\"" + "٣" + "\", \"^\\d$\")", "true"),
      ok ("matches(\"a\", \"^\\D$\")", "true"),
      ok ("matches(\"" + "٣" + "\", \"^[\\d]$\")", "true"),
      ok ("matches(\"a\", \"^[\\D]$\")", "true"),
      ok ("matches(\"5\", \"^[\\D]$\")", "false"),
      ok ("matches(\"a\", \"^\\w$\")", "true"),
      ok ("matches(\"_\", \"^\\w$\")", "false"),
      ok ("matches(\"_\", \"^\\W$\")", "true"),
      ok ("matches(\"+\", \"^[\\w]$\")", "true"),
      ok ("matches(\"_\", \"^[\\w]$\")", "false"),
      ok ("matches(\"_\", \"^[\\W]$\")", "true"),
      ok ("matches(\" \", \"^\\s$\")", "true"),
      ok ("matches(\"" + "\u00A0" + "\", \"^\\s$\")", "false"),
      ok ("matches(\"x\", \"^\\S$\")", "true"),
      ok ("matches(\" \", \"^[\\s]$\")", "true"),
      ok ("matches(\"x\", \"^[\\S]$\")", "true"),
      ok ("matches(\" \", \"^[\\S]$\")", "false"),
      // Block escapes (XSD Part 2 App. F.1.1).
      ok ("matches(\"a\", \"^\\p{IsBasicLatin}$\")", "true"),
      ok ("matches(\"é\", \"^\\p{IsBasicLatin}$\")", "false"),
      ok ("matches(\"é\", \"^\\P{IsBasicLatin}$\")", "true"),
      ok ("matches(\"é\", \"^[\\P{IsBasicLatin}]$\")", "true"),
      ok ("matches(\"a\", \"^[\\P{IsBasicLatin}]$\")", "false"),
      ok ("matches(\"é\", \"^[\\p{IsLatin-1Supplement}]$\")", "true"),
      err ("matches(\"a\", \"\\p{IsNoSuchBlock}\")", "FORX0002"),
      err ("matches(\"a\", \"[\\P{IsNoSuchBlock}]\")", "FORX0002"),
      err ("matches(\"a\", \"\\p{L\")", "FORX0002"),
      // Category escapes; C includes the unassigned code points.
      ok ("matches(\"A\", \"^\\p{Lu}$\")", "true"),
      ok ("matches(\"a\", \"^\\P{Lu}$\")", "true"),
      ok ("matches(\"a\", \"^\\P{C}$\")", "true"),
      ok ("matches(\"a\", \"^\\p{Cn}$\")", "false"),
      // The 'i' flag does not change what a category escape matches.
      ok ("matches(\"a\", \"\\p{Lu}\", \"i\")", "false"),
      ok ("matches(\"A\", \"\\p{Lu}\", \"i\")", "true"),
      // Quantifiers above RE2's own limit.
      ok ("matches(string-join((1 to 1500)!'a'), \"^a{1500}$\")", "true"),
      ok ("matches(string-join((1 to 1499)!'a'), \"^a{1500}$\")", "false"),
      ok ("matches(\"aaa\", \"a{2147483647}\")", "false"),
      ok ("matches(\"aaa\", \"a{2147483647,}\")", "false"),
      ok ("matches(\"aaa\", \"a{2147483647,2147483648}\")", "false"),
      ok ("matches(\"aaa\", \"^a{2,3}$\")", "true"),
      ok ("matches(\"a{\", \"a\\{\")", "true"),
      ok ("matches(\"{\", \"[{]\")", "true"),
      // Grammar errors (FORX0002).
      err ("matches(\"a\", \"(\")", "FORX0002"),
      err ("matches(\"a\", \"a{\")", "FORX0002"),
      err ("matches(\"a\", \"a{,2}\")", "FORX0002"),
      err ("matches(\"a\", \"a}\")", "FORX0002"),
      err ("matches(\"a\", \"(?i)a\")", "FORX0002"),
      err ("matches(\"a\", \"(?=a)\")", "FORX0002"),
      ok ("matches(\"a\", \"(?:a)\")", "true"),
      err ("matches(\"a\", \"\\b\")", "FORX0002"),
      err ("matches(\"a\", \"\\0\")", "FORX0002"),
      err ("matches(\"a\", \"(a)[\\1]\")", "FORX0002"),
      // Flags (§5.6.2): an unknown flag is FORX0001.
      err ("matches(\"a\", \"a\", \"z\")", "FORX0001"),
      err ("matches(\"a\", \"a\", ())", "XPTY0004"),
      err ("matches(\"a\", \"a\", 1)", "XPTY0004"),
      err ("matches(\"a\", ())", "XPTY0004"),
      ok ("matches((), \"a\")", "false"),
      ok ("matches((), \"^$\")", "true")
    );
  }

  /**
   * Covers fn:replace (F&O 3.1 §5.6.4) with the REC examples
   * and the $N rules.
   */
  @Test
  public void coreReplace ()
  {
    coreRun (
      ok ("replace(\"abracadabra\", \"bra\", \"*\")", "a*cada*"),
      ok ("replace(\"abracadabra\", \"a.*a\", \"*\")", "*"),
      ok ("replace(\"abracadabra\", \"a.*?a\", \"*\")", "*c*bra"),
      ok ("replace(\"abracadabra\", \"a\", \"\")", "brcdbr"),
      ok ("replace(\"abracadabra\", \"a(.)\", \"a$1$1\")", "abbraccaddabbra"),
      err ("replace(\"abracadabra\", \".*?\", \"$1\")", "FORX0003"),
      ok ("replace(\"AAAA\", \"A+\", \"b\")", "b"),
      ok ("replace(\"AAAA\", \"A+?\", \"b\")", "bbbb"),
      ok ("replace(\"darted\", \"^(.*?)d(.*)$\", \"$1c$2\")", "carted"),
      ok ("replace(\"abcd\", \"(ab)|(a)\", \"[1=$1][2=$2]\")", "[1=ab][2=]cd"),
      // $0 is the whole match; S < N <= 9 gives ""; N > 9 and > S drops the
      // last digit as a literal.
      ok ("replace(\"abc\", \"b\", \"[$0]\")", "a[b]c"),
      ok ("replace(\"abc\", \"(b)\", \"[$2]\")", "a[]c"),
      ok ("replace(\"abc\", \"(b)\", \"[$12]\")", "a[b2]c"),
      // \$ and \\ are literal.
      ok ("replace(\"abc\", \"b\", \"\\$\")", "a$c"),
      ok ("replace(\"abc\", \"b\", \"\\\\\")", "a\\c"),
      err ("replace(\"abc\", \"b\", \"$\")", "FORX0004"),
      err ("replace(\"abc\", \"b\", \"\\x\")", "FORX0004"),
      err ("replace(\"abc\", \"b\", \"x\\\")", "FORX0004"),
      // 'q' flag examples (§5.6.2).
      ok ("replace(\"a\\b\\c\", \"\\\", \"\\\\\", \"q\")", "a\\\\b\\\\c"),
      ok ("replace(\"a/b/c\", \"/\", \"$\", \"q\")", "a$b$c"),
      ok ("replace(\"A.b.a.\", \"a.\", \"$x\", \"iq\")", "$xb.$x"),
      err ("replace(\"abc\", \"\", \"x\", \"q\")", "FORX0003"),
      ok ("replace(\"abc\", \"B\", \"x\", \"i\")", "axc"),
      err ("replace(\"abc\", \"b\", \"x\", \"z\")", "FORX0001"),
      err ("replace(\"abc\", \"(\", \"x\")", "FORX0002"),
      err ("replace(\"abc\", (), \"x\")", "XPTY0004"),
      err ("replace(\"abc\", \"b\", ())", "XPTY0004"),
      ok ("replace((), \"b\", \"x\")", "")
    );
  }

  /**
   * Covers the xs: constructor functions dispatched from
   * functions.go (XPath 3.1 §3.1.5.2 / F&O 3.1 §18, §19), including the three
   * built-in list types.
   */
  @Test
  public void coreConstructors ()
  {
    coreRun (
      ok ("xs:integer('5') + 1", "6"),
      ok ("xs:integer(())", "()"),
      err ("xs:integer((1, 2))", "XPTY0004"),
      ok ("xs:double('INF') eq 1 div 0e0", "true"),
      ok ("xs:float('-INF') lt 0", "true"),
      ok ("xs:double(())", "()"),
      err ("xs:double('abc')", "FORG0001"),
      ok ("xs:gYear('2020') instance of xs:gYear", "true"),
      ok ("string(xs:gYearMonth('2020-02'))", "2020-02"),
      ok ("xs:anyURI('http://example.com/a') instance of xs:anyURI", "true"),
      ok ("xs:anyURI(())", "()"),
      // xs:QName: a prefix is resolved in the static context.
      ok ("namespace-uri-from-QName(xs:QName('xs:integer'))", "http://www.w3.org/2001/XMLSchema"),
      ok ("local-name-from-QName(xs:QName(' local '))", "local"),
      ok ("xs:QName(xs:QName('xs:integer')) eq xs:QName('xs:integer')", "true"),
      ok ("xs:QName(())", "()"),
      err ("xs:QName('undeclared:a')", "FONS0004"),
      err ("xs:QName(('a', 'b'))", "XPTY0004"),
      // List types: whitespace-separated tokens, each of the item type.
      ok ("count(xs:NMTOKENS(' a b  c '))", "3"),
      ok ("xs:NMTOKENS(' a b ')", "a|b"),
      ok ("xs:NMTOKENS(' a b ')[1] instance of xs:NMTOKEN", "true"),
      ok ("xs:IDREFS('x y')[2] instance of xs:IDREF", "true"),
      ok ("xs:ENTITIES('e1')[1] instance of xs:ENTITY", "true"),
      ok ("xs:NMTOKENS(())", "()"),
      err ("xs:NMTOKENS('')", "FORG0001"),
      err ("xs:NMTOKENS('  ')", "FORG0001"),
      err ("xs:IDREFS('1a')", "FORG0001"),
      err ("xs:NMTOKENS(('a', 'b'))", "XPTY0004")
    );
  }

  /**
   * Covers how a call is resolved: any prefix bound to a
   * standard function namespace (XPath 3.1 §3.1.5: a function is identified by
   * its expanded QName), partial application (§3.1.5.1), and the unknown-
   * function and fn-prefix errors (XPST0017, XPST0081).
   */
  @Test
  public void coreDispatch ()
  {
    coreRun (env ().ns ("xfn", "http://www.w3.org/2005/xpath-functions")
                   .ns ("m", "http://www.w3.org/2005/xpath-functions/math")
                   .ns ("mp", "http://www.w3.org/2005/xpath-functions/map")
                   .ns ("ar", "http://www.w3.org/2005/xpath-functions/array")
                   .ns ("xsd", "http://www.w3.org/2001/XMLSchema"),
      ok ("xfn:concat('a', 'b')", "ab"),
      ok ("xsd:integer('5') + 1", "6"),
      ok ("m:sqrt(16)", "4"),
      ok ("mp:size(map{1: 2})", "1"),
      ok ("ar:size([1, 2])", "2"),
      ok ("let $f := substring(?, 2) return $f('abc')", "bc"),
      ok ("concat('a', ?, ?)('b', 'c')", "abc"),
      err ("let $f := substring((1 div 0) idiv 1, ?) return 1", "FOAR0001")
    );
    // A host that predeclares no fn prefix leaves a lexical fn: unbound.
    coreRun (env ().staticSetup (StaticContext::removeAllNamespacesExceptXml),
      err ("fn:concat('a', 'b')", "XPST0081"),
      ok ("concat('a', 'b')", "ab")
    );
  }

  /**
   * The two category cases of {@link #coreMatches()} on codepoint 1: under XML
   * 1.0 (the default here) <code>codepoints-to-string(1)</code> is FOCH0001
   * (F&O 3.1 §5.2.1), so they run with XML 1.1 characters, where #x1 is
   * permitted.
   */
  @Test
  public void coreMatchesCategoryC ()
  {
    coreRun (env ().staticSetup (aSC -> aSC.setXmlVersion (EXmlVersion.V11)),
      ok ("matches(codepoints-to-string(1), \"^\\p{C}$\")", "true"),
      ok ("matches(codepoints-to-string(1), \"^[\\p{C}]$\")", "true")
    );
  }
}
