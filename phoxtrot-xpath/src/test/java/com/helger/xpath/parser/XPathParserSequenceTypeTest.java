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
package com.helger.xpath.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.EOccurrence;
import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for {@link XPathParser#parseSequenceType(String, StaticContext)}
 * and {@link XPathParser#parseFunctionSignature(String, StaticContext)}.
 *
 * @author Philip Helger
 */
public final class XPathParserSequenceTypeTest
{
  @NonNull
  private static SeqType _parse (@NonNull final String s)
  {
    return XPathParser.parseSequenceType (s, ParserTestHelper.createContext ());
  }

  private static void _check (@NonNull final String sExpected, @NonNull final String s)
  {
    assertEquals (s, sExpected, ParserTestHelper.seqType (_parse (s)));
  }

  private static void _expectError (@NonNull final String sCode, @NonNull final String s)
  {
    try
    {
      _parse (s);
      fail ("Expected " + sCode + " for " + s);
    }
    catch (final XPathException ex)
    {
      assertEquals (s + " -> " + ex.getMessage (), sCode, ex.getCode ());
    }
  }

  @Test
  public void testAtomic ()
  {
    final SeqType aType = _parse ("xs:string?");
    assertFalse (aType.empty ());
    assertEquals (ItemType.Kind.ATOMIC, aType.itemType ().kind ());
    assertSame (EAtomicType.STRING, aType.itemType ().atomicType ());
    assertSame (EOccurrence.ZERO_OR_ONE, aType.occurrence ());
    assertFalse (aType.listCast ());

    for (final EAtomicType e : EAtomicType.values ())
      assertSame (e, _parse ("xs:" + e.getLocalName ()).itemType ().atomicType ());
    _check ("xs:integer", "xs:integer");
    _check ("xs:integer*", "xs:integer*");
    _check ("xs:integer+", "xs:integer +");
    _check ("xs:anyAtomicType*", "xs:anyAtomicType*");
    _check ("xs:numeric?", "xs:numeric?");
    _check ("xs:integer", "Q{http://www.w3.org/2001/XMLSchema}integer");
    _check ("xs:integer?", "(xs:integer)?");
  }

  @Test
  public void testEmptyAndItem ()
  {
    final SeqType aEmpty = _parse ("empty-sequence()");
    assertTrue (aEmpty.empty ());
    assertNull (aEmpty.itemType ());
    assertSame (SeqType.EMPTY_SEQUENCE, aEmpty);
    _check ("item()", "item()");
    _check ("item()*", "item( )*");
  }

  @Test
  public void testKindTests ()
  {
    _check ("node()", "node()");
    _check ("text()?", "text()?");
    _check ("comment()", "comment()");
    _check ("namespace-node()", "namespace-node()");
    _check ("processing-instruction()", "processing-instruction()");
    _check ("processing-instruction(x)", "processing-instruction(x)");
    _check ("element()", "element()");
    _check ("element(a)*", "element(a)*");
    _check ("element(*,xs:string?)", "element(*, xs:string?)");
    _check ("attribute(a,xs:integer)+", "attribute(a, xs:integer)+");
    _check ("document-node()", "document-node()");
    _check ("document-node(element(Q{urn:p}a))", "document-node(element(p:a))");

    final SeqType aType = _parse ("element(p:a, xs:string?)");
    assertEquals (ENodeTestKind.ELEMENT, aType.itemType ().nodeTest ().kind ());
    assertEquals (new XdmQName ("urn:p", "a"), aType.itemType ().nodeTest ().name ());
    assertEquals (new XdmQName ("http://www.w3.org/2001/XMLSchema", "string"),
                  aType.itemType ().nodeTest ().typeName ());
    assertTrue (aType.itemType ().nodeTest ().nillable ());
  }

  @Test
  public void testFunctionMapArray ()
  {
    _check ("function(*)", "function(*)");
    _check ("(function() as item()*)", "function() as item()*");
    // The occurrence indicator binds to the return type
    _check ("(function(xs:string,node()*) as xs:boolean?)", "function(xs:string, node()*) as xs:boolean?");
    _check ("(function(xs:string,node()*) as xs:boolean)?", "(function(xs:string, node()*) as xs:boolean)?");
    _check ("(function((function(xs:integer) as xs:integer)) as item())",
            "function(function(xs:integer) as xs:integer) as item()");
    _check ("map(*)", "map(*)");
    _check ("map(xs:string,item()*)", "map(xs:string, item()*)");
    _check ("array(*)+", "array(*)+");
    _check ("array(map(*))", "array(map(*))");

    final SeqType aMap = _parse ("map(xs:integer, xs:string?)");
    assertEquals (ItemType.Kind.MAP, aMap.itemType ().kind ());
    assertFalse (aMap.itemType ().wildcard ());
    assertSame (EAtomicType.INTEGER, aMap.itemType ().mapKeyType ());
    assertSame (EOccurrence.ZERO_OR_ONE, aMap.itemType ().valueType ().occurrence ());
  }

  @Test
  public void testDefaultTypeNamespace ()
  {
    final StaticContext aCtx = ParserTestHelper.createContext ();
    aCtx.setDefaultElementNamespace ("http://www.w3.org/2001/XMLSchema");
    assertSame (EAtomicType.INTEGER, XPathParser.parseSequenceType ("integer", aCtx).itemType ().atomicType ());
    _expectError ("XPST0051", "integer");
  }

  @Test
  public void testErrors ()
  {
    _expectError ("XPST0003", "");
    _expectError ("XPST0003", "xs:integer xs:string");
    _expectError ("XPST0003", "xs:integer**");
    _expectError ("XPST0003", "empty-sequence()*");
    _expectError ("XPST0003", "function(xs:string)");
    _expectError ("XPST0003", "map(xs:string)");
    _expectError ("XPST0003", "array()");
    _expectError ("XPST0003", "item(");
    _expectError ("XPST0003", "if()");
    _expectError ("XPST0051", "empty-sequence");
    _expectError ("XPST0051", "xs:doesNotExist");
    _expectError ("XPST0051", "xs:NMTOKENS");
    _expectError ("XPST0051", "xs:anyType");
    _expectError ("XPST0003", "none()");
    _expectError ("XPST0003", "xs:integer()");
    _expectError ("XPST0081", "nope:integer");
    _expectError ("XPST0008", "schema-element(a)");
    _expectError ("XPST0008", "element(a, xs:nope)");
  }

  @Test
  public void testFunctionSignature ()
  {
    final StaticContext aCtx = new StaticContext ();
    XPathParser.FunctionSignature aSig = XPathParser.parseFunctionSignature ("(xs:string?, item()*) as xs:boolean",
                                                                            aCtx);
    assertEquals (2, aSig.paramTypes ().size ());
    assertEquals ("xs:string?", ParserTestHelper.seqType (aSig.paramTypes ().get (0)));
    assertEquals ("item()*", ParserTestHelper.seqType (aSig.paramTypes ().get (1)));
    assertEquals ("xs:boolean", ParserTestHelper.seqType (aSig.returnType ()));

    aSig = XPathParser.parseFunctionSignature ("() as xs:integer", aCtx);
    assertTrue (aSig.paramTypes ().isEmpty ());

    aSig = XPathParser.parseFunctionSignature ("( ) as empty-sequence()", aCtx);
    assertTrue (aSig.returnType ().empty ());

    aSig = XPathParser.parseFunctionSignature ("(item()*, function(item(), item()) as xs:boolean) as item()*",
                                               aCtx);
    assertEquals ("(function(item(),item()) as xs:boolean)", ParserTestHelper.seqType (aSig.paramTypes ().get (1)));

    aSig = XPathParser.parseFunctionSignature ("(map(*), xs:anyAtomicType) as item()*", aCtx);
    assertEquals ("map(*)", ParserTestHelper.seqType (aSig.paramTypes ().get (0)));

    aSig = XPathParser.parseFunctionSignature ("(array(*), xs:integer) as item()*", aCtx);
    assertEquals (2, aSig.paramTypes ().size ());

    aSig = XPathParser.parseFunctionSignature ("(xs:numeric?) as xs:numeric?", aCtx);
    assertSame (EAtomicType.NUMERIC, aSig.returnType ().itemType ().atomicType ());

    aSig = XPathParser.parseFunctionSignature ("(node()?, element()*, document-node()) as attribute()*", aCtx);
    assertEquals (3, aSig.paramTypes ().size ());

    for (final String s : new String [] { "(xs:string)",
                                          "xs:string as xs:string",
                                          "(xs:string,) as xs:string",
                                          "($a as xs:string) as xs:string",
                                          "(xs:string) as" })
      try
      {
        XPathParser.parseFunctionSignature (s, aCtx);
        fail (s);
      }
      catch (final XPathException ex)
      {
        assertEquals (s, "XPST0003", ex.getCode ());
      }
  }
}
