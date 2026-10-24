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
package com.helger.xslt.conformance;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * An xslt30-test <code>&lt;environment&gt;</code>: either a reference to a
 * named one (only <code>ref</code> set) or a declaration (catalog level, test
 * set level or inline in a test case).
 *
 * @param name
 *        The name, empty for an inline environment.
 * @param ref
 *        The referenced environment name, empty for a declaration.
 * @param baseDir
 *        Directory of the file the environment is declared in; relative
 *        <code>file</code> attributes resolve against it.
 * @param catalogLevel
 *        <code>true</code> if declared in <code>catalog.xml</code>.
 * @param sources
 *        The source documents.
 * @param stylesheets
 *        Stylesheet modules declared by the environment.
 * @param packages
 *        Library packages for <code>xsl:use-package</code>.
 * @param schemas
 *        Schema documents.
 * @param resources
 *        Unparsed-text resources.
 * @param collections
 *        Collections for <code>fn:collection</code>.
 * @param params
 *        Stylesheet parameters set by the environment.
 * @author Philip Helger
 */
public record XSLT30Environment (@NonNull String name,
                                 @NonNull String ref,
                                 @NonNull Path baseDir,
                                 boolean catalogLevel,
                                 @NonNull ICommonsList <Source> sources,
                                 @NonNull ICommonsList <Module> stylesheets,
                                 @NonNull ICommonsList <Module> packages,
                                 @NonNull ICommonsList <Schema> schemas,
                                 @NonNull ICommonsList <Resource> resources,
                                 @NonNull ICommonsList <Collection> collections,
                                 @NonNull ICommonsList <XSLT30Param> params)
{
  /**
   * A <code>&lt;source&gt;</code>.
   *
   * @param role
   *        "." for the principal source, empty otherwise.
   * @param file
   *        Relative file, may be empty.
   * @param uri
   *        URI under which the document is retrievable, may be empty.
   * @param select
   *        Selects the initial context item within the document (or
   *        constructs the document with <code>parse-xml(...)</code>), may be
   *        empty.
   * @param validation
   *        strict, lax, skip or empty.
   * @param streaming
   *        The <code>streaming</code> attribute, may be empty.
   * @param content
   *        Inline document text, <code>null</code> if there is no
   *        <code>&lt;content&gt;</code> child.
   * @param definesStylesheet
   *        <code>defines-stylesheet="true"</code>: the source embeds the
   *        stylesheet.
   */
  public record Source (@NonNull String role,
                        @NonNull String file,
                        @NonNull String uri,
                        @NonNull String select,
                        @NonNull String validation,
                        @NonNull String streaming,
                        @Nullable String content,
                        boolean definesStylesheet)
  {}

  /**
   * A <code>&lt;stylesheet&gt;</code> or <code>&lt;package&gt;</code>.
   *
   * @param file
   *        Relative file.
   * @param role
   *        principal, secondary or empty.
   * @param uri
   *        The package name, may be empty.
   * @param packageVersion
   *        The package version, may be empty.
   */
  public record Module (@NonNull String file, @NonNull String role, @NonNull String uri, @NonNull String packageVersion)
  {
    public boolean isSecondary ()
    {
      return "secondary".equals (role);
    }
  }

  /**
   * A <code>&lt;schema&gt;</code>.
   *
   * @param uri
   *        Target namespace, may be empty.
   * @param file
   *        Relative file, may be empty.
   * @param role
   *        source-reference, stylesheet-import, secondary or empty.
   */
  public record Schema (@NonNull String uri, @NonNull String file, @NonNull String role)
  {}

  /**
   * A <code>&lt;resource&gt;</code> for <code>fn:unparsed-text</code>.
   *
   * @param file
   *        Relative file.
   * @param uri
   *        The URI it is retrieved by.
   * @param encoding
   *        The declared encoding, may be empty.
   * @param mediaType
   *        The media type, may be empty.
   */
  public record Resource (@NonNull String file,
                          @NonNull String uri,
                          @NonNull String encoding,
                          @NonNull String mediaType)
  {}

  /**
   * A <code>&lt;collection&gt;</code>.
   *
   * @param uri
   *        Collection URI, empty for the default collection.
   * @param sources
   *        Its members.
   */
  public record Collection (@NonNull String uri, @NonNull ICommonsList <Source> sources)
  {}

  /** @return <code>true</code> if this is only a reference to a named environment. */
  public boolean isReference ()
  {
    return !ref.isEmpty ();
  }
}
