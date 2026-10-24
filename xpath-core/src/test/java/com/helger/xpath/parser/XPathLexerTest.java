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
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Test class for {@link XPathLexer}.
 *
 * @author Philip Helger
 */
public final class XPathLexerTest
{
  @NonNull
  private static String _kinds (@NonNull final String s)
  {
    final List <XPathToken> aTokens = XPathLexer.tokenize (s);
    final StringBuilder aSB = new StringBuilder ();
    for (final XPathToken t : aTokens)
    {
      if (t.kind () == ETokenKind.EOF)
        break;
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (t.kind ().name ());
      if (t.kind () == ETokenKind.NAME ||
          t.kind () == ETokenKind.STRING ||
          t.kind () == ETokenKind.INTEGER ||
          t.kind () == ETokenKind.DECIMAL ||
          t.kind () == ETokenKind.DOUBLE ||
          t.kind () == ETokenKind.PREFIX_WILDCARD ||
          t.kind () == ETokenKind.LOCAL_WILDCARD)
        aSB.append ('(').append (t.text ()).append (')');
      if (t.kind () == ETokenKind.BRACED_NAME || t.kind () == ETokenKind.BRACED_WILDCARD)
        aSB.append ("({").append (t.uri ()).append ('}').append (t.text ()).append (')');
    }
    return aSB.toString ();
  }

  @Test
  public void testOperators ()
  {
    assertEquals ("LPAREN RPAREN LBRACKET RBRACKET LBRACE RBRACE", _kinds ("()[]{}"));
    assertEquals ("DOT DOTDOT AT COMMA COLONCOLON COLON ASSIGN", _kinds (". .. @ , :: : :="));
    assertEquals ("SLASH SLASHSLASH PIPE CONCAT PLUS MINUS STAR", _kinds ("/ // | || + - *"));
    assertEquals ("EQ NE LT LE GT GE PRECEDES FOLLOWS", _kinds ("= != < <= > >= << >>"));
    assertEquals ("DOLLAR QUESTION HASH BANG ARROW", _kinds ("$ ? # ! =>"));
    assertEquals ("NAME(a) SLASHSLASH NAME(b)", _kinds ("a//b"));
    assertEquals ("NAME(a) BANG EQ NAME(b)", _kinds ("a ! = b"));
  }

  @Test
  public void testNames ()
  {
    assertEquals ("NAME(a-b.c)", _kinds ("a-b.c"));
    assertEquals ("NAME(p:local)", _kinds ("p:local"));
    assertEquals ("NAME(p) COLON NAME(local)", _kinds ("p : local"));
    assertEquals ("NAME(child) COLONCOLON NAME(a)", _kinds ("child::a"));
    assertEquals ("DOLLAR NAME(x) ASSIGN INTEGER(1)", _kinds ("$x:=1"));
    assertEquals ("NAME(a) COLON INTEGER(1)", _kinds ("a:1"));
    assertEquals ("PREFIX_WILDCARD(p)", _kinds ("p:*"));
    assertEquals ("LOCAL_WILDCARD(a)", _kinds ("*:a"));
    assertEquals ("STAR COLON STAR", _kinds ("*:*"));
    assertEquals ("BRACED_NAME({urn:x}a)", _kinds ("Q{urn:x}a"));
    assertEquals ("BRACED_NAME({}a)", _kinds ("Q{}a"));
    assertEquals ("BRACED_NAME({urn:x y}a)", _kinds ("Q{ urn:x \n y }a"));
    assertEquals ("BRACED_WILDCARD({urn:x}*)", _kinds ("Q{urn:x}*"));
    assertEquals ("NAME(Q)", _kinds ("Q"));
    assertEquals ("NAME(élève)", _kinds ("élève"));
    assertEquals ("NAME(a·b)", _kinds ("a·b"));
    // Supplementary characters
    assertEquals ("NAME(𐀀x)", _kinds ("𐀀x"));
    assertTrue (XPathLexer.isNCName ("a.b-c_d"));
    assertFalse (XPathLexer.isNCName ("1a"));
    assertFalse (XPathLexer.isNCName ("a:b"));
    assertFalse (XPathLexer.isNCName (""));
    assertFalse (XPathLexer.isNCNameStartChar (0xB5));
  }

  @Test
  public void testNumbers ()
  {
    assertEquals ("INTEGER(12)", _kinds ("12"));
    assertEquals ("DECIMAL(1.5)", _kinds ("1.5"));
    assertEquals ("DECIMAL(.5)", _kinds (".5"));
    assertEquals ("DECIMAL(5.)", _kinds ("5."));
    assertEquals ("DOUBLE(1e5)", _kinds ("1e5"));
    assertEquals ("DOUBLE(1.5E-3)", _kinds ("1.5E-3"));
    assertEquals ("DOUBLE(.5e+2)", _kinds (".5e+2"));
    assertEquals ("INTEGER(1) PLUS INTEGER(2)", _kinds ("1+2"));
    assertEquals ("INTEGER(1) MINUS INTEGER(2)", _kinds ("1-2"));
    assertEquals ("DOTDOT INTEGER(5)", _kinds ("..5"));
  }

  @Test
  public void testStringsAndComments ()
  {
    assertEquals ("STRING(a'b)", _kinds ("'a''b'"));
    assertEquals ("STRING(a\"b)", _kinds ("\"a\"\"b\""));
    assertEquals ("STRING(a\"b)", _kinds ("'a\"b'"));
    assertEquals ("STRING((: no comment :))", _kinds ("'(: no comment :)'"));
    assertEquals ("INTEGER(1) INTEGER(2)", _kinds ("1(: a (: b :) c :)2"));
    assertEquals ("", _kinds ("(::)"));
    assertEquals ("", _kinds (" \t\r\n"));
  }
}
