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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.helger.xpath.eval.ExprTestHelper.assertEvalError;
import static com.helger.xpath.eval.ExprTestHelper.assumeOps;
import static com.helger.xpath.eval.ExprTestHelper.assumeParser;
import static com.helger.xpath.eval.ExprTestHelper.boolValue;
import static com.helger.xpath.eval.ExprTestHelper.call;
import static com.helger.xpath.eval.ExprTestHelper.child;
import static com.helger.xpath.eval.ExprTestHelper.ctx;
import static com.helger.xpath.eval.ExprTestHelper.eval;
import static com.helger.xpath.eval.ExprTestHelper.fn;
import static com.helger.xpath.eval.ExprTestHelper.lit;
import static com.helger.xpath.eval.ExprTestHelper.parse;
import static com.helger.xpath.eval.ExprTestHelper.path;
import static com.helger.xpath.eval.ExprTestHelper.seq;
import static com.helger.xpath.eval.ExprTestHelper.str;

import org.junit.Test;

/**
 * Test class for class {@link FnBoolean}.
 *
 * @author Philip Helger
 */
public final class FnBooleanTest
{
  @Test
  public void testBasics ()
  {
    assumeParser ();
    assumeOps ();
    assertTrue (boolValue (eval (call (fn ("true")))));
    assertFalse (boolValue (eval (call (fn ("false")))));
    assertTrue (boolValue (eval (call (fn ("boolean"), str ("a")))));
    assertFalse (boolValue (eval (call (fn ("boolean"), seq ()))));
    assertTrue (boolValue (eval (call (fn ("not"), lit (0)))));
    assertFalse (boolValue (eval (call (fn ("not"), path (child ("a"))), ctx (parse ("<a/>")))));
    assertEvalError ("FORG0006", call (fn ("boolean"), seq (lit (1), lit (2))));
  }
}
