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
package com.helger.xslt.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * Where a sequence constructor writes its result (XSLT 3.0 section 5.7). Two
 * kinds exist: a {@link SequenceOutput} keeps the items as they are (the
 * result of xsl:function, of xsl:variable with an <code>as</code> attribute,
 * ...), a {@link ContentOutput} builds the children of an element or document
 * node by the rules for constructing complex content (atomic values become
 * text, adjacent text is merged, attributes are attached, ...).
 *
 * @author Philip Helger
 */
public interface IXsltOutput
{
  /**
   * Append an existing item. Its identity is kept where items are kept as
   * they are; where content is built, a node is copied (with its namespaces).
   *
   * @param aItem
   *        The item.
   */
  void append (@NonNull IXdmItem aItem);

  /**
   * Append a node that was just constructed by an instruction: it has no
   * parent and nobody else refers to it, so a content builder may adopt it
   * without copying. The node must be complete when it is appended.
   *
   * @param aNode
   *        The new node.
   */
  void appendNew (@NonNull XdmNode aNode);

  /**
   * Append a new text node.
   *
   * @param sText
   *        The text.
   */
  default void appendText (@NonNull final String sText)
  {
    appendNew (XdmNode.createText (sText));
  }

  /**
   * Append all items of a sequence via {@link #append(IXdmItem)}.
   *
   * @param aSeq
   *        The items.
   */
  default void appendAll (@NonNull final XdmSequence aSeq)
  {
    for (final IXdmItem aItem : aSeq)
      append (aItem);
  }

  /**
   * An instruction that constructs a document node (xsl:document, xsl:copy of
   * a document node) may write the content of that node straight into the
   * output instead, when the output would only replace the document node by
   * its children anyway.
   *
   * @return An output for the content of a new document node, or
   *         <code>null</code> if a real document node must be built.
   */
  @Nullable
  default IXsltOutput getDocumentContentOutput ()
  {
    return null;
  }
}
