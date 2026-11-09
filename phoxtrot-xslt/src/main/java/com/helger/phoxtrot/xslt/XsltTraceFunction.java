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
package com.helger.phoxtrot.xslt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * An executed stylesheet function (<code>xsl:function</code>) as passed to an
 * {@link IXsltTraceListener}.
 *
 * @param name
 *        The name of the function.
 * @param arity
 *        The number of its parameters.
 * @param moduleUri
 *        The URI of the stylesheet module of the <code>xsl:function</code>
 *        element, or <code>null</code> if unknown.
 * @param line
 *        The 1-based line of the <code>xsl:function</code> element, 0 if
 *        unknown.
 * @param column
 *        The 1-based column of the <code>xsl:function</code> element, 0 if
 *        unknown.
 * @author Philip Helger
 */
@Immutable
public record XsltTraceFunction (@NonNull XdmQName name, int arity, @Nullable String moduleUri, int line, int column)
{}
