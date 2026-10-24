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
package com.helger.xslt.conformance;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;

/**
 * One test-set file.
 *
 * @param name
 *        The test-set name.
 * @param file
 *        The test-set file (absolute), <code>null</code> if read from a
 *        string.
 * @param dir
 *        The directory of the test-set file; stylesheets, sources and
 *        expected-result files are relative to it.
 * @param dependencies
 *        The children of its top-level <code>&lt;dependencies&gt;</code>.
 * @param environments
 *        Named environments of the test set.
 * @param testCases
 *        The test cases in document order.
 * @author Philip Helger
 */
public record XSLT30TestSet (@NonNull String name,
                             @Nullable Path file,
                             @NonNull Path dir,
                             @NonNull ICommonsList <XSLT30Dependency> dependencies,
                             @NonNull ICommonsMap <String, XSLT30Environment> environments,
                             @NonNull ICommonsList <XSLT30TestCase> testCases)
{}
