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
package com.helger.xpath.pattern;

import java.util.ArrayDeque;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.xdm.XdmQName;

/**
 * Iterative walker over an expression tree, used for static questions about
 * patterns.
 *
 * @author Philip Helger
 */
@Immutable
final class ExprWalker
{
  @PresentForCodeCoverage
  private static final ExprWalker INSTANCE = new ExprWalker ();

  private ExprWalker ()
  {}

  private static void _pushChildren (@NonNull final IExpr aExpr, @NonNull final ArrayDeque <IExpr> aStack)
  {
    switch (aExpr)
    {
      case final IExpr.LiteralExpr e ->
      {
        // no children
      }
      case final IExpr.VarRefExpr e ->
      {
        // no children
      }
      case final IExpr.ContextItemExpr e ->
      {
        // no children
      }
      case final IExpr.PlaceholderExpr e ->
      {
        // no children
      }
      case final IExpr.NamedFunctionRefExpr e ->
      {
        // no children
      }
      case final IExpr.FunctionCallExpr e ->
      {
        aStack.addAll (e.args ());
      }
      case final IExpr.InlineFunctionExpr e ->
      {
        aStack.push (e.body ());
      }
      case final IExpr.DynamicCallExpr e ->
      {
        aStack.push (e.base ());
        aStack.addAll (e.args ());
      }
      case final IExpr.SequenceExpr e ->
      {
        aStack.addAll (e.items ());
      }
      case final IExpr.BinaryExpr e ->
      {
        aStack.push (e.left ());
        aStack.push (e.right ());
      }
      case final IExpr.UnaryExpr e ->
      {
        aStack.push (e.operand ());
      }
      case final IExpr.ComparisonExpr e ->
      {
        aStack.push (e.left ());
        aStack.push (e.right ());
      }
      case final IExpr.SetExpr e ->
      {
        aStack.push (e.left ());
        aStack.push (e.right ());
      }
      case final IExpr.RangeExpr e ->
      {
        aStack.push (e.from ());
        aStack.push (e.to ());
      }
      case final IExpr.StringConcatExpr e ->
      {
        aStack.addAll (e.parts ());
      }
      case final IExpr.IfExpr e ->
      {
        aStack.push (e.condition ());
        aStack.push (e.thenExpr ());
        aStack.push (e.elseExpr ());
      }
      case final IExpr.ForExpr e ->
      {
        for (final IExpr.Binding aBinding : e.bindings ())
          aStack.push (aBinding.expr ());
        aStack.push (e.body ());
      }
      case final IExpr.LetExpr e ->
      {
        for (final IExpr.Binding aBinding : e.bindings ())
          aStack.push (aBinding.expr ());
        aStack.push (e.body ());
      }
      case final IExpr.QuantifiedExpr e ->
      {
        for (final IExpr.Binding aBinding : e.bindings ())
          aStack.push (aBinding.expr ());
        aStack.push (e.satisfies ());
      }
      case final IExpr.SimpleMapExpr e ->
      {
        aStack.addAll (e.steps ());
      }
      case final IExpr.PathExpr e ->
      {
        if (e.start () != null)
          aStack.push (e.start ());
        for (final IExpr.StepExpr aStep : e.steps ())
        {
          aStack.addAll (aStep.predicates ());
          if (aStep.postfix () != null)
            aStack.push (aStep.postfix ());
        }
      }
      case final IExpr.FilterExpr e ->
      {
        aStack.push (e.primary ());
        aStack.addAll (e.predicates ());
      }
      case final IExpr.InstanceOfExpr e ->
      {
        aStack.push (e.operand ());
      }
      case final IExpr.TreatExpr e ->
      {
        aStack.push (e.operand ());
      }
      case final IExpr.CastExpr e ->
      {
        aStack.push (e.operand ());
      }
      case final IExpr.MapConstructorExpr e ->
      {
        aStack.addAll (e.keys ());
        aStack.addAll (e.values ());
      }
      case final IExpr.ArrayConstructorExpr e ->
      {
        aStack.addAll (e.items ());
      }
      case final IExpr.LookupExpr e ->
      {
        if (e.base () != null)
          aStack.push (e.base ());
        if (e.key () != null)
          aStack.push (e.key ());
      }
    }
  }

  /**
   * @param aExpr
   *        The expression tree.
   * @param aFilter
   *        Tests a function name.
   * @return <code>true</code> if the tree contains a static function call or
   *         a named function reference whose name passes the filter.
   */
  static boolean containsFunction (@NonNull final IExpr aExpr, @NonNull final Predicate <XdmQName> aFilter)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      if (aCur instanceof final IExpr.FunctionCallExpr aCall && aFilter.test (aCall.name ()))
        return true;
      if (aCur instanceof final IExpr.NamedFunctionRefExpr aRef && aFilter.test (aRef.name ()))
        return true;
      _pushChildren (aCur, aStack);
    }
    return false;
  }
}
