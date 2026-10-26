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

import org.jspecify.annotations.NonNull;
import org.junit.BeforeClass;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.jaxp.JdkSchemaProvider;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaSource;

/**
 * Test the functions and type tests that depend on schema information:
 * typed values, fn:nilled, fn:id/element-with-id/idref and
 * <code>instance of element(N, T)</code> on a schema-validated tree.
 *
 * @author Philip Helger
 */
public final class FnSchemaValidatedTest
{
  private static final String XSD = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t' xmlns:t='urn:t' elementFormDefault='qualified'>" +
                                    "<xs:element name='root'><xs:complexType><xs:sequence>" +
                                    "<xs:element name='int' type='xs:int' nillable='true' maxOccurs='2'/>" +
                                    "<xs:element name='list' type='t:intList'/>" +
                                    "<xs:element name='qn' type='xs:QName'/>" +
                                    "<xs:element name='dec'><xs:complexType><xs:simpleContent><xs:extension base='xs:decimal'>" +
                                    "<xs:attribute name='refs' type='xs:IDREFS'/></xs:extension></xs:simpleContent></xs:complexType></xs:element>" +
                                    "<xs:element name='item' maxOccurs='unbounded'><xs:complexType><xs:sequence>" +
                                    "<xs:element name='id' type='xs:ID'/></xs:sequence>" +
                                    "<xs:attribute name='key' type='xs:ID'/></xs:complexType></xs:element>" +
                                    "<xs:element name='mixed'><xs:complexType mixed='true'><xs:sequence>" +
                                    "<xs:element name='b' type='xs:string'/></xs:sequence></xs:complexType></xs:element>" +
                                    "</xs:sequence></xs:complexType></xs:element>" +
                                    "<xs:simpleType name='intList'><xs:list itemType='xs:integer'/></xs:simpleType>" +
                                    "</xs:schema>";
  private static final String XML = "<t:root xmlns:t='urn:t' xmlns:p='urn:p' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n" +
                                    "  <t:int> 42 </t:int>\n" +
                                    "  <t:int xsi:nil='true'/>\n" +
                                    "  <t:list> 1 2  3 </t:list>\n" +
                                    "  <t:qn>p:local</t:qn>\n" +
                                    "  <t:dec refs='a b'>1.50</t:dec>\n" +
                                    "  <t:item key='a'><t:id>x</t:id></t:item>\n" +
                                    "  <t:item key='b'><t:id>y</t:id></t:item>\n" +
                                    "  <t:mixed>m <t:b>n</t:b> o</t:mixed>\n" +
                                    "</t:root>";

  private static XdmNode s_aDoc;

  @BeforeClass
  public static void beforeClass () throws Exception
  {
    final XdmSchemaSource aSource = new XdmSchemaSource (null, XSD);
    final IXdmSchemaSet aSchemaSet = new JdkSchemaProvider ().compile (new CommonsArrayList <> (aSource));
    s_aDoc = new XdmParser ().setSchemaSet (aSchemaSet).parse (XML);
  }

  @NonNull
  private static XdmSequence _eval (@NonNull final String sExpr)
  {
    final StaticContext aStatic = new StaticContext ();
    aStatic.declareNamespace ("t", "urn:t");
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, aStatic);
    final DynamicContext aCtx = aCompiled.createDynamicContext ().withFocus (s_aDoc, 1, 1);
    return aCompiled.evaluate (aCtx);
  }

  @NonNull
  private static String _str (@NonNull final String sExpr)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : _eval (sExpr))
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (Atomizer.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @Test
  public void testTypedValues ()
  {
    assertEquals ("42 xs:int", _str ("let $v := data(/t:root/t:int[1]) return ($v, if ($v instance of xs:int) then 'xs:int' else 'other')"));
    assertEquals ("0", _str ("count(data(/t:root/t:int[2]))"));
    assertEquals ("3 6", _str ("let $v := data(/t:root/t:list) return (count($v), sum($v))"));
    assertEquals ("true", _str ("data(/t:root/t:list)[1] instance of xs:integer"));
    assertEquals ("urn:p local", _str ("let $q := data(/t:root/t:qn) return (namespace-uri-from-QName($q), local-name-from-QName($q))"));
    assertEquals ("true 1.5", _str ("let $v := data(/t:root/t:dec) return ($v instance of xs:decimal, $v)"));
    assertEquals ("2", _str ("count(data(/t:root/t:dec/@refs))"));
    // Complex content: untyped string value
    assertEquals ("true", _str ("data(/t:root/t:mixed) instance of xs:untypedAtomic"));
    assertEquals ("m n o", _str ("string(/t:root/t:mixed)"));
    // Whitespace in element-only content was dropped
    assertEquals ("x", _str ("string(/t:root/t:item[1])"));
    final XdmNode aInt = (XdmNode) _eval ("/t:root/t:int[1]").get (0);
    assertEquals (XdmAtomic.class, Atomizer.getTypedValue (aInt).getClass ());
  }

  @Test
  public void testNilled ()
  {
    assertEquals ("false true", _str ("/t:root/t:int ! nilled(.)"));
    assertEquals ("true false", _str ("/t:root/t:int[2] ! (. instance of element(t:int, xs:int?), . instance of element(t:int, xs:int))"));
    assertEquals ("true", _str ("/t:root/t:int[2] instance of element(t:int)"));
  }

  @Test
  public void testInstanceOfWithType ()
  {
    assertEquals ("true true true false false",
                  _str ("/t:root/t:int[1] ! (. instance of element(*, xs:decimal), . instance of element(*, xs:numeric), " +
                        ". instance of element(*, xs:anyAtomicType), . instance of element(*, xs:untyped), " +
                        ". instance of element(*, xs:string))"));
    assertEquals ("true true false",
                  _str ("/t:root/t:dec ! (. instance of element(*, xs:decimal), . instance of element(*, xs:anyType), " +
                        ". instance of element(*, xs:anySimpleType))"));
    assertEquals ("true false", _str ("/t:root/t:dec/@refs ! (. instance of attribute(*, xs:IDREFS), . instance of attribute(*, xs:untypedAtomic))"));
    assertEquals ("true false", _str ("/t:root/t:list ! (. instance of element(*, xs:anySimpleType), . instance of element(*, xs:anyAtomicType))"));
  }

  @Test
  public void testIDs ()
  {
    assertEquals ("item", _str ("id('a')/local-name()"));
    assertEquals ("id item", _str ("(id('y'), element-with-id('y'))/local-name()"));
    assertEquals ("refs refs", _str ("(idref('a'), idref('b'))/local-name()"));
  }
}
