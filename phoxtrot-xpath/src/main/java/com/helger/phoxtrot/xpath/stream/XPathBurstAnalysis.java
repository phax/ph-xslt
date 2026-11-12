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
package com.helger.phoxtrot.xpath.stream;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.EAxis;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.IExpr;

/**
 * The burst analysis of an expression: can it be evaluated on a buffered copy of the subtree of the context node that
 * keeps the context node's place in the document (as after <code>copy-of (.)</code> in a streamed run)? Unlike the
 * posture and sweep analysis of XSLT 3.0 section 19 ({@link XPathStreamability}) this is no rule of the specification,
 * but the question a validator asks that buffers one element at a time:
 * <ul>
 * <li>The context node, its attributes and every node below it can be read in any way: any number of reads, any
 * downward axis, the sibling axes below the context node, predicates, variables.</li>
 * <li>The ancestors of the context node and their attributes are available as in streaming: name, attributes and
 * further ancestors, but neither their string value nor their other children. The root (<code>/</code>) is one of
 * them, and its child element, the document element, too (the context node is assumed to be no document node).</li>
 * <li>Everything else of the document is not available: <code>fn:root</code>, other children of the root and any
 * descendant step from it (<code>//</code>), downward or sideways steps from an ancestor, the sibling,
 * <code>preceding</code> and <code>following</code> axes from the context node, <code>preceding</code>/
 * <code>following</code> from anywhere, <code>fn:id</code>, <code>fn:idref</code>, <code>fn:element-with-id</code> and
 * <code>fn:key</code> without a third argument within the subtree.</li>
 * <li>Variables declared as subtree variables hold nodes of the subtree; variables declared as external node variables
 * hold nodes outside it, which may be compared and atomised but not navigated from; any other variable is a grounded
 * value (atomic values or nodes of other documents), as are the results of <code>fn:doc</code>,
 * <code>fn:document</code> and <code>fn:collection</code>.</li>
 * <li>The result of the expression must not contain ancestors, as the host may atomise it.</li>
 * </ul>
 * The analysis tracks for every expression which of these regions its nodes can come from, and fails closed: an
 * unknown function, a node passed where a function may navigate from it, or a function item that may return nodes is
 * not burst. Function calls are classified with the usages of section 19.8.9 (absorption reads the subtree,
 * inspection only properties, transmission passes the nodes on, navigation fails for any node of the document).
 * <p>
 * The walk is recursive, at most {@link XPathStreamability#MAX_DEPTH} expressions deep; a deeper expression is not
 * burst.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XPathBurstAnalysis
{
  /**
   * The construct that leaves the subtree: an axis step (<code>step</code> set), or an expression - an absolute path
   * for the root, a function call, a variable reference, an inline function. Both are <code>null</code> if the
   * expression is nested too deeply to be analysed.
   *
   * @param expression
   *        The expression. May be <code>null</code>.
   * @param step
   *        The axis step. May be <code>null</code>.
   */
  public record BurstFailure (@Nullable IExpr expression, IExpr.@Nullable StepExpr step)
  {}

  /** The context node. */
  private static final int SELF = 1;
  /** The nodes strictly below the context node, and the attributes and namespaces of the subtree. */
  private static final int BELOW = 2;
  /** The ancestors of the context node. */
  private static final int ANCESTOR = 4;
  /** The attributes and namespaces of the ancestors. */
  private static final int ANCESTOR_ATTR = 8;
  /** Nodes outside the subtree from an external node variable. */
  private static final int EXTERNAL = 16;
  /** The root of the document (<code>/</code>), an ancestor whose child element is one too. */
  private static final int ROOT = 32;
  private static final int SUBTREE = SELF | BELOW;
  /** The regions whose introduction is no failure itself, so a later failure names where they came from. */
  private static final int SOURCED = ANCESTOR | ANCESTOR_ATTR | EXTERNAL | ROOT;

  /**
   * The regions the nodes of a value can come from (0 for values without nodes of the document) and the construct
   * that introduced the first of the {@link #SOURCED} ones.
   */
  private record Region (int flags, @Nullable BurstFailure source)
  {
    static final Region NONE = new Region (0, null);

    boolean isNone ()
    {
      return flags == 0;
    }

    @NonNull
    Region getUnion (@NonNull final Region aOther)
    {
      if (aOther.flags == 0)
        return this;
      if (flags == 0)
        return aOther;
      return new Region (flags | aOther.flags, source != null ? source : aOther.source);
    }
  }

  /** What a local variable binding shadowed, to restore it when the binding goes out of scope. */
  private record Shadowed (@NonNull XdmQName name, @Nullable Region previous, boolean bound)
  {}

  /** Thrown at the first construct that is not burst. */
  private static final class BurstFailureException extends RuntimeException
  {
    private final BurstFailure m_aFailure;

    BurstFailureException (@NonNull final BurstFailure aFailure)
    {
      super ("Not burst", null, false, false);
      m_aFailure = aFailure;
    }
  }

  private final Set <XdmQName> m_aSubtreeVariables;
  private final Set <XdmQName> m_aExternalVariables;
  private final IStreamFunctionResolver m_aResolver;
  /** The local variables in scope; NONE shadows a declared variable of the same name. */
  private final ICommonsMap <XdmQName, Region> m_aLocals = new CommonsHashMap <> ();
  private int m_nDepth;

  private XPathBurstAnalysis (@NonNull final Set <XdmQName> aSubtreeVariables,
                              @NonNull final Set <XdmQName> aExternalVariables,
                              @Nullable final IStreamFunctionResolver aResolver)
  {
    m_aSubtreeVariables = aSubtreeVariables;
    m_aExternalVariables = aExternalVariables;
    m_aResolver = aResolver;
  }

  @NonNull
  private static BurstFailureException _fail (@NonNull final IExpr aExpr)
  {
    return new BurstFailureException (new BurstFailure (aExpr, null));
  }

  /**
   * @return The failure for bad regions of a value: the construct that introduced them if they are
   *         {@link #SOURCED}, else the fallback.
   */
  @NonNull
  private static BurstFailureException _failFrom (@NonNull final Region aRegion,
                                                  final int nBadFlags,
                                                  @NonNull final BurstFailure aFallback)
  {
    if ((nBadFlags & SOURCED) != 0 && aRegion.source () != null)
      return new BurstFailureException (aRegion.source ());
    return new BurstFailureException (aFallback);
  }

  /**
   * Apply an operand usage to a value.
   *
   * @return The region the construct passes on: that of the value for transmission, none otherwise.
   */
  @NonNull
  private static Region _use (@NonNull final Region aRegion,
                              @NonNull final EStreamUsage eUsage,
                              @NonNull final IExpr aConstruct)
  {
    final int nBad = switch (eUsage)
    {
      case INSPECTION, TRANSMISSION -> 0;
      // The string value of an ancestor needs its whole subtree
      case ABSORPTION -> aRegion.flags () & (ANCESTOR | ROOT);
      // A node of the document may be navigated from in any direction
      case NAVIGATION -> aRegion.flags ();
    };
    if (nBad != 0)
      throw _failFrom (aRegion, nBad, new BurstFailure (aConstruct, null));
    return eUsage == EStreamUsage.TRANSMISSION ? aRegion : Region.NONE;
  }

  /**
   * @return The regions an axis step reaches from the given regions, or <code>-1</code> if it leaves what is
   *         available.
   */
  private static int _getAxisFlags (final int nFrom, final IExpr.@NonNull StepExpr aStep)
  {
    int ret = 0;
    if ((nFrom & SELF) != 0)
    {
      final int n = switch (aStep.axis ())
      {
        case SELF -> SELF;
        case CHILD, DESCENDANT, ATTRIBUTE, NAMESPACE -> BELOW;
        case DESCENDANT_OR_SELF -> SUBTREE;
        case PARENT, ANCESTOR -> ANCESTOR;
        case ANCESTOR_OR_SELF -> SELF | ANCESTOR;
        case FOLLOWING_SIBLING, PRECEDING_SIBLING, FOLLOWING, PRECEDING -> -1;
      };
      if (n < 0)
        return -1;
      ret |= n;
    }
    if ((nFrom & BELOW) != 0)
    {
      final int n = switch (aStep.axis ())
      {
        // The siblings of a node strictly below the context node are children of a node of the subtree
        case SELF, CHILD, DESCENDANT, ATTRIBUTE, NAMESPACE, DESCENDANT_OR_SELF, FOLLOWING_SIBLING, PRECEDING_SIBLING -> BELOW;
        case PARENT -> SUBTREE;
        case ANCESTOR, ANCESTOR_OR_SELF -> SUBTREE | ANCESTOR;
        case FOLLOWING, PRECEDING -> -1;
      };
      if (n < 0)
        return -1;
      ret |= n;
    }
    if ((nFrom & ANCESTOR) != 0)
    {
      final int n = switch (aStep.axis ())
      {
        case SELF, PARENT, ANCESTOR, ANCESTOR_OR_SELF -> ANCESTOR;
        case ATTRIBUTE, NAMESPACE -> ANCESTOR_ATTR;
        case CHILD, DESCENDANT, DESCENDANT_OR_SELF, FOLLOWING_SIBLING, PRECEDING_SIBLING, FOLLOWING, PRECEDING -> -1;
      };
      if (n < 0)
        return -1;
      ret |= n;
    }
    if ((nFrom & ANCESTOR_ATTR) != 0)
    {
      final int n = switch (aStep.axis ())
      {
        case SELF, DESCENDANT_OR_SELF -> ANCESTOR_ATTR;
        case PARENT, ANCESTOR -> ANCESTOR;
        case ANCESTOR_OR_SELF -> ANCESTOR | ANCESTOR_ATTR;
        // An attribute has no children, attributes or siblings
        case CHILD, DESCENDANT, ATTRIBUTE, NAMESPACE, FOLLOWING_SIBLING, PRECEDING_SIBLING -> 0;
        case FOLLOWING, PRECEDING -> -1;
      };
      if (n < 0)
        return -1;
      ret |= n;
    }
    if ((nFrom & ROOT) != 0)
    {
      final int n = switch (aStep.axis ())
      {
        case SELF, ANCESTOR_OR_SELF -> ROOT;
        // The document element, an ancestor-or-self of the context node; a comment or processing instruction after it
        // has not been read
        case CHILD -> aStep.test () != null &&
                      (aStep.test ().kind () == ENodeTestKind.NAME ||
                       aStep.test ().kind () == ENodeTestKind.ELEMENT) ? ANCESTOR : -1;
        // A document node has no parent, attributes or siblings
        case PARENT, ANCESTOR, ATTRIBUTE, NAMESPACE, FOLLOWING_SIBLING, PRECEDING_SIBLING -> 0;
        case DESCENDANT, DESCENDANT_OR_SELF, FOLLOWING, PRECEDING -> -1;
      };
      if (n < 0)
        return -1;
      ret |= n;
    }
    if ((nFrom & EXTERNAL) != 0)
    {
      // An external node may be compared, but not navigated from
      if (aStep.axis () != EAxis.SELF)
        return -1;
      ret |= EXTERNAL;
    }
    return ret;
  }

  @NonNull
  private static Region _axisStep (@NonNull final Region aFrom, final IExpr.@NonNull StepExpr aStep)
  {
    final int nFlags = _getAxisFlags (aFrom.flags (), aStep);
    if (nFlags < 0)
    {
      // Name the construct that brought in an ancestor or external node, unless the step fails from the subtree
      final int nWithoutSourced = _getAxisFlags (aFrom.flags () & ~SOURCED, aStep);
      throw _failFrom (aFrom, nWithoutSourced < 0 ? 0 : SOURCED, new BurstFailure (null, aStep));
    }
    BurstFailure aSource = aFrom.source ();
    if (aSource == null && (nFlags & SOURCED) != 0)
      aSource = new BurstFailure (null, aStep);
    return new Region (nFlags, (nFlags & SOURCED) != 0 ? aSource : null);
  }

  private void _predicates (@NonNull final List <IExpr> aPredicates, @NonNull final Region aContext)
  {
    // A predicate is a position or an effective boolean value: an inspection
    for (final IExpr aPredicate : aPredicates)
      _expr (aPredicate, aContext);
  }

  @NonNull
  private Region _path (final IExpr.@NonNull PathExpr aPath, @NonNull final Region aContext)
  {
    Region aCur;
    if (aPath.start () != null)
      aCur = _expr (aPath.start (), aContext);
    else
      if (aPath.absolute ())
      {
        // The root of the document of the context node
        aCur = new Region (ROOT, new BurstFailure (aPath, null));
      }
      else
        aCur = aContext;
    for (final IExpr.StepExpr aStep : aPath.steps ())
    {
      if (aStep.postfix () != null)
        aCur = _expr (aStep.postfix (), aCur);
      else
        aCur = _axisStep (aCur, aStep);
      _predicates (aStep.predicates (), aCur);
    }
    return aCur;
  }

  /**
   * Bind a local variable and remember what it shadowed.
   */
  private void _bindOne (@NonNull final XdmQName aName,
                         @NonNull final Region aRegion,
                         @NonNull final ICommonsList <Shadowed> aShadowed)
  {
    aShadowed.add (new Shadowed (aName, m_aLocals.get (aName), m_aLocals.containsKey (aName)));
    m_aLocals.put (aName, aRegion);
  }

  /**
   * Bind the variables of a for, let or quantified expression one after the other and remember what they shadowed.
   */
  private void _bind (@NonNull final List <IExpr.Binding> aBindings,
                      @NonNull final Region aContext,
                      @NonNull final ICommonsList <Shadowed> aShadowed)
  {
    for (final IExpr.Binding aBinding : aBindings)
    {
      _bindOne (aBinding.name (), _expr (aBinding.expr (), aContext), aShadowed);
    }
  }

  private void _unbind (@NonNull final ICommonsList <Shadowed> aShadowed)
  {
    for (int i = aShadowed.size () - 1; i >= 0; --i)
    {
      final Shadowed aEntry = aShadowed.get (i);
      if (aEntry.bound ())
        m_aLocals.put (aEntry.name (), aEntry.previous ());
      else
        m_aLocals.remove (aEntry.name ());
    }
  }

  @NonNull
  private Region _variable (final IExpr.@NonNull VarRefExpr aRef)
  {
    final XdmQName aName = aRef.name ();
    if (m_aLocals.containsKey (aName))
      return m_aLocals.get (aName);
    if (m_aSubtreeVariables.contains (aName))
      return new Region (SUBTREE, null);
    if (m_aExternalVariables.contains (aName))
      return new Region (EXTERNAL, new BurstFailure (aRef, null));
    return Region.NONE;
  }

  /**
   * A call of a function declared with a streamability category (XSLT 3.0 section 19.8.5): the category says what the
   * function does with its first argument, the other arguments have their type-determined usage.
   */
  @NonNull
  private Region _declaredCall (final IExpr.@NonNull FunctionCallExpr aCall,
                                @NonNull final StreamFunctionSignature aSig,
                                @NonNull final Region aContext)
  {
    final List <IExpr> aArgs = aCall.args ();
    Region ret = Region.NONE;
    for (int i = 0; i < aArgs.size (); ++i)
    {
      final Region aArg = _expr (aArgs.get (i), aContext);
      if (i > 0 || aSig.category () == EStreamFunctionCategory.UNCLASSIFIED)
      {
        _use (aArg, aSig.getUsage (i), aCall);
        continue;
      }
      switch (aSig.category ())
      {
        case ABSORBING -> _use (aArg, EStreamUsage.ABSORPTION, aCall);
        case INSPECTION -> _use (aArg, EStreamUsage.INSPECTION, aCall);
        case FILTER -> ret = aArg;
        case SHALLOW_DESCENT, DEEP_DESCENT ->
        {
          final int nBad = aArg.flags () & (ANCESTOR | EXTERNAL | ROOT);
          if (nBad != 0)
            throw _failFrom (aArg, nBad, new BurstFailure (aCall, null));
          // The descendants of an attribute are none
          if ((aArg.flags () & SUBTREE) != 0)
            ret = new Region (BELOW, null);
        }
        case ASCENT ->
        {
          if ((aArg.flags () & EXTERNAL) != 0)
            throw _failFrom (aArg, EXTERNAL, new BurstFailure (aCall, null));
          int nFlags = 0;
          if ((aArg.flags () & BELOW) != 0)
            nFlags |= SUBTREE | ANCESTOR;
          if ((aArg.flags () & (SELF | ANCESTOR | ANCESTOR_ATTR | ROOT)) != 0)
            nFlags |= ANCESTOR;
          if (nFlags != 0)
            ret = new Region (nFlags, aArg.source () != null ? aArg.source () : new BurstFailure (aCall, null));
        }
        default -> throw _fail (aCall);
      }
    }
    return ret;
  }

  /** Static function calls. */
  @NonNull
  private Region _functionCall (final IExpr.@NonNull FunctionCallExpr aCall, @NonNull final Region aContext)
  {
    final XdmQName aName = aCall.name ();
    final List <IExpr> aWritten = aCall.args ();
    final int nArity = aWritten.size ();

    if (m_aResolver != null && !XdmConstants.NS_FN.equals (aName.namespaceUri ()))
    {
      final StreamFunctionSignature aSig = m_aResolver.getStreamFunction (aName, nArity);
      if (aSig != null)
        return _declaredCall (aCall, aSig, aContext);
    }

    boolean bPartial = false;
    for (final IExpr aArg : aWritten)
      if (aArg instanceof IExpr.PlaceholderExpr)
        bPartial = true;
    if (bPartial)
    {
      // A partial application returns a function item: its captured focus and arguments must hold no nodes of the
      // document, as the result of its later call is not tracked
      if (XPathStreamability.isFocusDependent (aName, nArity) && !aContext.isNone ())
        throw _fail (aCall);
      for (final IExpr aArg : aWritten)
        if (!(aArg instanceof IExpr.PlaceholderExpr))
          _use (_expr (aArg, aContext), EStreamUsage.NAVIGATION, aCall);
      return Region.NONE;
    }

    final String sName = XPathStreamability.getBuiltinName (aName);
    if (sName == null)
    {
      // Fail closed: an unknown function may do anything with a node of the document
      throw _fail (aCall);
    }

    switch (sName)
    {
      case "fn:key":
      {
        // Only the form with a third argument within the subtree searches the subtree; the others search the whole
        // document
        if (nArity != 3)
          throw _fail (aCall);
        _use (_expr (aWritten.get (0), aContext), EStreamUsage.ABSORPTION, aCall);
        _use (_expr (aWritten.get (1), aContext), EStreamUsage.ABSORPTION, aCall);
        final Region aTop = _expr (aWritten.get (2), aContext);
        final int nBad = aTop.flags () & ~SUBTREE;
        if (nBad != 0)
          throw _failFrom (aTop, nBad, new BurstFailure (aCall, null));
        return aTop.isNone () ? Region.NONE : new Region (SUBTREE, null);
      }
      case "fn:id":
      case "fn:idref":
      case "fn:element-with-id":
      case "fn:root":
      {
        // They reach the whole document of their node argument, which must be another document
        final List <IExpr> aArgs = XPathStreamability.getArgumentsWithDefault (sName, aWritten);
        if (aArgs.isEmpty ())
          throw _fail (aCall);
        for (int i = 0; i < aArgs.size () - 1; ++i)
          _use (_expr (aArgs.get (i), aContext), EStreamUsage.ABSORPTION, aCall);
        if (!_expr (aArgs.get (aArgs.size () - 1), aContext).isNone ())
          throw _fail (aCall);
        return Region.NONE;
      }
      case "fn:position":
      case "fn:last":
      case "fn:current-grouping-key":
      case "fn:current-merge-key":
        // Numbers and atomic values of the focus or the host
        if (nArity == 0)
          return Region.NONE;
        break;
      case "fn:current":
        // The context item of the outermost expression
        return new Region (SELF, null);
      case "fn:unparsed-entity-uri":
      case "fn:unparsed-entity-public-id":
        // The short form reads the unparsed entities of the document, which the DTD declares before the context node
        if (nArity == 1)
        {
          _use (_expr (aWritten.get (0), aContext), EStreamUsage.ABSORPTION, aCall);
          return Region.NONE;
        }
        break;
      case "fn:current-group":
      case "fn:current-merge-group":
      case "fn:accumulator-before":
      case "fn:accumulator-after":
        // Values the host computes from the streamed document: fail closed
        throw _fail (aCall);
      case "fn:function-lookup":
        // May return a focus-dependent function bound to the context
        if (!aContext.isNone ())
          throw _fail (aCall);
        break;
      case "fn:reverse":
      case "fn:outermost":
      case "fn:innermost":
        // They return a subset of their argument, in another order: transmission
        if (nArity == 1)
          return _use (_expr (aWritten.get (0), aContext), EStreamUsage.TRANSMISSION, aCall);
        break;
      case "fn:normalize-space":
        // The short form reads the string value of the context item
        if (nArity == 0)
        {
          _use (aContext, EStreamUsage.ABSORPTION, aCall);
          return Region.NONE;
        }
        break;
      default:
        break;
    }

    final List <IExpr> aArgs = XPathStreamability.getArgumentsWithDefault (sName, aWritten);
    final EStreamUsage [] aUsages = XPathStreamability.getFunctionUsages (sName, aArgs.size ());
    if (aUsages == null)
    {
      // Fail closed: a function outside the catalogue may do anything with a node of the document
      throw _fail (aCall);
    }
    Region ret = Region.NONE;
    for (int i = 0; i < aArgs.size (); ++i)
      ret = ret.getUnion (_use (_expr (aArgs.get (i), aContext), aUsages[i], aCall));
    return ret;
  }

  @NonNull
  private Region _exprUnbounded (@NonNull final IExpr aExpr, @NonNull final Region aContext)
  {
    return switch (aExpr)
    {
      case final IExpr.LiteralExpr e -> Region.NONE;
      case final IExpr.ContextItemExpr e -> aContext;
      case final IExpr.VarRefExpr e -> _variable (e);
      case final IExpr.SequenceExpr e ->
      {
        Region ret = Region.NONE;
        for (final IExpr aItem : e.items ())
          ret = ret.getUnion (_expr (aItem, aContext));
        yield ret;
      }
      case final IExpr.RangeExpr e ->
      {
        _use (_expr (e.from (), aContext), EStreamUsage.ABSORPTION, e);
        _use (_expr (e.to (), aContext), EStreamUsage.ABSORPTION, e);
        yield Region.NONE;
      }
      case final IExpr.BinaryExpr e ->
      {
        final EStreamUsage eUsage = e.op () == EBinaryOp.AND || e.op () == EBinaryOp.OR ? EStreamUsage.INSPECTION
                                                                                         : EStreamUsage.ABSORPTION;
        _use (_expr (e.left (), aContext), eUsage, e);
        _use (_expr (e.right (), aContext), eUsage, e);
        yield Region.NONE;
      }
      case final IExpr.UnaryExpr e -> _use (_expr (e.operand (), aContext), EStreamUsage.ABSORPTION, e);
      case final IExpr.ComparisonExpr e ->
      {
        final EStreamUsage eUsage = e.op ().isNode () ? EStreamUsage.INSPECTION : EStreamUsage.ABSORPTION;
        _use (_expr (e.left (), aContext), eUsage, e);
        _use (_expr (e.right (), aContext), eUsage, e);
        yield Region.NONE;
      }
      case final IExpr.StringConcatExpr e ->
      {
        for (final IExpr aPart : e.parts ())
          _use (_expr (aPart, aContext), EStreamUsage.ABSORPTION, e);
        yield Region.NONE;
      }
      case final IExpr.SetExpr e ->
      {
        final Region aLeft = _expr (e.left (), aContext);
        final Region aRight = _expr (e.right (), aContext);
        // intersect and except can only remove nodes from the left operand
        yield e.op () == IExpr.SetExpr.ESetOp.UNION ? aLeft.getUnion (aRight) : aLeft;
      }
      case final IExpr.InstanceOfExpr e -> _use (_expr (e.operand (), aContext),
                                                 XPathStreamability.isDocElementTest (e.type ()) ? EStreamUsage.ABSORPTION
                                                                                                 : EStreamUsage.INSPECTION,
                                                 e);
      case final IExpr.TreatExpr e ->
      {
        final Region aOperand = _expr (e.operand (), aContext);
        if (XPathStreamability.isDocElementTest (e.type ()))
          _use (aOperand, EStreamUsage.ABSORPTION, e);
        yield aOperand;
      }
      case final IExpr.CastExpr e -> _use (_expr (e.operand (), aContext), EStreamUsage.ABSORPTION, e);
      case final IExpr.IfExpr e ->
      {
        _expr (e.condition (), aContext);
        yield _expr (e.thenExpr (), aContext).getUnion (_expr (e.elseExpr (), aContext));
      }
      case final IExpr.ForExpr e ->
      {
        final ICommonsList <Shadowed> aShadowed = new CommonsArrayList <> ();
        try
        {
          _bind (e.bindings (), aContext, aShadowed);
          yield _expr (e.body (), aContext);
        }
        finally
        {
          _unbind (aShadowed);
        }
      }
      case final IExpr.LetExpr e ->
      {
        final ICommonsList <Shadowed> aShadowed = new CommonsArrayList <> ();
        try
        {
          _bind (e.bindings (), aContext, aShadowed);
          yield _expr (e.body (), aContext);
        }
        finally
        {
          _unbind (aShadowed);
        }
      }
      case final IExpr.QuantifiedExpr e ->
      {
        final ICommonsList <Shadowed> aShadowed = new CommonsArrayList <> ();
        try
        {
          _bind (e.bindings (), aContext, aShadowed);
          _expr (e.satisfies (), aContext);
          yield Region.NONE;
        }
        finally
        {
          _unbind (aShadowed);
        }
      }
      case final IExpr.PathExpr e -> _path (e, aContext);
      case final IExpr.FilterExpr e ->
      {
        final Region aBase = _expr (e.primary (), aContext);
        _predicates (e.predicates (), aBase);
        yield aBase;
      }
      case final IExpr.SimpleMapExpr e ->
      {
        Region aCur = aContext;
        for (final IExpr aStep : e.steps ())
          aCur = _expr (aStep, aCur);
        yield aCur;
      }
      case final IExpr.FunctionCallExpr e -> _functionCall (e, aContext);
      case final IExpr.DynamicCallExpr e ->
      {
        // The function is not known, so an argument may be navigated from
        _expr (e.base (), aContext);
        for (final IExpr aArg : e.args ())
          if (!(aArg instanceof IExpr.PlaceholderExpr))
            _use (_expr (aArg, aContext), EStreamUsage.NAVIGATION, e);
        yield Region.NONE;
      }
      case final IExpr.NamedFunctionRefExpr e ->
      {
        // A focus-dependent function item is bound to the context of its creation
        if (XPathStreamability.isFocusDependent (e.name (), e.arity ()) && !aContext.isNone ())
          throw _fail (e);
        yield Region.NONE;
      }
      case final IExpr.InlineFunctionExpr e ->
      {
        // The body has no focus and parameters without nodes of the document (every call passes its arguments with
        // navigation); the nodes it returns would not be tracked through the call
        final ICommonsList <Shadowed> aShadowed = new CommonsArrayList <> ();
        try
        {
          for (final IExpr.Param aParam : e.params ())
            _bindOne (aParam.name (), Region.NONE, aShadowed);
          if (!_expr (e.body (), Region.NONE).isNone ())
            throw _fail (e);
          yield Region.NONE;
        }
        finally
        {
          _unbind (aShadowed);
        }
      }
      case final IExpr.MapConstructorExpr e ->
      {
        for (final IExpr aKey : e.keys ())
          _use (_expr (aKey, aContext), EStreamUsage.ABSORPTION, e);
        // A map entry that held nodes of the document would carry them past the analysis
        for (final IExpr aValue : e.values ())
          _use (_expr (aValue, aContext), EStreamUsage.NAVIGATION, e);
        yield Region.NONE;
      }
      case final IExpr.ArrayConstructorExpr e ->
      {
        for (final IExpr aItem : e.items ())
          _use (_expr (aItem, aContext), EStreamUsage.NAVIGATION, e);
        yield Region.NONE;
      }
      case final IExpr.LookupExpr e ->
      {
        if (e.base () != null)
          _expr (e.base (), aContext);
        if (e.key () != null)
          _use (_expr (e.key (), aContext), EStreamUsage.ABSORPTION, e);
        yield Region.NONE;
      }
      case final IExpr.PlaceholderExpr e -> Region.NONE;
    };
  }

  /**
   * The walk, at most {@link XPathStreamability#MAX_DEPTH} levels deep.
   */
  @NonNull
  private Region _expr (@NonNull final IExpr aExpr, @NonNull final Region aContext)
  {
    if (m_nDepth >= XPathStreamability.MAX_DEPTH)
      throw new BurstFailureException (new BurstFailure (null, null));
    m_nDepth++;
    try
    {
      return _exprUnbounded (aExpr, aContext);
    }
    finally
    {
      m_nDepth--;
    }
  }

  @Nullable
  private static BurstFailure _getBurstFailure (@NonNull final IExpr aExpr,
                                                @NonNull final Set <XdmQName> aSubtreeVariables,
                                                @NonNull final Set <XdmQName> aExternalVariables,
                                                @Nullable final IStreamFunctionResolver aResolver,
                                                @NonNull final EStreamUsage eResultUsage)
  {
    ValueEnforcer.notNull (aExpr, "Expr");
    ValueEnforcer.notNull (aSubtreeVariables, "SubtreeVariables");
    ValueEnforcer.notNull (aExternalVariables, "ExternalVariables");
    final ICommonsSet <XdmQName> aSubtree = new CommonsHashSet <> (aSubtreeVariables);
    final ICommonsSet <XdmQName> aExternal = new CommonsHashSet <> (aExternalVariables);
    try
    {
      final XPathBurstAnalysis aAnalysis = new XPathBurstAnalysis (aSubtree, aExternal, aResolver);
      _use (aAnalysis._expr (aExpr, new Region (SELF, null)), eResultUsage, aExpr);
      return null;
    }
    catch (final BurstFailureException ex)
    {
      return ex.m_aFailure;
    }
    catch (final StackOverflowError ex)
    {
      // Nested deeper than the stack of this thread allows: fail closed
      return new BurstFailure (null, null);
    }
  }

  /**
   * Find the first construct of an expression that leaves the buffered subtree of the context node.
   *
   * @param aExpr
   *        The expression. May not be <code>null</code>.
   * @param aSubtreeVariables
   *        The variables bound to nodes of the subtree. May not be <code>null</code>.
   * @param aExternalVariables
   *        The variables bound to nodes outside the subtree. May not be <code>null</code>.
   * @param aResolver
   *        The streamability categories of the functions outside the <code>fn</code> namespace. May be
   *        <code>null</code>.
   * @return <code>null</code> if the expression is burst, else the construct that is not.
   */
  @Nullable
  public static BurstFailure getBurstFailure (@NonNull final IExpr aExpr,
                                              @NonNull final Set <XdmQName> aSubtreeVariables,
                                              @NonNull final Set <XdmQName> aExternalVariables,
                                              @Nullable final IStreamFunctionResolver aResolver)
  {
    // The host may atomise the result
    return _getBurstFailure (aExpr, aSubtreeVariables, aExternalVariables, aResolver, EStreamUsage.ABSORPTION);
  }

  /**
   * Find the first construct of a non-positional pattern predicate that leaves the buffered subtree of the node it is
   * evaluated for (the context node). Its value is only used as effective boolean value, so it may return ancestors.
   * Whether the predicate is positional is for the caller to check.
   *
   * @param aPredicate
   *        The predicate. May not be <code>null</code>.
   * @param aSubtreeVariables
   *        The variables bound to nodes of the subtree. May not be <code>null</code>.
   * @param aExternalVariables
   *        The variables bound to nodes outside the subtree. May not be <code>null</code>.
   * @param aResolver
   *        The streamability categories of the functions outside the <code>fn</code> namespace. May be
   *        <code>null</code>.
   * @return <code>null</code> if the predicate is burst, else the construct that is not.
   */
  @Nullable
  public static BurstFailure getPredicateBurstFailure (@NonNull final IExpr aPredicate,
                                                       @NonNull final Set <XdmQName> aSubtreeVariables,
                                                       @NonNull final Set <XdmQName> aExternalVariables,
                                                       @Nullable final IStreamFunctionResolver aResolver)
  {
    return _getBurstFailure (aPredicate, aSubtreeVariables, aExternalVariables, aResolver, EStreamUsage.INSPECTION);
  }
}
