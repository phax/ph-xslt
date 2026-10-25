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

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;

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
  /**
   * The schema processor: Apache Xerces-J if it is on the class path (schema
   * component model: element declarations, content types, user-defined types),
   * unless <code>-Dqt3.schemaProcessor=jdk</code> selects the JDK validator.
   */
  private static final boolean USE_XERCES = XdmSchemaSupport.isAvailable () &&
                                            !"jdk".equals (System.getProperty ("qt3.schemaProcessor"));

  private static final ConcurrentHashMap <List <Path>, Optional <Schema>> CACHE = new ConcurrentHashMap <> ();
  private static final ConcurrentHashMap <List <Path>, Optional <IXdmSchemaSet>> SET_CACHE = new ConcurrentHashMap <> ();

  @PresentForCodeCoverage
  private static final QT3SchemaCache INSTANCE = new QT3SchemaCache ();

  private QT3SchemaCache ()
  {}

  /**
   * @return <code>true</code> if Apache Xerces-J is the schema processor of
   *         the run.
   */
  public static boolean isXerces ()
  {
    return USE_XERCES;
  }

  @NonNull
  private static Optional <IXdmSchemaSet> _compileSet (@NonNull final List <Path> aFiles)
  {
    final ICommonsList <XdmSchemaSource> aSources = new CommonsArrayList <> ();
    for (final Path aFile : aFiles)
      aSources.add (new XdmSchemaSource (aFile.toUri ().toString (), null));
    try
    {
      return Optional.of (XdmSchemaSupport.compile (aSources));
    }
    catch (final XdmSchemaException ex)
    {
      return Optional.empty ();
    }
  }

  @NonNull
  private static Optional <IXdmSchemaSet> _getSchemaSetOptional (@NonNull final List <Path> aFiles)
  {
    return SET_CACHE.computeIfAbsent (List.copyOf (aFiles), QT3SchemaCache::_compileSet);
  }

  @NonNull
  private static Optional <Schema> _compile (@NonNull final List <Path> aFiles)
  {
    if (USE_XERCES)
      return _getSchemaSetOptional (aFiles).map (IXdmSchemaSet::getJaxpSchema);
    final Source [] aSources = new Source [aFiles.size ()];
    for (int i = 0; i < aSources.length; ++i)
      aSources[i] = new StreamSource (aFiles.get (i).toUri ().toString ());
    try
    {
      return Optional.of (SchemaFactory.newDefaultInstance ().newSchema (aSources));
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

  /**
   * @param aFiles
   *        Absolute, normalised paths of the schema documents. Not empty.
   * @return The in-scope schema definitions of the documents, or
   *         <code>null</code> if they do not compile or the schema processor
   *         is not Xerces-J.
   */
  @Nullable
  public static IXdmSchemaSet getSchemaSet (@NonNull final List <Path> aFiles)
  {
    if (!USE_XERCES)
      return null;
    return _getSchemaSetOptional (aFiles).orElse (null);
  }
}
