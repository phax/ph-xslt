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
package com.helger.xpath.value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.xdm.IXdmItem;

/**
 * Test class for class {@link XdmSequence}, its single-item form and its
 * builders.
 *
 * @author Philip Helger
 */
public final class XdmSequenceTest
{
  @NonNull
  private static XdmAtomic _int (final int n)
  {
    return XdmAtomic.integer (n);
  }

  /** Check every accessor against the expected items. */
  private static void _assertItems (@NonNull final XdmSequence aSeq, @NonNull final IXdmItem... aExpected)
  {
    assertEquals (aExpected.length, aSeq.size ());
    assertEquals (aExpected.length == 0, aSeq.isEmpty ());
    assertEquals (aExpected.length == 1, aSeq.isSingleton ());
    for (int i = 0; i < aExpected.length; ++i)
      assertSame (aExpected[i], aSeq.get (i));
    try
    {
      aSeq.get (aExpected.length);
      fail ();
    }
    catch (final ArrayIndexOutOfBoundsException ex)
    {
      // expected
    }
    if (aExpected.length == 0)
      try
      {
        aSeq.getFirst ();
        fail ();
      }
      catch (final NoSuchElementException ex)
      {
        // expected
      }
    else
      assertSame (aExpected[0], aSeq.getFirst ());
    final ICommonsList <IXdmItem> aList = aSeq.getAsList ();
    assertEquals (aExpected.length, aList.size ());
    // The list is a copy
    aList.add (_int (99));
    assertEquals (aExpected.length, aSeq.size ());
    int nIndex = 0;
    final Iterator <IXdmItem> it = aSeq.iterator ();
    while (it.hasNext ())
      assertSame (aExpected[nIndex++], it.next ());
    assertEquals (aExpected.length, nIndex);
    try
    {
      it.next ();
      fail ();
    }
    catch (final NoSuchElementException ex)
    {
      // expected
    }
  }

  @Test
  public void testFactories ()
  {
    final XdmAtomic a = _int (1);
    final XdmAtomic b = _int (2);
    final XdmAtomic c = _int (3);
    _assertItems (XdmSequence.EMPTY);
    _assertItems (XdmSequence.of (a), a);
    assertSame (XdmSequence.EMPTY, XdmSequence.of (new IXdmItem [0]));
    _assertItems (XdmSequence.of (new IXdmItem [] { a }), a);
    _assertItems (XdmSequence.of (a, b, c), a, b, c);
    assertSame (XdmSequence.EMPTY, XdmSequence.of (List.of ()));
    _assertItems (XdmSequence.of (List.of (b)), b);
    _assertItems (XdmSequence.of (List.of (a, b)), a, b);
    assertEquals (EAtomicType.INTEGER, XdmSequence.of (a).getSingleAtomicType ());
    assertNull (XdmSequence.of (a, b).getSingleAtomicType ());
    assertNull (XdmSequence.EMPTY.getSingleAtomicType ());

    // The array is copied
    final IXdmItem [] aArray = { a, b };
    final XdmSequence aSeq = XdmSequence.of (aArray);
    aArray[0] = c;
    _assertItems (aSeq, a, b);
  }

  @Test
  public void testConcatAndSubSequence ()
  {
    final XdmAtomic a = _int (1);
    final XdmAtomic b = _int (2);
    final XdmAtomic c = _int (3);
    final XdmSequence aSingle = XdmSequence.of (a);
    final XdmSequence aPair = XdmSequence.of (b, c);
    assertSame (XdmSequence.EMPTY, XdmSequence.concat ());
    assertSame (aSingle, XdmSequence.concat (XdmSequence.EMPTY, aSingle));
    _assertItems (XdmSequence.concat (aSingle, aPair), a, b, c);
    _assertItems (XdmSequence.concat (aPair, XdmSequence.EMPTY, aSingle), b, c, a);
    _assertItems (XdmSequence.concat (aSingle, aSingle), a, a);

    final XdmSequence aTriple = XdmSequence.of (a, b, c);
    assertSame (XdmSequence.EMPTY, aTriple.subSequence (2, 2));
    assertSame (aTriple, aTriple.subSequence (0, 3));
    _assertItems (aTriple.subSequence (1, 2), b);
    _assertItems (aTriple.subSequence (1, 3), b, c);
    assertSame (aSingle, aSingle.subSequence (0, 1));
    assertSame (XdmSequence.EMPTY, aSingle.subSequence (1, 1));
    // A range beyond the end is padded with null as Arrays.copyOfRange does
    final XdmSequence aPadded = aTriple.subSequence (2, 4);
    assertEquals (2, aPadded.size ());
    assertSame (c, aPadded.get (0));
    assertNull (aPadded.get (1));
    final XdmSequence aSinglePadded = aSingle.subSequence (0, 2);
    assertEquals (2, aSinglePadded.size ());
    assertSame (a, aSinglePadded.get (0));
    assertNull (aSinglePadded.get (1));
    try
    {
      aSingle.subSequence (2, 3);
      fail ();
    }
    catch (final ArrayIndexOutOfBoundsException ex)
    {
      // expected
    }
  }

