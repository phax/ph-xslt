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
 * What {@link XPathStreamabilityAnalyzer} assumes about the context item of an expression.
 *
 * @author Philip Helger
 */
public enum EStreamContextMode
{
  /**
   * The context item is a streamed node (striding): the rules of XSLT 3.0 section 19 for one forward pass without
   * buffering. The default.
   */
  STREAMED,
  /**
   * The context node is the root of a buffered copy of its subtree that keeps its place in the document, as after
   * <code>copy-of (.)</code> in a streamed run: the subtree can be read in any way, the ancestors and their attributes
   * only as in streaming, nothing else of the document. Gives the verdict {@link EStreamabilityVerdict#BURST} for an
   * expression that is not motionless but needs nothing outside the subtree.
   */
  BURST,
  /**
   * The context item is a buffered node with the whole tree in memory, or a value (grounded): every axis is available.
   */
  GROUNDED
}
