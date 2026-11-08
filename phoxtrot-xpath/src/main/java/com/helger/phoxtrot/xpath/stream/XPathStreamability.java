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

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.numeric.mutable.MutableBoolean;
import com.helger.base.numeric.mutable.MutableInt;
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
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.pattern.CompiledXsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPattern;

/**
 * Guaranteed-streamability analysis for XPath expressions and XSLT match
 * patterns (XSLT 3.0 section 19 "Streamability" and appendix J), a port of
 * go-xslt's <code>internal/xpath/streamability.go</code>. It classifies an
 * expression by posture ({@link EPosture}) and sweep ({@link ESweep}); a
 * pattern is either motionless or free-ranging.
 * <p>
 * <b>Safety direction</b>: the analysis fails closed. Over-conservative is
 * merely incomplete; an unsound "streamable" is the one unacceptable outcome,
 * because the engine would then evaluate the construct in one forward pass and
 * silently produce wrong output. Every place where the analysis lacks the
 * static type information the specification assumes is resolved towards the
 * wider sweep, and every unknown function is roaming and free-ranging.
 * <p>
 * <b>Entry points</b> for the instruction-level analysis of the XSLT layer:
 * <ul>
 * <li>{@link #getStreamability(IExpr, StreamContext)} - posture and sweep of
 * an expression evaluated in the given context.</li>
 * <li>{@link #getPatternStreamability(IExpr, StreamContext)} - a match pattern
 * (section 19.8.10): grounded/motionless or roaming/free-ranging.</li>
 * <li>{@link #applyGeneralRules(List, StreamContext)} - the general rules of
 * section 19.8.1 over the already classified operands
 * ({@link StreamOperand}) of an instruction, shared with the expression walk
 * so that both obey one implementation.</li>
 * <li>{@link #getTypeDeterminedUsage(SeqType)} - the usage implied by a
 * required type (section 19.4).</li>
 * <li>{@link #isChildless(IExpr, StreamContext)},
 * {@link #isPatternChildless(IExpr)} - the static "cannot have children" test
 * that downgrades absorption to inspection.</li>
 * <li>{@link #usesFunction(IExpr, String)},
 * {@link #usesDeclaredStreamableFunction(IExpr, IStreamFunctionResolver)} -
 * call searches for the grouping-function boundary rule and for scoping
 * XTSE3430.</li>
 * </ul>
 * The hooks the XSLT layer supplies live on {@link StreamContext}: the context
 * posture and item type, the posture of <code>fn:current()</code>, the
 * streaming parameter of a stylesheet function, variables known to hold maps
 * or arrays, the accumulator rule phase, the population of the containing
 * <code>xsl:for-each-group</code>, the stylesheet function resolver
 * ({@link IStreamFunctionResolver}) and the "definite verdict" flag.
 * <p>
 * <b>Recursion</b>: the walk recurses over the expression tree like the
 * evaluator does, at most {@link #MAX_DEPTH} expressions deep per thread; a
 * deeper expression makes the public entry points fail closed (roaming and
 * free-ranging, never a definite verdict). As a last resort they do the same
 * if the walk still overflows the stack of the calling thread. The searches
 * over the tree are iterative.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathStreamability
{
  private static final PostureSweep GM = PostureSweep.GROUNDED_MOTIONLESS;
  private static final PostureSweep RF = PostureSweep.ROAMING_FREE_RANGING;

  /**
   * The maximum nesting of expressions the walk descends into, as the
   * stylesheet walk of the XSLT layer does. A deeper expression is roaming
   * and free-ranging.
   */
  public static final int MAX_DEPTH = 1000;

  /** The current nesting of the walk on this thread. */
  private static final ThreadLocal <MutableInt> DEPTH = ThreadLocal.withInitial ( () -> new MutableInt (0));

  /** Thrown when the walk would go deeper than {@link #MAX_DEPTH}. */
  private static final class DepthExceededException extends RuntimeException
  {
    DepthExceededException ()
    {
      super ("Expression nested more than " + MAX_DEPTH + " levels deep", null, false, false);
    }
  }

  private static final EStreamUsage A = EStreamUsage.ABSORPTION;
  private static final EStreamUsage I = EStreamUsage.INSPECTION;
  private static final EStreamUsage T = EStreamUsage.TRANSMISSION;
  private static final EStreamUsage N = EStreamUsage.NAVIGATION;

  /** A function whose short form defaults an argument to "." or to "/". */
  private record ContextDefault (int arity, boolean root)
  {}

  /** Every entry of section 19.8.9 whose usages are not all absorption. */
  private static final ICommonsMap <String, EStreamUsage []> FUNC_USAGES = new CommonsHashMap <> ();
  /** The entries of section 19.8.9 whose arguments are all absorption. */
  private static final ICommonsSet <String> ABSORBING_FUNCS = new CommonsHashSet <> ();
  /** Function name to the arity at which an argument defaults. */
  private static final ICommonsMap <String, ContextDefault> CONTEXT_DEFAULTS = new CommonsHashMap <> ();
  /** The built-ins that can return nodes. */
  private static final ICommonsSet <String> NODE_RETURNING_FUNCS = new CommonsHashSet <> ("fn:copy-of",
                                                                                          "fn:snapshot",
                                                                                          "fn:doc",
                                                                                          "fn:root",
                                                                                          "fn:id",
                                                                                          "fn:idref",
                                                                                          "fn:element-with-id",
                                                                                          "fn:key",
                                                                                          "fn:collection",
                                                                                          "fn:parse-xml",
                                                                                          "fn:parse-xml-fragment",
                                                                                          "fn:json-to-xml",
                                                                                          "fn:head",
                                                                                          "fn:tail",
                                                                                          "fn:subsequence",
                                                                                          "fn:remove",
                                                                                          "fn:insert-before",
                                                                                          "fn:reverse",
                                                                                          "fn:unordered",
                                                                                          "fn:outermost",
                                                                                          "fn:innermost",
                                                                                          "fn:for-each",
                                                                                          "fn:filter",
                                                                                          "fn:fold-left",
                                                                                          "fn:fold-right",
                                                                                          "fn:for-each-pair",
                                                                                          "fn:trace",
                                                                                          "fn:exactly-one",
                                                                                          "fn:zero-or-one",
                                                                                          "fn:one-or-more",
                                                                                          "fn:current-group",
                                                                                          "fn:current-merge-group",
                                                                                          "fn:function-lookup",
                                                                                          "fn:accumulator-before",
                                                                                          "fn:accumulator-after");
  /** The built-ins whose result is statically numeric. */
  private static final ICommonsSet <String> NUMERIC_FUNCS = new CommonsHashSet <> ("fn:position",
                                                                                   "fn:last",
                                                                                   "fn:count",
                                                                                   "fn:number",
                                                                                   "fn:sum",
                                                                                   "fn:avg",
                                                                                   "fn:min",
                                                                                   "fn:max",
                                                                                   "fn:abs",
                                                                                   "fn:floor",
                                                                                   "fn:ceiling",
                                                                                   "fn:round",
                                                                                   "fn:round-half-to-even",
                                                                                   "fn:string-length",
                                                                                   "fn:index-of",
                                                                                   "fn:function-arity");
  /** The built-ins that read the focus whatever their arity. */
  private static final ICommonsSet <String> FOCUS_FUNCS = new CommonsHashSet <> ("fn:position",
                                                                                 "fn:last",
                                                                                 "fn:current",
                                                                                 "fn:current-group",
                                                                                 "fn:current-grouping-key",
                                                                                 "fn:current-merge-group",
                                                                                 "fn:current-merge-key",
                                                                                 "fn:regex-group",
                                                                                 "fn:function-lookup",
                                                                                 "fn:static-base-uri",
                                                                                 "fn:default-collation",
                                                                                 "fn:accumulator-before",
                                                                                 "fn:accumulator-after");

  static
  {
    // Initialise the enums used deep inside the walk here: a StackOverflowError
    // inside a static initialiser would poison the class for good.
    EPosture.values ();
    ESweep.values ();
    EStreamUsage.values ();
    EStreamFunctionCategory.values ();
    EAccumulatorRulePhase.values ();
    EAxis.values ();
    ENodeTestKind.values ();
    EBinaryOp.values ();
    IExpr.SetExpr.ESetOp.values ();
    if (GM == null || RF == null)
      throw new IllegalStateException ("Class initialisation failed");

    FUNC_USAGES.put ("fn:base-uri", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:boolean", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:count", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:document", new EStreamUsage [] { A, I });
    FUNC_USAGES.put ("fn:document-uri", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:element-with-id", new EStreamUsage [] { A, N });
    FUNC_USAGES.put ("fn:empty", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:error", new EStreamUsage [] { A, A, N });
    FUNC_USAGES.put ("fn:exactly-one", new EStreamUsage [] { T });
    FUNC_USAGES.put ("fn:exists", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:filter", new EStreamUsage [] { N, I });
    FUNC_USAGES.put ("fn:fold-left", new EStreamUsage [] { N, A, I });
    FUNC_USAGES.put ("fn:fold-right", new EStreamUsage [] { N, A, I });
    FUNC_USAGES.put ("fn:for-each", new EStreamUsage [] { N, I });
    FUNC_USAGES.put ("fn:for-each-pair", new EStreamUsage [] { N, N, I });
    FUNC_USAGES.put ("fn:function-lookup", new EStreamUsage [] { A, A });
    FUNC_USAGES.put ("fn:generate-id", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:has-children", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:head", new EStreamUsage [] { T });
    FUNC_USAGES.put ("fn:id", new EStreamUsage [] { A, N });
    FUNC_USAGES.put ("fn:idref", new EStreamUsage [] { A, N });
    FUNC_USAGES.put ("fn:in-scope-prefixes", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:innermost", new EStreamUsage [] { N });
    FUNC_USAGES.put ("fn:insert-before", new EStreamUsage [] { T, A, T });
    FUNC_USAGES.put ("fn:key", new EStreamUsage [] { A, A, N });
    FUNC_USAGES.put ("fn:lang", new EStreamUsage [] { A, I });
    FUNC_USAGES.put ("fn:local-name", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:name", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:namespace-uri", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:namespace-uri-for-prefix", new EStreamUsage [] { A, I });
    FUNC_USAGES.put ("fn:nilled", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:node-name", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:not", new EStreamUsage [] { I });
    FUNC_USAGES.put ("fn:one-or-more", new EStreamUsage [] { T });
    FUNC_USAGES.put ("fn:path", new EStreamUsage [] { N });
    FUNC_USAGES.put ("fn:remove", new EStreamUsage [] { T, A });
    FUNC_USAGES.put ("fn:resolve-QName", new EStreamUsage [] { A, I });
    FUNC_USAGES.put ("fn:reverse", new EStreamUsage [] { N });
    FUNC_USAGES.put ("fn:subsequence", new EStreamUsage [] { T, A, A });
    FUNC_USAGES.put ("fn:tail", new EStreamUsage [] { T });
    FUNC_USAGES.put ("fn:trace", new EStreamUsage [] { T, A });
    FUNC_USAGES.put ("fn:unordered", new EStreamUsage [] { T });
    FUNC_USAGES.put ("fn:unparsed-entity-public-id", new EStreamUsage [] { A, I });
    FUNC_USAGES.put ("fn:unparsed-entity-uri", new EStreamUsage [] { A, I });
    FUNC_USAGES.put ("fn:zero-or-one", new EStreamUsage [] { T });
    // The map and array functions, exactly as the proformas of section 19.8.9
    // give them: a map or array operand is function(*)-typed, so its usage is
    // inspection (type-determined usage of section 19.4), never absorption. An
    // arity-dependent entry lists the longest form.
    FUNC_USAGES.put ("map:contains", new EStreamUsage [] { I, A });
    FUNC_USAGES.put ("map:entry", new EStreamUsage [] { A, N });
    FUNC_USAGES.put ("map:find", new EStreamUsage [] { I, A });
    FUNC_USAGES.put ("map:for-each", new EStreamUsage [] { I, I });
    FUNC_USAGES.put ("map:get", new EStreamUsage [] { I, A });
    FUNC_USAGES.put ("map:keys", new EStreamUsage [] { I });
    FUNC_USAGES.put ("map:merge", new EStreamUsage [] { I, I });
    FUNC_USAGES.put ("map:put", new EStreamUsage [] { I, A, N });
    FUNC_USAGES.put ("map:remove", new EStreamUsage [] { I, A });
    FUNC_USAGES.put ("map:size", new EStreamUsage [] { I });
    FUNC_USAGES.put ("array:append", new EStreamUsage [] { I, N });
    FUNC_USAGES.put ("array:filter", new EStreamUsage [] { I, I });
    FUNC_USAGES.put ("array:flatten", new EStreamUsage [] { A });
    FUNC_USAGES.put ("array:fold-left", new EStreamUsage [] { I, N, I });
    FUNC_USAGES.put ("array:fold-right", new EStreamUsage [] { I, N, I });
    FUNC_USAGES.put ("array:for-each", new EStreamUsage [] { I, I });
    FUNC_USAGES.put ("array:for-each-pair", new EStreamUsage [] { I, I, I });
    FUNC_USAGES.put ("array:get", new EStreamUsage [] { I, A });
    FUNC_USAGES.put ("array:head", new EStreamUsage [] { I });
    FUNC_USAGES.put ("array:insert-before", new EStreamUsage [] { I, A, N });
    FUNC_USAGES.put ("array:join", new EStreamUsage [] { I });
    FUNC_USAGES.put ("array:put", new EStreamUsage [] { I, I, N });
    FUNC_USAGES.put ("array:remove", new EStreamUsage [] { I, A });
    FUNC_USAGES.put ("array:reverse", new EStreamUsage [] { I });
    FUNC_USAGES.put ("array:size", new EStreamUsage [] { I });
    FUNC_USAGES.put ("array:sort", new EStreamUsage [] { I, A, I });
    FUNC_USAGES.put ("array:subarray", new EStreamUsage [] { I, A, A });
    FUNC_USAGES.put ("array:tail", new EStreamUsage [] { I });

    for (final String s : new String [] { "QName",
                                          "abs",
                                          "adjust-date-to-timezone",
                                          "adjust-dateTime-to-timezone",
                                          "adjust-time-to-timezone",
                                          "analyze-string",
                                          "available-environment-variables",
                                          "avg",
                                          "ceiling",
                                          "codepoint-equal",
                                          "codepoints-to-string",
                                          "collation-key",
                                          "collection",
                                          "compare",
                                          "concat",
                                          "contains",
                                          "copy-of",
                                          "current-date",
                                          "current-dateTime",
                                          "current-output-uri",
                                          "current-time",
                                          "data",
                                          "dateTime",
                                          "day-from-date",
                                          "day-from-dateTime",
                                          "days-from-duration",
                                          "deep-equal",
                                          "default-collation",
                                          "distinct-values",
                                          "doc",
                                          "doc-available",
                                          "element-available",
                                          "encode-for-uri",
                                          "ends-with",
                                          "environment-variable",
                                          "escape-html-uri",
                                          "false",
                                          "floor",
                                          "format-date",
                                          "format-dateTime",
                                          "format-integer",
                                          "format-number",
                                          "format-time",
                                          "function-arity",
                                          "function-available",
                                          "function-name",
                                          "hours-from-dateTime",
                                          "hours-from-duration",
                                          "hours-from-time",
                                          "implicit-timezone",
                                          "index-of",
                                          "iri-to-uri",
                                          "json-to-xml",
                                          "json-doc",
                                          "parse-json",
                                          "local-name-from-QName",
                                          "lower-case",
                                          "matches",
                                          "max",
                                          "min",
                                          "minutes-from-dateTime",
                                          "minutes-from-duration",
                                          "minutes-from-time",
                                          "month-from-date",
                                          "month-from-dateTime",
                                          "months-from-duration",
                                          "namespace-uri-from-QName",
                                          "normalize-space",
                                          "normalize-unicode",
                                          "number",
                                          "parse-xml",
                                          "parse-xml-fragment",
                                          "prefix-from-QName",
                                          // fn:random-number-generator($seed as xs:anyAtomicType?) takes an
                                          // atomic seed, so its one argument is absorption (section 19.4)
                                          "random-number-generator",
                                          "regex-group",
                                          "replace",
                                          "resolve-uri",
                                          "round",
                                          "round-half-to-even",
                                          "seconds-from-dateTime",
                                          "seconds-from-duration",
                                          "seconds-from-time",
                                          "serialize",
                                          "snapshot",
                                          "starts-with",
                                          "static-base-uri",
                                          "stream-available",
                                          "string",
                                          "string-join",
                                          "string-length",
                                          "string-to-codepoints",
                                          "substring",
                                          "substring-after",
                                          "substring-before",
                                          "sum",
                                          "system-property",
                                          "timezone-from-date",
                                          "timezone-from-dateTime",
                                          "timezone-from-time",
                                          "tokenize",
                                          "translate",
                                          "true",
                                          "type-available",
                                          "unparsed-text",
                                          "unparsed-text-available",
                                          "unparsed-text-lines",
                                          "upper-case",
                                          "uri-collection",
                                          "xml-to-json",
                                          "year-from-date",
                                          "year-from-dateTime",
                                          "years-from-duration" })
      ABSORBING_FUNCS.add ("fn:" + s);
    for (final String s : new String [] { "acos",
                                          "asin",
                                          "atan",
                                          "atan2",
                                          "cos",
                                          "exp",
                                          "exp10",
                                          "log",
                                          "log10",
                                          "pi",
                                          "pow",
                                          "sin",
                                          "sqrt",
                                          "tan" })
      ABSORBING_FUNCS.add ("math:" + s);

    for (final String s : new String [] { "base-uri",
                                          "copy-of",
                                          "data",
                                          "document-uri",
                                          "generate-id",
                                          "has-children",
                                          "local-name",
                                          "name",
                                          "namespace-uri",
                                          "nilled",
                                          "node-name",
                                          "number",
                                          "path",
                                          "snapshot",
                                          "string",
                                          "string-length" })
      CONTEXT_DEFAULTS.put ("fn:" + s, new ContextDefault (0, false));
    CONTEXT_DEFAULTS.put ("fn:element-with-id", new ContextDefault (1, false));
    CONTEXT_DEFAULTS.put ("fn:id", new ContextDefault (1, false));
    CONTEXT_DEFAULTS.put ("fn:idref", new ContextDefault (1, false));
    CONTEXT_DEFAULTS.put ("fn:lang", new ContextDefault (1, false));
    CONTEXT_DEFAULTS.put ("fn:key", new ContextDefault (2, true));
    CONTEXT_DEFAULTS.put ("fn:unparsed-entity-public-id", new ContextDefault (1, true));
    CONTEXT_DEFAULTS.put ("fn:unparsed-entity-uri", new ContextDefault (1, true));
  }

  @PresentForCodeCoverage
  private static final XPathStreamability INSTANCE = new XPathStreamability ();

  private XPathStreamability ()
  {}

  /**
   * @return The name of a built-in function in the form the tables use
   *         (<code>fn:name</code>, <code>map:get</code>, <code>math:pi</code>,
   *         <code>xs:integer</code>), or <code>null</code> for any other
   *         namespace.
   */
  @Nullable
  private static String _getBuiltinName (@NonNull final XdmQName aName)
  {
    final String sNs = aName.namespaceUri ();
    final String sPrefix;
    if (XdmConstants.NS_FN.equals (sNs))
      sPrefix = "fn:";
    else
      if (FunctionLibrary.NS_MAP.equals (sNs))
        sPrefix = "map:";
      else
        if (FunctionLibrary.NS_ARRAY.equals (sNs))
          sPrefix = "array:";
        else
          if (FunctionLibrary.NS_MATH.equals (sNs))
            sPrefix = "math:";
          else
            if (XdmConstants.NS_XS.equals (sNs))
              sPrefix = "xs:";
            else
              return null;
    return sPrefix + aName.localName ();
  }

  /**
   * @return <code>true</code> if a call of this name and arity reads the context
   *         item implicitly (sections 19.8.8.14 and 19.8.10).
   */
  private static boolean _isFocusDependent (@NonNull final XdmQName aName, final int nArity)
  {
    final String sName = _getBuiltinName (aName);
    if (sName == null)
      return false;
    if (FOCUS_FUNCS.contains (sName))
      return true;
    final ContextDefault aDefault = CONTEXT_DEFAULTS.get (sName);
    return aDefault != null && aDefault.arity () == nArity;
  }

  /**
   * @return The per-argument operand usages of a built-in function, or
   *         <code>null</code> when the function is outside the catalogue of
   *         section 19.8.9.
   */
  private static EStreamUsage @Nullable [] _getFunctionUsages (@NonNull final String sName, final int nArity)
  {
    final EStreamUsage [] aSig = FUNC_USAGES.get (sName);
    if (aSig != null)
    {
      final EStreamUsage [] ret = new EStreamUsage [nArity];
      for (int i = 0; i < nArity; ++i)
      {
        // Variadic tails repeat the last declared usage
        ret[i] = i < aSig.length ? aSig[i] : aSig[aSig.length - 1];
      }
      return ret;
    }
    if (ABSORBING_FUNCS.contains (sName))
    {
      final EStreamUsage [] ret = new EStreamUsage [nArity];
      for (int i = 0; i < nArity; ++i)
        ret[i] = A;
      return ret;
    }
    if (sName.startsWith ("xs:"))
    {
      // A constructor function: section 19.8.8.13 gives its single argument
      // absorption
      return nArity == 1 ? new EStreamUsage [] { A } : null;
    }
    return null;
  }

  /**
   * @return <code>true</code> for the argument placeholder <code>?</code> of a
   *         partial function application.
   */
  private static boolean _isPlaceholder (@NonNull final IExpr aExpr)
  {
    return aExpr instanceof IExpr.PlaceholderExpr;
  }

  /**
   * @return <code>true</code> if any argument is a placeholder, i.e. the call
   *         is a partial function application that returns a function item.
   */
  private static boolean _hasPlaceholder (@NonNull final List <IExpr> aArgs)
  {
    for (final IExpr aArg : aArgs)
      if (_isPlaceholder (aArg))
        return true;
    return false;
  }

  /** @return <code>true</code> if an axis step can only select childless nodes. */
  private static boolean _isStepChildless (@NonNull final EAxis eAxis, @NonNull final NodeTest aTest)
  {
    if (eAxis == EAxis.ATTRIBUTE || eAxis == EAxis.NAMESPACE)
      return true;
    return switch (aTest.kind ())
    {
      case TEXT, COMMENT, PROCESSING_INSTRUCTION, ATTRIBUTE, NAMESPACE_NODE -> true;
      default -> false;
    };
  }

  /**
   * @return <code>true</code> if the U-type of a step can include
   *         <code>element()</code>, which decides crawling vs. striding on the
   *         descendant axes.
   */
  private static boolean _isStepSelectingElements (@NonNull final EAxis eAxis, @NonNull final NodeTest aTest)
  {
    if (eAxis == EAxis.ATTRIBUTE || eAxis == EAxis.NAMESPACE)
      return false;
    return switch (aTest.kind ())
    {
      case TEXT, COMMENT, PROCESSING_INSTRUCTION, ATTRIBUTE, NAMESPACE_NODE, SCHEMA_ATTRIBUTE, DOCUMENT -> false;
      default -> true;
    };
  }

  /** @return <code>true</code> for a step whose static type can contain U{element}. */
  private static boolean _isStepSelectingElements (final IExpr.@NonNull StepExpr aStep)
  {
    if (aStep.postfix () != null)
      return true;
    return _isStepSelectingElements (aStep.axis (), aStep.test ());
  }

  /**
   * @return <code>true</code> for a SequenceType of the form
   *         <code>document-node(element(...))</code>, the one item type section
   *         19.8.8.5 treats as absorption.
   */
  private static boolean _isDocElementTest (@NonNull final SeqType aType)
  {
    final ItemType aItemType = aType.itemType ();
    if (aType.empty () || aItemType == null || aItemType.kind () != ItemType.Kind.NODE)
      return false;
    final NodeTest aTest = aItemType.nodeTest ();
    return aTest != null && aTest.kind () == ENodeTestKind.DOCUMENT && aTest.inner () != null;
  }

  /**
   * Push the direct operand expressions of a node onto the work stack of
   * {@link #_containsMatch(IExpr, Predicate, boolean)}: the arguments of a
   * call, the body of an inline function, the binding expressions and the
   * body of <code>for</code>/<code>let</code>/quantified expressions, the
   * start, predicates and postfix expressions of the steps of a path (the
   * steps themselves carry no expression otherwise), and so on. The order in
   * which they are pushed is not significant for the search.
   *
   * @param bSkipPredicates
   *        <code>true</code> to leave out the predicates of path steps and
   *        filter expressions.
   */
  private static void _pushChildren (@NonNull final IExpr aExpr,
                                     @NonNull final ArrayDeque <IExpr> aStack,
                                     final boolean bSkipPredicates)
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
      case final IExpr.FunctionCallExpr e -> aStack.addAll (e.args ());
      case final IExpr.InlineFunctionExpr e -> aStack.push (e.body ());
      case final IExpr.DynamicCallExpr e ->
      {
        aStack.push (e.base ());
        aStack.addAll (e.args ());
      }
      case final IExpr.SequenceExpr e -> aStack.addAll (e.items ());
      case final IExpr.BinaryExpr e ->
      {
        aStack.push (e.left ());
        aStack.push (e.right ());
      }
      case final IExpr.UnaryExpr e -> aStack.push (e.operand ());
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
      case final IExpr.StringConcatExpr e -> aStack.addAll (e.parts ());
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
      case final IExpr.SimpleMapExpr e -> aStack.addAll (e.steps ());
      case final IExpr.PathExpr e ->
      {
        if (e.start () != null)
          aStack.push (e.start ());
        for (final IExpr.StepExpr aStep : e.steps ())
        {
          if (!bSkipPredicates)
            aStack.addAll (aStep.predicates ());
          if (aStep.postfix () != null)
            aStack.push (aStep.postfix ());
        }
      }
      case final IExpr.FilterExpr e ->
      {
        aStack.push (e.primary ());
        if (!bSkipPredicates)
          aStack.addAll (e.predicates ());
      }
      case final IExpr.InstanceOfExpr e -> aStack.push (e.operand ());
      case final IExpr.TreatExpr e -> aStack.push (e.operand ());
      case final IExpr.CastExpr e -> aStack.push (e.operand ());
      case final IExpr.MapConstructorExpr e ->
      {
        aStack.addAll (e.keys ());
        aStack.addAll (e.values ());
      }
      case final IExpr.ArrayConstructorExpr e -> aStack.addAll (e.items ());
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
   * Iterative search over an expression tree.
   *
   * @param aExpr
   *        The tree.
   * @param aTest
   *        The test for each node.
   * @param bSkipPredicates
   *        <code>true</code> to not descend into the predicates of a step or a
   *        filter expression (the "unless that call or reference occurs within
   *        a nested predicate" of section 19.8.10).
   * @return <code>true</code> if any node passes the test.
   */
  private static boolean _containsMatch (@NonNull final IExpr aExpr,
                                         @NonNull final Predicate <IExpr> aTest,
                                         final boolean bSkipPredicates)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      if (aTest.test (aCur))
        return true;
      _pushChildren (aCur, aStack, bSkipPredicates);
    }
    return false;
  }

  /**
   * @return <code>true</code> if the expression can be proven to yield only
   *         items without children (attributes, namespaces, text, comment and
   *         processing instruction nodes, or atomic values). Absorption over such
   *         an operand is downgraded to inspection (section 19.8.1), which keeps
   *         <code>x[@code = 'a']</code> motionless. Unknown answers
   *         <code>false</code>, the conservative side. Iterative: every
   *         composite case is a conjunction over its parts.
   */
  private static boolean _isChildless (@NonNull final IExpr aExpr, @Nullable final StreamContext aEnv)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      final boolean bLeafOK = switch (aCur)
      {
        case final IExpr.ContextItemExpr e -> aEnv != null && aEnv.isContextChildless ();
        case final IExpr.LiteralExpr e -> true;
        // The flag only decides whether absorbing a streamed operand has to read
        // the input, and a variable not bound to a streaming parameter is
        // grounded - nothing it holds can move the stream
        case final IExpr.VarRefExpr e -> aEnv == null || !aEnv.isStreamingParam (e.name ());
        case final IExpr.PathExpr e ->
        {
          if (e.steps ().isEmpty ())
            yield false;
          final IExpr.StepExpr aLast = e.steps ().get (e.steps ().size () - 1);
          yield aLast.postfix () == null && _isStepChildless (aLast.axis (), aLast.test ());
        }
        case final IExpr.FilterExpr e ->
        {
          aStack.push (e.primary ());
          yield true;
        }
        case final IExpr.SimpleMapExpr e ->
        {
          // The value of A!B is the value of the last step
          if (e.steps ().isEmpty ())
            yield false;
          aStack.push (e.steps ().get (e.steps ().size () - 1));
          yield true;
        }
        case final IExpr.SetExpr e ->
        {
          // intersect and except yield a subset of the left operand
          aStack.push (e.left ());
          if (e.op () == IExpr.SetExpr.ESetOp.UNION)
            aStack.push (e.right ());
          yield true;
        }
        case final IExpr.IfExpr e ->
        {
          aStack.push (e.thenExpr ());
          aStack.push (e.elseExpr ());
          yield true;
        }
        case final IExpr.SequenceExpr e ->
        {
          if (e.items ().isEmpty ())
            yield false;
          aStack.addAll (e.items ());
          yield true;
        }
        case final IExpr.BinaryExpr e -> true;
        case final IExpr.UnaryExpr e -> true;
        case final IExpr.ComparisonExpr e -> true;
        case final IExpr.StringConcatExpr e -> true;
        case final IExpr.RangeExpr e -> true;
        case final IExpr.CastExpr e -> true;
        case final IExpr.InstanceOfExpr e -> true;
        case final IExpr.MapConstructorExpr e -> true;
        case final IExpr.ArrayConstructorExpr e -> true;
        case final IExpr.InlineFunctionExpr e -> true;
        case final IExpr.NamedFunctionRefExpr e -> true;
        case final IExpr.FunctionCallExpr e ->
        {
          final String sName = _getBuiltinName (e.name ());
          // go-xslt answers true for every name outside its list of node-returning
          // built-ins, which includes stylesheet and extension functions. Their
          // result type is not known here and may well be an element (a filter
          // or shallow-descent stylesheet function returns streamed nodes), so
          // claiming childlessness would downgrade string(f:filter(.)) to a
          // motionless inspection. Section 19.8.1 only permits the downgrade on
          // a static type, so those calls are not childless.
          if (sName == null)
            yield false;
          if (NODE_RETURNING_FUNCS.contains (sName))
            yield false;
          if (sName.equals ("fn:current"))
            yield aEnv != null && aEnv.isCurrentChildless ();
          // Everything else in the catalogue is atomic
          yield true;
        }
        default -> false;
      };
      if (!bLeafOK)
        return false;
    }
    return true;
  }

  /**
   * @return <code>true</code> if the static type of the expression is numeric
   *         (the test shared by sections 19.8.8.8 and 19.8.8.9).
   */
  private static boolean _isStaticNumeric (@NonNull final IExpr aExpr)
  {
    IExpr aCur = aExpr;
    while (aCur instanceof final IExpr.FilterExpr aFilter)
      aCur = aFilter.primary ();
    return switch (aCur)
    {
      case final IExpr.LiteralExpr e -> e.value ().isNumeric ();
      case final IExpr.BinaryExpr e -> e.op () != EBinaryOp.AND && e.op () != EBinaryOp.OR;
      case final IExpr.UnaryExpr e -> true;
      case final IExpr.RangeExpr e -> true;
      case final IExpr.FunctionCallExpr e ->
      {
        final String sName = _getBuiltinName (e.name ());
        yield sName != null && NUMERIC_FUNCS.contains (sName);
      }
      default -> false;
    };
  }

  /**
   * @return {@link #_isStaticNumeric(IExpr)} widened to the cases section
   *         19.8.10 calls "potentially numeric": <code>p[data(@status)]</code>
   *         is a positional predicate because <code>data()</code> may yield a
   *         number, and the static type of a variable is unknown
   *         (<code>p[$pnum + 1]</code>).
   */
  private static boolean _isStaticNumericLoose (@NonNull final IExpr aExpr)
  {
    if (_isStaticNumeric (aExpr))
      return true;
    if (aExpr instanceof final IExpr.FunctionCallExpr aCall)
    {
      final String sName = _getBuiltinName (aCall.name ());
      if ("fn:data".equals (sName) || "fn:string-to-codepoints".equals (sName))
        return true;
    }
    return aExpr instanceof IExpr.VarRefExpr;
  }

  /**
   * @return <code>true</code> if neither the expression nor any sub-expression
   *         reads the focus.
   */
  private static boolean _isFocusFree (@NonNull final IExpr aExpr)
  {
    return !_containsMatch (aExpr, x -> switch (x)
    {
      case final IExpr.ContextItemExpr e -> true;
      // A relative path is rooted at the context item; an absolute one still
      // needs the focus to find its root
      case final IExpr.PathExpr e -> true;
      case final IExpr.FunctionCallExpr e -> _isFocusDependent (e.name (), e.args ().size ());
      case final IExpr.NamedFunctionRefExpr e -> _isFocusDependent (e.name (), e.arity ());
      default -> false;
    }, false);
  }

  /**
   * The "static type is numeric and the predicate does not depend on the focus"
   * test of sections 19.8.8.8 and 19.8.8.9: a predicate such as
   * <code>[1]</code> or <code>[$i+1]</code> selects at most one node.
   */
  private static boolean _isNumericFocusFree (@NonNull final IExpr aPredicate)
  {
    return _isStaticNumeric (aPredicate) && _isFocusFree (aPredicate);
  }

  /**
   * @return <code>true</code> for <code>fn:position</code>,
   *         <code>fn:last</code> and <code>fn:function-lookup</code> (which
   *         could return either), the calls that make a predicate positional in
   *         the sense of section 19.8.10.
   */
  private static boolean _isPositionalCall (@NonNull final XdmQName aName)
  {
    final String sName = _getBuiltinName (aName);
    return "fn:position".equals (sName) || "fn:last".equals (sName) || "fn:function-lookup".equals (sName);
  }

  /**
   * The non-positional predicate test of section 19.8.10: no call to
   * <code>position</code>, <code>last</code> or <code>function-lookup</code>
   * outside a nested predicate, and a static type that cannot be numeric.
   */
  private static boolean _isNonPositional (@NonNull final IExpr aPredicate)
  {
    if (_isStaticNumericLoose (aPredicate))
      return false;
    return !_containsMatch (aPredicate, x -> switch (x)
    {
      case final IExpr.FunctionCallExpr e -> _isPositionalCall (e.name ());
      case final IExpr.NamedFunctionRefExpr e -> _isPositionalCall (e.name ());
      default -> false;
    }, true);
  }

  /** @return <code>true</code> if the expression textually references a streaming parameter. */
  private static boolean _mentionsStreamingParam (@NonNull final IExpr aExpr, @NonNull final StreamContext aEnv)
  {
    if (!aEnv.hasStreamingParams ())
      return false;
    return _containsMatch (aExpr,
                           x -> x instanceof final IExpr.VarRefExpr aRef && aEnv.isStreamingParam (aRef.name ()),
                           false);
  }

  /**
   * @return <code>true</code> if the base of a dynamic function call is
   *         statically known to be a map or an array, the condition the Note of
   *         section 19.8.8.11 attaches its absorption rule to: a map or array
   *         constructor written out, or a variable whose declared type the XSLT
   *         layer resolved to a map or array type.
   */
  private static boolean _isBaseMapOrArray (@NonNull final IExpr aBase, @NonNull final StreamContext aEnv)
  {
    return switch (aBase)
    {
      case final IExpr.MapConstructorExpr e -> true;
      case final IExpr.ArrayConstructorExpr e -> true;
      case final IExpr.VarRefExpr e -> aEnv.isMapArrayVariable (e.name ());
      default -> false;
    };
  }

  /**
   * @return <code>true</code> if <code>aStart</code>, in the start position of
   *         a path, provably contains a RootedPath (XSLT 3.0 section 5.5.2
   *         [6]), whose trailing path is optional, so a bare <code>$v</code> is
   *         one on its own. A motionless pattern contains no RootedPath (section
   *         19.8.10) and a scanning expression must be equivalent to a
   *         motionless pattern (section 19.8.8.8). Predicates are deliberately
   *         not followed: <code>p[@status = $status-codes[1]]</code> is a
   *         motionless pattern of section 19.8.10.
   */
  private static boolean _isStartRootedPath (@NonNull final IExpr aStart)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aStart);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      // A function call is either a FunctionCallP (doc/id/element-with-id/key/
      // root), which is a RootedPath, or a name the pattern grammar cannot
      // derive at all
      if (aCur instanceof IExpr.VarRefExpr || aCur instanceof IExpr.FunctionCallExpr)
        return true;
      if (aCur instanceof final IExpr.FilterExpr aFilter)
        aStack.push (aFilter.primary ());
      else
        if (aCur instanceof final IExpr.SetExpr aSet)
        {
          aStack.push (aSet.left ());
          aStack.push (aSet.right ());
        }
        else
          if (aCur instanceof final IExpr.PathExpr aPath && aPath.start () != null)
            aStack.push (aPath.start ());
    }
    return false;
  }

  /**
   * @return <code>true</code> if a path start is of a kind the XSLT 3.0 pattern
   *         grammar (section 5.5.2) cannot derive anywhere, so a path built on it
   *         is provably not "syntactically equivalent to some motionless
   *         pattern". Kinds not listed (a union, a function call, a variable
   *         reference, a parenthesised path) might still be pattern-shaped.
   */
  private static boolean _isStartNeverPattern (@NonNull final IExpr aStart)
  {
    return switch (aStart)
    {
      case final IExpr.SequenceExpr e -> true;
      case final IExpr.RangeExpr e -> true;
      case final IExpr.BinaryExpr e -> true;
      case final IExpr.UnaryExpr e -> true;
      case final IExpr.ComparisonExpr e -> true;
      case final IExpr.StringConcatExpr e -> true;
      case final IExpr.IfExpr e -> true;
      case final IExpr.ForExpr e -> true;
      case final IExpr.LetExpr e -> true;
      case final IExpr.QuantifiedExpr e -> true;
      case final IExpr.MapConstructorExpr e -> true;
      case final IExpr.ArrayConstructorExpr e -> true;
      case final IExpr.InlineFunctionExpr e -> true;
      case final IExpr.SimpleMapExpr e -> true;
      case final IExpr.CastExpr e -> true;
      case final IExpr.InstanceOfExpr e -> true;
      case final IExpr.TreatExpr e -> true;
      // go-xslt lists the arrow expression here; the parser of phoxtrot
      // desugars "x => $f()" to a dynamic call, and the pattern grammar has
      // no dynamic call either
      case final IExpr.DynamicCallExpr e -> true;
      default -> false;
    };
  }

  /**
   * The general streamability rules of section 19.8.1.
   * <p>
   * The distinction drawn throughout this analysis is between "the
   * Recommendation says no" and "this analysis could not prove yes", and it is
   * drawn here by a single structural fact: the first loop returns early for
   * any operand that is itself roaming or free-ranging, which is how an
   * inconclusive sub-analysis propagates. Every clause reached after that loop
   * has a fully classified operand list in hand, and the verdict it reaches is
   * a positive finding about the stylesheet. Only the clauses whose text is
   * likewise unconditional mark the context as definite.
   *
   * @param aOperands
   *        The classified operands.
   * @param bMaxCardOne
   *        Selects the built-in-function clause: a single crawling transmission
   *        operand of <code>fn:head</code>/<code>exactly-one</code>/
   *        <code>zero-or-one</code> yields striding.
   * @param aEnv
   *        Receives the definite verdicts. May be <code>null</code>.
   */
  @NonNull
  private static PostureSweep _applyGeneralRules (@NonNull final List <StreamOperand> aOperands,
                                                  final boolean bMaxCardOne,
                                                  @Nullable final StreamContext aEnv)
  {
    if (aOperands.isEmpty ())
      return GM;

    // The potentially consuming operands with their adjusted sweep
    final ICommonsList <StreamOperand> aPC = new CommonsArrayList <> ();
    final ICommonsList <ESweep> aPCSweeps = new CommonsArrayList <> ();
    for (final StreamOperand aOp : aOperands)
    {
      if (aOp.sweep () == ESweep.FREE_RANGING || aOp.posture () == EPosture.ROAMING)
        return RF;
      final ESweep eSweep;
      if (aOp.posture () == EPosture.GROUNDED)
        eSweep = aOp.sweep ();
      else
      {
        EStreamUsage eUsage = aOp.usage ();
        if (eUsage == EStreamUsage.ABSORPTION && aOp.childless ())
          eUsage = EStreamUsage.INSPECTION;
        switch (eUsage)
        {
          case ABSORPTION:
            // Climbing+absorption is free-ranging: reading the subtree of an
            // ancestor means re-reading what the stream already passed. That is
            // the Climbing/Absorption cell of the "adjusted sweep" table of
            // section 19.8.1, which has no escape clause.
            if (aOp.posture () == EPosture.CLIMBING)
            {
              if (aEnv != null)
                aEnv.markDefinite ();
              return RF;
            }
            eSweep = ESweep.CONSUMING;
            break;
          case INSPECTION:
          case TRANSMISSION:
            eSweep = aOp.sweep ();
            break;
          default:
            // Navigation over a streamed node: the Navigation column of the
            // table is free-ranging for every non-grounded posture. Reported as
            // the verdict of the Recommendation only when the navigation usage is
            // one it states (fn:reverse, fn:innermost, map entry values, ...);
            // navigation reached any other way is this analysis declining to
            // guess.
            if (aOp.recUsage () && aEnv != null)
              aEnv.markDefinite ();
            return RF;
        }
      }
      if (eSweep == ESweep.CONSUMING ||
          (aOp.usage () == EStreamUsage.TRANSMISSION && aOp.posture () != EPosture.GROUNDED))
      {
        aPC.add (aOp);
        aPCSweeps.add (eSweep);
      }
    }

    if (aPC.isEmpty ())
      return GM;

    if (aPC.size () == 1)
    {
      final StreamOperand aOp = aPC.getFirstOrNull ();
      final ESweep eSweep = aPCSweeps.getFirstOrNull ();
      if (aOp.higherOrder ())
      {
        // "If exactly one operand o is potentially consuming: if o is a
        // higher-order operand of C, then roaming and free-ranging."
        if (aEnv != null)
          aEnv.markDefinite ();
        return RF;
      }
      if (aOp.usage () == EStreamUsage.ABSORPTION || aOp.usage () == EStreamUsage.INSPECTION)
        return new PostureSweep (EPosture.GROUNDED, ESweep.CONSUMING);
      if (bMaxCardOne && aOp.posture () == EPosture.CRAWLING)
        return new PostureSweep (EPosture.STRIDING, eSweep);
      return new PostureSweep (aOp.posture (), eSweep);
    }

    final int nChoice = aPC.getFirstOrNull ().choice ();
    if (nChoice != 0)
    {
      boolean bAllSame = true;
      for (final StreamOperand aOp : aPC)
        if (aOp.choice () != nChoice)
        {
          bAllSame = false;
          break;
        }
      if (bAllSame)
      {
        final ICommonsList <PostureSweep> aItems = new CommonsArrayList <> ();
        for (int i = 0; i < aPC.size (); ++i)
          aItems.add (new PostureSweep (aPC.get (i).posture (), aPCSweeps.get (i)));
        return _combineChoice (aItems);
      }
    }

    // All motionless with one common posture: e.g. (@a, @b), which can only
    // happen when every one of them has usage transmission
    final EPosture eFirst = aPC.getFirstOrNull ().posture ();
    for (int i = 0; i < aPC.size (); ++i)
      if (aPCSweeps.get (i) != ESweep.MOTIONLESS || aPC.get (i).posture () != eFirst)
      {
        // The last bullet of "If more than one operand is potentially
        // consuming", reached only after the choice-group and the
        // all-motionless bullets declined: two operands were proven to move
        // the stream
        if (aEnv != null)
          aEnv.markDefinite ();
        return RF;
      }
    return new PostureSweep (eFirst, ESweep.MOTIONLESS);
  }

  /**
   * The choice-operand-group clause: the posture is the combined posture of the
   * group and the sweep its widest. Grounded members contribute no streamed
   * nodes, so they do not conflict with a streamed posture; two different
   * streamed postures do (section 19.8.1 note: "if (X) then @name else name is
   * guaranteed streamable").
   */
  @NonNull
  private static PostureSweep _combineChoice (@NonNull final List <PostureSweep> aItems)
  {
    EPosture ePosture = EPosture.GROUNDED;
    boolean bSeen = false;
    ESweep eSweep = ESweep.MOTIONLESS;
    for (final PostureSweep aItem : aItems)
    {
      eSweep = eSweep.getWider (aItem.sweep ());
      if (aItem.posture () == EPosture.GROUNDED)
        continue;
      if (!bSeen)
      {
        ePosture = aItem.posture ();
        bSeen = true;
      }
      else
        if (ePosture != aItem.posture ())
          return RF;
    }
    return new PostureSweep (ePosture, eSweep);
  }

  /**
   * @return The context with the context item moved onto the items
   *         <code>aBase</code> yields - the focus a predicate or a following
   *         map/path step actually sees. Only the properties of the item move:
   *         the context item can no longer be assumed to be a document node,
   *         and its childlessness is that of the base. The current-item
   *         properties deliberately do not move.
   */
  @NonNull
  private static StreamContext _focusOn (@NonNull final IExpr aBase, @NonNull final StreamContext aEnv)
  {
    final StreamContext ret = aEnv.getClone ();
    ret.setContextChildless (_isChildless (aBase, aEnv));
    ret.setContextDocument (false);
    ret.setFocusReset (true);
    return ret;
  }

  /**
   * Classify an expression as an operand in a given usage, with the
   * childless flag set where the expression can be proven to yield only
   * childless items.
   */
  @NonNull
  private static StreamOperand _op (@NonNull final IExpr aExpr,
                                    @NonNull final EPosture eCP,
                                    @NonNull final StreamContext aEnv,
                                    @NonNull final EStreamUsage eUsage)
  {
    return StreamOperand.of (_expr (aExpr, eCP, aEnv), eUsage).withChildless (_isChildless (aExpr, aEnv));
  }

  /**
   * The general rules (section 19.8.1) for a construct with one operand of
   * the given usage.
   */
  @NonNull
  private static PostureSweep _one (@NonNull final IExpr aExpr,
                                    @NonNull final EPosture eCP,
                                    @NonNull final StreamContext aEnv,
                                    @NonNull final EStreamUsage eUsage)
  {
    return _applyGeneralRules (new CommonsArrayList <> (_op (aExpr, eCP, aEnv, eUsage)), false, aEnv);
  }

  /**
   * The general rules (section 19.8.1) for a construct with two operands of
   * the same usage, e.g. an arithmetic or comparison expression.
   */
  @NonNull
  private static PostureSweep _pair (@NonNull final IExpr aLeft,
                                     @NonNull final IExpr aRight,
                                     @NonNull final EPosture eCP,
                                     @NonNull final StreamContext aEnv,
                                     @NonNull final EStreamUsage eUsage)
  {
    return _applyGeneralRules (new CommonsArrayList <> (_op (aLeft, eCP, aEnv, eUsage),
                                                        _op (aRight, eCP, aEnv, eUsage)),
                               false,
                               aEnv);
  }

  /**
   * The general rules (section 19.8.1) for a construct whose operands all
   * have the same usage. An empty list gives grounded and motionless.
   */
  @NonNull
  private static PostureSweep _list (@NonNull final List <IExpr> aExprs,
                                     @NonNull final EPosture eCP,
                                     @NonNull final StreamContext aEnv,
                                     @NonNull final EStreamUsage eUsage)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    for (final IExpr aExpr : aExprs)
      aOps.add (_op (aExpr, eCP, aEnv, eUsage));
    return _applyGeneralRules (aOps, false, aEnv);
  }

  /**
   * Section 19.8.8.4 for union, intersect and except.
   *
   * @param bSubset
   *        The result can only be a subset of the left operand (intersect and
   *        except), which lets it keep the posture of the left operand rather
   *        than widening to crawling: no two streamed subtrees can end up nested
   *        in the result that were not nested in the left operand already.
   */
  @NonNull
  private static PostureSweep _unionLike (@NonNull final IExpr aLeft,
                                          @NonNull final IExpr aRight,
                                          @NonNull final EPosture eCP,
                                          @NonNull final StreamContext aEnv,
                                          final boolean bSubset)
  {
    final PostureSweep aL = _expr (aLeft, eCP, aEnv);
    final PostureSweep aR = _expr (aRight, eCP, aEnv);
    final EPosture eLP = aL.posture ();
    final EPosture eRP = aR.posture ();
    if (!aL.isStreamable () || !aR.isStreamable ())
      return RF;
    if (eLP == EPosture.GROUNDED && aL.sweep () == ESweep.MOTIONLESS)
    {
      if (bSubset)
      {
        // Nothing streamed survives a subset of a grounded sequence
        return new PostureSweep (eLP, aL.sweep ().getWider (aR.sweep ()));
      }
      return aR;
    }
    if (eRP == EPosture.GROUNDED && aR.sweep () == ESweep.MOTIONLESS)
      return aL;
    final ESweep eWider = aL.sweep ().getWider (aR.sweep ());
    if (bSubset)
      return new PostureSweep (eLP, eWider);
    if (eLP == EPosture.CLIMBING && eRP == EPosture.CLIMBING)
      return new PostureSweep (EPosture.CLIMBING, eWider);
    if ((eLP == EPosture.STRIDING || eLP == EPosture.CRAWLING) &&
        (eRP == EPosture.STRIDING || eRP == EPosture.CRAWLING))
      return new PostureSweep (EPosture.CRAWLING, eWider);
    return RF;
  }

  /** The predicate clause of a scanning expression: motionless (with a striding focus) and non-positional. */
  private static boolean _isPredicateScanning (@NonNull final IExpr aPredicate, @NonNull final StreamContext aEnv)
  {
    if (!_isNonPositional (aPredicate))
      return false;
    final PostureSweep aPS = _expr (aPredicate, EPosture.STRIDING, aEnv);
    return aPS.sweep () == ESweep.MOTIONLESS && aPS.posture () != EPosture.ROAMING;
  }

  /** The AxisStep clause of section 19.8.8.7: a downward axis whose every predicate is motionless and non-positional. */
  private static boolean _isStepScanning (final IExpr.@NonNull StepExpr aStep, @NonNull final StreamContext aEnv)
  {
    if (aStep.postfix () != null)
      return false;
    switch (aStep.axis ())
    {
      case CHILD:
      case DESCENDANT:
      case DESCENDANT_OR_SELF:
      case SELF:
        break;
      default:
        return false;
    }
    for (final IExpr aPredicate : aStep.predicates ())
      if (!_isPredicateScanning (aPredicate, aEnv))
        return false;
    return true;
  }

  /**
   * @return <code>true</code> if <code>aStart</code> is a ParenthesizedExprP
   *         (section 5.5.2 [14]) whose every union operand is itself shaped like
   *         a motionless pattern, so a path built on it can still be a scanning
   *         expression: <code>(a|b)/c</code> is a pattern.
   */
  private static boolean _isStartUnionScanning (@NonNull final IExpr aStart, @NonNull final StreamContext aEnv)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aStart);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      if (aCur instanceof final IExpr.SetExpr aSet && aSet.op () == IExpr.SetExpr.ESetOp.UNION)
      {
        aStack.push (aSet.left ());
        aStack.push (aSet.right ());
        continue;
      }
      if (!(aCur instanceof final IExpr.PathExpr aPath))
        return false;
      // A RootedPath, or a shape the grammar cannot derive
      if (aPath.start () != null)
        return false;
      // With a non-document context item the leading "/" is root(self::node()),
      // which section 19.8.9.18 rewrites to a climb, and the rewritten
      // expression is no longer syntactically a pattern
      if (aPath.absolute () && !aEnv.isContextDocument ())
        return false;
      for (final IExpr.StepExpr aStep : aPath.steps ())
        if (!_isStepScanning (aStep, aEnv))
          return false;
    }
    return true;
  }

  /**
   * @return <code>true</code> if the path truncated after step
   *         <code>nUpTo</code> is a scanning expression (section 19.8.8.7). The
   *         start expression is admissible when it merely names the nodes the
   *         downward scan begins at: "." and a variable reference.
   */
  private static boolean _isPrefixScanning (final IExpr.@NonNull PathExpr aPath,
                                            final int nUpTo,
                                            @NonNull final StreamContext aEnv)
  {
    if (aPath.absolute () && !aEnv.isContextDocument ())
      return false;
    final IExpr aStart = aPath.start ();
    if (aStart != null &&
        !(aStart instanceof IExpr.ContextItemExpr) &&
        !(aStart instanceof IExpr.VarRefExpr) &&
        !_isStartUnionScanning (aStart, aEnv))
      return false;
    for (int i = 0; i <= nUpTo; ++i)
      if (!_isStepScanning (aPath.steps ().get (i), aEnv))
        return false;
    return true;
  }

  /** The table of section 19.8.8.8 plus its preceding special clauses. */
  @NonNull
  private static PostureSweep _axisStep (@NonNull final EPosture eCP,
                                         final IExpr.@NonNull StepExpr aStep,
                                         @NonNull final StreamContext aEnv)
  {
    if (eCP == EPosture.GROUNDED)
      return GM;
    if (eCP == EPosture.ROAMING)
      return RF;

    final EAxis eAxis = aStep.axis ();
    final NodeTest aTest = aStep.test ();
    final boolean bSelectsElements = _isStepSelectingElements (eAxis, aTest);

    // A numeric, focus-independent predicate on the descendant axis picks at
    // most one node, so the result strides instead of crawling
    if (eCP == EPosture.STRIDING && (eAxis == EAxis.DESCENDANT || eAxis == EAxis.DESCENDANT_OR_SELF))
      for (final IExpr aPredicate : aStep.predicates ())
        if (_isNumericFocusFree (aPredicate))
          return new PostureSweep (EPosture.STRIDING, ESweep.CONSUMING);

    final EPosture ePosture;
    final ESweep eSweep;
    switch (eCP)
    {
      case CLIMBING:
        switch (eAxis)
        {
          case SELF:
          case PARENT:
          case ANCESTOR_OR_SELF:
          case ANCESTOR:
            ePosture = EPosture.CLIMBING;
            eSweep = ESweep.MOTIONLESS;
            break;
          case ATTRIBUTE:
          case NAMESPACE:
            ePosture = EPosture.STRIDING;
            eSweep = ESweep.MOTIONLESS;
            break;
          default:
            return RF;
        }
        break;
      case STRIDING:
        switch (eAxis)
        {
          case PARENT:
          case ANCESTOR_OR_SELF:
          case ANCESTOR:
            ePosture = EPosture.CLIMBING;
            eSweep = ESweep.MOTIONLESS;
            break;
          case SELF:
          case ATTRIBUTE:
          case NAMESPACE:
            ePosture = EPosture.STRIDING;
            eSweep = ESweep.MOTIONLESS;
            break;
          case CHILD:
            ePosture = EPosture.STRIDING;
            eSweep = ESweep.CONSUMING;
            break;
          case DESCENDANT:
          case DESCENDANT_OR_SELF:
            ePosture = bSelectsElements ? EPosture.CRAWLING : EPosture.STRIDING;
            eSweep = ESweep.CONSUMING;
            break;
          default:
            return RF;
        }
        break;
      case CRAWLING:
        switch (eAxis)
        {
          case PARENT:
          case ANCESTOR_OR_SELF:
          case ANCESTOR:
            ePosture = EPosture.CLIMBING;
            eSweep = ESweep.MOTIONLESS;
            break;
          case ATTRIBUTE:
          case NAMESPACE:
            ePosture = EPosture.STRIDING;
            eSweep = ESweep.MOTIONLESS;
            break;
          case SELF:
            ePosture = bSelectsElements ? EPosture.CRAWLING : EPosture.STRIDING;
            eSweep = ESweep.MOTIONLESS;
            break;
          default:
            return RF;
        }
        break;
      default:
        return RF;
    }

    // Predicates on an axis step are assessed with the posture and node kind of
    // the step as context; any that is not motionless makes the step roam (the
    // rule of section 19.8.8.8, not the filter-expression rule)
    if (!aStep.predicates ().isEmpty ())
    {
      final StreamContext aPredCtx = aEnv.getClone ();
      aPredCtx.setContextPosture (ePosture);
      aPredCtx.setContextDocument (false);
      aPredCtx.setContextChildless (_isStepChildless (eAxis, aTest));
      for (final IExpr aPredicate : aStep.predicates ())
      {
        final PostureSweep aPS = _expr (aPredicate, ePosture, aPredCtx);
        if (aPS.sweep () != ESweep.MOTIONLESS || aPS.posture () == EPosture.ROAMING)
        {
          // "If the PredicateList contains a Predicate that is not motionless,
          // then the sweep is free-ranging and the posture is roaming" - stated
          // flatly, with no escape clause
          aEnv.markDefinite ();
          return RF;
        }
      }
    }
    return new PostureSweep (ePosture, eSweep);
  }

  /** Section 19.8.8.10 for a filter expression. */
  @NonNull
  private static PostureSweep _applyPredicates (@NonNull final PostureSweep aBase,
                                                @NonNull final List <IExpr> aPredicates,
                                                @NonNull final StreamContext aEnv)
  {
    EPosture ePosture = aBase.posture ();
    for (final IExpr aPredicate : aPredicates)
    {
      if (ePosture == EPosture.CRAWLING && _isNumericFocusFree (aPredicate))
      {
        ePosture = EPosture.STRIDING;
        continue;
      }
      final PostureSweep aPS = _expr (aPredicate, ePosture, aEnv);
      if (aPS.sweep () != ESweep.MOTIONLESS || aPS.posture () == EPosture.ROAMING)
      {
        // The last bullet of section 19.8.8.10, reached only after the two
        // recovering bullets declined
        aEnv.markDefinite ();
        return RF;
      }
      // Posture and sweep of the base are unchanged by a motionless predicate
    }
    return new PostureSweep (ePosture, aBase.sweep ());
  }

  /** Path expressions (section 19.8.8.7) and axis steps (section 19.8.8.8). */
  @NonNull
  private static PostureSweep _path (final IExpr.@NonNull PathExpr aPath,
                                     @NonNull final EPosture eCP,
                                     @NonNull final StreamContext aEnv)
  {
    final IExpr aStart = aPath.start ();
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    EPosture ePosture = eCP;
    ESweep eSweep = ESweep.MOTIONLESS;
    // A leading "/" resolved to the context item rather than to an upward
    // climb, which is what lets the remaining steps still qualify as a scanning
    // expression (//a inside xsl:source-document)
    boolean bRootCollapsed = !aPath.absolute ();
    EPosture eBase = eCP;

    if (aStart != null)
    {
      final PostureSweep aPS = _expr (aStart, eCP, aEnv);
      ePosture = aPS.posture ();
      eSweep = aPS.sweep ();
      eBase = ePosture;
    }
    else
      if (aPath.absolute ())
      {
        // Sections 19.8.8.7 and 19.8.9.18: "/" becomes root(self::node()),
        // which collapses to the context item when that is statically a
        // document node in a striding posture, and otherwise to
        // head(ancestor-or-self::node()) - climbing, so any following downward
        // step is roaming. That is the note of the specification that //a
        // streams only when the context item type is document-node().
        if (aEnv.isContextDocument () && (eCP == EPosture.STRIDING || eCP == EPosture.GROUNDED))
          bRootCollapsed = true;
        else
          if (eCP == EPosture.GROUNDED)
            bRootCollapsed = true;
          else
            ePosture = EPosture.CLIMBING;
      }

    // "a/b/c" is a tree of binary expressions, (a/b)/c. Each binary level gets
    // its own provisional posture and its own scanning-expression
    // reassessment, so the walk reassesses after every step rather than once
    // over the whole path.
    for (int i = 0; i < aSteps.size (); ++i)
    {
      final IExpr.StepExpr aStep = aSteps.get (i);
      final PostureSweep aStepPS;
      if (aStep.postfix () != null)
      {
        // A postfix step is evaluated with each item the preceding step
        // delivered as its focus, not with the outer focus of the path
        final StreamContext aStepEnv = i > 0 ? _focusOn (new IExpr.PathExpr (null, false, aSteps.subList (0, i)), aEnv)
                                             : aEnv;
        final PostureSweep aPostfixPS = _expr (aStep.postfix (), ePosture, aStepEnv);
        // In go-xslt the predicates of a postfix step are part of the postfix
        // expression (a filter expression); the AST of phoxtrot keeps them on the
        // step, so the filter-expression rule (section 19.8.8.10) is applied
        // here
        aStepPS = aStep.predicates ().isEmpty () ? aPostfixPS
                                                 : _applyPredicates (aPostfixPS,
                                                                     aStep.predicates (),
                                                                     _focusOn (aStep.postfix (), aStepEnv));
      }
      else
        aStepPS = _axisStep (ePosture, aStep, aEnv);
      ePosture = aStepPS.posture ();
      eSweep = eSweep.getWider (aStepPS.sweep ());

      // Scanning-expression reassessment (section 19.8.8.8): a purely downward
      // path whose predicates are all motionless and non-positional can be
      // evaluated by testing each descendant as it streams past, so its
      // provisional roaming posture becomes crawling and its sweep is that
      // single pass. It belongs to the binary RelativePathExpr rule: a lone
      // AxisStep has no left operand and keeps the verdict of the table of
      // section 19.8.8.9.
      final boolean bHasLeftOperand = i > 0 || aStart != null || aPath.absolute ();
      if (ePosture == EPosture.ROAMING &&
          bHasLeftOperand &&
          bRootCollapsed &&
          eBase != EPosture.ROAMING &&
          _isPrefixScanning (aPath, i, aEnv))
      {
        ePosture = _isStepSelectingElements (aStep) ? EPosture.CRAWLING : EPosture.STRIDING;
        eSweep = ESweep.CONSUMING;
      }
      if (ePosture == EPosture.ROAMING && aStart != null && _isStartRootedPath (aStart))
      {
        // No pattern equivalent to this expression can be motionless (section
        // 19.8.10, first condition), whatever the postures turned out to be -
        // the Note of section 19.8.8.8 says exactly this of
        // $node//section/head. A fact about how the expression is written, so
        // it is reportable.
        aEnv.markDefinite ();
      }
      if (ePosture == EPosture.ROAMING && aStart != null && _isStartNeverPattern (aStart))
      {
        // A scanning expression is defined as one "syntactically equivalent to
        // some motionless pattern", and the pattern grammar cannot derive this
        // start at any depth
        aEnv.markDefinite ();
      }
    }
    return new PostureSweep (ePosture, eSweep);
  }

  /** Section 19.8.5.5 (shallow-descent) and section 19.8.5.6 (deep-descent). */
  @NonNull
  private static PostureSweep _descentCall (@NonNull final StreamFunctionSignature aSig,
                                            @NonNull final List <IExpr> aArgs,
                                            @NonNull final EPosture eCP,
                                            @NonNull final StreamContext aEnv)
  {
    final IExpr aFirst = aArgs.get (0);
    final PostureSweep aP0 = _applyGeneralRules (new CommonsArrayList <> (StreamOperand.of (_expr (aFirst, eCP, aEnv),
                                                                                           aSig.firstAdjust ())
                                                                                      .withChildless (_isChildless (aFirst,
                                                                                                                    aEnv))),
                                                 false,
                                                 aEnv);
    if (aP0.posture () != EPosture.STRIDING && aP0.posture () != EPosture.GROUNDED)
      return RF;
    // The arguments after the first form a construct of their own, which may
    // not itself reach into the stream
    final ICommonsList <StreamOperand> aRest = new CommonsArrayList <> ();
    for (int i = 1; i < aArgs.size (); ++i)
      aRest.add (_op (aArgs.get (i), eCP, aEnv, aSig.getUsage (i)));
    final PostureSweep aP1 = _applyGeneralRules (aRest, false, aEnv);
    if (aP1.posture () != EPosture.GROUNDED)
      return RF;
    final ESweep eS0 = aP0.sweep ();
    final ESweep eS1 = aP1.sweep ();
    if (eS0 == ESweep.FREE_RANGING ||
        eS1 == ESweep.FREE_RANGING ||
        (eS0 == ESweep.CONSUMING && eS1 == ESweep.CONSUMING))
      return RF;
    if (aP0.posture () == EPosture.GROUNDED)
      return new PostureSweep (EPosture.GROUNDED, eS0.getWider (eS1));
    // A deep selection can return nodes nested inside one another
    final EPosture ePosture = aSig.category () == EStreamFunctionCategory.DEEP_DESCENT ? EPosture.CRAWLING
                                                                                         : aP0.posture ();
    // The sweep stays s0 only where nothing that could carry a subtree can
    // reach the function - neither the declared parameter type nor the static
    // type of the argument. Otherwise the descent has to read into it.
    if (aSig.firstParamNodes () && !_isChildless (aFirst, aEnv))
      return new PostureSweep (ePosture, ESweep.CONSUMING);
    return new PostureSweep (ePosture, eS0);
  }

  /**
   * The operands of a call to a stylesheet function (section 19.8.5): the
   * first argument with the usage the caller determined from the category,
   * every further argument with the type-determined usage of its parameter.
   *
   * @param eFirstUsage
   *        The usage of the first argument.
   */
  @NonNull
  private static ICommonsList <StreamOperand> _stylesheetOperands (@NonNull final StreamFunctionSignature aSig,
                                                                   @NonNull final List <IExpr> aArgs,
                                                                   @NonNull final EPosture eCP,
                                                                   @NonNull final StreamContext aEnv,
                                                                   @NonNull final EStreamUsage eFirstUsage)
  {
    final ICommonsList <StreamOperand> ret = new CommonsArrayList <> ();
    for (int i = 0; i < aArgs.size (); ++i)
      ret.add (_op (aArgs.get (i), eCP, aEnv, i == 0 ? eFirstUsage : aSig.getUsage (i)));
    return ret;
  }

  /** A call to a stylesheet function (section 19.8.5). */
  @NonNull
  private static PostureSweep _stylesheetCall (@NonNull final StreamFunctionSignature aSig,
                                               @NonNull final List <IExpr> aArgs,
                                               @NonNull final EPosture eCP,
                                               @NonNull final StreamContext aEnv)
  {
    // "All function calls to zero-arity stylesheet functions are grounded and
    // motionless" - a streamed node can only reach a function through an
    // argument
    if (aArgs.isEmpty ())
      return GM;

    if (_hasPlaceholder (aArgs))
    {
      // Section 19.8.8.13: partially applying a declared-streamable function to
      // a supplied, non-grounded first argument would capture a streamed node in
      // the function item it produces
      if (aSig.streamable () &&
          !_isPlaceholder (aArgs.get (0)) &&
          _expr (aArgs.get (0), eCP, aEnv).posture () != EPosture.GROUNDED)
        return RF;
      final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
      for (int i = 0; i < aArgs.size (); ++i)
        if (!_isPlaceholder (aArgs.get (i)))
          aOps.add (_op (aArgs.get (i), eCP, aEnv, aSig.getUsage (i)));
      return _applyGeneralRules (aOps, false, aEnv);
    }

    // Every category but the two descent ones just re-labels the usage of the
    // first argument and hands the result to the general rules
    switch (aSig.category ())
    {
      case UNCLASSIFIED:
        return _applyGeneralRules (_stylesheetOperands (aSig, aArgs, eCP, aEnv, aSig.getUsage (0)), false, aEnv);
      case ABSORBING:
        // Section 19.8.5.2: absorption may not be applied to a crawling
        // sequence, whose members' subtrees can overlap
        if (_expr (aArgs.get (0), eCP, aEnv).posture () == EPosture.CRAWLING)
          return RF;
        return _applyGeneralRules (_stylesheetOperands (aSig, aArgs, eCP, aEnv, EStreamUsage.ABSORPTION), false, aEnv);
      case INSPECTION:
        return _applyGeneralRules (_stylesheetOperands (aSig, aArgs, eCP, aEnv, EStreamUsage.INSPECTION), false, aEnv);
      case FILTER:
        return _applyGeneralRules (_stylesheetOperands (aSig, aArgs, eCP, aEnv, EStreamUsage.TRANSMISSION),
                                   false,
                                   aEnv);
      case ASCENT:
      {
        // Section 19.8.5.7: the call reports on ancestors, so it climbs -
        // provided nothing it does moves the stream at all
        final PostureSweep aPS = _applyGeneralRules (_stylesheetOperands (aSig,
                                                                          aArgs,
                                                                          eCP,
                                                                          aEnv,
                                                                          EStreamUsage.INSPECTION),
                                                     false,
                                                     aEnv);
        if (aPS.posture () == EPosture.ROAMING || aPS.sweep () != ESweep.MOTIONLESS)
          return RF;
        if (aPS.posture () == EPosture.GROUNDED)
          return GM;
        return new PostureSweep (EPosture.CLIMBING, ESweep.MOTIONLESS);
      }
      case SHALLOW_DESCENT:
      case DEEP_DESCENT:
        return _descentCall (aSig, aArgs, eCP, aEnv);
      default:
        return RF;
    }
  }

  /** Static function calls (section 19.8.9). */
  @NonNull
  private static PostureSweep _functionCall (final IExpr.@NonNull FunctionCallExpr aCall,
                                             @NonNull final EPosture eCP,
                                             @NonNull final StreamContext aEnv)
  {
    final XdmQName aName = aCall.name ();
    List <IExpr> aArgs = aCall.args ();

    // Section 19.8.5: a call to a stylesheet function is classified from the
    // category the function declares, not from the built-in catalogue. A
    // stylesheet function may not be in a reserved namespace, so this lookup
    // can never shadow a built-in.
    final IStreamFunctionResolver aResolver = aEnv.getFunctionResolver ();
    if (aResolver != null && !XdmConstants.NS_FN.equals (aName.namespaceUri ()))
    {
      final StreamFunctionSignature aSig = aResolver.getStreamFunction (aName, aArgs.size ());
      if (aSig != null)
        return _stylesheetCall (aSig, aArgs, eCP, aEnv);
    }

    // Section 19.8.8.13: a partial application of a focus-dependent function is
    // roaming whenever the focus is streamed
    if (_hasPlaceholder (aArgs) && _isFocusDependent (aName, aArgs.size ()) && eCP != EPosture.GROUNDED)
      return RF;

    final String sName = _getBuiltinName (aName);
    if (sName == null)
    {
      // Fail closed: an unknown function (a stylesheet function the resolver
      // does not know, an extension function) may do anything with a streamed
      // node
      return RF;
    }

    // Short forms that default an argument to the context item (or to the
    // root) are analysed as the expanded call
    final ContextDefault aDefault = CONTEXT_DEFAULTS.get (sName);
    if (aDefault != null && aArgs.size () == aDefault.arity ())
    {
      final ICommonsList <IExpr> aNewArgs = new CommonsArrayList <> (aArgs);
      aNewArgs.add (aDefault.root () ? new IExpr.PathExpr (null, true, new CommonsArrayList <> ())
                                     : new IExpr.ContextItemExpr ());
      aArgs = aNewArgs;
    }

    switch (sName)
    {
      case "fn:position":
      case "fn:current-grouping-key":
      case "fn:current-merge-group":
      case "fn:current-merge-key":
        return GM;
      case "fn:last":
        // Section 19.8.9.14: usable only where the focus is grounded or
        // climbing. The rule reads only the context posture, so it cannot be
        // reached by failing to prove something.
        if (eCP == EPosture.STRIDING || eCP == EPosture.CRAWLING || eCP == EPosture.ROAMING)
        {
          aEnv.markDefinite ();
          return RF;
        }
        return GM;
      case "fn:current":
      {
        // Section 19.8.9.3: motionless, with the posture the context item would
        // have at the top of the containing XPath expression - the focus the
        // instruction established. Striding is the fallback.
        final EPosture eCurrent = aEnv.getCurrentPosture ();
        return new PostureSweep (eCurrent != null ? eCurrent : EPosture.STRIDING, ESweep.MOTIONLESS);
      }
      case "fn:current-group":
      {
        // Section 19.8.9.4: the posture and sweep of the select expression of
        // the containing xsl:for-each-group. A grounded population is already
        // materialised, so reading the group cannot move the stream.
        final EPosture eGroup = aEnv.getGroupPosture ();
        if (eGroup != null)
        {
          if (eGroup == EPosture.GROUNDED)
            return GM;
          return new PostureSweep (eGroup, ESweep.CONSUMING);
        }
        // With no containing xsl:for-each-group the section ends "Otherwise,
        // roaming and free-ranging" - stated flatly about a lexical fact
        aEnv.markDefinite ();
        return RF;
      }
      case "fn:accumulator-before":
      case "fn:accumulator-after":
      {
        // Sections 19.8.9.1/.2: always grounded, and free-ranging if the
        // accumulator name is not itself motionless
        if (aArgs.size () == 1 && _expr (aArgs.get (0), eCP, aEnv).sweep () != ESweep.MOTIONLESS)
          return new PostureSweep (EPosture.GROUNDED, ESweep.FREE_RANGING);
        // The pre-descent value of an accumulator for a node is known the
        // moment the stream reaches that node
        if (sName.equals ("fn:accumulator-before"))
          return GM;
        // Section 19.8.9.1 (accumulator-after), in the order of the
        // specification
        if (eCP == EPosture.GROUNDED || aEnv.isContextChildless ())
          return GM;
        // The focus-setting container of the call is not the innermost
        // containing instruction, so the stream would have to be re-read
        if (aEnv.isFocusReset ())
          return new PostureSweep (EPosture.GROUNDED, ESweep.FREE_RANGING);
        switch (aEnv.getAccumulatorPhase ())
        {
          case START:
            // A pre-descent rule may not read a post-descent value
            return new PostureSweep (EPosture.GROUNDED, ESweep.FREE_RANGING);
          case END:
            return GM;
          default:
            break;
        }
        // A preceding instruction of the same sequence constructor already
        // read the subtree
        if (aEnv.isAccumulatorAfterConsumed ())
          return GM;
        // The remaining clauses turn on finer detail of where the call sits in
        // its instruction, so the answer is the widest outcome they allow short
        // of rejecting
        return new PostureSweep (EPosture.GROUNDED, ESweep.CONSUMING);
      }
      case "fn:root":
      {
        // Section 19.8.9.18: root(X) collapses to X when X is statically a
        // document node in a striding posture; otherwise it is
        // head(X/ancestor-or-self::node())
        final PostureSweep aArgPS = aArgs.size () == 1 ? _expr (aArgs.get (0), eCP, aEnv)
                                                       : new PostureSweep (eCP, ESweep.MOTIONLESS);
        if (aArgPS.posture () == EPosture.GROUNDED)
          return new PostureSweep (EPosture.GROUNDED, aArgPS.sweep ());
        if (aEnv.isContextDocument () && aArgPS.posture () == EPosture.STRIDING && aArgs.isEmpty ())
          return aArgPS;
        return new PostureSweep (EPosture.CLIMBING, aArgPS.sweep ());
      }
      case "fn:outermost":
      {
        // Section 19.8.9.15: transmission, with crawling lifted to striding
        if (aArgs.size () != 1)
          return RF;
        final PostureSweep aArgPS = _expr (aArgs.get (0), eCP, aEnv);
        final PostureSweep ret = _applyGeneralRules (new CommonsArrayList <> (StreamOperand.of (aArgPS,
                                                                                               EStreamUsage.TRANSMISSION)),
                                                     false,
                                                     aEnv);
        if (aArgPS.posture () == EPosture.CRAWLING && ret.isStreamable ())
          return new PostureSweep (EPosture.STRIDING, ret.sweep ());
        return ret;
      }
      case "fn:head":
      case "fn:exactly-one":
      case "fn:zero-or-one":
        // The three built-ins with a transmission argument and a return type of
        // maximum cardinality one (the crawling clause of section 19.8.1)
        if (aArgs.size () != 1)
          return RF;
        return _applyGeneralRules (new CommonsArrayList <> (_op (aArgs.get (0), eCP, aEnv, EStreamUsage.TRANSMISSION)),
                                   true,
                                   aEnv);
      case "fn:normalize-space":
        // The table lists the 0-arity form with no operands, which would make
        // it motionless even though it reads the string value of the context
        // item. Treated as absorption of "." - the honest reading, and the
        // conservative one.
        if (aArgs.isEmpty ())
          aArgs = new CommonsArrayList <> (new IExpr.ContextItemExpr ());
        break;
      default:
        break;
    }

    final EStreamUsage [] aUsages = _getFunctionUsages (sName, aArgs.size ());
    if (aUsages == null)
    {
      // Fail closed: a function outside the catalogue may do anything with a
      // streamed node
      return RF;
    }
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    for (int i = 0; i < aArgs.size (); ++i)
    {
      final IExpr aArg = aArgs.get (i);
      if (!_isPlaceholder (aArg))
      {
        // The usage came from the proforma of section 19.8.9, so it is the
        // one of the Recommendation and not a fall-back
        aOps.add (_op (aArg, eCP, aEnv, aUsages[i]).withRecUsage (true));
      }
    }
    return _applyGeneralRules (aOps, false, aEnv);
  }

  /**
   * The expression walk for one expression, called through
   * {@link #_expr(IExpr, EPosture, StreamContext)}, which bounds the depth.
   */
  @NonNull
  private static PostureSweep _exprUnbounded (@NonNull final IExpr aExpr,
                                              @NonNull final EPosture eCP,
                                              @NonNull final StreamContext aEnv)
  {
    // The switch over the sealed interface is exhaustive, so no expression kind
    // can slip through without a rule
    return switch (aExpr)
    {
      case final IExpr.LiteralExpr e -> GM;
      // Section 19.8.8.12: the posture is the context posture; the sweep is
      // always motionless - the operand usage of the containing construct
      // decides whether reading "." actually consumes
      case final IExpr.ContextItemExpr e -> new PostureSweep (eCP, ESweep.MOTIONLESS);
      case final IExpr.VarRefExpr e ->
      {
        // Section 19.8.8.12: grounded and motionless unless bound to a
        // streaming parameter, where the per-category rules of section 19.8.5
        // decide
        if (!aEnv.isStreamingParam (e.name ()))
          yield GM;
        // A non-singular reference: the construct between here and the function
        // body is evaluated repeatedly, so the subtree of the parameter would
        // have to be read more than once
        if (aEnv.isInHigherOrder () && !aEnv.isStreamingParamRepeatable ())
          yield RF;
        // No category gives a streaming-parameter reference a grounded posture,
        // so none or grounded means the caller did not say: fail closed
        final EPosture eParam = aEnv.getStreamingParamPosture ();
        if (eParam == null || eParam == EPosture.GROUNDED)
          yield RF;
        yield new PostureSweep (eParam, aEnv.getStreamingParamSweep ());
      }
      case final IExpr.SequenceExpr e -> _list (e.items (), eCP, aEnv, EStreamUsage.TRANSMISSION);
      case final IExpr.RangeExpr e -> _pair (e.from (), e.to (), eCP, aEnv, EStreamUsage.ABSORPTION);
      case final IExpr.BinaryExpr e -> _pair (e.left (),
                                              e.right (),
                                              eCP,
                                              aEnv,
                                              e.op () == EBinaryOp.AND || e.op () == EBinaryOp.OR ? EStreamUsage.INSPECTION
                                                                                                 : EStreamUsage.ABSORPTION);
      case final IExpr.UnaryExpr e -> _one (e.operand (), eCP, aEnv, EStreamUsage.ABSORPTION);
      // Node comparisons (is, <<, >>) inspect, value and general comparisons
      // absorb
      case final IExpr.ComparisonExpr e -> _pair (e.left (),
                                                  e.right (),
                                                  eCP,
                                                  aEnv,
                                                  e.op ().isNode () ? EStreamUsage.INSPECTION : EStreamUsage.ABSORPTION);
      case final IExpr.StringConcatExpr e -> _list (e.parts (), eCP, aEnv, EStreamUsage.ABSORPTION);
      // intersect and except can only remove nodes, so the result is a subset
      // of the left operand and keeps its posture
      case final IExpr.SetExpr e -> _unionLike (e.left (),
                                                e.right (),
                                                eCP,
                                                aEnv,
                                                e.op () != IExpr.SetExpr.ESetOp.UNION);
      // Section 19.8.8.5: inspection, except document-node(element(...)) which
      // cannot be decided without consuming the document node
      case final IExpr.InstanceOfExpr e -> _one (e.operand (),
                                                 eCP,
                                                 aEnv,
                                                 _isDocElementTest (e.type ()) ? EStreamUsage.ABSORPTION
                                                                               : EStreamUsage.INSPECTION);
      case final IExpr.TreatExpr e ->
      {
        if (_isDocElementTest (e.type ()))
        {
          // Section 19.8.8.6: "If the ItemType of ST is a DocumentTest ... that
          // contains an ElementTest or SchemaElementTest then roaming and
          // free-ranging" - the premise is the written type, nothing inferred
          aEnv.markDefinite ();
          yield RF;
        }
        yield _one (e.operand (), eCP, aEnv, EStreamUsage.TRANSMISSION);
      }
      case final IExpr.CastExpr e -> _one (e.operand (), eCP, aEnv, EStreamUsage.ABSORPTION);
      case final IExpr.IfExpr e ->
      {
        // Section 19.8.8.3: the condition is inspected; then/else form a
        // choice operand group with usage transmission
        yield _applyGeneralRules (new CommonsArrayList <> (_op (e.condition (), eCP, aEnv, EStreamUsage.INSPECTION),
                                                           _op (e.thenExpr (),
                                                                eCP,
                                                                aEnv,
                                                                EStreamUsage.TRANSMISSION).withChoice (1),
                                                           _op (e.elseExpr (),
                                                                eCP,
                                                                aEnv,
                                                                EStreamUsage.TRANSMISSION).withChoice (1)),
                                  false,
                                  aEnv);
      }
      case final IExpr.ForExpr e ->
      {
        // Section 19.8.8.1: every "in" sequence must be grounded, else roaming;
        // the return clause is a higher-order transmission operand
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        for (final IExpr.Binding aBinding : e.bindings ())
        {
          final PostureSweep aPS = _expr (aBinding.expr (), eCP, aEnv);
          if (aPS.posture () != EPosture.GROUNDED)
            yield RF;
          aOps.add (StreamOperand.of (aPS, EStreamUsage.NAVIGATION));
        }
        aOps.add (StreamOperand.of (_expr (e.body (), eCP, aEnv.getAsInHigherOrder ()), EStreamUsage.TRANSMISSION)
                               .withHigherOrder (true));
        yield _applyGeneralRules (aOps, false, aEnv);
      }
      case final IExpr.LetExpr e ->
      {
        // let $var := N return T - navigation on the binding keeps a streamed
        // node out of the variable
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        for (final IExpr.Binding aBinding : e.bindings ())
          aOps.add (StreamOperand.of (_expr (aBinding.expr (), eCP, aEnv), EStreamUsage.NAVIGATION));
        aOps.add (_op (e.body (), eCP, aEnv, EStreamUsage.TRANSMISSION));
        yield _applyGeneralRules (aOps, false, aEnv);
      }
      case final IExpr.QuantifiedExpr e ->
      {
        // Section 19.8.8.2: "in" navigation, "satisfies" a higher-order
        // inspection operand
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        for (final IExpr.Binding aBinding : e.bindings ())
          aOps.add (StreamOperand.of (_expr (aBinding.expr (), eCP, aEnv), EStreamUsage.NAVIGATION));
        aOps.add (StreamOperand.of (_expr (e.satisfies (), eCP, aEnv.getAsInHigherOrder ()), EStreamUsage.INSPECTION)
                               .withHigherOrder (true));
        yield _applyGeneralRules (aOps, false, aEnv);
      }
      case final IExpr.PathExpr e -> _path (e, eCP, aEnv);
      case final IExpr.FilterExpr e -> _applyPredicates (_expr (e.primary (), eCP, aEnv),
                                                         e.predicates (),
                                                         _focusOn (e.primary (), aEnv));
      case final IExpr.SimpleMapExpr e ->
      {
        // The result takes the posture of the right operand, assessed with the
        // posture of the left operand as context. The specification gives the
        // sweep as that of the right operand alone; this takes the wider of the
        // two, since the left operand really is evaluated (and a wider sweep
        // can only reject, never wrongly accept).
        final List <IExpr> aSteps = e.steps ();
        if (aSteps.isEmpty ())
          yield GM;
        PostureSweep aCur = _expr (aSteps.get (0), eCP, aEnv);
        // Every step after the first runs once per item the one before it
        // delivered, which makes it a higher-order operand. That item - not the
        // outer focus - is also the context item, so its childlessness travels
        // with the posture.
        IExpr aPrev = aSteps.get (0);
        StreamContext aPrevEnv = aEnv;
        for (int i = 1; i < aSteps.size (); ++i)
        {
          final StreamContext aStepEnv = _focusOn (aPrev, aPrevEnv);
          aStepEnv.setInHigherOrder (true);
          final PostureSweep aStepPS = _expr (aSteps.get (i), aCur.posture (), aStepEnv);
          aCur = new PostureSweep (aStepPS.posture (), aCur.sweep ().getWider (aStepPS.sweep ()));
          aPrev = aSteps.get (i);
          aPrevEnv = aStepEnv;
        }
        yield aCur;
      }
      case final IExpr.FunctionCallExpr e -> _functionCall (e, eCP, aEnv);
      case final IExpr.DynamicCallExpr e ->
      {
        // Section 19.8.8.11: the base is inspected; each argument takes its
        // usage from the static signature of the base, and where none is
        // available the fall-back makes it navigation. The one signature
        // always readable is the map/array one the Note singles out: the
        // argument type is xs:anyAtomicType, so the usage is absorption.
        final boolean bMapOrArray = _isBaseMapOrArray (e.base (), aEnv);
        final EStreamUsage eArgUsage = bMapOrArray ? EStreamUsage.ABSORPTION : EStreamUsage.NAVIGATION;
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        aOps.add (StreamOperand.of (_expr (e.base (), eCP, aEnv), EStreamUsage.INSPECTION));
        for (final IExpr aArg : e.args ())
          if (!_isPlaceholder (aArg))
            aOps.add (StreamOperand.of (_expr (aArg, eCP, aEnv), eArgUsage)
                                   .withChildless (bMapOrArray && _isChildless (aArg, aEnv)));
        yield _applyGeneralRules (aOps, false, aEnv);
      }
      // Section 19.8.8.14
      case final IExpr.NamedFunctionRefExpr e -> _isFocusDependent (e.name (), e.arity ()) &&
                                                 eCP != EPosture.GROUNDED ? RF : GM;
      // Section 19.8.8.15: grounded and motionless unless it closes over a
      // streaming parameter
      case final IExpr.InlineFunctionExpr e -> _mentionsStreamingParam (e.body (), aEnv) ? RF : GM;
      case final IExpr.MapConstructorExpr e ->
      {
        // Section 19.8.8.17 defers a map constructor to the equivalent xsl:map
        // instruction, one xsl:map-entry per pair; section 19.8.4.24 gives the
        // key absorption and the select navigation ("the select expression must
        // not return nodes from a streamed input document"). The map is
        // grounded with the widest entry sweep.
        ESweep eSweep = ESweep.MOTIONLESS;
        for (int i = 0; i < e.keys ().size (); ++i)
        {
          final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
          aOps.add (_op (e.keys ().get (i), eCP, aEnv, EStreamUsage.ABSORPTION));
          if (i < e.values ().size ())
          {
            // Navigation here is the word of section 19.8.4.24, not a fall-back
            aOps.add (StreamOperand.of (_expr (e.values ().get (i), eCP, aEnv), EStreamUsage.NAVIGATION)
                                   .withRecUsage (true));
          }
          final PostureSweep aPS = _applyGeneralRules (aOps, false, aEnv);
          if (!aPS.isStreamable ())
            yield RF;
          eSweep = eSweep.getWider (aPS.sweep ());
        }
        yield new PostureSweep (EPosture.GROUNDED, eSweep);
      }
      case final IExpr.ArrayConstructorExpr e ->
      {
        // The draft has no array entry. Arrays hold items the same way maps do,
        // so members get the value usage of map:entry (navigation): an array
        // may not carry streamed nodes.
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        for (final IExpr aItem : e.items ())
          aOps.add (StreamOperand.of (_expr (aItem, eCP, aEnv), EStreamUsage.NAVIGATION));
        final PostureSweep aPS = _applyGeneralRules (aOps, false, aEnv);
        if (!aPS.isStreamable ())
          yield RF;
        yield new PostureSweep (EPosture.GROUNDED, aPS.sweep ());
      }
      case final IExpr.LookupExpr e ->
      {
        // base?key over a map or an array: the base is inspected, the key
        // atomized
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        if (e.base () == null)
          aOps.add (new StreamOperand (eCP, ESweep.MOTIONLESS, EStreamUsage.INSPECTION));
        else
          aOps.add (_op (e.base (), eCP, aEnv, EStreamUsage.INSPECTION));
        if (e.key () != null)
          aOps.add (_op (e.key (), eCP, aEnv, EStreamUsage.ABSORPTION));
        yield _applyGeneralRules (aOps, false, aEnv);
      }
      case final IExpr.PlaceholderExpr e -> GM;
    };
  }

  /**
   * The expression walk. Recursive, at most {@link #MAX_DEPTH} levels deep on
   * this thread.
   *
   * @throws DepthExceededException
   *         if the walk would go deeper; the public entry points turn it into
   *         roaming and free-ranging.
   */
  @NonNull
  private static PostureSweep _expr (@NonNull final IExpr aExpr,
                                     @NonNull final EPosture eCP,
                                     @NonNull final StreamContext aEnv)
  {
    final MutableInt aDepth = DEPTH.get ();
    if (aDepth.intValue () >= MAX_DEPTH)
      throw new DepthExceededException ();
    aDepth.inc ();
    try
    {
      return _exprUnbounded (aExpr, eCP, aEnv);
    }
    finally
    {
      aDepth.dec ();
    }
  }

  /**
   * Check the top-level predicates of a pattern step: non-positional and
   * motionless.
   */
  private static boolean _arePatternPredicatesMotionless (@NonNull final List <IExpr> aPredicates,
                                                          @NonNull final EPosture ePosture,
                                                          @NonNull final StreamContext aPredCtx)
  {
    for (final IExpr aPredicate : aPredicates)
    {
      if (!_isNonPositional (aPredicate))
        return false;
      final PostureSweep aPS = _expr (aPredicate, ePosture, aPredCtx);
      if (aPS.sweep () != ESweep.MOTIONLESS || aPS.posture () == EPosture.ROAMING)
        return false;
    }
    return true;
  }

  /**
   * One path alternative of a pattern. A parenthesised pattern in the first
   * step is pushed onto the stack to be checked as a pattern of its own.
   */
  private static boolean _isPatternPathMotionless (final IExpr.@NonNull PathExpr aPath,
                                                   @NonNull final StreamContext aEnv,
                                                   @NonNull final ArrayDeque <IExpr> aStack)
  {
    // RootedPath: a pattern anchored at a variable reference or a function call
    // ($doc//p, id('abc')). A leading "/" is not a RootedPath.
    if (aPath.start () != null)
      return false;
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    // fn:current() inside any predicate of the pattern denotes the node the
    // whole pattern matches, which is what the last step selects
    boolean bCurrentChildless = false;
    if (!aSteps.isEmpty ())
    {
      final IExpr.StepExpr aLast = aSteps.get (aSteps.size () - 1);
      if (aLast.postfix () == null)
        bCurrentChildless = _isStepChildless (aLast.axis (), aLast.test ());
    }
    for (int i = 0; i < aSteps.size (); ++i)
    {
      final IExpr.StepExpr aStep = aSteps.get (i);
      if (aStep.postfix () != null)
      {
        // A parenthesised sub-pattern after "/" is judged not motionless, as
        // go-xslt does (fail closed)
        if (i > 0)
          return false;
        // A parenthesised pattern ((a|b)[p], (p except q)): the combining
        // operators are transparent, each branch is assessed on its own, and a
        // predicate on the parenthesised whole is one more top-level predicate.
        // The context item type of such a predicate is not statically known
        // here, so it is assessed with a striding focus and no childless
        // assumption - the conservative side.
        aStack.push (aStep.postfix ());
        final StreamContext aPredCtx = aEnv.getClone ();
        aPredCtx.setContextPosture (EPosture.STRIDING);
        aPredCtx.setContextDocument (false);
        aPredCtx.setContextChildless (false);
        if (!_arePatternPredicatesMotionless (aStep.predicates (), EPosture.STRIDING, aPredCtx))
          return false;
        continue;
      }
      // Section 19.8.10 assesses the predicates of a pattern with a striding
      // focus. The one exception is a caller that already knows the nodes
      // being matched are grounded (an xsl:for-each-group over a materialised
      // population), where nothing a predicate does can move the stream.
      final EPosture ePredPosture = aEnv.getContextPosture () == EPosture.GROUNDED ? EPosture.GROUNDED
                                                                                    : EPosture.STRIDING;
      final StreamContext aPredCtx = aEnv.getClone ();
      aPredCtx.setContextPosture (ePredPosture);
      aPredCtx.setContextDocument (false);
      // A top-level predicate is assessed with the context item type set to the
      // static type of the expression it applies to, which is what makes
      // text()[starts-with(., '$')] motionless
      aPredCtx.setContextChildless (_isStepChildless (aStep.axis (), aStep.test ()));
      aPredCtx.setCurrentChildless (bCurrentChildless);
      if (!_arePatternPredicatesMotionless (aStep.predicates (), ePredPosture, aPredCtx))
        return false;
    }
    return true;
  }

  /** Section 19.8.10 over the AST of {@link CompiledXsltPattern#getPatternExpr()}. */
  private static boolean _isPatternMotionless (@NonNull final IExpr aPattern, @NonNull final StreamContext aEnv)
  {
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aPattern);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      switch (aCur)
      {
        case final IExpr.ContextItemExpr e:
          // The predicate pattern "." matches any item, like self::node()
          break;
        case final IExpr.FilterExpr e:
        {
          // A predicate pattern ".[p]": a self::node() step with predicates
          if (!(e.primary () instanceof IExpr.ContextItemExpr))
            return false;
          final EPosture ePredPosture = aEnv.getContextPosture () == EPosture.GROUNDED ? EPosture.GROUNDED
                                                                                        : EPosture.STRIDING;
          final StreamContext aPredCtx = aEnv.getClone ();
          aPredCtx.setContextPosture (ePredPosture);
          aPredCtx.setContextDocument (false);
          aPredCtx.setContextChildless (false);
          aPredCtx.setCurrentChildless (false);
          if (!_arePatternPredicatesMotionless (e.predicates (), ePredPosture, aPredCtx))
            return false;
          break;
        }
        case final IExpr.SetExpr e:
          // The section names only three disqualifiers (a RootedPath, a
          // non-motionless or positional top-level predicate, a streaming
          // parameter reference), so union, intersect and except are
          // transparent
          aStack.push (e.left ());
          aStack.push (e.right ());
          break;
        case final IExpr.PathExpr e:
          if (!_isPatternPathMotionless (e, aEnv, aStack))
            return false;
          break;
        default:
          // Anything else - a variable reference, a function call - is a
          // RootedPath, which section 19.8.10 excludes outright
          return false;
      }
    }
    return true;
  }

  /**
   * Run a recursive walk of the analysis and fail closed if it nests deeper
   * than {@link #MAX_DEPTH} or still overflows the stack: the result is then
   * roaming and free-ranging, and the "definite" flag is reset to its value
   * before the walk, because the verdict is not one of the Recommendation.
   *
   * @param aWalk
   *        The walk to run.
   * @param aCtx
   *        The context whose definite flag is restored on overflow.
   * @return The result of the walk, or roaming and free-ranging.
   */
  @NonNull
  private static PostureSweep _guarded (@NonNull final Supplier <PostureSweep> aWalk,
                                        @NonNull final StreamContext aCtx)
  {
    final MutableBoolean aDefinite = aCtx.getDefiniteFlag ();
    final boolean bDefiniteBefore = aDefinite != null && aDefinite.booleanValue ();
    try
    {
      return aWalk.get ();
    }
    catch (final DepthExceededException | StackOverflowError ex)
    {
      // Nested deeper than the limit or the stack of this thread allows: fail
      // closed, and not as a verdict of the Recommendation
      if (aDefinite != null)
        aDefinite.set (bDefiniteBefore);
      return RF;
    }
  }

  /**
   * Classify an expression (XSLT 3.0 sections 19.8.8 and 19.8.9).
   *
   * @param aExpr
   *        The parsed expression. <code>null</code> is treated as an absent
   *        operand: grounded and motionless.
   * @param aCtx
   *        What the containing construct knows about the focus, and the hooks
   *        of the XSLT layer. Not modified, except for its definite flag.
   * @return The posture and sweep. Never <code>null</code>.
   */
  @NonNull
  public static PostureSweep getStreamability (@Nullable final IExpr aExpr, @NonNull final StreamContext aCtx)
  {
    ValueEnforcer.notNull (aCtx, "Ctx");
    if (aExpr == null)
      return GM;
    return _guarded ( () -> _expr (aExpr, aCtx.getContextPosture (), aCtx), aCtx);
  }

  /**
   * Classify a match pattern (XSLT 3.0 section 19.8.10). A pattern always
   * yields a boolean, so it is either grounded and motionless or roaming and
   * free-ranging.
   *
   * @param aPatternExpr
   *        The pattern AST, as {@link CompiledXsltPattern#getPatternExpr()}
   *        returns it. <code>null</code> is grounded and motionless.
   * @param aCtx
   *        The context. Its context posture matters only when it is grounded
   *        (the matched nodes are known to be grounded, e.g. an
   *        <code>xsl:for-each-group</code> over a materialised population);
   *        otherwise predicates are assessed with a striding focus.
   * @return {@link PostureSweep#GROUNDED_MOTIONLESS} for a motionless pattern,
   *         {@link PostureSweep#ROAMING_FREE_RANGING} otherwise.
   */
  @NonNull
  public static PostureSweep getPatternStreamability (@Nullable final IExpr aPatternExpr,
                                                      @NonNull final StreamContext aCtx)
  {
    ValueEnforcer.notNull (aCtx, "Ctx");
    if (aPatternExpr == null)
      return GM;
    return _guarded ( () -> _isPatternMotionless (aPatternExpr, aCtx) ? GM : RF, aCtx);
  }

  /**
   * Classify a match pattern (XSLT 3.0 section 19.8.10).
   *
   * @param aPattern
   *        The pattern. Only a {@link CompiledXsltPattern} can be analysed; any
   *        other implementation fails closed. <code>null</code> is grounded and
   *        motionless.
   * @param aCtx
   *        The context, see {@link #getPatternStreamability(IExpr, StreamContext)}.
   * @return {@link PostureSweep#GROUNDED_MOTIONLESS} for a motionless pattern,
   *         {@link PostureSweep#ROAMING_FREE_RANGING} otherwise.
   */
  @NonNull
  public static PostureSweep getPatternStreamability (@Nullable final XsltPattern aPattern,
                                                      @NonNull final StreamContext aCtx)
  {
    ValueEnforcer.notNull (aCtx, "Ctx");
    if (aPattern == null)
      return GM;
    if (!(aPattern instanceof final CompiledXsltPattern aCompiled))
      return RF;
    return getPatternStreamability (aCompiled.getPatternExpr (), aCtx);
  }

  /**
   * Apply the general streamability rules of XSLT 3.0 section 19.8.1 to the
   * already classified operands of a construct. Verdicts are not reported as
   * definite.
   *
   * @param aOperands
   *        The operands. May be empty.
   * @return The posture and sweep of the construct.
   */
  @NonNull
  public static PostureSweep applyGeneralRules (@NonNull final List <StreamOperand> aOperands)
  {
    return applyGeneralRules (aOperands, null);
  }

  /**
   * Apply the general streamability rules of XSLT 3.0 section 19.8.1 to the
   * already classified operands of a construct, reporting the clauses the
   * Recommendation states unconditionally through
   * {@link StreamContext#markDefinite()}. An instruction classifier with a
   * context at hand should prefer this one.
   *
   * @param aOperands
   *        The operands. May be empty.
   * @param aCtx
   *        Receives definite verdicts. May be <code>null</code>.
   * @return The posture and sweep of the construct.
   */
  @NonNull
  public static PostureSweep applyGeneralRules (@NonNull final List <StreamOperand> aOperands,
                                                @Nullable final StreamContext aCtx)
  {
    ValueEnforcer.notNullNoNullValue (aOperands, "Operands");
    return _applyGeneralRules (aOperands, false, aCtx);
  }

  /**
   * The usage implied by a required type (XSLT 3.0 section 19.4): a function
   * type inspects, an atomic type absorbs, anything else navigates.
   *
   * @param bIsFunction
   *        The required type, occurrence indicator ignored, is
   *        <code>function(*)</code> or a subtype (including maps and arrays).
   * @param bIsAtomic
   *        The required type, occurrence indicator ignored, is atomic.
   * @return The type-determined usage.
   */
  @NonNull
  public static EStreamUsage getTypeDeterminedUsage (final boolean bIsFunction, final boolean bIsAtomic)
  {
    if (bIsFunction)
      return EStreamUsage.INSPECTION;
    if (bIsAtomic)
      return EStreamUsage.ABSORPTION;
    return EStreamUsage.NAVIGATION;
  }

  /**
   * The usage implied by a required type (XSLT 3.0 section 19.4).
   *
   * @param aType
   *        The required type. <code>null</code> stands for an absent type
   *        (<code>item()*</code>), which navigates.
   * @return The type-determined usage.
   */
  @NonNull
  public static EStreamUsage getTypeDeterminedUsage (@Nullable final SeqType aType)
  {
    if (aType == null || aType.empty () || aType.itemType () == null)
      return EStreamUsage.NAVIGATION;
    final ItemType.Kind eKind = aType.itemType ().kind ();
    // Maps and arrays are function types in XPath 3.1
    return getTypeDeterminedUsage (eKind == ItemType.Kind.FUNCTION ||
                                   eKind == ItemType.Kind.MAP ||
                                   eKind == ItemType.Kind.ARRAY,
                                   eKind == ItemType.Kind.ATOMIC);
  }

  /**
   * @param aExpr
   *        The expression. <code>null</code> is an absent operand.
   * @return <code>true</code> if the expression can be proven to yield only
   *         items without children, which downgrades an absorption operand to
   *         inspection (section 19.8.1). An absent operand is childless.
   */
  public static boolean isChildless (@Nullable final IExpr aExpr)
  {
    return aExpr == null || _isChildless (aExpr, null);
  }

  /**
   * {@link #isChildless(IExpr)} with the context the expression will be
   * evaluated in. It matters for the expressions that are the context item -
   * <code>.</code> and <code>fn:current()</code> - which are childless exactly
   * when the item they stand for is (the body of
   * <code>xsl:for-each select="@*"</code> doing
   * <code>xsl:value-of select="."</code>), and for streaming parameters, which
   * may hold elements.
   *
   * @param aExpr
   *        The expression. <code>null</code> is an absent operand.
   * @param aCtx
   *        The context.
   * @return <code>true</code> if the expression can be proven to yield only
   *         items without children.
   */
  public static boolean isChildless (@Nullable final IExpr aExpr, @NonNull final StreamContext aCtx)
  {
    ValueEnforcer.notNull (aCtx, "Ctx");
    return aExpr == null || _isChildless (aExpr, aCtx);
  }

  /**
   * @param aPredicate
   *        A predicate. May not be <code>null</code>.
   * @return <code>true</code> if it passes the non-positional test of section
   *         19.8.10: no call to <code>position</code>, <code>last</code> or
   *         <code>function-lookup</code> outside a nested predicate, and no
   *         static type that is (loosely) numeric. A caller that needs the
   *         value never to be numeric has to check the type itself.
   */
  public static boolean isNonPositionalPredicate (@NonNull final IExpr aPredicate)
  {
    ValueEnforcer.notNull (aPredicate, "Predicate");
    return _isNonPositional (aPredicate);
  }

  /**
   * @param aPatternExpr
   *        The pattern AST, as {@link CompiledXsltPattern#getPatternExpr()}
   *        returns it.
   * @return <code>true</code> if every node the pattern can match is childless
   *         (an attribute, text, comment, processing instruction or namespace
   *         node). It gives the static context item type section 19.8.1 needs
   *         for a construct whose focus is a node matched by this pattern.
   *         <code>false</code> for <code>null</code>.
   */
  public static boolean isPatternChildless (@Nullable final IExpr aPatternExpr)
  {
    if (aPatternExpr == null)
      return false;
    final ArrayDeque <IExpr> aStack = new ArrayDeque <> ();
    aStack.push (aPatternExpr);
    while (!aStack.isEmpty ())
    {
      final IExpr aCur = aStack.pop ();
      if (aCur instanceof final IExpr.SetExpr aSet && aSet.op () == IExpr.SetExpr.ESetOp.UNION)
      {
        aStack.push (aSet.left ());
        aStack.push (aSet.right ());
        continue;
      }
      if (!(aCur instanceof final IExpr.PathExpr aPath) || aPath.steps ().isEmpty ())
        return false;
      final IExpr.StepExpr aLast = aPath.steps ().get (aPath.steps ().size () - 1);
      if (aPath.start () == null &&
          !aPath.absolute () &&
          aPath.steps ().size () == 1 &&
          aLast.postfix () != null &&
          aLast.predicates ().isEmpty ())
      {
        // Outer parentheses of a union alternative
        aStack.push (aLast.postfix ());
        continue;
      }
      if (aLast.postfix () != null || !_isStepChildless (aLast.axis (), aLast.test ()))
        return false;
    }
    return true;
  }

  /**
   * @param aExpr
   *        The expression. May be <code>null</code>.
   * @param sLocalName
   *        Local name of a function in the <code>fn</code> namespace.
   * @return <code>true</code> if the expression calls (or references) that
   *         function anywhere within it. Used for the rule of section 19.8.4.35
   *         that a grouping-function call may not cross a stream boundary.
   */
  public static boolean usesFunction (@Nullable final IExpr aExpr, @NonNull final String sLocalName)
  {
    ValueEnforcer.notNull (sLocalName, "LocalName");
    if (aExpr == null)
      return false;
    return ExprWalker.containsFunction (aExpr,
                                        x -> XdmConstants.NS_FN.equals (x.namespaceUri ()) &&
                                             sLocalName.equals (x.localName ()));
  }

  /**
   * @param aExpr
   *        The expression. May be <code>null</code>.
   * @param aResolver
   *        The stylesheet function resolver. May be <code>null</code>.
   * @return <code>true</code> if the expression calls a stylesheet function
   *         that the resolver resolves to a declared-streamable one. It lets the
   *         XSLT layer scope the XTSE3430 diagnostic to the constructs whose
   *         rules are implemented completely.
   */
  public static boolean usesDeclaredStreamableFunction (@Nullable final IExpr aExpr,
                                                        @Nullable final IStreamFunctionResolver aResolver)
  {
    if (aExpr == null || aResolver == null)
      return false;
    return _containsMatch (aExpr, x -> {
      if (x instanceof final IExpr.FunctionCallExpr aCall &&
          !XdmConstants.NS_FN.equals (aCall.name ().namespaceUri ()))
      {
        final StreamFunctionSignature aSig = aResolver.getStreamFunction (aCall.name (), aCall.args ().size ());
        return aSig != null && aSig.streamable ();
      }
      return false;
    }, false);
  }
}
