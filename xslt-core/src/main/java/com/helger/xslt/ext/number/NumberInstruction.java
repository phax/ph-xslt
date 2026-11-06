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
package com.helger.xslt.ext.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.pattern.CompiledXsltPattern;
import com.helger.xpath.pattern.PatternFinalNames;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.pattern.XsltPatternParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmDocumentNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.ext.number.NumberFormatter.Picture;
import com.helger.xslt.instr.AbstractInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.stream.StreamabilityIndex;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:number (XSLT 3.0 section 12): either formats the integers of the value
 * attribute, or numbers a node of the source tree by its position according to
 * level (single, multiple, any) and the count and from patterns, and writes the
 * formatted result as a text node.
 *
 * @author Philip Helger
 */
@Immutable
public final class NumberInstruction extends AbstractInstruction
{
  /** The value of the level attribute (XSLT 3.0 section 12.3). */
  private enum ELevel
  {
    SINGLE,
    MULTIPLE,
    ANY
  }

  /**
   * A count or from pattern. Unlike template patterns these may refer to the
   * local variables in scope of the xsl:number element, so they are compiled
   * with the full static context and matched in the run-time context.
   *
   * @param pattern
   *        The pattern.
   * @param staticContext
   *        Its static context.
   * @param elementNames
   *        The distinct names of the elements the pattern can match (see
   *        {@link XsltPattern#getFinalNames()}), or <code>null</code> if it can
   *        match other nodes than elements and attributes of known names.
   * @param exactNames
   *        <code>true</code> if the pattern matches exactly the elements of
   *        these names that have a parent: every alternative is a single
   *        <code>child::</code> step with an exact name test and no
   *        predicate. A candidate from the name index then matches without
   *        testing it.
   */
  private record NumberPattern (@NonNull XsltPattern pattern,
                                @NonNull StaticContext staticContext,
                                @Nullable List <XdmQName> elementNames,
                                boolean exactNames)
  {}

  /**
   * The state of one evaluation of a counting xsl:number: the run-time
   * context, the selected node, one match context per pattern for all
   * candidate nodes (the dynamic context does not change while counting), the
   * document of the selected node if it is a document that is not streamed,
   * and the running count of level="any".
   */
  private static final class Counting
  {
    private final XsltContext m_aCtx;
    private final IXdmNode m_aTarget;
    private final DynamicContext m_aCountCtx;
    private final DynamicContext m_aFromCtx;
    private final XdmDocumentNode m_aDoc;
    private long m_nCount;

    /**
     * @param aCtx
     *        The run-time context of the instruction.
     * @param aTarget
     *        The selected node.
     * @param aCount
     *        The count pattern, <code>null</code> for the default.
     * @param aFrom
     *        The from pattern, <code>null</code> if absent.
     */
    Counting (@NonNull final XsltContext aCtx,
              @NonNull final IXdmNode aTarget,
              @Nullable final NumberPattern aCount,
              @Nullable final NumberPattern aFrom)
    {
      m_aCtx = aCtx;
      m_aTarget = aTarget;
      final DynamicContext aDyn = aCtx.getDynamicContext ();
      m_aCountCtx = aCount == null ? null : aDyn.withStaticContext (aCount.staticContext ());
      m_aFromCtx = aFrom == null ? null : aDyn.withStaticContext (aFrom.staticContext ());
      IXdmNode aRoot = aTarget;
      while (aRoot.getParent () != null)
        aRoot = aRoot.getParent ();
      m_aDoc = aRoot instanceof final XdmDocumentNode aDoc && !aDoc.isStreamed () ? aDoc : null;
    }
  }

  private static final String [] [] FORBIDDEN_PATTERN_FUNCTIONS = { { "current-group", "XTSE1060" },
                                                                   { "current-grouping-key", "XTSE1070" },
                                                                   { "current-merge-group", "XTSE3470" },
                                                                   { "current-merge-key", "XTSE3500" } };

  /** The start-at syntax of XSLT 3.0 section 12.1 (XML whitespace). */
  private static final Pattern START_AT = Pattern.compile ("-?[0-9]+([ \\t\\n\\r]+-?[0-9]+)*");
  /** The lexical space of xs:integer, for grouping-size. */
  private static final Pattern INTEGER = Pattern.compile ("[+-]?[0-9]+");
  /**
   * Up to this many children of a parent the preceding siblings are tested one
   * by one; beyond it they come from the name index where the count pattern
   * allows it (the index keeps a list per parent and name asked for).
   */
  private static final int SIBLING_SCAN_LIMIT = 32;
  /** See {@link #getNameIndexCountCount()}. */
  private static final AtomicLong NAME_INDEX_COUNTS = new AtomicLong (0);

  private final XPathExpr m_aValue;
  private final XPathExpr m_aSelect;
  private final NumberPattern m_aCount;
  private final NumberPattern m_aFrom;
  private final ELevel m_eLevel;
  private final Avt m_aFormat;
  private final Avt m_aGroupSep;
  private final Avt m_aGroupSize;
  private final Avt m_aOrdinal;
  private final Avt m_aStartAt;
  private final Avt m_aLang;
  // The analysed format and the parsed start-at of constant attributes, null
  // if they are computed per evaluation
  private final Picture m_aConstPicture;
  private final ICommonsList <BigInteger> m_aConstStartAt;

