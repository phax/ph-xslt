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
package com.helger.xslt.stream;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.stream.EPosture;
import com.helger.xpath.stream.ESweep;
import com.helger.xpath.stream.StreamContext;
import com.helger.xpath.stream.XPathStreamability;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.runtime.XsltContext;

/**
 * The expression selecting the records of a streamed document, from the
 * document node: child steps only, each an element name test or
 * <code>*</code>, the last one also <code>node()</code>. A step may have
 * predicates that are motionless and non-positional and whose value is
 * statically boolean or a node sequence, so that whether a node is selected
 * depends on nothing but the node itself and its ancestors: on an inner step
 * it is decided when the start tag is read, on the last one when the record is
 * complete. A port of go-xslt's <code>strbStep</code>, which allows a
 * predicate on the last step only. The grammar is this small because it is
 * the subset for which a record can never contain another record, so that
 * each record is read once, in full, and dropped.
 *
 * @author Philip Helger
 */
@Immutable
public final class StreamRecordPath
{
  /** The built-in functions whose result is a boolean. */
  private static final ICommonsSet <String> BOOLEAN_FUNCS = new CommonsHashSet <> ("not",
                                                                                   "exists",
                                                                                   "empty",
                                                                                   "boolean",
                                                                                   "true",
                                                                                   "false",
                                                                                   "contains",
                                                                                   "starts-with",
                                                                                   "ends-with",
                                                                                   "matches",
                                                                                   "lang",
                                                                                   "deep-equal",
                                                                                   "has-children");

  private final XPathExpr m_aSelect;
  private final ICommonsList <NodeTest> m_aSteps;
  private final ICommonsList <List <IExpr>> m_aPredicates;

  private StreamRecordPath (@NonNull final XPathExpr aSelect,
                            @NonNull final ICommonsList <NodeTest> aSteps,
                            @NonNull final ICommonsList <List <IExpr>> aPredicates)
  {
    m_aSelect = aSelect;
    m_aSteps = aSteps;
    m_aPredicates = aPredicates;
  }

  @Nonnegative
  public int getLength ()
  {
    return m_aSteps.size ();
  }

  /**
   * @param nIndex
   *        0-based step index.
   * @param aNode
   *        The node at depth <code>nIndex + 1</code>.
   * @return <code>true</code> if the node test of the step selects it. The
   *         predicates are not applied.
   */
  public boolean matches (@Nonnegative final int nIndex, @NonNull final IXdmNode aNode)
  {
    final NodeTest aTest = m_aSteps.get (nIndex);
    if (aTest.kind () == ENodeTestKind.ANY_NODE)
      return !aNode.isAttribute () && !aNode.isDocument ();
    if (!aNode.isElement ())
      return false;
    return aTest.anyName () || aTest.name ().equals (aNode.getName ());
  }

  /**
   * @param nIndex
   *        0-based step index.
   * @return <code>true</code> if the step has predicates.
   */
  public boolean hasPredicates (@Nonnegative final int nIndex)
  {
    return !m_aPredicates.get (nIndex).isEmpty ();
  }

  /**
   * Apply the predicates of a step. An inner step is applied when the start
   * tag of the node is read, the last step when the record is complete; both
   * give the answer of the complete document, since the predicates are
   * motionless.
   *
   * @param nIndex
   *        0-based step index.
   * @param aNode
   *        The node the node test selected.
   * @param aCtx
   *        The run-time context of the consuming instruction (its variables).
   * @return <code>true</code> if every predicate is true for the node.
   */
  public boolean acceptsPredicates (@Nonnegative final int nIndex,
                                    @NonNull final IXdmNode aNode,
                                    @NonNull final XsltContext aCtx)
  {
    final List <IExpr> aPredicates = m_aPredicates.get (nIndex);
    if (aPredicates.isEmpty ())
      return true;
    final XsltContext aFocus = aCtx.withFocus (aNode, 1, 1);
    for (final IExpr aPredicate : aPredicates)
      if (!XPathEvaluator.evaluateEffectiveBooleanValue (aPredicate, m_aSelect.createDynamicContext (aFocus)))
        return false;
    return true;
  }

