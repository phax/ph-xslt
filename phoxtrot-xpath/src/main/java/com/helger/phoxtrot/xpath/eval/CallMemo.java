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
package com.helger.phoxtrot.xpath.eval;

import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Memoisation of static calls of built-in functions whose value is determined by the context item
 * and the variables they reference (loop-invariant hoisting, evaluated on first use): such a call
 * in a predicate or in the body of a <code>for</code>, <code>some</code> or <code>every</code>
 * expression is computed once and reused while neither the bindings of its variables nor (if it
 * reads the focus) the context item change, e.g. <code>normalize-space($item/a/b)</code> in the
 * predicate of <code>//x[normalize-space(c) = normalize-space($item/a/b)]</code>, or
 * <code>normalize-space(a/b)</code> in <code>some $p in //c satisfies normalize-space($p) =
 * normalize-space(a/b)</code>.
 * <ul>
 * <li>Only calls that {@link ExprWalker#isDeterminedByContextItem(IExpr, java.util.function.Predicate)}
 * accepts, with every static call inside resolving to the implementation of
 * {@link FunctionLibrary#getDefault()}, and with an argument that is more than a literal, a
 * variable or the context item (otherwise the lookup costs as much as the call).</li>
 * <li>A call that does not read the focus is reused for the same bindings of its variables and the
 * same static context, wherever it is evaluated. A call that reads the focus only within the
 * innermost loop and with the loop's context item ({@link DynamicContext#getLoopFocus()}), where
 * the focus does not change between iterations; in a predicate inside the loop the focus is
 * another item and nothing is looked up.</li>
 * <li>One value is kept per call expression and run (the last one); an error is not kept, so the
 * next evaluation raises it again.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@Immutable
final class CallMemo
{
  /** What is known about one call site, for one function library state. */
  static final class Plan
  {
    private final boolean m_bEligible;
    private final boolean m_bUsesFocus;
    private final XdmQName [] m_aVariables;

    private Plan (final boolean bEligible, final boolean bUsesFocus, @NonNull final XdmQName [] aVariables)
    {
      m_bEligible = bEligible;
      m_bUsesFocus = bUsesFocus;
      m_aVariables = aVariables;
    }
  }

  /** The last value of a call, with what it was computed for. */
  private record Entry (@NonNull StaticContext staticContext, @Nullable IXdmItem item, @NonNull Object [] tokens, @NonNull XdmSequence value)
  {}

  private static final XdmQName [] NO_VARIABLES = new XdmQName [0];
  /** The plan of a call that is never memoised. */
  private static final Plan NO_PLAN = new Plan (false, false, NO_VARIABLES);

  private CallMemo ()
  {}

  private static boolean _isBuiltIn (final IExpr.@NonNull FunctionCallExpr aCall, @NonNull final FunctionLibrary aLibrary)
  {
    final int nArity = aCall.args ().size ();
    final FunctionDef aDef = aCall.site ().getFunction (aLibrary, aCall.name (), nArity);
    if (aDef == null)
      return false;
    final FunctionDef aDefault = FunctionLibrary.getDefault ().getFunction (aCall.name (), nArity);
    return aDefault != null && aDefault.getImpl () == aDef.getImpl ();
  }

  /** An argument that costs more than a lookup: anything but a literal, a variable or <code>.</code>. */
  private static boolean _hasWork (final IExpr.@NonNull FunctionCallExpr aCall)
  {
    for (final IExpr aArg : aCall.args ())
      if (!(aArg instanceof IExpr.LiteralExpr) && !(aArg instanceof IExpr.VarRefExpr) && !(aArg instanceof IExpr.ContextItemExpr))
        return true;
    return false;
  }

  @NonNull
  private static Plan _createPlan (final IExpr.@NonNull FunctionCallExpr aCall, @NonNull final FunctionLibrary aLibrary)
  {
    if (aCall.args ().isEmpty () ||
        !_hasWork (aCall) ||
        aCall.site ().hasPlaceholder (aCall.args ()) ||
        !ExprWalker.isDeterminedByContextItem (aCall, x -> _isBuiltIn (x, aLibrary)))
      return NO_PLAN;
    final boolean bUsesFocus = !ExprWalker.isFocusIndependent (aCall, x -> _isBuiltIn (x, aLibrary));
    final ICommonsSet <XdmQName> aVariables = ExprWalker.getAllVariableReferences (aCall);
    return new Plan (true, bUsesFocus, aVariables.toArray (new XdmQName [aVariables.size ()]));
  }

  /**
   * @param aCall
   *        The call.
   * @param aCtx
   *        The dynamic context of its evaluation.
   * @return The plan if the call is memoised in this context, else <code>null</code>.
   */
  @Nullable
  static Plan getApplicablePlan (final IExpr.@NonNull FunctionCallExpr aCall, @NonNull final DynamicContext aCtx)
  {
    final Plan aPlan = (Plan) aCall.site ().getMemoPlan (aCtx.getStaticContext ().getFunctionLibrary (), x -> _createPlan (aCall, x));
    if (!aPlan.m_bEligible)
      return null;
    if (aPlan.m_bUsesFocus)
    {
      // Only where the focus stays the same between evaluations: the loop body at its own focus
      final IXdmItem aItem = aCtx.getContextItem ();
      if (aItem == null || aItem != aCtx.getLoopFocus ())
        return null;
    }
    return aPlan;
  }

  /**
   * Evaluate a call with the memo: the kept value if it was computed for the same static context,
   * variable bindings and (if the call reads the focus) context item, otherwise the evaluation,
   * which is then kept.
   *
   * @param aPlan
   *        The plan from {@link #getApplicablePlan(IExpr.FunctionCallExpr, DynamicContext)}.
   * @param aCall
   *        The call.
   * @param aCtx
   *        The dynamic context.
   * @param aEval
   *        Evaluates the call.
   * @return The value of the call.
   */
  @NonNull
  static XdmSequence evaluate (@NonNull final Plan aPlan,
                               final IExpr.@NonNull FunctionCallExpr aCall,
                               @NonNull final DynamicContext aCtx,
                               @NonNull final Supplier <XdmSequence> aEval)
  {
    final XdmQName [] aVariables = aPlan.m_aVariables;
    final Object [] aTokens = new Object [aVariables.length];
    for (int i = 0; i < aVariables.length; ++i)
      aTokens[i] = aCtx.getVariableToken (aVariables[i]);
    final StaticContext aStatic = aCtx.getStaticContext ();
    final IXdmItem aItem = aPlan.m_bUsesFocus ? aCtx.getContextItem () : null;
    final Map <Object, Object> aMemo = aCtx.getRunMemo ();
    if (aMemo.get (aCall) instanceof final Entry aEntry && aEntry.staticContext () == aStatic && aEntry.item () == aItem)
    {
      boolean bSame = true;
      for (int i = 0; i < aTokens.length; ++i)
        if (aEntry.tokens ()[i] != aTokens[i])
        {
          bSame = false;
          break;
        }
      if (bSame)
        return aEntry.value ();
    }
    final XdmSequence ret = aEval.get ();
    aMemo.put (aCall, new Entry (aStatic, aItem, aTokens, ret));
    return ret;
  }
}
