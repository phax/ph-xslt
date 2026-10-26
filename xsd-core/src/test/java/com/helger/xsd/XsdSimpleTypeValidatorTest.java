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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * The value checks of simple types, beyond the table of {@link XsdFacetTest}:
 * the primitive lexical spaces, the value-space equality of fixed values and
 * the bounded walks over cyclic unions (go-xslt issue 12).
 *
 * @author Philip Helger
 */
public final class XsdSimpleTypeValidatorTest
{
  private static final String XS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";

  @NonNull
  private static XsdSimpleType _type (@NonNull final String sBody, @NonNull final EXsdVersion eVersion)
  {
    final Compiled aCompiled = XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, eVersion);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    return (XsdSimpleType) aCompiled.schema ().m_aTypes.get (new XsdName ("", "t"));
  }

  private static boolean _valid (@NonNull final XsdSimpleType aType, @NonNull final String sValue)
  {
    try
    {
      XsdSimpleTypeValidator.validate (aType, sValue);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  private static boolean _lexical (@NonNull final EAtomicType ePrim, @NonNull final String sValue)
  {
    try
    {
      XsdSimpleTypeValidator.checkPrimitiveLexical (ePrim, sValue);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  @Test
  public void testPrimitiveLexical ()
  {
    assertTrue (_lexical (EAtomicType.INTEGER, "+12"));
    assertFalse (_lexical (EAtomicType.INTEGER, "1.5"));
    assertTrue (_lexical (EAtomicType.INTEGER, "123456789012345678901234567890"));
    assertTrue (_lexical (EAtomicType.BYTE, "127"));
    assertFalse (_lexical (EAtomicType.BYTE, "128"));
    assertFalse (_lexical (EAtomicType.UNSIGNED_BYTE, "-1"));
    assertTrue (_lexical (EAtomicType.UNSIGNED_LONG, "18446744073709551615"));
    assertFalse (_lexical (EAtomicType.UNSIGNED_LONG, "18446744073709551616"));
    assertFalse (_lexical (EAtomicType.POSITIVE_INTEGER, "0"));
    assertTrue (_lexical (EAtomicType.NEGATIVE_INTEGER, "-1"));
    assertTrue (_lexical (EAtomicType.DECIMAL, ".5"));
    assertTrue (_lexical (EAtomicType.DECIMAL, "5."));
    assertFalse (_lexical (EAtomicType.DECIMAL, "1/2"));
    assertFalse (_lexical (EAtomicType.DECIMAL, "."));
    assertTrue (_lexical (EAtomicType.DOUBLE, "1e400"));
    assertTrue (_lexical (EAtomicType.DOUBLE, "-INF"));
    assertTrue (_lexical (EAtomicType.FLOAT, "NaN"));
    assertFalse (_lexical (EAtomicType.DOUBLE, "1e"));
    assertTrue (_lexical (EAtomicType.DATE, "2024-02-29"));
    assertFalse (_lexical (EAtomicType.DATE, "2023-02-29"));
    assertTrue (_lexical (EAtomicType.BOOLEAN, "1"));
    assertFalse (_lexical (EAtomicType.BOOLEAN, "yes"));
    assertTrue (_lexical (EAtomicType.HEX_BINARY, "0aFF"));
    assertFalse (_lexical (EAtomicType.HEX_BINARY, "0aF"));
    assertTrue (_lexical (EAtomicType.BASE64_BINARY, "AQ=="));
    assertFalse (_lexical (EAtomicType.BASE64_BINARY, "AQ="));
    assertTrue (_lexical (EAtomicType.NCNAME, "a.b-c"));
    assertFalse (_lexical (EAtomicType.NCNAME, "a:b"));
    assertTrue (_lexical (EAtomicType.NAME, "a:b"));
    assertTrue (_lexical (EAtomicType.NMTOKEN, "1:2"));
    assertTrue (_lexical (EAtomicType.LANGUAGE, "en-GB"));
    assertFalse (_lexical (EAtomicType.LANGUAGE, "toolonglanguage"));
    assertTrue (_lexical (EAtomicType.QNAME, "p:a"));
    assertFalse (_lexical (EAtomicType.QNAME, "a:b:c"));
    assertTrue (_lexical (EAtomicType.STRING, "anything\u0001goes"));
  }

  @Test
  public void testFixedEqual ()
  {
    final XsdSimpleType aDecimal = _type ("<xs:simpleType name=\"t\"><xs:restriction base=\"xs:decimal\"/></xs:simpleType>",
                                          EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aDecimal, "2", " 2.0 "));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aDecimal, "2", "3"));
    final XsdSimpleType aString = _type ("<xs:simpleType name=\"t\"><xs:restriction base=\"xs:string\"/></xs:simpleType>",
                                         EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aString, "   1  2", "   1  2"));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aString, "   1  2", " 1 2"));
    final XsdSimpleType aList = _type ("<xs:simpleType name=\"t\"><xs:list itemType=\"xs:int\"/></xs:simpleType>", EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aList, "1 02", " 01   2 "));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aList, "1 2", "1 2 3"));
    final XsdSimpleType aUnion = _type ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"xs:int xs:boolean\"/></xs:simpleType>",
                                        EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aUnion, "1", "01"));
    // 1 is an int, true a boolean: different members
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aUnion, "1", "true"));
    assertNotNull (XsdSimpleTypeValidator.firstMemberRaw (aUnion, "true"));
    assertNull (XsdSimpleTypeValidator.firstMemberRaw (aUnion, "x"));
  }

  @Test
  public void testCyclicUnion ()
  {
    // XSD 1.0 tolerates a pure-union cycle (stE110); the member walks end
    final XsdSimpleType aSelf = _type ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"t xs:int\"/></xs:simpleType>",
                                       EXsdVersion.V10);
    assertTrue (_valid (aSelf, "12"));
    assertFalse (_valid (aSelf, "x"));
    assertNotNull (XsdSimpleTypeValidator.firstMemberRaw (aSelf, "12"));
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aSelf, "12", "012"));
    final XsdSimpleType aPair = _type ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"u xs:int\"/></xs:simpleType>" +
                                       "<xs:simpleType name=\"u\"><xs:union memberTypes=\"t xs:boolean\"/></xs:simpleType>",
                                       EXsdVersion.V10);
    assertTrue (_valid (aPair, "true"));
    assertTrue (_valid (aPair, "7"));
    assertFalse (_valid (aPair, "x"));
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aPair, "7", "07"));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aPair, "true", "1"));
    // Many members that all lead back: linear, not exponential
    final StringBuilder aSB = new StringBuilder ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"");
    for (int i = 0; i < 40; i++)
      aSB.append ("t ");
    aSB.append ("xs:int\"/></xs:simpleType>");
    final XsdSimpleType aWide = _type (aSB.toString (), EXsdVersion.V10);
    assertTrue (_valid (aWide, "5"));
    assertFalse (_valid (aWide, "x"));
  }

  @Test
  public void testQNameBinding ()
  {
    final XsdSimpleType aQName = _type ("<xs:simpleType name=\"t\"><xs:restriction base=\"xs:QName\"/></xs:simpleType>",
                                        EXsdVersion.V11);
    // Without a node only the shape is checked
    assertTrue (_valid (aQName, "p:a"));
    assertFalse (_valid (aQName, "a:b:c"));
  }
}
