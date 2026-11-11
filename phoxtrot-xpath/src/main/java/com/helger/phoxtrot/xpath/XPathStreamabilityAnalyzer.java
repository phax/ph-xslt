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
package com.helger.phoxtrot.xpath;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.EAxis;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.pattern.CompiledXsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPatternParser;
import com.helger.phoxtrot.xpath.stream.EPosture;
import com.helger.phoxtrot.xpath.stream.EStreamFunctionCategory;
import com.helger.phoxtrot.xpath.stream.PostureSweep;
import com.helger.phoxtrot.xpath.stream.StreamContext;
import com.helger.phoxtrot.xpath.stream.StreamFunctionSignature;
import com.helger.phoxtrot.xpath.stream.XPathStreamability;

/**
 * Tells whether an XPath expression or an XSLT match pattern can be evaluated while streaming, with the posture and
 * sweep analysis of XSLT 3.0 section 19 (the analysis the XSLT engine uses for its streamability rules). Meant for
 * hosts that decide upfront how to process a document, e.g. a Schematron validator: a rule context (pattern) that is
 * motionless can be matched on the current node, a test that is motionless needs only the context node, its
 * attributes and ancestors, a consuming one only the subtree of the context node (or the buffered subtree, see
 * {@link #setContextStreamed(boolean)}), and anything free-ranging needs more than one pass or the whole tree.
 * <ul>
 * <li>The context item is a streamed node by default (striding); with {@link #setContextStreamed(boolean)} set to
 * <code>false</code> it is a buffered one (grounded), which answers "streamable after buffering the context
 * subtree".</li>
 * <li>Variables are grounded (atomic values, or values computed in an earlier pass) unless declared as streamed nodes
 * with {@link #addStreamedVariable(XdmQName)}. A reference to a streamed variable is striding and motionless, and is
 * not streamable inside a construct that is evaluated repeatedly (a predicate, a loop body).</li>
 * <li>Built-in functions are classified as the specification says. Any other function (an extension function, a
 * stylesheet function) is not streamable unless declared with
 * {@link #addFunction(XdmQName, EStreamFunctionCategory, List)}.</li>
 * <li>The analysis fails closed: what it does not know is not streamable.</li>
 * </ul>
 * For a verdict other than motionless the result carries a reason: the innermost sub-expression that has the same
 * verdict when analysed on its own (for a path, the first step from which the path has that verdict; for a pattern,
 * the predicate that is not motionless or positional, or the start of a rooted path). Sub-expressions are analysed
 * with the context of the whole, so the reason is a hint and not part of the specified analysis; it has no position
 * in the expression text.
 * <p>
 * An instance is a reusable configuration; it is not thread-safe while it is changed.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XPathStreamabilityAnalyzer
{
  private boolean m_bContextStreamed = true;
  private final ICommonsOrderedSet <XdmQName> m_aStreamedVariables = new CommonsLinkedHashSet <> ();
  private final ICommonsMap <String, StreamFunctionSignature> m_aFunctions = new CommonsHashMap <> ();

  public XPathStreamabilityAnalyzer ()
  {}

  @NonNull
  private static String _getFunctionKey (@NonNull final XdmQName aName, final int nArity)
  {
    return aName.getEQName () + "#" + nArity;
  }

  /**
   * @return A node test as written in XPath, e.g. <code>x</code>, <code>*</code>, <code>text()</code>.
   */
  @NonNull
  private static String _getNodeTestText (@Nullable final NodeTest aTest)
  {
    if (aTest == null)
      return "node()";
    if (aTest.kind () == ENodeTestKind.NAME)
    {
      if (aTest.anyName () || aTest.name () == null)
        return "*";
      if (aTest.anyNamespace ())
        return "*:" + aTest.name ().localName ();
      if (aTest.anyLocalName ())
        return aTest.name ().hasPrefix () ? aTest.name ().prefix () + ":*" : "Q{" + aTest.name ().namespaceUri () + "}*";
      return aTest.name ().getLexicalName ();
    }
    return switch (aTest.kind ())
    {
      case ANY_NODE -> "node()";
      case DOCUMENT -> "document-node()";
      default -> aTest.kind ().name ().toLowerCase (Locale.ROOT).replace ('_', '-') + "()";
    };
  }

  @NonNull
  private static XPathStreamabilityReason _expression (@NonNull final EStreamabilityConstruct eConstruct,
                                                       @NonNull final String sName)
  {
    return new XPathStreamabilityReason (eConstruct, sName);
  }

  /**
   * @return The constant name of an operator enum in lower case with hyphens, e.g. <code>integer-divide</code>.
   */
  @NonNull
  private static String _getEnumText (@NonNull final Enum <?> aEnum)
  {
    return aEnum.name ().toLowerCase (Locale.ROOT).replace ('_', '-');
  }

  /**
   * @return The reason naming an expression that is not a path.
   */
  @NonNull
  private static XPathStreamabilityReason _describe (@NonNull final IExpr aExpr)
  {
    return switch (aExpr)
    {
      case final IExpr.FunctionCallExpr e -> _expression (EStreamabilityConstruct.FUNCTION,
                                                         e.name ().getLexicalName () + "#" + e.args ().size ());
      case final IExpr.NamedFunctionRefExpr e -> _expression (EStreamabilityConstruct.FUNCTION,
                                                             e.name ().getLexicalName () + "#" + e.arity ());
      case final IExpr.VarRefExpr e -> _expression (EStreamabilityConstruct.VARIABLE, "$" + e.name ().getLexicalName ());
      case final IExpr.ContextItemExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, ".");
      case final IExpr.ForExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "for");
      case final IExpr.LetExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "let");
      case final IExpr.QuantifiedExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, e.every () ? "every" : "some");
      case final IExpr.IfExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "if");
      case final IExpr.BinaryExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "operator " + _getEnumText (e.op ()));
      case final IExpr.ComparisonExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "comparison");
      case final IExpr.SetExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, _getEnumText (e.op ()));
      case final IExpr.SimpleMapExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "simple map");
      case final IExpr.FilterExpr e -> _expression (EStreamabilityConstruct.EXPRESSION, "filter");
      default ->
      {
        // The record name without the "Expr" suffix, e.g. "SequenceExpr" -> "sequence"
        final String sName = aExpr.getClass ().getSimpleName ().replace ("Expr", "");
        yield _expression (EStreamabilityConstruct.EXPRESSION,
                           sName.isEmpty () ? "expression" : sName.toLowerCase (Locale.ROOT));
      }
    };
  }

  /**
   * @return The step without its fused <code>descendant::</code> step, which belongs to the following step and must
   *         not be used when the path ends here.
   */
  private static IExpr.@NonNull StepExpr _withoutFused (final IExpr.@NonNull StepExpr aStep)
  {
    if (aStep.fused () == null)
      return aStep;
    return new IExpr.StepExpr (aStep.axis (), aStep.axisExplicit (), aStep.test (), aStep.predicates (), aStep.postfix ());
  }

  /**
   * @return The analysis context for one call. A new one each time, as the analysis records its definite flag in it.
   */
  @NonNull
  private StreamContext _createContext ()
  {
    final StreamContext ret = new StreamContext (m_bContextStreamed ? EPosture.STRIDING : EPosture.GROUNDED);
    if (m_aStreamedVariables.isNotEmpty ())
      ret.setStreamingParams (m_aStreamedVariables).setStreamingParamPosture (EPosture.STRIDING);
    if (m_aFunctions.isNotEmpty ())
    {
      final ICommonsMap <String, StreamFunctionSignature> aFunctions = m_aFunctions.getClone ();
      ret.setFunctionResolver ( (aName, nArity) -> aFunctions.get (_getFunctionKey (aName, nArity)));
    }
    return ret;
  }

  @NonNull
  private EStreamabilityVerdict _getVerdict (@NonNull final IExpr aExpr)
  {
    return EStreamabilityVerdict.getFromPostureSweep (XPathStreamability.getStreamability (aExpr, _createContext ()));
  }

  /**
   * @return The reason within a path that has the target verdict and no operand with it: the root, or the first step
   *         from which the path has the verdict.
   */
  @NonNull
  private XPathStreamabilityReason _findStepReason (final IExpr.@NonNull PathExpr aPath,
                                                    @NonNull final EStreamabilityVerdict eTarget)
  {
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    if (aPath.absolute () &&
        _getVerdict (new IExpr.PathExpr (null, true, new CommonsArrayList <> ())) == eTarget)
      return new XPathStreamabilityReason (EStreamabilityConstruct.ROOT, "/");
    for (int k = 1; k <= aSteps.size (); ++k)
    {
      final ICommonsList <IExpr.StepExpr> aPrefix = new CommonsArrayList <> (aSteps.subList (0, k - 1));
      final IExpr.StepExpr aStep = aSteps.get (k - 1);
      aPrefix.add (_withoutFused (aStep));
      if (_getVerdict (new IExpr.PathExpr (aPath.start (), aPath.absolute (), aPrefix)) == eTarget)
      {
        if (aStep.postfix () != null)
          return _describe (aStep.postfix ());
        // The step of a "//" (or the same step written out)
        if (aStep.axis () == EAxis.DESCENDANT_OR_SELF &&
            aStep.test () != null &&
            aStep.test ().kind () == ENodeTestKind.ANY_NODE &&
            aStep.predicates ().isEmpty ())
          return new XPathStreamabilityReason (EStreamabilityConstruct.AXIS_STEP, "//");
        return new XPathStreamabilityReason (EStreamabilityConstruct.AXIS_STEP,
                                             aStep.axis ().getName () + "::" + _getNodeTestText (aStep.test ()));
      }
    }
    return new XPathStreamabilityReason (EStreamabilityConstruct.EXPRESSION, "path");
  }

  /**
   * Descend from the expression to the innermost operand that has the target verdict on its own, iteratively.
   */
  @NonNull
  private XPathStreamabilityReason _findReason (@NonNull final IExpr aExpr, @NonNull final EStreamabilityVerdict eTarget)
  {
    IExpr aCur = aExpr;
    boolean bDescended = true;
    while (bDescended)
    {
      bDescended = false;
      for (final IExpr aChild : ExprWalker.getAllChildren (aCur))
        if (_getVerdict (aChild) == eTarget)
        {
          aCur = aChild;
          bDescended = true;
          break;
        }
    }
    if (aCur instanceof final IExpr.PathExpr aPath)
      return _findStepReason (aPath, eTarget);
    return _describe (aCur);
  }

  /**
   * @return The reason for a pattern that is not motionless: the first predicate that is positional or not
   *         motionless, else the start of a rooted path, else <code>null</code>.
   */
  @Nullable
  private XPathStreamabilityReason _findPatternReason (@NonNull final IExpr aPattern)
  {
    final ICommonsList <IExpr> aPredicates = new CommonsArrayList <> ();
    IExpr aRootedStart = null;
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aPattern);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.SetExpr e ->
        {
          aStack.push (e.right ());
          aStack.push (e.left ());
        }
        case final IExpr.PathExpr e ->
        {
          if (e.start () != null && aRootedStart == null)
            aRootedStart = e.start ();
          for (final IExpr.StepExpr aStep : e.steps ())
            aPredicates.addAll (aStep.predicates ());
        }
        case final IExpr.FilterExpr e -> aPredicates.addAll (e.predicates ());
        default ->
        {
          // Nothing to look at
        }
      }
    }
    for (final IExpr aPredicate : aPredicates)
    {
      if (!XPathStreamability.isNonPositionalPredicate (aPredicate))
        return new XPathStreamabilityReason (EStreamabilityConstruct.POSITIONAL_PREDICATE, "predicate");
      final EStreamabilityVerdict eVerdict = _getVerdict (aPredicate);
      if (eVerdict != EStreamabilityVerdict.MOTIONLESS)
        return _findReason (aPredicate, eVerdict);
    }
    return aRootedStart == null ? null : _describe (aRootedStart);
  }

  /**
   * @return <code>true</code> if the context item is a streamed node (the default).
   */
  public boolean isContextStreamed ()
  {
    return m_bContextStreamed;
  }

  /**
   * @param bContextStreamed
   *        <code>true</code> if the context item is a streamed node (striding, the default), <code>false</code> if it
   *        is a buffered node or a value (grounded).
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer setContextStreamed (final boolean bContextStreamed)
  {
    m_bContextStreamed = bContextStreamed;
    return this;
  }

  /**
   * Declare a variable as bound to streamed nodes. Every other variable is grounded.
   *
   * @param aName
   *        The variable name. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer addStreamedVariable (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aStreamedVariables.add (aName);
    return this;
  }

  /**
   * Declare how a function outside the <code>fn</code> namespace (an extension function or a function compiled from a
   * stylesheet) treats its arguments, as the <code>streamability</code> attribute of <code>xsl:function</code> does
   * (XSLT 3.0 section 19.8.5). A function that is not declared is not streamable. The declaration does not make the
   * function known to the static context.
   *
   * @param aName
   *        The function name. May not be <code>null</code>.
   * @param eCategory
   *        The streamability category; {@link EStreamFunctionCategory#UNCLASSIFIED} for a function that promises
   *        nothing (it is then streamable where its parameter types atomise streamed nodes). May not be
   *        <code>null</code>.
   * @param aParamTypes
   *        The declared types of the parameters in order (the arity is their number); a <code>null</code> entry stands
   *        for an absent type (<code>item()*</code>). May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XPathStreamabilityAnalyzer addFunction (@NonNull final XdmQName aName,
                                                 @NonNull final EStreamFunctionCategory eCategory,
                                                 @NonNull final List <SeqType> aParamTypes)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (eCategory, "Category");
    ValueEnforcer.notNull (aParamTypes, "ParamTypes");
    m_aFunctions.put (_getFunctionKey (aName, aParamTypes.size ()),
                      StreamFunctionSignature.createForParameterTypes (eCategory, aParamTypes));
    return this;
  }

  /**
   * Analyse a compiled expression.
   *
   * @param aXPath
   *        The expression. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  public XPathStreamabilityResult analyze (@NonNull final CompiledXPath aXPath)
  {
    ValueEnforcer.notNull (aXPath, "XPath");
    final IExpr aExpr = aXPath.getExpression ();
    final PostureSweep aPS = XPathStreamability.getStreamability (aExpr, _createContext ());
    final EStreamabilityVerdict eVerdict = EStreamabilityVerdict.getFromPostureSweep (aPS);
    return new XPathStreamabilityResult (aPS,
                                         eVerdict == EStreamabilityVerdict.MOTIONLESS ? null
                                                                                      : _findReason (aExpr, eVerdict));
  }

  /**
   * Compile and analyse an expression.
   *
   * @param sExpr
   *        The expression text. May not be <code>null</code>.
   * @param aStaticCtx
   *        The static context to compile it with. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   * @throws XPathException
   *         for static errors of the expression
   */
  @NonNull
  public XPathStreamabilityResult analyze (@NonNull final String sExpr, @NonNull final StaticContext aStaticCtx)
  {
    return analyze (XPathCompiler.compile (sExpr, aStaticCtx));
  }

  /**
   * Parse and analyse an XSLT match pattern (XSLT 3.0 section 19.8.10), e.g. the context of a Schematron rule. A
   * pattern is either motionless or free-ranging. With a streamed context its predicates are analysed with the
   * matched node as streamed context, otherwise as grounded.
   *
   * @param sPattern
   *        The pattern text. May not be <code>null</code>.
   * @param aStaticCtx
   *        The static context to parse it with. <code>fn:key</code> and <code>fn:current</code> must be registered in
   *        it if the pattern uses them. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   * @throws XPathException
   *         XTSE0340 for an invalid pattern, other static errors as the expression parser raises them
   */
  @NonNull
  public XPathStreamabilityResult analyzePattern (@NonNull final String sPattern, @NonNull final StaticContext aStaticCtx)
  {
    final XsltPattern aPattern = XsltPatternParser.parse (sPattern, aStaticCtx);
    final PostureSweep aPS = XPathStreamability.getPatternStreamability (aPattern, _createContext ());
    XPathStreamabilityReason aReason = null;
    if (EStreamabilityVerdict.getFromPostureSweep (aPS) != EStreamabilityVerdict.MOTIONLESS &&
        aPattern instanceof final CompiledXsltPattern aCompiled)
      aReason = _findPatternReason (aCompiled.getPatternExpr ());
    return new XPathStreamabilityResult (aPS, aReason);
  }
}
