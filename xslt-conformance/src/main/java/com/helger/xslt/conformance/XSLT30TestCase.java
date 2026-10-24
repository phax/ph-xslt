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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * One <code>&lt;test-case&gt;</code>.
 *
 * @param name
 *        The case name (unique in the suite).
 * @param dependencies
 *        The children of its <code>&lt;dependencies&gt;</code> elements.
 * @param environment
 *        Its first <code>&lt;environment&gt;</code> (declaration or
 *        reference), or <code>null</code>.
 * @param test
 *        The <code>&lt;test&gt;</code> element.
 * @param result
 *        The first child of <code>&lt;result&gt;</code>, or <code>null</code>
 *        if there is none.
 * @author Philip Helger
 */
public record XSLT30TestCase (@NonNull String name,
                              @NonNull ICommonsList <XSLT30Dependency> dependencies,
                              @Nullable XSLT30Environment environment,
                              @NonNull XSLT30Invocation test,
                              @Nullable XSLT30Assertion result)
{}
