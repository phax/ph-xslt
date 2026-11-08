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
package com.helger.phoxtrot.xpath.pattern;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.eval.XPathEvaluator;

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
  // The paths of a pattern that is a union of paths, for the quick test of
  // canMatch; null for any other pattern
  private final List <IExpr.PathExpr> m_aUnionPaths;
  // Parallel to m_aUnionPaths: the precomputed last step of each path, null
  // for a path the pre-test does not decide
  private final PatternMatcher.LastStep [] m_aLastSteps;
  // See getFinalNames; null if not every path ends in an exact name test
  private final PatternFinalNames m_aFinalNames;

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
    m_aUnionPaths = _getUnionPaths (aExpr);
    if (m_aUnionPaths == null)
      m_aLastSteps = null;
    else
    {
      m_aLastSteps = new PatternMatcher.LastStep [m_aUnionPaths.size ()];
      for (int i = 0; i < m_aLastSteps.length; ++i)
        m_aLastSteps[i] = PatternMatcher.getLastStep (m_aUnionPaths.get (i));
    }
    m_aFinalNames = m_aUnionPaths == null ? null : _getFinalNames (m_aUnionPaths);
  }

  /**
   * The alternatives of a pattern that consists only of path patterns joined by top-level or
   * nested unions, walked iteratively from left to right.
   *
   * @param aPattern
   *        The parsed pattern.
   * @return The paths, or <code>null</code> if the pattern contains anything else (a predicate
   *         pattern, an intersection or a difference).
   */
  @Nullable
  private static List <IExpr.PathExpr> _getUnionPaths (@NonNull final IExpr aPattern)
  {
    final ICommonsList <IExpr.PathExpr> ret = new CommonsArrayList <> ();
    final ICommonsList <IExpr> aStack = new CommonsArrayList <> (aPattern);
    while (aStack.isNotEmpty ())
    {
      final IExpr aCur = aStack.removeLast ();
      if (aCur instanceof final IExpr.SetExpr aSet && aSet.op () == IExpr.SetExpr.ESetOp.UNION)
      {
        aStack.add (aSet.right ());
        aStack.add (aSet.left ());
      }
      else
        if (aCur instanceof final IExpr.PathExpr aPath)
          ret.add (aPath);
        else
          return null;
    }
    return Collections.unmodifiableList (ret);
  }

  /**
   * The exact names of the last steps of the paths (see {@link #getFinalNames()}): each last step
   * must be an axis step (not parenthesised) with a name test without wildcard on an axis whose
   * principal node kind is element (child, descendant, descendant-or-self, self) or attribute.
   *
   * @param aPaths
   *        The paths of a union pattern.
   * @return The distinct names in order of appearance, or <code>null</code> if a path does not end
   *         in such a step.
   */
  @Nullable
  private static PatternFinalNames _getFinalNames (@NonNull final List <IExpr.PathExpr> aPaths)
  {
    final ICommonsList <XdmQName> aElements = new CommonsArrayList <> ();
    final ICommonsList <XdmQName> aAttributes = new CommonsArrayList <> ();
    for (final IExpr.PathExpr aPath : aPaths)
    {
      final List <IExpr.StepExpr> aSteps = aPath.steps ();
      if (aSteps.isEmpty ())
        return null;
      final IExpr.StepExpr aLast = aSteps.get (aSteps.size () - 1);
      if (aLast.postfix () != null)
        return null;
      final NodeTest aTest = aLast.test ();
      if (aTest.kind () != ENodeTestKind.NAME ||
          aTest.anyName () ||
          aTest.anyNamespace () ||
          aTest.anyLocalName () ||
          aTest.name () == null)
        return null;
      final ICommonsList <XdmQName> aTarget;
      switch (aLast.axis ())
      {
        case CHILD:
        case DESCENDANT:
        case DESCENDANT_OR_SELF:
        case SELF:
          aTarget = aElements;
          break;
        case ATTRIBUTE:
          aTarget = aAttributes;
          break;
        default:
          return null;
      }
      if (!aTarget.contains (aTest.name ()))
        aTarget.add (aTest.name ());
    }
    return new PatternFinalNames (Collections.unmodifiableList (aElements), Collections.unmodifiableList (aAttributes));
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
   *         {@link com.helger.phoxtrot.xpath.parser.XPathParser#parsePattern(String, com.helger.phoxtrot.xpath.context.StaticContext)}.
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
   * The pre-test of {@link PatternMatcher#canLastStepSelect(IExpr.PathExpr, IXdmNode)} for the union path at the
   * passed index, with the last step computed when the pattern was created.
   */
  private boolean _canLastStepSelect (final int nIndex, @NonNull final IXdmNode aNode)
  {
    final PatternMatcher.LastStep aLast = m_aLastSteps[nIndex];
    return aLast == null || aLast.canSelect (aNode);
  }

  /**
   * For a pattern that is a union of paths: <code>false</code> if the item is a node that the last
   * step of no path can select (wrong node kind for the axis or failing node test), so that no
   * path can match it; such a node would be rejected by the search of every path before anything
   * is evaluated. <code>true</code> for every other pattern and item.
   */
  @Override
  public boolean canMatch (@NonNull final IXdmItem aItem)
  {
    if (m_aUnionPaths == null || !(aItem instanceof final IXdmNode aNode))
      return true;
    for (int i = 0; i < m_aLastSteps.length; ++i)
      if (_canLastStepSelect (i, aNode))
        return true;
    return false;
  }

  @Override
  @Nullable
  public PatternFinalNames getFinalNames ()
  {
    return m_aFinalNames;
  }

  /**
   * Match a node against a pattern that is a union of paths, as <code>PatternMatcher.matchesNode</code> does: the
   * paths from left to right until one matches, each rejected by the pre-test of its precomputed last step before a
   * search is allocated. The context with the node as current item is only created for the first path that passes
   * the pre-test.
   *
   * @param aNode
   *        The candidate node.
   * @param aCtx
   *        Dynamic context for predicates and variable references.
   * @return <code>true</code> if a path matches the node.
   */
  private boolean _matchesUnionPaths (@NonNull final IXdmNode aNode, @NonNull final DynamicContext aCtx)
  {
    PatternMatcher aMatcher = null;
    for (int i = 0; i < m_aLastSteps.length; ++i)
    {
      if (!_canLastStepSelect (i, aNode))
      {
        // As the search would do before rejecting the node at its first step
        aCtx.checkInterrupt ();
        continue;
      }
      if (aMatcher == null)
        aMatcher = new PatternMatcher (aCtx.withCurrentItem (aNode), aNode, m_aPositionalPredicates);
      if (aMatcher.searchPath (m_aUnionPaths.get (i), aNode))
        return true;
    }
    return false;
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
      if (m_aUnionPaths != null && aItem instanceof final IXdmNode aNode)
        return _matchesUnionPaths (aNode, aCtx);
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
