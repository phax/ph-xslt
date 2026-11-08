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
package com.helger.xpath.conformance;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;

/**
 * A FOTS test-set file.
 *
 * @param name
 *        Test set name.
 * @param dir
 *        Directory of the test-set file; relative file references of test
 *        cases and assertions resolve against it.
 * @param dependencies
 *        Test-set level dependencies; they apply to every test case.
 * @param environments
 *        Test-set level named environments.
 * @param testCases
 *        The test cases in document order.
 * @author Philip Helger
 */
public record QT3TestSet (@NonNull String name,
                          @NonNull Path dir,
                          @NonNull ICommonsList <QT3Dependency> dependencies,
                          @NonNull ICommonsMap <String, QT3Environment> environments,
                          @NonNull ICommonsList <QT3TestCase> testCases)
{}
