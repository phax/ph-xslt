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
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.BuiltInFunctionItem;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:function-lookup, fn:function-name, fn:function-arity, fn:for-each, fn:filter, fn:fold-left, fn:fold-right, fn:for-each-pair, fn:sort, fn:apply.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnHigherOrder
{
  @PresentForCodeCoverage
  private static final FnHigherOrder INSTANCE = new FnHigherOrder ();

  private FnHigherOrder ()
  {}

  @NonNull
  private static XdmSequence _call1 (@NonNull final DynamicContext aCtx,
                                     @NonNull final IXdmItem aFunc,
                                     @NonNull final XdmSequence aArg)
  {
    return XPathEvaluator.callFunction (aCtx, aFunc, List.of (aArg));
  }

  @NonNull
  private static XdmSequence _call2 (@NonNull final DynamicContext aCtx,
                                     @NonNull final IXdmItem aFunc,
                                     @NonNull final XdmSequence aArg1,
                                     @NonNull final XdmSequence aArg2)
  {
    return XPathEvaluator.callFunction (aCtx, aFunc, List.of (aArg1, aArg2));
  }

  /** @return The conventional prefix of the standard function namespaces, or "" */
  @NonNull
  private static String _getConventionalPrefix (@NonNull final String sNamespaceUri)
  {
    switch (sNamespaceUri)
    {
      case XdmConstants.NS_FN:
        return "fn";
      case XdmConstants.NS_XS:
        return "xs";
      case FunctionLibrary.NS_MATH:
        return "math";
      case FunctionLibrary.NS_MAP:
        return "map";
      case FunctionLibrary.NS_ARRAY:
        return "array";
      default:
        return "";
    }
  }

  @NonNull
  private static XdmSequence _functionLookup (@NonNull final DynamicContext aCtx,
                                              @NonNull final List <XdmSequence> aArgs)
  {
    final XdmQName aName = (XdmQName) ((XdmAtomic) aArgs.get (0).get (0)).getValue ();
    final BigInteger aArity = ((XdmAtomic) aArgs.get (1).get (0)).getBigInteger ();
    if (aArity.signum () < 0 || aArity.bitLength () >= 31)
      return XdmSequence.EMPTY;
    final FunctionDef aDef = aCtx.getStaticContext ().getFunctionLibrary ().getFunction (aName, aArity.intValue ());
    if (aDef == null)
      return XdmSequence.EMPTY;
    // The dynamic context of the function-lookup call is part of the closure
    return XdmSequence.of (new BuiltInFunctionItem (aDef, aCtx));
  }

  @NonNull
  private static XdmSequence _functionName (@NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aFunc = aArgs.get (0).get (0);
    if (!(aFunc instanceof final IXdmFunction aFunction))
      return XdmSequence.EMPTY;
    final XdmQName aName = aFunction.getName ();
    if (aName == null)
      return XdmSequence.EMPTY;
    if (!aName.hasPrefix ())
    {
      final String sPrefix = _getConventionalPrefix (aName.namespaceUri ());
      if (!sPrefix.isEmpty ())
        return XdmSequence.of (XdmAtomic.qname (new XdmQName (aName.namespaceUri (), aName.localName (), sPrefix)));
    }
    return XdmSequence.of (XdmAtomic.qname (aName));
  }

  @NonNull
  private static XdmSequence _forEach (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aFunc = aArgs.get (1).get (0);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aArgs.get (0))
    {
      aCtx.checkInterrupt ();
      aBuilder.addAll (_call1 (aCtx, aFunc, XdmSequence.of (aItem)));
    }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _filter (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aFunc = aArgs.get (1).get (0);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aArgs.get (0))
    {
      aCtx.checkInterrupt ();
      final XdmSequence aResult = _call1 (aCtx, aFunc, XdmSequence.of (aItem));
      if (!aResult.isSingleton () ||
          !(aResult.get (0) instanceof final XdmAtomic aBool) ||
          aBool.getType () != EAtomicType.BOOLEAN)
        throw XPathException.type ("The predicate of fn:filter must return a single xs:boolean");
      if (aBool.getBoolean ())
        aBuilder.add (aItem);
    }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _foldLeft (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aFunc = aArgs.get (2).get (0);
    XdmSequence aAcc = aArgs.get (1);
    for (final IXdmItem aItem : aArgs.get (0))
    {
      aCtx.checkInterrupt ();
      aAcc = _call2 (aCtx, aFunc, aAcc, XdmSequence.of (aItem));
    }
    return aAcc;
  }

  @NonNull
  private static XdmSequence _foldRight (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aFunc = aArgs.get (2).get (0);
    final XdmSequence aSeq = aArgs.get (0);
    XdmSequence aAcc = aArgs.get (1);
    for (int i = aSeq.size () - 1; i >= 0; --i)
    {
      aCtx.checkInterrupt ();
      aAcc = _call2 (aCtx, aFunc, XdmSequence.of (aSeq.get (i)), aAcc);
    }
    return aAcc;
  }

  @NonNull
  private static XdmSequence _forEachPair (@NonNull final DynamicContext aCtx,
                                           @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aSeq1 = aArgs.get (0);
    final XdmSequence aSeq2 = aArgs.get (1);
    final IXdmItem aFunc = aArgs.get (2).get (0);
    final int nCount = Math.min (aSeq1.size (), aSeq2.size ());
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nCount; ++i)
    {
      aCtx.checkInterrupt ();
      aBuilder.addAll (_call2 (aCtx, aFunc, XdmSequence.of (aSeq1.get (i)), XdmSequence.of (aSeq2.get (i))));
    }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _getSortKey (@NonNull final XdmSequence aValue)
  {
    boolean bAtomic = true;
    for (final IXdmItem aItem : aValue)
      if (!(aItem instanceof XdmAtomic))
        bAtomic = false;
    final XdmSequence aAtoms = bAtomic ? aValue : Atomizer.atomize (aValue);
    boolean bUntyped = false;
    for (final IXdmItem aItem : aAtoms)
      if (((XdmAtomic) aItem).getType () == EAtomicType.UNTYPED_ATOMIC)
        bUntyped = true;
    if (!bUntyped)
      return aAtoms;
    // Untyped key values compare as strings
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aAtoms)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aItem;
      aBuilder.add (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC ? XdmAtomic.string (aAtomic.getString ())
                                                                    : aAtomic);
    }
    return aBuilder.build ();
  }

  private static int _compareKeys (@NonNull final XdmSequence aLeft,
                                   @NonNull final XdmSequence aRight,
                                   @Nullable final ICollation aCollation,
                                   @NonNull final DynamicContext aCtx)
  {
    final int nCount = Math.min (aLeft.size (), aRight.size ());
    for (int i = 0; i < nCount; ++i)
    {
      final int nCmp = AtomicComparator.compareForOrder ((XdmAtomic) aLeft.get (i),
                                                         (XdmAtomic) aRight.get (i),
                                                         aCollation,
                                                         aCtx);
      if (nCmp != 0)
        return nCmp;
    }
    return Integer.compare (aLeft.size (), aRight.size ());
  }

  private record SortEntry (IXdmItem item, XdmSequence key)
  {}

  @NonNull
  private static XdmSequence _sort (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aInput = aArgs.get (0);
    ICollation aCollation = null;
    if (aArgs.size () >= 2 && !aArgs.get (1).isEmpty ())
    {
      final String sUri = ((XdmAtomic) aArgs.get (1).get (0)).getString ();
      aCollation = CollationRegistry.getCollation (sUri);
      if (aCollation == null)
        throw XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "'");
    }
    final IXdmItem aKeyFunc = aArgs.size () >= 3 ? aArgs.get (2).get (0) : null;
    final ICommonsList <SortEntry> aEntries = new CommonsArrayList <> (aInput.size ());
    for (final IXdmItem aItem : aInput)
    {
      aCtx.checkInterrupt ();
      final XdmSequence aItemSeq = XdmSequence.of (aItem);
      final XdmSequence aKey = aKeyFunc != null ? _call1 (aCtx, aKeyFunc, aItemSeq) : aItemSeq;
      aEntries.add (new SortEntry (aItem, _getSortKey (aKey)));
    }
    final ICollation aFinalCollation = aCollation;
    // List.sort is a stable merge sort
    aEntries.sort ( (a, b) -> _compareKeys (a.key (), b.key (), aFinalCollation, aCtx));
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final SortEntry aEntry : aEntries)
      aBuilder.add (aEntry.item ());
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _apply (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aFunc = aArgs.get (0).get (0);
    final XdmArray aArray = (XdmArray) aArgs.get (1).get (0);
    final int nArity = XPathEvaluator.getFunctionArity (aFunc);
    if (nArity != aArray.size ())
      throw XPathException.of ("FOAP0001",
                               "The function has arity " + nArity + " but the array has " + aArray.size () + " members");
    return XPathEvaluator.callFunction (aCtx, aFunc, aArray.getAllMembers ());
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "function-lookup", "(xs:QName, xs:integer) as function(*)?", FnHigherOrder::_functionLookup, true);
    aLib.register (sNS, "function-name", "(function(*)) as xs:QName?", (aCtx, aArgs) -> _functionName (aArgs));
    aLib.register (sNS,
                   "function-arity",
                   "(function(*)) as xs:integer",
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.integer (XPathEvaluator.getFunctionArity (aArgs.get (0)
                                                                                                         .get (0)))));
    aLib.register (sNS, "for-each", "(item()*, function(item()) as item()*) as item()*", FnHigherOrder::_forEach);
    aLib.register (sNS, "filter", "(item()*, function(item()) as xs:boolean) as item()*", FnHigherOrder::_filter);
    aLib.register (sNS,
                   "fold-left",
                   "(item()*, item()*, function(item()*, item()) as item()*) as item()*",
                   FnHigherOrder::_foldLeft);
    aLib.register (sNS,
                   "fold-right",
                   "(item()*, item()*, function(item(), item()*) as item()*) as item()*",
                   FnHigherOrder::_foldRight);
    aLib.register (sNS,
                   "for-each-pair",
                   "(item()*, item()*, function(item(), item()) as item()*) as item()*",
                   FnHigherOrder::_forEachPair);
    aLib.register (sNS, "sort", "(item()*) as item()*", FnHigherOrder::_sort);
    aLib.register (sNS, "sort", "(item()*, xs:string?) as item()*", FnHigherOrder::_sort);
    aLib.register (sNS,
                   "sort",
                   "(item()*, xs:string?, function(item()) as xs:anyAtomicType*) as item()*",
                   FnHigherOrder::_sort);
    aLib.register (sNS, "apply", "(function(*), array(*)) as item()*", FnHigherOrder::_apply);
  }
}
