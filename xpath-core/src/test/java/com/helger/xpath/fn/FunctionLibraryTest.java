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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FunctionLibrary}.
 *
 * @author Philip Helger
 */
public final class FunctionLibraryTest
{
  private static final XdmQName FN_CONCAT = new XdmQName (XdmConstants.NS_FN, "concat");

  @Test
  public void testVariadicOfParentInChild ()
  {
    final FunctionLibrary aRoot = FunctionLibrary.createDefault ();
    final FunctionLibrary aChild = aRoot.createChild ();
    assertTrue (aRoot.containsName (FN_CONCAT));
    // No arity of the variadic fn:concat was created yet
    assertTrue (aChild.containsName (FN_CONCAT));
    assertTrue (aChild.createChild ().containsName (FN_CONCAT));
    assertNotNull (aChild.getFunction (FN_CONCAT, 5));
    // Hiding single arities leaves the others
    aChild.hide (FN_CONCAT, 2);
    assertNull (aChild.getFunction (FN_CONCAT, 2));
    assertNotNull (aChild.getFunction (FN_CONCAT, 3));
    assertTrue (aChild.containsName (FN_CONCAT));
    assertFalse (aChild.containsName (new XdmQName (XdmConstants.NS_FN, "no-such-function")));
  }

  @Test
  public void testVariadicOfChild ()
  {
    final XdmQName aName = new XdmQName ("urn:test", "v");
    final FunctionLibrary aChild = new FunctionLibrary ().createChild ();
    aChild.registerVariadic ("urn:test", "v", 1, "item()*", "item()*", (aCtx, aArgs) -> XdmSequence.EMPTY);
    assertTrue (aChild.containsName (aName));
    final FunctionLibrary.FunctionDef aDef = aChild.getFunction (aName, 3);
    assertNotNull (aDef);
    assertEquals (3, aDef.getArity ());
    assertSame (aDef, aChild.getFunction (aName, 3));
    assertSame (aDef, aChild.getFunctionForLookup (aName, 3));
    assertNull (aChild.getFunction (aName, 0));
    // Not visible in the parent
    assertNull (new FunctionLibrary ().getFunction (aName, 3));
  }
}
