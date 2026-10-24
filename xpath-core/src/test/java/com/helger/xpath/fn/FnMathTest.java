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

import static com.helger.xpath.fn.FnTestHelper.assertTyped;
import static com.helger.xpath.fn.FnTestHelper.assertXPathError;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link FnMath}.
 *
 * @author Philip Helger
 */
public final class FnMathTest
{
  @Test
  public void testBasics ()
  {
    assertTyped ("double:3.141592653589793", "math:pi()");
    assertTyped ("", "math:exp(())");
    assertTyped ("double:1", "math:exp(0)");
    assertTyped ("double:23.140692632779267", "math:exp(math:pi())");
    assertTyped ("double:1.0E-10", "math:exp10(-10)");
    assertTyped ("double:-INF", "math:log(0)");
    assertTyped ("double:NaN", "math:log(-1)");
    assertTyped ("double:3", "math:log10(1e3)");
    assertTyped ("double:-0", "math:sqrt(-0.0e0)");
    assertTyped ("double:NaN", "math:sqrt(-1)");
    assertTyped ("double:0", "math:sin(0)");
    assertTyped ("double:1", "math:cos(0)");
    assertTyped ("double:NaN", "math:asin(2)");
    assertTyped ("double:3.141592653589793", "math:acos(-1)");
    assertTyped ("double:0.7853981633974483", "math:atan(1)");
    assertTyped ("double:-3.141592653589793", "math:atan2(-0.0e0, -1)");
    assertXPathError ("XPTY0004", "math:atan2((), 1)");
  }

  @Test
  public void testPow ()
  {
    assertTyped ("", "math:pow((), 93.7)");
    assertTyped ("double:8", "math:pow(2, 3)");
    assertTyped ("double:-8", "math:pow(-2, 3)");
    assertTyped ("double:0.125", "math:pow(2, -3)");
    assertTyped ("double:INF", "math:pow(0, -3)");
    assertTyped ("double:-INF", "math:pow(-0e0, -3)");
    assertTyped ("double:1", "math:pow(xs:double('NaN'), 0)");
    assertTyped ("double:1", "math:pow(1, xs:double('NaN'))");
    assertTyped ("double:1", "math:pow(-1, xs:double('INF'))");
    assertTyped ("double:1", "math:pow(-1, xs:double('-INF'))");
    assertTyped ("double:NaN", "math:pow(-2.5e0, 2.00000001e0)");
    assertTyped ("double:0", "math:pow(0.5, xs:double('INF'))");
    assertEquals (1.0, FnMath.pow (1, Double.POSITIVE_INFINITY), 0);
  }
}
