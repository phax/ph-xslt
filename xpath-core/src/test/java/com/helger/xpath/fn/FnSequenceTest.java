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

import static com.helger.xpath.fn.FnTestHelper.assertLex;
import static com.helger.xpath.fn.FnTestHelper.assertTyped;
import static com.helger.xpath.fn.FnTestHelper.assertXPathError;
import static com.helger.xpath.fn.FnTestHelper.eval;
import static com.helger.xpath.fn.FnTestHelper.lex;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.xpath.eval.ExprTestHelper;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Test class for class {@link FnSequence}.
 *
 * @author Philip Helger
 */
public final class FnSequenceTest
{
  private static final String DOC = "<!DOCTYPE r [<!ATTLIST e id ID #IMPLIED ref IDREF #IMPLIED refs IDREFS #IMPLIED>]>" +
                                    "<r xml:lang='en-GB'>" +
                                    "<e id='a' refs='b c'><e id='b' ref='a'/></e>" +
                                    "<f xml:id='c' xml:lang='de'><g/></f>" +
                                    "<e id=' d ' ref='c'/>" +
                                    "</r>";

  @Test
  public void testBasics ()
  {
    assertLex ("true", "empty(())");
    assertLex ("false", "empty((1, 2))");
    assertLex ("true", "exists(0)");
    assertLex ("1", "head((1, 2, 3))");
    assertLex ("", "head(())");
    assertLex ("2 3", "tail((1, 2, 3))");
    assertLex ("", "tail(1)");
    assertLex ("3 2 1", "reverse(1 to 3)");
    assertLex ("1 2 3", "unordered(1 to 3)");
  }

  @Test
  public void testInsertBeforeRemove ()
  {
    assertLex ("z a b c", "insert-before(('a', 'b', 'c'), 0, 'z')");
    assertLex ("a z b c", "insert-before(('a', 'b', 'c'), 2, 'z')");
    assertLex ("a b c z", "insert-before(('a', 'b', 'c'), 99999999999999999999, 'z')");
    assertLex ("a b c", "insert-before(('a', 'b', 'c'), 2, ())");
    assertLex ("a c", "remove(('a', 'b', 'c'), 2)");
    assertLex ("a b c", "remove(('a', 'b', 'c'), 0)");
    assertLex ("a b c", "remove(('a', 'b', 'c'), -99999999999999999999)");
    assertXPathError ("XPTY0004", "remove((1, 2), 1.0)");
    assertXPathError ("XPTY0004", "insert-before((1, 2), (), 3)");
  }

  @Test
  public void testSubsequence ()
  {
    assertLex ("3 4 5", "subsequence(1 to 5, 3)");
    assertLex ("3 4", "subsequence(1 to 5, 3, 2)");
    assertLex ("1 2", "subsequence(1 to 5, 0, 3)");
    assertLex ("2 3", "subsequence(1 to 5, 1.5, 2.4)");
    assertLex ("", "subsequence(1 to 5, xs:double('NaN'))");
    assertLex ("", "subsequence(1 to 5, xs:double('-INF'), xs:double('INF'))");
    assertLex ("1 2 3 4 5", "subsequence(1 to 5, -3, xs:double('INF'))");
    assertLex ("99 100", "subsequence(1 to 100, 99, 2147483648)");
  }

  @Test
  public void testDistinctValues ()
  {
    assertLex ("1 2 3", "distinct-values((1, 2.0, 1e0, 3, 2))");
    assertLex ("NaN", "distinct-values((xs:double('NaN'), xs:float('NaN')))");
    assertLex ("NaN NaN", "distinct-values((xs:float('NaN'), 'NaN'))");
    assertLex ("a A b", "distinct-values(('a', 'A', xs:untypedAtomic('a'), xs:anyURI('b'), 'b'))");
    assertLex ("0", "distinct-values((0e0, -0e0))");
    assertLex ("1.2", "distinct-values((1.2, xs:float('1.2')))");
    assertLex ("true 1", "distinct-values((true(), 1, true()))");
    assertLex ("1.2", "distinct-values((1.2, 1.2e0))");
    assertLex ("10000", "count(distinct-values((1 to 10000, 1 to 10000)))");
    assertXPathError ("FOCH0002", "distinct-values(('a'), 'http://example.org/nope')");
  }

