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
package com.helger.phoxtrot.xpath.pattern;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xdm.XdmQName;

/**
 * The names the nodes matched by a pattern must have (see {@link XsltPattern#getFinalNames()}):
 * every matching node is an element with one of the element names or an attribute with one of the
 * attribute names. Both lists are distinct and unmodifiable; either may be empty.
 *
 * @param elementNames
 *        The element names.
 * @param attributeNames
 *        The attribute names.
 * @author Philip Helger
 */
public record PatternFinalNames (@NonNull List <XdmQName> elementNames, @NonNull List <XdmQName> attributeNames)
{}