  @Test
  public void testBuilder ()
  {
    final XdmAtomic a = _int (1);
    final XdmAtomic b = _int (2);
    final XdmAtomic c = _int (3);
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder (2);
    assertSame (XdmSequence.EMPTY, aBuilder.build ());
    aBuilder.add (a);
    final XdmSequence aOne = aBuilder.build ();
    _assertItems (aOne, a);
    // Exactly full: the buffer is handed over
    aBuilder.add (b);
    final XdmSequence aTwo = aBuilder.build ();
    _assertItems (aTwo, a, b);
    // Further additions copy the buffer first and change no built sequence
    aBuilder.add (c);
    final XdmSequence aThree = aBuilder.build ();
    _assertItems (aOne, a);
    _assertItems (aTwo, a, b);
    _assertItems (aThree, a, b, c);
    assertEquals (3, aBuilder.size ());

    // Hand over, then add within the capacity
    final XdmSequence.Builder aBuilder2 = new XdmSequence.Builder (4);
    aBuilder2.add (a).add (b).add (c).add (a);
    final XdmSequence aFull = aBuilder2.build ();
    aBuilder2.add (b);
    _assertItems (aFull, a, b, c, a);
    _assertItems (aBuilder2.build (), a, b, c, a, b);

    // addAll of single-item and multi-item sequences, also into a shared
    // buffer
    final XdmSequence.Builder aBuilder3 = new XdmSequence.Builder (2);
    aBuilder3.addAll (XdmSequence.EMPTY).addAll (XdmSequence.of (a)).addAll (XdmSequence.of (b));
    final XdmSequence aShared = aBuilder3.build ();
    aBuilder3.addAll (XdmSequence.of (c, a, b));
    _assertItems (aShared, a, b);
    _assertItems (aBuilder3.build (), a, b, c, a, b);
    // addAll into an empty builder shares the array until the next change
    final XdmSequence aSource = XdmSequence.of (a, b, c);
    final XdmSequence.Builder aBuilder5 = new XdmSequence.Builder ();
    aBuilder5.addAll (aSource);
    final XdmSequence aCopy = aBuilder5.build ();
    aBuilder5.add (a);
    _assertItems (aSource, a, b, c);
    _assertItems (aCopy, a, b, c);
    _assertItems (aBuilder5.build (), a, b, c, a);
    final XdmSequence.Builder aBuilder6 = new XdmSequence.Builder ();
    aBuilder6.addAll (aSource).addAll (aSource);
    _assertItems (aSource, a, b, c);
    _assertItems (aBuilder6.build (), a, b, c, a, b, c);
    // Many multi-item sequences after a single item
    final XdmSequence.Builder aBuilder7 = new XdmSequence.Builder ();
    aBuilder7.add (c);
    for (int i = 0; i < 1000; ++i)
      aBuilder7.addAll (aSource);
    final XdmSequence aMany = aBuilder7.build ();
    assertEquals (3001, aMany.size ());
    assertSame (c, aMany.get (0));
    assertSame (a, aMany.get (2998));
    assertSame (c, aMany.get (3000));
    // Many items
    final XdmSequence.Builder aBuilder4 = new XdmSequence.Builder ();
    final ICommonsList <IXdmItem> aExpected = new CommonsArrayList <> ();
    for (int i = 0; i < 100; ++i)
    {
      final XdmAtomic x = _int (i);
      aBuilder4.add (x);
      aExpected.add (x);
    }
    _assertItems (aBuilder4.build (), aExpected.toArray (new IXdmItem [0]));
  }

  @Test
  public void testListBuilder ()
  {
    final XdmAtomic a = _int (1);
    final XdmAtomic b = _int (2);
    final XdmAtomic c = _int (3);
    final XdmSequence.ListBuilder <XdmAtomic> aList = new XdmSequence.ListBuilder <> ();
    assertTrue (aList.isEmpty ());
    assertSame (XdmSequence.EMPTY, aList.build ());
    assertTrue (aList.add (a));
    assertEquals (1, aList.size ());
    assertSame (a, aList.get (0));
    _assertItems (aList.build (), a);
    assertFalse (aList.addAll (List.of ()));
    assertTrue (aList.addAll (List.of (b, c)));
    assertEquals (3, aList.size ());
    assertSame (a, aList.get (0));
    assertSame (c, aList.get (2));
    try
    {
      aList.get (3);
      fail ();
    }
    catch (final IndexOutOfBoundsException ex)
    {
      // expected
    }
    _assertItems (aList.build (), a, b, c);
    assertEquals (List.of (a, b, c), aList);
    // addAll into an empty list takes the array of the collection, later
    // additions do not change a built sequence
    final XdmSequence.ListBuilder <XdmAtomic> aList2 = new XdmSequence.ListBuilder <> ();
    assertTrue (aList2.addAll (new CommonsArrayList <> (a, b)));
    final XdmSequence aBuilt = aList2.build ();
    aList2.add (c);
    aList2.addAll (List.of (a));
    _assertItems (aBuilt, a, b);
    _assertItems (aList2.build (), a, b, c, a);
    final XdmSequence.ListBuilder <XdmAtomic> aList3 = new XdmSequence.ListBuilder <> ();
    aList3.addAll (List.of (c));
    _assertItems (aList3.build (), c);
    try
    {
      aList.add (0, a);
      fail ();
    }
    catch (final UnsupportedOperationException ex)
    {
      // expected
    }
  }

  @Test
  public void testLiteralExpr ()
  {
    final XdmAtomic a = _int (1);
    final IExpr.LiteralExpr aLit = new IExpr.LiteralExpr (a);
    _assertItems (aLit.sequence (), a);
    // The sequence does not take part in equality
    assertEquals (aLit, new IExpr.LiteralExpr (a, XdmSequence.of (a)));
    assertEquals (aLit.hashCode (), new IExpr.LiteralExpr (a).hashCode ());
    assertEquals ("LiteralExpr[value=" + a + "]", aLit.toString ());
    try
    {
      new IExpr.LiteralExpr (a, XdmSequence.of (_int (1)));
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
    try
    {
      new IExpr.LiteralExpr (a, XdmSequence.of (a, a));
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }
}
