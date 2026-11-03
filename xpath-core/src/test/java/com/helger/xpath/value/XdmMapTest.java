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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Test class for classes {@link XdmMap} and {@link XdmArray}.
 *
 * @author Philip Helger
 */
public final class XdmMapTest
{
  private static XdmSequence _seq (final String s)
  {
    return XdmSequence.of (XdmAtomic.string (s));
  }

  @Test
  public void testMapBuilder ()
  {
    final XdmMap.Builder aBuilder = new XdmMap.Builder ();
    assertTrue (aBuilder.putIfAbsent (XdmAtomic.integer (1), _seq ("a")));
    // 1.0 is the same key as 1
    assertFalse (aBuilder.putIfAbsent (XdmAtomic.decimal (BigDecimal.ONE), _seq ("b")));
    aBuilder.put (XdmAtomic.string ("k"), _seq ("c"));
    final XdmMap aMap = aBuilder.build ();
    assertEquals (2, aMap.size ());
    assertEquals ("a", ((XdmAtomic) aMap.get (XdmAtomic.dbl (1)).get (0)).getString ());
    assertNull (aMap.get (XdmAtomic.string ("1")));
    assertSame (XdmMap.EMPTY, new XdmMap.Builder ().build ());
  }

  @Test
  public void testMapCopyOnWrite ()
  {
    final XdmMap aMap = XdmMap.of (XdmAtomic.integer (1), _seq ("a"));
    final XdmMap aMap2 = aMap.put (XdmAtomic.integer (2), _seq ("b"))
                             .put (XdmAtomic.decimal (BigDecimal.ONE), _seq ("z"));
    assertEquals (1, aMap.size ());
    assertEquals (2, aMap2.size ());
    // The replaced entry keeps its position but takes the new key
    assertEquals (EAtomicType.DECIMAL, aMap2.getAllKeys ().get (0).getType ());
    assertEquals (1, aMap2.removeAll (XdmSequence.of (XdmAtomic.integer (2), XdmAtomic.integer (3))).size ());
    assertSame (aMap, aMap.remove (XdmAtomic.integer (5)));
  }

  @Test
  public void testArray ()
  {
    final XdmArray aArray = XdmArray.ofItems (XdmSequence.of (XdmAtomic.integer (1), XdmAtomic.integer (2)));
    assertEquals (2, aArray.size ());
    assertEquals (3, aArray.insertBefore (1, _seq ("x")).size ());
    assertEquals ("x", ((XdmAtomic) aArray.insertBefore (1, _seq ("x")).get (1).get (0)).getString ());
    assertEquals (1, aArray.subArray (1, 2).size ());
    assertSame (XdmArray.EMPTY, aArray.subArray (1, 1));
    assertEquals (2, ((XdmAtomic) aArray.reverse ().get (0).get (0)).getBigInteger ().intValue ());
    assertEquals (4, new XdmArray.Builder ().addAll (aArray).addAll (aArray).build ().size ());
  }

  private static void _assertSameContent (@NonNull final Map <String, String> aExpected, @NonNull final XdmMap aMap)
  {
    assertEquals (aExpected.size (), aMap.size ());
    final ICommonsList <String> aKeys = new CommonsArrayList <> ();
    for (final XdmMap.Entry aEntry : aMap.entries ())
    {
      aKeys.add (aEntry.key ().getString ());
      assertEquals (aExpected.get (aEntry.key ().getString ()), ((XdmAtomic) aEntry.value ().get (0)).getString ());
    }
    assertEquals (new CommonsArrayList <> (aExpected.keySet ()), aKeys);
    for (final var aEntry : aExpected.entrySet ())
      assertEquals (aEntry.getValue (),
                    ((XdmAtomic) aMap.get (XdmAtomic.string (aEntry.getKey ())).get (0)).getString ());
  }

