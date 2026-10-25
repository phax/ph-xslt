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
package com.helger.xslt.pkg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.tree.XslElement;

/**
 * One named component of a package manifest (XSLT 3.0 section 3.5.1).
 *
 * @param kind
 *        "template", "function", "variable", "attribute-set" or "mode".
 * @param name
 *        The expanded name, <code>null</code> for the unnamed mode.
 * @param arity
 *        The arity of a function, 0 otherwise.
 * @param visibility
 *        The visibility (public, private, final, abstract, hidden).
 * @param element
 *        The declaration, <code>null</code> for a mode used only implicitly.
 * @author Philip Helger
 */
public record PackageComponent (@NonNull String kind,
                                @Nullable XdmQName name,
                                int arity,
                                @NonNull String visibility,
                                @Nullable XslElement element)
{
  /**
   * @param sKind
   *        Component kind.
   * @param aName
   *        Component name, <code>null</code> for the unnamed mode.
   * @param nArity
   *        Arity of a function.
   * @return The symbolic name: two components are homonymous if these match.
   */
  @NonNull
  public static String getSymbolicName (@NonNull final String sKind, @Nullable final XdmQName aName, final int nArity)
  {
    final String s = sKind + "#" + (aName == null ? "" : aName.getEQName ());
    return "function".equals (sKind) ? s + "#" + nArity : s;
  }

  /** @return The symbolic name of this component. */
  @NonNull
  public String getSymbolicName ()
  {
    return getSymbolicName (kind, name, arity);
  }

  /**
   * @param sVisibility
   *        The new visibility.
   * @return A copy with that visibility.
   */
  @NonNull
  public PackageComponent withVisibility (@NonNull final String sVisibility)
  {
    return new PackageComponent (kind, name, arity, sVisibility, element);
  }

  /** @return A readable label, e.g. for messages. */
  @NonNull
  public String getLabel ()
  {
    return kind + " " + (name == null ? "#unnamed" : name.getEQName ()) + ("function".equals (kind) ? "#" + arity : "");
  }
}
