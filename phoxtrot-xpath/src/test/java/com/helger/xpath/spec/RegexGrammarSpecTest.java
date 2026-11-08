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
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.regex.XPathRegex;

/**
 * Port of go-xslt's <code>regex_grammar_test.go</code>: the regular
 * expression grammar and flags of F&amp;O 3.1 §5.6.
 *
 * @author Philip Helger
 */
public final class RegexGrammarSpecTest
{
  /**
   * A pattern and its validity in the three grammars go-xslt implements; only
   * the XPath column is checked here (XSD pattern facets are left to the JDK).
   */
  private record GrammarCase (@NonNull String pat, boolean xsd10, boolean xsd11, boolean xpath)
  {}

  @NonNull
  private static GrammarCase g (@NonNull final String sPat, final boolean bXsd10, final boolean bXsd11, final boolean bXPath)
  {
    return new GrammarCase (sPat, bXsd10, bXsd11, bXPath);
  }

  private static void _runGrammar (@NonNull final GrammarCase... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final GrammarCase aCase : aCases)
    {
      String sError = null;
      try
      {
        XPathRegex.compile (aCase.pat (), "");
      }
      catch (final XPathException ex)
      {
        sError = ex.getCode () + ": " + ex.getMessage ();
      }
      if ((sError == null) != aCase.xpath ())
        aErrors.add ("XPath " + aCase.pat () + ": err=" + sError + ", want valid=" + aCase.xpath ());
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  /**
   * The XPath grammar of F&amp;O 3.1 §5.6.1 (XSD's plus anchors, reluctant
   * quantifiers, back-references and "(?:...)"). The XSD 1.0 and 1.1 columns
   * are kept as data only.
   */
  @Test
  public void testRxValidateRegexGrammar ()
  {
    _runGrammar (g ("abc", true, true, true),
                g ("a|", true, true, true),
                g ("|", true, true, true),
                g ("(a|)", true, true, true),
                g ("a()b", true, true, true),
                g ("(a", false, false, false),
                g ("a)", false, false, false),
                g ("a]", false, false, false),
                // Quantifiers: a quantifier needs an atom and a piece has at most one.
                g ("a*", true, true, true),
                g ("a{2}", true, true, true),
                g ("a{2,}", true, true, true),
                g ("a{1,2}", true, true, true),
                g ("a{09,10}", true, true, true),
                g ("a{2,1}", false, false, false),
                g ("a{0010,9}", false, false, false),
                g ("a{,2}", false, false, false),
                g ("a{x}", false, false, false),
                g ("a{1", false, false, false),
                g ("a**", false, false, false),
                g ("a{2}*", false, false, false),
                g ("a*{2}", false, false, false),
                g ("a{1,2}{3}", false, false, false),
                g ("a?+", false, false, false),
                g ("*a", false, false, false),
                g ("+", false, false, false),
                g ("?", false, false, false),
                g ("{2}", false, false, false),
                g ("a|*", false, false, false),
                g ("(*)", false, false, false),
                // Reluctant quantifiers are F&O-only (§5.6.1.2).
                g ("a*?", false, false, true),
                g ("a+?", false, false, true),
                g ("a??", false, false, true),
                g ("a{2}?", false, false, true),
                g ("a{2,3}?", false, false, true),
                g ("a*?*", false, false, false),
                // Back-references (§5.6.1.4) and "(?:…)" (§5.6.1.3) are F&O-only;
                // no other "(?…)" form exists in any of the grammars.
                g ("(a)\\1", false, false, true),
                g ("(a)\\12", false, false, true),
                g ("(?:a)", false, false, true),
                g ("(?=a)", false, false, false),
                g ("(?i)a", false, false, false),
                // Escapes: the repertoire is closed.
                g ("\\n\\r\\t\\\\\\|\\.\\?\\*\\+\\(\\)\\{\\}\\-\\[\\]\\^", true, true, true),
                g ("\\s\\S\\i\\I\\c\\C\\d\\D\\w\\W", true, true, true),
                g ("\\$", false, false, true),
                g ("\\", false, false, false),
                g ("a\\", false, false, false),
                g ("\\b", false, false, false),
                g ("\\x41", false, false, false),
                g ("\\0", false, false, false),
                g ("\\p", false, false, false),
                g ("\\pL", false, false, false),
                g ("\\p{L", false, false, false),
                g ("\\p{Lu}", true, true, true),
                g ("\\P{Nd}", true, true, true),
                g ("\\p{Cn}", true, true, true),
                g ("\\p{Xx}", false, false, false),
                g ("\\p{L&}", false, false, false),
                // Block escapes: IsX with a grammatical name; an unknown block is an
                // error in XSD 1.0 only at the grammar level.
                g ("\\p{IsBasicLatin}", true, true, true),
                g ("\\p{IsLatin-1Supplement}", true, true, true),
                g ("\\p{Is}", false, false, false),
                g ("\\p{Is_A}", false, false, false),
                // go-xslt: grammatically valid (true); this port checks
                // through the function-level compile, where an unknown block
                // is FORX0002 (as testRxGrammarErrorsThroughFunctions asserts)
                g ("\\p{IsFooBar}", false, true, false),
                // Character classes.
                g ("[abc]", true, true, true),
                g ("[^abc]", true, true, true),
                g ("[a-z]", true, true, true),
                g ("[\\t-\\r]", true, true, true),
                g ("[\\--\\[]", true, true, true),
                g ("[a-]", true, true, true),
                g ("[-a]", true, true, true),
                g ("[a-z-]", true, true, true),
                g ("[]", false, false, false),
                g ("[^]", false, false, false),
                g ("a[]]b", false, false, false),
                g ("[[a]", false, false, false),
                g ("[a", false, false, false),
                g ("[a-", false, false, false),
                g ("[\\", false, false, false),
                g ("[z-a]", false, false, false),
                g ("[a-\\n]", false, false, false),
                g ("[--z]", false, false, false),
                g ("[!--]", false, false, false),
                g ("[a--b]", false, false, false),
                g ("[a-\\d]", false, false, false),
                g ("[\\d-z]", false, false, false),
                g ("[a-\\p{L}]", false, false, false),
                g ("[\\p{L]", false, false, false),
                g ("[\\p{Lu}\\d]", true, true, true),
                g ("[\\$-a]", false, false, true),
                g ("[\\1]", false, false, false),
                // Class subtraction: a non-empty base, and the subtraction last.
                g ("[a-z-[aeiou]]", true, true, true),
                g ("[a-z-[^b]]", true, true, true),
                g ("[\\p{L}-[a]]", true, true, true),
                g ("[-[a]]", false, false, false),
                g ("[a-z-[b]c]", false, false, false),
                g ("[a-z-[b]", false, false, false),
                g ("[a-z-[[b]]]", false, false, false),
                // XSD 1.0's charClass production rejects a chained range and a bare
                // '-' after a range unless it closes the class or precedes '-[';
                // XSD 1.1's charGroupPart (G.2) reads it as a literal.
                g ("[a-z--[b-z]]", true, true, true),
                g ("[a-c-1-4]", false, true, true),
                g ("[0-9-.]", false, true, true));
  }

  /**
   * The flags argument (F&amp;O 3.1 §5.6.2), using the spec's own examples for
   * 'x' and 'q'.
   */
  @Test
  public void testRxFlags ()
  {
    run (// 'x': whitespace is removed from the pattern, except inside a class.
        ok ("matches('helloworld', 'hello world', 'x')", "true"),
        ok ("matches('helloworld', 'hello[ ]world', 'x')", "false"),
        ok ("matches('hello world', 'hello\\ sworld', 'x')", "true"),
        ok ("matches('hello world', 'hello world', 'x')", "false"),
        ok ("matches('ab', ' a\tb\n\r ', 'x')", "true"),
        ok ("matches('a b', 'a[ ]b', 'x')", "true"),
        ok ("matches('[a', '\\[ a', 'x')", "true"),
        ok ("matches('a]', 'a \\]', 'x')", "true"),
        // 'q': every character is literal.
        ok ("matches('a.b', '.', 'q')", "true"),
        ok ("matches('ab', '.', 'q')", "false"),
        ok ("matches('a(b', '(', 'q')", "true"),
        ok ("replace('a/b/c', '/', '$', 'q')", "a$b$c"),
        // 's' and 'm'.
        ok ("matches(codepoints-to-string(10), '.')", "false"),
        ok ("matches(codepoints-to-string(13), '.')", "false"),
        ok ("matches(codepoints-to-string(10), '.', 's')", "true"),
        ok ("matches(concat('a', codepoints-to-string(10), 'b'), '^b$')", "false"),
        ok ("matches(concat('a', codepoints-to-string(10), 'b'), '^b$', 'm')", "true"),
        // 'i': case-variants; \p{Lu} keeps matching upper-case letters only.
        ok ("matches('ABC', 'abc', 'i')", "true"),
        ok ("matches('a', '\\p{Lu}', 'i')", "false"),
        ok ("matches('A', '\\p{Lu}', 'i')", "true"),
        ok ("matches('ab', 'AB', 'ims')", "true"),
        err ("matches('a', 'a', 'z')", "FORX0001"),
        err ("matches('a', 'a', 'I')", "FORX0001"),
        err ("replace('a', 'a', 'b', 'g')", "FORX0001"),
        err ("tokenize('a', 'a', 'X')", "FORX0001"));
  }

  /**
   * A pattern outside the F&amp;O grammar is FORX0002 at the function level
   * (§5.6.1).
   */
  @Test
  public void testRxGrammarErrorsThroughFunctions ()
  {
    run (err ("matches('a', 'a**')", "FORX0002"),
        err ("matches('a', '[a-\\d]')", "FORX0002"),
        err ("matches('a', '(?=a)')", "FORX0002"),
        err ("matches('a', '\\b')", "FORX0002"),
        err ("matches('a', '[a-z-[b]c]')", "FORX0002"),
        err ("replace('a', '[]', 'x')", "FORX0002"),
        err ("tokenize('a', 'a{2,1}')", "FORX0002"),
        err ("matches('a', '\\p{IsBadBlockName}')", "FORX0002"),
        err ("matches('a', '\\p{Unknown}')", "FORX0002"));
  }
}
