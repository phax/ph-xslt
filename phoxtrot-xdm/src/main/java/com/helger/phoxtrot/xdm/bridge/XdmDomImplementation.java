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
package com.helger.phoxtrot.xdm.bridge;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

import com.helger.annotation.concurrent.Immutable;

/**
 * The DOM implementation of {@link XdmDomView}: it supports the read-only part of the features "Core" and "XML"
 * (versions 1.0 to 3.0) and creates no documents.
 *
 * @author Philip Helger
 */
@Immutable
final class XdmDomImplementation implements DOMImplementation
{
  static final XdmDomImplementation INSTANCE = new XdmDomImplementation ();

  private XdmDomImplementation ()
  {}

  public boolean hasFeature (final String sFeature, final String sVersion)
  {
    if (sFeature == null)
      return false;
    final String sName = sFeature.startsWith ("+") ? sFeature.substring (1) : sFeature;
    if (!"Core".equalsIgnoreCase (sName) && !"XML".equalsIgnoreCase (sName))
      return false;
    return sVersion == null ||
           sVersion.isEmpty () ||
           "1.0".equals (sVersion) ||
           "2.0".equals (sVersion) ||
           "3.0".equals (sVersion);
  }

  public DocumentType createDocumentType (final String sQualifiedName, final String sPublicId, final String sSystemId)
  {
    throw AbstractXdmDomNode.createNotSupported ("createDocumentType");
  }

  public Document createDocument (final String sNamespaceUri, final String sQualifiedName, final DocumentType aDoctype)
  {
    throw AbstractXdmDomNode.createNotSupported ("createDocument");
  }

  public Object getFeature (final String sFeature, final String sVersion)
  {
    return hasFeature (sFeature, sVersion) ? this : null;
  }
}
