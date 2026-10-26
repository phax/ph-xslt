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
package com.helger.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The lexical helpers of the loader.
 *
 * @author Philip Helger
 */
public final class XsdLexHelperTest
{
  @Test
  public void testPermissiveNCName ()
  {
    assertTrue (XsdLexHelper.permissiveNCName ("a"));
    assertTrue (XsdLexHelper.permissiveNCName ("_a.b-c1"));
    // Non-ASCII stays permissive, letter numbers start a name
    assertTrue (XsdLexHelper.permissiveNCName ("été"));
    assertTrue (XsdLexHelper.permissiveNCName ("Ⅰx"));
    assertFalse (XsdLexHelper.permissiveNCName (""));
    assertFalse (XsdLexHelper.permissiveNCName ("1a"));
    assertFalse (XsdLexHelper.permissiveNCName ("-a"));
    assertFalse (XsdLexHelper.permissiveNCName ("a:b"));
    assertFalse (XsdLexHelper.permissiveNCName ("a b"));
    assertFalse (XsdLexHelper.permissiveNCName ("'"));
  }

  @Test
  public void testQNameLexical ()
  {
    assertTrue (XsdLexHelper.validQNameLexical ("a"));
    assertTrue (XsdLexHelper.validQNameLexical ("p:a"));
    assertFalse (XsdLexHelper.validQNameLexical ("xmlns:a"));
    assertFalse (XsdLexHelper.validQNameLexical (":a"));
    assertFalse (XsdLexHelper.validQNameLexical ("a:"));
    assertFalse (XsdLexHelper.validQNameLexical ("a:b:c"));
  }

  @Test
  public void testAnyURI10 ()
  {
    assertTrue (XsdLexHelper.validAnyURI10 ("http://example.org/a?b#c"));
    assertTrue (XsdLexHelper.validAnyURI10 ("rel/path"));
    assertTrue (XsdLexHelper.validAnyURI10 ("%20"));
    assertTrue (XsdLexHelper.validAnyURI10 ("é"));
    assertFalse (XsdLexHelper.validAnyURI10 ("a b"));
    assertFalse (XsdLexHelper.validAnyURI10 ("%2"));
    assertFalse (XsdLexHelper.validAnyURI10 (":a"));
    assertFalse (XsdLexHelper.validAnyURI10 ("b:"));
    assertFalse (XsdLexHelper.validAnyURI10 ("1a:b"));
  }

  @Test
  public void testNumbers ()
  {
    assertEquals (5, XsdLexHelper.nonNegInt (" 5 "));
    assertEquals (5, XsdLexHelper.nonNegInt ("+5"));
    assertEquals (-1, XsdLexHelper.nonNegInt ("-1"));
    assertEquals (-1, XsdLexHelper.nonNegInt ("x"));
    assertEquals (-1, XsdLexHelper.nonNegInt (""));
    assertEquals (7, XsdLexHelper.atoiOr ("7"));
    assertEquals (0, XsdLexHelper.atoiOr ("x"));
    assertEquals (Integer.MAX_VALUE, XsdLexHelper.atoiOr ("99999999999999999999"));
  }

  @Test
  public void testXmlFields ()
  {
    assertEquals ("[a, b, c]", XsdLexHelper.xmlFields ("  a\tb\n\r c ").toString ());
    assertEquals ("[]", XsdLexHelper.xmlFields (" \t ").toString ());
    assertTrue (XsdLexHelper.lexEqualCollapsed (" a  b ", "a b"));
  }
}
