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
package com.helger.xpath.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Test class for schema validation in {@link XdmParser} and the schema
 * annotations of {@link XdmTreeBuilder}.
 *
 * @author Philip Helger
 */
public final class XdmParserSchemaTest
{
  private static final String XSD = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t' xmlns:t='urn:t' elementFormDefault='qualified'>" +
                                    "<xs:element name='root'><xs:complexType><xs:sequence>" +
                                    "<xs:element name='int' type='xs:int' minOccurs='0' maxOccurs='unbounded' nillable='true'/>" +
                                    "<xs:element name='ids' type='t:idList' minOccurs='0' maxOccurs='unbounded'/>" +
                                    "<xs:element name='rid' type='t:rid' minOccurs='0' maxOccurs='unbounded'/>" +
                                    "<xs:element name='u' type='t:u' minOccurs='0' maxOccurs='unbounded'/>" +
                                    "<xs:element name='dec' minOccurs='0'><xs:complexType><xs:simpleContent><xs:extension base='xs:decimal'>" +
                                    "<xs:attribute name='key' type='xs:ID'/><xs:attribute name='refs' type='xs:IDREFS'/>" +
                                    "</xs:extension></xs:simpleContent></xs:complexType></xs:element>" +
                                    "<xs:element name='mixed' minOccurs='0'><xs:complexType mixed='true'><xs:sequence>" +
                                    "<xs:element name='b' type='xs:string' minOccurs='0'/></xs:sequence></xs:complexType></xs:element>" +
                                    "</xs:sequence></xs:complexType></xs:element>" +
                                    "<xs:simpleType name='idList'><xs:list itemType='xs:ID'/></xs:simpleType>" +
                                    "<xs:simpleType name='rid'><xs:restriction base='xs:ID'><xs:pattern value='[a-z]+'/></xs:restriction></xs:simpleType>" +
                                    "<xs:simpleType name='u'><xs:union memberTypes='t:rid xs:integer'/></xs:simpleType>" +
                                    "</xs:schema>";

  private static Schema _schema () throws SAXException
  {
    return SchemaFactory.newDefaultInstance ()
                        .newSchema (new StreamSource (new StringReader (XSD)));
  }

  private static XdmNode _parseRoot (final String sXml) throws XdmParseException, SAXException
  {
    return new XdmParser ().setSchema (_schema ()).parse (sXml).getChildAtIndex (0);
  }

  @Test
  public void testAnnotationsAndWhitespace () throws Exception
  {
    final XdmNode aRoot = _parseRoot ("<root xmlns='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n  <int> 12 </int>\n" +
                                      "  <int xsi:nil='true'/>\n  <dec key='k1' refs='k1  k1'>1.5</dec>\n" +
                                      "  <mixed>a <b>x</b> c</mixed>\n</root>");
    // Whitespace in element-only content is gone
    assertEquals (4, aRoot.getChildCount ());
    final XdmSchemaType aRootType = aRoot.getSchemaType ();
    assertNotNull (aRootType);
    assertTrue (aRootType.isAnonymous ());
    assertFalse (aRootType.isSimple ());
    assertNull (aRootType.getAtomicBase ());

    final XdmNode aInt = aRoot.getChildAtIndex (0);
    assertEquals (new XdmQName (XdmConstants.NS_XS, "int"), aInt.getTypeAnnotation ());
    assertTrue (aInt.getSchemaType ().isSimple ());
    assertEquals ("int", aInt.getSchemaType ().getAtomicBase ().localName ());
    assertTrue (aInt.getSchemaType ().isDerivedFrom (XdmConstants.NS_XS, "decimal"));
    assertFalse (aInt.getSchemaType ().isDerivedFrom (XdmConstants.NS_XS, "string"));
    assertFalse (aInt.isNilled ());
    // Both int elements share one schema type object
    assertSame (aInt.getSchemaType (), aRoot.getChildAtIndex (1).getSchemaType ());
    assertTrue (aRoot.getChildAtIndex (1).isNilled ());

    final XdmNode aDec = aRoot.getChildAtIndex (2);
    assertFalse (aDec.getSchemaType ().isSimple ());
    assertEquals ("decimal", aDec.getSchemaType ().getAtomicBase ().localName ());
    assertEquals (EIDKind.ID, aDec.getAttribute ("", "key").getIDKind ());
    assertEquals (EIDKind.IDREFS, aDec.getAttribute ("", "refs").getIDKind ());
    assertTrue (aDec.getAttribute ("", "refs").getSchemaType ().isList ());
    assertEquals ("IDREF", aDec.getAttribute ("", "refs").getSchemaType ().getAtomicBase ().localName ());

    final XdmNode aMixed = aRoot.getChildAtIndex (3);
    assertEquals ("a x c", aMixed.getStringValue ());
    assertNull (aMixed.getSchemaType ().getAtomicBase ());
  }

