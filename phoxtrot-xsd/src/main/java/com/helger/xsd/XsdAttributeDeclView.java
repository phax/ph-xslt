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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.ICommonsList;

/**
 * A global attribute declaration of the read-only component model (go-xslt
 * <code>AttributeDeclView</code>).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdAttributeDeclView
{
  private final XsdSchemaView m_aView;
  private final XsdAttributeDecl m_aDecl;

  XsdAttributeDeclView (@NonNull final XsdSchemaView aView, @NonNull final XsdAttributeDecl aDecl)
  {
    m_aView = aView;
    m_aDecl = aDecl;
  }

  /** @return The expanded name. */
  @NonNull
  public XsdName getName ()
  {
    return m_aDecl.m_aName;
  }

  /** @return The declaration's xs:documentation (needs documentation kept at compile time). */
  @NonNull
  public ICommonsList <XsdDocumentation> getDocumentation ()
  {
    return m_aDecl.m_aDocs.getClone ();
  }

  /** @return The attribute type. */
  @Nullable
  public XsdSimpleTypeView getType ()
  {
    return m_aView.simple (m_aDecl.m_aType);
  }

  /** @return The default value, <code>null</code> if none. */
  @Nullable
  public String getDefault ()
  {
    return m_aDecl.m_bHasDefault ? m_aDecl.m_sDefault : null;
  }

  /** @return The fixed value, <code>null</code> if none. */
  @Nullable
  public String getFixed ()
  {
    return m_aDecl.m_bHasFixed ? m_aDecl.m_sFixed : null;
  }

  @Override
  public String toString ()
  {
    return "XsdAttributeDeclView[" + m_aDecl.m_aName + "]";
  }
}
