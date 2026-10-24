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
package com.helger.xpath.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.IExpr.StepExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * A narrow, purely structural static-emptiness check for axis steps, the part
 * of XPath 3.1's optional Static Typing Feature that this engine implements: a
 * step whose static type is provably <code>empty-sequence()</code> raises
 * XPST0005. It is a port of go-xslt's <code>static_axes.go</code>.
 * <p>
 * An axis step can sometimes be proven to select nothing from the axis and
 * node-test shape of the preceding steps alone, without schema or data: the
 * attribute axis never selects anything from a document node,
 * <code>self::foo</code> never matches a node a previous step pinned to a
 * different element name, and so on. Every axis not reasoned about below
 * (ancestor, ancestor-or-self, following, preceding, the sibling axes,
 * namespace) and every name test with a wildcard or a prefix degrades to
 * "unknown", which never claims emptiness. The goal is zero false positives.
 * <p>
 * The check is only applied when
 * {@link com.helger.xpath.context.StaticContext#isStaticTyping()} is set:
 * outside the feature the same construct evaluates to an empty sequence.
 *
 * @author Philip Helger
 */
@Immutable
final class XPathStaticAxes
{
  /** The coarse node kind a step may produce. */
  private enum EStepKind
  {
    /** Unknown; never proves emptiness. */
    ANY,
    DOCUMENT,
    ELEMENT,
    ATTRIBUTE,
    TEXT,
    COMMENT,
    PI,
    NAMESPACE
  }

  /**
   * What is statically known about the nodes a step may produce.
   *
   * @param kind
   *        The node kind.
   * @param name
   *        The exact name, or <code>null</code> if not known.
   * @param parentless
   *        <code>true</code> if the nodes are known to have no parent,
   *        independent of their kind (the result of
   *        <code>fn:parse-xml-fragment</code>).
   */
  private record Shape (@NonNull EStepKind kind, @Nullable XdmQName name, boolean parentless)
  {
    static final Shape UNKNOWN = new Shape (EStepKind.ANY, null, false);
    static final Shape UNKNOWN_PARENTLESS = new Shape (EStepKind.ANY, null, true);
  }

  @PresentForCodeCoverage
  private static final XPathStaticAxes INSTANCE = new XPathStaticAxes ();

  private XPathStaticAxes ()
  {}

  /**
   * The principal node kind for name tests: attribute names on the attribute
   * axis, namespace names on the namespace axis, element names elsewhere.
   */
  @NonNull
  private static EStepKind _principalKind (@NonNull final EAxis eAxis)
  {
    if (eAxis == EAxis.ATTRIBUTE)
      return EStepKind.ATTRIBUTE;
    if (eAxis == EAxis.NAMESPACE)
      return EStepKind.NAMESPACE;
    return EStepKind.ELEMENT;
  }

  /** Only document and element nodes can have children. */
  private static boolean _hasChildrenKind (@NonNull final EStepKind eKind)
  {
    return eKind == EStepKind.DOCUMENT || eKind == EStepKind.ELEMENT;
  }

  private static boolean _isKindCompatible (@NonNull final EStepKind eA, @NonNull final EStepKind eB)
  {
    return eA == EStepKind.ANY || eB == EStepKind.ANY || eA == eB;
  }

  private static boolean _isNameCompatible (@Nullable final XdmQName aTestName, @Nullable final XdmQName aInName)
  {
    return aTestName == null || aInName == null || aTestName.equals (aInName);
  }

  /**
   * The kind and name a step's own axis and node test target, independent of
   * its input. Schema tests and anything without a safe answer are
   * {@link EStepKind#ANY}; element and attribute kind tests fix the kind, not
   * the name.
   */
  @NonNull
  private static Shape _testShapeOf (@NonNull final EAxis eAxis, @NonNull final NodeTest aTest)
  {
    return switch (aTest.kind ())
    {
      case NAME ->
      {
        final XdmQName aName = aTest.name ();
        final boolean bKnown = !aTest.anyName () &&
                               !aTest.anyNamespace () &&
                               !aTest.anyLocalName () &&
                               aName != null &&
                               !aName.hasPrefix () &&
                               !aName.localName ().isEmpty ();
        yield new Shape (_principalKind (eAxis), bKnown ? aName : null, false);
      }
      case TEXT -> new Shape (EStepKind.TEXT, null, false);
      case COMMENT -> new Shape (EStepKind.COMMENT, null, false);
      case PROCESSING_INSTRUCTION -> new Shape (EStepKind.PI, null, false);
      case ELEMENT -> new Shape (EStepKind.ELEMENT, null, false);
      case ATTRIBUTE -> new Shape (EStepKind.ATTRIBUTE, null, false);
      case DOCUMENT -> new Shape (EStepKind.DOCUMENT, null, false);
      case NAMESPACE_NODE -> new Shape (EStepKind.NAMESPACE, null, false);
      case ANY_NODE, SCHEMA_ELEMENT, SCHEMA_ATTRIBUTE -> Shape.UNKNOWN;
    };
  }

  /**
   * The shape one axis step produces from its input, or <code>null</code> if
   * the step is provably empty by structure alone. An imprecise answer
   * degrades to {@link EStepKind#ANY}, which is always safe.
   */
  @Nullable
  private static Shape _stepResult (@NonNull final EAxis eAxis, @NonNull final NodeTest aTest, @NonNull final Shape aIn)
  {
    final Shape aTestShape = _testShapeOf (eAxis, aTest);
    final EStepKind eTestKind = aTestShape.kind ();
    final XdmQName aTestName = aTestShape.name ();
    switch (eAxis)
    {
      case SELF:
      {
        if (aIn.kind () == EStepKind.ANY)
          return new Shape (eTestKind, aTestName, aIn.parentless ());
        if (!_isKindCompatible (eTestKind, aIn.kind ()))
          return null;
        if (!_isNameCompatible (aTestName, aIn.name ()))
          return null;
        return new Shape (aIn.kind (), aTestName != null ? aTestName : aIn.name (), aIn.parentless ());
      }
      case CHILD:
      case DESCENDANT:
        if (aIn.kind () != EStepKind.ANY && !_hasChildrenKind (aIn.kind ()))
          return null;
        return new Shape (eTestKind, aTestName, false);
      case DESCENDANT_OR_SELF:
      {
        final boolean bSelfOK = aIn.kind () == EStepKind.ANY ||
                                (_isKindCompatible (eTestKind, aIn.kind ()) &&
                                 _isNameCompatible (aTestName, aIn.name ()));
        final boolean bDescOK = aIn.kind () == EStepKind.ANY || _hasChildrenKind (aIn.kind ());
        if (!bSelfOK && !bDescOK)
          return null;
        if (bSelfOK && !bDescOK)
          return new Shape (aIn.kind (), aTestName != null ? aTestName : aIn.name (), aIn.parentless ());
        return new Shape (eTestKind, aTestName, false);
      }
      case PARENT:
        if (aIn.kind () == EStepKind.DOCUMENT || aIn.parentless ())
          return null;
        return new Shape (eTestKind, aTestName, false);
      case ATTRIBUTE:
        if (aIn.kind () != EStepKind.ANY && aIn.kind () != EStepKind.ELEMENT)
          return null;
        return new Shape (EStepKind.ATTRIBUTE, aTestName, false);
      default:
        // The other axes are deliberately not reasoned about
        return Shape.UNKNOWN;
    }
  }

  /**
   * @return <code>true</code> for a call of <code>fn:parse-xml-fragment</code>,
   *         whose result is always parentless whatever node kinds it contains.
   */
  private static boolean _isParentlessConstructor (@Nullable final IExpr aExpr)
  {
    return aExpr instanceof final IExpr.FunctionCallExpr aCall &&
           aCall.name ().isSameName (XdmConstants.NS_FN, "parse-xml-fragment");
  }

  /**
   * Check the own step chain of a path (not its nested predicates and postfix
   * expressions, which the caller visits separately).
   */
  private static void _checkPath (final IExpr.@NonNull PathExpr aPath)
  {
    Shape aCur = Shape.UNKNOWN;
    if (aPath.start () == null && aPath.absolute ())
    {
      // A rooted path starts at exactly the document node
      aCur = new Shape (EStepKind.DOCUMENT, null, false);
    }
    else
      if (_isParentlessConstructor (aPath.start ()))
        aCur = Shape.UNKNOWN_PARENTLESS;

    for (final StepExpr aStep : aPath.steps ())
    {
      if (aStep.postfix () != null)
      {
        // A postfix step is opaque, except a call of fn:parse-xml-fragment
        aCur = _isParentlessConstructor (aStep.postfix ()) ? Shape.UNKNOWN_PARENTLESS : Shape.UNKNOWN;
        continue;
      }
      final EAxis eAxis = aStep.axis ();
      final NodeTest aTest = aStep.test ();
      if (eAxis == null || aTest == null)
      {
        aCur = Shape.UNKNOWN;
        continue;
      }
      final Shape aOut = _stepResult (eAxis, aTest, aCur);
      if (aOut == null)
        throw XPathException.of ("XPST0005", "The " + eAxis.getName () + ":: axis can never select anything here");
      aCur = aOut;
    }
  }

  private static void _addBindings (@NonNull final ICommonsList <IExpr> aTarget,
                                    @NonNull final List <IExpr.Binding> aBindings)
  {
    for (final IExpr.Binding aBinding : aBindings)
      aTarget.add (aBinding.expr ());
  }

  /** The direct sub-expressions of an expression, in source order. */
  @NonNull
  private static ICommonsList <IExpr> _children (@NonNull final IExpr aExpr)
  {
    final ICommonsList <IExpr> ret = new CommonsArrayList <> ();
    switch (aExpr)
    {
      case final IExpr.LiteralExpr e ->
      {}
      case final IExpr.VarRefExpr e ->
      {}
      case final IExpr.ContextItemExpr e ->
      {}
      case final IExpr.PlaceholderExpr e ->
      {}
      case final IExpr.NamedFunctionRefExpr e ->
      {}
      case final IExpr.FunctionCallExpr e -> ret.addAll (e.args ());
      case final IExpr.InlineFunctionExpr e -> ret.add (e.body ());
      case final IExpr.DynamicCallExpr e ->
      {
        ret.add (e.base ());
        ret.addAll (e.args ());
      }
      case final IExpr.SequenceExpr e -> ret.addAll (e.items ());
      case final IExpr.BinaryExpr e ->
      {
        ret.add (e.left ());
        ret.add (e.right ());
      }
      case final IExpr.UnaryExpr e -> ret.add (e.operand ());
      case final IExpr.ComparisonExpr e ->
      {
        ret.add (e.left ());
        ret.add (e.right ());
      }
      case final IExpr.SetExpr e ->
      {
        ret.add (e.left ());
        ret.add (e.right ());
      }
      case final IExpr.RangeExpr e ->
      {
        ret.add (e.from ());
        ret.add (e.to ());
      }
      case final IExpr.StringConcatExpr e -> ret.addAll (e.parts ());
      case final IExpr.IfExpr e ->
      {
        ret.add (e.condition ());
        ret.add (e.thenExpr ());
        ret.add (e.elseExpr ());
      }
      case final IExpr.ForExpr e ->
      {
        _addBindings (ret, e.bindings ());
        ret.add (e.body ());
      }
      case final IExpr.LetExpr e ->
      {
        _addBindings (ret, e.bindings ());
        ret.add (e.body ());
      }
      case final IExpr.QuantifiedExpr e ->
      {
        _addBindings (ret, e.bindings ());
        ret.add (e.satisfies ());
      }
      case final IExpr.SimpleMapExpr e -> ret.addAll (e.steps ());
      case final IExpr.PathExpr e ->
      {
        if (e.start () != null)
          ret.add (e.start ());
        for (final StepExpr aStep : e.steps ())
        {
          ret.addAll (aStep.predicates ());
          if (aStep.postfix () != null)
            ret.add (aStep.postfix ());
        }
      }
      case final IExpr.FilterExpr e ->
      {
        ret.add (e.primary ());
        ret.addAll (e.predicates ());
      }
      case final IExpr.InstanceOfExpr e -> ret.add (e.operand ());
      case final IExpr.TreatExpr e -> ret.add (e.operand ());
      case final IExpr.CastExpr e -> ret.add (e.operand ());
      case final IExpr.MapConstructorExpr e ->
      {
        for (int i = 0; i < e.keys ().size (); ++i)
        {
          ret.add (e.keys ().get (i));
          if (i < e.values ().size ())
            ret.add (e.values ().get (i));
        }
      }
      case final IExpr.ArrayConstructorExpr e -> ret.addAll (e.items ());
      case final IExpr.LookupExpr e ->
      {
        if (e.base () != null)
          ret.add (e.base ());
        if (e.key () != null)
          ret.add (e.key ());
      }
    }
    return ret;
  }

  /**
   * Walk the whole expression tree (iteratively, in source order) and raise
   * XPST0005 for the first path whose own step chain provably selects nothing.
   *
   * @param aRoot
   *        The parsed expression.
   * @throws XPathException
   *         XPST0005 if a provably empty axis step is found.
   */
  static void checkStaticEmptyPaths (@NonNull final IExpr aRoot)
  {
    final Deque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IExpr aExpr = aStack.pop ();
      if (aExpr instanceof final IExpr.PathExpr aPath)
        _checkPath (aPath);
      final ICommonsList <IExpr> aChildren = _children (aExpr);
      for (int i = aChildren.size () - 1; i >= 0; --i)
        aStack.push (aChildren.get (i));
    }
  }
}
