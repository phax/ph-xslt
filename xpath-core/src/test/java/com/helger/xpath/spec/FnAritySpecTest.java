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
package com.helger.xpath.spec;

import static org.junit.Assert.assertTrue;

import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.xdm.XdmQName;

/**
 * Port of go-xslt <code>internal/xpath/fn_arity_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnAritySpecTest
{
  private static final String NS_FN = "http://www.w3.org/2005/xpath-functions";
  private static final String NS_MATH = "http://www.w3.org/2005/xpath-functions/math";
  private static final String NS_MAP = "http://www.w3.org/2005/xpath-functions/map";
  private static final String NS_ARRAY = "http://www.w3.org/2005/xpath-functions/array";
  private static final String NS_XS = "http://www.w3.org/2001/XMLSchema";
  private static final Integer I0 = Integer.valueOf (0);
  private static final Integer I1 = Integer.valueOf (1);
  private static final Integer I2 = Integer.valueOf (2);
  private static final Integer VARIADIC = Integer.valueOf (-1);
  private static final int MAX_PROBE = 50;

  /**
   * Function arities come from the F&O 3.1 signatures; a call or reference at
   * an arity no signature defines is XPST0017 (XPath 3.1 §3.1.5), and
   * fn:function-lookup at such an arity returns the empty sequence (§16.1.2).
   */
  @Test
  public void arityDirectCalls ()
  {
    run (
      err ("abs()", "XPST0017"),
      err ("abs(1, 2)", "XPST0017"),
      err ("string(1, 2)", "XPST0017"),
      err ("math:pi(1)", "XPST0017"),
      err ("math:atan2(1)", "XPST0017"),
      err ("concat('a')", "XPST0017")
    );
    run (
      ok ("concat('a', 'b', 'c', 'd', 'e')", "abcde"),
      ok ("string-join(('a', 'b'))", "ab"),
      ok ("math:atan2(0e0, 1e0)", "0")
    );
  }

  @Test
  public void arityFunctionLookup ()
  {
    run (
      // format-date exists only with 2 and 5 arguments.
      ok ("exists(function-lookup(xs:QName('fn:format-date'), 2))", "true"),
      ok ("exists(function-lookup(xs:QName('fn:format-date'), 3))", "false"),
      ok ("exists(function-lookup(xs:QName('fn:format-date'), 4))", "false"),
      ok ("exists(function-lookup(xs:QName('fn:format-date'), 5))", "true"),
      ok ("exists(function-lookup(xs:QName('fn:round'), 2))", "true"),
      ok ("exists(function-lookup(xs:QName('fn:round'), 3))", "false"),
      ok ("exists(function-lookup(xs:QName('math:atan2'), 2))", "true"),
      ok ("exists(function-lookup(xs:QName('math:atan2'), 1))", "false"),
      ok ("exists(function-lookup(xs:QName('xs:integer'), 1))", "true"),
      ok ("exists(function-lookup(xs:QName('xs:integer'), 2))", "false"),
      ok ("exists(function-lookup(xs:QName('fn:concat'), 7))", "true"),
      ok ("function-lookup(xs:QName('fn:concat'), 3)('a', 'b', 'c')", "abc")
    );
  }

  /**
   * Port of go-xslt's <code>StdFuncArity</code> test against
   * {@link FunctionLibrary#getDefault()}: the lowest and highest arity of a
   * known function (-1 for "variadic"), or unknown.
   */
  @Test
  public void arityStdFuncArity ()
  {
    final Object [] [] aCases = { { NS_FN, "abs", I1, I1, Boolean.TRUE },
                                  { NS_FN, "round", I1, I2, Boolean.TRUE },
                                  { NS_FN, "concat", I2, VARIADIC, Boolean.TRUE },
                                  { NS_MATH, "pi", I0, I0, Boolean.TRUE },
                                  { NS_MATH, "atan2", I2, I2, Boolean.TRUE },
                                  { NS_MAP, "merge", I1, I2, Boolean.TRUE },
                                  { NS_MAP, "get", I2, I2, Boolean.TRUE },
                                  { NS_ARRAY, "join", I1, I1, Boolean.TRUE },
                                  { NS_ARRAY, "for-each", I2, I2, Boolean.TRUE },
                                  { NS_XS, "integer", I1, I1, Boolean.TRUE },
                                  { NS_XS, "NMTOKENS", I1, I1, Boolean.TRUE },
                                  { NS_FN, "no-such-function", I0, I0, Boolean.FALSE },
                                  { NS_MATH, "no-such-function", I0, I0, Boolean.FALSE },
                                  { NS_MAP, "no-such-function", I0, I0, Boolean.FALSE },
                                  { NS_ARRAY, "no-such-function", I0, I0, Boolean.FALSE },
                                  { NS_XS, "noSuchType", I0, I0, Boolean.FALSE },
                                  { "http://example.com/other", "f", I0, I0, Boolean.FALSE } };
    final FunctionLibrary aLib = FunctionLibrary.getDefault ();
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Object [] aCase : aCases)
    {
      final XdmQName aName = new XdmQName ((String) aCase[0], (String) aCase[1]);
      final boolean bKnown = ((Boolean) aCase[4]).booleanValue ();
      int nLo = -1;
      int nHi = -1;
      for (int n = 0; n <= MAX_PROBE; n++)
        if (aLib.getFunction (aName, n) != null)
        {
          if (nLo < 0)
            nLo = n;
          nHi = n;
        }
      if (nHi == MAX_PROBE)
        nHi = VARIADIC.intValue ();
      final boolean bGotKnown = nLo >= 0;
      if (bGotKnown != bKnown ||
          (bKnown && (nLo != ((Integer) aCase[2]).intValue () || nHi != ((Integer) aCase[3]).intValue ())))
        aErrors.add (aName + " = " + nLo + ", " + nHi + ", " + bGotKnown + "; want " + aCase[2] + ", " + aCase[3] + ", " + bKnown);
    }
    assertTrue (aErrors.toString (), aErrors.isEmpty ());
  }

  /**
   * Port of go-xslt's <code>StdArityOK</code> test against
   * {@link FunctionLibrary#getDefault()}.
   */
  @Test
  public void arityStdArityOK ()
  {
    final Object [] [] aCases = { { NS_FN, "format-date", I2, Boolean.TRUE },
                                  { NS_FN, "format-date", Integer.valueOf (3), Boolean.FALSE },
                                  { NS_FN, "format-date", Integer.valueOf (4), Boolean.FALSE },
                                  { NS_FN, "format-date", Integer.valueOf (5), Boolean.TRUE },
                                  { NS_FN, "format-dateTime", Integer.valueOf (3), Boolean.FALSE },
                                  { NS_FN, "format-time", Integer.valueOf (5), Boolean.TRUE },
                                  { NS_FN, "concat", I1, Boolean.FALSE },
                                  { NS_FN, "concat", Integer.valueOf (100), Boolean.TRUE },
                                  { NS_FN, "abs", I0, Boolean.FALSE },
                                  { NS_FN, "abs", I1, Boolean.TRUE },
                                  { NS_MATH, "pi", I0, Boolean.TRUE },
                                  { NS_MATH, "pi", I1, Boolean.FALSE },
                                  { NS_MAP, "merge", I2, Boolean.TRUE },
                                  { NS_MAP, "merge", Integer.valueOf (3), Boolean.FALSE },
                                  { NS_ARRAY, "size", I1, Boolean.TRUE },
                                  { NS_XS, "integer", I1, Boolean.TRUE },
                                  { NS_XS, "integer", I2, Boolean.FALSE },
                                  { NS_FN, "no-such-function", I1, Boolean.FALSE } };
    final FunctionLibrary aLib = FunctionLibrary.getDefault ();
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final Object [] aCase : aCases)
    {
      final XdmQName aName = new XdmQName ((String) aCase[0], (String) aCase[1]);
      final boolean bGot = aLib.getFunction (aName, ((Integer) aCase[2]).intValue ()) != null;
      if (bGot != ((Boolean) aCase[3]).booleanValue ())
        aErrors.add (aName + "#" + aCase[2] + " = " + bGot + ", want " + aCase[3]);
    }
    assertTrue (aErrors.toString (), aErrors.isEmpty ());
  }

  /**
   * Named function references carry the declared F&O signature, so function
   * tests judge them by it (XPath 3.1 §2.5.6.3: parameter types are
   * contravariant, the result type covariant).
   */
  @Test
  public void aritySignedReferences ()
  {
    run (
      ok ("name#1 instance of function(node()?) as xs:string", "true"),
      ok ("name#1 instance of function(item()) as xs:string", "false"),
      ok ("abs#1 instance of function(xs:numeric?) as xs:numeric?", "true"),
      ok ("abs#1 instance of function(xs:integer) as xs:numeric?", "true"),
      ok ("abs#1 instance of function(xs:string) as xs:numeric?", "false"),
      ok ("count#1 instance of function(item()*) as xs:integer", "true"),
      ok ("count#1 instance of function(item()*) as xs:string", "false"),
      ok ("true#0 instance of function() as xs:boolean", "true"),
      ok ("concat#2 instance of function(xs:anyAtomicType?, xs:anyAtomicType?) as xs:string", "true"),
      ok ("substring#2 instance of function(xs:string?, xs:double) as xs:string", "true"),
      ok ("filter#2 instance of function(item()*, function(item()) as xs:boolean) as item()*", "true"),
      ok ("filter#2 instance of function(function(*), item()*) as item()*", "false"),
      ok ("math:pi#0 instance of function() as xs:double", "true"),
      ok ("function-arity(math:pi#0)", "0")
    );
  }
}
