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
package com.helger.phoxtrot.xslt.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.tree.XslNode;

/**
 * A dynamic error raised while a lazily bound local variable was evaluated. It
 * carries the error code, description, value and location of the original
 * error (its cause), plus the xsl:try scope the variable was declared in (see
 * {@link TryScope}).
 *
 * @author Philip Helger
 */
public final class LazyVariableException extends XsltException
{
  private final transient TryScope m_aScope;

  private LazyVariableException (@NonNull final XsltException aLocated, @Nullable final TryScope aScope)
  {
    super (aLocated, aLocated.getModuleUri (), aLocated.getLine (), aLocated.getColumn ());
    m_aScope = aScope;
  }

  /**
   * @param aEx
   *        The error of the evaluation.
   * @param aVariable
   *        The xsl:variable element, the location of an error that has none.
   * @param aScope
   *        The xsl:try scope the variable is declared in. May be
   *        <code>null</code>.
   * @return The error to raise.
   */
  @NonNull
  public static LazyVariableException of (@NonNull final XPathException aEx,
                                          @Nullable final XslNode aVariable,
                                          @Nullable final TryScope aScope)
  {
    final XPathException aLocated = XsltException.locate (aEx, aVariable);
    if (aLocated instanceof final XsltException aXEx)
      return new LazyVariableException (aXEx, aScope);
    return new LazyVariableException (new XsltException (aLocated, null, 0, 0), aScope);
  }

  /** @return The xsl:try scope the variable is declared in. May be <code>null</code>. */
  @Nullable
  TryScope getScope ()
  {
    return m_aScope;
  }
}
