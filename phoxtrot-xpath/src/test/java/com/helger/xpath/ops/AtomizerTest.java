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

import static com.helger.xpath.ops.OpsTestHelper.FUNCTION;
import static com.helger.xpath.ops.OpsTestHelper.assertError;
import static com.helger.xpath.ops.OpsTestHelper.dbl;
import static com.helger.xpath.ops.OpsTestHelper.dec;
import static com.helger.xpath.ops.OpsTestHelper.flt;
import static com.helger.xpath.ops.OpsTestHelper.i;
import static com.helger.xpath.ops.OpsTestHelper.str;
import static com.helger.xpath.ops.OpsTestHelper.untyped;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link Atomizer}.
 *
 * @author Philip Helger
 */
public final class AtomizerTest
{
  private static XdmSequence _seq (final IXdmItem... aItems)
  {
    return XdmSequence.of (aItems);
  }

  private static XdmArray _array (final XdmSequence... aMembers)
  {
    return XdmArray.of (List.of (aMembers));
  }

  private static IXdmNode _doc ()
  {
    final IXdmNode aDoc = IXdmNode.createDocument ();
    final IXdmNode aRoot = IXdmNode.createElement (new XdmQName ("a"));
    aRoot.addAttribute (IXdmNode.createAttribute (new XdmQName ("att"), "v"));
    aRoot.appendChild (IXdmNode.createText ("hello "));
    final IXdmNode aB = IXdmNode.createElement (new XdmQName ("b"));
    aB.appendChild (IXdmNode.createText ("world"));
    aRoot.appendChild (aB);
    aRoot.appendChild (IXdmNode.createComment ("c"));
    aRoot.appendChild (IXdmNode.createProcessingInstruction ("pi", "data"));
    aDoc.appendChild (aRoot);
    return aDoc;
  }

  private static void _assertAtomic (final EAtomicType eType, final String sLexical, final IXdmItem aItem)
  {
    final XdmAtomic a = (XdmAtomic) aItem;
    assertSame (eType, a.getType ());
    assertEquals (sLexical, a.getLexical ());
  }

  @Test
  public void testAtomizeAtomic ()
  {
    final XdmSequence aSeq = _seq (i (1), str ("a"));
    assertSame (aSeq, Atomizer.atomize (aSeq));
    assertSame (XdmSequence.EMPTY, Atomizer.atomize (XdmSequence.EMPTY));
  }

  @Test
  public void testAtomizeNodes ()
  {
    final IXdmNode aDoc = _doc ();
    final IXdmNode aRoot = aDoc.getChildAtIndex (0);
    final XdmSequence aRes = Atomizer.atomize (_seq (i (7),
                                                     aDoc,
                                                     aRoot,
                                                     aRoot.getAttributeAtIndex (0),
                                                     aRoot.getChildAtIndex (0),
                                                     aRoot.getChildAtIndex (2),
                                                     aRoot.getChildAtIndex (3)));
    assertEquals (7, aRes.size ());
    _assertAtomic (EAtomicType.INTEGER, "7", aRes.get (0));
    _assertAtomic (EAtomicType.UNTYPED_ATOMIC, "hello world", aRes.get (1));
    _assertAtomic (EAtomicType.UNTYPED_ATOMIC, "hello world", aRes.get (2));
    _assertAtomic (EAtomicType.UNTYPED_ATOMIC, "v", aRes.get (3));
    _assertAtomic (EAtomicType.UNTYPED_ATOMIC, "hello ", aRes.get (4));
    // comments and processing instructions give xs:string
    _assertAtomic (EAtomicType.STRING, "c", aRes.get (5));
    _assertAtomic (EAtomicType.STRING, "data", aRes.get (6));
  }

  @Test
  public void testAtomizeArrays ()
  {
    final XdmArray aInner = _array (_seq (i (2)), XdmSequence.EMPTY);
    final XdmArray aOuter = _array (_seq (i (1), aInner), _seq (_array (_seq (i (3), i (4)))));
    final XdmSequence aRes = Atomizer.atomize (_seq (aOuter, i (5)));
    assertEquals (5, aRes.size ());
    for (int n = 0; n < 5; ++n)
      _assertAtomic (EAtomicType.INTEGER, Integer.toString (n + 1), aRes.get (n));
    assertSame (XdmSequence.EMPTY, Atomizer.atomize (_seq (XdmArray.EMPTY)));
  }

