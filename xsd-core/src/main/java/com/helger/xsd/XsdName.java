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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.xdm.XdmQName;

/**
 * An expanded (namespace-qualified) name: the identity key of schema components
 * and instance elements. The empty name (both parts empty) stands for "no
 * name", e.g. of an anonymous type.
 *
 * @param space
 *        The namespace URI, empty for no namespace.
 * @param local
 *        The local name.
 * @author Philip Helger
 */
public record XsdName (@NonNull String space, @NonNull String local)
{
  /** The empty name. */
  public static final XsdName NONE = new XsdName ("", "");

  /** @return <code>true</code> for the empty name. */
  public boolean isZero ()
  {
    return space.isEmpty () && local.isEmpty ();
  }

  /**
   * @param aName
   *        The XDM name. May not be <code>null</code>.
   * @return The expanded name without prefix.
   */
  @NonNull
  public static XsdName of (@NonNull final XdmQName aName)
  {
    return new XsdName (aName.namespaceUri (), aName.localName ());
  }

  /** @return The local name, or <code>{namespace}local</code> with a namespace. */
  @Override
  public String toString ()
  {
    return space.isEmpty () ? local : "{" + space + "}" + local;
  }
}
