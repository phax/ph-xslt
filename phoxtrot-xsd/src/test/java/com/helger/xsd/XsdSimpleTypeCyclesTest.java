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
package com.helger.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * Port of go-xslt <code>TestSimpleTypeCyclesTerminate</code> (issue 12): a
 * simple type that reaches itself must neither overflow a walk nor survive
 * compilation. A pure-union cycle is tolerated in XSD 1.0, collapsed to the
 * members that do not lead back; a cycle through a restriction base or a list
 * item type is a circular derivation in both versions; any cycle is one in
 * XSD 1.1.
 *
 * @author Philip Helger
 */
public final class XsdSimpleTypeCyclesTest
{
  private static final String CIRCULAR = "circular";

  private record CycleCase (@NonNull String name, @NonNull String schema, @NonNull String want10, @NonNull String want11)
  {}

  @NonNull
  private static String _verdicts (@NonNull final CycleCase aCase, @NonNull final EXsdVersion eVersion, final String sWant)
  {
    final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                                                       aCase.schema () +
                                                       "</xs:schema>",
                                                       null,
                                                       eVersion);
    if (CIRCULAR.equals (sWant))
    {
      assertSame (aCase.name () + ", " + eVersion, EVerdict.INVALID, aCompiled.verdict ());
      assertTrue (aCase.name () + ", " + eVersion + ": " + aCompiled.message (),
                  aCompiled.message ().contains ("circular simpleType derivation"));
      return CIRCULAR;
    }
    assertSame (aCase.name () + ", " + eVersion + ": " + aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    final ICommonsList <String> aGot = new CommonsArrayList <> ();
    for (final String sValue : new String [] { "x", "7" })
    {
      final String sInstance = aCase.name ().contains ("attribute") ? "<e a=\"" + sValue + "\"/>" : "<e>" + sValue + "</e>";
      aGot.add (XsdTestSupport.validate (aCompiled.schema (), sInstance) == EVerdict.VALID ? "valid" : "invalid");
    }
    return aGot.get (0) + "/" + aGot.get (1);
  }

  @Test
  public void testSimpleTypeCyclesTerminate ()
  {
    final CycleCase [] aCases = { new CycleCase ("self as element type",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 "valid/valid",
                                                 CIRCULAR),
                                  new CycleCase ("self as attribute type",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:int\"/></xs:simpleType><xs:element name=\"e\"><xs:complexType><xs:attribute name=\"a\" type=\"u\"/></xs:complexType></xs:element>",
                                                 "invalid/valid",
                                                 CIRCULAR),
                                  new CycleCase ("self as restriction base with enumeration",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:simpleType name=\"v\"><xs:restriction base=\"u\"><xs:enumeration value=\"x\"/></xs:restriction></xs:simpleType><xs:element name=\"e\" type=\"v\"/>",
                                                 "valid/invalid",
                                                 CIRCULAR),
                                  new CycleCase ("self as list item type",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:simpleType name=\"l\"><xs:list itemType=\"u\"/></xs:simpleType><xs:element name=\"e\" type=\"l\"/>",
                                                 "valid/valid",
                                                 CIRCULAR),
                                  new CycleCase ("self as only member",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u\"/></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 "invalid/invalid",
                                                 CIRCULAR),
                                  new CycleCase ("two unions (stE110)",
                                                 "<xs:simpleType name=\"st\"><xs:union memberTypes=\"xs:int xs:string st2\"/></xs:simpleType><xs:simpleType name=\"st2\"><xs:union memberTypes=\"st\"/></xs:simpleType><xs:element name=\"e\" type=\"st2\"/>",
                                                 "valid/valid",
                                                 CIRCULAR),
                                  new CycleCase ("three unions",
                                                 "<xs:simpleType name=\"a\"><xs:union memberTypes=\"b xs:int\"/></xs:simpleType><xs:simpleType name=\"b\"><xs:union memberTypes=\"c\"/></xs:simpleType><xs:simpleType name=\"c\"><xs:union memberTypes=\"a xs:string\"/></xs:simpleType><xs:element name=\"e\" type=\"b\"/>",
                                                 "valid/valid",
                                                 CIRCULAR),
                                  new CycleCase ("inline union naming the enclosing union",
                                                 "<xs:simpleType name=\"u\"><xs:union><xs:simpleType><xs:union memberTypes=\"u xs:string\"/></xs:simpleType></xs:union></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 "valid/valid",
                                                 CIRCULAR),
                                  new CycleCase ("inline restriction of the enclosing union",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"xs:int\"><xs:simpleType><xs:restriction base=\"u\"/></xs:simpleType></xs:union></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 CIRCULAR,
                                                 CIRCULAR),
                                  new CycleCase ("inline list of the enclosing union",
                                                 "<xs:simpleType name=\"u\"><xs:union memberTypes=\"xs:int\"><xs:simpleType><xs:list itemType=\"u\"/></xs:simpleType></xs:union></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 CIRCULAR,
                                                 CIRCULAR),
                                  new CycleCase ("restriction of an inline union naming it",
                                                 "<xs:simpleType name=\"u\"><xs:restriction><xs:simpleType><xs:union memberTypes=\"u xs:string\"/></xs:simpleType></xs:restriction></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 CIRCULAR,
                                                 CIRCULAR),
                                  new CycleCase ("list of an inline union naming it",
                                                 "<xs:simpleType name=\"u\"><xs:list><xs:simpleType><xs:union memberTypes=\"u xs:string\"/></xs:simpleType></xs:list></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                                 CIRCULAR,
                                                 CIRCULAR) };
    for (final CycleCase aCase : aCases)
    {
      assertEquals (aCase.name () + ", XSD 1.0", aCase.want10 (), _verdicts (aCase, EXsdVersion.V10, aCase.want10 ()));
      assertEquals (aCase.name () + ", XSD 1.1", aCase.want11 (), _verdicts (aCase, EXsdVersion.V11, aCase.want11 ()));
    }
  }

  @Test
  public void testCollapsedUnionHasNoCycle ()
  {
    final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                                                       "<xs:simpleType name=\"t\"><xs:union memberTypes=\"t xs:int xs:string\"/></xs:simpleType></xs:schema>",
                                                       null,
                                                       EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    final XsdSimpleType aType = (XsdSimpleType) aCompiled.schema ().m_aTypes.get (new XsdName ("", "t"));
    assertEquals (2, aType.m_aMembers.size ());
    assertTrue (aType.m_aMembers.stream ().noneMatch (x -> x == aType));
  }
}
