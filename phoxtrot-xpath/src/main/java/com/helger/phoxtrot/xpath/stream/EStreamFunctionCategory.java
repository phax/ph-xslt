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
package com.helger.phoxtrot.xpath.stream;

/**
 * The streamability category of a stylesheet function (XSLT 3.0 section
 * 19.8.5): the promise its declaration (<code>xsl:function/@streamability</code>)
 * makes about what it does with the nodes supplied in its first argument.
 *
 * @author Philip Helger
 */
public enum EStreamFunctionCategory
{
  /**
   * The default. The function promises nothing, so a streamed node reaches it
   * only where the declared type of the parameter atomizes it.
   */
  UNCLASSIFIED,
  /** Reads the subtrees of the nodes of the first argument. */
  ABSORBING,
  /** Reads only properties available without advancing the stream. */
  INSPECTION,
  /** Returns a motionless-selected subset of the first argument. */
  FILTER,
  /** Returns nodes below those of the first argument, none an ancestor of another. */
  SHALLOW_DESCENT,
  /** Returns descendants, which may nest. */
  DEEP_DESCENT,
  /** Returns ancestors. */
  ASCENT
}
