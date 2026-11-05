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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.ExprWalker;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * The implementation of {@link XsltPattern} created by
 * {@link XsltPatternParser}. Immutable and thread-safe.
 * <p>
 * Dynamic errors during matching make the item not match (XSLT 3.0 section
 * 5.5.4), except a circularity (XTDE0640), a limit violation (XPDY0130) and a
 * cancellation, which propagate.
 *
 * @author Philip Helger
 */
@Immutable
public final class CompiledXsltPattern implements XsltPattern
{
  private final String m_sSource;
  private final IExpr m_aExpr;
  private final double m_dDefaultPriority;
  private final List <CompiledXsltPattern> m_aAlternatives;
  private final Set <IExpr> m_aPositionalPredicates;

  /**
   * Create the pattern and collect, once, its positional predicates (see
   * {@link #_collectPositionalPredicates(IExpr, Set)}) for the matcher.
   *
   * @param sSource
   *        The pattern text as written.
   * @param aExpr
   *        The parsed pattern.
   * @param dDefaultPriority
   *        The default priority; for a top-level union the maximum of the
   *        alternatives.
   * @param aAlternatives
   *        The patterns of the top-level union alternatives, empty if there
   *        is no top-level union. Taken over, not copied.
   */
  CompiledXsltPattern (@NonNull final String sSource,
                       @NonNull final IExpr aExpr,
                       final double dDefaultPriority,
                       @NonNull final List <CompiledXsltPattern> aAlternatives)
  {
    m_sSource = sSource;
    m_aExpr = aExpr;
    m_dDefaultPriority = dDefaultPriority;
    m_aAlternatives = aAlternatives;
    final Set <IExpr> aPositional = Collections.newSetFromMap (new IdentityHashMap <> ());
    _collectPositionalPredicates (aExpr, aPositional);
    m_aPositionalPredicates = Collections.unmodifiableSet (aPositional);
  }

  /**
   * Collect the axis-step predicates of the pattern that may depend on the
   * position or size of the focus.
   * <p>
   * The pattern tree is walked iteratively through set operators and the
   * postfix expressions of parenthesised steps; a predicate is collected
   * (by identity) if it calls <code>fn:position</code> or
   * <code>fn:last</code> anywhere.
   */
  private static void _collectPositionalPredicates (@NonNull final IExpr aPattern, @NonNull final Set <IExpr> aOut)
  {
    final ICommonsList <IExpr> aStack = new CommonsArrayList <> (aPattern);
    while (aStack.isNotEmpty ())
    {
      final IExpr aCur = aStack.removeLast ();
      if (aCur instanceof final IExpr.SetExpr aSet)
      {
        aStack.add (aSet.left ());
        aStack.add (aSet.right ());
      }
      else
        if (aCur instanceof final IExpr.PathExpr aPath)
          for (final IExpr.StepExpr aStep : aPath.steps ())
            if (aStep.postfix () != null)
              aStack.add (aStep.postfix ());
            else
              for (final IExpr aPred : aStep.predicates ())
                if (ExprWalker.containsFunction (aPred, ExprWalker::isPositionOrLast))
                  aOut.add (aPred);
    }
  }

  @NonNull
  public String getSource ()
  {
    return m_sSource;
  }

  /**
   * @return The parsed pattern. A predicate pattern is a
   *         {@link IExpr.ContextItemExpr} or a {@link IExpr.FilterExpr} on it;
   *         a selection pattern consists of {@link IExpr.SetExpr} nodes over
   *         {@link IExpr.PathExpr} nodes, where a parenthesised pattern is a
   *         step with a postfix. See
   *         {@link com.helger.xpath.parser.XPathParser#parsePattern(String, com.helger.xpath.context.StaticContext)}.
   */
  @NonNull
  public IExpr getPatternExpr ()
  {
    return m_aExpr;
  }

  /** @return <code>true</code> for a predicate pattern (<code>.</code> with zero or more predicates). */
  public boolean isPredicatePattern ()
  {
    return m_aExpr instanceof IExpr.ContextItemExpr ||
           (m_aExpr instanceof final IExpr.FilterExpr aFilter && aFilter.primary () instanceof IExpr.ContextItemExpr);
  }

  /**
   * @param aFilter
   *        Tests a function name.
   * @return <code>true</code> if the pattern, including its predicates,
   *         contains a static call of (or a named reference to) a function
   *         whose name passes the filter. Used for static checks such as
   *         XTSE1060 (<code>current-group()</code> in a pattern).
   */
  public boolean containsFunction (@NonNull final Predicate <XdmQName> aFilter)
  {
    ValueEnforcer.notNull (aFilter, "Filter");
    return ExprWalker.containsFunction (m_aExpr, aFilter);
  }

  /**
   * Match an item (XSLT 3.0 section 5.5.3). The candidate becomes the current
   * item. A plain <code>.</code> matches every item; a predicate pattern
   * <code>.[...]</code> matches if the filter expression, evaluated with the
   * item as context item at position 1 of 1, is non-empty. A selection pattern
   * never matches an item that is not a node, and otherwise is decided by
   * <code>PatternMatcher.matchesNode</code>.
   * <p>
   * A dynamic error makes the item not match (XSLT 3.0 section 5.5.4), except
   * XTDE0640 and XPDY0130, which are rethrown, and a pending cancellation,
   * which {@link DynamicContext#checkInterrupt()} raises instead. A stack
   * overflow becomes XPDY0130.
   *
   * @param aItem
   *        The candidate item.
   * @param aCtx
   *        Dynamic context for predicates and variable references.
   * @return <code>true</code> if the item matches.
   */
  public boolean matches (@NonNull final IXdmItem aItem, @NonNull final DynamicContext aCtx)
  {
    ValueEnforcer.notNull (aItem, "Item");
    ValueEnforcer.notNull (aCtx, "Ctx");
    try
    {
      final DynamicContext aRealCtx = aCtx.withCurrentItem (aItem);
      if (m_aExpr instanceof IExpr.ContextItemExpr)
        return true;
      if (m_aExpr instanceof final IExpr.FilterExpr aFilter && aFilter.primary () instanceof IExpr.ContextItemExpr)
        return !XPathEvaluator.evaluate (aFilter, aRealCtx.withFocus (aItem, 1, 1)).isEmpty ();
      if (!(aItem instanceof final IXdmNode aNode))
        return false;
      return new PatternMatcher (aRealCtx, aNode, m_aPositionalPredicates).matchesNode (m_aExpr, aNode);
    }
    catch (final XPathException ex)
    {
      final String sCode = ex.getCode ();
      if ("XTDE0640".equals (sCode) || "XPDY0130".equals (sCode))
        throw ex;
      // A cancellation must not be mistaken for a non-match
      aCtx.checkInterrupt ();
      return false;
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "Pattern matching is nested too deeply", ex);
    }
  }

  public double getDefaultPriority ()
  {
    return m_dDefaultPriority;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XsltPattern> getAlternatives ()
  {
    if (m_aAlternatives.isEmpty ())
      return new CommonsArrayList <> (this);
    return new CommonsArrayList <> (m_aAlternatives);
  }

  @Override
  public String toString ()
  {
    return m_sSource;
  }
}
