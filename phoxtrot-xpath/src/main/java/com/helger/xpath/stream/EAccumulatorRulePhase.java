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
package com.helger.xpath.stream;

/**
 * Which phase of an <code>xsl:accumulator-rule</code> a construct belongs to,
 * or that it belongs to none (XSLT 3.0 section 19.8.9.1).
 *
 * @author Philip Helger
 */
public enum EAccumulatorRulePhase
{
  /** The construct is not inside an <code>xsl:accumulator-rule</code>. */
  NONE,
  /**
   * A pre-descent rule (<code>phase="start"</code>), where no post-descent
   * accumulator value for the node can be available yet.
   */
  START,
  /** A post-descent rule (<code>phase="end"</code>), where they all are. */
  END
}
