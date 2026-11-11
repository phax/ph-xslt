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
package com.helger.phoxtrot.xpath.ast;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;

/**
 * Iterative walker over an expression tree, used for static questions about
 * expressions and patterns.
 *
 * @author Philip Helger
 */
@Immutable
public final class ExprWalker
{
  // Functions of the fn namespace that always return an xs:boolean
  private static final ICommonsSet <String> BOOLEAN_FUNCTIONS = new CommonsHashSet <> ("not",
                                                                                       "exists",
                                                                                       "empty",
                                                                                       "boolean",
                                                                                       "true",
                                                                                       "false",
                                                                                       "contains",
                                                                                       "starts-with",
                                                                                       "ends-with",
                                                                                       "matches",
                                                                                       "deep-equal",
                                                                                       "lang");

  // Functions of the fn namespace that read neither the focus nor anything
  // that changes between two calls of one run, with any number of arguments
  private static final ICommonsSet <String> FOCUS_FREE_FUNCTIONS = new CommonsHashSet <> ("true",
                                                                                          "false",
                                                                                          "not",
                                                                                          "boolean",
                                                                                          "exists",
                                                                                          "empty",
                                                                                          "abs",
                                                                                          "ceiling",
                                                                                          "floor",
                                                                                          "round",
                                                                                          "round-half-to-even",
                                                                                          "format-integer",
                                                                                          "format-number",
                                                                                          "concat",
                                                                                          "string-join",
                                                                                          "substring",
                                                                                          "upper-case",
                                                                                          "lower-case",
                                                                                          "translate",
                                                                                          "contains",
                                                                                          "starts-with",
                                                                                          "ends-with",
                                                                                          "substring-before",
                                                                                          "substring-after",
                                                                                          "matches",
                                                                                          "replace",
                                                                                          "tokenize",
                                                                                          "codepoints-to-string",
                                                                                          "string-to-codepoints",
                                                                                          "compare",
                                                                                          "codepoint-equal",
                                                                                          "normalize-unicode",
                                                                                          "encode-for-uri",
                                                                                          "iri-to-uri",
                                                                                          "escape-html-uri",
                                                                                          "contains-token",
                                                                                          "head",
                                                                                          "tail",
                                                                                          "insert-before",
                                                                                          "remove",
                                                                                          "reverse",
                                                                                          "subsequence",
                                                                                          "distinct-values",
                                                                                          "index-of",
                                                                                          "deep-equal",
                                                                                          "zero-or-one",
                                                                                          "one-or-more",
                                                                                          "exactly-one",
                                                                                          "count",
                                                                                          "avg",
                                                                                          "max",
                                                                                          "min",
                                                                                          "sum",
                                                                                          "year-from-dateTime",
                                                                                          "month-from-dateTime",
                                                                                          "day-from-dateTime",
                                                                                          "hours-from-dateTime",
                                                                                          "minutes-from-dateTime",
                                                                                          "seconds-from-dateTime",
                                                                                          "timezone-from-dateTime",
                                                                                          "year-from-date",
                                                                                          "month-from-date",
                                                                                          "day-from-date",
                                                                                          "timezone-from-date",
                                                                                          "hours-from-time",
                                                                                          "minutes-from-time",
                                                                                          "seconds-from-time",
                                                                                          "timezone-from-time",
                                                                                          "years-from-duration",
                                                                                          "months-from-duration",
                                                                                          "days-from-duration",
                                                                                          "hours-from-duration",
                                                                                          "minutes-from-duration",
                                                                                          "seconds-from-duration",
                                                                                          "dateTime",
                                                                                          "adjust-dateTime-to-timezone",
                                                                                          "adjust-date-to-timezone",
                                                                                          "adjust-time-to-timezone",
                                                                                          "current-dateTime",
                                                                                          "current-date",
                                                                                          "current-time",
                                                                                          "implicit-timezone",
                                                                                          "default-collation",
                                                                                          "default-language",
                                                                                          "static-base-uri",
                                                                                          "QName",
                                                                                          "error");
  // Functions of the fn namespace that read the context item without an
  // argument, but are focus-free with at least one
  private static final ICommonsSet <String> FOCUS_FREE_WITH_ARGUMENT = new CommonsHashSet <> ("string",
                                                                                              "data",
                                                                                              "number",
                                                                                              "string-length",
                                                                                              "normalize-space",
                                                                                              "name",
                                                                                              "local-name",
                                                                                              "namespace-uri");

