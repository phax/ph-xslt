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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * A FOTS <code>&lt;test-case&gt;</code>.
 *
 * @param name
 *        Test case name.
 * @param dependencies
 *        The test case's own dependencies (not those of the test set).
 * @param environment
 *        The first <code>&lt;environment&gt;</code> (reference or inline);
 *        <code>null</code> if none, which means the empty environment.
 * @param testExpr
 *        The text of <code>&lt;test&gt;</code>.
 * @param testFile
 *        The <code>file</code> attribute of <code>&lt;test&gt;</code>, empty
 *        if absent.
 * @param result
 *        The first assertion below <code>&lt;result&gt;</code>;
 *        <code>null</code> if there is none.
 * @author Philip Helger
 */
public record QT3TestCase (@NonNull String name,
                           @NonNull ICommonsList <QT3Dependency> dependencies,
                           @Nullable QT3Environment environment,
                           @NonNull String testExpr,
                           @NonNull String testFile,
                           @Nullable QT3Assertion result)
{}
