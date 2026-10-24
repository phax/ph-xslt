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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * A FOTS <code>&lt;environment&gt;</code>: either a reference to a named one
 * (only {@link #getRef()} set) or a full declaration (catalog level, test-set
 * level or inline in a test case).
 *
 * @author Philip Helger
 */
public final class QT3Environment
{
  /**
   * A <code>&lt;source&gt;</code>: role "." is the context item, "$name" a
   * variable, no role a document only reachable through its URI.
   *
   * @param role
   *        Role, may be empty.
   * @param file
   *        File relative to the environment's base directory, may be empty.
   * @param uri
   *        The URI under which fn:doc finds it, may be empty.
   * @param validation
   *        The validation mode (strict/lax), may be empty.
   */
  public record Source (@NonNull String role, @NonNull String file, @NonNull String uri, @NonNull String validation)
  {}

  /**
   * A <code>&lt;resource&gt;</code> for fn:unparsed-text and friends.
   *
   * @param file
   *        File relative to the environment's base directory.
   * @param uri
   *        URI under which it is retrieved.
   * @param encoding
   *        The "HTTP header" encoding, may be empty.
   * @param mediaType
   *        The media type, may be empty.
   */
  public record Resource (@NonNull String file, @NonNull String uri, @NonNull String encoding, @NonNull String mediaType)
  {}

  /**
   * A <code>&lt;namespace prefix="..." uri="..."/&gt;</code>; an empty prefix
   * is the default element namespace.
   *
   * @param prefix
   *        Prefix, may be empty.
   * @param uri
   *        Namespace URI.
   */
  public record Namespace (@NonNull String prefix, @NonNull String uri)
  {}

  /**
   * An external variable <code>&lt;param&gt;</code>.
   *
   * @param name
   *        Variable name (lexical QName).
   * @param namespaceUri
   *        The namespace URI of a prefixed name as bound on the param element
   *        itself or its ancestors in the catalog file; empty if unprefixed or
   *        unbound there.
   * @param select
   *        The expression giving its value, may be empty.
   * @param as
   *        Declared type, may be empty.
   * @param declared
   *        The raw <code>declared</code> attribute, may be empty.
   */
  public record Param (@NonNull String name,
                       @NonNull String namespaceUri,
                       @NonNull String select,
                       @NonNull String as,
                       @NonNull String declared)
  {}

  /**
   * A <code>&lt;schema&gt;</code> declaration.
   *
   * @param uri
   *        Target namespace, may be empty.
   * @param file
   *        File, may be empty.
   */
  public record Schema (@NonNull String uri, @NonNull String file)
  {}

  /**
   * A <code>&lt;collection uri="..."&gt;</code> with its member sources; an
   * empty URI is the default collection.
   *
   * @param uri
   *        Collection URI.
   * @param sources
   *        Member sources.
   */
  public record Collection (@NonNull String uri, @NonNull ICommonsList <Source> sources)
  {}

  /**
   * A <code>&lt;collation uri="..." default="..."/&gt;</code>.
   *
   * @param uri
   *        Collation URI.
   * @param isDefault
   *        Whether it is the default collation.
   */
  public record Collation (@NonNull String uri, boolean isDefault)
  {}

  /**
   * A <code>&lt;decimal-format&gt;</code>, recorded with all its attributes
   * (including namespace declarations on the element itself).
   *
   * @param attributes
   *        Attribute name to value, in document order.
   */
  public record DecimalFormat (@NonNull ICommonsOrderedMap <String, String> attributes)
  {}

  private final String m_sName;
  private final String m_sRef;
  private final Path m_aBaseDir;
  private final ICommonsList <Source> m_aSources = new CommonsArrayList <> ();
  private final ICommonsList <Resource> m_aResources = new CommonsArrayList <> ();
  private final ICommonsList <Namespace> m_aNamespaces = new CommonsArrayList <> ();
  private final ICommonsList <Param> m_aParams = new CommonsArrayList <> ();
  private final ICommonsList <Schema> m_aSchemas = new CommonsArrayList <> ();
  private final ICommonsList <Collection> m_aCollections = new CommonsArrayList <> ();
  private final ICommonsList <Collation> m_aCollations = new CommonsArrayList <> ();
  private final ICommonsList <DecimalFormat> m_aDecimalFormats = new CommonsArrayList <> ();
  private String m_sStaticBaseUri;
  private String m_sContextItemSelect;

  /**
   * @param sName
   *        Environment name, may be empty for inline environments.
   * @param sRef
   *        Referenced environment name, empty for a declaration.
   * @param aBaseDir
   *        The directory relative file names are resolved against.
   */
  public QT3Environment (@NonNull final String sName, @NonNull final String sRef, @NonNull final Path aBaseDir)
  {
    m_sName = sName;
    m_sRef = sRef;
    m_aBaseDir = aBaseDir;
  }

  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  /** @return The referenced environment name; empty for a declaration. */
  @NonNull
  public String getRef ()
  {
    return m_sRef;
  }

  public boolean isReference ()
  {
    return !m_sRef.isEmpty ();
  }

  @NonNull
  public Path getBaseDir ()
  {
    return m_aBaseDir;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Source> getAllSources ()
  {
    return m_aSources.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Resource> getAllResources ()
  {
    return m_aResources.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Namespace> getAllNamespaces ()
  {
    return m_aNamespaces.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Param> getAllParams ()
  {
    return m_aParams.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Schema> getAllSchemas ()
  {
    return m_aSchemas.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Collection> getAllCollections ()
  {
    return m_aCollections.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Collation> getAllCollations ()
  {
    return m_aCollations.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <DecimalFormat> getAllDecimalFormats ()
  {
    return m_aDecimalFormats.getClone ();
  }

  /** @return The explicit static base URI or <code>null</code>. */
  @Nullable
  public String getStaticBaseUri ()
  {
    return m_sStaticBaseUri;
  }

  /** @return The <code>&lt;context-item select="..."/&gt;</code> expression or <code>null</code>. */
  @Nullable
  public String getContextItemSelect ()
  {
    return m_sContextItemSelect;
  }

  void addSource (@NonNull final Source aSource)
  {
    m_aSources.add (aSource);
  }

  void addResource (@NonNull final Resource aResource)
  {
    m_aResources.add (aResource);
  }

  void addNamespace (@NonNull final Namespace aNamespace)
  {
    m_aNamespaces.add (aNamespace);
  }

  void addParam (@NonNull final Param aParam)
  {
    m_aParams.add (aParam);
  }

  void addSchema (@NonNull final Schema aSchema)
  {
    m_aSchemas.add (aSchema);
  }

  void addCollection (@NonNull final Collection aCollection)
  {
    m_aCollections.add (aCollection);
  }

  void addCollation (@NonNull final Collation aCollation)
  {
    m_aCollations.add (aCollation);
  }

  void addDecimalFormat (@NonNull final DecimalFormat aDecimalFormat)
  {
    m_aDecimalFormats.add (aDecimalFormat);
  }

  void setStaticBaseUri (@Nullable final String sStaticBaseUri)
  {
    m_sStaticBaseUri = sStaticBaseUri;
  }

  void setContextItemSelect (@Nullable final String sContextItemSelect)
  {
    m_sContextItemSelect = sContextItemSelect;
  }

  @Override
  public String toString ()
  {
    return isReference () ? "environment ref=" + m_sRef : "environment " + m_sName;
  }
}
