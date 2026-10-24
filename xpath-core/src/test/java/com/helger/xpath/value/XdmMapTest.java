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

import java.math.BigDecimal;

import org.junit.Test;

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
}
