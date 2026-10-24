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

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.parser.XPathParser;

/**
 * Parser for XSLT 3.0 patterns (a port of go-xslt's
 * <code>internal/xpath/pattern.go</code> and <code>pattern_calls.go</code>).
 * The grammar is that of XSLT 3.0 section 5.5.2 (Pattern30): predicate
 * patterns, unions, intersections and differences of path patterns, rooted
 * paths starting with a variable reference or a call of <code>doc</code>,
 * <code>id</code>, <code>element-with-id</code>, <code>key</code> or
 * <code>root</code>, and steps on the child, descendant, attribute, self,
 * descendant-or-self and namespace axes. Default priorities follow section
 * 6.5.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltPatternParser
{
  /** The error code of a pattern that does not match the grammar. */
  public static final String ERR_INVALID_PATTERN = "XTSE0340";

  @PresentForCodeCoverage
  private static final XsltPatternParser INSTANCE = new XsltPatternParser ();

  private XsltPatternParser ()
  {}

  /** @return The inner pattern of a parenthesised pattern without predicates, else <code>null</code>. */
  @Nullable
  private static IExpr _getParenthesisedContent (@NonNull final IExpr aExpr)
  {
    if (aExpr instanceof final IExpr.PathExpr aPath &&
        aPath.start () == null &&
        !aPath.absolute () &&
        aPath.steps ().size () == 1)
    {
      final IExpr.StepExpr aStep = aPath.steps ().get (0);
      if (aStep.postfix () != null && aStep.predicates ().isEmpty ())
        return aStep.postfix ();
    }
    return null;
  }

  /** Split the top-level unions, stripping outer parentheses (XSLT 3.0 section 6.5). */
  private static void _collectAlternatives (@NonNull final IExpr aExpr, @NonNull final List <IExpr> aOut)
  {
    final ICommonsList <IExpr> aStack = new CommonsArrayList <> (aExpr);
    while (aStack.isNotEmpty ())
    {
      final IExpr aCur = aStack.removeLast ();
      if (aCur instanceof final IExpr.SetExpr aSet && aSet.op () == IExpr.SetExpr.ESetOp.UNION)
      {
        // Right first, so that the left one is processed first
        aStack.add (aSet.right ());
        aStack.add (aSet.left ());
        continue;
      }
      final IExpr aInner = _getParenthesisedContent (aCur);
      if (aInner != null)
        aStack.add (aInner);
      else
        aOut.add (aCur);
    }
  }

  /** The default priority of a single node test (XSLT 3.0 section 6.5). */
  private static double _getNodeTestPriority (@NonNull final NodeTest aTest)
  {
    switch (aTest.kind ())
    {
      case NAME:
        if (aTest.anyName ())
          return -0.5;
        if (aTest.anyNamespace () || aTest.anyLocalName ())
          return -0.25;
        return 0;
      case PROCESSING_INSTRUCTION:
        return aTest.name () != null ? 0 : -0.5;
      case ELEMENT:
      case ATTRIBUTE:
      {
        final boolean bNamed = !aTest.anyName () && aTest.name () != null;
        final boolean bTyped = aTest.typeName () != null;
        if (bNamed && bTyped)
          return 0.25;
        if (bNamed || bTyped)
          return 0;
        return -0.5;
      }
      case SCHEMA_ELEMENT:
      case SCHEMA_ATTRIBUTE:
        return 0.25;
      case DOCUMENT:
      {
        final NodeTest aInner = aTest.inner ();
        if (aInner != null && (aInner.kind () == ENodeTestKind.ELEMENT || aInner.kind () == ENodeTestKind.SCHEMA_ELEMENT))
          return _getNodeTestPriority (aInner);
        return -0.5;
      }
      default:
        return -0.5;
    }
  }

  /**
   * The default priority of one alternative (XSLT 3.0 section 6.5).
   *
   * @param aAlternative
   *        A pattern without top-level union.
   * @return The priority.
   */
  static double getDefaultPriority (@NonNull final IExpr aAlternative)
  {
    if (aAlternative instanceof IExpr.ContextItemExpr)
      return -1;
    if (aAlternative instanceof final IExpr.FilterExpr aFilter && aFilter.primary () instanceof IExpr.ContextItemExpr)
      return 1;
    // intersect/except: the priority of the first operand
    IExpr aFirst = aAlternative;
    while (aFirst instanceof final IExpr.SetExpr aSet && aSet.op () != IExpr.SetExpr.ESetOp.UNION)
      aFirst = aSet.left ();
    if (!(aFirst instanceof final IExpr.PathExpr aPath))
      return 0.5;
    if (aPath.start () != null)
      return 0.5;
    if (aPath.absolute ())
      return aPath.steps ().isEmpty () ? -0.5 : 0.5;
    if (aPath.steps ().size () != 1)
      return 0.5;
    final IExpr.StepExpr aStep = aPath.steps ().get (0);
    if (aStep.postfix () != null || !aStep.predicates ().isEmpty ())
      return 0.5;
    return _getNodeTestPriority (aStep.test ());
  }

  /**
   * @param sPattern
   *        The pattern text.
   * @param aStaticCtx
   *        Static context (namespaces, default element namespace, variables,
   *        functions - <code>fn:key</code> and <code>fn:current</code> must be
   *        registered by the XSLT engine if the pattern uses them).
   * @return The compiled pattern.
   * @throws com.helger.xpath.XPathException
   *         XTSE0340 for an invalid pattern, other static errors as the
   *         expression parser raises them.
   */
  @NonNull
  public static XsltPattern parse (@NonNull final String sPattern, @NonNull final StaticContext aStaticCtx)
  {
    ValueEnforcer.notNull (sPattern, "Pattern");
    ValueEnforcer.notNull (aStaticCtx, "StaticCtx");
    final XPathParser.PatternParseResult aResult;
    try
    {
      aResult = XPathParser.parsePattern (sPattern, aStaticCtx);
    }
    catch (final XPathException ex)
    {
      if ("XPST0003".equals (ex.getCode ()))
        throw new XPathException (ERR_INVALID_PATTERN,
                                  "Invalid pattern '" + sPattern + "': " + ex.getDescription (),
                                  ex);
      throw ex;
    }
    final IExpr aExpr = aResult.pattern ();
    final Map <IExpr, String> aSources = aResult.sources ();

    final ICommonsList <IExpr> aAltExprs = new CommonsArrayList <> ();
    _collectAlternatives (aExpr, aAltExprs);
    final ICommonsList <CompiledXsltPattern> aAlternatives = new CommonsArrayList <> ();
    double dMax = Double.NEGATIVE_INFINITY;
    for (final IExpr aAlt : aAltExprs)
    {
      final double dPriority = getDefaultPriority (aAlt);
      dMax = Math.max (dMax, dPriority);
      if (aAltExprs.size () > 1)
      {
        final String sAltSource = aSources.get (aAlt);
        aAlternatives.add (new CompiledXsltPattern (sAltSource != null ? sAltSource : sPattern,
                                                    aAlt,
                                                    dPriority,
                                                    new CommonsArrayList <> ()));
      }
    }
    return new CompiledXsltPattern (sPattern, aExpr, dMax, aAlternatives);
  }
}