  private NumberInstruction (@NonNull final XslElement aEl,
                             @Nullable final XPathExpr aValue,
                             @Nullable final XPathExpr aSelect,
                             @Nullable final NumberPattern aCount,
                             @Nullable final NumberPattern aFrom,
                             @NonNull final ELevel eLevel,
                             @NonNull final Avt aFormat,
                             @Nullable final Avt aGroupSep,
                             @Nullable final Avt aGroupSize,
                             @Nullable final Avt aOrdinal,
                             @NonNull final Avt aStartAt,
                             @Nullable final Avt aLang,
                             @Nullable final Picture aConstPicture,
                             @Nullable final ICommonsList <BigInteger> aConstStartAt)
  {
    super (aEl);
    m_aValue = aValue;
    m_aSelect = aSelect;
    m_aCount = aCount;
    m_aFrom = aFrom;
    m_eLevel = eLevel;
    m_aFormat = aFormat;
    m_aGroupSep = aGroupSep;
    m_aGroupSize = aGroupSize;
    m_aOrdinal = aOrdinal;
    m_aStartAt = aStartAt;
    m_aLang = aLang;
    m_aConstPicture = aConstPicture;
    m_aConstStartAt = aConstStartAt;
  }

  /**
   * The default count pattern: same node kind and, for named nodes, the same
   * expanded name.
   */
  private static boolean _isSameKindAndName (@NonNull final IXdmNode aNode, @NonNull final IXdmNode aTarget)
  {
    if (aNode.getKind () != aTarget.getKind ())
      return false;
    switch (aNode.getKind ())
    {
      case ELEMENT:
      case ATTRIBUTE:
      case PROCESSING_INSTRUCTION:
      case NAMESPACE:
        return aNode.getName ().equals (aTarget.getName ());
      default:
        return true;
    }
  }

  /**
   * Check whether a node is on no preceding-sibling axis: a parentless node,
   * an attribute or a namespace node.
   *
   * @param aNode
   *        The node.
   * @return <code>true</code> if the node has no siblings to count.
   */
  private static boolean _hasNoSiblings (@NonNull final IXdmNode aNode)
  {
    return aNode.getParent () == null ||
           aNode.getKind () == ENodeKind.ATTRIBUTE ||
           aNode.getKind () == ENodeKind.NAMESPACE;
  }

  /**
   * Find the index of a child among the children of its parent, by identity
   * and with a linear scan.
   *
   * @param aParent
   *        The parent.
   * @param aChild
   *        The child to find.
   * @return The 0-based index, or -1 if the node is no child of the parent
   *         (e.g. an attribute).
   */
  private static int _indexInParent (@NonNull final IXdmNode aParent, @NonNull final IXdmNode aChild)
  {
    final int nCount = aParent.getChildCount ();
    for (int i = 0; i < nCount; ++i)
      if (aParent.getChildAtIndex (i) == aChild)
        return i;
    return -1;
  }

  /**
   * <code>matches-count($node)</code> of XSLT 3.0 section 12.3: the count
   * pattern if present, otherwise the default of the same node kind and
   * expanded name as the selected node.
   *
   * @param aC
   *        The counting state.
   * @param aNode
   *        The node to test.
   * @return <code>true</code> if the node is counted.
   */
  private boolean _matchesCount (@NonNull final Counting aC, @NonNull final IXdmNode aNode)
  {
    if (m_aCount == null)
      return _isSameKindAndName (aNode, aC.m_aTarget);
    return m_aCount.pattern ().matches (aNode, aC.m_aCountCtx);
  }

  /**
   * The from pattern part of <code>matches-from($node)</code> (XSLT 3.0
   * section 12.3). The spec also makes every root node match; the callers get
   * that effect by stopping their upward walk at the root.
   *
   * @param aC
   *        The counting state.
   * @param aNode
   *        The node to test.
   * @return <code>true</code> if there is a from pattern and the node matches
   *         it.
   */
  private boolean _matchesFrom (@NonNull final Counting aC, @NonNull final IXdmNode aNode)
  {
    return m_aFrom != null && m_aFrom.pattern ().matches (aNode, aC.m_aFromCtx);
  }

  /**
   * @param aC
   *        The counting state.
   * @return The names of the elements the count pattern can match in this
   *         evaluation: those of the count pattern, or for the default pattern
   *         and a selected element the name of that element.
   *         <code>null</code> if it can match other nodes.
   */
  @Nullable
  private List <XdmQName> _getCountNames (@NonNull final Counting aC)
  {
    if (m_aCount != null)
      return m_aCount.elementNames ();
    return aC.m_aTarget.getKind () == ENodeKind.ELEMENT ? List.of (aC.m_aTarget.getName ()) : null;
  }

  /**
   * @return <code>true</code> if every element of the names of
   *         {@link #_getCountNames(Counting)} that has a parent matches the
   *         count pattern, so that candidates need not be tested.
   */
  private boolean _isCountExact ()
  {
    return m_aCount == null || m_aCount.exactNames ();
  }

