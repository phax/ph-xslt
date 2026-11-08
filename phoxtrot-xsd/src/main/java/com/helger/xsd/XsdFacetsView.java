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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * The effective constraining facets of a simple type (go-xslt
 * <code>Facets</code>): each one as declared by the nearest restriction step
 * that sets it. <code>null</code> means the facet is not set. Bounds are
 * lexical strings in the type's own lexical space.
 *
 * @param length
 *        length
 * @param minLength
 *        minLength
 * @param maxLength
 *        maxLength
 * @param minInclusive
 *        minInclusive
 * @param maxInclusive
 *        maxInclusive
 * @param minExclusive
 *        minExclusive
 * @param maxExclusive
 *        maxExclusive
 * @param totalDigits
 *        totalDigits
 * @param fractionDigits
 *        fractionDigits
 * @param whiteSpace
 *        <code>preserve</code>, <code>replace</code> or <code>collapse</code>
 *        - always set.
 * @param patterns
 *        One entry per restriction step that declares patterns, nearest first:
 *        a value must match every entry, and within an entry one of its
 *        alternatives. Needs documentation kept at compile time.
 * @param explicitTimezone
 *        XSD 1.1 <code>required</code>, <code>prohibited</code> or
 *        <code>optional</code>; empty when unset.
 * @author Philip Helger
 */
public record XsdFacetsView (@Nullable Integer length,
                             @Nullable Integer minLength,
                             @Nullable Integer maxLength,
                             @Nullable String minInclusive,
                             @Nullable String maxInclusive,
                             @Nullable String minExclusive,
                             @Nullable String maxExclusive,
                             @Nullable Integer totalDigits,
                             @Nullable Integer fractionDigits,
                             @NonNull String whiteSpace,
                             @NonNull ICommonsList <ICommonsList <String>> patterns,
                             @NonNull String explicitTimezone)
{}
