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
package com.helger.xpath.xdm.schema;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmValidationException;

/**
 * A compiled set of schema components (the in-scope schema definitions of a
 * stylesheet or an XPath expression), including the built-in types. Immutable
 * and thread-safe. Created by {@link XdmSchemaSupport}.
 *
 * @author Philip Helger
 */
public interface IXdmSchemaSet
{
  /**
   * @param aName
   *        Type name.
   * @return The named type definition (built-in or user-defined), or
   *         <code>null</code>.
   */
  @Nullable
  IXdmTypeDefinition getTypeDefinition (@NonNull XdmQName aName);

  /**
   * @param aName
   *        Element name.
   * @return The top-level element declaration, or <code>null</code>.
   */
  @Nullable
  IXdmElementDeclaration getElementDeclaration (@NonNull XdmQName aName);

  /**
   * @param aName
   *        Attribute name.
   * @return The top-level attribute declaration, or <code>null</code>.
   */
  @Nullable
  IXdmAttributeDeclaration getAttributeDeclaration (@NonNull XdmQName aName);

  /**
   * Validate an element (with its subtree) in place: on success every element
   * and attribute of the subtree carries the type annotation the validation
   * gave it, nilled and the schema ID kinds are set, and elements that were not
   * assessed are annotated <code>xs:anyType</code>.
   *
   * @param aElement
   *        The element. Its in-scope namespaces are taken into account.
   * @param aType
   *        The type to validate the element against, or <code>null</code> to
   *        validate it against the top-level declaration of its name.
   * @param bDocumentLevel
   *        <code>true</code> to check the document-level constraints (ID
   *        uniqueness, IDREF targets) too.
   * @throws XdmValidationException
   *         if the element is not valid (nothing is changed then)
   */
  void validateElement (@NonNull XdmNode aElement,
                        @Nullable IXdmTypeDefinition aType,
                        boolean bDocumentLevel) throws XdmValidationException;

  /**
   * Validate a document in place, strictly: the document element must be
   * declared, and the document-level constraints (ID uniqueness, IDREF
   * targets) are checked. On success the tree carries the schema information
   * as after {@link #validateElement(XdmNode, IXdmTypeDefinition, boolean)},
   * default attributes and default element content are added, and whitespace
   * text nodes in element-only content are removed. ID kinds from the DTD are
   * kept unless the schema gives one. Schema location hints in the document
   * are not followed.
   *
   * @param aDocument
   *        The document node, as built by
   *        {@link com.helger.xpath.xdm.XdmParser}.
   * @throws XdmValidationException
   *         if the document is not valid (nothing is changed then)
   */
  void validateDocument (@NonNull XdmNode aDocument) throws XdmValidationException;
}