  /**
   * The elements of some names from the name index of a document.
   *
   * @param aDoc
   *        The document.
   * @param aNames
   *        The distinct element names.
   * @return One list per name in document order, or <code>null</code> if the
   *         document has no name index (the tree is not numbered).
   */
  @Nullable
  private static ICommonsList <List <IXdmNode>> _getElementLists (@NonNull final XdmDocumentNode aDoc,
                                                                 @NonNull final List <XdmQName> aNames)
  {
    final ICommonsList <List <IXdmNode>> ret = new CommonsArrayList <> ();
    for (final XdmQName aName : aNames)
    {
      final List <IXdmNode> aNodes = aDoc.getElementsByName (aName);
      if (aNodes == null)
        return null;
      ret.add (aNodes);
    }
    return ret;
  }

  /**
   * The number of preceding siblings of an element that match count, from the
   * name index of the document: the children of the parent with one of the
   * count names (kept by the index per parent and name) before the element in
   * document order, found by binary search. With exact names they are counted
   * without testing them, otherwise each one is tested against the count
   * pattern. A sibling of another name cannot match.
   *
   * @param aC
   *        The counting state, with a document.
   * @param aNode
   *        The element, which has a parent.
   * @return The number, or -1 if the count pattern can match other nodes than
   *         elements of known names or the tree is not numbered.
   */
  private long _countPrecedingSiblingsByName (@NonNull final Counting aC, @NonNull final IXdmNode aNode)
  {
    final List <XdmQName> aNames = _getCountNames (aC);
    if (aNames == null)
      return -1;
    final IXdmNode aParent = aNode.getParent ();
    final ICommonsList <List <IXdmNode>> aLists = new CommonsArrayList <> ();
    for (final XdmQName aName : aNames)
    {
      final List <IXdmNode> aSiblings = aC.m_aDoc.getChildElementsByName (aParent, aName);
      if (aSiblings == null)
        return -1;
      aLists.add (aSiblings);
    }
    NAME_INDEX_COUNTS.incrementAndGet ();
    final boolean bExact = _isCountExact ();
    final int nOrder = aNode.getOrder ();
    long ret = 0;
    for (final List <IXdmNode> aSiblings : aLists)
    {
      final int nEnd = XdmDocumentNode.getLowerBound (aSiblings, 0, nOrder);
      if (bExact)
        ret += nEnd;
      else
        for (int i = 0; i < nEnd; ++i)
        {
          aC.m_aCtx.getDynamicContext ().checkInterrupt ();
          if (_matchesCount (aC, aSiblings.get (i)))
            ret++;
        }
    }
    return ret;
  }

  /**
   * 1 + the number of preceding siblings matching count; 1 for a node without
   * siblings (a parentless node, an attribute or a namespace node). For an
   * element in a document whose parent has more than
   * {@link #SIBLING_SCAN_LIMIT} children the siblings come from the name index
   * if the count pattern allows it
   * ({@link #_countPrecedingSiblingsByName(Counting, IXdmNode)}); otherwise the
   * preceding siblings are tested one by one, against the names alone if the
   * count pattern consists of exact names (a sibling has a parent).
   */
  private long _siblingIndex (@NonNull final Counting aC, @NonNull final IXdmNode aNode)
  {
    if (_hasNoSiblings (aNode))
      return 1;
    final IXdmNode aParent = aNode.getParent ();
    final int nCount = aParent.getChildCount ();
    if (aC.m_aDoc != null && aNode.getKind () == ENodeKind.ELEMENT && nCount > SIBLING_SCAN_LIMIT)
    {
      final long nPreceding = _countPrecedingSiblingsByName (aC, aNode);
      if (nPreceding >= 0)
        return nPreceding + 1;
    }
    // An explicit count pattern of exact names is decided by the name
    final List <XdmQName> aExact = m_aCount != null && m_aCount.exactNames () ? m_aCount.elementNames () : null;
    long ret = 1;
    for (int i = 0; i < nCount; ++i)
    {
      final IXdmNode aSibling = aParent.getChildAtIndex (i);
      if (aSibling == aNode)
        break;
      if (aExact != null ? (aSibling.getKind () == ENodeKind.ELEMENT && aExact.contains (aSibling.getName ()))
                         : _matchesCount (aC, aSibling))
        ret++;
    }
    return ret;
  }

  /**
   * level="single" (XSLT 3.0 section 12.3): walk up the ancestor-or-self axis
   * to the innermost node matching count; if a node matching from (but not
   * count) is reached first, nothing is numbered. The number is the sibling
   * index of the counted node.
   *
   * @param aC
   *        The counting state.
   * @return The number, or 0 for the empty place marker.
   */
  private long _countSingle (@NonNull final Counting aC)
  {
    // count is tested before from: a node matching both is still numbered
    IXdmNode aCur = aC.m_aTarget;
    while (aCur != null)
    {
      if (_matchesCount (aC, aCur))
        return _siblingIndex (aC, aCur);
      if (_matchesFrom (aC, aCur))
        return 0;
      aCur = aCur.getParent ();
    }
    return 0;
  }