  private static boolean _canMatchSame (@NonNull final NodeTest a, @NonNull final NodeTest b)
  {
    if (a.kind () == ENodeTestKind.ANY_NODE || b.kind () == ENodeTestKind.ANY_NODE || a.anyName () || b.anyName ())
      return true;
    return a.name ().equals (b.name ());
  }

  /**
   * @param aOther
   *        Another record path over the same document.
   * @return <code>true</code> if a record of one path could contain a record
   *         of the other: the shorter path matches, step by step (predicates
   *         ignored), a node the longer one passes through. Two such paths
   *         cannot be read in one pass record by record.
   */
  public boolean isNestedWith (@NonNull final StreamRecordPath aOther)
  {
    if (getLength () == aOther.getLength ())
      return false;
    final int nShort = Math.min (getLength (), aOther.getLength ());
    for (int i = 0; i < nShort; ++i)
      if (!_canMatchSame (m_aSteps.get (i), aOther.m_aSteps.get (i)))
        return false;
    return true;
  }

  /**
   * @return <code>true</code> if the predicate yields a boolean or a node
   *         sequence whatever the input, never a number (which would make it
   *         a positional predicate).
   */
  private static boolean _isBooleanOrNodes (@NonNull final IExpr aExpr)
  {
    return switch (aExpr)
    {
      case final IExpr.ComparisonExpr e -> true;
      case final IExpr.QuantifiedExpr e -> true;
      case final IExpr.InstanceOfExpr e -> true;
      case final IExpr.CastExpr e -> e.castable ();
      case final IExpr.BinaryExpr e -> e.op () == EBinaryOp.AND || e.op () == EBinaryOp.OR;
      case final IExpr.FunctionCallExpr e -> XdmConstants.NS_FN.equals (e.name ().namespaceUri ()) &&
                                              BOOLEAN_FUNCS.contains (e.name ().localName ());
      case final IExpr.PathExpr e -> !e.steps ().isEmpty () &&
                                     e.steps ().get (e.steps ().size () - 1).axis () != null &&
                                     e.steps ().get (e.steps ().size () - 1).test () != null &&
                                     e.steps ().get (e.steps ().size () - 1).postfix () == null;
      default -> false;
    };
  }

  private static boolean _isAcceptedPredicate (@NonNull final IExpr aPredicate)
  {
    if (!_isBooleanOrNodes (aPredicate) || !XPathStreamability.isNonPositionalPredicate (aPredicate))
      return false;
    return XPathStreamability.getStreamability (aPredicate, new StreamContext (EPosture.STRIDING)).sweep () ==
           ESweep.MOTIONLESS;
  }

  /**
   * @param aSelect
   *        The select expression of the consumer, evaluated with the document
   *        node as context item.
   * @return The record path, or <code>null</code> if the expression is not
   *         one.
   */
  @Nullable
  public static StreamRecordPath of (@Nullable final XPathExpr aSelect)
  {
    if (aSelect == null)
      return null;
    if (!(aSelect.getExpression () instanceof final IExpr.PathExpr aPath) || aPath.start () != null)
      return null;
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    if (aSteps.isEmpty ())
      return null;
    final ICommonsList <NodeTest> aTests = new CommonsArrayList <> ();
    final ICommonsList <List <IExpr>> aPredicates = new CommonsArrayList <> ();
    for (int i = 0; i < aSteps.size (); ++i)
    {
      final IExpr.StepExpr aStep = aSteps.get (i);
      if (aStep.axis () != EAxis.CHILD || aStep.postfix () != null || aStep.fused () != null || aStep.test () == null)
        return null;
      for (final IExpr aPredicate : aStep.predicates ())
        if (!_isAcceptedPredicate (aPredicate))
          return null;
      final NodeTest aTest = aStep.test ();
      final boolean bLast = i == aSteps.size () - 1;
      if (aTest.kind () == ENodeTestKind.ANY_NODE)
      {
        if (!bLast)
          return null;
      }
      else
        if (aTest.kind () != ENodeTestKind.NAME ||
            aTest.anyNamespace () ||
            aTest.anyLocalName () ||
            (!aTest.anyName () && aTest.name () == null))
          return null;
      aTests.add (aTest);
      aPredicates.add (aStep.predicates ());
    }
    return new StreamRecordPath (aSelect, aTests, aPredicates);
  }
}
