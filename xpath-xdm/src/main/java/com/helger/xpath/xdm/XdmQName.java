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
package com.helger.xpath.xdm;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.enforce.ValueEnforcer;

/**
 * An expanded QName: namespace URI, local part and the advisory prefix. The
 * prefix is kept for serialisation only and takes no part in
 * {@link #equals(Object)} or {@link #hashCode()}.
 *
 * @param namespaceUri
 *        The namespace URI. Never <code>null</code>; the empty string means
 *        "no namespace".
 * @param localName
 *        The local part. Never empty.
 * @param prefix
 *        The prefix used in the source. Never <code>null</code>; the empty
 *        string means "no prefix".
 * @author Philip Helger
 */
public record XdmQName (@NonNull String namespaceUri, @NonNull String localName, @NonNull String prefix)
{
  public XdmQName
  {
    ValueEnforcer.notNull (namespaceUri, "NamespaceUri");
    ValueEnforcer.notEmpty (localName, "LocalName");
    ValueEnforcer.notNull (prefix, "Prefix");
  }

  /**
   * Create a name in no namespace.
   *
   * @param sLocalName
   *        The local part. May neither be <code>null</code> nor empty.
   */
  public XdmQName (@NonNull final String sLocalName)
  {
    this ("", sLocalName, "");
  }

  public XdmQName (@NonNull final String sNamespaceUri, @NonNull final String sLocalName)
  {
    this (sNamespaceUri, sLocalName, "");
  }

  public boolean hasNamespace ()
  {
    return !namespaceUri.isEmpty ();
  }

  public boolean hasPrefix ()
  {
    return !prefix.isEmpty ();
  }

  /**
   * @return The name as written in a source document: <code>prefix:local</code>
   *         or just <code>local</code>.
   */
  @NonNull
  public String getLexicalName ()
  {
    return prefix.isEmpty () ? localName : prefix + ":" + localName;
  }

  /**
   * @return The name in URIQualifiedName notation <code>Q{uri}local</code>
   *         (XPath 3.1 section 3.1.2), without the braces part for names in no
   *         namespace.
   */
  @NonNull
  public String getEQName ()
  {
    return namespaceUri.isEmpty () ? localName : "Q{" + namespaceUri + "}" + localName;
  }

  public boolean isSameName (@Nullable final String sNamespaceUri, @Nullable final String sLocalName)
  {
    return namespaceUri.equals (sNamespaceUri == null ? "" : sNamespaceUri) && localName.equals (sLocalName);
  }

  @Override
  public boolean equals (final Object aOther)
  {
    if (this == aOther)
      return true;
    return aOther instanceof final XdmQName aOtherName &&
           namespaceUri.equals (aOtherName.namespaceUri) &&
           localName.equals (aOtherName.localName);
  }

  @Override
  public int hashCode ()
  {
    return Objects.hash (namespaceUri, localName);
  }

  @Override
  public String toString ()
  {
    return getEQName ();
  }
}
