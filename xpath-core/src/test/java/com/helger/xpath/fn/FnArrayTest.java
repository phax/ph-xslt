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

import static com.helger.xpath.serialize.XPathTestEval.adaptive;
import static com.helger.xpath.serialize.XPathTestEval.assertError;
import static com.helger.xpath.serialize.XPathTestEval.evalString;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link FnArray}.
 *
 * @author Philip Helger
 */
public final class FnArrayTest
{
  @Test
  public void testAccess ()
  {
    assertEquals ("3", evalString ("array:size([1, (2, 3), ()])"));
    assertEquals ("2 3", evalString ("array:get([1, (2, 3)], 2)"));
    assertError ("FOAY0001", "array:get([1], 2)");
    assertError ("FOAY0001", "array:get([1], 0)");
    assertEquals ("[1,\"x\"]", adaptive ("array:put([1, 2], 2, 'x')"));
    assertError ("FOAY0001", "array:put([], 1, 'x')");
    assertEquals ("[1,2,(3,4)]", adaptive ("array:append([1, 2], (3, 4))"));
    assertEquals ("1", evalString ("array:head([1, 2])"));
    assertError ("FOAY0001", "array:head([])");
    assertEquals ("[2]", adaptive ("array:tail([1, 2])"));
    assertError ("FOAY0001", "array:tail([])");
  }

  @Test
  public void testSubarrayRemoveInsert ()
  {
    assertEquals ("[2,3]", adaptive ("array:subarray([1, 2, 3], 2)"));
    assertEquals ("[2]", adaptive ("array:subarray([1, 2, 3], 2, 1)"));
    assertEquals ("[]", adaptive ("array:subarray([1, 2, 3], 4)"));
    assertError ("FOAY0001", "array:subarray([1, 2, 3], 5)");
    assertError ("FOAY0001", "array:subarray([1, 2, 3], 0)");
    assertError ("FOAY0002", "array:subarray([1, 2, 3], 1, -1)");
    assertError ("FOAY0001", "array:subarray([1, 2, 3], 2, 3)");
    assertEquals ("[2]", adaptive ("array:remove([1, 2, 3], (1, 3, 3))"));
    assertError ("FOAY0001", "array:remove([1, 2, 3], 4)");
    assertEquals ("[0,1,2]", adaptive ("array:insert-before([1, 2], 1, 0)"));
    assertEquals ("[1,2,3]", adaptive ("array:insert-before([1, 2], 3, 3)"));
    assertError ("FOAY0001", "array:insert-before([1, 2], 4, 3)");
  }

  @Test
  public void testStructure ()
  {
    assertEquals ("[3,2,1]", adaptive ("array:reverse([1, 2, 3])"));
    assertEquals ("[1,2,3]", adaptive ("array:join(([1], [], [2, 3]))"));
    assertEquals ("[]", adaptive ("array:join(())"));
    assertEquals ("1 2 3 4 5", evalString ("array:flatten((1, [2, [3, [4]]], 5))"));
  }

  @Test
  public void testHigherOrder ()
  {
    assertEquals ("[2,4]", adaptive ("array:for-each([1, 2], function($x) { $x * 2 })"));
    assertEquals ("[2,4]", adaptive ("array:filter([1, 2, 3, 4], function($x) { $x mod 2 = 0 })"));
    assertError ("XPTY0004", "array:filter([1, 2], function($x) { $x })");
    assertEquals ("10", evalString ("array:fold-left([1, 2, 3, 4], 0, function($a, $b) { $a + $b })"));
    assertEquals ("(1, (2, (3, ())))",
                  evalString ("array:fold-right([1, 2, 3], '()', function($m, $a) { '(' || $m || ', ' || $a || ')' })"));
    assertEquals ("[11,22]", adaptive ("array:for-each-pair([1, 2, 3], [10, 20], function($a, $b) { $a + $b })"));
  }

  @Test
  public void testSort ()
  {
    assertEquals ("[1,2,3]", adaptive ("array:sort([3, 1, 2])"));
    assertEquals ("[\"a\",\"b\",\"c\"]", adaptive ("array:sort(['c', 'a', 'b'], ())"));
    assertEquals ("[3,2,1]", adaptive ("array:sort([1, 2, 3], (), function($x) { -$x })"));
    // members are sequences and compared lexicographically
    assertEquals ("[(1,1),(1,2),2]", adaptive ("array:sort([(1, 2), 2, (1, 1)])"));
    assertError ("XPTY0004", "array:sort([1, 'a'])");
  }
}
