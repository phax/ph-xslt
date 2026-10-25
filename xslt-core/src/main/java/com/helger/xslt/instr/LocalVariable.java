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
package com.helger.xslt.instr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslNode;

/**
 * A local xsl:variable: evaluates its value and binds it for the following
 * siblings.
 *
 * @author Philip Helger
 */
@Immutable
public final class LocalVariable implements IBindingInstruction
{
  private final VariableDef m_aDef;

  public LocalVariable (@NonNull final VariableDef aDef)
  {
    m_aDef = aDef;
  }

  @NonNull
  public VariableDef getDefinition ()
  {
    return m_aDef;
  }

  @NonNull
  public XsltContext bind (@NonNull final XsltContext aCtx)
  {
    return aCtx.withVariable (m_aDef.getName (), m_aDef.evaluate (aCtx));
  }

  @Nullable
  public XslNode getLocation ()
  {
    return m_aDef.getElement ();
  }
}
