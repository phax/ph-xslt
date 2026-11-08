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
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * A <code>&lt;testGroup&gt;</code>: at most one schema test plus the instance
 * tests that run against its schema.
 *
 * @param name
 *        Group name.
 * @param version
 *        The version token list, empty for all versions.
 * @param schemaTest
 *        The schema test, or <code>null</code> for a group whose instances
 *        are governed only by their own <code>xsi:schemaLocation</code> hints.
 * @param instanceTests
 *        The instance tests in document order.
 * @author Philip Helger
 */
public record XsdTestGroup (@NonNull String name,
                            @NonNull String version,
                            @Nullable XsdSchemaTest schemaTest,
                            @NonNull ICommonsList <XsdInstanceTest> instanceTests)
{}