  @Test
  public void testIndexOf ()
  {
    assertLex ("2 4", "index-of((10, 20, 30, 20), 20)");
    assertLex ("", "index-of((10, 20, 30), 'x')");
    assertLex ("", "index-of(xs:double('NaN'), xs:double('NaN'))");
    assertLex ("1 2", "index-of(('a', xs:untypedAtomic('a')), 'a')");
    assertLex ("1", "index-of(1.0, 1)");
  }

  @Test
  public void testDeepEqualAndCardinality ()
  {
    assertLex ("true", "deep-equal((1, 'a'), (1.0, 'a'))");
    assertLex ("false", "deep-equal((1, 2), (2, 1))");
    assertLex ("true", "deep-equal(xs:double('NaN'), xs:float('NaN'))");
    assertXPathError ("FOTY0015", "deep-equal(abs#1, abs#1)");
    assertLex ("", "zero-or-one(())");
    assertXPathError ("FORG0003", "zero-or-one((1, 2))");
    assertLex ("1 2", "one-or-more((1, 2))");
    assertXPathError ("FORG0004", "one-or-more(())");
    assertLex ("1", "exactly-one(1)");
    assertXPathError ("FORG0005", "exactly-one(())");
    assertXPathError ("FORG0005", "exactly-one((1, 2))");
  }

  @Test
  public void testIDFunctions ()
  {
    final IXdmNode aDoc = ExprTestHelper.parse (DOC);
    assertEquals ("e e", lex (eval ("id(('a', 'b'))", aDoc)));
    assertEquals ("e f", lex (eval ("id('c a')", aDoc)));
    assertEquals ("e", lex (eval ("id('d')", aDoc)));
    assertEquals ("", lex (eval ("id('x')", aDoc)));
    assertEquals ("e", lex (eval ("element-with-id('b')", aDoc)));
    assertEquals ("ref", lex (eval ("idref('a')", aDoc)));
    assertEquals ("refs ref", lex (eval ("idref('c')", aDoc)));
    assertEquals ("refs ref", lex (eval ("idref(' c ')", aDoc)));
    assertEquals ("", lex (eval ("idref('b c')", aDoc)));
    assertEquals ("e", lex (eval ("id('a', //g)", aDoc)));
    FnTestHelper.assertXPathError ("XPDY0002", "id('a')");
    FnTestHelper.assertXPathError ("XPTY0004", "id('a')", XdmAtomic.integer (1));
    final IXdmNode aElement = ExprTestHelper.parse ("<a id='x'/>").getChildAtIndex (0);
    final IXdmNode aOrphan = IXdmNode.createElement (aElement.getName ());
    FnTestHelper.assertXPathError ("FODC0001", "id('x')", aOrphan);
    FnTestHelper.assertXPathError ("FODC0001", "idref('x', .)", aOrphan);
  }

  @Test
  public void testLang ()
  {
    final IXdmNode aDoc = ExprTestHelper.parse (DOC);
    assertEquals ("true", lex (eval ("lang('en', (//e)[1])", aDoc)));
    assertEquals ("true", lex (eval ("lang('EN-gb', (//e)[1])", aDoc)));
    assertEquals ("false", lex (eval ("lang('en-US', (//e)[1])", aDoc)));
    assertEquals ("true", lex (eval ("lang('de', //g)", aDoc)));
    assertEquals ("true", lex (eval ("//g/lang('de')", aDoc)));
    assertEquals ("false", lex (eval ("lang('en', .)", aDoc)));
    assertEquals ("false", lex (eval ("lang((), //g)", aDoc)));
    FnTestHelper.assertXPathError ("XPTY0004", "lang('en')", XdmAtomic.integer (1));
  }

  @Test
  public void testInnermostOutermost ()
  {
    final IXdmNode aDoc = ExprTestHelper.parse (DOC);
    assertEquals ("e g e", lex (eval ("innermost(//*)", aDoc)));
    assertEquals ("r", lex (eval ("outermost(//*)", aDoc)));
    assertEquals ("e f e", lex (eval ("outermost(//*[not(self::r)])", aDoc)));
    assertEquals ("id refs id ref id ref", lex (eval ("innermost((//e, //e/@*))", aDoc)));
    assertTyped ("", "innermost(())");
    assertXPathError ("XPTY0004", "innermost(1)");
  }
}
