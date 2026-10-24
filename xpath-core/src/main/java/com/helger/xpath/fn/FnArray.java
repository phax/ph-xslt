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
import java.util.BitSet;
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
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;

/**
 * Function family: the array: namespace functions (F&amp;O 3.1 section 17.3).
 *
 * @author Philip Helger
 */
@Immutable
public final class FnArray
{
  @PresentForCodeCoverage
  private static final FnArray INSTANCE = new FnArray ();

  private FnArray ()
  {}

  private record SortEntry (XdmSequence member, XdmSequence key)
  {}

  @NonNull
  private static XdmArray _array (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    return (XdmArray) aArgs.get (nIndex).get (0);
  }

  @NonNull
  private static BigInteger _integer (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    return ((XdmAtomic) aArgs.get (nIndex).get (0)).getBigInteger ();
  }

  /**
   * @return The 0-based index for a 1-based position that must be in 1..nMax
   */
  private static int _checkPosition (@NonNull final BigInteger aPos, final int nMax)
  {
    if (aPos.signum () <= 0 || aPos.compareTo (BigInteger.valueOf (nMax)) > 0)
      throw XPathException.of ("FOAY0001", "Array position " + aPos + " is out of bounds (1.." + nMax + ")");
    return aPos.intValue () - 1;
  }

  @NonNull
  private static XdmSequence _one (@NonNull final IXdmItem aItem)
  {
    return XdmSequence.of (aItem);
  }

  @NonNull
  private static XdmSequence _subarray (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    final BigInteger aStart = _integer (aArgs, 1);
    final int nSize = aArray.size ();
    final int nStart = _checkPosition (aStart, nSize + 1);
    final int nLength;
    if (aArgs.size () > 2)
    {
      final BigInteger aLength = _integer (aArgs, 2);
      if (aLength.signum () < 0)
        throw XPathException.of ("FOAY0002", "The length " + aLength + " of array:subarray is negative");
      if (aLength.compareTo (BigInteger.valueOf (nSize - nStart)) > 0)
        throw XPathException.of ("FOAY0001", "array:subarray exceeds the array bounds");
      nLength = aLength.intValue ();
    }
    else
      nLength = nSize - nStart;
    return _one (aArray.subArray (nStart, nStart + nLength));
  }

