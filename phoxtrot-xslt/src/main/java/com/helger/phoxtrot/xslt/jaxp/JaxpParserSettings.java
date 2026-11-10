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
package com.helger.phoxtrot.xslt.jaxp;

import javax.xml.XMLConstants;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.EntityResolver;

import com.helger.phoxtrot.xdm.XdmParser;

/**
 * The settings every {@link XdmParser} of the JAXP adapter is created with:
 * stylesheets, sources and the documents and modules read through
 * {@link JaxpResourceResolver}.
 *
 * @param loadExternalEntities
 *        Whether external DTDs and entities are loaded
 *        ({@link XdmParser#setLoadExternalEntities(boolean)}).
 * @param accessExternalDtd
 *        The value of {@link XMLConstants#ACCESS_EXTERNAL_DTD}: the schemes
 *        the parser may open itself for them
 *        ({@link XdmParser#setAccessExternalDtd(String)}).
 * @param entityResolver
 *        The SAX entity resolver asked first for them, <code>null</code> for
 *        none ({@link XdmParser#setEntityResolver(EntityResolver)}).
 * @author Philip Helger
 */
record JaxpParserSettings (boolean loadExternalEntities,
                           @NonNull String accessExternalDtd,
                           @Nullable EntityResolver entityResolver)
{
  /**
   * @param bRecordLocations
   *        Whether the parsed nodes get their locations
   *        ({@link XdmParser#setRecordLocations(boolean)}).
   * @return A new parser with these settings. Never <code>null</code>.
   */
  @NonNull
  XdmParser createParser (final boolean bRecordLocations)
  {
    return new XdmParser ().setLoadExternalEntities (loadExternalEntities)
                           .setAccessExternalDtd (accessExternalDtd)
                           .setEntityResolver (entityResolver)
                           .setRecordLocations (bRecordLocations);
  }
}
