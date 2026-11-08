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

/**
 * One <code>msg:message</code> entry of an <code>xs:appinfo</code> (namespace
 * {@link XsdConstants#NS_MESSAGES}): a readable diagnostic for an assertion, an
 * element declaration or a model group. It only affects the wording of an
 * error, never the verdict.
 *
 * @param rule
 *        The <code>rule</code> attribute, empty when absent.
 * @param lang
 *        The <code>xml:lang</code>, empty when absent.
 * @param text
 *        The trimmed text.
 * @param on
 *        On an element declaration or group: the kind of cardinality error the
 *        message is for (<code>missing</code>, <code>too-many</code>), empty
 *        for both.
 * @author Philip Helger
 */
record XsdMessage (@NonNull String rule, @NonNull String lang, @NonNull String text, @NonNull String on)
{}
