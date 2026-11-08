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
 * The usage of an operand role (XSLT 3.0 section 19.4): how the containing
 * construct uses a node supplied in that role.
 *
 * @author Philip Helger
 */
public enum EStreamUsage
{
  /**
   * The construct reads the subtree rooted at the node (atomization, string
   * value, copying).
   */
  ABSORPTION,
  /**
   * Only properties available without reading the subtree (name, existence,
   * type).
   */
  INSPECTION,
  /** The node may be passed through to the result, in document order. */
  TRANSMISSION,
  /**
   * The construct may navigate freely from the node, or the analysis cannot
   * tell what it does with it.
   */
  NAVIGATION
}
