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
package com.helger.xsd.conformance;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;

/**
 * An <code>&lt;instanceTest&gt;</code>: validate one instance document against
 * the schema of its test group.
 *
 * @param name
 *        Test name; failing cases are reported by it.
 * @param version
 *        The version token list, empty for all versions.
 * @param document
 *        The <code>xlink:href</code> of the
 *        <code>&lt;instanceDocument&gt;</code>, relative to the test-set file.
 * @param expected
 *        The expected verdicts.
 * @param currentStatuses
 *        The <code>status</code> of every <code>&lt;current&gt;</code> element
 *        in document order.
 * @author Philip Helger
 */
public record XsdInstanceTest (@NonNull String name,
                               @NonNull String version,
                               @NonNull String document,
                               @NonNull ICommonsList <XsdExpected> expected,
                               @NonNull ICommonsList <String> currentStatuses)
{}
