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
import java.net.URI;
import java.net.URISyntaxException;
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
  @PresentForCodeCoverage
  private static final FnSequence INSTANCE = new FnSequence ();

  private FnSequence ()
  {}

  /** Bucket key of a numeric value in fn:distinct-values. */
  private record NumericBucket (double value)
  {}

  /** Bucket key of a string-like value in fn:distinct-values. */
  private record StringBucket (Object key)
  {}

  @Nullable
  private static ICollation _resolveCollation (@NonNull final DynamicContext aCtx, @NonNull final String sUri)
  {
    String sAbsUri = sUri;
    try
    {
      final URI aUri = new URI (sUri);
      if (!aUri.isAbsolute ())
      {
        final String sBase = aCtx.getStaticContext ().getBaseUri ();
        if (sBase != null)
          sAbsUri = new URI (sBase).resolve (aUri).toString ();
      }
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      throw XPathException.of ("FOCH0002", "Invalid collation URI '" + sUri + "'");
    }
    final ICollation ret = CollationRegistry.getCollation (sAbsUri);
    if (ret == null)
      throw XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "'");
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
   *         for the codepoint collation.
   */
  @Nullable
  static ICollation getDefaultCollation (@NonNull final DynamicContext aCtx)
  {
    return _resolveCollation (aCtx, aCtx.getStaticContext ().getDefaultCollation ());
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

  /** fn:round on a double, as needed by fn:subsequence. */
  private static double _roundHalfUp (final double d)
  {
    if (Double.isNaN (d) || Double.isInfinite (d))
      return d;
    return Math.floor (d + 0.5);
  }

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

  private static boolean _isNaN (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    return aPayload instanceof final Double aD && aD.isNaN () || aPayload instanceof final Float aF && aF.isNaN ();
  }

  /**
   * @return A key that two values equal under eq always share, or
   *         <code>null</code> if there is none for the value's type.
   */
  @Nullable
  private static Object _getBucketKey (@NonNull final XdmAtomic aValue,
                                       @Nullable final ICollation aCollation,
                                       final boolean bNarrow)
  {
    if (aValue.isNumeric ())
    {
      double d = aValue.getAsDouble ();
      if (Double.isNaN (d))
        return new NumericBucket (Double.NaN);
      // A comparison with an xs:float happens in float space
      if (bNarrow)
        d = (float) d;
      if (d == 0)
        d = 0;
      return new NumericBucket (d);
    }
    if (aValue.isStringLike ())
      return new StringBucket (aCollation == null ? aValue.getString ()
                                                  : aCollation.getCollationKey (aValue.getString ()));
    return null;
  }

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
      final Object aKey = _getBucketKey (aCmp, aCollation, bNarrow);
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

  @NonNull
  private static IXdmNode _getDocumentRoot (@NonNull final IXdmNode aNode, @NonNull final String sFunc)
  {
    final IXdmNode aRoot = aNode.getRoot ();
    if (!aRoot.isDocument ())
      throw XPathException.of ("FODC0001", "The tree searched by fn:" + sFunc + " is not rooted at a document node");
    return aRoot;
  }

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

  private static boolean _isIDRef (@NonNull final IXdmNode aNode)
  {
    final EIDKind eKind = aNode.getIDKind ();
    return eKind == EIDKind.IDREF || eKind == EIDKind.IDREFS;
  }

  private static boolean _matchesIDRef (@NonNull final String sValue, @NonNull final ICommonsSet <String> aWanted)
  {
    for (final String sToken : _tokenize (sValue))
      if (aWanted.contains (sToken))
        return true;
    return false;
  }

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

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "empty", "(item()*) as xs:boolean", (aCtx, aArgs) -> _bool (aArgs.get (0).isEmpty ()));
    aLib.register (sNS, "exists", "(item()*) as xs:boolean", (aCtx, aArgs) -> _bool (!aArgs.get (0).isEmpty ()));
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
