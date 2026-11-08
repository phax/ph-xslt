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

import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.XdmQName;

/**
 * A catalog <code>&lt;param&gt;</code>: a stylesheet parameter (in
 * <code>&lt;test&gt;</code> or <code>&lt;environment&gt;</code>), a parameter of
 * the initial template or mode, or an argument of the initial function.
 *
 * @param name
 *        The expanded name, <code>null</code> if its prefix is not bound in the
 *        catalog.
 * @param lexicalName
 *        The name as written.
 * @param select
 *        The XPath expression giving the value.
 * @param as
 *        The type the value is supplied as, empty if absent.
 * @param isStatic
 *        <code>static="yes"</code>: supplied at compile time.
 * @param tunnel
 *        <code>tunnel="yes"</code>.
 * @param namespaces
 *        The prefixed namespaces in scope on the element, for evaluating
 *        <code>select</code>.
 * @author Philip Helger
 */
public record XSLT30Param (@Nullable XdmQName name,
                           @NonNull String lexicalName,
                           @NonNull String select,
                           @NonNull String as,
                           boolean isStatic,
                           boolean tunnel,
                           @NonNull ICommonsOrderedMap <String, String> namespaces)
{}
