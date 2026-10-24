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
package com.helger.xpath.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;

/**
 * Test class for the XML and XSD version settings of class
 * {@link StaticContext}.
 *
 * @author Philip Helger
 */
public final class StaticContextTest
{
  private static String _eval (final StaticContext aStatic, final String sExpr)
  {
    final XdmSequence aRes = XPathCompiler.compile (sExpr, aStatic).evaluate (new DynamicContext (aStatic));
    return ((XdmAtomic) aRes.getFirst ()).getLexical ();
  }

  private static void _assertError (final StaticContext aStatic, final String sExpr, final String sCode)
  {
    try
    {
      _eval (aStatic, sExpr);
      fail (sExpr + " should fail with " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (sExpr, sCode, ex.getCode ());
    }
  }

  @Test
  public void testDefaults ()
  {
    final StaticContext aStatic = new StaticContext ();
    assertEquals (EXmlVersion.V10, aStatic.getXmlVersion ());
    assertEquals (EXsdVersion.V10, aStatic.getXsdVersion ());
    assertEquals (EXsdVersion.V11, EXsdVersion.getFromIDOrNull ("1.1"));
    assertEquals (null, EXmlVersion.getFromIDOrNull ("1.0:4-"));
  }

  @Test
  public void testPlusInf ()
  {
    final StaticContext aXsd10 = new StaticContext ().setXsdVersion (EXsdVersion.V10);
    _assertError (aXsd10, "xs:double('+INF')", "FORG0001");
    _assertError (aXsd10, "xs:float('+INF')", "FORG0001");
    assertEquals ("false", _eval (aXsd10, "'+INF' castable as xs:double"));
    assertEquals ("INF", _eval (aXsd10, "string(xs:double('INF'))"));

    final StaticContext aXsd11 = new StaticContext ().setXsdVersion (EXsdVersion.V11);
    assertEquals ("INF", _eval (aXsd11, "string(xs:double('+INF'))"));
    assertEquals ("INF", _eval (aXsd11, "string(xs:float(' +INF '))"));
  }

  @Test
  public void testYearZero ()
  {
    final StaticContext aXsd10 = new StaticContext ().setXsdVersion (EXsdVersion.V10);
    _assertError (aXsd10, "'0000' cast as xs:gYear", "FORG0001");
    _assertError (aXsd10, "'-0000' cast as xs:gYear", "FORG0001");
    _assertError (aXsd10, "'0000-05' cast as xs:gYearMonth", "FORG0001");
    assertEquals ("0001", _eval (aXsd10, "string(xs:gYear('0001'))"));
    assertEquals ("-0001-05", _eval (aXsd10, "string(xs:gYearMonth('-0001-05'))"));

    final StaticContext aXsd11 = new StaticContext ().setXsdVersion (EXsdVersion.V11);
    assertEquals ("0000", _eval (aXsd11, "string(xs:gYear('0000'))"));
    assertEquals ("true", _eval (aXsd11, "xs:gYear('-0000') eq xs:gYear('0000')"));
    assertEquals ("0000-05", _eval (aXsd11, "string(xs:gYearMonth('0000-05'))"));
  }

  @Test
  public void testRegexCharClasses ()
  {
    final StaticContext aXsd10 = new StaticContext ().setXsdVersion (EXsdVersion.V10);
    _assertError (aXsd10, "matches('a-x', '^[a-a-x-x]+$')", "FORX0002");
    _assertError (aXsd10, "replace('a-x', '[a-a-x-x]', '')", "FORX0002");
    _assertError (aXsd10, "tokenize('a-x', '[a-a-x-x]')", "FORX0002");

    final StaticContext aXsd11 = new StaticContext ().setXsdVersion (EXsdVersion.V11);
    assertEquals ("true", _eval (aXsd11, "matches('a-x', '^[a-a-x-x]+$')"));
    assertEquals ("", _eval (aXsd11, "replace('a-x', '[a-a-x-x]', '')"));
  }

  @Test
  public void testCodepointsToString ()
  {
    final StaticContext aXml10 = new StaticContext ().setXmlVersion (EXmlVersion.V10);
    _assertError (aXml10, "codepoints-to-string(12)", "FOCH0001");
    _assertError (aXml10, "codepoints-to-string(0)", "FOCH0001");
    assertEquals ("9", _eval (aXml10, "string(string-to-codepoints(codepoints-to-string(9)))"));

    final StaticContext aXml11 = new StaticContext ().setXmlVersion (EXmlVersion.V11);
    assertEquals ("12", _eval (aXml11, "string(string-to-codepoints(codepoints-to-string(12)))"));
    assertEquals ("1", _eval (aXml11, "string(string-to-codepoints(codepoints-to-string(1)))"));
    _assertError (aXml11, "codepoints-to-string(0)", "FOCH0001");
    _assertError (aXml11, "codepoints-to-string(55296)", "FOCH0001");
  }
}