  @NonNull
  private static XdmSequence _remove (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    final BitSet aDrop = new BitSet (aArray.size ());
    for (final IXdmItem aPos : aArgs.get (1))
      aDrop.set (_checkPosition (((XdmAtomic) aPos).getBigInteger (), aArray.size ()));
    if (aDrop.isEmpty ())
      return _one (aArray);
    final XdmArray.Builder aBuilder = new XdmArray.Builder (aArray.size ());
    for (int i = 0; i < aArray.size (); ++i)
      if (!aDrop.get (i))
        aBuilder.add (aArray.get (i));
    return _one (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _join (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aArrays = aArgs.get (0);
    if (aArrays.isSingleton ())
      return aArrays;
    final XdmArray.Builder aBuilder = new XdmArray.Builder ();
    for (final IXdmItem aItem : aArrays)
      aBuilder.addAll ((XdmArray) aItem);
    return _one (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _forEach (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    final IXdmItem aFunc = aArgs.get (1).get (0);
    final XdmArray.Builder aBuilder = new XdmArray.Builder (aArray.size ());
    for (final XdmSequence aMember : aArray.members ())
    {
      aCtx.checkInterrupt ();
      aBuilder.add (XPathEvaluator.callFunction (aCtx, aFunc, List.of (aMember)));
    }
    return _one (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _filter (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    final IXdmItem aFunc = aArgs.get (1).get (0);
    final XdmArray.Builder aBuilder = new XdmArray.Builder (aArray.size ());
    for (final XdmSequence aMember : aArray.members ())
    {
      aCtx.checkInterrupt ();
      final XdmSequence aResult = XPathEvaluator.callFunction (aCtx, aFunc, List.of (aMember));
      if (!aResult.isSingleton () ||
          !(aResult.get (0) instanceof final XdmAtomic aBool) ||
          aBool.getType () != EAtomicType.BOOLEAN)
        throw XPathException.type ("The predicate of array:filter must return a single xs:boolean");
      if (aBool.getBoolean ())
        aBuilder.add (aMember);
    }
    return _one (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _foldLeft (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    final IXdmItem aFunc = aArgs.get (2).get (0);
    XdmSequence aAcc = aArgs.get (1);
    for (final XdmSequence aMember : aArray.members ())
    {
      aCtx.checkInterrupt ();
      aAcc = XPathEvaluator.callFunction (aCtx, aFunc, List.of (aAcc, aMember));
    }
    return aAcc;
  }

  @NonNull
  private static XdmSequence _foldRight (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    final IXdmItem aFunc = aArgs.get (2).get (0);
    XdmSequence aAcc = aArgs.get (1);
    for (int i = aArray.size () - 1; i >= 0; --i)
    {
      aCtx.checkInterrupt ();
      aAcc = XPathEvaluator.callFunction (aCtx, aFunc, List.of (aArray.get (i), aAcc));
    }
    return aAcc;
  }

  @NonNull
  private static XdmSequence _forEachPair (@NonNull final DynamicContext aCtx,
                                           @NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray1 = _array (aArgs, 0);
    final XdmArray aArray2 = _array (aArgs, 1);
    final IXdmItem aFunc = aArgs.get (2).get (0);
    final int nCount = Math.min (aArray1.size (), aArray2.size ());
    final XdmArray.Builder aBuilder = new XdmArray.Builder (nCount);
    for (int i = 0; i < nCount; ++i)
    {
      aCtx.checkInterrupt ();
      aBuilder.add (XPathEvaluator.callFunction (aCtx, aFunc, List.of (aArray1.get (i), aArray2.get (i))));
    }
    return _one (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _getSortKey (@NonNull final XdmSequence aValue)
  {
    final XdmSequence aAtoms = Atomizer.atomize (aValue);
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

  @NonNull
  private static XdmSequence _sort (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmArray aArray = _array (aArgs, 0);
    ICollation aCollation = null;
    if (aArgs.size () >= 2 && !aArgs.get (1).isEmpty ())
    {
      final String sUri = ((XdmAtomic) aArgs.get (1).get (0)).getString ();
      aCollation = CollationRegistry.getCollation (sUri);
      if (aCollation == null)
        throw XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "'");
    }
    else
    {
      final String sDefault = aCtx.getStaticContext ().getDefaultCollation ();
      if (sDefault != null && !sDefault.equals (CollationRegistry.CODEPOINT_URI))
        aCollation = CollationRegistry.getCollation (sDefault);
    }
    final IXdmItem aKeyFunc = aArgs.size () >= 3 ? aArgs.get (2).get (0) : null;
    final ICommonsList <SortEntry> aEntries = new CommonsArrayList <> (aArray.size ());
    for (final XdmSequence aMember : aArray.members ())
    {
      aCtx.checkInterrupt ();
      final XdmSequence aKey = aKeyFunc != null ? XPathEvaluator.callFunction (aCtx, aKeyFunc, List.of (aMember))
                                                : aMember;
      aEntries.add (new SortEntry (aMember, _getSortKey (aKey)));
    }
    final ICollation aFinalCollation = aCollation;
    // List.sort is a stable merge sort
    aEntries.sort ( (a, b) -> _compareKeys (a.key (), b.key (), aFinalCollation, aCtx));
    final XdmArray.Builder aBuilder = new XdmArray.Builder (aEntries.size ());
    for (final SortEntry aEntry : aEntries)
      aBuilder.add (aEntry.member ());
    return _one (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _head (@NonNull final XdmArray aArray)
  {
    if (aArray.isEmpty ())
      throw XPathException.of ("FOAY0001", "array:head of an empty array");
    return aArray.get (0);
  }

  @NonNull
  private static XdmSequence _tail (@NonNull final XdmArray aArray)
  {
    if (aArray.isEmpty ())
      throw XPathException.of ("FOAY0001", "array:tail of an empty array");
    return _one (aArray.subArray (1, aArray.size ()));
  }

  /**
   * Flatten arrays recursively (array:flatten), also used by serialisation.
   *
   * @param aInput
   *        Any sequence.
   * @return The sequence with every array replaced by its flattened members.
   */
  @NonNull
  public static XdmSequence flatten (@NonNull final XdmSequence aInput)
  {
    boolean bAnyArray = false;
    for (final IXdmItem aItem : aInput)
      if (aItem instanceof XdmArray)
      {
        bAnyArray = true;
        break;
      }
    if (!bAnyArray)
      return aInput;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    final ArrayDeque <IXdmItem> aStack = new ArrayDeque <> ();
    for (int i = aInput.size () - 1; i >= 0; --i)
      aStack.push (aInput.get (i));
    while (!aStack.isEmpty ())
    {
      final IXdmItem aItem = aStack.pop ();
      if (aItem instanceof final XdmArray aArray)
      {
        for (int i = aArray.size () - 1; i >= 0; --i)
        {
          final XdmSequence aMember = aArray.get (i);
          for (int j = aMember.size () - 1; j >= 0; --j)
            aStack.push (aMember.get (j));
        }
      }
      else
        aBuilder.add (aItem);
    }
    return aBuilder.build ();
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = FunctionLibrary.NS_ARRAY;
    aLib.register (sNS,
                   "size",
                   "(array(*)) as xs:integer",
                   (aCtx, aArgs) -> _one (XdmAtomic.integer (_array (aArgs, 0).size ())));
    aLib.register (sNS, "get", "(array(*), xs:integer) as item()*", (aCtx, aArgs) -> {
      final XdmArray aArray = _array (aArgs, 0);
      return aArray.get (_checkPosition (_integer (aArgs, 1), aArray.size ()));
    });
    aLib.register (sNS, "put", "(array(*), xs:integer, item()*) as array(*)", (aCtx, aArgs) -> {
      final XdmArray aArray = _array (aArgs, 0);
      return _one (aArray.put (_checkPosition (_integer (aArgs, 1), aArray.size ()), aArgs.get (2)));
    });
    aLib.register (sNS,
                   "append",
                   "(array(*), item()*) as array(*)",
                   (aCtx, aArgs) -> _one (_array (aArgs, 0).append (aArgs.get (1))));
    aLib.register (sNS, "subarray", "(array(*), xs:integer) as array(*)", (aCtx, aArgs) -> _subarray (aArgs));
    aLib.register (sNS,
                   "subarray",
                   "(array(*), xs:integer, xs:integer) as array(*)",
                   (aCtx, aArgs) -> _subarray (aArgs));
    aLib.register (sNS, "remove", "(array(*), xs:integer*) as array(*)", (aCtx, aArgs) -> _remove (aArgs));
    aLib.register (sNS, "insert-before", "(array(*), xs:integer, item()*) as array(*)", (aCtx, aArgs) -> {
      final XdmArray aArray = _array (aArgs, 0);
      return _one (aArray.insertBefore (_checkPosition (_integer (aArgs, 1), aArray.size () + 1), aArgs.get (2)));
    });
    aLib.register (sNS, "head", "(array(*)) as item()*", (aCtx, aArgs) -> _head (_array (aArgs, 0)));
    aLib.register (sNS, "tail", "(array(*)) as array(*)", (aCtx, aArgs) -> _tail (_array (aArgs, 0)));
    aLib.register (sNS, "reverse", "(array(*)) as array(*)", (aCtx, aArgs) -> _one (_array (aArgs, 0).reverse ()));
    aLib.register (sNS, "join", "(array(*)*) as array(*)", (aCtx, aArgs) -> _join (aArgs));
    aLib.register (sNS, "flatten", "(item()*) as item()*", (aCtx, aArgs) -> flatten (aArgs.get (0)));
    aLib.register (sNS, "for-each", "(array(*), function(item()*) as item()*) as array(*)", FnArray::_forEach);
    aLib.register (sNS, "filter", "(array(*), function(item()*) as xs:boolean) as array(*)", FnArray::_filter);
    aLib.register (sNS,
                   "fold-left",
                   "(array(*), item()*, function(item()*, item()*) as item()*) as item()*",
                   FnArray::_foldLeft);
    aLib.register (sNS,
                   "fold-right",
                   "(array(*), item()*, function(item()*, item()*) as item()*) as item()*",
                   FnArray::_foldRight);
    aLib.register (sNS,
                   "for-each-pair",
                   "(array(*), array(*), function(item()*, item()*) as item()*) as array(*)",
                   FnArray::_forEachPair);
    aLib.register (sNS, "sort", "(array(*)) as array(*)", FnArray::_sort);
    aLib.register (sNS, "sort", "(array(*), xs:string?) as array(*)", FnArray::_sort);
    aLib.register (sNS,
                   "sort",
                   "(array(*), xs:string?, function(item()*) as xs:anyAtomicType*) as array(*)",
                   FnArray::_sort);
  }
}
