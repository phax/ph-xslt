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
package com.helger.phoxtrot.xpath;

/**
 * The kind of construct an {@link XPathStreamabilityReason} names.
 *
 * @author Philip Helger
 */
public enum EStreamabilityConstruct
{
  /** An axis step, named as <code>axis::test</code>. */
  AXIS_STEP,
  /** The root of the tree (<code>/</code>). */
  ROOT,
  /** A static function call or a named function reference, named as <code>name#arity</code>. */
  FUNCTION,
  /** A variable reference, named as <code>$name</code>. */
  VARIABLE,
  /** A predicate that depends on the position (<code>position()</code>, <code>last()</code>, a number). */
  POSITIONAL_PREDICATE,
  /** Any other expression, named by its kind (for example <code>for</code> or <code>operator +</code>). */
  EXPRESSION
}
