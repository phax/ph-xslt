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
 * One <code>xs:documentation</code> of an <code>xs:annotation</code>. Only kept
 * when the schema is compiled with documentation (see
 * {@link XsdCompileOptions#isKeepDocumentation()}).
 *
 * @param lang
 *        The <code>xml:lang</code> of the element, empty when absent.
 * @param source
 *        The <code>source</code> attribute (a URI), empty when absent.
 * @param text
 *        The text content (mixed content flattened to plain text), with leading
 *        and trailing white space removed.
 * @author Philip Helger
 */
public record XsdDocumentation (@NonNull String lang, @NonNull String source, @NonNull String text)
{}
