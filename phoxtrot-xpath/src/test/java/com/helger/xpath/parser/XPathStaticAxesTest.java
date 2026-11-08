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
package com.helger.xpath.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;

/**
 * Test class for {@link XPathStaticAxes}.
 *
 * @author Philip Helger
 */
public final class XPathStaticAxesTest
{
  @NonNull
  private static StaticContext _ctx (final boolean bStaticTyping)
  {
    return new StaticContext ().declareNamespace ("p", "urn:p").setStaticTyping (bStaticTyping);
  }

  private static void _expectEmpty (@NonNull final String... aExprs)
  {
    for (final String sExpr : aExprs)
    {
      try
      {
        XPathParser.parse (sExpr, _ctx (true));
        fail ("Expected XPST0005 for: " + sExpr);
      }
      catch (final XPathException ex)
      {
        assertEquals (sExpr + " -> " + ex.getMessage (), "XPST0005", ex.getCode ());
      }
      // Without the feature the same expression compiles
      assertNotNull (XPathParser.parse (sExpr, _ctx (false)));
    }
  }

  private static void _expectFine (@NonNull final String... aExprs)
  {
    for (final String sExpr : aExprs)
      try
      {
        assertNotNull (sExpr, XPathParser.parse (sExpr, _ctx (true)));
      }
      catch (final XPathException ex)
      {
        fail (sExpr + " -> " + ex.getMessage ());
      }
  }

  @Test
  public void testDefaultOff ()
  {
    assertFalse (new StaticContext ().isStaticTyping ());
  }

  @Test
  public void testQT3AxisCases ()
  {
    // ST-Axes001 to ST-Axes015 and parse-xml-fragment-022-st
    _expectEmpty ("fn:count(//center/self::nowhere)",
                  "fn:count(//center/@center-attr-2/self::*)",
                  "fn:count(//center/@center-attr-3/self::center-attr-3)",
                  "fn:count(//center/@center-attr-1/self::nowhere)",
                  "fn:count(//center/text()/self::*)",
                  "fn:count(//center/text()/self::center)",
                  "fn:count(//center/@center-attr-1/descendant-or-self::*)",
                  "fn:count(//center/@center-attr-2/descendant-or-self::far-south)",
                  "fn:count(//center/@center-attr-2/descendant-or-self::center-attr-2)",
                  "fn:count(//center/text()/descendant-or-self::*)",
                  "fn:count(/attribute::*)",
                  "fn:count(/@*)",
                  "fn:count(/parent::*)",
                  "fn:count(/..)",
                  "fn:count(/self::*)",
                  "parse-xml-fragment(\"<a/>\")/..");
  }

  @Test
  public void testLeafKinds ()
  {
    _expectEmpty ("a/@b/c",
                  "a/@b/child::node()",
                  "a/@b/descendant::x",
                  "a/text()/node()",
                  "a/comment()/@x",
                  "a/processing-instruction()/x",
                  "a/@b/@c",
                  "/document-node()/self::element()",
                  "a/@b/self::b",
                  "a/@b/self::p:b");
  }

  @Test
  public void testNested ()
  {
    _expectEmpty ("for $x in 1 to 3 return a/@b/c",
                  "a[@b/c]",
                  "let $f := function () { /.. } return 1",
                  "map { 'k' : a/text()/b }",
                  "[1, /@x]",
                  "if (1) then 2 else a/@b/self::c",
                  "(1, 2) ! /self::x",
                  "x/(/@y)");
  }

  @Test
  public void testNotProvablyEmpty ()
  {
    _expectFine ("a/b/c",
                 "//center/self::center",
                 "//center/@x/self::attribute()",
                 "//center/@x/self::node()",
                 "//center/@x/self::attribute(x)",
                 "a/@b/descendant-or-self::node()",
                 "a/@b/..",
                 "a/@b/ancestor::x",
                 "a/text()/following-sibling::x",
                 "(.)/self::p:c/self::c",
                 "a/self::*:b",
                 "a/self::a/self::a",
                 "/node()",
                 "/self::node()",
                 "/descendant-or-self::x",
                 "(.)/..",
                 "./..",
                 "a/./..",
                 "parse-xml-fragment('<a/>')/a/..",
                 "parse-xml-fragment('<a/>')[1]/..",
                 "a/root()/..",
                 "1 + 2");
  }
}