  @Test
  public void testElementIDs () throws Exception
  {
    final XdmNode aRoot = _parseRoot ("<root xmlns='urn:t'><ids>a</ids><ids>b c</ids><rid>d</rid><u>e</u><u>12</u></root>");
    assertEquals (EIDKind.ID, aRoot.getChildAtIndex (0).getIDKind ());
    // A list of IDs is an ID only as a singleton
    assertEquals (EIDKind.NONE, aRoot.getChildAtIndex (1).getIDKind ());
    assertEquals (EIDKind.ID, aRoot.getChildAtIndex (2).getIDKind ());
    assertEquals ("ID", aRoot.getChildAtIndex (2).getSchemaType ().getAtomicBase ().localName ());
    // A union is an ID only if the ID member matched
    final XdmNode aU1 = aRoot.getChildAtIndex (3);
    final XdmNode aU2 = aRoot.getChildAtIndex (4);
    assertEquals (EIDKind.ID, aU1.getIDKind ());
    assertEquals (EIDKind.NONE, aU2.getIDKind ());
    assertEquals (new XdmQName ("urn:t", "u"), aU1.getTypeAnnotation ());
    assertEquals ("integer", aU2.getSchemaType ().getAtomicBase ().localName ());
  }

  @Test
  public void testCommentOrder () throws Exception
  {
    final XdmNode aRoot = _parseRoot ("<root xmlns='urn:t'><mixed>a<!--c-->b<b>x</b></mixed></root>");
    final XdmNode aMixed = aRoot.getChildAtIndex (0);
    assertEquals (4, aMixed.getChildCount ());
    assertEquals ("a", aMixed.getChildAtIndex (0).getValue ());
    assertEquals (ENodeKind.COMMENT, aMixed.getChildAtIndex (1).getKind ());
    assertEquals ("b", aMixed.getChildAtIndex (2).getValue ());
  }

  @Test
  public void testInvalid () throws Exception
  {
    final XdmParser aParser = new XdmParser ().setSchema (_schema ());
    try
    {
      aParser.parse ("<root xmlns='urn:t'><int>x</int></root>");
      fail ();
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
    try
    {
      aParser.parse ("<undeclared/>");
      fail ();
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
    // Not well-formed is not a validation error
    try
    {
      aParser.parse ("<root xmlns='urn:t'>");
      fail ();
    }
    catch (final XdmParseException ex)
    {
      assertFalse (ex instanceof XdmValidationException);
    }
  }

  @Test
  public void testWithoutSchemaUnchanged () throws Exception
  {
    final XdmNode aRoot = new XdmParser ().parse ("<root xmlns='urn:t'>\n <int>1</int>\n</root>").getChildAtIndex (0);
    assertEquals (3, aRoot.getChildCount ());
    assertNull (aRoot.getSchemaType ());
    assertNull (aRoot.getTypeAnnotation ());
  }
}
