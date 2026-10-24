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

/**
 * The token kinds of the XPath 3.1 lexer.
 *
 * @author Philip Helger
 */
enum ETokenKind
{
  EOF,
  LPAREN,
  RPAREN,
  LBRACKET,
  RBRACKET,
  LBRACE,
  RBRACE,
  /** <code>.</code> */
  DOT,
  /** <code>..</code> */
  DOTDOT,
  AT,
  COMMA,
  /** <code>::</code> */
  COLONCOLON,
  /** A lone <code>:</code> (map entry separator) */
  COLON,
  /** <code>:=</code> */
  ASSIGN,
  SLASH,
  SLASHSLASH,
  PIPE,
  /** <code>||</code> */
  CONCAT,
  PLUS,
  MINUS,
  /** <code>*</code>: multiplication, wildcard or occurrence indicator, decided by the parser */
  STAR,
  EQ,
  NE,
  LT,
  LE,
  GT,
  GE,
  /** <code>&lt;&lt;</code> */
  PRECEDES,
  /** <code>&gt;&gt;</code> */
  FOLLOWS,
  DOLLAR,
  QUESTION,
  HASH,
  /** <code>!</code> */
  BANG,
  /** <code>=&gt;</code> */
  ARROW,
  INTEGER,
  DECIMAL,
  DOUBLE,
  /** A string literal; the text is the unescaped value */
  STRING,
  /** An NCName or a lexical QName <code>prefix:local</code> */
  NAME,
  /** <code>Q{uri}local</code>; the text is the local name, the URI is separate */
  BRACED_NAME,
  /** <code>prefix:*</code>; the text is the prefix */
  PREFIX_WILDCARD,
  /** <code>*:local</code>; the text is the local name */
  LOCAL_WILDCARD,
  /** <code>Q{uri}*</code>; the URI is separate */
  BRACED_WILDCARD
}
