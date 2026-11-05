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

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.numeric.mutable.MutableBoolean;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EOccurrence;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.pattern.CompiledXsltPattern;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.stream.EAccumulatorRulePhase;
import com.helger.xpath.stream.EPosture;
import com.helger.xpath.stream.EStreamFunctionCategory;
import com.helger.xpath.stream.EStreamUsage;
import com.helger.xpath.stream.ESweep;
import com.helger.xpath.stream.IStreamFunctionResolver;
import com.helger.xpath.stream.PostureSweep;
import com.helger.xpath.stream.StreamContext;
import com.helger.xpath.stream.StreamFunctionSignature;
import com.helger.xpath.stream.StreamOperand;
import com.helger.xpath.stream.XPathStreamability;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.AvtParser;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.StylesheetCompiler;
import com.helger.xslt.compile.XsltElementSpecs;
import com.helger.xslt.instr.CoreInstructions;
import com.helger.xslt.model.AttributeSet;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.UserFunction;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Guaranteed-streamability analysis at the instruction level (XSLT 3.0
 * section 19.8.3 to 19.8.6, and the streamability rules of sections 6.7.1,
 * 15.4, 18.1 and 18.2.8), built on the expression-level analysis of
 * {@link XPathStreamability}. A port of go-xslt's
 * <code>internal/xslt/streamability.go</code>.
 * <p>
 * The compiler calls {@link #check(StylesheetCompiler, StreamabilityIndex)}
 * only when streamability declarations are binding
 * ({@link com.helger.xslt.XsltCompileOptions#isEnforceStreamability()}). It
 * raises XTSE3430 for a construct declared streamable that is not guaranteed
 * streamable, and XTSE3155 for a declared-streamable stylesheet function
 * without parameters:
 * <ul>
 * <li>every template rule of a mode declared <code>streamable="yes"</code>:
 * motionless match pattern (section 19.8.10), a guaranteed-streamable body and
 * a grounded type-adjusted result (section 6.7.1);</li>
 * <li>every stylesheet function with a streamability category other than
 * <code>unclassified</code> (section 19.8.5);</li>
 * <li>every accumulator declared streamable (section 18.2.8);</li>
 * <li>every <code>xsl:merge-source</code> declared streamable (section
 * 15.4);</li>
 * <li>every <code>xsl:source-document</code> declared streamable (section
 * 18.1), reported only for a verdict the Recommendation states
 * unconditionally, a body that returns streamed nodes, or a body calling a
 * declared-streamable function (as go-xslt measured; anything else is this
 * analysis being incomplete and the construct runs buffered).</li>
 * </ul>
 * <p>
 * <b>Safety direction</b>: the analysis fails closed. An instruction is
 * streamable only if a rule below says so; an unknown instruction, an operand
 * the compiler did not record ({@link StreamabilityIndex}) or a nesting deeper
 * than {@link #MAX_DEPTH} is roaming and free-ranging.
 * <p>
 * The analysis walks the stylesheet tree ({@link XslElement}); the compiled
 * instructions are opaque. The walk recurses over the nesting of the
 * stylesheet, like the compiler itself, and is depth-limited.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsltStreamability
{
  /** The deepest instruction nesting analysed; anything deeper fails closed. */
  public static final int MAX_DEPTH = 1000;

  private static final PostureSweep GM = PostureSweep.GROUNDED_MOTIONLESS;
  private static final PostureSweep RF = PostureSweep.ROAMING_FREE_RANGING;
  private static final EStreamUsage ABSORPTION = EStreamUsage.ABSORPTION;
  private static final EStreamUsage INSPECTION = EStreamUsage.INSPECTION;
  private static final EStreamUsage TRANSMISSION = EStreamUsage.TRANSMISSION;
  private static final EStreamUsage NAVIGATION = EStreamUsage.NAVIGATION;
  private static final XdmQName VAR_VALUE = new XdmQName ("value");
  private static final String [] SORT_AVTS = { "lang", "order", "collation", "stable", "case-order", "data-type" };
  private static final String [] MERGE_KEY_AVTS = { "lang", "order", "collation", "case-order", "data-type" };
  private static final String [] NUMBER_AVTS = { "format",
                                                 "lang",
                                                 "letter-value",
                                                 "ordinal",
                                                 "start-at",
                                                 "grouping-separator",
                                                 "grouping-size" };
  private static final String [] RESULT_DOCUMENT_AVTS = { "format",
                                                          "href",
                                                          "method",
                                                          "allow-duplicate-names",
                                                          "build-tree",
                                                          "byte-order-mark",
                                                          "cdata-section-elements",
                                                          "doctype-public",
                                                          "doctype-system",
                                                          "encoding",
                                                          "escape-uri-attributes",
                                                          "html-version",
                                                          "include-content-type",
                                                          "indent",
                                                          "item-separator",
                                                          "json-node-output-method",
                                                          "media-type",
                                                          "normalization-form",
                                                          "omit-xml-declaration",
                                                          "output-version",
                                                          "parameter-document",
                                                          "standalone",
                                                          "suppress-indentation",
                                                          "undeclare-prefixes" };

  /** The first construct found non-streamable in one walk. */
  private record Failure (@Nullable XslNode location, @NonNull String reason)
  {}

  /** The outcome of classifying one sequence constructor. */
  private record Verdict (@NonNull PostureSweep result, @Nullable XsltException error)
  {
    boolean isStreamable ()
    {
      return error == null;
    }
  }

  private final StylesheetCompiler m_aCompiler;
  private final CompiledStylesheet m_aSheet;
  private final StreamabilityIndex m_aIndex;
  private final ICommonsSet <XdmQName> m_aStreamableModes = new CommonsHashSet <> ();
  // "EQName#arity" to the function with the highest import precedence
  private final ICommonsMap <String, UserFunction> m_aFunctions = new CommonsHashMap <> ();
  private final IStreamFunctionResolver m_aResolver = this::_resolveFunction;
  // The state of one walk, see _reset
  private Failure m_aFailure;
  private boolean m_bChildOfFork;
  private final ICommonsSet <XdmQName> m_aSetsInProgress = new CommonsHashSet <> ();
  private int m_nDepth;

  private XsltStreamability (@NonNull final StylesheetCompiler aCompiler, @NonNull final StreamabilityIndex aIndex)
  {
    m_aCompiler = aCompiler;
    m_aSheet = aCompiler.getStylesheet ();
    m_aIndex = aIndex;
    for (final Mode aMode : m_aSheet.modes ().values ())
      if (aMode.isStreamable ())
        m_aStreamableModes.add (aMode.getName ());
    for (final UserFunction aFunc : m_aSheet.functions ())
    {
      final String sKey = aFunc.getName ().getEQName () + "#" + aFunc.getArity ();
      final UserFunction aPrev = m_aFunctions.get (sKey);
      if (aPrev == null || aPrev.getElement ().getPrecedence () < aFunc.getElement ().getPrecedence ())
        m_aFunctions.put (sKey, aFunc);
    }
  }

  private static boolean _isKind (@Nullable final SeqType aType, final ItemType.@NonNull Kind... aKinds)
  {
    if (aType == null || aType.empty () || aType.itemType () == null)
      return false;
    for (final ItemType.Kind eKind : aKinds)
      if (aType.itemType ().kind () == eKind)
        return true;
    return false;
  }

  /** The type-determined usage of a required type (section 19.4). */
  @NonNull
  static EStreamUsage getTypeUsage (@Nullable final SeqType aType)
  {
    return XPathStreamability.getTypeDeterminedUsage (aType);
  }

  /**
   * A declared type naming a map or an array: the static knowledge the Note of
   * section 19.8.8.11 needs to read a dynamic call on the variable as a lookup.
   */
  static boolean isMapOrArray (@Nullable final SeqType aType)
  {
    return _isKind (aType, ItemType.Kind.MAP, ItemType.Kind.ARRAY);
  }

  /**
   * A declared type that section 19.4 places in the navigation column by
   * itself (a node test or <code>item()</code>), as opposed to navigation as
   * the fall-through for a type this analysis does not read.
   */
  static boolean isNavigationCertain (@Nullable final SeqType aType)
  {
    return _isKind (aType, ItemType.Kind.ITEM, ItemType.Kind.NODE);
  }

  /**
   * The usage that applies the function conversion rules to a value of the
   * declared type (the type-adjusted posture and sweep of section 19.8.5): a
   * type that converts nothing leaves posture and sweep untouched, which is
   * transmission rather than the navigation of section 19.4.
   */
  @NonNull
  static EStreamUsage getAdjustUsage (@Nullable final SeqType aType)
  {
    final EStreamUsage eUsage = getTypeUsage (aType);
    if (eUsage == ABSORPTION || eUsage == INSPECTION)
      return eUsage;
    return TRANSMISSION;
  }

  /**
   * Section 19.8.8.11: the declared type has a non-empty intersection with
   * U{N}, so a streaming parameter of that type can carry a streamed node. An
   * absent type (<code>item()*</code>) permits nodes.
   */
  static boolean isPermittingNodes (@Nullable final SeqType aType)
  {
    if (aType == null)
      return true;
    if (aType.empty ())
      return false;
    return !_isKind (aType, ItemType.Kind.ATOMIC, ItemType.Kind.FUNCTION, ItemType.Kind.MAP, ItemType.Kind.ARRAY);
  }

  /**
   * Section 19.8.5.5: the declared type can include a document or an element
   * node. A type this test cannot read answers <code>true</code>.
   */
  static boolean isAdmittingNodes (@Nullable final SeqType aType)
  {
    if (!isPermittingNodes (aType))
      return false;
    if (aType != null && _isKind (aType, ItemType.Kind.NODE) && aType.itemType ().nodeTest () != null)
      switch (aType.itemType ().nodeTest ().kind ())
      {
        case ATTRIBUTE, SCHEMA_ATTRIBUTE, TEXT, COMMENT, PROCESSING_INSTRUCTION, NAMESPACE_NODE:
          return false;
        default:
          break;
      }
    return true;
  }

  /**
   * The declared type permits more than one node: the signature rule of
   * section 19.8.5 for every category but absorbing.
   */
  static boolean isPermittingManyNodes (@Nullable final SeqType aType)
  {
    if (aType == null)
      return true;
    if (!isPermittingNodes (aType))
      return false;
    return aType.occurrence () == EOccurrence.ZERO_OR_MORE || aType.occurrence () == EOccurrence.ONE_OR_MORE;
  }

  /**
   * Read <code>xsl:function/@streamability</code> (section 19.8.5). A value in
   * an implementation-defined namespace, or one this processor does not know,
   * is analysed as <code>unclassified</code>.
   *
   * @param sValue
   *        The attribute value. May be <code>null</code>.
   * @return The category. Never <code>null</code>.
   */
  @NonNull
  static EStreamFunctionCategory getFunctionCategory (@Nullable final String sValue)
  {
    if (sValue == null)
      return EStreamFunctionCategory.UNCLASSIFIED;
    return switch (sValue.trim ())
    {
      case "absorbing" -> EStreamFunctionCategory.ABSORBING;
      case "inspection" -> EStreamFunctionCategory.INSPECTION;
      case "filter" -> EStreamFunctionCategory.FILTER;
      case "shallow-descent" -> EStreamFunctionCategory.SHALLOW_DESCENT;
      case "deep-descent" -> EStreamFunctionCategory.DEEP_DESCENT;
      case "ascent" -> EStreamFunctionCategory.ASCENT;
      default -> EStreamFunctionCategory.UNCLASSIFIED;
    };
  }

  @NonNull
  private static EStreamFunctionCategory _getFunctionCategory (@NonNull final UserFunction aFunc)
  {
    return getFunctionCategory (aFunc.getElement ().getAttr ("streamability"));
  }

  @NonNull
  private static String _getCategoryName (@NonNull final EStreamFunctionCategory eCategory)
  {
    return switch (eCategory)
    {
      case ABSORBING -> "absorbing";
      case INSPECTION -> "inspection";
      case FILTER -> "filter";
      case SHALLOW_DESCENT -> "shallow-descent";
      case DEEP_DESCENT -> "deep-descent";
      case ASCENT -> "ascent";
      default -> "unclassified";
    };
  }

  /**
   * Section 19.8.5: the constraint each category places on the posture and
   * sweep of the function result.
   */
  private static boolean _isFunctionResultAllowed (@NonNull final EStreamFunctionCategory eCategory,
                                                   @NonNull final PostureSweep aPS)
  {
    final EPosture eP = aPS.posture ();
    final ESweep eS = aPS.sweep ();
    return switch (eCategory)
    {
      case ABSORBING -> eP == EPosture.GROUNDED && eS != ESweep.FREE_RANGING;
      case INSPECTION -> eP == EPosture.GROUNDED && eS == ESweep.MOTIONLESS;
      case FILTER -> (eP == EPosture.STRIDING || eP == EPosture.GROUNDED) && eS == ESweep.MOTIONLESS;
      case SHALLOW_DESCENT -> (eP == EPosture.STRIDING || eP == EPosture.GROUNDED) && eS != ESweep.FREE_RANGING;
      case DEEP_DESCENT -> (eP == EPosture.CRAWLING || eP == EPosture.STRIDING || eP == EPosture.GROUNDED) &&
                           eS != ESweep.FREE_RANGING;
      // Section 19.8.5.7: climbing or grounded, and motionless
      case ASCENT -> (eP == EPosture.CLIMBING || eP == EPosture.GROUNDED) && eS == ESweep.MOTIONLESS;
      default -> true;
    };
  }

  /**
   * Section 19.8.5 "Rules for the function signature": every
   * declared-streamable category but absorbing needs a streaming parameter
   * that permits at most one node.
   */
  private static boolean _isSingleNodeCategory (@NonNull final EStreamFunctionCategory eCategory)
  {
    return switch (eCategory)
    {
      case INSPECTION, FILTER, SHALLOW_DESCENT, DEEP_DESCENT, ASCENT -> true;
      default -> false;
    };
  }

  @NonNull
  private static String _getModeName (@NonNull final XdmQName aMode)
  {
    return Mode.UNNAMED.equals (aMode) ? "#unnamed" : aMode.getEQName ();
  }

  @NonNull
  private static String _what (@NonNull final XslElement aEl)
  {
    if (aEl.isXsl ())
      return "xsl:" + aEl.getLocalName ();
    return "the literal result element " + aEl.getName ().getLexicalName ();
  }

  /**
   * The context posture and sweep of the implicit
   * <code>select="child::node()"</code> of <code>xsl:apply-templates</code>
   * (the child axis row of section 19.8.8.8).
   */
  @NonNull
  private static PostureSweep _getChildAxis (@NonNull final EPosture eContextPosture)
  {
    return switch (eContextPosture)
    {
      case GROUNDED -> GM;
      case STRIDING -> new PostureSweep (EPosture.STRIDING, ESweep.CONSUMING);
      default -> RF;
    };
  }

  /**
   * A crawling posture that comes from a descendant step written in the path
   * itself (<code>//</code> or the descendant axis outside any predicate or
   * string literal), not from combining striding operands with a union.
   */
  static boolean isWritingDescendantStep (@Nullable final String sExpr)
  {
    if (sExpr == null)
      return false;
    int nDepth = 0;
    char cQuote = 0;
    final int nLen = sExpr.length ();
    for (int i = 0; i < nLen; ++i)
    {
      final char c = sExpr.charAt (i);
      if (cQuote != 0)
      {
        if (c == cQuote)
          cQuote = 0;
      }
      else
        if (c == '\'' || c == '"')
          cQuote = c;
        else
          if (c == '[' || c == '(')
            nDepth++;
          else
            if (c == ']' || c == ')')
              nDepth--;
            else
              if (nDepth == 0)
              {
                if (c == '/' && i + 1 < nLen && sExpr.charAt (i + 1) == '/')
                  return true;
                if (c == 'd' && sExpr.startsWith ("descendant", i))
                  return true;
                // A union: the case of sx-union-202, see _forEach
                if (c == '|')
                  return false;
              }
    }
    return false;
  }

  /**
   * @return <code>true</code> if the element is written inside an
   *         <code>xsl:for-each-group</code> or an <code>xsl:merge</code>, which
   *         bind a current group an inner expression may refer to.
   */
  private static boolean _isInsideGroupingElement (@NonNull final XslElement aEl)
  {
    for (XslElement aCur = aEl.getParent (); aCur != null; aCur = aCur.getParent ())
      if (aCur.isXsl ("for-each-group") || aCur.isXsl ("merge"))
        return true;
    return false;
  }

  /**
   * The children of an element that form its sequence constructor: without
   * xsl:param, xsl:sort, xsl:with-param and xsl:context-item (like
   * {@link CompileContext#compileContent(XslElement)}).
   */
  @NonNull
  private static ICommonsList <XslNode> _getContent (@NonNull final XslElement aEl)
  {
    final ICommonsList <XslNode> ret = new CommonsArrayList <> ();
    for (final XslNode aNode : aEl.getChildren ())
    {
      if (aNode instanceof final XslElement aChild && aChild.isXsl ())
        switch (aChild.getLocalName ())
        {
          case "param", "sort", "with-param", "context-item":
            continue;
          default:
            break;
        }
      ret.add (aNode);
    }
    return ret;
  }

  /** All elements of the subtree of an element, the element included. */
  @NonNull
  private static ICommonsList <XslElement> _getSubtree (@NonNull final XslElement aRoot)
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    final ArrayDeque <XslElement> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final XslElement aCur = aStack.pop ();
      ret.add (aCur);
      final ICommonsList <XslElement> aChildren = aCur.getElementChildren ();
      for (int i = aChildren.size () - 1; i >= 0; --i)
        aStack.push (aChildren.get (i));
    }
    return ret;
  }

  @Nullable
  private StreamFunctionSignature _resolveFunction (@NonNull final XdmQName aName, final int nArity)
  {
    final UserFunction aFunc = m_aFunctions.get (aName.getEQName () + "#" + nArity);
    if (aFunc == null)
      return null;
    final EStreamFunctionCategory eCategory = _getFunctionCategory (aFunc);
    final ICommonsList <EStreamUsage> aUsages = new CommonsArrayList <> ();
    for (final VariableDef aParam : aFunc.getParams ())
      aUsages.add (getTypeUsage (aParam.getAs ()));
    final SeqType aFirst = aFunc.getParams ().isEmpty () ? null : aFunc.getParams ().getFirst ().getAs ();
    return new StreamFunctionSignature (eCategory,
                                        aUsages,
                                        getAdjustUsage (aFirst),
                                        aFunc.getParams ().isNotEmpty () && isAdmittingNodes (aFirst),
                                        eCategory != EStreamFunctionCategory.UNCLASSIFIED);
  }

  private void _reset ()
  {
    m_aFailure = null;
    m_bChildOfFork = false;
    m_aSetsInProgress.clear ();
    m_nDepth = 0;
  }

  @NonNull
  private PostureSweep _reject (@Nullable final XslNode aLocation, @NonNull final String sReason)
  {
    if (m_aFailure == null)
      m_aFailure = new Failure (aLocation, sReason);
    return RF;
  }

  /**
   * {@link #_reject(XslNode, String)} for a verdict the Recommendation states
   * unconditionally - a numbered rule of the instruction whose premises are
   * facts about the stylesheet - rather than one this analysis reached by
   * failing to prove something. Only those justify XTSE3430 for an
   * <code>xsl:source-document</code>.
   */
  @NonNull
  private PostureSweep _rejectRec (@NonNull final StreamContext aCtx,
                                   @NonNull final XslNode aLocation,
                                   @NonNull final String sReason)
  {
    aCtx.markDefinite ();
    return _reject (aLocation, sReason);
  }

  /**
   * @return The parsed expression of an attribute, or <code>null</code> if
   *         the attribute is absent. An attribute the compiler did not record
   *         makes the walk fail and also returns <code>null</code>.
   */
  @Nullable
  private IExpr _getExpr (@NonNull final XslElement aEl, @NonNull final String sAttr)
  {
    final String s = aEl.getAttr (sAttr);
    if (s == null)
      return null;
    final IExpr ret = m_aIndex.getExpression (aEl, s);
    if (ret == null)
      _reject (aEl, "the " + sAttr + " expression of " + _what (aEl) + " is not known to the analysis");
    return ret;
  }

  @Nullable
  private SeqType _getAs (@NonNull final XslElement aEl)
  {
    final String s = aEl.getAttr ("as");
    return s == null ? null : m_aIndex.getSequenceType (aEl, s);
  }

  private void _addExpr (@NonNull final ICommonsList <StreamOperand> aOps,
                         @NonNull final XslElement aEl,
                         @NonNull final String sAttr,
                         @NonNull final StreamContext aCtx,
                         @NonNull final EStreamUsage eUsage,
                         final boolean bHigherOrder,
                         final int nChoice,
                         final boolean bRecUsage)
  {
    final IExpr aExpr = _getExpr (aEl, sAttr);
    if (aExpr == null)
    {
      if (aEl.hasAttr (sAttr))
        aOps.add (StreamOperand.of (RF, eUsage));
      return;
    }
    final StreamContext aOpCtx = bHigherOrder ? aCtx.getAsInHigherOrder () : aCtx;
    final PostureSweep aPS = XPathStreamability.getStreamability (aExpr, aOpCtx);
    aOps.add (new StreamOperand (aPS.posture (),
                                 aPS.sweep (),
                                 eUsage,
                                 XPathStreamability.isChildless (aExpr, aOpCtx),
                                 bHigherOrder,
                                 nChoice,
                                 bRecUsage));
  }

  private void _addExpr (@NonNull final ICommonsList <StreamOperand> aOps,
                         @NonNull final XslElement aEl,
                         @NonNull final String sAttr,
                         @NonNull final StreamContext aCtx,
                         @NonNull final EStreamUsage eUsage)
  {
    _addExpr (aOps, aEl, sAttr, aCtx, eUsage, false, 0, false);
  }

  /**
   * One operand per expression of an attribute value template (or text value
   * template) written on an element; a constant template has none.
   */
  private void _addAvtText (@NonNull final ICommonsList <StreamOperand> aOps,
                            @NonNull final XslElement aEl,
                            @NonNull final String sText,
                            @NonNull final StreamContext aCtx,
                            @NonNull final EStreamUsage eUsage)
  {
    final ICommonsList <Object> aParts;
    try
    {
      aParts = AvtParser.split (sText);
    }
    catch (final XPathException ex)
    {
      _reject (aEl, "an attribute value template of " + _what (aEl) + " cannot be read");
      aOps.add (StreamOperand.of (RF, eUsage));
      return;
    }
    for (final Object aPart : aParts)
      if (aPart instanceof final AvtParser.ExprPart aExprPart)
      {
        final IExpr aExpr = m_aIndex.getExpression (aEl, aExprPart.text ());
        if (aExpr == null)
        {
          _reject (aEl, "an expression of a value template of " + _what (aEl) + " is not known to the analysis");
          aOps.add (StreamOperand.of (RF, eUsage));
        }
        else
          aOps.add (StreamOperand.of (XPathStreamability.getStreamability (aExpr, aCtx), eUsage)
                                 .withChildless (XPathStreamability.isChildless (aExpr, aCtx)));
      }
  }

  private void _addAvt (@NonNull final ICommonsList <StreamOperand> aOps,
                        @NonNull final XslElement aEl,
                        @NonNull final String sAttr,
                        @NonNull final StreamContext aCtx,
                        @NonNull final EStreamUsage eUsage)
  {
    final String s = aEl.getAttr (sAttr);
    if (s != null)
      _addAvtText (aOps, aEl, s, aCtx, eUsage);
  }

  @Nullable
  private XsltPattern _getPattern (@NonNull final XslElement aEl, @NonNull final String sAttr)
  {
    final String s = aEl.getAttr (sAttr);
    if (s == null)
      return null;
    final XsltPattern ret = m_aIndex.getPattern (aEl, s);
    if (ret == null)
      _reject (aEl, "the " + sAttr + " pattern of " + _what (aEl) + " is not known to the analysis");
    return ret;
  }

  private void _addPattern (@NonNull final ICommonsList <StreamOperand> aOps,
                            @NonNull final XslElement aEl,
                            @NonNull final String sAttr,
                            @NonNull final StreamContext aCtx,
                            @NonNull final EStreamUsage eUsage,
                            final boolean bHigherOrder)
  {
    if (!aEl.hasAttr (sAttr))
      return;
    final XsltPattern aPattern = _getPattern (aEl, sAttr);
    final PostureSweep aPS = aPattern == null ? RF
                                              : XPathStreamability.getPatternStreamability (aPattern,
                                                                                             bHigherOrder ? aCtx.getAsInHigherOrder ()
                                                                                                          : aCtx);
    aOps.add (new StreamOperand (aPS.posture (), aPS.sweep (), eUsage, true, bHigherOrder, 0, false));
  }

  /** A text node of a sequence constructor (section 19.8.3). */
  @NonNull
  private PostureSweep _text (@NonNull final XslText aText, @NonNull final StreamContext aCtx)
  {
    final XslElement aParent = aText.getParent ();
    if (aParent == null || !CompileContext.isExpandText (aText))
      return GM;
    // A text value template is an absorption operand
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addAvtText (aOps, aParent, aText.getValue (), aCtx, ABSORPTION);
    if (aOps.isEmpty ())
      return GM;
    return XPathStreamability.applyGeneralRules (aOps);
  }

  /**
   * Classify a sequence constructor (section 19.8.3): each contained
   * instruction is a transmission operand, and the general rules decide.
   */
  @NonNull
  private PostureSweep _body (@NonNull final List <XslNode> aChildren, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    StreamContext aCur = aCtx;
    for (final XslNode aNode : aChildren)
    {
      final PostureSweep aPS;
      if (aNode instanceof final XslText aText)
        aPS = _text (aText, aCur);
      else
      {
        final XslElement aEl = (XslElement) aNode;
        if (aEl.isXsl ("fallback"))
          continue;
        aPS = _instr (aEl, aCur);
        // A variable declared as a map or an array type is the static
        // signature the Note of section 19.8.8.11 needs to read a later $v(@x)
        // as a lookup. In scope for the following siblings, as in XSLT.
        if ((aEl.isXsl ("variable") || aEl.isXsl ("param")) && isMapOrArray (_getAs (aEl)))
        {
          final XdmQName aName = aEl.hasAttr ("name") ? aEl.resolveQName (aEl.getAttr ("name").trim (), false) : null;
          if (aName != null)
            aCur = aCur.getClone ().addMapArrayVariable (aName);
        }
      }
      // Section 19.8.9.1 treats the operands of a sequence constructor as
      // ordered: once an instruction consumed the stream, a later
      // accumulator-after is motionless
      if (aPS.sweep () == ESweep.CONSUMING && !aCur.isAccumulatorAfterConsumed ())
        aCur = aCur.getClone ().setAccumulatorAfterConsumed (true);
      aOps.add (StreamOperand.of (aPS, TRANSMISSION));
    }
    if (aOps.isEmpty ())
      return GM;
    return XPathStreamability.applyGeneralRules (aOps, aCtx);
  }

  private void _addBody (@NonNull final ICommonsList <StreamOperand> aOps,
                         @NonNull final List <XslNode> aChildren,
                         @NonNull final StreamContext aCtx,
                         @NonNull final EStreamUsage eUsage,
                         final boolean bHigherOrder,
                         final int nChoice)
  {
    if (aChildren.isEmpty ())
      return;
    final PostureSweep aPS = _body (aChildren, bHigherOrder ? aCtx.getAsInHigherOrder () : aCtx);
    aOps.add (new StreamOperand (aPS.posture (), aPS.sweep (), eUsage, false, bHigherOrder, nChoice, false));
  }

  private void _addBody (@NonNull final ICommonsList <StreamOperand> aOps,
                         @NonNull final List <XslNode> aChildren,
                         @NonNull final StreamContext aCtx,
                         @NonNull final EStreamUsage eUsage)
  {
    _addBody (aOps, aChildren, aCtx, eUsage, false, 0);
  }

  /**
   * xsl:param or xsl:with-param initialisers (section 19.8.4.9): the usage is
   * type-determined from the declared type.
   */
  @NonNull
  private ICommonsList <StreamOperand> _params (@NonNull final List <XslElement> aParams,
                                                @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> ret = new CommonsArrayList <> ();
    for (final XslElement aParam : aParams)
    {
      final EStreamUsage eUsage = getTypeUsage (_getAs (aParam));
      _addExpr (ret, aParam, "select", aCtx, eUsage);
      _addBody (ret, _getContent (aParam), aCtx, eUsage);
    }
    return ret;
  }

  /**
   * xsl:sort children (sections 19.8.4.5 and 19.8.4.31): the attribute value
   * templates absorb, and the sort key absorbs with a grounded focus.
   */
  @NonNull
  private ICommonsList <StreamOperand> _sorts (@NonNull final List <XslElement> aSorts,
                                               @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> ret = new CommonsArrayList <> ();
    for (final XslElement aSort : aSorts)
    {
      for (final String sAttr : SORT_AVTS)
        _addAvt (ret, aSort, sAttr, aCtx, ABSORPTION);
      final StreamContext aKeyCtx = aCtx.getClone ().setContextPosture (EPosture.GROUNDED).setContextDocument (false);
      _addExpr (ret, aSort, "select", aKeyCtx, ABSORPTION);
      _addBody (ret, _getContent (aSort), aKeyCtx, ABSORPTION);
    }
    return ret;
  }

  /** The names of a use-attribute-sets attribute, or <code>null</code> on error. */
  @Nullable
  private ICommonsList <XdmQName> _getUseAttributeSets (@NonNull final XslElement aEl, final boolean bXslNamespace)
  {
    final ICommonsList <XdmQName> ret = new CommonsArrayList <> ();
    final String s = bXslNamespace ? aEl.getAttribute (XsltNames.NS_XSL, "use-attribute-sets")
                                   : aEl.getAttr ("use-attribute-sets");
    if (s != null)
      for (final String sToken : XsltNames.tokenize (s))
      {
        final XdmQName aName = aEl.resolveQName (sToken, false);
        if (aName == null)
        {
          _reject (aEl, "the attribute set name " + sToken + " of " + _what (aEl) + " cannot be resolved");
          return null;
        }
        ret.add (m_aCompiler.mapAttributeSetName (aEl, aName));
      }
    return ret;
  }

  /**
   * A use-attribute-sets reference (section 19.8.6). The streamability is
   * based on the declared streamability of the named sets: a set not declared
   * <code>streamable="yes"</code> makes the referring instruction roaming and
   * free-ranging (the Notes of sections 19.8.4.1, 19.8.4.11 and 19.8.4.15),
   * and a set that is declared so must also be streamable. The operands are
   * the contents of every declaration of every set, with usage transmission.
   *
   * @return The combined posture and sweep, or <code>null</code> if the
   *         reference is not streamable.
   */
  @Nullable
  private PostureSweep _attributeSets (@NonNull final List <XdmQName> aNames,
                                       @NonNull final XslElement aEl,
                                       @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    for (final XdmQName aName : aNames)
    {
      // A cycle is XTSE0720, diagnosed by the compiler; here it only has to
      // terminate
      if (m_aSetsInProgress.contains (aName))
        continue;
      final AttributeSet aSet = m_aSheet.getAttributeSet (aName);
      if (aSet == null)
      {
        _reject (aEl, "the attribute set " + aName.getEQName () + " is not declared");
        return null;
      }
      for (final AttributeSet.Decl aDecl : aSet.decls ())
        if (!XsltNames.isXsltTrue (aDecl.element ().getAttr ("streamable")))
        {
          // Stated by the Recommendation without qualification, and turning
          // only on an attribute that is written or not
          aCtx.markDefinite ();
          _reject (aEl, "the attribute set " + aName.getEQName () + " is not declared streamable");
          return null;
        }
      m_aSetsInProgress.add (aName);
      try
      {
        for (final AttributeSet.Decl aDecl : aSet.decls ())
        {
          if (aDecl.uses ().isNotEmpty ())
          {
            final PostureSweep aUsed = _attributeSets (aDecl.uses (), aEl, aCtx);
            if (aUsed == null)
              return null;
            aOps.add (StreamOperand.of (aUsed, TRANSMISSION));
          }
          aOps.add (StreamOperand.of (_body (_getContent (aDecl.element ()), aCtx), TRANSMISSION));
        }
      }
      finally
      {
        m_aSetsInProgress.remove (aName);
      }
    }
    final PostureSweep ret = XPathStreamability.applyGeneralRules (aOps);
    if (!ret.isStreamable ())
    {
      _reject (aEl, "the attribute sets used by " + _what (aEl) + " are not streamable");
      return null;
    }
    return ret;
  }

  /**
   * The general streamability rules of section 19.8.1 over the operands of an
   * instruction, plus its use-attribute-sets reference.
   */
  @NonNull
  private PostureSweep _general (@NonNull final XslElement aEl,
                                 @NonNull final ICommonsList <StreamOperand> aOps,
                                 @Nullable final List <XdmQName> aUseSets,
                                 @NonNull final StreamContext aCtx)
  {
    if (aUseSets == null)
      return RF;
    if (!aUseSets.isEmpty ())
    {
      final PostureSweep aSets = _attributeSets (aUseSets, aEl, aCtx);
      if (aSets == null)
        return RF;
      aOps.add (StreamOperand.of (aSets, TRANSMISSION));
    }
    // The adjusted-sweep table of section 19.8.1 makes a navigation operand
    // over any posture but grounded free-ranging, with no escape clause.
    // Reported for the operand roles whose navigation usage the
    // Recommendation states outright.
    for (final StreamOperand aOp : aOps)
      if (aOp.recUsage () &&
          aOp.usage () == NAVIGATION &&
          aOp.posture () != EPosture.GROUNDED &&
          aOp.posture () != EPosture.ROAMING &&
          aOp.sweep () != ESweep.FREE_RANGING)
        return _rejectRec (aCtx,
                           aEl,
                           _what (aEl) + " binds a node of the streamed document, which the navigation usage of its initialiser forbids");
    return XPathStreamability.applyGeneralRules (aOps);
  }

  @NonNull
  private PostureSweep _general (@NonNull final XslElement aEl,
                                 @NonNull final ICommonsList <StreamOperand> aOps,
                                 @NonNull final StreamContext aCtx)
  {
    return _general (aEl, aOps, new CommonsArrayList <> (), aCtx);
  }

  /** The xsl:fallback content of an extension or unknown instruction (section 19.8.4.2). */
  @NonNull
  private PostureSweep _fallback (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <XslNode> aChildren = new CommonsArrayList <> ();
    for (final XslElement aFallback : aEl.getXslChildren ("fallback"))
      aChildren.addAll (aFallback.getChildren ());
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addBody (aOps, aChildren, aCtx, TRANSMISSION);
    return _general (aEl, aOps, aCtx);
  }

  /** Section 19.8.4.1: literal result elements. */
  @NonNull
  private PostureSweep _literalResultElement (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addBody (aOps, aEl.getChildren (), aCtx, ABSORPTION);
    for (final Map.Entry <XdmQName, String> aEntry : aEl.getAllAttributes ().entrySet ())
      if (!XsltNames.NS_XSL.equals (aEntry.getKey ().namespaceUri ()))
        _addAvtText (aOps, aEl, aEntry.getValue (), aCtx, ABSORPTION);
    return _general (aEl, aOps, _getUseAttributeSets (aEl, true), aCtx);
  }

  /** Section 19.8.4.41: a local xsl:variable. */
  @NonNull
  private PostureSweep _localVariable (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    EStreamUsage eSelectUsage = NAVIGATION;
    EStreamUsage eBodyUsage = ABSORPTION;
    // Without an as attribute the select expression navigates, as the
    // Recommendation states; with one the usage is type-determined, and only a
    // node or item type makes navigation the Recommendation's answer rather
    // than the fall-through of this analysis
    boolean bRecUsage = true;
    if (aEl.hasAttr ("as"))
    {
      final SeqType aAs = _getAs (aEl);
      eSelectUsage = getTypeUsage (aAs);
      eBodyUsage = eSelectUsage;
      bRecUsage = isNavigationCertain (aAs);
    }
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "select", aCtx, eSelectUsage, false, 0, bRecUsage);
    _addBody (aOps, _getContent (aEl), aCtx, eBodyUsage);
    return _general (aEl, aOps, aCtx);
  }

  /** Section 19.8.4.18: xsl:for-each. */
  @NonNull
  private PostureSweep _forEach (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final IExpr aSelect = _getExpr (aEl, "select");
    final PostureSweep aSel = XPathStreamability.getStreamability (aSelect, aCtx);
    final ICommonsList <XslElement> aSorts = aEl.getXslChildren ("sort");
    final ICommonsList <XslNode> aContent = _getContent (aEl);
    if (aSel.posture () == EPosture.GROUNDED)
    {
      final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
      aOps.add (StreamOperand.of (aSel, INSPECTION));
      // A grounded population makes fn:current() in the body grounded too
      final StreamContext aBodyCtx = aCtx.getAsInHigherOrder ()
                                         .getClone ()
                                         .setContextPosture (EPosture.GROUNDED)
                                         .setContextDocument (false)
                                         .setCurrentPosture (EPosture.GROUNDED);
      aOps.add (StreamOperand.of (_body (aContent, aBodyCtx), TRANSMISSION).withHigherOrder (true));
      aOps.addAll (_sorts (aSorts, aCtx));
      return XPathStreamability.applyGeneralRules (aOps, aCtx);
    }
    if (aSorts.isNotEmpty ())
      return _reject (aEl, "xsl:for-each over streamed nodes cannot sort");
    // The context item of the body is one item of the selection, so what the
    // selection can yield decides whether reading its value has to descend
    final StreamContext aBodyCtx = aCtx.getClone ()
                                       .setContextPosture (aSel.posture ())
                                       .setCurrentPosture (aSel.posture ())
                                       .setContextDocument (false)
                                       .setContextChildless (XPathStreamability.isChildless (aSelect, aCtx));
    final PostureSweep aBody = _body (aContent, aBodyCtx);
    if (aSel.posture () == EPosture.CRAWLING && aBody.sweep () == ESweep.CONSUMING)
    {
      // Third rule of section 19.8.4.18. Reported as definite only for a
      // descendant step written in the path: a union of striding paths
      // (sx-union-C) is crawling too, but the suite expects it to run, as
      // go-xslt measured
      if (isWritingDescendantStep (aEl.getAttr ("select")))
        return _rejectRec (aCtx, aEl, "the body of xsl:for-each consumes a crawling selection");
      return _reject (aEl, "the body of xsl:for-each consumes a crawling selection");
    }
    if (!aSel.isStreamable ())
      return _reject (aEl, "the select expression of xsl:for-each is not streamable");
    if (!aBody.isStreamable ())
      return _reject (aEl, "the body of xsl:for-each is not streamable");
    return new PostureSweep (aBody.posture (), aSel.sweep ().getWider (aBody.sweep ()));
  }

  /** Section 19.8.4.19: xsl:for-each-group. */
  @NonNull
  private PostureSweep _forEachGroup (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    // Only this instruction itself is the child of xsl:fork, not the
    // instructions nested in it
    final boolean bChildOfFork = m_bChildOfFork;
    m_bChildOfFork = false;

    final IExpr aSelect = _getExpr (aEl, "select");
    final PostureSweep aSel = XPathStreamability.getStreamability (aSelect, aCtx);
    final boolean bChildless = XPathStreamability.isChildless (aSelect, aCtx);
    final String sKeyAttr = aEl.hasAttr ("group-by") ? "group-by" : aEl.hasAttr ("group-adjacent") ? "group-adjacent"
                                                                                                    : null;
    final String sPatternAttr = aEl.hasAttr ("group-starting-with") ? "group-starting-with"
                                                                    : aEl.hasAttr ("group-ending-with") ? "group-ending-with"
                                                                                                         : null;
    final ICommonsList <XslElement> aSorts = aEl.getXslChildren ("sort");
    final ICommonsList <XslNode> aContent = _getContent (aEl);
    final ICommonsList <StreamOperand> aCollation = new CommonsArrayList <> ();
    _addAvt (aCollation, aEl, "collation", aCtx, ABSORPTION);

    if (aSel.posture () == EPosture.GROUNDED)
    {
      final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
      aOps.add (StreamOperand.of (aSel, INSPECTION));
      aOps.addAll (aCollation);
      aOps.addAll (_sorts (aSorts, aCtx));
      final StreamContext aGroundedCtx = aCtx.getClone ().setContextPosture (EPosture.GROUNDED).setContextDocument (false);
      if (sKeyAttr != null)
        _addExpr (aOps, aEl, sKeyAttr, aGroundedCtx, ABSORPTION);
      // The patterns are matched against the population, which is grounded
      if (sPatternAttr != null)
        _addPattern (aOps, aEl, sPatternAttr, aGroundedCtx, INSPECTION, true);
      // The body can still reach back into the enclosing stream through the
      // context of the xsl:for-each-group itself
      final StreamContext aBodyCtx = aGroundedCtx.getClone ().setGroupPosture (EPosture.GROUNDED).getAsInHigherOrder ();
      aOps.add (StreamOperand.of (_body (aContent, aBodyCtx), TRANSMISSION).withHigherOrder (true));
      return XPathStreamability.applyGeneralRules (aOps);
    }
    if (aEl.hasAttr ("group-by") && !bChildOfFork)
    {
      // Second rule: group-by and not a child of xsl:fork
      return _rejectRec (aCtx,
                         aEl,
                         "xsl:for-each-group with group-by over streamed nodes is only streamable as a child of xsl:fork");
    }
    if (sKeyAttr != null)
    {
      // The grouping key is evaluated with an item of the population as its
      // focus
      final StreamContext aKeyCtx = aCtx.getClone ()
                                        .setContextPosture (aSel.posture ())
                                        .setContextDocument (false)
                                        .setContextChildless (bChildless);
      final IExpr aKey = _getExpr (aEl, sKeyAttr);
      if (XPathStreamability.getStreamability (aKey, aKeyCtx).sweep () != ESweep.MOTIONLESS)
      {
        // Third rule: a grouping key that is not motionless
        return _rejectRec (aCtx, aEl, "the grouping key of xsl:for-each-group is not motionless");
      }
    }
    if (aSorts.isNotEmpty ())
      return _reject (aEl, "xsl:for-each-group over streamed nodes cannot sort");
    for (final StreamOperand aOp : aCollation)
      if (aOp.posture () != EPosture.GROUNDED || aOp.sweep () != ESweep.MOTIONLESS)
        return _reject (aEl, "the collation of xsl:for-each-group is not grounded and motionless");
    if (sPatternAttr != null)
    {
      // Section 19.8.4.19 lists the patterns as higher-order operands with
      // usage inspection only in its rule for a grounded population. The suite
      // settles the streamed case: si-group-030 and si-group-063 require
      // XTSE3430 for a pattern with a value or positional predicate, and
      // section 19.8.10 lists p[b] as not motionless.
      final StreamContext aPatternCtx = aCtx.getClone ().setContextPosture (EPosture.STRIDING).setContextDocument (false);
      final XsltPattern aPattern = _getPattern (aEl, sPatternAttr);
      if (aPattern == null ||
          XPathStreamability.getPatternStreamability (aPattern, aPatternCtx).sweep () != ESweep.MOTIONLESS)
        return _reject (aEl, "the grouping pattern of xsl:for-each-group is not motionless");
    }
    final StreamContext aBodyCtx = aCtx.getClone ()
                                       .setContextPosture (aSel.posture ())
                                       .setCurrentPosture (aSel.posture ())
                                       .setContextDocument (false)
                                       .setGroupPosture (aSel.posture ());
    final PostureSweep aBody = _body (aContent, aBodyCtx);
    if (aSel.posture () == EPosture.CRAWLING && aBody.sweep () == ESweep.CONSUMING)
    {
      // Fifth rule: a crawling population and a consuming body
      return _rejectRec (aCtx, aEl, "the body of xsl:for-each-group consumes a crawling population");
    }
    if (!aSel.isStreamable () || !aBody.isStreamable ())
      return _reject (aEl, "xsl:for-each-group is not streamable");
    return new PostureSweep (aBody.posture (), aSel.sweep ().getWider (aBody.sweep ()));
  }

  /** The xsl:on-completion child of xsl:iterate. */
  @NonNull
  private PostureSweep _onCompletion (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "select", aCtx, TRANSMISSION);
    _addBody (aOps, _getContent (aEl), aCtx, TRANSMISSION);
    if (aOps.isEmpty ())
      return GM;
    return XPathStreamability.applyGeneralRules (aOps, aCtx);
  }

  /** Section 19.8.4.22: xsl:iterate. */
  @NonNull
  private PostureSweep _iterate (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final IExpr aSelect = _getExpr (aEl, "select");
    final PostureSweep aSel = XPathStreamability.getStreamability (aSelect, aCtx);
    final ICommonsList <XslElement> aParams = aEl.getXslChildren ("param");
    final ICommonsList <XslElement> aCompletions = aEl.getXslChildren ("on-completion");
    final XslElement aCompletion = aCompletions.isEmpty () ? null : aCompletions.getFirst ();
    final ICommonsList <XslNode> aContent = new CommonsArrayList <> ();
    for (final XslNode aNode : _getContent (aEl))
      if (!(aNode instanceof final XslElement aChild && aChild.isXsl ("on-completion")))
        aContent.add (aNode);
    // xsl:on-completion has no context item: a roaming context posture makes
    // any reference to the focus fail
    final StreamContext aCompletionCtx = aCtx.getClone ().setContextPosture (EPosture.ROAMING);

    if (aSel.posture () == EPosture.GROUNDED)
    {
      final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
      aOps.add (StreamOperand.of (aSel, INSPECTION));
      for (final StreamOperand aOp : _params (aParams, aCtx))
        aOps.add (new StreamOperand (aOp.posture (), aOp.sweep (), NAVIGATION));
      final StreamContext aBodyCtx = aCtx.getClone ()
                                         .setContextPosture (aSel.posture ())
                                         .setCurrentPosture (aSel.posture ());
      aOps.add (StreamOperand.of (_body (aContent, aBodyCtx), TRANSMISSION));
      if (aCompletion != null)
        aOps.add (StreamOperand.of (_onCompletion (aCompletion, aCompletionCtx), TRANSMISSION));
      return XPathStreamability.applyGeneralRules (aOps);
    }
    for (final StreamOperand aOp : _params (aParams, aCtx))
      if (aOp.posture () != EPosture.GROUNDED || aOp.sweep () != ESweep.MOTIONLESS)
      {
        // First rule of the non-grounded case
        return _rejectRec (aCtx, aEl, "a parameter of xsl:iterate is not grounded and motionless");
      }
    if (aCompletion != null)
    {
      final PostureSweep aPS = _onCompletion (aCompletion, aCompletionCtx);
      if (aPS.posture () != EPosture.GROUNDED || aPS.sweep () != ESweep.MOTIONLESS)
      {
        // Second rule
        return _rejectRec (aCtx, aEl, "xsl:on-completion is not grounded and motionless");
      }
    }
    final StreamContext aBodyCtx = aCtx.getClone ()
                                       .setContextPosture (aSel.posture ())
                                       .setCurrentPosture (aSel.posture ())
                                       .setContextDocument (false)
                                       .setContextChildless (XPathStreamability.isChildless (aSelect, aCtx));
    final PostureSweep aBody = _body (aContent, aBodyCtx);
    if (aSel.posture () == EPosture.CRAWLING && aBody.sweep () == ESweep.CONSUMING)
    {
      // Third rule
      return _rejectRec (aCtx, aEl, "the body of xsl:iterate consumes a crawling selection");
    }
    if (!aSel.isStreamable () || !aBody.isStreamable ())
      return _reject (aEl, "xsl:iterate is not streamable");
    return new PostureSweep (aBody.posture (), aSel.sweep ().getWider (aBody.sweep ()));
  }

  /** Section 19.8.4.5: xsl:apply-templates. */
  @NonNull
  private PostureSweep _applyTemplates (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final IExpr aSelect = _getExpr (aEl, "select");
    final PostureSweep aSel;
    final boolean bChildless;
    if (aEl.hasAttr ("select"))
    {
      aSel = XPathStreamability.getStreamability (aSelect, aCtx);
      // A selection of childless nodes (attributes, text) is inspected
      // rather than absorbed, which makes select="@*" motionless
      bChildless = XPathStreamability.isChildless (aSelect, aCtx);
    }
    else
    {
      // An absent select is an implicit child::node(), which can select
      // elements
      aSel = _getChildAxis (aCtx.getContextPosture ());
      bChildless = false;
    }
    final ICommonsList <XslElement> aSorts = aEl.getXslChildren ("sort");
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    aOps.add (StreamOperand.of (aSel, ABSORPTION).withChildless (bChildless));
    aOps.addAll (_params (aEl.getXslChildren ("with-param"), aCtx));
    if (aSel.posture () == EPosture.GROUNDED)
    {
      aOps.addAll (_sorts (aSorts, aCtx));
      return XPathStreamability.applyGeneralRules (aOps);
    }
    if (aSorts.isNotEmpty ())
      return _reject (aEl, "xsl:apply-templates over streamed nodes cannot sort");
    // The mode must itself be declared streamable. mode="#current" is always
    // acceptable: a template reached with a streamed node runs in a
    // streamable mode.
    final String sMode = aEl.getAttr ("mode");
    final String sModeTrimmed = sMode == null ? null : sMode.trim ();
    if (!"#current".equals (sModeTrimmed))
    {
      final XdmQName aMode;
      if (sModeTrimmed == null || "#default".equals (sModeTrimmed))
        aMode = StylesheetCompiler.getDefaultMode (aEl);
      else
        if ("#unnamed".equals (sModeTrimmed))
          aMode = Mode.UNNAMED;
        else
          aMode = aEl.resolveQName (sModeTrimmed, false);
      if (aMode == null || !m_aStreamableModes.contains (aMode))
        return _reject (aEl,
                        "xsl:apply-templates dispatches into the mode " +
                             (aMode == null ? sModeTrimmed : _getModeName (aMode)) +
                             ", which is not declared streamable");
    }
    if (aSel.posture () == EPosture.CLIMBING || aSel.posture () == EPosture.CRAWLING)
    {
      // Fourth rule: a climbing or crawling selection
      return _rejectRec (aCtx,
                         aEl,
                         "the select expression of xsl:apply-templates is " +
                               aSel.posture ().getName () +
                               ", which cannot be dispatched in a single pass");
    }
    return XPathStreamability.applyGeneralRules (aOps);
  }

  /** Section 19.8.4.20: xsl:fork. */
  @NonNull
  private PostureSweep _fork (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <XslElement> aGroups = aEl.getXslChildren ("for-each-group");
    if (aGroups.isNotEmpty ())
    {
      m_bChildOfFork = true;
      try
      {
        return _instr (aGroups.getFirst (), aCtx);
      }
      finally
      {
        m_bChildOfFork = false;
      }
    }
    ESweep eSweep = ESweep.MOTIONLESS;
    for (final XslElement aBranch : aEl.getXslChildren ("sequence"))
    {
      final PostureSweep aPS = _instr (aBranch, aCtx);
      if (aPS.posture () != EPosture.GROUNDED)
      {
        // Third rule ("xsl:fork has to assemble its results in the correct
        // order, and streamed nodes cannot be re-ordered"). Not reported as
        // definite: si-fork-006 expects such a fork to run, as go-xslt measured
        return _reject (aEl, "a branch of xsl:fork returns streamed nodes, which cannot be reordered");
      }
      if (aPS.sweep () == ESweep.FREE_RANGING)
        return _reject (aEl, "a branch of xsl:fork is not streamable");
      eSweep = eSweep.getWider (aPS.sweep ());
    }
    return new PostureSweep (EPosture.GROUNDED, eSweep);
  }

  /** Section 19.8.4.23: xsl:map. */
  @NonNull
  private PostureSweep _map (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <XslNode> aContent = _getContent (aEl);
    final ICommonsList <XslElement> aEntries = new CommonsArrayList <> ();
    boolean bAllEntries = true;
    for (final XslNode aNode : aContent)
      if (aNode instanceof final XslElement aChild)
      {
        if (aChild.isXsl ("map-entry"))
          aEntries.add (aChild);
        else
          if (!aChild.isXsl ("fallback"))
            bAllEntries = false;
      }
      else
        if (!((XslText) aNode).isWhitespaceOnly ())
          bAllEntries = false;
    if (!bAllEntries || aEntries.isEmpty ())
      return _body (aContent, aCtx);
    ESweep eSweep = ESweep.MOTIONLESS;
    for (final XslElement aEntry : aEntries)
    {
      final PostureSweep aPS = _instr (aEntry, aCtx);
      if (!aPS.isStreamable ())
      {
        // First rule for content made only of xsl:map-entry children
        return _rejectRec (aCtx, aEntry, "an xsl:map-entry is not streamable");
      }
      eSweep = eSweep.getWider (aPS.sweep ());
    }
    return new PostureSweep (EPosture.GROUNDED, eSweep);
  }

  /**
   * Section 19.8.4.25: xsl:merge does not touch the containing stream,
   * provided every merge source selects grounded, motionless input.
   */
  @NonNull
  private PostureSweep _merge (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    final StreamContext aGroundedCtx = aCtx.getClone ().setContextPosture (EPosture.GROUNDED).setContextDocument (false);
    for (final XslElement aSource : aEl.getXslChildren ("merge-source"))
    {
      // With for-each-item or for-each-source the select expression is
      // evaluated against that input, never against the enclosing stream
      final boolean bOwnInput = aSource.hasAttr ("for-each-item") || aSource.hasAttr ("for-each-source");
      _addExpr (aOps, aSource, "select", bOwnInput ? aGroundedCtx : aCtx, NAVIGATION);
      _addExpr (aOps, aSource, "for-each-item", aCtx, NAVIGATION);
      _addExpr (aOps, aSource, "for-each-source", aCtx, NAVIGATION);
      for (final XslElement aKey : aSource.getXslChildren ("merge-key"))
      {
        for (final String sAttr : MERGE_KEY_AVTS)
          _addAvt (aOps, aKey, sAttr, aCtx, ABSORPTION);
        _addExpr (aOps, aKey, "select", aGroundedCtx, ABSORPTION);
        _addBody (aOps, _getContent (aKey), aGroundedCtx, ABSORPTION);
      }
    }
    for (final StreamOperand aOp : aOps)
      if (aOp.posture () != EPosture.GROUNDED || aOp.sweep () != ESweep.MOTIONLESS)
        return _reject (aEl, "the selection of an xsl:merge-source is not grounded and motionless");
    // The merge action runs over grounded merge groups, but nothing in it may
    // reach back into the enclosing stream
    for (final XslElement aAction : aEl.getXslChildren ("merge-action"))
      if (!_body (_getContent (aAction), aGroundedCtx).isStreamable ())
        return _reject (aEl, "the body of xsl:merge-action is not streamable");
    return GM;
  }

  /**
   * @return <code>true</code> if an expression in the subtree of the element
   *         calls <code>current-group()</code> or
   *         <code>current-merge-group()</code>.
   */
  private boolean _mentionsGroupFunction (@NonNull final XslElement aEl)
  {
    for (final XslElement aCur : _getSubtree (aEl))
      for (final IExpr aExpr : m_aIndex.getAllExpressions (aCur))
        if (XPathStreamability.usesFunction (aExpr, "current-group") ||
            XPathStreamability.usesFunction (aExpr, "current-merge-group"))
          return true;
    return false;
  }

  /**
   * Section 19.8.4.35: xsl:source-document opens its own stream, so with
   * respect to the enclosing one it is grounded with the sweep of its href.
   * The exception is a call to a grouping function whose owning instruction is
   * outside; any such call is rejected, the conservative form of that test.
   */
  @NonNull
  private PostureSweep _sourceDocument (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    // A grounded population is already materialised, so reaching its current
    // group from inside crosses no boundary that matters
    final boolean bGroundedGroup = aCtx.getGroupPosture () == EPosture.GROUNDED;
    if (!bGroundedGroup && _mentionsGroupFunction (aEl))
      return _reject (aEl,
                      "xsl:source-document contains a call to a grouping function, which cannot be assessed across a stream boundary");
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addAvt (aOps, aEl, "href", aCtx, ABSORPTION);
    return XPathStreamability.applyGeneralRules (aOps);
  }

  /** Section 19.8.4.30: xsl:number. The node being numbered navigates. */
  @NonNull
  private PostureSweep _number (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "value", aCtx, ABSORPTION);
    if (aEl.hasAttr ("select"))
      _addExpr (aOps, aEl, "select", aCtx, NAVIGATION);
    else
      if (!aEl.hasAttr ("value"))
      {
        // Without value and select the context item is numbered
        aOps.add (new StreamOperand (aCtx.getContextPosture (), ESweep.MOTIONLESS, NAVIGATION));
      }
    for (final String sAttr : NUMBER_AVTS)
      _addAvt (aOps, aEl, sAttr, aCtx, ABSORPTION);
    _addPattern (aOps, aEl, "count", aCtx, INSPECTION, true);
    _addPattern (aOps, aEl, "from", aCtx, INSPECTION, true);
    return _general (aEl, aOps, aCtx);
  }

  /**
   * Section 19.8.4.12: xsl:copy. The node itself is only inspected, and the
   * content is assessed with the selected node as its focus. With a select
   * attribute the content is a higher-order operand (the Note of that
   * section).
   */
  @NonNull
  private PostureSweep _copy (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final IExpr aSelect = _getExpr (aEl, "select");
    final boolean bSelect = aEl.hasAttr ("select");
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "select", aCtx, INSPECTION);
    final StreamContext aContentCtx = aCtx.getClone ()
                                          .setContextPosture (bSelect ? XPathStreamability.getStreamability (aSelect,
                                                                                                             aCtx)
                                                                                          .posture ()
                                                                      : aCtx.getContextPosture ())
                                          .setContextDocument (false)
                                          .setContextChildless (bSelect ? XPathStreamability.isChildless (aSelect,
                                                                                                          aCtx)
                                                                        : aCtx.isContextChildless ());
    _addBody (aOps, _getContent (aEl), aContentCtx, ABSORPTION, bSelect, 0);
    return _general (aEl, aOps, _getUseAttributeSets (aEl, false), aCtx);
  }

  /**
   * Section 19.8.4.31: xsl:perform-sort. The population navigates, because
   * sorting does not preserve document order.
   */
  @NonNull
  private PostureSweep _performSort (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final IExpr aSelect = _getExpr (aEl, "select");
    final boolean bSelect = aEl.hasAttr ("select");
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "select", aCtx, NAVIGATION);
    _addBody (aOps, _getContent (aEl), aCtx, NAVIGATION);
    final StreamContext aSortCtx = aCtx.getClone ()
                                       .setContextPosture (bSelect ? XPathStreamability.getStreamability (aSelect, aCtx)
                                                                                       .posture ()
                                                                   : aCtx.getContextPosture ())
                                       .setContextDocument (false)
                                       .setContextChildless (bSelect ? XPathStreamability.isChildless (aSelect, aCtx)
                                                                     : aCtx.isContextChildless ());
    aOps.addAll (_sorts (aEl.getXslChildren ("sort"), aSortCtx));
    return _general (aEl, aOps, aCtx);
  }

  /** Section 19.8.4.10: xsl:choose. The branches form one choice operand group. */
  @NonNull
  private PostureSweep _choose (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    for (final XslElement aChild : aEl.getElementChildren ())
      if (aChild.isXsl ("when"))
      {
        _addExpr (aOps, aChild, "test", aCtx, INSPECTION);
        _addBody (aOps, _getContent (aChild), aCtx, TRANSMISSION, false, 1);
      }
      else
        if (aChild.isXsl ("otherwise"))
          _addBody (aOps, _getContent (aChild), aCtx, TRANSMISSION, false, 1);
    return _general (aEl, aOps, aCtx);
  }

  /**
   * Section 19.8.4.37: xsl:try. The try body and the catch bodies form a
   * choice operand group.
   */
  @NonNull
  private PostureSweep _try (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "select", aCtx, TRANSMISSION);
    final ICommonsList <XslNode> aBody = new CommonsArrayList <> ();
    for (final XslNode aNode : _getContent (aEl))
      if (!(aNode instanceof final XslElement aChild && aChild.isXsl ("catch")))
        aBody.add (aNode);
    _addBody (aOps, aBody, aCtx, TRANSMISSION);
    for (final XslElement aCatch : aEl.getXslChildren ("catch"))
    {
      _addExpr (aOps, aCatch, "select", aCtx, TRANSMISSION, false, 1, false);
      _addBody (aOps, _getContent (aCatch), aCtx, TRANSMISSION, false, 1);
    }
    return _general (aEl, aOps, aCtx);
  }

  /**
   * Section 19.8.4.3: xsl:analyze-string. The branches navigate (they run more
   * than once) over a grounded focus: their context item is a string.
   */
  @NonNull
  private PostureSweep _analyzeString (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
    _addAvt (aOps, aEl, "regex", aCtx, ABSORPTION);
    _addAvt (aOps, aEl, "flags", aCtx, ABSORPTION);
    final StreamContext aGroundedCtx = aCtx.getClone ().setContextPosture (EPosture.GROUNDED).setContextDocument (false);
    for (final XslElement aChild : aEl.getElementChildren ())
      if (aChild.isXsl ("matching-substring") || aChild.isXsl ("non-matching-substring"))
        _addBody (aOps, _getContent (aChild), aGroundedCtx, NAVIGATION);
    return _general (aEl, aOps, aCtx);
  }

  /** Section 19.8.4.16: xsl:evaluate. */
  @NonNull
  private PostureSweep _evaluate (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
    _addExpr (aOps, aEl, "xpath", aCtx, ABSORPTION);
    _addExpr (aOps, aEl, "context-item", aCtx, NAVIGATION);
    _addExpr (aOps, aEl, "with-params", aCtx, NAVIGATION);
    _addExpr (aOps, aEl, "namespace-context", aCtx, INSPECTION);
    _addAvt (aOps, aEl, "base-uri", aCtx, ABSORPTION);
    _addAvt (aOps, aEl, "schema-aware", aCtx, ABSORPTION);
    aOps.addAll (_params (aEl.getXslChildren ("with-param"), aCtx));
    return _general (aEl, aOps, aCtx);
  }

  /**
   * Classify one instruction. Every instruction the walk does not know is
   * roaming and free-ranging.
   */
  @NonNull
  private PostureSweep _instr (@NonNull final XslElement aEl, @NonNull final StreamContext aCtx)
  {
    if (m_nDepth >= MAX_DEPTH)
      return _reject (aEl, "the instructions are nested too deeply for the analysis");
    m_nDepth++;
    try
    {
      if (!aEl.isXsl ())
      {
        if (CoreInstructions.getExtensionNamespaces (aEl).contains (aEl.getName ().namespaceUri ()))
          return _fallback (aEl, aCtx);
        return _literalResultElement (aEl, aCtx);
      }
      final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
      switch (aEl.getLocalName ())
      {
        case "variable", "param":
          return _localVariable (aEl, aCtx);
        case "value-of":
          // Section 19.8.4.38
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "separator", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "apply-templates":
          return _applyTemplates (aEl, aCtx);
        case "for-each":
          return _forEach (aEl, aCtx);
        case "if":
          // Section 19.8.4.21
          _addExpr (aOps, aEl, "test", aCtx, INSPECTION);
          _addBody (aOps, _getContent (aEl), aCtx, TRANSMISSION);
          return _general (aEl, aOps, aCtx);
        case "choose":
          return _choose (aEl, aCtx);
        case "call-template":
          // Section 19.8.4.9: the context item is passed implicitly, with the
          // usage of item()*
          aOps.add (new StreamOperand (aCtx.getContextPosture (), ESweep.MOTIONLESS, NAVIGATION));
          aOps.addAll (_params (aEl.getXslChildren ("with-param"), aCtx));
          return _general (aEl, aOps, aCtx);
        case "attribute":
          // Section 19.8.4.7
          _addAvt (aOps, aEl, "name", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "namespace", aCtx, ABSORPTION);
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "separator", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "element":
          // Section 19.8.4.15
          _addAvt (aOps, aEl, "name", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "namespace", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, _getUseAttributeSets (aEl, false), aCtx);
        case "text":
          // Section 19.8.4.36; a text value template absorbs
          if (CompileContext.isExpandText (aEl))
          {
            final StringBuilder aSB = new StringBuilder ();
            for (final XslNode aNode : aEl.getChildren ())
              if (aNode instanceof final XslText aText)
                aSB.append (aText.getValue ());
            _addAvtText (aOps, aEl, aSB.toString (), aCtx, ABSORPTION);
          }
          return _general (aEl, aOps, aCtx);
        case "copy":
          return _copy (aEl, aCtx);
        case "copy-of":
          // Section 19.8.4.13
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "comment":
          // Section 19.8.4.11
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "processing-instruction":
          // Section 19.8.4.32
          _addAvt (aOps, aEl, "name", aCtx, ABSORPTION);
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "namespace":
          // Section 19.8.4.27
          _addAvt (aOps, aEl, "name", aCtx, ABSORPTION);
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "sequence", "on-empty", "on-non-empty", "break":
          // Sections 19.8.4.34 and 19.8.4.8: the instructions that transmit
          // streamed nodes; xsl:on-empty and xsl:on-non-empty are ordinary
          // transmission operands of their sequence constructor (19.8.3)
          _addExpr (aOps, aEl, "select", aCtx, TRANSMISSION);
          _addBody (aOps, _getContent (aEl), aCtx, TRANSMISSION);
          return _general (aEl, aOps, aCtx);
        case "where-populated":
          _addBody (aOps, _getContent (aEl), aCtx, TRANSMISSION);
          return _general (aEl, aOps, aCtx);
        case "document":
          // Section 19.8.4.14
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "analyze-string":
          return _analyzeString (aEl, aCtx);
        case "next-match", "apply-imports":
          // Sections 19.8.4.4 and 19.8.4.29: an implicit context item operand
          // with usage absorption
          aOps.add (new StreamOperand (aCtx.getContextPosture (), ESweep.MOTIONLESS, ABSORPTION));
          aOps.addAll (_params (aEl.getXslChildren ("with-param"), aCtx));
          return _general (aEl, aOps, aCtx);
        case "next-iteration":
          // Section 19.8.4.28
          aOps.addAll (_params (aEl.getXslChildren ("with-param"), aCtx));
          return _general (aEl, aOps, aCtx);
        case "for-each-group":
          return _forEachGroup (aEl, aCtx);
        case "evaluate":
          return _evaluate (aEl, aCtx);
        case "iterate":
          return _iterate (aEl, aCtx);
        case "fork":
          return _fork (aEl, aCtx);
        case "merge":
          return _merge (aEl, aCtx);
        case "perform-sort":
          return _performSort (aEl, aCtx);
        case "number":
          return _number (aEl, aCtx);
        case "result-document":
          // Section 19.8.4.33
          for (final String sAttr : RESULT_DOCUMENT_AVTS)
            _addAvt (aOps, aEl, sAttr, aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "map":
          return _map (aEl, aCtx);
        case "map-entry":
          // Section 19.8.4.24: the value navigates, so a map cannot hold
          // streamed nodes
          _addExpr (aOps, aEl, "key", aCtx, ABSORPTION);
          _addExpr (aOps, aEl, "select", aCtx, NAVIGATION);
          _addBody (aOps, _getContent (aEl), aCtx, NAVIGATION);
          return _general (aEl, aOps, aCtx);
        case "message":
          // Section 19.8.4.26
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "terminate", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "error-code", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "assert":
          // Section 19.8.4.6
          _addExpr (aOps, aEl, "test", aCtx, INSPECTION);
          _addExpr (aOps, aEl, "select", aCtx, ABSORPTION);
          _addAvt (aOps, aEl, "error-code", aCtx, ABSORPTION);
          _addBody (aOps, _getContent (aEl), aCtx, ABSORPTION);
          return _general (aEl, aOps, aCtx);
        case "source-document":
          return _sourceDocument (aEl, aCtx);
        case "try":
          return _try (aEl, aCtx);
        default:
          // An unknown instruction in forwards compatible mode runs its
          // xsl:fallback content (section 19.8.4.2)
          if (!XsltElementSpecs.isKnown (aEl.getLocalName ()) && aEl.getXslChildren ("fallback").isNotEmpty ())
            return _fallback (aEl, aCtx);
          return _reject (aEl, _what (aEl) + " has no streamability rule");
      }
    }
    finally
    {
      m_nDepth--;
    }
  }

  /**
   * Classify a sequence constructor declared streamable. The verdict carries
   * the XTSE3430 error naming the construct that defeated the analysis.
   */
  @NonNull
  private Verdict _classify (@NonNull final List <XslNode> aChildren,
                             @NonNull final XslNode aLocation,
                             @NonNull final StreamContext aCtx,
                             @NonNull final String sWhat)
  {
    aCtx.setFunctionResolver (m_aResolver);
    final PostureSweep aPS = _body (aChildren, aCtx);
    if (aPS.isStreamable () && m_aFailure == null)
      return new Verdict (aPS, null);
    XslNode aAt = aLocation;
    String sWhy = "it is " + aPS.posture ().getName () + " and " + aPS.sweep ().getName ();
    if (m_aFailure != null)
    {
      sWhy = m_aFailure.reason ();
      if (m_aFailure.location () != null)
        aAt = m_aFailure.location ();
    }
    return new Verdict (aPS,
                        XsltException.at (aAt,
                                          "XTSE3430",
                                          sWhat + " is declared streamable but is not guaranteed streamable: " + sWhy));
  }

  /** Section 19.8.5: a zero-arity function may only be unclassified (XTSE3155). */
  private void _checkFunctionSignatures ()
  {
    for (final UserFunction aFunc : m_aFunctions.values ())
    {
      final EStreamFunctionCategory eCategory = _getFunctionCategory (aFunc);
      if (eCategory != EStreamFunctionCategory.UNCLASSIFIED && aFunc.getParams ().isEmpty ())
        throw XsltException.at (aFunc.getElement (),
                                "XTSE3155",
                                "The function " +
                                                     aFunc.getName ().getEQName () +
                                                     " has no parameters, so its streamability must be unclassified, not " +
                                                     _getCategoryName (eCategory));
    }
  }

  /**
   * Every template rule of a mode declared streamable: a motionless match
   * pattern (section 19.8.10), a guaranteed-streamable body assessed with a
   * striding context posture, and a grounded type-adjusted result (section
   * 6.7.1).
   */
  private void _checkModeRules ()
  {
    if (m_aStreamableModes.isEmpty ())
      return;
    for (final Template aTemplate : m_aSheet.templates ())
    {
      if (aTemplate.getPattern () == null)
        continue;
      XdmQName aMode = null;
      if (aTemplate.isAllModes ())
        aMode = m_aStreamableModes.iterator ().next ();
      else
        for (final XdmQName aCur : aTemplate.getModes ())
          if (m_aStreamableModes.contains (aCur))
          {
            aMode = aCur;
            break;
          }
      if (aMode == null)
        continue;
      final XslElement aEl = aTemplate.getElement ();
      final String sWhat = "the template rule for the streamable mode " + _getModeName (aMode);
      final XsltPattern aPattern = aTemplate.getPattern ().pattern ();
      if (!XPathStreamability.getPatternStreamability (aPattern, new StreamContext (EPosture.STRIDING))
                             .isStreamable ())
        throw XsltException.at (aEl, "XTSE3430", sWhat + " has a match pattern that is not motionless: " + aPattern);
      // Section 19.3: the context item type of the body is the match type of
      // the pattern, which section 19.8.1 needs to downgrade absorption to
      // inspection
      final IExpr aPatternExpr = aPattern instanceof final CompiledXsltPattern aCompiled ? aCompiled.getPatternExpr ()
                                                                                        : null;
      final boolean bChildless = XPathStreamability.isPatternChildless (aPatternExpr);
      final String sSource = aPattern instanceof final CompiledXsltPattern aCompiled ? aCompiled.getSource ().trim ()
                                                                                    : "";
      final StreamContext aCtx = new StreamContext (EPosture.STRIDING).setContextDocument ("/".equals (sSource) ||
                                                                                           "document-node()".equals (sSource))
                                                                      .setContextChildless (bChildless)
                                                                      .setCurrentChildless (bChildless);
      _reset ();
      final Verdict aVerdict = _classify (_getContent (aEl), aEl, aCtx, sWhat);
      if (!aVerdict.isStreamable ())
        throw aVerdict.error ();
      // Section 6.7.1: the type-adjusted posture of the body, with respect to
      // the declared result type, must be grounded - a template rule cannot
      // hand streamed nodes back to its caller
      final PostureSweep aAdjusted = XPathStreamability.applyGeneralRules (new CommonsArrayList <> (StreamOperand.of (aVerdict.result (),
                                                                                                                      getAdjustUsage (aTemplate.getAs ()))));
      if (aAdjusted.posture () != EPosture.GROUNDED)
        throw XsltException.at (aEl,
                                "XTSE3430",
                                sWhat +
                                            " is declared streamable but returns streamed nodes: its body is " +
                                            aAdjusted.posture ().getName ());
    }
  }

  /**
   * Section 19.8.5: the body of every declared-streamable stylesheet function.
   * The category fixes what posture and sweep the type-adjusted result may
   * have. A stylesheet function has no focus, so the body is assessed with a
   * roaming context posture; the first parameter is the streaming parameter.
   */
  private void _checkFunctions ()
  {
    for (final UserFunction aFunc : m_aFunctions.values ())
    {
      final XslElement aEl = aFunc.getElement ();
      final EStreamFunctionCategory eCategory = _getFunctionCategory (aFunc);
      if (eCategory == EStreamFunctionCategory.UNCLASSIFIED || aFunc.getParams ().isEmpty ())
        continue;
      final String sWhat = "the function " + aFunc.getName ().getEQName () + " with streamability " + _getCategoryName (eCategory);
      final VariableDef aFirst = aFunc.getParams ().getFirst ();
      final SeqType aFirstType = aFirst.getAs ();
      if (_isSingleNodeCategory (eCategory) && isPermittingManyNodes (aFirstType))
        throw XsltException.at (aEl,
                                "XTSE3430",
                                sWhat +
                                            " is not guaranteed streamable: its streaming parameter $" +
                                            aFirst.getName ().getEQName () +
                                            " permits more than one node");
      final StreamContext aCtx = new StreamContext (EPosture.ROAMING);
      // Section 19.8.8.12: a reference to a parameter whose type cannot hold a
      // node is grounded, the ordinary rule
      if (isPermittingNodes (aFirstType))
      {
        aCtx.addStreamingParam (aFirst.getName ());
        // The "Rules for references to the streaming parameter" of section
        // 19.8.5: striding everywhere, climbing in an ascent function, and
        // consuming in an absorbing function whose parameter permits more
        // than one node
        aCtx.setStreamingParamPosture (eCategory == EStreamFunctionCategory.ASCENT ? EPosture.CLIMBING
                                                                                   : EPosture.STRIDING);
        aCtx.setStreamingParamSweep (eCategory == EStreamFunctionCategory.ABSORBING &&
                                     isPermittingManyNodes (aFirstType) ? ESweep.CONSUMING : ESweep.MOTIONLESS);
        // Retained conservatism of go-xslt, not a rule of the Recommendation:
        // the "singular" test of the 2015 draft, which only ever rejects more
        aCtx.setStreamingParamRepeatable (eCategory == EStreamFunctionCategory.INSPECTION ||
                                          eCategory == EStreamFunctionCategory.FILTER ||
                                          eCategory == EStreamFunctionCategory.ASCENT);
      }
      _reset ();
      aCtx.setFunctionResolver (m_aResolver);
      final PostureSweep aBody = _body (_getContent (aEl), aCtx);
      final PostureSweep aResult = XPathStreamability.applyGeneralRules (new CommonsArrayList <> (StreamOperand.of (aBody,
                                                                                                                    getAdjustUsage (aFunc.getAs ()))));
      if (_isFunctionResultAllowed (eCategory, aResult) && m_aFailure == null)
        continue;
      XslNode aAt = aEl;
      String sWhy = "its result is " + aResult.posture ().getName () + " and " + aResult.sweep ().getName ();
      if (m_aFailure != null)
      {
        sWhy = m_aFailure.reason ();
        if (m_aFailure.location () != null)
          aAt = m_aFailure.location ();
      }
      throw XsltException.at (aAt, "XTSE3430", sWhat + " is not guaranteed streamable: " + sWhy);
    }
  }

  /**
   * @return All top-level declarations of all modules, the children of
   *         xsl:override included.
   */
  @NonNull
  private ICommonsList <XslElement> _getDeclarations ()
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    for (final XslModule aModule : m_aSheet.modules ())
      if (aModule.getRoot () != null)
        for (final XslElement aDecl : aModule.getRoot ().getElementChildren ())
          if (aDecl.isXsl ("use-package"))
          {
            for (final XslElement aOverride : aDecl.getXslChildren ("override"))
              ret.addAll (aOverride.getElementChildren ());
          }
          else
            ret.add (aDecl);
    return ret;
  }

  /**
   * Section 18.2.8: every accumulator declared streamable needs motionless
   * match patterns, and a grounded and motionless initial value and rule
   * values.
   */
  private void _checkAccumulators ()
  {
    for (final XslElement aEl : _getDeclarations ())
    {
      if (!aEl.isXsl ("accumulator") || !XsltNames.isXsltTrue (aEl.getAttr ("streamable")))
        continue;
      final String sWhat = "the accumulator " + aEl.getAttr ("name");
      _reset ();
      // The initial value has no context item at all
      final IExpr aInitial = _getExpr (aEl, "initial-value");
      final PostureSweep aInit = aInitial == null ? RF
                                                  : XPathStreamability.getStreamability (aInitial,
                                                                                         new StreamContext (EPosture.ROAMING).setFunctionResolver (m_aResolver));
      if (aInit.posture () != EPosture.GROUNDED || aInit.sweep () != ESweep.MOTIONLESS)
        throw XsltException.at (aEl,
                                "XTSE3430",
                                sWhat +
                                            " is declared streamable but its initial value is " +
                                            aInit.posture ().getName () +
                                            " and " +
                                            aInit.sweep ().getName ());
      final SeqType aAs = _getAs (aEl);
      for (final XslElement aRule : aEl.getXslChildren ("accumulator-rule"))
      {
        final XsltPattern aMatch = _getPattern (aRule, "match");
        if (aMatch == null ||
            !XPathStreamability.getPatternStreamability (aMatch, new StreamContext (EPosture.STRIDING)).isStreamable ())
          throw XsltException.at (aRule,
                                  "XTSE3430",
                                  sWhat +
                                              " is declared streamable but the match pattern " +
                                              aRule.getAttr ("match") +
                                              " of one of its rules is not motionless");
        // The value is evaluated with the matched node as its focus, so a
        // pattern that can only match childless nodes keeps string(.)
        // motionless; fn:current() is the same node
        final boolean bChildless = aMatch instanceof final CompiledXsltPattern aCompiled &&
                                   XPathStreamability.isPatternChildless (aCompiled.getPatternExpr ());
        final String sPhase = aRule.getAttr ("phase");
        final EAccumulatorRulePhase ePhase = sPhase != null && "end".equals (sPhase.trim ()) ? EAccumulatorRulePhase.END
                                                                                             : EAccumulatorRulePhase.START;
        final StreamContext aCtx = new StreamContext (EPosture.STRIDING).setContextChildless (bChildless)
                                                                        .setCurrentChildless (bChildless)
                                                                        .setAccumulatorPhase (ePhase)
                                                                        .setFunctionResolver (m_aResolver);
        // $value holds the value so far, of the declared type of the
        // accumulator (accumulator-053)
        if (isMapOrArray (aAs))
          aCtx.addMapArrayVariable (VAR_VALUE);
        final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
        if (aRule.hasAttr ("select"))
          _addExpr (aOps, aRule, "select", aCtx, getAdjustUsage (aAs));
        else
          aOps.add (StreamOperand.of (_body (_getContent (aRule), aCtx), getAdjustUsage (aAs)));
        // The declared type applies the function conversion rules to the rule
        // result, so the value held is the type-adjusted one
        final PostureSweep aPS = XPathStreamability.applyGeneralRules (aOps);
        if (aPS.posture () != EPosture.GROUNDED || aPS.sweep () != ESweep.MOTIONLESS || m_aFailure != null)
          throw XsltException.at (aRule,
                                  "XTSE3430",
                                  sWhat +
                                              " is declared streamable but one of its rules computes a value that is " +
                                              aPS.posture ().getName () +
                                              " and " +
                                              aPS.sweep ().getName ());
      }
    }
  }

  /**
   * @return Every element of every module, in document order, collected
   *         iteratively.
   */
  @NonNull
  private ICommonsList <XslElement> _getAllElements ()
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    for (final XslModule aModule : m_aSheet.modules ())
      if (aModule.getRoot () != null)
        ret.addAll (_getSubtree (aModule.getRoot ()));
    return ret;
  }

  /**
   * Section 15.4: an xsl:merge-source declared streamable needs a
   * for-each-source attribute, no sort-before-merge, and a select expression
   * that, assessed with a striding document node as context item, is striding
   * or grounded and motionless or consuming. Each condition reads a written
   * attribute or the posture of one expression, so each is reported directly.
   */
  private void _checkMergeSources (@NonNull final List <XslElement> aAll)
  {
    for (final XslElement aEl : aAll)
    {
      if (!aEl.isXsl ("merge-source") || !XsltNames.isXsltTrue (aEl.getAttr ("streamable")))
        continue;
      final String sSelect = aEl.getAttr ("select");
      final IExpr aSelect = sSelect == null ? null : m_aIndex.getExpression (aEl, sSelect);
      // Not compiled (inside an xsl:fallback that is never used)
      if (aSelect == null)
        continue;
      if (!aEl.hasAttr ("for-each-source"))
        throw XsltException.at (aEl, "XTSE3430", "xsl:merge-source is declared streamable but has no for-each-source attribute");
      if (XsltNames.isXsltTrue (aEl.getAttr ("sort-before-merge")))
        throw XsltException.at (aEl, "XTSE3430", "xsl:merge-source is declared streamable but specifies sort-before-merge='yes'");
      final PostureSweep aPS = XPathStreamability.getStreamability (aSelect,
                                                                    new StreamContext (EPosture.STRIDING).setContextDocument (true));
      if ((aPS.posture () != EPosture.STRIDING && aPS.posture () != EPosture.GROUNDED) ||
          (aPS.sweep () != ESweep.MOTIONLESS && aPS.sweep () != ESweep.CONSUMING))
        throw XsltException.at (aEl,
                                "XTSE3430",
                                "xsl:merge-source is declared streamable but its select expression is " +
                                            aPS.posture ().getName () +
                                            " and " +
                                            aPS.sweep ().getName () +
                                            ", not striding or grounded with a motionless or consuming sweep");
    }
  }

  /**
   * @return <code>true</code> if an expression in the subtree of the element
   *         calls a declared-streamable stylesheet function.
   */
  private boolean _callsDeclaredStreamable (@NonNull final XslElement aEl)
  {
    for (final XslElement aCur : _getSubtree (aEl))
      for (final IExpr aExpr : m_aIndex.getAllExpressions (aCur))
        if (XPathStreamability.usesDeclaredStreamableFunction (aExpr, m_aResolver))
          return true;
    return false;
  }

  /**
   * Section 18.1: an xsl:source-document declared streamable needs a grounded
   * sequence constructor. Every such body is classified, but only some
   * verdicts are reported (XTSE3430 permits processing without streaming "if
   * the user has indicated that the processor is to handle this situation"):
   * a body that classifies and is not grounded, a verdict of a rule the
   * Recommendation states unconditionally, and a body calling a
   * declared-streamable function, whose rules are implemented completely.
   * Anything else is this analysis being incomplete; go-xslt measured that
   * reporting it loses far more cases than it wins.
   */
  private void _checkSourceDocuments (@NonNull final List <XslElement> aAll,
                                      @NonNull final StreamVerdicts aVerdicts,
                                      final boolean bEnforce)
  {
    for (final XslElement aEl : aAll)
    {
      if (!aEl.isXsl ("source-document") || !XsltNames.isXsltTrue (aEl.getAttr ("streamable")))
        continue;
      // Not compiled (inside an xsl:fallback that is never used)
      final String sHref = aEl.getAttr ("href");
      if (sHref == null)
        continue;
      // Inside xsl:for-each-group or xsl:merge the body may legitimately
      // reach the current group, which a fresh context cannot express; the
      // analysis of the enclosing construct covers it
      if (_isInsideGroupingElement (aEl))
        continue;
      final MutableBoolean aDefinite = new MutableBoolean (false);
      final StreamContext aCtx = new StreamContext (EPosture.STRIDING).setContextDocument (true)
                                                                      .setDefiniteFlag (aDefinite);
      _reset ();
      final Verdict aVerdict = _classify (_getContent (aEl), aEl, aCtx, "xsl:source-document");
      if (aVerdict.isStreamable ())
      {
        if (aVerdict.result ().posture () != EPosture.GROUNDED)
        {
          if (bEnforce)
            throw XsltException.at (aEl,
                                    "XTSE3430",
                                    "xsl:source-document is declared streamable but is not guaranteed streamable: its sequence constructor is " +
                                                aVerdict.result ().posture ().getName () +
                                                " rather than grounded, so it returns nodes of the streamed document");
        }
        else
          aVerdicts.addSourceDocument (aEl);
      }
      else
        if (bEnforce && (aDefinite.booleanValue () || _callsDeclaredStreamable (aEl)))
          throw aVerdict.error ();
    }
  }

  /**
   * The verdicts streamed execution needs beyond guaranteed streamability:
   * the literal result elements around the consumer of a streamed body whose
   * attribute value templates are motionless with the document node as
   * context item (they are evaluated before any record is read), and the
   * xsl:merge instructions whose streamable merge sources can be read as
   * streams (the merge action does not ask for the number of merge groups).
   */
  private void _checkStreamedExecution (@NonNull final List <XslElement> aAll, @NonNull final StreamVerdicts aVerdicts)
  {
    for (final XslElement aEl : aAll)
    {
      final boolean bHost = (aEl.isXsl ("source-document") && XsltNames.isXsltTrue (aEl.getAttr ("streamable"))) ||
                            (aEl.isXsl ("template") && aEl.hasAttr ("match"));
      if (bHost)
        for (final XslElement aWrapper : StreamPlanner.getWrapperCandidates (aEl))
        {
          if (!StreamPlanner.hasAvt (aWrapper))
            continue;
          final StreamContext aCtx = new StreamContext (EPosture.STRIDING).setContextDocument (true)
                                                                          .setFunctionResolver (m_aResolver);
          final ICommonsList <StreamOperand> aOps = new CommonsArrayList <> ();
          _reset ();
          for (final Map.Entry <XdmQName, String> aEntry : aWrapper.getAllAttributes ().entrySet ())
            if (!XsltNames.NS_XSL.equals (aEntry.getKey ().namespaceUri ()))
              _addAvtText (aOps, aWrapper, aEntry.getValue (), aCtx, ABSORPTION);
          final PostureSweep aPS = XPathStreamability.applyGeneralRules (aOps, aCtx);
          if (m_aFailure == null && aPS.isStreamable () && aPS.sweep () == ESweep.MOTIONLESS)
            aVerdicts.addMotionlessWrapper (aWrapper);
        }
      if (aEl.isXsl ("merge"))
      {
        boolean bStreamable = false;
        for (final XslElement aSource : aEl.getXslChildren ("merge-source"))
          if (XsltNames.isXsltTrue (aSource.getAttr ("streamable")))
            bStreamable = true;
        boolean bUsesLast = false;
        for (final XslElement aAction : aEl.getXslChildren ("merge-action"))
          for (final XslElement aCur : _getSubtree (aAction))
            for (final IExpr aExpr : m_aIndex.getAllExpressions (aCur))
              if (XPathStreamability.usesFunction (aExpr, "last"))
                bUsesLast = true;
        if (bStreamable && !bUsesLast)
          aVerdicts.addStreamedMerge (aEl);
      }
    }
  }

  /**
   * Check that everything a stylesheet declares streamable is guaranteed
   * streamable. Called by the compiler at the end of compilation when
   * streamability declarations are binding or streaming is claimed (then the
   * verdicts decide what is executed streamed).
   *
   * @param aCompiler
   *        The compiler, with its stylesheet compiled. May not be
   *        <code>null</code>.
   * @param aIndex
   *        The operands the compiler recorded. May not be <code>null</code>.
   * @param bEnforce
   *        <code>true</code> if streamability declarations are binding: a
   *        failure is then an error; else it only means that the construct is
   *        not streamed.
   * @return What was proven guaranteed streamable.
   * @throws XsltException
   *         XTSE3430 or XTSE3155, only when enforcing
   */
  @NonNull
  public static StreamVerdicts check (@NonNull final StylesheetCompiler aCompiler,
                                      @NonNull final StreamabilityIndex aIndex,
                                      final boolean bEnforce)
  {
    final XsltStreamability aChecker = new XsltStreamability (aCompiler, aIndex);
    final StreamVerdicts ret = new StreamVerdicts ();
    try
    {
      aChecker._checkFunctionSignatures ();
      aChecker._checkModeRules ();
      aChecker._checkFunctions ();
      aChecker._checkAccumulators ();
      aChecker._checkMergeSources (aChecker._getAllElements ());
    }
    catch (final XsltException ex)
    {
      if (bEnforce)
        throw ex;
      // Fail closed: the verdicts of the other constructs trust the
      // declarations
      ret.setDeclarationsFailed ();
      return ret;
    }
    for (final XdmQName aMode : aChecker.m_aStreamableModes)
      ret.addStreamableMode (aMode);
    for (final XslElement aEl : aChecker._getDeclarations ())
      if (aEl.isXsl ("accumulator") && XsltNames.isXsltTrue (aEl.getAttr ("streamable")) && aEl.hasAttr ("name"))
      {
        final XdmQName aName = aEl.resolveQName (aEl.getAttr ("name").trim (), false);
        if (aName != null)
          ret.addStreamableAccumulator (aName);
      }
    aChecker._checkSourceDocuments (aChecker._getAllElements (), ret, bEnforce);
    aChecker._checkStreamedExecution (aChecker._getAllElements (), ret);
    return ret;
  }
}
