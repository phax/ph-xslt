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
package com.helger.xpath.fn;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.NodeSorter;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: fn:empty, exists, head, tail, insert-before, remove,
 * reverse, subsequence, unordered, distinct-values, index-of, deep-equal,
 * zero-or-one, one-or-more, exactly-one, id, idref, element-with-id, lang,
 * innermost, outermost.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnSequence
{
  /**
   * The implementation of <code>fn:exists</code>. The evaluator recognises it
   * in a static call and stops evaluating the argument at its first item.
   */
  public static final IXPathFunction EXISTS = (aCtx, aArgs) -> _bool (!aArgs.get (0).isEmpty ());

  /**
   * The implementation of <code>fn:empty</code>. The evaluator recognises it
   * in a static call and stops evaluating the argument at its first item.
   */
  public static final IXPathFunction EMPTY = (aCtx, aArgs) -> _bool (aArgs.get (0).isEmpty ());

  @PresentForCodeCoverage
  private static final FnSequence INSTANCE = new FnSequence ();

  private FnSequence ()
  {}

  /**
   * Resolve a collation URI with {@link com.helger.xpath.context.StaticContext#resolveCollation(String)} of the
   * static context of the call.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param sUri
   *        The collation URI as written.
   * @return The collation, <code>null</code> for the codepoint collation (the
   *         convention of the comparison helpers).
   * @throws XPathException
   *         FOCH0002 for a syntactically invalid or unsupported collation URI.
   */
  @Nullable
  private static ICollation _resolveCollation (@NonNull final DynamicContext aCtx, @NonNull final String sUri)
  {
    final ICollation ret = aCtx.getStaticContext ().resolveCollation (sUri);
    // The comparison helpers use null for the codepoint collation
    return ret == CollationRegistry.CODEPOINT ? null : ret;
  }

  /**
   * @param aCtx
   *        Dynamic context (for the static base URI).
   * @param aArg
   *        A collation argument (a single xs:string).
   * @return The collation, <code>null</code> for the codepoint collation.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @Nullable
  static ICollation getCollation (@NonNull final DynamicContext aCtx, @NonNull final XdmSequence aArg)
  {
    return _resolveCollation (aCtx, ((XdmAtomic) aArg.get (0)).getString ());
  }

  /**
   * @param aCtx
   *        Dynamic context.
   * @return The default collation of the static context, <code>null</code>
   *         for the codepoint collation. Resolved once per static context (see
   *         {@link com.helger.xpath.context.StaticContext#getResolvedDefaultCollation()}).
   */
  @Nullable
  static ICollation getDefaultCollation (@NonNull final DynamicContext aCtx)
  {
    final ICollation ret = aCtx.getStaticContext ().getResolvedDefaultCollation ();
    return ret == CollationRegistry.CODEPOINT ? null : ret;
  }

  @NonNull
  private static XdmSequence _bool (final boolean b)
  {
    return XdmSequence.of (XdmAtomic.bool (b));
  }

  /** @return The position argument clamped to the int range. */
  private static long _getPosition (@NonNull final XdmSequence aArg)
  {
    final BigInteger aBI = ((XdmAtomic) aArg.get (0)).getBigInteger ();
    if (aBI.bitLength () > 40)
      return aBI.signum () < 0 ? Long.MIN_VALUE / 2 : Long.MAX_VALUE / 2;
    return aBI.longValue ();
  }

  /**
   * <code>fn:insert-before($target as item()*, $position as xs:integer,
   * $inserts as item()*) as item()*</code> (F&amp;O 3.1 section 14.1.5): a
   * position below 1 inserts at the start, one beyond the end appends.
   *
   * @param aArgs
   *        The arguments.
   * @return The new sequence.
   */
  @NonNull
  private static XdmSequence _insertBefore (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aTarget = aArgs.get (0);
    final XdmSequence aInserts = aArgs.get (2);
    final long nPos = Math.min (Math.max (_getPosition (aArgs.get (1)), 1), aTarget.size () + 1L);
    final int nIndex = (int) nPos - 1;
    return XdmSequence.concat (aTarget.subSequence (0, nIndex),
                               aInserts,
                               aTarget.subSequence (nIndex, aTarget.size ()));
  }

  /**
   * <code>fn:remove($target as item()*, $position as xs:integer) as
   * item()*</code> (F&amp;O 3.1 section 14.1.6).
   *
   * @param aArgs
   *        The arguments.
   * @return The sequence without the item at the 1-based position; unchanged
   *         if the position is out of range.
   */
  @NonNull
  private static XdmSequence _remove (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aTarget = aArgs.get (0);
    final long nPos = _getPosition (aArgs.get (1));
    if (nPos < 1 || nPos > aTarget.size ())
      return aTarget;
    final int nIndex = (int) nPos - 1;
    return XdmSequence.concat (aTarget.subSequence (0, nIndex), aTarget.subSequence (nIndex + 1, aTarget.size ()));
  }

  /**
   * @param aSeq
   *        A sequence.
   * @return The items in reverse order (<code>fn:reverse</code>, F&amp;O 3.1
   *         section 14.1.7); the sequence itself if it has at most one item.
   */
  @NonNull
  private static XdmSequence _reverse (@NonNull final XdmSequence aSeq)
  {
    final int nSize = aSeq.size ();
    if (nSize <= 1)
      return aSeq;
    final IXdmItem [] aItems = new IXdmItem [nSize];
    for (int i = 0; i < nSize; ++i)
      aItems[nSize - 1 - i] = aSeq.get (i);
    return XdmSequence.of (aItems);
  }

  /**
   * fn:round on a double, as needed by fn:subsequence: halves round towards
   * positive infinity. The fraction is compared with 0.5 instead of computing
   * <code>floor(d + 0.5)</code>, whose addition can round up a value just
   * below a half (such as 0.49999999999999994).
   */
  private static double _roundHalfUp (final double d)
  {
    if (Double.isNaN (d) || Double.isInfinite (d))
      return d;
    // floor(d) and the fraction d - floor(d) are both exact
    final double dFloor = Math.floor (d);
    return d - dFloor >= 0.5 ? dFloor + 1 : dFloor;
  }

  /**
   * <code>fn:subsequence($sourceSeq as item()*, $startingLoc as xs:double[,
   * $length as xs:double]) as item()*</code> (F&amp;O 3.1 section 14.1.8): the
   * items at the 1-based positions <code>p</code> with <code>round($start)
   * &lt;= p &lt; round($start) + round($length)</code>, with the rounding of
   * {@link #_roundHalfUp(double)} and no upper limit without
   * <code>$length</code>. A NaN bound, also from <code>-INF + INF</code>,
   * gives the empty sequence.
   *
   * @param aArgs
   *        The arguments.
   * @return The subsequence.
   */
  @NonNull
  private static XdmSequence _subsequence (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aSeq = aArgs.get (0);
    final double dStart = _roundHalfUp (((XdmAtomic) aArgs.get (1).get (0)).getAsDouble ());
    double dEnd = Double.POSITIVE_INFINITY;
    if (aArgs.size () > 2)
      dEnd = dStart + _roundHalfUp (((XdmAtomic) aArgs.get (2).get (0)).getAsDouble ());
    // Items at positions p with start <= p < end
    if (Double.isNaN (dStart) || Double.isNaN (dEnd))
      return XdmSequence.EMPTY;
    final double dFirst = Math.max (dStart, 1);
    final double dLast = Math.min (dEnd - 1, aSeq.size ());
    if (dFirst > dLast)
      return XdmSequence.EMPTY;
    return aSeq.subSequence ((int) dFirst - 1, (int) dLast);
  }

  /** @return The value as compared by eq: xs:untypedAtomic as xs:string. */
  @NonNull
  private static XdmAtomic _getComparisonForm (@NonNull final XdmAtomic aValue)
  {
    return aValue.getType () == EAtomicType.UNTYPED_ATOMIC ? XdmAtomic.string (aValue.getString ()) : aValue;
  }

  /**
   * @param aValue
   *        An atomic value.
   * @return <code>true</code> if it is a float or double NaN.
   */
  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return (aPayload instanceof final Double aD && aD.isNaN ()) || (aPayload instanceof final Float aF && aF.isNaN ());
  }

  /**
   * <code>fn:distinct-values($arg as xs:anyAtomicType*[, $collation as
   * xs:string]) as xs:anyAtomicType*</code> (F&amp;O 3.1 section 14.2.1): keep
   * the first of every group of values that are equal by
   * {@link AtomicComparator#isEqualForDistinct} (xs:untypedAtomic compared as
   * xs:string, NaN equal to NaN), in input order. To stay linear the values
   * are put into buckets by {@link AtomicComparator#getDistinctBucketKey} and
   * only compared within a bucket; values without a bucket key are compared
   * with all other such values. If any value is an xs:float, the keys are
   * computed for float precision. Checks for interruption every 1024 values.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The distinct values.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _distinctValues (@NonNull final DynamicContext aCtx,
                                              @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aInput = aArgs.get (0);
    final ICollation aCollation = aArgs.size () > 1 ? getCollation (aCtx, aArgs.get (1)) : getDefaultCollation (aCtx);
    boolean bNarrow = false;
    for (final IXdmItem aItem : aInput)
      if (((XdmAtomic) aItem).getType () == EAtomicType.FLOAT)
      {
        bNarrow = true;
        break;
      }
    // Candidates are bucketed so that the eq scan stays linear for the usual
    // types; the other types are compared against all kept values
    final ICommonsMap <Object, ICommonsList <XdmAtomic>> aBuckets = new CommonsHashMap <> ();
    final ICommonsList <XdmAtomic> aOther = new CommonsArrayList <> ();
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    int nCount = 0;
    for (final IXdmItem aItem : aInput)
    {
      if ((++nCount & 0x3ff) == 0)
        aCtx.checkInterrupt ();
      final XdmAtomic aValue = (XdmAtomic) aItem;
      final XdmAtomic aCmp = _getComparisonForm (aValue);
      final Object aKey = AtomicComparator.getDistinctBucketKey (aCmp, aCollation, bNarrow);
      ICommonsList <XdmAtomic> aCandidates = aKey == null ? aOther : aBuckets.get (aKey);
      boolean bDuplicate = false;
      if (aCandidates != null)
        for (final XdmAtomic aKept : aCandidates)
          if (AtomicComparator.isEqualForDistinct (aCmp, aKept, aCollation, aCtx))
          {
            bDuplicate = true;
            break;
          }
      if (bDuplicate)
        continue;
      if (aCandidates == null)
      {
        aCandidates = new CommonsArrayList <> (1);
        aBuckets.put (aKey, aCandidates);
      }
      aCandidates.add (aCmp);
      aBuilder.add (aValue);
    }
    return aBuilder.build ();
  }

  /**
   * <code>fn:index-of($seq as xs:anyAtomicType*, $search as xs:anyAtomicType[,
   * $collation as xs:string]) as xs:integer*</code> (F&amp;O 3.1 section
   * 14.2.2): the 1-based positions of the values equal to the search value by
   * {@link AtomicComparator#isEqualForDistinct}, with xs:untypedAtomic
   * compared as xs:string. A NaN search value matches nothing.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The positions in ascending order.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation.
   */
  @NonNull
  private static XdmSequence _indexOf (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aSeq = aArgs.get (0);
    final XdmAtomic aSearch = _getComparisonForm ((XdmAtomic) aArgs.get (1).get (0));
    final ICollation aCollation = aArgs.size () > 2 ? getCollation (aCtx, aArgs.get (2)) : getDefaultCollation (aCtx);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    if (_isNaN (aSearch))
      return XdmSequence.EMPTY;
    for (int i = 0; i < aSeq.size (); ++i)
    {
      if ((i & 0x3ff) == 0x3ff)
        aCtx.checkInterrupt ();
      final XdmAtomic aValue = _getComparisonForm ((XdmAtomic) aSeq.get (i));
      if (AtomicComparator.isEqualForDistinct (aValue, aSearch, aCollation, aCtx))
        aBuilder.add (XdmAtomic.integer (i + 1L));
    }
    return aBuilder.build ();
  }

  /**
   * <code>fn:deep-equal($parameter1 as item()*, $parameter2 as item()*[,
   * $collation as xs:string]) as xs:boolean</code> (F&amp;O 3.1 section
   * 14.2.3), delegated to {@link AtomicComparator#deepEqual}.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         FOCH0002 for an unsupported collation, FOTY0015 if a function item
   *         has to be compared.
   */
  @NonNull
  private static XdmSequence _deepEqual (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final ICollation aCollation = aArgs.size () > 2 ? getCollation (aCtx, aArgs.get (2)) : getDefaultCollation (aCtx);
    return _bool (AtomicComparator.deepEqual (aArgs.get (0), aArgs.get (1), aCollation, aCtx));
  }

  /**
   * @return The node given as argument at the index, or the context node.
   */
  @NonNull
  private static IXdmNode _getNodeArg (@NonNull final DynamicContext aCtx,
                                      @NonNull final List <XdmSequence> aArgs,
                                      final int nIndex,
                                      @NonNull final String sFunc)
  {
    if (aArgs.size () > nIndex)
      return (IXdmNode) aArgs.get (nIndex).get (0);
    final IXdmItem aItem = aCtx.getContextItem ();
    if (aItem == null)
      throw XPathException.noContext ("fn:" + sFunc + " has no context item");
    if (!(aItem instanceof final IXdmNode aNode))
      throw XPathException.type ("The context item of fn:" + sFunc + " is not a node");
    return aNode;
  }

  /**
   * @param aNode
   *        The node whose tree is searched.
   * @param sFunc
   *        The function name, for the message.
   * @return The root of the tree, which must be a document node.
   * @throws XPathException
   *         FODC0001 if the root is not a document node.
   */
  @NonNull
  private static IXdmNode _getDocumentRoot (@NonNull final IXdmNode aNode, @NonNull final String sFunc)
  {
    final IXdmNode aRoot = aNode.getRoot ();
    if (!aRoot.isDocument ())
      throw XPathException.of ("FODC0001", "The tree searched by fn:" + sFunc + " is not rooted at a document node");
    return aRoot;
  }

  /**
   * @param c
   *        A character.
   * @return <code>true</code> for XML whitespace: space, tab, line feed and
   *         carriage return.
   */
  private static boolean _isXmlWhitespace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /** Split at XML whitespace, dropping empty tokens. */
  @NonNull
  private static ICommonsList <String> _tokenize (@NonNull final String s)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    int nStart = -1;
    for (int i = 0; i <= s.length (); ++i)
    {
      final boolean bWS = i == s.length () || _isXmlWhitespace (s.charAt (i));
      if (bWS)
      {
        if (nStart >= 0)
        {
          ret.add (s.substring (nStart, i));
          nStart = -1;
        }
      }
      else
        if (nStart < 0)
          nStart = i;
    }
    return ret;
  }

  /**
   * <code>fn:id($arg as xs:string*[, $node as node()]) as element()*</code>
   * and <code>fn:element-with-id</code> with the same signature (F&amp;O 3.1
   * sections 14.5.1 and 14.5.2). The strings are split at whitespace into the
   * wanted ID values; the document of the node (the context node without
   * <code>$node</code>) is searched iteratively for elements with an ID
   * attribute (DTD type ID or <code>xml:id</code>) whose trimmed value is
   * wanted, and for elements whose own typed value is an ID: those are
   * returned themselves by <code>fn:id</code> and as their parent element by
   * <code>fn:element-with-id</code>. Checks for interruption every 1024
   * elements.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @param bElementWithID
   *        <code>true</code> for <code>fn:element-with-id</code>.
   * @return The elements in document order without duplicates; empty if no ID
   *         value is wanted.
   * @throws XPathException
   *         XPDY0002 or XPTY0004 if the context item is needed and absent or
   *         not a node, FODC0001 if the tree is not rooted at a document node.
   */
  @NonNull
  private static XdmSequence _id (@NonNull final DynamicContext aCtx,
                                  @NonNull final List <XdmSequence> aArgs,
                                  final boolean bElementWithID)
  {
    final String sFunc = bElementWithID ? "element-with-id" : "id";
    final IXdmNode aRoot = _getDocumentRoot (_getNodeArg (aCtx, aArgs, 1, sFunc), sFunc);
    final ICommonsSet <String> aWanted = new CommonsHashSet <> ();
    for (final IXdmItem aItem : aArgs.get (0))
      aWanted.addAll (_tokenize (((XdmAtomic) aItem).getString ()));
    if (aWanted.isEmpty ())
      return XdmSequence.EMPTY;

    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    int nCount = 0;
    while (!aStack.isEmpty ())
    {
      if ((++nCount & 0x3ff) == 0)
        aCtx.checkInterrupt ();
      final IXdmNode aNode = aStack.pop ();
      if (aNode.isElement ())
      {
        for (int i = 0; i < aNode.getAttributeCount (); ++i)
        {
          final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
          if (aAttr.getIDKind () == EIDKind.ID && aWanted.contains (aAttr.getValue ().trim ()))
          {
            aBuilder.add (aNode);
            break;
          }
        }
        // An element whose typed value is an ID identifies its parent
        if (aNode.getIDKind () == EIDKind.ID && aWanted.contains (aNode.getStringValue ().trim ()))
        {
          if (!bElementWithID)
            aBuilder.add (aNode);
          else
          {
            final IXdmNode aParent = aNode.getParent ();
            if (aParent != null && aParent.isElement ())
              aBuilder.add (aParent);
          }
        }
      }
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
      {
        final IXdmNode aChild = aNode.getChildAtIndex (i);
        if (aChild.isElement ())
          aStack.push (aChild);
      }
    }
    return NodeSorter.sortAndDeduplicate (aBuilder.build ());
  }

  /**
   * @param aNode
   *        An element or attribute.
   * @return <code>true</code> if its ID kind is IDREF or IDREFS.
   */
  private static boolean _isIDRef (@NonNull final IXdmNode aNode)
  {
    final EIDKind eKind = aNode.getIDKind ();
    return eKind == EIDKind.IDREF || eKind == EIDKind.IDREFS;
  }

  /**
   * @param sValue
   *        The value of an IDREF or IDREFS node.
   * @param aWanted
   *        The wanted ID values.
   * @return <code>true</code> if any whitespace-separated token of the value
   *         is wanted.
   */
  private static boolean _matchesIDRef (@NonNull final String sValue, @NonNull final ICommonsSet <String> aWanted)
  {
    for (final String sToken : _tokenize (sValue))
      if (aWanted.contains (sToken))
        return true;
    return false;
  }

  /**
   * <code>fn:idref($arg as xs:string*[, $node as node()]) as node()*</code>
   * (F&amp;O 3.1 section 14.5.3): every string that is a single token after
   * whitespace collapsing is a wanted ID value; the document of the node (the
   * context node without <code>$node</code>) is searched iteratively for
   * elements and attributes of kind IDREF or IDREFS that reference one of
   * them. The traversal visits nodes in document order, so the result needs no
   * sorting. Checks for interruption every 1024 elements.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The referencing elements and attributes in document order.
   * @throws XPathException
   *         XPDY0002 or XPTY0004 if the context item is needed and absent or
   *         not a node, FODC0001 if the tree is not rooted at a document node.
   */
  @NonNull
  private static XdmSequence _idref (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmNode aRoot = _getDocumentRoot (_getNodeArg (aCtx, aArgs, 1, "idref"), "idref");
    // Each string is one candidate ID value (after whitespace collapsing);
    // a string that is not a single token can never match
    final ICommonsSet <String> aWanted = new CommonsHashSet <> ();
    for (final IXdmItem aItem : aArgs.get (0))
    {
      final ICommonsList <String> aTokens = _tokenize (((XdmAtomic) aItem).getString ());
      if (aTokens.size () == 1)
        aWanted.add (aTokens.getFirstOrNull ());
    }
    if (aWanted.isEmpty ())
      return XdmSequence.EMPTY;

    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    int nCount = 0;
    while (!aStack.isEmpty ())
    {
      if ((++nCount & 0x3ff) == 0)
        aCtx.checkInterrupt ();
      final IXdmNode aNode = aStack.pop ();
      if (aNode.isElement ())
      {
        if (_isIDRef (aNode) && _matchesIDRef (aNode.getStringValue (), aWanted))
          aBuilder.add (aNode);
        for (int i = 0; i < aNode.getAttributeCount (); ++i)
        {
          final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
          if (_isIDRef (aAttr) && _matchesIDRef (aAttr.getValue (), aWanted))
            aBuilder.add (aAttr);
        }
      }
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
      {
        final IXdmNode aChild = aNode.getChildAtIndex (i);
        if (aChild.isElement ())
          aStack.push (aChild);
      }
    }
    return aBuilder.build ();
  }

  /**
   * <code>fn:lang($testlang as xs:string?[, $node as node()]) as
   * xs:boolean</code> (F&amp;O 3.1 section 13.4): find the nearest
   * <code>xml:lang</code> attribute on the node or its ancestors; it matches
   * if, both lower-cased with the root locale, it equals the test language
   * or starts with it followed by <code>-</code>. An empty test language
   * counts as the empty string.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return <code>false</code> if there is no <code>xml:lang</code>.
   * @throws XPathException
   *         XPDY0002 or XPTY0004 if the context item is needed and absent or
   *         not a node.
   */
  @NonNull
  private static XdmSequence _lang (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aTestArg = aArgs.get (0);
    final String sTest = aTestArg.isEmpty () ? "" : ((XdmAtomic) aTestArg.get (0)).getString ()
                                                                                  .toLowerCase (Locale.ROOT);
    for (IXdmNode aNode = _getNodeArg (aCtx, aArgs, 1, "lang"); aNode != null; aNode = aNode.getParent ())
    {
      final IXdmNode aLang = aNode.getKind () == ENodeKind.ELEMENT ? aNode.getAttribute (XdmConstants.NS_XML, "lang")
                                                                  : null;
      if (aLang != null)
      {
        final String sLang = aLang.getValue ().toLowerCase (Locale.ROOT);
        return _bool (sLang.equals (sTest) || sLang.startsWith (sTest + "-"));
      }
    }
    return _bool (false);
  }

  /**
   * <code>fn:innermost($nodes as node()*) as node()*</code> and
   * <code>fn:outermost</code> with the same signature (F&amp;O 3.1 sections
   * 13.8 and 13.9). The nodes are first sorted into document order without
   * duplicates. Innermost keeps the nodes that are not an ancestor of another
   * node in the set (the ancestors of every node are marked once, stopping at
   * the first already marked one); outermost keeps the nodes that have no
   * ancestor in the set. Checks for interruption once per node.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aInput
   *        The nodes.
   * @param bInnermost
   *        <code>true</code> for innermost, <code>false</code> for outermost.
   * @return The selected nodes in document order.
   */
  @NonNull
  private static XdmSequence _innermostOutermost (@NonNull final DynamicContext aCtx,
                                                  @NonNull final XdmSequence aInput,
                                                  final boolean bInnermost)
  {
    final XdmSequence aNodes = NodeSorter.sortAndDeduplicate (aInput);
    if (aNodes.size () <= 1)
      return aNodes;
    final Set <IXdmNode> aInSet = Collections.newSetFromMap (new IdentityHashMap <> ());
    for (final IXdmItem aItem : aNodes)
      aInSet.add ((IXdmNode) aItem);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    if (bInnermost)
    {
      // Mark every ancestor of a node in the set
      final Set <IXdmNode> aHasDescendant = Collections.newSetFromMap (new IdentityHashMap <> ());
      for (final IXdmItem aItem : aNodes)
      {
        aCtx.checkInterrupt ();
        for (IXdmNode aAnc = ((IXdmNode) aItem).getParent (); aAnc != null; aAnc = aAnc.getParent ())
          if (!aHasDescendant.add (aAnc))
            break;
      }
      for (final IXdmItem aItem : aNodes)
        if (!aHasDescendant.contains (aItem))
          aBuilder.add (aItem);
    }
    else
    {
      for (final IXdmItem aItem : aNodes)
      {
        aCtx.checkInterrupt ();
        boolean bHasAncestor = false;
        for (IXdmNode aAnc = ((IXdmNode) aItem).getParent (); aAnc != null; aAnc = aAnc.getParent ())
          if (aInSet.contains (aAnc))
          {
            bHasAncestor = true;
            break;
          }
        if (!bHasAncestor)
          aBuilder.add (aItem);
      }
    }
    return aBuilder.build ();
  }

  /**
   * Register the sequence functions <code>fn:empty</code> ({@link #EMPTY}),
   * <code>fn:exists</code> ({@link #EXISTS}), <code>fn:head</code>,
   * <code>fn:tail</code>, <code>fn:insert-before</code>,
   * <code>fn:remove</code>, <code>fn:reverse</code>,
   * <code>fn:subsequence</code>, <code>fn:unordered</code> (returns its
   * argument unchanged), <code>fn:distinct-values</code>,
   * <code>fn:index-of</code>, <code>fn:deep-equal</code>, the cardinality
   * checks <code>fn:zero-or-one</code> (FORG0003), <code>fn:one-or-more</code>
   * (FORG0004) and <code>fn:exactly-one</code> (FORG0005), the ID functions
   * <code>fn:id</code>, <code>fn:element-with-id</code> and
   * <code>fn:idref</code> and <code>fn:lang</code> (with a focus-dependent
   * form without node argument), and <code>fn:innermost</code> and
   * <code>fn:outermost</code>.
   *
   * @param aLib
   *        The library to register into.
   */
  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "empty", "(item()*) as xs:boolean", EMPTY);
    aLib.register (sNS, "exists", "(item()*) as xs:boolean", EXISTS);
    aLib.register (sNS, "head", "(item()*) as item()?", (aCtx, aArgs) -> {
      final XdmSequence aSeq = aArgs.get (0);
      return aSeq.isEmpty () ? XdmSequence.EMPTY : XdmSequence.of (aSeq.get (0));
    });
    aLib.register (sNS, "tail", "(item()*) as item()*", (aCtx, aArgs) -> {
      final XdmSequence aSeq = aArgs.get (0);
      return aSeq.size () <= 1 ? XdmSequence.EMPTY : aSeq.subSequence (1, aSeq.size ());
    });
    aLib.register (sNS,
                   "insert-before",
                   "(item()*, xs:integer, item()*) as item()*",
                   (aCtx, aArgs) -> _insertBefore (aArgs));
    aLib.register (sNS, "remove", "(item()*, xs:integer) as item()*", (aCtx, aArgs) -> _remove (aArgs));
    aLib.register (sNS, "reverse", "(item()*) as item()*", (aCtx, aArgs) -> _reverse (aArgs.get (0)));
    aLib.register (sNS, "subsequence", "(item()*, xs:double) as item()*", (aCtx, aArgs) -> _subsequence (aArgs));
    aLib.register (sNS,
                   "subsequence",
                   "(item()*, xs:double, xs:double) as item()*",
                   (aCtx, aArgs) -> _subsequence (aArgs));
    aLib.register (sNS, "unordered", "(item()*) as item()*", (aCtx, aArgs) -> aArgs.get (0));
    aLib.register (sNS,
                   "distinct-values",
                   "(xs:anyAtomicType*) as xs:anyAtomicType*",
                   FnSequence::_distinctValues);
    aLib.register (sNS,
                   "distinct-values",
                   "(xs:anyAtomicType*, xs:string) as xs:anyAtomicType*",
                   FnSequence::_distinctValues);
    aLib.register (sNS, "index-of", "(xs:anyAtomicType*, xs:anyAtomicType) as xs:integer*", FnSequence::_indexOf);
    aLib.register (sNS,
                   "index-of",
                   "(xs:anyAtomicType*, xs:anyAtomicType, xs:string) as xs:integer*",
                   FnSequence::_indexOf);
    aLib.register (sNS, "deep-equal", "(item()*, item()*) as xs:boolean", FnSequence::_deepEqual);
    aLib.register (sNS, "deep-equal", "(item()*, item()*, xs:string) as xs:boolean", FnSequence::_deepEqual);
    aLib.register (sNS, "zero-or-one", "(item()*) as item()?", (aCtx, aArgs) -> {
      if (aArgs.get (0).size () > 1)
        throw XPathException.of ("FORG0003", "fn:zero-or-one called with a sequence of more than one item");
      return aArgs.get (0);
    });
    aLib.register (sNS, "one-or-more", "(item()*) as item()+", (aCtx, aArgs) -> {
      if (aArgs.get (0).isEmpty ())
        throw XPathException.of ("FORG0004", "fn:one-or-more called with an empty sequence");
      return aArgs.get (0);
    });
    aLib.register (sNS, "exactly-one", "(item()*) as item()", (aCtx, aArgs) -> {
      if (!aArgs.get (0).isSingleton ())
        throw XPathException.of ("FORG0005",
                                 "fn:exactly-one called with a sequence of " + aArgs.get (0).size () + " items");
      return aArgs.get (0);
    });
    aLib.register (sNS, "id", "(xs:string*) as element()*", (aCtx, aArgs) -> _id (aCtx, aArgs, false), true);
    aLib.register (sNS, "id", "(xs:string*, node()) as element()*", (aCtx, aArgs) -> _id (aCtx, aArgs, false));
    aLib.register (sNS,
                   "element-with-id",
                   "(xs:string*) as element()*",
                   (aCtx, aArgs) -> _id (aCtx, aArgs, true),
                   true);
    aLib.register (sNS,
                   "element-with-id",
                   "(xs:string*, node()) as element()*",
                   (aCtx, aArgs) -> _id (aCtx, aArgs, true));
    aLib.register (sNS, "idref", "(xs:string*) as node()*", FnSequence::_idref, true);
    aLib.register (sNS, "idref", "(xs:string*, node()) as node()*", FnSequence::_idref);
    aLib.register (sNS, "lang", "(xs:string?) as xs:boolean", FnSequence::_lang, true);
    aLib.register (sNS, "lang", "(xs:string?, node()) as xs:boolean", FnSequence::_lang);
    aLib.register (sNS,
                   "innermost",
                   "(node()*) as node()*",
                   (aCtx, aArgs) -> _innermostOutermost (aCtx, aArgs.get (0), true));
    aLib.register (sNS,
                   "outermost",
                   "(node()*) as node()*",
                   (aCtx, aArgs) -> _innermostOutermost (aCtx, aArgs.get (0), false));
  }
}
