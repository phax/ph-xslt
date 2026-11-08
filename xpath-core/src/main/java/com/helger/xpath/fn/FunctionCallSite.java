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
package com.helger.xpath.fn;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.xdm.XdmQName;

/**
 * The resolution cache of one static function call site, that is of one
 * function name and arity (a site must not be used for another name or arity):
 * the function definition that
 * {@link FunctionLibrary#getFunction(XdmQName, int)} gave for the last library
 * the call was evaluated with, together with that library and its modification
 * stamp (the sum of the change counters of the library and its parents). A call
 * with the same library and an unchanged stamp reuses the definition without
 * building the lookup key; any other call (another library, e.g. the function
 * scope of another package, or a registration in the library or a parent since
 * the last lookup) looks the function up again and replaces the entry. The
 * result is therefore always the one of a direct lookup.
 * <p>
 * Safe for concurrent use: the entry is immutable and published through a
 * volatile field; concurrent calls may each look up and store an entry, all
 * with correct results.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class FunctionCallSite
{
  /** One cached resolution; a <code>null</code> definition is cached as well. */
  private record Entry (@NonNull FunctionLibrary library, long stamp, @Nullable FunctionDef def)
  {}

  private volatile Entry m_aEntry;
  // Whether the arguments of the call contain a placeholder: 0 = not known
  // yet, 1 = no, 2 = yes
  private volatile int m_nPlaceholder;

  /**
   * Does the call of this site have an argument placeholder (a partial
   * function application)? The answer is computed from the arguments on the
   * first call and kept, as the arguments of a call site never change.
   * Concurrent first calls may compute it twice, with the same result.
   *
   * @param aArgs
   *        The argument expressions of the call of this site.
   * @return <code>true</code> if any argument is <code>?</code>.
   */
  public boolean hasPlaceholder (@NonNull final List <IExpr> aArgs)
  {
    final int nKnown = m_nPlaceholder;
    if (nKnown != 0)
      return nKnown == 2;
    boolean bFound = false;
    for (final IExpr aArg : aArgs)
      if (aArg instanceof IExpr.PlaceholderExpr)
      {
        bFound = true;
        break;
      }
    m_nPlaceholder = bFound ? 2 : 1;
    return bFound;
  }

  /**
   * Resolve the function of this call site: the cached definition if the
   * entry is for the same library instance with the same modification stamp,
   * otherwise the result of {@link FunctionLibrary#getFunction(XdmQName, int)},
   * which is then cached. The stamp is read before the lookup.
   *
   * @param aLib
   *        The function library of the static context of the evaluation.
   * @param aName
   *        Expanded function name of the call.
   * @param nArity
   *        Number of arguments of the call.
   * @return <code>null</code> if there is no such function.
   */
  @Nullable
  public FunctionDef getFunction (@NonNull final FunctionLibrary aLib, @NonNull final XdmQName aName, final int nArity)
  {
    final long nStamp = aLib.getModificationStamp ();
    final Entry aEntry = m_aEntry;
    if (aEntry != null && aEntry.library () == aLib && aEntry.stamp () == nStamp)
      return aEntry.def ();
    final FunctionDef ret = aLib.getFunction (aName, nArity);
    m_aEntry = new Entry (aLib, nStamp, ret);
    return ret;
  }

  /**
   * All call sites are equal, so that the site does not take part in the
   * equality of the expression node that holds it.
   */
  @Override
  public boolean equals (final Object o)
  {
    return o instanceof FunctionCallSite;
  }

  @Override
  public int hashCode ()
  {
    return 0;
  }

  @Override
  public String toString ()
  {
    return "FunctionCallSite";
  }
}
