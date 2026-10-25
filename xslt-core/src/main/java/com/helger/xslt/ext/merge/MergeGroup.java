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
package com.helger.xslt.ext.merge;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.value.XdmSequence;

/**
 * The current merge group (XSLT 3.0 section 15.5): all items of the group and
 * the items per named merge source.
 *
 * @param items
 *        All items of the group, in merge source order.
 * @param bySource
 *        The items per merge source name; every name of the xsl:merge is
 *        contained.
 * @author Philip Helger
 */
@Immutable
public record MergeGroup (@NonNull XdmSequence items, @NonNull ICommonsMap <String, XdmSequence> bySource)
{}
