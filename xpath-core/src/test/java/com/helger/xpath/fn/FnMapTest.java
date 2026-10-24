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
 * Test class for class {@link FnMap}.
 *
 * @author Philip Helger
 */
public final class FnMapTest
{
  @Test
  public void testBasics ()
  {
    assertEquals ("3", evalString ("map:size(map{1:'a', 2:'b', 3:'c'})"));
    assertEquals ("1 2 3", evalString ("map:keys(map{1:'a', 2:'b', 3:'c'})"));
    assertEquals ("true false", evalString ("map:contains(map{1:'a'}, 1.0), map:contains(map{1:'a'}, '1')"));
    assertEquals ("b", evalString ("map:get(map{1:'a', 2:'b'}, 2)"));
    assertEquals ("", evalString ("map:get(map{1:'a'}, 5)"));
    assertEquals ("map{1:\"z\",2:\"b\"}", adaptive ("map:put(map{1:'a', 2:'b'}, 1, 'z')"));
    assertEquals ("map{\"k\":(1,2)}", adaptive ("map:entry('k', (1, 2))"));
    assertEquals ("map{2:\"b\"}", adaptive ("map:remove(map{1:'a', 2:'b', 3:'c'}, (1, 3, 7))"));
    assertEquals ("1a 2b", evalString ("map:for-each(map{1:'a', 2:'b'}, function($k, $v) { $k || $v })"));
  }

  @Test
  public void testMerge ()
  {
    assertEquals ("map{}", adaptive ("map:merge(())"));
    assertEquals ("map{1:\"a\",2:\"c\"}", adaptive ("map:merge((map{1:'a'}, map{1:'b', 2:'c'}))"));
    assertEquals ("map{1:\"b\",2:\"c\"}",
                  adaptive ("map:merge((map{1:'a'}, map{1:'b', 2:'c'}), map{'duplicates':'use-last'})"));
    assertEquals ("map{1:(\"a\",\"b\"),2:\"c\"}",
                  adaptive ("map:merge((map{1:'a'}, map{1:'b', 2:'c'}), map{'duplicates':'combine'})"));
    assertError ("FOJS0003", "map:merge((map{1:'a'}, map{1:'b'}), map{'duplicates':'reject'})");
    assertError ("FOJS0005", "map:merge((map{1:'a'}), map{'duplicates':'unknown'})");
    assertError ("XPTY0004", "map:merge((map{1:'a'}), map{'duplicates':12})");
    // Large merges are linear
    assertEquals ("20000", evalString ("map:size(map:merge((1 to 20000) ! map:entry(., .)))"));
  }

  @Test
  public void testFind ()
  {
    assertEquals ("[]", adaptive ("map:find((), 1)"));
    assertEquals ("[\"x\",\"y\",(\"z1\",\"z2\")]",
                  adaptive ("map:find([map{'k':'x', 'n':map{'k':'y'}}, [map{'k':('z1','z2')}]], 'k')"));
  }

  @Test
  public void testSameKey ()
  {
    assertEquals ("1", evalString ("map:size(map:merge((map{1:'a'}, map{1.0:'b'}, map{1e0:'c'})))"));
    assertEquals ("2", evalString ("map:size(map{'1':'a', 1:'b'})"));
  }
}
