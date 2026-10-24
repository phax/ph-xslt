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

import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnQName}.
 *
 * @author Philip Helger
 */
public final class FnQNameTest
{
  @Test
  public void testQName ()
  {
    final XdmSequence aSeq = eval ("QName('urn:x', 'p:local')");
    assertEquals (new XdmQName ("urn:x", "local", "p"), ((XdmAtomic) aSeq.get (0)).getValue ());
    assertEquals ("p", ((XdmQName) ((XdmAtomic) aSeq.get (0)).getValue ()).prefix ());
    assertEquals ("local", str ("QName((), 'local')"));
    assertError ("FOCA0002", "QName((), 'p:local')");
    assertError ("FOCA0002", "QName('', 'p:local')");
    assertError ("FOCA0002", "QName('urn:x', '1abc')");
    assertError ("FOCA0002", "QName('urn:x', 'a:b:c')");
    assertError ("XPTY0004", "QName('urn:x', ())");
  }

  @Test
  public void testAccessors ()
  {
    assertEquals ("p", str ("prefix-from-QName(QName('urn:x', 'p:local'))"));
    assertTrue (eval ("prefix-from-QName(QName('urn:x', 'local'))").isEmpty ());
    assertTrue (eval ("prefix-from-QName(())").isEmpty ());
    final XdmSequence aLocal = eval ("local-name-from-QName(QName('urn:x', 'p:local'))");
    assertEquals (EAtomicType.NCNAME, ((XdmAtomic) aLocal.get (0)).getType ());
    assertEquals ("local", str (aLocal));
    final XdmSequence aNs = eval ("namespace-uri-from-QName(QName('urn:x', 'p:local'))");
    assertEquals (EAtomicType.ANY_URI, ((XdmAtomic) aNs.get (0)).getType ());
    assertEquals ("urn:x", str (aNs));
    assertEquals ("", str ("namespace-uri-from-QName(QName('', 'local'))"));
    assertTrue (eval ("namespace-uri-from-QName(())").isEmpty ());
    assertError ("XPTY0004", "local-name-from-QName('a')");
  }

  @Test
  public void testResolveQName ()
  {
    final XdmNode aDoc = parse ("<r xmlns='urn:d' xmlns:a='urn:a'/>");
    final XdmSequence aSeq = eval ("resolve-QName('a:x', /*)", aDoc);
    assertEquals (new XdmQName ("urn:a", "x"), ((XdmAtomic) aSeq.get (0)).getValue ());
    assertEquals ("urn:d", str ("namespace-uri-from-QName(resolve-QName('x', /*))", aDoc));
    assertTrue (eval ("resolve-QName((), /*)", aDoc).isEmpty ());
    assertError ("FONS0004", "resolve-QName('z:x', /*)", aDoc);
    assertError ("FOCA0002", "resolve-QName('a:x:y', /*)", aDoc);
    assertError ("FOCA0002", "resolve-QName('', /*)", aDoc);
  }
}
