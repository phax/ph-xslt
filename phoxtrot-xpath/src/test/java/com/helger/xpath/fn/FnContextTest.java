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
import static org.junit.Assert.assertTrue;

import static com.helger.xpath.eval.ExprTestHelper.assertEvalError;
import static com.helger.xpath.eval.ExprTestHelper.assumeDateTime;
import static com.helger.xpath.eval.ExprTestHelper.assumeParser;
import static com.helger.xpath.eval.ExprTestHelper.call;
import static com.helger.xpath.eval.ExprTestHelper.ctx;
import static com.helger.xpath.eval.ExprTestHelper.eval;
import static com.helger.xpath.eval.ExprTestHelper.fn;
import static com.helger.xpath.eval.ExprTestHelper.ints;
import static com.helger.xpath.eval.ExprTestHelper.longs;
import static com.helger.xpath.eval.ExprTestHelper.stringValue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.Test;

import com.helger.xpath.ast.IExpr;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;

/**
 * Test class for class {@link FnContext}.
 *
 * @author Philip Helger
 */
public final class FnContextTest
{
  @Test
  public void testLexical ()
  {
    final OffsetDateTime aDT = OffsetDateTime.of (2026, 3, 4, 5, 6, 7, 120_000_000, ZoneOffset.ofHoursMinutes (-5, -30));
    assertEquals ("2026-03-04T05:06:07.12-05:30", FnContext.getLexical (aDT, EAtomicType.DATE_TIME));
    assertEquals ("2026-03-04-05:30", FnContext.getLexical (aDT, EAtomicType.DATE));
    assertEquals ("05:06:07.12-05:30", FnContext.getLexical (aDT, EAtomicType.TIME));
    final OffsetDateTime aUtc = OffsetDateTime.of (12, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    assertEquals ("0012-01-01T00:00:00Z", FnContext.getLexical (aUtc, EAtomicType.DATE_TIME));
  }

  @Test
  public void testPositionAndLast ()
  {
    assumeParser ();
    assertEvalError ("XPDY0002", call (fn ("position")));
    assertEvalError ("XPDY0002", call (fn ("last")));
    final DynamicContext aCtx = ctx ().withFocus (XdmAtomic.integer (1), 2, 5);
    assertEquals (longs (2), ints (eval (call (fn ("position")), aCtx)));
    assertEquals (longs (5), ints (eval (call (fn ("last")), aCtx)));
  }

  @Test
  public void testStaticValues ()
  {
    assumeParser ();
    final XdmSequence aLang = eval (call (fn ("default-language")));
    assertEquals (EAtomicType.LANGUAGE, ((XdmAtomic) aLang.get (0)).getType ());
    assertEquals ("en", ((XdmAtomic) aLang.get (0)).getString ());
    assertEquals ("http://www.w3.org/2005/xpath-functions/collation/codepoint",
                  stringValue (eval (call (fn ("default-collation")))));
    assertTrue (eval (call (fn ("static-base-uri"))).isEmpty ());
    final DynamicContext aCtx = ctx ();
    aCtx.getStaticContext ().setBaseUri ("http://example.org/");
    final XdmSequence aBase = eval (call (fn ("static-base-uri")), aCtx);
    assertEquals (EAtomicType.ANY_URI, ((XdmAtomic) aBase.get (0)).getType ());
    assertEquals ("http://example.org/", ((XdmAtomic) aBase.get (0)).getString ());
  }

  @Test
  public void testCurrentDateTime ()
  {
    assumeParser ();
    assumeDateTime ();
    final DynamicContext aCtx = ctx ();
    aCtx.setCurrentDateTime (OffsetDateTime.of (2026, 10, 24, 12, 0, 0, 0, ZoneOffset.ofHours (2)));
    final IExpr aNow = call (fn ("current-dateTime"));
    final XdmAtomic aDT = (XdmAtomic) eval (aNow, aCtx).get (0);
    assertEquals (EAtomicType.DATE_TIME_STAMP, aDT.getType ());
    assertEquals ("2026-10-24T12:00:00+02:00", aDT.getLexical ());
    assertEquals ("2026-10-24+02:00", ((XdmAtomic) eval (call (fn ("current-date")), aCtx).get (0)).getLexical ());
    assertEquals ("12:00:00+02:00", ((XdmAtomic) eval (call (fn ("current-time")), aCtx).get (0)).getLexical ());
    assertEquals ("PT2H", ((XdmAtomic) eval (call (fn ("implicit-timezone")), aCtx).get (0)).getLexical ());
  }
}
