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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;

/**
 * Test class for class {@link FnString}.
 *
 * @author Philip Helger
 */
public final class FnStringTest
{
  @NonNull
  private static XdmSequence _eval (@NonNull final String sExpr, @NonNull final StaticContext aStatic)
  {
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, aStatic);
    return aCompiled.evaluate (aCompiled.createDynamicContext ());
  }

  /** @return The string values of the result items, joined with "|" */
  @NonNull
  static String eval (@NonNull final String sExpr)
  {
    final StringBuilder aSB = new StringBuilder ();
    boolean bFirst = true;
    for (final IXdmItem aItem : _eval (sExpr, new StaticContext ()))
    {
      if (bFirst)
        bFirst = false;
      else
        aSB.append ('|');
      aSB.append (Atomizer.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  static void assertError (@NonNull final String sCode, @NonNull final String sExpr)
  {
    try
    {
      _eval (sExpr, new StaticContext ());
      fail ("Expected " + sCode + " for " + sExpr);
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr, sCode, ex.getCode ());
    }
  }

  @Test
  public void testCodepoints ()
  {
    assertEquals ("Hello", eval ("codepoints-to-string((72, 101, 108, 108, 111))"));
    assertEquals ("", eval ("codepoints-to-string(())"));
    assertEquals ("𐀀", eval ("codepoints-to-string(65536)"));
    assertEquals ("97|65536|98", eval ("string-to-codepoints('a' || codepoints-to-string(65536) || 'b')"));
    assertEquals ("", eval ("string-to-codepoints('')"));
    assertError ("FOCH0001", "codepoints-to-string(0)");
    assertError ("FOCH0001", "codepoints-to-string(55296)");
    assertError ("FOCH0001", "codepoints-to-string(1114112)");
  }

  @Test
  public void testCompare ()
  {
    assertEquals ("-1", eval ("compare('abc', 'abd')"));
    assertEquals ("0", eval ("compare('abc', 'abc')"));
    assertEquals ("1", eval ("compare('b', 'a')"));
    assertEquals ("", eval ("compare((), 'a')"));
    // Code point order, not UTF-16 order
    assertEquals ("1", eval ("compare(codepoints-to-string(65536), codepoints-to-string(65000))"));
    assertEquals ("0",
                  eval ("compare('ABC', 'abc', 'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive')"));
    assertEquals ("true", eval ("codepoint-equal('a', 'a')"));
    assertEquals ("", eval ("codepoint-equal('a', ())"));
    assertError ("FOCH0002", "compare('a', 'b', 'http://example.org/unknown')");
  }

  @Test
  public void testConcatJoin ()
  {
    assertEquals ("ab1true", eval ("concat('a', 'b', 1, true())"));
    assertEquals ("a", eval ("concat('a', ())"));
    assertEquals ("a-b-c", eval ("string-join(('a', 'b', 'c'), '-')"));
    assertEquals ("abc", eval ("string-join(('a', 'b', 'c'))"));
    assertEquals ("1 2", eval ("string-join((1, 2), ' ')"));
    assertEquals ("", eval ("string-join((), '-')"));
  }

  @Test
  public void testSubstring ()
  {
    assertEquals (" car", eval ("substring('motor car', 6)"));
    assertEquals ("ada", eval ("substring('metadata', 4, 3)"));
    assertEquals ("234", eval ("substring('12345', 1.5, 2.6)"));
    assertEquals ("12", eval ("substring('12345', 0, 3)"));
    assertEquals ("", eval ("substring('12345', 5, -3)"));
    assertEquals ("1", eval ("substring('12345', -3, 5)"));
    assertEquals ("", eval ("substring('12345', 0 div 0e0, 3)"));
    assertEquals ("", eval ("substring('12345', 1, 0 div 0e0)"));
    assertEquals ("12345", eval ("substring('12345', -42, 1 div 0e0)"));
    assertEquals ("", eval ("substring('12345', -1 div 0e0, 1 div 0e0)"));
    assertEquals ("", eval ("substring((), 1, 3)"));
    // Positions count code points
    assertEquals ("b", eval ("substring('a' || codepoints-to-string(65536) || 'b', 3)"));
  }

  @Test
  public void testLengthAndSpace ()
  {
    assertEquals ("3", eval ("string-length('a' || codepoints-to-string(65536) || 'b')"));
    assertEquals ("0", eval ("string-length(())"));
    assertEquals ("a b c", eval ("normalize-space('  a \t\n b   c ')"));
    assertEquals ("", eval ("normalize-space(())"));
    assertEquals (" a", eval ("normalize-space(' a ')"));
    assertError ("XPDY0002", "string-length()");
  }

  @Test
  public void testNormalizeUnicode ()
  {
    assertEquals ("é", eval ("normalize-unicode('é')"));
    assertEquals ("é", eval ("normalize-unicode('é', 'NFD')"));
    assertEquals ("é", eval ("normalize-unicode('é', ' nfd ')"));
    assertEquals ("fi", eval ("normalize-unicode('ﬁ', 'NFKC')"));
    assertEquals ("é", eval ("normalize-unicode('é', '')"));
    assertEquals (" ́", eval ("normalize-unicode('́', 'FULLY-NORMALIZED')"));
    assertEquals (" া", eval ("normalize-unicode('া', 'FULLY-NORMALIZED')"));
    assertEquals ("blah", eval ("normalize-unicode('blah', 'FULLY-NORMALIZED')"));
    assertError ("FOCH0003", "normalize-unicode('a', 'NFX')");
  }

  @Test
  public void testCase ()
  {
    assertEquals ("ABC!D", eval ("upper-case('abC!d')"));
    assertEquals ("abc!d", eval ("lower-case('ABc!D')"));
    assertEquals ("STRASSE", eval ("upper-case('straße')"));
    assertEquals ("i̇", eval ("lower-case('İ')"));
    // No final sigma rule
    assertEquals ("σσ", eval ("lower-case('ΣΣ')"));
    assertEquals ("", eval ("upper-case(())"));
  }

  @Test
  public void testTranslate ()
  {
    assertEquals ("BAr", eval ("translate('bar', 'abc', 'ABC')"));
    assertEquals ("AAA", eval ("translate('--aaa--', 'abc-', 'ABC')"));
    assertEquals ("ABdAB", eval ("translate('abcdabc', 'abc', 'AB')"));
    assertEquals ("x", eval ("translate(codepoints-to-string(65536), codepoints-to-string(65536), 'x')"));
  }

  @Test
  public void testSubstringMatching ()
  {
    assertEquals ("true", eval ("contains('tattoo', 't')"));
    assertEquals ("false", eval ("contains('tattoo', 'ttt')"));
    assertEquals ("true", eval ("contains('', ())"));
    assertEquals ("true", eval ("starts-with('tattoo', 'tat')"));
    assertEquals ("true", eval ("ends-with('tattoo', 'too')"));
    assertEquals ("t", eval ("substring-before('tattoo', 'attoo')"));
    assertEquals ("", eval ("substring-before('tattoo', 'xyz')"));
    assertEquals ("too", eval ("substring-after('tattoo', 'tat')"));
    assertEquals ("tattoo", eval ("substring-after('tattoo', '')"));
    final String sHtml = "'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive'";
    assertEquals ("true", eval ("contains('ABC', 'b', " + sHtml + ")"));
    assertEquals ("false", eval ("contains('Á', 'á', " + sHtml + ")"));
    assertEquals ("C", eval ("substring-after('ABC', 'b', " + sHtml + ")"));
    // Relative collation URI resolved against the static base URI
    final StaticContext aStatic = new StaticContext ().setBaseUri ("http://www.w3.org/2005/xpath-functions/");
    assertEquals ("a",
                  Atomizer.getStringValue (_eval ("substring-before('abc', 'b', 'collation/codepoint')", aStatic).get (0)));
  }

  @Test
  public void testUcaSubstringMatching ()
  {
    final String sShifted = "'http://www.w3.org/2013/collation/UCA?alternate=shifted'";
    assertEquals ("true", eval ("contains('abcdefghi', '-d-e-f-', " + sShifted + ")"));
    assertEquals ("abc--", eval ("substring-before('abc--d-e-fghi', '--d-e-', " + sShifted + ")"));
    final String sPrimary = "'http://www.w3.org/2013/collation/UCA?strength=primary'";
    assertEquals ("true", eval ("starts-with('École', 'ec', " + sPrimary + ")"));
    assertEquals ("true", eval ("ends-with('café', 'E', " + sPrimary + ")"));
  }

  @Test
  public void testContainsToken ()
  {
    assertEquals ("true", eval ("contains-token('red green blue ', 'red')"));
    assertEquals ("true", eval ("contains-token(('red', 'green', 'blue'), ' red ')"));
    assertEquals ("false", eval ("contains-token('red, green, blue', 'red')"));
    assertEquals ("false", eval ("contains-token('red green', '')"));
    assertEquals ("false", eval ("contains-token('red green', 'red')"));
    assertEquals ("true",
                  eval ("contains-token('red green blue', 'RED', 'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive')"));
  }

  @Test
  public void testCollationKey ()
  {
    final String sHtml = "'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive'";
    assertEquals ("true", eval ("collation-key('ABC', " + sHtml + ") eq collation-key('abc', " + sHtml + ")"));
    assertEquals ("false", eval ("collation-key('ABC') eq collation-key('abc')"));
    final String sPrimary = "'http://www.w3.org/2013/collation/UCA?strength=primary'";
    assertEquals ("true", eval ("collation-key('a', " + sPrimary + ") eq collation-key('A', " + sPrimary + ")"));
    assertEquals ("false",
                  eval ("collation-key('abc123', " + sPrimary + ") eq collation-key('ABC 123', " + sPrimary + ")"));
    assertError ("FOCH0002", "collation-key('a', 'http://example.org/unknown')");
  }
}
