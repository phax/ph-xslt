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
package com.helger.xslt.tree;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A node of a stylesheet module after static processing (use-when, shadow
 * attributes): either an element or a text node. Comments and processing
 * instructions of the stylesheet are not represented, adjacent text is merged.
 *
 * @author Philip Helger
 */
public abstract class XslNode
{
  private final XslModule m_aModule;
  private XslElement m_aParent;

  protected XslNode (@NonNull final XslModule aModule)
  {
    m_aModule = aModule;
  }

  /** @return The module this node belongs to. */
  @NonNull
  public final XslModule getModule ()
  {
    return m_aModule;
  }

  /** @return The parent element or <code>null</code> for a module root. */
  @Nullable
  public final XslElement getParent ()
  {
    return m_aParent;
  }

  final void setParent (@Nullable final XslElement aParent)
  {
    m_aParent = aParent;
  }

  /** @return The 1-based source line, 0 if unknown. */
  public abstract int getLine ();

  /** @return The 1-based source column, 0 if unknown. */
  public abstract int getColumn ();
}
