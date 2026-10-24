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
package com.helger.xpath.ast;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.xdm.XdmQName;

/**
 * A parsed XPath 3.1 expression. All names are resolved to expanded names at
 * parse time against the static context, so evaluation never needs a prefix
 * map. Arrow expressions are desugared by the parser (<code>a =&gt; f(b)</code>
 * becomes the call <code>f(a, b)</code>, <code>a =&gt; $f(b)</code> a dynamic
 * call), so there is no arrow node. The "//" abbreviation is expanded to an
 * explicit <code>descendant-or-self::node()</code> step.
 *
 * @author Philip Helger
 */
public sealed interface IExpr
{
  /** A string, integer, decimal or double literal. */
  record LiteralExpr (@NonNull XdmAtomic value) implements IExpr
  {}

  record VarRefExpr (@NonNull XdmQName name) implements IExpr
  {}

  /** The context item expression <code>.</code> */
  record ContextItemExpr () implements IExpr
  {}

  /** A static function call; an argument may be a {@link PlaceholderExpr}. */
  record FunctionCallExpr (@NonNull XdmQName name, @NonNull List <IExpr> args) implements IExpr
  {}

  /** The argument placeholder <code>?</code> of a partial function application. */
  record PlaceholderExpr () implements IExpr
  {}

  /** A named function reference <code>name#arity</code>. */
  record NamedFunctionRefExpr (@NonNull XdmQName name, int arity) implements IExpr
  {}

  /** One parameter of an inline function. */
  record Param (@NonNull XdmQName name, @Nullable SeqType type)
  {}

  /** <code>function($a as T, ...) as R { body }</code> */
  record InlineFunctionExpr (@NonNull List <Param> params, @Nullable SeqType returnType, @NonNull IExpr body) implements IExpr
  {}

  /** A dynamic function call <code>base(args)</code>; an argument may be a {@link PlaceholderExpr}. */
  record DynamicCallExpr (@NonNull IExpr base, @NonNull List <IExpr> args) implements IExpr
  {}

  /** The comma operator and parenthesised sequences; the empty sequence has no items. */
  record SequenceExpr (@NonNull List <IExpr> items) implements IExpr
  {}

  record BinaryExpr (@NonNull EBinaryOp op, @NonNull IExpr left, @NonNull IExpr right) implements IExpr
  {}

  /** Unary minus ({@code negate}) or plus. */
  record UnaryExpr (boolean negate, @NonNull IExpr operand) implements IExpr
  {}

  record ComparisonExpr (@NonNull EComparisonOp op, @NonNull IExpr left, @NonNull IExpr right) implements IExpr
  {}

  /** <code>union</code>/<code>|</code>, <code>intersect</code> and <code>except</code>. */
  record SetExpr (@NonNull ESetOp op, @NonNull IExpr left, @NonNull IExpr right) implements IExpr
  {
    /** The set operators. */
    public enum ESetOp
    {
      UNION,
      INTERSECT,
      EXCEPT
    }
  }

  record RangeExpr (@NonNull IExpr from, @NonNull IExpr to) implements IExpr
  {}

  /** The string concatenation operator <code>||</code> with two or more operands. */
  record StringConcatExpr (@NonNull List <IExpr> parts) implements IExpr
  {}

  record IfExpr (@NonNull IExpr condition, @NonNull IExpr thenExpr, @NonNull IExpr elseExpr) implements IExpr
  {}

  /** One variable binding of for, let and quantified expressions. */
  record Binding (@NonNull XdmQName name, @Nullable SeqType type, @NonNull IExpr expr)
  {}

  /** <code>for $v in e, ... return body</code> */
  record ForExpr (@NonNull List <Binding> bindings, @NonNull IExpr body) implements IExpr
  {}

  /** <code>let $v := e, ... return body</code> */
  record LetExpr (@NonNull List <Binding> bindings, @NonNull IExpr body) implements IExpr
  {}

  /** <code>some</code> / <code>every</code> */
  record QuantifiedExpr (boolean every, @NonNull List <Binding> bindings, @NonNull IExpr satisfies) implements IExpr
  {}

  /** <code>e1 ! e2 ! e3</code> with at least two steps. */
  record SimpleMapExpr (@NonNull List <IExpr> steps) implements IExpr
  {}

  /**
   * One step of a path. Either an axis step (axis, test, predicates) or, when
   * <code>postfix</code> is set, an arbitrary expression evaluated once per
   * context item (<code>a/f(.)</code>, <code>a/$x</code>, <code>a/(b|c)</code>);
   * then axis and test are <code>null</code> and the predicates apply to its
   * result.
   *
   * @param axisExplicit
   *        The axis was written (<code>child::</code>, <code>@</code>), not
   *        defaulted - XSLT pattern matching needs the difference.
   */
  record StepExpr (@Nullable EAxis axis,
                   boolean axisExplicit,
                   @Nullable NodeTest test,
                   @NonNull List <IExpr> predicates,
                   @Nullable IExpr postfix)
  {}

  /**
   * A path. <code>start</code> is the expression a relative path hangs off (a
   * primary expression with its predicates) or <code>null</code> for a pure
   * location path. <code>absolute</code> marks a leading <code>/</code>; with
   * no steps it is the root expression <code>/</code>.
   */
  record PathExpr (@Nullable IExpr start, boolean absolute, @NonNull List <StepExpr> steps) implements IExpr
  {}

  /** A primary expression followed by predicates: <code>$x[1]</code>. */
  record FilterExpr (@NonNull IExpr primary, @NonNull List <IExpr> predicates) implements IExpr
  {}

  record InstanceOfExpr (@NonNull IExpr operand, @NonNull SeqType type) implements IExpr
  {}

  record TreatExpr (@NonNull IExpr operand, @NonNull SeqType type) implements IExpr
  {}

  /** <code>cast as</code> / <code>castable as</code>; the type is a SingleType (atomic with optional <code>?</code>). */
  record CastExpr (@NonNull IExpr operand, @NonNull SeqType type, boolean castable) implements IExpr
  {}

  /** <code>map { k : v, ... }</code> */
  record MapConstructorExpr (@NonNull List <IExpr> keys, @NonNull List <IExpr> values) implements IExpr
  {}

  /** <code>[a, b]</code> (curly = false, one member per item) or <code>array { e }</code> (curly = true). */
  record ArrayConstructorExpr (@NonNull List <IExpr> items, boolean curly) implements IExpr
  {}

  /**
   * The lookup operator. <code>base</code> is <code>null</code> for the unary
   * form <code>?key</code>, <code>key</code> is <code>null</code> for the
   * wildcard <code>?*</code>.
   */
  record LookupExpr (@Nullable IExpr base, @Nullable IExpr key) implements IExpr
  {}
}
