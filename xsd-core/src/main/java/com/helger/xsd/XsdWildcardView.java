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

import com.helger.collection.commons.ICommonsList;

/**
 * An xs:any or xs:anyAttribute (go-xslt <code>WildcardView</code>).
 *
 * @param mode
 *        <code>any</code>, <code>other</code> or <code>list</code>.
 * @param namespaces
 *        For <code>list</code>: the namespaces (<code>##targetNamespace</code>
 *        resolved, the empty string for <code>##local</code>).
 * @param processContents
 *        <code>strict</code>, <code>lax</code> or <code>skip</code>.
 * @author Philip Helger
 */
public record XsdWildcardView (@NonNull String mode, @NonNull ICommonsList <String> namespaces, @NonNull String processContents) implements
                              IXsdTermView
{}
