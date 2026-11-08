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
package com.helger.xpath.xdm.xerces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmSchemaSource;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;
import com.helger.xpath.xdm.schema.XdmTypedToken;

/**
 * Test class for the Xerces-J schema bridge.
 *
 * @author Philip Helger
 */
public final class XercesSchemaProviderTest
{
  private static final String NS = "urn:t";
  private static final String XSD = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t' xmlns:t='urn:t'>" +
                                    "<xs:element name='a' type='t:at'/>" +
                                    "<xs:element name='b' substitutionGroup='t:a'/>" +
                                    "<xs:complexType name='at'><xs:sequence>" +
                                    "<xs:element name='c' type='t:u' maxOccurs='9'/>" +
                                    "</xs:sequence><xs:attribute name='size' type='t:size'/>" +
                                    "<xs:attribute name='key' type='xs:ID'/><xs:attribute name='def' type='xs:int' default='3'/></xs:complexType>" +
                                    "<xs:simpleType name='u'><xs:union memberTypes='xs:integer xs:date'/></xs:simpleType>" +
                                    "<xs:simpleType name='size'><xs:restriction base='xs:positiveInteger'><xs:maxInclusive value='20'/></xs:restriction></xs:simpleType>" +
                                    "<xs:simpleType name='sizes'><xs:list itemType='t:size'/></xs:simpleType>" +
                                    "</xs:schema>";

  private static IXdmSchemaSet _compile () throws Exception
  {
    final ICommonsList <XdmSchemaSource> aSources = new CommonsArrayList <> (new XdmSchemaSource (null, XSD));
    return XdmSchemaSupport.compile (aSources);
  }

  @Test
  public void testModel () throws Exception
  {
    assertTrue (XdmSchemaSupport.isAvailable ());
    final IXdmSchemaSet aSet = _compile ();
    final IXdmElementDeclaration aA = aSet.getElementDeclaration (new XdmQName (NS, "a"));
    assertNotNull (aA);
    assertNotNull (aA.getSubstitutionMember (new XdmQName (NS, "b")));
    assertNull (aA.getSubstitutionMember (new XdmQName (NS, "c")));
    assertEquals (EXdmContentType.ELEMENT_ONLY, aA.getTypeDefinition ().getContentType ());
    assertNull (aSet.getElementDeclaration (new XdmQName (NS, "c")));

    final IXdmTypeDefinition aSize = aSet.getTypeDefinition (new XdmQName (NS, "size"));
    assertEquals ("positiveInteger", aSize.getBuiltinAtomicType ().localName ());
    assertTrue (aSize.isDerivedFrom (aSet.getTypeDefinition (new XdmQName (XdmConstants.NS_XS, "integer"))));
    assertFalse (aSize.isDerivedFrom (aSet.getTypeDefinition (new XdmQName (XdmConstants.NS_XS, "string"))));

    final ICommonsList <XdmTypedToken> aTokens = aSet.getTypeDefinition (new XdmQName (NS, "sizes")).validate (" 3  4 ", null);
    assertEquals (2, aTokens.size ());
    assertEquals ("4", aTokens.get (1).lexical ());
    assertEquals (aSize, aTokens.get (1).type ());

    final ICommonsList <XdmTypedToken> aUnion = aSet.getTypeDefinition (new XdmQName (NS, "u")).validate ("2020-01-01", null);
    assertEquals ("date", aUnion.get (0).builtinType ().localName ());
    try
    {
      aSize.validate ("21", null);
      fail ();
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
    // The built-in set knows the built-in types
    assertNotNull (XdmSchemaSupport.getBuiltinSchemaSet ().getTypeDefinition (new XdmQName (XdmConstants.NS_XS, "ID")));
  }

  @Test
  public void testValidateElement () throws Exception
  {
    final IXdmSchemaSet aSet = _compile ();
    final IXdmNode aDoc = new XdmParser ().parse ("<t:a xmlns:t='urn:t' size='7'>\n  <c>12</c>\n  <c>2020-01-01</c>\n</t:a>");
    final IXdmNode aA = aDoc.getChildAtIndex (0);
    aSet.validateElement (aA, null, true);
    assertEquals (new XdmQName (NS, "at"), aA.getSchemaType ().getName ());
    assertNotNull (aA.getSchemaType ().getDefinition ());
    // Whitespace of element-only content is gone
    assertEquals (2, aA.getChildCount ());
    final IXdmNode aC2 = aA.getChildAtIndex (1);
    assertEquals ("date", aC2.getSchemaType ().getValueDefinition ().getName ().localName ());
    assertEquals ("size", aA.getAttribute ("", "size").getSchemaType ().getName ().localName ());

    // Against a named type, with an undeclared element name
    final IXdmNode aX = new XdmParser ().parse ("<x><c>1</c></x>").getChildAtIndex (0);
    aSet.validateElement (aX, aSet.getTypeDefinition (new XdmQName (NS, "at")), false);
    assertEquals ("at", aX.getSchemaType ().getName ().localName ());

    final IXdmNode aBad = new XdmParser ().parse ("<t:a xmlns:t='urn:t'><c>x</c></t:a>").getChildAtIndex (0);
    try
    {
      aSet.validateElement (aBad, null, false);
      fail ();
    }
    catch (final XdmValidationException ex)
    {
      assertFalse (ex.isDocumentConstraint ());
      assertNull (aBad.getSchemaType ());
    }
  }

  @Test
  public void testValidateDocument () throws Exception
  {
    final XdmParser aParser = new XdmParser ().setSchemaSet (_compile ());
    final IXdmNode aDoc = aParser.parse ("<t:a xmlns:t='urn:t' size='7' key=' k '>\n  <c>12</c>\n  <c>2020-01-01</c>\n</t:a>");
    final IXdmNode aA = aDoc.getChildAtIndex (0);
    assertEquals (new XdmQName (NS, "at"), aA.getSchemaType ().getName ());
    assertEquals (EXdmContentType.ELEMENT_ONLY, aA.getSchemaType ().getDefinition ().getContentType ());
    // Whitespace of element-only content is gone
    assertEquals (2, aA.getChildCount ());
    assertEquals ("date", aA.getChildAtIndex (1).getSchemaType ().getValueDefinition ().getName ().localName ());
    // The union attribute type is the declared one
    assertEquals ("size", aA.getAttribute ("", "size").getSchemaType ().getName ().localName ());
    assertEquals (EIDKind.ID, aA.getAttribute ("", "key").getIDKind ());
    assertEquals ("k", aA.getAttribute ("", "key").getValue ());
    assertEquals ("3", aA.getAttribute ("", "def").getValue ());
    try
    {
      aParser.parse ("<t:a xmlns:t='urn:t'><c>x</c></t:a>");
      fail ();
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
  }
}
