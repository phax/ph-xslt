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
 * A simple or complex type definition of the read-only component model (go-xslt
 * <code>TypeDefinitionView</code>).
 *
 * @author Philip Helger
 */
public sealed interface IXsdTypeDefinitionView permits XsdSimpleTypeView, XsdComplexTypeView
{
  /** @return The expanded name; {@link XsdName#NONE} for an anonymous type. */
  @NonNull
  XsdName getName ();

  /** @return <code>true</code> for a simple type (atomic, list or union). */
  boolean isSimple ();

  /**
   * @return The built-in type this type is, or derives from through
   *         restriction steps: <code>{xs}decimal</code> for a restriction of a
   *         restriction of xs:decimal. For a list it is the item type's, for a
   *         union xs:anySimpleType, for a complex type without simple content
   *         xs:anyType.
   */
  @NonNull
  XsdName getBuiltinBase ();
}
