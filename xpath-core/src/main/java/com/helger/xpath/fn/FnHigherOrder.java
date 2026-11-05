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

  /**
   * Call a function item (or map or array) with one argument through
   * {@link XPathEvaluator#callFunction}, which applies the function conversion
   * rules of its signature.
   *
   * @param aCtx
   *        The dynamic context of the caller.
   * @param aFunc
   *        The function item.
   * @param aArg
   *        The argument.
   * @return The result.
   */
  @NonNull
  private static XdmSequence _call1 (@NonNull final DynamicContext aCtx,
                                     @NonNull final IXdmItem aFunc,
                                     @NonNull final XdmSequence aArg)
  {
    return XPathEvaluator.callFunction (aCtx, aFunc, List.of (aArg));
  }

  /**
   * Call a function item with two arguments, like
   * {@link #_call1(DynamicContext, IXdmItem, XdmSequence)}.
   *
   * @param aCtx
   *        The dynamic context of the caller.
   * @param aFunc
   *        The function item.
   * @param aArg1
   *        The first argument.
   * @param aArg2
   *        The second argument.
   * @return The result.
   */
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

  /**
   * <code>fn:function-lookup($name as xs:QName, $arity as xs:integer) as
   * function(*)?</code> (F&amp;O 3.1 section 16.1.1): find the function
   * through {@link FunctionLibrary#getFunctionForLookup(XdmQName, int)} of the
   * static context and return it as a function item that keeps the dynamic
   * context of this call (so a focus-dependent function such as
   * <code>position#0</code> sees the focus of the lookup). A negative arity or
   * one that does not fit in 31 bits gives the empty sequence, as does an
   * unknown function.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The name and the arity.
   * @return The function item or the empty sequence.
   */
  @NonNull
  private static XdmSequence _functionLookup (@NonNull final DynamicContext aCtx,
                                              @NonNull final List <XdmSequence> aArgs)
  {
    final XdmQName aName = (XdmQName) ((XdmAtomic) aArgs.get (0).get (0)).getValue ();
    final BigInteger aArity = ((XdmAtomic) aArgs.get (1).get (0)).getBigInteger ();
    if (aArity.signum () < 0 || aArity.bitLength () >= 31)
      return XdmSequence.EMPTY;
    final FunctionDef aDef = aCtx.getStaticContext ().getFunctionLibrary ().getFunctionForLookup (aName, aArity.intValue ());
    if (aDef == null)
      return XdmSequence.EMPTY;
    // The dynamic context of the function-lookup call is part of the closure
    return XdmSequence.of (new BuiltInFunctionItem (aDef, aCtx));
  }

  /**
   * <code>fn:function-name($func as function(*)) as xs:QName?</code> (F&amp;O
   * 3.1 section 16.1.2). Maps, arrays and anonymous functions give the empty
   * sequence. A name without prefix in one of the standard namespaces (fn, xs,
   * math, map, array) gets its conventional prefix; other names are returned
   * as they are.
   *
   * @param aArgs
   *        The function item.
   * @return The name or the empty sequence.
   */
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

  /**
   * <code>fn:for-each($seq as item()*, $action as function(item()) as item()*)
   * as item()*</code> (F&amp;O 3.1 section 16.2.1): call the function for
   * every item in order and concatenate the results. Checks for interruption
   * before every call.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The sequence and the function.
   * @return The concatenated results.
   */
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

  /**
   * <code>fn:filter($seq as item()*, $f as function(item()) as xs:boolean) as
   * item()*</code> (F&amp;O 3.1 section 16.2.2): keep the items for which the
   * function returns <code>true</code>, in order. Checks for interruption
   * before every call.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The sequence and the predicate.
   * @return The items that pass.
   * @throws XPathException
   *         XPTY0004 if a call does not return exactly one xs:boolean.
   */
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

  /**
   * <code>fn:fold-left($seq as item()*, $zero as item()*, $f as
   * function(item()*, item()) as item()*) as item()*</code> (F&amp;O 3.1
   * section 16.2.3): starting with the zero value, call the function with the
   * accumulator and each item from first to last.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The sequence, the zero value and the function.
   * @return The final accumulator; the zero value for an empty sequence.
   */
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

  /**
   * <code>fn:fold-right($seq as item()*, $zero as item()*, $f as
   * function(item(), item()*) as item()*) as item()*</code> (F&amp;O 3.1
   * section 16.2.4): starting with the zero value, call the function with each
   * item from last to first and the accumulator. Iterative, so a long sequence
   * cannot overflow the Java stack.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The sequence, the zero value and the function.
   * @return The final accumulator; the zero value for an empty sequence.
   */
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

  /**
   * <code>fn:for-each-pair($seq1 as item()*, $seq2 as item()*, $action as
   * function(item(), item()) as item()*) as item()*</code> (F&amp;O 3.1
   * section 16.2.5): call the function with the items at the same position of
   * both sequences, up to the length of the shorter one, and concatenate the
   * results.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The two sequences and the function.
   * @return The concatenated results.
   */
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

  /**
   * Prepare a sort key of <code>fn:sort</code>: atomise the value unless it
   * consists of atomic values only, then replace every xs:untypedAtomic value
   * by an xs:string with the same string value, so untyped keys compare as
   * strings.
   *
   * @param aValue
   *        The key value, the item itself or the result of the key function.
   * @return The atomic key sequence.
   */
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

  /**
   * Compare two sort keys of <code>fn:sort</code> item by item with
   * {@link AtomicComparator#compareForOrder}; the first difference decides. If
   * one key is a prefix of the other, the shorter one comes first (so an empty
   * key sorts before every other key).
   *
   * @param aLeft
   *        The left key.
   * @param aRight
   *        The right key.
   * @param aCollation
   *        The collation for strings, <code>null</code> for the codepoint
   *        collation.
   * @param aCtx
   *        The dynamic context (implicit timezone).
   * @return Negative, 0 or positive.
   * @throws XPathException
   *         XPTY0004 if two key values at the same position are not
   *         comparable.
   */
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

  /** An item of the <code>fn:sort</code> input with its prepared sort key. */
  private record SortEntry (IXdmItem item, XdmSequence key)
  {}

  /**
   * <code>fn:sort($input as item()*[, $collation as xs:string?[, $key as
   * function(item()) as xs:anyAtomicType*]]) as item()*</code> (F&amp;O 3.1
   * section 16.2.6). The key of every item is the item itself or the result of
   * the key function, prepared by {@link #_getSortKey(XdmSequence)}; an empty
   * collation argument means the codepoint collation. The sort is stable, so
   * items with equal keys keep their input order. All keys are computed before
   * sorting, checking for interruption before each.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The sorted items.
   * @throws XPathException
   *         FOCH0002 for an unknown collation, XPTY0004 for incomparable keys.
   */
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

  /**
   * <code>fn:apply($function as function(*), $array as array(*)) as
   * item()*</code> (F&amp;O 3.1 section 16.2.7): call the function with the
   * members of the array as arguments.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The function and the array.
   * @return The result of the call.
   * @throws XPathException
   *         FOAP0001 if the arity of the function differs from the size of the
   *         array.
   */
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

  /**
   * fn:load-xquery-module (F&amp;O 3.1 section 16.3.1): this processor has no
   * XQuery implementation, which the function reports as FOQM0006.
   */
  @NonNull
  private static XdmSequence _loadXQueryModule ()
  {
    throw XPathException.of ("FOQM0006", "No XQuery processor is available to load a library module");
  }

  /**
   * Register the higher-order functions of F&amp;O 3.1 section 16:
   * <code>fn:function-lookup</code> (focus-dependent, since the function items
   * it returns keep the focus of the call), <code>fn:function-name</code>,
   * <code>fn:function-arity</code>, <code>fn:for-each</code>,
   * <code>fn:filter</code>, <code>fn:fold-left</code>,
   * <code>fn:fold-right</code>, <code>fn:for-each-pair</code>,
   * <code>fn:sort</code> (arity 1 to 3), <code>fn:apply</code> and
   * <code>fn:load-xquery-module</code> (arity 1 and 2, always FOQM0006).
   *
   * @param aLib
   *        The library to register into.
   */
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
    aLib.register (sNS, "load-xquery-module", "(xs:string) as map(*)", (aCtx, aArgs) -> _loadXQueryModule ());
    aLib.register (sNS,
                   "load-xquery-module",
                   "(xs:string, map(*)) as map(*)",
                   (aCtx, aArgs) -> _loadXQueryModule ());
  }
}
