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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;

/**
 * Entry point to schema processing (the schema component model and validation
 * of XDM trees), which is the basis of schema-awareness. It is backed by one
 * of two optional providers, both loaded by name only if present: the native
 * validator (<code>com.helger.xsd.XsdSchemaProvider</code> of the module
 * <code>xsd-core</code>) or Apache Xerces-J. The system property
 * <code>xdm.schemaProvider</code> selects <code>native</code> or
 * <code>xerces</code>; without it the native validator is used if it is on the
 * class path, else Xerces-J. When the selected provider is not on the class
 * path, {@link #isAvailable()} is <code>false</code> and nothing of it is ever
 * loaded. Xerces-J classes are instantiated directly, never through a JAXP
 * lookup, so the JAXP defaults of the application stay untouched. The JDK's
 * own validator is available separately and with less information, as
 * {@link com.helger.xpath.xdm.jaxp.JdkSchemaProvider}.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XdmSchemaSupport
{
  /** The class that is checked to find out whether Xerces-J is present. */
  private static final String XERCES_MARKER_CLASS = "org.apache.xerces.xs.XSModel";
  /** The provider implementation, only loaded when Xerces-J is present. */
  private static final String PROVIDER_CLASS = "com.helger.xpath.xdm.xerces.XercesSchemaProvider";
  /** The native provider (module xsd-core), only loaded when present. */
  private static final String NATIVE_PROVIDER_CLASS = "com.helger.xsd.XsdSchemaProvider";
  /** The system property that selects the provider: <code>native</code> or <code>xerces</code>. */
  public static final String SYSTEM_PROPERTY_PROVIDER = "xdm.schemaProvider";

  private static final IXdmSchemaProvider PROVIDER = _loadProvider ();

  @PresentForCodeCoverage
  private static final XdmSchemaSupport INSTANCE = new XdmSchemaSupport ();

  private XdmSchemaSupport ()
  {}

  @Nullable
  private static IXdmSchemaProvider _loadNativeProvider (@NonNull final ClassLoader aLoader)
  {
    try
    {
      return (IXdmSchemaProvider) Class.forName (NATIVE_PROVIDER_CLASS, true, aLoader)
                                       .getDeclaredConstructor ()
                                       .newInstance ();
    }
    catch (final ReflectiveOperationException | LinkageError ex)
    {
      return null;
    }
  }

  @Nullable
  private static IXdmSchemaProvider _loadProvider ()
  {
    final ClassLoader aLoader = XdmSchemaSupport.class.getClassLoader ();
    final String sWanted = System.getProperty (SYSTEM_PROPERTY_PROVIDER, "");
    if (!"xerces".equals (sWanted))
    {
      final IXdmSchemaProvider aNative = _loadNativeProvider (aLoader);
      if (aNative != null || "native".equals (sWanted))
        return aNative;
    }
    try
    {
      Class.forName (XERCES_MARKER_CLASS, false, aLoader);
    }
    catch (final ClassNotFoundException | LinkageError ex)
    {
      return null;
    }
    try
    {
      return (IXdmSchemaProvider) Class.forName (PROVIDER_CLASS, true, aLoader).getDeclaredConstructor ().newInstance ();
    }
    catch (final ReflectiveOperationException | LinkageError ex)
    {
      return null;
    }
  }

  /**
   * @return <code>true</code> if schema processing is available, i.e. the
   *         selected provider (the native validator or Apache Xerces-J) is on
   *         the class path.
   */
  public static boolean isAvailable ()
  {
    return PROVIDER != null;
  }

  @NonNull
  private static IXdmSchemaProvider _getProvider ()
  {
    if (PROVIDER == null)
      throw new IllegalStateException ("Schema processing needs the native validator (xsd-core) or Apache Xerces-J (xerces:xercesImpl) on the class path");
    return PROVIDER;
  }

  /**
   * @return The simple class name of the provider in use, e.g.
   *         <code>XsdSchemaProvider</code> or <code>XercesSchemaProvider</code>;
   *         <code>null</code> if schema processing is not available.
   */
  @Nullable
  public static String getProviderName ()
  {
    return PROVIDER == null ? null : PROVIDER.getClass ().getSimpleName ();
  }

  /**
   * Compile schema documents into one schema set.
   *
   * @param aSources
   *        The schema documents. May not be <code>null</code>.
   * @return The schema set.
   * @throws XdmSchemaException
   *         if the documents cannot be read or are not a valid schema
   * @throws IllegalStateException
   *         if schema processing is not available
   */
  @NonNull
  public static IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    ValueEnforcer.notNull (aSources, "Sources");
    return _getProvider ().compile (aSources);
  }

  /**
   * @return The schema set with only the built-in types.
   * @throws IllegalStateException
   *         if schema processing is not available
   */
  @NonNull
  public static IXdmSchemaSet getBuiltinSchemaSet ()
  {
    try
    {
      return _getProvider ().compile (new CommonsArrayList <> ());
    }
    catch (final XdmSchemaException ex)
    {
      throw new IllegalStateException ("Failed to create the built-in schema set", ex);
    }
  }
}
