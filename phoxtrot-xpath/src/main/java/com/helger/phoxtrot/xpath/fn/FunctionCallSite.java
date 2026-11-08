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
package com.helger.phoxtrot.xpath.fn;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.phoxtrot.xpath.value.XdmSequence;

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
 * The site also keeps the arguments that are literals, converted to the
 * parameter types of one resolved definition (see
 * {@link #getConvertedLiterals(FunctionDef, boolean)}), so that a literal is
 * not converted again on every call.
 * <p>
 * Safe for concurrent use: the entries are immutable and published through
 * volatile fields; concurrent calls may each look up and store an entry, all
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

  /**
   * The literal arguments of a call converted to the parameter types of one
   * function definition, for one setting of the XPath 1.0 compatibility mode.
   * Immutable.
   */
  public static final class ConvertedLiterals
  {
    private final FunctionDef m_aDef;
    private final boolean m_bXPath10Compatibility;
    private final XdmSequence [] m_aValues;

    /**
     * @param aDef
     *        The function definition whose parameter types the values were
     *        converted to.
     * @param bXPath10Compatibility
     *        The XPath 1.0 compatibility mode of the conversion.
     * @param aValues
     *        One entry per argument: the converted value of a literal, or
     *        <code>null</code> for an argument that is no literal or whose
     *        conversion failed (it is then converted on every call, raising
     *        the error at its time). The array is copied.
     */
    public ConvertedLiterals (@NonNull final FunctionDef aDef,
                              final boolean bXPath10Compatibility,
                              @NonNull final XdmSequence [] aValues)
    {
      m_aDef = aDef;
      m_bXPath10Compatibility = bXPath10Compatibility;
      m_aValues = aValues.clone ();
    }

    /**
     * @param nIndex
     *        0-based argument index.
     * @return The converted value of the literal argument at the index, or
     *         <code>null</code> if the argument must be evaluated and converted
     *         on the call.
     */
    @Nullable
    public XdmSequence get (final int nIndex)
    {
      return m_aValues[nIndex];
    }
  }

  private volatile Entry m_aEntry;
  // Whether the arguments of the call contain a placeholder: 0 = not known
  // yet, 1 = no, 2 = yes
  private volatile int m_nPlaceholder;
  // Whether the arguments of the call contain a literal: 0 = not known
  // yet, 1 = no, 2 = yes
  private volatile int m_nLiteral;
  private volatile ConvertedLiterals m_aConvertedLiterals;

  /**
   * Is any argument of the call of this site a literal? Computed on the first
   * call and kept, like {@link #hasPlaceholder(List)}.
   *
   * @param aArgs
   *        The argument expressions of the call of this site.
   * @return <code>true</code> if any argument is a {@link IExpr.LiteralExpr}.
   */
  public boolean hasLiteralArgument (@NonNull final List <IExpr> aArgs)
  {
    final int nKnown = m_nLiteral;
    if (nKnown != 0)
      return nKnown == 2;
    boolean bFound = false;
    for (final IExpr aArg : aArgs)
      if (aArg instanceof IExpr.LiteralExpr)
      {
        bFound = true;
        break;
      }
    m_nLiteral = bFound ? 2 : 1;
    return bFound;
  }

  /**
   * The converted literal arguments stored last, if they were converted for
   * this function definition (by identity: a re-resolved definition after a
   * library change needs a new conversion) and this compatibility mode.
   *
   * @param aDef
   *        The definition the call resolved to.
   * @param bXPath10Compatibility
   *        The XPath 1.0 compatibility mode of the static context.
   * @return <code>null</code> if nothing matching is stored.
   */
  @Nullable
  public ConvertedLiterals getConvertedLiterals (@NonNull final FunctionDef aDef, final boolean bXPath10Compatibility)
  {
    final ConvertedLiterals ret = m_aConvertedLiterals;
    if (ret != null && ret.m_aDef == aDef && ret.m_bXPath10Compatibility == bXPath10Compatibility)
      return ret;
    return null;
  }

  /**
   * Store converted literal arguments, replacing the previous ones.
   *
   * @param aConverted
   *        The converted literals.
   */
  public void setConvertedLiterals (@NonNull final ConvertedLiterals aConverted)
  {
    m_aConvertedLiterals = aConverted;
  }

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
