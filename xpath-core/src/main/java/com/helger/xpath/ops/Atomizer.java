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
package com.helger.xpath.ops;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Iterator;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * fn:data and effective boolean value. Owner: atomic-values workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class Atomizer
{
  @PresentForCodeCoverage
  private static final Atomizer INSTANCE = new Atomizer ();

  private Atomizer ()
  {}

  @NonNull
  private static String _describe (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof XdmMap)
      return "a map";
    if (aItem instanceof XdmArray)
      return "an array";
    if (aItem instanceof IXdmFunction)
      return "a function item";
    return aItem.getClass ().getSimpleName ();
  }

  private static void _atomizeNonArray (@NonNull final IXdmItem aItem, final XdmSequence.@NonNull Builder aBuilder)
  {
    if (aItem instanceof final XdmAtomic aAtomic)
      aBuilder.add (aAtomic);
    else
      if (aItem instanceof final XdmNode aNode)
        aBuilder.add (getTypedValue (aNode));
      else
        throw XPathException.of ("FOTY0013", "Cannot atomize " + _describe (aItem));
  }

  /**
   * Add the atomised form of one item; arrays are flattened without recursion.
   */
  private static void _atomizeItem (@NonNull final IXdmItem aItem, final XdmSequence.@NonNull Builder aBuilder)
  {
    if (!(aItem instanceof final XdmArray aArray))
    {
      _atomizeNonArray (aItem, aBuilder);
      return;
    }
    // Explicit stack of member iterators: the nesting depth is input
    // controlled
    final ArrayDeque <Iterator <IXdmItem>> aStack = new ArrayDeque <> ();
    for (int i = aArray.size () - 1; i >= 0; --i)
      aStack.push (aArray.get (i).iterator ());
    while (!aStack.isEmpty ())
    {
      final Iterator <IXdmItem> aIt = aStack.peek ();
      if (!aIt.hasNext ())
      {
        aStack.pop ();
        continue;
      }
      final IXdmItem aCur = aIt.next ();
      if (aCur instanceof final XdmArray aInner)
      {
        for (int i = aInner.size () - 1; i >= 0; --i)
          aStack.push (aInner.get (i).iterator ());
      }
      else
        _atomizeNonArray (aCur, aBuilder);
    }
  }

  /**
   * Atomise a sequence: nodes give their typed value (xs:untypedAtomic for
   * documents, elements, attributes and text without schema; xs:string for
   * comments, processing instructions and namespace nodes), arrays are
   * flattened recursively, atomic values stay.
   *
   * @param aSeq
   *        The sequence to atomise.
   * @return A sequence of {@link XdmAtomic} only. The input itself if it is
   *         already atomic.
   * @throws com.helger.xpath.XPathException
   *         FOTY0013 for maps and function items.
   */
  @NonNull
  public static XdmSequence atomize (@NonNull final XdmSequence aSeq)
  {
    final int nSize = aSeq.size ();
    int nFirstNonAtomic = 0;
    while (nFirstNonAtomic < nSize && aSeq.get (nFirstNonAtomic) instanceof XdmAtomic)
      nFirstNonAtomic++;
    if (nFirstNonAtomic == nSize)
      return aSeq;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nFirstNonAtomic; ++i)
      aBuilder.add (aSeq.get (i));
    for (int i = nFirstNonAtomic; i < nSize; ++i)
      _atomizeItem (aSeq.get (i), aBuilder);
    return aBuilder.build ();
  }

  /**
   * Typed value of a single node without schema information: xs:string for
   * comments, processing instructions and namespace nodes, otherwise
   * xs:untypedAtomic of the string value.
   *
   * @param aNode
   *        The node.
   * @return The typed value.
   */
  @NonNull
  public static XdmAtomic getTypedValue (@NonNull final XdmNode aNode)
  {
    return switch (aNode.getKind ())
    {
      case COMMENT, PROCESSING_INSTRUCTION, NAMESPACE -> XdmAtomic.string (aNode.getStringValue ());
      default -> XdmAtomic.untyped (aNode.getStringValue ());
    };
  }

  /**
   * @param aSeq
   *        Any sequence.
   * @return The effective boolean value (XPath 3.1 section 2.4.3).
   * @throws com.helger.xpath.XPathException
   *         FORG0006 where the EBV is not defined.
   */
  public static boolean getEffectiveBooleanValue (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return false;
    final IXdmItem aFirst = aSeq.get (0);
    if (aFirst instanceof XdmNode)
      return true;
    if (aSeq.size () > 1)
      throw XPathException.of ("FORG0006",
                               "The effective boolean value of a sequence of " +
                                           aSeq.size () +
                                           " items starting with an atomic value or function is not defined");
    if (aFirst instanceof final XdmAtomic aAtomic)
    {
      final EAtomicType eType = aAtomic.getType ();
      if (eType == EAtomicType.BOOLEAN)
        return aAtomic.getBoolean ();
      if (aAtomic.isStringLike ())
        return !aAtomic.getString ().isEmpty ();
      if (eType.isNumeric ())
      {
        final Object aPayload = aAtomic.getValue ();
        if (aPayload instanceof final BigInteger aBI)
          return aBI.signum () != 0;
        if (aPayload instanceof final BigDecimal aBD)
          return aBD.signum () != 0;
        final double d = aAtomic.getAsDouble ();
        return d != 0 && !Double.isNaN (d);
      }
      throw XPathException.of ("FORG0006",
                               "The effective boolean value of a value of type " +
                                           eType.getQualifiedName () +
                                           " is not defined");
    }
    throw XPathException.of ("FORG0006", "The effective boolean value of " + _describe (aFirst) + " is not defined");
  }

  /**
   * @param aItem
   *        The item.
   * @return The string value of an item as fn:string defines it.
   * @throws com.helger.xpath.XPathException
   *         FOTY0014 for function items, maps and arrays.
   */
  @NonNull
  public static String getStringValue (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmAtomic aAtomic)
      return aAtomic.getLexical ();
    if (aItem instanceof final XdmNode aNode)
      return aNode.getStringValue ();
    throw XPathException.of ("FOTY0014", "The string value of " + _describe (aItem) + " is not defined");
  }
}
