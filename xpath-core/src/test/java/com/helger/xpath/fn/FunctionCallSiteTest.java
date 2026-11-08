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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EOccurrence;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for {@link FunctionCallSite}: the cached resolution of a static
 * call is always the one of a direct {@link FunctionLibrary#getFunction(XdmQName, int)}.
 *
 * @author Philip Helger
 */
public final class FunctionCallSiteTest
{
  private static final String NS = "urn:test:site";
  private static final XdmQName F = new XdmQName (NS, "f");
  private static final SeqType STRING = SeqType.of (ItemType.atomic (EAtomicType.STRING), EOccurrence.ONE);

  @NonNull
  private static IXPathFunction _const (@NonNull final String s)
  {
    return (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.string (s));
  }

  @NonNull
  private static IXPathFunction _prefix (@NonNull final String sPrefix)
  {
    return (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.string (sPrefix + ((XdmAtomic) aArgs.get (0).get (0)).getString ()));
  }

  /** The type name and the value of the first argument. */
  @NonNull
  private static IXPathFunction _describeArg ()
  {
    return (aCtx, aArgs) -> {
      final XdmAtomic aArg = (XdmAtomic) aArgs.get (0).get (0);
      return XdmSequence.of (XdmAtomic.string (aArg.getType ().getQualifiedName () + ":" + aArg.getLexical ()));
    };
  }

  @NonNull
  private static String _eval (@NonNull final IExpr aExpr, @NonNull final FunctionLibrary aLib, final boolean bCompat)
  {
    final StaticContext aSC = new StaticContext ().setFunctionLibrary (aLib).setXPath10Compatibility (bCompat);
    return ((XdmAtomic) XPathEvaluator.evaluate (aExpr, new DynamicContext (aSC)).get (0)).getString ();
  }

  @NonNull
  private static String _eval (@NonNull final IExpr aExpr, @NonNull final FunctionLibrary aLib)
  {
    return _eval (aExpr, aLib, false);
  }

  @NonNull
  private static String _evalError (@NonNull final IExpr aExpr, @NonNull final FunctionLibrary aLib, final boolean bCompat)
  {
    try
    {
      _eval (aExpr, aLib, bCompat);
      fail ();
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  @NonNull
  private static IExpr _parse (@NonNull final String sExpr, @NonNull final FunctionLibrary aLib)
  {
    final StaticContext aSC = new StaticContext ().setFunctionLibrary (aLib).declareNamespace ("t", NS);
    return XPathParser.parse (sExpr, aSC);
  }

  @Test
  public void testSameAsDirectLookup ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    final FunctionCallSite aSite = new FunctionCallSite ();
    final FunctionCallSite aSiteNone = new FunctionCallSite ();
    final XdmQName aConcat = new XdmQName (XdmConstants.NS_FN, "concat");
    final XdmQName aSubstring = new XdmQName (XdmConstants.NS_FN, "substring");
    for (int i = 0; i < 3; ++i)
    {
      assertSame (aLib.getFunction (aSubstring, 2), aSite.getFunction (aLib, aSubstring, 2));
      assertNull (aSiteNone.getFunction (aLib, new XdmQName (NS, "none"), 1));
    }
    // A variadic function: the one created on demand
    final FunctionCallSite aSite2 = new FunctionCallSite ();
    final FunctionDef aDef = aSite2.getFunction (aLib, aConcat, 4);
    assertNotNull (aDef);
    assertSame (aLib.getFunction (aConcat, 4), aDef);
    assertSame (aDef, aSite2.getFunction (aLib, aConcat, 4));
  }

  @Test
  public void testRegistrationAfterFirstCall ()
  {
    final FunctionLibrary aLib = new FunctionLibrary ();
    final FunctionCallSite aSite = new FunctionCallSite ();
    // Unknown first, cached as such
    assertNull (aSite.getFunction (aLib, F, 0));
    assertNull (aSite.getFunction (aLib, F, 0));
    aLib.registerParsed (F, List.of (), STRING, _const ("a"), false);
    final FunctionDef aA = aSite.getFunction (aLib, F, 0);
    assertNotNull (aA);
    assertSame (aLib.getFunction (F, 0), aA);
    // Replacing the function with the same name and arity
    aLib.registerParsed (F, List.of (), STRING, _const ("b"), false);
    final FunctionDef aB = aSite.getFunction (aLib, F, 0);
    assertNotEquals (aA, aB);
    assertSame (aLib.getFunction (F, 0), aB);
    // A registration of another name changes nothing for this site
    aLib.register (NS, "g", "() as xs:string", _const ("g"));
    assertSame (aB, aSite.getFunction (aLib, F, 0));
  }

  @Test
  public void testChildLibraries ()
  {
    final FunctionLibrary aRoot = new FunctionLibrary ();
    aRoot.registerParsed (F, List.of (), STRING, _const ("root"), false);
    final FunctionLibrary aChild = aRoot.createChild ();
    final FunctionLibrary aGrandChild = aChild.createChild ();
    final FunctionCallSite aSite = new FunctionCallSite ();
    final FunctionDef aRootDef = aRoot.getFunction (F, 0);
    assertSame (aRootDef, aSite.getFunction (aGrandChild, F, 0));
    // Overridden in the middle library after the first call
    aChild.registerParsed (F, List.of (), STRING, _const ("child"), false);
    final FunctionDef aChildDef = aChild.getFunction (F, 0);
    assertNotEquals (aRootDef, aChildDef);
    assertSame (aChildDef, aSite.getFunction (aGrandChild, F, 0));
    // Hidden in the grandchild
    aGrandChild.hide (F, 0);
    assertNull (aSite.getFunction (aGrandChild, F, 0));
    // Alternating libraries at one site: each gets its own result
    for (int i = 0; i < 3; ++i)
    {
      assertSame (aRootDef, aSite.getFunction (aRoot, F, 0));
      assertSame (aChildDef, aSite.getFunction (aChild, F, 0));
      assertNull (aSite.getFunction (aGrandChild, F, 0));
    }
    // Replaced in the root: visible through the root only, the child keeps its own
    aRoot.registerParsed (F, List.of (), STRING, _const ("root2"), false);
    assertSame (aRoot.getFunction (F, 0), aSite.getFunction (aRoot, F, 0));
    assertNotEquals (aRootDef, aSite.getFunction (aRoot, F, 0));
    assertSame (aChildDef, aSite.getFunction (aChild, F, 0));
  }

  @Test
  public void testVariadicRegisteredLater ()
  {
    final FunctionLibrary aRoot = new FunctionLibrary ();
    final FunctionLibrary aChild = aRoot.createChild ();
    final FunctionCallSite aSite = new FunctionCallSite ();
    assertNull (aSite.getFunction (aChild, F, 3));
    aRoot.registerVariadic (NS, "f", 1, "item()*", "xs:string", _const ("v"));
    final FunctionDef aDef = aSite.getFunction (aChild, F, 3);
    assertNotNull (aDef);
    assertSame (aChild.getFunction (F, 3), aDef);
  }

  /**
   * One parsed expression evaluated with several libraries and with a library
   * changed between evaluations gives the result of a fresh lookup each time.
   */
  @Test
  public void testExpressionWithSeveralLibraries ()
  {
    final FunctionLibrary aBase = FunctionLibrary.createDefault ();
    aBase.registerParsed (F, List.of (STRING), STRING, _prefix ("base:"), false);
    final FunctionLibrary aPkg = aBase.createChild ();
    aPkg.registerParsed (F, List.of (STRING), STRING, _prefix ("pkg:"), false);
    final IExpr aExpr = _parse ("t:f('x') || '/' || concat('a', 'b', 'c')", aBase);
    for (int i = 0; i < 3; ++i)
    {
      assertEquals ("base:x/abc", _eval (aExpr, aBase));
      assertEquals ("pkg:x/abc", _eval (aExpr, aPkg));
    }
    // Hidden in the package after the first evaluation: XPST0017
    aPkg.hide (F, 1);
    try
    {
      _eval (aExpr, aPkg);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPST0017", ex.getCode ());
    }
    assertEquals ("base:x/abc", _eval (aExpr, aBase));
    // Registered again
    aPkg.registerParsed (F, List.of (STRING), STRING, _const ("again"), false);
    assertEquals ("again/abc", _eval (aExpr, aPkg));
  }

  /**
   * A literal argument is converted once per resolved definition and
   * compatibility mode: the result is always the one of the conversion of the
   * current definition, also when the definition changes between calls.
   */
  @Test
  public void testConvertedLiterals ()
  {
    final SeqType aDouble = SeqType.of (ItemType.atomic (EAtomicType.DOUBLE), EOccurrence.ONE);
    final SeqType aDecimal = SeqType.of (ItemType.atomic (EAtomicType.DECIMAL), EOccurrence.ONE);
    final FunctionLibrary aBase = FunctionLibrary.createDefault ();
    aBase.registerParsed (F, List.of (aDouble), STRING, _describeArg (), false);
    final FunctionLibrary aPkg = aBase.createChild ();
    aPkg.registerParsed (F, List.of (STRING), STRING, _describeArg (), false);
    final IExpr aExpr = _parse ("t:f(2)", aBase);
    final IExpr aStringExpr = _parse ("t:f('2')", aBase);
    for (int i = 0; i < 3; ++i)
    {
      // Promoted to xs:double for the base definition
      assertEquals ("xs:double:2", _eval (aExpr, aBase));
      // No conversion to xs:string: XPTY0004 on every call
      assertEquals ("XPTY0004", _evalError (aExpr, aPkg, false));
      // XPath 1.0 compatibility mode: fn:string
      assertEquals ("xs:string:2", _eval (aExpr, aPkg, true));
      // XPath 1.0 compatibility mode: fn:number, otherwise an error
      assertEquals ("xs:double:2", _eval (aStringExpr, aBase, true));
      assertEquals ("XPTY0004", _evalError (aStringExpr, aBase, false));
      assertEquals ("xs:string:2", _eval (aStringExpr, aPkg, false));
    }
    // Replaced after the first calls: the literal is converted for the new
    // definition
    aBase.registerParsed (F, List.of (aDecimal), STRING, _describeArg (), false);
    assertEquals ("xs:integer:2", _eval (aExpr, aBase));
    assertEquals ("XPTY0004", _evalError (aStringExpr, aBase, false));
  }

  /**
   * A literal whose conversion fails raises its error at the time of the call
   * of a direct conversion: after the errors of the arguments before it.
   */
  @Test
  public void testLiteralConversionErrorMoment ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    final IExpr aBad = _parse ("substring('abc', 'x')", aLib);
    final IExpr aEarlier = _parse ("substring(error(QName('urn:test:site', 'first')), 'x')", aLib);
    final IExpr aGood = _parse ("substring('abcd', 2) || substring('abcd', 2, 2)", aLib);
    for (int i = 0; i < 3; ++i)
    {
      assertEquals ("XPTY0004", _evalError (aBad, aLib, false));
      assertEquals ("first", _evalError (aEarlier, aLib, false));
      assertEquals ("bcdbc", _eval (aGood, aLib));
    }
  }

  /**
   * The site does not take part in the equality of the call node: two nodes
   * with the same name and argument list are equal, also after one of them
   * was evaluated.
   */
  @Test
  public void testEquality ()
  {
    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    final IExpr.FunctionCallExpr a = (IExpr.FunctionCallExpr) _parse ("concat('a', 'b')", aLib);
    final IExpr.FunctionCallExpr b = new IExpr.FunctionCallExpr (a.name (), a.args ());
    _eval (a, aLib);
    assertEquals (a, b);
    assertEquals (a.hashCode (), b.hashCode ());
  }

  /** Concurrent evaluation with two libraries at one call site. */
  @Test
  public void testConcurrent () throws Exception
  {
    final FunctionLibrary aBase = FunctionLibrary.createDefault ();
    aBase.registerParsed (F, List.of (), STRING, _const ("base"), false);
    final FunctionLibrary aPkg = aBase.createChild ();
    aPkg.registerParsed (F, List.of (), STRING, _const ("pkg"), false);
    final IExpr aExpr = _parse ("t:f()", aBase);
    final ExecutorService aES = Executors.newFixedThreadPool (4);
    try
    {
      final ICommonsList <Future <String>> aFutures = new CommonsArrayList <> ();
      for (int t = 0; t < 8; ++t)
      {
        final boolean bPkg = (t % 2) == 0;
        aFutures.add (aES.submit ( () -> {
          for (int i = 0; i < 2_000; ++i)
          {
            final String sGot = _eval (aExpr, bPkg ? aPkg : aBase);
            if (!sGot.equals (bPkg ? "pkg" : "base"))
              return sGot;
          }
          return "ok";
        }));
      }
      for (final Future <String> aFuture : aFutures)
        assertEquals ("ok", aFuture.get ());
    }
    finally
    {
      aES.shutdown ();
      aES.awaitTermination (10, TimeUnit.SECONDS);
    }
  }
}
