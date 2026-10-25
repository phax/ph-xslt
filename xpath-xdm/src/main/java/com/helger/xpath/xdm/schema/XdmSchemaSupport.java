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
import java.util.function.IntFunction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;

/**
 * Entry point to schema processing (the schema component model and validation
 * of XDM trees), which is the basis of schema-awareness. It is backed by
 * Apache Xerces-J, an optional dependency: when Xerces-J is not on the class
 * path, {@link #isAvailable()} is <code>false</code> and nothing of it is ever
 * loaded. Xerces-J classes are instantiated directly, never through a JAXP
 * lookup, so the JAXP defaults of the application stay untouched.
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

  private static final IXdmSchemaProvider PROVIDER = _loadProvider ();

  @PresentForCodeCoverage
  private static final XdmSchemaSupport INSTANCE = new XdmSchemaSupport ();

  private XdmSchemaSupport ()
  {}

  @Nullable
  private static IXdmSchemaProvider _loadProvider ()
  {
    final ClassLoader aLoader = XdmSchemaSupport.class.getClassLoader ();
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
   * @return <code>true</code> if schema processing is available, i.e. Apache
   *         Xerces-J is on the class path.
   */
  public static boolean isAvailable ()
  {
    return PROVIDER != null;
  }

  @NonNull
  private static IXdmSchemaProvider _getProvider ()
  {
    if (PROVIDER == null)
      throw new IllegalStateException ("Schema processing needs Apache Xerces-J (xerces:xercesImpl) on the class path");
    return PROVIDER;
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

  /**
   * @param aTypeInfo
   *        A type reported by a validator.
   * @return The schema component model of the type, if the validator was the
   *         one of {@link IXdmSchemaSet#getJaxpSchema()}; <code>null</code>
   *         for other validators (e.g. the JDK's) and if schema processing is
   *         not available.
   */
  @Nullable
  public static IXdmTypeDefinition getDefinition (@Nullable final TypeInfo aTypeInfo)
  {
    if (aTypeInfo == null || PROVIDER == null)
      return null;
    return PROVIDER.getDefinition (aTypeInfo);
  }

  /**
   * @param aValidatorHandler
   *        A validator handler.
   * @return For the validator of {@link IXdmSchemaSet#getJaxpSchema()}: a
   *         function from the attribute index of the current start tag to the
   *         declared type of the attribute (for a union type the union, not
   *         the member that matched); otherwise <code>null</code>.
   */
  @Nullable
  public static IntFunction <TypeInfo> getAttributeTypeDefinitionAccess (@NonNull final Object aValidatorHandler)
  {
    return PROVIDER == null ? null : PROVIDER.getAttributeTypeDefinitionAccess (aValidatorHandler);
  }
}
