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
package com.helger.phoxtrot.xpath.conformance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.jaxp.JdkSchemaProvider;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xdm.schema.XdmSchemaException;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSource;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSupport;

/**
 * Compiles the schema documents of an environment with the schema processor
 * of the run and caches the result by the list of files, since the same
 * catalogue environment is used by many test cases. A schema that does not
 * compile is cached as unavailable; its sources are then read without
 * validation, like Go. The caches are static and shared by all test sets of
 * the JVM.
 * <p>
 * The schema processor is the provider of {@link XdmSchemaSupport} (the
 * native validator of <code>phoxtrot-xsd</code> if it is on the class path, else
 * Apache Xerces-J; <code>-Dxdm.schemaProvider=native|xerces</code> picks
 * one), unless that is not available or <code>-Dqt3.schemaProcessor=jdk</code>
 * selects the JDK validator, which only validates and gives no in-scope
 * schema definitions.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class QT3SchemaCache
{
  /**
   * The schema processor: the provider of {@link XdmSchemaSupport} (the
   * native validator or Apache Xerces-J, both with a schema component model:
   * element declarations, content types, user-defined types) if one is
   * available, unless <code>-Dqt3.schemaProcessor=jdk</code> selects the JDK
   * validator. Despite the name, <code>true</code> does not mean Xerces-J.
   */
  private static final boolean USE_XERCES = XdmSchemaSupport.isAvailable () &&
                                            !"jdk".equals (System.getProperty ("qt3.schemaProcessor"));

  private static final JdkSchemaProvider JDK_PROVIDER = new JdkSchemaProvider ();

  private static final ConcurrentHashMap <List <Path>, Optional <IXdmSchemaSet>> JDK_CACHE = new ConcurrentHashMap <> ();
  private static final ConcurrentHashMap <List <Path>, Optional <IXdmSchemaSet>> SET_CACHE = new ConcurrentHashMap <> ();

  @PresentForCodeCoverage
  private static final QT3SchemaCache INSTANCE = new QT3SchemaCache ();

  private QT3SchemaCache ()
  {}

  /**
   * @return <code>true</code> if the provider of {@link XdmSchemaSupport}
   *         (the native validator or Apache Xerces-J) is the schema processor
   *         of the run, <code>false</code> for the JDK validator.
   */
  public static boolean isXerces ()
  {
    return USE_XERCES;
  }

  /**
   * @param aFiles
   *        The schema documents.
   * @return One schema source per file, identified by its <code>file:</code>
   *         URI only (the processor reads the file itself).
   */
  @NonNull
  private static ICommonsList <XdmSchemaSource> _getSources (@NonNull final List <Path> aFiles)
  {
    final ICommonsList <XdmSchemaSource> ret = new CommonsArrayList <> ();
    for (final Path aFile : aFiles)
      ret.add (new XdmSchemaSource (aFile.toUri ().toString (), null));
    return ret;
  }

  /**
   * Compile the files together into one schema set with the provider of
   * {@link XdmSchemaSupport}.
   *
   * @param aFiles
   *        The schema documents.
   * @return The schema set, or empty if compiling raised an
   *         {@link XdmSchemaException}.
   */
  @NonNull
  private static Optional <IXdmSchemaSet> _compileSet (@NonNull final List <Path> aFiles)
  {
    try
    {
      return Optional.of (XdmSchemaSupport.compile (_getSources (aFiles)));
    }
    catch (final XdmSchemaException ex)
    {
      return Optional.empty ();
    }
  }

  /**
   * @param aFiles
   *        The schema documents.
   * @return The cached result of <code>_compileSet</code> for this list of
   *         files (order matters), compiling it on first use.
   */
  @NonNull
  private static Optional <IXdmSchemaSet> _getSchemaSetOptional (@NonNull final List <Path> aFiles)
  {
    return SET_CACHE.computeIfAbsent (List.copyOf (aFiles), QT3SchemaCache::_compileSet);
  }

  /**
   * Compile the files together with the JDK validator.
   *
   * @param aFiles
   *        The schema documents.
   * @return The schema set (validation only), or empty if compiling raised
   *         an {@link XdmSchemaException}.
   */
  @NonNull
  private static Optional <IXdmSchemaSet> _compileJdk (@NonNull final List <Path> aFiles)
  {
    try
    {
      return Optional.of (JDK_PROVIDER.compile (_getSources (aFiles)));
    }
    catch (final XdmSchemaException ex)
    {
      return Optional.empty ();
    }
  }

  /**
   * @param aFiles
   *        Absolute, normalised paths of the schema documents. Not empty.
   * @return The schema set to validate source documents against (the
   *         provider of {@link XdmSchemaSupport} or the JDK validator, see
   *         {@link #isXerces()}), or <code>null</code> if it does not
   *         compile. Cached per list of files.
   */
  @Nullable
  public static IXdmSchemaSet getValidationSchemaSet (@NonNull final List <Path> aFiles)
  {
    if (USE_XERCES)
      return _getSchemaSetOptional (aFiles).orElse (null);
    return JDK_CACHE.computeIfAbsent (List.copyOf (aFiles), QT3SchemaCache::_compileJdk).orElse (null);
  }

  /**
   * @param aFiles
   *        Absolute, normalised paths of the schema documents. Not empty.
   * @return The in-scope schema definitions of the documents, or
   *         <code>null</code> if they do not compile or the schema processor
   *         is the JDK validator.
   */
  @Nullable
  public static IXdmSchemaSet getSchemaSet (@NonNull final List <Path> aFiles)
  {
    if (!USE_XERCES)
      return null;
    return _getSchemaSetOptional (aFiles).orElse (null);
  }
}
