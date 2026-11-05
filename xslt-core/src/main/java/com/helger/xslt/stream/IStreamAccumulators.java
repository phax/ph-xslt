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
package com.helger.xslt.stream;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.xdm.IXdmNode;

/**
 * The accumulators of a streamed document, computed as the reader advances
 * (XSLT 3.0 section 18.2.8): the start-phase rules when a node is entered, the
 * end-phase rules when it is complete. Every rule of a streamable accumulator
 * is motionless, so a node's value needs nothing but the node and its
 * ancestors. The values are kept only while the node is.
 *
 * @author Philip Helger
 */
public interface IStreamAccumulators
{
  /**
   * @param aNode
   *        A node that was just read (an element without children yet, or a
   *        leaf).
   */
  void enter (@NonNull IXdmNode aNode);

  /**
   * @param aNode
   *        A node that is complete (an element after its end tag, a leaf, or
   *        the document node at the end).
   */
  void exit (@NonNull IXdmNode aNode);

  /**
   * Release the values of a node and its subtree, when it is dropped.
   *
   * @param aNode
   *        The node.
   */
  void forget (@NonNull IXdmNode aNode);
}
