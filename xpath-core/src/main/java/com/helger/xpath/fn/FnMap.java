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

import java.util.ArrayDeque;
import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;

/**
 * Function family: the map: namespace functions (F&amp;O 3.1 section 17.1).
 *
 * @author Philip Helger
 */
@Immutable
public final class FnMap
{
  @PresentForCodeCoverage
  private static final FnMap INSTANCE = new FnMap ();

  private FnMap ()
  {}

  @NonNull
  private static XdmMap _map (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    return (XdmMap) aArgs.get (nIndex).get (0);
  }

  @NonNull
  private static XdmAtomic _atomic (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    return (XdmAtomic) aArgs.get (nIndex).get (0);
  }

  @NonNull
  private static XdmSequence _merge (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    String sDuplicates = "use-first";
    if (aArgs.size () > 1)
    {
      final XdmSequence aValue = FnOptionMap.getOption (_map (aArgs, 1), "duplicates", FnOptionMap.STRING, aCtx);
      if (aValue != null)
        sDuplicates = ((XdmAtomic) aValue.get (0)).getString ();
    }
    switch (sDuplicates)
    {
      case "reject":
      case "use-first":
      case "use-last":
      case "use-any":
      case "combine":
        break;
      default:
        throw XPathException.of ("FOJS0005", "Invalid value '" + sDuplicates + "' for option 'duplicates'");
    }
    final XdmSequence aMaps = aArgs.get (0);
    if (aMaps.isEmpty ())
      return XdmSequence.of (XdmMap.EMPTY);
    if (aMaps.isSingleton ())
      return aMaps;
    final XdmMap.Builder aBuilder = new XdmMap.Builder ();
    for (final IXdmItem aItem : aMaps)
    {
      aCtx.checkInterrupt ();
      for (final XdmMap.Entry aEntry : ((XdmMap) aItem).entries ())
      {
        final XdmSequence aOld = aBuilder.get (aEntry.key ());
        if (aOld == null)
          aBuilder.put (aEntry.key (), aEntry.value ());
        else
          switch (sDuplicates)
          {
            case "reject":
              throw XPathException.of ("FOJS0003", "Duplicate key '" + aEntry.key ().getLexical () + "' in map:merge");
            case "use-last":
              aBuilder.put (aEntry.key (), aEntry.value ());
              break;
            case "combine":
              aBuilder.put (aEntry.key (), XdmSequence.concat (aOld, aEntry.value ()));
              break;
            default:
              // use-first, use-any: keep the first
              break;
          }
      }
    }
    return XdmSequence.of (aBuilder.build ());
  }

  @NonNull
  private static XdmSequence _find (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic aKey = _atomic (aArgs, 1);
    final XdmArray.Builder aResult = new XdmArray.Builder ();
    // Pre-order depth-first walk with an explicit stack of pending items
    final ArrayDeque <IXdmItem> aStack = new ArrayDeque <> ();
    final XdmSequence aInput = aArgs.get (0);
    for (int i = aInput.size () - 1; i >= 0; --i)
      aStack.push (aInput.get (i));
    int nSteps = 0;
    while (!aStack.isEmpty ())
    {
      if ((++nSteps & 0x3ff) == 0)
        aCtx.checkInterrupt ();
      final IXdmItem aItem = aStack.pop ();
      if (aItem instanceof final XdmMap aMap)
      {
        final XdmSequence aValue = aMap.get (aKey);
        if (aValue != null)
          aResult.add (aValue);
        final List <XdmMap.Entry> aEntries = aMap.getAllEntries ();
        for (int i = aEntries.size () - 1; i >= 0; --i)
        {
          final XdmSequence aEntryValue = aEntries.get (i).value ();
          for (int j = aEntryValue.size () - 1; j >= 0; --j)
            aStack.push (aEntryValue.get (j));
        }
      }
      else
        if (aItem instanceof final XdmArray aArray)
        {
          for (int i = aArray.size () - 1; i >= 0; --i)
          {
            final XdmSequence aMember = aArray.get (i);
            for (int j = aMember.size () - 1; j >= 0; --j)
              aStack.push (aMember.get (j));
          }
        }
    }
    return XdmSequence.of (aResult.build ());
  }

  @NonNull
  private static XdmSequence _forEach (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmMap aMap = _map (aArgs, 0);
    final IXdmItem aFunc = aArgs.get (1).get (0);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final XdmMap.Entry aEntry : aMap.entries ())
    {
      aCtx.checkInterrupt ();
      aBuilder.addAll (XPathEvaluator.callFunction (aCtx,
                                                    aFunc,
                                                    List.of (XdmSequence.of (aEntry.key ()), aEntry.value ())));
    }
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _keys (@NonNull final XdmMap aMap)
  {
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final XdmMap.Entry aEntry : aMap.entries ())
      aBuilder.add (aEntry.key ());
    return aBuilder.build ();
  }

  @NonNull
  private static XdmSequence _get (@NonNull final XdmMap aMap, @NonNull final XdmAtomic aKey)
  {
    final XdmSequence ret = aMap.get (aKey);
    return ret == null ? XdmSequence.EMPTY : ret;
  }

  @NonNull
  private static XdmSequence _one (@NonNull final IXdmItem aItem)
  {
    return XdmSequence.of (aItem);
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = FunctionLibrary.NS_MAP;
    aLib.register (sNS, "merge", "(map(*)*) as map(*)", FnMap::_merge);
    aLib.register (sNS, "merge", "(map(*)*, map(*)) as map(*)", FnMap::_merge);
    aLib.register (sNS,
                   "size",
                   "(map(*)) as xs:integer",
                   (aCtx, aArgs) -> _one (XdmAtomic.integer (_map (aArgs, 0).size ())));
    aLib.register (sNS, "keys", "(map(*)) as xs:anyAtomicType*", (aCtx, aArgs) -> _keys (_map (aArgs, 0)));
    aLib.register (sNS,
                   "contains",
                   "(map(*), xs:anyAtomicType) as xs:boolean",
                   (aCtx, aArgs) -> _one (XdmAtomic.bool (_map (aArgs, 0).containsKey (_atomic (aArgs, 1)))));
    aLib.register (sNS,
                   "get",
                   "(map(*), xs:anyAtomicType) as item()*",
                   (aCtx, aArgs) -> _get (_map (aArgs, 0), _atomic (aArgs, 1)));
    aLib.register (sNS, "find", "(item()*, xs:anyAtomicType) as array(*)", FnMap::_find);
    aLib.register (sNS,
                   "put",
                   "(map(*), xs:anyAtomicType, item()*) as map(*)",
                   (aCtx, aArgs) -> _one (_map (aArgs, 0).put (_atomic (aArgs, 1), aArgs.get (2))));
    aLib.register (sNS,
                   "entry",
                   "(xs:anyAtomicType, item()*) as map(*)",
                   (aCtx, aArgs) -> _one (XdmMap.of (_atomic (aArgs, 0), aArgs.get (1))));
    aLib.register (sNS,
                   "remove",
                   "(map(*), xs:anyAtomicType*) as map(*)",
                   (aCtx, aArgs) -> _one (_map (aArgs, 0).removeAll (aArgs.get (1))));
    aLib.register (sNS,
                   "for-each",
                   "(map(*), function(xs:anyAtomicType, item()*) as item()*) as item()*",
                   FnMap::_forEach);
  }
}
