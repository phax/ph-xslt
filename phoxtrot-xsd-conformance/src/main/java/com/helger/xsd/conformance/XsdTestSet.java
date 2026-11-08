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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;

/**
 * A <code>.testSet</code> file of the suite.
 *
 * @param name
 *        Test set name.
 * @param version
 *        The version token list, empty for all versions.
 * @param dir
 *        Directory of the test-set file; document references resolve against
 *        it.
 * @param groups
 *        The test groups in document order.
 * @author Philip Helger
 */
public record XsdTestSet (@NonNull String name,
                          @NonNull String version,
                          @NonNull Path dir,
                          @NonNull ICommonsList <XsdTestGroup> groups)
{}