  @PresentForCodeCoverage
  private static final ExprWalker INSTANCE = new ExprWalker ();

  private ExprWalker ()
  {}

  /**
   * Push the direct operand expressions of a node onto the work stack: the
   * arguments of calls, the body of an inline function, the binding
   * expressions and the body of <code>for</code>/<code>let</code>/quantified
   * expressions, the start, predicates and postfix expressions of the steps of
   * a path, the primary and predicates of a filter expression, and so on. The
   * <code>fused</code> step of a {@link IExpr.StepExpr} is not visited, as it
   * repeats the following step. The order is not significant for the callers,
   * which only ask whether any node matches.
   */
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
  public static boolean containsFunction (@NonNull final IExpr aExpr, @NonNull final Predicate <XdmQName> aFilter)
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

  /**
   * @param aName
   *        A function name.
   * @return <code>true</code> for <code>fn:position</code> and
   *         <code>fn:last</code>.
   */
  public static boolean isPositionOrLast (@NonNull final XdmQName aName)
  {
    return XdmConstants.NS_FN.equals (aName.namespaceUri ()) &&
           (aName.localName ().equals ("position") || aName.localName ().equals ("last"));
  }

  /**
   * @return <code>true</code> if the expression can only evaluate to a boolean
   *         or a node sequence, never to a number.
   */
  private static boolean _isBooleanOrNodes (@NonNull final IExpr aExpr)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.ComparisonExpr e ->
        {
          // always a boolean
        }
        case final IExpr.BinaryExpr e ->
        {
          if (e.op () != EBinaryOp.AND && e.op () != EBinaryOp.OR)
            return false;
        }
        case final IExpr.SetExpr e ->
        {
          aStack.push (e.left ());
          aStack.push (e.right ());
        }
        case final IExpr.PathExpr e ->
        {
          if (e.start () != null)
            return false;
          for (final IExpr.StepExpr aStep : e.steps ())
            if (aStep.postfix () != null)
              return false;
        }
        case final IExpr.FunctionCallExpr e ->
        {
          if (!XdmConstants.NS_FN.equals (e.name ().namespaceUri ()) ||
              !BOOLEAN_FUNCTIONS.contains (e.name ().localName ()))
            return false;
        }
        default ->
        {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * A predicate passing this test selects the same nodes whatever the context
   * position and size are, and is never read as a position: it can only be a
   * boolean or a node sequence and does not call or reference
   * <code>fn:position</code>, <code>fn:last</code> or
   * <code>fn:function-lookup</code> (which can return either) anywhere
   * inside.
   *
   * @param aPredicate
   *        The predicate expression.
   * @return <code>true</code> if it is provably focus-free and non-numeric.
   */
  public static boolean isFocusFreeBoolean (@NonNull final IExpr aPredicate)
  {
    return _isBooleanOrNodes (aPredicate) &&
           !containsFunction (aPredicate,
                              aName -> isPositionOrLast (aName) ||
                                       (XdmConstants.NS_FN.equals (aName.namespaceUri ()) &&
                                        aName.localName ().equals ("function-lookup")));
  }

  /**
   * @return <code>true</code> if a static call of this name and arity is one
   *         of the built-in functions that read neither the focus nor anything
   *         that differs between two calls of one run: the listed functions of
   *         the fn namespace, all of the math namespace and the constructor
   *         functions of the xs namespace.
   */
  private static boolean _isFocusFreeFunction (@NonNull final XdmQName aName, final int nArity)
  {
    final String sNamespaceUri = aName.namespaceUri ();
    if (XdmConstants.NS_FN.equals (sNamespaceUri))
      return FOCUS_FREE_FUNCTIONS.contains (aName.localName ()) ||
             (nArity >= 1 && FOCUS_FREE_WITH_ARGUMENT.contains (aName.localName ()));
    if (FunctionLibrary.NS_MATH.equals (sNamespaceUri))
      return true;
    return XdmConstants.NS_XS.equals (sNamespaceUri) && nArity == 1;
  }

  /**
   * Is the value of an expression the same whatever the focus is, and the same
   * on every evaluation within one run? This fails closed: it is
   * <code>true</code> only if the tree contains no context item expression, no
   * unary lookup, no path other than one hanging off a start expression through
   * axis steps without predicates (<code>$x/a/@b</code>), no function item
   * (inline function, named function reference, dynamic call, argument
   * placeholder), and only static calls of the built-in functions of
   * {@link #_isFocusFreeFunction(XdmQName, int)} that the caller also accepts.
   * Variables are fine, whether bound outside or inside the expression. An
   * expression inside a nested predicate is held to the same rules, so a nested
   * <code>$s[. gt 1]</code> makes the whole expression not focus-free.
   *
   * @param aExpr
   *        The expression, e.g. a predicate.
   * @param aCallFilter
   *        Tests each static function call that passes the name check, e.g.
   *        that it resolves to the built-in implementation.
   * @return <code>true</code> if the expression is provably focus-free and
   *         deterministic within a run.
   */
  public static boolean isFocusIndependent (@NonNull final IExpr aExpr,
                                            @NonNull final Predicate <IExpr.FunctionCallExpr> aCallFilter)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.ContextItemExpr e ->
        {
          return false;
        }
        case final IExpr.PlaceholderExpr e ->
        {
          return false;
        }
        case final IExpr.NamedFunctionRefExpr e ->
        {
          return false;
        }
        case final IExpr.InlineFunctionExpr e ->
        {
          return false;
        }
        case final IExpr.DynamicCallExpr e ->
        {
          return false;
        }
        case final IExpr.FunctionCallExpr e ->
        {
          if (!_isFocusFreeFunction (e.name (), e.args ().size ()) || !aCallFilter.test (e))
            return false;
          aStack.addAll (e.args ());
        }
        case final IExpr.PathExpr e ->
        {
          if (e.start () == null || e.absolute ())
            return false;
          for (final IExpr.StepExpr aStep : e.steps ())
            if (aStep.postfix () != null || !aStep.predicates ().isEmpty ())
              return false;
          aStack.push (e.start ());
        }
        case final IExpr.LookupExpr e ->
        {
          if (e.base () == null)
            return false;
          _pushChildren (e, aStack);
        }
        default -> _pushChildren (aCur, aStack);
      }
    }
    return true;
  }

  /**
   * Is the value of an expression determined by the context item, the values
   * of the variables it references and the static context alone, the same on
   * every evaluation within one run with these? Like
   * {@link #isFocusIndependent(IExpr, Predicate)} this fails closed, but the
   * context item and paths of any shape are allowed: it is <code>true</code>
   * only if the tree contains no unary or postfix lookup, no function item
   * (inline function, named function reference, dynamic call, argument
   * placeholder), no call of <code>fn:position</code> or <code>fn:last</code>
   * (also not in a nested predicate), and only static calls of the built-in
   * functions of {@link #_isFocusFreeFunction(XdmQName, int)} or of the
   * functions that read the context item without an argument (such as
   * <code>fn:normalize-space()</code>), that the caller also accepts.
   *
   * @param aExpr
   *        The expression.
   * @param aCallFilter
   *        Tests each static function call that passes the name check, e.g.
   *        that it resolves to the built-in implementation.
   * @return <code>true</code> if the expression is provably determined by the
   *         context item and its variables.
   */
  public static boolean isDeterminedByContextItem (@NonNull final IExpr aExpr,
                                                   @NonNull final Predicate <IExpr.FunctionCallExpr> aCallFilter)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.PlaceholderExpr e ->
        {
          return false;
        }
        case final IExpr.NamedFunctionRefExpr e ->
        {
          return false;
        }
        case final IExpr.InlineFunctionExpr e ->
        {
          return false;
        }
        case final IExpr.DynamicCallExpr e ->
        {
          return false;
        }
        case final IExpr.LookupExpr e ->
        {
          return false;
        }
        case final IExpr.FunctionCallExpr e ->
        {
          final int nArity = e.args ().size ();
          final boolean bKnown = _isFocusFreeFunction (e.name (), nArity) ||
                                 (nArity == 0 &&
                                  XdmConstants.NS_FN.equals (e.name ().namespaceUri ()) &&
                                  FOCUS_FREE_WITH_ARGUMENT.contains (e.name ().localName ()));
          if (!bKnown || !aCallFilter.test (e))
            return false;
          aStack.addAll (e.args ());
        }
        default -> _pushChildren (aCur, aStack);
      }
    }
    return true;
  }

  /**
   * @param aExpr
   *        The expression.
   * @return The names of all variables referenced in the tree, also of those
   *         bound inside it: a superset of its free variables. Never
   *         <code>null</code>.
   */
  @NonNull
  public static ICommonsSet <XdmQName> getAllVariableReferences (@NonNull final IExpr aExpr)
  {
    final ICommonsSet <XdmQName> ret = new CommonsHashSet <> ();
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      if (aCur instanceof final IExpr.VarRefExpr aVar)
        ret.add (aVar.name ());
      _pushChildren (aCur, aStack);
    }
    return ret;
  }

  /**
   * The local variables in scope of a subexpression, innermost first: a
   * persistent list shared by the subexpressions of one scope.
   */
  private record LocalScope (@NonNull XdmQName name, @Nullable LocalScope next)
  {
    static boolean contains (@Nullable final LocalScope aScope, @NonNull final XdmQName aName)
    {
      for (LocalScope aCur = aScope; aCur != null; aCur = aCur.next ())
        if (aCur.name ().equals (aName))
          return true;
      return false;
    }
  }

  /** A subexpression still to visit, with the local variables in its scope. */
  private record ScopedExpr (@NonNull IExpr expr, @Nullable LocalScope scope)
  {}

  /**
   * Push the binding expressions of a <code>for</code>, <code>let</code> or
   * quantified expression, each with the variables of the bindings before it
   * in scope, and its body with all of them, so that the bindings are popped
   * first and in order.
   */
  private static void _pushBindings (@NonNull final List <IExpr.Binding> aBindings,
                                     @NonNull final IExpr aBody,
                                     @Nullable final LocalScope aOuter,
                                     @NonNull final ArrayDeque <ScopedExpr> aStack)
  {
    final LocalScope [] aScopes = new LocalScope [aBindings.size () + 1];
    aScopes[0] = aOuter;
    for (int i = 0; i < aBindings.size (); ++i)
      aScopes[i + 1] = new LocalScope (aBindings.get (i).name (), aScopes[i]);
    aStack.push (new ScopedExpr (aBody, aScopes[aBindings.size ()]));
    for (int i = aBindings.size () - 1; i >= 0; --i)
      aStack.push (new ScopedExpr (aBindings.get (i).expr (), aScopes[i]));
  }

  /**
   * The free variables of an expression: the names of the variables it
   * references that it does not bind itself, i.e. that refer to the
   * variables of the static context. A variable bound by
   * <code>for</code>, <code>let</code>, <code>some</code>/<code>every</code>
   * or as parameter of an inline function is local in the expressions in its
   * scope (the return or satisfies expression and the following bindings,
   * the function body); a reference outside that scope, or to the same name
   * before it is bound, is free. Walks the tree iteratively.
   *
   * @param aExpr
   *        The expression.
   * @return The free variable names in the order of their first reference.
   *         Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsOrderedSet <XdmQName> getFreeVariables (@NonNull final IExpr aExpr)
  {
    final ICommonsOrderedSet <XdmQName> ret = new CommonsLinkedHashSet <> ();
    final ArrayDeque <ScopedExpr> aStack = new ArrayDeque <> ();
    final ArrayDeque <IExpr> aChildren = new ArrayDeque <> ();
    aStack.push (new ScopedExpr (aExpr, null));
    while (!aStack.isEmpty ())
    {
      final ScopedExpr aCur = aStack.pop ();
      final LocalScope aScope = aCur.scope ();
      switch (aCur.expr ())
      {
        case final IExpr.VarRefExpr e ->
        {
          if (!LocalScope.contains (aScope, e.name ()))
            ret.add (e.name ());
        }
        case final IExpr.ForExpr e -> _pushBindings (e.bindings (), e.body (), aScope, aStack);
        case final IExpr.LetExpr e -> _pushBindings (e.bindings (), e.body (), aScope, aStack);
        case final IExpr.QuantifiedExpr e -> _pushBindings (e.bindings (), e.satisfies (), aScope, aStack);
        case final IExpr.InlineFunctionExpr e ->
        {
          LocalScope aInner = aScope;
          for (final IExpr.Param aParam : e.params ())
            aInner = new LocalScope (aParam.name (), aInner);
          aStack.push (new ScopedExpr (e.body (), aInner));
        }
        default ->
        {
          // Moved over from the end, so that they are popped in the same order as from the stack _pushChildren filled
          _pushChildren (aCur.expr (), aChildren);
          while (!aChildren.isEmpty ())
            aStack.push (new ScopedExpr (aChildren.pollLast (), aScope));
        }
      }
    }
    return ret;
  }
}