  @Test
  public void testAtomizeDeepArray ()
  {
    XdmArray aArray = _array (_seq (i (42)));
    for (int n = 0; n < 100_000; ++n)
      aArray = _array (_seq (aArray));
    final XdmSequence aRes = Atomizer.atomize (_seq (aArray));
    assertEquals (1, aRes.size ());
    _assertAtomic (EAtomicType.INTEGER, "42", aRes.get (0));
  }

  @Test
  public void testAtomizeErrors ()
  {
    assertError ("FOTY0013", () -> Atomizer.atomize (_seq (XdmMap.EMPTY)));
    assertError ("FOTY0013", () -> Atomizer.atomize (_seq (i (1), FUNCTION)));
    assertError ("FOTY0013", () -> Atomizer.atomize (_seq (_array (_seq (FUNCTION)))));
  }

  @Test
  public void testTypedValue ()
  {
    _assertAtomic (EAtomicType.UNTYPED_ATOMIC, "t", Atomizer.getTypedValue (IXdmNode.createText ("t")));
    _assertAtomic (EAtomicType.STRING, "c", Atomizer.getTypedValue (IXdmNode.createComment ("c")));
    _assertAtomic (EAtomicType.UNTYPED_ATOMIC,
                   "",
                   Atomizer.getTypedValue (IXdmNode.createElement (new XdmQName ("e"))));
  }

  @Test
  public void testEffectiveBooleanValue ()
  {
    final IXdmNode aElem = IXdmNode.createElement (new XdmQName ("e"));
    assertFalse (Atomizer.getEffectiveBooleanValue (XdmSequence.EMPTY));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (aElem)));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (aElem, aElem)));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (aElem, i (0))));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (XdmAtomic.TRUE)));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (XdmAtomic.FALSE)));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (str (""))));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (str ("false"))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (untyped (""))));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (XdmAtomic.anyUri ("x"))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (XdmAtomic.anyUri (""))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (i (0))));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (i (-1))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (dec ("0.000"))));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (dec ("0.001"))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (dbl (Double.NaN))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (dbl (-0.0))));
    assertFalse (Atomizer.getEffectiveBooleanValue (_seq (flt (Float.NaN))));
    assertTrue (Atomizer.getEffectiveBooleanValue (_seq (flt (1e-30f))));

    assertError ("FORG0006", () -> Atomizer.getEffectiveBooleanValue (_seq (i (1), i (2))));
    assertError ("FORG0006", () -> Atomizer.getEffectiveBooleanValue (_seq (i (1), aElem)));
    assertError ("FORG0006",
                 () -> Atomizer.getEffectiveBooleanValue (_seq (XdmAtomic.qname (new XdmQName ("q")))));
    assertError ("FORG0006",
                 () -> Atomizer.getEffectiveBooleanValue (_seq (AtomicCaster.cast (str ("01"),
                                                                                    EAtomicType.HEX_BINARY,
                                                                                    null))));
    assertError ("FORG0006", () -> Atomizer.getEffectiveBooleanValue (_seq (XdmMap.EMPTY)));
    assertError ("FORG0006", () -> Atomizer.getEffectiveBooleanValue (_seq (XdmArray.EMPTY)));
    assertError ("FORG0006", () -> Atomizer.getEffectiveBooleanValue (_seq (FUNCTION)));
  }

  @Test
  public void testStringValue ()
  {
    assertEquals ("1.0E6", Atomizer.getStringValue (dbl (1e6)));
    assertEquals ("hello world", Atomizer.getStringValue (_doc ()));
    assertEquals ("c", Atomizer.getStringValue (IXdmNode.createComment ("c")));
    assertError ("FOTY0014", () -> Atomizer.getStringValue (XdmMap.EMPTY));
    assertError ("FOTY0014", () -> Atomizer.getStringValue (XdmArray.EMPTY));
    assertError ("FOTY0014", () -> Atomizer.getStringValue (FUNCTION));
  }
}
