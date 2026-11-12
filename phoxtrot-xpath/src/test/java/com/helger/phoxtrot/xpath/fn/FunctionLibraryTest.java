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
package com.helger.phoxtrot.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

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

  private static void _assertReadOnly (final Runnable aChange)
  {
    try
    {
      aChange.run ();
      fail ("A frozen library must refuse the change");
    }
    catch (final IllegalStateException ex)
    {
      // expected
    }
  }

  @Test
  public void testSharedDefaultIsReadOnly ()
  {
    final FunctionLibrary aDefault = FunctionLibrary.getDefault ();
    assertSame (aDefault, FunctionLibrary.getDefault ());
    final XdmQName aName = new XdmQName ("urn:test", "f");
    final long nStamp = aDefault.getModificationStamp ();
    final IXPathFunction aImpl = (aCtx, aArgs) -> XdmSequence.EMPTY;
    _assertReadOnly ( () -> aDefault.register ("urn:test", "f", "() as item()*", aImpl));
    _assertReadOnly ( () -> aDefault.register ("urn:test", "f", "() as item()*", aImpl, true, aImpl));
    _assertReadOnly ( () -> aDefault.registerVariadic ("urn:test", "f", 1, "item()*", "item()*", aImpl));
    _assertReadOnly ( () -> aDefault.hide (FN_CONCAT, 2));
    _assertReadOnly ( () -> aDefault.hideFromLookup (FN_CONCAT, 2));
    final FunctionLibrary.FunctionDef aConcat2 = aDefault.getFunction (FN_CONCAT, 2);
    assertNotNull (aConcat2);
    _assertReadOnly ( () -> aDefault.registerParsed (aName,
                                                     aConcat2.getParamTypes (),
                                                     aConcat2.getReturnType (),
                                                     aImpl,
                                                     false));
    _assertReadOnly ( () -> aDefault.registerForLookup (aName,
                                                        aConcat2.getParamTypes (),
                                                        aConcat2.getReturnType (),
                                                        aImpl,
                                                        false));
    // The on-demand arities of fn:concat are still created, without changing the stamp
    assertNotNull (aDefault.getFunction (FN_CONCAT, 17));
    assertEquals (nStamp, aDefault.getModificationStamp ());
    assertNull (aDefault.getFunction (aName, 0));

    // A child takes the changes and shadows the default
    final FunctionLibrary aChild = aDefault.createChild ();
    aChild.registerParsed (FN_CONCAT, aConcat2.getParamTypes (), aConcat2.getReturnType (), aImpl, false);
    assertSame (aImpl, aChild.getFunction (FN_CONCAT, 2).getImpl ());
    assertSame (aConcat2, aDefault.getFunction (FN_CONCAT, 2));
    // A library of createDefault stays writable
    FunctionLibrary.createDefault ().hide (FN_CONCAT, 2);
    assertTrue (aDefault.isFrozen ());
  }

  @NonNull
  private static String _evalExt (@NonNull final FunctionLibrary aLib, @NonNull final String sExpr)
  {
    final StaticContext aSC = new StaticContext ().setFunctionLibrary (aLib).declareNamespace ("ext", "urn:ext");
    return Atomizer.getStringValue (XPathCompiler.compile (sExpr, aSC).evaluate (new DynamicContext (aSC)).get (0));
  }

  @NonNull
  private static String _evalExtError (@NonNull final FunctionLibrary aLib, @NonNull final String sExpr)
  {
    try
    {
      _evalExt (aLib, sExpr);
      fail (sExpr);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  @Test
  public void testTypedRegistration ()
  {
    final SeqType aDouble = SeqType.of (ItemType.atomic (EAtomicType.DOUBLE), EOccurrence.ONE);
    final FunctionLibrary aLib = FunctionLibrary.getDefault ().createChild ();
    aLib.register (new XdmQName ("urn:ext", "twice"),
                   List.of (aDouble),
                   aDouble,
                   (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.dbl (((XdmAtomic) aArgs.get (0).get (0)).getAsDouble () * 2)));
    final FunctionLibrary.FunctionDef aDef = aLib.getFunction (new XdmQName ("urn:ext", "twice"), 1);
    assertNotNull (aDef);
    assertFalse (aDef.isFocusDependent ());
    // Numeric promotion, untyped value cast, atomisation of a node without type
    assertEquals ("4", _evalExt (aLib, "ext:twice(2)"));
    assertEquals ("7", _evalExt (aLib, "ext:twice(xs:untypedAtomic('3.5'))"));
    assertEquals ("10", _evalExt (aLib, "ext:twice(parse-xml('<a>5</a>')/a)"));
    // Wrong type, wrong cardinality, untyped value that is no number
    assertEquals ("XPTY0004", _evalExtError (aLib, "ext:twice('3')"));
    assertEquals ("XPTY0004", _evalExtError (aLib, "ext:twice((1, 2))"));
    assertEquals ("XPTY0004", _evalExtError (aLib, "ext:twice(())"));
    assertEquals ("FORG0001", _evalExtError (aLib, "ext:twice(xs:untypedAtomic('x'))"));
  }

  @Test
  public void testFreeze ()
  {
    final XdmQName aName = new XdmQName ("urn:test", "f");
    final IXPathFunction aImpl = (aCtx, aArgs) -> XdmSequence.EMPTY;
    final FunctionLibrary aRoot = new FunctionLibrary ();
    aRoot.register ("urn:test", "f", "() as item()*", aImpl);
    final FunctionLibrary aChild = aRoot.createChild ();
    assertFalse (aRoot.isFrozen ());
    assertFalse (aChild.isFrozen ());
    // The parent must be frozen first
    _assertReadOnly (aChild::freeze);
    assertFalse (aChild.isFrozen ());

    assertSame (aRoot, aRoot.freeze ());
    assertTrue (aRoot.isFrozen ());
    // Freezing again does nothing
    aRoot.freeze ();
    final long nStamp = aRoot.getModificationStamp ();
    _assertReadOnly ( () -> aRoot.register ("urn:test", "g", "() as item()*", aImpl));
    _assertReadOnly ( () -> aRoot.hide (aName, 0));
    _assertReadOnly ( () -> aRoot.hideFromLookup (aName, 0));
    assertEquals (nStamp, aRoot.getModificationStamp ());
    assertNotNull (aRoot.getFunction (aName, 0));

    // The child is still writable until it is frozen itself
    aChild.hide (aName, 0);
    assertNull (aChild.getFunction (aName, 0));
    aChild.freeze ();
    assertTrue (aChild.isFrozen ());
    _assertReadOnly ( () -> aChild.register ("urn:test", "g", "() as item()*", aImpl));
    // A new child of a frozen library is writable
    final FunctionLibrary aGrandChild = aChild.createChild ();
    assertFalse (aGrandChild.isFrozen ());
    aGrandChild.register ("urn:test", "g", "() as item()*", aImpl);
    assertNotNull (aGrandChild.getFunction (new XdmQName ("urn:test", "g"), 0));
  }
  @Test
  public void testXsltSignatures ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefaultWithXsltSignatures ();
    assertFalse (aLib.isFrozen ());
    assertNotNull (aLib.getFunction (new XdmQName (XdmConstants.NS_FN, "key"), 3));
    assertNotNull (aLib.getFunction (new XdmQName (XdmConstants.NS_FN, "accumulator-after"), 1));
    // The F&O functions are still there
    assertNotNull (aLib.getFunction (new XdmQName (XdmConstants.NS_FN, "count"), 1));
    // The default library itself is unchanged
    assertNull (FunctionLibrary.getDefault ().getFunction (new XdmQName (XdmConstants.NS_FN, "key"), 2));

    final StaticContext aSC = new StaticContext ();
    aSC.setFunctionLibrary (aLib);
    for (final String sExpr : new String [] { "key('k', 'a')", "current()", "document('x.xml')" })
    {
      try
      {
        XPathCompiler.compile (sExpr, aSC).evaluate (new DynamicContext (aSC));
        fail (sExpr);
      }
      catch (final XPathException ex)
      {
        assertEquals (sExpr, "XPST0017", ex.getCode ());
      }
    }
    // Without the XSLT signatures, the call does not compile
    final StaticContext aPlain = new StaticContext ();
    try
    {
      XPathCompiler.compile ("key('k', @id)", aPlain);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPST0017", ex.getCode ());
    }
  }
}
