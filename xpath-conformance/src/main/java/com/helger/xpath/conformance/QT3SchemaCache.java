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
package com.helger.xpath.conformance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Compiles the schema documents of an environment with the JDK schema factory
 * and caches the result by the list of files, since the same catalogue
 * environment is used by many test cases. A schema that does not compile is
 * cached as unavailable; its sources are then read without validation, like
 * Go.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class QT3SchemaCache
{
  private static final ConcurrentHashMap <List <Path>, Optional <Schema>> CACHE = new ConcurrentHashMap <> ();

  @PresentForCodeCoverage
  private static final QT3SchemaCache INSTANCE = new QT3SchemaCache ();

  private QT3SchemaCache ()
  {}

  @NonNull
  private static Optional <Schema> _compile (@NonNull final List <Path> aFiles)
  {
    final Source [] aSources = new Source [aFiles.size ()];
    for (int i = 0; i < aSources.length; ++i)
      aSources[i] = new StreamSource (aFiles.get (i).toUri ().toString ());
    try
    {
      return Optional.of (SchemaFactory.newInstance (XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema (aSources));
    }
    catch (final SAXException ex)
    {
      return Optional.empty ();
    }
  }

  /**
   * @param aFiles
   *        Absolute, normalised paths of the schema documents. Not empty.
   * @return The compiled schema or <code>null</code> if it does not compile.
   */
  @Nullable
  public static Schema getSchema (@NonNull final List <Path> aFiles)
  {
    return CACHE.computeIfAbsent (List.copyOf (aFiles), QT3SchemaCache::_compile).orElse (null);
  }
}
