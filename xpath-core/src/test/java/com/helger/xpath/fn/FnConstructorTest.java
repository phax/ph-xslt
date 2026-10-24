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

import static com.helger.xpath.fn.FnTestHelper.assertLex;
import static com.helger.xpath.fn.FnTestHelper.assertTyped;
import static com.helger.xpath.fn.FnTestHelper.assertXPathError;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnConstructor}.
 *
 * @author Philip Helger
 */
public final class FnConstructorTest
{
  @Test
  public void testRegistration ()
  {
    final FunctionLibrary aLib = FunctionLibrary.getDefault ();
    for (final EAtomicType eType : EAtomicType.values ())
    {
      final XdmQName aName = new XdmQName (XdmConstants.NS_XS, eType.getLocalName ());
      if (eType == EAtomicType.ANY_ATOMIC_TYPE || eType == EAtomicType.NOTATION)
        assertNull (eType.toString (), aLib.getFunction (aName, 1));
      else
        assertNotNull (eType.toString (), aLib.getFunction (aName, 1));
    }
    assertNotNull (aLib.getFunction (new XdmQName (XdmConstants.NS_XS, "NMTOKENS"), 1));
  }

  @Test
  public void testAtomic ()
  {
    assertTyped ("integer:12", "xs:integer(' 12 ')");
    assertTyped ("byte:-5", "xs:byte('-5')");
    assertTyped ("", "xs:integer(())");
    assertTyped ("double:1", "xs:double(true())");
    assertTyped ("string:3.5", "xs:string(3.5)");
    assertTyped ("untypedAtomic:a", "xs:untypedAtomic('a')");
    assertTyped ("double:1", "xs:numeric('1')");
    assertTyped ("decimal:1.5", "xs:numeric(1.5)");
    assertTyped ("QName:xs:integer", "xs:QName('xs:integer')");
    assertLex ("true", "xs:QName('fn:abs') eq function-name(abs#1)");
    assertTyped ("", "xs:error(())");
    assertLex ("true", "xs:error#1 instance of function(xs:anyAtomicType?) as empty-sequence()");
    assertXPathError ("FORG0001", "xs:error('x')");
    assertXPathError ("FORG0001", "xs:integer('x')");
    assertXPathError ("FORG0001", "xs:byte(300)");
    assertXPathError ("XPTY0004", "xs:integer((1, 2))");
    assertXPathError ("XPST0017", "xs:NOTATION('a')");
    assertXPathError ("XPST0017", "xs:anyAtomicType('a')");
  }

  @Test
  public void testList ()
  {
    assertTyped ("NMTOKEN:a NMTOKEN:b NMTOKEN:c", "xs:NMTOKENS(' a b  c ')");
    assertTyped ("IDREF:x", "xs:IDREFS('x')");
    assertTyped ("ENTITY:e1 ENTITY:e2", "xs:ENTITIES('e1 e2')");
    assertTyped ("", "xs:NMTOKENS(())");
    assertXPathError ("FORG0001", "xs:NMTOKENS(' ')");
    assertXPathError ("FORG0001", "xs:IDREFS('1a')");
    assertXPathError ("XPTY0004", "xs:NMTOKENS(1)");
  }
}
