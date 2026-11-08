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
package com.helger.phoxtrot.xpath.ast;

/**
 * Comparison operators of the three comparison families.
 *
 * @author Philip Helger
 */
public enum EComparisonOp
{
  GENERAL_EQ,
  GENERAL_NE,
  GENERAL_LT,
  GENERAL_LE,
  GENERAL_GT,
  GENERAL_GE,
  VALUE_EQ,
  VALUE_NE,
  VALUE_LT,
  VALUE_LE,
  VALUE_GT,
  VALUE_GE,
  /** <code>is</code> */
  NODE_IS,
  /** <code>&lt;&lt;</code> */
  NODE_PRECEDES,
  /** <code>&gt;&gt;</code> */
  NODE_FOLLOWS;

  /**
   * @return <code>true</code> for the general comparisons <code>=</code>,
   *         <code>!=</code>, <code>&lt;</code>, <code>&lt;=</code>,
   *         <code>&gt;</code> and <code>&gt;=</code>.
   */
  public boolean isGeneral ()
  {
    return ordinal () <= GENERAL_GE.ordinal ();
  }

  /**
   * @return <code>true</code> for the value comparisons <code>eq</code>,
   *         <code>ne</code>, <code>lt</code>, <code>le</code>, <code>gt</code>
   *         and <code>ge</code>.
   */
  public boolean isValue ()
  {
    return ordinal () >= VALUE_EQ.ordinal () && ordinal () <= VALUE_GE.ordinal ();
  }

  /**
   * @return <code>true</code> for the node comparisons <code>is</code>,
   *         <code>&lt;&lt;</code> and <code>&gt;&gt;</code>.
   */
  public boolean isNode ()
  {
    return ordinal () >= NODE_IS.ordinal ();
  }
}
