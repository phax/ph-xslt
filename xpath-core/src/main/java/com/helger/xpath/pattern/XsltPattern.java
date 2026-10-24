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
package com.helger.xpath.pattern;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.xdm.IXdmItem;

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