  /**
   * level="multiple" (XSLT 3.0 section 12.3): the ancestor-or-self nodes
   * matching count, up to and including the innermost node matching from (or
   * the root), each numbered by its sibling index, outermost first.
   *
   * @param aC
   *        The counting state.
   * @return The place marker, possibly empty.
   */
  @NonNull
  private ICommonsList <BigInteger> _countMultiple (@NonNull final Counting aC)
  {
    final ICommonsList <IXdmNode> aChain = new CommonsArrayList <> ();
    for (IXdmNode aCur = aC.m_aTarget; aCur != null; aCur = aCur.getParent ())
    {
      if (_matchesCount (aC, aCur))
        aChain.add (aCur);
      if (_matchesFrom (aC, aCur))
        break;
    }
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    for (int i = aChain.size () - 1; i >= 0; --i)
      ret.add (BigInteger.valueOf (_siblingIndex (aC, aChain.get (i))));
    return ret;
  }

  /**
   * One node of the level="any" walk: count it if it matches count.
   *
   * @param aC
   *        The counting state.
   * @param aNode
   *        The node.
   * @return <code>true</code> if it matches from, so that the walk ends.
   */
  private boolean _visitAny (@NonNull final Counting aC, @NonNull final IXdmNode aNode)
  {
    aC.m_aCtx.getDynamicContext ().checkInterrupt ();
    if (_matchesCount (aC, aNode))
      aC.m_nCount++;
    return _matchesFrom (aC, aNode);
  }

  /**
   * The last node in document order before a position that matches the from
   * pattern, among the elements of the from names: the lists are merged
   * backwards by document order, so the candidates are tested in reverse
   * document order like the walk of the whole tree does, and the first
   * matching one is the result. With exact names the first candidate is the
   * result without testing it.
   *
   * @param aC
   *        The counting state.
   * @param aLists
   *        The elements of the from names, one list per name.
   * @param nBefore
   *        The document order number the candidates must be before.
   * @return The node, or <code>null</code> if no such element matches.
   */
  @Nullable
  private IXdmNode _findLastFrom (@NonNull final Counting aC,
                                  @NonNull final ICommonsList <List <IXdmNode>> aLists,
                                  final int nBefore)
  {
    final int nLists = aLists.size ();
    final int [] aPos = new int [nLists];
    for (int i = 0; i < nLists; ++i)
      aPos[i] = XdmDocumentNode.getLowerBound (aLists.get (i), 0, nBefore) - 1;
    while (true)
    {
      int nBest = -1;
      int nBestOrder = 0;
      for (int i = 0; i < nLists; ++i)
        if (aPos[i] >= 0)
        {
          final int nOrder = aLists.get (i).get (aPos[i]).getOrder ();
          if (nBest < 0 || nOrder > nBestOrder)
          {
            nBest = i;
            nBestOrder = nOrder;
          }
        }
      if (nBest < 0)
        return null;
      final IXdmNode aNode = aLists.get (nBest).get (aPos[nBest]);
      if (m_aFrom.exactNames ())
        return aNode;
      aC.m_aCtx.getDynamicContext ().checkInterrupt ();
      if (_matchesFrom (aC, aNode))
        return aNode;
      aPos[nBest]--;
    }
  }

  /**
   * level="any" from the name index of the document, for the nodes before the
   * start node (the selected node, or the parent of a selected attribute or
   * namespace node, both already visited). If the count pattern and the from
   * pattern (if any) can match only elements of known names, the only nodes
   * that can matter are the elements of these names before the start node:
   * the last one matching from is found first (the root if there is none),
   * then the elements of the count names from that node on to the start node
   * are counted, found by binary search on the document order and, unless the
   * count names are exact, each tested against the count pattern.
   *
   * @param aC
   *        The counting state. The count is added to it.
   * @param aStart
   *        The start node.
   * @return <code>false</code> if the name index cannot be used (no document
   *         root, a streamed document, a tree that is not numbered, or a
   *         pattern that can match other nodes); nothing was counted then.
   */
  private boolean _countAnyByNames (@NonNull final Counting aC, @NonNull final IXdmNode aStart)
  {
    if (aC.m_aDoc == null)
      return false;
    final List <XdmQName> aCountNames = _getCountNames (aC);
    if (aCountNames == null || (m_aFrom != null && m_aFrom.elementNames () == null))
      return false;
    final ICommonsList <List <IXdmNode>> aCountLists = _getElementLists (aC.m_aDoc, aCountNames);
    if (aCountLists == null)
      return false;
    ICommonsList <List <IXdmNode>> aFromLists = null;
    if (m_aFrom != null)
    {
      aFromLists = _getElementLists (aC.m_aDoc, m_aFrom.elementNames ());
      if (aFromLists == null)
        return false;
    }
    NAME_INDEX_COUNTS.incrementAndGet ();
    final int nStart = aStart.getOrder ();
    // Without a matching from node the root is the one matching from
    int nFrom = aC.m_aDoc.getOrder ();
    if (aFromLists != null)
    {
      final IXdmNode aFromNode = _findLastFrom (aC, aFromLists, nStart);
      if (aFromNode != null)
        nFrom = aFromNode.getOrder ();
    }
    final boolean bExact = _isCountExact ();
    for (final List <IXdmNode> aNodes : aCountLists)
    {
      final int nLow = XdmDocumentNode.getLowerBound (aNodes, 0, nFrom);
      final int nHigh = XdmDocumentNode.getLowerBound (aNodes, nLow, nStart);
      if (bExact)
        aC.m_nCount += nHigh - nLow;
      else
        for (int i = nLow; i < nHigh; ++i)
        {
          aC.m_aCtx.getDynamicContext ().checkInterrupt ();
          if (_matchesCount (aC, aNodes.get (i)))
            aC.m_nCount++;
        }
    }
    return true;
  }

