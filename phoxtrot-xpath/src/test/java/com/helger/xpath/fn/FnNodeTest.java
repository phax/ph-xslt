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

import static com.helger.xpath.fn.FnNodeTestHelper.assertError;
import static com.helger.xpath.fn.FnNodeTestHelper.eval;
import static com.helger.xpath.fn.FnNodeTestHelper.parse;
import static com.helger.xpath.fn.FnNodeTestHelper.str;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Test class for class {@link FnNode}.
 *
 * @author Philip Helger
 */
public final class FnNodeTest
{
  @Test
  public void testInScopePrefixes ()
  {
    final IXdmNode aDoc = parse ("<r xmlns='urn:d' xmlns:a='urn:a'><e xmlns='' xmlns:b='urn:b'/></r>");
    assertEquals ("xml  a", str ("in-scope-prefixes(/*:r)", aDoc));
    assertEquals ("xml a b", str ("in-scope-prefixes(//*:e)", aDoc));
    assertError ("XPTY0004", "in-scope-prefixes(/)", aDoc);
  }

  @Test
  public void testNamespaceUriForPrefix ()
  {
    final IXdmNode aDoc = parse ("<r xmlns='urn:d' xmlns:a='urn:a'><e xmlns=''/></r>");
    assertEquals ("urn:a", str ("namespace-uri-for-prefix('a', /*:r)", aDoc));
    assertEquals ("urn:d", str ("namespace-uri-for-prefix('', /*:r)", aDoc));
    assertEquals ("urn:d", str ("namespace-uri-for-prefix((), /*:r)", aDoc));
    assertTrue (eval ("namespace-uri-for-prefix('', //*:e)", aDoc).isEmpty ());
    assertTrue (eval ("namespace-uri-for-prefix('z', /*:r)", aDoc).isEmpty ());
    assertEquals ("http://www.w3.org/XML/1998/namespace", str ("namespace-uri-for-prefix('xml', /*:r)", aDoc));
    assertError ("XPTY0004", "namespace-uri-for-prefix('a', 1)", XdmAtomic.integer (1));
  }
}
