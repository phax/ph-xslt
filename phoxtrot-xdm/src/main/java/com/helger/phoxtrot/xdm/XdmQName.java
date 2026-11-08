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
package com.helger.phoxtrot.xdm;

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

  /**
   * Create a name without prefix.
   *
   * @param sNamespaceUri
   *        The namespace URI, the empty string for no namespace. May not be
   *        <code>null</code>.
   * @param sLocalName
   *        The local part. May neither be <code>null</code> nor empty.
   */
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

  /**
   * Get this name with the canonical instances of its strings
   * ({@link String#intern()}). The JDK XML parsers report element and
   * attribute names as such instances, so that comparing an interned name
   * with the name of a parsed node is decided by the identity checks of
   * {@link String#equals(Object)} on a match. The JVM keeps interned strings
   * in a thread-safe table from which unused ones are garbage collected.
   * Meant for names that are compared often, such as the names of node tests,
   * since every call looks up all three strings.
   *
   * @return This name if its strings are canonical already, otherwise an
   *         equal name with the same prefix and canonical strings.
   */
  @NonNull
  public XdmQName getInterned ()
  {
    final String sNamespaceUri = namespaceUri.intern ();
    final String sLocalName = localName.intern ();
    final String sPrefix = prefix.intern ();
    if (sNamespaceUri == namespaceUri && sLocalName == localName && sPrefix == prefix)
      return this;
    return new XdmQName (sNamespaceUri, sLocalName, sPrefix);
  }

  /**
   * Compare the expanded name, ignoring the prefix.
   *
   * @param sNamespaceUri
   *        The namespace URI; <code>null</code> is the same as the empty
   *        string, no namespace.
   * @param sLocalName
   *        The local part. <code>null</code> never matches.
   * @return <code>true</code> if namespace URI and local part are equal.
   */
  public boolean isSameName (@Nullable final String sNamespaceUri, @Nullable final String sLocalName)
  {
    return namespaceUri.equals (sNamespaceUri == null ? "" : sNamespaceUri) && localName.equals (sLocalName);
  }

  @Override
  public boolean equals (final Object aOther)
  {
    if (this == aOther)
      return true;
    // The local names are short and usually differ, the namespace URIs are
    // long and often equal, so the local name is compared first. Different
    // local names are told apart by their cached hash codes, without reading
    // the characters
    return aOther instanceof final XdmQName aOtherName &&
           localName.hashCode () == aOtherName.localName.hashCode () &&
           localName.equals (aOtherName.localName) &&
           namespaceUri.equals (aOtherName.namespaceUri);
  }

  @Override
  public int hashCode ()
  {
    // The value of Objects.hash (namespaceUri, localName) without the varargs
    // array
    return 31 * (31 + namespaceUri.hashCode ()) + localName.hashCode ();
  }

  @Override
  public String toString ()
  {
    return getEQName ();
  }
}