  /**
   * level="any" by walking the tree, for the nodes before the start node (the
   * selected node, or the parent of a selected attribute or namespace node,
   * both already visited): the preceding siblings of the start node and of
   * each of its ancestors with their subtrees, then the ancestor itself, all
   * in reverse document order and iteratively with one stack, until a node
   * matching from.
   *
   * @param aC
   *        The counting state. The count is added to it.
   * @param aStart
   *        The start node.
   */
  private void _countAnyByWalk (@NonNull final Counting aC, @NonNull final IXdmNode aStart)
  {
    // The nodes from the subtree root down to the current one, and for each
    // the index of its next child to visit (counting down)
    final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> ();
    int [] aNext = new int [16];
    IXdmNode aCur = aStart;
    while (true)
    {
      final IXdmNode aParent = aCur.getParent ();
      if (aParent == null)
        return;
      final int nIndex = _indexInParent (aParent, aCur);
      for (int i = nIndex - 1; i >= 0; --i)
      {
        final IXdmNode aRoot = aParent.getChildAtIndex (i);
        aStack.add (aRoot);
        aNext[0] = aRoot.getChildCount () - 1;
        while (aStack.isNotEmpty ())
        {
          final int nTop = aStack.size () - 1;
          final IXdmNode aNode = aStack.get (nTop);
          final int nChild = aNext[nTop];
          if (nChild >= 0)
          {
            // Descend into the last child not yet visited
            aNext[nTop] = nChild - 1;
            final IXdmNode aChild = aNode.getChildAtIndex (nChild);
            if (nTop + 1 == aNext.length)
              aNext = Arrays.copyOf (aNext, aNext.length * 2);
            aNext[nTop + 1] = aChild.getChildCount () - 1;
            aStack.add (aChild);
          }
          else
          {
            // All children are visited: the node itself comes before them
            aStack.removeLast ();
            if (_visitAny (aC, aNode))
              return;
          }
        }
      }
      if (_visitAny (aC, aParent))
        return;
      aCur = aParent;
    }
  }

  /**
   * level="any": the nodes on the preceding and ancestor-or-self axes that
   * match count, not before the last of them matching from (XSLT 3.0 section
   * 12.3). The selected node and, for an attribute or namespace node, its
   * parent are tested first (other attributes of the parent are on neither
   * axis); the nodes before them come from the name index of the document
   * where the patterns allow it
   * ({@link #_countAnyByNames(Counting, IXdmNode)}), otherwise from a walk in
   * reverse document order that stops at the from node
   * ({@link #_countAnyByWalk(Counting, IXdmNode)}).
   *
   * @param aC
   *        The counting state.
   * @return The number of counted nodes, 0 for the empty place marker.
   */
  private long _countAny (@NonNull final Counting aC)
  {
    final IXdmNode aTarget = aC.m_aTarget;
    if (_visitAny (aC, aTarget))
      return aC.m_nCount;
    IXdmNode aStart = aTarget;
    if (aTarget.getKind () == ENodeKind.ATTRIBUTE || aTarget.getKind () == ENodeKind.NAMESPACE)
    {
      aStart = aTarget.getParent ();
      if (aStart == null)
        return aC.m_nCount;
      if (_visitAny (aC, aStart))
        return aC.m_nCount;
    }
    if (!_countAnyByNames (aC, aStart))
      _countAnyByWalk (aC, aStart);
    return aC.m_nCount;
  }

  /**
   * Round a number like fn:round: to the nearest integer, a half rounded
   * towards positive infinity (so -0.5 gives 0).
   */
  @NonNull
  private static BigInteger _round (@NonNull final BigDecimal aValue)
  {
    return aValue.add (BigDecimal.valueOf (5, 1)).setScale (0, RoundingMode.FLOOR).toBigInteger ();
  }

