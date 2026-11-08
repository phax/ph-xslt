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
package com.helger.phoxtrot.xslt.instr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.runtime.LazyVariableException;
import com.helger.phoxtrot.xslt.runtime.TryScope;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.XslNode;

/**
 * A local xsl:variable: binds its value for the following siblings. The value
 * is evaluated on first use, in the context of the declaration, and only then
 * (as by Saxon; XPath 3.1 section 2.3.4 leaves the order of evaluation open):
 * a variable no path uses raises no error. The required type ("as") is checked
 * at that point as well, and an error is located at the xsl:variable element
 * unless it has a location of its own.
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

  /**
   * Bind the variable lazily: the returned context maps the name to a
   * supplier that evaluates the definition in <code>aCtx</code> (the context
   * at the declaration, not at the first use) when the value is first needed.
   * The innermost xsl:try scope at the time of binding is captured, so that a
   * dynamic error raised later by the evaluation is wrapped in a
   * {@link LazyVariableException} that carries this scope and is located at
   * the xsl:variable element if it has no location of its own.
   *
   * @param aCtx
   *        The context at the declaration.
   * @return The context for the following siblings, with the variable bound.
   */
  @NonNull
  public XsltContext bind (@NonNull final XsltContext aCtx)
  {
    final TryScope aScope = aCtx.get (TryScope.KEY);
    return aCtx.withLazyVariable (m_aDef.getName (), () -> {
      try
      {
        return m_aDef.evaluate (aCtx);
      }
      catch (final XPathException ex)
      {
        throw LazyVariableException.of (ex, m_aDef.getElement (), aScope);
      }
    });
  }

  @Nullable
  public XslNode getLocation ()
  {
    return m_aDef.getElement ();
  }
}
