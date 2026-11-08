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
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * An attribute of a complex type (go-xslt <code>AttributeUseView</code>).
 *
 * @param documentation
 *        The annotation of the use's own xs:attribute (for a reference the
 *        reference's); needs documentation kept at compile time.
 * @param declaration
 *        The attribute declaration the use refers to.
 * @param name
 *        The attribute name.
 * @param type
 *        The attribute type.
 * @param use
 *        optional, required or prohibited.
 * @param defaultValue
 *        The default value, <code>null</code> if none.
 * @param fixedValue
 *        The fixed value, <code>null</code> if none.
 * @author Philip Helger
 */
public record XsdAttributeUseView (@NonNull ICommonsList <XsdDocumentation> documentation,
                                   @NonNull XsdAttributeDeclView declaration,
                                   @NonNull XsdName name,
                                   @Nullable XsdSimpleTypeView type,
                                   @NonNull EXsdUse use,
                                   @Nullable String defaultValue,
                                   @Nullable String fixedValue)
{}
