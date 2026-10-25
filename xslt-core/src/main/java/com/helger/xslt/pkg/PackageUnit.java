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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;

/**
 * One package taking part in a compilation: the principal package or one use
 * of a library package. Its modules are the package module and the modules it
 * includes or imports. A library package used twice is two units.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class PackageUnit
{
  /** One xsl:use-package of this package with the unit it loaded. */
  public record Use (@NonNull XslElement element, @NonNull PackageUnit used)
  {}

  private final String m_sKey;
  private final boolean m_bPrincipal;
  private final ICommonsList <XslModule> m_aModules = new CommonsArrayList <> ();
  private final ICommonsList <Use> m_aUses = new CommonsArrayList <> ();
  private final ICommonsSet <String> m_aHidden = new CommonsHashSet <> ();

  public PackageUnit (@NonNull final String sKey, final boolean bPrincipal)
  {
    m_sKey = sKey;
    m_bPrincipal = bPrincipal;
  }

  /** @return The identity of the package source (equal for two uses of one package). */
  @NonNull
  public String getKey ()
  {
    return m_sKey;
  }

  public boolean isPrincipal ()
  {
    return m_bPrincipal;
  }

  /** @return The package module, <code>null</code> before it is loaded. */
  @Nullable
  public XslModule getPackageModule ()
  {
    return m_aModules.getFirstOrNull ();
  }

  /** @return The xsl:package (or xsl:stylesheet) element of the package module. */
  @Nullable
  public XslElement getRoot ()
  {
    final XslModule aModule = getPackageModule ();
    return aModule == null ? null : aModule.getRoot ();
  }

  /** @return The modules of the package, the package module first (modifiable). */
  @NonNull
  public ICommonsList <XslModule> modules ()
  {
    return m_aModules;
  }

  /** @return The xsl:use-package elements of the package (modifiable). */
  @NonNull
  public ICommonsList <Use> uses ()
  {
    return m_aUses;
  }

  /**
   * @return The symbolic names of used-package components that code in this
   *         package cannot see (modifiable).
   */
  @NonNull
  public ICommonsSet <String> hidden ()
  {
    return m_aHidden;
  }

  @Override
  public String toString ()
  {
    return "package " + m_sKey;
  }
}