  @Test
  public void testMapAgainstModel ()
  {
    // The first keys have the same hash code, the others are spread
    final ICommonsList <String> aPool = new CommonsArrayList <> ("Aa", "BB", "AaAa", "AaBB", "BBAa", "BBBB");
    for (int i = 0; i < 300; ++i)
      aPool.add ("key" + i);
    final Random aRandom = new Random (42);
    final Map <String, String> aModel = new LinkedHashMap <> ();
    XdmMap aMap = XdmMap.EMPTY;
    final ICommonsList <Map <String, String>> aOldModels = new CommonsArrayList <> ();
    final ICommonsList <XdmMap> aOldMaps = new CommonsArrayList <> ();
    for (int i = 0; i < 20_000; ++i)
    {
      final String sKey = aPool.get (aRandom.nextInt (aPool.size ()));
      if (aRandom.nextInt (3) == 0)
      {
        aModel.remove (sKey);
        aMap = aMap.remove (XdmAtomic.string (sKey));
      }
      else
      {
        aModel.put (sKey, "v" + i);
        aMap = aMap.put (XdmAtomic.string (sKey), _seq ("v" + i));
      }
      if (i % 1_000 == 0)
      {
        aOldModels.add (new LinkedHashMap <> (aModel));
        aOldMaps.add (aMap);
        _assertSameContent (aModel, aMap);
      }
    }
    _assertSameContent (aModel, aMap);
    // Every earlier version is unchanged, also the ones with their ordered
    // view built before the later changes
    for (int i = 0; i < aOldMaps.size (); ++i)
      _assertSameContent (aOldModels.get (i), aOldMaps.get (i));
    final XdmMap.Builder aBuilder = new XdmMap.Builder (aMap);
    aBuilder.put (XdmAtomic.string ("new"), _seq ("n"));
    aModel.put ("new", "n");
    _assertSameContent (aModel, aBuilder.build ());
  }

  @Test
  public void testMapRemoveKeepsOrder ()
  {
    final XdmMap aMap = XdmMap.EMPTY.put (XdmAtomic.string ("a"), _seq ("1"))
                                    .put (XdmAtomic.string ("b"), _seq ("2"))
                                    .put (XdmAtomic.string ("c"), _seq ("3"))
                                    .remove (XdmAtomic.string ("a"))
                                    .put (XdmAtomic.string ("a"), _seq ("4"));
    assertEquals ("b", aMap.getAllKeys ().get (0).getString ());
    assertEquals ("c", aMap.getAllKeys ().get (1).getString ());
    assertEquals ("a", aMap.getAllKeys ().get (2).getString ());
    assertSame (XdmMap.EMPTY, XdmMap.of (XdmAtomic.string ("a"), _seq ("1")).remove (XdmAtomic.string ("a")));
  }

  @Test
  public void testMapUniformTypes ()
  {
    final XdmMap aMap = XdmMap.of (XdmAtomic.string ("a"), XdmSequence.of (XdmAtomic.integer (1)))
                              .put (XdmAtomic.string ("b"), XdmSequence.of (XdmAtomic.integer (2)));
    assertNull (XdmMap.EMPTY.getUniformKeyType ());
    assertEquals (EAtomicType.STRING, aMap.getUniformKeyType ());
    assertEquals (EAtomicType.INTEGER, aMap.getUniformValueType ());
    // Replacing an entry changes the type just as adding one does
    final XdmMap aOtherKey = aMap.put (XdmAtomic.untyped ("a"), XdmSequence.of (XdmAtomic.integer (1)));
    assertNull (aOtherKey.getUniformKeyType ());
    assertEquals (EAtomicType.INTEGER, aOtherKey.getUniformValueType ());
    assertNull (aMap.put (XdmAtomic.string ("c"), _seq ("x")).getUniformValueType ());
    // More than one item, or no atomic value
    assertNull (aMap.put (XdmAtomic.string ("c"), XdmSequence.EMPTY).getUniformValueType ());
    assertNull (aMap.put (XdmAtomic.string ("c"),
                          XdmSequence.of (XdmAtomic.integer (1), XdmAtomic.integer (2))).getUniformValueType ());
    assertNull (XdmMap.of (XdmAtomic.string ("a"), XdmSequence.of (XdmMap.EMPTY)).getUniformValueType ());
    // The builder gives the same summary
    assertEquals (EAtomicType.INTEGER, new XdmMap.Builder (aMap).build ().getUniformValueType ());
    assertNull (new XdmMap.Builder (aMap).put (XdmAtomic.integer (1), XdmSequence.of (XdmAtomic.integer (1)))
                                         .build ()
                                         .getUniformKeyType ());
    // A type is never restored after the entry was removed
    assertNull (aMap.put (XdmAtomic.integer (1), XdmSequence.EMPTY)
                    .remove (XdmAtomic.integer (1))
                    .getUniformKeyType ());
    // An empty map starts over
    assertEquals (EAtomicType.DECIMAL,
                  XdmMap.of (XdmAtomic.string ("a"), XdmSequence.EMPTY)
                        .remove (XdmAtomic.string ("a"))
                        .put (XdmAtomic.decimal (BigDecimal.ONE), XdmSequence.EMPTY)
                        .getUniformKeyType ());
  }

