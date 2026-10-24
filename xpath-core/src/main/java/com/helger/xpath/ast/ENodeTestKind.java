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
package com.helger.xpath.ast;

/**
 * Kind of a node test / KindTest.
 *
 * @author Philip Helger
 */
public enum ENodeTestKind
{
  /** A name test on the principal node kind of the axis, possibly with wildcards. */
  NAME,
  /** <code>node()</code> */
  ANY_NODE,
  /** <code>text()</code> */
  TEXT,
  /** <code>comment()</code> */
  COMMENT,
  /** <code>processing-instruction()</code> with optional target */
  PROCESSING_INSTRUCTION,
  /** <code>element()</code>, <code>element(name)</code>, <code>element(name, type)</code> */
  ELEMENT,
  /** <code>attribute()</code>, <code>attribute(name)</code>, <code>attribute(name, type)</code> */
  ATTRIBUTE,
  /** <code>document-node()</code> with optional inner element test */
  DOCUMENT,
  /** <code>namespace-node()</code> */
  NAMESPACE_NODE,
  /** <code>schema-element(name)</code>; never matches without a schema */
  SCHEMA_ELEMENT,
  /** <code>schema-attribute(name)</code>; never matches without a schema */
  SCHEMA_ATTRIBUTE
}
