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
package com.helger.xpath.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.ops.AtomicArithmetic;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.ops.SchemaTypeOps;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * Tree-walking evaluator for {@link IExpr} (a port of go-xslt's <code>eval.go</code> and
 * <code>axes.go</code>). Owner: evaluator workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathEvaluator
{
  /**
   * The largest integer range (<code>a to b</code>) that is materialised; longer ranges are an
   * implementation limit (XPDY0130), as in go-xslt.
   */
  public static final long MAX_RANGE_SIZE = 10_000_000L;

  private static final XdmSequence TRUE_SEQ = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE_SEQ = XdmSequence.of (XdmAtomic.FALSE);

  @PresentForCodeCoverage
  private static final XPathEvaluator INSTANCE = new XPathEvaluator ();

  private XPathEvaluator ()
  {}

  @NonNull
  private static XdmSequence _bool (final boolean b)
  {
    return b ? TRUE_SEQ : FALSE_SEQ;
  }

  private static boolean _ebv (@NonNull final XdmSequence aSeq)
  {
    return Atomizer.getEffectiveBooleanValue (aSeq);
  }

  @NonNull
  private static IXdmItem _getContextItem (@NonNull final DynamicContext aCtx)
  {
    final IXdmItem ret = aCtx.getContextItem ();
    if (ret == null)
      throw XPathException.noContext ("The context item is absent");
    return ret;
  }

  /** Atomise, without calling the atomiser for sequences that are atomic already. */
  @NonNull
  private static XdmSequence _atomize (@NonNull final XdmSequence aSeq)
  {
    for (final IXdmItem aItem : aSeq)
      if (!(aItem instanceof XdmAtomic))
        return Atomizer.atomize (aSeq);
    return aSeq;
  }

  /**
   * Atomise an operand that must be empty or one atomic value.
   *
   * @return <code>null</code> for the empty sequence.
   */
  @Nullable
  private static XdmAtomic _atomizeOptional (@NonNull final XdmSequence aSeq, @NonNull final String sWhat)
  {
    final XdmSequence aAtoms = _atomize (aSeq);
    if (aAtoms.isEmpty ())
      return null;
    if (aAtoms.size () > 1)
      throw XPathException.type ("The " + sWhat + " is a sequence of " + aAtoms.size () + " items");
    return (XdmAtomic) aAtoms.get (0);
  }

  @NonNull
  private static String _getStringValue (@NonNull final XdmAtomic aValue)
  {
    return aValue.isStringLike () ? aValue.getString () : aValue.getLexical ();
  }

  @NonNull
  private static XdmAtomic _untypedToDouble (@NonNull final XdmAtomic aValue, @NonNull final DynamicContext aCtx)
  {
    if (aValue.getType () == EAtomicType.UNTYPED_ATOMIC)
      return AtomicCaster.castFromString (aValue.getString (), EAtomicType.DOUBLE, aCtx.getStaticContext ());
    return aValue;
  }

  /**
   * @return <code>true</code> if the numeric value equals the 1-based position exactly.
   */
  private static boolean _isPosition (@NonNull final XdmAtomic aValue, final int nPosition)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 && aBigInt.intValue () == nPosition;
    if (aPayload instanceof final BigDecimal aBigDec)
      return aBigDec.compareTo (BigDecimal.valueOf (nPosition)) == 0;
    return aValue.getAsDouble () == nPosition;
  }

  /**
   * @return The position a numeric literal predicate selects, or -1 if it selects none (fractional,
   *         NaN, out of range).
   */
  private static int _getLiteralPosition (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 ? aBigInt.intValue () : -1;
    if (aPayload instanceof final BigDecimal aBigDec)
    {
      final BigDecimal aStripped = aBigDec.stripTrailingZeros ();
      if (aStripped.scale () > 0 || aStripped.compareTo (BigDecimal.valueOf (Integer.MAX_VALUE)) > 0)
        return -1;
      return aStripped.intValue ();
    }
    final double d = aValue.getAsDouble ();
    if (d != Math.rint (d) || d < 1 || d > Integer.MAX_VALUE)
      return -1;
    return (int) d;
  }

  /**
   * Apply one predicate to a sequence in its current order (axis order for an axis step).
   */
  @NonNull
  private static XdmSequence _applyPredicate (@NonNull final XdmSequence aSeq,
                                              @NonNull final IExpr aPredicate,
                                              @NonNull final DynamicContext aCtx)
  {
    final int nSize = aSeq.size ();
    if (nSize == 0)
      return aSeq;
    if (aPredicate instanceof final IExpr.LiteralExpr aLiteral && aLiteral.value ().isNumeric ())
    {
      // A numeric literal selects one position without evaluating per item
      final int nPos = _getLiteralPosition (aLiteral.value ());
      return nPos >= 1 && nPos <= nSize ? XdmSequence.of (aSeq.get (nPos - 1)) : XdmSequence.EMPTY;
    }
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      final IXdmItem aItem = aSeq.get (i);
      final XdmSequence aValue = evaluateInternal (aPredicate, aCtx.withFocus (aItem, i + 1, nSize));
      final boolean bKeep;
      if (aValue.isSingleton () && aValue.get (0) instanceof final XdmAtomic aAtomic && aAtomic.isNumeric ())
        bKeep = _isPosition (aAtomic, i + 1);
      else
        bKeep = _ebv (aValue);
      if (bKeep)
        aBuilder.add (aItem);
    }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _applyPredicates (@NonNull final XdmSequence aSeq,
                                               @NonNull final List <IExpr> aPredicates,
                                               @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = aSeq;
    for (final IExpr aPredicate : aPredicates)
      ret = _applyPredicate (ret, aPredicate, aCtx);
    return ret;
  }

  /**
   * One axis step from one context node.
   *
   * @return The selected nodes in document order.
   */
  @NonNull
  private static XdmSequence _evalAxisStep (final IExpr.@NonNull StepExpr aStep,
                                            @NonNull final XdmNode aNode,
                                            @NonNull final DynamicContext aCtx)
  {
    final EAxis eAxis = aStep.axis ();
    final NodeTest aTest = aStep.test ();
    final ENodeKind ePrincipal = AxisNavigator.getPrincipalNodeKind (eAxis);
    final List <XdmNode> aNodes = new ArrayList <> ();
    AxisNavigator.collect (eAxis,
                           aNode,
                           aTest == null ? null : x -> NodeTestMatcher.matches (aTest, x, ePrincipal),
                           aNodes);
    XdmSequence ret = _applyPredicates (XdmSequence.of (aNodes), aStep.predicates (), aCtx);
    if (eAxis.isReverse () && ret.size () > 1)
    {
      // Positions were counted in reverse document order; the step result is
      // in document order
      final ICommonsList <IXdmItem> aList = ret.getAsList ();
      Collections.reverse (aList);
      ret = XdmSequence.of (aList);
    }
    return ret;
  }

  @NonNull
  private static XdmNode _getStepContextNode (@NonNull final IXdmItem aItem, @NonNull final String sCode)
  {
    if (aItem instanceof final XdmNode aNode)
      return aNode;
    throw XPathException.of (sCode, "The context item of a path step is not a node");
  }

  /**
   * @return <code>true</code> if all items are nodes (the result needs a document order sort).
   * @throws XPathException
   *         XPTY0018 for a mix of nodes and other items in the last step.
   */
  private static boolean _isAllNodes (@NonNull final XdmSequence aSeq, final boolean bLastStep)
  {
    int nNodes = 0;
    for (final IXdmItem aItem : aSeq)
      if (aItem instanceof XdmNode)
        nNodes++;
    if (nNodes > 0 && nNodes < aSeq.size () && bLastStep)
      throw XPathException.of ("XPTY0018",
                               "The result of the last step of a path expression mixes nodes and non-node items");
    return nNodes == aSeq.size ();
  }

  /** Apply a step (not the first of a relative path) to every item of the previous step. */
  @NonNull
  private static XdmSequence _applyStep (final IExpr.@NonNull StepExpr aStep,
                                         @NonNull final XdmSequence aCurrent,
                                         @NonNull final DynamicContext aCtx,
                                         final boolean bLastStep)
  {
    final int nSize = aCurrent.size ();
    if (nSize == 0)
      return aCurrent;
    if (aStep.postfix () == null)
    {
      if (nSize == 1)
        return _evalAxisStep (aStep, _getStepContextNode (aCurrent.get (0), "XPTY0019"), aCtx);
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aCurrent)
      {
        aCtx.checkInterrupt ();
        aBuilder.addAll (_evalAxisStep (aStep, _getStepContextNode (aItem, "XPTY0019"), aCtx));
      }
      return NodeSorter.sortAndDeduplicate (aBuilder.build ());
    }

    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      final IXdmItem aItem = aCurrent.get (i);
      _getStepContextNode (aItem, "XPTY0019");
      final DynamicContext aSub = aCtx.withFocus (aItem, i + 1, nSize);
      aBuilder.addAll (_applyPredicates (evaluateInternal (aStep.postfix (), aSub), aStep.predicates (), aSub));
    }
    final XdmSequence ret = aBuilder.build ();
    if (_isAllNodes (ret, bLastStep))
      return NodeSorter.sortAndDeduplicate (ret);
    return ret;
  }

  @NonNull
  private static XdmSequence _evalPath (final IExpr.@NonNull PathExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final List <IExpr.StepExpr> aSteps = aExpr.steps ();
    final int nSteps = aSteps.size ();
    XdmSequence aCurrent;
    int nFirst = 0;
    if (aExpr.start () != null)
      aCurrent = evaluateInternal (aExpr.start (), aCtx);
    else
      if (aExpr.absolute ())
      {
        final IXdmItem aItem = _getContextItem (aCtx);
        if (!(aItem instanceof final XdmNode aNode))
          throw XPathException.of ("XPTY0020", "The context item for '/' is not a node");
        final XdmNode aRoot = aNode.getRoot ();
        if (!aRoot.isDocument ())
          throw XPathException.of ("XPDY0050",
                                   "The root of the tree containing the context item is not a document node");
        aCurrent = XdmSequence.of (aRoot);
      }
      else
      {
        if (nSteps == 0)
          return XdmSequence.of (_getContextItem (aCtx));
        final IExpr.StepExpr aFirst = aSteps.get (0);
        if (aFirst.postfix () != null)
        {
          // A filter expression as first step keeps its own order
          aCurrent = _applyPredicates (evaluateInternal (aFirst.postfix (), aCtx), aFirst.predicates (), aCtx);
        }
        else
        {
          final IXdmItem aItem = _getContextItem (aCtx);
          if (!(aItem instanceof final XdmNode aNode))
            throw XPathException.of ("XPTY0020", "The context item for an axis step is not a node");
          aCurrent = _evalAxisStep (aFirst, aNode, aCtx);
        }
        nFirst = 1;
      }
    for (int i = nFirst; i < nSteps; ++i)
      aCurrent = _applyStep (aSteps.get (i), aCurrent, aCtx, i == nSteps - 1);
    return aCurrent;
  }

  @NonNull
  private static XdmSequence _evalVarRef (final IExpr.@NonNull VarRefExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence ret = aCtx.getVariable (aExpr.name ());
    if (ret == null)
      throw XPathException.noContext ("No value is bound to the variable $" + aExpr.name ().getEQName ());
    return ret;
  }

  private static boolean _hasPlaceholder (@NonNull final List <IExpr> aArgs)
  {
    for (final IExpr aArg : aArgs)
      if (aArg instanceof IExpr.PlaceholderExpr)
        return true;
    return false;
  }

  @NonNull
  private static String _functionDisplayName (@Nullable final XdmQName aName)
  {
    if (aName == null)
      return "function item";
    final String sNs = aName.namespaceUri ();
    if (XdmConstants.NS_FN.equals (sNs))
      return "fn:" + aName.localName ();
    if (FunctionLibrary.NS_MATH.equals (sNs))
      return "math:" + aName.localName ();
    if (FunctionLibrary.NS_MAP.equals (sNs))
      return "map:" + aName.localName ();
    if (FunctionLibrary.NS_ARRAY.equals (sNs))
      return "array:" + aName.localName ();
    return aName.getEQName ();
  }

  /**
   * Apply the function conversion rules to one argument; a failure names the function and the argument position.
   */
  @NonNull
  private static XdmSequence _convertArgument (@NonNull final XdmSequence aValue,
                                               @NonNull final SeqType aType,
                                               @NonNull final DynamicContext aCtx,
                                               @Nullable final XdmQName aFunctionName,
                                               final int nArgIndex)
  {
    try
    {
      return SequenceTypeMatcher.convert (aValue, aType, aCtx, "XPTY0004");
    }
    catch (final XPathException ex)
    {
      if (ex.getClass () != XPathException.class || !XPathException.ERR_NAMESPACE.equals (ex.getCodeName ().namespaceUri ()))
        throw ex;
      throw new XPathException (ex.getCode (),
                                _functionDisplayName (aFunctionName) +
                                                ", argument " +
                                                (nArgIndex + 1) +
                                                ": " +
                                                ex.getDescription (),
                                ex);
    }
  }

  @NonNull
  private static XdmSequence _invokeBuiltIn (@NonNull final FunctionDef aDef,
                                             @NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs)
  {
    aCtx.enterCall ();
    try
    {
      return aDef.getImpl ().call (aCtx, aArgs);
    }
    finally
    {
      aCtx.leaveCall ();
    }
  }

  /**
   * Partial application: evaluate and convert the bound arguments now.
   */
  @NonNull
  private static XdmSequence _partialApply (@NonNull final IXdmFunction aFunc,
                                            @NonNull final List <IExpr> aArgs,
                                            @NonNull final DynamicContext aCtx)
  {
    final List <SeqType> aParamTypes = aFunc.getParamTypes ();
    final XdmSequence [] aBound = new XdmSequence [aArgs.size ()];
    for (int i = 0; i < aBound.length; ++i)
    {
      final IExpr aArg = aArgs.get (i);
      if (!(aArg instanceof IExpr.PlaceholderExpr))
        aBound[i] = _convertArgument (evaluateInternal (aArg, aCtx), aParamTypes.get (i), aCtx, aFunc.getName (), i);
    }
    return XdmSequence.of (new PartialFunctionItem (aFunc, aBound));
  }

  @NonNull
  private static FunctionDef _getFunctionDef (@NonNull final DynamicContext aCtx,
                                              @NonNull final XdmQName aName,
                                              final int nArity)
  {
    final FunctionDef ret = aCtx.getStaticContext ().getFunctionLibrary ().getFunction (aName, nArity);
    if (ret == null)
      throw XPathException.of ("XPST0017", "Unknown function " + aName.getEQName () + "#" + nArity);
    return ret;
  }

  @NonNull
  private static XdmSequence _evalFunctionCall (final IExpr.@NonNull FunctionCallExpr aExpr,
                                                @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aArgExprs = aExpr.args ();
    final FunctionDef aDef = _getFunctionDef (aCtx, aExpr.name (), aArgExprs.size ());
    if (_hasPlaceholder (aArgExprs))
      return _partialApply (new BuiltInFunctionItem (aDef, aCtx), aArgExprs, aCtx);

    final List <SeqType> aParamTypes = aDef.getParamTypes ();
    final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> (aArgExprs.size ());
    for (int i = 0; i < aArgExprs.size (); ++i)
    {
      // XPath 1.0 compatibility rules are applied inside convert
      final XdmSequence aValue = evaluateInternal (aArgExprs.get (i), aCtx);
      aArgs.add (_convertArgument (aValue, aParamTypes.get (i), aCtx, aDef.getName (), i));
    }
    return _invokeBuiltIn (aDef, aCtx, aArgs);
  }

  @NonNull
  private static XdmSequence _evalDynamicCall (final IExpr.@NonNull DynamicCallExpr aExpr,
                                               @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aBase = evaluateInternal (aExpr.base (), aCtx);
    if (!aBase.isSingleton ())
      throw XPathException.type ("The target of a dynamic function call must be a single item, but it has " +
                                 aBase.size () +
                                 " items");
    final IXdmItem aFuncItem = aBase.get (0);
    final int nArity = getFunctionArity (aFuncItem);
    if (nArity < 0)
      throw XPathException.type ("The target of a dynamic function call is not a function");
    final List <IExpr> aArgExprs = aExpr.args ();
    if (nArity != aArgExprs.size ())
      throw XPathException.type ("A function of arity " +
                                 nArity +
                                 " is called with " +
                                 aArgExprs.size () +
                                 " arguments");
    if (_hasPlaceholder (aArgExprs))
      return _partialApply (asFunction (aFuncItem), aArgExprs, aCtx);

    final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> (aArgExprs.size ());
    for (final IExpr aArgExpr : aArgExprs)
      aArgs.add (evaluateInternal (aArgExpr, aCtx));
    return callFunction (aCtx, aFuncItem, aArgs);
  }

  /**
   * @return The default collation of the static context, <code>null</code> for the codepoint
   *         collation.
   */
  @Nullable
  private static ICollation _getDefaultCollation (@NonNull final DynamicContext aCtx)
  {
    final String sUri = aCtx.getStaticContext ().getDefaultCollation ();
    return CollationRegistry.CODEPOINT_URI.equals (sUri) ? null : CollationRegistry.getCollationOrThrow (sUri);
  }

  @Nullable
  private static BigInteger _getRangeOperand (@NonNull final XdmSequence aSeq, @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aValue = _atomizeOptional (aSeq, "operand of 'to'");
    if (aValue == null)
      return null;
    if (aValue.getType () == EAtomicType.UNTYPED_ATOMIC)
      aValue = AtomicCaster.castFromString (aValue.getString (), EAtomicType.INTEGER, aCtx.getStaticContext ());
    if (!aValue.getType ().isIntegerFamily ())
      throw XPathException.type ("The operands of 'to' must be integers, not " + aValue.getType ().getQualifiedName ());
    return aValue.getBigInteger ();
  }

  @NonNull
  private static EComparisonOp _swapGeneralOp (@NonNull final EComparisonOp eOp)
  {
    return switch (eOp)
    {
      case GENERAL_LT -> EComparisonOp.GENERAL_GT;
      case GENERAL_GT -> EComparisonOp.GENERAL_LT;
      case GENERAL_LE -> EComparisonOp.GENERAL_GE;
      case GENERAL_GE -> EComparisonOp.GENERAL_LE;
      default -> eOp;
    };
  }

  /**
   * @return <code>true</code> if the xs:integer or xs:decimal value compares true under the
   *         operator with some integer of the range. Both are compared exactly, at any magnitude.
   */
  private static boolean _isRangeHitExact (@NonNull final EComparisonOp eOp,
                                           @NonNull final XdmAtomic aValue,
                                           @NonNull final BigInteger aFrom,
                                           @NonNull final BigInteger aTo)
  {
    final BigDecimal aV = aValue.getValue () instanceof final BigInteger aBigInt ? new BigDecimal (aBigInt)
                                                                                 : aValue.getBigDecimal ();
    final BigDecimal aLo = new BigDecimal (aFrom);
    final BigDecimal aHi = new BigDecimal (aTo);
    return switch (eOp)
    {
      case GENERAL_EQ -> (aV.signum () == 0 || aV.stripTrailingZeros ().scale () <= 0) &&
                         aV.compareTo (aLo) >= 0 &&
                         aV.compareTo (aHi) <= 0;
      case GENERAL_NE -> aFrom.compareTo (aTo) != 0 || aV.compareTo (aLo) != 0;
      case GENERAL_LT -> aV.compareTo (aHi) < 0;
      case GENERAL_LE -> aV.compareTo (aHi) <= 0;
      case GENERAL_GT -> aV.compareTo (aLo) > 0;
      default -> aV.compareTo (aLo) >= 0;
    };
  }

  /**
   * {@link #_isRangeHitExact(EComparisonOp, XdmAtomic, BigInteger, BigInteger)} for an xs:double or
   * xs:float value, to whose type every range member is promoted first. Promotion is monotonic, so
   * the promoted bounds settle each operator; for "!=" some member differs from the value unless
   * both promoted bounds (and so every member between them) equal it.
   */
  private static boolean _isRangeHitFloat (@NonNull final EComparisonOp eOp,
                                           @NonNull final XdmAtomic aValue,
                                           @NonNull final BigInteger aFrom,
                                           @NonNull final BigInteger aTo)
  {
    final double d = aValue.getAsDouble ();
    if (Double.isNaN (d))
      return eOp == EComparisonOp.GENERAL_NE;
    final boolean bFloat = aValue.getType () == EAtomicType.FLOAT;
    final double dLo = bFloat ? aFrom.floatValue () : aFrom.doubleValue ();
    final double dHi = bFloat ? aTo.floatValue () : aTo.doubleValue ();
    return switch (eOp)
    {
      case GENERAL_EQ -> d >= dLo && d <= dHi && d == Math.rint (d);
      case GENERAL_NE -> dLo != d || dHi != d;
      case GENERAL_LT -> d < dHi;
      case GENERAL_LE -> d <= dHi;
      case GENERAL_GT -> d > dLo;
      default -> d >= dLo;
    };
  }

  /**
   * A general comparison one of whose operands is a range expression, without materialising the
   * range (<code>$c = (32 to 55295)</code> is a bounds test; RangeExpr-409c compares against a
   * range of 10^13 integers). The existential semantics are exact: for a numeric v and the integers
   * from..to, "v = range" holds iff v is an integer in [from, to], "v != range" iff some member
   * differs from v, and the orderings compare v against the nearest bound. An empty range makes
   * every comparison false.
   *
   * @param eOp
   *        The operator, oriented so that the other operand is on the left.
   * @param aOtherAtoms
   *        The atomised other operand.
   * @return <code>null</code> if the fast path does not apply (the other operand holds anything but
   *         numeric or untyped atomic values); the caller then takes the ordinary path.
   */
  @Nullable
  private static XdmSequence _evalRangeComparison (@NonNull final EComparisonOp eOp,
                                                   final IExpr.@NonNull RangeExpr aRange,
                                                   @NonNull final XdmSequence aOtherAtoms,
                                                   @NonNull final DynamicContext aCtx)
  {
    final ICommonsList <XdmAtomic> aValues = new CommonsArrayList <> (aOtherAtoms.size ());
    for (final IXdmItem aItem : aOtherAtoms)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aItem;
      if (aAtomic.isNumeric ())
        aValues.add (aAtomic);
      else
        if (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC)
          aValues.add (_untypedToDouble (aAtomic, aCtx));
        else
          return null;
    }
    final BigInteger aFrom = _getRangeOperand (evaluateInternal (aRange.from (), aCtx), aCtx);
    final BigInteger aTo = _getRangeOperand (evaluateInternal (aRange.to (), aCtx), aCtx);
    if (aFrom == null || aTo == null || aFrom.compareTo (aTo) > 0 || aValues.isEmpty ())
      return FALSE_SEQ;
    for (final XdmAtomic aValue : aValues)
    {
      final Object aPayload = aValue.getValue ();
      final boolean bHit;
      if (aPayload instanceof BigInteger || aPayload instanceof BigDecimal)
        bHit = _isRangeHitExact (eOp, aValue, aFrom, aTo);
      else
        bHit = _isRangeHitFloat (eOp, aValue, aFrom, aTo);
      if (bHit)
        return TRUE_SEQ;
    }
    return FALSE_SEQ;
  }

  /** Marks a sequence for which no equality index can be built. */
  private static final Object NO_INDEX = new Object ();
  private static final String RUN_CACHE_EQ_INDEX = "xpath:eq-index";

  /**
   * Build (or get from the run cache) a hash index of a long sequence for general equality: kind 0
   * = numbers as doubles (untypedAtomic cast to xs:double), 1 = integers exactly, 2 = strings by
   * codepoints.
   *
   * @return The set of keys or <code>null</code> if no index is possible.
   */
  @Nullable
  @SuppressWarnings ("unchecked")
  private static Set <Object> _getEqualityIndex (@NonNull final XdmSequence aSeq,
                                                 final int nKind,
                                                 @NonNull final DynamicContext aCtx)
  {
    final Map <String, Object> aCache = aCtx.getRunCache ();
    IdentityHashMap <XdmSequence, Object []> aIndexes = (IdentityHashMap <XdmSequence, Object []>) aCache.get (RUN_CACHE_EQ_INDEX);
    if (aIndexes == null)
    {
      aIndexes = new IdentityHashMap <> ();
      aCache.put (RUN_CACHE_EQ_INDEX, aIndexes);
    }
    final Object [] aSlots = aIndexes.computeIfAbsent (aSeq, k -> new Object [3]);
    if (aSlots[nKind] == null)
    {
      final Set <Object> aSet = new HashSet <> ();
      Object aResult = aSet;
      for (final IXdmItem aItem : _atomize (aSeq))
      {
        final XdmAtomic aAtom = (XdmAtomic) aItem;
        final EAtomicType eType = aAtom.getType ();
        if (nKind == 0 && eType == EAtomicType.UNTYPED_ATOMIC)
        {
          try
          {
            final double d = AtomicCaster.castFromString (aAtom.getString (),
                                                          EAtomicType.DOUBLE,
                                                          aCtx.getStaticContext ()).getAsDouble ();
            // NaN equals nothing; +0 and -0 are equal
            if (!Double.isNaN (d))
              aSet.add (Double.valueOf (d == 0 ? 0d : d));
          }
          catch (final XPathException ex)
          {
            aResult = NO_INDEX;
            break;
          }
        }
        else
          if (nKind == 1 && eType.isIntegerFamily ())
            aSet.add (aAtom.getBigInteger ());
          else
            if (nKind == 2 && aAtom.isStringLike ())
              aSet.add (aAtom.getString ());
            else
            {
              aResult = NO_INDEX;
              break;
            }
      }
      aSlots[nKind] = aResult;
    }
    return aSlots[nKind] == NO_INDEX ? null : (Set <Object>) aSlots[nKind];
  }

  /**
   * General equality of one atomic value against a long sequence via a hash index (the same long
   * sequence is typically compared many times, e.g. in a predicate).
   *
   * @return The result or <code>null</code> if the fast path does not apply.
   */
  @Nullable
  private static XdmSequence _evalIndexedEquality (@NonNull final XdmSequence aLeft,
                                                   @NonNull final XdmSequence aRight,
                                                   @NonNull final DynamicContext aCtx)
  {
    final boolean bLeftBig = aLeft.size () >= 64;
    final XdmSequence aBig = bLeftBig ? aLeft : aRight;
    final XdmSequence aOne = bLeftBig ? aRight : aLeft;
    if (aBig.size () < 64 || aOne.size () != 1 || !(aOne.get (0) instanceof final XdmAtomic aProbe))
      return null;
    if (_getDefaultCollation (aCtx) != null)
      return null;
    final EAtomicType eType = aProbe.getType ();
    if (eType == EAtomicType.UNTYPED_ATOMIC)
      return null;
    if (eType.isIntegerFamily ())
    {
      final Set <Object> aInts = _getEqualityIndex (aBig, 1, aCtx);
      if (aInts != null)
        return _bool (aInts.contains (aProbe.getBigInteger ()));
    }
    if (eType.isNumeric ())
    {
      final Set <Object> aDoubles = _getEqualityIndex (aBig, 0, aCtx);
      if (aDoubles != null)
      {
        final double d = aProbe.getAsDouble ();
        return _bool (!Double.isNaN (d) && aDoubles.contains (Double.valueOf (d == 0 ? 0d : d)));
      }
      return null;
    }
    if (aProbe.isStringLike ())
    {
      final Set <Object> aStrings = _getEqualityIndex (aBig, 2, aCtx);
      if (aStrings != null)
        return _bool (aStrings.contains (aProbe.getString ()));
    }
    return null;
  }

  @NonNull
  private static XdmSequence _evalComparison (final IExpr.@NonNull ComparisonExpr aExpr,
                                              @NonNull final DynamicContext aCtx)
  {
    // XPath 1.0 compatibility mode has its own general comparison rules
    if (aExpr.op ().isGeneral () && !aCtx.getStaticContext ().isXPath10Compatibility ())
    {
      if (aExpr.right () instanceof final IExpr.RangeExpr aRange)
      {
        final XdmSequence aLeft = _atomize (evaluateInternal (aExpr.left (), aCtx));
        final XdmSequence aFast = _evalRangeComparison (aExpr.op (), aRange, aLeft, aCtx);
        if (aFast != null)
          return aFast;
        return _bool (AtomicComparator.generalCompare (aExpr.op (),
                                                       aLeft,
                                                       _atomize (evaluateInternal (aRange, aCtx)),
                                                       _getDefaultCollation (aCtx),
                                                       aCtx));
      }
      if (aExpr.left () instanceof final IExpr.RangeExpr aRange)
      {
        final XdmSequence aRight = _atomize (evaluateInternal (aExpr.right (), aCtx));
        final XdmSequence aFast = _evalRangeComparison (_swapGeneralOp (aExpr.op ()), aRange, aRight, aCtx);
        if (aFast != null)
          return aFast;
        return _bool (AtomicComparator.generalCompare (aExpr.op (),
                                                       _atomize (evaluateInternal (aRange, aCtx)),
                                                       aRight,
                                                       _getDefaultCollation (aCtx),
                                                       aCtx));
      }
    }
    final XdmSequence aLeft = evaluateInternal (aExpr.left (), aCtx);
    final XdmSequence aRight = evaluateInternal (aExpr.right (), aCtx);
    if (aExpr.op () == EComparisonOp.GENERAL_EQ && !aCtx.getStaticContext ().isXPath10Compatibility ())
    {
      final XdmSequence aFast = _evalIndexedEquality (aLeft, aRight, aCtx);
      if (aFast != null)
        return aFast;
    }
    if (aExpr.op ().isGeneral ())
      return _bool (AtomicComparator.generalCompare (aExpr.op (),
                                                     _atomize (aLeft),
                                                     _atomize (aRight),
                                                     _getDefaultCollation (aCtx),
                                                     aCtx));

    if (aExpr.op ().isValue ())
    {
      XdmAtomic aL = _atomizeOptional (aLeft, "left operand of a value comparison");
      if (aL == null)
        return XdmSequence.EMPTY;
      XdmAtomic aR = _atomizeOptional (aRight, "right operand of a value comparison");
      if (aR == null)
        return XdmSequence.EMPTY;
      if (aL.getType () == EAtomicType.UNTYPED_ATOMIC)
        aL = XdmAtomic.string (aL.getString ());
      if (aR.getType () == EAtomicType.UNTYPED_ATOMIC)
        aR = XdmAtomic.string (aR.getString ());
      return _bool (AtomicComparator.valueCompare (aExpr.op (), aL, aR, _getDefaultCollation (aCtx), aCtx));
    }

    // Node comparison
    if (aLeft.isEmpty () || aRight.isEmpty ())
      return XdmSequence.EMPTY;
    if (!aLeft.isSingleton () ||
        !aRight.isSingleton () ||
        !(aLeft.get (0) instanceof final XdmNode aL) ||
        !(aRight.get (0) instanceof final XdmNode aR))
      throw XPathException.type ("The operands of a node comparison must be single nodes");
    return switch (aExpr.op ())
    {
      case NODE_IS -> _bool (aL == aR);
      case NODE_PRECEDES -> _bool (XdmNode.compareDocumentOrder (aL, aR) < 0);
      default -> _bool (XdmNode.compareDocumentOrder (aL, aR) > 0);
    };
  }

  /**
   * An arithmetic operand in XPath 1.0 compatibility mode (XPath 3.1 section 3.5): only the first
   * atomised item counts, an empty operand is NaN, and booleans, strings, untyped and numeric
   * values are converted with fn:number.
   */
  @NonNull
  private static XdmAtomic _arithmeticOperand10 (@NonNull final XdmSequence aSeq)
  {
    final XdmSequence aAtoms = _atomize (aSeq);
    if (aAtoms.isEmpty ())
      return XdmAtomic.dbl (Double.NaN);
    final XdmAtomic aValue = (XdmAtomic) aAtoms.get (0);
    final EAtomicType eType = aValue.getType ();
    if (aValue.isNumeric () ||
        eType == EAtomicType.BOOLEAN ||
        eType.isStringFamily () ||
        eType == EAtomicType.UNTYPED_ATOMIC)
      return XdmAtomic.dbl (AtomicComparator.toNumber (aValue));
    return aValue;
  }

  @NonNull
  private static XdmSequence _evalBinary (final IExpr.@NonNull BinaryExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final EBinaryOp eOp = aExpr.op ();
    if (eOp == EBinaryOp.AND)
      return _bool (_ebv (evaluateInternal (aExpr.left (), aCtx)) && _ebv (evaluateInternal (aExpr.right (), aCtx)));
    if (eOp == EBinaryOp.OR)
      return _bool (_ebv (evaluateInternal (aExpr.left (), aCtx)) || _ebv (evaluateInternal (aExpr.right (), aCtx)));

    final XdmSequence aLeftSeq = evaluateInternal (aExpr.left (), aCtx);
    final XdmSequence aRightSeq = evaluateInternal (aExpr.right (), aCtx);
    if (aCtx.getStaticContext ().isXPath10Compatibility ())
      return XdmSequence.of (AtomicArithmetic.arithmetic (eOp,
                                                          _arithmeticOperand10 (aLeftSeq),
                                                          _arithmeticOperand10 (aRightSeq),
                                                          aCtx));
    final XdmAtomic aLeft = _atomizeOptional (aLeftSeq, "left operand of an arithmetic expression");
    if (aLeft == null)
      return XdmSequence.EMPTY;
    final XdmAtomic aRight = _atomizeOptional (aRightSeq, "right operand of an arithmetic expression");
    if (aRight == null)
      return XdmSequence.EMPTY;
    return XdmSequence.of (AtomicArithmetic.arithmetic (eOp,
                                                        _untypedToDouble (aLeft, aCtx),
                                                        _untypedToDouble (aRight, aCtx),
                                                        aCtx));
  }

  @NonNull
  private static XdmSequence _evalUnary (final IExpr.@NonNull UnaryExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aOperand = evaluateInternal (aExpr.operand (), aCtx);
    final XdmAtomic aNum;
    if (aCtx.getStaticContext ().isXPath10Compatibility ())
      aNum = _arithmeticOperand10 (aOperand);
    else
    {
      final XdmAtomic aValue = _atomizeOptional (aOperand, "operand of a unary operator");
      if (aValue == null)
        return XdmSequence.EMPTY;
      aNum = _untypedToDouble (aValue, aCtx);
    }
    if (aExpr.negate ())
      return XdmSequence.of (AtomicArithmetic.negate (aNum));
    if (!aNum.isNumeric ())
      throw XPathException.type ("The operand of unary plus must be numeric, not " +
                                 aNum.getType ().getQualifiedName ());
    return XdmSequence.of (aNum);
  }

  @NonNull
  private static XdmSequence _evalStringConcat (final IExpr.@NonNull StringConcatExpr aExpr,
                                                @NonNull final DynamicContext aCtx)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IExpr aPart : aExpr.parts ())
    {
      final XdmAtomic aValue = _atomizeOptional (evaluateInternal (aPart, aCtx), "operand of '||'");
      if (aValue != null)
        aSB.append (_getStringValue (aValue));
    }
    return XdmSequence.of (XdmAtomic.string (aSB.toString ()));
  }

  @NonNull
  private static XdmSequence _checkNodes (@NonNull final XdmSequence aSeq, @NonNull final String sOp)
  {
    for (final IXdmItem aItem : aSeq)
      if (!(aItem instanceof XdmNode))
        throw XPathException.type ("The operands of '" + sOp + "' must be nodes");
    return aSeq;
  }

  @NonNull
  private static XdmSequence _evalSet (final IExpr.@NonNull SetExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final String sOp = aExpr.op ().name ().toLowerCase (Locale.ROOT);
    final XdmSequence aLeft = _checkNodes (evaluateInternal (aExpr.left (), aCtx), sOp);
    final XdmSequence aRight = _checkNodes (evaluateInternal (aExpr.right (), aCtx), sOp);
    if (aExpr.op () == IExpr.SetExpr.ESetOp.UNION)
      return NodeSorter.sortAndDeduplicate (XdmSequence.concat (aLeft, aRight));

    final Set <IXdmItem> aRightSet = Collections.newSetFromMap (new IdentityHashMap <> ());
    for (final IXdmItem aItem : aRight)
      aRightSet.add (aItem);
    final boolean bIntersect = aExpr.op () == IExpr.SetExpr.ESetOp.INTERSECT;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aLeft)
      if (aRightSet.contains (aItem) == bIntersect)
        aBuilder.add (aItem);
    return NodeSorter.sortAndDeduplicate (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _evalRange (final IExpr.@NonNull RangeExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aFromSeq = evaluateInternal (aExpr.from (), aCtx);
    final XdmSequence aToSeq = evaluateInternal (aExpr.to (), aCtx);
    final BigInteger aFrom = _getRangeOperand (aFromSeq, aCtx);
    final BigInteger aTo = _getRangeOperand (aToSeq, aCtx);
    if (aFrom == null || aTo == null || aFrom.compareTo (aTo) > 0)
      return XdmSequence.EMPTY;
    final BigInteger aSpan = aTo.subtract (aFrom);
    if (aSpan.compareTo (BigInteger.valueOf (MAX_RANGE_SIZE)) >= 0)
      throw XPathException.of ("XPDY0130", "The integer range " + aFrom + " to " + aTo + " is too large");
    final int nCount = aSpan.intValue () + 1;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    if (aFrom.bitLength () < 63 && aTo.bitLength () < 63)
    {
      final long nFrom = aFrom.longValue ();
      for (int i = 0; i < nCount; ++i)
      {
        if ((i & 4095) == 0)
          aCtx.checkInterrupt ();
        aBuilder.add (XdmAtomic.integer (nFrom + i));
      }
    }
    else
      for (int i = 0; i < nCount; ++i)
      {
        if ((i & 4095) == 0)
          aCtx.checkInterrupt ();
        aBuilder.add (XdmAtomic.integer (aFrom.add (BigInteger.valueOf (i))));
      }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _bindingValue (final IExpr.@NonNull Binding aBinding,
                                            @NonNull final XdmSequence aValue,
                                            @NonNull final DynamicContext aCtx)
  {
    if (aBinding.type () == null)
      return aValue;
    return SequenceTypeMatcher.convert (aValue, aBinding.type (), aCtx, "XPTY0004");
  }

  private static void _evalFor (@NonNull final List <IExpr.Binding> aBindings,
                                final int nIndex,
                                @NonNull final IExpr aBody,
                                @NonNull final DynamicContext aCtx,
                                final XdmSequence.@NonNull Builder aOut)
  {
    if (nIndex == aBindings.size ())
    {
      aOut.addAll (evaluateInternal (aBody, aCtx));
      return;
    }
    final IExpr.Binding aBinding = aBindings.get (nIndex);
    final XdmSequence aSeq = evaluateInternal (aBinding.expr (), aCtx);
    for (final IXdmItem aItem : aSeq)
    {
      aCtx.checkInterrupt ();
      final XdmSequence aValue = _bindingValue (aBinding, XdmSequence.of (aItem), aCtx);
      _evalFor (aBindings, nIndex + 1, aBody, aCtx.withVariable (aBinding.name (), aValue), aOut);
    }
  }

  private static boolean _evalQuantified (final IExpr.@NonNull QuantifiedExpr aExpr,
                                          final int nIndex,
                                          @NonNull final DynamicContext aCtx)
  {
    final List <IExpr.Binding> aBindings = aExpr.bindings ();
    if (nIndex == aBindings.size ())
      return _ebv (evaluateInternal (aExpr.satisfies (), aCtx));
    final IExpr.Binding aBinding = aBindings.get (nIndex);
    final boolean bEvery = aExpr.every ();
    for (final IXdmItem aItem : evaluateInternal (aBinding.expr (), aCtx))
    {
      aCtx.checkInterrupt ();
      final XdmSequence aValue = _bindingValue (aBinding, XdmSequence.of (aItem), aCtx);
      final boolean b = _evalQuantified (aExpr, nIndex + 1, aCtx.withVariable (aBinding.name (), aValue));
      if (bEvery && !b)
        return false;
      if (!bEvery && b)
        return true;
    }
    return bEvery;
  }

  @NonNull
  private static XdmSequence _evalLet (final IExpr.@NonNull LetExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    DynamicContext aCur = aCtx;
    for (final IExpr.Binding aBinding : aExpr.bindings ())
      aCur = aCur.withVariable (aBinding.name (),
                                _bindingValue (aBinding, evaluateInternal (aBinding.expr (), aCur), aCur));
    return evaluateInternal (aExpr.body (), aCur);
  }

  @NonNull
  private static XdmSequence _evalSimpleMap (final IExpr.@NonNull SimpleMapExpr aExpr,
                                             @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aSteps = aExpr.steps ();
    XdmSequence aCur = evaluateInternal (aSteps.get (0), aCtx);
    for (int nStep = 1; nStep < aSteps.size (); ++nStep)
    {
      final IExpr aStep = aSteps.get (nStep);
      final int nSize = aCur.size ();
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (int i = 0; i < nSize; ++i)
      {
        aCtx.checkInterrupt ();
        aBuilder.addAll (evaluateInternal (aStep, aCtx.withFocus (aCur.get (i), i + 1, nSize)));
      }
      aCur = aBuilder.build ();
    }
    return aCur;
  }

  @NonNull
  private static XdmSequence _castToList (@NonNull final XdmAtomic aValue,
                                          @NonNull final EAtomicType eItemType,
                                          @NonNull final DynamicContext aCtx)
  {
    if (!aValue.getType ().isStringFamily () && aValue.getType () != EAtomicType.UNTYPED_ATOMIC)
      throw XPathException.type ("Only strings can be cast to a list type, not " +
                                 aValue.getType ().getQualifiedName ());
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final String sToken : aValue.getString ().split ("[ \\t\\r\\n]+"))
      if (!sToken.isEmpty ())
        aBuilder.add (AtomicCaster.castFromString (sToken, eItemType, aCtx.getStaticContext ()));
    if (aBuilder.size () == 0)
      throw XPathException.invalidValue ("A value of a list type must have at least one item");
    return aBuilder.build ();
  }

  @NonNull
  private static XdmAtomic _castAtomic (@NonNull final XdmAtomic aValue,
                                        @NonNull final EAtomicType eTarget,
                                        @NonNull final DynamicContext aCtx)
  {
    if (eTarget == EAtomicType.NUMERIC)
    {
      // Casting to the union xs:numeric tries its members in order
      if (aValue.isNumeric ())
        return aValue;
      return AtomicCaster.cast (aValue, EAtomicType.DOUBLE, aCtx.getStaticContext ());
    }
    return AtomicCaster.cast (aValue, eTarget, aCtx.getStaticContext ());
  }

  private static boolean _isCastable (@NonNull final XdmAtomic aValue,
                                      @NonNull final EAtomicType eTarget,
                                      @NonNull final DynamicContext aCtx)
  {
    if (eTarget == EAtomicType.NUMERIC)
      return aValue.isNumeric () || AtomicCaster.isCastable (aValue, EAtomicType.DOUBLE, aCtx.getStaticContext ());
    return AtomicCaster.isCastable (aValue, eTarget, aCtx.getStaticContext ());
  }

  /**
   * Cast or castable to a user-defined simple type of the in-scope schema definitions.
   */
  @NonNull
  private static XdmSequence _evalSchemaCast (final IExpr.@NonNull CastExpr aExpr,
                                              @NonNull final IXdmTypeDefinition aTarget,
                                              @NonNull final DynamicContext aCtx)
  {
    final SeqType aType = aExpr.type ();
    final boolean bCastable = aExpr.castable ();
    final XdmSequence aAtoms = _atomize (evaluateInternal (aExpr.operand (), aCtx));
    if (aAtoms.isEmpty ())
    {
      final boolean bAllowEmpty = aType.occurrence ().allowsEmpty ();
      if (bCastable)
        return _bool (bAllowEmpty);
      if (bAllowEmpty)
        return XdmSequence.EMPTY;
      throw XPathException.type ("An empty sequence cannot be cast to a user-defined type");
    }
    if (aAtoms.size () > 1)
    {
      if (bCastable)
        return FALSE_SEQ;
      throw XPathException.type ("A sequence of " + aAtoms.size () + " items cannot be cast");
    }
    final XdmAtomic aValue = (XdmAtomic) aAtoms.get (0);
    if (!bCastable)
      return SchemaTypeOps.cast (aValue, aTarget, aCtx.getStaticContext ());
    try
    {
      SchemaTypeOps.cast (aValue, aTarget, aCtx.getStaticContext ());
      return TRUE_SEQ;
    }
    catch (final XPathException ex)
    {
      return FALSE_SEQ;
    }
  }

  @NonNull
  private static XdmSequence _evalCast (final IExpr.@NonNull CastExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final SeqType aType = aExpr.type ();
    final IXdmTypeDefinition aSchemaTarget = aType.itemType ().schemaType ();
    if (aSchemaTarget != null)
      return _evalSchemaCast (aExpr, aSchemaTarget, aCtx);
    final EAtomicType eTarget = aType.itemType ().atomicType ();
    if (eTarget == EAtomicType.ANY_ATOMIC_TYPE || eTarget == EAtomicType.NOTATION)
      throw XPathException.of ("XPST0080", eTarget.getQualifiedName () + " is not a valid cast target");
    final boolean bCastable = aExpr.castable ();
    final XdmSequence aAtoms = _atomize (evaluateInternal (aExpr.operand (), aCtx));
    if (aAtoms.isEmpty ())
    {
      final boolean bAllowEmpty = aType.occurrence ().allowsEmpty ();
      if (bCastable)
        return _bool (bAllowEmpty);
      if (bAllowEmpty)
        return XdmSequence.EMPTY;
      throw XPathException.type ("An empty sequence cannot be cast to " + eTarget.getQualifiedName ());
    }
    if (aAtoms.size () > 1)
    {
      if (bCastable)
        return FALSE_SEQ;
      throw XPathException.type ("A sequence of " + aAtoms.size () + " items cannot be cast");
    }
    final XdmAtomic aValue = (XdmAtomic) aAtoms.get (0);
    if (aType.listCast ())
    {
      if (!bCastable)
        return _castToList (aValue, eTarget, aCtx);
      try
      {
        _castToList (aValue, eTarget, aCtx);
        return TRUE_SEQ;
      }
      catch (final XPathException ex)
      {
        return FALSE_SEQ;
      }
    }
    if (bCastable)
      return _bool (_isCastable (aValue, eTarget, aCtx));
    return XdmSequence.of (_castAtomic (aValue, eTarget, aCtx));
  }

  @NonNull
  private static XdmSequence _evalMapConstructor (final IExpr.@NonNull MapConstructorExpr aExpr,
                                                  @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aKeys = aExpr.keys ();
    final List <IExpr> aValues = aExpr.values ();
    final XdmMap.Builder aMap = new XdmMap.Builder (aKeys.size ());
    for (int i = 0; i < aKeys.size (); ++i)
    {
      final XdmSequence aKeySeq = _atomize (evaluateInternal (aKeys.get (i), aCtx));
      if (!aKeySeq.isSingleton ())
        throw XPathException.type ("A map key must be a single atomic value, but it has " + aKeySeq.size () + " items");
      final XdmAtomic aKey = (XdmAtomic) aKeySeq.get (0);
      final XdmSequence aValue = evaluateInternal (aValues.get (i), aCtx);
      if (!aMap.putIfAbsent (aKey, aValue))
        throw XPathException.of ("XQDY0137", "Duplicate key in a map constructor");
    }
    return XdmSequence.of (aMap.build ());
  }

  @NonNull
  private static XdmSequence _evalArrayConstructor (final IExpr.@NonNull ArrayConstructorExpr aExpr,
                                                    @NonNull final DynamicContext aCtx)
  {
    final ICommonsList <XdmSequence> aMembers = new CommonsArrayList <> ();
    for (final IExpr aItemExpr : aExpr.items ())
    {
      final XdmSequence aValue = evaluateInternal (aItemExpr, aCtx);
      if (aExpr.curly ())
      {
        for (final IXdmItem aItem : aValue)
          aMembers.add (XdmSequence.of (aItem));
      }
      else
        aMembers.add (aValue);
    }
    return XdmSequence.of (XdmArray.of (aMembers));
  }

  @NonNull
  private static XdmSequence _getArrayMember (@NonNull final XdmArray aArray,
                                              @NonNull final XdmAtomic aKey,
                                              @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aIndex = aKey;
    if (aIndex.getType () == EAtomicType.UNTYPED_ATOMIC)
      aIndex = AtomicCaster.castFromString (aIndex.getString (), EAtomicType.INTEGER, aCtx.getStaticContext ());
    if (!aIndex.getType ().isIntegerFamily ())
      throw XPathException.type ("An array index must be an xs:integer, not " + aIndex.getType ().getQualifiedName ());
    final BigInteger aBigIndex = aIndex.getBigInteger ();
    if (aBigIndex.signum () <= 0 || aBigIndex.compareTo (BigInteger.valueOf (aArray.size ())) > 0)
      throw XPathException.of ("FOAY0001",
                               "Array index " + aBigIndex + " is out of bounds (1.." + aArray.size () + ")");
    return aArray.get (aBigIndex.intValue () - 1);
  }

  @NonNull
  private static XdmSequence _evalLookup (final IExpr.@NonNull LookupExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aBase = aExpr.base () == null ? XdmSequence.of (_getContextItem (aCtx))
                                                    : evaluateInternal (aExpr.base (), aCtx);
    final XdmSequence aKeys = aExpr.key () == null ? null : _atomize (evaluateInternal (aExpr.key (), aCtx));
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aBase)
    {
      if (aItem instanceof final XdmMap aMap)
      {
        if (aKeys == null)
        {
          for (final XdmMap.Entry aEntry : aMap.getAllEntries ())
            aBuilder.addAll (aEntry.value ());
        }
        else
          for (final IXdmItem aKey : aKeys)
          {
            final XdmSequence aValue = aMap.get ((XdmAtomic) aKey);
            if (aValue != null)
              aBuilder.addAll (aValue);
          }
      }
      else
        if (aItem instanceof final XdmArray aArray)
        {
          if (aKeys == null)
          {
            for (int i = 0; i < aArray.size (); ++i)
              aBuilder.addAll (aArray.get (i));
          }
          else
            for (final IXdmItem aKey : aKeys)
              aBuilder.addAll (_getArrayMember (aArray, (XdmAtomic) aKey, aCtx));
        }
        else
          throw XPathException.type ("The lookup operator requires a map or an array");
    }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _evalSequence (final IExpr.@NonNull SequenceExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final List <IExpr> aItems = aExpr.items ();
    if (aItems.size () == 1)
      return evaluateInternal (aItems.get (0), aCtx);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IExpr aItem : aItems)
      aBuilder.addAll (evaluateInternal (aItem, aCtx));
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _evalForExpr (final IExpr.@NonNull ForExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    _evalFor (aExpr.bindings (), 0, aExpr.body (), aCtx, aBuilder);
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _evalTreat (final IExpr.@NonNull TreatExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aValue = evaluateInternal (aExpr.operand (), aCtx);
    if (!SequenceTypeMatcher.matches (aValue, aExpr.type ()))
      throw XPathException.of ("XPDY0050",
                               "The value does not match the type " + SequenceTypeMatcher.getAsString (aExpr.type ()));
    return aValue;
  }

  @NonNull
  private static XdmSequence _evalNamedFunctionRef (final IExpr.@NonNull NamedFunctionRefExpr aExpr,
                                                    @NonNull final DynamicContext aCtx)
  {
    final FunctionDef aDef = _getFunctionDef (aCtx, aExpr.name (), aExpr.arity ());
    return XdmSequence.of (new BuiltInFunctionItem (aDef, aDef.isFocusDependent () ? aCtx : null));
  }

  /**
   * The recursive evaluation entry, used by the function items of this package. Errors from a too
   * deep Java stack are mapped once in {@link #evaluate(IExpr, DynamicContext)}.
   */
  @NonNull
  static XdmSequence evaluateInternal (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    return switch (aExpr)
    {
      case final IExpr.LiteralExpr e -> XdmSequence.of (e.value ());
      case final IExpr.VarRefExpr e -> _evalVarRef (e, aCtx);
      case final IExpr.ContextItemExpr e -> XdmSequence.of (_getContextItem (aCtx));
      case final IExpr.FunctionCallExpr e -> _evalFunctionCall (e, aCtx);
      case final IExpr.PlaceholderExpr e -> throw XPathException.syntax ("An argument placeholder is only allowed in a function call");
      case final IExpr.NamedFunctionRefExpr e -> _evalNamedFunctionRef (e, aCtx);
      case final IExpr.InlineFunctionExpr e -> XdmSequence.of (new InlineFunctionItem (e, aCtx));
      case final IExpr.DynamicCallExpr e -> _evalDynamicCall (e, aCtx);
      case final IExpr.SequenceExpr e -> _evalSequence (e, aCtx);
      case final IExpr.BinaryExpr e -> _evalBinary (e, aCtx);
      case final IExpr.UnaryExpr e -> _evalUnary (e, aCtx);
      case final IExpr.ComparisonExpr e -> _evalComparison (e, aCtx);
      case final IExpr.SetExpr e -> _evalSet (e, aCtx);
      case final IExpr.RangeExpr e -> _evalRange (e, aCtx);
      case final IExpr.StringConcatExpr e -> _evalStringConcat (e, aCtx);
      case final IExpr.IfExpr e -> _ebv (evaluateInternal (e.condition (), aCtx)) ? evaluateInternal (e.thenExpr (),
                                                                                                      aCtx)
                                                                                  : evaluateInternal (e.elseExpr (),
                                                                                                      aCtx);
      case final IExpr.ForExpr e -> _evalForExpr (e, aCtx);
      case final IExpr.LetExpr e -> _evalLet (e, aCtx);
      case final IExpr.QuantifiedExpr e -> _bool (_evalQuantified (e, 0, aCtx));
      case final IExpr.SimpleMapExpr e -> _evalSimpleMap (e, aCtx);
      case final IExpr.PathExpr e -> _evalPath (e, aCtx);
      case final IExpr.FilterExpr e -> _applyPredicates (evaluateInternal (e.primary (), aCtx), e.predicates (), aCtx);
      case final IExpr.InstanceOfExpr e -> _bool (SequenceTypeMatcher.matches (evaluateInternal (e.operand (), aCtx),
                                                                               e.type ()));
      case final IExpr.TreatExpr e -> _evalTreat (e, aCtx);
      case final IExpr.CastExpr e -> _evalCast (e, aCtx);
      case final IExpr.MapConstructorExpr e -> _evalMapConstructor (e, aCtx);
      case final IExpr.ArrayConstructorExpr e -> _evalArrayConstructor (e, aCtx);
      case final IExpr.LookupExpr e -> _evalLookup (e, aCtx);
    };
  }

  /**
   * @param aExpr
   *        Parsed expression.
   * @param aCtx
   *        Dynamic context.
   * @return The result sequence; node sequences produced by path expressions are in document order
   *         without duplicates.
   * @throws com.helger.xpath.XPathException
   *         for dynamic and type errors.
   */
  @NonNull
  public static XdmSequence evaluate (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    try
    {
      return evaluateInternal (aExpr, aCtx);
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The evaluation is nested too deeply", ex);
    }
  }

  /**
   * @param aItem
   *        Any item.
   * @return The arity if the item is a function item (1 for maps and arrays), -1 otherwise.
   */
  public static int getFunctionArity (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return aFunc.getArity ();
    if (aItem instanceof XdmMap || aItem instanceof XdmArray)
      return 1;
    return -1;
  }

  /**
   * @param aItem
   *        A function, map or array.
   * @return The item as {@link IXdmFunction}; maps and arrays are wrapped.
   * @throws XPathException
   *         XPTY0004 for other items.
   */
  @NonNull
  public static IXdmFunction asFunction (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return aFunc;
    if (aItem instanceof XdmMap || aItem instanceof XdmArray)
      return new MapArrayFunctionItem (aItem);
    throw XPathException.type ("The item is not a function");
  }

  /**
   * Call a function item dynamically: check the arity, apply the function conversion rules to the
   * arguments, invoke the function within
   * {@link DynamicContext#enterCall()}/{@link DynamicContext#leaveCall()}, and convert the result
   * to the declared return type. Maps and arrays are functions of arity one. This is the entry
   * point for higher-order functions in other packages.
   *
   * @param aCtx
   *        The caller's dynamic context.
   * @param aFuncItem
   *        A function, map or array.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         XPTY0004 for a non-function or an arity mismatch, FOAY0001 for an array index out of
   *         bounds.
   */
  @NonNull
  public static XdmSequence callFunction (@NonNull final DynamicContext aCtx,
                                          @NonNull final IXdmItem aFuncItem,
                                          @NonNull final List <XdmSequence> aArgs)
  {
    final int nArity = getFunctionArity (aFuncItem);
    if (nArity < 0)
      throw XPathException.type ("The item is not a function");
    if (aArgs.size () != nArity)
      throw XPathException.type ("A function of arity " + nArity + " is called with " + aArgs.size () + " arguments");

    if (aFuncItem instanceof final XdmMap aMap)
    {
      final XdmSequence aKey = SequenceTypeMatcher.convert (aArgs.get (0),
                                                            SequenceTypeMatcher.ANY_ATOMIC_ONE,
                                                            aCtx,
                                                            "XPTY0004");
      final XdmSequence ret = aMap.get ((XdmAtomic) aKey.get (0));
      return ret == null ? XdmSequence.EMPTY : ret;
    }
    if (aFuncItem instanceof final XdmArray aArray)
    {
      final XdmSequence aIndex = SequenceTypeMatcher.convert (aArgs.get (0),
                                                              SequenceTypeMatcher.INTEGER_ONE,
                                                              aCtx,
                                                              "XPTY0004");
      return _getArrayMember (aArray, (XdmAtomic) aIndex.get (0), aCtx);
    }

    final IXdmFunction aFunc = (IXdmFunction) aFuncItem;
    final List <SeqType> aParamTypes = aFunc.getParamTypes ();
    final ICommonsList <XdmSequence> aConverted = new CommonsArrayList <> (nArity);
    for (int i = 0; i < nArity; ++i)
      aConverted.add (_convertArgument (aArgs.get (i), aParamTypes.get (i), aCtx, aFunc.getName (), i));
    aCtx.checkInterrupt ();
    final XdmSequence aResult;
    aCtx.enterCall ();
    try
    {
      aResult = aFunc.call (aCtx, aConverted);
    }
    finally
    {
      aCtx.leaveCall ();
    }
    if (aFunc instanceof final AbstractFunctionItem aOwn && aOwn.isResultTrusted ())
      return aResult;
    return SequenceTypeMatcher.convert (aResult, aFunc.getReturnType (), aCtx, "XPTY0004");
  }
}