  @Test
  public void testArrayAppendSharesBackingArray ()
  {
    XdmArray aArray = XdmArray.EMPTY;
    final ICommonsList <XdmArray> aVersions = new CommonsArrayList <> ();
    for (int i = 0; i < 100; ++i)
    {
      aArray = aArray.append (_seq ("m" + i));
      aVersions.add (aArray);
    }
    for (int i = 0; i < 100; ++i)
    {
      final XdmArray aVersion = aVersions.get (i);
      assertEquals (i + 1, aVersion.size ());
      assertEquals (i + 1, aVersion.members ().size ());
      assertEquals (i + 1, aVersion.getAllMembers ().size ());
      assertEquals ("m" + i, ((XdmAtomic) aVersion.get (i).get (0)).getString ());
    }
    // Appending to an older version must not touch the slot of a newer one
    final XdmArray aBranch = aVersions.get (49).append (_seq ("branch"));
    assertEquals ("branch", ((XdmAtomic) aBranch.get (50).get (0)).getString ());
    assertEquals ("m50", ((XdmAtomic) aVersions.get (50).get (50).get (0)).getString ());
    assertEquals ("m50", ((XdmAtomic) aArray.get (50).get (0)).getString ());
    // No member beyond the size, although the backing array has more slots
    final XdmArray aShort = XdmArray.EMPTY.append (_seq ("a"));
    try
    {
      aShort.get (1);
      fail ();
    }
    catch (final IndexOutOfBoundsException ex)
    {
      // Expected
    }
    assertEquals (1, aShort.reverse ().size ());
    assertEquals (2, aShort.insertBefore (1, _seq ("b")).size ());
    assertEquals (1, aShort.put (0, _seq ("c")).size ());
    assertEquals (1, aShort.subArray (0, 1).size ());
  }

  @Test
  public void testArrayAppendConcurrently () throws Exception
  {
    final XdmArray aBase = XdmArray.EMPTY.append (_seq ("base"));
    final int nThreads = 8;
    final ICommonsList <Future <XdmArray>> aResults = new CommonsArrayList <> ();
    final ExecutorService aExecutor = Executors.newFixedThreadPool (nThreads);
    try
    {
      final CountDownLatch aStart = new CountDownLatch (nThreads);
      for (int i = 0; i < nThreads; ++i)
      {
        final String sValue = "t" + i;
        aResults.add (aExecutor.submit ( () -> {
          aStart.countDown ();
          aStart.await ();
          return aBase.append (_seq (sValue));
        }));
      }
      for (int i = 0; i < nThreads; ++i)
        assertEquals ("t" + i, ((XdmAtomic) aResults.get (i).get ().get (1).get (0)).getString ());
    }
    finally
    {
      aExecutor.shutdown ();
    }
  }
}