  /**
   * The integers of the value attribute (XSLT 3.0 section 12.2): each atomized
   * value is converted like <code>xs:integer(round(number($V)))</code>
   * (integers are kept exactly, decimals are rounded without going through
   * xs:double) and XTDE0980 is raised if that is not possible (NaN or
   * infinity) or if the rounded integer is negative. So -0.3 gives 0.
   */
  @NonNull
  private ICommonsList <BigInteger> _computeValue (@NonNull final XsltContext aCtx)
  {
    final XdmSequence aAtomized = Atomizer.atomize (m_aValue.evaluate (aCtx));
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    for (final IXdmItem aItem : aAtomized)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aItem;
      final EAtomicType eType = aAtomic.getType ();
      if (eType.isIntegerFamily ())
      {
        final BigInteger aInt = aAtomic.getBigInteger ();
        if (aInt.signum () < 0)
          throw new XPathException ("XTDE0980", "The value " + aInt + " of xsl:number is negative");
        ret.add (aInt);
        continue;
      }
      if (eType.isDerivedFrom (EAtomicType.DECIMAL))
      {
        final BigDecimal aDec = aAtomic.getBigDecimal ();
        final BigInteger aRounded = _round (aDec);
        if (aRounded.signum () < 0)
          throw new XPathException ("XTDE0980", "The value " + aDec.toPlainString () + " of xsl:number is negative");
        ret.add (aRounded);
        continue;
      }
      final double d = AtomicComparator.toNumber (aAtomic);
      if (Double.isNaN (d) || Double.isInfinite (d))
        throw new XPathException ("XTDE0980",
                                  "The value '" + aAtomic.getLexical () + "' of xsl:number is not convertible to an integer");
      final BigInteger aRounded = _round (new BigDecimal (d));
      if (aRounded.signum () < 0)
        throw new XPathException ("XTDE0980", "The value " + aAtomic.getLexical () + " of xsl:number is negative");
      ret.add (aRounded);
    }
    return ret;
  }

  /** The numbers to format: from the value attribute or by counting nodes. */
  @NonNull
  private ICommonsList <BigInteger> _compute (@NonNull final XsltContext aCtx)
  {
    if (m_aValue != null)
      return _computeValue (aCtx);

    final IXdmNode aTarget;
    if (m_aSelect != null)
    {
      final XdmSequence aSel = m_aSelect.evaluate (aCtx);
      if (aSel.size () != 1)
        throw new XPathException ("XTTE1000", "The select attribute of xsl:number must select exactly one node, not " + aSel.size () + " items");
      if (!(aSel.get (0) instanceof final IXdmNode aNode))
        throw new XPathException ("XTTE1000", "The select attribute of xsl:number did not select a node");
      aTarget = aNode;
    }
    else
    {
      if (!(aCtx.getContextItem () instanceof final IXdmNode aNode))
        throw new XPathException ("XTTE0990", "The context item of xsl:number is absent or not a node");
      aTarget = aNode;
    }

    final Counting aC = new Counting (aCtx, aTarget, m_aCount, m_aFrom);
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    switch (m_eLevel)
    {
      case MULTIPLE:
        return _countMultiple (aC);
      case ANY:
      {
        final long n = _countAny (aC);
        if (n > 0)
          ret.add (BigInteger.valueOf (n));
        return ret;
      }
      default:
      {
        final long n = _countSingle (aC);
        if (n > 0)
          ret.add (BigInteger.valueOf (n));
        return ret;
      }
    }
  }

  /**
   * A coarse BCP 47 shape check: subtags of 1 to 8 ASCII letters or digits,
   * the first starting with a letter.
   */
  private static boolean _isLanguageTag (@NonNull final String s)
  {
    final String [] aParts = s.split ("-", -1);
    for (int i = 0; i < aParts.length; ++i)
    {
      final String sPart = aParts[i];
      if (sPart.isEmpty () || sPart.length () > 8)
        return false;
      for (int j = 0; j < sPart.length (); ++j)
      {
        final char c = sPart.charAt (j);
        final boolean bLetter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        final boolean bDigit = c >= '0' && c <= '9';
        if (!bLetter && !(bDigit && (i > 0 || j > 0)))
          return false;
      }
    }
    return true;
  }

  /**
   * Parse the effective value of start-at (XSLT 3.0 section 12.1) into its
   * whitespace-separated integers. After trimming leading and trailing XML
   * whitespace the value must match <code>-?[0-9]+(\s+-?[0-9]+)*</code>: no
   * plus sign, no empty value.
   *
   * @param s
   *        The effective start-at value.
   * @return The integers, never empty, or <code>null</code> if the value does
   *         not have that syntax.
   */
  @Nullable
  private static ICommonsList <BigInteger> _parseStartAt (@NonNull final String s)
  {
    final String sTrimmed = XsltNames.trimXmlWhitespace (s);
    if (!START_AT.matcher (sTrimmed).matches ())
      return null;
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    for (final String sPart : sTrimmed.split ("[ \\t\\n\\r]+"))
      ret.add (new BigInteger (sPart));
    return ret;
  }

  /**
   * Parse the effective value of grouping-size (XSLT 3.0 section 12.4): after
   * trimming leading and trailing XML whitespace it must be in the lexical
   * space of xs:integer. Zero or a negative value means no grouping; a value
   * beyond the int range is capped to {@link Integer#MAX_VALUE}.
   *
   * @param s
   *        The effective grouping-size value.
   * @return The group size (0 for no grouping), or -1 if the value is not an
   *         xs:integer.
   */
  private static int _parseGroupingSize (@NonNull final String s)
  {
    final String sTrimmed = XsltNames.trimXmlWhitespace (s);
    if (!INTEGER.matcher (sTrimmed).matches ())
      return -1;
    final BigInteger aSize = new BigInteger (sTrimmed);
    if (aSize.signum () <= 0)
      return 0;
    return aSize.bitLength () < Integer.SIZE ? aSize.intValue () : Integer.MAX_VALUE;
  }

  /**
   * @return The number of sibling counts and level="any" counts made so far
   *         (in this JVM) from the name index of the document instead of
   *         testing every preceding node. For tests that need to know which
   *         path ran: the number is the same either way.
   */
  public static long getNameIndexCountCount ()
  {
    return NAME_INDEX_COUNTS.get ();
  }

  /**
   * Run xsl:number (XSLT 3.0 section 12): compute the place marker, re-base it
   * by start-at (only evaluated for a non-empty place marker; the last
   * start-at value applies to the surplus numbers), evaluate the conversion
   * attributes of section 12.4 and format with {@link NumberFormatter}. A
   * grouping-size of zero or less disables grouping. The result is written
   * as one text node, nothing for an empty string.
   *
   * @param aCtx
   *        The run-time context.
   * @param aOut
   *        The output that receives the text node.
   * @throws XPathException
   *         XTDE0030 for an invalid start-at, a grouping-size that is no
   *         xs:integer or a lang value that is no language tag, plus the errors of the place marker computation
   *         (XTDE0980, XTTE0990, XTTE1000).
   */
  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final ICommonsList <BigInteger> aNums = _compute (aCtx);
    if (aNums.isNotEmpty ())
    {
      // Each number is shifted by its start-at value minus one; the last
      // start-at value is repeated for surplus numbers
      ICommonsList <BigInteger> aStarts = m_aConstStartAt;
      if (aStarts == null)
      {
        final String sStartAt = m_aStartAt.evaluate (aCtx);
        aStarts = _parseStartAt (sStartAt);
        if (aStarts == null)
          throw new XPathException ("XTDE0030", "The start-at value '" + sStartAt + "' of xsl:number is not a list of integers");
      }
      for (int i = 0; i < aNums.size (); ++i)
      {
        final BigInteger aDelta = aStarts.get (Math.min (i, aStarts.size () - 1)).subtract (BigInteger.ONE);
        if (aDelta.signum () != 0)
          aNums.set (i, aNums.get (i).add (aDelta));
      }
    }
    final Picture aPicture = m_aConstPicture != null ? m_aConstPicture
                                                     : NumberFormatter.parsePicture (m_aFormat.evaluate (aCtx));
    final String sGroupSep = m_aGroupSep != null ? m_aGroupSep.evaluate (aCtx) : "";
    int nGroupSize = 0;
    if (m_aGroupSize != null)
    {
      final String sGroupSize = m_aGroupSize.evaluate (aCtx);
      nGroupSize = _parseGroupingSize (sGroupSize);
      if (nGroupSize < 0)
        throw new XPathException ("XTDE0030", "The grouping-size value '" + sGroupSize + "' of xsl:number is not an integer");
    }
    String sLang = null;
    if (m_aLang != null)
    {
      sLang = m_aLang.evaluate (aCtx);
      if (!sLang.isEmpty () && !_isLanguageTag (sLang))
        throw new XPathException ("XTDE0030", "The lang value '" + sLang + "' of xsl:number is not a valid language code");
    }
    final String sOrdinal = m_aOrdinal != null ? m_aOrdinal.evaluate (aCtx) : "";
    final String sText = NumberFormatter.format (aNums, aPicture, sGroupSep, nGroupSize, sLang, sOrdinal);
    if (!sText.isEmpty ())
      aOut.appendNew (IXdmNode.createText (sText));
  }

  /**
   * The names of a pattern that matches exactly the elements of these names
   * that have a parent: every top-level alternative is a relative path of a
   * single <code>child::</code> step (written or defaulted) with an exact name
   * test, without predicates.
   *
   * @param aPattern
   *        The pattern.
   * @return The distinct names, or <code>null</code> if the pattern has
   *         another shape.
   */
  @Nullable
  private static List <XdmQName> _getExactNames (@NonNull final XsltPattern aPattern)
  {
    final ICommonsList <XdmQName> ret = new CommonsArrayList <> ();
    for (final XsltPattern aAlternative : aPattern.getAlternatives ())
    {
      if (!(aAlternative instanceof final CompiledXsltPattern aCP) ||
          !(aCP.getPatternExpr () instanceof final IExpr.PathExpr aPath) ||
          aPath.start () != null ||
          aPath.absolute () ||
          aPath.steps ().size () != 1)
        return null;
      final IExpr.StepExpr aStep = aPath.steps ().getFirst ();
      if (aStep.axis () != EAxis.CHILD ||
          aStep.postfix () != null ||
          aStep.fused () != null ||
          !aStep.predicates ().isEmpty ())
        return null;
      final NodeTest aTest = aStep.test ();
      if (aTest == null ||
          aTest.kind () != ENodeTestKind.NAME ||
          aTest.anyName () ||
          aTest.anyNamespace () ||
          aTest.anyLocalName () ||
          aTest.name () == null)
        return null;
      if (!ret.contains (aTest.name ()))
        ret.add (aTest.name ());
    }
    return ret;
  }

  /**
   * Compile the count or from attribute as a pattern with the full static
   * context of the element (so it may use local variables), register it with
   * the streamability index if there is one, determine the element names it
   * can match (for the name index of the document), and reject static calls of
   * fn:current-group (XTSE1060), fn:current-grouping-key (XTSE1070),
   * fn:current-merge-group (XTSE3470) and fn:current-merge-key (XTSE3500).
   *
   * @param aCtx
   *        The compile context.
   * @param aEl
   *        The xsl:number element.
   * @param sAttr
   *        "count" or "from".
   * @return The compiled pattern, <code>null</code> if the attribute is
   *         absent.
   */
  @Nullable
  private static NumberPattern _compilePattern (@NonNull final CompileContext aCtx,
                                                @NonNull final XslElement aEl,
                                                @NonNull final String sAttr)
  {
    final String sPattern = aEl.getAttr (sAttr);
    if (sPattern == null)
      return null;
    final StaticContext aSC = aCtx.createStaticContext (aEl);
    final XsltPattern aPattern;
    try
    {
      aPattern = XsltPatternParser.parse (sPattern, aSC);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
    final StreamabilityIndex aIndex = aCtx.getCompiler ().getStreamabilityIndex ();
    if (aIndex != null)
      aIndex.addPattern (aEl, sPattern, aPattern);
    if (aPattern instanceof final CompiledXsltPattern aCP)
      for (final String [] aEntry : FORBIDDEN_PATTERN_FUNCTIONS)
      {
        final Predicate <XdmQName> aFilter = n -> XdmConstants.NS_FN.equals (n.namespaceUri ()) &&
                                                   aEntry[0].equals (n.localName ());
        if (aCP.containsFunction (aFilter))
          throw XsltException.at (aEl, aEntry[1], aEntry[0] + "() may not be used in a pattern");
      }
    final List <XdmQName> aExact = _getExactNames (aPattern);
    if (aExact != null)
      return new NumberPattern (aPattern, aSC, aExact, true);
    final PatternFinalNames aFinal = aPattern.getFinalNames ();
    return new NumberPattern (aPattern, aSC, aFinal == null ? null : aFinal.elementNames (), false);
  }

  /**
   * Compile an xsl:number element.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction.
   */
  @NonNull
  public static IInstruction compile (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aValue = aCtx.compileOptionalExpression (aEl, "value");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final NumberPattern aCount = _compilePattern (aCtx, aEl, "count");
    final NumberPattern aFrom = _compilePattern (aCtx, aEl, "from");
    ELevel eLevel = ELevel.SINGLE;
    final String sLevel = aEl.getAttr ("level");
    if (sLevel != null)
      switch (sLevel.strip ())
      {
        case "single" -> eLevel = ELevel.SINGLE;
        case "multiple" -> eLevel = ELevel.MULTIPLE;
        case "any" -> eLevel = ELevel.ANY;
        default -> throw XsltException.at (aEl,
                                           "XTSE0020",
                                           "The level attribute of xsl:number must be single, multiple or any, not '" +
                                                       sLevel +
                                                       "'");
      }
    final String sFormat = aEl.getAttr ("format");
    final Avt aFormat = aCtx.compileAvt (aEl, sFormat == null ? "1" : sFormat);
    final Avt aGroupSep = aCtx.compileOptionalAvt (aEl, "grouping-separator");
    final Avt aGroupSize = aCtx.compileOptionalAvt (aEl, "grouping-size");
    if (aGroupSize != null && aGroupSize.isConstant () && _parseGroupingSize (aGroupSize.getConstantValue ()) < 0)
      throw XsltException.at (aEl,
                              "XTSE0020",
                              "The grouping-size value '" + aGroupSize.getConstantValue () + "' of xsl:number is not an integer");
    final Avt aOrdinal = aCtx.compileOptionalAvt (aEl, "ordinal");
    final String sStartAt = aEl.getAttr ("start-at");
    final Avt aStartAt = aCtx.compileAvt (aEl, sStartAt == null ? "1" : sStartAt);
    final ICommonsList <BigInteger> aConstStartAt = aStartAt.isConstant () ? _parseStartAt (aStartAt.getConstantValue ()) : null;
    if (aStartAt.isConstant () && aConstStartAt == null)
      throw XsltException.at (aEl,
                              "XTSE0020",
                              "The start-at value '" + aStartAt.getConstantValue () + "' of xsl:number is not a list of integers");
    final Avt aLang = aCtx.compileOptionalAvt (aEl, "lang");
    // letter-value is accepted; traditional numbering falls back to alphabetic
    aCtx.compileOptionalAvt (aEl, "letter-value");
    return new NumberInstruction (aEl,
                                  aValue,
                                  aSelect,
                                  aCount,
                                  aFrom,
                                  eLevel,
                                  aFormat,
                                  aGroupSep,
                                  aGroupSize,
                                  aOrdinal,
                                  aStartAt,
                                  aLang,
                                  aFormat.isConstant () ? NumberFormatter.parsePicture (aFormat.getConstantValue ()) : null,
                                  aConstStartAt);
  }
}
