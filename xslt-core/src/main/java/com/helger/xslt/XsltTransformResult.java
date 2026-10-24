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
package com.helger.xslt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;

/**
 * The outcome of a transformation: the principal result in raw form, as a
 * tree and serialised, plus secondary results and messages.
 *
 * @param principalRaw
 *        The raw principal result sequence (before sequence normalisation).
 * @param principalTree
 *        The principal result tree (document node), or <code>null</code> when
 *        the raw result is not a tree (e.g. an initial function returning
 *        atomic values).
 * @param principalSerialized
 *        The principal result serialised with the unnamed xsl:output
 *        declaration.
 * @param secondaryResults
 *        Results of xsl:result-document.
 * @param messages
 *        The text of all xsl:message instructions in execution order.
 * @author Philip Helger
 */
@Immutable
public record XsltTransformResult (@NonNull XdmSequence principalRaw,
                                   @Nullable XdmNode principalTree,
                                   @NonNull String principalSerialized,
                                   @NonNull ICommonsList <SecondaryResult> secondaryResults,
                                   @NonNull ICommonsList <String> messages)
{
  /** One xsl:result-document result. */
  public record SecondaryResult (@NonNull String href,
                                 @NonNull XdmSequence raw,
                                 @Nullable XdmNode tree,
                                 @NonNull String serialized)
  {}
}
