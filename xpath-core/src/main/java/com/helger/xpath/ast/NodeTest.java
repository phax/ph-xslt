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
package com.helger.xpath.ast;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * A node test of a step and the node-kind part of an ItemType. Names are
 * resolved at parse time.
 *
 * @param kind
 *        What is tested.
 * @param anyName
 *        <code>*</code> wildcard: any name (for NAME) / no name given (for
 *        element(), attribute()).
 * @param anyNamespace
 *        <code>*:local</code>: any namespace, the local name in
 *        <code>name</code>.
 * @param anyLocalName
 *        <code>prefix:*</code>: the namespace in <code>name</code>, any local
 *        name.
 * @param name
 *        The resolved name. For ELEMENT/ATTRIBUTE/SCHEMA_* the name argument;
 *        for PROCESSING_INSTRUCTION the target as local name; <code>null</code>
 *        if none or a wildcard has no name part.
 * @param typeName
 *        Optional type annotation of element(N, T) / attribute(N, T).
 * @param nillable
 *        The <code>?</code> of element(N, T?).
 * @param inner
 *        The element test of document-node(element(...)), else
 *        <code>null</code>.
 * @param elementDeclaration
 *        The declaration named by schema-element(N), resolved at parse time
 *        against the in-scope schema definitions, else <code>null</code>.
 * @param attributeDeclaration
 *        The declaration named by schema-attribute(N), else
 *        <code>null</code>.
 * @param typeDefinition
 *        The user-defined type named by element(N, T) / attribute(N, T),
 *        resolved at parse time against the in-scope schema definitions, else
 *        <code>null</code>.
 * @author Philip Helger
 */
public record NodeTest (@NonNull ENodeTestKind kind,
                        boolean anyName,
                        boolean anyNamespace,
                        boolean anyLocalName,
                        @Nullable XdmQName name,
                        @Nullable XdmQName typeName,
                        boolean nillable,
                        @Nullable NodeTest inner,
                        @Nullable IXdmElementDeclaration elementDeclaration,
                        @Nullable IXdmAttributeDeclaration attributeDeclaration,
                        @Nullable IXdmTypeDefinition typeDefinition)
{
  /**
   * A node test without schema declarations.
   *
   * @param kind
   *        What is tested.
   * @param anyName
   *        Any name.
   * @param anyNamespace
   *        Any namespace.
   * @param anyLocalName
   *        Any local name.
   * @param name
   *        The resolved name.
   * @param typeName
   *        Optional type name.
   * @param nillable
   *        Nillable.
   * @param inner
   *        Inner element test.
   */
  public NodeTest (@NonNull final ENodeTestKind kind,
                   final boolean anyName,
                   final boolean anyNamespace,
                   final boolean anyLocalName,
                   @Nullable final XdmQName name,
                   @Nullable final XdmQName typeName,
                   final boolean nillable,
                   @Nullable final NodeTest inner)
  {
    this (kind, anyName, anyNamespace, anyLocalName, name, typeName, nillable, inner, null, null, null);
  }

  public static final NodeTest ANY_NODE = new NodeTest (ENodeTestKind.ANY_NODE,
                                                        false,
                                                        false,
                                                        false,
                                                        null,
                                                        null,
                                                        false,
                                                        null);

  /** @return A plain test for a node kind without arguments. */
  @NonNull
  public static NodeTest ofKind (@NonNull final ENodeTestKind eKind)
  {
    return new NodeTest (eKind, false, false, false, null, null, false, null);
  }
}
