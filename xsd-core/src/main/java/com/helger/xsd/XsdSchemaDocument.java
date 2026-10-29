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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.IXdmNode;

/**
 * One schema document of a compile (the entry document plus every document
 * reached through include/import/redefine/override), with its own namespace
 * context - go-xslt <code>schemaDoc</code>.
 *
 * @author Philip Helger
 */
final class XsdSchemaDocument
{
  /** The xs:schema element. */
  final IXdmNode m_aElement;
  /** The directory relative references resolve against, may be <code>null</code>. */
  final Path m_aBaseDir;
  String m_sTNS = "";
  boolean m_bElemQualified;
  boolean m_bAttrQualified;
  String m_sFinalDefault = "";
  String m_sBlockDefault = "";
  /**
   * The document declared no targetNamespace of its own but adopted one from
   * an including schema (chameleon include).
   */
  boolean m_bChameleon;
  /**
   * The namespaces this document declares an xs:import for (empty string for
   * an import without namespace).
   */
  final ICommonsSet <String> m_aImports = new CommonsHashSet <> ();
  /** xs:schema/@defaultAttributes (XSD 1.1). */
  XsdName m_aDefaultAttrs = XsdName.NONE;
  boolean m_bHasDefaultAttrs;
  /** This document's xs:defaultOpenContent element (XSD 1.1). */
  IXdmNode m_aDefaultOpenNode;

  XsdSchemaDocument (@NonNull final IXdmNode aElement, @Nullable final Path aBaseDir)
  {
    m_aElement = aElement;
    m_aBaseDir = aBaseDir;
  }

  @Override
  public String toString ()
  {
    return "XsdSchemaDocument[" + m_sTNS + "]";
  }
}
