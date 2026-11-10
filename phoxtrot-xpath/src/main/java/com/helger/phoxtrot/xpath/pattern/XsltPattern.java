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
package com.helger.phoxtrot.xpath.pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xpath.context.DynamicContext;

/**
 * A compiled XSLT 3.0 pattern (XSLT 3.0 section 5.5): a predicate pattern
 * (<code>.[...]</code>), a type pattern, or a path pattern with unions,
 * intersections and excepts. Implementations are immutable and thread-safe.
 * Owner: pattern workstream.
 *
 * @author Philip Helger
 */
public interface XsltPattern
{
  /** @return The pattern text as written. */
  @NonNull
  String getSource ();

  /**
   * @param aItem
   *        The candidate item.
   * @param aCtx
   *        Dynamic context for predicates and variable references; the
   *        current item (<code>fn:current()</code>) must be the candidate.
   * @return <code>true</code> if the item matches.
   */
  boolean matches (@NonNull IXdmItem aItem, @NonNull DynamicContext aCtx);

  /**
   * A test on the item alone, without evaluating anything: <code>false</code> means that
   * {@link #matches(IXdmItem, DynamicContext)} is <code>false</code> for the item with every
   * context. <code>true</code> decides nothing.
   *
   * @param aItem
   *        The candidate item.
   * @return <code>false</code> if the item cannot match.
   */
  default boolean canMatch (@NonNull final IXdmItem aItem)
  {
    return true;
  }

  /**
   * The names the nodes this pattern matches must have: if every alternative of the pattern (the
   * operands of top-level unions) is a path whose last step tests an exact element or attribute
   * name (no wildcard, no kind test, not the namespace axis), every matching node is an element
   * or attribute with one of these names. A parenthesised union with predicates that are never
   * read as a position counts as the union of its paths. A host may then take the candidate nodes from a name
   * index and test only those with {@link #matches(IXdmItem, DynamicContext)}.
   *
   * @return The names, or <code>null</code> if the pattern can match other nodes.
   */
  @Nullable
  default PatternFinalNames getFinalNames ()
  {
    return null;
  }

  /**
   * @return The default priority (XSLT 3.0 section 6.5). For a pattern with
   *         top-level union alternatives this is only meaningful per
   *         alternative, see {@link #getAlternatives()}.
   */
  double getDefaultPriority ();

  /**
   * @return The top-level union alternatives (a template rule with a union
   *         pattern is treated as one rule per alternative, each with its own
   *         default priority); a single-element list containing this pattern
   *         if there is no top-level union.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsList <XsltPattern> getAlternatives ();
}
