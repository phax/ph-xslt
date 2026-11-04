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
package com.helger.xslt.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.XPathException;

/**
 * The xsl:try evaluations a piece of code runs inside, innermost first: one
 * object per evaluation of an xsl:try body, linked to the enclosing one. A
 * lazily evaluated local variable remembers the scope it was declared in, so
 * that an xsl:try catches its errors only if the variable was declared inside
 * it (XSLT 3.0 section 8.3: the errors caught "do not include errors that
 * occur while evaluating references to variables whose declaration and
 * initialization is outside the xsl:try").
 *
 * @author Philip Helger
 */
@Immutable
public final class TryScope
{
  /** The innermost xsl:try evaluation, kept in template and function calls. */
  public static final XsltStateKey <TryScope> KEY = new XsltStateKey <> ("try-scope", XsltStateKey.EScope.KEPT);

  private final TryScope m_aParent;

  private TryScope (@Nullable final TryScope aParent)
  {
    m_aParent = aParent;
  }

  /**
   * @param aCtx
   *        The context of an xsl:try instruction.
   * @return The context its body is evaluated in, with a new innermost scope.
   */
  @NonNull
  public static XsltContext enter (@NonNull final XsltContext aCtx)
  {
    return aCtx.with (KEY, new TryScope (aCtx.get (KEY)));
  }

  /**
   * @param aDeclared
   *        The scope a variable was declared in. May be <code>null</code> for
   *        outside every xsl:try.
   * @return <code>true</code> if it is this scope or nested inside it.
   */
  private boolean _contains (@Nullable final TryScope aDeclared)
  {
    for (TryScope aCur = aDeclared; aCur != null; aCur = aCur.m_aParent)
      if (aCur == this)
        return true;
    return false;
  }

  /**
   * @param aCtx
   *        The context of the xsl:try body, as returned by
   *        {@link #enter(XsltContext)}.
   * @param aEx
   *        An error raised by the body.
   * @return <code>true</code> if the error comes from a lazily evaluated
   *         variable declared outside this xsl:try, which the xsl:try must not
   *         catch.
   */
  public static boolean isDeclaredOutside (@NonNull final XsltContext aCtx, @NonNull final XPathException aEx)
  {
    final TryScope aThis = aCtx.get (KEY);
    for (Throwable aCur = aEx; aCur != null; aCur = aCur.getCause ())
      if (aCur instanceof final LazyVariableException aLazy && (aThis == null || !aThis._contains (aLazy.getScope ())))
        return true;
    return false;
  }
}
